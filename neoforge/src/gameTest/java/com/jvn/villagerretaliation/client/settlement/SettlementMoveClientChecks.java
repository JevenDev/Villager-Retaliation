package com.jvn.villagerretaliation.client.settlement;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

final class SettlementMoveClientChecks {
    private final SettlementViewScreen view;
    private final UUID resident;
    private final Consumer<String> capture;
    private int phase, ticks, markerTicks;
    private Vec3 original;
    private boolean noAi, arrived;
    private BlockPos destination;
    private double cameraX, cameraZ;

    SettlementMoveClientChecks(SettlementViewScreen view, UUID resident, Consumer<String> capture) {
        this.view = view; this.resident = resident; this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        if (++this.ticks > 600) throw new IllegalStateException("Movement marker checks timed out at " + this.phase);
        if (this.phase == 0) {
            minecraft.getSingleplayerServer().submit(() -> {
                var villager = (Villager) minecraft.getSingleplayerServer().overworld().getEntity(this.resident);
                this.original = villager.position();
                this.noAi = villager.isNoAi();
                this.destination = villager.blockPosition().east(8).south(5);
                villager.setNoAi(false);
            }).join();
            this.cameraX = this.view.focusX(); this.cameraZ = this.view.focusZ();
            this.view.focus(this.original.x + 4, this.original.z + 2);
            next();
        } else if (this.phase == 1 && this.ticks > 12) {
            var villager = java.util.stream.StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                    .filter(entity -> entity.getUUID().equals(this.resident)).findFirst().orElseThrow();
            click(villager.getBoundingBox().getCenter(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(this.view.selectedResidents().contains(this.resident), "resident is selected for a move command");
            click(this.destination.below().getCenter(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            require(this.view.contextPopup() != null, "ground right-click opens movement actions");
            var action = this.view.contextPopup().buttons().stream()
                    .filter(button -> button.active && button.getMessage().getString().startsWith("Move to")).findFirst().orElseThrow();
            action.onPress();
            next();
        } else if (this.phase == 2 && !this.view.movementDestinations().isEmpty()) {
            this.destination = this.view.movementDestinations().iterator().next();
            if (++this.markerTicks < 3) return false;
            this.capture.accept("movement-destination-marker");
            next();
        } else if (this.phase == 3) {
            this.arrived |= minecraft.getSingleplayerServer().submit(() -> {
                var villager = (Villager) minecraft.getSingleplayerServer().overworld().getEntity(this.resident);
                return villager.blockPosition().distSqr(this.destination) <= 4
                        && !com.jvn.villagerretaliation.settlement.SettlementMoveService.controls(villager);
            }).join();
            if (!this.arrived || !this.view.movementDestinations().isEmpty()) return false;
            this.capture.accept("movement-destination-cleared");
            minecraft.getSingleplayerServer().submit(() -> {
                var villager = (Villager) minecraft.getSingleplayerServer().overworld().getEntity(this.resident);
                com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
                villager.setNoAi(this.noAi);
                villager.teleportTo(this.original.x, this.original.y, this.original.z);
            }).join();
            this.view.focus(this.cameraX, this.cameraZ);
            next();
        } else if (this.phase == 4 && this.ticks > 20) return true;
        return false;
    }

    private void next() { this.phase++; this.ticks = 0; }

    private void click(Vec3 position, int button) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var relative = position.subtract(camera.getPosition());
        double distance = relative.dot(new Vec3(camera.getLookVector()));
        double scale = this.view.height / (2 * distance * Math.tan(Math.toRadians(SettlementViewClient.FOV / 2)));
        double x = this.view.viewportWidth() / 2.0 - relative.dot(new Vec3(camera.getLeftVector())) * scale;
        double y = this.view.height / 2.0 - relative.dot(new Vec3(camera.getUpVector())) * scale;
        this.view.mouseClicked(x, y, button);
        this.view.mouseReleased(x, y, button);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
