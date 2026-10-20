package com.jvn.villagerretaliation.quest.provider;

import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record QuestProviderBinding(
        ResourceLocation providerType,
        UUID providerId,
        String displayName,
        ResourceLocation professionId,
        int level,
        ResourceKey<Level> dimension,
        BlockPos pos,
        String villageKey,
        Map<VillagerSkill, Integer> skills,
        boolean live
) {
    public QuestProviderBinding {
        displayName = displayName == null ? "" : displayName;
        level = Math.max(0, level);
        pos = pos == null ? null : pos.immutable();
        villageKey = villageKey == null ? "" : villageKey;
        skills = freezeSkills(skills);
    }

    public QuestProviderBinding(ResourceLocation providerType, UUID providerId, String displayName,
            ResourceKey<Level> dimension, BlockPos pos, String groupKey, boolean live) {
        this(providerType, providerId, displayName, null, 0, dimension, pos, groupKey, Map.of(), live);
    }

    public static QuestProviderBinding fromEntity(ResourceLocation providerType,
            net.minecraft.world.entity.Entity entity, String groupKey) {
        java.util.Objects.requireNonNull(providerType, "quest provider type");
        java.util.Objects.requireNonNull(entity, "quest provider entity");
        return new QuestProviderBinding(providerType, entity.getUUID(), entity.getName().getString(),
                entity.level().dimension(), entity.blockPosition(), groupKey, true);
    }

    public static QuestProviderBinding fromSnapshot(QuestProviderSnapshot snapshot) {
        java.util.Objects.requireNonNull(snapshot, "quest provider snapshot");
        return new QuestProviderBinding(snapshot.type(), snapshot.id(), snapshot.name(), snapshot.dimension(),
                snapshot.position(), snapshot.groupKey(), snapshot.live());
    }

    public QuestProviderSnapshot snapshot() {
        return new QuestProviderSnapshot(this.providerType, this.providerId, this.displayName,
                this.dimension, this.pos, this.villageKey, this.live);
    }

    public boolean matchesProviderId(UUID id) {
        return id != null && id.equals(this.providerId);
    }

    public int skillValue(VillagerSkill skill) {
        if (skill == null) {
            return 0;
        }
        return this.skills.getOrDefault(skill, VillagerSkillSet.DEFAULT.get(skill));
    }

    private static Map<VillagerSkill, Integer> freezeSkills(Map<VillagerSkill, Integer> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<VillagerSkill, Integer> copy = new EnumMap<>(VillagerSkill.class);
        for (Map.Entry<VillagerSkill, Integer> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), VillagerSkillSet.clamp(entry.getValue()));
            }
        }
        return copy.isEmpty() ? Map.of() : Collections.unmodifiableMap(copy);
    }
}
