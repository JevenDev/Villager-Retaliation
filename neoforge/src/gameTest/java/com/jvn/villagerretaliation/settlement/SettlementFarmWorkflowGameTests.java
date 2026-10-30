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

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void farmJobsPauseSaveAndCancelWithoutUndoingTerrain(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var inventory = HiredJobInventory.getJobInventory(fixture.villager());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.WATER_BUCKET));
        var farm = new SettlementFarm(fixture.base(), fixture.base().offset(8, 0, 8));
        helper.assertTrue(SettlementFarmWork.start(fixture.owner(), fixture.town(),
                SettlementFarmTerrain.preview(level, farm, pos -> true), farm).isEmpty(), "job starts");
        var job = fixture.town().farmJobs().values().iterator().next();
        var state = HiredWorkStateStore.state(fixture.villager());
        var water = SettlementFarmWork.next(level, state);
        tickAt(helper, fixture, water.position());
        helper.assertValueEqual(job.done(true), 1, "water progress is visible in the persistent job");
        helper.assertTrue(SettlementFarmJobService.configure(fixture.owner(), fixture.town(), job.id(), job.revision(), 8, true,
                SettlementFarmJob.AUTOMATIC), "job pauses");
        var soil = SettlementFarmWork.next(level, state);
        tickAt(helper, fixture, soil.position());
        helper.assertFalse(level.getBlockState(soil.position()).is(Blocks.FARMLAND), "paused job changes no soil");
        helper.assertTrue(SettlementFarmJobService.configure(fixture.owner(), fixture.town(), job.id(), job.revision(), 8, false,
                SettlementFarmJob.AUTOMATIC), "job resumes");
        tickAt(helper, fixture, soil.position());
        helper.assertTrue(level.getBlockState(soil.position()).is(Blocks.FARMLAND), "resuming continues the same soil task");
        helper.assertTrue(SettlementFarmJobService.configure(fixture.owner(), fixture.town(), job.id(), job.revision(), 8, true,
                SettlementFarmJob.AUTOMATIC), "resumed progress can be paused again");
        var saved = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
        var restored = saved.farmJobs().get(job.id());
        helper.assertTrue(restored.paused(), "pause survives town save");
        helper.assertValueEqual(restored.priority(), 8, "priority survives town save");
        helper.assertValueEqual(restored.done(true), 1, "completed water survives town save");
        helper.assertFalse(SettlementFarmJobService.configure(fixture.owner(), fixture.town(), job.id(), job.revision() - 1,
                1, false, SettlementFarmJob.AUTOMATIC), "stale controls cannot overwrite newer settings");
        var cancel = new com.jvn.villagerretaliation.network.SettlementManagementActionPayload.CancelProject(job.id());
        helper.assertTrue(SettlementManagementValidation.selection(fixture.owner(), fixture.town(), cancel).isEmpty(), "project cancellation accepts farm jobs");
        helper.assertTrue(SettlementFarmJobService.cancel(fixture.owner(), fixture.town(), job.id()), "job cancels");
        helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "cancel releases the worker task");
        helper.assertTrue(level.getBlockState(water.position()).is(Blocks.WATER), "cancel retains placed water");
        helper.assertTrue(fixture.town().areas().containsKey(job.area()), "cancel retains the farm and its settings");
        fixture.town().workers().get(fixture.villager().getUUID()).restore(fixture.villager());
        helper.assertTrue(inventory.findSupply(stack -> stack.is(Items.WATER_BUCKET)).isEmpty(), "worker handoff does not restore a spent water bucket");
        helper.assertValueEqual(bucketCount(inventory), 1, "cancellation preserves the real remaining bucket");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void farmQueueUsesPriorityAndReassignsRemainingWork(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var inventory = HiredJobInventory.getJobInventory(fixture.villager());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.WATER_BUCKET));
        var farms = List.of(new SettlementFarm(fixture.base(), fixture.base().offset(8, 0, 3)),
                new SettlementFarm(fixture.base().south(6), fixture.base().offset(8, 0, 9)),
                new SettlementFarm(fixture.base().east(12), fixture.base().offset(20, 0, 3)));
        for (var farm : farms)
            helper.assertTrue(SettlementFarmWork.start(fixture.owner(), fixture.town(),
                    SettlementFarmTerrain.preview(level, farm, pos -> true), farm).isEmpty(), "busy qualified farmers allow queued plots");
        var jobs = List.copyOf(fixture.town().farmJobs().values());
        helper.assertValueEqual(jobs.get(0).status(), SettlementProject.Status.ACTIVE, "first job is active");
        helper.assertValueEqual(jobs.get(1).status(), SettlementProject.Status.PENDING, "second job waits");
        var urgent = jobs.get(2);
        helper.assertTrue(SettlementFarmJobService.configure(fixture.owner(), fixture.town(), urgent.id(), urgent.revision(), 9,
                false, SettlementFarmJob.AUTOMATIC), "pending priority changes");
        helper.assertTrue(SettlementFarmJobService.cancel(fixture.owner(), fixture.town(), jobs.get(0).id()), "first job releases its farmer");
        SettlementFarmJobService.tick(level, fixture.town());
        helper.assertValueEqual(urgent.status(), SettlementProject.Status.ACTIVE, "highest priority pending job starts first");
        helper.assertValueEqual(jobs.get(1).status(), SettlementProject.Status.PENDING, "lower priority job stays queued");
        var state = HiredWorkStateStore.state(fixture.villager());
        var water = SettlementFarmWork.next(level, state);
        tickAt(helper, fixture, water.position());
        var replacement = EntityType.VILLAGER.create(level);
        replacement.setPos(fixture.base().above().getBottomCenter());
        replacement.setNoAi(true);
        replacement.setVillagerData(replacement.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, replacement, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        level.addFreshEntity(replacement);
        HiredJobInventory.getJobInventory(replacement).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        helper.assertTrue(SettlementFarmJobService.configure(fixture.owner(), fixture.town(), urgent.id(), urgent.revision(), 9,
                false, replacement.getUUID()), "a replacement farmer can finish already irrigated work without a bucket");
        helper.assertValueEqual(urgent.worker(), replacement.getUUID(), "replacement owns the active job");
        helper.assertValueEqual(urgent.done(true), 1, "reassignment retains completed irrigation");
        helper.assertFalse(SettlementFarmWork.next(level, HiredWorkStateStore.state(replacement)).water(), "replacement resumes soil work");
        fixture.villager().discard();
        replacement.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void editingPlotsPreservesIdentitySettingsAndRejectsStaleReviews(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var base = fixture.base();
        level.setBlock(base.offset(4, 0, 4), Blocks.WATER.defaultBlockState(), 2);
        var oldShape = new SettlementFarm(SettlementFarm.Shape.CIRCLE, List.of(base.offset(4, 0, 4), base.offset(7, 0, 4)));
        var mix = new SettlementCropPlan(List.of(50, 50, 0, 0, 0, 0), false, 1);
        var previous = SettlementFarmWork.plot(SettlementFarmTerrain.preview(level, oldShape, pos -> true), oldShape, null, mix);
        previous = new SettlementArea(previous.id(), SettlementAreaType.FARM, "River crops", previous.min(), previous.max(), 7, true, mix, false, oldShape);
        helper.assertTrue(fixture.town().putArea(fixture.owner().getUUID(), previous), "existing shaped plot is saved");
        var shape = new SettlementFarm(base.offset(1, 0, 1), base.offset(7, 0, 7));
        var session = new SettlementFarmSession();
        var id = UUID.randomUUID();
        var request = new com.jvn.villagerretaliation.network.SettlementFarmRequestPayload(id, 1,
                com.jvn.villagerretaliation.network.SettlementFarmRequestPayload.Action.PREVIEW, id, shape, previous.id(), oldShape, mix);
        var review = session.request(fixture.owner(), fixture.town(), id, true, pos -> true, request);
        helper.assertValueEqual(review.status(), com.jvn.villagerretaliation.network.SettlementFarmResultPayload.Status.PREVIEW, review.message());
        helper.assertTrue(review.message().contains("planting cells"), "preview includes actual planting capacity");
        var changed = new SettlementArea(previous.id(), previous.type(), "Renamed crops", previous.min(), previous.max(), previous.priority(),
                previous.enabled(), mix, false, oldShape);
        helper.assertTrue(fixture.town().putArea(fixture.owner().getUUID(), changed), "another manager changes plot metadata");
        var confirm = new com.jvn.villagerretaliation.network.SettlementFarmRequestPayload(id, 1,
                com.jvn.villagerretaliation.network.SettlementFarmRequestPayload.Action.CONFIRM, review.token(), null);
        helper.assertValueEqual(session.request(fixture.owner(), fixture.town(), id, true, pos -> true, confirm).status(),
                com.jvn.villagerretaliation.network.SettlementFarmResultPayload.Status.REJECTED, "stale metadata prevents confirmation");
        helper.assertTrue(SettlementFarmWork.start(fixture.owner(), fixture.town(),
                SettlementFarmTerrain.preview(level, shape, pos -> true), shape, previous.id(), mix).isEmpty(), "explicit edit updates the plot");
        var updated = fixture.town().areas().get(previous.id());
        helper.assertValueEqual(updated.name(), changed.name(), "editing keeps the current name");
        helper.assertValueEqual(updated.crops(), mix, "editing keeps crop percentages and reserve cycles");
        helper.assertValueEqual(updated.priority(), 7, "editing keeps priority");
        helper.assertValueEqual(updated.farm(), shape, "editing updates geometry using the same plot identity");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void farmPacketsCarryCropPlansGeometryAndProgress(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var farm = new SettlementFarm(fixture.base(), fixture.base().offset(8, 0, 8));
        var plan = new SettlementCropPlan(List.of(0, 100, 0, 0, 0, 0));
        var request = new com.jvn.villagerretaliation.network.SettlementFarmRequestPayload(UUID.randomUUID(), 3,
                com.jvn.villagerretaliation.network.SettlementFarmRequestPayload.Action.PREVIEW, UUID.randomUUID(), farm, UUID.randomUUID(), farm, plan);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementFarmRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.SettlementFarmRequestPayload.STREAM_CODEC.decode(buffer), request,
                    "crop plan and original plot geometry round trip");
            inventoryBucket(fixture);
            helper.assertTrue(SettlementFarmWork.start(fixture.owner(), fixture.town(),
                    SettlementFarmTerrain.preview(level, farm, pos -> true), farm, null, plan).isEmpty(), "single-crop plan starts");
            var page = SettlementManagementService.snapshot(fixture.owner(), new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(
                    fixture.town().id().value(), com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS, 0, "", 1, false));
            com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.encode(buffer, page);
            var decoded = com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded, page, "farm progress survives project snapshot transport");
            var project = (com.jvn.villagerretaliation.network.SettlementManagementPayload.Project) decoded.entries().getFirst();
            helper.assertTrue(project.farm() != null && project.farm().waterTotal() == 1, "farm projects report irrigation separately");
        } finally { buffer.release(); }
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_farm")
    public static void farmJobsSuspendAfterPermissionLossAndCancelWhileUnloaded(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        inventoryBucket(fixture);
        var delegate = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FarmDelegate"));
        helper.assertTrue(fixture.town().setPermissions(fixture.owner().getUUID(), delegate.getUUID(),
                java.util.EnumSet.of(SettlementPermission.VIEW, SettlementPermission.BUILD, SettlementPermission.STAFF)), "delegate can request farm work");
        var farm = new SettlementFarm(fixture.base(), fixture.base().offset(8, 0, 8));
        helper.assertTrue(SettlementFarmWork.start(delegate, fixture.town(), SettlementFarmTerrain.preview(level, farm, pos -> true), farm).isEmpty(),
                "authorized delegate starts preparation");
        var job = fixture.town().farmJobs().values().iterator().next();
        var state = HiredWorkStateStore.state(fixture.villager());
        var water = SettlementFarmWork.next(level, state);
        tickAt(helper, fixture, water.position());
        helper.assertTrue(fixture.town().setPermissions(fixture.owner().getUUID(), delegate.getUUID(),
                java.util.EnumSet.of(SettlementPermission.VIEW)), "delegate loses work permissions");
        helper.assertFalse(SettlementFarmJobService.blocker(fixture.villager(), state).isEmpty(), "permission loss blocks work immediately");
        SettlementFarmJobService.tick(level, fixture.town());
        helper.assertValueEqual(job.status(), SettlementProject.Status.PENDING, "unauthorized job suspends without losing progress");
        helper.assertTrue(HiredTemporaryWorkService.queue(state) == null, "permission loss clears the active task");
        helper.assertTrue(SettlementFarmJobService.configure(fixture.owner(), fixture.town(), job.id(), job.revision(), 5, false,
                SettlementFarmJob.AUTOMATIC), "leader can authorize the remaining work");
        helper.assertValueEqual(job.status(), SettlementProject.Status.ACTIVE, "leader authorization resumes preparation");
        helper.assertValueEqual(job.done(true), 1, "renewed authorization retains irrigation progress");
        SettlementBackgroundService.onUnloaded(fixture.villager());
        fixture.villager().remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        SettlementFarmJobService.tick(level, fixture.town());
        helper.assertTrue(job.blocker().contains("unloaded"), "unloaded preparation explains its pause");
        helper.assertTrue(SettlementFarmJobService.cancel(fixture.owner(), fixture.town(), job.id()), "unloaded job cancels");
        helper.assertTrue(HiredTemporaryWorkService.queue(fixture.town().workers().get(fixture.villager().getUUID()).settings()) == null,
                "unloaded worker cannot resume a cancelled task");
        var saved = fixture.town().save(level.registryAccess());
        var restored = SettlementRecord.load(saved, level.registryAccess(), () -> {});
        helper.assertValueEqual(restored.farmJobs().get(job.id()).status(), SettlementProject.Status.CANCELLED, "unloaded cancellation survives saving");
        saved.remove("FarmJobs");
        helper.assertTrue(SettlementRecord.load(saved, level.registryAccess(), () -> {}).farmJobs().isEmpty(), "old towns without farm jobs remain loadable");
        helper.succeed();
    }

    public static com.jvn.villagerretaliation.network.SettlementManagementPayload prepareClientJob(ServerPlayer owner,
            SettlementFarm farm, SettlementCropPlan plan) {
        var town = SettlementSavedData.get(owner.serverLevel()).settlements().stream()
                .filter(candidate -> candidate.contains(farm.first())).findFirst().orElseThrow();
        String error = SettlementFarmWork.start(owner, town, SettlementFarmTerrain.preview(owner.serverLevel(), farm, town::contains), farm, null, plan);
        if (!error.isEmpty()) throw new IllegalStateException(error);
        return SettlementManagementService.snapshot(owner, new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(
                town.id().value(), com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS, 0, "", 1, false));
    }

    public static com.jvn.villagerretaliation.network.SettlementManagementPayload.Area cancelClientJob(ServerPlayer owner, UUID id, boolean removePlot) {
        var town = SettlementSavedData.get(owner.serverLevel()).settlements().stream().filter(candidate -> candidate.farmJobs().containsKey(id)).findFirst().orElseThrow();
        var job = town.farmJobs().get(id);
        if (job.unfinished() && !SettlementFarmJobService.cancel(owner, town, id)) throw new IllegalStateException("Client farm job did not cancel");
        var area = (com.jvn.villagerretaliation.network.SettlementManagementPayload.Area) SettlementManagementService.snapshot(owner,
                new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(town.id().value(),
                        com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.AREAS, 0, job.area().toString(), 1, false)).entries().getFirst();
        if (removePlot && !town.removeArea(owner.getUUID(), area.id())) throw new IllegalStateException("Client farm plot was not removed");
        return area;
    }

    private static void inventoryBucket(Fixture fixture) {
        var inventory = HiredJobInventory.getJobInventory(fixture.villager());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.WATER_BUCKET));
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
