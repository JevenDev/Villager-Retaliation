package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ItemFilterModeChangePayload(int menuSlotIndex, int modeId) implements CustomPacketPayload {
    public static final Type<ItemFilterModeChangePayload> TYPE = VillagerPayloads.type("item_filter_mode_change");
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemFilterModeChangePayload> STREAM_CODEC =
            VillagerPayloads.codec(ItemFilterModeChangePayload::encode, ItemFilterModeChangePayload::decode);

    public ItemFilterModeChangePayload(int menuSlotIndex) {
        this(menuSlotIndex, -1);
    }

    public ItemFilterModeChangePayload(int menuSlotIndex, VillagerItemFilterData.Mode mode) {
        this(menuSlotIndex, mode == VillagerItemFilterData.Mode.DENYLIST ? 1 : 0);
    }

    public VillagerItemFilterData.Mode requestedMode() {
        return switch (this.modeId) {
            case 0 -> VillagerItemFilterData.Mode.ALLOWLIST;
            case 1 -> VillagerItemFilterData.Mode.DENYLIST;
            default -> null;
        };
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ItemFilterModeChangePayload payload) {
        buffer.writeInt(payload.menuSlotIndex());
        buffer.writeByte(payload.modeId());
    }

    private static ItemFilterModeChangePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ItemFilterModeChangePayload(buffer.readInt(), buffer.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
