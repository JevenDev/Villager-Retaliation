package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.ClipboardWorkforceService;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerStatus;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

record SettlementWorkStatus(String status, String diagnostic) {
    static SettlementWorkStatus observe(ServerLevel level, Villager villager) {
        var session = HiredWorkSession.active(level, villager);
        var brain = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        if (brain.failureReason().startsWith("temporary_task_") || brain.failureReason().startsWith("fishing_")) return new SettlementWorkStatus(brain.failureReason(),
                SettlementWorkerDiagnostics.describe(brain.failureReason()));
        if (villager.isSleeping() || brain.status().equals("interaction.work.status.sleeping")
                || brain.status().equals("interaction.work.status.tired")) return new SettlementWorkStatus("resting", "");
        if (brain.taskState() == HiredWorkerTaskState.MOVING_TO_TARGET || brain.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA)
            return new SettlementWorkStatus("workforce.pathing", "");
        if (brain.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE || brain.taskState() == HiredWorkerTaskState.DEPOSITING)
            return new SettlementWorkStatus("workforce.depositing", "");
        if (brain.status().equals("interaction.work.status.returning_bounds")) return new SettlementWorkStatus("workforce.pathing", "");
        if (brain.status().equals("interaction.work.hauling.no_demand")) return new SettlementWorkStatus("no_demand", "");
        if (brain.status().equals("interaction.work.builder.choose_structure")) return new SettlementWorkStatus("no_construction_project", "");
        if (brain.status().endsWith(".no_filter")) return new SettlementWorkStatus("no_filter", "");
        if (brain.status().endsWith(".no_crafting_table") || brain.status().equals("interaction.work.craftsman.no_table")) return new SettlementWorkStatus("no_crafting_table", "");
        if (brain.status().equals("interaction.work.cooking.no_station")) return new SettlementWorkStatus("no_furnace", "");
        if (brain.failureReason().endsWith("_profession_required")) return new SettlementWorkStatus("profession_required", "");
        var preview = ClipboardWorkforceService.workPreview(session, brain);
        String status = !preview.diagnostic().isBlank() && (brain.taskState() == HiredWorkerTaskState.SELECTING_TARGET
                || preview.status() == WorkerStatus.WAITING
                || preview.status() == WorkerStatus.UNKNOWN)
                ? "needs_attention" : "workforce." + preview.status().name().toLowerCase(Locale.ROOT);
        return new SettlementWorkStatus(status, preview.diagnostic());
    }
}
