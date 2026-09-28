package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementOwnershipService {
    private static final java.util.Map<net.minecraft.server.MinecraftServer, java.util.Map<UUID, Recovery>> RECOVERIES = new java.util.WeakHashMap<>();

    private SettlementOwnershipService() {
    }

    public static boolean offer(ServerPlayer player, SettlementRecord town, UUID successor) {
        if (player == null || town == null || !player.isAlive() || player.isSpectator()) return false;
        var offer = town.offerLeadership(player.getUUID(), successor, player.serverLevel().getGameTime());
        if (offer == null) return false;
        player.sendSystemMessage(Component.literal("Leadership offer created for " + successor
                + ". Current ownership remains until acceptance. Offer expires in 20 minutes of server time. "
                + "Acceptance revokes existing delegates. Cancel with /vr settlement " + town.id().value() + " cancel " + offer.id()));
        var recipient = player.getServer().getPlayerList().getPlayer(successor);
        if (recipient != null) recipient.sendSystemMessage(Component.literal(instruction(town)));
        return true;
    }

    public static String instruction(SettlementRecord town) {
        var offer = town.leadershipOffer();
        return "Leadership offered for " + town.id().value() + ". Accept with /vr settlement " + town.id().value()
                + " accept " + offer.id() + ". This gives you the town, treasury and responsibility for its work. "
                + "Existing delegates lose access. Decline with /vr settlement " + town.id().value() + " cancel " + offer.id();
    }

    public static boolean accept(ServerPlayer player, SettlementRecord town, UUID offer) {
        if (player == null || town == null || !player.isAlive() || player.isSpectator()
                || !town.acceptLeadership(player.getUUID(), offer, player.serverLevel().getGameTime())) return false;
        changed(player.serverLevel(), town, player.getUUID());
        return true;
    }

    public static UUID previewRecovery(net.minecraft.commands.CommandSourceStack source, SettlementRecord town, UUID successor) {
        if (!source.hasPermission(2) || town == null || successor == null || successor.equals(town.leader())) return null;
        var pending = RECOVERIES.computeIfAbsent(source.getServer(), ignored -> new java.util.HashMap<>());
        long now = source.getServer().overworld().getGameTime();
        pending.values().removeIf(value -> value.expiresAt() <= now);
        if (pending.size() >= 256) return null;
        UUID token = UUID.randomUUID();
        pending.put(token, new Recovery(sourceIdentity(source), town.id(), town.leader(), successor, now + 1200L));
        source.sendSuccess(() -> Component.literal("Recover settlement " + town.id().value() + " from owner " + town.leader()
                + " to " + successor + ". This revokes all delegates and pending leadership offers. "
                + "Treasury, residents and work stay with the town. Confirm within 60 seconds with /vr admin settlement confirmOwner " + token), false);
        return token;
    }

    public static boolean confirmRecovery(net.minecraft.commands.CommandSourceStack source, UUID token) {
        if (!source.hasPermission(2)) return false;
        var pending = RECOVERIES.getOrDefault(source.getServer(), java.util.Map.of());
        var recovery = pending.get(token);
        if (recovery == null || !recovery.actor().equals(sourceIdentity(source))) return false;
        pending.remove(token);
        var town = SettlementSavedData.get(source.getLevel()).get(recovery.town()).orElse(null);
        if (town == null || !town.id().equals(recovery.town()) || !town.leader().equals(recovery.previous())
                || source.getServer().overworld().getGameTime() >= recovery.expiresAt()
                || !town.recoverLeadership(source, recovery.successor())) return false;
        changed(source.getLevel(), town, recovery.successor());
        source.sendSuccess(() -> Component.literal("Settlement ownership recovered for " + town.id().value()
                + ". New owner: " + recovery.successor()), true);
        return true;
    }

    private static String sourceIdentity(net.minecraft.commands.CommandSourceStack source) {
        return source.getEntity() == null ? source.getTextName() : source.getEntity().getUUID().toString();
    }

    private record Recovery(String actor, com.jvn.villagerretaliation.allegiance.VillageAllegianceId town,
                            UUID previous, UUID successor, long expiresAt) {
    }

    static void changed(net.minecraft.server.level.ServerLevel level, SettlementRecord town, UUID successor) {
        town.recordEvent(level.getGameTime(), SettlementEvent.Type.LEADERSHIP_TRANSFERRED, successor.toString());
        SettlementViewService.closeTown(town);
        for (var dimension : level.getServer().getAllLevels()) {
            for (var id : java.util.List.copyOf(town.duties().keySet())) {
                if (dimension.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager villager) {
                    SettlementWorkforceService.reconcile(dimension, villager);
                }
            }
        }
    }
}
