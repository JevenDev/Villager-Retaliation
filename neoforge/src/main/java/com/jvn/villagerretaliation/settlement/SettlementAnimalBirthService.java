package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.ChunkPos;

final class SettlementAnimalBirthService {
    private static final Map<SettlementRecord, Integer> CURSORS = new WeakHashMap<>();

    private SettlementAnimalBirthService() {
    }

    static boolean pending(ServerLevel level, SettlementRecord town, UUID id) {
        var birth = town.animalBirths().get(id);
        if (birth == null || birth.childApplied()) return false;
        var pos = new ChunkPos(birth.position());
        var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk != null && SettlementChunkReceipts.version(chunk, receiptKey(town, birth), birth.id()) >= 1L) {
            birth.applyChild();
            town.changed();
            return false;
        }
        return true;
    }

    private static String receiptKey(SettlementRecord town, SettlementAnimalBirth birth) {
        return "AnimalBirth:" + town.id().value() + ":" + birth.id();
    }

    static void update(ServerLevel level, SettlementRecord town) {
        var births = new ArrayList<>(town.animalBirths().values());
        if (births.isEmpty()) return;
        for (var birth : births) queueExperience(level, town, birth);
        int cursor = Math.floorMod(CURSORS.getOrDefault(town, 0), births.size());
        int count = Math.min(16, births.size());
        for (int index = 0; index < count; index++) materialize(level, town, births.get((cursor + index) % births.size()));
        CURSORS.put(town, (cursor + count) % births.size());
    }

    static void queueExperience(ServerLevel level, SettlementRecord town, SettlementAnimalBirth birth) {
        if (birth.experienceQueued()) return;
        var chunk = level.getChunkSource().getChunkNow(birth.position().getX() >> 4, birth.position().getZ() >> 4);
        boolean delivered = chunk != null && SettlementChunkReceipts.version(chunk, receiptKey(town, birth), birth.id()) >= 2L;
        if (!delivered && birth.experience() > 0) {
            var location = birth.position().getCenter();
            var orb = new ExperienceOrb(level, location.x, location.y, location.z, birth.experience());
            orb.setUUID(birth.experienceId());
            SettlementGroundExperienceService.submit(level, town, orb, birth.createdAt(level.getGameTime()));
        }
        birth.queueExperience();
        town.changed();
    }

    static boolean materialize(ServerLevel level, SettlementRecord town, SettlementAnimalBirth birth) {
        if (!town.dimension().equals(level.dimension().location())) return false;
        var pos = new ChunkPos(birth.position());
        var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        String key = receiptKey(town, birth);
        long version = chunk == null ? 0L : SettlementChunkReceipts.version(chunk, key, birth.id());
        if (version >= 2L) {
            town.removeAnimalBirth(birth.id());
            return true;
        }
        queueExperience(level, town, birth);
        if (chunk == null || !level.areEntitiesLoaded(pos.toLong())) return false;
        if (version >= 1L) birth.applyChild();
        if (!birth.childApplied()) {
            var existing = existing(level, birth.id());
            if (existing == null) {
                var state = town.livestock().get(birth.id());
                Entity child = EntityType.loadEntityRecursive(birth.entity(), level, entity -> entity);
                if (state != null && child instanceof net.minecraft.world.entity.animal.Animal animal) state.restore(animal);
                if (child == null || !child.getSelfAndPassengers().allMatch(entity -> safe(level, entity))) return false;
                if (!level.tryAddFreshEntityWithPassengers(child)) return false;
            }
            birth.applyChild();
            SettlementChunkReceipts.applied(chunk, key, birth.id(), 1L);
            town.changed();
        }
        var experience = town.groundExperience().get(birth.experienceId());
        if (experience != null) SettlementGroundExperienceService.materialize(level, town, experience);
        experience = town.groundExperience().get(birth.experienceId());
        if (experience != null && experience.pendingSpawn() && !experience.removed()) return false;
        SettlementChunkReceipts.applied(chunk, key, birth.id(), 2L);
        town.removeAnimalBirth(birth.id());
        return true;
    }

    static Entity existing(ServerLevel level, UUID id) {
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            var entity = dimension.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static boolean safe(ServerLevel level, Entity entity) {
        var box = entity.getBoundingBox();
        var min = BlockPos.containing(box.minX, box.minY, box.minZ);
        var max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        if (level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
                || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max)) return false;
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null || !level.areEntitiesLoaded(ChunkPos.asLong(x, z))) return false;
            }
        }
        return level.noCollision(entity, box);
    }
}
