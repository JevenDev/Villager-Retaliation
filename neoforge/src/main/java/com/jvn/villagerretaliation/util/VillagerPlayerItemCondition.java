package com.jvn.villagerretaliation.util;

import com.jvn.villagerretaliation.compat.AccessoryInventoryCompat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public record VillagerPlayerItemCondition(
        List<ItemSelector> selectors,
        Set<ItemSlot> slots,
        OptionalInt minDurability,
        OptionalInt maxDurability,
        OptionalInt minDurabilityPercent,
        OptionalInt maxDurabilityPercent,
        List<EnchantmentSelector> enchantments) {
    private static final VillagerPlayerItemCondition EMPTY = new VillagerPlayerItemCondition(
            List.of(),
            Set.of(),
            OptionalInt.empty(),
            OptionalInt.empty(),
            OptionalInt.empty(),
            OptionalInt.empty(),
            List.of());

    public static VillagerPlayerItemCondition empty() {
        return EMPTY;
    }

    public static VillagerPlayerItemCondition read(JsonObject entry) {
        List<ItemSelector> selectors = readItemSelectors(entry);
        OptionalInt minDurability = firstPresent(
                readOptionalInt(entry, "min_player_item_durability"),
                readOptionalInt(entry, "min_held_item_durability"));
        OptionalInt maxDurability = firstPresent(
                readOptionalInt(entry, "max_player_item_durability"),
                readOptionalInt(entry, "max_held_item_durability"));
        OptionalInt minDurabilityPercent = firstPresent(
                readOptionalInt(entry, "min_player_item_durability_percent"),
                readOptionalInt(entry, "min_held_item_durability_percent"));
        OptionalInt maxDurabilityPercent = firstPresent(
                readOptionalInt(entry, "max_player_item_durability_percent"),
                readOptionalInt(entry, "max_held_item_durability_percent"));
        OptionalInt minEnchantmentLevel = firstPresent(
                readOptionalInt(entry, "min_player_item_enchantment_level"),
                readOptionalInt(entry, "min_held_item_enchantment_level"));
        OptionalInt maxEnchantmentLevel = firstPresent(
                readOptionalInt(entry, "max_player_item_enchantment_level"),
                readOptionalInt(entry, "max_held_item_enchantment_level"));
        List<EnchantmentSelector> enchantments = readEnchantmentSelectors(entry, minEnchantmentLevel, maxEnchantmentLevel);
        if (selectors.isEmpty()
                && minDurability.isEmpty()
                && maxDurability.isEmpty()
                && minDurabilityPercent.isEmpty()
                && maxDurabilityPercent.isEmpty()
                && enchantments.isEmpty()) {
            return empty();
        }

        Set<ItemSlot> slots = readSlots(entry);
        if (slots.isEmpty()) {
            slots = EnumSet.of(ItemSlot.HANDS);
        }
        return new VillagerPlayerItemCondition(
                List.copyOf(selectors),
                Set.copyOf(slots),
                minDurability,
                maxDurability,
                minDurabilityPercent,
                maxDurabilityPercent,
                List.copyOf(enchantments));
    }

    public boolean isEmpty() {
        return this.selectors.isEmpty()
                && this.minDurability.isEmpty()
                && this.maxDurability.isEmpty()
                && this.minDurabilityPercent.isEmpty()
                && this.maxDurabilityPercent.isEmpty()
                && this.enchantments.isEmpty();
    }

    public boolean matches(Player player) {
        return this.isEmpty() || matchingStack(player).isPresent();
    }

    public Map<String, String> replacements(Player player) {
        return matchingStack(player)
                .map(match -> {
                    ItemStack stack = match.stack();
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    DurabilityInfo durability = DurabilityInfo.of(stack);
                    EnchantmentInfo enchantment = match.enchantment()
                            .or(() -> EnchantmentInfo.first(stack))
                            .orElse(EnchantmentInfo.empty());
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("player_item", stack.getHoverName().getString());
                    replacements.put("held_item", stack.getHoverName().getString());
                    replacements.put("player_item_id", itemId);
                    replacements.put("held_item_id", itemId);
                    replacements.put("player_item_slot", match.slot().id());
                    replacements.put("held_item_slot", match.slot().id());
                    replacements.put("player_item_durability", Integer.toString(durability.remaining()));
                    replacements.put("held_item_durability", Integer.toString(durability.remaining()));
                    replacements.put("player_item_max_durability", Integer.toString(durability.maximum()));
                    replacements.put("held_item_max_durability", Integer.toString(durability.maximum()));
                    replacements.put("player_item_damage", Integer.toString(durability.damage()));
                    replacements.put("held_item_damage", Integer.toString(durability.damage()));
                    replacements.put("player_item_durability_percent", Integer.toString(durability.percent()));
                    replacements.put("held_item_durability_percent", Integer.toString(durability.percent()));
                    replacements.put("player_item_enchantment", enchantment.name());
                    replacements.put("held_item_enchantment", enchantment.name());
                    replacements.put("player_item_enchantment_full", enchantment.fullName());
                    replacements.put("held_item_enchantment_full", enchantment.fullName());
                    replacements.put("player_item_enchantment_id", enchantment.id());
                    replacements.put("held_item_enchantment_id", enchantment.id());
                    replacements.put("player_item_enchantment_level", Integer.toString(enchantment.level()));
                    replacements.put("held_item_enchantment_level", Integer.toString(enchantment.level()));
                    return replacements;
                })
                .orElseGet(Map::of);
    }

    private Optional<MatchedItem> matchingStack(Player player) {
        if (player == null || this.isEmpty()) {
            return Optional.empty();
        }

        for (ItemSlot slot : expandedSlots()) {
            Optional<MatchedItem> match = switch (slot) {
                case MAIN_HAND -> matchingStack(player.getMainHandItem(), ItemSlot.MAIN_HAND);
                case OFF_HAND -> matchingStack(player.getOffhandItem(), ItemSlot.OFF_HAND);
                case ARMOR -> matchingStack(player.getArmorSlots(), ItemSlot.ARMOR);
                case HOTBAR -> matchingHotbarStack(player);
                case INVENTORY -> matchingStack(player.getInventory().items, ItemSlot.INVENTORY);
                case ACCESSORIES -> matchingStack(AccessoryInventoryCompat.equippedStacks(player), ItemSlot.ACCESSORIES);
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
                case ACCESSORIES -> expanded.add(ItemSlot.ACCESSORIES);
                case EQUIPMENT -> {
                    expanded.add(ItemSlot.MAIN_HAND);
                    expanded.add(ItemSlot.OFF_HAND);
                    expanded.add(ItemSlot.ARMOR);
                    expanded.add(ItemSlot.ACCESSORIES);
                }
                case ANY -> {
                    expanded.add(ItemSlot.MAIN_HAND);
                    expanded.add(ItemSlot.OFF_HAND);
                    expanded.add(ItemSlot.ARMOR);
                    expanded.add(ItemSlot.HOTBAR);
                    expanded.add(ItemSlot.INVENTORY);
                    expanded.add(ItemSlot.ACCESSORIES);
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
        Optional<EnchantmentInfo> enchantment = matchingEnchantment(stack);
        return matchesItem(stack) && matchesDurability(stack) && matchesEnchantment(enchantment)
                ? Optional.of(new MatchedItem(stack, slot, enchantment))
                : Optional.empty();
    }

    private boolean matchesItem(ItemStack stack) {
        return this.selectors.isEmpty() || this.selectors.stream().anyMatch(selector -> selector.matches(stack));
    }

    private boolean matchesDurability(ItemStack stack) {
        if (this.minDurability.isEmpty()
                && this.maxDurability.isEmpty()
                && this.minDurabilityPercent.isEmpty()
                && this.maxDurabilityPercent.isEmpty()) {
            return true;
        }
        if (!stack.isDamageableItem()) {
            return false;
        }
        DurabilityInfo durability = DurabilityInfo.of(stack);
        return this.minDurability.stream().allMatch(min -> durability.remaining() >= min)
                && this.maxDurability.stream().allMatch(max -> durability.remaining() <= max)
                && this.minDurabilityPercent.stream().allMatch(min -> durability.percent() >= min)
                && this.maxDurabilityPercent.stream().allMatch(max -> durability.percent() <= max);
    }

    private Optional<EnchantmentInfo> matchingEnchantment(ItemStack stack) {
        if (this.enchantments.isEmpty()) {
            return Optional.empty();
        }
        for (EnchantmentSelector selector : this.enchantments) {
            Optional<EnchantmentInfo> match = selector.match(stack);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private boolean matchesEnchantment(Optional<EnchantmentInfo> match) {
        return this.enchantments.isEmpty() || match.isPresent();
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

    private static List<EnchantmentSelector> readEnchantmentSelectors(
            JsonObject entry,
            OptionalInt minLevel,
            OptionalInt maxLevel) {
        List<EnchantmentSelector> selectors = new ArrayList<>();
        for (String value : readStringList(entry, "player_item_enchantment")) {
            parseResourceLocation(value).map(location -> new EnchantmentSelector(location, minLevel, maxLevel)).ifPresent(selectors::add);
        }
        for (String value : readStringList(entry, "held_item_enchantment")) {
            parseResourceLocation(value).map(location -> new EnchantmentSelector(location, minLevel, maxLevel)).ifPresent(selectors::add);
        }
        readEnchantmentSelectorList(entry.get("player_item_enchantments"), minLevel, maxLevel, selectors);
        readEnchantmentSelectorList(entry.get("held_item_enchantments"), minLevel, maxLevel, selectors);
        return selectors;
    }

    private static void readEnchantmentSelectorList(
            JsonElement element,
            OptionalInt fallbackMinLevel,
            OptionalInt fallbackMaxLevel,
            List<EnchantmentSelector> selectors) {
        if (element == null) {
            return;
        }
        if (element.isJsonPrimitive()) {
            parseResourceLocation(element.getAsString())
                    .map(location -> new EnchantmentSelector(location, fallbackMinLevel, fallbackMaxLevel))
                    .ifPresent(selectors::add);
            return;
        }
        if (element.isJsonObject()) {
            readEnchantmentSelector(element.getAsJsonObject(), fallbackMinLevel, fallbackMaxLevel).ifPresent(selectors::add);
            return;
        }
        if (!element.isJsonArray()) {
            return;
        }
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) {
                parseResourceLocation(child.getAsString())
                        .map(location -> new EnchantmentSelector(location, fallbackMinLevel, fallbackMaxLevel))
                        .ifPresent(selectors::add);
            } else if (child.isJsonObject()) {
                readEnchantmentSelector(child.getAsJsonObject(), fallbackMinLevel, fallbackMaxLevel).ifPresent(selectors::add);
            }
        }
    }

    private static Optional<EnchantmentSelector> readEnchantmentSelector(
            JsonObject entry,
            OptionalInt fallbackMinLevel,
            OptionalInt fallbackMaxLevel) {
        String id = readString(entry, "id");
        if (id.isBlank()) {
            id = readString(entry, "enchantment");
        }
        if (id.isBlank()) {
            id = readString(entry, "name");
        }
        OptionalInt minLevel = firstPresent(readOptionalInt(entry, "min_level"), fallbackMinLevel);
        OptionalInt maxLevel = firstPresent(readOptionalInt(entry, "max_level"), fallbackMaxLevel);
        return parseResourceLocation(id).map(location -> new EnchantmentSelector(location, minLevel, maxLevel));
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

    private static String readString(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
    }

    private static OptionalInt readOptionalInt(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(element.getAsInt());
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return OptionalInt.empty();
        }
    }

    private static OptionalInt firstPresent(OptionalInt first, OptionalInt second) {
        return first.isPresent() ? first : second;
    }

    public enum ItemSlot {
        MAIN_HAND("main_hand"),
        OFF_HAND("off_hand"),
        HANDS("hands"),
        ARMOR("armor"),
        HOTBAR("hotbar"),
        INVENTORY("inventory"),
        ACCESSORIES("accessories"),
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

    public record EnchantmentSelector(ResourceLocation id, OptionalInt minLevel, OptionalInt maxLevel) {
        private Optional<EnchantmentInfo> match(ItemStack stack) {
            return EnchantmentInfo.all(stack).stream()
                    .filter(enchantment -> enchantment.matches(this))
                    .findFirst();
        }
    }

    private record MatchedItem(ItemStack stack, ItemSlot slot, Optional<EnchantmentInfo> enchantment) {
    }

    private record DurabilityInfo(int remaining, int maximum, int damage, int percent) {
        private static DurabilityInfo of(ItemStack stack) {
            if (!stack.isDamageableItem()) {
                return new DurabilityInfo(0, 0, 0, 0);
            }
            int maximum = Math.max(0, stack.getMaxDamage());
            int damage = Math.max(0, stack.getDamageValue());
            int remaining = Math.max(0, maximum - damage);
            int percent = maximum <= 0 ? 0 : Math.round(remaining * 100.0F / maximum);
            return new DurabilityInfo(remaining, maximum, damage, percent);
        }
    }

    private record EnchantmentInfo(String id, String name, String fullName, int level) {
        private static EnchantmentInfo empty() {
            return new EnchantmentInfo("", "", "", 0);
        }

        private static Optional<EnchantmentInfo> first(ItemStack stack) {
            return all(stack).stream().findFirst();
        }

        private static List<EnchantmentInfo> all(ItemStack stack) {
            List<EnchantmentInfo> enchantments = new ArrayList<>();
            addAll(stack.getEnchantments(), enchantments);
            addAll(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY), enchantments);
            return List.copyOf(enchantments);
        }

        private static void addAll(ItemEnchantments source, List<EnchantmentInfo> enchantments) {
            for (var entry : source.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                int level = entry.getIntValue();
                String id = holder.unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("");
                String name = holder.value().description().getString();
                String fullName = Enchantment.getFullname(holder, level).getString();
                enchantments.add(new EnchantmentInfo(id, name, fullName, level));
            }
        }

        private boolean matches(EnchantmentSelector selector) {
            return this.id.equals(selector.id().toString())
                    && selector.minLevel().stream().allMatch(min -> this.level >= min)
                    && selector.maxLevel().stream().allMatch(max -> this.level <= max);
        }
    }
}
