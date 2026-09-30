package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HuntingWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementHuntingSupplyGameTests {
    private SettlementHuntingSupplyGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void unloadedHunterCollectsFiniteNativeLootAndRejectsOldBodies(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.IRON_SWORD);
        var item = item(helper, fixture, new ItemStack(Items.BEEF, 3));
        var original = item.saveWithoutId(new CompoundTag());
        var state = record(helper, fixture, item);
        long day = helper.getLevel().getDayTime();
        try {
            helper.getLevel().setDayTime(6_000L);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(count(helper, fixture, Items.BEEF), 3, "the main background dispatcher runs the unloaded hunter");
            helper.assertTrue(state.removed(), "pickup records the finite native stack's removal");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(count(helper, fixture, Items.BEEF), 3, "the same work interval cannot collect another copy");
            var returning = item(helper, fixture, new ItemStack(Items.BEEF));
            returning.load(original);
            helper.assertFalse(SettlementGroundItemService.onActive(returning), "an old native loot body is consumed before it can return");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertTrue(restored.groundItems().get(item.getUUID()).removed(), "the pickup survives a town save");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "hunting loot pickup never loads its chunk");
        } finally {
            helper.getLevel().setDayTime(day);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void backgroundHunterUsesTheNativeAnimalHostileFishAndAmmoLootFilter(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.IRON_SWORD);
        var accepted = List.of(Items.BEEF, Items.LEATHER, Items.WHITE_WOOL, Items.BONE, Items.ROTTEN_FLESH,
                Items.GUNPOWDER, Items.ENDER_PEARL, Items.COD, Items.PRISMARINE_SHARD, Items.SPECTRAL_ARROW);
        for (var item : accepted) record(helper, fixture, item(helper, fixture, new ItemStack(item, 2)));
        var excluded = List.of(Items.EMERALD, Items.BREAD, Items.EGG, Items.STONE);
        for (var item : excluded) record(helper, fixture, item(helper, fixture, new ItemStack(item, 2)));
        for (int step = 0; step < 16; step++) work(helper, fixture);
        for (var item : accepted) helper.assertValueEqual(count(helper, fixture, item), 2, "native hunting loot is collected exactly once: " + item);
        for (var item : excluded) helper.assertValueEqual(count(helper, fixture, item), 0, "unrelated items are left on the ground: " + item);
        helper.assertValueEqual(fixture.town().groundItems().values().stream().filter(state -> !state.removed()).count(), 4L,
                "all excluded native stacks remain available");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void hunterKeepsPendingDropsAndPartialNativeRemaindersFinite(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.IRON_SWORD);
        fill(helper, fixture);
        supply(helper, fixture, HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.LEATHER, 63));
        var item = item(helper, fixture, new ItemStack(Items.LEATHER, 5));
        var state = record(helper, fixture, item);
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.LEATHER), 64, "only one unit fits in the existing output stack");
        helper.assertValueEqual(state.drop().item(helper.getLevel().registryAccess()).getCount(), 4, "the native remainder retains exactly four units");
        work(helper, fixture);
        helper.assertValueEqual(state.drop().item(helper.getLevel().registryAccess()).getCount(), 4, "full output cannot silently discard loot");
        supply(helper, fixture, HiredJobInventory.MAIN_GRID_START, ItemStack.EMPTY);
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.LEATHER), 4, "the surviving native remainder is collected after capacity returns");
        var pending = SettlementItemDrop.create(item(helper, fixture, new ItemStack(Items.BONE, 2)), fixture.area().id(), 0L);
        pending.access(fixture.worker(), 0);
        fixture.town().recordItemDrop(pending);
        supply(helper, fixture, HiredJobInventory.MAIN_GRID_START + 1, ItemStack.EMPTY);
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.BONE), 2, "pending actual death loot uses the same hunter collection path");
        helper.assertFalse(fixture.town().itemDrops().containsKey(pending.id()), "the pending source is consumed once");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void hunterRequiresAWeaponAndUsesNativeWeaponRanking(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.STICK);
        var item = record(helper, fixture, item(helper, fixture, new ItemStack(Items.BEEF, 2)));
        work(helper, fixture);
        helper.assertValueEqual(fixture.worker().status(), "missing_hunting_weapon", "a tool outside the native hunting set cannot start work");
        helper.assertFalse(item.removed(), "missing equipment preserves the loot");
        supply(helper, fixture, HiredJobInventory.HOTBAR_START, new ItemStack(Items.STONE_SWORD));
        var axe = new ItemStack(Items.DIAMOND_AXE);
        axe.setDamageValue(43);
        supply(helper, fixture, HiredJobInventory.HOTBAR_START + 1, axe);
        work(helper, fixture);
        var equipped = fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT);
        helper.assertTrue(equipped.is(Items.DIAMOND_AXE), "the same native ranking selects the best carried hunting weapon");
        helper.assertValueEqual(equipped.getDamageValue(), 43, "equipping and collecting loot preserve actual durability");
        helper.assertValueEqual(count(helper, fixture, Items.BEEF), 2, "the equipped hunter collects the finite loot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void hunterFetchesOneActualWeaponWithoutReplayingTheWithdrawal(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.STICK);
        var depot = depot(fixture, true);
        var weapon = new ItemStack(Items.IRON_SWORD);
        weapon.setDamageValue(37);
        depot.setItem(0, weapon);
        work(helper, fixture);
        var equipped = fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT);
        helper.assertTrue(equipped.is(Items.IRON_SWORD), "the hunter equips the actual depot weapon");
        helper.assertValueEqual(equipped.getDamageValue(), 37, "depot withdrawal preserves weapon components");
        helper.assertTrue(depot.getItem(0).isEmpty(), "the weapon leaves finite depot stock");
        helper.assertValueEqual(count(helper, fixture, Items.STICK), 1, "the former held item remains in the worker inventory");
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.IRON_SWORD), 1, "another work step cannot duplicate the withdrawn weapon");
        helper.assertFalse(helper.getLevel().hasChunkAt(depot.position()), "recorded access does not load the depot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void hunterRestocksFiniteAmmoAndPreservesTheNativeReturnInventory(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.BOW);
        var depot = depot(fixture, true);
        depot.setItem(0, new ItemStack(Items.SPECTRAL_ARROW, 64));
        depot.setItem(1, new ItemStack(Items.SPECTRAL_ARROW, 16));
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.SPECTRAL_ARROW), 64, "one restock uses the native stack limit");
        helper.assertValueEqual(depot.count(stack -> stack.is(Items.SPECTRAL_ARROW)), 16, "the depot retains the exact finite remainder");
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.SPECTRAL_ARROW), 64, "a ready weapon does not reserve or withdraw more ammunition");
        var returning = fixture.villager();
        helper.assertTrue(fixture.worker().restore(returning), "native return receives the pending work revision");
        helper.assertValueEqual(HiredJobInventory.getJobInventory(returning).findSupply(HiredRangedAmmo::isAmmo).getCount(), 64,
                "the real native inventory receives the stored ammunition");
        helper.assertFalse(fixture.worker().restore(returning), "the same native return revision cannot apply twice");
        var saved = SettlementWorkerState.load(fixture.worker().save());
        helper.assertValueEqual(saved.items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(Items.SPECTRAL_ARROW))
                .mapToInt(ItemStack::getCount).sum(), 64, "saving preserves real ammunition instead of an abstract readiness flag");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void unknownDepotAccessAndDisabledSupplyUseCannotWithdrawAmmo(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.CROSSBOW);
        var depot = depot(fixture, false);
        depot.setItem(0, new ItemStack(Items.ARROW, 7));
        work(helper, fixture);
        helper.assertValueEqual(fixture.worker().status(), "hunting_storage_unreachable", "unknown walking terrain blocks the supply trip");
        helper.assertValueEqual(depot.getItem(0).getCount(), 7, "an unobserved depot route preserves ammunition");
        SettlementExperienceTestTerrain.flat(fixture.town());
        fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.ARROW), 0, "the native storage toggle still blocks withdrawal after the route is known");
        fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.ARROW), 7, "the same finite stock becomes available after access is enabled");
        helper.assertTrue(depot.getItem(0).isEmpty(), "successful preparation removes the actual source arrows");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void fullSupplyInventoryAndInsufficientWorkKeepDepotAmmoIntact(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.BOW);
        var depot = depot(fixture, true);
        depot.setItem(0, new ItemStack(Items.TIPPED_ARROW, 5));
        fill(helper, fixture);
        work(helper, fixture);
        helper.assertValueEqual(fixture.worker().status(), "supply_full", "ammunition requires actual inventory capacity");
        helper.assertValueEqual(depot.getItem(0).getCount(), 5, "failed supply insertion cannot consume arrows");
        supply(helper, fixture, HiredJobInventory.HOTBAR_START, ItemStack.EMPTY);
        SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
        SettlementBackgroundHunting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 1, 1.0D);
        helper.assertValueEqual(depot.getItem(0).getCount(), 5, "an unfinished supply trip cannot consume arrows");
        helper.assertValueEqual(count(helper, fixture, Items.TIPPED_ARROW), 0, "an unfinished trip cannot retain its trial insertion");
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.TIPPED_ARROW), 5, "available work and capacity complete the finite transfer");
        helper.assertTrue(depot.getItem(0).isEmpty(), "the completed transfer consumes exactly its source stock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void carriedOutputAmmoIsPromotedByTheNativeInventoryRule(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.BOW);
        var inventory = HiredJobInventory.getJobInventory(fixture.villager());
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.HOTBAR_START; slot++) {
            inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        helper.assertTrue(inventory.insertOutput(new ItemStack(Items.ARROW, 6)).isEmpty(), "hunting loot occupies an overflow output slot");
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        work(helper, fixture);
        var owner = fixture.worker().context(helper.getLevel(), fixture.town());
        helper.assertValueEqual(HiredJobInventory.getJobInventory(owner).findSupply(HiredRangedAmmo::isAmmo).getCount(), 6,
                "the native inventory promotion makes carried loot usable ammunition");
        helper.assertValueEqual(count(helper, fixture, Items.ARROW), 6, "promotion cannot create or consume ammunition");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void huntingLootHonorsNativePathAvoidanceAndUnknownRoutes(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.IRON_SWORD);
        var item = item(helper, fixture, new ItemStack(Items.BEEF, 2));
        var state = record(helper, fixture, item);
        var owner = fixture.villager();
        for (int attempt = 0; attempt < 16; attempt++) HiredPathMemory.recordFailure(helper.getLevel(), owner, item.blockPosition());
        try {
            work(helper, fixture);
            helper.assertFalse(state.removed(), "the native path-avoidance cooldown remains active while unloaded");
            HiredPathMemory.clearAvoided(owner, item.blockPosition());
            item.setPos(fixture.town().center().east(8).getBottomCenter());
            fixture.town().removeGroundItem(item.getUUID());
            state = SettlementGroundItem.capture(item, fixture.area().id(), 0L);
            fixture.town().recordGroundItem(state);
            work(helper, fixture);
            helper.assertValueEqual(fixture.worker().status(), "hunting_loot_unreachable", "an unknown route cannot collect distant loot");
            helper.assertFalse(state.removed(), "distant unobserved loot remains finite and uncollected");
            state.drop().access(fixture.worker(), 8);
            work(helper, fixture);
            helper.assertTrue(state.removed(), "recorded physical access allows later collection");
        } finally {
            HiredPathMemory.clearAvoided(owner, fixture.town().center().east());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void restDisabledWorkAndLegacyMissingContextPreserveHuntingLoot(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.IRON_SWORD);
        var state = record(helper, fixture, item(helper, fixture, new ItemStack(Items.BEEF, 2)));
        long day = helper.getLevel().getDayTime();
        try {
            helper.getLevel().setDayTime(18_000L);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertFalse(state.removed(), "night rest takes priority over background hunting");
            helper.getLevel().setDayTime(6_000L);
            fixture.worker().settings().putBoolean("Enabled", false);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertFalse(state.removed(), "disabled worker settings block collection");
            var saved = fixture.worker().save();
            saved.remove("EntityContext");
            var legacy = SettlementWorkerState.load(saved);
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            SettlementBackgroundHunting.tick(helper.getLevel(), fixture.town(), legacy, 200, 1.0D);
            helper.assertValueEqual(legacy.status(), "awaiting_worker_observation", "old hunter saves wait for a real worker context");
            helper.assertFalse(state.removed(), "missing legacy context cannot invent a worker or collect resources");
        } finally {
            helper.getLevel().setDayTime(day);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void activeHunterOwnsPickupAndBackgroundObservationCannotRepeatIt(GameTestHelper helper) {
        var fixture = fixture(helper, true, Items.IRON_SWORD);
        var level = helper.getLevel();
        var villager = fixture.villager();
        var item = item(helper, fixture, new ItemStack(Items.LEATHER, 3));
        helper.assertTrue(level.addFreshEntity(villager) && level.addFreshEntity(item), "the actual hunter and loot enter the native world");
        long day = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            helper.assertTrue(level.isPositionEntityTicking(villager.blockPosition()), "the native hunter owns its active interval");
            SettlementBackgroundService.update(level, fixture.town(), 200L);
            helper.assertValueEqual(item.getItem().getCount(), 3, "background observation leaves native pickup to the active hunter");
            var inventory = HiredJobInventory.getJobInventory(villager);
            var context = new HiredWorkContext(inventory, HiredWorkStateStore.state(villager), fixture.town().center(),
                    fixture.area().min(), fixture.area().max(), 12, 6, true, 100, false, true);
            new HuntingWorker().tick(level, villager, null, context);
            helper.assertTrue(item.isRemoved(), "the actual native hunting worker collects its loot");
            SettlementBackgroundService.update(level, fixture.town(), 400L);
            helper.assertValueEqual(count(helper, fixture, Items.LEATHER), 3, "the next observation records exactly the native pickup");
        } finally {
            villager.discard();
            item.discard();
            level.setDayTime(day);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void currentNativeObstructionsBlockPreviouslyObservedDepotAccess(GameTestHelper helper) {
        var fixture = fixture(helper, true, Items.BOW);
        var level = helper.getLevel();
        var center = fixture.town().center();
        var depot = depot(fixture, true);
        SettlementTestPlots.activateDepot(level, fixture.town(), depot);
        depot.setItem(0, new ItemStack(Items.ARROW, 2));
        for (var floor : BlockPos.betweenClosed(center.offset(-5, -1, -5), center.offset(8, -1, 5))) {
            level.setBlockAndUpdate(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy < 3; dy++) {
                level.setBlockAndUpdate(center.relative(direction).above(dy), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
        }
        work(helper, fixture);
        helper.assertValueEqual(fixture.worker().status(), "hunting_storage_unreachable", "current native walls override an older clear terrain observation");
        helper.assertValueEqual(depot.getItem(0).getCount(), 2, "blocked access cannot withdraw ammunition");
        for (int dy = 0; dy < 3; dy++) level.setBlockAndUpdate(center.east().above(dy), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.ARROW), 2, "removing the real obstruction permits a later finite withdrawal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void configuredAutoDepositTransfersActualHuntingOutput(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.IRON_SWORD);
        var depot = depot(fixture, false);
        depot.configure(SettlementDepotPurpose.GENERAL, 0);
        record(helper, fixture, item(helper, fixture, new ItemStack(Items.BEEF, 4)));
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.BEEF), 4, "hunting first collects the actual drop");
        fixture.worker().settings().putBoolean("AutoDepositOutputs", true);
        work(helper, fixture);
        helper.assertValueEqual(fixture.worker().status(), "hunting_storage_unreachable", "an unknown output route reports its actual blocker");
        helper.assertValueEqual(count(helper, fixture, Items.BEEF), 4, "inaccessible storage preserves carried output");
        SettlementExperienceTestTerrain.flat(fixture.town());
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.BEEF), 0, "automatic deposit removes the carried output");
        helper.assertValueEqual(depot.count(stack -> stack.is(Items.BEEF)), 4, "automatic deposit adds the same finite output to town storage");
        work(helper, fixture);
        helper.assertValueEqual(depot.count(stack -> stack.is(Items.BEEF)), 4, "later work cannot repeat the completed deposit");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void hunterAmmoRespectsItsOwnFilterAndTheDepotReserve(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.BOW);
        var depot = depot(fixture, true);
        var filter = new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.ITEM_FILTER.get());
        com.jvn.villagerretaliation.item.VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.SPECTRAL_ARROW));
        supply(helper, fixture, HiredJobInventory.FILTER_SLOT, filter.copy());
        com.jvn.villagerretaliation.item.VillagerFilterPolicy.setDirection(filter,
                com.jvn.villagerretaliation.item.VillagerFilterPolicy.TransferDirection.PROVIDE);
        com.jvn.villagerretaliation.item.VillagerFilterPolicy.setStockTarget(filter, 2);
        depot.observeRules(List.of(filter));
        depot.setItem(0, new ItemStack(Items.ARROW, 3));
        depot.setItem(1, new ItemStack(Items.SPECTRAL_ARROW, 8));
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.ARROW), 0, "the hunter's own filter rejects other ammunition");
        helper.assertValueEqual(count(helper, fixture, Items.SPECTRAL_ARROW), 6, "only stock above the framed reserve can be withdrawn");
        helper.assertValueEqual(depot.getItem(1).getCount(), 2, "the town's configured reserve remains intact");
        helper.assertValueEqual(depot.getItem(0).getCount(), 3, "rejected ammunition remains in its source slot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_supplies")
    public static void hunterWeaponWithdrawalRespectsItsAssignedItemFilter(GameTestHelper helper) {
        var fixture = fixture(helper, false, Items.STICK);
        var depot = depot(fixture, true);
        depot.setItem(0, new ItemStack(Items.IRON_SWORD));
        var filter = new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.ITEM_FILTER.get());
        com.jvn.villagerretaliation.item.VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.ARROW));
        supply(helper, fixture, HiredJobInventory.FILTER_SLOT, filter);
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.IRON_SWORD), 0, "native withdrawal filtering applies to depot weapons too");
        helper.assertFalse(depot.getItem(0).isEmpty(), "a rejected weapon remains town stock");
        supply(helper, fixture, HiredJobInventory.FILTER_SLOT, ItemStack.EMPTY);
        work(helper, fixture);
        helper.assertValueEqual(count(helper, fixture, Items.IRON_SWORD), 1, "clearing the restriction permits one actual weapon transfer");
        helper.assertTrue(depot.getItem(0).isEmpty(), "the accepted weapon is removed from its source");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, boolean loaded, Item weapon) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var center = loaded ? origin : origin.offset(13_000_000, 0, 13_000_000).atY(80);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HUNTING_GROUNDS, "Hunting grounds",
                center.offset(-2, -1, -2), center.offset(10, 4, 10), 5, true);
        town.putArea(town.leader(), area);
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getBottomCenter());
        villager.setOnGround(true);
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FLETCHER));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(weapon));
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        return new Fixture(town, area, worker, villager);
    }

    private static SettlementDepot depot(Fixture fixture, boolean observedTerrain) {
        var center = fixture.town().center();
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Supplies",
                center.east(4), center.offset(6, 2, 2), 5, true);
        fixture.town().putArea(fixture.town().leader(), storage);
        var depot = fixture.town().registerDepot(fixture.town().leader(), storage.id(), storage.min(), SettlementDepotPurpose.MATERIALS);
        depot.observeRules(List.of());
        if (observedTerrain) SettlementExperienceTestTerrain.flat(fixture.town());
        return depot;
    }

    private static ItemEntity item(GameTestHelper helper, Fixture fixture, ItemStack stack) {
        var position = fixture.town().center().east().getBottomCenter();
        var item = new ItemEntity(helper.getLevel(), position.x, position.y, position.z, stack);
        item.setNoGravity(true);
        item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        return item;
    }

    private static SettlementGroundItem record(GameTestHelper helper, Fixture fixture, ItemEntity item) {
        var state = SettlementGroundItem.capture(item, fixture.area().id(), 0L);
        state.drop().access(fixture.worker(), 0);
        fixture.town().recordGroundItem(state);
        return state;
    }

    private static void work(GameTestHelper helper, Fixture fixture) {
        SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
        SettlementBackgroundHunting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
    }

    private static void supply(GameTestHelper helper, Fixture fixture, int slot, ItemStack stack) {
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        items.set(slot, stack);
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
    }

    private static void fill(GameTestHelper helper, Fixture fixture) {
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            items.set(slot, new ItemStack(Items.STONE, 64));
        }
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
    }

    private static int count(GameTestHelper helper, Fixture fixture, Item item) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementWorkerState worker, Villager villager) {
    }
}
