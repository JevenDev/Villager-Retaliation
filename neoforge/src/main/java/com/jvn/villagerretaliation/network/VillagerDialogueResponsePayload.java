package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerDialogueResponsePayload(
        int entityId,
        DialogueRequestType requestType,
        String text,
        int reputation,
        VillagerReputationLevel reputationLevel)
        implements CustomPacketPayload {
    public static final Type<VillagerDialogueResponsePayload> TYPE = new Type<>(
            VillagerRetaliation.id("villager_dialogue_response")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerDialogueResponsePayload> STREAM_CODEC =
            StreamCodec.of(VillagerDialogueResponsePayload::encode, VillagerDialogueResponsePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerDialogueResponsePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeEnum(payload.requestType());
        buffer.writeUtf(payload.text(), 512);
        buffer.writeVarInt(payload.reputation());
        buffer.writeEnum(payload.reputationLevel());
    }

    private static VillagerDialogueResponsePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerDialogueResponsePayload(
                buffer.readVarInt(),
                buffer.readEnum(DialogueRequestType.class),
                buffer.readUtf(512),
                buffer.readVarInt(),
                buffer.readEnum(VillagerReputationLevel.class)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
