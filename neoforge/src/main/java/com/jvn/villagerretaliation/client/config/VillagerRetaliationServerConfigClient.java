package com.jvn.villagerretaliation.client.config;

import com.jvn.villagerretaliation.config.VillagerStatDisplayMode;
import com.jvn.villagerretaliation.network.ServerConfigSyncPayload;

public final class VillagerRetaliationServerConfigClient {
    private static boolean showVillagerNameTags = true;
    private static boolean villagerGiftsEnabled = true;
    private static boolean skillTradeFeaturesEnabled;
    private static VillagerStatDisplayMode villagerStatDisplayMode = VillagerStatDisplayMode.PARTY_ONLY;

    private VillagerRetaliationServerConfigClient() {
    }

    public static void accept(ServerConfigSyncPayload payload) {
        showVillagerNameTags = payload.showVillagerNameTags();
        villagerGiftsEnabled = payload.villagerGiftsEnabled();
        skillTradeFeaturesEnabled = payload.skillTradeFeaturesEnabled();
        villagerStatDisplayMode = payload.villagerStatDisplayMode();
    }

    public static boolean showVillagerNameTags() {
        return showVillagerNameTags;
    }

    public static boolean villagerGiftsEnabled() {
        return villagerGiftsEnabled;
    }

    public static boolean skillTradeFeaturesEnabled() {
        return skillTradeFeaturesEnabled;
    }

    public static VillagerStatDisplayMode villagerStatDisplayMode() {
        return villagerStatDisplayMode;
    }

    public static void reset() {
        showVillagerNameTags = true;
        villagerGiftsEnabled = true;
        villagerStatDisplayMode = VillagerStatDisplayMode.PARTY_ONLY;
        skillTradeFeaturesEnabled = false;
    }
}
