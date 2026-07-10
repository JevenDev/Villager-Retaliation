package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PartyActionRequestPayload(Action action, UUID targetId, UUID invitationId) implements CustomPacketPayload {
    public static final Type<PartyActionRequestPayload> TYPE = VillagerPayloads.type("party_action_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyActionRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(PartyActionRequestPayload::encode, PartyActionRequestPayload::decode);

    public PartyActionRequestPayload(Action action) {
        this(action, null, null);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PartyActionRequestPayload payload) {
        buffer.writeEnum(payload.action());
        buffer.writeBoolean(payload.targetId() != null);
        if (payload.targetId() != null) {
            buffer.writeUUID(payload.targetId());
        }
        buffer.writeBoolean(payload.invitationId() != null);
        if (payload.invitationId() != null) {
            buffer.writeUUID(payload.invitationId());
        }
    }

    private static PartyActionRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID targetId = buffer.readBoolean() ? buffer.readUUID() : null;
        UUID invitationId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new PartyActionRequestPayload(action, targetId, invitationId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        SEND_INVITATION,
        ACCEPT_INVITATION,
        DECLINE_INVITATION,
        LEAVE_PARTY,
        REMOVE_PLAYER,
        DISBAND_PARTY
    }
}
