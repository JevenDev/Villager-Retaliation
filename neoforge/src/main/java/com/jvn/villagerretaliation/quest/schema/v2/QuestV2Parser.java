package com.jvn.villagerretaliation.quest.schema.v2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.provider.QuestProviderRegistry;
import com.jvn.villagerretaliation.quest.provider.QuestProviderTypeDescriptor;
import com.jvn.villagerretaliation.quest.provider.QuestProviderDeathProtection;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.QuestResourceSource;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

public final class QuestV2Parser {
    private static final String RESOURCE_ROOT = "quests";
    private static final String GENERATED_ID_PREFIX = "__generated";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
    private static final Set<String> ROOT_KEYS = Set.of(
            "schema",
            "id",
            "metadata",
            "provider",
            "availability",
            "lifecycle",
            "dialogue",
            "target",
            "entry_stage",
            "stages",
            "events",
            "rewards",
            "ui",
            "external_scenes");
    private static final Set<String> METADATA_KEYS = Set.of(
            "title",
            "description",
            "title_key",
            "description_key",
            "questline",
            "tags",
            "parent",
            "show_locked_adventure_hint",
            "author",
            "version",
            "revision",
            "migration");
    private static final Set<String> TARGET_KEYS = Set.of(
            "structure",
            "dimension",
            "pieces",
            "search_radius",
            "discovery_radius",
            "proof_item",
            "proof_item_components",
            "proof_item_durability",
            "proof_item_custom_data",
            "proof_item_nbt");
    private static final Set<String> PROVIDER_KEYS = Set.of(
            "type",
            "capabilities",
            "required_capabilities",
            "death_protection",
            "filters",
            "data");
    private static final Set<String> AVAILABILITY_KEYS = Set.of(
            "conditions",
            "active",
            "expiration",
            "branch",
            "cooldown",
            "cooldown_ticks",
            "cooldown_days",
            "cooldown_seconds",
            "completion_cooldown",
            "completion_cooldown_ticks",
            "completion_cooldown_days",
            "completion_cooldown_seconds",
            "prerequisite_cooldown",
            "prerequisite_cooldown_ticks",
            "prerequisite_cooldown_days",
            "prerequisite_cooldown_seconds",
            "exclusive_group",
            "exclusive_on",
            "blocks_on_start",
            "blocks_on_completion",
            "repeatable",
            "max_starts",
            "max_completions",
            "completion_scope",
            "scope",
            "abandonment",
            "abandonment_cooldown",
            "abandonment_cooldown_ticks",
            "abandonment_cooldown_days",
            "abandonment_cooldown_seconds",
            "consume_on_completion",
            "consume_on_abandonment",
            "locked_to_villager",
            "cross_villager_compatible",
            "prerequisites");
    private static final Set<String> LIFECYCLE_KEYS = Set.of(
            "on_start",
            "on_complete",
            "on_abandon",
            "on_expire",
            "on_fail",
            "on_stage_enter",
            "on_stage_exit",
            "dialogue");
    private static final Set<String> HOOK_KEYS = Set.of(
            "actions",
            "transition",
            "next",
            "stage",
            "scene",
            "complete",
            "abandon",
            "fail");
    private static final Set<String> STAGE_KEYS = Set.of(
            "id",
            "title",
            "title_key",
            "description",
            "description_key",
            "objectives",
            "complete_when",
            "completion",
            "completion_mode",
            "completion_count",
            "bonuses",
            "next",
            "dialogue",
            "scenes",
            "responses",
            "events",
            "on_enter",
            "on_exit",
            "entry_actions",
            "exit_actions",
            "rewards",
            "ui",
            "metadata");
    private static final Set<String> OBJECTIVE_KEYS = Set.of(
            "id",
            "type",
            "optional",
            "count",
            "consume",
            "tracker",
            "conditions",
            "criterion",
            "match",
            "target",
            "targets",
            "structure",
            "dimension",
            "location",
            "radius",
            "search_radius",
            "discovery_radius",
            "item",
            "items",
            "item_tag",
            "item_tags",
            "selection",
            "components",
            "durability",
            "min_durability",
            "max_durability",
            "min_durability_percent",
            "max_durability_percent",
            "enchantment",
            "enchantments",
            "min_enchantment_level",
            "max_enchantment_level",
            "custom_data",
            "nbt",
            "entity",
            "entities",
            "entity_tag",
            "entity_tags",
            "block",
            "blocks",
            "block_tag",
            "block_tags",
            "memory",
            "memory_tag",
            "memory_tags",
            "gift_reaction",
            "gift_reactions",
            "reputation_level",
            "reputation_levels",
            "min",
            "max",
            "scope",
            "quest",
            "quest_id",
            "tag",
            "tags",
            "key",
            "value",
            "values",
            "stage",
            "stages",
            "choices",
            "metadata",
            "ui");
    private static final Set<String> DIALOGUE_SLOT_KEYS = Set.of(
            "scene",
            "scene_ref",
            "external",
            "external_scene",
            "external_entry",
            "label",
            "request",
            "show_for_babies",
            "order",
            "text",
            "text_key",
            "lines",
            "responses",
            "conditions",
            "actions",
            "metadata");
    private static final Set<String> SCENE_KEYS = Set.of(
            "id",
            "slot",
            "label",
            "request",
            "show_for_babies",
            "order",
            "text",
            "text_key",
            "lines",
            "responses",
            "actions",
            "conditions",
            "next",
            "transition",
            "external",
            "external_scene",
            "external_entry",
            "scene_ref",
            "metadata");
    private static final Set<String> EXTERNAL_SCENE_KEYS = Set.of(
            "tree",
            "tree_id",
            "dialogue_tree",
            "entry",
            "entry_id",
            "metadata");
    private static final Set<String> RESPONSE_KEYS = Set.of(
            "id",
            "label",
            "label_key",
            "text",
            "text_key",
            "lines",
            "conditions",
            "actions",
            "transition",
            "next",
            "stage",
            "scene",
            "response",
            "complete",
            "abandon",
            "fail",
            "request",
            "order",
            "metadata");
    private static final Set<String> TRANSITION_KEYS = Set.of(
            "stage",
            "scene",
            "response",
            "complete",
            "abandon",
            "fail");
    private static final Set<String> EVENT_KEYS = Set.of(
            "id",
            "event",
            "type",
            "trigger",
            "stage",
            "stages",
            "conditions",
            "actions",
            "transition",
            "next",
            "cooldown",
            "cooldown_ticks",
            "cooldown_seconds",
            "cooldown_days",
            "radius",
            "repeatable",
            "metadata");
    private static final Set<String> REWARDS_KEYS = Set.of(
            "actions",
            "experience",
            "reputation",
            "gossip_reputation",
            "loot_table",
            "memory_event",
            "memory_scope");
    private static final Set<String> UI_KEYS = Set.of(
            "title",
            "title_key",
            "description",
            "description_key",
            "tracker_text",
            "tracker_text_key",
            "tracker_complete_text",
            "tracker_complete_text_key",
            "show_progress",
            "progress",
            "placeholders",
            "metadata",
            "icon",
            "color",
            "outline_color",
            "priority",
            "hidden");

    private QuestV2Parser() {
    }

    public static Optional<QuestV2Resource> parse(QuestResourceEnvelope envelope) {
        if (envelope == null) {
            return Optional.empty();
        }
        return parse(envelope.location(), envelope.source(), envelope.root());
    }

    public static Optional<QuestV2Resource> parse(ResourceLocation location, JsonObject root) {
        return parse(location, new QuestResourceSource(location, ""), root);
    }

    public static Optional<QuestV2Resource> parse(
            ResourceLocation location,
            QuestResourceSource source,
            JsonObject root) {
        if (root == null) {
            return Optional.empty();
        }

        Validator validator = new Validator(location);
        validator.expectKeys(root, "", ROOT_KEYS);

        ResourceLocation id = readResourceLocation(validator, root, "/id", "id")
                .orElse(fallbackQuestId(location));
        if (id == null) {
            validator.error("/id", "quest id is required and must be a valid resource location.", "Add an id such as villagerretaliation:example_quest.", Set.of());
        }

        Map<String, JsonElement> metadata = readMetadata(validator, root.get("metadata"));
        QuestV2Resource.Provider provider = readProvider(validator, root.get("provider"));
        QuestV2Resource.Availability availability = readAvailability(validator, root.get("availability"));
        QuestV2Resource.Lifecycle lifecycle = readLifecycle(validator, root.get("lifecycle"));
        JsonObject target = readTarget(validator, root.get("target"), "/target");
        String entryStage = readString(root, "entry_stage");

        List<QuestV2Resource.Stage> stages = readStages(validator, root.get("stages"));
        Map<String, QuestV2Resource.Stage> stagesById = indexStages(validator, stages);
        List<QuestV2Resource.Event> events = readEvents(validator, root.get("events"), "/events");
        QuestV2Resource.Rewards rewards = readRewards(validator, root.get("rewards"), "/rewards");
        QuestV2Resource.UiHints ui = readUi(validator, root.get("ui"), "/ui");

        QuestV2Resource resource = id == null
                ? null
                : new QuestV2Resource(
                        id,
                        source,
                        metadata,
                        provider,
                        availability,
                        lifecycle,
                        target,
                        entryStage,
                        stages,
                        stagesById,
                        events,
                        rewards,
                        ui);
        if (resource != null) {
            validateGraph(validator, resource);
        }
        return validator.valid() && resource != null ? Optional.of(resource) : Optional.empty();
    }

    private static Map<String, JsonElement> readMetadata(Validator validator, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }
        JsonObject object = validator.object(element, "/metadata", "metadata", true);
        if (object == null) {
            return Map.of();
        }
        validator.expectKeys(object, "/metadata", METADATA_KEYS);
        return new LinkedHashMap<>(object.asMap());
    }

    private static JsonObject readTarget(Validator validator, JsonElement element, String pointer) {
        if (element == null || element.isJsonNull()) {
            return new JsonObject();
        }
        JsonObject object = validator.object(element, pointer, "target", true);
        if (object == null) {
            return new JsonObject();
        }
        validator.expectKeys(object, pointer, TARGET_KEYS);
        readResourceLocation(validator, object, pointer + "/structure", "structure");
        readResourceLocation(validator, object, pointer + "/dimension", "dimension");
        readResourceLocation(validator, object, pointer + "/proof_item", "proof_item");
        validateItemPredicateObjects(validator, object, pointer, "proof_item_components", "proof_item_durability",
                "proof_item_custom_data", "proof_item_nbt");
        return object;
    }

    private static QuestV2Resource.Provider readProvider(Validator validator, JsonElement element) {
        JsonObject object = validator.object(element, "/provider", "provider", true);
        if (object == null) {
            return QuestV2Resource.Provider.EMPTY;
        }
        validator.expectKeys(object, "/provider", PROVIDER_KEYS);
        Optional<ResourceLocation> type = readResourceLocation(validator, object, "/provider/type", "type");
        if (type.isEmpty()) {
            validator.error("/provider/type", "provider type is required.", "Set provider.type to a registered provider such as villagerretaliation:villager.", Set.of());
        }
        QuestProviderTypeDescriptor descriptor = type.flatMap(QuestV2Parser::providerDescriptor).orElse(null);
        if (type.isPresent() && descriptor == null) {
            validator.error(
                    "/provider/type",
                    "unknown provider type \"" + type.get() + "\".",
                    "Use one of the provider types exported in quest-registry-metadata.json.",
                    Set.of(type.get().toString()));
        }

        Set<ResourceLocation> requiredCapabilities = readResourceLocationSet(
                validator,
                object,
                "/provider/required_capabilities",
                "required_capabilities",
                "capabilities");
        if (descriptor != null) {
            for (ResourceLocation capability : requiredCapabilities) {
                if (!descriptor.capabilities().contains(capability)) {
                    validator.error(
                            "/provider/required_capabilities",
                            "provider " + descriptor.id() + " does not support live capability " + capability + ".",
                            "Remove the capability or choose a provider that exports it.",
                            Set.of(descriptor.id().toString(), capability.toString()));
                }
            }
        }
        String deathProtectionValue = readString(object, "death_protection");
        QuestProviderDeathProtection deathProtection = QuestProviderDeathProtection.parse(deathProtectionValue)
                .orElseGet(() -> {
                    DatapackDiagnostics.warnQuestV2Validation(
                            validator.location,
                            "/provider/death_protection",
                            "invalid provider death protection \"" + deathProtectionValue + "\"; using none.",
                            "Use none, while_active, or after_start.",
                            Set.of(deathProtectionValue));
                    return QuestProviderDeathProtection.NONE;
                });
        return new QuestV2Resource.Provider(type.orElse(null), requiredCapabilities, deathProtection, object);
    }

    private static QuestV2Resource.Availability readAvailability(Validator validator, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return QuestV2Resource.Availability.EMPTY;
        }
        JsonObject object = validator.object(element, "/availability", "availability", true);
        if (object == null) {
            return QuestV2Resource.Availability.EMPTY;
        }
        validator.expectKeys(object, "/availability", AVAILABILITY_KEYS);
        List<JsonObject> conditions = readConditionObjects(validator, object.get("conditions"), "/availability/conditions");
        List<ResourceLocation> prerequisites = readResourceLocationList(
                validator,
                object.get("prerequisites"),
                "/availability/prerequisites");
        return new QuestV2Resource.Availability(conditions, prerequisites, object);
    }

    private static QuestV2Resource.Lifecycle readLifecycle(Validator validator, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return QuestV2Resource.Lifecycle.EMPTY;
        }
        JsonObject object = validator.object(element, "/lifecycle", "lifecycle", true);
        if (object == null) {
            return QuestV2Resource.Lifecycle.EMPTY;
        }
        validator.expectKeys(object, "/lifecycle", LIFECYCLE_KEYS);
        Map<String, QuestV2Resource.Hook> hooks = new LinkedHashMap<>();
        for (String key : LIFECYCLE_KEYS) {
            if ("dialogue".equals(key) || !object.has(key)) {
                continue;
            }
            QuestV2Resource.Hook hook = readLifecycleHook(validator, key, object.get(key), "/lifecycle/" + key);
            hooks.put(key, hook);
        }
        List<QuestV2Resource.Scene> dialogue = readScenes(validator, object.get("dialogue"), "/lifecycle/dialogue");
        return new QuestV2Resource.Lifecycle(hooks, dialogue);
    }

    private static QuestV2Resource.Hook readLifecycleHook(
            Validator validator,
            String id,
            JsonElement element,
            String pointer) {
        JsonObject object = validator.object(element, pointer, "lifecycle hook", true);
        if (object == null) {
            return new QuestV2Resource.Hook(id, List.of(), QuestV2Resource.Transition.EMPTY, new JsonObject());
        }
        validator.expectKeys(object, pointer, HOOK_KEYS);
        List<JsonObject> actions = readActionObjects(validator, object.get("actions"), pointer + "/actions");
        QuestV2Resource.Transition transition = readTransition(validator, object, pointer);
        if (!actions.isEmpty() && !transition.isEmpty()) {
            validator.error(
                    pointer,
                    "lifecycle hook cannot define both actions and a transition.",
                    "Choose actions for side effects or transition for flow control, not both.",
                    Set.of(id));
        }
        return new QuestV2Resource.Hook(id, actions, transition, object);
    }

    private static List<QuestV2Resource.Stage> readStages(Validator validator, JsonElement element) {
        JsonArray array = validator.array(element, "/stages", "stages", true);
        if (array == null) {
            return List.of();
        }
        List<QuestV2Resource.Stage> stages = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String pointer = "/stages/" + index;
            JsonObject object = validator.object(array.get(index), pointer, "stage", true);
            if (object == null) {
                continue;
            }
            validator.expectKeys(object, pointer, STAGE_KEYS);
            String id = readString(object, "id");
            validateId(validator, pointer + "/id", id, "stage id");
            List<QuestV2Resource.Objective> objectives = readObjectives(validator, object.get("objectives"), pointer + "/objectives");
            List<String> completeWhen = readObjectiveReferences(validator, object.get("complete_when"), pointer + "/complete_when");
            QuestV2Resource.Transition next = readTransition(validator, object.get("next"), pointer + "/next");
            Map<String, QuestV2Resource.DialogueSlot> dialogueSlots = readDialogueSlots(validator, object.get("dialogue"), pointer + "/dialogue");
            Map<String, QuestV2Resource.Scene> scenes = indexScenes(
                    validator,
                    readScenes(validator, object.get("scenes"), pointer + "/scenes"),
                    pointer + "/scenes");
            List<QuestV2Resource.Response> responses = readResponses(validator, object.get("responses"), pointer + "/responses");
            List<QuestV2Resource.Event> events = readEvents(validator, object.get("events"), pointer + "/events");
            List<JsonObject> entryActions = readActionObjects(validator, firstElement(object, "on_enter", "entry_actions"), pointer + "/on_enter");
            List<JsonObject> exitActions = readActionObjects(validator, firstElement(object, "on_exit", "exit_actions"), pointer + "/on_exit");
            QuestV2Resource.Rewards rewards = readRewards(validator, object.get("rewards"), pointer + "/rewards");
            QuestV2Resource.UiHints ui = readUi(validator, object.get("ui"), pointer + "/ui");
            stages.add(new QuestV2Resource.Stage(
                    id,
                    objectives,
                    completeWhen,
                    next,
                    dialogueSlots,
                    scenes,
                    responses,
                    events,
                    entryActions,
                    exitActions,
                    rewards,
                    ui,
                    object));
        }
        return List.copyOf(stages);
    }

    private static List<QuestV2Resource.Objective> readObjectives(
            Validator validator,
            JsonElement element,
            String pointer) {
        JsonArray array = validator.array(element, pointer, "objectives", true);
        if (array == null) {
            return List.of();
        }
        List<QuestV2Resource.Objective> objectives = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int index = 0; index < array.size(); index++) {
            String objectivePointer = pointer + "/" + index;
            JsonObject object = validator.object(array.get(index), objectivePointer, "objective", true);
            if (object == null) {
                continue;
            }
            validator.expectKeys(object, objectivePointer, OBJECTIVE_KEYS);
            String id = readString(object, "id");
            validateId(validator, objectivePointer + "/id", id, "objective id");
            if (!id.isBlank() && !ids.add(id)) {
                validator.error(
                        objectivePointer + "/id",
                        "duplicate objective id \"" + id + "\" in stage.",
                        "Use unique objective ids within each stage.",
                        Set.of(id));
            }
            String type = readString(object, "type");
            if (type.isBlank()) {
                validator.error(objectivePointer + "/type", "objective type is required.", "Use a registered objective type.", Set.of(id));
            } else if (QuestObjectiveRegistry.objectiveTypeBySerializedName(type) == null) {
                validator.error(
                        objectivePointer + "/type",
                        "unknown objective type \"" + type + "\".",
                        "Use one of the objective types exported in quest-registry-metadata.json.",
                        Set.of(type));
            }
            readConditionObjects(validator, object.get("conditions"), objectivePointer + "/conditions");
            validateItemPredicateObjects(
                    validator, object, objectivePointer, "components", "durability", "custom_data", "nbt");
            validateRandomItemSelection(validator, object, objectivePointer, type);
            objectives.add(new QuestV2Resource.Objective(
                    id,
                    QuestObjectiveRegistry.canonicalTypeId(type),
                    readBoolean(object, "optional"),
                    object));
        }
        return List.copyOf(objectives);
    }

    private static void validateRandomItemSelection(
            Validator validator,
            JsonObject objective,
            String pointer,
            String type) {
        JsonElement items = objective.get("items");
        JsonElement rawSelection = objective.get("selection");
        if (items == null && rawSelection == null) {
            return;
        }
        String selection = readString(objective, "selection");
        if (!"item_check".equals(QuestObjectiveRegistry.canonicalTypeId(type))) {
            validator.error(pointer, "selection and items are only supported by item_check objectives.",
                    "Remove the random item fields from this objective.", Set.of());
            return;
        }
        if (!readString(objective, "item").isBlank()) {
            validator.error(pointer, "random item_check objectives cannot combine item with items.",
                    "Use either the fixed item field or selection with items.", Set.of());
        }
        if (!"random".equals(selection)) {
            validator.error(pointer + "/selection", "items requires selection to be random.",
                    "Set selection to random.", Set.of());
        }
        JsonArray entries = validator.array(items, pointer + "/items", "random item entries", true);
        if (entries == null || entries.isEmpty()) {
            validator.error(pointer + "/items", "random item entries must not be empty.",
                    "Add at least one item or item tag.", Set.of());
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            JsonElement entry = entries.get(index);
            String entryPointer = pointer + "/items/" + index;
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                String value = entry.getAsString().trim();
                String id = value.startsWith("#") ? value.substring(1) : value;
                if (id.isBlank() || ResourceLocation.tryParse(id) == null) {
                    validator.error(entryPointer, "invalid item selector: " + value,
                            "Use a namespaced item id or prefix an item tag with #.", Set.of(value));
                }
                continue;
            }
            JsonObject weighted = validator.object(entry, entryPointer, "weighted item entry", true);
            if (weighted == null) {
                continue;
            }
            validator.expectKeys(weighted, entryPointer, Set.of("item", "tag", "weight"));
            String item = readString(weighted, "item");
            String tag = readString(weighted, "tag");
            if (item.isBlank() == tag.isBlank()) {
                validator.error(entryPointer, "weighted item entry must define exactly one of item or tag.",
                        "Set one selector and remove the other.", Set.of());
            }
            String selector = item.isBlank() ? tag : item;
            if (selector.startsWith("#")) {
                selector = selector.substring(1);
            }
            if (!selector.isBlank() && ResourceLocation.tryParse(selector) == null) {
                validator.error(entryPointer, "invalid item selector: " + selector,
                        "Use a namespaced item or tag id.", Set.of(selector));
            }
            JsonElement weight = weighted.get("weight");
            if (weight != null && (!weight.isJsonPrimitive()
                    || !weight.getAsJsonPrimitive().isNumber()
                    || weight.getAsDouble() < 1.0D
                    || weight.getAsDouble() != Math.rint(weight.getAsDouble())
                    || weight.getAsDouble() > Integer.MAX_VALUE)) {
                validator.error(entryPointer + "/weight", "weight must be a positive integer.",
                        "Use an integer of 1 or greater.", Set.of());
            }
        }
    }

    private static void validateItemPredicateObjects(
            Validator validator,
            JsonObject object,
            String pointer,
            String componentsKey,
            String durabilityKey,
            String customDataKey,
            String nbtKey) {
        for (String key : List.of(componentsKey, durabilityKey, customDataKey, nbtKey)) {
            if (object.has(key)) {
                validator.object(object.get(key), pointer + "/" + key, key, false);
            }
        }
    }

    private static Map<String, QuestV2Resource.DialogueSlot> readDialogueSlots(
            Validator validator,
            JsonElement element,
            String pointer) {
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }
        JsonObject object = validator.object(element, pointer, "dialogue slots", true);
        if (object == null) {
            return Map.of();
        }
        Map<String, QuestV2Resource.DialogueSlot> slots = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String slot = entry.getKey();
            String slotPointer = pointer + "/" + escapePointer(slot);
            JsonObject slotObject = validator.object(entry.getValue(), slotPointer, "dialogue slot", true);
            if (slotObject == null) {
                continue;
            }
            validator.expectKeys(slotObject, slotPointer, DIALOGUE_SLOT_KEYS);
            String scene = readString(slotObject, "scene", "scene_ref");
            QuestV2Resource.ExternalScene external = readExternalScene(
                    validator,
                    slotObject,
                    slotPointer,
                    slot);
            QuestV2Resource.Scene inlineScene = null;
            if (hasInlineSceneContent(slotObject)) {
                inlineScene = readScene(validator, slotObject, slotPointer, slot);
            }
            if (inlineScene != null && (!external.isEmpty() || !scene.isBlank())) {
                validator.error(
                        slotPointer,
                        "dialogue slot cannot mix inline scene content with scene_ref or external_scene.",
                        "Use either inline content or a reference.",
                        Set.of(slot));
            }
            slots.put(slot, new QuestV2Resource.DialogueSlot(slot, scene, inlineScene, external, slotObject));
        }
        return Map.copyOf(slots);
    }

    private static List<QuestV2Resource.Scene> readScenes(Validator validator, JsonElement element, String pointer) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        JsonArray array = validator.array(element, pointer, "scenes", true);
        if (array == null) {
            return List.of();
        }
        List<QuestV2Resource.Scene> scenes = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String scenePointer = pointer + "/" + index;
            JsonObject object = validator.object(array.get(index), scenePointer, "scene", true);
            if (object == null) {
                continue;
            }
            scenes.add(readScene(validator, object, scenePointer, "scene_" + index));
        }
        return List.copyOf(scenes);
    }

    private static QuestV2Resource.Scene readScene(
            Validator validator,
            JsonObject object,
            String pointer,
            String fallbackId) {
        validator.expectKeys(object, pointer, SCENE_KEYS);
        String id = firstNonBlank(readString(object, "id"), fallbackId);
        validateId(validator, pointer + "/id", id, "scene id");
        QuestV2Resource.ExternalScene external = readExternalScene(
                validator,
                object,
                pointer,
                id,
                "scene_ref");
        List<String> lines = readStringList(object.get("lines"));
        String text = readString(object, "text");
        if (!text.isBlank()) {
            lines = append(lines, text);
        }
        String textKey = readString(object, "text_key");
        List<QuestV2Resource.Response> responses = readResponses(validator, object.get("responses"), pointer + "/responses");
        readActionObjects(validator, object.get("actions"), pointer + "/actions");
        readConditionObjects(validator, object.get("conditions"), pointer + "/conditions");
        QuestV2Resource.Scene scene = new QuestV2Resource.Scene(id, lines, textKey, external, responses, object);
        if (scene.hasInlineContent() && !external.isEmpty()) {
            validator.error(
                    pointer,
                    "scene cannot mix inline dialogue content with an external scene reference.",
                    "Use either inline lines/responses or external_scene.",
                    Set.of(id, external.tree().toString()));
        }
        return scene;
    }

    private static List<QuestV2Resource.Response> readResponses(
            Validator validator,
            JsonElement element,
            String pointer) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        JsonArray array = validator.array(element, pointer, "responses", true);
        if (array == null) {
            return List.of();
        }
        List<QuestV2Resource.Response> responses = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int index = 0; index < array.size(); index++) {
            String responsePointer = pointer + "/" + index;
            JsonObject object = validator.object(array.get(index), responsePointer, "response", true);
            if (object == null) {
                continue;
            }
            validator.expectKeys(object, responsePointer, RESPONSE_KEYS);
            String id = readString(object, "id");
            if (id.isBlank()) {
                validator.error(
                        responsePointer + "/id",
                        "response id is required.",
                        "Add a stable response id so transitions can reference it.",
                        Set.of());
            } else {
                validateId(validator, responsePointer + "/id", id, "response id");
                if (!ids.add(id)) {
                    validator.error(
                            responsePointer + "/id",
                            "duplicate response id \"" + id + "\" in scene.",
                            "Use unique response ids within the scene.",
                            Set.of(id));
                }
            }
            QuestV2Resource.TextSpec label = readTextSpec(object);
            QuestV2Resource.Transition transition = readTransition(validator, object, responsePointer);
            List<JsonObject> actions = readActionObjects(validator, object.get("actions"), responsePointer + "/actions");
            validateResponseTransitionActionConflicts(validator, responsePointer, transition, actions);
            responses.add(new QuestV2Resource.Response(id, label, transition, actions, object));
        }
        return List.copyOf(responses);
    }

    private static List<QuestV2Resource.Event> readEvents(Validator validator, JsonElement element, String pointer) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        JsonArray array = validator.array(element, pointer, "events", true);
        if (array == null) {
            return List.of();
        }
        List<QuestV2Resource.Event> events = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int index = 0; index < array.size(); index++) {
            String eventPointer = pointer + "/" + index;
            JsonObject object = validator.object(array.get(index), eventPointer, "event", true);
            if (object == null) {
                continue;
            }
            validator.expectKeys(object, eventPointer, EVENT_KEYS);
            String id = readString(object, "id");
            validateId(validator, eventPointer + "/id", id, "event id");
            if (!id.isBlank() && !ids.add(id)) {
                validator.error(
                        eventPointer + "/id",
                        "duplicate event id \"" + id + "\".",
                        "Use unique event ids in this event list.",
                        Set.of(id));
            }
            String trigger = firstNonBlank(readString(object, "event"), readString(object, "trigger"), readString(object, "type"));
            if (trigger.isBlank()) {
                validator.error(eventPointer + "/event", "event trigger is required.", "Use a registered trigger event.", Set.of(id));
            } else if (QuestTriggerRegistry.eventBySerializedName(trigger) == null) {
                validator.error(
                        eventPointer + "/event",
                        "unknown trigger event \"" + trigger + "\".",
                        "Use one of the trigger events exported in quest-registry-metadata.json.",
                        Set.of(trigger));
            }
            Set<String> stages = new LinkedHashSet<>(readStringList(object.get("stages")));
            String stage = readString(object, "stage");
            if (!stage.isBlank()) {
                stages.add(stage);
            }
            List<JsonObject> conditions = readConditionObjects(validator, object.get("conditions"), eventPointer + "/conditions");
            List<JsonObject> actions = readActionObjects(validator, object.get("actions"), eventPointer + "/actions");
            QuestV2Resource.Transition transition = readTransition(validator, object, eventPointer);
            events.add(new QuestV2Resource.Event(
                    id,
                    QuestTriggerRegistry.canonicalEventId(trigger),
                    stages,
                    conditions,
                    actions,
                    transition,
                    object));
        }
        return List.copyOf(events);
    }

    private static QuestV2Resource.Rewards readRewards(Validator validator, JsonElement element, String pointer) {
        if (element == null || element.isJsonNull()) {
            return QuestV2Resource.Rewards.EMPTY;
        }
        JsonObject object = validator.object(element, pointer, "rewards", true);
        if (object == null) {
            return QuestV2Resource.Rewards.EMPTY;
        }
        validator.expectKeys(object, pointer, REWARDS_KEYS);
        List<JsonObject> actions = readActionObjects(validator, object.get("actions"), pointer + "/actions");
        return new QuestV2Resource.Rewards(actions, object);
    }

    private static QuestV2Resource.UiHints readUi(Validator validator, JsonElement element, String pointer) {
        if (element == null || element.isJsonNull()) {
            return QuestV2Resource.UiHints.EMPTY;
        }
        JsonObject object = validator.object(element, pointer, "ui", true);
        if (object == null) {
            return QuestV2Resource.UiHints.EMPTY;
        }
        validator.expectKeys(object, pointer, UI_KEYS);
        Map<String, String> placeholders = new LinkedHashMap<>();
        JsonObject placeholderObject = validator.optionalObject(object.get("placeholders"), pointer + "/placeholders", "ui placeholders");
        if (placeholderObject != null) {
            for (Map.Entry<String, JsonElement> entry : placeholderObject.entrySet()) {
                String key = entry.getKey();
                if (!isPlaceholderName(key)) {
                    validator.error(
                            pointer + "/placeholders/" + escapePointer(key),
                            "placeholder name \"" + key + "\" is invalid.",
                            "Use letters, numbers, and underscores.",
                            Set.of(key));
                }
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                    validator.error(
                            pointer + "/placeholders/" + escapePointer(key),
                            "placeholder value must be a string expression.",
                            "Use a string expression for the placeholder source.",
                            Set.of(key));
                    continue;
                }
                placeholders.put(key, entry.getValue().getAsString().trim());
            }
        }
        String trackerText = readString(object, "tracker_text");
        validatePlaceholders(validator, trackerText, placeholders.keySet(), pointer + "/tracker_text");
        validatePlaceholders(validator, readString(object, "title"), placeholders.keySet(), pointer + "/title");
        validatePlaceholders(validator, readString(object, "description"), placeholders.keySet(), pointer + "/description");
        return new QuestV2Resource.UiHints(trackerText, readString(object, "tracker_text_key"), placeholders, object);
    }

    private static QuestV2Resource.Transition readTransition(
            Validator validator,
            JsonObject object,
            String pointer) {
        JsonElement transition = object.get("transition");
        if (transition != null && !transition.isJsonNull()) {
            return readTransition(validator, transition, pointer + "/transition");
        }
        if (hasAny(object, "next", "stage", "scene", "response", "complete", "abandon", "fail")) {
            JsonObject synthetic = new JsonObject();
            copyIfPresent(object, synthetic, "next", "stage");
            copyIfPresent(object, synthetic, "stage", "stage");
            copyIfPresent(object, synthetic, "scene", "scene");
            copyIfPresent(object, synthetic, "response", "response");
            copyIfPresent(object, synthetic, "complete", "complete");
            copyIfPresent(object, synthetic, "abandon", "abandon");
            copyIfPresent(object, synthetic, "fail", "fail");
            return readTransitionObject(validator, synthetic, pointer);
        }
        return QuestV2Resource.Transition.EMPTY;
    }

    private static QuestV2Resource.Transition readTransition(
            Validator validator,
            JsonElement element,
            String pointer) {
        if (element == null || element.isJsonNull()) {
            return QuestV2Resource.Transition.EMPTY;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String stage = element.getAsString().trim();
            if (stage.isBlank()) {
                validator.error(pointer, "transition target stage must not be blank.", "Reference a stage id or remove the transition.", Set.of());
            }
            return new QuestV2Resource.Transition(stage, "", "", false, false, false);
        }
        JsonObject object = validator.object(element, pointer, "transition", true);
        if (object == null) {
            return QuestV2Resource.Transition.EMPTY;
        }
        return readTransitionObject(validator, object, pointer);
    }

    private static QuestV2Resource.Transition readTransitionObject(
            Validator validator,
            JsonObject object,
            String pointer) {
        validator.expectKeys(object, pointer, TRANSITION_KEYS);
        String stage = readString(object, "stage");
        String scene = readString(object, "scene");
        String response = readString(object, "response");
        boolean complete = readBoolean(object, "complete");
        boolean abandon = readBoolean(object, "abandon");
        boolean fail = readBoolean(object, "fail");
        int targetCount = 0;
        targetCount += stage.isBlank() ? 0 : 1;
        targetCount += scene.isBlank() ? 0 : 1;
        targetCount += response.isBlank() ? 0 : 1;
        targetCount += complete ? 1 : 0;
        targetCount += abandon ? 1 : 0;
        targetCount += fail ? 1 : 0;
        if (targetCount == 0) {
            validator.error(pointer, "transition must choose a target.", "Set exactly one of stage, scene, response, complete, abandon, or fail.", Set.of());
        } else if (targetCount > 1) {
            validator.error(pointer, "transition must choose only one target.", "Keep exactly one transition target.", relevantIds(stage, scene, response));
        }
        return new QuestV2Resource.Transition(stage, scene, response, complete, abandon, fail);
    }

    private static void validateGraph(Validator validator, QuestV2Resource resource) {
        if (resource.stages().isEmpty()) {
            validator.error("/stages", "at least one stage is required.", "Add an ordered stages array with an entry stage.", Set.of(resource.id().toString()));
            return;
        }
        if (resource.entryStage().isBlank()) {
            validator.error("/entry_stage", "entry_stage is required.", "Set entry_stage to the first stage id.", Set.of(resource.id().toString()));
        } else if (!resource.stagesById().containsKey(resource.entryStage())) {
            validator.error(
                    "/entry_stage",
                    "entry_stage references missing stage \"" + resource.entryStage() + "\".",
                    "Set entry_stage to an id from stages[].id.",
                    Set.of(resource.entryStage()));
        }

        Map<String, String> objectivePointers = objectivePointers(resource);
        for (QuestV2Resource.Stage stage : resource.stages()) {
            String stagePointer = stagePointer(resource, stage.id());
            for (String objectiveId : stage.completeWhenObjectives()) {
                if (!objectivePointers.containsKey(objectiveId)) {
                    validator.error(
                            stagePointer + "/complete_when",
                            "complete_when references missing objective \"" + objectiveId + "\".",
                            "Reference an objective id defined in the same quest.",
                            Set.of(objectiveId));
                }
            }
            validateTransitionReferences(validator, resource, stage.next(), stagePointer + "/next", stage.id());
            validateDialogueSlots(validator, resource, stage, stagePointer);
            for (QuestV2Resource.Response response : stage.responses()) {
                validateTransitionReferences(validator, resource, response.transition(), stagePointer + "/responses/" + response.id(), stage.id());
            }
            for (QuestV2Resource.Event event : stage.events()) {
                validateEventReferences(validator, resource, event, stagePointer + "/events/" + event.id(), stage.id());
            }
        }
        for (QuestV2Resource.Event event : resource.events()) {
            validateEventReferences(validator, resource, event, "/events/" + event.id(), "");
        }
        validateReachability(validator, resource);
    }

    private static void validateDialogueSlots(
            Validator validator,
            QuestV2Resource resource,
            QuestV2Resource.Stage stage,
            String stagePointer) {
        for (QuestV2Resource.DialogueSlot slot : stage.dialogueSlots().values()) {
            String slotPointer = stagePointer + "/dialogue/" + escapePointer(slot.slot());
            if (!slot.scene().isBlank() && !stage.scenes().containsKey(slot.scene())) {
                validator.error(
                        slotPointer + "/scene",
                        "dialogue slot references missing local scene \"" + slot.scene() + "\".",
                        "Reference a scene id from this stage's scenes array.",
                        Set.of(stage.id(), slot.scene()));
            }
            QuestV2Resource.Scene inlineScene = slot.inlineScene();
            if (inlineScene != null) {
                for (QuestV2Resource.Response response : inlineScene.responses()) {
                    validateTransitionReferences(
                            validator,
                            resource,
                            response.transition(),
                            slotPointer + "/responses/" + response.id(),
                            stage.id());
                }
            }
        }
        for (QuestV2Resource.Scene scene : stage.scenes().values()) {
            for (QuestV2Resource.Response response : scene.responses()) {
                validateTransitionReferences(
                        validator,
                        resource,
                        response.transition(),
                        stagePointer + "/scenes/" + scene.id() + "/responses/" + response.id(),
                        stage.id());
            }
        }
    }

    private static void validateEventReferences(
            Validator validator,
            QuestV2Resource resource,
            QuestV2Resource.Event event,
            String pointer,
            String localStage) {
        for (String stageId : event.stages()) {
            if (!resource.stagesById().containsKey(stageId)) {
                validator.error(
                        pointer + "/stages",
                        "event references missing stage \"" + stageId + "\".",
                        "Reference a stage id from stages[].id.",
                        Set.of(event.id(), stageId));
            }
        }
        validateTransitionReferences(validator, resource, event.transition(), pointer + "/transition", localStage);
    }

    private static void validateTransitionReferences(
            Validator validator,
            QuestV2Resource resource,
            QuestV2Resource.Transition transition,
            String pointer,
            String localStage) {
        if (transition == null || transition.isEmpty()) {
            return;
        }
        if (!transition.stage().isBlank() && !resource.stagesById().containsKey(transition.stage())) {
            validator.error(
                    pointer,
                    "transition references missing stage \"" + transition.stage() + "\".",
                    "Reference a stage id from stages[].id.",
                    Set.of(transition.stage()));
        }
        if (!transition.scene().isBlank() && !localStage.isBlank()) {
            QuestV2Resource.Stage stage = resource.stagesById().get(localStage);
            if (stage != null && !stage.scenes().containsKey(transition.scene())) {
                validator.error(
                        pointer,
                        "transition references missing local scene \"" + transition.scene() + "\".",
                        "Reference a scene id from the current stage.",
                        Set.of(localStage, transition.scene()));
            }
        }
    }

    private static void validateTransitionReferences(
            Validator validator,
            QuestV2Resource.Stage stage,
            QuestV2Resource.Transition transition,
            String pointer) {
        if (transition == null || transition.isEmpty()) {
            return;
        }
        if (!transition.scene().isBlank() && !stage.scenes().containsKey(transition.scene())) {
            validator.error(
                    pointer,
                    "transition references missing local scene \"" + transition.scene() + "\".",
                    "Reference a scene id from this stage.",
                    Set.of(stage.id(), transition.scene()));
        }
    }

    private static void validateResponseTransitionActionConflicts(
            Validator validator,
            String pointer,
            QuestV2Resource.Transition transition,
            List<JsonObject> actions) {
        if (transition == null
                || transition.isEmpty()
                || !transition.scene().isBlank()
                || !transition.response().isBlank()
                || actions == null
                || actions.isEmpty()) {
            return;
        }
        for (int index = 0; index < actions.size(); index++) {
            if (isStageChangingAction(actions.get(index))) {
                validator.error(
                        pointer + "/actions/" + index,
                        "response cannot define both a transition and a stage-changing action.",
                        "Use transition or next for branch flow, and keep actions for side effects.",
                        Set.of());
            }
        }
    }

    private static boolean isStageChangingAction(JsonObject action) {
        if (action == null) {
            return false;
        }
        String type = VillagerActionRegistry.canonicalTypeId(readString(action, "type", "action"));
        if ("quest_transition".equals(type)) {
            return true;
        }
        if ("set_variable".equals(type)) {
            String key = firstNonBlank(
                    readString(action, "variable"),
                    firstNonBlank(readString(action, "key"), readString(action, "fact")));
            return action.has("stage") || "stage".equals(key);
        }
        if (!"quest".equals(type)) {
            return false;
        }
        VillagerActionDefinition.QuestAction questAction =
                VillagerActionDefinition.QuestAction.bySerializedName(readString(action, "action"));
        return questAction == VillagerActionDefinition.QuestAction.START
                || questAction == VillagerActionDefinition.QuestAction.TURN_IN
                || questAction == VillagerActionDefinition.QuestAction.ABANDON
                || questAction == VillagerActionDefinition.QuestAction.BLOCK;
    }

    private static void validateReachability(Validator validator, QuestV2Resource resource) {
        if (resource.entryStage().isBlank() || !resource.stagesById().containsKey(resource.entryStage())) {
            return;
        }
        Set<String> reachable = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(resource.entryStage());
        while (!queue.isEmpty()) {
            String stageId = queue.removeFirst();
            if (!reachable.add(stageId)) {
                continue;
            }
            QuestV2Resource.Stage stage = resource.stagesById().get(stageId);
            if (stage == null) {
                continue;
            }
            enqueueStage(queue, resource, stage.next());
            for (QuestV2Resource.Response response : stage.responses()) {
                enqueueStage(queue, resource, response.transition());
            }
            for (QuestV2Resource.Event event : stage.events()) {
                enqueueStage(queue, resource, event.transition());
            }
            for (QuestV2Resource.DialogueSlot slot : stage.dialogueSlots().values()) {
                QuestV2Resource.Scene inlineScene = slot.inlineScene();
                if (inlineScene == null) {
                    continue;
                }
                for (QuestV2Resource.Response response : inlineScene.responses()) {
                    enqueueStage(queue, resource, response.transition());
                }
            }
            for (QuestV2Resource.Scene scene : stage.scenes().values()) {
                for (QuestV2Resource.Response response : scene.responses()) {
                    enqueueStage(queue, resource, response.transition());
                }
            }
        }
        for (QuestV2Resource.Stage stage : resource.stages()) {
            if (!reachable.contains(stage.id())) {
                validator.error(
                        stagePointer(resource, stage.id()) + "/id",
                        "stage \"" + stage.id() + "\" is unreachable from entry_stage.",
                        "Link to it from an earlier transition or remove it.",
                        Set.of(stage.id()));
            }
        }
    }

    private static void enqueueStage(
            ArrayDeque<String> queue,
            QuestV2Resource resource,
            QuestV2Resource.Transition transition) {
        if (transition != null && !transition.stage().isBlank() && resource.stagesById().containsKey(transition.stage())) {
            queue.addLast(transition.stage());
        }
    }

    private static Map<String, QuestV2Resource.Stage> indexStages(
            Validator validator,
            List<QuestV2Resource.Stage> stages) {
        Map<String, QuestV2Resource.Stage> byId = new LinkedHashMap<>();
        for (int index = 0; index < stages.size(); index++) {
            QuestV2Resource.Stage stage = stages.get(index);
            if (stage.id().isBlank()) {
                continue;
            }
            QuestV2Resource.Stage previous = byId.putIfAbsent(stage.id(), stage);
            if (previous != null) {
                validator.error(
                        "/stages/" + index + "/id",
                        "duplicate stage id \"" + stage.id() + "\".",
                        "Use unique stage ids within a quest.",
                        Set.of(stage.id()));
            }
        }
        return Map.copyOf(byId);
    }

    private static Map<String, QuestV2Resource.Scene> indexScenes(
            Validator validator,
            List<QuestV2Resource.Scene> scenes,
            String pointer) {
        Map<String, QuestV2Resource.Scene> byId = new LinkedHashMap<>();
        for (int index = 0; index < scenes.size(); index++) {
            QuestV2Resource.Scene scene = scenes.get(index);
            if (scene.id().isBlank()) {
                continue;
            }
            QuestV2Resource.Scene previous = byId.putIfAbsent(scene.id(), scene);
            if (previous != null) {
                validator.error(
                        pointer + "/" + index + "/id",
                        "duplicate scene id \"" + scene.id() + "\".",
                        "Use unique scene ids within a stage.",
                        Set.of(scene.id()));
            }
        }
        return Map.copyOf(byId);
    }

    private static Map<String, String> objectivePointers(QuestV2Resource resource) {
        Map<String, String> pointers = new LinkedHashMap<>();
        for (QuestV2Resource.Stage stage : resource.stages()) {
            int index = 0;
            for (QuestV2Resource.Objective objective : stage.objectives()) {
                if (!objective.id().isBlank()) {
                    pointers.put(objective.id(), stagePointer(resource, stage.id()) + "/objectives/" + index);
                }
                index++;
            }
        }
        return Map.copyOf(pointers);
    }

    private static List<String> readObjectiveReferences(Validator validator, JsonElement element, String pointer) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            return readStringList(element);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String objective = readString(object, "objective", "objective_id");
            String type = readString(object, "type");
            if (objective.isBlank() && ("objective".equals(type) || "objectives".equals(type))) {
                objective = readString(object, "id");
            }
            return objective.isBlank() ? List.of() : List.of(objective);
        }
        JsonArray array = validator.array(element, pointer, "objective references", true);
        if (array == null) {
            return List.of();
        }
        List<String> references = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement child = array.get(index);
            if (child.isJsonPrimitive()) {
                String value = child.getAsString().trim();
                if (!value.isBlank()) {
                    references.add(value);
                }
                continue;
            }
            JsonObject object = validator.object(child, pointer + "/" + index, "objective predicate", true);
            if (object != null) {
                String objective = readString(object, "objective", "objective_id");
                String type = readString(object, "type");
                if (objective.isBlank() && ("objective".equals(type) || "objectives".equals(type))) {
                    objective = readString(object, "id");
                }
                if (!objective.isBlank()) {
                    references.add(objective);
                }
            }
        }
        return List.copyOf(references);
    }

    private static List<JsonObject> readConditionObjects(Validator validator, JsonElement element, String pointer) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<JsonObject> conditions = new ArrayList<>();
        if (element.isJsonObject()) {
            validateConditionObject(validator, element.getAsJsonObject(), pointer);
            conditions.add(element.getAsJsonObject());
            return List.copyOf(conditions);
        }
        JsonArray array = validator.array(element, pointer, "conditions", true);
        if (array == null) {
            return List.of();
        }
        for (int index = 0; index < array.size(); index++) {
            JsonObject object = validator.object(array.get(index), pointer + "/" + index, "condition", true);
            if (object == null) {
                continue;
            }
            validateConditionObject(validator, object, pointer + "/" + index);
            conditions.add(object);
        }
        return List.copyOf(conditions);
    }

    private static void validateConditionObject(Validator validator, JsonObject object, String pointer) {
        String type = readString(object, "type");
        if (type.isBlank()) {
            validator.error(pointer + "/type", "condition type is required.", "Use a registered condition type.", Set.of());
            return;
        }
        String canonical = DialogueCondition.canonicalTypeId(type);
        boolean known = DialogueCondition.descriptors().stream().anyMatch(descriptor -> descriptor.id().equals(canonical));
        if (!known) {
            validator.error(
                    pointer + "/type",
                    "unknown condition type \"" + type + "\".",
                    "Use one of the condition types exported in quest-registry-metadata.json.",
                    Set.of(type));
        }
    }

    private static List<JsonObject> readActionObjects(Validator validator, JsonElement element, String pointer) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        JsonArray array = validator.array(element, pointer, "actions", true);
        if (array == null) {
            return List.of();
        }
        List<JsonObject> actions = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonObject object = validator.object(array.get(index), pointer + "/" + index, "action", true);
            if (object == null) {
                continue;
            }
            String type = readString(object, "type", "action");
            if (type.isBlank()) {
                validator.error(pointer + "/" + index + "/type", "action type is required.", "Use a registered action type.", Set.of());
            } else if (VillagerActionRegistry.kindBySerializedName(type) == VillagerActionDefinition.Kind.NONE) {
                validator.error(
                        pointer + "/" + index + "/type",
                        "unknown action type \"" + type + "\".",
                        "Use one of the action types exported in quest-registry-metadata.json.",
                        Set.of(type));
            }
            actions.add(object);
        }
        return List.copyOf(actions);
    }

    private static void validatePlaceholders(
            Validator validator,
            String value,
            Set<String> placeholders,
            String pointer) {
        if (value == null || value.isBlank()) {
            return;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!placeholders.contains(name)) {
                validator.error(
                        pointer,
                        "text references undefined UI placeholder {" + name + "}.",
                        "Add ui.placeholders." + name + " or remove the placeholder.",
                        Set.of(name));
            }
        }
    }

    private static void validateId(Validator validator, String pointer, String id, String label) {
        if (id == null || id.isBlank()) {
            validator.error(pointer, label + " is required.", "Add a stable non-empty id.", Set.of());
            return;
        }
        if (isReservedGeneratedId(id)) {
            validator.error(
                    pointer,
                    label + " \"" + id + "\" collides with reserved generated-id space.",
                    "Use an id that does not start with " + GENERATED_ID_PREFIX + " or vr$.",
                    Set.of(id));
        }
    }

    private static boolean isReservedGeneratedId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(GENERATED_ID_PREFIX) || normalized.startsWith("vr$");
    }

    private static Optional<QuestProviderTypeDescriptor> providerDescriptor(ResourceLocation id) {
        return QuestProviderRegistry.descriptors().stream()
                .filter(descriptor -> descriptor.id().equals(id))
                .findFirst();
    }

    private static QuestV2Resource.ExternalScene readExternalScene(
            Validator validator,
            JsonObject object,
            String pointer,
            String defaultEntry,
            String... extraKeys) {
        JsonElement element = null;
        String key = "";
        List<String> keys = new ArrayList<>();
        keys.add("external_scene");
        keys.add("external");
        if (extraKeys != null) {
            keys.addAll(List.of(extraKeys));
        }
        for (String candidate : keys) {
            if (object.has(candidate)) {
                element = object.get(candidate);
                key = candidate;
                break;
            }
        }
        String entry = firstNonBlank(readString(object, "external_entry"), defaultEntry);
        if (element == null || element.isJsonNull()) {
            return QuestV2Resource.ExternalScene.EMPTY;
        }
        String externalPointer = pointer + "/" + escapePointer(key);
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            if (value.isBlank()) {
                return QuestV2Resource.ExternalScene.EMPTY;
            }
            Optional<ResourceLocation> tree = DatapackJsonReader.parseResourceLocation(value);
            if (tree.isEmpty()) {
                validator.error(
                        externalPointer,
                        "invalid external dialogue tree \"" + value + "\".",
                        "Use a resource location such as namespace:path.",
                        Set.of(value));
                return QuestV2Resource.ExternalScene.EMPTY;
            }
            return new QuestV2Resource.ExternalScene(tree.get(), entry);
        }
        JsonObject external = validator.object(element, externalPointer, "external dialogue scene", true);
        if (external == null) {
            return QuestV2Resource.ExternalScene.EMPTY;
        }
        validator.expectKeys(external, externalPointer, EXTERNAL_SCENE_KEYS);
        String treeValue = readString(external, "tree", "tree_id", "dialogue_tree");
        if (treeValue.isBlank()) {
            validator.error(
                    externalPointer + "/tree",
                    "external dialogue scene must define tree.",
                    "Set external.tree to the dialogue tree resource id.",
                    Set.of());
            return QuestV2Resource.ExternalScene.EMPTY;
        }
        Optional<ResourceLocation> tree = DatapackJsonReader.parseResourceLocation(treeValue);
        if (tree.isEmpty()) {
            validator.error(
                    externalPointer + "/tree",
                    "invalid external dialogue tree \"" + treeValue + "\".",
                    "Use a resource location such as namespace:path.",
                    Set.of(treeValue));
            return QuestV2Resource.ExternalScene.EMPTY;
        }
        return new QuestV2Resource.ExternalScene(
                tree.get(),
                firstNonBlank(readString(external, "entry", "entry_id"), entry));
    }

    private static Optional<ResourceLocation> readResourceLocation(
            Validator validator,
            JsonObject object,
            String pointer,
            String... keys) {
        String value = readString(object, keys);
        if (value.isBlank()) {
            return Optional.empty();
        }
        Optional<ResourceLocation> location = DatapackJsonReader.parseResourceLocation(value);
        if (location.isEmpty()) {
            validator.error(
                    pointer,
                    "invalid resource location \"" + value + "\".",
                    "Use a namespaced id such as villagerretaliation:example.",
                    Set.of(value));
        }
        return location;
    }

    private static Optional<ResourceLocation> readResourceLocation(JsonObject object, String... keys) {
        return DatapackJsonReader.parseResourceLocation(readString(object, keys));
    }

    private static Set<ResourceLocation> readResourceLocationSet(
            Validator validator,
            JsonObject object,
            String pointer,
            String... keys) {
        Set<ResourceLocation> values = new LinkedHashSet<>();
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            int index = 0;
            for (String value : readStringList(element)) {
                Optional<ResourceLocation> parsed = DatapackJsonReader.parseResourceLocation(value);
                if (parsed.isPresent()) {
                    values.add(parsed.get());
                } else {
                    validator.error(
                            pointer + "/" + index,
                            "invalid resource location \"" + value + "\".",
                            "Use a namespaced id such as villagerretaliation:live_provider.",
                            Set.of(value));
                }
                index++;
            }
        }
        return Set.copyOf(values);
    }

    private static List<ResourceLocation> readResourceLocationList(
            Validator validator,
            JsonElement element,
            String pointer) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<ResourceLocation> values = new ArrayList<>();
        int index = 0;
        for (String value : readStringList(element)) {
            Optional<ResourceLocation> parsed = DatapackJsonReader.parseResourceLocation(value);
            if (parsed.isPresent()) {
                values.add(parsed.get());
            } else {
                validator.error(
                        pointer + "/" + index,
                        "invalid prerequisite quest id \"" + value + "\".",
                        "Use a namespaced quest id such as villagerretaliation:first_quest.",
                        Set.of(value));
            }
            index++;
        }
        return List.copyOf(values);
    }

    private static QuestV2Resource.TextSpec readTextSpec(JsonObject object) {
        return new QuestV2Resource.TextSpec(
                readString(object, "label", "text"),
                readString(object, "label_key", "text_key"),
                readStringList(object.get("lines")));
    }

    private static String readString(JsonObject object, String... keys) {
        if (object == null) {
            return "";
        }
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsString().trim();
            }
        }
        return "";
    }

    private static boolean readBoolean(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return DatapackJsonReader.readBoolean(element, false);
    }

    private static List<String> readStringList(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            if (!value.isBlank()) {
                values.add(value);
            }
            return List.copyOf(values);
        }
        if (!element.isJsonArray()) {
            return List.of();
        }
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) {
                String value = child.getAsString().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<String> append(List<String> values, String value) {
        List<String> copy = new ArrayList<>(values == null ? List.of() : values);
        if (value != null && !value.isBlank()) {
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    private static boolean hasInlineSceneContent(JsonObject object) {
        return object != null
                && (object.has("lines")
                        || object.has("text")
                        || object.has("text_key")
                        || object.has("responses"));
    }

    private static boolean hasAny(JsonObject object, String... keys) {
        if (object == null) {
            return false;
        }
        for (String key : keys) {
            if (object.has(key)) {
                return true;
            }
        }
        return false;
    }

    private static JsonElement firstElement(JsonObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            if (object.has(key)) {
                return object.get(key);
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static Set<String> relevantIds(String... values) {
        if (values == null || values.length == 0) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                ids.add(value.trim());
            }
        }
        return Set.copyOf(ids);
    }

    private static void copyIfPresent(JsonObject source, JsonObject target, String sourceKey, String targetKey) {
        if (source.has(sourceKey)) {
            target.add(targetKey, source.get(sourceKey));
        }
    }

    private static ResourceLocation fallbackQuestId(ResourceLocation location) {
        if (location == null) {
            return null;
        }
        String path = location.getPath();
        if (!path.startsWith(RESOURCE_ROOT + "/") || !path.endsWith(".json")) {
            return null;
        }
        String questPath = path.substring((RESOURCE_ROOT + "/").length(), path.length() - ".json".length());
        return ResourceLocation.tryParse(location.getNamespace() + ":" + questPath);
    }

    private static String stagePointer(QuestV2Resource resource, String stageId) {
        for (int index = 0; index < resource.stages().size(); index++) {
            if (resource.stages().get(index).id().equals(stageId)) {
                return "/stages/" + index;
            }
        }
        return "/stages";
    }

    private static boolean isPlaceholderName(String value) {
        return value != null && value.matches("[a-zA-Z0-9_]+");
    }

    private static String escapePointer(String value) {
        return value == null ? "" : value.replace("~", "~0").replace("/", "~1");
    }

    private static final class Validator {
        private final ResourceLocation location;
        private boolean valid = true;

        private Validator(ResourceLocation location) {
            this.location = location;
        }

        private boolean valid() {
            return this.valid;
        }

        private void expectKeys(JsonObject object, String pointer, Set<String> allowedKeys) {
            if (object == null) {
                return;
            }
            for (String key : object.keySet()) {
                if (!allowedKeys.contains(key)) {
                    error(
                            childPointer(pointer, key),
                            "unsupported field \"" + key + "\".",
                            "Remove the field or move it to a supported quest module v2 location.",
                            Set.of(key));
                }
            }
        }

        private JsonObject object(JsonElement element, String pointer, String label, boolean required) {
            if (element == null || element.isJsonNull()) {
                if (required) {
                    error(pointer, label + " must be an object.", "Provide an object for " + label + ".", Set.of());
                }
                return null;
            }
            if (!element.isJsonObject()) {
                error(pointer, label + " must be an object.", "Provide an object for " + label + ".", Set.of());
                return null;
            }
            return element.getAsJsonObject();
        }

        private JsonObject optionalObject(JsonElement element, String pointer, String label) {
            if (element == null || element.isJsonNull()) {
                return null;
            }
            return object(element, pointer, label, true);
        }

        private JsonArray array(JsonElement element, String pointer, String label, boolean required) {
            if (element == null || element.isJsonNull()) {
                if (required) {
                    error(pointer, label + " must be an array.", "Provide an array for " + label + ".", Set.of());
                }
                return null;
            }
            if (!element.isJsonArray()) {
                error(pointer, label + " must be an array.", "Provide an array for " + label + ".", Set.of());
                return null;
            }
            return element.getAsJsonArray();
        }

        private void error(String pointer, String message, String suggestedFix, Set<String> relevantIds) {
            this.valid = false;
            DatapackDiagnostics.warnQuestV2Validation(
                    this.location,
                    pointer,
                    message,
                    suggestedFix,
                    relevantIds == null ? Set.of() : new HashSet<>(relevantIds));
        }

        private static String childPointer(String pointer, String key) {
            String escaped = escapePointer(key);
            return pointer == null || pointer.isBlank() ? "/" + escaped : pointer + "/" + escaped;
        }
    }
}
