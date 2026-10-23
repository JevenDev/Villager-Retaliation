package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("authoring_preview")
@PrefixGameTestTemplate(false)
public final class QuestPlaytestGameTests {
    private QuestPlaytestGameTests() { }

    @GameTest(template = "empty", batch = "authoring_playtest_flow", timeoutTicks = 200)
    public static void naturalOffersKeepTheIssuerAndRequireRealTurnIn(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var server = level.getServer();
        var player = helper.makeMockServerPlayerInLevel();
        var provider = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 1, 2);
        var other = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 4, 1, 2);
        provider.setNoAi(true);
        other.setNoAi(true);
        player.teleportTo(provider.getX() - 1, provider.getY(), provider.getZ());
        var quest = fixture(false);
        var followup = fixture("playtest_fixture:followup", false);
        var data = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(level);
        var selected = java.util.List.copyOf(server.getPackRepository().getSelectedIds());
        try (var restore = com.jvn.villagerretaliation.quest.VillagerQuestResources.preserveTestOverride()) {
            enableTestPack(server);
            com.jvn.villagerretaliation.quest.VillagerQuestResources.installCompiledTestCatalog(server, java.util.List.of(quest, followup));
            com.jvn.villagerretaliation.quest.VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
            QuestPlaytestSession.open(player, quest.id(), false);
            QuestPlaytestSession.require(player, quest.id()).provider(provider);
            helper.assertTrue(data.get(player.getUUID(), quest.id()) == null, "preparing an offer does not accept the quest");
            helper.assertFalse(QuestPlaytestSession.held(player, quest.id(), "work"), "normal gameplay progresses without Next");
            var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(level, player, provider);
            var otherContext = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(level, player, other);
            helper.assertTrue(com.jvn.villagerretaliation.quest.VillagerQuestService.matchesState(otherContext, followup.id(), java.util.Set.of("available")),
                    "the focused menu does not block authored follow-up quest eligibility");
            var unrelated = com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition.simple("unrelated", "Small talk",
                    com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType.QUESTION, 0);
            var offer = QuestPlaytestOffers.filter(context, java.util.List.of(unrelated), false);
            helper.assertValueEqual(offer.size(), 1, "the test offers only its selected quest");
            helper.assertFalse(offer.getFirst().id().equals(unrelated.id()), "unrelated dialogue is hidden");
            helper.assertTrue(QuestPlaytestOffers.filter(otherContext, java.util.List.of(unrelated), false).isEmpty(), "a different villager cannot offer the selected quest");
            var service = com.jvn.villagerretaliation.quest.VillagerQuestService.performAction(context, quest.id(),
                    com.jvn.villagerretaliation.action.VillagerActionDefinition.QuestAction.START).orElseThrow();
            helper.assertValueEqual(service.status(), "started", "normal acceptance starts the quest");
            var progress = data.get(player.getUUID(), quest.id());
            helper.assertValueEqual(progress.startedVillagerId(), provider.getUUID(), "acceptance remembers this exact issuer");
            helper.assertValueEqual(QuestPlaytestOffers.provider(player, quest.id(), null), provider, "restart preserves the selected provider");
            helper.assertFalse(com.jvn.villagerretaliation.quest.VillagerQuestService.performAction(context, quest.id(),
                    com.jvn.villagerretaliation.action.VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow().status().equals("completed"), "turn-in requires actual supplies");
            QuestPlaytestSetup.prepare(com.jvn.villagerretaliation.quest.VillagerQuestService.debugPlaytestContext(player, quest.id()),
                    quest.asQuestDefinition().objectives().getFirst(), "");
            com.jvn.villagerretaliation.quest.VillagerQuestService.onPlayerTick(player);
            helper.assertValueEqual(progress.currentStage(), "return", "supplies advance the quest naturally");
            helper.assertFalse(com.jvn.villagerretaliation.quest.VillagerQuestService.performAction(otherContext, quest.id(),
                    com.jvn.villagerretaliation.action.VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow().status().equals("completed"), "a different villager cannot receive the turn-in");
            helper.assertValueEqual(com.jvn.villagerretaliation.quest.VillagerQuestService.performAction(context, quest.id(),
                    com.jvn.villagerretaliation.action.VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow().status(), "completed", "the issuer accepts the natural turn-in");
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "turn-in consumes supplied quest items");
            helper.assertValueEqual(player.totalExperience, 7, "turn-in grants the real reward once");
            com.jvn.villagerretaliation.quest.VillagerQuestService.performAction(context, quest.id(),
                    com.jvn.villagerretaliation.action.VillagerActionDefinition.QuestAction.TURN_IN);
            helper.assertValueEqual(player.totalExperience, 7, "duplicate turn-in does not replay rewards");
            QuestPlaytestSession.close(server, player.getUUID());
            helper.assertValueEqual(QuestPlaytestOffers.filter(context, java.util.List.of(unrelated), false), java.util.List.of(unrelated), "stopping restores normal dialogue");
        } finally {
            QuestPlaytestSession.close(server, player.getUUID());
            data.remove(player.getUUID(), quest.id());
            com.jvn.villagerretaliation.quest.VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            com.jvn.villagerretaliation.quest.VillagerQuestService.clearRuntimeState(player);
            server.getPackRepository().setSelected(selected);
            provider.discard();
            other.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_playtest_structure", timeoutTicks = 200)
    public static void spawnedStructureIsBoundBeforeAcceptanceAndDetectedDuringGameplay(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var server = level.getServer();
        var player = helper.makeMockServerPlayerInLevel();
        var provider = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 1, 2);
        provider.setNoAi(true);
        player.teleportTo(provider.getX() - 1, provider.getY(), provider.getZ());
        var quest = fixture(true);
        var selected = java.util.List.copyOf(server.getPackRepository().getSelectedIds());
        try (var restore = com.jvn.villagerretaliation.quest.VillagerQuestResources.preserveTestOverride()) {
            enableTestPack(server);
            com.jvn.villagerretaliation.quest.VillagerQuestResources.installCompiledTestCatalog(server, java.util.List.of(quest));
            QuestPlaytestSession.open(player, quest.id(), false);
            var controls = QuestPlaytestSession.require(player, quest.id());
            controls.provider(provider);
            QuestPlaytestStructures.spawn(player, quest.id(), "");
            var target = controls.target("");
            helper.assertTrue(target != null, "the spawned structure has a bound test target before accepting");
            var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(level, player, provider);
            helper.assertValueEqual(com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets.locateInitialTarget(context, quest.asQuestDefinition()).orElseThrow(), target,
                    "normal acceptance locates the spawned structure");
            var progress = new com.jvn.villagerretaliation.quest.runtime.QuestProgressModel();
            progress.setTarget(provider.getUUID(), target.dimension(), target.pos(), "");
            helper.assertTrue(com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets.isAtQuestTarget(level, target.pos(), quest.asQuestDefinition(), progress),
                    "real structure starts and references satisfy normal visit detection");
            QuestPlaytestStructures.spawn(player, quest.id(), "");
            helper.assertValueEqual(controls.target(""), target, "repeated setup reuses the same structure");
        } finally {
            QuestPlaytestSession.close(server, player.getUUID());
            server.getPackRepository().setSelected(selected);
            provider.discard();
        }
        helper.succeed();
    }

    private static void enableTestPack(net.minecraft.server.MinecraftServer server) throws java.io.IOException {
        var directory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("datapacks/test_content");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"Playtest fixture\"}}");
        var repository = server.getPackRepository();
        repository.reload();
        var selected = new java.util.ArrayList<>(repository.getSelectedIds());
        if (!selected.contains("file/test_content")) selected.add("file/test_content");
        repository.setSelected(selected);
    }

    private static com.jvn.villagerretaliation.quest.compiled.CompiledQuest fixture(boolean structure) {
        return fixture("playtest_fixture:flow", structure);
    }

    private static com.jvn.villagerretaliation.quest.compiled.CompiledQuest fixture(String id, boolean structure) {
        var root = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"playtest_fixture:flow",
                 "metadata":{"title":"Playtest fixture"},"provider":{"type":"villagerretaliation:villager"},
                 "availability":{"max_starts":0,"max_completions":0},"entry_stage":"work",
                 "rewards":{"experience":7},"stages":[
                  {"id":"work","objectives":[{"id":"supplies","type":"item_check","item":"minecraft:bread","count":2,"consume":true}],"complete_when":["supplies"],"next":"return"},
                  {"id":"return","objectives":[]}]}
                """).getAsJsonObject();
        root.addProperty("id", id);
        if (structure) root.add("target", JsonParser.parseString("{\"structure\":\"minecraft:desert_pyramid\"}"));
        var envelope = com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope.read(ResourceLocation.parse("playtest_fixture:quests/flow.json"), root).orElseThrow();
        return com.jvn.villagerretaliation.quest.QuestV2Compiler.compile(com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }


    @GameTest(template = "empty", batch = "authoring_preview")
    public static void navigationRestoresBranchesWithoutRepeatingEffects(GameTestHelper helper) {
        var source = JsonParser.parseString("""
                {"nodes":{"start":{"exchanges":[{"text":"First"},{"text":"Second"}],
                  "actions":[{"type":"experience","amount":7}],"responses":[{"id":"go","label":"Go","next":"finish"}]},
                  "finish":{"lines":["Finished"],"end":true}}}
                """).getAsJsonObject();
        try (var session = new DialoguePreviewSession(source, ResourceLocation.parse("test:playtest"), new PreviewConditions(), key -> null)) {
            session.start("start");
            session.advance();
            session.choose(0);
            int effects = session.log().size();
            session.previous();
            helper.assertValueEqual(session.nodeId(), "start", "previous restores the response source");
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Second", "previous restores the exact exchange");
            helper.assertValueEqual(session.choices().size(), 1, "previous restores response choices");
            session.exchange(0);
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "First", "exchange selection seeks directly");
            helper.assertValueEqual(session.log().size(), effects, "seeking does not execute effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void visualEditsKeepCursorHistoryAndAppliedLogic(GameTestHelper helper) {
        var source = JsonParser.parseString("""
                {"nodes":{"start":{"exchanges":[{"text":"First"},{"text":"Second"}],
                  "actions":[{"type":"experience","amount":7}],"responses":[{"id":"go","label":"Go","next":"finish"}]},
                  "finish":{"lines":["Finished"],"end":true}}}
                """).getAsJsonObject();
        try (var session = new DialoguePreviewSession(source, ResourceLocation.parse("test:visuals"), new PreviewConditions(), key -> null)) {
            session.start("start"); session.advance();
            var draft = source.deepCopy();
            var node = draft.getAsJsonObject("nodes").getAsJsonObject("start");
            node.getAsJsonArray("exchanges").get(1).getAsJsonObject().addProperty("text", "Updated");
            node.getAsJsonArray("exchanges").get(0).getAsJsonObject().addProperty("text", "New first");
            node.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("amount", 99);
            int logSize = session.log().size();
            session.refreshPresentation(draft);
            helper.assertValueEqual(session.cursor().exchange(), 1, "visual edits preserve the selected exchange");
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Updated", "visible text refreshes");
            helper.assertValueEqual(session.definition().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("actions")
                    .get(0).getAsJsonObject().get("amount").getAsInt(), 7, "logic edits stay pending");
            session.previous();
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "New first", "history uses the latest presentation");
            helper.assertValueEqual(session.log().size(), logSize, "visual refresh never executes effects");
        }
        var changed = source.deepCopy();
        changed.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("exchanges").get(0).getAsJsonObject().addProperty("text", "New text");
        helper.assertTrue(PlaytestDrafts.sameLogic(java.util.Map.of("tree.json", source), java.util.Map.of("tree.json", changed)), "text changes are presentation only");
        changed.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("responses").get(0).getAsJsonObject().addProperty("next", "start");
        helper.assertFalse(PlaytestDrafts.sameLogic(java.util.Map.of("tree.json", source), java.util.Map.of("tree.json", changed)), "response destinations require apply");
        var display = source.deepCopy();
        var title = new com.google.gson.JsonObject(); title.addProperty("title", "New title");
        display.add("display", title);
        helper.assertTrue(PlaytestDrafts.sameLogic(java.util.Map.of("tree.json", source), java.util.Map.of("tree.json", display)), "adding a display title is presentation only");
        var locale = JsonParser.parseString("""
                {"messages":{"line":[{"text":"Hello","conditions":[{"type":"quest_fact","value":"before"}]}]}}
                """).getAsJsonObject();
        var changedLocale = locale.deepCopy();
        changedLocale.getAsJsonObject("messages").getAsJsonArray("line").get(0).getAsJsonObject()
                .getAsJsonArray("conditions").get(0).getAsJsonObject().addProperty("value", "after");
        helper.assertFalse(PlaytestDrafts.sameLogic(java.util.Map.of("data/test/quests/demo/locales/en_us.json", locale), java.util.Map.of("data/test/quests/demo/locales/en_us.json", changedLocale)), "locale condition edits require apply");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void manualSteppingAndBypassesStayScoped(GameTestHelper helper) {
        var controls = new QuestPlaytestSession.Controls();
        helper.assertTrue(controls.held("start"), "quests wait for Next by default");
        controls.advance("start", () -> {
            helper.assertFalse(controls.held("start"), "Next releases the current stage");
            helper.assertTrue(controls.held("finish"), "Next does not run through another stage");
        });
        helper.assertTrue(controls.held("start"), "the hold is restored after a step");
        controls.force("start", "supplies");
        helper.assertTrue(controls.forced("finish", "supplies"), "item bypasses remain available for later turn-in");
        helper.assertFalse(controls.forced("finish", "other"), "bypasses leave unrelated requirements alone");
        controls.forceStage("start");
        helper.assertFalse(controls.stageForced("finish"), "stage conditions remain scoped");
        controls.clear();
        helper.assertFalse(controls.forced("start", "supplies"), "restart clears bypasses");
        try {
            controls.advance("start", () -> { throw new IllegalStateException("test failure"); });
        } catch (IllegalStateException expected) {
            helper.assertTrue(controls.held("start"), "a failed step restores the hold");
        }
        helper.succeed();
    }
}
