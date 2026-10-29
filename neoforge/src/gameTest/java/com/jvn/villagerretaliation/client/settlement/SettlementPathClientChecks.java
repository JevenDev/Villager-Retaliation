package com.jvn.villagerretaliation.client.settlement;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

final class SettlementPathClientChecks {
    private final SettlementViewScreen view;
    private final BlockPos center;
    private final Consumer<String> capture;
    private int phase;
    private int ticks;
    private boolean refreshed;
    private Vec3 start;
    private double cameraX;
    private double cameraZ;

    SettlementPathClientChecks(SettlementViewScreen view, BlockPos center, Consumer<String> capture) {
        this.view = view;
        this.center = center;
        this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        if (this.phase == 9) return true;
        if (++this.ticks > 500) throw new IllegalStateException("Path client checks timed out at " + this.phase + ": " + this.view.pathEditor().hint());
        var editor = this.view.pathEditor();
        if (this.phase == 0) {
            this.cameraX = this.view.focusX();
            this.cameraZ = this.view.focusZ();
            this.view.focus(this.center.getX() + 4, this.center.getZ() + 6);
            require(this.view.children().stream().filter(SettlementViewUi.IconButton.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.visible).noneMatch(button -> button.getMessage().getString().equals("Path") || button.getMessage().getString().equals("Branch")),
                    "path tools are grouped inside Build");
            button("Build").onPress();
            var buildLabels = this.view.contextPopup().buttons().stream().map(button -> button.getMessage().getString()).toList();
            require(buildLabels.containsAll(java.util.List.of("Farmland", "Path", "Wall", "Upgrade wall", "Repair wall", "Destroy wall"))
                    && java.util.Arrays.stream(com.jvn.villagerretaliation.settlement.SettlementInfrastructure.Type.values())
                            .allMatch(type -> buildLabels.contains(type.label())), "Build offers construction, infrastructure and wall maintenance tools");
            this.start = this.center.offset(1, -1, 6).getCenter().add(0, 0.48, 0);
            this.phase++;
            this.ticks = 0;
        } else if (this.phase == 1 && this.ticks > 15) {
            SettlementViewPanelClientChecks.verifyBuildPanels(this.view);
            this.capture.accept("build-menu");
            this.view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(this.view.contextPopup() == null && !editor.active(), "Escape closes Build without starting placement");
            var build = button("Build");
            this.view.mouseClicked(build.getX() + 2, build.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(this.view.contextPopup() != null, "Build opens on a toolbar click");
            this.view.mouseClicked(build.getX() + 2, build.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(this.view.contextPopup() == null, "clicking Build again closes the submenu");
            chooseBuildTool("Path");
            require(editor.active(), "Build menu starts a path draft");
            var map = this.view.overviewMap();
            float height = this.view.viewHeight();
            this.view.mouseScrolled(map.left + map.width / 2.0, map.top + map.height / 2.0, 0, -1);
            require(this.view.viewHeight() > height, "scrolling over the hidden map zooms the world");
            this.view.mouseScrolled(map.left + map.width / 2.0, map.top + map.height / 2.0, 0, 1);
            click(this.start);
            require(editor.draft().path().nodes().size() == 1, "ground click creates the first node");
            var point = project(this.start.add(5, 0, 0));
            this.view.mouseMoved(point.x, point.y);
            this.phase++;
            this.ticks = 0;
        } else if (this.phase == 2 && this.ticks > 3) {
            require(!editor.cells().isEmpty(), "first node produces a cursor preview");
            this.capture.accept("path-live-preview");
            drag(this.start.add(5, 0, 0), this.start.add(5, 0, 2));
            require(editor.draft().path().nodes().size() == 2, "click-drag creates the second node");
            require(editor.draft().path().nodes().getLast().outgoing().lengthSqr() > 0.1, "click-drag creates curve handles");
            button("+").onPress();
            button("Clean").onPress();
            require(editor.draft().path().width() == 4, "width control updates the draft");
            button("Undo").onPress();
            button("Redo").onPress();
            require(editor.draft().path().style() == com.jvn.villagerretaliation.settlement.SettlementPath.Style.NATURAL, "redo restores the natural style");
            this.phase++;
            this.ticks = 0;
        } else if (this.phase == 3 && this.ticks > 3) {
            var original = editor.draft().path().nodes().getFirst().position();
            drag(original, original.add(1, 0, -1));
            require(!editor.draft().path().nodes().getFirst().position().equals(original), "node gizmo moves the draft anchor");
            button("Undo").onPress();
            require(editor.draft().path().nodes().getFirst().position().equals(original), "undo restores a moved anchor");
            var node = editor.draft().path().nodes().getLast();
            click(node.position());
            drag(node.position().add(node.outgoing()), node.position().add(1, 0, 2));
            require(editor.draft().path().nodes().getLast().outgoing().x > 0.5, "curve handle gizmo follows dragging");
            click(editor.draft().path().nodes().getFirst().position());
            button("Insert").onPress();
            click(this.start.add(2, 0, -1));
            require(editor.draft().path().nodes().size() == 3, "Insert places a node after the selection");
            button("Remove").onPress();
            require(editor.draft().path().nodes().size() == 2, "Remove deletes the selected node");
            button("Undo").onPress();
            require(editor.draft().path().nodes().size() == 3, "draft undo restores the removed node");
            button("Redo").onPress();
            var point = project(this.start);
            this.view.mouseClicked(point.x, point.y, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            this.view.mouseDragged(point.x + 8, point.y + 4, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 8, 4);
            this.view.mouseReleased(point.x + 8, point.y + 4, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            require(editor.draft().path().nodes().size() == 2, "panning keeps the path draft intact");
            var kept = editor.draft().path();
            this.view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            this.view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(editor.active() && editor.draft().path().equals(kept) && !editor.draft().extending(),
                    "Escape stops extending without clearing the draft");
            var extension = this.start.add(0, 0, 5);
            var cursor = project(extension);
            require(this.view.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .noneMatch(button -> button.visible && button.isMouseOver(cursor.x, cursor.y)), "extension target is clear of the toolbar");
            this.view.mouseMoved(cursor.x, cursor.y);
            require(editor.hover() == null, "a stopped draft has no cursor connection");
            click(extension);
            require(editor.active() && editor.draft().path().equals(kept), "ground clicks do not extend a stopped draft");
            button("Extend").onPress();
            click(extension);
            require(editor.draft().path().nodes().size() == 3, "Extend adds a third node away from existing curve handles");
            require(editor.draft().path().nodes().getLast().outgoing().lengthSqr() > 0,
                    "new nodes retain the curve choice without dragging");
            click(editor.draft().path().nodes().getFirst().position());
            require(editor.draft().path().closed(), "clicking the first node closes the loop: " + editor.draft().selected() + " / " + editor.draft().extending());
            button("Undo").onPress();
            require(!editor.draft().path().closed(), "loop closure can be undone");
            button("Redo").onPress();
            require(editor.draft().path().closed(), "loop closure can be restored");
            this.phase++;
            this.ticks = 0;
        } else if (this.phase == 4 && editor.canConfirm()) {
            if (!this.refreshed) {
                button("Refresh").onPress();
                require(!editor.canConfirm(), "refresh requires a new server preview");
                this.refreshed = true;
                return false;
            }
            require(button("Confirm").getWidth() < 96 && button("Cancel").getWidth() < 96,
                    "path actions use compact content widths");
            require(button("Confirm").getY() - button("Natural").getY() <= 22,
                    "the primary path toolbar fits within two rows");
            this.capture.accept("path-loop-confirmation");
            button("Confirm").onPress();
            require(editor.confirming(), "Confirm waits for the server");
            this.phase++;
        } else if (this.phase == 5 && !editor.active() && minecraft.level.getBlockState(BlockPos.containing(this.start)).is(Blocks.DIRT_PATH)) {
            require(editor.centerline().isEmpty(), "confirmed curve geometry is discarded");
            chooseBuildTool("Path");
            require(!editor.draft().path().branch(), "the path tool also handles branching");
            require(editor.draft().path().width() == 4, "settings survive confirmation");
            require(editor.draft().smoothMode(), "curve mode survives confirmation");
            click(this.start);
            require(editor.draft().path().nodes().size() == 1, "Path starts on existing path terrain");
            click(this.start.add(0, 0, -4));
            require(editor.draft().path().nodes().size() == 2, "branch can extend away from the existing path");
            require(editor.draft().path().nodes().getLast().outgoing().lengthSqr() > 0, "the next path continues with curved nodes");
            this.phase++;
            this.ticks = 0;
        } else if (this.phase == 6 && editor.canConfirm()) {
            var reviewed = editor.draft().path();
            editor.hover(this.start.add(3, 0, -3));
            editor.tick(minecraft.level, java.util.UUID.randomUUID(), pos -> true, request -> { throw new IllegalStateException("hover must not request another path review"); });
            require(editor.canConfirm() && editor.draft().path().equals(reviewed), "cursor extensions preserve the confirmed path revision");
            editor.hover(null);
            editor.tick(minecraft.level, java.util.UUID.randomUUID(), pos -> true, request -> { throw new IllegalStateException("leaving the preview must not request another path review"); });
            SettlementViewPanelClientChecks.verifyHintToggle(this.view);
            require(editor.canConfirm() && editor.draft().path().equals(reviewed), "toggling hints preserves the reviewed path and Confirm");
            this.capture.accept("path-branch-preview");
            button("Confirm").onPress();
            this.phase++;
        } else if (this.phase == 7 && !editor.active()) {
            chooseBuildTool("Path");
            require(editor.draft().path().nodes().isEmpty() && !editor.draft().canUndo(), "confirmed nodes and history are discarded");
            button("Curves").onPress();
            click(this.start);
            click(this.start.add(3, 0, 0));
            require(editor.draft().path().nodes().stream().allMatch(node -> node.incoming().lengthSqr() == 0 && node.outgoing().lengthSqr() == 0),
                    "lines mode creates straight segments");
            button("Lines").onPress();
            click(editor.draft().path().nodes().getFirst().position());
            require(editor.draft().path().nodes().stream().allMatch(node -> node.outgoing().lengthSqr() > 0),
                    "curve mode exposes handles on another node without toggling again");
            var first = editor.draft().path().nodes().getFirst();
            var handle = project(first.position().add(first.outgoing()));
            var hit = SettlementPathGizmo.hit(editor.draft(), position -> {
                var point = project(position);
                return new double[]{point.x, point.y};
            }, handle.x, handle.y);
            require(hit != null && hit.node() == 0 && hit.part() == 1, "selected curve handles can be picked immediately");
            drag(first.position().add(first.outgoing()), first.position().add(1, 0, -2));
            var adjusted = editor.draft().path().nodes().getFirst();
            require(!adjusted.outgoing().equals(first.outgoing()), "newly exposed curve handles respond to dragging");
            click(editor.draft().path().nodes().getLast().position());
            click(adjusted.position());
            require(editor.draft().smoothMode() && editor.draft().path().nodes().getFirst().equals(adjusted),
                    "switching nodes preserves curve mode and edited handles");
            button("Curves").onPress();
            click(editor.draft().path().nodes().getLast().position());
            require(!editor.draft().smoothMode(), "selecting a curved node preserves lines mode");
            this.view.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(editor.active(), "Escape keeps a lines draft too");
            button("Cancel").onPress();
            require(!editor.active(), "Cancel clears the draft");
            chooseBuildTool("Path");
            require(!editor.draft().smoothMode(), "lines mode remains selected for the next path");
            button("Cancel").onPress();
            this.view.focus(this.cameraX, this.cameraZ);
            this.phase++;
            this.ticks = 0;
        } else if (this.phase == 8 && this.ticks > 15) {
            require(!this.view.buildMode() && this.view.panelOpacity() == 1, "cancel restores the map and info viewer");
            require(this.view.uiWindows().stream().flatMap(window -> window.widgets().stream())
                    .allMatch(this.view.children()::contains), "restored panels accept input again");
            this.capture.accept("build-panels-restored");
            this.phase++;
            return true;
        }
        return false;
    }

    private void chooseBuildTool(String label) {
        button("Build").onPress();
        require(this.view.contextPopup() != null && this.view.contextPopup().target().kind() == SettlementViewPopup.Kind.BUILD,
                "Build opens the construction submenu");
        var action = this.view.contextPopup().buttons().stream()
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
        require(action.active, label + " is available");
        this.view.mouseClicked(action.getX() + 2, action.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.view.mouseReleased(action.getX() + 2, action.getY() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(this.view.contextPopup() == null, "choosing a build tool closes the submenu");
    }

    private Button button(String label) {
        var direct = this.view.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && button.getMessage().getString().equals(label)).findFirst();
        if (direct.isPresent()) return direct.get();
        button("Edit").onPress();
        return this.view.contextPopup().buttons().stream().filter(button -> button.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing path control: " + label));
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
        var a = project(from);
        var b = project(to);
        this.view.mouseClicked(a.x, a.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.view.mouseDragged(b.x, b.y, GLFW.GLFW_MOUSE_BUTTON_LEFT, b.x - a.x, b.y - a.y);
        this.view.mouseReleased(b.x, b.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
