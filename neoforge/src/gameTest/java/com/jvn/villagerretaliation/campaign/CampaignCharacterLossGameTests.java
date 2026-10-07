package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.campaign.content.CampaignSchema;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignCharacterLossGameTests {
    private static final ResourceLocation CAMPAIGN = id("losses");
    private static final ResourceLocation CATCH_UP = id("losses_catch_up");

    private CampaignCharacterLossGameTests() { }

    @GameTest(template = "empty", batch = "campaign_character_loss")
    public static void alternateLossPoliciesRequireAnAuthoredReachableCatchUp(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var root = fixture.base.raw();
            helper.assertTrue(CampaignSchema.validate(CAMPAIGN, root).isEmpty(), "alternate loss fixture matches the exported schema");
            for (int mutation = 0; mutation < 6; mutation++) {
                var broken = root.deepCopy();
                var milestone = broken.getAsJsonArray("milestones").get(0).getAsJsonObject();
                if (mutation == 0) milestone.getAsJsonObject("observe").remove("character");
                if (mutation == 1) milestone.getAsJsonObject("observe").addProperty("character", "missing");
                if (mutation == 2) milestone.getAsJsonObject("observe").addProperty("event", "minecraft:ender_dragon_defeated");
                if (mutation == 3) milestone.remove("observe");
                if (mutation == 4) milestone.addProperty("irreversible", false);
                if (mutation == 5) milestone.remove("catch_up");
                var parsed = CampaignDefinitionParser.parse(CAMPAIGN, broken);
                helper.assertFalse(parsed.valid(), "incomplete or unrelated loss policy is rejected: " + mutation);
                helper.assertTrue(parsed.diagnostics().stream().allMatch(value -> !value.jsonPointer().isBlank()), "loss diagnostics retain source pointers");
            }
            var originalProvider = root.deepCopy();
            originalProvider.getAsJsonArray("milestones").get(0).getAsJsonObject().getAsJsonObject("catch_up").addProperty("quest", CAMPAIGN.toString());
            originalProvider.getAsJsonArray("milestones").get(0).getAsJsonObject().getAsJsonObject("catch_up").addProperty("stage", "report");
            var parsed = CampaignDefinitionParser.parse(CAMPAIGN, originalProvider);
            helper.assertTrue(parsed.valid(), "structural references compile before quest ownership checks");
            helper.assertTrue(CampaignDefinitionParser.validateQuestReferences(parsed.definition(), fixture.base.catalog).stream()
                    .anyMatch(value -> value.message().contains("lost character")), "catch-up cannot depend on the lost provider");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_loss")
    public static void unboundAndUnloadedCharactersNeverTriggerTheLossPath(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.observe();
            helper.assertTrue(fixture.instance().outcomes().isEmpty(), "observation never casts an unbound role");
            var mentor = fixture.mentor();
            var saved = mentor.saveWithoutId(new CompoundTag());
            mentor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.reload();
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            var unrelated = fixture.villager();
            unrelated.discard();
            fixture.observe();
            helper.assertTrue(fixture.instance().outcomes().isEmpty(), "unloading or losing another villager does not resolve the role");
            helper.assertFalse(CampaignCharacterSavedData.get(helper.getLevel()).forEntity(mentor.getUUID()).lost(), "unloaded identity remains alive in the ledger");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "observation does not load chunks");
            var restored = fixture.restore(saved);
            helper.assertValueEqual(fixture.base.resolve().entity(), restored, "the original UUID resumes the role");
            fixture.observe();
            helper.assertTrue(fixture.instance().outcomes().isEmpty(), "recovery does not fabricate a death");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_loss")
    public static void deathAndPermanentRemovalCommitOnceWithoutParticipants(GameTestHelper helper) throws IOException {
        for (var reason : List.of(Entity.RemovalReason.KILLED, Entity.RemovalReason.DISCARDED)) {
            try (var fixture = new Fixture(helper)) {
                var mentor = fixture.mentor();
                helper.assertFalse(CampaignCharacterService.protects(mentor), "alternate path does not apply essential-character protection");
                mentor.remove(reason);
                fixture.reload();
                helper.assertTrue(CampaignCharacterSavedData.get(helper.getLevel()).forEntity(mentor.getUUID()).lost(), "known loss is durable before observation");
                fixture.observe();
                var first = fixture.instance().outcomes().get("mentor_loss");
                helper.assertTrue(first != null && first.origin() == CampaignInstance.Origin.WORLD_OBSERVATION, "authoritative loss commits the authored world outcome");
                helper.assertValueEqual(first.outcome(), "lost", "observed outcome retains its stable id");
                helper.assertTrue(fixture.instance().players().isEmpty(), "world observation does not award participation or personal progress");
                fixture.reload(); fixture.observe(); fixture.observe();
                helper.assertValueEqual(fixture.instance().outcomes().get("mentor_loss"), first, "reloads and duplicate observations preserve the receipt");
                helper.assertValueEqual(fixture.instance().worldRevision(), 1L, "shared event advances once");
                helper.assertTrue(fixture.base.resolve().diagnostic().contains("alternate story path"), "lost role explains its continuation policy");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_loss")
    public static void originalRecoveryBeforeObservationDoesNotCommitAStaleLoss(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor();
            var saved = mentor.saveWithoutId(new CompoundTag());
            mentor.discard();
            var restored = fixture.restore(saved);
            fixture.observe();
            helper.assertTrue(fixture.instance().outcomes().isEmpty(), "live original identity prevents stale loss from committing");
            helper.assertValueEqual(fixture.base.resolve().entity(), restored, "recovery durably clears the loss flag");
            fixture.reload(); fixture.observe();
            helper.assertTrue(fixture.instance().outcomes().isEmpty(), "restored role remains on its original path");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_loss")
    public static void recoveryAfterACommittedLossNeverRewritesTheSharedOutcome(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor();
            var saved = mentor.saveWithoutId(new CompoundTag());
            mentor.discard(); fixture.observe();
            var committed = fixture.instance().outcomes().get("mentor_loss");
            fixture.restore(saved);
            helper.assertTrue(fixture.base.resolve().available(), "an explicitly restored original entity can be recovered");
            fixture.reload(); fixture.observe();
            helper.assertValueEqual(fixture.instance().outcomes().get("mentor_loss"), committed, "restoring an entity does not undo irreversible history");
            helper.assertTrue(fixture.instance().players().isEmpty(), "recovery grants no participation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_loss")
    public static void missingContentAndOtherWorldModesPreservePendingLosses(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.mentor().discard();
            var catalog = CampaignResources.current(fixture.base.server);
            CampaignResources.installForTests(fixture.base.server, new CampaignContentCatalog(fixture.base.catalog, Map.of(), List.of()));
            fixture.observe();
            helper.assertTrue(fixture.instance().outcomes().isEmpty(), "missing content keeps the saved loss dormant");
            CampaignResources.installForTests(fixture.base.server, catalog);
            for (String mode : List.of("sandbox", "legacy")) {
                fixture.base.mode(mode); fixture.observe();
                helper.assertTrue(fixture.instance().outcomes().isEmpty(), "ordinary worlds do not commit campaign observations");
            }
            fixture.base.mode("campaign"); fixture.reload(); fixture.observe();
            helper.assertTrue(fixture.instance().outcomes().containsKey("mentor_loss"), "restored content and Campaign Mode resume the known loss");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_loss")
    public static void corruptBindingsAndIncompatibleDefinitionsCannotInventLossOutcomes(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.mentor().discard();
            var storage = fixture.base.server.overworld().getDataStorage();
            var data = CampaignCharacterSavedData.get(helper.getLevel());
            var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            for (int mutation = 0; mutation < 3; mutation++) {
                var broken = saved.copy();
                if (mutation == 0) broken.putInt("DataVersion", 99);
                if (mutation == 1) broken.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0).putUUID("Instance", UUID.randomUUID());
                if (mutation == 2) broken.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0).putString("LossPolicy", "DOWNED");
                storage.set(CampaignCharacterSavedData.DATA_NAME, CampaignCharacterSavedData.load(broken, fixture.base.disk.root));
                fixture.observe();
                helper.assertTrue(fixture.instance().outcomes().isEmpty(), "invalid or foreign binding cannot resolve story history");
            }
            storage.set(CampaignCharacterSavedData.DATA_NAME, data);
            var root = fixture.base.raw(); root.addProperty("revision", 2); fixture.base.install(root);
            fixture.observe();
            helper.assertTrue(fixture.instance().outcomes().isEmpty(), "changed definitions require explicit repair");
            fixture.base.install(fixture.base.raw()); fixture.observe();
            helper.assertTrue(fixture.instance().outcomes().containsKey("mentor_loss"), "matching restored saves permit the authored event");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_loss")
    public static void failedOutcomeWritesKeepTheSavedLossAvailableForRetry(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.mentor().discard();
            var original = fixture.data();
            var blocked = fixture.base.disk.root.resolve("blocked");
            Files.writeString(blocked, "not a directory");
            var storage = fixture.base.server.overworld().getDataStorage();
            storage.set(CampaignSavedData.DATA_NAME, CampaignSavedData.load(original.save(new CompoundTag(), helper.getLevel().registryAccess()), blocked));
            try {
                fixture.observe();
                helper.assertTrue(fixture.instance().outcomes().isEmpty(), "failed write never publishes an outcome");
                helper.assertTrue(CampaignCharacterSavedData.get(helper.getLevel()).bindings().values().iterator().next().lost(), "durable source event remains available");
            } finally { storage.set(CampaignSavedData.DATA_NAME, original); Files.delete(blocked); }
            fixture.observe(); fixture.reload();
            helper.assertTrue(fixture.instance().outcomes().containsKey("mentor_loss"), "retry commits and persists the same observed loss");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_loss")
    public static void ownedSpawnLossUsesItsReceiptWithoutRespawningTheCharacter(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var root = fixture.base.raw();
            root.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("source", "owned_spawn");
            fixture.base.install(root);
            helper.getLevel().setBlockAndUpdate(fixture.base.origin.below(), Blocks.STONE.defaultBlockState());
            var first = fixture.base.resolve();
            helper.assertTrue(first.available(), first.diagnostic());
            first.entity().remove(Entity.RemovalReason.KILLED);
            fixture.reload(); fixture.observe();
            helper.assertTrue(fixture.instance().outcomes().containsKey("mentor_loss"), "owned character death resolves the authored loss path");
            helper.assertFalse(fixture.base.resolve().available(), "lost campaign-owned character cannot respawn");
            var receipt = CampaignSpawnSavedData.get(helper.getLevel()).receipts().values().iterator().next();
            helper.assertTrue(receipt.committed(), "original spawn receipt remains committed");
            helper.assertValueEqual(receipt.binding().entityId(), first.binding().entityId(), "spawn history retains the original identity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_loss", timeoutTicks = 200)
    public static void activeAbsentAndLatePlayersCanFinishTheAuthoredLossPath(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mentor = fixture.mentor();
            var witness = fixture.witness();
            var active = fixture.player();
            fixture.select(active, mentor, CAMPAIGN, "visit", "offer");
            var progress = VillagerQuestSavedData.get(helper.getLevel()).get(active.getUUID(), CAMPAIGN);
            helper.assertTrue(progress != null && !SceneSavedData.get(helper.getLevel()).active().isEmpty(), "original quest is waiting on its character scene");
            var absent = fixture.player();
            fixture.data().enterMilestone(fixture.content().definition(), absent.getUUID(), "mentor_loss", helper.getLevel().getGameTime());
            mentor.discard(); fixture.observe();
            var committed = fixture.instance().outcomes().get("mentor_loss");
            fixture.reload();
            progress = VillagerQuestSavedData.get(helper.getLevel()).get(active.getUUID(), CAMPAIGN);
            helper.assertTrue(CampaignQuestReconciliation.reconcile(active, fixture.base.catalog.quest(CAMPAIGN).orElseThrow(), progress), "active quest enters its authored alternate path");
            helper.assertTrue(SceneSavedData.get(helper.getLevel()).active().isEmpty(), "loss supersedes the scene waiting for the missing actor");
            var late = fixture.player();
            for (var player : List.of(active, absent, late)) {
                fixture.finishCatchUp(player, witness);
                var personal = fixture.instance().players().get(player.getUUID());
                helper.assertTrue(personal.milestones().get("mentor_loss").completed(), "every story position can finish the loss path");
                helper.assertTrue(personal.participation().isEmpty(), "learning a loss never claims participation");
                helper.assertValueEqual(player.totalExperience, 5, "only the authored personal reward is delivered");
                helper.assertTrue(CampaignProgression.chapter(fixture.content().definition(), fixture.instance(), player.getUUID(), "arrival").completed(), "alternate quest completes the original chapter");
                for (String reward : List.of("loss_shared", "loss_participants")) {
                    var raw = JsonParser.parseString("{\"actions\":[{\"type\":\"campaign_reward\",\"reward\":\"" + reward + "\",\"operation_id\":\"try_original\"}]}").getAsJsonObject();
                    var action = VillagerActionDefinition.readList(CAMPAIGN, "fixture", raw, CAMPAIGN).getFirst();
                    var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, witness);
                    helper.assertFalse(VillagerActionRegistry.execute(context, action, Map.of()).success(), "catch-up cannot claim shared or participation rewards");
                }
            }
            fixture.reload(); fixture.observe();
            helper.assertValueEqual(fixture.instance().outcomes().get("mentor_loss"), committed, "all paths retain the same shared loss receipt");
            helper.assertValueEqual(CampaignRewardSavedData.get(helper.getLevel()).receipts().size(), 3, "each player has one personal reward receipt");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final CampaignCharacterTestFixture base;
        final CampaignRewardSavedData previousRewards;
        final List<Villager> villagers = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();

        Fixture(GameTestHelper helper) throws IOException {
            this.helper = helper;
            base = new CampaignCharacterTestFixture(helper, "losses", "loss_greeting", List.of("losses_catch_up"));
            previousRewards = CampaignRewardSavedData.get(helper.getLevel());
            base.server.overworld().getDataStorage().set(CampaignRewardSavedData.DATA_NAME, CampaignRewardSavedData.load(new CompoundTag(), base.disk.root));
        }

        CampaignSavedData data() { return CampaignSavedData.get(helper.getLevel()); }
        CampaignInstance instance() { return data().instances().get(CAMPAIGN); }
        CampaignContentCatalog.Entry content() { return CampaignResources.current(base.server).campaigns().get(CAMPAIGN); }
        void observe() { CampaignWorldEventService.reconcile(base.server); }

        Villager villager() {
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            villager.setNoAi(true);
            villagers.add(villager);
            return villager;
        }

        Villager mentor() {
            var villager = villager();
            var result = base.resolve();
            helper.assertTrue(result.available() && result.entity() == villager, "fixture casts its mentor: " + result.diagnostic());
            return villager;
        }

        Villager witness() {
            var villager = villager();
            var result = CampaignCharacterService.resolve(base.server, CAMPAIGN, "witness");
            helper.assertTrue(result.available() && result.entity() == villager, "witness has its own persistent identity");
            return villager;
        }

        Villager restore(CompoundTag saved) {
            var restored = EntityType.VILLAGER.create(helper.getLevel()); restored.load(saved);
            helper.assertTrue(helper.getLevel().addFreshEntity(restored), "original character reloads");
            villagers.add(restored);
            return restored;
        }

        ServerPlayer player() {
            var player = helper.makeMockServerPlayerInLevel();
            players.add(player);
            return player;
        }

        String select(ServerPlayer player, Villager villager, ResourceLocation quest, String stage, String slot) {
            players.forEach(value -> VillagerConversationService.endForPlayer(value, false));
            player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
            String selected = base.select(context, quest, stage, slot);
            var pending = CampaignQuestAcceptanceService.pending(player);
            if (pending != null) CampaignQuestAcceptanceService.handle(player, new CampaignQuestConfirmationRequestPayload(pending.id(), pending.revision(), true));
            return selected;
        }

        void finishCatchUp(ServerPlayer player, Villager witness) {
            select(player, witness, CATCH_UP, "catch_up", "offer");
            select(player, witness, CATCH_UP, "catch_up", "reminder");
            String request = select(player, witness, CATCH_UP, "catch_up_report", "turn_in");
            int experience = player.totalExperience;
            VillagerDialogueRequestHandler.handle(player, witness.getId(), request);
            helper.assertValueEqual(player.totalExperience, experience, "duplicate response does not repeat personal rewards");
            helper.assertValueEqual(VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), CATCH_UP).completionCount(), 1, "witness dialogue completes the alternate quest");
        }

        void reload() throws IOException {
            base.reload();
            var storage = base.server.overworld().getDataStorage();
            storage.set(CampaignRewardSavedData.DATA_NAME, CampaignRewardSavedData.load(base.disk.read(CampaignRewardSavedData.DATA_NAME), base.disk.root));
            var quests = VillagerQuestSavedData.get(helper.getLevel());
            storage.set("villagerretaliation_quests", VillagerQuestSavedData.load(quests.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess()));
        }

        @Override public void close() throws IOException {
            for (var player : players) {
                VillagerConversationService.endForPlayer(player, false);
                CampaignQuestAcceptanceService.onLogout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
                Files.deleteIfExists(CampaignPlayerRewards.directory(player).resolve(player.getStringUUID() + ".dat"));
                Files.deleteIfExists(CampaignPlayerRewards.directory(player).resolve(player.getStringUUID() + ".dat_old"));
            }
            villagers.forEach(Entity::discard);
            base.server.overworld().getDataStorage().set(CampaignRewardSavedData.DATA_NAME, previousRewards);
            Files.deleteIfExists(base.disk.root.resolve(CampaignRewardSavedData.DATA_NAME + ".dat"));
            DialogueTreeService.clearRuntimeState();
            base.close();
        }
    }
}
