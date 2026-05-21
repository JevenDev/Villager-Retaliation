package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerDialogueRequestPayload(int entityId, String optionId) implements CustomPacketPayload {
    public static final Type<VillagerDialogueRequestPayload> TYPE = VillagerPayloads.type("villager_dialogue_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerDialogueRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerDialogueRequestPayload::encode, VillagerDialogueRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerDialogueRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.optionId(), 128);
    }

    private static VillagerDialogueRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerDialogueRequestPayload(buffer.readVarInt(), buffer.readUtf(128));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
