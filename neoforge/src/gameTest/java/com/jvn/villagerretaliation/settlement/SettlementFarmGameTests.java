package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.client.settlement.SettlementFarmEditor;
import com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.SettlementFarmRequestPayload;
import com.jvn.villagerretaliation.network.SettlementFarmRequestPayload.Action;
import com.jvn.villagerretaliation.network.SettlementFarmResultPayload;
import com.jvn.villagerretaliation.network.SettlementFarmResultPayload.Status;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_farm")
@PrefixGameTestTemplate(false)
public final class SettlementFarmGameTests {
    private SettlementFarmGameTests() {}

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void irrigationOptimizesRectanglesAndCoversIrregularElevations(GameTestHelper helper) {
        for (int width : List.of(3, 9, 10, 18, 27, 64)) {
            for (int depth : List.of(3, 9, 18, 27, 64)) {
                var soil = new ArrayList<BlockPos>();
                for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) soil.add(new BlockPos(x - 33, 70, z - 20));
                var water = SettlementFarmIrrigation.plan(soil, soil);
                helper.assertTrue(water != null, "rectangle can be irrigated");
                helper.assertValueEqual(water.size(), Math.ceilDiv(width, 9) * Math.ceilDiv(depth, 9), "rectangles use optimal spacing");
                helper.assertTrue(soil.stream().allMatch(pos -> water.stream().anyMatch(source -> SettlementFarmIrrigation.reaches(source, pos))),
                        "every soil cell is covered");
                helper.assertValueEqual(SettlementFarmIrrigation.plan(soil, soil), water, "preview is deterministic");
            }
        }
        var soil = new ArrayList<BlockPos>();
        for (int x = 0; x < 20; x++) for (int z = 0; z < 12; z++)
            if ((x + z) % 5 != 0) soil.add(new BlockPos(x, x < 10 ? 70 : 72, z));
        var water = SettlementFarmIrrigation.plan(soil, soil);
        helper.assertTrue(water != null && soil.stream().allMatch(pos -> water.stream().anyMatch(source -> SettlementFarmIrrigation.reaches(source, pos))),
                "obstacles and terraces still receive full coverage");
        helper.assertFalse(SettlementFarmIrrigation.reaches(new BlockPos(0, 70, 0), new BlockPos(0, 71, 0)), "lower water cannot hydrate higher soil");
        helper.assertTrue(SettlementFarmIrrigation.reaches(new BlockPos(0, 71, 0), new BlockPos(4, 70, 4)), "diagonal range and upper water match Minecraft");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void terrainSkipsStoneAndPlantsAndReusesWater(GameTestHelper helper) {
        var base = ground(helper, 9, 9);
        var level = helper.getLevel();
        level.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(base.east(), Blocks.PODZOL.defaultBlockState(), 2);
        level.setBlock(base.south(), Blocks.MYCELIUM.defaultBlockState(), 2);
        level.setBlock(base.east(2).above(), Blocks.POPPY.defaultBlockState(), 2);
        level.setBlock(base.east(3).above(), Blocks.SHORT_GRASS.defaultBlockState(), 2);
        var farm = new SettlementFarm(base, base.offset(8, 0, 8));
        var preview = SettlementFarmTerrain.preview(level, farm, pos -> true);
        helper.assertTrue(preview.error().isEmpty(), preview.error());
        helper.assertValueEqual(preview.count(SettlementFarmTerrain.Kind.SKIPPED), 4L, "only tillable and unobstructed soil is selected");
        helper.assertValueEqual(preview.count(SettlementFarmTerrain.Kind.WATER), 1L, "one center source serves a nine-by-nine field");
        helper.assertTrue(level.getBlockState(base.offset(4, 0, 4)).is(Blocks.DIRT), "preview never edits terrain");
        level.setBlock(base.offset(4, 0, 4), Blocks.WATER.defaultBlockState(), 2);
        var supplied = SettlementFarmTerrain.preview(level, farm, pos -> true);
        helper.assertValueEqual(supplied.count(SettlementFarmTerrain.Kind.WATER), 0L, "existing water is free irrigation");
        helper.assertFalse(SettlementFarmTerrain.preview(level, farm, pos -> pos.getX() != base.getX()).error().isEmpty(), "ownership is enforced");
        level.setBlock(base.offset(4, 0, 4), Blocks.DIRT.defaultBlockState(), 2);
        for (var pos : BlockPos.betweenClosed(base.below(), base.offset(8, -1, 8))) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        helper.assertFalse(SettlementFarmTerrain.preview(level, farm, pos -> true).error().isEmpty(), "leaking holes cannot be approved");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void previewPacketsAndResizingRejectStaleResults(GameTestHelper helper) {
        var base = ground(helper, 18, 9);
        long[] now = {0};
        var editor = new SettlementFarmEditor(() -> now[0]);
        var session = UUID.randomUUID();
        var requests = new ArrayList<SettlementFarmRequestPayload>();
        editor.begin();
        editor.click(base, point -> new double[]{point.x, point.z}, 0, 0);
        editor.hover(base.offset(8, 0, 8));
        editor.click(base.offset(8, 0, 8), point -> new double[]{point.x, point.z}, 0, 0);
        now[0] = 200;
        editor.tick(helper.getLevel(), session, pos -> true, requests::add);
        var request = requests.getLast();
        helper.assertValueEqual(editor.cells().stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.WATER).count(), 1L, "small preview has one source");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementFarmRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(SettlementFarmRequestPayload.STREAM_CODEC.decode(buffer), request, "request round trip preserves the area");
        } finally { buffer.release(); }
        var accepted = new SettlementFarmResultPayload(session, request.revision(), UUID.randomUUID(), Status.PREVIEW, editor.cells(), "ready");
        editor.accept(accepted);
        helper.assertTrue(editor.canConfirm(), "current review can be confirmed");
        editor.click(base.offset(17, 0, 8), point -> new double[]{point.x, point.z}, base.getX() + 17, base.getZ() + 8);
        editor.release();
        now[0] = 400;
        editor.tick(helper.getLevel(), session, pos -> true, requests::add);
        helper.assertValueEqual(editor.cells().stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.WATER).count(), 2L, "resizing reoptimizes irrigation");
        editor.accept(accepted);
        helper.assertFalse(editor.canConfirm(), "old review cannot approve resized farmland");
        editor.cancel(session, requests::add);
        helper.assertFalse(editor.active(), "cancelling clears the draft");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm", timeoutTicks = 200)
    public static void farmerRequiresSuppliesResumesAndFinishesWithoutReplacingStone(GameTestHelper helper) {
        var base = ground(helper, 4, 4);
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FarmOwner"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), base, "");
        var town = SettlementSavedData.get(level).establish(registry, id, owner.getUUID(), base, level.getGameTime()).orElseThrow();
        SettlementTestPlots.claim(level, town, base, base.offset(3, 3, 3));
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Farm", base, base.offset(3, 3, 3), 5, true);
        helper.assertTrue(town.putArea(owner.getUUID(), area), "farm workplace is registered");
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(base.above().getBottomCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        level.addFreshEntity(villager);
        SettlementWorkforceService.reconcile(level, villager);
        var inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        var state = HiredWorkStateStore.state(villager);
        state.putBoolean("AutoDepositOutputs", false);
        state.putBoolean("UseAssignedStorageForSupplies", false);
        var session = new SettlementFarmSession();
        var sessionId = UUID.randomUUID();
        var farm = new SettlementFarm(base, base.offset(3, 0, 3));
        try {
            var review = session.request(owner, town, sessionId, true, pos -> true,
                    new SettlementFarmRequestPayload(sessionId, 1, Action.PREVIEW, sessionId, farm));
            helper.assertTrue(review.message().contains("water buckets"), "missing materials are visible");
            var missing = session.request(owner, town, sessionId, true, pos -> true,
                    new SettlementFarmRequestPayload(sessionId, 1, Action.CONFIRM, review.token(), null));
            helper.assertValueEqual(missing.status(), Status.PREVIEW, "no water means no work is assigned");
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "shortage never starts partial work");
            inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.WATER_BUCKET));
            var confirm = new SettlementFarmRequestPayload(sessionId, 1, Action.CONFIRM, missing.token(), null);
            helper.assertValueEqual(session.request(owner, town, sessionId, false, pos -> true, confirm).status(), Status.REJECTED, "permissions are rechecked");
            level.setBlock(base.offset(3, 0, 3), Blocks.COARSE_DIRT.defaultBlockState(), 2);
            var refreshed = session.request(owner, town, sessionId, true, pos -> true, confirm);
            helper.assertValueEqual(refreshed.status(), Status.PREVIEW, "changed terrain requires another review");
            helper.assertTrue(refreshed.message().contains("Terrain changed"), "terrain change is explained");
            confirm = new SettlementFarmRequestPayload(sessionId, 1, Action.CONFIRM, refreshed.token(), null);
            var applied = session.request(owner, town, sessionId, true, pos -> true, confirm);
            helper.assertValueEqual(applied.status(), Status.APPLIED, applied.message());
            helper.assertValueEqual(session.request(owner, town, sessionId, true, pos -> true, confirm), applied, "duplicate confirmation does not queue twice");
            var next = SettlementFarmWork.next(level, state);
            helper.assertTrue(next.water(), "water is placed before tilling");
            var waterPos = next.position();
            var changed = review.cells().stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.TILL).findFirst().orElseThrow().position();
            level.setBlock(changed, Blocks.STONE.defaultBlockState(), 2);
            int iterations = 0;
            while (HiredTemporaryWorkService.queue(state) != null && iterations++ < 100) {
                var step = SettlementFarmWork.next(level, state);
                helper.assertTrue(step.error().isEmpty(), step.error());
                if (step.position() != null) villager.setPos(step.position().above().getBottomCenter());
                var work = HiredWorkSession.active(level, villager);
                var result = work.worker().tick(level, villager, owner, work.context());
                HiredTemporaryWorkService.completed(level, villager, work, result);
                if (iterations == 1) {
                    helper.assertTrue(level.getBlockState(waterPos).is(Blocks.WATER), "farmer places a water source");
                    helper.assertTrue(inventory.findSupply(stack -> stack.is(Items.WATER_BUCKET)).isEmpty(), "water bucket is consumed");
                    var snapshot = new CompoundTag();
                    HiredTemporaryWorkService.copyState(state, snapshot);
                    helper.assertTrue(snapshot.contains(SettlementFarmWork.TAG), "handoff includes the plan and cursor");
                    state.put(SettlementFarmWork.TAG, snapshot.getCompound(SettlementFarmWork.TAG).copy());
                    HiredWorkSession.invalidate(villager);
                }
            }
            helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "the complete area finishes and releases the farmer");
            helper.assertTrue(level.getBlockState(changed).is(Blocks.STONE), "changed stone is skipped");
            long tilled = review.cells().stream().filter(cell -> level.getBlockState(cell.position()).is(Blocks.FARMLAND)).count();
            helper.assertValueEqual(tilled, 14L, "all remaining tillable soil becomes farmland");
            helper.assertTrue(inventory.hasOutput(stack -> stack.is(Items.BUCKET)), "empty bucket is returned");
            helper.assertFalse(state.contains(SettlementFarmWork.TAG), "completed job state is cleared");

            for (var pos : BlockPos.betweenClosed(base, base.offset(3, 0, 3))) level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
            var fresh = SettlementFarmTerrain.preview(level, farm, pos -> true);
            var depot = SettlementWallMaterialsGameTests.supply(level, town, base.east(6));
            depot.clearContent();
            depot.setItem(0, new ItemStack(Items.WATER_BUCKET));
            state.putBoolean("UseAssignedStorageForSupplies", true);
            SettlementDepotService.synchronizeStorage(level, villager, town);
            SettlementTestPlots.planSupplies(level, town);
            helper.assertTrue(SettlementFarmWork.worker(level, town, fresh).worker() == villager, "assigned storage can supply the water quote");
            town.configureDepot(owner.getUUID(), depot.id(), SettlementDepotPurpose.OUTPUT, 5);
            SettlementTestPlots.planSupplies(level, town);
            helper.assertTrue(SettlementFarmWork.worker(level, town, fresh).worker() == null, "output-only water cannot fund farmland");
            town.configureDepot(owner.getUUID(), depot.id(), SettlementDepotPurpose.MATERIALS, 5);
            SettlementTestPlots.planSupplies(level, town);
            helper.assertTrue(SettlementFarmWork.start(owner, town, fresh).isEmpty(), "farmer can start with water in storage");
            villager.setPos(depot.position().south().getBottomCenter());
            var collection = HiredWorkSession.active(level, villager);
            collection.worker().tick(level, villager, owner, collection.context());
            helper.assertTrue(!inventory.findSupply(stack -> stack.is(Items.WATER_BUCKET)).isEmpty(), "farmer collects water at storage");
            helper.assertValueEqual(depot.count(stack -> stack.is(Items.WATER_BUCKET)), 0, "collection removes one real bucket");
            helper.assertTrue(HiredTemporaryWorkService.submit(owner, villager, null, false).isEmpty(), "farmland can be cancelled through the task queue");
            helper.assertFalse(state.contains(SettlementFarmWork.TAG), "cancellation clears the saved plan");
            helper.assertTrue(!inventory.findSupply(stack -> stack.is(Items.WATER_BUCKET)).isEmpty(), "cancellation retains unused water");
        } finally { villager.discard(); }
        helper.succeed();
    }

    private static BlockPos ground(GameTestHelper helper, int width, int depth) {
        var base = helper.absolutePos(new BlockPos(4, 40, 4));
        for (var pos : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(width, 4, depth)))
            helper.getLevel().setBlock(pos, pos.getY() <= base.getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return base;
    }
}
