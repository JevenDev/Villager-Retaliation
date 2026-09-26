package com.jvn.villagerretaliation.client.allegiance;

import com.jvn.villagerretaliation.network.VillageOverviewPayload;
import net.minecraft.client.Minecraft;

public final class VillageOverviewClient {
    private VillageOverviewClient() {
    }

    public static void accept(VillageOverviewPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(payload.dimension())) {
            return;
        }
        if (minecraft.screen instanceof VillageOverviewScreen screen && screen.accepts(payload)) {
            screen.update(payload);
        } else if (payload.openScreen()) {
            minecraft.setScreen(new VillageOverviewScreen(payload));
        }
    }
}
