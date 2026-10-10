package com.jvn.villagerretaliation.api;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.QuestTriggerRegistry;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.provider.QuestProviderRegistry;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ClientSync;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ToolingMetadata;
import com.jvn.villagerretaliation.api.registry.FreezableExtensionRegistry;
import com.jvn.villagerretaliation.api.registry.RuntimeTypeDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Scene extensions execute through attached step executors. Quest registries expose descriptor metadata. */
public final class VillagerRetaliationRegistries {
    public static final FreezableExtensionRegistry<RuntimeTypeDescriptor> ACTOR_TYPES = registry("actor type");
    public static final FreezableExtensionRegistry<RuntimeTypeDescriptor> SCENE_STEPS = registry("scene step");
    public static final FreezableExtensionRegistry<RuntimeTypeDescriptor> ENCOUNTER_TEMPLATES = registry("encounter template");
    public static final FreezableExtensionRegistry<RuntimeTypeDescriptor> QUEST_PROVIDERS = registry("quest provider");
    public static final FreezableExtensionRegistry<RuntimeTypeDescriptor> QUEST_OBJECTIVES = registry("quest objective");
    public static final FreezableExtensionRegistry<RuntimeTypeDescriptor> QUEST_ACTIONS = registry("quest action");
    public static final FreezableExtensionRegistry<RuntimeTypeDescriptor> QUEST_CONDITIONS = registry("quest condition");
    public static final FreezableExtensionRegistry<RuntimeTypeDescriptor> QUEST_TRIGGER_EVENTS = registry("quest trigger event");

    private static volatile boolean builtInsRegistered;

    private VillagerRetaliationRegistries() {
    }

    public static synchronized void registerBuiltIns() {
        if (builtInsRegistered) {
            return;
        }
        registerActor("player", Set.of(capability("live_entity"), capability("player")), Set.of(capability("identity")));
        registerActor("villager", Set.of(capability("live_entity"), capability("living"), capability("pathfinding"), capability("dialogue")),
                Set.of(capability("identity"), capability("display"), capability("position")));
        registerActor("living_entity", Set.of(capability("live_entity"), capability("living"), capability("pathfinding")),
                Set.of(capability("identity"), capability("display"), capability("position")));
        registerActor("hostile_encounter_group", Set.of(capability("encounter")), Set.of(capability("identity"), capability("members")));
        registerActor("position", Set.of(capability("position")), Set.of(capability("position")));

        for (String id : List.of("wait_ticks", "wait_condition", "move_actor", "face_actor", "face_position", "dialogue",
                "action_batch", "quest_transition", "scene_branch", "scene_complete", "scene_fail",
                "start_encounter", "wait_encounter", "cancel_encounter", "cleanup_encounter")) {
            registerSimple(SCENE_STEPS, id, RecoveryMode.WORLD_RECONCILED, ClientSync.PARTICIPANTS);
        }
        SCENE_STEPS.register(com.jvn.villagerretaliation.scene.model.CampaignFollowDefinition.descriptor());
        registerSimple(ENCOUNTER_TEMPLATES, "controlled", RecoveryMode.WORLD_RECONCILED, ClientSync.PARTICIPANTS);

        for (var provider : QuestProviderRegistry.descriptors()) {
            QUEST_PROVIDERS.register(descriptor(provider.id().getPath(), Set.of(), provider.capabilities(), Set.of(),
                    RecoveryMode.NATURALLY_IDEMPOTENT, ClientSync.NONE, new JsonObject()));
        }
        for (var objective : QuestObjectiveRegistry.descriptors()) {
            JsonObject schema = objective.implementation() == null ? new JsonObject()
                    : objective.implementation().structuralSchema().orElseGet(JsonObject::new);
            QUEST_OBJECTIVES.register(descriptor(objective.id(), namespaced(objective.aliases()), Set.of(), Set.of(),
                    RecoveryMode.NATURALLY_IDEMPOTENT, ClientSync.OWNER, schema));
        }
        for (var action : VillagerActionRegistry.descriptors()) {
            QUEST_ACTIONS.register(descriptor(action.id(), namespaced(action.aliases()),
                    capabilities(action.capabilities(), false), capabilities(action.capabilities(), true),
                    action.recoveryMode(), ClientSync.OWNER, action.schema()));
        }
        for (var condition : DialogueCondition.descriptors()) {
            QUEST_CONDITIONS.register(descriptor(condition.id(), namespaced(condition.aliases()),
                    capabilities(condition.capabilities(), false), capabilities(condition.capabilities(), true),
                    RecoveryMode.NATURALLY_IDEMPOTENT, ClientSync.NONE, condition.schema()));
        }
        for (var trigger : QuestTriggerRegistry.descriptors()) {
            QUEST_TRIGGER_EVENTS.register(descriptor(trigger.id(), namespaced(trigger.aliases()), Set.of(), Set.of(),
                    RecoveryMode.NATURALLY_IDEMPOTENT, ClientSync.NONE, new JsonObject()));
        }
        builtInsRegistered = true;
    }

    public static synchronized void freezeForDatapackCompilation() {
        registerBuiltIns();
        ACTOR_TYPES.freeze();
        SCENE_STEPS.freeze();
        ENCOUNTER_TEMPLATES.freeze();
        QUEST_PROVIDERS.freeze();
        QUEST_OBJECTIVES.freeze();
        QUEST_ACTIONS.freeze();
        QUEST_CONDITIONS.freeze();
        QUEST_TRIGGER_EVENTS.freeze();
    }

    public static boolean frozen() {
        return ACTOR_TYPES.frozen() && SCENE_STEPS.frozen() && ENCOUNTER_TEMPLATES.frozen()
                && QUEST_PROVIDERS.frozen() && QUEST_OBJECTIVES.frozen() && QUEST_ACTIONS.frozen()
                && QUEST_CONDITIONS.frozen() && QUEST_TRIGGER_EVENTS.frozen();
    }

    private static void registerActor(String path, Set<ResourceLocation> live, Set<ResourceLocation> snapshot) {
        ACTOR_TYPES.register(descriptor(path, live, snapshot, RecoveryMode.WORLD_RECONCILED, ClientSync.PARTICIPANTS));
    }

    private static void registerSimple(FreezableExtensionRegistry<RuntimeTypeDescriptor> registry, String path,
            RecoveryMode recovery, ClientSync sync) {
        registry.register(descriptor(path, Set.of(), Set.of(), recovery, sync));
    }

    private static RuntimeTypeDescriptor descriptor(String path, Set<ResourceLocation> live, Set<ResourceLocation> snapshot,
            RecoveryMode recovery, ClientSync sync) {
        return descriptor(path, Set.of(), live, snapshot, recovery, sync, new JsonObject());
    }

    private static RuntimeTypeDescriptor descriptor(String path, Set<ResourceLocation> aliases,
            Set<ResourceLocation> live, Set<ResourceLocation> snapshot, RecoveryMode recovery, ClientSync sync,
            JsonObject schema) {
        ResourceLocation id = VillagerRetaliation.id(path);
        return new RuntimeTypeDescriptor(id, aliases, live, snapshot,
                JsonObject::deepCopy,
                value -> List.of(),
                (value, context) -> value,
                value -> id + " " + String.valueOf(value),
                recovery,
                new ToolingMetadata(humanize(path), "Built-in " + humanize(path) + " type.", schemaMetadata(schema), true),
                sync);
    }

    private static Map<String, Object> schemaMetadata(JsonObject schema) {
        if (schema == null || schema.entrySet().isEmpty()) return Map.of("type", "object");
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        schema.entrySet().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
        return fields;
    }

    private static Set<ResourceLocation> namespaced(Set<String> values) {
        return values.stream().map(VillagerRetaliation::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<ResourceLocation> capabilities(Set<? extends Enum<?>> values, boolean snapshot) {
        return values.stream()
                .filter(value -> value.name().endsWith("_SNAPSHOT") == snapshot)
                .map(value -> capability(value.name().toLowerCase(java.util.Locale.ROOT)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String humanize(String path) {
        String value = path.replace('_', ' ');
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static ResourceLocation capability(String path) {
        return VillagerRetaliation.id("capability/" + path);
    }

    private static <D extends com.jvn.villagerretaliation.api.registry.ExtensionDescriptor>
            FreezableExtensionRegistry<D> registry(String name) {
        return new FreezableExtensionRegistry<>(name);
    }
}
