package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DuelFxStatePayload(
        boolean active,
        boolean boundaryVisible,
        double centerX,
        double centerY,
        double centerZ,
        float radius,
        int boundaryGraceTicks,
        int boundaryDelayTicks,
        int result) implements CustomPacketPayload {
    public static final int RESULT_NONE = -1;
    public static final int RESULT_DRAW = 0;
    public static final int RESULT_WIN = 1;
    public static final int RESULT_LOSS = 2;
    public static final Type<DuelFxStatePayload> TYPE = VillagerPayloads.type("duel_fx_state");
    public static final StreamCodec<RegistryFriendlyByteBuf, DuelFxStatePayload> STREAM_CODEC =
            VillagerPayloads.codec(DuelFxStatePayload::encode, DuelFxStatePayload::decode);

    public static DuelFxStatePayload inactive(int result) {
        return new DuelFxStatePayload(false, false, 0.0D, 0.0D, 0.0D, 0.0F, 1, 0, result);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, DuelFxStatePayload payload) {
        buffer.writeBoolean(payload.active());
        buffer.writeBoolean(payload.boundaryVisible());
        buffer.writeDouble(payload.centerX());
        buffer.writeDouble(payload.centerY());
        buffer.writeDouble(payload.centerZ());
        buffer.writeFloat(payload.radius());
        buffer.writeVarInt(payload.boundaryGraceTicks());
        buffer.writeVarInt(payload.boundaryDelayTicks());
        buffer.writeVarInt(payload.result());
    }

    private static DuelFxStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new DuelFxStatePayload(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    public DuelFxStatePayload {
        if (!active) boundaryVisible = false;
        radius = Math.max(0.0F, radius);
        boundaryGraceTicks = Math.max(1, boundaryGraceTicks);
        boundaryDelayTicks = Math.max(0, boundaryDelayTicks);
        if (result < RESULT_NONE || result > RESULT_LOSS) result = RESULT_NONE;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
