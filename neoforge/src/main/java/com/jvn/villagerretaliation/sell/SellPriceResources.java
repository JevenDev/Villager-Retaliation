package com.jvn.villagerretaliation.sell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.ServerResourceCache;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SellPriceResources {
    public static final String RESOURCE_ROOT = "sell_prices";
    private static final Set<String> ALLOWED_KEYS =
            Set.of("enabled", "item", "item_count", "currency_count");
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
        return Optional.ofNullable(CACHE.get(server).byItem().get(stack.getItem()));
    }

    public static Map<Item, SellPriceDefinition> definitions(MinecraftServer server) {
        return CACHE.get(server).byItem();
    }

    private static Catalog read(MinecraftServer server) {
        Map<Item, SellPriceDefinition> definitions = new LinkedHashMap<>();
        Map<Item, ResourceLocation> sources = new LinkedHashMap<>();
        DatapackResourceLoader.forEachJsonResource(server, RESOURCE_ROOT, (location, resource) ->
                readFile(location, resource).ifPresent(definition -> {
                    ResourceLocation previous = sources.put(definition.item(), location);
                    if (previous != null) {
                        DatapackDiagnostics.warnDuplicateId(
                                location,
                                "sell-price item",
                                BuiltInRegistries.ITEM.getKey(definition.item()).toString(),
                                previous);
                    }
                    definitions.put(definition.item(), definition);
                }));
        return new Catalog(Map.copyOf(definitions));
    }

    private static Optional<SellPriceDefinition> readFile(ResourceLocation location, Resource resource) {
        Optional<JsonObject> loaded = DatapackResourceLoader.readObject(location, "sell price", resource);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        JsonObject root = loaded.get();
        DatapackDiagnostics.warnUnknownRootKeys(location, "sell price", root, ALLOWED_KEYS);
        if (!DatapackJsonReader.readBoolean(root, "enabled", true)) {
            return Optional.empty();
        }

        ResourceLocation itemId = ResourceLocation.tryParse(DatapackJsonReader.readString(root, "item"));
        Item item = itemId == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
        if (item == Items.AIR) {
            DatapackDiagnostics.warnSkippedEntry(location, "sell price", "item", "unknown or missing item id.");
            return Optional.empty();
        }

        try {
            SellPriceDefinition.IntRange itemCount = readRange(root.get("item_count"), "item_count");
            SellPriceDefinition.IntRange currencyCount = readRange(root.get("currency_count"), "currency_count");
            return Optional.of(new SellPriceDefinition(resourceId(location), item, itemCount, currencyCount));
        } catch (IllegalArgumentException exception) {
            DatapackDiagnostics.warnSkippedEntry(location, "sell price", "range", exception.getMessage());
            return Optional.empty();
        }
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

    private static ResourceLocation resourceId(ResourceLocation location) {
        String path = location.getPath();
        String prefix = RESOURCE_ROOT + "/";
        String suffix = ".json";
        String idPath = path.startsWith(prefix) && path.endsWith(suffix)
                ? path.substring(prefix.length(), path.length() - suffix.length())
                : path;
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), idPath);
    }

    private record Catalog(Map<Item, SellPriceDefinition> byItem) {
        private static Catalog empty() {
            return new Catalog(Map.of());
        }
    }
}
