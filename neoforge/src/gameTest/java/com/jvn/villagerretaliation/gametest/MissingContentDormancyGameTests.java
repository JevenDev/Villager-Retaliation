package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class MissingContentDormancyGameTests {
    private MissingContentDormancyGameTests() {
    }

    @GameTest(
            template = "empty",
            timeoutTicks = 100,
            batch = "encounter_missing_saved_content")
    public static void missingEncounterDefinitionStaysDormantAndResumes(
            GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var templateId = VillagerRetaliation.id("dormant_encounter_fixture");
        List<String> errors = new ArrayList<>();
        EncounterTemplate template =
                EncounterResources.parse(
                        VillagerRetaliation.id("encounters/dormant_encounter_fixture.json"),
                        JsonParser.parseString(
                                        """
                                        {
                                          "schema": "villagerretaliation:encounter/v1",
                                          "id": "villagerretaliation:dormant_encounter_fixture",
                                          "members": [
                                            {"id": "guard", "entity": "minecraft:zombie"}
                                          ],
                                          "spawn_mode": "group",
                                          "boss_bar": false
                                        }
                                        """)
                                .getAsJsonObject(),
                        errors);
        helper.assertTrue(
                template != null && errors.isEmpty(),
                "dormancy fixture did not parse: " + errors);

        SceneSavedData data = new SceneSavedData();
        EncounterInstance encounter =
                new EncounterInstance(
                        UUID.randomUUID(),
                        templateId,
                        UUID.randomUUID(),
                        "dormancy/start",
                        "dormancy",
                        Set.of(),
                        helper.getLevel().dimension().location(),
                        helper.absolutePos(new BlockPos(4, 1, 4)),
                        1,
                        "normal",
                        template.cleanupPolicy(),
                        template.completionCondition(),
                        1);
        EncounterInstance resumed = encounter;
        QuestContentCatalog baseline = QuestContentCatalogs.current(server);
        QuestContentCatalog complete =
                withEncounter(baseline, template, baseline.generation() + 1L);
        try {
            QuestContentCatalogs.installForTests(server, complete, null);
            helper.assertTrue(
                    EncounterResources.template(server, templateId).isPresent(),
                    "fixture definition was not installed");

            QuestContentCatalogs.installForTests(server, baseline, null);
            EncounterService.Result missing =
                    EncounterService.refresh(server, data, encounter);
            helper.assertValueEqual(
                    missing.status(),
                    EncounterService.Status.WAITING,
                    "missing encounter definition was not dormant");
            helper.assertValueEqual(
                    encounter.state(),
                    EncounterInstance.EncounterState.PREPARED,
                    "missing encounter definition changed saved state");
            helper.assertTrue(
                    encounter.contentUnresolved()
                            && encounter.diagnostic().contains(templateId.toString()),
                    "missing encounter diagnostic omitted its stable ID");

            CompoundTag saved = encounter.save();
            resumed = EncounterInstance.load(saved);
            helper.assertValueEqual(
                    resumed.state(),
                    EncounterInstance.EncounterState.PREPARED,
                    "dormant encounter state did not reload");
            helper.assertTrue(
                    resumed.contentUnresolved()
                            && resumed.contentDiagnostic().contains(templateId.toString()),
                    "dormant encounter diagnostic did not reload");

            QuestContentCatalogs.installForTests(server, complete, null);
            EncounterService.Result restored =
                    EncounterService.refresh(server, data, resumed);
            helper.assertTrue(
                    restored.status() != EncounterService.Status.FAILED,
                    "restored encounter failed instead of resuming: " + restored.diagnostic());
            helper.assertFalse(
                    resumed.contentUnresolved(),
                    "restored encounter retained its missing-definition diagnostic");
            helper.assertValueEqual(
                    resumed.templateId(), templateId, "restored encounter changed stable identity");
            helper.assertTrue(
                    resumed.state() != EncounterInstance.EncounterState.FAILED,
                    "restored encounter remained failed");
        } finally {
            EncounterService.hideBossBar(resumed.id());
            QuestContentCatalogs.installForTests(server, baseline, null);
        }
        helper.succeed();
    }

    @GameTest(
            template = "empty",
            timeoutTicks = 100,
            batch = "encounter_missing_event_content")
    public static void deathEventsStayDormantUntilAllDefinitionsReturn(
            GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var templateId = VillagerRetaliation.id("dormant_event_fixture");
        List<String> errors = new ArrayList<>();
        EncounterTemplate template =
                EncounterResources.parse(
                        VillagerRetaliation.id("encounters/dormant_event_fixture.json"),
                        JsonParser.parseString(
                                        """
                                        {
                                          "schema": "villagerretaliation:encounter/v1",
                                          "id": "villagerretaliation:dormant_event_fixture",
                                          "members": [
                                            {"id": "guard", "entity": "minecraft:zombie"}
                                          ],
                                          "boss_bar": false,
                                          "failure": {
                                            "on_player_death": "branch_scene",
                                            "on_protected_actor_death": "fail",
                                            "retry_delay_ticks": 20,
                                            "max_attempts": 1,
                                            "retain_defeated": false,
                                            "branch_step": "fallback"
                                          }
                                        }
                                        """)
                                .getAsJsonObject(),
                        errors);
        helper.assertTrue(
                template != null && errors.isEmpty(),
                "event dormancy fixture did not parse: " + errors);

        var parsedScene =
                SceneParser.parse(
                        VillagerRetaliation.id("scenes/dormant_event_owner.json"),
                        JsonParser.parseString(
                                        """
                                        {
                                          "schema": "villagerretaliation:scene/v1",
                                          "id": "villagerretaliation:dormant_event_owner",
                                          "ownership": "player",
                                          "entry_step": "wait",
                                          "actors": [],
                                          "steps": [
                                            {
                                              "id": "wait",
                                              "type": "villagerretaliation:wait_ticks",
                                              "data": {"ticks": 200},
                                              "next": "fallback"
                                            },
                                            {
                                              "id": "fallback",
                                              "type": "villagerretaliation:scene_fail"
                                            }
                                          ]
                                        }
                                        """)
                                .getAsJsonObject());
        var compiledScene = SceneCompiler.compile(parsedScene.resource());
        helper.assertTrue(
                compiledScene.valid(),
                "event owner scene did not compile: " + compiledScene.diagnostics());

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SceneSavedData data = new SceneSavedData();
        var scene =
                data.start(
                                compiledScene.scene(),
                                "dormant_event_owner",
                                new SceneOwner(
                                        SceneResource.OwnershipMode.PLAYER,
                                        player.getUUID(),
                                        null,
                                        null,
                                        ""),
                                null,
                                Set.of(player.getUUID()),
                                Map.of(),
                                0L)
                        .instance();
        scene.currentRecord(compiledScene.scene().steps().get(scene.currentStep()).type());
        EncounterInstance encounter =
                data.startEncounter(
                                template,
                                scene,
                                "dormant_event/start",
                                helper.getLevel().dimension().location(),
                                helper.absolutePos(new BlockPos(4, 1, 4)),
                                "normal")
                        .encounter();
        QuestContentCatalog baseline = QuestContentCatalogs.current(server);
        QuestContentCatalog complete =
                withEncounterAndScene(
                        baseline, template, compiledScene.scene(), baseline.generation() + 1L);
        try {
            QuestContentCatalogs.installForTests(server, complete, null);
            EncounterService.Result spawned =
                    EncounterService.reconcileSpawn(server, data, encounter, template);
            helper.assertValueEqual(
                    spawned.status(), EncounterService.Status.ACTIVE, "fixture did not activate");
            var mob =
                    (net.minecraft.world.entity.LivingEntity)
                            helper.getLevel().getEntity(encounter.spawned().iterator().next());

            QuestContentCatalogs.installForTests(
                    server,
                    withScene(baseline, compiledScene.scene(), complete.generation() + 1L),
                    null);
            EncounterService.onDeath(mob, data);
            helper.assertTrue(
                    encounter.defeated().isEmpty()
                            && encounter.defeatedMemberIds().isEmpty()
                            && encounter.state() == EncounterInstance.EncounterState.ACTIVE,
                    "missing encounter definition consumed a death event");

            QuestContentCatalogs.installForTests(
                    server,
                    withEncounter(baseline, template, complete.generation() + 2L),
                    null);
            EncounterService.onDeath(player, data);
            helper.assertTrue(
                    encounter.state() == EncounterInstance.EncounterState.ACTIVE
                            && scene.stepRecords()
                                    .get(scene.currentStep())
                                    .chosenTransition()
                                    .isBlank(),
                    "missing owning scene definition applied a failure branch");

            QuestContentCatalogs.installForTests(server, complete, null);
            EncounterService.onDeath(mob, data);
            helper.assertTrue(
                    encounter.defeated().contains(mob.getUUID())
                            && encounter.state() == EncounterInstance.EncounterState.COMPLETED,
                    "restored definitions did not resume encounter death processing");
        } finally {
            EncounterService.hideBossBar(encounter.id());
            QuestContentCatalogs.installForTests(server, baseline, null);
        }
        helper.succeed();
    }

    @GameTest(
            template = "empty",
            timeoutTicks = 100,
            batch = "encounter_missing_reward_content")
    public static void missingEncounterRewardStaysPendingAndRetriesExactlyOnce(
            GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var templateId = VillagerRetaliation.id("dormant_encounter_reward_fixture");
        var rewardId = VillagerRetaliation.id("quest/bread_delivery");
        List<String> errors = new ArrayList<>();
        EncounterTemplate template =
                EncounterResources.parse(
                        VillagerRetaliation.id(
                                "encounters/dormant_encounter_reward_fixture.json"),
                        JsonParser.parseString(
                                        """
                                        {
                                          "schema": "villagerretaliation:encounter/v1",
                                          "id": "villagerretaliation:dormant_encounter_reward_fixture",
                                          "members": [
                                            {"id": "guard", "entity": "minecraft:zombie"}
                                          ],
                                          "boss_bar": false,
                                          "rewards": {
                                            "completion": [
                                              {
                                                "id": "completion",
                                                "loot_table": "villagerretaliation:quest/bread_delivery"
                                              }
                                            ]
                                          }
                                        }
                                        """)
                                .getAsJsonObject(),
                        errors);
        helper.assertTrue(
                template != null && errors.isEmpty(),
                "reward dormancy fixture did not parse: " + errors);

        var parsedScene =
                SceneParser.parse(
                        VillagerRetaliation.id("scenes/dormant_reward_owner.json"),
                        JsonParser.parseString(
                                        """
                                        {
                                          "schema": "villagerretaliation:scene/v1",
                                          "id": "villagerretaliation:dormant_reward_owner",
                                          "ownership": "player",
                                          "entry_step": "done",
                                          "actors": [],
                                          "steps": [
                                            {
                                              "id": "done",
                                              "type": "villagerretaliation:scene_complete"
                                            }
                                          ]
                                        }
                                        """)
                                .getAsJsonObject());
        var compiledScene = SceneCompiler.compile(parsedScene.resource());
        helper.assertTrue(
                compiledScene.valid(),
                "reward owner scene did not compile: " + compiledScene.diagnostics());

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SceneSavedData data = new SceneSavedData();
        var scene =
                data.start(
                                compiledScene.scene(),
                                "dormant_reward_owner",
                                new SceneOwner(
                                        SceneResource.OwnershipMode.PLAYER,
                                        player.getUUID(),
                                        null,
                                        null,
                                        ""),
                                null,
                                Set.of(player.getUUID()),
                                Map.of(),
                                0L)
                        .instance();
        EncounterInstance encounter =
                new EncounterInstance(
                        UUID.randomUUID(),
                        templateId,
                        scene.id(),
                        "dormant_reward/start",
                        "dormant_reward",
                        Set.of(player.getUUID()),
                        helper.getLevel().dimension().location(),
                        helper.absolutePos(new BlockPos(4, 1, 4)),
                        1,
                        "normal",
                        template.cleanupPolicy(),
                        template.completionCondition(),
                        1);
        encounter.complete();
        QuestContentCatalog baseline = QuestContentCatalogs.current(server);
        QuestContentCatalog complete =
                withEncounter(baseline, template, baseline.generation() + 1L);
        try {
            QuestContentCatalogs.installForTests(server, complete, null);
            helper.assertTrue(
                    complete.rewards().bundled(rewardId).isPresent(),
                    "fixture reward was not available before removal");

            QuestContentCatalogs.installForTests(
                    server,
                    withoutRewards(complete, complete.generation() + 1L),
                    null);
            EncounterService.Result unresolved =
                    EncounterService.refresh(server, data, encounter);
            helper.assertValueEqual(
                    unresolved.status(),
                    EncounterService.Status.WAITING,
                    "missing encounter reward was not left pending");
            helper.assertValueEqual(
                    encounter.state(),
                    EncounterInstance.EncounterState.COMPLETED,
                    "missing reward changed terminal encounter state");
            helper.assertTrue(
                    encounter.contentUnresolved()
                            && encounter.diagnostic().contains(rewardId.toString()),
                    "missing reward diagnostic omitted its stable ID");
            helper.assertTrue(
                    scene.receipts().isEmpty(),
                    "missing reward reserved a receipt before it could roll");

            EncounterInstance reloaded = EncounterInstance.load(encounter.save());
            helper.assertTrue(
                    reloaded.completionRewardEligible() && reloaded.contentUnresolved(),
                    "pending reward dormancy did not survive save/load");

            QuestContentCatalogs.installForTests(server, complete, null);
            EncounterService.Result restored =
                    EncounterService.refresh(server, data, reloaded);
            helper.assertValueEqual(
                    restored.status(),
                    EncounterService.Status.COMPLETED,
                    "restored encounter reward did not resume");
            helper.assertFalse(
                    reloaded.contentUnresolved(),
                    "restored reward left the encounter dormant");
            helper.assertValueEqual(
                    scene.receipts().size(), 1, "restored reward did not create one receipt");
            EncounterService.refresh(server, data, reloaded);
            helper.assertValueEqual(
                    scene.receipts().size(), 1, "restored reward replayed after its receipt");
        } finally {
            EncounterService.hideBossBar(encounter.id());
            QuestContentCatalogs.installForTests(server, baseline, null);
        }
        helper.succeed();
    }

    private static QuestContentCatalog withEncounter(
            QuestContentCatalog source,
            EncounterTemplate template,
            long generation) {
        Map<net.minecraft.resources.ResourceLocation, EncounterTemplate> encounters =
                new LinkedHashMap<>(source.encounters());
        encounters.put(template.id(), template);
        return new QuestContentCatalog(
                generation,
                source.compiledQuestCatalog(),
                source.dialogueCatalog(),
                source.quests(),
                source.objectiveEventQuestIds(),
                source.factQuestIds(),
                source.memoryEventQuestIds(),
                source.exclusiveGroupQuestIds(),
                source.triggerEventQuestIds(),
                source.scenes(),
                encounters,
                source.pools(),
                source.bundles(),
                source.localization(),
                source.rewards());
    }

    private static QuestContentCatalog withScene(
            QuestContentCatalog source, CompiledScene scene, long generation) {
        Map<net.minecraft.resources.ResourceLocation, CompiledScene> scenes =
                new LinkedHashMap<>(source.scenes());
        scenes.put(scene.id(), scene);
        return new QuestContentCatalog(
                generation,
                source.compiledQuestCatalog(),
                source.dialogueCatalog(),
                source.quests(),
                source.objectiveEventQuestIds(),
                source.factQuestIds(),
                source.memoryEventQuestIds(),
                source.exclusiveGroupQuestIds(),
                source.triggerEventQuestIds(),
                scenes,
                source.encounters(),
                source.pools(),
                source.bundles(),
                source.localization(),
                source.rewards());
    }

    private static QuestContentCatalog withEncounterAndScene(
            QuestContentCatalog source,
            EncounterTemplate template,
            CompiledScene scene,
            long generation) {
        QuestContentCatalog withScene = withScene(source, scene, generation);
        return withEncounter(withScene, template, generation);
    }

    private static QuestContentCatalog withoutRewards(
            QuestContentCatalog source, long generation) {
        return new QuestContentCatalog(
                generation,
                source.compiledQuestCatalog(),
                source.dialogueCatalog(),
                source.quests(),
                source.objectiveEventQuestIds(),
                source.factQuestIds(),
                source.memoryEventQuestIds(),
                source.exclusiveGroupQuestIds(),
                source.triggerEventQuestIds(),
                source.scenes(),
                source.encounters(),
                source.pools(),
                source.bundles(),
                source.localization(),
                QuestRewardCatalog.empty());
    }
}
