package com.jvn.villagerretaliation.diplomacy.route;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("diplomatic_routes")
@PrefixGameTestTemplate(false)
public final class DiplomaticTravelMapGameTests {
    @GameTest(template = "empty")
    public static void preparationRevealsNothingAndTravelRevealsOnlyItsCorridor(GameTestHelper helper) {
        var level = helper.getLevel(); var village = VillageAllegianceId.random(); var other = VillageAllegianceId.random();
        var dimension = level.dimension().location(); var start = new BlockPos(24000000, 70, 24000000);
        int[] heights = new int[256], colors = new int[256]; byte[] kinds = new byte[256];
        Arrays.fill(heights, 70); Arrays.fill(colors, 0x68a857); Arrays.fill(kinds, RouteTerrain.WALK);
        var terrain = RouteSavedData.get(level);
        for (int x = -1; x <= 3; x++) for (int z = -1; z <= 1; z++) terrain.observe(dimension,
                new RouteTerrain(new ChunkPos((start.getX() >> 4) + x, (start.getZ() >> 4) + z), heights, kinds, colors, level.getGameTime()));
        var map = new DiplomaticTravelMapData();
        helper.assertTrue(map.page(village, 0, 0, dimension).cells().isEmpty(), "route observations alone reveal no terrain");
        map.observe(level, village, start);
        var first = map.page(village, 0, 0, dimension);
        helper.assertValueEqual(first.cells().size(), 9, "actual travel reveals a three-block corridor");
        helper.assertTrue(first.cells().stream().allMatch(cell -> Math.abs(cell.position().getX() - start.getX()) <= 1
                && Math.abs(cell.position().getZ() - start.getZ()) <= 1), "no terrain outside the travelled corridor is disclosed");
        helper.assertTrue(map.page(other, 0, 0, dimension).cells().isEmpty(), "other villages receive no corridor history");
        map.observe(level, village, start);
        helper.assertTrue(map.page(village, first.epoch(), first.next(), dimension).cells().isEmpty(), "repeated observations send no duplicate terrain");
        for (int x = 1; x < 35; x++) map.observe(level, village, start.offset(x, 0, 0));
        var next = map.page(village, first.epoch(), first.next(), dimension);
        helper.assertValueEqual(next.cells().size(), 64, "terrain update is bounded to 64 columns");
        helper.assertTrue(next.more(), "remaining terrain is delivered incrementally");
        helper.assertTrue(next.cells().stream().allMatch(cell -> cell.sequence() > first.next()), "incremental response excludes prior columns");
        var last = map.page(village, next.epoch(), next.next(), dimension);
        helper.assertTrue(!last.cells().isEmpty() && !last.more(), "next page completes the incremental corridor");
        helper.assertTrue(level.getChunkSource().getChunkNow(start.getX() >> 4, start.getZ() >> 4) == null, "revealing cached travel terrain loads no chunk");
        map.observe(level, other, start.offset(40, 0, 0));
        map.merge(village, other);
        helper.assertTrue(map.page(village, 0, 0, dimension).cells().isEmpty(), "merged source history has one canonical owner");
        var merged = map.page(other, 0, 0, dimension);
        helper.assertTrue(merged.epoch() > first.epoch() && merged.cells().size() == 64 && merged.more(), "merge invalidates stale cursors and preserves bounded history");
        var saved = map.save(new CompoundTag(), level.registryAccess());
        helper.assertValueEqual(DiplomaticTravelMapData.load(saved, level.registryAccess()).save(new CompoundTag(), level.registryAccess()), saved, "corridor history survives restart independently of personal terrain");
        var future = saved.copy(); future.putInt("Version", 100);
        var recovered = DiplomaticTravelMapData.load(future, level.registryAccess());
        helper.assertTrue(recovered.readOnly(), "unsupported corridor data is preserved read-only");
        helper.assertValueEqual(recovered.save(new CompoundTag(), level.registryAccess()), future, "unsupported map is not reset");
        helper.succeed();
    }
}
