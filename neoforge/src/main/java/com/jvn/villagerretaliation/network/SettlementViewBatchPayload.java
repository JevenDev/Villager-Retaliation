package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import com.jvn.villagerretaliation.settlement.SettlementViewAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementViewBatchPayload(UUID session, UUID operation, SettlementViewAction action, List<Target> targets,
                                         UUID area, SettlementGuardOrder order, HiredTemporaryTask task, boolean append) implements CustomPacketPayload {
    public SettlementViewBatchPayload(UUID session, UUID operation, SettlementViewAction action, List<Target> targets,
                                      UUID area, SettlementGuardOrder order) {
        this(session, operation, action, targets, area, order, null, false);
    }

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
            if (p.action() == SettlementViewAction.ASSIGN_HOME) {
                b.writeBlockPos(target.bed());
                b.writeBoolean(target.expectedBed() != null);
                if (target.expectedBed() != null) b.writeBlockPos(target.expectedBed());
            }
        }
        SettlementManagementPayload.writeOptionalId(b, p.area());
        SettlementManagementActionPayload.writeGuardOrder(b, p.order());
        b.writeBoolean(p.task() != null);
        if (p.task() != null) p.task().write(b);
        b.writeBoolean(p.append());
    }, b -> {
        UUID session = b.readUUID();
        UUID operation = b.readUUID();
        var action = b.readEnum(SettlementViewAction.class);
        int count = VillagerPayloads.readCollectionSize(b, MAX_TARGETS, "view command targets");
        List<Target> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID resident = b.readUUID();
            UUID expectedArea = SettlementManagementPayload.readOptionalId(b);
            targets.add(action == SettlementViewAction.ASSIGN_HOME
                    ? new Target(resident, expectedArea, b.readBlockPos(), b.readBoolean() ? b.readBlockPos() : null)
                    : new Target(resident, expectedArea));
        }
        return new SettlementViewBatchPayload(session, operation, action, targets, SettlementManagementPayload.readOptionalId(b),
                SettlementManagementActionPayload.readGuardOrder(b), b.readBoolean() ? HiredTemporaryTask.read(b) : null, b.readBoolean());
    });

    public SettlementViewBatchPayload {
        targets = List.copyOf(targets);
        if (session == null || operation == null || action == null || targets.isEmpty() || targets.size() > MAX_TARGETS
                || targets.stream().map(Target::resident).distinct().count() != targets.size()
                || (action == SettlementViewAction.ASSIGN) != (area != null)
                || (action.targeting() != null) != (order != null)
                || order != null && order.mode() != action.targeting()
                || (action == SettlementViewAction.TEMPORARY_TASK) != (task != null)
                || targets.stream().anyMatch(target -> action == SettlementViewAction.ASSIGN_HOME
                        ? target.bed() == null || target.expectedArea() != null
                        : target.bed() != null || target.expectedBed() != null)
                || action == SettlementViewAction.ASSIGN_HOME && targets.stream().map(Target::bed).distinct().count() != targets.size()
                || append && task == null) throw new IllegalArgumentException("Invalid view command");
    }

    // operation IDs carry a session prefix and increasing sequence so evicted results cannot be replayed
    public static UUID operation(UUID session, long sequence) {
        if (sequence <= 0) throw new IllegalArgumentException("Invalid operation sequence");
        return new UUID(session.getMostSignificantBits(), sequence);
    }

    public long sequence() {
        return operation.getMostSignificantBits() == session.getMostSignificantBits()
                ? Math.max(0L, operation.getLeastSignificantBits()) : 0L;
    }

    public record Target(UUID resident, UUID expectedArea, BlockPos bed, BlockPos expectedBed) {
        public Target(UUID resident, UUID expectedArea) { this(resident, expectedArea, null, null); }

        public Target {
            java.util.Objects.requireNonNull(resident);
            if (bed != null) bed = bed.immutable();
            if (expectedBed != null) expectedBed = expectedBed.immutable();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
