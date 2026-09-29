package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.work.HiredFishingSpots;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HiredFishingRequestPayload(UUID worker, UUID request, long revision, HiredFishingSpots spots) implements CustomPacketPayload {
    public static final Type<HiredFishingRequestPayload> TYPE = VillagerPayloads.type("hired_fishing_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredFishingRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredFishingRequestPayload::encode, HiredFishingRequestPayload::decode);

    public HiredFishingRequestPayload {
        java.util.Objects.requireNonNull(worker);
        java.util.Objects.requireNonNull(request);
        if (revision < 0L) throw new IllegalArgumentException("Invalid fishing revision");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, HiredFishingRequestPayload payload) {
        buffer.writeUUID(payload.worker());
        buffer.writeUUID(payload.request());
        buffer.writeVarLong(payload.revision());
        buffer.writeBoolean(payload.spots() != null);
        if (payload.spots() != null) writeSpots(buffer, payload.spots());
    }

    private static HiredFishingRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredFishingRequestPayload(buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(),
                buffer.readBoolean() ? readSpots(buffer) : null);
    }

    static void writeSpots(RegistryFriendlyByteBuf buffer, HiredFishingSpots spots) {
        buffer.writeVarInt(spots.positions().size());
        spots.positions().forEach(buffer::writeBlockPos);
    }

    static HiredFishingSpots readSpots(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > HiredFishingSpots.MAX_SPOTS) throw new IllegalArgumentException("Too many fishing spots");
        var positions = new ArrayList<net.minecraft.core.BlockPos>(count);
        for (int index = 0; index < count; index++) positions.add(buffer.readBlockPos());
        return new HiredFishingSpots(positions);
    }

    @Override
    public Type<HiredFishingRequestPayload> type() { return TYPE; }
}
