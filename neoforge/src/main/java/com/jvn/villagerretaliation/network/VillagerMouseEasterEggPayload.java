package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerMouseEasterEggPayload(int entityId, Kind kind) implements CustomPacketPayload {
    public static final Type<VillagerMouseEasterEggPayload> TYPE = VillagerPayloads.type("villager_mouse_easter_egg");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerMouseEasterEggPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerMouseEasterEggPayload::encode, VillagerMouseEasterEggPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerMouseEasterEggPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeEnum(payload.kind());
    }

    private static VillagerMouseEasterEggPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerMouseEasterEggPayload(buffer.readVarInt(), buffer.readEnum(Kind.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Kind {
        STARE
    }
}
