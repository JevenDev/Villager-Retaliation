package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class SettlementMoveMarkers {
    private SettlementMoveMarkers() { }

    static Set<BlockPos> destinations(Collection<UUID> selected, Map<UUID, Resident> residents, Map<UUID, BlockPos> confirmed) {
        var destinations = new LinkedHashSet<BlockPos>();
        for (var id : selected) {
            var resident = residents.get(id);
            var destination = confirmed.getOrDefault(id, resident == null ? null : resident.moveDestination());
            if (destination != null) destinations.add(destination);
        }
        return destinations;
    }

    static void render(PoseStack pose, VertexConsumer lines, BlockPos destination) {
        var center = Vec3.atBottomCenterOf(destination).add(0, 0.08, 0);
        for (int segment = 0; segment < 24; segment++) {
            double angle = segment * Math.PI / 12, next = (segment + 1) * Math.PI / 12;
            line(pose, lines, center.add(Math.cos(angle) * 0.7, 0, Math.sin(angle) * 0.7),
                    center.add(Math.cos(next) * 0.7, 0, Math.sin(next) * 0.7));
        }
        var top = center.add(0, 2.4, 0);
        var tip = top.add(0.9, -0.3, 0);
        line(pose, lines, center, top);
        line(pose, lines, top, tip);
        line(pose, lines, tip, top.add(0, -0.6, 0));
    }

    private static void line(PoseStack pose, VertexConsumer lines, Vec3 from, Vec3 to) {
        SettlementSelectionGizmo.line(pose, lines, from, to, 1.0F, 0.8F, 0.2F, 1.0F);
    }
}
