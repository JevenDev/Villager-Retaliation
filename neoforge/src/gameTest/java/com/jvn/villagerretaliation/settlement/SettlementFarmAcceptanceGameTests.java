package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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

@GameTestHolder("settlement_farm_acceptance")
@PrefixGameTestTemplate(false)
public final class SettlementFarmAcceptanceGameTests {
    private static final java.util.Map<net.minecraft.server.level.ServerLevel, Environment> ENVIRONMENTS = new java.util.HashMap<>();
    private SettlementFarmAcceptanceGameTests() {}

    private static final class Environment implements net.minecraft.gametest.framework.GameTestListener {
        private final net.minecraft.server.level.ServerLevel level;
        private final long day;
        private final boolean daylight, spawning;
        private final java.util.Set<UUID> farmers = new java.util.HashSet<>();
        private int users;

        private Environment(net.minecraft.server.level.ServerLevel level) {
            this.level = level;
            this.day = level.getDayTime();
            this.daylight = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            this.spawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        }

        private void finished() {
            if (--users != 0) return;
            for (var id : farmers) if (level.getEntity(id) instanceof Villager farmer) farmer.discard();
            level.setDayTime(day);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(daylight, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning, level.getServer());
            ENVIRONMENTS.remove(level);
        }

        @Override
        public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {}

        @Override
        public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) {
            finished();
        }

        @Override
        public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) {
            finished();
        }

        @Override
        public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo previous, net.minecraft.gametest.framework.GameTestInfo next,
                                     net.minecraft.gametest.framework.GameTestRunner runner) {}
    }

    @GameTest(template = "empty", batch = "native_farm", timeoutTicks = 4000)
    public static void nativeFarmerWalksRepeatedWaterTripsWithoutTeleports(GameTestHelper helper) {
        var fixture = fixture(helper, 10, 2, false);
        var inventory = HiredJobInventory.getJobInventory(fixture.farmer());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.BUCKET));
        var job = start(helper, fixture);
        helper.assertValueEqual(job.total(true), 2, "two sources require two refill trips");
        var initial = fixture.farmer().position();
        int[] ticks = {0}, refills = {0}, previousWater = {0}, previousDone = {0}, lastProgress = {0};
        var progress = new java.util.ArrayList<String>();
        helper.onEachTick(() -> {
            int water = inventory.supplySlots().stream().map(inventory::getItem).filter(stack -> stack.is(Items.WATER_BUCKET)).mapToInt(ItemStack::getCount).sum();
            if (water > previousWater[0]) refills[0]++;
            previousWater[0] = water;
            int done = job.done(true) + job.done(false);
            if (done != previousDone[0]) {
                progress.add(ticks[0] + "=" + done);
                lastProgress[0] = ticks[0];
            }
            previousDone[0] = done;
            helper.assertTrue(++ticks[0] < 3900 && ticks[0] - lastProgress[0] < 1000,
                    "native preparation stalled: " + diagnostic(fixture, job) + " / progress " + progress);
            if (job.unfinished()) return;
            helper.assertValueEqual(job.status(), SettlementProject.Status.COMPLETED, "native farmer finishes the job");
            helper.assertTrue(fixture.farmer().position().distanceToSqr(initial) > 4, "farmer actually walks");
            helper.assertTrue(refills[0] >= 2, "one bucket is refilled on separate trips");
            helper.assertValueEqual(bucketCount(inventory), 1, "walking and refilling do not duplicate or lose buckets");
            helper.assertTrue(SettlementFarmWater.infinite(helper.getLevel(), fixture.source()), "refills preserve the source");
            helper.assertValueEqual(job.done(false), job.total(false), "all planned soil is processed");
            fixture.farmer().discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "native_farm", timeoutTicks = 4000)
    public static void nativeFarmerFetchesReplacementHoeAfterBreaking(GameTestHelper helper) {
        var fixture = fixture(helper, 3, 3, true);
        var inventory = HiredJobInventory.getJobInventory(fixture.farmer());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.WATER_BUCKET));
        var fragile = new ItemStack(Items.IRON_HOE);
        fragile.setDamageValue(fragile.getMaxDamage() - 2);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, fragile);
        var depot = SettlementWallMaterialsGameTests.supply(helper.getLevel(), fixture.town(), fixture.base().east(20).above());
        depot.clearContent();
        var job = start(helper, fixture);
        int[] ticks = {0}, brokenAt = {-1}, previousDone = {0}, lastProgress = {0};
        var progress = new java.util.ArrayList<String>();
        helper.onEachTick(() -> {
            int done = job.done(true) + job.done(false);
            if (done != previousDone[0]) {
                progress.add(ticks[0] + "=" + done);
                lastProgress[0] = ticks[0];
            }
            previousDone[0] = done;
            helper.assertTrue(++ticks[0] < 3900 && ticks[0] - lastProgress[0] < 1800,
                    "tool recovery stalled: " + diagnostic(fixture, job) + " / progress " + progress);
            if (brokenAt[0] < 0 && inventory.getItem(HiredJobInventory.MAINHAND_SLOT).isEmpty()) brokenAt[0] = ticks[0];
            if (brokenAt[0] >= 0 && ticks[0] == brokenAt[0] + 80) {
                helper.assertValueEqual(job.done(true), 1, "tool shortage retains irrigation");
                depot.setItem(0, new ItemStack(Items.IRON_HOE));
                SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            }
            if (job.unfinished()) return;
            helper.assertTrue(brokenAt[0] >= 0, "the first hoe really breaks");
            helper.assertValueEqual(job.status(), SettlementProject.Status.COMPLETED, "replacement tool resumes native work");
            helper.assertTrue(inventory.getItem(HiredJobInventory.MAINHAND_SLOT).is(Items.IRON_HOE), "farmer collected the replacement tool");
            helper.assertValueEqual(depot.count(stack -> stack.is(Items.IRON_HOE)), 0, "storage loses exactly the fetched hoe");
            fixture.farmer().discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "native_farm_obstacle", timeoutTicks = 2400)
    public static void nativeFarmerRetriesAfterBlockedReturnRouteOpens(GameTestHelper helper) {
        var fixture = fixture(helper, 3, 3, false);
        fixture.farmer().setPos(fixture.base().west(4).above().getBottomCenter());
        var cage = fixture.farmer().blockPosition();
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
            for (int y = 0; y < 3; y++) helper.getLevel().setBlock(cage.relative(direction).above(y), Blocks.STONE.defaultBlockState(), 3);
        var inventory = HiredJobInventory.getJobInventory(fixture.farmer());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.WATER_BUCKET));
        var job = start(helper, fixture);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            helper.assertTrue(++ticks[0] < 2300, "route recovery stalled: " + diagnostic(fixture, job));
            if (ticks[0] == 120) {
                helper.assertValueEqual(job.done(true), 0, "blocked farmer cannot place distant water");
                for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
                    for (int y = 0; y < 3; y++) helper.getLevel().setBlock(cage.relative(direction).above(y), Blocks.AIR.defaultBlockState(), 3);
            }
            if (job.unfinished()) return;
            helper.assertTrue(ticks[0] > 120, "job did not bypass the obstruction");
            helper.assertValueEqual(job.status(), SettlementProject.Status.COMPLETED, "opening the route resumes work without reissuing the job");
            fixture.farmer().discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "native_farm_inventory", timeoutTicks = 2400)
    public static void nativeFarmerResumesRefillingWhenSupplySpaceOpens(GameTestHelper helper) {
        var fixture = fixture(helper, 3, 3, false);
        var inventory = HiredJobInventory.getJobInventory(fixture.farmer());
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.BUCKET, 2));
        var job = start(helper, fixture);
        int[] ticks = {0}, blockedAt = {-1};
        helper.onEachTick(() -> {
            helper.assertTrue(++ticks[0] < 2300, "inventory recovery stalled: " + diagnostic(fixture, job));
            if (blockedAt[0] < 0 && job.blocker().equals("interaction.work.farmland.bucket_space")) blockedAt[0] = ticks[0];
            if (blockedAt[0] >= 0 && ticks[0] == blockedAt[0] + 40) {
                helper.assertValueEqual(job.done(true), 0, "full supplies prevent water placement without losing work");
                helper.assertValueEqual(bucketCount(inventory), 2, "failed refill does not consume buckets");
                inventory.setItem(inventory.supplySlots().getLast(), ItemStack.EMPTY);
            }
            if (job.unfinished()) return;
            helper.assertTrue(blockedAt[0] >= 0, "the full inventory actually blocked a refill");
            helper.assertValueEqual(job.status(), SettlementProject.Status.COMPLETED, "freeing a slot resumes native work");
            helper.assertValueEqual(bucketCount(inventory), 2, "recovery conserves both buckets");
            fixture.farmer().discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "native_farm_schedule", timeoutTicks = 2400)
    public static void nativeFarmerKeepsPartialWorkThroughTheNight(GameTestHelper helper) {
        var fixture = fixture(helper, 3, 3, false);
        var inventory = HiredJobInventory.getJobInventory(fixture.farmer());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.WATER_BUCKET));
        var job = start(helper, fixture);
        int[] ticks = {0}, nightAt = {-1}, soil = {0};
        helper.onEachTick(() -> {
            helper.assertTrue(++ticks[0] < 2300, "workday recovery stalled: " + diagnostic(fixture, job));
            if (nightAt[0] < 0 && job.done(true) == 1) {
                nightAt[0] = ticks[0];
                soil[0] = job.done(false);
                helper.getLevel().setDayTime(18000);
            }
            if (nightAt[0] >= 0 && ticks[0] <= nightAt[0] + 80)
                helper.assertValueEqual(job.done(false), soil[0], "night rest preserves partial work without continuing preparation");
            if (nightAt[0] >= 0 && ticks[0] == nightAt[0] + 80) helper.getLevel().setDayTime(6000);
            if (job.unfinished()) return;
            helper.assertTrue(nightAt[0] >= 0 && ticks[0] > nightAt[0] + 80, "the job resumes after the next workday starts");
            helper.assertValueEqual(job.status(), SettlementProject.Status.COMPLETED, "morning resumes the same preparation job");
            fixture.farmer().discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "farm_water_enclosure")
    public static void adjacentIrrigationRechecksTheEntireApprovedEnclosure(GameTestHelper helper) {
        var fixture = fixture(helper, 10, 2, false);
        var preview = SettlementFarmTerrain.preview(helper.getLevel(), fixture.farm(), fixture.town()::contains);
        var water = preview.cells().stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.WATER)
                .map(SettlementFarmTerrain.Cell::position).collect(java.util.stream.Collectors.toSet());
        helper.assertValueEqual(water.size(), 2, "narrow field needs adjacent irrigation sources");
        var first = water.iterator().next();
        var second = water.stream().filter(pos -> !pos.equals(first)).findFirst().orElseThrow();
        helper.getLevel().setBlock(first, Blocks.WATER.defaultBlockState(), 3);
        helper.assertTrue(SettlementFarmTerrain.contained(helper.getLevel(), second, water), "approved adjacent sources remain enclosed");
        helper.getLevel().setBlock(second, Blocks.DIRT_PATH.defaultBlockState(), 3);
        helper.assertTrue(SettlementFarmTerrain.contained(helper.getLevel(), second, water), "a path block being replaced is part of the approved enclosure");
        helper.getLevel().setBlock(first.below(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertFalse(SettlementFarmTerrain.contained(helper.getLevel(), second, water), "a leak below the earlier source still blocks placement");
        helper.getLevel().setBlock(first.below(), Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(first.north(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertFalse(SettlementFarmTerrain.contained(helper.getLevel(), second, water), "an opened perimeter still blocks placement");
        fixture.farmer().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "farm_cancelled_return")
    public static void returningFarmerDropsCancelledPreparation(GameTestHelper helper) {
        var fixture = fixture(helper, 3, 3, false);
        fixture.farmer().setNoAi(true);
        var inventory = HiredJobInventory.getJobInventory(fixture.farmer());
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.WATER_BUCKET));
        var job = start(helper, fixture);
        var saved = fixture.farmer().saveWithoutId(new net.minecraft.nbt.CompoundTag());
        SettlementBackgroundService.onUnloaded(fixture.farmer());
        fixture.farmer().remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(SettlementFarmJobService.cancel(fixture.owner(), fixture.town(), job.id()), "unloaded preparation cancels");
        helper.runAfterDelay(2, () -> {
            var returning = EntityType.VILLAGER.create(helper.getLevel());
            returning.load(saved);
            helper.assertTrue(helper.getLevel().addFreshEntity(returning), "the old entity returns");
            SettlementBackgroundService.onActive(returning);
            helper.assertTrue(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.queue(HiredWorkStateStore.state(returning)) == null,
                    "returning entity discards its cancelled queue");
            helper.assertValueEqual(job.status(), SettlementProject.Status.CANCELLED, "returning entity cannot reopen the cancelled job");
            helper.assertValueEqual(bucketCount(HiredJobInventory.getJobInventory(returning)), 1, "cancellation preserves the carried bucket");
            returning.discard();
            helper.succeed();
        });
    }

    @net.minecraft.gametest.framework.GameTestGenerator
    public static java.util.Collection<net.minecraft.gametest.framework.TestFunction> repeatedWaterTrips() {
        return java.util.stream.IntStream.range(0, 2).mapToObj(repeat -> new net.minecraft.gametest.framework.TestFunction(
                "native_farm_repeat", "nativefarm.repeat_water_trips_" + repeat, "settlement_farm_acceptance:empty",
                4000, 0, true, SettlementFarmAcceptanceGameTests::nativeFarmerWalksRepeatedWaterTripsWithoutTeleports)).toList();
    }

    @net.minecraft.gametest.framework.GameTestGenerator
    public static java.util.Collection<net.minecraft.gametest.framework.TestFunction> farmingRegressions() {
        if (!java.util.Arrays.asList(System.getProperty("neoforge.enabledGameTestNamespaces", "").split(",")).contains("settlement_farm_acceptance"))
            return List.of();
        return java.util.Arrays.stream(SettlementFarmingGameTests.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GameTest.class))
                .sorted(java.util.Comparator.comparing(java.lang.reflect.Method::getName))
                .map(method -> new net.minecraft.gametest.framework.TestFunction("background_farm_regressions",
                        "farmingregressions." + method.getName().toLowerCase(java.util.Locale.ROOT), "settlement_farm_acceptance:empty",
                        method.getAnnotation(GameTest.class).timeoutTicks(), 0, true, helper -> {
                    try { method.invoke(null, helper); }
                    catch (java.lang.reflect.InvocationTargetException exception) { throw new AssertionError(exception.getCause()); }
                    catch (ReflectiveOperationException exception) { throw new IllegalStateException("Cannot run farming regression", exception); }
                })).toList();
    }

    @GameTest(template = "empty", batch = "shaped_farm_parity")
    public static void loadedAndBackgroundMixesMatchSeedReservesAfterSaving(GameTestHelper helper) {
        SettlementFarmingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var base = fixture.plot().position();
            var shape = new SettlementFarm(SettlementFarm.Shape.POLYGON, List.of(base, base.east(2), base.offset(2, 0, 1),
                    base.offset(1, 0, 1), base.offset(1, 0, 2), base.south(2)));
            var mix = new SettlementCropPlan(List.of(50, 50, 0, 0, 0, 0), false, 1);
            var area = new SettlementArea(fixture.area().id(), SettlementAreaType.FARM, "Shaped mix", base, base.offset(2, 1, 2), 5, true, mix, false, shape);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), area), "shaped crop mix is configured");
            var positions = List.of(base, base.east(), base.south());
            for (var pos : positions.subList(1, positions.size())) {
                fixture.chunk().setBlockState(pos, Blocks.FARMLAND.defaultBlockState(), false);
                fixture.town().recordField(SettlementFieldPlot.capture(level, area.id(), pos, fixture.chunk(), level.getGameTime()));
            }
            var live = positions.stream().map(pos -> SettlementPlanting.crop(fixture.villager(), pos)).toList();
            var background = positions.stream().map(pos -> SettlementPlanting.choose(level, fixture.town(), area,
                    fixture.town().fields().get(pos), pos, fixture.worker().settings()).crop()).toList();
            helper.assertValueEqual(live, background, "loaded and background workers choose the same crops");
            var wheat = com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop.WHEAT;
            var carrot = com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop.CARROT;
            var targets = SettlementPlantingReserve.targets(fixture.town(), area);
            helper.assertValueEqual(targets.getOrDefault(wheat, 0).longValue(), live.stream().filter(crop -> crop == wheat).count(),
                    "wheat reserves count actual planting cells");
            helper.assertValueEqual(targets.getOrDefault(carrot, 0).longValue(), live.stream().filter(crop -> crop == carrot).count(),
                    "carrot reserves count actual planting cells");
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertValueEqual(SettlementPlantingReserve.targets(restored, restored.areas().get(area.id())), targets,
                    "shape and seed reserve allocation survive a save");
        });
    }

    static Fixture fixture(GameTestHelper helper, int width, int depth, boolean storage) {
        var level = helper.getLevel();
        var environment = ENVIRONMENTS.computeIfAbsent(level, Environment::new);
        environment.users++;
        helper.testInfo.addListener(environment);
        var base = helper.absolutePos(new BlockPos(6, 40, 6));
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "NativeFarm"));
        var fixture = fixture(level, owner, base, width, depth, storage);
        environment.farmers.add(fixture.farmer().getUUID());
        return fixture;
    }

    static Fixture fixture(net.minecraft.server.level.ServerLevel level, ServerPlayer owner, BlockPos base, int width, int depth, boolean storage) {
        for (var pos : BlockPos.betweenClosed(base.offset(-7, -1, -3), base.offset(24, 4, 12)))
            level.setBlock(pos, pos.getY() <= base.getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), base, "");
        var town = SettlementSavedData.get(level).establish(registry, id, owner.getUUID(), base, level.getGameTime()).orElseThrow();
        SettlementTestPlots.claim(level, town, base.offset(-7, 0, -3), base.offset(24, 4, 12));
        var farmer = EntityType.VILLAGER.create(level);
        farmer.setPos(base.above().getBottomCenter());
        farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        farmer.setVillagerXp(10);
        VillageAllegianceApi.assignKnown(level, farmer, id, AllegianceAssignmentSource.EXPLICIT_API);
        level.addFreshEntity(farmer);
        var inventory = HiredJobInventory.getJobInventory(farmer);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        var state = HiredWorkStateStore.state(farmer);
        state.putBoolean("AutoDepositOutputs", false);
        state.putBoolean("UseAssignedStorageForSupplies", storage);
        var source = base.offset(15, 0, 5);
        for (var pos : List.of(source, source.east(), source.south(), source.east().south()))
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        return new Fixture(owner, town, farmer, base, source, new SettlementFarm(base, base.offset(width - 1, 0, depth - 1)));
    }

    static SettlementFarmJob start(GameTestHelper helper, Fixture fixture) {
        var preview = SettlementFarmTerrain.preview(helper.getLevel(), fixture.farm(), fixture.town()::contains);
        String error = SettlementFarmWork.start(fixture.owner(), fixture.town(), preview, fixture.farm());
        helper.assertTrue(error.isEmpty(), "native farm starts: " + error);
        return fixture.town().farmJobs().values().stream().filter(SettlementFarmJob::unfinished).findFirst().orElseThrow();
    }

    static String diagnostic(Fixture fixture, SettlementFarmJob job) {
        var state = HiredWorkStateStore.state(fixture.farmer());
        return job.blocker() + " / " + state.getString("Status") + " / " + state.getString("WorkerTaskState")
                + " / position " + fixture.farmer().blockPosition() + " / soil " + job.done(false) + "/" + job.total(false)
                + " / water " + job.done(true) + "/" + job.total(true)
                + " / health " + fixture.farmer().getHealth() + " / alive " + fixture.farmer().isAlive()
                + " / target " + com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.snapshot(state, fixture.farmer().level().getGameTime()).targetPos()
                + " / navigation " + fixture.farmer().getNavigation().getTargetPos()
                + " done=" + fixture.farmer().getNavigation().isDone()
                + " / pending soil " + job.work().getList("Cells", net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(net.minecraft.nbt.CompoundTag.class::cast)
                        .filter(cell -> !cell.getBoolean("Water") && !cell.getBoolean("Done")).map(cell -> BlockPos.of(cell.getLong("Pos"))).toList()
                + " / remaining water " + job.work().getList("Cells", net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(net.minecraft.nbt.CompoundTag.class::cast)
                        .filter(cell -> cell.getBoolean("Water") && !cell.getBoolean("Done")).map(cell -> {
                            var pos = BlockPos.of(cell.getLong("Pos"));
                            return pos + "=" + fixture.farmer().level().getBlockState(pos) + " above=" + fixture.farmer().level().getBlockState(pos.above())
                                    + " contained=" + SettlementFarmTerrain.contained(fixture.farmer().level(), pos);
                        }).toList();
    }

    static int bucketCount(HiredJobInventory inventory) {
        return inventory.supplySlots().stream().map(inventory::getItem).filter(stack -> stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET))
                .mapToInt(ItemStack::getCount).sum();
    }

    record Fixture(ServerPlayer owner, SettlementRecord town, Villager farmer, BlockPos base, BlockPos source, SettlementFarm farm) {}
}
