package com.jvn.villagerretaliation.config;

import com.jvn.villagerretaliation.campaign.CampaignWorldMode;

public enum NewWorldMode {
    CAMPAIGN(CampaignWorldMode.CAMPAIGN),
    SANDBOX(CampaignWorldMode.SANDBOX);

    private final CampaignWorldMode worldMode;

    NewWorldMode(CampaignWorldMode worldMode) {
        this.worldMode = worldMode;
    }

    public CampaignWorldMode worldMode() {
        return this.worldMode;
    }
}
