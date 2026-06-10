package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ConstructionBlueprintPlacementPayload(UUID jobId, Action action, int steps, BlockPos targetPos)
        implements CustomPacketPayload {
    public static final Type<ConstructionBlueprintPlacementPayload> TYPE = VillagerPayloads.type("construction_blueprint_placement");
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionBlueprintPlacementPayload> STREAM_CODEC =
            VillagerPayloads.codec(ConstructionBlueprintPlacementPayload::encode, ConstructionBlueprintPlacementPayload::decode);

    public ConstructionBlueprintPlacementPayload {
        steps = Math.max(1, Math.min(8, steps));
        targetPos = targetPos == null ? BlockPos.ZERO : targetPos.immutable();
    }

    public ConstructionBlueprintPlacementPayload(UUID jobId, Action action, int steps) {
        this(jobId, action, steps, BlockPos.ZERO);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ConstructionBlueprintPlacementPayload payload) {
        buffer.writeUUID(payload.jobId());
        buffer.writeEnum(payload.action());
        buffer.writeVarInt(payload.steps());
        buffer.writeBlockPos(payload.targetPos());
    }

    private static ConstructionBlueprintPlacementPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ConstructionBlueprintPlacementPayload(
                buffer.readUUID(),
                buffer.readEnum(Action.class),
                buffer.readVarInt(),
                buffer.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        DEPLOY_AT,
        MOVE_NORTH,
        MOVE_EAST,
        MOVE_SOUTH,
        MOVE_WEST,
        MOVE_UP,
        MOVE_DOWN,
        ROTATE_CLOCKWISE,
        ROTATE_COUNTERCLOCKWISE,
        TOGGLE_LOCK
    }
}
