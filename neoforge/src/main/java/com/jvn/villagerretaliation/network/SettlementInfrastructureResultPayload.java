package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementInfrastructure;
import com.jvn.villagerretaliation.settlement.SettlementInfrastructureTerrain;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementInfrastructureResultPayload(UUID session, long revision, UUID token, Status status, List<Cell> cells, String message)
        implements CustomPacketPayload {
    public enum Status { PREVIEW, APPLIED, REJECTED, CANCELLED }
    public record Cell(BlockPos position, net.minecraft.world.level.block.state.BlockState state, SettlementInfrastructureTerrain.Kind kind) {}
    public static final Type<SettlementInfrastructureResultPayload> TYPE = VillagerPayloads.type("settlement_infrastructure_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementInfrastructureResultPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session()); b.writeVarLong(p.revision()); b.writeUUID(p.token()); b.writeEnum(p.status());
        b.writeVarInt(p.cells().size());
        for (var cell : p.cells()) { b.writeBlockPos(cell.position()); b.writeVarInt(net.minecraft.world.level.block.Block.getId(cell.state())); b.writeEnum(cell.kind()); }
        b.writeUtf(p.message(), 512);
    }, b -> {
        var session = b.readUUID();
        long revision = b.readVarLong();
        var token = b.readUUID();
        var status = b.readEnum(Status.class);
        int count = VillagerPayloads.readCollectionSize(b, SettlementInfrastructure.MAX_CELLS, "construction preview cells");
        var cells = new ArrayList<Cell>();
        for (int i = 0; i < count; i++) cells.add(new Cell(b.readBlockPos(), net.minecraft.world.level.block.Block.stateById(b.readVarInt()), b.readEnum(SettlementInfrastructureTerrain.Kind.class)));
        return new SettlementInfrastructureResultPayload(session, revision, token, status, cells, b.readUtf(512));
    });
    public SettlementInfrastructureResultPayload {
        cells = List.copyOf(cells);
        if (session == null || revision <= 0 || token == null || status == null || cells.size() > SettlementInfrastructure.MAX_CELLS
                || message == null || message.length() > 512) throw new IllegalArgumentException("Invalid construction result");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
