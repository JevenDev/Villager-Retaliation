package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Versioned persistent model for one explicit worker recipe and optional exact ingredient
 * alternatives. Recipe identity is authoritative; result identity is only presentation.
 */
public final class VillagerRecipeFilterData {
    public static final int CURRENT_VERSION = 1;
    public static final int MAX_INGREDIENTS = 9;
    public static final int MAX_ALTERNATIVES_PER_INGREDIENT = 64;
    public static final int MAX_CATALOG_RECIPES = 4096;

    private static final String ROOT_TAG = VillagerRetaliation.MOD_ID + ":recipe_filter";
    private static final String VERSION_TAG = "Version";
    private static final String RECIPE_TAG = "Recipe";
    private static final String INGREDIENTS_TAG = "Ingredients";
    private static final String SLOT_TAG = "Slot";
    private static final String ITEM_TAG = "Item";

    private VillagerRecipeFilterData() {
    }

    public static Configuration read(ItemStack filter) {
        if (!VillagerRetaliationItems.isRecipeFilter(filter)) {
            return Configuration.empty();
        }
        CustomData data = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data.isEmpty()) {
            return Configuration.empty();
        }
        CompoundTag customData = data.copyTag();
        if (!customData.contains(ROOT_TAG)) {
            return Configuration.empty();
        }
        if (!customData.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return Configuration.malformed();
        }
        CompoundTag root = customData.getCompound(ROOT_TAG);
        if (!root.contains(VERSION_TAG, Tag.TAG_INT)
                || root.getInt(VERSION_TAG) != CURRENT_VERSION
                || !root.contains(RECIPE_TAG, Tag.TAG_STRING)) {
            return Configuration.malformed();
        }
        ResourceLocation recipeId = ResourceLocation.tryParse(root.getString(RECIPE_TAG));
        if (recipeId == null) {
            return Configuration.malformed();
        }

        Map<Integer, ResourceLocation> narrowed = new LinkedHashMap<>();
        ListTag entries = new ListTag();
        if (root.contains(INGREDIENTS_TAG)) {
            if (!root.contains(INGREDIENTS_TAG, Tag.TAG_LIST)) {
                return Configuration.malformed();
            }
            entries = (ListTag) root.get(INGREDIENTS_TAG);
            if (entries == null
                    || !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
                return Configuration.malformed();
            }
        }
        for (Tag raw : entries) {
            if (!(raw instanceof CompoundTag entry)
                    || !entry.contains(SLOT_TAG, Tag.TAG_INT)
                    || !entry.contains(ITEM_TAG, Tag.TAG_STRING)) {
                return Configuration.malformed();
            }
            int slot = entry.getInt(SLOT_TAG);
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getString(ITEM_TAG));
            if (slot < 0
                    || slot >= MAX_INGREDIENTS
                    || itemId == null
                    || !BuiltInRegistries.ITEM.containsKey(itemId)
                    || narrowed.putIfAbsent(slot, itemId) != null) {
                return Configuration.malformed();
            }
        }
        return Configuration.configured(recipeId, narrowed);
    }

    public static Resolution resolve(Level level, ItemStack filter) {
        Configuration configuration = read(filter);
        if (configuration.state() == StoredState.EMPTY) {
            return Resolution.empty(configuration);
        }
        if (configuration.state() == StoredState.MALFORMED || level == null) {
            return Resolution.invalid(ResolutionState.MALFORMED, configuration);
        }
        Optional<RecipeHolder<?>> holder = recipe(level, configuration.recipeId());
        if (holder.isEmpty()) {
            return Resolution.invalid(ResolutionState.MISSING_RECIPE, configuration);
        }
        if (!isSupported(level, holder.get())) {
            return Resolution.invalid(ResolutionState.UNSUPPORTED_RECIPE, configuration);
        }
        List<Ingredient> ingredients = holder.get().value().getIngredients();
        for (Map.Entry<Integer, ResourceLocation> entry : configuration.narrowedIngredients().entrySet()) {
            int slot = entry.getKey();
            if (slot >= ingredients.size()
                    || ingredients.get(slot).isEmpty()
                    || ingredientChoices(ingredients.get(slot)).stream()
                            .noneMatch(choice -> BuiltInRegistries.ITEM.getKey(choice.getItem()).equals(entry.getValue()))) {
                return Resolution.invalid(ResolutionState.INVALID_ALTERNATIVE, configuration);
            }
        }
        return Resolution.valid(configuration, holder.get());
    }

    public static List<RecipeHolder<?>> catalog(Level level) {
        if (level == null) {
            return List.of();
        }
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        recipes.addAll(level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING));
        recipes.addAll(level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING));
        recipes.addAll(level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING));
        recipes.addAll(level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING));
        return recipes.stream()
                .filter(holder -> isSupported(level, holder))
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .limit(MAX_CATALOG_RECIPES)
                .toList();
    }

    public static Optional<RecipeHolder<?>> recipe(Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return Optional.empty();
        }
        return level.getRecipeManager().byKey(recipeId);
    }

    public static boolean setRecipe(ItemStack filter, Level level, ResourceLocation recipeId) {
        if (!VillagerRetaliationItems.isRecipeFilter(filter)) {
            return false;
        }
        if (recipeId == null) {
            boolean changed = read(filter).state() != StoredState.EMPTY;
            clear(filter);
            return changed;
        }
        Optional<RecipeHolder<?>> holder = recipe(level, recipeId);
        if (holder.isEmpty() || !isSupported(level, holder.get())) {
            return false;
        }
        Configuration current = read(filter);
        if (current.state() == StoredState.CONFIGURED
                && recipeId.equals(current.recipeId())
                && current.narrowedIngredients().isEmpty()) {
            return false;
        }
        write(filter, Configuration.configured(recipeId, Map.of()));
        return true;
    }

    public static boolean setIngredient(
            ItemStack filter,
            Level level,
            int slot,
            ResourceLocation itemId) {
        Resolution resolution = resolve(level, filter);
        if (!VillagerRetaliationItems.isRecipeFilter(filter)
                || !resolution.valid()
                || slot < 0
                || slot >= resolution.recipe().value().getIngredients().size()) {
            return false;
        }
        Ingredient ingredient = resolution.recipe().value().getIngredients().get(slot);
        if (ingredient.isEmpty()) {
            return false;
        }
        if (itemId != null && ingredientChoices(ingredient).stream()
                .noneMatch(choice -> BuiltInRegistries.ITEM.getKey(choice.getItem()).equals(itemId))) {
            return false;
        }
        Map<Integer, ResourceLocation> narrowed =
                new LinkedHashMap<>(resolution.configuration().narrowedIngredients());
        ResourceLocation previous = itemId == null ? narrowed.remove(slot) : narrowed.put(slot, itemId);
        if ((itemId == null && previous == null) || itemId != null && itemId.equals(previous)) {
            return false;
        }
        write(filter, Configuration.configured(resolution.configuration().recipeId(), narrowed));
        return true;
    }

    public static List<ItemStack> ingredientChoices(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return List.of();
        }
        Map<ResourceLocation, ItemStack> unique = new LinkedHashMap<>();
        for (ItemStack choice : ingredient.getItems()) {
            if (choice.isEmpty()) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(choice.getItem());
            unique.putIfAbsent(itemId, choice.copyWithCount(1));
            if (unique.size() >= MAX_ALTERNATIVES_PER_INGREDIENT) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    public static boolean matchesIngredient(
            Level level,
            ItemStack filter,
            int slot,
            ItemStack candidate) {
        Resolution resolution = resolve(level, filter);
        if (!resolution.valid() || candidate == null || candidate.isEmpty()) {
            return false;
        }
        List<Ingredient> ingredients = resolution.recipe().value().getIngredients();
        if (slot < 0 || slot >= ingredients.size() || ingredients.get(slot).isEmpty()) {
            return false;
        }
        ResourceLocation narrowed = resolution.configuration().narrowedIngredients().get(slot);
        return narrowed == null
                ? ingredients.get(slot).test(candidate)
                : candidate.is(BuiltInRegistries.ITEM.get(narrowed));
    }

    public static boolean matchesResult(Level level, ItemStack filter, ItemStack candidate) {
        Resolution resolution = resolve(level, filter);
        if (!resolution.valid() || candidate == null || candidate.isEmpty()) {
            return false;
        }
        ItemStack result = resolution.recipe().value().getResultItem(level.registryAccess());
        return !result.isEmpty() && ItemStack.isSameItemSameComponents(
                result.copyWithCount(1), candidate.copyWithCount(1));
    }

    public static ItemStack result(Level level, ItemStack filter) {
        Resolution resolution = resolve(level, filter);
        return resolution.valid()
                ? resolution.recipe().value().getResultItem(level.registryAccess()).copy()
                : ItemStack.EMPTY;
    }

    public static void clear(ItemStack filter) {
        if (!VillagerRetaliationItems.isRecipeFilter(filter)) {
            return;
        }
        CustomData existing = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (existing.isEmpty()) {
            return;
        }
        CompoundTag customData = existing.copyTag();
        customData.remove(ROOT_TAG);
        store(filter, customData);
    }

    public static boolean isDefault(ItemStack filter) {
        return VillagerRetaliationItems.isRecipeFilter(filter)
                && read(filter).state() == StoredState.EMPTY
                && !VillagerFilterPolicy.hasStoredPolicy(filter);
    }

    public static void copyConfiguration(ItemStack source, ItemStack target) {
        Configuration configuration = read(source);
        if (configuration.state() == StoredState.CONFIGURED) {
            write(target, configuration);
        } else {
            clear(target);
        }
        VillagerFilterPolicy.copyConfiguration(source, target);
    }

    public static List<Component> tooltip(ItemStack filter) {
        Configuration configuration = read(filter);
        List<Component> tooltip = new ArrayList<>(VillagerFilterPolicy.tooltip(filter));
        if (configuration.state() == StoredState.EMPTY) {
            tooltip.add(Component.translatable("item.villagerretaliation.recipe_filter.empty")
                    .withStyle(ChatFormatting.GRAY));
        } else if (configuration.state() == StoredState.MALFORMED) {
            tooltip.add(Component.translatable("item.villagerretaliation.recipe_filter.malformed")
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable(
                            "item.villagerretaliation.recipe_filter.recipe",
                            configuration.recipeId().toString())
                    .withStyle(ChatFormatting.AQUA));
            if (!configuration.narrowedIngredients().isEmpty()) {
                tooltip.add(Component.translatable(
                                "item.villagerretaliation.recipe_filter.narrowed",
                                configuration.narrowedIngredients().size())
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable("item.villagerretaliation.recipe_filter.process_only")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.villagerretaliation.recipe_filter.controls")
                .withStyle(ChatFormatting.DARK_GRAY));
        return List.copyOf(tooltip);
    }

    private static boolean isSupported(Level level, RecipeHolder<?> holder) {
        if (holder == null) {
            return false;
        }
        Recipe<?> recipe = holder.value();
        RecipeType<?> type = recipe.getType();
        return !recipe.isSpecial()
                && !recipe.getIngredients().isEmpty()
                && recipe.getIngredients().size() <= MAX_INGREDIENTS
                && level != null
                && !recipe.getResultItem(level.registryAccess()).isEmpty()
                && (type == RecipeType.CRAFTING
                        || type == RecipeType.SMELTING
                        || type == RecipeType.SMOKING
                        || type == RecipeType.BLASTING);
    }

    private static void write(ItemStack filter, Configuration configuration) {
        if (!VillagerRetaliationItems.isRecipeFilter(filter)
                || configuration.state() != StoredState.CONFIGURED
                || configuration.recipeId() == null) {
            return;
        }
        CustomData existing = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag customData = existing.isEmpty() ? new CompoundTag() : existing.copyTag();
        CompoundTag root = new CompoundTag();
        root.putInt(VERSION_TAG, CURRENT_VERSION);
        root.putString(RECIPE_TAG, configuration.recipeId().toString());
        ListTag ingredients = new ListTag();
        configuration.narrowedIngredients().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag ingredient = new CompoundTag();
                    ingredient.putInt(SLOT_TAG, entry.getKey());
                    ingredient.putString(ITEM_TAG, entry.getValue().toString());
                    ingredients.add(ingredient);
                });
        root.put(INGREDIENTS_TAG, ingredients);
        customData.put(ROOT_TAG, root);
        store(filter, customData);
    }

    private static void store(ItemStack filter, CompoundTag customData) {
        if (customData.isEmpty()) {
            filter.remove(DataComponents.CUSTOM_DATA);
        } else {
            filter.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        }
    }

    public enum StoredState {
        EMPTY,
        CONFIGURED,
        MALFORMED
    }

    public enum ResolutionState {
        EMPTY,
        VALID,
        MALFORMED,
        MISSING_RECIPE,
        UNSUPPORTED_RECIPE,
        INVALID_ALTERNATIVE
    }

    public record Configuration(
            StoredState state,
            ResourceLocation recipeId,
            Map<Integer, ResourceLocation> narrowedIngredients) {
        public Configuration {
            state = state == null ? StoredState.MALFORMED : state;
            narrowedIngredients = narrowedIngredients == null
                    ? Map.of()
                    : Map.copyOf(narrowedIngredients);
        }

        private static Configuration empty() {
            return new Configuration(StoredState.EMPTY, null, Map.of());
        }

        private static Configuration malformed() {
            return new Configuration(StoredState.MALFORMED, null, Map.of());
        }

        private static Configuration configured(
                ResourceLocation recipeId,
                Map<Integer, ResourceLocation> narrowedIngredients) {
            return new Configuration(StoredState.CONFIGURED, recipeId, narrowedIngredients);
        }
    }

    public record Resolution(
            ResolutionState state,
            Configuration configuration,
            RecipeHolder<?> recipe) {
        public boolean valid() {
            return state == ResolutionState.VALID && recipe != null;
        }

        private static Resolution empty(Configuration configuration) {
            return new Resolution(ResolutionState.EMPTY, configuration, null);
        }

        private static Resolution invalid(ResolutionState state, Configuration configuration) {
            return new Resolution(state, configuration, null);
        }

        private static Resolution valid(Configuration configuration, RecipeHolder<?> recipe) {
            return new Resolution(ResolutionState.VALID, configuration, recipe);
        }
    }
}
