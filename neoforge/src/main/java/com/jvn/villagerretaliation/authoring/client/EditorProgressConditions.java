package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;

final class EditorProgressConditions {
    private EditorProgressConditions() { }

    static String label(String type) {
        return switch (type) {
            case "player_dimension_visited" -> "Player has entered dimensions";
            case "world_dimension_unlocked" -> "World has unlocked dimensions";
            case "world_advancement" -> "World has obtained advancements";
            case "player_killed_entity" -> "Player has killed entities";
            case "player_killed_by_entity" -> "Player has been killed by entities";
            case "player_died_from" -> "Player has died from damage types";
            case "player_last_death" -> "Most recent player death";
            case "player_biome_visited" -> "Player has visited biomes";
            case "player_crafted_item" -> "Player has crafted items";
            case "player_used_item" -> "Player has used items";
            case "player_deaths" -> "Total player deaths";
            case "player_kills" -> "Total player kills (PvP)";
            case "player_stat" -> "Player statistic threshold";
            case "player_structure_visited" -> "Player has visited structures";
            case "player_settlement_visited" -> "Player has visited settlements";
            case "player_destination_count" -> "Distinct destinations visited";
            case "player_item_possessed" -> "Player has ever possessed items";
            case "event_history" -> "Event count and timing";
            case "personal_trade" -> "Trade history with this villager";
            case "settlement_need" -> "Settlement needs and population";
            case "settlement_contribution" -> "Player contributions to this settlement";
            case "advancement", "advancements" -> "Player has obtained advancements";
            default -> type;
        };
    }

    static JsonObject seed(String type) {
        var condition = new JsonObject();
        condition.addProperty("type", type);
        if (type.equals("player_structure_visited") || type.equals("player_item_possessed")) {
            var ids = new JsonArray();
            ids.add(type.equals("player_structure_visited") ? "minecraft:monument" : "minecraft:diamond");
            condition.add(type.equals("player_structure_visited") ? "structures" : "items", ids);
            condition.addProperty("all", true);
            return condition;
        }
        if (type.equals("player_settlement_visited") || type.equals("player_destination_count")) {
            condition.addProperty(type.equals("player_settlement_visited") ? "settlement" : "destination",
                    type.equals("player_settlement_visited") ? "current" : "structures");
            condition.addProperty("min", 1);
            return condition;
        }
        if (type.equals("player_stat")) {
            condition.addProperty("category", "custom");
            condition.addProperty("target", "minecraft:mob_kills");
            condition.addProperty("min", 1);
            return condition;
        }
        if (java.util.Set.of("event_history", "personal_trade", "settlement_need", "settlement_contribution").contains(type)) {
            condition.addProperty("min", 1);
            switch (type) {
                case "event_history" -> {
                    condition.addProperty("event", "villagerretaliation:player_died");
                    condition.addProperty("scope", "player");
                    condition.addProperty("metric", "count");
                }
                case "personal_trade" -> condition.addProperty("metric", "trades");
                case "settlement_need" -> condition.addProperty("need", "food");
                case "settlement_contribution" -> condition.addProperty("metric", "items");
                default -> throw new IllegalStateException("Unknown progress condition");
            }
            return condition;
        }
        if (type.equals("advancement") || type.equals("advancements")) {
            var ids = new JsonArray();
            ids.add("minecraft:story/mine_stone");
            condition.add("advancements", ids);
            condition.addProperty("all", true);
            return condition;
        }
        for (var kind : PlayerHistoryConditions.Kind.values()) {
            if (!kind.id().equals(type)) continue;
            if (kind.count()) condition.addProperty("min", 1);
            else {
                var list = new JsonArray();
                list.add(PlayerHistoryConditions.schema(kind).getAsJsonObject("properties")
                        .getAsJsonObject(kind.field()).getAsJsonObject("items").get("default").deepCopy());
                condition.add(kind.field(), list);
                if (kind != PlayerHistoryConditions.Kind.PLAYER_LAST_DEATH) condition.addProperty("all", true);
            }
            return condition;
        }
        return null;
    }

    static JsonObject deathObjective() {
        var objective = new JsonObject();
        objective.addProperty("type", "criterion");
        objective.addProperty("criterion", "villagerretaliation:player_died");
        objective.addProperty("count", 1);
        return objective;
    }

    static boolean hasMatchMode(JsonObject value) {
        if (!value.has("type") || !value.get("type").isJsonPrimitive()) return false;
        if (value.has("all") && (!value.get("all").isJsonPrimitive() || !value.getAsJsonPrimitive("all").isBoolean())) return false;
        String type = value.get("type").getAsString();
        if (type.equals("advancement") || type.equals("advancements") || type.equals("player_structure_visited") || type.equals("player_item_possessed")) return true;
        for (var kind : PlayerHistoryConditions.Kind.values())
            if (kind.id().equals(type)) return !kind.count() && kind != PlayerHistoryConditions.Kind.PLAYER_LAST_DEATH;
        return false;
    }

    static String resourceField(String path) {
        String[] parts = path.split("/");
        String field = parts[parts.length - 1];
        if (field.matches("[0-9]+") && parts.length > 1) field = parts[parts.length - 2];
        return switch (field) {
            case "structures" -> "structure";
            case "dimensions" -> "dimension";
            case "biomes" -> "biome";
            case "advancements" -> "advancement";
            case "entities" -> "entity";
            case "items" -> "item";
            case "damage_types" -> "damage_type";
            default -> field;
        };
    }

    static List<String> statisticTargets(JsonObject condition) {
        var selected = condition.get("category");
        if (selected != null && (!selected.isJsonPrimitive() || !selected.getAsJsonPrimitive().isString())) return List.of();
        String category = selected == null ? "custom" : selected.getAsString();
        var targets = switch (category) {
            case "mined" -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet();
            case "killed", "killed_by" -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet();
            case "custom" -> net.minecraft.core.registries.BuiltInRegistries.CUSTOM_STAT.keySet();
            default -> net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet();
        };
        return targets.stream().map(Object::toString).sorted().toList();
    }

    static List<String> historyTargets(JsonObject condition) {
        var selected = condition.get("event");
        if (selected != null && (!selected.isJsonPrimitive() || !selected.getAsJsonPrimitive().isString())) return List.of();
        String event = selected == null ? "" : selected.getAsString();
        if (event.endsWith(":player_died")) return resources("damage_type");
        if (event.endsWith(":entity_killed")) return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString).sorted().toList();
        if (event.endsWith(":dimension_entered")) return resources("dimension");
        if (event.endsWith(":biome_discovered")) return resources("biome");
        if (event.endsWith(":advancement_earned")) return resources("advancement");
        if (event.endsWith(":structure_visited")) return resources("structure");
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).sorted().toList();
    }

    static List<String> resources(String field) {
        var minecraft = Minecraft.getInstance();
        if (field.equals("criterion")) return List.of("villagerretaliation:player_died");
        if (field.equals("event")) {
            var events = new java.util.TreeSet<String>();
            for (var tag : com.jvn.villagerretaliation.village.VillageEventMemory.EventTag.values())
                events.add(com.jvn.villagerretaliation.village.VillageEventMemory.idFor(tag).toString());
            for (String name : List.of("structure_visited", "settlement_visited", "item_discovered", "dimension_entered", "advancement_earned", "biome_discovered", "player_died", "entity_killed",
                    "trade_completed", "trade_item_paid", "trade_item_received", "trade_currency_paid", "trade_currency_received",
                    "contribution_items", "contribution_food", "contribution_equipment", "contribution_currency",
                    "contribution_defense", "contribution_construction", "contribution_storage")) events.add("villagerretaliation:" + name);
            return List.copyOf(events);
        }
        if (minecraft == null || minecraft.getConnection() == null) return List.of();
        return switch (field) {
            case "structure" -> minecraft.getSingleplayerServer() != null
                    ? minecraft.getSingleplayerServer().registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().stream().map(Object::toString).sorted().toList()
                    : minecraft.level == null ? List.of() : minecraft.level.registryAccess().registry(Registries.STRUCTURE)
                            .map(registry -> registry.keySet().stream().map(Object::toString).sorted().toList()).orElseGet(List::of);
            case "dimension" -> minecraft.getConnection().levels().stream().map(key -> key.location().toString()).sorted().toList();
            case "biome" -> minecraft.level == null ? List.of() : minecraft.level.registryAccess()
                    .registryOrThrow(Registries.BIOME).keySet().stream().map(Object::toString).sorted().toList();
            case "damage_type" -> minecraft.level == null ? List.of() : minecraft.level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).keySet().stream().map(Object::toString).sorted().toList();
            case "advancement" -> minecraft.getSingleplayerServer() != null
                    ? minecraft.getSingleplayerServer().getAdvancements().getAllAdvancements().stream().map(holder -> holder.id().toString()).sorted().toList()
                    : minecraft.getConnection().getAdvancements().getTree().nodes().stream().map(node -> node.holder().id().toString()).sorted().toList();
            default -> List.of();
        };
    }
}
