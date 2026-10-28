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
        var expected = new java.util.HashSet<>(com.jvn.villagerretaliation.quest.content.bundle.BuiltInQuestBundleCompatibility.rules().frozenQuestIds());
        for (String role : List.of("animal_handling", "brewing", "builder", "combat", "cook", "courier", "craftsman",
                "farming", "fishing", "hunting", "logging", "mining", "nitwit", "smelter"))
            expected.add(ResourceLocation.fromNamespaceAndPath("villagerretaliation", "clipboard_" + role));
        expected.addAll(List.of(ResourceLocation.parse("villagerretaliation:urgent_dispatch"),
                ResourceLocation.parse("villagerretaliation:second_dispatch"),
                ResourceLocation.parse("villagerretaliation:missing_neighbor"),
                ResourceLocation.parse("villagerretaliation:last_light")));
        helper.assertValueEqual(catalog.quests().keySet(), expected,
                "packaged quests retain the frozen inventory, worker tutorials and courier outcomes");

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

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_bundle_pilot")
    public static void everyEmbeddedDialogueBindingHasANetworkSafeEntry(GameTestHelper helper) {
        var catalog = QuestContentCatalogs.current(helper.getLevel().getServer()).dialogueCatalog();
        for (var binding : catalog.bindings().values()) {
            if (!binding.treeId().equals(com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCompiler.treeId(binding.questId()))) continue;
            var tree = catalog.tree(binding.treeId()).orElseThrow();
            var entry = tree.entry(binding.entryId()).orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException(
                    "Missing generated dialogue entry: " + binding));
            helper.assertTrue(com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition.isNetworkSafeId(entry.toOption(tree.id()).id()),
                    "generated entry fits the request packet: " + binding);
        }
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

        helper.assertTrue(held != replacement, "reload must publish a new snapshot object; report="
                + QuestContentCatalogs.loadReport(server).entries());
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

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_content_catalog_reload")
    public static void failedCandidateRetainsThePriorLiveSnapshot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        QuestContentCatalog held = QuestContentCatalogs.current(server);
        QuestContentLoadReport originalReport = QuestContentCatalogs.loadReport(server);
        QuestContentLoadReport failedReport = QuestContentLoadReport.builder(held.generation() + 1000L)
                .add(
                        ResourceLocation.parse("examplemod:quests/road/broken/quest.json"),
                        "catalog/test",
                        QuestContentLoadReport.Severity.ERROR,
                        "synthetic invalid candidate")
                .build();

        try {
            QuestContentCatalog selected = QuestContentCatalogs.attemptPublicationForTests(
                    server,
                    QuestContentCatalog.empty(),
                    failedReport,
                    false);
            helper.assertTrue(selected == held, "failed candidate replaced the live snapshot");
            helper.assertTrue(
                    QuestContentCatalogs.current(server) == held,
                    "service lookup observed a failed candidate");
            helper.assertTrue(
                    QuestContentCatalogs.loadReport(server) == failedReport,
                    "failed candidate report was not published separately");
            helper.assertValueEqual(
                    held.quests().size(),
                    QuestContentCatalogs.current(server).quests().size(),
                    "held quest view changed after rejected publication");
        } finally {
            QuestContentCatalogs.installForTests(server, held, originalReport);
        }
        helper.succeed();
    }
}
