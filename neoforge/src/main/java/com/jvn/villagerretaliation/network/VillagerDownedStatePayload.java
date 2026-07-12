package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerDownedStatePayload(int entityId, boolean downed) implements CustomPacketPayload {
    public static final Type<VillagerDownedStatePayload> TYPE = VillagerPayloads.type("villager_downed_state");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerDownedStatePayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerDownedStatePayload::encode, VillagerDownedStatePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerDownedStatePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeBoolean(payload.downed());
    }

    private static VillagerDownedStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerDownedStatePayload(buffer.readVarInt(), buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
