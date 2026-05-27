package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerTradeRefreshStatePayload(int entityId, List<Integer> pendingOfferIndexes) implements CustomPacketPayload {
    public static final Type<VillagerTradeRefreshStatePayload> TYPE = VillagerPayloads.type("villager_trade_refresh_state");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerTradeRefreshStatePayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerTradeRefreshStatePayload::encode, VillagerTradeRefreshStatePayload::decode);

    public VillagerTradeRefreshStatePayload {
        pendingOfferIndexes = List.copyOf(pendingOfferIndexes);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerTradeRefreshStatePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeVarInt(payload.pendingOfferIndexes().size());
        for (int offerIndex : payload.pendingOfferIndexes()) {
            buffer.writeVarInt(offerIndex);
        }
    }

    private static VillagerTradeRefreshStatePayload decode(RegistryFriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        int size = buffer.readVarInt();
        List<Integer> pendingOfferIndexes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            pendingOfferIndexes.add(buffer.readVarInt());
        }
        return new VillagerTradeRefreshStatePayload(entityId, pendingOfferIndexes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
