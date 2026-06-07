package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClipboardWorkAreaActionPayload(UUID villagerId, Action action, int steps) implements CustomPacketPayload {
    public static final Type<ClipboardWorkAreaActionPayload> TYPE = VillagerPayloads.type("clipboard_work_area_action");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardWorkAreaActionPayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardWorkAreaActionPayload::encode, ClipboardWorkAreaActionPayload::decode);

    public ClipboardWorkAreaActionPayload {
        steps = Math.max(1, Math.min(5, steps));
    }

    public ClipboardWorkAreaActionPayload(UUID villagerId, Action action) {
        this(villagerId, action, 1);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardWorkAreaActionPayload payload) {
        buffer.writeUUID(payload.villagerId());
        buffer.writeEnum(payload.action());
        buffer.writeVarInt(payload.steps());
    }

    private static ClipboardWorkAreaActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClipboardWorkAreaActionPayload(buffer.readUUID(), buffer.readEnum(Action.class), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        SET_CENTER_HERE,
        RESET_CENTER_TO_VILLAGER,
        PREVIEW,
        CONFIGURE_ROLE,
        INCREASE_HORIZONTAL_RANGE,
        DECREASE_HORIZONTAL_RANGE,
        INCREASE_VERTICAL_RANGE,
        DECREASE_VERTICAL_RANGE,
        EXPAND_NORTH,
        EXPAND_EAST,
        EXPAND_SOUTH,
        EXPAND_WEST,
        CONTRACT_NORTH,
        CONTRACT_EAST,
        CONTRACT_SOUTH,
        CONTRACT_WEST,
        EXPAND_UP,
        EXPAND_DOWN,
        CONTRACT_UP,
        CONTRACT_DOWN
    }
}
