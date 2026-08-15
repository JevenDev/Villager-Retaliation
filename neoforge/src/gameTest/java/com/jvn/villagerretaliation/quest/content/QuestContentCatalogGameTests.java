package com.jvn.villagerretaliation.quest.content;

import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.pool.QuestPoolResources;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
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

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_bundle_pilot")
    public static void pilotBundlesCompileWithExactStableRuntimeIdentity(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        VillagerQuestResources.clearCache();
        QuestContentCatalog catalog = QuestContentCatalogs.current(server);

        ResourceLocation breadId = ResourceLocation.parse("villagerretaliation:bread_delivery");
        ResourceLocation horizonId = ResourceLocation.parse("villagerretaliation:choose_the_horizon");
        QuestBundlePath.Owner breadOwner =
                QuestBundlePath.Owner.quest("villagerretaliation", "village_supply", "bread_delivery");
        QuestBundlePath.Owner horizonOwner =
                QuestBundlePath.Owner.quest("villagerretaliation", "cartographers_atlas", "choose_the_horizon");

        helper.assertTrue(catalog.bundles().containsKey(breadOwner)
                        && catalog.bundles().containsKey(horizonOwner),
                "pilot bundles were not accepted: bundles=" + catalog.bundles().keySet()
                        + ", report=" + QuestContentCatalogs.loadReport(server).entries());
        helper.assertValueEqual(catalog.quests().size(), 85, "pilot migration changed the built-in quest set");

        CompiledQuest bread = catalog.compiledQuest(breadId).orElseThrow();
        CompiledQuest horizon = catalog.compiledQuest(horizonId).orElseThrow();
        helper.assertValueEqual(
                bread.stages().stream().map(stage -> stage.id()).toList(),
                List.of("work", "return"),
                "simple pilot stage order changed");
        helper.assertValueEqual(
                horizon.stages().stream().map(stage -> stage.id()).toList(),
                List.of("started", "coast_final", "dark_roof_final", "coast_chosen", "dark_roof_chosen"),
                "complex pilot branch order changed");
        helper.assertValueEqual(
                horizon.stagesById().get("coast_final").objectives().stream()
                        .map(objective -> objective.id()).toList(),
                List.of("coast_final.carry_spyglass",
                        "coast_final.carry_boat",
                        "coast_final.bring_provisions"),
                "complex pilot coast objective order changed");
        helper.assertTrue(bread.source().resource().getPath().endsWith(
                        "quests/village_supply/bread_delivery/quest.json")
                        && horizon.source().resource().getPath().endsWith(
                                "quests/cartographers_atlas/choose_the_horizon/quest.json"),
                "runtime did not compile the bundle-owned quest definitions");

        helper.assertTrue(catalog.rewards().bundled().keySet().containsAll(List.of(
                        ResourceLocation.parse("villagerretaliation:quest/bread_delivery"),
                        ResourceLocation.parse("villagerretaliation:quest/choose_the_horizon"),
                        ResourceLocation.parse("villagerretaliation:quest/choose_the_horizon_coast_bonus"),
                        ResourceLocation.parse("villagerretaliation:quest/choose_the_horizon_dark_roof_bonus"))),
                "pilot reward IDs were not preserved");
        helper.assertValueEqual(
                catalog.localization().payload(
                                "en_us",
                                "quest.cartographers_atlas.choose_the_horizon.stage.started.dialogue.offer.lines")
                        .orElseThrow().getAsJsonArray().size(),
                2,
                "complex pilot localized variant order was not preserved");
        helper.assertTrue(catalog.bundles().get(horizonOwner).definitions()
                        .get(QuestBundlePath.Kind.SCENE)
                        .containsKey(ResourceLocation.parse("villagerretaliation:atlas_horizon_choice")),
                "complex pilot private scene was not owned by its bundle");
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
