package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.entity.VillagerFishingHook;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementFishingWorkGameTests {
    private SettlementFishingWorkGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_work")
    public static void unloadedFisherCatchesRealLootAndDepositsWithoutLoadingChunks(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            for (int now = 200; now <= 6_000; now += 200) {
                SettlementBackgroundService.update(level, fixture.town(), now);
            }
            int deposited = 0;
            for (int slot = 0; slot < fixture.output().getContainerSize(); slot++) {
                deposited += fixture.output().getItem(slot).getCount();
            }
            helper.assertTrue(deposited > 0, "running server time produces real fishing loot in the output depot");
            helper.assertTrue(rod(helper, fixture).getDamageValue() > 0, "completed catches wear the actual rod");
            helper.assertTrue(SettlementPracticeGameTests.xp(profile) > 0.0D, "completed fishing grants role practice");
            helper.assertFalse(level.hasChunkAt(fixture.spot().water()), "fishing and its loot context never load the observed shore");
            helper.assertTrue(level.getEntity(fixture.worker().id()) == null, "background fishing never registers its saved entity context");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(fixture.villager())
                    .getItem(HiredJobInventory.MAINHAND_SLOT).getDamageValue(), 0, "the detached native inventory waits for its receipt");
            helper.assertTrue(fixture.worker().restore(fixture.villager()), "completed work restores on the native villager");
            var restored = HiredJobInventory.getJobInventory(fixture.villager()).captureSnapshot();
            helper.assertFalse(fixture.worker().restore(fixture.villager()), "the same inventory receipt cannot apply twice");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(fixture.villager()).captureSnapshot(), restored,
                    "a repeated return preserves the inventory");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_work")
    public static void fullFishingInventoryPersistsOneCatchAndOneRodWearThroughNativeReturn(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            var carried = fixture.worker().items(level.registryAccess());
            for (int slot : fixture.worker().outputSlots()) carried.set(slot, new ItemStack(Items.STONE, 64));
            fixture.worker().updateInventory(level.registryAccess(), carried);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            ready(helper, fixture, 1, 0, -1L);
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertTrue(SettlementBackgroundFishing.hasPendingCatch(fixture.worker().settings()), "a full bag keeps the rolled catch");
            helper.assertValueEqual(rod(helper, fixture).getDamageValue(), 1, "the rolled catch wears its rod exactly once");
            var pending = fixture.worker().settings().getCompound("SettlementFishingCatch").copy();
            var loaded = SettlementWorkerState.load(fixture.worker().save());
            fixture.town().recordWorker(loaded);
            for (int now = 400; now <= 1_200; now += 200) SettlementBackgroundService.update(level, fixture.town(), now);
            helper.assertValueEqual(loaded.settings().getCompound("SettlementFishingCatch"), pending, "saving and waiting cannot reroll loot");
            helper.assertValueEqual(loaded.items(level.registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue(), 1,
                    "waiting cannot apply more durability damage");
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), 0.0D, "uncollected loot grants no extra practice");
            loaded.restore(fixture.villager());
            helper.assertFalse(SettlementBackgroundFishing.finishReturnedCatch(level, fixture.villager()), "a full native bag retains pending loot");
            var inventory = HiredJobInventory.getJobInventory(fixture.villager());
            int slot = loaded.outputSlots().getFirst();
            inventory.setItem(slot, ItemStack.EMPTY);
            helper.assertTrue(SettlementBackgroundFishing.finishReturnedCatch(level, fixture.villager()), "native space accepts the saved catch");
            var expected = ItemStack.parseOptional(level.registryAccess(), pending.getList("Items", Tag.TAG_COMPOUND).getCompound(0));
            helper.assertTrue(ItemStack.matches(inventory.getItem(slot), expected), "the exact saved stack reaches the native inventory");
            helper.assertFalse(SettlementBackgroundFishing.hasPendingCatch(loaded.settings()), "the town clears the catch before another observation");
            double xp = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(xp > 0.0D, "the collected catch grants fishing practice");
            SettlementBackgroundFishing.finishReturnedCatch(level, fixture.villager());
            loaded.restore(fixture.villager());
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), xp, "repeated return cannot repeat practice");
            helper.assertTrue(ItemStack.matches(inventory.getItem(slot), expected), "repeated return cannot duplicate the catch");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_work")
    public static void savedFishingCastResumesOnceAndRejectsAChangedRod(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            ready(helper, fixture, 0, 500, -1L);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            SettlementBackgroundService.update(level, fixture.town(), 20L);
            var saved = SettlementWorkerState.load(fixture.worker().save());
            int remaining = saved.settings().getCompound("SettlementFishingCast").getCompound("Progress").getInt("Lured");
            helper.assertValueEqual(remaining, 480, "only elapsed server ticks advance the cast");
            saved.restore(fixture.villager());
            var nativeState = HiredWorkStateStore.state(fixture.villager());
            var hook = new VillagerFishingHook(fixture.villager(), level, fixture.spot().water().getCenter(), 0, 0, 100);
            SettlementBackgroundFishing.resume(nativeState, rod(helper, fixture), fixture.spot().water(), fixture.spot().approach(), hook);
            helper.assertValueEqual(hook.captureFishingProgress().getInt("Lured"), remaining, "the saved cast reaches the native hook");
            helper.assertValueEqual(hook.position(), fixture.spot().water().getCenter(), "a resumed hook starts at its observed water");
            helper.assertTrue(hook.canResumeFishing(), "resuming keeps the hook bobbing instead of restarting its flight");
            var next = new VillagerFishingHook(fixture.villager(), level, fixture.spot().water().getCenter(), 0, 0, 100);
            SettlementBackgroundFishing.resume(nativeState, rod(helper, fixture), fixture.spot().water(), fixture.spot().approach(), next);
            helper.assertValueEqual(next.captureFishingProgress().getInt("Lured"), 0, "the same cast cannot resume twice");
            nativeState.put("SettlementFishingCast", saved.settings().getCompound("SettlementFishingCast").copy());
            var changed = rod(helper, fixture).copy();
            changed.setDamageValue(4);
            SettlementBackgroundFishing.resume(nativeState, changed, fixture.spot().water(), fixture.spot().approach(), next);
            helper.assertValueEqual(next.captureFishingProgress().getInt("Lured"), 0, "changing rod components cancels the old cast");
            helper.assertFalse(level.hasChunkAt(fixture.spot().water()), "resuming a cast does not load the shore");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_work")
    public static void fishingHandoffExcludesNativeClockTicksAndBoundsLongAbsence(GameTestHelper helper) {
        fixture(helper, fixture -> {
            ready(helper, fixture, 0, 500, 198L);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.worker().settings().getCompound("SettlementFishingCast")
                    .getCompound("Progress").getInt("Lured"), 498, "native ticks before handoff are not counted again");
            helper.assertValueEqual(fixture.worker().progress(), 0.0D, "native ticks cannot become background travel credit");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.worker().settings().getCompound("SettlementFishingCast")
                    .getCompound("Progress").getInt("Lured"), 498, "one server interval cannot replay");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 1_000_000L);
            helper.assertValueEqual(fixture.worker().settings().getCompound("SettlementFishingCast")
                    .getCompound("Progress").getInt("Lured"), 298, "a long absence contributes at most 200 server ticks");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_work")
    public static void backgroundFishingMendsAfterWearAndCannotRescueABrokenRod(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var items = fixture.worker().items(level.registryAccess());
            var rod = items.get(HiredJobInventory.MAINHAND_SLOT);
            rod.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
            rod.setDamageValue(20);
            fixture.worker().updateInventory(level.registryAccess(), items);
            ready(helper, fixture, 1, 0, -1L);
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertTrue(rod(helper, fixture).getDamageValue() < 20, "native repair effects consume catch XP after rod wear");
            items = fixture.worker().items(level.registryAccess());
            items.get(HiredJobInventory.MAINHAND_SLOT).setDamageValue(rod.getMaxDamage() - 1);
            fixture.worker().updateInventory(level.registryAccess(), items);
            fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
            ready(helper, fixture, 1, 0, -1L);
            SettlementBackgroundService.update(level, fixture.town(), 400L);
            helper.assertTrue(rod(helper, fixture).isEmpty(), "the final durability point breaks before Mending can repair");
            helper.assertValueEqual(fixture.worker().settings().getList("SettlementFishingExperience", Tag.TAG_COMPOUND).size(), 1,
                    "XP without any remaining Mending equipment waits for a native return");
            var saved = SettlementWorkerState.load(fixture.worker().save());
            helper.assertValueEqual(saved.settings().getList("SettlementFishingExperience", Tag.TAG_COMPOUND).size(), 1,
                    "unconsumed owned XP survives saving");
            helper.assertFalse(level.hasChunkAt(fixture.spot().water()), "rod breakage and Mending cannot load fishing terrain");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_work")
    public static void backgroundFisherFetchesFiniteRodsAndPreservesHeldEquipment(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var items = fixture.worker().items(level.registryAccess());
            items.set(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
            fixture.worker().updateInventory(level.registryAccess(), items);
            fixture.supply().setItem(0, new ItemStack(Items.FISHING_ROD));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertValueEqual(fixture.worker().status(), "missing_rod", "disabled supply access cannot fetch a depot rod");
            helper.assertValueEqual(fixture.supply().getItem(0).getCount(), 1, "blocked acquisition preserves finite stock");
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
            for (int now = 400; now <= 1_000; now += 200) SettlementBackgroundService.update(level, fixture.town(), now);
            helper.assertTrue(rod(helper, fixture).is(Items.FISHING_ROD), "the supplied rod becomes real equipment");
            helper.assertTrue(fixture.supply().getItem(0).isEmpty(), "equipping consumes the depot rod");
            helper.assertTrue(fixture.worker().items(level.registryAccess()).stream().anyMatch(stack -> stack.is(Items.IRON_AXE)),
                    "replacing the held tool preserves it in the supply inventory");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_work")
    public static void invalidFishingSitesAndPausedWorkersCannotProduceCatches(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            ready(helper, fixture, 1, 0, -1L);
            fixture.worker().settings().putBoolean("Enabled", false);
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertValueEqual(rod(helper, fixture).getDamageValue(), 0, "paused workers cannot retrieve catches");
            fixture.worker().settings().putBoolean("Enabled", true);
            setBlock(fixture.chunk(), fixture.spot().water(), Blocks.STONE.defaultBlockState());
            fixture.spot().observe(level, fixture.chunk());
            SettlementBackgroundService.update(level, fixture.town(), 400L);
            helper.assertValueEqual(fixture.worker().status(), "no_observed_fishery", "removed water cancels its cast");
            helper.assertValueEqual(rod(helper, fixture).getDamageValue(), 0, "invalid sites cannot wear rods or create loot");
            helper.assertFalse(fixture.worker().settings().contains("SettlementFishingCast"), "the invalid cast is retired");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_work")
    public static void oldFishingWorkersWaitForAnActualEntityObservation(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var old = fixture.worker().save();
            old.remove("EntityContext");
            var restored = SettlementWorkerState.load(old);
            fixture.town().recordWorker(restored);
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertValueEqual(restored.status(), "awaiting_worker_observation", "older saves wait for actual enchantment and loot context");
            helper.assertValueEqual(restored.items(level.registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue(), 0,
                    "missing context cannot consume equipment");
            restored.observe(level, fixture.villager(), SettlementWorkforceService.duty(fixture.villager()).orElseThrow(), 200L);
            SettlementBackgroundService.update(level, fixture.town(), 400L);
            helper.assertFalse(restored.status().equals("awaiting_worker_observation"), "a native observation enables background work");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_work")
    public static void completedNativeFishingPracticeTransfersToBackgroundOnlyOnce(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var profile = SettlementPracticeGameTests.prepare(helper, fixture.worker());
            var state = HiredWorkStateStore.state(fixture.villager());
            state.putBoolean("FishingCatchCompleted", true);
            state.putDouble("FishingCatchPracticeUnits", 1.0D);
            state.putLong("FishingCatchRepetitionKey", 41L);
            HiredJobInventory.getJobInventory(fixture.villager()).setItem(HiredJobInventory.MAINHAND_SLOT, ItemStack.EMPTY);
            fixture.worker().observe(level, fixture.villager(), SettlementWorkforceService.duty(fixture.villager()).orElseThrow(), 0L);
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            double xp = SettlementPracticeGameTests.xp(profile);
            helper.assertTrue(xp > 0.0D, "a native completed catch retains its unprocessed practice");
            SettlementBackgroundService.update(level, fixture.town(), 400L);
            fixture.worker().restore(fixture.villager());
            helper.assertValueEqual(SettlementPracticeGameTests.xp(profile), xp, "background and return cannot reaward native practice");
            helper.assertFalse(HiredWorkStateStore.state(fixture.villager()).getBoolean("FishingCatchCompleted"),
                    "the native completion receipt clears on return");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_handoff")
    public static void backgroundFishingTakesTheLatestLiveHookAndReturnsItOnce(GameTestHelper helper) {
        fixture(helper, true, fixture -> {
            var level = helper.getLevel();
            var villager = fixture.villager();
            level.addFreshEntity(villager);
            var progress = new CompoundTag();
            progress.putInt("Lured", 500);
            progress.putInt("WorkSpeedPercent", 100);
            progress.putBoolean("OpenWater", true);
            var hook = new VillagerFishingHook(villager, level, fixture.spot().water().getCenter(), 0, 0, 100);
            hook.resumeFishingAt(fixture.spot().water(), progress);
            level.addFreshEntity(hook);
            try {
                var state = HiredWorkStateStore.state(villager);
                state.putInt("FishingActiveHookId", hook.getId());
                state.putLong("FishingWaterPos", fixture.spot().water().west().asLong());
                state.putLong("FishingApproachPos", fixture.spot().approach().asLong());
                fixture.worker().observe(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), level.getGameTime());
                helper.assertValueEqual(fixture.worker().settings().getCompound("SettlementFishingCast").getLong("Water"),
                        fixture.spot().water().asLong(), "observation captures the actual bobber position after its flight");
                hook.advanceBackground(10, false, true);
                SettlementBackgroundFishing.tick(level, fixture.town(), fixture.worker(), 200, 1.0D, level.getGameTime() + 10L);
                helper.assertFalse(hook.isAlive(), "background work retires the live hook before advancing");
                helper.assertValueEqual(fixture.worker().settings().getCompound("SettlementFishingCast")
                        .getCompound("Progress").getInt("Lured"), 480, "handoff takes the latest hook clock before applying its own interval");
                fixture.worker().restore(villager);
                helper.assertFalse(state.contains("FishingActiveHookId"), "the old native hook ID cannot reappear on return");
                var resumed = new VillagerFishingHook(villager, level, fixture.spot().water().getCenter(), 0, 0, 100);
                SettlementBackgroundFishing.resume(state, rod(helper, fixture), fixture.spot().water(), fixture.spot().approach(), resumed);
                helper.assertValueEqual(resumed.captureFishingProgress().getInt("Lured"), 480, "the return preserves the transferred cast clock");
                helper.assertFalse(state.contains("SettlementFishingCast"), "the native cast consumes its resume record once");
            } finally {
                hook.discard();
                villager.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_work")
    public static void fishingCastsUseTheActualRodEnchantmentsAndAptitude(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var items = fixture.worker().items(level.registryAccess());
            var rod = items.get(HiredJobInventory.MAINHAND_SLOT);
            var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            rod.enchant(enchantments.getOrThrow(Enchantments.LURE), 3);
            rod.enchant(enchantments.getOrThrow(Enchantments.LUCK_OF_THE_SEA), 2);
            fixture.worker().updateInventory(level.registryAccess(), items);
            fixture.worker().work(39, 1.0D);
            SettlementBackgroundService.update(level, fixture.town(), 1L);
            var progress = fixture.worker().settings().getCompound("SettlementFishingCast").getCompound("Progress");
            helper.assertValueEqual(progress.getInt("Luck"), 2, "Luck of the Sea comes from the equipped rod");
            helper.assertValueEqual(progress.getInt("LureSpeed"), 300, "Lure uses native seconds converted to ticks");
            helper.assertValueEqual(progress.getInt("WorkSpeedPercent"),
                    com.jvn.villagerretaliation.interaction.HiredVillagerRoles.skillWorkSpeedPercent(fixture.worker().aptitude()),
                    "the fishing clock uses the real worker aptitude");
            helper.assertValueEqual(rod(helper, fixture).getDamageValue(), 0, "casting alone does not wear the rod");
        });
    }

    private static ItemStack rod(GameTestHelper helper, Fixture fixture) {
        return fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT);
    }

    private static void ready(GameTestHelper helper, Fixture fixture, int nibble, int lured, long nativeTick) {
        var progress = new CompoundTag();
        progress.putInt("Nibble", nibble);
        progress.putInt("Lured", lured);
        progress.putInt("WorkSpeedPercent", 100);
        progress.putBoolean("OpenWater", true);
        progress.putLong("UpdatedAt", nativeTick);
        var cast = new CompoundTag();
        cast.put("Progress", progress);
        cast.put("Rod", rod(helper, fixture).save(helper.getLevel().registryAccess()));
        cast.putLong("Water", fixture.spot().water().asLong());
        cast.putLong("Approach", fixture.spot().approach().asLong());
        fixture.worker().settings().put("SettlementFishingCast", cast);
        fixture.worker().settings().putLong("FishingWaterPos", fixture.spot().water().asLong());
        fixture.worker().settings().putLong("FishingApproachPos", fixture.spot().approach().asLong());
    }

    private static void setBlock(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        fixture(helper, false, check);
    }

    private static void fixture(GameTestHelper helper, boolean loaded, Consumer<Fixture> check) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var chunk = loaded ? level.getChunkAt(origin)
                : new LevelChunk(level, new ChunkPos(660_000 + origin.getX() * 4, 660_000 + origin.getZ() * 4));
        var water = new BlockPos(chunk.getPos().getMinBlockX() + 8, loaded ? origin.getY() + 4 : 63, chunk.getPos().getMinBlockZ() + 8);
        var approach = water.west(4).above();
        if (loaded) {
            for (BlockPos position : BlockPos.betweenClosed(water.offset(-5, -2, -3), water.offset(3, 3, 3))) {
                chunk.setBlockState(position, Blocks.AIR.defaultBlockState(), false);
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                setBlock(chunk, water.offset(x, 0, z), Blocks.WATER.defaultBlockState());
                setBlock(chunk, water.offset(x, -1, z), Blocks.WATER.defaultBlockState());
            }
        }
        for (int z = -3; z <= 3; z++) setBlock(chunk, approach.offset(0, -1, z), Blocks.STONE.defaultBlockState());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), approach, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), approach, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FISHERY, "Shore", approach.north(3), approach.south(3), 5, true);
        town.putArea(town.leader(), area);
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage", approach.west(3), approach.west(), 5, true);
        town.putArea(town.leader(), storage);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", approach.west(7), approach.west(5), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(home.min()), List.of(), true));
        var supply = town.registerDepot(town.leader(), storage.id(), storage.min(), SettlementDepotPurpose.MATERIALS);
        var output = town.registerDepot(town.leader(), storage.id(), storage.max(), SettlementDepotPurpose.OUTPUT);
        supply.observeRules(List.of());
        output.observeRules(List.of());
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(approach.getBottomCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FISHERMAN));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.FISHING_ROD));
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        var spot = SettlementFishingSpot.capture(level, area, water, approach, chunk);
        helper.assertTrue(spot != null && spot.valid(), "the prepared shore has observed water and a safe casting site");
        town.recordFishingSpots(area.id(), List.of(spot));
        if (!loaded) helper.assertFalse(level.hasChunkAt(water), "the fixture does not load the fishing shore");
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            check.accept(new Fixture(town, area, chunk, spot, supply, output, worker, villager));
        } finally {
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, LevelChunk chunk, SettlementFishingSpot spot,
                           SettlementDepot supply, SettlementDepot output, SettlementWorkerState worker, Villager villager) {
    }
}
