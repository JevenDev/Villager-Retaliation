package com.jvn.villagerretaliation.interaction.work.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The bill for a complete authored template. Payment never changes its block palette. */
public record BuilderConstructionCost(Mode mode, List<Ingredient> ingredients) {
    public static final BuilderConstructionCost EXACT = new BuilderConstructionCost(Mode.EXACT, List.of());
    public static final int MAX_INGREDIENTS = 256;
    public static final int MAX_COUNT = 1_000_000;

    public BuilderConstructionCost {
        if (mode == null || ingredients == null || ingredients.size() > MAX_INGREDIENTS) {
            throw new IllegalArgumentException("Invalid construction cost");
        }
        ingredients = List.copyOf(ingredients);
        if (mode == Mode.SIMPLIFIED && ingredients.isEmpty()) {
            throw new IllegalArgumentException("Simplified construction costs must not be empty");
        }
        if (mode == Mode.EXACT && !ingredients.isEmpty()) {
            throw new IllegalArgumentException("Exact costs are derived from the template");
        }
    }

    public static List<Ingredient> requirements(BuilderStructureScanner.StructurePlan plan) {
        BuilderConstructionCost cost = plan.entry().constructionCost();
        if (cost.mode() == Mode.SIMPLIFIED) return cost.ingredients();
        return plan.materials().stream().map(material -> new Ingredient(
                BuiltInRegistries.ITEM.getKey(material.item().getItem()), false, material.count())).toList();
    }

    /** Strict parsing prevents a typo or malformed bill becoming a free structure. */
    public static BuilderConstructionCost parse(JsonObject json) {
        if (json == null) throw new IllegalArgumentException("construction_cost must be an object");
        for (String key : json.keySet()) {
            if (!key.equals("mode") && !key.equals("materials")) {
                throw new IllegalArgumentException("Unknown construction_cost key: " + key);
            }
        }
        if (!json.has("mode") || !json.get("mode").isJsonPrimitive()
                || !json.getAsJsonPrimitive("mode").isString()) {
            throw new IllegalArgumentException("construction_cost requires mode exact or simplified");
        }
        Mode mode;
        try {
            mode = Mode.valueOf(json.get("mode").getAsString().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unknown construction cost mode", exception);
        }
        if (mode == Mode.EXACT) {
            if (json.has("materials")) throw new IllegalArgumentException("Exact costs cannot override materials");
            return EXACT;
        }
        if (!json.has("materials") || !json.get("materials").isJsonArray()) {
            throw new IllegalArgumentException("Simplified costs require a materials array");
        }
        JsonArray materials = json.getAsJsonArray("materials");
        List<Ingredient> ingredients = new ArrayList<>();
        for (JsonElement element : materials) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Material must be an object");
            JsonObject material = element.getAsJsonObject();
            for (String key : material.keySet()) {
                if (!key.equals("item") && !key.equals("tag") && !key.equals("count")) {
                    throw new IllegalArgumentException("Unknown material key: " + key);
                }
            }
            if (material.has("item") == material.has("tag")) {
                throw new IllegalArgumentException("Material requires exactly one item or tag");
            }
            boolean tag = material.has("tag");
            JsonElement idElement = material.get(tag ? "tag" : "item");
            if (!idElement.isJsonPrimitive() || !idElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Material id must be a string");
            }
            ResourceLocation id = ResourceLocation.tryParse(idElement.getAsString());
            if (id == null) throw new IllegalArgumentException("Invalid material id");
            if (!tag && (BuiltInRegistries.ITEM.getOptional(id).isEmpty()
                    || BuiltInRegistries.ITEM.get(id) == Items.AIR)) {
                throw new IllegalArgumentException("Unknown material item: " + id);
            }
            JsonElement count = material.get("count");
            if (count == null || !count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Material count must be an integer");
            }
            int amount;
            try {
                amount = count.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Material count must be an integer", exception);
            }
            ingredients.add(new Ingredient(id, tag, amount));
        }
        return new BuilderConstructionCost(mode, ingredients);
    }

    public enum Mode { EXACT, SIMPLIFIED }

    public record Ingredient(ResourceLocation id, boolean tag, int count) {
        public Ingredient {
            if (id == null || count < 1 || count > MAX_COUNT) {
                throw new IllegalArgumentException("Material count must be between 1 and " + MAX_COUNT);
            }
        }

        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && (tag
                    ? stack.is(TagKey.create(Registries.ITEM, id))
                    : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id));
        }

        public String displayName() {
            return tag ? "#" + id : new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
        }
    }
}
