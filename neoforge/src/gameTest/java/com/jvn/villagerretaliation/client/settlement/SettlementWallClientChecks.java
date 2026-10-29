package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementWallGeometry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

final class SettlementWallClientChecks {
    private final SettlementViewScreen view;
    private final BlockPos start;
    private final Consumer<String> capture;
    private final Map<BlockPos, BlockState> before = new LinkedHashMap<>(), targets = new LinkedHashMap<>();
    private int phase, ticks;
    private double cameraX, cameraZ;

    SettlementWallClientChecks(SettlementViewScreen view, BlockPos center, Consumer<String> capture) {
        this.view = view; this.start = center.offset(1, 0, 20); this.capture = capture;
    }
    boolean tick(Minecraft minecraft) {
        if (++this.ticks > 600) throw new IllegalStateException("Wall client checks timed out at " + this.phase + ": " + this.view.wallEditor().hint());
        var editor = this.view.wallEditor();
        if (this.phase == 0) {
            minecraft.getSingleplayerServer().submit(() -> {
                var level = minecraft.getSingleplayerServer().overworld();
                for (var pos : java.util.List.of(this.start.east(12), this.start.east(12).above())) {
                    this.before.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }).join();
            this.cameraX = this.view.focusX(); this.cameraZ = this.view.focusZ();
            this.view.focus(this.start.getX() + 12, this.start.getZ());
            this.view.mouseScrolled(this.view.viewportWidth() / 2.0, 80, 0, -10);
            next();
        } else if (this.phase == 1 && this.ticks > 30) {
            open();
            this.capture.accept("wall-placement-start");
            click(Vec3.atBottomCenterOf(this.start).add(0, -0.02, 0));
            click(Vec3.atBottomCenterOf(this.start.east(24)).add(0, -0.02, 0));
            require(editor.draft().wall().nodes().size() == 2, "ground clicks create two tower nodes: " + editor.draft().wall().nodes());
            button("Auto towers: off").onPress();
            var slider = slider();
            require(slider.active, "auto towers enables spacing control");
            double x = slider.getX() + 4 + 2.0 / 58 * (slider.getWidth() - 8);
            this.view.mouseClicked(x, slider.getY() + 8, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            this.view.mouseReleased(x, slider.getY() + 8, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(editor.draft().spacing() == 8, "slider selects tower spacing: " + editor.draft().spacing());
            require(SettlementWallGeometry.towers(editor.draft().wall()).size() == 4, "preview distributes two extra towers between endpoints");
            button("Undo").onPress();
            require(editor.draft().spacing() == 16, "spacing changes are undoable");
            button("Redo").onPress();
            var kept = editor.draft().wall();
            this.view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(editor.draft().wall().equals(kept) && !editor.draft().extending(), "Escape keeps the wall and stops extending");
            int y = editor.draft().wall().nodes().getLast().getY();
            edit("Height +");
            require(editor.draft().wall().nodes().getLast().getY() == y + 1, "height control raises selected node");
            edit("Height -");
            edit("Height: ground");
            require(editor.draft().level(), "level placement can be selected");
            edit("Height: level");
            for (String choice : java.util.List.of("Default", "Low stone", "Tall stone", "None")) edit("Tower: " + choice);
            require(editor.draft().wall().nodeTowers().getLast() == com.jvn.villagerretaliation.settlement.SettlementWall.Tower.DEFAULT,
                    "all existing tower options can be selected");
            edit("Snap: on"); require(!editor.draft().snapping(), "snapping can be disabled");
            edit("Snap: off");
            edit("Extend");
            click(Vec3.atBottomCenterOf(editor.draft().wall().nodes().getFirst()).add(0, 0.15, 0));
            require(editor.draft().wall().nodes().getLast().equals(editor.draft().wall().nodes().getFirst()), "click connects to another preview node");
            button("Undo").onPress();
            this.view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            next();
        } else if (this.phase == 2 && editor.canConfirm()) {
            require(editor.ghost() != null && !editor.cells().isEmpty(), "wall preview renders real template blocks");
            var ghost = editor.ghost();
            editor.tick(minecraft.level, java.util.UUID.randomUUID(), pos -> true, request -> {});
            require(editor.ghost() == ghost, "unchanged wall previews retain their render snapshot");
            var skipped = editor.cells().stream().filter(cell -> cell.kind() == com.jvn.villagerretaliation.settlement.SettlementWallTerrain.Kind.SKIPPED)
                    .map(com.jvn.villagerretaliation.network.SettlementWallResultPayload.Cell::position).collect(java.util.stream.Collectors.toSet());
            require(!skipped.isEmpty() && editor.hint().contains(" skipped"), "terrain overlaps are confirmable and counted");
            require(editor.ghost().blocks().stream().noneMatch(block -> skipped.contains(editor.ghost().origin().offset(block.localPos()))),
                    "skipped terrain has no wall ghost");
            this.capture.accept("wall-low-stone-spacing");
            button("Low stone").onPress();
            next();
        } else if (this.phase == 3 && editor.canConfirm() && this.ticks > 5) {
            this.capture.accept("wall-tall-stone");
            button("Tall stone").onPress();
            next();
        } else if (this.phase == 4 && editor.canConfirm() && this.ticks > 5) {
            this.capture.accept("wall-timber-reinforced");
            button("Timber reinforced").onPress();
            var original = editor.draft().wall().nodes().getLast();
            drag(Vec3.atBottomCenterOf(original).add(0, 0.15, 0), Vec3.atBottomCenterOf(original.south()).add(0, 0.15, 0));
            require(!editor.draft().wall().nodes().getLast().equals(original), "tower nodes can be dragged");
            button("Undo").onPress();
            require(editor.draft().wall().nodes().getLast().equals(original), "undo restores moved tower");
            button("Auto towers: on").onPress();
            require(SettlementWallGeometry.towers(editor.draft().wall()).size() == 2, "turning auto towers off removes generated towers");
            button("Auto towers: off").onPress();
            next();
        } else if (this.phase == 5 && editor.canConfirm()) {
            var cells = java.util.List.copyOf(editor.cells());
            minecraft.getSingleplayerServer().submit(() -> {
                var level = minecraft.getSingleplayerServer().overworld();
                for (var cell : cells) {
                    this.before.putIfAbsent(cell.position(), level.getBlockState(cell.position()));
                    this.targets.put(cell.position(), cell.kind() == com.jvn.villagerretaliation.settlement.SettlementWallTerrain.Kind.SKIPPED
                            ? level.getBlockState(cell.position()) : cell.state());
                }
            }).join();
            this.capture.accept("wall-confirmation");
            button("Confirm").onPress();
            require(editor.confirming(), "wall confirmation waits for the server");
            next();
        } else if (this.phase == 6 && !editor.active() && this.ticks > 8) {
            minecraft.getSingleplayerServer().submit(() -> {
                var level = minecraft.getSingleplayerServer().overworld();
                this.targets.forEach((pos, state) -> require(level.getBlockState(pos).equals(state), "confirmed wall matches its block preview"));
            }).join();
            open();
            require(editor.draft().automatic() && editor.draft().spacing() == 8, "tower distribution survives confirmation");
            require(editor.draft().wall().nodes().isEmpty() && !editor.draft().canUndo(), "confirmed wall history is discarded");
            button("Cancel").onPress();
            require(!editor.active(), "Cancel exits the wall tool");
            open("Upgrade wall");
            require(editor.pickingUpgrade(), "upgrade action begins wall selection");
            var pick = this.targets.entrySet().stream().filter(entry -> !entry.getValue().isAir()
                    && entry.getKey().getX() == this.start.getX() && entry.getKey().getZ() == this.start.getZ())
                    .max(java.util.Comparator.comparingInt(entry -> entry.getKey().getY())).orElseThrow().getKey();
            click(pick.getCenter());
            next();
        } else if (this.phase == 7 && editor.upgrading() && editor.canConfirm()) {
            require(editor.draft().wall().nodes().size() == 2, "selecting placed wall restores its route");
            button("Low stone").onPress();
            next();
        } else if (this.phase == 8 && editor.canConfirm()) {
            this.capture.accept("wall-upgrade-preview");
            var cells = java.util.List.copyOf(editor.cells());
            minecraft.getSingleplayerServer().submit(() -> {
                var level = minecraft.getSingleplayerServer().overworld();
                this.targets.clear();
                for (var cell : cells) {
                    this.before.putIfAbsent(cell.position(), level.getBlockState(cell.position()));
                    this.targets.put(cell.position(), cell.kind() == com.jvn.villagerretaliation.settlement.SettlementWallTerrain.Kind.SKIPPED
                            ? level.getBlockState(cell.position()) : cell.state());
                }
            }).join();
            button("Confirm upgrade").onPress();
            next();
        } else if (this.phase == 9 && !editor.active() && this.ticks > 8
                && this.targets.entrySet().stream().allMatch(entry -> minecraft.level.getBlockState(entry.getKey()).equals(entry.getValue()))) {
            minecraft.getSingleplayerServer().submit(() -> {
                var level = minecraft.getSingleplayerServer().overworld();
                this.targets.forEach((pos, state) -> require(level.getBlockState(pos).equals(state), "upgrade matches the preview"));
            }).join();
            open("Destroy wall");
            require(editor.pickingUpgrade() && editor.destroying(), "Destroy begins piece selection");
            var pick = this.targets.entrySet().stream().filter(entry -> !entry.getValue().isAir()
                    && entry.getKey().getX() == this.start.getX() && entry.getKey().getZ() == this.start.getZ())
                    .max(java.util.Comparator.comparingInt(entry -> entry.getKey().getY())).orElseThrow().getKey();
            click(pick.getCenter());
            next();
        } else if (this.phase == 10 && editor.destroying() && editor.canConfirm()) {
            require(editor.cells().stream().allMatch(cell -> cell.state().isAir()), "destroy preview only removes recorded blocks");
            this.capture.accept("wall-destroy-preview");
            this.targets.clear(); editor.cells().forEach(cell -> this.targets.put(cell.position(), cell.state()));
            button("Confirm destroy").onPress();
            next();
        } else if (this.phase == 11 && !editor.active()) {
            minecraft.getSingleplayerServer().submit(() -> {
                var level = minecraft.getSingleplayerServer().overworld();
                this.targets.forEach((pos, state) -> require(level.getBlockState(pos).equals(state), "destroy matches its removal preview"));
                this.before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
            }).join();
            this.view.focus(this.cameraX, this.cameraZ);
            this.view.mouseScrolled(this.view.viewportWidth() / 2.0, 80, 0, 100);
            next();
        } else if (this.phase == 12 && this.ticks > 20) return true;
        return false;
    }
    private void next() { this.phase++; this.ticks = 0; }
    private void open() { open("Wall"); }
    private void open(String label) {
        button("Build").onPress();
        require(this.view.contextPopup().buttons().stream().noneMatch(button -> button.getMessage().getString().equals("Branch")),
                "Branch is removed from the Build menu");
        var action = this.view.contextPopup().buttons().stream().filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
        this.view.mouseClicked(action.getX() + 2, action.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.view.mouseReleased(action.getX() + 2, action.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(this.view.wallEditor().active(), "Build opens the Wall tool");
    }
    private void edit(String label) {
        button("Edit").onPress();
        var action = this.view.contextPopup().buttons().stream().filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
        require(action.active, "wall edit action is available: " + label);
        this.view.mouseClicked(action.getX() + 2, action.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.view.mouseReleased(action.getX() + 2, action.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private Button button(String label) {
        return this.view.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && button.getMessage().getString().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing wall control: " + label));
    }
    private SettlementWallSpacingSlider slider() {
        return this.view.children().stream().filter(SettlementWallSpacingSlider.class::isInstance)
                .map(SettlementWallSpacingSlider.class::cast).filter(slider -> slider.visible).findFirst().orElseThrow();
    }
    private Vec3 project(Vec3 position) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var relative = position.subtract(camera.getPosition());
        double distance = relative.dot(new Vec3(camera.getLookVector()));
        double scale = this.view.height / (2 * distance * Math.tan(Math.toRadians(SettlementViewClient.FOV / 2)));
        return new Vec3(this.view.viewportWidth() / 2.0 - relative.dot(new Vec3(camera.getLeftVector())) * scale,
                this.view.height / 2.0 - relative.dot(new Vec3(camera.getUpVector())) * scale, 0);
    }
    private void click(Vec3 world) {
        var point = project(world);
        this.view.mouseClicked(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.view.mouseReleased(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private void drag(Vec3 from, Vec3 to) {
        var a = project(from); var b = project(to);
        this.view.mouseClicked(a.x, a.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.view.mouseDragged(b.x, b.y, GLFW.GLFW_MOUSE_BUTTON_LEFT, b.x - a.x, b.y - a.y);
        this.view.mouseReleased(b.x, b.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
