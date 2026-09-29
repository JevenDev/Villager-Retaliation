package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.CookingWorker;
import com.jvn.villagerretaliation.interaction.work.SmeltingWorker;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record HiredProductionRecipe(ResourceLocation recipe, Map<Integer, ResourceLocation> alternatives,
                                    ResourceLocation brewingItem, ResourceLocation potion) {
    public HiredProductionRecipe {
        alternatives = Map.copyOf(Objects.requireNonNull(alternatives));
        if ((recipe == null) == (brewingItem == null) || (brewingItem == null) != (potion == null)
                || recipe == null && !alternatives.isEmpty() || alternatives.size() > VillagerRecipeFilterData.MAX_INGREDIENTS
                || alternatives.keySet().stream().anyMatch(slot -> slot < 0 || slot >= VillagerRecipeFilterData.MAX_INGREDIENTS)) {
            throw new IllegalArgumentException("Invalid production recipe");
        }
    }

    public static HiredProductionRecipe exact(ResourceLocation recipe, Map<Integer, ResourceLocation> alternatives) {
        return new HiredProductionRecipe(Objects.requireNonNull(recipe), alternatives, null, null);
    }

    public static HiredProductionRecipe brewing(ResourceLocation item, ResourceLocation potion) {
        return new HiredProductionRecipe(null, Map.of(), Objects.requireNonNull(item), Objects.requireNonNull(potion));
    }

    public ItemStack filter(Level level) {
        if (recipe == null) return ItemStack.EMPTY;
        var filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        if (!VillagerRecipeFilterData.setRecipe(filter, level, recipe)) return ItemStack.EMPTY;
        for (var entry : alternatives.entrySet()) {
            if (!VillagerRecipeFilterData.setIngredient(filter, level, entry.getKey(), entry.getValue())) return ItemStack.EMPTY;
        }
        return filter;
    }

    public ItemStack output(Level level) {
        if (recipe == null) return HiredBrewingRecipeCatalog.find(level, brewingItem, potion)
                .map(route -> route.output().copy()).orElse(ItemStack.EMPTY);
        var resolution = VillagerRecipeFilterData.resolve(level, filter(level));
        return resolution.valid() ? resolution.recipe().value().getResultItem(level.registryAccess()).copy() : ItemStack.EMPTY;
    }

    public boolean supports(Level level, HiredVillagerRole role) {
        if (recipe == null) return role == HiredVillagerRole.BREWING && !output(level).isEmpty();
        var resolution = VillagerRecipeFilterData.resolve(level, filter(level));
        if (!resolution.valid()) return false;
        var type = resolution.recipe().value().getType();
        var result = resolution.recipe().value().getResultItem(level.registryAccess());
        return switch (role) {
            case CRAFTSMAN -> type == RecipeType.CRAFTING;
            case COOK -> CookingWorker.isFood(result) && (type == RecipeType.CRAFTING
                    || type == RecipeType.SMELTING || type == RecipeType.SMOKING);
            case SMELTER -> (type == RecipeType.SMELTING || type == RecipeType.BLASTING) && SmeltingWorker.isSmeltedOre(result)
                    && resolution.recipe().value().getIngredients().size() == 1
                    && java.util.Arrays.stream(resolution.recipe().value().getIngredients().getFirst().getItems())
                        .anyMatch(stack -> SmeltingWorker.isRawOre(stack) && (alternatives.get(0) == null
                            || alternatives.get(0).equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))));
            default -> false;
        };
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        if (recipe != null) tag.putString("Recipe", recipe.toString());
        else {
            tag.putString("BrewingItem", brewingItem.toString());
            tag.putString("Potion", potion.toString());
        }
        var choices = new ListTag();
        alternatives.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var choice = new CompoundTag();
            choice.putInt("Slot", entry.getKey());
            choice.putString("Item", entry.getValue().toString());
            choices.add(choice);
        });
        tag.put("Alternatives", choices);
        return tag;
    }

    public static HiredProductionRecipe load(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || tag.contains("Recipe") && !tag.contains("Recipe", Tag.TAG_STRING)
                || tag.contains("BrewingItem") && !tag.contains("BrewingItem", Tag.TAG_STRING)
                || tag.contains("Potion") && !tag.contains("Potion", Tag.TAG_STRING)
                || !(tag.get("Alternatives") instanceof ListTag choices)
                || !choices.isEmpty() && choices.getElementType() != Tag.TAG_COMPOUND
                || choices.size() > VillagerRecipeFilterData.MAX_INGREDIENTS) {
            throw new IllegalArgumentException("Invalid saved production recipe");
        }
        var alternatives = new LinkedHashMap<Integer, ResourceLocation>();
        for (Tag raw : choices) {
            var choice = (CompoundTag) raw;
            if (!choice.contains("Slot", Tag.TAG_INT) || !choice.contains("Item", Tag.TAG_STRING)
                    || alternatives.putIfAbsent(choice.getInt("Slot"), ResourceLocation.parse(choice.getString("Item"))) != null) {
                throw new IllegalArgumentException("Invalid saved ingredient alternative");
            }
        }
        return new HiredProductionRecipe(tag.contains("Recipe") ? ResourceLocation.parse(tag.getString("Recipe")) : null, alternatives,
                tag.contains("BrewingItem") ? ResourceLocation.parse(tag.getString("BrewingItem")) : null,
                tag.contains("Potion") ? ResourceLocation.parse(tag.getString("Potion")) : null);
    }
}
