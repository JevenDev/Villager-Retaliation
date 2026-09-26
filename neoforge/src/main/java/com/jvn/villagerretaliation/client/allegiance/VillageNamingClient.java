package com.jvn.villagerretaliation.client.allegiance;

import com.jvn.villagerretaliation.network.OpenVillageNamingPayload;
import com.jvn.villagerretaliation.network.VillageRenameResultPayload;
import net.minecraft.client.Minecraft;

public final class VillageNamingClient {
    private VillageNamingClient() {
    }

    public static void open(OpenVillageNamingPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            minecraft.setScreen(new VillageNamingScreen(payload));
        }
    }

    public static void accept(VillageRenameResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof VillageNamingScreen screen) {
            screen.accept(payload);
        }
    }
}
