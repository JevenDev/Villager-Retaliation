package com.jvn.villagerretaliation.interaction;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.dialogue.DialogueTextEffects;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VillagerCurrencyResources {
    private static final String CURRENCY_ROOT = "currency";
    private static final int DEFAULT_TEXT_COLOR = 0xFF55FF55;
    public static final TagKey<Item> CURRENCY_TAG = TagKey.create(Registries.ITEM, VillagerRetaliation.id("currency"));
    private static final ResourceLocation DEFAULT_ICON_SPRITE = ResourceLocation.withDefaultNamespace("item/emerald");
    private static final CurrencyDefinition DEFAULT_CURRENCY = new CurrencyDefinition(
            Items.EMERALD,
            Set.of(Items.EMERALD),
            Set.of(CURRENCY_TAG),
            "emerald",
            "emeralds",
            "Emeralds",
            DEFAULT_ICON_SPRITE,
            DEFAULT_TEXT_COLOR
    );

    private static volatile CachedCurrency cachedCurrency = CachedCurrency.empty();

    private VillagerCurrencyResources() {
    }

    public static void warm(MinecraftServer server) {
        load(server);
    }

    public static void clearCache() {
        cachedCurrency = CachedCurrency.empty();
    }

    public static boolean isCurrency(MinecraftServer server, ItemStack stack) {
        return !stack.isEmpty() && load(server).matches(stack);
    }

    public static ItemStack createStack(MinecraftServer server, int count) {
        return load(server).createStack(count);
    }

    public static Item primaryItem(MinecraftServer server) {
        return load(server).item();
    }

    public static int maxStackSize(MinecraftServer server) {
        return load(server).item().getDefaultMaxStackSize();
    }

    public static String nameForCount(MinecraftServer server, int count) {
        return load(server).nameForCount(count);
    }

    public static String format(MinecraftServer server, int count) {
        return count + " " + nameForCount(server, count);
    }

    public static Text text(MinecraftServer server) {
        CurrencyDefinition currency = load(server);
        return new Text(currency.name(), currency.pluralName(), currency.walletLabel(), currency.iconSprite(), currency.textColor());
    }

    private static CurrencyDefinition load(MinecraftServer server) {
        if (server == null) {
            return DEFAULT_CURRENCY;
        }
        CachedCurrency current = cachedCurrency;
        if (current.server() == server) {
            return current.currency();
        }

        synchronized (VillagerCurrencyResources.class) {
            current = cachedCurrency;
            if (current.server() == server) {
                return current.currency();
            }

            CurrencyDefinition loaded = read(server);
            cachedCurrency = new CachedCurrency(server, loaded);
            return loaded;
        }
    }

    private static CurrencyDefinition read(MinecraftServer server) {
        CurrencyDefinition[] selected = new CurrencyDefinition[]{DEFAULT_CURRENCY};
        DatapackResourceLoader.forEachJsonResource(
                server,
                CURRENCY_ROOT,
                location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID),
                (location, resource) -> readFile(location, resource).ifPresent(currency -> selected[0] = currency));
        return selected[0];
    }

    private static Optional<CurrencyDefinition> readFile(ResourceLocation location, Resource resource) {
        return DatapackResourceLoader.readObject(location, "currency", resource)
                .flatMap(VillagerCurrencyResources::readCurrency);
    }

    private static Optional<CurrencyDefinition> readCurrency(JsonObject root) {
        Optional<Item> item = readItem(DatapackJsonReader.readString(root, "item"));
        if (item.isEmpty()) {
            return Optional.empty();
        }

        Set<Item> acceptedItems = new LinkedHashSet<>();
        acceptedItems.add(item.get());
        for (String value : DatapackJsonReader.readStringList(root, "accepted_item", "accepted_items", "items")) {
            readItem(value).ifPresent(acceptedItems::add);
        }

        Set<TagKey<Item>> acceptedTags = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(root, "accepted_tag", "accepted_tags", "tags")) {
            parseTag(value).ifPresent(acceptedTags::add);
        }

        String fallbackName = item.get().getDescription().getString();
        String name = fallback(DatapackJsonReader.readString(root, "name", "singular_name"), fallbackName);
        String pluralName = fallback(DatapackJsonReader.readString(root, "plural_name"), name + "s");
        String walletLabel = fallback(DatapackJsonReader.readString(root, "wallet_label"), titleCase(pluralName));
        ResourceLocation iconSprite = readIconSprite(root).orElseGet(() -> itemSprite(item.get()));
        int textColor = readTextColor(root).orElse(DEFAULT_TEXT_COLOR);
        return Optional.of(new CurrencyDefinition(
                item.get(),
                Set.copyOf(acceptedItems),
                Set.copyOf(acceptedTags),
                name,
                pluralName,
                walletLabel,
                iconSprite,
                textColor
        ));
    }

    private static Optional<Integer> readTextColor(JsonObject root) {
        for (String key : new String[]{"text_color", "wallet_text_color", "wallet_color", "color"}) {
            Integer rgb = DialogueTextEffects.parseColor(DatapackJsonReader.readString(root, key));
            if (rgb != null) {
                return Optional.of(0xFF000000 | rgb);
            }
        }
        return Optional.empty();
    }

    private static Optional<ResourceLocation> readIconSprite(JsonObject root) {
        return parseSpriteLocation(DatapackJsonReader.readString(
                root,
                "icon_sprite",
                "currency_sprite",
                "display_sprite",
                "sprite",
                "texture"
        ));
    }

    private static Optional<Item> readItem(String value) {
        return parseResourceLocation(value)
                .flatMap(location -> BuiltInRegistries.ITEM.getOptional(location))
                .filter(item -> item != Items.AIR);
    }

    private static Optional<TagKey<Item>> parseTag(String value) {
        String normalized = value != null && value.startsWith("#") ? value.substring(1) : value;
        return parseResourceLocation(normalized)
                .map(location -> TagKey.create(Registries.ITEM, location));
    }

    private static Optional<ResourceLocation> parseResourceLocation(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.contains(":") ? value : "minecraft:" + value;
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }

    private static Optional<ResourceLocation> parseSpriteLocation(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().replace('\\', '/');
        int namespaceSeparator = normalized.indexOf(':');
        String namespace = namespaceSeparator >= 0 ? normalized.substring(0, namespaceSeparator) : "minecraft";
        String path = namespaceSeparator >= 0 ? normalized.substring(namespaceSeparator + 1) : normalized;
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - ".png".length());
        }
        return Optional.ofNullable(ResourceLocation.tryParse(namespace + ":" + path));
    }

    private static ResourceLocation itemSprite(Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), "item/" + itemId.getPath());
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private record CurrencyDefinition(
            Item item,
            Set<Item> acceptedItems,
            Set<TagKey<Item>> acceptedTags,
            String name,
            String pluralName,
            String walletLabel,
            ResourceLocation iconSprite,
            int textColor) {
        private boolean matches(ItemStack stack) {
            return this.acceptedItems.stream().anyMatch(stack::is)
                    || this.acceptedTags.stream().anyMatch(stack::is);
        }

        private ItemStack createStack(int count) {
            int safeCount = Math.max(0, Math.min(count, this.item.getDefaultMaxStackSize()));
            return safeCount <= 0 ? ItemStack.EMPTY : new ItemStack(this.item, safeCount);
        }

        private String nameForCount(int count) {
            return count == 1 ? this.name : this.pluralName;
        }
    }

    private record CachedCurrency(MinecraftServer server, CurrencyDefinition currency) {
        private static CachedCurrency empty() {
            return new CachedCurrency(null, DEFAULT_CURRENCY);
        }
    }

    public record Text(String name, String pluralName, String walletLabel, ResourceLocation iconSprite, int textColor) {
    }
}
