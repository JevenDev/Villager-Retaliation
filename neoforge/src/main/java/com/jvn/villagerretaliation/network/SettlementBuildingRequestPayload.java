package com.jvn.villagerretaliation.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementBuildingRequestPayload(UUID town, UUID building, UUID request, Action action,
                                               boolean confirm, String fingerprint) implements CustomPacketPayload {
    public enum Action { INSPECT, REPAIR, RENOVATE, DISMANTLE }
    public static final Type<SettlementBuildingRequestPayload> TYPE = VillagerPayloads.type("settlement_building");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementBuildingRequestPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.town()); b.writeUUID(p.building()); b.writeUUID(p.request()); b.writeEnum(p.action());
        b.writeBoolean(p.confirm()); b.writeUtf(p.fingerprint(), 64);
    }, b -> new SettlementBuildingRequestPayload(b.readUUID(), b.readUUID(), b.readUUID(), b.readEnum(Action.class), b.readBoolean(), b.readUtf(64)));
    public SettlementBuildingRequestPayload {
        Objects.requireNonNull(town); Objects.requireNonNull(building); Objects.requireNonNull(request); Objects.requireNonNull(action);
        if (fingerprint == null || fingerprint.length() > 64 || confirm && action == Action.INSPECT)
            throw new IllegalArgumentException("Invalid building request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
