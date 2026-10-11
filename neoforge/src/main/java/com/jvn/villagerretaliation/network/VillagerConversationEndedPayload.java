package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerConversationEndedPayload(int entityId, String goodbyeText, boolean finishExchanges, com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera camera) implements CustomPacketPayload {
    public VillagerConversationEndedPayload(int entityId, String goodbyeText, boolean finishExchanges) {
        this(entityId, goodbyeText, finishExchanges, null);
    }

    public VillagerConversationEndedPayload(int entityId, String goodbyeText) {
        this(entityId, goodbyeText, false);
    }

    public static final Type<VillagerConversationEndedPayload> TYPE = VillagerPayloads.type("villager_conversation_ended");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerConversationEndedPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerConversationEndedPayload::encode, VillagerConversationEndedPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerConversationEndedPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.goodbyeText(), 512);
        buffer.writeBoolean(payload.finishExchanges());
        com.jvn.villagerretaliation.dialogue.DialogueCamera.write(buffer, payload.camera());
    }

    private static VillagerConversationEndedPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerConversationEndedPayload(buffer.readVarInt(), buffer.readUtf(512), buffer.readBoolean(), com.jvn.villagerretaliation.dialogue.DialogueCamera.read(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
