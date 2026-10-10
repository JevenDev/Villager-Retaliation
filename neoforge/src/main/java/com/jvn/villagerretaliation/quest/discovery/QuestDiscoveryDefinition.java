package com.jvn.villagerretaliation.quest.discovery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.util.item.ItemStackPredicate;
import com.jvn.villagerretaliation.util.item.ItemStackPredicateParser;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record QuestDiscoveryDefinition(String id, String event, boolean offer, JsonObject match,
        ItemStackPredicate itemPredicate, List<DialogueCondition> conditions) {
    public static QuestDiscoveryDefinition parse(ResourceLocation quest, JsonObject entry, HolderLookup.Provider registries) {
        JsonObject match = entry.has("match") ? entry.getAsJsonObject("match").deepCopy() : new JsonObject();
        for (String field : List.of("item", "item_tag", "dimension", "structure", "biome"))
            if (match.has(field) && ResourceLocation.tryParse(match.get(field).getAsString()) == null)
                throw new IllegalArgumentException("Invalid discovery " + field + " resource id.");
        return new QuestDiscoveryDefinition(entry.get("id").getAsString(), entry.get("event").getAsString(),
                entry.has("mode") && entry.get("mode").getAsString().equals("offer"), match,
                ItemStackPredicateParser.parse(registries, match, List.of(), List.of()),
                DialogueCondition.readList(quest, "discovery." + entry.get("id").getAsString(), entry, quest));
    }

    public boolean matchesItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (event.equals("book_opened") && !stack.is(Items.WRITTEN_BOOK)) return false;
        if (match.has("item") && !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(string("item"))) return false;
        if (match.has("item_tag") && !stack.is(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse(string("item_tag"))))) return false;
        if (match.has("book")) {
            if (!stack.is(Items.WRITTEN_BOOK)) return false;
            var data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null || !data.copyTag().getString("villagerretaliation:quest_book").equals(string("book"))) return false;
        }
        return itemPredicate.matches(stack);
    }

    public boolean matchesPayload(Map<String, String> payload) {
        if (!match.has("payload")) return true;
        for (var entry : match.getAsJsonObject("payload").entrySet())
            if (!entry.getValue().getAsString().equals(payload.get(entry.getKey()))) return false;
        return true;
    }

    public String string(String key) { return match.has(key) ? match.get(key).getAsString() : ""; }
    public double radius() { return match.has("radius") ? match.get("radius").getAsDouble() : 8; }

    public static JsonObject arraySchema() {
        return JsonParser.parseString("""
                {"type":"array","maxItems":128,"items":{"type":"object","additionalProperties":false,"required":["id","event"],
                  "properties":{
                    "id":{"type":"string","pattern":"^[a-z][a-z0-9_.-]{0,63}$"},
                    "event":{"enum":["item_acquired","item_used","book_opened","location","mob_kill","block_break",
                      "block_place","block_interact","memory_event","gift","trade","reputation","criterion"]},
                    "mode":{"enum":["automatic","offer"]},"conditions":{"type":"array","items":{"type":"object"}},
                    "match":{"type":"object","additionalProperties":false,"properties":{
                      "item":{"type":"string"},"item_tag":{"type":"string"},"book":{"type":"string"},
                      "components":{"type":"object"},"durability":{"type":"object"},
                      "dimension":{"type":"string"},"structure":{"type":"string"},"biome":{"type":"string"},
                      "pos":{"type":"array","minItems":3,"maxItems":3,"items":{"type":"integer"}},
                      "radius":{"type":"number","minimum":1,"maximum":512},
                      "payload":{"type":"object","additionalProperties":{"type":"string"}}}}},
                  "allOf":[
                    {"if":{"properties":{"event":{"const":"location"}}},"then":{"required":["match"],
                      "properties":{"match":{"anyOf":[{"required":["structure"]},{"required":["biome"]},{"required":["pos"]}]}}}},
                    {"if":{"properties":{"event":{"enum":["item_acquired","item_used","book_opened"]}}},"then":{"required":["match"],
                      "properties":{"match":{"anyOf":[{"required":["item"]},{"required":["item_tag"]},{"required":["book"]}]}}}}
                  ]}}
                """).getAsJsonObject();
    }
}
