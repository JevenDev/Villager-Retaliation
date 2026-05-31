package com.jvn.villagerretaliation.util;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
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
}
