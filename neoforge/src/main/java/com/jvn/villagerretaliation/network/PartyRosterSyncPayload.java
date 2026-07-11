package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.party.PartyCommandMode;
import com.jvn.villagerretaliation.party.PartyDropCollectionMode;
import com.jvn.villagerretaliation.party.PartyPolicyState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PartyRosterSyncPayload(
        boolean active,
        UUID partyId,
        String leaderName,
        boolean recipientLeader,
        PartyPolicyState attackWithParty,
        PartyPolicyState defendParty,
        boolean sharedVillagerInventories,
        List<PlayerEntry> players,
        List<VillagerEntry> villagers) implements CustomPacketPayload {
    private static final int MAX_PLAYERS = 4;
    private static final int MAX_VILLAGERS = 4;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_PROFESSION_LENGTH = 128;
    public static final Type<PartyRosterSyncPayload> TYPE = VillagerPayloads.type("party_roster_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyRosterSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(PartyRosterSyncPayload::encode, PartyRosterSyncPayload::decode);

    public static PartyRosterSyncPayload empty() {
        return new PartyRosterSyncPayload(false, null, "", false,
                PartyPolicyState.ON, PartyPolicyState.ON, true, List.of(), List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PartyRosterSyncPayload payload) {
        buffer.writeBoolean(payload.active());
        if (!payload.active()) {
            return;
        }
        buffer.writeUUID(payload.partyId());
        buffer.writeUtf(payload.leaderName(), MAX_NAME_LENGTH);
        buffer.writeBoolean(payload.recipientLeader());
        buffer.writeEnum(payload.attackWithParty());
        buffer.writeEnum(payload.defendParty());
        buffer.writeBoolean(payload.sharedVillagerInventories());
        buffer.writeVarInt(Math.min(MAX_PLAYERS, payload.players().size()));
        for (int i = 0; i < Math.min(MAX_PLAYERS, payload.players().size()); i++) {
            PlayerEntry player = payload.players().get(i);
            buffer.writeUUID(player.playerId());
            buffer.writeUtf(player.name(), MAX_NAME_LENGTH);
            buffer.writeBoolean(player.online());
            buffer.writeBoolean(player.leader());
        }
        buffer.writeVarInt(Math.min(MAX_VILLAGERS, payload.villagers().size()));
        for (int i = 0; i < Math.min(MAX_VILLAGERS, payload.villagers().size()); i++) {
            VillagerEntry villager = payload.villagers().get(i);
            buffer.writeUUID(villager.villagerId());
            buffer.writeVarInt(villager.entityId());
            buffer.writeUtf(villager.name(), MAX_NAME_LENGTH);
            buffer.writeUtf(villager.professionKey(), MAX_PROFESSION_LENGTH);
            buffer.writeEnum(villager.commandMode());
            buffer.writeBoolean(villager.available());
            buffer.writeVarInt(villager.remainingDays());
            buffer.writeBoolean(villager.attackWithParty());
            buffer.writeBoolean(villager.defendParty());
            buffer.writeEnum(villager.dropCollectionMode());
        }
    }

    private static PartyRosterSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return empty();
        }
        UUID partyId = buffer.readUUID();
        String leaderName = buffer.readUtf(MAX_NAME_LENGTH);
        boolean recipientLeader = buffer.readBoolean();
        PartyPolicyState attackWithParty = buffer.readEnum(PartyPolicyState.class);
        PartyPolicyState defendParty = buffer.readEnum(PartyPolicyState.class);
        boolean sharedVillagerInventories = buffer.readBoolean();
        int playerCount = VillagerPayloads.readCollectionSize(buffer, MAX_PLAYERS, "party players");
        List<PlayerEntry> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            players.add(new PlayerEntry(
                    buffer.readUUID(),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readBoolean(),
                    buffer.readBoolean()));
        }
        int villagerCount = VillagerPayloads.readCollectionSize(buffer, MAX_VILLAGERS, "party villagers");
        List<VillagerEntry> villagers = new ArrayList<>(villagerCount);
        for (int i = 0; i < villagerCount; i++) {
            villagers.add(new VillagerEntry(
                    buffer.readUUID(),
                    buffer.readVarInt(),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readUtf(MAX_PROFESSION_LENGTH),
                    buffer.readEnum(PartyCommandMode.class),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readEnum(PartyDropCollectionMode.class)));
        }
        return new PartyRosterSyncPayload(true, partyId, leaderName, recipientLeader,
                attackWithParty, defendParty, sharedVillagerInventories,
                List.copyOf(players), List.copyOf(villagers));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record PlayerEntry(UUID playerId, String name, boolean online, boolean leader) {
    }

    public record VillagerEntry(
            UUID villagerId,
            int entityId,
            String name,
            String professionKey,
            PartyCommandMode commandMode,
            boolean available,
            int remainingDays,
            boolean attackWithParty,
            boolean defendParty,
            PartyDropCollectionMode dropCollectionMode) {
    }
}
