package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementWallGeometry;
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
        int selected = -1;
        double best = 10;
        for (int i = 0; i < (draft.wall().part() == com.jvn.villagerretaliation.settlement.SettlementWall.Part.TOWER ? 1 : draft.wall().nodes().size()); i++) {
            var point = project.apply(Vec3.atBottomCenterOf(draft.wall().nodes().get(i)).add(0, 0.15, 0));
            if (point == null) continue;
            double distance = Math.hypot(point[0] - x, point[1] - y);
            if (distance < best) { best = distance; selected = i; }
        }
        return selected;
    }
    static void renderBlocked(SettlementWallEditor editor, PoseStack pose, VertexConsumer lines) {
        for (var cell : editor.cells()) {
            boolean removal = editor.editingPlaced() && cell.state().isAir() && cell.kind() == SettlementWallTerrain.Kind.PLACE;
            if (!removal && (cell.kind() == SettlementWallTerrain.Kind.PLACE || cell.kind() == SettlementWallTerrain.Kind.EXISTING)) continue;
            float green = cell.kind() == SettlementWallTerrain.Kind.UNAVAILABLE ? 0.5F : 0.08F;
            LevelRenderer.renderLineBox(pose, lines, new AABB(cell.position()).inflate(0.005), 1, green, 0.05F, 0.8F);
        }
    }
    static void renderNodes(SettlementWallEditor editor, PoseStack pose, VertexConsumer lines) {
        if (editor.shown() == null) return;
        var manual = editor.draft().wall().nodes();
        var nodes = SettlementWallGeometry.towers(editor.shown());
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
