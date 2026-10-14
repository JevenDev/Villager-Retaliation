package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerDialogueRequestPayload(int entityId, String optionId, java.util.UUID greetingRequestId, java.util.UUID selectionToken) implements CustomPacketPayload {
    public VillagerDialogueRequestPayload(int entityId, String optionId) {
        this(entityId, optionId, null, null);
    }
    public VillagerDialogueRequestPayload(int entityId, String optionId, java.util.UUID greetingRequestId) {
        this(entityId, optionId, greetingRequestId, null);
    }
    public static final Type<VillagerDialogueRequestPayload> TYPE = VillagerPayloads.type("villager_dialogue_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerDialogueRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerDialogueRequestPayload::encode, VillagerDialogueRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerDialogueRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.optionId(), 128);
        buffer.writeBoolean(payload.greetingRequestId() != null);
        if (payload.greetingRequestId() != null) buffer.writeUUID(payload.greetingRequestId());
        buffer.writeBoolean(payload.selectionToken() != null);
        if (payload.selectionToken() != null) buffer.writeUUID(payload.selectionToken());
    }

    private static VillagerDialogueRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerDialogueRequestPayload(buffer.readVarInt(), buffer.readUtf(128),
                buffer.readBoolean() ? buffer.readUUID() : null, buffer.readBoolean() ? buffer.readUUID() : null);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
