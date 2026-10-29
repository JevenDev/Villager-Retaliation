package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementFarm;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementFarmRequestPayload(UUID session, long revision, Action action, UUID token, SettlementFarm farm)
        implements CustomPacketPayload {
    public enum Action { PREVIEW, CONFIRM, CANCEL }
    public static final Type<SettlementFarmRequestPayload> TYPE = VillagerPayloads.type("settlement_farm_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementFarmRequestPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session()); b.writeVarLong(p.revision()); b.writeEnum(p.action()); b.writeUUID(p.token());
        if (p.action() == Action.PREVIEW) { b.writeBlockPos(p.farm().first()); b.writeBlockPos(p.farm().second()); }
    }, b -> {
        var session = b.readUUID();
        long revision = b.readVarLong();
        var action = b.readEnum(Action.class);
        var token = b.readUUID();
        return new SettlementFarmRequestPayload(session, revision, action, token,
                action == Action.PREVIEW ? new SettlementFarm(b.readBlockPos(), b.readBlockPos()) : null);
    });
    public SettlementFarmRequestPayload {
        if (session == null || revision <= 0 || action == null || token == null || (action == Action.PREVIEW) != (farm != null))
            throw new IllegalArgumentException("Invalid farmland request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
