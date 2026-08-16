package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.network.ClipboardWorkforceSyncPayload;
import net.minecraft.client.Minecraft;

public final class ClipboardWorkforceClient {
    private ClipboardWorkforceClient() {
    }

    public static void accept(ClipboardWorkforceSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ClipboardWorkforceScreen screen) {
            screen.updateSnapshot(payload.snapshot());
        } else if (payload.openScreen()) {
            minecraft.setScreen(new ClipboardWorkforceScreen(payload.snapshot()));
        }
    }
}
