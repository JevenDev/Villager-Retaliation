package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementPath;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record SettlementPathRequestPayload(UUID session, long revision, Action action, UUID token, SettlementPath path)
        implements CustomPacketPayload {
    public enum Action { PREVIEW, CONFIRM, CANCEL }
    public static final Type<SettlementPathRequestPayload> TYPE = VillagerPayloads.type("settlement_path_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementPathRequestPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session());
        b.writeVarLong(p.revision());
        b.writeEnum(p.action());
        b.writeUUID(p.token());
        b.writeBoolean(p.path() != null);
        if (p.path() != null) {
            b.writeVarInt(p.path().width());
            b.writeEnum(p.path().style());
            b.writeEnum(p.path().material());
            b.writeLong(p.path().seed());
            b.writeBoolean(p.path().branch());
            b.writeVarInt(p.path().nodes().size());
            for (var node : p.path().nodes()) {
                writeVector(b, node.position());
                writeVector(b, node.incoming());
                writeVector(b, node.outgoing());
            }
        }
    }, b -> {
        var session = b.readUUID();
        long revision = b.readVarLong();
        var action = b.readEnum(Action.class);
        var token = b.readUUID();
        SettlementPath path = null;
        if (b.readBoolean()) {
            int width = b.readVarInt();
            var style = b.readEnum(SettlementPath.Style.class);
            var material = b.readEnum(SettlementPath.Material.class);
            long seed = b.readLong();
            boolean branch = b.readBoolean();
            int count = VillagerPayloads.readCollectionSize(b, SettlementPath.MAX_NODES, "path nodes");
            var nodes = new ArrayList<SettlementPath.Node>();
            for (int i = 0; i < count; i++) nodes.add(new SettlementPath.Node(readVector(b), readVector(b), readVector(b)));
            path = new SettlementPath(nodes, width, style, material, seed, branch);
        }
        return new SettlementPathRequestPayload(session, revision, action, token, path);
    });

    public SettlementPathRequestPayload {
        if (session == null || revision <= 0 || action == null || token == null
                || (action == Action.PREVIEW) != (path != null)) throw new IllegalArgumentException("Invalid path request");
    }
    private static void writeVector(RegistryFriendlyByteBuf b, Vec3 point) {
        b.writeDouble(point.x); b.writeDouble(point.y); b.writeDouble(point.z);
    }
    private static Vec3 readVector(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
