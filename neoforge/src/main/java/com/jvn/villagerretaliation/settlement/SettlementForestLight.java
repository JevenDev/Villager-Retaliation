package com.jvn.villagerretaliation.settlement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LightEngine;

final class SettlementForestLight {
    private final SettlementForest forest;
    private final Map<BlockPos, SettlementForestSection> sections;
    private final Map<BlockPos, byte[]> sky = new HashMap<>();
    private final Map<BlockPos, byte[]> block = new HashMap<>();

    SettlementForestLight(SettlementForest forest) {
        this.forest = forest;
        this.sections = forest.sections();
        for (var section : this.sections.values()) {
            if (!section.conflict()) {
                this.sky.put(section.origin(), new byte[section.size()]);
                this.block.put(section.origin(), new byte[section.size()]);
            }
        }
        var skyQueue = new ArrayDeque<BlockPos>();
        var blockQueue = new ArrayDeque<BlockPos>();
        for (var section : forest.sections().values()) {
            if (section.conflict() || !section.environmentKnown()) continue;
            for (BlockPos position : BlockPos.betweenClosed(section.min(), section.max())) {
                int emission = section.state(position).getLightEmission(forest, position);
                if (emission > 0) offer(this.block, blockQueue, position, Math.min(15, emission));
            }
            for (var entry : section.lightSources().entrySet()) {
                if (forest.observed(entry.getKey())) continue;
                var source = entry.getValue();
                for (var direction : Direction.values()) {
                    var target = entry.getKey().relative(direction);
                    if (!forest.observed(target)) continue;
                    spread(this.sky, skyQueue, entry.getKey(), source.state(), target, direction, source.sky(), true);
                    spread(this.block, blockQueue, entry.getKey(), source.state(), target, direction, source.block(), false);
                }
            }
        }
        propagate(this.sky, skyQueue, true);
        propagate(this.block, blockQueue, false);
    }

    int brightness(BlockPos position, int skyDarken) {
        return Math.max(value(this.block, position), value(this.sky, position) - skyDarken);
    }

    private void propagate(Map<BlockPos, byte[]> light, ArrayDeque<BlockPos> queue, boolean sky) {
        while (!queue.isEmpty()) {
            var position = queue.removeFirst();
            int value = value(light, position);
            var state = this.forest.getBlockState(position);
            for (var direction : Direction.values()) {
                var target = position.relative(direction);
                if (this.forest.observed(target)) spread(light, queue, position, state, target, direction, value, sky);
            }
        }
    }

    private void spread(Map<BlockPos, byte[]> light, ArrayDeque<BlockPos> queue, BlockPos position, BlockState state,
                        BlockPos target, Direction direction, int value, boolean sky) {
        if (value - (sky && value == 15 && direction == Direction.DOWN ? 0 : 1) <= value(light, target)) return;
        var targetState = this.forest.getBlockState(target);
        int opacity = targetState.getLightBlock(this.forest, target);
        int attenuation = sky && value == 15 && direction == Direction.DOWN && opacity == 0 ? 0 : Math.max(1, opacity);
        int next = value - LightEngine.getLightBlockInto(this.forest, state, position, targetState, target, direction, attenuation);
        if (next > value(light, target)) offer(light, queue, target, next);
    }

    private int value(Map<BlockPos, byte[]> light, BlockPos position) {
        var section = this.sections.get(SettlementForestSection.sectionOrigin(position));
        var values = section == null ? null : light.get(section.origin());
        return values == null || !section.contains(position) ? 0 : values[section.index(position)];
    }

    private void offer(Map<BlockPos, byte[]> light, ArrayDeque<BlockPos> queue, BlockPos position, int value) {
        if (value <= value(light, position)) return;
        var section = this.sections.get(SettlementForestSection.sectionOrigin(position));
        light.get(section.origin())[section.index(position)] = (byte) value;
        queue.addLast(position.immutable());
    }

    static Map<BlockPos, Source> observe(LevelChunk chunk, BlockPos min, BlockPos max) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) return Map.of();
        var sources = new LinkedHashMap<BlockPos, Source>();
        for (BlockPos position : BlockPos.betweenClosed(min, max)) {
            if (position.getX() > min.getX() && position.getX() < max.getX() && position.getY() > min.getY()
                    && position.getY() < max.getY() && position.getZ() > min.getZ() && position.getZ() < max.getZ()) continue;
            for (var direction : Direction.values()) {
                var outside = position.relative(direction);
                if (inside(outside, min, max) || sources.containsKey(outside)) continue;
                var neighbor = outside.getX() >> 4 == chunk.getPos().x && outside.getZ() >> 4 == chunk.getPos().z
                        ? chunk : level.getChunkSource().getChunkNow(outside.getX() >> 4, outside.getZ() >> 4);
                if (neighbor == null) continue;
                int sky = level.getBrightness(LightLayer.SKY, outside);
                int block = level.getBrightness(LightLayer.BLOCK, outside);
                if (sky > 0 || block > 0) sources.put(outside.immutable(), new Source(neighbor.getBlockState(outside), sky, block));
            }
        }
        return sources;
    }

    static CompoundTag save(Map<BlockPos, Source> sources) {
        var tag = new CompoundTag();
        var palette = new ArrayList<BlockState>();
        var indexes = new HashMap<BlockState, Integer>();
        var positions = new long[sources.size()];
        var states = new int[sources.size()];
        var light = new byte[sources.size()];
        int index = 0;
        for (var entry : sources.entrySet()) {
            positions[index] = entry.getKey().asLong();
            states[index] = indexes.computeIfAbsent(entry.getValue().state(), state -> {
                palette.add(state);
                return palette.size() - 1;
            });
            light[index] = (byte) (entry.getValue().sky() << 4 | entry.getValue().block());
            index++;
        }
        var values = new ListTag();
        palette.forEach(state -> values.add(NbtUtils.writeBlockState(state)));
        tag.put("Palette", values);
        tag.putLongArray("Positions", positions);
        tag.putIntArray("States", states);
        tag.putByteArray("Light", light);
        return tag;
    }

    static Map<BlockPos, Source> load(CompoundTag tag, BlockPos min, BlockPos max) {
        var positions = tag.getLongArray("Positions");
        var states = tag.getIntArray("States");
        var light = tag.getByteArray("Light");
        var palette = new ArrayList<BlockState>();
        for (Tag raw : tag.getList("Palette", Tag.TAG_COMPOUND)) {
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), (CompoundTag) raw));
        }
        if (positions.length > 1536 || positions.length != states.length || positions.length != light.length || palette.size() > positions.length) {
            throw new IllegalArgumentException("Forest light boundary arrays do not match");
        }
        var sources = new LinkedHashMap<BlockPos, Source>();
        for (int index = 0; index < positions.length; index++) {
            var position = BlockPos.of(positions[index]);
            int distance = Math.max(0, min.getX() - position.getX()) + Math.max(0, position.getX() - max.getX())
                    + Math.max(0, min.getY() - position.getY()) + Math.max(0, position.getY() - max.getY())
                    + Math.max(0, min.getZ() - position.getZ()) + Math.max(0, position.getZ() - max.getZ());
            if (distance != 1 || states[index] < 0 || states[index] >= palette.size() || sources.containsKey(position)) {
                throw new IllegalArgumentException("Forest light source must be unique and adjacent to its observed section");
            }
            sources.put(position, new Source(palette.get(states[index]), (light[index] & 255) >> 4, light[index] & 15));
        }
        return sources;
    }

    private static boolean inside(BlockPos position, BlockPos min, BlockPos max) {
        return position.getX() >= min.getX() && position.getX() <= max.getX()
                && position.getY() >= min.getY() && position.getY() <= max.getY()
                && position.getZ() >= min.getZ() && position.getZ() <= max.getZ();
    }

    record Source(BlockState state, int sky, int block) {
    }
}
