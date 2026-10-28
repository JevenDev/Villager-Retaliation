package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementWall;
import com.jvn.villagerretaliation.settlement.SettlementWallTerrain;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record SettlementWallResultPayload(UUID session, long revision, UUID token, Status status, List<Cell> cells, String message)
        implements CustomPacketPayload {
    public enum Status { PREVIEW, REJECTED, APPLIED, CANCELLED }
    public record Cell(BlockPos position, BlockState state, SettlementWallTerrain.Kind kind) {}
    public static final Type<SettlementWallResultPayload> TYPE = VillagerPayloads.type("settlement_wall_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementWallResultPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session()); b.writeVarLong(p.revision()); b.writeUUID(p.token()); b.writeEnum(p.status());
        b.writeVarInt(p.cells().size());
        for (var cell : p.cells()) {
            b.writeBlockPos(cell.position()); b.writeVarInt(Block.getId(cell.state())); b.writeEnum(cell.kind());
        }
        b.writeUtf(p.message(), 512);
    }, b -> {
        var session = b.readUUID();
        long revision = b.readVarLong();
        var token = b.readUUID();
        var status = b.readEnum(Status.class);
        int count = VillagerPayloads.readCollectionSize(b, SettlementWall.MAX_BLOCKS, "wall preview blocks");
        var cells = new ArrayList<Cell>();
        for (int i = 0; i < count; i++) cells.add(new Cell(b.readBlockPos(), Block.stateById(b.readVarInt()), b.readEnum(SettlementWallTerrain.Kind.class)));
        return new SettlementWallResultPayload(session, revision, token, status, cells, b.readUtf(512));
    });
    public SettlementWallResultPayload {
        cells = List.copyOf(cells);
        if (session == null || revision <= 0 || token == null || status == null || cells.size() > SettlementWall.MAX_BLOCKS
                || message == null || message.length() > 512) throw new IllegalArgumentException("Invalid wall result");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
