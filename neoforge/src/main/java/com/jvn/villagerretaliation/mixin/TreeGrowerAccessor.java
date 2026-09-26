package com.jvn.villagerretaliation.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TreeGrower.class)
public interface TreeGrowerAccessor {
    @Invoker("getConfiguredFeature")
    ResourceKey<ConfiguredFeature<?, ?>> villagerretaliation$configuredFeature(RandomSource random, boolean flowers);

    @Invoker("getConfiguredMegaFeature")
    ResourceKey<ConfiguredFeature<?, ?>> villagerretaliation$configuredMegaFeature(RandomSource random);
}
