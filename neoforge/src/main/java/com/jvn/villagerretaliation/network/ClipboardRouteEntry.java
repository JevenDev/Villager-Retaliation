package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.HiredRoute;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record ClipboardRouteEntry(
        ResourceLocation dimension,
        List<BlockPos> nodes,
        boolean loop,
        String ownerName,
        String jobName) {
    private static final int LABEL_LENGTH = 64;

    public ClipboardRouteEntry {
        List<BlockPos> safeNodes = new ArrayList<>();
        if (nodes != null) {
            for (BlockPos node : nodes) {
                if (node != null && safeNodes.size() < HiredRoute.MAX_NODES) {
                    safeNodes.add(node.immutable());
                }
            }
        }
        nodes = List.copyOf(safeNodes);
        loop = loop && nodes.size() > 1 && HiredRoute.canConnect(nodes.getLast(), nodes.getFirst());
        ownerName = sanitizeLabel(ownerName);
        jobName = sanitizeLabel(jobName);
    }

    public ClipboardRouteEntry(ResourceLocation dimension, List<BlockPos> nodes, boolean loop) {
        this(dimension, nodes, loop, "", "");
    }

    private static String sanitizeLabel(String label) {
        if (label == null) {
            return "";
        }
        String trimmed = label.trim();
        return trimmed.length() > LABEL_LENGTH ? trimmed.substring(0, LABEL_LENGTH) : trimmed;
    }
}
