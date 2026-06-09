package com.jvn.villagerretaliation.interaction.work;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

public final class HiredBrewingRecipeCatalog {
    private static final int MAX_ROUTE_DEPTH = 8;

    private HiredBrewingRecipeCatalog() {
    }

    public static List<BrewingRoute> routes(Level level) {
        if (level == null) {
            return List.of();
        }
        PotionBrewing brewing = level.potionBrewing();
        List<Item> ingredients = BuiltInRegistries.ITEM.stream()
                .filter(item -> brewing.isIngredient(new ItemStack(item)))
                .toList();
        Queue<SearchState> queue = new ArrayDeque<>();
        Map<BrewingKey, List<Item>> discovered = new HashMap<>();
        BrewingKey waterKey = key(Items.POTION, Potions.WATER);
        queue.add(new SearchState(waterKey, PotionContents.createItemStack(Items.POTION, Potions.WATER), List.of()));
        discovered.put(waterKey, List.of());

        while (!queue.isEmpty()) {
            SearchState current = queue.remove();
            if (current.ingredients().size() >= MAX_ROUTE_DEPTH) {
                continue;
            }
            for (Item ingredient : ingredients) {
                ItemStack ingredientStack = new ItemStack(ingredient);
                ItemStack result = brewing.mix(ingredientStack, current.stack());
                Optional<BrewingKey> resultKey = key(result);
                if (resultKey.isEmpty() || resultKey.get().equals(current.key()) || discovered.containsKey(resultKey.get())) {
                    continue;
                }
                List<Item> route = new ArrayList<>(current.ingredients());
                route.add(ingredient);
                discovered.put(resultKey.get(), List.copyOf(route));
                queue.add(new SearchState(resultKey.get(), result.copyWithCount(1), List.copyOf(route)));
            }
        }

        List<BrewingRoute> routes = new ArrayList<>();
        for (Map.Entry<BrewingKey, List<Item>> entry : discovered.entrySet()) {
            if (entry.getKey().equals(waterKey) || entry.getValue().isEmpty()) {
                continue;
            }
            Optional<Holder<Potion>> potion = potionHolder(entry.getKey().potionId());
            Optional<Item> item = item(entry.getKey().itemId());
            if (potion.isEmpty() || item.isEmpty() || !(item.get() instanceof PotionItem)) {
                continue;
            }
            routes.add(new BrewingRoute(
                    entry.getKey().itemId(),
                    entry.getKey().potionId(),
                    PotionContents.createItemStack(item.get(), potion.get()),
                    entry.getValue()));
        }
        routes.sort(Comparator.comparing(route -> route.output().getHoverName().getString()));
        return routes;
    }

    public static Optional<BrewingRoute> find(Level level, ResourceLocation itemId, ResourceLocation potionId) {
        if (itemId == null || potionId == null) {
            return Optional.empty();
        }
        return routes(level).stream()
                .filter(route -> route.itemId().equals(itemId) && route.potionId().equals(potionId))
                .findFirst();
    }

    public static List<BrewingPotionChoice> potionChoices(Level level) {
        Map<BrewingEffectKey, List<BrewingRoute>> grouped = new LinkedHashMap<>();
        for (BrewingRoute route : routes(level)) {
            BrewingEffectKey key = effectKey(route);
            if (key.effectIds().isEmpty()) {
                continue;
            }
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(route);
        }
        List<BrewingPotionChoice> choices = new ArrayList<>();
        for (Map.Entry<BrewingEffectKey, List<BrewingRoute>> entry : grouped.entrySet()) {
            List<BrewingRoute> groupedRoutes = sortedRoutes(entry.getValue());
            choices.add(new BrewingPotionChoice(entry.getKey(), potionLabel(groupedRoutes.getFirst()), groupedRoutes));
        }
        choices.sort(Comparator.comparing(BrewingPotionChoice::label));
        return choices;
    }

    public static List<BrewingLevelChoice> levelChoices(BrewingPotionChoice choice) {
        if (choice == null) {
            return List.of();
        }
        Map<Integer, List<BrewingRoute>> grouped = new HashMap<>();
        for (BrewingRoute route : choice.routes()) {
            grouped.computeIfAbsent(level(route), ignored -> new ArrayList<>()).add(route);
        }
        List<BrewingLevelChoice> choices = new ArrayList<>();
        for (Map.Entry<Integer, List<BrewingRoute>> entry : grouped.entrySet()) {
            choices.add(new BrewingLevelChoice(entry.getKey(), sortedRoutes(entry.getValue())));
        }
        choices.sort(Comparator.comparingInt(BrewingLevelChoice::level));
        return choices;
    }

    public static List<BrewingDurationChoice> durationChoices(BrewingLevelChoice choice) {
        if (choice == null) {
            return List.of();
        }
        Map<Integer, List<BrewingRoute>> grouped = new HashMap<>();
        for (BrewingRoute route : choice.routes()) {
            grouped.computeIfAbsent(durationTicks(route), ignored -> new ArrayList<>()).add(route);
        }
        List<BrewingDurationChoice> choices = new ArrayList<>();
        for (Map.Entry<Integer, List<BrewingRoute>> entry : grouped.entrySet()) {
            choices.add(new BrewingDurationChoice(entry.getKey(), sortedRoutes(entry.getValue())));
        }
        choices.sort(Comparator.comparingInt(BrewingDurationChoice::sortDurationTicks));
        return choices;
    }

    public static List<BrewingTypeChoice> typeChoices(BrewingDurationChoice choice) {
        if (choice == null) {
            return List.of();
        }
        Map<BrewingPotionType, BrewingRoute> grouped = new HashMap<>();
        for (BrewingRoute route : choice.routes()) {
            grouped.putIfAbsent(BrewingPotionType.of(route.output()), route);
        }
        List<BrewingTypeChoice> choices = new ArrayList<>();
        for (Map.Entry<BrewingPotionType, BrewingRoute> entry : grouped.entrySet()) {
            choices.add(new BrewingTypeChoice(entry.getKey(), entry.getValue()));
        }
        choices.sort(Comparator.comparingInt(choiceEntry -> choiceEntry.type().sortOrder()));
        return choices;
    }

    public static boolean isWaterPotion(ItemStack stack) {
        return stack != null
                && stack.is(Items.POTION)
                && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
    }

    private static Optional<BrewingKey> key(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof PotionItem)) {
            return Optional.empty();
        }
        Optional<Holder<Potion>> potion = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion();
        if (potion.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BrewingKey(BuiltInRegistries.ITEM.getKey(stack.getItem()), potionId(potion.get())));
    }

    private static BrewingKey key(Item item, Holder<Potion> potion) {
        return new BrewingKey(BuiltInRegistries.ITEM.getKey(item), potionId(potion));
    }

    private static ResourceLocation potionId(Holder<Potion> potion) {
        return potion.unwrapKey()
                .map(key -> key.location())
                .orElseGet(() -> BuiltInRegistries.POTION.getKey(potion.value()));
    }

    private static Optional<Holder<Potion>> potionHolder(ResourceLocation id) {
        for (Holder<Potion> holder : BuiltInRegistries.POTION.holders().toList()) {
            if (potionId(holder).equals(id)) {
                return Optional.of(holder);
            }
        }
        return Optional.empty();
    }

    private static Optional<Item> item(ResourceLocation id) {
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return Optional.empty();
        }
        return Optional.of(BuiltInRegistries.ITEM.get(id));
    }

    private static List<BrewingRoute> sortedRoutes(List<BrewingRoute> routes) {
        return routes.stream()
                .sorted(Comparator.comparing(route -> route.output().getHoverName().getString()))
                .toList();
    }

    private static BrewingEffectKey effectKey(BrewingRoute route) {
        return new BrewingEffectKey(effects(route).stream()
                .map(effect -> effectId(effect.getEffect()))
                .sorted()
                .toList());
    }

    private static ResourceLocation effectId(Holder<MobEffect> effect) {
        return effect.unwrapKey()
                .map(key -> key.location())
                .orElseGet(() -> BuiltInRegistries.MOB_EFFECT.getKey(effect.value()));
    }

    private static String potionLabel(BrewingRoute route) {
        Optional<Holder<Potion>> potion = route.output()
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion();
        String key = Potion.getName(potion, "item.minecraft.potion.effect.");
        String label = Component.translatable(key).getString();
        if (label.isBlank() || label.equals(key)) {
            return route.output().getHoverName().getString();
        }
        return label;
    }

    private static int durationTicks(BrewingRoute route) {
        List<MobEffectInstance> effects = effects(route);
        if (effects.isEmpty() || effects.stream().allMatch(effect -> effect.getEffect().value().isInstantenous())) {
            return 0;
        }
        return effects.stream()
                .filter(effect -> !effect.getEffect().value().isInstantenous())
                .mapToInt(MobEffectInstance::getDuration)
                .max()
                .orElse(0);
    }

    private static int level(BrewingRoute route) {
        return effects(route).stream()
                .mapToInt(effect -> effect.getAmplifier() + 1)
                .max()
                .orElse(1);
    }

    private static List<MobEffectInstance> effects(BrewingRoute route) {
        List<MobEffectInstance> effects = new ArrayList<>();
        route.output()
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .getAllEffects()
                .forEach(effects::add);
        return effects;
    }

    private record BrewingKey(ResourceLocation itemId, ResourceLocation potionId) {
    }

    private record SearchState(BrewingKey key, ItemStack stack, List<Item> ingredients) {
    }

    public record BrewingRoute(ResourceLocation itemId, ResourceLocation potionId, ItemStack output, List<Item> ingredients) {
        public BrewingRoute {
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        }

        public Set<Item> uniqueIngredients() {
            return new HashSet<>(this.ingredients);
        }
    }

    public record BrewingEffectKey(List<ResourceLocation> effectIds) {
        public BrewingEffectKey {
            effectIds = effectIds == null ? List.of() : List.copyOf(effectIds);
        }
    }

    public record BrewingPotionChoice(BrewingEffectKey key, String label, List<BrewingRoute> routes) {
        public BrewingPotionChoice {
            routes = routes == null ? List.of() : List.copyOf(routes);
        }
    }

    public record BrewingDurationChoice(int durationTicks, List<BrewingRoute> routes) {
        public BrewingDurationChoice {
            routes = routes == null ? List.of() : List.copyOf(routes);
        }

        private int sortDurationTicks() {
            return this.durationTicks <= 0 ? 0 : this.durationTicks;
        }
    }

    public record BrewingLevelChoice(int level, List<BrewingRoute> routes) {
        public BrewingLevelChoice {
            routes = routes == null ? List.of() : List.copyOf(routes);
        }
    }

    public record BrewingTypeChoice(BrewingPotionType type, BrewingRoute route) {
    }

    public enum BrewingPotionType {
        REGULAR(0, "recruit.brewing_type_regular"),
        SPLASH(1, "recruit.brewing_type_splash"),
        LINGERING(2, "recruit.brewing_type_lingering"),
        OTHER(3, "");

        private final int sortOrder;
        private final String labelKey;

        BrewingPotionType(int sortOrder, String labelKey) {
            this.sortOrder = sortOrder;
            this.labelKey = labelKey;
        }

        public int sortOrder() {
            return this.sortOrder;
        }

        public String labelKey() {
            return this.labelKey;
        }

        static BrewingPotionType of(ItemStack stack) {
            if (stack.is(Items.POTION)) {
                return REGULAR;
            }
            if (stack.is(Items.SPLASH_POTION)) {
                return SPLASH;
            }
            if (stack.is(Items.LINGERING_POTION)) {
                return LINGERING;
            }
            return OTHER;
        }
    }
}
