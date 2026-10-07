package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.PartyQuestService;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestCatalog;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.runtime.QuestStageActionService;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSaveMigrations;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneState;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignQuestAccessGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("test:access");
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:opening");
    private static final ResourceLocation CATCH_UP = ResourceLocation.parse("test:catch_up");
    private static final ResourceLocation LATER = ResourceLocation.parse("test:later");

    private CampaignQuestAccessGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_quest_access")
    public static void ordinaryQuestsStayAvailableInEveryWorldMode(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var ordinary = quest(QUEST, false).asQuestDefinition();
            for (var mode : CampaignWorldMode.values()) {
                helper.assertTrue(CampaignQuestAccess.contentIssue(mode, ordinary, disk.data, catalog()).isBlank(),
                        "ordinary quest content remains usable in " + mode);
            }
            helper.assertTrue(disk.data.instances().isEmpty(), "read-only checks never create campaign state");
            helper.assertTrue(PartyQuestService.isShareable(ordinary), "ordinary item quests remain party shareable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_access")
    public static void campaignQuestsRequireTheirPermanentWorldMode(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var quest = quest(QUEST, true).asQuestDefinition();
            for (var mode : CampaignWorldMode.values()) {
                helper.assertValueEqual(CampaignQuestAccess.contentIssue(mode, quest, disk.data, catalog()).isBlank(),
                        mode == CampaignWorldMode.CAMPAIGN, "campaign ownership is enforced in " + mode);
            }
            helper.assertFalse(PartyQuestService.isShareable(quest), "campaign progression cannot merge through ordinary party quests");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_access")
    public static void missingAndIncompatibleContentPreservesSavedProgress(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var content = catalog().campaigns().get(CAMPAIGN);
            var saved = disk.data.open(content, 1L);
            var quest = quest(QUEST, true).asQuestDefinition();
            var missing = new CampaignContentCatalog(QuestContentCatalog.empty(), Map.of(), List.of());
            helper.assertFalse(CampaignQuestAccess.contentIssue(CampaignWorldMode.CAMPAIGN, quest, disk.data, missing).isBlank(), "missing content pauses work");
            var old = content.definition();
            var changed = new CampaignDefinition(old.id(), 2, "changed", old.titleKey(), old.localizationPrefix(),
                    old.questBundles(), old.chapters(), old.milestones(), old.facts(), old.source());
            var incompatible = new CampaignContentCatalog(QuestContentCatalog.empty(), Map.of(CAMPAIGN,
                    new CampaignContentCatalog.Entry(changed, QuestLocaleCatalog.empty())), List.of());
            helper.assertFalse(CampaignQuestAccess.contentIssue(CampaignWorldMode.CAMPAIGN, quest, disk.data, incompatible).isBlank(), "incompatible content pauses work");
            helper.assertValueEqual(disk.data.instances().get(CAMPAIGN), saved, "dormancy preserves the original save");
            helper.assertTrue(CampaignQuestAccess.contentIssue(CampaignWorldMode.CAMPAIGN, quest, disk.data, catalog()).isBlank(), "restoring content resumes eligibility");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_access")
    public static void chapterOffersFollowPersonalProgressWithoutWritingDuringScans(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var catalog = catalog();
            var original = quest(QUEST, true).asQuestDefinition();
            var later = quest(LATER, true).asQuestDefinition();
            UUID player = UUID.randomUUID();
            helper.assertTrue(CampaignQuestAccess.availabilityIssue(original, player, disk.data, catalog).isBlank(), "opening chapter can be offered before an instance exists");
            helper.assertFalse(CampaignQuestAccess.availabilityIssue(later, player, disk.data, catalog).isBlank(), "later chapter starts locked");
            helper.assertTrue(disk.data.instances().isEmpty(), "availability scans cannot write campaign instances");
            var content = catalog.campaigns().get(CAMPAIGN);
            disk.data.open(content, 1L);
            disk.data.enterMilestone(content.definition(), player, "dragon", 2L);
            disk.data.commitWorldOutcome(content, "dragon", "defeated", UUID.randomUUID(), 0L,
                    CampaignInstance.Origin.PLAYER_DECISION, Set.of(player), 3L);
            helper.assertFalse(CampaignQuestAccess.availabilityIssue(later, player, disk.data, catalog).isBlank(), "world completion alone does not finish a personal chapter");
            disk.data.completeMilestone(content.definition(), player, "dragon", CampaignInstance.Route.ORIGINAL, "defeated", UUID.randomUUID(), 4L);
            disk.data.completeQuest(content.definition(), player, QUEST);
            helper.assertTrue(CampaignQuestAccess.availabilityIssue(later, player, disk.data, catalog).isBlank(), "completed personal chapter unlocks its sequel");
            helper.assertFalse(CampaignQuestAccess.availabilityIssue(later, UUID.randomUUID(), disk.data, catalog).isBlank(), "late players still need their own progression");
            helper.assertFalse(CampaignQuestAccess.availabilityIssue(original, player, disk.data, catalog).isBlank(), "completed campaign quests cannot replay rewards");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_access")
    public static void catchUpOffersRequireAResolvedMilestoneAndNoCompletedPersonalPath(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var catalog = catalog();
            var content = catalog.campaigns().get(CAMPAIGN);
            var quest = quest(CATCH_UP, true).asQuestDefinition();
            UUID player = UUID.randomUUID();
            disk.data.open(content, 1L);
            helper.assertFalse(CampaignQuestAccess.availabilityIssue(quest, player, disk.data, catalog).isBlank(), "catch-up cannot precede the shared event");
            disk.data.commitWorldOutcome(content, "dragon", "defeated", UUID.randomUUID(), 0L,
                    CampaignInstance.Origin.PLAYER_DECISION, Set.of(), 2L);
            helper.assertTrue(CampaignQuestAccess.availabilityIssue(quest, player, disk.data, catalog).isBlank(), "late arrival is offered the authored catch-up quest");
            helper.assertTrue(disk.data.instances().get(CAMPAIGN).players().isEmpty(), "offers do not invent discovery or participation");
            disk.data.enterMilestone(content.definition(), player, "dragon", 3L);
            disk.data.completeMilestone(content.definition(), player, "dragon", CampaignInstance.Route.CATCH_UP, "defeated", UUID.randomUUID(), 4L);
            helper.assertTrue(CampaignQuestAccess.availabilityIssue(quest, player, disk.data, catalog).isBlank(), "the rest of a catch-up quest remains recoverable after its milestone marker");
            disk.data.completeQuest(content.definition(), player, CATCH_UP);
            helper.assertFalse(CampaignQuestAccess.availabilityIssue(quest, player, disk.data, catalog).isBlank(), "completed personal quest is not offered twice");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_access")
    public static void dormantStageActionsCannotGiveRewardsOrConsumeTheirCheckpoint(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        var quest = quest(QUEST, true).asQuestDefinition();
        var progress = VillagerQuestSavedData.get(helper.getLevel()).getOrCreate(player.getUUID(), QUEST);
        var actions = VillagerActionDefinition.readList(QUEST, "fixture", JsonParser.parseString(
                "{\"actions\":[{\"type\":\"experience\",\"amount\":7}]}").getAsJsonObject());
        helper.assertValueEqual(actions.size(), 1, "reward fixture parses");
        int before = player.totalExperience;
        var result = QuestStageActionService.run(context, quest, progress, "entry", actions, Map.of(), () -> { });
        helper.assertValueEqual(result.status(), ActionStatus.UNAVAILABLE, "missing campaign blocks its effects");
        helper.assertValueEqual(player.totalExperience, before, "dormant quest gives no reward");
        helper.assertTrue(progress.stageActions().isEmpty(), "content restoration can retry the untouched action");
        progress.bindCampaign(CAMPAIGN);
        var altered = QuestStageActionService.run(context, quest(QUEST, false).asQuestDefinition(), progress,
                "entry", actions, Map.of(), () -> { });
        helper.assertValueEqual(altered.status(), ActionStatus.UNAVAILABLE, "removed ownership cannot bypass the effect gate");
        helper.assertValueEqual(player.totalExperience, before, "changed ownership cannot give the blocked reward");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_access")
    public static void missingCampaignQuestPausesSceneExecutionAcrossReloads(GameTestHelper helper) {
        var raw = JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"test:campaign_scene","definition_version":1,
                 "ownership":"player","entry_step":"finish","timeout_ticks":10,
                 "steps":[{"id":"finish","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject();
        var scene = SceneCompiler.compile(SceneParser.parse(CAMPAIGN, raw).resource()).scene();
        helper.assertTrue(scene != null, "scene fixture compiles");
        UUID player = UUID.randomUUID();
        var instance = new SceneInstance(UUID.randomUUID(), scene, "fixture", new SceneOwner(SceneResource.OwnershipMode.PLAYER,
                player, null, null, ""), UUID.randomUUID(), Set.of(player), Map.of(), 1L);
        instance.linkQuest(QUEST);
        instance.bindCampaign(CAMPAIGN);
        var data = SceneSavedData.get(helper.getLevel());
        SceneStepEngine.process(helper.getLevel().getServer(), data, instance, scene, 3L);
        helper.assertTrue(instance.campaignPaused(), "missing owner pauses before scene actions");
        var restored = SceneInstance.load(instance.save());
        SceneStepEngine.process(helper.getLevel().getServer(), data, restored, scene, 103L);
        helper.assertValueEqual(restored.state(), SceneState.PENDING, "missing content cannot time out or complete the scene");
        helper.assertTrue(restored.stepRecords().isEmpty(), "no effects prepared while dormant");
        helper.assertValueEqual(restored.owningCampaignId(), CAMPAIGN, "campaign ownership survives a missing quest definition");
        var server = helper.getLevel().getServer();
        var original = QuestContentCatalogs.current(server);
        var report = QuestContentCatalogs.loadReport(server);
        var fixture = quest(QUEST, true);
        var compiled = new java.util.LinkedHashMap<ResourceLocation, CompiledQuest>();
        original.compiledQuests().forEach(value -> compiled.put(value.id(), value));
        compiled.put(QUEST, fixture);
        var definitions = new java.util.LinkedHashMap<>(original.quests());
        definitions.put(QUEST, fixture.asQuestDefinition());
        var replacement = new QuestContentCatalog(original.generation(), new CompiledQuestCatalog(compiled), original.dialogueCatalog(),
                definitions, original.objectiveEventQuestIds(), original.factQuestIds(), original.memoryEventQuestIds(),
                original.exclusiveGroupQuestIds(), original.triggerEventQuestIds(), original.scenes(), original.encounters(), original.pools(),
                original.bundles(), original.localization(), original.rewards());
        QuestContentCatalogs.installForTests(server, replacement, report);
        CampaignResources.installForTests(server, new CampaignContentCatalog(replacement, catalog().campaigns(), List.of()));
        try {
            for (long time = 203L; time < 208L && !restored.state().terminal(); time++) {
                SceneStepEngine.process(server, data, restored, scene, time);
            }
            helper.assertFalse(restored.campaignPaused(), "restored content resumes the saved scene");
            helper.assertValueEqual(restored.state(), SceneState.COMPLETED, "restored scene finishes without losing its timeout to dormancy");
            helper.assertValueEqual(restored.timeoutStartGameTime(), 201L, "dormant time does not consume the overall scene deadline");
            helper.assertFalse(restored.resumeCampaign(303L), "resuming twice cannot extend the deadline twice");
        } finally {
            QuestContentCatalogs.installForTests(server, original, report);
            CampaignResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_access")
    public static void legacyScenesAndContinuationsKeepTheirOwnershipBoundaries(GameTestHelper helper) {
        var old = new CompoundTag();
        old.putInt("DataVersion", 8);
        var list = new ListTag();
        list.add(new CompoundTag());
        old.put("Instances", list);
        var migrated = SceneSaveMigrations.migrate(old, SceneSavedData.CURRENT_DATA_VERSION).data();
        helper.assertValueEqual(migrated.getList("Instances", Tag.TAG_COMPOUND).getCompound(0).getLong("CampaignPausedAt"), -1L,
                "legacy scenes begin without a campaign pause");
        helper.assertFalse(old.getList("Instances", Tag.TAG_COMPOUND).getCompound(0).contains("CampaignPausedAt"), "migration leaves source data intact");
        var continuation = new SceneContinuation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                QUEST, UUID.randomUUID(), "fixture", List.of(), 0, Map.of());
        continuation.bindCampaign(CAMPAIGN);
        var restored = SceneContinuation.load(continuation.save());
        helper.assertValueEqual(restored.campaignId(), CAMPAIGN, "continuations retain their campaign even when content disappears");
        helper.assertFalse(CampaignQuestAccess.contentIssue(helper.getLevel().getServer(), QUEST, restored.campaignId()).isBlank(), "missing quest cannot resume a saved campaign continuation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_access")
    public static void savedCampaignOwnershipCannotDisappearThroughAQuestUpdate(GameTestHelper helper) {
        var data = new VillagerQuestSavedData();
        UUID player = UUID.randomUUID();
        data.getOrCreate(player, QUEST).bindCampaign(CAMPAIGN);
        var restored = VillagerQuestSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        var progress = restored.get(player, QUEST);
        helper.assertValueEqual(progress.campaignId(), CAMPAIGN.toString(), "quest progress retains its owning campaign");
        helper.assertFalse(CampaignQuestAccess.contentIssue(helper.getLevel(), quest(QUEST, false).asQuestDefinition(), progress).isBlank(),
                "removing metadata cannot convert saved campaign progress into ordinary quest progress");
        helper.assertFalse(CampaignQuestAccess.contentIssue(helper.getLevel(), null, progress).isBlank(),
                "missing quest definitions preserve campaign dormancy");
        helper.assertTrue(CampaignQuestAccess.contentIssue(helper.getLevel(), quest(QUEST, false).asQuestDefinition(),
                new VillagerQuestSavedData.QuestProgress()).isBlank(), "ordinary existing saves remain ordinary");
        helper.succeed();
    }

    private static CompiledQuest quest(ResourceLocation id, boolean campaign) {
        var raw = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"test:opening","metadata":{},
                 "localization_prefix":"quest.access.","provider":{"type":"villagerretaliation:villager"},
                 "entry_stage":"started","stages":[{"id":"started","objectives":[{"id":"paper","type":"item_check","item":"minecraft:paper","count":1}]}]}
                """).getAsJsonObject();
        raw.addProperty("id", id.toString());
        if (campaign) raw.getAsJsonObject("metadata").addProperty("campaign", CAMPAIGN.toString());
        var envelope = QuestResourceEnvelope.read(id, raw).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }

    private static CampaignContentCatalog catalog() {
        var definition = new CampaignDefinition(CAMPAIGN, 1, "fixture", "campaign.access.title", "campaign.access.",
                List.of(QUEST, CATCH_UP, LATER), Map.of(
                        "opening", new CampaignDefinition.Chapter("opening", "campaign.access.opening", List.of(), List.of(QUEST), ""),
                        "later", new CampaignDefinition.Chapter("later", "campaign.access.later",
                                List.of(new CampaignDefinition.Prerequisite("opening", "", "", "")), List.of(LATER), "")),
                Map.of("dragon", new CampaignDefinition.Milestone("dragon", "opening", QUEST, true,
                        Map.of("defeated", new CampaignDefinition.Outcome("defeated", "campaign.access.defeated", "")),
                        new CampaignDefinition.CatchUp(CATCH_UP, "started", ""), null, "")), Map.of(), CAMPAIGN);
        return new CampaignContentCatalog(QuestContentCatalog.empty(), Map.of(CAMPAIGN,
                new CampaignContentCatalog.Entry(definition, QuestLocaleCatalog.empty())), List.of());
    }

    private static final class Disk implements AutoCloseable {
        private final Path root = Files.createTempDirectory("campaign-access-");
        private final CampaignSavedData data = CampaignSavedData.load(new CompoundTag(), root);

        private Disk() throws IOException {
        }

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(root.resolve(CampaignSavedData.DATA_NAME + ".dat"));
            Files.deleteIfExists(root);
        }
    }
}
