package com.jvn.villagerretaliation.quest.follower;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.compiler.SceneDiagnostic;
import java.util.ArrayList;
import net.minecraft.resources.ResourceLocation;

public record QuestFollowerDirective(SceneActorDeclaration actor, String command, boolean stageScoped,
        double distance, double maximumDistance, double speed, JsonObject data) {
    public static QuestFollowerDirective parse(ResourceLocation source, JsonObject data) {
        var issues = com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(data, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid follower directive: " + issues);
        var actors = new JsonArray();
        actors.add(data.getAsJsonObject("actor"));
        var diagnostics = new ArrayList<SceneDiagnostic>();
        var parsed = SceneParser.parseActors(actors, source, diagnostics);
        if (parsed.size() != 1 || !diagnostics.isEmpty()) throw new IllegalArgumentException("Invalid follower actor: " + diagnostics);
        var actor = parsed.getFirst();
        if (!java.util.Set.of(SceneActorDeclaration.BindingSource.QUEST_PROVIDER, SceneActorDeclaration.BindingSource.UUID,
                SceneActorDeclaration.BindingSource.CAMPAIGN_ROLE, SceneActorDeclaration.BindingSource.ENTITY_TAG,
                SceneActorDeclaration.BindingSource.OWNED_SPAWN, SceneActorDeclaration.BindingSource.ENCOUNTER).contains(actor.bindingSource()))
            throw new IllegalArgumentException("Follower actor requires an entity binding source.");
        if (!java.util.Set.of(ResourceLocation.parse("villagerretaliation:villager"),
                ResourceLocation.parse("villagerretaliation:living_entity")).contains(actor.actorType()))
            throw new IllegalArgumentException("Followers require a villager or living_entity actor type.");
        if (actor.replacementPolicy() != SceneActorDeclaration.ReplacementPolicy.FIXED
                && actor.replacementPolicy() != SceneActorDeclaration.ReplacementPolicy.OPTIONAL)
            throw new IllegalArgumentException("Followers use fixed entity bindings.");
        if (actor.deathPolicy() == SceneActorDeclaration.DeathPolicy.RESPAWN_IF_OWNED
                || actor.deathPolicy() == SceneActorDeclaration.DeathPolicy.CONTINUE_WITH_SNAPSHOT)
            throw new IllegalArgumentException("Followers require a live actor and cannot respawn actors.");
        if (actor.lethalDamagePolicy() != SceneActorDeclaration.LethalDamagePolicy.NORMAL)
            throw new IllegalArgumentException("Use protected to prevent lethal follower damage.");
        if (actor.bindingSource() != SceneActorDeclaration.BindingSource.QUEST_PROVIDER && actor.bindingReference().isBlank())
            throw new IllegalArgumentException("Follower actor requires a binding reference.");
        if (actor.bindingSource() == SceneActorDeclaration.BindingSource.UUID)
            java.util.UUID.fromString(actor.bindingReference());
        if (actor.filters().keySet().stream().anyMatch(key -> !key.equals("entity_type")))
            throw new IllegalArgumentException("Follower actors support only the entity_type filter.");
        double distance = number(data, "distance", 2);
        double maximum = number(data, "maximum_distance", 48);
        if (maximum <= distance) throw new IllegalArgumentException("Follower maximum_distance must exceed distance.");
        return new QuestFollowerDirective(actor, data.has("command") ? data.get("command").getAsString() : "follow",
                data.has("scope") && data.get("scope").getAsString().equals("stage"), distance,
                maximum, number(data, "speed", 0.55), data.deepCopy());
    }

    private static double number(JsonObject object, String field, double fallback) {
        return object.has(field) ? object.get(field).getAsDouble() : fallback;
    }

    public static JsonObject schema() {
        var schema = JsonParser.parseString("""
                {"type":"object","required":["actor"],"properties":{
                  "command":{"enum":["follow","wait","resume","release"]},"scope":{"enum":["quest","stage"]},
                  "distance":{"type":"number","minimum":1,"maximum":16},
                  "maximum_distance":{"type":"number","minimum":2,"maximum":128},
                  "speed":{"type":"number","minimum":0.05,"maximum":1},"protected":{"type":"boolean"}
                }}
                """).getAsJsonObject();
        var actor = com.jvn.villagerretaliation.scene.schema.SceneSchema.actor();
        actor.getAsJsonArray("required").add("binding_source");
        actor.add("allOf", JsonParser.parseString("""
                [{"if":{"properties":{"binding_source":{"enum":["uuid","campaign_role","entity_tag","owned_spawn","encounter"]}}},
                  "then":{"anyOf":[{"required":["binding"]},{"required":["source"]}]}}]
                """));
        var fields = actor.getAsJsonObject("properties");
        fields.add("type", JsonParser.parseString("{\"enum\":[\"villagerretaliation:villager\",\"villagerretaliation:living_entity\"]}"));
        fields.add("binding_source", JsonParser.parseString("{\"enum\":[\"quest_provider\",\"uuid\",\"campaign_role\",\"entity_tag\",\"owned_spawn\",\"encounter\"]}"));
        fields.add("replacement_policy", JsonParser.parseString("{\"enum\":[\"fixed\",\"optional\"]}"));
        fields.add("death_policy", JsonParser.parseString("{\"enum\":[\"fail\",\"block\",\"apply_missing_policy\"]}"));
        fields.add("lethal_damage_policy", JsonParser.parseString("{\"const\":\"normal\"}"));
        fields.add("filters", JsonParser.parseString("{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"entity_type\":{\"type\":\"string\",\"pattern\":\"^[a-z0-9_.-]+:[a-z0-9_./-]+$\"}}}"));
        schema.getAsJsonObject("properties").add("actor", actor);
        return schema;
    }
}
