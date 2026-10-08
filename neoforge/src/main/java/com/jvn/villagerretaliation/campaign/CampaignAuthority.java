package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.party.PartyService;
import net.minecraft.server.level.ServerPlayer;

public final class CampaignAuthority {
    private static final ThreadLocal<Delegation> DELEGATED = new ThreadLocal<>();

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
        var delegated = DELEGATED.get();
        if (player != null && delegated != null && delegated.actor().equals(player.getUUID()) && delegated.controller().server == player.server
                && player.server.getPlayerList().getPlayer(delegated.controller().getUUID()) == delegated.controller()) {
            return directControlIssue(delegated.controller(), delegated.actor());
        }
        return canControl(player) ? "" : "Your party leader or a member with Admin Privileges must control campaign conversations.";
    }

    public static <T> T withController(ServerPlayer controller, java.util.UUID actor, java.util.function.Supplier<T> work) {
        String issue = directControlIssue(controller, actor);
        if (!issue.isBlank()) throw new IllegalArgumentException(issue);
        var previous = DELEGATED.get();
        DELEGATED.set(new Delegation(controller, actor));
        try { return work.get(); }
        finally {
            if (previous == null) DELEGATED.remove();
            else DELEGATED.set(previous);
        }
    }

    private static String directControlIssue(ServerPlayer controller, java.util.UUID actor) {
        if (!canControl(controller)) return "Your party leader or a member with Admin Privileges must control campaign conversations.";
        if (controller.getUUID().equals(actor)) return "";
        return PartyService.getPartyForPlayer(controller.serverLevel(), controller.getUUID()).filter(party -> party.playerIds().contains(actor)).isPresent()
                ? "" : "The deciding player and controller must still belong to the same party.";
    }

    private record Delegation(ServerPlayer controller, java.util.UUID actor) { }
}
