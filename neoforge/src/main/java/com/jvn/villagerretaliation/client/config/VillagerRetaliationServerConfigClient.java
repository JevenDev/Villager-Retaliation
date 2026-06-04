package com.jvn.villagerretaliation.client.config;

import com.jvn.villagerretaliation.network.ServerConfigSyncPayload;

public final class VillagerRetaliationServerConfigClient {
    private static boolean showVillagerNameTags = true;

    private VillagerRetaliationServerConfigClient() {
    }

    public static void accept(ServerConfigSyncPayload payload) {
        showVillagerNameTags = payload.showVillagerNameTags();
    }

    public static boolean showVillagerNameTags() {
        return showVillagerNameTags;
    }

    public static void reset() {
        showVillagerNameTags = true;
    }
}
