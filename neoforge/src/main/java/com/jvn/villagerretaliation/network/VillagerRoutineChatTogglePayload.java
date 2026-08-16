package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerRoutineChatTogglePayload(int entityId, boolean muted) implements CustomPacketPayload {
    public static final Type<VillagerRoutineChatTogglePayload> TYPE = VillagerPayloads.type("villager_routine_chat_toggle");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerRoutineChatTogglePayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerRoutineChatTogglePayload::encode, VillagerRoutineChatTogglePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerRoutineChatTogglePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeBoolean(payload.muted());
    }

    private static VillagerRoutineChatTogglePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerRoutineChatTogglePayload(buffer.readVarInt(), buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
