package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClipboardStorageActionPayload(int entityId, Action action) implements CustomPacketPayload {
    public static final Type<ClipboardStorageActionPayload> TYPE = VillagerPayloads.type("clipboard_storage_action");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardStorageActionPayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardStorageActionPayload::encode, ClipboardStorageActionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardStorageActionPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeEnum(payload.action());
    }

    private static ClipboardStorageActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClipboardStorageActionPayload(buffer.readVarInt(), buffer.readEnum(Action.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        ASSIGN,
        SHOW,
        REMOVE,
        CLEAR_SELECTION
    }
}
