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
        return 4;
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
            VillagerDialogueCameraFocus.restore(new VillagerDialogueCameraFocus.State(smoother, 0.2));
            var saved = VillagerDialogueCameraFocus.suspend();
            check(saved.smoother() == smoother && saved.smoother().initialized() && saved.authoredZoom() == 0.2,
                    "preview must save the previous focus rotation and zoom");
            var preview = VillagerDialogueCameraFocus.suspend();
            check(preview.smoother() != smoother && !preview.smoother().initialized() && Double.isNaN(preview.authoredZoom()),
                    "preview must start with independent focus rotation and zoom");
            VillagerDialogueCameraFocus.restore(saved);
            var restored = VillagerDialogueCameraFocus.suspend();
            check(restored.equals(saved) && smoother.initialized(), "preview close must restore the original smoother and zoom");
            VillagerDialogueCameraFocus.restore(saved);
            VillagerDialogueCameraFocus.clear();
            var cleared = VillagerDialogueCameraFocus.suspend();
            check(!cleared.smoother().initialized() && Double.isNaN(cleared.authoredZoom()), "invalidated preview must clear focus state");
        } finally {
            VillagerDialogueCameraFocus.restore(original);
        }
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
