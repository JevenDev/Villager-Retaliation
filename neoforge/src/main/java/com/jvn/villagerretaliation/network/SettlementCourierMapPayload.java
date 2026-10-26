package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementCourierMapPayload(int requestId, UUID village, int page, boolean moreJourneys, boolean terrainAllowed,
                                          long epoch, long next, boolean moreTerrain, List<Cell> cells, List<Courier> couriers,
                                          UUID routeJourney, int routeKey, int routeOffset, boolean moreRoute, List<BlockPos> route) implements CustomPacketPayload {
    public enum Stage { PREPARING, OUTBOUND, COLLECTION, DELIVERY, RETURNING, BLOCKED, COMPLETE, ENCOUNTER }
    public record Cell(BlockPos position, int color) {
        public Cell { if (position == null || color <= 0 || color > 0xffffff) throw new IllegalArgumentException("Invalid observed courier terrain"); }
    }
    public record Courier(UUID id, String name, BlockPos position, Stage stage, boolean observed, boolean selectedVillage, String blocker) {
        public Courier {
            if (id == null || name == null || name.length() > 128 || position == null || stage == null || blocker == null || blocker.length() > 80
                    || !selectedVillage && (stage != Stage.ENCOUNTER || !observed || !blocker.isEmpty())) throw new IllegalArgumentException("Invalid courier marker");
        }
    }
    public static final Type<SettlementCourierMapPayload> TYPE = VillagerPayloads.type("settlement_courier_map");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementCourierMapPayload> STREAM_CODEC = VillagerPayloads.codec(
            SettlementCourierMapPayload::encode, SettlementCourierMapPayload::decode);
    public SettlementCourierMapPayload {
        cells = List.copyOf(cells); couriers = List.copyOf(couriers); route = List.copyOf(route);
        if (requestId < 0 || page < 0 || page > 128 || epoch < 0 || next < 0 || cells.size() > 64 || couriers.size() > 8
                || route.size() > 256 || routeOffset < 0 || routeOffset > 65536 || routeJourney == null && !route.isEmpty()
                || !terrainAllowed && (!cells.isEmpty() || epoch != 0 || next != 0 || moreTerrain)) throw new IllegalArgumentException("Invalid courier map response");
    }
    private static void encode(RegistryFriendlyByteBuf b, SettlementCourierMapPayload p) {
        b.writeVarInt(p.requestId); VillageRelationsRequestPayload.writeId(b, p.village); b.writeVarInt(p.page);
        b.writeBoolean(p.moreJourneys); b.writeBoolean(p.terrainAllowed); b.writeVarLong(p.epoch); b.writeVarLong(p.next); b.writeBoolean(p.moreTerrain);
        b.writeVarInt(p.cells.size()); for (var cell : p.cells) { b.writeBlockPos(cell.position); b.writeInt(cell.color); }
        b.writeVarInt(p.couriers.size()); for (var courier : p.couriers) {
            b.writeUUID(courier.id); b.writeUtf(courier.name, 128); b.writeBlockPos(courier.position); b.writeEnum(courier.stage);
            b.writeBoolean(courier.observed); b.writeBoolean(courier.selectedVillage); b.writeUtf(courier.blocker, 80);
        }
        VillageRelationsRequestPayload.writeId(b, p.routeJourney); b.writeInt(p.routeKey); b.writeVarInt(p.routeOffset); b.writeBoolean(p.moreRoute);
        b.writeVarInt(p.route.size()); p.route.forEach(b::writeBlockPos);
    }
    private static SettlementCourierMapPayload decode(RegistryFriendlyByteBuf b) {
        int id = b.readVarInt(); UUID village = VillageRelationsRequestPayload.readId(b); int page = b.readVarInt();
        boolean more = b.readBoolean(), allowed = b.readBoolean(); long epoch = b.readVarLong(), next = b.readVarLong(); boolean moreTerrain = b.readBoolean();
        var cells = new ArrayList<Cell>(); for (int count = count(b, 64); count > 0; count--) cells.add(new Cell(b.readBlockPos(), b.readInt()));
        var couriers = new ArrayList<Courier>(); for (int count = count(b, 8); count > 0; count--)
            couriers.add(new Courier(b.readUUID(), b.readUtf(128), b.readBlockPos(), b.readEnum(Stage.class), b.readBoolean(), b.readBoolean(), b.readUtf(80)));
        var journey = VillageRelationsRequestPayload.readId(b); int key = b.readInt(), offset = b.readVarInt(); boolean moreRoute = b.readBoolean();
        var route = new ArrayList<BlockPos>(); for (int count = count(b, 256); count > 0; count--) route.add(b.readBlockPos());
        return new SettlementCourierMapPayload(id, village, page, more, allowed, epoch, next, moreTerrain, cells, couriers, journey, key, offset, moreRoute, route);
    }
    private static int count(RegistryFriendlyByteBuf b, int limit) {
        int count = b.readVarInt(); if (count < 0 || count > limit) throw new IllegalArgumentException("Courier map page exceeds its limit"); return count;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
