package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.network.ProductionRequestPayload;
import com.jvn.villagerretaliation.network.ProductionSnapshotPayload;
import com.jvn.villagerretaliation.network.ProductionTarget;
import java.util.UUID;
import net.minecraft.core.HolderLookup;

public final class ProductionClientState {
    private final ProductionTarget target;
    private ProductionSnapshotPayload snapshot;
    private HiredProductionQueue queue;
    private UUID pending;
    private boolean action;
    private long sentAt = -1L;
    private String feedback = "";

    public ProductionClientState(ProductionTarget target) {
        this.target = target;
    }

    public ProductionTarget target() { return this.target; }
    public ProductionSnapshotPayload snapshot() { return this.snapshot; }
    public HiredProductionQueue queue() { return this.queue; }
    public String feedback() { return this.feedback; }
    public boolean busy() { return this.pending != null && this.action; }
    public boolean editable() { return !busy() && this.snapshot != null && this.snapshot.editable(); }

    public ProductionRequestPayload request(ProductionRequestPayload.Command command, long now) {
        boolean viewing = command instanceof ProductionRequestPayload.View;
        if (this.pending != null && (this.action || viewing) || !viewing && !editable()) return null;
        this.action = !viewing;
        this.pending = UUID.randomUUID();
        this.sentAt = now;
        if (!(command instanceof ProductionRequestPayload.View)) this.feedback = "Saving...";
        return new ProductionRequestPayload(this.target, this.pending, this.queue == null ? 0L : this.queue.configurationRevision(), command);
    }

    public boolean accept(ProductionSnapshotPayload payload, HolderLookup.Provider provider) {
        if (!this.target.equals(payload.target()) || !payload.request().equals(this.pending)) return false;
        this.snapshot = payload;
        this.queue = payload.queue() == null ? null : HiredProductionQueue.load(payload.queue(), provider, ignored -> {});
        this.pending = null;
        this.feedback = payload.feedback().isEmpty() ? payload.blocker() : payload.feedback();
        return true;
    }

    public ProductionRequestPayload poll(long now) {
        if (this.pending != null && now - this.sentAt >= 10_000L) {
            this.pending = null;
            this.feedback = "No acknowledgement received. Refreshing orders...";
        }
        return this.pending == null && (this.sentAt < 0L || now - this.sentAt >= 1_000L)
                ? request(new ProductionRequestPayload.View(), now) : null;
    }
}
