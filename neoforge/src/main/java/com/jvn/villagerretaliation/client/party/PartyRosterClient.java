package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class PartyRosterClient {
    private static PartyRosterSyncPayload roster = PartyRosterSyncPayload.empty();

    private PartyRosterClient() {
    }

    public static void accept(PartyRosterSyncPayload payload) {
        PartyRosterSyncPayload updatedRoster = payload == null ? PartyRosterSyncPayload.empty() : payload;
        boolean leftParty = roster.active() && !updatedRoster.active();
        roster = updatedRoster;
        if (leftParty) PartyInventoryOverlay.resetOpenInventoryPage();
    }

    public static PartyRosterSyncPayload roster() {
        return roster;
    }

    public static boolean hasAdminPrivileges() {
        var player = Minecraft.getInstance().player;
        if (!roster.active() || player == null) return false;
        return roster.players().stream()
                .filter(entry -> entry.playerId().equals(player.getUUID()))
                .findFirst()
                .map(PartyRosterSyncPayload.PlayerEntry::adminPrivileges)
                .orElse(roster.recipientLeader());
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        roster = PartyRosterSyncPayload.empty();
        PartyPlayerSkinResolver.clear();
        PartyInventoryOverlay.resetPreferredInventoryPage();
    }
}
