package com.jvn.villagerretaliation.client.item;

import com.jvn.villagerretaliation.network.BlueprintChecklistSyncPayload;
import net.minecraft.client.Minecraft;

public final class BlueprintChecklistClient {
    private BlueprintChecklistClient() {
    }

    public static void accept(BlueprintChecklistSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof BlueprintChecklistScreen screen) {
            screen.accept(payload);
        } else if (payload.openScreen()) {
            minecraft.setScreen(new BlueprintChecklistScreen(payload));
        }
    }
}
