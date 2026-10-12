package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerHealingRequestPayload(int entityId, Action action, UUID quoteId) implements CustomPacketPayload {
    public static final UUID NO_QUOTE = new UUID(0L, 0L);
    public enum Action { OPEN, CONFIRM, CLOSE }
    public static final Type<VillagerHealingRequestPayload> TYPE = VillagerPayloads.type("villager_healing_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerHealingRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerHealingRequestPayload::encode, VillagerHealingRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerHealingRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeEnum(payload.action());
        buffer.writeUUID(payload.quoteId());
    }

    private static VillagerHealingRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerHealingRequestPayload(buffer.readVarInt(), buffer.readEnum(Action.class), buffer.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
