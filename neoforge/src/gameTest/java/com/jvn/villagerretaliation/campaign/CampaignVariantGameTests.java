package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignVariantGameTests {
    private CampaignVariantGameTests() { }

    @GameTest(template = "empty", batch = "campaign_location_variants", timeoutTicks = 200)
    public static void authoredOpenDecisionAndCatchUpHaveSeparateRewards(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            var participant = fixture.player();
            fixture.select(participant, "variants", "choose", "offer", 0);
            fixture.select(participant, "variants", "choose", "reminder", 0);
            helper.assertTrue(fixture.receipt("open").complete(), "authored dialogue applies its named variant");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.floor).is(Blocks.MOSSY_STONE_BRICKS), "open outcome changes its owned floor");
            fixture.select(participant, "variants", "report", "turn_in", 0);
            helper.assertValueEqual(participant.totalExperience, 13, "participant receives the original reward");
            fixture.reload();
            var late = fixture.player();
            fixture.select(late, "variants_catch_up", "learn", "offer", 0);
            fixture.select(late, "variants_catch_up", "learn", "turn_in", 0);
            helper.assertValueEqual(late.totalExperience, 5, "catch-up has its own personal reward");
            helper.assertTrue(fixture.instance().players().get(late.getUUID()).participation().isEmpty(), "late arrival earns no invented participation");
            helper.assertValueEqual(fixture.data().receipts().size(), 1, "catch-up does not repeat the shared location effect");
            helper.assertValueEqual(fixture.reconcile(), "", "completed location effect remains satisfied");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_variants", timeoutTicks = 200)
    public static void laterAuthoredDecisionAdvancesOwnershipWithoutReplayingTheFirst(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            var player = fixture.player();
            fixture.select(player, "variants", "choose", "offer", 0);
            fixture.select(player, "variants", "choose", "reminder", 1);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.entry).is(Blocks.IRON_BARS), "sealed outcome changes its owned doorway");
            helper.assertTrue(fixture.receipt("sealed").complete(), "first variant is durable");
            fixture.reload();
            fixture.select(player, "variants", "restore", "reminder", 0);
            helper.assertTrue(fixture.receipt("restored").complete(), "later outcome advances the same site ownership");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.entry).isAir(), "restoration reopens the doorway");
            helper.getLevel().setBlock(fixture.entry, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            fixture.reload();
            helper.assertValueEqual(fixture.reconcile(), "", "repeating completed effects is harmless");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.entry).is(Blocks.DIAMOND_BLOCK), "replay preserves later player construction");
            helper.assertValueEqual(fixture.data().receipts().size(), 2, "each committed outcome owns one saved effect");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_variants")
    public static void competingOutcomesOnlyApplyTheCommittedVariant(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            var winner = fixture.commit("gate", "sealed", UUID.randomUUID());
            var loser = fixture.commit("gate", "open", UUID.randomUUID());
            helper.assertValueEqual(loser.committed(), winner.committed(), "losing request receives the actual world outcome");
            helper.assertValueEqual(fixture.reconcile(), "", "committed variant applies");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.entry).is(Blocks.IRON_BARS), "losing open request cannot change the gate");
            helper.assertValueEqual(fixture.data().receipts().size(), 1, "competing request creates no second effect");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_variants")
    public static void restoredLookingPlayerEditsRequireAnAuditedRepair(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            helper.getLevel().setBlock(fixture.floor, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.getLevel().setBlock(fixture.floor, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            fixture.commit("gate", "sealed", UUID.randomUUID());
            helper.assertFalse(fixture.reconcile().isBlank(), "matching terrain cannot replace lost ownership");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.entry).isAir(), "preflight prevents partial effects when another cell already conflicts");
            var operation = fixture.receipt("sealed").operation();
            var operator = UUID.randomUUID();
            helper.assertValueEqual(CampaignLocationVariants.preserve(fixture.base.server, operation, operator, "Keep the player's repaired floor"), "", "operator can preserve the current terrain");
            fixture.reload();
            helper.assertTrue(fixture.receipt("sealed").complete(), "repair survives reload");
            helper.assertValueEqual(fixture.receipt("sealed").audit().operator(), operator, "repair retains its operator");
            helper.assertValueEqual(fixture.reconcile(), "", "preserved effect no longer blocks the story");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.entry).isAir(), "repair never performs the skipped mutation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_variants")
    public static void missingGenerationProofCannotBeRecreatedFromMatchingTerrain(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            var chunk = helper.getLevel().getChunkAt(fixture.floor);
            var generated = chunk.getData(CampaignGeneratedBlocks.GENERATION);
            chunk.removeData(CampaignGeneratedBlocks.GENERATION);
            fixture.commit("gate", "sealed", UUID.randomUUID());
            helper.assertFalse(fixture.reconcile().isBlank(), "missing generation proof stays unavailable");
            helper.assertFalse(chunk.hasData(CampaignGeneratedBlocks.GENERATION), "reconciliation never invents a generation record");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.floor).is(Blocks.STONE_BRICKS), "matching terrain remains untouched");
            chunk.setData(CampaignGeneratedBlocks.GENERATION, generated);
            helper.assertValueEqual(fixture.reconcile(), "", "restoring actual proof permits recovery");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_variants")
    public static void incompatibleContentAndSandboxModeLeavePendingTerrainUntouched(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            fixture.commit("gate", "sealed", UUID.randomUUID());
            fixture.base.mode("sandbox");
            helper.assertFalse(fixture.reconcile().isBlank(), "sandbox cannot apply campaign location effects");
            fixture.base.mode("campaign");
            var raw = fixture.base.raw(); raw.addProperty("revision", 2);
            var parsed = CampaignDefinitionParser.parse(fixture.base.campaignId, raw);
            helper.assertTrue(parsed.valid(), "changed revision remains valid authored content");
            fixture.base.install(raw);
            helper.assertFalse(fixture.reconcile().isBlank(), "incompatible content pauses the saved world effect");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.entry).isAir(), "pending effect never mutates under changed content");
            CampaignResources.installForTests(fixture.base.server, fixture.base.campaigns);
            helper.assertValueEqual(fixture.reconcile(), "", "original content resumes the same outcome");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_variants", timeoutTicks = 200)
    public static void requiredOutcomeWaitsThroughNativeStorageFailureAndContinuationReload(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            var player = fixture.player();
            fixture.select(player, "variants", "choose", "offer", 0);
            var chunk = helper.getLevel().getChunkAt(fixture.floor);
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (event.getChunk() == chunk) event.getData().getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).remove("villagerretaliation:campaign_blocks");
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { fixture.select(player, "variants", "choose", "reminder", 1); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            var progress = VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), fixture.base.campaignId);
            helper.assertValueEqual(progress.currentStage(), "choose", "required pending outcome prevents the following stage transition");
            helper.assertValueEqual(fixture.instance().outcomes().get("gate").outcome(), "sealed", "choice is committed before waiting");
            var scenes = SceneSavedData.get(helper.getLevel());
            var saved = scenes.save(new CompoundTag(), helper.getLevel().registryAccess());
            var restored = SceneSavedData.load(saved, helper.getLevel().registryAccess());
            fixture.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", restored);
            helper.assertValueEqual(fixture.reconcile(), "", "native storage recovery completes the recorded effect");
            var levelData = (net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData();
            long before = levelData.getGameTime();
            try {
                levelData.setGameTime((before / 100 + 1) * 100);
                SceneContinuationService.maintain(fixture.base.server, restored);
            } finally { levelData.setGameTime(before); }
            helper.assertValueEqual(progress.currentStage(), "restore", "saved continuation resumes its next action after durable recovery");
            helper.assertValueEqual(fixture.data().receipts().size(), 1, "continuation retry does not repeat the outcome effect");
        }
        helper.succeed();
    }
}
