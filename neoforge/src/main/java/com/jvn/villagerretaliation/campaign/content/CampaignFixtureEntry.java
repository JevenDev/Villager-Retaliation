package com.jvn.villagerretaliation.campaign.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.CampaignFacts;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record CampaignFixtureEntry(String id, String chapter, List<Effect> effects, String pointer) {
    private static final String ID = "^[a-z0-9][a-z0-9_./-]*$";
    private static final String RESOURCE = "^[a-z0-9_.-]+:[a-z0-9_./-]+$";

    public CampaignFixtureEntry { effects = List.copyOf(effects); }

    public sealed interface Effect permits WorldOutcome, CompleteMilestone, CompleteQuest, SetFact {
        String id();
        String pointer();
    }

    public record WorldOutcome(String id, String milestone, String outcome, String pointer) implements Effect { }
    public record CompleteMilestone(String id, String milestone, String outcome, String pointer) implements Effect { }
    public record CompleteQuest(String id, ResourceLocation quest, String pointer) implements Effect { }
    public record SetFact(String id, String fact, String value, String pointer) implements Effect { }

    static JsonObject schema() {
        var choices = new JsonArray();
        choices.add(effect("world_outcome", List.of("milestone", "outcome"),
                "milestone", CampaignSchema.text(ID), "outcome", CampaignSchema.text(ID)));
        choices.add(effect("complete_milestone", List.of("milestone", "outcome"),
                "milestone", CampaignSchema.text(ID), "outcome", CampaignSchema.text(ID)));
        choices.add(effect("complete_quest", List.of("quest"), "quest", CampaignSchema.text(RESOURCE)));
        var value = CampaignSchema.type("string");
        value.addProperty("maxLength", 4096);
        choices.add(effect("set_fact", List.of("fact", "value"), "fact", CampaignSchema.text(ID), "value", value));
        var effect = new JsonObject();
        effect.add("oneOf", choices);
        return CampaignSchema.object(List.of("id", "chapter", "effects"), "id", CampaignSchema.text(ID),
                "chapter", CampaignSchema.text(ID), "effects", CampaignSchema.array(effect, 0));
    }

    private static JsonObject effect(String type, List<String> required, Object... fields) {
        var properties = new ArrayList<Object>(List.of("id", CampaignSchema.text(ID), "type", CampaignSchema.text(ID)));
        Collections.addAll(properties, fields);
        var keys = new ArrayList<>(List.of("id", "type"));
        keys.addAll(required);
        var schema = CampaignSchema.object(keys, properties.toArray());
        schema.getAsJsonObject("properties").getAsJsonObject("type").addProperty("const", type);
        return schema;
    }

    static Map<String, CampaignFixtureEntry> read(ResourceLocation source, JsonArray values, List<QuestDiagnostic> diagnostics) {
        var entries = new LinkedHashMap<String, CampaignFixtureEntry>();
        for (int index = 0; index < values.size(); index++) {
            var raw = values.get(index).getAsJsonObject();
            String pointer = "/fixture_entries/" + index;
            String id = raw.get("id").getAsString();
            var effects = new ArrayList<Effect>();
            var rows = raw.getAsJsonArray("effects");
            for (int row = 0; row < rows.size(); row++) {
                var effect = rows.get(row).getAsJsonObject();
                String at = pointer + "/effects/" + row, effectId = effect.get("id").getAsString();
                effects.add(switch (effect.get("type").getAsString()) {
                    case "world_outcome" -> new WorldOutcome(effectId, effect.get("milestone").getAsString(), effect.get("outcome").getAsString(), at);
                    case "complete_milestone" -> new CompleteMilestone(effectId, effect.get("milestone").getAsString(), effect.get("outcome").getAsString(), at);
                    case "complete_quest" -> new CompleteQuest(effectId, ResourceLocation.parse(effect.get("quest").getAsString()), at);
                    case "set_fact" -> new SetFact(effectId, effect.get("fact").getAsString(), effect.get("value").getAsString(), at);
                    default -> throw new IllegalArgumentException("Unknown validated fixture effect");
                });
            }
            if (entries.putIfAbsent(id, new CampaignFixtureEntry(id, raw.get("chapter").getAsString(), effects, pointer)) != null)
                diagnostics.add(CampaignSchema.error(source, pointer + "/id", "duplicate fixture entry " + id));
        }
        return entries;
    }

    static void validate(CampaignDefinition definition, List<QuestDiagnostic> diagnostics) {
        for (var entry : definition.fixtureEntries().values()) entry.validateEntry(definition, diagnostics);
    }

    private void validateEntry(CampaignDefinition definition, List<QuestDiagnostic> diagnostics) {
        var selected = definition.chapters().get(chapter);
        if (selected == null) issue(definition, diagnostics, pointer + "/chapter", "unknown fixture chapter " + chapter);
        var ids = new LinkedHashSet<String>();
        var world = new LinkedHashMap<String, String>();
        var milestones = new LinkedHashMap<String, String>();
        var quests = new LinkedHashSet<ResourceLocation>();
        var facts = new LinkedHashSet<String>();
        for (var effect : effects) {
            if (!ids.add(effect.id())) issue(definition, diagnostics, effect.pointer() + "/id", "duplicate fixture effect " + effect.id());
            if (effect instanceof WorldOutcome outcome) {
                var milestone = outcome(definition, diagnostics, outcome.milestone(), outcome.outcome(), effect.pointer());
                if (milestone != null && !milestone.irreversible()) issue(definition, diagnostics, effect.pointer() + "/milestone", "fixture world outcome requires an irreversible milestone");
                if (world.putIfAbsent(outcome.milestone(), outcome.outcome()) != null)
                    issue(definition, diagnostics, effect.pointer() + "/milestone", "duplicate fixture world milestone " + outcome.milestone());
            } else if (effect instanceof CompleteMilestone completion) {
                var milestone = outcome(definition, diagnostics, completion.milestone(), completion.outcome(), effect.pointer());
                if (milestone != null && milestone.irreversible() && !completion.outcome().equals(world.get(completion.milestone())))
                    issue(definition, diagnostics, effect.pointer() + "/outcome", "fixture personal completion requires an earlier matching world outcome effect");
                if (milestones.putIfAbsent(completion.milestone(), completion.outcome()) != null)
                    issue(definition, diagnostics, effect.pointer() + "/milestone", "duplicate fixture personal milestone " + completion.milestone());
            } else if (effect instanceof CompleteQuest completion) {
                if (!definition.questBundles().contains(completion.quest())) issue(definition, diagnostics, effect.pointer() + "/quest", "fixture quest is not declared in quest_bundles: " + completion.quest());
                if (!quests.add(completion.quest())) issue(definition, diagnostics, effect.pointer() + "/quest", "duplicate fixture quest " + completion.quest());
                if (definition.milestones().values().stream().anyMatch(milestone -> milestone.quest().equals(completion.quest()) && !milestones.containsKey(milestone.id())))
                    issue(definition, diagnostics, effect.pointer() + "/quest", "complete the quest's personal milestones earlier in the fixture");
            } else if (effect instanceof SetFact value) {
                var fact = definition.facts().get(value.fact());
                if (fact == null) issue(definition, diagnostics, effect.pointer() + "/fact", "unknown fixture fact " + value.fact());
                else {
                    try { new CampaignFacts.Value(fact.kind(), value.value()); }
                    catch (IllegalArgumentException exception) { issue(definition, diagnostics, effect.pointer() + "/value", exception.getMessage()); }
                }
                if (!facts.add(value.fact())) issue(definition, diagnostics, effect.pointer() + "/fact", "duplicate fixture fact " + value.fact());
            }
        }
        if (selected == null) return;
        for (var prerequisite : selected.prerequisites()) {
            boolean satisfied;
            if (!prerequisite.chapter().isBlank()) {
                var prior = definition.chapters().get(prerequisite.chapter());
                satisfied = prior != null && quests.containsAll(prior.quests()) && definition.milestones().values().stream()
                        .filter(milestone -> milestone.chapter().equals(prior.id())).allMatch(milestone -> milestones.containsKey(milestone.id()));
            } else satisfied = milestones.containsKey(prerequisite.milestone())
                    && (prerequisite.outcome().isBlank() || prerequisite.outcome().equals(milestones.get(prerequisite.milestone())));
            if (!satisfied) issue(definition, diagnostics, pointer + "/effects", "fixture effects do not satisfy chapter prerequisite at " + prerequisite.pointer());
        }
    }

    private static CampaignDefinition.Milestone outcome(CampaignDefinition definition, List<QuestDiagnostic> diagnostics,
            String id, String outcome, String pointer) {
        var milestone = definition.milestones().get(id);
        if (milestone == null) issue(definition, diagnostics, pointer + "/milestone", "unknown fixture milestone " + id);
        else if (!milestone.outcomes().containsKey(outcome)) issue(definition, diagnostics, pointer + "/outcome", "unknown fixture outcome " + outcome);
        return milestone;
    }

    private static void issue(CampaignDefinition definition, List<QuestDiagnostic> diagnostics, String pointer, String message) {
        diagnostics.add(CampaignSchema.error(definition.source(), pointer, message));
    }
}
