package com.jvn.villagerretaliation.interaction.work;
import com.jvn.villagerretaliation.interaction.work.production.*;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation_production_features")
@PrefixGameTestTemplate(false)
public final class ProductionFeaturesGameTests {
    private static final String BONUS_TAG = "VillagerRetaliationCookBatch";

    @GameTest(template = "empty")
    public static void nonFoodRecipesRespectNativeStationAndComponents(GameTestHelper h) {
        var level = h.getLevel();
        @SuppressWarnings("unchecked") var smelt = (RecipeType<AbstractCookingRecipe>)(Object)RecipeType.SMELTING;
        @SuppressWarnings("unchecked") var blast = (RecipeType<AbstractCookingRecipe>)(Object)RecipeType.BLASTING;
        for (var item : java.util.List.of(Items.SAND, Items.COBBLESTONE, Items.STONE, Items.CLAY_BALL, Items.OAK_LOG, Items.IRON_ORE))
            h.assertTrue(SmeltingWorker.isSmeltableOre(level, new ItemStack(item), smelt, ItemStack.EMPTY), "actual non-food smelting recipes are supported");
        h.assertFalse(SmeltingWorker.isSmeltableOre(level, new ItemStack(Items.SAND), blast, ItemStack.EMPTY), "glass has no blast recipe");
        h.assertFalse(SmeltingWorker.isSmeltableOre(level, new ItemStack(Items.POTATO), smelt, ItemStack.EMPTY), "food remains Cook responsibility");
        h.assertFalse(SmeltingWorker.isSmeltableOre(level, new ItemStack(Items.BREAD), smelt, ItemStack.EMPTY), "no invented bread smelting recipe");
        var named = new ItemStack(Items.GLASS); named.set(DataComponents.CUSTOM_NAME, Component.literal("Special glass"));
        h.assertFalse(SmeltingWorker.isDesiredSmeltedOutput(level, ItemStack.EMPTY, named), "unproduced component variants are not claimed");
        for (var recipe : java.util.List.of("glass", "stone", "smooth_stone"))
            h.assertTrue(HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace(recipe), Map.of()).supports(level, HiredVillagerRole.SMELTER), "queue and legacy agree on non-metal support");
        h.assertFalse(HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("baked_potato"), Map.of()).supports(level, HiredVillagerRole.SMELTER), "queued food is not Smelter work");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void exactSmeltingFilterAndDenyRetainTheirSemantics(GameTestHelper h) {
        var level=h.getLevel();
        @SuppressWarnings("unchecked") var type=(RecipeType<AbstractCookingRecipe>)(Object)RecipeType.SMELTING;
        var filter=new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        VillagerRecipeFilterData.setRecipe(filter,level,ResourceLocation.withDefaultNamespace("glass"));
        h.assertTrue(SmeltingWorker.isSmeltableOre(level,new ItemStack(Items.SAND),type,filter),"exact glass accepts sand");
        h.assertFalse(SmeltingWorker.isSmeltableOre(level,new ItemStack(Items.COBBLESTONE),type,filter),"same station does not bypass exact recipe");
        VillagerFilterPolicy.applyChange(filter,VillagerFilterPolicy.PolicyField.LIST_MODE,VillagerFilterPolicy.ListMode.DENY_MATCHING.networkId());
        h.assertFalse(SmeltingWorker.isSmeltableOre(level,new ItemStack(Items.SAND),type,filter),"deny vetoes exact glass");
        h.assertTrue(SmeltingWorker.isSmeltableOre(level,new ItemStack(Items.COBBLESTONE),type,filter),"deny admits other supported recipes");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realGlassConsumesSandFuelAndNativeTime(GameTestHelper h) {
        var f=fixture(h); try {
            f.context().inventory().insertSupply(new ItemStack(Items.SAND,4));
            var order=order(h,"glass",2); HiredProductionService.appendPersonal(f.owner(),f.villager(),0L,order);
            tick(h,f); cook(h,f,199);
            h.assertTrue(furnace(h,f).getItem(2).isEmpty(),"native duration is not shortened");
            cook(h,f,201); tick(h,f); tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.GLASS),2,"two actual glass outputs");
            h.assertValueEqual(f.context().inventory().countItem(Items.SAND),2,"two inputs consumed");
            h.assertValueEqual(f.context().inventory().countItem(Items.COAL),0,"fuel really loaded");
            h.assertValueEqual(queue(h,f).order(order.id()).produced(),2L,"finite quota counts real base output");
        } finally {cleanup(h,f);} h.succeed();
    }

    @GameTest(template = "empty")
    public static void realStoneUsesFurnaceAndRejectsBlastAndBadFuel(GameTestHelper h) {
        var f=fixture(h); try {
            f.context().inventory().insertSupply(new ItemStack(Items.COBBLESTONE,2));
            var order=order(h,"stone",1); HiredProductionService.appendPersonal(f.owner(),f.villager(),0L,order);
            h.getLevel().setBlock(f.station(),Blocks.BLAST_FURNACE.defaultBlockState(),3); tick(h,f);
            h.assertTrue(furnace(h,f).getItem(0).isEmpty(),"blast furnace cannot process stone");
            h.getLevel().setBlock(f.station(),Blocks.FURNACE.defaultBlockState(),3);
            furnace(h,f).setItem(1,new ItemStack(Items.DIRT)); tick(h,f);
            h.assertTrue(furnace(h,f).getItem(0).isEmpty(),"invalid fuel prevents consumption");
            furnace(h,f).setItem(1,ItemStack.EMPTY);
            f.context().inventory().consumeSupply(s->s.is(Items.COAL),1); tick(h,f);
            h.assertTrue(furnace(h,f).getItem(0).isEmpty(),"missing fuel leaves inputs untouched");
            f.context().inventory().insertSupply(new ItemStack(Items.COAL)); tick(h,f); cook(h,f,200); tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.STONE),1,"actual stone recipe completes");
            h.assertValueEqual(f.context().inventory().countItem(Items.COBBLESTONE),1,"only one ingredient consumed");
        } finally {cleanup(h,f);} h.succeed();
    }

    @GameTest(template = "empty")
    public static void lavaFuelPreservesItsBucket(GameTestHelper h) {
        var f=fixture(h); try {
            f.context().inventory().consumeSupply(s->s.is(Items.COAL),1);
            f.context().inventory().insertSupply(new ItemStack(Items.LAVA_BUCKET));
            f.context().inventory().insertSupply(new ItemStack(Items.SAND));
            var order=order(h,"glass",1); HiredProductionService.appendPersonal(f.owner(),f.villager(),0L,order);
            tick(h,f); cook(h,f,200); tick(h,f); tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.GLASS),1,"lava fires a real glass recipe");
            h.assertTrue(furnace(h,f).getItem(1).is(Items.BUCKET) || f.context().inventory().countItem(Items.BUCKET)==1,"fuel container is retained");
        } finally {cleanup(h,f);} h.succeed();
    }

    @GameTest(template = "empty")
    public static void deterministicBonusBoundariesAndUnsafeResults(GameTestHelper h) {
        for(int roll=0;roll<100;roll++) {
            h.assertFalse(CookBonusService.wins(0,roll),"zero disables bonus");
            h.assertTrue(CookBonusService.wins(100,roll),"100 always wins");
            h.assertValueEqual(CookBonusService.wins(10,roll),roll<10,"10 percent exact boundary");
        }
        h.assertFalse(CookBonusService.wins(100,-1),"invalid roll rejected");
        h.assertFalse(CookBonusService.wins(100,100),"out of range roll rejected");
        h.assertTrue(CookBonusService.eligibleCooking(new ItemStack(Items.POTATO),new ItemStack(Items.BAKED_POTATO)),"plain potato eligible");
        h.assertFalse(CookBonusService.eligibleCooking(new ItemStack(Items.KELP),new ItemStack(Items.DRIED_KELP)),"reversible fuel conversions excluded");
        h.assertFalse(CookBonusService.eligibleCooking(new ItemStack(Items.BREAD),new ItemStack(Items.BREAD)),"identity conversion excluded");
        var special=new ItemStack(Items.BAKED_POTATO); special.set(DataComponents.CUSTOM_NAME,Component.literal("Special"));
        h.assertFalse(CookBonusService.eligibleCooking(new ItemStack(Items.POTATO),special),"component result excluded");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breadBonusConsumesThreeWheatAndDoesNotRepeat(GameTestHelper h) {
        int old=chance(100); var f=fixture(h,HiredVillagerRole.COOK); try {
            filter(h,f,"bread"); f.context().inventory().insertSupply(new ItemStack(Items.WHEAT,3));
            tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BREAD),2,"chef creates bread plus one bonus");
            h.assertValueEqual(f.context().inventory().countItem(Items.WHEAT),0,"three real wheat consumed");
            tick(h,f); tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BREAD),2,"failed/repeated work cannot duplicate bonus");
        } finally {chance(old);cleanup(h,f);} h.succeed();
    }

    @GameTest(template = "empty")
    public static void breadAtZeroChanceAndFiniteOrdersUseNormalCounts(GameTestHelper h) {
        int old=chance(0);var f=fixture(h,HiredVillagerRole.COOK);try {
            filter(h,f,"bread");f.context().inventory().insertSupply(new ItemStack(Items.WHEAT,6));tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BREAD),1,"zero chance gives normal output");
            chance(100);var order=order(h,"bread",1);HiredProductionService.appendPersonal(f.owner(),f.villager(),0L,order);tick(h,f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BREAD),2,"explicit finite count suppresses perk");
            h.assertValueEqual(queue(h,f).order(order.id()).produced(),1L,"bonus is never another order receipt");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void continuousBreadOrderGetsPerkButOnlyOneReceipt(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            f.context().inventory().insertSupply(new ItemStack(Items.WHEAT,3));
            var order=continuous(h,"bread");HiredProductionService.appendPersonal(f.owner(),f.villager(),0L,order);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BREAD),2,"continuous chef output includes bonus");
            h.assertValueEqual(queue(h,f).order(order.id()).produced(),1L,"only base recipe completes work");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void breadPreservesInputsWhenBonusCapacityIsUnavailable(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            filter(h,f,"bread");f.context().inventory().insertSupply(new ItemStack(Items.WHEAT,6));fill(f);oneFoodSpace(f,Items.BREAD);
            h.assertTrue(f.context().canStoreOutputs(java.util.List.of(new ItemStack(Items.BREAD))),"normal bread alone fits");
            h.assertFalse(f.context().canStoreOutputs(java.util.List.of(new ItemStack(Items.BREAD,2))),"bonus bread does not fit");
            tick(h,f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.WHEAT),6,"full inventory consumes no ingredients");
            h.assertValueEqual(f.context().inventory().countItem(Items.BREAD),63,"no dropped or partially stored bread");
            clearFill(f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BREAD),65,"one operation resumes after space opens");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void componentBearingWheatProducesNormalBreadWithoutPerk(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            filter(h,f,"bread");var wheat=new ItemStack(Items.WHEAT,3);wheat.set(DataComponents.CUSTOM_NAME,Component.literal("Special wheat"));
            f.context().inventory().insertSupply(wheat);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BREAD),1,"component-bearing ingredients exclude bonus without changing normal craft");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void workerLoadedCookBonusSurvivesNativeSaveAndRepeatedCollection(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            f.context().inventory().insertSupply(new ItemStack(Items.POTATO,2));
            var order=continuous(h,"baked_potato");HiredProductionService.appendPersonal(f.owner(),f.villager(),0L,order);tick(h,f);
            cook(h,f,200);reload(h,f);cook(h,f,200);reload(h,f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BAKED_POTATO),4,"two real transitions award exactly two bonuses across reload");
            h.assertValueEqual(queue(h,f).order(order.id()).produced(),2L,"bonus does not inflate receipts or practice base count");
            tick(h,f);tick(h,f);reload(h,f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BAKED_POTATO),4,"recollection and reload do not duplicate");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void unownedPreexistingOutputNeverGetsBonus(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            furnace(h,f).setItem(2,new ItemStack(Items.BAKED_POTATO,3));tick(h,f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BAKED_POTATO),3,"preexisting player output is collected without perk");
            h.assertTrue(furnace(h,f).getPersistentData().getCompound(BONUS_TAG).isEmpty(),"collection cannot create ownership");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullStorageKeepsEarnedCookBonusAndBaseUntilBothFit(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            f.context().inventory().insertSupply(new ItemStack(Items.POTATO));
            var order=continuous(h,"baked_potato");HiredProductionService.appendPersonal(f.owner(),f.villager(),0L,order);tick(h,f);cook(h,f,200);fill(f);oneFoodSpace(f,Items.BAKED_POTATO);
            h.assertTrue(f.context().canStoreOutputs(java.util.List.of(new ItemStack(Items.BAKED_POTATO))),"base output alone fits");
            h.assertFalse(f.context().canStoreOutputs(java.util.List.of(new ItemStack(Items.BAKED_POTATO,2))),"earned bonus needs more room");
            tick(h,f);reload(h,f);tick(h,f);
            h.assertValueEqual(furnace(h,f).getItem(2).getCount(),1,"full inventory leaves base output at station");
            h.assertValueEqual(CookBonusService.bonus(h.getLevel(),f.villager(),furnace(h,f),1),1,"earned bonus persists without reroll");
            clearFill(f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BAKED_POTATO),65,"base and saved bonus enter inventory together");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledLoadedCookOrderStillAwardsOnlyItsActualProduction(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            f.context().inventory().insertSupply(new ItemStack(Items.POTATO));
            var order=continuous(h,"baked_potato");HiredProductionService.appendPersonal(f.owner(),f.villager(),0L,order);tick(h,f);queue(h,f).cancel(order.id());
            cook(h,f,200);tick(h,f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BAKED_POTATO),2,"cancelled loaded batch completes once");
            h.assertValueEqual(queue(h,f).order(order.id()).produced(),1L,"cancelled recipe counts one real event");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void jobSwitchDisablesFutureBonusAndOutputTamperingRevokesCredits(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            f.context().inventory().insertSupply(new ItemStack(Items.POTATO));
            var order=continuous(h,"baked_potato");HiredProductionService.appendPersonal(f.owner(),f.villager(),0L,order);tick(h,f);
            HireContractStore.save(f.villager(),HireContract.regular(f.owner().getUUID(),h.getLevel().getGameTime(),1,1,1,HiredVillagerRole.SMELTER));
            cook(h,f,200);
            h.assertValueEqual(CookBonusService.bonus(h.getLevel(),f.villager(),furnace(h,f),1),0,"changed job earns no further bonus");
            furnace(h,f).setItem(2,new ItemStack(Items.BAKED_POTATO,2));
            h.assertValueEqual(CookBonusService.bonus(h.getLevel(),f.villager(),furnace(h,f),2),0,"external station output is not attributed");
            h.assertTrue(furnace(h,f).getPersistentData().getCompound(BONUS_TAG).isEmpty(),"changed native batch revokes credit");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void finiteCookFurnaceQuotaExcludesBonus(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            f.context().inventory().insertSupply(new ItemStack(Items.POTATO,3));
            var order=order(h,"baked_potato",1);HiredProductionService.appendPersonal(f.owner(),f.villager(),0L,order);tick(h,f);cook(h,f,200);tick(h,f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BAKED_POTATO),1,"finite order remains exact even at guaranteed chance");
            h.assertValueEqual(f.context().inventory().countItem(Items.POTATO),2,"remaining ingredients preserved");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void genericCraftsmanDoesNotReceiveChefBreadBonus(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.CRAFTSMAN);try {
            h.getLevel().setBlock(h.absolutePos(new BlockPos(3,2,1)),Blocks.CRAFTING_TABLE.defaultBlockState(),3);
            filter(h,f,"bread");f.context().inventory().insertSupply(new ItemStack(Items.WHEAT,3));
            for(int i=0;i<20;i++)new CraftsmanWorker().tick(h.getLevel(),f.villager(),f.owner(),f.context());
            h.assertValueEqual(f.context().inventory().countItem(Items.BREAD),1,"generic Craftsman keeps ordinary yield");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void cookCakeKeepsMilkBucketsAndCannotReceiveBonus(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            filter(h,f,"cake");f.context().inventory().insertSupply(new ItemStack(Items.WHEAT,3));
            f.context().inventory().insertSupply(new ItemStack(Items.SUGAR,2));f.context().inventory().insertSupply(new ItemStack(Items.EGG));
            for(int i=0;i<3;i++)f.context().inventory().insertSupply(new ItemStack(Items.MILK_BUCKET));
            tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.CAKE),1,"container-bearing food keeps normal yield");
            h.assertValueEqual(f.context().inventory().countItem(Items.BUCKET),3,"all three milk containers survive");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void tutorialContractsNeverRollOrBindCookBonus(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            HireContractStore.save(f.villager(),HireContract.tutorial(f.owner().getUUID(),h.getLevel().getGameTime(),HiredVillagerRole.COOK,UUID.randomUUID()));
            h.assertFalse(CookBonusService.roll(f.villager()),"even guaranteed chance cannot expand a free trial");
            furnace(h,f).setItem(0,new ItemStack(Items.POTATO,2));CookBonusService.loaded(h.getLevel(),f.villager(),furnace(h,f));
            h.assertTrue(furnace(h,f).getPersistentData().getCompound(BONUS_TAG).isEmpty(),"trial loading never grants bonus attribution");
            filter(h,f,"bread");f.context().inventory().insertSupply(new ItemStack(Items.WHEAT,3));tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BREAD),0,"unbound trial cannot fall through to general crafting");
            h.assertValueEqual(f.context().inventory().countItem(Items.WHEAT),3,"trial budget bypass consumes nothing");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void dataDrivenNonMetalRecipeRetainsResultComponentsAndBatchCounts(GameTestHelper h) {
        var f=fixture(h);var manager=h.getLevel().getRecipeManager();var original=java.util.List.copyOf(manager.getRecipes());
        try {
            var output=new ItemStack(Items.GLASS,2);output.set(DataComponents.CUSTOM_NAME,Component.literal("Kiln glass"));
            var recipe=new RecipeHolder<>(ResourceLocation.parse("villagerretaliation:test_kiln_glass"),
                    new SmeltingRecipe("",CookingBookCategory.BLOCKS,Ingredient.of(Items.DIRT),output,0,5));
            var recipes=new java.util.ArrayList<RecipeHolder<?>>(original);recipes.add(recipe);manager.replaceRecipes(recipes);
            f.context().inventory().insertSupply(new ItemStack(Items.DIRT,2));
            var order=HiredProductionOrder.create(h.getLevel(),HiredProductionRecipe.exact(recipe.id(),Map.of()),HiredProductionOrder.Mode.BATCH,4,null);
            h.assertValueEqual(HiredProductionService.appendPersonal(f.owner(),f.villager(),0L,order),"","custom non-food recipe accepted");
            tick(h,f);cook(h,f,10);tick(h,f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.GLASS),4,"actual recipe count retained");
            h.assertValueEqual(f.context().inventory().countItem(Items.DIRT),0,"two real inputs paid for two operations");
            h.assertTrue(f.context().inventory().outputSlots().stream().map(f.context().inventory()::getItem)
                    .filter(st->st.is(Items.GLASS)).allMatch(st->HiredProductionStock.matches(output,st)),"output components survive collection");
            h.assertValueEqual(queue(h,f).order(order.id()).produced(),4L,"quota accounts whole component-bearing recipe outputs");
        }finally {manager.replaceRecipes(original);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacySmelterSelectsUsableFurnaceForSandAndKeepsFullOutput(GameTestHelper h) {
        var f=fixture(h);try {
            var nearer=h.absolutePos(new BlockPos(1,2,2));h.getLevel().setBlock(nearer,Blocks.BLAST_FURNACE.defaultBlockState(),3);
            f.context().inventory().insertSupply(new ItemStack(Items.SAND));
            tick(h,f);tick(h,f);cook(h,f,200);fill(f);tick(h,f);
            h.assertValueEqual(furnace(h,f).getItem(2).getCount(),1,"non-metal output remains when inventory full");
            h.assertTrue(((AbstractFurnaceBlockEntity)h.getLevel().getBlockEntity(nearer)).getItem(0).isEmpty(),"nearer blast furnace does not steal incompatible work");
            clearFill(f);tick(h,f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.GLASS),1,"legacy worker collects real glass once");
            h.assertValueEqual(f.context().inventory().countItem(Items.SAND),0,"legacy worker consumed real sand");
        }finally {cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void preexistingPlayerInputCannotEarnChefBonusFromVanillaTicks(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);try {
            furnace(h,f).setItem(0,new ItemStack(Items.POTATO));furnace(h,f).setItem(1,new ItemStack(Items.COAL));
            cook(h,f,200);tick(h,f);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BAKED_POTATO),1,"player-loaded ingredients do not establish worker attribution");
        }finally {chance(old);cleanup(h,f);}h.succeed();
    }

    @GameTest(template = "empty")
    public static void dataPackReverseConversionsSuppressChefBonuses(GameTestHelper h) {
        int old=chance(100);var f=fixture(h,HiredVillagerRole.COOK);
        var manager=h.getLevel().getRecipeManager();var original=java.util.List.copyOf(manager.getRecipes());
        try {
            var breadReverse=new RecipeHolder<>(ResourceLocation.parse("villagerretaliation:test_reverse_bread"),
                    new ShapelessRecipe("",CraftingBookCategory.MISC,new ItemStack(Items.WHEAT,3),
                        net.minecraft.core.NonNullList.of(Ingredient.EMPTY,Ingredient.of(Items.BREAD))));
            var potatoReverse=new RecipeHolder<>(ResourceLocation.parse("villagerretaliation:test_reverse_potato"),
                    new ShapelessRecipe("",CraftingBookCategory.MISC,new ItemStack(Items.POTATO),
                        net.minecraft.core.NonNullList.of(Ingredient.EMPTY,Ingredient.of(Items.BAKED_POTATO))));
            var recipes=new java.util.ArrayList<RecipeHolder<?>>(original);recipes.add(breadReverse);recipes.add(potatoReverse);manager.replaceRecipes(recipes);
            filter(h,f,"bread");f.context().inventory().insertSupply(new ItemStack(Items.WHEAT,3));tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BREAD),1,"registered bread reversal removes bonus eligibility");
            filter(h,f,"baked_potato");f.context().inventory().insertSupply(new ItemStack(Items.POTATO));
            // Manual worker ticks do not advance the world's facility scan cooldown after changing recipe type.
            f.context().state().remove("NextCookingStationScanGameTime");
            tick(h,f);tick(h,f);cook(h,f,200);tick(h,f);
            h.assertValueEqual(f.context().inventory().countItem(Items.BAKED_POTATO),1,"registered cooked-food reversal removes bonus eligibility");
        }finally {manager.replaceRecipes(original);chance(old);cleanup(h,f);}h.succeed();
    }

    private static int chance(int value) {int old=VillagerRetaliationConfig.HIRED_COOK_BONUS_CHANCE_PERCENT.get();VillagerRetaliationConfig.HIRED_COOK_BONUS_CHANCE_PERCENT.set(value);return old;}
    private static void filter(GameTestHelper h,Fixture f,String recipe) {
        var filter=new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());VillagerRecipeFilterData.setRecipe(filter,h.getLevel(),ResourceLocation.withDefaultNamespace(recipe));f.context().inventory().setItem(HiredJobInventory.FILTER_SLOT,filter);
    }
    private static HiredProductionOrder continuous(GameTestHelper h,String recipe) {
        return HiredProductionOrder.create(h.getLevel(),HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace(recipe),Map.of()),HiredProductionOrder.Mode.CONTINUOUS,1,null);
    }
    private static void reload(GameTestHelper h,Fixture f) {
        var saved=furnace(h,f).saveWithFullMetadata(h.getLevel().registryAccess());
        furnace(h,f).loadWithComponents(saved,h.getLevel().registryAccess());
    }
    private static void fill(Fixture f) {
        for(int slot:f.context().inventory().supplySlots())if(f.context().inventory().getItem(slot).isEmpty())f.context().inventory().setItem(slot,new ItemStack(Items.DIRT,64));
        for(int slot:f.context().inventory().outputSlots())if(f.context().inventory().getItem(slot).isEmpty())f.context().inventory().setItem(slot,new ItemStack(Items.DIRT,64));
    }
    private static void oneFoodSpace(Fixture f,net.minecraft.world.item.Item item) {
        int slot=f.context().inventory().supplySlots().stream().filter(i->f.context().inventory().getItem(i).is(Items.DIRT)).findFirst().orElseThrow();
        f.context().inventory().setItem(slot,ItemStack.EMPTY);
        if(!f.context().inventory().insertOutput(new ItemStack(item,63)).isEmpty())throw new IllegalStateException("food capacity fixture failed");
    }
    private static void clearFill(Fixture f) {
        for(int slot=0;slot<HiredJobInventory.SLOT_COUNT;slot++)if(f.context().inventory().getItem(slot).is(Items.DIRT))f.context().inventory().setItem(slot,ItemStack.EMPTY);
    }
    private static HiredProductionOrder order(GameTestHelper helper, String recipe, int amount) {
        return HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace(recipe), Map.of()),
                HiredProductionOrder.Mode.BATCH, amount, null);
    }

    private static void tick(GameTestHelper helper, Fixture fixture) {
        if (fixture.role() == HiredVillagerRole.COOK) new com.jvn.villagerretaliation.interaction.work.CookingWorker().tick(helper.getLevel(), fixture.villager(), fixture.owner(), fixture.context());
        else new com.jvn.villagerretaliation.interaction.work.SmeltingWorker().tick(helper.getLevel(), fixture.villager(), fixture.owner(), fixture.context());
    }

    private static void cook(GameTestHelper helper, Fixture fixture, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), fixture.station(), helper.getLevel().getBlockState(fixture.station()), furnace(helper, fixture));
        }
    }

    private static AbstractFurnaceBlockEntity furnace(GameTestHelper helper, Fixture fixture) {
        return (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(fixture.station());
    }

    private static HiredProductionQueue queue(GameTestHelper helper, Fixture fixture) {
        return HiredProductionService.access(helper.getLevel(), fixture.villager(), fixture.role()).queue();
    }

    private static void cleanup(GameTestHelper helper, Fixture fixture) {
        HiredProductionStations.get(helper.getLevel()).workerRemoved(fixture.villager().getUUID());
        fixture.villager().discard();
    }

    private static Fixture fixture(GameTestHelper helper) {
        return fixture(helper, HiredVillagerRole.SMELTER);
    }

    private static Fixture fixture(GameTestHelper helper, HiredVillagerRole role) {
        var level = helper.getLevel();
        for (int x = 0; x <= 6; x++) for (int z = 0; z <= 6; z++) {
            level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(helper.absolutePos(new BlockPos(x, 2, z)), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(helper.absolutePos(new BlockPos(x, 3, z)), Blocks.AIR.defaultBlockState(), 3);
        }
        var station = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(station, Blocks.FURNACE.defaultBlockState(), 3);
        if (role == HiredVillagerRole.COOK) level.setBlock(helper.absolutePos(new BlockPos(3, 2, 1)), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        villager.setNoAi(true);
        villager.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(villager), "the producer is loaded");
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FurnaceOwner"));
        HireContractStore.save(villager, HireContract.regular(owner.getUUID(), level.getGameTime(), 1, 1, 1, role));
        var state = HiredWorkStateStore.state(villager);
        HiredWorkerBrain.initialize(state);
        var area = HiredWorkArea.fromBounds(helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(5, 4, 5)), true);
        var inventory = HiredJobInventory.getJobInventory(villager);


        inventory.insertSupply(new ItemStack(Items.COAL));
        var context = new HiredWorkContext(inventory, state, area.center(), area.min(), area.max(), area.horizontalRadius(), area.verticalRadius(),
                true, 100, false, false);
        return new Fixture(villager, owner, station, context, role);
    }

    private record Fixture(Villager villager, ServerPlayer owner, BlockPos station, HiredWorkContext context, HiredVillagerRole role) {
    }
}
