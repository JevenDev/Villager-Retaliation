package com.jvn.villagerretaliation.interaction.work.logging;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("worker_performance")
@PrefixGameTestTemplate(false)
public final class SharedTreeDiscoveryGameTests {
    @GameTest(template = "empty", batch = "shared_tree_discovery")
    public static void sharedDiscoveryInvalidatesHeldCacheAfterBlockEdits(GameTestHelper helper) {
        var level = helper.getLevel();
        var root = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(root.below(), Blocks.DIRT.defaultBlockState());
        for (int y = 0; y < 4; y++) level.setBlockAndUpdate(root.above(y), Blocks.OAK_LOG.defaultBlockState());
        var crown = root.above(4);
        var leaves = List.of(crown, crown.north(), crown.south(), crown.east(), crown.west());
        for (var leaf : leaves) level.setBlockAndUpdate(leaf, Blocks.OAK_LEAVES.defaultBlockState());
        var first = LoggingTreeGeometry.discovery(level, Set.of());
        helper.assertTrue(first.isNaturalTree(root), "natural tree is discovered");
        var second = LoggingTreeGeometry.discovery(level, Set.of());
        helper.assertTrue(first == second, "workers with equal filters share discovery");
        helper.assertTrue(second.isNaturalTree(root.above()), "another log uses the discovered tree");
        helper.assertValueEqual(first.analysisCount(), 1, "shared tree geometry is calculated once");
        for (int index = 0; index < 9; index++) LoggingTreeGeometry.discovery(level,
                Set.of(net.minecraft.resources.ResourceLocation.parse("villagerretaliation:filter_" + index)));
        for (var leaf : leaves) level.setBlockAndUpdate(leaf, Blocks.AIR.defaultBlockState());
        helper.assertFalse(first.isNaturalTree(root), "removing the canopy invalidates a held cache even after its filter entry was evicted");
        helper.succeed();
    }
}
