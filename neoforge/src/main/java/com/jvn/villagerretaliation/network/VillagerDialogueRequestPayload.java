package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerDialogueRequestPayload(int entityId, DialogueRequestType requestType) implements CustomPacketPayload {
    public static final Type<VillagerDialogueRequestPayload> TYPE = VillagerPayloads.type("villager_dialogue_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerDialogueRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerDialogueRequestPayload::encode, VillagerDialogueRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerDialogueRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeEnum(payload.requestType());
    }

    private static VillagerDialogueRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerDialogueRequestPayload(buffer.readVarInt(), buffer.readEnum(DialogueRequestType.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
