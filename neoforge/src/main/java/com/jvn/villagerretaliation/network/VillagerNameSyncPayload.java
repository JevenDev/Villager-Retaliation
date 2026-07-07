package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerNameSyncPayload(int entityId, UUID villagerId, String nameKey, String fallbackName)
        implements CustomPacketPayload {
    private static final int NAME_KEY_LENGTH = 256;
    private static final int FALLBACK_NAME_LENGTH = 128;
    public static final Type<VillagerNameSyncPayload> TYPE = VillagerPayloads.type("villager_name_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerNameSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerNameSyncPayload::encode, VillagerNameSyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerNameSyncPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUUID(payload.villagerId());
        buffer.writeUtf(payload.nameKey(), NAME_KEY_LENGTH);
        buffer.writeUtf(payload.fallbackName(), FALLBACK_NAME_LENGTH);
    }

    private static VillagerNameSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerNameSyncPayload(
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readUtf(NAME_KEY_LENGTH),
                buffer.readUtf(FALLBACK_NAME_LENGTH)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
