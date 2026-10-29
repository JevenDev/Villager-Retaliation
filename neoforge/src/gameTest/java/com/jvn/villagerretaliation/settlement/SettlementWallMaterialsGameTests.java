package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.network.SettlementWallRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWallRequestPayload.Action;
import com.jvn.villagerretaliation.network.SettlementWallResultPayload.Status;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_wall")
@PrefixGameTestTemplate(false)
public final class SettlementWallMaterialsGameTests {
    private SettlementWallMaterialsGameTests() {}

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void fixedTowerAndSpanPricesScaleWithFoundationsAndSkips(GameTestHelper helper) {
        var base = new BlockPos(0, 80, 0);
        var wall = new SettlementWall(SettlementWall.Type.LOW_STONE, List.of(base, base.east(18)), false, 0);
        var cells = SettlementWallGeometry.build(wall).cells().stream().map(cell -> new SettlementWallTerrain.Cell(
                cell.position(), cell.state(), Blocks.AIR.defaultBlockState(), null, SettlementWallTerrain.Kind.PLACE)).toList();
        var preview = new SettlementWallTerrain.Preview(cells, "");
        helper.assertValueEqual(SettlementWallMaterials.cost(wall, preview, null), new SettlementWallMaterials.Cost(84, 0),
                "two short towers plus thirteen exposed span columns");
        var tall = new SettlementWall(SettlementWall.Type.TALL_STONE, wall.nodes(), false, 0);
        helper.assertValueEqual(cost(tall), new SettlementWallMaterials.Cost(100, 0), "tall towers use their fixed rate");
        var timber = new SettlementWall(SettlementWall.Type.TIMBER, wall.nodes(), false, 0);
        helper.assertValueEqual(cost(timber), new SettlementWallMaterials.Cost(126, 7), "timber spans require stone and any logs");
        var extended = new java.util.ArrayList<>(cells);
        extended.add(new SettlementWallTerrain.Cell(base.below(), Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), null,
                SettlementWallTerrain.Kind.PLACE));
        helper.assertTrue(SettlementWallMaterials.cost(wall, new SettlementWallTerrain.Preview(extended, ""), null).stone() > 84,
                "downward support increases the average price");
        helper.assertValueEqual(SettlementWallMaterials.cost(wall, new SettlementWallTerrain.Preview(cells.stream().map(cell ->
                new SettlementWallTerrain.Cell(cell.position(), cell.target(), cell.before(), null, SettlementWallTerrain.Kind.SKIPPED)).toList(), ""), null),
                SettlementWallMaterials.Cost.ZERO, "overlapping terrain is not charged");
        helper.assertTrue(SettlementWallMaterials.logs(new ItemStack(Items.BIRCH_LOG))
                && SettlementWallMaterials.logs(new ItemStack(Items.STRIPPED_SPRUCE_LOG)), "different logs share one pool");
        helper.assertFalse(SettlementWallMaterials.logs(new ItemStack(Items.OAK_PLANKS)), "planks are not accepted as logs");
        helper.assertFalse(SettlementWallMaterials.stone(new ItemStack(Items.IRON_ORE)), "ores are not stone currency");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void upgradesChargeOnlyAdditionalValueAndMissingBlocks(GameTestHelper helper) {
        var low = new SettlementWall(SettlementWall.Type.LOW_STONE, List.of(BlockPos.ZERO, new BlockPos(18, 0, 0)), false, 0,
                List.of(SettlementWall.Tower.DEFAULT, SettlementWall.Tower.NONE), SettlementWall.Part.TOWER);
        var high = new SettlementWall(SettlementWall.Type.TALL_STONE, low.nodes(), false, 0, low.nodeTowers(), low.part());
        var original = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        SettlementWallGeometry.build(low).cells().stream().filter(cell -> !cell.state().isAir()).forEach(cell -> original.put(cell.position(), cell.state()));
        var entry = new SettlementWallsSavedData.Entry(UUID.randomUUID(), UUID.randomUUID(),
                com.jvn.villagerretaliation.allegiance.VillageAllegianceId.random(), helper.getLevel().dimension().location(), low, original);
        var cells = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        SettlementWallGeometry.build(high).cells().forEach(cell -> cells.put(cell.position(), cell.state()));
        original.keySet().forEach(pos -> cells.putIfAbsent(pos, Blocks.AIR.defaultBlockState()));
        var preview = new SettlementWallTerrain.Preview(cells.entrySet().stream().map(cell -> new SettlementWallTerrain.Cell(cell.getKey(),
                cell.getValue(), original.getOrDefault(cell.getKey(), Blocks.AIR.defaultBlockState()), null, SettlementWallTerrain.Kind.PLACE)).toList(), "");
        helper.assertValueEqual(SettlementWallMaterials.cost(high, preview, entry), new SettlementWallMaterials.Cost(8, 0),
                "16-stone tower credits its intact value toward the 24-stone tower");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void confirmationPaysOnceAndShortagesNeverPartiallyCharge(GameTestHelper helper) {
        var level = helper.getLevel();
        var base = helper.absolutePos(new BlockPos(5, 80, 5));
        for (var pos : BlockPos.betweenClosed(base.offset(-4, -1, -5), base.offset(23, 12, 5)))
            level.setBlock(pos, pos.getY() < base.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        var town = town(level, base);
        var depot = supply(level, town, base.north(8));
        depot.clearContent();
        depot.setItem(0, new ItemStack(Items.STONE, 64));
        depot.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        depot.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
        SettlementTestPlots.planSupplies(level, town);
        var wall = new SettlementWall(SettlementWall.Type.LOW_STONE, List.of(base, base.east(18)), false, 0);
        var session = new SettlementWallSession();
        var id = UUID.randomUUID();
        var preview = session.request(level, id, true, pos -> true, new SettlementWallRequestPayload(id, 1, Action.PREVIEW, id, wall), town.id());
        helper.assertTrue(preview.message().contains("84/84"), "preview includes the funded material quote");
        depot.setItem(1, ItemStack.EMPTY);
        depot.setItem(2, ItemStack.EMPTY);
        SettlementTestPlots.planSupplies(level, town);
        var confirm = new SettlementWallRequestPayload(id, 1, Action.CONFIRM, preview.token(), null);
        var missing = session.request(level, id, true, pos -> true, confirm, town.id());
        helper.assertValueEqual(missing.status(), Status.PREVIEW, "insufficient stock keeps the draft available");
        helper.assertValueEqual(depot.getItem(0).getCount(), 64, "shortage does not partially charge");
        helper.assertTrue(level.getBlockState(base).isAir(), "shortage places nothing");
        var cancelled = new SettlementWallSession();
        var cancelledId = UUID.randomUUID();
        cancelled.request(level, cancelledId, true, pos -> true, new SettlementWallRequestPayload(cancelledId, 1, Action.PREVIEW, cancelledId, wall), town.id());
        cancelled.request(level, cancelledId, true, pos -> true, new SettlementWallRequestPayload(cancelledId, 2, Action.CANCEL, cancelledId, null), town.id());
        helper.assertValueEqual(depot.getItem(0).getCount(), 64, "preview and cancel never reserve or consume materials");
        depot.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        depot.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
        SettlementTestPlots.planSupplies(level, town);
        confirm = new SettlementWallRequestPayload(id, 1, Action.CONFIRM, missing.token(), null);
        helper.assertValueEqual(session.request(level, id, false, pos -> true, confirm, town.id()).status(), Status.REJECTED, "permission is required before payment");
        helper.assertValueEqual(depot.count(SettlementWallMaterials::stone), 192, "permission rejection leaves materials untouched");
        var paid = session.request(level, id, true, pos -> true, confirm, town.id());
        helper.assertValueEqual(paid.status(), Status.APPLIED, "mixed stone and cobble pay for instant placement: " + paid.message());
        helper.assertValueEqual(depot.count(SettlementWallMaterials::stone), 108, "exactly the quoted amount is spent");
        helper.assertValueEqual(session.request(level, id, true, pos -> true, confirm, town.id()), paid, "duplicate returns the original receipt");
        helper.assertValueEqual(depot.count(SettlementWallMaterials::stone), 108, "duplicate does not charge again");
        helper.assertTrue(town.projects().isEmpty() && town.workers().isEmpty(), "instant walls require neither a project nor a worker");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void outputOnlyAndUnobservedStorageCannotPay(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = town(level, helper.absolutePos(new BlockPos(5, 140, 5)));
        var depot = supply(level, town, town.center().north(8));
        var timber = SettlementWallMaterials.prepare(level, town, UUID.randomUUID(), new SettlementWallMaterials.Cost(10, 1025));
        helper.assertFalse(timber.pay(), "log shortages cannot partially spend the stone pool");
        helper.assertValueEqual(depot.count(SettlementWallMaterials::stone), 2048, "both pools are checked before any debit");
        town.configureDepot(town.leader(), depot.id(), SettlementDepotPurpose.OUTPUT, 5);
        SettlementTestPlots.planSupplies(level, town);
        var payment = SettlementWallMaterials.prepare(level, town, UUID.randomUUID(), new SettlementWallMaterials.Cost(10, 0));
        helper.assertFalse(payment.pay(), "output-only storage is protected");
        helper.assertValueEqual(depot.count(SettlementWallMaterials::stone), 2048, "protected stock remains intact");
        town.configureDepot(town.leader(), depot.id(), SettlementDepotPurpose.MATERIALS, 5);
        town.removeDepot(depot.id());
        var unobserved = town.registerDepot(town.leader(), depot.area(), depot.position(), SettlementDepotPurpose.MATERIALS);
        unobserved.setItem(0, new ItemStack(Items.STONE, 64));
        SettlementTestPlots.planSupplies(level, town);
        helper.assertFalse(SettlementWallMaterials.prepare(level, town, UUID.randomUUID(), new SettlementWallMaterials.Cost(10, 0)).pay(),
                "unobserved filters cannot be bypassed");
        helper.succeed();
    }

    private static SettlementWallMaterials.Cost cost(SettlementWall wall) {
        return SettlementWallMaterials.cost(wall, new SettlementWallTerrain.Preview(SettlementWallGeometry.build(wall).cells().stream()
                .map(cell -> new SettlementWallTerrain.Cell(cell.position(), cell.state(), Blocks.AIR.defaultBlockState(), null,
                        SettlementWallTerrain.Kind.PLACE)).toList(), ""), null);
    }

    static SettlementRecord town(ServerLevel level, BlockPos position) {
        var data = SettlementSavedData.get(level);
        return data.at(level.dimension().location(), position).orElseGet(() -> {
            var registry = VillageAllegianceRegistrySavedData.get(level);
            var id = registry.create(level.getGameTime(), level.dimension().location(), position, "");
            return data.establish(registry, id, UUID.randomUUID(), position, level.getGameTime()).orElseThrow();
        });
    }

    public static Runnable supplyForClient(ServerLevel level, SettlementRecord town, BlockPos position) {
        var original = town.depots().values().stream().findFirst().orElse(null);
        var contents = new java.util.ArrayList<ItemStack>();
        if (original != null) for (int slot = 0; slot < original.getContainerSize(); slot++) contents.add(original.getItem(slot).copy());
        var purpose = original == null ? SettlementDepotPurpose.MATERIALS : original.purpose();
        int priority = original == null ? 5 : original.priority();
        var block = level.getBlockState(original == null ? position : original.position());
        var depot = supply(level, town, position);
        return () -> {
            if (original == null) {
                town.removeDepot(depot.id());
                town.removeArea(town.leader(), depot.area());
                level.setBlockAndUpdate(position, block);
            } else {
                depot.clearContent();
                for (int slot = 0; slot < contents.size(); slot++) depot.setItem(slot, contents.get(slot));
                town.configureDepot(town.leader(), depot.id(), purpose, priority);
            }
        };
    }

    public static SettlementDepot supply(ServerLevel level, SettlementRecord town, BlockPos position) {
        var depot = town.depots().values().stream().findFirst().orElse(null);
        if (depot == null) {
            SettlementTestPlots.claim(level, town, position, position);
            var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Wall materials", position, position, 5, true);
            if (!town.putArea(town.leader(), area)) throw new IllegalStateException("Could not create wall test storage");
            depot = town.registerDepot(town.leader(), area.id(), position, SettlementDepotPurpose.MATERIALS);
            if (depot == null) throw new IllegalStateException("Could not register wall test storage");
        }
        town.configureDepot(town.leader(), depot.id(), SettlementDepotPurpose.MATERIALS, 5);
        SettlementTestPlots.activateDepot(level, town, depot);
        depot.observeRules(List.of());
        depot.clearContent();
        for (int i = 0; i < 32; i++) depot.setItem(i, new ItemStack(i % 2 == 0 ? Items.COBBLESTONE : Items.STONE, 64));
        for (int i = 32; i < 48; i++) depot.setItem(i, new ItemStack(i % 2 == 0 ? Items.OAK_LOG : Items.BIRCH_LOG, 64));
        SettlementTestPlots.planSupplies(level, town);
        return depot;
    }
}
