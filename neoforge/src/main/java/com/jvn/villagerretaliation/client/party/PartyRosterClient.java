package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class PartyRosterClient {
    private static PartyRosterSyncPayload roster = PartyRosterSyncPayload.empty();

    private PartyRosterClient() {
    }

    public static void accept(PartyRosterSyncPayload payload) {
        roster = payload == null ? PartyRosterSyncPayload.empty() : payload;
    }

    public static PartyRosterSyncPayload roster() {
        return roster;
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        roster = PartyRosterSyncPayload.empty();
    }
}
