package com.jvn.villagerretaliation.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;

final class SettlementExperienceTestTerrain {
    private SettlementExperienceTestTerrain() {
    }

    static void flat(SettlementRecord town) {
        var center = town.center();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) {
            var section = new BlockPos((center.getX() & ~15) + x * 16, (center.getY() & ~15) + y * 16, (center.getZ() & ~15) + z * 16);
            var tag = new CompoundTag();
            tag.putLong("Origin", section.asLong());
            var palette = new ListTag();
            palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
            palette.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
            tag.put("Palette", palette);
            var blocks = new byte[8192];
            for (var position : BlockPos.betweenClosed(section, section.offset(15, 15, 15))) {
                if (position.getY() < center.getY()) {
                    int index = (((position.getY() & 15) << 8) | ((position.getZ() & 15) << 4) | (position.getX() & 15)) * 2;
                    blocks[index + 1] = 1;
                }
            }
            tag.putByteArray("Blocks", blocks);
            town.recordNavigation(SettlementNavigationSection.load(tag));
        }
    }
}
