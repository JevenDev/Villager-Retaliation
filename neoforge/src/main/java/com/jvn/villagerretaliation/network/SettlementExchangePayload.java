package com.jvn.villagerretaliation.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementExchangePayload(int sequence, CompoundTag data) implements CustomPacketPayload {
    public static final Type<SettlementExchangePayload> TYPE = VillagerPayloads.type("settlement_exchange");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementExchangePayload> STREAM_CODEC = VillagerPayloads.codec(
            (buffer, value) -> { buffer.writeVarInt(value.sequence); buffer.writeNbt(value.data); },
            buffer -> new SettlementExchangePayload(buffer.readVarInt(), buffer.readNbt()));
    public SettlementExchangePayload {
        if (sequence < 0 || data == null) throw new IllegalArgumentException("Invalid exchange response");
        data = data.copy();
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
