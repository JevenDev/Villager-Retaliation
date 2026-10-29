package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementInfrastructure;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementInfrastructureRequestPayload(UUID session, long revision, Action action, UUID token, SettlementInfrastructure infrastructure)
        implements CustomPacketPayload {
    public enum Action { PREVIEW, CONFIRM, CANCEL }
    public static final Type<SettlementInfrastructureRequestPayload> TYPE = VillagerPayloads.type("settlement_infrastructure_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementInfrastructureRequestPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session()); b.writeVarLong(p.revision()); b.writeEnum(p.action()); b.writeUUID(p.token());
        if (p.action() == Action.PREVIEW) { b.writeEnum(p.infrastructure().type()); b.writeBlockPos(p.infrastructure().first()); b.writeBlockPos(p.infrastructure().second()); }
    }, b -> {
        var session = b.readUUID();
        long revision = b.readVarLong();
        var action = b.readEnum(Action.class);
        var token = b.readUUID();
        return new SettlementInfrastructureRequestPayload(session, revision, action, token,
                action == Action.PREVIEW ? new SettlementInfrastructure(b.readEnum(SettlementInfrastructure.Type.class), b.readBlockPos(), b.readBlockPos()) : null);
    });
    public SettlementInfrastructureRequestPayload {
        if (session == null || revision <= 0 || action == null || token == null || (action == Action.PREVIEW) != (infrastructure != null))
            throw new IllegalArgumentException("Invalid construction request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
