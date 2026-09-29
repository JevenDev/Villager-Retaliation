package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public enum SettlementViewAction {
    MOVE("Move", SettlementGuardOrder.Mode.MOVE),
    PATROL("Patrol", SettlementGuardOrder.Mode.PATROL),
    DEFEND("Defend", SettlementGuardOrder.Mode.DEFEND),
    RETURN("Return to assignment", null),
    ENABLE("Enable work", null),
    PAUSE("Pause work", null),
    ASSIGN("Assign to area", null),
    TEMPORARY_TASK("Temporary task", null),
    CANCEL_TASKS("Cancel temporary tasks", null);

    private final String label;
    private final SettlementGuardOrder.Mode targeting;

    SettlementViewAction(String label, SettlementGuardOrder.Mode targeting) {
        this.label = label;
        this.targeting = targeting;
    }

    public String label() { return this.label; }
    public SettlementPermission permission() { return SettlementPermission.STAFF; }
    public SettlementGuardOrder.Mode targeting() { return this.targeting; }
    public boolean guard() { return this == MOVE || this == PATROL || this == DEFEND || this == RETURN; }

    public boolean eligible(SettlementManagementPayload.Resident resident, SettlementManagementPayload.Area workplace) {
        if (resident == null || resident.baby()) return false;
        if (this == TEMPORARY_TASK || this == CANCEL_TASKS) return workplace != null
                && com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.forRole(workplace.kind().role()) != null;
        if (this == ASSIGN) return workplace != null && workplace.kind().role() != null && resident.roles().contains(workplace.kind().role());
        return workplace != null && workplace.kind().role() != null && (!guard() || workplace.kind() == SettlementAreaType.GUARD_POST);
    }

    boolean execute(ServerPlayer player, SettlementRecord town, UUID resident, UUID area, SettlementGuardOrder order) {
        return switch (this) {
            case MOVE, PATROL, DEFEND, RETURN -> SettlementGuardService.temporaryCommand(player, town, resident, order);
            case ENABLE, PAUSE -> SettlementWorkerConfigurationService.setEnabled(player, town, resident, town.duties().get(resident), this == ENABLE);
            case ASSIGN -> SettlementWorkforceService.assignResident(player.serverLevel(), player, town.id(), resident, area);
            case TEMPORARY_TASK, CANCEL_TASKS -> false;
        };
    }
}
