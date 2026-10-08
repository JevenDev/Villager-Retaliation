package com.jvn.villagerretaliation.scene.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts;
import com.jvn.villagerretaliation.api.registry.RuntimeTypeDescriptor;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record CampaignFollowDefinition(String actor, String targetActor, double followDistance,
        double arrivalDistance, double speed, int pollTicks, long timeoutTicks) {
    public static final ResourceLocation TYPE = ResourceLocation.parse("villagerretaliation:follow_actor");
    private static final String ALIAS = "^[a-z][a-z0-9_.-]{0,63}$";

    public CampaignFollowDefinition {
        if (actor == null || !actor.matches(ALIAS) || targetActor == null || !targetActor.matches(ALIAS) || actor.equals(targetActor)
                || !Double.isFinite(followDistance) || followDistance < 2 || followDistance > 32
                || !Double.isFinite(arrivalDistance) || arrivalDistance < 0.25 || arrivalDistance > 4
                || !Double.isFinite(speed) || speed < 0.05 || speed > 1 || pollTicks < 1 || pollTicks > 5
                || timeoutTicks < 1 || timeoutTicks > 72000)
            throw new IllegalArgumentException("Invalid campaign follow step.");
    }

    public static CampaignFollowDefinition parse(JsonObject data) {
        var issues = QuestJsonSchema.validate(data, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid campaign follow data: " + issues);
        return new CampaignFollowDefinition(data.get("actor").getAsString(), data.get("target_actor").getAsString(),
                number(data, "follow_distance", 8), number(data, "arrival_distance", 1.5), number(data, "speed", 0.55),
                (int) number(data, "poll_ticks", 5), (long) number(data, "timeout_ticks", 1200));
    }

    public List<String> actorIssues(Map<String, SceneActorDeclaration> actors, List<String> used) {
        var issues = new ArrayList<String>();
        var guide = actors.get(actor);
        var destination = actors.get(targetActor);
        if (guide == null || guide.bindingSource() != SceneActorDeclaration.BindingSource.CAMPAIGN_ROLE)
            issues.add("follow actor must bind a campaign character role");
        if (destination == null || destination.bindingSource() != SceneActorDeclaration.BindingSource.CAMPAIGN_LOCATION)
            issues.add("follow destination must bind a campaign location");
        if (!used.contains(actor) || !used.contains(targetActor))
            issues.add("follow step actors must include its guide and destination aliases");
        return List.copyOf(issues);
    }

    public static RuntimeTypeDescriptor descriptor() {
        return new RuntimeTypeDescriptor(TYPE, Set.of(), Set.of(), Set.of(), CampaignFollowDefinition::parse,
                value -> List.of(), (value, context) -> value, Object::toString,
                ExtensionContracts.RecoveryMode.WORLD_RECONCILED,
                new ExtensionContracts.ToolingMetadata("Follow a campaign character",
                        "Accompany a bound character to an authored location, waiting for enrolled players.", schemaFields(), true),
                ExtensionContracts.ClientSync.PARTICIPANTS);
    }

    public static JsonObject schema() { return new Gson().toJsonTree(schemaFields()).getAsJsonObject(); }

    private static Map<String, Object> schemaFields() {
        var fields = new LinkedHashMap<String, Object>();
        var alias = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(Map.of("type", "string", "pattern", ALIAS)));
        fields.put("actor", alias);
        fields.put("target_actor", alias);
        fields.put("follow_distance", numberSchema(2, 32, false));
        fields.put("arrival_distance", numberSchema(0.25, 4, false));
        fields.put("speed", numberSchema(0.05, 1, false));
        fields.put("poll_ticks", numberSchema(1, 5, true));
        fields.put("timeout_ticks", numberSchema(1, 72000, true));
        return java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(Map.of("type", "object", "additionalProperties", false,
                "required", List.of("actor", "target_actor"), "properties", fields)));
    }

    private static Map<String, Object> numberSchema(double minimum, double maximum, boolean integer) {
        return java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(
                Map.of("type", integer ? "integer" : "number", "minimum", minimum, "maximum", maximum)));
    }

    private static double number(JsonObject data, String key, double fallback) {
        return data.has(key) ? data.get(key).getAsDouble() : fallback;
    }
}
