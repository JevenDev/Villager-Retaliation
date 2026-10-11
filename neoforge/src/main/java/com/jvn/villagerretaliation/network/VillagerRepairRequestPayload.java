package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerRepairRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<VillagerRepairRequestPayload> TYPE = VillagerPayloads.type("villager_repair_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerRepairRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerRepairRequestPayload::encode, VillagerRepairRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerRepairRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
    }

    private static VillagerRepairRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerRepairRequestPayload(buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
