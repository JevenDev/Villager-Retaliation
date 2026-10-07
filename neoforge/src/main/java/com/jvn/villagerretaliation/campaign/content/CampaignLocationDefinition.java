package com.jvn.villagerretaliation.campaign.content;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record CampaignLocationDefinition(String id, String titleKey, Source source, ResourceLocation dimension,
        BlockPos origin, int searchRadius, ResourceLocation structure, ResourceLocation structureMarker, String pointer) {
    public static final int MAX_SEARCH_RADIUS = 256;

    public CampaignLocationDefinition {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_./-]*") || id.length() > 256
                || titleKey == null || titleKey.isBlank() || source == null || dimension == null
                || searchRadius < 1 || searchRadius > MAX_SEARCH_RADIUS
                || origin != null && (Math.abs((long) origin.getX()) > 30_000_000L || Math.abs((long) origin.getY()) > 30_000_000L
                        || Math.abs((long) origin.getZ()) > 30_000_000L)
                || source == Source.MARKER && origin == null
                || (source == Source.STRUCTURE) != (structure != null)
                || structureMarker != null && source != Source.STRUCTURE) {
            throw new IllegalArgumentException("Invalid campaign location definition.");
        }
        origin = origin == null ? null : origin.immutable();
    }

    public CampaignLocationDefinition(String id, String titleKey, Source source, ResourceLocation dimension,
            BlockPos origin, int searchRadius, ResourceLocation structure, String pointer) {
        this(id, titleKey, source, dimension, origin, searchRadius, structure, null, pointer);
    }

    public enum Source { MARKER, VILLAGE, STRUCTURE }
}
