package com.jvn.villagerretaliation.client.settlement;

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

final class SettlementInfrastructureClientChecks {
    private final SettlementViewScreen view;
    private final BlockPos start;
    private final Consumer<String> capture;
    private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
    private int phase, ticks;

    SettlementInfrastructureClientChecks(SettlementViewScreen view, BlockPos center, Consumer<String> capture) {
        this.view = view;
        this.start = center.offset(1, -1, 14);
        this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        if (this.phase == 6) return true;
        if (++this.ticks > 600) throw new IllegalStateException("Infrastructure client checks timed out at " + this.phase + ": " + this.view.infrastructureEditor().hint());
        var editor = this.view.infrastructureEditor();
        if (this.phase == 0) {
            minecraft.getSingleplayerServer().submit(() -> {
                var level = minecraft.getSingleplayerServer().overworld();
                for (var pos : BlockPos.betweenClosed(this.start.offset(-2, -1, -2), this.start.offset(16, 5, 10))) {
                    this.before.put(pos.immutable(), level.getBlockState(pos));
                    level.setBlock(pos, pos.getY() <= this.start.getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }).join();
            this.view.focus(this.start.getX() + 5, this.start.getZ() + 3);
            this.view.mouseScrolled(this.view.viewportWidth() / 2.0, 80, 0, -10);
            next();
        } else if (this.phase == 1 && this.ticks > 30) {
            open("Bridge");
            click(this.start);
            click(this.start.east(8));
            next();
        } else if (this.phase == 2 && this.ticks > 10 && editor.canConfirm()) {
            require(editor.cells().stream().anyMatch(cell -> cell.state().is(Blocks.OAK_FENCE)), "bridge preview includes rails");
            require(editor.ghost(minecraft.level) != null, "bridge has a rendered construction preview");
            this.capture.accept("infrastructure-bridge");
            button("Cancel").onPress();
            require(!editor.active(), "cancel closes the bridge draft");
            open("Animal pen");
            click(this.start);
            click(this.start.offset(6, 0, 6));
            next();
        } else if (this.phase == 3 && this.ticks > 10 && editor.canConfirm()) {
            require(editor.cells().stream().anyMatch(cell -> cell.state().is(Blocks.OAK_FENCE_GATE)), "pen preview includes its gate");
            require(editor.cells().stream().anyMatch(cell -> cell.position().getY() == this.start.getY() + 4), "pen preview includes a shelter roof");
            this.capture.accept("infrastructure-animal-pen");
            var a = project(this.start.offset(6, 0, 6));
            var b = project(this.start.offset(8, 0, 8));
            this.view.mouseClicked(a.x, a.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            this.view.mouseDragged(b.x, b.y, GLFW.GLFW_MOUSE_BUTTON_LEFT, b.x - a.x, b.y - a.y);
            this.view.mouseReleased(b.x, b.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(!editor.canConfirm(), "resizing invalidates the previous approval");
            next();
        } else if (this.phase == 4 && this.ticks > 10 && editor.canConfirm()) {
            this.capture.accept("infrastructure-pen-resized");
            button("Cancel").onPress();
            minecraft.getSingleplayerServer().submit(() -> {
                var level = minecraft.getSingleplayerServer().overworld();
                require(level.getBlockState(this.start.east(4)).is(Blocks.DIRT), "previews never construct blocks");
                this.before.forEach((pos, state) -> level.setBlock(pos, state, 2));
            }).join();
            next();
        } else if (this.phase == 5 && this.ticks > 20) next();
        return this.phase == 6;
    }

    private void open(String label) {
        button("Build").onPress();
        for (String name : new String[]{"Bridge", "Stairs", "Animal pen", "Clear land", "Level land", "Fill land", "Repair wall"})
            require(this.view.contextPopup().buttons().stream().anyMatch(button -> button.getMessage().getString().equals(name)),
                    name + " is present in the Build menu");
        var action = this.view.contextPopup().buttons().stream().filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
        require(action.active, label + " is enabled");
        this.view.mouseClicked(action.getX() + 2, action.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.view.mouseReleased(action.getX() + 2, action.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(this.view.infrastructureEditor().active(), label + " opens the construction tool");
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
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
