package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.network.ClipboardWorkforceSyncPayload;
import net.minecraft.client.Minecraft;

public final class ClipboardWorkforceClient {
    private ClipboardWorkforceClient() {
    }

    public static void accept(ClipboardWorkforceSyncPayload payload) {
        Minecraft.getInstance().setScreen(new ClipboardWorkforceScreen(payload.snapshot()));
    }
}
