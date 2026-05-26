package com.jvn.villagerretaliation.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class DatapackDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DatapackDiagnostics() {
    }

    public static void warnMisplacedRootKeys(
            ResourceLocation location,
            String systemName,
            JsonObject root,
            Map<String, String> expectedPaths) {
        for (Map.Entry<String, String> entry : expectedPaths.entrySet()) {
            if (root.has(entry.getKey())) {
                LOGGER.warn(
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
                LOGGER.warn(
                        "Villager Retaliation datapack {} {} contains unsupported {} field \"{}\"; it will be ignored.",
                        location,
                        context,
                        systemName,
                        key);
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
        LOGGER.warn(
                "Villager Retaliation datapack {} {} uses trigger \"{}\", which is not a valid {} trigger. {}",
                location,
                context,
                trigger,
                systemName,
                expected);
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
        LOGGER.warn(
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
        LOGGER.warn(
                "Villager Retaliation datapack {} {} sets player_item_slots without an item, tag, durability, or enchantment filter; the slot filter will not match anything by itself.",
                location,
                context);
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
}
