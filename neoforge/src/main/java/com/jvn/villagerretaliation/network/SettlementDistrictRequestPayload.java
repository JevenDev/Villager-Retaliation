package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementPath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementDistrictRequestPayload(UUID town, UUID request, boolean confirm, String fingerprint,
        List<SettlementManagementActionPayload.CommissionBuilding> buildings, List<PlannedPath> paths, boolean pathsFirst) implements CustomPacketPayload {
    public SettlementDistrictRequestPayload(UUID town, UUID request, boolean confirm, String fingerprint,
            List<SettlementManagementActionPayload.CommissionBuilding> buildings, List<PlannedPath> paths) {
        this(town, request, confirm, fingerprint, buildings, paths, true);
    }
    public static final int MAX_PROJECTS = 16;
    public record PlannedPath(UUID id, SettlementPath path) { public PlannedPath { Objects.requireNonNull(id); Objects.requireNonNull(path); } }
    public static final Type<SettlementDistrictRequestPayload> TYPE = VillagerPayloads.type("settlement_district");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementDistrictRequestPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.town()); b.writeUUID(p.request()); b.writeBoolean(p.confirm()); b.writeUtf(p.fingerprint(), 64); b.writeBoolean(p.pathsFirst());
        b.writeVarInt(p.buildings().size());
        for (var building : p.buildings()) SettlementManagementActionPayload.STREAM_CODEC.encode(b, new SettlementManagementActionPayload(p.town(), building, building.request()));
        b.writeVarInt(p.paths().size());
        for (var path : p.paths()) SettlementPathRequestPayload.STREAM_CODEC.encode(b, new SettlementPathRequestPayload(p.request(), 1, SettlementPathRequestPayload.Action.PREVIEW, path.id(), path.path()));
    }, b -> {
        var town = b.readUUID(); var request = b.readUUID(); boolean confirm = b.readBoolean(); var fingerprint = b.readUtf(64); boolean pathsFirst = b.readBoolean();
        var buildings = new ArrayList<SettlementManagementActionPayload.CommissionBuilding>();
        int count = VillagerPayloads.readCollectionSize(b, MAX_PROJECTS, "district buildings");
        for (int i = 0; i < count; i++) {
            var action = SettlementManagementActionPayload.STREAM_CODEC.decode(b);
            if (!action.town().equals(town) || !(action.command() instanceof SettlementManagementActionPayload.CommissionBuilding building))
                throw new IllegalArgumentException("Invalid district building");
            buildings.add(building);
        }
        var paths = new ArrayList<PlannedPath>(); count = VillagerPayloads.readCollectionSize(b, MAX_PROJECTS, "district paths");
        for (int i = 0; i < count; i++) {
            var path = SettlementPathRequestPayload.STREAM_CODEC.decode(b);
            if (!path.session().equals(request) || path.action() != SettlementPathRequestPayload.Action.PREVIEW || path.path() == null)
                throw new IllegalArgumentException("Invalid district path");
            paths.add(new PlannedPath(path.token(), path.path()));
        }
        return new SettlementDistrictRequestPayload(town, request, confirm, fingerprint, buildings, paths, pathsFirst);
    });
    public SettlementDistrictRequestPayload {
        Objects.requireNonNull(town); Objects.requireNonNull(request);
        buildings = List.copyOf(buildings); paths = List.copyOf(paths);
        var ids = new HashSet<UUID>(); buildings.forEach(building -> ids.add(building.request())); paths.forEach(path -> ids.add(path.id()));
        if (fingerprint == null || fingerprint.length() > 64 || buildings.size() + paths.size() < 1 || buildings.size() + paths.size() > MAX_PROJECTS
                || ids.size() != buildings.size() + paths.size()) throw new IllegalArgumentException("Invalid district plan");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
