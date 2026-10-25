package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraTimeline;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraTimeline.Pose;
import com.jvn.villagerretaliation.client.trade.TradeCameraMotion;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_camera")
@PrefixGameTestTemplate(false)
public final class DialogueCameraGameTests {
    private DialogueCameraGameTests() { }

    @GameTest(template = "empty", batch = "dialogue_camera")
    public static void nodeCamerasKeepPrecedingResponseSpeechInOrder(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 1, 1, 1);
        var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(
                helper.getLevel(), player, villager);
        var id = ResourceLocation.parse("fixture:camera_response");
        try {
            for (boolean explicitExchange : List.of(false, true)) {
                var root = json("""
                        {"id":"fixture:camera_response","entries":[{"id":"start","label":"Ask","start":"start"}],
                         "nodes":{"start":{"lines":["Choose."],"responses":[
                           {"id":"accept","label":"Accept","lines":["Your payment was accepted."],"next":"done"}]},
                           "done":{"lines":["Goodbye."],"camera":{"preset":"behind"},"end":true}}}
                        """);
                if (explicitExchange) {
                    var response = root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("responses").get(0).getAsJsonObject();
                    response.remove("lines");
                    response.add("exchanges", JsonParser.parseString("""
                            [{"speaker":{"type":"player"},"text":"I accept."}]
                            """));
                }
                var tree = DialogueTreeResources.readGeneratedTree(id, root, id);
                helper.assertTrue(tree != null, "camera response content loads");
                DialogueTreeResources.installTestTrees(player.server, context.locale(), List.of(tree), java.util.Set.of());
                com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.startEntry(context, id, "start").orElseThrow();
                var option = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.activeOptions(context).orElseThrow().getFirst();
                var result = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.handleDialogueOption(context, option).orElseThrow();
                helper.assertValueEqual(result.exchanges().stream().map(DialogueSpeech::text).toList(),
                        List.of(explicitExchange ? "I accept." : "Your payment was accepted.", "Goodbye."),
                        "response speech is retained once before the destination line");
                helper.assertValueEqual(result.exchanges().getFirst().speaker(),
                        explicitExchange ? player.getUUID() : villager.getUUID(), "response speaker is retained");
                helper.assertTrue(result.exchanges().getFirst().camera() == null, "the destination camera does not leak onto the response");
                helper.assertValueEqual(result.exchanges().getLast().camera(), DialogueCamera.read(root.getAsJsonObject("nodes").getAsJsonObject("done")),
                        "the destination line retains its camera");
            }
        } finally {
            com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.leaveActiveSession(context);
            DialogueTreeResources.clearCache();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_camera")
    public static void sharedFixturesMatchTheParserAndSchema(GameTestHelper helper) {
        try (var stream = DialogueCameraGameTests.class.getResourceAsStream("/dialogue_camera/cases.json")) {
            if (stream == null) throw new IllegalStateException("Missing dialogue camera fixtures");
            var cases = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            for (var raw : cases) {
                var entry = raw.getAsJsonObject();
                boolean valid = entry.get("valid").getAsBoolean();
                helper.assertValueEqual(QuestJsonSchema.validate(entry.get("camera"), DialogueCamera.schema()).isEmpty(),
                        valid, entry.get("name").getAsString());
                try {
                    var camera = DialogueCamera.read(entry);
                    helper.assertTrue(valid, "invalid camera must be rejected");
                    var copy = new JsonObject();
                    copy.add("camera", DialogueCamera.json(camera));
                    helper.assertValueEqual(DialogueCamera.read(copy), camera, "camera JSON round trip");
                } catch (IllegalArgumentException expected) {
                    helper.assertTrue(!valid, "valid camera must parse: " + entry.get("name"));
                }
            }
        } catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
        for (double number : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var value = json("""
                    {"camera":{"preset":"behind"}}
                    """);
            value.getAsJsonObject("camera").addProperty("fov", number);
            rejected(helper, () -> DialogueCamera.read(value));
        }
        var excessive = json("""
                {"camera":{"shots":[]}}
                """);
        for (int index = 0; index < 33; index++)
            excessive.getAsJsonObject("camera").getAsJsonArray("shots").add(json("""
                    {"preset":"behind"}
                    """));
        rejected(helper, () -> DialogueCamera.read(excessive));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_camera")
    public static void exchangesInheritCamerasAndSaveOptionalMetadata(GameTestHelper helper) {
        var entry = json("""
                {"camera":{"preset":"birds_eye"},"exchanges":[
                  {"speaker":{"type":"role","role":"provider"},"text":"Look around."},
                  {"speaker":{"type":"player"},"text":"I see it.","camera":{"preset":"speaker_facing","fov":45}}]}
                """);
        var lines = DialogueExchange.read(entry);
        helper.assertValueEqual(lines.getFirst().camera(), DialogueCamera.read(entry), "entry camera is inherited");
        helper.assertValueEqual(lines.getLast().camera().shots().getFirst().fov(), 45.0D, "exchange overrides its entry");
        var id = ResourceLocation.parse("fixture:camera");
        var continuation = new SceneContinuation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                id, null, "/camera", List.of(), 0, Map.of());
        continuation.dialogueResume(new SceneContinuation.DialogueResume(id, "start", false, "", false, lines));
        helper.assertValueEqual(SceneContinuation.load(continuation.save()).dialogueResume().exchanges(), lines,
                "suspended spoken content retains cameras without persisting animation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_camera")
    public static void cameraPayloadsRoundTripAndRejectUnboundedCounts(GameTestHelper helper) {
        var camera = DialogueCamera.read(json("""
                {"camera":{"shots":[{"preset":"birds_eye","hold_seconds":1},
                  {"preset":"over_shoulder_right","fov":45,"transition":{"type":"lerp","duration_seconds":3}}]}}
                """));
        var identity = UUID.randomUUID();
        var speech = new DialogueSpeech(identity, 1, "Guide", DialogueTextSegment.parse("Look here.", DialogueTextEffects.NONE), camera);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            DialogueSpeech.writeList(buffer, List.of(speech));
            helper.assertValueEqual(DialogueSpeech.readList(buffer), List.of(speech), "ordinary camera wire round trip");
            buffer.clear();
            var closing = new VillagerConversationEndedPayload(1, "Goodbye.", false, camera);
            VillagerConversationEndedPayload.STREAM_CODEC.encode(buffer, closing);
            helper.assertValueEqual(VillagerConversationEndedPayload.STREAM_CODEC.decode(buffer), closing, "closing camera round trip");
            buffer.clear();
            var line = new CampaignConversationViewPayload.Line("1/0", identity, 1, "Guide", "Look here.", 0, 1,
                    camera, null, List.of(new CampaignConversationViewPayload.CameraSubject(identity, 1)));
            var session = new CampaignConversationViewPayload.Session(UUID.randomUUID(), 1, "Camera",
                    ConversationSession.Phase.EXCHANGES, line, true, false, "", List.of(), List.of(), null, "", null, List.of());
            var payload = new CampaignConversationViewPayload(UUID.randomUUID(), 1, identity, 0, 1, List.of(), session, "");
            CampaignConversationViewPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(CampaignConversationViewPayload.STREAM_CODEC.decode(buffer), payload, "campaign subjects and camera round trip");
            for (int count : new int[] {-1, 33, Integer.MAX_VALUE}) {
                buffer.clear();
                buffer.writeBoolean(true);
                buffer.writeEnum(com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Target.SPEAKER);
                buffer.writeBoolean(false);
                buffer.writeVarInt(count);
                rejected(helper, () -> DialogueCamera.read(buffer));
            }
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_camera")
    public static void timelineUsesElapsedSecondsAndShortestRotation(GameTestHelper helper) {
        var shots = DialogueCamera.read(json("""
                {"camera":{"shots":[{"preset":"birds_eye","hold_seconds":1,"transition":{"type":"cut"}},
                  {"preset":"over_shoulder_right","transition":{"type":"lerp","duration_seconds":3}}]}}
                """)).shots();
        var bird = new Pose(new Vec3(0, 6, 0), 170, -60, 70);
        var shoulder = new Pose(new Vec3(2, 2, -2), -170, 0, 40);
        var timeline = new DialogueCameraTimeline(shots, bird);
        java.util.function.Function<DialogueCamera.Shot, Pose> destination = shot -> shot == shots.getFirst() ? bird : shoulder;
        helper.assertValueEqual(timeline.update(0.5, destination), bird, "first shot holds");
        timeline.update(0.5, destination);
        var half = timeline.update(1.5, destination);
        helper.assertTrue(half.position().distanceTo(bird.position().lerp(shoulder.position(), 0.5)) < 1.0E-6,
                "three-second lerp is halfway after 1.5 seconds");
        helper.assertTrue(Math.abs(half.yaw() - 180) < 0.001 && half.fov() == 55, "short yaw path and FOV interpolate together");
        helper.assertValueEqual(timeline.update(1.5, destination), shoulder, "three-second destination");
        helper.assertValueEqual(timeline.update(600, destination), shoulder, "final shot holds");
        var restarted = new DialogueCameraTimeline(List.of(shots.getLast()), half);
        helper.assertValueEqual(restarted.update(0, ignored -> bird), half, "early advance begins at the rendered pose");
        var cut = new DialogueCameraTimeline(List.of(shots.getFirst()), half);
        helper.assertValueEqual(cut.update(0, ignored -> bird), bird, "cuts apply immediately");
        helper.assertValueEqual(cut.update(0, ignored -> bird), bird, "paused time does not advance");
        var corrected = new Pose(bird.position().add(0, -1, 0), bird.yaw(), bird.pitch(), bird.fov());
        var obstructed = new DialogueCameraTimeline(shots, bird);
        obstructed.update(0.9, destination);
        helper.assertValueEqual(obstructed.update(0.1, destination, corrected), corrected,
                "a sequence lerp starts at the currently rendered collision-corrected pose");
        rejected(helper, () -> timeline.update(Double.NaN, destination));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_camera")
    public static void tradingMotionEasesAndRetargetsAcrossFrameRates(GameTestHelper helper) {
        var source = new Pose(Vec3.ZERO, 179, 0, 96);
        var target = new Pose(new Vec3(2, 1, 0), -179, 20, 55);
        var slow = new TradeCameraMotion(source);
        var fast = new TradeCameraMotion(source);
        helper.assertValueEqual(slow.update(0, target), source, "opening begins at the rendered view");
        double previousFov = source.fov();
        for (int step = 0; step < 30; step++) {
            var sample = slow.update(1.0 / 30, target);
            fast.update(1.0 / 60, target);
            var equivalent = fast.update(1.0 / 60, target);
            helper.assertTrue(sample.position().distanceTo(equivalent.position()) < 1.0E-6
                            && Math.abs(sample.fov() - equivalent.fov()) < 1.0E-6,
                    "motion is independent of frame rate");
            helper.assertTrue(sample.fov() <= previousFov && sample.fov() >= target.fov(), "static zoom eases without overshooting");
            helper.assertTrue(sample.yaw() >= 179 && sample.yaw() <= 181, "rotation follows the shortest yaw path");
            previousFov = sample.fov();
        }
        var beforeClose = slow.pose();
        helper.assertValueEqual(slow.update(0, source), beforeClose, "closing does not snap the current view");
        var returning = slow.update(0.2, source);
        helper.assertTrue(returning.fov() > beforeClose.fov() && returning.fov() < source.fov(), "closing smoothly widens FOV");
        var restored = slow.update(1, source);
        helper.assertTrue(restored.position().distanceTo(source.position()) < 0.001
                        && Math.abs(restored.fov() - source.fov()) < 0.01, "the view converges back to its starting pose");
        rejected(helper, () -> slow.update(Double.NaN, source));
        rejected(helper, () -> slow.update(-1, source));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_camera")
    public static void everyInterpolatedAndCorrectedPositionCanBeBounded(GameTestHelper helper) {
        for (var player : List.of(Vec3.ZERO, new Vec3(100, -64, -100))) {
            for (var destination : List.of(player, player.add(8, 0, 0), player.add(500, 200, -400))) {
                for (int step = 0; step <= 100; step++) {
                    Vec3 interpolated = player.add(-100, 80, 10).lerp(destination, step / 100.0);
                    helper.assertTrue(DialogueCamera.clamp(interpolated, player).distanceTo(player) <= 8.000001,
                            "all frames remain inside the eight-block sphere");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_camera")
    public static void ordinaryOpeningsClosingsAndTreesPreserveAuthoredCameras(GameTestHelper helper) throws ReflectiveOperationException, IOException {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 1, 1, 1);
        player.setPos(villager.position().add(0, 0, -2));
        var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        var location = ResourceLocation.parse("fixture:cameras");
        var root = fixture("normal");
        var read = com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources.class.getDeclaredMethod("readDialogueLines",
                ResourceLocation.class, JsonObject.class, com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata.class,
                java.util.Set.class, Map.class, Map.class);
        read.setAccessible(true);
        var lines = new java.util.LinkedHashMap<String, com.jvn.villagerretaliation.dialogue.normal.DialogueLine>();
        read.invoke(null, location, root, com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata.EMPTY, java.util.Set.of(), lines, new java.util.HashMap<>());
        helper.assertValueEqual(lines.get("camera_line").selectText(context, List.of()).camera().shots().size(), 2,
                "normal speech inherits an entry sequence");
        read = com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources.class.getDeclaredMethod("readConversationLines",
                ResourceLocation.class, JsonObject.class, com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata.class,
                String.class, java.util.Set.class, Map.class, Map.class);
        read.setAccessible(true);
        for (var section : List.of("openings", "closings")) {
            var entries = new java.util.LinkedHashMap<String, Object>();
            read.invoke(null, location, root, com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata.EMPTY,
                    section, java.util.Set.of(), entries, new java.util.HashMap<>());
            helper.assertTrue(!entries.isEmpty(), section + " fixture produces spoken entries");
            var entry = entries.values().iterator().next();
            var select = entry.getClass().getDeclaredMethod("selectSpeech", DialogueContext.class);
            select.setAccessible(true);
            helper.assertTrue(((DialogueSpeech) select.invoke(entry, context)).camera() != null, section + " preserve the selected camera");
        }
        var tree = DialogueTreeResources.readGeneratedTree(location, fixture("tree"), location);
        helper.assertTrue(tree.node("valley").orElseThrow().camera() != null, "tree validation preserves node cameras");
        DialogueTreeResources.installTestTrees(player.server, context.locale(), List.of(tree), java.util.Set.of());
        try {
            var started = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.startEntryResult(context, tree.id(), "begin", Map.of(), false);
            helper.assertTrue(started.outcome().success(), "camera tree executes its entry: " + started.outcome().message());
            var result = started.dialogue().orElseThrow(() -> new IllegalStateException("camera tree starts: " + started.outcome().message()));
            helper.assertValueEqual(result.exchanges().getFirst().camera().shots().size(), 2, "tree starts the authored sequence");
            var option = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.activeOptions(context)
                    .orElseThrow(() -> new IllegalStateException("camera tree exposes its next response")).getFirst();
            result = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.handleDialogueOption(context, option)
                    .orElseThrow(() -> new IllegalStateException("camera tree advances to the speaker-facing node"));
            helper.assertValueEqual(result.exchanges().getFirst().camera().shots().getFirst().preset(),
                    DialogueCamera.Preset.SPEAKER_FACING, "following tree node cuts to the speaker");
            var alternate = fixture("tree");
            alternate.getAsJsonObject("nodes").getAsJsonObject("speaker").add("lines", JsonParser.parseString("""
                    [{"text":"Close-up.","camera":{"preset":"speaker_facing","fov":55}}]
                    """));
            var variation = DialogueTreeResources.readGeneratedTree(location, alternate, location);
            DialogueTreeResources.installTestTrees(player.server, context.locale(), List.of(variation), java.util.Set.of());
            com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.startEntry(context, variation.id(), "begin").orElseThrow();
            option = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.activeOptions(context).orElseThrow().getFirst();
            result = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.handleDialogueOption(context, option).orElseThrow();
            helper.assertValueEqual(result.exchanges().getFirst().camera().shots().getFirst().fov(), 55.0D,
                    "the selected text variant can override the containing camera");
        } finally {
            com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.leaveActiveSession(context);
            DialogueTreeResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_camera_facing")
    public static void interactionTurnsTheVillagersBodyAndHeadTowardThePlayer(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        player.setPos(villager.position().add(0, 0, -3));
        for (boolean forced : List.of(false, true)) {
            villager.setYRot(0);
            villager.setYHeadRot(0);
            villager.setYBodyRot(0);
            boolean started = forced
                    ? com.jvn.villagerretaliation.interaction.VillagerConversationService.startForcedIgnoringDisposition(player, villager)
                    : com.jvn.villagerretaliation.interaction.VillagerConversationService.start(player, villager);
            helper.assertTrue(started, "facing fixture starts its dialogue");
            try {
                for (int tick = 0; tick < 6; tick++)
                    helper.assertTrue(com.jvn.villagerretaliation.interaction.VillagerConversationService.validate(player, villager),
                            "dialogue remains active while turning");
                helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(villager.yBodyRot - 180)) < 0.001,
                        "stationary villager turns its body toward the player");
                helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(villager.getYHeadRot() - 180)) < 0.001,
                        "villager turns its head even when AI is disabled");
                player.setPos(villager.position().add(-3, 0, 0));
                for (int tick = 0; tick < 6; tick++)
                    com.jvn.villagerretaliation.interaction.VillagerConversationService.validate(player, villager);
                helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(villager.yBodyRot - 90)) < 0.001,
                        "body follows a participant who moves during dialogue");
            } finally {
                com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(player, false);
                player.setPos(villager.position().add(0, 0, -3));
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_camera")
    public static void authoredOrientationAndPositionRoundTrip(GameTestHelper helper) {
        var camera = DialogueCamera.read(json("""
                {"camera":{"shots":[
                  {"preset":"custom","anchor":"speaker","perspective":"third_person","offset":[1,0.2,-2],
                   "orientation":{"yaw":32.5,"pitch":-12},"fov":51},
                  {"preset":"custom","anchor":"player","perspective":"third_person","placement":"fixed",
                   "offset":[0,0,0],"position":[104.25,72,-28],"orientation":{"yaw":-170,"pitch":45}}]}}
                """));
        var copy = new JsonObject();
        copy.add("camera", DialogueCamera.json(camera));
        helper.assertValueEqual(DialogueCamera.read(copy), camera, "captured orientation and position survive JSON");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            DialogueCamera.write(buffer, camera);
            helper.assertValueEqual(DialogueCamera.read(buffer), camera, "captured orientation and position survive packets");
            helper.assertTrue(!buffer.isReadable(), "extended camera packet is fully consumed");
            buffer.clear();
            var legacy = DialogueCamera.read(json("{\"camera\":{\"preset\":\"behind\"}}"));
            DialogueCamera.write(buffer, legacy);
            helper.assertTrue(buffer.readBoolean(), "legacy camera remains present");
            buffer.readEnum(com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Target.class);
            helper.assertTrue(!buffer.readBoolean(), "legacy camera zoom encoding is unchanged");
            helper.assertValueEqual(buffer.readVarInt(), 1, "legacy shots keep their original wire marker");
            buffer.readerIndex(0);
            helper.assertValueEqual(DialogueCamera.read(buffer), legacy, "legacy shots still decode");
        } finally { buffer.release(); }
        for (var invalid : List.of(
                "{\"camera\":{\"preset\":\"behind\",\"orientation\":{\"yaw\":0,\"pitch\":91}}}",
                "{\"camera\":{\"preset\":\"behind\",\"position\":[0,0,0]}}",
                "{\"camera\":{\"preset\":\"behind\",\"orientation\":{\"yaw\":361,\"pitch\":0}}}")) {
            var definition = json(invalid);
            helper.assertTrue(!QuestJsonSchema.validate(definition.get("camera"), DialogueCamera.schema()).isEmpty(),
                    "invalid capture must fail schema validation");
            rejected(helper, () -> DialogueCamera.read(definition));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_camera")
    public static void capturedOffsetsReproduceMinecraftCoordinates(GameTestHelper helper) {
        var pivot = new Vec3(100, 65.62, -200);
        var expected = new Vec3(1.25, -0.3, -2.5);
        for (float yaw : new float[] {-180, -90, -15, 0, 45, 90, 179}) {
            var forward = com.jvn.villagerretaliation.client.interaction.DialogueCameraTransforms.forward(
                    pivot.add(Vec3.directionFromRotation(0, yaw).scale(4)), pivot, 0);
            var position = com.jvn.villagerretaliation.client.interaction.DialogueCameraTransforms.position(expected, pivot, forward);
            var restored = com.jvn.villagerretaliation.client.interaction.DialogueCameraTransforms.offset(position, pivot, forward);
            helper.assertTrue(restored.distanceTo(expected) < 1.0E-6, "relative capture inverts runtime positioning at every heading");
            float worldYaw = yaw + 37;
            float relative = com.jvn.villagerretaliation.client.interaction.DialogueCameraTransforms.relativeYaw(worldYaw, forward);
            float replayed = relative + com.jvn.villagerretaliation.client.interaction.DialogueCameraTransforms.yaw(forward);
            helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(replayed - worldYaw)) < 0.001,
                    "relative orientation preserves the visible camera heading");
        }
        var fallback = com.jvn.villagerretaliation.client.interaction.DialogueCameraTransforms.forward(pivot, pivot, 90);
        helper.assertTrue(fallback.distanceTo(Vec3.directionFromRotation(0, 90)) < 1.0E-6,
                "coincident subjects use the player heading");
        helper.succeed();
    }

    private static JsonObject fixture(String name) throws IOException {
        try (var stream = DialogueCameraGameTests.class.getResourceAsStream("/dialogue_camera/" + name + ".json")) {
            if (stream == null) throw new IllegalStateException("Missing camera fixture " + name);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void rejected(GameTestHelper helper, Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        helper.fail("invalid camera content must be rejected");
    }
}
