package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.duel.DuelAvailabilityReason;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenVillagerDuelPayload(
        int entityId,
        String villagerName,
        boolean available,
        DuelAvailabilityReason reason,
        int villagerWins,
        int villagerLosses,
        int consecutiveLosses,
        long cooldownTicks,
        int arenaRadius,
        int boundaryGraceTicks,
        int timeoutTicks,
        int cooldownDays,
        int playerBalance,
        int villagerBalance,
        String currencyName) implements CustomPacketPayload {
    private static final int MAX_TEXT = 128;
    public static final Type<OpenVillagerDuelPayload> TYPE = VillagerPayloads.type("open_villager_duel");
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVillagerDuelPayload> STREAM_CODEC =
            VillagerPayloads.codec(OpenVillagerDuelPayload::encode, OpenVillagerDuelPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, OpenVillagerDuelPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.villagerName(), MAX_TEXT);
        buffer.writeBoolean(payload.available());
        buffer.writeEnum(payload.reason());
        buffer.writeVarInt(payload.villagerWins());
        buffer.writeVarInt(payload.villagerLosses());
        buffer.writeVarInt(payload.consecutiveLosses());
        buffer.writeVarLong(payload.cooldownTicks());
        buffer.writeVarInt(payload.arenaRadius());
        buffer.writeVarInt(payload.boundaryGraceTicks());
        buffer.writeVarInt(payload.timeoutTicks());
        buffer.writeVarInt(payload.cooldownDays());
        buffer.writeVarInt(payload.playerBalance());
        buffer.writeVarInt(payload.villagerBalance());
        buffer.writeUtf(payload.currencyName(), MAX_TEXT);
    }

    private static OpenVillagerDuelPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenVillagerDuelPayload(buffer.readVarInt(), buffer.readUtf(MAX_TEXT), buffer.readBoolean(),
                buffer.readEnum(DuelAvailabilityReason.class), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(MAX_TEXT));
    }

    public int maximumStake() {
        return Math.min(this.playerBalance, this.villagerBalance);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
