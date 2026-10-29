package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementFarmTerrain;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

final class SettlementFarmClientChecks {
    private final SettlementViewScreen view;
    private final BlockPos start;
    private final Consumer<String> capture;
    private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
    private int phase, ticks;
    private double cameraX, cameraZ;

    SettlementFarmClientChecks(SettlementViewScreen view, BlockPos center, Consumer<String> capture) {
        this.view = view;
        this.start = center.offset(1, -1, 14);
        this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        if (this.phase == 7) return true;
        if (++this.ticks > 600) throw new IllegalStateException("Farmland client checks timed out at " + this.phase + ": " + this.view.farmEditor().hint());
        var editor = this.view.farmEditor();
        if (this.phase == 0) {
            minecraft.getSingleplayerServer().submit(() -> {
                var level = minecraft.getSingleplayerServer().overworld();
                for (var pos : BlockPos.betweenClosed(this.start.offset(-1, -1, -1), this.start.offset(18, 3, 9))) {
                    this.before.put(pos.immutable(), level.getBlockState(pos));
                    level.setBlock(pos, pos.getY() <= this.start.getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
                level.setBlock(this.start.east(2), Blocks.STONE.defaultBlockState(), 2);
            }).join();
            this.cameraX = this.view.focusX();
            this.cameraZ = this.view.focusZ();
            this.view.focus(this.start.getX() + 8, this.start.getZ() + 4);
            this.view.mouseScrolled(this.view.viewportWidth() / 2.0, 80, 0, -10);
            next();
        } else if (this.phase == 1 && this.ticks > 30) {
            button("Build").onPress();
            var action = this.view.contextPopup().buttons().stream()
                    .filter(button -> button.getMessage().getString().equals("Farmland")).findFirst().orElseThrow();
            require(action.active, "Farmland is available in the Build menu");
            this.view.mouseClicked(action.getX() + 2, action.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            this.view.mouseReleased(action.getX() + 2, action.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(editor.active(), "Build opens the farmland tool");
            click(this.start);
            next();
        } else if (this.phase == 2 && this.ticks > 3) {
            var point = project(this.start.offset(8, 0, 8));
            this.view.mouseMoved(point.x, point.y);
            click(this.start.offset(8, 0, 8));
            next();
        } else if (this.phase == 3 && this.ticks > 10 && editor.canConfirm()) {
            require(count(SettlementFarmTerrain.Kind.WATER) == 1, "nine-by-nine preview has one water source");
            require(count(SettlementFarmTerrain.Kind.SKIPPED) == 1, "stone is visibly skipped");
            this.capture.accept("farmland-nine-by-nine");
            var a = project(this.start.offset(8, 0, 8));
            var b = project(this.start.offset(17, 0, 8));
            this.view.mouseClicked(a.x, a.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            this.view.mouseDragged(b.x, b.y, GLFW.GLFW_MOUSE_BUTTON_LEFT, b.x - a.x, b.y - a.y);
            this.view.mouseReleased(b.x, b.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(!editor.canConfirm(), "resizing invalidates the old confirmation");
            next();
        } else if (this.phase == 4 && this.ticks > 10 && editor.canConfirm()) {
            require(count(SettlementFarmTerrain.Kind.WATER) == 2, "resizing repositions water to cover the full field");
            this.capture.accept("farmland-resized-irrigation");
            button("Cancel").onPress();
            require(!editor.active(), "cancel clears farmland placement");
            next();
        } else if (this.phase == 5 && this.ticks > 3) {
            minecraft.getSingleplayerServer().submit(() -> {
                var level = minecraft.getSingleplayerServer().overworld();
                require(level.getBlockState(this.start.offset(4, 0, 4)).is(Blocks.DIRT), "preview and cancellation never till terrain");
                this.before.forEach((pos, state) -> level.setBlock(pos, state, 2));
            }).join();
            this.view.focus(this.cameraX, this.cameraZ);
            this.view.mouseScrolled(this.view.viewportWidth() / 2.0, 80, 0, 100);
            next();
        } else if (this.phase == 6 && this.ticks > 30) next();
        return this.phase == 7;
    }

    private long count(SettlementFarmTerrain.Kind kind) {
        return this.view.farmEditor().cells().stream().filter(cell -> cell.kind() == kind).count();
    }
    private void next() { this.phase++; this.ticks = 0; }
    private Button button(String label) {
        return this.view.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private Vec3 project(BlockPos pos) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var relative = Vec3.atBottomCenterOf(pos.above()).subtract(camera.getPosition());
        double distance = relative.dot(new Vec3(camera.getLookVector()));
        double scale = this.view.height / (2 * distance * Math.tan(Math.toRadians(SettlementViewClient.FOV / 2)));
        return new Vec3(this.view.viewportWidth() / 2.0 - relative.dot(new Vec3(camera.getLeftVector())) * scale,
                this.view.height / 2.0 - relative.dot(new Vec3(camera.getUpVector())) * scale, 0);
    }
    private void click(BlockPos pos) {
        var point = project(pos);
        this.view.mouseClicked(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.view.mouseReleased(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
