package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.quest.QuestDefinition;

final class EditorPlaytestState {
    private EditorPlaytestState() { }
    static String restartReason(JsonObject applied, JsonObject draft, String stage) {
        if (applied == null || stage.isBlank()) return "";
        var stages = draft.get("stages");
        boolean exists = stages != null && (stages.isJsonObject() ? stages.getAsJsonObject().has(stage)
                : stages.isJsonArray() && stages.getAsJsonArray().asList().stream().anyMatch(value -> value.isJsonObject()
                    && stage.equals(DialogueGraph.string(value.getAsJsonObject(), "id"))));
        if (!exists)
            return "The running stage was removed. Restart from offer before applying changes.";
        if (revision(draft) <= revision(applied)) return "";
        var migration = EditorSchema.object(draft.get("migration"));
        if (migration.isEmpty() && draft.has("revision") && draft.get("revision").isJsonObject())
            migration = EditorSchema.object(draft.getAsJsonObject("revision").get("migration"));
        String policy = DialogueGraph.string(migration, "active_policy");
        if (policy.isBlank()) policy = DialogueGraph.string(migration, "on_active_change");
        if (QuestDefinition.RevisionPolicy.bySerializedName(policy) != QuestDefinition.RevisionPolicy.KEEP)
            return "This revision changes active quest progress (" + policy + "). Restart from offer before applying changes.";
        return "";
    }
    private static int revision(JsonObject root) {
        if (!root.has("revision")) return 1;
        var value = root.get("revision");
        return value.isJsonObject() ? value.getAsJsonObject().has("number") ? value.getAsJsonObject().get("number").getAsInt() : 1 : value.getAsInt();
    }
}
