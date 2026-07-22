package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DuelInventoryStatePayload(boolean active, boolean assignedLoadout)
        implements CustomPacketPayload {
    public static final Type<DuelInventoryStatePayload> TYPE = VillagerPayloads.type("duel_inventory_state");
    public static final StreamCodec<RegistryFriendlyByteBuf, DuelInventoryStatePayload> STREAM_CODEC =
            VillagerPayloads.codec(DuelInventoryStatePayload::encode, DuelInventoryStatePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, DuelInventoryStatePayload payload) {
        buffer.writeBoolean(payload.active());
        buffer.writeBoolean(payload.assignedLoadout());
    }

    private static DuelInventoryStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new DuelInventoryStatePayload(buffer.readBoolean(), buffer.readBoolean());
    }

    public DuelInventoryStatePayload {
        if (!active) assignedLoadout = false;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
