package com.jvn.villagerretaliation.client.trade;

import com.jvn.toucanlib.client.camera.ToucanCameraBasis;
import com.jvn.villagerretaliation.client.config.VillagerRetaliationServerConfigClient;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraController;
import com.jvn.villagerretaliation.client.villager.VillagerTradingTargetFinder;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Target;
import com.jvn.villagerretaliation.mixin.client.AbstractContainerScreenAccessor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.Vec3;

public final class VillagerTradeScreenLayout {
    private static final Camera CAMERA = new Camera(Target.CHARACTER, null);
    private final int targetId;
    private AbstractVillager merchant;

    public VillagerTradeScreenLayout() { this(-1); }
    public VillagerTradeScreenLayout(int targetId) { this.targetId = targetId; }

    private boolean right;
    private boolean split;
    private boolean withinRange = true;
    private double anchorLeft = Double.NaN;
    private long lastLayoutNanos;
    private int layoutWidth;
    private int layoutHeight;

    public interface Host {
        VillagerTradeScreenLayout villagerretaliation$tradeLayout();
    }

    public static VillagerTradeScreenLayout of(MerchantScreen screen) {
        return ((Host) screen).villagerretaliation$tradeLayout();
    }

    public static VillagerTradeScreenLayout of(Screen screen) {
        return screen instanceof Host host ? host.villagerretaliation$tradeLayout() : null;
    }

    public void update(AbstractContainerScreen<?> screen) {
        var minecraft = Minecraft.getInstance();
        if (merchant == null && minecraft.player != null) {
            merchant = targetId >= 0
                    ? minecraft.level != null && minecraft.level.getEntity(targetId) instanceof AbstractVillager villager ? villager : null
                    : VillagerTradingTargetFinder.findTradingVillagerOrSingleNearby(minecraft).orElse(null);
            if (merchant != null) {
                var rotation = ToucanCameraBasis.rotationToTarget(minecraft.player.getEyePosition(),
                        merchant.getEyePosition(), minecraft.player.getYRot(), minecraft.player.getXRot());
                right = Mth.wrapDegrees(rotation.yaw() - minecraft.player.getYRot()) < 0;
            }
        }
        int extension = screen instanceof MerchantScreen && VillagerRetaliationServerConfigClient.skillTradeFeaturesEnabled() ? 20 : 0;
        split = eligible() && fitsHalf(screen.width, screen.height, screen.getXSize(), screen.getYSize(), extension);
        // anchor to the settled framing so camera tracking never moves the click targets
        int targetLeft = left(screen.width, screen.getXSize(), extension, right, split);
        long now = System.nanoTime();
        if (Double.isNaN(anchorLeft) || layoutWidth != screen.width || layoutHeight != screen.height) {
            anchorLeft = targetLeft;
        } else {
            // keep a stalled frame from consuming the whole transition
            double seconds = Math.clamp((now - lastLayoutNanos) / 1_000_000_000.0, 0, 0.05);
            anchorLeft = Mth.lerp(-Math.expm1(-12 * seconds), anchorLeft, targetLeft);
        }
        lastLayoutNanos = now;
        layoutWidth = screen.width;
        layoutHeight = screen.height;
        int left = (int) Math.round(anchorLeft);
        int delta = left - screen.getGuiLeft();
        if (delta == 0) return;
        ((AbstractContainerScreenAccessor) screen).villagerretaliation$setLeftPos(left);
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget) widget.setX(widget.getX() + delta);
        }
    }

    public boolean split() { return split; }

    public static boolean hidesRecipeViewer(Screen screen) {
        var layout = of(screen);
        return layout != null && layout.split();
    }

    public static double targetFov(double baseFov, double distance, double height) {
        double progress = Math.clamp((distance / Math.max(0.1, height) - 0.75) / 1.75, 0, 1);
        progress *= progress * (3 - 2 * progress);
        double nearFov = Math.min(baseFov, Math.max(40, baseFov * 0.85));
        return Mth.lerp(progress, nearFov, Math.min(nearFov, 55));
    }

    private boolean eligible() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null && merchant != null) {
            // require a full block of return travel before re-entering split framing
            withinRange = minecraft.player.distanceToSqr(merchant) <= (withinRange ? 64 : 49);
        }
        return minecraft.player != null && minecraft.level != null && merchant != null
                && merchant.level() == minecraft.level && merchant.isAlive() && !merchant.isRemoved()
                && withinRange && minecraft.player.isAlive()
                && !minecraft.player.isSpectator() && minecraft.getCameraEntity() == minecraft.player
                && VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.get()
                && VillagerRetaliationConfig.ENABLE_NORMAL_DIALOGUE_CAMERA_FOCUS.get();
    }

    public DialogueCameraController.Frame frame(AbstractContainerScreen<?> screen) {
        update(screen);
        return !split ? null : new DialogueCameraController.Frame("trade/" + screen.getMenu().containerId
                + "/" + merchant.getUUID(), CAMERA, merchant, merchant, List.of(merchant), false);
    }

    public static boolean fitsHalf(int width, int height, int panelWidth, int panelHeight, int extension) {
        return width / 2 >= panelWidth + extension + 16 && height >= panelHeight + 16;
    }

    public static int left(int width, int panelWidth, int extension, boolean right, boolean split) {
        return left(width, panelWidth, extension, right, split, width * (right ? 0.25 : 0.75));
    }

    public static int left(int width, int panelWidth, int extension, boolean right, boolean split, double merchantX) {
        int center = split ? (int) Math.round((merchantX + (right ? width : 0)) * 0.5) : width / 2;
        return Math.clamp(center - (panelWidth + extension) / 2 + extension,
                Math.min(extension, Math.max(0, width - panelWidth)), Math.max(0, width - panelWidth));
    }

    public static Vec3 focus(Entity merchant, float partialTick) {
        return merchant.getPosition(partialTick).add(0, merchant.getBbHeight() * 0.6, 0);
    }

    public Vec3 cameraPosition(AbstractContainerScreen<?> screen, Vec3 eye, Vec3 aim, double fov) {
        var minecraft = Minecraft.getInstance();
        double fraction = Math.clamp((double) screen.getYSize() / screen.height * 1.25, 0.5, 0.7);
        double aspect = (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
        return cameraPosition(eye, aim, minimumDistance(merchant.getBbHeight(), fov, aspect, fraction), minecraft.player.getYRot());
    }

    public static double minimumDistance(double height, double fov, double aspect, double fraction) {
        double tangent = Math.tan(Math.toRadians(fov) / 2);
        // account for the reduced depth when the villager is framed to one side
        double side = 0.5 * aspect * tangent;
        return height * Math.sqrt(1 + side * side) / (2 * fraction * tangent);
    }

    public static Vec3 cameraPosition(Vec3 eye, Vec3 aim, double minimumDistance, float yaw) {
        Vec3 horizontal = eye.subtract(aim).multiply(1, 0, 1);
        double distance = horizontal.length();
        Vec3 direction = distance < 0.001 ? Vec3.directionFromRotation(0, yaw).scale(-1) : horizontal.scale(1 / distance);
        double vertical = eye.y - aim.y;
        double required = Math.sqrt(Math.max(0, minimumDistance * minimumDistance - vertical * vertical));
        double pullback = Math.max(distance, required);
        return new Vec3(aim.x + direction.x * pullback, eye.y, aim.z + direction.z * pullback);
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
