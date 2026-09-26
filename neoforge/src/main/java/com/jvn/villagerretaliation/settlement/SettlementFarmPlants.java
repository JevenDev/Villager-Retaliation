package com.jvn.villagerretaliation.settlement;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

final class SettlementFarmPlants {
    private SettlementFarmPlants() {
    }

    static boolean supported(BlockState state) {
        return state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS)
                || state.is(Blocks.TORCHFLOWER_CROP) || state.is(Blocks.TORCHFLOWER)
                || state.is(Blocks.PITCHER_CROP) && state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER
                || stem(state) || attachedStem(state) || fruit(state) || state.is(Blocks.COCOA);
    }

    static boolean stem(BlockState state) {
        return state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM);
    }

    static boolean attachedStem(BlockState state) {
        return state.is(Blocks.ATTACHED_MELON_STEM) || state.is(Blocks.ATTACHED_PUMPKIN_STEM);
    }

    static boolean fruit(BlockState state) {
        return state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN);
    }

    static boolean harvestable(BlockState state) {
        if (!supported(state)) return false;
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        if (state.is(Blocks.PITCHER_CROP)) return age(state) == PitcherCropBlock.MAX_AGE;
        if (state.is(Blocks.COCOA)) return age(state) == CocoaBlock.MAX_AGE;
        return fruit(state) || state.is(Blocks.TORCHFLOWER);
    }

    static int age(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.getAge(state);
        if (stem(state)) return state.getValue(StemBlock.AGE);
        if (attachedStem(state)) return StemBlock.MAX_AGE;
        if (state.is(Blocks.PITCHER_CROP)) return state.getValue(PitcherCropBlock.AGE);
        if (state.is(Blocks.COCOA)) return state.getValue(CocoaBlock.AGE);
        return 0;
    }

    static BlockState plant(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) || !(stack.getItem() instanceof BlockItem item)) return null;
        var state = item.getBlock().defaultBlockState();
        return supported(state) && (state.getBlock() instanceof CropBlock || stem(state) || state.is(Blocks.PITCHER_CROP)) ? state : null;
    }

    static BlockState fruitFor(BlockState stem) {
        return (stem.is(Blocks.MELON_STEM) || stem.is(Blocks.ATTACHED_MELON_STEM) ? Blocks.MELON : Blocks.PUMPKIN).defaultBlockState();
    }

    static BlockState attachedFor(BlockState stem) {
        return (stem.is(Blocks.MELON_STEM) ? Blocks.ATTACHED_MELON_STEM : Blocks.ATTACHED_PUMPKIN_STEM).defaultBlockState();
    }

    static BlockState detachedFor(BlockState stem) {
        return (stem.is(Blocks.ATTACHED_MELON_STEM) ? Blocks.MELON_STEM : Blocks.PUMPKIN_STEM)
                .defaultBlockState().setValue(StemBlock.AGE, StemBlock.MAX_AGE);
    }
}
