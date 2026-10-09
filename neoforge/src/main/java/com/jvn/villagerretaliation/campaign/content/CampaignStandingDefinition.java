package com.jvn.villagerretaliation.campaign.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;

public record CampaignStandingDefinition(String id, Scope scope, String titleKey, int minimum, int maximum, int initial,
        Map<String, Reaction> reactions, String pointer) {
    public CampaignStandingDefinition {
        reactions = Collections.unmodifiableMap(new LinkedHashMap<>(reactions));
    }

    public Reaction reaction(int value) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException("Standing is outside its authored range");
        Reaction result = null;
        for (var reaction : reactions.values()) {
            if (value < reaction.minimum()) break;
            result = reaction;
        }
        return result;
    }

    public static CampaignStandingDefinition find(CampaignDefinition campaign, Scope scope, String subject) {
        return (scope == Scope.CHARACTER ? campaign.relationships() : campaign.factions()).get(subject);
    }

    public static Stream<CampaignStandingDefinition> all(CampaignDefinition campaign) {
        return Stream.concat(campaign.relationships().values().stream(), campaign.factions().values().stream());
    }

    static JsonObject schema() {
        var reaction = CampaignSchema.object(List.of("id", "title_key", "minimum"),
                "id", CampaignSchema.text("^[a-z0-9][a-z0-9_./-]*$"), "title_key", CampaignSchema.text(""), "minimum", number());
        var reactions = CampaignSchema.array(reaction, 1);
        reactions.addProperty("maxItems", 64);
        return CampaignSchema.object(List.of("id", "title_key", "minimum", "maximum", "initial", "reactions"),
                "id", CampaignSchema.text("^[a-z0-9][a-z0-9_./-]*$"), "title_key", CampaignSchema.text(""),
                "minimum", number(), "maximum", number(), "initial", number(), "reactions", reactions);
    }

    private static JsonObject number() {
        var value = CampaignSchema.type("integer");
        value.addProperty("minimum", Integer.MIN_VALUE);
        value.addProperty("maximum", Integer.MAX_VALUE);
        return value;
    }

    static Map<String, CampaignStandingDefinition> read(ResourceLocation source, JsonArray entries, Scope scope, List<QuestDiagnostic> issues) {
        var values = new LinkedHashMap<String, CampaignStandingDefinition>();
        String section = scope == Scope.CHARACTER ? "relationships" : "factions";
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index).getAsJsonObject();
            String pointer = "/" + section + "/" + index;
            int minimum = entry.get("minimum").getAsInt(), maximum = entry.get("maximum").getAsInt(), initial = entry.get("initial").getAsInt();
            if (maximum < minimum) issues.add(CampaignSchema.error(source, pointer + "/maximum", "standing maximum must not be below its minimum"));
            if (initial < minimum || initial > maximum) issues.add(CampaignSchema.error(source, pointer + "/initial", "initial standing must be inside its authored range"));
            var reactions = new LinkedHashMap<String, Reaction>();
            var thresholds = entry.getAsJsonArray("reactions");
            int previous = minimum;
            for (int at = 0; at < thresholds.size(); at++) {
                var threshold = thresholds.get(at).getAsJsonObject();
                String path = pointer + "/reactions/" + at;
                int start = threshold.get("minimum").getAsInt();
                if (at == 0 && start != minimum) issues.add(CampaignSchema.error(source, path + "/minimum", "the first reaction must start at the standing minimum"));
                if (at > 0 && start <= previous) issues.add(CampaignSchema.error(source, path + "/minimum", "reaction thresholds must increase in authored order"));
                if (start < minimum || start > maximum) issues.add(CampaignSchema.error(source, path + "/minimum", "reaction threshold must be inside the standing range"));
                String id = threshold.get("id").getAsString();
                if (reactions.putIfAbsent(id, new Reaction(id, threshold.get("title_key").getAsString(), start, path)) != null)
                    issues.add(CampaignSchema.error(source, path + "/id", "duplicate standing reaction " + id));
                previous = start;
            }
            String id = entry.get("id").getAsString();
            var value = new CampaignStandingDefinition(id, scope, entry.get("title_key").getAsString(), minimum, maximum, initial, reactions, pointer);
            if (values.putIfAbsent(id, value) != null) issues.add(CampaignSchema.error(source, pointer + "/id", "duplicate " + section + " id " + id));
        }
        return values;
    }

    static void validate(CampaignDefinition campaign, List<QuestDiagnostic> issues) {
        all(campaign).forEach(value -> {
            if (value.scope() == Scope.CHARACTER && !campaign.characters().containsKey(value.id()))
                issues.add(CampaignSchema.error(campaign.source(), value.pointer() + "/id", "relationship must name an authored campaign character " + value.id()));
            localized(campaign, value.titleKey(), value.pointer() + "/title_key", issues);
            value.reactions().values().forEach(reaction -> localized(campaign, reaction.titleKey(), reaction.pointer() + "/title_key", issues));
        });
    }

    private static void localized(CampaignDefinition campaign, String key, String pointer, List<QuestDiagnostic> issues) {
        if (!key.startsWith(campaign.localizationPrefix()))
            issues.add(CampaignSchema.error(campaign.source(), pointer, "localization key must use the campaign prefix " + campaign.localizationPrefix()));
    }

    public enum Scope { CHARACTER, FACTION }
    public record Reaction(String id, String titleKey, int minimum, String pointer) { }
}
