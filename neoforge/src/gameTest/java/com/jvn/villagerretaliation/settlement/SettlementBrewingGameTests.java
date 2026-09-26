package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementBrewingGameTests {
    private static final SettlementBrewingOrder HEALING = new SettlementBrewingOrder(
            ResourceLocation.withDefaultNamespace("potion"), ResourceLocation.withDefaultNamespace("healing"), 6, false);

    private SettlementBrewingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_brewing")
    public static void civicOrdersRequireStaffCurrentDutyAndAnActualRecipe(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BrewDelegate"));
        try {
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.LOGISTICS));
            helper.assertFalse(configure(delegate, fixture, HEALING), "inventory access cannot issue production orders");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
            helper.assertTrue(configure(delegate, fixture, HEALING), "staff can direct a civic brewer without a paid contract");
            var state = HiredWorkStateStore.state(fixture.villager());
            helper.assertValueEqual(SettlementBrewingOrder.read(state), HEALING, "the native brewer receives the requested output and quantity");
            helper.assertFalse(state.hasUUID("BrewingOrderContractId"), "civic work does not create a personal contract");
            helper.assertFalse(SettlementBrewingService.configure(delegate, fixture.town(), fixture.villager().getUUID(), UUID.randomUUID(), HEALING),
                    "stale workplaces cannot redirect production");
            helper.assertFalse(configure(delegate, fixture, new SettlementBrewingOrder(HEALING.item(), ResourceLocation.withDefaultNamespace("unknown"), 1, false)),
                    "unknown recipes are rejected by the server");
            helper.assertValueEqual(SettlementBrewingOrder.read(state), HEALING, "denied edits preserve the accepted order");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of());
            helper.assertFalse(configure(delegate, fixture, null), "revoked staff cannot cancel orders");
        } finally {
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_brewing")
    public static void remoteOrdersSurviveSavingWithoutReplacingInventoryReceipts(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var level = helper.getLevel();
        var worker = fixture.town().workers().get(fixture.villager().getUUID());
        var entitySave = new CompoundTag();
        fixture.villager().saveWithoutId(entitySave);
        var items = worker.items(level.registryAccess());
        items.set(HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.GLASS_BOTTLE, 7));
        worker.updateInventory(level.registryAccess(), items);
        worker.work(200, 1);
        helper.assertTrue(worker.spend(100), "the background inventory has a newer receipt");
        long inventoryVersion = worker.version();
        helper.assertTrue(configure(fixture.leader(), fixture, HEALING), "unloaded brewers accept production orders");
        helper.assertValueEqual(worker.version(), inventoryVersion, "ordering does not modify the inventory receipt");
        var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
        var savedWorker = restored.workers().get(fixture.villager().getUUID());
        Villager returning = EntityType.VILLAGER.create(level);
        returning.load(entitySave);
        savedWorker.restore(returning);
        helper.assertValueEqual(SettlementBrewingOrder.read(HiredWorkStateStore.state(returning)), HEALING, "the saved order reaches the returning brewer");
        helper.assertValueEqual(HiredJobInventory.getJobInventory(returning).getItem(HiredJobInventory.MAIN_GRID_START).getCount(), 7,
                "newer inventory and orders restore independently");
        HiredWorkStateStore.state(returning).putInt("BrewingRemaining", 2);
        savedWorker.restore(returning);
        helper.assertValueEqual(HiredWorkStateStore.state(returning).getInt("BrewingRemaining"), 2, "repeat reconciliation preserves active progress");
        BrewingWorker.clearOrder(HiredWorkStateStore.state(returning));
        savedWorker.restore(returning);
        helper.assertFalse(BrewingWorker.hasOrder(HiredWorkStateStore.state(returning)), "completed orders cannot be resurrected by old receipts");
        helper.assertFalse(level.hasChunkAt(fixture.town().center()), "remote ordering never loads town chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_brewing")
    public static void cancellingKeepsRealStandContentsAndWorkerSupplies(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        try {
            BlockPos position = fixture.area().min().offset(2, 0, 2);
            helper.getLevel().setBlockAndUpdate(position, Blocks.BREWING_STAND.defaultBlockState());
            var stand = (BrewingStandBlockEntity) helper.getLevel().getBlockEntity(position);
            stand.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
            stand.setItem(3, new ItemStack(Items.GLISTERING_MELON_SLICE, 2));
            var inventory = HiredJobInventory.getJobInventory(fixture.villager());
            inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.BLAZE_POWDER, 3));
            helper.assertTrue(configure(fixture.leader(), fixture, HEALING), "the brewer receives an order");
            helper.assertTrue(configure(fixture.leader(), fixture, null), "staff can stop that order");
            helper.assertFalse(BrewingWorker.hasOrder(HiredWorkStateStore.state(fixture.villager())), "the native order is stopped");
            helper.assertValueEqual(stand.getItem(0).getCount(), 1, "the bottle stays in the real stand");
            helper.assertValueEqual(stand.getItem(3).getCount(), 2, "loaded ingredients are neither consumed nor refunded");
            helper.assertValueEqual(inventory.getItem(HiredJobInventory.HOTBAR_START).getCount(), 3, "carried supplies stay with the worker");
        } finally {
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_brewing")
    public static void cancellationAndReassignmentDoNotReviveAnUnloadedOrder(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var continuous = new SettlementBrewingOrder(HEALING.item(), HEALING.potion(), 1, true);
        helper.assertTrue(configure(fixture.leader(), fixture, continuous), "continuous orders can be configured remotely");
        SettlementBackgroundService.onActive(fixture.villager());
        helper.assertValueEqual(SettlementBrewingOrder.read(HiredWorkStateStore.state(fixture.villager())), continuous, "continuous mode reaches the native worker");
        var entitySave = new CompoundTag();
        fixture.villager().saveWithoutId(entitySave);
        helper.assertTrue(configure(fixture.leader(), fixture, null), "remote cancellation is accepted");
        Villager returning = EntityType.VILLAGER.create(helper.getLevel());
        returning.load(entitySave);
        SettlementBackgroundService.onActive(returning);
        helper.assertFalse(BrewingWorker.hasOrder(HiredWorkStateStore.state(returning)), "remote cancellation replaces the older entity order");
        helper.assertTrue(configure(fixture.leader(), fixture, HEALING), "a new order can be accepted");
        helper.assertTrue(SettlementWorkforceService.release(helper.getLevel(), fixture.leader(), fixture.town().id(), fixture.villager().getUUID()),
                "the leader releases the absent brewer");
        SettlementBackgroundService.onActive(fixture.villager());
        helper.assertFalse(BrewingWorker.hasOrder(HiredWorkStateStore.state(fixture.villager())), "release clears the former civic order");
        fixture.town().assignDuty(fixture.villager().getUUID(), fixture.area().id());
        SettlementWorkforceService.reconcile(helper.getLevel(), fixture.villager());
        SettlementBackgroundService.onActive(fixture.villager());
        helper.assertFalse(BrewingWorker.hasOrder(HiredWorkStateStore.state(fixture.villager())), "reassigning the same workplace does not restart the cancelled order");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_brewing")
    public static void breweryRemovalClearsOrdersWithoutErasingLaterPersonalWork(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        try {
            helper.assertTrue(configure(fixture.leader(), fixture, HEALING), "the civic order is accepted");
            helper.assertTrue(fixture.town().removeArea(fixture.leader().getUUID(), fixture.area().id()), "the brewery can be removed");
            SettlementWorkforceService.reconcile(helper.getLevel(), fixture.villager());
            helper.assertFalse(BrewingWorker.hasOrder(HiredWorkStateStore.state(fixture.villager())), "removing the workplace stops its production order");
            var state = HiredWorkStateStore.state(fixture.villager());
            var contract = UUID.randomUUID();
            state.putUUID("SettlementBrewingArea", fixture.area().id());
            BrewingWorker.setOrder(state, HEALING.item(), HEALING.potion(), 9, false, contract);
            SettlementBrewingService.reconcile(fixture.villager());
            helper.assertValueEqual(state.getUUID("BrewingOrderContractId"), contract, "stale settlement markers cannot clear a newer personal order");
            helper.assertValueEqual(state.getInt("BrewingRemaining"), 9, "personal production remains intact");
            helper.assertFalse(state.hasUUID("SettlementBrewingArea"), "the old civic marker is discarded");
        } finally {
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_brewing")
    public static void managementRoundTripsOrdersAndRejectsInvalidQuantities(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var payload = new SettlementManagementActionPayload(fixture.town().id().value(),
                    new SettlementManagementActionPayload.Brew(fixture.villager().getUUID(), fixture.area().id(), HEALING));
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), payload, "brewing has a typed command codec");
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), payload), "management uses the validated production service");
            var snapshot = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(
                    fixture.town().id().value(), SettlementManagementRequestPayload.Page.RESIDENTS, 0, fixture.villager().getUUID().toString(), 1, false));
            SettlementManagementPayload.STREAM_CODEC.encode(buffer, snapshot);
            var decoded = SettlementManagementPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(((SettlementManagementPayload.Resident) decoded.entries().getFirst()).brewingOrder(), HEALING,
                    "the resident panel receives the current order");
            var cancel = new SettlementManagementActionPayload(fixture.town().id().value(),
                    new SettlementManagementActionPayload.Brew(fixture.villager().getUUID(), fixture.area().id(), null));
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, cancel);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), cancel, "cancellation round trips without a recipe");
        } finally {
            buffer.release();
        }
        for (int amount : new int[] {0, -1, SettlementBrewingOrder.MAX_AMOUNT + 1}) {
            boolean rejected = false;
            try {
                new SettlementBrewingOrder(HEALING.item(), HEALING.potion(), amount, false);
            } catch (IllegalArgumentException exception) {
                rejected = true;
            }
            helper.assertTrue(rejected, "orders reject invalid quantities before execution");
        }
        helper.succeed();
    }

    private static boolean configure(ServerPlayer player, Fixture fixture, SettlementBrewingOrder order) {
        return SettlementBrewingService.configure(player, fixture.town(), fixture.villager().getUUID(), fixture.area().id(), order);
    }

    private static Fixture fixture(GameTestHelper helper, boolean loaded) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var center = loaded ? origin : new BlockPos(10_000_000 + origin.getX() * 8, 64, 10_000_000 + origin.getZ() * 8);
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BrewLeader"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.BREWERY, "Brewery", center, center.offset(5, 3, 5), 5, true);
        helper.assertTrue(town.putArea(leader.getUUID(), area), "the brewery is registered");
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.CLERIC));
        VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
        if (loaded) {
            helper.assertTrue(level.addFreshEntity(villager), "the brewer is loaded");
        }
        helper.assertTrue(SettlementWorkforceService.assign(level, leader, id, villager, area.id()), "the cleric is assigned to brewing");
        town.recordWorker(SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L));
        SettlementCareService.onUnloaded(villager);
        return new Fixture(town, area, leader, villager);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, ServerPlayer leader, Villager villager) {
    }
}
