package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.tracking.QuestPresentationService;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_reliability")
@PrefixGameTestTemplate(false)
public final class QuestPresentationCacheGameTests {
    @GameTest(template = "empty", batch = "quest_presentation_cache")
    public static void historySnapshotsRefreshAfterCompletionEvictionAndReload(GameTestHelper helper) {
        var data = new VillagerQuestSavedData();
        var player = java.util.UUID.randomUUID();
        var id = VillagerRetaliation.id("history_cache");
        var progress = data.getOrCreate(player, id);
        helper.assertFalse(progress.hasCompletionHistory(), "new progress has no archived completions");
        progress.start(null, Level.OVERWORLD, null, 0);
        progress.complete(1, false);
        var first = progress.completionHistory();
        helper.assertTrue(first == progress.completionHistory(), "unchanged history reuses its immutable conversion");
        for (int index = 1; index <= VillagerQuestSavedData.MAX_COMPLETION_HISTORY; index++) {
            progress.start(null, Level.OVERWORLD, null, index * 2L);
            progress.complete(index * 2L + 1, false);
        }
        var full = progress.completionHistory();
        helper.assertValueEqual(first.size(), 1, "previous snapshots remain immutable");
        helper.assertValueEqual(full.size(), VillagerQuestSavedData.MAX_COMPLETION_HISTORY, "history remains bounded");
        progress.start(null, Level.OVERWORLD, null, 500);
        progress.complete(501, false);
        var replaced = progress.completionHistory();
        helper.assertTrue(full != replaced && full.size() == replaced.size(), "same-size history eviction refreshes the cache");
        helper.assertValueEqual(replaced.getFirst().completionIndex(), 3, "oldest retained completion advances");
        var restored = VillagerQuestSavedData.load(data.save(new CompoundTag(), null), null).get(player, id);
        helper.assertValueEqual(restored.completionHistory(), replaced, "reload preserves the latest history");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_presentation_cache")
    public static void completionPresentationRefreshesForInputsLocaleAndContent(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();
        var catalog = QuestContentCatalogs.current(server);
        var report = QuestContentCatalogs.loadReport(server);
        var information = player.clientInformation();
        var id = VillagerRetaliation.id("presentation_cache");
        var definition = QuestDefinitionFixtures.read(id,
                JsonParser.parseString("{\"display\":{\"title\":\"Completed job\"}}").getAsJsonObject(), id);
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(null, Level.OVERWORLD, null, 0);
        progress.complete(1, false);
        var history = progress.completionHistory().getFirst();
        try {
            var first = QuestPresentationService.completedHistoryEntry(player, definition, history, List.of());
            helper.assertTrue(first == QuestPresentationService.completedHistoryEntry(player, definition, history, List.of()),
                    "stable archived presentation reuses the cached entry");
            var prerequisites = List.of(new QuestTrackerSyncPayload.Prerequisite("test:parent", "Complete parent", false));
            var updated = QuestPresentationService.completedHistoryEntry(player, definition, history, prerequisites);
            helper.assertTrue(updated != first && updated.prerequisites().equals(prerequisites),
                    "changed prerequisite presentation invalidates the entry");
            player.updateOptions(new net.minecraft.server.level.ClientInformation("fr_fr", information.viewDistance(),
                    information.chatVisibility(), information.chatColors(), information.modelCustomisation(),
                    information.mainHand(), information.textFilteringEnabled(), information.allowsListing()));
            var translated = QuestPresentationService.completedHistoryEntry(player, definition, history, prerequisites);
            helper.assertTrue(translated != updated, "locale change rebuilds completed presentation");
            var replacement = new QuestContentCatalog(catalog.generation(), catalog.compiledQuestCatalog(), catalog.dialogueCatalog(),
                    catalog.quests(), catalog.objectiveEventQuestIds(), catalog.factQuestIds(), catalog.memoryEventQuestIds(),
                    catalog.exclusiveGroupQuestIds(), catalog.triggerEventQuestIds(), catalog.scenes(), catalog.encounters(),
                    catalog.pools(), catalog.bundles(), catalog.localization(), catalog.rewards(), catalog.conversations(), catalog.socialProgressions());
            QuestContentCatalogs.installForTests(server, replacement, report);
            var reloaded = QuestPresentationService.completedHistoryEntry(player, definition, history, prerequisites);
            helper.assertTrue(reloaded != translated, "catalog replacement invalidates completed presentation");
            QuestPresentationService.clearRuntimeState(player);
            helper.assertTrue(reloaded != QuestPresentationService.completedHistoryEntry(player, definition, history, prerequisites),
                    "player cleanup drops cached entries");
        } finally {
            player.updateOptions(information);
            QuestContentCatalogs.installForTests(server, catalog, report);
            VillagerQuestService.clearRuntimeState(player);
        }
        helper.succeed();
    }
}
