package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementManagementActionResultPayload(UUID town, boolean applied, UUID request, String failure) implements CustomPacketPayload {
    public SettlementManagementActionResultPayload(UUID town, boolean applied) {
        this(town, applied, null, "");
    }

    public SettlementManagementActionResultPayload {
        if (town == null || failure == null || failure.length() > 64) throw new IllegalArgumentException("Invalid settlement action result");
    }
    public static final Type<SettlementManagementActionResultPayload> TYPE = VillagerPayloads.type("settlement_management_action_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementManagementActionResultPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> {
                buffer.writeUUID(payload.town());
                buffer.writeBoolean(payload.applied());
                buffer.writeBoolean(payload.request() != null);
                if (payload.request() != null) buffer.writeUUID(payload.request());
                buffer.writeUtf(payload.failure(), 64);
            }, buffer -> new SettlementManagementActionResultPayload(buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean() ? buffer.readUUID() : null, buffer.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
