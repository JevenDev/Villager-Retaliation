package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

public record BlueprintChecklistTogglePayload(InteractionHand hand, int entryIndex)
        implements CustomPacketPayload {
    public static final Type<BlueprintChecklistTogglePayload> TYPE =
            VillagerPayloads.type("blueprint_checklist_toggle");
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintChecklistTogglePayload> STREAM_CODEC =
            VillagerPayloads.codec(BlueprintChecklistTogglePayload::encode, BlueprintChecklistTogglePayload::decode);

    public BlueprintChecklistTogglePayload {
        hand = hand == null ? InteractionHand.MAIN_HAND : hand;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, BlueprintChecklistTogglePayload payload) {
        buffer.writeEnum(payload.hand());
        buffer.writeVarInt(payload.entryIndex());
    }

    private static BlueprintChecklistTogglePayload decode(RegistryFriendlyByteBuf buffer) {
        return new BlueprintChecklistTogglePayload(
                buffer.readEnum(InteractionHand.class), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
