package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClipboardWorkAreaDraftPayload(Action action, int steps) implements CustomPacketPayload {
    public static final Type<ClipboardWorkAreaDraftPayload> TYPE = VillagerPayloads.type("clipboard_work_area_draft");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardWorkAreaDraftPayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardWorkAreaDraftPayload::encode, ClipboardWorkAreaDraftPayload::decode);

    public ClipboardWorkAreaDraftPayload {
        steps = Math.max(1, Math.min(8, steps));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardWorkAreaDraftPayload payload) {
        buffer.writeEnum(payload.action());
        buffer.writeVarInt(payload.steps());
    }

    private static ClipboardWorkAreaDraftPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClipboardWorkAreaDraftPayload(buffer.readEnum(Action.class), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        MOVE_NORTH,
        MOVE_EAST,
        MOVE_SOUTH,
        MOVE_WEST,
        MOVE_UP,
        MOVE_DOWN,
        EXPAND_HORIZONTAL,
        CONTRACT_HORIZONTAL,
        EXPAND_VERTICAL,
        CONTRACT_VERTICAL
    }
}
