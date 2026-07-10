package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.OpenPlayerPartyMenuPayload;
import net.minecraft.client.Minecraft;

public final class PlayerPartyInteractionClient {
    private PlayerPartyInteractionClient() {
    }

    public static void open(OpenPlayerPartyMenuPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new PlayerPartyInteractionScreen(minecraft.screen, payload));
    }
}
