package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

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
                    .createRegion(minecraft.level, SectionPos.of(roof));
            require(region != null && region.getBlockState(roof).isAir(), "terrain mesh snapshots omit the hidden roof");
        }
        DialogueCameraOcclusion.clear();
        require(DialogueCameraOcclusion.hides(roof), "dialogue cleanup does not clear a settlement mask");
        peek.update(level, window, List.of(bounds), ray);
        require(DialogueCameraOcclusion.hides(roof), "repeated hovering does not descend through the floor");
        peek.update(level, window, List.of(), ray);
        require(DialogueCameraOcclusion.hides(roof), "an unregistered target fades back before restoring its roof");
        peek.update(level, window, List.of(bounds), ray);
        peek.clear();
        require(!DialogueCameraOcclusion.hides(roof), "closing peek restores masked sections");
        DialogueCameraOcclusion.built(SectionPos.of(roof), masked);
        DialogueCameraOcclusion.tick();
        require(!DialogueCameraOcclusion.hides(roof), "stale terrain mesh completions cannot restore a closed cutaway");
        verifyFade(minecraft, roof);
        verifyDither(minecraft);
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
                    require(!DialogueCameraOcclusion.hides(roof), "hovering alone does not activate peeking");
                    view.keyPressed(GLFW.GLFW_KEY_GRAVE_ACCENT, 0, 0);
                    hover.invoke(view, sx, sy);
                    require(DialogueCameraOcclusion.hides(roof), "the rendered building under the cursor activates the screen's registered-building cutaway");
                    verifyInteriorClicks(minecraft, view, center, world, hover);
                    hover.invoke(view, 0.0, 0.0);
                    require(DialogueCameraOcclusion.hides(roof), "leaving the world starts a smooth restoration");
                    view.clearPeek();
                    view.keyReleased(GLFW.GLFW_KEY_GRAVE_ACCENT, 0, 0);
                    hover.invoke(view, sx, sy);
                    require(!DialogueCameraOcclusion.hides(roof), "releasing the key stops manual peeking");
                    var mapping = SettlementViewKeyMappings.PEEK;
                    var original = mapping.getKey();
                    try {
                        mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));
                        view.keyPressed(GLFW.GLFW_KEY_GRAVE_ACCENT, 0, 0);
                        hover.invoke(view, sx, sy);
                        require(!DialogueCameraOcclusion.hides(roof), "the previous binding no longer activates peeking");
                        view.keyPressed(GLFW.GLFW_KEY_J, 0, 0);
                        hover.invoke(view, sx, sy);
                        require(DialogueCameraOcclusion.hides(roof), "a rebound peek key activates the cutaway");
                        view.keyReleased(GLFW.GLFW_KEY_J, 0, 0);
                    } finally {
                        mapping.setKey(original);
                        view.clearControls();
                        view.clearPeek();
                    }
                    verifyEntityFocus(minecraft, view, center, hover, world);
                    view.keyPressed(GLFW.GLFW_KEY_GRAVE_ACCENT, 0, 0);
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
            view.clearControls();
            view.clearPeek();
            minecraft.setWindowActive(focused);
        }
    }

    private static void verifyInteriorClicks(Minecraft minecraft, SettlementViewScreen view, BlockPos center,
            Method world, Method hover) throws ReflectiveOperationException {
        var selected = SettlementViewScreen.class.getDeclaredField("selectedBlock");
        selected.setAccessible(true);
        var camera = minecraft.gameRenderer.getMainCamera();
        for (int x = -7; x <= -4; x++) {
            for (int z = -2; z <= 2; z++) {
                var target = center.offset(x, -1, z).getCenter().add(0, 0.51, 0);
                var point = project(view, target);
                if (!(Boolean) world.invoke(view, point.x, point.y)) continue;
                var ray = new SettlementSelectionGizmo.Ray(camera.getPosition(), target.subtract(camera.getPosition()));
                var exterior = minecraft.level.clip(new ClipContext(ray.origin(), ray.end(),
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
                if (!DialogueCameraOcclusion.hides(exterior.getBlockPos())) continue;
                var interior = SettlementViewPeek.clip(minecraft.level, ray.origin(), ray.end(), ClipContext.Block.OUTLINE, minecraft.player);
                if (interior.getType() == HitResult.Type.MISS || interior.getBlockPos().getY() != center.getY() - 1) continue;
                view.mouseClicked(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                view.mouseReleased(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                require(interior.getBlockPos().equals(selected.get(view)), "left-clicking through the cutaway selects the interior block");
                require(view.inspectorSelection() == null, "the enclosing building area cannot override an interior block selection");
                view.mouseClicked(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                view.mouseReleased(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                require(view.contextPopup() == null, "right-clicking through the cutaway does not open the enclosing building's actions");
                view.clearPeek();
                view.keyPressed(GLFW.GLFW_KEY_GRAVE_ACCENT, 0, 0);
                view.mouseClicked(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                view.mouseReleased(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                require(interior.getBlockPos().equals(selected.get(view)), "a click refreshes the cutaway before the next hover frame");
                view.keyReleased(GLFW.GLFW_KEY_GRAVE_ACCENT, 0, 0);
                view.clearPeek();
                view.mouseClicked(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                view.mouseReleased(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                require(exterior.getBlockPos().equals(selected.get(view)) && view.inspectorSelection() instanceof SettlementManagementPayload.Area,
                        "normal building selection still targets the exterior when not peeking");
                view.keyPressed(GLFW.GLFW_KEY_GRAVE_ACCENT, 0, 0);
                hover.invoke(view, point.x, point.y);
                var bed = center.offset(-6, 0, 0);
                var bedPoint = project(view, bed.getCenter());
                require((Boolean) world.invoke(view, bedPoint.x, bedPoint.y), "the fixture bed is clickable outside the floating windows");
                view.mouseClicked(bedPoint.x, bedPoint.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                view.mouseReleased(bedPoint.x, bedPoint.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                require(view.selectedBeds().equals(List.of(bed.north())), "left-clicking through the roof selects the interior bed");
                require(view.contextPopup() != null && view.contextPopup().target().kind() == SettlementViewPopup.Kind.BEDS,
                        "the interior bed retains its assignment actions");
                view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                view.mouseClicked(bedPoint.x, bedPoint.y, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                require(view.contextPopup() != null && view.contextPopup().target().kind() == SettlementViewPopup.Kind.BEDS,
                        "right-clicking through the roof targets the bed rather than the enclosing building");
                view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                view.keyPressed(GLFW.GLFW_KEY_GRAVE_ACCENT, 0, 0);
                hover.invoke(view, point.x, point.y);
                return;
            }
        }
        throw new IllegalStateException("the fixture must have a clickable interior floor outside the floating windows");
    }

    private static Vec3 project(SettlementViewScreen view, Vec3 target) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var offset = target.subtract(camera.getPosition());
        double depth = offset.dot(new Vec3(camera.getLookVector()));
        double scale = view.height / (2 * depth * Math.tan(Math.toRadians(SettlementViewClient.FOV / 2)));
        return new Vec3(view.viewportWidth() / 2.0 - offset.dot(new Vec3(camera.getLeftVector())) * scale,
                view.height / 2.0 - offset.dot(new Vec3(camera.getUpVector())) * scale, 0);
    }

    private static void verifyFade(Minecraft minecraft, BlockPos roof) {
        var fade = new SettlementPeekRenderer();
        var delayed = roof.offset(16, 0, 0);
        var blocks = Set.of(roof, roof.east(), delayed);
        try {
            fade.target(blocks);
            var identity = new Matrix4f();
            var event = new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS,
                    minecraft.levelRenderer, new PoseStack(), identity, identity, 0, minecraft.getTimer(),
                    minecraft.gameRenderer.getMainCamera(), new net.minecraft.client.renderer.culling.Frustum(identity, identity));
            fade.render(event);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "the cached block overlay uploads and renders without OpenGL errors");
            fade.advance(1_000);
            fade.advance(1_200);
            require(fade.opacity(roof) == 1, "the roof stays opaque until its masked mesh uploads");
            var masked = DialogueCameraOcclusion.snapshot(minecraft.level);
            for (var position : blocks) {
                if (!position.equals(delayed)) DialogueCameraOcclusion.built(SectionPos.of(position), masked);
            }
            DialogueCameraOcclusion.tick();
            fade.advance(1_425);
            require(Math.abs(fade.opacity(roof) - 0.5F) < 0.001F, "the dissolve reaches its midpoint gradually");
            require(fade.opacity(delayed) == 1, "an offscreen section cannot hold up the visible section's fade");
            DialogueCameraOcclusion.built(SectionPos.of(delayed), masked);
            DialogueCameraOcclusion.tick();
            fade.advance(1_650);
            require(fade.opacity(roof) == 0, "the completed dissolve leaves a clear interior");
            fade.target(Set.of());
            fade.advance(1_875);
            require(Math.abs(fade.opacity(roof) - 0.5F) < 0.001F && DialogueCameraOcclusion.hides(roof),
                    "release fades the replacement back before unmasking terrain");
            fade.target(blocks);
            require(Math.abs(fade.opacity(roof) - 0.5F) < 0.001F, "re-pressing reverses the fade without jumping");
            fade.advance(2_100);
            require(fade.opacity(roof) == 0, "a reversed fade finishes revealing the interior");
            fade.target(Set.of());
            fade.advance(2_550);
            require(!DialogueCameraOcclusion.hides(roof) && fade.opacity(roof) == 1, "the fully restored replacement hands off to terrain");
            DialogueCameraOcclusion.built(SectionPos.of(roof), masked);
            DialogueCameraOcclusion.tick();
            require(!DialogueCameraOcclusion.settlementMeshesReady(blocks), "stale uploads cannot complete restoration");
            var restored = DialogueCameraOcclusion.snapshot(minecraft.level);
            for (var position : blocks) DialogueCameraOcclusion.built(SectionPos.of(position), restored);
            DialogueCameraOcclusion.tick();
            require(DialogueCameraOcclusion.settlementMeshesReady(blocks), "restoration observes uploads even with an empty mask");
            DialogueCameraOcclusion.built(SectionPos.of(roof), new DialogueCameraOcclusion.Snapshot(null, masked.version(), blocks));
            DialogueCameraOcclusion.tick();
            require(DialogueCameraOcclusion.settlementMeshesReady(blocks), "a late upload from another world cannot invalidate restoration");
            fade.advance(2_600);
        } finally {
            fade.clear();
        }
    }

    private static void verifyEntityFocus(Minecraft minecraft, SettlementViewScreen view, BlockPos center,
            Method hover, Method world) throws ReflectiveOperationException {
        var field = SettlementViewScreen.class.getDeclaredField("roster");
        field.setAccessible(true);
        var roster = (Map<?, ?>) field.get(view);
        for (var entry : roster.values()) {
            if (!(entry instanceof SettlementManagementPayload.Resident resident)) continue;
            Entity entity = null;
            for (var candidate : minecraft.level.entitiesForRendering()) {
                if (candidate.getUUID().equals(resident.id())) entity = candidate;
            }
            if (entity == null) continue;
            var original = entity.position();
            double focusX = view.focusX(), focusZ = view.focusZ();
            try {
                entity.setPos(center.getX() - 5.5, center.getY(), center.getZ() + 1.5);
                view.inspect(resident);
                view.keyPressed(GLFW.GLFW_KEY_F, 0, 0);
                hover.invoke(view, 0.0, 0.0);
                require(DialogueCameraOcclusion.hides(center.offset(-5, 4, 1)),
                        "focusing an obscured resident reveals its registered building without the peek key");
                var point = project(view, entity.getBoundingBox().getCenter());
                require((Boolean) world.invoke(view, point.x, point.y), "the focused resident is clickable outside the floating windows");
                view.mouseClicked(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                view.mouseReleased(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                require(view.selectedResidents().equals(List.of(resident.id())), "an automatic cutaway lets clicks reach the obscured resident");
                view.mouseClicked(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                require(view.contextPopup() != null && view.contextPopup().target().kind() == SettlementViewPopup.Kind.RESIDENT
                        && resident.id().equals(view.contextPopup().target().id()), "right-clicking an automatic cutaway opens the interior resident's actions");
                view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                view.clearPeek();
                entity.setPos(center.getX() + 8.5, center.getY(), center.getZ() + 8.5);
                hover.invoke(view, 0.0, 0.0);
                require(DialogueCameraOcclusion.snapshot(minecraft.level).blocks().isEmpty(),
                        "a focused resident outside a registered building does not open a cutaway");
                entity.setPos(center.getX() - 5.5, center.getY(), center.getZ() + 1.5);
                view.focus(focusX, focusZ);
                hover.invoke(view, 0.0, 0.0);
                require(DialogueCameraOcclusion.snapshot(minecraft.level).blocks().isEmpty(),
                        "moving focus away stops automatic peeking");
            } finally {
                entity.setPos(original);
                view.focus(focusX, focusZ);
                view.clearControls();
                view.clearPeek();
            }
            return;
        }
        throw new IllegalStateException("a fixture resident must be loaded for automatic focus checks");
    }

    private static void verifyDither(Minecraft minecraft) {
        var target = new TextureTarget(32, 32, true, Minecraft.ON_OSX);
        var texture = new DynamicTexture(16, 16, true);
        var mesh = new VertexBuffer(VertexBuffer.Usage.STATIC);
        int texture0 = RenderSystem.getShaderTexture(0);
        int texture2 = RenderSystem.getShaderTexture(2);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        try {
            var field = SettlementPeekRenderer.class.getDeclaredField("shader");
            field.setAccessible(true);
            var shader = (ShaderInstance) field.get(null);
            require(shader != null, "the peek shader compiles and loads");
            texture.getPixels().fillRect(0, 0, 16, 16, -1);
            texture.upload();
            RenderSystem.setShaderTexture(0, texture.getId());
            RenderSystem.setShaderTexture(2, texture.getId());
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            var vertices = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (int[] point : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}})
                vertices.addVertex(point[0], point[1], 0).setColor(-1).setUv(0.5F, 0.5F).setUv2(0, 0).setNormal(0, 0, 1);
            mesh.bind();
            mesh.upload(vertices.buildOrThrow());
            target.setClearColor(0, 0, 0, 0);
            byte[] midpoint = null;
            for (float opacity : new float[]{0, 0.5F, 0.5F, 1}) {
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                shader.safeGetUniform("Opacity").set(opacity);
                mesh.bind();
                mesh.drawWithShader(new Matrix4f(), new Matrix4f(), shader);
                var pixels = BufferUtils.createByteBuffer(32 * 32 * 4);
                GL11.glReadPixels(0, 0, 32, 32, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                var alpha = new byte[32 * 32];
                int visible = 0;
                for (int index = 0; index < alpha.length; index++) {
                    alpha[index] = pixels.get(index * 4 + 3);
                    if (alpha[index] != 0) visible++;
                }
                if (opacity == 0) require(visible == 0, "the dissolved shader writes no pixels");
                else if (opacity == 1) require(visible == alpha.length, "the restored shader covers every pixel");
                else {
                    require(visible > 450 && visible < 575, "the midpoint uses fine pixel coverage rather than disappearing whole blocks");
                    if (midpoint != null) require(java.util.Arrays.equals(midpoint, alpha), "the dither pattern stays stable between frames");
                    midpoint = alpha;
                }
            }
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "the dither shader renders without OpenGL errors");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        } finally {
            VertexBuffer.unbind();
            mesh.close();
            texture.close();
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShaderTexture(0, texture0);
            RenderSystem.setShaderTexture(2, texture2);
            if (depth) RenderSystem.enableDepthTest();
            if (cull) RenderSystem.enableCull();
            if (blend) RenderSystem.enableBlend();
        }
    }

    static final class LiveFade {
        private final SettlementPeekRenderer fade = new SettlementPeekRenderer();
        private final java.util.function.Consumer<RenderLevelStageEvent> render = event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) this.fade.render(event);
        };
        private final java.lang.reflect.Field groups;
        private final BlockPos roof;
        private boolean restoring;
        private int ticks;

        LiveFade(BlockPos roof) {
            this.roof = roof;
            try {
                this.groups = SettlementPeekRenderer.class.getDeclaredField("fades");
                this.groups.setAccessible(true);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
            NeoForge.EVENT_BUS.addListener(this.render);
            this.fade.target(Set.of(roof, roof.east()));
        }

        boolean tick() {
            try {
                require(++this.ticks < 200, "real terrain uploads must complete both directions of the fade");
                if (!this.restoring && this.fade.opacity(this.roof) == 0) {
                    this.fade.target(Set.of());
                    this.restoring = true;
                    this.ticks = 0;
                } else if (this.restoring && ((List<?>) this.groups.get(this.fade)).isEmpty()) {
                    require(!DialogueCameraOcclusion.hides(this.roof), "real terrain uploads restore the roof and release the overlay");
                    close();
                    return true;
                }
                return false;
            } catch (IllegalAccessException exception) {
                close();
                throw new IllegalStateException(exception);
            } catch (RuntimeException exception) {
                close();
                throw exception;
            }
        }

        private void close() {
            NeoForge.EVENT_BUS.unregister(this.render);
            this.fade.clear();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
