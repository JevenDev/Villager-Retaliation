package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.HiredRoute;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClipboardRouteSyncPayload(List<ClipboardRouteEntry> entries, int ticks) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 16;
    private static final int LABEL_LENGTH = 64;
    public static final Type<ClipboardRouteSyncPayload> TYPE = VillagerPayloads.type("clipboard_route_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardRouteSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardRouteSyncPayload::encode, ClipboardRouteSyncPayload::decode);

    public ClipboardRouteSyncPayload {
        entries = entries == null ? List.of() : List.copyOf(entries.stream().limit(MAX_ENTRIES).toList());
        ticks = Math.max(0, ticks);
    }

    public static ClipboardRouteSyncPayload single(ResourceLocation dimension, HiredRoute route, int ticks) {
        if (route == null || route.isEmpty()) {
            return new ClipboardRouteSyncPayload(List.of(), ticks);
        }
        return new ClipboardRouteSyncPayload(List.of(new ClipboardRouteEntry(dimension, route.nodes(), route.loop())), ticks);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardRouteSyncPayload payload) {
        buffer.writeVarInt(Math.min(MAX_ENTRIES, payload.entries().size()));
        for (ClipboardRouteEntry entry : payload.entries()) {
            encodeEntry(buffer, entry);
        }
        buffer.writeVarInt(payload.ticks());
    }

    private static ClipboardRouteSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_ENTRIES, "clipboard route entries");
        List<ClipboardRouteEntry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(decodeEntry(buffer));
        }
        return new ClipboardRouteSyncPayload(entries, buffer.readVarInt());
    }

    static void encodeEntry(RegistryFriendlyByteBuf buffer, ClipboardRouteEntry entry) {
        buffer.writeResourceLocation(entry.dimension());
        buffer.writeVarInt(Math.min(HiredRoute.MAX_NODES, entry.nodes().size()));
        for (BlockPos node : entry.nodes()) {
            buffer.writeBlockPos(node);
        }
        buffer.writeBoolean(entry.loop());
        buffer.writeUtf(entry.ownerName(), LABEL_LENGTH);
        buffer.writeUtf(entry.jobName(), LABEL_LENGTH);
    }

    static ClipboardRouteEntry decodeEntry(RegistryFriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int nodeCount = VillagerPayloads.readCollectionSize(buffer, HiredRoute.MAX_NODES, "clipboard route nodes");
        List<BlockPos> nodes = new ArrayList<>(nodeCount);
        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            nodes.add(buffer.readBlockPos());
        }
        return new ClipboardRouteEntry(
                dimension,
                nodes,
                buffer.readBoolean(),
                buffer.readUtf(LABEL_LENGTH),
                buffer.readUtf(LABEL_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
