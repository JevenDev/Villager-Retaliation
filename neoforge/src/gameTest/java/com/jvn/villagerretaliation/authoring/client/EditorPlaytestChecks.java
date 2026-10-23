package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.preview.PlaytestLineSources;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

final class EditorPlaytestChecks {
    private EditorPlaytestChecks() { }
    static int run() throws java.io.IOException {
        applyingRequiresExplicitRestartForDestructiveMigration();
        questBaselinesRestoreOnlyTheSelectedQuest();
        factBaselinesKeepUnrelatedScopes();
        repeatedLinesUseTheirSourceAndFollowExchangeIds();
        return 4;
    }
    private static void applyingRequiresExplicitRestartForDestructiveMigration() {
        var applied = json("{\"revision\":1,\"stages\":[{\"id\":\"start\"}]}");
        var draft = applied.deepCopy();
        draft.addProperty("revision", 2);
        check(EditorPlaytestState.restartReason(applied, draft, "start").isEmpty(), "keep policy preserves an active stage");
        draft.add("migration", json("{\"active_policy\":\"restart\"}"));
        check(!EditorPlaytestState.restartReason(applied, draft, "start").isEmpty(), "restart migration requires an explicit restart");
        check(EditorPlaytestState.restartReason(applied, draft, "").isEmpty(), "an offer can load a restart migration without executing actions");
        draft.remove("migration");
        check(!EditorPlaytestState.restartReason(applied, draft, "removed").isEmpty(), "removing the running stage requires a restart");
        draft.add("revision", json("{\"number\":2,\"migration\":{\"active_policy\":\"reset_stage\"}}"));
        check(!EditorPlaytestState.restartReason(applied, draft, "start").isEmpty(), "nested revision policies are checked");
    }
    private static void questBaselinesRestoreOnlyTheSelectedQuest() {
        var data = new VillagerQuestSavedData();
        var player = UUID.randomUUID(); var otherPlayer = UUID.randomUUID();
        var quest = ResourceLocation.parse("test:baseline"); var other = ResourceLocation.parse("test:other");
        data.getOrCreate(player, quest).setCurrentStage("start");
        data.getOrCreate(player, other).setCurrentStage("untouched");
        data.getOrCreate(otherPlayer, quest).setCurrentStage("another_player");
        data.setTrackedQuest(player, quest);
        var baseline = data.debugSnapshotQuest(player, quest);
        data.get(player, quest).setCurrentStage("finish");
        data.clearTrackedQuest(player);
        data.debugRestoreQuest(player, quest, baseline);
        check(data.get(player, quest).currentStage().equals("start") && data.getTrackedQuests(player).contains(quest), "progress and tracking restore together");
        data.get(player, quest).setCurrentStage("again");
        data.debugRestoreQuest(player, quest, baseline);
        check(data.get(player, quest).currentStage().equals("start"), "restoring does not mutate the baseline");
        check(data.get(player, other).currentStage().equals("untouched") && data.get(otherPlayer, quest).currentStage().equals("another_player"), "other quests and players stay untouched");
        var missing = ResourceLocation.parse("test:missing");
        var absent = data.debugSnapshotQuest(player, missing);
        data.getOrCreate(player, missing);
        data.debugRestoreQuest(player, missing, absent);
        check(data.get(player, missing) == null, "restoring an offer removes newly created progress");
    }
    private static void factBaselinesKeepUnrelatedScopes() {
        var facts = new VillagerQuestFacts();
        facts.setVariable("player:one", "route", "north");
        facts.setVariable("world", "route", "shared");
        var snapshot = facts.debugSnapshot("player:one");
        facts.setVariable("player:one", "route", "south");
        facts.setVariable("player:one", "new_fact", "discard");
        facts.debugRestoreScope("player:one", snapshot);
        check(facts.variable("player:one", "route").orElseThrow().equals("north") && facts.variable("player:one", "new_fact").isEmpty(), "facts restore without keeping later additions");
        check(facts.variable("world", "route").orElseThrow().equals("shared"), "shared world facts are not changed");
        facts.debugRestoreScope("player:one", VillagerQuestFacts.FactSnapshot.EMPTY);
        check(facts.debugSnapshot("player:one").empty(), "empty snapshots restore absent scopes");
    }
    private static void repeatedLinesUseTheirSourceAndFollowExchangeIds() throws java.io.IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-playtest-sources"));
        var document = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/repeated.json", json("""
                {"id":"test:repeated","nodes":{"first":{"lines":["Same"]},"second":{"lines":["Same"]},
                "exchange":{"exchanges":[{"id":"a","speaker":{"type":"role","role":"provider"},"text":"Same"},
                {"id":"b","speaker":{"type":"role","role":"provider"},"text":"Same"}]}}}
                """));
        workspace.add(document);
        var applied = Map.of(document.resourcePath(), document.json());
        document.edit("Change repeated wording", root -> {
            root.getAsJsonObject("nodes").getAsJsonObject("first").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("First edit"));
            root.getAsJsonObject("nodes").getAsJsonObject("second").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Second edit"));
            var exchanges = root.getAsJsonObject("nodes").getAsJsonObject("exchange").getAsJsonArray("exchanges");
            exchanges.get(0).getAsJsonObject().addProperty("text", "Exchange A");
            exchanges.get(1).getAsJsonObject().addProperty("text", "Exchange B");
            var first = exchanges.remove(0); exchanges.add(first);
        });
        EditorPlaytestPresentation.update(workspace, document, applied, Map.of(), "en_us", Map.of());
        check("First edit".equals(EditorPlaytestPresentation.sourcedText(new PlaytestLineSources.Source(document.id(), "first", "", "variants", "first"))), "first repeated line has independent wording");
        check("Second edit".equals(EditorPlaytestPresentation.sourcedText(new PlaytestLineSources.Source(document.id(), "second", "", "variants", "second"))), "second repeated line has independent wording");
        check("Exchange A".equals(EditorPlaytestPresentation.sourcedText(new PlaytestLineSources.Source(document.id(), "exchange", "", "exchanges", "a"))), "reordered exchanges retain source identity");
        var speech = new com.jvn.villagerretaliation.dialogue.DialogueSpeech(null, -1, "", java.util.List.of());
        var source = new PlaytestLineSources.Source(document.id(), "first", "", "variants", "first");
        PlaytestLineSources.active(true);
        PlaytestLineSources.register(speech, source);
        check(source.equals(PlaytestLineSources.get(speech.presentationId())), "presentation ids retain source identity without changing packets");
        PlaytestLineSources.active(false);
        check(PlaytestLineSources.get(speech.presentationId()) == null, "closing playtest clears source identities");
        EditorPlaytestPresentation.clear();
    }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
