package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementInfrastructureTerrain;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class SettlementInfrastructureGizmo {
    private SettlementInfrastructureGizmo() {}

    static void renderHandles(SettlementInfrastructureEditor editor, com.mojang.blaze3d.vertex.PoseStack pose,
                              com.mojang.blaze3d.vertex.VertexConsumer lines) {
        for (var pos : new net.minecraft.core.BlockPos[]{editor.first(), editor.second()}) {
            if (pos == null) continue;
            var center = pos.getCenter().add(0, 0.5, 0);
            LevelRenderer.renderLineBox(pose, lines, new AABB(center, center).inflate(0.2), 1, 0.9F, 0.3F, 1);
        }
    }

    static void render(SettlementInfrastructureEditor editor, RenderLevelStageEvent event) {
        com.jvn.villagerretaliation.client.PreviewMeshCache.render(editor, editor.cells(), () -> {
            var mesh = new com.jvn.villagerretaliation.client.PreviewMeshCache();
            for (var cell : editor.cells()) {
                if (cell.kind() == SettlementInfrastructureTerrain.Kind.EXISTING) continue;
                var pos = cell.position();
                var section = mesh.at(pos);
                boolean removal = cell.kind() == SettlementInfrastructureTerrain.Kind.REMOVE || cell.kind() == SettlementInfrastructureTerrain.Kind.BLOCKED;
                LevelRenderer.renderLineBox(section.pose(), section.getBuffer(RenderType.lines()), new AABB(pos).deflate(0.01),
                        removal ? 1 : 0.3F, removal ? 0.2F : 0.9F, removal ? 0.1F : 1, 0.9F);
            }
            return mesh;
        }, event);
    }
}
