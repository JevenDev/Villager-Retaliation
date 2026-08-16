package com.jvn.villagerretaliation.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.item.ItemStackPredicate;
import com.jvn.villagerretaliation.util.item.ItemStackPredicateParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record VillagerInventoryItemRemoval(
        List<ItemSelector> selectors,
        int count,
        ItemStackPredicate stackPredicate) {
    private static final VillagerInventoryItemRemoval EMPTY =
            new VillagerInventoryItemRemoval(List.of(), 0, ItemStackPredicate.ANY);

    public VillagerInventoryItemRemoval(List<ItemSelector> selectors, int count) {
        this(selectors, count, ItemStackPredicate.ANY);
    }

    public VillagerInventoryItemRemoval {
        selectors = selectors == null ? List.of() : List.copyOf(selectors);
        stackPredicate = stackPredicate == null ? ItemStackPredicate.ANY : stackPredicate;
    }

    public static VillagerInventoryItemRemoval empty() {
        return EMPTY;
    }

    public static Optional<VillagerInventoryItemRemoval> read(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }

        JsonObject object = element.getAsJsonObject();
        int count = Math.max(0, readInt(object, "count", readInt(object, "amount", 0)));
        List<ItemSelector> selectors = readItemSelectors(object);
        if (count <= 0 || selectors.isEmpty()) {
            return Optional.empty();
        }
        ItemStackPredicate stackPredicate = ItemStackPredicateParser.parse(
                ItemStackPredicateParser.DEFAULT_REGISTRIES,
                object,
                selectors.stream().map(ItemSelector::item).filter(java.util.Objects::nonNull).toList(),
                "components",
                "durability",
                "custom_data",
                "nbt");
        return Optional.of(new VillagerInventoryItemRemoval(selectors, count, stackPredicate));
    }

    public boolean isEmpty() {
        return this.count <= 0 || this.selectors.isEmpty();
    }

    public boolean canRemove(Player player) {
        return matchingCount(player) >= this.count;
    }

    public int matchingCount(Player player) {
        if (player == null || this.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (ItemStack stack : removableStacks(player)) {
            if (!stack.isEmpty() && matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public boolean remove(Player player) {
        return removeStacks(player).isPresent();
    }

    public Optional<List<ItemStack>> removeStacks(Player player) {
        if (!canRemove(player)) {
            return Optional.empty();
        }

        List<ItemStack> removedStacks = new ArrayList<>();
        int remaining = this.count;
        for (ItemStack stack : removableStacks(player)) {
            if (remaining <= 0) {
                break;
            }
            if (stack.isEmpty() || !matches(stack)) {
                continue;
            }

            int removed = Math.min(remaining, stack.getCount());
            removedStacks.add(stack.copyWithCount(removed));
            stack.shrink(removed);
            remaining -= removed;
        }
        player.getInventory().setChanged();
        return Optional.of(List.copyOf(removedStacks));
    }

    public List<ItemStack> previewRemovedStacks(Player player) {
        if (!canRemove(player)) {
            return List.of();
        }

        List<ItemStack> removedStacks = new ArrayList<>();
        int remaining = this.count;
        for (ItemStack stack : removableStacks(player)) {
            if (remaining <= 0) {
                break;
            }
            if (stack.isEmpty() || !matches(stack)) {
                continue;
            }

            int removed = Math.min(remaining, stack.getCount());
            removedStacks.add(stack.copyWithCount(removed));
            remaining -= removed;
        }
        return List.copyOf(removedStacks);
    }

    public Map<String, String> replacements() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("payment_count", Integer.toString(this.count));
        replacements.put("payment_items", describeItems());
        return replacements;
    }

    private boolean matches(ItemStack stack) {
        return this.stackPredicate.matches(stack)
                && this.selectors.stream().anyMatch(selector -> selector.matches(stack));
    }

    private String describeItems() {
        return this.selectors.stream()
                .map(ItemSelector::description)
                .reduce((left, right) -> left + " or " + right)
                .orElse("items");
    }

    private static Iterable<ItemStack> removableStacks(Player player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    private static List<ItemSelector> readItemSelectors(JsonObject entry) {
        List<ItemSelector> selectors = new ArrayList<>();
        for (String value : readStringList(entry, "item")) {
            parseItemSelector(value).ifPresent(selectors::add);
        }
        for (String value : readStringList(entry, "items")) {
            parseItemSelector(value).ifPresent(selectors::add);
        }
        for (String value : readStringList(entry, "tag")) {
            parseTagSelector(value).ifPresent(selectors::add);
        }
        for (String value : readStringList(entry, "tags")) {
            parseTagSelector(value).ifPresent(selectors::add);
        }
        return selectors;
    }

    private static Optional<ItemSelector> parseItemSelector(String value) {
        if (value.startsWith("#")) {
            return parseTagSelector(value.substring(1));
        }
        return readItem(value).map(ItemSelector::item);
    }

    private static Optional<ItemSelector> parseTagSelector(String value) {
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        return parseResourceLocation(normalized)
                .map(location -> ItemSelector.tag(TagKey.create(Registries.ITEM, location), location));
    }

    private static Optional<Item> readItem(String value) {
        return parseResourceLocation(value)
                .flatMap(location -> BuiltInRegistries.ITEM.getOptional(location))
                .filter(item -> item != Items.AIR);
    }

    private static Optional<ResourceLocation> parseResourceLocation(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.contains(":") ? value.trim() : "minecraft:" + value.trim();
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return fallback;
        }
    }

    private static List<String> readStringList(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (!element.isJsonArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (JsonElement child : array) {
            if (!child.isJsonPrimitive()) {
                continue;
            }
            String value = child.getAsString().trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    public record ItemSelector(
            Item item,
            TagKey<Item> tag,
            ResourceLocation id) {
        private static ItemSelector item(Item item) {
            return new ItemSelector(item, null, BuiltInRegistries.ITEM.getKey(item));
        }

        private static ItemSelector tag(TagKey<Item> tag, ResourceLocation id) {
            return new ItemSelector(null, tag, id);
        }

        private boolean matches(ItemStack stack) {
            if (this.item != null) {
                return stack.is(this.item);
            }
            return this.tag != null && stack.is(this.tag);
        }

        private String description() {
            return this.tag == null ? this.id.toString() : "#" + this.id;
        }
    }
}
