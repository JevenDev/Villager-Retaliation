package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.*;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;

public record SettlementViewOverviewPayload(UUID session, int requestId, long generation, Header header,
                                            Page category, int index, int total, List<Entry> entries)
        implements CustomPacketPayload {
    public static final int PAGE_SIZE = 64;
    public static final List<Page> CATEGORIES = List.of(Page.RESIDENTS, Page.AREAS, Page.PROJECTS, Page.ISSUES);
    public static final Type<SettlementViewOverviewPayload> TYPE = VillagerPayloads.type("settlement_view_overview");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementViewOverviewPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementViewOverviewPayload::encode, SettlementViewOverviewPayload::decode);

    public SettlementViewOverviewPayload {
        if (session == null || requestId < 0 || generation <= 0 || header == null || !CATEGORIES.contains(category)
                || index < 0 || total < 0 || (long) index * PAGE_SIZE > total
                || entries.size() != Math.min(PAGE_SIZE, total - (long) index * PAGE_SIZE)) {
            throw new IllegalArgumentException("Invalid overview page");
        }
        entries = List.copyOf(entries);
        for (var entry : entries) {
            if (!(switch (category) {
                case RESIDENTS -> entry instanceof Resident;
                case AREAS -> entry instanceof Area;
                case PROJECTS -> entry instanceof Project;
                case ISSUES -> entry instanceof Issue;
                default -> false;
            })) throw new IllegalArgumentException("Invalid overview category");
        }
    }

    private static void encode(RegistryFriendlyByteBuf b, SettlementViewOverviewPayload p) {
        b.writeUUID(p.session());
        b.writeVarInt(p.requestId());
        b.writeVarLong(p.generation());
        SettlementManagementPayload.writeHeader(b, p.header());
        b.writeEnum(p.category());
        b.writeVarInt(p.index());
        b.writeVarInt(p.total());
        b.writeVarInt(p.entries().size());
        for (var entry : p.entries()) {
            if (entry instanceof Resident r) {
                b.writeUUID(r.id());
                b.writeUtf(r.name(), 128);
                b.writeResourceLocation(r.profession());
                b.writeBoolean(r.baby());
                b.writeEnum(r.presence());
                b.writeBlockPos(r.position());
                SettlementManagementPayload.writeOptionalId(b, r.area());
                b.writeUtf(r.status(), 64);
                b.writeVarInt(r.roles().size());
                r.roles().forEach(b::writeEnum);
                SettlementManagementActionPayload.writeGuardOrder(b, r.guardOrder());
                b.writeUtf(r.diagnostic(), 180);
            } else SettlementManagementPayload.writeEntry(b, entry);
        }
    }

    private static SettlementViewOverviewPayload decode(RegistryFriendlyByteBuf b) {
        UUID session = b.readUUID();
        int request = b.readVarInt();
        long generation = b.readVarLong();
        var header = SettlementManagementPayload.readHeader(b);
        Page category = b.readEnum(Page.class);
        int index = b.readVarInt();
        int total = b.readVarInt();
        int count = VillagerPayloads.readCollectionSize(b, PAGE_SIZE, "view overview");
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (category != Page.RESIDENTS) {
                entries.add(SettlementManagementPayload.readEntry(b, category));
                continue;
            }
            UUID id = b.readUUID();
            String name = b.readUtf(128);
            var profession = b.readResourceLocation();
            boolean baby = b.readBoolean();
            var presence = b.readEnum(VillageOverviewPayload.Presence.class);
            var position = b.readBlockPos();
            UUID area = SettlementManagementPayload.readOptionalId(b);
            String status = b.readUtf(64);
            int roles = VillagerPayloads.readCollectionSize(b,
                    com.jvn.villagerretaliation.interaction.HiredVillagerRole.values().length, "view roles");
            List<com.jvn.villagerretaliation.interaction.HiredVillagerRole> available = new ArrayList<>();
            for (int r = 0; r < roles; r++) available.add(b.readEnum(com.jvn.villagerretaliation.interaction.HiredVillagerRole.class));
            entries.add(new Resident(id, name, profession, baby, presence, position, area, -1, -1, status, available,
                    SettlementManagementActionPayload.readGuardOrder(b), null, null, b.readUtf(180)));
        }
        return new SettlementViewOverviewPayload(session, request, generation, header, category, index, total, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
