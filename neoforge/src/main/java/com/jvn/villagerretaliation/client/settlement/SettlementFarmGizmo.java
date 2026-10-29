package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementFarmTerrain;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class SettlementFarmGizmo {
    private SettlementFarmGizmo() {}

    public static void renderHandles(SettlementFarmEditor editor, com.mojang.blaze3d.vertex.PoseStack pose,
                                     com.mojang.blaze3d.vertex.VertexConsumer lines) {
        for (var pos : new net.minecraft.core.BlockPos[]{editor.first(), editor.second()}) {
            if (pos == null) continue;
            var center = pos.getCenter().add(0, 0.5, 0);
            LevelRenderer.renderLineBox(pose, lines, new AABB(center, center).inflate(0.2), 1, 0.9F, 0.3F, 1);
        }
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
                boolean existing = cell.kind() == SettlementFarmTerrain.Kind.EXISTING;
                LevelRenderer.renderLineBox(section.pose(), section.getBuffer(RenderType.lines()),
                        new AABB(pos.getX() + 0.03, pos.getY() + 0.94, pos.getZ() + 0.03,
                                pos.getX() + 0.97, pos.getY() + 1.03, pos.getZ() + 0.97),
                        water || existing ? 0.2F : skipped ? 0.55F : 1,
                        blocked ? 0.1F : water ? 0.6F : skipped ? 0.55F : 0.8F,
                        water || existing ? 1 : skipped ? 0.55F : 0.15F, 0.9F);
            }
            return mesh;
        }, event);
    }
}
