package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
