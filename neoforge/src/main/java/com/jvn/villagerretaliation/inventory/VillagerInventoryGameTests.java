package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import com.jvn.villagerretaliation.item.VillagerFilterMatcher;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerItemFilterItem;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingInventoryBridge;
import com.jvn.villagerretaliation.network.ItemFilterCombinationChangePayload;
import com.jvn.villagerretaliation.recipe.VillagerAttributeFilterCopyRecipe;
import com.jvn.villagerretaliation.recipe.VillagerFilterResetRecipe;
import com.jvn.villagerretaliation.recipe.VillagerItemFilterCopyRecipe;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.party.PartyVillagerDropCollection;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
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
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

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
    public static void openingInventoryDoesNotCountAlreadyMissingGiftAsTaken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrMissingGiftInventoryOpen");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        var advancement = level.getServer().getAdvancements().get(
                com.jvn.villagerretaliation.VillagerRetaliation.id("reputation/changed_my_mind"));
        helper.assertTrue(advancement != null, "the gift return advancement should be loaded");
        helper.assertFalse(
                player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "the test player should not start with the gift return advancement");

        VillagerGiftReturnTracker.recordStoredGift(
                level, villager, player, new ItemStack(Items.EMERALD), 5);

        VillagerGiftReturnTracker.GiftSnapshot snapshot =
                VillagerGiftReturnTracker.capture(player, villager);

        helper.assertFalse(
                player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "opening inventory must not blame the player for a gift that was already missing");
        helper.assertTrue(snapshot.counts().isEmpty(),
                "stale missing-gift ledger entries should be pruned before the inventory snapshot");

        ItemStack consumedGift = VillagerGiftReturnTracker.markStoredGift(
                new ItemStack(Items.GOLD_INGOT), player, villager);
        helper.assertTrue(VillagerInventoryAccess.addItem(villager, consumedGift).isEmpty(),
                "the simulated consumed gift should begin in villager storage");
        VillagerGiftReturnTracker.recordStoredGift(level, villager, player, consumedGift, 5);
        snapshot = VillagerGiftReturnTracker.capture(player, villager);
        helper.assertFalse(
                VillagerInventoryAccess.takeCarriedItem(
                        villager, stack -> stack.is(Items.GOLD_INGOT)).isEmpty(),
                "the simulated villager behavior should consume the stored gift");
        VillagerGiftReturnTracker.applyTakenGiftPenalties(player, villager, snapshot);
        helper.assertFalse(
                player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "a gift removed by villager behavior must not be attributed to the viewing player");

        ItemStack returnedGift = VillagerGiftReturnTracker.markStoredGift(
                new ItemStack(Items.DIAMOND), player, villager);
        helper.assertTrue(VillagerInventoryAccess.addItem(villager, returnedGift).isEmpty(),
                "the returned gift should begin in villager storage");
        VillagerGiftReturnTracker.recordStoredGift(level, villager, player, returnedGift, 5);
        snapshot = VillagerGiftReturnTracker.capture(player, villager);
        helper.assertValueEqual(snapshot.counts().size(), 1,
                "the stored returned gift should be present in the opening snapshot");
        helper.assertValueEqual(snapshot.counts().getFirst().count(), 1,
                "the opening snapshot should count the stored returned gift");
        ItemStack takenGift = VillagerInventoryAccess.takeCarriedItem(
                villager, stack -> stack.is(Items.DIAMOND));
        helper.assertTrue(VillagerGiftReturnTracker.isStoredGiftFrom(takenGift, player.getUUID()),
                "the removed gift should retain its giver tracking");
        player.getInventory().setItem(0, takenGift);
        helper.assertTrue(VillagerGiftReturnTracker.isStoredGiftFrom(
                        player.getInventory().getItem(0), player.getUUID()),
                "the player inventory should contain the tracked returned gift");
        helper.assertValueEqual(
                VillagerInventoryContainer.countStoredGiftItem(
                        villager, player.getUUID(), returnedGift),
                0,
                "the returned gift should no longer be in villager storage");
        VillagerGiftReturnTracker.applyTakenGiftPenalties(player, villager, snapshot);
        helper.assertTrue(
                VillagerGiftReturnTracker.giftedBy(player.getInventory().getItem(0)).isEmpty(),
                "a tracked gift actually received by the player should still be processed as taken back");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void attributeFiltersSupportMultipleMatcherPolicies(GameTestHelper helper) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        VillagerAttributeFilterData.Attribute placeable = new VillagerAttributeFilterData.Attribute(
                VillagerAttributeFilterData.AttributeType.PLACEABLE, "");
        VillagerAttributeFilterData.Attribute fuel = new VillagerAttributeFilterData.Attribute(
                VillagerAttributeFilterData.AttributeType.FURNACE_FUEL, "");
        helper.assertTrue(VillagerAttributeFilterData.setSelected(filter, placeable, false),
                "the first attribute should be stored");
        helper.assertTrue(VillagerAttributeFilterData.toggleSelected(filter, fuel),
                "a second distinct attribute should be stored");
        helper.assertValueEqual(VillagerAttributeFilterData.read(filter).attributes().size(), 2,
                "multi-attribute configuration size");

        helper.assertTrue(VillagerFilterMatcher.matches(
                        helper.getLevel(), filter, new ItemStack(Items.STONE)),
                "Match Any should accept a placeable non-fuel");
        helper.assertTrue(VillagerFilterMatcher.matches(
                        helper.getLevel(), filter, new ItemStack(Items.STICK)),
                "Match Any should accept a fuel that is not placeable");
        helper.assertFalse(VillagerFilterMatcher.matches(
                        helper.getLevel(), filter, new ItemStack(Items.APPLE)),
                "Match Any should reject a candidate satisfying neither attribute");

        VillagerFilterPolicy.setPolicy(
                filter,
                VillagerFilterPolicy.TransferDirection.BOTH,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                VillagerFilterPolicy.CombinationMode.MATCH_ALL,
                java.util.OptionalInt.empty());
        helper.assertTrue(VillagerFilterMatcher.matches(
                        helper.getLevel(), filter, new ItemStack(Items.OAK_PLANKS)),
                "Match All should accept a block that is also furnace fuel");
        helper.assertFalse(VillagerFilterMatcher.matches(
                        helper.getLevel(), filter, new ItemStack(Items.STONE)),
                "Match All should reject a partial match");

        VillagerFilterPolicy.setListMode(filter, VillagerFilterPolicy.ListMode.DENY_MATCHING);
        helper.assertFalse(VillagerFilterMatcher.matches(
                        helper.getLevel(), filter, new ItemStack(Items.OAK_PLANKS)),
                "Deny Matching should reject the complete intersection");
        helper.assertTrue(VillagerFilterMatcher.matches(
                        helper.getLevel(), filter, new ItemStack(Items.STONE)),
                "Deny Matching should permit a partial match");

        CompoundTag malformedData = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        malformedData.getCompound("villagerretaliation:attribute_filter")
                .putInt("Version", VillagerAttributeFilterData.CURRENT_VERSION + 1);
        filter.set(DataComponents.CUSTOM_DATA, CustomData.of(malformedData));
        helper.assertFalse(VillagerFilterMatcher.matches(
                        helper.getLevel(), filter, new ItemStack(Items.STONE)),
                "malformed matcher data must fail closed even in Deny Matching mode");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void attributeFiltersMatchDirectlyAndConstrainItemFilterEntries(GameTestHelper helper) {
        ItemStack fuelFilter = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        VillagerAttributeFilterData.Attribute furnaceFuel = new VillagerAttributeFilterData.Attribute(
                VillagerAttributeFilterData.AttributeType.FURNACE_FUEL,
                "");
        helper.assertTrue(VillagerAttributeFilterData.availableAttributes(
                        new ItemStack(Items.STICK), helper.getLevel()).contains(furnaceFuel),
                "reference-item inspection should expose furnace fuel");
        helper.assertTrue(VillagerAttributeFilterData.setSelected(fuelFilter, furnaceFuel, false),
                "a selected attribute should persist");
        helper.assertTrue(VillagerAttributeFilterData.matches(
                        helper.getLevel(), fuelFilter, new ItemStack(Items.WOODEN_PICKAXE)),
                "a direct attribute filter should accept matching items");
        helper.assertFalse(VillagerAttributeFilterData.matches(
                        helper.getLevel(), fuelFilter, new ItemStack(Items.STONE)),
                "a direct attribute filter should reject non-matching items");

        ItemStack nonStackableFilter = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        VillagerAttributeFilterData.setSelected(
                nonStackableFilter,
                new VillagerAttributeFilterData.Attribute(
                        VillagerAttributeFilterData.AttributeType.NOT_STACKABLE,
                        ""),
                false);

        ItemStack combined = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntryCombination(
                combined, VillagerItemFilterData.EntryCombination.ALL);
        helper.assertTrue(VillagerItemFilterData.setEntry(
                        combined, 0, new ItemStack(Items.WOODEN_PICKAXE)),
                "ordinary identity entry should be accepted");
        helper.assertTrue(VillagerItemFilterData.setEntry(combined, 1, fuelFilter),
                "configured attribute filter should be accepted as a nested entry");
        helper.assertTrue(VillagerItemFilterData.setEntry(combined, 2, nonStackableFilter),
                "multiple distinct attribute constraints should be accepted");
        helper.assertTrue(VillagerAttributeFilterData.read(
                        VillagerItemFilterData.entry(combined, 1)).attribute().equals(furnaceFuel),
                "nested filter configuration must survive item-filter serialization");
        helper.assertTrue(VillagerItemFilterData.matches(
                        helper.getLevel(), combined, new ItemStack(Items.WOODEN_PICKAXE)),
                "identity and every attribute constraint should match conjunctively");
        helper.assertFalse(VillagerItemFilterData.matches(
                        helper.getLevel(), combined, new ItemStack(Items.STICK)),
                "matching attributes must not bypass the configured item identity");
        helper.assertFalse(VillagerItemFilterData.matches(
                        helper.getLevel(), combined, new ItemStack(Items.STONE_PICKAXE)),
                "matching stackability must not bypass identity and fuel constraints");

        VillagerItemFilterData.setMode(combined, VillagerItemFilterData.Mode.DENYLIST);
        helper.assertFalse(VillagerItemFilterData.matches(
                        helper.getLevel(), combined, new ItemStack(Items.WOODEN_PICKAXE)),
                "denylist mode should negate the complete nested expression");
        helper.assertTrue(VillagerItemFilterData.matches(
                        helper.getLevel(), combined, new ItemStack(Items.STICK)),
                "denylist mode should allow candidates outside the complete expression");
        helper.succeed();
    }
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void itemFiltersPreserveComponentsAndUseRawNestedMatchers(GameTestHelper helper) {
        ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
        ItemStack poison = PotionContents.createItemStack(Items.POTION, Potions.POISON);
        ItemStack potionFilter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        helper.assertTrue(VillagerItemFilterData.setEntry(potionFilter, 0, healing),
                "a configured potion should be accepted");
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        VillagerItemFilterData.entry(potionFilter, 0), healing),
                "potion contents should survive filter serialization");
        helper.assertTrue(VillagerItemFilterData.matches(helper.getLevel(), potionFilter, healing),
                "the configured potion should match");
        helper.assertFalse(VillagerItemFilterData.matches(helper.getLevel(), potionFilter, poison),
                "a different potion in the same bottle type should not match");
        helper.assertTrue(VillagerItemFilterData.setEntry(potionFilter, 1, poison),
                "different potion variants should be valid alternatives");

        ItemStack allowedWood = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(allowedWood, 0, new ItemStack(Items.STICK));
        VillagerItemFilterData.setEntry(allowedWood, 1, new ItemStack(Items.WOODEN_PICKAXE));
        ItemStack deniedSticks = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setMode(deniedSticks, VillagerItemFilterData.Mode.DENYLIST);
        VillagerItemFilterData.setEntry(deniedSticks, 0, new ItemStack(Items.STICK));
        VillagerFilterPolicy.setPolicy(
                deniedSticks,
                VillagerFilterPolicy.TransferDirection.PROVIDE,
                VillagerFilterPolicy.ListMode.DENY_MATCHING,
                VillagerFilterPolicy.CombinationMode.MATCH_ANY,
                java.util.OptionalInt.of(64));

        ItemStack combined = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntryCombination(
                combined, VillagerItemFilterData.EntryCombination.ALL);
        helper.assertTrue(VillagerItemFilterData.setEntry(combined, 0, allowedWood),
                "a nested allowlist should be accepted");
        helper.assertTrue(VillagerItemFilterData.setEntry(combined, 1, deniedSticks),
                "a nested denylist should be accepted alongside it");
        helper.assertTrue(VillagerRetaliationItems.isItemFilter(
                        VillagerItemFilterData.entry(combined, 0)),
                "nested filter configuration should survive serialization");
        helper.assertFalse(VillagerItemFilterData.matches(
                        helper.getLevel(), combined, new ItemStack(Items.WOODEN_PICKAXE)),
                "Match All should reject a candidate outside the nested raw matcher");
        helper.assertTrue(VillagerItemFilterData.matches(
                        helper.getLevel(), combined, new ItemStack(Items.STICK)),
                "nested mode, direction, and stock must not change its raw predicate");
        helper.assertFalse(VillagerItemFilterData.matches(
                        helper.getLevel(), combined, new ItemStack(Items.STONE_PICKAXE)),
                "the nested allowlist should still reject unrelated items");
        helper.assertFalse(VillagerItemFilterData.setEntry(combined, 2, combined.copy()),
                "a filter must not directly contain an identical copy of itself");

        ItemStack nested = allowedWood;
        for (int depth = 0; depth < VillagerItemFilterData.MAX_NESTING_DEPTH; depth++) {
            ItemStack parent = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
            helper.assertTrue(VillagerItemFilterData.setEntry(parent, 0, nested),
                    "nesting within the supported depth should be accepted");
            nested = parent;
        }
        ItemStack tooDeep = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        helper.assertFalse(VillagerItemFilterData.setEntry(tooDeep, 0, nested),
                "nesting beyond the supported depth should be rejected");
        helper.assertTrue(VillagerItemFilterData.setEntry(
                        tooDeep, 0, new ItemStack(Items.STICK)),
                "a shallow entry should provide a bounded malformed-data fixture");
        CompoundTag tooDeepData = tooDeep.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag tooDeepEntry = tooDeepData
                .getCompound("villagerretaliation:item_filter")
                .getList("Entries", Tag.TAG_COMPOUND)
                .getCompound(0);
        tooDeepEntry.putString("Item", "villagerretaliation:item_filter");
        tooDeepEntry.put("Data", nested.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
        tooDeep.set(DataComponents.CUSTOM_DATA, CustomData.of(tooDeepData));
        helper.assertFalse(VillagerFilterMatcher.matches(
                        helper.getLevel(), tooDeep, new ItemStack(Items.STICK)),
                "serialized nesting beyond the runtime limit must fail closed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void itemFilterCombinationModesCoverMixedEntriesAndDenylists(GameTestHelper helper) {
        ItemStack cookedMeat = attributeTag("c:foods/cooked_meat");
        ItemStack wool = attributeTag("minecraft:wool");
        ItemStack emmitt = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(emmitt, 0, cookedMeat);
        VillagerItemFilterData.setEntry(emmitt, 1, wool);
        VillagerItemFilterData.setEntry(emmitt, 2, new ItemStack(Items.COOKED_COD));
        VillagerItemFilterData.setEntry(emmitt, 3, new ItemStack(Items.COOKED_SALMON));
        VillagerItemFilterData.setEntry(emmitt, 4, new ItemStack(Items.LEATHER));
        helper.assertValueEqual(
                VillagerItemFilterData.entryCombination(emmitt),
                VillagerItemFilterData.EntryCombination.ANY,
                "new filters should default to ANY composition");
        for (Item item : List.of(
                Items.COOKED_MUTTON,
                Items.WHITE_WOOL,
                Items.COOKED_COD,
                Items.COOKED_SALMON,
                Items.LEATHER)) {
            helper.assertTrue(
                    VillagerItemFilterData.matches(helper.getLevel(), emmitt, new ItemStack(item)),
                    item + " should match Emmitt's mixed category-or-identity allowlist");
        }
        helper.assertFalse(
                VillagerItemFilterData.matches(helper.getLevel(), emmitt, new ItemStack(Items.MUTTON)),
                "raw mutton should not match Emmitt's mixed allowlist");

        ItemStack intersection = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntryCombination(
                intersection, VillagerItemFilterData.EntryCombination.ALL);
        VillagerItemFilterData.setEntry(intersection, 0, new ItemStack(Items.WHITE_WOOL));
        VillagerItemFilterData.setEntry(intersection, 1, wool);
        helper.assertTrue(
                VillagerItemFilterData.matches(
                        helper.getLevel(), intersection, new ItemStack(Items.WHITE_WOOL)),
                "ALL should accept an item satisfying every entry");
        helper.assertFalse(
                VillagerItemFilterData.matches(
                        helper.getLevel(), intersection, new ItemStack(Items.BLACK_WOOL)),
                "ALL should reject a partial match");

        ItemStack deniedAny = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setMode(deniedAny, VillagerItemFilterData.Mode.DENYLIST);
        VillagerItemFilterData.setEntry(deniedAny, 0, wool);
        VillagerItemFilterData.setEntry(deniedAny, 1, new ItemStack(Items.LEATHER));
        helper.assertFalse(
                VillagerItemFilterData.matches(
                        helper.getLevel(), deniedAny, new ItemStack(Items.BLACK_WOOL)),
                "ANY denylist should reject any matching entry");
        helper.assertFalse(
                VillagerItemFilterData.matches(
                        helper.getLevel(), deniedAny, new ItemStack(Items.LEATHER)),
                "ANY denylist should reject a concrete match");
        helper.assertTrue(
                VillagerItemFilterData.matches(helper.getLevel(), deniedAny, new ItemStack(Items.DIRT)),
                "ANY denylist should allow candidates outside every entry");

        ItemStack deniedAll = intersection.copy();
        VillagerItemFilterData.setMode(deniedAll, VillagerItemFilterData.Mode.DENYLIST);
        helper.assertFalse(
                VillagerItemFilterData.matches(
                        helper.getLevel(), deniedAll, new ItemStack(Items.WHITE_WOOL)),
                "ALL denylist should reject the complete intersection");
        helper.assertTrue(
                VillagerItemFilterData.matches(
                        helper.getLevel(), deniedAll, new ItemStack(Items.BLACK_WOOL)),
                "ALL denylist should allow a partial match");

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void explicitMatchAnyPolicyOverridesLegacyItemFilterComposition(GameTestHelper helper) {
        ItemStack cookedFood = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(cookedFood, 0, attributeTag("minecraft:meat"));
        VillagerItemFilterData.setEntry(cookedFood, 1, new ItemStack(Items.SALMON));
        VillagerItemFilterData.setEntry(cookedFood, 2, new ItemStack(Items.COOKED_SALMON));

        CompoundTag customData = cookedFood.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        customData.getCompound("villagerretaliation:item_filter").remove("EntryCombination");
        cookedFood.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        VillagerFilterPolicy.setPolicy(
                cookedFood,
                VillagerFilterPolicy.TransferDirection.BOTH,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                VillagerFilterPolicy.CombinationMode.MATCH_ANY,
                java.util.OptionalInt.empty());

        helper.assertValueEqual(
                VillagerItemFilterData.entryCombination(cookedFood),
                VillagerItemFilterData.EntryCombination.LEGACY,
                "fixture should retain its legacy item-filter composition");
        helper.assertValueEqual(
                VillagerFilterPolicy.read(cookedFood).combinationMode(),
                VillagerFilterPolicy.CombinationMode.MATCH_ANY,
                "fixture should carry the newer explicit Match Any policy");
        helper.assertTrue(
                VillagerFilterMatcher.matches(helper.getLevel(), cookedFood, new ItemStack(Items.COOKED_MUTTON)),
                "explicit Match Any should accept cooked mutton through the meat-tag entry");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void itemFilterCombinationMigrationCopyAndPacketsAreSafe(GameTestHelper helper) {
        ItemStack fuel = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        VillagerAttributeFilterData.setSelected(
                fuel,
                new VillagerAttributeFilterData.Attribute(
                        VillagerAttributeFilterData.AttributeType.FURNACE_FUEL, ""),
                false);
        ItemStack legacy = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(legacy, 0, new ItemStack(Items.STICK));
        VillagerItemFilterData.setEntry(legacy, 1, fuel);
        CompoundTag legacyData =
                legacy.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        legacyData.getCompound("villagerretaliation:item_filter").remove("EntryCombination");
        legacy.set(DataComponents.CUSTOM_DATA, CustomData.of(legacyData));
        helper.assertValueEqual(
                VillagerItemFilterData.entryCombination(legacy),
                VillagerItemFilterData.EntryCombination.LEGACY,
                "roots without combination data should retain compatibility semantics");
        helper.assertTrue(
                VillagerItemFilterData.matches(helper.getLevel(), legacy, new ItemStack(Items.STICK)),
                "legacy identity-OR plus nested-AND behavior should remain intact");
        helper.assertFalse(
                VillagerItemFilterData.matches(
                        helper.getLevel(), legacy, new ItemStack(Items.WOODEN_PICKAXE)),
                "migration must not silently reinterpret an established filter");

        ItemStack copied = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.copyConfiguration(legacy, copied);
        helper.assertValueEqual(
                VillagerItemFilterData.entryCombination(copied),
                VillagerItemFilterData.EntryCombination.LEGACY,
                "copy normalization should preserve compatibility mode");
        CompoundTag copiedRoot = copied.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getCompound("villagerretaliation:item_filter");
        helper.assertValueEqual(
                copiedRoot.getString("EntryCombination"),
                "legacy",
                "copied legacy filters should persist an explicit marker");

        ItemStack unknown = copied.copy();
        CompoundTag unknownData =
                unknown.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        unknownData.getCompound("villagerretaliation:item_filter")
                .putString("EntryCombination", "future_mode");
        unknown.set(DataComponents.CUSTOM_DATA, CustomData.of(unknownData));
        helper.assertValueEqual(
                VillagerItemFilterData.entryCombination(unknown),
                VillagerItemFilterData.EntryCombination.LEGACY,
                "unknown persisted values should fail safely to compatibility mode");

        ItemFilterCombinationChangePayload invalid = new ItemFilterCombinationChangePayload(99);
        helper.assertTrue(
                !invalid.isValid() && invalid.requestedCombination() == null,
                "invalid packet values should not author a logic mode");
        ServerPlayer player = fakePlayer(helper.getLevel(), "VrCombinationPacket");
        player.getInventory().setItem(player.getInventory().selected, copied);
        VillagerItemFilterMenu menu = new VillagerItemFilterMenu(103, player.getInventory(), copied);
        player.containerMenu = menu;
        VillagerItemFilterItem.handleCombinationChange(player, invalid.combinationId());
        helper.assertValueEqual(
                VillagerItemFilterData.entryCombination(player.getMainHandItem()),
                VillagerItemFilterData.EntryCombination.LEGACY,
                "an invalid packet should leave the filter untouched");
        VillagerItemFilterItem.handleCombinationChange(
                player, VillagerItemFilterData.EntryCombination.ANY.networkId());
        helper.assertValueEqual(
                VillagerItemFilterData.entryCombination(player.getMainHandItem()),
                VillagerItemFilterData.EntryCombination.ANY,
                "the server handler should accept ANY");
        VillagerItemFilterItem.handleCombinationChange(
                player, VillagerItemFilterData.EntryCombination.ALL.networkId());
        helper.assertValueEqual(
                VillagerItemFilterData.entryCombination(player.getMainHandItem()),
                VillagerItemFilterData.EntryCombination.ALL,
                "the server handler should synchronize ALL");
        menu.removed(player);
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void itemFilterAmountsCapPersistAndGateDuplicates(GameTestHelper helper) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        helper.assertTrue(VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.EMERALD)),
                "the first concrete identity should be accepted as unlimited");
        helper.assertValueEqual(VillagerItemFilterData.amount(filter, 0), 0,
                "new concrete entries should default to unlimited");
        helper.assertFalse(VillagerItemFilterData.setEntry(filter, 1, new ItemStack(Items.EMERALD)),
                "an unlimited identity should reject a duplicate");

        helper.assertTrue(VillagerItemFilterData.setAmount(filter, 0, 1000),
                "a concrete identity should accept the maximum stock amount");
        helper.assertValueEqual(VillagerItemFilterData.formatAmount(
                        VillagerItemFilterData.amount(filter, 0)), "1K",
                "the maximum should use the compact 1K label");
        helper.assertTrue(VillagerItemFilterData.setEntry(filter, 1, new ItemStack(Items.EMERALD)),
                "a positive amount should unlock a duplicate identity");
        helper.assertValueEqual(VillagerItemFilterData.amount(filter, 1), 1,
                "a newly added duplicate should start at one");
        helper.assertValueEqual(VillagerItemFilterData.minimumAmount(filter, 0), 1,
                "every member of a duplicate identity group should have a minimum of one");
        helper.assertValueEqual(VillagerItemFilterData.amountLimit(
                        filter, new ItemStack(Items.EMERALD)), 1001,
                "duplicate entry amounts should combine into one stock limit");

        VillagerItemFilterData.AmountAdjustment lowerBound =
                VillagerItemFilterData.adjustAmount(filter, 1, -10);
        helper.assertTrue(lowerBound.valid() && lowerBound.hitLimit() && !lowerBound.changed(),
                "scrolling a duplicate below one should report its lower bound without changing it");
        helper.assertValueEqual(VillagerItemFilterData.amount(filter, 1), 1,
                "a duplicate identity must remain at one or more");

        ItemStack copied = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.copyConfiguration(filter, copied);
        helper.assertValueEqual(VillagerItemFilterData.amount(copied, 0), 1000,
                "copying a filter should preserve its configured amounts");
        helper.assertValueEqual(VillagerItemFilterData.amount(copied, 1), 1,
                "copying a filter should preserve duplicate entry amounts");

        helper.assertTrue(VillagerItemFilterData.setEntry(filter, 0, ItemStack.EMPTY),
                "removing one duplicate should be accepted");
        helper.assertValueEqual(VillagerItemFilterData.minimumAmount(filter, 1), 0,
                "the final identity entry should be allowed to return to unlimited");
        helper.assertTrue(VillagerItemFilterData.adjustAmount(filter, 1, -1).changed(),
                "scrolling the final entry down from one should restore unlimited");
        helper.assertValueEqual(VillagerItemFilterData.amount(filter, 1), 0,
                "zero should persist as the unlimited sentinel");

        ItemStack nested = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(nested, 0, new ItemStack(Items.DIRT));
        helper.assertTrue(VillagerItemFilterData.setEntry(filter, 2, nested),
                "nested filters should remain valid entries");
        helper.assertFalse(VillagerItemFilterData.setAmount(filter, 2, 5),
                "nested filters must not acquire stock amounts");

        VillagerItemFilterData.setMode(copied, VillagerItemFilterData.Mode.DENYLIST);
        helper.assertValueEqual(VillagerItemFilterData.amountLimit(
                        copied, new ItemStack(Items.EMERALD)), 0,
                "denylist mode should disable stock-limit enforcement");

        ServerPlayer player = fakePlayer(helper.getLevel(), "VrAmountMenuReplacement");
        ItemStack openedFilter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(openedFilter, 0, new ItemStack(Items.EMERALD));
        player.getInventory().setItem(player.getInventory().selected, openedFilter);
        VillagerItemFilterMenu menu = new VillagerItemFilterMenu(
                97, player.getInventory(), openedFilter);
        ItemStack synchronizedReplacement = openedFilter.copy();
        player.getInventory().setItem(player.getInventory().selected, synchronizedReplacement);
        helper.assertTrue(menu.isEditingHeldFilter(),
                "replacing the synchronized held stack must not invalidate the open filter menu");
        helper.assertTrue(menu.adjustEntryAmount(0, 1).changed(),
                "amount scrolling should update the synchronized replacement stack");
        helper.assertValueEqual(VillagerItemFilterData.amount(synchronizedReplacement, 0), 1,
                "the amount change should target the live held filter instead of its stale opening object");
        helper.assertTrue(menu.adjustEntryAmount(0, 100).changed(),
                "the menu should accept the combined control and shift scroll increment");
        helper.assertValueEqual(VillagerItemFilterData.amount(synchronizedReplacement, 0), 101,
                "the combined control and shift increment should add one hundred");
        menu.removed(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void framedItemFilterCapsAssignedOutputDeposits(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrFramedAmountOutput");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        BlockPos outputRel = new BlockPos(5, 2, 2);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        Container chest = container(level, output);
        chest.setItem(0, new ItemStack(Items.EMERALD, 30));

        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.EMERALD));
        VillagerItemFilterData.setAmount(filter, 0, 32);
        ItemFrame filterFrame = new ItemFrame(level, output.relative(Direction.SOUTH), Direction.SOUTH);
        filterFrame.setItem(filter);
        helper.assertTrue(level.addFreshEntity(filterFrame), "amount filter item frame should spawn");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "amount-filtered output assignment");
        AssignedStorageSavedData.AssignedContainerRecord assigned =
                AssignedStorageService.assignedStorage(level, villager).getFirst();
        helper.assertTrue(assigned.outputFilterSnapshotKnown(),
                "assigning a loaded output should persist its item-frame configuration");
        helper.assertValueEqual(assigned.outputFilters().size(), 1, "persisted output filter count");
        helper.assertTrue(ItemStack.matches(assigned.outputFilters().getFirst(), filter.copyWithCount(1)),
                "the durable output filter should preserve item-filter components and amount limits");

        ItemStack remainder = AssignedStorageService.depositStack(villager, new ItemStack(Items.EMERALD, 10));
        helper.assertValueEqual(remainder.getCount(), 8,
                "assigned output should insert only the two items remaining under its stock limit");
        helper.assertValueEqual(countItem(chest, Items.EMERALD), 32,
                "existing contents should count toward the framed filter amount");
        helper.assertValueEqual(
                AssignedStorageService.assignedOutputCapacityFor(
                        villager,
                        new ItemStack(Items.EMERALD),
                        10),
                0,
                "a reached stock target should expose no downstream capacity");
        chest.removeItem(0, 2);
        helper.assertValueEqual(AssignedStorageService.assignedOutputCapacityFor(
                villager, new ItemStack(Items.EMERALD), 10), 2,
                "removing stock should immediately reopen exactly that much downstream capacity");
        chest.getItem(0).grow(2);
        helper.assertFalse(AssignedStorageService.courierOutputStorageAccepts(
                        level, villager, output, new ItemStack(Items.EMERALD)),
                "a capped output should stop accepting matching courier cargo");
        helper.assertValueEqual(AssignedStorageService.depositStack(
                        villager, new ItemStack(Items.DIRT, 4)).getCount(), 4,
                "the amount filter should still reject identities outside its expression");

        ItemFrame unlimitedFrame = new ItemFrame(level, output.relative(Direction.NORTH), Direction.NORTH);
        unlimitedFrame.setItem(new ItemStack(Items.EMERALD));
        helper.assertTrue(level.addFreshEntity(unlimitedFrame), "unlimited item frame should spawn");
        ContainerFilterResolver.invalidateFrame(level, unlimitedFrame);
        ItemStack constrainedRemainder = AssignedStorageService.depositStack(
                villager, new ItemStack(Items.EMERALD, 3));
        helper.assertValueEqual(constrainedRemainder.getCount(), 3,
                "a finite matching frame should remain more restrictive than an unlimited alternative");
        helper.assertValueEqual(countItem(chest, Items.EMERALD), 32,
                "an unlimited alternative must not bypass a reached finite target");
        AssignedStorageService.assignedOutputCapacityFor(villager, new ItemStack(Items.EMERALD), 1);
        AssignedStorageSavedData.AssignedContainerRecord refreshed =
                AssignedStorageService.assignedStorage(level, villager).getFirst();
        helper.assertValueEqual(refreshed.outputFilters().size(), 2,
                "an observable frame edit should refresh the durable output-filter snapshot");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        unlimitedFrame.discard();
        filterFrame.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void framedItemFilterCountsBothHalvesOfDoubleChest(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrFramedAmountDoubleChest");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        BlockPos leftRel = new BlockPos(5, 2, 2);
        BlockPos rightRel = leftRel.east();
        BlockPos left = helper.absolutePos(leftRel);
        BlockPos right = helper.absolutePos(rightRel);
        setBlock(helper, leftRel, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT));
        setBlock(helper, rightRel, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT));
        container(level, left).setItem(0, new ItemStack(Items.EMERALD, 20));
        container(level, right).setItem(0, new ItemStack(Items.EMERALD, 11));

        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.EMERALD));
        VillagerItemFilterData.setAmount(filter, 0, 32);
        ItemFrame frame = new ItemFrame(level, right.relative(Direction.SOUTH), Direction.SOUTH);
        frame.setItem(filter);
        helper.assertTrue(level.addFreshEntity(frame), "double-chest amount filter should spawn");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), left)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "double-chest output assignment");

        ItemStack remainder = AssignedStorageService.depositStackAtAssignedStorage(
                villager, left, new ItemStack(Items.EMERALD, 5));
        helper.assertValueEqual(remainder.getCount(), 4,
                "only one emerald should fit under the combined double-chest limit");
        helper.assertValueEqual(
                countItem(container(level, left), Items.EMERALD)
                        + countItem(container(level, right), Items.EMERALD),
                32,
                "both chest halves should contribute to one stock count");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        frame.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void attachedFilterResolverTracksLogicalContainerAndInvalidation(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        BlockPos leftRel = new BlockPos(4, 2, 2);
        BlockPos rightRel = leftRel.east();
        BlockPos left = helper.absolutePos(leftRel);
        BlockPos right = helper.absolutePos(rightRel);
        setDoubleChest(helper, leftRel, rightRel);

        ItemStack listFilter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(listFilter, 0, new ItemStack(Items.DIRT));
        ItemFrame listFrame = new ItemFrame(level, left.relative(Direction.SOUTH), Direction.SOUTH);
        listFrame.setItem(listFilter);
        helper.assertTrue(level.addFreshEntity(listFrame), "list filter frame should spawn");

        ItemStack attributeFilter = attributeTag("minecraft:logs");
        GlowItemFrame glowFrame = new GlowItemFrame(level, right.relative(Direction.SOUTH), Direction.SOUTH);
        glowFrame.setItem(attributeFilter);
        helper.assertTrue(level.addFreshEntity(glowFrame), "glow filter frame should spawn");

        ItemFrame legacyFrame = new ItemFrame(level, right.relative(Direction.NORTH), Direction.NORTH);
        legacyFrame.setItem(new ItemStack(Items.DIAMOND));
        helper.assertTrue(level.addFreshEntity(legacyFrame), "legacy exact-item frame should spawn");

        level.setBlockAndUpdate(left.above(), Blocks.STONE.defaultBlockState());
        ItemFrame nearbyFrame = new ItemFrame(level, left.above().relative(Direction.SOUTH), Direction.SOUTH);
        nearbyFrame.setItem(new ItemStack(Items.GOLD_INGOT));
        helper.assertTrue(level.addFreshEntity(nearbyFrame), "nearby unrelated frame should spawn");

        ContainerFilterResolver.clearRuntimeState();
        VillagerInventoryOverflowService.ContainerCandidate candidate =
                VillagerInventoryOverflowService.ContainerCandidate.resolve(level, left);
        helper.assertTrue(candidate != null, "double chest should resolve as one logical container");
        helper.assertValueEqual(candidate.positions().size(), 2, "logical double-chest positions");

        ContainerFilterResolver.Resolution first = ContainerFilterResolver.resolve(level, candidate);
        helper.assertTrue(first.live(), "loaded frame neighborhood should resolve live");
        helper.assertValueEqual(first.rules().size(), 3, "both halves and glow frames should be combined once");
        helper.assertTrue(first.rules().stream().anyMatch(stack -> ItemStack.matches(stack, listFilter.copyWithCount(1))),
                "configured list filter should be preserved");
        helper.assertTrue(first.rules().stream().anyMatch(stack -> ItemStack.matches(stack, attributeFilter.copyWithCount(1))),
                "configured attribute filter should be preserved");
        helper.assertTrue(first.rules().stream().anyMatch(stack -> stack.is(Items.DIAMOND)),
                "ordinary framed items should retain legacy exact-item behavior");
        helper.assertValueEqual(ContainerFilterResolver.cachedContainerCount(), 1,
                "both halves should share one cache entry");

        listFrame.setRotation(5);
        ContainerFilterResolver.invalidateFrame(level, listFrame);
        ContainerFilterResolver.Resolution rotated = ContainerFilterResolver.resolve(level, candidate);
        helper.assertValueEqual(rotated.rules().size(), 3,
                "frame rotation must not change its attached container");

        listFrame.setItem(new ItemStack(Items.EMERALD));
        ContainerFilterResolver.invalidateFrame(level, listFrame);
        ContainerFilterResolver.Resolution replaced = ContainerFilterResolver.resolve(level, candidate);
        helper.assertTrue(replaced.rules().stream().anyMatch(stack -> stack.is(Items.EMERALD)),
                "replacing a framed rule should invalidate the cached result");
        helper.assertFalse(replaced.rules().stream().anyMatch(stack -> ItemStack.matches(stack, listFilter)),
                "the replaced configured rule must not remain cached");

        glowFrame.setItem(ItemStack.EMPTY);
        ContainerFilterResolver.invalidateFrame(level, glowFrame);
        helper.assertValueEqual(ContainerFilterResolver.resolve(level, candidate).rules().size(), 2,
                "removing a framed rule should invalidate the cached result");

        ContainerFilterResolver.invalidateContainer(level, right);
        helper.assertValueEqual(ContainerFilterResolver.cachedContainerCount(), 0,
                "changing either chest half should invalidate the logical container");

        listFrame.discard();
        glowFrame.discard();
        legacyFrame.discard();
        nearbyFrame.discard();
        ContainerFilterResolver.clearRuntimeState();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void itemHandlerContainerAdapterSimulatesAndCommitsSafely(GameTestHelper helper) {
        ItemStackHandler handler = new ItemStackHandler(2);
        ItemHandlerContainerAdapter adapter = new ItemHandlerContainerAdapter(handler);
        helper.assertValueEqual(adapter.insertionCapacity(new ItemStack(Items.EMERALD), 100), 100,
                "simulated capacity should span capability slots without mutation");
        helper.assertTrue(handler.getStackInSlot(0).isEmpty() && handler.getStackInSlot(1).isEmpty(),
                "capacity simulation must not mutate the item handler");
        ItemStack remainder = adapter.insert(new ItemStack(Items.EMERALD, 80), false);
        helper.assertTrue(remainder.isEmpty(), "capability insertion should accept available space");
        helper.assertValueEqual(
                handler.getStackInSlot(0).getCount() + handler.getStackInSlot(1).getCount(), 80,
                "committed insertion should preserve the full item count");
        helper.assertValueEqual(adapter.extract(0, 20, false).getCount(), 20,
                "capability extraction should return the amount actually removed");
        helper.assertValueEqual(
                handler.getStackInSlot(0).getCount() + handler.getStackInSlot(1).getCount(), 60,
                "capability extraction should neither duplicate nor lose the remaining stock");
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
        helper.assertTrue(unauthorizedMenu.getSlot(HiredJobInventory.FILTER_SLOT).mayPlace(
                        new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get())),
                "the dedicated slot should accept item filters");
        helper.assertTrue(unauthorizedMenu.getSlot(HiredJobInventory.FILTER_SLOT).mayPlace(
                        new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get())),
                "the dedicated slot should accept attribute filters");
        helper.assertFalse(unauthorizedMenu.getSlot(HiredJobInventory.FILTER_SLOT).mayPlace(
                        new ItemStack(Items.DIRT)),
                "the dedicated slot should reject ordinary items");
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
    public static void villagerAttributeFilterCopyRecipeCopiesOnlyConfiguration(GameTestHelper helper) {
        ItemStack configured = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        VillagerAttributeFilterData.setSelected(
                configured,
                new VillagerAttributeFilterData.Attribute(
                        VillagerAttributeFilterData.AttributeType.PLACEABLE,
                        ""),
                true);
        configured.set(DataComponents.CUSTOM_NAME, Component.literal("Do not copy this"));
        ItemStack empty = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        CraftingInput input = CraftingInput.of(2, 1, List.of(configured, empty));
        VillagerAttributeFilterCopyRecipe recipe =
                new VillagerAttributeFilterCopyRecipe(CraftingBookCategory.MISC);

        helper.assertTrue(recipe.matches(input, helper.getLevel()),
                "configured and default attribute filters should match in either order");
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(VillagerRetaliationItems.ATTRIBUTE_FILTER.get()),
                "copy recipe should produce attribute filters");
        helper.assertValueEqual(result.getCount(), 2,
                "copy recipe should produce two attribute filters");
        helper.assertValueEqual(
                VillagerAttributeFilterData.read(result),
                VillagerAttributeFilterData.read(configured),
                "copy recipe should preserve the selected attribute and inversion");
        helper.assertFalse(result.has(DataComponents.CUSTOM_NAME),
                "copy recipe should not copy unrelated components");
        helper.assertFalse(recipe.matches(
                        CraftingInput.of(2, 1, List.of(empty, empty.copy())),
                        helper.getLevel()),
                "two default attribute filters should not match");
        helper.assertFalse(recipe.matches(
                        CraftingInput.of(2, 1, List.of(configured, new ItemStack(Items.PAPER))),
                        helper.getLevel()),
                "unrelated ingredients should invalidate the recipe");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerFilterResetRecipeClearsBothFilterTypes(GameTestHelper helper) {
        VillagerFilterResetRecipe recipe = new VillagerFilterResetRecipe(CraftingBookCategory.MISC);

        ItemStack itemFilter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(itemFilter, 0, new ItemStack(Items.COBBLESTONE));
        VillagerItemFilterData.setMode(itemFilter, VillagerItemFilterData.Mode.DENYLIST);
        itemFilter.set(DataComponents.CUSTOM_NAME, Component.literal("Reset me"));
        CraftingInput itemInput = CraftingInput.of(1, 1, List.of(itemFilter));
        helper.assertTrue(recipe.matches(itemInput, helper.getLevel()),
                "a single item filter should match the reset recipe");
        ItemStack resetItemFilter = recipe.assemble(itemInput, helper.getLevel().registryAccess());
        helper.assertTrue(resetItemFilter.is(VillagerRetaliationItems.ITEM_FILTER.get())
                        && resetItemFilter.getCount() == 1
                        && VillagerItemFilterData.isDefault(resetItemFilter),
                "resetting should return one default item filter");
        helper.assertFalse(resetItemFilter.has(DataComponents.CUSTOM_DATA)
                        || resetItemFilter.has(DataComponents.CUSTOM_NAME),
                "reset item filter should not retain NBT or unrelated components");

        ItemStack attributeFilter = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        VillagerAttributeFilterData.setSelected(
                attributeFilter,
                new VillagerAttributeFilterData.Attribute(
                        VillagerAttributeFilterData.AttributeType.PLACEABLE,
                        ""),
                true);
        attributeFilter.set(DataComponents.CUSTOM_NAME, Component.literal("Reset me too"));
        CraftingInput attributeInput = CraftingInput.of(1, 1, List.of(attributeFilter));
        helper.assertTrue(recipe.matches(attributeInput, helper.getLevel()),
                "a single attribute filter should match the reset recipe");
        ItemStack resetAttributeFilter =
                recipe.assemble(attributeInput, helper.getLevel().registryAccess());
        helper.assertTrue(resetAttributeFilter.is(VillagerRetaliationItems.ATTRIBUTE_FILTER.get())
                        && resetAttributeFilter.getCount() == 1
                        && VillagerAttributeFilterData.isDefault(resetAttributeFilter),
                "resetting should return one default attribute filter");
        helper.assertFalse(resetAttributeFilter.has(DataComponents.CUSTOM_DATA)
                        || resetAttributeFilter.has(DataComponents.CUSTOM_NAME),
                "reset attribute filter should not retain NBT or unrelated components");

        helper.assertFalse(recipe.matches(
                        CraftingInput.of(2, 1, List.of(itemFilter, attributeFilter)),
                        helper.getLevel()),
                "more than one filter should invalidate the reset recipe");
        helper.assertFalse(recipe.matches(
                        CraftingInput.of(1, 1, List.of(new ItemStack(Items.PAPER))),
                        helper.getLevel()),
                "ordinary items should not match the reset recipe");
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
    public static void openPersonalInventoryCannotDuplicateBorrowedWeapons(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrOpenWeaponInventory");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        helper.assertTrue(
                VillagerInventoryContainer.addItem(villager, new ItemStack(Items.NETHERITE_SWORD)).isEmpty(),
                "personal inventory should accept the sword");
        helper.assertTrue(
                VillagerInventoryContainer.addItem(villager, new ItemStack(Items.CROSSBOW)).isEmpty(),
                "personal inventory should accept the crossbow");

        VillagerInventoryContainer openInventory = new VillagerInventoryContainer(villager);
        openInventory.startOpen(player);
        helper.assertFalse(
                VillagerInventoryContainer.tryBorrowCombatWeapon(
                        villager,
                        VillagerRetaliationVillagerWeapons::isCrossbowWeapon),
                "quick-command loadout changes must not borrow from an open personal inventory");
        helper.assertTrue(villager.getMainHandItem().isEmpty(), "blocked borrowing must not equip a copied weapon");
        helper.assertValueEqual(countStored(villager, Items.NETHERITE_SWORD), 1, "the sword count must remain exact");
        helper.assertValueEqual(countStored(villager, Items.CROSSBOW), 1, "the crossbow count must remain exact");
        openInventory.stopOpen(player);

        helper.assertTrue(
                VillagerInventoryContainer.tryBorrowCombatWeapon(
                        villager,
                        VillagerRetaliationVillagerWeapons::isCrossbowWeapon),
                "borrowing should resume after the personal inventory closes");
        helper.assertTrue(villager.getMainHandItem().is(Items.CROSSBOW), "the crossbow should move to the main hand");
        helper.assertValueEqual(countStored(villager, Items.CROSSBOW), 0, "a borrowed crossbow must leave storage");
        VillagerInventoryContainer.returnBorrowedCombatWeapon(villager);
        helper.assertValueEqual(countStored(villager, Items.NETHERITE_SWORD), 1, "the sword must not duplicate during the cycle");
        helper.assertValueEqual(countStored(villager, Items.CROSSBOW), 1, "the crossbow must return exactly once");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void closingStalePersonalInventoryDoesNotResurrectItems(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrStaleInventoryClose");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        VillagerInventoryContainer.addItem(villager, new ItemStack(Items.NETHERITE_SWORD));
        VillagerInventoryContainer openInventory = new VillagerInventoryContainer(villager);
        openInventory.startOpen(player);

        VillagerInventoryContainer.saveFullInventory(
                villager,
                NonNullList.withSize(VillagerInventoryContainer.INVENTORY_SLOT_COUNT, ItemStack.EMPTY));
        openInventory.stopOpen(player);

        helper.assertValueEqual(
                countStored(villager, Items.NETHERITE_SWORD),
                0,
                "closing an older menu snapshot must not write removed items back");
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
    public static void jobInventoryReclaimsBorrowedMainHandWithoutDuplicating(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager,
                EquipmentSlot.MAINHAND,
                new ItemStack(Items.GOLDEN_AXE));
        VillagerInventoryContainer.addItem(villager, new ItemStack(Items.NETHERITE_SWORD));
        helper.assertTrue(
                VillagerInventoryContainer.tryBorrowCombatWeapon(villager),
                "villager should borrow its personal weapon before receiving job gear");

        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        jobInventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));

        helper.assertFalse(
                VillagerInventoryContainer.hasBorrowedCombatWeapon(villager),
                "job equipment should settle the personal weapon loan");
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_PICKAXE),
                "job equipment should own the live main hand");
        helper.assertValueEqual(countStored(villager, Items.NETHERITE_SWORD), 1,
                "borrowed personal weapon should return exactly once");
        helper.assertValueEqual(countStored(villager, Items.GOLDEN_AXE), 1,
                "displaced personal main hand should be stored exactly once");
        helper.assertValueEqual(countStored(villager, Items.IRON_PICKAXE), 0,
                "active job gear must not be copied into personal inventory");
        helper.assertTrue(
                VillagerInventoryContainer.tryBorrowCombatWeapon(villager),
                "personal combat weapons may temporarily overlay authoritative job equipment");
        helper.assertTrue(villager.getMainHandItem().is(Items.NETHERITE_SWORD)
                        && jobInventory.getItem(HiredJobInventory.MAINHAND_SLOT).is(Items.IRON_PICKAXE),
                "the borrowed weapon must not remove the authoritative job stack");
        VillagerInventoryContainer.returnBorrowedCombatWeapon(villager);
        helper.assertFalse(VillagerInventoryContainer.hasBorrowedCombatWeapon(villager),
                "returning the personal loan should clear its runtime ownership");
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_PICKAXE),
                "returning the personal loan should restore authoritative job equipment");
        helper.assertValueEqual(countStored(villager, Items.NETHERITE_SWORD), 1,
                "the overlaid personal weapon should return exactly once");

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
    public static void jobInventoryMenuQuickMoveSeparatesSuppliesAndEquipables(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrJobMenuQuickMove");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        player.moveTo(villager.getX(), villager.getY(), villager.getZ());
        HiredVillagerContractService.startHireContract(level, villager, player, 1, 0);

        player.getInventory().setItem(9, new ItemStack(Items.WHEAT, 8));
        player.getInventory().setItem(10, new ItemStack(Items.ELYTRA));
        VillagerInventoryMenu menu = new VillagerInventoryMenu(
                3,
                player.getInventory(),
                villager,
                VillagerInventoryMenu.ViewMode.JOB,
                false,
                true);

        helper.assertFalse(menu.quickMoveStack(player, HiredJobInventory.SLOT_COUNT).isEmpty(),
                "shift-clicking supplies should move the stack");
        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        helper.assertTrue(jobInventory.getItem(HiredJobInventory.MAINHAND_SLOT).isEmpty()
                        && jobInventory.getItem(HiredJobInventory.OFFHAND_SLOT).isEmpty(),
                "ordinary supplies must not occupy either equipment hand");
        helper.assertTrue(jobInventory.getItem(HiredJobInventory.MAIN_GRID_START).is(Items.WHEAT)
                        && jobInventory.getItem(HiredJobInventory.MAIN_GRID_START).getCount() == 8,
                "ordinary supplies should start in the main supply grid");
        helper.assertValueEqual(
                jobInventory.slotType(HiredJobInventory.MAIN_GRID_START),
                HiredJobInventorySlotType.SUPPLY,
                "quick-moved supplies should retain supply ownership");

        helper.assertFalse(menu.quickMoveStack(player, HiredJobInventory.SLOT_COUNT + 1).isEmpty(),
                "shift-clicking a component-based wearable should move the stack");
        helper.assertTrue(jobInventory.getItem(1).is(Items.ELYTRA),
                "elytra should route to the chest equipment slot");

        menu.removed(player);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void inventoryValidityHonorsConfiguredDialogueDistance(GameTestHelper helper) {
        buildFloor(helper, 0, 16, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrConfiguredInventoryDistance");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        player.moveTo(villager.getX() + 12.0D, villager.getY(), villager.getZ());

        double previousDistance = VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();
        try {
            VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.set(16.0D);
            helper.assertTrue(new VillagerInventoryContainer(villager).stillValid(player),
                    "personal inventory validity should honor a configured distance above eight blocks");
            helper.assertTrue(HiredJobInventory.getJobInventory(villager).stillValid(player),
                    "job inventory validity should honor a configured distance above eight blocks");
        } finally {
            VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.set(previousDistance);
        }

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
        helper.assertFalse(record.outputFilterSnapshotKnown(),
                "legacy storage should remain fail-closed until its frame configuration is observed");

        CompoundTag saved = loaded.save(new CompoundTag(), level.registryAccess());
        helper.assertValueEqual(saved.getList("Entries", Tag.TAG_COMPOUND).size(), 1, "save should keep only valid assigned storage");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignedStorageMigratesToolAndInputPurposesToSupplies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID villagerId = UUID.nameUUIDFromBytes(
                "villagerretaliation:storage-purpose-migration".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CompoundTag root = new CompoundTag();
        ListTag entries = new ListTag();
        for (int index = 0; index < 2; index++) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", level.dimension().location().toString());
            entry.putLong("Pos", helper.absolutePos(new BlockPos(2 + index, 2, 2)).asLong());
            entry.putUUID("Villager", villagerId);
            entry.putString("Purpose", index == 0 ? "tool" : "input");
            entries.add(entry);
        }
        root.put("Entries", entries);

        AssignedStorageSavedData loaded = AssignedStorageSavedData.load(root, level.registryAccess());
        List<AssignedStorageSavedData.AssignedContainerRecord> supplies =
                loaded.assignedTo(villagerId, AssignedStorageService.SUPPLY_PURPOSE);
        helper.assertValueEqual(supplies.size(), 2, "legacy tool and input assignments should both become supplies");
        helper.assertTrue(supplies.stream().allMatch(record -> AssignedStorageService.SUPPLY_PURPOSE.equals(record.purpose())),
                "migrated assignments should expose only the canonical supply purpose");

        CompoundTag saved = loaded.save(new CompoundTag(), level.registryAccess());
        ListTag savedEntries = saved.getList("Entries", Tag.TAG_COMPOUND);
        helper.assertTrue(savedEntries.stream()
                        .filter(CompoundTag.class::isInstance)
                        .map(CompoundTag.class::cast)
                        .allMatch(entry -> AssignedStorageService.SUPPLY_PURPOSE.equals(entry.getString("Purpose"))),
                "migrated assignments should save back using only the supply purpose");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignedStoragePersistsOutputFilterSnapshots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.DIAMOND));
        VillagerItemFilterData.setAmount(filter, 0, 24);
        UUID villagerId = UUID.nameUUIDFromBytes(
                "villagerretaliation:durable-output-filter".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AssignedStorageSavedData data = new AssignedStorageSavedData();
        data.assign(new AssignedStorageSavedData.AssignedContainerRecord(
                level.dimension(),
                helper.absolutePos(new BlockPos(2, 2, 2)),
                villagerId,
                UUID.randomUUID(),
                AssignedStorageService.OUTPUT_PURPOSE,
                0,
                "valid",
                List.of(filter),
                true));

        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        AssignedStorageSavedData loaded = AssignedStorageSavedData.load(saved, level.registryAccess());
        AssignedStorageSavedData.AssignedContainerRecord restored = loaded.assignedTo(villagerId).getFirst();
        helper.assertTrue(restored.outputFilterSnapshotKnown(),
                "serialized output assignment should retain a known filter snapshot");
        helper.assertValueEqual(restored.outputFilters().size(), 1,
                "serialized output assignment should retain every framed filter");
        helper.assertTrue(ItemStack.matches(restored.outputFilters().getFirst(), filter.copyWithCount(1)),
                "serialized output assignment should retain filter identity, expression, and amount limit");
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

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void supplyTransformIsAtomicWhenCraftedOutputCannotFit(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        fillMainJobGrid(inventory, Items.COBBLESTONE);
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.HOTBAR_START; slot++) {
            inventory.markPlayerPlacedSupply(slot);
        }
        for (int slot = HiredJobInventory.HOTBAR_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
        }

        int ingredientSlot = HiredJobInventory.MAIN_GRID_START;
        inventory.setItem(ingredientSlot, new ItemStack(Items.GLASS_BOTTLE, 2));
        helper.assertFalse(inventory.tryTransformSupplies(
                        Map.of(Items.GLASS_BOTTLE, 1),
                        List.of(new ItemStack(Items.POTION))),
                "a full inventory must reject a transform whose ingredient stack remains occupied");
        helper.assertValueEqual(inventory.getItem(ingredientSlot).getCount(), 2,
                "a rejected transform must not consume ingredients");

        inventory.setItem(ingredientSlot, new ItemStack(Items.GLASS_BOTTLE));
        helper.assertTrue(inventory.tryTransformSupplies(
                        Map.of(Items.GLASS_BOTTLE, 1),
                        List.of(new ItemStack(Items.POTION))),
                "a transform should reuse a slot freed by its consumed ingredient");
        helper.assertTrue(inventory.getItem(ingredientSlot).is(Items.POTION),
                "the produced item should occupy the freed supply slot");

        inventory.setItem(ingredientSlot, new ItemStack(Items.BUCKET, 2));
        inventory.markPlayerPlacedSupply(ingredientSlot);
        helper.assertFalse(inventory.tryTransformSuppliesToOutputs(
                        Map.of(Items.BUCKET, 1),
                        List.of(new ItemStack(Items.MILK_BUCKET))),
                "a rejected supply-to-output transform must remain atomic");
        helper.assertValueEqual(inventory.getItem(ingredientSlot).getCount(), 2,
                "a rejected supply-to-output transform must retain its input");

        inventory.setItem(ingredientSlot, new ItemStack(Items.BUCKET));
        helper.assertTrue(inventory.tryTransformSuppliesToOutputs(
                        Map.of(Items.BUCKET, 1),
                        List.of(new ItemStack(Items.MILK_BUCKET))),
                "an output transform should reuse a default output slot freed by its input");
        helper.assertTrue(inventory.getItem(ingredientSlot).is(Items.MILK_BUCKET),
                "the transformed output should occupy the freed grid slot");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void outputPromotionNeverExceedsRequestedCount(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        fillMainJobGrid(inventory, Items.COBBLESTONE);
        helper.assertTrue(inventory.insertOutput(new ItemStack(Items.ARROW, 64)).isEmpty(),
                "the output fixture should claim the first hotbar slot");
        int outputSlot = HiredJobInventory.HOTBAR_START;
        int supplySlot = outputSlot + 1;
        inventory.setItem(supplySlot, new ItemStack(Items.ARROW, 63));
        for (int slot = supplySlot + 1; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
        }

        helper.assertValueEqual(inventory.promoteOutputToSupply(stack -> stack.is(Items.ARROW), 1), 1,
                "promotion must report only the requested item count");
        helper.assertValueEqual(inventory.getItem(outputSlot).getCount(), 63,
                "partial promotion must remove exactly one output item");
        helper.assertTrue(inventory.isOutputSlot(outputSlot),
                "a partially promoted source stack must remain output");
        helper.assertValueEqual(inventory.getItem(supplySlot).getCount(), 64,
                "the promoted item should merge into supply exactly once");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void containerTransfersTrustObservedChangesNotRequestedCounts(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        SimpleContainer rejectingInsert = new SimpleContainer(1) {
            @Override
            public void setItem(int slot, ItemStack stack) {
                // Simulates a modded container rejecting an otherwise advertised slot.
            }
        };
        ItemStack insertionRemainder = VillagerInventoryOverflowService.insertIntoContainer(
                rejectingInsert, new ItemStack(Items.DIAMOND, 5));
        helper.assertValueEqual(insertionRemainder.getCount(), 5,
                "a rejected container insertion must retain the entire source stack");
        helper.assertTrue(rejectingInsert.isEmpty(),
                "a rejecting container should remain empty");

        SimpleContainer rejectingRemoval = new SimpleContainer(new ItemStack(Items.DIAMOND, 5)) {
            @Override
            public ItemStack removeItem(int slot, int amount) {
                return getItem(slot).copyWithCount(Math.min(amount, getItem(slot).getCount()));
            }
        };
        ItemStack extracted = VillagerInventoryOverflowService.extractUpTo(
                villager, rejectingRemoval, 0, 3);
        helper.assertTrue(extracted.isEmpty(),
                "a container that did not change must not be credited as withdrawn");
        helper.assertValueEqual(rejectingRemoval.getItem(0).getCount(), 5,
                "a rejected withdrawal must leave the source untouched");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void farmOverflowDropsOnlyTheUnstoredRemainder(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        fillMainJobGrid(inventory, Items.COBBLESTONE);
        inventory.setItem(HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.WHEAT, 63));
        for (int slot = HiredJobInventory.HOTBAR_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.WHEAT, 8));

        helper.assertFalse(HiredFarmingInventoryBridge.storeFarmDrops(
                        villager,
                        inventory,
                        List.of(new ItemStack(Items.WHEAT, 5))),
                "partial farm storage should report overflow");
        helper.assertValueEqual(inventory.getItem(HiredJobInventory.MAIN_GRID_START).getCount(), 64,
                "farm storage should retain the portion that fit");
        int dropped = helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        villager.getBoundingBox().inflate(2.0D),
                        entity -> entity.getItem().is(Items.WHEAT))
                .stream()
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
        helper.assertValueEqual(dropped, 4,
                "farm overflow should drop exactly the portion that did not fit");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void deathDropsTrackedMainHandInsteadOfTransientOverride(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager,
                EquipmentSlot.MAINHAND,
                new ItemStack(Items.NETHERITE_SWORD));
        VillagerRetaliationVillagerEquipment.setTemporaryMainHand(
                villager,
                new ItemStack(Items.CROSSBOW),
                0.0F);

        List<ItemEntity> drops = new ArrayList<>();
        LivingDropsEvent event = new LivingDropsEvent(villager, villager.damageSources().generic(), drops, false);
        VillagerInventoryAccess.dropAllInventoryAndEquipment(villager, event);

        helper.assertValueEqual(
                countEventDrops(drops, Items.NETHERITE_SWORD),
                1,
                "death must preserve the real tracked weapon exactly once");
        helper.assertValueEqual(
                countEventDrops(drops, Items.CROSSBOW),
                0,
                "a zero-drop-chance transient hand must not replace owned inventory in death drops");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void deathDropsBorrowedLoadoutExactlyOnce(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager,
                EquipmentSlot.MAINHAND,
                new ItemStack(Items.NETHERITE_SWORD));
        VillagerInventoryContainer.addItem(villager, new ItemStack(Items.CROSSBOW));
        helper.assertTrue(
                VillagerInventoryContainer.tryBorrowCombatWeapon(
                        villager,
                        VillagerRetaliationVillagerWeapons::isCrossbowWeapon),
                "the ranged loadout should borrow the crossbow");

        List<ItemEntity> drops = new ArrayList<>();
        LivingDropsEvent event = new LivingDropsEvent(villager, villager.damageSources().generic(), drops, false);
        VillagerInventoryAccess.dropAllInventoryAndEquipment(villager, event);

        helper.assertValueEqual(countEventDrops(drops, Items.NETHERITE_SWORD), 1, "the displaced sword must drop once");
        helper.assertValueEqual(countEventDrops(drops, Items.CROSSBOW), 1, "the borrowed crossbow must drop once");
        helper.assertValueEqual(countStored(villager, Items.NETHERITE_SWORD), 0, "death must clear stored sword state");
        helper.assertValueEqual(countStored(villager, Items.CROSSBOW), 0, "death must clear stored crossbow state");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyDropPickupConservesPartialAndRejectedStacks(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        inventory.setItem(HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.WHEAT, 63));
        ItemEntity groundItem = new ItemEntity(
                level,
                villager.getX(),
                villager.getY(),
                villager.getZ(),
                new ItemStack(Items.WHEAT, 5));

        helper.assertValueEqual(
                PartyVillagerDropCollection.collectAny(villager, groundItem),
                1,
                "pickup should move only the available capacity");
        helper.assertValueEqual(countJobInventoryItem(inventory, Items.WHEAT), 64, "inventory should receive one item");
        helper.assertValueEqual(groundItem.getItem().getCount(), 4, "the exact remainder must stay on the ground");
        helper.assertValueEqual(
                PartyVillagerDropCollection.collectAny(villager, groundItem),
                0,
                "a full inventory must reject the remaining ground stack");
        helper.assertValueEqual(groundItem.getItem().getCount(), 4, "a rejected pickup must not consume or copy items");

        groundItem.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void carriedTotemReplacesAndRestoresRoleOffhand(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        VillagerRetaliationVillagerEquipment.setRoleEquipment(
                villager, EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.TOTEM_OF_UNDYING));

        runDefensiveLoadoutScan(helper.getLevel(), villager);
        helper.assertTrue(villager.getOffhandItem().is(Items.TOTEM_OF_UNDYING),
                "a carried totem should replace role-controlled off-hand gear");
        helper.assertValueEqual(countStored(villager, Items.SHIELD), 1,
                "the displaced role shield should occupy the totem's source slot");
        helper.assertValueEqual(countStored(villager, Items.TOTEM_OF_UNDYING), 0,
                "the equipped totem must have exactly one owner");

        VillagerDefensiveLoadoutService.prepareForInventoryAccess(villager);
        helper.assertTrue(villager.getOffhandItem().is(Items.SHIELD),
                "opening inventory should restore displaced off-hand gear");
        helper.assertValueEqual(countStored(villager, Items.TOTEM_OF_UNDYING), 1,
                "returning the loan should restore the same totem to storage");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void manualOffhandOverridesTotemButManualMainhandDoesNot(GameTestHelper helper) {
        Villager offhandOverride = spawnVillager(helper, new BlockPos(1, 2, 1));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                offhandOverride, EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        VillagerInventoryAccess.addItem(offhandOverride, new ItemStack(Items.TOTEM_OF_UNDYING));
        runDefensiveLoadoutScan(helper.getLevel(), offhandOverride);
        helper.assertTrue(offhandOverride.getOffhandItem().is(Items.SHIELD)
                        && countStored(offhandOverride, Items.TOTEM_OF_UNDYING) == 1,
                "an explicitly assigned off-hand should remain authoritative");

        Villager mainhandOverride = spawnVillager(helper, new BlockPos(3, 2, 1));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                mainhandOverride, EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        VillagerInventoryAccess.addItem(mainhandOverride, new ItemStack(Items.TOTEM_OF_UNDYING));
        runDefensiveLoadoutScan(helper.getLevel(), mainhandOverride);
        helper.assertTrue(mainhandOverride.getMainHandItem().is(Items.IRON_SWORD)
                        && mainhandOverride.getOffhandItem().is(Items.TOTEM_OF_UNDYING),
                "manual main-hand gear should not block automatic off-hand protection");

        offhandOverride.discard();
        mainhandOverride.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void consumedBorrowedTotemReplenishesWithoutDuplication(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.TOTEM_OF_UNDYING));
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.TOTEM_OF_UNDYING));
        runDefensiveLoadoutScan(helper.getLevel(), villager);

        villager.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        helper.assertTrue(villager.isAlive(), "the automatically borrowed totem should prevent death");
        VillagerDefensiveLoadoutService.onVillagerTickPost(villager);

        helper.assertTrue(villager.getOffhandItem().is(Items.TOTEM_OF_UNDYING),
                "the next carried totem should replenish the consumed off-hand loan");
        helper.assertValueEqual(countStored(villager, Items.TOTEM_OF_UNDYING), 0,
                "one consumed and one equipped totem should leave no stored duplicate");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void babyVillagersDoNotAutoEquipTotems(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        villager.setBaby(true);
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.TOTEM_OF_UNDYING));
        VillagerDefensiveLoadoutService.onVillagerTickPost(villager);
        helper.assertTrue(villager.getOffhandItem().isEmpty()
                        && countStored(villager, Items.TOTEM_OF_UNDYING) == 1,
                "baby villagers should leave carried totems in storage");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void totemsProtectVillagersFromEitherHand(GameTestHelper helper) {
        Villager mainhand = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager offhand = spawnVillager(helper, new BlockPos(3, 2, 1));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                mainhand, EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                offhand, EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        mainhand.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        offhand.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        VillagerRetaliationVillagerEquipment.maintainPlayerManagedMainHand(mainhand);
        helper.assertTrue(mainhand.isAlive() && mainhand.getMainHandItem().isEmpty(),
                "a main-hand totem should stay consumed after tracked-equipment maintenance");
        helper.assertTrue(offhand.isAlive() && offhand.getOffhandItem().isEmpty(),
                "an off-hand totem should be consumed while protecting its villager");

        mainhand.discard();
        offhand.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void jobTotemsAreConsumedFromEitherHand(GameTestHelper helper) {
        Villager mainhand = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager offhand = spawnVillager(helper, new BlockPos(3, 2, 1));
        HiredJobInventory mainInventory = HiredJobInventory.getJobInventory(mainhand);
        HiredJobInventory offInventory = HiredJobInventory.getJobInventory(offhand);
        mainInventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.TOTEM_OF_UNDYING));
        offInventory.setItem(HiredJobInventory.OFFHAND_SLOT, new ItemStack(Items.TOTEM_OF_UNDYING));

        mainhand.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        offhand.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        HiredJobInventory.clearRuntimeState(mainhand);
        HiredJobInventory.clearRuntimeState(offhand);
        HiredJobInventory.maintainEquipmentSlots(mainhand);
        HiredJobInventory.maintainEquipmentSlots(offhand);

        helper.assertTrue(mainhand.isAlive()
                        && mainhand.getMainHandItem().isEmpty()
                        && mainInventory.getItem(HiredJobInventory.MAINHAND_SLOT).isEmpty(),
                "a job main-hand totem should be consumed from both the live hand and its authority");
        helper.assertTrue(offhand.isAlive()
                        && offhand.getOffhandItem().isEmpty()
                        && offInventory.getItem(HiredJobInventory.OFFHAND_SLOT).isEmpty(),
                "a job off-hand totem should be consumed from both the live hand and its authority");

        mainhand.discard();
        offhand.discard();
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

    private static void runDefensiveLoadoutScan(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        long interval = 20L;
        long offset = TickThrottle.spreadOffset(villager.getUUID(), interval);
        long delta = Math.floorMod(offset - Math.floorMod(now, interval), interval);
        ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData())
                .setGameTime(now + delta);
        VillagerDefensiveLoadoutService.onVillagerTickPost(villager);
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

    private static void setDoubleChest(GameTestHelper helper, BlockPos leftRelative, BlockPos rightRelative) {
        ServerLevel level = helper.getLevel();
        level.setBlock(
                helper.absolutePos(leftRelative),
                Blocks.CHEST.defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.NORTH)
                        .setValue(ChestBlock.TYPE, ChestType.LEFT),
                Block.UPDATE_CLIENTS);
        level.setBlock(
                helper.absolutePos(rightRelative),
                Blocks.CHEST.defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.NORTH)
                        .setValue(ChestBlock.TYPE, ChestType.RIGHT),
                Block.UPDATE_CLIENTS);
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

    private static ItemStack attributeTag(String tag) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        VillagerAttributeFilterData.setSelected(
                filter,
                new VillagerAttributeFilterData.Attribute(
                        VillagerAttributeFilterData.AttributeType.IN_TAG, tag),
                false);
        return filter;
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
