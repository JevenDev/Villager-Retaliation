package com.jvn.villagerretaliation.action;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class VillagerActionRegistry {
    private static final List<ActionTypeDescriptor> REGISTRATIONS = List.of(
            register(
                    "notification",
                    VillagerActionDefinition.Kind.NOTIFICATION,
                    aliases("notify", "hud", "message"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE)),
            register(
                    "tracker",
                    VillagerActionDefinition.Kind.TRACKER,
                    aliases("quest_tracker", "flash_tracker"),
                    capabilities(ActionCapability.PLAYER_LIVE)),
            register(
                    "forced_dialogue",
                    VillagerActionDefinition.Kind.FORCED_DIALOGUE,
                    aliases("force_dialogue", "dialogue"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE)),
            register(
                    "quest",
                    VillagerActionDefinition.Kind.QUEST,
                    aliases("quest_action"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE, ActionCapability.PROVIDER_SNAPSHOT)),
            register(
                    "quest_transition",
                    VillagerActionDefinition.Kind.QUEST_TRANSITION,
                    aliases("response_transition", "quest_response_transition", "branch_transition"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE, ActionCapability.WORLD_KNOWN)),
            register(
                    "experience",
                    VillagerActionDefinition.Kind.EXPERIENCE,
                    aliases("xp"),
                    capabilities(ActionCapability.PLAYER_LIVE)),
            register(
                    "reputation",
                    VillagerActionDefinition.Kind.REPUTATION,
                    aliases("rep"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE)),
            register(
                    "gossip",
                    VillagerActionDefinition.Kind.GOSSIP,
                    aliases("gossip_reputation"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE)),
            register(
                    "memory",
                    VillagerActionDefinition.Kind.MEMORY,
                    aliases("memory_event"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE, ActionCapability.WORLD_KNOWN)),
            register(
                    "loot",
                    VillagerActionDefinition.Kind.LOOT,
                    aliases("loot_table"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.WORLD_KNOWN)),
            register(
                    "set_tag",
                    VillagerActionDefinition.Kind.SET_TAG,
                    aliases("quest_tag", "add_tag", "tag"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.WORLD_KNOWN,
                            ActionCapability.PROVIDER_LIVE, ActionCapability.PROVIDER_SNAPSHOT)),
            register(
                    "clear_tag",
                    VillagerActionDefinition.Kind.CLEAR_TAG,
                    aliases("remove_tag", "unset_tag"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.WORLD_KNOWN,
                            ActionCapability.PROVIDER_LIVE, ActionCapability.PROVIDER_SNAPSHOT)),
            register(
                    "set_variable",
                    VillagerActionDefinition.Kind.SET_VARIABLE,
                    aliases("variable", "set_fact", "fact", "set_stage", "quest_stage", "stage"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.WORLD_KNOWN,
                            ActionCapability.PROVIDER_LIVE, ActionCapability.PROVIDER_SNAPSHOT)),
            register(
                    "counter",
                    VillagerActionDefinition.Kind.COUNTER,
                    aliases("increment_counter", "add_counter"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.WORLD_KNOWN,
                            ActionCapability.PROVIDER_LIVE, ActionCapability.PROVIDER_SNAPSHOT)),
            register(
                    "start_scene",
                    VillagerActionDefinition.Kind.START_SCENE,
                    aliases("scene", "scene_start"),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.WORLD_KNOWN,
                            ActionCapability.PROVIDER_LIVE, ActionCapability.PROVIDER_SNAPSHOT))
    );
    private static final Map<String, ActionTypeDescriptor> BY_ALIAS = descriptorsByAlias();
    private static final Map<VillagerActionDefinition.Kind, ActionTypeDescriptor> BY_KIND = descriptorsByKind();

    private VillagerActionRegistry() {
    }

    public static List<ActionTypeDescriptor> descriptors() {
        return REGISTRATIONS;
    }

    public static VillagerActionDefinition.Kind kindBySerializedName(String value) {
        ActionTypeDescriptor descriptor = BY_ALIAS.get(normalizeType(value));
        return descriptor == null ? VillagerActionDefinition.Kind.NONE : descriptor.kind();
    }

    public static String canonicalTypeId(String type) {
        String normalized = normalizeType(type);
        ActionTypeDescriptor descriptor = BY_ALIAS.get(normalized);
        return descriptor == null ? normalized : descriptor.id();
    }

    public static String canonicalTypeId(VillagerActionDefinition action) {
        if (action == null) {
            return "none";
        }
        ActionTypeDescriptor descriptor = BY_KIND.get(action.kind());
        return descriptor == null ? action.kind().serializedName() : descriptor.id();
    }

    public static Set<ActionCapability> capabilities(VillagerActionDefinition action) {
        if (action == null) {
            return Set.of();
        }
        ActionTypeDescriptor descriptor = BY_KIND.get(action.kind());
        return descriptor == null ? Set.of() : descriptor.capabilities();
    }

    public static ActionResult execute(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements) {
        return run(context, action, inheritedReplacements, false);
    }

    public static ActionResult dryRun(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements) {
        return run(context, action, inheritedReplacements, true);
    }

    private static ActionResult run(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements,
            boolean dryRun) {
        if (action == null || action.kind() == VillagerActionDefinition.Kind.NONE) {
            return ActionResult.skipped("action is empty", Set.of());
        }
        Set<ActionCapability> capabilities = capabilities(action);
        if (dryRun) {
            return ActionResult.skipped("dry run", capabilities);
        }
        if (context == null && requiresLiveContext(capabilities)) {
            return ActionResult.failed("live dialogue context unavailable", capabilities);
        }
        VillagerActionResult legacyResult =
                VillagerActionExecutor.executeDirect(context, action, inheritedReplacements);
        return legacyResult.ran()
                ? ActionResult.success(legacyResult, capabilities)
                : ActionResult.skipped("action made no changes", capabilities, legacyResult);
    }

    private static boolean requiresLiveContext(Set<ActionCapability> capabilities) {
        return capabilities.contains(ActionCapability.PLAYER_LIVE)
                || capabilities.contains(ActionCapability.PROVIDER_LIVE);
    }

    private static ActionTypeDescriptor register(
            String id,
            VillagerActionDefinition.Kind kind,
            Set<String> aliases,
            Set<ActionCapability> capabilities) {
        return new ActionTypeDescriptor(id, kind, aliases, capabilities);
    }

    private static Map<String, ActionTypeDescriptor> descriptorsByAlias() {
        Map<String, ActionTypeDescriptor> descriptors = new LinkedHashMap<>();
        for (ActionTypeDescriptor descriptor : REGISTRATIONS) {
            descriptors.put(descriptor.id(), descriptor);
            for (String alias : descriptor.aliases()) {
                descriptors.put(normalizeType(alias), descriptor);
            }
        }
        return Map.copyOf(descriptors);
    }

    private static Map<VillagerActionDefinition.Kind, ActionTypeDescriptor> descriptorsByKind() {
        Map<VillagerActionDefinition.Kind, ActionTypeDescriptor> descriptors = new HashMap<>();
        for (ActionTypeDescriptor descriptor : REGISTRATIONS) {
            descriptors.put(descriptor.kind(), descriptor);
        }
        return Map.copyOf(descriptors);
    }

    private static Set<String> aliases(String... aliases) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (aliases != null) {
            for (String alias : aliases) {
                String normalized = normalizeType(alias);
                if (!normalized.isBlank()) {
                    result.add(normalized);
                }
            }
        }
        return Set.copyOf(result);
    }

    private static Set<ActionCapability> capabilities(ActionCapability... capabilities) {
        return capabilities == null || capabilities.length == 0
                ? Set.of()
                : Set.of(capabilities);
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    public record ActionTypeDescriptor(
            String id,
            VillagerActionDefinition.Kind kind,
            Set<String> aliases,
            Set<ActionCapability> capabilities
    ) {
        public ActionTypeDescriptor {
            id = normalizeType(id);
            kind = kind == null ? VillagerActionDefinition.Kind.NONE : kind;
            aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
    }
}
