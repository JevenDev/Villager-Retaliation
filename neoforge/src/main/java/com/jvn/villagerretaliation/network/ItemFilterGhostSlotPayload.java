package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Synchronizes an externally selected item into one of an open filter's ghost slots. */
public record ItemFilterGhostSlotPayload(int slot, ItemStack entry) implements CustomPacketPayload {
    public static final Type<ItemFilterGhostSlotPayload> TYPE = VillagerPayloads.type("item_filter_ghost_slot");
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemFilterGhostSlotPayload> STREAM_CODEC =
            VillagerPayloads.codec(ItemFilterGhostSlotPayload::encode, ItemFilterGhostSlotPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ItemFilterGhostSlotPayload payload) {
        buffer.writeByte(payload.slot());
        ItemStack.STREAM_CODEC.encode(buffer, payload.entry());
    }

    private static ItemFilterGhostSlotPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ItemFilterGhostSlotPayload(buffer.readByte(), ItemStack.STREAM_CODEC.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
