package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;

public final class SettlementBlueprintService {
    private SettlementBlueprintService() {
    }

    public static ItemStack preview(ServerPlayer player, SettlementRecord town, ResourceLocation structure) {
        if (!canPlan(player, town)) return ItemStack.EMPTY;
        var entry = BuilderStructureCatalog.byId(player.server, structure).orElse(null);
        var plan = entry == null ? null : BuilderStructureScanner.scan(player.serverLevel(), entry, Rotation.NONE).orElse(null);
        if (plan == null) return ItemStack.EMPTY;
        return ConstructionBlueprintItem.create(player.serverLevel(), null, plan, origin(plan, town.center()),
                UUID.randomUUID(), 0, 0, "", 0L);
    }

    public static String order(ServerPlayer player, SettlementRecord town, ResourceLocation structure, BlockPos anchor, Rotation rotation) {
        return order(player, town, structure, anchor, rotation, true);
    }

    public static String order(ServerPlayer player, SettlementRecord town, ResourceLocation structure, BlockPos anchor, Rotation rotation, boolean confirm) {
        if (player == null || town == null || !player.isAlive() || player.isSpectator()
                || !town.dimension().equals(player.level().dimension().location())
                || !town.permits(player.getUUID(), SettlementPermission.BUILD)) return "Build permission is required.";
        if (town.stage().projects() == 0) return "Advance the settlement to Hamlet to unlock construction.";
        var entry = BuilderStructureCatalog.byId(player.server, structure).orElse(null);
        var plan = entry == null || rotation == null ? null : BuilderStructureScanner.scan(player.serverLevel(), entry, rotation).orElse(null);
        if (plan == null) return "This structure is unavailable or exceeds the construction size limit.";
        var origin = origin(plan, anchor);
        if (!within(player, town, plan, origin)) return "Move the entire footprint inside the village and world bounds.";
        var min = plan.worldMin(origin);
        var max = plan.worldMax(origin);
        long width = (long) max.getX() - min.getX() + 9;
        long height = (long) max.getY() - min.getY() + 6;
        long depth = (long) max.getZ() - min.getZ() + 9;
        if (width > 128 || height > 64 || depth > 128 || width * height * depth > SettlementArea.MAX_VOLUME)
            return "This structure is too large for a construction site.";
        var level = player.serverLevel();
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return "Load every chunk under the footprint before ordering.";
            }
        }
        for (var block : plan.blocks()) {
            var pos = plan.worldPos(origin, block);
            var state = block.state();
            if (state.getBlock() instanceof net.minecraft.world.level.block.GameMasterBlock
                    || state.getDestroySpeed(level, pos) < 0 || state.is(net.minecraft.world.level.block.Blocks.SPAWNER)
                    || state.is(net.minecraft.world.level.block.Blocks.LAVA))
                return "The structure contains a block that village builders cannot safely place.";
            var check = com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner.canReserveAt(level, null, pos, state);
            if (!check.valid()) return "Clear the placement conflict at " + pos.toShortString() + " or choose another site.";
        }
        var project = SettlementProject.queued(town, plan, origin, level.getGameTime());
        if (SettlementSavedData.get(level).settlements().stream().filter(other -> other.dimension().equals(town.dimension()))
                .flatMap(other -> other.projects().values().stream()).anyMatch(existing -> existing.reservesFootprint() && existing.overlaps(project)))
            return "This footprint overlaps another construction order. Move it or cancel that order.";
        if (!confirm) return "";
        if (!town.queueProject(player.getUUID(), project)) return "The construction queue is full. Cancel an order before adding another.";
        SettlementConstructionSiteService.observeProject(level, town, project, null);
        SettlementConstructionQueue.dispatch(level, town);
        return "";
    }

    public static boolean draft(ServerPlayer player, SettlementRecord town, ResourceLocation structure) {
        if (!canPlan(player, town)) {
            return false;
        }
        var entry = BuilderStructureCatalog.byId(player.server, structure).orElse(null);
        var plan = entry == null ? null : BuilderStructureScanner.scan(player.serverLevel(), entry, Rotation.NONE).orElse(null);
        if (plan == null) {
            return false;
        }
        BlockPos origin = origin(plan, town.center());
        if (!within(player, town, plan, origin)) {
            return false;
        }
        int paper = -1;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.PAPER)) {
                paper = slot;
                break;
            }
        }
        if (paper < 0) {
            return false;
        }
        ItemStack blueprint = ConstructionBlueprintItem.create(player.serverLevel(), null, plan, origin,
                UUID.randomUUID(), 0, plan.price(), "", 0L);
        player.getInventory().removeItem(paper, 1);
        if (!player.getInventory().add(blueprint)) {
            player.drop(blueprint, false);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    public static boolean place(ServerPlayer player, SettlementRecord town, UUID blueprintId, BlockPos anchor, Rotation rotation) {
        if (!canPlan(player, town)) {
            return false;
        }
        ItemStack blueprint = ItemStack.EMPTY;
        ConstructionBlueprintItem.PreviewData preview = null;
        for (var stack : player.getHandSlots()) {
            var candidate = ConstructionBlueprintItem.previewData(stack).orElse(null);
            if (candidate != null && candidate.jobId().equals(blueprintId)) {
                blueprint = stack;
                preview = candidate;
                break;
            }
        }
        if (preview == null || preview.locked() || preview.placementLocked() || !preview.dimension().equals(player.level().dimension())
                || SettlementSavedData.get(player.serverLevel()).settlements().stream().anyMatch(other -> other.projects().containsKey(blueprintId))) {
            return false;
        }
        var entry = BuilderStructureCatalog.byId(player.server, preview.structureId()).orElse(null);
        var plan = entry == null ? null : BuilderStructureScanner.scan(player.serverLevel(), entry, rotation).orElse(null);
        if (plan == null) {
            return false;
        }
        BlockPos origin = origin(plan, anchor);
        if (!within(player, town, plan, origin)) {
            return false;
        }
        ConstructionBlueprintItem.updatePlacement(blueprint, player.serverLevel(), plan, origin, rotation);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    private static BlockPos origin(BuilderStructureScanner.StructurePlan plan, BlockPos anchor) {
        return anchor.offset(-plan.localCenter().getX(), -plan.localMin().getY(), -plan.localCenter().getZ());
    }

    private static boolean within(ServerPlayer player, SettlementRecord town, BuilderStructureScanner.StructurePlan plan, BlockPos origin) {
        BlockPos min = origin.offset(plan.localMin());
        BlockPos max = origin.offset(plan.localMax());
        var level = player.serverLevel();
        return SettlementGuardOrder.within(town, min) && SettlementGuardOrder.within(town, max)
                && !level.isOutsideBuildHeight(min) && !level.isOutsideBuildHeight(max)
                && level.getWorldBorder().isWithinBounds(min) && level.getWorldBorder().isWithinBounds(max);
    }

    private static boolean canPlan(ServerPlayer player, SettlementRecord town) {
        return player != null && town != null && player.isAlive() && !player.isSpectator()
                && town.dimension().equals(player.level().dimension().location()) && town.stage().projects() > 0
                && town.permits(player.getUUID(), SettlementPermission.BUILD);
    }
}
