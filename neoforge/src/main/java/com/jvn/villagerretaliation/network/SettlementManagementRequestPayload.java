package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementManagementRequestPayload(UUID town, Page page, int index, String query, int requestId, boolean openScreen)
        implements CustomPacketPayload {
    public static final int PAGE_SIZE = 24;
    public static final Type<SettlementManagementRequestPayload> TYPE = VillagerPayloads.type("settlement_management_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementManagementRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementManagementRequestPayload::encode, SettlementManagementRequestPayload::decode);

    public SettlementManagementRequestPayload {
        if (page == null || index < 0 || index > 1_000_000 || query == null || query.length() > 64 || requestId < 0
                || town == null && page != Page.TOWNS) {
            throw new IllegalArgumentException("Invalid settlement management request");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SettlementManagementRequestPayload payload) {
        buffer.writeBoolean(payload.town() != null);
        if (payload.town() != null) {
            buffer.writeUUID(payload.town());
        }
        buffer.writeEnum(payload.page());
        buffer.writeVarInt(payload.index());
        buffer.writeUtf(payload.query(), 64);
        buffer.writeVarInt(payload.requestId());
        buffer.writeBoolean(payload.openScreen());
    }

    private static SettlementManagementRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        UUID town = buffer.readBoolean() ? buffer.readUUID() : null;
        return new SettlementManagementRequestPayload(town, buffer.readEnum(Page.class), buffer.readVarInt(),
                buffer.readUtf(64), buffer.readVarInt(), buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Page {
        TOWNS,
        SUMMARY,
        RESIDENTS,
        AREAS,
        DEPOTS,
        APPLICANTS,
        TRADE,
        DELEGATES,
        PROJECTS,
        BEDS,
        HISTORY,
        ISSUES
    }
}
