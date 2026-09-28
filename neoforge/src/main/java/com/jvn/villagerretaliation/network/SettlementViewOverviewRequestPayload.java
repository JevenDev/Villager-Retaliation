package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementViewOverviewRequestPayload(UUID session, int requestId, long generation,
                                                    SettlementManagementRequestPayload.Page category, int index)
        implements CustomPacketPayload {
    public static final Type<SettlementViewOverviewRequestPayload> TYPE = VillagerPayloads.type("settlement_view_overview_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementViewOverviewRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec((b, p) -> {
                b.writeUUID(p.session());
                b.writeVarInt(p.requestId());
                b.writeVarLong(p.generation());
                b.writeEnum(p.category());
                b.writeVarInt(p.index());
            }, b -> new SettlementViewOverviewRequestPayload(b.readUUID(), b.readVarInt(), b.readVarLong(),
                    b.readEnum(SettlementManagementRequestPayload.Page.class), b.readVarInt()));

    public SettlementViewOverviewRequestPayload {
        if (session == null || requestId < 0 || generation < 0 || index < 0 || index > 1_000_000
                || !SettlementViewOverviewPayload.CATEGORIES.contains(category)
                || generation == 0 && (category != SettlementManagementRequestPayload.Page.RESIDENTS || index != 0)) {
            throw new IllegalArgumentException("Invalid overview request");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
