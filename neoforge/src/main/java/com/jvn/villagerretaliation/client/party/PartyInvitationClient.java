package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.PartyInvitationSyncPayload;
import net.minecraft.client.Minecraft;

public final class PartyInvitationClient {
    private PartyInvitationClient() {
    }

    public static void accept(PartyInvitationSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new PartyInvitationScreen(minecraft.screen, payload));
    }
}
