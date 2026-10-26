package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.jvn.villagerretaliation.diplomacy.VillageRelation;
import com.jvn.villagerretaliation.diplomacy.VillageStanding;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VillageRelationsPayload(int requestId, UUID perspective, Entry origin,
                                      VillageRelationsRequestPayload.View view, int index, int total, long revision, long time,
                                      boolean represents, boolean inspects, VillageDiplomacyService.Result result,
                                      String blocker, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<VillageRelationsPayload> TYPE = VillagerPayloads.type("village_relations");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageRelationsPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillageRelationsPayload::encode, VillageRelationsPayload::decode);

    public VillageRelationsPayload {
        entries = List.copyOf(entries);
        if (requestId < 0 || view == null || index < 0 || total < 0 || revision < 0 || time < 0 || result == null
                || blocker == null || blocker.length() > 256 || entries.size() > VillageRelationsRequestPayload.PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid relations page");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillageRelationsPayload value) {
        buffer.writeVarInt(value.requestId()); VillageRelationsRequestPayload.writeId(buffer, value.perspective());
        buffer.writeBoolean(value.origin() != null);
        if (value.origin() != null) value.origin().encode(buffer);
        buffer.writeEnum(value.view()); buffer.writeVarInt(value.index()); buffer.writeVarInt(value.total());
        buffer.writeVarLong(value.revision()); buffer.writeVarLong(value.time());
        buffer.writeBoolean(value.represents()); buffer.writeBoolean(value.inspects()); buffer.writeEnum(value.result());
        buffer.writeUtf(value.blocker(), 256); buffer.writeVarInt(value.entries().size());
        value.entries().forEach(entry -> entry.encode(buffer));
    }

    private static VillageRelationsPayload decode(RegistryFriendlyByteBuf buffer) {
        int request = buffer.readVarInt(); UUID perspective = VillageRelationsRequestPayload.readId(buffer);
        Entry origin = buffer.readBoolean() ? Entry.decode(buffer) : null;
        var view = buffer.readEnum(VillageRelationsRequestPayload.View.class);
        int index = buffer.readVarInt(), total = buffer.readVarInt();
        long revision = buffer.readVarLong(), time = buffer.readVarLong();
        boolean represents = buffer.readBoolean(), inspects = buffer.readBoolean();
        var result = buffer.readEnum(VillageDiplomacyService.Result.class);
        String blocker = buffer.readUtf(256);
        int size = VillagerPayloads.readCollectionSize(buffer, VillageRelationsRequestPayload.PAGE_SIZE, "relations");
        var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) entries.add(Entry.decode(buffer));
        return new VillageRelationsPayload(request, perspective, origin, view, index, total, revision, time,
                represents, inspects, result, blocker, entries);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(UUID id, String name, ResourceLocation dimension, BlockPos position,
                        boolean relationKnown, int standing, VillageStanding.Band band, VillageRelation condition,
                        boolean established, boolean personal, boolean shared, String attribution, String reason,
                        long date, long arrival) {
        public Entry {
            if (id == null || name == null || name.length() > 64 || dimension == null || position == null
                    || Math.abs((long) standing) > 100 || band == null || condition == null
                    || attribution == null || attribution.length() > 64 || reason == null || reason.length() > 64
                    || date < 0 || arrival < 0) throw new IllegalArgumentException("Invalid relations entry");
            position = position.immutable();
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(id); buffer.writeUtf(name, 64); buffer.writeResourceLocation(dimension); buffer.writeBlockPos(position);
            buffer.writeBoolean(relationKnown); buffer.writeInt(standing); buffer.writeEnum(band); buffer.writeEnum(condition);
            buffer.writeBoolean(established); buffer.writeBoolean(personal); buffer.writeBoolean(shared);
            buffer.writeUtf(attribution, 64); buffer.writeUtf(reason, 64); buffer.writeVarLong(date); buffer.writeVarLong(arrival);
        }

        private static Entry decode(RegistryFriendlyByteBuf buffer) {
            return new Entry(buffer.readUUID(), buffer.readUtf(64), buffer.readResourceLocation(), buffer.readBlockPos(),
                    buffer.readBoolean(), buffer.readInt(), buffer.readEnum(VillageStanding.Band.class), buffer.readEnum(VillageRelation.class),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(64), buffer.readUtf(64),
                    buffer.readVarLong(), buffer.readVarLong());
        }
    }
}
