package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_authoring")
@PrefixGameTestTemplate(false)
public final class DialogueChoiceGameTests {
    private DialogueChoiceGameTests() { }

    @GameTest(template = "empty", batch = "dialogue_choices")
    public static void branchesRejoinPreserveSpeechAndRevalidateLiveConditions(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        var id = ResourceLocation.parse("fixture:branches");
        var root = JsonParser.parseString("""
                {"id":"fixture:branches","entries":[{"id":"start","label":"Ask","start":"start"}],
                 "nodes":{"start":{"exchanges":[{"speaker":{"type":"role","role":"provider"},"text":"Hello."}],
                 "responses":[{"id":"left","label":"Button only","next":"left",
                   "conditions":[{"type":"health","subject":"player","min":0.5}],
                   "exchanges":[{"speaker":{"type":"player"},"text":"I will help."}]},
                   {"id":"right","label":"Other route","next":"right"}]},
                 "left":{"lines":["Left route."],"responses":[{"id":"join","label":"Continue","next":"join"}]},
                 "right":{"lines":["Right route."],"responses":[{"id":"join","label":"Continue","next":"join"}]},
                 "join":{"lines":["Together again."],"responses":[{"id":"done","label":"Goodbye","end":true}]}}}
                """).getAsJsonObject();
        var tree = DialogueTreeResources.readGeneratedTree(id, root, id);
        helper.assertTrue(tree != null, "branching tree loads");
        DialogueTreeResources.installTestTrees(player.server, context.locale(), List.of(tree), Set.of());
        try {
            player.setHealth(player.getMaxHealth());
            DialogueTreeService.startEntry(context, id, "start").orElseThrow();
            var left = DialogueTreeService.activeOptions(context).orElseThrow().stream()
                    .filter(option -> option.treeReference().responseId().equals("left")).findFirst().orElseThrow();
            player.setHealth(player.getMaxHealth() / 4);
            helper.assertTrue(DialogueTreeService.handleDialogueOption(context, left).isEmpty(),
                    "conditions changing after presentation prevent selection");
            player.setHealth(player.getMaxHealth() / 2);
            var reply = DialogueTreeService.handleDialogueOption(context, left).orElseThrow();
            helper.assertValueEqual(reply.exchanges().getFirst().text(), "I will help.", "explicit player exchange is spoken");
            helper.assertTrue(reply.exchanges().stream().noneMatch(speech -> speech.text().equals("Button only")),
                    "button labels are not repeated as speech");
            var data = DialogueTreeSessionSavedData.get(helper.getLevel());
            var key = new DialogueTreeService.SessionKey(player.getUUID(), villager.getUUID());
            var restored = DialogueTreeSessionSavedData.load(data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess()));
            helper.assertValueEqual(restored.get(key), data.get(key), "existing session save format round trips");
            var joined = DialogueTreeService.handleDialogueOption(context, DialogueTreeService.activeOptions(context).orElseThrow().getFirst()).orElseThrow();
            helper.assertValueEqual(joined.text(), "Together again.", "first branch rejoins");
            DialogueTreeService.handleDialogueOption(context, DialogueTreeService.activeOptions(context).orElseThrow().getFirst()).orElseThrow();
            helper.assertTrue(DialogueTreeService.activeOptions(context).isEmpty(), "explicit ending clears the session");
            DialogueTreeService.startEntry(context, id, "start").orElseThrow();
            var right = DialogueTreeService.activeOptions(context).orElseThrow().stream()
                    .filter(option -> option.treeReference().responseId().equals("right")).findFirst().orElseThrow();
            DialogueTreeService.handleDialogueOption(context, right).orElseThrow();
            helper.assertValueEqual(DialogueTreeService.handleDialogueOption(context,
                    DialogueTreeService.activeOptions(context).orElseThrow().getFirst()).orElseThrow().text(),
                    "Together again.", "second branch reaches the same rejoin node");
        } finally {
            DialogueTreeService.leaveActiveSession(context);
            DialogueTreeResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_choices")
    public static void disabledChoicesRemainVisibleAndCannotAdvance(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        var id = ResourceLocation.parse("fixture:locked_choice");
        var root = JsonParser.parseString("""
                {"id":"fixture:locked_choice","entries":[{"id":"start","label":"Ask","start":"start"}],
                 "nodes":{"start":{"lines":["Hello"],"responses":[
                   {"id":"hidden","label":"Hidden","conditions":[{"type":"reputation","min":1000000}],"end":true},
                   {"id":"locked","label":"Locked","unavailable_behavior":"disable","unavailable":"Earn my trust first.",
                    "conditions":[{"type":"reputation","min":1000000}],"next":"end"}]},
                  "end":{"lines":["Done"],"end":true}}}
                """).getAsJsonObject();
        var tree = DialogueTreeResources.readGeneratedTree(id, root, id);
        helper.assertTrue(tree != null, "tree loads");
        DialogueTreeResources.installTestTrees(player.server, context.locale(), List.of(tree), Set.of());
        try {
            DialogueTreeService.startEntry(context, id, "start").orElseThrow();
            var options = DialogueTreeService.activeOptions(context).orElseThrow();
            helper.assertValueEqual(options.size(), 1, "hidden responses stay hidden");
            helper.assertFalse(options.getFirst().available(), "disabled response is not selectable");
            helper.assertValueEqual(options.getFirst().unavailableReason(), "Earn my trust first.", "authored explanation");
            helper.assertTrue(DialogueTreeService.handleDialogueOption(context, options.getFirst()).isEmpty(),
                    "server rejects a disabled response");
            helper.assertValueEqual(DialogueTreeService.activeOptions(context).orElseThrow().getFirst().id(),
                    options.getFirst().id(), "rejected selection does not advance");
        } finally {
            DialogueTreeService.leaveActiveSession(context);
            DialogueTreeResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_choices")
    public static void publishedChoicesRejectReplaysStaleViewsAndOtherPlayers(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var other = helper.makeMockServerPlayerInLevel();
        var option = DialogueOptionDefinition.simple("reply", "Reply", DialogueRequestType.STORY, 0);
        option = com.jvn.villagerretaliation.interaction.DialogueChoiceRequests.publish(player, 42, List.of(option)).getFirst();
        helper.assertFalse(com.jvn.villagerretaliation.interaction.DialogueChoiceRequests.consume(other, 42, "reply", option.selectionToken()),
                "another player cannot select the response");
        helper.assertFalse(com.jvn.villagerretaliation.interaction.DialogueChoiceRequests.consume(player, 43, "reply", option.selectionToken()),
                "another villager cannot receive the response");
        helper.assertTrue(com.jvn.villagerretaliation.interaction.DialogueChoiceRequests.consume(player, 42, "reply", option.selectionToken()),
                "current response accepted");
        helper.assertFalse(com.jvn.villagerretaliation.interaction.DialogueChoiceRequests.consume(player, 42, "reply", option.selectionToken()),
                "duplicate cannot execute twice");
        var next = com.jvn.villagerretaliation.interaction.DialogueChoiceRequests.publish(player, 42, List.of(option)).getFirst();
        helper.assertFalse(com.jvn.villagerretaliation.interaction.DialogueChoiceRequests.consume(player, 42, "reply", option.selectionToken()),
                "the same response id at a later node does not accept a stale token");
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var published = com.jvn.villagerretaliation.interaction.DialogueChoiceRequests.publish(
                player, villager.getId(), List.of(next)).getFirst();
        VillagerInteractionService.sendForcedDialogueReputation(player, villager, List.of(published));
        helper.assertFalse(com.jvn.villagerretaliation.interaction.DialogueChoiceRequests.consume(
                player, villager.getId(), published.id(), published.selectionToken()),
                "the real response send path replaces tokens even when option objects are reused");
        var locked = next.withChoice(com.jvn.villagerretaliation.dialogue.DialogueChoice.DISABLED, false, "Locked");
        com.jvn.villagerretaliation.interaction.DialogueChoiceRequests.publish(player, 42, List.of(locked));
        helper.assertFalse(com.jvn.villagerretaliation.interaction.DialogueChoiceRequests.consume(player, 42, "reply", locked.selectionToken()),
                "disabled responses have no actionable token");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_choices")
    public static void availabilityAndSelectionTokensSurviveNetworkRoundTrip(GameTestHelper helper) {
        var option = DialogueOptionDefinition.simple("reply", "Reply", DialogueRequestType.STORY, 0)
                .withChoice(com.jvn.villagerretaliation.dialogue.DialogueChoice.DISABLED, false, "First earn my trust.");
        var payload = new com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload(42, 0,
                com.jvn.villagerretaliation.reputation.VillagerReputationLevel.NEUTRAL, DialogueDisposition.NEUTRAL,
                com.jvn.villagerretaliation.mood.VillagerMood.NEUTRAL, false, List.of(option), List.of());
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload.STREAM_CODEC.decode(buffer).dialogueOptions().getFirst();
            helper.assertFalse(decoded.available(), "availability is retained");
            helper.assertValueEqual(decoded.unavailableReason(), option.unavailableReason(), "reason is retained");
            helper.assertValueEqual(decoded.selectionToken(), option.selectionToken(), "selection token is retained");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_choices")
    public static void markupDiagnosticsPreserveLiteralAndStyledText(GameTestHelper helper) throws java.io.IOException {
        try (var stream = DialogueChoiceGameTests.class.getResourceAsStream("/dialogue_markup/cases.json");
                var reader = new java.io.InputStreamReader(java.util.Objects.requireNonNull(stream), java.nio.charset.StandardCharsets.UTF_8)) {
            for (var value : JsonParser.parseReader(reader).getAsJsonArray()) {
                var entry = value.getAsJsonObject();
                helper.assertValueEqual(!DialogueTextSegment.markupWarnings(entry.get("text").getAsString()).isEmpty(),
                        entry.get("warning").getAsBoolean(), entry.get("text").getAsString());
            }
        }
        helper.assertValueEqual(DialogueTextSegment.plainText(DialogueTextSegment.parse("2 < 5", DialogueTextEffects.NONE)),
                "2 < 5", "literal text remains unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_choices")
    public static void runtimeRejectsBrokenTargetsAndDuplicateResponses(GameTestHelper helper) {
        var id = ResourceLocation.parse("fixture:broken");
        var root = JsonParser.parseString("""
                {"id":"fixture:broken","entries":[{"id":"start","label":"Ask","start":"start"}],
                 "nodes":{"start":{"responses":[{"id":"reply","label":"Reply","next":"missing"}]}}}
                """).getAsJsonObject();
        helper.assertTrue(DialogueTreeResources.readGeneratedTree(id, root, id) == null, "missing target rejected");
        var responses = root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("responses");
        responses.get(0).getAsJsonObject().addProperty("next", "start");
        helper.assertTrue(DialogueTreeResources.readGeneratedTree(id, root, id) != null, "legitimate loop accepted");
        responses.add(responses.get(0).deepCopy());
        helper.assertTrue(DialogueTreeResources.readGeneratedTree(id, root, id) == null, "duplicate choice rejected");
        helper.succeed();
    }
}
