package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.client.settlement.SettlementInteriorCutaway;
import com.jvn.villagerretaliation.client.settlement.SettlementViewBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_peek")
@PrefixGameTestTemplate(false)
public final class SettlementInteriorCutawayGameTests {
    private SettlementInteriorCutawayGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_peek", skyAccess = true)
    public static void cutawayKeepsFloorsFurnitureAndFarWalls(GameTestHelper helper) {
        var bounds = house(helper, 8);
        helper.setBlock(18, 5, 18, Blocks.CHEST);
        for (var pos : BlockPos.betweenClosed(24, 4, 16, 24, 8, 22)) helper.setBlock(pos, Blocks.BRICKS);
        var room = detect(helper, bounds);
        helper.assertTrue(room != null, "a registered above-ground room beside another building is detected");
        var mask = room.mask(helper.absolutePos(new BlockPos(4, 15, 19)).getCenter());
        helper.assertTrue(mask.contains(helper.absolutePos(new BlockPos(19, 8, 19))), "the roof is hidden");
        helper.assertTrue(mask.contains(helper.absolutePos(new BlockPos(16, 6, 19))), "the near wall is hidden");
        helper.assertTrue(!mask.contains(helper.absolutePos(new BlockPos(22, 6, 19))), "the far wall remains visible");
        helper.assertTrue(!mask.contains(helper.absolutePos(new BlockPos(19, 4, 19))), "the floor remains visible");
        helper.assertTrue(!mask.contains(helper.absolutePos(new BlockPos(18, 5, 18))), "interior storage remains visible");
        helper.assertTrue(mask.stream().allMatch(pos -> bounds.box().contains(pos.getCenter())), "adjacent buildings are never masked");
        helper.assertTrue(mask.stream().allMatch(pos -> pos.getY() > room.floor()), "nothing beneath the floor is hidden");
        helper.assertTrue(room.unchanged(helper.getLevel(), pos -> true), "unchanged room geometry can be reused");
        helper.setBlock(19, 8, 19, Blocks.DIRT);
        helper.assertTrue(!room.unchanged(helper.getLevel(), pos -> true), "roof changes invalidate the cache");
        helper.assertTrue(detect(helper, bounds) == null, "natural roof cover disables the whole cutaway");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_peek", skyAccess = true)
    public static void upperFloorAndOpenDoorwayStayStable(GameTestHelper helper) {
        var bounds = house(helper, 12);
        for (var pos : BlockPos.betweenClosed(16, 8, 16, 22, 8, 22)) helper.setBlock(pos, Blocks.OAK_PLANKS);
        helper.setBlock(16, 9, 19, Blocks.AIR);
        helper.setBlock(16, 10, 19, Blocks.AIR);
        var room = detect(helper, bounds);
        helper.assertTrue(room != null, "an open doorway does not prevent peeking");
        helper.assertValueEqual(room.floor(), helper.absolutePos(new BlockPos(19, 8, 19)).getY(), "the upper floor is selected first");
        helper.assertTrue(room.mask(helper.absolutePos(new BlockPos(4, 20, 19)).getCenter()).stream()
                .allMatch(pos -> pos.getY() > room.floor()), "the upper floor and lower story are preserved");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_peek", skyAccess = true)
    public static void terrainAndUnloadedBoundariesNeverRevealCaves(GameTestHelper helper) {
        var bounds = house(helper, 8);
        helper.assertTrue(SettlementInteriorCutaway.detect(helper.getLevel(), bounds,
                helper.absolutePos(new BlockPos(19, 8, 19)), pos -> pos.getX() < bounds.max().getX()) == null,
                "partially unavailable geometry is rejected");
        helper.assertTrue(SettlementInteriorCutaway.detect(helper.getLevel(), bounds,
                helper.absolutePos(new BlockPos(19, 8, 19)), pos -> pos.getX() >= bounds.min().getX() - 1) == null,
                "an unavailable exterior terrain probe rejects the room without scanning unknown heights");
        for (var pos : BlockPos.betweenClosed(13, 4, 13, 14, 10, 25)) helper.setBlock(pos, Blocks.STONE);
        helper.assertTrue(detect(helper, bounds) == null, "raised natural ground outside a wall prevents a cave cutaway");
        for (var pos : BlockPos.betweenClosed(13, 4, 13, 14, 10, 25)) helper.setBlock(pos, Blocks.AIR);
        for (var pos : BlockPos.betweenClosed(16, 9, 16, 22, 9, 22)) helper.setBlock(pos, Blocks.STONE);
        helper.assertTrue(detect(helper, bounds) == null, "terrain over a crafted roof cannot be removed");
        var oversized = new SettlementViewBounds(bounds.min(), bounds.max().offset(128, 0, 128));
        helper.assertTrue(detect(helper, oversized) == null, "oversized registered areas do not trigger unbounded scans");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_peek", skyAccess = true)
    public static void overhangHoverKeepsAnOpenEntranceInsideTheRoomBoundary(GameTestHelper helper) {
        house(helper, 8);
        for (var pos : BlockPos.betweenClosed(12, 4, 12, 26, 4, 26)) {
            if (pos.getX() < 16 || pos.getX() > 22 || pos.getZ() < 16 || pos.getZ() > 22)
                helper.setBlock(pos, Blocks.GRASS_BLOCK);
        }
        for (var pos : BlockPos.betweenClosed(15, 8, 15, 23, 8, 23)) helper.setBlock(pos, Blocks.OAK_SLAB);
        helper.setBlock(16, 5, 19, Blocks.AIR);
        helper.setBlock(16, 6, 19, Blocks.AIR);
        var bounds = new SettlementViewBounds(helper.absolutePos(new BlockPos(15, 4, 15)), helper.absolutePos(new BlockPos(23, 8, 23)));
        var room = SettlementInteriorCutaway.detect(helper.getLevel(), bounds, helper.absolutePos(new BlockPos(15, 8, 19)), pos -> true);
        helper.assertTrue(room != null, "hovering the overhang finds the interior beyond an open doorway");
        var mask = room.mask(helper.absolutePos(new BlockPos(4, 15, 19)).getCenter());
        helper.assertTrue(mask.contains(helper.absolutePos(new BlockPos(19, 8, 19))), "the interior roof is hidden");
        helper.assertTrue(!mask.contains(helper.absolutePos(new BlockPos(22, 6, 19))), "the exterior path does not reverse the far wall mask");
        helper.assertTrue(mask.stream().allMatch(pos -> pos.getY() > room.floor()), "outdoor ground remains intact");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_peek", skyAccess = true)
    public static void registeredTentWithSlopedWoolWallsAndFenceSupportsCanBePeeked(GameTestHelper helper)
            throws java.io.IOException, com.mojang.brigadier.exceptions.CommandSyntaxException {
        var template = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
        template.load(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), net.minecraft.nbt.TagParser.parseTag(
                java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("villagerretaliation.gameteststructures"), "settlement_peek_tent.snbt"))));
        var origin = helper.absolutePos(new BlockPos(16, 4, 16));
        helper.assertTrue(template.placeInWorld(helper.getLevel(), origin, origin,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), helper.getLevel().random, 2),
                "the saved tent geometry is placed in the test world");
        helper.assertBlockPresent(Blocks.GREEN_WOOL, 21, 9, 20);
        var bounds = new SettlementViewBounds(origin.offset(0, 1, 0), origin.offset(10, 6, 9));
        var roof = origin.offset(5, 5, 4);
        var room = SettlementInteriorCutaway.detect(helper.getLevel(), bounds, roof, pos -> true);
        helper.assertTrue(room != null, "the registered tent from the settlement save has a peekable interior");
        var mask = room.mask(helper.absolutePos(new BlockPos(15, 12, -5)).getCenter());
        helper.assertTrue(mask.contains(roof), "the wool canopy above the beds is hidden");
        helper.assertTrue(mask.stream().allMatch(pos -> bounds.box().contains(pos.getCenter())), "the tent cutaway stays inside registered bounds");
        helper.assertTrue(mask.stream().noneMatch(pos -> helper.getLevel().getBlockState(pos).is(Blocks.RED_BED)), "beds remain visible");
        helper.assertTrue(mask.stream().allMatch(pos -> pos.getY() > room.floor()), "the grass and dirt floor remains visible");
        helper.succeed();
    }

    private static SettlementInteriorCutaway.Room detect(GameTestHelper helper, SettlementViewBounds bounds) {
        return SettlementInteriorCutaway.detect(helper.getLevel(), bounds,
                new BlockPos(bounds.min().getX() + 3, bounds.max().getY(), bounds.min().getZ() + 3), pos -> true);
    }

    private static SettlementViewBounds house(GameTestHelper helper, int roof) {
        for (var pos : BlockPos.betweenClosed(12, 3, 12, 26, 14, 26)) {
            boolean ground = pos.getY() == 3;
            boolean shell = pos.getX() >= 16 && pos.getX() <= 22 && pos.getZ() >= 16 && pos.getZ() <= 22
                    && pos.getY() >= 4 && pos.getY() <= roof && (pos.getY() == 4 || pos.getY() == roof
                    || pos.getX() == 16 || pos.getX() == 22 || pos.getZ() == 16 || pos.getZ() == 22);
            helper.setBlock(pos, ground ? Blocks.DIRT : shell ? Blocks.OAK_PLANKS : Blocks.AIR);
        }
        return new SettlementViewBounds(helper.absolutePos(new BlockPos(16, 4, 16)), helper.absolutePos(new BlockPos(22, roof, 22)));
    }
}
