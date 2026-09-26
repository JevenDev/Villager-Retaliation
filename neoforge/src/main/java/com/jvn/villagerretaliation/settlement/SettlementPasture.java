package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.event.EventHooks;

final class SettlementPasture implements BlockGetter {
    private final ServerLevel level;
    private final SettlementRecord town;
    private final UUID area;

    private SettlementPasture(ServerLevel level, SettlementRecord town, UUID area) {
        this.level = level;
        this.town = town;
        this.area = area;
    }

    static void advance(ServerLevel level, SettlementRecord town, SettlementFieldPlot source, double elapsed, RandomSource random) {
        var area = town.areas().get(source.area());
        if (area == null || !area.enabled() || area.type() != SettlementAreaType.ANIMAL_PEN) return;
        int ticks = source.grassTicks(elapsed);
        if (ticks == 0) return;
        var pasture = new SettlementPasture(level, town, source.area());
        for (int tick = 0; tick < ticks && source.soil().is(Blocks.GRASS_BLOCK); tick++) {
            if (!pasture.canBeGrass(source, false)) {
                source.change(Blocks.DIRT.defaultBlockState(), source.crop());
                break;
            }
            if (source.brightness(level.getSkyDarken()) < 9) continue;
            for (int attempt = 0; attempt < 4; attempt++) {
                BlockPos position = source.position().offset(random.nextInt(3) - 1,
                        random.nextInt(5) - 3, random.nextInt(3) - 1);
                var target = town.fields().get(position);
                if (target == null || !target.area().equals(source.area())
                        || !area.contains(position) && !area.contains(position.above()) || !SettlementFieldService.available(level, target)
                        || !target.soil().is(Blocks.DIRT) || !pasture.canBeGrass(target, true)) continue;
                target.change(Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY, target.crop().is(Blocks.SNOW)),
                        target.crop());
                SettlementFieldService.applyLoaded(level, target);
            }
        }
    }

    static void graze(ServerLevel level, SettlementRecord town, SettlementLivestock state, int elapsed) {
        if (elapsed <= 0) return;
        var area = town.areas().get(state.area());
        if (state.unsafe() || area == null || !area.enabled() || area.type() != SettlementAreaType.ANIMAL_PEN
                || !area.contains(state.position()) || !(state.context(level) instanceof Sheep sheep) || sheep.isNoAi() || !sheep.isAlive()) {
            state.resetGrazing();
            return;
        }
        var plot = town.fields().get(state.position().below());
        if (!edible(level, plot, state)) {
            state.resetGrazing();
            return;
        }
        int ticks = state.grazingTicks(elapsed, sheep.isBaby());
        for (int tick = 0; tick < ticks; tick++) {
            if (!edible(level, plot, state)) {
                state.resetGrazing();
                break;
            }
            if (EventHooks.canEntityGrief(level, sheep)) {
                if (plot.crop().is(Blocks.SHORT_GRASS)) plot.change(plot.soil(), Blocks.AIR.defaultBlockState());
                else plot.change(Blocks.DIRT.defaultBlockState(), plot.crop());
                SettlementFieldService.applyLoaded(level, plot);
            }
            state.ate(sheep);
        }
    }

    private static boolean edible(ServerLevel level, SettlementFieldPlot plot, SettlementLivestock animal) {
        return plot != null && plot.area().equals(animal.area()) && SettlementFieldService.available(level, plot)
                && (plot.crop().is(Blocks.SHORT_GRASS) || plot.soil().is(Blocks.GRASS_BLOCK));
    }

    private boolean canBeGrass(SettlementFieldPlot plot, boolean spreading) {
        var cover = plot.crop();
        if (spreading && cover.getFluidState().is(FluidTags.WATER)) return false;
        if (cover.is(Blocks.SNOW) && cover.getValue(SnowLayerBlock.LAYERS) == 1) return true;
        if (cover.getFluidState().getAmount() == 8) return false;
        return LightEngine.getLightBlockInto(this, Blocks.GRASS_BLOCK.defaultBlockState(), plot.position(), cover,
                plot.position().above(), Direction.UP, cover.getLightBlock(this, plot.position().above())) < 15;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        var plot = this.town.fields().get(position);
        if (plot != null && plot.area().equals(this.area) && !plot.conflict()) return plot.soil();
        plot = this.town.fields().get(position.below());
        if (plot != null && plot.area().equals(this.area) && !plot.conflict()) return plot.crop();
        return Blocks.BARRIER.defaultBlockState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        return null;
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }
}
