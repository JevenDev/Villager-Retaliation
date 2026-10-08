package com.jvn.villagerretaliation.campaign.content;

import net.minecraft.resources.ResourceLocation;

public record CampaignEvidenceDefinition(String id, String titleKey, String textKey, String location,
        ResourceLocation entityTag, int searchRadius, String pointer) {
    public static final int MAX_SEARCH_RADIUS = 32;

    public CampaignEvidenceDefinition {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_./-]*") || id.length() > 256
                || titleKey == null || titleKey.isBlank() || textKey == null || textKey.isBlank()
                || location == null || location.isBlank() || entityTag == null
                || searchRadius < 1 || searchRadius > MAX_SEARCH_RADIUS)
            throw new IllegalArgumentException("Invalid campaign evidence definition.");
    }
}
