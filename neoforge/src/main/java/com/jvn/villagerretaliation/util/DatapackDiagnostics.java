package com.jvn.villagerretaliation.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.QuestDiagnostic;
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

    public static List<QuestDiagnostic> structuredRecent() {
        synchronized (RECENT) {
            return RECENT.stream()
                    .map(Entry::diagnostic)
                    .toList();
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
                warnStructured(
                        "datapack.misplaced_root_key",
                        location,
                        pointerForKey("file root", entry.getKey()),
                        "Move that content to " + entry.getValue() + ".",
                        Set.of(entry.getKey()),
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
                warnStructured(
                        "datapack.unsupported_field",
                        location,
                        pointerForKey(context, key),
                        "Remove the field or move it to a supported location.",
                        Set.of(key),
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
                warnStructured(
                        "datapack.deprecated_field",
                        location,
                        pointerForKey(context, key),
                        replacement,
                        Set.of(key),
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

    public static void warnInvalidDialogueCondition(ResourceLocation location, String context, String message) {
        warnInvalidDialogueCondition(location, context, contextToJsonPointer(context), message);
    }

    public static void warnInvalidDialogueCondition(
            ResourceLocation location,
            String context,
            String jsonPointer,
            String message) {
        warnStructured(
                "datapack.invalid_dialogue_condition",
                location,
                jsonPointer,
                "Check the supported condition, action, or quest field shape for this location.",
                Set.of(),
                "Villager Retaliation datapack {} {} has invalid dialogue condition: {}",
                location,
                context,
                message);
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
        warnStructured(
                "datapack.invalid_trigger",
                location,
                contextToJsonPointer(context),
                expected,
                Set.of(trigger),
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
        warnStructured(
                "datapack.skipped_file",
                location,
                "",
                "Fix the JSON syntax or root schema for this file.",
                Set.of(),
                "Villager Retaliation datapack {} could not load {} data and will be skipped: {}",
                location,
                systemName,
                exception.getMessage());
    }

    public static void warnSkippedEntry(
            ResourceLocation location,
            String systemName,
            String context,
            String reason) {
        warnStructured(
                "datapack.skipped_entry",
                location,
                contextToJsonPointer(context),
                reason,
                Set.of(),
                "Villager Retaliation datapack {} {} {} will be skipped: {}",
                location,
                systemName,
                context,
                reason);
    }

    public static void warnInvalidResourceLocation(
            ResourceLocation location,
            String systemName,
            String context,
            String value,
            String expected) {
        warnStructured(
                "datapack.invalid_resource_location",
                location,
                contextToJsonPointer(context),
                expected,
                Set.of(value),
                "Villager Retaliation datapack {} {} references invalid {} id \"{}\". {}",
                location,
                context,
                systemName,
                value,
                expected);
    }

    public static void warnDuplicateId(
            ResourceLocation location,
            String systemName,
            String id,
            ResourceLocation previousLocation) {
        warnStructured(
                "datapack.duplicate_id",
                location,
                "",
                "Use a unique id or intentionally replace the earlier definition.",
                Set.of(id, previousLocation == null ? "" : previousLocation.toString()),
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
        warnStructured(
                "datapack.unknown_profession",
                location,
                contextToJsonPointer(context),
                hint,
                Set.of(profession),
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
        warnStructured(
                "datapack.inert_player_item_slots",
                location,
                contextToJsonPointer(context),
                "Add an item, tag, durability, or enchantment filter, or remove player_item_slots.",
                Set.of("player_item_slots"),
                "Villager Retaliation datapack {} {} sets player_item_slots without an item, tag, durability, or enchantment filter; the slot filter will not match anything by itself.",
                location,
                context);
    }

    private static void warnStructured(
            String code,
            ResourceLocation location,
            String jsonPointer,
            String suggestedFix,
            Set<String> relevantIds,
            String template,
            Object... args) {
        LOGGER.warn(template, args);
        String message = format(template, args);
        remember(new Entry(message, QuestDiagnostic.warning(
                code,
                location,
                jsonPointer,
                message,
                suggestedFix,
                relevantIds)));
    }

    private static void remember(Entry entry) {
        synchronized (RECENT) {
            RECENT.addLast(entry);
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

    private static String pointerForKey(String context, String key) {
        String pointer = contextToJsonPointer(context);
        String escaped = escapeJsonPointer(key);
        return pointer.isBlank() ? "/" + escaped : pointer + "/" + escaped;
    }

    private static String contextToJsonPointer(String context) {
        if (context == null || context.isBlank() || "file root".equals(context.trim())) {
            return "";
        }
        String pointer = context.trim()
                .replace('\\', '/')
                .replace('"', ' ')
                .replace('.', '/')
                .replace('[', '/')
                .replace("]", "")
                .replaceAll("\\s+", "/")
                .replaceAll("/+", "/")
                .replaceAll("^/|/$", "");
        if (pointer.isBlank()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (String segment : pointer.split("/")) {
            if (!segment.isBlank()) {
                escaped.append('/').append(escapeJsonPointer(segment));
            }
        }
        return escaped.toString();
    }

    private static String escapeJsonPointer(String value) {
        return value == null ? "" : value.replace("~", "~0").replace("/", "~1");
    }

    public record Entry(String message, QuestDiagnostic diagnostic) {
        public Entry(String message) {
            this(message, QuestDiagnostic.warning("datapack.diagnostic", null, "", message, "", Set.of()));
        }

        public Entry {
            message = message == null ? "" : message;
            diagnostic = diagnostic == null
                    ? QuestDiagnostic.warning("datapack.diagnostic", null, "", message, "", Set.of())
                    : diagnostic;
        }
    }
}
