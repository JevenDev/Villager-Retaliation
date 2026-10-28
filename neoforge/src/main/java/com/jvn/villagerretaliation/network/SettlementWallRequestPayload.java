package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementWall;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementWallRequestPayload(UUID session, long revision, Action action, UUID token, SettlementWall wall, UUID upgrade, BlockPos pick)
        implements CustomPacketPayload {
    public enum Action { PREVIEW, CONFIRM, CANCEL, SELECT_UPGRADE, SELECT_DESTROY, PREVIEW_DESTROY }
    public static final UUID NONE = new UUID(0, 0);
    public SettlementWallRequestPayload(UUID session, long revision, Action action, UUID token, SettlementWall wall) {
        this(session, revision, action, token, wall, NONE, BlockPos.ZERO);
    }
    public static final Type<SettlementWallRequestPayload> TYPE = VillagerPayloads.type("settlement_wall_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementWallRequestPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session());
        b.writeVarLong(p.revision());
        b.writeEnum(p.action());
        b.writeUUID(p.token());
        b.writeUUID(p.upgrade());
        if (p.action() == Action.SELECT_UPGRADE || p.action() == Action.SELECT_DESTROY) b.writeBlockPos(p.pick());
        if (p.action() == Action.PREVIEW) writeWall(b, p.wall());
    }, b -> {
        var session = b.readUUID();
        long revision = b.readVarLong();
        var action = b.readEnum(Action.class);
        var token = b.readUUID();
        var upgrade = b.readUUID();
        var pick = action == Action.SELECT_UPGRADE || action == Action.SELECT_DESTROY ? b.readBlockPos() : BlockPos.ZERO;
        SettlementWall wall = action == Action.PREVIEW ? readWall(b) : null;
        return new SettlementWallRequestPayload(session, revision, action, token, wall, upgrade, pick);
    });
    static void writeWall(RegistryFriendlyByteBuf b, SettlementWall wall) {
        b.writeEnum(wall.type()); b.writeEnum(wall.part()); b.writeBoolean(wall.closed()); b.writeVarInt(wall.towerSpacing());
        b.writeVarInt(wall.nodes().size());
        for (int i = 0; i < wall.nodes().size(); i++) {
            b.writeBlockPos(wall.nodes().get(i)); b.writeEnum(wall.nodeTowers().get(i));
        }
    }
    static SettlementWall readWall(RegistryFriendlyByteBuf b) {
        var type = b.readEnum(SettlementWall.Type.class);
        var part = b.readEnum(SettlementWall.Part.class);
        boolean closed = b.readBoolean();
        int spacing = b.readVarInt();
        int count = VillagerPayloads.readCollectionSize(b, SettlementWall.MAX_NODES, "wall nodes");
        var nodes = new ArrayList<BlockPos>();
        var towers = new ArrayList<SettlementWall.Tower>();
        for (int i = 0; i < count; i++) {
            nodes.add(b.readBlockPos()); towers.add(b.readEnum(SettlementWall.Tower.class));
        }
        return new SettlementWall(type, nodes, closed, spacing, towers, part);
    }

    public SettlementWallRequestPayload {
        if (session == null || revision <= 0 || action == null || token == null || upgrade == null || pick == null
                || (action == Action.PREVIEW) != (wall != null)) throw new IllegalArgumentException("Invalid wall request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
