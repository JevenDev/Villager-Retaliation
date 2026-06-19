package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestMetadata;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestObjective;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestProvider;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestRewards;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestStage;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTrigger;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestUi;
import com.jvn.villagerretaliation.quest.compiled.QuestSourcePointer;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Resource;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class QuestV2Compiler {
    private QuestV2Compiler() {
    }

    public static Optional<CompiledQuest> compile(QuestV2Resource resource, QuestResourceEnvelope envelope) {
        if (resource == null || envelope == null) {
            return Optional.empty();
        }

        QuestSourcePointer source = QuestSourcePointer.from(envelope);
        CanonicalQuestJson canonical = canonicalize(resource, source);
        QuestDefinition definition = VillagerQuestResources.readCanonicalQuest(
                envelope.location(),
                canonical.root(),
                resource.id());
        if (definition == null) {
            DatapackDiagnostics.warnQuestV2Validation(
                    envelope.location(),
                    "",
                    "quest module v2 could not compile to the canonical quest runtime model.",
                    "Check objective runtime requirements and action payloads.",
                    Set.of(resource.id().toString()));
            return Optional.empty();
        }
        return Optional.of(compileDefinition(definition, source, canonical));
    }

    private static CanonicalQuestJson canonicalize(QuestV2Resource resource, QuestSourcePointer source) {
        JsonObject root = new JsonObject();
        root.addProperty("id", resource.id().toString());
        addDisplay(root, resource);
        addQuestIdentity(root, resource);
        addOffer(root, resource);
        addRules(root, resource);
        addTracker(root, resource);

        Map<String, QuestSourcePointer> objectiveSources = new LinkedHashMap<>();
        Map<String, QuestSourcePointer> stageSources = new LinkedHashMap<>();
        Map<String, QuestSourcePointer> triggerSources = new LinkedHashMap<>();
        JsonArray objectives = new JsonArray();
        JsonObject stages = new JsonObject();
        JsonArray triggers = new JsonArray();

        Map<String, Integer> stageIndexes = stageIndexes(resource);
        List<VillagerActionDefinition> globalStageEnter = lifecycleActions(
                resource,
                "on_stage_enter",
                source.child("lifecycle", "on_stage_enter"));
        List<VillagerActionDefinition> globalStageExit = lifecycleActions(
                resource,
                "on_stage_exit",
                source.child("lifecycle", "on_stage_exit"));

        for (QuestV2Resource.Stage stage : orderedStages(resource)) {
            int stageIndex = stageIndexes.getOrDefault(stage.id(), 0);
            QuestSourcePointer stageSource = source.child("stages", Integer.toString(stageIndex));
            stageSources.put(stage.id(), stageSource);
            stages.add(stage.id(), canonicalStage(
                    resource,
                    stage,
                    stageSource,
                    objectives,
                    objectiveSources,
                    globalStageEnter,
                    globalStageExit));
            appendStageEvents(resource, source, stage, stageIndex, triggers, triggerSources);
        }

        root.add("objectives", objectives);
        root.add("stages", stages);
        appendLifecycleTriggers(resource, source, triggers, triggerSources);
        appendRootEvents(resource, source, triggers, triggerSources);
        if (triggers.size() > 0) {
            root.add("triggers", triggers);
        }

        JsonObject rewards = canonicalRewards(
                resource.id(),
                source.resource(),
                "quest module v2 rewards",
                resource.rewards());
        if (rewards.size() > 0) {
            root.add("rewards", rewards);
        }
        return new CanonicalQuestJson(root, objectiveSources, stageSources, triggerSources);
    }

    private static void addDisplay(JsonObject root, QuestV2Resource resource) {
        JsonObject display = new JsonObject();
        putString(display, "title", firstNonBlank(
                metadataString(resource, "title"),
                uiString(resource.ui(), "title")));
        putString(display, "description", firstNonBlank(
                metadataString(resource, "description"),
                uiString(resource.ui(), "description")));
        putString(display, "title_key", firstNonBlank(
                metadataString(resource, "title_key"),
                uiString(resource.ui(), "title_key")));
        putString(display, "description_key", firstNonBlank(
                metadataString(resource, "description_key"),
                uiString(resource.ui(), "description_key")));
        if (display.size() > 0) {
            root.add("display", display);
        }
    }

    private static void addQuestIdentity(JsonObject root, QuestV2Resource resource) {
        putString(root, "questline", metadataString(resource, "questline"));
        putString(root, "parent", firstNonBlank(
                metadataString(resource, "parent"),
                firstResourceLocationString(resource.availability().data(), "prerequisites")));
        JsonArray tags = stringArray(resource.metadata().get("tags"));
        if (tags.size() > 0) {
            root.add("tags", tags);
        }
    }

    private static void addOffer(JsonObject root, QuestV2Resource resource) {
        JsonObject offer = new JsonObject();
        JsonObject filters = DatapackJsonReader.readObject(resource.provider().data(), "filters");
        if (filters != null) {
            copyIfPresent(filters, offer, "professions");
            copyIfPresent(filters, offer, "min_villager_level");
            copyIfPresent(filters, offer, "skills");
        }
        JsonArray conditions = jsonObjectArray(resource.availability().conditions());
        if (conditions.size() > 0) {
            offer.add("conditions", conditions);
        }
        if (offer.size() > 0) {
            root.add("offer", offer);
        }
    }

    private static void addRules(JsonObject root, QuestV2Resource resource) {
        JsonObject rules = new JsonObject();
        JsonObject availability = resource.availability().data();
        if (availability.has("repeatable")) {
            copyIfPresent(availability, rules, "repeatable");
        }
        copyIfPresent(availability, rules, "cooldown_ticks", "completion_cooldown_ticks");
        if (availability.has("exclusive_group")) {
            JsonObject branch = new JsonObject();
            copyIfPresent(availability, branch, "exclusive_group");
            rules.add("branch", branch);
        }
        if (rules.size() > 0) {
            root.add("rules", rules);
        }
    }

    private static void addTracker(JsonObject root, QuestV2Resource resource) {
        JsonObject tracker = new JsonObject();
        putString(tracker, "title", firstNonBlank(
                uiString(resource.ui(), "title"),
                metadataString(resource, "title")));
        putString(tracker, "title_key", firstNonBlank(
                uiString(resource.ui(), "title_key"),
                metadataString(resource, "title_key")));
        JsonObject steps = new JsonObject();
        JsonObject rootStep = trackerStep(resource.ui());
        if (rootStep.size() > 0) {
            steps.add("choice", rootStep.deepCopy());
            steps.add("fact", rootStep.deepCopy());
            steps.add("inactive", rootStep.deepCopy());
        }
        for (QuestV2Resource.Stage stage : orderedStages(resource)) {
            JsonObject stageStep = trackerStep(stage.ui());
            if (stageStep.size() > 0) {
                steps.add(stage.id(), stageStep);
            }
        }
        if (steps.size() > 0) {
            tracker.add("steps", steps);
        }
        if (tracker.size() > 0) {
            root.add("tracker", tracker);
        }
    }

    private static JsonObject canonicalStage(
            QuestV2Resource resource,
            QuestV2Resource.Stage stage,
            QuestSourcePointer stageSource,
            JsonArray objectives,
            Map<String, QuestSourcePointer> objectiveSources,
            List<VillagerActionDefinition> globalStageEnter,
            List<VillagerActionDefinition> globalStageExit) {
        JsonObject canonical = new JsonObject();
        JsonArray objectiveIds = new JsonArray();
        Map<String, String> canonicalIds = new LinkedHashMap<>();
        for (int index = 0; index < stage.objectives().size(); index++) {
            QuestV2Resource.Objective objective = stage.objectives().get(index);
            String canonicalId = canonicalObjectiveId(stage, objective);
            canonicalIds.put(objective.id(), canonicalId);
            objectiveIds.add(canonicalId);
            objectives.add(canonicalObjective(resource, stage, objective, canonicalId));
            objectiveSources.put(canonicalId, stageSource.child("objectives", Integer.toString(index)));
        }
        canonical.add("objectives", objectiveIds);

        JsonArray completeWhen = new JsonArray();
        List<String> predicates = stage.completeWhenObjectives().isEmpty()
                ? stage.objectives().stream()
                        .filter(objective -> !objective.optional())
                        .map(QuestV2Resource.Objective::id)
                        .toList()
                : stage.completeWhenObjectives();
        for (String localObjectiveId : predicates) {
            String canonicalId = canonicalIds.get(localObjectiveId);
            if (canonicalId != null && !canonicalId.isBlank()) {
                completeWhen.add(canonicalId);
            }
        }
        if (completeWhen.size() > 0) {
            canonical.add("complete_when", completeWhen);
        }
        if (!stage.next().stage().isBlank()) {
            canonical.addProperty("next", stage.next().stage());
        }

        JsonArray entryActions = new JsonArray();
        addActionDefinitions(entryActions, globalStageEnter);
        addActionObjects(entryActions, stage.entryActions());
        addActionObjects(entryActions, rewardActions(stage.rewards()));
        if (entryActions.size() > 0) {
            canonical.add("entry_actions", entryActions);
        }

        JsonArray exitActions = new JsonArray();
        addActionObjects(exitActions, stage.exitActions());
        addActionDefinitions(exitActions, globalStageExit);
        if (exitActions.size() > 0) {
            canonical.add("exit_actions", exitActions);
        }
        return canonical;
    }

    private static JsonObject canonicalObjective(
            QuestV2Resource resource,
            QuestV2Resource.Stage stage,
            QuestV2Resource.Objective objective,
            String canonicalId) {
        JsonObject canonical = objective.data().deepCopy();
        canonical.addProperty("id", canonicalId);
        canonical.addProperty("type", objective.type());
        if (objective.optional()) {
            canonical.addProperty("optional", true);
        }
        flattenNestedObject(canonical, "target");
        flattenNestedObject(canonical, "location");
        addChoiceRuntimeDefault(canonical, objective);
        addObjectiveTracker(canonical);
        if ((objective.type().equals("fact") || objective.type().equals("choice")) && !canonical.has("quest")) {
            canonical.addProperty("quest", resource.id().toString());
        }
        return canonical;
    }

    private static void addChoiceRuntimeDefault(JsonObject canonical, QuestV2Resource.Objective objective) {
        if (!"choice".equals(objective.type()) || hasFactDefinition(canonical)) {
            return;
        }
        canonical.addProperty("choice", objective.id());
    }

    private static boolean hasFactDefinition(JsonObject object) {
        return hasAny(
                object,
                "tag",
                "tags",
                "fact_tag",
                "quest_tag",
                "key",
                "variable",
                "counter",
                "fact",
                "stage",
                "stages",
                "value",
                "values",
                "choice",
                "choices",
                "min",
                "max");
    }

    private static void addObjectiveTracker(JsonObject canonical) {
        JsonObject tracker = optionalObject(canonical.get("tracker"));
        if (tracker == null) {
            tracker = new JsonObject();
        } else {
            tracker = tracker.deepCopy();
        }
        JsonObject objectiveUi = optionalObject(canonical.get("ui"));
        putStringIfBlank(tracker, "text", uiString(objectiveUi, "tracker_text"));
        putStringIfBlank(tracker, "text_key", uiString(objectiveUi, "tracker_text_key"));
        if (tracker.size() > 0) {
            canonical.add("tracker", tracker);
        }
    }

    private static void appendLifecycleTriggers(
            QuestV2Resource resource,
            QuestSourcePointer source,
            JsonArray triggers,
            Map<String, QuestSourcePointer> triggerSources) {
        appendLifecycleTrigger(
                resource,
                source,
                triggers,
                triggerSources,
                "on_start",
                QuestDefinition.TriggerEvent.STARTED);
        appendLifecycleTrigger(
                resource,
                source,
                triggers,
                triggerSources,
                "on_complete",
                QuestDefinition.TriggerEvent.COMPLETED);
        appendLifecycleTrigger(
                resource,
                source,
                triggers,
                triggerSources,
                "on_abandon",
                QuestDefinition.TriggerEvent.ABANDONED);
        appendLifecycleTrigger(
                resource,
                source,
                triggers,
                triggerSources,
                "on_expire",
                QuestDefinition.TriggerEvent.EXPIRED);
        appendLifecycleTrigger(
                resource,
                source,
                triggers,
                triggerSources,
                "on_fail",
                QuestDefinition.TriggerEvent.ABANDONED);
    }

    private static void appendLifecycleTrigger(
            QuestV2Resource resource,
            QuestSourcePointer source,
            JsonArray triggers,
            Map<String, QuestSourcePointer> triggerSources,
            String hookId,
            QuestDefinition.TriggerEvent event) {
        QuestV2Resource.Hook hook = resource.lifecycle().hooks().get(hookId);
        if (hook == null) {
            return;
        }
        JsonArray actions = actionArray(resource.id(), hook.actions(), hook.transition());
        if (actions.size() == 0) {
            return;
        }
        String triggerId = "lifecycle." + hookId;
        JsonObject trigger = new JsonObject();
        trigger.addProperty("id", triggerId);
        trigger.addProperty("event", QuestTriggerRegistry.canonicalEventId(event));
        trigger.add("actions", actions);
        triggers.add(trigger);
        triggerSources.put(triggerId, source.child("lifecycle", hookId));
    }

    private static List<VillagerActionDefinition> lifecycleActions(
            QuestV2Resource resource,
            String hookId,
            QuestSourcePointer source) {
        QuestV2Resource.Hook hook = resource.lifecycle().hooks().get(hookId);
        if (hook == null) {
            return List.of();
        }
        JsonArray actions = actionArray(resource.id(), hook.actions(), hook.transition());
        if (actions.size() == 0) {
            return List.of();
        }
        JsonObject wrapper = new JsonObject();
        wrapper.add("actions", actions);
        return VillagerActionDefinition.readList(
                source.resource(),
                "quest module v2 lifecycle " + hookId,
                wrapper,
                resource.id());
    }

    private static void appendRootEvents(
            QuestV2Resource resource,
            QuestSourcePointer source,
            JsonArray triggers,
            Map<String, QuestSourcePointer> triggerSources) {
        for (int index = 0; index < resource.events().size(); index++) {
            QuestV2Resource.Event event = resource.events().get(index);
            appendEventTrigger(
                    resource.id(),
                    event,
                    "",
                    event.id(),
                    source.child("events", Integer.toString(index)),
                    triggers,
                    triggerSources);
        }
    }

    private static void appendStageEvents(
            QuestV2Resource resource,
            QuestSourcePointer source,
            QuestV2Resource.Stage stage,
            int stageIndex,
            JsonArray triggers,
            Map<String, QuestSourcePointer> triggerSources) {
        for (int index = 0; index < stage.events().size(); index++) {
            QuestV2Resource.Event event = stage.events().get(index);
            appendEventTrigger(
                    resource.id(),
                    event,
                    stage.id(),
                    stage.id() + "." + event.id(),
                    source.child("stages", Integer.toString(stageIndex), "events", Integer.toString(index)),
                    triggers,
                    triggerSources);
        }
    }

    private static void appendEventTrigger(
            ResourceLocation questId,
            QuestV2Resource.Event event,
            String ownerStage,
            String triggerId,
            QuestSourcePointer source,
            JsonArray triggers,
            Map<String, QuestSourcePointer> triggerSources) {
        JsonArray actions = actionArray(questId, event.actions(), event.transition());
        if (actions.size() == 0) {
            return;
        }
        JsonObject trigger = new JsonObject();
        trigger.addProperty("id", triggerId);
        trigger.addProperty("event", event.trigger());
        JsonArray stages = stringArray(event.stages());
        if (stages.size() == 0 && ownerStage != null && !ownerStage.isBlank()) {
            stages.add(ownerStage);
        }
        if (stages.size() > 0) {
            trigger.add("stages", stages);
        }
        JsonArray conditions = jsonObjectArray(event.conditions());
        if (conditions.size() > 0) {
            trigger.add("conditions", conditions);
        }
        trigger.add("actions", actions);
        copyIfPresent(event.data(), trigger, "cooldown_ticks");
        copyIfPresent(event.data(), trigger, "radius");
        copyIfPresent(event.data(), trigger, "repeatable");
        triggers.add(trigger);
        triggerSources.put(triggerId, source);
    }

    private static JsonArray actionArray(
            ResourceLocation questId,
            List<JsonObject> rawActions,
            QuestV2Resource.Transition transition) {
        JsonArray actions = new JsonArray();
        addActionObjects(actions, rawActions);
        addActionObjects(actions, transitionActions(questId, transition));
        return actions;
    }

    private static List<JsonObject> transitionActions(ResourceLocation questId, QuestV2Resource.Transition transition) {
        if (transition == null || transition.isEmpty()) {
            return List.of();
        }
        if (!transition.stage().isBlank()) {
            JsonObject action = new JsonObject();
            action.addProperty("type", "set_variable");
            action.addProperty("quest", questId.toString());
            action.addProperty("scope", "quest");
            action.addProperty("stage", transition.stage());
            action.addProperty("flash_tracker", true);
            return List.of(action);
        }
        if (transition.complete()) {
            return List.of(questAction(questId, "turn_in"));
        }
        if (transition.abandon() || transition.fail()) {
            return List.of(questAction(questId, "abandon"));
        }
        return List.of();
    }

    private static JsonObject questAction(ResourceLocation questId, String actionId) {
        JsonObject action = new JsonObject();
        action.addProperty("type", "quest");
        action.addProperty("quest", questId.toString());
        action.addProperty("action", actionId);
        return action;
    }

    private static JsonObject canonicalRewards(
            ResourceLocation questId,
            ResourceLocation location,
            String context,
            QuestV2Resource.Rewards rewards) {
        JsonObject canonical = new JsonObject();
        if (rewards == null) {
            return canonical;
        }
        JsonObject data = rewards.data();
        putInt(canonical, "experience", DatapackJsonReader.readInt(data, "experience", 0));
        putInt(canonical, "reputation", DatapackJsonReader.readInt(data, "reputation", 0));
        putInt(canonical, "gossip_reputation", DatapackJsonReader.readInt(data, "gossip_reputation", 0));
        copyIfPresent(data, canonical, "loot_table");
        copyIfPresent(data, canonical, "memory_event");

        JsonObject wrapper = new JsonObject();
        wrapper.add("actions", jsonObjectArray(rewards.actions()));
        for (VillagerActionDefinition action : VillagerActionDefinition.readList(location, context, wrapper, questId)) {
            switch (action.kind()) {
                case EXPERIENCE -> putInt(canonical, "experience",
                        DatapackJsonReader.readInt(canonical, "experience", 0) + Math.max(0, action.amount()));
                case REPUTATION -> putInt(canonical, "reputation",
                        DatapackJsonReader.readInt(canonical, "reputation", 0) + action.amount());
                case GOSSIP -> putInt(canonical, "gossip_reputation",
                        DatapackJsonReader.readInt(canonical, "gossip_reputation", 0) + action.amount());
                case MEMORY -> {
                    if (action.memoryTag() != null) {
                        canonical.addProperty("memory_event", action.memoryTag().toString());
                    }
                }
                case LOOT -> {
                    if (action.lootTable() != null) {
                        canonical.addProperty("loot_table", action.lootTable().toString());
                    }
                }
                default -> {
                }
            }
        }
        return canonical;
    }

    private static List<JsonObject> rewardActions(QuestV2Resource.Rewards rewards) {
        if (rewards == null) {
            return List.of();
        }
        List<JsonObject> actions = new ArrayList<>();
        for (JsonObject action : rewards.actions()) {
            actions.add(action.deepCopy());
        }
        JsonObject data = rewards.data();
        addRewardAction(actions, "experience", DatapackJsonReader.readInt(data, "experience", 0), "");
        addRewardAction(actions, "reputation", DatapackJsonReader.readInt(data, "reputation", 0), "");
        addRewardAction(actions, "gossip", DatapackJsonReader.readInt(data, "gossip_reputation", 0), "");
        addRewardAction(actions, "memory", 0, DatapackJsonReader.readString(data, "memory_event"));
        addRewardAction(actions, "loot", 0, DatapackJsonReader.readString(data, "loot_table"));
        return List.copyOf(actions);
    }

    private static void addRewardAction(List<JsonObject> actions, String type, int amount, String resource) {
        if (amount == 0 && (resource == null || resource.isBlank())) {
            return;
        }
        JsonObject action = new JsonObject();
        action.addProperty("type", type);
        if (amount != 0) {
            action.addProperty("amount", amount);
        }
        if ("memory".equals(type)) {
            action.addProperty("memory_event", resource);
        } else if ("loot".equals(type)) {
            action.addProperty("loot_table", resource);
        }
        actions.add(action);
    }

    private static CompiledQuest compileDefinition(
            QuestDefinition definition,
            QuestSourcePointer source,
            CanonicalQuestJson canonical) {
        List<CompiledQuestObjective> objectives = compileObjectives(definition, canonical.objectiveSources(), source);
        Map<String, CompiledQuestObjective> objectivesById = indexObjectives(objectives);
        List<CompiledQuestStage> stages = compileStages(definition, objectivesById, canonical.stageSources(), source);
        Map<String, CompiledQuestStage> stagesById = indexStages(stages);
        List<CompiledQuestTrigger> triggers = compileTriggers(definition, canonical.triggerSources(), source);
        QuestTriggerIndex triggerIndex = QuestTriggerRegistry.index(triggers);

        return new CompiledQuest(
                definition.id(),
                source,
                definition,
                new CompiledQuestMetadata(
                        definition.title(),
                        definition.description(),
                        definition.titleKey(),
                        definition.descriptionKey(),
                        definition.questline(),
                        definition.tags(),
                        definition.parent(),
                        definition.metadata()),
                new CompiledQuestProvider(definition.offer()),
                definition.target(),
                definition.rules(),
                new CompiledQuestUi(definition.tracker(), definition.dialogue(), definition.links()),
                objectives,
                objectivesById,
                stages,
                stagesById,
                triggers,
                triggerIndex.triggersByEvent(),
                triggerIndex,
                new CompiledQuestRewards(definition.rewards()));
    }

    private static List<CompiledQuestObjective> compileObjectives(
            QuestDefinition definition,
            Map<String, QuestSourcePointer> sources,
            QuestSourcePointer source) {
        List<CompiledQuestObjective> objectives = new ArrayList<>();
        int index = 0;
        for (QuestDefinition.Objective objective : definition.objectives()) {
            objectives.add(new CompiledQuestObjective(
                    objective.id(),
                    index,
                    objective,
                    sources.getOrDefault(objective.id(), source.child("objectives", Integer.toString(index)))));
            index++;
        }
        return List.copyOf(objectives);
    }

    private static Map<String, CompiledQuestObjective> indexObjectives(List<CompiledQuestObjective> objectives) {
        Map<String, CompiledQuestObjective> byId = new LinkedHashMap<>();
        for (CompiledQuestObjective objective : objectives) {
            byId.put(objective.id(), objective);
        }
        return byId;
    }

    private static List<CompiledQuestStage> compileStages(
            QuestDefinition definition,
            Map<String, CompiledQuestObjective> objectivesById,
            Map<String, QuestSourcePointer> sources,
            QuestSourcePointer source) {
        List<CompiledQuestStage> stages = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, QuestDefinition.Stage> entry : definition.stages().entrySet()) {
            stages.add(new CompiledQuestStage(
                    entry.getKey(),
                    index,
                    entry.getValue(),
                    resolveStageObjectives(entry.getValue(), objectivesById),
                    sources.getOrDefault(entry.getKey(), source.child("stages", entry.getKey()))));
            index++;
        }
        return List.copyOf(stages);
    }

    private static List<CompiledQuestObjective> resolveStageObjectives(
            QuestDefinition.Stage stage,
            Map<String, CompiledQuestObjective> objectivesById) {
        return stage.objectives().stream()
                .map(objectivesById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Map<String, CompiledQuestStage> indexStages(List<CompiledQuestStage> stages) {
        Map<String, CompiledQuestStage> byId = new LinkedHashMap<>();
        for (CompiledQuestStage stage : stages) {
            byId.put(stage.id(), stage);
        }
        return byId;
    }

    private static List<CompiledQuestTrigger> compileTriggers(
            QuestDefinition definition,
            Map<String, QuestSourcePointer> sources,
            QuestSourcePointer source) {
        List<CompiledQuestTrigger> triggers = new ArrayList<>();
        int index = 0;
        for (QuestDefinition.Trigger trigger : definition.triggers()) {
            triggers.add(new CompiledQuestTrigger(
                    trigger.id(),
                    index,
                    trigger,
                    sources.getOrDefault(trigger.id(), source.child("triggers", Integer.toString(index)))));
            index++;
        }
        return List.copyOf(triggers);
    }

    private static List<QuestV2Resource.Stage> orderedStages(QuestV2Resource resource) {
        if (resource.entryStage().isBlank() || resource.stages().isEmpty()) {
            return resource.stages();
        }
        List<QuestV2Resource.Stage> ordered = new ArrayList<>();
        QuestV2Resource.Stage entry = resource.stagesById().get(resource.entryStage());
        if (entry != null) {
            ordered.add(entry);
        }
        for (QuestV2Resource.Stage stage : resource.stages()) {
            if (entry == null || !stage.id().equals(entry.id())) {
                ordered.add(stage);
            }
        }
        return List.copyOf(ordered);
    }

    private static Map<String, Integer> stageIndexes(QuestV2Resource resource) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < resource.stages().size(); index++) {
            indexes.put(resource.stages().get(index).id(), index);
        }
        return indexes;
    }

    private static String canonicalObjectiveId(QuestV2Resource.Stage stage, QuestV2Resource.Objective objective) {
        return stage.id() + "." + objective.id();
    }

    private static JsonObject trackerStep(QuestV2Resource.UiHints ui) {
        JsonObject step = new JsonObject();
        putString(step, "text", ui.trackerText());
        putString(step, "text_key", ui.trackerTextKey());
        return step;
    }

    private static void addActionDefinitions(JsonArray target, List<VillagerActionDefinition> actions) {
        for (VillagerActionDefinition action : actions) {
            JsonObject object = new JsonObject();
            object.addProperty("type", action.kind().serializedName());
            if (action.questId() != null) {
                object.addProperty("quest", action.questId().toString());
            }
            if (action.questAction() != VillagerActionDefinition.QuestAction.NONE) {
                object.addProperty("action", action.questAction().name().toLowerCase(java.util.Locale.ROOT));
            }
            if (action.amount() != 0) {
                object.addProperty("amount", action.amount());
            }
            if (action.memoryTag() != null) {
                object.addProperty("memory_event", action.memoryTag().toString());
            }
            if (action.lootTable() != null) {
                object.addProperty("loot_table", action.lootTable().toString());
            }
            if (!action.notificationTrigger().isBlank()) {
                object.addProperty("notification", action.notificationTrigger());
            }
            if (!action.text().isBlank()) {
                object.addProperty("text", action.text());
            }
            if (!action.forcedDialogue().isBlank()) {
                object.addProperty("forced_dialogue", action.forcedDialogue());
            }
            object.addProperty("flash_tracker", action.flashTracker());
            if (action.factScope() != null) {
                object.addProperty("scope", action.factScope().name().toLowerCase(java.util.Locale.ROOT));
            }
            if (action.factTag() != null) {
                object.addProperty("fact_tag", action.factTag().toString());
            }
            if (!action.factKey().isBlank()) {
                object.addProperty("key", action.factKey());
            }
            if (!action.factValue().isBlank()) {
                object.addProperty("value", action.factValue());
            }
            target.add(object);
        }
    }

    private static void addActionObjects(JsonArray target, List<JsonObject> actions) {
        if (actions == null) {
            return;
        }
        for (JsonObject action : actions) {
            target.add(action.deepCopy());
        }
    }

    private static JsonArray jsonObjectArray(List<JsonObject> objects) {
        JsonArray array = new JsonArray();
        addActionObjects(array, objects);
        return array;
    }

    private static JsonArray stringArray(Iterable<String> values) {
        JsonArray array = new JsonArray();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                array.add(value.trim());
            }
        }
        return array;
    }

    private static JsonArray stringArray(JsonElement element) {
        JsonArray array = new JsonArray();
        if (element == null || element.isJsonNull()) {
            return array;
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            if (!value.isBlank()) {
                array.add(value);
            }
            return array;
        }
        if (!element.isJsonArray()) {
            return array;
        }
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) {
                String value = child.getAsString().trim();
                if (!value.isBlank()) {
                    array.add(value);
                }
            }
        }
        return array;
    }

    private static void flattenNestedObject(JsonObject object, String key) {
        JsonObject nested = optionalObject(object.get(key));
        if (nested == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : nested.entrySet()) {
            object.add(entry.getKey(), entry.getValue().deepCopy());
        }
    }

    private static JsonObject optionalObject(JsonElement element) {
        return element == null || !element.isJsonObject() ? null : element.getAsJsonObject();
    }

    private static void copyIfPresent(JsonObject source, JsonObject target, String key) {
        copyIfPresent(source, target, key, key);
    }

    private static void copyIfPresent(JsonObject source, JsonObject target, String sourceKey, String targetKey) {
        if (source != null && source.has(sourceKey)) {
            target.add(targetKey, source.get(sourceKey).deepCopy());
        }
    }

    private static void putString(JsonObject object, String key, String value) {
        if (value != null && !value.isBlank()) {
            object.addProperty(key, value);
        }
    }

    private static void putStringIfBlank(JsonObject object, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String existing = DatapackJsonReader.readString(object, key);
        if (existing.isBlank()) {
            object.addProperty(key, value);
        }
    }

    private static void putInt(JsonObject object, String key, int value) {
        if (value != 0) {
            object.addProperty(key, value);
        }
    }

    private static String metadataString(QuestV2Resource resource, String key) {
        JsonElement element = resource.metadata().get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString().trim() : "";
    }

    private static String uiString(QuestV2Resource.UiHints ui, String key) {
        return uiString(ui.data(), key);
    }

    private static String uiString(JsonObject ui, String key) {
        return ui == null ? "" : DatapackJsonReader.readString(ui, key);
    }

    private static String firstResourceLocationString(JsonObject object, String key) {
        if (object == null) {
            return "";
        }
        for (String value : DatapackJsonReader.readStringList(object, key)) {
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static boolean hasAny(JsonObject object, String... keys) {
        if (object == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (object.has(key)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private record CanonicalQuestJson(
            JsonObject root,
            Map<String, QuestSourcePointer> objectiveSources,
            Map<String, QuestSourcePointer> stageSources,
            Map<String, QuestSourcePointer> triggerSources
    ) {
    }
}
