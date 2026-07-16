package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerMountTargetModePayload(
        boolean active,
        int villagerEntityId,
        int remainingTicks) implements CustomPacketPayload {
    public static final Type<VillagerMountTargetModePayload> TYPE =
            VillagerPayloads.type("villager_mount_target_mode");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerMountTargetModePayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerMountTargetModePayload::encode, VillagerMountTargetModePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerMountTargetModePayload payload) {
        buffer.writeBoolean(payload.active());
        buffer.writeVarInt(payload.villagerEntityId());
        buffer.writeVarInt(Math.max(0, payload.remainingTicks()));
    }

    private static VillagerMountTargetModePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerMountTargetModePayload(
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
