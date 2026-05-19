package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerGiftRequestPayload(int entityId, int inventorySlot) implements CustomPacketPayload {
    public static final Type<VillagerGiftRequestPayload> TYPE = new Type<>(
            VillagerRetaliation.id("villager_gift_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerGiftRequestPayload> STREAM_CODEC =
            StreamCodec.of(VillagerGiftRequestPayload::encode, VillagerGiftRequestPayload::decode);

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
