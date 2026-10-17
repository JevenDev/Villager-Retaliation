package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkerFacts;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerStatus;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.WorkBlocker;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

record SettlementWorkStatus(String status, String diagnostic) {
    static SettlementWorkStatus observe(ServerLevel level, Villager villager) {
        var session = HiredWorkSession.active(level, villager);
        var brain = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        var blocker = WorkBlocker.read(session.state());
        if (villager.isSleeping() || blocker == WorkBlocker.RESTING) return new SettlementWorkStatus("resting", "");
        if (session.role() == com.jvn.villagerretaliation.interaction.HiredVillagerRole.FARMING
                && brain.status().startsWith("interaction.work.farming.planting_"))
            return new SettlementWorkStatus(brain.status().endsWith("_missing") ? "missing_planting_item"
                    : brain.status().endsWith("_restricted") ? "planting_restricted" : "planting",
                    SettlementPlanting.description(session.state()));
        if (brain.taskState() == HiredWorkerTaskState.MOVING_TO_TARGET || brain.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA)
            return new SettlementWorkStatus("workforce.pathing", "");
        if (brain.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE || brain.taskState() == HiredWorkerTaskState.DEPOSITING)
            return new SettlementWorkStatus("workforce.depositing", "");
        if (brain.status().equals("interaction.work.status.returning_bounds")) return new SettlementWorkStatus("workforce.pathing", "");
        if (brain.status().equals("interaction.work.hauling.no_demand")) return new SettlementWorkStatus("no_demand", "");
        if (brain.status().equals("interaction.work.builder.choose_structure")) return new SettlementWorkStatus("no_construction_project", "");
        if (blocker == WorkBlocker.MISSING_FILTER) return new SettlementWorkStatus("no_filter", "");
        if (blocker == WorkBlocker.MISSING_CRAFTING_TABLE) return new SettlementWorkStatus("no_crafting_table", "");
        if (blocker == WorkBlocker.MISSING_FURNACE) return new SettlementWorkStatus("no_furnace", "");
        if (blocker == WorkBlocker.PROFESSION_REQUIRED) return new SettlementWorkStatus("profession_required", "");
        var facts = HiredWorkerFacts.observe(session, brain);
        String diagnostic = SettlementWorkerDiagnostics.describe(facts);
        if (facts.canBeBlocked() && (facts.blocker().startsWith("temporary_task_")
                || facts.blocker().startsWith("fishing_") || facts.blocker().startsWith("transfer_"))) {
            return new SettlementWorkStatus(facts.blocker(), diagnostic);
        }
        String status = !diagnostic.isBlank() && (brain.taskState() == HiredWorkerTaskState.SELECTING_TARGET
                || facts.status() == WorkerStatus.WAITING
                || facts.status() == WorkerStatus.UNKNOWN)
                ? "needs_attention" : "workforce." + facts.status().name().toLowerCase(Locale.ROOT);
        return new SettlementWorkStatus(status, diagnostic);
    }
}
