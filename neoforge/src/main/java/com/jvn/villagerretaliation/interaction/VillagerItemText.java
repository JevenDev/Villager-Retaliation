package com.jvn.villagerretaliation.interaction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

public final class VillagerItemText {
    private static final String RESOURCE_ROOT = "item_text/";
    private static final ItemTextDefinition DEFAULT_DEFINITION = defaultDefinition();
    private static volatile CachedDefinitions cachedDefinitions = CachedDefinitions.empty();

    private VillagerItemText() {
    }

    public static void warm(MinecraftServer server) {
        load(server, VillagerLocale.DEFAULT_LOCALE);
    }

    public static void clearCache() {
        cachedDefinitions = CachedDefinitions.empty();
    }

    public static String dialogueName(MinecraftServer server, ItemStack stack) {
        return dialogueName(server, VillagerLocale.DEFAULT_LOCALE, stack);
    }

    public static String dialogueName(MinecraftServer server, String locale, ItemStack stack) {
        return itemName(server, locale, 1, stack);
    }

    public static String stackName(MinecraftServer server, ItemStack stack) {
        return stackName(server, VillagerLocale.DEFAULT_LOCALE, stack);
    }

    public static String stackName(MinecraftServer server, String locale, ItemStack stack) {
        if (stack.isEmpty()) {
            return "item";
        }
        ItemTextDefinition definition = load(server, locale);
        int count = stack.getCount();
        CountForm form = definition.form(count);
        String itemName = itemName(server, locale, count, stack);
        return form.format()
                .replace("{count}", Integer.toString(count))
                .replace("{item}", itemName);
    }

    public static String countedName(int count, String singularName) {
        return DEFAULT_DEFINITION.format(count, singularName, null);
    }

    public static String countedName(MinecraftServer server, String locale, int count, String singularName) {
        return load(server, locale).format(count, singularName, null);
    }

    public static String countedName(
            MinecraftServer server,
            String locale,
            int count,
            ResourceLocation itemId,
            String singularName) {
        return load(server, locale).format(count, singularName, itemId);
    }

    private static String itemName(MinecraftServer server, String locale, int count, ItemStack stack) {
        if (stack.isEmpty()) {
            return "item";
        }
        ItemTextDefinition definition = load(server, locale);
        CountForm form = definition.form(count);
        if (VillagerCurrencyResources.isCurrency(server, stack)) {
            String localizedCurrency = definition.currencyNames().get(form.id());
            if (localizedCurrency == null || localizedCurrency.isBlank()) {
                localizedCurrency = VillagerCurrencyResources.nameForCount(server, count);
            }
            return localizedCurrency;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return definition.itemName(form, itemId, stack.getHoverName().getString());
    }

    private static ItemTextDefinition load(MinecraftServer server, String locale) {
        if (server == null) {
            return DEFAULT_DEFINITION;
        }
        String normalizedLocale = VillagerLocale.normalize(locale);
        CachedDefinitions current = cachedDefinitions;
        if (current.server() == server && current.byLocale().containsKey(normalizedLocale)) {
            return current.byLocale().get(normalizedLocale);
        }
        synchronized (VillagerItemText.class) {
            current = cachedDefinitions;
            Map<String, ItemTextDefinition> definitions = current.server() == server
                    ? new HashMap<>(current.byLocale())
                    : new HashMap<>();
            ItemTextDefinition cached = definitions.get(normalizedLocale);
            if (cached != null) {
                return cached;
            }
            ItemTextDefinition loaded = read(server, normalizedLocale);
            definitions.put(normalizedLocale, loaded);
            cachedDefinitions = new CachedDefinitions(server, Map.copyOf(definitions));
            return loaded;
        }
    }

    private static ItemTextDefinition read(MinecraftServer server, String locale) {
        ItemTextDefinition definition = readLocale(server, VillagerLocale.DEFAULT_LOCALE, DEFAULT_DEFINITION);
        if (!VillagerLocale.DEFAULT_LOCALE.equals(locale)) {
            definition = readLocale(server, locale, definition);
        }
        return definition;
    }

    private static ItemTextDefinition readLocale(
            MinecraftServer server,
            String locale,
            ItemTextDefinition fallback) {
        ItemTextDefinition[] selected = new ItemTextDefinition[]{fallback};
        DatapackResourceLoader.forEachJsonResource(
                server,
                RESOURCE_ROOT + locale,
                (location, resource) -> DatapackResourceLoader.readObject(location, "item text", resource)
                        .ifPresent(root -> selected[0] = parseDefinition(root, selected[0])));
        return selected[0];
    }

    private static ItemTextDefinition parseDefinition(JsonObject root, ItemTextDefinition fallback) {
        List<CountForm> forms = root.has("forms") && root.get("forms").isJsonArray()
                ? readForms(root)
                : fallback.forms();
        if (forms.isEmpty()) {
            forms = fallback.forms();
        }

        Map<String, String> currencyNames = new LinkedHashMap<>(fallback.currencyNames());
        readFormsObject(DatapackJsonReader.readObject(root, "currency")).forEach(currencyNames::put);

        Map<ResourceLocation, Map<String, String>> itemNames = new LinkedHashMap<>(fallback.itemNames());
        JsonObject items = DatapackJsonReader.readObject(root, "items");
        if (items != null) {
            for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
                ResourceLocation itemId = ResourceLocation.tryParse(entry.getKey());
                if (itemId != null && entry.getValue().isJsonObject()) {
                    Map<String, String> names = new LinkedHashMap<>(itemNames.getOrDefault(itemId, Map.of()));
                    readFormsObject(entry.getValue().getAsJsonObject()).forEach(names::put);
                    itemNames.put(itemId, Map.copyOf(names));
                }
            }
        }

        List<InflectionRule> rules = root.has("rules") && root.get("rules").isJsonArray()
                ? readRules(root)
                : fallback.rules();
        return new ItemTextDefinition(
                List.copyOf(forms),
                Map.copyOf(currencyNames),
                Map.copyOf(itemNames),
                List.copyOf(rules));
    }

    private static List<CountForm> readForms(JsonObject root) {
        List<CountForm> forms = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("forms")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String id = DatapackJsonReader.readString(entry, "id");
            String format = DatapackJsonReader.readString(entry, "format");
            String countPattern = DatapackJsonReader.readString(entry, "count_pattern");
            if (id.isBlank() || format.isBlank()) {
                continue;
            }
            Pattern pattern = compile(countPattern);
            if (countPattern.isBlank() || pattern != null) {
                forms.add(new CountForm(id, pattern, format));
            }
        }
        return forms;
    }

    private static List<InflectionRule> readRules(JsonObject root) {
        List<InflectionRule> rules = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("rules")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            Pattern pattern = compile(DatapackJsonReader.readString(entry, "pattern"));
            String replacement = DatapackJsonReader.readString(entry, "replacement");
            if (pattern != null && !replacement.isBlank()) {
                rules.add(new InflectionRule(
                        Set.copyOf(DatapackJsonReader.readStringList(entry, "forms")),
                        pattern,
                        replacement));
            }
        }
        return rules;
    }

    private static Map<String, String> readFormsObject(JsonObject object) {
        if (object == null) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                String value = entry.getValue().getAsString().trim();
                if (!value.isBlank()) {
                    values.put(entry.getKey(), value);
                }
            }
        }
        return values;
    }

    private static Pattern compile(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(value);
        } catch (PatternSyntaxException ignored) {
            return null;
        }
    }

    private static ItemTextDefinition defaultDefinition() {
        List<CountForm> forms = List.of(
                new CountForm("one", Pattern.compile("1"), "{item}"),
                new CountForm("other", null, "{count} {item}"));
        Map<ResourceLocation, Map<String, String>> items = new LinkedHashMap<>();
        for (String id : List.of(
                "minecraft:bread", "minecraft:charcoal", "minecraft:clay_ball", "minecraft:coal",
                "minecraft:cod", "minecraft:flint", "minecraft:glass", "minecraft:gunpowder",
                "minecraft:leather", "minecraft:mutton", "minecraft:paper", "minecraft:salmon",
                "minecraft:string", "minecraft:wheat")) {
            ResourceLocation itemId = ResourceLocation.parse(id);
            items.put(itemId, Map.of("other", BuiltInRegistries.ITEM.get(itemId).getDescription().getString()));
        }
        List<InflectionRule> rules = List.of(
                rule("(?i)^(.+?)(?= of )", "$1s"),
                rule("(?i)(.*(?<!s)s)$", "$1"),
                rule("(?i)(.*(?:ch|sh|ss|x|z))$", "$1es"),
                rule("(?i)(.*[^aeiou])y$", "$1ies"),
                rule("(?i)(.*)$", "$1s"));
        return new ItemTextDefinition(forms, Map.of(), Map.copyOf(items), rules);
    }

    private static InflectionRule rule(String pattern, String replacement) {
        return new InflectionRule(Set.of("other"), Pattern.compile(pattern), replacement);
    }

    private record CountForm(String id, Pattern countPattern, String format) {
        private boolean matches(int count) {
            return this.countPattern != null && this.countPattern.matcher(Integer.toString(count)).matches();
        }
    }

    private record InflectionRule(Set<String> forms, Pattern pattern, String replacement) {
        private boolean applies(String form) {
            return this.forms.isEmpty() || this.forms.contains(form);
        }
    }

    private record ItemTextDefinition(
            List<CountForm> forms,
            Map<String, String> currencyNames,
            Map<ResourceLocation, Map<String, String>> itemNames,
            List<InflectionRule> rules) {
        private CountForm form(int count) {
            return this.forms.stream().filter(candidate -> candidate.matches(count)).findFirst().orElse(this.forms.getLast());
        }

        private String format(int count, String singularName, ResourceLocation itemId) {
            CountForm form = form(count);
            String itemName = itemName(form, itemId, singularName);
            return form.format()
                    .replace("{count}", Integer.toString(count))
                    .replace("{item}", itemName);
        }

        private String itemName(CountForm form, ResourceLocation itemId, String fallbackName) {
            String safeFallback = fallbackName == null || fallbackName.isBlank() ? "item" : fallbackName;
            Map<String, String> localizedNames = itemId == null ? Map.of() : this.itemNames.getOrDefault(itemId, Map.of());
            String localized = localizedNames.get(form.id());
            if (localized == null || localized.isBlank()) {
                localized = localizedNames.get("other");
            }
            if (localized != null && !localized.isBlank()) {
                return localized;
            }
            if ("one".equals(form.id())) {
                return safeFallback;
            }
            for (InflectionRule rule : this.rules) {
                if (rule.applies(form.id()) && rule.pattern().matcher(safeFallback).find()) {
                    return rule.pattern().matcher(safeFallback).replaceFirst(rule.replacement());
                }
            }
            return safeFallback;
        }
    }

    private record CachedDefinitions(MinecraftServer server, Map<String, ItemTextDefinition> byLocale) {
        private static CachedDefinitions empty() {
            return new CachedDefinitions(null, Map.of());
        }
    }
}
