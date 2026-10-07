package com.jvn.villagerretaliation.campaign;

public enum CampaignWorldMode {
    LEGACY("legacy"),
    CAMPAIGN("campaign"),
    SANDBOX("sandbox"),
    UNKNOWN("unknown");

    private final String id;

    CampaignWorldMode(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static CampaignWorldMode byId(String id) {
        for (CampaignWorldMode mode : values()) {
            if (mode.id.equals(id)) return mode;
        }
        return UNKNOWN;
    }
}
