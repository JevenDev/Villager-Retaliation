package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerReputationSyncPayload(int entityId, UUID villagerId, int reputation, VillagerReputationLevel level)
        implements CustomPacketPayload {
    public static final Type<VillagerReputationSyncPayload> TYPE = VillagerPayloads.type("villager_reputation_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerReputationSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerReputationSyncPayload::encode, VillagerReputationSyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerReputationSyncPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUUID(payload.villagerId());
        buffer.writeVarInt(payload.reputation());
        buffer.writeEnum(payload.level());
    }

    private static VillagerReputationSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerReputationSyncPayload(
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readEnum(VillagerReputationLevel.class)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
