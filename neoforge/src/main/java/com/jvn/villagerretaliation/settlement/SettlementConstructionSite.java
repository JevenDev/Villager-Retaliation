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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

final class SettlementConstructionSite implements BlockGetter {
    private final UUID project;
    private final SettlementArea area;
    private final Map<BlockPos, SettlementTerrainSection> sections = new LinkedHashMap<>();
    private final Map<UUID, SettlementConstructionPlacement> placements = new LinkedHashMap<>();
    private final Map<BlockPos, BlockState> planned = new LinkedHashMap<>();

    SettlementConstructionSite(UUID project, SettlementArea area) {
        if (project == null || area.type() != SettlementAreaType.CONSTRUCTION) throw new IllegalArgumentException("Construction site requires its project and area");
        this.project = project;
        this.area = area;
    }

    UUID project() {
        return this.project;
    }

    SettlementArea area() {
        return this.area;
    }

    boolean matches(SettlementArea area) {
        return area != null && area.type() == SettlementAreaType.CONSTRUCTION && this.area.id().equals(area.id())
                && this.area.min().equals(area.min()) && this.area.max().equals(area.max());
    }

    Map<BlockPos, SettlementTerrainSection> sections() {
        return Collections.unmodifiableMap(this.sections);
    }

    Map<UUID, SettlementConstructionPlacement> placements() {
        return Collections.unmodifiableMap(this.placements);
    }

    void clearObservations() {
        if (!pending()) {
            this.sections.clear();
            this.planned.clear();
        }
    }

    boolean pending() {
        return !this.placements.isEmpty();
    }

    boolean conflict() {
        return this.placements.values().stream().anyMatch(SettlementConstructionPlacement::conflict);
    }

    BlockPos min() {
        return this.area.min().offset(-2, -2, -2);
    }

    BlockPos max() {
        return this.area.max().offset(2, 2, 2);
    }

    void record(SettlementTerrainSection section) {
        if (!inside(section.min()) || !inside(section.max()) || section.pending()) throw new IllegalArgumentException("Construction observations exceed their area");
        this.sections.put(section.origin(), section);
    }

    void queue(SettlementConstructionPlacement placement) {
        for (var part : placement.parts()) {
            if (!this.area.contains(part.position()) || !observed(part.position()) || getBlockState(part.position()) != part.before()) {
                throw new IllegalArgumentException("Construction requires matching observed terrain inside its area");
            }
        }
        recordPlacement(placement);
    }

    private void recordPlacement(SettlementConstructionPlacement placement) {
        if (placement.parts().stream().anyMatch(part -> !this.area.contains(part.position()) || !observed(part.position()))) {
            throw new IllegalArgumentException("Construction placement exceeds observed bounds");
        }
        if (this.placements.putIfAbsent(placement.id(), placement) != null) throw new IllegalArgumentException("Construction placement identity is duplicated");
        placement.parts().forEach(part -> this.planned.put(part.position(), part.after()));
    }

    void applied(UUID id) {
        this.placements.remove(id);
        this.planned.clear();
        this.placements.values().forEach(placement -> placement.parts().forEach(part -> this.planned.put(part.position(), part.after())));
    }

    boolean observed(BlockPos pos) {
        var section = this.sections.get(SettlementTerrainSection.sectionOrigin(pos));
        return section != null && section.contains(pos);
    }

    private boolean inside(BlockPos pos) {
        return pos.getX() >= min().getX() && pos.getX() <= max().getX() && pos.getY() >= min().getY()
                && pos.getY() <= max().getY() && pos.getZ() >= min().getZ() && pos.getZ() <= max().getZ();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (!observed(pos)) throw new SettlementConstructionView.Unavailable();
        return this.planned.getOrDefault(pos, this.sections.get(SettlementTerrainSection.sectionOrigin(pos)).state(pos));
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        if (getBlockState(pos).hasBlockEntity()) throw new SettlementConstructionView.Unavailable();
        return null;
    }

    @Override
    public int getHeight() {
        return max().getY() - min().getY() + 1;
    }

    @Override
    public int getMinBuildHeight() {
        return min().getY();
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("Area", this.area.save());
        var sections = new ListTag();
        this.sections.values().forEach(section -> sections.add(section.save()));
        tag.put("Sections", sections);
        var placements = new ListTag();
        this.placements.values().forEach(placement -> placements.add(placement.save()));
        tag.put("Placements", placements);
        return tag;
    }

    static SettlementConstructionSite load(UUID project, CompoundTag tag) {
        var site = new SettlementConstructionSite(project, SettlementArea.load(tag.getCompound("Area")));
        for (Tag raw : tag.getList("Sections", Tag.TAG_COMPOUND)) {
            var section = SettlementTerrainSection.load("ConstructionSurvey", project, (CompoundTag) raw);
            if (site.sections.containsKey(section.origin())) throw new IllegalArgumentException("Construction observation duplicates a section");
            site.record(section);
        }
        for (Tag raw : tag.getList("Placements", Tag.TAG_COMPOUND)) site.recordPlacement(SettlementConstructionPlacement.load((CompoundTag) raw));
        return site;
    }
}
