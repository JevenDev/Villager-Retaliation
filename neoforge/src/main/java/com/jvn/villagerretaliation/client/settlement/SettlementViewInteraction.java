package com.jvn.villagerretaliation.client.settlement;

public final class SettlementViewInteraction {
    public enum Mode { SELECT, DRAW_AREA, EDIT_BOUNDS, PLACE_BUILDING, SELECT_PLOT, GUARD_TARGET, TASK_AREA }
    private Mode mode = Mode.SELECT;

    public Mode mode() { return this.mode; }
    public boolean is(Mode mode) { return this.mode == mode; }
    public void enter(Mode mode) { this.mode = java.util.Objects.requireNonNull(mode); }
}
