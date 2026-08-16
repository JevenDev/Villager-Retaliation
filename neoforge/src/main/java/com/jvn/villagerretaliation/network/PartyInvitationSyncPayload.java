package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PartyInvitationSyncPayload(UUID invitationId, String inviterName, long expiresGameTime)
        implements CustomPacketPayload {
    private static final int MAX_NAME_LENGTH = 64;
    public static final Type<PartyInvitationSyncPayload> TYPE = VillagerPayloads.type("party_invitation_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyInvitationSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(PartyInvitationSyncPayload::encode, PartyInvitationSyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, PartyInvitationSyncPayload payload) {
        buffer.writeUUID(payload.invitationId());
        buffer.writeUtf(payload.inviterName(), MAX_NAME_LENGTH);
        buffer.writeLong(payload.expiresGameTime());
    }

    private static PartyInvitationSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new PartyInvitationSyncPayload(
                buffer.readUUID(),
                buffer.readUtf(MAX_NAME_LENGTH),
                buffer.readLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
