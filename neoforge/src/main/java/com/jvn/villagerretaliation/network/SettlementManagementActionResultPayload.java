package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementManagementActionResultPayload(UUID town, boolean applied, String reason, net.minecraft.world.item.ItemStack preview, int requestId) implements CustomPacketPayload {
    public static final Type<SettlementManagementActionResultPayload> TYPE = VillagerPayloads.type("settlement_management_action_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementManagementActionResultPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> {
                buffer.writeUUID(payload.town());
                buffer.writeBoolean(payload.applied());
                buffer.writeUtf(payload.reason(), 512);
                buffer.writeVarInt(payload.requestId());
                net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.preview());
            }, buffer -> new SettlementManagementActionResultPayload(buffer.readUUID(), buffer.readBoolean(),
                    buffer.readUtf(512), buffer.readVarInt(), buffer));

    private SettlementManagementActionResultPayload(UUID town, boolean applied, String reason, int requestId, RegistryFriendlyByteBuf buffer) {
        this(town, applied, reason, net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), requestId);
    }

    public SettlementManagementActionResultPayload(UUID town, boolean applied, String reason, net.minecraft.world.item.ItemStack preview) {
        this(town, applied, reason, preview, 0);
    }

    public SettlementManagementActionResultPayload(UUID town, boolean applied) {
        this(town, applied, "", net.minecraft.world.item.ItemStack.EMPTY);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
