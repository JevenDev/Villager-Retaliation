package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerMountTargetCancelPayload() implements CustomPacketPayload {
    public static final Type<VillagerMountTargetCancelPayload> TYPE =
            VillagerPayloads.type("villager_mount_target_cancel");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerMountTargetCancelPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> {}, buffer -> new VillagerMountTargetCancelPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
