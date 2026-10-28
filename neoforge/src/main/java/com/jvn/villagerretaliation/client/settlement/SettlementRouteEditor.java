package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

final class SettlementRouteEditor {
    private enum State { EDITING, SAVING, UNCERTAIN, RELOADING }
    private State state = State.EDITING;
    private SettlementRouteDraft draft;
    private UUID area;
    private UUID resident;
    private SettlementGuardOrder expected;
    private boolean inherited;
    private boolean dragHit;
    private double dragX;
    private double dragY;
    private BlockPos preview;

    void begin(UUID area, UUID resident, HiredVillagerRole role, SettlementGuardOrder saved, SettlementGuardOrder defaultRoute) {
        clear();
        var initial = saved != null ? saved : defaultRoute;
        this.area = area;
        this.resident = resident;
        this.expected = resident == null ? saved : initial;
        this.inherited = resident != null && saved == null;
        this.draft = new SettlementRouteDraft(role, initial);
    }

    SettlementRouteDraft draft() { return this.draft; }
    UUID area() { return this.area; }
    UUID resident() { return this.resident; }
    boolean inherited() { return this.inherited; }
    boolean uncertain() { return this.state == State.UNCERTAIN; }
    boolean reloading() { return this.state == State.RELOADING; }
    boolean dragHit() { return this.dragHit; }
    BlockPos preview() { return this.preview; }
    void preview(BlockPos position) { this.preview = position; }

    boolean canSubmit() { return this.draft != null && !this.draft.dragging() && this.state == State.EDITING; }

    SettlementManagementActionPayload.SaveRoute submit(boolean inherit) {
        if (!canSubmit() || !inherit && !this.draft.validation().isEmpty()) return null;
        this.state = State.SAVING;
        return new SettlementManagementActionPayload.SaveRoute(this.area, this.resident, this.expected,
                inherit ? null : this.draft.route());
    }

    boolean accept(boolean applied) {
        if (this.state != State.SAVING) return false;
        this.state = State.EDITING;
        return applied;
    }

    boolean timeout(boolean submitting) {
        if (this.state != State.SAVING || submitting) return false;
        this.state = State.UNCERTAIN;
        return true;
    }

    void reload() { cancelDrag(); this.state = State.RELOADING; }

    void edit(int action) {
        if (this.draft == null || this.state == State.SAVING || this.state == State.RELOADING) return;
        switch (action) {
            case 4 -> this.draft.insertAfterSelection();
            case 5 -> this.draft.remove();
            case 6 -> this.draft.reorder(-1);
            case 7 -> this.draft.reorder(1);
            case 8 -> this.draft.toggleLoop();
            case 9 -> this.draft.branchFromSelection();
            case 10 -> this.draft.clear();
            case 11 -> {
                if (this.draft.inserting()) this.draft.insertAfterSelection();
                if (this.draft.branching()) this.draft.branchFromSelection();
            }
            default -> { }
        }
    }

    void beginDrag(int handle, double x, double y) {
        this.draft.beginDrag(handle);
        this.dragHit = true;
        this.dragX = x;
        this.dragY = y;
    }

    void drag(double x, double y, BlockPos surface) {
        this.dragHit = surface != null;
        this.dragX = x;
        this.dragY = y;
        if (surface != null) this.draft.dragTo(surface.above());
    }

    boolean movedSinceDrag(double x, double y) { return x != this.dragX || y != this.dragY; }

    boolean finishDrag(Predicate<BlockPos> available) {
        return this.draft.finishDrag(position -> this.dragHit && available.test(position));
    }

    void cancelDrag() {
        if (this.draft != null) this.draft.cancelDrag();
        this.dragHit = false;
        this.preview = null;
    }

    void clear() {
        cancelDrag();
        this.draft = null;
        this.area = null;
        this.resident = null;
        this.expected = null;
        this.inherited = false;
        this.state = State.EDITING;
        this.dragX = 0;
        this.dragY = 0;
    }
}
