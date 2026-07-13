package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.party.PartyQuickCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PartyQuickCommandRequestPayload(
        PartyQuickCommand command,
        int targetEntityId,
        BlockPos targetPosition) implements CustomPacketPayload {
    public static final int NO_ENTITY = -1;
    public static final Type<PartyQuickCommandRequestPayload> TYPE =
            VillagerPayloads.type("party_quick_command_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyQuickCommandRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(PartyQuickCommandRequestPayload::encode, PartyQuickCommandRequestPayload::decode);

    public PartyQuickCommandRequestPayload(PartyQuickCommand command) {
        this(command, NO_ENTITY, null);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PartyQuickCommandRequestPayload payload) {
        buffer.writeEnum(payload.command());
        buffer.writeVarInt(payload.targetEntityId());
        buffer.writeBoolean(payload.targetPosition() != null);
        if (payload.targetPosition() != null) {
            buffer.writeBlockPos(payload.targetPosition());
        }
    }

    private static PartyQuickCommandRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        PartyQuickCommand command = buffer.readEnum(PartyQuickCommand.class);
        int targetEntityId = buffer.readVarInt();
        BlockPos targetPosition = buffer.readBoolean() ? buffer.readBlockPos() : null;
        return new PartyQuickCommandRequestPayload(command, targetEntityId, targetPosition);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
