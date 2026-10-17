package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.WorkBlocker;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("worker_structure")
@PrefixGameTestTemplate(false)
public final class WorkerStateContractGameTests {
    @GameTest(template = "empty")
    public static void blockersSurviveMessageChangesAndLegacySaves(GameTestHelper helper) {
        var state = new CompoundTag();
        state.putString("Status", "interaction.work.craftsman.no_filter");
        helper.assertValueEqual(WorkBlocker.read(state), WorkBlocker.MISSING_FILTER, "old worker saves retain their blocker");
        var result = WorkResult.idle("a.renamed.message").withBlocker(WorkBlocker.MISSING_CRAFTING_TABLE);
        state.putString("Status", result.status());
        result.blocker().save(state);
        helper.assertValueEqual(WorkBlocker.read(state.copy()), WorkBlocker.MISSING_CRAFTING_TABLE, "new blockers survive persistence independently of display keys");
        state.putString("Status", "interaction.work.cooking.no_station");
        helper.assertValueEqual(WorkBlocker.read(state), WorkBlocker.MISSING_CRAFTING_TABLE, "display text cannot change a typed blocker");
        WorkBlocker.NONE.save(state);
        helper.assertFalse(WorkBlocker.read(state).blocksService(), "stale display text cannot block an available service");
        state.putString("WorkerTaskState", "paused_missing_tool");
        helper.assertValueEqual(WorkBlocker.read(state), WorkBlocker.MISSING_TOOL, "equipment task states remain authoritative");
        WorkBlocker.RESTING.save(state);
        helper.assertTrue(WorkBlocker.read(state).preservesService(), "rest retains the last service assessment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void backgroundServiceAvailabilityDoesNotParseMessages(GameTestHelper helper) {
        var saved = new CompoundTag();
        saved.putUUID("Id", UUID.randomUUID());
        saved.putUUID("Area", UUID.randomUUID());
        saved.putString("Role", "CRAFTSMAN");
        var worker = SettlementWorkerState.load(saved);
        helper.assertTrue(worker.supportsService(), "legacy records without readiness remain compatible");
        worker.status("a.renamed.blocked.message", WorkBlocker.MISSING_FILTER);
        helper.assertFalse(worker.supportsService(), "a typed blocker disables the service");
        worker.status("a.renamed.rest.message", WorkBlocker.RESTING);
        helper.assertFalse(SettlementWorkerState.load(worker.save()).supportsService(), "rest and persistence retain the blocker");
        worker.status("no_filter", WorkBlocker.NONE);
        helper.assertTrue(worker.supportsService(), "display text alone cannot disable the service");
        helper.succeed();
    }
}
