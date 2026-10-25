package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import java.util.List;

final class EditorSupportingResources {
    static EditorResourceRecipes.Kind kind(AuthoringDocument document) {
        String path = DefinitionKind.unscopedPath(document.resourcePath());
        for (var kind : EditorResourceRecipes.Kind.values())
            if (kind != EditorResourceRecipes.Kind.SCENE && kind != EditorResourceRecipes.Kind.ENCOUNTER
                    && path.matches("data/[^/]+/" + (kind == EditorResourceRecipes.Kind.WORD_POOL ? "dialogue_words/[^/]+" : kind.directory) + "/.+\\.json")) return kind;
        return null;
    }

    static JsonObject seed(EditorResourceRecipes.Kind kind, String id) {
        return switch (kind) {
            case TRADE -> {
                var root = json("{\"replace\":false,\"entries\":[]}"); var trade = trade(id);
                root.getAsJsonArray("entries").add(trade); yield root;
            }
            case SELL_PRICE -> json("{\"item\":\"minecraft:wheat\",\"item_count\":8,\"currency_count\":1,\"enabled\":true}");
            case DUEL_KIT -> {
                var root = json("""
                        {"name":"Practice duel","description":"Iron swords and shields.","combat_style":"melee","sort_order":100,
                         "player":{"inventory":[{"slot":0,"stack":{"id":"minecraft:iron_sword","count":1}}],"equipment":{"offhand":{"id":"minecraft:shield","count":1}}},
                         "villager":{"equipment":{"mainhand":{"id":"minecraft:iron_sword","count":1},"offhand":{"id":"minecraft:shield","count":1}}}}
                        """);
                root.addProperty("name", EditorContentBrowser.humanize(id.substring(id.indexOf(':') + 1))); yield root;
            }
            case RAID_LOADOUT -> {
                var root = json("{\"replace\":false,\"loadouts\":[]}");
                root.getAsJsonArray("loadouts").add(loadout(id)); yield root;
            }
            case WORD_POOL -> json("{\"values\":[\"Hello.\",\"Good to see you.\"]}");
            case STYLE -> {
                var root = json("{\"tag\":\"custom_style\",\"text_effects\":{\"italic\":true},\"speed\":1}");
                String tag = id.substring(id.indexOf(':') + 1).replaceAll("[^a-z0-9_]", "_");
                if (!tag.matches("[a-z].*")) tag = "style_" + tag;
                tag = tag.substring(0, Math.min(64, tag.length()));
                if (!com.jvn.villagerretaliation.dialogue.DialogueStyle.validTag(tag)) tag = "style_" + tag;
                root.addProperty("tag", tag); yield root;
            }
            default -> throw new IllegalArgumentException("Choose a supporting resource recipe");
        };
    }

    static JsonObject trade(String id) {
        var trade = json("""
                {"professions":["minecraft:farmer"],"skills":["villagerretaliation:farming"],"min_rank":"novice","max_rank":"master",
                 "villager_level":1,"chance":1,"weight":10,"cost":{"item":"minecraft:emerald","count":1},
                 "result":{"item":"minecraft:bread","count":4},"max_uses":{"base":12},"xp":2,"price_multiplier":0.05}
                """);
        trade.addProperty("id", id); return trade;
    }

    private static JsonObject loadout(String id) {
        var value = json("""
                {"excluded_professions":["minecraft:nitwit"],"difficulty_pools":{
                 "normal":{"weapons":["minecraft:stone_sword"],"armor_chance":0.35,"enchant_chance":0.05,
                  "armor_sets":[{"weight":1,"head":"minecraft:iron_helmet"}]}}}
                """);
        value.addProperty("id", id); return value;
    }

    static String addTrade(com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace workspace, AuthoringDocument document) {
        requireArray(document.json(), "entries");
        var entries = DialogueGraph.array(document.json(), "entries");
        String namespace = document.resourcePath().split("/")[1];
        String id = unique(workspace, EditorResourceRecipes.Kind.TRADE, "entries", namespace + ":trade_");
        document.edit("Add trade", root -> {
            var values = DialogueGraph.array(root, "entries"); values.add(trade(id)); root.add("entries", values);
        });
        return "/entries/" + entries.size();
    }

    static String addLoadout(com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace workspace, AuthoringDocument document) {
        requireArray(document.json(), "loadouts");
        var entries = DialogueGraph.array(document.json(), "loadouts");
        String id = unique(workspace, EditorResourceRecipes.Kind.RAID_LOADOUT, "loadouts", "loadout_");
        document.edit("Add raid loadout", root -> {
            var values = DialogueGraph.array(root, "loadouts"); values.add(loadout(id)); root.add("loadouts", values);
        });
        return "/loadouts/" + entries.size();
    }

    static String addWording(AuthoringDocument document, boolean variant) {
        requireArray(document.json(), "values");
        int index = DialogueGraph.array(document.json(), "values").size();
        if (index >= 256) throw new IllegalArgumentException("Use at most 256 word pool values");
        document.edit("Add word pool entry", root -> {
            var values = DialogueGraph.array(root, "values");
            if (variant) values.add(json("{\"text\":\"Hello.\",\"weight\":1,\"conditions\":[]}"));
            else values.add("Hello.");
            root.add("values", values);
        });
        return "/values/" + index;
    }

    static boolean literalWording(AuthoringDocument document, String pointer) {
        return kind(document) == EditorResourceRecipes.Kind.WORD_POOL && pointer.matches("/values/[0-9]+(/text)?");
    }

    static void addRate(AuthoringDocument document) {
        var root = document.json();
        requireArray(root, "rates");
        if (root.has("rates") && (root.has("item_count") || root.has("currency_count")))
            throw new IllegalArgumentException("Repair the mixed legacy and list rates before adding another rate");
        var rates = DialogueGraph.array(root, "rates");
        if (rates.size() >= 256) throw new IllegalArgumentException("Use at most 256 sell rates");
        if (!root.has("rates")) {
            if (!root.has("item_count") || !root.has("currency_count")) throw new IllegalArgumentException("Set the first exchange rate before adding another");
            var first = new JsonObject(); first.add("item_count", root.get("item_count").deepCopy()); first.add("currency_count", root.get("currency_count").deepCopy());
            rates.add(first);
        }
        rates.add(json("{\"item_count\":16,\"currency_count\":2}"));
        document.edit("Add sell rate", value -> {
            value.remove("item_count"); value.remove("currency_count"); value.add("rates", rates.deepCopy());
        });
    }

    static void rangeCounts(AuthoringDocument document) {
        document.edit("Use ranged sell counts", root -> {
            var values = root.has("rates") ? root.getAsJsonArray("rates").asList() : List.of(root);
            for (var value : values) {
                var rate = value.getAsJsonObject();
                for (String field : List.of("item_count", "currency_count")) {
                    var count = rate.get(field);
                    if (count == null || !count.isJsonPrimitive() && !count.isJsonObject()) throw new IllegalArgumentException("Set valid counts before converting to ranges");
                    if (count.isJsonPrimitive()) {
                        if (!count.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Sell counts must be numbers");
                        var range = new JsonObject(); range.add("min", count.deepCopy()); range.add("max", count.deepCopy());
                        rate.add(field, range);
                    }
                }
            }
        });
    }

    static String addInventory(AuthoringDocument document, String participant) {
        if (!List.of("player", "villager").contains(participant)) throw new IllegalArgumentException("Choose a duel participant");
        var body = participant(document.json(), participant); requireArray(body, "inventory");
        var inventory = DialogueGraph.array(body, "inventory");
        var used = new java.util.HashSet<Integer>();
        for (var entry : inventory) if (entry.isJsonObject()) {
            var slot = entry.getAsJsonObject().get("slot");
            if (slot != null && slot.isJsonPrimitive() && slot.getAsJsonPrimitive().isNumber()) used.add(slot.getAsInt());
        }
        int slot = 0; while (used.contains(slot)) slot++;
        if (slot > 255) throw new IllegalArgumentException("Every supported inventory slot is occupied");
        var item = json("{\"stack\":{\"id\":\"minecraft:bread\",\"count\":1}}"); item.addProperty("slot", slot);
        inventory.add(item); body.add("inventory", inventory);
        document.edit("Add kit inventory item", root -> root.add(participant, body));
        return "/" + participant + "/inventory/" + (inventory.size() - 1);
    }

    static void equipment(AuthoringDocument document, String participant, String slot, String item) {
        if (!List.of("player", "villager").contains(participant) || !List.of("mainhand", "offhand", "head", "chest", "legs", "feet").contains(slot))
            throw new IllegalArgumentException("Choose a supported equipment slot");
        var body = participant(document.json(), participant);
        if (body.has("equipment") && !body.get("equipment").isJsonObject()) throw new IllegalArgumentException("Repair the equipment object first");
        var equipment = EditorSchema.object(body.get("equipment"));
        if (equipment.has(slot)) throw new IllegalArgumentException("That slot already has equipment. Edit its existing stack.");
        var stack = new JsonObject(); stack.addProperty("id", item); stack.addProperty("count", 1);
        equipment.add(slot, stack); body.add("equipment", equipment);
        document.edit("Add kit equipment", root -> root.add(participant, body));
    }

    static boolean hasEntry(com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace workspace, EditorResourceRecipes.Kind kind, String id) {
        String field = kind == EditorResourceRecipes.Kind.TRADE ? "entries" : "loadouts";
        for (var document : workspace.documents()) if (kind(document) == kind)
            for (var entry : DialogueGraph.array(document.json(), field))
                if (entry.isJsonObject() && DialogueGraph.string(entry.getAsJsonObject(), "id").equals(id)) return true;
        return false;
    }

    private static String unique(com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace workspace, EditorResourceRecipes.Kind kind, String field, String prefix) {
        var entries = new JsonArray();
        for (var document : workspace.documents()) if (kind(document) == kind) entries.addAll(DialogueGraph.array(document.json(), field));
        return EditorSceneDraft.unique(entries, "id", prefix);
    }

    private static JsonObject participant(JsonObject root, String name) {
        if (root.has(name) && !root.get(name).isJsonObject()) throw new IllegalArgumentException("Repair the participant object first");
        return EditorSchema.object(root.get(name));
    }

    private static void requireArray(JsonObject root, String field) {
        if (root.has(field) && !root.get(field).isJsonArray()) throw new IllegalArgumentException("Repair " + field + " before adding entries");
    }

    static boolean itemField(AuthoringDocument document, String path) {
        var kind = kind(document);
        if (kind == EditorResourceRecipes.Kind.DUEL_KIT) return path.matches("/(player|villager)/(inventory/[0-9]+/stack|equipment/[^/]+)/id");
        return kind == EditorResourceRecipes.Kind.RAID_LOADOUT && (path.matches(".*/weapons/[0-9]+") || path.matches(".*/armor_sets/[0-9]+/(head|chest|legs|feet)"));
    }

    static JsonObject schema(AuthoringDocument document) {
        var kind = kind(document);
        if (kind == null) return new JsonObject();
        if (kind == EditorResourceRecipes.Kind.TRADE) return json(com.jvn.villagerretaliation.trade.SkillTradeSchemaTool.schema());
        if (kind == EditorResourceRecipes.Kind.STYLE) return com.jvn.villagerretaliation.dialogue.DialogueStyle.schema();
        var resource = json("{\"type\":\"string\",\"minLength\":1}");
        var count = json("{\"type\":\"integer\",\"minimum\":1}");
        var stack = object("id", resource, "count", json("{\"type\":\"integer\",\"minimum\":1,\"maximum\":99}"), "components", object());
        var stacks = object();
        for (String slot : List.of("mainhand", "offhand", "head", "chest", "legs", "feet")) stacks.getAsJsonObject("properties").add(slot, stack.deepCopy());
        var inventory = array(object("slot", json("{\"type\":\"integer\",\"minimum\":0,\"maximum\":255}"), "stack", stack));
        return switch (kind) {
            case SELL_PRICE -> {
                var range = new JsonObject(); var choices = new JsonArray();
                choices.add(count); choices.add(object("min", count, "max", count)); range.add("anyOf", choices);
                var rates = array(object("item_count", range, "currency_count", range)); rates.addProperty("minItems", 1); rates.addProperty("maxItems", 256);
                yield object("enabled", json("{\"type\":\"boolean\"}"), "item", resource, "item_count", range, "currency_count", range,
                        "rates", rates, "market_group", resource, "priority", json("{\"type\":\"integer\"}"), "components", object(), "durability", object());
            }
            case DUEL_KIT -> {
                var body = object("inventory", inventory, "equipment", stacks);
                yield object("name", json("{\"type\":\"string\",\"minLength\":1,\"maxLength\":128}"),
                        "description", json("{\"type\":\"string\",\"minLength\":1,\"maxLength\":512}"),
                        "sort_order", json("{\"type\":\"integer\"}"), "bring_your_own", json("{\"type\":\"boolean\"}"),
                        "combat_style", json("{\"enum\":[\"melee\",\"ranged\"]}"), "player", body, "villager", body);
            }
            case RAID_LOADOUT -> {
                var chance = json("{\"type\":\"number\",\"minimum\":0,\"maximum\":1}");
                var armor = object("weight", count, "head", resource, "chest", resource, "legs", resource, "feet", resource);
                var pool = object("weapons", array(resource), "armor_chance", chance, "enchant_chance", chance, "armor_sets", array(armor));
                var pools = object("peaceful", pool, "easy", pool, "normal", pool, "hard", pool);
                yield object("replace", json("{\"type\":\"boolean\"}"), "loadouts", array(object("id", resource,
                        "professions", array(resource), "excluded_professions", array(resource), "difficulty_pools", pools)));
            }
            case WORD_POOL -> {
                var text = json("{\"type\":\"string\",\"minLength\":1,\"maxLength\":512,\"default\":\"Hello.\"}");
                text.addProperty("pattern", "^[^{}<>§\\p{Cntrl}]+$");
                var entry = object("text", text, "weight", json("{\"type\":\"integer\",\"minimum\":1,\"maximum\":1000}"),
                        "conditions", json("{\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/condition\"}}"));
                entry.add("required", JsonParser.parseString("[\"text\"]"));
                var choices = new JsonArray(); choices.add(text); choices.add(entry);
                var value = new JsonObject(); value.add("anyOf", choices);
                var values = array(value); values.addProperty("minItems", 1); values.addProperty("maxItems", 256);
                yield object("values", values);
            }
            default -> new JsonObject();
        };
    }

    private static JsonObject object(Object... fields) {
        var result = json("{\"type\":\"object\",\"properties\":{}}"); var properties = result.getAsJsonObject("properties");
        for (int i = 0; i < fields.length; i += 2) properties.add((String) fields[i], ((JsonObject) fields[i + 1]).deepCopy());
        return result;
    }
    private static JsonObject array(JsonObject items) { var result = json("{\"type\":\"array\"}"); result.add("items", items.deepCopy()); return result; }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
