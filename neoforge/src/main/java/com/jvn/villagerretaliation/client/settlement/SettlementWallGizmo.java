package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementWallTerrain;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class SettlementWallGizmo {
    private SettlementWallGizmo() {}
    static int hit(SettlementWallDraft draft, Function<Vec3, double[]> project, double x, double y) {
        var wall = draft.wall();
        var nodes = wall.part() == com.jvn.villagerretaliation.settlement.SettlementWall.Part.TOWER
                ? wall.nodes().subList(0, 1) : wall.nodes();
        return hit(nodes, project, x, y);
    }
    static net.minecraft.core.BlockPos hitAutomatic(SettlementWallDraft draft, Function<Vec3, double[]> project, double x, double y) {
        var wall = draft.wall();
        if (!draft.automatic() || wall.part() != com.jvn.villagerretaliation.settlement.SettlementWall.Part.COMPLETE) return null;
        var nodes = com.jvn.villagerretaliation.settlement.SettlementWallGeometry.towers(wall).stream()
                .filter(node -> !wall.nodes().contains(node)).toList();
        int index = hit(nodes, project, x, y);
        return index < 0 ? null : nodes.get(index);
    }
    private static int hit(java.util.List<net.minecraft.core.BlockPos> nodes, Function<Vec3, double[]> project, double x, double y) {
        int selected = -1;
        double best = 10;
        for (int i = 0; i < nodes.size(); i++) {
            var point = project.apply(Vec3.atBottomCenterOf(nodes.get(i)).add(0, 0.15, 0));
            if (point == null) continue;
            double distance = Math.hypot(point[0] - x, point[1] - y);
            if (distance < best) { best = distance; selected = i; }
        }
        return selected;
    }
    static void renderBlocked(SettlementWallEditor editor, net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        com.jvn.villagerretaliation.client.PreviewMeshCache.render(editor, java.util.List.of(editor.cells(), editor.editingPlaced()), () -> {
            var mesh = new com.jvn.villagerretaliation.client.PreviewMeshCache();
            for (var cell : editor.cells()) {
                boolean removal = editor.editingPlaced() && cell.state().isAir() && cell.kind() == SettlementWallTerrain.Kind.PLACE;
                if (!removal && (cell.kind() == SettlementWallTerrain.Kind.PLACE || cell.kind() == SettlementWallTerrain.Kind.EXISTING)) continue;
                float green = cell.kind() == SettlementWallTerrain.Kind.SKIPPED ? 0.7F
                        : cell.kind() == SettlementWallTerrain.Kind.UNAVAILABLE ? 0.5F : 0.08F;
                var section = mesh.at(cell.position());
                LevelRenderer.renderLineBox(section.pose(), section.getBuffer(net.minecraft.client.renderer.RenderType.lines()),
                        new AABB(cell.position()).inflate(0.005), 1, green, 0.05F, 0.8F);
            }
            return mesh;
        }, event);
    }
    static void renderNodes(SettlementWallEditor editor, PoseStack pose, VertexConsumer lines) {
        if (editor.shown() == null) return;
        var manual = editor.draft().wall().nodes();
        var nodes = editor.towerNodes();
        for (var node : nodes) {
            boolean selected = editor.draft().selected() >= 0 && node.equals(manual.get(editor.draft().selected()));
            boolean automatic = !manual.contains(node);
            var point = Vec3.atBottomCenterOf(node).add(0, 0.15, 0);
            LevelRenderer.renderLineBox(pose, lines, new AABB(point, point).inflate(selected ? 0.4 : 0.25),
                    automatic ? 0.2F : 1, selected ? 0.8F : 1, automatic ? 1 : 0.25F, 1);
        }
        for (int i = 1; editor.shown().part() != com.jvn.villagerretaliation.settlement.SettlementWall.Part.TOWER && i < manual.size(); i++)
            SettlementSelectionGizmo.line(pose, lines, Vec3.atBottomCenterOf(manual.get(i - 1)),
                    Vec3.atBottomCenterOf(manual.get(i)), 0.3F, 0.8F, 1, 1);
    }
}
