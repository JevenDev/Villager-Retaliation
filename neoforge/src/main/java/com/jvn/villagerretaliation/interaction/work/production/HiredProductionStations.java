package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.saveddata.SavedData;

public final class HiredProductionStations extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_production_stations";
    private static final int MAX_CLAIMS = 4096;
    private final Map<BlockPos, Claim> claims = new LinkedHashMap<>();
    private CompoundTag rejected;

    public static HiredProductionStations get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(HiredProductionStations::new,
                HiredProductionStations::load, DataFixTypes.LEVEL), DATA_NAME);
    }

    public boolean readOnly() {
        return this.rejected != null;
    }

    public Claim claim(ServerLevel level, BlockPos position) {
        var claim = this.claims.get(position);
        if (claim == null || readOnly()) return claim;
        if (claim.scope() == HiredProductionQueue.Scope.WORKSITE && SettlementSavedData.get(level).readOnly()) return claim;
        var queue = queue(level, claim);
        if (!claim.started() && claim.expires() <= level.getGameTime()
                || queue != null && queue.allocations().stream().noneMatch(allocation -> allocation.id().equals(claim.allocation()))
                || claim.scope() == HiredProductionQueue.Scope.WORKSITE && queue == null) {
            this.claims.remove(position);
            setDirty();
            return null;
        }
        return claim;
    }

    public Claim reconcile(ServerLevel level, BlockPos position, UUID machine) {
        var claim = claim(level, position);
        if (claim != null && !readOnly() && !claim.machine().equals(machine)) {
            var queue = queue(level, claim);
            if (queue != null) queue.lost(claim.allocation());
            release(claim.allocation());
            return null;
        }
        return claim;
    }

    public boolean available(ServerLevel level, BlockPos position) {
        return !readOnly() && claim(level, position) == null;
    }

    public boolean reserve(ServerLevel level, HiredProductionQueue queue, HiredProductionQueue.Allocation allocation, UUID machine) {
        if (readOnly() || allocation == null || allocation.station() == null || allocation.worker() == null || machine == null
                || !allocation.equals(queue.allocation(allocation.worker()))) return false;
        var existing = claim(level, allocation.station());
        if (existing != null && !existing.machine().equals(machine)) {
            var previous = queue(level, existing);
            if (existing.queue().equals(queue.owner()) && existing.scope() == queue.scope()) previous = queue;
            if (previous != null) previous.lost(existing.allocation());
            this.claims.remove(allocation.station());
            setDirty();
            if (existing.allocation().equals(allocation.id())) return false;
            existing = null;
        }
        if (existing != null && (!existing.queue().equals(queue.owner()) || !existing.allocation().equals(allocation.id()))) return false;
        if (existing == null && this.claims.size() >= MAX_CLAIMS) return false;
        var updated = new Claim(queue.owner(), queue.scope(), allocation.id(), allocation.worker(), machine,
                allocation.started(), Math.max(0L, level.getGameTime()) + 200L);
        this.claims.put(allocation.station(), updated);
        setDirty();
        return true;
    }

    public void release(UUID allocation) {
        if (!readOnly() && this.claims.values().removeIf(claim -> claim.allocation().equals(allocation))) setDirty();
    }

    public void workerRemoved(UUID worker) {
        if (!readOnly() && this.claims.values().removeIf(claim -> claim.worker().equals(worker)
                && (claim.scope() == HiredProductionQueue.Scope.PERSONAL || !claim.started()))) setDirty();
    }

    private static HiredProductionQueue queue(ServerLevel level, Claim claim) {
        if (claim.scope() == HiredProductionQueue.Scope.WORKSITE) {
            for (var town : SettlementSavedData.get(level).settlements()) {
                if (town.dimension().equals(level.dimension().location())) {
                    var queue = town.productionQueues().get(claim.queue());
                    if (queue != null) return queue;
                }
            }
        } else if (level.getEntity(claim.worker()) instanceof Villager villager) {
            var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager);
            if (state.contains(HiredProductionService.STATE_TAG, Tag.TAG_COMPOUND)) {
                try {
                    var queue = HiredProductionQueue.load(state.getCompound(HiredProductionService.STATE_TAG), level.registryAccess(),
                            updated -> state.put(HiredProductionService.STATE_TAG, updated.save(level.registryAccess())));
                    if (queue.owner().equals(claim.queue()) && queue.scope() == claim.scope()) return queue;
                } catch (IllegalArgumentException exception) {
                    return null;
                }
            }
        }
        return null;
    }

    public static HiredProductionStations load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new HiredProductionStations();
        try {
            if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                    || !(tag.get("Claims") instanceof ListTag entries) || entries.size() > MAX_CLAIMS
                    || !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("Invalid production station ledger");
            }
            for (Tag raw : entries) {
                var entry = (CompoundTag) raw;
                if (!entry.contains("Position", Tag.TAG_LONG) || !entry.hasUUID("Queue") || !entry.hasUUID("Allocation")
                        || !entry.hasUUID("Worker") || !entry.hasUUID("Machine") || !entry.contains("Scope", Tag.TAG_STRING)
                        || !entry.contains("Started", Tag.TAG_BYTE) || !entry.contains("Expires", Tag.TAG_LONG)) {
                    throw new IllegalArgumentException("Invalid production station claim");
                }
                var claim = new Claim(entry.getUUID("Queue"), HiredProductionQueue.Scope.valueOf(entry.getString("Scope")),
                        entry.getUUID("Allocation"), entry.getUUID("Worker"), entry.getUUID("Machine"),
                        entry.getBoolean("Started"), entry.getLong("Expires"));
                if (data.claims.putIfAbsent(BlockPos.of(entry.getLong("Position")), claim) != null
                        || data.claims.values().stream().filter(other -> other.allocation().equals(claim.allocation())).count() > 1L) {
                    throw new IllegalArgumentException("Duplicate production station claim");
                }
            }
        } catch (IllegalArgumentException exception) {
            data.claims.clear();
            data.rejected = tag.copy();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (readOnly()) return this.rejected.copy();
        tag.putInt("Version", 1);
        var entries = new ListTag();
        this.claims.forEach((position, claim) -> {
            var entry = new CompoundTag();
            entry.putLong("Position", position.asLong());
            entry.putUUID("Queue", claim.queue());
            entry.putString("Scope", claim.scope().name());
            entry.putUUID("Allocation", claim.allocation());
            entry.putUUID("Worker", claim.worker());
            entry.putUUID("Machine", claim.machine());
            entry.putBoolean("Started", claim.started());
            entry.putLong("Expires", claim.expires());
            entries.add(entry);
        });
        tag.put("Claims", entries);
        return tag;
    }

    public record Claim(UUID queue, HiredProductionQueue.Scope scope, UUID allocation, UUID worker, UUID machine, boolean started, long expires) {
        public Claim {
            Objects.requireNonNull(queue);
            Objects.requireNonNull(scope);
            Objects.requireNonNull(allocation);
            Objects.requireNonNull(worker);
            Objects.requireNonNull(machine);
            if (expires < 0L) throw new IllegalArgumentException("Invalid station claim expiry");
        }
    }
}
