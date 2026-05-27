package com.jvn.villagerretaliation.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class DatapackDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RECENT_DIAGNOSTICS = 80;
    private static final ArrayDeque<Entry> RECENT = new ArrayDeque<>();

    private DatapackDiagnostics() {
    }

    public static List<Entry> recent() {
        synchronized (RECENT) {
            return List.copyOf(RECENT);
        }
    }

    public static void clear() {
        synchronized (RECENT) {
            RECENT.clear();
        }
    }

    public static void warnMisplacedRootKeys(
            ResourceLocation location,
            String systemName,
            JsonObject root,
            Map<String, String> expectedPaths) {
        for (Map.Entry<String, String> entry : expectedPaths.entrySet()) {
            if (root.has(entry.getKey())) {
                warn(
                        "Villager Retaliation datapack {} is loaded as {}, but contains top-level \"{}\". Move that content to {}.",
                        location,
                        systemName,
                        entry.getKey(),
                        entry.getValue());
            }
        }
    }

    public static void warnUnknownRootKeys(
            ResourceLocation location,
            String systemName,
            JsonObject root,
            Set<String> allowedKeys) {
        warnUnknownKeys(location, systemName, "file root", root, allowedKeys);
    }

    public static void warnUnknownKeys(
            ResourceLocation location,
            String systemName,
            String context,
            JsonObject entry,
            Set<String> allowedKeys) {
        for (String key : entry.keySet()) {
            if (!allowedKeys.contains(key)) {
                warn(
                        "Villager Retaliation datapack {} {} contains unsupported {} field \"{}\"; it will be ignored.",
                        location,
                        context,
                        systemName,
                        key);
            }
        }
    }

    public static void warnDeprecatedKeys(
            ResourceLocation location,
            String systemName,
            String context,
            JsonObject entry,
            Set<String> deprecatedKeys,
            String removalVersion,
            String replacement) {
        for (String key : entry.keySet()) {
            if (deprecatedKeys.contains(key)) {
                warn(
                        "Villager Retaliation datapack {} {} uses deprecated {} field \"{}\"; it will be removed in {}. {}",
                        location,
                        context,
                        systemName,
                        key,
                        removalVersion,
                        replacement);
            }
        }
    }

    public static void warnInvalidTrigger(
            ResourceLocation location,
            String systemName,
            String context,
            String trigger,
            String expected) {
        if (trigger == null || trigger.isBlank()) {
            return;
        }
        warn(
                "Villager Retaliation datapack {} {} uses trigger \"{}\", which is not a valid {} trigger. {}",
                location,
                context,
                trigger,
                systemName,
                expected);
    }

    public static void warnSkippedFile(
            ResourceLocation location,
            String systemName,
            Exception exception) {
        warn(
                "Villager Retaliation datapack {} could not load {} data and will be skipped: {}",
                location,
                systemName,
                exception.getMessage());
    }

    public static void warnDuplicateId(
            ResourceLocation location,
            String systemName,
            String id,
            ResourceLocation previousLocation) {
        warn(
                "Villager Retaliation datapack {} replaces {} id \"{}\" that was already loaded from {}.",
                location,
                systemName,
                id,
                previousLocation);
    }

    public static void warnUnknownProfession(
            ResourceLocation location,
            String context,
            String profession) {
        if (profession == null || profession.isBlank()) {
            return;
        }
        String hint = profession.contains(":")
                ? "Confirm that a loaded mod registers this profession id."
                : "Vanilla professions may omit minecraft:, but modded professions need their full id such as modid:" + profession + ".";
        warn(
                "Villager Retaliation datapack {} {} references unknown profession \"{}\". {}",
                location,
                context,
                profession,
                hint);
    }

    public static void warnInertPlayerItemSlots(
            ResourceLocation location,
            String context,
            JsonObject entry) {
        if (!hasAny(entry, "player_item_slot", "player_item_slots")) {
            return;
        }
        if (hasAny(entry,
                "player_item",
                "player_items",
                "player_item_tag",
                "player_item_tags",
                "min_player_item_durability",
                "max_player_item_durability",
                "min_player_item_durability_percent",
                "max_player_item_durability_percent",
                "min_held_item_durability",
                "max_held_item_durability",
                "min_held_item_durability_percent",
                "max_held_item_durability_percent",
                "player_item_enchantment",
                "player_item_enchantments",
                "held_item_enchantment",
                "held_item_enchantments",
                "min_player_item_enchantment_level",
                "max_player_item_enchantment_level",
                "min_held_item_enchantment_level",
                "max_held_item_enchantment_level")) {
            return;
        }
        warn(
                "Villager Retaliation datapack {} {} sets player_item_slots without an item, tag, durability, or enchantment filter; the slot filter will not match anything by itself.",
                location,
                context);
    }

    private static void warn(String template, Object... args) {
        LOGGER.warn(template, args);
        remember(format(template, args));
    }

    private static void remember(String message) {
        synchronized (RECENT) {
            RECENT.addLast(new Entry(message));
            while (RECENT.size() > MAX_RECENT_DIAGNOSTICS) {
                RECENT.removeFirst();
            }
        }
    }

    private static String format(String template, Object... args) {
        StringBuilder builder = new StringBuilder(template.length() + args.length * 16);
        int cursor = 0;
        int argIndex = 0;
        int placeholder = template.indexOf("{}");
        while (placeholder >= 0) {
            builder.append(template, cursor, placeholder);
            builder.append(argIndex < args.length ? String.valueOf(args[argIndex++]) : "{}");
            cursor = placeholder + 2;
            placeholder = template.indexOf("{}", cursor);
        }
        builder.append(template, cursor, template.length());
        if (argIndex < args.length) {
            List<String> extras = new ArrayList<>();
            while (argIndex < args.length) {
                extras.add(String.valueOf(args[argIndex++]));
            }
            builder.append(" ").append(extras);
        }
        return builder.toString();
    }

    private static boolean hasAny(JsonObject entry, String... keys) {
        for (String key : keys) {
            JsonElement element = entry.get(key);
            if (element != null && !element.isJsonNull()) {
                return true;
            }
        }
        return false;
    }

    public record Entry(String message) {
    }
}
