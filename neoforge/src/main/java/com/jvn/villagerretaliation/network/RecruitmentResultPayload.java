package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentCommand;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentSnapshot;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentState;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RecruitmentResultPayload(
        int entityId,
        boolean success,
        FailureReason failureReason,
        VillagerAssignmentSnapshot assignment,
        String responseKey) implements CustomPacketPayload {
    private static final int RESPONSE_KEY_LENGTH = 256;
    public static final Type<RecruitmentResultPayload> TYPE = VillagerPayloads.type("recruitment_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, RecruitmentResultPayload> STREAM_CODEC =
            VillagerPayloads.codec(RecruitmentResultPayload::encode, RecruitmentResultPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, RecruitmentResultPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeBoolean(payload.success());
        buffer.writeEnum(payload.failureReason());
        VillagerAssignmentSnapshot snapshot = payload.assignment();
        buffer.writeEnum(snapshot.state());
        buffer.writeBoolean(snapshot.owner().isPresent());
        snapshot.owner().ifPresent(buffer::writeUUID);
        buffer.writeEnum(snapshot.command());
        buffer.writeBoolean(snapshot.role() != null);
        if (snapshot.role() != null) buffer.writeEnum(snapshot.role());
        writePos(buffer, snapshot.workAnchor());
        writePos(buffer, snapshot.homeAnchor());
        buffer.writeVarLong(snapshot.hiredAt());
        buffer.writeVarLong(snapshot.revision());
        buffer.writeVarInt(snapshot.schemaVersion());
        buffer.writeUtf(payload.responseKey() == null ? "" : payload.responseKey(), RESPONSE_KEY_LENGTH);
    }

    private static RecruitmentResultPayload decode(RegistryFriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        boolean success = buffer.readBoolean();
        FailureReason reason = buffer.readEnum(FailureReason.class);
        VillagerAssignmentState state = buffer.readEnum(VillagerAssignmentState.class);
        Optional<UUID> owner = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        VillagerAssignmentCommand command = buffer.readEnum(VillagerAssignmentCommand.class);
        HiredVillagerRole role = buffer.readBoolean() ? buffer.readEnum(HiredVillagerRole.class) : null;
        BlockPos workAnchor = readPos(buffer);
        BlockPos homeAnchor = readPos(buffer);
        VillagerAssignmentSnapshot snapshot = new VillagerAssignmentSnapshot(
                state, owner, command, role, workAnchor, homeAnchor,
                buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt());
        return new RecruitmentResultPayload(entityId, success, reason, snapshot, buffer.readUtf(RESPONSE_KEY_LENGTH));
    }

    private static void writePos(RegistryFriendlyByteBuf buffer, BlockPos pos) {
        buffer.writeBoolean(pos != null);
        if (pos != null) buffer.writeBlockPos(pos);
    }

    private static BlockPos readPos(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readBlockPos() : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum FailureReason {
        NONE,
        INVALID_TARGET,
        STALE_STATE,
        NOT_OWNER,
        ALREADY_OWNED,
        INELIGIBLE,
        REPUTATION_TOO_LOW,
        HIRE_CAP_REACHED,
        INSUFFICIENT_PAYMENT,
        BUSY,
        INVALID_ROLE,
        INVALID_TRANSITION,
        CONVERSATION_ENDED,
        UNKNOWN
    }
}
