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
    private java.util.UUID farmer;
    private com.jvn.villagerretaliation.network.SettlementManagementPayload.Project job;

    SettlementFarmClientChecks(SettlementViewScreen view, BlockPos center, Consumer<String> capture) {
        this.view = view;
        this.start = center.offset(1, -1, 14);
        this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        if (this.phase == 11) return true;
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
                level.setBlock(this.start.offset(4, 1, 1), Blocks.DIRT.defaultBlockState(), 2);
                var town = com.jvn.villagerretaliation.settlement.SettlementSavedData.get(level).settlements().stream()
                        .filter(candidate -> candidate.contains(this.start)).findFirst().orElseThrow();
                var farmer = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
                farmer.setPos(this.start.above().getBottomCenter());
                farmer.setNoAi(true);
                farmer.setCustomName(net.minecraft.network.chat.Component.literal("River"));
                farmer.setVillagerData(farmer.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
                com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, farmer, town.id(),
                        com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
                level.addFreshEntity(farmer);
                this.farmer = farmer.getUUID();
                var inventory = com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(farmer);
                inventory.setItem(com.jvn.villagerretaliation.inventory.HiredJobInventory.MAINHAND_SLOT,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HOE));
                inventory.setItem(inventory.supplySlots().getFirst(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
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
            require(count(SettlementFarmTerrain.Kind.DRY) == 1, "exposed soil is marked dry without blocking the field");
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
            button("Circle").onPress();
            require(editor.nodes().isEmpty() && !editor.canConfirm(), "changing mode clears the rectangle and its approval");
            click(this.start.offset(8, 0, 4));
            next();
        } else if (this.phase == 5 && this.ticks > 3) {
            click(this.start.offset(12, 0, 4));
            next();
        } else if (this.phase == 6 && this.ticks > 10 && editor.canConfirm()) {
            require(editor.cells().size() == 49, "circle includes the disk and excludes its bounding corners");
            this.capture.accept("farmland-circle");
            button("Crop plan").onPress();
            this.phase = 20;
            this.ticks = 0;
        } else if (this.phase == 20 && this.ticks > 5 && minecraft.screen instanceof SettlementFarmPlanScreen) {
            button(net.minecraft.network.chat.Component.translatable("villagerretaliation.gui.settlement.crop.wheat").getString()).onPress();
            var inputs = minecraft.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
            inputs.get(0).setValue("60");
            inputs.get(1).setValue("40");
            requireControlsFit(minecraft);
            inputs = minecraft.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
            require(inputs.get(0).getValue().equals("60") && inputs.get(1).getValue().equals("40"), "resizing preserves mixed crop edits");
            this.phase = 26;
            this.ticks = 0;
        } else if (this.phase == 26 && this.ticks > 5 && minecraft.screen instanceof SettlementFarmPlanScreen) {
            this.capture.accept("farmland-crop-plan");
            button("Apply crop plan").onPress();
            require(editor.crops().enabled(), "placement stores the selected crop plan");
            this.phase = 21;
            this.ticks = 0;
        } else if (this.phase == 21 && this.ticks > 10 && editor.canConfirm()) {
            button("Polygon").onPress();
            click(this.start);
            click(this.start.east(8));
            click(this.start.south(8));
            require(editor.selecting() && !editor.canConfirm(), "polygon keeps adding nodes until closed");
            button("Close polygon").onPress();
            require(editor.crops().enabled(), "shape changes retain the crop plan");
            this.phase = 7;
            this.ticks = 0;
        } else if (this.phase == 7 && this.ticks > 10 && editor.canConfirm()) {
            require(editor.cells().size() == 45, "triangle preview follows its polygon footprint");
            this.capture.accept("farmland-polygon");
            var a = project(this.start.east(8));
            var b = project(this.start.east(10));
            this.view.mouseClicked(a.x, a.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            this.view.mouseDragged(b.x, b.y, GLFW.GLFW_MOUSE_BUTTON_LEFT, b.x - a.x, b.y - a.y);
            this.view.mouseReleased(b.x, b.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(!editor.canConfirm(), "moving a polygon node invalidates approval");
            next();
        } else if (this.phase == 8 && this.ticks > 10 && editor.canConfirm()) {
            require(editor.cells().size() > 45, "dragging a polygon node updates the footprint");
            this.capture.accept("farmland-polygon-resized");
            button("Cancel").onPress();
            require(!editor.active(), "cancel clears farmland placement");
            next();
        } else if (this.phase == 9 && this.ticks > 3) {
            var farm = new com.jvn.villagerretaliation.settlement.SettlementFarm(editor.shape(), editor.nodes());
            var playerId = minecraft.player.getUUID();
            var snapshot = minecraft.getSingleplayerServer().submit(() ->
                    com.jvn.villagerretaliation.settlement.SettlementFarmWorkflowGameTests.prepareClientJob(
                            minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId), farm, editor.crops())).join();
            this.job = snapshot.entries().stream().filter(com.jvn.villagerretaliation.network.SettlementManagementPayload.Project.class::isInstance)
                    .map(com.jvn.villagerretaliation.network.SettlementManagementPayload.Project.class::cast)
                    .filter(project -> project.farm() != null).findFirst().orElseThrow();
            SettlementViewClient.openPanels(this.view);
            minecraft.setScreen(new SettlementProjectScreen(this.view.parent(), this.job, this.view));
            this.phase = 22;
            this.ticks = 0;
        } else if (this.phase == 22 && this.ticks > 10 && minecraft.screen instanceof SettlementProjectScreen) {
            requireControlsFit(minecraft);
            this.capture.accept("farmland-preparation-job");
            button("Cancel job").onPress();
            this.phase = 23;
            this.ticks = 0;
        } else if (this.phase == 23 && this.ticks > 10 && minecraft.screen instanceof SettlementViewScreen) {
            var playerId = minecraft.player.getUUID();
            var area = minecraft.getSingleplayerServer().submit(() ->
                    com.jvn.villagerretaliation.settlement.SettlementFarmWorkflowGameTests.cancelClientJob(
                            minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId), this.job.id(), false)).join();
            this.view.editFarm(area);
            require(editor.crops().enabled(), "reopening the plot retains its crop plan");
            require(editor.shape() == com.jvn.villagerretaliation.settlement.SettlementFarm.Shape.POLYGON, "reopening retains polygon geometry");
            this.phase = 24;
            this.ticks = 0;
        } else if (this.phase == 24 && this.ticks > 10 && editor.canConfirm()) {
            this.capture.accept("farmland-existing-plot");
            button("Cancel").onPress();
            this.phase = 25;
            this.ticks = 0;
        } else if (this.phase == 25 && this.ticks > 3) {
            var playerId = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                com.jvn.villagerretaliation.settlement.SettlementFarmWorkflowGameTests.cancelClientJob(
                        minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId), this.job.id(), true);
                var level = minecraft.getSingleplayerServer().overworld();
                require(level.getBlockState(this.start.offset(4, 0, 4)).is(Blocks.DIRT), "preview and cancellation never till terrain");
                this.before.forEach((pos, state) -> level.setBlock(pos, state, 2));
                if (level.getEntity(this.farmer) != null) level.getEntity(this.farmer).discard();
            }).join();
            this.view.focus(this.cameraX, this.cameraZ);
            this.view.mouseScrolled(this.view.viewportWidth() / 2.0, 80, 0, 100);
            this.phase = 10;
            this.ticks = 0;
        } else if (this.phase == 10 && this.ticks > 30) next();
        return this.phase == 11;
    }

    private long count(SettlementFarmTerrain.Kind kind) {
        return this.view.farmEditor().cells().stream().filter(cell -> cell.kind() == kind).count();
    }
    private void next() { this.phase++; this.ticks = 0; }
    private Button button(String label) {
        return Minecraft.getInstance().screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
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
    private static void requireControlsFit(Minecraft minecraft) {
        var screen = minecraft.screen;
        int width = screen.width, height = screen.height;
        for (var size : java.util.List.of(new int[]{320, 240}, new int[]{426, 240}, new int[]{width, height})) {
            screen.resize(minecraft, size[0], size[1]);
            var widgets = screen.children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                    .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).filter(widget -> widget.visible).toList();
            for (var widget : widgets) {
                require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= screen.width && widget.getBottom() <= screen.height,
                        "farm controls fit the window: " + widget.getMessage().getString());
                for (var other : widgets)
                    if (widget != other) require(widget.getRight() <= other.getX() || other.getRight() <= widget.getX()
                            || widget.getBottom() <= other.getY() || other.getBottom() <= widget.getY(), "farm controls do not overlap");
            }
        }
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
