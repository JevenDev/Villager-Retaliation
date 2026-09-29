package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.work.HiredFishingSpots;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HiredFishingSnapshotPayload(ResourceLocation dimension, UUID worker, UUID request, long revision,
        HiredFishingSpots spots, boolean editable, boolean applied, String feedback) implements CustomPacketPayload {
    public static final Type<HiredFishingSnapshotPayload> TYPE = VillagerPayloads.type("hired_fishing_snapshot");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredFishingSnapshotPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredFishingSnapshotPayload::encode, HiredFishingSnapshotPayload::decode);

    public HiredFishingSnapshotPayload {
        java.util.Objects.requireNonNull(dimension);
        java.util.Objects.requireNonNull(worker);
        java.util.Objects.requireNonNull(request);
        java.util.Objects.requireNonNull(spots);
        if (revision < 0L || feedback == null || feedback.length() > 256) throw new IllegalArgumentException("Invalid fishing snapshot");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, HiredFishingSnapshotPayload payload) {
        buffer.writeResourceLocation(payload.dimension());
        buffer.writeUUID(payload.worker());
        buffer.writeUUID(payload.request());
        buffer.writeVarLong(payload.revision());
        HiredFishingRequestPayload.writeSpots(buffer, payload.spots());
        buffer.writeBoolean(payload.editable());
        buffer.writeBoolean(payload.applied());
        buffer.writeUtf(payload.feedback(), 256);
    }

    private static HiredFishingSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredFishingSnapshotPayload(buffer.readResourceLocation(), buffer.readUUID(), buffer.readUUID(),
                buffer.readVarLong(), HiredFishingRequestPayload.readSpots(buffer), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readUtf(256));
    }

    @Override
    public Type<HiredFishingSnapshotPayload> type() { return TYPE; }
}
