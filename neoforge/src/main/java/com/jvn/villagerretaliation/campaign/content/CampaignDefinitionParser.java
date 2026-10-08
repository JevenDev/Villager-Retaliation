package com.jvn.villagerretaliation.campaign.content;

import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import net.minecraft.core.BlockPos;
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
                put(outcomes, outcomeId, new CampaignDefinition.Outcome(outcomeId, text(outcome, "title_key"),
                        java.util.stream.StreamSupport.stream(array(outcome, "location_variants").spliterator(), false).map(JsonElement::getAsString).toList(), outcomePointer),
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
                observation = new CampaignDefinition.Observation(resource(value, "event"), text(value, "outcome"), text(value, "character"), pointer + "/observe");
            }
            boolean irreversible = !entry.has("irreversible") || entry.get("irreversible").getAsBoolean();
            put(milestones, id, new CampaignDefinition.Milestone(id, text(entry, "chapter"), resource(entry, "quest"),
                    text(entry, "stage"), irreversible, outcomes, catchUp, observation, pointer), source, pointer, diagnostics);
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
        Map<String, CampaignRewardDefinition> rewards = new LinkedHashMap<>();
        JsonArray rewardEntries = array(root, "rewards");
        for (int index = 0; index < rewardEntries.size(); index++) {
            JsonObject entry = rewardEntries.get(index).getAsJsonObject();
            String pointer = "/rewards/" + index;
            String id = text(entry, "id");
            put(rewards, id, new CampaignRewardDefinition(id, resource(entry, "quest"),
                    CampaignRewardDefinition.Scope.valueOf(text(entry, "scope").toUpperCase(Locale.ROOT)),
                    text(entry, "milestone"), text(entry, "outcome"), entry.has("experience") ? entry.get("experience").getAsInt() : 0,
                    entry.has("loot_table") ? resource(entry, "loot_table") : null, pointer), source, pointer, diagnostics);
        }
        Map<String, CampaignLocationDefinition> locations = new LinkedHashMap<>();
        JsonArray locationEntries = array(root, "locations");
        for (int index = 0; index < locationEntries.size(); index++) {
            JsonObject entry = locationEntries.get(index).getAsJsonObject();
            String pointer = "/locations/" + index;
            String id = text(entry, "id");
            var kind = CampaignLocationDefinition.Source.valueOf(text(entry, "source").toUpperCase(Locale.ROOT));
            if (kind == CampaignLocationDefinition.Source.MARKER && (!entry.has("origin") || entry.has("search_radius"))) {
                diagnostics.add(CampaignSchema.error(source, pointer, "marker locations require an origin and cannot search"));
                continue;
            }
            if ((kind == CampaignLocationDefinition.Source.STRUCTURE) != entry.has("structure")) {
                diagnostics.add(CampaignSchema.error(source, pointer + "/structure", "only structure locations require a structure id"));
                continue;
            }
            var origin = entry.has("origin") ? entry.getAsJsonObject("origin") : null;
            var position = origin == null ? null : new BlockPos(
                    origin.get("x").getAsInt(), origin.get("y").getAsInt(), origin.get("z").getAsInt());
            put(locations, id, new CampaignLocationDefinition(id, text(entry, "title_key"), kind, resource(entry, "dimension"),
                    position, entry.has("search_radius") ? entry.get("search_radius").getAsInt() : CampaignLocationDefinition.MAX_SEARCH_RADIUS,
                    entry.has("structure") ? resource(entry, "structure") : null,
                    entry.has("structure_marker") ? resource(entry, "structure_marker") : null, pointer), source, pointer, diagnostics);
        }
        Map<String, CampaignCharacterDefinition> characters = new LinkedHashMap<>();
        JsonArray characterEntries = array(root, "characters");
        for (int index = 0; index < characterEntries.size(); index++) {
            JsonObject entry = characterEntries.get(index).getAsJsonObject();
            String pointer = "/characters/" + index;
            String id = text(entry, "id");
            var kind = CampaignCharacterDefinition.Source.valueOf(text(entry, "source").toUpperCase(Locale.ROOT));
            int searchRadius = entry.has("search_radius") ? entry.get("search_radius").getAsInt()
                    : kind == CampaignCharacterDefinition.Source.OWNED_SPAWN ? 4 : 32;
            put(characters, id, new CampaignCharacterDefinition(id, text(entry, "title_key"),
                    kind, text(entry, "location"), searchRadius,
                    entry.has("profession") ? resource(entry, "profession") : null,
                    CampaignCharacterDefinition.LossPolicy.valueOf(text(entry, "loss_policy").toUpperCase(Locale.ROOT)),
                    text(entry, "replacement_role"), pointer), source, pointer, diagnostics);
        }
        Map<String, CampaignEvidenceDefinition> evidence = new LinkedHashMap<>();
        JsonArray evidenceEntries = array(root, "evidence");
        for (int index = 0; index < evidenceEntries.size(); index++) {
            JsonObject entry = evidenceEntries.get(index).getAsJsonObject();
            String pointer = "/evidence/" + index;
            String id = text(entry, "id");
            put(evidence, id, new CampaignEvidenceDefinition(id, text(entry, "title_key"), text(entry, "text_key"),
                    text(entry, "location"), resource(entry, "entity_tag"), entry.has("search_radius") ? entry.get("search_radius").getAsInt() : 8,
                    pointer), source, pointer, diagnostics);
        }
        CampaignDefinition definition = new CampaignDefinition(resource(root, "id"), root.get("revision").getAsInt(),
                QuestBundleFingerprints.definition(root), text(root, "title_key"), text(root, "localization_prefix"), bundles, chapters, milestones, facts, rewards, locations, characters, evidence,
                CampaignLocationVariant.read(source, array(root, "location_variants"), diagnostics), source);
        validateReferences(definition, diagnostics);
        CampaignLocationVariant.validate(definition, diagnostics);
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
        for (var chapter : definition.chapters().values()) {
            for (int index = 0; index < chapter.quests().size(); index++) {
                requireCampaignOwner(definition, quests, chapter.quests().get(index), chapter.pointer() + "/quests/" + index, diagnostics);
            }
        }
        for (var reward : definition.rewards().values()) {
            requireCampaignOwner(definition, quests, reward.quest(), reward.pointer() + "/quest", diagnostics);
        }
        for (var questId : definition.questBundles()) {
            var quest = quests.compiledQuest(questId).orElse(null);
            if (quest == null) continue;
            if (!quest.provider().campaignRole().isBlank() && !definition.characters().containsKey(quest.provider().campaignRole())) {
                diagnostics.add(CampaignContentCatalog.inPack(CampaignSchema.error(quest.source().resource(), "/provider/campaign_role",
                        "unknown campaign character " + quest.provider().campaignRole()), quest.source().sourcePackId()));
            }
            for (var objective : quest.objectives()) {
                com.jvn.villagerretaliation.campaign.CampaignStoryObjective.referenceIssues(objective.definition().story(), definition, quests, questId)
                        .forEach((field, message) -> diagnostics.add(CampaignContentCatalog.inPack(CampaignSchema.error(
                                objective.source().resource(), objective.source().jsonPointer() + "/story/" + field, message),
                                objective.source().sourcePackId())));
                String alias = objective.definition().campaignLocation();
                if (!alias.isBlank() && !definition.locations().containsKey(alias)) diagnostics.add(CampaignContentCatalog.inPack(CampaignSchema.error(
                        objective.source().resource(), objective.source().jsonPointer() + "/campaign_location", "unknown campaign location " + alias),
                        objective.source().sourcePackId()));
            }
        }
        for (var bundle : quests.bundles().values()) {
            if (bundle.questId() == null || !definition.questBundles().contains(bundle.questId())) continue;
            for (var sceneId : bundle.definitions().getOrDefault(QuestBundlePath.Kind.SCENE, Map.of()).keySet()) {
                var scene = quests.scenes().get(sceneId);
                if (scene == null) continue;
                for (var actor : scene.actors().values()) {
                    if (actor.bindingSource() == SceneActorDeclaration.BindingSource.CAMPAIGN_ROLE
                            && !definition.characters().containsKey(actor.bindingReference())) diagnostics.add(CampaignSchema.error(definition.source(),
                            "/quest_bundles", "scene " + sceneId + " actor " + actor.alias() + " references unknown campaign character " + actor.bindingReference()));
                    if (actor.bindingSource() == SceneActorDeclaration.BindingSource.CAMPAIGN_LOCATION
                            && !definition.locations().containsKey(actor.bindingReference())) diagnostics.add(CampaignSchema.error(definition.source(),
                            "/quest_bundles", "scene " + sceneId + " actor " + actor.alias() + " references unknown campaign location " + actor.bindingReference()));
                }
            }
        }
        Set<String> stageOwners = new HashSet<>();
        for (var milestone : definition.milestones().values()) {
            var original = quests.compiledQuest(milestone.quest()).orElse(null);
            String entryStage = milestone.stage().isBlank() && original != null ? original.entryStage() : milestone.stage();
            if (original != null && !original.stagesById().containsKey(entryStage)) {
                diagnostics.add(CampaignSchema.error(definition.source(), milestone.pointer() + "/stage", "milestone stage does not exist: " + entryStage));
            }
            if (!stageOwners.add(milestone.quest() + "/" + entryStage)) {
                diagnostics.add(CampaignSchema.error(definition.source(), milestone.pointer() + "/stage", "another milestone already owns this quest stage"));
            }
            var catchUp = milestone.catchUp();
            if (catchUp == null) continue;
            requireCampaignOwner(definition, quests, catchUp.quest(), catchUp.pointer() + "/quest", diagnostics);
            if (catchUp.quest().equals(milestone.quest()) && catchUp.stage().equals(entryStage)) {
                diagnostics.add(CampaignSchema.error(definition.source(), catchUp.pointer() + "/stage", "catch-up must use an alternate entry stage"));
            }
            var quest = quests.compiledQuest(catchUp.quest()).orElse(null);
            if (quest != null && !quest.stagesById().containsKey(catchUp.stage())) {
                diagnostics.add(CampaignSchema.error(definition.source(), catchUp.pointer() + "/stage",
                        "catch-up stage does not exist in " + catchUp.quest() + ": " + catchUp.stage()));
            }
            if (quest != null && milestone.observation() != null && milestone.observation().characterLost()
                    && quest.provider().campaignRole().equals(milestone.observation().character())) {
                diagnostics.add(CampaignSchema.error(definition.source(), catchUp.pointer() + "/quest",
                        "character-loss catch-up cannot require the lost character as its quest provider"));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static void requireCampaignOwner(CampaignDefinition definition, QuestContentCatalog quests,
            ResourceLocation questId, String pointer, List<QuestDiagnostic> diagnostics) {
        var quest = quests.compiledQuest(questId).orElse(null);
        if (quest != null && !definition.id().equals(quest.metadata().campaign())) {
            diagnostics.add(CampaignSchema.error(definition.source(), pointer,
                    "quest metadata.campaign must identify the owning campaign " + definition.id() + ": " + questId));
        }
    }

    private static void validateReferences(CampaignDefinition definition, List<QuestDiagnostic> diagnostics) {
        ResourceLocation source = definition.source();
        Set<ResourceLocation> bundles = new HashSet<>(definition.questBundles());
        for (var reward : definition.rewards().values()) {
            requireBundle(source, bundles, reward.quest(), reward.pointer() + "/quest", diagnostics);
            if (reward.experience() == 0 && reward.lootTable() == null) diagnostics.add(CampaignSchema.error(source, reward.pointer(), "reward needs experience or a loot table"));
            if (reward.scope() == CampaignRewardDefinition.Scope.PERSONAL) {
                if (!reward.milestone().isBlank() || !reward.outcome().isBlank()) diagnostics.add(CampaignSchema.error(source, reward.pointer(), "personal rewards use their quest's authored conditions, not shared milestone credit"));
            } else {
                var milestone = definition.milestones().get(reward.milestone());
                if (milestone == null || !milestone.irreversible() || !milestone.quest().equals(reward.quest())) {
                    diagnostics.add(CampaignSchema.error(source, reward.pointer() + "/milestone", "shared and participant rewards require an irreversible milestone owned by their quest"));
                } else if (!reward.outcome().isBlank() && !milestone.outcomes().containsKey(reward.outcome())) {
                    diagnostics.add(CampaignSchema.error(source, reward.pointer() + "/outcome", "unknown reward outcome " + reward.outcome()));
                }
            }
        }
        for (var character : definition.characters().values()) {
            localized(definition, character.titleKey(), character.pointer() + "/title_key", diagnostics);
            if (!definition.locations().containsKey(character.location())) diagnostics.add(CampaignSchema.error(source,
                    character.pointer() + "/location", "unknown campaign location " + character.location()));
            if (character.lossPolicy() == CampaignCharacterDefinition.LossPolicy.ALTERNATE_PATH
                    && definition.milestones().values().stream().noneMatch(milestone -> milestone.observation() != null
                            && milestone.observation().characterLost() && character.id().equals(milestone.observation().character()))) {
                diagnostics.add(CampaignSchema.error(source, character.pointer() + "/loss_policy",
                        "alternate_path requires a milestone observing this character's permanent loss"));
            }
        }
        for (var character : definition.characters().values()) {
            var visited = new HashSet<String>();
            var current = character;
            while (current != null && current.lossPolicy() == CampaignCharacterDefinition.LossPolicy.REPLACEMENT) {
                if (!visited.add(current.id())) {
                    diagnostics.add(CampaignSchema.error(source, character.pointer() + "/replacement_role", "character replacements contain a cycle"));
                    break;
                }
                var replacement = definition.characters().get(current.replacementRole());
                if (replacement == null) diagnostics.add(CampaignSchema.error(source, current.pointer() + "/replacement_role",
                        "unknown replacement character " + current.replacementRole()));
                current = replacement;
            }
        }
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        localized(definition, definition.titleKey(), "/title_key", diagnostics);
        definition.locations().values().forEach(location -> localized(definition, location.titleKey(), location.pointer() + "/title_key", diagnostics));
        for (var evidence : definition.evidence().values()) {
            localized(definition, evidence.titleKey(), evidence.pointer() + "/title_key", diagnostics);
            localized(definition, evidence.textKey(), evidence.pointer() + "/text_key", diagnostics);
            if (!definition.locations().containsKey(evidence.location())) diagnostics.add(CampaignSchema.error(source,
                    evidence.pointer() + "/location", "unknown campaign location " + evidence.location()));
        }
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
                if (milestone.observation().characterLost() && !definition.characters().containsKey(milestone.observation().character())) {
                    diagnostics.add(CampaignSchema.error(source, milestone.observation().pointer() + "/character",
                            "unknown campaign character " + milestone.observation().character()));
                }
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
