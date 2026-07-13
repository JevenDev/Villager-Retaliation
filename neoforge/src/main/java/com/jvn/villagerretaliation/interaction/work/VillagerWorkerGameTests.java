package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.work.logging.LoggingWorker;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.interaction.work.mining.HiredOreBlockTracker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHorizontalOptions;
import com.jvn.villagerretaliation.interaction.work.mining.MiningExcavationSupport;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
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
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.HiredJobInventorySlotType;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.mixin.AbstractArrowAccessor;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderPaymentEscrowService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
                AssignedStorageService.INPUT_PURPOSE);
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
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.BUTCHER));
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
        VillagerWalletService.spendCurrency(villager, walletCurrency, VillagerWalletService.WalletSource.DEBUG);

        BuilderPaymentEscrowService.escrow(villager, jobId, 23);
        int refunded = BuilderPaymentEscrowService.refund(hirer, villager, Optional.of(jobId), 23);
        VillagerWalletService.addCurrency(villager, 50, VillagerWalletService.WalletSource.DEBUG);
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
                AssignedStorageService.TOOL_PURPOSE);
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
                        .anyMatch(record -> AssignedStorageService.TOOL_PURPOSE.equals(record.purpose())),
                "tool storage purpose should persist");
        helper.assertTrue(
                AssignedStorageService.assignedStorage(level, villager).stream()
                        .anyMatch(record -> AssignedStorageService.OUTPUT_PURPOSE.equals(record.purpose())),
                "output storage purpose should persist");

        AssignedStorageService.AssignSummary duplicateTool = AssignedStorageService.assign(
                hirer,
                villager,
                List.of(storage),
                AssignedStorageService.TOOL_PURPOSE);
        helper.assertValueEqual(duplicateTool.alreadyAssigned(), 1, "duplicate tool storage assignment should not add another record");

        AssignedStorageService.AssignSummary shared = AssignedStorageService.assign(
                hirer,
                otherVillager,
                List.of(storage),
                AssignedStorageService.INPUT_PURPOSE);
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
                AssignedStorageService.INPUT_PURPOSE);
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

        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.insertOutput(new ItemStack(Items.COBBLESTONE, 7));
        helper.assertTrue(inventory.depositOutputToAssignedStorage(), "output should deposit to assigned output storage");
        helper.assertValueEqual(countItem(container(level, output), Items.COBBLESTONE), 7, "output chest item count");
        helper.assertValueEqual(countItem(container(level, input), Items.COBBLESTONE), 0, "input chest should not receive outputs first");

        AssignedStorageService.removeAllAssignedStorage(level, villager);
        AssignedStorageService.removeAllAssignedStorage(level, otherVillager);
        villager.discard();
        otherVillager.discard();
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

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 320)
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

        runWorkerUntil(helper, worker, level, villager, hirer, context, 240, () ->
                level.getBlockState(helper.absolutePos(targetRel)).isAir());

        helper.assertTrue(level.getBlockState(helper.absolutePos(targetRel)).isAir(),
                "miner should descend from the surface side and mine the valid lower target");
        helper.assertTrue(context.inventory().hasOutput(stack -> stack.is(Items.COBBLESTONE)),
                "returned excavation should store mined drops as output");
        helper.assertTrue(context.isInsideWorkArea(villager.blockPosition()),
                "miner should finish the recovered excavation inside the work area");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80000)
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

        Villager villager = spawnVillager(helper, new BlockPos(11, 6, 6));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        pinHiredWorkServicePhase(level, villager);
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 64);
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
        helper.assertValueEqual(MiningWorker.phase(context), "find_target",
                "mode change should reset the typed mining phase");

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

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void sharedRouteNavigatorApproachesNonStandableContainerNode(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 2));
        tickVillager(level, villager, 20);
        BlockPos nodeRel = new BlockPos(5, 2, 2);
        BlockPos node = helper.absolutePos(nodeRel);
        setBlock(helper, nodeRel, Blocks.CHEST.defaultBlockState());

        CompoundTag state = new CompoundTag();
        HiredWorkContext context = routeContext(helper, villager, state, List.of(nodeRel));
        for (int tick = 0; tick < 100 && villager.blockPosition().distSqr(node) > 4.0D; tick++) {
            HiredRouteNavigator.maintainRoute(level, villager, context, 0.5D);
            level.tickNonPassenger(villager);
        }

        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        helper.assertTrue(villager.blockPosition().distSqr(node) <= 4.0D,
                "shared route navigation should reach a valid block beside a non-standable node; pos="
                        + villager.blockPosition() + ", nav=" + villager.getNavigation().getTargetPos()
                        + ", state=" + snapshot.taskState() + ", failure=" + snapshot.failureReason());
        helper.assertFalse(villager.blockPosition().equals(node),
                "shared route navigation should not try to stand inside a container node");

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
                AssignedStorageService.INPUT_PURPOSE).assigned(), 1, "courier input assignment");
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

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void courierHandlesContainerRouteNodesAndRepeatsMultipleInputLoop(GameTestHelper helper) {
        buildFloor(helper, 0, 12, 0, 5, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrCourierMultiInput");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 2));
        BlockPos firstInputRel = new BlockPos(2, 2, 2);
        BlockPos secondInputRel = new BlockPos(6, 2, 2);
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
        container(level, firstInput).setItem(0, new ItemStack(Items.COBBLESTONE, 20));
        container(level, secondInput).setItem(0, new ItemStack(Items.DIRT, 13));

        helper.assertValueEqual(AssignedStorageService.assign(
                hirer,
                villager,
                List.of(
                        new AssignedStorageService.StoragePosition(level.dimension(), firstInput),
                        new AssignedStorageService.StoragePosition(level.dimension(), secondInput)),
                AssignedStorageService.INPUT_PURPOSE).assigned(), 2, "courier multi-input assignment");
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

        runWorkerUntil(helper, worker, level, villager, hirer, context, 240, () ->
                countItem(container(level, output), Items.COBBLESTONE) == 20
                        && countItem(container(level, output), Items.DIRT) == 13
                        && "pickup".equals(state.getString("CourierPhase")));

        helper.assertValueEqual(countItem(container(level, firstInput), Items.COBBLESTONE), 0,
                "courier should empty the first route input");
        helper.assertValueEqual(countItem(container(level, secondInput), Items.DIRT), 0,
                "courier should visit the second input container route node");

        container(level, firstInput).setItem(0, new ItemStack(Items.COBBLESTONE, 7));
        container(level, secondInput).setItem(0, new ItemStack(Items.DIRT, 5));
        runWorkerUntil(helper, worker, level, villager, hirer, context, 240, () ->
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
        helper.assertFalse(villager.blockPosition().equals(firstInput),
                "courier should use a valid adjacent standing block for the first container node");

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

        HiredVillagerContractService.endHireContract(level, villager, hirer);
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

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
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
        int closeEnough = villager.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getCloseEnoughDist)
                .orElse(-1);
        helper.assertValueEqual(closeEnough, 0, "strict work return should require exact arrival");

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

        ClipboardWorkforceSnapshot snapshot = ClipboardWorkforceService.snapshot(hirer);
        helper.assertValueEqual(snapshot.workers().size(), 1, "clipboard worker rows");
        helper.assertFalse(snapshot.workers().getFirst().noWorkArea(), "clipboard should not show missing work area for a claimed job-block site");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
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
        HiredRoute route = new HiredRoute(List.of(firstRouteNode, secondRouteNode), false);
        helper.assertTrue(
                HiredVillagerWorkService.setRoute(hirer, level, villager, route),
                "route assignment should succeed");

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

    private static void pinHiredWorkServicePhase(ServerLevel level, Villager villager) {
        int interval = Math.max(10, VillagerRetaliationConfig.HIRED_WORK_TICK_INTERVAL.get());
        long least = Math.floorMod(-level.getGameTime(), interval);
        villager.setUUID(new UUID(0x564d696e6572544cL, least));
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
                100,
                true,
                true);
    }

    private static HiredWorkContext routeContext(
            GameTestHelper helper,
            Villager villager,
            CompoundTag state,
            List<BlockPos> routeRelativeNodes) {
        List<BlockPos> routeNodes = new ArrayList<>();
        for (BlockPos node : routeRelativeNodes) {
            routeNodes.add(helper.absolutePos(node));
        }
        BlockPos center = routeNodes.isEmpty() ? helper.absolutePos(BlockPos.ZERO) : routeNodes.getFirst();
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
                100,
                true,
                true,
                HiredJobSite.fromWorkArea(disabledArea),
                new HiredRoute(routeNodes, false));
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
