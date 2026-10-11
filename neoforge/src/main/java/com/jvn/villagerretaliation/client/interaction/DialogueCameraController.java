package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.camera.ToucanCameraBasis;
import com.jvn.toucanlib.client.camera.ToucanCameraTargetPoints;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraTimeline.Pose;
import com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Anchor;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Placement;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Perspective;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Preset;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Shot;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Target;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class DialogueCameraController {
    private static DialogueCameraTimeline timeline;
    private static String key;
    private static String failedKey;
    private static Frame frame;
    private static Pose pose;
    private static Shot prepared;
    private static Vec3 forward;
    private static Vec3 fixed;
    private static long lastNanos;
    private static boolean wasPaused;
    private static double baseFov = 70;
    private static Object level;

    private DialogueCameraController() { }

    public record Frame(String key, Camera camera, Entity speaker, Entity character, List<Entity> subjects, boolean hold, boolean cancelSequence) {
        public Frame { subjects = List.copyOf(subjects); }
        public Frame(String key, Camera camera, Entity speaker, Entity character, List<Entity> subjects, boolean hold) {
            this(key, camera, speaker, character, subjects, hold, false);
        }
    }

    public static boolean active() { return timeline != null; }

    static VillagerInteractionScreen screen() {
        var screen = Minecraft.getInstance().screen;
        return screen instanceof VillagerInteractionScreen interaction ? interaction
                : screen instanceof VillagerInteractionChatScreen chat ? chat.interactionScreen() : null;
    }

    public static boolean synchronize() {
        var minecraft = Minecraft.getInstance();
        var screen = screen();
        var trade = minecraft.screen instanceof MerchantScreen merchant ? merchant : null;
        if ((screen == null && trade == null) || minecraft.player == null
                || minecraft.level == null || !minecraft.player.isAlive() || minecraft.player.isSpectator()
                || minecraft.getCameraEntity() != minecraft.player
                || !VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.get()
                || !ClientVillagerConversationState.usesForcedCameraZoom()
                    && !VillagerRetaliationConfig.ENABLE_NORMAL_DIALOGUE_CAMERA_FOCUS.get()) {
            clear();
            return false;
        }
        if (level != minecraft.level) clear();
        level = minecraft.level;
        var next = trade == null ? screen.dialogueCameraFrame() : VillagerTradeScreenLayout.of(trade).frame(trade);
        if (next == null || !next.hold() && (next.camera() == null || next.camera().shots().isEmpty())) {
            clear();
            return false;
        }
        if (next.hold()) {
            if (active() && next.cancelSequence()) timeline.hold();
            return active();
        }
        if (next.key().equals(failedKey)) return false;
        failedKey = null;
        frame = next;
        if (!next.key().equals(key)) {
            key = next.key();
            var camera = minecraft.gameRenderer.getMainCamera();
            var source = pose == null ? new Pose(camera.getPosition(), camera.getYRot(), camera.getXRot(), baseFov) : pose;
            timeline = new DialogueCameraTimeline(next.camera().shots(), source);
            prepared = null;
            fixed = null;
            lastNanos = System.nanoTime();
            wasPaused = minecraft.isPaused();
            Entity speaker = subject(frame.speaker()) ? frame.speaker() : frame.character();
            if (subject(speaker)) pose = timeline.update(0, shot -> destination(shot, speaker, minecraft.getTimer().getGameTimeDeltaPartialTick(false)));
        }
        return true;
    }

    public static Pose update(net.minecraft.client.Camera camera, float partialTick) {
        if (!synchronize()) return null;
        long now = System.nanoTime();
        boolean paused = Minecraft.getInstance().isPaused();
        double seconds = paused || wasPaused ? 0 : (now - lastNanos) / 1_000_000_000.0D;
        lastNanos = now;
        wasPaused = paused;
        Entity speaker = subject(frame.speaker()) ? frame.speaker() : subject(frame.character()) ? frame.character() : null;
        if (speaker == null) {
            clear();
            return null;
        }
        pose = timeline.update(seconds, shot -> destination(shot, speaker, partialTick), pose);
        var minecraft = Minecraft.getInstance();
        Vec3 player = minecraft.player.getPosition(partialTick);
        Vec3 position = DialogueCamera.clamp(pose.position(), player);
        var subjects = timeline.shot().target() == Target.GROUP ? validSubjects() : List.of(target(timeline.shot(), speaker));
        Vec3 adjusted = DialogueCameraOcclusion.resolve(position, minecraft.player.getEyePosition(partialTick), subjects, validSubjects(), partialTick);
        if (adjusted == null) {
            String failed = key;
            clear();
            failedKey = failed;
            return null;
        }
        adjusted = DialogueCamera.clamp(adjusted, player);
        if (!adjusted.equals(position)) {
            var rotation = rotation(adjusted, focus(target(timeline.shot(), speaker), partialTick), pose.yaw(), pose.pitch(), pose.fov());
            pose = new Pose(adjusted, rotation.yaw(), rotation.pitch(), pose.fov());
        } else pose = new Pose(position, pose.yaw(), pose.pitch(), pose.fov());
        return pose;
    }

    private static Pose destination(Shot shot, Entity speaker, float partialTick) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        Entity target = target(shot, speaker);
        Entity anchor = shot.anchor() == Anchor.SPEAKER ? speaker : player;
        Vec3 pivot = anchor.getEyePosition(partialTick);
        if (prepared != shot) {
            Entity other = speaker == player && subject(frame.character()) ? frame.character() : speaker;
            Entity principal = speaker != player && speaker != frame.character() && subject(frame.character())
                    ? frame.character() : player;
            Vec3 direction = focus(other, partialTick).subtract(principal.getEyePosition(partialTick)).multiply(1, 0, 1);
            forward = direction.lengthSqr() < 1.0E-8 ? Vec3.directionFromRotation(0, player.getYRot()) : direction.normalize();
            prepared = shot;
            fixed = null;
        }
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 offset = switch (shot.preset()) {
            case FIRST_PERSON, TELESCOPE, CUSTOM -> Vec3.ZERO;
            case THIRD_PERSON -> new Vec3(0, 0, -4);
            case OVER_SHOULDER_LEFT -> new Vec3(-0.8, 0.25, -2.5);
            case OVER_SHOULDER_RIGHT -> new Vec3(0.8, 0.25, -2.5);
            case BIRDS_EYE -> new Vec3(0, 6, 0);
            case BEHIND -> new Vec3(0, 2.5, -4);
            case SPEAKER_FACING -> new Vec3(0, 0, -2);
        };
        if (shot.preset() == Preset.SPEAKER_FACING && speaker == player) offset = new Vec3(0, 0, 2);
        offset = offset.add(shot.offset());
        Vec3 position = pivot.add(right.scale(offset.x)).add(0, offset.y, 0).add(forward.scale(offset.z));
        if (shot.placement() == Placement.FIXED) {
            if (fixed == null) fixed = DialogueCamera.clamp(position, player.getPosition(partialTick));
            position = fixed;
        }
        position = DialogueCamera.clamp(position, player.getPosition(partialTick));
        Vec3 aim = focus(target, partialTick);
        double fov = shot.fov() != null ? shot.fov() : shot.preset() == Preset.TELESCOPE ? 20 : baseFov;
        if (shot.target() == Target.GROUP && validSubjects().size() > 1) {
            AABB bounds = null;
            for (var entity : validSubjects()) {
                var box = entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position()));
                bounds = bounds == null ? box : bounds.minmax(box);
            }
            Vec3 center = bounds.getCenter();
            double radius = Math.sqrt(bounds.getXsize() * bounds.getXsize() + bounds.getYsize() * bounds.getYsize()
                    + bounds.getZsize() * bounds.getZsize()) * 0.5;
            double distance = center.distanceTo(position);
            double aspect = (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
            double limit = shot.fov() == null ? DialogueCamera.MAX_FOV : fov;
            double required = Math.toDegrees(2 * Math.atan2(radius, Math.max(0.01, distance - radius))) / Math.min(1, aspect);
            if (required > limit && shot.placement() == Placement.RELATIVE) {
                Vec3 direction = center.subtract(position);
                direction = direction.lengthSqr() < 1.0E-8 ? forward : direction.normalize();
                double needed = radius + radius / Math.tan(Math.toRadians(limit * Math.min(1, aspect) * 0.5));
                position = DialogueCamera.clamp(center.subtract(direction.scale(needed)), player.getPosition(partialTick));
                distance = center.distanceTo(position);
                required = Math.toDegrees(2 * Math.atan2(radius, Math.max(0.01, distance - radius))) / Math.min(1, aspect);
            }
            if (required <= limit + 0.001) {
                aim = center;
                if (shot.fov() == null) fov = Math.min(DialogueCamera.MAX_FOV, Math.max(fov, required));
            }
        }
        var rotation = rotation(position, aim, player.getYRot(), player.getXRot(), fov);
        return new Pose(position, rotation.yaw(), rotation.pitch(), fov);
    }

    private static ToucanCameraBasis.Rotation rotation(Vec3 position, Vec3 aim, float yaw, float pitch, double fov) {
        return Minecraft.getInstance().screen instanceof MerchantScreen trade
                ? VillagerTradeScreenLayout.of(trade).rotation(position, aim, yaw, pitch, fov)
                : ToucanCameraBasis.rotationToTarget(position, aim, yaw, pitch);
    }

    private static Entity target(Shot shot, Entity speaker) {
        Entity target = shot.target() == Target.CHARACTER && subject(frame.character()) ? frame.character() : speaker;
        return target == Minecraft.getInstance().player && shot.perspective() == Perspective.FIRST_PERSON
                && subject(frame.character()) ? frame.character() : target;
    }

    private static List<Entity> validSubjects() { return frame.subjects().stream().filter(DialogueCameraController::subject).toList(); }
    private static boolean subject(Entity entity) { return entity != null && entity.isAlive() && !entity.isRemoved() && entity.level() == Minecraft.getInstance().level; }
    private static Vec3 focus(Entity entity, float partialTick) { return ToucanCameraTargetPoints.focusPoint(entity, partialTick); }

    public static boolean detached() { return active() && timeline.shot().perspective() == Perspective.THIRD_PERSON; }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) baseFov = event.getFOV();
        if (synchronize()) {
            double fov = pose == null ? timeline.shot().fov() != null ? timeline.shot().fov()
                    : timeline.shot().preset() == Preset.TELESCOPE ? 20 : baseFov : pose.fov();
            event.setFOV(fov);
        }
    }

    public static void onRenderHand(RenderHandEvent event) { if (active()) event.setCanceled(true); }

    public static void tick() {
        if (active()) {
            synchronize();
            if (Minecraft.getInstance().isPaused()) {
                wasPaused = true;
                lastNanos = System.nanoTime();
            }
        }
        DialogueCameraOcclusion.tick();
    }

    public static void clear() {
        timeline = null;
        failedKey = null;
        frame = null;
        key = null;
        pose = null;
        prepared = null;
        fixed = null;
        level = null;
        wasPaused = false;
        DialogueCameraOcclusion.clear();
    }
}
