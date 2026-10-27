package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class SettlementPeekClientChecks {
    private SettlementPeekClientChecks() {
    }

    static void verify(Minecraft minecraft, SettlementViewScreen view, BlockPos center) {
        var level = minecraft.level;
        var bounds = new SettlementViewBounds(center.offset(-9, -1, -4), center.offset(-2, 5, 4));
        var chunks = new ArrayList<ChunkPos>();
        for (int x = (center.getX() - 16) >> 4; x <= (center.getX() + 16) >> 4; x++) {
            for (int z = (center.getZ() - 16) >> 4; z <= (center.getZ() + 16) >> 4; z++) chunks.add(new ChunkPos(x, z));
        }
        var window = new SettlementViewWindow(center, 32, center.getX(), center.getZ(), 8, chunks);
        var roof = center.offset(-5, 4, 1);
        Vec3 start = roof.above(12).getCenter();
        Vec3 end = roof.below(12).getCenter();
        var ray = new SettlementSelectionGizmo.Ray(start, end.subtract(start));
        var peek = new SettlementViewPeek();
        peek.update(level, window, List.of(bounds), ray);
        if (!DialogueCameraOcclusion.supportsMasking()) {
            require(!DialogueCameraOcclusion.hides(roof), "unsupported renderers keep the roof visible");
            peek.clear();
            return;
        }
        require(DialogueCameraOcclusion.hides(roof), "hovering a registered roof publishes the cutaway mask");
        require(!DialogueCameraOcclusion.hides(roof.atY(center.getY() - 1)), "the supporting floor stays visible");
        var visible = SettlementViewPeek.clip(level, start, end, ClipContext.Block.OUTLINE, minecraft.player);
        require(visible.getType() != HitResult.Type.MISS && visible.getBlockPos().getY() < roof.getY(),
                "picking reaches the visible interior through the hidden roof");
        require(!level.getBlockState(roof).isAir(), "render masking preserves the actual world blocks");
        var masked = DialogueCameraOcclusion.snapshot(level);
        if (!net.neoforged.fml.ModList.get().isLoaded("sodium")) {
            var region = new net.minecraft.client.renderer.chunk.RenderRegionCache()
                    .createRegion(minecraft.level, net.minecraft.core.SectionPos.of(roof));
            require(region != null && region.getBlockState(roof).isAir(), "terrain mesh snapshots omit the hidden roof");
        }
        DialogueCameraOcclusion.clear();
        require(DialogueCameraOcclusion.hides(roof), "dialogue cleanup does not clear a settlement mask");
        peek.update(level, window, List.of(bounds), ray);
        require(DialogueCameraOcclusion.hides(roof), "repeated hovering does not descend through the floor");
        peek.update(level, window, List.of(), ray);
        require(!DialogueCameraOcclusion.hides(roof), "an unregistered target restores its roof");
        peek.update(level, window, List.of(bounds), ray);
        peek.clear();
        require(!DialogueCameraOcclusion.hides(roof), "closing peek restores masked sections");
        DialogueCameraOcclusion.built(net.minecraft.core.SectionPos.of(roof), masked);
        DialogueCameraOcclusion.tick();
        require(!DialogueCameraOcclusion.hides(roof), "stale terrain mesh completions cannot restore a closed cutaway");
        verifyScreenHover(minecraft, view, center);
    }

    private static void verifyScreenHover(Minecraft minecraft, SettlementViewScreen view, BlockPos center) {
        boolean focused = minecraft.isWindowActive();
        minecraft.setWindowActive(true);
        try {
            var hover = SettlementViewScreen.class.getDeclaredMethod("updatePeek", double.class, double.class);
            var world = SettlementViewScreen.class.getDeclaredMethod("worldContains", double.class, double.class);
            hover.setAccessible(true);
            world.setAccessible(true);
            var camera = minecraft.gameRenderer.getMainCamera();
            double tangent = Math.tan(Math.toRadians(SettlementViewClient.FOV / 2));
            for (int x = -7; x <= -4; x++) {
                for (int z = -2; z <= 2; z++) {
                    var roof = center.offset(x, 4, z);
                    var relative = roof.getCenter().subtract(camera.getPosition());
                    double depth = relative.dot(new Vec3(camera.getLookVector()));
                    if (depth <= 0) continue;
                    double sx = view.viewportWidth() / 2.0 - relative.dot(new Vec3(camera.getLeftVector())) * view.height / (2 * depth * tangent);
                    double sy = view.height / 2.0 - relative.dot(new Vec3(camera.getUpVector())) * view.height / (2 * depth * tangent);
                    if (!(Boolean) world.invoke(view, sx, sy)) continue;
                    hover.invoke(view, sx, sy);
                    require(DialogueCameraOcclusion.hides(roof), "the rendered building under the cursor activates the screen's registered-building cutaway");
                    hover.invoke(view, 0.0, 0.0);
                    require(!DialogueCameraOcclusion.hides(roof), "moving the cursor onto the screen chrome restores the roof");
                    hover.invoke(view, sx, sy);
                    minecraft.setWindowActive(false);
                    hover.invoke(view, sx, sy);
                    require(!DialogueCameraOcclusion.hides(roof), "an unfocused view restores the roof");
                    return;
                }
            }
            throw new IllegalStateException("the fixture roof must have a visible hover target outside the floating windows");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        } finally {
            view.clearPeek();
            minecraft.setWindowActive(focused);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
