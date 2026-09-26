package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

public record SettlementViewStatePayload(UUID session, UUID town, ResourceLocation dimension, int requestId,
                                         SettlementViewWindow window, boolean active) implements CustomPacketPayload {
    public static final Type<SettlementViewStatePayload> TYPE = VillagerPayloads.type("settlement_view_state");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementViewStatePayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementViewStatePayload::encode, SettlementViewStatePayload::decode);

    public SettlementViewStatePayload {
        java.util.Objects.requireNonNull(session);
        java.util.Objects.requireNonNull(town);
        java.util.Objects.requireNonNull(dimension);
        java.util.Objects.requireNonNull(window);
        if (requestId < 0) {
            throw new IllegalArgumentException("Invalid settlement view request");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SettlementViewStatePayload payload) {
        buffer.writeUUID(payload.session());
        buffer.writeUUID(payload.town());
        buffer.writeResourceLocation(payload.dimension());
        buffer.writeVarInt(payload.requestId());
        buffer.writeBoolean(payload.active());
        var window = payload.window();
        buffer.writeBlockPos(window.center());
        buffer.writeVarInt(window.radius());
        buffer.writeDouble(window.x());
        buffer.writeDouble(window.z());
        buffer.writeFloat(window.height());
        buffer.writeVarInt(window.chunks().size());
        window.chunks().forEach(buffer::writeChunkPos);
    }

    private static SettlementViewStatePayload decode(RegistryFriendlyByteBuf buffer) {
        UUID session = buffer.readUUID();
        UUID town = buffer.readUUID();
        ResourceLocation dimension = buffer.readResourceLocation();
        int requestId = buffer.readVarInt();
        boolean active = buffer.readBoolean();
        var center = buffer.readBlockPos();
        int radius = buffer.readVarInt();
        double x = buffer.readDouble();
        double z = buffer.readDouble();
        float height = buffer.readFloat();
        int count = VillagerPayloads.readCollectionSize(buffer, SettlementViewWindow.MAX_CHUNKS, "settlement view chunks");
        var chunks = new ArrayList<ChunkPos>(count);
        for (int index = 0; index < count; index++) {
            chunks.add(buffer.readChunkPos());
        }
        return new SettlementViewStatePayload(session, town, dimension, requestId,
                new SettlementViewWindow(center, radius, x, z, height, chunks), active);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
