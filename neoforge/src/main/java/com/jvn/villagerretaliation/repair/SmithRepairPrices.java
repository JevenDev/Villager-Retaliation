package com.jvn.villagerretaliation.repair;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;

public final class SmithRepairPrices {
    private static final ResourceLocation PRICES = VillagerRetaliation.id("smith_repairs/prices.json");
    private static volatile Cached cached;
    private static volatile int generation;

    private SmithRepairPrices() {
    }

    public static void clearCache() {
        cached = null;
        generation++;
    }

    public static int generation() {
        return generation;
    }

    public static int basePrice(MinecraftServer server, ItemStack stack) {
        return load(server).price(stack);
    }

    private static Definition load(MinecraftServer server) {
        Cached current = cached;
        if (current != null && current.server() == server) return current.definition();
        Definition definition = read(server.getResourceManager());
        cached = new Cached(server, definition);
        return definition;
    }

    public static void reload(MinecraftServer server, net.minecraft.server.packs.resources.ResourceManager resources) {
        cached = new Cached(server, read(resources));
        generation++;
    }

    private static Definition read(net.minecraft.server.packs.resources.ResourceManager resources) {
        return resources.getResource(PRICES)
                .flatMap(resource -> DatapackResourceLoader.readObject(PRICES, "smith repair prices", resource))
                .map(root -> {
                    try {
                        return parse(root);
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        DatapackDiagnostics.warnSkippedFile(PRICES, "smith repair prices", exception);
                        return defaults();
                    }
                }).orElseGet(SmithRepairPrices::defaults);
    }

    public static Definition parse(JsonObject root) {
        Definition defaults = defaults();
        Map<String, Integer> tiers = new HashMap<>(defaults.tiers());
        Map<Item, Integer> items = new HashMap<>();
        if (root.has("tiers")) {
            if (!root.get("tiers").isJsonObject()) throw new IllegalArgumentException("Repair tiers must be an object");
            root.getAsJsonObject("tiers").entrySet().forEach(entry -> {
                if (!tiers.containsKey(entry.getKey())) throw new IllegalArgumentException("Unknown repair tier: " + entry.getKey());
                tiers.put(entry.getKey(), positivePrice(entry.getValue()));
            });
        }
        if (root.has("items")) {
            if (!root.get("items").isJsonObject()) throw new IllegalArgumentException("Repair items must be an object");
            root.getAsJsonObject("items").entrySet().forEach(entry -> {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null) throw new IllegalArgumentException("Unknown repair item: " + entry.getKey());
                items.put(item, positivePrice(entry.getValue()));
            });
        }
        int fallback = root.has("fallback") ? positivePrice(root.get("fallback")) : defaults.fallback();
        return new Definition(fallback, Map.copyOf(tiers), Map.copyOf(items));
    }

    private static int positivePrice(com.google.gson.JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Repair prices must be integers from 1 to 1000000");
        }
        try {
            int price = value.getAsBigDecimal().intValueExact();
            if (price < 1 || price > 1_000_000) throw new IllegalArgumentException("Repair price out of range");
            return price;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Repair price must be an integer", exception);
        }
    }

    public static Definition defaults() {
        return new Definition(4, Map.of("wood", 1, "leather", 1, "stone", 2, "chainmail", 2,
                "iron", 3, "gold", 3, "diamond", 6, "netherite", 10), Map.of());
    }

    private static String tier(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem item) {
            if (item.getTier() == Tiers.WOOD) return "wood";
            if (item.getTier() == Tiers.STONE) return "stone";
            if (item.getTier() == Tiers.IRON) return "iron";
            if (item.getTier() == Tiers.GOLD) return "gold";
            if (item.getTier() == Tiers.DIAMOND) return "diamond";
            if (item.getTier() == Tiers.NETHERITE) return "netherite";
        }
        if (stack.getItem() instanceof ArmorItem item) {
            if (item.getMaterial().equals(ArmorMaterials.LEATHER)) return "leather";
            if (item.getMaterial().equals(ArmorMaterials.CHAIN)) return "chainmail";
            if (item.getMaterial().equals(ArmorMaterials.IRON)) return "iron";
            if (item.getMaterial().equals(ArmorMaterials.GOLD)) return "gold";
            if (item.getMaterial().equals(ArmorMaterials.DIAMOND)) return "diamond";
            if (item.getMaterial().equals(ArmorMaterials.NETHERITE)) return "netherite";
        }
        return "";
    }

    public record Definition(int fallback, Map<String, Integer> tiers, Map<Item, Integer> items) {
        public int price(ItemStack stack) {
            return items.getOrDefault(stack.getItem(), tiers.getOrDefault(tier(stack), fallback));
        }
    }

    private record Cached(MinecraftServer server, Definition definition) {
    }
}
