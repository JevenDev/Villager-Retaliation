package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementFarm;
import com.jvn.villagerretaliation.settlement.SettlementFarmTerrain;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class SettlementFarmGizmo {
    private SettlementFarmGizmo() {}

    public static void renderHandles(SettlementFarmEditor editor, com.mojang.blaze3d.vertex.PoseStack pose,
                                     com.mojang.blaze3d.vertex.VertexConsumer lines) {
        for (var pos : editor.nodes()) {
            var center = pos.getCenter().add(0, 0.5, 0);
            LevelRenderer.renderLineBox(pose, lines, new AABB(center, center).inflate(0.2), 1, 0.9F, 0.3F, 1);
        }
        var nodes = editor.outlineNodes();
        if (nodes.size() < 2) return;
        if (editor.shape() == SettlementFarm.Shape.POLYGON) {
            for (int i = 0; i < nodes.size(); i++)
                line(pose, lines, nodes.get(i).getCenter().add(0, 0.5, 0),
                        nodes.get((i + 1) % nodes.size()).getCenter().add(0, 0.5, 0));
        } else if (editor.shape() == SettlementFarm.Shape.CIRCLE) {
            var center = editor.first().getCenter().add(0, 0.5, 0);
            double radius = Math.hypot((double) editor.second().getX() - editor.first().getX(),
                    (double) editor.second().getZ() - editor.first().getZ());
            line(pose, lines, center, editor.second().getCenter().add(0, 0.5, 0));
            for (int i = 0; i < 64; i++) {
                double a = i * Math.PI / 32, b = (i + 1) * Math.PI / 32;
                line(pose, lines, center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                        center.add(Math.cos(b) * radius, 0, Math.sin(b) * radius));
            }
        }
    }

    private static void line(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer lines, Vec3 a, Vec3 b) {
        SettlementSelectionGizmo.line(pose, lines, a, b, 1, 0.9F, 0.3F, 1);
    }

    public static void render(SettlementFarmEditor editor, RenderLevelStageEvent event) {
        com.jvn.villagerretaliation.client.PreviewMeshCache.render(editor, editor.cells(), () -> {
            var mesh = new com.jvn.villagerretaliation.client.PreviewMeshCache();
            for (var cell : editor.cells()) {
                var pos = cell.position();
                var section = mesh.at(pos);
                boolean water = cell.kind() == SettlementFarmTerrain.Kind.WATER;
                boolean blocked = cell.kind() == SettlementFarmTerrain.Kind.OUTSIDE || cell.kind() == SettlementFarmTerrain.Kind.UNAVAILABLE;
                boolean skipped = cell.kind() == SettlementFarmTerrain.Kind.SKIPPED;
                boolean dry = cell.kind() == SettlementFarmTerrain.Kind.DRY;
                boolean existing = cell.kind() == SettlementFarmTerrain.Kind.EXISTING;
                LevelRenderer.renderLineBox(section.pose(), section.getBuffer(RenderType.lines()),
                        new AABB(pos.getX() + 0.03, pos.getY() + 0.94, pos.getZ() + 0.03,
                                pos.getX() + 0.97, pos.getY() + 1.03, pos.getZ() + 0.97),
                        water || existing ? 0.2F : skipped ? 0.55F : 1,
                        blocked ? 0.1F : dry ? 0.35F : water ? 0.6F : skipped ? 0.55F : 0.8F,
                        water || existing ? 1 : dry ? 0.05F : skipped ? 0.55F : 0.15F, 0.9F);
            }
            return mesh;
        }, event);
    }
}
