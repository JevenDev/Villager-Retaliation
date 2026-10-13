package com.jvn.villagerretaliation.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
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
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

public final class VillagerActionRegistry {
    private static final List<ActionTypeRegistration> REGISTRATIONS = List.of(
            register("companion_unlock", VillagerActionDefinition.Kind.COMPANION_UNLOCK, aliases(), capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE)),
            register("companion_bond", VillagerActionDefinition.Kind.COMPANION_BOND, aliases(), capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE)),
            register("follower", VillagerActionDefinition.Kind.FOLLOWER, aliases(), capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.WORLD_KNOWN)),
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
                    "campaign_payment",
                    VillagerActionDefinition.Kind.CAMPAIGN_PAYMENT,
                    aliases(),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE, ActionCapability.WORLD_KNOWN)),
            register(
                    "campaign_reward",
                    VillagerActionDefinition.Kind.CAMPAIGN_REWARD,
                    aliases(),
                    capabilities(ActionCapability.PLAYER_LIVE, ActionCapability.PROVIDER_LIVE, ActionCapability.WORLD_KNOWN)),
            register(
                    "campaign_standing",
                    VillagerActionDefinition.Kind.CAMPAIGN_STANDING,
                    aliases(),
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
    private static final Map<String, ActionTypeRegistration> BY_ALIAS = descriptorsByAlias();
    private static final Map<VillagerActionDefinition.Kind, ActionTypeRegistration> BY_KIND = descriptorsByKind();

    private VillagerActionRegistry() {
    }

    public static List<ActionTypeDescriptor> descriptors() {
        return REGISTRATIONS.stream().map(ActionTypeRegistration::descriptor).toList();
    }

    public static VillagerActionDefinition.Kind kindBySerializedName(String value) {
        ActionTypeRegistration registration = BY_ALIAS.get(normalizeType(value));
        return registration == null ? VillagerActionDefinition.Kind.NONE : registration.descriptor().kind();
    }

    public static String canonicalTypeId(String type) {
        String normalized = normalizeType(type);
        ActionTypeRegistration registration = BY_ALIAS.get(normalized);
        return registration == null ? normalized : registration.descriptor().id();
    }

    public static String canonicalTypeId(VillagerActionDefinition action) {
        if (action == null) {
            return "none";
        }
        ActionTypeRegistration registration = BY_KIND.get(action.kind());
        return registration == null ? action.kind().serializedName() : registration.descriptor().id();
    }

    public static Set<ActionCapability> capabilities(VillagerActionDefinition action) {
        if (action == null) {
            return Set.of();
        }
        ActionTypeRegistration registration = BY_KIND.get(action.kind());
        return registration == null ? Set.of() : registration.descriptor().capabilities();
    }

    public static ActionResult execute(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements) {
        return run(context, action, inheritedReplacements, false, null);
    }

    public static ActionResult execute(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements,
            com.jvn.villagerretaliation.campaign.CampaignOutcomeService.Actor outcomeActor) {
        return run(context, action, inheritedReplacements, false, outcomeActor);
    }

    public static ActionResult dryRun(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements) {
        return run(context, action, inheritedReplacements, true, null);
    }

    private static ActionResult run(
            DialogueContext context,
            VillagerActionDefinition action,
            Map<String, String> inheritedReplacements,
            boolean dryRun,
            com.jvn.villagerretaliation.campaign.CampaignOutcomeService.Actor outcomeActor) {
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
        if (context != null && context.villager() == null && switch (action.kind()) {
            case FORCED_DIALOGUE, REPUTATION, GOSSIP, PROFILE_ATTRIBUTE, MEMORY, DRAW_WEAPON -> true;
            default -> action.factScope() == QuestFactScope.VILLAGER || action.factScope() == QuestFactScope.VILLAGE;
        }) return ActionResult.unavailable("This action requires a quest provider.", capabilities, VillagerActionResult.EMPTY);
        if (outcomeActor != null && action.kind() != VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME) {
            return ActionResult.failed("A saved decision actor cannot receive another player's personal effects.", capabilities);
        }
        ActionTypeRegistration registration = BY_KIND.get(action.kind());
        return registration.handler().execute(context, action, inheritedReplacements, outcomeActor);
    }

    private static ActionResult executeLegacy(
            DialogueContext context, VillagerActionDefinition action, Map<String, String> inheritedReplacements,
            com.jvn.villagerretaliation.campaign.CampaignOutcomeService.Actor outcomeActor) {
        Set<ActionCapability> capabilities = capabilities(action);
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

    static Optional<VillagerActionDefinition> read(ResourceLocation location, String context, JsonObject entry,
            ResourceLocation defaultQuestId, VillagerActionDefinition.Kind kind) {
        ActionTypeRegistration registration = BY_KIND.get(kind);
        if (registration == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "action must define a supported type.");
            return Optional.empty();
        }
        return registration.reader().read(location, context, entry, defaultQuestId).filter(action -> {
            if (registration.requiredFields().test(action)) return true;
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context,
                    "action is missing required fields for type \"" + registration.descriptor().id() + "\".");
            return false;
        });
    }

    public static RecoveryMode recoveryMode(VillagerActionDefinition action) {
        ActionTypeRegistration registration = action == null ? null : BY_KIND.get(action.kind());
        return registration == null ? RecoveryMode.UNSAFE_BLOCK : registration.recovery();
    }

    public static boolean reconcilesPreparedOperation(VillagerActionDefinition action) {
        ActionTypeRegistration registration = action == null ? null : BY_KIND.get(action.kind());
        return registration != null && registration.reconcilesPreparedOperation();
    }

    public static JsonArray structuralRules() {
        JsonArray rules = new JsonArray();
        for (ActionTypeRegistration registration : REGISTRATIONS) {
            JsonObject fields = registration.schema().get();
            if (fields.entrySet().isEmpty()) continue;
            JsonArray names = new JsonArray();
            names.add(registration.descriptor().id());
            registration.descriptor().aliases().stream().sorted().forEach(names::add);
            JsonObject type = new JsonObject();
            type.add("enum", names);
            JsonObject properties = new JsonObject();
            properties.add("type", type);
            JsonObject when = new JsonObject();
            when.add("properties", properties);
            JsonArray required = new JsonArray();
            required.add("type");
            when.add("required", required);
            JsonObject rule = new JsonObject();
            rule.add("if", when);
            rule.add("then", fields);
            rules.add(rule);
        }
        return rules;
    }

    private static ActionTypeRegistration register(String id, VillagerActionDefinition.Kind kind,
            Set<String> aliases, Set<ActionCapability> capabilities) {
        ActionTypeDescriptor descriptor = new ActionTypeDescriptor(id, kind, aliases, capabilities);
        return switch (kind) {
            case COMPANION_UNLOCK -> registration(descriptor, action -> action.amount() >= -1000 && action.amount() <= 1000,
                    (context, action, replacements, actor) -> com.jvn.villagerretaliation.party.CompanionRelationshipService.execute(context, action),
                    com.jvn.villagerretaliation.party.CompanionRelationshipService::unlockSchema, RecoveryMode.NATURALLY_IDEMPOTENT);
            case COMPANION_BOND -> registration(descriptor, action -> !action.factKey().isBlank() && action.amount() >= 1 && action.amount() <= 5,
                    (context, action, replacements, actor) -> com.jvn.villagerretaliation.party.CompanionRelationshipService.execute(context, action),
                    com.jvn.villagerretaliation.party.CompanionRelationshipService::bondSchema, RecoveryMode.NATURALLY_IDEMPOTENT);
            case FOLLOWER -> registration(descriptor, action -> true,
                    (context, action, replacements, actor) -> com.jvn.villagerretaliation.quest.follower.QuestFollowerService.execute(context, action),
                    com.jvn.villagerretaliation.quest.follower.QuestFollowerDirective::schema, RecoveryMode.NATURALLY_IDEMPOTENT);
            case CAMPAIGN_PAYMENT -> registration(descriptor, action -> true,
                    (context, action, replacements, actor) -> com.jvn.villagerretaliation.campaign.CampaignPaymentService.execute(context, action),
                    com.jvn.villagerretaliation.campaign.CampaignPaymentAction::schema, RecoveryMode.RECEIPT_REQUIRED, true);
            case CAMPAIGN_REWARD -> registration(descriptor, action -> true,
                    (context, action, replacements, actor) -> com.jvn.villagerretaliation.campaign.CampaignRewardService.execute(context, action),
                    com.jvn.villagerretaliation.campaign.CampaignRewardAction::schema, RecoveryMode.RECEIPT_REQUIRED, true);
            case CAMPAIGN_STANDING -> registration(descriptor, action -> true,
                    (context, action, replacements, actor) -> com.jvn.villagerretaliation.campaign.CampaignStandingService.execute(context, action),
                    com.jvn.villagerretaliation.campaign.CampaignStandingAction::schema, RecoveryMode.RECEIPT_REQUIRED, true);
            case CAMPAIGN_FACT -> registration(descriptor, action -> true,
                    (context, action, replacements, actor) -> com.jvn.villagerretaliation.campaign.CampaignFactService.execute(context, action),
                    com.jvn.villagerretaliation.campaign.CampaignFactAction::schema, RecoveryMode.RECEIPT_REQUIRED, true);
            case CAMPAIGN_OUTCOME -> registration(descriptor, action -> true,
                    (context, action, replacements, actor) -> com.jvn.villagerretaliation.campaign.CampaignOutcomeService.execute(context, action, actor),
                    com.jvn.villagerretaliation.campaign.CampaignOutcomeAction::schema, RecoveryMode.RECEIPT_REQUIRED, true);
            case START_SCENE -> registration(descriptor, action -> action.sceneId() != null && !action.sceneOperationId().isBlank(),
                    VillagerActionRegistry::executeScene, JsonObject::new, RecoveryMode.RECEIPT_REQUIRED, true);
            case FORCED_DIALOGUE -> registration(descriptor, action -> !action.forcedDialogue().isBlank(),
                    (context, action, replacements, actor) -> {
                        ActionResult result = VillagerActionExecutor.executeForcedDialogue(context, action, replacements);
                        return new ActionResult(result.status(), result.message(), capabilities(action), result.legacyResult(),
                                result.suspensionId(), result.suspensionKind(), result.retryOnResume());
                    }, JsonObject::new, RecoveryMode.RECEIPT_REQUIRED);
            case QUEST -> registration(descriptor, action -> action.questId() != null && action.questAction() != VillagerActionDefinition.QuestAction.NONE);
            case QUEST_TRANSITION -> registration(descriptor, action -> !action.questTransition().isEmpty());
            case MEMORY -> registration(descriptor, action -> action.memoryTag() != null);
            case LOOT -> registration(descriptor, action -> action.lootTable() != null);
            case NOTIFICATION -> registration(descriptor, action -> !action.notificationTrigger().isBlank() || !action.text().isBlank());
            case SET_TAG, CLEAR_TAG -> registration(descriptor, action -> action.factTag() != null,
                    VillagerActionRegistry::executeLegacy, JsonObject::new, RecoveryMode.NATURALLY_IDEMPOTENT);
            case SET_VARIABLE -> registration(descriptor, action -> !action.factKey().isBlank() && !action.factValue().isBlank(),
                    VillagerActionRegistry::executeLegacy, JsonObject::new, RecoveryMode.NATURALLY_IDEMPOTENT);
            case COUNTER -> registration(descriptor, action -> !action.factKey().isBlank());
            case PROFILE_ATTRIBUTE -> registration(descriptor, action -> VillagerSocialAttribute.bySerializedName(action.factKey()) != null);
            case TRACKER -> registration(descriptor, action -> true,
                    VillagerActionRegistry::executeLegacy, JsonObject::new, RecoveryMode.NATURALLY_IDEMPOTENT);
            case EXPERIENCE, REPUTATION, GOSSIP, DRAW_WEAPON -> registration(descriptor, action -> true);
            case NONE -> throw new IllegalArgumentException("empty actions cannot be registered");
        };
    }

    private static ActionTypeRegistration registration(ActionTypeDescriptor descriptor,
            Predicate<VillagerActionDefinition> requiredFields) {
        return registration(descriptor, requiredFields, VillagerActionRegistry::executeLegacy,
                JsonObject::new, RecoveryMode.RECEIPT_REQUIRED);
    }

    private static ActionTypeRegistration registration(ActionTypeDescriptor descriptor,
            Predicate<VillagerActionDefinition> requiredFields, ActionHandler handler,
            Supplier<JsonObject> schema, RecoveryMode recovery) {
        return registration(descriptor, requiredFields, handler, schema, recovery, false);
    }

    private static ActionTypeRegistration registration(ActionTypeDescriptor descriptor,
            Predicate<VillagerActionDefinition> requiredFields, ActionHandler handler,
            Supplier<JsonObject> schema, RecoveryMode recovery, boolean reconcilesPreparedOperation) {
        return new ActionTypeRegistration(descriptor,
                (location, context, entry, defaultQuestId) -> VillagerActionDefinition.readRegistered(
                        location, context, entry, defaultQuestId, descriptor.kind()),
                requiredFields, handler, schema, recovery, reconcilesPreparedOperation);
    }

    private static ActionResult executeScene(DialogueContext context, VillagerActionDefinition action,
            Map<String, String> replacements, com.jvn.villagerretaliation.campaign.CampaignOutcomeService.Actor actor) {
        Set<ActionCapability> capabilities = capabilities(action);
        SceneLaunchService.LaunchResult launch = SceneLaunchService.launch(context, action);
        if (!launch.accepted()) return ActionResult.unavailable(launch.diagnostic(), capabilities, VillagerActionResult.EMPTY);
        if (action.waitForScene()) return ActionResult.suspended(launch.instanceId(), capabilities);
        return launch.created() ? ActionResult.success(VillagerActionResult.success(), capabilities)
                : ActionResult.alreadySatisfied(launch.diagnostic(), capabilities, VillagerActionResult.EMPTY);
    }

    private static Map<String, ActionTypeRegistration> descriptorsByAlias() {
        Map<String, ActionTypeRegistration> registrations = new LinkedHashMap<>();
        for (ActionTypeRegistration registration : REGISTRATIONS) {
            ActionTypeDescriptor descriptor = registration.descriptor();
            if (registrations.putIfAbsent(descriptor.id(), registration) != null)
                throw new IllegalStateException("duplicate action type " + descriptor.id());
            for (String alias : descriptor.aliases()) {
                if (registrations.putIfAbsent(normalizeType(alias), registration) != null)
                    throw new IllegalStateException("colliding action alias " + alias);
            }
        }
        return Map.copyOf(registrations);
    }

    private static Map<VillagerActionDefinition.Kind, ActionTypeRegistration> descriptorsByKind() {
        Map<VillagerActionDefinition.Kind, ActionTypeRegistration> registrations = new HashMap<>();
        for (ActionTypeRegistration registration : REGISTRATIONS) {
            if (registrations.putIfAbsent(registration.descriptor().kind(), registration) != null)
                throw new IllegalStateException("duplicate action kind " + registration.descriptor().kind());
        }
        return Map.copyOf(registrations);
    }

    private record ActionTypeRegistration(ActionTypeDescriptor descriptor, ActionReader reader,
            Predicate<VillagerActionDefinition> requiredFields, ActionHandler handler,
            Supplier<JsonObject> schema, RecoveryMode recovery, boolean reconcilesPreparedOperation) { }

    @FunctionalInterface
    private interface ActionReader {
        Optional<VillagerActionDefinition> read(ResourceLocation location, String context, JsonObject entry,
                ResourceLocation defaultQuestId);
    }

    @FunctionalInterface
    private interface ActionHandler {
        ActionResult execute(DialogueContext context, VillagerActionDefinition action, Map<String, String> replacements,
                com.jvn.villagerretaliation.campaign.CampaignOutcomeService.Actor outcomeActor);
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
        public JsonObject schema() {
            ActionTypeRegistration registration = BY_KIND.get(kind);
            return registration == null ? new JsonObject() : registration.schema().get();
        }

        public boolean reconcilesPreparedOperation() {
            ActionTypeRegistration registration = BY_KIND.get(kind);
            return registration != null && registration.reconcilesPreparedOperation();
        }

        public RecoveryMode recoveryMode() {
            ActionTypeRegistration registration = BY_KIND.get(kind);
            return registration == null ? RecoveryMode.UNSAFE_BLOCK : registration.recovery();
        }

        public ActionTypeDescriptor {
            id = normalizeType(id);
            kind = kind == null ? VillagerActionDefinition.Kind.NONE : kind;
            aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
    }
}
