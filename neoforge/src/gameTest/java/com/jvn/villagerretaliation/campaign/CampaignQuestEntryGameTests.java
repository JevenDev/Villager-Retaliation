package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.QuestContentLoadReport;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignQuestEntryGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("test:entry_route");
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:entry_quest");
    private static final ResourceLocation ALTERNATE = ResourceLocation.parse("test:entry_alternate");
    private static final ResourceLocation FOLLOW_UP = ResourceLocation.parse("test:entry_follow_up");
    private static final ResourceLocation COOLDOWN = ResourceLocation.parse("test:entry_cooldown");

    private CampaignQuestEntryGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void latePlayerEntersTheAlternateStageWithoutOriginalEffectsOrRewards(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.resolve();
            int experience = fixture.player.totalExperience;
            var started = VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true);
            helper.assertTrue(started.started(), "late player's catch-up starts: " + started.message());
            var progress = fixture.progress(QUEST);
            helper.assertValueEqual(progress.currentStage(), "catch_up", "catch-up is selected before stage initialization");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 5, "only authored catch-up entry effects apply");
            helper.assertValueEqual(progress.campaignEntryStage(), "catch_up", "alternate entry is retained for later completion");
            var restored = VillagerQuestSavedData.load(VillagerQuestSavedData.get(helper.getLevel())
                    .save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(restored.get(fixture.player.getUUID(), QUEST).campaignAlternateEntry(), "reload retains the reward boundary");
            progress.markObjectiveComplete("catch_up.learn");
            var result = VillagerQuestService.performAction(VillagerInteractionService.createDialogueContext(
                    helper.getLevel(), fixture.player, fixture.villager), QUEST, VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow();
            helper.assertValueEqual(result.status(), "completed", "authored catch-up objectives allow personal completion");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 5, "original quest rewards and completed triggers are not replayed");
            helper.assertTrue(fixture.data.instances().get(CAMPAIGN).players().get(fixture.player.getUUID()).participation().isEmpty(),
                    "catch-up does not invent participation");
            var personal = fixture.data.instances().get(CAMPAIGN).players().get(fixture.player.getUUID());
            helper.assertTrue(personal.milestones().get("dragon").completed() && personal.completedQuests().contains(QUEST),
                    "quest completion advances the personal campaign milestone");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void separateCatchUpQuestKeepsItsOwnAuthoredRewards(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, ALTERNATE)) {
            fixture.resolve();
            int experience = fixture.player.totalExperience;
            var started = VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, ALTERNATE, true);
            helper.assertTrue(started.started(), "separate catch-up quest starts: " + started.message());
            var progress = fixture.progress(ALTERNATE);
            helper.assertValueEqual(progress.currentStage(), "catch_up", "alternate quest uses its authored catch-up entry");
            helper.assertFalse(progress.campaignAlternateEntry(), "alternate quest owns its personal effects");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 205, "alternate quest gets its own entry and started effects");
            progress.markObjectiveComplete("catch_up.learn");
            var result = VillagerQuestService.performAction(VillagerInteractionService.createDialogueContext(
                    helper.getLevel(), fixture.player, fixture.villager), ALTERNATE, VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow();
            helper.assertValueEqual(result.status(), "completed", "separate catch-up quest completes");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 1005, "alternate quest keeps its own personal completion rewards");
            helper.assertTrue(fixture.progress(QUEST) == null, "original quest effects and rewards remain untouched");
            helper.assertTrue(fixture.data.instances().get(CAMPAIGN).players().get(fixture.player.getUUID()).completedQuests().contains(QUEST),
                    "alternate quest completion satisfies the original personal chapter requirement");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void unresolvedMilestoneStartsNormallyWithoutCreditingParticipation(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            int experience = fixture.player.totalExperience;
            var started = VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true);
            helper.assertTrue(started.started(), "unresolved original quest starts: " + started.message());
            helper.assertValueEqual(fixture.progress(QUEST).currentStage(), "started", "unresolved milestone retains its original entry");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 300, "normal quest entry and started actions still execute");
            var player = fixture.data.instances().get(CAMPAIGN).players().get(fixture.player.getUUID());
            helper.assertTrue(player.participation().isEmpty(), "accepting a quest cannot prove participation in a future event");
            helper.assertValueEqual(player.milestones().get("dragon").route(), CampaignInstance.Route.ORIGINAL, "personal milestone entry is saved");
            fixture.progress(QUEST).markObjectiveComplete("started.decide");
            var premature = VillagerQuestService.performAction(VillagerInteractionService.createDialogueContext(
                    helper.getLevel(), fixture.player, fixture.villager), QUEST, VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow();
            helper.assertValueEqual(premature.status(), "unavailable", "unresolved shared milestones cannot be bypassed by turning in the quest");
            helper.assertValueEqual(fixture.progress(QUEST).completionCount(), 0, "blocked completion does not consume the personal quest");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 300, "blocked completion gives no reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void resolvedOriginalQuestRedirectsToAnAuthoredAlternateQuestBeforeEffects(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, ALTERNATE)) {
            fixture.resolve();
            int experience = fixture.player.totalExperience;
            var started = VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true);
            helper.assertFalse(started.started(), "resolved original entry cannot start its effects");
            helper.assertTrue(started.message().contains(ALTERNATE.toString()), "response names the authored alternate quest");
            helper.assertTrue(fixture.progress(QUEST) == null, "redirect leaves original quest progress untouched");
            helper.assertValueEqual(fixture.player.totalExperience, experience, "redirect gives no original entry reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void activePlayerChangesPathsBeforeOldEffectsCanContinue(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            int before = fixture.player.totalExperience;
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "original quest starts");
            var progress = fixture.progress(QUEST);
            UUID run = progress.questRunId();
            progress.markObjectiveComplete("started.decide");
            progress.addObjectiveCounter("started.decide", 12);
            progress.requestStageEntry("started");
            progress.requestStageTransition("catch_up");
            progress.deferLifecycleEvent(com.jvn.villagerretaliation.quest.QuestDefinition.TriggerEvent.STARTED);
            fixture.resolve();
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), fixture.player, fixture.villager);
            var blocked = VillagerQuestService.performAction(context, QUEST, VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow();
            helper.assertValueEqual(blocked.status(), "unavailable", "the old choice is rejected before reconciliation runs");
            helper.assertValueEqual(fixture.player.totalExperience - before, 300, "old pending entry cannot replay");
            var quest = fixture.definition(QUEST);
            helper.assertTrue(CampaignQuestReconciliation.reconcile(fixture.player, quest, progress), "active quest adopts catch-up");
            helper.assertValueEqual(progress.currentStage(), "catch_up", "authored catch-up stage is selected");
            helper.assertValueEqual(progress.questRunId(), run, "personal quest identity survives the handoff");
            helper.assertFalse(progress.objectiveComplete("started.decide"), "old objective completion cannot substitute for catch-up");
            helper.assertValueEqual(progress.objectiveCounter("started.decide"), 0, "old counters cannot substitute for catch-up");
            helper.assertTrue(progress.pendingLifecycleEvents().isEmpty() && progress.pendingStageTransition().isBlank(), "old pending work is discarded");
            helper.assertFalse(CampaignQuestReconciliation.reconcile(fixture.player, quest, progress), "outcome receipt prevents another handoff");
            VillagerQuestService.performAction(context, QUEST, VillagerActionDefinition.QuestAction.REMIND);
            helper.assertValueEqual(fixture.player.totalExperience - before, 305, "only alternate entry effects run");
            VillagerQuestService.performAction(context, QUEST, VillagerActionDefinition.QuestAction.REMIND);
            helper.assertValueEqual(fixture.player.totalExperience - before, 305, "refresh cannot replay the alternate entry");
            var restored = VillagerQuestSavedData.load(VillagerQuestSavedData.get(helper.getLevel())
                    .save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(CampaignQuestReconciliation.pending(helper.getLevel(), fixture.player.getUUID(), quest,
                    restored.get(fixture.player.getUUID(), QUEST)) == null, "reload retains the applied outcome receipt");
            progress.markObjectiveComplete("catch_up.learn");
            helper.assertValueEqual(VillagerQuestService.performAction(context, QUEST, VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow().status(),
                    "completed", "catch-up completes the personal quest");
            helper.assertValueEqual(fixture.player.totalExperience - before, 305, "original completion effects are not awarded");
            helper.assertTrue(fixture.data.instances().get(CAMPAIGN).players().get(fixture.player.getUUID()).participation().isEmpty(),
                    "the absent player receives no participation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void activeOriginalWaitsForItsSeparateCatchUpQuest(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, ALTERNATE)) {
            int before = fixture.player.totalExperience;
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "original quest starts");
            fixture.resolve();
            var original = fixture.progress(QUEST);
            helper.assertTrue(CampaignQuestReconciliation.reconcile(fixture.player, fixture.definition(QUEST), original), "original adopts the alternate quest");
            helper.assertValueEqual(original.campaignWaitingQuest(), ALTERNATE.toString(), "waiting state names the authored quest");
            helper.assertValueEqual(original.state(), VillagerQuestSavedData.QuestState.ACTIVE, "waiting does not falsely fail or complete the quest");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), fixture.player, fixture.villager);
            helper.assertValueEqual(VillagerQuestService.performAction(context, QUEST, VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow().status(),
                    "unavailable", "original cannot turn in while catch-up remains unfinished");
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, ALTERNATE, true).started(), "alternate can be accepted at its provider");
            fixture.progress(ALTERNATE).markObjectiveComplete("catch_up.learn");
            helper.assertValueEqual(VillagerQuestService.performAction(context, ALTERNATE, VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow().status(),
                    "completed", "alternate completes");
            helper.assertTrue(CampaignQuestReconciliation.reconcile(fixture.player, fixture.definition(QUEST), original), "personal equivalence completes the waiting original");
            helper.assertValueEqual(original.completionCount(), 1, "original completes personally once");
            helper.assertFalse(CampaignQuestReconciliation.reconcile(fixture.player, fixture.definition(QUEST), original), "repeat reconciliation is inert");
            helper.assertValueEqual(fixture.player.totalExperience - before, 1305, "only original acceptance and alternate personal rewards apply");
            helper.assertTrue(fixture.data.instances().get(CAMPAIGN).players().get(fixture.player.getUUID()).participation().isEmpty(),
                    "personal equivalence cannot award participation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void participantsKeepTheirOriginalPath(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "original quest starts");
            fixture.resolve(Set.of(fixture.player.getUUID()));
            var progress = fixture.progress(QUEST);
            helper.assertFalse(CampaignQuestReconciliation.reconcile(fixture.player, fixture.definition(QUEST), progress), "participants are not redirected");
            helper.assertValueEqual(progress.currentStage(), "started", "participant retains original objective");
            helper.assertFalse(progress.campaignAlternateEntry(), "participant retains original reward rules");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void supersededScenesCannotRunFailureBranchesOrResumeOldActions(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "original quest starts");
            var progress = fixture.progress(QUEST);
            var scene = com.jvn.villagerretaliation.scene.compiler.SceneCompiler.compile(
                    com.jvn.villagerretaliation.scene.compiler.SceneParser.parse(CAMPAIGN, JsonParser.parseString("""
                            {"schema":"villagerretaliation:scene/v1","id":"test:catch_up_scene","definition_version":1,
                             "ownership":"player","entry_step":"finish","cancellation_policy":"run_failure_step",
                             "steps":[{"id":"finish","type":"villagerretaliation:scene_complete","failure_step":"recover"},
                                      {"id":"recover","type":"villagerretaliation:scene_complete"}]}
                            """).getAsJsonObject()).resource()).scene();
            helper.assertTrue(scene != null, "scene fixture compiles");
            var scenes = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel());
            var owner = new com.jvn.villagerretaliation.scene.runtime.SceneOwner(
                    com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER, fixture.player.getUUID(), null, null, "");
            var running = scenes.start(scene, "catch-up-fixture", owner, progress.questRunId(), Set.of(fixture.player.getUUID()), Map.of(),
                    helper.getLevel().getGameTime(), QUEST).instance();
            running.bindCampaign(CAMPAIGN);
            var otherRun = scenes.start(scene, "other-run", owner, UUID.randomUUID(), Set.of(fixture.player.getUUID()), Map.of(),
                    helper.getLevel().getGameTime(), QUEST).instance();
            var continuation = scenes.suspendContinuation(running, fixture.player.getUUID(), fixture.villager.getUUID(),
                    "old-entry", List.of(), 0, Map.of(), QUEST, progress.questRunId());
            fixture.resolve();
            com.jvn.villagerretaliation.scene.runtime.SceneStepEngine.process(helper.getLevel().getServer(), scenes, running, scene,
                    helper.getLevel().getGameTime());
            helper.assertTrue(running.campaignPaused(), "scene cannot advance between world commit and reconciliation");
            helper.assertTrue(CampaignQuestReconciliation.reconcile(fixture.player, fixture.definition(QUEST), progress), "scene handoff succeeds");
            helper.assertValueEqual(running.state(), com.jvn.villagerretaliation.scene.runtime.SceneState.CANCELLED, "superseded scene terminates");
            helper.assertValueEqual(running.currentStep(), "finish", "cancellation does not enter its authored failure branch");
            com.jvn.villagerretaliation.scene.runtime.SceneStepEngine.process(helper.getLevel().getServer(), scenes, running, scene,
                    helper.getLevel().getGameTime());
            helper.assertValueEqual(running.state(), com.jvn.villagerretaliation.scene.runtime.SceneState.CANCELLED, "stale scene work cannot revive a superseded path");
            helper.assertFalse(otherRun.state().terminal(), "another run is unaffected");
            helper.assertTrue(continuation.completionReceipt(), "old continuation is settled");
            helper.assertValueEqual(continuation.actionStatus(), com.jvn.villagerretaliation.action.ActionStatus.UNAVAILABLE, "old actions cannot resume");
            var restored = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.load(scenes.save(new CompoundTag(), helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess());
            helper.assertTrue(restored.get(running.id()).orElseThrow().state().terminal()
                    && restored.continuation(continuation.id()).orElseThrow().completionReceipt(), "reload retains scene and continuation cancellation");
            com.jvn.villagerretaliation.scene.runtime.SceneTransitionService.supersede(scenes, otherRun, helper.getLevel().getGameTime());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void campaignQuestWritesRemainBlockedUntilStorageRecovers(GameTestHelper helper) throws IOException {
        Path directory = Files.createTempDirectory("campaign-quest-write-");
        Path blocked = directory.resolve("blocked");
        Files.createFile(blocked);
        var data = new VillagerQuestSavedData();
        UUID player = UUID.randomUUID();
        var progress = data.getOrCreate(player, QUEST);
        progress.bindCampaign(CAMPAIGN);
        UUID operation = UUID.randomUUID();
        progress.enterCampaignCatchUp("catch_up", null, operation);
        data.requireCampaignPersistence();
        try {
            try {
                data.persistCampaignProgress(blocked, helper.getLevel().registryAccess());
                throw new AssertionError("a file cannot be used as the save directory");
            } catch (IOException expected) {
                helper.assertTrue(data.campaignPersistencePending(), "failed durable write remains pending");
            }
            data.persistCampaignProgress(directory, helper.getLevel().registryAccess());
            helper.assertFalse(data.campaignPersistencePending(), "successful retry releases the execution gate");
            var saved = net.minecraft.nbt.NbtIo.readCompressed(directory.resolve("villagerretaliation_quests.dat"),
                    net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var restored = VillagerQuestSavedData.load(saved.getCompound("data"), helper.getLevel().registryAccess());
            helper.assertTrue(restored.get(player, QUEST).appliedCampaignCatchUp(operation), "receipt reaches durable storage");
            helper.assertValueEqual(restored.get(player, QUEST).pendingStageEntry(), "catch_up", "entry resumes after reload");
        } finally {
            Files.deleteIfExists(blocked);
            Files.deleteIfExists(directory.resolve("villagerretaliation_quests.dat"));
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void aReloadedActiveQuestCatchesUpOnTheNextProgressTick(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            int before = fixture.player.totalExperience;
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "original quest starts");
            var saved = VillagerQuestSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            fixture.resolve();
            var restored = VillagerQuestSavedData.load(saved, helper.getLevel().registryAccess());
            fixture.server.overworld().getDataStorage().set("villagerretaliation_quests", restored);
            fixture.player.tickCount = 0;
            VillagerQuestService.onPlayerTick(fixture.player);
            helper.assertValueEqual(fixture.progress(QUEST).currentStage(), "catch_up", "restored active progress reconciles after an absent player's milestone resolves");
            helper.assertValueEqual(fixture.player.totalExperience - before, 305, "the next progress tick runs only the catch-up entry");
            VillagerQuestService.onPlayerTick(fixture.player);
            helper.assertValueEqual(fixture.player.totalExperience - before, 305, "later ticks cannot repeat the entry reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void existingCatchUpEntriesMigrateWithoutReplayingActions(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.resolve();
            int before = fixture.player.totalExperience;
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "catch-up starts");
            fixture.progress(QUEST).addObjectiveCounter("catch_up.learn", 2);
            var saved = VillagerQuestSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            saved.putInt("DataVersion", 8);
            for (var raw : saved.getList("Entries", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                ((CompoundTag) raw).remove("CampaignCatchUpOperations");
            }
            var restored = VillagerQuestSavedData.load(saved, helper.getLevel().registryAccess());
            fixture.server.overworld().getDataStorage().set("villagerretaliation_quests", restored);
            helper.assertTrue(fixture.progress(QUEST).campaignLegacyEntry(), "version eight marks its existing catch-up entry for receipt migration");
            helper.assertTrue(CampaignQuestReconciliation.reconcile(fixture.player, fixture.definition(QUEST), fixture.progress(QUEST)), "migration saves the existing route receipt");
            helper.assertValueEqual(fixture.progress(QUEST).objectiveCounter("catch_up.learn"), 2, "migration preserves catch-up progress");
            helper.assertTrue(fixture.progress(QUEST).pendingStageEntry().isBlank(), "migration does not request another entry execution");
            helper.assertFalse(fixture.progress(QUEST).campaignLegacyEntry(), "migration is acknowledged once");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), fixture.player, fixture.villager);
            VillagerQuestService.performAction(context, QUEST, VillagerActionDefinition.QuestAction.REMIND);
            helper.assertValueEqual(fixture.player.totalExperience - before, 5, "existing catch-up entry reward cannot replay during migration");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void laterMilestonesEnterWhenTheirStageIsReached(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "decision")) {
            int before = fixture.player.totalExperience;
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "original quest starts");
            helper.assertFalse(fixture.data.instances().get(CAMPAIGN).players().containsKey(fixture.player.getUUID()), "quest acceptance does not enter a later milestone");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), fixture.player, fixture.villager);
            helper.assertTrue(VillagerQuestService.syncQuestStage(context, QUEST, "decision"), "later stage can be reached");
            var personal = fixture.data.instances().get(CAMPAIGN).players().get(fixture.player.getUUID());
            helper.assertValueEqual(personal.milestones().get("dragon").route(), CampaignInstance.Route.ORIGINAL, "reaching the stage saves personal milestone entry");
            helper.assertValueEqual(fixture.progress(QUEST).currentStage(), "decision", "unresolved milestone uses its original stage");
            helper.assertValueEqual(fixture.player.totalExperience - before, 410, "normal exit and milestone entry effects keep their order");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void aResolvedLaterMilestoneRedirectsBeforeOriginalTransitionEffects(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "decision")) {
            int before = fixture.player.totalExperience;
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "original quest starts");
            fixture.resolve();
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), fixture.player, fixture.villager);
            helper.assertTrue(VillagerQuestService.syncQuestStage(context, QUEST, "decision"), "resolved milestone adopts catch-up");
            helper.assertValueEqual(fixture.progress(QUEST).currentStage(), "catch_up", "authored alternate replaces the requested original milestone");
            helper.assertValueEqual(fixture.player.totalExperience - before, 305, "original exit and decision effects cannot run after resolution");
            helper.assertFalse(VillagerQuestService.syncQuestStage(context, QUEST, "decision"), "a stale request cannot return to the superseded milestone");
            helper.assertValueEqual(fixture.player.totalExperience - before, 305, "duplicate transition cannot replay original effects");
            helper.assertTrue(fixture.data.instances().get(CAMPAIGN).players().get(fixture.player.getUUID()).participation().isEmpty(), "stage catch-up cannot invent participation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void anEarlyWorldOutcomeDoesNotSkipTheIntroductionToALaterMilestone(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "decision")) {
            fixture.resolve();
            int before = fixture.player.totalExperience;
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "introduction remains playable after the world outcome");
            helper.assertValueEqual(fixture.progress(QUEST).currentStage(), "started", "catch-up waits for the authored milestone boundary");
            helper.assertValueEqual(fixture.player.totalExperience - before, 300, "introduction effects remain available");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), fixture.player, fixture.villager);
            helper.assertTrue(VillagerQuestService.syncQuestStage(context, QUEST, "decision"), "later boundary selects catch-up");
            helper.assertValueEqual(fixture.player.totalExperience - before, 305, "only catch-up effects run at the resolved milestone");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void separateCatchUpSatisfiesQuestConditionsAndLaterQuestPrerequisites(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, ALTERNATE)) {
            fixture.resolve();
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, ALTERNATE, true).started(), "catch-up starts");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), fixture.player, fixture.villager);
            helper.assertFalse(VillagerQuestService.matchesState(context, FOLLOW_UP, Set.of("available")), "later chapter is blocked before personal catch-up");
            fixture.progress(ALTERNATE).markObjectiveComplete("catch_up.learn");
            helper.assertValueEqual(VillagerQuestService.performAction(context, ALTERNATE, VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow().status(),
                    "completed", "personal catch-up completes");
            helper.assertTrue(fixture.progress(QUEST) == null, "equivalence does not create an original quest run");
            helper.assertTrue(VillagerQuestService.matchesState(context, QUEST, Set.of("completed")), "authored quest conditions recognize personal equivalence");
            helper.assertFalse(VillagerQuestService.matchesState(context, QUEST, Set.of("not_started")), "completed equivalent quest cannot appear unstarted");
            helper.assertTrue(VillagerQuestService.matchesState(context, FOLLOW_UP, Set.of("available")), "later quest prerequisite and chapter unlock together");
            helper.assertFalse(VillagerQuestService.matchesState(context, COOLDOWN, Set.of("available")), "personal catch-up does not skip an authored prerequisite cooldown");
            helper.assertValueEqual(CampaignQuestAccess.personalCompletion(helper.getLevel(), fixture.player.getUUID(), fixture.definition(QUEST)).gameTime(),
                    fixture.progress(ALTERNATE).completedGameTime(), "prerequisite cooldown uses personal catch-up time");
        }
        helper.succeed();
    }


    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void acceptingSharedConsequencesStartsOnceAndSurvivesReload(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var context = fixture.context();
            var blocked = VillagerQuestService.performAction(context, QUEST, VillagerActionDefinition.QuestAction.START).orElseThrow();
            helper.assertValueEqual(blocked.status(), "unavailable", "unconfirmed direct starts are blocked");
            helper.assertTrue(fixture.progress(QUEST) == null, "blocked acceptance has no quest effects");
            int experience = fixture.player.totalExperience;
            var request = fixture.confirmation();
            helper.assertTrue(request.text().contains("offline") && request.text().contains("defeated"), "impact includes shared scope and named outcome");
            helper.assertTrue(fixture.progress(QUEST) == null, "prompt does not start the quest");
            var answer = new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(request.id(), request.revision(), true);
            CampaignQuestAcceptanceService.handle(fixture.player, answer);
            var progress = fixture.progress(QUEST);
            helper.assertTrue(progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE, "confirmed quest starts");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 300, "entry and start effects apply once");
            helper.assertTrue(progress.campaignAcceptance() != null && progress.campaignAcceptance().requestId().equals(request.id()),
                    "accepted request is saved with the quest run");
            helper.assertValueEqual(progress.campaignAcceptance().fixedOutcomes(), Map.of("dragon", "defeated"), "fixed consequences are retained");
            CampaignQuestAcceptanceService.handle(fixture.player, answer);
            helper.assertValueEqual(fixture.player.totalExperience - experience, 300, "duplicate confirmation cannot repeat effects");
            var restored = VillagerQuestSavedData.load(VillagerQuestSavedData.get(helper.getLevel()).save(new CompoundTag(),
                    helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertValueEqual(restored.get(fixture.player.getUUID(), QUEST).campaignAcceptance(), progress.campaignAcceptance(),
                    "reload retains acceptance without needing the transient prompt");
            var disk = net.minecraft.nbt.NbtIo.readCompressed(fixture.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data/villagerretaliation_quests.dat"), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var durable = VillagerQuestSavedData.load(disk.getCompound("data"), helper.getLevel().registryAccess()).get(fixture.player.getUUID(), QUEST);
            helper.assertTrue(durable != null && durable.campaignAcceptance() != null && !durable.pendingStageEntry().isBlank(),
                    "acceptance and pending entry are durable before effects run");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void decliningSharedConsequencesLeavesQuestUntouched(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            int experience = fixture.player.totalExperience;
            var request = fixture.confirmation();
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    request.id(), request.revision(), false));
            helper.assertTrue(fixture.progress(QUEST) == null, "decline does not start a quest");
            helper.assertValueEqual(fixture.player.totalExperience, experience, "decline has no effects");
            helper.assertTrue(CampaignQuestAcceptanceService.pending(fixture.player) == null, "decline consumes the prompt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void outcomeCommittedDuringConfirmationInvalidatesTheOffer(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var request = fixture.confirmation();
            fixture.resolve();
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    request.id(), request.revision(), true));
            helper.assertTrue(fixture.progress(QUEST) == null, "stale confirmation cannot replay the original path");
            helper.assertFalse(CampaignQuestAcceptanceService.requiresConfirmation(fixture.context(), fixture.definition(QUEST)),
                    "already resolved catch-up has no shared consequence to confirm");
            var result = VillagerQuestService.performAction(fixture.context(), QUEST, VillagerActionDefinition.QuestAction.START).orElseThrow();
            helper.assertValueEqual(result.status(), "started", "fresh catch-up offer can proceed");
            helper.assertValueEqual(fixture.progress(QUEST).currentStage(), "catch_up", "fresh offer follows committed outcome");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void oldConfirmationCannotConsumeNewerRequest(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var old = fixture.confirmation();
            var current = fixture.confirmation();
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    old.id(), old.revision(), true));
            helper.assertValueEqual(CampaignQuestAcceptanceService.pending(fixture.player).id(), current.id(), "old request cannot replace current prompt");
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    current.id(), current.revision() + 1, true));
            helper.assertTrue(fixture.progress(QUEST) == null, "forged revision cannot start quest");
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    current.id(), current.revision(), true));
            helper.assertTrue(fixture.progress(QUEST) != null, "current request remains usable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void replacementConversationCannotUseOldConfirmation(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var request = fixture.confirmation();
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(fixture.player, false);
            com.jvn.villagerretaliation.interaction.VillagerConversationService.startForcedIgnoringDisposition(fixture.player, fixture.villager);
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    request.id(), request.revision(), true));
            helper.assertTrue(fixture.progress(QUEST) == null, "same character in a new conversation cannot reuse an old confirmation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void changedQuestProgressInvalidatesConfirmation(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var request = fixture.confirmation();
            var progress = VillagerQuestSavedData.get(helper.getLevel()).getOrCreate(fixture.player.getUUID(), QUEST);
            progress.bindCampaign(CAMPAIGN);
            progress.setCurrentStage("started");
            progress.setCurrentStage("catch_up");
            progress.setCurrentStage("started");
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    request.id(), request.revision(), true));
            helper.assertTrue(progress.state() != VillagerQuestSavedData.QuestState.ACTIVE, "changed progress cannot use earlier approval");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void unavailableSelectionCannotUseConfirmation(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var selected = new java.util.concurrent.atomic.AtomicBoolean(true);
            fixture.prepareConversation();
            CampaignQuestAcceptanceService.beforeSelection(fixture.context(), "test_accept", Set.of(QUEST), Set.of(QUEST),
                    selected::get, () -> VillagerQuestService.performAction(fixture.context(), QUEST, VillagerActionDefinition.QuestAction.START));
            var request = CampaignQuestAcceptanceService.pending(fixture.player);
            selected.set(false);
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    request.id(), request.revision(), true));
            helper.assertTrue(fixture.progress(QUEST) == null, "unavailable option cannot execute its old actions");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void permissionRevocationPreventsAcceptance(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var request = fixture.confirmation();
            var leader = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.createParty(leader).success(), "leader creates party");
            try {
                var invitation = com.jvn.villagerretaliation.party.PartyService.sendInvitation(leader, fixture.player);
                helper.assertTrue(invitation.success(), "player receives invitation");
                helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.acceptInvitation(fixture.player, invitation.invitationId()).success(), "player joins");
                com.jvn.villagerretaliation.party.PartyService.setAdminPrivileges(leader, fixture.player.getUUID(), true);
                helper.assertTrue(CampaignAuthority.canControl(fixture.player), "checked member can control");
                com.jvn.villagerretaliation.party.PartyService.setAdminPrivileges(leader, fixture.player.getUUID(), false);
                CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                        request.id(), request.revision(), true));
                helper.assertTrue(fixture.progress(QUEST) == null, "revoked member cannot accept shared impact");
                helper.assertTrue(CampaignAuthority.canControl(leader), "leader retains control");
            } finally {
                com.jvn.villagerretaliation.party.PartyService.leaveParty(fixture.player);
                com.jvn.villagerretaliation.party.PartyService.leaveParty(leader);
            }
        }
        helper.succeed();
    }


    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void dialogueResponseWaitsForConfirmationBeforeItsFirstEffect(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var treeId = fixture.installConfirmationTree();
            fixture.player.setPos(fixture.villager.getX() + 1, fixture.villager.getY(), fixture.villager.getZ());
            helper.assertTrue(com.jvn.villagerretaliation.interaction.VillagerConversationService.start(fixture.player, fixture.villager),
                    "normal conversation starts");
            com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.startEntry(fixture.context(), treeId, "start");
            var option = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.activeOptions(fixture.context()).orElseThrow().getFirst();
            int experience = fixture.player.totalExperience;
            com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(fixture.player, fixture.villager.getId(), option.id());
            var request = CampaignQuestAcceptanceService.pending(fixture.player);
            helper.assertTrue(request != null, "real dialogue handler issues impact confirmation");
            helper.assertValueEqual(fixture.player.totalExperience, experience, "even actions before quest start wait for confirmation");
            helper.assertTrue(fixture.progress(QUEST) == null, "quest has not started behind prompt");
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    request.id(), request.revision(), true));
            helper.assertTrue(fixture.progress(QUEST) != null, "confirmed dialogue starts the quest");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 311, "response and quest effects execute once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void replacementDialogueNodeRejectsEarlierConfirmation(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var treeId = fixture.installConfirmationTree();
            fixture.player.setPos(fixture.villager.getX() + 1, fixture.villager.getY(), fixture.villager.getZ());
            com.jvn.villagerretaliation.interaction.VillagerConversationService.start(fixture.player, fixture.villager);
            com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.startEntry(fixture.context(), treeId, "start");
            var option = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.activeOptions(fixture.context()).orElseThrow().getFirst();
            com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(fixture.player, fixture.villager.getId(), option.id());
            var request = java.util.Objects.requireNonNull(CampaignQuestAcceptanceService.pending(fixture.player));
            com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.startEntry(fixture.context(), treeId, "start");
            int experience = fixture.player.totalExperience;
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    request.id(), request.revision(), true));
            helper.assertTrue(fixture.progress(QUEST) == null, "re-entering the same node still invalidates an earlier selection");
            helper.assertValueEqual(fixture.player.totalExperience, experience, "old node effects remain unapplied");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void missingCampaignContentInvalidatesConfirmation(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var request = fixture.confirmation();
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(
                    QuestContentCatalogs.current(fixture.server), Map.of(), List.of()));
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    request.id(), request.revision(), true));
            helper.assertTrue(fixture.progress(QUEST) == null, "missing campaign content cannot use a prior approval");
            helper.assertTrue(fixture.data.instances().containsKey(CAMPAIGN), "missing content preserves the campaign instance");
        }
        helper.succeed();
    }


    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void acceptedQuestResumesAfterSceneAndSaveReload(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            int experience = fixture.player.totalExperience;
            var continuation = fixture.interruptedOffer();
            helper.assertValueEqual(fixture.player.totalExperience - experience, 11, "only actions before the scene have run");
            helper.assertTrue(!continuation.campaignConsent().isEmpty(), "pending acceptance is attached to its action sequence");
            var scenes = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel());
            fixture.server.overworld().getDataStorage().set("villagerretaliation_scenes",
                    com.jvn.villagerretaliation.scene.persistence.SceneSavedData.load(scenes.save(new CompoundTag(),
                            helper.getLevel().registryAccess()), helper.getLevel().registryAccess()));
            fixture.finishInterruptedOffer(continuation.id());
            var completed = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel())
                    .continuation(continuation.id()).orElseThrow();
            helper.assertTrue(completed.completionReceipt(), "saved action sequence completes");
            helper.assertTrue(fixture.progress(QUEST) != null && fixture.progress(QUEST).campaignAcceptance() != null, "saved approval starts quest");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 316, "resumed actions and quest start run once");
            helper.assertTrue(completed.campaignConsent().isEmpty(), "consumed approval is removed from the continuation");
            fixture.finishInterruptedOffer(continuation.id());
            helper.assertValueEqual(fixture.player.totalExperience - experience, 316, "completed continuation cannot reuse approval");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void sharedOutcomeChangesInvalidateInterruptedApproval(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            int experience = fixture.player.totalExperience;
            var continuation = fixture.interruptedOffer();
            fixture.resolve();
            fixture.finishInterruptedOffer(continuation.id());
            helper.assertTrue(continuation.completionReceipt(), "outdated approval finishes without replay");
            helper.assertValueEqual(continuation.actionStatus(), com.jvn.villagerretaliation.action.ActionStatus.UNAVAILABLE,
                    "changed shared state requires a fresh quest offer");
            helper.assertTrue(fixture.progress(QUEST) == null, "original path is not started");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 11, "no actions after the interruption run");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void missingCampaignPausesInterruptedApprovalUntilRestored(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            int experience = fixture.player.totalExperience;
            var continuation = fixture.interruptedOffer();
            var catalog = QuestContentCatalogs.current(fixture.server);
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(catalog, Map.of(), List.of()));
            fixture.finishInterruptedOffer(continuation.id());
            helper.assertFalse(continuation.completionReceipt(), "missing content leaves continuation dormant");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 11, "dormant sequence has no further effects");
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(catalog, Map.of(CAMPAIGN, fixture.content), List.of()));
            fixture.finishInterruptedOffer(continuation.id());
            helper.assertTrue(continuation.completionReceipt() && fixture.progress(QUEST) != null, "restored compatible content resumes accepted quest");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 316, "restoration preserves the prior action cursor");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void revokedAuthorityPausesInterruptedApproval(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            int experience = fixture.player.totalExperience;
            var continuation = fixture.interruptedOffer();
            var leader = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.createParty(leader).success(), "party created");
            try {
                var invitation = com.jvn.villagerretaliation.party.PartyService.sendInvitation(leader, fixture.player);
                helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.acceptInvitation(fixture.player, invitation.invitationId()).success(),
                        "controller joins party");
                com.jvn.villagerretaliation.party.PartyService.setAdminPrivileges(leader, fixture.player.getUUID(), false);
                fixture.finishInterruptedOffer(continuation.id());
                helper.assertFalse(continuation.completionReceipt(), "revoked authority pauses the sequence");
                helper.assertTrue(continuation.actionDiagnostic().contains("Admin Privileges"), "pause explains required authority");
                helper.assertValueEqual(fixture.player.totalExperience - experience, 11, "paused sequence performs no further actions");
                com.jvn.villagerretaliation.party.PartyService.setAdminPrivileges(leader, fixture.player.getUUID(), true);
                fixture.finishInterruptedOffer(continuation.id());
                helper.assertTrue(fixture.progress(QUEST) != null, "restored authority resumes the approved start");
                helper.assertValueEqual(fixture.player.totalExperience - experience, 316, "resumption does not replay earlier actions");
            } finally {
                com.jvn.villagerretaliation.party.PartyService.leaveParty(fixture.player);
                com.jvn.villagerretaliation.party.PartyService.leaveParty(leader);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void unreadableInterruptedConsentIsPreservedAndBlocked(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            int experience = fixture.player.totalExperience;
            var continuation = fixture.interruptedOffer();
            var malformed = continuation.campaignConsent();
            malformed.putInt("Version", 99);
            continuation.campaignConsent(malformed);
            fixture.finishInterruptedOffer(continuation.id());
            helper.assertFalse(continuation.completionReceipt(), "future consent stays repairable");
            helper.assertValueEqual(continuation.campaignConsent().getInt("Version"), 99, "future data is preserved");
            helper.assertTrue(continuation.actionDiagnostic().contains("unsupported"), "unsupported version is explained");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 11, "future consent cannot authorize actions");
            var serialized = continuation.save();
            serialized.putString("CampaignConsent", "unreadable");
            var restored = com.jvn.villagerretaliation.scene.runtime.SceneContinuation.load(serialized);
            helper.assertFalse(restored.campaignConsent().isEmpty(), "invalid NBT cannot silently remove the consent gate");
            helper.assertTrue(CampaignQuestAcceptanceService.resumeAccess(fixture.context(), restored.campaignConsent()).paused(),
                    "invalid NBT pauses before any resumed action");
        }
        helper.succeed();
    }


    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void parentContinuationRecognizesApprovalConsumedByItsDependency(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            int experience = fixture.player.totalExperience;
            var child = fixture.interruptedOffer();
            var scenes = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel());
            var scene = scenes.get(child.sceneInstanceId()).orElseThrow();
            var actions = VillagerActionDefinition.readList(QUEST, "parent", JsonParser.parseString("""
                    {"actions":[{"type":"experience","amount":17}]}
                    """).getAsJsonObject());
            var parent = scenes.suspendContinuation(scene, fixture.player.getUUID(), fixture.villager.getUUID(),
                    "parent/approval", actions, 0, Map.of(), null, null);
            parent.campaignConsent(child.campaignConsent());
            parent.waitOn(scene.id(), child.id(), false);
            fixture.finishInterruptedOffer(child.id());
            com.jvn.villagerretaliation.scene.SceneContinuationService.maintain(fixture.server, scenes);
            helper.assertTrue(child.completionReceipt() && parent.completionReceipt(), "dependency and parent both finish");
            helper.assertValueEqual(parent.actionStatus(), com.jvn.villagerretaliation.action.ActionStatus.SUCCESS,
                    "child's accepted quest is not mistaken for conflicting progress");
            helper.assertTrue(parent.campaignConsent().isEmpty(), "parent retires consumed approval");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 333, "both sequences execute once");
            fixture.finishInterruptedOffer(child.id());
            helper.assertValueEqual(fixture.player.totalExperience - experience, 333, "nested approval cannot replay either sequence");
        }
        helper.succeed();
    }


    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void campaignJournalShowsChapterAccessWithoutStartingProgress(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var view = CampaignJournalPresenter.entries(CampaignWorldMode.CAMPAIGN, CampaignResources.current(fixture.server),
                    fixture.data, fixture.player.getUUID(), "en_us").getFirst();
            helper.assertTrue(view.chapters().stream().anyMatch(chapter -> chapter.id().equals("opening") && chapter.available()), "opening chapter is available");
            helper.assertTrue(view.chapters().stream().anyMatch(chapter -> chapter.id().equals("later") && !chapter.available()
                    && !chapter.blockers().isEmpty()), "later chapter explains its personal prerequisite");
            helper.assertTrue(fixture.data.instances().isEmpty() && fixture.progress(QUEST) == null, "journal reads do not enroll players or start campaigns");
            helper.assertTrue(CampaignJournalPresenter.entries(CampaignWorldMode.SANDBOX, CampaignResources.current(fixture.server),
                    fixture.data, fixture.player.getUUID(), "en_us").isEmpty(), "sandbox journal does not offer new campaigns");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void campaignJournalSeparatesSharedOutcomesFromDiscoveryAndParticipation(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, ALTERNATE)) {
            fixture.resolve();
            var catalog = CampaignResources.current(fixture.server);
            var unknown = CampaignJournalPresenter.entries(CampaignWorldMode.CAMPAIGN, catalog, fixture.data, fixture.player.getUUID(), "en_us")
                    .getFirst().milestones().getFirst();
            helper.assertTrue(unknown.resolved() && !unknown.discovered() && !unknown.participated(), "shared event does not invent personal knowledge or participation");
            helper.assertTrue(unknown.outcome().isBlank() && unknown.outcomeTitle().isBlank(), "undiscovered outcome text stays hidden");
            helper.assertTrue(unknown.catchUp() && unknown.catchUpQuest().equals(ALTERNATE.toString()), "late player can find the authored alternate quest");
            fixture.data.enterMilestone(fixture.content.definition(), fixture.player.getUUID(), "dragon", 3L);
            var known = CampaignJournalPresenter.entries(CampaignWorldMode.CAMPAIGN, catalog, fixture.data, fixture.player.getUUID(), "en_us")
                    .getFirst().milestones().getFirst();
            helper.assertTrue(known.discovered() && known.entered() && known.catchUp() && !known.participated(),
                    "catch-up discovery is shown independently from participant credit");
            helper.assertValueEqual(known.outcome(), "defeated", "journal reports the actual shared outcome");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void campaignJournalPreservesParticipantStatus(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "original path begins");
            fixture.resolve(Set.of(fixture.player.getUUID()));
            var view = CampaignJournalPresenter.entries(CampaignWorldMode.CAMPAIGN, CampaignResources.current(fixture.server),
                    fixture.data, fixture.player.getUUID(), "en_us").getFirst().milestones().getFirst();
            helper.assertTrue(view.resolved() && view.discovered() && view.participated() && !view.catchUp(),
                    "participant keeps the original route and explicit credit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void campaignJournalRetainsReadableHistoryWhenContentIsMissing(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.resolve();
            fixture.data.discover(fixture.content.definition(), fixture.player.getUUID(), "dragon");
            var missing = new CampaignContentCatalog(QuestContentCatalogs.current(fixture.server), Map.of(), List.of());
            var view = CampaignJournalPresenter.entries(CampaignWorldMode.CAMPAIGN, missing, fixture.data, fixture.player.getUUID(), "en_us").getFirst();
            helper.assertTrue(!view.diagnostic().isBlank() && view.chapters().isEmpty(), "missing content is dormant with a diagnostic");
            helper.assertValueEqual(view.title(), fixture.data.instances().get(CAMPAIGN).title(), "saved campaign title remains readable");
            helper.assertTrue(view.milestones().getFirst().discovered() && !view.milestones().getFirst().participated(), "saved personal distinction survives missing definitions");
            var sandbox = CampaignJournalPresenter.entries(CampaignWorldMode.SANDBOX, missing, fixture.data, fixture.player.getUUID(), "en_us").getFirst();
            helper.assertTrue(!sandbox.diagnostic().isBlank() && sandbox.milestones().getFirst().resolved(), "disabled progression does not erase recorded outcomes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void campaignJournalKeepsUnresolvedPersonalProgressWhenContentIsMissing(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "original path begins");
            var missing = new CampaignContentCatalog(QuestContentCatalogs.current(fixture.server), Map.of(), List.of());
            var view = CampaignJournalPresenter.entries(CampaignWorldMode.CAMPAIGN, missing, fixture.data, fixture.player.getUUID(), "en_us").getFirst();
            helper.assertValueEqual(view.milestones().size(), 1, "unresolved personal milestones remain visible without definitions");
            var milestone = view.milestones().getFirst();
            helper.assertTrue(milestone.entered() && !milestone.resolved() && !milestone.completed() && !milestone.participated(),
                    "missing content preserves in-progress state without inventing outcomes or participation");
        }
        helper.succeed();
    }

    private static CompiledQuest quest(ResourceLocation id, String milestoneStage) {
        var raw = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"test:entry_quest","localization_prefix":"quest.entry_fixture.",
                 "metadata":{"campaign":"test:entry_route"},"provider":{"type":"villagerretaliation:villager"},
                 "events":[{"id":"began","event":"started","actions":[{"type":"experience","amount":200}]},
                           {"id":"ended","event":"completed","actions":[{"type":"experience","amount":300}]}],
                 "rewards":{"experience":500},"entry_stage":"started","stages":[
                  {"id":"started","next":"catch_up","on_enter":[{"type":"experience","amount":100}],"objectives":[{"id":"decide","type":"choice"}]},
                  {"id":"catch_up","on_enter":[{"type":"experience","amount":5}],"objectives":[{"id":"learn","type":"choice"}]}]}
                """).getAsJsonObject();
        raw.addProperty("id", id.toString());
        if (id.equals(FOLLOW_UP) || id.equals(COOLDOWN)) {
            raw.add("availability", JsonParser.parseString("""
                    {"prerequisites":["test:entry_quest"]}
                    """));
            if (id.equals(COOLDOWN)) raw.getAsJsonObject("availability").addProperty("prerequisite_cooldown_ticks", 20);
        }
        if (!milestoneStage.isBlank()) {
            var stages = raw.getAsJsonArray("stages");
            var opening = stages.get(0).getAsJsonObject();
            opening.addProperty("next", milestoneStage);
            opening.add("on_exit", JsonParser.parseString("""
                    [{"type":"experience","amount":40}]
                    """));
            stages.add(JsonParser.parseString("""
                    {"id":"decision","next":"catch_up","on_enter":[{"type":"experience","amount":70}],
                     "objectives":[{"id":"resolve","type":"choice"}]}
                    """));
        }
        var envelope = QuestResourceEnvelope.read(id, raw).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }

    private static final class Fixture implements AutoCloseable {
        private final MinecraftServer server;
        private final QuestContentCatalog previous;
        private final QuestContentLoadReport report;
        private final CampaignSavedData previousData;
        private final VillagerQuestSavedData previousQuests;
        private final com.jvn.villagerretaliation.scene.persistence.SceneSavedData previousScenes;
        private final Path directory;
        private final CampaignSavedData data;
        private final CampaignContentCatalog.Entry content;
        private final ServerPlayer player;
        private final Villager villager;

        private Fixture(GameTestHelper helper, ResourceLocation catchUpQuest) throws IOException {
            this(helper, catchUpQuest, "");
        }

        private Fixture(GameTestHelper helper, ResourceLocation catchUpQuest, String milestoneStage) throws IOException {
            this.server = helper.getLevel().getServer();
            this.previous = QuestContentCatalogs.current(server);
            this.report = QuestContentCatalogs.loadReport(server);
            this.previousData = CampaignSavedData.get(helper.getLevel());
            this.previousQuests = VillagerQuestSavedData.get(helper.getLevel());
            server.overworld().getDataStorage().set("villagerretaliation_quests", new VillagerQuestSavedData());
            this.previousScenes = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel());
            server.overworld().getDataStorage().set("villagerretaliation_scenes", new com.jvn.villagerretaliation.scene.persistence.SceneSavedData());
            this.directory = Files.createTempDirectory("campaign-entry-");
            this.data = CampaignSavedData.load(new CompoundTag(), directory);
            this.player = helper.makeMockServerPlayerInLevel();
            this.villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            var compiled = new LinkedHashMap<ResourceLocation, CompiledQuest>();
            previous.compiledQuests().forEach(value -> compiled.put(value.id(), value));
            var definitions = new LinkedHashMap<>(previous.quests());
            for (var id : List.of(QUEST, ALTERNATE, FOLLOW_UP, COOLDOWN)) {
                var quest = quest(id, milestoneStage);
                compiled.put(id, quest);
                definitions.put(id, quest.asQuestDefinition());
            }
            var replacement = new QuestContentCatalog(previous.generation(), new CompiledQuestCatalog(compiled), previous.dialogueCatalog(),
                    definitions, previous.objectiveEventQuestIds(), previous.factQuestIds(), previous.memoryEventQuestIds(),
                    previous.exclusiveGroupQuestIds(), previous.triggerEventQuestIds(), previous.scenes(), previous.encounters(), previous.pools(),
                    previous.bundles(), previous.localization(), previous.rewards());
            var definition = new CampaignDefinition(CAMPAIGN, 1, "fixture", "campaign.entry.title", "campaign.entry.",
                    List.of(QUEST, ALTERNATE, FOLLOW_UP, COOLDOWN), Map.of(
                            "opening", new CampaignDefinition.Chapter("opening", "campaign.entry.chapter", List.of(), List.of(QUEST), ""),
                            "later", new CampaignDefinition.Chapter("later", "campaign.entry.later",
                                    List.of(new CampaignDefinition.Prerequisite("opening", "", "", "")), List.of(FOLLOW_UP, COOLDOWN), "")),
                    Map.of("dragon", new CampaignDefinition.Milestone("dragon", "opening", QUEST, milestoneStage, true,
                            Map.of("defeated", new CampaignDefinition.Outcome("defeated", "campaign.entry.defeated", "")),
                            new CampaignDefinition.CatchUp(catchUpQuest, "catch_up", ""), null, "")), Map.of(), CAMPAIGN);
            this.content = new CampaignContentCatalog.Entry(definition, QuestLocaleCatalog.empty());
            QuestContentCatalogs.installForTests(server, replacement, report);
            CampaignResources.installForTests(server, new CampaignContentCatalog(replacement, Map.of(CAMPAIGN, content), List.of()));
            server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, data);
        }



        private ResourceLocation installConfirmationTree() {
            return installConfirmationTree(false);
        }

        private ResourceLocation installConfirmationTree(boolean interrupted) {
            var treeId = ResourceLocation.parse("test:campaign_confirmation");
            var raw = JsonParser.parseString("""
                    {"id":"test:campaign_confirmation","entries":[{"id":"start","label":"Start","start":"opening"}],
                     "nodes":{"opening":{"lines":["Ready"],"responses":[{"id":"accept","label":"Accept","end":true,
                      "actions":[{"type":"experience","amount":11},{"type":"quest","quest":"test:entry_quest","action":"start","required":true}]}]}}}
                    """).getAsJsonObject();
            var current = QuestContentCatalogs.current(server);
            var scenes = new LinkedHashMap<>(current.scenes());
            if (interrupted) {
                var actions = raw.getAsJsonObject("nodes").getAsJsonObject("opening")
                        .getAsJsonArray("responses").get(0).getAsJsonObject().getAsJsonArray("actions");
                var start = actions.remove(1);
                actions.add(JsonParser.parseString("""
                        {"type":"start_scene","scene":"test:approval_wait","operation_id":"acceptance_wait","wait_for_result":true,"required":true}
                        """));
                actions.add(JsonParser.parseString("""
                        {"type":"experience","amount":5}
                        """));
                actions.add(start);
                var scene = com.jvn.villagerretaliation.scene.compiler.SceneCompiler.compile(
                        com.jvn.villagerretaliation.scene.compiler.SceneParser.parse(CAMPAIGN, JsonParser.parseString("""
                                {"schema":"villagerretaliation:scene/v1","id":"test:approval_wait","ownership":"player",
                                 "entry_step":"wait","actors":[],"steps":[
                                  {"id":"wait","type":"villagerretaliation:wait_ticks","data":{"ticks":20},"next":"done"},
                                  {"id":"done","type":"villagerretaliation:scene_complete"}]}
                                """).getAsJsonObject()).resource()).scene();
                if (scene == null) throw new IllegalStateException("Approval scene did not compile");
                scenes.put(scene.id(), scene);
            }
            var tree = com.jvn.villagerretaliation.dialogue.DialogueTreeResources.readGeneratedTree(treeId, raw, QUEST);
            var trees = new LinkedHashMap<>(current.dialogueCatalog().trees());
            trees.put(treeId, tree);
            var dialogue = new com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCatalog(
                    trees, current.dialogueCatalog().bindings(), current.dialogueCatalog().treeSources());
            var replacement = new QuestContentCatalog(current.generation(), new CompiledQuestCatalog(
                    current.compiledQuests().stream().collect(java.util.stream.Collectors.toMap(CompiledQuest::id, value -> value))),
                    dialogue, current.quests(), current.objectiveEventQuestIds(), current.factQuestIds(), current.memoryEventQuestIds(),
                    current.exclusiveGroupQuestIds(), current.triggerEventQuestIds(), scenes, current.encounters(), current.pools(),
                    current.bundles(), current.localization(), current.rewards());
            QuestContentCatalogs.installForTests(server, replacement, report);
            CampaignResources.installForTests(server, new CampaignContentCatalog(replacement, Map.of(CAMPAIGN, content), List.of()));
            com.jvn.villagerretaliation.dialogue.DialogueTreeResources.clearCache();
            if (interrupted) com.jvn.villagerretaliation.scene.SceneRuntime.initialize(server);
            return treeId;
        }


        private com.jvn.villagerretaliation.scene.runtime.SceneContinuation interruptedOffer() {
            var tree = installConfirmationTree(true);
            player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
            if (!com.jvn.villagerretaliation.interaction.VillagerConversationService.start(player, villager)) {
                throw new IllegalStateException("Could not start interrupted fixture conversation");
            }
            com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.startEntry(context(), tree, "start");
            var option = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.activeOptions(context()).orElseThrow().getFirst();
            com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player, villager.getId(), option.id());
            var request = java.util.Objects.requireNonNull(CampaignQuestAcceptanceService.pending(player));
            CampaignQuestAcceptanceService.handle(player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    request.id(), request.revision(), true));
            return com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(player.serverLevel()).continuations().stream()
                    .filter(value -> player.getUUID().equals(value.playerId()) && !value.completionReceipt()).findFirst().orElseThrow();
        }

        private void finishInterruptedOffer(UUID continuationId) {
            var data = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(player.serverLevel());
            var continuation = data.continuation(continuationId).orElseThrow();
            var scene = data.get(continuation.sceneInstanceId()).orElseThrow();
            if (!scene.state().terminal()) com.jvn.villagerretaliation.scene.runtime.SceneTransitionService.complete(
                    data, scene, player.serverLevel().getGameTime());
            com.jvn.villagerretaliation.scene.SceneContinuationService.maintain(server, data);
        }

        private com.jvn.villagerretaliation.dialogue.DialogueContext context() {
            return VillagerInteractionService.createDialogueContext(player.serverLevel(), player, villager);
        }

        private void prepareConversation() {
            player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
            if (!com.jvn.villagerretaliation.interaction.VillagerConversationService.startForcedIgnoringDisposition(player, villager)) {
                throw new IllegalStateException("Could not start fixture conversation");
            }
        }

        private CampaignQuestAcceptanceService.Request confirmation() {
            if (com.jvn.villagerretaliation.interaction.VillagerConversationService.sessionId(player, villager) == null) prepareConversation();
            CampaignQuestAcceptanceService.beforeSelection(context(), "test_accept", Set.of(QUEST), Set.of(QUEST), () -> true,
                    () -> VillagerQuestService.performAction(context(), QUEST, VillagerActionDefinition.QuestAction.START));
            return java.util.Objects.requireNonNull(CampaignQuestAcceptanceService.pending(player), "confirmation created");
        }

        private VillagerQuestSavedData.QuestProgress progress(ResourceLocation quest) {
            return VillagerQuestSavedData.get(server.overworld()).get(player.getUUID(), quest);
        }

        private void resolve() throws IOException {
            resolve(Set.of());
        }

        private com.jvn.villagerretaliation.quest.QuestDefinition definition(ResourceLocation id) {
            return com.jvn.villagerretaliation.quest.VillagerQuestResources.quest(server, id).orElseThrow();
        }

        private void resolve(Set<UUID> participants) throws IOException {
            data.open(content, 1L);
            data.commitWorldOutcome(content, "dragon", "defeated", UUID.randomUUID(), 0L,
                    CampaignInstance.Origin.PLAYER_DECISION, participants, 2L);
        }

        @Override
        public void close() throws IOException {
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(player, false);
            CampaignQuestAcceptanceService.onLogout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
            server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, previousData);
            server.overworld().getDataStorage().set("villagerretaliation_quests", previousQuests);
            previousQuests.setDirty();
            previousScenes.setDirty();
            server.overworld().getDataStorage().set("villagerretaliation_scenes", previousScenes);
            QuestContentCatalogs.installForTests(server, previous, report);
            CampaignResources.clearCache();
            com.jvn.villagerretaliation.dialogue.DialogueTreeResources.clearCache();
            com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.clearRuntimeState();
            VillagerQuestSavedData.get(server.overworld()).remove(player.getUUID(), QUEST);
            VillagerQuestSavedData.get(server.overworld()).remove(player.getUUID(), ALTERNATE);
            VillagerQuestSavedData.get(server.overworld()).remove(player.getUUID(), FOLLOW_UP);
            Files.deleteIfExists(directory.resolve(CampaignSavedData.DATA_NAME + ".dat"));
            Files.deleteIfExists(directory);
        }
    }
}
