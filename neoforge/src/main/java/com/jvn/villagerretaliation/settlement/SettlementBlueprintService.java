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
