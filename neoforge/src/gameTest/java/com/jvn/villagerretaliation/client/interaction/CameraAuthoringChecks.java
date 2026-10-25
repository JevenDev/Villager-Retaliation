package com.jvn.villagerretaliation.client.interaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.toucanlib.client.camera.ToucanCameraTurnSmoother;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Target;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class CameraAuthoringChecks {
    private CameraAuthoringChecks() { }

    public static int run() {
        capturedTransformsRoundTrip();
        authoredCamerasRoundTrip();
        invalidCamerasAreRejected();
        previewFocusStateIsIsolated();
        fovTransitionsStayContinuous();
        defaultTransitionsAndExplicitCuts();
        return 6;
    }

    private static void capturedTransformsRoundTrip() {
        var pivot = new Vec3(100, 65.62, -200);
        var expected = new Vec3(1.25, -0.3, -2.5);
        for (float yaw : new float[] {-180, -90, -15, 0, 45, 90, 179}) {
            var forward = DialogueCameraTransforms.forward(pivot.add(Vec3.directionFromRotation(0, yaw).scale(4)), pivot, 0);
            var position = DialogueCameraTransforms.position(expected, pivot, forward);
            check(DialogueCameraTransforms.offset(position, pivot, forward).distanceTo(expected) < 1.0E-6,
                    "capture must invert runtime placement at every heading");
            float worldYaw = yaw + 37;
            float replayed = DialogueCameraTransforms.relativeYaw(worldYaw, forward) + DialogueCameraTransforms.yaw(forward);
            check(Math.abs(Mth.wrapDegrees(replayed - worldYaw)) < 0.001, "capture must preserve world heading");
        }
        check(DialogueCameraTransforms.forward(pivot, pivot, 90).distanceTo(Vec3.directionFromRotation(0, 90)) < 1.0E-6,
                "coincident subjects must use player heading");
    }

    private static void authoredCamerasRoundTrip() {
        var camera = DialogueCamera.read(json("""
                {"camera":{"shots":[
                  {"preset":"custom","anchor":"speaker","perspective":"third_person","offset":[1,0.2,-2],
                   "orientation":{"yaw":32.5,"pitch":-12},"fov":51},
                  {"preset":"custom","anchor":"player","perspective":"third_person","offset":[0,0,0],
                   "placement":"fixed","position":[104.25,72,-28],
                   "orientation":{"yaw":-170,"pitch":45}},
                  {"preset":"behind"}]}}
                """));
        var copy = new JsonObject();
        copy.add("camera", DialogueCamera.json(camera));
        check(QuestJsonSchema.validate(copy.get("camera"), DialogueCamera.schema()).isEmpty(), "capture must satisfy schema");
        check(camera.equals(DialogueCamera.read(copy)), "mixed captures must survive JSON");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            DialogueCamera.write(buffer, camera);
            check(camera.equals(DialogueCamera.read(buffer)) && !buffer.isReadable(), "mixed captures must survive packets");
            buffer.clear();
            var legacy = DialogueCamera.read(json("{\"camera\":{\"preset\":\"behind\"}}"));
            DialogueCamera.write(buffer, legacy);
            check(buffer.readBoolean(), "legacy camera must remain present");
            buffer.readEnum(Target.class);
            check(!buffer.readBoolean() && buffer.readVarInt() == 1, "legacy wire marker must remain unchanged");
            buffer.readerIndex(0);
            check(legacy.equals(DialogueCamera.read(buffer)) && !buffer.isReadable(), "legacy packet must round trip");
        } finally {
            buffer.release();
        }
    }

    private static void invalidCamerasAreRejected() {
        for (var invalid : List.of(
                "{\"preset\":\"behind\",\"orientation\":{\"yaw\":0,\"pitch\":91}}",
                "{\"preset\":\"behind\",\"position\":[0,0,0]}",
                "{\"preset\":\"behind\",\"orientation\":{\"yaw\":361,\"pitch\":0}}")) {
            var definition = json(invalid);
            check(!QuestJsonSchema.validate(definition, DialogueCamera.schema()).isEmpty(), "invalid capture must fail schema");
            var wrapper = new JsonObject();
            wrapper.add("camera", definition);
            try {
                DialogueCamera.read(wrapper);
                throw new AssertionError("invalid capture must fail parsing");
            } catch (IllegalArgumentException expected) { }
        }
    }

    private static void previewFocusStateIsIsolated() {
        var original = VillagerDialogueCameraFocus.suspend();
        try {
            var smoother = new ToucanCameraTurnSmoother();
            smoother.update(0, 0, 90, 20, 0.5, 10, 0.08F, 0.25F);
            var fov = new VillagerDialogueCameraFocus.FovTransition(70, 0);
            VillagerDialogueCameraFocus.restore(new VillagerDialogueCameraFocus.State(smoother, 0.2, fov,
                    new java.lang.ref.WeakReference<>(null)));
            var saved = VillagerDialogueCameraFocus.suspend();
            check(saved.smoother() == smoother && saved.smoother().initialized() && saved.authoredZoom() == 0.2 && saved.fov() == fov,
                    "preview must save the previous focus rotation and zoom");
            var preview = VillagerDialogueCameraFocus.suspend();
            check(preview.smoother() != smoother && !preview.smoother().initialized() && Double.isNaN(preview.authoredZoom()) && preview.fov() == null,
                    "preview must start with independent focus rotation and zoom");
            VillagerDialogueCameraFocus.restore(saved);
            var restored = VillagerDialogueCameraFocus.suspend();
            check(restored.equals(saved) && smoother.initialized(), "preview close must restore the original smoother and zoom");
            VillagerDialogueCameraFocus.restore(saved);
            VillagerDialogueCameraFocus.clear();
            var cleared = VillagerDialogueCameraFocus.suspend();
            check(!cleared.smoother().initialized() && Double.isNaN(cleared.authoredZoom()) && cleared.fov() == null, "invalidated preview must clear focus state");
        } finally {
            VillagerDialogueCameraFocus.restore(original);
        }
    }

    private static void defaultTransitionsAndExplicitCuts() {
        var smooth = DialogueCamera.read(json("{\"camera\":{\"preset\":\"speaker_facing\",\"fov\":30}}")).shots().getFirst();
        check(smooth.transition() == DialogueCamera.Transition.LERP && smooth.durationSeconds() == 0.25
                && smooth.easing() == DialogueCamera.Easing.SMOOTHSTEP, "shots must ease by default");
        var cut = DialogueCamera.read(json("{\"camera\":{\"preset\":\"speaker_facing\",\"fov\":30,\"transition\":{\"type\":\"cut\"}}}")).shots().getFirst();
        var source = new DialogueCameraTimeline.Pose(Vec3.ZERO, 0, 0, 96);
        var target = new DialogueCameraTimeline.Pose(new Vec3(2, 1, 0), 90, 20, 30);
        var smoothTimeline = new DialogueCameraTimeline(List.of(smooth), source);
        check(smoothTimeline.update(0, ignored -> target).equals(source), "default transitions must begin at the visible pose");
        var halfway = smoothTimeline.update(0.125, ignored -> target);
        check(halfway.equals(source.interpolate(target, 0.5)), "default transitions must ease position, rotation and FOV together");
        check(smoothTimeline.update(0.125, ignored -> target).equals(target), "default transitions must reach their destination");
        check(new DialogueCameraTimeline(List.of(cut), halfway).update(0, ignored -> target).equals(target),
                "explicit cuts must change position, rotation and FOV immediately");
    }

    private static void fovTransitionsStayContinuous() {
        var slow = new VillagerDialogueCameraFocus.FovTransition(96, 0);
        var fast = new VillagerDialogueCameraFocus.FovTransition(96, 0);
        double previous = 96;
        for (int frame = 1; frame <= 30; frame++) {
            long now = frame * 1_000_000_000L / 30;
            double current = slow.update(45, now, false, 0.25);
            fast.update(45, (frame * 2 - 1) * 1_000_000_000L / 60, false, 0.25);
            double equivalent = fast.update(45, now, false, 0.25);
            check(Math.abs(current - equivalent) < 0.000001, "zoom must ease consistently at different frame rates");
            check(current <= previous && current >= 45, "zoom must approach its target without overshooting");
            previous = current;
        }
        check(Math.abs(previous - 45) < 0.001, "zoom must settle at the requested FOV");
        slow.retarget("next-line", 1_000_000_000L);
        check(slow.update(80, 1_000_000_000L, false, 0.25) == previous, "skipping must preserve the visible FOV");
        slow.retarget("next-line", 1_025_000_000L);
        double interrupted = slow.update(80, 1_025_000_000L, false, 0.25);
        check(interrupted > previous && interrupted < 80, "skipping must ease toward the new FOV");
        check(slow.update(20, 1_025_000_000L, false, 0.25) == interrupted, "rapid skipping must retain the current FOV");
        check(slow.update(20, 5_000_000_000L, true, 0.25) == interrupted, "pausing must freeze the zoom");
        check(slow.update(20, 6_000_000_000L, false, 0.25) == interrupted, "resuming must not include paused time");
        double resumed = slow.update(20, 9_000_000_000L, false, 0.25);
        check(resumed > 20 && resumed < interrupted, "a stalled frame must not jump to the target");
        check(slow.update(96, 9_000_000_000L, false, 0.25) == resumed, "closing must start at the visible zoom");
        for (int frame = 1; frame <= 60; frame++) slow.update(96, 9_000_000_000L + frame * 1_000_000_000L / 60, false, 0.25);
        check(Math.abs(slow.update(96, 10_000_000_000L, false, 0.25) - 96) < 0.001, "closing must restore the player FOV");
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
