package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_construction_safety")
@PrefixGameTestTemplate(false)
public final class SettlementConstructionSafetyGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void preparationDistinguishesTerrainFromCraftedBuildings(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.assertTrue(MiningBlockRules.isBuilderClearableObstruction(level, position, Blocks.STONE.defaultBlockState()), "Natural stone can be prepared with tools");
        helper.assertFalse(MiningBlockRules.isBuilderClearableObstruction(level, position, Blocks.OAK_PLANKS.defaultBlockState()), "Plank buildings require player clearing");
        helper.assertFalse(MiningBlockRules.isBuilderClearableObstruction(level, position, Blocks.STONE_BRICKS.defaultBlockState()), "Crafted masonry is protected");
        helper.assertFalse(BuilderSitePlanner.safeReplaceable(level, position, Blocks.TORCH.defaultBlockState()), "Collisionless authored fixtures are not disposable soft terrain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void newSiteCannotClaimAnExistingMatchingContainer(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var chest = Blocks.CHEST.defaultBlockState();
        level.setBlock(origin, chest, 2);
        var container = (net.minecraft.world.Container) level.getBlockEntity(origin);
        container.setItem(0, new ItemStack(Items.DIAMOND, 7));
        var entry = new BuilderStructureCatalog.Entry(ResourceLocation.fromNamespaceAndPath("villagerretaliation", "safety_fixture"), "House", "Safety fixture", 0);
        var block = new BuilderStructureScanner.BuildBlock(BlockPos.ZERO, chest, null, new ItemStack(Items.CHEST), BuilderStructureScanner.BuilderToolAction.NONE);
        var plan = new BuilderStructureScanner.StructurePlan(entry, Rotation.NONE, new Vec3i(1, 1, 1), BlockPos.ZERO, BlockPos.ZERO, List.of(block), List.of(), 1, List.of());
        var area = HiredWorkArea.fromBounds(origin.offset(-2, -1, -2), origin.offset(2, 3, 2), true);
        helper.assertFalse(BuilderSitePlanner.validateSite(level, null, null, area, plan, origin).valid(), "Matching chest state must not bypass existing container protection");
        helper.assertValueEqual(container.getItem(0).getCount(), 7, "Preview is read-only and preserves belongings");
        helper.succeed();
    }
}
