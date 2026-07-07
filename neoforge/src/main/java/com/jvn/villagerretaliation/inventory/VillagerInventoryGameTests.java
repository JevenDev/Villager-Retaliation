package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.ai.behavior.TradeWithVillager;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerInventoryGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final String JOB_INVENTORY_TAG = "VillagerRetaliationJobInventory";
    private static final String EXTRA_INVENTORY_TAG = "VillagerRetaliationExtraInventory";

    static {
        configureGameTestStructures();
    }

    private VillagerInventoryGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void jobInventoryCleansEmptyPersistenceAndMaintainsOnlyEquipment(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);

        inventory.setItem(6, new ItemStack(Items.COBBLESTONE));
        helper.assertTrue(
                villager.getPersistentData().contains(JOB_INVENTORY_TAG, Tag.TAG_COMPOUND),
                "non-empty job inventory should persist");
        helper.assertFalse(
                HiredJobInventory.hasJobEquipmentForSlot(villager, EquipmentSlot.MAINHAND),
                "supply-only job inventory should not control main hand");

        inventory.clearContent();
        helper.assertFalse(
                villager.getPersistentData().contains(JOB_INVENTORY_TAG, Tag.TAG_COMPOUND),
                "empty job inventory should remove its persistent tag");

        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(
                HiredJobInventory.hasJobEquipmentForSlot(villager, EquipmentSlot.MAINHAND),
                "saved job main hand should control the villager main hand");
        VillagerRetaliationVillagerEquipment.restoreMainHand(villager, ItemStack.EMPTY);
        HiredJobInventory.maintainEquipmentSlots(villager);
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_PICKAXE), "equipment maintenance should restore saved job gear");

        inventory.removeItemNoUpdate(HiredJobInventory.MAINHAND_SLOT);
        helper.assertFalse(
                HiredJobInventory.hasJobEquipmentForSlot(villager, EquipmentSlot.MAINHAND),
                "removed job gear should release main hand control");
        helper.assertFalse(
                villager.getPersistentData().contains(JOB_INVENTORY_TAG, Tag.TAG_COMPOUND),
                "removing the final job item should clean persistence");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void regularInventoryAddsItemsChecksCapacityAndRemovesEmptyExtraPersistence(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        ItemStack firstRemainder = VillagerInventoryContainer.addItem(villager, new ItemStack(Items.WHEAT, 40));
        ItemStack secondRemainder = VillagerInventoryContainer.addItem(villager, new ItemStack(Items.WHEAT, 30));
        helper.assertTrue(firstRemainder.isEmpty() && secondRemainder.isEmpty(), "regular inventory should accept wheat stacks");
        helper.assertValueEqual(countStored(villager, Items.WHEAT), 70, "regular inventory should merge matching stacks");

        NonNullList<ItemStack> fullInventory = NonNullList.withSize(VillagerInventoryContainer.INVENTORY_SLOT_COUNT, ItemStack.EMPTY);
        for (int slot = 0; slot < fullInventory.size(); slot++) {
            fullInventory.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        VillagerInventoryContainer.saveFullInventory(villager, fullInventory);
        helper.assertFalse(
                VillagerInventoryContainer.canAddItems(villager, List.of(new ItemStack(Items.DIRT))),
                "full regular inventory should reject more items");

        VillagerInventoryContainer.saveFullInventory(
                villager,
                NonNullList.withSize(VillagerInventoryContainer.INVENTORY_SLOT_COUNT, ItemStack.EMPTY));
        helper.assertFalse(
                villager.getPersistentData().contains(EXTRA_INVENTORY_TAG, Tag.TAG_COMPOUND),
                "empty extra inventory should not leave a persistent tag");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void regularInventoryBorrowedCombatWeaponReturnsToInventory(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        helper.assertTrue(
                VillagerInventoryContainer.addItem(villager, new ItemStack(Items.IRON_SWORD)).isEmpty(),
                "regular inventory should accept a combat weapon");
        helper.assertTrue(VillagerInventoryContainer.hasUsableWeapon(villager), "stored sword should count as usable");
        helper.assertTrue(VillagerInventoryContainer.tryBorrowCombatWeapon(villager), "villager should borrow stored sword");
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD), "borrowed sword should move to main hand");
        helper.assertValueEqual(countStored(villager, Items.IRON_SWORD), 0, "borrowed sword should leave storage while borrowed");

        VillagerInventoryContainer.returnBorrowedCombatWeapon(villager);
        helper.assertFalse(VillagerInventoryContainer.hasBorrowedCombatWeapon(villager), "borrowed state should clear after return");
        helper.assertTrue(villager.getMainHandItem().isEmpty(), "main hand should clear after borrowed sword returns");
        helper.assertValueEqual(countStored(villager, Items.IRON_SWORD), 1, "returned sword should be back in regular inventory");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void personalInventoryRespectsJobControlledEquipment(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        jobInventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));

        VillagerInventoryContainer personalInventory = new VillagerInventoryContainer(villager);
        helper.assertTrue(
                personalInventory.getItem(VillagerInventoryContainer.HELD_SLOT).isEmpty(),
                "personal inventory should hide job-controlled main hand");
        personalInventory.setItem(VillagerInventoryContainer.HELD_SLOT, new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_PICKAXE), "personal inventory should not replace job gear");

        jobInventory.removeItemNoUpdate(HiredJobInventory.MAINHAND_SLOT);
        personalInventory.refreshFromVillager();
        personalInventory.setItem(VillagerInventoryContainer.HELD_SLOT, new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(villager.getMainHandItem().is(Items.DIAMOND_SWORD), "personal inventory should control released main hand");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void regularInventoryMigratesLegacyOverflowOnlyOnce(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        int vanillaSlots = Math.min(
                VillagerInventoryContainer.INVENTORY_SLOT_COUNT,
                villager.getInventory().getContainerSize());
        int currentExtraSlots = Math.max(0, VillagerInventoryContainer.INVENTORY_SLOT_COUNT - vanillaSlots);
        NonNullList<ItemStack> legacyExtraInventory = NonNullList.withSize(currentExtraSlots + 1, ItemStack.EMPTY);
        legacyExtraInventory.set(currentExtraSlots, new ItemStack(Items.EMERALD, 3));
        CompoundTag legacyTag = ContainerHelper.saveAllItems(
                new CompoundTag(),
                legacyExtraInventory,
                true,
                level.registryAccess());
        villager.getPersistentData().put(EXTRA_INVENTORY_TAG, legacyTag);

        new VillagerInventoryContainer(villager);
        helper.assertValueEqual(
                countDroppedItems(level, villager.blockPosition(), Items.EMERALD),
                3,
                "legacy overflow should drop once on inventory load");
        helper.assertFalse(
                villager.getPersistentData().contains(EXTRA_INVENTORY_TAG, Tag.TAG_COMPOUND),
                "legacy overflow migration should clear stale extra inventory");

        new VillagerInventoryContainer(villager);
        helper.assertValueEqual(
                countDroppedItems(level, villager.blockPosition(), Items.EMERALD),
                3,
                "legacy overflow should not duplicate on a second inventory load");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void tradePreviewMixinPreservesVillagerMainHand(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrTradePreview");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        player.moveTo(villager.getX() + 1.0D, villager.getY(), villager.getZ(), 0.0F, 0.0F);
        villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, player);
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));

        ShowTradesToPlayer previewBehavior = new ShowTradesToPlayer(400, 1600);
        helper.assertFalse(
                previewBehavior.checkExtraStartConditions(level, villager),
                "trade preview behavior should not start for player-held trade items");
        helper.assertFalse(
                previewBehavior.canStillUse(level, villager, level.getGameTime()),
                "trade preview behavior should stop if already running");

        invokeShowTradesToPlayerDisplayAsHeldItem(villager, new ItemStack(Items.EMERALD));
        helper.assertTrue(villager.getMainHandItem().is(Items.DIAMOND_SWORD), "trade preview should not replace real main hand");

        invokeShowTradesToPlayerClearHeldItem(villager);
        helper.assertTrue(villager.getMainHandItem().is(Items.DIAMOND_SWORD), "trade preview cleanup should not clear real main hand");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void managedMainHandMaintenanceMovesDisplacedStackOnce(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            villager.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        VillagerRetaliationVillagerEquipment.setPickedUpMainHand(villager, new ItemStack(Items.DIAMOND_SWORD));

        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD, 3));
        helper.assertTrue(
                VillagerRetaliationVillagerEquipment.maintainPlayerManagedMainHand(villager),
                "managed main hand maintenance should restore tracked equipment");
        helper.assertTrue(villager.getMainHandItem().is(Items.DIAMOND_SWORD), "tracked weapon should return to main hand");
        helper.assertValueEqual(countStored(villager, Items.EMERALD), 3, "displaced stack should be stored once");
        helper.assertValueEqual(countStored(villager, Items.DIAMOND_SWORD), 0, "tracked weapon should not be copied into inventory");
        helper.assertValueEqual(countDroppedItems(level, villager.blockPosition(), Items.EMERALD), 0, "displaced stack should use extended inventory before dropping");

        VillagerRetaliationVillagerEquipment.maintainPlayerManagedMainHand(villager);
        helper.assertValueEqual(countStored(villager, Items.EMERALD), 3, "repeat maintenance should not duplicate displaced stack");
        helper.assertValueEqual(countStored(villager, Items.DIAMOND_SWORD), 0, "repeat maintenance should not duplicate tracked weapon");
        helper.assertValueEqual(countDroppedItems(level, villager.blockPosition(), Items.EMERALD), 0, "repeat maintenance should not drop duplicates");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerFoodSharingMixinKeepsBreadInInventory(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        Villager source = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager target = spawnVillager(helper, new BlockPos(2, 2, 1));
        source.getInventory().setItem(0, new ItemStack(Items.BREAD, 32));

        invokeTradeWithVillagerThrowHalfStack(source, Set.of(Items.BREAD), target);
        helper.assertValueEqual(source.getInventory().countItem(Items.BREAD), 32, "suppressed food sharing should not remove bread");
        helper.assertValueEqual(countDroppedItems(level, source.blockPosition(), Items.BREAD), 0, "suppressed food sharing should not spawn bread");
        source.discard();
        target.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void heroGiftMixinSuppressesDroppedGifts(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hero = fakePlayer(level, "VrGiftSuppress");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        invokeHeroGiftThrow(new GiveGiftToHero(100), villager, hero);
        helper.assertValueEqual(countDroppedItems(level, villager.blockPosition(), Items.WHEAT_SEEDS), 0, "suppressed hero gifts should not spawn items");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void jobInventoryDisplacesRegularMainHandWithoutDuplicating(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager,
                EquipmentSlot.MAINHAND,
                new ItemStack(Items.DIAMOND_SWORD));

        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        jobInventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_PICKAXE), "job gear should take main hand authority");
        helper.assertValueEqual(countStored(villager, Items.DIAMOND_SWORD), 1, "displaced personal weapon should be stored once");
        helper.assertValueEqual(countStored(villager, Items.IRON_PICKAXE), 0, "active job gear should not be copied into personal inventory");

        jobInventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        helper.assertValueEqual(countStored(villager, Items.IRON_PICKAXE), 0, "replacing job gear should not duplicate previous job gear");
        helper.assertValueEqual(countStored(villager, Items.DIAMOND_SWORD), 1, "personal weapon should remain stored exactly once");

        jobInventory.removeItemNoUpdate(HiredJobInventory.MAINHAND_SLOT);
        helper.assertTrue(villager.getMainHandItem().isEmpty(), "removed job gear should clear job-controlled main hand");
        helper.assertValueEqual(countStored(villager, Items.DIAMOND_SWORD), 1, "stored personal weapon should not duplicate after job removal");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void jobInventoryAssignedStorageDepositMovesExactCountOnce(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrInventoryDeposit");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        BlockPos storageRel = new BlockPos(3, 2, 1);
        BlockPos storagePos = helper.absolutePos(storageRel);
        setBlock(helper, storageRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, storagePos);

        AssignedStorageService.AssignSummary summary = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), storagePos)),
                AssignedStorageService.OUTPUT_PURPOSE);
        helper.assertValueEqual(summary.assigned(), 1, "output storage assignment");

        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        helper.assertTrue(jobInventory.insertOutput(new ItemStack(Items.COBBLESTONE, 32)).isEmpty(), "output should fit");
        helper.assertTrue(jobInventory.depositOutputToAssignedStorage(), "first deposit should move output");
        helper.assertFalse(jobInventory.depositOutputToAssignedStorage(), "second deposit should find no remaining output");
        helper.assertValueEqual(countItem(container(level, storagePos), Items.COBBLESTONE), 32, "storage should receive the exact output count once");
        helper.assertValueEqual(countJobInventoryItem(jobInventory, Items.COBBLESTONE), 0, "job inventory should have no duplicate remainder");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignedStorageSavedDataSkipsMalformedLegacyEntries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID villagerId = UUID.nameUUIDFromBytes("villagerretaliation:storage-save-villager".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID hirerId = UUID.nameUUIDFromBytes("villagerretaliation:storage-save-hirer".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        BlockPos storagePos = helper.absolutePos(new BlockPos(2, 2, 2));

        CompoundTag root = new CompoundTag();
        ListTag entries = new ListTag();
        CompoundTag legacyValid = new CompoundTag();
        legacyValid.putString("Dimension", level.dimension().location().toString());
        legacyValid.putLong("Pos", storagePos.asLong());
        legacyValid.putUUID("Villager", villagerId);
        legacyValid.putUUID("Hirer", hirerId);
        entries.add(legacyValid);

        CompoundTag missingVillager = new CompoundTag();
        missingVillager.putString("Dimension", level.dimension().location().toString());
        missingVillager.putLong("Pos", helper.absolutePos(new BlockPos(3, 2, 2)).asLong());
        entries.add(missingVillager);

        CompoundTag invalidDimension = new CompoundTag();
        invalidDimension.putString("Dimension", "not a valid id");
        invalidDimension.putLong("Pos", helper.absolutePos(new BlockPos(4, 2, 2)).asLong());
        invalidDimension.putUUID("Villager", UUID.randomUUID());
        entries.add(invalidDimension);
        root.put("Entries", entries);

        AssignedStorageSavedData loaded = AssignedStorageSavedData.load(root, level.registryAccess());
        List<AssignedStorageSavedData.AssignedContainerRecord> records = loaded.assignedTo(villagerId);
        helper.assertValueEqual(records.size(), 1, "malformed assigned-storage entries should be skipped");
        AssignedStorageSavedData.AssignedContainerRecord record = records.getFirst();
        helper.assertValueEqual(record.dimension(), level.dimension(), "legacy storage dimension");
        helper.assertValueEqual(record.pos(), storagePos, "legacy storage position");
        helper.assertValueEqual(record.hirerId(), hirerId, "legacy storage hirer");
        helper.assertValueEqual(record.purpose(), "general", "missing legacy purpose should default safely");
        helper.assertValueEqual(record.validationStatus(), "unknown", "missing legacy validation should default safely");

        CompoundTag saved = loaded.save(new CompoundTag(), level.registryAccess());
        helper.assertValueEqual(saved.getList("Entries", Tag.TAG_COMPOUND).size(), 1, "save should keep only valid assigned storage");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void deathDropsPersonalAndJobInventory(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        helper.assertTrue(
                VillagerInventoryContainer.addItem(villager, new ItemStack(Items.BREAD, 5)).isEmpty(),
                "personal inventory should accept bread");
        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        jobInventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(jobInventory.insertOutput(new ItemStack(Items.COAL, 3)).isEmpty(), "job output should fit");

        List<ItemEntity> drops = new ArrayList<>();
        LivingDropsEvent event = new LivingDropsEvent(villager, villager.damageSources().generic(), drops, false);
        VillagerInventoryAccess.dropAllInventoryAndEquipment(villager, event);

        helper.assertValueEqual(countEventDrops(drops, Items.BREAD), 5, "death should drop personal inventory");
        helper.assertValueEqual(countEventDrops(drops, Items.IRON_PICKAXE), 1, "death should drop job equipment");
        helper.assertValueEqual(countEventDrops(drops, Items.COAL), 3, "death should drop job output");
        helper.assertValueEqual(countStored(villager, Items.BREAD), 0, "personal inventory should clear after death drops");
        helper.assertTrue(HiredJobInventory.getJobInventory(villager).isEmpty(), "job inventory should clear after death drops");

        villager.discard();
        helper.succeed();
    }

    private static int countStored(Villager villager, Item item) {
        int count = 0;
        NonNullList<ItemStack> inventory = VillagerInventoryContainer.loadFullInventory(villager);
        for (ItemStack stack : inventory) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countEventDrops(Iterable<ItemEntity> drops, Item item) {
        int count = 0;
        for (ItemEntity entity : drops) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    private static int countItem(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countJobInventoryItem(HiredJobInventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void invokeTradeWithVillagerThrowHalfStack(
            Villager villager,
            Set<Item> items,
            LivingEntity target) {
        try {
            Method method = TradeWithVillager.class.getDeclaredMethod(
                    "throwHalfStack",
                    Villager.class,
                    Set.class,
                    LivingEntity.class);
            method.setAccessible(true);
            method.invoke(null, villager, items, target);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("Could not invoke TradeWithVillager.throwHalfStack: " + exception);
        }
    }

    private static void invokeShowTradesToPlayerDisplayAsHeldItem(Villager villager, ItemStack stack) {
        try {
            Method method = ShowTradesToPlayer.class.getDeclaredMethod(
                    "displayAsHeldItem",
                    Villager.class,
                    ItemStack.class);
            method.setAccessible(true);
            method.invoke(null, villager, stack);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("Could not invoke ShowTradesToPlayer.displayAsHeldItem: " + exception);
        }
    }

    private static void invokeShowTradesToPlayerClearHeldItem(Villager villager) {
        try {
            Method method = ShowTradesToPlayer.class.getDeclaredMethod("clearHeldItem", Villager.class);
            method.setAccessible(true);
            method.invoke(null, villager);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("Could not invoke ShowTradesToPlayer.clearHeldItem: " + exception);
        }
    }

    private static void invokeHeroGiftThrow(GiveGiftToHero behavior, Villager villager, LivingEntity target) {
        try {
            Method method = GiveGiftToHero.class.getDeclaredMethod("throwGift", Villager.class, LivingEntity.class);
            method.setAccessible(true);
            method.invoke(behavior, villager, target);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("Could not invoke GiveGiftToHero.throwGift: " + exception);
        }
    }

    private static Container container(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            return container;
        }
        throw new GameTestAssertException("Expected container at " + pos);
    }

    private static int countDroppedItems(ServerLevel level, BlockPos center, Item item) {
        int count = 0;
        AABB area = new AABB(center).inflate(8.0D);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    private static void buildFloor(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ, int y) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                setBlock(helper, new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                setBlock(helper, new BlockPos(x, y + 1, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, y + 2, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void setBlock(GameTestHelper helper, BlockPos relativePos, BlockState state) {
        helper.getLevel().setBlock(helper.absolutePos(relativePos), state, Block.UPDATE_ALL);
    }

    private static Villager spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw new GameTestAssertException("Could not create villager");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(villager)) {
            throw new GameTestAssertException("Could not add villager to level");
        }
        level.tickNonPassenger(villager);
        return villager;
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("villagerretaliation:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(id, name));
        BlockPos spawn = level.getSharedSpawnPos();
        player.moveTo(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    private static void configureGameTestStructures() {
        String configured = System.getProperty("villagerretaliation.gameteststructures");
        if (configured != null && !configured.isBlank()) {
            StructureUtils.testStructuresDir = configured;
            return;
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("src/main/gameteststructures"));
        candidates.add(Path.of("../src/main/gameteststructures"));
        candidates.add(Path.of("neoforge/src/main/gameteststructures"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                StructureUtils.testStructuresDir = candidate.toAbsolutePath().normalize().toString();
                return;
            }
        }
    }
}
