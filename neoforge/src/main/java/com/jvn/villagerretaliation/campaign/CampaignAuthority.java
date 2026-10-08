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

    public static String controlIssue(ServerPlayer controller, java.util.UUID actor) {
        String issue = controlIssue(controller);
        if (!issue.isBlank() || controller.getUUID().equals(actor)) return issue;
        var party = PartyService.getPartyForPlayer(controller.serverLevel(), controller.getUUID()).orElse(null);
        return party != null && party.playerIds().contains(actor) ? ""
                : "The deciding player and controller must still belong to the same party.";
    }

    public static String controlIssue(ServerPlayer player) {
        return canControl(player) ? "" : "Your party leader or a member with Admin Privileges must control campaign conversations.";
    }
}
