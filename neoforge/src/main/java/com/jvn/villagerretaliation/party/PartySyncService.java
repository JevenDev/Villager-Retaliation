package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PartySyncService {
    private PartySyncService() {
    }

    public static void sendTo(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PartyRecord party = PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).orElse(null);
        send(player, party == null ? PartyRosterSyncPayload.empty() : snapshot(player.getServer(), party, player));
    }

    public static void syncParty(MinecraftServer server, UUID partyId) {
        syncParty(server, partyId, null, null);
    }

    public static void syncPartyWithOfflinePlayer(MinecraftServer server, UUID partyId, UUID offlinePlayerId) {
        syncParty(server, partyId, offlinePlayerId, null);
    }

    public static void syncPartyWithUnavailableVillager(MinecraftServer server, UUID partyId, UUID unavailableVillagerId) {
        syncParty(server, partyId, null, unavailableVillagerId);
    }

    private static void syncParty(
            MinecraftServer server,
            UUID partyId,
            UUID offlinePlayerId,
            UUID unavailableVillagerId) {
        if (server == null || partyId == null) {
            return;
        }
        PartyRecord party = PartyService.getParty(server.overworld(), partyId).orElse(null);
        if (party == null) {
            return;
        }
        for (UUID playerId : party.playerIds()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                send(player, snapshot(server, party, player, offlinePlayerId, unavailableVillagerId));
            }
        }
    }

    public static void clear(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            send(player, PartyRosterSyncPayload.empty());
        }
    }

    public static void clear(MinecraftServer server, List<UUID> playerIds) {
        if (playerIds == null) {
            return;
        }
        playerIds.forEach(playerId -> clear(server, playerId));
    }

    private static PartyRosterSyncPayload snapshot(MinecraftServer server, PartyRecord party, ServerPlayer recipient) {
        return snapshot(server, party, recipient, null, null);
    }

    private static PartyRosterSyncPayload snapshot(
            MinecraftServer server,
            PartyRecord party,
            ServerPlayer recipient,
            UUID offlinePlayerId,
            UUID unavailableVillagerId) {
        List<PartyRosterSyncPayload.PlayerEntry> players = new ArrayList<>(party.playerIds().size());
        for (UUID playerId : party.playerIds()) {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            players.add(new PartyRosterSyncPayload.PlayerEntry(
                    playerId,
                    online == null ? profileName(server, playerId) : online.getGameProfile().getName(),
                    online != null && !playerId.equals(offlinePlayerId),
                    playerId.equals(party.leaderId())));
        }
        long now = server.overworld().getGameTime();
        List<PartyRosterSyncPayload.VillagerEntry> villagers = new ArrayList<>(party.villagers().size());
        for (PartyVillagerRecord record : party.villagers()) {
            Villager loaded = findLoadedVillager(server, record.villagerId());
            villagers.add(new PartyRosterSyncPayload.VillagerEntry(
                    record.villagerId(),
                    loaded == null ? -1 : loaded.getId(),
                    record.cachedName(),
                    record.cachedProfession(),
                    record.commandMode(),
                    loaded != null && loaded.isAlive() && !record.villagerId().equals(unavailableVillagerId),
                    record.remainingDays(now),
                    record.combatMode(),
                    record.attackMode(),
                    record.dropCollectionMode(),
                    record.quickCommandsEnabled()));
        }
        return new PartyRosterSyncPayload(
                true,
                party.id(),
                profileName(server, party.leaderId()),
                recipient.getUUID().equals(party.leaderId()),
                combatModeState(party),
                attackModeState(party),
                party.sharedVillagerInventories(),
                List.copyOf(players),
                List.copyOf(villagers));
    }

    static PartyCombatModeState combatModeState(PartyRecord party) {
        if (party.villagers().isEmpty()) {
            return PartyCombatModeState.of(party.combatMode());
        }
        PartyCombatMode first = party.villagers().getFirst().combatMode();
        boolean mixed = party.villagers().stream().anyMatch(villager -> villager.combatMode() != first);
        return mixed ? PartyCombatModeState.CUSTOM : PartyCombatModeState.of(first);
    }

    static PartyAttackModeState attackModeState(PartyRecord party) {
        if (party.villagers().isEmpty()) {
            return PartyAttackModeState.of(party.attackMode());
        }
        PartyAttackMode first = party.villagers().getFirst().attackMode();
        boolean mixed = party.villagers().stream().anyMatch(villager -> villager.attackMode() != first);
        return mixed ? PartyAttackModeState.CUSTOM : PartyAttackModeState.of(first);
    }

    private static String profileName(MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getProfileCache()
                .get(playerId)
                .map(GameProfile::getName)
                .filter(name -> !name.isBlank())
                .orElse("Player");
    }

    private static Villager findLoadedVillager(MinecraftServer server, UUID villagerId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(villagerId);
            if (entity instanceof Villager villager) {
                return villager;
            }
        }
        return null;
    }

    private static void send(ServerPlayer player, PartyRosterSyncPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (UnsupportedOperationException ignored) {
            // Game tests can use mock connections without negotiated custom payloads.
        }
    }
}
