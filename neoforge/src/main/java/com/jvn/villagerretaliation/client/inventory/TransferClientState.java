package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.network.TransferRequestPayload;
import com.jvn.villagerretaliation.network.TransferSnapshotPayload;
import com.jvn.villagerretaliation.network.ProductionTarget;
import java.util.UUID;
import net.minecraft.core.HolderLookup;

public final class TransferClientState {
    private final ProductionTarget target;
    private TransferSnapshotPayload snapshot;
    private HiredTransferQueue queue;
    private UUID pending;
    private boolean action;
    private long sentAt = -1L;
    private String feedback = "";

    public TransferClientState(ProductionTarget target) {
        this.target = target;
    }

    public ProductionTarget target() { return this.target; }
    public TransferSnapshotPayload snapshot() { return this.snapshot; }
    public HiredTransferQueue queue() { return this.queue; }
    public String feedback() { return this.feedback; }
    public boolean busy() { return this.pending != null && this.action; }
    public boolean editable() { return !busy() && this.snapshot != null && this.snapshot.editable(); }

    public TransferRequestPayload request(TransferRequestPayload.Command command, long now) {
        boolean viewing = command instanceof TransferRequestPayload.View;
        if (this.pending != null && (this.action || viewing) || !viewing && !editable()) return null;
        this.action = !viewing;
        this.pending = UUID.randomUUID();
        this.sentAt = now;
        if (!(command instanceof TransferRequestPayload.View)) this.feedback = "Saving...";
        return new TransferRequestPayload(this.target, this.pending, this.queue == null ? 0L : this.queue.revision(), command);
    }

    public boolean accept(TransferSnapshotPayload payload, HolderLookup.Provider provider) {
        if (!this.target.equals(payload.target()) || !payload.request().equals(this.pending)) return false;
        this.snapshot = payload;
        this.queue = payload.queue() == null ? null : HiredTransferQueue.load(payload.queue(), provider, ignored -> {});
        this.pending = null;
        this.feedback = payload.feedback().isEmpty() ? payload.blocker() : payload.feedback();
        return true;
    }

    public TransferRequestPayload poll(long now) {
        if (this.pending != null && now - this.sentAt >= 10_000L) {
            this.pending = null;
            this.feedback = "No acknowledgement received. Refreshing orders...";
        }
        return this.pending == null && (this.sentAt < 0L || now - this.sentAt >= 1_000L)
                ? request(new TransferRequestPayload.View(), now) : null;
    }
}
