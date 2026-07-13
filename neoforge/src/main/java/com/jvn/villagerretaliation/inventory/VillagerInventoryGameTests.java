package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerItemFilterItem;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.recipe.VillagerItemFilterCopyRecipe;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
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
    public static void inventoryButtonPrefersActiveHiredJobInventory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrPreferredJobInventory");
        ServerPlayer outsider = fakePlayer(level, "VrPreferredPersonalInventory");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        helper.assertValueEqual(
                VillagerInventoryAccess.preferredViewMode(level, villager, hirer),
                VillagerInventoryMenu.ViewMode.PERSONAL,
                "unhired villagers should open their personal inventory first");

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
        helper.assertValueEqual(
                VillagerInventoryAccess.preferredViewMode(level, villager, hirer),
                VillagerInventoryMenu.ViewMode.JOB,
                "the hirer should open the active job inventory first");
        helper.assertValueEqual(
                VillagerInventoryAccess.preferredViewMode(level, villager, outsider),
                VillagerInventoryMenu.ViewMode.PERSONAL,
                "other players must not be routed into the hired job inventory");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerItemFilterMatchesIdentityAndStacksByConfiguration(GameTestHelper helper) {
        ItemStack allowlist = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        helper.assertTrue(VillagerItemFilterData.setEntry(allowlist, 0, new ItemStack(Items.IRON_PICKAXE)),
                "first allowlist entry should be stored");
        helper.assertFalse(VillagerItemFilterData.setEntry(allowlist, 1, new ItemStack(Items.IRON_PICKAXE)),
                "duplicate item identities should be rejected");

        ItemStack damagedPickaxe = new ItemStack(Items.IRON_PICKAXE);
        damagedPickaxe.setDamageValue(100);
        damagedPickaxe.set(DataComponents.CUSTOM_NAME, Component.literal("Different components"));
        helper.assertTrue(VillagerItemFilterData.matches(allowlist, damagedPickaxe),
                "allowlist should ignore damage, names, and other components");
        helper.assertFalse(VillagerItemFilterData.matches(allowlist, new ItemStack(Items.DIRT)),
                "allowlist should reject unlisted items");

        ItemStack denylist = allowlist.copy();
        VillagerItemFilterData.setMode(denylist, VillagerItemFilterData.Mode.DENYLIST);
        helper.assertFalse(VillagerItemFilterData.matches(denylist, damagedPickaxe),
                "denylist should reject listed items");
        helper.assertTrue(VillagerItemFilterData.matches(denylist, new ItemStack(Items.DIRT)),
                "denylist should permit unlisted items");

        ItemStack emptyAllowlist = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        helper.assertFalse(VillagerItemFilterData.matches(emptyAllowlist, new ItemStack(Items.DIRT)),
                "empty allowlist should permit nothing");
        ItemStack emptyDenylist = emptyAllowlist.copy();
        VillagerItemFilterData.setMode(emptyDenylist, VillagerItemFilterData.Mode.DENYLIST);
        helper.assertTrue(VillagerItemFilterData.matches(emptyDenylist, new ItemStack(Items.DIRT)),
                "empty denylist should permit everything");

        ItemStack identical = allowlist.copyWithCount(64);
        helper.assertTrue(ItemStack.isSameItemSameComponents(allowlist, identical),
                "identically configured filters should stack");
        helper.assertFalse(ItemStack.isSameItemSameComponents(allowlist, denylist),
                "different filter modes should prevent stacking");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerItemFilterPersistsWithoutJoiningJobAutomation(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.COBBLESTONE));
        VillagerItemFilterService.replaceFilter(villager, filter);

        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        helper.assertValueEqual(inventory.getContainerSize(), 43, "job inventory should include nine hotbar slots and one filter slot");
        helper.assertTrue(inventory.getItem(HiredJobInventory.FILTER_SLOT).is(VillagerRetaliationItems.ITEM_FILTER.get()),
                "dedicated filter slot should contain the assigned copy");
        helper.assertFalse(inventory.isSupplySlot(HiredJobInventory.FILTER_SLOT),
                "filter slot should not be a supply slot");
        helper.assertFalse(inventory.isOutputSlot(HiredJobInventory.FILTER_SLOT),
                "filter slot should not be an output slot");
        helper.assertValueEqual(inventory.consumeSupply(VillagerRetaliationItems::isItemFilter, 1), 0,
                "job automation should never consume the dedicated filter");
        inventory.insertSupply(new ItemStack(Items.DIRT, 64));
        helper.assertTrue(inventory.getItem(HiredJobInventory.FILTER_SLOT).is(VillagerRetaliationItems.ITEM_FILTER.get()),
                "supply insertion should skip the dedicated filter slot");

        HiredJobInventory.clearRuntimeState(villager);
        HiredJobInventory reloaded = HiredJobInventory.getJobInventory(villager);
        helper.assertTrue(VillagerItemFilterData.entry(
                        reloaded.getItem(HiredJobInventory.FILTER_SLOT), 0).is(Items.COBBLESTONE),
                "filter data should survive save and reload");

        Villager legacyVillager = spawnVillager(helper, new BlockPos(3, 2, 1));
        NonNullList<ItemStack> legacyItems = NonNullList.withSize(33, ItemStack.EMPTY);
        legacyItems.set(32, new ItemStack(Items.GOLD_INGOT, 7));
        CompoundTag legacyTag = ContainerHelper.saveAllItems(
                new CompoundTag(), legacyItems, true, helper.getLevel().registryAccess());
        legacyVillager.getPersistentData().put("VillagerRetaliationJobInventory", legacyTag);
        HiredJobInventory.clearRuntimeState(legacyVillager);
        HiredJobInventory legacyLoaded = HiredJobInventory.getJobInventory(legacyVillager);
        helper.assertValueEqual(legacyLoaded.getItem(32).getCount(), 7,
                "legacy slot 32 should retain its saved contents");
        helper.assertTrue(legacyLoaded.getItem(HiredJobInventory.FILTER_SLOT).isEmpty(),
                "legacy 33-slot data should load with an empty filter slot");

        Villager legacyFilterVillager = spawnVillager(helper, new BlockPos(4, 2, 1));
        NonNullList<ItemStack> legacyFilterItems = NonNullList.withSize(34, ItemStack.EMPTY);
        legacyFilterItems.set(6, new ItemStack(Items.LADDER, 4));
        legacyFilterItems.set(32, new ItemStack(Items.IRON_INGOT, 5));
        legacyFilterItems.set(33, filter.copy());
        CompoundTag legacyFilterTag = ContainerHelper.saveAllItems(
                new CompoundTag(), legacyFilterItems, true, helper.getLevel().registryAccess());
        legacyFilterVillager.getPersistentData().put(JOB_INVENTORY_TAG, legacyFilterTag);
        HiredJobInventory.clearRuntimeState(legacyFilterVillager);
        HiredJobInventory migratedFilterInventory = HiredJobInventory.getJobInventory(legacyFilterVillager);
        helper.assertTrue(migratedFilterInventory.getItem(HiredJobInventory.HOTBAR_START).isEmpty(),
                "legacy filter slot should become the first empty hotbar slot");
        helper.assertTrue(migratedFilterInventory.getItem(HiredJobInventory.FILTER_SLOT).is(VillagerRetaliationItems.ITEM_FILTER.get()),
                "legacy filter should migrate to the new dedicated filter slot");
        helper.assertValueEqual(migratedFilterInventory.getItem(32).getCount(), 5,
                "legacy non-filter positions should remain unchanged");
        helper.assertTrue(migratedFilterInventory.getItem(6).is(Items.LADDER),
                "legacy supply items should remain in their existing positions");
        helper.assertValueEqual(migratedFilterInventory.slotType(6), HiredJobInventorySlotType.SUPPLY,
                "non-empty implicit legacy supply slots should retain their role");
        helper.assertValueEqual(migratedFilterInventory.slotType(7), HiredJobInventorySlotType.OUTPUT,
                "empty legacy supply reservations should adopt the new dynamic main-grid default");
        helper.assertValueEqual(
                legacyFilterVillager.getPersistentData().getCompound(JOB_INVENTORY_TAG).getInt("LayoutVersion"),
                2,
                "legacy filter migration should persist the current layout version");
        HiredJobInventory.clearRuntimeState(legacyFilterVillager);
        HiredJobInventory migratedFilterReloaded = HiredJobInventory.getJobInventory(legacyFilterVillager);
        helper.assertTrue(migratedFilterReloaded.getItem(HiredJobInventory.HOTBAR_START).isEmpty(),
                "reloading a migrated filter should not duplicate it into the hotbar");
        helper.assertTrue(migratedFilterReloaded.getItem(HiredJobInventory.FILTER_SLOT).is(VillagerRetaliationItems.ITEM_FILTER.get()),
                "migrated filter should remain in the dedicated slot after reload");

        ServerPlayer outsider = fakePlayer(helper.getLevel(), "VrFilterOutsider");
        VillagerInventoryMenu unauthorizedMenu = new VillagerInventoryMenu(
                98, outsider.getInventory(), villager, VillagerInventoryMenu.ViewMode.JOB, false, false);
        helper.assertFalse(unauthorizedMenu.getSlot(HiredJobInventory.FILTER_SLOT).mayPickup(outsider),
                "a non-hirer should not be able to remove the assigned filter");
        outsider.getInventory().setItem(outsider.getInventory().selected, filter.copy());
        VillagerItemFilterItem.handleModeChange(outsider, 9999, -1);
        helper.assertTrue(VillagerItemFilterData.mode(outsider.getMainHandItem()) == VillagerItemFilterData.Mode.ALLOWLIST,
                "invalid menu-slot requests should not mutate a filter");
        unauthorizedMenu.removed(outsider);

        villager.discard();
        legacyVillager.discard();
        legacyFilterVillager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerItemFilterAssignmentConsumesAndReturnsExactlyOne(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrFilterAssignment");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);

        ItemStack firstStack = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get(), 3);
        VillagerItemFilterData.setEntry(firstStack, 0, new ItemStack(Items.COBBLESTONE));
        hirer.getInventory().setItem(hirer.getInventory().selected, firstStack);
        VillagerItemFilterService.AssignmentResult first = VillagerItemFilterService.assignHeldFilter(
                hirer, villager, VillagerItemFilterData.Mode.ALLOWLIST);
        helper.assertTrue(first.assigned() && !first.replaced(), "first filter should be assigned without replacement");
        helper.assertValueEqual(hirer.getMainHandItem().getCount(), 2,
                "survival assignment should consume exactly one held filter");
        helper.assertTrue(VillagerItemFilterData.entry(
                        VillagerItemFilterService.assignedFilter(villager), 0).is(Items.COBBLESTONE),
                "assigned filter should copy the held configuration");

        hirer.getInventory().setItem(1, hirer.getMainHandItem().copy());
        ItemStack replacementStack = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get(), 2);
        VillagerItemFilterData.setEntry(replacementStack, 0, new ItemStack(Items.DIRT));
        hirer.getInventory().setItem(hirer.getInventory().selected, replacementStack);
        int cobblestoneFiltersBefore = hirer.getInventory().items.stream()
                .filter(VillagerRetaliationItems::isItemFilter)
                .filter(stack -> VillagerItemFilterData.entry(stack, 0).is(Items.COBBLESTONE))
                .mapToInt(ItemStack::getCount)
                .sum();
        VillagerItemFilterService.AssignmentResult replacement = VillagerItemFilterService.assignHeldFilter(
                hirer, villager, VillagerItemFilterData.Mode.DENYLIST);
        int cobblestoneFiltersAfter = hirer.getInventory().items.stream()
                .filter(VillagerRetaliationItems::isItemFilter)
                .filter(stack -> VillagerItemFilterData.entry(stack, 0).is(Items.COBBLESTONE))
                .mapToInt(ItemStack::getCount)
                .sum();
        helper.assertTrue(replacement.assigned() && replacement.replaced(),
                "second assignment should report replacement");
        helper.assertValueEqual(hirer.getMainHandItem().getCount(), 1,
                "replacement should consume exactly one new filter");
        helper.assertValueEqual(cobblestoneFiltersAfter, cobblestoneFiltersBefore + 1,
                "replacement should return exactly one old filter");
        helper.assertTrue(VillagerItemFilterData.mode(VillagerItemFilterService.assignedFilter(villager))
                        == VillagerItemFilterData.Mode.DENYLIST
                        && VillagerItemFilterData.entry(
                        VillagerItemFilterService.assignedFilter(villager), 0).is(Items.DIRT),
                "replacement should apply the selected mode only to the assigned copy");

        ServerPlayer outsider = fakePlayer(level, "VrFilterAssignmentOutsider");
        outsider.getInventory().setItem(
                outsider.getInventory().selected,
                new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get(), 2));
        VillagerItemFilterService.AssignmentResult rejected = VillagerItemFilterService.assignHeldFilter(
                outsider, villager, VillagerItemFilterData.Mode.ALLOWLIST);
        helper.assertFalse(rejected.assigned(), "a player who did not hire the villager should be rejected");
        helper.assertValueEqual(outsider.getMainHandItem().getCount(), 2,
                "rejected assignment should not consume a filter");
        helper.assertTrue(VillagerItemFilterData.mode(VillagerItemFilterService.assignedFilter(villager))
                        == VillagerItemFilterData.Mode.DENYLIST,
                "rejected assignment should not replace the villager's filter");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerItemFilterCoversStoragePreflightAndWithdrawalPaths(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrFilterStorage");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        BlockPos inputRel = new BlockPos(3, 2, 1);
        BlockPos paymentRel = new BlockPos(3, 2, 3);
        BlockPos inputPos = helper.absolutePos(inputRel);
        BlockPos paymentPos = helper.absolutePos(paymentRel);
        setBlock(helper, inputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, paymentRel, VillagerRetaliationBlocks.PAYMENT_BOX.get().defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, inputPos);
        AssignedStorageService.removeAssignedContainer(level, paymentPos);
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), inputPos)),
                AssignedStorageService.GENERAL_PURPOSE).assigned(), 1, "input storage assignment");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), paymentPos)),
                AssignedStorageService.PAYMENT_PURPOSE).assigned(), 1, "payment storage assignment");

        Container input = container(level, inputPos);
        Container payment = container(level, paymentPos);
        input.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        input.setItem(1, new ItemStack(Items.DIRT, 3));
        payment.setItem(0, new ItemStack(Items.DIRT, 3));
        helper.assertValueEqual(AssignedStorageService.countItems(villager, ignored -> true), 7,
                "missing villager filter should preserve prior counting behavior");

        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.COBBLESTONE));
        VillagerItemFilterService.replaceFilter(villager, filter);
        helper.assertValueEqual(AssignedStorageService.countItems(villager, ignored -> true), 4,
                "filtered counts should exclude disallowed items");
        helper.assertValueEqual(AssignedStorageService.countItems(villager, stack -> stack.is(Items.DIRT)), 0,
                "existing worker predicates should remain combined with the filter");
        helper.assertTrue(AssignedStorageService.nearestAssignedStoragePosContaining(
                        level, villager, stack -> stack.is(Items.DIRT)) == null,
                "search should not navigate to a container for disallowed items");
        helper.assertTrue(inputPos.equals(AssignedStorageService.nearestAssignedStoragePosContaining(
                        level, villager, stack -> stack.is(Items.COBBLESTONE))),
                "search should still locate allowed items");

        int transferred = AssignedStorageService.transferItemsAtAssignedStorage(
                villager, inputPos, ignored -> true, 1, offered -> ItemStack.EMPTY);
        helper.assertValueEqual(transferred, 1, "transfer should move an allowed item");
        helper.assertValueEqual(AssignedStorageService.consumeItems(villager, ignored -> true, 10), 3,
                "consumption should remove the remaining allowed items only");
        helper.assertValueEqual(countItem(input, Items.DIRT), 3,
                "disallowed input items should remain untouched");

        helper.assertValueEqual(AssignedStorageService.countPaymentItems(villager, stack -> stack.is(Items.DIRT)), 3,
                "payment counts should ignore the villager item filter");
        helper.assertValueEqual(AssignedStorageService.consumePaymentItems(
                        villager, stack -> stack.is(Items.DIRT), 1), 1,
                "payment consumption should ignore the villager item filter");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerItemFilterCopyRecipeCopiesOnlyConfiguration(GameTestHelper helper) {
        ItemStack configured = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(configured, 0, new ItemStack(Items.COBBLESTONE));
        VillagerItemFilterData.setEntry(configured, 8, new ItemStack(Items.IRON_INGOT));
        VillagerItemFilterData.setMode(configured, VillagerItemFilterData.Mode.DENYLIST);
        configured.set(DataComponents.CUSTOM_NAME, Component.literal("Do not copy this"));
        ItemStack empty = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        CraftingInput input = CraftingInput.of(2, 1, List.of(empty, configured));
        VillagerItemFilterCopyRecipe recipe = new VillagerItemFilterCopyRecipe(CraftingBookCategory.MISC);

        helper.assertTrue(recipe.matches(input, helper.getLevel()),
                "configured and default filters should match in either order");
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertValueEqual(result.getCount(), 2, "copy recipe should produce two filters");
        helper.assertTrue(VillagerItemFilterData.mode(result) == VillagerItemFilterData.Mode.DENYLIST,
                "copy recipe should preserve mode");
        helper.assertTrue(VillagerItemFilterData.entry(result, 0).is(Items.COBBLESTONE)
                        && VillagerItemFilterData.entry(result, 8).is(Items.IRON_INGOT),
                "copy recipe should preserve all ghost entries");
        helper.assertFalse(result.has(DataComponents.CUSTOM_NAME),
                "copy recipe should not copy unrelated components");
        helper.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(empty, empty.copy())), helper.getLevel()),
                "two default filters should not match");
        helper.assertFalse(recipe.matches(CraftingInput.of(
                        2, 1, List.of(configured, new ItemStack(Items.PAPER))), helper.getLevel()),
                "unrelated ingredients should invalidate the recipe");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void jobInventoryReusesItsLoadedViewUntilRuntimeStateClears(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        HiredJobInventory first = HiredJobInventory.getJobInventory(villager);
        first.setItem(6, new ItemStack(Items.COBBLESTONE));
        HiredJobInventory reused = HiredJobInventory.getJobInventory(villager);
        helper.assertTrue(first == reused, "job inventory lookups should reuse the loaded runtime view");
        helper.assertTrue(reused.getItem(6).is(Items.COBBLESTONE), "reused inventory should retain current contents");

        HiredJobInventory.clearRuntimeState(villager);
        HiredJobInventory reloaded = HiredJobInventory.getJobInventory(villager);
        helper.assertFalse(first == reloaded, "runtime cleanup should release the cached inventory view");
        helper.assertTrue(reloaded.getItem(6).is(Items.COBBLESTONE), "reloaded inventory should read persisted contents");

        villager.discard();
        helper.succeed();
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
        VillagerInventoryContainer container = new VillagerInventoryContainer(villager);
        helper.assertValueEqual(container.getContainerSize(), 42,
                "personal inventory should include armor, 36 storage slots, and both hands");

        ItemStack firstRemainder = VillagerInventoryContainer.addItem(villager, new ItemStack(Items.WHEAT, 40));
        ItemStack secondRemainder = VillagerInventoryContainer.addItem(villager, new ItemStack(Items.WHEAT, 30));
        helper.assertTrue(firstRemainder.isEmpty() && secondRemainder.isEmpty(), "regular inventory should accept wheat stacks");
        helper.assertValueEqual(countStored(villager, Items.WHEAT), 70, "regular inventory should merge matching stacks");

        NonNullList<ItemStack> mainGridFull = NonNullList.withSize(VillagerInventoryContainer.INVENTORY_SLOT_COUNT, ItemStack.EMPTY);
        for (int slot = 0; slot < VillagerInventoryContainer.HOTBAR_START; slot++) {
            mainGridFull.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        VillagerInventoryContainer.saveFullInventory(villager, mainGridFull);
        helper.assertTrue(VillagerInventoryContainer.addItem(villager, new ItemStack(Items.DIRT)).isEmpty(),
                "regular inventory should use the hotbar after the main grid is full");
        helper.assertTrue(VillagerInventoryContainer.loadFullInventory(villager)
                        .get(VillagerInventoryContainer.HOTBAR_START).is(Items.DIRT),
                "regular insertion should fill the first hotbar slot only after the main grid");

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
    public static void regularInventoryRestoresExpandedHotbarPersistence(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        int vanillaSlots = Math.min(
                VillagerInventoryContainer.INVENTORY_SLOT_COUNT,
                villager.getInventory().getContainerSize());
        int expandedExtraSlots = Math.max(0, VillagerInventoryContainer.INVENTORY_SLOT_COUNT - vanillaSlots);
        NonNullList<ItemStack> expandedExtraInventory = NonNullList.withSize(expandedExtraSlots, ItemStack.EMPTY);
        expandedExtraInventory.set(expandedExtraSlots - 1, new ItemStack(Items.EMERALD, 3));
        CompoundTag expandedTag = ContainerHelper.saveAllItems(
                new CompoundTag(),
                expandedExtraInventory,
                true,
                level.registryAccess());
        villager.getPersistentData().put(EXTRA_INVENTORY_TAG, expandedTag);

        new VillagerInventoryContainer(villager);
        helper.assertValueEqual(countStored(villager, Items.EMERALD), 3,
                "expanded personal hotbar contents should load from extra inventory persistence");
        helper.assertValueEqual(countDroppedItems(level, villager.blockPosition(), Items.EMERALD), 0,
                "expanded personal hotbar contents should no longer be treated as legacy overflow");

        new VillagerInventoryContainer(villager);
        helper.assertValueEqual(countStored(villager, Items.EMERALD), 3,
                "expanded personal hotbar contents should remain stable across repeated loads");
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
    public static void jobInventoryMenuMarksPlayerPlacedGridItemsAsSupply(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrJobMenuSupply");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        VillagerInventoryMenu menu = new VillagerInventoryMenu(
                1,
                player.getInventory(),
                villager,
                VillagerInventoryMenu.ViewMode.JOB,
                true,
                true);
        menu.getSlot(18).setByPlayer(new ItemStack(Items.COD, 4));
        menu.getSlot(HiredJobInventory.HOTBAR_START).setByPlayer(new ItemStack(Items.DIRT, 3));

        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        helper.assertValueEqual(jobInventory.slotType(18), HiredJobInventorySlotType.SUPPLY, "player-placed grid item slot type");
        helper.assertTrue(jobInventory.findSupply(stack -> stack.is(Items.COD)).is(Items.COD), "player-placed grid item should count as supply");
        helper.assertValueEqual(jobInventory.slotType(HiredJobInventory.HOTBAR_START), HiredJobInventorySlotType.SUPPLY,
                "arbitrary player-placed hotbar item slot type");
        helper.assertTrue(jobInventory.getItem(HiredJobInventory.HOTBAR_START).is(Items.DIRT),
                "job hotbar should accept arbitrary player-placed items");
        helper.assertValueEqual(menu.getSlot(HiredJobInventory.HOTBAR_START).y, 161,
                "job hotbar should align with the separated texture row");

        menu.removed(player);

        VillagerInventoryMenu personalMenu = new VillagerInventoryMenu(
                2,
                player.getInventory(),
                villager,
                VillagerInventoryMenu.ViewMode.PERSONAL,
                true,
                true);
        int personalHotbarMenuSlot = VillagerInventoryContainer.ARMOR_SLOT_COUNT
                + VillagerInventoryContainer.HOTBAR_START;
        helper.assertValueEqual(personalMenu.getSlot(personalHotbarMenuSlot).getSlotIndex(), personalHotbarMenuSlot,
                "personal hotbar should expose the expanded storage index");
        helper.assertValueEqual(personalMenu.getSlot(personalHotbarMenuSlot).y, 161,
                "personal hotbar should align with the separated texture row");
        personalMenu.removed(player);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void jobInventoryPrioritizesToolsAndUsesHotbarForOverflow(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);

        helper.assertTrue(inventory.insertTool(new ItemStack(Items.IRON_PICKAXE)).isEmpty(),
                "first assigned tool should fit in mainhand");
        helper.assertTrue(inventory.getItem(HiredJobInventory.MAINHAND_SLOT).is(Items.IRON_PICKAXE),
                "first assigned tool should occupy mainhand");
        for (int slot = HiredJobInventory.HOTBAR_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            helper.assertTrue(inventory.insertTool(new ItemStack(Items.IRON_AXE)).isEmpty(),
                    "additional assigned tool should fit in the hotbar");
        }
        for (int slot = HiredJobInventory.HOTBAR_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            helper.assertTrue(inventory.getItem(slot).is(Items.IRON_AXE),
                    "additional assigned tools should fill hotbar slots before the main grid");
        }
        helper.assertTrue(inventory.insertTool(new ItemStack(Items.IRON_SHOVEL)).isEmpty(),
                "tool overflow should fit in the main grid");
        helper.assertTrue(inventory.getItem(HiredJobInventory.MAIN_GRID_START).is(Items.IRON_SHOVEL),
                "tool overflow should begin at the first main-grid slot");

        inventory.clearContent();
        inventory.setItem(HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.IRON_PICKAXE));
        inventory.markPlayerPlacedSupply(HiredJobInventory.MAIN_GRID_START);
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.DIAMOND_PICKAXE));
        ItemStack equalScoreBest = inventory.equipBestTool(
                stack -> stack.is(Items.IRON_PICKAXE) || stack.is(Items.DIAMOND_PICKAXE),
                ignored -> 1.0D);
        helper.assertTrue(equalScoreBest.is(Items.DIAMOND_PICKAXE),
                "equal-scoring stored tools should prefer the hotbar over the main grid");
        helper.assertTrue(inventory.getItem(HiredJobInventory.MAIN_GRID_START).is(Items.IRON_PICKAXE),
                "equal-score selection should not rearrange the existing main-grid tool");

        inventory.clearContent();
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.WHEAT)).isEmpty(),
                "ordinary supply should fit in the main grid");
        helper.assertTrue(inventory.getItem(HiredJobInventory.MAIN_GRID_START).is(Items.WHEAT),
                "ordinary supplies should claim the main grid before the hotbar");
        helper.assertTrue(inventory.getItem(HiredJobInventory.HOTBAR_START).isEmpty(),
                "ordinary supplies should leave hotbar space available while the main grid has room");

        inventory.clearContent();
        fillMainJobGrid(inventory, Items.COBBLESTONE);
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.WHEAT)).isEmpty(),
                "ordinary supply overflow should fit in the hotbar");
        helper.assertTrue(inventory.getItem(HiredJobInventory.HOTBAR_START).is(Items.WHEAT),
                "ordinary supply overflow should use the first hotbar slot");

        inventory.clearContent();
        helper.assertTrue(inventory.insertOutput(new ItemStack(Items.BONE)).isEmpty(),
                "ordinary output should fit in the main grid");
        helper.assertTrue(inventory.getItem(HiredJobInventory.MAIN_GRID_START).is(Items.BONE),
                "ordinary outputs should claim the main grid before the hotbar");
        helper.assertTrue(inventory.getItem(HiredJobInventory.HOTBAR_START).isEmpty(),
                "ordinary outputs should leave hotbar space available while the main grid has room");

        inventory.clearContent();
        fillMainJobGrid(inventory, Items.COBBLESTONE);
        helper.assertTrue(inventory.insertOutput(new ItemStack(Items.BONE)).isEmpty(),
                "ordinary output overflow should fit in the hotbar");
        helper.assertTrue(inventory.getItem(HiredJobInventory.HOTBAR_START).is(Items.BONE),
                "ordinary output overflow should use the first hotbar slot");
        for (int slot = HiredJobInventory.HOTBAR_START + 1; slot < HiredJobInventory.FILTER_SLOT - 1; slot++) {
            inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        helper.assertTrue(inventory.canStoreOutputs(List.of(new ItemStack(Items.GRAVEL, 64))),
                "output simulation should include the final available hotbar slot");
        inventory.setItem(HiredJobInventory.FILTER_SLOT - 1, new ItemStack(Items.DIRT, 64));
        helper.assertFalse(inventory.canStoreOutputs(List.of(new ItemStack(Items.GRAVEL, 64))),
                "output simulation should reject items when the grid and hotbar are full");

        inventory.clearContent();
        fillMainJobGrid(inventory, Items.COBBLESTONE);
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.HOTBAR_START; slot++) {
            inventory.markPlayerPlacedSupply(slot);
        }
        for (int slot = HiredJobInventory.HOTBAR_START; slot < HiredJobInventory.FILTER_SLOT - 1; slot++) {
            inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        helper.assertTrue(inventory.canStoreSuppliesAfterDepositingOutputs(List.of(new ItemStack(Items.GRAVEL, 64))),
                "supply simulation should include the final available hotbar slot");
        inventory.setItem(HiredJobInventory.FILTER_SLOT - 1, new ItemStack(Items.DIRT, 64));
        helper.assertFalse(inventory.canStoreSuppliesAfterDepositingOutputs(List.of(new ItemStack(Items.GRAVEL, 64))),
                "supply simulation should reject items when the grid and hotbar are full");

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
        NonNullList<ItemStack> personalInventory = NonNullList.withSize(
                VillagerInventoryContainer.INVENTORY_SLOT_COUNT,
                ItemStack.EMPTY);
        personalInventory.set(VillagerInventoryContainer.HOTBAR_START, new ItemStack(Items.BREAD, 5));
        VillagerInventoryContainer.saveFullInventory(villager, personalInventory);
        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        jobInventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(jobInventory.insertOutput(new ItemStack(Items.COAL, 3)).isEmpty(), "job output should fit");
        jobInventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.BONE, 2));

        List<ItemEntity> drops = new ArrayList<>();
        LivingDropsEvent event = new LivingDropsEvent(villager, villager.damageSources().generic(), drops, false);
        VillagerInventoryAccess.dropAllInventoryAndEquipment(villager, event);

        helper.assertValueEqual(countEventDrops(drops, Items.BREAD), 5, "death should drop personal inventory");
        helper.assertValueEqual(countEventDrops(drops, Items.IRON_PICKAXE), 1, "death should drop job equipment");
        helper.assertValueEqual(countEventDrops(drops, Items.COAL), 3, "death should drop job output");
        helper.assertValueEqual(countEventDrops(drops, Items.BONE), 2, "death should drop job hotbar contents");
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

    private static void fillMainJobGrid(HiredJobInventory inventory, Item item) {
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.HOTBAR_START; slot++) {
            inventory.setItem(slot, new ItemStack(item, 64));
        }
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
