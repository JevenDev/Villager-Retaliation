package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatcher;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressionService;
import com.jvn.villagerretaliation.quest.runtime.QuestRewardDeliveryService;
import com.jvn.villagerretaliation.quest.runtime.QuestStartRules;
import com.jvn.villagerretaliation.quest.runtime.QuestStateMachine;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_reliability")
@PrefixGameTestTemplate(false)
public final class QuestReliabilityGameTests {
    private static final UUID PLAYER = new UUID(0, 701);
    private static final UUID ISSUER = new UUID(0, 702);

    private QuestReliabilityGameTests() { }

    @GameTest(template = "empty")
    public static void repeatedCompletionsHaveFreshRunsAndExactlyOneRewardPlan(GameTestHelper helper) {
        var quest = detachedFixture("\"repeatable\":true,\"completion_cooldown_ticks\":20");
        var data = new VillagerQuestSavedData();
        var progress = data.getOrCreate(PLAYER, quest.id());
        List<Integer> rewards = new ArrayList<>();
        start(quest, progress, ISSUER, 0);
        UUID firstRun = progress.questRunId();
        progress.beginCompletion(quest.rewards());
        deliver(progress, quest, rewards);
        helper.assertTrue(QuestLifecycleService.complete(quest.id(), progress, 1, quest.rules().consumeOnCompletion()).type()
                == QuestLifecycleService.LifecycleEventType.COMPLETED, "first completion commits");
        helper.assertFalse(QuestStateMachine.complete(progress, 2, false).dirty(), "duplicate completion is blocked");
        deliver(progress, quest, rewards);
        helper.assertValueEqual(rewards, List.of(7), "repeated claims cannot replay the first reward");
        data = roundTrip(data);
        progress = data.get(PLAYER, quest.id());
        helper.assertFalse(eligible(quest, progress, ISSUER, 20), "cooldown holds immediately before its boundary");
        helper.assertTrue(eligible(quest, progress, ISSUER, 21), "cooldown permits the next completion run");
        start(quest, progress, ISSUER, 21);
        helper.assertFalse(firstRun.equals(progress.questRunId()), "repeat has a fresh stable operation identity");
        helper.assertTrue(progress.rewardDelivery() == null, "a new run receives a new reward ledger");
        progress.beginCompletion(quest.rewards());
        deliver(progress, quest, rewards);
        QuestLifecycleService.complete(quest.id(), progress, 22, quest.rules().consumeOnCompletion());
        progress = roundTrip(data).get(PLAYER, quest.id());
        helper.assertValueEqual(rewards, List.of(7, 7), "each run grants the reward exactly once");
        helper.assertValueEqual(progress.completionCount(), 2, "two completions survive native NBT reload");
        helper.assertValueEqual(progress.completionHistory().size(), 2, "history is retained between runs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pendingPartyRewardsBlockOffersUntilClaimedIncludingAfterReload(GameTestHelper helper) {
        var quest = detachedFixture("\"repeatable\":true");
        var data = new VillagerQuestSavedData();
        var progress = data.getOrCreate(PLAYER, quest.id());
        start(quest, progress, ISSUER, 0);
        QuestLifecycleService.complete(quest.id(), progress, 1, false);
        progress.markPendingPartyReward();
        helper.assertFalse(eligible(quest, progress, ISSUER, 100), "offers cannot advertise a run with unclaimed party rewards");
        helper.assertFalse(QuestStateMachine.start(progress, ISSUER, Level.OVERWORLD, null, 100).dirty(),
                "actual start agrees with offer eligibility");
        progress = roundTrip(data).get(PLAYER, quest.id());
        helper.assertTrue(progress.pendingPartyReward(), "unclaimed reward survives reconnect storage roundtrip");
        helper.assertFalse(eligible(quest, progress, ISSUER, 100), "reloading cannot bypass the reward barrier");
        progress.markPartyRewardClaimed();
        helper.assertTrue(eligible(quest, progress, ISSUER, 100), "claimed rewards release eligible repeat offers");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rewardFailureResumesSavedPlanAndPreventsAbortOrRestart(GameTestHelper helper) {
        var quest = detachedFixture("\"repeatable\":true");
        var data = new VillagerQuestSavedData();
        var progress = data.getOrCreate(PLAYER, quest.id());
        start(quest, progress, ISSUER, 0);
        var rewards = new QuestDefinition.Rewards(7, 3, 0, null, null, null);
        progress.beginCompletion(rewards);
        List<String> effects = new ArrayList<>();
        var failed = QuestRewardDeliveryService.deliver(progress, rewards, Map.of(), (action, inherited) -> {
            effects.add(action.kind().name());
            return action.kind() == VillagerActionDefinition.Kind.REPUTATION
                    ? ActionResult.unavailable("issuer unavailable", Set.of(), VillagerActionResult.EMPTY) : success();
        }, (action, result) -> { }, () -> { });
        helper.assertFalse(failed.success(), "partial delivery reports its failure");
        helper.assertValueEqual(effects, List.of("EXPERIENCE", "REPUTATION"), "delivery stops at unavailable effect");
        progress = roundTrip(data).get(PLAYER, quest.id());
        helper.assertFalse(eligible(quest, progress, ISSUER, 100), "pending completion cannot offer a new run");
        helper.assertFalse(QuestStateMachine.abandon(progress, 100, false).dirty(), "abandon cannot discard a partial claim");
        helper.assertFalse(QuestStateMachine.start(progress, ISSUER, Level.OVERWORLD, null, 100, true).dirty(),
                "even forced restart preserves the pending reward ledger");
        effects.clear();
        var restored = progress;
        var outcome = QuestRewardDeliveryService.deliver(restored, QuestDefinition.Rewards.EMPTY, Map.of(), (action, inherited) -> {
            effects.add(action.kind().name() + ":" + action.amount());
            return success();
        }, (action, result) -> { }, () -> { });
        helper.assertTrue(outcome.success(), "recovered provider resumes delivery");
        helper.assertValueEqual(effects, List.of("REPUTATION:3"), "reload resumes the saved plan without replaying XP or adopting new rewards");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneOffLimitsAndPreviouslyConsumedSavesRemainLocked(GameTestHelper helper) {
        var oneOff = detachedFixture("\"repeatable\":false,\"max_starts\":1,\"max_completions\":1,\"consume_on_completion\":true");
        var data = new VillagerQuestSavedData();
        var progress = data.getOrCreate(PLAYER, oneOff.id());
        start(oneOff, progress, ISSUER, 0);
        QuestLifecycleService.complete(oneOff.id(), progress, 1, true);
        progress = roundTrip(data).get(PLAYER, oneOff.id());
        helper.assertFalse(eligible(oneOff, progress, ISSUER, Long.MAX_VALUE), "intentional one-off completion stays locked");
        var repeatable = detachedFixture("\"repeatable\":true");
        helper.assertFalse(eligible(repeatable, progress, ISSUER, Long.MAX_VALUE),
                "corrected definitions never silently reset previously consumed saves");
        helper.assertValueEqual(progress.consumedReason(), "completion", "original consumed reason is preserved");
        helper.assertValueEqual(progress.completionCount(), 1, "no retroactive completion or reward is created");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void abandonmentPoliciesAndStartLimitsSurviveReload(GameTestHelper helper) {
        for (String policy : List.of("allow_repickup", "cooldown", "remove_forever")) {
            var quest = detachedFixture("\"repeatable\":true,\"abandonment\":\"" + policy + "\",\"abandonment_cooldown_ticks\":20");
            var data = new VillagerQuestSavedData();
            var progress = data.getOrCreate(PLAYER, quest.id());
            start(quest, progress, ISSUER, 0);
            progress.addObjectiveCounter("evidence", 1);
            UUID firstRun = progress.questRunId();
            QuestLifecycleService.abandon(quest.id(), progress, 1,
                    quest.rules().consumeOnAbandonment() || quest.rules().abandonment() == QuestDefinition.AbandonmentMode.REMOVE_FOREVER);
            progress = roundTrip(data).get(PLAYER, quest.id());
            helper.assertValueEqual(eligible(quest, progress, ISSUER, 20), policy.equals("allow_repickup"), "authored immediate repickup policy: " + policy);
            helper.assertValueEqual(eligible(quest, progress, ISSUER, 21), !policy.equals("remove_forever"), "authored delayed repickup policy: " + policy);
            if (!policy.equals("remove_forever")) {
                start(quest, progress, ISSUER, 21);
                helper.assertValueEqual(progress.objectiveCounter("evidence"), 0, "restart clears abandoned objective credit");
                helper.assertFalse(firstRun.equals(progress.questRunId()), "abandon/restart uses a new run");
                helper.assertValueEqual(progress.abandonCount(), 1, "restart preserves lifetime abandon history");
            }
        }
        var limited = detachedFixture("\"repeatable\":true,\"max_starts\":1");
        var progress = new VillagerQuestSavedData.QuestProgress();
        start(limited, progress, ISSUER, 0);
        QuestLifecycleService.abandon(limited.id(), progress, 1, false);
        helper.assertFalse(eligible(limited, progress, ISSUER, 100), "allow_repickup still respects explicit lifetime start limits");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void completionLimitsAndProviderCompatibilityStayAuthoritative(GameTestHelper helper) {
        var quest = detachedFixture("\"repeatable\":true,\"max_completions\":2,\"cross_villager_compatible\":false");
        var progress = new VillagerQuestSavedData.QuestProgress();
        start(quest, progress, ISSUER, 0);
        QuestLifecycleService.complete(quest.id(), progress, 1, false);
        helper.assertFalse(eligible(quest, progress, UUID.randomUUID(), 100), "incompatible giver cannot offer the repeat");
        helper.assertTrue(eligible(quest, progress, ISSUER, 100), "original giver can offer it below the completion limit");
        start(quest, progress, ISSUER, 100);
        QuestLifecycleService.complete(quest.id(), progress, 101, false);
        helper.assertFalse(eligible(quest, progress, ISSUER, 200), "explicit completion cap wins over repeatability");
        var scoped = detachedFixture("\"repeatable\":true,\"max_completions\":1,\"completion_scope\":\"world\"");
        helper.assertFalse(QuestStartRules.canStart(scoped, null, ISSUER, 0, () -> 1), "world completion cap applies before personal progress exists");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failureAndExpirationRetainTheirConfiguredRepickupPolicies(GameTestHelper helper) {
        for (boolean repeatable : List.of(false, true)) {
            var quest = detachedFixture("\"repeatable\":" + repeatable);
            var progress = new VillagerQuestSavedData.QuestProgress();
            start(quest, progress, ISSUER, 0);
            QuestLifecycleService.fail(quest.id(), progress, 1, "explicit_fail");
            helper.assertValueEqual(eligible(quest, progress, ISSUER, 100), repeatable, "failure follows existing repeatability policy");
            helper.assertFalse(QuestStateMachine.fail(progress, 2, "duplicate").dirty(), "repeated failure cannot mutate terminal state");
        }
        for (boolean repickup : List.of(false, true)) {
            var quest = detachedFixture("\"repeatable\":false,\"expiration\":{\"after_ticks\":20,\"allow_repickup\":" + repickup + "}");
            var data = new VillagerQuestSavedData();
            var progress = data.getOrCreate(PLAYER, quest.id());
            start(quest, progress, ISSUER, 0);
            QuestLifecycleService.expire(quest.id(), progress, 20, quest.rules().expiration().consume());
            progress = roundTrip(data).get(PLAYER, quest.id());
            helper.assertValueEqual(eligible(quest, progress, ISSUER, 100), repickup, "expiration follows explicit repickup policy after reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeRepeatedEventsAdvanceOnlyCurrentObjectivesAndPublishCompletionOnce(GameTestHelper helper) {
        var quest = fixture("\"repeatable\":true", false).asQuestDefinition();
        var progress = new VillagerQuestSavedData.QuestProgress();
        start(quest, progress, ISSUER, 0);
        var objective = quest.objectives().getFirst();
        List<String> completions = new ArrayList<>();
        helper.assertFalse(QuestObjectiveDispatcher.dispatch(progress, List.of(objective), true, QuestObjectiveEventKind.CRITERION,
                value -> false, value -> false, value -> completions.add(value.id()), null), "unrelated action grants no credit");
        for (int i = 0; i < 3; i++) QuestObjectiveDispatcher.dispatch(progress, List.of(objective), true, QuestObjectiveEventKind.CRITERION,
                value -> true, value -> false, value -> completions.add(value.id()), null);
        helper.assertValueEqual(progress.objectiveCounter(objective.id()), 2, "completed counter is stable under repeated notifications");
        helper.assertValueEqual(completions, List.of(objective.id()), "objective completion callback runs once");
        helper.assertTrue(QuestProgressionService.advanceSaved(quest, progress, 1, value -> progress.objectiveComplete(value.id()), conditions -> true),
                "completed intended actions advance the authored stage");
        helper.assertValueEqual(progress.currentStage(), "return", "stage reaches the turn-in boundary");
        helper.assertFalse(QuestProgressionService.advanceSaved(quest, progress, 2, value -> true, conditions -> true), "repeated observation cannot invent a transition");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void providerRecoveryPreservesRunCountersAndDeferredWork(GameTestHelper helper) {
        var quest = detachedFixture("\"repeatable\":true,\"cross_villager_compatible\":true");
        var data = new VillagerQuestSavedData();
        var progress = data.getOrCreate(PLAYER, quest.id());
        start(quest, progress, ISSUER, 0);
        UUID run = progress.questRunId();
        progress.addObjectiveCounter("work/evidence", 1);
        progress.deferLifecycleEvent(QuestDefinition.TriggerEvent.STARTED);
        UUID replacement = UUID.randomUUID();
        progress.rebindProvider(binding(replacement), 10, "operator_rebind");
        progress = roundTrip(data).get(PLAYER, quest.id());
        helper.assertValueEqual(progress.questRunId(), run, "supported operator rebind keeps operation identity");
        helper.assertValueEqual(progress.startedVillagerId(), replacement, "replacement issuer survives reload");
        helper.assertValueEqual(progress.objectiveCounter("work/evidence"), 1, "replacement keeps earned progress");
        helper.assertTrue(progress.hasPendingLifecycleEvents(), "deferred provider-dependent work survives reload");
        helper.assertValueEqual(progress.providerRebindHistory().size(), 1, "rebind remains auditable");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_reliability_native")
    public static void nativeBuiltInRepeatableDefinitionsPermitFutureRuns(GameTestHelper helper) {
        int repeatables = 0;
        for (var quest : VillagerQuestResources.quests(helper.getLevel().getServer())) {
            if (!quest.rules().repeatable()) continue;
            repeatables++;
            var progress = new VillagerQuestSavedData.QuestProgress();
            start(quest, progress, ISSUER, 0);
            QuestLifecycleService.complete(quest.id(), progress, 1, quest.rules().consumeOnCompletion());
            helper.assertTrue(eligible(quest, progress, ISSUER, 1 + quest.rules().completionCooldownTicks()),
                    "built-in repeatable must allow its next run: " + quest.id());
        }
        helper.assertValueEqual(repeatables, 33, "the existing repeatable catalog is preserved");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_reliability_native")
    public static void nativeTurnInConsumesItemsAndAwardsOncePerRun(GameTestHelper helper) {
        var compiled = fixture("\"repeatable\":true,\"cross_villager_compatible\":true", true);
        var player = helper.makeMockServerPlayerInLevel();
        var issuer = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        issuer.setNoAi(true);
        var level = helper.getLevel();
        var data = VillagerQuestSavedData.get(level);
        VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(compiled));
        VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        try {
            for (int run = 1; run <= 2; run++) {
                var context = VillagerInteractionService.createDialogueContext(level, player, issuer);
                var started = VillagerQuestService.performAction(context, compiled.id(), VillagerActionDefinition.QuestAction.START).orElseThrow();
                helper.assertValueEqual(started.status(), "started", "normal action starts run " + run);
                var missing = VillagerQuestService.performAction(context, compiled.id(), VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow();
                helper.assertFalse(missing.status().equals("completed"), "turn-in requires actual supplies");
                player.getInventory().setItem(0, new ItemStack(Items.BREAD, 2));
                player.getInventory().setChanged();
                var completed = VillagerQuestService.performAction(context, compiled.id(), VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow();
                helper.assertValueEqual(completed.status(), "completed", "normal turn-in completes run " + run);
                helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "each run hands supplies to the giver");
                helper.assertValueEqual(player.totalExperience, run * 7, "each run grants exactly seven experience");
                VillagerQuestService.performAction(context, compiled.id(), VillagerActionDefinition.QuestAction.TURN_IN);
                helper.assertValueEqual(player.totalExperience, run * 7, "duplicate turn-in cannot replay rewards");
                helper.assertValueEqual(data.get(player.getUUID(), compiled.id()).completionCount(), run, "duplicate turn-in cannot increment completions");
            }
        } finally {
            data.remove(player.getUUID(), compiled.id());
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            VillagerQuestService.clearRuntimeState(player);
            issuer.discard();
            VillagerQuestResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_reliability_native")
    public static void nativeMissingGiverCanBeAbandonedAndRestartedWithoutRewards(GameTestHelper helper) {
        var compiled = fixture("\"repeatable\":true,\"cross_villager_compatible\":true", true);
        var player = helper.makeMockServerPlayerInLevel();
        var issuer = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        var replacement = helper.spawn(EntityType.VILLAGER, 3, 1, 2);
        issuer.setNoAi(true);
        replacement.setNoAi(true);
        var level = helper.getLevel();
        var data = VillagerQuestSavedData.get(level);
        VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(compiled));
        VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        try {
            var context = VillagerInteractionService.createDialogueContext(level, player, issuer);
            helper.assertValueEqual(VillagerQuestService.performAction(context, compiled.id(), VillagerActionDefinition.QuestAction.START)
                    .orElseThrow().status(), "started", "normal acceptance starts with the original giver");
            var progress = data.get(player.getUUID(), compiled.id());
            UUID firstRun = progress.questRunId();
            helper.assertTrue(VillagerQuestService.pendingPartyRewardProvider(player, progress) == issuer,
                    "reward lookup resolves the actual living issuer");
            issuer.discard();
            helper.assertTrue(VillagerQuestService.pendingPartyRewardProvider(player, progress) == null,
                    "nearby giver never silently claims the missing issuer's pending rewards");
            helper.assertTrue(VillagerQuestService.abandonQuestFromJournal(player, compiled.id()),
                    "journal abandonment works without a loaded giver");
            context = VillagerInteractionService.createDialogueContext(level, player, replacement);
            helper.assertValueEqual(VillagerQuestService.performAction(context, compiled.id(), VillagerActionDefinition.QuestAction.START)
                    .orElseThrow().status(), "started", "configured compatible replacement offers the abandoned quest");
            helper.assertFalse(firstRun.equals(progress.questRunId()), "replacement starts a fresh run");
            helper.assertValueEqual(progress.startedVillagerId(), replacement.getUUID(), "new run binds the selected giver");
            helper.assertValueEqual(player.totalExperience, 0, "abandon and restart grant no completion reward");
        } finally {
            data.remove(player.getUUID(), compiled.id());
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            VillagerQuestService.clearRuntimeState(player);
            issuer.discard();
            replacement.discard();
            VillagerQuestResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_reliability_native")
    public static void nativePartyKillCreditRejectsOutsidersAndDuplicateDeaths(GameTestHelper helper) {
        var compiled = fixture("\"repeatable\":true", "{\"id\":\"evidence\",\"type\":\"mob_kill\",\"entity\":\"minecraft:zombie\",\"count\":2}");
        var player = helper.makeMockServerPlayerInLevel();
        var outsider = helper.makeMockServerPlayerInLevel();
        var issuer = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        var first = helper.spawn(EntityType.ZOMBIE, 3, 1, 2);
        var second = helper.spawn(EntityType.ZOMBIE, 4, 1, 2);
        issuer.setNoAi(true);
        first.setNoAi(true);
        second.setNoAi(true);
        var level = helper.getLevel();
        var data = VillagerQuestSavedData.get(level);
        VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(compiled));
        VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        try {
            helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.createParty(player).success(), "leader creates a party");
            var context = VillagerInteractionService.createDialogueContext(level, player, issuer);
            helper.assertValueEqual(VillagerQuestService.performAction(context, compiled.id(), VillagerActionDefinition.QuestAction.START)
                    .orElseThrow().status(), "started", "normal acceptance enrolls the shared quest");
            var progress = data.get(player.getUUID(), compiled.id());
            var objective = compiled.asQuestDefinition().objectives().getFirst();
            var shared = PartyQuestService.sharedForPlayer(level, player.getUUID(), compiled.id()).orElseThrow();
            VillagerQuestService.onEntityKilled(first, outsider);
            helper.assertValueEqual(shared.objectiveCounter(objective.id()), 0, "outsider action grants no party credit");
            VillagerQuestService.onEntityKilled(first, player);
            VillagerQuestService.onEntityKilled(first, player);
            helper.assertValueEqual(shared.objectiveCounter(objective.id()), 1, "one death notification grants credit once across the shared instance");
            helper.assertValueEqual(progress.objectiveCounter(objective.id()), 1, "personal projection is not credited twice");
            VillagerQuestService.onEntityKilled(second, player);
            helper.assertValueEqual(progress.objectiveCounter(objective.id()), 2, "a distinct participating kill earns the next credit");
            helper.assertTrue(data.get(outsider.getUUID(), compiled.id()) == null, "participation does not auto-enroll outsiders");
        } finally {
            data.remove(player.getUUID(), compiled.id());
            com.jvn.villagerretaliation.party.PartyVillagerContractService.disband(player);
            VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
            VillagerQuestService.clearRuntimeState(player);
            issuer.discard();
            first.discard();
            second.discard();
            VillagerQuestResources.clearCache();
        }
        helper.succeed();
    }

    private static VillagerQuestSavedData roundTrip(VillagerQuestSavedData data) {
        return VillagerQuestSavedData.load(data.save(new CompoundTag(), null), null);
    }

    private static boolean eligible(QuestDefinition quest, VillagerQuestSavedData.QuestProgress progress, UUID provider, long time) {
        return QuestStartRules.canStart(quest, progress, provider, time, () -> 0);
    }

    private static void start(QuestDefinition quest, VillagerQuestSavedData.QuestProgress progress, UUID provider, long time) {
        QuestLifecycleService.start(quest.id(), progress, binding(provider), null, time, PLAYER, null);
        QuestLifecycleService.initializeStage(quest, progress, time);
    }

    private static QuestProviderBinding binding(UUID provider) {
        return new QuestProviderBinding(VillagerRetaliation.id("villager"), provider, "Keeper", null, 1,
                Level.OVERWORLD, BlockPos.ZERO, "", Map.of(), true);
    }

    private static ActionResult success() { return ActionResult.success(VillagerActionResult.success(), Set.of()); }

    private static void deliver(VillagerQuestSavedData.QuestProgress progress, QuestDefinition quest, List<Integer> rewards) {
        var result = QuestRewardDeliveryService.deliver(progress, quest.rewards(), Map.of(), (action, inherited) -> {
            rewards.add(action.amount());
            return success();
        }, (action, outcome) -> { }, () -> { });
        if (!result.success()) throw new AssertionError(result.message());
    }

    private static QuestDefinition detachedFixture(String availability) {
        var id = VillagerRetaliation.id("quest_reliability_fixture");
        var raw = JsonParser.parseString("{\"max_starts\":0,\"max_completions\":0," + availability + "}").getAsJsonObject();
        long cooldown = raw.has("completion_cooldown_ticks") ? raw.get("completion_cooldown_ticks").getAsLong() : 0;
        var rules = VillagerQuestResources.readRuleFields(id, raw, id, cooldown, QuestDefinition.Branching.DEFAULT);
        return new QuestDefinition(id, "Reliability fixture", "", "", "", "", Set.of(), null, List.of(), false,
                null, null, List.of(), rules, null, "", Map.of(), List.of(),
                new QuestDefinition.Rewards(7, 0, 0, null, null, null), null, null, null);
    }

    private static CompiledQuest fixture(String availability, boolean items) {
        String objective = items ? "{\"id\":\"evidence\",\"type\":\"item_check\",\"item\":\"minecraft:bread\",\"count\":2,\"consume\":true}"
                : "{\"id\":\"evidence\",\"type\":\"criterion\",\"criterion\":\"villagerretaliation:reliability\",\"count\":2}";
        return fixture(availability, objective);
    }

    private static CompiledQuest fixture(String availability, String objective) {
        var raw = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:quest_reliability_fixture",
                 "metadata":{"title":"Reliability fixture"},"provider":{"type":"villagerretaliation:villager"},
                 "availability":{"max_starts":0,"max_completions":0,%s},"entry_stage":"work",
                 "rewards":{"experience":7},"stages":[
                  {"id":"work","objectives":[%s],"complete_when":["evidence"],"next":"return"},
                  {"id":"return","objectives":[]}]}
                """.formatted(availability, objective)).getAsJsonObject();
        var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/quest_reliability_fixture.json"), raw).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }
}
