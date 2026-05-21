package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerInventoryRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<VillagerInventoryRequestPayload> TYPE = VillagerPayloads.type("villager_inventory_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerInventoryRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerInventoryRequestPayload::encode, VillagerInventoryRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerInventoryRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
    }

    private static VillagerInventoryRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerInventoryRequestPayload(buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
