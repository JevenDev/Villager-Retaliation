package com.jvn.villagerretaliation.settlement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class SettlementForest implements BlockGetter {
    private final UUID area;
    private final BlockPos min;
    private final BlockPos max;
    private final Map<BlockPos, SettlementForestSection> sections = new LinkedHashMap<>();
    private SettlementForestLight light;
    private int growthCursor;

    SettlementForest(SettlementArea area) {
        this(area.id(), area.min(), area.max());
    }

    private SettlementForest(UUID area, BlockPos min, BlockPos max) {
        new SettlementArea(area, SettlementAreaType.FORESTRY, "Forest", min, max, 0, true);
        this.area = area;
        this.min = min.immutable();
        this.max = max.immutable();
    }

    public UUID area() {
        return this.area;
    }

    boolean ownedBy(SettlementRecord town) {
        return town.contains(this.min, this.max);
    }

    boolean matches(SettlementArea area) {
        return area.id().equals(this.area) && area.type() == SettlementAreaType.FORESTRY
                && area.min().equals(this.min) && area.max().equals(this.max);
    }

    boolean pending() {
        return this.sections.values().stream().anyMatch(SettlementForestSection::pending);
    }

    boolean conflict() {
        return this.sections.values().stream().anyMatch(SettlementForestSection::conflict);
    }

    Map<BlockPos, SettlementForestSection> sections() {
        return Collections.unmodifiableMap(this.sections);
    }

    void record(SettlementForestSection section) {
        if (!contains(section.min()) || !contains(section.max())) {
            throw new IllegalArgumentException("Observed forest section exceeds its area");
        }
        this.sections.put(section.origin(), section);
        invalidateLight();
    }

    void remove(BlockPos origin) {
        this.sections.remove(origin);
        invalidateLight();
    }

    boolean contains(BlockPos position) {
        return position.getX() >= this.min.getX() && position.getX() <= this.max.getX()
                && position.getY() >= this.min.getY() && position.getY() <= this.max.getY()
                && position.getZ() >= this.min.getZ() && position.getZ() <= this.max.getZ();
    }

    boolean observed(BlockPos position) {
        var section = this.sections.get(SettlementForestSection.sectionOrigin(position));
        return section != null && section.contains(position) && !section.conflict();
    }

    boolean change(Map<BlockPos, BlockState> changes) {
        return change(changes, java.util.List.of());
    }

    boolean change(Map<BlockPos, BlockState> changes, java.util.List<SettlementTerrainTick> ticks) {
        for (var tick : ticks) {
            if (!observed(tick.position())) return false;
        }
        var grouped = new LinkedHashMap<SettlementForestSection, Map<BlockPos, BlockState>>();
        for (var entry : changes.entrySet()) {
            var section = this.sections.get(SettlementForestSection.sectionOrigin(entry.getKey()));
            if (section == null || !section.canChange(entry.getKey(), entry.getValue())) return false;
            grouped.computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(entry.getKey().immutable(), entry.getValue());
        }
        boolean lightChanged = changes.entrySet().stream().anyMatch(entry -> net.minecraft.world.level.lighting.LightEngine
                .hasDifferentLightProperties(this, entry.getKey(), getBlockState(entry.getKey()), entry.getValue()));
        grouped.forEach(SettlementForestSection::change);
        if (lightChanged) invalidateLight();
        for (var tick : ticks) this.sections.get(SettlementForestSection.sectionOrigin(tick.position())).schedule(tick);
        return true;
    }

    int brightness(BlockPos position, int skyDarken) {
        if (this.light == null) this.light = new SettlementForestLight(this);
        return this.light.brightness(position, skyDarken);
    }

    void invalidateLight() {
        this.light = null;
    }

    int growthCursor() {
        return this.growthCursor;
    }

    void growthCursor(int cursor) {
        this.growthCursor = Math.max(0, cursor);
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        var section = this.sections.get(SettlementForestSection.sectionOrigin(position));
        return section != null && section.contains(position) ? section.state(position) : Blocks.BARRIER.defaultBlockState();
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
        return this.max.getY() - this.min.getY() + 1;
    }

    @Override
    public int getMinBuildHeight() {
        return this.min.getY();
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Area", this.area);
        tag.putLong("Min", this.min.asLong());
        tag.putLong("Max", this.max.asLong());
        tag.putInt("GrowthCursor", this.growthCursor);
        var sections = new ListTag();
        this.sections.values().forEach(section -> sections.add(section.save()));
        tag.put("Sections", sections);
        return tag;
    }

    static SettlementForest load(CompoundTag tag) {
        if (!tag.hasUUID("Area") || !tag.contains("Min", Tag.TAG_LONG) || !tag.contains("Max", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Forest observations require an area and bounds");
        }
        var forest = new SettlementForest(tag.getUUID("Area"), BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")));
        for (Tag raw : tag.getList("Sections", Tag.TAG_COMPOUND)) {
            var section = SettlementForestSection.load(forest.area, (CompoundTag) raw);
            if (forest.sections.containsKey(section.origin())) {
                throw new IllegalArgumentException("Forest observations duplicate a chunk section");
            }
            forest.record(section);
        }
        forest.growthCursor = Math.max(0, tag.getInt("GrowthCursor"));
        return forest;
    }
}
