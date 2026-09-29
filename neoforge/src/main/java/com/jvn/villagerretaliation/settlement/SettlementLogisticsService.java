package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementLogisticsService {
    private static final String TASK_TAG = "SettlementHaulTask";
    private static final String RETRY_TAG = "SettlementHaulRetryAt";
    private static final String FAILED_DEPOT_TAG = "SettlementHaulFailedDepot";

    private SettlementLogisticsService() {
    }

    public static boolean setStockTarget(ServerPlayer player, VillageAllegianceId id, UUID depot,
                                         ItemStack sample, int count) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        return SettlementSavedData.get(player.serverLevel()).get(id)
                .filter(town -> town.dimension().equals(player.level().dimension().location()))
                .map(town -> town.setDepotStockTarget(player.getUUID(), depot, sample, count)).orElse(false);
    }

    public static boolean isHauler(Villager villager) {
        return SettlementWorkforceService.duty(villager)
                .map(duty -> duty.area().type() == SettlementAreaType.DEPOT).orElse(false);
    }

    public static SettlementHaulTask task(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return null;
        }
        return SettlementHaulTask.load(HiredWorkStateStore.state(villager).getCompound(TASK_TAG), level.registryAccess());
    }

    public static void clear(Villager villager) {
        HiredWorkStateStore.state(villager).remove(TASK_TAG);
        AssignedStorageService.releaseCourierClaims(villager);
    }

    public static void resetAssignment(Villager villager) {
        var state = HiredWorkStateStore.state(villager);
        clear(villager);
        state.remove(RETRY_TAG);
        state.remove(FAILED_DEPOT_TAG);
        HiredRoute.clear(state);
    }

    public static void failed(Villager villager, UUID depot) {
        var state = HiredWorkStateStore.state(villager);
        clear(villager);
        state.putUUID(FAILED_DEPOT_TAG, depot);
        state.putLong(RETRY_TAG, villager.level().getGameTime() + 200L);
    }

    public static SettlementHaulTask prepare(ServerLevel level, Villager villager, SettlementRecord town) {
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        List<ItemStack> cargo = inventory.collectOutputItems().stream().map(HiredJobInventory.OutputStack::stack).toList();
        SettlementHaulTask current = task(villager);
        var links = SettlementWorksiteService.links(town, town.duties().get(villager.getUUID()));
        if (current != null && links.permitsOutput(current.destination())
                && (!cargo.isEmpty() || links.permitsSupply(current.source())) && valid(level, town, current, !cargo.isEmpty())
                && (cargo.isEmpty() || cargo.stream().anyMatch(current::matches))) {
            return current;
        }
        clear(villager);
        List<SettlementDepot> depots = SettlementStock.depots(town).stream()
                .filter(depot -> level.hasChunkAt(depot.position()))
                .filter(depot -> !blocked(villager, depot.id())).toList();
        SettlementHaulTask selected = null;
        if (!cargo.isEmpty()) {
            for (ItemStack stack : cargo) {
                for (SettlementDepot destination : depots) {
                    if (destination.purpose().providesSupplies() && links.permitsOutput(destination.id())
                            && SettlementStock.capacity(List.of(destination), stack) > 0
                            && AssignedStorageService.unreservedFramedAllowance(level, destination.position(), stack,
                                    VillagerFilterPolicy.TransferOperation.RECEIVE) > 0) {
                        selected = new SettlementHaulTask(destination.id(), destination.id(), stack,
                                destination.count(candidate -> ItemStack.isSameItemSameComponents(candidate, stack)) + stack.getCount(), true);
                        break;
                    }
                }
                if (selected != null) {
                    break;
                }
            }
        } else {
            selected = select(level, town, depots, villager);
        }
        if (selected != null) {
            HiredWorkStateStore.state(villager).put(TASK_TAG, selected.save(level.registryAccess()));
        }
        SettlementDepotService.synchronizeStorage(level, villager, town);
        if (selected != null && cargo.isEmpty()) {
            AssignedStorageService.reserveCourierTransferClaims(villager, town.depots().get(selected.source()).position(), 64);
        }
        return selected;
    }

    static SettlementHaulTask select(ServerLevel level, SettlementRecord town, List<SettlementDepot> depots, Villager worker) {
        return select(level, town, depots, worker.getUUID(), worker.blockPosition(),
                sample -> VillagerItemFilterService.mayWithdraw(worker, sample), false);
    }

    static SettlementHaulTask selectBackground(ServerLevel level, SettlementRecord town, List<SettlementDepot> depots,
                                               SettlementWorkerState worker) {
        ItemStack filter = worker.items(level.registryAccess()).get(HiredJobInventory.FILTER_SLOT);
        return select(level, town, depots, worker.id(), worker.position(), sample -> filter.isEmpty()
                || com.jvn.villagerretaliation.item.VillagerRetaliationItems.isRecipeFilter(filter)
                || com.jvn.villagerretaliation.item.VillagerFilterMatcher.matches(level, filter, sample), true);
    }

    private static SettlementHaulTask select(ServerLevel level, SettlementRecord town, List<SettlementDepot> depots,
                                             UUID worker, net.minecraft.core.BlockPos position,
                                             java.util.function.Predicate<ItemStack> mayWithdraw, boolean background) {
        var links = SettlementWorksiteService.links(town, town.duties().get(worker));
        for (SettlementDepot destination : depots) {
            if (!links.permitsOutput(destination.id())) continue;
            if (destination.stockTargets().isEmpty() && destination.purpose() != SettlementDepotPurpose.FOOD) {
                continue;
            }
            List<SettlementDepot> sources = sources(depots, destination, position);
            for (SettlementDepot source : sources) {
                if (!links.permitsSupply(source.id())) continue;
                for (int slot = 0; slot < source.getContainerSize(); slot++) {
                    ItemStack sample = source.getItem(slot);
                    if (sample.isEmpty() || !destination.purpose().accepts(sample)
                            || !mayWithdraw.test(sample)) {
                        continue;
                    }
                    int target = requestedTarget(level, town, destination, sample);
                    int available = source.count(stack -> ItemStack.isSameItemSameComponents(stack, sample))
                            - Math.max(0, requestedTarget(level, town, source, sample))
                            - AssignedStorageService.reservedItemCount(level, source.position(), worker,
                                    VillagerFilterPolicy.TransferOperation.PROVIDE, sample);
                    available = Math.min(available, framedAllowance(level, source, sample, VillagerFilterPolicy.TransferOperation.PROVIDE, background));
                    int reserved = AssignedStorageService.reservedItemCount(level, destination.position(), worker,
                            VillagerFilterPolicy.TransferOperation.RECEIVE, sample);
                    if (target > destination.count(stack -> ItemStack.isSameItemSameComponents(stack, sample)) + reserved
                            && available > 0 && SettlementStock.capacity(List.of(destination), sample) > 0
                            && framedAllowance(level, destination, sample, VillagerFilterPolicy.TransferOperation.RECEIVE, background) > 0) {
                        return new SettlementHaulTask(source.id(), destination.id(), sample, target, false);
                    }
                }
            }
        }
        for (SettlementDepot source : depots) {
            if (source.purpose() != SettlementDepotPurpose.OUTPUT || !links.permitsSupply(source.id())) {
                continue;
            }
            for (int slot = 0; slot < source.getContainerSize(); slot++) {
                ItemStack sample = source.getItem(slot);
                if (sample.isEmpty() || !mayWithdraw.test(sample)
                        || source.count(stack -> ItemStack.isSameItemSameComponents(stack, sample))
                                <= Math.max(0, source.stockTarget(sample))
                                        + AssignedStorageService.reservedItemCount(level, source.position(), worker,
                                                VillagerFilterPolicy.TransferOperation.PROVIDE, sample)) {
                    continue;
                }
                if (framedAllowance(level, source, sample, VillagerFilterPolicy.TransferOperation.PROVIDE, background) <= 0) {
                    continue;
                }
                for (SettlementDepot destination : depots) {
                    if (!destination.id().equals(source.id()) && links.permitsOutput(destination.id()) && destination.purpose() == SettlementDepotPurpose.GENERAL
                            && destination.stockTarget(sample) < 0 && SettlementStock.capacity(List.of(destination), sample) > 0
                            && framedAllowance(level, destination, sample, VillagerFilterPolicy.TransferOperation.RECEIVE, background) > 0) {
                        int existing = destination.count(stack -> ItemStack.isSameItemSameComponents(stack, sample));
                        return new SettlementHaulTask(source.id(), destination.id(), sample, existing + 64, true);
                    }
                }
            }
        }
        return null;
    }

    static int framedAllowance(ServerLevel level, SettlementDepot depot, ItemStack sample,
                               VillagerFilterPolicy.TransferOperation operation, boolean background) {
        if (!background) {
            return AssignedStorageService.unreservedFramedAllowance(level, depot.position(), sample, operation);
        }
        return depot.rulesObserved() ? AssignedStorageService.observedTransferAllowance(level, depot.position(), depot,
                depot.observedRules(), sample, operation) : 0;
    }

    private static List<SettlementDepot> sources(List<SettlementDepot> depots, SettlementDepot destination, BlockPos worker) {
        return depots.stream().filter(depot -> !depot.id().equals(destination.id()))
                .sorted(Comparator.comparingInt((SettlementDepot depot) -> depot.purpose() == SettlementDepotPurpose.OUTPUT ? 0 : 1)
                        .thenComparingDouble(depot -> depot.position().distSqr(worker) + depot.position().distSqr(destination.position())))
                .toList();
    }

    private static boolean blocked(Villager villager, UUID depot) {
        var state = HiredWorkStateStore.state(villager);
        return state.getLong(RETRY_TAG) > villager.level().getGameTime()
                && state.hasUUID(FAILED_DEPOT_TAG) && state.getUUID(FAILED_DEPOT_TAG).equals(depot);
    }

    private static boolean valid(ServerLevel level, SettlementRecord town, SettlementHaulTask task, boolean carrying) {
        SettlementDepot source = town.depots().get(task.source());
        SettlementDepot destination = town.depots().get(task.destination());
        if (destination == null || !enabled(town, destination) || !level.hasChunkAt(destination.position())
                || SettlementStock.capacity(List.of(destination), task.sample()) == 0) {
            return false;
        }
        int target = task.surplus() ? task.target() : requestedTarget(level, town, destination, task.sample());
        if (target <= destination.count(task::matches)) {
            return false;
        }
        return carrying || source != null && enabled(town, source) && level.hasChunkAt(source.position())
                && source.count(task::matches) > Math.max(0, requestedTarget(level, town, source, task.sample()));
    }

    private static boolean enabled(SettlementRecord town, SettlementDepot depot) {
        SettlementArea area = town.areas().get(depot.area());
        return area != null && area.enabled();
    }

    static int requestedTarget(ServerLevel level, SettlementRecord town, SettlementDepot depot, ItemStack sample) {
        int configured = depot.stockTarget(sample);
        if (configured >= 0 || depot.purpose() != SettlementDepotPurpose.FOOD || !SettlementFoodService.isMeal(sample)) {
            return configured;
        }
        int population = VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), level.getGameTime()).size();
        long foodDepots = SettlementStock.depots(town).stream().filter(candidate -> candidate.purpose() == SettlementDepotPurpose.FOOD).count();
        long nutrition = 0L;
        for (int slot = 0; slot < depot.getContainerSize(); slot++) {
            ItemStack stored = depot.getItem(slot);
            if (SettlementFoodService.isMeal(stored) && !ItemStack.isSameItemSameComponents(stored, sample)) {
                nutrition += (long) stored.getCount() * stored.get(DataComponents.FOOD).nutrition();
            }
        }
        long needed = Math.max(0L, (long) population * SettlementFoodService.dailyNutrition() / Math.max(1L, foodDepots) - nutrition);
        int perItem = sample.get(DataComponents.FOOD).nutrition();
        return (int) Math.min(4_096L, (needed + perItem - 1L) / perItem);
    }

    public static List<AssignedContainerRecord> assignments(ServerLevel level, Villager villager, SettlementRecord town) {
        SettlementHaulTask task = task(villager);
        if (task == null) {
            return List.of();
        }
        List<AssignedContainerRecord> records = new ArrayList<>();
        SettlementDepot source = town.depots().get(task.source());
        SettlementDepot destination = town.depots().get(task.destination());
        if (source != null && !task.source().equals(task.destination()) && enabled(town, source)) {
            records.add(reference(level, villager, town, source, AssignedStorageService.SUPPLY_PURPOSE));
        }
        if (destination != null && enabled(town, destination)) {
            records.add(reference(level, villager, town, destination, AssignedStorageService.OUTPUT_PURPOSE));
        }
        return List.copyOf(records);
    }

    private static AssignedContainerRecord reference(ServerLevel level, Villager villager, SettlementRecord town,
                                                       SettlementDepot depot, String purpose) {
        return new AssignedContainerRecord(level.dimension(), depot.position(), villager.getUUID(), town.leader(),
                purpose, 9 - depot.priority(), "unknown");
    }

    public static boolean permitsStorage(Villager villager, SettlementDepot depot, String purpose) {
        SettlementHaulTask task = task(villager);
        return task != null && (AssignedStorageService.SUPPLY_PURPOSE.equals(purpose)
                && task.source().equals(depot.id()) && !task.source().equals(task.destination())
                || AssignedStorageService.OUTPUT_PURPOSE.equals(purpose) && task.destination().equals(depot.id()));
    }

    public static int transferAllowance(ServerLevel level, UUID worker, BlockPos position, ItemStack sample,
                                         VillagerFilterPolicy.TransferOperation operation, int claimed) {
        if (worker == null || !(level.getEntity(worker) instanceof Villager villager)) {
            return Integer.MAX_VALUE;
        }
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty == null || duty.area().type().role() != HiredVillagerRole.COURIER) {
            return Integer.MAX_VALUE;
        }
        SettlementHaulTask task = task(villager);
        if (task == null || !task.matches(sample)) {
            return 0;
        }
        SettlementRecord town = duty.town();
        boolean providing = operation == VillagerFilterPolicy.TransferOperation.PROVIDE;
        SettlementDepot depot = town.depots().get(providing ? task.source() : task.destination());
        if (depot == null || !depot.position().equals(position) || !enabled(town, depot)) {
            return 0;
        }
        int current = depot.count(task::matches);
        int target = providing || !task.surplus() ? Math.max(0, requestedTarget(level, town, depot, sample)) : task.target();
        return Math.max(0, (providing ? current - target : target - current) - claimed);
    }
}
