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

    private static CompiledQuest quest(ResourceLocation id) {
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
        var envelope = QuestResourceEnvelope.read(id, raw).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }

    private static final class Fixture implements AutoCloseable {
        private final MinecraftServer server;
        private final QuestContentCatalog previous;
        private final QuestContentLoadReport report;
        private final CampaignSavedData previousData;
        private final Path directory;
        private final CampaignSavedData data;
        private final CampaignContentCatalog.Entry content;
        private final ServerPlayer player;
        private final Villager villager;

        private Fixture(GameTestHelper helper, ResourceLocation catchUpQuest) throws IOException {
            this.server = helper.getLevel().getServer();
            this.previous = QuestContentCatalogs.current(server);
            this.report = QuestContentCatalogs.loadReport(server);
            this.previousData = CampaignSavedData.get(helper.getLevel());
            this.directory = Files.createTempDirectory("campaign-entry-");
            this.data = CampaignSavedData.load(new CompoundTag(), directory);
            this.player = helper.makeMockServerPlayerInLevel();
            this.villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            var compiled = new LinkedHashMap<ResourceLocation, CompiledQuest>();
            previous.compiledQuests().forEach(value -> compiled.put(value.id(), value));
            var definitions = new LinkedHashMap<>(previous.quests());
            for (var id : List.of(QUEST, ALTERNATE)) {
                var quest = quest(id);
                compiled.put(id, quest);
                definitions.put(id, quest.asQuestDefinition());
            }
            var replacement = new QuestContentCatalog(previous.generation(), new CompiledQuestCatalog(compiled), previous.dialogueCatalog(),
                    definitions, previous.objectiveEventQuestIds(), previous.factQuestIds(), previous.memoryEventQuestIds(),
                    previous.exclusiveGroupQuestIds(), previous.triggerEventQuestIds(), previous.scenes(), previous.encounters(), previous.pools(),
                    previous.bundles(), previous.localization(), previous.rewards());
            var definition = new CampaignDefinition(CAMPAIGN, 1, "fixture", "campaign.entry.title", "campaign.entry.",
                    List.of(QUEST, ALTERNATE), Map.of("opening", new CampaignDefinition.Chapter("opening", "campaign.entry.chapter", List.of(), List.of(QUEST), "")),
                    Map.of("dragon", new CampaignDefinition.Milestone("dragon", "opening", QUEST, true,
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
            data.open(content, 1L);
            data.commitWorldOutcome(content, "dragon", "defeated", UUID.randomUUID(), 0L,
                    CampaignInstance.Origin.PLAYER_DECISION, Set.of(), 2L);
        }

        @Override
        public void close() throws IOException {
            server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, previousData);
            QuestContentCatalogs.installForTests(server, previous, report);
            CampaignResources.clearCache();
            VillagerQuestSavedData.get(server.overworld()).remove(player.getUUID(), QUEST);
            VillagerQuestSavedData.get(server.overworld()).remove(player.getUUID(), ALTERNATE);
            Files.deleteIfExists(directory.resolve(CampaignSavedData.DATA_NAME + ".dat"));
            Files.deleteIfExists(directory);
        }
    }
}
