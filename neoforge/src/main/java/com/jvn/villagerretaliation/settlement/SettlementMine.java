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

public final class SettlementMine implements BlockGetter {
    static final int SAFETY_MARGIN = 2;
    private final UUID area;
    private final BlockPos min;
    private final BlockPos max;
    private final Map<BlockPos, SettlementTerrainSection> sections = new LinkedHashMap<>();

    SettlementMine(SettlementArea area) {
        this(area.id(), area.min(), area.max());
    }

    private SettlementMine(UUID area, BlockPos min, BlockPos max) {
        new SettlementArea(area, SettlementAreaType.MINE, "Mine", min, max, 0, true);
        this.area = area;
        this.min = min.immutable();
        this.max = max.immutable();
    }

    public UUID area() {
        return this.area;
    }

    boolean matches(SettlementArea area) {
        return area.id().equals(this.area) && area.type() == SettlementAreaType.MINE
                && area.min().equals(this.min) && area.max().equals(this.max);
    }

    boolean pending() {
        return this.sections.values().stream().anyMatch(SettlementTerrainSection::pending);
    }

    boolean conflict() {
        return this.sections.values().stream().anyMatch(SettlementTerrainSection::conflict);
    }

    Map<BlockPos, SettlementTerrainSection> sections() {
        return Collections.unmodifiableMap(this.sections);
    }

    void record(SettlementTerrainSection section) {
        if (!withinObservations(section.min()) || !withinObservations(section.max())) {
            throw new IllegalArgumentException("Observed mine section exceeds its area");
        }
        this.sections.put(section.origin(), section);
    }

    void remove(BlockPos origin) {
        this.sections.remove(origin);
    }

    BlockPos observedMin() {
        return this.min.offset(-SAFETY_MARGIN, -SAFETY_MARGIN, -SAFETY_MARGIN);
    }

    BlockPos observedMax() {
        return this.max.offset(SAFETY_MARGIN, SAFETY_MARGIN, SAFETY_MARGIN);
    }

    private boolean withinObservations(BlockPos position) {
        var min = observedMin();
        var max = observedMax();
        return position.getX() >= min.getX() && position.getX() <= max.getX()
                && position.getY() >= min.getY() && position.getY() <= max.getY()
                && position.getZ() >= min.getZ() && position.getZ() <= max.getZ();
    }

    BlockPos min() {
        return this.min;
    }

    BlockPos max() {
        return this.max;
    }

    boolean fullyObserved(int minHeight, int maxHeight) {
        if (this.min.getY() < minHeight || this.max.getY() >= maxHeight) return false;
        var observedMin = observedMin();
        var observedMax = observedMax();
        var min = new BlockPos(observedMin.getX(), Math.max(minHeight, observedMin.getY()), observedMin.getZ());
        var max = new BlockPos(observedMax.getX(), Math.min(maxHeight - 1, observedMax.getY()), observedMax.getZ());
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int y = min.getY() >> 4; y <= max.getY() >> 4; y++) {
                for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                    var origin = new BlockPos(x << 4, y << 4, z << 4);
                    var section = this.sections.get(origin);
                    if (section == null || section.conflict()
                            || !section.contains(new BlockPos(Math.max(min.getX(), origin.getX()), Math.max(min.getY(), origin.getY()),
                                    Math.max(min.getZ(), origin.getZ())))
                            || !section.contains(new BlockPos(Math.min(max.getX(), origin.getX() + 15), Math.min(max.getY(), origin.getY() + 15),
                                    Math.min(max.getZ(), origin.getZ() + 15)))) return false;
                }
            }
        }
        return true;
    }

    boolean containsAccess(BlockPos position) {
        return position.getX() >= this.min.getX() - 1 && position.getX() <= this.max.getX() + 1
                && position.getY() >= this.min.getY() && position.getY() <= this.max.getY() + 1
                && position.getZ() >= this.min.getZ() - 1 && position.getZ() <= this.max.getZ() + 1;
    }

    boolean contains(BlockPos position) {
        return position.getX() >= this.min.getX() && position.getX() <= this.max.getX()
                && position.getY() >= this.min.getY() && position.getY() <= this.max.getY()
                && position.getZ() >= this.min.getZ() && position.getZ() <= this.max.getZ();
    }

    boolean observed(BlockPos position) {
        var section = this.sections.get(SettlementTerrainSection.sectionOrigin(position));
        return section != null && section.contains(position) && !section.conflict();
    }

    boolean change(Map<BlockPos, BlockState> changes) {
        var grouped = new LinkedHashMap<SettlementTerrainSection, Map<BlockPos, BlockState>>();
        for (var entry : changes.entrySet()) {
            var section = this.sections.get(SettlementTerrainSection.sectionOrigin(entry.getKey()));
            if (!contains(entry.getKey()) || section == null || !section.canChange(entry.getKey(), entry.getValue())) return false;
            grouped.computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(entry.getKey().immutable(), entry.getValue());
        }
        grouped.forEach(SettlementTerrainSection::change);
        return true;
    }

    boolean changeFloor(BlockPos position, BlockState state) {
        var section = this.sections.get(SettlementTerrainSection.sectionOrigin(position));
        if (position.getY() != this.min.getY() - 1 || position.getX() < this.min.getX() || position.getX() > this.max.getX()
                || position.getZ() < this.min.getZ() || position.getZ() > this.max.getZ() || section == null
                || state.isAir() || !state.getFluidState().isEmpty() || !section.canChange(position, state)
                || !com.jvn.villagerretaliation.interaction.work.mining.MiningHazardManager.canReplaceWithFill(this, this::observed, position)) return false;
        section.change(Map.of(position, state));
        return true;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        var section = this.sections.get(SettlementTerrainSection.sectionOrigin(position));
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
        return observedMax().getY() - observedMin().getY() + 1;
    }

    @Override
    public int getMinBuildHeight() {
        return observedMin().getY();
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Area", this.area);
        tag.putLong("Min", this.min.asLong());
        tag.putLong("Max", this.max.asLong());
        var sections = new ListTag();
        this.sections.values().forEach(section -> sections.add(section.save()));
        tag.put("Sections", sections);
        return tag;
    }

    static SettlementMine load(CompoundTag tag) {
        if (!tag.hasUUID("Area") || !tag.contains("Min", Tag.TAG_LONG) || !tag.contains("Max", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Mine observations require an area and bounds");
        }
        var mine = new SettlementMine(tag.getUUID("Area"), BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")));
        for (Tag raw : tag.getList("Sections", Tag.TAG_COMPOUND)) {
            var section = SettlementTerrainSection.load("Mine", mine.area, (CompoundTag) raw);
            if (mine.sections.containsKey(section.origin())) {
                throw new IllegalArgumentException("Mine observations duplicate a chunk section");
            }
            mine.record(section);
        }
        return mine;
    }
}
