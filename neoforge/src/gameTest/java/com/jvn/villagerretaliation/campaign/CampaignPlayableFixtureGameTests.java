package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.QuestContentLoadReport;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignPlayableFixtureGameTests {
    private static final ResourceLocation CAMPAIGN = id("reconciliation");

    private CampaignPlayableFixtureGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_playable_fixture", timeoutTicks = 200)
    public static void optInFixtureCompilesWithoutEnteringTheProductionCatalog(GameTestHelper helper) throws IOException {
        helper.assertFalse(QuestContentCatalogs.current(helper.getLevel().getServer()).quests().containsKey(id("decision")), "fixture is absent from production offers");
        try (Fixture fixture = new Fixture(helper)) {
            helper.assertValueEqual(fixture.catalog.quests().size(), 4, "both original and alternate quest bundles compile");
            helper.assertFalse(fixture.catalog.dialogueCatalog().trees().isEmpty(), "localized fixture conversations compile");
            helper.assertTrue(CampaignProgression.chapter(fixture.content.definition(), null, fixture.player().getUUID(), "decision").available(), "opening chapter is available");
            helper.assertFalse(CampaignProgression.chapter(fixture.content.definition(), null, fixture.player().getUUID(), "dragon").available(), "dragon chapter requires personal progress");
        }
        helper.assertFalse(QuestContentCatalogs.current(helper.getLevel().getServer()).quests().containsKey(id("decision")), "fixture leaves production catalog unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_playable_fixture", timeoutTicks = 200)
    public static void bothDecisionsHavePlayableCatchUpDialogueWithoutOriginalRewards(GameTestHelper helper) throws IOException {
        for (int choice = 0; choice < 2; choice++) {
            try (Fixture fixture = new Fixture(helper)) {
                var participant = fixture.player();
                fixture.finishDecision(participant, choice);
                var shared = fixture.data.instances().get(CAMPAIGN).outcomes().get("decision");
                helper.assertValueEqual(shared.outcome(), choice == 0 ? "admitted" : "refused", "selected response commits its named outcome");
                helper.assertValueEqual(participant.totalExperience, 40, "original completion grants its authored reward");
                var late = fixture.player();
                fixture.finishCatchUp(late, "decision");
                helper.assertValueEqual(late.totalExperience, 5, "late player receives only the personal catch-up reward");
                var personal = fixture.data.instances().get(CAMPAIGN).players().get(late.getUUID());
                helper.assertTrue(personal.milestones().get("decision").completed(), "catch-up completes the personal milestone");
                helper.assertTrue(personal.participation().isEmpty(), "catch-up does not invent participation");
                helper.assertTrue(CampaignProgression.chapter(fixture.content.definition(), fixture.data.instances().get(CAMPAIGN), late.getUUID(), "dragon").available(), "catch-up unlocks the next chapter");
                helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).outcomes().get("decision"), shared, "catch-up never rewrites the shared decision");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_playable_fixture", timeoutTicks = 200)
    public static void dragonFixtureReconcilesActiveAbsentAndLatePlayersAfterReload(GameTestHelper helper) throws IOException {
        var end = helper.getLevel().getServer().getLevel(Level.END);
        var previousDragon = end.getDragonFight();
        try (Fixture fixture = new Fixture(helper)) {
            end.setDragonFight(new EndDragonFight(end, 0L, EndDragonFight.Data.DEFAULT));
            var active = fixture.player();
            fixture.finishDecision(active, 0);
            fixture.select(active, "dragon", "watch", "offer", 0);
            helper.assertValueEqual(fixture.progress(active, "dragon").currentStage(), "watch", "active player undertakes the original dragon objective");
            var absent = fixture.player();
            fixture.finishCatchUp(absent, "decision");
            VillagerConversationService.endForPlayer(absent, false);
            end.setDragonFight(new EndDragonFight(end, 0L, new EndDragonFight.Data(false, false, true, false,
                    Optional.empty(), Optional.empty(), Optional.empty())));
            CampaignWorldEventService.reconcile(fixture.data, fixture.content, end, 10L);
            var committed = fixture.data.instances().get(CAMPAIGN).outcomes().get("dragon");
            fixture.reload();
            CampaignWorldEventService.reconcile(fixture.data, fixture.content, end, 11L);
            fixture.finishCatchUp(active, "dragon");
            fixture.finishCatchUp(absent, "dragon");
            var late = fixture.player();
            fixture.finishCatchUp(late, "decision");
            fixture.finishCatchUp(late, "dragon");
            CampaignWorldEventService.reconcile(fixture.data, fixture.content, end, 12L);
            helper.assertValueEqual(fixture.data.instances().get(CAMPAIGN).outcomes().get("dragon"), committed, "repeated defeat and reconciliation preserve the original receipt");
            helper.assertValueEqual(active.totalExperience, 45, "active player gets only the dragon catch-up reward after an observed defeat");
            helper.assertValueEqual(absent.totalExperience, 10, "absent player gets two personal catch-up rewards");
            helper.assertValueEqual(late.totalExperience, 10, "late player catches up without shared rewards");
            for (var player : List.of(active, absent, late)) {
                var personal = fixture.data.instances().get(CAMPAIGN).players().get(player.getUUID());
                helper.assertTrue(personal.milestones().get("dragon").completed(), "every player can complete the dragon chapter");
                helper.assertFalse(personal.participation().contains("dragon"), "observed world state does not invent dragon participation");
            }
        } finally {
            end.setDragonFight(previousDragon);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_playable_fixture", timeoutTicks = 200)
    public static void earlyDragonDefeatAllowsTheWholeFixtureToBePlayedLater(GameTestHelper helper) throws IOException {
        var end = helper.getLevel().getServer().getLevel(Level.END);
        var previousDragon = end.getDragonFight();
        try (Fixture fixture = new Fixture(helper)) {
            end.setDragonFight(new EndDragonFight(end, 0L, new EndDragonFight.Data(false, false, true, false,
                    Optional.empty(), Optional.empty(), Optional.empty())));
            CampaignWorldEventService.reconcile(fixture.data, fixture.content, end, 1L);
            helper.assertTrue(fixture.data.instances().get(CAMPAIGN).players().isEmpty(), "early defeat does not create personal progress");
            var player = fixture.player();
            fixture.finishDecision(player, 1);
            fixture.finishCatchUp(player, "dragon");
            helper.assertValueEqual(player.totalExperience, 45, "early dragon defeat does not award the original dragon reward");
            helper.assertTrue(CampaignProgression.chapter(fixture.content.definition(), fixture.data.instances().get(CAMPAIGN), player.getUUID(), "dragon").completed(), "the later player can finish the campaign fixture");
        } finally {
            end.setDragonFight(previousDragon);
        }
        helper.succeed();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }

    private static JsonObject resource(String path) throws IOException {
        try (var stream = CampaignPlayableFixtureGameTests.class.getResourceAsStream("/campaign_fixture/data/campaign_fixture/" + path)) {
            if (stream == null) throw new IOException("Missing campaign fixture resource " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final QuestContentCatalog previous;
        private final QuestContentLoadReport report;
        private final CampaignSavedData previousData;
        private final VillagerQuestSavedData previousQuests;
        private final QuestContentCatalog catalog;
        private final CampaignContentCatalog.Entry content;
        private final Path directory;
        private CampaignSavedData data;
        private final Villager villager;
        private final List<ServerPlayer> players = new ArrayList<>();

        private Fixture(GameTestHelper helper) throws IOException {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.previous = QuestContentCatalogs.current(server);
            this.report = QuestContentCatalogs.loadReport(server);
            this.previousData = CampaignSavedData.get(helper.getLevel());
            this.previousQuests = VillagerQuestSavedData.get(helper.getLevel());
            List<QuestBundleTransactions.RawResource> resources = new ArrayList<>();
            for (String quest : List.of("decision", "dragon", "decision_catch_up", "dragon_catch_up")) {
                for (String file : List.of("quest.json", "locales/en_us.json")) {
                    String path = "quests/campaign_fixture/" + quest + "/" + file;
                    resources.add(QuestBundleTransactions.RawResource.valid(0, "campaign_fixture", id(path), resource(path)));
                }
            }
            var bundles = QuestBundleTransactions.compile(resources, QuestBundleTransactions.CompatibilityRules.empty());
            helper.assertTrue(bundles.diagnostics().isEmpty(), "fixture bundle diagnostics: " + bundles.diagnostics());
            var quests = VillagerQuestResources.snapshotForCatalog(server, bundles);
            this.catalog = new QuestContentCatalog(previous.generation() + 1L, quests.compiledCatalog(), quests.dialogueCatalog(), quests.quests(),
                    quests.objectiveEventQuestIds(), quests.factQuestIds(), quests.memoryEventQuestIds(), quests.exclusiveGroupQuestIds(),
                    quests.triggerEventQuestIds(), Map.of(), Map.of(), List.of(), bundles.bundles(), bundles.localization(), QuestRewardCatalog.fromBundles(bundles.bundles()));
            var locale = QuestLocaleCatalog.read(resource("campaigns/reconciliation/locales/en_us.json"));
            helper.assertTrue(locale.valid(), "campaign locale compiles");
            var campaigns = CampaignContentCatalog.compile(catalog, List.of(new CampaignContentCatalog.Source(id("campaigns/reconciliation/campaign.json"),
                    "campaign_fixture", resource("campaigns/reconciliation/campaign.json"), QuestLocaleCatalog.empty().overlay("en_us", locale.messages()), List.of())));
            helper.assertTrue(campaigns.diagnostics().isEmpty(), "fixture campaign diagnostics: " + campaigns.diagnostics());
            this.content = campaigns.campaigns().get(CAMPAIGN);
            this.directory = Files.createTempDirectory("campaign-playable-");
            this.data = CampaignSavedData.load(new CompoundTag(), directory);
            this.villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            QuestContentCatalogs.installForTests(server, catalog, report);
            CampaignResources.installForTests(server, campaigns);
            server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, data);
            server.overworld().getDataStorage().set("villagerretaliation_quests", new VillagerQuestSavedData());
        }

        private ServerPlayer player() {
            var player = helper.makeMockServerPlayerInLevel();
            player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
            players.add(player);
            return player;
        }

        private String select(ServerPlayer player, String quest, String stage, String slot, int index) {
            players.forEach(value -> VillagerConversationService.endForPlayer(value, false));
            helper.assertTrue(VillagerConversationService.start(player, villager), "fixture conversation starts");
            var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, villager);
            var binding = catalog.dialogueCatalog().bindings(id(quest)).stream()
                    .filter(value -> value.stageId().equals(stage) && value.slot().equals(slot)).findFirst().orElseThrow();
            helper.assertTrue(DialogueTreeService.startEntry(context, binding.treeId(), binding.entryId()).isPresent(), "fixture dialogue opens: " + quest + "/" + stage + "/" + slot);
            var options = DialogueTreeService.activeOptions(context).orElseThrow();
            if (stage.equals("catch_up") && slot.equals("reminder")) helper.assertValueEqual(options.size(), 1, "only the committed catch-up outcome is offered");
            VillagerDialogueRequestHandler.handle(player, villager.getId(), options.get(index).id());
            var pending = CampaignQuestAcceptanceService.pending(player);
            if (pending != null) CampaignQuestAcceptanceService.handle(player, new CampaignQuestConfirmationRequestPayload(pending.id(), pending.revision(), true));
            return options.get(index).id();
        }

        private void finishDecision(ServerPlayer player, int index) {
            select(player, "decision", "choose", "offer", 0);
            helper.assertTrue(progress(player, "decision") != null, "acceptance creates personal quest progress");
            select(player, "decision", "choose", "reminder", index);
            select(player, "decision", "report", "turn_in", 0);
            helper.assertValueEqual(progress(player, "decision").completionCount(), 1, "original dialogue completes the quest");
        }

        private void finishCatchUp(ServerPlayer player, String milestone) {
            String quest = milestone + "_catch_up";
            select(player, quest, "catch_up", "offer", 0);
            select(player, quest, "catch_up", "reminder", 0);
            String completedRequest = select(player, quest, "catch_up_report", "turn_in", 0);
            int rewarded = player.totalExperience;
            VillagerDialogueRequestHandler.handle(player, villager.getId(), completedRequest);
            helper.assertValueEqual(player.totalExperience, rewarded, "repeating the completed response cannot duplicate catch-up rewards");
            helper.assertValueEqual(progress(player, quest).completionCount(), 1, "catch-up dialogue completes the quest once");
        }

        private VillagerQuestSavedData.QuestProgress progress(ServerPlayer player, String quest) {
            return VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), id(quest));
        }

        private void reload() {
            data = CampaignSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), directory);
            server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, data);
            var quests = VillagerQuestSavedData.get(helper.getLevel());
            server.overworld().getDataStorage().set("villagerretaliation_quests", VillagerQuestSavedData.load(
                    quests.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess()));
        }

        @Override
        public void close() throws IOException {
            for (var player : players) {
                VillagerConversationService.endForPlayer(player, false);
                CampaignQuestAcceptanceService.onLogout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
            }
            server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, previousData);
            server.overworld().getDataStorage().set("villagerretaliation_quests", previousQuests);
            previousQuests.setDirty();
            QuestContentCatalogs.installForTests(server, previous, report);
            CampaignResources.clearCache();
            com.jvn.villagerretaliation.dialogue.DialogueTreeResources.clearCache();
            DialogueTreeService.clearRuntimeState();
            Files.deleteIfExists(directory.resolve(CampaignSavedData.DATA_NAME + ".dat"));
            Files.deleteIfExists(directory);
        }
    }
}
