package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.camera.ToucanCameraBasis;
import com.jvn.toucanlib.client.camera.ToucanCameraTargetPoints;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraTimeline.Pose;
import com.jvn.villagerretaliation.client.trade.TradeCameraMotion;
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
import java.lang.ref.WeakReference;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class DialogueCameraController {
    private static DialogueCameraTimeline timeline;
    private static TradeCameraMotion tradeMotion;
    private static boolean trading;
    private static double releaseSeconds;
    private static Pose lastRendered;
    private static WeakReference<Object> renderedLevel = new WeakReference<>(null);
    private static double renderedFov = 70;
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
    private static Preview preview;
    private static FocusTracking focusTracking = new FocusTracking();
    private static ResolvedFocus resolvedFocus;

    private DialogueCameraController() { }

    public record Frame(String key, Camera camera, Entity speaker, Entity character, List<Entity> subjects, boolean hold, boolean cancelSequence) {
        public Frame { subjects = List.copyOf(subjects); }
        public Frame(String key, Camera camera, Entity speaker, Entity character, List<Entity> subjects, boolean hold) {
            this(key, camera, speaker, character, subjects, hold, false);
        }
    }

    public static boolean active() { return timeline != null || tradeMotion != null || preview != null && preview.manualPose != null; }

    public static boolean previewing() { return preview != null; }

    public static Preview beginPreview() {
        if (preview != null) throw new IllegalStateException("A dialogue camera preview is already active");
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()
                || minecraft.getCameraEntity() != minecraft.player)
            throw new IllegalStateException("Camera previews require a living player viewing their own camera");
        var session = new Preview();
        reset();
        preview = session;
        return session;
    }

    public static final class Preview implements AutoCloseable {
        private final State saved = new State(timeline, tradeMotion, trading, releaseSeconds, lastRendered,
                renderedLevel, renderedFov, key, failedKey, frame, pose, prepared, forward, fixed, baseFov, level, focusTracking, resolvedFocus);
        private final Object previewLevel = Minecraft.getInstance().level;
        private final VillagerDialogueCameraFocus.State savedFocus = VillagerDialogueCameraFocus.suspend();
        private Frame override;
        private Pose manualPose;
        private boolean closed;

        private Preview() { }

        public boolean active() { return !closed && preview == this; }

        public void frame(Frame value) {
            if (!active()) throw new IllegalStateException("Camera preview is closed");
            override = value;
            manualPose = null;
        }

        public void pose(Pose value) {
            if (!active()) throw new IllegalStateException("Camera preview is closed");
            if (value == null || !Double.isFinite(value.position().lengthSqr()) || !Float.isFinite(value.yaw())
                    || !Float.isFinite(value.pitch()) || Math.abs(value.pitch()) > 90 || !Double.isFinite(value.fov())
                    || value.fov() < DialogueCamera.MIN_FOV || value.fov() > DialogueCamera.MAX_FOV)
                throw new IllegalArgumentException("Invalid camera preview pose");
            manualPose = value;
        }

        @Override
        public void close() {
            if (!active()) return;
            closed = true;
            preview = null;
            reset();
            if (previewLevel != Minecraft.getInstance().level) {
                VillagerDialogueCameraFocus.clear();
                return;
            }
            VillagerDialogueCameraFocus.restore(savedFocus);
            timeline = saved.timeline(); tradeMotion = saved.tradeMotion(); trading = saved.trading();
            releaseSeconds = saved.releaseSeconds(); lastRendered = saved.lastRendered(); renderedLevel = saved.renderedLevel();
            renderedFov = saved.renderedFov(); key = saved.key(); failedKey = saved.failedKey(); frame = saved.frame();
            pose = saved.pose(); prepared = saved.prepared(); forward = saved.forward(); fixed = saved.fixed();
            baseFov = saved.baseFov(); level = saved.level();
            focusTracking = saved.focusTracking(); resolvedFocus = saved.resolvedFocus();
            lastNanos = System.nanoTime();
            wasPaused = Minecraft.getInstance().isPaused();
        }
    }

    private record State(DialogueCameraTimeline timeline, TradeCameraMotion tradeMotion, boolean trading,
            double releaseSeconds, Pose lastRendered, WeakReference<Object> renderedLevel, double renderedFov,
            String key, String failedKey, Frame frame, Pose pose, Shot prepared, Vec3 forward, Vec3 fixed,
            double baseFov, Object level, FocusTracking focusTracking, ResolvedFocus resolvedFocus) { }
    public static Pose renderedPose() {
        return pose != null ? pose : renderedLevel.get() == Minecraft.getInstance().level ? lastRendered : null;
    }

    static VillagerInteractionScreen screen() {
        var screen = Minecraft.getInstance().screen;
        return screen instanceof VillagerInteractionScreen interaction ? interaction
                : screen instanceof VillagerInteractionChatScreen chat ? chat.interactionScreen() : null;
    }

    public static boolean synchronize() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()
                || minecraft.getCameraEntity() != minecraft.player
                || preview != null && preview.previewLevel != minecraft.level
                || preview == null && minecraft.player.isSpectator()) {
            clear();
            return false;
        }
        if (preview != null && preview.manualPose != null) {
            if (preview.override == null || !subject(preview.override.speaker())) {
                clear();
                return false;
            }
            frame = preview.override;
            return true;
        }
        var screen = screen();
        var trade = minecraft.screen instanceof AbstractContainerScreen<?> container
                && VillagerTradeScreenLayout.of(container) != null ? container : null;
        if (preview == null && (!VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.get()
                || !ClientVillagerConversationState.usesForcedCameraZoom()
                    && !VillagerRetaliationConfig.ENABLE_NORMAL_DIALOGUE_CAMERA_FOCUS.get())) {
            clear();
            return false;
        }
        if (level != minecraft.level) reset();
        level = minecraft.level;
        var next = preview != null && preview.override != null ? preview.override
                : trade != null ? VillagerTradeScreenLayout.of(trade).frame(trade)
                : screen == null ? null : screen.dialogueCameraFrame();
        if (trade != null && next != null) {
            if (tradeMotion == null || !trading) {
                var camera = minecraft.gameRenderer.getMainCamera();
                var source = pose != null ? new Pose(pose.position(), pose.yaw(), pose.pitch(), renderedFov)
                        : renderedLevel.get() == minecraft.level && lastRendered != null
                        ? lastRendered : new Pose(camera.getPosition(), camera.getYRot(), camera.getXRot(), renderedFov);
                tradeMotion = new TradeCameraMotion(source);
                pose = source;
                timeline = null;
                key = null;
                failedKey = null;
                lastNanos = System.nanoTime();
                wasPaused = minecraft.isPaused();
            }
            frame = next;
            trading = true;
            releaseSeconds = 0;
            return true;
        }
        if (tradeMotion != null) {
            if (screen == null && (minecraft.screen == null || trade != null)) {
                trading = false;
                return true;
            }
            tradeMotion = null;
            trading = false;
        }
        if (next == null || !next.hold() && (next.camera() == null || next.camera().shots().isEmpty())) {
            reset();
            return false;
        }
        if (next.hold()) {
            if (timeline != null && next.cancelSequence()) timeline.hold();
            return active();
        }
        if (next.key().equals(failedKey)) return false;
        failedKey = null;
        frame = next;
        if (!next.key().equals(key)) {
            key = next.key();
            var camera = minecraft.gameRenderer.getMainCamera();
            var source = pose == null ? new Pose(camera.getPosition(), camera.getYRot(), camera.getXRot(), renderedFov)
                    : new Pose(pose.position(), pose.yaw(), pose.pitch(), renderedFov);
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
        if (preview != null && preview.manualPose != null) {
            var minecraft = Minecraft.getInstance();
            var requested = preview.manualPose;
            var position = DialogueCamera.clamp(requested.position(), minecraft.player.getPosition(partialTick));
            var adjusted = DialogueCameraOcclusion.resolve(position, minecraft.player.getEyePosition(partialTick),
                    List.of(frame.speaker()), validSubjects(), partialTick);
            if (adjusted == null) {
                pose = null;
                return null;
            }
            pose = new Pose(DialogueCamera.clamp(adjusted, minecraft.player.getPosition(partialTick)),
                    requested.yaw(), requested.pitch(), requested.fov());
            return pose;
        }
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
        // resume a stalled trade transition from its last visible frame
        if (tradeMotion != null) return updateTrade(camera, speaker, partialTick, Math.min(seconds, 0.05));
        var rendered = pose == null ? null : new Pose(pose.position(), pose.yaw(), pose.pitch(), renderedFov);
        pose = timeline.update(seconds, shot -> destination(shot, speaker, partialTick), rendered);
        var minecraft = Minecraft.getInstance();
        Vec3 player = minecraft.player.getPosition(partialTick);
        Vec3 position = DialogueCamera.clamp(pose.position(), player);
        var subjects = timeline.shot().target() == Target.GROUP ? validSubjects() : List.of(target(timeline.shot(), speaker));
        Vec3 adjusted = focused(timeline.shot()) && resolvedFocus != null
                ? DialogueCameraOcclusion.resolveFocus(position, minecraft.player.getEyePosition(partialTick), resolvedFocus.point(),
                        resolvedFocus.subject(), resolvedFocus.block(), validSubjects(), partialTick)
                : DialogueCameraOcclusion.resolve(position, minecraft.player.getEyePosition(partialTick), subjects, validSubjects(), partialTick);
        if (adjusted == null) {
            String failed = key;
            reset();
            level = minecraft.level;
            failedKey = failed;
            return null;
        }
        adjusted = DialogueCamera.clamp(adjusted, player);
        if (!adjusted.equals(position) && timeline.shot().orientation() == null) {
            var rotation = ToucanCameraBasis.rotationToTarget(adjusted,
                    focused(timeline.shot()) && resolvedFocus != null ? resolvedFocus.point() : focus(target(timeline.shot(), speaker), partialTick), pose.yaw(), pose.pitch());
            pose = new Pose(adjusted, rotation.yaw(), rotation.pitch(), pose.fov());
        } else pose = new Pose(adjusted, pose.yaw(), pose.pitch(), pose.fov());
        return pose;
    }

    private static Pose updateTrade(net.minecraft.client.Camera camera, Entity speaker, float partialTick, double seconds) {
        var minecraft = Minecraft.getInstance();
        Pose target;
        if (trading && minecraft.screen instanceof AbstractContainerScreen<?> trade
                && VillagerTradeScreenLayout.of(trade) != null) {
            Vec3 eye = minecraft.player.getEyePosition(partialTick);
            Vec3 aim = VillagerTradeScreenLayout.focus(speaker, partialTick);
            double fov = VillagerTradeScreenLayout.targetFov(baseFov, eye.distanceTo(aim), speaker.getBbHeight());
            var layout = VillagerTradeScreenLayout.of(trade);
            Vec3 position = DialogueCamera.clamp(layout.cameraPosition(trade, eye, aim, fov), minecraft.player.getPosition(partialTick));
            // ease toward the collision-adjusted destination instead of correcting after the spring
            position = DialogueCameraOcclusion.placement(position, eye, List.of(speaker), partialTick, true);
            if (position == null) {
                clear();
                return null;
            }
            var rotation = layout.rotation(position, aim, camera.getYRot(), camera.getXRot(), fov);
            target = new Pose(position, rotation.yaw(), rotation.pitch(), fov);
        } else {
            releaseSeconds += seconds;
            target = new Pose(camera.getPosition(), camera.getYRot(), camera.getXRot(), baseFov);
        }
        pose = tradeMotion.update(seconds, target);
        if (!trading) {
            if (releaseSeconds >= 0.8) {
                clear();
                return null;
            }
            // fade the tracking lag so moving players also return without a final snap
            double progress = releaseSeconds / 0.8;
            pose = pose.interpolate(target, progress * progress * (3 - 2 * progress));
        }
        Vec3 adjusted = DialogueCameraOcclusion.track(pose.position(), minecraft.player.getEyePosition(partialTick),
                List.of(speaker), List.of(speaker), partialTick, true);
        if (adjusted == null) {
            clear();
            return null;
        }
        pose = new Pose(DialogueCamera.clamp(adjusted, minecraft.player.getPosition(partialTick)), pose.yaw(), pose.pitch(), pose.fov());
        return pose;
    }

    public static void rememberCamera(net.minecraft.client.Camera camera) {
        var currentLevel = Minecraft.getInstance().level;
        if (renderedLevel.get() != currentLevel) renderedLevel = new WeakReference<>(currentLevel);
        lastRendered = new Pose(camera.getPosition(), camera.getYRot(), camera.getXRot(), renderedFov);
    }

    public static void rememberFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) renderedFov = event.getFOV();
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
            forward = DialogueCameraTransforms.forward(focus(other, partialTick), principal.getEyePosition(partialTick), player.getYRot());
            prepared = shot;
            fixed = null;
            focusTracking = new FocusTracking();
        }
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
        Vec3 position = shot.position() != null ? shot.position()
                : DialogueCameraTransforms.position(offset, pivot, forward);
        if (shot.placement() == Placement.FIXED) {
            if (fixed == null) fixed = position;
            position = fixed;
        }
        position = DialogueCamera.clamp(position, player.getPosition(partialTick));
        resolvedFocus = resolveFocus(shot, target, partialTick);
        Vec3 aim = focused(shot) ? focusTracking.update(resolvedFocus) : resolvedFocus.point();
        resolvedFocus = new ResolvedFocus(aim, resolvedFocus.subject(), resolvedFocus.block(), resolvedFocus.identity());
        double fov = shot.fov() != null ? shot.fov() : shot.preset() == Preset.TELESCOPE ? 20 : baseFov;
        if (shot.orientation() == null && shot.target() == Target.GROUP && validSubjects().size() > 1) {
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
        if (shot.orientation() != null) {
            float yaw = shot.orientation().yaw();
            if (shot.placement() == Placement.RELATIVE) yaw += DialogueCameraTransforms.yaw(forward);
            return new Pose(position, yaw, shot.orientation().pitch(), fov);
        }
        var rotation = ToucanCameraBasis.rotationToTarget(position, aim, player.getYRot(), player.getXRot());
        return new Pose(position, rotation.yaw(), rotation.pitch(), fov);
    }

    private static Entity target(Shot shot, Entity speaker) {
        if (shot.target() == Target.PLAYER) return Minecraft.getInstance().player;
        Entity target = shot.target() == Target.CHARACTER && subject(frame.character()) ? frame.character() : speaker;
        return shot.focus() == DialogueCamera.Focus.EYES && target == Minecraft.getInstance().player && shot.perspective() == Perspective.FIRST_PERSON
                && subject(frame.character()) ? frame.character() : target;
    }

    private record ResolvedFocus(Vec3 point, Entity subject, net.minecraft.core.BlockPos block, String identity) { }

    public static boolean capturesFocus(Entity entity) {
        return timeline != null && frame != null && timeline.shot().focus() != DialogueCamera.Focus.EYES
                && entity == target(timeline.shot(), subject(frame.speaker()) ? frame.speaker() : frame.character());
    }

    private static boolean focused(Shot shot) { return shot.focus() != DialogueCamera.Focus.EYES || shot.reference() != null; }

    private static ResolvedFocus resolveFocus(Shot shot, Entity target, float partialTick) {
        var minecraft = Minecraft.getInstance();
        var binding = shot.binding();
        if (shot.reference() != null && binding != null && binding.dimension().equals(minecraft.level.dimension().location())) {
            if (binding.entity() != null) {
                for (var entity : minecraft.level.entitiesForRendering()) {
                    if (binding.entity().equals(entity.getUUID()) && subject(entity) && entity.distanceToSqr(minecraft.player) <= 64 * 64
                            && entity instanceof net.minecraft.world.entity.item.ItemEntity item
                            && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).equals(shot.reference().match()))
                        return new ResolvedFocus(entity.getPosition(partialTick).add(0, entity.getBbHeight() * .5 + .1, 0),
                                entity, null, entity.getUUID().toString());
                }
            } else if (binding.block() != null && minecraft.level.hasChunkAt(binding.block())
                    && Vec3.atCenterOf(binding.block()).distanceToSqr(minecraft.player.position()) <= 64 * 64) {
                var state = minecraft.level.getBlockState(binding.block());
                if (!state.isAir() && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(shot.reference().match()))
                    return new ResolvedFocus(Vec3.atCenterOf(binding.block()), null, binding.block(), binding.block().toShortString());
            }
        }
        var point = DialogueCameraFocusPoints.point(target, shot.focus(), partialTick);
        return new ResolvedFocus(point, target, null, target.getUUID() + "/" + shot.focus()
                + "/" + DialogueCameraFocusPoints.captured(target, shot.focus()));
    }

    private static final class FocusTracking {
        private String identity;
        private Vec3 point;
        private Vec3 source;
        private double elapsed;
        private long lastNanos;
        private boolean paused;

        Vec3 update(ResolvedFocus next) {
            long now = System.nanoTime();
            boolean nextPaused = Minecraft.getInstance().isPaused();
            double seconds = lastNanos == 0 || paused || nextPaused ? 0 : Math.min(.05, (now - lastNanos) / 1_000_000_000.0);
            lastNanos = now;
            paused = nextPaused;
            if (point == null) { point = next.point(); identity = next.identity(); }
            if (!next.identity().equals(identity)) {
                source = point;
                elapsed = 0;
                identity = next.identity();
            }
            elapsed += seconds;
            double progress = Math.clamp(elapsed / DialogueCamera.DEFAULT_TRANSITION_SECONDS, 0, 1);
            point = source == null ? next.point() : source.lerp(next.point(), progress * progress * (3 - 2 * progress));
            if (progress == 1) source = null;
            return point;
        }
    }

    private static List<Entity> validSubjects() { return frame.subjects().stream().filter(DialogueCameraController::subject).toList(); }
    private static boolean subject(Entity entity) { return entity != null && entity.isAlive() && !entity.isRemoved() && entity.level() == Minecraft.getInstance().level; }
    private static Vec3 focus(Entity entity, float partialTick) { return ToucanCameraTargetPoints.focusPoint(entity, partialTick); }

    public static boolean detached() {
        if (preview != null && preview.manualPose != null) return true;
        return tradeMotion != null ? !trading && !Minecraft.getInstance().options.getCameraType().isFirstPerson()
                : timeline != null && (timeline.shot().perspective() == Perspective.THIRD_PERSON
                    || timeline.shot().focus() != DialogueCamera.Focus.EYES
                        && frame != null && target(timeline.shot(), frame.speaker()) == Minecraft.getInstance().player);
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) baseFov = event.getFOV();
        if (synchronize()) {
            double fov = preview != null && preview.manualPose != null ? preview.manualPose.fov()
                    : pose == null ? timeline.shot().fov() != null ? timeline.shot().fov()
                    : timeline.shot().preset() == Preset.TELESCOPE ? 20 : baseFov : pose.fov();
            event.setFOV(fov);
        }
        rememberFov(event);
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
        if (preview != null) {
            preview.closed = true;
            VillagerDialogueCameraFocus.clear();
        }
        preview = null;
        reset();
    }

    private static void reset() {
        timeline = null;
        tradeMotion = null;
        trading = false;
        releaseSeconds = 0;
        failedKey = null;
        frame = null;
        key = null;
        pose = null;
        prepared = null;
        fixed = null;
        level = null;
        focusTracking = new FocusTracking();
        resolvedFocus = null;
        wasPaused = false;
        DialogueCameraOcclusion.clear();
    }
}
