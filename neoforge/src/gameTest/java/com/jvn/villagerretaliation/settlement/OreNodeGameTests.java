package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.mining.OreNodes;
import com.jvn.villagerretaliation.interaction.work.mining.OreNodeDefinitions;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ore_nodes")
@PrefixGameTestTemplate(false)
public final class OreNodeGameTests {
    private OreNodeGameTests() { }

    private static BlockPos ore(GameTestHelper helper, boolean natural) {
        var position = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(position, Blocks.DIAMOND_ORE.defaultBlockState());
        if (natural) {
            var nodes = new CompoundTag();
            nodes.putInt(Long.toString(position.asLong()), 0);
            OreNodes.apply(helper.getLevel().getChunkAt(position), new CompoundTag(), nodes);
        }
        return position;
    }

    @GameTest(template = "empty", batch = "ore_nodes")
    public static void generatedTerrainRecordsOresAndReplacementClearsEligibility(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var source = new ProtoChunk(new net.minecraft.world.level.ChunkPos(pos), UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
        source.setBlockState(pos, Blocks.DIAMOND_ORE.defaultBlockState(), false);
        var chunk = new LevelChunk(level, source, null);
        helper.assertValueEqual(OreNodes.remaining(OreNodes.snapshot(chunk, pos, pos), pos, chunk.getBlockState(pos)), 10, "promotion captures generated ore");
        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
        chunk.setBlockState(pos, Blocks.DIAMOND_ORE.defaultBlockState(), false);
        helper.assertTrue(OreNodes.snapshot(chunk, pos, pos).isEmpty(), "replaced ore is ordinary");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "ore_nodes")
    public static void minersShareReserveAndFinalExtractionBreaksBlock(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = ore(helper, true);
        var miner = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 1));
        var second = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 1));
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        for (int i = 0; i < 9; i++) {
            helper.assertTrue(OreNodes.mine(level, pos, i % 2 == 0 ? miner : second, tool), "extraction succeeds");
            helper.assertTrue(level.getBlockState(pos).is(Blocks.DIAMOND_ORE), "reserve keeps ore present");
        }
        helper.assertTrue(OreNodes.mine(level, pos, miner, tool), "last extraction succeeds");
        helper.assertTrue(level.getBlockState(pos).isAir(), "last extraction depletes ore");
        helper.assertTrue(OreNodes.snapshot(level.getChunkAt(pos), pos, pos).isEmpty(), "depleted reserve removed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "ore_nodes")
    public static void placedOresAndSilkTouchBreakNormally(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = ore(helper, false);
        var miner = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 1));
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        helper.assertTrue(OreNodes.mine(level, pos, miner, tool) && level.getBlockState(pos).isAir(), "placed ore breaks once");
        ore(helper, true);
        tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        var drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(pos), level, pos, null, miner, tool);
        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.DIAMOND_ORE)), "silk touch retains normal loot");
        helper.assertTrue(OreNodes.mine(level, pos, miner, tool) && level.getBlockState(pos).isAir(), "silk touch removes whole deposit");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "ore_nodes")
    public static void backgroundReserveSurvivesSaveAndReconcilesOnlyOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = ore(helper, true);
        var chunk = level.getChunkAt(pos);
        var area = UUID.randomUUID();
        var section = SettlementTerrainSection.capture("Mine", area, pos, pos, chunk);
        helper.assertTrue(section.extractOre(level, pos, new ItemStack(Items.DIAMOND_PICKAXE)), "background extraction retains ore");
        var saved = section.save();
        var restored = SettlementTerrainSection.load("Mine", area, saved);
        helper.assertTrue(restored.reconcile(chunk), "saved extraction reconciles");
        helper.assertValueEqual(OreNodes.remaining(OreNodes.snapshot(chunk, pos, pos), pos, chunk.getBlockState(pos)), 9, "one use consumed");
        helper.assertTrue(SettlementTerrainSection.load("Mine", area, saved).reconcile(chunk), "replayed receipt is accepted");
        helper.assertValueEqual(OreNodes.remaining(OreNodes.snapshot(chunk, pos, pos), pos, chunk.getBlockState(pos)), 9, "receipt cannot consume or restore reserve twice");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "ore_nodes")
    public static void backgroundReserveRejectsLiveMiningConflict(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = ore(helper, true);
        var chunk = level.getChunkAt(pos);
        var section = SettlementTerrainSection.capture("Mine", UUID.randomUUID(), pos, pos, chunk);
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        section.extractOre(level, pos, tool);
        OreNodes.mine(level, pos, helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 1)), tool);
        helper.assertFalse(section.reconcile(chunk), "stale background reserve cannot overwrite live mining");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "ore_nodes")
    public static void dataDefinitionsIncludeDefaultAndSpecificOreTags(GameTestHelper helper) {
        helper.assertValueEqual(OreNodeDefinitions.extractions(Blocks.COAL_ORE.defaultBlockState()), 100, "coal override");
        helper.assertValueEqual(OreNodeDefinitions.extractions(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState()), 100, "iron variants use tag");
        helper.assertValueEqual(OreNodeDefinitions.extractions(Blocks.GOLD_ORE.defaultBlockState()), 50, "default ore reserve");
        helper.assertValueEqual(OreNodeDefinitions.extractions(Blocks.STONE.defaultBlockState()), 0, "ordinary terrain excluded");
        helper.succeed();
    }
}
