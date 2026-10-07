package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.party.PartyService;
import net.minecraft.server.level.ServerPlayer;

public final class CampaignAuthority {
    private CampaignAuthority() {
    }

    public static boolean canControl(ServerPlayer player) {
        return player != null && PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID())
                .map(party -> party.hasAdminPrivileges(player.getUUID())).orElse(true);
    }

    public static String controlIssue(ServerPlayer player) {
        return canControl(player) ? "" : "Your party leader or a member with Admin Privileges must control campaign conversations.";
    }
}
