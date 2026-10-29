package com.jvn.villagerretaliation.client.settlement;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class SettlementPlacementSummary {
    private SettlementPlacementSummary() { }

    static List<Component> describe(SettlementBuildingDraft building, SettlementWallEditor walls, SettlementPathEditor paths,
                                    SettlementFarmEditor farms, SettlementInfrastructureEditor infrastructure) {
        String title, status, materials;
        List<BlockPos> positions;
        if (building != null) {
            title = building.label;
            status = building.placementStatus();
            var bounds = building.bounds();
            positions = List.of(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ), BlockPos.containing(bounds.maxX - 1, bounds.maxY - 1, bounds.maxZ - 1));
            materials = building.currentPreview() ? building.preview.materials().stream().limit(8)
                    .map(material -> material.label() + " " + material.available() + "/" + material.required())
                    .collect(java.util.stream.Collectors.joining(", ")) : "Awaiting validated requirements";
            if (materials.isBlank()) materials = "No items required";
            if (building.currentPreview() && building.preview.materials().size() > 8) materials += ", more in Review placement";
        } else if (walls.active()) {
            title = (walls.destroying() ? "Remove " : walls.repairing() ? "Repair " : walls.upgrading() ? "Upgrade " : "") + walls.draft().wall().type().label();
            status = walls.placementStatus();
            materials = walls.requirements();
            positions = walls.cells().isEmpty() ? walls.draft().wall().nodes() : walls.cells().stream().map(cell -> cell.position()).toList();
        } else if (paths.active()) {
            title = "Path / width " + paths.draft().path().width();
            status = paths.placementStatus();
            materials = paths.requirements();
            positions = paths.cells().stream().map(cell -> cell.position()).toList();
        } else if (farms.active()) {
            title = "Farmland / " + farms.shape().label();
            status = farms.placementStatus();
            materials = farms.requirements();
            positions = farms.cells().isEmpty() ? farms.nodes() : farms.cells().stream().map(cell -> cell.position()).toList();
        } else if (infrastructure.active()) {
            title = infrastructure.type().label();
            status = infrastructure.placementStatus();
            materials = infrastructure.requirements();
            positions = infrastructure.first() == null ? List.of() : List.of(infrastructure.first(), infrastructure.second());
        } else return List.of();
        var lines = new ArrayList<Component>();
        lines.add(Component.literal("Placing: " + title));
        lines.add(Component.literal(status).withColor(status.startsWith("Cannot") || status.startsWith("Request failed") ? 0xFF9999
                : status.startsWith("Valid") ? 0xA5DBAD : 0xE7C77A));
        if (!positions.isEmpty()) {
            int minX = positions.stream().mapToInt(BlockPos::getX).min().orElseThrow(), maxX = positions.stream().mapToInt(BlockPos::getX).max().orElseThrow();
            int minY = positions.stream().mapToInt(BlockPos::getY).min().orElseThrow(), maxY = positions.stream().mapToInt(BlockPos::getY).max().orElseThrow();
            int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow(), maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
            lines.add(Component.literal("Size: " + (maxX - minX + 1) + " x " + (maxY - minY + 1) + " x " + (maxZ - minZ + 1) + " blocks / Y: " + minY + (maxY == minY ? "" : " to " + maxY)));
        }
        lines.add(Component.literal((building == null ? "Requirements: " : "Materials (available/required): ") + materials));
        return List.copyOf(lines);
    }

    static String failure(String failure) {
        return switch (failure) {
            case "bounds", "site_outside_area" -> "Outside owned land or allowed height";
            case "overlap" -> "Overlaps an existing area or project";
            case "blocked_existing" -> "A building, container or protected block is in the way";
            case "site_unloaded" -> "Terrain has not loaded yet";
            case "stage_capacity" -> "This settlement stage cannot start construction";
            case "queue_full" -> "The construction queue is full";
            default -> Component.translatableWithFallback("villagerretaliation.gui.settlement.failure." + failure, failure.replace('_', ' ')).getString();
        };
    }
}
