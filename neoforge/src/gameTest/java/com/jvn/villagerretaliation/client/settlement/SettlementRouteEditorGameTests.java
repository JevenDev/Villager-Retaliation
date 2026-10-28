package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_view")
@PrefixGameTestTemplate(false)
public final class SettlementRouteEditorGameTests {
    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void saveFailuresPreserveDraftAndTimeoutRequiresReload(GameTestHelper helper) {
        var editor = new SettlementRouteEditor();
        var area = UUID.randomUUID();
        var resident = UUID.randomUUID();
        var original = route();
        editor.begin(area, resident, HiredVillagerRole.COMBAT, null, original);
        helper.assertTrue(editor.inherited(), "workers without overrides start from the default route");
        editor.edit(8);
        var request = editor.submit(false);
        helper.assertTrue(request.area().equals(area) && request.resident().equals(resident)
                && request.expected().equals(original) && request.route().loop() && !original.loop(),
                "saving uses the original effective route and an isolated draft");
        helper.assertTrue(editor.submit(false) == null && !editor.timeout(true), "pending saves cannot be submitted twice");
        helper.assertTrue(!editor.accept(false) && editor.canSubmit() && editor.draft().loop(), "a rejected save keeps the editable draft");
        editor.submit(false);
        helper.assertTrue(editor.timeout(false) && editor.uncertain() && !editor.canSubmit(), "an unconfirmed save requires a fresh snapshot");
        helper.assertTrue(!editor.accept(true) && editor.submit(false) == null, "late results cannot confirm a timed out draft");
        editor.reload();
        helper.assertTrue(editor.reloading() && !editor.canSubmit(), "reload blocks submission until authoritative data arrives");
        var refreshed = request.route();
        editor.begin(area, resident, HiredVillagerRole.COMBAT, refreshed, original);
        var inherit = editor.submit(true);
        helper.assertTrue(!editor.inherited() && inherit.expected().equals(refreshed) && inherit.route() == null,
                "reloading replaces the expected route and inheritance clears the override");
        helper.assertTrue(editor.accept(true), "a confirmed save signals completion");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void invalidDragsRestoreNodesAndToolChangesClearRouteState(GameTestHelper helper) {
        var interaction = new SettlementViewInteraction();
        var editor = interaction.route();
        var original = route();
        for (var mode : SettlementViewInteraction.Mode.values()) {
            if (mode == SettlementViewInteraction.Mode.EDIT_ROUTE) continue;
            interaction.enter(SettlementViewInteraction.Mode.EDIT_ROUTE);
            editor.begin(UUID.randomUUID(), null, HiredVillagerRole.COMBAT, original, null);
            editor.beginDrag(0, 10, 20);
            editor.drag(11, 21, new BlockPos(1, 63, 0));
            editor.drag(12, 22, null);
            helper.assertTrue(!editor.finishDrag(position -> true) && editor.draft().nodes().equals(original.points()),
                    "dropping outside the world restores the original nodes");
            editor.beginDrag(0, 10, 20);
            editor.drag(11, 21, new BlockPos(1, 63, 0));
            helper.assertTrue(!editor.movedSinceDrag(11, 21) && editor.movedSinceDrag(12, 21), "release coordinates track the last drag");
            helper.assertTrue(editor.finishDrag(position -> true) && editor.draft().nodes().getFirst().equals(new BlockPos(1, 64, 0)),
                    "valid drops update the draft");
            var draft = editor.draft();
            var before = draft.nodes();
            editor.beginDrag(0, 11, 21);
            editor.drag(12, 22, new BlockPos(2, 63, 0));
            editor.preview(new BlockPos(3, 64, 0));
            interaction.enter(mode);
            helper.assertTrue(!draft.dragging() && draft.nodes().equals(before), "leaving a tool cancels unfinished movement");
            helper.assertTrue(editor.draft() == null && editor.area() == null && editor.resident() == null
                    && editor.preview() == null && !editor.uncertain() && !editor.reloading(), "every other tool releases route state");
        }
        helper.succeed();
    }

    private static SettlementGuardOrder route() {
        return new SettlementGuardOrder(SettlementGuardOrder.Mode.PATROL, List.of(new BlockPos(0, 64, 0), new BlockPos(8, 64, 0)));
    }
}
