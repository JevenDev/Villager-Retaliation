package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.HiredRoute;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClipboardRouteSyncPayload(List<ClipboardRouteEntry> entries, int ticks, boolean draft) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 16;
    private static final int LABEL_LENGTH = 64;
    public static final Type<ClipboardRouteSyncPayload> TYPE = VillagerPayloads.type("clipboard_route_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardRouteSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardRouteSyncPayload::encode, ClipboardRouteSyncPayload::decode);

    public ClipboardRouteSyncPayload {
        entries = entries == null ? List.of() : List.copyOf(entries.stream().limit(MAX_ENTRIES).toList());
        ticks = Math.max(0, ticks);
    }

    public ClipboardRouteSyncPayload(List<ClipboardRouteEntry> entries, int ticks) {
        this(entries, ticks, false);
    }

    public static ClipboardRouteSyncPayload single(ResourceLocation dimension, HiredRoute route, int ticks) {
        if (route == null || route.isEmpty()) {
            return new ClipboardRouteSyncPayload(List.of(), ticks, false);
        }
        return new ClipboardRouteSyncPayload(
                List.of(new ClipboardRouteEntry(dimension, route.nodes(), route.loop(), route.branches(), "", "")), ticks, false);
    }

    public static ClipboardRouteSyncPayload draft(ResourceLocation dimension, HiredRoute route) {
        if (route == null || route.isEmpty()) {
            return new ClipboardRouteSyncPayload(List.of(), 0, true);
        }
        return new ClipboardRouteSyncPayload(
                List.of(new ClipboardRouteEntry(dimension, route.nodes(), route.loop(), route.branches(), "", "")), 0, true);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardRouteSyncPayload payload) {
        buffer.writeVarInt(Math.min(MAX_ENTRIES, payload.entries().size()));
        for (ClipboardRouteEntry entry : payload.entries()) {
            encodeEntry(buffer, entry);
        }
        buffer.writeVarInt(payload.ticks());
        buffer.writeBoolean(payload.draft());
    }

    private static ClipboardRouteSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_ENTRIES, "clipboard route entries");
        List<ClipboardRouteEntry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(decodeEntry(buffer));
        }
        return new ClipboardRouteSyncPayload(entries, buffer.readVarInt(), buffer.readBoolean());
    }

    static void encodeEntry(RegistryFriendlyByteBuf buffer, ClipboardRouteEntry entry) {
        buffer.writeResourceLocation(entry.dimension());
        buffer.writeVarInt(Math.min(HiredRoute.MAX_NODES, entry.nodes().size()));
        for (BlockPos node : entry.nodes()) {
            buffer.writeBlockPos(node);
        }
        buffer.writeBoolean(entry.loop());
        buffer.writeVarInt(Math.min(HiredRoute.MAX_BRANCHES, entry.branches().size()));
        for (HiredRoute.Branch branch : entry.branches()) {
            buffer.writeBlockPos(branch.anchor());
            buffer.writeBlockPos(branch.end());
        }
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
        boolean loop = buffer.readBoolean();
        int branchCount = VillagerPayloads.readCollectionSize(buffer, HiredRoute.MAX_BRANCHES, "clipboard route branches");
        List<HiredRoute.Branch> branches = new ArrayList<>(branchCount);
        for (int branchIndex = 0; branchIndex < branchCount; branchIndex++) {
            branches.add(new HiredRoute.Branch(buffer.readBlockPos(), buffer.readBlockPos()));
        }
        return new ClipboardRouteEntry(
                dimension,
                nodes,
                loop,
                branches,
                buffer.readUtf(LABEL_LENGTH),
                buffer.readUtf(LABEL_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
