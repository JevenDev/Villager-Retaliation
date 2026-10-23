package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.runtime.QuestPerformanceState;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_mechanics")
@PrefixGameTestTemplate(false)
public final class QuestPerformanceGameTests {
    @GameTest(template = "empty")
    public static void tiersUseInclusiveBoundsAllRequirementsAndAuthoredOrder(GameTestHelper helper) {
        var performance = fixture().rewards().performance();
        helper.assertValueEqual(performance.select(Map.of("health", 80.0, "speed", 40.0, "bonus", 1.0)).id(), "gold", "exact thresholds earn gold");
        helper.assertValueEqual(performance.select(Map.of("health", 80.0, "speed", 41.0, "bonus", 1.0)).id(), "iron", "one failed requirement selects the next tier");
        helper.assertValueEqual(performance.select(Map.of("health", 39.0)).id(), "copper", "lower health earns copper");
        helper.assertTrue(performance.select(Map.of()) == null, "unknown measurements never satisfy a threshold");
        var rewards = performance.tiers().getFirst().rewards().apply(fixture().rewards());
        helper.assertValueEqual(rewards.experience(), 0, "explicit zero overrides the base");
        helper.assertValueEqual(rewards.reputation(), 5, "omitted fields inherit the base");
        helper.assertValueEqual(rewards.lootTable(), ResourceLocation.parse("test:gold"), "tier chooses another loot table");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void healthTimeAndOptionalObjectivesSurviveReloadAndFreeze(GameTestHelper helper) {
        var quest = fixture();
        var data = new VillagerQuestSavedData();
        UUID player = UUID.randomUUID();
        var progress = data.getOrCreate(player, quest.id());
        progress.start(UUID.randomUUID(), Level.OVERWORLD, null, 100);
        QuestLifecycleService.initializeStage(quest, progress, 100);
        var state = progress.performance();
        state.update(quest, progress, 100, true, true, actor -> 100.0);
        state.update(quest, progress, 120, true, true, actor -> 80.0);
        state.update(quest, progress, 130, false, true, actor -> 90.0);
        data = VillagerQuestSavedData.load(data.save(new CompoundTag(), null), null);
        progress = data.get(player, quest.id());
        state = progress.performance();
        state.update(quest, progress, 500, true, true, actor -> 100.0);
        helper.assertValueEqual(state.values().get("speed"), 30.0, "offline time is excluded after reload");
        helper.assertValueEqual(state.values().get("offline"), 400.0, "offline clock option works independently of inactive time");
        helper.assertValueEqual(state.values().get("health"), 80.0, "healing does not erase lowest health");
        helper.assertValueEqual(state.values().get("final"), 100.0, "final health includes healing");
        progress.markObjectiveComplete("work.bonus");
        state.update(quest, progress, 510, true, true, actor -> 100.0);
        helper.assertValueEqual(state.values().get("bonus"), 1.0, "existing optional objectives count");
        state.freeze(quest.rewards());
        helper.assertValueEqual(state.tier(), "gold", "performance freezes at completion");
        state = QuestPerformanceState.load(state.save());
        state.update(quest, progress, 9999, true, true, actor -> 0.0);
        state.event(quest.rewards().performance(), QuestPerformance.Metric.DEATHS, 1, metric -> true);
        helper.assertValueEqual(state.tier(), "gold", "reload and delayed claim retain the tier");
        helper.assertValueEqual(state.values().get("speed"), 40.0, "time stops at completion");
        helper.assertValueEqual(state.resolvedRewards().experience(), 0, "resolved rewards survive reload");
        progress.start(UUID.randomUUID(), Level.OVERWORLD, null, 10000);
        helper.assertFalse(progress.performance().frozen(), "a repeated run resets performance");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scopedMeasurementsAccumulateAcrossVisitsAndRespectClockOptions(GameTestHelper helper) {
        var quest = fixture();
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(UUID.randomUUID(), Level.OVERWORLD, null, 100);
        QuestLifecycleService.initializeStage(quest, progress, 100);
        var state = progress.performance();
        state.update(quest, progress, 110, true, false, actor -> 90.0);
        state.update(quest, progress, 200, true, false, actor -> 90.0);
        helper.assertValueEqual(state.values().get("speed"), 10.0, "inactive time pauses the default clock");
        helper.assertValueEqual(state.values().get("world"), 100.0, "configured world clock includes inactive time");
        state.update(quest, progress, 210, true, true, actor -> 90.0);
        QuestLifecycleService.transitionStage(quest, progress, "done", 220);
        state.update(quest, progress, 400, true, true, actor -> 50.0);
        helper.assertValueEqual(state.values().get("speed"), 20.0, "stage time stops on exit");
        helper.assertValueEqual(state.values().get("final"), 90.0, "stage health is frozen on exit");
        QuestLifecycleService.transitionStage(quest, progress, "work", 400);
        state.update(quest, progress, 410, true, true, actor -> 50.0);
        helper.assertValueEqual(state.values().get("speed"), 30.0, "repeated visits accumulate time");
        helper.assertValueEqual(state.values().get("health"), 50.0, "lowest health spans visits");
        state.event(quest.rewards().performance(), QuestPerformance.Metric.DEATHS, 1, metric -> true);
        state.event(quest.rewards().performance(), QuestPerformance.Metric.DAMAGE_TAKEN, 3.5, metric -> true);
        state.event(quest.rewards().performance(), QuestPerformance.Metric.KILLS, 2, metric -> true);
        helper.assertValueEqual(state.values().get("deaths"), 1.0, "deaths count within the scope");
        helper.assertValueEqual(state.values().get("damage"), 3.5, "damage retains fractional health");
        helper.assertValueEqual(state.values().get("kills"), 2.0, "kills accumulate");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unavailableActorsAndLegacyHistoryCannotEarnInventedGrades(GameTestHelper helper) {
        var quest = fixture();
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(UUID.randomUUID(), Level.OVERWORLD, null, 100);
        QuestLifecycleService.initializeStage(quest, progress, 100);
        var state = progress.performance();
        state.update(quest, progress, 100, true, true, actor -> 100.0);
        state.update(quest, progress, 110, true, true, actor -> null);
        helper.assertFalse(state.values().containsKey("final"), "unloaded actors have unknown final health");
        state.health(quest.rewards().performance(), "companion", 0);
        helper.assertValueEqual(state.values().get("health"), 0.0, "confirmed death records zero");
        state.actorReleased("companion");
        state.update(quest, progress, 120, true, true, actor -> null);
        helper.assertValueEqual(state.values().get("final"), 0.0, "explicit release preserves captured health");
        var legacy = new QuestPerformanceState(false);
        legacy.update(quest, progress, 120, true, true, actor -> 100.0);
        helper.assertValueEqual(legacy.freeze(quest.rewards()).experience(), 10, "legacy runs receive base rewards");
        helper.assertValueEqual(legacy.tier(), "", "legacy runs have no fabricated grade");
        var retrofit = new QuestPerformanceState(true);
        retrofit.update(quest, progress, 120, true, true, actor -> 100.0);
        helper.assertValueEqual(retrofit.freeze(quest.rewards()).experience(), 10, "adding measurements to an ongoing run cannot invent history");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidPerformanceDefinitionsFailValidation(GameTestHelper helper) {
        var raw = rawFixture();
        for (String invalid : new String[] {
                "{\"measurements\": {}, \"tiers\": []}",
                "{\"measurements\": {\"time\": {\"metric\": \"elapsed_ticks\"}}, \"tiers\": [{\"id\": \"gold\", \"requirements\": {\"missing\": {\"min\": 1}}, \"rewards\": {}}]}",
                "{\"measurements\": {\"time\": {\"metric\": \"elapsed_ticks\"}}, \"tiers\": [{\"id\": \"gold\", \"requirements\": {\"time\": {\"min\": 2, \"max\": 1}}, \"rewards\": {}}]}" }) {
            boolean rejected = false;
            try { QuestPerformance.read(JsonParser.parseString(invalid)); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "invalid performance is rejected");
        }
        raw.getAsJsonObject("performance").getAsJsonObject("measurements").getAsJsonObject("bonus").addProperty("objective", "work.required");
        helper.assertTrue(QuestV2Parser.parse(ResourceLocation.parse("test:performance"), raw).isEmpty(), "optional metric cannot reference a required objective");
        var malformed = rawFixture();
        malformed.getAsJsonArray("stages").add(com.google.gson.JsonNull.INSTANCE);
        helper.assertTrue(QuestV2Parser.parse(ResourceLocation.parse("test:performance"), malformed).isEmpty(), "malformed stages produce diagnostics without crashing performance validation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_performance_live")
    public static void liveFollowerDamageAndFilteredKillsReachPerformanceTracking(GameTestHelper helper) throws Exception {
        try (var preserved = VillagerQuestResources.preserveTestOverride()) {
            var compiled = compiledFixture();
            var quest = compiled.asQuestDefinition();
            var level = helper.getLevel();
            VillagerQuestResources.installCompiledTestCatalog(level.getServer(), java.util.List.of(compiled));
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            for (int tick = 0; tick < 60; tick++) player.tick();
            var progress = VillagerQuestSavedData.get(level).getOrCreate(player.getUUID(), quest.id());
            progress.start(UUID.randomUUID(), level.dimension(), null, level.getGameTime());
            progress.beginRun(player.getUUID(), quest.id(), null);
            QuestLifecycleService.initializeStage(quest, progress, level.getGameTime());
            var wolf = net.minecraft.world.entity.EntityType.WOLF.create(level);
            wolf.setPos(player.getX() + 2, player.getY(), player.getZ());
            wolf.addTag("performance_test");
            level.addFreshEntity(wolf);
            var context = com.jvn.villagerretaliation.dialogue.DialogueContext.forPlayer(player);
            var action = quest.stages().get("work").entryActions().getFirst();
            helper.assertTrue(com.jvn.villagerretaliation.quest.follower.QuestFollowerService.execute(context, action).success(), "follower binds to this quest run");
            wolf.setHealth(wolf.getMaxHealth() / 2);
            com.jvn.villagerretaliation.quest.follower.QuestFollowerService.samplePerformance(wolf, false);
            wolf.setHealth(wolf.getMaxHealth());
            com.jvn.villagerretaliation.quest.runtime.QuestPerformanceService.update(level, player.getUUID(), quest, progress);
            helper.assertValueEqual(progress.performance().values().get("health"), 50.0, "live follower remembers lowest health");
            helper.assertValueEqual(progress.performance().values().get("final"), 100.0, "live follower can heal");
            player.setHealth(20);
            helper.assertTrue(player.hurt(level.damageSources().generic(), 3), "test damage reaches the player after spawn protection expires");
            helper.assertValueEqual(progress.performance().values().get("damage"), 3.0, "post-mitigation player damage reaches quest tracking");
            player.invulnerableTime = 0;
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 200));
            player.setAbsorptionAmount(4);
            helper.assertValueEqual(player.getAbsorptionAmount(), 4.0F, "the absorption effect supplies a real shield");
            player.hurt(level.damageSources().generic(), 3);
            helper.assertValueEqual(progress.performance().values().get("damage"), 3.0, "absorbed damage does not count as lost health");
            VillagerQuestService.onEntityKilled(wolf, player);
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            VillagerQuestService.onEntityKilled(zombie, player);
            helper.assertValueEqual(progress.performance().values().get("kills"), 2.0, "each player-attributed kill counts once");
            helper.assertValueEqual(progress.performance().values().get("zombies"), 1.0, "kill filters exclude other entity types");
            com.jvn.villagerretaliation.quest.follower.QuestFollowerService.release(level, wolf.getUUID());
            com.jvn.villagerretaliation.quest.runtime.QuestPerformanceService.update(level, player.getUUID(), quest, progress);
            helper.assertValueEqual(progress.performance().values().get("final"), 100.0, "cleanup preserves captured actor health");
            wolf.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void journalRequirementsAndTierPreviewsSurviveNetworkEncoding(GameTestHelper helper) {
        var quest = fixture();
        var description = com.jvn.villagerretaliation.quest.tracking.QuestPerformancePresentation.description(null, quest, "A task. ".repeat(40));
        helper.assertTrue(description.length() > 256 && description.contains("80%") && description.contains("gold"),
                "journal includes every tier requirement");
        var rewards = com.jvn.villagerretaliation.quest.tracking.QuestTrackerPresenter.rewardPreviews(null, quest, Map.of());
        helper.assertTrue(rewards.stream().anyMatch(value -> value.label().equals("gold")), "reward previews label the tier");
        var entry = new com.jvn.villagerretaliation.network.QuestTrackerSyncPayload.Entry(
                quest.id().toString(), "Task", "Work", description, "", 0, false, "active",
                "", "", "", java.util.List.of(), rewards, java.util.List.of(), java.util.List.of(), false, false);
        var payload = new com.jvn.villagerretaliation.network.QuestTrackerSyncPayload(java.util.List.of(entry), quest.id().toString(), false);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            com.jvn.villagerretaliation.network.QuestTrackerSyncPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = com.jvn.villagerretaliation.network.QuestTrackerSyncPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded.entries().getFirst().description(), description, "network preserves long performance requirements");
            helper.assertValueEqual(decoded.entries().getFirst().rewardPreviews(), rewards, "network preserves tier reward labels");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void changedMeasurementsUseBaseRewardsAndFrozenDeliveryResumesWithoutReselection(GameTestHelper helper) {
        var quest = fixture();
        UUID player = UUID.randomUUID();
        var data = new VillagerQuestSavedData();
        var progress = data.getOrCreate(player, quest.id());
        progress.start(UUID.randomUUID(), Level.OVERWORLD, null, 100);
        QuestLifecycleService.initializeStage(quest, progress, 100);
        progress.performance().update(quest, progress, 100, true, true, actor -> 100.0);
        var saved = progress.performance().save();
        saved.putString("TrackingSignature", "older measurement definitions");
        var changed = QuestPerformanceState.load(saved);
        changed.update(quest, progress, 110, true, true, actor -> 100.0);
        helper.assertValueEqual(changed.freeze(quest.rewards()).experience(), 10, "changed tracking rules cannot invent a complete history");
        progress.markObjectiveComplete("work.bonus");
        progress.performance().update(quest, progress, 110, true, true, actor -> 100.0);
        var selected = progress.performance().freeze(quest.rewards());
        progress.beginCompletion(selected);
        var reputations = new java.util.concurrent.atomic.AtomicInteger();
        com.jvn.villagerretaliation.quest.runtime.QuestRewardDeliveryService.deliver(progress, selected, Map.of(),
                (action, values) -> {
                    if (action.kind() == com.jvn.villagerretaliation.action.VillagerActionDefinition.Kind.LOOT)
                        return com.jvn.villagerretaliation.action.ActionResult.unavailable("temporarily unavailable", java.util.Set.of(),
                                com.jvn.villagerretaliation.action.VillagerActionResult.EMPTY);
                    if (action.kind() == com.jvn.villagerretaliation.action.VillagerActionDefinition.Kind.REPUTATION) reputations.incrementAndGet();
                    return com.jvn.villagerretaliation.action.ActionResult.success(
                            com.jvn.villagerretaliation.action.VillagerActionResult.success(), java.util.Set.of());
                }, (action, result) -> { }, () -> { });
        helper.assertValueEqual(progress.rewardDelivery().nextAction(), 4, "delivery checkpoints before the unresolved loot action");
        data = VillagerQuestSavedData.load(data.save(new CompoundTag(), null), null);
        progress = data.get(player, quest.id());
        var result = com.jvn.villagerretaliation.quest.runtime.QuestRewardDeliveryService.deliver(progress,
                QuestDefinition.Rewards.EMPTY, Map.of(), (action, values) -> {
                    helper.assertValueEqual(action.lootTable(), ResourceLocation.parse("test:gold"), "retry keeps the selected tier's loot");
                    return com.jvn.villagerretaliation.action.ActionResult.success(
                            com.jvn.villagerretaliation.action.VillagerActionResult.success(), java.util.Set.of());
                }, (action, outcome) -> { }, () -> { });
        helper.assertTrue(result.success(), "delivery resumes successfully after reload");
        helper.assertValueEqual(reputations.get(), 1, "already delivered reputation is not repeated");
        helper.succeed();
    }

    private static QuestDefinition fixture() {
        return compiledFixture().asQuestDefinition();
    }

    private static com.jvn.villagerretaliation.quest.compiled.CompiledQuest compiledFixture() {
        var raw = rawFixture();
        var envelope = QuestResourceEnvelope.read(ResourceLocation.parse("test:quests/performance.json"), raw).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }

    private static com.google.gson.JsonObject rawFixture() {
        return JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"test:performance",
                 "provider":{"type":"villagerretaliation:villager"},"entry_stage":"work",
                 "stages":[{"id":"work","followers":[{"command":"follow","actor":{"alias":"companion","type":"villagerretaliation:living_entity","binding_source":"entity_tag","binding":"performance_test"}}],
                   "objectives":[{"id":"required","type":"condition","timer":{"duration_ticks":20,"mode":"duration"}},
                   {"id":"bonus","type":"condition","optional":true,"timer":{"duration_ticks":20,"mode":"duration"}}],"next":"done"},
                   {"id":"done","objectives":[]}],
                 "rewards":{"experience":10,"reputation":5},
                 "performance":{"measurements":{
                   "speed":{"metric":"elapsed_ticks","stage":"work"},
                   "world":{"metric":"elapsed_ticks","include_inactive":true,"include_offline":true},
                   "offline":{"metric":"elapsed_ticks","include_offline":true},
                   "health":{"metric":"lowest_health_percent","actor":"companion","stage":"work"},
                   "final":{"metric":"final_health_percent","actor":"companion","stage":"work"},
                   "bonus":{"metric":"optional_objectives","objective":"work.bonus"},
                   "deaths":{"metric":"deaths"},"damage":{"metric":"damage_taken"},"kills":{"metric":"kills"},
                   "zombies":{"metric":"kills","entity_types":["minecraft:zombie"]}},
                   "tiers":[{"id":"gold","requirements":{"health":{"min":80},"speed":{"max":40},"bonus":{"min":1}},
                     "rewards":{"experience":0,"loot_table":"test:gold"}},
                     {"id":"iron","requirements":{"health":{"min":40}},"rewards":{"experience":20}},
                     {"id":"copper","requirements":{"health":{"min":0}},"rewards":{}}]}}
                """).getAsJsonObject();
    }
}
