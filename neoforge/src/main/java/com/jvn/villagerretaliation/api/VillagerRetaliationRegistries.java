package com.jvn.villagerretaliation.api;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ClientSync;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ToolingMetadata;
import com.jvn.villagerretaliation.api.registry.FreezableExtensionRegistry;
import com.jvn.villagerretaliation.api.registry.RuntimeTypeDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Supported registration surface. Extensions register during mod construction, before datapack compilation. */
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
        registerSimple(ENCOUNTER_TEMPLATES, "controlled", RecoveryMode.WORLD_RECONCILED, ClientSync.PARTICIPANTS);

        registerSimple(QUEST_PROVIDERS, "villager", RecoveryMode.NATURALLY_IDEMPOTENT, ClientSync.NONE);
        for (String id : List.of("mob_kill", "block_break", "block_place", "block_interact", "structure_visit",
                "location_visit", "item_check", "memory_event", "trade", "gift", "reputation", "choice", "fact", "condition")) {
            registerSimple(QUEST_OBJECTIVES, id, RecoveryMode.NATURALLY_IDEMPOTENT, ClientSync.OWNER);
        }
        for (String id : List.of("notification", "tracker", "forced_dialogue", "quest", "quest_transition", "experience",
                "reputation", "gossip", "memory", "loot", "set_tag", "clear_tag", "set_variable", "counter", "start_scene")) {
            registerSimple(QUEST_ACTIONS, id, switch (id) {
                case "set_tag", "clear_tag", "set_variable", "tracker" -> RecoveryMode.NATURALLY_IDEMPOTENT;
                default -> RecoveryMode.RECEIPT_REQUIRED;
            }, ClientSync.OWNER);
        }
        for (String id : List.of("all", "any", "not", "reputation", "memory", "family", "relationship",
                "recruitment_memory", "villager_age", "social_attribute", "skill", "villager_level", "quest",
                "quest_fact", "selected_choice", "stage_history", "mood", "weather", "time")) {
            registerSimple(QUEST_CONDITIONS, id, RecoveryMode.NATURALLY_IDEMPOTENT, ClientSync.NONE);
        }
        for (String id : List.of("player_tick", "proximity", "started", "progress", "stage_changed", "completed",
                "failed", "abandoned", "expired")) {
            registerSimple(QUEST_TRIGGER_EVENTS, id, RecoveryMode.NATURALLY_IDEMPOTENT, ClientSync.NONE);
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
        ResourceLocation id = VillagerRetaliation.id(path);
        return new RuntimeTypeDescriptor(id, Set.of(), live, snapshot,
                JsonObject::deepCopy,
                value -> List.of(),
                (value, context) -> value,
                value -> id + " " + String.valueOf(value),
                recovery,
                new ToolingMetadata(humanize(path), "Built-in " + humanize(path) + " type.", Map.of("type", "object"), true),
                sync);
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
