package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementManagementActionResultPayload(UUID town, boolean applied) implements CustomPacketPayload {
    public static final Type<SettlementManagementActionResultPayload> TYPE = VillagerPayloads.type("settlement_management_action_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementManagementActionResultPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> {
                buffer.writeUUID(payload.town());
                buffer.writeBoolean(payload.applied());
            }, buffer -> new SettlementManagementActionResultPayload(buffer.readUUID(), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
