package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.EntityDialogueContext;
import com.jvn.villagerretaliation.quest.content.QuestContentAccess;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.follower.QuestFollowerSavedData;
import com.jvn.villagerretaliation.quest.follower.QuestFollowerService;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressModel;
import com.jvn.villagerretaliation.quest.runtime.QuestModelStore;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressTypes;
import com.jvn.villagerretaliation.quest.runtime.QuestRuntimeAccess;
import com.jvn.villagerretaliation.quest.runtime.QuestRuntimes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_quests")
@PrefixGameTestTemplate(false)
public final class QuestBackendGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("backend_fixture:escort");

    private QuestBackendGameTests() { }

    @GameTest(template = "empty", batch = "narrative_backends")
    public static void followersRetainTheirBackendAndPauseWithoutIt(GameTestHelper helper) {
        var level = helper.getLevel();
        var original = QuestFollowerSavedData.get(level);
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var wolf = helper.spawn(EntityType.WOLF, 2, 1, 1);
        cow.setNoAi(true);
        player.teleportTo(wolf.getX(), wolf.getY(), wolf.getZ());
        var backend = new Backend();
        var progress = backend.store.getOrCreate(player.getUUID(), QUEST);
        progress.start(cow.getUUID(), level.dimension(), null, level.getGameTime());
        progress.beginRun(player.getUUID(), QUEST, null);
        progress.setCurrentStage("escort");
        level.getServer().overworld().getDataStorage().set("villagerretaliation_followers", new QuestFollowerSavedData());
        try (var registration = QuestRuntimes.register(QUEST.getNamespace(), backend)) {
            var context = new EntityDialogueContext(level, player, cow, "en_us", "");
            var raw = JsonParser.parseString("""
                    {"type":"follower","command":"follow","protected":true,
                     "actor":{"alias":"companion","type":"villagerretaliation:living_entity","binding_source":"uuid","binding":"%s"}}
                    """.formatted(wolf.getUUID())).getAsJsonObject();
            var action = VillagerActionDefinition.readObjects(QUEST, "test", List.of(raw), QUEST).getFirst();
            helper.assertTrue(QuestFollowerService.execute(context, action, (actor, owner) -> "").success(), "a custom quest store can own a follower");
            helper.assertTrue(QuestFollowerService.controls(wolf), "follower control reads the configured store");
            helper.assertTrue(QuestFollowerService.protects(wolf), "follower protection reads the configured store");
            helper.assertTrue(QuestRuntimes.progress(level, player.getUUID(), QUEST) == progress, "shared lookups return the consumer's model");
            helper.assertTrue(VillagerQuestSavedData.get(level).get(player.getUUID(), QUEST) == null, "the shared runtime does not create a VR quest record");
            var saved = QuestFollowerSavedData.get(level).save(new CompoundTag(), level.registryAccess());
            helper.assertValueEqual(saved.getList("Followers", 10).getCompound(0).getString("Runtime"), backend.id().toString(), "the follower saves its backend identity");
            level.getServer().overworld().getDataStorage().set("villagerretaliation_followers", QuestFollowerSavedData.load(saved, level.registryAccess()));
            registration.close();
            QuestFollowerService.tickMob(wolf);
            helper.assertFalse(QuestFollowerService.controls(wolf), "missing backend suspends navigation");
            helper.assertTrue(QuestFollowerService.retained(wolf), "missing backend retains the follower's saved binding");
            helper.assertFalse(QuestFollowerService.command(player, wolf.getUUID(), false), "missing backend cannot authorize commands");
            helper.assertValueEqual(QuestFollowerSavedData.get(level).save(new CompoundTag(), level.registryAccess())
                    .getList("Followers", 10).getCompound(0).getString("Runtime"), backend.id().toString(), "saving while unavailable retains ownership");
            try (var restored = QuestRuntimes.register(QUEST.getNamespace(), backend)) {
                registration.close();
                helper.assertTrue(QuestFollowerService.controls(wolf), "the same backend can resume its saved follower");
                helper.assertTrue(QuestFollowerService.command(player, wolf.getUUID(), false), "the original player retains command authority");
                var stranger = helper.makeMockServerPlayerInLevel();
                stranger.teleportTo(wolf.getX(), wolf.getY(), wolf.getZ());
                helper.assertFalse(QuestFollowerService.command(stranger, wolf.getUUID(), true), "another player's missing quest cannot authorize commands");
                helper.assertTrue(backend.fail(player, QUEST, progress.questRunId()), "the backend applies its own failure policy");
                QuestFollowerService.tickMob(wolf);
                helper.assertFalse(QuestFollowerService.retained(wolf), "terminal consumer quests release their followers");
            }
        } finally {
            level.getServer().overworld().getDataStorage().set("villagerretaliation_followers", original);
            cow.discard();
            wolf.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "narrative_backends")
    public static void sceneContinuationsUseTheConfiguredBackendAndRejectReplacedRuns(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        cow.setNoAi(true);
        var backend = new Backend();
        var progress = backend.store.getOrCreate(player.getUUID(), QUEST);
        progress.start(cow.getUUID(), level.dimension(), null, level.getGameTime());
        progress.beginRun(player.getUUID(), QUEST, null);
        var original = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(level);
        var sceneId = ResourceLocation.parse("backend_fixture:wait");
        var parsed = com.jvn.villagerretaliation.scene.compiler.SceneParser.parse(sceneId,
                JsonParser.parseString("""
                        {"schema":"villagerretaliation:scene/v1","id":"backend_fixture:wait","ownership":"player","entry_step":"wait",
                         "actors":[{"alias":"provider","type":"villagerretaliation:living_entity","binding_source":"quest_provider"}],
                         "steps":[{"id":"wait","type":"villagerretaliation:wait_ticks","data":{"ticks":20},"next":"done"},
                           {"id":"done","type":"villagerretaliation:scene_complete"}]}
                        """).getAsJsonObject());
        var compiled = com.jvn.villagerretaliation.scene.compiler.SceneCompiler.compile(parsed.resource());
        helper.assertTrue(compiled.valid(), "backend scene compiles");
        try (var runtime = QuestRuntimes.register(QUEST.getNamespace(), backend);
                var factory = com.jvn.villagerretaliation.dialogue.NarrativeActors.register(ResourceLocation.parse("minecraft:cow"),
                        (world, viewer, speaker) -> new EntityDialogueContext(world, viewer, speaker, "en_us", ""))) {
            com.jvn.villagerretaliation.scene.SceneResources.installTestScenes(level.getServer(), List.of(compiled.scene()));
            com.jvn.villagerretaliation.scene.SceneRuntime.initialize(level.getServer());
            var data = new com.jvn.villagerretaliation.scene.persistence.SceneSavedData();
            level.getServer().overworld().getDataStorage().set("villagerretaliation_scenes", data);
            var context = new EntityDialogueContext(level, player, cow, "en_us", "");
            for (boolean replaceRun : List.of(false, true)) {
                String operation = replaceRun ? "changed_run" : "same_run";
                var raw = JsonParser.parseString("""
                        [{"type":"start_scene","scene":"backend_fixture:wait","quest":"backend_fixture:escort",
                          "operation_id":"%s","wait_for_result":true,"required":true},
                         {"type":"experience","amount":7}]
                        """.formatted(operation)).getAsJsonArray();
                var actions = VillagerActionDefinition.readObjects(QUEST, "test", raw.asList().stream().map(com.google.gson.JsonElement::getAsJsonObject).toList(), QUEST);
                int before = player.totalExperience;
                UUID expectedRun = progress.questRunId();
                var result = com.jvn.villagerretaliation.scene.SceneContinuationService.run(context, actions, Map.of(), () -> {}, operation, null);
                helper.assertTrue(result.outcome().suspended(), "backend actions suspend on the launched scene");
                var continuation = data.continuation(result.continuationId()).orElseThrow();
                helper.assertValueEqual(continuation.questRunId(), expectedRun, "continuations retain the backend quest run");
                var scene = data.get(continuation.sceneInstanceId()).orElseThrow();
                helper.assertValueEqual(scene.owningQuestInstance(), expectedRun, "scene launch retains the backend quest run");
                helper.assertValueEqual(scene.actorBindings().get("provider").sourceType(), ResourceLocation.parse("villagerretaliation:living_entity"),
                        "generic scene providers are not marked as villager providers");
                data = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.load(
                        data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
                level.getServer().overworld().getDataStorage().set("villagerretaliation_scenes", data);
                continuation = data.continuation(result.continuationId()).orElseThrow();
                if (replaceRun) {
                    progress.start(cow.getUUID(), level.dimension(), null, level.getGameTime() + 1);
                    progress.beginRun(player.getUUID(), QUEST, null);
                    helper.assertFalse(expectedRun.equals(progress.questRunId()), "replacement quest run has a new identity");
                }
                com.jvn.villagerretaliation.scene.runtime.SceneTransitionService.complete(data,
                        data.get(continuation.sceneInstanceId()).orElseThrow(), level.getGameTime());
                com.jvn.villagerretaliation.scene.SceneContinuationService.maintain(level.getServer(), data);
                helper.assertTrue(continuation.completionReceipt(), "backend continuation records its completion result");
                helper.assertValueEqual(player.totalExperience, replaceRun ? before : before + 7,
                        "only the original backend run can apply its waiting reward");
                if (replaceRun) helper.assertValueEqual(continuation.actionStatus(), com.jvn.villagerretaliation.action.ActionStatus.UNAVAILABLE,
                        "a changed backend run is unavailable");
                com.jvn.villagerretaliation.scene.SceneContinuationService.maintain(level.getServer(), data);
                helper.assertValueEqual(player.totalExperience, replaceRun ? before : before + 7, "completed continuations do not replay rewards");
            }
        } finally {
            level.getServer().overworld().getDataStorage().set("villagerretaliation_scenes", original);
            com.jvn.villagerretaliation.scene.SceneResources.clearCache();
            com.jvn.villagerretaliation.scene.SceneRuntime.initialize(level.getServer());
            cow.discard();
        }
        helper.succeed();
    }

    private static final class Store implements QuestModelStore<QuestProgressModel> {
        private final Map<UUID, Map<ResourceLocation, QuestProgressModel>> entries = new HashMap<>();

        @Override public QuestProgressModel get(UUID player, ResourceLocation quest) {
            return entries.getOrDefault(player, Map.of()).get(quest);
        }

        @Override public QuestProgressModel getOrCreate(UUID player, ResourceLocation quest) {
            return entries.computeIfAbsent(player, ignored -> new HashMap<>()).computeIfAbsent(quest, ignored -> new QuestProgressModel());
        }

        @Override public List<Map.Entry<ResourceLocation, QuestProgressModel>> activeProgress(UUID player) {
            return entries.getOrDefault(player, Map.of()).entrySet().stream()
                    .filter(entry -> entry.getValue().status() == QuestProgressTypes.QuestState.ACTIVE).toList();
        }

        @Override public void changed() { }
    }

    private static final class Backend implements QuestRuntimeAccess {
        private final Store store = new Store();
        private final QuestDefinition definition = QuestDefinitionFixtures.read(QUEST,
                JsonParser.parseString("{\"objectives\":[]}").getAsJsonObject(), QUEST);
        private final QuestContentAccess content = new QuestContentAccess() {
            @Override public Optional<QuestDefinition> quest(ResourceLocation id) { return QUEST.equals(id) ? Optional.of(definition) : Optional.empty(); }
            @Override public Optional<CompiledQuest> compiledQuest(ResourceLocation id) { return Optional.empty(); }
            @Override public Optional<QuestTriggerIndex> questTriggerIndex(ResourceLocation id) { return Optional.empty(); }
            @Override public Set<ResourceLocation> questIdsForObjectiveEvent(QuestObjectiveEventKind kind) { return Set.of(); }
            @Override public Set<ResourceLocation> memoryEventQuestIds(ResourceLocation tag) { return Set.of(); }
        };

        @Override public ResourceLocation id() { return ResourceLocation.parse("backend_fixture:quests"); }
        @Override public QuestModelStore<QuestProgressModel> progress(ServerLevel level) { return store; }
        @Override public QuestContentAccess content(MinecraftServer server) { return content; }

        @Override public boolean fail(ServerPlayer player, ResourceLocation quest, UUID expectedRun) {
            var progress = store.get(player.getUUID(), quest);
            if (progress == null || !expectedRun.equals(progress.questRunId())) return false;
            progress.fail(player.serverLevel().getGameTime(), "follower_missing");
            store.changed();
            return true;
        }
    }
}
