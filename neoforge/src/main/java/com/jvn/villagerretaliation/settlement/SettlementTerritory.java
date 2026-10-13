package com.jvn.villagerretaliation.settlement;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

public final class SettlementTerritory {
    public static final int MAX_CHUNK = 1_874_999;
    private final ChunkPos foundingChunk;
    private final LongLinkedOpenHashSet plots = new LongLinkedOpenHashSet();
    private final Set<SettlementStage> rewards = EnumSet.noneOf(SettlementStage.class);
    private int freeClaims;
    private int paidPurchases;
    private int earnedCapacity;
    private int grandfatheredCapacity;
    private long revision;
    private boolean migrated;
    private String conflict = "";

    SettlementTerritory(BlockPos center) {
        this.foundingChunk = new ChunkPos(center);
        this.earnedCapacity = SettlementStageRules.forStage(SettlementStage.CAMP).plots();
    }

    void start() {
        if (this.migrated) throw new IllegalStateException("Territory is already initialized");
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                this.plots.add(ChunkPos.asLong(this.foundingChunk.x + x, this.foundingChunk.z + z));
            }
        }
        this.rewards.add(SettlementStage.CAMP);
        this.migrated = true;
        this.revision++;
    }

    public ChunkPos foundingChunk() { return this.foundingChunk; }
    public Set<Long> plots() { return Collections.unmodifiableSet(this.plots); }
    public int freeClaims() { return this.freeClaims; }
    public int paidPurchases() { return this.paidPurchases; }
    public int capacity() { return this.earnedCapacity + this.grandfatheredCapacity; }
    public long revision() { return this.revision; }
    public boolean migrated() { return this.migrated; }
    public String conflict() { return this.conflict; }
    public boolean active() { return this.migrated && !this.plots.isEmpty(); }

    public boolean contains(BlockPos position) {
        return contains(Math.floorDiv(position.getX(), 16), Math.floorDiv(position.getZ(), 16));
    }

    public boolean contains(int x, int z) {
        return this.plots.contains(ChunkPos.asLong(x, z));
    }

    public boolean contains(BlockPos min, BlockPos max) {
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) return false;
        int minX = Math.floorDiv(min.getX(), 16), maxX = Math.floorDiv(max.getX(), 16);
        int minZ = Math.floorDiv(min.getZ(), 16), maxZ = Math.floorDiv(max.getZ(), 16);
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > this.plots.size()) return false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!contains(x, z)) return false;
            }
        }
        return true;
    }

    public boolean adjacent(int x, int z) {
        return contains(x - 1, z) || contains(x + 1, z) || contains(x, z - 1) || contains(x, z + 1);
    }

    public static boolean validChunk(int x, int z) {
        return x >= -MAX_CHUNK && x <= MAX_CHUNK && z >= -MAX_CHUNK && z <= MAX_CHUNK;
    }

    void claim(int x, int z, boolean free) {
        if (!active() || !validChunk(x, z) || contains(x, z) || !adjacent(x, z)
                || this.plots.size() >= capacity() || free && this.freeClaims == 0) {
            throw new IllegalStateException("Unvalidated territory claim");
        }
        this.plots.add(ChunkPos.asLong(x, z));
        if (free) this.freeClaims--;
        else this.paidPurchases++;
        this.revision++;
    }

    void autonomousCapacity(int remainingPaidClaims) {
        int desired=Math.addExact(plots.size(),Math.max(0,remainingPaidClaims));
        if(desired>capacity()) {grandfatheredCapacity=Math.addExact(grandfatheredCapacity,desired-capacity());revision++;}
    }
    void advance(SettlementStage stage) {
        refresh(stage);
        if (this.rewards.add(stage)) {
            this.freeClaims = Math.addExact(this.freeClaims, SettlementStageRules.forStage(stage).freePlots());
            this.revision++;
        }
    }

    void refresh(SettlementStage stage) {
        int capacity = SettlementStageRules.forStage(stage).plots();
        if (capacity > this.earnedCapacity) {
            this.earnedCapacity = capacity;
            this.revision++;
        }
    }

    void migrate(Set<Long> owned, SettlementStage stage, String conflict) {
        if (this.migrated) throw new IllegalStateException("Territory migration cannot repeat");
        int entitlement = 9;
        for (var earned : SettlementStage.values()) {
            if (earned.ordinal() > stage.ordinal()) break;
            this.rewards.add(earned);
            entitlement = Math.addExact(entitlement, SettlementStageRules.forStage(earned).freePlots());
        }
        this.plots.addAll(owned);
        this.earnedCapacity = Math.max(this.earnedCapacity, SettlementStageRules.forStage(stage).plots());
        this.grandfatheredCapacity = Math.max(0, this.plots.size() - this.earnedCapacity);
        this.freeClaims = Math.max(0, entitlement - this.plots.size());
        this.conflict = conflict;
        this.migrated = true;
        this.revision++;
    }

    public int envelopeRadius(BlockPos center) {
        long radius = 16;
        for (long packed : this.plots) {
            var chunk = new ChunkPos(packed);
            radius = Math.max(radius, Math.max(Math.abs((long) chunk.getMinBlockX() - center.getX()),
                    Math.abs((long) chunk.getMaxBlockX() - center.getX())));
            radius = Math.max(radius, Math.max(Math.abs((long) chunk.getMinBlockZ() - center.getZ()),
                    Math.abs((long) chunk.getMaxBlockZ() - center.getZ())));
        }
        return Math.toIntExact(radius);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLongArray("Plots", this.plots.stream().mapToLong(Long::longValue).toArray());
        tag.putInt("FreeClaims", this.freeClaims);
        tag.putInt("PaidPurchases", this.paidPurchases);
        tag.putInt("EarnedCapacity", this.earnedCapacity);
        tag.putInt("GrandfatheredCapacity", this.grandfatheredCapacity);
        int mask = 0;
        for (var stage : this.rewards) mask |= 1 << stage.ordinal();
        tag.putInt("Rewards", mask);
        tag.putLong("Revision", this.revision);
        tag.putBoolean("Migrated", this.migrated);
        tag.putString("Conflict", this.conflict);
        return tag;
    }

    static SettlementTerritory load(BlockPos center, CompoundTag tag) {
        var territory = new SettlementTerritory(center);
        territory.freeClaims = tag.getInt("FreeClaims");
        territory.paidPurchases = tag.getInt("PaidPurchases");
        territory.earnedCapacity = tag.getInt("EarnedCapacity");
        territory.grandfatheredCapacity = tag.getInt("GrandfatheredCapacity");
        territory.revision = tag.getLong("Revision");
        territory.migrated = tag.getBoolean("Migrated");
        territory.conflict = tag.getString("Conflict");
        if (territory.freeClaims < 0 || territory.paidPurchases < 0 || territory.earnedCapacity < 9
                || territory.grandfatheredCapacity < 0 || territory.revision < 0
                || (long) territory.earnedCapacity + territory.grandfatheredCapacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid saved settlement territory");
        }
        for (long packed : tag.getLongArray("Plots")) {
            var chunk = new ChunkPos(packed);
            if (!validChunk(chunk.x, chunk.z) || !territory.plots.add(packed)) {
                throw new IllegalArgumentException("Invalid or duplicate saved plot");
            }
        }
        if (territory.plots.size() > territory.capacity()) throw new IllegalArgumentException("Saved plots exceed earned capacity");
        int mask = tag.getInt("Rewards");
        if (mask < 0 || mask >= 1 << SettlementStage.values().length) throw new IllegalArgumentException("Invalid plot rewards");
        for (var stage : SettlementStage.values()) {
            if ((mask & 1 << stage.ordinal()) != 0) territory.rewards.add(stage);
        }
        return territory;
    }
}
