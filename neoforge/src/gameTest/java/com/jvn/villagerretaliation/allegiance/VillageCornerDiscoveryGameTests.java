package com.jvn.villagerretaliation.allegiance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillageCornerDiscoveryGameTests {
    private VillageCornerDiscoveryGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "village_corner_discovery")
    public static void vanillaVillageCanBeDiscoveredAcrossAChunkCorner(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos bed = new BlockPos(Math.floorDiv(origin.getX(), 16) * 16 + 15,
                origin.getY(), Math.floorDiv(origin.getZ(), 16) * 16 + 15);
        BlockPos table = bed.offset(1, 0, 1);
        level.getChunkAt(bed);
        level.getChunkAt(table);
        level.setBlockAndUpdate(bed.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(bed.south().below(), Blocks.STONE.defaultBlockState());
        var state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlock(bed, state.setValue(BedBlock.PART, BedPart.HEAD), 2);
        level.setBlock(bed.south(), state.setValue(BedBlock.PART, BedPart.FOOT), 2);
        var manager = level.getPoiManager();
        manager.add(bed, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME));
        helper.assertTrue(manager.take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bed), bed, 1).isPresent(),
                "the real bed is occupied");
        try {
            helper.assertTrue(level.isVillage(table), "Minecraft recognizes the diagonal neighboring section as part of the village");
            var registry = VillageAllegianceRegistrySavedData.get(level);
            var discovered = registry.discoverAt(level, table);
            helper.assertTrue(discovered.isPresent(), "settlement discovery agrees with the vanilla village at the corner");
            helper.assertTrue(registry.record(discovered.orElseThrow()).orElseThrow().sourceSections().contains(SectionPos.asLong(bed)),
                    "the discovered identity includes the actual occupied bed section");
        } finally {
            manager.release(bed);
        }
        helper.succeed();
    }
}
