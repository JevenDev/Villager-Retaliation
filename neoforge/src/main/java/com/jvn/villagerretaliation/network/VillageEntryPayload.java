package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.allegiance.VillageOpinion;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VillageEntryPayload(ResourceLocation dimension, String name, VillageOpinion opinion)
        implements CustomPacketPayload {
    public static final Type<VillageEntryPayload> TYPE = VillagerPayloads.type("village_entry");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageEntryPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> {
                buffer.writeResourceLocation(payload.dimension());
                buffer.writeUtf(payload.name(), 128);
                buffer.writeDouble(payload.opinion().score());
                buffer.writeEnum(payload.opinion().level());
            }, buffer -> new VillageEntryPayload(buffer.readResourceLocation(), buffer.readUtf(128),
                    new VillageOpinion(buffer.readDouble(), buffer.readEnum(VillagerReputationLevel.class))));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
