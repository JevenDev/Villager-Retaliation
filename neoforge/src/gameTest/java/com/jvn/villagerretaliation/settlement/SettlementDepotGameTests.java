package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.SettlementDepotBlockEntity;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.mojang.authlib.GameProfile;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementDepotGameTests {
    private SettlementDepotGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void depotCapacityAndComponentsSurviveReload(GameTestHelper helper) {
        AtomicInteger writes = new AtomicInteger();
        var depot = new SettlementDepot(UUID.randomUUID(), UUID.randomUUID(), BlockPos.ZERO,
                SettlementDepotPurpose.GENERAL, 5, writes::incrementAndGet);
        for (int slot = 0; slot < SettlementDepot.SLOT_COUNT; slot++) {
            depot.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        helper.assertValueEqual(depot.deposit(new ItemStack(Items.STONE, 7)).getCount(), 7,
                "a full depot returns all excess items");
        helper.assertValueEqual(depot.count(stack -> stack.is(Items.STONE)), 3_456, "storage is finite");
        helper.assertValueEqual(depot.withdraw(stack -> stack.is(Items.STONE), 8).getCount(), 8,
                "withdrawal removes real items");
        ItemStack named = new ItemStack(Items.BREAD, 4);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Town bread"));
        depot.setItem(1, named);
        int beforeLoad = writes.get();
        var restored = SettlementDepot.load(depot.save(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess(), writes::incrementAndGet);
        helper.assertValueEqual(writes.get(), beforeLoad, "loading does not mutate the saved town revision");
        helper.assertValueEqual(restored.getItem(1).getHoverName().getString(), "Town bread", "item components persist");
        helper.assertValueEqual(restored.getItem(0).getCount(), 56, "withdrawn stock is not recreated on load");
        helper.assertTrue(restored.configure(SettlementDepotPurpose.FOOD, 9), "purpose and priority are configurable");
        helper.assertValueEqual(restored.deposit(new ItemStack(Items.STONE, 2)).getCount(), 2,
                "food depots reject new non-food stock without destroying it");
        helper.assertValueEqual(restored.getItem(2).getCount(), 64, "changing purpose preserves existing stock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void depotBlockAndBackgroundLedgerShareOneInventory(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var ledger = fixture.block().inventory();
        fixture.block().setItem(0, new ItemStack(Items.BREAD, 12));
        helper.assertValueEqual(ledger.count(stack -> stack.is(Items.BREAD)), 12, "block deposits reach the ledger");
        ledger.withdraw(stack -> stack.is(Items.BREAD), 5);
        helper.assertValueEqual(fixture.block().getItem(0).getCount(), 7, "background withdrawals reach the block");
        CompoundTag blockTag = fixture.block().saveWithFullMetadata(helper.getLevel().registryAccess());
        helper.assertFalse(blockTag.contains("Items"), "the chunk stores a reference rather than a second inventory");
        var reloaded = new SettlementDepotBlockEntity(fixture.block().getBlockPos(), fixture.block().getBlockState());
        reloaded.setLevel(helper.getLevel());
        reloaded.loadWithComponents(blockTag, helper.getLevel().registryAccess());
        helper.assertTrue(reloaded.inventory() == ledger, "a chunk reload resolves the existing ledger");
        var copiedElsewhere = new SettlementDepotBlockEntity(fixture.block().getBlockPos().east(), fixture.block().getBlockState());
        copiedElsewhere.setLevel(helper.getLevel());
        copiedElsewhere.loadWithComponents(blockTag, helper.getLevel().registryAccess());
        helper.assertTrue(copiedElsewhere.inventory() == null, "copied block data cannot expose inventory at another location");
        var data = SettlementSavedData.get(helper.getLevel());
        var restored = SettlementSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        var savedDepot = restored.get(fixture.town().id()).orElseThrow().depots().get(ledger.id());
        helper.assertValueEqual(savedDepot.getItem(0).getCount(), 7, "world reload preserves the reconciled stock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void depotAccessUsesIndependentLogisticsPermission(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ServerPlayer viewer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DepotViewer"));
        viewer.setPos(fixture.leader().position());
        fixture.town().setPermissions(fixture.leader().getUUID(), viewer.getUUID(), Set.of(SettlementPermission.VIEW));
        helper.assertFalse(fixture.block().stillValid(viewer), "view permission does not grant stock access");
        fixture.town().setPermissions(fixture.leader().getUUID(), viewer.getUUID(), Set.of(SettlementPermission.LOGISTICS));
        helper.assertTrue(fixture.block().stillValid(viewer), "logistics permission grants stock access");
        fixture.town().setPermissions(fixture.leader().getUUID(), viewer.getUUID(), Set.of());
        helper.assertFalse(fixture.block().stillValid(viewer), "revoking permission invalidates an open depot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void depotDestructionDropsStockOnceAndRemovesLedger(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        BlockPos pos = fixture.block().getBlockPos();
        fixture.block().setItem(0, new ItemStack(Items.BREAD, 9));
        UUID depotId = fixture.block().inventory().id();
        helper.getLevel().removeBlock(pos, false);
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0D)).stream()
                .map(ItemEntity::getItem).filter(stack -> stack.is(Items.BREAD)).mapToInt(ItemStack::getCount).sum();
        helper.assertValueEqual(dropped, 9, "breaking the depot drops its real contents once");
        helper.assertFalse(fixture.town().depots().containsKey(depotId), "the old ledger cannot supply background work");
        helper.getLevel().setBlockAndUpdate(pos, VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
        helper.assertTrue(SettlementDepotService.register(fixture.leader(), pos), "a replacement depot can be registered");
        var replacement = (SettlementDepotBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(replacement.isEmpty(), "a replacement does not restore the destroyed stock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicStorageUsesTownDepotsAndIgnoresPersonalChests(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.setPos(fixture.leader().position());
        villager.setNoAi(true);
        level.addFreshEntity(villager);
        VillageAllegianceApi.assignKnown(level, villager, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertTrue(SettlementWorkforceService.assign(level, fixture.leader(), fixture.town().id(), villager,
                fixture.area().id()), "the farmer is assigned to the town");
        fixture.block().setItem(0, new ItemStack(Items.BREAD, 9));
        BlockPos personalPos = fixture.block().getBlockPos().offset(3, 0, 0);
        helper.setBlock(new BlockPos(5, 2, 2), Blocks.CHEST);
        var personal = (net.minecraft.world.Container) level.getBlockEntity(personalPos);
        personal.setItem(0, new ItemStack(Items.BREAD, 32));
        AssignedStorageSavedData.get(level).assign(new AssignedContainerRecord(level.dimension(), personalPos,
                villager.getUUID(), fixture.leader().getUUID(), AssignedStorageService.GENERAL_PURPOSE, 0, "valid"));
        helper.assertValueEqual(AssignedStorageService.countItems(villager, stack -> stack.is(Items.BREAD)), 9,
                "civic workers count the depot once and ignore personal chest references");
        helper.assertValueEqual(AssignedStorageService.consumeItems(villager, stack -> stack.is(Items.BREAD), 3), 3,
                "existing supply transfers withdraw real depot items");
        helper.assertValueEqual(fixture.block().getItem(0).getCount(), 6, "the ledger reflects worker consumption");
        helper.assertValueEqual(personal.getItem(0).getCount(), 32, "personal stock remains untouched");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void buildingChangesCannotOrphanDepotStock(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.block().setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        var data = SettlementSavedData.get(helper.getLevel());
        var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        helper.assertFalse(data.removeArea(registry, fixture.town().id(), fixture.leader().getUUID(), fixture.area().id()),
                "an area containing a depot must be emptied or moved before removal");
        var home = new SettlementArea(fixture.area().id(), SettlementAreaType.HOME, "Home",
                fixture.area().min(), fixture.area().max(), 5, true);
        helper.assertFalse(data.putArea(registry, fixture.town().id(), fixture.leader().getUUID(), home),
                "housing conversion cannot orphan the depot");
        helper.assertValueEqual(fixture.block().getItem(0).getCount(), 8, "rejected changes retain the stock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void existingStorageReferencesRespectPermissionAndPurposeChanges(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var level = helper.getLevel();
        Villager worker = EntityType.VILLAGER.create(level);
        worker.setPos(fixture.leader().position());
        worker.setNoAi(true);
        level.addFreshEntity(worker);
        UUID delegate = UUID.randomUUID();
        fixture.town().setPermissions(fixture.leader().getUUID(), delegate, Set.of(SettlementPermission.LOGISTICS));
        fixture.block().setItem(0, new ItemStack(Items.BREAD, 8));
        AssignedStorageSavedData.get(level).assign(new AssignedContainerRecord(level.dimension(),
                fixture.block().getBlockPos(), worker.getUUID(), delegate, AssignedStorageService.SUPPLY_PURPOSE, 0, "valid"));
        helper.assertValueEqual(AssignedStorageService.countItems(worker, stack -> stack.is(Items.BREAD)), 8,
                "an authorized supply reference can read town stock");
        fixture.town().configureDepot(fixture.leader().getUUID(), fixture.block().inventory().id(),
                SettlementDepotPurpose.OUTPUT, 5);
        helper.assertValueEqual(AssignedStorageService.countItems(worker, stack -> stack.is(Items.BREAD)), 0,
                "changing depot purpose immediately blocks stale supply references");
        fixture.town().configureDepot(fixture.leader().getUUID(), fixture.block().inventory().id(),
                SettlementDepotPurpose.GENERAL, 5);
        fixture.town().setPermissions(fixture.leader().getUUID(), delegate, Set.of());
        helper.assertValueEqual(AssignedStorageService.countItems(worker, stack -> stack.is(Items.BREAD)), 0,
                "revoked logistics access also blocks existing worker references");
        helper.assertValueEqual(fixture.block().getItem(0).getCount(), 8, "rejected access preserves inventory");
        worker.discard();
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "DepotLeader"));
        leader.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var data = SettlementSavedData.get(level);
        var town = data.establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Farm",
                center.offset(-1, -1, -1), center.offset(5, 3, 5), 5, true);
        helper.assertTrue(data.putArea(registry, id, leader.getUUID(), area), "the work area is registered");
        BlockPos depotPos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(depotPos, VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
        helper.assertTrue(SettlementDepotService.register(leader, depotPos), "the dedicated depot is registered");
        return new Fixture(town, area, leader, (SettlementDepotBlockEntity) level.getBlockEntity(depotPos));
    }

    private record Fixture(SettlementRecord town, SettlementArea area, ServerPlayer leader, SettlementDepotBlockEntity block) {
    }
}
