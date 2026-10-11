package com.jvn.villagerretaliation.client.trade;

import com.jvn.toucanlib.client.camera.ToucanCameraBasis;
import com.jvn.villagerretaliation.client.config.VillagerRetaliationServerConfigClient;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraController;
import com.jvn.villagerretaliation.client.villager.VillagerTradingTargetFinder;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Target;
import com.jvn.villagerretaliation.mixin.client.AbstractContainerScreenAccessor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.Vec3;

public final class VillagerTradeScreenLayout {
    private static final Camera CAMERA = new Camera(Target.SPEAKER, null, List.of(new DialogueCamera.Shot(
            DialogueCamera.Preset.FIRST_PERSON, DialogueCamera.Anchor.PLAYER, DialogueCamera.Placement.RELATIVE,
            DialogueCamera.Perspective.FIRST_PERSON, Vec3.ZERO, Target.CHARACTER, 55.0,
            DialogueCamera.Transition.LERP, 0.35, DialogueCamera.Easing.SMOOTHSTEP, 0)));
    private AbstractVillager merchant;
    private boolean right;
    private boolean split;

    public interface Host {
        VillagerTradeScreenLayout villagerretaliation$tradeLayout();
    }

    public static VillagerTradeScreenLayout of(MerchantScreen screen) {
        return ((Host) screen).villagerretaliation$tradeLayout();
    }

    public void update(MerchantScreen screen) {
        var minecraft = Minecraft.getInstance();
        if (merchant == null && minecraft.player != null) {
            merchant = VillagerTradingTargetFinder.findTradingVillagerOrSingleNearby(minecraft).orElse(null);
            if (merchant != null) {
                var rotation = ToucanCameraBasis.rotationToTarget(minecraft.player.getEyePosition(),
                        merchant.getEyePosition(), minecraft.player.getYRot(), minecraft.player.getXRot());
                right = Mth.wrapDegrees(rotation.yaw() - minecraft.player.getYRot()) < 0;
            }
        }
        int extension = VillagerRetaliationServerConfigClient.skillTradeFeaturesEnabled() ? 20 : 0;
        split = eligible() && fitsHalf(screen.width, screen.height, screen.getXSize(), screen.getYSize(), extension);
        int left = left(screen.width, screen.getXSize(), extension, right, split);
        int delta = left - screen.getGuiLeft();
        if (delta == 0) return;
        ((AbstractContainerScreenAccessor) screen).villagerretaliation$setLeftPos(left);
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget) widget.setX(widget.getX() + delta);
        }
    }

    public boolean split() { return split; }

    private boolean eligible() {
        var minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.level != null && merchant != null
                && merchant.level() == minecraft.level && merchant.isAlive() && !merchant.isRemoved()
                && minecraft.player.distanceToSqr(merchant) <= 64 && minecraft.player.isAlive()
                && !minecraft.player.isSpectator() && minecraft.getCameraEntity() == minecraft.player
                && VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.get()
                && VillagerRetaliationConfig.ENABLE_NORMAL_DIALOGUE_CAMERA_FOCUS.get();
    }

    public DialogueCameraController.Frame frame(MerchantScreen screen) {
        update(screen);
        return !split ? null : new DialogueCameraController.Frame("trade/" + screen.getMenu().containerId
                + "/" + merchant.getUUID(), CAMERA, merchant, merchant, List.of(merchant), false);
    }

    public static boolean fitsHalf(int width, int height, int panelWidth, int panelHeight, int extension) {
        return width / 2 >= panelWidth + extension + 16 && height >= panelHeight + 16;
    }

    public static int left(int width, int panelWidth, int extension, boolean right, boolean split) {
        int center = split ? (right ? width * 3 / 4 : width / 4) : width / 2;
        return Math.clamp(center - (panelWidth + extension) / 2 + extension,
                Math.min(extension, Math.max(0, width - panelWidth)), Math.max(0, width - panelWidth));
    }

    public ToucanCameraBasis.Rotation rotation(Vec3 position, Vec3 focus, float yaw, float pitch, double fov) {
        var centered = ToucanCameraBasis.rotationToTarget(position, focus, yaw, pitch);
        var minecraft = Minecraft.getInstance();
        return rotation(centered.yaw(), centered.pitch(), fov,
                (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight()), right);
    }

    public static ToucanCameraBasis.Rotation rotation(float yaw, float pitch, double fov, double aspect, boolean right) {
        double angle = Math.atan(0.5 * aspect * Math.tan(Math.toRadians(fov) / 2));
        double targetPitch = Math.toRadians(pitch);
        // keep the subject vertically centered while aiming into the empty half
        double turn = Math.asin(Math.clamp(Math.sin(angle) / Math.max(0.001, Math.cos(targetPitch)), -0.99, 0.99));
        double adjustedPitch = Math.atan2(Math.sin(targetPitch), Math.cos(targetPitch) * Math.cos(turn));
        return new ToucanCameraBasis.Rotation(yaw + (float) Math.toDegrees(right ? turn : -turn),
                (float) Math.toDegrees(adjustedPitch));
    }
}
