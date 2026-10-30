package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredJobSetting;
import com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_farm")
@PrefixGameTestTemplate(false)
public final class SettlementFarmWorkflowGameTests {
    private SettlementFarmWorkflowGameTests() {}

    @GameTest(template = "empty", batch = "settlement_farm", timeoutTicks = 500)
    public static void oneBucketPreparesMultipleSourcesAndWaitsForCrop(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var state = HiredWorkStateStore.state(fixture.villager());
        var inventory = HiredJobInventory.getJobInventory(fixture.villager());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.BUCKET));
        var farm = new SettlementFarm(fixture.base(), fixture.base().offset(17, 0, 8));
        var preview = SettlementFarmTerrain.preview(level, farm, pos -> true);
        helper.assertValueEqual(preview.count(SettlementFarmTerrain.Kind.WATER), 2L, "two sources need repeated trips");
        helper.assertTrue(SettlementFarmWork.start(fixture.owner(), fixture.town(), preview, farm).isEmpty(), "unassigned farmer can start a plot");
        var area = fixture.town().workplace(fixture.town().duties().get(fixture.villager().getUUID()));
        helper.assertValueEqual(area.farm(), farm, "placed geometry is the assigned workplace");
        helper.assertValueEqual(SettlementArea.load(area.save()), area, "plot survives saving");
        var source = fixture.base().offset(20, 0, 12);
        pool(helper, source);
        int initialDamage = inventory.getItem(HiredJobInventory.MAINHAND_SLOT).getDamageValue();
        int[] ticks = {0};
        helper.onEachTick(() -> {
            var step = SettlementFarmWork.next(level, state);
            if (step.position() != null) {
                boolean refill = step.water() && inventory.findSupply(stack -> stack.is(Items.WATER_BUCKET)).isEmpty();
                fixture.villager().setPos((refill ? source.west().above() : step.position().above()).getBottomCenter());
            }
            fixture.villager().setOnGround(true);
            var session = HiredWorkSession.active(level, fixture.villager());
            var result = session.worker().tick(level, fixture.villager(), fixture.owner(), session.context());
            HiredTemporaryWorkService.completed(level, fixture.villager(), session, result);
            if (HiredTemporaryWorkService.queue(state) != null) {
                helper.assertTrue(++ticks[0] < 450, "preparation should finish after repeated refills: " + result.status());
                return;
            }
            helper.assertTrue(preview.cells().stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.TILL)
                    .allMatch(cell -> level.getBlockState(cell.position()).is(Blocks.FARMLAND)), "every planned soil cell is tilled");
            helper.assertTrue(preview.cells().stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.WATER)
                    .allMatch(cell -> level.getBlockState(cell.position()).is(Blocks.WATER)), "all planned water comes from one reusable bucket");
            helper.assertValueEqual(bucketCount(inventory), 1, "no bucket is lost or duplicated");
            helper.assertTrue(SettlementFarmWater.infinite(level, source), "refilling preserves the infinite source");
            helper.assertValueEqual(inventory.getItem(HiredJobInventory.MAINHAND_SLOT).getDamageValue() - initialDamage, 160,
                    "only tilling damages the hoe");
            helper.assertFalse(HiredFarmingOptions.plantCrops(state), "no crop choice leaves prepared land waiting");
            HiredJobSetting.CROP_SELECTION.apply(state, HiredFarmingOptions.Crop.WHEAT.ordinal());
            helper.assertTrue(HiredFarmingOptions.plantCrops(state), "a later crop instruction enables planting");
            fixture.villager().discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void shortagesRetainWaterAndAllowHydratedTilling(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var state = HiredWorkStateStore.state(fixture.villager());
        var inventory = HiredJobInventory.getJobInventory(fixture.villager());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.WATER_BUCKET));
        var preview = SettlementFarmTerrain.preview(level, new SettlementFarm(fixture.base(), fixture.base().offset(17, 0, 8)), pos -> true);
        helper.assertTrue(SettlementFarmWork.start(fixture.owner(), fixture.town(), preview).isEmpty(), "one filled bucket starts a larger job");
        var water = SettlementFarmWork.next(level, state);
        tickAt(helper, fixture, water.position());
        for (int slot : inventory.supplySlots()) if (inventory.getItem(slot).is(Items.BUCKET)) inventory.setItem(slot, ItemStack.EMPTY);
        var till = SettlementFarmWork.next(level, state, false);
        helper.assertTrue(!till.water() && till.error().isEmpty(), "water shortage still exposes irrigated soil work");
        var fragile = new ItemStack(Items.IRON_HOE);
        fragile.setDamageValue(fragile.getMaxDamage() - 1);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, fragile);
        tickAt(helper, fixture, till.position());
        helper.assertTrue(inventory.getItem(HiredJobInventory.MAINHAND_SLOT).isEmpty(), "last durability breaks the hoe");
        till = SettlementFarmWork.next(level, state, false);
        tickAt(helper, fixture, till.position());
        helper.assertFalse(level.getBlockState(till.position()).is(Blocks.FARMLAND), "missing hoe pauses tilling");
        helper.assertTrue(level.getBlockState(water.position()).is(Blocks.WATER), "tool loss retains placed water");
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        tickAt(helper, fixture, till.position());
        helper.assertTrue(level.getBlockState(till.position()).is(Blocks.FARMLAND), "replacement hoe resumes without water supplies");
        var saved = new CompoundTag();
        HiredTemporaryWorkService.copyState(state, saved);
        state.put(SettlementFarmWork.TAG, saved.getCompound(SettlementFarmWork.TAG).copy());
        var next = SettlementFarmWork.next(level, state, false);
        helper.assertFalse(next.position().equals(till.position()), "restored progress does not redo completed soil");
        helper.assertTrue(SettlementFarmWork.next(level, state).water(), "water remains pending and takes priority when available");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void storageCollectionHonorsBucketAllowance(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var state = HiredWorkStateStore.state(fixture.villager());
        var inventory = HiredJobInventory.getJobInventory(fixture.villager());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.BUCKET));
        var preview = SettlementFarmTerrain.preview(level, new SettlementFarm(fixture.base(), fixture.base().offset(8, 0, 8)), pos -> true);
        helper.assertTrue(SettlementFarmWork.start(fixture.owner(), fixture.town(), preview).isEmpty(), "plot starts");
        for (int slot : inventory.supplySlots()) if (inventory.getItem(slot).is(Items.BUCKET)) inventory.setItem(slot, ItemStack.EMPTY);
        var depot = SettlementWallMaterialsGameTests.supply(level, fixture.town(), fixture.base().east(22));
        depot.clearContent();
        depot.setItem(0, new ItemStack(Items.BUCKET, 16));
        state.putBoolean("UseAssignedStorageForSupplies", true);
        HiredJobSetting.FARM_BUCKETS.apply(state, 3);
        SettlementDepotService.synchronizeStorage(level, fixture.villager(), fixture.town());
        SettlementTestPlots.planSupplies(level, fixture.town());
        fixture.villager().setPos(depot.position().south().getBottomCenter());
        var session = HiredWorkSession.active(level, fixture.villager());
        session.worker().tick(level, fixture.villager(), fixture.owner(), session.context());
        helper.assertValueEqual(bucketCount(inventory), 4, "worker collects only its allowance");
        helper.assertValueEqual(depot.count(stack -> stack.is(Items.BUCKET)), 12, "other villagers retain the remaining buckets");
        helper.assertValueEqual(HiredJobSetting.FARM_BUCKETS.read(new CompoundTag()), 0, "default allowance is one bucket");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void infiniteWaterRequiresRegenerationAndSettingsMigrate(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var source = fixture.base().offset(20, 0, 12);
        level.setBlock(source, Blocks.WATER.defaultBlockState(), 2);
        helper.assertFalse(SettlementFarmWater.infinite(level, source), "single irrigation hole cannot refill buckets");
        pool(helper, source);
        helper.assertTrue(SettlementFarmWater.infinite(level, source), "contained two-by-two water is renewable");
        var rule = level.getGameRules().getRule(GameRules.RULE_WATER_SOURCE_CONVERSION);
        boolean original = rule.get();
        try {
            rule.set(false, level.getServer());
            helper.assertFalse(SettlementFarmWater.infinite(level, source), "disabled regeneration forbids automatic refills");
        } finally { rule.set(original, level.getServer()); }
        var settings = SettlementWorkerSettings.capture(HiredVillagerRole.FARMING, new CompoundTag());
        var saved = settings.save();
        saved.putInt("Version", 4);
        saved.getCompound("Options").remove("FARM_BUCKETS");
        saved.putInt("Overrides", saved.getInt("Overrides") & ~(1 << SettlementWorkerSetting.FARM_BUCKETS.ordinal()));
        helper.assertValueEqual(SettlementWorkerSettings.load(saved).options().get(SettlementWorkerSetting.FARM_BUCKETS), 0,
                "old farm settings gain the one-bucket default");
        var unversioned = settings.save();
        unversioned.remove("Version");
        unversioned.remove("Overrides");
        unversioned.remove("Storage");
        unversioned.remove("FishingSpots");
        for (var option : SettlementWorkerSetting.values())
            if (option.ordinal() > SettlementWorkerSetting.GUARD_MODE.ordinal())
                unversioned.getCompound("Options").remove(option.name());
        helper.assertValueEqual(SettlementWorkerSettings.load(unversioned).options().get(SettlementWorkerSetting.FARM_BUCKETS), 0,
                "unversioned settings remain loadable");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void unassignedFarmerCanStartFromSharedStorage(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var state = HiredWorkStateStore.state(fixture.villager());
        state.remove("UseAssignedStorageForSupplies");
        var inventory = HiredJobInventory.getJobInventory(fixture.villager());
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, ItemStack.EMPTY);
        var depot = SettlementWallMaterialsGameTests.supply(level, fixture.town(), fixture.base().east(22));
        depot.clearContent();
        depot.setItem(0, new ItemStack(Items.BUCKET, 16));
        depot.setItem(1, new ItemStack(Items.IRON_HOE));
        var preview = SettlementFarmTerrain.preview(level, new SettlementFarm(fixture.base(), fixture.base().offset(8, 0, 8)), pos -> true);
        helper.assertTrue(SettlementFarmWork.start(fixture.owner(), fixture.town(), preview).isEmpty(),
                "shared supplies qualify an unassigned farmer without a legacy area");
        SettlementTestPlots.planSupplies(level, fixture.town());
        fixture.villager().setPos(depot.position().south().getBottomCenter());
        var session = HiredWorkSession.active(level, fixture.villager());
        session.worker().tick(level, fixture.villager(), fixture.owner(), session.context());
        helper.assertValueEqual(bucketCount(inventory), 1, "first assignment collects the one-bucket default");
        helper.assertValueEqual(depot.count(stack -> stack.is(Items.BUCKET)), 15, "first farmer leaves surplus buckets in storage");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void previewRequiresReadyWorkerAndIrrigatedSoilNeedsNoBucket(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var farm = new SettlementFarm(fixture.base(), fixture.base().offset(8, 0, 8));
        var session = new SettlementFarmSession();
        var id = UUID.randomUUID();
        var empty = new UUID(0, 0);
        var request = new com.jvn.villagerretaliation.network.SettlementFarmRequestPayload(id, 1,
                com.jvn.villagerretaliation.network.SettlementFarmRequestPayload.Action.PREVIEW, empty, farm);
        var rejected = session.request(fixture.owner(), fixture.town(), id, true, pos -> true, request);
        helper.assertValueEqual(rejected.status(), com.jvn.villagerretaliation.network.SettlementFarmResultPayload.Status.REJECTED,
                "missing bucket prevents confirmation");
        helper.assertTrue(rejected.message().contains("bucket"), "preview identifies the missing supply");
        level.setBlock(fixture.base().offset(4, 0, 4), Blocks.WATER.defaultBlockState(), 2);
        request = new com.jvn.villagerretaliation.network.SettlementFarmRequestPayload(id, 2,
                com.jvn.villagerretaliation.network.SettlementFarmRequestPayload.Action.PREVIEW, empty, farm);
        var ready = session.request(fixture.owner(), fixture.town(), id, true, pos -> true, request);
        helper.assertValueEqual(ready.status(), com.jvn.villagerretaliation.network.SettlementFarmResultPayload.Status.PREVIEW,
                "already irrigated soil needs no bucket");
        HiredJobInventory.getJobInventory(fixture.villager()).setItem(HiredJobInventory.MAINHAND_SLOT, ItemStack.EMPTY);
        request = new com.jvn.villagerretaliation.network.SettlementFarmRequestPayload(id, 2,
                com.jvn.villagerretaliation.network.SettlementFarmRequestPayload.Action.CONFIRM, ready.token(), null);
        var changed = session.request(fixture.owner(), fixture.town(), id, true, pos -> true, request);
        helper.assertValueEqual(changed.status(), com.jvn.villagerretaliation.network.SettlementFarmResultPayload.Status.REJECTED,
                "confirmation rechecks tool availability");
        helper.assertTrue(changed.message().contains("hoe"), "changed readiness reports the missing hoe");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void cropMixUsesPlantableCellsInsideTheShape(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var base = fixture.base();
        var shape = new SettlementFarm(SettlementFarm.Shape.POLYGON, List.of(base, base.east(8),
                base.offset(8, 0, 2), base.offset(2, 0, 2), base.offset(2, 0, 8), base.south(8)));
        var plan = new SettlementCropPlan(List.of(50, 50, 0, 0, 0, 0));
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Mixed plot", base, base.offset(8, 2, 8),
                5, true, plan, false, shape);
        helper.assertTrue(fixture.town().putArea(fixture.owner().getUUID(), area), "shaped farm is registered");
        pool(helper, base.offset(3, 0, 3));
        var positions = List.of(base.offset(0, 0, 1), base.offset(1, 0, 1), base.offset(2, 0, 1),
                base.offset(3, 0, 1), base.offset(0, 0, 2), base.offset(1, 0, 2));
        for (var pos : positions) {
            level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 2);
            fixture.town().recordField(SettlementFieldPlot.capture(level, area.id(), pos, level.getChunkAt(pos), level.getGameTime()));
        }
        var outside = base.offset(6, 0, 6);
        level.setBlock(outside, Blocks.FARMLAND.defaultBlockState(), 2);
        fixture.town().recordField(SettlementFieldPlot.capture(level, area.id(), outside, level.getChunkAt(outside), level.getGameTime()));
        var crops = positions.stream().map(pos -> SettlementPlanting.choose(level, fixture.town(), area,
                fixture.town().fields().get(pos), pos, new CompoundTag()).crop()).toList();
        helper.assertValueEqual(crops.stream().filter(crop -> crop == HiredFarmingOptions.Crop.WHEAT).count(), 3L,
                "half of actual planting cells are wheat");
        helper.assertValueEqual(crops.stream().filter(crop -> crop == HiredFarmingOptions.Crop.CARROT).count(), 3L,
                "half of actual planting cells are carrots despite empty bounding-box space");
        fixture.villager().discard();
        helper.succeed();
    }

    private static void tickAt(GameTestHelper helper, Fixture fixture, BlockPos position) {
        fixture.villager().setPos(position.above().getBottomCenter());
        var session = HiredWorkSession.active(helper.getLevel(), fixture.villager());
        session.worker().tick(helper.getLevel(), fixture.villager(), fixture.owner(), session.context());
    }

    private static int bucketCount(HiredJobInventory inventory) {
        return inventory.supplySlots().stream().map(inventory::getItem)
                .filter(stack -> stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET)).mapToInt(ItemStack::getCount).sum();
    }

    private static void pool(GameTestHelper helper, BlockPos source) {
        for (var pos : List.of(source, source.east(), source.south(), source.east().south()))
            helper.getLevel().setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var base = helper.absolutePos(new BlockPos(4, 40, 4));
        for (var pos : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(24, 4, 16)))
            level.setBlock(pos, pos.getY() <= base.getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FarmWorkflow"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), base, "");
        var town = SettlementSavedData.get(level).establish(registry, id, owner.getUUID(), base, level.getGameTime()).orElseThrow();
        SettlementTestPlots.claim(level, town, base.offset(-1, 0, -1), base.offset(24, 4, 16));
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(base.above().getBottomCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
        level.addFreshEntity(villager);
        var inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        var state = HiredWorkStateStore.state(villager);
        state.putBoolean("AutoDepositOutputs", false);
        state.putBoolean("UseAssignedStorageForSupplies", false);
        return new Fixture(owner, town, villager, base);
    }

    private record Fixture(ServerPlayer owner, SettlementRecord town, Villager villager, BlockPos base) {}
}
