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
            server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, previousData);
            server.overworld().getDataStorage().set("villagerretaliation_quests", previousQuests);
            previousQuests.setDirty();
            previousScenes.setDirty();
            server.overworld().getDataStorage().set("villagerretaliation_scenes", previousScenes);
            QuestContentCatalogs.installForTests(server, previous, report);
            CampaignResources.clearCache();
            VillagerQuestSavedData.get(server.overworld()).remove(player.getUUID(), QUEST);
            VillagerQuestSavedData.get(server.overworld()).remove(player.getUUID(), ALTERNATE);
            VillagerQuestSavedData.get(server.overworld()).remove(player.getUUID(), FOLLOW_UP);
            Files.deleteIfExists(directory.resolve(CampaignSavedData.DATA_NAME + ".dat"));
            Files.deleteIfExists(directory);
        }
    }
}
