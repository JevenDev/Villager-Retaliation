package com.jvn.villagerretaliation.campaign.content;

import net.minecraft.resources.ResourceLocation;

public record CampaignRewardDefinition(String id, ResourceLocation quest, Scope scope, String milestone,
        String outcome, int experience, ResourceLocation lootTable, String pointer) {
    public enum Scope { PERSONAL, PARTICIPANTS, SHARED }
}
