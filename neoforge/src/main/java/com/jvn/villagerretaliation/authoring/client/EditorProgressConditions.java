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
            case "player_kills" -> "Total player kills";
            case "advancement", "advancements" -> "Player has obtained advancements";
            default -> type;
        };
    }

    static JsonObject seed(String type) {
        var condition = new JsonObject();
        condition.addProperty("type", type);
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
        if (type.equals("advancement") || type.equals("advancements")) return true;
        for (var kind : PlayerHistoryConditions.Kind.values())
            if (kind.id().equals(type)) return !kind.count() && kind != PlayerHistoryConditions.Kind.PLAYER_LAST_DEATH;
        return false;
    }

    static String resourceField(String path) {
        String[] parts = path.split("/");
        String field = parts[parts.length - 1];
        if (field.matches("[0-9]+") && parts.length > 1) field = parts[parts.length - 2];
        return switch (field) {
            case "dimensions" -> "dimension";
            case "biomes" -> "biome";
            case "advancements" -> "advancement";
            case "entities" -> "entity";
            case "items" -> "item";
            case "damage_types" -> "damage_type";
            default -> field;
        };
    }

    static List<String> resources(String field) {
        var minecraft = Minecraft.getInstance();
        if (field.equals("criterion")) return List.of("villagerretaliation:player_died");
        if (minecraft == null || minecraft.getConnection() == null) return List.of();
        return switch (field) {
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
