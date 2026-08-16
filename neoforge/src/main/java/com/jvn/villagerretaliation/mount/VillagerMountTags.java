package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class VillagerMountTags {
    public static final TagKey<EntityType<?>> ASSIGNABLE_MOUNTS =
            TagKey.create(Registries.ENTITY_TYPE, VillagerRetaliation.id("assignable_mounts"));

    private VillagerMountTags() {
    }
}
