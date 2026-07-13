package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerHungerSyncPayload(int entityId, int hunger) implements CustomPacketPayload {
    public static final Type<VillagerHungerSyncPayload> TYPE = VillagerPayloads.type("villager_hunger_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerHungerSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerHungerSyncPayload::encode, VillagerHungerSyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerHungerSyncPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeVarInt(payload.hunger());
    }

    private static VillagerHungerSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerHungerSyncPayload(buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
