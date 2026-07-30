package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ItemFilterCombinationChangePayload(int combinationId) implements CustomPacketPayload {
    public static final Type<ItemFilterCombinationChangePayload> TYPE =
            VillagerPayloads.type("item_filter_combination_change");
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemFilterCombinationChangePayload> STREAM_CODEC =
            VillagerPayloads.codec(
                    ItemFilterCombinationChangePayload::encode,
                    ItemFilterCombinationChangePayload::decode);

    public ItemFilterCombinationChangePayload(VillagerItemFilterData.EntryCombination combination) {
        this(combination == null ? -1 : combination.networkId());
    }

    public VillagerItemFilterData.EntryCombination requestedCombination() {
        return VillagerItemFilterData.EntryCombination.fromNetworkId(this.combinationId);
    }

    public boolean isValid() {
        return requestedCombination() != null;
    }

    private static void encode(
            RegistryFriendlyByteBuf buffer, ItemFilterCombinationChangePayload payload) {
        buffer.writeByte(payload.combinationId());
    }

    private static ItemFilterCombinationChangePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ItemFilterCombinationChangePayload(buffer.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
