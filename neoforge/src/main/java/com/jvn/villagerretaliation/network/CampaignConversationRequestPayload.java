package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CampaignConversationRequestPayload(UUID viewId, UUID requestId, int entityId, UUID character,
        Action action, UUID session, long revision, String conversation, String argument, int page, int textPage) implements CustomPacketPayload {
    public static final Type<CampaignConversationRequestPayload> TYPE = VillagerPayloads.type("campaign_conversation_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, CampaignConversationRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(CampaignConversationRequestPayload::encode, CampaignConversationRequestPayload::decode);

    public CampaignConversationRequestPayload {
        if (viewId == null || requestId == null || character == null || action == null || entityId < 0 || revision < 0 || page < 0 || textPage < 0
                || conversation == null || conversation.length() > 256 || argument == null || argument.length() > 256) {
            throw new IllegalArgumentException("Invalid campaign conversation request");
        }
        if (action == Action.START ? session != null || conversation.isBlank() || argument.isBlank()
                : !conversation.isEmpty() || action != Action.CHOOSE && action != Action.HAND_OVER && action != Action.CONFIRM && action != Action.DECLINE && !argument.isEmpty()) {
            throw new IllegalArgumentException("Unexpected campaign conversation arguments");
        }
        if (action != Action.TOPICS && action != Action.START && action != Action.CLOSE && session == null) {
            throw new IllegalArgumentException("Campaign conversation request requires a session");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, CampaignConversationRequestPayload value) {
        buffer.writeUUID(value.viewId());
        buffer.writeUUID(value.requestId());
        buffer.writeVarInt(value.entityId());
        buffer.writeUUID(value.character());
        buffer.writeEnum(value.action());
        buffer.writeBoolean(value.session() != null);
        if (value.session() != null) buffer.writeUUID(value.session());
        buffer.writeVarLong(value.revision());
        buffer.writeUtf(value.conversation(), 256);
        buffer.writeUtf(value.argument(), 256);
        buffer.writeVarInt(value.page());
        buffer.writeVarInt(value.textPage());
    }

    private static CampaignConversationRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new CampaignConversationRequestPayload(buffer.readUUID(), buffer.readUUID(), buffer.readVarInt(), buffer.readUUID(),
                buffer.readEnum(Action.class), buffer.readBoolean() ? buffer.readUUID() : null, buffer.readVarLong(),
                buffer.readUtf(256), buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt());
    }

    public enum Action { TOPICS, START, VIEW, CLOSE, ADVANCE, CHOOSE, ROLL, RESUME, TAKE_CONTROL, HAND_OVER, CONFIRM, DECLINE }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
