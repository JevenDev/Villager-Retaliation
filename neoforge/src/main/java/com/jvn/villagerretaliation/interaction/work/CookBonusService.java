package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.interaction.work.production.HiredQueuedFurnaceWorker;
import com.jvn.villagerretaliation.quest.tutorial.TutorialWorkService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** A bounded food perk, separate from work receipts, practice, and production order quantities.
 * Only native transitions of worker-loaded inputs earn furnace credits. Credits and rolls are
 * persisted on the station; collection moves bonus and base together or leaves both untouched.
 */
public final class CookBonusService {
    private static final String TAG = "VillagerRetaliationCookBatch";
    private static final Map<Item, Item> COOKED = Map.of(
            Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP,
            Items.CHICKEN, Items.COOKED_CHICKEN, Items.MUTTON, Items.COOKED_MUTTON,
            Items.RABBIT, Items.COOKED_RABBIT, Items.COD, Items.COOKED_COD,
            Items.SALMON, Items.COOKED_SALMON, Items.POTATO, Items.BAKED_POTATO);
    private record Tick(int input, int output, ItemStack expected, boolean eligible) { }
    private static final Map<AbstractFurnaceBlockEntity, Tick> TICKS = new IdentityHashMap<>();
    private CookBonusService() { }

    public static boolean wins(int percent, int roll) {
        return roll >= 0 && roll < 100 && roll < Math.clamp(percent, 0, 100);
    }

    public static boolean plain(ItemStack stack) {
        return !stack.isEmpty() && HiredJobInventory.withoutJobItemMarker(stack).getComponentsPatch().isEmpty();
    }

    public static boolean eligibleCooking(ItemStack input, ItemStack result) {
        return plain(input) && plain(result) && result.getCount() == 1
                && COOKED.get(input.getItem()) == result.getItem();
    }

    public static boolean eligibleBread(ServerLevel level, CraftingRecipe recipe, WorkCraftingPlan.Result plan) {
        return !recipe.isSpecial() && plan.crafts() == 1 && plan.products().size() == 1
                && plan.surplus().isEmpty() && plan.result().is(Items.BREAD)
                && plan.result().getCount() == 1 && plain(plan.result())
                && plan.consumedStacks().stream().allMatch(stack -> stack.is(Items.WHEAT) && plain(stack))
                && plan.consumedStacks().stream().mapToInt(ItemStack::getCount).sum() == 3
                && !hasReverseRecipe(level, Items.BREAD, Items.WHEAT);
    }

    /** Conservative direct reverse-route check, including data-pack additions. */
    private static boolean hasReverseRecipe(ServerLevel level, Item food, Item input) {
        var sample = new ItemStack(food);
        return level.getRecipeManager().getRecipes().stream().anyMatch(holder ->
                holder.value().getResultItem(level.registryAccess()).is(input)
                        && holder.value().getIngredients().stream().anyMatch(ingredient -> ingredient.test(sample)));
    }

    public static boolean roll(Villager worker) {
        return !TutorialWorkService.isTrial(worker)
                && wins(VillagerRetaliationConfig.HIRED_COOK_BONUS_CHANCE_PERCENT.get(), worker.getRandom().nextInt(100));
    }

    /** Called only immediately after a Cook actually removes supplies and loads an empty station. */
    public static void loaded(ServerLevel level, Villager worker, AbstractFurnaceBlockEntity furnace) {
        furnace.getPersistentData().remove(TAG);
        if (TutorialWorkService.isTrial(worker) || !furnace.getItem(2).isEmpty()) return;
        var input = furnace.getItem(0);
        var recipe = level.getRecipeManager().getRecipeFor(HiredQueuedFurnaceWorker.recipeType(furnace), new SingleRecipeInput(input), level).orElse(null);
        if (recipe == null) return;
        var result = recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess());
        if (!eligibleCooking(input, result) || input.getCount() > 64
                || hasReverseRecipe(level, result.getItem(), input.getItem())) return;
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Worker", worker.getUUID());
        tag.putString("Recipe", recipe.id().toString());
        tag.put("Input", input.copyWithCount(1).save(level.registryAccess()));
        tag.put("Result", result.save(level.registryAccess()));
        tag.putInt("Remaining", input.getCount());
        tag.putInt("Output", 0);
        tag.putInt("Credits", 0);
        tag.putInt("Bonus", 0);
        furnace.getPersistentData().put(TAG, tag);
        furnace.setChanged();
    }

    public static void beforeTick(Level world, AbstractFurnaceBlockEntity furnace) {
        TICKS.remove(furnace);
        var tag = furnace.getPersistentData().getCompound(TAG);
        if (!(world instanceof ServerLevel level) || tag.isEmpty()) return;
        if (!valid(level, furnace, tag)) { clear(furnace); return; }
        var input = furnace.getItem(0);
        if (input.isEmpty()) return;
        var recipe = level.getRecipeManager().getRecipeFor(HiredQueuedFurnaceWorker.recipeType(furnace), new SingleRecipeInput(input), level).orElse(null);
        var expected = ItemStack.parseOptional(level.registryAccess(), tag.getCompound("Result"));
        if (recipe == null || !recipe.id().toString().equals(tag.getString("Recipe"))
                || !ItemStack.matches(recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess()), expected)) {
            clear(furnace); return;
        }
        var entity = level.getEntity(tag.getUUID("Worker"));
        boolean eligible = entity instanceof Villager worker && !TutorialWorkService.isTrial(worker)
                && VillagerWorkAuthority.assignedRole(level, worker) == HiredVillagerRole.COOK;
        TICKS.put(furnace, new Tick(input.getCount(), furnace.getItem(2).getCount(), expected, eligible));
    }

    public static void afterTick(Level world, AbstractFurnaceBlockEntity furnace) {
        var tick = TICKS.remove(furnace);
        if (tick == null || !(world instanceof ServerLevel level)) return;
        var tag = furnace.getPersistentData().getCompound(TAG);
        var output = furnace.getItem(2);
        if (furnace.getItem(0).getCount() == tick.input() - 1 && output.getCount() == tick.output() + 1
                && ItemStack.isSameItemSameComponents(output, tick.expected())) {
            tag.putInt("Remaining", tick.input() - 1);
            tag.putInt("Output", output.getCount());
            tag.putInt("Credits", tag.getInt("Credits") + 1);
            if (tick.eligible() && level.getEntity(tag.getUUID("Worker")) instanceof Villager worker
                    && !hasReverseRecipe(level, tick.expected().getItem(),
                        ItemStack.parseOptional(level.registryAccess(), tag.getCompound("Input")).getItem())
                    && roll(worker))
                tag.putInt("Bonus", tag.getInt("Bonus") + 1);
            furnace.setChanged();
        } else if (furnace.getItem(0).getCount() != tick.input() || output.getCount() != tick.output()) clear(furnace);
    }

    private static boolean valid(ServerLevel level, AbstractFurnaceBlockEntity furnace, CompoundTag tag) {
        int remaining = tag.getInt("Remaining"), credits = tag.getInt("Credits"), bonus = tag.getInt("Bonus");
        var input = furnace.getItem(0);
        var output = furnace.getItem(2);
        return tag.getInt("Version") == 1 && tag.hasUUID("Worker")
                && remaining >= 0 && remaining <= 64 && credits >= 0 && credits <= 64 && bonus >= 0 && bonus <= credits
                && input.getCount() == remaining && output.getCount() == tag.getInt("Output") && credits <= output.getCount()
                && (input.isEmpty() || ItemStack.isSameItemSameComponents(input, ItemStack.parseOptional(level.registryAccess(), tag.getCompound("Input"))))
                && (output.isEmpty() || ItemStack.isSameItemSameComponents(output, ItemStack.parseOptional(level.registryAccess(), tag.getCompound("Result"))));
    }

    public static int bonus(ServerLevel level, Villager worker, AbstractFurnaceBlockEntity furnace, int baseCount) {
        var tag = furnace.getPersistentData().getCompound(TAG);
        if (tag.isEmpty()) return 0;
        if (!valid(level, furnace, tag)) { clear(furnace); return 0; }
        return !TutorialWorkService.isTrial(worker) && tag.getUUID("Worker").equals(worker.getUUID())
                ? Math.min(Math.max(0, baseCount), tag.getInt("Bonus")) : 0;
    }

    /** Called after the base and bonus have both entered inventory. Never credits another work event. */
    public static void collected(AbstractFurnaceBlockEntity furnace, int baseCount, int bonus) {
        var tag = furnace.getPersistentData().getCompound(TAG);
        if (tag.isEmpty()) return;
        tag.putInt("Output", furnace.getItem(2).getCount());
        tag.putInt("Credits", Math.max(0, tag.getInt("Credits") - baseCount));
        tag.putInt("Bonus", Math.min(tag.getInt("Credits"), Math.max(0, tag.getInt("Bonus") - bonus)));
        if (tag.getInt("Remaining") == 0 && tag.getInt("Credits") == 0) clear(furnace);
        furnace.setChanged();
    }

    private static void clear(AbstractFurnaceBlockEntity furnace) {
        furnace.getPersistentData().remove(TAG);
        furnace.setChanged();
    }
}
