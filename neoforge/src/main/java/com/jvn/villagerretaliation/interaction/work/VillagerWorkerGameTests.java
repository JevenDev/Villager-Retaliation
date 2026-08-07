package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.work.logging.LoggingWorker;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.interaction.work.mining.HiredOreBlockTracker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHorizontalOptions;
import com.jvn.villagerretaliation.interaction.work.mining.MiningExcavationSupport;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.debug.HiredStressGridService;
import com.jvn.villagerretaliation.combat.VillagerCombatSkillBehavior;
import com.jvn.villagerretaliation.entity.VillagerFishingHook;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceService;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.interaction.HiredVillagerIndex;
import com.jvn.villagerretaliation.interaction.HiredJobSite;
import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.HiredJobInventorySlotType;
import com.jvn.villagerretaliation.inventory.PaymentBoxChunkLoadingService;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.mixin.AbstractArrowAccessor;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderPaymentEscrowService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerWorkerGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final String WORK_STATE_TAG = "VillagerRetaliationHiredWork";

    static {
        configureGameTestStructures();
    }

    private VillagerWorkerGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void workerRegistryCoversEveryRoleAndStatusRolesFailSafely(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 8, 0, 8, 1);
        ServerPlayer hirer = fakePlayer(level, "VrWorkerRegistry");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));

        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            HiredRoleWorker worker = HiredRoleWorkerRegistry.get(role);
            helper.assertTrue(worker != null, role + " should have a worker registered");

            HiredWorkContext context = context(helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(7, 4, 7), true);
            WorkResult result = worker.tick(level, villager, hirer, context);
            helper.assertTrue(result != null, role + " worker should return a result");
            helper.assertFalse(result.status().isBlank(), role + " worker should expose a safe status");
            HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
            helper.assertTrue(snapshot.taskState() != null, role + " worker should leave a readable task state");
        }

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void workerBrainStateTargetsRoundTripAndClearSafely(GameTestHelper helper) {
        CompoundTag state = new CompoundTag();
        HiredWorkerBrain.initialize(state);
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, 0L).taskState(),
                HiredWorkerTaskState.IDLE,
                "default worker state");

        BlockPos workTarget = new BlockPos(4, 2, 4);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.WORKING, workTarget);
        HiredWorkerBrain.Snapshot working = HiredWorkerBrain.snapshot(state, 10L);
        helper.assertValueEqual(working.taskState(), HiredWorkerTaskState.WORKING, "working state");
        helper.assertValueEqual(working.targetPos(), workTarget, "working target");

        BlockPos storageTarget = new BlockPos(6, 2, 6);
        HiredJobInventory dummyInventory = new HiredJobInventory(spawnVillager(helper, new BlockPos(1, 2, 1)));
        HiredWorkContext context = new HiredWorkContext(
                dummyInventory,
                state,
                BlockPos.ZERO,
                BlockPos.ZERO,
                BlockPos.ZERO,
                4,
                2,
                false,
                100,
                true,
                true);
        HiredWorkerBrain.setStorageTarget(context, storageTarget);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
        HiredWorkerBrain.Snapshot storing = HiredWorkerBrain.snapshot(state, 11L);
        helper.assertValueEqual(storing.storageTargetPos(), storageTarget, "storage target should be retained while moving to storage");

        HiredWorkerBrain.setState(context, HiredWorkerTaskState.IDLE);
        HiredWorkerBrain.Snapshot idle = HiredWorkerBrain.snapshot(state, 12L);
        helper.assertTrue(idle.targetPos() == null, "idle state should clear block target");
        helper.assertTrue(idle.storageTargetPos() == null, "idle state should clear storage target");
        helper.assertValueEqual(HiredWorkerTaskState.byId("target_unreachable"), HiredWorkerTaskState.FAILED_COOLDOWN, "legacy state alias");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void exactSupplyConsumptionDoesNotEatPartialIngredients(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.WHEAT)).isEmpty(), "single ingredient should fit");

        helper.assertFalse(
                inventory.consumeSupplyExactly(stack -> stack.is(Items.WHEAT), 2),
                "an incomplete ingredient set should not be consumed");
        helper.assertValueEqual(
                inventory.findSupply(stack -> stack.is(Items.WHEAT)).getCount(),
                1,
                "failed exact consumption should preserve the ingredient");

        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.WHEAT)).isEmpty(), "second ingredient should fit");
        helper.assertTrue(
                inventory.consumeSupplyExactly(stack -> stack.is(Items.WHEAT), 2),
                "a complete ingredient set should be consumed");
        helper.assertTrue(
                inventory.findSupply(stack -> stack.is(Items.WHEAT)).isEmpty(),
                "successful exact consumption should remove both ingredients");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void nitwitWorkerReportsBeforeItsFirstCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredWorkContext context = context(
                helper,
                villager,
                new CompoundTag(),
                new BlockPos(0, 2, 0),
                new BlockPos(2, 4, 2),
                true);
        NitwitWorker worker = new NitwitWorker();

        WorkResult first = worker.tick(level, villager, fakePlayer(level, "VrNitwitFirstReport"), context);
        helper.assertTrue(first.completed(), "a nitwit should produce its first report immediately");
        WorkResult second = worker.tick(level, villager, fakePlayer(level, "VrNitwitCooldown"), context);
        helper.assertFalse(second.completed(), "the next nitwit report should respect the cooldown");
        helper.assertValueEqual(second.status(), "interaction.work.nitwit.cooldown", "nitwit cooldown status");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void supplyCraftingRequiresTableForBreadAndCraftsWhenPresent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 8, 0, 8, 1);
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredWorkContext context = context(
                helper,
                villager,
                new CompoundTag(),
                new BlockPos(1, 2, 1),
                new BlockPos(7, 4, 7),
                true);
        HiredJobInventory inventory = context.inventory();
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.WHEAT, 3)).isEmpty(), "wheat should fit");

        helper.assertFalse(
                HiredSupplyCrafting.craftCarriedSupplyItemWithStations(level, context, Items.BREAD),
                "bread should require a crafting table");
        helper.assertValueEqual(countInventoryItem(inventory, Items.WHEAT), 3, "failed craft should preserve wheat");

        setBlock(helper, new BlockPos(3, 2, 3), Blocks.CRAFTING_TABLE.defaultBlockState());
        helper.assertTrue(
                HiredSupplyCrafting.craftCarriedSupplyItemWithStations(level, context, Items.BREAD),
                "bread should craft when a table is in the work area");
        helper.assertValueEqual(countInventoryItem(inventory, Items.WHEAT), 0, "bread should consume three wheat");
        helper.assertValueEqual(countInventoryItem(inventory, Items.BREAD), 1, "bread output");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void craftsmanExactRecipeProducesJobOutputTransactionally(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 8, 0, 8, 1);
        setBlock(helper, new BlockPos(3, 2, 3), Blocks.CRAFTING_TABLE.defaultBlockState());
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredWorkContext context = context(
                helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(7, 4, 7), true);
        HiredJobInventory inventory = context.inventory();
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.WHEAT, 3)).isEmpty(), "wheat should fit");
        CraftingRecipe breadRecipe = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .map(holder -> holder.value())
                .filter(recipe -> recipe.getResultItem(level.registryAccess()).is(Items.BREAD))
                .findFirst()
                .orElseThrow();

        helper.assertTrue(
                HiredSupplyCrafting.craftCarriedRecipeToOutputsWithStations(level, context, breadRecipe),
                "the selected exact recipe should craft");
        helper.assertValueEqual(countInventoryItem(inventory, Items.WHEAT), 0, "exact craft input");
        int outputBread = inventory.outputSlots().stream()
                .map(inventory::getItem)
                .filter(stack -> stack.is(Items.BREAD))
                .mapToInt(ItemStack::getCount)
                .sum();
        helper.assertValueEqual(outputBread, 1, "crafted item should be classified as output");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void craftsmanModesCycleAndPersist(GameTestHelper helper) {
        CompoundTag state = new CompoundTag();
        helper.assertValueEqual(CraftsmanWorker.mode(state), CraftsmanWorker.Mode.PREFER_FIRST, "default mode");
        helper.assertValueEqual(CraftsmanWorker.cycleMode(state), CraftsmanWorker.Mode.ROUND_ROBIN, "round robin mode");
        helper.assertValueEqual(CraftsmanWorker.mode(state), CraftsmanWorker.Mode.ROUND_ROBIN, "stored round robin mode");
        helper.assertValueEqual(
                CraftsmanWorker.cycleMode(state),
                CraftsmanWorker.Mode.FORCED_ROUND_ROBIN,
                "forced round robin mode");
        helper.assertValueEqual(CraftsmanWorker.cycleMode(state), CraftsmanWorker.Mode.PREFER_FIRST, "mode wraps");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void supplyCraftingBuildsCakePrerequisitesAndReturnsBuckets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 8, 0, 8, 1);
        setBlock(helper, new BlockPos(3, 2, 3), Blocks.CRAFTING_TABLE.defaultBlockState());
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredWorkContext context = context(
                helper,
                villager,
                new CompoundTag(),
                new BlockPos(1, 2, 1),
                new BlockPos(7, 4, 7),
                true);
        HiredJobInventory inventory = context.inventory();
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.MILK_BUCKET, 3)).isEmpty(), "milk should fit");
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.SUGAR_CANE, 2)).isEmpty(), "sugar cane should fit");
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.EGG)).isEmpty(), "egg should fit");
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.WHEAT, 3)).isEmpty(), "wheat should fit");

        helper.assertTrue(
                HiredSupplyCrafting.craftCarriedSupplyItemWithStations(level, context, Items.CAKE),
                "cake should recursively craft sugar from sugar cane");
        helper.assertValueEqual(countInventoryItem(inventory, Items.CAKE), 1, "cake output");
        helper.assertValueEqual(countInventoryItem(inventory, Items.SUGAR_CANE), 0, "sugar cane prerequisite input");
        helper.assertValueEqual(countInventoryItem(inventory, Items.BUCKET), 3, "milk buckets should be returned");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void cookFilterSelectsOnlyConfiguredCraftableFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 8, 0, 8, 1);
        setBlock(helper, new BlockPos(3, 2, 3), Blocks.CRAFTING_TABLE.defaultBlockState());
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        ServerPlayer hirer = fakePlayer(level, "VrCookFilterStorage");
        BlockPos storageRelative = new BlockPos(4, 2, 4);
        BlockPos storage = helper.absolutePos(storageRelative);
        setBlock(helper, storageRelative, Blocks.CHEST.defaultBlockState());
        container(level, storage).setItem(0, new ItemStack(Items.WHEAT, 3));
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), storage)),
                AssignedStorageService.SUPPLY_PURPOSE);
        HiredWorkContext context = context(
                helper,
                villager,
                new CompoundTag(),
                new BlockPos(1, 2, 1),
                new BlockPos(7, 4, 7),
                true);
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.BREAD));
        VillagerItemFilterService.replaceFilter(villager, filter);

        helper.assertValueEqual(
                AssignedStorageService.countItems(villager, stack -> stack.is(Items.WHEAT)),
                0,
                "normal withdrawals should still honor the bread allowlist");
        helper.assertValueEqual(
                AssignedStorageService.countItemsIgnoringFilter(villager, stack -> stack.is(Items.WHEAT)),
                3,
                "cook recipe planning should see wheat behind its output filter");

        CookingWorker.CraftingAssessment bread = CookingWorker.assessCraftingTargets(
                level,
                villager,
                context,
                VillagerItemFilterService.assignedFilter(villager));
        helper.assertTrue(bread.selection() != null, "configured bread should produce a crafting plan");
        helper.assertTrue(bread.selection().result().is(Items.BREAD), "crafting plan should target bread");

        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.CAKE));
        VillagerItemFilterService.replaceFilter(villager, filter);
        CookingWorker.CraftingAssessment cake = CookingWorker.assessCraftingTargets(
                level,
                villager,
                context,
                VillagerItemFilterService.assignedFilter(villager));
        helper.assertTrue(cake.selection() == null, "unavailable cake materials should not produce a plan");
        helper.assertTrue(cake.hasRecipe(), "the configured cake recipe should still be recognized");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    @SuppressWarnings("unchecked")
    public static void cookFilterKeepsSmokerRecipesAndFiltersTheirOutputs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RecipeType<AbstractCookingRecipe> smoking =
                (RecipeType<AbstractCookingRecipe>) (RecipeType<?>) RecipeType.SMOKING;
        ItemStack cookedBeefFilter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(cookedBeefFilter, 0, new ItemStack(Items.COOKED_BEEF));
        ItemStack breadFilter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(breadFilter, 0, new ItemStack(Items.BREAD));

        helper.assertTrue(
                CookingWorker.isCookableFoodForFilter(
                        level,
                        new ItemStack(Items.BEEF),
                        smoking,
                        cookedBeefFilter),
                "a cooked-beef filter should keep the smoker beef recipe available");
        helper.assertFalse(
                CookingWorker.isCookableFoodForFilter(
                        level,
                        new ItemStack(Items.BEEF),
                        smoking,
                        breadFilter),
                "a bread filter should reject cooked beef from the smoker");
        helper.assertTrue(
                CookingWorker.isCookableFoodForFilter(
                        level,
                        new ItemStack(Items.BEEF),
                        smoking,
                        ItemStack.EMPTY),
                "an unfiltered cook should preserve legacy smoker behavior");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    @SuppressWarnings("unchecked")
    public static void cookRecipeFilterUsesExactSmokingStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        BlockPos furnaceRel = new BlockPos(3, 2, 2);
        BlockPos smokerRel = new BlockPos(2, 2, 3);
        setBlock(helper, furnaceRel, Blocks.FURNACE.defaultBlockState());
        setBlock(helper, smokerRel, Blocks.SMOKER.defaultBlockState());
        Container furnace = container(level, helper.absolutePos(furnaceRel));
        Container smoker = container(level, helper.absolutePos(smokerRel));

        RecipeType<AbstractCookingRecipe> smoking =
                (RecipeType<AbstractCookingRecipe>) (RecipeType<?>) RecipeType.SMOKING;
        RecipeHolder<AbstractCookingRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(smoking, new SingleRecipeInput(new ItemStack(Items.BEEF)), level)
                .orElseThrow();
        ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        helper.assertTrue(
                VillagerRecipeFilterData.setRecipe(filter, level, recipe.id()),
                "smoking recipe should configure");

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        VillagerItemFilterService.replaceFilter(villager, filter);
        HiredWorkContext context = context(
                helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        helper.assertTrue(context.inventory().insertSupply(new ItemStack(Items.BEEF, 4)).isEmpty(), "beef should fit");
        helper.assertTrue(context.inventory().insertSupply(new ItemStack(Items.COAL, 2)).isEmpty(), "fuel should fit");

        WorkResult result = new CookingWorker().tick(
                level, villager, fakePlayer(level, "VrExactSmokingCook"), context);

        helper.assertValueEqual(result.status(), "interaction.work.cooking.loaded_input", "smoker load status");
        helper.assertValueEqual(smoker.getItem(0).getCount(), 4, "exact smoking recipe should use smoker");
        helper.assertTrue(furnace.getItem(0).isEmpty(), "furnace must remain unused");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    @SuppressWarnings("unchecked")
    public static void smelterRecipeFilterUsesExactBlastingStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        BlockPos furnaceRel = new BlockPos(3, 2, 2);
        BlockPos blastRel = new BlockPos(2, 2, 3);
        setBlock(helper, furnaceRel, Blocks.FURNACE.defaultBlockState());
        setBlock(helper, blastRel, Blocks.BLAST_FURNACE.defaultBlockState());
        Container furnace = container(level, helper.absolutePos(furnaceRel));
        Container blastFurnace = container(level, helper.absolutePos(blastRel));

        RecipeType<AbstractCookingRecipe> blasting =
                (RecipeType<AbstractCookingRecipe>) (RecipeType<?>) RecipeType.BLASTING;
        RecipeHolder<AbstractCookingRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(blasting, new SingleRecipeInput(new ItemStack(Items.RAW_IRON)), level)
                .orElseThrow();
        ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        helper.assertTrue(
                VillagerRecipeFilterData.setRecipe(filter, level, recipe.id()),
                "blasting recipe should configure");

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        VillagerItemFilterService.replaceFilter(villager, filter);
        HiredWorkContext context = context(
                helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        helper.assertTrue(
                context.inventory().insertSupply(new ItemStack(Items.RAW_IRON, 4)).isEmpty(),
                "raw iron should fit");
        helper.assertTrue(context.inventory().insertSupply(new ItemStack(Items.COAL, 2)).isEmpty(), "fuel should fit");

        WorkResult result = new SmeltingWorker().tick(
                level, villager, fakePlayer(level, "VrExactBlastingSmelter"), context);

        helper.assertValueEqual(result.status(), "interaction.work.smelting.loaded_input", "blast furnace load status");
        helper.assertValueEqual(blastFurnace.getItem(0).getCount(), 4, "exact blasting recipe should use blast furnace");
        helper.assertTrue(furnace.getItem(0).isEmpty(), "ordinary furnace must remain unused");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void cookCollectsReadyOutputBeforeCraftingMoreFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        BlockPos furnaceRel = new BlockPos(3, 2, 2);
        setBlock(helper, furnaceRel, Blocks.FURNACE.defaultBlockState());
        setBlock(helper, new BlockPos(2, 2, 3), Blocks.CRAFTING_TABLE.defaultBlockState());
        Container furnace = container(level, helper.absolutePos(furnaceRel));
        furnace.setItem(2, new ItemStack(Items.COOKED_BEEF, 2));

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.BREAD));
        VillagerItemFilterData.setEntry(filter, 1, new ItemStack(Items.COOKED_BEEF));
        VillagerItemFilterService.replaceFilter(villager, filter);
        HiredWorkContext context = context(
                helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        helper.assertTrue(context.inventory().insertSupply(new ItemStack(Items.WHEAT, 3)).isEmpty(), "wheat should fit");

        WorkResult result = new CookingWorker().tick(
                level, villager, fakePlayer(level, "VrCookOutputPriority"), context);

        helper.assertTrue(result.completed(), "ready furnace output should be collected first");
        helper.assertTrue(furnace.getItem(2).isEmpty(), "ready cooked food should leave the furnace");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.COOKED_BEEF), 2, "collected cooked beef");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.WHEAT), 3, "wheat should remain unused");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.BREAD), 0, "bread should not be crafted");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void cookSkipsContaminatedFurnaceForCleanStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 7, 0, 7, 1);
        BlockPos wrongRel = new BlockPos(3, 2, 2);
        BlockPos cleanRel = new BlockPos(2, 2, 3);
        setBlock(helper, wrongRel, Blocks.FURNACE.defaultBlockState());
        setBlock(helper, cleanRel, Blocks.FURNACE.defaultBlockState());
        Container wrong = container(level, helper.absolutePos(wrongRel));
        Container clean = container(level, helper.absolutePos(cleanRel));
        wrong.setItem(2, new ItemStack(Items.STONE));

        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 3));
        HiredWorkContext context = context(
                helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(6, 4, 6), true);
        helper.assertTrue(context.inventory().insertSupply(new ItemStack(Items.BEEF, 4)).isEmpty(), "beef should fit");
        helper.assertTrue(context.inventory().insertSupply(new ItemStack(Items.COAL, 2)).isEmpty(), "fuel should fit");

        WorkResult result = new CookingWorker().tick(
                level, villager, fakePlayer(level, "VrCleanCookStation"), context);

        helper.assertValueEqual(result.status(), "interaction.work.cooking.loaded_input", "clean station load status");
        helper.assertValueEqual(clean.getItem(0).getCount(), 4, "clean furnace should receive food");
        helper.assertTrue(wrong.getItem(2).is(Items.STONE), "contaminating output must remain untouched");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void smelterSkipsContaminatedFurnaceForCleanStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 7, 0, 7, 1);
        BlockPos wrongRel = new BlockPos(3, 2, 2);
        BlockPos cleanRel = new BlockPos(2, 2, 3);
        setBlock(helper, wrongRel, Blocks.FURNACE.defaultBlockState());
        setBlock(helper, cleanRel, Blocks.FURNACE.defaultBlockState());
        Container wrong = container(level, helper.absolutePos(wrongRel));
        Container clean = container(level, helper.absolutePos(cleanRel));
        wrong.setItem(2, new ItemStack(Items.STONE));

        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 3));
        HiredWorkContext context = context(
                helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(6, 4, 6), true);
        helper.assertTrue(
                context.inventory().insertSupply(new ItemStack(Items.RAW_IRON, 4)).isEmpty(),
                "raw iron should fit");
        helper.assertTrue(context.inventory().insertSupply(new ItemStack(Items.COAL, 2)).isEmpty(), "fuel should fit");

        WorkResult result = new SmeltingWorker().tick(
                level, villager, fakePlayer(level, "VrCleanSmeltingStation"), context);

        helper.assertValueEqual(result.status(), "interaction.work.smelting.loaded_input", "clean station load status");
        helper.assertValueEqual(clean.getItem(0).getCount(), 4, "clean furnace should receive ore");
        helper.assertTrue(wrong.getItem(2).is(Items.STONE), "contaminating output must remain untouched");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void cookAcceptsKelpWhenCookingResultIsFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 5, 0, 5, 1);
        BlockPos furnaceRel = new BlockPos(3, 2, 2);
        setBlock(helper, furnaceRel, Blocks.FURNACE.defaultBlockState());
        Container furnace = container(level, helper.absolutePos(furnaceRel));

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredWorkContext context = context(
                helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(4, 4, 4), true);
        helper.assertTrue(context.inventory().insertSupply(new ItemStack(Items.KELP, 4)).isEmpty(), "kelp should fit");
        helper.assertTrue(context.inventory().insertSupply(new ItemStack(Items.COAL, 2)).isEmpty(), "fuel should fit");

        WorkResult result = new CookingWorker().tick(
                level, villager, fakePlayer(level, "VrKelpCook"), context);

        helper.assertValueEqual(result.status(), "interaction.work.cooking.loaded_input", "kelp load status");
        helper.assertValueEqual(furnace.getItem(0).getCount(), 4, "kelp should enter the furnace");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void processingFuelRemaindersDoNotGrantPractice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 5, 0, 5, 1);
        BlockPos furnaceRel = new BlockPos(3, 2, 2);
        setBlock(helper, furnaceRel, Blocks.FURNACE.defaultBlockState());
        Container furnace = container(level, helper.absolutePos(furnaceRel));
        furnace.setItem(1, new ItemStack(Items.BUCKET));

        Villager cook = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredWorkContext cookContext = context(
                helper, cook, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(4, 4, 4), true);
        WorkResult cookResult = new CookingWorker().tick(
                level, cook, fakePlayer(level, "VrCookFuelRemainder"), cookContext);
        helper.assertTrue(cookResult.practice().isEmpty(), "collecting a bucket must not train Cooking");
        cook.discard();

        furnace.setItem(1, new ItemStack(Items.BUCKET));
        Villager smelter = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredWorkContext smelterContext = context(
                helper, smelter, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(4, 4, 4), true);
        WorkResult smelterResult = new SmeltingWorker().tick(
                level, smelter, fakePlayer(level, "VrSmelterFuelRemainder"), smelterContext);
        helper.assertTrue(smelterResult.practice().isEmpty(), "collecting a bucket must not train Smithing");
        smelter.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void recipeFilterHonorsNarrowedCraftingIngredient(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        RecipeHolder<CraftingRecipe> holder = level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(candidate -> candidate.value()
                        .getResultItem(level.registryAccess()).is(Items.FIRE_CHARGE))
                .filter(candidate -> candidate.value().getIngredients().stream()
                        .anyMatch(ingredient -> ingredient.getItems().length > 1))
                .findFirst()
                .orElseThrow();
        List<Ingredient> ingredients = holder.value().getIngredients();
        int narrowedSlot = -1;
        ItemStack narrowedChoice = ItemStack.EMPTY;
        for (int slot = 0; slot < ingredients.size(); slot++) {
            ItemStack[] choices = ingredients.get(slot).getItems();
            if (choices.length > 1) {
                narrowedSlot = slot;
                narrowedChoice = choices[choices.length - 1].copyWithCount(1);
                break;
            }
        }
        helper.assertTrue(narrowedSlot >= 0 && !narrowedChoice.isEmpty(), "alternative ingredient should exist");

        ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        helper.assertTrue(VillagerRecipeFilterData.setRecipe(filter, level, holder.id()), "recipe should configure");
        helper.assertTrue(
                VillagerRecipeFilterData.setIngredient(
                        filter, level, narrowedSlot, BuiltInRegistries.ITEM.getKey(narrowedChoice.getItem())),
                "ingredient alternative should narrow");

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredWorkContext context = context(
                helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        Map<net.minecraft.world.item.Item, Integer> supplied = new LinkedHashMap<>();
        for (int slot = 0; slot < ingredients.size(); slot++) {
            Ingredient ingredient = ingredients.get(slot);
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack choice = slot == narrowedSlot ? narrowedChoice : ingredient.getItems()[0];
            supplied.merge(choice.getItem(), 1, Integer::sum);
        }
        supplied.forEach((item, count) ->
                helper.assertTrue(
                        context.inventory().insertSupply(new ItemStack(item, count)).isEmpty(),
                        "recipe ingredient should fit"));

        Map<Integer, net.minecraft.world.item.Item> narrowed =
                HiredProcessingRecipeFilter.narrowedCraftingIngredients(level, filter, holder);
        helper.assertTrue(
                narrowed.get(narrowedSlot) == narrowedChoice.getItem(),
                "plan should retain the narrowed ingredient");
        helper.assertTrue(
                HiredSupplyCrafting.craftCarriedRecipeWithStations(
                        level, context, holder.value(), narrowed),
                "exact narrowed recipe should craft");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.FIRE_CHARGE),
                3,
                "configured recipe output should be produced");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void smelterMovesFromBusyCachedFurnaceToAnotherFurnace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        BlockPos busyRel = new BlockPos(3, 2, 2);
        BlockPos availableRel = new BlockPos(2, 2, 3);
        setBlock(helper, busyRel, Blocks.FURNACE.defaultBlockState());
        setBlock(helper, availableRel, Blocks.FURNACE.defaultBlockState());
        Container busy = container(level, helper.absolutePos(busyRel));
        Container available = container(level, helper.absolutePos(availableRel));
        busy.setItem(0, new ItemStack(Items.RAW_IRON, 8));
        busy.setItem(1, new ItemStack(Items.COAL, 2));

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = new CompoundTag();
        state.putLong("SmeltingCachedStationPos", helper.absolutePos(busyRel).asLong());
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        helper.assertTrue(
                context.inventory().insertSupply(new ItemStack(Items.RAW_IRON, 4)).isEmpty(),
                "smelter ore supply should fit");
        helper.assertTrue(
                context.inventory().insertSupply(new ItemStack(Items.COAL, 2)).isEmpty(),
                "smelter fuel supply should fit");

        WorkResult result = new SmeltingWorker().tick(
                level, villager, fakePlayer(level, "VrMultiSmelter"), context);

        helper.assertValueEqual(result.status(), "interaction.work.smelting.loaded_input", "second furnace work status");
        helper.assertValueEqual(available.getItem(0).getCount(), 4, "smelter should load the available furnace");
        helper.assertValueEqual(busy.getItem(0).getCount(), 8, "busy furnace input should remain in place");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void cookMovesFromBusyCachedFurnaceToAnotherFurnace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        BlockPos busyRel = new BlockPos(3, 2, 2);
        BlockPos availableRel = new BlockPos(2, 2, 3);
        setBlock(helper, busyRel, Blocks.FURNACE.defaultBlockState());
        setBlock(helper, availableRel, Blocks.FURNACE.defaultBlockState());
        Container busy = container(level, helper.absolutePos(busyRel));
        Container available = container(level, helper.absolutePos(availableRel));
        busy.setItem(0, new ItemStack(Items.BEEF, 8));
        busy.setItem(1, new ItemStack(Items.COAL, 2));

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = new CompoundTag();
        state.putLong("CookingCachedStationPos", helper.absolutePos(busyRel).asLong());
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        helper.assertTrue(
                context.inventory().insertSupply(new ItemStack(Items.BEEF, 4)).isEmpty(),
                "cook food supply should fit");
        helper.assertTrue(
                context.inventory().insertSupply(new ItemStack(Items.COAL, 2)).isEmpty(),
                "cook fuel supply should fit");

        WorkResult result = new CookingWorker().tick(
                level, villager, fakePlayer(level, "VrMultiCook"), context);

        helper.assertValueEqual(result.status(), "interaction.work.cooking.loaded_input", "second furnace work status");
        helper.assertValueEqual(available.getItem(0).getCount(), 4, "cook should load the available furnace");
        helper.assertValueEqual(busy.getItem(0).getCount(), 8, "busy furnace input should remain in place");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void brewerCollectsIngredientRemainderBeforeCompletingOrder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 5, 0, 5, 1);
        BlockPos standRel = new BlockPos(3, 2, 2);
        setBlock(helper, standRel, Blocks.BREWING_STAND.defaultBlockState());
        Container stand = container(level, helper.absolutePos(standRel));
        HiredBrewingRecipeCatalog.BrewingRoute route = brewingRoute(
                level, PotionContents.createItemStack(Items.LINGERING_POTION, Potions.SWIFTNESS));
        stand.setItem(0, route.output().copy());
        stand.setItem(3, new ItemStack(Items.GLASS_BOTTLE));

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = new CompoundTag();
        BrewingWorker.setOrder(state, route.itemId(), route.potionId(), 6, false);
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(4, 4, 4), true);

        WorkResult result = new BrewingWorker().tick(
                level, villager, fakePlayer(level, "VrBrewerRemainder"), context);

        helper.assertValueEqual(
                result.status(),
                "interaction.work.brewing.collected_ingredient_remainder",
                "brewer remainder status");
        helper.assertTrue(stand.getItem(3).isEmpty(), "brewer should clear the ingredient remainder");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.GLASS_BOTTLE),
                1,
                "brewer should recover the glass bottle as supply");
        helper.assertTrue(
                ItemStack.isSameItemSameComponents(stand.getItem(0), route.output()),
                "remainder cleanup should leave the finished potion in place");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void brewerSkipsIncompatibleCachedStand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        BlockPos blockedRel = new BlockPos(3, 2, 2);
        BlockPos usableRel = new BlockPos(2, 2, 3);
        setBlock(helper, blockedRel, Blocks.BREWING_STAND.defaultBlockState());
        setBlock(helper, usableRel, Blocks.BREWING_STAND.defaultBlockState());
        Container blocked = container(level, helper.absolutePos(blockedRel));
        Container usable = container(level, helper.absolutePos(usableRel));
        HiredBrewingRecipeCatalog.BrewingRoute route = brewingRoute(
                level, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
        blocked.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.POISON));
        usable.setItem(0, route.output().copy());

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = new CompoundTag();
        state.putLong("BrewingCachedStandPos", helper.absolutePos(blockedRel).asLong());
        BrewingWorker.setOrder(state, route.itemId(), route.potionId(), 1, false);
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);

        WorkResult result = new BrewingWorker().tick(
                level, villager, fakePlayer(level, "VrBrewerAlternateStand"), context);

        helper.assertValueEqual(
                result.status(), "interaction.work.brewing.collected_output", "alternate stand work status");
        helper.assertTrue(usable.getItem(0).isEmpty(), "brewer should collect from the compatible stand");
        helper.assertFalse(blocked.getItem(0).isEmpty(), "brewer should leave the incompatible stand untouched");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), route.output().getItem()),
                1,
                "brewer should store the requested potion");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void brewerTrimsFullWaterBottleBatchToOrderSize(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 5, 0, 5, 1);
        BlockPos standRel = new BlockPos(3, 2, 2);
        setBlock(helper, standRel, Blocks.BREWING_STAND.defaultBlockState());
        Container stand = container(level, helper.absolutePos(standRel));
        ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        for (int slot = 0; slot < 3; slot++) {
            stand.setItem(slot, waterBottle.copy());
        }
        HiredBrewingRecipeCatalog.BrewingRoute route = brewingRoute(
                level, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = new CompoundTag();
        BrewingWorker.setOrder(state, route.itemId(), route.potionId(), 1, false);
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(4, 4, 4), true);
        for (var ingredient : route.ingredients()) {
            helper.assertTrue(
                    context.inventory().insertSupply(new ItemStack(ingredient)).isEmpty(),
                    "brewing ingredient should fit");
        }
        helper.assertTrue(
                context.inventory().insertSupply(new ItemStack(Items.BLAZE_POWDER)).isEmpty(),
                "brewing fuel should fit");

        WorkResult result = new BrewingWorker().tick(
                level, villager, fakePlayer(level, "VrBrewerExactBatch"), context);

        helper.assertValueEqual(
                result.status(), "interaction.work.brewing.cleared_extra_bottles", "trimmed batch status");
        int standWaterBottles = 0;
        for (int slot = 0; slot < 3; slot++) {
            if (HiredBrewingRecipeCatalog.isWaterPotion(stand.getItem(slot))) {
                standWaterBottles++;
            }
        }
        helper.assertValueEqual(standWaterBottles, 1, "one-bottle order should leave one bottle in the stand");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.POTION),
                2,
                "extra water bottles should return to job supplies");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void brewerCannotFillWaterBottlesThroughWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        BlockPos standRel = new BlockPos(2, 2, 3);
        setBlock(helper, standRel, Blocks.BREWING_STAND.defaultBlockState());
        setBlock(helper, new BlockPos(4, 2, 2), Blocks.WATER.defaultBlockState());
        setBlock(helper, new BlockPos(3, 2, 2), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 3, 2), Blocks.STONE.defaultBlockState());
        HiredBrewingRecipeCatalog.BrewingRoute route = brewingRoute(
                level, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = new CompoundTag();
        BrewingWorker.setOrder(state, route.itemId(), route.potionId(), 1, false);
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        for (var ingredient : route.ingredients()) {
            helper.assertTrue(
                    context.inventory().insertSupply(new ItemStack(ingredient)).isEmpty(),
                    "brewing ingredient should fit");
        }
        helper.assertTrue(
                context.inventory().insertSupply(new ItemStack(Items.BLAZE_POWDER)).isEmpty(),
                "brewing fuel should fit");
        helper.assertTrue(
                context.inventory().insertSupply(new ItemStack(Items.GLASS_BOTTLE)).isEmpty(),
                "glass bottle should fit");

        new BrewingWorker().tick(level, villager, fakePlayer(level, "VrBrewerWaterWall"), context);

        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.GLASS_BOTTLE),
                1,
                "wall should prevent the brewer from consuming the glass bottle");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.POTION),
                0,
                "wall should prevent the brewer from filling a water bottle");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void brewerFillsBottleFromVisibleWaterSource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 5, 0, 5, 1);
        BlockPos standRel = new BlockPos(2, 2, 3);
        setBlock(helper, standRel, Blocks.BREWING_STAND.defaultBlockState());
        setBlock(helper, new BlockPos(3, 2, 2), Blocks.WATER.defaultBlockState());
        HiredBrewingRecipeCatalog.BrewingRoute route = brewingRoute(
                level, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = new CompoundTag();
        BrewingWorker.setOrder(state, route.itemId(), route.potionId(), 1, false);
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(4, 4, 4), true);
        for (var ingredient : route.ingredients()) {
            helper.assertTrue(
                    context.inventory().insertSupply(new ItemStack(ingredient)).isEmpty(),
                    "brewing ingredient should fit");
        }
        helper.assertTrue(
                context.inventory().insertSupply(new ItemStack(Items.BLAZE_POWDER)).isEmpty(),
                "brewing fuel should fit");
        helper.assertTrue(
                context.inventory().insertSupply(new ItemStack(Items.GLASS_BOTTLE)).isEmpty(),
                "glass bottle should fit");

        new BrewingWorker().tick(level, villager, fakePlayer(level, "VrBrewerVisibleWater"), context);
        Container stand = container(level, helper.absolutePos(standRel));
        int standWaterBottles = 0;
        for (int slot = 0; slot < 3; slot++) {
            if (HiredBrewingRecipeCatalog.isWaterPotion(stand.getItem(slot))) {
                standWaterBottles++;
            }
        }

        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.GLASS_BOTTLE),
                0,
                "visible water should consume the glass bottle");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.POTION) + standWaterBottles,
                1,
                "visible water should produce or load a water bottle");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void lowSkillCookCollectsHalfOfBaselineOutputPerTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 5, 0, 5, 1);
        BlockPos stationRel = new BlockPos(3, 2, 2);
        BlockPos station = helper.absolutePos(stationRel);
        setBlock(helper, stationRel, Blocks.FURNACE.defaultBlockState());
        Container furnace = container(level, station);
        furnace.setItem(2, new ItemStack(Items.COOKED_BEEF, 20));

        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = new CompoundTag();
        state.putLong("CookingCachedStationPos", station.asLong());
        HiredWorkContext context = context(
                helper,
                villager,
                state,
                new BlockPos(1, 2, 1),
                new BlockPos(4, 4, 4),
                true,
                50);

        WorkResult result = new CookingWorker().tick(
                level, villager, fakePlayer(level, "VrLowSkillCookTransfer"), context);

        helper.assertTrue(result.completed(), "collecting a facility output should complete one work action");
        helper.assertValueEqual(furnace.getItem(2).getCount(), 12, "low-skill cook should leave output beyond the trip limit");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.COOKED_BEEF),
                8,
                "50% transfer capacity should collect 8 items from the 16-item baseline");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void courierRoleIsAvailableToEveryProfessionIncludingUnemployed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager unemployed = spawnVillager(helper, new BlockPos(2, 2, 2));
        unemployed.setVillagerData(unemployed.getVillagerData().setProfession(VillagerProfession.NONE));
        Villager farmer = spawnVillager(helper, new BlockPos(4, 2, 2));
        farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
        Villager baby = spawnVillager(helper, new BlockPos(3, 2, 4));
        baby.setBaby(true);

        helper.assertTrue(
                HiredVillagerRoles.availableContractRoles(level, unemployed).contains(HiredVillagerRole.COURIER),
                "unemployed villagers should always offer the courier role");
        helper.assertTrue(
                HiredVillagerRoles.availableContractRoles(level, farmer).contains(HiredVillagerRole.COURIER),
                "employed villagers should always offer the courier role");
        helper.assertTrue(
                HiredVillagerRoles.isSkillUnlocked(unemployed, HiredVillagerRole.COURIER, 0),
                "courier should not require a skill threshold");
        helper.assertTrue(
                HiredVillagerRoles.availableContractRoles(level, baby).isEmpty(),
                "baby villagers should have no available hired roles");
        ServerPlayer hirer = fakePlayer(level, "VrBabyHireGuard");
        HiredVillagerContractService.startHireContract(level, baby, hirer, 1, 8, HiredVillagerRole.COURIER);
        helper.assertFalse(
                HiredVillagerContractService.hasContract(baby),
                "baby villagers should reject direct contract creation");

        unemployed.discard();
        farmer.discard();
        baby.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void animalHandlerShearsSheepWithAssignedTool(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalShearing");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        Sheep sheep = spawnAnimal(helper, EntityType.SHEEP, new BlockPos(4, 2, 3));

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        ItemStack shears = new ItemStack(Items.SHEARS);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, shears);
        AnimalBreedingWorker worker = new AnimalBreedingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 20, sheep::isSheared);

        helper.assertTrue(sheep.isSheared(), "animal handler should shear an adult sheep with assigned shears");
        helper.assertValueEqual(shears.getDamageValue(), 1, "shearing should damage the assigned tool once");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void animalHandlerDoesNotShearWhenOptionIsDisabled(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalNoShearing");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        Sheep sheep = spawnAnimal(helper, EntityType.SHEEP, new BlockPos(4, 2, 3));

        CompoundTag state = new CompoundTag();
        HiredAnimalHandlingOptions.toggle(state, HiredAnimalHandlingOptions.SHEAR_SHEEP);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.SHEARS));
        AnimalBreedingWorker worker = new AnimalBreedingWorker();

        for (int tick = 0; tick < 20; tick++) {
            worker.maintain(level, villager, context);
            worker.tick(level, villager, hirer, context);
            level.tickNonPassenger(villager);
        }

        helper.assertFalse(sheep.isSheared(), "animal handler should leave sheep alone when shearing is disabled");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void animalHandlerPeriodicallyDepositsOutputsWhileShearing(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalShearingDeposit");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        Sheep sheep = spawnAnimal(helper, EntityType.SHEEP, new BlockPos(4, 2, 3));
        BlockPos chestRelative = new BlockPos(3, 2, 2);
        BlockPos chest = helper.absolutePos(chestRelative);
        setBlock(helper, chestRelative, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        CompoundTag state = new CompoundTag();
        state.putLong(AnimalBreedingWorker.NEXT_SHEARING_DEPOSIT_GAME_TIME_TAG, level.getGameTime());
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.SHEARS));
        context.inventory().insertOutput(new ItemStack(Items.WHITE_WOOL, 4));

        AnimalBreedingWorker worker = new AnimalBreedingWorker();
        worker.tick(level, villager, hirer, context);

        helper.assertValueEqual(countItem(container(level, chest), Items.WHITE_WOOL), 4, "periodic shearing deposit");
        helper.assertFalse(context.hasOutputToDeposit(), "periodic shearing deposit should empty job outputs");
        helper.assertFalse(sheep.isSheared(), "the due deposit should run before the next shearing action");
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void animalHandlerCollectsBucketFromAssignedStorageBeforeMilking(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalMilkingStorage");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(4, 2, 3));
        BlockPos chestRelative = new BlockPos(2, 2, 3);
        BlockPos chest = helper.absolutePos(chestRelative);
        setBlock(helper, chestRelative, Blocks.CHEST.defaultBlockState());
        container(level, chest).setItem(0, new ItemStack(Items.BUCKET));
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.SUPPLY_PURPOSE);

        HiredWorkContext context = context(
                helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        AnimalBreedingWorker worker = new AnimalBreedingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 80, () ->
                countInventoryItem(context.inventory(), Items.MILK_BUCKET) == 1);

        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.MILK_BUCKET),
                1,
                "animal handler should collect a bucket from storage and milk the cow");
        helper.assertValueEqual(countItem(container(level, chest), Items.BUCKET), 0, "stored bucket should be consumed");
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void animalHandlerDepositsDueWoolWithoutReadySheep(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalIdleShearingDeposit");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        Sheep sheep = spawnAnimal(helper, EntityType.SHEEP, new BlockPos(4, 2, 3));
        sheep.setSheared(true);
        BlockPos chestRelative = new BlockPos(3, 2, 2);
        BlockPos chest = helper.absolutePos(chestRelative);
        setBlock(helper, chestRelative, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        CompoundTag state = new CompoundTag();
        state.putLong(AnimalBreedingWorker.NEXT_SHEARING_DEPOSIT_GAME_TIME_TAG, level.getGameTime());
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        context.inventory().insertOutput(new ItemStack(Items.WHITE_WOOL, 4));

        new AnimalBreedingWorker().tick(level, villager, hirer, context);

        helper.assertValueEqual(countItem(container(level, chest), Items.WHITE_WOOL), 4, "due wool deposit");
        helper.assertFalse(context.hasOutputToDeposit(), "due wool should leave the job inventory");
        helper.assertTrue(sheep.isSheared(), "deposit should not require a ready sheep");
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void animalHandlerDoesNotFeedWidelySeparatedBreedingPair(GameTestHelper helper) {
        buildFloor(helper, 0, 12, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalSeparatedPair");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        Cow first = spawnAnimal(helper, EntityType.COW, new BlockPos(4, 2, 3));
        Cow second = spawnAnimal(helper, EntityType.COW, new BlockPos(10, 2, 3));
        first.setNoAi(true);
        second.setNoAi(true);

        HiredWorkContext context = context(
                helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(11, 4, 5), true);
        context.inventory().insertSupply(new ItemStack(Items.WHEAT, 2));
        AnimalBreedingWorker worker = new AnimalBreedingWorker();
        for (int tick = 0; tick < 10; tick++) {
            worker.tick(level, villager, hirer, context);
            level.tickNonPassenger(villager);
        }

        helper.assertFalse(first.isInLove() || second.isInLove(), "separated animals must not be fed remotely");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.WHEAT),
                2,
                "invalid remote pair should not consume breeding food");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void animalHandlerBlacklistsUnreachableAnimal(GameTestHelper helper) {
        buildFloor(helper, 0, 12, 0, 12, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalUnreachable");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 6));
        Cow cow = spawnAnimal(helper, EntityType.COW, new BlockPos(9, 2, 6));
        cow.setNoAi(true);
        for (int z = 1; z <= 11; z++) {
            for (int y = 2; y <= 4; y++) {
                setBlock(helper, new BlockPos(6, y, z), Blocks.STONE.defaultBlockState());
            }
        }

        HiredWorkContext context = context(
                helper, villager, new CompoundTag(), new BlockPos(1, 2, 1), new BlockPos(11, 4, 11), true);
        context.inventory().insertSupply(new ItemStack(Items.BUCKET));
        AnimalBreedingWorker worker = new AnimalBreedingWorker();
        for (int attempt = 0; attempt < 3; attempt++) {
            worker.tick(level, villager, hirer, context);
        }

        helper.assertTrue(
                HiredPathMemory.isAvoided(level, villager, cow.blockPosition()),
                "third failed animal path should temporarily blacklist the target");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.BUCKET),
                1,
                "unreachable animal should not consume its handling supply");
        HiredPathMemory.clear(villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void animalHandlerCullCapUsesPerTypePoolsAndWeapon(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 7, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalCull");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(2, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(4, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(3, 2, 4));
        spawnAnimal(helper, EntityType.PIG, new BlockPos(2, 2, 4));
        spawnAnimal(helper, EntityType.PIG, new BlockPos(4, 2, 4));

        CompoundTag state = new CompoundTag();
        HiredAnimalCullSettings.setCap(state, 2);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(6, 4, 6), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        AnimalBreedingWorker worker = new AnimalBreedingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 20, () ->
                countAliveAnimals(level, helper, Cow.class, new BlockPos(1, 2, 1), new BlockPos(6, 4, 6)) == 2);

        helper.assertValueEqual(
                countAliveAnimals(level, helper, Cow.class, new BlockPos(1, 2, 1), new BlockPos(6, 4, 6)),
                2,
                "cow pool should be culled to cap");
        helper.assertValueEqual(
                countAliveAnimals(level, helper, Pig.class, new BlockPos(1, 2, 1), new BlockPos(6, 4, 6)),
                2,
                "pig pool should not share the cow cull cap overflow");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void animalHandlerCullsAnimalsAcrossLargeWorkArea(GameTestHelper helper) {
        buildFloor(helper, 0, 32, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalCullDistant");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        Cow first = spawnAnimal(helper, EntityType.COW, new BlockPos(26, 2, 3));
        Cow second = spawnAnimal(helper, EntityType.COW, new BlockPos(28, 2, 3));
        Cow third = spawnAnimal(helper, EntityType.COW, new BlockPos(30, 2, 3));
        first.setNoAi(true);
        second.setNoAi(true);
        third.setNoAi(true);

        CompoundTag state = new CompoundTag();
        HiredAnimalCullSettings.setCap(state, 2);
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(31, 4, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        AnimalBreedingWorker worker = new AnimalBreedingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 240, () ->
                countAliveAnimals(level, helper, Cow.class, new BlockPos(1, 2, 1), new BlockPos(31, 4, 5)) == 2);

        helper.assertValueEqual(
                countAliveAnimals(level, helper, Cow.class, new BlockPos(1, 2, 1), new BlockPos(31, 4, 5)),
                2,
                "animal handler should cull a distant over-cap herd anywhere in its work area");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void animalHandlerBreedsAnimalsAcrossLargeWorkArea(GameTestHelper helper) {
        buildFloor(helper, 0, 32, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalBreedDistant");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        Cow first = spawnAnimal(helper, EntityType.COW, new BlockPos(28, 2, 3));
        Cow second = spawnAnimal(helper, EntityType.COW, new BlockPos(30, 2, 3));
        first.setNoAi(true);
        second.setNoAi(true);

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(31, 4, 5), true);
        context.inventory().insertSupply(new ItemStack(Items.WHEAT, 2));
        AnimalBreedingWorker worker = new AnimalBreedingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 240, () -> first.isInLove() && second.isInLove());

        helper.assertTrue(
                first.isInLove() && second.isInLove(),
                "animal handler should breed a distant pair anywhere in its work area");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void animalHandlerCullCapDoesNotCullBabies(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 7, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalCullBabies");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(2, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(4, 2, 3));
        Cow firstBaby = spawnAnimal(helper, EntityType.COW, new BlockPos(2, 2, 4));
        Cow secondBaby = spawnAnimal(helper, EntityType.COW, new BlockPos(4, 2, 4));
        firstBaby.setBaby(true);
        secondBaby.setBaby(true);

        CompoundTag state = new CompoundTag();
        HiredAnimalCullSettings.setCap(state, 2);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(6, 4, 6), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        AnimalBreedingWorker worker = new AnimalBreedingWorker();

        for (int tick = 0; tick < 20; tick++) {
            worker.maintain(level, villager, context);
            worker.tick(level, villager, hirer, context);
            level.tickNonPassenger(villager);
        }

        helper.assertValueEqual(
                countAliveAdultAnimals(level, helper, Cow.class, new BlockPos(1, 2, 1), new BlockPos(6, 4, 6)),
                2,
                "adult cow pool should already be at the cap");
        helper.assertValueEqual(
                countAliveBabyAnimals(level, helper, Cow.class, new BlockPos(1, 2, 1), new BlockPos(6, 4, 6)),
                2,
                "baby cows should not be culled or counted toward the adult cull cap");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void animalHandlerCullCapRequiresSwordOrAxe(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrAnimalCullNoWeapon");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(2, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(4, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(3, 2, 4));

        CompoundTag state = new CompoundTag();
        HiredAnimalCullSettings.setCap(state, 2);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 5), true);
        AnimalBreedingWorker worker = new AnimalBreedingWorker();

        WorkResult result = worker.tick(level, villager, hirer, context);

        helper.assertValueEqual(
                result.status(),
                "interaction.work.animal_breeding.missing_cull_weapon",
                "over-cap animal handling should request a sword or axe");
        helper.assertValueEqual(
                countAliveAnimals(level, helper, Cow.class, new BlockPos(1, 2, 1), new BlockPos(5, 4, 5)),
                3,
                "animal handler should not cull without a sword or axe");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void animalHandlerCollectsCullDrops(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 7, 1);
        ServerLevel level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(true, level.getServer());
        ServerPlayer hirer = fakePlayer(level, "VrAnimalCullDrops");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(2, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(4, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(3, 2, 4));

        CompoundTag state = new CompoundTag();
        HiredAnimalCullSettings.setCap(state, 2);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(6, 4, 6), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        AnimalBreedingWorker worker = new AnimalBreedingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 100, () ->
                countAliveAnimals(level, helper, Cow.class, new BlockPos(1, 2, 1), new BlockPos(6, 4, 6)) == 2
                        && countInventoryItem(context.inventory(), Items.BEEF) > 0);

        helper.assertTrue(
                countInventoryItem(context.inventory(), Items.BEEF) > 0,
                "animal handler should collect beef drops from culled cows into job inventory");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void animalHandlerCollectsCullDropsBeforeNextTarget(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 7, 1);
        ServerLevel level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(true, level.getServer());
        ServerPlayer hirer = fakePlayer(level, "VrAnimalCullDropOrder");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(2, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(4, 2, 3));
        spawnAnimal(helper, EntityType.COW, new BlockPos(3, 2, 4));
        spawnAnimal(helper, EntityType.COW, new BlockPos(4, 2, 4));

        CompoundTag state = new CompoundTag();
        HiredAnimalCullSettings.setCap(state, 2);
        HiredWorkContext context = context(
                helper,
                villager,
                state,
                new BlockPos(1, 2, 1),
                new BlockPos(6, 4, 6),
                true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        AnimalBreedingWorker worker = new AnimalBreedingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 100, () ->
                countAliveAnimals(level, helper, Cow.class, new BlockPos(1, 2, 1), new BlockPos(6, 4, 6)) == 3);

        ItemEntity guaranteedDrop = new ItemEntity(
                level,
                villager.getX(),
                villager.getY(),
                villager.getZ(),
                new ItemStack(Items.BEEF));
        guaranteedDrop.setNoPickUpDelay();
        level.addFreshEntity(guaranteedDrop);

        for (int tick = 0; tick < 3; tick++) {
            worker.tick(level, villager, hirer, context);
        }

        helper.assertValueEqual(
                countAliveAnimals(level, helper, Cow.class, new BlockPos(1, 2, 1), new BlockPos(6, 4, 6)),
                3,
                "animal handler must finish post-cull cleanup before starting another cull");
        helper.assertTrue(
                countInventoryItem(context.inventory(), Items.BEEF) > 0,
                "animal handler should pick up the completed cull's drops during cleanup");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hunterCollectsLootNearRoute(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 8, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrHunterRouteLoot");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(new BlockPos(2, 2, 3), new BlockPos(6, 2, 3)));
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));

        BlockPos lootPos = helper.absolutePos(new BlockPos(4, 2, 3));
        ItemEntity beef = new ItemEntity(
                level,
                lootPos.getX() + 0.5D,
                lootPos.getY(),
                lootPos.getZ() + 0.5D,
                new ItemStack(Items.BEEF, 2));
        level.addFreshEntity(beef);

        WorkResult result = new HuntingWorker().tick(level, villager, hirer, context);

        helper.assertTrue(result != null, "hunter should process route loot");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.BEEF), 2, "hunter should collect route loot into job output");
        helper.assertFalse(beef.isAlive(), "collected route loot item should be removed");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hunterRecoversStuckArrowNearRoute(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 8, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrHunterRouteArrow");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(new BlockPos(2, 2, 3), new BlockPos(6, 2, 3)));
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.BOW));

        BlockPos arrowPos = helper.absolutePos(new BlockPos(6, 2, 3));
        Arrow arrow = new Arrow(
                level,
                arrowPos.getX() + 0.5D,
                arrowPos.getY(),
                arrowPos.getZ() + 0.5D,
                new ItemStack(Items.ARROW),
                null);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        ((AbstractArrowAccessor) arrow).villagerretaliation$setInGround(true);
        level.addFreshEntity(arrow);

        HuntingWorker worker = new HuntingWorker();
        WorkResult firstResult = worker.tick(level, villager, hirer, context);

        helper.assertTrue(firstResult != null, "hunter should process route arrow recovery");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.ARROW), 0, "hunter should not recover route arrow from range");
        helper.assertTrue(arrow.isAlive(), "route arrow should remain until the hunter walks over to it");

        runWorkerUntil(helper, worker, level, villager, hirer, context, 100, () ->
                countInventoryItem(context.inventory(), Items.ARROW) == 1 && !arrow.isAlive());

        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.ARROW), 1, "hunter should recover stuck route arrow into supplies");
        helper.assertFalse(arrow.isAlive(), "recovered route arrow entity should be removed");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hunterClearsActiveTargetToRecoverMissingCrossbowAmmo(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 8, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrHunterActiveAmmo");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FLETCHER));
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8, HiredVillagerRole.HUNTING);

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(7, 4, 7), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.CROSSBOW));
        Cow target = spawnAnimal(helper, EntityType.COW, new BlockPos(4, 2, 3));
        villager.setTarget(target);

        BlockPos arrowPos = helper.absolutePos(new BlockPos(6, 2, 3));
        Arrow arrow = new Arrow(
                level,
                arrowPos.getX() + 0.5D,
                arrowPos.getY(),
                arrowPos.getZ() + 0.5D,
                new ItemStack(Items.ARROW),
                null);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        ((AbstractArrowAccessor) arrow).villagerretaliation$setInGround(true);
        level.addFreshEntity(arrow);

        WorkResult result = new HuntingWorker().tick(level, villager, hirer, context);

        helper.assertTrue(result != null, "hunter should process missing active-target ammo");
        helper.assertValueEqual(result.status(), HiredRangedAmmo.STATUS_COLLECTING, "hunter should recover ammo before chasing");
        helper.assertTrue(target.isAlive(), "ammo recovery should not hurt the hunting target");
        helper.assertTrue(villager.getTarget() == null, "hunter should clear active target while restocking ranged ammo");
        helper.assertTrue(arrow.isAlive(), "hunter should walk to the arrow instead of recovering it from range");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.ARROW), 0, "hunter should not recover arrow until in pickup reach");

        target.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void normalAndStressHuntersShareProductionTargetingAndReacquire(GameTestHelper helper) {
        buildFloor(helper, 0, 18, 0, 8, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrHunterParity");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager normalHunter = spawnVillager(helper, new BlockPos(3, 2, 3));
        Villager stressHunter = spawnVillager(helper, new BlockPos(13, 2, 3));
        normalHunter.setVillagerData(normalHunter.getVillagerData().setProfession(VillagerProfession.FLETCHER));
        stressHunter.setVillagerData(stressHunter.getVillagerData().setProfession(VillagerProfession.FLETCHER));
        Villager normalDecoy = spawnVillager(helper, new BlockPos(3, 2, 3));
        Villager stressDecoy = spawnVillager(helper, new BlockPos(13, 2, 3));
        Cow normalTarget = spawnAnimal(helper, EntityType.COW, new BlockPos(3, 2, 4));
        Cow stressTarget = spawnAnimal(helper, EntityType.COW, new BlockPos(13, 2, 4));

        helper.assertTrue(HiredVillagerContractService.startHireContract(
                        level, normalHunter, hirer, 1, 32, HiredVillagerRole.HUNTING),
                "normal hunter should accept a production hunting contract");
        helper.assertTrue(HiredVillagerContractService.startHireContract(
                        level, stressHunter, hirer, 1, 32, HiredVillagerRole.HUNTING),
                "stress-tagged hunter should accept the same production hunting contract");
        helper.assertTrue(HiredVillagerWorkService.setWorkArea(
                        hirer, level, normalHunter,
                        helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(8, 4, 7))),
                "normal hunter should accept its work area");
        helper.assertTrue(HiredVillagerWorkService.setWorkArea(
                        hirer, level, stressHunter,
                        helper.absolutePos(new BlockPos(10, 2, 1)), helper.absolutePos(new BlockPos(17, 4, 7))),
                "stress-tagged hunter should accept its work area through production logic");
        HiredWorkSession normalSession = HiredWorkSession.active(level, normalHunter);
        HiredWorkSession stressSession = HiredWorkSession.active(level, stressHunter);
        CompoundTag normalState = normalSession.state();
        CompoundTag stressState = stressSession.state();
        for (CompoundTag state : List.of(normalState, stressState)) {
            state.putBoolean(HiredHuntingTargets.HUNT_ANIMALS_TAG, true);
            state.putBoolean(HiredHuntingTargets.HUNT_HOSTILES_TAG, false);
            state.putBoolean(HiredHuntingTargets.HUNT_PLAYERS_TAG, false);
        }
        HiredWorkContext normalContext = normalSession.context();
        HiredWorkContext stressContext = stressSession.context();
        normalContext.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        stressContext.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        stressHunter.getPersistentData().putBoolean("VillagerRetaliationHiredStressWorker", true);
        stressHunter.getPersistentData().putString("VillagerRetaliationHiredStressRole", "hunting");

        HiredHuntingTargets.Selection normalTargets = HiredHuntingTargets.fromState(normalState);
        HiredHuntingTargets.Selection stressTargets = HiredHuntingTargets.fromState(stressState);
        helper.assertTrue(HuntingWorker.tryAcquireTarget(
                        level, normalHunter, normalContext, normalTargets),
                "normal hunter should acquire through the production scan path");
        helper.assertTrue(HuntingWorker.tryAcquireTarget(
                        level, stressHunter, stressContext, stressTargets),
                "stress-tagged hunter should acquire through the same production scan path");

        helper.assertTrue(com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.hasRetaliationTarget(normalHunter, normalTarget),
                "normal animal-only hunter should skip a closer irrelevant villager and acquire the cow");
        helper.assertTrue(com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.hasRetaliationTarget(stressHunter, stressTarget),
                "stress-tagged hunter should use the same production acquisition path as a normal hunter");

        normalTarget.discard();
        com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.clearCustomTarget(normalHunter);
        ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData())
                .setGameTime(level.getGameTime() + 21L);
        Cow replacement = spawnAnimal(helper, EntityType.COW, new BlockPos(3, 2, 4));
        helper.assertTrue(HuntingWorker.tryAcquireTarget(
                        level, normalHunter, normalContext, normalTargets),
                "hunter should rerun the production scan after its normal interval");
        helper.assertTrue(com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.hasRetaliationTarget(normalHunter, replacement),
                "hunter should reacquire a newly available eligible target after its normal scan interval");

        replacement.discard();
        stressTarget.discard();
        normalDecoy.discard();
        stressDecoy.discard();
        HiredVillagerContractService.endHireContract(level, normalHunter, hirer);
        HiredVillagerContractService.endHireContract(level, stressHunter, hirer);
        normalHunter.discard();
        stressHunter.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningSurfaceReturnTargetCachesAcrossMovementAndInvalidatesOnTerrainChange(GameTestHelper helper) {
        buildFloor(helper, 0, 12, 0, 10, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrMiningReturnCache");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager miner = spawnVillager(helper, new BlockPos(1, 2, 5));
        miner.setVillagerData(miner.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        helper.assertTrue(
                HiredVillagerContractService.startHireContract(
                        level, miner, hirer, 1, 32, HiredVillagerRole.MINING),
                "miner should accept a contract for return-target caching");
        helper.assertTrue(
                HiredVillagerWorkService.setWorkArea(
                        hirer,
                        level,
                        miner,
                        helper.absolutePos(new BlockPos(6, 1, 3)),
                        helper.absolutePos(new BlockPos(10, 1, 7))),
                "mining area should be assigned");
        HiredWorkSession session = HiredWorkSession.active(level, miner);
        session.state().putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        MiningWorker.invalidateExcavationReturnTarget(miner);
        HiredPathMemory.clear(miner);

        BlockPos firstTarget = MiningWorker.excavationReturnTarget(level, miner, session.context());
        long firstPathCount = HiredPathMemory.pathCreationDebug(level, miner).totalCount();
        helper.assertTrue(firstTarget != null && firstPathCount > 0,
                "initial excavation return target should perform a reachable surface search");

        BlockPos moved = helper.absolutePos(new BlockPos(2, 2, 5));
        miner.setPos(moved.getX() + 0.5D, moved.getY(), moved.getZ() + 0.5D);
        BlockPos cachedTarget = MiningWorker.excavationReturnTarget(level, miner, session.context());
        helper.assertValueEqual(cachedTarget, firstTarget,
                "walking miner should retain its selected surface entry target");
        helper.assertValueEqual(
                HiredPathMemory.pathCreationDebug(level, miner).totalCount(),
                firstPathCount,
                "walking across origin blocks must not recreate the surface-entry candidate paths");

        level.setBlock(firstTarget, Blocks.STONE.defaultBlockState(), 3);
        HiredPathMemory.onBlockChanged(level, firstTarget);
        BlockPos terrainAdjustedTarget = MiningWorker.excavationReturnTarget(level, miner, session.context());
        helper.assertTrue(terrainAdjustedTarget != null && !terrainAdjustedTarget.equals(firstTarget),
                "blocking the cached landing should invalidate it and select another surface entry");
        helper.assertTrue(HiredPathMemory.pathCreationDebug(level, miner).totalCount() > firstPathCount,
                "terrain invalidation should permit a fresh recovery path search");

        HiredVillagerContractService.endHireContract(level, miner, hirer);
        miner.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void managedMainHandCacheTracksMutationRestoreAndRuntimeInvalidation(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 1);
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack axe = new ItemStack(Items.IRON_AXE);

        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager, net.minecraft.world.entity.EquipmentSlot.MAINHAND, sword);
        CompoundTag swordOwnership = VillagerRetaliationVillagerEquipment.captureOwnershipState(villager);
        helper.assertTrue(
                VillagerRetaliationVillagerEquipment.playerManagedMainHandStack(villager).is(Items.IRON_SWORD),
                "manual main hand should be decoded into the runtime cache");

        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager, net.minecraft.world.entity.EquipmentSlot.MAINHAND, axe);
        helper.assertTrue(
                VillagerRetaliationVillagerEquipment.playerManagedMainHandStack(villager).is(Items.IRON_AXE),
                "authoritative mutation should update the cached managed stack immediately");

        VillagerRetaliationVillagerEquipment.restoreOwnershipState(villager, swordOwnership);
        helper.assertTrue(
                VillagerRetaliationVillagerEquipment.playerManagedMainHandStack(villager).is(Items.IRON_SWORD),
                "ownership restore should invalidate and decode the restored stack");
        VillagerRetaliationVillagerEquipment.clearRuntimeState(villager);
        helper.assertTrue(
                VillagerRetaliationVillagerEquipment.playerManagedMainHandStack(villager).is(Items.IRON_SWORD),
                "unload-style runtime invalidation should rebuild from persisted ownership data");
        VillagerRetaliationVillagerEquipment.clearPlayerManagedMainHand(villager);
        helper.assertTrue(
                VillagerRetaliationVillagerEquipment.playerManagedMainHandStack(villager).isEmpty(),
                "clearing ownership must also clear the cached stack");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void pausingBuilderWorkPreservesPaidTask(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = persistentWorkState(villager);
        seedBuilderTask(state, 19, 0);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.WORKING, helper.absolutePos(new BlockPos(3, 2, 3)));

        HiredVillagerWorkService.pauseWork(level, villager, HiredVillagerRole.BUILDER, "paused_for_test");

        helper.assertTrue(BuilderTaskState.hasTask(state), "temporary builder pause must keep the paid task");
        helper.assertValueEqual(BuilderTaskState.paidCurrency(state), 19, "paid amount should survive pause");
        helper.assertValueEqual(BuilderTaskState.placedIndex(state), 0, "build progress should survive pause");
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, level.getGameTime()).taskState(),
                HiredWorkerTaskState.AWAITING_INSTRUCTION,
                "paused builder should stop active movement without deleting the job");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void cancelingBuilderWorkClearsPaidTaskExplicitly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = persistentWorkState(villager);
        seedBuilderTask(state, 19, 3);

        HiredVillagerWorkService.cancelWork(level, villager, HiredVillagerRole.BUILDER, "cancelled_for_test");

        helper.assertFalse(BuilderTaskState.hasTask(state), "explicit builder cancellation should clear the task");
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, level.getGameTime()).taskState(),
                HiredWorkerTaskState.AWAITING_INSTRUCTION,
                "cancelled builder should return to awaiting instructions");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void builderEscrowRefundDoesNotDependOnVillagerWallet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerPlayer hirer = fakePlayer(level, "VrBuilderEscrow");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        UUID jobId = UUID.randomUUID();
        int beforeCurrency = countCurrency(hirer);
        int walletCurrency = VillagerWalletService.getCurrentEmeralds(villager);
        VillagerWalletService.spendCurrency(villager, walletCurrency);

        BuilderPaymentEscrowService.escrow(villager, jobId, 23);
        int refunded = BuilderPaymentEscrowService.refund(hirer, villager, Optional.of(jobId), 23);
        VillagerWalletService.addCurrency(villager, 50);
        int refundedAgain = BuilderPaymentEscrowService.refund(hirer, villager, Optional.of(jobId), 23);

        helper.assertValueEqual(refunded, 23, "escrow should refund the paid builder amount");
        helper.assertValueEqual(refundedAgain, 0, "builder escrow refund should be idempotent");
        helper.assertValueEqual(countCurrency(hirer) - beforeCurrency, 23, "refund should reach the hirer inventory");
        helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), 50, "repeated refund should not fall back to wallet funds");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void builderDeathFinalizesUnstartedEscrowWithoutLosingPayment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerPlayer hirer = fakePlayer(level, "VrBuilderDeath");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.MASON));
        HiredVillagerContractService.startOneOffBuilderJob(level, villager, hirer);

        CompoundTag state = persistentWorkState(villager);
        UUID jobId = seedBuilderTask(state, 23, 0);
        BuilderPaymentEscrowService.escrow(villager, jobId, 23);
        int beforeCurrency = countCurrency(hirer);
        BlockPos deathPos = villager.blockPosition();

        HiredVillagerContractService.onVillagerDeath(level, villager);

        helper.assertFalse(HiredVillagerContractService.isHired(level, villager), "dead villager should no longer have an active hire");
        helper.assertFalse(BuilderTaskState.hasTask(state), "death finalization should clear the unstarted builder task");
        int recovered = countCurrency(hirer) - beforeCurrency + countDroppedCurrency(level, deathPos);
        helper.assertValueEqual(recovered, 23, "unstarted builder escrow should refund or drop without losing payment");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void workAreaScanningIsBatchedAndCursorDriven(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(3, 3, 3), true);

        HiredWorkAreaScan.Result first = HiredWorkAreaScan.collect(context, "Cursor", 5, ignored -> true);
        helper.assertValueEqual(first.visitedPositions(), 5L, "first scan should visit only the configured batch");
        helper.assertFalse(first.completedFullPass(), "first scan should not finish a larger area");
        helper.assertTrue(HiredWorkAreaScan.isInProgress(context, "Cursor"), "scan cursor should persist between work ticks");

        long cursorAfterFirstBatch = state.getLong("Cursor");
        HiredWorkAreaScan.Result second = HiredWorkAreaScan.collect(context, "Cursor", 5, pos -> pos.getY() == context.workMax().getY());
        helper.assertTrue(state.getLong("Cursor") != cursorAfterFirstBatch || second.completedFullPass(), "second scan should advance the cursor");
        helper.assertTrue(second.candidates().stream().allMatch(pos -> pos.getY() == context.workMax().getY()), "scan should respect candidate filters");

        int guard = 0;
        while (HiredWorkAreaScan.isInProgress(context, "Cursor") && guard++ < 10) {
            HiredWorkAreaScan.collect(context, "Cursor", 5, ignored -> true);
        }
        helper.assertFalse(HiredWorkAreaScan.isInProgress(context, "Cursor"), "scan cursor should clear after a full pass");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void targetSearchUsesActivePlannedAndCooldownBeforeScanning(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(4, 2, 4), true);
        HiredTargetSearch.Messages messages = new HiredTargetSearch.Messages(
                "active",
                "planned",
                "cooldown",
                "none",
                "partial_",
                "found",
                40);

        BlockPos active = new BlockPos(2, 2, 2);
        BlockPos activeResult = HiredTargetSearch.find(
                helper.getLevel(),
                context,
                () -> active,
                pos -> true,
                ignored -> null,
                pos -> true,
                "NextScan",
                "ScanCursor",
                1,
                candidates -> candidates.isEmpty() ? null : candidates.getFirst(),
                messages);
        helper.assertValueEqual(activeResult, active, "active target should win without scanning");
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, helper.getLevel().getGameTime()).lastTargetScanResult(),
                "active",
                "active scan result");

        state.putLong("NextScan", helper.getLevel().getGameTime() + 100L);
        BlockPos cooldownResult = HiredTargetSearch.find(
                helper.getLevel(),
                context,
                () -> null,
                pos -> true,
                ignored -> null,
                pos -> true,
                "NextScan",
                "ScanCursor",
                1,
                candidates -> candidates.isEmpty() ? null : candidates.getFirst(),
                messages);
        helper.assertTrue(cooldownResult == null, "search should honor no-target cooldown before a new scan");
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, helper.getLevel().getGameTime()).lastTargetScanResult(),
                "cooldown",
                "cooldown scan result");

        state.remove("NextScan");
        BlockPos scanned = HiredTargetSearch.find(
                helper.getLevel(),
                context,
                () -> null,
                pos -> true,
                ignored -> null,
                pos -> pos.equals(context.workMin()),
                "NextScan",
                "ScanCursor",
                64,
                candidates -> candidates.isEmpty() ? null : candidates.getFirst(),
                messages);
        helper.assertValueEqual(scanned, context.workMin(), "search should rebuild from filtered scan candidates");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void fishingWorkerRejectsWaterloggedSlabWater(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrFishingSlab");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 2));
        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(6, 3, 3), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.FISHING_ROD));
        setBlock(
                helper,
                new BlockPos(5, 2, 2),
                Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));

        WorkResult result = new FishingWorker().tick(level, villager, hirer, context);

        helper.assertFalse(
                "interaction.work.fishing.cast".equals(result.status()),
                "waterlogged slab should not count as an open fishing cast target");
        helper.assertTrue(
                level.getEntitiesOfClass(VillagerFishingHook.class, villager.getBoundingBox().inflate(16.0D)).isEmpty(),
                "fisherman should not spawn a hook through a waterlogged slab");
        helper.assertFalse(state.contains("FishingWaterPos"), "blocked water should not be remembered as a fishing target");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void fishingWorkerDepositsAfterEveryCatch(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrFishingCatchDeposit");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos chestRel = new BlockPos(3, 2, 2);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(
                helper,
                villager,
                state,
                new BlockPos(1, 2, 1),
                new BlockPos(5, 4, 5),
                true);
        context.inventory().insertOutput(new ItemStack(Items.COD));
        FishingWorker.queueCompletedCatch(context);

        WorkResult result = new FishingWorker().tick(level, villager, hirer, context);

        helper.assertValueEqual(
                countItem(container(level, chest), Items.COD),
                1,
                "fisherman should deposit a catch without waiting for output inventory to fill");
        helper.assertFalse(context.hasOutputToDeposit(), "completed catch should leave no carried output");
        helper.assertTrue(result.completed(), "catch should complete after the immediate deposit");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void fishingWorkerAimsPastVeryCloseShoreWater(GameTestHelper helper) {
        buildFloor(helper, 0, 11, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(6, 2, 2));
        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(0, 2, 1), new BlockPos(11, 3, 3), true);
        for (int x = 2; x <= 5; x++) {
            setBlock(helper, new BlockPos(x, 2, 2), Blocks.WATER.defaultBlockState());
        }

        Vec3 target = FishingWorker.castTarget(level, context, villager, helper.absolutePos(new BlockPos(5, 2, 2)));
        BlockPos targetBlock = helper.relativePos(BlockPos.containing(target.x, target.y, target.z));

        helper.assertTrue(targetBlock.getX() <= 3, "near-shore cast should aim farther into connected open water");
        helper.assertTrue(
                horizontalDistance(new BlockPos(6, 2, 2), targetBlock) >= 3.0D,
                "extended cast target should avoid a stubby fishing line");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void pathingFindsAdjacentReachableTargetAndRejectsBlockedOrOutOfAreaTargets(GameTestHelper helper) {
        buildFloor(helper, 0, 9, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 3));
        ServerLevel level = helper.getLevel();
        tickVillager(level, villager, 20);
        BlockPos reachable = helper.absolutePos(new BlockPos(6, 2, 3));
        setBlock(helper, new BlockPos(6, 2, 3), Blocks.COAL_ORE.defaultBlockState());

        BlockPos directApproach = helper.absolutePos(new BlockPos(5, 2, 3));
        helper.assertTrue(HiredMoveToBlockFaceJob.isValidApproachPosition(level, directApproach), "direct ore approach should be walkable");
        helper.assertTrue(
                HiredMoveToBlockFaceJob.visibleHitPosition(level, villager, villager.getEyePosition(), reachable) != null,
                "reachable ore should expose a visible face");
        net.minecraft.world.level.pathfinder.Path directPath = villager.getNavigation().createPath(directApproach, 0);
        helper.assertTrue(directPath != null && directPath.canReach(), "vanilla navigation should reach the direct ore approach");

        HiredPathResult reachableResult = new HiredMoveToBlockFaceJob(level, villager, List.of(reachable), 16).search();
        helper.assertTrue(reachableResult.reachesDestination(), "nearby exposed ore should have an adjacent reachable approach");
        helper.assertTrue(reachableResult.target() != null, "reachable result should include a target");
        helper.assertTrue(reachableResult.target().approachPos().distSqr(reachable) <= 4, "worker should choose an adjacent approach");
        helper.assertTrue(
                HiredMoveToBlockFaceJob.canReachFromCurrentPosition(level, villager, reachableResult.target())
                        || reachableResult.path() != null && reachableResult.path().canReach(),
                "target should be immediately reachable or have a complete path");

        HiredPathResult outOfArea = new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(reachable),
                16,
                pos -> pos.getX() < reachable.getX()).search();
        helper.assertFalse(outOfArea.reachesDestination(), "work-area filter should reject targets outside the allowed area");

        BlockPos blocked = helper.absolutePos(new BlockPos(8, 2, 3));
        setBlock(helper, new BlockPos(8, 2, 3), Blocks.COAL_ORE.defaultBlockState());
        for (BlockPos rel : List.of(
                new BlockPos(7, 2, 3),
                new BlockPos(9, 2, 3),
                new BlockPos(8, 2, 2),
                new BlockPos(8, 2, 4),
                new BlockPos(8, 3, 3))) {
            setBlock(helper, rel, Blocks.STONE.defaultBlockState());
        }
        HiredPathResult blockedResult = new HiredMoveToBlockFaceJob(level, villager, List.of(blocked), 16).search();
        helper.assertFalse(blockedResult.reachesDestination(), "fully blocked target should fail gracefully");

        setBlock(helper, new BlockPos(3, 2, 3), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 3, 3), Blocks.STONE.defaultBlockState());
        BlockPos observer = helper.absolutePos(new BlockPos(1, 2, 3));
        villager.moveTo(observer.getX() + 0.5D, observer.getY(), observer.getZ() + 0.5D, 0.0F, 0.0F);
        Vec3 eye = villager.getEyePosition();
        Vec3 hit = Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 3)));
        setBlock(helper, new BlockPos(4, 2, 3), Blocks.COAL_ORE.defaultBlockState());
        helper.assertFalse(
                HiredMoveToBlockFaceJob.hasLineOfSightToBlock(level, villager, eye, helper.absolutePos(new BlockPos(4, 2, 3)), hit),
                "solid blocks should block required line of sight");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void pathingCanTargetSaplingsWithoutStandingOnSaplings(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        ServerLevel level = helper.getLevel();
        tickVillager(level, villager, 20);

        BlockPos sapling = helper.absolutePos(new BlockPos(3, 2, 3));
        setBlock(helper, new BlockPos(3, 2, 3), Blocks.OAK_SAPLING.defaultBlockState());
        villager.moveTo(sapling.getX() + 0.5D, sapling.getY(), sapling.getZ() + 0.5D, 0.0F, 0.0F);

        HiredPathResult result = new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(sapling),
                16,
                ignored -> true,
                pos -> !level.getBlockState(pos).is(BlockTags.SAPLINGS),
                pos -> pos.equals(villager.blockPosition()) || !level.getBlockState(pos).is(BlockTags.SAPLINGS),
                ignored -> false).search();

        helper.assertTrue(result.reachesDestination(), "sapling target should still be reachable");
        helper.assertTrue(result.target() != null, "sapling target should have an approach");
        helper.assertFalse(result.target().approachPos().equals(sapling), "approach should not be the sapling block");
        helper.assertFalse(
                level.getBlockState(result.target().approachPos()).is(BlockTags.SAPLINGS),
                "approach should not stand inside any sapling");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void pathingCanRouteAroundSimpleObstaclesWithoutLeavingTheWorkArea(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 8, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 4));
        ServerLevel level = helper.getLevel();
        tickVillager(level, villager, 20);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 4));
        villager.moveTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
        villager.setDeltaMovement(Vec3.ZERO);
        villager.getNavigation().stop();
        for (int z = 3; z <= 5; z++) {
            setBlock(helper, new BlockPos(4, 2, z), Blocks.STONE.defaultBlockState());
            setBlock(helper, new BlockPos(4, 3, z), Blocks.STONE.defaultBlockState());
        }
        BlockPos target = helper.absolutePos(new BlockPos(8, 2, 4));
        setBlock(helper, new BlockPos(8, 2, 4), Blocks.COAL_ORE.defaultBlockState());
        BlockPos routeApproach = helper.absolutePos(new BlockPos(7, 2, 4));
        net.minecraft.world.level.pathfinder.Path routePath = villager.getNavigation().createPath(routeApproach, 0);
        helper.assertTrue(routePath != null && routePath.canReach(), "vanilla navigation should route around the obstacle to the target approach");

        HiredPathResult result = new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(target),
                32,
                pos -> pos.getX() >= helper.absolutePos(new BlockPos(0, 0, 0)).getX()
                        && pos.getX() <= helper.absolutePos(new BlockPos(10, 0, 0)).getX()
                        && pos.getZ() >= helper.absolutePos(new BlockPos(0, 0, 0)).getZ()
                        && pos.getZ() <= helper.absolutePos(new BlockPos(0, 0, 8)).getZ()).search();

        helper.assertTrue(result.reachesDestination(), "path search should route around a simple obstacle wall");
        helper.assertTrue(result.path() == null || HiredMoveToBlockFaceJob.pathStaysInsideFilter(result.path(), pos ->
                pos.getX() >= helper.absolutePos(new BlockPos(0, 0, 0)).getX()
                        && pos.getX() <= helper.absolutePos(new BlockPos(10, 0, 0)).getX()
                        && pos.getZ() >= helper.absolutePos(new BlockPos(0, 0, 0)).getZ()
                        && pos.getZ() <= helper.absolutePos(new BlockPos(0, 0, 8)).getZ()), "path should stay in the allowed area");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void pathMemoryAvoidsRepeatedFailuresReservationsAndNavigationStalls(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        Villager first = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager second = spawnVillager(helper, new BlockPos(2, 2, 1));
        tickVillager(level, first, 20);
        tickVillager(level, second, 20);
        BlockPos target = helper.absolutePos(new BlockPos(4, 2, 4));

        net.minecraft.world.level.pathfinder.Path firstPath = HiredPathMemory.createPath(level, first, target, 0);
        net.minecraft.world.level.pathfinder.Path cachedPath = HiredPathMemory.createPath(level, first, target, 0);
        helper.assertTrue(firstPath != null && cachedPath != null, "reachable paths should be created and cached");
        HiredPathMemory.PathCreationDebug pathDebug = HiredPathMemory.pathCreationDebug(level, first);
        helper.assertValueEqual(pathDebug.totalCount(), 1L, "second identical path should reuse cache");
        helper.assertValueEqual(pathDebug.cacheHitTotal(), 1L, "cache hit should be tracked");
        HiredPathMemory.onBlockChanged(level, target);
        HiredPathMemory.createPath(level, first, target, 0);
        pathDebug = HiredPathMemory.pathCreationDebug(level, first);
        helper.assertValueEqual(pathDebug.totalCount(), 2L, "block changes should invalidate cached paths touching the target chunk");

        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "nether level should exist for path memory dimension regression");
        HiredPathMemory.rememberRecent(nether, target);
        helper.assertValueEqual(HiredPathMemory.recentCost(first, target), 0.0D, "recent targets should not leak across dimensions");
        HiredPathMemory.rememberRecent(level, target);
        helper.assertTrue(HiredPathMemory.recentCost(first, target) > 0.0D, "same-dimension recent targets should add candidate cost");

        helper.assertFalse(HiredPathMemory.recordFailure(level, first, target), "first path failure should not blacklist");
        helper.assertFalse(HiredPathMemory.recordFailure(level, first, target), "second path failure should not blacklist");
        helper.assertTrue(HiredPathMemory.recordFailure(level, first, target), "third path failure should blacklist");
        helper.assertTrue(HiredPathMemory.isAvoided(level, first, target), "blacklisted target should be avoided");

        HiredPathMemory.reserveTarget(level, first, target);
        helper.assertTrue(HiredPathMemory.isReservedByOther(level, second, target), "other workers should respect target reservations");
        HiredPathMemory.releaseAll(first);
        helper.assertFalse(HiredPathMemory.isReservedByOther(level, second, target), "reservations should clear when a worker stops");

        HiredPathMemory.rememberNavigationProgress(level, first, target, 25.0D);
        helper.assertFalse(HiredPathMemory.isNavigationBlocked(level, first, target, 25.0D), "same-tick navigation should not be marked stuck");

        helper.assertValueEqual(HiredPathMemory.adjustedCandidateLimit(first, 64), 64, "fresh path searches should use the full candidate cap");
        HiredPathMemory.recordPathSearchFailure(level, first);
        helper.assertTrue(HiredPathMemory.pathSearchRetryCooldownTicks(level, first) > 0L, "path failures should schedule a retry cooldown");
        HiredPathMemory.recordPathSearchFailure(level, first);
        helper.assertTrue(HiredPathMemory.adjustedCandidateLimit(first, 64) < 64, "repeated path failures should reduce candidate cap");
        helper.assertValueEqual(HiredPathMemory.adjustedCandidateLimitForDistance(64, 47.0D * 47.0D, false), 64, "near workers should keep full path search budget");
        helper.assertTrue(HiredPathMemory.adjustedCandidateLimitForDistance(64, 49.0D * 49.0D, false) < 64, "far workers should use a smaller path search budget");
        helper.assertTrue(HiredPathMemory.adjustedCandidateLimitForDistance(64, 97.0D * 97.0D, false) <= 16, "very far workers should use a deep LOD path search budget");
        helper.assertValueEqual(HiredPathMemory.adjustedCandidateLimitForDistance(64, 97.0D * 97.0D, true), 64, "urgent workers should keep full path search budget");
        helper.assertValueEqual(HiredPathMemory.adjustedCandidateLimitForDistance(64, Double.MAX_VALUE, false), 64, "worker searches without observers should keep full path search budget");
        BlockPos approach = helper.absolutePos(new BlockPos(3, 2, 3));
        HiredPathMemory.rememberUnreachableApproach(level, first, approach);
        helper.assertTrue(HiredPathMemory.isApproachRecentlyUnreachable(level, first, approach), "failed approach positions should be cached");
        HiredPathMemory.onBlockChanged(level, approach);
        helper.assertFalse(HiredPathMemory.isApproachRecentlyUnreachable(level, first, approach), "changed terrain should retry failed approaches");
        helper.assertValueEqual(HiredPathMemory.pathSearchRetryCooldownTicks(level, first), 0L, "changed terrain should clear path search backoff");
        HiredPathMemory.clearUnreachableApproach(first, approach);
        HiredPathMemory.clearPathSearchFailures(first);
        BlockPos chunkInteriorChange = helper.absolutePos(new BlockPos(1, 2, 1)).immutable();
        BlockPos neighborChunkApproach = new BlockPos(
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(chunkInteriorChange.getX()) + 1),
                chunkInteriorChange.getY(),
                chunkInteriorChange.getZ());
        HiredPathMemory.recordPathSearchFailure(level, first);
        HiredPathMemory.rememberUnreachableApproach(level, first, neighborChunkApproach);
        HiredPathMemory.onBlockChanged(level, chunkInteriorChange);
        helper.assertFalse(HiredPathMemory.isApproachRecentlyUnreachable(level, first, neighborChunkApproach), "chunk-radius terrain changes should retry neighboring chunk approaches");
        helper.assertValueEqual(HiredPathMemory.pathSearchRetryCooldownTicks(level, first), 0L, "chunk-radius terrain changes should clear neighboring path backoff");
        helper.assertValueEqual(
                HiredPathMemory.adjustedCandidateLimit(level, first, 64),
                HiredPathMemory.adjustedCandidateLimit(first, 64),
                "unhired direct path probes should ignore player-distance LOD");

        HiredPathMemory.clear(first);
        HiredPathMemory.clear(second);
        first.discard();
        second.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void jobInventoryProtectsGearAndUsesEmptyGridSlotsDynamically(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);

        ItemStack protectedEmerald = HiredJobInventory.markAsProtectedVillagerProperty(
                new ItemStack(Items.EMERALD),
                villager,
                "gametest");
        inventory.setItem(HiredJobInventory.MAIN_GRID_START, protectedEmerald);
        ItemStack removed = inventory.removeItem(HiredJobInventory.MAIN_GRID_START, 1);
        helper.assertTrue(removed.isEmpty(), "protected job items should not be removable by automation");
        helper.assertValueEqual(inventory.slotType(HiredJobInventory.MAIN_GRID_START),
                HiredJobInventorySlotType.PROTECTED_PROPERTY, "protected slot type");

        ItemStack outputRemainder = inventory.insertOutput(new ItemStack(Items.WHEAT, 3));
        helper.assertTrue(outputRemainder.isEmpty(), "output should fit into output slots");
        helper.assertTrue(inventory.getItem(HiredJobInventory.MAIN_GRID_START).is(Items.EMERALD),
                "protected output slot should remain untouched");
        helper.assertTrue(inventory.getItem(HiredJobInventory.MAIN_GRID_START + 1).is(Items.WHEAT),
                "output should use the next main-grid slot");

        for (int slot = HiredJobInventory.HOTBAR_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            helper.assertTrue(inventory.getItem(slot).isEmpty(), "outputs should not occupy hotbar slot " + slot);
            helper.assertValueEqual(inventory.slotType(slot), HiredJobInventorySlotType.SUPPLY,
                    "empty hotbar slot should retain supply preference " + slot);
        }

        for (int i = 0; i < HiredJobInventory.MAIN_GRID_SLOT_COUNT - 2; i++) {
            helper.assertTrue(inventory.insertOutput(new ItemStack(Items.COBBLESTONE, 64)).isEmpty(), "output filler should fit " + i);
        }
        ItemStack overflowOutput = inventory.insertOutput(new ItemStack(Items.DIRT, 3));
        helper.assertTrue(overflowOutput.isEmpty(), "outputs should spill into the hotbar after the main grid fills");
        helper.assertTrue(inventory.getItem(HiredJobInventory.HOTBAR_START).is(Items.DIRT),
                "output overflow should claim the first hotbar slot");
        helper.assertValueEqual(inventory.slotType(HiredJobInventory.HOTBAR_START),
                HiredJobInventorySlotType.OUTPUT, "claimed output overflow slot type");

        inventory.clearContent();
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.HOTBAR_START; slot++) {
            inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
        }

        ItemStack supplyRemainder = inventory.insertSupplyFromStorage(new ItemStack(Items.LADDER, 3));
        helper.assertTrue(supplyRemainder.isEmpty(), "storage-sourced supplies should spill into an empty hotbar slot");
        helper.assertTrue(inventory.getItem(HiredJobInventory.HOTBAR_START).is(Items.LADDER),
                "supply overflow should claim the first empty hotbar slot");
        helper.assertValueEqual(inventory.slotType(HiredJobInventory.HOTBAR_START),
                HiredJobInventorySlotType.SUPPLY, "claimed supply overflow slot type");
        helper.assertTrue(HiredJobInventory.isJobItem(inventory.getItem(HiredJobInventory.HOTBAR_START)),
                "storage-sourced supplies should be tagged as job items");

        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));
        ItemStack toolRemainder = inventory.insertToolFromStorage(new ItemStack(Items.DIAMOND_PICKAXE));
        helper.assertTrue(toolRemainder.isEmpty(), "storage tools should use the next empty hotbar slot");
        helper.assertTrue(inventory.getItem(HiredJobInventory.HOTBAR_START + 1).is(Items.DIAMOND_PICKAXE),
                "tool overflow should prioritize the hotbar");
        helper.assertValueEqual(inventory.slotType(HiredJobInventory.HOTBAR_START + 1),
                HiredJobInventorySlotType.SUPPLY, "tool overflow slot type");
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_PICKAXE), "gear slots should stay synced to villager equipment");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void endedContractOverflowBlocksForeignHirerUntilClaimExpires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerPlayer originalHirer = fakePlayer(level, "VrOverflowOwner");
        ServerPlayer otherHirer = fakePlayer(level, "VrOverflowOther");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        HiredVillagerContractService.startHireContract(level, villager, originalHirer, 1, 8);
        UUID originalContractId = HiredVillagerContractService.currentContractId(villager).orElseThrow();
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.DIRT, 5)).isEmpty(), "contract supplies should fit");
        helper.assertValueEqual(
                HiredJobInventory.jobItemContractId(inventory.findSupply(stack -> stack.is(Items.DIRT))).orElse(null),
                originalContractId,
                "job supplies should be stamped with the active contract");

        HiredVillagerContractService.endHireContract(level, villager, originalHirer);

        helper.assertTrue(
                HiredVillagerContractService.hasBlockingJobInventoryOverflow(level, villager),
                "leftover removable contract items should create a claim");
        helper.assertFalse(
                HiredVillagerContractService.hasForeignJobInventoryOverflow(level, villager, originalHirer),
                "the previous hirer's own leftovers should not be foreign");
        helper.assertTrue(
                HiredVillagerContractService.hasForeignJobInventoryOverflow(level, villager, otherHirer),
                "other players should be blocked by previous-contract leftovers");
        helper.assertTrue(
                HiredVillagerContractService.canAccessJobInventory(level, villager, originalHirer),
                "the previous hirer should be able to reclaim overflow during the claim window");
        helper.assertFalse(
                HiredVillagerContractService.canAccessJobInventory(level, villager, otherHirer),
                "foreign players should not be able to claim overflow during the claim window");

        CompoundTag claim = villager.getPersistentData().getCompound("VillagerRetaliationJobInventoryOverflowClaim");
        claim.putLong("ExpiresGameTime", level.getGameTime());
        helper.assertFalse(
                HiredVillagerContractService.hasBlockingJobInventoryOverflow(level, villager),
                "expired claims should stop blocking new contracts");

        HiredVillagerContractService.startHireContract(level, villager, otherHirer, 1, 8);
        UUID newContractId = HiredVillagerContractService.currentContractId(villager).orElseThrow();
        HiredJobInventory refreshedInventory = HiredJobInventory.getJobInventory(villager);
        helper.assertValueEqual(
                HiredJobInventory.jobItemContractId(refreshedInventory.findSupply(stack -> stack.is(Items.DIRT))).orElse(null),
                newContractId,
                "expired overflow should be claimed by the next contract that takes it on");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void contractHandoffClearsBrewingOrders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerPlayer hirer = fakePlayer(level, "VrBrewingOrderOwner");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        villager.getPersistentData()
                .getCompound("VillagerRetaliationHireContract")
                .putString("Role", HiredVillagerRole.BREWING.serializedName());
        CompoundTag state = new CompoundTag();
        villager.getPersistentData().put(WORK_STATE_TAG, state);
        BrewingWorker.setOrder(
                state,
                ResourceLocation.fromNamespaceAndPath("minecraft", "potion"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "water"),
                3,
                false,
                HiredVillagerContractService.currentContractId(villager).orElse(null));
        helper.assertTrue(BrewingWorker.hasOrder(state), "brewing order should be present before contract end");

        HiredVillagerContractService.endHireContract(level, villager, hirer);

        helper.assertFalse(BrewingWorker.hasOrder(state), "contract end should clear brewing orders from the old contract");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void autoPaymentDoesNotRenewWhileWaitingForOfflineHirer(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrOfflineRenewal");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        BlockPos paymentRel = new BlockPos(2, 2, 1);
        BlockPos payment = helper.absolutePos(paymentRel);
        setBlock(helper, paymentRel, VillagerRetaliationBlocks.PAYMENT_BOX.get().defaultBlockState());
        container(level, payment).setItem(0, new ItemStack(Items.EMERALD, 16));
        AssignedStorageService.removeAssignedContainer(level, payment);
        AssignedStorageService.AssignSummary paymentAssignment = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), payment)),
                AssignedStorageService.PAYMENT_PURPOSE);
        helper.assertValueEqual(paymentAssignment.assigned(), 1, "payment box assignment");

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        HiredVillagerContractService.setAutoPaymentEnabled(villager, true);
        CompoundTag contract = villager.getPersistentData().getCompound("VillagerRetaliationHireContract");
        long expiredAt = level.getGameTime();
        contract.putLong("EndGameTime", expiredAt);
        CompoundTag workState = new CompoundTag();
        workState.putString("Status", HiredVillagerWorkService.WAITING_FOR_HIRER_STATUS);
        villager.getPersistentData().put(WORK_STATE_TAG, workState);

        HiredVillagerContractService.onVillagerTickPost(villager);

        helper.assertValueEqual(countItem(container(level, payment), Items.EMERALD), 16, "offline renewal should not charge");
        helper.assertValueEqual(contract.getLong("EndGameTime"), expiredAt, "offline renewal should not extend time");
        helper.assertValueEqual(contract.getString("Status"), "active", "contract should stay active until normal expiry processing");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void contractExtensionClampsToThirtyRemainingDays(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrContractHorizon");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        HiredVillagerContractService.startHireContract(level, villager, hirer, 25, 25);

        int extensionDays = HiredVillagerContractService.getAvailableExtensionDays(level, villager, hirer, 10);
        helper.assertValueEqual(extensionDays, 5, "extension should only add days up to the 30-day horizon");

        int extensionCost = HiredVillagerContractService.getExtensionCost(level, villager, hirer, 10);
        helper.assertTrue(
                HiredVillagerContractService.extendHireContract(level, villager, hirer, 10, extensionCost),
                "extension within horizon should succeed");
        helper.assertValueEqual(
                HiredVillagerContractService.getRemainingHireDays(level, villager),
                30,
                "remaining contract days should be capped at 30");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void autoPaymentRenewalUsesCurrentDailyRate(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        BlockPos paymentRel = new BlockPos(2, 2, 1);
        BlockPos payment = helper.absolutePos(paymentRel);
        setBlock(helper, paymentRel, VillagerRetaliationBlocks.PAYMENT_BOX.get().defaultBlockState());
        container(level, payment).setItem(0, new ItemStack(Items.EMERALD, 64));
        AssignedStorageService.removeAssignedContainer(level, payment);
        AssignedStorageService.AssignSummary paymentAssignment = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), payment)),
                AssignedStorageService.PAYMENT_PURPOSE);
        helper.assertValueEqual(paymentAssignment.assigned(), 1, "payment box assignment");

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 1);
        HiredVillagerContractService.setAutoPaymentEnabled(villager, true);
        int currentDailyCost = HiredVillagerContractService.getDailyCost(level, villager, hirer);
        CompoundTag contract = villager.getPersistentData().getCompound("VillagerRetaliationHireContract");
        long expiredAt = level.getGameTime();
        contract.putLong("EndGameTime", expiredAt);

        HiredVillagerContractService.expireHireContractIfNeeded(level, villager);

        helper.assertValueEqual(
                countItem(container(level, payment), Items.EMERALD),
                64 - currentDailyCost,
                "auto renewal should consume the current daily rate");
        helper.assertValueEqual(contract.getInt("DailyCost"), currentDailyCost, "renewed daily cost should track current rate");
        helper.assertValueEqual(contract.getLong("EndGameTime"), expiredAt + 24000L, "auto renewal should add one day");
        helper.assertValueEqual(contract.getString("Status"), "active", "contract should return to active after renewal");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void paymentStorageAssignmentRejectsOtherDimensions(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "nether level should exist for cross-dimension storage regression");
        ServerPlayer hirer = fakePlayer(level, "VrCrossDimPayment");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        BlockPos netherPayment = new BlockPos(0, 80, 0);
        nether.getChunk(netherPayment);
        nether.setBlock(netherPayment, VillagerRetaliationBlocks.PAYMENT_BOX.get().defaultBlockState(), Block.UPDATE_ALL);
        AssignedStorageService.removeAssignedContainer(nether, netherPayment);

        AssignedStorageService.AssignSummary summary = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(nether.dimension(), netherPayment)),
                AssignedStorageService.PAYMENT_PURPOSE);

        helper.assertValueEqual(summary.assigned(), 0, "cross-dimension payment assignment should not be accepted");
        helper.assertValueEqual(summary.invalid(), 1, "cross-dimension payment assignment should be reported invalid");
        helper.assertTrue(
                AssignedStorageService.assignedPaymentStorage(level, villager).isEmpty(),
                "cross-dimension payment assignment should not persist");

        AssignedStorageService.removeAssignedContainer(nether, netherPayment);
        nether.setBlock(netherPayment, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignedStorageSupportsMultiplePurposesPerVillagerContainer(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrMixedStorage");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager otherVillager = spawnVillager(helper, new BlockPos(2, 2, 4));
        BlockPos chestRel = new BlockPos(4, 2, 2);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);

        AssignedStorageService.StoragePosition storage = new AssignedStorageService.StoragePosition(level.dimension(), chest);
        AssignedStorageService.AssignSummary globalSummary = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(storage),
                AssignedStorageService.GENERAL_PURPOSE);
        AssignedStorageService.AssignSummary toolSummary = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(storage),
                AssignedStorageService.SUPPLY_PURPOSE);
        AssignedStorageService.AssignSummary outputSummary = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(storage),
                AssignedStorageService.OUTPUT_PURPOSE);

        helper.assertValueEqual(globalSummary.assigned(), 1, "global storage assignment");
        helper.assertValueEqual(toolSummary.assigned(), 1, "tool storage assignment");
        helper.assertValueEqual(outputSummary.assigned(), 1, "output storage assignment");
        helper.assertValueEqual(AssignedStorageService.assignedStorage(level, villager).size(), 3, "mixed storage assignment count");
        helper.assertTrue(
                AssignedStorageService.assignedStorage(level, villager).stream()
                        .anyMatch(record -> AssignedStorageService.GENERAL_PURPOSE.equals(record.purpose())),
                "global storage purpose should persist");
        helper.assertTrue(
                AssignedStorageService.assignedStorage(level, villager).stream()
                        .anyMatch(record -> AssignedStorageService.SUPPLY_PURPOSE.equals(record.purpose())),
                "tool storage purpose should persist");
        helper.assertTrue(
                AssignedStorageService.assignedStorage(level, villager).stream()
                        .anyMatch(record -> AssignedStorageService.OUTPUT_PURPOSE.equals(record.purpose())),
                "output storage purpose should persist");

        AssignedStorageService.AssignSummary duplicateTool = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(storage),
                AssignedStorageService.SUPPLY_PURPOSE);
        helper.assertValueEqual(duplicateTool.alreadyAssigned(), 1, "duplicate tool storage assignment should not add another record");

        AssignedStorageService.AssignSummary shared = AssignedStorageService.assign(
                hirer,
                otherVillager,
                List.of(storage),
                AssignedStorageService.SUPPLY_PURPOSE);
        helper.assertValueEqual(shared.assigned(), 1, "same physical storage should be shareable by another villager");
        helper.assertValueEqual(
                AssignedStorageService.assignedStorage(level, otherVillager).size(),
                1,
                "shared storage assignment should persist independently");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        helper.assertValueEqual(
                AssignedStorageService.assignedStorage(level, otherVillager).size(),
                1,
                "removing one villager's assignments should preserve another villager's shared assignment");
        AssignedStorageService.removeAllAssignedStorage(level, otherVillager);
        villager.discard();
        otherVillager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void adultJobAvailabilityOnlyKeepsExplicitRoleRestrictions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildFloor(helper, 0, 6, 0, 6, 1);
        Villager farmer = spawnVillager(helper, new BlockPos(2, 2, 2));
        farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
        Villager mason = spawnVillager(helper, new BlockPos(4, 2, 2));
        mason.setVillagerData(mason.getVillagerData().setProfession(VillagerProfession.MASON));

        VillagerSkillSet lowSkills = VillagerSkillSet.filled(1);

        helper.assertTrue(
                HiredVillagerRoles.isSkillUnlocked("none", false, lowSkills, HiredVillagerRole.MINING),
                "low aptitude must not prevent an adult from taking Mining work");
        helper.assertTrue(
                HiredVillagerRoles.isSkillUnlocked("weaponsmith", false, lowSkills, HiredVillagerRole.MINING),
                "profession must not gate an adult's noncanonical jobs");
        helper.assertTrue(
                HiredVillagerRoles.availableContractRoles(level, farmer).contains(HiredVillagerRole.COMBAT),
                "every ordinary role should be available to an adult villager");
        helper.assertTrue(
                HiredVillagerRoles.isSkillUnlocked("none", false, lowSkills, HiredVillagerRole.COURIER),
                "Courier should be universally available to adults");
        helper.assertFalse(
                HiredVillagerRoles.isSkillUnlocked("none", true, VillagerSkillSet.filled(100), HiredVillagerRole.COURIER),
                "babies must remain ineligible even for universal roles");
        helper.assertFalse(
                HiredVillagerRoles.isSkillUnlocked("none", false, VillagerSkillSet.filled(100), HiredVillagerRole.NITWIT),
                "non-nitwits must not qualify for Nitwit work through skills");
        helper.assertTrue(
                HiredVillagerRoles.isSkillUnlocked("nitwit", false, lowSkills, HiredVillagerRole.NITWIT),
                "nitwits should automatically qualify for Nitwit work");
        helper.assertTrue(
                HiredVillagerRoles.canOfferBuilderService(level, mason),
                "an adult villager should be able to offer one-off Builder services");
        helper.assertFalse(
                HiredVillagerRoles.availableContractRoles(level, mason).contains(HiredVillagerRole.BUILDER),
                "Builder should remain excluded from ordinary contracts");

        farmer.discard();
        mason.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void jobCapabilityEndpointsAndTransferLimitsAreMonotonic(GameTestHelper helper) {
        helper.assertValueEqual(HiredVillagerRoles.aptitude(0, 0), 0, "zero aptitude endpoint");
        helper.assertValueEqual(HiredVillagerRoles.aptitude(50, 50), 50, "midpoint aptitude");
        helper.assertValueEqual(HiredVillagerRoles.aptitude(100, 100), 100, "maximum aptitude endpoint");
        helper.assertValueEqual(HiredVillagerRoles.skillWorkSpeedPercent(0), 50, "minimum work speed");
        helper.assertValueEqual(HiredVillagerRoles.skillWorkSpeedPercent(30), 75, "developing work speed");
        helper.assertValueEqual(HiredVillagerRoles.skillWorkSpeedPercent(60), 100, "standard work speed");
        helper.assertValueEqual(HiredVillagerRoles.skillWorkSpeedPercent(80), 113, "advanced work speed");
        helper.assertValueEqual(HiredVillagerRoles.skillWorkSpeedPercent(100), 125, "maximum work speed");
        helper.assertValueEqual(HiredVillagerRoles.blockWorkSpeedPercent(0), 85, "minimum block-work speed");
        helper.assertValueEqual(HiredVillagerRoles.blockWorkSpeedPercent(60), 100, "standard block-work speed");
        helper.assertValueEqual(HiredVillagerRoles.blockWorkSpeedPercent(100), 110, "maximum block-work speed");
        helper.assertValueEqual(HiredVillagerRoles.transferCapacityPercent(0), 50, "minimum transfer capacity");
        helper.assertValueEqual(HiredVillagerRoles.transferCapacityPercent(50), 92, "developing transfer capacity");
        helper.assertValueEqual(HiredVillagerRoles.transferCapacityPercent(60), 100, "standard transfer capacity");
        helper.assertValueEqual(HiredVillagerRoles.transferCapacityPercent(100), 150, "maximum transfer capacity");
        helper.assertValueEqual(HiredVillagerRoles.courierTransferLimit(0), 1, "courier zero aptitude capacity");
        helper.assertValueEqual(HiredVillagerRoles.courierTransferLimit(10), 2, "courier aptitude 10 capacity");
        helper.assertValueEqual(HiredVillagerRoles.courierTransferLimit(20), 4, "courier aptitude 20 capacity");
        helper.assertValueEqual(HiredVillagerRoles.courierTransferLimit(30), 8, "courier aptitude 30 capacity");
        helper.assertValueEqual(HiredVillagerRoles.courierTransferLimit(40), 16, "courier aptitude 40 capacity");
        helper.assertValueEqual(HiredVillagerRoles.courierTransferLimit(50), 32, "courier aptitude 50 capacity");
        helper.assertValueEqual(HiredVillagerRoles.courierTransferLimit(60), 64, "courier standard capacity");
        helper.assertValueEqual(HiredVillagerRoles.courierTransferLimit(79), 64, "courier standard tier upper boundary");
        helper.assertValueEqual(HiredVillagerRoles.courierTransferLimit(80), 96, "courier advanced capacity");
        helper.assertValueEqual(HiredVillagerRoles.courierTransferLimit(99), 96, "courier advanced tier upper boundary");
        helper.assertValueEqual(HiredVillagerRoles.courierTransferLimit(100), 128, "courier maximum capacity");
        helper.assertValueEqual(HiredVillagerRoles.transferLimit(16, 50), 8, "low-skill facility collection");
        helper.assertValueEqual(HiredVillagerRoles.transferLimit(16, 150), 24, "high-skill facility collection");
        helper.assertValueEqual(HiredVillagerRoles.baseTransferItems(HiredVillagerRole.CRAFTSMAN), 32,
                "Craftsman transfer display must match its material pull");
        helper.assertValueEqual(HiredVillagerRoles.roleActionSpeedPercent(HiredVillagerRole.MINING, 0), 85,
                "Mining should use the narrow block-work curve");
        helper.assertValueEqual(HiredVillagerRoles.roleActionSpeedPercent(HiredVillagerRole.COURIER, 100), 100,
                "Courier aptitude must not change action speed");
        helper.assertValueEqual(HiredVillagerRoles.roleCadencePercent(HiredVillagerRole.CRAFTSMAN, 0), 50,
                "Craftsman aptitude should control recipe crafting cadence");
        helper.assertValueEqual(HiredVillagerRoles.roleCadencePercent(HiredVillagerRole.BREWING, 100), 125,
                "Brewer aptitude should control preparation and stand-transfer cadence");
        helper.assertValueEqual(HiredVillagerRoles.roleCadencePercent(HiredVillagerRole.COOK, 100), 100,
                "Cook aptitude must affect capacity rather than cadence");
        helper.assertValueEqual(HiredVillagerRoles.roleCadencePercent(HiredVillagerRole.FARMING, 100), 125,
                "Farming should use the broad action-cadence curve");
        helper.assertValueEqual(HiredVillagerRoles.roleTransferCapacityPercent(HiredVillagerRole.BREWING, 0), 100,
                "Brewer material pickup should stay at its standard batch size");
        helper.assertValueEqual(HiredVillagerRoles.roleTransferCapacityPercent(HiredVillagerRole.CRAFTSMAN, 100), 100,
                "Craftsman material pickup should stay at its standard batch size");
        helper.assertValueEqual(HiredVillagerRoles.roleTransferCapacityPercent(HiredVillagerRole.COOK, 0), 50,
                "Cook aptitude should continue to control material capacity");
        helper.assertValueEqual(VillagerCombatSkillBehavior.meleeAttackSpeedPercent(0), 91,
                "minimum Guarding attack speed");
        helper.assertValueEqual(VillagerCombatSkillBehavior.meleeAttackSpeedPercent(60), 100,
                "standard Guarding attack speed");
        helper.assertValueEqual(VillagerCombatSkillBehavior.meleeDamagePercent(100), 108,
                "maximum Guarding damage modifier");
        helper.assertValueEqual(VillagerCombatSkillBehavior.rangedAttackSpeedPercent(100), 110,
                "maximum Archery attack speed");
        helper.assertValueEqual(VillagerCombatSkillBehavior.rangedSpreadPercent(0), 135,
                "minimum Archery projectile spread");
        helper.assertValueEqual(VillagerCombatSkillBehavior.rangedSpreadPercent(60), 100,
                "standard Archery projectile spread");
        helper.assertValueEqual(VillagerCombatSkillBehavior.rangedSpreadPercent(100), 75,
                "maximum Archery projectile spread");
        helper.assertFalse(VillagerCombatSkillBehavior.canUseAxeBreaker(59),
                "Guarding below standard should not unlock axe shield-breaking");
        helper.assertTrue(VillagerCombatSkillBehavior.canUseAxeBreaker(60),
                "standard Guarding should unlock axe shield-breaking");
        helper.assertValueEqual(HiredVillagerRoles.scaledDurationTicks(400, 50), 800, "low-skill fishing wait");
        helper.assertValueEqual(HiredVillagerRoles.scaledDurationTicks(400, 100), 400, "baseline fishing wait");
        helper.assertValueEqual(HiredVillagerRoles.scaledDurationTicks(400, 125), 320, "high-skill fishing wait");
        helper.assertValueEqual(HiredVillagerRoles.scaledDurationTicks(30, 85), 35,
                "low-skill tool-assisted block completion");
        helper.assertValueEqual(HiredVillagerRoles.scaledDurationTicks(30, 110), 27,
                "high-skill tool-assisted block completion");
        helper.assertValueEqual(
                HiredVillagerWorkService.calculateEfficiencyPercent(100, 50, 0, false, 25, 175),
                50,
                "low aptitude efficiency");
        helper.assertValueEqual(
                HiredVillagerWorkService.calculateEfficiencyPercent(100, 125, 0, false, 25, 175),
                125,
                "high aptitude efficiency");
        helper.assertValueEqual(
                HiredVillagerWorkService.calculateEfficiencyPercent(100, 50, 0, true, 25, 175),
                30,
                "missing-tool penalty after skill scaling");
        helper.assertValueEqual(
                HiredVillagerWorkService.calculateEfficiencyPercent(300, 125, 8, false, 25, 175),
                175,
                "maximum configured efficiency clamp");
        helper.assertValueEqual(
                HiredVillagerWorkService.calculateEfficiencyPercent(1, 50, -15, true, 25, 175),
                25,
                "minimum configured efficiency clamp");
        helper.assertValueEqual(HiredVillagerWorkService.effectiveWorkTickInterval(40, 50), 80, "low-skill work cadence");
        helper.assertValueEqual(HiredVillagerWorkService.effectiveWorkTickInterval(40, 100), 40, "baseline work cadence");
        helper.assertValueEqual(HiredVillagerWorkService.effectiveWorkTickInterval(40, 125), 32, "high-skill work cadence");
        helper.assertValueEqual(HiredVillagerWorkService.completedTaskCooldownTicks(50), 160, "low-skill completion cooldown");
        helper.assertValueEqual(HiredVillagerWorkService.completedTaskCooldownTicks(100), 80, "baseline completion cooldown");
        helper.assertValueEqual(HiredVillagerWorkService.completedTaskCooldownTicks(125), 64, "high-skill completion cooldown");
        helper.assertValueEqual(
                HiredVillagerWorkService.maxWorkRadiusForScore(HiredVillagerRole.MINING, 0, 16, 32),
                16,
                "low aptitude work radius");
        helper.assertValueEqual(
                HiredVillagerWorkService.maxWorkRadiusForScore(HiredVillagerRole.MINING, 50, 16, 32),
                16,
                "baseline aptitude work radius");
        helper.assertValueEqual(
                HiredVillagerWorkService.maxWorkRadiusForScore(HiredVillagerRole.MINING, 100, 16, 32),
                32,
                "maximum aptitude work radius");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void autoPaymentRenewsAfterHirerReturnsToFundedPaymentBox(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        BlockPos paymentRel = new BlockPos(2, 2, 1);
        BlockPos payment = helper.absolutePos(paymentRel);
        setBlock(helper, paymentRel, VillagerRetaliationBlocks.PAYMENT_BOX.get().defaultBlockState());
        container(level, payment).setItem(0, new ItemStack(Items.EMERALD, 64));
        AssignedStorageService.removeAssignedContainer(level, payment);

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 1);
        AssignedStorageService.AssignSummary paymentAssignment = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), payment)),
                AssignedStorageService.PAYMENT_PURPOSE);
        helper.assertValueEqual(paymentAssignment.assigned(), 1, "payment box assignment");
        helper.assertTrue(
                HiredVillagerContractService.isAutoPaymentEnabled(level, villager),
                "assigning recurring payment storage should enable automatic renewal");
        int currentDailyCost = HiredVillagerContractService.getDailyCost(level, villager, hirer);
        CompoundTag contract = villager.getPersistentData().getCompound("VillagerRetaliationHireContract");
        long expiredAt = level.getGameTime();
        contract.putLong("EndGameTime", expiredAt);
        CompoundTag workState = new CompoundTag();
        workState.putString("Status", HiredVillagerWorkService.WAITING_FOR_HIRER_STATUS);
        villager.getPersistentData().put(WORK_STATE_TAG, workState);

        HiredVillagerContractService.expireHireContractIfNeeded(level, villager);

        helper.assertValueEqual(
                countItem(container(level, payment), Items.EMERALD),
                64 - currentDailyCost,
                "returning hirer's funded payment box should renew the contract");
        helper.assertValueEqual(contract.getString("Status"), "active", "renewed contract should remain active");
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(workState, level.getGameTime()).taskState(),
                HiredWorkerTaskState.IDLE,
                "villager should resume work after automatic renewal");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void softPaymentBoxLoadsAreBoundedAndRequireLivingVillager(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 1);

        AssignedStorageSavedData data = AssignedStorageSavedData.get(level);
        for (int index = 0; index < 6; index++) {
            BlockPos paymentPos = helper.absolutePos(new BlockPos(160_000 + index * 16, 2, 160_000));
            data.assign(new AssignedStorageSavedData.AssignedContainerRecord(
                    level.dimension(),
                    paymentPos,
                    villager.getUUID(),
                    hirer.getUUID(),
                    AssignedStorageService.PAYMENT_PURPOSE,
                    index,
                    "unloaded"));
        }

        int requested = PaymentBoxChunkLoadingService.requestLoads(level, villager);
        helper.assertValueEqual(requested, 4, "one renewal should request at most four payment chunks");
        PaymentBoxChunkLoadingService.releaseLoads(level, villager);

        villager.discard();
        helper.assertValueEqual(
                PaymentBoxChunkLoadingService.requestLoads(level, villager),
                0,
                "removed or dead villagers should not load payment chunks");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void staleStorageValidationPreservesSharedAssignments(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrSharedStorageValidation");
        Villager staleVillager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager paymentVillager = spawnVillager(helper, new BlockPos(2, 2, 4));
        BlockPos storageRel = new BlockPos(4, 2, 2);
        BlockPos storage = helper.absolutePos(storageRel);
        setBlock(helper, storageRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, storage);

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                staleVillager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), storage)),
                AssignedStorageService.GENERAL_PURPOSE).assigned(), 1, "initial general storage assignment");

        setBlock(helper, storageRel, VillagerRetaliationBlocks.PAYMENT_BOX.get().defaultBlockState());
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                paymentVillager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), storage)),
                AssignedStorageService.PAYMENT_PURPOSE).assigned(), 1, "shared payment storage assignment");

        AssignedStorageService.countItemsInNonPaymentStorage(staleVillager, ignored -> true);

        helper.assertTrue(
                AssignedStorageService.assignedStorage(level, staleVillager).isEmpty(),
                "validation should remove the stale general assignment");
        helper.assertValueEqual(
                AssignedStorageService.assignedPaymentStorage(level, paymentVillager).size(),
                1,
                "validation should preserve another villager's valid assignment at the same block");
        helper.assertTrue(
                AssignedStorageService.hasLoadedAssignedPaymentStorage(level, paymentVillager),
                "the preserved payment assignment should remain usable");

        AssignedStorageService.removeAssignedContainer(level, storage);
        staleVillager.discard();
        paymentVillager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignedStoragePriorityStaysMonotonicAfterRemoval(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrStoragePriority");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        List<BlockPos> storagePositions = List.of(
                new BlockPos(3, 2, 2),
                new BlockPos(5, 2, 2),
                new BlockPos(3, 2, 4));
        for (BlockPos storageRel : storagePositions) {
            setBlock(helper, storageRel, Blocks.CHEST.defaultBlockState());
            AssignedStorageService.removeAssignedContainer(level, helper.absolutePos(storageRel));
        }
        BlockPos first = helper.absolutePos(storagePositions.get(0));
        BlockPos second = helper.absolutePos(storagePositions.get(1));
        BlockPos third = helper.absolutePos(storagePositions.get(2));

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), first),
                        new AssignedStorageService.StoragePosition(level.dimension(), second)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 2, "initial output assignments");

        AssignedStorageSavedData data = AssignedStorageSavedData.get(level);
        var firstRecord = data.assignedTo(villager.getUUID(), AssignedStorageService.OUTPUT_PURPOSE).stream()
                .filter(record -> record.pos().equals(first))
                .findFirst()
                .orElseThrow();
        helper.assertTrue(data.removeAssignment(firstRecord), "first output assignment should be removable");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), third)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "replacement output assignment");

        var remaining = data.assignedTo(villager.getUUID(), AssignedStorageService.OUTPUT_PURPOSE);
        helper.assertValueEqual(
                remaining.stream().filter(record -> record.pos().equals(second)).findFirst().orElseThrow().priority(),
                1,
                "surviving assignment priority");
        helper.assertValueEqual(
                remaining.stream().filter(record -> record.pos().equals(third)).findFirst().orElseThrow().priority(),
                2,
                "new assignment should follow the highest existing priority");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignedStoragePersistsPurposeOwnershipAndOutputPriority(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerStorage");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager otherVillager = spawnVillager(helper, new BlockPos(2, 2, 4));
        BlockPos inputRel = new BlockPos(4, 2, 2);
        BlockPos outputRel = new BlockPos(5, 2, 2);
        BlockPos input = helper.absolutePos(inputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, inputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, input);
        AssignedStorageService.removeAssignedContainer(level, output);

        AssignedStorageService.AssignSummary inputSummary = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), input)),
                AssignedStorageService.SUPPLY_PURPOSE);
        AssignedStorageService.AssignSummary outputSummary = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE);
        helper.assertValueEqual(inputSummary.assigned(), 1, "input storage assignment");
        helper.assertValueEqual(outputSummary.assigned(), 1, "output storage assignment");

        AssignedStorageService.AssignSummary shared = AssignedStorageService.assign(
                hirer,
                otherVillager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE);
        helper.assertValueEqual(shared.assigned(), 1, "output storage should be shareable by another villager");

        container(level, output).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.insertOutput(new ItemStack(Items.COBBLESTONE, 7));
        helper.assertTrue(inventory.depositOutputToAssignedStorage(), "output should deposit to assigned output storage");
        helper.assertValueEqual(countItem(container(level, output), Items.COBBLESTONE), 12, "output chest item count");
        helper.assertTrue(container(level, output).getItem(1).isEmpty(),
                "returned output should merge into an identical normal stack");
        helper.assertFalse(HiredJobInventory.isJobItem(container(level, output).getItem(0)),
                "completed-job output should lose its job metadata in storage");
        helper.assertValueEqual(countItem(container(level, input), Items.COBBLESTONE), 0, "input chest should not receive outputs first");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        AssignedStorageService.removeAllAssignedStorage(level, otherVillager);
        villager.discard();
        otherVillager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void expiredContractCleansReturnedJobInventoryItems(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrExpiredReturnTag");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        BlockPos chestRel = new BlockPos(2, 2, 3);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);
        container(level, chest).setItem(0, new ItemStack(Items.DIRT, 3));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        helper.assertTrue(inventory.insertSupply(new ItemStack(Items.DIRT, 5)).isEmpty(),
                "contract supplies should fit in job inventory");
        villager.getPersistentData().getCompound("VillagerRetaliationHireContract")
                .putLong("EndGameTime", level.getGameTime());

        HiredVillagerContractService.expireHireContractIfNeeded(level, villager);

        helper.assertTrue(inventory.findSupply(stack -> stack.is(Items.DIRT)).isEmpty(),
                "expired contract should return removable supplies to assigned storage");
        helper.assertValueEqual(container(level, chest).getItem(0).getCount(), 8,
                "expired-contract supplies should merge into an identical normal stack");
        helper.assertTrue(container(level, chest).getItem(1).isEmpty(),
                "cleaned expired-contract supplies should not occupy a separate stack");
        helper.assertFalse(HiredJobInventory.isJobItem(container(level, chest).getItem(0)),
                "expired-contract supplies should lose their job metadata in storage");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignedOutputStorageDepositsFromAdjacentApproachWithoutNudge(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerStorageNudge");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos chestRel = new BlockPos(3, 2, 2);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        BlockPos approach = helper.absolutePos(new BlockPos(3, 2, 3));
        villager.moveTo(approach.getX() + 0.18D, approach.getY(), approach.getZ() + 0.82D, 0.0F, 0.0F);
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.insertOutput(new ItemStack(Items.COBBLESTONE, 11));

        helper.assertTrue(
                AssignedStorageService.canInteractWithAssignedStorage(villager, chest),
                "off-center villager should still be able to interact with adjacent assigned storage");
        helper.assertTrue(
                inventory.depositOutputToAssignedStorageAt(chest),
                "off-center adjacent villager should deposit without needing a nudge");
        helper.assertValueEqual(countItem(container(level, chest), Items.COBBLESTONE), 11, "adjacent output deposit count");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void miningWorkerMinesOnlyValidOreStoresDropsAndQueuesAdjacentTargets(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerMining");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredOreBlockTracker.clearRuntimeState();
        HiredRoleWorkerRegistry.clearRuntimeState();

        BlockPos firstOreRel = new BlockPos(3, 2, 2);
        BlockPos secondOreRel = new BlockPos(4, 2, 2);
        BlockPos invalidRel = new BlockPos(3, 2, 3);
        setBlock(helper, firstOreRel, Blocks.COAL_ORE.defaultBlockState());
        setBlock(helper, secondOreRel, Blocks.COAL_ORE.defaultBlockState());
        setBlock(helper, invalidRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(6, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 80, () ->
                level.getBlockState(helper.absolutePos(firstOreRel)).isAir());
        helper.assertTrue(level.getBlockState(helper.absolutePos(firstOreRel)).isAir(), "miner should break the first valid exposed ore");
        boolean secondOreRemaining = !level.getBlockState(helper.absolutePos(secondOreRel)).isAir();
        helper.assertTrue(secondOreRemaining || countInventoryItem(context.inventory(), Items.COAL) >= 2,
                "adjacent ore should stay queued or be mined as the next valid vein block");
        helper.assertTrue(level.getBlockState(helper.absolutePos(invalidRel)).is(Blocks.STONE), "miner should not break unrelated stone in exposed-ore mode");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COAL)), "mined coal drops should be stored as output");

        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertTrue(
                snapshot.taskState() == HiredWorkerTaskState.MOVING_TO_TARGET
                        || snapshot.taskState() == HiredWorkerTaskState.FINDING_CHAIN_TARGET
                        || snapshot.taskState() == HiredWorkerTaskState.WORKING
                        || snapshot.taskState() == HiredWorkerTaskState.IDLE,
                "miner should recover into a valid follow-up state");

        HiredOreBlockTracker.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningWorkerKeepsSameOreProgressWhileRepositioning(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerMiningReposition");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredOreBlockTracker.clearRuntimeState();

        BlockPos oreRel = new BlockPos(3, 2, 2);
        setBlock(helper, oreRel, Blocks.DEEPSLATE_COAL_ORE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(
                helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(7, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.STONE_PICKAXE));
        MiningWorker worker = new MiningWorker();

        worker.tick(level, villager, hirer, context);
        int startedProgress = context.progressTicks();
        helper.assertTrue(startedProgress > 0, "miner should begin breaking the exposed ore");

        BlockPos repositioned = helper.absolutePos(new BlockPos(7, 2, 4));
        villager.moveTo(repositioned.getX() + 0.5D, repositioned.getY(), repositioned.getZ() + 0.5D, 0.0F, 0.0F);
        worker.tick(level, villager, hirer, context);

        helper.assertValueEqual(
                context.progressTicks(),
                startedProgress,
                "temporary same-target repositioning must not restart ore break progress");
        helper.assertTrue(
                level.getBlockState(helper.absolutePos(oreRel)).is(Blocks.DEEPSLATE_COAL_ORE),
                "repositioning must not complete the block early");

        HiredOreBlockTracker.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void miningOreTrackerIndexesLoadedChunksAndTracksRecentExposure(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        BlockPos oreRel = new BlockPos(3, 2, 2);
        BlockPos ore = helper.absolutePos(oreRel);
        BlockPos cornerOreRel = new BlockPos(6, 5, 5);
        BlockPos cornerOre = helper.absolutePos(cornerOreRel);
        setBlock(helper, oreRel, Blocks.COAL_ORE.defaultBlockState());
        setBlock(helper, cornerOreRel, Blocks.IRON_ORE.defaultBlockState());
        HiredOreBlockTracker.clearRuntimeState();

        helper.assertTrue(
                HiredOreBlockTracker.nearbyOreBlocks(level, ore, 4, 4).contains(ore),
                "loaded-chunk ore index should discover an existing ore block");

        HiredOreBlockTracker.onBlockBroken(level, ore.west());
        helper.assertTrue(
                HiredOreBlockTracker.recentlyExposedOreBlocks(level, ore, 4, 4).contains(ore),
                "breaking an adjacent block should record the newly exposed ore");
        helper.assertFalse(
                HiredOreBlockTracker.recentlyExposedOreBlocks(level, ore.east(6), 1, 4).contains(ore),
                "recent exposure queries should remain bounded to their requested radius");

        helper.assertTrue(
                HiredOreBlockTracker.nearbyOreBlocks(level, ore, 3, 3).contains(cornerOre),
                "ore search should include vertical corners of its rectangular work bounds");

        level.setBlock(ore, Blocks.AIR.defaultBlockState(), 3);
        helper.assertFalse(
                HiredOreBlockTracker.nearbyOreBlocks(level, ore, 4, 4).contains(ore),
                "ore index queries should evict blocks that no longer contain ore");

        HiredOreBlockTracker.clearRuntimeState();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100, batch = "assigned_output_storage_fallback")
    public static void workerSkipsFullOutputStorageAndContinuesToAvailableContainer(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerOutputFallback");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos fullChestRel = new BlockPos(3, 2, 2);
        BlockPos availableChestRel = new BlockPos(5, 2, 3);
        BlockPos fullChest = helper.absolutePos(fullChestRel);
        BlockPos availableChest = helper.absolutePos(availableChestRel);
        setBlock(helper, fullChestRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, availableChestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, fullChest);
        AssignedStorageService.removeAssignedContainer(level, availableChest);
        AssignedStorageService.clearRuntimeState();
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), fullChest),
                        new AssignedStorageService.StoragePosition(level.dimension(), availableChest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        Container fullContainer = container(level, fullChest);
        for (int slot = 0; slot < fullContainer.getContainerSize(); slot++) {
            fullContainer.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        fullContainer.setChanged();

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(6, 4, 5), true);
        context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 12));
        OutputDepositProbe worker = new OutputDepositProbe();

        AbstractBlockWorker.DepositResult firstResult = worker.deposit(level, context, villager);
        helper.assertValueEqual(firstResult, AbstractBlockWorker.DepositResult.MOVING,
                "a full nearest chest should redirect the worker instead of pausing all storage");
        HiredWorkerBrain.Snapshot redirected = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(redirected.taskState(), HiredWorkerTaskState.MOVING_TO_STORAGE,
                "worker should remain in an active storage movement state");
        helper.assertValueEqual(redirected.storageTargetPos(), availableChest,
                "worker should target the next available assigned output chest");

        AbstractBlockWorker.DepositResult secondResult = worker.deposit(level, context, villager);
        helper.assertValueEqual(secondResult, AbstractBlockWorker.DepositResult.DEPOSITED,
                "worker should deposit into the fallback output chest");
        helper.assertValueEqual(countItem(container(level, availableChest), Items.COBBLESTONE), 12,
                "fallback output chest should receive the worker's items");
        helper.assertValueEqual(countItem(fullContainer, Items.COBBLESTONE), 0,
                "full output chest should remain unchanged");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        AssignedStorageService.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 420)
    public static void miningWorkerDepositsAndReturnsToExposedOreWorkArea(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerExposedDeposit");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredOreBlockTracker.clearRuntimeState();
        HiredRoleWorkerRegistry.clearRuntimeState();

        BlockPos firstOreRel = new BlockPos(3, 2, 2);
        BlockPos secondOreRel = new BlockPos(4, 2, 2);
        BlockPos chestRel = new BlockPos(7, 2, 2);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, firstOreRel, Blocks.COAL_ORE.defaultBlockState());
        setBlock(helper, secondOreRel, Blocks.COAL_ORE.defaultBlockState());
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        for (int slot = 6; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            context.inventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        context.inventory().setItem(18, new ItemStack(Items.COAL, 63));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 360, () ->
                level.getBlockState(helper.absolutePos(firstOreRel)).isAir()
                        && level.getBlockState(helper.absolutePos(secondOreRel)).isAir()
                        && countItem(container(level, chest), Items.COAL) > 0);

        helper.assertTrue(level.getBlockState(helper.absolutePos(firstOreRel)).isAir(),
                "exposed miner should break the first ore before depositing");
        helper.assertTrue(level.getBlockState(helper.absolutePos(secondOreRel)).isAir(),
                "exposed miner should return from storage and break the queued ore");
        helper.assertTrue(countItem(container(level, chest), Items.COAL) > 0,
                "exposed miner should deposit filled output to the assigned chest");
        helper.assertTrue(context.isInsideWorkArea(villager.blockPosition()),
                "exposed miner should resume from inside the assigned work area");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        HiredOreBlockTracker.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerPausesSafelyForMissingToolsAndFullOutput(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerMiningPause");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        setBlock(helper, new BlockPos(3, 2, 2), Blocks.COAL_ORE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        WorkResult missingTool = new MiningWorker().tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot missingToolState = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(missingToolState.taskState(), HiredWorkerTaskState.PAUSED_MISSING_TOOL, "missing pickaxe state");
        helper.assertValueEqual(missingToolState.failureReason(), "missing_pickaxe", "missing pickaxe reason");
        helper.assertFalse(missingTool.completed(), "missing tool should not pretend work completed");

        for (int slot = 6; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            context.inventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.assertFalse(context.hasOutputSpace(), "filled output inventory should report no output space");
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        WorkResult fullOutput = new MiningWorker().tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot fullOutputState = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(fullOutputState.taskState(), HiredWorkerTaskState.PAUSED_FULL_INVENTORY, "full output state");
        helper.assertValueEqual(fullOutputState.failureReason(), "output_inventory_full", "full output reason");
        helper.assertValueEqual(fullOutput.status(), "interaction.work.mining.output_full_blocked", "full output status");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(3, 2, 2))).is(Blocks.COAL_ORE),
                "miner should not break ore when output cannot fit");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningWorkerRechecksOutputCapacityBeforeBreakingCachedTarget(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerMiningCapacityRecheck");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        BlockPos oreRel = new BlockPos(3, 2, 2);
        BlockPos ore = helper.absolutePos(oreRel);
        setBlock(helper, oreRel, Blocks.COAL_ORE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        worker.tick(level, villager, hirer, context);
        helper.assertValueEqual(state.getLong("MiningOutputCapacityCheckedTarget"), ore.asLong(),
                "miner should cache the capacity preflight for its active target");

        for (int slot = 6; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            context.inventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        context.inventory().setItem(6, new ItemStack(Items.COBBLESTONE, 63));
        helper.assertTrue(context.hasOutputSpace(),
                "a partially filled output stack should leave generic output space");
        helper.assertFalse(context.canStoreOutputs(List.of(new ItemStack(Items.COAL))),
                "the remaining output space should not accept the target drop");

        for (int tick = 0; tick < 80
                && HiredWorkerBrain.snapshot(state, level.getGameTime()).taskState()
                != HiredWorkerTaskState.PAUSED_FULL_INVENTORY; tick++) {
            worker.tick(level, villager, hirer, context);
            level.tickNonPassenger(villager);
        }

        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(snapshot.taskState(), HiredWorkerTaskState.PAUSED_FULL_INVENTORY,
                "completion should recheck capacity after a cached preflight");
        helper.assertTrue(level.getBlockState(ore).is(Blocks.COAL_ORE),
                "miner should leave the ore intact when its actual drop no longer fits");

        HiredOreBlockTracker.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void miningWorkerExcavatesTopLayerWithoutLadders(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 0);
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerExcavateTop");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, targetRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 80, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(), "top excavation layer should be mined without ladders");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COBBLESTONE)), "excavation drops should be stored as output");
        helper.assertFalse(
                HiredWorkerBrain.snapshot(state, level.getGameTime()).failureReason().equals("missing_ladders"),
                "top layer excavation should not pause for missing ladders");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerDoesNotMineUnsafeBlockUnderfoot(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerUnsafeOre");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        HiredOreBlockTracker.clearRuntimeState();
        HiredRoleWorkerRegistry.clearRuntimeState();
        BlockPos oreRel = new BlockPos(2, 1, 2);
        BlockPos lavaRel = new BlockPos(2, 0, 2);
        setBlock(helper, oreRel, Blocks.COAL_ORE.defaultBlockState());
        setBlock(helper, lavaRel, Blocks.LAVA.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, oreRel, new BlockPos(2, 2, 2), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        for (int tick = 0; tick < 40; tick++) {
            worker.maintain(level, villager, context);
            worker.tick(level, villager, hirer, context);
            level.tickNonPassenger(villager);
        }

        helper.assertTrue(level.getBlockState(helper.absolutePos(oreRel)).is(Blocks.COAL_ORE),
                "miner should not break an ore directly underfoot when lava is below");
        helper.assertTrue(level.getBlockState(helper.absolutePos(lavaRel)).is(Blocks.LAVA),
                "unsafe landing lava should remain covered by the ore");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.COAL), 0,
                "unsafe underfoot ore should not produce job output");

        villager.discard();
        HiredOreBlockTracker.clearRuntimeState();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningWorkerDoesNotFetchUnusedTorchSupplyBeforeMining(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 0);
        buildFloor(helper, 0, 8, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerNoTorchFetch");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);
        BlockPos chestRel = new BlockPos(7, 2, 3);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        container(level, chest).setItem(0, new ItemStack(Items.TORCH, 16));
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.GENERAL_PURPOSE);

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, targetRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        WorkResult result = worker.tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertFalse(snapshot.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE,
                "miner should not walk to storage only because optional torches are present; status=" + result.status());
        helper.assertTrue(snapshot.storageTargetPos() == null,
                "miner should not set a storage target for unused torch supply");

        runWorkerUntil(helper, worker, level, villager, hirer, context, 80, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());
        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should mine the available target before fetching optional torch supply");
        helper.assertValueEqual(countItem(container(level, chest), Items.TORCH), 16,
                "unused torch stack should remain in storage");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningWorkerPausesWhenLadderStorageCannotFitSupply(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLadderSupplyFull");
        Villager villager = spawnVillager(helper, new BlockPos(6, 2, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);
        BlockPos chestRel = new BlockPos(7, 2, 3);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        container(level, chest).setItem(0, new ItemStack(Items.LADDER, 8));
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.GENERAL_PURPOSE);

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, new BlockPos(3, 2, 3), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        for (int slot = 6; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            context.inventory().setItem(
                    slot,
                    HiredJobInventory.markAsProtectedVillagerProperty(
                            new ItemStack(Items.STICK, 64),
                            villager,
                            "full_support_inventory_fixture"));
        }

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(result.status(), "interaction.work.mining.support.inventory_full", "ladder supply full status");
        helper.assertValueEqual(snapshot.taskState(), HiredWorkerTaskState.PAUSED_FULL_INVENTORY, "ladder supply full task state");
        helper.assertValueEqual(snapshot.failureReason(), "support_inventory_full", "ladder supply full reason");
        helper.assertTrue(snapshot.storageTargetPos() == null,
                "miner should not keep a storage target when it cannot accept required ladder supplies");
        helper.assertValueEqual(countItem(container(level, chest), Items.LADDER), 8,
                "unaccepted ladders should remain in storage");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.LADDER), 0,
                "job inventory should not pretend it gathered ladders");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerDoesNotPlaceTopLayerLadderSupportWhenSupplied(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerTopNoLadder");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos ladderRel = new BlockPos(2, 1, 2);
        BlockPos backingRel = new BlockPos(2, 1, 1);
        setBlock(helper, ladderRel, Blocks.AIR.defaultBlockState());
        setBlock(helper, backingRel, Blocks.AIR.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 1, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.LADDER, 8));
        context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 4));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        helper.assertFalse(level.getBlockState(helper.absolutePos(ladderRel)).is(Blocks.LADDER),
                "top excavation layer should not place a ladder even when ladders are supplied");
        helper.assertTrue(level.getBlockState(helper.absolutePos(backingRel)).isAir(),
                "top excavation layer should not spend mined blocks on ladder backing");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.LADDER), 8, "top layer ladder supply count");
        helper.assertTrue(result.progressed(), "top layer miner should keep working instead of placing support");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerDoesNotPlaceTopLayerTorchSupportWhenSupplied(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerTopNoTorch");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos torchRel = new BlockPos(3, 1, 2);
        BlockPos backingRel = new BlockPos(3, 1, 1);
        setBlock(helper, torchRel, Blocks.AIR.defaultBlockState());
        setBlock(helper, backingRel, Blocks.AIR.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 1, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.TORCH, 8));
        context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 4));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        helper.assertFalse(level.getBlockState(helper.absolutePos(torchRel)).is(Blocks.WALL_TORCH),
                "top excavation layer should not place a wall torch support");
        helper.assertTrue(level.getBlockState(helper.absolutePos(backingRel)).isAir(),
                "top excavation layer should not spend mined blocks on torch backing");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.TORCH), 8, "top layer torch supply count");
        helper.assertTrue(result.progressed(), "top layer miner should keep working instead of placing support");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerRequiresLaddersBeforeExcavatingBelowTopLayer(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerExcavateDeep");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, new BlockPos(3, 2, 3), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(snapshot.taskState(), HiredWorkerTaskState.WAITING_FOR_MATERIALS, "missing ladder task state");
        helper.assertValueEqual(snapshot.failureReason(), "missing_ladders", "missing ladder reason");
        helper.assertValueEqual(result.status(), "interaction.work.mining.support.missing_ladders", "missing ladder status");
        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).is(Blocks.STONE),
                "miner should not dig deeper excavation layers without ladder access");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningWorkerPlacesLadderSupportBeforeExcavatingBelowTopLayer(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerDeepLadder");
        Villager villager = spawnVillager(helper, new BlockPos(3, 3, 3));
        BlockPos ladderRel = new BlockPos(2, 2, 2);
        setBlock(helper, ladderRel, Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(2, 1, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 1, 3), Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 2, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.LADDER, 8));
        context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 4));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        helper.assertValueEqual(result.status(), "interaction.work.mining.support.placed_ladder", "deep layer ladder support status");
        helper.assertTrue(level.getBlockState(helper.absolutePos(ladderRel)).is(Blocks.LADDER),
                "lower excavation layer should place ladder support before mining downward");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(2, 2, 1))).is(Blocks.COBBLESTONE),
                "lower excavation ladder may spend a mined block on needed backing");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.LADDER), 7, "deep layer ladder supply count");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningWorkerStopsLadderSupportAtSurfaceWhenWorkAreaExtendsAbove(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerSurfaceLadder");
        Villager villager = spawnVillager(helper, new BlockPos(3, 3, 3));
        BlockPos surfaceLadderRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = new BlockPos(3, 1, 3);
        for (int y = 2; y <= 5; y++) {
            setBlock(helper, new BlockPos(2, y, 2), Blocks.AIR.defaultBlockState());
            setBlock(helper, new BlockPos(2, y, 1), Blocks.AIR.defaultBlockState());
        }
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 5, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.LADDER, 8));
        context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 4));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        helper.assertValueEqual(result.status(), "interaction.work.mining.support.placed_ladder", "surface-clamped ladder status");
        helper.assertTrue(level.getBlockState(helper.absolutePos(surfaceLadderRel)).is(Blocks.LADDER),
                "ladder support should start at the first surface dismount");
        for (int y = 3; y <= 5; y++) {
            helper.assertFalse(level.getBlockState(helper.absolutePos(new BlockPos(2, y, 2))).is(Blocks.LADDER),
                    "ladder support should not be placed above the surface dismount at y=" + y);
        }
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.LADDER), 7, "surface-clamped ladder supply count");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void miningWorkerReplansLowerExcavationTargetInsteadOfMiningFromOutsideWorkArea(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        buildFloor(helper, 0, 6, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerExcavateBounds");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 3));
        tickVillager(level, villager, 20);
        BlockPos outsideStart = helper.absolutePos(new BlockPos(1, 2, 3));
        villager.moveTo(outsideStart.getX() + 0.5D, outsideStart.getY(), outsideStart.getZ() + 0.5D, 0.0F, 0.0F);
        BlockPos targetRel = new BlockPos(2, 1, 3);

        for (int y = 1; y <= 3; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        setBlock(helper, new BlockPos(2, 2, 3), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(2, 3, 3), Blocks.AIR.defaultBlockState());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 3, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();
        BlockPos target = helper.absolutePos(targetRel);
        BlockPos pathOrigin = villager.blockPosition().immutable();

        helper.assertTrue(MiningBlockRules.isMineableExcavationBlock(level, target), "fixture target should be mineable");
        helper.assertTrue(MiningBlockRules.isCurrentExcavationLayer(level, context, target), "fixture target should be the current layer");
        BlockPos insideApproach = helper.absolutePos(new BlockPos(2, 2, 3));
        helper.assertTrue(HiredMoveToBlockFaceJob.isValidApproachPosition(level, insideApproach), "fixture inside approach should be walkable");
        HiredPathResult constrainedPath = new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(target),
                20,
                context::isInsideWorkArea,
                context::isInsideWorkArea,
                pos -> context.isInsideWorkArea(pos) || pos.equals(pathOrigin),
                ignored -> false,
                (candidateTarget, approach) -> MiningExcavationSupport.hasCompleteLadderRouteToLayer(
                        level,
                        context,
                        candidateTarget.getY())
                        && MiningExcavationSupport.entryTarget(level, context) != null
                        && MiningExcavationSupport.shouldUseLadderFallback(
                                context,
                                villager,
                                new HiredPathTarget(candidateTarget.immutable(), approach.immutable(), candidateTarget.getCenter()))).search();
        helper.assertTrue(constrainedPath.reachesDestination(), "fixture target should have an inside lower-layer approach");

        WorkResult firstTick = worker.tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot firstSnapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertTrue(
                firstTick.progressed(),
                "outside lower-layer target should be converted into movement progress, status=" + firstTick.status());
        helper.assertTrue(
                firstSnapshot.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA
                        || firstSnapshot.taskState() == HiredWorkerTaskState.MOVING_TO_TARGET,
                "outside lower-layer task state should return or resume safely, state=" + firstSnapshot.taskState());
        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).is(Blocks.STONE),
                "miner should not start breaking a lower excavation layer while standing outside the work area");
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        helper.assertTrue(navigationTarget == null
                        || context.isInsideWorkArea(navigationTarget)
                        || navigationTarget.getY() == context.workMax().getY() + 1,
                "lower excavation navigation target should stay inside the assigned work area or on the ladder return landing");

        runWorkerUntil(helper, worker, level, villager, hirer, context, 100, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());
        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should still recover and mine the lower target from a valid approach");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COBBLESTONE)),
                "recovered excavation should store drops as output");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 260)
    public static void miningWorkerDescendsLadderAndMinesLowerExcavationTarget(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        buildFloor(helper, 0, 6, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerDescendMine");
        Villager villager = spawnVillager(helper, new BlockPos(2, 4, 2));
        BlockPos targetRel = new BlockPos(3, 1, 3);

        for (int y = 1; y <= 3; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                if (x == 2 && z == 2) {
                    continue;
                }
                setBlock(helper, new BlockPos(x, 1, z), Blocks.AIR.defaultBlockState());
            }
        }
        setBlock(helper, new BlockPos(2, 1, 3), Blocks.BEDROCK.defaultBlockState());
        setBlock(helper, new BlockPos(3, 1, 2), Blocks.BEDROCK.defaultBlockState());
        setBlock(helper, new BlockPos(3, 2, 3), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 3, 3), Blocks.AIR.defaultBlockState());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 3, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 180, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should descend the ladder and mine the lower excavation target");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COBBLESTONE)),
                "descended excavation should store drops as output");
        helper.assertTrue(context.isInsideWorkArea(villager.blockPosition()),
                "miner should end the downward path inside the excavation work area");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 260)
    public static void miningWorkerDescendsAfterBeingPushedToUpperLadderLanding(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerPushDescend");
        Villager villager = spawnVillager(helper, new BlockPos(2, 5, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);

        for (int y = 1; y <= 4; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        for (int y = 1; y <= 4; y++) {
            for (int x = 2; x <= 4; x++) {
                for (int z = 2; z <= 4; z++) {
                    if (x == 2 && z == 2) {
                        continue;
                    }
                    setBlock(helper, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        setBlock(helper, new BlockPos(2, 4, 3), Blocks.BEDROCK.defaultBlockState());
        setBlock(helper, new BlockPos(3, 2, 2), Blocks.BEDROCK.defaultBlockState());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();
        BlockPos target = helper.absolutePos(targetRel);

        WorkResult firstTick = worker.tick(level, villager, hirer, context);
        helper.assertTrue(firstTick.progressed(), "lower target recovery should start movement");

        runWorkerUntil(helper, worker, level, villager, hirer, context, 120, () ->
                state.contains("ActiveWorkApproachPos"));
        helper.assertTrue(state.contains("ActiveWorkApproachPos"),
                "miner should select a lower-layer approach after descending from the upper landing");
        BlockPos activeApproach = BlockPos.of(state.getLong("ActiveWorkApproachPos"));
        helper.assertTrue(activeApproach.getY() <= target.getY() + 1,
                "lower excavation target should not use the upper ladder landing as its mining stance");
        helper.assertTrue(level.getBlockState(target).is(Blocks.STONE),
                "lower target should still exist before the forced displacement recovery step");

        BlockPos pushedRel = new BlockPos(2, 5, 3);
        BlockPos pushed = helper.absolutePos(pushedRel);
        villager.moveTo(pushed.getX() + 0.5D, pushed.getY(), pushed.getZ() + 0.5D, 0.0F, 0.0F);
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);

        runWorkerUntil(helper, worker, level, villager, hirer, context, 200, () ->
                level.getBlockState(target).isAir());

        helper.assertTrue(level.getBlockState(target).isAir(),
                "miner pushed to the upper landing should descend and mine the lower excavation target");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COBBLESTONE)),
                "pushed descent excavation should store drops as output");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 220)
    public static void ladderNavigationAllowsImmediateDescentAfterSurfaceDismount(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 0);
        ServerLevel level = helper.getLevel();

        for (int y = 1; y <= 3; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        setBlock(helper, new BlockPos(3, 0, 2), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 1, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 2, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 3, 2), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 4, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 5, 2), Blocks.AIR.defaultBlockState());

        Villager villager = spawnVillager(helper, new BlockPos(3, 1, 2));
        BlockPos topDismount = helper.absolutePos(new BlockPos(3, 4, 2));
        BlockPos lowerDismount = helper.absolutePos(new BlockPos(3, 1, 2));

        for (int tick = 0; tick < 100 && !villager.blockPosition().equals(topDismount); tick++) {
            helper.assertTrue(
                    VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, topDismount, 0.55D),
                    "ladder helper should keep climbing toward the surface dismount");
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertValueEqual(villager.blockPosition(), topDismount, "villager should reach the top ladder dismount");

        helper.assertTrue(
                VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, lowerDismount, 0.55D),
                "recent top dismount should allow intentional immediate descent");

        for (int tick = 0; tick < 100 && !villager.blockPosition().equals(lowerDismount); tick++) {
            helper.assertTrue(
                    VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, lowerDismount, 0.55D),
                    "ladder helper should keep descending after reversing from storage height; pos="
                            + villager.blockPosition() + ", route="
                            + VillagerTaskNavigationUtil.ladderRouteDebug(level, villager, lowerDismount));
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertValueEqual(villager.blockPosition(), lowerDismount, "villager should return to the lower ladder dismount");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 1400)
    public static void ladderNavigationMovesFromEveryColumnHeightToTopAndBottom(GameTestHelper helper) {
        buildTallLadderFixture(helper, 2, 1, 2, 6);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(3, 1, 2));
        BlockPos bottomDismount = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos topDismount = helper.absolutePos(new BlockPos(3, 7, 2));

        for (int y = 1; y <= 6; y++) {
            moveVillagerToBlock(villager, helper.absolutePos(new BlockPos(2, y, 2)));
            runLadderNavigationUntil(helper, level, villager, topDismount, 120,
                    "villager should climb from ladder y=" + y + " to the top dismount");

            moveVillagerToBlock(villager, helper.absolutePos(new BlockPos(2, y, 2)));
            runLadderNavigationUntil(helper, level, villager, bottomDismount, 120,
                    "villager should descend from ladder y=" + y + " to the bottom dismount");
        }

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 240)
    public static void ladderNavigationBottomDismountClearsClimbStateAndStaysLanded(GameTestHelper helper) {
        buildTallLadderFixture(helper, 2, 1, 2, 6);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 6, 2));
        BlockPos bottomDismount = helper.absolutePos(new BlockPos(3, 1, 2));

        runLadderNavigationUntil(helper, level, villager, bottomDismount, 160,
                "villager should descend to the bottom dismount");
        helper.assertFalse(villager.isNoGravity(), "bottom dismount should clear ladder no-gravity");
        helper.assertTrue(Math.abs(villager.getDeltaMovement().y) < 0.08D,
                "bottom dismount should not keep descent velocity");

        double landedY = villager.getY();
        for (int tick = 0; tick < 20; tick++) {
            level.tickNonPassenger(villager);
        }
        helper.assertValueEqual(villager.blockPosition(), bottomDismount, "villager should stay on the bottom dismount");
        helper.assertTrue(Math.abs(villager.getY() - landedY) < 0.15D,
                "villager should not bob after bottom dismount");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void ladderNavigationBottomRungSnapsToLowerLandingWithoutFalling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 1; y <= 4; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        setBlock(helper, new BlockPos(3, -1, 2), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 0, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 1, 2), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(3, 2, 2), Blocks.AIR.defaultBlockState());

        Villager villager = spawnVillager(helper, new BlockPos(2, 1, 2));
        BlockPos lowerLanding = helper.absolutePos(new BlockPos(3, 0, 2));
        moveVillagerToBlock(villager, helper.absolutePos(new BlockPos(2, 1, 2)));

        helper.assertTrue(
                VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, lowerLanding, 0.55D),
                "bottom rung dismount should be handled by the ladder helper");
        helper.assertValueEqual(villager.blockPosition(), lowerLanding,
                "bottom rung dismount should snap to the lower landing instead of drifting over the gap");
        helper.assertFalse(villager.isNoGravity(), "lower landing snap should clear ladder no-gravity");

        for (int tick = 0; tick < 20; tick++) {
            level.tickNonPassenger(villager);
            helper.assertTrue(villager.getY() >= lowerLanding.getY() - 0.05D,
                    "villager should not fall below the lower ladder landing");
        }
        helper.assertValueEqual(villager.blockPosition(), lowerLanding,
                "villager should remain on the lower landing after dismount");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 260)
    public static void ladderNavigationRetargetsPathDuringActiveClimb(GameTestHelper helper) {
        buildTallLadderFixture(helper, 2, 1, 2, 6);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(3, 1, 2));
        BlockPos bottomDismount = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos topDismount = helper.absolutePos(new BlockPos(3, 7, 2));

        for (int tick = 0; tick < 90 && villager.getY() < bottomDismount.getY() + 3.0D; tick++) {
            helper.assertTrue(
                    VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, topDismount, 0.55D),
                    "villager should start the upward ladder climb");
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertTrue(villager.getY() >= bottomDismount.getY() + 3.0D
                        && villager.getY() < topDismount.getY() - 0.5D,
                "fixture should place the villager mid-climb before retargeting");

        double retargetY = villager.getY();
        helper.assertTrue(
                VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, bottomDismount, 0.55D),
                "intentional mid-climb retarget should be accepted");
        for (int tick = 0; tick < 10; tick++) {
            VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, bottomDismount, 0.55D);
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertTrue(villager.getY() <= retargetY + 0.2D,
                "retargeted ladder navigation should not continue the stale upward climb");

        runLadderNavigationUntil(helper, level, villager, bottomDismount, 120,
                "villager should recover and descend to the newly assigned lower target");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 240)
    public static void horizontalMiningExcavatesTwoHighTunnelWithoutLadders(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerHorizontalTunnel");
        Villager villager = spawnVillager(helper, new BlockPos(4, 2, 3));
        BlockPos lower = new BlockPos(3, 2, 3);
        BlockPos upper = new BlockPos(3, 3, 3);
        setBlock(helper, lower, Blocks.STONE.defaultBlockState());
        setBlock(helper, upper, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.HORIZONTAL_EXCAVATION.serializedName());
        HiredWorkContext context = context(helper, villager, state, lower, upper, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.LADDER, 8));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 160, () ->
                level.getBlockState(helper.absolutePos(lower)).isAir()
                        && level.getBlockState(helper.absolutePos(upper)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(lower)).isAir(), "horizontal miner should clear the lower tunnel block");
        helper.assertTrue(level.getBlockState(helper.absolutePos(upper)).isAir(), "horizontal miner should clear the upper tunnel block");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.LADDER), 8, "horizontal mining must not consume ladders");
        helper.assertFalse(HiredWorkerBrain.snapshot(state, level.getGameTime()).failureReason().equals("missing_ladders"),
                "horizontal mining must not request ladders");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void horizontalMiningRepairsMalformedStairPlanBeforeImmediateTargeting(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerHorizontalStairRecovery");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        BlockPos supportRel = new BlockPos(3, 2, 3);
        BlockPos support = helper.absolutePos(supportRel);
        setBlock(helper, supportRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.HORIZONTAL_EXCAVATION.serializedName());
        state.putString("HorizontalExcavationStairAxis", "diagonal");
        state.putBoolean("HorizontalExcavationStairFromMin", true);
        state.putInt("HorizontalExcavationStairLane", support.getX());
        HiredWorkContext context = context(
                helper,
                villager,
                state,
                new BlockPos(2, 2, 2),
                new BlockPos(6, 7, 4),
                true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.setProgressTicks(200);

        new MiningWorker().tick(level, villager, hirer, context);

        helper.assertTrue(level.getBlockState(support).is(Blocks.STONE),
                "immediate targeting must preserve a reserved horizontal stair support");
        helper.assertValueEqual(state.getString("HorizontalExcavationStairAxis"), "x",
                "malformed stair axis should be replaced with a valid route");
        helper.assertValueEqual(state.getInt("HorizontalExcavationStairLane"), villager.blockPosition().getZ(),
                "recovered stair route should use an in-bounds lane near the miner");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void horizontalMiningPatchesFloorFromMinedOutputsByDefault(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerHorizontalFloor");
        Villager villager = spawnVillager(helper, new BlockPos(4, 2, 3));
        BlockPos hole = new BlockPos(3, 1, 3);
        BlockPos workCell = new BlockPos(3, 2, 3);
        setBlock(helper, hole, Blocks.AIR.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.HORIZONTAL_EXCAVATION.serializedName());
        HiredWorkContext context = context(helper, villager, state, workCell, workCell, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 1));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);

        helper.assertTrue(level.getBlockState(helper.absolutePos(hole)).is(Blocks.COBBLESTONE),
                "horizontal floor holes should be patched with mined output blocks by default");
        helper.assertTrue(result.status().contains("fall_guard"), "floor repair should report hazard remediation");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void horizontalMiningFloorPatchingCanBeDisabled(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerHorizontalFloorOff");
        Villager villager = spawnVillager(helper, new BlockPos(4, 2, 3));
        BlockPos hole = new BlockPos(3, 1, 3);
        BlockPos workCell = new BlockPos(3, 2, 3);
        setBlock(helper, hole, Blocks.AIR.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.HORIZONTAL_EXCAVATION.serializedName());
        state.putBoolean(MiningHorizontalOptions.PATCH_FLOOR_TAG, false);
        HiredWorkContext context = context(helper, villager, state, workCell, workCell, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 1));

        new MiningWorker().tick(level, villager, hirer, context);

        helper.assertTrue(level.getBlockState(helper.absolutePos(hole)).isAir(),
                "disabled horizontal floor patching should leave floor holes unchanged");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.COBBLESTONE), 1,
                "disabled floor patching should not consume fill blocks");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningRulesTreatLadderFaceAsExposedForShaftExtension(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos targetRel = new BlockPos(3, 1, 3);
        for (Direction direction : Direction.values()) {
            setBlock(helper, targetRel.relative(direction), Blocks.STONE.defaultBlockState());
        }
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());
        setBlock(helper, targetRel.above().north(), Blocks.STONE.defaultBlockState());
        setBlock(
                helper,
                targetRel.above(),
                Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));

        helper.assertTrue(
                MiningBlockRules.isMineableExcavationBlock(level, helper.absolutePos(targetRel)),
                "the next shaft block should remain mineable through an existing ladder face");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 320)
    public static void ladderNavigationChoosesReachableRungWhenBottomEntryIsBlocked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 1; y <= 5; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }

        // The closest bottom rung has no valid standing floor. A one-block step provides
        // a reachable entry beside the second rung and a platform provides a top dismount.
        setBlock(helper, new BlockPos(4, 0, 2), Blocks.COBBLESTONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 1, 2), Blocks.COBBLESTONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 5, 2), Blocks.COBBLESTONE.defaultBlockState());
        Villager villager = spawnVillager(helper, new BlockPos(4, 1, 2));
        BlockPos target = helper.absolutePos(new BlockPos(3, 6, 2));

        for (int tick = 0; tick < 220 && !villager.blockPosition().equals(target); tick++) {
            VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, target, 0.55D);
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertTrue(villager.blockPosition().equals(target),
                "column-level ladder planning should reach the safe top dismount; pos="
                        + villager.blockPosition() + ", precise=(" + villager.getX() + "," + villager.getY() + ","
                        + villager.getZ() + "), nav=" + villager.getNavigation().getTargetPos()
                        + ", route=" + VillagerTaskNavigationUtil.ladderRouteDebug(level, villager, target));
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void ladderNavigationRejectsBlockedDirectEntryCorner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 1; y <= 5; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }

        setBlock(helper, new BlockPos(3, 0, 2), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(4, 0, 3), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(4, 1, 2), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(4, 2, 2), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(3, 5, 2), Blocks.STONE.defaultBlockState());

        Villager villager = spawnVillager(helper, new BlockPos(4, 1, 3));
        BlockPos target = helper.absolutePos(new BlockPos(3, 6, 2));

        helper.assertValueEqual(
                VillagerTaskNavigationUtil.ladderRouteDebug(level, villager, target),
                "none",
                "blocked diagonal corner should not be treated as a direct ladder entry");
        helper.assertFalse(
                VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, target, 0.55D),
                "ladder navigation should fail cleanly instead of steering into a blocking corner");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 420)
    public static void miningWorkerDepositsAndReturnsToLowerExcavationByLadder(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 3);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerDepositReturnMine");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos firstTargetRel = new BlockPos(3, 1, 3);
        BlockPos secondTargetRel = new BlockPos(4, 1, 3);
        BlockPos chestRel = new BlockPos(6, 4, 3);
        BlockPos chest = helper.absolutePos(chestRel);

        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                setBlock(helper, new BlockPos(x, 1, z), Blocks.BEDROCK.defaultBlockState());
                setBlock(helper, new BlockPos(x, 2, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, 3, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, 4, z), Blocks.AIR.defaultBlockState());
            }
        }
        setBlock(helper, firstTargetRel, Blocks.STONE.defaultBlockState());
        setBlock(helper, secondTargetRel, Blocks.STONE.defaultBlockState());
        for (int y = 1; y <= 3; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 3, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        for (int slot = 6; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            context.inventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        context.inventory().setItem(18, new ItemStack(Items.COBBLESTONE, 63));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 360, () ->
                level.getBlockState(helper.absolutePos(firstTargetRel)).isAir()
                        && level.getBlockState(helper.absolutePos(secondTargetRel)).isAir()
                        && countItem(container(level, chest), Items.COBBLESTONE) > 0);

        helper.assertTrue(level.getBlockState(helper.absolutePos(firstTargetRel)).isAir(),
                "miner should clear the first lower excavation target before depositing");
        helper.assertTrue(level.getBlockState(helper.absolutePos(secondTargetRel)).isAir(),
                "miner should return from storage and clear the remaining lower excavation target");
        helper.assertTrue(countItem(container(level, chest), Items.COBBLESTONE) > 0,
                "miner should deposit filled output to the assigned surface chest");
        helper.assertTrue(context.isInsideWorkArea(villager.blockPosition()),
                "miner should finish the resumed lower excavation inside the work area");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 480)
    public static void miningWorkerOutsideAreaReturnsBeforeTargetScan(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 3);
        buildFloor(helper, 0, 8, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerOutsideReturnMine");
        movePlayer(helper, hirer, new BlockPos(1, 4, 1));
        Villager villager = spawnVillager(helper, new BlockPos(6, 4, 3));
        BlockPos targetRel = new BlockPos(3, 1, 3);

        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                setBlock(helper, new BlockPos(x, 1, z), Blocks.BEDROCK.defaultBlockState());
                setBlock(helper, new BlockPos(x, 2, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, 3, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, 4, z), Blocks.AIR.defaultBlockState());
            }
        }
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());
        for (int y = 1; y <= 3; y++) {
            setBlock(helper, new BlockPos(2, y, 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(2, y, 2),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 3, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        HiredWorkerBrain.setFailure(context, "target_unreachable", level.getGameTime() + 100L);
        HiredWorkerBrain.setLastTargetScanResult(context, "no_targets");
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
        MiningWorker worker = new MiningWorker();

        WorkResult firstTick = worker.tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot firstState = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertTrue(firstTick.progressed(), "outside miner should begin returning instead of idling without targets; status="
                + firstTick.status() + ", task=" + firstState.taskState() + ", failure=" + firstState.failureReason()
                + ", scan=" + firstState.lastTargetScanResult() + ", target=" + firstState.targetPos());
        helper.assertTrue(
                firstState.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA
                        || firstState.taskState() == HiredWorkerTaskState.MOVING_TO_TARGET,
                "outside miner should return or immediately resume target movement; state=" + firstState.taskState());
        helper.assertFalse(firstState.failureReason().contains("target_unreachable"),
                "stale target failure should be cleared during outside return");
        helper.assertFalse(firstState.lastTargetScanResult().contains("no_targets"),
                "stale no-target scan should be cleared during outside return");
        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).is(Blocks.STONE),
                "return tick should not mine or reject the valid target before navigating back");

        helper.startSequence()
                .thenExecuteFor(360, () -> {
                    if (!level.getBlockState(helper.absolutePos(targetRel)).isAir()) {
                        worker.maintain(level, villager, context);
                        if (Math.floorMod(level.getGameTime() + villager.getUUID().getLeastSignificantBits(), 10L) == 0L) {
                            worker.tick(level, villager, hirer, context);
                        }
                    }
                })
                .thenExecute(() -> {
                    HiredWorkerBrain.Snapshot finalState = HiredWorkerBrain.snapshot(state, level.getGameTime());
                    helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                            "miner should descend from the surface side and mine the valid lower target; pos="
                                    + villager.blockPosition() + ", nav=" + villager.getNavigation().getTargetPos()
                                    + ", state=" + finalState.taskState() + ", failure=" + finalState.failureReason()
                                    + ", target=" + finalState.targetPos());
                    helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COBBLESTONE)),
                            "returned excavation should store mined drops as output");
                    helper.assertTrue(context.isInsideWorkArea(villager.blockPosition()),
                            "miner should finish the recovered excavation inside the work area");
                    villager.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80000, batch = "mining_full_mixed_box")
    public static void miningWorkerServiceExcavatesFullMixedBoxAndDeposits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setDayTime(1000L);
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(12, 6, 6));

        BlockPos workMinRel = new BlockPos(2, 1, 2);
        BlockPos workMaxRel = new BlockPos(10, 5, 10);
        fillMixedExcavationBox(helper, workMinRel, workMaxRel);
        buildStoragePlatform(helper, 11, 15, 4, 8, 5);

        BlockPos chestRel = new BlockPos(12, 6, 6);
        BlockPos paymentRel = new BlockPos(13, 6, 6);
        BlockPos chest = helper.absolutePos(chestRel);
        BlockPos payment = helper.absolutePos(paymentRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, paymentRel, VillagerRetaliationBlocks.PAYMENT_BOX.get().defaultBlockState());
        Container supplyAndOutput = container(level, chest);
        supplyAndOutput.setItem(0, new ItemStack(Items.LADDER, 64));
        supplyAndOutput.setItem(1, new ItemStack(Items.TORCH, 64));
        supplyAndOutput.setItem(2, new ItemStack(Items.DIAMOND_PICKAXE));
        supplyAndOutput.setItem(3, new ItemStack(Items.DIAMOND_SHOVEL));
        container(level, payment).setItem(0, new ItemStack(Items.EMERALD, 64));

        Villager villager = spawnVillager(helper, new BlockPos(10, 6, 6));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        helper.assertTrue(
                HiredVillagerContractService.startHireContract(level, villager, hirer, 4, 64),
                "toolsmith fixture should accept a mining contract");
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.MINING),
                "toolsmith villager should accept mining role");
        HiredVillagerWorkService.setWorkArea(
                hirer,
                level,
                villager,
                helper.absolutePos(workMinRel),
                helper.absolutePos(workMaxRel));
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        session.state().putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        session.state().putBoolean("UseAssignedStorageForSupplies", true);
        session.state().putBoolean("AutoDepositOutputs", true);

        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.removeAssignedContainer(level, payment);
        AssignedStorageService.AssignSummary chestAssignment = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.GENERAL_PURPOSE);
        AssignedStorageService.AssignSummary paymentAssignment = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), payment)),
                AssignedStorageService.PAYMENT_PURPOSE);
        helper.assertValueEqual(chestAssignment.assigned(), 1, "mixed mining chest assignment");
        helper.assertValueEqual(paymentAssignment.assigned(), 1, "mixed mining payment assignment");
        runHiredMiningServiceUntil(helper, level, villager, workMinRel, workMaxRel, 76000, () ->
                mixedExcavationBoxCleared(level, helper, workMinRel, workMaxRel)
                        && !HiredJobInventory.getJobInventory(villager).hasOutputItems());

        helper.assertTrue(mixedExcavationBoxCleared(level, helper, workMinRel, workMaxRel),
                "miner should clear every original grass/dirt/stone/iron block from the assigned box");
        helper.assertTrue(countItem(supplyAndOutput, Items.DIRT) >= 150,
                "miner should deposit grass and dirt drops not reused as support backing into the assigned chest, count="
                        + countItem(supplyAndOutput, Items.DIRT));
        helper.assertTrue(countItem(supplyAndOutput, Items.RAW_IRON) >= 40,
                "miner should deposit iron ore drops into the assigned chest, count="
                        + countItem(supplyAndOutput, Items.RAW_IRON));
        helper.assertTrue(countItem(supplyAndOutput, Items.COBBLESTONE) > 0,
                "miner should deposit stone drops into the assigned chest");
        helper.assertFalse(HiredJobInventory.getJobInventory(villager).hasOutputItems(),
                "miner should finish with job output inventory deposited");
        helper.assertTrue(countItem(container(level, payment), Items.EMERALD) >= 64,
                "payment box should remain assigned and stocked with emeralds");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerHarvestsAndReplantsReadyCrop(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingDirect");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos composterRel = new BlockPos(2, 1, 2);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        BlockPos cropRel = new BlockPos(3, 2, 2);
        setBlock(helper, new BlockPos(3, 1, 2), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cropRel, ((CropBlock) Blocks.WHEAT).getStateForAge(7));

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), false);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        context.inventory().insertPlainSupply(new ItemStack(Items.WHEAT_SEEDS));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        BlockState crop = level.getBlockState(helper.absolutePos(cropRel));
        helper.assertTrue(crop.is(Blocks.WHEAT), "farming worker should replant wheat after harvesting");
        helper.assertValueEqual(crop.getValue(CropBlock.AGE), 0, "replanted wheat should start fresh");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.WHEAT)), "harvested wheat should be stored as job output");
        helper.assertValueEqual(result.status(), "interaction.work.farming.completed_crop", "direct crop completion status");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerHarvestsMelonBlockOutput(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingMelon");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos composterRel = new BlockPos(2, 1, 2);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(
                MemoryModuleType.JOB_SITE,
                GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        BlockPos melonRel = new BlockPos(3, 2, 2);
        setBlock(helper, melonRel, Blocks.MELON.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(
                helper,
                villager,
                state,
                new BlockPos(1, 2, 1),
                new BlockPos(5, 4, 4),
                false);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        helper.assertTrue(
                level.getBlockState(helper.absolutePos(melonRel)).isAir(),
                "farming worker should harvest a mature melon output block");
        helper.assertTrue(
                context.inventory().hasOutput(stack -> stack.is(Items.MELON_SLICE)),
                "melon slices should be stored as job output");
        helper.assertValueEqual(
                result.status(),
                "interaction.work.farming.completed_output",
                "melon completion status");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerIgnoresCropOutsideCircularJobSiteRange(GameTestHelper helper) {
        buildFloor(helper, 0, 12, 0, 12, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingCircle");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos composterRel = new BlockPos(2, 1, 2);
        BlockPos composter = helper.absolutePos(composterRel);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), composter));
        BlockPos cornerCropRel = new BlockPos(10, 2, 10);
        BlockPos cornerCrop = helper.absolutePos(cornerCropRel);
        setBlock(helper, cornerCropRel.below(), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cornerCropRel, ((CropBlock) Blocks.WHEAT).getStateForAge(7));

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(
                helper,
                villager,
                state,
                new BlockPos(1, 2, 1),
                new BlockPos(5, 4, 4),
                false);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        BlockState cornerCropState = level.getBlockState(cornerCrop);
        helper.assertTrue(
                cornerCropState.getBlock() instanceof CropBlock crop && crop.isMaxAge(cornerCropState),
                "farmer should leave a crop outside the circular job-site range untouched");
        helper.assertValueEqual(
                result.status(),
                "interaction.work.farming.waiting_for_growth",
                "outside-corner crop should not become a field target");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerBoundsGrowingCropPresenceScans(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingBoundedScan");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        CompoundTag state = new CompoundTag();
        state.putBoolean(HiredFarmingOptions.TILL_SOIL_TAG, false);
        HiredWorkContext context = context(
                helper,
                villager,
                state,
                new BlockPos(0, 2, 0),
                new BlockPos(48, 14, 48),
                true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        FarmingWorker worker = new FarmingWorker();

        for (int tick = 0; tick < 41; tick++) {
            worker.tick(level, villager, hirer, context);
        }

        helper.assertTrue(
                state.getLong("FarmingFieldGrowingScanCursor") > 0L,
                "growing-crop presence scan should preserve a cursor instead of scanning the whole work area at once");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 240)
    public static void miningWorkerPlugsLavaBeforeExcavatingAdjacentBlock(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLavaPlug");
        BlockPos targetRel = new BlockPos(3, 1, 3);
        BlockPos lavaRel = targetRel.east();
        Villager villager = spawnVillager(helper, targetRel.above());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());
        setBlock(helper, lavaRel, Blocks.LAVA.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, targetRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.COBBLESTONE, 4));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 160, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should excavate the target after containing the lava");
        helper.assertTrue(level.getBlockState(helper.absolutePos(lavaRel)).is(Blocks.COBBLESTONE),
                "miner should replace the exposed lava cell with a solid plug");
        helper.assertTrue(level.getFluidState(helper.absolutePos(lavaRel)).isEmpty(),
                "lava plug should leave no fluid in the exposed cell");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 360)
    public static void miningWorkerFetchesLavaPlugBlocksFromAssignedStorage(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 0);
        buildFloor(helper, 0, 8, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerStoredLavaPlug");
        BlockPos targetRel = new BlockPos(3, 1, 3);
        BlockPos lavaRel = targetRel.east();
        BlockPos chestRel = new BlockPos(7, 2, 3);
        BlockPos chest = helper.absolutePos(chestRel);
        Villager villager = spawnVillager(helper, targetRel.above());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());
        setBlock(helper, lavaRel, Blocks.LAVA.defaultBlockState());
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        container(level, chest).setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.AssignSummary assignment = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.GENERAL_PURPOSE);
        helper.assertValueEqual(assignment.assigned(), 1, "hazard fill storage assignment");
        helper.assertValueEqual(
                AssignedStorageService.countItems(villager, stack -> stack.is(Items.COBBLESTONE)),
                8,
                "hazard fill blocks visible in assigned input storage");
        helper.assertTrue(
                AssignedStorageService.nearestAssignedStoragePosContaining(
                        level,
                        villager,
                        stack -> stack.is(Items.COBBLESTONE)) != null,
                "hazard fill storage should be discoverable before worker navigation");

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, targetRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        MiningWorker worker = new MiningWorker();

        WorkResult firstResult = worker.tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot firstSnapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(firstResult.status(), "interaction.work.mining.hazard.gathered_fill_blocks",
                "hazard storage transfer status");
        helper.assertValueEqual(firstSnapshot.taskState(), HiredWorkerTaskState.RETURNING_TO_WORK_AREA,
                "hazard storage transfer task state");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.COBBLESTONE), 1,
                "worker should retain the single fill block fetched for the active plan");
        helper.assertValueEqual(countItem(container(level, chest), Items.COBBLESTONE), 7,
                "hazard remediation should debit the fetched fill block from assigned storage");

        runWorkerUntil(helper, worker, level, villager, hirer, context, 280, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should return from assigned storage and finish the excavation target");
        helper.assertTrue(level.getBlockState(helper.absolutePos(lavaRel)).is(Blocks.COBBLESTONE),
                "miner should use a stored fill block to seal the exposed lava");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void miningWorkerWaitsForLavaPlugBlocksWithoutExposingHazard(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerMissingLavaPlug");
        BlockPos targetRel = new BlockPos(3, 1, 3);
        BlockPos lavaRel = targetRel.east();
        Villager villager = spawnVillager(helper, targetRel.above());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());
        setBlock(helper, lavaRel, Blocks.LAVA.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, targetRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(result.status(), "interaction.work.mining.hazard.missing_fill_blocks",
                "missing hazard fill status");
        helper.assertValueEqual(snapshot.taskState(), HiredWorkerTaskState.WAITING_FOR_MATERIALS,
                "missing hazard fill task state");
        helper.assertValueEqual(snapshot.failureReason(), "missing_hazard_fill_blocks",
                "missing hazard fill reason");
        helper.assertValueEqual(MiningWorker.phase(context), "blocked_missing_supplies",
                "missing hazard fill mining phase");
        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).is(Blocks.STONE),
                "miner must not open the excavation face without a lava plug");
        helper.assertTrue(level.getBlockState(helper.absolutePos(lavaRel)).is(Blocks.LAVA),
                "blocked miner should leave the contained lava source untouched");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 500)
    public static void miningWorkerDrainsBoundedWaterPocketWithTemporaryFill(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerWaterDrain");
        BlockPos targetRel = new BlockPos(3, 1, 3);
        BlockPos firstWaterRel = targetRel.east();
        BlockPos secondWaterRel = firstWaterRel.east();
        Villager villager = spawnVillager(helper, targetRel.above());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());
        setBlock(helper, firstWaterRel, Blocks.WATER.defaultBlockState());
        setBlock(helper, secondWaterRel, Blocks.WATER.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, secondWaterRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.COBBLESTONE, 8));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 400, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir()
                        && level.getBlockState(helper.absolutePos(firstWaterRel)).isAir()
                        && level.getBlockState(helper.absolutePos(secondWaterRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should clear the original stone after draining water");
        helper.assertTrue(level.getFluidState(helper.absolutePos(firstWaterRel)).isEmpty(),
                "first bounded water source should be drained");
        helper.assertTrue(level.getFluidState(helper.absolutePos(secondWaterRel)).isEmpty(),
                "second bounded water source should be drained");
        helper.assertTrue(level.getBlockState(helper.absolutePos(firstWaterRel)).isAir()
                        && level.getBlockState(helper.absolutePos(secondWaterRel)).isAir(),
                "temporary water-displacement blocks should be excavated after the pocket is dry");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void miningWorkerBuildsFallGuardBeforeRemovingFloor(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFallGuard");
        BlockPos targetRel = new BlockPos(3, 2, 3);
        BlockPos guardRel = targetRel.below();
        Villager villager = spawnVillager(helper, targetRel.above());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());
        setBlock(helper, guardRel, Blocks.AIR.defaultBlockState());
        setBlock(helper, targetRel.west(), Blocks.BEDROCK.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, targetRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.COBBLESTONE, 4));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 220, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should remove the assigned floor only after securing the drop");
        helper.assertTrue(level.getBlockState(helper.absolutePos(guardRel)).is(Blocks.COBBLESTONE),
                "miner should leave a solid fall guard below the bottom of the assigned excavation");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 500)
    public static void miningWorkerResumesPersistedWaterHazardPlan(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerPersistedWater");
        BlockPos targetRel = new BlockPos(2, 1, 3);
        BlockPos firstWaterRel = targetRel.east();
        BlockPos secondWaterRel = firstWaterRel.east();
        BlockPos thirdWaterRel = secondWaterRel.east();
        Villager villager = spawnVillager(helper, targetRel.above());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());
        setBlock(helper, firstWaterRel, Blocks.WATER.defaultBlockState());
        setBlock(helper, secondWaterRel, Blocks.WATER.defaultBlockState());
        setBlock(helper, thirdWaterRel, Blocks.WATER.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, thirdWaterRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.COBBLESTONE, 12));

        MiningWorker firstWorkerInstance = new MiningWorker();
        firstWorkerInstance.tick(level, villager, hirer, context);
        helper.assertTrue(state.contains("MiningHazardPlanKind")
                        && state.getLongArray("MiningHazardPlanPositions").length >= 2,
                "bounded water remediation should persist its remaining plan in worker state");

        MiningWorker resumedWorkerInstance = new MiningWorker();
        runWorkerUntil(helper, resumedWorkerInstance, level, villager, hirer, context, 400, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir()
                        && level.getBlockState(helper.absolutePos(firstWaterRel)).isAir()
                        && level.getBlockState(helper.absolutePos(secondWaterRel)).isAir()
                        && level.getBlockState(helper.absolutePos(thirdWaterRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "reconstructed mining worker should finish the original excavation target");
        helper.assertTrue(level.getFluidState(helper.absolutePos(firstWaterRel)).isEmpty()
                        && level.getFluidState(helper.absolutePos(secondWaterRel)).isEmpty()
                        && level.getFluidState(helper.absolutePos(thirdWaterRel)).isEmpty(),
                "reconstructed mining worker should resume and finish the persisted water drain");
        helper.assertFalse(state.contains("MiningHazardPlanKind"),
                "completed persisted hazard plan should be removed from worker state");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 360)
    public static void miningWorkerSealsUnboundedWaterAtExcavationFace(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerWaterSeal");
        BlockPos targetRel = new BlockPos(2, 1, 3);
        BlockPos sealRel = targetRel.east();
        BlockPos remoteWaterRel = targetRel.east(4);
        Villager villager = spawnVillager(helper, targetRel.above());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());
        for (int dx = 1; dx <= 4; dx++) {
            setBlock(helper, targetRel.east(dx), Blocks.WATER.defaultBlockState());
        }

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        HiredWorkContext context = context(helper, villager, state, targetRel, targetRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.COBBLESTONE, 4));
        MiningWorker worker = new MiningWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 280, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should excavate after isolating the external water body");
        helper.assertTrue(level.getBlockState(helper.absolutePos(sealRel)).is(Blocks.COBBLESTONE),
                "miner should seal the water face adjacent to the excavation");
        helper.assertTrue(level.getFluidState(helper.absolutePos(sealRel)).isEmpty(),
                "water seal should not retain fluid in the barrier cell");
        helper.assertTrue(level.getFluidState(helper.absolutePos(remoteWaterRel)).is(FluidTags.WATER),
                "sealing should leave the remote unbounded water body intact");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void miningLayerCacheDoesNotTreatUnloadedWorkAreaAsComplete(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        CompoundTag state = new CompoundTag();
        BlockPos farRel = new BlockPos(4096, 2, 4096);
        HiredWorkContext context = context(helper, villager, state, farRel, farRel.above(2), true);

        helper.assertFalse(level.hasChunkAt(context.workMin()),
                "fixture work area should remain unloaded");
        helper.assertTrue(MiningBlockRules.currentExcavationLayer(level, context) == null,
                "an unavailable excavation layer should not be selected");
        helper.assertFalse(state.contains("CurrentExcavationLayerPresent")
                        || state.contains("CurrentExcavationLayerExpiresGameTime"),
                "an unloaded work area must not persist a cached completion result");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerSanitizesMalformedShaftAndBarrierState(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerMiningStateSanitize");
        Villager villager = spawnVillager(helper, new BlockPos(2, 1, 3));
        BlockPos barrierRel = new BlockPos(3, 1, 3);
        BlockPos barrier = helper.absolutePos(barrierRel);
        setBlock(helper, barrierRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        state.putString("ExcavationLadderX", "broken");
        state.putInt("ExcavationLadderZ", barrier.getZ());
        state.putString("ExcavationLadderFacing", Direction.NORTH.getName());
        state.putLongArray("MiningPermanentHazardBarriers", new long[] {barrier.asLong(), barrier.asLong()});
        HiredWorkContext context = context(
                helper,
                villager,
                state,
                new BlockPos(2, 1, 3),
                new BlockPos(3, 2, 3),
                true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));

        MiningWorker.excavationEntryTarget(level, context);
        helper.assertFalse(state.contains("ExcavationLadderX")
                        || state.contains("ExcavationLadderZ")
                        || state.contains("ExcavationLadderFacing"),
                "partial or incorrectly typed shaft state should be discarded atomically");

        new MiningWorker().tick(level, villager, hirer, context);
        helper.assertValueEqual(state.getLongArray("MiningPermanentHazardBarriers").length, 1,
                "persisted permanent barriers should be deduplicated during validation");
        helper.assertValueEqual(state.getLongArray("MiningPermanentHazardBarriers")[0], barrier.asLong(),
                "barrier validation should preserve the live in-scope barrier");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void miningConfigurationResetClearsOnlyScopedInfrastructureState(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 0);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        BlockPos targetRel = new BlockPos(2, 1, 2);
        BlockPos target = helper.absolutePos(targetRel);

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        state.putString("MiningHazardPlanKind", "water");
        state.putLongArray("MiningHazardPlanPositions", new long[] {target.asLong()});
        state.putLongArray("MiningPermanentHazardBarriers", new long[] {target.asLong()});
        state.putInt("ExcavationLadderX", target.getX());
        state.putInt("ExcavationLadderZ", target.getZ());
        state.putString("ExcavationLadderFacing", Direction.NORTH.getName());
        state.putString("HorizontalExcavationStairAxis", "x");
        state.putBoolean("HorizontalExcavationStairFromMin", true);
        state.putInt("HorizontalExcavationStairLane", target.getZ());
        state.putBoolean("HorizontalExcavationStairCleanup", true);
        state.putLong("ActiveWorkBlockPos", target.asLong());
        HiredWorkContext context = context(helper, villager, state, targetRel, targetRel, true);

        MiningWorker.resetForModeChange(level, villager, context, HiredMiningMode.EXPOSED_ORES);
        helper.assertFalse(state.contains("MiningHazardPlanKind"),
                "mode change should discard an in-progress hazard plan");
        helper.assertTrue(state.getLongArray("MiningPermanentHazardBarriers").length == 1,
                "mode change should preserve permanent safety barriers for the same work area");
        helper.assertTrue(state.contains("ExcavationLadderX"),
                "mode change should preserve the shaft selected for the same work area");
        helper.assertFalse(state.contains("ActiveWorkBlockPos"),
                "mode change should clear the stale active mining target");
        helper.assertFalse(state.contains("HorizontalExcavationStairAxis")
                        || state.contains("HorizontalExcavationStairLane")
                        || state.contains("HorizontalExcavationStairCleanup"),
                "mode change should discard the horizontal excavation plan");
        helper.assertValueEqual(MiningWorker.phase(context), "find_target",
                "mode change should reset the typed mining phase");

        state.putString("MiningHazardPlanKind", "water");
        state.putLongArray("MiningHazardPlanPositions", new long[] {target.asLong()});
        state.putString("HorizontalExcavationStairAxis", "z");
        state.putBoolean("HorizontalExcavationStairFromMin", false);
        state.putInt("HorizontalExcavationStairLane", target.getX());
        MiningWorker.resetForOptionChange(level, villager, context, HiredMiningMode.EXPOSED_ORES);
        helper.assertFalse(state.contains("MiningHazardPlanKind"),
                "option change should discard an in-progress plan built under the old setting");
        helper.assertTrue(state.getLongArray("MiningPermanentHazardBarriers").length == 1,
                "option change should preserve permanent safety barriers");
        helper.assertTrue(state.contains("ExcavationLadderX"),
                "option change should preserve the selected shaft");
        helper.assertTrue(state.contains("HorizontalExcavationStairAxis"),
                "option change should preserve the horizontal excavation route");

        MiningWorker.resetForWorkAreaChange(level, villager, context, HiredMiningMode.EXPOSED_ORES);
        helper.assertValueEqual(state.getLongArray("MiningPermanentHazardBarriers").length, 0,
                "work-area change should discard barrier metadata from the previous area");
        helper.assertFalse(state.contains("ExcavationLadderX")
                        || state.contains("ExcavationLadderZ")
                        || state.contains("ExcavationLadderFacing"),
                "work-area change should discard the previous excavation shaft");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerSynchronizesExternalModeChangesAndClearsOldPlans(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerMiningModeSync");
        Villager villager = spawnVillager(helper, new BlockPos(2, 1, 2));
        BlockPos areaRel = new BlockPos(2, 1, 2);
        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXPOSED_ORES.serializedName());
        HiredWorkContext context = context(helper, villager, state, areaRel, areaRel, true);
        state.putInt("MiningStateVersion", 2);
        state.putString("MiningStateMode", HiredMiningMode.HORIZONTAL_EXCAVATION.serializedName());
        state.putLong("MiningStateWorkMin", context.workMin().asLong());
        state.putLong("MiningStateWorkMax", context.workMax().asLong());
        state.putString("HorizontalExcavationStairAxis", "x");
        state.putBoolean("HorizontalExcavationStairFromMin", true);
        state.putInt("HorizontalExcavationStairLane", context.workMin().getZ());

        new MiningWorker().tick(level, villager, hirer, context);

        helper.assertFalse(state.contains("HorizontalExcavationStairAxis")
                        || state.contains("HorizontalExcavationStairLane"),
                "automatic mode synchronization should not retain the previous horizontal plan");
        helper.assertValueEqual(state.getString("MiningStateMode"), HiredMiningMode.EXPOSED_ORES.serializedName(),
                "automatic synchronization should persist the current mode");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void miningWorkerCancelsResolvedHazardStorageTrip(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 0);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerResolvedHazardTrip");
        BlockPos targetRel = new BlockPos(2, 1, 2);
        BlockPos target = helper.absolutePos(targetRel);
        Villager villager = spawnVillager(helper, targetRel.above());
        setBlock(helper, targetRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        state.putString("MiningState", "gather_hazard_blocks");
        state.putString("MiningHazardPlanKind", "water");
        state.putLongArray("MiningHazardPlanPositions", new long[] {target.asLong()});
        HiredWorkContext context = context(helper, villager, state, targetRel, targetRel, true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        HiredWorkerBrain.setStorageTarget(context, target.east(3));
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);

        new MiningWorker().tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());

        helper.assertTrue(snapshot.storageTargetPos() == null,
                "resolved persisted hazard plan should release its obsolete storage target");
        helper.assertValueEqual(state.getInt("MiningHazardPlanIndex"), 1,
                "resolved persisted hazard cell should advance without gathering supplies");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void miningWorkerReplansPersistedShaftWithImpossibleGap(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerBlockedShaft");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        BlockPos blockedShaftRel = new BlockPos(2, 2, 2);
        BlockPos recoveredShaftRel = new BlockPos(4, 2, 2);
        BlockPos recoveredBackingRel = new BlockPos(4, 2, 1);
        setBlock(helper, blockedShaftRel, Blocks.BEDROCK.defaultBlockState());
        setBlock(helper, recoveredShaftRel, Blocks.AIR.defaultBlockState());
        setBlock(helper, recoveredBackingRel, Blocks.STONE.defaultBlockState());

        CompoundTag state = new CompoundTag();
        state.putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        BlockPos blockedShaft = helper.absolutePos(blockedShaftRel);
        state.putInt("ExcavationLadderX", blockedShaft.getX());
        state.putInt("ExcavationLadderZ", blockedShaft.getZ());
        state.putString("ExcavationLadderFacing", Direction.SOUTH.getName());
        HiredWorkContext context = context(helper, villager, state, new BlockPos(2, 1, 2), new BlockPos(4, 2, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        context.inventory().insertSupply(new ItemStack(Items.LADDER, 8));

        WorkResult result = new MiningWorker().tick(level, villager, hirer, context);
        BlockPos recoveredShaft = helper.absolutePos(recoveredShaftRel);

        helper.assertValueEqual(result.status(), "interaction.work.mining.support.placed_ladder", "blocked shaft recovery status");
        helper.assertTrue(level.getBlockState(blockedShaft).is(Blocks.BEDROCK),
                "shaft recovery should not alter an impossible obstruction");
        helper.assertTrue(level.getBlockState(recoveredShaft).is(Blocks.LADDER),
                "miner should move the shaft to a fully viable column");
        helper.assertValueEqual(state.getInt("ExcavationLadderX"), recoveredShaft.getX(), "replanned shaft x");
        helper.assertValueEqual(state.getInt("ExcavationLadderZ"), recoveredShaft.getZ(), "replanned shaft z");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 180)
    public static void sharedRouteNavigatorApproachesNonStandableContainerNode(GameTestHelper helper) {
        buildFloor(helper, -6, 10, -2, 7, 1);
        ServerLevel level = helper.getLevel();
        BlockPos startRel = new BlockPos(1, 2, 2);
        Villager villager = spawnVillager(helper, startRel);
        tickVillager(level, villager, 20);
        villager.moveTo(helper.absolutePos(startRel).getCenter());
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        BlockPos nodeRel = new BlockPos(5, 2, 2);
        BlockPos node = helper.absolutePos(nodeRel);
        setBlock(helper, nodeRel, Blocks.CHEST.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(helper, villager, state, List.of(nodeRel));
        helper.startSequence()
                .thenExecuteFor(140, () -> {
                    if (villager.blockPosition().distSqr(node) > 4.0D) {
                        HiredRouteNavigator.maintainRoute(level, villager, context, 0.5D);
                    }
                })
                .thenExecute(() -> {
                    HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
                    helper.assertTrue(villager.blockPosition().distSqr(node) <= 4.0D,
                            "shared route navigation should reach a valid block beside a non-standable node; pos="
                                    + villager.blockPosition() + ", nav=" + villager.getNavigation().getTargetPos()
                                    + ", state=" + snapshot.taskState() + ", failure=" + snapshot.failureReason());
                    helper.assertFalse(villager.blockPosition().equals(node),
                            "shared route navigation should not try to stand inside a container node");
                    villager.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void sharedRouteNavigatorRecoversAtNearestNodeAfterRouteTimeout(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        BlockPos startRel = new BlockPos(1, 2, 2);
        Villager villager = spawnVillager(helper, startRel);
        tickVillager(level, villager, 20);
        villager.moveTo(helper.absolutePos(startRel).getCenter());
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        BlockPos nearestNodeRel = new BlockPos(4, 2, 2);
        BlockPos lastNodeRel = new BlockPos(8, 2, 2);
        BlockPos nearestNode = helper.absolutePos(nearestNodeRel);

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(nearestNodeRel, lastNodeRel));
        state.putInt("RouteNodeIndex", 1);
        state.putLong("RouteLastNodeReachedGameTime", level.getGameTime() - 20L * 30L);

        HiredRouteNavigator.maintainRoute(level, villager, context, 0.5D);

        helper.assertValueEqual(state.getInt("RouteNodeIndex"), 0,
                "timed-out route navigation should re-anchor at the nearest route node");
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        helper.assertTrue(navigationTarget != null && navigationTarget.distSqr(nearestNode) <= 4.0D,
                "route recovery should immediately pathfind toward the nearest node; target=" + navigationTarget);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void courierRecoversAtNearestRouteNodeAfterRouteTimeout(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierRouteRecovery");
        BlockPos startRel = new BlockPos(1, 2, 2);
        Villager villager = spawnVillager(helper, startRel);
        tickVillager(level, villager, 20);
        villager.moveTo(helper.absolutePos(startRel).getCenter());
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        BlockPos nearestNodeRel = new BlockPos(4, 2, 2);
        BlockPos lastNodeRel = new BlockPos(8, 2, 2);
        BlockPos nearestNode = helper.absolutePos(nearestNodeRel);

        CompoundTag state = new CompoundTag();
        state.putString("CourierPhase", "outbound");
        state.putInt("CourierRouteIndex", 1);
        state.putLong("CourierRouteLastNodeReachedGameTime", level.getGameTime() - 20L * 30L);
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(nearestNodeRel, lastNodeRel));

        new CourierWorker().tick(level, villager, hirer, context);

        helper.assertValueEqual(state.getInt("CourierRouteIndex"), 0,
                "timed-out courier route navigation should re-anchor at the nearest route node");
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        helper.assertTrue(navigationTarget != null && navigationTarget.distSqr(nearestNode) <= 4.0D,
                "courier route recovery should immediately pathfind toward the nearest node; target=" + navigationTarget);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void courierSkipsInputDuringUnreachableRetryCooldown(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierInputFailure");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 2));
        BlockPos failedInputRel = new BlockPos(3, 2, 2);
        BlockPos availableInputRel = new BlockPos(6, 2, 2);
        BlockPos outputRel = new BlockPos(9, 2, 2);
        BlockPos failedInput = helper.absolutePos(failedInputRel);
        BlockPos availableInput = helper.absolutePos(availableInputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, failedInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, availableInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        container(level, failedInput).setItem(0, new ItemStack(Items.DIRT, 4));
        container(level, availableInput).setItem(0, new ItemStack(Items.COBBLESTONE, 5));

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), failedInput),
                        new AssignedStorageService.StoragePosition(level.dimension(), availableInput)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 2, "courier input assignments");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "courier output assignment");

        List<BlockPos> beforeFailure = AssignedStorageService.assignedCourierInputStoragePositionsContaining(
                level, villager, stack -> !stack.isEmpty());
        helper.assertTrue(beforeFailure.contains(failedInput) && beforeFailure.contains(availableInput),
                "both reachable inputs should initially be eligible");

        AssignedStorageService.rememberInputStorageFailure(
                level, villager, failedInput, "courier_input_unreachable");

        List<BlockPos> duringCooldown = AssignedStorageService.assignedCourierInputStoragePositionsContaining(
                level, villager, stack -> !stack.isEmpty());
        helper.assertFalse(duringCooldown.contains(failedInput),
                "an unreachable input should be skipped during its retry cooldown");
        helper.assertTrue(duringCooldown.contains(availableInput),
                "one failed input must not block other route inputs");

        AssignedStorageService.clearStorageFailure(level, villager, failedInput);
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void courierCollectsReachableInputBesideRouteNode(GameTestHelper helper) {
        buildFloor(helper, 0, 14, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierRouteStorageReach");
        BlockPos standingRel = new BlockPos(3, 2, 2);
        BlockPos routeNodeRel = new BlockPos(4, 2, 2);
        BlockPos inputRel = new BlockPos(7, 2, 2);
        BlockPos outputRel = new BlockPos(12, 2, 2);
        Villager villager = spawnVillager(helper, standingRel);
        tickVillager(level, villager, 20);
        villager.moveTo(helper.absolutePos(standingRel).getCenter());
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        BlockPos input = helper.absolutePos(inputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, inputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, input);
        AssignedStorageService.removeAssignedContainer(level, output);
        container(level, input).setItem(0, new ItemStack(Items.DIRT, 12));

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), input)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 1, "reachable route input assignment");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "reachable route output assignment");

        CompoundTag state = new CompoundTag();
        state.putString("CourierPhase", "outbound");
        state.putInt("CourierRouteIndex", 0);
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(routeNodeRel, outputRel));

        new CourierWorker().tick(level, villager, hirer, context);

        helper.assertValueEqual(countItem(container(level, input), Items.DIRT), 0,
                "courier should collect an assigned input chest it can reach from a route node");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.DIRT), 12,
                "courier should place route-collected cargo into its job inventory");
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void courierCollectsStackedDoubleChestInputsAssignedByFarHalf(GameTestHelper helper) {
        buildFloor(helper, 0, 14, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierStackedDoubleInput");
        BlockPos routeNodeRel = new BlockPos(3, 2, 2);
        BlockPos lowerNearRel = new BlockPos(6, 2, 2);
        BlockPos lowerFarRel = lowerNearRel.east();
        BlockPos upperNearRel = lowerNearRel.above();
        BlockPos upperFarRel = lowerFarRel.above();
        BlockPos outputRel = new BlockPos(12, 2, 2);
        Villager villager = spawnVillager(helper, routeNodeRel);
        tickVillager(level, villager, 20);
        villager.moveTo(helper.absolutePos(routeNodeRel).getCenter());
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);

        setBlock(helper, lowerNearRel, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT));
        setBlock(helper, lowerFarRel, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT));
        setBlock(helper, upperNearRel, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT));
        setBlock(helper, upperFarRel, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT));
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());

        BlockPos lowerNear = helper.absolutePos(lowerNearRel);
        BlockPos lowerFar = helper.absolutePos(lowerFarRel);
        BlockPos upperNear = helper.absolutePos(upperNearRel);
        BlockPos upperFar = helper.absolutePos(upperFarRel);
        BlockPos output = helper.absolutePos(outputRel);
        container(level, lowerNear).setItem(0, new ItemStack(Items.COD, 7));
        container(level, upperNear).setItem(0, new ItemStack(Items.SALMON, 9));

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), lowerFar),
                        new AssignedStorageService.StoragePosition(level.dimension(), upperFar)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 2, "far-half input assignments");
        AssignedStorageService.AssignSummary duplicateHalf = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), lowerNear)),
                AssignedStorageService.SUPPLY_PURPOSE);
        helper.assertValueEqual(duplicateHalf.assigned(), 0,
                "the connected half must not create a second assignment");
        helper.assertValueEqual(duplicateHalf.alreadyAssigned(), 1,
                "either half should resolve to the existing multiblock assignment");
        helper.assertValueEqual(AssignedStorageService.assignedStorageAt(
                        level,
                        villager,
                        List.of(new AssignedStorageService.StoragePosition(level.dimension(), upperNear))).size(),
                1,
                "assignment lookup should recognize the unrecorded connected half");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "stacked-double output assignment");

        CompoundTag state = new CompoundTag();
        state.putString("CourierPhase", "outbound");
        state.putInt("CourierRouteIndex", 0);
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(routeNodeRel, outputRel));
        CourierWorker worker = new CourierWorker();
        worker.tick(level, villager, hirer, context);
        worker.tick(level, villager, hirer, context);

        helper.assertValueEqual(countItem(container(level, lowerNear), Items.COD), 0,
                "courier should collect the nearer half of the lower assigned double chest");
        helper.assertValueEqual(countItem(container(level, upperNear), Items.SALMON), 0,
                "courier should collect the nearer half of the stacked assigned double chest");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.COD), 7,
                "lower double-chest cargo should enter the courier inventory");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.SALMON), 9,
                "upper double-chest cargo should enter the courier inventory");

        helper.assertValueEqual(AssignedStorageService.removeAssignedStorageAt(
                        level,
                        villager,
                        List.of(new AssignedStorageService.StoragePosition(level.dimension(), lowerNear))),
                1,
                "removing from either half should remove the whole-container assignment");
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 700)
    public static void courierCollectsInputsThroughTwoBranchEndpoints(GameTestHelper helper) {
        buildFloor(helper, 0, 14, 0, 14, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierBranchInputs");
        BlockPos firstNodeRel = new BlockPos(2, 2, 2);
        BlockPos lastNodeRel = new BlockPos(12, 2, 2);
        BlockPos firstAnchorRel = new BlockPos(4, 2, 2);
        BlockPos firstBranchEndRel = new BlockPos(4, 2, 6);
        BlockPos firstInputRel = new BlockPos(4, 2, 8);
        BlockPos secondAnchorRel = new BlockPos(8, 2, 2);
        BlockPos secondBranchEndRel = new BlockPos(8, 2, 10);
        BlockPos secondInputRel = new BlockPos(8, 2, 12);
        BlockPos outputRel = new BlockPos(2, 2, 4);
        Villager villager = spawnVillager(helper, firstNodeRel);
        BlockPos firstInput = helper.absolutePos(firstInputRel);
        BlockPos secondInput = helper.absolutePos(secondInputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, firstInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, secondInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        container(level, firstInput).setItem(0, new ItemStack(Items.DIRT, 7));
        container(level, secondInput).setItem(0, new ItemStack(Items.COBBLESTONE, 9));

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), firstInput),
                        new AssignedStorageService.StoragePosition(level.dimension(), secondInput)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 2, "two branch input assignments");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "branch output assignment");

        HiredRoute route = new HiredRoute(
                List.of(helper.absolutePos(firstNodeRel), helper.absolutePos(lastNodeRel)),
                false,
                List.of(
                        new HiredRoute.Branch(
                                helper.absolutePos(firstAnchorRel),
                                helper.absolutePos(firstBranchEndRel)),
                        new HiredRoute.Branch(
                                helper.absolutePos(secondAnchorRel),
                                helper.absolutePos(secondBranchEndRel))));
        helper.assertValueEqual(
                route.traversalNodes(),
                List.of(
                        helper.absolutePos(firstNodeRel),
                        helper.absolutePos(firstAnchorRel),
                        helper.absolutePos(firstBranchEndRel),
                        helper.absolutePos(firstAnchorRel),
                        helper.absolutePos(secondAnchorRel),
                        helper.absolutePos(secondBranchEndRel),
                        helper.absolutePos(secondAnchorRel),
                        helper.absolutePos(lastNodeRel)),
                "both branches should become ordinary out-and-back route nodes");

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(helper, villager, state, route, 100);
        CourierWorker worker = new CourierWorker();
        runWorkerUntil(helper, worker, level, villager, hirer, context, 600, () ->
                countItem(container(level, output), Items.DIRT) == 7
                        && countItem(container(level, output), Items.COBBLESTONE) == 9
                        && "pickup".equals(state.getString("CourierPhase")));

        helper.assertValueEqual(countItem(container(level, firstInput), Items.DIRT), 0,
                "courier should empty the first branch input");
        helper.assertValueEqual(countItem(container(level, secondInput), Items.COBBLESTONE), 0,
                "courier should empty the second branch input");
        helper.assertValueEqual(countItem(container(level, output), Items.DIRT), 7,
                "courier should deliver the first branch cargo");
        helper.assertValueEqual(countItem(container(level, output), Items.COBBLESTONE), 9,
                "courier should deliver the second branch cargo");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 20)
    public static void courierLoopTraversalIncludesClosingBranchAndDistance(GameTestHelper helper) {
        BlockPos first = new BlockPos(0, 2, 0);
        BlockPos second = new BlockPos(12, 2, 0);
        BlockPos third = new BlockPos(6, 2, 10);
        BlockPos branchAnchor = new BlockPos(3, 2, 5);
        BlockPos branchEnd = new BlockPos(0, 2, 5);
        HiredRoute route = new HiredRoute(
                List.of(first, second, third),
                true,
                List.of(new HiredRoute.Branch(branchAnchor, branchEnd)));
        List<BlockPos> expectedTraversal = List.of(
                first,
                second,
                third,
                branchAnchor,
                branchEnd,
                branchAnchor,
                first);

        helper.assertTrue(route.loop(), "the route fixture should retain its valid closing segment");
        helper.assertValueEqual(
                route.traversalNodes(),
                expectedTraversal,
                "a loop traversal should follow its closing segment and branches attached to it");

        double expectedDistance = 0.0D;
        for (int index = 1; index < expectedTraversal.size(); index++) {
            expectedDistance += Math.sqrt(
                    expectedTraversal.get(index - 1).distSqr(expectedTraversal.get(index)));
        }
        helper.assertTrue(
                Math.abs(CourierWorker.routeDistance(route) - expectedDistance) < 0.000001D,
                "courier practice distance should use the complete expanded traversal");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 700)
    public static void courierServicesOverlappingInputFromTerminalBranchOnce(GameTestHelper helper) {
        buildFloor(helper, 0, 18, 0, 18, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierTerminalBranch");
        BlockPos firstNodeRel = new BlockPos(2, 2, 2);
        BlockPos lastNodeRel = new BlockPos(16, 2, 2);
        BlockPos branchEndRel = new BlockPos(16, 2, 14);
        BlockPos inputRel = new BlockPos(16, 2, 16);
        BlockPos outputRel = new BlockPos(2, 2, 4);
        Villager villager = spawnVillager(helper, firstNodeRel);
        BlockPos input = helper.absolutePos(inputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, inputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        container(level, input).setItem(0, new ItemStack(Items.DIRT, 11));

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), input)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 1, "overlapping branch input assignment");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "terminal branch output assignment");

        BlockPos firstNode = helper.absolutePos(firstNodeRel);
        BlockPos lastNode = helper.absolutePos(lastNodeRel);
        BlockPos branchEnd = helper.absolutePos(branchEndRel);
        HiredRoute route = new HiredRoute(
                List.of(firstNode, lastNode),
                false,
                List.of(new HiredRoute.Branch(lastNode, branchEnd)));
        helper.assertValueEqual(
                route.traversalNodes(),
                List.of(firstNode, lastNode, branchEnd),
                "a terminal branch must extend the route without returning to its anchor before reversal");

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(helper, villager, state, route, 100);
        CourierWorker worker = new CourierWorker();
        runWorkerUntil(helper, worker, level, villager, hirer, context, 600, () ->
                countItem(container(level, output), Items.DIRT) == 11
                        && "pickup".equals(state.getString("CourierPhase")));

        helper.assertValueEqual(countItem(container(level, input), Items.DIRT), 0,
                "courier should collect an input overlapping the base and terminal branch ranges");
        helper.assertValueEqual(countItem(container(level, output), Items.DIRT), 11,
                "courier should deliver cargo collected from the terminal branch");
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void courierMovesInputToOutputAlongAssignedRoute(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierRoute");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 2));
        BlockPos inputRel = new BlockPos(4, 2, 2);
        BlockPos outputRel = new BlockPos(7, 2, 2);
        BlockPos input = helper.absolutePos(inputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, inputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, input);
        AssignedStorageService.removeAssignedContainer(level, output);
        container(level, input).setItem(0, new ItemStack(Items.COBBLESTONE, 20));

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), input)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 1, "courier input assignment");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "courier output assignment");

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(inputRel, outputRel));
        CourierWorker worker = new CourierWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 160, () ->
                countItem(container(level, output), Items.COBBLESTONE) == 20
                        && "pickup".equals(state.getString("CourierPhase")));

        helper.assertValueEqual(countItem(container(level, input), Items.COBBLESTONE), 0,
                "courier should collect all eligible input items into its job inventory");
        helper.assertValueEqual(countItem(container(level, output), Items.COBBLESTONE), 20, "courier output item count");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.COBBLESTONE), 0, "courier should retain no duplicate cargo");
        helper.assertValueEqual(state.getString("CourierPhase"), "pickup",
                "courier should return to the route start before waiting for the next load");
        helper.assertTrue(villager.blockPosition().distSqr(context.route().first()) <= 4.0D,
                "courier should physically return to the beginning of its route");
        helper.assertFalse(villager.blockPosition().equals(input),
                "courier should stand beside a container route node instead of trying to occupy it");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void courierLeavesUnmatchedSourceItemsUntouched(GameTestHelper helper) {
        buildFloor(helper, 0, 7, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierUnmatchedSource");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        BlockPos inputRel = new BlockPos(3, 2, 2);
        BlockPos outputRel = new BlockPos(6, 2, 2);
        BlockPos input = helper.absolutePos(inputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, inputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, input);
        AssignedStorageService.removeAssignedContainer(level, output);
        Container inputContainer = container(level, input);
        inputContainer.setItem(0, new ItemStack(Items.BEEF, 8));

        ItemFrame frame = new ItemFrame(
                level,
                output.relative(Direction.SOUTH),
                Direction.SOUTH);
        frame.setItem(new ItemStack(Items.LEATHER));
        helper.assertTrue(level.addFreshEntity(frame), "leather output frame should spawn");

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), input)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 1, "unmatched input assignment");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "filtered output assignment");

        helper.assertValueEqual(
                AssignedStorageService.courierTransferState(level, villager),
                AssignedStorageService.CourierTransferState.NO_OUTPUT_ROUTE,
                "unmatched input should have no output route");
        int moved = AssignedStorageService.transferCourierItemsAtAssignedStorage(
                villager,
                input,
                64,
                ignored -> ItemStack.EMPTY);
        helper.assertValueEqual(moved, 0, "no-output-route pickup must move nothing");
        helper.assertValueEqual(countItem(inputContainer, Items.BEEF), 8,
                "unmatched source items must remain untouched");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        frame.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 400)
    public static void courierPausesAndResumesAtFilteredOutputLimit(GameTestHelper helper) {
        buildFloor(helper, 0, 9, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierBackpressure");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 2));
        BlockPos inputRel = new BlockPos(2, 2, 2);
        BlockPos outputRel = new BlockPos(7, 2, 2);
        BlockPos input = helper.absolutePos(inputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, inputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        Container inputContainer = container(level, input);
        Container outputContainer = container(level, output);
        inputContainer.setItem(0, new ItemStack(Items.EMERALD, 16));
        outputContainer.setItem(0, new ItemStack(Items.EMERALD, 8));

        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.EMERALD));
        VillagerItemFilterData.setAmount(filter, 0, 8);
        ItemFrame frame = new ItemFrame(level, output.relative(Direction.SOUTH), Direction.SOUTH);
        frame.setItem(filter);
        helper.assertTrue(level.addFreshEntity(frame), "bounded courier output filter should spawn");

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), input)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 1, "backpressure input assignment");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "backpressure output assignment");

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(inputRel, outputRel));
        CourierWorker worker = new CourierWorker();

        worker.tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot paused = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(paused.taskState(), HiredWorkerTaskState.PAUSED_OUTPUT_BACKPRESSURE,
                "a courier should pause before collecting from a full bounded output");
        helper.assertValueEqual(countItem(inputContainer, Items.EMERALD), 16,
                "backpressure must leave upstream input untouched");
        helper.assertFalse(context.inventory().hasOutputItems(),
                "a paused courier must not retain avoidable cargo");
        helper.assertValueEqual(
                ClipboardWorkforceService.previewStatus(HiredVillagerRole.COURIER, paused, context.inventory()),
                ClipboardWorkforceSnapshot.WorkerStatus.OUTPUT_BACKPRESSURE,
                "clipboard preview should present backpressure as an informational status");

        outputContainer.removeItem(0, 1);
        runWorkerUntil(helper, worker, level, villager, hirer, context, 260, () ->
                countItem(outputContainer, Items.EMERALD) == 8
                        && countItem(inputContainer, Items.EMERALD) == 15
                        && !context.inventory().hasOutputItems()
                        && HiredWorkerBrain.snapshot(state, level.getGameTime()).taskState()
                                == HiredWorkerTaskState.PAUSED_OUTPUT_BACKPRESSURE);

        helper.assertValueEqual(countItem(inputContainer, Items.EMERALD), 15,
                "the resumed courier should collect only the downstream allowance");
        helper.assertValueEqual(countItem(outputContainer, Items.EMERALD), 8,
                "the resumed courier should restore, but never exceed, the configured stock target");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        frame.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 400)
    public static void courierRoutesCargoUsingOutputItemFrames(GameTestHelper helper) {
        buildFloor(helper, 0, 11, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierFrames");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 2));
        BlockPos inputRel = new BlockPos(2, 2, 2);
        BlockPos steakOutputRel = new BlockPos(6, 2, 2);
        BlockPos generalOutputRel = new BlockPos(10, 2, 2);
        BlockPos input = helper.absolutePos(inputRel);
        BlockPos steakOutput = helper.absolutePos(steakOutputRel);
        BlockPos generalOutput = helper.absolutePos(generalOutputRel);
        setBlock(helper, inputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, steakOutputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, generalOutputRel, Blocks.CHEST.defaultBlockState());

        ItemStack steakFilter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(steakFilter, 0, new ItemStack(Items.COOKED_BEEF));
        ItemFrame steakFrame = new ItemFrame(level, steakOutput.relative(Direction.SOUTH), Direction.SOUTH);
        steakFrame.setItem(steakFilter);
        helper.assertTrue(level.addFreshEntity(steakFrame), "steak filter item frame should spawn");

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), input)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 1, "framed courier input assignment");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), steakOutput),
                        new AssignedStorageService.StoragePosition(level.dimension(), generalOutput)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 2, "framed courier output assignments");

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(inputRel, steakOutputRel, generalOutputRel));
        helper.assertTrue(context.inventory().insertOutput(new ItemStack(Items.COOKED_BEEF, 8)).isEmpty(),
                "courier fixture should accept steak cargo");
        helper.assertTrue(context.inventory().insertOutput(new ItemStack(Items.DIRT, 6)).isEmpty(),
                "courier fixture should accept fallback cargo");
        state.putString("CourierPhase", "deliver");

        CourierWorker worker = new CourierWorker();
        runWorkerUntil(helper, worker, level, villager, hirer, context, 900, () ->
                countItem(container(level, steakOutput), Items.COOKED_BEEF) == 8
                        && countItem(container(level, generalOutput), Items.DIRT) == 6);

        helper.assertValueEqual(countItem(container(level, steakOutput), Items.COOKED_BEEF), 8,
                "matching steak should use the framed output");
        helper.assertValueEqual(countItem(container(level, steakOutput), Items.DIRT), 0,
                "non-matching cargo must not enter the framed output");
        helper.assertValueEqual(countItem(container(level, generalOutput), Items.DIRT), 6,
                "non-matching cargo should continue to another output");
        helper.assertValueEqual(countItem(container(level, generalOutput), Items.COOKED_BEEF), 0,
                "a matching framed output should take priority over an unframed output");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        steakFrame.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void courierOutputItemFrameRespectsDenylistFilter(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierDenyFilter");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        BlockPos outputRel = new BlockPos(5, 2, 2);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());

        ItemStack denylist = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setMode(denylist, VillagerItemFilterData.Mode.DENYLIST);
        VillagerItemFilterData.setEntry(denylist, 0, new ItemStack(Items.DIRT));
        ItemFrame frame = new ItemFrame(level, output.relative(Direction.SOUTH), Direction.SOUTH);
        frame.setItem(denylist);
        helper.assertTrue(level.addFreshEntity(frame), "denylist filter item frame should spawn");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "denylist courier output assignment");

        helper.assertFalse(AssignedStorageService.courierOutputStorageAccepts(
                        level, villager, output, new ItemStack(Items.DIRT)),
                "denylist item frames should reject listed cargo");
        helper.assertTrue(AssignedStorageService.courierOutputStorageAccepts(
                        level, villager, output, new ItemStack(Items.DIAMOND)),
                "denylist item frames should accept unlisted cargo");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        frame.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void courierItemFrameFilterCoversBothHalvesOfDoubleChest(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierDoubleFrame");
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

        ItemFrame frame = new ItemFrame(level, right.relative(Direction.SOUTH), Direction.SOUTH);
        frame.setItem(new ItemStack(Items.DIAMOND));
        helper.assertTrue(level.addFreshEntity(frame), "double-chest item frame should spawn");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), left)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "double chest output assignment");

        helper.assertTrue(AssignedStorageService.courierOutputStorageAccepts(
                        level, villager, left, new ItemStack(Items.DIAMOND)),
                "a frame on either double-chest half should filter the combined container");
        helper.assertFalse(AssignedStorageService.courierOutputStorageAccepts(
                        level, villager, left, new ItemStack(Items.DIRT)),
                "the connected chest half must reject non-matching courier cargo");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        frame.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void courierContinuesPastEmptyRouteNodeInSameTick(GameTestHelper helper) {
        buildFloor(helper, 0, 12, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierEmptyNode");
        BlockPos firstNodeRel = new BlockPos(2, 2, 2);
        BlockPos middleNodeRel = new BlockPos(6, 2, 2);
        BlockPos lastNodeRel = new BlockPos(10, 2, 2);
        Villager villager = spawnVillager(helper, firstNodeRel);
        BlockPos inputRel = new BlockPos(10, 2, 5);
        BlockPos input = helper.absolutePos(inputRel);
        setBlock(helper, inputRel, Blocks.CHEST.defaultBlockState());
        container(level, input).setItem(0, new ItemStack(Items.COBBLESTONE));
        AssignedStorageService.removeAssignedContainer(level, input);

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), input)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 1, "empty-node courier input assignment");

        CompoundTag state = new CompoundTag();
        state.putString("CourierPhase", "outbound");
        state.putInt("CourierRouteIndex", 0);
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(firstNodeRel, middleNodeRel, lastNodeRel));

        new CourierWorker().tick(level, villager, hirer, context);

        helper.assertValueEqual(state.getInt("CourierRouteIndex"), 1,
                "courier should advance past an empty route node immediately");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 500)
    public static void courierBatchesContainersTetheredToTheSameRouteNode(GameTestHelper helper) {
        buildFloor(helper, 0, 14, 0, 10, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierNodeBatch");
        BlockPos firstNodeRel = new BlockPos(3, 2, 3);
        BlockPos lastNodeRel = new BlockPos(12, 2, 3);
        Villager villager = spawnVillager(helper, firstNodeRel);
        BlockPos firstInputRel = new BlockPos(3, 2, 6);
        BlockPos secondInputRel = new BlockPos(6, 2, 6);
        BlockPos outputRel = new BlockPos(12, 2, 6);
        BlockPos firstInput = helper.absolutePos(firstInputRel);
        BlockPos secondInput = helper.absolutePos(secondInputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, firstInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, secondInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, firstInput);
        AssignedStorageService.removeAssignedContainer(level, secondInput);
        AssignedStorageService.removeAssignedContainer(level, output);
        container(level, firstInput).setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        container(level, secondInput).setItem(0, new ItemStack(Items.DIRT, 6));

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), firstInput),
                        new AssignedStorageService.StoragePosition(level.dimension(), secondInput)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 2, "same-node courier input assignments");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "same-node courier output assignment");

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(firstNodeRel, lastNodeRel));
        CourierWorker worker = new CourierWorker();
        boolean[] sawDirectContainerHandoff = {false};
        boolean[] sawPrematureNodeReturn = {false};

        runWorkerUntil(helper, worker, level, villager, hirer, context, 400, () -> {
            if (AssignedStorageService.SUPPLY_PURPOSE.equals(state.getString("CourierStoragePurpose"))
                    && state.getLongArray("CourierVisitedStorage").length == 1) {
                if (state.getBoolean("CourierStorageReturnToNode")) {
                    sawPrematureNodeReturn[0] = true;
                } else if (state.contains("CourierStorageTarget", Tag.TAG_LONG)) {
                    sawDirectContainerHandoff[0] = true;
                }
            }
            return countItem(container(level, output), Items.COBBLESTONE) == 8
                    && countItem(container(level, output), Items.DIRT) == 6
                    && "pickup".equals(state.getString("CourierPhase"));
        });

        helper.assertTrue(sawDirectContainerHandoff[0],
                "courier should move directly between containers tethered to one route node");
        helper.assertFalse(sawPrematureNodeReturn[0],
                "courier should not return to the route node until its container batch is complete");
        helper.assertValueEqual(countItem(container(level, firstInput), Items.COBBLESTONE), 0,
                "courier should collect the first same-node input");
        helper.assertValueEqual(countItem(container(level, secondInput), Items.DIRT), 0,
                "courier should collect the second same-node input");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 500)
    public static void courierRetriesOutputSweepFromRouteStart(GameTestHelper helper) {
        buildFloor(helper, 0, 14, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierOutputRetry");
        BlockPos firstNodeRel = new BlockPos(2, 2, 2);
        BlockPos outputRel = new BlockPos(3, 2, 2);
        BlockPos lastNodeRel = new BlockPos(12, 2, 2);
        Villager villager = spawnVillager(helper, firstNodeRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, output);
        Container outputContainer = container(level, output);
        for (int slot = 0; slot < outputContainer.getContainerSize(); slot++) {
            outputContainer.setItem(slot, new ItemStack(Items.STONE, 64));
        }

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "courier retry output assignment");

        CompoundTag state = new CompoundTag();
        state.putString("CourierPhase", "deliver");
        state.putInt("CourierRouteIndex", 0);
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(firstNodeRel, new BlockPos(7, 2, 2), lastNodeRel));
        helper.assertTrue(
                context.inventory().insertOutput(new ItemStack(Items.COBBLESTONE, 12)).isEmpty(),
                "courier retry cargo should fit");
        CourierWorker worker = new CourierWorker();

        worker.tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot paused = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(paused.taskState(), HiredWorkerTaskState.PAUSED_OUTPUT_BACKPRESSURE,
                "a courier with retained cargo should pause when every matching output is full");
        helper.assertFalse(state.contains("WorkerFailureReason", Tag.TAG_STRING),
                "output backpressure should not be reported as a worker failure");
        helper.assertValueEqual(
                ClipboardWorkforceService.previewStatus(HiredVillagerRole.COURIER, paused, context.inventory()),
                ClipboardWorkforceSnapshot.WorkerStatus.OUTPUT_BACKPRESSURE,
                "clipboard preview should report a normal output-capacity pause");

        outputContainer.clearContent();
        runWorkerUntil(helper, worker, level, villager, hirer, context, 180, () ->
                countItem(outputContainer, Items.COBBLESTONE) == 12);

        helper.assertFalse(context.inventory().hasOutputItems(),
                "courier should deliver retained cargo after output storage becomes available");
        helper.assertFalse(state.contains("WorkerFailureReason", Tag.TAG_STRING),
                "successful backpressure recovery should remain failure-free");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 1000)
    public static void courierResumesBranchInputsWhenCarriedCargoMatchesNoOutput(GameTestHelper helper) {
        buildFloor(helper, 0, 20, 0, 18, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierUnmatchedCargo");
        BlockPos firstNodeRel = new BlockPos(2, 2, 2);
        BlockPos branchAnchorRel = new BlockPos(16, 2, 2);
        BlockPos branchEndRel = new BlockPos(16, 2, 12);
        BlockPos inputRel = new BlockPos(16, 2, 14);
        BlockPos leatherOutputRel = new BlockPos(2, 2, 5);
        BlockPos muttonOutputRel = new BlockPos(5, 2, 5);
        Villager villager = spawnVillager(helper, firstNodeRel);
        BlockPos input = helper.absolutePos(inputRel);
        BlockPos leatherOutput = helper.absolutePos(leatherOutputRel);
        BlockPos muttonOutput = helper.absolutePos(muttonOutputRel);
        setBlock(helper, inputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, leatherOutputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, muttonOutputRel, Blocks.CHEST.defaultBlockState());
        container(level, input).setItem(0, new ItemStack(Items.LEATHER, 6));
        container(level, input).setItem(1, new ItemStack(Items.BEEF, 8));

        ItemFrame leatherFrame = new ItemFrame(
                level,
                leatherOutput.relative(Direction.SOUTH),
                Direction.SOUTH);
        leatherFrame.setItem(new ItemStack(Items.LEATHER));
        helper.assertTrue(level.addFreshEntity(leatherFrame), "leather output frame should spawn");
        ItemFrame cookedMuttonFrame = new ItemFrame(
                level,
                muttonOutput.relative(Direction.SOUTH),
                Direction.SOUTH);
        cookedMuttonFrame.setItem(new ItemStack(Items.COOKED_MUTTON));
        helper.assertTrue(level.addFreshEntity(cookedMuttonFrame), "cooked-mutton output frame should spawn");

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), input)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 1, "unmatched-cargo branch input");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), leatherOutput),
                        new AssignedStorageService.StoragePosition(level.dimension(), muttonOutput)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 2, "filtered unmatched-cargo outputs");

        HiredRoute route = new HiredRoute(
                List.of(helper.absolutePos(firstNodeRel), helper.absolutePos(branchAnchorRel)),
                false,
                List.of(new HiredRoute.Branch(
                        helper.absolutePos(branchAnchorRel),
                        helper.absolutePos(branchEndRel))));
        CompoundTag state = new CompoundTag();
        state.putString("CourierPhase", "deliver");
        state.putInt("CourierRouteIndex", 0);
        HiredWorkContext context = routeContext(helper, villager, state, route, 100);
        helper.assertTrue(context.inventory().insertOutput(new ItemStack(Items.MUTTON, 7)).isEmpty(),
                "raw mutton should seed the unmatched retained cargo");
        CourierWorker worker = new CourierWorker();
        boolean[] resumedInputSweep = {false};

        runWorkerUntil(helper, worker, level, villager, hirer, context, 900, () -> {
            if ("outbound".equals(state.getString("CourierPhase"))
                    && context.inventory().hasOutput(stack -> stack.is(Items.MUTTON))) {
                resumedInputSweep[0] = true;
            }
            return countItem(container(level, leatherOutput), Items.LEATHER) == 6;
        });

        helper.assertTrue(resumedInputSweep[0],
                "an unmatched delivery sweep should return to collecting branch inputs");
        helper.assertValueEqual(countItem(container(level, input), Items.LEATHER), 0,
                "retained unmatched cargo must not block leather pickup");
        helper.assertValueEqual(countItem(container(level, input), Items.BEEF), 8,
                "an unmatched source variant must remain in its input container");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.BEEF), 0,
                "an unmatched source variant must not become courier cargo");
        helper.assertValueEqual(countItem(container(level, leatherOutput), Items.LEATHER), 6,
                "newly collected leather should still reach its matching output");
        helper.assertValueEqual(countItem(container(level, muttonOutput), Items.MUTTON), 0,
                "raw mutton must not enter a cooked-mutton filtered output");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.MUTTON), 7,
                "unmatched raw mutton should remain retained until a compatible output exists");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        leatherFrame.discard();
        cookedMuttonFrame.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 500)
    public static void courierAppliesSkillScaledCapacityPerInputContainer(GameTestHelper helper) {
        buildFloor(helper, 0, 12, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierCargoLimit");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 2));
        BlockPos firstInputRel = new BlockPos(4, 2, 2);
        BlockPos secondInputRel = new BlockPos(7, 2, 2);
        BlockPos outputRel = new BlockPos(10, 2, 2);
        BlockPos firstInput = helper.absolutePos(firstInputRel);
        BlockPos secondInput = helper.absolutePos(secondInputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, firstInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, secondInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, firstInput);
        AssignedStorageService.removeAssignedContainer(level, secondInput);
        AssignedStorageService.removeAssignedContainer(level, output);
        container(level, firstInput).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        container(level, firstInput).setItem(1, new ItemStack(Items.COBBLESTONE, 36));
        container(level, secondInput).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        container(level, secondInput).setItem(1, new ItemStack(Items.COBBLESTONE, 36));

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), firstInput),
                        new AssignedStorageService.StoragePosition(level.dimension(), secondInput)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 2, "courier input assignments");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "courier output assignment");

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(firstInputRel, secondInputRel, outputRel));
        CourierWorker worker = new CourierWorker();
        runWorkerUntil(helper, worker, level, villager, hirer, context, 400, () ->
                countItem(container(level, output), Items.COBBLESTONE) == 192
                        && "pickup".equals(state.getString("CourierPhase")));

        helper.assertValueEqual(countItem(container(level, firstInput), Items.COBBLESTONE), 4,
                "mid-skill courier should take 96 items from the first input");
        helper.assertValueEqual(countItem(container(level, secondInput), Items.COBBLESTONE), 4,
                "mid-skill courier should independently take 96 items from the second input");
        helper.assertValueEqual(countItem(container(level, output), Items.COBBLESTONE), 192,
                "each assigned input should receive its own skill-scaled pickup allowance");
        helper.assertFalse(context.inventory().hasOutputItems(),
                "courier should finish the route without retained cargo");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void courierPatrolsRouteWhileInputsAreEmpty(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierEmptyInput");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 2));
        BlockPos firstInputRel = new BlockPos(2, 2, 2);
        BlockPos secondInputRel = new BlockPos(6, 2, 2);
        BlockPos outputRel = new BlockPos(9, 2, 2);
        BlockPos firstInput = helper.absolutePos(firstInputRel);
        BlockPos secondInput = helper.absolutePos(secondInputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, firstInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, secondInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, firstInput);
        AssignedStorageService.removeAssignedContainer(level, secondInput);
        AssignedStorageService.removeAssignedContainer(level, output);

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), firstInput),
                        new AssignedStorageService.StoragePosition(level.dimension(), secondInput)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 2, "courier empty-input assignments");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "courier output assignment");

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(firstInputRel, secondInputRel, outputRel));
        CourierWorker worker = new CourierWorker();

        WorkResult emptyInputResult = worker.tick(level, villager, hirer, context);
        helper.assertValueEqual(emptyInputResult.status(),
                "interaction.work.courier.following_route_outbound",
                "courier should begin its route when every input is empty");
        helper.assertValueEqual(state.getString("CourierPhase"), "outbound",
                "empty input should not leave the courier waiting in pickup phase");

        container(level, secondInput).setItem(0, new ItemStack(Items.DIRT, 12));
        runWorkerUntil(helper, worker, level, villager, hirer, context, 240, () ->
                countItem(container(level, output), Items.DIRT) == 12);

        helper.assertValueEqual(countItem(container(level, firstInput), Items.DIRT), 0,
                "empty first input should not stop the courier");
        helper.assertValueEqual(countItem(container(level, secondInput), Items.DIRT), 0,
                "courier should collect cargo encountered later on the route");
        helper.assertValueEqual(countItem(container(level, output), Items.DIRT), 12,
                "courier should deliver cargo collected after an empty input");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 1200)
    public static void courierDetoursToMultipleInputsAndRetracesRoute(GameTestHelper helper) {
        buildFloor(helper, 0, 18, 0, 20, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierMultiInput");
        BlockPos firstNodeRel = new BlockPos(2, 2, 2);
        BlockPos secondNodeRel = new BlockPos(8, 2, 2);
        BlockPos lastNodeRel = new BlockPos(16, 2, 2);
        Villager villager = spawnVillager(helper, firstNodeRel);
        BlockPos firstInputRel = new BlockPos(8, 2, 6);
        BlockPos secondInputRel = new BlockPos(16, 2, 18);
        BlockPos outputRel = new BlockPos(2, 2, 4);
        BlockPos firstInput = helper.absolutePos(firstInputRel);
        BlockPos secondInput = helper.absolutePos(secondInputRel);
        BlockPos output = helper.absolutePos(outputRel);
        setBlock(helper, firstInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, secondInputRel, Blocks.CHEST.defaultBlockState());
        setBlock(helper, outputRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, firstInput);
        AssignedStorageService.removeAssignedContainer(level, secondInput);
        AssignedStorageService.removeAssignedContainer(level, output);
        container(level, firstInput).setItem(0, new ItemStack(Items.COBBLESTONE, 20));
        container(level, secondInput).setItem(0, new ItemStack(Items.DIRT, 13));

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), firstInput),
                        new AssignedStorageService.StoragePosition(level.dimension(), secondInput)),
                AssignedStorageService.SUPPLY_PURPOSE).assigned(), 2, "courier multi-input assignment");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), output)),
                AssignedStorageService.OUTPUT_PURPOSE).assigned(), 1, "courier output assignment");

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(
                helper,
                villager,
                state,
                List.of(firstNodeRel, secondNodeRel, lastNodeRel));
        CourierWorker worker = new CourierWorker();
        boolean[] sawMiddleReturnNode = {false};

        runWorkerUntil(helper, worker, level, villager, hirer, context, 1000, () -> {
            if ("return".equals(state.getString("CourierPhase"))
                    && state.getInt("CourierRouteIndex") == 1) {
                sawMiddleReturnNode[0] = true;
            }
            return countItem(container(level, output), Items.COBBLESTONE) == 20
                    && countItem(container(level, output), Items.DIRT) == 13
                    && "pickup".equals(state.getString("CourierPhase"));
        });

        helper.assertTrue(sawMiddleReturnNode[0],
                "courier should revisit the middle route node while retracing its return path");
        helper.assertValueEqual(countItem(container(level, firstInput), Items.COBBLESTONE), 0,
                "courier should empty the first route input");
        helper.assertValueEqual(countItem(container(level, secondInput), Items.DIRT), 0,
                "courier should visit the second input container route node");

        container(level, firstInput).setItem(0, new ItemStack(Items.COBBLESTONE, 7));
        container(level, secondInput).setItem(0, new ItemStack(Items.DIRT, 5));
        runWorkerUntil(helper, worker, level, villager, hirer, context, 1000, () ->
                countItem(container(level, output), Items.COBBLESTONE) == 27
                        && countItem(container(level, output), Items.DIRT) == 18
                        && "pickup".equals(state.getString("CourierPhase")));

        helper.assertValueEqual(countItem(container(level, output), Items.COBBLESTONE), 27,
                "courier should repeat the route for newly added first-input items");
        helper.assertValueEqual(countItem(container(level, output), Items.DIRT), 18,
                "courier should repeat the route for newly added later-input items");
        helper.assertFalse(context.inventory().hasOutputItems(),
                "courier should deposit every carried stack before returning");
        helper.assertTrue(villager.blockPosition().distSqr(context.route().first()) <= 4.0D,
                "courier should await the next load at the route beginning");
        helper.assertFalse(villager.blockPosition().equals(output),
                "courier should return from the output detour to the first route node");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void paymentStorageCanBeSharedByMultipleVillagers(GameTestHelper helper) {
        buildFloor(helper, 0, 5, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrSharedPayment");
        Villager first = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager second = spawnVillager(helper, new BlockPos(1, 2, 3));
        BlockPos paymentRel = new BlockPos(3, 2, 2);
        BlockPos payment = helper.absolutePos(paymentRel);
        setBlock(helper, paymentRel, VillagerRetaliationBlocks.PAYMENT_BOX.get().defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, payment);
        AssignedStorageService.StoragePosition storage = new AssignedStorageService.StoragePosition(level.dimension(), payment);

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer, first, List.of(storage), AssignedStorageService.PAYMENT_PURPOSE).assigned(), 1,
                "first villager shared payment assignment");
        helper.assertValueEqual(AssignedStorageService.assign(
                hirer, second, List.of(storage), AssignedStorageService.PAYMENT_PURPOSE).assigned(), 1,
                "second villager shared payment assignment");
        helper.assertValueEqual(AssignedStorageService.assignedPaymentStorage(level, first).size(), 1,
                "first villager should retain payment assignment");
        helper.assertValueEqual(AssignedStorageService.assignedPaymentStorage(level, second).size(), 1,
                "second villager should retain payment assignment");

        container(level, payment).setItem(0, new ItemStack(Items.EMERALD, 12));
        helper.assertValueEqual(
                AssignedStorageService.consumePaymentItems(first, stack -> stack.is(Items.EMERALD), 4),
                4,
                "first villager should pull payment from shared box");
        helper.assertValueEqual(
                AssignedStorageService.consumePaymentItems(second, stack -> stack.is(Items.EMERALD), 4),
                4,
                "second villager should also pull payment from shared box");
        helper.assertValueEqual(
                countItem(container(level, payment), Items.EMERALD),
                4,
                "shared payment pulls should consume each villager's payment exactly once");

        AssignedStorageService.removeAllAssignedStorage(level, first);
        helper.assertValueEqual(AssignedStorageService.assignedPaymentStorage(level, second).size(), 1,
                "removing first villager should preserve shared payment assignment");
        AssignedStorageService.removeAllAssignedStorage(level, second);
        first.discard();
        second.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerFindsCropsAboveAssignedFarmlandLayerWithoutJobSite(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingLayer");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos cropRel = new BlockPos(3, 2, 2);
        setBlock(helper, cropRel.below(), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cropRel, ((CropBlock) Blocks.WHEAT).getStateForAge(7));

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, cropRel.below(), cropRel.below(), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        context.inventory().insertPlainSupply(new ItemStack(Items.WHEAT_SEEDS));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        BlockState crop = level.getBlockState(helper.absolutePos(cropRel));
        helper.assertTrue(crop.is(Blocks.WHEAT), "farmer should replant the crop above an assigned farmland-layer area");
        helper.assertValueEqual(crop.getValue(CropBlock.AGE), 0, "replanted crop should be immature");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.WHEAT)), "soil-layer crop harvest should store output");
        helper.assertValueEqual(result.status(), "interaction.work.farming.completed_crop", "soil-layer crop completion status");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerPlantsEmptyFarmlandFromJobInventory(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingPlant");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos cropRel = new BlockPos(3, 2, 2);
        setBlock(helper, cropRel.below(), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cropRel, Blocks.AIR.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, cropRel.below(), cropRel.below(), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        context.inventory().insertPlainSupply(new ItemStack(Items.WHEAT_SEEDS));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        BlockState crop = level.getBlockState(helper.absolutePos(cropRel));
        helper.assertTrue(crop.is(Blocks.WHEAT), "farmer should plant wheat above assigned empty farmland");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.WHEAT_SEEDS), 0, "planting should consume one job seed");
        helper.assertValueEqual(result.status(), "interaction.work.farming.tending_fields", "planting status");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerKeepsHarvestingBeforeDepositingPartialOutput(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingBatch");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos chestRel = new BlockPos(2, 2, 3);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        BlockPos cropRel = new BlockPos(3, 2, 2);
        setBlock(helper, cropRel.below(), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cropRel, ((CropBlock) Blocks.WHEAT).getStateForAge(7));

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        context.inventory().insertPlainSupply(new ItemStack(Items.WHEAT_SEEDS));
        context.inventory().insertPlainOutput(new ItemStack(Items.WHEAT, 3));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        BlockState crop = level.getBlockState(helper.absolutePos(cropRel));
        helper.assertValueEqual(result.status(), "interaction.work.farming.completed_crop", "farmer should keep harvesting before storage");
        helper.assertTrue(crop.is(Blocks.WHEAT), "farmer should harvest and replant the next ready crop");
        helper.assertValueEqual(countItem(container(level, chest), Items.WHEAT), 0, "farmer should not deposit while more crops are ready");
        helper.assertTrue(countInventoryItem(context.inventory(), Items.WHEAT) > 3, "new harvest should stay in job output");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerGuidesVanillaFarmerToDistantReadyCrop(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingGuide");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos composterRel = new BlockPos(3, 1, 3);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        BlockPos cropRel = new BlockPos(8, 2, 3);
        setBlock(helper, cropRel.below(), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cropRel, ((CropBlock) Blocks.WHEAT).getStateForAge(7));

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(9, 4, 5), false);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        BlockPos crop = helper.absolutePos(cropRel);
        helper.assertValueEqual(result.status(), "interaction.work.farming.moving_to_crop", "field guide status");
        helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .map(walkTarget -> crop.equals(walkTarget.getTarget().currentBlockPosition()))
                        .orElse(false),
                "farmer should be guided to the distant ready crop");
        helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.SECONDARY_JOB_SITE)
                        .map(sites -> sites.stream().anyMatch(site -> site.pos().equals(crop.below())))
                        .orElse(false),
                "guided crop should seed vanilla secondary farmland memory");
        helper.assertTrue(level.getBlockState(crop).getBlock() == Blocks.WHEAT,
                "guide should leave actual harvesting to vanilla");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerStartsHiredNavigationToAssignedCropWithoutJobSite(GameTestHelper helper) {
        HiredPathMemory.clear();
        buildFloor(helper, 0, 10, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingNoSiteCrop");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos cropRel = new BlockPos(7, 2, 3);
        BlockPos crop = helper.absolutePos(cropRel);
        setBlock(helper, cropRel.below(), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cropRel, ((CropBlock) Blocks.WHEAT).getStateForAge(7));

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(9, 4, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);

        helper.assertValueEqual(result.status(), "interaction.work.farming.moving_to_crop", "assigned crop navigation status");
        helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .map(walkTarget -> crop.equals(walkTarget.getTarget().currentBlockPosition()))
                        .orElse(false),
                "farmer should expose the crop as the active hired walk target");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerStartsHiredNavigationToAssignedDirtWithoutJobSite(GameTestHelper helper) {
        HiredPathMemory.clear();
        buildFloor(helper, 0, 10, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingNoSiteDirt");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos dirtRel = new BlockPos(7, 1, 3);
        BlockPos standTarget = helper.absolutePos(dirtRel.above());
        setBlock(helper, dirtRel, Blocks.DIRT.defaultBlockState());
        setBlock(helper, dirtRel.above(), Blocks.AIR.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredFarmingOptions.initializeDefaults(state);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 1, 1), new BlockPos(9, 3, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);

        helper.assertValueEqual(result.status(), "interaction.work.farming.moving_to_soil", "assigned dirt navigation status");
        helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .map(walkTarget -> standTarget.equals(walkTarget.getTarget().currentBlockPosition()))
                        .orElse(false),
                "farmer should expose the soil stand target as the active hired walk target");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerPrioritizesReadyCropOverNearbyEmptyFarmland(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingHarvestFirst");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos composterRel = new BlockPos(3, 1, 3);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        BlockPos emptyFarmlandRel = new BlockPos(4, 2, 3);
        setBlock(helper, emptyFarmlandRel.below(), Blocks.FARMLAND.defaultBlockState());
        BlockPos cropRel = new BlockPos(8, 2, 3);
        setBlock(helper, cropRel.below(), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cropRel, ((CropBlock) Blocks.WHEAT).getStateForAge(7));

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(9, 4, 5), false);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        helper.assertFalse(villager.hasFarmSeeds(), "fixture farmer should not be able to plant the empty farmland");

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        BlockPos crop = helper.absolutePos(cropRel);
        BlockPos emptyFarmland = helper.absolutePos(emptyFarmlandRel);
        helper.assertValueEqual(result.status(), "interaction.work.farming.moving_to_crop", "ready crop should win field guide priority");
        helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .map(walkTarget -> crop.equals(walkTarget.getTarget().currentBlockPosition()))
                        .orElse(false),
                "farmer should walk to the distant ready crop before empty farmland");
        helper.assertFalse(
                villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .map(walkTarget -> emptyFarmland.equals(walkTarget.getTarget().currentBlockPosition()))
                        .orElse(false),
                "nearby empty farmland should not steal harvest focus");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerTillsDirtInAssignedAreaByDefault(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingTillDefault");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos composterRel = new BlockPos(2, 1, 2);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        BlockPos dirtRel = new BlockPos(3, 1, 2);
        setBlock(helper, dirtRel, Blocks.DIRT.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredFarmingOptions.initializeDefaults(state);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        BlockPos dirt = helper.absolutePos(dirtRel);
        helper.assertValueEqual(result.status(), "interaction.work.farming.tilled_soil", "default farming soil preparation status");
        helper.assertTrue(level.getBlockState(dirt).is(Blocks.FARMLAND), "farmer should till dirt under the assigned field area");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerLeavesDirtWhenTillingDisabled(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingTillOff");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos composterRel = new BlockPos(2, 1, 2);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        BlockPos dirtRel = new BlockPos(3, 1, 2);
        setBlock(helper, dirtRel, Blocks.DIRT.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredFarmingOptions.initializeDefaults(state);
        state.putBoolean(HiredFarmingOptions.TILL_SOIL_TAG, false);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        BlockPos dirt = helper.absolutePos(dirtRel);
        helper.assertValueEqual(result.status(), "interaction.work.farming.waiting_for_growth", "disabled tilling should leave farmer idle");
        helper.assertTrue(level.getBlockState(dirt).is(Blocks.DIRT), "disabled tilling option should leave dirt unchanged");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerClipboardShowsWaitingForGrowingCrops(GameTestHelper helper) {
        HiredVillagerIndex.clearRuntimeState();
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingCropWait");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos composterRel = new BlockPos(2, 1, 2);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        BlockPos cropRel = new BlockPos(3, 2, 2);
        setBlock(helper, cropRel.below(), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cropRel, ((CropBlock) Blocks.WHEAT).getStateForAge(3));
        BlockPos chestRel = new BlockPos(5, 2, 4);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);

        CompoundTag state = new CompoundTag();
        HiredFarmingOptions.initializeDefaults(state);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot workerState = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertValueEqual(result.status(), "interaction.work.farming.waiting_for_growth", "growing crops should leave farmer waiting");
        helper.assertValueEqual(
                workerState.lastTargetScanResult(),
                "field_scan_full_waiting_for_crops",
                "immature crop scan should not look like a generic no-target scan");

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.FARMING),
                "farmer role should be available for clipboard crop-wait test");
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.GENERAL_PURPOSE);
        HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.IDLE, null);
        HiredWorkerBrain.setLastTargetScanResult(session.context(), workerState.lastTargetScanResult());
        HiredVillagerIndex.update(level, villager);

        ClipboardWorkforceSnapshot snapshot = ClipboardWorkforceService.snapshot(hirer);
        helper.assertValueEqual(snapshot.workers().size(), 1, "clipboard worker rows");
        ClipboardWorkforceSnapshot.WorkerRow row = snapshot.workers().getFirst();
        helper.assertValueEqual(
                row.status(),
                ClipboardWorkforceSnapshot.WorkerStatus.WAITING_FOR_CROPS,
                "clipboard should show the crop-waiting status");
        helper.assertFalse(row.noTargets(), "waiting for growing crops should not count as a no-target warning");

        HiredWorkerBrain.setLastTargetScanResult(session.context(), "field_scan_full_no_targets");
        ClipboardWorkforceSnapshot noCropSnapshot = ClipboardWorkforceService.snapshot(hirer);
        ClipboardWorkforceSnapshot.WorkerRow noCropRow = noCropSnapshot.workers().getFirst();
        helper.assertValueEqual(
                noCropRow.status(),
                ClipboardWorkforceSnapshot.WorkerStatus.NO_TARGETS,
                "clipboard should still show no targets when no crops are growing");
        helper.assertTrue(noCropRow.noTargets(), "empty fields should keep the no-target warning");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        HiredVillagerIndex.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerPrioritizesReadyCropOverTillingSoil(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingCropBeforeDirt");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos composterRel = new BlockPos(3, 1, 3);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        BlockPos dirtRel = new BlockPos(4, 1, 3);
        setBlock(helper, dirtRel, Blocks.DIRT.defaultBlockState());
        BlockPos cropRel = new BlockPos(8, 2, 3);
        setBlock(helper, cropRel.below(), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cropRel, ((CropBlock) Blocks.WHEAT).getStateForAge(7));

        CompoundTag state = new CompoundTag();
        HiredFarmingOptions.initializeDefaults(state);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(9, 4, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        WorkResult result = new FarmingWorker().tick(level, villager, hirer, context);
        BlockPos crop = helper.absolutePos(cropRel);
        BlockPos dirt = helper.absolutePos(dirtRel);
        helper.assertValueEqual(result.status(), "interaction.work.farming.moving_to_crop", "ready crop should win before tilling soil");
        helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .map(walkTarget -> crop.equals(walkTarget.getTarget().currentBlockPosition()))
                        .orElse(false),
                "farmer should walk to the ready crop before preparing dirt");
        helper.assertTrue(level.getBlockState(dirt).is(Blocks.DIRT), "ready crop priority should leave dirt untouched for this tick");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerTransfersVanillaHarvestsToJobInventory(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingOutputSweep");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        BlockPos composterRel = new BlockPos(2, 1, 2);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        HiredJobInventory markerInventory = HiredJobInventory.getJobInventory(villager);
        helper.assertTrue(markerInventory.insertOutput(new ItemStack(Items.WHEAT, 12)).isEmpty(), "fixture output should fit");
        ItemStack pollutedWheat = markerInventory.getItem(HiredJobInventory.MAIN_GRID_START).copy();
        helper.assertTrue(HiredJobInventory.isJobItem(pollutedWheat), "fixture wheat should start with legacy job metadata");
        markerInventory.setItem(HiredJobInventory.MAIN_GRID_START, ItemStack.EMPTY);
        villager.getInventory().setItem(0, pollutedWheat);

        CompoundTag state = new CompoundTag();
        HiredFarmingOptions.initializeDefaults(state);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        new FarmingWorker().tick(level, villager, hirer, context);
        helper.assertValueEqual(countItem(villager.getInventory(), Items.WHEAT), 0, "personal wheat should be swept from vanilla inventory");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.WHEAT), 12, "harvested wheat should move into job inventory output");
        helper.assertFalse(
                HiredJobInventory.isJobItem(context.inventory().getItem(HiredJobInventory.MAIN_GRID_START)),
                "harvested wheat should remain a vanilla-clean item stack");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void hiredFarmerVanillaPickupRoutesCropDropsToJobInventory(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingPickup");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.FARMING),
                "farmer role should be active for pickup routing");
        BlockPos composterRel = new BlockPos(2, 1, 2);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        session.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        BlockPos itemPos = helper.absolutePos(new BlockPos(2, 2, 2));
        ItemEntity cropDrop = new ItemEntity(level, itemPos.getX() + 0.5D, itemPos.getY(), itemPos.getZ() + 0.5D, new ItemStack(Items.WHEAT, 5));
        cropDrop.setNoPickUpDelay();
        level.addFreshEntity(cropDrop);
        helper.assertTrue(villager.wantsToPickUp(cropDrop.getItem()), "hired farmer should accept farm drops through the job pickup route");
        helper.assertFalse(
                villager.wantsToPickUp(new ItemStack(Items.COBBLESTONE)),
                "hired farmer should not accept unrelated drops into personal inventory while working");
        helper.assertTrue(
                HiredFarmingInventoryBridge.capturePickup(level, villager, cropDrop),
                "hired farmer pickup should be captured before vanilla inventory insertion");

        HiredJobInventory pickupInventory = HiredJobInventory.getJobInventory(villager);
        helper.assertTrue(cropDrop.isRemoved(), "hired farmer should pick up the whole wheat stack");
        helper.assertValueEqual(countItem(villager.getInventory(), Items.WHEAT), 0, "hired farmer pickup should not touch personal wheat");
        helper.assertValueEqual(countInventoryItem(pickupInventory, Items.WHEAT), 5, "hired farmer pickup should route wheat to job inventory");
        helper.assertFalse(
                HiredJobInventory.isJobItem(pickupInventory.getItem(HiredJobInventory.MAIN_GRID_START)),
                "captured wheat should remain a vanilla-clean item stack");

        BlockPos outsidePos = helper.absolutePos(new BlockPos(10, 2, 10));
        ItemEntity outsideDrop = new ItemEntity(
                level,
                outsidePos.getX() + 0.5D,
                outsidePos.getY(),
                outsidePos.getZ() + 0.5D,
                new ItemStack(Items.WHEAT));
        outsideDrop.setNoPickUpDelay();
        level.addFreshEntity(outsideDrop);
        helper.assertTrue(
                HiredFarmingInventoryBridge.shouldDiscardWantedItem(level, villager, outsideDrop),
                "wanted-item memory should reject farm drops outside the circular job-site range");
        helper.assertTrue(
                HiredFarmingInventoryBridge.capturePickup(level, villager, outsideDrop),
                "out-of-range farm drops should be intercepted before vanilla inventory insertion");
        helper.assertTrue(outsideDrop.isAlive(), "intercepted out-of-range farm drop should remain in the world");
        helper.assertValueEqual(
                countInventoryItem(pickupInventory, Items.WHEAT),
                5,
                "out-of-range farm drop should not enter the job inventory");

        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            pickupInventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.assertFalse(
                villager.wantsToPickUp(new ItemStack(Items.WHEAT)),
                "hired farmer should not target farm drops when the job inventory cannot accept them");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        outsideDrop.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingWorkerKeepsPlantableCropReserveInJobInventoryAndStoresSurplus(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingReserve");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        BlockPos composterRel = new BlockPos(2, 1, 2);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        villager.getInventory().setItem(0, new ItemStack(Items.CARROT, 20));

        CompoundTag state = new CompoundTag();
        HiredFarmingOptions.initializeDefaults(state);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(5, 4, 4), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        new FarmingWorker().tick(level, villager, hirer, context);
        helper.assertValueEqual(countItem(villager.getInventory(), Items.CARROT), 0, "hired farmer should not keep personal carrot reserve");
        helper.assertValueEqual(countInventoryItem(context.inventory(), Items.CARROT), 20, "all carrots should move into job inventory");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.CARROT, HiredJobInventorySlotType.SUPPLY),
                8,
                "job inventory should keep carrot planting reserve as supply");
        helper.assertValueEqual(
                countInventoryItem(context.inventory(), Items.CARROT, HiredJobInventorySlotType.OUTPUT),
                12,
                "surplus carrots should move into job inventory output");
        helper.assertFalse(
                HiredJobInventory.isJobItem(context.inventory().getItem(HiredJobInventory.MAIN_GRID_START + 1)),
                "surplus carrots should remain a vanilla-clean item stack");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void hiredFarmerPlantsFromJobInventoryWithoutPersonalSeeds(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
        ServerPlayer hirer = fakePlayer(level, "VrWorkerFarmingJobSeeds");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.FARMING),
                "farmer role should be active for job-inventory planting");
        BlockPos composterRel = new BlockPos(3, 1, 2);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        BlockPos cropRel = new BlockPos(2, 2, 2);
        setBlock(helper, cropRel.below(), Blocks.FARMLAND.defaultBlockState());
        setBlock(helper, cropRel, Blocks.AIR.defaultBlockState());

        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        session.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        session.inventory().insertPlainSupply(new ItemStack(Items.WHEAT_SEEDS));
        villager.getBrain().setMemory(
                MemoryModuleType.SECONDARY_JOB_SITE,
                List.of(GlobalPos.of(level.dimension(), helper.absolutePos(cropRel.below()))));
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        helper.assertFalse(villager.hasFarmSeeds(), "fixture farmer should have no personal planting seeds");

        HarvestFarmland harvestFarmland = new HarvestFarmland();
        long gameTime = level.getGameTime() + 1L;
        helper.assertTrue(harvestFarmland.tryStart(level, villager, gameTime), "vanilla harvest behavior should start for empty farmland");
        harvestFarmland.tickOrStop(level, villager, gameTime + 1L);

        BlockState planted = level.getBlockState(helper.absolutePos(cropRel));
        HiredJobInventory plantedInventory = HiredJobInventory.getJobInventory(villager);
        helper.assertTrue(planted.is(Blocks.WHEAT), "vanilla farming should plant wheat from job inventory seed");
        helper.assertValueEqual(countItem(villager.getInventory(), Items.WHEAT_SEEDS), 0, "planting should not use personal seed inventory");
        helper.assertValueEqual(countInventoryItem(plantedInventory, Items.WHEAT_SEEDS), 0, "job seed should be consumed by vanilla planting");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void loggingWorkerHarvestsNaturalLogsStoresDropsAndHandlesRemovedTrees(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLogging");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        BlockPos logRel = new BlockPos(4, 2, 3);
        setBlock(helper, new BlockPos(4, 1, 3), Blocks.DIRT.defaultBlockState());
        for (BlockPos rel : List.of(
                logRel,
                new BlockPos(4, 3, 3),
                new BlockPos(4, 4, 3))) {
            setBlock(helper, rel, Blocks.OAK_LOG.defaultBlockState());
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        for (BlockPos rel : List.of(
                new BlockPos(4, 5, 3),
                new BlockPos(3, 5, 3),
                new BlockPos(5, 5, 3),
                new BlockPos(4, 5, 2),
                new BlockPos(4, 5, 4),
                new BlockPos(3, 4, 3),
                new BlockPos(5, 4, 3),
                new BlockPos(4, 4, 2),
                new BlockPos(4, 4, 4))) {
            setBlock(helper, rel, leaves);
        }

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(7, 6, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
        LoggingWorker worker = new LoggingWorker();
        HiredPathTarget logTarget = new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(helper.absolutePos(logRel)),
                8,
                context::isInsideWorkArea,
                blockState -> blockState.is(BlockTags.LEAVES)).search().target();
        helper.assertTrue(logTarget != null, "test tree log should have a reachable leaf-transparent work face");

        runWorkerUntil(helper, worker, level, villager, hirer, context, 160, () ->
                !level.getBlockState(helper.absolutePos(logRel)).is(BlockTags.LOGS));
        helper.assertFalse(level.getBlockState(helper.absolutePos(logRel)).is(BlockTags.LOGS), "logger should remove the valid natural log");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.OAK_LOG)), "logger should store log drops");

        BlockPos removedTreeRel = new BlockPos(6, 2, 3);
        setBlock(helper, removedTreeRel, Blocks.OAK_LOG.defaultBlockState());
        worker.prepareBreakingTarget(level, context, villager, helper.absolutePos(removedTreeRel));
        setBlock(helper, removedTreeRel, Blocks.AIR.defaultBlockState());
        WorkResult removedResult = worker.tick(level, villager, hirer, context);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertFalse(snapshot.taskState() == HiredWorkerTaskState.WORKING, "logger should not stay working on a removed tree");
        helper.assertFalse(removedResult.status().isBlank(), "removed tree should still produce a safe status");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 400)
    public static void loggingWorkerDepositsAfterEveryCompletedTree(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLoggingDeposit");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        BlockPos chestRel = new BlockPos(2, 2, 2);
        BlockPos chest = helper.absolutePos(chestRel);
        setBlock(helper, chestRel, Blocks.CHEST.defaultBlockState());
        AssignedStorageService.removeAssignedContainer(level, chest);
        AssignedStorageService.assign(
                hirer,
                villager,
                List.of(new AssignedStorageService.StoragePosition(level.dimension(), chest)),
                AssignedStorageService.OUTPUT_PURPOSE);

        BlockPos rootRel = new BlockPos(4, 2, 3);
        setBlock(helper, rootRel.below(), Blocks.DIRT.defaultBlockState());
        for (int y = 2; y <= 4; y++) {
            setBlock(helper, new BlockPos(4, y, 3), Blocks.OAK_LOG.defaultBlockState());
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        for (BlockPos rel : List.of(
                new BlockPos(4, 5, 3),
                new BlockPos(3, 5, 3),
                new BlockPos(5, 5, 3),
                new BlockPos(4, 5, 2),
                new BlockPos(4, 5, 4),
                new BlockPos(3, 4, 3),
                new BlockPos(5, 4, 3),
                new BlockPos(4, 4, 2),
                new BlockPos(4, 4, 4))) {
            setBlock(helper, rel, leaves);
        }

        CompoundTag state = new CompoundTag();
        HiredLoggingOptions.initializeDefaults(state);
        HiredLoggingOptions.toggle(state, HiredLoggingOptions.PLANT_SAPLINGS);
        HiredWorkContext context = context(
                helper,
                villager,
                state,
                new BlockPos(1, 2, 1),
                new BlockPos(7, 6, 5),
                true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
        LoggingWorker worker = new LoggingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 260, () ->
                countItem(container(level, chest), Items.OAK_LOG) == 3
                        && !context.hasOutputToDeposit());

        helper.assertValueEqual(
                countItem(container(level, chest), Items.OAK_LOG),
                3,
                "logger should deposit the completed tree's logs immediately");
        helper.assertFalse(context.hasOutputToDeposit(), "completed tree deposit should empty carried outputs");
        helper.assertFalse(
                level.getBlockState(helper.absolutePos(rootRel)).is(BlockTags.LOGS),
                "logger should finish chopping before depositing");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 400)
    public static void loggingWorkerContinuesWithoutAssignedOutputStorage(GameTestHelper helper) {
        buildFloor(helper, 0, 13, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLoggingNoStorage");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        AssignedStorageService.removeAllAssignedStorage(level, villager);

        BlockPos oakRootRel = new BlockPos(4, 2, 3);
        BlockPos birchRootRel = new BlockPos(10, 2, 3);
        for (BlockPos rootRel : List.of(oakRootRel, birchRootRel)) {
            boolean oak = rootRel.equals(oakRootRel);
            BlockState log = oak ? Blocks.OAK_LOG.defaultBlockState() : Blocks.BIRCH_LOG.defaultBlockState();
            BlockState leaves = (oak ? Blocks.OAK_LEAVES : Blocks.BIRCH_LEAVES)
                    .defaultBlockState()
                    .setValue(BlockStateProperties.PERSISTENT, false);
            setBlock(helper, rootRel.below(), Blocks.DIRT.defaultBlockState());
            for (int y = 0; y <= 2; y++) {
                setBlock(helper, rootRel.above(y), log);
            }
            for (BlockPos leafRel : List.of(
                    rootRel.offset(0, 3, 0),
                    rootRel.offset(-1, 3, 0),
                    rootRel.offset(1, 3, 0),
                    rootRel.offset(0, 3, -1),
                    rootRel.offset(0, 3, 1),
                    rootRel.offset(-1, 2, 0),
                    rootRel.offset(1, 2, 0),
                    rootRel.offset(0, 2, -1),
                    rootRel.offset(0, 2, 1))) {
                setBlock(helper, leafRel, leaves);
            }
        }

        CompoundTag state = new CompoundTag();
        HiredLoggingOptions.initializeDefaults(state);
        HiredLoggingOptions.toggle(state, HiredLoggingOptions.PLANT_SAPLINGS);
        HiredWorkContext context = context(
                helper,
                villager,
                state,
                new BlockPos(1, 2, 1),
                new BlockPos(12, 6, 5),
                true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
        LoggingWorker worker = new LoggingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 300, () ->
                !level.getBlockState(helper.absolutePos(oakRootRel)).is(BlockTags.OAK_LOGS)
                        && !level.getBlockState(helper.absolutePos(birchRootRel)).is(BlockTags.BIRCH_LOGS));

        helper.assertFalse(
                level.getBlockState(helper.absolutePos(oakRootRel)).is(BlockTags.OAK_LOGS),
                "logger should finish the first tree without output storage");
        helper.assertFalse(
                level.getBlockState(helper.absolutePos(birchRootRel)).is(BlockTags.BIRCH_LOGS),
                "logger should continue to a second tree without output storage");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.OAK_LOG)), "oak drops should remain carried as output");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.BIRCH_LOG)), "birch drops should remain carried as output");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void loggingWorkerKeepsLeafConnectedTreeFamiliesSeparate(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLoggingFamilies");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        BlockPos oakRootRel = new BlockPos(4, 2, 3);
        BlockPos birchRootRel = new BlockPos(7, 2, 3);
        setBlock(helper, new BlockPos(4, 1, 3), Blocks.DIRT.defaultBlockState());
        setBlock(helper, new BlockPos(7, 1, 3), Blocks.DIRT.defaultBlockState());
        for (int y = 2; y <= 4; y++) {
            setBlock(helper, new BlockPos(4, y, 3), Blocks.OAK_LOG.defaultBlockState());
            setBlock(helper, new BlockPos(7, y, 3), Blocks.BIRCH_LOG.defaultBlockState());
        }

        BlockState oakLeaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        for (BlockPos rel : List.of(
                new BlockPos(4, 5, 3),
                new BlockPos(3, 5, 3),
                new BlockPos(5, 5, 3),
                new BlockPos(4, 5, 2),
                new BlockPos(4, 5, 4),
                new BlockPos(3, 4, 3),
                new BlockPos(5, 4, 3),
                new BlockPos(4, 4, 2),
                new BlockPos(4, 4, 4))) {
            setBlock(helper, rel, oakLeaves);
        }
        BlockState birchLeaves = Blocks.BIRCH_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        for (BlockPos rel : List.of(
                new BlockPos(7, 5, 3),
                new BlockPos(6, 5, 3),
                new BlockPos(8, 5, 3),
                new BlockPos(7, 5, 2),
                new BlockPos(7, 5, 4),
                new BlockPos(6, 4, 3),
                new BlockPos(8, 4, 3),
                new BlockPos(7, 4, 2),
                new BlockPos(7, 4, 4))) {
            setBlock(helper, rel, birchLeaves);
        }

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(9, 6, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
        LoggingWorker worker = new LoggingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 160, () ->
                !level.getBlockState(helper.absolutePos(oakRootRel)).is(BlockTags.OAK_LOGS));

        helper.assertFalse(level.getBlockState(helper.absolutePos(oakRootRel)).is(BlockTags.OAK_LOGS), "logger should harvest the selected oak tree");
        helper.assertTrue(level.getBlockState(helper.absolutePos(birchRootRel)).is(BlockTags.BIRCH_LOGS), "leaf-connected birch should remain for a separate harvest");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.OAK_LOG)), "oak drops should be stored as output");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void loggingWorkerKeepsLeafConnectedSameSpeciesTreesSeparate(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLoggingSameSpecies");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        BlockPos firstRootRel = new BlockPos(4, 2, 3);
        BlockPos secondRootRel = new BlockPos(7, 2, 3);
        setBlock(helper, firstRootRel.below(), Blocks.DIRT.defaultBlockState());
        setBlock(helper, secondRootRel.below(), Blocks.DIRT.defaultBlockState());
        for (int y = 2; y <= 4; y++) {
            setBlock(helper, new BlockPos(4, y, 3), Blocks.OAK_LOG.defaultBlockState());
            setBlock(helper, new BlockPos(7, y, 3), Blocks.OAK_LOG.defaultBlockState());
        }

        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        for (BlockPos rel : List.of(
                new BlockPos(4, 5, 3),
                new BlockPos(3, 5, 3),
                new BlockPos(5, 5, 3),
                new BlockPos(4, 5, 2),
                new BlockPos(4, 5, 4),
                new BlockPos(7, 5, 3),
                new BlockPos(6, 5, 3),
                new BlockPos(8, 5, 3),
                new BlockPos(7, 5, 2),
                new BlockPos(7, 5, 4))) {
            setBlock(helper, rel, leaves);
        }

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(9, 6, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
        LoggingWorker worker = new LoggingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 160, () ->
                !level.getBlockState(helper.absolutePos(firstRootRel)).is(BlockTags.OAK_LOGS));

        helper.assertFalse(level.getBlockState(helper.absolutePos(firstRootRel)).is(BlockTags.OAK_LOGS), "logger should harvest the nearer oak tree");
        helper.assertTrue(level.getBlockState(helper.absolutePos(secondRootRel)).is(BlockTags.OAK_LOGS), "a separately rooted oak sharing the canopy should remain for its own harvest");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void loggingWorkerRecognizesMangroveRootsAsNaturalTreeBases(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLoggingMangrove");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        BlockPos rootLogRel = new BlockPos(4, 2, 3);
        setBlock(helper, rootLogRel.below(), Blocks.MANGROVE_ROOTS.defaultBlockState());
        for (int y = 2; y <= 4; y++) {
            setBlock(helper, new BlockPos(4, y, 3), Blocks.MANGROVE_LOG.defaultBlockState());
        }
        BlockState leaves = Blocks.MANGROVE_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        for (BlockPos rel : List.of(
                new BlockPos(4, 5, 3),
                new BlockPos(3, 5, 3),
                new BlockPos(5, 5, 3),
                new BlockPos(4, 5, 2),
                new BlockPos(4, 5, 4))) {
            setBlock(helper, rel, leaves);
        }

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(7, 6, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
        LoggingWorker worker = new LoggingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 160, () ->
                !level.getBlockState(helper.absolutePos(rootLogRel)).is(BlockTags.MANGROVE_LOGS));

        helper.assertFalse(level.getBlockState(helper.absolutePos(rootLogRel)).is(BlockTags.MANGROVE_LOGS), "logger should recognize and harvest a mangrove trunk rooted above mangrove roots");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.MANGROVE_LOG)), "mangrove drops should be stored as output");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void loggingWorkerHarvestsAndReplantsCrimsonFungi(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrWorkerLoggingCrimson");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        BlockPos rootStemRel = new BlockPos(4, 2, 3);
        setBlock(helper, rootStemRel.below(), Blocks.CRIMSON_NYLIUM.defaultBlockState());
        for (int y = 2; y <= 4; y++) {
            setBlock(helper, new BlockPos(4, y, 3), Blocks.CRIMSON_STEM.defaultBlockState());
        }
        for (BlockPos rel : List.of(
                new BlockPos(4, 5, 3),
                new BlockPos(3, 5, 3),
                new BlockPos(5, 5, 3),
                new BlockPos(4, 5, 2),
                new BlockPos(4, 5, 4))) {
            setBlock(helper, rel, Blocks.NETHER_WART_BLOCK.defaultBlockState());
        }

        CompoundTag state = new CompoundTag();
        state.putBoolean(HiredLoggingOptions.PLANT_SAPLINGS_TAG, true);
        HiredWorkContext context = context(helper, villager, state, new BlockPos(1, 2, 1), new BlockPos(7, 6, 5), true);
        context.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
        context.inventory().setItem(6, new ItemStack(Items.CRIMSON_FUNGUS));
        LoggingWorker worker = new LoggingWorker();

        runWorkerUntil(helper, worker, level, villager, hirer, context, 180, () ->
                level.getBlockState(helper.absolutePos(rootStemRel)).is(Blocks.CRIMSON_FUNGUS));

        helper.assertTrue(level.getBlockState(helper.absolutePos(rootStemRel)).is(Blocks.CRIMSON_FUNGUS), "logger should replant a harvested crimson fungus");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.CRIMSON_STEM)), "crimson stem drops should be stored as output");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void hiredAiSuppressionStartsOnlyForActiveWorkAndClearsAfterDisableOrContractEnd(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        level.setDayTime(1000L);
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));

        helper.assertFalse(HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager), "unhired villagers should keep vanilla brain ticks");
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        HiredVillagerWorkService.initializeWorkArea(level, villager);
        CompoundTag state = villager.getPersistentData().getCompound(WORK_STATE_TAG);
        state.putBoolean("Enabled", true);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.SELECTING_TARGET, null);
        helper.assertTrue(HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager), "active hired work should suppress vanilla idle AI");
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
        helper.assertTrue(
                HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager),
                "waiting hired work should not run unrelated vanilla behaviors");
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.PAUSED_OUTPUT_BACKPRESSURE, null);
        helper.assertTrue(
                HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager),
                "output-backpressured hired work should not run unrelated vanilla behaviors");

        BlockPos workTarget = helper.absolutePos(new BlockPos(5, 2, 3));
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.WORKING, workTarget);
        hirer.moveTo(workTarget.getX() + 0.5D, workTarget.getY(), workTarget.getZ() + 0.5D);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(hirer, true));
        villager.getLookControl().setLookAt(hirer, 30.0F, 30.0F);

        villager.getLookControl().tick();

        helper.assertValueEqual(
                villager.getLookControl().getWantedX(),
                workTarget.getX() + 0.5D,
                "work look-control mixin target x");
        helper.assertValueEqual(
                villager.getLookControl().getWantedY(),
                workTarget.getY() + 0.5D,
                "work look-control mixin target y");
        helper.assertValueEqual(
                villager.getLookControl().getWantedZ(),
                workTarget.getZ() + 0.5D,
                "work look-control mixin target z");

        HiredVillagerFocusService.onVillagerTickPre(villager);

        helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.LOOK_TARGET)
                        .map(lookTarget -> !(lookTarget instanceof EntityTracker)
                                && workTarget.equals(lookTarget.currentBlockPosition()))
                        .orElse(false),
                "active block work should replace a player look target with the work target");

        state.putBoolean("Enabled", false);
        helper.assertFalse(HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager), "disabled work should clear suppression");
        state.putBoolean("Enabled", true);
        HiredVillagerContractService.endHireContract(level, villager, hirer);
        helper.assertFalse(HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager), "ended contracts should clear suppression");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void disablingHiredWorkRunsWorkerPauseCleanup(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        level.setDayTime(1000L);
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8, HiredVillagerRole.NITWIT);
        HiredVillagerWorkService.initializeWorkArea(level, villager);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", false);
        session.context().setProgressTicks(20);
        HiredWorkPlan.replace(session.context(), List.of(villager.blockPosition()), 1);
        HiredWorkerBrain.setFailure(session.context(), "fixture_failure", level.getGameTime() + 100L);
        HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.WORKING, villager.blockPosition());

        HiredVillagerWorkService.onVillagerTickPost(villager);

        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        helper.assertValueEqual(snapshot.taskState(), HiredWorkerTaskState.AWAITING_INSTRUCTION, "disabled worker task state");
        helper.assertValueEqual(snapshot.progressTicks(), 0, "disabled worker progress should reset");
        helper.assertValueEqual(HiredWorkPlan.size(session.context()), 0, "disabled worker plan should clear");
        helper.assertTrue(snapshot.failureReason().isEmpty(), "disabled worker failure should clear");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hiredWalkTargetTrackingDoesNotClaimVanillaNavigation(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 1);
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 3));
        BlockPos target = helper.absolutePos(new BlockPos(6, 2, 3));

        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), 0.5F, 1));
        helper.assertFalse(
                VillagerTaskNavigationUtil.isHiredWalkTarget(villager),
                "an ordinary vanilla walk target must not be marked as hired navigation");

        VillagerTaskNavigationUtil.setHiredWalkTarget(villager, target, 0.5D, 1);
        helper.assertTrue(
                VillagerTaskNavigationUtil.isHiredWalkTarget(villager),
                "hired walk targets should be identifiable by job-site mixins");

        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        helper.assertFalse(
                VillagerTaskNavigationUtil.isHiredWalkTarget(villager),
                "stopping hired navigation should clear its marker");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void hiredWorkPausesForVanillaRestAndReportsSleepStatus(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        level.setDayTime(1000L);
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));

        HiredVillagerContractService.startHireContract(
                level, villager, hirer, 1, 8, HiredVillagerRole.FARMING);
        HiredVillagerWorkService.initializeWorkArea(level, villager);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        CompoundTag state = session.state();
        state.putBoolean("Enabled", true);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.SELECTING_TARGET, null);

        level.setDayTime(13000L);
        helper.assertFalse(
                HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager),
                "scheduled rest should keep vanilla brain ticks available for bed pathing");
        villager.getBrain().setActiveActivityIfPossible(Activity.REST);
        HiredVillagerWorkService.onVillagerTickPost(villager);
        helper.assertValueEqual(state.getString("Status"), "interaction.work.status.tired", "night work status");
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, level.getGameTime()).taskState(),
                HiredWorkerTaskState.AWAITING_INSTRUCTION,
                "hired task should pause while vanilla rest is active");

        villager.startSleeping(villager.blockPosition());
        HiredVillagerWorkService.onVillagerTickPost(villager);
        helper.assertValueEqual(state.getString("Status"), "interaction.work.status.sleeping", "sleeping work status");
        villager.stopSleeping();

        state.putLong("NextWorkGameTime", level.getGameTime() + 200L);
        level.setDayTime(1000L);
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        HiredVillagerWorkService.onVillagerTickPost(villager);
        helper.assertValueEqual(
                HiredWorkerBrain.snapshot(state, level.getGameTime()).taskState(),
                HiredWorkerTaskState.IDLE,
                "hired task should be ready to resume after vanilla rest ends");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void guardStaysAwakeAndWorkingDuringVanillaRest(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        level.setDayTime(13000L);
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager guard = spawnVillager(helper, new BlockPos(3, 2, 3));
        guard.setVillagerData(guard.getVillagerData().setProfession(VillagerProfession.WEAPONSMITH));

        HiredVillagerContractService.startHireContract(
                level, guard, hirer, 1, 8, HiredVillagerRole.COMBAT);
        HiredVillagerWorkService.initializeWorkArea(level, guard);
        HiredWorkSession session = HiredWorkSession.active(level, guard);
        CompoundTag state = session.state();
        state.putBoolean("Enabled", true);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.AWAITING_INSTRUCTION, guard.blockPosition());

        guard.getBrain().setActiveActivityIfPossible(Activity.REST);
        guard.startSleeping(guard.blockPosition());

        helper.assertFalse(
                HiredVillagerFocusService.shouldUseVanillaRest(level, guard),
                "an on-duty guard should reject vanilla rest");
        helper.assertTrue(
                VillagerBehaviorSuppressionPolicy.suppresses(
                        guard, VillagerBehaviorSuppressionPolicy.Behavior.SLEEPING),
                "an on-duty guard should suppress sleeping behavior");
        helper.assertValueEqual(
                com.jvn.villagerretaliation.interaction.VillagerAiArbitration.currentPriority(level, guard),
                com.jvn.villagerretaliation.interaction.VillagerAiArbitration.Priority.HIRED_ROLE_TASK,
                "guard duty should outrank an existing sleeping pose");

        VillagerBehaviorSuppressionPolicy.enforce(level, guard);
        helper.assertFalse(guard.isSleeping(), "guard policy should wake a guard already in bed");
        helper.assertTrue(
                VillagerBehaviorSuppressionPolicy.shouldSuppressVanillaBrainTick(level, guard),
                "the vanilla rest schedule should stay suppressed while guard duty is active");

        HiredVillagerWorkService.onVillagerTickPost(guard);
        helper.assertFalse(
                state.getString("Status").equals("interaction.work.status.sleeping")
                        || state.getString("Status").equals("interaction.work.status.tired"),
                "guard work should continue instead of reporting a vanilla rest pause");

        HiredVillagerContractService.endHireContract(level, guard, hirer);
        guard.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void stressWorkerStaysActiveDuringVanillaRestWithTarget(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        level.setDayTime(13000L);
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.setCustomName(Component.literal("Stress Farming #1"));

        HiredVillagerContractService.startHireContract(
                level, villager, hirer, 1, 8, HiredVillagerRole.FARMING);
        HiredVillagerWorkService.initializeWorkArea(level, villager);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        CompoundTag state = session.state();
        state.putBoolean("Enabled", true);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.SELECTING_TARGET, null);

        Cow target = spawnAnimal(helper, EntityType.COW, new BlockPos(4, 2, 3));
        villager.getBrain().setActiveActivityIfPossible(Activity.REST);
        villager.startSleeping(villager.blockPosition());
        villager.setTarget(target);

        helper.assertTrue(villager.getTarget() == target, "test should exercise the active-target scheduler path");
        helper.assertTrue(HiredStressGridService.isStressWorker(villager), "legacy stress-grid name should be recognized");
        helper.assertFalse(
                HiredVillagerFocusService.shouldUseVanillaRest(level, villager),
                "stress workers should not enter the vanilla rest pause");
        HiredVillagerWorkService.onVillagerTickPost(villager);
        helper.assertFalse(villager.isSleeping(), "stress worker should be woken for continuous load testing");
        helper.assertFalse(
                state.getString("Status").equals("interaction.work.status.sleeping")
                        || state.getString("Status").equals("interaction.work.status.tired"),
                "stress worker should not report a vanilla rest status");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        target.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void stressMiningFixtureProvidesCompleteVerticalAccess(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 8, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrStressMiningAccess");
        Villager miner = spawnVillager(helper, new BlockPos(4, 2, 4));
        miner.setVillagerData(miner.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        helper.assertTrue(
                HiredVillagerContractService.startHireContract(
                        level, miner, hirer, 1, 64, HiredVillagerRole.MINING),
                "stress miner should accept a mining contract");

        BlockPos cell = helper.absolutePos(new BlockPos(4, 2, 4));
        int bottomY = Math.max(level.getMinBuildHeight() + 2, cell.getY() - 24);
        HiredVillagerWorkService.setWorkArea(
                hirer,
                level,
                miner,
                new BlockPos(cell.getX() - 3, bottomY, cell.getZ() - 3),
                cell.offset(3, 0, 3));
        miner.getPersistentData().putBoolean("VillagerRetaliationHiredStressWorker", true);
        miner.getPersistentData().putString("VillagerRetaliationHiredStressRole", "mining");
        miner.getPersistentData().putLong("VillagerRetaliationHiredStressCell", cell.asLong());

        HiredStressGridService.maintainStressWorker(level, miner);
        HiredWorkSession session = HiredWorkSession.active(level, miner);
        helper.assertValueEqual(
                HiredMiningMode.fromState(session.state()),
                HiredMiningMode.EXCAVATE_AREA,
                "stress mining fixture should force vertical excavation mode");
        helper.assertTrue(
                MiningExcavationSupport.hasCompleteLadderRouteToLayer(level, session.context(), bottomY),
                "stress mining fixture should provide a complete route to its deepest assigned layer");
        for (int y = bottomY; y <= cell.getY(); y++) {
            BlockPos ladderPos = new BlockPos(cell.getX() - 3, y, cell.getZ() - 3);
            helper.assertTrue(level.getBlockState(ladderPos).is(Blocks.LADDER),
                    "stress mining access should contain a ladder at y=" + y);
        }

        HiredVillagerContractService.endHireContract(level, miner, hirer);
        miner.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void stressCombatAndHuntingTargetsRenewAfterDefeat(GameTestHelper helper) {
        buildFloor(helper, 0, 14, 0, 8, 1);
        ServerLevel level = helper.getLevel();
        BlockPos combatCell = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos huntingCell = helper.absolutePos(new BlockPos(10, 2, 3));
        Villager combat = spawnVillager(helper, new BlockPos(3, 2, 3));
        Villager hunting = spawnVillager(helper, new BlockPos(10, 2, 3));

        combat.getPersistentData().putBoolean("VillagerRetaliationHiredStressWorker", true);
        combat.getPersistentData().putString("VillagerRetaliationHiredStressRole", "combat");
        combat.getPersistentData().putLong("VillagerRetaliationHiredStressCell", combatCell.asLong());
        hunting.getPersistentData().putBoolean("VillagerRetaliationHiredStressWorker", true);
        hunting.getPersistentData().putString("VillagerRetaliationHiredStressRole", "hunting");
        hunting.getPersistentData().putLong("VillagerRetaliationHiredStressCell", huntingCell.asLong());

        ItemEntity combatDrop = new ItemEntity(
                level,
                combatCell.getX() + 0.5D,
                combatCell.getY(),
                combatCell.getZ() + 0.5D,
                new ItemStack(Items.BEEF));
        ExperienceOrb huntingExperience = new ExperienceOrb(
                level,
                huntingCell.getX() + 0.5D,
                huntingCell.getY(),
                huntingCell.getZ() + 0.5D,
                5);
        level.addFreshEntity(combatDrop);
        level.addFreshEntity(huntingExperience);

        HiredStressGridService.maintainStressWorker(level, combat);
        HiredStressGridService.maintainStressWorker(level, hunting);
        helper.assertFalse(combatDrop.isAlive(), "combat stress cell should clear mob item drops");
        helper.assertFalse(huntingExperience.isAlive(), "hunting stress cell should clear XP drops");
        List<Cow> firstCombatTargets = level.getEntitiesOfClass(
                Cow.class, new AABB(combatCell).inflate(3.0D), Cow::isAlive);
        List<Cow> firstHuntingTargets = level.getEntitiesOfClass(
                Cow.class, new AABB(huntingCell).inflate(3.0D), Cow::isAlive);
        helper.assertValueEqual(firstCombatTargets.size(), 1, "combat should receive one initial stress target");
        helper.assertValueEqual(firstHuntingTargets.size(), 1, "hunting should receive one initial stress target");
        Cow firstCombat = firstCombatTargets.getFirst();
        Cow firstHunting = firstHuntingTargets.getFirst();
        helper.assertFalse(firstCombat.isInvulnerable(), "combat target must be killable");
        helper.assertFalse(firstHunting.isInvulnerable(), "hunting target must be killable");
        UUID firstCombatId = firstCombat.getUUID();
        UUID firstHuntingId = firstHunting.getUUID();

        firstCombat.discard();
        firstHunting.discard();
        combat.setTarget(null);
        HiredStressGridService.maintainStressWorker(level, combat);
        HiredStressGridService.maintainStressWorker(level, hunting);

        List<Cow> renewedCombatTargets = level.getEntitiesOfClass(
                Cow.class, new AABB(combatCell).inflate(3.0D), Cow::isAlive);
        List<Cow> renewedHuntingTargets = level.getEntitiesOfClass(
                Cow.class, new AABB(huntingCell).inflate(3.0D), Cow::isAlive);
        helper.assertValueEqual(renewedCombatTargets.size(), 1, "combat should receive one replacement target");
        helper.assertValueEqual(renewedHuntingTargets.size(), 1, "hunting should receive one replacement target");
        helper.assertFalse(
                renewedCombatTargets.getFirst().getUUID().equals(firstCombatId),
                "combat replacement should be a new mob");
        helper.assertFalse(
                renewedHuntingTargets.getFirst().getUUID().equals(firstHuntingId),
                "hunting replacement should be a new mob");

        renewedCombatTargets.getFirst().discard();
        renewedHuntingTargets.getFirst().discard();
        combat.discard();
        hunting.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hiredWorkerAtPaddedWorkEdgeStillReturnsToStrictBounds(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 8, 1);
        ServerLevel level = helper.getLevel();
        level.setDayTime(1000L);
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 4));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));
        tickVillager(level, villager, 20);
        BlockPos outsideStart = helper.absolutePos(new BlockPos(2, 2, 4));
        villager.moveTo(outsideStart.getX() + 0.5D, outsideStart.getY(), outsideStart.getZ() + 0.5D, 0.0F, 0.0F);

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8, HiredVillagerRole.NITWIT);
        helper.assertTrue(
                HiredVillagerWorkService.setWorkArea(
                        hirer,
                        level,
                        villager,
                        helper.absolutePos(new BlockPos(4, 2, 3)),
                        helper.absolutePos(new BlockPos(6, 4, 5))),
                "work area assignment should succeed");
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.IDLE, null);

        helper.assertFalse(
                session.context().isInsideWorkArea(villager.blockPosition()),
                "fixture villager should start outside the strict work box");
        helper.assertTrue(
                HiredVillagerWorkService.isInsideEffectiveWorkArea(
                        level,
                        villager,
                        HiredVillagerRole.NITWIT,
                        session.context(),
                        villager.blockPosition()),
                "fixture villager should still be inside the old padded tether");
        net.minecraft.world.level.pathfinder.Path preflightPath = HiredPathMemory.createPath(
                level,
                villager,
                helper.absolutePos(new BlockPos(4, 2, 4)),
                0);
        helper.assertTrue(
                preflightPath != null && preflightPath.canReach(),
                "fixture should provide an exact path back into the strict work box");

        HiredVillagerWorkService.onVillagerTickPost(villager);

        HiredWorkSession afterTick = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(afterTick.state(), level.getGameTime());
        helper.assertValueEqual(
                snapshot.taskState(),
                HiredWorkerTaskState.RETURNING_TO_WORK_AREA,
                "worker at padded edge should return before resuming work");
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        helper.assertTrue(
                navigationTarget != null && afterTick.context().isInsideWorkArea(navigationTarget),
                "return navigation target should be inside the strict work box");
        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hiredFarmerStandingAcrossAssignedFieldLayersStopsReturning(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 0);
        ServerLevel level = helper.getLevel();
        level.setDayTime(1000L);
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        BlockPos cropRel = new BlockPos(3, 2, 3);
        setBlock(helper, cropRel.below(), Blocks.FARMLAND.defaultBlockState());
        Villager villager = spawnVillager(helper, cropRel);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        setBlock(helper, cropRel, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        BlockPos crop = helper.absolutePos(cropRel);
        villager.moveTo(crop.getX() + 0.5D, crop.getY(), crop.getZ() + 0.5D, 0.0F, 0.0F);

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.FARMING),
                "farmer role should be active for farmland-layer return test");
        helper.assertTrue(
                HiredVillagerWorkService.setWorkArea(
                        hirer,
                        level,
                        villager,
                        helper.absolutePos(new BlockPos(2, 1, 2)),
                        helper.absolutePos(new BlockPos(4, 1, 4))),
                "farmland-layer work area assignment should succeed");

        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        session.state().putLong("NextWorkGameTime", level.getGameTime() + 200L);
        session.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        HiredWorkerBrain.setState(
                session.state(),
                HiredWorkerTaskState.RETURNING_TO_WORK_AREA,
                helper.absolutePos(cropRel.below()));

        helper.assertFalse(
                session.context().isInsideWorkArea(villager.blockPosition()),
                "fixture farmer feet should be above the strict farmland-layer work box");
        helper.assertTrue(
                session.context().isInsideWorkArea(villager.blockPosition().below()),
                "fixture farmer should be standing on an assigned farmland-layer block");

        HiredVillagerWorkService.onVillagerTickPost(villager);

        HiredWorkSession afterTick = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(afterTick.state(), level.getGameTime());
        helper.assertValueEqual(
                snapshot.taskState(),
                HiredWorkerTaskState.IDLE,
                "farmer standing above assigned farmland should stop returning and resume work");
        helper.assertFalse(
                villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "resolved farmland-layer return should clear stale pathing");

        helper.assertTrue(
                HiredVillagerWorkService.setWorkArea(hirer, level, villager, crop, crop),
                "crop-layer work area assignment should succeed");
        villager.moveTo(crop.getX() + 0.5D, crop.getY() - 0.0625D, crop.getZ() + 0.5D, 0.0F, 0.0F);
        HiredWorkSession cropLayerSession = HiredWorkSession.active(level, villager);
        cropLayerSession.state().putBoolean("Enabled", true);
        cropLayerSession.state().putLong("NextWorkGameTime", level.getGameTime() + 200L);
        HiredWorkerBrain.setState(
                cropLayerSession.state(),
                HiredWorkerTaskState.RETURNING_TO_WORK_AREA,
                crop);

        helper.assertFalse(
                cropLayerSession.context().isInsideWorkArea(villager.blockPosition()),
                "fixture farmer feet should be below the strict crop-layer work box");
        helper.assertTrue(
                cropLayerSession.context().isInsideWorkArea(villager.blockPosition().above()),
                "fixture farmer should be standing under an assigned crop-layer block");

        HiredVillagerWorkService.onVillagerTickPost(villager);

        HiredWorkSession afterCropLayerTick = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot cropLayerSnapshot = HiredWorkerBrain.snapshot(afterCropLayerTick.state(), level.getGameTime());
        helper.assertValueEqual(
                cropLayerSnapshot.taskState(),
                HiredWorkerTaskState.IDLE,
                "farmer standing at farmland height under assigned crops should stop returning and resume work");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void hiredFarmingWithHoeKeepsFarmerBrainTick(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        level.setDayTime(1000L);
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        Villager otherVillager = spawnVillager(helper, new BlockPos(4, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos composterRel = new BlockPos(3, 1, 3);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.FARMING),
                "farmer role should be available for vanilla farming suppression test");
        HiredVillagerWorkService.setWorkArea(
                hirer,
                level,
                villager,
                helper.absolutePos(new BlockPos(1, 2, 1)),
                helper.absolutePos(new BlockPos(5, 4, 5)));
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.IDLE, null);
        session.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        helper.assertFalse(
                HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager),
                "ready hired farmer should keep vanilla brain ticks for vanilla farming");

        villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, otherVillager);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(otherVillager.blockPosition()));
        HiredVillagerFocusService.onVillagerTickPre(villager);
        helper.assertFalse(
                villager.getBrain().hasMemoryValue(MemoryModuleType.INTERACTION_TARGET),
                "ready hired farmer should still ignore social interaction targets");
        helper.assertFalse(
                villager.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                "ready hired farmer should still ignore social look targets");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        otherVillager.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void hiredFarmingSuppressesClaimedJobSiteBlockTargets(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        level.setDayTime(1000L);
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        BlockPos composterRel = new BlockPos(3, 1, 3);
        BlockPos composter = helper.absolutePos(composterRel);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), composter));

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.FARMING),
                "farmer role should be available for claimed job-site suppression test");
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.IDLE, null);
        session.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));

        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(composter), 0.4F, 4));
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(composter));
        helper.assertTrue(
                HiredVillagerFocusService.shouldSuppressClaimedJobSiteBlockUse(level, villager),
                "active hired farmer should suppress direct claimed job-site block use");
        helper.assertTrue(
                HiredVillagerFocusService.isClaimedJobSitePathFloor(villager, composter),
                "claimed job-site block should be blocked as a path floor");

        HiredVillagerFocusService.suppressClaimedJobSiteBlockNavigation(level, villager);
        helper.assertFalse(
                villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "claimed job-site walk target should be cleared");
        helper.assertFalse(
                villager.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                "claimed job-site look target should be cleared");

        BlockPos fieldTarget = helper.absolutePos(new BlockPos(4, 2, 3));
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(fieldTarget), 0.5F, 1));
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(fieldTarget));
        HiredVillagerFocusService.suppressClaimedJobSiteBlockNavigation(level, villager);
        helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .map(walkTarget -> fieldTarget.equals(walkTarget.getTarget().currentBlockPosition()))
                        .orElse(false),
                "field walk target should remain available for vanilla farming");
        helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.LOOK_TARGET)
                        .map(lookTarget -> fieldTarget.equals(lookTarget.currentBlockPosition()))
                        .orElse(false),
                "field look target should remain available for vanilla farming");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingClaimedJobBlockBecomesWorkSite(GameTestHelper helper) {
        HiredVillagerIndex.clearRuntimeState();
        buildFloor(helper, 0, 10, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrFarmingJobSite");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        BlockPos composterRel = new BlockPos(8, 1, 3);
        BlockPos composter = helper.absolutePos(composterRel);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        Villager villager = spawnVillager(helper, new BlockPos(8, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), composter));

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.FARMING),
                "farmer role should be available for job-block site test");
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredVillagerIndex.update(level, villager);

        helper.assertFalse(session.area().explicitlyAssigned(), "claimed job block should not pretend to be a custom clipboard box");
        helper.assertTrue(session.area().usable(), "claimed job block should synthesize a usable work site");
        helper.assertValueEqual(session.jobSite().anchor(), composter, "job site anchor");
        helper.assertValueEqual(session.jobSite().anchorSource(), HiredJobSite.AnchorSource.VANILLA_JOB_SITE, "job site source");
        helper.assertTrue(session.context().hasWorkArea(), "worker context should scan the synthesized job-block site");
        helper.assertTrue(
                session.context().isInsideWorkArea(villager.blockPosition()),
                "farmer standing by the claimed job block should be inside the synthesized site");
        BlockPos squareCorner = composter.offset(8, 1, 8);
        helper.assertTrue(
                session.context().isInsideWorkArea(squareCorner),
                "fixture corner should remain inside the synthesized scan bounds");
        helper.assertFalse(
                HiredVillagerWorkService.isInsideEffectiveWorkArea(
                        level,
                        villager,
                        HiredVillagerRole.FARMING,
                        session.context(),
                        squareCorner),
                "synthesized job-site tether should exclude square corners beyond its circular radius");

        ClipboardWorkforceSnapshot snapshot = ClipboardWorkforceService.snapshot(hirer);
        helper.assertValueEqual(snapshot.workers().size(), 1, "clipboard worker rows");
        helper.assertFalse(snapshot.workers().getFirst().noWorkArea(), "clipboard should not show missing work area for a claimed job-block site");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void hiredFarmerSuppressesSeedPickupWhileThreatened(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
        ServerPlayer hirer = fakePlayer(level, "VrWorkerCombatPickup");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.FARMING),
                "farmer role should be active for the combat pickup fixture");
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);

        ItemEntity seeds = new ItemEntity(
                level,
                villager.getX(),
                villager.getY(),
                villager.getZ(),
                new ItemStack(Items.WHEAT_SEEDS, 5));
        seeds.setNoPickUpDelay();
        level.addFreshEntity(seeds);
        var hostile = helper.spawn(EntityType.ZOMBIE, 4, 2, 2);
        villager.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, hostile);

        helper.assertFalse(villager.wantsToPickUp(seeds.getItem()),
                "threatened farmer should reject seeds instead of starting item collection");
        helper.assertFalse(HiredFarmingInventoryBridge.capturePickup(level, villager, seeds),
                "combat should suspend the hired farming pickup route");
        helper.assertTrue(seeds.isAlive(), "combat pickup suppression should leave the seed entity untouched");
        helper.assertValueEqual(countInventoryItem(session.inventory(), Items.WHEAT_SEEDS), 0,
                "combat pickup should not add seeds to the job inventory");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        hostile.discard();
        seeds.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void builderOnlyReportsMissingStorageWhenStorageIsRequired(GameTestHelper helper) {
        HiredVillagerIndex.clearRuntimeState();
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrBuilderStorageWarning");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.MASON));

        HiredVillagerContractService.startOneOffBuilderJob(level, villager, hirer);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.WORKING, null);
        HiredVillagerIndex.update(level, villager);

        ClipboardWorkforceSnapshot suppliedSnapshot = ClipboardWorkforceService.snapshot(hirer);
        helper.assertValueEqual(suppliedSnapshot.workers().size(), 1, "clipboard builder rows");
        helper.assertFalse(
                suppliedSnapshot.workers().getFirst().noStorage(),
                "builder with enough carried materials should not require assigned storage");
        helper.assertFalse(
                suppliedSnapshot.warnings().stream().anyMatch(warning ->
                        warning.type() == ClipboardWorkforceSnapshot.WarningType.NO_STORAGE),
                "clipboard should not show a missing-storage warning for an unblocked builder");

        HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.PAUSED_NO_STORAGE, null);
        ClipboardWorkforceSnapshot blockedSnapshot = ClipboardWorkforceService.snapshot(hirer);
        helper.assertTrue(
                blockedSnapshot.workers().getFirst().noStorage(),
                "builder should still report missing storage when material collection is blocked");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        HiredVillagerIndex.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void farmingJobSiteTetherPreventsClipboardTooFarWarning(GameTestHelper helper) {
        HiredVillagerIndex.clearRuntimeState();
        buildFloor(helper, 0, 10, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrFarmingTether");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        BlockPos composterRel = new BlockPos(8, 1, 3);
        setBlock(helper, composterRel, Blocks.COMPOSTER.defaultBlockState());
        Villager villager = spawnVillager(helper, new BlockPos(8, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composterRel)));

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.FARMING),
                "farmer role should be available for job-site tether test");
        HiredVillagerWorkService.setWorkArea(
                hirer,
                level,
                villager,
                helper.absolutePos(new BlockPos(1, 2, 1)),
                helper.absolutePos(new BlockPos(4, 4, 5)));
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.IDLE, null);
        session.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        HiredVillagerIndex.update(level, villager);

        helper.assertFalse(session.context().isInsideWorkArea(villager.blockPosition()),
                "fixture villager should stand outside the strict custom work box");
        helper.assertTrue(
                HiredVillagerWorkService.isInsideEffectiveWorkArea(
                        level,
                        villager,
                        HiredVillagerRole.FARMING,
                        session.context(),
                        villager.blockPosition()),
                "farmer job site should count as the effective work tether");

        ClipboardWorkforceSnapshot snapshot = ClipboardWorkforceService.snapshot(hirer);
        helper.assertValueEqual(snapshot.workers().size(), 1, "clipboard worker rows");
        ClipboardWorkforceSnapshot.WorkerRow row = snapshot.workers().getFirst();
        helper.assertFalse(row.tooFar(), "clipboard should not report too far while farmer is at claimed job site");
        helper.assertFalse(row.noWorkArea(), "claimed farmer job site should satisfy the effective work area");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        HiredVillagerIndex.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void clipboardAllowsStorageTripsOutsideWorkArea(GameTestHelper helper) {
        HiredVillagerIndex.clearRuntimeState();
        buildFloor(helper, 0, 20, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrClipboardStorageTrip");
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(16, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));

        HiredVillagerContractService.startHireContract(
                level, villager, hirer, 1, 8, HiredVillagerRole.FARMING);
        helper.assertTrue(
                HiredVillagerWorkService.setWorkArea(
                        hirer,
                        level,
                        villager,
                        helper.absolutePos(new BlockPos(1, 2, 1)),
                        helper.absolutePos(new BlockPos(5, 4, 5))),
                "storage-trip fixture work area should be assigned");
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        HiredWorkerBrain.setStorageTarget(session.context(), villager.blockPosition());
        HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.MOVING_TO_STORAGE, null);
        HiredVillagerIndex.update(level, villager);

        helper.assertFalse(
                HiredVillagerWorkService.isInsideEffectiveWorkArea(
                        level,
                        villager,
                        HiredVillagerRole.FARMING,
                        session.context(),
                        villager.blockPosition()),
                "storage-trip fixture villager should be outside the work area");
        ClipboardWorkforceSnapshot movingSnapshot = ClipboardWorkforceService.snapshot(hirer);
        helper.assertFalse(
                movingSnapshot.workers().getFirst().tooFar(),
                "clipboard should not report too far while moving to output storage");

        HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.RETURNING_TO_WORK_AREA, session.context().workCenter());
        ClipboardWorkforceSnapshot returningSnapshot = ClipboardWorkforceService.snapshot(hirer);
        helper.assertFalse(
                returningSnapshot.workers().getFirst().tooFar(),
                "clipboard should not report too far while returning from output storage");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        HiredVillagerIndex.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200, batch = "clipboard_inventory_diagnostics")
    public static void clipboardInventoryFullRequiresBlockedWorkerState(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 6, 1);
        Villager animalHandler = spawnVillager(helper, new BlockPos(3, 2, 3));
        CompoundTag workerState = new CompoundTag();
        HiredWorkContext workerContext = context(
                helper,
                animalHandler,
                workerState,
                new BlockPos(1, 2, 1),
                new BlockPos(6, 4, 5),
                true);
        HiredJobInventory inventory = workerContext.inventory();
        HiredWorkerBrain.setFailure(workerContext, "animal_food_inventory_full", 0L);
        HiredWorkerBrain.setState(workerState, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, null);
        HiredWorkerBrain.Snapshot paused = HiredWorkerBrain.snapshot(workerState, helper.getLevel().getGameTime());
        helper.assertFalse(
                inventory.isCapacityBlockedForFailure("animal_food_inventory_full"),
                "an empty animal-handler job inventory must not be classified as full");
        helper.assertValueEqual(
                ClipboardWorkforceService.previewStatus(HiredVillagerRole.ANIMAL_HANDLING, paused, inventory),
                ClipboardWorkforceSnapshot.WorkerStatus.WAITING,
                "an empty paused animal handler should refresh its marker status to waiting");

        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.assertTrue(
                inventory.isCapacityBlockedForFailure("animal_food_inventory_full"),
                "a completely filled job inventory must retain its supply-capacity warning");
        helper.assertValueEqual(
                ClipboardWorkforceService.previewStatus(HiredVillagerRole.ANIMAL_HANDLING, paused, inventory),
                ClipboardWorkforceSnapshot.WorkerStatus.INVENTORY_FULL,
                "a genuinely full animal handler should refresh its marker status to inventory full");

        animalHandler.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void assignedRouteSatisfiesWorkSiteAndTakesPriority(GameTestHelper helper) {
        HiredVillagerIndex.clearRuntimeState();
        buildFloor(helper, 0, 32, 0, 32, 1);
        ServerLevel level = helper.getLevel();
        level.setDayTime(1000L);
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        movePlayer(helper, hirer, new BlockPos(1, 2, 1));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.WEAPONSMITH));
        Villager otherVillager = spawnVillager(helper, new BlockPos(3, 2, 2));

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8, HiredVillagerRole.COMBAT);
        BlockPos firstRouteNode = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos secondRouteNode = helper.absolutePos(new BlockPos(6, 2, 2));
        BlockPos branchAnchor = helper.absolutePos(new BlockPos(4, 2, 2));
        BlockPos branchEnd = helper.absolutePos(new BlockPos(4, 2, 18));
        HiredRoute route = new HiredRoute(
                List.of(firstRouteNode, secondRouteNode),
                false,
                List.of(new HiredRoute.Branch(branchAnchor, branchEnd)));
        helper.assertTrue(
                HiredVillagerWorkService.setRoute(hirer, level, villager, route),
                "route assignment should succeed");
        helper.assertValueEqual(
                HiredVillagerWorkService.route(level, villager).branches().size(),
                1,
                "assigned branch should survive route persistence");

        HiredWorkSession routeOnly = HiredWorkSession.active(level, villager);
        helper.assertFalse(routeOnly.context().hasWorkArea(), "route-only combat worker should not need a work site");
        helper.assertTrue(
                HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager),
                "route-assigned worker should suppress vanilla social AI before route movement starts");
        helper.assertTrue(
                HiredVillagerWorkService.hasEffectiveWorkArea(level, villager, routeOnly),
                "route should satisfy the effective work assignment");
        helper.assertTrue(
                HiredVillagerWorkService.isInsideEffectiveWorkArea(
                        level,
                        villager,
                        HiredVillagerRole.COMBAT,
                        routeOnly.context(),
                        firstRouteNode),
                "route node should be inside the effective assignment");
        BlockPos branchReach = helper.absolutePos(new BlockPos(4, 2, 30));
        helper.assertTrue(
                routeOnly.context().isInsideRouteArea(branchReach),
                "branch should extend route filtering beyond the base route corridor");

        villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, otherVillager);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(otherVillager.blockPosition()));
        HiredVillagerFocusService.onVillagerTickPre(villager);
        helper.assertFalse(
                villager.getBrain().hasMemoryValue(MemoryModuleType.INTERACTION_TARGET),
                "route-assigned worker should drop social interaction targets");
        helper.assertFalse(
                villager.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                "route-assigned worker should drop social look targets before movement starts");

        otherVillager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, villager);
        otherVillager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(villager.blockPosition()));
        otherVillager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(villager.blockPosition()), 0.4F, 2));
        HiredVillagerFocusService.onVillagerTickPre(otherVillager);
        helper.assertFalse(
                otherVillager.getBrain().hasMemoryValue(MemoryModuleType.INTERACTION_TARGET),
                "nearby villagers should not keep a busy hired worker as an interaction target");
        helper.assertFalse(
                otherVillager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "nearby villagers should not walk over to socialize with a busy hired worker");

        BlockPos workSiteMin = helper.absolutePos(new BlockPos(28, 2, 28));
        BlockPos workSiteMax = helper.absolutePos(new BlockPos(30, 4, 30));
        helper.assertTrue(
                HiredVillagerWorkService.setWorkArea(hirer, level, villager, workSiteMin, workSiteMax),
                "work site assignment should succeed while a route exists");
        HiredWorkSession routeAndSite = HiredWorkSession.active(level, villager);
        BlockPos insideWorkSite = helper.absolutePos(new BlockPos(29, 2, 29));
        helper.assertTrue(routeAndSite.context().isInsideWorkArea(insideWorkSite), "fixture position should be inside the assigned work site");
        helper.assertFalse(
                routeAndSite.context().isInsideWorkAreaOrRoute(insideWorkSite),
                "route-priority target filters should ignore the assigned work site");
        helper.assertFalse(
                HiredVillagerWorkService.isInsideEffectiveWorkArea(
                        level,
                        villager,
                        HiredVillagerRole.COMBAT,
                        routeAndSite.context(),
                        insideWorkSite),
                "route should take priority over an assigned work site");
        helper.assertTrue(
                HiredVillagerWorkService.isInsideEffectiveWorkArea(
                        level,
                        villager,
                        HiredVillagerRole.COMBAT,
                        routeAndSite.context(),
                        firstRouteNode),
                "route should remain the effective assignment");

        HiredVillagerIndex.update(level, villager);
        ClipboardWorkforceSnapshot snapshot = ClipboardWorkforceService.snapshot(hirer);
        helper.assertValueEqual(snapshot.workers().size(), 1, "clipboard worker rows");
        ClipboardWorkforceSnapshot.WorkerRow row = snapshot.workers().getFirst();
        helper.assertFalse(row.noWorkArea(), "clipboard should not report missing work area for a route-assigned worker");
        helper.assertFalse(row.tooFar(), "clipboard should not report too far while the worker is on its route");
        helper.assertValueEqual(row.areaStatus(), "route", "clipboard assignment source");

        helper.assertTrue(
                HiredVillagerWorkService.clearRoute(hirer, level, villager),
                "route clear should succeed");
        HiredWorkSession siteOnly = HiredWorkSession.active(level, villager);
        helper.assertFalse(siteOnly.context().hasRoute(), "cleared route should no longer be active");
        helper.assertTrue(
                HiredVillagerWorkService.hasEffectiveWorkArea(level, villager, siteOnly),
                "work site should satisfy the effective assignment after route clear");
        helper.assertTrue(
                HiredVillagerWorkService.isInsideEffectiveWorkArea(
                        level,
                        villager,
                        HiredVillagerRole.COMBAT,
                        siteOnly.context(),
                        insideWorkSite),
                "work site should take over after route clear");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        HiredVillagerIndex.clearRuntimeState();
        otherVillager.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200, batch = "clipboard_nitwit_diagnostics")
    public static void clipboardDoesNotInventAssignmentsForNitwitWorkers(GameTestHelper helper) {
        HiredVillagerIndex.clearRuntimeState();
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrClipboardNitwitDiagnostics");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));

        HiredVillagerContractService.startHireContract(
                level, villager, hirer, 1, 8, HiredVillagerRole.NITWIT);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.IDLE, null);
        HiredVillagerIndex.update(level, villager);

        ClipboardWorkforceSnapshot.WorkerRow row = ClipboardWorkforceService.snapshot(hirer).workers().getFirst();
        helper.assertFalse(row.noStorage(), "nitwit work should not require an output container");
        helper.assertFalse(row.noWorkArea(), "nitwit work should not require a work area");
        helper.assertFalse(row.tooFar(), "nitwit work should not be measured against a nonexistent work area");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        HiredVillagerIndex.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200, batch = "clipboard_stale_target_diagnostics")
    public static void clipboardIgnoresStaleTargetFailuresDuringActiveWork(GameTestHelper helper) {
        HiredVillagerIndex.clearRuntimeState();
        buildFloor(helper, 0, 8, 0, 6, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrClipboardStaleTarget");
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 3));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));

        HiredVillagerContractService.startHireContract(
                level, villager, hirer, 1, 8, HiredVillagerRole.FARMING);
        helper.assertTrue(
                HiredVillagerWorkService.setWorkArea(
                        hirer,
                        level,
                        villager,
                        helper.absolutePos(new BlockPos(1, 2, 1)),
                        helper.absolutePos(new BlockPos(6, 4, 5))),
                "stale-target fixture work area should be assigned");
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        HiredWorkerBrain.setLastTargetScanResult(session.context(), "field_scan_full_no_targets");
        HiredWorkerBrain.setFailure(session.context(), "target_unreachable", level.getGameTime() + 100L);
        HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.WORKING, villager.blockPosition());
        HiredVillagerIndex.update(level, villager);

        ClipboardWorkforceSnapshot active = ClipboardWorkforceService.snapshot(hirer);
        helper.assertFalse(
                active.workers().getFirst().noTargets(),
                "stale target diagnostics must not override an actively working task");

        HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.FAILED_COOLDOWN, villager.blockPosition());
        ClipboardWorkforceSnapshot blocked = ClipboardWorkforceService.snapshot(hirer);
        helper.assertTrue(
                blocked.workers().getFirst().noTargets(),
                "the same target failure should be reported while it is the current blocked state");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        HiredVillagerIndex.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200, batch = "clipboard_builder_diagnostics")
    public static void clipboardClassifiesBuilderStorageRootCauses(GameTestHelper helper) {
        HiredVillagerIndex.clearRuntimeState();
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrClipboardBuilderCauses");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.MASON));

        HiredVillagerContractService.startOneOffBuilderJob(level, villager, hirer);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        session.state().putBoolean("Enabled", true);
        HiredVillagerIndex.update(level, villager);

        HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.WAITING_FOR_MATERIALS, null);
        HiredWorkerBrain.setFailure(session.context(), "missing_builder_materials_storage_too_far", 0L);
        ClipboardWorkforceSnapshot.WorkerRow tooFar = ClipboardWorkforceService.snapshot(hirer).workers().getFirst();
        helper.assertTrue(tooFar.materialStorageUnreachable(), "distant builder storage should be reported as unavailable");
        helper.assertFalse(tooFar.missingMaterials(), "distant storage should not be mislabeled as missing materials");

        HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.PAUSED_FULL_INVENTORY, villager.blockPosition());
        HiredWorkerBrain.setFailure(session.context(), "builder_material_output_storage_unreachable", level.getGameTime() + 100L);
        ClipboardWorkforceSnapshot.WorkerRow unreachable = ClipboardWorkforceService.snapshot(hirer).workers().getFirst();
        helper.assertTrue(unreachable.materialStorageUnreachable(), "blocked builder output storage should report the path issue");
        helper.assertFalse(unreachable.materialInventoryFull(), "a blocked storage path is not a material-capacity issue");
        helper.assertFalse(unreachable.inventoryFull(), "the root storage-path issue should suppress the generic inventory warning");

        HiredWorkerBrain.setFailure(session.context(), "builder_material_inventory_full", level.getGameTime() + 100L);
        ClipboardWorkforceSnapshot.WorkerRow full = ClipboardWorkforceService.snapshot(hirer).workers().getFirst();
        helper.assertTrue(full.materialInventoryFull(), "builder material capacity should retain its specialized warning");
        helper.assertFalse(full.materialStorageUnreachable(), "material capacity should not be reported as an unreachable path");
        helper.assertFalse(full.inventoryFull(), "specialized material capacity should suppress the generic inventory warning");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        HiredVillagerIndex.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    private static void runWorkerUntil(
            GameTestHelper helper,
            HiredRoleWorker worker,
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            HiredWorkContext context,
            int maxTicks,
            java.util.function.BooleanSupplier done) {
        for (int tick = 0; tick < maxTicks && !done.getAsBoolean(); tick++) {
            worker.maintain(level, villager, context);
            worker.tick(level, villager, hirer, context);
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        if (!done.getAsBoolean()) {
            HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
            String debug = worker instanceof LoggingWorker ? " " + LoggingWorker.debugSummary(context) : "";
            BlockPos navTarget = villager.getNavigation().getTargetPos();
            BlockPos activeTarget = context.state().contains("ActiveWorkBlockPos")
                    ? BlockPos.of(context.state().getLong("ActiveWorkBlockPos"))
                    : null;
            BlockPos activeApproach = context.state().contains("ActiveWorkApproachPos")
                    ? BlockPos.of(context.state().getLong("ActiveWorkApproachPos"))
                    : null;
            throw new GameTestAssertException("Worker did not reach expected state in " + maxTicks
                    + " direct ticks; task=" + snapshot.taskState()
                    + ", failure=" + snapshot.failureReason()
                    + ", scan=" + snapshot.lastTargetScanResult()
                    + ", progress=" + context.progressTicks()
                    + ", pos=" + villager.blockPosition()
                    + ", precise=(" + String.format(java.util.Locale.ROOT, "%.2f", villager.getX())
                    + "," + String.format(java.util.Locale.ROOT, "%.2f", villager.getY())
                    + "," + String.format(java.util.Locale.ROOT, "%.2f", villager.getZ()) + ")"
                    + ", nav=" + navTarget
                    + ", active=" + activeTarget
                    + ", approach=" + activeApproach
                    + ", ladderRoute=" + VillagerTaskNavigationUtil.ladderRouteDebug(
                    level,
                    villager,
                    activeApproach == null ? context.workCenter() : activeApproach)
                    + debug);
        }
    }

    private static void runHiredMiningServiceUntil(
            GameTestHelper helper,
            ServerLevel level,
            Villager villager,
            BlockPos workMinRel,
            BlockPos workMaxRel,
            int maxTicks,
            java.util.function.BooleanSupplier done) {
        int lastRemainingBlocks = remainingMixedExcavationBlocks(level, helper, workMinRel, workMaxRel);
        int ticksSinceBlockProgress = 0;
        for (int tick = 0; tick < maxTicks && !done.getAsBoolean(); tick++) {
            level.setDayTime(1000L);
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData())
                    .setGameTime(level.getGameTime() + 1L);
            villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            HiredVillagerContractService.onVillagerTickPost(villager);
            HiredVillagerWorkService.onVillagerTickPost(villager);
            VillagerTaskNavigationUtil.tickVillagerWaterSafety(level, villager);
            VillagerTaskNavigationUtil.tickPathDoors(level, villager);
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);

            int remainingBlocks = remainingMixedExcavationBlocks(level, helper, workMinRel, workMaxRel);
            if (remainingBlocks < lastRemainingBlocks) {
                lastRemainingBlocks = remainingBlocks;
                ticksSinceBlockProgress = 0;
            } else if (remainingBlocks > 0) {
                ticksSinceBlockProgress++;
            }
            if (ticksSinceBlockProgress > 3000) {
                throw mixedMiningServiceFailure(
                        helper,
                        level,
                        villager,
                        workMinRel,
                        workMaxRel,
                        "Hired mining service stalled for " + ticksSinceBlockProgress
                                + " ticks without clearing another original block");
            }
        }
        if (!done.getAsBoolean()) {
            throw mixedMiningServiceFailure(
                    helper,
                    level,
                    villager,
                    workMinRel,
                    workMaxRel,
                    "Hired mining service did not finish mixed excavation in " + maxTicks + " ticks");
        }
    }

    private static GameTestAssertException mixedMiningServiceFailure(
            GameTestHelper helper,
            ServerLevel level,
            Villager villager,
            BlockPos workMinRel,
            BlockPos workMaxRel,
            String reason) {
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        BlockPos navTarget = villager.getNavigation().getTargetPos();
        BlockPos entryTarget = MiningWorker.excavationEntryTarget(level, session.context());
        BlockPos returnTarget = MiningWorker.excavationReturnTarget(level, villager, session.context());
        return new GameTestAssertException(reason
                + "; task=" + snapshot.taskState()
                + ", failure=" + snapshot.failureReason()
                + ", scan=" + snapshot.lastTargetScanResult()
                + ", status=" + session.state().getString("Status")
                + ", progress=" + session.context().progressTicks()
                + ", progressTime=" + session.state().getLong("LastMiningBreakProgressGameTime")
                + ", now=" + level.getGameTime()
                + ", layer=" + MiningBlockRules.currentExcavationLayer(level, session.context())
                + ", pos=" + villager.blockPosition()
                + ", precise=(" + String.format(java.util.Locale.ROOT, "%.2f", villager.getX())
                + "," + String.format(java.util.Locale.ROOT, "%.2f", villager.getY())
                + "," + String.format(java.util.Locale.ROOT, "%.2f", villager.getZ()) + ")"
                + ", nav=" + navTarget
                + ", storage=" + snapshot.storageTargetPos()
                + ", target=" + snapshot.targetPos()
                + ", entry=" + entryTarget
                + ", return=" + returnTarget
                + ", remainingBlocks=" + remainingMixedExcavationBlocks(level, helper, workMinRel, workMaxRel)
                + ", remainingOutput=" + session.inventory().hasOutputItems()
                + ", ladders=" + ladderSummary(level, helper, workMinRel, workMaxRel));
    }


    private static void fillMixedExcavationBox(GameTestHelper helper, BlockPos min, BlockPos max) {
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockState state;
                    if (y == max.getY()) {
                        state = Blocks.GRASS_BLOCK.defaultBlockState();
                    } else if (y == max.getY() - 1) {
                        state = Blocks.DIRT.defaultBlockState();
                    } else if (Math.floorMod(x + y + z, 5) == 0) {
                        state = Blocks.IRON_ORE.defaultBlockState();
                    } else {
                        state = Blocks.STONE.defaultBlockState();
                    }
                    setBlock(helper, new BlockPos(x, y, z), state);
                }
            }
        }
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                setBlock(helper, new BlockPos(x, min.getY() - 1, z), Blocks.BEDROCK.defaultBlockState());
                setBlock(helper, new BlockPos(x, max.getY() + 1, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, max.getY() + 2, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void buildStoragePlatform(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ, int floorY) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                setBlock(helper, new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState());
                setBlock(helper, new BlockPos(x, floorY + 1, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, floorY + 2, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void buildTallLadderFixture(GameTestHelper helper, int x, int minY, int z, int maxY) {
        setBlock(helper, new BlockPos(x + 1, minY - 1, z), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, minY, z), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, minY + 1, z), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, maxY, z), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, maxY + 1, z), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, maxY + 2, z), Blocks.AIR.defaultBlockState());
        for (int y = minY; y <= maxY; y++) {
            setBlock(helper, new BlockPos(x, y, z - 1), Blocks.STONE.defaultBlockState());
            setBlock(
                    helper,
                    new BlockPos(x, y, z),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            if (y > minY && y < maxY) {
                setBlock(helper, new BlockPos(x + 1, y, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x + 1, y + 1, z), Blocks.AIR.defaultBlockState());
            }
        }
        setBlock(helper, new BlockPos(x + 1, maxY, z), Blocks.STONE.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, maxY + 1, z), Blocks.AIR.defaultBlockState());
        setBlock(helper, new BlockPos(x + 1, maxY + 2, z), Blocks.AIR.defaultBlockState());
    }

    private static void moveVillagerToBlock(Villager villager, BlockPos pos) {
        VillagerTaskNavigationUtil.clearRuntimeState(villager);
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        villager.setNoGravity(false);
        villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
    }

    private static void runLadderNavigationUntil(
            GameTestHelper helper,
            ServerLevel level,
            Villager villager,
            BlockPos target,
            int maxTicks,
            String message) {
        for (int tick = 0; tick < maxTicks && !villager.blockPosition().equals(target); tick++) {
            VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, target, 0.55D);
            VillagerTaskNavigationUtil.tickPathLadders(level, villager);
            level.tickNonPassenger(villager);
        }
        helper.assertValueEqual(villager.blockPosition(), target, message);
    }

    private static boolean mixedExcavationBoxCleared(ServerLevel level, GameTestHelper helper, BlockPos min, BlockPos max) {
        return remainingMixedExcavationBlocks(level, helper, min, max) == 0;
    }

    private static int remainingMixedExcavationBlocks(ServerLevel level, GameTestHelper helper, BlockPos min, BlockPos max) {
        int remaining = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (isOriginalMixedExcavationMaterial(level.getBlockState(helper.absolutePos(new BlockPos(x, y, z))))) {
                        remaining++;
                    }
                }
            }
        }
        return remaining;
    }

    private static String ladderSummary(ServerLevel level, GameTestHelper helper, BlockPos min, BlockPos max) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        StringBuilder builder = new StringBuilder("[");
        int count = 0;
        for (int x = min.getX() - 1; x <= max.getX() + 1; x++) {
            for (int y = min.getY() - 1; y <= max.getY() + 1; y++) {
                for (int z = min.getZ() - 1; z <= max.getZ() + 1; z++) {
                    BlockPos absolute = helper.absolutePos(new BlockPos(x, y, z));
                    if (!level.getBlockState(absolute).is(Blocks.LADDER)) {
                        continue;
                    }
                    if (count++ > 0) {
                        builder.append(",");
                    }
                    builder.append("(")
                            .append(absolute.getX() - origin.getX()).append(",")
                            .append(absolute.getY() - origin.getY()).append(",")
                            .append(absolute.getZ() - origin.getZ()).append(")");
                }
            }
        }
        builder.append("]");
        return builder.toString();
    }

    private static boolean isOriginalMixedExcavationMaterial(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.STONE)
                || state.is(Blocks.IRON_ORE);
    }

    private static HiredWorkContext context(
            GameTestHelper helper,
            Villager villager,
            CompoundTag state,
            BlockPos minRelative,
            BlockPos maxRelative,
            boolean hasWorkArea) {
        return context(helper, villager, state, minRelative, maxRelative, hasWorkArea, 100);
    }

    private static HiredWorkContext context(
            GameTestHelper helper,
            Villager villager,
            CompoundTag state,
            BlockPos minRelative,
            BlockPos maxRelative,
            boolean hasWorkArea,
            int transferCapacityPercent) {
        BlockPos min = helper.absolutePos(minRelative);
        BlockPos max = helper.absolutePos(maxRelative);
        BlockPos center = new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2);
        HiredWorkerBrain.initialize(state);
        return new HiredWorkContext(
                HiredJobInventory.getJobInventory(villager),
                state,
                center,
                min,
                max,
                Math.max(4, Math.max(max.getX() - min.getX(), max.getZ() - min.getZ())),
                Math.max(2, max.getY() - min.getY()),
                hasWorkArea,
                50,
                100,
                transferCapacityPercent,
                100,
                true,
                true);
    }

    private static final class OutputDepositProbe extends AbstractBlockWorker {
        private DepositResult deposit(ServerLevel level, HiredWorkContext context, Villager villager) {
            return depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
        }

        @Override
        public HiredVillagerRole role() {
            return HiredVillagerRole.FARMING;
        }

        @Override
        public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
            return WorkResult.idle("interaction.work.status.idle");
        }
    }

    private static HiredWorkContext routeContext(
            GameTestHelper helper,
            Villager villager,
            CompoundTag state,
            List<BlockPos> routeRelativeNodes) {
        return routeContext(helper, villager, state, routeRelativeNodes, 100);
    }

    private static HiredWorkContext routeContext(
            GameTestHelper helper,
            Villager villager,
            CompoundTag state,
            List<BlockPos> routeRelativeNodes,
            int transferCapacityPercent) {
        List<BlockPos> routeNodes = new ArrayList<>();
        for (BlockPos node : routeRelativeNodes) {
            routeNodes.add(helper.absolutePos(node));
        }
        return routeContext(
                helper,
                villager,
                state,
                new HiredRoute(routeNodes, false),
                transferCapacityPercent);
    }

    private static HiredWorkContext routeContext(
            GameTestHelper helper,
            Villager villager,
            CompoundTag state,
            HiredRoute route,
            int transferCapacityPercent) {
        BlockPos center = route.nodes().isEmpty() ? helper.absolutePos(BlockPos.ZERO) : route.nodes().getFirst();
        HiredWorkArea disabledArea = HiredWorkArea.fromCenter(center, 1, 2, false).asUsable(false);
        HiredWorkerBrain.initialize(state);
        return new HiredWorkContext(
                HiredJobInventory.getJobInventory(villager),
                state,
                center,
                disabledArea.min(),
                disabledArea.max(),
                disabledArea.horizontalRadius(),
                disabledArea.verticalRadius(),
                false,
                50,
                100,
                transferCapacityPercent,
                100,
                true,
                true,
                HiredJobSite.fromWorkArea(disabledArea),
                route);
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        double dx = first.getX() + 0.5D - (second.getX() + 0.5D);
        double dz = first.getZ() + 0.5D - (second.getZ() + 0.5D);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static UUID seedBuilderTask(CompoundTag state, int paidCurrency, int placedIndex) {
        CompoundTag task = new CompoundTag();
        UUID jobId = UUID.randomUUID();
        task.putString("JobId", jobId.toString());
        task.putString("StructureId", "villagerretaliation:test_structure");
        task.putString("StructureLabel", "Test Structure");
        task.putLong("Origin", BlockPos.ZERO.asLong());
        task.putString("Rotation", "NONE");
        task.putString("Phase", "building");
        task.putInt("PlacedIndex", Math.max(0, placedIndex));
        task.putInt("TotalBlocks", 8);
        task.putInt("PaidCurrency", Math.max(0, paidCurrency));
        task.putLong("StartedGameTime", 1L);
        state.put(BuilderTaskState.TASK_TAG, task);
        return jobId;
    }

    private static CompoundTag persistentWorkState(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(WORK_STATE_TAG)) {
            data.put(WORK_STATE_TAG, new CompoundTag());
        }
        CompoundTag state = data.getCompound(WORK_STATE_TAG);
        HiredWorkerBrain.initialize(state);
        return state;
    }

    private static int countCurrency(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (VillagerCurrencyResources.isCurrency(player.serverLevel().getServer(), stack)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (VillagerCurrencyResources.isCurrency(player.serverLevel().getServer(), stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countDroppedCurrency(ServerLevel level, BlockPos center) {
        int count = 0;
        AABB area = new AABB(center).inflate(4.0D);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (VillagerCurrencyResources.isCurrency(level.getServer(), entity.getItem())) {
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

    private static <T extends Animal> T spawnAnimal(GameTestHelper helper, EntityType<T> type, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        T animal = type.create(level);
        if (animal == null) {
            throw new GameTestAssertException("Could not create animal " + type);
        }
        BlockPos pos = helper.absolutePos(relativePos);
        animal.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(animal)) {
            throw new GameTestAssertException("Could not add animal to level");
        }
        level.tickNonPassenger(animal);
        return animal;
    }

    private static <T extends Animal> int countAliveAnimals(
            ServerLevel level,
            GameTestHelper helper,
            Class<T> animalClass,
            BlockPos minRelative,
            BlockPos maxRelative) {
        BlockPos min = helper.absolutePos(minRelative);
        BlockPos max = helper.absolutePos(maxRelative);
        AABB bounds = new AABB(
                min.getX(),
                min.getY(),
                min.getZ(),
                max.getX() + 1.0D,
                max.getY() + 1.0D,
                max.getZ() + 1.0D);
        return level.getEntitiesOfClass(animalClass, bounds, Animal::isAlive).size();
    }

    private static <T extends Animal> int countAliveAdultAnimals(
            ServerLevel level,
            GameTestHelper helper,
            Class<T> animalClass,
            BlockPos minRelative,
            BlockPos maxRelative) {
        BlockPos min = helper.absolutePos(minRelative);
        BlockPos max = helper.absolutePos(maxRelative);
        AABB bounds = new AABB(
                min.getX(),
                min.getY(),
                min.getZ(),
                max.getX() + 1.0D,
                max.getY() + 1.0D,
                max.getZ() + 1.0D);
        return level.getEntitiesOfClass(animalClass, bounds, animal -> animal.isAlive() && !animal.isBaby()).size();
    }

    private static <T extends Animal> int countAliveBabyAnimals(
            ServerLevel level,
            GameTestHelper helper,
            Class<T> animalClass,
            BlockPos minRelative,
            BlockPos maxRelative) {
        BlockPos min = helper.absolutePos(minRelative);
        BlockPos max = helper.absolutePos(maxRelative);
        AABB bounds = new AABB(
                min.getX(),
                min.getY(),
                min.getZ(),
                max.getX() + 1.0D,
                max.getY() + 1.0D,
                max.getZ() + 1.0D);
        return level.getEntitiesOfClass(animalClass, bounds, animal -> animal.isAlive() && animal.isBaby()).size();
    }

    private static HiredBrewingRecipeCatalog.BrewingRoute brewingRoute(
            ServerLevel level,
            ItemStack output) {
        return HiredBrewingRecipeCatalog.routes(level).stream()
                .filter(route -> ItemStack.isSameItemSameComponents(route.output(), output))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Expected brewing route for " + output));
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("villagerretaliation:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(id, name));
        BlockPos spawn = level.getSharedSpawnPos();
        player.moveTo(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    private static void movePlayer(GameTestHelper helper, ServerPlayer player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
    }

    private static void tickVillager(ServerLevel level, Villager villager, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            level.tickNonPassenger(villager);
        }
    }

    private static Container container(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            return container;
        }
        throw new GameTestAssertException("Expected container at " + pos);
    }

    private static int countItem(Container container, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countInventoryItem(HiredJobInventory inventory, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countInventoryItem(
            HiredJobInventory inventory,
            net.minecraft.world.item.Item item,
            HiredJobInventorySlotType slotType) {
        int count = 0;
        for (int slot = 0; slot < HiredJobInventory.SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item) && inventory.slotType(slot) == slotType) {
                count += stack.getCount();
            }
        }
        return count;
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
