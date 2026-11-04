package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementDistrictRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementPathResultPayload;
import com.jvn.villagerretaliation.settlement.SettlementPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class SettlementDistrictDraft {
    record Path(UUID id, SettlementPath path, List<SettlementPathResultPayload.Cell> cells) {}
    private final List<SettlementBuildingDraft> buildings = new ArrayList<>();
    private final List<Path> paths = new ArrayList<>();
    private UUID request = UUID.randomUUID();
    private UUID rendered;
    private com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewData ghost;
    private boolean pathsFirst = true;
    boolean pathsFirst() { return this.pathsFirst; }
    void pathsFirst(boolean enabled) { this.pathsFirst = enabled; this.request = UUID.randomUUID(); }
    List<SettlementBuildingDraft> buildings() { return List.copyOf(this.buildings); }
    List<Path> paths() { return List.copyOf(this.paths); }
    int size() { return this.buildings.size() + this.paths.size(); }
    boolean full() { return size() >= SettlementDistrictRequestPayload.MAX_PROJECTS; }
    boolean canAdd(SettlementBuildingDraft building) {
        return building.ready() && !full() && !this.buildings.contains(building)
                && this.buildings.stream().mapToLong(b -> b.preview.blocks().size()).sum() + building.preview.blocks().size() <= 65_536;
    }
    void add(SettlementBuildingDraft building) {
        if (!canAdd(building)) throw new IllegalStateException("Building is not ready for the district");
        this.buildings.add(building); this.request = UUID.randomUUID();
    }
    void add(SettlementPath path, List<SettlementPathResultPayload.Cell> cells) {
        if (full()) throw new IllegalStateException("District is full");
        this.paths.add(new Path(UUID.randomUUID(), path, List.copyOf(cells))); this.request = UUID.randomUUID();
    }
    com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewData ghost() {
        if (this.buildings.isEmpty()) return null;
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null) return null;
        if (this.request.equals(this.rendered) && this.ghost != null && this.ghost.dimension().equals(minecraft.level.dimension())) return this.ghost;
        var previews = this.buildings.stream().map(SettlementBuildingDraft::ghost).filter(java.util.Objects::nonNull).toList();
        if (previews.isEmpty()) return null;
        var origin = previews.getFirst().origin();
        var blocks = new ArrayList<com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewBlock>();
        for (var preview : previews) for (var block : preview.blocks())
            blocks.add(new com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewBlock(block.worldPos(preview.origin()).subtract(origin), block.state()));
        var min = new net.minecraft.core.BlockPos(previews.stream().mapToInt(p -> p.worldMin().getX()).min().orElseThrow(),
                previews.stream().mapToInt(p -> p.worldMin().getY()).min().orElseThrow(), previews.stream().mapToInt(p -> p.worldMin().getZ()).min().orElseThrow()).subtract(origin);
        var max = new net.minecraft.core.BlockPos(previews.stream().mapToInt(p -> p.worldMax().getX()).max().orElseThrow(),
                previews.stream().mapToInt(p -> p.worldMax().getY()).max().orElseThrow(), previews.stream().mapToInt(p -> p.worldMax().getZ()).max().orElseThrow()).subtract(origin);
        this.rendered = this.request;
        return this.ghost = new com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewData(this.request,
                com.jvn.villagerretaliation.settlement.SettlementProject.AUTOMATIC_BUILDER, minecraft.level.dimension(), previews.getFirst().structureId(),
                "District plan", origin, net.minecraft.world.level.block.Rotation.NONE, min, max, blocks.size(), 0, 0, "", "", "",
                false, false, false, false, List.copyOf(blocks));
    }

    void remove(int index) {
        if (index < this.buildings.size()) this.buildings.remove(index); else this.paths.remove(index - this.buildings.size());
        this.request = UUID.randomUUID();
    }
    void clear() { this.buildings.clear(); this.paths.clear(); this.request = UUID.randomUUID(); }
    SettlementDistrictRequestPayload request(UUID town, boolean confirm, String fingerprint) {
        return new SettlementDistrictRequestPayload(town, this.request, confirm, fingerprint, this.buildings.stream()
                .map(b -> new SettlementManagementActionPayload.CommissionBuilding(b.request, b.structure, b.anchor, b.rotation, b.builder, b.preparation, b.preview.fingerprint())).toList(),
                this.paths.stream().map(p -> new SettlementDistrictRequestPayload.PlannedPath(p.id(), p.path())).toList(), this.pathsFirst);
    }
}
