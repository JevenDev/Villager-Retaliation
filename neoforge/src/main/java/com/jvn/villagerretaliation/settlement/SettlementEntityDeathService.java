package com.jvn.villagerretaliation.settlement;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;

public final class SettlementEntityDeathService {
    private static final Map<LivingEntity, SettlementRecord> OWNERS = new WeakHashMap<>();

    private SettlementEntityDeathService() {
    }

    static void recorded(UUID id) {
        OWNERS.keySet().removeIf(entity -> entity.getUUID().equals(id));
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        for (var death : List.copyOf(town.entityDeaths().values())) {
            if (!death.applied()) {
                var existing = SettlementAnimalBirthService.existing(level, death.id());
                if (existing instanceof LivingEntity living && living.level() instanceof ServerLevel current && matches(death, living)) {
                    apply(current, town, death, living);
                } else if (existing == null && level.areEntitiesLoaded(ChunkPos.asLong(death.position()))) {
                    if (receipt(level, death.position(), deathKey(death.type(), death.id()), death.id(), true)) death.apply();
                }
            }
            for (var experience : List.copyOf(death.experience().values())) {
                if (!delivered(level, experience)) {
                    SettlementGroundExperienceService.submit(level, town, experience.context(level), experience.checkedAt());
                }
                death.removeExperience(experience.id());
            }
            finish(town, death);
            town.changed();
        }
    }

    public static boolean onJoined(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return true;
        var type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (receipt(level, entity.blockPosition(), deathKey(type, entity.getUUID()), entity.getUUID(), false) && entity.isAlive()) {
            entity.discard();
            return false;
        }
        return onActive(entity);
    }

    public static boolean onActive(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return true;
        var town = owner(level, entity);
        if (town == null) return !entity.isRemoved();
        var death = town.entityDeaths().get(entity.getUUID());
        if (matches(death, entity) && (!death.applied() || entity.isAlive())) apply(level, town, death, entity);
        return !entity.isRemoved();
    }

    private static SettlementRecord owner(ServerLevel level, LivingEntity entity) {
        if (OWNERS.containsKey(entity)) {
            var cached = OWNERS.get(entity);
            if (cached == null || cached.entityDeaths().containsKey(entity.getUUID())) return cached;
        }
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (town.entityDeaths().containsKey(entity.getUUID())) {
                OWNERS.put(entity, town);
                return town;
            }
        }
        OWNERS.put(entity, null);
        return null;
    }

    private static boolean matches(SettlementEntityDeath death, LivingEntity entity) {
        return death.id().equals(entity.getUUID()) && death.type().equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    private static void apply(ServerLevel level, SettlementRecord town, SettlementEntityDeath death, LivingEntity entity) {
        boolean current = receipt(level, entity.blockPosition(), deathKey(death.type(), death.id()), death.id(), true);
        var origin = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, town.dimension()));
        if (origin != null && receipt(origin, death.position(), deathKey(death.type(), death.id()), death.id(), true) && current) death.apply();
        if (entity.isAlive()) entity.discard();
        town.removeLivestock(death.id());
        finish(town, death);
        town.changed();
    }

    private static String deathKey(ResourceLocation type, UUID id) {
        return "EntityDeath:" + type + ":" + id;
    }

    private static boolean receipt(ServerLevel level, BlockPos position, String key, UUID id, boolean write) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) return false;
        if (write) SettlementChunkReceipts.applied(chunk, key, id, 1L);
        return SettlementChunkReceipts.version(chunk, key, id) >= 1L;
    }

    private static boolean delivered(ServerLevel level, SettlementEntityDeath.Experience experience) {
        return receipt(level, experience.position(), "DeathExperience:" + experience.id(), experience.id(), false);
    }

    private static void finish(SettlementRecord town, SettlementEntityDeath death) {
        if (death.applied() && death.experience().isEmpty()) town.removeEntityDeath(death.id());
    }
}
