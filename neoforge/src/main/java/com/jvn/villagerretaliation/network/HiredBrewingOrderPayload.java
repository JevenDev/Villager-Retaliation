package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HiredBrewingOrderPayload(
        int entityId,
        ResourceLocation itemId,
        ResourceLocation potionId,
        int amount,
        boolean continuous) implements CustomPacketPayload {
    public static final Type<HiredBrewingOrderPayload> TYPE = VillagerPayloads.type("hired_brewing_order");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredBrewingOrderPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredBrewingOrderPayload::encode, HiredBrewingOrderPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, HiredBrewingOrderPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeResourceLocation(payload.itemId());
        buffer.writeResourceLocation(payload.potionId());
        buffer.writeVarInt(payload.amount());
        buffer.writeBoolean(payload.continuous());
    }

    private static HiredBrewingOrderPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredBrewingOrderPayload(
                buffer.readVarInt(),
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readVarInt(),
                buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
