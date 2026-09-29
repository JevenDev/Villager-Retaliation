package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import java.util.UUID;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class HiredProductionService {
    public static final String STATE_TAG = "ProductionQueue";

    private HiredProductionService() {
    }

    public static Access access(ServerLevel level, Villager villager, HiredVillagerRole role) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty != null) {
            var queue = duty.town().productionQueues().get(duty.area().id());
            return queue == null ? new Access(false, null, "") : new Access(true, queue,
                    queue.role() == role ? "" : "production_role_changed");
        }
        var state = HiredWorkStateStore.state(villager);
        if (!state.contains(STATE_TAG)) return new Access(false, null, "");
        try {
            if (!state.contains(STATE_TAG, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid personal production queue");
            var queue = HiredProductionQueue.load(state.getCompound(STATE_TAG), level.registryAccess(),
                    updated -> state.put(STATE_TAG, updated.save(level.registryAccess())));
            if (queue.scope() != HiredProductionQueue.Scope.PERSONAL) return new Access(true, null, "invalid_production_queue");
            if (HiredVillagerContractService.currentContractId(villager).filter(queue.owner()::equals).isEmpty()) {
                return new Access(true, null, "production_contract_changed");
            }
            return new Access(true, queue, queue.role() == role ? "" : "production_role_changed");
        } catch (IllegalArgumentException exception) {
            return new Access(true, null, "invalid_production_queue");
        }
    }

    public static String appendPersonal(ServerPlayer player, Villager villager, long revision, HiredProductionOrder requested) {
        if (!owns(player, villager)) return "Worker permission changed";
        var level = player.serverLevel();
        var authority = VillagerWorkAuthority.resolve(level, villager);
        var error = validate(level, authority.role(), requested);
        if (!error.isEmpty()) return error;
        if (requested.storage() != null && AssignedStorageService.productionStockAt(villager, requested.storage(), ignored -> false) < 0L) {
            return "Choose an available assigned output storage";
        }
        var access = access(level, villager, authority.role());
        if (!access.blocker().isEmpty()) return access.blocker();
        var queue = access.queue();
        if (queue == null) {
            if (revision != 0L) return "Production orders changed";
            var contract = HiredVillagerContractService.currentContractId(villager).orElse(null);
            if (contract == null) return "Worker contract changed";
            var state = HiredWorkStateStore.state(villager);
            queue = new HiredProductionQueue(contract, HiredProductionQueue.Scope.PERSONAL, authority.role(),
                    updated -> state.put(STATE_TAG, updated.save(level.registryAccess())));
        } else if (queue.configurationRevision() != revision) return "Production orders changed";
        if (!queue.append(requested)) return "Production queue is full or the order already exists";
        HiredWorkSession.invalidate(villager);
        return "";
    }

    public static String editPersonal(ServerPlayer player, Villager villager, long revision, UUID order, Edit edit, int index) {
        if (!owns(player, villager)) return "Worker permission changed";
        var authority = VillagerWorkAuthority.resolve(player.serverLevel(), villager);
        var access = access(player.serverLevel(), villager, authority.role());
        if (!access.blocker().isEmpty()) return access.blocker();
        if (access.queue() == null || access.queue().configurationRevision() != revision) return "Production orders changed";
        if (!edit(access.queue(), order, edit, index)) return "Order has unfinished production or is unavailable";
        HiredWorkSession.invalidate(villager);
        return "";
    }

    public static void releasePlanned(ServerLevel level, Villager villager, HiredVillagerRole role) {
        var access = access(level, villager, role);
        if (access.queue() != null) {
            var allocation = access.queue().allocation(villager.getUUID());
            if (allocation != null && !allocation.started()) access.queue().release(allocation.id());
        }
        AssignedStorageService.releaseProductionInputs(villager);
    }

    public static boolean owns(ServerPlayer player, Villager villager) {
        if (player == null || villager == null || !player.isAlive() || player.isSpectator() || !villager.isAlive() || villager.isBaby()
                || player.level() != villager.level() || player.serverLevel().getEntity(villager.getUUID()) != villager) return false;
        var authority = VillagerWorkAuthority.resolve(player.serverLevel(), villager);
        return authority.active() && !authority.civic() && authority.owner().filter(player.getUUID()::equals).isPresent();
    }

    public static String validate(ServerLevel level, HiredVillagerRole role, HiredProductionOrder requested) {
        if (requested == null || role == null || requested.produced() != 0L || requested.paused() || requested.cancelled() || !requested.blocker().isEmpty()) {
            return "Invalid new production order";
        }
        if (!requested.recipe().supports(level, role) || !ItemStack.matches(requested.output(), requested.recipe().output(level))) {
            return "Recipe is unavailable or incompatible with this role";
        }
        if (requested.storage() != null && (level.isOutsideBuildHeight(requested.storage())
                || !level.getWorldBorder().isWithinBounds(requested.storage()))) return "Output storage is outside world bounds";
        return "";
    }

    public static boolean edit(HiredProductionQueue queue, UUID order, Edit edit, int index) {
        if (edit == null || order == null) return false;
        var workers = queue.allocations().stream().filter(allocation -> !allocation.started() && allocation.order().equals(order))
                .map(HiredProductionQueue.Allocation::worker).toList();
        boolean changed = switch (edit) {
            case PAUSE -> queue.pause(order, true);
            case RESUME -> queue.pause(order, false);
            case CANCEL -> queue.cancel(order);
            case REMOVE -> queue.remove(order);
            case MOVE -> queue.reorder(order, index);
        };
        if (changed) for (var worker : workers) {
            if (queue.allocation(worker) == null) AssignedStorageService.releaseProductionInputs(worker);
        }
        return changed;
    }

    public record Access(boolean managed, HiredProductionQueue queue, String blocker) {
    }

    public enum Edit {
        PAUSE,
        RESUME,
        CANCEL,
        REMOVE,
        MOVE
    }
}
