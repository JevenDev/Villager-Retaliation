package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementBuildingInfoPayload(UUID town, UUID building, UUID request, String name, List<String> lines,
                                            String fingerprint, String feedback, boolean queued) implements CustomPacketPayload {
    public static final Type<SettlementBuildingInfoPayload> TYPE = VillagerPayloads.type("settlement_building_info");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementBuildingInfoPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.town()); b.writeUUID(p.building()); b.writeUUID(p.request()); b.writeUtf(p.name(), 128);
        b.writeVarInt(p.lines().size()); p.lines().forEach(line -> b.writeUtf(line, 256));
        b.writeUtf(p.fingerprint(), 64); b.writeUtf(p.feedback(), 256); b.writeBoolean(p.queued());
    }, b -> {
        var town = b.readUUID(); var building = b.readUUID(); var request = b.readUUID(); var name = b.readUtf(128);
        var lines = new ArrayList<String>(); int count = VillagerPayloads.readCollectionSize(b, 128, "building details");
        for (int i = 0; i < count; i++) lines.add(b.readUtf(256));
        return new SettlementBuildingInfoPayload(town, building, request, name, lines, b.readUtf(64), b.readUtf(256), b.readBoolean());
    });
    public SettlementBuildingInfoPayload {
        if (town == null || building == null || request == null || name == null || name.length() > 128 || lines.size() > 128
                || lines.stream().anyMatch(line -> line == null || line.length() > 256) || fingerprint == null || fingerprint.length() > 64
                || feedback == null || feedback.length() > 256) throw new IllegalArgumentException("Invalid building details");
        lines = List.copyOf(lines);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
