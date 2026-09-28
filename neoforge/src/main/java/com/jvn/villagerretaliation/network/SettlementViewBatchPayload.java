package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import com.jvn.villagerretaliation.settlement.SettlementViewAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementViewBatchPayload(UUID session, UUID operation, SettlementViewAction action, List<Target> targets,
                                         UUID area, SettlementGuardOrder order) implements CustomPacketPayload {
    public static final int MAX_TARGETS = 128;
    public static final Type<SettlementViewBatchPayload> TYPE = VillagerPayloads.type("settlement_view_batch");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementViewBatchPayload> STREAM_CODEC = VillagerPayloads.codec((b, p) -> {
        b.writeUUID(p.session());
        b.writeUUID(p.operation());
        b.writeEnum(p.action());
        b.writeVarInt(p.targets().size());
        for (var target : p.targets()) {
            b.writeUUID(target.resident());
            SettlementManagementPayload.writeOptionalId(b, target.expectedArea());
        }
        SettlementManagementPayload.writeOptionalId(b, p.area());
        SettlementManagementActionPayload.writeGuardOrder(b, p.order());
    }, b -> {
        UUID session = b.readUUID();
        UUID operation = b.readUUID();
        var action = b.readEnum(SettlementViewAction.class);
        int count = VillagerPayloads.readCollectionSize(b, MAX_TARGETS, "view command targets");
        List<Target> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) targets.add(new Target(b.readUUID(), SettlementManagementPayload.readOptionalId(b)));
        return new SettlementViewBatchPayload(session, operation, action, targets, SettlementManagementPayload.readOptionalId(b),
                SettlementManagementActionPayload.readGuardOrder(b));
    });

    public SettlementViewBatchPayload {
        targets = List.copyOf(targets);
        if (session == null || operation == null || action == null || targets.isEmpty() || targets.size() > MAX_TARGETS
                || targets.stream().map(Target::resident).distinct().count() != targets.size()
                || (action == SettlementViewAction.ASSIGN) != (area != null)
                || (action.targeting() != null) != (order != null)
                || order != null && order.mode() != action.targeting()) throw new IllegalArgumentException("Invalid view command");
    }

    public record Target(UUID resident, UUID expectedArea) {
        public Target { java.util.Objects.requireNonNull(resident); }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
