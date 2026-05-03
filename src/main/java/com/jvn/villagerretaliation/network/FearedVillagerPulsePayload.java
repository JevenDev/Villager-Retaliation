package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FearedVillagerPulsePayload(int entityId, int ticks) implements CustomPacketPayload {
    public static final Type<FearedVillagerPulsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "feared_villager_pulse")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, FearedVillagerPulsePayload> STREAM_CODEC =
            StreamCodec.of(FearedVillagerPulsePayload::encode, FearedVillagerPulsePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, FearedVillagerPulsePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeVarInt(payload.ticks());
    }

    private static FearedVillagerPulsePayload decode(RegistryFriendlyByteBuf buffer) {
        return new FearedVillagerPulsePayload(buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
