package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

final class SettlementViewPeek {
    private Level level;
    private SettlementInteriorCutaway.Room room;
    private long nextValidation;
    private SettlementViewBounds target;
    private BlockPos targetHit;

    void update(Level level, SettlementViewWindow window, List<SettlementViewBounds> candidates, SettlementSelectionGizmo.Ray ray) {
        if (this.level != level) clear();
        this.level = level;
        if (!DialogueCameraOcclusion.supportsMasking()) {
            clear();
            return;
        }
        var hit = level.clip(new ClipContext(ray.origin(), ray.end(), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        var bounds = hit.getType() == HitResult.Type.MISS ? null : candidates.stream()
                .filter(candidate -> candidate.box().contains(hit.getBlockPos().getCenter()))
                .min(Comparator.comparingLong(SettlementViewBounds::volume)).orElse(null);
        if (bounds == null) {
            clear();
            return;
        }
        long now = Util.getMillis();
        boolean sameRoom = this.room != null && this.room.bounds().equals(bounds)
                && (this.room.roof().contains(hit.getBlockPos())
                    || this.room.walls().stream().anyMatch(wall -> wall.position().equals(hit.getBlockPos())));
        boolean sameTarget = bounds.equals(this.target) && hit.getBlockPos().equals(this.targetHit);
        if (!sameRoom && !sameTarget || now >= this.nextValidation) {
            if (!sameRoom || !this.room.unchanged(level, window::contains))
                this.room = SettlementInteriorCutaway.detect(level, bounds, hit.getBlockPos(), window::contains);
            this.nextValidation = now + 200;
            this.target = bounds;
            this.targetHit = hit.getBlockPos();
        }
        DialogueCameraOcclusion.settlementMask(this.room == null ? Set.of() : this.room.mask(ray.origin()));
    }

    void clear() {
        this.level = null;
        this.room = null;
        this.nextValidation = 0;
        this.target = null;
        this.targetHit = null;
        DialogueCameraOcclusion.settlementMask(Set.of());
    }

    static BlockHitResult clip(Level level, Vec3 start, Vec3 end, ClipContext.Block mode, Entity entity) {
        var context = new ClipContext(start, end, mode, ClipContext.Fluid.NONE, entity);
        return BlockGetter.traverseBlocks(start, end, context, (ctx, pos) -> {
            if (DialogueCameraOcclusion.hides(pos)) return null;
            var state = level.getBlockState(pos);
            return level.clipWithInteractionOverride(start, end, pos, ctx.getBlockShape(state, level, pos), state);
        }, ctx -> BlockHitResult.miss(end, Direction.getNearest(start.x - end.x, start.y - end.y, start.z - end.z), BlockPos.containing(end)));
    }
}
