package com.jvn.villagerretaliation.action;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.scene.SceneLaunchService;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.quest.runtime.QuestStageActionService;
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
                    "campaign_fact",
                    VillagerActionDefinition.Kind.CAMPAIGN_FACT,
                    aliases(),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE, ActionCapability.WORLD_KNOWN)),
            register(
                    "campaign_outcome",
                    VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME,
                    aliases(),
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
                    "profile_attribute",
                    VillagerActionDefinition.Kind.PROFILE_ATTRIBUTE,
                    aliases("social_attribute", "attribute_reward"),
                    capabilities(ActionCapability.PROVIDER_LIVE)),
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
                    "draw_weapon",
                    VillagerActionDefinition.Kind.DRAW_WEAPON,
                    aliases("arm", "ready_weapon"),
                    capabilities(ActionCapability.PROVIDER_LIVE)),
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
        if (action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_FACT) {
            return com.jvn.villagerretaliation.campaign.CampaignFactService.execute(context, action);
        }
        if (action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME) {
            return com.jvn.villagerretaliation.campaign.CampaignOutcomeService.execute(context, action);
        }
        if (action.kind() == VillagerActionDefinition.Kind.FORCED_DIALOGUE) {
            ActionResult result = VillagerActionExecutor.executeForcedDialogue(context, action, inheritedReplacements);
            return new ActionResult(result.status(), result.message(), capabilities, result.legacyResult(),
                    result.suspensionId(), result.suspensionKind(), result.retryOnResume());
        }
        if (action.kind() == VillagerActionDefinition.Kind.START_SCENE) {
            SceneLaunchService.LaunchResult launch = SceneLaunchService.launch(context, action);
            if (!launch.accepted()) {
                return ActionResult.unavailable(launch.diagnostic(), capabilities, VillagerActionResult.EMPTY);
            }
            if (action.waitForScene()) return ActionResult.suspended(launch.instanceId(), capabilities);
            return launch.created() ? ActionResult.success(VillagerActionResult.success(), capabilities)
                    : ActionResult.alreadySatisfied(launch.diagnostic(), capabilities, VillagerActionResult.EMPTY);
        }
        if (context != null && action.questId() != null && SceneContinuationService.questOperation(action)) {
            var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), action.questId());
            if (progress != null && progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                    && progress.state() != VillagerQuestSavedData.QuestState.NOT_STARTED) {
                ActionResult pending = QuestStageActionService.pending(context, progress);
                if (pending != null) return new ActionResult(pending.status(), pending.message(), capabilities,
                        pending.legacyResult(), pending.suspensionId(), pending.suspensionKind(), pending.retryOnResume());
                if (terminalQuestActionSatisfied(action, progress)) {
                    return ActionResult.alreadySatisfied("quest outcome already applied", capabilities, VillagerActionResult.EMPTY);
                }
            }
        }
        VillagerActionResult legacyResult =
                VillagerActionExecutor.executeDirect(context, action, inheritedReplacements);
        if (context != null && action.questId() != null && SceneContinuationService.questOperation(action)) {
            var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), action.questId());
            ActionResult pending = progress == null ? null : QuestStageActionService.pending(context, progress);
            if (pending != null) {
                return new ActionResult(pending.status(), pending.message(), capabilities, legacyResult,
                        pending.suspensionId(), pending.suspensionKind(), pending.retryOnResume());
            }
        }
        if (action.kind() == VillagerActionDefinition.Kind.QUEST_TRANSITION) {
            return switch (legacyResult.replacements().getOrDefault("quest_transition_status", "missing")) {
                case "selected" -> ActionResult.success(legacyResult, capabilities);
                case "duplicate" -> ActionResult.alreadySatisfied("choice already recorded", capabilities, legacyResult);
                default -> ActionResult.unavailable("quest transition unavailable", capabilities, legacyResult);
            };
        }
        if (action.kind() == VillagerActionDefinition.Kind.QUEST) {
            String status = legacyResult.replacements().getOrDefault("quest_action_status", "unavailable");
            boolean applied = switch (action.questAction()) {
                case START -> status.equals("started") || status.equals("reminder");
                case REMIND -> status.equals("reminder");
                case TURN_IN -> status.equals("completed");
                case ABANDON -> status.startsWith("abandoned");
                case FAIL -> status.equals("failed");
                case BLOCK -> status.equals("branch_locked") || status.equals("consumed");
                case NONE -> false;
            };
            return applied ? ActionResult.success(legacyResult, capabilities)
                    : ActionResult.unavailable(status, capabilities, legacyResult);
        }
        if (legacyResult.ran()) return ActionResult.success(legacyResult, capabilities);
        if (factSatisfied(context, action, inheritedReplacements)) {
            var scope = action.factScope().scope(context, action.questId());
            Map<String, String> replacements = new LinkedHashMap<>(DialoguePlaceholders.base(context));
            if (inheritedReplacements != null) replacements.putAll(inheritedReplacements);
            String value = VillagerDialogueResources.resolveTemplate(action.factValue(), replacements);
            VillagerActionResult satisfied = VillagerActionExecutor.factResult(action, replacements, scope.asString(), value, 0);
            return ActionResult.alreadySatisfied("fact already satisfied", capabilities,
                    new VillagerActionResult(false, satisfied.lineId(), satisfied.text(), satisfied.replacements(), false));
        }
        return ActionResult.unavailable("action did not apply", capabilities, legacyResult);
    }

    private static boolean terminalQuestActionSatisfied(VillagerActionDefinition action,
            VillagerQuestSavedData.QuestProgress progress) {
        if (action.kind() != VillagerActionDefinition.Kind.QUEST) return false;
        return switch (action.questAction()) {
            case TURN_IN -> progress.state() == VillagerQuestSavedData.QuestState.COMPLETED
                    || progress.state() == VillagerQuestSavedData.QuestState.CONSUMED && progress.consumedReason().equals("completion");
            case ABANDON -> progress.state() == VillagerQuestSavedData.QuestState.ABANDONED
                    || progress.state() == VillagerQuestSavedData.QuestState.CONSUMED && progress.consumedReason().equals("abandonment");
            case FAIL -> progress.state() == VillagerQuestSavedData.QuestState.FAILED;
            case BLOCK -> progress.state() == VillagerQuestSavedData.QuestState.CONSUMED;
            default -> false;
        };
    }

    private static boolean factSatisfied(DialogueContext context, VillagerActionDefinition action,
            Map<String, String> inheritedReplacements) {
        if (context == null) return false;
        var scope = action.factScope().scope(context, action.questId());
        if (scope.isBlank()) return false;
        VillagerQuestFacts facts = VillagerQuestFacts.get(context.level());
        return switch (action.kind()) {
            case SET_TAG -> action.factTag() != null && facts.hasTag(scope, action.factTag());
            case CLEAR_TAG -> action.factTag() != null && !facts.hasTag(scope, action.factTag());
            case SET_VARIABLE -> {
                Map<String, String> replacements = new LinkedHashMap<>(DialoguePlaceholders.base(context));
                if (inheritedReplacements != null) replacements.putAll(inheritedReplacements);
                String expected = VillagerDialogueResources.resolveTemplate(action.factValue(), replacements);
                boolean stageMatches = true;
                if (action.factScope() == QuestFactScope.QUEST && action.factKey().equals("stage")) {
                    var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), action.questId());
                    stageMatches = progress != null && expected.equals(progress.currentStage());
                }
                yield stageMatches && !action.factKey().isBlank()
                        && facts.variable(scope, action.factKey()).filter(expected::equals).isPresent();
            }
            default -> false;
        };
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
