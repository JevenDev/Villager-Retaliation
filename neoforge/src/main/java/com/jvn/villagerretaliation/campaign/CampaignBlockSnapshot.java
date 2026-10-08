package com.jvn.villagerretaliation.campaign;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public record CampaignBlockSnapshot(BlockState state, CompoundTag blockEntity) {
    public CampaignBlockSnapshot {
        CampaignStateCodec.require(state != null && state.hasBlockEntity() == (blockEntity != null), "incomplete campaign block snapshot");
        blockEntity = blockEntity == null ? null : blockEntity.copy();
    }

    @Override public CompoundTag blockEntity() { return blockEntity == null ? null : blockEntity.copy(); }

    public static CampaignBlockSnapshot capture(ServerLevel level, LevelChunk chunk, BlockPos position) {
        BlockState state = chunk.getBlockState(position);
        BlockEntity entity = state.hasBlockEntity() ? chunk.getBlockEntity(position) : null;
        CampaignStateCodec.require(!state.hasBlockEntity() || entity != null, "campaign block entity has not finished loading");
        return new CampaignBlockSnapshot(state, entity == null ? null : entity.saveWithFullMetadata(level.registryAccess()));
    }

    public static CampaignBlockSnapshot defaultState(ServerLevel level, BlockPos position, BlockState state) {
        BlockEntity entity = state.getBlock() instanceof EntityBlock factory ? factory.newBlockEntity(position, state) : null;
        return new CampaignBlockSnapshot(state, entity == null ? null : entity.saveWithFullMetadata(level.registryAccess()));
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("BlockState", NbtUtils.writeBlockState(state));
        if (blockEntity != null) tag.put("BlockEntity", blockEntity.copy());
        return tag;
    }

    static CampaignBlockSnapshot load(CompoundTag tag) {
        CampaignStateCodec.require(tag.getAllKeys().equals(tag.contains("BlockEntity") ? java.util.Set.of("BlockState", "BlockEntity") : java.util.Set.of("BlockState"))
                && tag.contains("BlockState", Tag.TAG_COMPOUND)
                && (!tag.contains("BlockEntity") || tag.contains("BlockEntity", Tag.TAG_COMPOUND)), "invalid campaign block snapshot fields");
        var encoded = tag.getCompound("BlockState");
        var state = BlockState.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        CampaignStateCodec.require(NbtUtils.writeBlockState(state).equals(encoded), "campaign block state is missing or incompatible");
        return new CampaignBlockSnapshot(state, tag.contains("BlockEntity") ? tag.getCompound("BlockEntity") : null);
    }

    boolean includedIn(CompoundTag chunk, BlockPos position) {
        BlockState saved = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (var raw : chunk.getList("sections", Tag.TAG_COMPOUND)) {
            var section = (CompoundTag) raw;
            if (section.getByte("Y") != position.getY() >> 4 || !section.contains("block_states", Tag.TAG_COMPOUND)) continue;
            var blocks = net.minecraft.world.level.chunk.PalettedContainer.codecRW(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY,
                    BlockState.CODEC, net.minecraft.world.level.chunk.PalettedContainer.Strategy.SECTION_STATES,
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
                    .parse(NbtOps.INSTANCE, section.getCompound("block_states")).getOrThrow();
            saved = blocks.get(position.getX() & 15, position.getY() & 15, position.getZ() & 15);
            break;
        }
        if (!saved.equals(state)) return false;
        CompoundTag savedEntity = null;
        for (var raw : chunk.getList("block_entities", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (entry.getInt("x") != position.getX() || entry.getInt("y") != position.getY() || entry.getInt("z") != position.getZ()) continue;
            if (savedEntity != null) return false;
            savedEntity = entry.copy();
            savedEntity.remove("keepPacked");
        }
        return java.util.Objects.equals(blockEntity, savedEntity);
    }

    void validatePosition(BlockPos position) {
        if (blockEntity == null) return;
        CampaignStateCodec.require(blockEntity.contains("id", Tag.TAG_STRING)
                && blockEntity.contains("x", Tag.TAG_INT) && blockEntity.getInt("x") == position.getX()
                && blockEntity.contains("y", Tag.TAG_INT) && blockEntity.getInt("y") == position.getY()
                && blockEntity.contains("z", Tag.TAG_INT) && blockEntity.getInt("z") == position.getZ(), "campaign block entity belongs to another position");
    }
}
