package com.jvn.villagerretaliation.client.settlement;

public final class SettlementViewInteraction {
    public enum Mode { SELECT, DRAW_AREA, EDIT_BOUNDS, PLACE_BUILDING, SELECT_PLOT, GUARD_TARGET, TASK_AREA, EDIT_ROUTE, SELECT_STORAGE }
    private Mode mode = Mode.SELECT;
    private final SettlementRouteEditor route = new SettlementRouteEditor();

    public Mode mode() { return this.mode; }
    public boolean is(Mode mode) { return this.mode == mode; }
    SettlementRouteEditor route() { return this.route; }
    public void enter(Mode mode) {
        this.mode = java.util.Objects.requireNonNull(mode);
        if (mode != Mode.EDIT_ROUTE) this.route.clear();
    }
}
