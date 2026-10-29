package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.client.settlement.SettlementWallDraft;
import com.jvn.villagerretaliation.network.SettlementWallRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWallRequestPayload.Action;
import com.jvn.villagerretaliation.network.SettlementWallResultPayload.Status;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_wall")
@PrefixGameTestTemplate(false)
public final class SettlementWoodWallGameTests {
    private SettlementWoodWallGameTests() {}

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void woodenDesignsHaveStaggeredUprightLogsAndDistinctLowBoards(GameTestHelper helper) {
        var palisade = SettlementWallTemplates.get("palisade_span");
        var heights = new java.util.HashSet<Integer>();
        for (int x = 0; x < palisade.size().getX(); x++) {
            int column = x;
            var logs = palisade.cells().stream().filter(cell -> cell.position().getX() == column && cell.state().is(Blocks.OAK_LOG)).toList();
            int height = logs.stream().mapToInt(cell -> cell.position().getY()).max().orElseThrow() + 1;
            heights.add(height);
            helper.assertTrue(logs.stream().allMatch(cell -> cell.state().getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y),
                    "palisade logs stand vertically");
            helper.assertTrue(logs.stream().anyMatch(cell -> cell.position().getY() == height - 1 && cell.position().getZ() == column % 2),
                    "the visible log tops stagger diagonally");
            for (int y = 0; y < 2; y++) {
                var center = new BlockPos(x, y, 1);
                helper.assertTrue(logs.stream().anyMatch(cell -> cell.position().equals(center)), "the staggered face has a continuous barrier behind it");
            }
        }
        helper.assertValueEqual(heights, java.util.Set.of(3, 4), "log heights vary by one block");
        helper.assertValueEqual(palisade.cells().stream().filter(cell -> cell.state().is(Blocks.OAK_FENCE)).count(), 2L, "fence tips are occasional");
        var plank = SettlementWallTemplates.get("plank_span");
        helper.assertTrue(plank.cells().stream().anyMatch(cell -> cell.state().is(Blocks.OAK_PLANKS)), "second type uses boards");
        helper.assertTrue(plank.cells().stream().anyMatch(cell -> cell.state().is(Blocks.OAK_SLAB)), "second type has a low slab cap");
        helper.assertTrue(plank.cells().stream().filter(cell -> !cell.state().isAir()).allMatch(cell -> cell.position().getY() < 3),
                "the boarded span is lower than the palisade");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void woodenCostsUseOnlyLogsAndHonorExplicitStoneTowers(GameTestHelper helper) {
        var nodes = List.of(BlockPos.ZERO, new BlockPos(18, 0, 0));
        var palisade = new SettlementWall(SettlementWall.Type.PALISADE, nodes, false, 0);
        var plank = new SettlementWall(SettlementWall.Type.PLANK, nodes, false, 0);
        helper.assertValueEqual(cost(palisade), new SettlementWallMaterials.Cost(0, 13), "two posts plus seventeen half-log span columns");
        helper.assertValueEqual(cost(plank), new SettlementWallMaterials.Cost(0, 7), "lower boards are the cheapest type");
        var mixed = new SettlementWall(palisade.type(), nodes, false, 0, List.of(SettlementWall.Tower.LOW_STONE, SettlementWall.Tower.DEFAULT));
        helper.assertValueEqual(cost(mixed), new SettlementWallMaterials.Cost(16, 10), "an explicitly selected stone tower still requires stone");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void spanEditsKeepSmallJunctionsAndLegacyStoneFootprints(GameTestHelper helper) {
        for (var type : SettlementWall.Type.values()) {
            var wall = new SettlementWall(type, List.of(BlockPos.ZERO, new BlockPos(18, 0, 0)), false, 0,
                    List.of(SettlementWall.Tower.DEFAULT, SettlementWall.Tower.DEFAULT), SettlementWall.Part.SPAN);
            var draft = SettlementWallDraft.edit(wall);
            draft.type(type == SettlementWall.Type.PALISADE ? SettlementWall.Type.LOW_STONE : SettlementWall.Type.PALISADE);
            for (var node : wall.nodes()) helper.assertValueEqual(draft.wall().towerAt(node), wall.towerAt(node),
                    "changing a span does not silently resize its neighboring junctions");
            draft.undo();
            helper.assertValueEqual(draft.wall().type(), type, "span material change remains undoable");
        }
        var wall = new SettlementWall(SettlementWall.Type.PALISADE, List.of(BlockPos.ZERO, new BlockPos(18, 0, 0)), false, 0);
        var pieces = SettlementWallGeometry.pieces(wall);
        var span = pieces.stream().filter(piece -> piece.part() == SettlementWall.Part.SPAN).findFirst().orElseThrow();
        helper.assertValueEqual(span.nodeTowers(), List.of(SettlementWall.Tower.PALISADE, SettlementWall.Tower.PALISADE),
                "saved spans remember concrete post choices");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void woodenPlacementAndSpanUpgradeWorkFromLogOnlyStorage(GameTestHelper helper) {
        var level = helper.getLevel();
        var base = helper.absolutePos(new BlockPos(5, 120, 5));
        for (var pos : BlockPos.betweenClosed(base.offset(-4, -1, -5), base.offset(23, 10, 5)))
            level.setBlock(pos, pos.getY() < base.getY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        var town = SettlementWallMaterialsGameTests.town(level, base);
        var depot = SettlementWallMaterialsGameTests.supply(level, town, base.north(8));
        depot.clearContent();
        depot.setItem(0, new ItemStack(Items.BIRCH_LOG, 64));
        SettlementTestPlots.planSupplies(level, town);
        var wall = new SettlementWall(SettlementWall.Type.PALISADE, List.of(base, base.east(18)), false, 0);
        var id = UUID.randomUUID();
        var session = new SettlementWallSession();
        var preview = session.request(level, id, true, pos -> true, new SettlementWallRequestPayload(id, 1, Action.PREVIEW, id, wall), town.id());
        helper.assertTrue(preview.message().contains("Logs 13/13") && !preview.message().contains("Stone"), "quote is log-only");
        helper.assertValueEqual(session.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 1, Action.CONFIRM, preview.token(), null), town.id()).status(), Status.APPLIED, "logs pay for placement");
        helper.assertValueEqual(depot.count(SettlementWallMaterials::logs), 51, "only the quoted logs are removed");
        helper.assertTrue(level.getBlockState(base).is(Blocks.OAK_LOG), "accepted log species do not change the authored oak appearance");
        var editId = UUID.randomUUID();
        var edit = new SettlementWallSession();
        var selected = edit.request(level, editId, true, pos -> true, new SettlementWallRequestPayload(editId, 1, Action.SELECT_UPGRADE,
                editId, null, SettlementWallRequestPayload.NONE, base.east(8)), town.id());
        helper.assertValueEqual(selected.status(), Status.SELECTED, "new wooden spans retain their identity");
        var draft = SettlementWallDraft.edit(selected.selected());
        draft.type(SettlementWall.Type.PLANK);
        var changed = edit.request(level, editId, true, pos -> true, new SettlementWallRequestPayload(editId, 2, Action.PREVIEW,
                editId, draft.wall(), selected.upgrade(), BlockPos.ZERO), town.id());
        helper.assertValueEqual(changed.status(), Status.PREVIEW, "boarded replacement keeps its palisade junctions: " + changed.message());
        helper.assertValueEqual(edit.request(level, editId, true, pos -> true,
                new SettlementWallRequestPayload(editId, 2, Action.CONFIRM, changed.token(), null), town.id()).status(), Status.APPLIED, "wooden span changes apply");
        helper.assertValueEqual(depot.count(SettlementWallMaterials::logs), 51, "cheaper replacement does not refund logs");
        helper.assertTrue(level.getBlockState(base).is(Blocks.OAK_LOG), "neighboring posts remain intact");
        helper.succeed();
    }

    private static SettlementWallMaterials.Cost cost(SettlementWall wall) {
        return SettlementWallMaterials.cost(wall, new SettlementWallTerrain.Preview(SettlementWallGeometry.build(wall).cells().stream()
                .map(cell -> new SettlementWallTerrain.Cell(cell.position(), cell.state(), Blocks.AIR.defaultBlockState(), null,
                        SettlementWallTerrain.Kind.PLACE)).toList(), ""), null);
    }
}
