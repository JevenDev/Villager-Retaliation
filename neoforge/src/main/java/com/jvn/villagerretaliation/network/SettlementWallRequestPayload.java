package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementWall;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementWallRequestPayload(UUID session, long revision, Action action, UUID token, SettlementWall wall)
        implements CustomPacketPayload {
    public enum Action { PREVIEW, CONFIRM, CANCEL }
    public static final Type<SettlementWallRequestPayload> TYPE = VillagerPayloads.type("settlement_wall_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementWallRequestPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session());
        b.writeVarLong(p.revision());
        b.writeEnum(p.action());
        b.writeUUID(p.token());
        if (p.action() == Action.PREVIEW) {
            b.writeEnum(p.wall().type());
            b.writeBoolean(p.wall().closed());
            b.writeVarInt(p.wall().towerSpacing());
            b.writeVarInt(p.wall().nodes().size());
            p.wall().nodes().forEach(b::writeBlockPos);
        }
    }, b -> {
        var session = b.readUUID();
        long revision = b.readVarLong();
        var action = b.readEnum(Action.class);
        var token = b.readUUID();
        SettlementWall wall = null;
        if (action == Action.PREVIEW) {
            var type = b.readEnum(SettlementWall.Type.class);
            boolean closed = b.readBoolean();
            int spacing = b.readVarInt();
            int count = VillagerPayloads.readCollectionSize(b, SettlementWall.MAX_NODES, "wall nodes");
            var nodes = new ArrayList<BlockPos>();
            for (int i = 0; i < count; i++) nodes.add(b.readBlockPos());
            wall = new SettlementWall(type, nodes, closed, spacing);
        }
        return new SettlementWallRequestPayload(session, revision, action, token, wall);
    });
    public SettlementWallRequestPayload {
        if (session == null || revision <= 0 || action == null || token == null
                || (action == Action.PREVIEW) != (wall != null)) throw new IllegalArgumentException("Invalid wall request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
