package com.jvn.villagerretaliation.quest.content;

import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.pool.QuestPoolResources;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import java.util.ArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class QuestContentCatalogGameTests {
    private QuestContentCatalogGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_content_catalog_snapshot")
    public static void gameplayViewsShareOneImmutableSnapshot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        QuestContentCatalog snapshot = QuestContentCatalogs.current(server);

        helper.assertTrue(snapshot == QuestContentCatalogs.current(server),
                "current catalog must be stable until publication");
        helper.assertValueEqual(
                snapshot.questDefinitions(),
                VillagerQuestResources.quests(server),
                "quest facade must use the current snapshot");
        helper.assertValueEqual(
                new ArrayList<>(snapshot.scenes().values()),
                SceneResources.scenes(server),
                "scene facade must use the current snapshot");
        helper.assertValueEqual(
                new ArrayList<>(snapshot.encounters().values()),
                EncounterResources.templates(server),
                "encounter facade must use the current snapshot");
        helper.assertValueEqual(
                snapshot.pools(),
                QuestPoolResources.pools(server),
                "pool facade must use the current snapshot");
        helper.assertValueEqual(
                snapshot.generation(),
                QuestContentCatalogs.loadReport(server).generation(),
                "catalog and report generations must match");

        boolean immutable = false;
        try {
            snapshot.quests().clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "catalog maps must be immutable");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_content_catalog_reload")
    public static void reloadNeverMutatesAServiceHeldSnapshot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        QuestContentCatalog held = QuestContentCatalogs.current(server);
        int heldQuestCount = held.quests().size();
        int heldSceneCount = held.scenes().size();
        long heldGeneration = held.generation();

        QuestContentCatalogs.invalidate();
        QuestContentCatalog replacement = QuestContentCatalogs.current(server);

        helper.assertTrue(held != replacement, "reload must publish a new snapshot object");
        helper.assertTrue(
                replacement.generation() > heldGeneration,
                "reload must advance the snapshot generation");
        helper.assertValueEqual(held.quests().size(), heldQuestCount,
                "held quest view changed after publication");
        helper.assertValueEqual(held.scenes().size(), heldSceneCount,
                "held scene view changed after publication");
        helper.assertValueEqual(
                replacement.generation(),
                QuestContentCatalogs.loadReport(server).generation(),
                "replacement report does not belong to the replacement snapshot");
        helper.succeed();
    }
}
