package com.jvn.villagerretaliation.settlement;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class SettlementConstructionPlacement {
    private final UUID id;
    private final List<Part> parts;
    private boolean conflict;
    private final java.util.Map<net.minecraft.world.level.chunk.LevelChunk, Long> appliedRevisions = new java.util.WeakHashMap<>();

    private final java.util.Map<BlockPos, SettlementReceipt> receipts = new java.util.LinkedHashMap<>();

    SettlementConstructionPlacement(ServerLevel level, List<Part> parts) {
        this(parts);
        for (var part : parts) this.receipts.put(part.position(), SettlementReceipt.issue(level, "construction", part.position()));
    }

    SettlementConstructionPlacement(List<Part> parts) {
        this(UUID.randomUUID(), parts);
    }

    private SettlementConstructionPlacement(UUID id, List<Part> parts) {
        if (id == null || parts.isEmpty() || parts.size() > 2
                || parts.stream().map(Part::position).distinct().count() != parts.size()) {
            throw new IllegalArgumentException("Construction placement requires a unique identity and one or two positions");
        }
        this.id = id;
        this.parts = List.copyOf(parts);
    }

    UUID id() {
        return this.id;
    }

    List<Part> parts() {
        return this.parts;
    }

    boolean conflict() {
        return this.conflict;
    }

    boolean reconcile(ServerLevel level, UUID project) {
        for (var part : this.parts) {
            var pos = part.position();
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !level.areEntitiesLoaded(chunk.getPos().toLong())) return false;
            if (SettlementChunkReceipts.version(chunk, key(project, pos), this.id, this.receipts.get(pos)) >= 1L) continue;
            for (int x = pos.getX() - 2 >> 4; x <= pos.getX() + 2 >> 4; x++) {
                for (int z = pos.getZ() - 2 >> 4; z <= pos.getZ() + 2 >> 4; z++) {
                    if (level.getChunkSource().getChunkNow(x, z) == null) return false;
                }
            }
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || chunk.getBlockState(pos) != part.before() || part.before().hasBlockEntity()) {
                this.conflict = true;
                return false;
            }
            if (!part.after().getCollisionShape(chunk, pos).isEmpty()
                    && !level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos), entity -> entity.isAlive() && !entity.isSpectator()).isEmpty()) return false;
        }
        this.conflict = false;
        for (var part : this.parts) {
            var pos = part.position();
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (SettlementChunkReceipts.version(chunk, key(project, pos), this.id, this.receipts.get(pos)) >= 1L) continue;
            chunk.setBlockState(pos, part.after(), false);
            if (chunk.getBlockState(pos) != part.after()) {
                this.conflict = true;
                return false;
            }
            var entity = chunk.getBlockEntity(pos);
            if (entity != null) {
                SettlementConstructionMachines.restore(level, this.id, part, entity);
                SettlementConstructionMachines.delivered(level, project, entity);
                entity.setChanged();
            }
            SettlementMineReactions.queue(chunk, pos, part.before(), chunk.getBlockState(pos));
            SettlementChunkReceipts.applied(chunk, key(project, pos), this.id, 1L, this.receipts.get(pos));
        }
        return true;
    }

    void acknowledgeNative(ServerLevel level, UUID project) {
        for (var part : this.parts) {
            var chunk = level.getChunkAt(part.position());
            SettlementChunkReceipts.applied(chunk, key(project, part.position()), this.id, 1L, this.receipts.get(part.position()));
        }
    }

    boolean durable(ServerLevel level) {
        boolean durable = true;
        for (var part : this.parts) {
            var chunk = level.getChunkSource().getChunkNow(part.position().getX() >> 4, part.position().getZ() >> 4);
            if (chunk == null) return false;
            long revision = this.appliedRevisions.computeIfAbsent(chunk, SettlementChunkCheckpoint::revision);
            durable &= SettlementChunkCheckpoint.durable(level, chunk, revision);
        }
        return durable;
    }

    boolean applied(net.minecraft.world.level.chunk.LevelChunk chunk, UUID project, BlockPos pos) {
        return SettlementChunkReceipts.version(chunk, key(project, pos), this.id, this.receipts.get(pos)) >= 1L;
    }

    private String key(UUID project, BlockPos pos) {
        return "Construction:" + project + ":" + this.id + ":" + pos.asLong();
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putBoolean("Conflict", this.conflict);
        var parts = new ListTag();
        for (var part : this.parts) {
            var entry = new CompoundTag();
            entry.putLong("Position", part.position().asLong());
            if (this.receipts.containsKey(part.position())) entry.put("Receipt", this.receipts.get(part.position()).save());
            entry.put("Before", NbtUtils.writeBlockState(part.before()));
            entry.put("After", NbtUtils.writeBlockState(part.after()));
            if (part.blockEntity() != null) entry.put("BlockEntity", part.blockEntity().copy());
            parts.add(entry);
        }
        tag.put("Parts", parts);
        return tag;
    }

    static SettlementConstructionPlacement load(CompoundTag tag) {
        if (!tag.hasUUID("Id")) throw new IllegalArgumentException("Construction placement identity is missing");
        var parts = new java.util.ArrayList<Part>();
        for (Tag raw : tag.getList("Parts", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.contains("Position", Tag.TAG_LONG) || !entry.contains("Before", Tag.TAG_COMPOUND)
                    || !entry.contains("After", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Construction placement state is missing");
            parts.add(new Part(BlockPos.of(entry.getLong("Position")),
                    NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("Before")),
                    NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("After")),
                    entry.contains("BlockEntity", Tag.TAG_COMPOUND) ? entry.getCompound("BlockEntity") : null));
        }
        var placement = new SettlementConstructionPlacement(tag.getUUID("Id"), parts);
        for (Tag raw : tag.getList("Parts", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (entry.contains("Receipt")) placement.receipts.put(BlockPos.of(entry.getLong("Position")), SettlementReceipt.load(entry.getCompound("Receipt")));
        }
        placement.conflict = tag.getBoolean("Conflict");
        return placement;
    }

    record Part(BlockPos position, BlockState before, BlockState after, CompoundTag blockEntity) {
        Part {
            position = position.immutable();
            blockEntity = blockEntity == null ? null : blockEntity.copy();
            if (before.hasBlockEntity() || before == after || blockEntity != null && !after.hasBlockEntity()) {
                throw new IllegalArgumentException("Construction placement cannot replace an existing block entity or unchanged block");
            }
        }
    }
}
