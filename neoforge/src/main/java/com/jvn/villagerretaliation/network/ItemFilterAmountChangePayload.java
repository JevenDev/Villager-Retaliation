package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Requests one validated quantity adjustment for an open item-filter ghost slot. */
public record ItemFilterAmountChangePayload(int slot, int delta) implements CustomPacketPayload {
    public static final Type<ItemFilterAmountChangePayload> TYPE =
            VillagerPayloads.type("item_filter_amount_change");
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemFilterAmountChangePayload> STREAM_CODEC =
            VillagerPayloads.codec(ItemFilterAmountChangePayload::encode, ItemFilterAmountChangePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ItemFilterAmountChangePayload payload) {
        buffer.writeByte(payload.slot());
        buffer.writeByte(payload.delta());
    }

    private static ItemFilterAmountChangePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ItemFilterAmountChangePayload(buffer.readByte(), buffer.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
