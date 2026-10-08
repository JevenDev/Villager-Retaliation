package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignVariantIntegrationGameTests {
    private CampaignVariantIntegrationGameTests() { }

    @GameTest(template = "empty", batch = "campaign_variant_integration", timeoutTicks = 200)
    public static void observedDragonDefeatAppliesLocationConsequencesWithoutParticipantCredit(GameTestHelper helper) throws Exception {
        var end = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
        var prior = end.getDragonFight();
        try (var fixture = new CampaignVariantTestFixture(helper, campaign ->
                campaign.getAsJsonArray("milestones").get(0).getAsJsonObject().add("observe",
                        com.google.gson.JsonParser.parseString("{\"event\":\"minecraft:ender_dragon_defeated\",\"outcome\":\"sealed\"}")))) {
            end.setDragonFight(new net.minecraft.world.level.dimension.end.EndDragonFight(end, 0L,
                    new net.minecraft.world.level.dimension.end.EndDragonFight.Data(false, false, true, false,
                            java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty())));
            CampaignWorldEventService.reconcile(fixture.base.server);
            helper.assertTrue(fixture.receipt("sealed").complete(), "ordinary world state applies its authored location effect");
            helper.assertValueEqual(fixture.instance().outcomes().get("gate").origin(), CampaignInstance.Origin.WORLD_OBSERVATION, "source stays an observed world event");
            helper.assertTrue(fixture.instance().players().values().stream().allMatch(value -> value.participation().isEmpty()), "observation awards no participation");
            fixture.reload();
            CampaignWorldEventService.reconcile(fixture.base.server);
            helper.assertValueEqual(fixture.data().receipts().size(), 1, "repeat observation never duplicates the location change");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.entry).is(Blocks.IRON_BARS), "observed outcome retains its gate");
        } finally { end.setDragonFight(prior); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_variant_integration", timeoutTicks = 200)
    public static void persistentConversationResumesItsCommittedLocationEffectAfterReload(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper); var storage = new ConversationTestStorage(helper.getLevel())) {
            var player = fixture.player();
            fixture.select(player, "variants", "choose", "offer", 0);
            VillagerConversationService.endForPlayer(player, false);
            var result = ConversationService.start(player, net.minecraft.resources.ResourceLocation.parse("campaign_fixture:gate_decision"), "gate", UUID.randomUUID());
            helper.assertTrue(result.session() != null && result.diagnostic().isBlank(), "persistent fixture starts: " + result.diagnostic());
            result = intent(player, result, ConversationSession.Intent.ADVANCE, "");
            result = intent(player, result, ConversationSession.Intent.CHOOSE, "sealed");
            helper.assertTrue(result.session().confirmation() != null, "shared choice receives its impact confirmation");
            var chunk = helper.getLevel().getChunkAt(fixture.floor);
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (event.getChunk() == chunk) event.getData().getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).remove("villagerretaliation:campaign_blocks");
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { result = intent(player, result, ConversationSession.Intent.CONFIRM, result.session().confirmation().id().toString()); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.RESPONSE_EFFECTS, "pending effect cannot enter the next node");
            helper.assertFalse(result.diagnostic().isBlank(), "the player sees that this required change is waiting");
            helper.assertValueEqual(com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(helper.getLevel())
                    .get(player.getUUID(), fixture.base.campaignId).currentStage(), "choose", "quest transition waits behind the required location effect");
            var session = result.session().identity().id();
            storage.reload();
            fixture.reload();
            helper.assertValueEqual(fixture.reconcile(), "", "native storage recovery completes the same outcome");
            var levelData = (net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData();
            long time = levelData.getGameTime();
            try {
                levelData.setGameTime((time / 100 + 1) * 100);
                result = intent(player, ConversationService.view(player, session), ConversationSession.Intent.RESUME, "");
            } finally { levelData.setGameTime(time); }
            helper.assertValueEqual(result.session().node(), "sealed", "saved conversation follows its original choice");
            helper.assertValueEqual(com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(helper.getLevel())
                    .get(player.getUUID(), fixture.base.campaignId).currentStage(), "restore", "saved conversation advances the quest after its location effect");
            result = intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "conversation reaches its authored outcome");
            helper.assertValueEqual(fixture.data().receipts().size(), 1, "resuming conversation does not duplicate world effects");
            storage.reload();
            helper.assertValueEqual(ConversationService.view(player, session).session(), result.session(), "completed presentation survives reload");
            fixture.select(player, "variants", "restore", "reminder", 0);
            fixture.select(player, "variants", "report", "turn_in", 0);
            helper.assertValueEqual(player.totalExperience, 13, "conversation route can finish the quest and claim its participant reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_variant_integration", timeoutTicks = 200)
    public static void interruptedLaterCellPreservesPlayerEditsToCompletedCells(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            fixture.commit("gate", "sealed", UUID.randomUUID());
            var chunk = helper.getLevel().getChunkAt(fixture.entry);
            var once = new java.util.concurrent.atomic.AtomicBoolean();
            Consumer<ChunkDataEvent.Save> listener = event -> {
                var receipt = CampaignBlockOwnership.receipt(chunk, fixture.entry);
                if (event.getChunk() == chunk && receipt != null && receipt.state() == CampaignBlockOwnershipLedger.State.RESERVED
                        && once.compareAndSet(false, true))
                    helper.getLevel().setBlock(fixture.entry, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            };
            NeoForge.EVENT_BUS.addListener(listener);
            String issue;
            try { issue = fixture.reconcile(); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertTrue(once.get() && !issue.isBlank(), "second cell mutation interrupts its reservation");
            helper.assertValueEqual(fixture.receipt("sealed").cursor(), 1, "first cell already has a durable completion receipt");
            helper.getLevel().setBlock(fixture.floor, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            fixture.reload();
            helper.assertFalse(fixture.reconcile().isBlank(), "lost ownership remains a conflict after reload");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.floor).is(Blocks.GOLD_BLOCK), "earlier completed cell is never replayed");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.entry).is(Blocks.DIAMOND_BLOCK), "conflicting pending cell is preserved");
            helper.assertValueEqual(CampaignLocationVariants.preserve(fixture.base.server, fixture.receipt("sealed").operation(),
                    UUID.randomUUID(), "Keep both player edits"), "", "operator repair retires the remaining cell");
            helper.assertTrue(fixture.receipt("sealed").complete(), "explicit repair permits later story progress");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_variant_integration")
    public static void locationRepairCommandRequiresOperatorAuthority(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            fixture.commit("gate", "sealed", UUID.randomUUID());
            helper.getLevel().setBlock(fixture.floor, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertFalse(fixture.reconcile().isBlank(), "fixture creates an inspectable conflict");
            var operation = fixture.receipt("sealed").operation();
            String command = "vr admin campaignLocation preserve " + operation + " Keep player construction";
            var dispatcher = fixture.base.server.getCommands().getDispatcher();
            var source = fixture.base.server.createCommandSourceStack().withSuppressedOutput();
            boolean denied = false;
            try { dispatcher.execute(command, source.withPermission(0)); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            helper.assertTrue(denied && !fixture.receipt("sealed").complete(), "ordinary players cannot retire a campaign effect");
            helper.assertValueEqual(dispatcher.execute("vr admin campaignLocation list", source.withPermission(2)), 1, "default page lists the saved effect");
            helper.assertValueEqual(dispatcher.execute("vr admin campaignLocation list 2", source.withPermission(2)), 0, "unavailable page is rejected");
            helper.assertValueEqual(dispatcher.execute(command, source.withPermission(2)), 1, "operator can preserve conflicting terrain");
            fixture.reload();
            helper.assertValueEqual(fixture.receipt("sealed").audit().reason(), "Keep player construction", "operator reason is durable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_variant_integration")
    public static void journalShowsMissingAndUnreadableEffectsOnlyAfterDiscovery(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            var participant = fixture.player();
            var unaware = fixture.player();
            fixture.commit("gate", "sealed", participant.getUUID());
            helper.assertTrue(journal(fixture, participant).diagnostic().contains("location change is pending"), "committed outcome is visible before its first receipt");
            helper.assertFalse(journal(fixture, unaware).diagnostic().contains("location change"), "undiscovered choices reveal no location consequence");
            helper.assertValueEqual(fixture.reconcile(), "", "authored consequence completes");
            helper.assertTrue(journal(fixture, participant).diagnostic().isBlank(), "completed receipt clears the notice");
            var saved = fixture.data().save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
            var future = saved.copy();
            future.putInt("DataVersion", 99);
            fixture.base.server.overworld().getDataStorage().set(CampaignVariantSavedData.DATA_NAME,
                    CampaignVariantSavedData.load(future, fixture.base.disk.root));
            helper.assertTrue(journal(fixture, participant).diagnostic().contains("location change is pending"), "unreadable receipts keep a repair notice");
            helper.assertFalse(journal(fixture, unaware).diagnostic().contains("location change"), "unreadable state does not reveal another player's discovery");
            fixture.base.server.overworld().getDataStorage().set(CampaignVariantSavedData.DATA_NAME,
                    CampaignVariantSavedData.load(saved, fixture.base.disk.root));
            helper.assertTrue(journal(fixture, participant).diagnostic().isBlank(), "restored receipts clear the repair notice");
        }
        helper.succeed();
    }

    private static CampaignJournalPresenter.Entry journal(CampaignVariantTestFixture fixture, net.minecraft.server.level.ServerPlayer player) {
        return CampaignJournalPresenter.entries(player).stream().filter(entry -> entry.campaignId().equals(fixture.base.campaignId.toString())).findFirst().orElseThrow();
    }

    private static ConversationService.Result intent(net.minecraft.server.level.ServerPlayer player, ConversationService.Result result,
            ConversationSession.Intent intent, String argument) {
        return ConversationService.request(player, result.session().identity().id(), result.session().revision(), UUID.randomUUID(), intent, argument);
    }
}
