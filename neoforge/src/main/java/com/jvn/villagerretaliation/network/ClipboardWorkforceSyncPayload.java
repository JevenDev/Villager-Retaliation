package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.JobSummary;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningSummary;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningType;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerRow;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerStatus;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClipboardWorkforceSyncPayload(ClipboardWorkforceSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<ClipboardWorkforceSyncPayload> TYPE = VillagerPayloads.type("clipboard_workforce_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardWorkforceSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardWorkforceSyncPayload::encode, ClipboardWorkforceSyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardWorkforceSyncPayload payload) {
        ClipboardWorkforceSnapshot snapshot = payload.snapshot() == null ? ClipboardWorkforceSnapshot.empty() : payload.snapshot();
        buffer.writeVarInt(snapshot.totalHired());
        buffer.writeVarInt(snapshot.maxHired());
        buffer.writeVarInt(snapshot.workingCount());
        buffer.writeVarInt(snapshot.idleCount());
        buffer.writeVarInt(snapshot.warningCount());
        buffer.writeVarInt(snapshot.assignedStorageCount());
        buffer.writeVarInt(snapshot.paymentContainerCount());
        buffer.writeVarInt(snapshot.dailyWages());
        writeJobs(buffer, snapshot.jobs());
        writeWorkers(buffer, snapshot.workers());
        writeWarnings(buffer, snapshot.warnings());
    }

    private static ClipboardWorkforceSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClipboardWorkforceSyncPayload(new ClipboardWorkforceSnapshot(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readJobs(buffer),
                readWorkers(buffer),
                readWarnings(buffer)
        ));
    }

    private static void writeJobs(RegistryFriendlyByteBuf buffer, List<JobSummary> jobs) {
        buffer.writeVarInt(jobs.size());
        for (JobSummary job : jobs) {
            buffer.writeEnum(job.role());
            buffer.writeVarInt(job.count());
        }
    }

    private static List<JobSummary> readJobs(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<JobSummary> jobs = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            jobs.add(new JobSummary(buffer.readEnum(HiredVillagerRole.class), buffer.readVarInt()));
        }
        return jobs;
    }

    private static void writeWorkers(RegistryFriendlyByteBuf buffer, List<WorkerRow> workers) {
        buffer.writeVarInt(workers.size());
        for (WorkerRow worker : workers) {
            buffer.writeUUID(worker.villagerId());
            buffer.writeUtf(worker.displayName(), 128);
            buffer.writeEnum(worker.role());
            buffer.writeEnum(worker.status());
            buffer.writeUtf(worker.target(), 64);
            buffer.writeBoolean(worker.storageAssigned());
            buffer.writeVarInt(worker.storageCount());
            buffer.writeVarInt(worker.workRadius());
            buffer.writeVarInt(worker.dailyWage());
            buffer.writeBoolean(worker.inventoryFull());
            buffer.writeBoolean(worker.unpaid());
            buffer.writeBoolean(worker.noStorage());
            buffer.writeBoolean(worker.noTargets());
            buffer.writeBoolean(worker.tooFar());
        }
    }

    private static List<WorkerRow> readWorkers(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<WorkerRow> workers = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            workers.add(new WorkerRow(
                    buffer.readUUID(),
                    buffer.readUtf(128),
                    buffer.readEnum(HiredVillagerRole.class),
                    buffer.readEnum(WorkerStatus.class),
                    buffer.readUtf(64),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            ));
        }
        return workers;
    }

    private static void writeWarnings(RegistryFriendlyByteBuf buffer, List<WarningSummary> warnings) {
        buffer.writeVarInt(warnings.size());
        for (WarningSummary warning : warnings) {
            buffer.writeEnum(warning.type());
            buffer.writeEnum(warning.role());
            buffer.writeVarInt(warning.count());
        }
    }

    private static List<WarningSummary> readWarnings(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<WarningSummary> warnings = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            warnings.add(new WarningSummary(
                    buffer.readEnum(WarningType.class),
                    buffer.readEnum(HiredVillagerRole.class),
                    buffer.readVarInt()
            ));
        }
        return warnings;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
