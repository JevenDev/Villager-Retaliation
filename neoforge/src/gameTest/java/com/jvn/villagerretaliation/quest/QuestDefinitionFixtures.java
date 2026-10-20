package com.jvn.villagerretaliation.quest;

import static com.jvn.villagerretaliation.quest.VillagerQuestResources.firstNonBlank;
import static com.jvn.villagerretaliation.quest.VillagerQuestResources.inferQuestline;
import static com.jvn.villagerretaliation.quest.VillagerQuestResources.readActionsFromKey;
import static com.jvn.villagerretaliation.quest.VillagerQuestResources.readBonusOutcomes;
import static com.jvn.villagerretaliation.quest.VillagerQuestResources.readLines;
import static com.jvn.villagerretaliation.quest.VillagerQuestResources.readObjective;
import static com.jvn.villagerretaliation.quest.VillagerQuestResources.readOffer;
import static com.jvn.villagerretaliation.quest.VillagerQuestResources.readRevision;
import static com.jvn.villagerretaliation.quest.VillagerQuestResources.readRules;
import static com.jvn.villagerretaliation.quest.VillagerQuestResources.readStagePredicates;
import static com.jvn.villagerretaliation.quest.VillagerQuestResources.readStringMap;
import static com.jvn.villagerretaliation.quest.VillagerQuestResources.readTarget;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextVariant;
import com.jvn.villagerretaliation.dialogue.normal.DialogueUsagePolicy;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.item.ItemStackPredicateParser;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;

final class QuestDefinitionFixtures {
    private QuestDefinitionFixtures() { }

    static QuestDefinition read(ResourceLocation location, JsonObject root, ResourceLocation fallbackId) {
        return readQuest(location, root, fallbackId, ItemStackPredicateParser.DEFAULT_REGISTRIES);
    }

    private static QuestDefinition readQuest(
            ResourceLocation location,
            JsonObject root,
            ResourceLocation fallbackId,
            HolderLookup.Provider registries) {
        ResourceLocation id = DatapackJsonReader.readResourceLocation(root, "id").orElse(fallbackId);
        if (id == null) {
            return null;
        }

        JsonObject display = DatapackJsonReader.readObject(root, "display");
        String title = display == null ? "" : DatapackJsonReader.readString(display, "title");
        String description = display == null ? "" : DatapackJsonReader.readString(display, "description");
        String titleKey = display == null ? "" : DatapackJsonReader.readString(display, "title_key");
        String descriptionKey = display == null ? "" : DatapackJsonReader.readString(display, "description_key");
        ResourceLocation parent = DatapackJsonReader.readResourceLocation(root, "parent").orElse(null);
        List<ResourceLocation> prerequisites = readQuestPrerequisites(root, parent);
        QuestDefinition.Target target;
        List<QuestDefinition.Objective> objectives;
        ResourceLocation campaign;
        try {
            campaign = com.jvn.villagerretaliation.campaign.content.CampaignQuestReference.read(root.get("campaign"));
            target = readTarget(root, registries);
            objectives = readObjectives(location, root, id, registries);
            if (campaign == null && objectives.stream().anyMatch(objective -> !objective.campaignLocation().isBlank() || objective.story() != null)) {
                throw new IllegalArgumentException("campaign location and story objectives require a campaign owner");
            }
        } catch (IllegalArgumentException exception) {
            DatapackDiagnostics.warnSkippedEntry(location, "quest", id.toString(), exception.getMessage());
            return null;
        }

        return new QuestDefinition(
                id,
                title,
                description,
                titleKey,
                descriptionKey,
                firstNonBlank(DatapackJsonReader.readString(root, "questline"), inferQuestline(location)),
                readQuestTags(root),
                parent,
                prerequisites,
                DatapackJsonReader.readBoolean(root, "show_locked_adventure_hint", true),
                readOffer(location, root, id),
                target,
                objectives,
                readRules(location, root, id),
                readTracker(root),
                DatapackJsonReader.readString(root, "entry_stage"),
                readStages(location, root, id),
                readTriggers(location, root, id),
                readRewards(root),
                readDialogue(location, id, root),
                DialogueEntryMetadata.read(location, "quest", "quest", root),
                readLinks(root),
                readRevision(root),
                campaign
        );
    }

    private static List<ResourceLocation> readQuestPrerequisites(JsonObject root, ResourceLocation parent) {
        List<ResourceLocation> prerequisites = new ArrayList<>();
        for (String value : DatapackJsonReader.readStringList(root, "prerequisites")) {
            DatapackJsonReader.parseResourceLocation(value).ifPresent(prerequisites::add);
        }
        if (prerequisites.isEmpty() && parent != null) {
            prerequisites.add(parent);
        }
        return List.copyOf(prerequisites);
    }

    private static Set<String> readQuestTags(JsonObject root) {
        Set<String> tags = new LinkedHashSet<>(DatapackJsonReader.readStringList(root, "tag", "tags"));
        String group = DatapackJsonReader.readString(root, "group");
        if (!group.isBlank()) {
            tags.add("group." + group);
        }
        return Set.copyOf(tags);
    }

    private static List<QuestDefinition.Objective> readObjectives(
            ResourceLocation location,
            JsonObject root,
            ResourceLocation defaultQuestId,
            HolderLookup.Provider registries) {
        JsonElement element = root.get("objectives");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest objectives", "objectives must be an array.");
            return List.of();
        }

        List<QuestDefinition.Objective> objectives = new ArrayList<>();
        Set<String> objectiveIds = new LinkedHashSet<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) {
                readObjective(location, child.getAsJsonObject(), index, defaultQuestId, registries).ifPresent(objective -> {
                    if (!objectiveIds.add(objective.id())) {
                        DatapackDiagnostics.warnInvalidDialogueCondition(
                                location,
                                "quest objective \"" + objective.id() + "\"",
                                "duplicate objective id; later duplicate is ignored.");
                        return;
                    }
                    objectives.add(objective);
                });
            }
            index++;
        }
        return List.copyOf(objectives);
    }

    private static QuestDefinition.Rewards readRewards(JsonObject root) {
        JsonObject rewards = DatapackJsonReader.readObject(root, "rewards");
        if (rewards == null) {
            return QuestDefinition.Rewards.EMPTY;
        }
        return new QuestDefinition.Rewards(
                Math.max(0, DatapackJsonReader.readInt(rewards, "experience", 0)),
                DatapackJsonReader.readInt(rewards, "reputation", 0),
                DatapackJsonReader.readInt(rewards, "gossip_reputation", 0),
                DatapackJsonReader.readResourceLocation(rewards, "loot_table").orElse(null),
                readMemoryEvent(rewards),
                VillageEventMemory.MemoryScope.parse(DatapackJsonReader.readString(rewards, "memory_scope"))
                        .orElse(VillageEventMemory.MemoryScope.BOTH)
        );
    }

    private static QuestDefinition.Tracker readTracker(JsonObject root) {
        JsonObject tracker = DatapackJsonReader.readObject(root, "tracker");
        if (tracker == null) {
            return QuestDefinition.Tracker.EMPTY;
        }

        Map<String, QuestDefinition.Step> steps = new LinkedHashMap<>();
        JsonObject stepsObject = DatapackJsonReader.readObject(tracker, "steps");
        if (stepsObject != null) {
            for (Map.Entry<String, JsonElement> entry : stepsObject.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    steps.put(entry.getKey(), readTrackerStep(entry.getValue().getAsJsonObject()));
                }
            }
        }

        return new QuestDefinition.Tracker(
                DatapackJsonReader.readString(tracker, "title"),
                DatapackJsonReader.readString(tracker, "title_key"),
                steps,
                readStringMap(DatapackJsonReader.readObject(tracker, "metadata")),
                DatapackJsonReader.readResourceLocation(tracker, "icon").orElse(null),
                DatapackJsonReader.readString(tracker, "color"),
                DatapackJsonReader.readString(tracker, "outline_color"),
                DatapackJsonReader.readInt(tracker, "priority", 0),
                DatapackJsonReader.readBoolean(tracker, "hidden", false)
        );
    }

    private static QuestDefinition.Step readTrackerStep(JsonObject step) {
        return new QuestDefinition.Step(
                DatapackJsonReader.readString(step, "text"),
                DatapackJsonReader.readString(step, "text_key"),
                DatapackJsonReader.readBoolean(step, "show_progress", true),
                (float) DatapackJsonReader.readDouble(step, "progress", -1.0D),
                readStringMap(DatapackJsonReader.readObject(step, "metadata"))
        );
    }

    private static Map<String, QuestDefinition.Stage> readStages(
            ResourceLocation location,
            JsonObject root,
            ResourceLocation defaultQuestId) {
        JsonElement element = root.get("stages");
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }
        if (!element.isJsonObject()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest stages", "stages must be an object keyed by stage id.");
            return Map.of();
        }

        Map<String, QuestDefinition.Stage> stages = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String stageId = entry.getKey() == null ? "" : entry.getKey().trim();
            if (stageId.isBlank()) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest stages", "stage id must not be blank.");
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest stage \"" + stageId + "\"", "stage must be an object.");
                continue;
            }
            if (stages.containsKey(stageId)) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest stage \"" + stageId + "\"", "duplicate stage id; later duplicate is ignored.");
                continue;
            }
            stages.put(stageId, readStage(location, stageId, entry.getValue().getAsJsonObject(), defaultQuestId));
        }
        return java.util.Collections.unmodifiableMap(stages);
    }

    private static QuestDefinition.Stage readStage(
            ResourceLocation location,
            String stageId,
            JsonObject stage,
            ResourceLocation defaultQuestId) {
        String context = "quest stage \"" + stageId + "\"";
        JsonObject completion = DatapackJsonReader.readObject(stage, "completion");
        return new QuestDefinition.Stage(
                stageId,
                DatapackJsonReader.readStringList(stage, "objective", "objectives"),
                readStagePredicates(location, context + ".complete_when", stage.get("complete_when"), defaultQuestId),
                QuestDefinition.CompletionMode.bySerializedName(firstNonBlank(
                        completion == null ? "" : DatapackJsonReader.readString(completion, "mode"),
                        DatapackJsonReader.readString(stage, "completion_mode"))),
                completion == null
                        ? DatapackJsonReader.readInt(stage, "completion_count", 1)
                        : DatapackJsonReader.readInt(completion, "count", 1),
                firstNonBlank(
                        DatapackJsonReader.readString(stage, "next"),
                        DatapackJsonReader.readString(stage, "next_stage")),
                readActionsFromKey(location, context + ".entry_actions", stage, "entry_actions", defaultQuestId),
                readActionsFromKey(location, context + ".exit_actions", stage, "exit_actions", defaultQuestId),
                readStageBranches(location, context, stage.get("branches"), defaultQuestId),
                readBonusOutcomes(location, context, stage.get("bonuses"), defaultQuestId),
                readStringMap(DatapackJsonReader.readObject(stage, "metadata")));
    }

    private static List<QuestDefinition.StageBranch> readStageBranches(
            ResourceLocation location,
            String context,
            JsonElement element,
            ResourceLocation defaultQuestId) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".branches", "branches must be an array.");
            return List.of();
        }
        List<QuestDefinition.StageBranch> branches = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonObject()) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".branches[" + index + "]", "branch must be an object.");
                index++;
                continue;
            }
            JsonObject branch = child.getAsJsonObject();
            String id = firstNonBlank(DatapackJsonReader.readString(branch, "id"), "branch_" + index);
            if (!ids.add(id)) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".branches[" + index + "]", "duplicate branch id; later duplicate is ignored.");
                index++;
                continue;
            }
            branches.add(new QuestDefinition.StageBranch(
                    id,
                    DatapackJsonReader.readString(branch, "label"),
                    DatapackJsonReader.readString(branch, "label_key"),
                    DialogueCondition.readList(location, context + ".branches[" + id + "]", branch, defaultQuestId),
                    readActionsFromKey(location, context + ".branches[" + id + "].actions", branch, "actions", defaultQuestId),
                    firstNonBlank(
                            DatapackJsonReader.readString(branch, "next"),
                            DatapackJsonReader.readString(branch, "next_stage")),
                    readStageBranchBlockers(location, context + ".branches[" + id + "]", branch.get("blocked_by"), defaultQuestId)));
            index++;
        }
        return List.copyOf(branches);
    }

    private static List<QuestDefinition.StageBranchBlocker> readStageBranchBlockers(
            ResourceLocation location,
            String context,
            JsonElement element,
            ResourceLocation defaultQuestId) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".blocked_by", "blocked_by must be an array.");
            return List.of();
        }
        List<QuestDefinition.StageBranchBlocker> blockers = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonObject()) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".blocked_by[" + index + "]", "blocked_by entry must be an object.");
                index++;
                continue;
            }
            JsonObject blocker = child.getAsJsonObject();
            blockers.add(new QuestDefinition.StageBranchBlocker(
                    DialogueCondition.readList(location, context + ".blocked_by[" + index + "]", blocker, defaultQuestId),
                    DatapackJsonReader.readString(blocker, "reason"),
                    DatapackJsonReader.readString(blocker, "reason_key")));
            index++;
        }
        return List.copyOf(blockers);
    }

    private static List<QuestDefinition.Trigger> readTriggers(
            ResourceLocation location,
            JsonObject root,
            ResourceLocation defaultQuestId) {
        JsonElement element = root.get("triggers");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            return List.of();
        }

        List<QuestDefinition.Trigger> triggers = new ArrayList<>();
        Set<String> triggerIds = new LinkedHashSet<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) {
                readTrigger(location, child.getAsJsonObject(), index, defaultQuestId).ifPresent(trigger -> {
                    if (!triggerIds.add(trigger.id())) {
                        DatapackDiagnostics.warnInvalidDialogueCondition(
                                location,
                                "quest trigger \"" + trigger.id() + "\"",
                                "duplicate trigger id; later duplicate is ignored.");
                        return;
                    }
                    triggers.add(trigger);
                });
            }
            index++;
        }
        return List.copyOf(triggers);
    }

    private static Optional<QuestDefinition.Trigger> readTrigger(
            ResourceLocation location,
            JsonObject trigger,
            int index,
            ResourceLocation defaultQuestId) {
        QuestDefinition.TriggerEvent event = QuestDefinition.TriggerEvent.bySerializedName(
                DatapackJsonReader.readString(trigger, "event"));
        if (event == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest trigger", "unknown trigger event.");
            return Optional.empty();
        }
        String id = DatapackJsonReader.readString(trigger, "id");
        if (id.isBlank()) {
            id = event.name().toLowerCase(Locale.ROOT) + "_" + index;
        }
        List<VillagerActionDefinition> actions = VillagerActionDefinition.readListOrInline(
                location,
                "quest trigger \"" + id + "\"",
                trigger,
                defaultQuestId);
        if (actions.isEmpty()) {
            return Optional.empty();
        }
        boolean repeatable = defaultTriggerRepeatable(actions);
        if (trigger.has("repeatable")) {
            repeatable = DatapackJsonReader.readBoolean(trigger, "repeatable", repeatable);
        }

        return Optional.of(new QuestDefinition.Trigger(
                id,
                event,
                DialogueCondition.readList(location, "quest trigger \"" + id + "\"", trigger, defaultQuestId),
                actions,
                readTriggerStages(trigger),
                DatapackJsonReader.readDurationTicks(trigger, "cooldown", defaultTriggerCooldown(event)),
                DatapackJsonReader.readDouble(trigger, "radius", 10.0D),
                repeatable,
                DatapackJsonReader.readInt(trigger, "priority", 0),
                DatapackJsonReader.readDouble(trigger, "chance", 1.0D),
                DatapackJsonReader.readBoolean(trigger, "exclusive", false),
                DatapackJsonReader.readInt(trigger, "weight", 1)
        ));
    }

    private static Set<String> readTriggerStages(JsonObject trigger) {
        Set<String> stages = new LinkedHashSet<>();
        for (String stage : DatapackJsonReader.readStringList(trigger, "stage", "stages")) {
            String normalized = stage == null ? "" : stage.trim();
            if (!normalized.isBlank()) {
                stages.add(normalized);
            }
        }
        return Set.copyOf(stages);
    }

    private static boolean defaultTriggerRepeatable(List<VillagerActionDefinition> actions) {
        return actions.stream().noneMatch(action -> action.kind() == VillagerActionDefinition.Kind.FORCED_DIALOGUE);
    }

    private static long defaultTriggerCooldown(QuestDefinition.TriggerEvent event) {
        return QuestTriggerRegistry.defaultCooldownTicks(event);
    }

    private static ResourceLocation readMemoryEvent(JsonObject rewards) {
        String value = DatapackJsonReader.readString(rewards, "memory_event");
        if (value.isBlank()) {
            return null;
        }
        return VillageEventMemory.parseTagId(value).orElse(null);
    }

    private static QuestDefinition.Dialogue readDialogue(ResourceLocation location, ResourceLocation questId, JsonObject root) {
        JsonObject dialogue = DatapackJsonReader.readObject(root, "dialogue");
        if (dialogue == null) {
            return QuestDefinition.Dialogue.EMPTY;
        }
        return new QuestDefinition.Dialogue(
                readLines(dialogue, "start"),
                readLineKeys(dialogue, "start"),
                readLines(dialogue, "reminder"),
                readLineKeys(dialogue, "reminder"),
                readLines(dialogue, "turn_in"),
                readLineKeys(dialogue, "turn_in"),
                readLines(dialogue, "already_completed"),
                readLineKeys(dialogue, "already_completed"),
                readLines(dialogue, "unavailable"),
                readLineKeys(dialogue, "unavailable"),
                readLines(dialogue, "inactive"),
                readLineKeys(dialogue, "inactive"),
                readLines(dialogue, "missing_target"),
                readLineKeys(dialogue, "missing_target"),
                readLines(dialogue, "missing_proof"),
                readLineKeys(dialogue, "missing_proof"),
                readLines(dialogue, "locate_failed"),
                readLineKeys(dialogue, "locate_failed"),
                readDialogueVariants(location, questId, dialogue)
        );
    }

    private static Map<String, List<DialogueTextVariant>> readDialogueVariants(
            ResourceLocation location, ResourceLocation questId, JsonObject dialogue) {
        Map<String, List<DialogueTextVariant>> variants = new LinkedHashMap<>();
        for (String slot : List.of("start", "reminder", "turn_in", "already_completed", "unavailable",
                "inactive", "missing_target", "missing_proof", "locate_failed")) {
            JsonElement element = dialogue.get(slot);
            if (element == null || !containsDialogueVariantObject(element)) {
                continue;
            }
            List<DialogueTextVariant> parsed = DialogueTextVariant.read(
                    location, "quest dialogue", "dialogue." + slot, questId + "/dialogue/" + slot,
                    element, questId, DialogueEntryMetadata.EMPTY, DialogueUsagePolicy.DEFAULT);
            if (!parsed.isEmpty()) {
                variants.put(slot, parsed);
            }
        }
        return Map.copyOf(variants);
    }

    private static boolean containsDialogueVariantObject(JsonElement element) {
        if (element.isJsonObject()) {
            return true;
        }
        return element.isJsonArray() && element.getAsJsonArray().asList().stream().anyMatch(JsonElement::isJsonObject);
    }

    private static QuestDefinition.Links readLinks(JsonObject root) {
        JsonObject links = DatapackJsonReader.readObject(root, "links");
        if (links == null) {
            return QuestDefinition.Links.EMPTY;
        }
        return new QuestDefinition.Links(
                DatapackJsonReader.readResourceLocation(links, "dialogue_tree").orElse(null),
                DatapackJsonReader.readString(links, "offer"),
                DatapackJsonReader.readString(links, "reminder"),
                DatapackJsonReader.readString(links, "turn_in"),
                DatapackJsonReader.readStringList(links, "forced_dialogue")
        );
    }

    private static List<String> readLineKeys(JsonObject root, String key) {
        List<String> keys = new ArrayList<>();
        keys.addAll(DatapackJsonReader.readStringList(root, key + "_key", key + "_keys"));
        JsonElement element = root.get(key);
        if (element != null && element.isJsonObject()) {
            keys.addAll(DatapackJsonReader.readStringList(element.getAsJsonObject(), "text_key", "text_keys"));
        }
        return keys.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }
}
