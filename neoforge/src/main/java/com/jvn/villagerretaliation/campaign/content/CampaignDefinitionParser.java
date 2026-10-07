package com.jvn.villagerretaliation.campaign.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleFingerprints;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class CampaignDefinitionParser {
    private CampaignDefinitionParser() {
    }

    public static Result parse(ResourceLocation source, JsonElement input) {
        List<QuestDiagnostic> diagnostics = new ArrayList<>(CampaignSchema.validate(source, input));
        if (!diagnostics.isEmpty()) return new Result(null, diagnostics);
        JsonObject root = input.getAsJsonObject();
        List<ResourceLocation> bundles = resources(root.getAsJsonArray("quest_bundles"));
        unique(bundles, source, "/quest_bundles", diagnostics);
        Map<String, CampaignDefinition.Chapter> chapters = new LinkedHashMap<>();
        JsonArray chapterEntries = root.getAsJsonArray("chapters");
        for (int index = 0; index < chapterEntries.size(); index++) {
            JsonObject entry = chapterEntries.get(index).getAsJsonObject();
            String pointer = "/chapters/" + index;
            String id = text(entry, "id");
            List<CampaignDefinition.Prerequisite> prerequisites = new ArrayList<>();
            JsonArray requirements = array(entry, "prerequisites");
            for (int requirement = 0; requirement < requirements.size(); requirement++) {
                JsonObject value = requirements.get(requirement).getAsJsonObject();
                prerequisites.add(new CampaignDefinition.Prerequisite(text(value, "chapter"), text(value, "milestone"),
                        text(value, "outcome"), pointer + "/prerequisites/" + requirement));
            }
            List<ResourceLocation> quests = resources(entry.getAsJsonArray("quests"));
            unique(quests, source, pointer + "/quests", diagnostics);
            put(chapters, id, new CampaignDefinition.Chapter(id, text(entry, "title_key"), prerequisites, quests, pointer),
                    source, pointer, diagnostics);
        }
        Map<String, CampaignDefinition.Milestone> milestones = new LinkedHashMap<>();
        JsonArray milestoneEntries = root.getAsJsonArray("milestones");
        for (int index = 0; index < milestoneEntries.size(); index++) {
            JsonObject entry = milestoneEntries.get(index).getAsJsonObject();
            String pointer = "/milestones/" + index;
            String id = text(entry, "id");
            Map<String, CampaignDefinition.Outcome> outcomes = new LinkedHashMap<>();
            JsonArray outcomeEntries = entry.getAsJsonArray("outcomes");
            for (int outcomeIndex = 0; outcomeIndex < outcomeEntries.size(); outcomeIndex++) {
                JsonObject outcome = outcomeEntries.get(outcomeIndex).getAsJsonObject();
                String outcomeId = text(outcome, "id");
                String outcomePointer = pointer + "/outcomes/" + outcomeIndex;
                put(outcomes, outcomeId, new CampaignDefinition.Outcome(outcomeId, text(outcome, "title_key"), outcomePointer),
                        source, outcomePointer, diagnostics);
            }
            CampaignDefinition.CatchUp catchUp = null;
            if (entry.has("catch_up")) {
                JsonObject value = entry.getAsJsonObject("catch_up");
                catchUp = new CampaignDefinition.CatchUp(resource(value, "quest"), text(value, "stage"), pointer + "/catch_up");
            }
            CampaignDefinition.Observation observation = null;
            if (entry.has("observe")) {
                JsonObject value = entry.getAsJsonObject("observe");
                observation = new CampaignDefinition.Observation(resource(value, "event"), text(value, "outcome"), pointer + "/observe");
            }
            boolean irreversible = !entry.has("irreversible") || entry.get("irreversible").getAsBoolean();
            put(milestones, id, new CampaignDefinition.Milestone(id, text(entry, "chapter"), resource(entry, "quest"),
                    irreversible, outcomes, catchUp, observation, pointer), source, pointer, diagnostics);
        }
        Map<String, CampaignDefinition.Fact> facts = new LinkedHashMap<>();
        JsonArray factEntries = array(root, "facts");
        for (int index = 0; index < factEntries.size(); index++) {
            JsonObject entry = factEntries.get(index).getAsJsonObject();
            String pointer = "/facts/" + index;
            String id = text(entry, "id");
            put(facts, id, new CampaignDefinition.Fact(id,
                    CampaignDefinition.FactScope.valueOf(text(entry, "scope").toUpperCase(Locale.ROOT)),
                    CampaignDefinition.FactKind.valueOf(text(entry, "kind").toUpperCase(Locale.ROOT)), pointer),
                    source, pointer, diagnostics);
        }
        CampaignDefinition definition = new CampaignDefinition(resource(root, "id"), root.get("revision").getAsInt(),
                QuestBundleFingerprints.definition(root), text(root, "title_key"), text(root, "localization_prefix"), bundles, chapters, milestones, facts, source);
        validateReferences(definition, diagnostics);
        return new Result(diagnostics.isEmpty() ? definition : null, diagnostics);
    }

    public static List<QuestDiagnostic> validateQuestReferences(CampaignDefinition definition, QuestContentCatalog quests) {
        List<QuestDiagnostic> diagnostics = new ArrayList<>();
        Set<ResourceLocation> owned = new HashSet<>();
        quests.bundles().values().forEach(bundle -> {
            if (!bundle.owner().shared() && bundle.questId() != null) owned.add(bundle.questId());
        });
        for (int index = 0; index < definition.questBundles().size(); index++) {
            ResourceLocation quest = definition.questBundles().get(index);
            if (!owned.contains(quest) || quests.compiledQuest(quest).isEmpty()) {
                diagnostics.add(CampaignSchema.error(definition.source(), "/quest_bundles/" + index,
                        "quest bundle is unavailable: " + quest));
            }
        }
        for (var milestone : definition.milestones().values()) {
            var catchUp = milestone.catchUp();
            if (catchUp == null) continue;
            var quest = quests.compiledQuest(catchUp.quest()).orElse(null);
            if (quest != null && !quest.stagesById().containsKey(catchUp.stage())) {
                diagnostics.add(CampaignSchema.error(definition.source(), catchUp.pointer() + "/stage",
                        "catch-up stage does not exist in " + catchUp.quest() + ": " + catchUp.stage()));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static void validateReferences(CampaignDefinition definition, List<QuestDiagnostic> diagnostics) {
        ResourceLocation source = definition.source();
        Set<ResourceLocation> bundles = new HashSet<>(definition.questBundles());
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        localized(definition, definition.titleKey(), "/title_key", diagnostics);
        for (var chapter : definition.chapters().values()) {
            localized(definition, chapter.titleKey(), chapter.pointer() + "/title_key", diagnostics);
            Set<String> requiredChapters = new LinkedHashSet<>();
            Set<String> requiredTargets = new HashSet<>();
            for (var requirement : chapter.prerequisites()) {
                boolean hasChapter = !requirement.chapter().isBlank();
                boolean hasMilestone = !requirement.milestone().isBlank();
                if (hasChapter == hasMilestone) {
                    diagnostics.add(CampaignSchema.error(source, requirement.pointer(),
                            "prerequisite must reference exactly one chapter or milestone"));
                    continue;
                }
                String target = hasChapter ? "chapter/" + requirement.chapter() : "milestone/" + requirement.milestone();
                if (!requiredTargets.add(target)) diagnostics.add(CampaignSchema.error(source, requirement.pointer(), "duplicate prerequisite " + target));
                if (hasChapter) {
                    if (!definition.chapters().containsKey(requirement.chapter())) {
                        diagnostics.add(CampaignSchema.error(source, requirement.pointer() + "/chapter", "unknown chapter " + requirement.chapter()));
                    } else requiredChapters.add(requirement.chapter());
                    if (!requirement.outcome().isBlank()) diagnostics.add(CampaignSchema.error(source,
                            requirement.pointer() + "/outcome", "chapter prerequisites cannot name a milestone outcome"));
                } else {
                    var milestone = definition.milestones().get(requirement.milestone());
                    if (milestone == null) {
                        diagnostics.add(CampaignSchema.error(source, requirement.pointer() + "/milestone", "unknown milestone " + requirement.milestone()));
                    } else {
                        requiredChapters.add(milestone.chapter());
                        if (!requirement.outcome().isBlank() && !milestone.outcomes().containsKey(requirement.outcome())) {
                            diagnostics.add(CampaignSchema.error(source, requirement.pointer() + "/outcome", "unknown milestone outcome " + requirement.outcome()));
                        }
                    }
                }
            }
            dependencies.put(chapter.id(), requiredChapters);
            for (int index = 0; index < chapter.quests().size(); index++) {
                ResourceLocation quest = chapter.quests().get(index);
                requireBundle(source, bundles, quest, chapter.pointer() + "/quests/" + index, diagnostics);
            }
        }
        for (var milestone : definition.milestones().values()) {
            var chapter = definition.chapters().get(milestone.chapter());
            if (chapter == null) diagnostics.add(CampaignSchema.error(source, milestone.pointer() + "/chapter", "unknown chapter " + milestone.chapter()));
            else if (!chapter.quests().contains(milestone.quest())) diagnostics.add(CampaignSchema.error(source,
                    milestone.pointer() + "/quest", "milestone quest must belong to its chapter"));
            requireBundle(source, bundles, milestone.quest(), milestone.pointer() + "/quest", diagnostics);
            milestone.outcomes().values().forEach(outcome -> localized(definition, outcome.titleKey(), outcome.pointer() + "/title_key", diagnostics));
            if (milestone.irreversible() && milestone.catchUp() == null) {
                diagnostics.add(CampaignSchema.error(source, milestone.pointer() + "/catch_up", "irreversible milestone requires an authored catch-up entry"));
            }
            if (milestone.catchUp() != null) requireBundle(source, bundles, milestone.catchUp().quest(), milestone.catchUp().pointer() + "/quest", diagnostics);
            if (milestone.observation() != null) {
                if (!milestone.irreversible()) diagnostics.add(CampaignSchema.error(source,
                        milestone.observation().pointer(), "external world observations require an irreversible milestone"));
                if (!milestone.outcomes().containsKey(milestone.observation().outcome())) diagnostics.add(CampaignSchema.error(source,
                        milestone.observation().pointer() + "/outcome", "observed outcome does not exist"));
            }
        }
        Set<String> resolved = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (var entry : dependencies.entrySet()) {
                if (!resolved.contains(entry.getKey()) && resolved.containsAll(entry.getValue())) changed |= resolved.add(entry.getKey());
            }
        } while (changed);
        for (String chapter : dependencies.keySet()) {
            if (!resolved.contains(chapter)) diagnostics.add(CampaignSchema.error(source,
                    definition.chapters().get(chapter).pointer() + "/prerequisites", "chapter prerequisites contain a cycle or an unresolved dependency"));
        }
    }

    private static void localized(CampaignDefinition definition, String key, String pointer, List<QuestDiagnostic> diagnostics) {
        if (!key.startsWith(definition.localizationPrefix())) diagnostics.add(CampaignSchema.error(definition.source(), pointer,
                "localization key must use the campaign prefix " + definition.localizationPrefix()));
    }

    private static void requireBundle(ResourceLocation source, Set<ResourceLocation> bundles, ResourceLocation quest,
            String pointer, List<QuestDiagnostic> diagnostics) {
        if (!bundles.contains(quest)) diagnostics.add(CampaignSchema.error(source, pointer, "quest is not declared in quest_bundles: " + quest));
    }

    private static <T> void put(Map<String, T> values, String id, T value, ResourceLocation source,
            String pointer, List<QuestDiagnostic> diagnostics) {
        if (values.putIfAbsent(id, value) != null) diagnostics.add(CampaignSchema.error(source, pointer + "/id", "duplicate id " + id));
    }

    private static <T> void unique(List<T> values, ResourceLocation source, String pointer, List<QuestDiagnostic> diagnostics) {
        Set<T> seen = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            if (!seen.add(values.get(index))) diagnostics.add(CampaignSchema.error(source, pointer + "/" + index, "duplicate reference " + values.get(index)));
        }
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static ResourceLocation resource(JsonObject object, String key) {
        return ResourceLocation.parse(text(object, key));
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static List<ResourceLocation> resources(JsonArray values) {
        List<ResourceLocation> result = new ArrayList<>();
        for (JsonElement value : values) result.add(ResourceLocation.parse(value.getAsString()));
        return List.copyOf(result);
    }

    public record Result(CampaignDefinition definition, List<QuestDiagnostic> diagnostics) {
        public Result {
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean valid() {
            return this.definition != null && this.diagnostics.isEmpty();
        }
    }
}
