package com.jvn.villagerretaliation.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementRoutesPayload(int sequence, CompoundTag data) implements CustomPacketPayload {
    public static final Type<SettlementRoutesPayload> TYPE = VillagerPayloads.type("settlement_routes");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementRoutesPayload> STREAM_CODEC = VillagerPayloads.codec(
            (buffer, value) -> { buffer.writeVarInt(value.sequence); buffer.writeNbt(value.data); },
            buffer -> new SettlementRoutesPayload(buffer.readVarInt(), buffer.readNbt()));
    public SettlementRoutesPayload {
        if (sequence < 0 || data == null) throw new IllegalArgumentException("Invalid route response");
        data = data.copy();
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
