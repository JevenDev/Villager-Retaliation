package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementDistrictResultPayload(UUID town, UUID request, String fingerprint, List<String> lines, String failure, boolean queued) implements CustomPacketPayload {
    public static final Type<SettlementDistrictResultPayload> TYPE = VillagerPayloads.type("settlement_district_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementDistrictResultPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.town()); b.writeUUID(p.request()); b.writeUtf(p.fingerprint(), 64); b.writeVarInt(p.lines().size());
        p.lines().forEach(line -> b.writeUtf(line, 256)); b.writeUtf(p.failure(), 256); b.writeBoolean(p.queued());
    }, b -> {
        var town = b.readUUID(); var request = b.readUUID(); var fingerprint = b.readUtf(64); var lines = new ArrayList<String>();
        int count = VillagerPayloads.readCollectionSize(b, 320, "district quote");
        for (int i = 0; i < count; i++) lines.add(b.readUtf(256));
        return new SettlementDistrictResultPayload(town, request, fingerprint, lines, b.readUtf(256), b.readBoolean());
    });
    public SettlementDistrictResultPayload {
        if (town == null || request == null || fingerprint == null || fingerprint.length() > 64 || lines.size() > 320
                || lines.stream().anyMatch(line -> line == null || line.length() > 256) || failure == null || failure.length() > 256)
            throw new IllegalArgumentException("Invalid district quote");
        lines = List.copyOf(lines);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
