package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenVillagerInteractionPayload(
        int entityId,
        String villagerNameKey,
        String villagerNameFallback,
        String professionName,
        boolean baby,
        int reputation,
        VillagerReputationLevel reputationLevel)
        implements CustomPacketPayload {
    public static final Type<OpenVillagerInteractionPayload> TYPE = VillagerPayloads.type("open_villager_interaction");
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVillagerInteractionPayload> STREAM_CODEC =
            VillagerPayloads.codec(OpenVillagerInteractionPayload::encode, OpenVillagerInteractionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, OpenVillagerInteractionPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.villagerNameKey());
        buffer.writeUtf(payload.villagerNameFallback());
        buffer.writeUtf(payload.professionName());
        buffer.writeBoolean(payload.baby());
        buffer.writeVarInt(payload.reputation());
        buffer.writeEnum(payload.reputationLevel());
    }

    private static OpenVillagerInteractionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenVillagerInteractionPayload(
                buffer.readVarInt(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readEnum(VillagerReputationLevel.class)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
