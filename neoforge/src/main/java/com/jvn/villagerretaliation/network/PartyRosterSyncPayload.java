package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.party.PartyAttackMode;
import com.jvn.villagerretaliation.party.PartyAttackModeState;
import com.jvn.villagerretaliation.party.PartyCommandMode;
import com.jvn.villagerretaliation.party.PartyCombatMode;
import com.jvn.villagerretaliation.party.PartyCombatModeState;
import com.jvn.villagerretaliation.party.PartyDropCollectionMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PartyRosterSyncPayload(
        boolean active,
        UUID partyId,
        String leaderName,
        boolean recipientLeader,
        PartyCombatModeState combatMode,
        PartyAttackModeState attackMode,
        boolean sharedVillagerInventories,
        boolean mountMode,
        boolean mountFeatureAvailable,
        ResourceLocation quickCommandMoveDimension,
        BlockPos quickCommandMoveTarget,
        boolean standGuardActive,
        List<PlayerEntry> players,
        List<VillagerEntry> villagers) implements CustomPacketPayload {
    private static final int MAX_PLAYERS = 4;
    private static final int MAX_VILLAGERS = 4;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_PROFESSION_LENGTH = 128;
    private static final int MAX_GENDER_LENGTH = 32;
    public static final Type<PartyRosterSyncPayload> TYPE = VillagerPayloads.type("party_roster_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyRosterSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(PartyRosterSyncPayload::encode, PartyRosterSyncPayload::decode);

    public static PartyRosterSyncPayload empty() {
        return new PartyRosterSyncPayload(false, null, "", false,
                PartyCombatModeState.ATTACK_WITH_PARTY, PartyAttackModeState.ALL,
                true, false, false, null, null, false, List.of(), List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PartyRosterSyncPayload payload) {
        buffer.writeBoolean(payload.active());
        if (!payload.active()) {
            return;
        }
        buffer.writeUUID(payload.partyId());
        buffer.writeUtf(payload.leaderName(), MAX_NAME_LENGTH);
        buffer.writeBoolean(payload.recipientLeader());
        buffer.writeEnum(payload.combatMode());
        buffer.writeEnum(payload.attackMode());
        buffer.writeBoolean(payload.sharedVillagerInventories());
        buffer.writeBoolean(payload.mountMode());
        buffer.writeBoolean(payload.mountFeatureAvailable());
        boolean hasMoveTarget = payload.quickCommandMoveDimension() != null
                && payload.quickCommandMoveTarget() != null;
        buffer.writeBoolean(hasMoveTarget);
        if (hasMoveTarget) {
            buffer.writeResourceLocation(payload.quickCommandMoveDimension());
            buffer.writeBlockPos(payload.quickCommandMoveTarget());
        }
        buffer.writeBoolean(payload.standGuardActive());
        buffer.writeVarInt(Math.min(MAX_PLAYERS, payload.players().size()));
        for (int i = 0; i < Math.min(MAX_PLAYERS, payload.players().size()); i++) {
            PlayerEntry player = payload.players().get(i);
            buffer.writeUUID(player.playerId());
            buffer.writeUtf(player.name(), MAX_NAME_LENGTH);
            buffer.writeBoolean(player.online());
            buffer.writeBoolean(player.leader());
            buffer.writeBoolean(player.adminPrivileges());
        }
        buffer.writeVarInt(Math.min(MAX_VILLAGERS, payload.villagers().size()));
        for (int i = 0; i < Math.min(MAX_VILLAGERS, payload.villagers().size()); i++) {
            VillagerEntry villager = payload.villagers().get(i);
            buffer.writeUUID(villager.villagerId());
            buffer.writeVarInt(villager.entityId());
            buffer.writeUtf(villager.name(), MAX_NAME_LENGTH);
            buffer.writeUtf(villager.professionKey(), MAX_PROFESSION_LENGTH);
            buffer.writeUtf(villager.genderName(), MAX_GENDER_LENGTH);
            buffer.writeEnum(villager.commandMode());
            buffer.writeBoolean(villager.available());
            buffer.writeVarInt(villager.remainingDays());
            buffer.writeVarLong(villager.contractEndGameTime());
            buffer.writeEnum(villager.combatMode());
            buffer.writeEnum(villager.attackMode());
            buffer.writeEnum(villager.dropCollectionMode());
            buffer.writeBoolean(villager.quickCommandsEnabled());
            buffer.writeBoolean(villager.assignedMount());
        }
    }

    private static PartyRosterSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return empty();
        }
        UUID partyId = buffer.readUUID();
        String leaderName = buffer.readUtf(MAX_NAME_LENGTH);
        boolean recipientLeader = buffer.readBoolean();
        PartyCombatModeState combatMode = buffer.readEnum(PartyCombatModeState.class);
        PartyAttackModeState attackMode = buffer.readEnum(PartyAttackModeState.class);
        boolean sharedVillagerInventories = buffer.readBoolean();
        boolean mountMode = buffer.readBoolean();
        boolean mountFeatureAvailable = buffer.readBoolean();
        boolean hasMoveTarget = buffer.readBoolean();
        ResourceLocation quickCommandMoveDimension = hasMoveTarget ? buffer.readResourceLocation() : null;
        BlockPos quickCommandMoveTarget = hasMoveTarget ? buffer.readBlockPos() : null;
        boolean standGuardActive = buffer.readBoolean();
        int playerCount = VillagerPayloads.readCollectionSize(buffer, MAX_PLAYERS, "party players");
        List<PlayerEntry> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            players.add(new PlayerEntry(
                    buffer.readUUID(),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readBoolean(),
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
                    buffer.readUtf(MAX_GENDER_LENGTH),
                    buffer.readEnum(PartyCommandMode.class),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarLong(),
                    buffer.readEnum(PartyCombatMode.class),
                    buffer.readEnum(PartyAttackMode.class),
                    buffer.readEnum(PartyDropCollectionMode.class),
                    buffer.readBoolean(),
                    buffer.readBoolean()));
        }
        return new PartyRosterSyncPayload(true, partyId, leaderName, recipientLeader,
                combatMode, attackMode, sharedVillagerInventories, mountMode, mountFeatureAvailable,
                quickCommandMoveDimension, quickCommandMoveTarget, standGuardActive,
                List.copyOf(players), List.copyOf(villagers));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record PlayerEntry(
            UUID playerId, String name, boolean online, boolean leader, boolean adminPrivileges) {
    }

    public record VillagerEntry(
            UUID villagerId,
            int entityId,
            String name,
            String professionKey,
            String genderName,
            PartyCommandMode commandMode,
            boolean available,
            int remainingDays,
            long contractEndGameTime,
            PartyCombatMode combatMode,
            PartyAttackMode attackMode,
            PartyDropCollectionMode dropCollectionMode,
            boolean quickCommandsEnabled,
            boolean assignedMount) {
    }
}
