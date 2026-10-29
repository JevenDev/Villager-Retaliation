package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementFarm;
import com.jvn.villagerretaliation.settlement.SettlementFarmTerrain;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementFarmResultPayload(UUID session, long revision, UUID token, Status status, List<Cell> cells, String message)
        implements CustomPacketPayload {
    public enum Status { PREVIEW, APPLIED, REJECTED, CANCELLED }
    public record Cell(BlockPos position, SettlementFarmTerrain.Kind kind) {}
    public static final Type<SettlementFarmResultPayload> TYPE = VillagerPayloads.type("settlement_farm_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementFarmResultPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session()); b.writeVarLong(p.revision()); b.writeUUID(p.token()); b.writeEnum(p.status());
        b.writeVarInt(p.cells().size());
        for (var cell : p.cells()) { b.writeBlockPos(cell.position()); b.writeEnum(cell.kind()); }
        b.writeUtf(p.message(), 512);
    }, b -> {
        var session = b.readUUID();
        long revision = b.readVarLong();
        var token = b.readUUID();
        var status = b.readEnum(Status.class);
        int count = VillagerPayloads.readCollectionSize(b, SettlementFarm.MAX_CELLS, "farmland preview cells");
        var cells = new ArrayList<Cell>();
        for (int i = 0; i < count; i++) cells.add(new Cell(b.readBlockPos(), b.readEnum(SettlementFarmTerrain.Kind.class)));
        return new SettlementFarmResultPayload(session, revision, token, status, cells, b.readUtf(512));
    });
    public SettlementFarmResultPayload {
        cells = List.copyOf(cells);
        if (session == null || revision <= 0 || token == null || status == null || cells.size() > SettlementFarm.MAX_CELLS
                || message == null || message.length() > 512) throw new IllegalArgumentException("Invalid farmland result");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
