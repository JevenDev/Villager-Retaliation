package com.jvn.villagerretaliation.sell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.ServerResourceCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SellPriceResources {
    public static final String RESOURCE_ROOT = "sell_prices";
    private static final Set<String> ALLOWED_KEYS =
            Set.of(
                    "enabled",
                    "item",
                    "item_count",
                    "currency_count",
                    "rates",
                    "market_group",
                    "components",
                    "durability",
                    "priority");
    private static final Set<String> ALLOWED_RATE_KEYS = Set.of("item_count", "currency_count");
    private static final Comparator<SellPriceDefinition> MATCH_ORDER = Comparator
            .comparingInt(SellPriceDefinition::priority)
            .reversed()
            .thenComparing(definition -> definition.id().toString(), Comparator.reverseOrder());
    private static final HolderLookup.Provider DEFAULT_REGISTRIES =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final ServerResourceCache<Catalog> CACHE =
            ServerResourceCache.create(Catalog::empty, SellPriceResources::read);
    private static final java.util.concurrent.atomic.AtomicLong GENERATION = new java.util.concurrent.atomic.AtomicLong();

    private SellPriceResources() {
    }

    public static void warm(MinecraftServer server) {
        CACHE.get(server);
    }

    public static void clearCache() {
        CACHE.clear();
        GENERATION.incrementAndGet();
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static Optional<SellPriceDefinition> definition(MinecraftServer server, ItemStack stack) {
        if (server == null
                || stack == null
                || stack.isEmpty()
                || VillagerCurrencyResources.isCurrency(server, stack)) {
            return Optional.empty();
        }
        return selectDefinition(stack, CACHE.get(server).byItem().getOrDefault(stack.getItem(), List.of()));
    }

    static Optional<SellPriceDefinition> selectDefinition(
            ItemStack stack,
            List<SellPriceDefinition> definitions) {
        if (stack == null || stack.isEmpty() || definitions == null || definitions.isEmpty()) {
            return Optional.empty();
        }
        return definitions.stream()
                .filter(definition -> definition.stackPredicate().matches(stack))
                .min(MATCH_ORDER);
    }

    public static Map<Item, List<SellPriceDefinition>> definitions(MinecraftServer server) {
        return CACHE.get(server).byItem();
    }

    private static Catalog read(MinecraftServer server) {
        Map<Item, List<SellPriceDefinition>> definitions = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> sources = new LinkedHashMap<>();
        DatapackResourceLoader.forEachJsonResource(server, RESOURCE_ROOT, (location, resource) ->
                readFile(server.registryAccess(), location, resource).forEach(definition -> {
                    sources.put(definition.id(), location);
                    definitions.computeIfAbsent(definition.item(), ignored -> new ArrayList<>())
                            .add(definition);
                }));

        LinkedHashMap<Item, List<SellPriceDefinition>> ordered = new LinkedHashMap<>();
        definitions.forEach((item, itemDefinitions) -> {
            ArrayList<SellPriceDefinition> sorted = new ArrayList<>(itemDefinitions);
            sorted.sort(MATCH_ORDER);
            ordered.put(item, List.copyOf(sorted));
        });
        Map<Item, List<SellPriceDefinition>> immutable =
                Collections.unmodifiableMap(ordered);
        diagnoseAmbiguities(immutable, sources);
        return new Catalog(immutable);
    }

    private static void diagnoseAmbiguities(
            Map<Item, List<SellPriceDefinition>> definitions,
            Map<ResourceLocation, ResourceLocation> sources) {
        Set<String> warnedPairs = new java.util.HashSet<>();
        definitions.forEach((item, candidates) -> {
            for (int firstIndex = 0; firstIndex < candidates.size(); firstIndex++) {
                SellPriceDefinition first = candidates.get(firstIndex);
                for (int secondIndex = firstIndex + 1; secondIndex < candidates.size(); secondIndex++) {
                    SellPriceDefinition second = candidates.get(secondIndex);
                    if (first.priority() != second.priority()
                            || !first.stackPredicate().overlaps(second.stackPredicate())) {
                        continue;
                    }
                    String firstId = first.id().toString();
                    String secondId = second.id().toString();
                    String pair = firstId.compareTo(secondId) <= 0
                            ? firstId + "|" + secondId + "|" + first.priority()
                            : secondId + "|" + firstId + "|" + first.priority();
                    if (warnedPairs.add(pair)) {
                        DatapackDiagnostics.warnAmbiguousSellPrice(
                                sources.getOrDefault(first.id(), first.id()),
                                BuiltInRegistries.ITEM.getKey(item),
                                first.id(),
                                second.id(),
                                first.priority());
                    }
                }
            }
        });
    }

    private static List<SellPriceDefinition> readFile(
            HolderLookup.Provider registries,
            ResourceLocation location,
            Resource resource) {
        Optional<JsonObject> loaded = DatapackResourceLoader.readObject(location, "sell price", resource);
        return loaded.map(root -> definitionsFromJson(registries, location, root)).orElseGet(List::of);
    }

    static Optional<SellPriceDefinition> definitionFromJson(ResourceLocation location, JsonObject root) {
        return definitionFromJson(DEFAULT_REGISTRIES, location, root);
    }

    static Optional<SellPriceDefinition> definitionFromJson(
            HolderLookup.Provider registries,
            ResourceLocation location,
            JsonObject root) {
        return definitionsFromJson(registries, location, root).stream().findFirst();
    }

    static List<SellPriceDefinition> definitionsFromJson(ResourceLocation location, JsonObject root) {
        return definitionsFromJson(DEFAULT_REGISTRIES, location, root);
    }

    static List<SellPriceDefinition> definitionsFromJson(
            HolderLookup.Provider registries,
            ResourceLocation location,
            JsonObject root) {
        if (location == null || root == null) {
            return List.of();
        }
        DatapackDiagnostics.warnUnknownRootKeys(location, "sell price", root, ALLOWED_KEYS);
        if (!DatapackJsonReader.readBoolean(root, "enabled", true)) {
            return List.of();
        }

        ItemSelection selection;
        try {
            selection = readItemSelection(registries, root);
        } catch (IllegalArgumentException exception) {
            DatapackDiagnostics.warnSkippedEntry(location, "sell price", "item", exception.getMessage());
            return List.of();
        }

        try {
            List<SellRateDefinition> rates = readRates(location, root);
            ResourceLocation marketGroup = readMarketGroup(root, selection.defaultMarketGroup());
            SellStackPredicate stackPredicate = SellStackPredicateParser.parse(
                    registries, root, selection.shorthandComponents(), selection.items());
            int priority = readPriority(root);
            ResourceLocation definitionId = resourceId(location);
            return selection.items().stream()
                    .map(item -> new SellPriceDefinition(
                            definitionId, item, rates, marketGroup, stackPredicate, priority))
                    .toList();
        } catch (IllegalArgumentException exception) {
            DatapackDiagnostics.warnSkippedEntry(location, "sell price", "definition", exception.getMessage());
            return List.of();
        }
    }

    private static List<SellRateDefinition> readRates(ResourceLocation location, JsonObject root) {
        boolean hasRates = root.has("rates");
        boolean hasLegacyItemCount = root.has("item_count");
        boolean hasLegacyCurrencyCount = root.has("currency_count");
        if (hasRates && (hasLegacyItemCount || hasLegacyCurrencyCount)) {
            throw new IllegalArgumentException(
                    "rates cannot be combined with legacy top-level item_count or currency_count.");
        }
        if (!hasRates) {
            return List.of(new SellRateDefinition(
                    readRange(root.get("item_count"), "item_count"),
                    readRange(root.get("currency_count"), "currency_count")));
        }

        JsonElement element = root.get("rates");
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("rates must be a non-empty array.");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            throw new IllegalArgumentException("rates must be a non-empty array.");
        }
        if (array.size() > SellPriceDefinition.MAX_RATES) {
            throw new IllegalArgumentException(
                    "rates must contain at most " + SellPriceDefinition.MAX_RATES + " entries.");
        }

        ArrayList<SellRateDefinition> rates = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            JsonElement rateElement = array.get(index);
            if (rateElement == null || !rateElement.isJsonObject()) {
                throw new IllegalArgumentException("rates[" + index + "] must be an object.");
            }
            JsonObject rate = rateElement.getAsJsonObject();
            DatapackDiagnostics.warnUnknownKeys(
                    location, "sell price rate", "rates[" + index + "]", rate, ALLOWED_RATE_KEYS);
            rates.add(new SellRateDefinition(
                    readRange(rate.get("item_count"), "rates[" + index + "].item_count"),
                    readRange(rate.get("currency_count"), "rates[" + index + "].currency_count")));
        }
        return List.copyOf(rates);
    }

    private static ItemSelection readItemSelection(HolderLookup.Provider registries, JsonObject root) {
        String selector = DatapackJsonReader.readString(root, "item").trim();
        boolean isTag = selector.startsWith("#");
        boolean hasShorthand = selector.indexOf('[') >= 0;
        if (!isTag && hasShorthand) {
            SellStackPredicateParser.ParsedItem parsed =
                    SellStackPredicateParser.parseItemShorthand(registries, selector);
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(parsed.item());
            return new ItemSelection(List.of(parsed.item()), itemId, parsed.components());
        }
        if (isTag && hasShorthand) {
            throw new IllegalArgumentException(
                    "component shorthand is only supported for exact item ids; use components with tags.");
        }

        String rawId = isTag ? selector.substring(1) : selector;
        ResourceLocation selectorId = ResourceLocation.tryParse(rawId);
        if (selectorId == null) {
            throw new IllegalArgumentException("must be a valid item id or #item tag.");
        }
        if (!isTag) {
            Item item = BuiltInRegistries.ITEM.getOptional(selectorId).orElse(Items.AIR);
            if (item == Items.AIR) {
                throw new IllegalArgumentException("references an unknown item id.");
            }
            return new ItemSelection(List.of(item), selectorId, List.of());
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, selectorId);
        List<Item> items = BuiltInRegistries.ITEM.getTag(tag)
                .map(holders -> holders.stream()
                        .map(holder -> holder.value())
                        .filter(item -> item != Items.AIR)
                        .sorted(java.util.Comparator.comparing(
                                item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                        .toList())
                .orElseGet(List::of);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("references an unknown or empty item tag.");
        }
        return new ItemSelection(items, selectorId, List.of());
    }

    private static ResourceLocation readMarketGroup(JsonObject root, ResourceLocation defaultGroup) {
        if (!root.has("market_group")) {
            return defaultGroup;
        }
        JsonElement element = root.get("market_group");
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("market_group must be a resource location string.");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(element.getAsString());
        if (parsed == null) {
            throw new IllegalArgumentException("market_group must be a valid resource location.");
        }
        return parsed;
    }

    private static SellPriceDefinition.IntRange readRange(JsonElement element, String fieldName) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        if (element.isJsonPrimitive()) {
            return SellPriceDefinition.IntRange.fixed(readPositiveInteger(element, fieldName));
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(fieldName + " must be an integer or an object with min and max.");
        }
        JsonObject range = element.getAsJsonObject();
        if (!range.has("min") || !range.has("max")) {
            throw new IllegalArgumentException(fieldName + " range requires both min and max.");
        }
        int min = readPositiveInteger(range.get("min"), fieldName + ".min");
        int max = readPositiveInteger(range.get("max"), fieldName + ".max");
        return new SellPriceDefinition.IntRange(min, max);
    }

    private static int readPositiveInteger(JsonElement element, String fieldName) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()
                || !element.getAsString().matches("[0-9]+")) {
            throw new IllegalArgumentException(fieldName + " must be a positive integer.");
        }
        try {
            return Integer.parseInt(element.getAsString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " is outside the supported integer range.");
        }
    }

    private static int readPriority(JsonObject root) {
        JsonElement element = root.get("priority");
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        if (!element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()
                || !element.getAsString().matches("-?[0-9]+")) {
            throw new IllegalArgumentException("priority must be an integer.");
        }
        try {
            return Integer.parseInt(element.getAsString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("priority is outside the supported integer range.");
        }
    }

    private static ResourceLocation resourceId(ResourceLocation location) {
        String path = location.getPath();
        String prefix = RESOURCE_ROOT + "/";
        String suffix = ".json";
        String idPath = path.startsWith(prefix) && path.endsWith(suffix)
                ? path.substring(prefix.length(), path.length() - suffix.length())
                : path;
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), idPath);
    }

    private record ItemSelection(
            List<Item> items,
            ResourceLocation defaultMarketGroup,
            List<ComponentPredicate> shorthandComponents) {
    }

    private record Catalog(Map<Item, List<SellPriceDefinition>> byItem) {
        private static Catalog empty() {
            return new Catalog(Map.of());
        }
    }
}
