package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerGiftRequestPayload(int entityId, int inventorySlot) implements CustomPacketPayload {
    public static final Type<VillagerGiftRequestPayload> TYPE = VillagerPayloads.type("villager_gift_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerGiftRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerGiftRequestPayload::encode, VillagerGiftRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerGiftRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeVarInt(payload.inventorySlot());
    }

    private static VillagerGiftRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerGiftRequestPayload(buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
