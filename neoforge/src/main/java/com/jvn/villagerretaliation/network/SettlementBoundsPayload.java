package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

public record SettlementBoundsPayload(UUID town, ResourceLocation dimension, int requestId, boolean allowed,
                                      List<ChunkPos> plots) implements CustomPacketPayload {
    public static final Type<SettlementBoundsPayload> TYPE = VillagerPayloads.type("settlement_bounds");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementBoundsPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementBoundsPayload::write, SettlementBoundsPayload::read);

    public SettlementBoundsPayload {
        plots = List.copyOf(plots);
        if (town == null || dimension == null || requestId < 0 || plots.size() > 121
                || !allowed && !plots.isEmpty() || plots.stream().distinct().count() != plots.size()
                || !plots.isEmpty() && (plots.stream().mapToInt(plot -> plot.x).max().orElseThrow()
                        - (long) plots.stream().mapToInt(plot -> plot.x).min().orElseThrow() > 10
                || plots.stream().mapToInt(plot -> plot.z).max().orElseThrow()
                        - (long) plots.stream().mapToInt(plot -> plot.z).min().orElseThrow() > 10)) {
            throw new IllegalArgumentException("Invalid settlement bounds snapshot");
        }
    }

    private static void write(RegistryFriendlyByteBuf buffer, SettlementBoundsPayload payload) {
        buffer.writeUUID(payload.town());
        buffer.writeResourceLocation(payload.dimension());
        buffer.writeVarInt(payload.requestId());
        buffer.writeBoolean(payload.allowed());
        buffer.writeVarInt(payload.plots().size());
        payload.plots().forEach(buffer::writeChunkPos);
    }

    private static SettlementBoundsPayload read(RegistryFriendlyByteBuf buffer) {
        UUID town = buffer.readUUID();
        var dimension = buffer.readResourceLocation();
        int request = buffer.readVarInt();
        boolean allowed = buffer.readBoolean();
        int size = VillagerPayloads.readCollectionSize(buffer, 121, "settlement bounds");
        var plots = new ArrayList<ChunkPos>(size);
        for (int index = 0; index < size; index++) plots.add(buffer.readChunkPos());
        return new SettlementBoundsPayload(town, dimension, request, allowed, plots);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
