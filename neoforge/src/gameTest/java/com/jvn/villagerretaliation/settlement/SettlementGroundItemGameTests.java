package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementGroundItemGameTests {
    private SettlementGroundItemGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void unloadedNativeEggsAreFiniteAndTheirOldBodiesCannotReturn(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var item = item(helper, fixture, 3);
        var state = record(helper, fixture, item);
        var original = item.saveWithoutId(new CompoundTag());
        work(helper, fixture);
        helper.assertValueEqual(carried(helper, fixture), 3, "the observed native stack is collected once");
        helper.assertTrue(state.removed(), "the consumed native stack keeps a removal record while unloaded");
        work(helper, fixture);
        helper.assertValueEqual(carried(helper, fixture), 3, "the removal record cannot be collected again");
        var returning = item(helper, fixture, 1);
        returning.load(original);
        helper.assertFalse(SettlementGroundItemService.onActive(returning), "an old native body is removed before it can tick");
        helper.assertTrue(returning.isRemoved(), "native return applies background consumption");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "collection and return checks do not force chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void partialLoadedPickupAndChunkReceiptsPreserveOnlyTheRemainder(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var item = item(helper, fixture, 5);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "the native eggs enter the pen");
        try {
            SettlementGroundItemService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            var oldEntity = item.saveWithoutId(new CompoundTag());
            var oldState = fixture.town().groundItems().get(item.getUUID()).save();
            fillOutput(helper, fixture);
            int slot = fixture.worker().outputSlots().getFirst();
            supply(helper, fixture, slot, new ItemStack(Items.EGG, 15));
            work(helper, fixture);
            helper.assertValueEqual(carried(helper, fixture), 16, "only one egg fits");
            helper.assertValueEqual(item.getItem().getCount(), 4, "the actual native body retains four eggs");
            fixture.town().removeGroundItem(item.getUUID());
            fixture.town().recordGroundItem(SettlementGroundItem.load(oldState, helper.getLevel().registryAccess()));
            item.load(oldEntity);
            helper.assertTrue(SettlementGroundItemService.onJoined(item), "partial consumption permits the surviving body");
            helper.assertValueEqual(item.getItem().getCount(), 4, "a newer chunk receipt repairs an older town and entity snapshot");
            supply(helper, fixture, slot, ItemStack.EMPTY);
            work(helper, fixture);
            helper.assertValueEqual(carried(helper, fixture), 4, "the second pickup transfers exactly the remainder");
            helper.assertTrue(item.isRemoved(), "full consumption removes the actual item");
            var replay = item(helper, fixture, 1);
            replay.load(oldEntity);
            helper.assertFalse(helper.getLevel().addFreshEntity(replay), "the saved removal receipt rejects an old entity join");
        } finally {
            item.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void fullOutputAndUnobservedRoutesKeepNativeGroundItems(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var item = item(helper, fixture, 2);
        item.setPos(fixture.town().center().east(8).getBottomCenter());
        var state = record(helper, fixture, item);
        state.drop().access(fixture.worker(), -1);
        work(helper, fixture);
        helper.assertValueEqual(carried(helper, fixture), 0, "an unobserved route cannot collect the stack");
        helper.assertFalse(state.removed(), "an inaccessible native item remains intact");
        state.drop().access(fixture.worker(), 8);
        fillOutput(helper, fixture);
        work(helper, fixture);
        helper.assertFalse(state.removed(), "full storage leaves the ground item intact");
        supply(helper, fixture, fixture.worker().outputSlots().getFirst(), ItemStack.EMPTY);
        work(helper, fixture);
        helper.assertValueEqual(carried(helper, fixture), 2, "available capacity and a real route collect the same finite stack");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void nativeExpiryPersistsADeletionAcrossSaves(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var item = item(helper, fixture, 1);
        item.lifespan = 1;
        var state = record(helper, fixture, item);
        SettlementGroundItemService.update(helper.getLevel(), fixture.town(), 200L);
        helper.assertTrue(state.removed(), "background expiry records native removal");
        var saved = fixture.town().save(helper.getLevel().registryAccess());
        var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(restored.groundItems().get(item.getUUID()).removed(), "the deletion survives a town save");
        helper.assertFalse(SettlementGroundItemService.onActive(item), "the expired body cannot return");
        work(helper, fixture);
        helper.assertValueEqual(carried(helper, fixture), 0, "expired native eggs cannot be collected");
        saved.remove("GroundItems");
        helper.assertTrue(SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {}).groundItems().isEmpty(),
                "older settlement saves require no native-item ledger");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void nativeGroundItemsHaveNoOfflineCatchupOrDuplicateAging(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var state = record(helper, fixture, item(helper, fixture, 1));
        SettlementGroundItemService.update(helper.getLevel(), fixture.town(), 48_000L);
        helper.assertValueEqual(state.drop().context(helper.getLevel()).getAge(), 200, "a clock gap advances at most one running interval");
        long version = state.version();
        SettlementGroundItemService.update(helper.getLevel(), fixture.town(), 48_000L);
        helper.assertValueEqual(state.version(), version, "repeating an interval does not create another version");
        var loaded = SettlementGroundItem.load(state.save(), helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.drop().context(helper.getLevel()).getAge(), 200, "saved native aging survives reload");
        helper.assertValueEqual(loaded.drop().steps(fixture.worker()), 0, "observed access survives the same save");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void activeNativeItemsOwnTheirAgeAndExternalPickups(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var item = item(helper, fixture, 5);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "the active native stack is present");
        try {
            SettlementGroundItemService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            SettlementGroundItemService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime() + 200L);
            helper.assertValueEqual(item.getAge(), 0, "background simulation does not age an active entity");
            item.setItem(new ItemStack(Items.EGG, 2));
            work(helper, fixture);
            helper.assertValueEqual(carried(helper, fixture), 2, "collection observes another actor's partial native pickup");
            helper.assertTrue(item.isRemoved(), "only the surviving native quantity was consumed");
        } finally {
            item.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void nativeDiscardAndOldTownReplayCannotReviveTheItem(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var item = item(helper, fixture, 2);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "the actual item is present");
        SettlementGroundItemService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
        var saved = fixture.town().groundItems().get(item.getUUID()).save();
        item.discard();
        helper.assertFalse(fixture.town().groundItems().containsKey(item.getUUID()), "native removal releases its observed record");
        fixture.town().recordGroundItem(SettlementGroundItem.load(saved, helper.getLevel().registryAccess()));
        work(helper, fixture);
        helper.assertValueEqual(carried(helper, fixture), 0, "an older town save cannot credit a discarded native item");
        SettlementGroundItemService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime());
        helper.assertFalse(fixture.town().groundItems().containsKey(item.getUUID()), "the chunk receipt retires the stale record");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void stopTickingCapturesTheActualLastNativeQuantity(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var item = item(helper, fixture, 7);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "the native stack is present");
        try {
            SettlementGroundItemService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            item.setItem(new ItemStack(Items.EGG, 3));
            SettlementSimulationHandoff.stopTicking(item);
            var state = fixture.town().groundItems().get(item.getUUID());
            helper.assertValueEqual(state.drop().item(helper.getLevel().registryAccess()).getCount(), 3,
                    "the handoff records pickups since the last periodic observation");
            helper.assertValueEqual(state.drop().checkedAt(), helper.getLevel().getGameTime(), "the background clock starts at the native handoff");
        } finally {
            item.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void overlappingWorkplacesShareOneNativeItemLedger(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var other = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HUNTING_GROUNDS, "Hunt",
                fixture.area().min(), fixture.area().max(), 5, true);
        fixture.town().putArea(fixture.town().leader(), other);
        var item = item(helper, fixture, 2);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "one native stack is present in both designations");
        try {
            SettlementGroundItemService.observeArea(helper.getLevel(), fixture.town(), other);
            SettlementGroundItemService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            helper.assertValueEqual(fixture.town().groundItems().size(), 1, "overlapping areas do not copy the resource");
            work(helper, fixture);
            helper.assertValueEqual(carried(helper, fixture), 2, "the pen can collect the resource first observed by the other workplace");
        } finally {
            item.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "settlement_ground_item_chunks")
    public static void loadedNonTickingItemsContinueAgingAndCollecting(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var level = helper.getLevel();
        var position = new ChunkPos(fixture.town().center());
        level.getChunkSource().addRegionTicket(TicketType.FORCED, position, 0, position);
        level.getChunk(position.x, position.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(position.toLong()), "native entity storage is ready");
        }).thenExecute(() -> {
            var item = item(helper, fixture, 3);
            try {
                helper.assertFalse(level.isPositionEntityTicking(item.blockPosition()), "the viewing chunk is loaded without entity ticking");
                helper.assertTrue(level.addFreshEntity(item), "the native item enters the non-ticking chunk");
                SettlementGroundItemService.observeArea(level, fixture.town(), fixture.area());
                long now = level.getGameTime();
                SettlementGroundItemService.update(level, fixture.town(), now + 200L);
                helper.assertValueEqual(item.getAge(), 200, "background aging reaches the actual non-ticking native entity");
                SettlementGroundItemService.update(level, fixture.town(), now + 200L);
                helper.assertValueEqual(item.getAge(), 200, "repeated background update does not age twice");
                work(helper, fixture);
                helper.assertValueEqual(carried(helper, fixture), 3, "background pickup consumes the actual loaded stack");
                helper.assertTrue(item.isRemoved(), "collection removes the non-ticking native body");
            } finally {
                item.discard();
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, position, 0, position);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_ground_item_unload")
    public static void nativeChunkUnloadCollectionAndReloadCannotDuplicateEggs(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var level = helper.getLevel();
        var position = new ChunkPos(fixture.town().center());
        var item = item(helper, fixture, 4);
        level.getChunkSource().addRegionTicket(TicketType.FORCED, position, 0, position);
        level.getChunk(position.x, position.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(position.toLong()), "the native item chunk has loaded");
        }).thenExecute(() -> {
            helper.assertTrue(level.addFreshEntity(item), "the real native eggs enter their chunk");
            SettlementGroundItemService.observeArea(level, fixture.town(), fixture.area());
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, position, 0, position);
        }).thenWaitUntil(() -> {
            helper.assertFalse(level.areEntitiesLoaded(position.toLong()) || level.hasChunkAt(fixture.town().center()),
                    "the actual native item chunk unloads");
        }).thenExecute(() -> {
            work(helper, fixture);
            helper.assertValueEqual(carried(helper, fixture), 4, "the observed stack is collected while its real chunk is absent");
            helper.assertTrue(fixture.town().groundItems().get(item.getUUID()).removed(), "consumption awaits native-body reconciliation");
            level.getChunkSource().addRegionTicket(TicketType.FORCED, position, 0, position);
            level.getChunk(position.x, position.z);
        }).thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(position.toLong()), "the original entity storage reloads");
        }).thenExecute(() -> {
            try {
                helper.assertTrue(level.getEntity(item.getUUID()) == null, "the consumed native item is rejected during real chunk reload");
                work(helper, fixture);
                helper.assertValueEqual(carried(helper, fixture), 4, "reloaded terrain cannot repeat the pickup");
            } finally {
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, position, 0, position);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void externallyEmptiedNativeStacksCannotReappearFromReceipts(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var item = item(helper, fixture, 2);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "the native stack is present");
        try {
            SettlementGroundItemService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            var saved = item.saveWithoutId(new CompoundTag());
            item.setItem(ItemStack.EMPTY);
            SettlementGroundItemService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime());
            var replay = item(helper, fixture, 1);
            replay.load(saved);
            helper.assertFalse(SettlementGroundItemService.onJoined(replay), "an empty native stack leaves a removal receipt");
            work(helper, fixture);
            helper.assertValueEqual(carried(helper, fixture), 0, "another actor's full pickup cannot become background output");
        } finally {
            item.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void unloadingCapturesDropsCreatedAfterTheLastAreaSurvey(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var item = item(helper, fixture, 3);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "a new native drop arrives after the workplace survey");
        try {
            helper.assertFalse(fixture.town().groundItems().containsKey(item.getUUID()), "the new item has not yet been surveyed");
            SettlementSimulationHandoff.stopTicking(item);
            var state = fixture.town().groundItems().get(item.getUUID());
            helper.assertTrue(state != null, "the actual entity handoff captures the unsurveyed drop");
            helper.assertValueEqual(state.drop().item(helper.getLevel().registryAccess()).getCount(), 3, "handoff records the finite native stack");
            helper.assertValueEqual(state.drop().steps(fixture.worker()), 0, "the last loaded handoff observes actual pickup access");
        } finally {
            item.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ground_items")
    public static void changingWorkplacesRequiresAnItemRouteForTheNewArea(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var item = item(helper, fixture, 1);
        item.setPos(fixture.town().center().east(8).getBottomCenter());
        var state = record(helper, fixture, item);
        state.drop().access(fixture.worker(), 8);
        var workerTag = fixture.worker().save();
        workerTag.putUUID("Area", UUID.randomUUID());
        var reassigned = SettlementWorkerState.load(workerTag);
        helper.assertValueEqual(state.drop().steps(reassigned), -1, "a route through the old workplace does not authorize pickup in the new one");
        var saved = state.save();
        var restored = SettlementGroundItem.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.drop().steps(fixture.worker()), 8, "the original area's observed route survives saving");
        helper.assertValueEqual(restored.drop().steps(reassigned), -1, "saving does not erase the route's area boundary");
        var legacy = state.drop().save();
        for (var raw : legacy.getList("Access", net.minecraft.nbt.Tag.TAG_COMPOUND)) ((CompoundTag) raw).remove("Area");
        helper.assertValueEqual(SettlementItemDrop.load(legacy, helper.getLevel().registryAccess()).steps(fixture.worker()), 8,
                "older drop saves retain access within their recorded source area");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, boolean loaded) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var center = loaded ? origin : origin.offset(9_000_000, 0, 9_000_000).atY(80);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.ANIMAL_PEN, "Pen",
                center.offset(-2, -1, -2), center.offset(10, 4, 10), 5, true);
        town.putArea(town.leader(), area);
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getBottomCenter());
        villager.setOnGround(true);
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        return new Fixture(town, area, worker);
    }

    private static ItemEntity item(GameTestHelper helper, Fixture fixture, int count) {
        var position = fixture.town().center().east().getBottomCenter();
        var item = new ItemEntity(helper.getLevel(), position.x, position.y, position.z, new ItemStack(Items.EGG, count));
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
        for (int step = 0; step < 8; step++) SettlementBackgroundAnimalHandling.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
    }

    private static int carried(GameTestHelper helper, Fixture fixture) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream()
                .filter(stack -> stack.is(Items.EGG)).mapToInt(ItemStack::getCount).sum();
    }

    private static void fillOutput(GameTestHelper helper, Fixture fixture) {
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            if (items.get(slot).isEmpty()) items.set(slot, new ItemStack(Items.STONE, 64));
        }
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
    }

    private static void supply(GameTestHelper helper, Fixture fixture, int slot, ItemStack item) {
        var items = fixture.worker().items(helper.getLevel().registryAccess());
        items.set(slot, item);
        fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementWorkerState worker) {
    }
}
