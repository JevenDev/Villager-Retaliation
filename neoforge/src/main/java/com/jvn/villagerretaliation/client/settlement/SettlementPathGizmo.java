package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementPathTerrain;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SettlementPathGizmo {
    private SettlementPathGizmo() {}
    public record Handle(int node, int part) {}

    public static Handle hit(SettlementPathDraft draft, Function<Vec3, double[]> project, double x, double y) {
        var nodes = draft.path().nodes();
        Handle closest = null;
        double best = 10;
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            int min = i == draft.selected() ? -1 : 0, max = i == draft.selected() ? 1 : 0;
            for (int part = min; part <= max; part++) {
                Vec3 offset = part < 0 ? node.incoming() : part > 0 ? node.outgoing() : Vec3.ZERO;
                if (part != 0 && offset.lengthSqr() < 0.01) continue;
                var point = project.apply(node.position().add(offset));
                if (point == null) continue;
                double distance = Math.hypot(point[0] - x, point[1] - y);
                if (distance < best) { best = distance; closest = new Handle(i, part); }
            }
        }
        return closest;
    }

    public static Vec3 plane(SettlementSelectionGizmo.Ray ray, double height) {
        if (Math.abs(ray.direction().y) < 0.00001) return null;
        double t = (height - ray.origin().y) / ray.direction().y;
        return t < 0 || t > 512 ? null : ray.origin().add(ray.direction().scale(t));
    }

    public static void renderTerrain(SettlementPathEditor editor, PoseStack pose, VertexConsumer terrain) {
        for (var cell : editor.cells()) {
            float red = cell.kind() == SettlementPathTerrain.Kind.SKIPPED ? 1 : 0.25F;
            float green = cell.kind() == SettlementPathTerrain.Kind.SKIPPED ? 0.35F : 0.9F;
            float blue = cell.kind() == SettlementPathTerrain.Kind.EXISTING ? 1 : 0.2F;
            var pos = cell.position();
            LevelRenderer.renderLineBox(pose, terrain, new AABB(pos.getX() + 0.03, pos.getY() + 0.94, pos.getZ() + 0.03,
                    pos.getX() + 0.97, pos.getY() + 1.02, pos.getZ() + 0.97), red, green, blue, 0.85F);
        }
    }

    public static void renderHandles(SettlementPathEditor editor, PoseStack pose, VertexConsumer handles) {
        if (!editor.active()) return;
        var path = editor.draft().path();
        var samples = editor.centerline();
        for (int i = 1; i < samples.size(); i++) SettlementSelectionGizmo.line(pose, handles, samples.get(i - 1), samples.get(i), 0.3F, 0.8F, 1, 1);
        for (int i = 0; i < path.nodes().size(); i++) {
            var node = path.nodes().get(i);
            boolean selected = i == editor.draft().selected();
            var hovered = editor.hoveredHandle();
            boolean nodeHovered = hovered != null && hovered.node() == i && hovered.part() == 0;
            marker(pose, handles, node.position(), nodeHovered ? 0.28 : selected ? 0.24 : 0.17, 1, selected || nodeHovered ? 0.8F : 1, 0.3F);
            if (!selected) continue;
            for (int part : new int[]{-1, 1}) {
                var offset = part < 0 ? node.incoming() : node.outgoing();
                if (offset.lengthSqr() < 0.01) continue;
                var point = node.position().add(offset);
                SettlementSelectionGizmo.line(pose, handles, node.position(), point, 0.4F, 0.75F, 1, 1);
                boolean handleHovered = hovered != null && hovered.node() == i && hovered.part() == part;
                marker(pose, handles, point, handleHovered ? 0.2 : 0.14, handleHovered ? 1 : 0.4F, 0.75F, handleHovered ? 0.3F : 1);
            }
        }
    }
    private static void marker(PoseStack pose, VertexConsumer lines, Vec3 point, double size, float r, float g, float b) {
        LevelRenderer.renderLineBox(pose, lines, new AABB(point, point).inflate(size), r, g, b, 1);
    }
}
