package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CampaignEvidenceFields {
    public static final String DEFINITION = "campaign_evidence_fields";

    private CampaignEvidenceFields() { }

    public static JsonObject reference() { return CampaignFieldSchema.reference(DEFINITION); }

    public static JsonObject definition() {
        return CampaignFieldSchema.nestedDefinition(DEFINITION, Map.of("campaign_evidence", CampaignEvidencePredicate.schema()));
    }

    public static JsonObject schema() {
        var schema = reference();
        var definitions = new JsonObject(); definitions.add(DEFINITION, definition()); schema.add("$defs", definitions);
        return schema;
    }

    public static List<QuestJsonSchema.Issue> issues(JsonObject data) { return QuestJsonSchema.validate(data, schema()); }

    public static List<Reference> references(JsonObject data) {
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
            var type = object.get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()
                    && type.getAsString().equals("campaign_evidence")) {
                var condition = CampaignEvidencePredicate.read(object);
                if (condition != null) result.add(new Reference(condition, pointer + "/evidence"));
            }
            object.entrySet().forEach(entry -> visit(entry.getValue(),
                    pointer + "/" + entry.getKey().replace("~", "~0").replace("/", "~1"), result));
        }
    }

    public record Reference(CampaignEvidencePredicate condition, String pointer) { }
}
