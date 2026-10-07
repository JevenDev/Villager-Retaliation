package com.jvn.villagerretaliation.campaign.content;

import net.minecraft.resources.ResourceLocation;

public record CampaignCharacterDefinition(String id, String titleKey, Source source, String location,
        int searchRadius, ResourceLocation profession, LossPolicy lossPolicy, String pointer) {
    public static final int MAX_SEARCH_RADIUS = 64;

    public CampaignCharacterDefinition {
        if (!localId(id) || titleKey == null || titleKey.isBlank() || source == null || !localId(location)
                || searchRadius < 1 || searchRadius > MAX_SEARCH_RADIUS || lossPolicy == null) {
            throw new IllegalArgumentException("Invalid campaign character definition.");
        }
    }

    private static boolean localId(String value) {
        return value != null && value.length() <= 256 && value.matches("[a-z0-9][a-z0-9_./-]*");
    }

    public enum Source { EXISTING_VILLAGER }
    public enum LossPolicy { DOWNED }
}
