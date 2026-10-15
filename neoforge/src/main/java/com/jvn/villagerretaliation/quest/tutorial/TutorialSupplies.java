package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;

/** Modest permanent working inputs, issued once per job. No matching player property is reclaimed. */
public final class TutorialSupplies{
    private TutorialSupplies(){}
    public static String provide(ServerPlayer player,Villager worker,TutorialRun run){
        var role=HiredVillagerRole.bySerializedName(run.binding().role());var bill=new LinkedHashMap<String,ItemStack>();
        switch(role){
            case FARMING->{bill.put("hoe",new ItemStack(Items.WOODEN_HOE));bill.put("seeds",new ItemStack(Items.WHEAT_SEEDS,2));}
            case FISHING->bill.put("rod",new ItemStack(Items.FISHING_ROD));
            case MINING->bill.put("pickaxe",new ItemStack(Items.WOODEN_PICKAXE));
            case LOGGING->{bill.put("axe",new ItemStack(Items.WOODEN_AXE));
                var session=com.jvn.villagerretaliation.interaction.HiredWorkSession.create(player.serverLevel(),worker,role);
                for(var pos:session.context().workAreaPositions()){
                    var logs=com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.selectedTreeLogs(player.serverLevel(),pos,Set.of());
                    if(!logs.isEmpty()&&logs.size()<=8){var sapling=com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.saplingForTree(player.serverLevel(),player.serverLevel()::hasChunkAt,logs);if(!sapling.isEmpty())bill.put("sapling",sapling.copyWithCount(1));break;}
                }}
            case HUNTING,COMBAT->bill.put("sword",new ItemStack(Items.WOODEN_SWORD));
            case ANIMAL_HANDLING->{bill.put("shears",new ItemStack(Items.SHEARS));bill.put("wheat",new ItemStack(Items.WHEAT,2));}
            case CRAFTSMAN->{if(TutorialCraftingPlan.recipe(player.serverLevel())==null)return "The runtime one-log recipe is unavailable; no inputs were issued.";bill.put("log",new ItemStack(Items.OAK_LOG));}
            case COOK->{bill.put("raw_cod",new ItemStack(Items.COD,2));bill.put("coal",new ItemStack(Items.COAL));}
            case SMELTER->{bill.put("raw_iron",new ItemStack(Items.RAW_IRON,2));bill.put("coal",new ItemStack(Items.COAL));}
            case COURIER->bill.put("parcel",new ItemStack(Items.OAK_LOG,4));
            case BREWING->{
                var state=com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(worker);
                var route=TutorialBrewingPlan.chosen(player.serverLevel(),state);
                if(route==null||!route.output().is(Items.POTION))return "Choose the regular potion in Configure Brewing first, then request its actual material bill.";
                if(run.snapshot().learned().stream().anyMatch(s->s.startsWith("supply_route:")&&!s.equals("supply_route:"+route.itemId()+"|"+route.potionId())))return "This lesson's supplied route is already fixed; restore that order instead of requesting a second batch.";
                run.learn("supply_route:"+route.itemId()+"|"+route.potionId());
                for(int i=0;i<3;i++)bill.put("water_"+i,PotionContents.createItemStack(Items.POTION,Potions.WATER));
                for(int i=0;i<route.ingredients().size();i++)bill.put("ingredient_"+i,new ItemStack(route.ingredients().get(i)));
                bill.put("fuel",new ItemStack(Items.BLAZE_POWDER,(route.ingredients().size()+19)/20));
            }
            case BUILDER->{var plan=TutorialBuilderPlan.plan(player.serverLevel(),net.minecraft.world.level.block.Rotation.NONE).orElse(null);if(plan==null)return "The runtime Plains Accessory 1 template is unavailable.";
                int index=0;for(var material:plan.materials()){int left=material.count();while(left>0){int n=Math.min(left,material.item().getMaxStackSize());bill.put("material_"+index++,material.item().copyWithCount(n));left-=n;}}
                var actions=new HashSet<com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.BuilderToolAction>();plan.blocks().forEach(b->actions.add(b.toolAction()));
                for(var action:actions) switch(action){case AXE_STRIP->bill.put("axe",new ItemStack(Items.WOODEN_AXE));case SHOVEL_FLATTEN->bill.put("shovel",new ItemStack(Items.WOODEN_SHOVEL));case HOE_TILL->bill.put("hoe",new ItemStack(Items.WOODEN_HOE));default->{}}
            }
            default->{}
        }
        if(java.util.Set.of(HiredVillagerRole.CRAFTSMAN,HiredVillagerRole.COOK,HiredVillagerRole.SMELTER).contains(role)){
            net.minecraft.resources.ResourceLocation recipeId=null;
            if(role==HiredVillagerRole.CRAFTSMAN){var recipe=TutorialCraftingPlan.recipe(player.serverLevel());if(recipe!=null)recipeId=recipe.id();}
            else{
                @SuppressWarnings("unchecked") var type=(net.minecraft.world.item.crafting.RecipeType<net.minecraft.world.item.crafting.AbstractCookingRecipe>)(net.minecraft.world.item.crafting.RecipeType<?>)(role==HiredVillagerRole.COOK?net.minecraft.world.item.crafting.RecipeType.SMOKING:net.minecraft.world.item.crafting.RecipeType.SMELTING);
                var recipe=player.serverLevel().getRecipeManager().getRecipeFor(type,new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(TutorialFurnacePlan.input(role))),player.serverLevel()).orElse(null);
                if(recipe!=null)recipeId=recipe.id();
            }
            if(recipeId==null)return "The selected runtime recipe is unavailable; no substitute filter was issued.";
            var filter=new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.RECIPE_FILTER.get());
            if(!com.jvn.villagerretaliation.item.VillagerRecipeFilterData.setRecipe(filter,player.serverLevel(),recipeId))return "The recipe filter cannot resolve this running pack's exact recipe.";
            bill.put("recipe_filter",filter);
        }
        if(role==HiredVillagerRole.BUILDER||role==HiredVillagerRole.BREWING){
            String content=bill.entrySet().stream().map(entry->entry.getKey()+"="+entry.getValue().save(player.registryAccess())).collect(java.util.stream.Collectors.joining("|"));
            String fingerprint;
            try{fingerprint=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
            catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
            String key="provision_bill:"+fingerprint;
            if(run.snapshot().learned().stream().anyMatch(previous->previous.startsWith("provision_bill:")&&!previous.equals(key)))return "The runtime material bill changed. Your original once-only allowance is retained; no replacement batch was issued.";
            run.learn(key);TutorialSavedData.get(player.serverLevel()).setDirty();
        }
        for(var entry:bill.entrySet()){
            var result=TutorialProvisionService.deliver(player,run,entry.getKey(),entry.getValue());
            if(result==TutorialProvisionService.Delivery.WAITING_FOR_SPACE)return "Make inventory room and request the remaining supplies again. Delivered portions are recorded and will not repeat.";
            if(result==TutorialProvisionService.Delivery.CONFLICT)return "The saved provision record requires recovery. No replacement allowance was issued.";
        }
        return bill.isEmpty()?"This job needs no free item reward.":"Once-only working inputs are recorded. Keep them permanently; put the inputs in accessible Supplies. Nothing matching your own items will be reclaimed.";
    }
}
