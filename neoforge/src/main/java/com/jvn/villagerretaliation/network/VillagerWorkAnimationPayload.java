package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record VillagerWorkAnimationPayload(
        int entityId,
        int animation,
        int durationTicks,
        ItemStack item
) implements CustomPacketPayload {
    public static final Type<VillagerWorkAnimationPayload> TYPE = VillagerPayloads.type("villager_work_animation");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerWorkAnimationPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerWorkAnimationPayload::encode, VillagerWorkAnimationPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerWorkAnimationPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeVarInt(payload.animation());
        buffer.writeVarInt(payload.durationTicks());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.item());
    }

    private static VillagerWorkAnimationPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerWorkAnimationPayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
