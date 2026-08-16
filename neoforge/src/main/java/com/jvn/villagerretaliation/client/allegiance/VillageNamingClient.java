package com.jvn.villagerretaliation.client.allegiance;

import com.jvn.villagerretaliation.network.OpenVillageNamingPayload;
import net.minecraft.client.Minecraft;

public final class VillageNamingClient {
    private VillageNamingClient() {
    }

    public static void open(OpenVillageNamingPayload payload) {
        Minecraft.getInstance().setScreen(new VillageNamingScreen(payload));
    }
}
