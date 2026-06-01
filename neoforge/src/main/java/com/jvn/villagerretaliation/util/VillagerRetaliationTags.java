package com.jvn.villagerretaliation.util;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public final class VillagerRetaliationTags {
    private VillagerRetaliationTags() {
    }

    public static final class Blocks {
        public static final TagKey<Block> WATCHED_CONTAINERS = TagKey.create(
                Registries.BLOCK,
                VillagerRetaliation.id("watched_containers"));

        private Blocks() {
        }
    }

    public static final class EntityTypes {
        public static final TagKey<EntityType<?>> NATURAL_HOSTILE_TARGETS = TagKey.create(
                Registries.ENTITY_TYPE,
                VillagerRetaliation.id("natural_hostile_targets"));

        public static final TagKey<EntityType<?>> IGNORED_NATURAL_HOSTILE_TARGETS = TagKey.create(
                Registries.ENTITY_TYPE,
                VillagerRetaliation.id("ignored_natural_hostile_targets"));

        private EntityTypes() {
        }
    }
}
