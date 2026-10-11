package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.quest.tracking.QuestPresentationService;
import com.jvn.villagerretaliation.quest.tracking.QuestTrackerPresenter;
import com.jvn.villagerretaliation.quest.query.QuestProgressSnapshot;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class QuestPresentationGameTests {
    private QuestPresentationGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_quest_presentation")
    public static void presentationUsesEvaluatedProgressWithoutChangingTheQuest(GameTestHelper helper) {
        var definition = definition("""
                {"display":{"title":"Supplies","description":"Bring the requested crop."},
                 "objectives":[{"id":"crop","type":"item_check","selection":"random",
                   "items":["minecraft:wheat","minecraft:carrot"],"count":4,
                   "tracker":{"text":"Bring {objective_count} {objective_item}","complete_text":"Delivered {objective_item}"}}]}
                """);
        var objective = definition.objectives().getFirst();
        var player = helper.makeMockServerPlayerInLevel();
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(UUID.randomUUID(), Level.OVERWORLD, null, 42L);
        progress.setIssuer(progress.startedVillagerId(), "Mara", "minecraft:farmer", 2,
                Level.OVERWORLD, new BlockPos(12, 65, -4), "home");
        var partial = new QuestPresentationService.ObjectiveState(3, 17, false, 0.75F);
        var values = QuestPresentationService.trackerReplacements(player, definition, progress, objective, true, false, partial);
        helper.assertValueEqual(values.get("objective_progress_count"), "3", "the supplied objective count controls presentation");
        helper.assertValueEqual(values.get("objective_progress"), "0.75", "the supplied progress fraction controls presentation");
        helper.assertValueEqual(values.get("objective_reputation"), "17", "the supplied reputation controls presentation");
        helper.assertValueEqual(values.get("objective_complete"), "no", "partial progress stays incomplete in presentation");
        helper.assertValueEqual(values.get("issuer_name"), "Mara", "unloaded issuers retain their saved identity");
        helper.assertValueEqual(values.get("issuer_status"), "last_seen", "saved identity is presented as last seen");
        helper.assertValueEqual(values.get("has_proof"), "no", "proof availability comes from orchestration");
        helper.assertTrue(progress.resolvedObjectiveItem(objective.id()) == null, "formatting cannot choose a random quest item");
        helper.assertValueEqual(progress.objectiveCounter(objective.id()), 0, "formatting cannot advance an objective counter");
        helper.assertFalse(progress.objectiveComplete(objective.id()), "formatting cannot complete the objective");

        var selected = ResourceLocation.parse("minecraft:carrot");
        progress.resolveObjectiveItem(objective.id(), selected);
        var complete = new QuestPresentationService.ObjectiveState(4, 18, true, 1.0F);
        var completed = QuestPresentationService.trackerReplacements(player, definition, progress, objective, true, true, complete);
        helper.assertValueEqual(completed.get("objective_item_id"), selected.toString(), "formatting uses the saved item selection");
        helper.assertValueEqual(completed.get("objective_complete"), "yes", "evaluated completion is displayed");
        helper.assertValueEqual(QuestPresentationService.trackerReplacements(player, definition, progress, objective, true, true, complete),
                completed, "repeated presentation is stable");
        helper.assertValueEqual(progress.resolvedObjectiveItem(objective.id()), selected, "formatting never rerolls the saved selection");
        var historySteps = QuestPresentationService.completedObjectiveSteps(player, definition, progress);
        helper.assertValueEqual(historySteps.getFirst().label(), "Delivered " + QuestPresentationService.itemName(selected),
                "completed objective labels use the authored text and saved item");
        helper.assertTrue(historySteps.getFirst().completed(), "completed journal entries display completed objectives");
        helper.assertFalse(progress.objectiveComplete(objective.id()), "displaying completion does not award completion");
        helper.assertValueEqual(progress.state(), VillagerQuestSavedData.QuestState.ACTIVE, "presentation leaves the quest active");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_presentation")
    public static void liveIssuerAndCompletedHistoryUseTheirOwnIdentity(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        villager.setCustomName(Component.literal("Current name"));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        var definition = definition("""
                {"display":{"title":"Past errand"},"objectives":[{"id":"crop","type":"item_check","item":"minecraft:wheat","count":2}]}
                """);
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(villager.getUUID(), Level.OVERWORLD, null, 42L);
        progress.setIssuer(villager.getUUID(), "Saved name", "minecraft:librarian", 3,
                Level.NETHER, new BlockPos(100, 65, -20), "old_home");
        var live = QuestPresentationService.trackerReplacements(player, definition, progress, null, true, false,
                QuestPresentationService.ObjectiveState.EMPTY);
        helper.assertValueEqual(live.get("issuer_name"), "Current name", "loaded issuer names take precedence over saved labels");
        helper.assertValueEqual(live.get("issuer_dimension"), "Overworld", "loaded issuer dimensions take precedence over saved locations");
        helper.assertValueEqual(live.get("issuer_status"), "current", "loaded issuers are marked current");

        var history = new VillagerQuestSavedData.CompletionHistoryEntry(2, villager.getUUID(), 42L, 80L, "Past name",
                "minecraft:cartographer", 2, Level.NETHER, new BlockPos(24, 70, -18), "past_home");
        var historical = QuestPresentationService.completionHistoryReplacements(player, definition, history,
                QuestPresentationService.completionIssuerSummary(history), QuestPresentationService.completionIssuerLocationSummary(history));
        helper.assertValueEqual(historical.get("issuer_name"), "Past name", "completion history keeps the issuer name from that run");
        helper.assertValueEqual(historical.get("issuer_dimension"), "The Nether", "completion history keeps the dimension from that run");
        helper.assertValueEqual(historical.get("issuer_status"), "completed", "completed history is not a live issuer view");
        helper.assertValueEqual(historical.get("completion_count"), "2", "completed runs remain individually identified");
        var entry = QuestPresentationService.completedHistoryEntry(player, definition, history, java.util.List.of());
        helper.assertValueEqual(entry.questId(), definition.id() + "#completed/2", "archived runs retain a distinct journal identity");
        helper.assertValueEqual(entry.journal().completedGameTime(), 80L, "archived entries retain their completion time");
        helper.assertValueEqual(entry.issuer(), QuestPresentationService.completionIssuerSummary(history), "journal payload uses the historical issuer");
        helper.assertTrue(entry.objectiveSteps().stream().allMatch(step -> step.completed()), "archived payload marks its objective steps completed");
        helper.assertValueEqual(progress.issuerName(), "Saved name", "presentation does not overwrite the saved issuer");
        helper.assertValueEqual(progress.issuerDimension(), Level.NETHER, "presentation does not rewrite the saved location");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_presentation")
    public static void immutableQuestViewsPreserveSelectionsAndPresentationAfterRuntimeChanges(GameTestHelper helper) {
        var definition = definition("""
                {"display":{"title":"Supplies"},
                 "objectives":[{"id":"crop","type":"item_check","selection":"random",
                   "items":["minecraft:wheat","minecraft:carrot"],"count":4}]}
                """);
        var objective = definition.objectives().getFirst();
        var player = helper.makeMockServerPlayerInLevel();
        var progress = new VillagerQuestSavedData.QuestProgress();
        UUID issuer = UUID.randomUUID();
        progress.start(issuer, Level.OVERWORLD, null, 42L);
        UUID originalRun = progress.beginRun(player.getUUID(), definition.id(), null);
        progress.setIssuer(issuer, "Mara", "minecraft:farmer", 2, Level.OVERWORLD, new BlockPos(12, 65, -4), "home");
        progress.setTarget(issuer, Level.OVERWORLD, new BlockPos(200, 64, 300), objective.id());
        var selected = ResourceLocation.parse("minecraft:carrot");
        progress.resolveObjectiveItem(objective.id(), selected);
        var snapshot = QuestProgressSnapshot.capture(progress, definition);
        var state = new QuestPresentationService.ObjectiveState(3, 17, false, 0.75F);
        var legacy = QuestPresentationService.trackerReplacements(player, definition, progress, objective, true, false, state);
        helper.assertValueEqual(QuestPresentationService.trackerReplacementsView(player, definition, snapshot, objective, true, false, state),
                legacy, "snapshot and compatibility entry points produce the same replacements");
        var legacyItems = QuestTrackerPresenter.questItems(definition, progress, QuestPresentationService::itemName,
                item -> 3, ignored -> 3, ignored -> selected, definition.objectives());
        helper.assertValueEqual(QuestTrackerPresenter.questItemsView(definition, snapshot, QuestPresentationService::itemName,
                item -> 3, ignored -> 3, ignored -> selected, definition.objectives()), legacyItems, "snapshot item previews preserve counts and labels");
        progress.setIssuer(issuer, "Changed", "minecraft:librarian", 3, Level.NETHER, BlockPos.ZERO, "away");
        progress.setTarget(issuer, Level.NETHER, BlockPos.ZERO, objective.id());
        progress.markObjectiveComplete(objective.id());
        progress.markVisitedTarget();
        helper.assertValueEqual(QuestPresentationService.trackerReplacementsView(player, definition, snapshot, objective, true, false, state),
                legacy, "later runtime mutations cannot change the captured presentation facts");
        helper.assertFalse(snapshot.objectiveComplete(objective.id()) || snapshot.visitedTarget(), "captured completion state stays unchanged");
        helper.assertValueEqual(QuestProgressSnapshot.resolvedItem(objective, snapshot), selected, "query retains the recorded random item");
        helper.assertValueEqual(snapshot.questRunId(), originalRun, "query identifies its original run");
        try { snapshot.resolvedObjectiveItems().clear(); helper.fail("query selections must be immutable"); }
        catch (UnsupportedOperationException expected) { }
        helper.succeed();
    }

    private static QuestDefinition definition(String raw) {
        var id = VillagerRetaliation.id("quest_presentation");
        return VillagerQuestResources.readCanonicalQuest(id, JsonParser.parseString(raw).getAsJsonObject(), id);
    }
}
