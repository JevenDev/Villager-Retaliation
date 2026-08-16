package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerJobInventoryRequestPayload(int entityId, boolean jobInventory) implements CustomPacketPayload {
    public static final Type<VillagerJobInventoryRequestPayload> TYPE = VillagerPayloads.type("villager_job_inventory_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerJobInventoryRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerJobInventoryRequestPayload::encode, VillagerJobInventoryRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerJobInventoryRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeBoolean(payload.jobInventory());
    }

    private static VillagerJobInventoryRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerJobInventoryRequestPayload(buffer.readVarInt(), buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
