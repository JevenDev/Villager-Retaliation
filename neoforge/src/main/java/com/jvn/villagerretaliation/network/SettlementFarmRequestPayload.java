package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementFarm;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementFarmRequestPayload(UUID session, long revision, Action action, UUID token, SettlementFarm farm,
                                           UUID area, SettlementFarm original, com.jvn.villagerretaliation.settlement.SettlementCropPlan crops)
        implements CustomPacketPayload {
    public SettlementFarmRequestPayload(UUID session, long revision, Action action, UUID token, SettlementFarm farm) {
        this(session, revision, action, token, farm, null, null, com.jvn.villagerretaliation.settlement.SettlementCropPlan.NONE);
    }

    public enum Action { PREVIEW, CONFIRM, CANCEL }
    public static final Type<SettlementFarmRequestPayload> TYPE = VillagerPayloads.type("settlement_farm_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementFarmRequestPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session()); b.writeVarLong(p.revision()); b.writeEnum(p.action()); b.writeUUID(p.token());
        if (p.action() == Action.PREVIEW) {
            b.writeEnum(p.farm().shape());
            b.writeVarInt(p.farm().nodes().size());
            for (var node : p.farm().nodes()) b.writeBlockPos(node);
            b.writeBoolean(p.area() != null);
            if (p.area() != null) b.writeUUID(p.area());
            SettlementFarm.writeOptional(b, p.original());
            p.crops().write(b);
        }
    }, b -> {
        var session = b.readUUID();
        long revision = b.readVarLong();
        var action = b.readEnum(Action.class);
        var token = b.readUUID();
        SettlementFarm farm = null;
        UUID area = null;
        SettlementFarm original = null;
        var crops = com.jvn.villagerretaliation.settlement.SettlementCropPlan.NONE;
        if (action == Action.PREVIEW) {
            var shape = b.readEnum(SettlementFarm.Shape.class);
            int count = VillagerPayloads.readCollectionSize(b, SettlementFarm.MAX_NODES, "farmland nodes");
            var nodes = new java.util.ArrayList<net.minecraft.core.BlockPos>();
            for (int i = 0; i < count; i++) nodes.add(b.readBlockPos());
            farm = new SettlementFarm(shape, nodes);
            if (b.readBoolean()) area = b.readUUID();
            original = SettlementFarm.readOptional(b);
            crops = com.jvn.villagerretaliation.settlement.SettlementCropPlan.read(b);
        }
        return new SettlementFarmRequestPayload(session, revision, action, token, farm, area, original, crops);
    });
    public SettlementFarmRequestPayload {
        if (session == null || revision <= 0 || action == null || token == null || crops == null || area == null && original != null || (action == Action.PREVIEW) != (farm != null))
            throw new IllegalArgumentException("Invalid farmland request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
