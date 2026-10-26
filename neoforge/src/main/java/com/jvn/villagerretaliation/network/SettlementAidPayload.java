package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementAidService;
import com.jvn.villagerretaliation.settlement.SettlementFoodOffer;
import com.jvn.villagerretaliation.settlement.SettlementFoodRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementAidPayload(int requestId, UUID village, String name, long policyRevision, SettlementAidRequestPayload.Policy policy,
                                   boolean authorized, int page, int total, long time, long neededNutrition, SettlementAidService.Result result,
                                   String blocker, List<Entry> entries) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 24;
    public static final Type<SettlementAidPayload> TYPE = VillagerPayloads.type("settlement_aid");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementAidPayload> STREAM_CODEC = VillagerPayloads.codec(SettlementAidPayload::encode, SettlementAidPayload::decode);
    public SettlementAidPayload {
        entries = List.copyOf(entries);
        if (requestId < 0 || village == null || name == null || name.length() > 64 || policyRevision < 0 || policy == null || page < 0
                || total < 0 || total > 1024 || time < 0 || neededNutrition < 0 || result == null || blocker == null || blocker.length() > 80 || entries.size() > PAGE_SIZE)
            throw new IllegalArgumentException("Invalid aid page");
    }
    public record Entry(UUID id, UUID counterpart, String name, boolean donating, long revision, SettlementFoodRequest.State state,
                        long requested, long offered, long delivered, SettlementFoodOffer.Arrangement arrangement, long date, long arrives, String blocker) {
        public Entry {
            if (id == null || counterpart == null || name == null || name.length() > 64 || revision < 0 || state == null
                    || requested <= 0 || requested > 10000000 || offered < 0 || offered > requested || delivered < 0 || delivered > offered
                    || date < 0 || arrives < 0 || blocker == null || blocker.length() > 80) throw new IllegalArgumentException("Invalid aid entry");
        }
        void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(id); buffer.writeUUID(counterpart); buffer.writeUtf(name, 64); buffer.writeBoolean(donating); buffer.writeVarLong(revision);
            buffer.writeEnum(state); buffer.writeVarLong(requested); buffer.writeVarLong(offered); buffer.writeVarLong(delivered);
            buffer.writeBoolean(arrangement != null); if (arrangement != null) buffer.writeEnum(arrangement);
            buffer.writeVarLong(date); buffer.writeVarLong(arrives); buffer.writeUtf(blocker, 80);
        }
        static Entry decode(RegistryFriendlyByteBuf buffer) {
            return new Entry(buffer.readUUID(), buffer.readUUID(), buffer.readUtf(64), buffer.readBoolean(), buffer.readVarLong(),
                    buffer.readEnum(SettlementFoodRequest.State.class), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                    buffer.readBoolean() ? buffer.readEnum(SettlementFoodOffer.Arrangement.class) : null, buffer.readVarLong(), buffer.readVarLong(), buffer.readUtf(80));
        }
    }
    private static void encode(RegistryFriendlyByteBuf buffer, SettlementAidPayload value) {
        buffer.writeVarInt(value.requestId); buffer.writeUUID(value.village); buffer.writeUtf(value.name, 64); buffer.writeVarLong(value.policyRevision);
        value.policy.encode(buffer); buffer.writeBoolean(value.authorized); buffer.writeVarInt(value.page); buffer.writeVarInt(value.total);
        buffer.writeVarLong(value.time); buffer.writeVarLong(value.neededNutrition); buffer.writeEnum(value.result); buffer.writeUtf(value.blocker, 80);
        buffer.writeVarInt(value.entries.size()); value.entries.forEach(entry -> entry.encode(buffer));
    }
    private static SettlementAidPayload decode(RegistryFriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt(); var village = buffer.readUUID(); var name = buffer.readUtf(64); long revision = buffer.readVarLong();
        var policy = SettlementAidRequestPayload.Policy.decode(buffer); boolean authorized = buffer.readBoolean();
        int page = buffer.readVarInt(), total = buffer.readVarInt(); long time = buffer.readVarLong(), needed = buffer.readVarLong();
        var result = buffer.readEnum(SettlementAidService.Result.class); var blocker = buffer.readUtf(80);
        int size = VillagerPayloads.readCollectionSize(buffer, PAGE_SIZE, "food requests"); var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) entries.add(Entry.decode(buffer));
        return new SettlementAidPayload(requestId, village, name, revision, policy, authorized, page, total, time, needed, result, blocker, entries);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
