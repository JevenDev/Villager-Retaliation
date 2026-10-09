package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.village.VillageEventMemory;
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
import com.jvn.villagerretaliation.util.ContentTags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;

public final class QuestV2Compiler {
    private static final Set<String> AVAILABILITY_BRANCH_FIELDS = Set.of(
            "exclusive_group", "exclusive_on", "blocks_on_start", "blocks_on_completion");

    private QuestV2Compiler() {
    }

    public static Optional<CompiledQuest> compile(QuestV2Resource resource, QuestResourceEnvelope envelope) {
        return compile(resource, envelope, com.jvn.villagerretaliation.util.item.ItemStackPredicateParser.DEFAULT_REGISTRIES);
    }

    public static Optional<CompiledQuest> compile(
            QuestV2Resource resource, QuestResourceEnvelope envelope, HolderLookup.Provider registries) {
        if (resource == null || envelope == null) return Optional.empty();
        QuestSourcePointer source = QuestSourcePointer.from(envelope);
        String parentId = firstNonBlank(metadataString(resource, "parent"), resource.availability().prerequisites().isEmpty()
                ? "" : resource.availability().prerequisites().getFirst().toString());
        ResourceLocation parent = DatapackJsonReader.parseResourceLocation(parentId).orElse(null);
        List<ResourceLocation> prerequisites = resource.availability().prerequisites().isEmpty()
                ? parent == null ? List.of() : List.of(parent) : resource.availability().prerequisites();
        List<CompiledQuestObjective> objectives = new ArrayList<>();
        Set<String> objectiveIds = new LinkedHashSet<>();
        List<CompiledQuestStage> stages = new ArrayList<>();
        List<CompiledQuestTrigger> triggers = new ArrayList<>();
        Map<String, Integer> stageIndexes = stageIndexes(resource);
        List<VillagerActionDefinition> globalEntry = lifecycleActions(resource, source, "on_stage_enter");
        List<VillagerActionDefinition> globalExit = lifecycleActions(resource, source, "on_stage_exit");
        QuestDefinition.Target target;
        try {
            target = VillagerQuestResources.readTargetFields(resource.target(), registries);
            for (QuestV2Resource.Stage stage : orderedStages(resource)) {
                QuestSourcePointer stageSource = source.child("stages", Integer.toString(stageIndexes.get(stage.id())));
                for (int index = 0; index < stage.objectives().size(); index++) {
                    QuestV2Resource.Objective objective = stage.objectives().get(index);
                    String id = canonicalObjectiveId(stage, objective);
                    if (!objectiveIds.add(id)) throw new IllegalArgumentException("duplicate compiled objective id " + id);
                    var definition = VillagerQuestResources.readObjectiveFields(source.resource(),
                            QuestObjectiveFields.v2(objective, id, objectives.size()), resource.id(), registries);
                    if (definition.isPresent()) objectives.add(new CompiledQuestObjective(id, objectives.size(),
                            definition.get(), stageSource.child("objectives", Integer.toString(index))));
                }
                stages.add(new CompiledQuestStage(stage.id(), stages.size(),
                        compileStage(resource, stage, stageSource, globalEntry, globalExit), List.of(), stageSource));
                for (int index = 0; index < stage.events().size(); index++) {
                    QuestV2Resource.Event event = stage.events().get(index);
                    appendEvent(resource.id(), event, stage.id(), stage.id() + "." + event.id(),
                            stageSource.child("events", Integer.toString(index)), triggers);
                }
            }
        } catch (IllegalArgumentException exception) {
            DatapackDiagnostics.warnSkippedEntry(source.resource(), "quest", resource.id().toString(), exception.getMessage());
            return Optional.empty();
        }
        appendLifecycle(resource, source, "on_start", QuestDefinition.TriggerEvent.STARTED, triggers);
        appendLifecycle(resource, source, "on_complete", QuestDefinition.TriggerEvent.COMPLETED, triggers);
        appendLifecycle(resource, source, "on_abandon", QuestDefinition.TriggerEvent.ABANDONED, triggers);
        appendLifecycle(resource, source, "on_expire", QuestDefinition.TriggerEvent.EXPIRED, triggers);
        appendLifecycle(resource, source, "on_fail", QuestDefinition.TriggerEvent.FAILED, triggers);
        for (int index = 0; index < resource.events().size(); index++) {
            QuestV2Resource.Event event = resource.events().get(index);
            appendEvent(resource.id(), event, "", event.id(), source.child("events", Integer.toString(index)), triggers);
        }
        return Optional.of(new CompiledQuest(resource.id(), source, null,
                new CompiledQuestMetadata(firstNonBlank(firstNonBlank(metadataString(resource, "title"), uiString(resource.ui(), "title")), resource.id().toString()),
                        firstNonBlank(metadataString(resource, "description"), uiString(resource.ui(), "description")),
                        firstNonBlank(metadataString(resource, "title_key"), uiString(resource.ui(), "title_key")),
                        firstNonBlank(metadataString(resource, "description_key"), uiString(resource.ui(), "description_key")),
                        firstNonBlank(metadataString(resource, "questline"), VillagerQuestResources.inferQuestline(source.resource())),
                        ContentTags.normalizeAll(new LinkedHashSet<>(DatapackJsonReader.readStringValues(resource.metadata().get("tags")))), parent, DialogueEntryMetadata.EMPTY,
                        com.jvn.villagerretaliation.campaign.content.CampaignQuestReference.read(resource.metadata().get("campaign"))),
                new CompiledQuestProvider(resource.provider().type(), compileOffer(resource, source),
                        resource.provider().deathProtection(), resource.provider().blocksHiring(), resource.provider().campaignRole()),
                target, resource.entryStage(), prerequisites, compileRules(resource, source),
                new CompiledQuestUi(compileTracker(resource), QuestDefinition.Dialogue.EMPTY, QuestDefinition.Links.EMPTY),
                objectives, Map.of(), stages, Map.of(), triggers, Map.of(), QuestTriggerIndex.EMPTY,
                new CompiledQuestRewards(compileRewards(resource.id(), source, resource.rewards())),
                DatapackJsonReader.readBoolean(resource.metadata().get("show_locked_adventure_hint"), true),
                VillagerQuestResources.readRevision(resource.metadata().get("revision"), optionalObject(resource.metadata().get("migration")))));
    }

    private static QuestDefinition.Stage compileStage(QuestV2Resource resource, QuestV2Resource.Stage stage,
            QuestSourcePointer source, List<VillagerActionDefinition> globalEntry, List<VillagerActionDefinition> globalExit) {
        Map<String, String> objectiveIds = new LinkedHashMap<>();
        stage.objectives().forEach(objective -> objectiveIds.put(objective.id(), canonicalObjectiveId(stage, objective)));
        JsonElement authored = stage.data().get("complete_when");
        List<QuestDefinition.StagePredicate> predicates = authored == null || authored.isJsonNull()
                ? stage.objectives().stream().filter(objective -> !objective.optional())
                        .map(objective -> new QuestDefinition.StagePredicate(objectiveIds.get(objective.id()), List.of())).toList()
                : qualifyPredicates(VillagerQuestResources.readStagePredicates(
                        source.resource(), source.jsonPointer() + "/complete_when", authored, resource.id()), objectiveIds);
        List<VillagerActionDefinition> entry = new ArrayList<>(globalEntry);
        entry.addAll(VillagerActionDefinition.readObjects(source.resource(), source.jsonPointer() + "/entry", stage.entryActions(), resource.id()));
        entry.addAll(rewardActions(resource.id(), source, stage.rewards()));
        List<VillagerActionDefinition> exit = new ArrayList<>(VillagerActionDefinition.readObjects(
                source.resource(), source.jsonPointer() + "/exit", stage.exitActions(), resource.id()));
        exit.addAll(globalExit);
        List<QuestDefinition.BonusOutcome> bonuses = VillagerQuestResources.readBonusOutcomes(
                source.resource(), source.jsonPointer(), stage.data().get("bonuses"), resource.id()).stream()
                .map(bonus -> new QuestDefinition.BonusOutcome(bonus.id(), qualifyPredicates(bonus.when(), objectiveIds),
                        bonus.mode(), bonus.count(), bonus.actions())).toList();
        JsonObject completion = DatapackJsonReader.readObject(stage.data(), "completion");
        String mode = completion != null && completion.has("mode") ? DatapackJsonReader.readString(completion, "mode")
                : DatapackJsonReader.readString(stage.data(), "completion_mode");
        int count = completion != null && completion.has("count") ? DatapackJsonReader.readInt(completion, "count", 1)
                : DatapackJsonReader.readInt(stage.data(), "completion_count", 1);
        return new QuestDefinition.Stage(stage.id(), List.copyOf(objectiveIds.values()), predicates,
                QuestDefinition.CompletionMode.bySerializedName(mode), count, stage.next().stage(), entry, exit, List.of(), bonuses,
                VillagerQuestResources.readStringMap(DatapackJsonReader.readObject(stage.data(), "metadata")));
    }

    private static List<QuestDefinition.StagePredicate> qualifyPredicates(
            List<QuestDefinition.StagePredicate> predicates, Map<String, String> objectiveIds) {
        return predicates.stream().map(predicate -> new QuestDefinition.StagePredicate(
                objectiveIds.getOrDefault(predicate.objective(), predicate.objective()), predicate.conditions())).toList();
    }

    private static QuestDefinition.Tracker compileTracker(QuestV2Resource resource) {
        Map<String, QuestDefinition.Step> steps = new LinkedHashMap<>();
        QuestDefinition.Step root = trackerStep(resource.ui());
        if (root != null) {
            steps.put("choice", root);
            steps.put("fact", root);
            steps.put("inactive", root);
        }
        for (QuestV2Resource.Stage stage : orderedStages(resource)) {
            QuestDefinition.Step step = trackerStep(stage.ui());
            if (step != null) steps.put(stage.id(), step);
        }
        JsonObject ui = resource.ui().data();
        return new QuestDefinition.Tracker(firstNonBlank(uiString(ui, "title"), metadataString(resource, "title")),
                firstNonBlank(uiString(ui, "title_key"), metadataString(resource, "title_key")), steps,
                VillagerQuestResources.readStringMap(DatapackJsonReader.readObject(ui, "metadata")),
                DatapackJsonReader.readResourceLocation(ui, "icon").orElse(null), uiString(ui, "color"), uiString(ui, "outline_color"),
                DatapackJsonReader.readInt(ui, "priority", 0), DatapackJsonReader.readBoolean(ui, "hidden", false));
    }

    private static QuestDefinition.Step trackerStep(QuestV2Resource.UiHints ui) {
        if (ui.trackerText().isBlank() && ui.trackerTextKey().isBlank()
                && !hasAny(ui.data(), "show_progress", "progress")) return null;
        return new QuestDefinition.Step(ui.trackerText(), ui.trackerTextKey(),
                DatapackJsonReader.readBoolean(ui.data(), "show_progress", true),
                (float) DatapackJsonReader.readDouble(ui.data(), "progress", -1.0D), Map.of());
    }

    private static List<VillagerActionDefinition> lifecycleActions(
            QuestV2Resource resource, QuestSourcePointer source, String hookId) {
        QuestV2Resource.Hook hook = resource.lifecycle().hooks().get(hookId);
        return hook == null ? List.of() : actions(resource.id(), source.child("lifecycle", hookId), hook.actions(), hook.transition());
    }

    private static void appendLifecycle(QuestV2Resource resource, QuestSourcePointer source, String hookId,
            QuestDefinition.TriggerEvent event, List<CompiledQuestTrigger> triggers) {
        List<VillagerActionDefinition> actions = lifecycleActions(resource, source, hookId);
        if (actions.isEmpty()) return;
        addTrigger(new QuestDefinition.Trigger("lifecycle." + hookId, event, List.of(), actions, Set.of(),
                QuestTriggerRegistry.defaultCooldownTicks(event), 10.0D, repeatable(actions)), source.child("lifecycle", hookId), triggers);
    }

    private static void appendEvent(ResourceLocation questId, QuestV2Resource.Event event, String ownerStage,
            String id, QuestSourcePointer source, List<CompiledQuestTrigger> triggers) {
        List<VillagerActionDefinition> actions = actions(questId, source, event.actions(), event.transition());
        if (actions.isEmpty()) return;
        QuestDefinition.TriggerEvent type = QuestDefinition.TriggerEvent.bySerializedName(event.trigger());
        if (type == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source.resource(), source.jsonPointer(), "unknown trigger event.");
            return;
        }
        JsonObject data = event.data();
        Set<String> stages = event.stages().isEmpty() && !ownerStage.isBlank() ? Set.of(ownerStage) : event.stages();
        addTrigger(new QuestDefinition.Trigger(id, type,
                DialogueCondition.readObjects(source.resource(), source.jsonPointer(), event.conditions(), questId), actions, stages,
                DatapackJsonReader.readDurationTicks(data, "cooldown", QuestTriggerRegistry.defaultCooldownTicks(type)),
                DatapackJsonReader.readDouble(data, "radius", 10.0D), DatapackJsonReader.readBoolean(data, "repeatable", repeatable(actions)),
                DatapackJsonReader.readInt(data, "priority", 0), DatapackJsonReader.readDouble(data, "chance", 1.0D),
                DatapackJsonReader.readBoolean(data, "exclusive", false), DatapackJsonReader.readInt(data, "weight", 1)), source, triggers);
    }

    private static void addTrigger(QuestDefinition.Trigger trigger, QuestSourcePointer source, List<CompiledQuestTrigger> triggers) {
        if (triggers.stream().anyMatch(existing -> existing.id().equals(trigger.id()))) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source.resource(), source.jsonPointer(), "duplicate trigger id " + trigger.id() + ".");
            return;
        }
        triggers.add(new CompiledQuestTrigger(trigger.id(), triggers.size(), trigger, source));
    }

    private static boolean repeatable(List<VillagerActionDefinition> actions) {
        return actions.stream().noneMatch(action -> action.kind() == VillagerActionDefinition.Kind.FORCED_DIALOGUE);
    }

    private static List<VillagerActionDefinition> actions(ResourceLocation questId, QuestSourcePointer source,
            List<JsonObject> authored, QuestV2Resource.Transition transition) {
        List<VillagerActionDefinition> actions = new ArrayList<>(VillagerActionDefinition.readObjects(
                source.resource(), source.jsonPointer(), authored, questId));
        if (transition != null && !transition.isEmpty()) {
            if (!transition.stage().isBlank()) {
                actions.add(new VillagerActionDefinition(VillagerActionDefinition.Kind.SET_VARIABLE, questId,
                        VillagerActionDefinition.QuestAction.NONE, 0, null, null, null, "", "", "", true,
                        QuestFactScope.QUEST, null, "stage", transition.stage(), Map.of(), Map.of(), null, null, "", false, false));
            } else if (transition.complete() || transition.abandon() || transition.fail()) {
                actions.add(new VillagerActionDefinition(VillagerActionDefinition.Kind.QUEST, questId,
                        transition.complete() ? VillagerActionDefinition.QuestAction.TURN_IN
                                : transition.abandon() ? VillagerActionDefinition.QuestAction.ABANDON : VillagerActionDefinition.QuestAction.FAIL,
                        0, null, null, null, "", "", "", true, QuestFactScope.PLAYER, null, "", "", Map.of(), Map.of(), null, null, "", false, false));
            }
        }
        return List.copyOf(actions);
    }

    private static QuestDefinition.Rewards compileRewards(ResourceLocation questId, QuestSourcePointer source, QuestV2Resource.Rewards rewards) {
        JsonObject data = rewards.data();
        int experience = DatapackJsonReader.readInt(data, "experience", 0);
        int reputation = DatapackJsonReader.readInt(data, "reputation", 0);
        int gossip = DatapackJsonReader.readInt(data, "gossip_reputation", 0);
        ResourceLocation loot = DatapackJsonReader.readResourceLocation(data, "loot_table").orElse(null);
        ResourceLocation memory = VillageEventMemory.parseTagId(DatapackJsonReader.readString(data, "memory_event")).orElse(null);
        VillageEventMemory.MemoryScope scope = VillageEventMemory.MemoryScope.parse(DatapackJsonReader.readString(data, "memory_scope"))
                .orElse(VillageEventMemory.MemoryScope.BOTH);
        for (VillagerActionDefinition action : VillagerActionDefinition.readObjects(source.resource(), source.jsonPointer() + "/rewards", rewards.actions(), questId)) {
            switch (action.kind()) {
                case EXPERIENCE -> experience += Math.max(0, action.amount());
                case REPUTATION -> reputation += action.amount();
                case GOSSIP -> gossip += action.amount();
                case LOOT -> loot = action.lootTable();
                case MEMORY -> { memory = action.memoryTag(); scope = action.memoryScope(); }
                default -> { }
            }
        }
        return new QuestDefinition.Rewards(Math.max(0, experience), reputation, gossip, loot, memory, scope);
    }

    private static List<VillagerActionDefinition> rewardActions(ResourceLocation questId, QuestSourcePointer source, QuestV2Resource.Rewards rewards) {
        List<VillagerActionDefinition> actions = new ArrayList<>(VillagerActionDefinition.readObjects(
                source.resource(), source.jsonPointer() + "/rewards", rewards.actions(), questId));
        JsonObject data = rewards.data();
        addRewardAction(actions, VillagerActionDefinition.Kind.EXPERIENCE, DatapackJsonReader.readInt(data, "experience", 0), null);
        addRewardAction(actions, VillagerActionDefinition.Kind.REPUTATION, DatapackJsonReader.readInt(data, "reputation", 0), null);
        addRewardAction(actions, VillagerActionDefinition.Kind.GOSSIP, DatapackJsonReader.readInt(data, "gossip_reputation", 0), null);
        String memory = DatapackJsonReader.readString(data, "memory_event");
        ResourceLocation memoryId = VillageEventMemory.parseTagId(memory).orElse(null);
        if (!memory.isBlank() && memoryId == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source.resource(), source.jsonPointer() + "/rewards/memory_event",
                    "memory action uses invalid tag \"" + memory + "\".");
        }
        ResourceLocation loot = DatapackJsonReader.readResourceLocation(data, "loot_table").orElse(null);
        if (!DatapackJsonReader.readString(data, "loot_table").isBlank() && loot == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(source.resource(), source.jsonPointer() + "/rewards/loot_table",
                    "loot action requires a valid loot table id.");
        }
        addRewardAction(actions, VillagerActionDefinition.Kind.MEMORY, 0, memoryId);
        addRewardAction(actions, VillagerActionDefinition.Kind.LOOT, 0, loot);
        return List.copyOf(actions);
    }

    private static void addRewardAction(List<VillagerActionDefinition> actions, VillagerActionDefinition.Kind kind, int amount, ResourceLocation resource) {
        if (amount == 0 && resource == null) return;
        actions.add(new VillagerActionDefinition(kind, null, VillagerActionDefinition.QuestAction.NONE, amount,
                kind == VillagerActionDefinition.Kind.MEMORY ? resource : null, VillageEventMemory.MemoryScope.BOTH,
                kind == VillagerActionDefinition.Kind.LOOT ? resource : null, "", "", "", true, QuestFactScope.PLAYER,
                null, "", "", Map.of(), Map.of(), null, null, "", false, false));
    }

    private static QuestDefinition.Offer compileOffer(QuestV2Resource resource, QuestSourcePointer source) {
        JsonObject availability = resource.availability().data();
        return VillagerQuestResources.readOfferFields(source.resource(),
                DatapackJsonReader.readObject(resource.provider().data(), "filters"),
                DialogueCondition.readObjects(source.resource(), "quest offer", resource.availability().conditions(), resource.id()),
                DatapackJsonReader.readInt(availability, "weight", DatapackJsonReader.readInt(availability, "selection_weight", 1)));
    }

    private static QuestDefinition.Rules compileRules(QuestV2Resource resource, QuestSourcePointer source) {
        JsonObject availability = resource.availability().data();
        JsonObject branch = DatapackJsonReader.readObject(availability, "branch");
        ResourceLocation group = null;
        for (String key : List.of("exclusive_group", "group")) {
            JsonElement value = branchValue(availability, branch, key);
            if (value != null && value.isJsonPrimitive()) {
                group = DatapackJsonReader.parseResourceLocation(value.getAsString()).orElse(null);
                if (group != null) break;
            }
        }
        var branching = new QuestDefinition.Branching(group,
                QuestDefinition.BranchLockEvent.bySerializedName(branchString(availability, branch, "exclusive_on", "lock_on")),
                branchResources(availability, branch, "blocks_on_start", "lock_on_start"),
                branchResources(availability, branch, "blocks", "blocks_on_completion", "blocks_on_complete", "lock_on_completion", "lock_on_complete"));
        return VillagerQuestResources.readRuleFields(source.resource(), availability, resource.id(),
                completionCooldown(availability), branching);
    }

    private static long completionCooldown(JsonObject availability) {
        for (String unit : List.of("ticks", "days", "seconds")) {
            String canonical = "completion_cooldown_" + unit;
            Long value = DatapackJsonReader.readNullableLong(availability,
                    availability.has(canonical) ? canonical : "cooldown_" + unit);
            if (value != null) return Math.max(0L, value * switch (unit) {
                case "days" -> 24000L;
                case "seconds" -> 20L;
                default -> 1L;
            });
        }
        return 0L;
    }

    private static JsonElement branchValue(JsonObject availability, JsonObject branch, String key) {
        JsonObject owner = AVAILABILITY_BRANCH_FIELDS.contains(key) && availability.has(key) ? availability : branch;
        return owner == null ? null : owner.get(key);
    }

    private static String branchString(JsonObject availability, JsonObject branch, String... keys) {
        for (String key : keys) {
            JsonElement value = branchValue(availability, branch, key);
            if (value != null && value.isJsonPrimitive()) {
                String text = value.getAsString().trim();
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private static Set<ResourceLocation> branchResources(JsonObject availability, JsonObject branch, String... keys) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (String key : keys) {
            for (String value : DatapackJsonReader.readStringValues(branchValue(availability, branch, key)))
                DatapackJsonReader.parseResourceLocation(value).ifPresent(result::add);
        }
        return Set.copyOf(result);
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

    private static JsonObject optionalObject(JsonElement element) {
        return element == null || !element.isJsonObject() ? null : element.getAsJsonObject();
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

}
