package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerNameSyncPayload(int entityId, UUID villagerId, String nameKey, String fallbackName)
        implements CustomPacketPayload {
    public static final Type<VillagerNameSyncPayload> TYPE = VillagerPayloads.type("villager_name_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerNameSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerNameSyncPayload::encode, VillagerNameSyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerNameSyncPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUUID(payload.villagerId());
        buffer.writeUtf(payload.nameKey());
        buffer.writeUtf(payload.fallbackName());
    }

    private static VillagerNameSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerNameSyncPayload(
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readUtf(),
                buffer.readUtf()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
