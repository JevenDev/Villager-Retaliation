package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementViewBatchResultPayload(UUID session, UUID operation, List<Outcome> outcomes) implements CustomPacketPayload {
    public static final Type<SettlementViewBatchResultPayload> TYPE = VillagerPayloads.type("settlement_view_batch_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementViewBatchResultPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session());
        b.writeUUID(p.operation());
        b.writeVarInt(p.outcomes().size());
        for (var result : p.outcomes()) {
            b.writeUUID(result.resident());
            b.writeEnum(result.status());
            b.writeUtf(result.reason(), 128);
        }
    }, b -> {
        UUID session = b.readUUID();
        UUID operation = b.readUUID();
        int count = VillagerPayloads.readCollectionSize(b, SettlementViewBatchPayload.MAX_TARGETS, "view command outcomes");
        List<Outcome> outcomes = new ArrayList<>();
        for (int i = 0; i < count; i++) outcomes.add(new Outcome(b.readUUID(), b.readEnum(Status.class), b.readUtf(128)));
        return new SettlementViewBatchResultPayload(session, operation, outcomes);
    });

    public SettlementViewBatchResultPayload {
        outcomes = List.copyOf(outcomes);
        if (session == null || operation == null || outcomes.isEmpty() || outcomes.size() > SettlementViewBatchPayload.MAX_TARGETS
                || outcomes.stream().map(Outcome::resident).distinct().count() != outcomes.size()) {
            throw new IllegalArgumentException("Invalid command outcomes");
        }
    }

    public enum Status { APPLIED, SKIPPED, FAILED }

    public record Outcome(UUID resident, Status status, String reason) {
        public Outcome {
            if (resident == null || status == null || reason == null || reason.length() > 128) throw new IllegalArgumentException("Invalid outcome");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
