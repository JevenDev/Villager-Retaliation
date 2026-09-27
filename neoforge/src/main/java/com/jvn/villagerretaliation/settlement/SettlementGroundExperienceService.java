package com.jvn.villagerretaliation.settlement;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SettlementGroundExperienceService {
    private static final Map<ExperienceOrb, SettlementRecord> OWNERS = new WeakHashMap<>();
    private static final Map<SettlementArea, Integer> CURSORS = new WeakHashMap<>();

    private SettlementGroundExperienceService() {
    }

    static void observeArea(ServerLevel level, SettlementRecord town, SettlementArea area) {
        var bounds = new AABB(area.min().getX(), area.min().getY(), area.min().getZ(),
                area.max().getX() + 1.0D, area.max().getY() + 1.0D, area.max().getZ() + 1.0D);
        var orbs = level.getEntitiesOfClass(ExperienceOrb.class, bounds, orb -> orb.isAlive() && orb.getValue() > 0);
        if (orbs.isEmpty()) return;
        int cursor = Math.floorMod(CURSORS.getOrDefault(area, 0), orbs.size());
        int count = Math.min(64, orbs.size());
        for (int index = 0; index < count; index++) observe(level, town, orbs.get((cursor + index) % orbs.size()));
        CURSORS.put(area, (cursor + count) % orbs.size());
    }

    private static void observe(ServerLevel level, SettlementRecord town, ExperienceOrb orb) {
        if (!town.dimension().equals(level.dimension().location()) || orb.level() != level
                || !orb.isAlive() || orb.getValue() <= 0 || !readReceipt(level, orb)) return;
        var previous = owner(level, orb);
        if (previous != null && previous != town) return;
        var state = town.groundExperience().get(orb.getUUID());
        if (state == null) {
            state = SettlementGroundExperience.capture(orb, level.getGameTime());
            town.recordGroundExperience(state);
        } else {
            state = synchronize(level, town, state);
            apply(level, town, state, orb);
            if (orb.isRemoved()) return;
            state.observe(orb, level.isPositionEntityTicking(orb.blockPosition()) ? level.getGameTime() : state.checkedAt());
        }
        OWNERS.put(orb, town);
        writeReceipt(level, state, state.position());
        town.changed();
    }

    public static void onChanged(ExperienceOrb orb) {
        if (!(orb.level() instanceof ServerLevel level) || !orb.isAlive() || orb.getValue() <= 0) return;
        var town = owner(level, orb);
        if (town != null && town.areas().values().stream().noneMatch(area -> area.contains(orb.blockPosition()))) {
            var state = synchronize(level, town, town.groundExperience().get(orb.getUUID()));
            apply(level, town, state, orb);
            if (orb.isRemoved()) return;
            town.removeGroundExperience(orb.getUUID());
            OWNERS.remove(orb);
            town = null;
        }
        if (town == null) town = at(level, orb.blockPosition());
        if (town != null) observe(level, town, orb);
    }

    public static void beforeAward(ServerLevel level, Vec3 position) {
        for (var orb : level.getEntitiesOfClass(ExperienceOrb.class, AABB.ofSize(position, 1.0D, 1.0D, 1.0D))) onActive(orb);
    }

    public static void onAwarded(ServerLevel level, Vec3 position) {
        for (var orb : level.getEntitiesOfClass(ExperienceOrb.class, AABB.ofSize(position, 1.0D, 1.0D, 1.0D))) onChanged(orb);
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        for (var original : List.copyOf(town.groundExperience().values())) {
            var state = synchronize(level, town, original);
            var entity = SettlementAnimalBirthService.existing(level, state.id());
            if (entity instanceof ExperienceOrb orb && orb.level() == level) {
                if (!readReceipt(level, orb)) {
                    town.removeGroundExperience(state.id());
                    continue;
                }
                apply(level, town, state, orb);
                if (orb.isRemoved()) continue;
                boolean active = level.isPositionEntityTicking(orb.blockPosition());
                state.observe(orb, active ? now : state.checkedAt());
                if (!active) {
                    state.advance(now);
                    apply(level, town, state, orb);
                }
                if (town.areas().values().stream().noneMatch(area -> area.contains(orb.blockPosition()))) {
                    town.removeGroundExperience(state.id());
                    OWNERS.remove(orb);
                } else {
                    writeReceipt(level, state, state.position());
                }
            } else if (entity != null) {
                town.removeGroundExperience(state.id());
            } else if (level.areEntitiesLoaded(ChunkPos.asLong(state.position()))) {
                state.remove();
                remove(level, town, state);
            } else {
                state.advance(now);
            }
            town.changed();
        }
    }

    public static boolean onActive(ExperienceOrb orb) {
        if (!(orb.level() instanceof ServerLevel level)) return true;
        var town = owner(level, orb);
        if (town != null) {
            var state = synchronize(level, town, town.groundExperience().get(orb.getUUID()));
            apply(level, town, state, orb);
        }
        return !orb.isRemoved();
    }

    public static boolean onJoined(ExperienceOrb orb) {
        if (!(orb.level() instanceof ServerLevel level)) return true;
        return readReceipt(level, orb) && onActive(orb);
    }

    public static void onUnloaded(ExperienceOrb orb) {
        if (!(orb.level() instanceof ServerLevel level)) return;
        var town = owner(level, orb);
        OWNERS.remove(orb);
        var reason = orb.getRemovalReason();
        if (town != null && (reason == null || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK)
                && town.areas().values().stream().noneMatch(area -> area.contains(orb.blockPosition()))) {
            var state = synchronize(level, town, town.groundExperience().get(orb.getUUID()));
            apply(level, town, state, orb);
            if (state.removed()) return;
            town.removeGroundExperience(orb.getUUID());
            town = null;
        }
        if (town == null && orb.getValue() > 0 && (reason == null || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK)) {
            town = at(level, orb.blockPosition());
            if (town != null) town.recordGroundExperience(SettlementGroundExperience.capture(orb, level.getGameTime()));
        }
        if (town == null) return;
        var state = synchronize(level, town, town.groundExperience().get(orb.getUUID()));
        if (reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            town.removeGroundExperience(orb.getUUID());
        } else if (state.removed() || reason != null && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            state.remove();
            writeReceipt(level, state, orb.blockPosition());
            remove(level, town, state);
        } else {
            apply(level, town, state, orb);
            if (!state.removed()) {
                state.observe(orb, level.getGameTime());
                writeReceipt(level, state, state.position());
                town.changed();
            }
        }
    }

    private static SettlementRecord at(ServerLevel level, BlockPos position) {
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (town.dimension().equals(level.dimension().location())
                    && town.areas().values().stream().anyMatch(area -> area.contains(position))) return town;
        }
        return null;
    }

    private static SettlementRecord owner(ServerLevel level, ExperienceOrb orb) {
        if (OWNERS.containsKey(orb)) {
            var cached = OWNERS.get(orb);
            if (cached == null || cached.groundExperience().containsKey(orb.getUUID())) return cached;
        }
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (town.dimension().equals(level.dimension().location()) && town.groundExperience().containsKey(orb.getUUID())) {
                OWNERS.put(orb, town);
                return town;
            }
        }
        OWNERS.put(orb, null);
        return null;
    }

    private static LevelChunk chunk(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
    }

    private static SettlementGroundExperience synchronize(ServerLevel level, SettlementRecord town, SettlementGroundExperience state) {
        var chunk = chunk(level, state.position());
        if (chunk == null) return state;
        var tag = SettlementChunkReceipts.groundExperience(chunk, false).getCompound(state.id().toString());
        if (tag.isEmpty() || tag.getLong("Version") < state.version()
                || tag.getLong("Version") == state.version() && !tag.getBoolean("Removed")) return state;
        var receipt = SettlementGroundExperience.load(tag);
        if (!receipt.id().equals(state.id())) throw new IllegalStateException("Ground experience receipt identity differs");
        town.removeGroundExperience(state.id());
        town.recordGroundExperience(receipt);
        return receipt;
    }

    private static boolean readReceipt(ServerLevel level, ExperienceOrb orb) {
        var chunk = chunk(level, orb.blockPosition());
        if (chunk == null) return !orb.isRemoved();
        var tag = SettlementChunkReceipts.groundExperience(chunk, false).getCompound(orb.getUUID().toString());
        if (!tag.isEmpty()) SettlementGroundExperience.load(tag).restore(orb);
        return !orb.isRemoved();
    }

    private static boolean writeReceipt(ServerLevel level, SettlementGroundExperience state, BlockPos position) {
        var chunk = chunk(level, position);
        if (chunk == null) return false;
        var entries = SettlementChunkReceipts.groundExperience(chunk, true);
        String key = state.id().toString();
        if (entries.getCompound(key).getLong("Version") > state.version()) return false;
        entries.put(key, state.save());
        chunk.setUnsaved(true);
        return true;
    }

    private static void apply(ServerLevel level, SettlementRecord town, SettlementGroundExperience state, ExperienceOrb orb) {
        if (SettlementGroundExperience.version(orb) > state.version()
                || SettlementGroundExperience.version(orb) == state.version() && !state.removed()) return;
        writeReceipt(level, state, orb.blockPosition());
        state.restore(orb);
        if (state.removed()) remove(level, town, state);
        town.changed();
    }

    private static void remove(ServerLevel level, SettlementRecord town, SettlementGroundExperience state) {
        if (writeReceipt(level, state, state.position())) town.removeGroundExperience(state.id());
        town.changed();
    }
}
