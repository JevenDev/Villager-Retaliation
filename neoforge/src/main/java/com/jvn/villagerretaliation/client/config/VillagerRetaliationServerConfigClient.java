package com.jvn.villagerretaliation.client.config;

import com.jvn.villagerretaliation.config.VillagerStatDisplayMode;
import com.jvn.villagerretaliation.network.ServerConfigSyncPayload;

public final class VillagerRetaliationServerConfigClient {
    private static boolean showVillagerNameTags = true;
    private static VillagerStatDisplayMode villagerStatDisplayMode = VillagerStatDisplayMode.PARTY_ONLY;

    private VillagerRetaliationServerConfigClient() {
    }

    public static void accept(ServerConfigSyncPayload payload) {
        showVillagerNameTags = payload.showVillagerNameTags();
        villagerStatDisplayMode = payload.villagerStatDisplayMode();
    }

    public static boolean showVillagerNameTags() {
        return showVillagerNameTags;
    }

    public static VillagerStatDisplayMode villagerStatDisplayMode() {
        return villagerStatDisplayMode;
    }

    public static void reset() {
        showVillagerNameTags = true;
        villagerStatDisplayMode = VillagerStatDisplayMode.PARTY_ONLY;
    }
}
