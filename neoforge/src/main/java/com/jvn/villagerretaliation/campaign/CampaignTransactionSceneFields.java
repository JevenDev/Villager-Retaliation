package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class CampaignTransactionSceneFields {
    public static final String DEFINITION = "campaign_transaction_fields";

    private CampaignTransactionSceneFields() { }

    public static JsonObject reference() {
        var value = new JsonObject();
        value.addProperty("$ref", "#/$defs/" + DEFINITION);
        return value;
    }

    public static JsonObject definition() {
        var rules = new JsonArray();
        var object = new JsonObject();
        object.add("additionalProperties", reference());
        var actions = new JsonArray();
        var payment = CampaignPaymentAction.schema();
        var properties = payment.getAsJsonObject("properties");
        properties.add("quest", CampaignFieldSchema.string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
        properties.add("quest_id", CampaignFieldSchema.string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
        var owners = new JsonArray();
        for (String name : new String[] {"quest", "quest_id"}) {
            var owner = new JsonObject(); var required = new JsonArray(); required.add(name); owner.add("required", required); owners.add(owner);
        }
        payment.add("anyOf", owners);
        actions.add(typeRule("campaign_payment", payment));
        actions.add(typeRule("campaign_transaction", CampaignTransactionPredicate.schema()));
        object.add("allOf", actions);
        rules.add(shapeRule("object", object));
        var array = new JsonObject(); array.add("items", reference());
        rules.add(shapeRule("array", array));
        var result = new JsonObject(); result.add("allOf", rules);
        return result;
    }

    public static JsonObject schema() {
        var schema = reference();
        var definitions = new JsonObject(); definitions.add(DEFINITION, definition()); schema.add("$defs", definitions);
        return schema;
    }

    public static List<QuestJsonSchema.Issue> issues(JsonObject data) { return QuestJsonSchema.validate(data, schema()); }

    private static JsonObject shapeRule(String type, JsonObject consequence) {
        var condition = new JsonObject(); condition.addProperty("type", type);
        var rule = new JsonObject(); rule.add("if", condition); rule.add("then", consequence);
        return rule;
    }

    private static JsonObject typeRule(String type, JsonObject consequence) {
        var condition = new JsonObject();
        var pattern = new StringBuilder("^[\\x00-\\x20]*");
        for (char letter : type.toCharArray()) pattern.append(Character.isLetter(letter)
                ? "[" + letter + Character.toUpperCase(letter) + "]" : Character.toString(letter));
        pattern.append("[\\x00-\\x20]*$");
        var properties = new JsonObject(); var name = new JsonObject();
        name.addProperty("type", "string"); name.addProperty("pattern", pattern.toString()); properties.add("type", name);
        condition.add("properties", properties);
        var required = new JsonArray(); required.add("type"); condition.add("required", required);
        var rule = new JsonObject(); rule.add("if", condition); rule.add("then", consequence);
        return rule;
    }

    public static List<Reference> references(JsonObject data) {
        if (!issues(data).isEmpty()) return List.of();
        var result = new ArrayList<Reference>();
        visit(data, "", result);
        return List.copyOf(result);
    }

    private static void visit(JsonElement value, String pointer, List<Reference> result) {
        if (value.isJsonArray()) {
            var array = value.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) visit(array.get(index), pointer + "/" + index, result);
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            var rawType = object.get("type");
            String type = rawType != null && rawType.isJsonPrimitive() && rawType.getAsJsonPrimitive().isString() ? rawType.getAsString().trim().toLowerCase(java.util.Locale.ROOT) : "";
            if (type.equals("campaign_payment")) {
                var action = CampaignPaymentAction.read(object);
                var quest = object.has("quest") ? object.get("quest") : object.get("quest_id");
                result.add(new Reference(null, ResourceLocation.parse(quest.getAsString()), action.transaction(), true, pointer + "/transaction"));
            } else if (type.equals("campaign_transaction")) {
                var condition = CampaignTransactionPredicate.read(object);
                result.add(new Reference(condition.campaign(), condition.quest(), condition.transaction(), false, pointer + "/transaction"));
            }
            object.entrySet().forEach(entry -> visit(entry.getValue(),
                    pointer + "/" + entry.getKey().replace("~", "~0").replace("/", "~1"), result));
        }
    }

    public record Reference(ResourceLocation campaign, ResourceLocation quest, String transaction, boolean payment, String pointer) { }
}
