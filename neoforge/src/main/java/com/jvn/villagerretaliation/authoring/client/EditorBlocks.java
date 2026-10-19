package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.ArrayList;
import java.util.List;

final class EditorBlocks {
    enum Kind {
        PLAYER_DIMENSION_VISITED("Player has entered dimensions", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.PLAYER_DIMENSION_VISITED),
        WORLD_DIMENSION_UNLOCKED("World has unlocked dimensions", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.WORLD_DIMENSION_UNLOCKED),
        WORLD_ADVANCEMENT("World has obtained advancements", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.WORLD_ADVANCEMENT),
        PLAYER_KILLED_ENTITY("Player has killed entities", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.PLAYER_KILLED_ENTITY),
        PLAYER_KILLED_BY_ENTITY("Player has been killed by entities", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.PLAYER_KILLED_BY_ENTITY),
        PLAYER_DIED_FROM("Player has died from damage types", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.PLAYER_DIED_FROM),
        PLAYER_LAST_DEATH("Most recent player death", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.PLAYER_LAST_DEATH),
        PLAYER_BIOME_VISITED("Player has visited biomes", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.PLAYER_BIOME_VISITED),
        PLAYER_CRAFTED_ITEM("Player has crafted items", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.PLAYER_CRAFTED_ITEM),
        PLAYER_USED_ITEM("Player has used items", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.PLAYER_USED_ITEM),
        PLAYER_DEATHS("Total player deaths", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.PLAYER_DEATHS),
        PLAYER_KILLS("Total player kills (PvP)", com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind.PLAYER_KILLS),
        PLAYER_STAT("player_stat"),
        EVENT_HISTORY("event_history"),
        PERSONAL_TRADE("personal_trade"),
        SETTLEMENT_NEED("settlement_need"),
        SETTLEMENT_CONTRIBUTION("settlement_contribution"),
        ADVANCEMENT_GATE("Player has obtained advancements", true, "{\"type\":\"advancement\",\"advancements\":[\"minecraft:story/mine_stone\"],\"all\":true}"),
        REPUTATION_GATE("Require reputation", true, "{\"type\":\"reputation\",\"min\":0}"),
        ITEM_GATE("Require an item", true, "{\"type\":\"player_item\",\"item\":\"minecraft:emerald\"}"),
        TIME_GATE("Require time of day", true, "{\"type\":\"time\",\"time\":\"morning\"}"),
        QUEST_GATE("Require quest state", true, "{\"type\":\"quest\",\"quest\":\"example:quest\",\"state\":\"completed\"}"),
        WEATHER_GATE("Require weather", true, "{\"type\":\"weather\",\"weather\":\"clear\"}"),
        MESSAGE("Show a message", false, "{\"type\":\"notification\",\"text\":\"Hello.\"}"),
        EXPERIENCE("Give experience", false, "{\"type\":\"experience\",\"amount\":1}"),
        REPUTATION("Change reputation", false, "{\"type\":\"reputation\",\"amount\":1}");

        final String label;
        final boolean condition;
        final String json;

        Kind(String label, com.jvn.villagerretaliation.dialogue.PlayerHistoryConditions.Kind kind) {
            this(label, true, EditorProgressConditions.seed(kind.id()).toString());
        }

        Kind(String type) {
            this(EditorProgressConditions.label(type), true, EditorProgressConditions.seed(type).toString());
        }

        Kind(String label, boolean condition, String json) {
            this.label = label;
            this.condition = condition;
            this.json = json;
        }
    }

    private EditorBlocks() { }

    static List<Kind> available(AuthoringDocument document, String pointer) {
        if ((document.kind() == DefinitionKind.RESOURCE || document.kind() == DefinitionKind.WORLD_CAMPAIGN)) return List.of();
        JsonObject root = document.json();
        boolean nodeOwner = false, responseOwner = false, exchangeOwner = false;
        for (var node : DialogueGraph.nodes(document)) {
            if (pointer.equals(node.pointer())) nodeOwner = true;
            JsonObject value = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            String responseField = document.kind() == DefinitionKind.FORCED ? "options" : "responses";
            var responses = DialogueGraph.array(value, responseField);
            for (int i = 0; i < responses.size(); i++)
                if (responses.get(i).isJsonObject() && pointer.equals(node.pointer() + "/" + responseField + "/" + i)) responseOwner = true;
            var exchanges = DialogueGraph.array(value, "exchanges");
            for (int i = 0; i < exchanges.size(); i++)
                if (exchanges.get(i).isJsonObject() && pointer.equals(node.pointer() + "/exchanges/" + i)) exchangeOwner = true;
        }
        List<Kind> result = new ArrayList<>();
        if (document.kind() == DefinitionKind.CONVERSATION) {
            if (responseOwner || exchangeOwner) result.addAll(java.util.Arrays.stream(Kind.values()).filter(value -> value.condition).toList());
            if (nodeOwner || responseOwner) result.addAll(List.of(Kind.EXPERIENCE, Kind.REPUTATION));
        } else if (document.kind() == DefinitionKind.QUEST) {
            if (nodeOwner) result.addAll(List.of(Kind.EXPERIENCE, Kind.REPUTATION));
        } else if (nodeOwner || responseOwner) result.addAll(List.of(Kind.values()));
        return List.copyOf(result);
    }

    static String append(AuthoringDocument document, String pointer, Kind kind) {
        if (!available(document, pointer).contains(kind)) throw new IllegalArgumentException("This block is not supported at the selected location.");
        String field = kind.condition ? "conditions" : document.kind() == DefinitionKind.CONVERSATION ? "effects"
                : document.kind() == DefinitionKind.QUEST ? "on_enter" : "actions";
        JsonObject selected = JsonPointers.get(document.json(), pointer).getAsJsonObject();
        if (field.equals("on_enter") && !selected.has(field) && selected.has("entry_actions")) field = "entry_actions";
        String targetField = field;
        int index = selected.has(field) ? selected.getAsJsonArray(field).size() : 0;
        document.edit("Add " + kind.label.toLowerCase(java.util.Locale.ROOT), root -> {
            JsonObject owner = JsonPointers.get(root, pointer).getAsJsonObject();
            JsonArray values = owner.has(targetField) ? owner.getAsJsonArray(targetField) : new JsonArray();
            JsonObject block = JsonParser.parseString(kind.json).getAsJsonObject();
            if (targetField.equals("effects")) {
                JsonObject effect = new JsonObject();
                String id = "effect_" + (index + 1);
                java.util.Set<String> used = new java.util.HashSet<>();
                collectEffectIds(root, used);
                while (used.contains(id)) id += "_new";
                effect.addProperty("id", id);
                effect.add("action", block);
                block = effect;
            }
            values.add(block);
            owner.add(targetField, values);
        });
        return pointer + "/" + field + "/" + index;
    }

    private static void collectEffectIds(com.google.gson.JsonElement value, java.util.Set<String> ids) {
        if (value.isJsonArray()) {
            for (var child : value.getAsJsonArray()) collectEffectIds(child, ids);
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            for (var effect : DialogueGraph.array(object, "effects"))
                if (effect.isJsonObject()) ids.add(DialogueGraph.string(effect.getAsJsonObject(), "id"));
            for (var child : object.entrySet()) collectEffectIds(child.getValue(), ids);
        }
    }
}
