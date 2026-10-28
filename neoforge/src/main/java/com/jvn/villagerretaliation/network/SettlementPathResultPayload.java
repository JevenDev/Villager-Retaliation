package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementPath;
import com.jvn.villagerretaliation.settlement.SettlementPathTerrain;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementPathResultPayload(UUID session, long revision, UUID token, Status status, List<Cell> cells, String message)
        implements CustomPacketPayload {
    public enum Status { PREVIEW, APPLIED, REJECTED, CANCELLED }
    public record Cell(BlockPos position, SettlementPathTerrain.Kind kind) {}
    public static final Type<SettlementPathResultPayload> TYPE = VillagerPayloads.type("settlement_path_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementPathResultPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session()); b.writeVarLong(p.revision()); b.writeUUID(p.token()); b.writeEnum(p.status());
        b.writeVarInt(p.cells().size());
        for (var cell : p.cells()) { b.writeBlockPos(cell.position()); b.writeEnum(cell.kind()); }
        b.writeUtf(p.message(), 512);
    }, b -> {
        var session = b.readUUID();
        long revision = b.readVarLong();
        var token = b.readUUID();
        var status = b.readEnum(Status.class);
        int count = VillagerPayloads.readCollectionSize(b, SettlementPath.MAX_COLUMNS, "path preview cells");
        var cells = new ArrayList<Cell>();
        for (int i = 0; i < count; i++) cells.add(new Cell(b.readBlockPos(), b.readEnum(SettlementPathTerrain.Kind.class)));
        return new SettlementPathResultPayload(session, revision, token, status, cells, b.readUtf(512));
    });
    public SettlementPathResultPayload {
        cells = List.copyOf(cells);
        if (session == null || revision <= 0 || token == null || status == null || cells.size() > SettlementPath.MAX_COLUMNS
                || message == null || message.length() > 512) throw new IllegalArgumentException("Invalid path result");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
