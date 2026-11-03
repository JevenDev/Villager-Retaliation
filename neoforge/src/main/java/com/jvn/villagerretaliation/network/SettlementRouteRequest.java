package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementPath;
import com.jvn.villagerretaliation.settlement.SettlementRouteShape;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementRouteRequest(int sequence, Action action, UUID origin, UUID destination, UUID route, UUID journey,
                                     long revision, String name, List<BlockPos> points, Map<UUID, Boolean> members, int section, SettlementRouteShape shape) implements CustomPacketPayload {
    public enum Action { VIEW, PREVIEW, SAVE, REMOVE, BUILD, CONFIGURE, RECALL, CLOSE }
    public static final Type<SettlementRouteRequest> TYPE = VillagerPayloads.type("settlement_route_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementRouteRequest> STREAM_CODEC = VillagerPayloads.codec(SettlementRouteRequest::encode, SettlementRouteRequest::decode);
    public SettlementRouteRequest(int sequence, Action action, UUID origin, UUID destination, UUID route, UUID journey,
                                  long revision, String name, List<BlockPos> points, Map<UUID, Boolean> members, int section) {
        this(sequence, action, origin, destination, route, journey, revision, name, points, members, section, null);
    }
    public SettlementRouteRequest {
        points = List.copyOf(points); members = Map.copyOf(members);
        if (shape != null && !shape.points().equals(points)) throw new IllegalArgumentException("Route nodes do not match waypoints");
        if (sequence < 0 || action == null || revision < 0 || name == null || name.length() > 48 || points.size() > SettlementRouteShape.MAX_POINTS || members.size() > 7
                || section < 0 || section >= 512 || (action == Action.PREVIEW || action == Action.SAVE) && (origin == null || destination == null || route == null || name.isBlank())
                || (action == Action.CONFIGURE || action == Action.RECALL) && journey == null) throw new IllegalArgumentException("Invalid route request");
    }
    private static void encode(RegistryFriendlyByteBuf b, SettlementRouteRequest p) {
        b.writeVarInt(p.sequence); b.writeEnum(p.action); VillageRelationsRequestPayload.writeId(b, p.origin); VillageRelationsRequestPayload.writeId(b, p.destination);
        VillageRelationsRequestPayload.writeId(b, p.route); VillageRelationsRequestPayload.writeId(b, p.journey); b.writeVarLong(p.revision); b.writeUtf(p.name, 48);
        b.writeVarInt(p.points.size()); p.points.forEach(b::writeBlockPos); b.writeVarInt(p.members.size());
        p.members.forEach((id, guard) -> { b.writeUUID(id); b.writeBoolean(guard); }); b.writeVarInt(p.section);
        b.writeBoolean(p.shape != null);
        if (p.shape != null) {
            b.writeBoolean(p.shape.smooth()); b.writeVarInt(p.shape.nodes().size());
            for (var node : p.shape.nodes()) { vector(b, node.position()); vector(b, node.incoming()); vector(b, node.outgoing()); }
        }
    }
    private static SettlementRouteRequest decode(RegistryFriendlyByteBuf b) {
        int sequence = b.readVarInt(); var action = b.readEnum(Action.class);
        UUID origin = VillageRelationsRequestPayload.readId(b), destination = VillageRelationsRequestPayload.readId(b), route = VillageRelationsRequestPayload.readId(b), journey = VillageRelationsRequestPayload.readId(b);
        long revision = b.readVarLong(); String name = b.readUtf(48);
        var points = new java.util.ArrayList<BlockPos>(); for (int n = count(b, SettlementRouteShape.MAX_POINTS); n > 0; n--) points.add(b.readBlockPos());
        var members = new java.util.LinkedHashMap<UUID, Boolean>();
        for (int n = count(b, 7); n > 0; n--) if (members.put(b.readUUID(), b.readBoolean()) != null) throw new IllegalArgumentException("Duplicate traveler");
        int section = b.readVarInt(); SettlementRouteShape shape = null;
        if (b.readBoolean()) {
            boolean smooth = b.readBoolean(); var nodes = new java.util.ArrayList<SettlementPath.Node>();
            for (int n = count(b, SettlementRouteShape.MAX_POINTS + 2); n > 0; n--) nodes.add(new SettlementPath.Node(vector(b), vector(b), vector(b)));
            shape = new SettlementRouteShape(nodes, smooth);
        }
        return new SettlementRouteRequest(sequence, action, origin, destination, route, journey, revision, name, points, members, section, shape);
    }
    private static void vector(RegistryFriendlyByteBuf b, Vec3 point) { b.writeDouble(point.x); b.writeDouble(point.y); b.writeDouble(point.z); }
    private static Vec3 vector(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    private static int count(RegistryFriendlyByteBuf b, int limit) { int n = b.readVarInt(); if (n < 0 || n > limit) throw new IllegalArgumentException("Route request exceeds limit"); return n; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
