package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenPlayerPartyMenuPayload(
        UUID targetId,
        String targetName,
        boolean canInvite,
        boolean canRemove) implements CustomPacketPayload {
    private static final int MAX_NAME_LENGTH = 64;
    public static final Type<OpenPlayerPartyMenuPayload> TYPE = VillagerPayloads.type("open_player_party_menu");
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPlayerPartyMenuPayload> STREAM_CODEC =
            VillagerPayloads.codec(OpenPlayerPartyMenuPayload::encode, OpenPlayerPartyMenuPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, OpenPlayerPartyMenuPayload payload) {
        buffer.writeUUID(payload.targetId());
        buffer.writeUtf(payload.targetName(), MAX_NAME_LENGTH);
        buffer.writeBoolean(payload.canInvite());
        buffer.writeBoolean(payload.canRemove());
    }

    private static OpenPlayerPartyMenuPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenPlayerPartyMenuPayload(
                buffer.readUUID(),
                buffer.readUtf(MAX_NAME_LENGTH),
                buffer.readBoolean(),
                buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
