package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.network.ProductionRequestPayload;
import com.jvn.villagerretaliation.network.ProductionSnapshotPayload;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementProductionService;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ProductionManagementService {
    private ProductionManagementService() {
    }

    public static boolean supports(HiredVillagerRole role) {
        return role == HiredVillagerRole.CRAFTSMAN || role == HiredVillagerRole.COOK
                || role == HiredVillagerRole.SMELTER || role == HiredVillagerRole.BREWING;
    }

    public static void handle(ServerPlayer player, ProductionRequestPayload request) {
        if (!ServerboundRequestLimiter.tryAcquire(player, ProductionRequestPayload.TYPE.id(), 3L)) {
            PacketDistributor.sendToPlayer(player, unavailable(player, request, "Please wait before changing production again"));
            return;
        }
        boolean action = !(request.command() instanceof ProductionRequestPayload.View);
        String feedback = action ? apply(player, request) : "";
        PacketDistributor.sendToPlayer(player, snapshot(player, request, action && feedback.isEmpty(), feedback));
    }

    public static String apply(ServerPlayer player, ProductionRequestPayload request) {
        if (player == null || !player.isAlive() || player.isSpectator()) return "Production permission changed";
        if (request.command() instanceof ProductionRequestPayload.View) return "";
        var level = player.serverLevel();
        var target = request.target();
        Villager villager = target.town() == null && level.getEntity(target.subject()) instanceof Villager loaded ? loaded : null;
        var town = target.town() == null ? null : SettlementSavedData.get(level).get(new VillageAllegianceId(target.town())).orElse(null);
        if (target.town() == null ? !HiredProductionService.owns(player, villager)
                : town == null || !town.id().value().equals(target.town()) || !town.dimension().equals(level.dimension().location())
                    || !town.permits(player.getUUID(), SettlementPermission.VIEW) || town.workplace(target.subject()) == null) {
            return "Production permission or assignment changed";
        }
        var role = villager != null ? VillagerWorkAuthority.resolve(level, villager).role() : town.workplace(target.subject()).type().role();
        if (!supports(role)) return "This role does not have a production queue";
        if (request.command() instanceof ProductionRequestPayload.Append append) {
            HiredProductionOrder order;
            try {
                order = HiredProductionOrder.create(level, append.recipe(), append.mode(), append.amount(), append.storage());
            } catch (IllegalArgumentException exception) {
                return "Recipe or output storage is unavailable";
            }
            return villager != null ? HiredProductionService.appendPersonal(player, villager, request.revision(), order)
                    : SettlementProductionService.append(player, town, target.subject(), request.revision(), order);
        }
        var edit = (ProductionRequestPayload.Edit) request.command();
        return villager != null ? HiredProductionService.editPersonal(player, villager, request.revision(), edit.order(), edit.edit(), edit.index())
                : SettlementProductionService.edit(player, town, target.subject(), request.revision(), edit.order(), edit.edit(), edit.index());
    }

    public static ProductionSnapshotPayload snapshot(ServerPlayer player, ProductionRequestPayload request, boolean applied, String feedback) {
        if (!player.isAlive() || player.isSpectator()) return unavailable(player, request, "Production permission changed");
        if (request.target().town() != null) return SettlementProductionService.snapshot(player, request, applied, feedback);
        var level = player.serverLevel();
        var villager = level.getEntity(request.target().subject()) instanceof Villager loaded ? loaded : null;
        if (!HiredProductionService.owns(player, villager)) return unavailable(player, request, "Personal worker is unavailable");
        var role = VillagerWorkAuthority.resolve(level, villager).role();
        if (!supports(role)) return unavailable(player, request, "This role does not have a production queue");
        var access = HiredProductionService.access(level, villager, role);
        var queue = access.blocker().isEmpty() ? access.queue() : null;
        var state = HiredWorkStateStore.state(villager);
        var legacy = queue == null && role == HiredVillagerRole.BREWING ? legacy(level, villager.getUUID(), state, activeBottles(level, state)) : null;
        var storages = AssignedStorageService.assignedStorage(level, villager).stream()
                .filter(record -> record.dimension().equals(level.dimension()) && (record.purpose().equals(AssignedStorageService.OUTPUT_PURPOSE)
                        || record.purpose().equals(AssignedStorageService.GENERAL_PURPOSE)))
                .map(record -> record.pos()).distinct().limit(128)
                .map(position -> new ProductionSnapshotPayload.Storage(position, position.toShortString(),
                        AssignedStorageService.productionStockAt(villager, position, ignored -> false) >= 0L)).toList();
        var stock = new LinkedHashMap<UUID, Long>();
        if (queue != null) queue.orders().forEach(order -> stock.put(order.id(), HiredProductionStock.count(level, villager, queue, order)));
        String blocker = access.blocker();
        if (blocker.isEmpty() && legacy == null && role == HiredVillagerRole.BREWING && BrewingWorker.hasOrder(state)) blocker = "The legacy brewing order requires recovery";
        return new ProductionSnapshotPayload(level.dimension().location(), request.target(), request.request(), applied, role,
                title(villager.getName().getString()), blocker.isEmpty(), blocker, feedback, queue == null ? null : queue.save(level.registryAccess()),
                legacy == null ? 0 : 1, legacy == null ? List.of() : List.of(legacy), storages, stock);
    }

    public static ProductionSnapshotPayload unavailable(ServerPlayer player, ProductionRequestPayload request, String feedback) {
        return new ProductionSnapshotPayload(player.serverLevel().dimension().location(), request.target(), request.request(), false,
                null, "", false, feedback, feedback, null, 0, List.of(), List.of(), java.util.Map.of());
    }

    public static ProductionSnapshotPayload.Legacy legacy(ServerLevel level, UUID worker, CompoundTag state, int activeBottles) {
        if (!BrewingWorker.hasOrder(state)) return null;
        var item = ResourceLocation.tryParse(state.getString("BrewingTargetItem"));
        var potion = ResourceLocation.tryParse(state.getString("BrewingTargetPotion"));
        boolean continuous = state.getBoolean("BrewingContinuous");
        int amount = continuous ? 1 : state.getInt("BrewingRemaining");
        if (item == null || potion == null || amount < 1 || amount > HiredProductionOrder.MAX_AMOUNT
                || com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog.find(level, item, potion).isEmpty()) return null;
        return new ProductionSnapshotPayload.Legacy(worker, HiredProductionRecipe.brewing(item, potion), amount, continuous, activeBottles);
    }

    public static int activeBottles(ServerLevel level, CompoundTag state) {
        if (!state.contains("BrewingCachedStandPos", Tag.TAG_LONG)) return 0;
        var position = BlockPos.of(state.getLong("BrewingCachedStandPos"));
        if (!level.hasChunkAt(position)) return -1;
        return level.getBlockEntity(position) instanceof BrewingStandBlockEntity stand ? BrewingWorker.loadedBottleCount(stand) : 0;
    }

    public static String title(String value) {
        return value.length() > 128 ? value.substring(0, 128) : value;
    }
}
