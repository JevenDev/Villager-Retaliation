package com.jvn.villagerretaliation.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record VillagerPlayerItemCondition(
        List<ItemSelector> selectors,
        Set<ItemSlot> slots) {
    private static final VillagerPlayerItemCondition EMPTY = new VillagerPlayerItemCondition(List.of(), Set.of());

    public static VillagerPlayerItemCondition empty() {
        return EMPTY;
    }

    public static VillagerPlayerItemCondition read(JsonObject entry) {
        List<ItemSelector> selectors = readItemSelectors(entry);
        if (selectors.isEmpty()) {
            return empty();
        }

        Set<ItemSlot> slots = readSlots(entry);
        if (slots.isEmpty()) {
            slots = EnumSet.of(ItemSlot.HANDS);
        }
        return new VillagerPlayerItemCondition(List.copyOf(selectors), Set.copyOf(slots));
    }

    public boolean isEmpty() {
        return this.selectors.isEmpty();
    }

    public boolean matches(Player player) {
        return this.isEmpty() || matchingStack(player).isPresent();
    }

    public Map<String, String> replacements(Player player) {
        return matchingStack(player)
                .map(match -> {
                    ItemStack stack = match.stack();
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("player_item", stack.getHoverName().getString());
                    replacements.put("held_item", stack.getHoverName().getString());
                    replacements.put("player_item_id", itemId);
                    replacements.put("held_item_id", itemId);
                    replacements.put("player_item_slot", match.slot().id());
                    replacements.put("held_item_slot", match.slot().id());
                    return replacements;
                })
                .orElseGet(Map::of);
    }

    private Optional<MatchedItem> matchingStack(Player player) {
        if (player == null || this.selectors.isEmpty()) {
            return Optional.empty();
        }

        for (ItemSlot slot : expandedSlots()) {
            Optional<MatchedItem> match = switch (slot) {
                case MAIN_HAND -> matchingStack(player.getMainHandItem(), ItemSlot.MAIN_HAND);
                case OFF_HAND -> matchingStack(player.getOffhandItem(), ItemSlot.OFF_HAND);
                case ARMOR -> matchingStack(player.getArmorSlots(), ItemSlot.ARMOR);
                case HOTBAR -> matchingHotbarStack(player);
                case INVENTORY -> matchingStack(player.getInventory().items, ItemSlot.INVENTORY);
                case HANDS, EQUIPMENT, ANY -> Optional.empty();
            };
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private Set<ItemSlot> expandedSlots() {
        EnumSet<ItemSlot> expanded = EnumSet.noneOf(ItemSlot.class);
        for (ItemSlot slot : this.slots) {
            switch (slot) {
                case MAIN_HAND -> expanded.add(ItemSlot.MAIN_HAND);
                case OFF_HAND -> expanded.add(ItemSlot.OFF_HAND);
                case HANDS -> {
                    expanded.add(ItemSlot.MAIN_HAND);
                    expanded.add(ItemSlot.OFF_HAND);
                }
                case ARMOR -> expanded.add(ItemSlot.ARMOR);
                case HOTBAR -> expanded.add(ItemSlot.HOTBAR);
                case INVENTORY -> expanded.add(ItemSlot.INVENTORY);
                case EQUIPMENT -> {
                    expanded.add(ItemSlot.MAIN_HAND);
                    expanded.add(ItemSlot.OFF_HAND);
                    expanded.add(ItemSlot.ARMOR);
                }
                case ANY -> {
                    expanded.add(ItemSlot.MAIN_HAND);
                    expanded.add(ItemSlot.OFF_HAND);
                    expanded.add(ItemSlot.ARMOR);
                    expanded.add(ItemSlot.HOTBAR);
                    expanded.add(ItemSlot.INVENTORY);
                }
            }
        }
        return expanded;
    }

    private Optional<MatchedItem> matchingHotbarStack(Player player) {
        List<ItemStack> items = player.getInventory().items;
        int hotbarSize = Math.min(9, items.size());
        for (int index = 0; index < hotbarSize; index++) {
            Optional<MatchedItem> match = matchingStack(items.get(index), ItemSlot.HOTBAR);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private Optional<MatchedItem> matchingStack(Iterable<ItemStack> stacks, ItemSlot slot) {
        for (ItemStack stack : stacks) {
            Optional<MatchedItem> match = matchingStack(stack, slot);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private Optional<MatchedItem> matchingStack(ItemStack stack, ItemSlot slot) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return this.selectors.stream().anyMatch(selector -> selector.matches(stack))
                ? Optional.of(new MatchedItem(stack, slot))
                : Optional.empty();
    }

    private static List<ItemSelector> readItemSelectors(JsonObject entry) {
        List<ItemSelector> selectors = new ArrayList<>();
        for (String value : readStringList(entry, "player_item")) {
            parseItemSelector(value).ifPresent(selectors::add);
        }
        for (String value : readStringList(entry, "player_items")) {
            parseItemSelector(value).ifPresent(selectors::add);
        }
        for (String value : readStringList(entry, "player_item_tag")) {
            parseTagSelector(value).ifPresent(selectors::add);
        }
        for (String value : readStringList(entry, "player_item_tags")) {
            parseTagSelector(value).ifPresent(selectors::add);
        }
        return selectors;
    }

    private static Set<ItemSlot> readSlots(JsonObject entry) {
        EnumSet<ItemSlot> slots = EnumSet.noneOf(ItemSlot.class);
        for (String value : readStringList(entry, "player_item_slot")) {
            parseSlot(value).ifPresent(slots::add);
        }
        for (String value : readStringList(entry, "player_item_slots")) {
            parseSlot(value).ifPresent(slots::add);
        }
        return slots;
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
                .map(location -> ItemSelector.tag(TagKey.create(Registries.ITEM, location)));
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
        String normalized = value.contains(":") ? value : "minecraft:" + value;
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }

    private static Optional<ItemSlot> parseSlot(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_');
        if ("MAINHAND".equals(normalized)) {
            normalized = "MAIN_HAND";
        } else if ("OFFHAND".equals(normalized)) {
            normalized = "OFF_HAND";
        }
        try {
            return Optional.of(ItemSlot.valueOf(normalized));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
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
        for (JsonElement child : element.getAsJsonArray()) {
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

    public enum ItemSlot {
        MAIN_HAND("main_hand"),
        OFF_HAND("off_hand"),
        HANDS("hands"),
        ARMOR("armor"),
        HOTBAR("hotbar"),
        INVENTORY("inventory"),
        EQUIPMENT("equipment"),
        ANY("any");

        private final String id;

        ItemSlot(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }
    }

    public record ItemSelector(Item item, TagKey<Item> tag) {
        private static ItemSelector item(Item item) {
            return new ItemSelector(item, null);
        }

        private static ItemSelector tag(TagKey<Item> tag) {
            return new ItemSelector(null, tag);
        }

        private boolean matches(ItemStack stack) {
            if (this.item != null) {
                return stack.is(this.item);
            }
            return this.tag != null && stack.is(this.tag);
        }
    }

    private record MatchedItem(ItemStack stack, ItemSlot slot) {
    }
}
