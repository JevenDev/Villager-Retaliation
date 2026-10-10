package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
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
    public static void journalCampaignAcceptanceWorksWithoutConversationOrProvider(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var context = com.jvn.villagerretaliation.dialogue.DialogueContext.forPlayer(fixture.player);
            CampaignQuestAcceptanceService.beforeSelection(context, "discovery:" + QUEST, Set.of(QUEST), Set.of(QUEST), () -> true,
                    () -> VillagerQuestService.startDiscoveredQuest(context, QUEST));
            var request = CampaignQuestAcceptanceService.pending(fixture.player);
            helper.assertTrue(request != null && request.entityId() < 0 && request.providerId() == null, "journal confirmation does not invent a provider");
            helper.assertTrue(fixture.progress(QUEST) == null, "irreversible quest waits for confirmation");
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    request.id(), request.revision(), true));
            helper.assertTrue(fixture.progress(QUEST) != null && fixture.progress(QUEST).state() == VillagerQuestSavedData.QuestState.ACTIVE, "confirmed journal quest starts");
            helper.assertTrue(fixture.progress(QUEST).startedVillagerId() == null, "accepted quest remains provider-free");
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

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void fixedOutcomeUsesAcceptanceAndCommitsOnlyOnce(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.acceptQuest();
            var action = outcomeAction("defeated");
            var applied = CampaignOutcomeService.execute(fixture.context(), action);
            helper.assertTrue(applied.success(), "a consequence fixed at acceptance needs no second prompt: " + applied.message());
            var instance = fixture.data.instances().get(CAMPAIGN);
            var outcome = instance.outcomes().get("dragon");
            helper.assertTrue(outcome != null && instance.players().get(fixture.player.getUUID()).participation().contains("dragon"),
                    "decision grants the controller explicit participation");
            helper.assertValueEqual(CampaignOutcomeService.execute(fixture.context(), action).status(),
                    com.jvn.villagerretaliation.action.ActionStatus.ALREADY_SATISFIED, "duplicate operation returns its receipt");
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).worldRevision(), 1L, "duplicate operation cannot commit again");
            var restored = CampaignSavedData.load(fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess()), fixture.directory);
            helper.assertValueEqual(restored.instances().get(CAMPAIGN), instance, "committed identity and participation survive reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void unconfirmedOutcomeStopsFollowingActions(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "", true)) {
            fixture.acceptQuest();
            int experience = fixture.player.totalExperience;
            var actions = List.of(outcomeAction("spared"), experienceAction(13));
            var result = com.jvn.villagerretaliation.action.ActionSequenceRunner.run(fixture.context(), actions, 0, Map.of(), null, null);
            helper.assertFalse(result.outcome().success(), "accepting an open choice does not approve a particular outcome");
            helper.assertValueEqual(fixture.player.totalExperience, experience, "required decision failure prevents following rewards");
            helper.assertTrue(fixture.data.instances().get(CAMPAIGN).outcomes().isEmpty(), "unconfirmed decision has no world effect");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void dialogueDecisionWaitsForConfirmationBeforeEffects(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "", true)) {
            fixture.acceptQuest();
            var tree = fixture.installConfirmationTree(false, true);
            fixture.startDecisionTree(tree);
            var option = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.activeOptions(fixture.context()).orElseThrow().getFirst();
            int experience = fixture.player.totalExperience;
            com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(fixture.player, fixture.villager.getId(), option.id());
            var request = CampaignQuestAcceptanceService.pending(fixture.player);
            helper.assertTrue(request != null && request.decisions().size() == 1 && request.impacts().isEmpty(), "existing dialogue opens a decision confirmation");
            helper.assertValueEqual(fixture.player.totalExperience, experience, "even effects before the decision wait for confirmation");
            fixture.confirm(request);
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).outcomes().get("dragon").outcome(), "spared", "authored outcome commits");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 11, "confirmed response runs once");
            fixture.confirm(request);
            helper.assertValueEqual(fixture.player.totalExperience - experience, 11, "duplicate confirmation cannot replay response effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void competingPlayersKeepTheFirstDecisionAndReconcileTheOther(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "", true)) {
            fixture.acceptQuest();
            var other = helper.makeMockServerPlayerInLevel();
            var provider = helper.spawn(EntityType.VILLAGER, 3, 1, 3);
            other.setPos(provider.getX() + 1, provider.getY(), provider.getZ());
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), other, provider);
            try {
                helper.assertTrue(VillagerQuestService.debugStartQuest(other, provider, QUEST, true).started(), "second player undertakes the same milestone");
                helper.assertTrue(com.jvn.villagerretaliation.interaction.VillagerConversationService.startForcedIgnoringDisposition(other, provider), "second conversation starts");
                var first = fixture.decision(outcomeAction("spared"), () -> CampaignOutcomeService.execute(fixture.context(), outcomeAction("spared")));
                int experience = other.totalExperience;
                var actions = List.of(outcomeAction("defeated"), experienceAction(13));
                CampaignQuestAcceptanceService.beforeSelection(context, "other_decision", Set.of(), Set.of(QUEST), actions, () -> true,
                        () -> com.jvn.villagerretaliation.action.ActionSequenceRunner.run(context, actions, 0, Map.of(), null, null));
                var second = CampaignQuestAcceptanceService.pending(other);
                helper.assertTrue(second != null, "both requests observe the unresolved milestone");
                fixture.confirm(first);
                CampaignQuestAcceptanceService.handle(other, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(second.id(), second.revision(), true));
                var instance = fixture.data.instances().get(CAMPAIGN);
                helper.assertValueEqual(instance.outcomes().get("dragon").outcome(), "spared", "first committed outcome remains authoritative");
                helper.assertValueEqual(instance.worldRevision(), 1L, "competing choice does not commit twice");
                var late = instance.players().get(other.getUUID());
                helper.assertTrue(late.participation().isEmpty() && late.milestones().get("dragon").route() == CampaignInstance.Route.CATCH_UP,
                        "losing player receives catch-up without participant credit");
                helper.assertValueEqual(other.totalExperience, experience, "losing choice cannot grant its reward");
                helper.assertTrue(CampaignOutcomeService.execute(context, outcomeAction("defeated")).message().contains("spared"),
                        "losing action reports the actual committed outcome");
            } finally {
                com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(other, false);
                CampaignQuestAcceptanceService.onLogout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(other));
                other.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void declinedDecisionHasNoEffects(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "", true)) {
            fixture.acceptQuest();
            var request = fixture.decision(outcomeAction("spared"), () -> CampaignOutcomeService.execute(fixture.context(), outcomeAction("spared")));
            CampaignQuestAcceptanceService.handle(fixture.player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(request.id(), request.revision(), false));
            fixture.confirm(request);
            helper.assertTrue(fixture.data.instances().get(CAMPAIGN).outcomes().isEmpty(), "declined approval cannot be reused");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void interruptedDecisionSurvivesReloadAndCommitsOnce(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "", true)) {
            fixture.acceptQuest();
            int experience = fixture.player.totalExperience;
            var continuation = fixture.interruptedDecision();
            helper.assertTrue(!continuation.campaignConsent().isEmpty(), "pending outcome approval is captured");
            var restored = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.load(
                    com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            fixture.server.overworld().getDataStorage().set("villagerretaliation_scenes", restored);
            fixture.finishInterruptedOffer(continuation.id());
            helper.assertTrue(restored.continuation(continuation.id()).orElseThrow().completionReceipt(), "saved decision resumes");
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).outcomes().get("dragon").outcome(), "spared", "saved outcome remains selected");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 16, "scene resumes at the saved action cursor");
            fixture.finishInterruptedOffer(continuation.id());
            helper.assertValueEqual(fixture.player.totalExperience - experience, 16, "resuming again cannot duplicate following effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void interruptedDecisionRejectsAChangedWorld(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "", true)) {
            fixture.acceptQuest();
            int experience = fixture.player.totalExperience;
            var continuation = fixture.interruptedDecision();
            fixture.resolve();
            fixture.finishInterruptedOffer(continuation.id());
            helper.assertValueEqual(continuation.actionStatus(), com.jvn.villagerretaliation.action.ActionStatus.UNAVAILABLE, "stale decision is rejected");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 11, "actions after the interrupted decision do not run");
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).outcomes().get("dragon").outcome(), "defeated", "external outcome remains authoritative");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void malformedSavedDecisionIsPreservedAndCannotResume(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "", true)) {
            fixture.acceptQuest();
            int experience = fixture.player.totalExperience;
            var continuation = fixture.interruptedDecision();
            var saved = continuation.save();
            var actions = saved.getList("Actions", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var raw : actions) {
                var action = (CompoundTag) raw;
                if (action.getString("Kind").equals("CAMPAIGN_OUTCOME")) action.getCompound("CampaignOutcome").putString("Outcome", "invalid outcome");
            }
            var restored = com.jvn.villagerretaliation.scene.runtime.SceneContinuation.load(saved);
            helper.assertTrue(restored.hasUnreadableActions(), "malformed outcome is retained for repair");
            helper.assertValueEqual(restored.save().get("Actions"), saved.get("Actions"), "malformed action bytes survive another save");
            var data = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel());
            var whole = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            var continuations = new net.minecraft.nbt.ListTag();
            continuations.add(saved);
            whole.put("Continuations", continuations);
            fixture.server.overworld().getDataStorage().set("villagerretaliation_scenes",
                    com.jvn.villagerretaliation.scene.persistence.SceneSavedData.load(whole, helper.getLevel().registryAccess()));
            fixture.finishInterruptedOffer(continuation.id());
            helper.assertTrue(fixture.data.instances().get(CAMPAIGN).outcomes().isEmpty(), "unreadable action cannot commit an outcome");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 11, "unreadable action pauses the whole remaining sequence");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void interruptedDecisionRejectsLeavingAndReturningToTheSameStage(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "", true)) {
            fixture.acceptQuest();
            int experience = fixture.player.totalExperience;
            var continuation = fixture.interruptedDecision();
            fixture.progress(QUEST).setCurrentStage("catch_up");
            fixture.progress(QUEST).setCurrentStage("started");
            fixture.finishInterruptedOffer(continuation.id());
            helper.assertValueEqual(continuation.actionStatus(), com.jvn.villagerretaliation.action.ActionStatus.UNAVAILABLE,
                    "returning to the same stage cannot reuse approval from its previous revision");
            helper.assertTrue(fixture.data.instances().get(CAMPAIGN).outcomes().isEmpty(), "old stage approval has no world effect");
            helper.assertValueEqual(fixture.player.totalExperience - experience, 11, "stale stage does not resume following effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void interruptedDecisionWaitsForContentAndAuthorityToReturn(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "", true)) {
            fixture.acceptQuest();
            int experience = fixture.player.totalExperience;
            var continuation = fixture.interruptedDecision();
            var catalog = QuestContentCatalogs.current(fixture.server);
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(catalog, Map.of(), List.of()));
            fixture.finishInterruptedOffer(continuation.id());
            helper.assertFalse(continuation.completionReceipt(), "missing story content pauses decision approval");
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(catalog, Map.of(CAMPAIGN, fixture.content), List.of()));
            var leader = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.createParty(leader).success(), "party created");
            try {
                var invitation = com.jvn.villagerretaliation.party.PartyService.sendInvitation(leader, fixture.player);
                helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.acceptInvitation(fixture.player, invitation.invitationId()).success(), "controller joins party");
                com.jvn.villagerretaliation.party.PartyService.setAdminPrivileges(leader, fixture.player.getUUID(), false);
                fixture.finishInterruptedOffer(continuation.id());
                helper.assertFalse(continuation.completionReceipt(), "missing Admin Privileges pauses the approved decision");
                helper.assertTrue(fixture.data.instances().get(CAMPAIGN).outcomes().isEmpty(), "paused decision has no world effect");
                helper.assertValueEqual(fixture.player.totalExperience - experience, 11, "no effects run while approval is paused");
                com.jvn.villagerretaliation.party.PartyService.setAdminPrivileges(leader, fixture.player.getUUID(), true);
                fixture.finishInterruptedOffer(continuation.id());
                helper.assertTrue(continuation.completionReceipt(), "restored content and authority resume the approved decision");
                helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).outcomes().get("dragon").outcome(), "spared", "restored approval preserves the selected outcome");
            } finally {
                com.jvn.villagerretaliation.party.PartyService.leaveParty(fixture.player);
                com.jvn.villagerretaliation.party.PartyService.leaveParty(leader);
                leader.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void authoredFactActionsKeepScopesAndCannotRepeatAfterReload(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.acceptQuest();
            var shared = factAction("public_count", "add", "3", "shared");
            var personal = factAction("private_count", "add", "2", "personal");
            helper.assertTrue(CampaignFactService.execute(fixture.context(), shared).success(), "shared fact applies");
            helper.assertTrue(CampaignFactService.execute(fixture.context(), personal).success(), "personal fact applies");
            var restored = CampaignSavedData.load(fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess()), fixture.directory);
            fixture.server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, restored);
            helper.assertValueEqual(CampaignFactService.execute(fixture.context(), shared).status(),
                    com.jvn.villagerretaliation.action.ActionStatus.ALREADY_SATISFIED, "duplicate shared action returns its durable receipt");
            helper.assertValueEqual(CampaignFactService.execute(fixture.context(), personal).status(),
                    com.jvn.villagerretaliation.action.ActionStatus.ALREADY_SATISFIED, "duplicate personal action returns its durable receipt");
            helper.assertValueEqual(restored.readFact(fixture.content.definition(), UUID.randomUUID(), "public_count").value().text(), "3", "world value is shared and not incremented again");
            helper.assertTrue(restored.readFact(fixture.content.definition(), UUID.randomUUID(), "private_count").value() == null, "another player's discovery remains absent");
            helper.assertTrue(factCondition("private_count", "at_least", "2").matches(fixture.context()), "counter threshold reads saved personal scope");
            helper.assertFalse(factCondition("private_count", "at_most", "1").matches(fixture.context()), "counter comparison rejects unmet threshold");
            helper.assertTrue(restored.instances().get(CAMPAIGN).players().get(fixture.player.getUUID()).participation().isEmpty(), "fact action grants no participation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void catchUpFactActionsAllowDiscoveryButRejectWorldChanges(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.resolve();
            helper.assertTrue(VillagerQuestService.debugStartQuest(fixture.player, fixture.villager, QUEST, true).started(), "late player starts catch-up");
            var before = fixture.data.instances().get(CAMPAIGN);
            helper.assertFalse(CampaignFactService.execute(fixture.context(), factAction("public_count", "add", "3", "shared")).success(), "catch-up cannot mutate shared facts");
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN), before, "rejected world action has no receipt or revision");
            helper.assertTrue(CampaignFactService.execute(fixture.context(), factAction("knows_gate", "set", "true", "discovery")).success(), "catch-up can record authored personal discovery");
            helper.assertTrue(factCondition("knows_gate", "equals", "true").matches(fixture.context()), "personal discovery unlocks its condition");
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).worldRevision(), before.worldRevision(), "catch-up discovery leaves the world unchanged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void unavailableCampaignFactsRemainUnavailableThroughNestedNegation(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.acceptQuest();
            var condition = factCondition("knows_gate", "equals", "true");
            var negated = new com.jvn.villagerretaliation.dialogue.DialogueCondition.Not(condition);
            helper.assertTrue(negated.matches(fixture.context()), "an unset fact in available content may be tested through negation");
            var nested = new com.jvn.villagerretaliation.dialogue.DialogueCondition.Not(
                    new com.jvn.villagerretaliation.dialogue.DialogueCondition.AnyOf(List.of(negated, condition)));
            var catalog = QuestContentCatalogs.current(fixture.server);
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(catalog, Map.of(), List.of()));
            for (var candidate : List.of(condition, negated, nested)) {
                helper.assertFalse(candidate.matches(fixture.context()), "missing content cannot unlock a negated condition");
                helper.assertFalse(candidate.matches(fixture.context(), null), "trigger evaluation uses the same missing-content boundary");
                helper.assertValueEqual(com.jvn.villagerretaliation.dialogue.DialogueCondition.trace(fixture.context(), candidate).outcome(),
                        com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.UNKNOWN, "diagnostics distinguish missing content from an unmet condition");
            }
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(catalog, Map.of(CAMPAIGN, fixture.content), List.of()));
            helper.assertTrue(negated.matches(fixture.context()), "restoring content resumes the authored condition");
            var invalidComparison = factCondition("knows_gate", "at_least", "true");
            helper.assertFalse(new com.jvn.villagerretaliation.dialogue.DialogueCondition.Not(invalidComparison).matches(fixture.context()), "invalid numeric comparison cannot unlock its negation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void invalidFactActionsStopFollowingEffects(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.acceptQuest();
            int before = fixture.player.totalExperience;
            for (var action : List.of(factAction("missing", "set", "true", "missing"),
                    factAction("knows_gate", "set", "yes", "invalid_tag"), factAction("ruler", "add", "1", "invalid_counter"))) {
                var sequence = com.jvn.villagerretaliation.action.ActionSequenceRunner.run(fixture.context(),
                        List.of(action, experienceAction(13)), 0, Map.of(), null, null);
                helper.assertFalse(sequence.outcome().success(), "invalid authored fact is a required failure");
            }
            helper.assertValueEqual(fixture.player.totalExperience, before, "required fact failures prevent following rewards");
            helper.assertTrue(fixture.data.instances().get(CAMPAIGN).facts().receipts().isEmpty(), "invalid actions save no receipts");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void storyFactActionsRequireCurrentAdminPrivileges(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.acceptQuest();
            var leader = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.createParty(leader).success(), "party created");
            try {
                var invitation = com.jvn.villagerretaliation.party.PartyService.sendInvitation(leader, fixture.player);
                helper.assertTrue(com.jvn.villagerretaliation.party.PartyService.acceptInvitation(fixture.player, invitation.invitationId()).success(), "player joins party");
                com.jvn.villagerretaliation.party.PartyService.setAdminPrivileges(leader, fixture.player.getUUID(), false);
                var action = factAction("knows_gate", "set", "true", "discover");
                helper.assertFalse(CampaignFactService.execute(fixture.context(), action).success(), "listener cannot control campaign effects");
                helper.assertTrue(fixture.data.instances().get(CAMPAIGN).facts().receipts().isEmpty(), "permission failure has no effects");
                com.jvn.villagerretaliation.party.PartyService.setAdminPrivileges(leader, fixture.player.getUUID(), true);
                helper.assertTrue(CampaignFactService.execute(fixture.context(), action).success(), "restored authority allows the authored effect");
            } finally {
                com.jvn.villagerretaliation.party.PartyService.leaveParty(fixture.player);
                com.jvn.villagerretaliation.party.PartyService.leaveParty(leader);
                leader.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void storyFactReceiptsSurviveSceneSuspensionAndReload(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.acceptQuest();
            fixture.installConfirmationTree(true);
            var action = factAction("private_count", "add", "3", "count_once");
            var wait = VillagerActionDefinition.readList(CAMPAIGN, "test", JsonParser.parseString("""
                    {"actions":[{"type":"start_scene","scene":"test:approval_wait","operation_id":"fact_wait","wait_for_result":true,"required":true}]}
                    """).getAsJsonObject(), QUEST).getFirst();
            int before = fixture.player.totalExperience;
            var result = com.jvn.villagerretaliation.scene.SceneContinuationService.run(fixture.context(),
                    List.of(action, wait, action, experienceAction(13)), Map.of(), null, "fact_fixture", null, QUEST, fixture.progress(QUEST).questRunId());
            helper.assertTrue(result.suspended(), "authored scene pauses after the first fact operation");
            var restored = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.load(
                    com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            fixture.server.overworld().getDataStorage().set("villagerretaliation_scenes", restored);
            fixture.finishInterruptedOffer(result.continuationId());
            helper.assertTrue(restored.continuation(result.continuationId()).orElseThrow().completionReceipt(), "saved fact actions resume after the scene");
            helper.assertTrue(factCondition("private_count", "equals", "3").matches(fixture.context()), "replayed operation retains the first counter value");
            helper.assertValueEqual(fixture.player.totalExperience - before, 13, "later actions resume once");
            fixture.finishInterruptedOffer(result.continuationId());
            helper.assertValueEqual(fixture.player.totalExperience - before, 13, "completed continuation cannot repeat later actions");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void sharedFactOperationAppliesOnceAcrossOriginalPlayers(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.acceptQuest();
            var other = helper.makeMockServerPlayerInLevel();
            try {
                helper.assertTrue(VillagerQuestService.debugStartQuest(other, fixture.villager, QUEST, true).started(), "second player enters the original path");
                var action = factAction("public_count", "add", "3", "shared");
                helper.assertTrue(CampaignFactService.execute(fixture.context(), action).success(), "first player applies the authored world effect");
                var second = CampaignFactService.execute(VillagerInteractionService.createDialogueContext(helper.getLevel(), other, fixture.villager), action);
                helper.assertValueEqual(second.status(), com.jvn.villagerretaliation.action.ActionStatus.ALREADY_SATISFIED, "second original player receives the same world receipt");
                helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).facts().world().get("public_count").text(), "3", "one world operation cannot increment once per player");
                helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).facts().receipts().size(), 1, "shared operation retains its first actor and receipt");
            } finally {
                other.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void outcomeConditionsSeparateWorldDiscoveryParticipationAndCompletion(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            for (String scope : List.of("world", "discovered", "participated", "completed")) {
                helper.assertFalse(outcomeCondition("dragon", "defeated", scope).matches(fixture.context()), "unresolved outcome does not match " + scope);
            }
            helper.assertTrue(fixture.data.instances().isEmpty(), "reading outcome conditions does not enroll a player");
            fixture.resolve();
            helper.assertTrue(outcomeCondition("dragon", "defeated", "world").matches(fixture.context()), "world outcome is available before discovery");
            helper.assertFalse(outcomeCondition("dragon", "defeated", "discovered").matches(fixture.context()), "world observation invents no discovery");
            fixture.data.discover(fixture.content.definition(), fixture.player.getUUID(), "dragon");
            helper.assertTrue(outcomeCondition("dragon", "defeated", "discovered").matches(fixture.context()), "discovery unlocks personal dialogue");
            helper.assertFalse(outcomeCondition("dragon", "defeated", "completed").matches(fixture.context()), "discovery is not completion");
            fixture.data.enterMilestone(fixture.content.definition(), fixture.player.getUUID(), "dragon", 3L);
            helper.assertFalse(outcomeCondition("dragon", "defeated", "completed").matches(fixture.context()), "entering catch-up is not completion");
            var result = fixture.data.completeMilestone(fixture.content.definition(), fixture.player.getUUID(), "dragon",
                    CampaignInstance.Route.CATCH_UP, "defeated", UUID.randomUUID(), 4L);
            helper.assertValueEqual(result.status(), CampaignSavedData.Status.APPLIED, "authored catch-up completes personally");
            var restored = CampaignSavedData.load(fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess()), fixture.directory);
            fixture.server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, restored);
            helper.assertTrue(outcomeCondition("dragon", "defeated", "completed").matches(fixture.context()), "completion survives reload");
            helper.assertFalse(outcomeCondition("dragon", "defeated", "participated").matches(fixture.context()), "completed catch-up never awards participant dialogue");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void participantDialogueRequiresTheCommittedOutcomeAndPersonalCredit(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST, "", true)) {
            fixture.acceptQuest();
            fixture.resolve(Set.of(fixture.player.getUUID()));
            helper.assertTrue(outcomeCondition("dragon", "defeated", "participated").matches(fixture.context()), "participant sees the committed choice");
            helper.assertTrue(outcomeCondition("dragon", "defeated", "discovered").matches(fixture.context()), "participant knows the outcome");
            helper.assertFalse(outcomeCondition("dragon", "defeated", "completed").matches(fixture.context()), "participation does not finish the personal quest");
            for (String scope : List.of("world", "discovered", "participated", "completed")) {
                helper.assertFalse(outcomeCondition("dragon", "spared", scope).matches(fixture.context()), "another authored outcome cannot match " + scope);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void unavailableOutcomeConditionsCannotUnlockNegatedDialogue(GameTestHelper helper) throws IOException {
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            var condition = outcomeCondition("dragon", "defeated", "world");
            var negated = new com.jvn.villagerretaliation.dialogue.DialogueCondition.Not(condition);
            helper.assertTrue(negated.matches(fixture.context()), "a valid unresolved milestone may be negated");
            for (var invalid : List.of(outcomeCondition("missing", "defeated", "world"), outcomeCondition("dragon", "missing", "discovered"))) {
                helper.assertFalse(new com.jvn.villagerretaliation.dialogue.DialogueCondition.Not(invalid).matches(fixture.context()), "invalid references remain unavailable through negation");
            }
            var catalog = QuestContentCatalogs.current(fixture.server);
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(catalog, Map.of(), List.of()));
            var nested = new com.jvn.villagerretaliation.dialogue.DialogueCondition.AnyOf(List.of(negated, condition));
            for (var candidate : List.of(condition, negated, nested)) {
                helper.assertFalse(candidate.matches(fixture.context(), null), "missing content blocks nested outcome conditions");
                helper.assertValueEqual(com.jvn.villagerretaliation.dialogue.DialogueCondition.trace(fixture.context(), candidate).outcome(),
                        com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.UNKNOWN, "unavailability remains diagnostic");
            }
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(catalog, Map.of(CAMPAIGN, fixture.content), List.of()));
            helper.assertTrue(negated.matches(fixture.context()), "restoring content restores the condition");
            fixture.resolve();
            var saved = fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess());
            saved.putInt("DataVersion", CampaignSavedData.CURRENT_DATA_VERSION + 1);
            fixture.server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, CampaignSavedData.load(saved, fixture.directory));
            helper.assertFalse(condition.matches(fixture.context()), "unreadable campaign data cannot satisfy a condition");
            helper.assertFalse(negated.matches(fixture.context()), "unreadable campaign data cannot satisfy its negation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_entry")
    public static void outcomeConditionsStayUnavailableInSandboxAndLegacyWorlds(GameTestHelper helper) throws IOException {
        var storage = helper.getLevel().getServer().overworld().getDataStorage();
        var previousMode = CampaignWorldModeSavedData.get(helper.getLevel());
        try (Fixture fixture = new Fixture(helper, QUEST)) {
            fixture.resolve();
            var condition = outcomeCondition("dragon", "defeated", "world");
            helper.assertTrue(condition.matches(fixture.context()), "Campaign Mode sees its committed outcome");
            for (var mode : List.of(CampaignWorldMode.SANDBOX, CampaignWorldMode.LEGACY)) {
                var tag = new CompoundTag();
                tag.putInt("DataVersion", CampaignWorldModeSavedData.CURRENT_DATA_VERSION);
                tag.putString("Mode", mode.id());
                storage.set(CampaignWorldModeSavedData.DATA_NAME, CampaignWorldModeSavedData.load(tag, helper.getLevel().registryAccess()));
                helper.assertFalse(condition.matches(fixture.context()), "campaign conditions stay dormant in " + mode);
                helper.assertFalse(new com.jvn.villagerretaliation.dialogue.DialogueCondition.Not(condition).matches(fixture.context()), "world mode cannot unlock negated campaign content");
            }
        } finally {
            storage.set(CampaignWorldModeSavedData.DATA_NAME, previousMode);
        }
        helper.succeed();
    }

    private static com.jvn.villagerretaliation.dialogue.DialogueCondition outcomeCondition(String milestone, String outcome, String scope) {
        var raw = new JsonObject();
        raw.addProperty("type", "campaign_outcome");
        raw.addProperty("campaign", CAMPAIGN.toString());
        raw.addProperty("milestone", milestone);
        raw.addProperty("outcome", outcome);
        raw.addProperty("scope", scope);
        var array = new com.google.gson.JsonArray();
        array.add(raw);
        var root = new JsonObject();
        root.add("conditions", array);
        return com.jvn.villagerretaliation.dialogue.DialogueCondition.readList(CAMPAIGN, "test", root, QUEST).getFirst();
    }

    private static VillagerActionDefinition factAction(String fact, String mutation, String value, String operation) {
        var raw = new com.google.gson.JsonObject();
        raw.addProperty("type", "campaign_fact");
        raw.addProperty("fact", fact);
        raw.addProperty("mutation", mutation);
        raw.addProperty("value", value);
        raw.addProperty("operation_id", operation);
        if (fact.equals("public_count") || fact.equals("ruler")) raw.addProperty("milestone", "dragon");
        var array = new com.google.gson.JsonArray();
        array.add(raw);
        var entry = new com.google.gson.JsonObject();
        entry.add("actions", array);
        return VillagerActionDefinition.readList(CAMPAIGN, "test", entry, QUEST).getFirst();
    }

    private static com.jvn.villagerretaliation.dialogue.DialogueCondition factCondition(String fact, String comparison, String value) {
        var raw = new com.google.gson.JsonObject();
        raw.addProperty("type", "campaign_fact");
        raw.addProperty("campaign", CAMPAIGN.toString());
        raw.addProperty("fact", fact);
        raw.addProperty("comparison", comparison);
        raw.addProperty("value", value);
        var array = new com.google.gson.JsonArray();
        array.add(raw);
        var entry = new com.google.gson.JsonObject();
        entry.add("conditions", array);
        return com.jvn.villagerretaliation.dialogue.DialogueCondition.readList(CAMPAIGN, "test", entry, QUEST).getFirst();
    }

    private static VillagerActionDefinition outcomeAction(String outcome) {
        var raw = JsonParser.parseString("{\"actions\":[{\"type\":\"campaign_outcome\",\"milestone\":\"dragon\",\"outcome\":\""
                + outcome + "\",\"operation_id\":\"resolve_dragon\"}]}").getAsJsonObject();
        return VillagerActionDefinition.readList(CAMPAIGN, "test", raw, QUEST).getFirst();
    }

    private static VillagerActionDefinition experienceAction(int amount) {
        return VillagerActionDefinition.readList(CAMPAIGN, "test", JsonParser.parseString(
                "{\"actions\":[{\"type\":\"experience\",\"amount\":" + amount + "}]}").getAsJsonObject(), QUEST).getFirst();
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
            this(helper, catchUpQuest, milestoneStage, false);
        }

        private Fixture(GameTestHelper helper, ResourceLocation catchUpQuest, String milestoneStage, boolean choices) throws IOException {
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
                            choices ? Map.of("defeated", new CampaignDefinition.Outcome("defeated", "campaign.entry.defeated", ""),
                                    "spared", new CampaignDefinition.Outcome("spared", "campaign.entry.spared", ""))
                                    : Map.of("defeated", new CampaignDefinition.Outcome("defeated", "campaign.entry.defeated", "")),
                            new CampaignDefinition.CatchUp(catchUpQuest, "catch_up", ""), null, "")), Map.of(
                            "public_count", new CampaignDefinition.Fact("public_count", CampaignDefinition.FactScope.WORLD, CampaignDefinition.FactKind.COUNTER, ""),
                            "private_count", new CampaignDefinition.Fact("private_count", CampaignDefinition.FactScope.PLAYER, CampaignDefinition.FactKind.COUNTER, ""),
                            "knows_gate", new CampaignDefinition.Fact("knows_gate", CampaignDefinition.FactScope.PLAYER, CampaignDefinition.FactKind.TAG, ""),
                            "ruler", new CampaignDefinition.Fact("ruler", CampaignDefinition.FactScope.WORLD, CampaignDefinition.FactKind.VARIABLE, "")), CAMPAIGN);
            this.content = new CampaignContentCatalog.Entry(definition, QuestLocaleCatalog.empty());
            QuestContentCatalogs.installForTests(server, replacement, report);
            CampaignResources.installForTests(server, new CampaignContentCatalog(replacement, Map.of(CAMPAIGN, content), List.of()));
            server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, data);
        }



        private ResourceLocation installConfirmationTree() {
            return installConfirmationTree(false);
        }

        private ResourceLocation installConfirmationTree(boolean interrupted) {
            return installConfirmationTree(interrupted, false);
        }

        private ResourceLocation installConfirmationTree(boolean interrupted, boolean decision) {
            var treeId = ResourceLocation.parse("test:campaign_confirmation");
            var raw = JsonParser.parseString("""
                    {"id":"test:campaign_confirmation","entries":[{"id":"start","label":"Start","start":"opening"}],
                     "nodes":{"opening":{"lines":["Ready"],"responses":[{"id":"accept","label":"Accept","end":true,
                      "actions":[{"type":"experience","amount":11},{"type":"quest","quest":"test:entry_quest","action":"start","required":true}]}]}}}
                    """).getAsJsonObject();
            if (decision) {
                var actions = raw.getAsJsonObject("nodes").getAsJsonObject("opening").getAsJsonArray("responses")
                        .get(0).getAsJsonObject().getAsJsonArray("actions");
                actions.set(1, JsonParser.parseString("""
                        {"type":"campaign_outcome","quest":"test:entry_quest","milestone":"dragon","outcome":"spared","operation_id":"resolve_dragon"}
                        """));
            }
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


        private void confirm(CampaignQuestAcceptanceService.Request request) {
            CampaignQuestAcceptanceService.handle(player, new com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload(
                    request.id(), request.revision(), true));
        }

        private void acceptQuest() { confirm(confirmation()); }

        private CampaignQuestAcceptanceService.Request decision(VillagerActionDefinition action, Runnable work) {
            CampaignQuestAcceptanceService.beforeSelection(context(), "decision", Set.of(), Set.of(QUEST), List.of(action), () -> true, work);
            return java.util.Objects.requireNonNull(CampaignQuestAcceptanceService.pending(player), "decision confirmation");
        }

        private void startDecisionTree(ResourceLocation tree) {
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(player, false);
            if (!com.jvn.villagerretaliation.interaction.VillagerConversationService.start(player, villager)) {
                throw new IllegalStateException("Could not start decision conversation");
            }
            com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.startEntry(context(), tree, "start");
        }

        private com.jvn.villagerretaliation.scene.runtime.SceneContinuation interruptedDecision() {
            var tree = installConfirmationTree(true, true);
            startDecisionTree(tree);
            var option = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.activeOptions(context()).orElseThrow().getFirst();
            com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player, villager.getId(), option.id());
            confirm(java.util.Objects.requireNonNull(CampaignQuestAcceptanceService.pending(player)));
            return com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(player.serverLevel()).continuations().stream()
                    .filter(value -> player.getUUID().equals(value.playerId()) && !value.completionReceipt()).findFirst().orElseThrow();
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
