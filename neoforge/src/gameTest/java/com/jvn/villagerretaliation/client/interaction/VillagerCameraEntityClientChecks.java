package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

final class VillagerCameraEntityClientChecks {
    private VillagerCameraEntityClientChecks() { }

    static void verify(AbstractContainerScreen<?> screen, int merchantId) {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();
        var before = DialogueCameraController.renderedPose();
        var villager = minecraft.level.getEntity(merchantId);
        var passerby = EntityType.IRON_GOLEM.create(minecraft.level);
        require(passerby != null, "passing entity fixture is available");
        passerby.setNoAi(true);
        Vec3 middle = before.position().lerp(villager.getEyePosition(), 0.5).add(0, -1, 0);
        passerby.setPos(middle);
        minecraft.level.addEntity(passerby);
        int beforeLeft = screen.getGuiLeft();
        var widgetXs = screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(child -> ((AbstractWidget) child).getX()).toList();
        try {
            for (Vec3 position : List.of(before.position().add(0, -1, 0), middle,
                    middle.add(1, 0, 0), middle.add(-1, 0, 0))) {
                passerby.setPos(position);
                DialogueCameraOcclusion.clear();
                for (int step = 0; step < 30; step++) {
                    elapseCameraTime(0.05);
                    var pose = DialogueCameraController.update(camera, 1);
                    require(pose != null && pose.position().distanceTo(before.position()) < 0.02
                                    && Math.abs(Mth.wrapDegrees(pose.yaw() - before.yaw())) < 0.2
                                    && Math.abs(pose.pitch() - before.pitch()) < 0.2 && Math.abs(pose.fov() - before.fov()) < 0.05,
                            "unrelated entities crossing the view cannot reframe the trade camera");
                    VillagerTradeScreenLayout.of(screen).update(screen);
                    require(screen.getGuiLeft() == beforeLeft, "passing entities cannot recenter the villager menu");
                    require(screen.children().stream().filter(AbstractWidget.class::isInstance)
                                    .map(child -> ((AbstractWidget) child).getX()).toList().equals(widgetXs),
                            "passing entities cannot move menu buttons");
                }
                if (position.equals(middle) || position.equals(before.position().add(0, -1, 0)))
                    require(DialogueCameraOcclusion.hides(passerby), "entities obstructing the trade view are hidden without moving the camera");
            }
            Vec3 protectedPosition = DialogueCameraOcclusion.placement(villager.getEyePosition(), minecraft.player.getEyePosition(),
                    List.of(villager), 1, true);
            require(protectedPosition != null && !villager.getBoundingBox().inflate(0.15).contains(protectedPosition),
                    "the camera still avoids the trading villager's body");
            require(!DialogueCameraOcclusion.hides(villager), "the trading villager remains visible");
        } finally {
            minecraft.level.removeEntity(passerby.getId(), Entity.RemovalReason.DISCARDED);
            DialogueCameraOcclusion.clear();
        }
    }

    private static void elapseCameraTime(double seconds) {
        try {
            var field = DialogueCameraController.class.getDeclaredField("lastNanos");
            field.setAccessible(true);
            field.setLong(null, System.nanoTime() - (long) (seconds * 1_000_000_000));
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
