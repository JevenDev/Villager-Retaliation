package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

final class SettlementNavigationSection {
    private final BlockPos origin;
    private final List<BlockState> palette;
    private final byte[] blocks;

    private SettlementNavigationSection(BlockPos origin, List<BlockState> palette, byte[] blocks) {
        this.origin = origin.immutable();
        this.palette = List.copyOf(palette);
        this.blocks = blocks;
    }

    BlockPos origin() {
        return this.origin;
    }

    BlockState state(BlockPos position) {
        int index = index(position) * 2;
        return this.palette.get((Byte.toUnsignedInt(this.blocks[index]) << 8) | Byte.toUnsignedInt(this.blocks[index + 1]));
    }

    static SettlementNavigationSection capture(BlockPos origin, LevelChunk chunk) {
        if (!SettlementTerrainSection.sectionOrigin(origin).equals(origin)
                || origin.getX() >> 4 != chunk.getPos().x || origin.getZ() >> 4 != chunk.getPos().z) {
            throw new IllegalArgumentException("Navigation section must belong to its observed chunk");
        }
        var palette = new ArrayList<BlockState>();
        var indexes = new HashMap<BlockState, Integer>();
        byte[] blocks = new byte[8192];
        for (var position : BlockPos.betweenClosed(origin, origin.offset(15, 15, 15))) {
            int value = indexes.computeIfAbsent(chunk.getBlockState(position), state -> {
                palette.add(state);
                return palette.size() - 1;
            });
            int index = index(position) * 2;
            blocks[index] = (byte) (value >> 8);
            blocks[index + 1] = (byte) value;
        }
        return new SettlementNavigationSection(origin, palette, blocks);
    }

    private static int index(BlockPos position) {
        return ((position.getY() & 15) << 8) | ((position.getZ() & 15) << 4) | (position.getX() & 15);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("Origin", this.origin.asLong());
        var palette = new ListTag();
        this.palette.forEach(state -> palette.add(NbtUtils.writeBlockState(state)));
        tag.put("Palette", palette);
        tag.putByteArray("Blocks", this.blocks.clone());
        return tag;
    }

    static SettlementNavigationSection load(CompoundTag tag) {
        if (!tag.contains("Origin", Tag.TAG_LONG) || !tag.contains("Blocks", Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException("Navigation observation requires its origin and blocks");
        }
        var origin = BlockPos.of(tag.getLong("Origin"));
        var blocks = tag.getByteArray("Blocks");
        var palette = new ArrayList<BlockState>();
        for (var raw : tag.getList("Palette", Tag.TAG_COMPOUND)) {
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), (CompoundTag) raw));
        }
        if (!SettlementTerrainSection.sectionOrigin(origin).equals(origin)
                || blocks.length != 8192 || palette.isEmpty() || palette.size() > 4096) {
            throw new IllegalArgumentException("Navigation observation has invalid bounds or palette");
        }
        for (int index = 0; index < blocks.length; index += 2) {
            int value = (Byte.toUnsignedInt(blocks[index]) << 8) | Byte.toUnsignedInt(blocks[index + 1]);
            if (value >= palette.size()) throw new IllegalArgumentException("Navigation observation contains an unknown block");
        }
        return new SettlementNavigationSection(origin, palette, blocks.clone());
    }
}
