package com.jvn.villagerretaliation.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillageOverviewRequestPayload(BlockPos anchor) implements CustomPacketPayload {
    public static final Type<VillageOverviewRequestPayload> TYPE = VillagerPayloads.type("village_overview_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageOverviewRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> buffer.writeBlockPos(payload.anchor()),
                    buffer -> new VillageOverviewRequestPayload(buffer.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
