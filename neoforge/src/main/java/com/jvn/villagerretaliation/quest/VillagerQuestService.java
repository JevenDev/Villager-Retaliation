package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets;
import com.jvn.villagerretaliation.quest.tracking.QuestTrackerLimits;
import com.jvn.villagerretaliation.quest.tracking.QuestTrackerPresenter;
import com.jvn.villagerretaliation.quest.tracking.QuestPresentationService;
import com.jvn.villagerretaliation.quest.query.QuestProgressSnapshot;
import com.jvn.villagerretaliation.quest.inventory.QuestInventoryService;
import com.jvn.villagerretaliation.quest.inventory.QuestInventoryService.ItemHandInResult;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveResult;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRequirement;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatchService;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvent;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDebugState;
import com.jvn.villagerretaliation.quest.debug.QuestDebugTraceService;
import com.jvn.villagerretaliation.quest.debug.QuestDebugService;
import com.jvn.villagerretaliation.quest.debug.QuestDebugFormatter;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import com.jvn.villagerretaliation.quest.pool.QuestPoolResources;
import com.jvn.villagerretaliation.quest.runtime.QuestStageBranchOptionIds;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.runtime.QuestStageTransitionCoordinator;
import com.jvn.villagerretaliation.quest.runtime.QuestStageActionService;
import com.jvn.villagerretaliation.quest.runtime.QuestRewardService;
import com.jvn.villagerretaliation.quest.persistence.QuestDefinitionMigration;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardResolver;
import com.jvn.villagerretaliation.scene.SceneLifecycleIntegration;
import com.jvn.villagerretaliation.scene.SceneJournalPresenter;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.campaign.CampaignQuestAccess;
import com.jvn.villagerretaliation.campaign.CampaignQuestEntries;
import com.jvn.villagerretaliation.campaign.CampaignQuestReconciliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueQuestAction;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCatalog;
import com.jvn.villagerretaliation.dialogue.resources.DangerousStructureStoryResources;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.dialogue.VillagerStoryHintService;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.mood.VillagerMoodSavedData;
import com.jvn.villagerretaliation.mood.VillagerMoodState;
import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.network.QuestTrackerRequestPayload;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTrigger;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTransition;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import com.jvn.villagerretaliation.social.VillagerFamilyTreeSnapshot;
import com.jvn.villagerretaliation.social.VillagerRelationshipSnapshot;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import com.jvn.villagerretaliation.util.VillagerWorldTargetCache;
import com.jvn.villagerretaliation.util.item.ItemStackPredicate;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerQuestService {
    private static final QuestStageTransitionCoordinator STAGES = new QuestStageTransitionCoordinator(new StageAccess());
    private static final QuestDebugService DEBUG = new QuestDebugService(new DebugAccess());
    private static final int QUEST_PROGRESS_SCAN_INTERVAL_TICKS = 20;
    private static final int QUEST_ITEM_TRACKER_SYNC_INTERVAL_TICKS = 5;
    private static final int NEARBY_AVAILABLE_QUEST_RADIUS = 32;
    private static final int MAX_NEARBY_AVAILABLE_QUEST_VILLAGERS = 16;
    private static final int NEARBY_AVAILABLE_QUEST_CACHE_TICKS = QUEST_PROGRESS_SCAN_INTERVAL_TICKS * 2;
    private static final long DIRECT_HIT_MEMORY_TICKS = 20L * 60L * 20L;
    private static final long BROKEN_BED_MEMORY_TICKS = 20L * 60L * 20L;
    private static final int QUEST_TRACKER_HEARTBEAT_TICKS = 20 * 30;
    private static final long QUEST_STORY_HINT_TICKS = 20L * 60L * 60L * 6L;
    private static final int REPEATABLE_QUEST_PROVIDER_KNOWLEDGE_REWARD = 1;
    private static final int ONE_SHOT_QUEST_PROVIDER_KNOWLEDGE_REWARD = 2;
    private static final ResourceLocation QUEST_STARTED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_started");
    private static final ResourceLocation QUEST_COMPLETED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_completed");
    private static final ResourceLocation QUEST_ABANDONED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_abandoned");
    private static final ResourceLocation QUEST_FAILED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_failed");
    private static final ResourceLocation QUEST_EXPIRED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_expired");
    private static final ResourceLocation QUEST_OBJECTIVE_COMPLETED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_objective_completed");
    private static final ResourceLocation QUEST_BRANCH_LOCKED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_branch_locked");
    private static final ResourceLocation QUEST_BRANCH_SELECTED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_branch_selected");
    private static final ResourceLocation QUEST_BRANCH_BLOCKED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_branch_blocked");
    private static final String BRANCH_LOCK_CONSUMED_REASON = "branch_lock";
    private static final String QUEST_OFFER_HINT_TAG = "quest_offer_hint";
    private static final int STAGE_BRANCH_OPTION_ORDER = 900;
    private static final int QUEST_OFFER_HINT_ORDER = -4;
    private static final ThreadLocal<Boolean> DISPATCHING_STAGE_TRIGGERS =
            ThreadLocal.withInitial(() -> false);
    private static final Set<UUID> CLIENT_EFFECTS_SUPPRESSED_FOR_TEST_PLAYERS = new HashSet<>();
    private static final Map<UUID, TrackerSyncState> LAST_TRACKER_SYNCS = new HashMap<>();
    private static final Map<UUID, NearbyAvailableQuestCache> NEARBY_AVAILABLE_QUEST_CACHES = new HashMap<>();
    private static final PartyQuestEnrollmentService PARTY_QUEST_ENROLLMENT = new PartyQuestEnrollmentService(
            (context, definition, progress) -> canStart(context, definition, progress, false),
            VillagerQuestService::ensureRandomItemSelections,
            VillagerQuestService::applyPartyMemberStart,
            player -> sendTrackerSync(player, true));

    private VillagerQuestService() {
    }

    public static void setClientEffectsSuppressedForTests(ServerPlayer player, boolean suppressed) {
        if (player == null) {
            return;
        }
        if (suppressed) {
            CLIENT_EFFECTS_SUPPRESSED_FOR_TEST_PLAYERS.add(player.getUUID());
        } else {
            CLIENT_EFFECTS_SUPPRESSED_FOR_TEST_PLAYERS.remove(player.getUUID());
        }
    }

    private enum ConditionMatch {
        MET,
        UNMET,
        UNKNOWN
    }

    private record QuestOfferHint(QuestOfferHintKind kind, int villagerLevel, VillagerSkill skill) {
        private static QuestOfferHint trust() {
            return new QuestOfferHint(QuestOfferHintKind.TRUST, 0, null);
        }

        private static QuestOfferHint level(int villagerLevel) {
            return new QuestOfferHint(QuestOfferHintKind.LEVEL, villagerLevel, null);
        }

        private static QuestOfferHint skill(VillagerSkill skill) {
            return new QuestOfferHint(QuestOfferHintKind.SKILL, 0, skill);
        }

        private static QuestOfferHint timing() {
            return new QuestOfferHint(QuestOfferHintKind.TIMING, 0, null);
        }
    }

    private enum QuestOfferHintKind {
        TRUST,
        LEVEL,
        SKILL,
        TIMING
    }

    public static void clearRuntimeState() {
        DISPATCHING_STAGE_TRIGGERS.remove();
        CLIENT_EFFECTS_SUPPRESSED_FOR_TEST_PLAYERS.clear();
        LAST_TRACKER_SYNCS.clear();
        NEARBY_AVAILABLE_QUEST_CACHES.clear();
        QuestInventoryService.clearRuntimeState();
        QuestDebugTraceService.clearRuntimeState();
    }

    public static void clearRuntimeState(ServerPlayer player) {
        if (player != null) {
            UUID playerId = player.getUUID();
            CLIENT_EFFECTS_SUPPRESSED_FOR_TEST_PLAYERS.remove(playerId);
            LAST_TRACKER_SYNCS.remove(playerId);
            NEARBY_AVAILABLE_QUEST_CACHES.remove(playerId);
            QuestInventoryService.clearRuntimeState(player);
            QuestDebugTraceService.clearRuntimeState(player);
        }
    }

    public static boolean matchesState(DialogueContext context, ResourceLocation questId, Set<String> states) {
        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        if (definition == null) {
            return false;
        }
        if (states == null || states.isEmpty()) {
            return true;
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), questId);
        for (String state : states) {
            if (matchesState(context, definition, progress, state)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasSelectedChoice(
            DialogueContext context,
            ResourceLocation questId,
            String scenePath,
            String responseId,
            String priorStage,
            String nextStage) {
        return com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess.hasSelectedChoice(context,
                new DialogueCondition.SelectedChoice(questId, scenePath, responseId, priorStage, nextStage));
    }

    public static boolean hasStageHistory(DialogueContext context, ResourceLocation questId, String stage,
            String priorStage, String nextStage) {
        return com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess.hasStageHistory(context,
                new DialogueCondition.StageHistory(questId, stage, priorStage, nextStage));
    }

    public static List<DialogueOptionDefinition> stageBranchOptions(DialogueContext context) {
        if (context == null) {
            return List.of();
        }
        List<DialogueOptionDefinition> options = new ArrayList<>();
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        int order = STAGE_BRANCH_OPTION_ORDER;
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : data.activeProgress(context.player().getUUID())) {
            QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), entry.getKey()).orElse(null);
            VillagerQuestSavedData.QuestProgress progress = entry.getValue();
            if (definition == null
                    || definition.stages().isEmpty()
                    || !matchesVillagerLock(context, definition, progress)
                    || !activeConditionsMet(context, definition)) {
                continue;
            }
            QuestDefinition.Stage stage = definition.stages().get(progress.currentStage());
            if (stage == null || stage.branches().isEmpty()) {
                continue;
            }
            for (QuestDefinition.StageBranch branch : stage.branches()) {
                if (!shouldShowStageBranchOption(context, branch)) {
                    continue;
                }
                String label = stageBranchLabel(context, definition, progress, branch);
                if (label.isBlank()) {
                    continue;
                }
                options.add(DialogueOptionDefinition.simple(
                        QuestStageBranchOptionIds.create(definition.id(), branch.id()),
                        label,
                        DialogueRequestType.QUESTION,
                        order++));
            }
        }
        return List.copyOf(options);
    }

    public static List<DialogueOptionDefinition> embeddedDialogueOptions(
            DialogueContext context,
            DialogueDisposition disposition) {
        if (context == null) {
            return List.of();
        }
        QuestDialogueCatalog catalog = VillagerQuestResources.questDialogueCatalog(context.level().getServer());
        if (catalog.bindings().isEmpty()) {
            return List.of();
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        List<DialogueOptionDefinition> options = new ArrayList<>();
        for (QuestDialogueCatalog.Binding binding : catalog.bindings().values()) {
            QuestDefinition definition = VillagerQuestResources
                    .quest(context.level().getServer(), binding.questId())
                    .orElse(null);
            if (definition == null) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
            if (!matchesEmbeddedDialogueBinding(context, definition, progress, binding)) {
                continue;
            }

            DialogueTreeDefinition tree = DialogueTreeResources
                    .tree(context.level().getServer(), context.locale(), binding.treeId())
                    .orElse(null);
            if (tree == null || !tree.matches(context)) {
                continue;
            }
            DialogueTreeDefinition.Entry entry = tree.entry(binding.entryId()).orElse(null);
            if (entry != null && entry.matches(context, disposition)) {
                options.add(entry.toOption(context, tree.id(), DialogueEntryMetadata.EMPTY));
            }
        }
        appendQuestOfferHintOptions(context, options);
        options.sort(Comparator.comparingInt(DialogueOptionDefinition::order).thenComparing(DialogueOptionDefinition::id));
        return List.copyOf(options);
    }

    private static void appendQuestOfferHintOptions(DialogueContext context, List<DialogueOptionDefinition> options) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        int order = QUEST_OFFER_HINT_ORDER;
        for (QuestDefinition definition : VillagerQuestResources.quests(context.level().getServer())) {
            VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
            Optional<QuestOfferHint> hint = questOfferHintCandidate(context, definition, progress);
            if (hint.isEmpty()) {
                continue;
            }
            options.add(DialogueOptionDefinition.quest(
                    questOfferHintOptionId(definition),
                    questOfferHintOptionLabel(definition),
                    DialogueRequestType.QUESTION,
                    new DialogueQuestAction(definition.id(), DialogueQuestAction.Action.HINT),
                    questOfferHintMetadata(definition),
                    order++));
        }
    }

    private static Optional<String> questOfferHint(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return questOfferHintCandidate(context, definition, progress)
                .map(hint -> selectQuestOfferHint(context, hint));
    }

    private static Optional<QuestOfferHint> questOfferHintCandidate(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (context == null
                || definition == null
                || !definition.showLockedAdventureHint()
                || context.villager() == null || context.villager().isBaby()
                || !definition.prerequisites().isEmpty()
                || canStart(context, definition, progress)
                || !offerProfessionMatches(context, definition.offer())) {
            return Optional.empty();
        }
        if (canStart(context, definition, progress, true)) {
            return Optional.of(questOfferRequirementHint(context, definition));
        }
        return Optional.empty();
    }

    private static boolean offerProfessionMatches(DialogueContext context, QuestDefinition.Offer offer) {
        return offer == null
                || offer.professions().isEmpty()
                || offer.professions().contains(context.profession());
    }

    private static QuestOfferHint questOfferRequirementHint(DialogueContext context, QuestDefinition definition) {
        QuestDefinition.Offer offer = definition.offer();
        if (offer == null) {
            return QuestOfferHint.timing();
        }
        if (!offer.conditions().isEmpty() && !DialogueCondition.matchesAll(context, offer.conditions())) {
            return QuestOfferHint.trust();
        }
        if (context.villager().getVillagerData().getLevel() < offer.minVillagerLevel()) {
            return QuestOfferHint.level(offer.minVillagerLevel());
        }
        Optional<VillagerSkill> unmetSkill = strongestUnmetOfferSkill(context, offer);
        if (unmetSkill.isPresent()) {
            return QuestOfferHint.skill(unmetSkill.get());
        }
        return QuestOfferHint.trust();
    }

    private static Optional<VillagerSkill> strongestUnmetOfferSkill(DialogueContext context, QuestDefinition.Offer offer) {
        VillagerSkill strongest = null;
        int strongestShortfall = 0;
        for (Map.Entry<VillagerSkill, Integer> entry : offer.minSkills().entrySet()) {
            int required = VillagerSkillSet.clamp(entry.getValue());
            int current = context.skillValue(entry.getKey());
            int shortfall = required - current;
            if (shortfall > strongestShortfall) {
                strongest = entry.getKey();
                strongestShortfall = shortfall;
            }
        }
        return Optional.ofNullable(strongest);
    }

    private static String selectQuestOfferHint(DialogueContext context, QuestOfferHint hint) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("level", villagerLevelName(hint.villagerLevel()));
        replacements.put("skill", skillName(hint.skill()));
        return VillagerDialogueResources.message(context, questOfferHintMessageKey(hint.kind()), replacements)
                .orElse("");
    }

    private static String questOfferHintMessageKey(QuestOfferHintKind kind) {
        String suffix = switch (kind) {
            case TRUST -> "trust";
            case LEVEL -> "level";
            case SKILL -> "skill";
            case TIMING -> "timing";
        };
        return "quest.offer_hint." + suffix;
    }

    private static String questOfferHintOptionId(QuestDefinition definition) {
        return "quest_offer_hint_" + definition.id().getNamespace() + "_" + definition.id().getPath().replace('/', '_');
    }

    private static String questOfferHintOptionLabel(QuestDefinition definition) {
        return definition.title();
    }

    private static DialogueEntryMetadata questOfferHintMetadata(QuestDefinition definition) {
        return new DialogueEntryMetadata(
                "quest offer hint",
                Set.of("quest_v2", QUEST_OFFER_HINT_TAG),
                "",
                definition.id().toString(),
                "offer_hint",
                "");
    }

    private static String skillName(VillagerSkill skill) {
        return VillagerInteractionTextUtil.titleCaseIdentifier(skill == null ? "skill" : skill.serializedName());
    }

    private static String villagerLevelName(int level) {
        return switch (Math.max(1, Math.min(5, level))) {
            case 2 -> "Apprentice";
            case 3 -> "Journeyman";
            case 4 -> "Expert";
            case 5 -> "Master";
            default -> "Novice";
        };
    }

    private static boolean matchesEmbeddedDialogueBinding(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDialogueCatalog.Binding binding) {
        String slot = normalizeEmbeddedDialogueSlot(binding.slot());
        if (slot.isBlank() || slot.startsWith("scene:")) {
            return false;
        }
        if (activeQuestLockedToDifferentProvider(context, definition, progress)) {
            return false;
        }
        boolean activeStage = matchesEmbeddedDialogueStage(definition, progress, binding);
        return switch (slot) {
            case "offer", "start", "begin" ->
                    isInitialEmbeddedDialogueStage(definition, binding) && canStart(context, definition, progress);
            case "reminder", "remind", "details", "active", "in_progress", "incomplete" ->
                    activeStage && activeConditionsMet(context, definition) && !isReadyToTurnIn(context, definition, progress);
            case "ready", "turn_in", "turnin", "turn-in", "complete", "completeable", "completable" ->
                    activeStage && isReadyToTurnIn(context, definition, progress);
            case "responses", "stage_responses", "stage-responses" ->
                    activeStage && activeConditionsMet(context, definition);
            case "already_completed", "already-completed", "completed" ->
                    matchesState(context, definition, progress, "completed");
            case "unavailable" ->
                    isInitialEmbeddedDialogueStage(definition, binding)
                            && !matchesState(context, definition, progress, "completed")
                            && matchesState(context, definition, progress, "unavailable");
            case "abandoned", "dropped" ->
                    matchesState(context, definition, progress, "abandoned");
            case "expired", "timed_out", "timed-out" ->
                    matchesState(context, definition, progress, "expired");
            case "consumed", "removed" ->
                    matchesState(context, definition, progress, "consumed");
            case "branch_locked", "branch-locked" ->
                    matchesState(context, definition, progress, "branch_locked");
            default -> !binding.stageId().isBlank() && activeStage && activeConditionsMet(context, definition);
        };
    }

    private static boolean activeQuestLockedToDifferentProvider(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && !matchesVillagerLock(context, definition, progress);
    }

    private static boolean matchesEmbeddedDialogueStage(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDialogueCatalog.Binding binding) {
        return definition != null
                && progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && binding.stageId().equals(progress.currentStage());
    }

    private static boolean isInitialEmbeddedDialogueStage(
            QuestDefinition definition,
            QuestDialogueCatalog.Binding binding) {
        return binding.stageId().isBlank()
                || binding.stageId().equals(QuestLifecycleService.initialStage(definition));
    }

    private static String normalizeEmbeddedDialogueSlot(String slot) {
        String normalized = slot == null ? "" : slot.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("lifecycle:")
                ? normalized.substring("lifecycle:".length())
                : normalized;
    }

    public static Optional<VillagerDialogueService.DialogueResult> handleDialogueOption(
            DialogueContext context,
            DialogueOptionDefinition option) {
        Optional<VillagerDialogueService.DialogueResult> branchResult =
                handleStageBranchOption(context, option.id());
        if (branchResult.isPresent()) {
            return branchResult;
        }

        DialogueQuestAction questAction = option.questAction();
        if (questAction.isEmpty()) {
            return Optional.empty();
        }

        return performAction(context, questAction.questId(), questAction.action())
                .map(QuestActionOutcome::dialogueResult);
    }

    public static boolean isQuestDialogueOption(DialogueOptionDefinition option) {
        if (option == null) {
            return false;
        }
        if (QuestStageBranchOptionIds.parse(option.id()).isPresent()) {
            return true;
        }
        if (!option.questAction().isEmpty()) {
            return true;
        }
        return !option.metadata().quest().isBlank()
                || option.metadata().tags().contains("quest_v2");
    }

    private static Optional<VillagerDialogueService.DialogueResult> handleStageBranchOption(
            DialogueContext context,
            String optionId) {
        Optional<QuestStageBranchOptionIds.Key> key = QuestStageBranchOptionIds.parse(optionId);
        if (key.isEmpty()) {
            return Optional.empty();
        }

        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), key.get().questId()).orElse(null);
        if (definition == null) {
            return Optional.of(result(
                    "missing",
                    "quest_stage_branch_missing",
                    "That choice is no longer available.",
                    Map.of()).dialogueResult());
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress == null
                || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                || !matchesVillagerLock(context, definition, progress)) {
            return Optional.of(stageBranchUnavailableResult(context, definition, progress).dialogueResult());
        }

        resumePendingStageEntry(context, definition, progress);
        QuestDefinition.Stage stage = definition.stages().get(progress.currentStage());
        QuestDefinition.StageBranch branch = stage == null
                ? null
                : stage.branches().stream()
                        .filter(candidate -> candidate.id().equals(key.get().branchId()))
                        .findFirst()
                        .orElse(null);
        if (branch == null) {
            return Optional.of(stageBranchUnavailableResult(context, definition, progress).dialogueResult());
        }

        Map<String, String> replacements = stageBranchReplacements(context, definition, progress, branch);
        QuestDefinition.StageBranchBlocker blocker = matchingStageBranchBlocker(context, branch).orElse(null);
        if (blocker != null) {
            recordStageBranchFact(context, definition, progress, branch, QUEST_BRANCH_BLOCKED_FACT, "blocked");
            return Optional.of(result(
                    "blocked",
                    "quest_stage_branch_blocked_" + branch.id(),
                    stageBranchBlockerText(context, definition, progress, branch, blocker),
                    replacements).dialogueResult());
        }
        if (!stageBranchConditionsMet(context, branch)) {
            return Optional.of(result(
                    "unavailable",
                    "quest_stage_branch_unavailable_" + branch.id(),
                    resolveQuestBranchMessage(
                            context,
                            "quest.stage_branch.unavailable",
                            "That choice is not available right now.",
                            replacements),
                    replacements).dialogueResult());
        }

        if (!QuestStageActionService.ready(context, progress) || !progress.pendingStageTransition().isBlank()
                || !progress.pendingStageEntry().isBlank()) {
            return Optional.of(stageBranchUnavailableResult(context, definition, progress).dialogueResult());
        }
        recordStageBranchFact(context, definition, progress, branch, QUEST_BRANCH_SELECTED_FACT, "selected");
        ActionResult branchResult = runStageActions(context, definition, progress, "branch/" + branch.id(), branch.actions());
        boolean changed = branchResult.legacyResult().ran();
        if (!branchResult.success()) {
            if (branchResult.suspended()) progress.requestStageTransition(branch.next());
            data.setDirty();
            sendTrackerSync(context.player(), true);
            return Optional.of(stageBranchUnavailableResult(context, definition, progress).dialogueResult());
        }
        if (!branch.next().isBlank() && definition.stages().containsKey(branch.next())) {
            changed |= changeQuestStage(context, definition, progress, branch.next(), true, true);
        }
        changed |= advanceStageIfComplete(context, definition, progress);
        if (changed) {
            data.setDirty();
        }
        sendTrackerSync(context.player(), true);
        return Optional.of(result(
                "selected",
                "quest_stage_branch_selected_" + branch.id(),
                resolveQuestBranchMessage(
                        context,
                        "quest.stage_branch.selected",
                        "Choice recorded: {branch_label}",
                        stageBranchReplacements(context, definition, progress, branch)),
                stageBranchReplacements(context, definition, progress, branch)).dialogueResult());
    }

    private static QuestActionOutcome stageBranchUnavailableResult(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        Map<String, String> replacements = definition == null || progress == null
                ? Map.of()
                : replacements(context, definition, progress);
        return result(
                "unavailable",
                "quest_stage_branch_unavailable",
                resolveQuestBranchMessage(
                        context,
                        "quest.stage_branch.unavailable",
                        "That choice is not available right now.",
                        replacements),
                replacements);
    }

    private static boolean shouldShowStageBranchOption(DialogueContext context, QuestDefinition.StageBranch branch) {
        return stageBranchConditionsMet(context, branch)
                || matchingStageBranchBlocker(context, branch)
                        .map(blocker -> !blocker.reason().isBlank() || !blocker.reasonKey().isBlank())
                        .orElse(false);
    }

    private static boolean stageBranchConditionsMet(DialogueContext context, QuestDefinition.StageBranch branch) {
        return DialogueCondition.matchesAll(context, branch.conditions());
    }

    private static Optional<QuestDefinition.StageBranchBlocker> matchingStageBranchBlocker(
            DialogueContext context,
            QuestDefinition.StageBranch branch) {
        for (QuestDefinition.StageBranchBlocker blocker : branch.blockedBy()) {
            if (!blocker.conditions().isEmpty() && conditionsMatch(context, blocker.conditions())) {
                return Optional.of(blocker);
            }
        }
        return Optional.empty();
    }

    private static boolean conditionsMatch(DialogueContext context, List<DialogueCondition> conditions) {
        return DialogueCondition.matchesAll(context, conditions);
    }

    private static String stageBranchLabel(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.StageBranch branch) {
        Map<String, String> replacements = stageBranchReplacements(context, definition, progress, branch);
        if (!branch.labelKey().isBlank()) {
            return VillagerDialogueResources.message(context, branch.labelKey(), replacements)
                    .orElseGet(() -> stageBranchFallbackLabel(branch, replacements));
        }
        return stageBranchFallbackLabel(branch, replacements);
    }

    private static String stageBranchFallbackLabel(
            QuestDefinition.StageBranch branch,
            Map<String, String> replacements) {
        if (!branch.label().isBlank()) {
            return VillagerDialogueResources.resolveTemplate(branch.label(), replacements);
        }
        return branch.id().replace('_', ' ');
    }

    private static String stageBranchBlockerText(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.StageBranch branch,
            QuestDefinition.StageBranchBlocker blocker) {
        Map<String, String> replacements = stageBranchReplacements(context, definition, progress, branch);
        if (!blocker.reasonKey().isBlank()) {
            return VillagerDialogueResources.message(context, blocker.reasonKey(), replacements)
                    .orElseGet(() -> stageBranchFallbackBlockerText(context, blocker, replacements));
        }
        return stageBranchFallbackBlockerText(context, blocker, replacements);
    }

    private static String stageBranchFallbackBlockerText(
            DialogueContext context,
            QuestDefinition.StageBranchBlocker blocker,
            Map<String, String> replacements) {
        if (!blocker.reason().isBlank()) {
            return VillagerDialogueResources.resolveTemplate(blocker.reason(), replacements);
        }
        return resolveQuestBranchMessage(
                context,
                "quest.stage_branch.blocked",
                "That choice is blocked right now.",
                replacements);
    }

    private static Map<String, String> stageBranchReplacements(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.StageBranch branch) {
        Map<String, String> replacements = new LinkedHashMap<>(replacements(context, definition, progress));
        replacements.put("branch", branch.id());
        replacements.put("branch_id", branch.id());
        replacements.put("branch_label", stageBranchFallbackLabel(branch, replacements));
        replacements.put("branch_stage", progress == null ? "" : progress.currentStage());
        replacements.put("branch_next_stage", branch.next());
        return Map.copyOf(replacements);
    }

    private static String resolveQuestBranchMessage(
            DialogueContext context,
            String key,
            String fallback,
            Map<String, String> replacements) {
        return VillagerDialogueResources.message(context, key, replacements)
                .orElseGet(() -> VillagerDialogueResources.resolveTemplate(fallback, replacements));
    }

    private static void recordStageBranchFact(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.StageBranch branch,
            ResourceLocation tag,
            String status) {
        String scopeKey = playerQuestScopeKey(context.player(), definition);
        if (scopeKey.isBlank()) {
            return;
        }
        VillagerQuestFacts facts = VillagerQuestFacts.get(context.level());
        facts.setTag(scopeKey, tag);
        facts.setVariable(scopeKey, "branch", branch.id());
        facts.setVariable(scopeKey, "branch_status", status);
        facts.setVariable(scopeKey, "branch_stage", progress == null ? "" : progress.currentStage());
        facts.setVariable(scopeKey, "branch_next_stage", branch.next());
    }

    public static Optional<QuestActionOutcome> performAction(
            DialogueContext context,
            ResourceLocation questId,
            DialogueQuestAction.Action action) {
        if (action == DialogueQuestAction.Action.HINT) {
            return questOfferHintAction(context, questId);
        }
        return performAction(context, questId, fromDialogueAction(action));
    }

    private static Optional<QuestActionOutcome> questOfferHintAction(DialogueContext context, ResourceLocation questId) {
        if (context == null || questId == null) {
            return Optional.empty();
        }
        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        if (definition == null) {
            return Optional.of(result(
                    "missing",
                    "quest_offer_hint_missing_" + questId,
                    "I cannot find the notes for that work.",
                    Map.of()));
        }
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), definition.id());
        String hint = questOfferHint(context, definition, progress)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> QuestPresentationService.resolveQuestText(
                        context,
                        definition.dialogue().selectUnavailableText(context),
                        replacements(context, definition, progress)));
        return Optional.of(result(
                "offer_hint",
                lineId(definition, "offer_hint"),
                hint,
                replacements(context, definition, progress)));
    }

    public static Optional<QuestActionOutcome> performAction(
            DialogueContext context,
            ResourceLocation questId,
            VillagerActionDefinition.QuestAction action) {
        if (questId == null || action == null || action == VillagerActionDefinition.QuestAction.NONE) {
            return Optional.empty();
        }

        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        if (definition == null) {
            return Optional.of(result(
                    "missing",
                    "quest_missing_" + questId,
                    "I cannot find the notes for that quest.",
                    Map.of()));
        }

        String campaignIssue = CampaignQuestAccess.executionIssue(context, definition);
        if (!campaignIssue.isBlank()) return Optional.of(result("unavailable", lineId(definition, "unavailable"), campaignIssue, Map.of()));
        var savedProgress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), definition.id());
        if (savedProgress != null && savedProgress.completionPending() && action != VillagerActionDefinition.QuestAction.TURN_IN) {
            return Optional.of(result("reward_pending", lineId(definition, "unavailable"),
                    "Finish claiming this quest's rewards before changing it.", replacements(context, definition, savedProgress)));
        }
        resumePendingStageEntry(context, definition, savedProgress);
        return Optional.of(switch (action) {
            case START -> startQuest(context, definition);
            case REMIND -> remindQuest(context, definition);
            case TURN_IN -> turnInQuest(context, definition);
            case FAIL -> failQuest(context, definition, "explicit_fail");
            case ABANDON -> abandonQuest(context, definition);
            case BLOCK -> blockQuest(context, definition);
            case NONE -> result("none", "quest_no_action", "", Map.of());
        });
    }

    public static VillagerActionResult applyCompiledTransition(
            DialogueContext context,
            CompiledQuestTransition transition,
            Map<String, String> inheritedReplacements) {
        if (context == null || transition == null || transition.isEmpty()) {
            return VillagerActionResult.EMPTY;
        }
        QuestDefinition definition = VillagerQuestResources
                .quest(context.level().getServer(), transition.questId())
                .orElse(null);
        if (definition == null) {
            return transitionActionResult(
                    "missing",
                    "Quest transition target is missing.",
                    inheritedReplacements,
                    context,
                    null,
                    null,
                    transition,
                    false);
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        String campaignIssue = CampaignQuestAccess.executionIssue(context, definition);
        if (!campaignIssue.isBlank()) return transitionActionResult("unavailable", campaignIssue,
                inheritedReplacements, context, definition, progress, transition, false);
        if ((progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED)
                && transition.stageId().equals(initialStage(definition))
                && canStart(context, definition, progress)) {
            startQuest(context, definition);
            progress = data.get(context.player().getUUID(), definition.id());
        }
        if (progress == null || !matchesVillagerLock(context, definition, progress)) {
            return transitionActionResult(
                    "unavailable",
                    "That choice is not available right now.",
                    inheritedReplacements,
                    context,
                    definition,
                    progress,
                    transition,
                    false);
        }
        resumePendingStageEntry(context, definition, progress);
        if (progress.hasChoice(transition.scenePath(), transition.responseId(), transition.stageId())) {
            return transitionActionResult(
                    "duplicate",
                    "That choice has already been recorded.",
                    inheritedReplacements,
                    context,
                    definition,
                    progress,
                    transition,
                    false);
        }
        if (progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                || !progress.currentStage().equals(transition.stageId())) {
            return transitionActionResult(
                    "stale",
                    "That choice is no longer available.",
                    inheritedReplacements,
                    context,
                    definition,
                    progress,
                    transition,
                    false);
        }

        String priorStage = progress.currentStage();
        String selectedNextStage = transition.target() == CompiledQuestTransition.Target.STAGE
                ? transition.targetStage()
                : transition.target().name().toLowerCase(Locale.ROOT);
        boolean changed = switch (transition.target()) {
            case STAGE -> changeQuestStage(context, definition, progress, transition.targetStage(), true, true);
            case COMPLETE -> transitionTerminalAction(context, definition, progress, VillagerActionDefinition.QuestAction.TURN_IN);
            case ABANDON -> transitionTerminalAction(context, definition, progress, VillagerActionDefinition.QuestAction.ABANDON);
            case FAIL -> transitionTerminalAction(context, definition, progress, VillagerActionDefinition.QuestAction.FAIL);
            case NONE -> false;
        };
        if (!changed) {
            return transitionActionResult(
                    "blocked",
                    "",
                    inheritedReplacements,
                    context,
                    definition,
                    progress,
                    transition,
                    false);
        }

        com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.recordChoice(context.player(), definition.id(), transition.sourcePointer());
        progress.recordChoice(
                transition.scenePath(),
                transition.responseId(),
                priorStage,
                selectedNextStage,
                context.level().getGameTime());
        markQuestChoiceFacts(context, definition, transition, priorStage, selectedNextStage);
        if (progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            advanceStageIfComplete(context, definition, progress);
        }
        data.setDirty();
        sendTrackerSync(context.player(), true);
        return transitionActionResult(
                "selected",
                "",
                inheritedReplacements,
                context,
                definition,
                progress,
                transition,
                true);
    }

    private static boolean transitionTerminalAction(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            VillagerActionDefinition.QuestAction action) {
        VillagerQuestSavedData.QuestState previousState = progress.state();
        String previousStage = progress.currentStage();
        QuestActionOutcome outcome = performAction(context, definition.id(), action)
                .orElse(null);
        return outcome != null
                && (progress.state() != previousState || !progress.currentStage().equals(previousStage));
    }

    private static VillagerActionResult transitionActionResult(
            String status,
            String text,
            Map<String, String> inheritedReplacements,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            CompiledQuestTransition transition,
            boolean flashTracker) {
        Map<String, String> values = new LinkedHashMap<>();
        if (inheritedReplacements != null) {
            values.putAll(inheritedReplacements);
        }
        if (context != null && definition != null) {
            values.putAll(replacements(context, definition, progress));
        }
        values.put("quest_transition_status", status == null ? "" : status);
        values.put("quest_transition_scene", transition == null ? "" : transition.scenePath());
        values.put("quest_transition_response", transition == null ? "" : transition.responseId());
        values.put("quest_transition_prior_stage", transition == null ? "" : transition.stageId());
        values.put("quest_transition_next_stage", transition == null ? "" : transition.targetStage());
        values.put("quest_transition_source", transition == null ? "" : transition.sourcePointer());
        String lineId = definition == null
                ? ""
                : lineId(definition, "response_transition_" + values.get("quest_transition_response"));
        return new VillagerActionResult(true, lineId, text, values, flashTracker);
    }

    private static VillagerActionDefinition.QuestAction fromDialogueAction(DialogueQuestAction.Action action) {
        if (action == null) {
            return VillagerActionDefinition.QuestAction.NONE;
        }
        return switch (action) {
            case START -> VillagerActionDefinition.QuestAction.START;
            case REMIND -> VillagerActionDefinition.QuestAction.REMIND;
            case TURN_IN -> VillagerActionDefinition.QuestAction.TURN_IN;
            case ABANDON -> VillagerActionDefinition.QuestAction.ABANDON;
            case BLOCK -> VillagerActionDefinition.QuestAction.BLOCK;
            case HINT, NONE -> VillagerActionDefinition.QuestAction.NONE;
        };
    }

    public static Map<String, String> replacementsFor(DialogueContext context, ResourceLocation questId) {
        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        if (definition == null) {
            return Map.of();
        }
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), questId);
        return replacements(context, definition, progress);
    }


    public static void onPlayerTick(ServerPlayer player) {
        boolean progressScanTick = player.tickCount % QUEST_PROGRESS_SCAN_INTERVAL_TICKS == 0;
        boolean itemTrackerSyncTick = player.tickCount % QUEST_ITEM_TRACKER_SYNC_INTERVAL_TICKS == 0;
        if (!(player.level() instanceof ServerLevel level)
                || (!progressScanTick && !itemTrackerSyncTick)
                || !player.isAlive()
                || player.isSpectator()) {
            return;
        }

        if (progressScanTick) {
            attachPendingPartyQuests(player, false);
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        if (!progressScanTick) {
            if (hasActiveQuestItemTracking(level, player, data)) {
                sendTrackerSync(player, false);
            }
            return;
        }

        boolean changed = false;
        boolean progressNotice = false;
        for (var entry : data.progress(player.getUUID())) {
            var progress = entry.getValue();
            if (com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.suspended(player, entry.getKey())) continue;
            if (progress.pendingPartyReward()) PartyQuestRewardService.deliverPendingPartyReward(player, entry.getKey(), progress);
            boolean maintenance = progress.hasPendingLifecycleEvents() || progress.hasPendingTriggerEvents() || !progress.campaignId().isBlank();
            if (!maintenance && progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) continue;
            var definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition == null) continue;
            if (maintenance) {
                changed |= CampaignQuestReconciliation.reconcile(player, definition, progress);
                changed |= CampaignQuestEntries.syncCompletion(player, definition, progress);
                if (progress.hasPendingLifecycleEvents() || progress.hasPendingTriggerEvents()) {
                    changed |= resumePendingTriggerWork(player, definition, progress);
                }
            }
            if (progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) continue;
            if (!CampaignQuestAccess.executionIssue(level, player.getUUID(), definition, progress).isBlank()) continue;
            DialogueContext questContext = contextForStartedVillager(level, player, progress).orElse(null);
            if (reconcileDefinitionRevision(level, player, definition, progress, questContext)) {
                changed = true;
                progressNotice = true;
            }
            if (progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
                continue;
            }
            if (progress.completionPending()) {
                if (questContext != null) {
                    var rewardDeliveryBefore = progress.rewardDelivery();
                    var outcome = finishQuestCompletion(questContext, definition, progress);
                    changed |= outcome.status().equals("completed") || !Objects.equals(rewardDeliveryBefore, progress.rewardDelivery());
                }
                continue;
            }
            boolean migratedStage = migrateRetiredHearthboundStage(definition, progress);
            if (migratedStage) {
                changed = true;
                progressNotice = true;
            }
            if (migratedStage && questContext != null) {
                syncQuestStageFact(questContext, definition, progress.currentStage());
            }
            if (expireQuestIfNeeded(player, definition, progress, questContext)) {
                changed = true;
                progressNotice = true;
                clearTrackedQuestIf(data, player, entry.getKey());
                com.jvn.villagerretaliation.party.PartyService
                        .getPartyForPlayer(level, player.getUUID())
                        .ifPresent(party -> PartyQuestService.detachQuest(
                                level,
                                party,
                                player.getUUID(),
                                entry.getKey()));
                continue;
            }
            boolean active = !definition.rules().activeState().pauseProgressWhenUnmet()
                    || activeConditionsState(questContext, player, level, definition, progress) == ConditionMatch.MET;
            changed |= com.jvn.villagerretaliation.quest.runtime.QuestTimers.update(definition, progress, level.getGameTime(), active);
            if (updateQuestTimers(player, definition, progress, questContext)) {
                changed = true;
                progressNotice = true;
                if (progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) continue;
            }
            if (definition.rules().activeState().pauseProgressWhenUnmet()
                    && activeConditionsState(questContext, player, level, definition, progress) != ConditionMatch.MET) {
                continue;
            }
            boolean questProgressChanged = false;
            for (var objective : QuestObjectiveQuery.stageObjectives(definition, progress.currentStage())) {
                if (objective.type() != QuestDefinition.ObjectiveType.FOLLOWER_ARRIVAL) continue;
                var target = com.jvn.villagerretaliation.quest.follower.QuestFollowerArrivalObjective.destination(level, objective).orElse(null);
                if (target != null && (!target.position().equals(progress.targetPos())
                        || !target.dimension().equals(progress.targetDimension()) || !objective.id().equals(progress.targetObjectiveId()))) {
                    progress.setTarget(progress.startedVillagerId(), target.dimension(), target.position(), objective.id());
                    changed = true;
                }
                break;
            }
            if (!progress.campaignAlternateEntry() && !progress.hasProof()
                    && definition.target().hasProofItem()
                    && hasRequiredProof(player, definition)
                    && progress.markHasProof()) {
                changed = true;
                progressNotice = true;
                questProgressChanged = true;
                sendQuestProgressNotification(
                        player,
                        definition,
                        progress,
                        "quest.updated",
                        "Quest updated: {quest}");
            }
            if (!progress.campaignAlternateEntry() && !progress.visitedTarget()
                    && VillagerQuestTargets.isAtQuestTarget(level, player.blockPosition(), definition, progress)
                    && progress.markVisitedTarget()) {
                changed = true;
                progressNotice = true;
                questProgressChanged = true;
                sendQuestProgressNotification(
                        player,
                        definition,
                        progress,
                        "quest.location_reached",
                        "Quest location reached: {quest}");
            }
            if (updateObjectiveProgress(level, player, definition, progress, questContext)) {
                changed = true;
                progressNotice = true;
                questProgressChanged = true;
                sendQuestProgressNotification(
                        player,
                        definition,
                        progress,
                        "quest.updated",
                        "Quest updated: {quest}");
            }
            if (advanceStageAfterEvent(level, player, definition, progress, questContext)) {
                changed = true;
                progressNotice = true;
                questProgressChanged = true;
            }
            if (questProgressChanged) {
                changed |= dispatchQuestTriggers(player, definition, progress, QuestDefinition.TriggerEvent.PROGRESS);
            }
            changed |= dispatchQuestTriggers(player, definition, progress, QuestDefinition.TriggerEvent.PLAYER_TICK);
            changed |= dispatchQuestTriggers(player, definition, progress, QuestDefinition.TriggerEvent.PROXIMITY);
        }
        if (changed) {
            data.setDirty();
            sendTrackerSync(player, progressNotice);
        } else if (player.tickCount % (QUEST_PROGRESS_SCAN_INTERVAL_TICKS * 2) == 0) {
            sendTrackerSync(player, false);
        }
    }

    private static boolean reconcileDefinitionRevision(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueContext context) {
        QuestDefinitionMigration.Result migration = QuestDefinitionMigration.apply(
                definition, progress, level.getGameTime());
        if (!migration.changed()) {
            return false;
        }
        if (migration.failed()) {
            SceneLifecycleIntegration.onQuestTerminal(level, player.getUUID(), definition.id(), "failed");
            if (context != null) {
                markQuestLifecycleFact(level, player, definition, QUEST_FAILED_FACT, "failed");
                sendQuestNotification(context, "quest.failed", definition, progress, "Quest failed: {quest}");
                dispatchQuestTriggers(context, definition, progress, QuestDefinition.TriggerEvent.FAILED);
            }
        }
        if (context != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            syncQuestStageFact(context, definition, progress.currentStage());
        }
        QuestDebugTraceService.recordIfEnabled(
                player,
                QuestDebugTraceService.EventType.OBJECTIVE_PROGRESS,
                definition.id(),
                "definition_revision from=" + migration.previousRevision()
                        + " to=" + migration.currentRevision()
                        + " policy=" + migration.policy().name().toLowerCase(Locale.ROOT));
        return true;
    }

    public static boolean migrateRetiredHearthboundStage(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (definition == null || progress == null) {
            return false;
        }
        String stage = progress.currentStage();
        boolean retiredStage =
                (definition.id().equals(VillagerRetaliation.id("first_fire")) && stage.equals("fill_pots"))
                        || (definition.id().equals(VillagerRetaliation.id("shared_table")) && stage.equals("sweeten"));
        return retiredStage && progress.setCurrentStage(definition.entryStage());
    }

    private static boolean hasActiveQuestItemTracking(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData data) {
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : data.activeProgress(player.getUUID())) {
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            if (definition.target().hasProofItem()) {
                return true;
            }
            for (QuestDefinition.Objective objective : requiredObjectivesForReadiness(definition, entry.getValue())) {
                if (objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK
                        && QuestObjectiveQuery.resolvedItem(objective, entry.getValue()) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void onVillagerDeath(Villager villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        List<VillagerQuestSavedData.QuestEntry> affected = data.activeProgressStartedBy(villager.getUUID());
        if (affected.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        boolean changed = false;
        for (VillagerQuestSavedData.QuestEntry entry : affected) {
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.questId()).orElse(null);
            if ((!entry.progress().campaignId().isBlank() && !CampaignQuestAccess.executionIssue(level, entry.playerId(), definition, entry.progress()).isBlank())
                    || definition != null && (!CampaignQuestAccess.contentIssue(level, definition).isBlank()
                    || !definition.rules().lockedToVillager())) {
                continue;
            }

            if (entry.progress().completionPending()) continue;
            QuestLifecycleService.expire(entry.questId(), entry.progress(), gameTime, false);
            SceneLifecycleIntegration.onQuestTerminal(level, entry.playerId(), entry.questId(), "expired");
            if (definition != null) {
                deferLifecycleEvent(level, definition, entry.progress(), QuestDefinition.TriggerEvent.EXPIRED);
            }
            changed = true;
            PartyService.getPartyForPlayer(level, entry.playerId())
                    .ifPresent(party -> PartyQuestService.detachQuest(
                            level,
                            party,
                            entry.playerId(),
                            entry.questId()));
            data.removeTrackedQuest(entry.playerId(), entry.questId());

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.playerId());
            if (player != null) {
                if (definition != null) {
                    sendQuestIssuerDeathNotification(player, definition, entry.progress());
                }
                sendTrackerSync(player, true);
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    public static boolean isReadyToTurnIn(DialogueContext context, ResourceLocation questId) {
        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        if (definition == null) {
            return false;
        }
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), questId);
        return isReadyToTurnIn(context, definition, progress);
    }

    public static void flashTracker(ServerPlayer player, boolean flash) {
        sendTrackerSync(player, flash);
    }

    public static void handleTrackerRequest(ServerPlayer player, String questIdText, QuestTrackerRequestPayload.Action action) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation questId = ResourceLocation.tryParse(questIdText);
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        if (action == QuestTrackerRequestPayload.Action.REFRESH) {
            sendTrackerSync(player, false, true);
            return;
        }
        if (action == QuestTrackerRequestPayload.Action.ACCEPT) {
            if (questId != null) com.jvn.villagerretaliation.quest.discovery.QuestDiscoveryService.accept(player, questId);
            sendTrackerSync(player, false, true);
            return;
        }
        if (action == QuestTrackerRequestPayload.Action.ABANDON) {
            abandonQuestFromJournal(player, questId);
            sendTrackerSync(player, false, true);
            return;
        }
        if (questId == null) {
            sendTrackerSync(player, false, true);
            return;
        }

        QuestTrackerRequestPayload.Action requestedAction =
                action == null ? QuestTrackerRequestPayload.Action.TOGGLE : action;
        PartyRecord party = PartyService.getPartyForPlayer(level, player.getUUID()).orElse(null);
        if (isEnrolledPartyQuest(party, player.getUUID(), questId)) {
            if (!party.hasAdminPrivileges(player.getUUID())) {
                player.sendSystemMessage(Component.translatable(
                        "villagerretaliation.party.error.admin_privileges_required"));
                sendTrackerSync(player, false, true);
                return;
            }
            boolean alreadyTracked = party.trackedQuests().contains(questId);
            boolean addsTracking = requestedAction == QuestTrackerRequestPayload.Action.TRACK
                    || requestedAction == QuestTrackerRequestPayload.Action.TOGGLE && !alreadyTracked;
            if (addsTracking && !canTrackPartyQuest(level, player, party, questId)) {
                sendTrackerSync(player, false, true);
                return;
            }
            data.removeTrackedQuest(player.getUUID(), questId);
            boolean changed = switch (requestedAction) {
                case TRACK -> party.setTrackedQuest(questId);
                case UNTRACK -> party.removeTrackedQuest(questId);
                case TOGGLE -> party.toggleTrackedQuest(questId);
                case ABANDON, REFRESH, ACCEPT -> false;
            };
            if (changed) {
                PartyService.markChanged(level);
            }
            sendPartyTrackerSync(player, party, false);
            return;
        }

        if (!canTrackQuest(level, player, questId)) {
            sendTrackerSync(player, false, true);
            return;
        }
        switch (requestedAction) {
            case TRACK -> data.setTrackedQuest(player.getUUID(), questId);
            case UNTRACK -> data.removeTrackedQuest(player.getUUID(), questId);
            case TOGGLE -> data.toggleTrackedQuest(player.getUUID(), questId);
            case ABANDON, REFRESH -> {
            }
        }
        sendTrackerSync(player, false, true);
    }

    public static boolean abandonQuestFromJournal(ServerPlayer player, ResourceLocation questId) {
        if (player == null || questId == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), questId);
        if (definition == null || progress == null || progress.completionPending() || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            return false;
        }
        DialogueContext liveContext = contextForStartedVillager(level, player, progress).orElse(null);
        if (liveContext != null) {
            return abandonQuest(liveContext, definition).status().startsWith("abandoned");
        }

        boolean consume = definition.rules().consumeOnAbandonment()
                || definition.rules().abandonment() == QuestDefinition.AbandonmentMode.REMOVE_FOREVER;
        QuestLifecycleService.abandon(definition.id(), progress, level.getGameTime(), consume);
        SceneLifecycleIntegration.onQuestTerminal(level, player.getUUID(), definition.id(), "abandoned");
        boolean deferred = deferLifecycleEvent(
                level, definition, progress, QuestDefinition.TriggerEvent.ABANDONED);
        com.jvn.villagerretaliation.party.PartyService.getPartyForPlayer(level, player.getUUID())
                .ifPresent(party -> PartyQuestService.detachQuest(level, party, player.getUUID(), definition.id()));
        markQuestLifecycleFact(level, player, definition, QUEST_ABANDONED_FACT, "abandoned");
        clearTrackedQuestIf(data, player, definition.id());
        data.setDirty();
        sendQuestProgressNotification(player, definition, progress, "quest.abandoned", "Quest abandoned: {quest}");
        QuestDebugTraceService.recordIfEnabled(player, QuestDebugTraceService.EventType.TRIGGER, definition.id(),
                "journal_abandon provider=missing lifecycle_dispatch=" + (deferred ? "queued" : "no_hook"));
        return true;
    }

    public static ProviderRebindResult debugRebindQuest(
            ServerPlayer player,
            Villager replacement,
            ResourceLocation questId) {
        return DEBUG.debugRebindQuest(player, replacement, questId);
    }

    public static DebugStartResult debugStartQuest(ServerPlayer player, Villager provider, ResourceLocation questId, boolean force) {
        return DEBUG.debugStartQuest(player, provider, questId, force);
    }

    public static DebugRemoveResult debugRemoveQuest(ServerPlayer player, ResourceLocation questId) {
        return DEBUG.debugRemoveQuest(player, questId);
    }

    public static DebugInspectResult debugInspectQuest(ServerPlayer player, ResourceLocation questId) {
        return DEBUG.debugInspectQuest(player, questId);
    }

    public static DebugInspectResult debugWhyAvailable(ServerPlayer player, Villager provider, ResourceLocation questId) {
        if (player == null || provider == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player with a nearby provider.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugInspectResult(false, List.of(), "Unknown quest: " + questId);
        }

        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, provider);
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(level).get(player.getUUID(), definition.id());
        boolean offerMatches = VillagerQuestProviderType.INSTANCE.matchesOffer(
                QuestExecutionContext.fromDialogueContext(context, definition, "debug_why_available"),
                definition);
        boolean available = canStart(context, definition, progress);
        String status = available ? "available" : startBlockedStatus(context, definition, progress);

        List<String> lines = new ArrayList<>();
        lines.add("why_available quest=" + definition.id() + " available=" + available + " status=" + status);
        String campaignIssue = CampaignQuestAccess.availabilityIssue(context, definition);
        if (definition.campaign() != null || !campaignIssue.isBlank()) {
            lines.add("campaign=" + definition.campaign() + " available=" + campaignIssue.isBlank() + " reason=" + campaignIssue);
        }
        lines.add("provider match=" + offerMatches
                + " issuer_lock=" + (progress == null ? "n/a" : matchesVillagerLock(context, definition, progress))
                + " cross_villager_locked=" + crossVillagerLocked(context, definition, progress)
                + " provider=" + debugProvider(provider));
        lines.add("limits parent_completed=" + parentCompleted(context, definition)
                + " start_limit=" + withinStartLimit(definition, progress)
                + " completion_limit=" + withinCompletionLimit(context, definition, progress)
                + " repeatable=" + definition.rules().repeatable()
                + " completion_cooldown_active=" + completionCooldownActive(context, definition, progress));
        lines.add("state saved=" + (progress != null)
                + " state=" + debugEnum(progress == null ? VillagerQuestSavedData.QuestState.NOT_STARTED : progress.state())
                + " starts=" + (progress == null ? 0 : progress.startCount())
                + " completions=" + (progress == null ? 0 : progress.completionCount()));
        appendActiveConditionDebugLines(lines, player, level, context, definition, progress);

        QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.PROVIDER, definition.id(),
                "offer_match=" + offerMatches + " provider=" + debugProvider(provider));
        recordConditionDebugTrace(player, definition, context, "available");
        return new DebugInspectResult(true, lines, "");
    }

    public static DebugInspectResult debugWhyHidden(ServerPlayer player, Villager provider, ResourceLocation questId) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player so quest state can be resolved.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugInspectResult(false, List.of(), "Unknown quest: " + questId);
        }

        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(level).get(player.getUUID(), definition.id());
        DialogueContext context = provider == null
                ? contextForStartedVillager(level, player, progress).orElse(null)
                : VillagerInteractionService.createDialogueContext(level, player, provider);
        ConditionMatch activeConditions = activeConditionsState(context, player, level, definition, progress);
        boolean rawActive = progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && (context == null || matchesVillagerLock(context, definition, progress));
        boolean hiddenByActiveConditions = rawActive
                && activeConditions == ConditionMatch.UNMET
                && definition.rules().activeState().hideWhenUnmet();
        boolean available = context != null && canStart(context, definition, progress);
        String reason = hiddenByActiveConditions
                ? "active_conditions_unmet"
                : rawActive ? "visible_active"
                : available ? "available_to_start"
                : context == null ? "no_live_provider_context" : startBlockedStatus(context, definition, progress);

        List<String> lines = new ArrayList<>();
        lines.add("why_hidden quest=" + definition.id()
                + " hidden=" + hiddenByActiveConditions
                + " reason=" + reason
                + " active_conditions=" + debugEnum(activeConditions)
                + " hide_when_unmet=" + definition.rules().activeState().hideWhenUnmet());
        lines.add("state saved=" + (progress != null)
                + " state=" + debugEnum(progress == null ? VillagerQuestSavedData.QuestState.NOT_STARTED : progress.state())
                + " stage=" + (progress == null ? "none" : progress.currentStage())
                + " provider=" + (provider == null ? "started_or_none" : debugProvider(provider)));
        appendActiveConditionDebugLines(lines, player, level, context, definition, progress);

        QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.PROVIDER, definition.id(),
                "hidden_context=" + (context == null ? "missing" : "live") + " provider="
                        + (provider == null ? "started_or_none" : debugProvider(provider)));
        recordConditionDebugTrace(player, definition, context, "hidden");
        return new DebugInspectResult(true, lines, "");
    }

    public static DebugInspectResult debugTraceQuest(ServerPlayer player, Villager provider, ResourceLocation questId) {
        if (player == null || provider == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player with a nearby provider.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugInspectResult(false, List.of(), "Unknown quest: " + questId);
        }

        QuestDebugTraceService.clear(player);
        QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.NOTE, definition.id(), "capture started");
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, provider);
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(level).get(player.getUUID(), definition.id());
        debugWhyAvailable(player, provider, definition.id());
        debugWhyHidden(player, provider, definition.id());
        recordDialogueSlotTrace(player, context, definition, progress);
        recordResponseTrace(player, context, definition, progress);
        recordObjectiveTrace(player, context, level, definition, progress);
        recordTriggerTrace(player, context, definition, progress);
        QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.TRACKER_SYNC, definition.id(),
                "reason=debug_capture bounded_capacity=" + QuestDebugTraceService.capacity());
        QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.NOTE, definition.id(), "capture complete");
        return debugTraceRecent(player, QuestDebugTraceService.capacity());
    }

    public static DebugInspectResult debugTraceRecent(ServerPlayer player, int limit) {
        return DEBUG.debugTraceRecent(player, limit);
    }

    public static DebugInspectResult debugTraceClear(ServerPlayer player) {
        return DEBUG.debugTraceClear(player);
    }

    public static DebugInspectResult debugTraceSetEnabled(ServerPlayer player, boolean enabled) {
        return DEBUG.debugTraceSetEnabled(player, enabled);
    }

    public static com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext debugPlaytestContext(ServerPlayer player, ResourceLocation quest) {
        var definition = VillagerQuestResources.quest(player.server, quest).orElseThrow(() -> new IllegalArgumentException("Unknown quest " + quest));
        var progress = VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), quest);
        if (progress == null) throw new IllegalStateException("Start the quest first");
        var context = contextForStartedVillager(player.serverLevel(), player, progress).orElseThrow(() -> new IllegalStateException("The quest provider is not loaded"));
        return objectiveEvaluationContext(player, context, player.serverLevel(), definition, progress);
    }

    public static com.jvn.villagerretaliation.authoring.preview.QuestPlaytestView debugPlaytestView(ServerPlayer player, ResourceLocation quest) {
        var definition = VillagerQuestResources.quest(player.server, quest).orElse(null);
        var progress = VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), quest);
        if (definition == null || progress == null) return com.jvn.villagerretaliation.authoring.preview.QuestPlaytestView.empty();
        var context = contextForStartedVillager(player.serverLevel(), player, progress).orElse(null);
        var evaluation = objectiveEvaluationContext(player, context, player.serverLevel(), definition, progress);
        var rows = new java.util.ArrayList<com.jvn.villagerretaliation.authoring.preview.QuestPlaytestView.Objective>();
        for (var objective : com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery.stageObjectives(definition, progress.currentStage())) {
            var result = QuestObjectiveRegistry.evaluate(evaluation, objective).orElse(com.jvn.villagerretaliation.quest.objectives.QuestObjectiveResult.incomplete(0, "No evaluator"));
            boolean complete = objectiveComplete(player, context, player.serverLevel(), definition, progress, objective);
            rows.add(new com.jvn.villagerretaliation.authoring.preview.QuestPlaytestView.Objective(objective.id(),
                    QuestObjectiveRegistry.canonicalTypeId(objective), complete, complete ? 1 : result.progress(), result.message(),
                    com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSetup.label(objective)));
        }
        var readiness = com.jvn.villagerretaliation.quest.tracking.QuestStageReadiness.forCurrentStage(definition, progress,
                objective -> objectiveComplete(player, context, player.serverLevel(), definition, progress, objective),
                conditions -> context != null && (com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.stageForced(player, quest, progress.currentStage())
                        || com.jvn.villagerretaliation.dialogue.DialogueCondition.matchesAll(context, conditions)));
        var transitions = context == null ? java.util.List.<com.jvn.villagerretaliation.authoring.preview.QuestPlaytestView.Transition>of()
                : java.util.stream.Stream.concat(stageBranchOptions(context).stream(), embeddedDialogueOptions(context, com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition.NEUTRAL).stream())
                    .filter(option -> com.jvn.villagerretaliation.quest.runtime.QuestStageBranchOptionIds.parse(option.id()).map(key -> key.questId().equals(quest)).orElse(false)
                            || option.treeReference() != null && VillagerQuestResources.questDialogueCatalog(player.server).bindings(quest).stream().anyMatch(binding -> binding.treeId().equals(option.treeReference().treeId())))
                    .map(option -> new com.jvn.villagerretaliation.authoring.preview.QuestPlaytestView.Transition(option.id(), option.label(), option.available(), option.unavailableReason())).toList();
        var controls = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.controls(player, quest);
        var provider = progress.startedVillagerId() == null ? null : player.serverLevel().getEntity(progress.startedVillagerId());
        return new com.jvn.villagerretaliation.authoring.preview.QuestPlaytestView(progress.currentStage(), progress.state().name().toLowerCase(java.util.Locale.ROOT),
                provider == null ? -1 : provider.getId(), controls == null || controls.hold(), readiness.ready(), readiness.reason(), rows,
                definition.stages().keySet().stream().toList(), transitions, controls == null ? List.of() : controls.log(), context == null ? Map.of() : replacements(context, definition, progress));
    }

    public static void debugPlaytestAdvance(ServerPlayer player, ResourceLocation quest) {
        var evaluation = debugPlaytestContext(player, quest);
        var controls = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.require(player, quest);
        controls.advance(evaluation.progress().currentStage(), () -> {
            advanceStageIfComplete(evaluation.dialogueContext(), evaluation.definition(), evaluation.progress());
            VillagerQuestSavedData.get(player.serverLevel()).setDirty();
            sendTrackerSync(player, true);
        });
    }

    public static void debugPlaytestReplay(ServerPlayer player, ResourceLocation quest) {
        var evaluation = debugPlaytestContext(player, quest);
        com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.require(player, quest).clear();
        var progress = evaluation.progress();
        if (progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) throw new IllegalStateException("Restart the completed quest before replaying a stage");
        if (!QuestStageActionService.ready(evaluation.dialogueContext(), progress)) throw new IllegalStateException("Resolve pending stage actions before replaying");
        var stage = evaluation.definition().stages().get(progress.currentStage());
        progress.resetObjectiveProgress(new java.util.HashSet<>(stage.objectives()));
        progress.resetBonusesForStage(stage.id());
        progress.requestStageEntry(stage.id());
        STAGES.resumePendingStageEntry(evaluation.dialogueContext(), evaluation.definition(), progress);
        VillagerQuestSavedData.get(player.serverLevel()).setDirty();
        sendTrackerSync(player, true);
    }

    public static DebugInspectResult debugObjectives(ServerPlayer player, ResourceLocation questId) {
        return DEBUG.debugObjectives(player, questId);
    }

    public static DebugInspectResult debugSetQuestStage(ServerPlayer player, ResourceLocation questId, String stage) {
        return DEBUG.debugSetQuestStage(player, questId, stage);
    }

    public static DebugInspectResult debugFireTrigger(
            ServerPlayer player,
            ResourceLocation questId,
            QuestDefinition.TriggerEvent event) {
        return DEBUG.debugFireTrigger(player, questId, event);
    }

    public static DebugInspectResult debugDryRunTriggerActions(
            ServerPlayer player,
            ResourceLocation questId,
            String triggerId) {
        return DEBUG.debugDryRunTriggerActions(player, questId, triggerId);
    }

    public static DebugInspectResult debugFactScope(ServerPlayer player, String scopeKey) {
        return DEBUG.debugFactScope(player, scopeKey);
    }

    public static boolean syncQuestStage(DialogueContext context, ResourceLocation questId, String stage) {
        if (context == null || questId == null || stage == null) {
            return false;
        }
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), questId);
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            return false;
        }
        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        String normalizedStage = stage.trim();
        if (definition == null || !definition.stages().containsKey(normalizedStage)) {
            return false;
        }
        boolean changed = changeQuestStage(context, definition, progress, normalizedStage, true, true);
        if (changed) {
            data.setDirty();
            advanceStageIfComplete(context, definition, progress);
        }
        return changed;
    }

    private static void initializeQuestStage(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        STAGES.initializeQuestStage(context, definition, progress);
    }

    private static void initializeQuestStage(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String entryStage) {
        STAGES.initializeQuestStage(context, definition, progress, entryStage);
    }

    private static String initialStage(QuestDefinition definition) {
        return QuestLifecycleService.initialStage(definition);
    }

    private static void ensureStageTarget(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (context == null || definition == null || progress == null
                || definition.target().hasStructureTarget() && !progress.campaignAlternateEntry()) {
            return;
        }
        QuestDefinition.Objective targetObjective = QuestObjectiveQuery.stageObjectives(definition, progress.currentStage()).stream()
                .filter(QuestObjectiveRegistry::requiresLocatedTarget)
                .findFirst()
                .orElse(null);
        if (targetObjective == null
                || (targetObjective.id().equals(progress.targetObjectiveId()) && progress.targetPos() != null)) {
            return;
        }
        VillagerQuestTargets.locateTarget(context.level(), context.origin(), targetObjective)
                .ifPresent(target -> {
                    progress.setTarget(context.providerId(), target.dimension(), target.pos(), target.objectiveId());
                    rememberQuestStoryHint(context, definition, target);
                    maybeGiveQuestTargetMap(context, definition, target);
                });
    }

    private static boolean changeQuestStage(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String stage,
            boolean runExitActions,
            boolean runEntryActions) {
        return STAGES.changeQuestStage(context, definition, progress, stage, runExitActions, runEntryActions);
    }

    private static boolean resumePendingStageEntry(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return STAGES.resumePendingStageEntry(context, definition, progress);
    }

    private static void syncQuestStageFact(
            DialogueContext context,
            QuestDefinition definition,
            String stage) {
        STAGES.syncQuestStageFact(context, definition, stage);
    }

    private static boolean advanceStageIfComplete(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return STAGES.advanceStageIfComplete(context, definition, progress);
    }

    private static boolean advanceStageAfterEvent(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return STAGES.advanceStageAfterEvent(level, player, definition, progress);
    }

    private static boolean advanceStageAfterEvent(ServerLevel level, ServerPlayer player, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, DialogueContext context) {
        return STAGES.advanceStageAfterEvent(level, player, definition, progress, context);
    }

    private static ActionResult runStageActions(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String key,
            List<VillagerActionDefinition> actions) {
        return QuestStageActionService.run(context, definition, progress, key, actions,
                replacements(context, definition, progress), () -> sendTrackerSync(context.player(), true));
    }

    public static boolean canStartDiscoveredQuest(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return (progress == null || !progress.completionPending() && !progress.pendingPartyReward())
                && canStart(context, definition, progress, false);
    }

    public static QuestActionOutcome startDiscoveredQuest(DialogueContext context, ResourceLocation id) {
        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), id).orElse(null);
        return definition == null ? result("unavailable", "", "Quest content is unavailable.", Map.of())
                : startQuest(context, definition, false, false);
    }

    private static QuestActionOutcome startQuest(DialogueContext context, QuestDefinition definition) {
        return startQuest(context, definition, false, false);
    }

    private static QuestActionOutcome startQuest(
            DialogueContext context,
            QuestDefinition definition,
            boolean bypassOfferRequirements,
            boolean forceRestart) {
        String campaignIssue = CampaignQuestAccess.executionIssue(context, definition);
        if (!campaignIssue.isBlank()) return result("unavailable", lineId(definition, "unavailable"), campaignIssue, Map.of());
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress != null && progress.completionPending()) {
            return result("reward_pending", lineId(definition, "unavailable"),
                    "Finish claiming this quest's rewards before restarting it.", replacements(context, definition, progress));
        }
        if (progress != null && progress.pendingPartyReward()) {
            PartyQuestRewardService.deliverPendingPartyRewards(context.player());
            if (progress.pendingPartyReward()) {
                return result(
                        "pending_reward",
                        lineId(definition, "unavailable"),
                        QuestPresentationService.resolveGlobalText(
                                context.player(),
                                "quest.party_reward_pending",
                                "Your previous party quest reward is still pending.",
                                replacements(context, definition, progress)),
                        replacements(context, definition, progress));
            }
        }
        if (!forceRestart && progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            if (matchesVillagerLock(context, definition, progress)) {
                linkExistingActivePartyQuest(context, definition, progress);
                if (resumePendingLifecycleEvents(context.player(), definition, progress)) data.setDirty();
                return remindQuest(context, definition);
            }
            return result(
                    "locked_to_villager",
                    lineId(definition, "unavailable"),
                    startBlockedLine(context, definition, progress),
                    replacements(context, definition, progress));
        }
        if (!forceRestart && progress != null && !QuestStageActionService.ready(context, progress)) {
            return result("unavailable", lineId(definition, "unavailable"),
                    startBlockedLine(context, definition, progress), replacements(context, definition, progress));
        }
        if (!forceRestart && !canStart(context, definition, progress, bypassOfferRequirements)) {
            return result(
                    startBlockedStatus(context, definition, progress),
                    lineId(definition, "unavailable"),
                    startBlockedLine(context, definition, progress),
                    replacements(context, definition, progress));
        }

        campaignIssue = CampaignQuestAccess.availabilityIssue(context, definition);
        if (!campaignIssue.isBlank()) return result("unavailable", lineId(definition, "unavailable"), campaignIssue, Map.of());
        var acceptance = com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.approved(context, definition);
        if (!forceRestart && acceptance == null
                && com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.requiresConfirmation(context, definition)) {
            return result("unavailable", lineId(definition, "unavailable"),
                    "Review and confirm this quest's shared consequences before accepting it.", Map.of());
        }
        CampaignQuestEntries.Entry campaignEntry = CampaignQuestEntries.start(context, definition);
        if (!campaignEntry.available()) return result("unavailable", lineId(definition, "unavailable"), campaignEntry.diagnostic(), Map.of());
        VillagerQuestTargets.LocatedTarget target = (campaignEntry.alternateStage()
                ? VillagerQuestTargets.locateStageTarget(context, definition, campaignEntry.stage())
                : VillagerQuestTargets.locateInitialTarget(context, definition)).orElse(null);
        if (target == null && (campaignEntry.alternateStage()
                ? QuestObjectiveQuery.stageObjectives(definition, campaignEntry.stage()).stream()
                        .anyMatch(objective -> !objective.optional() && QuestObjectiveRegistry.requiresLocatedTarget(objective))
                : VillagerQuestTargets.requiresLocatedTarget(definition, campaignEntry.stage()))) {
            return result(
                    "locate_failed",
                    lineId(definition, "locate_failed"),
                    QuestPresentationService.resolveQuestText(
                            context,
                            definition.dialogue().selectLocateFailedText(context),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }

        VillagerQuestSavedData.QuestProgress started = data.getOrCreate(context.player().getUUID(), definition.id());
        QuestProviderBinding providerBinding = VillagerQuestProviderType.INSTANCE.bindingFromDialogueContext(context);
        PartyQuestEnrollmentService.StartPlan partyStart = preparePartyStart(context, definition);
        UUID definitiveRunId = partyStart == null ? null : partyStart.shared().instanceId();
        QuestLifecycleService.start(definition.id(), started, providerBinding, target,
                context.level().getGameTime(), context.player().getUUID(), definitiveRunId, forceRestart);
        started.adoptDefinitionRevision(definition.revision().number());
        started.bindCampaign(definition.campaign());
        started.campaignAcceptance(acceptance);
        started.campaignEntryStage(campaignEntry.alternateStage() ? campaignEntry.stage() : "");
        if (campaignEntry.catchUp()) started.recordCampaignCatchUp(campaignEntry.worldOperation());
        ensureRandomItemSelections(definition, started);
        if (acceptance != null) {
            QuestLifecycleService.initializeStage(definition, started, context.level().getGameTime(), campaignEntry.stage());
            started.requestStageEntry(campaignEntry.stage());
            deferLifecycleEvent(context.level(), definition, started, QuestDefinition.TriggerEvent.STARTED);
            if (!com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.persistAcceptedStart(context)) {
                return result("unavailable", lineId(definition, "unavailable"),
                        "Your accepted quest is waiting for world storage. Its effects will resume when it can be saved.", Map.of());
            }
        }
        if (partyStart != null) {
            partyStart.shared().enroll(context.player().getUUID(), false);
            started.linkPartyQuest(definitiveRunId);
            com.jvn.villagerretaliation.party.PartyService.markChanged(context.level());
        }
        markContinuousTriggersUsed(started, context, definition);
        if (definition.target().hasProofItem() && hasRequiredProof(context.player(), definition)) {
            started.markHasProof();
        }
        data.setDirty();
        PartyRecord currentParty = PartyService
                .getPartyForPlayer(context.level(), context.player().getUUID())
                .orElse(null);
        if (currentParty == null) {
            data.setTrackedQuest(context.player().getUUID(), definition.id());
        } else if (partyStart != null && currentParty.hasAdminPrivileges(context.player().getUUID())) {
            currentParty.setTrackedQuest(definition.id());
            PartyService.markChanged(context.level());
        }
        VillagerQuestDeathProtectionService.markAfterSuccessfulStart(
                context.level(),
                context.villager(),
                definition.id());
        shareStartedQuest(context, definition, started, partyStart);
        markQuestLifecycleFact(context.level(), context.player(), definition, QUEST_STARTED_FACT, "started");
        if (acceptance == null) {
            initializeQuestStage(context, definition, started, campaignEntry.stage());
        } else {
            resumePendingStageEntry(context, definition, started);
            advanceStageIfComplete(context, definition, started);
        }
        if (!campaignEntry.alternateStage()) lockBranchQuests(context, definition, QuestDefinition.BranchLockEvent.STARTED);
        data.setDirty();
        if (target != null) {
            rememberQuestStoryHint(context, definition, target);
            maybeGiveQuestTargetMap(context, definition, target);
        }
        sendQuestNotification(context, "quest.started", definition, started, "Quest started: {quest}");
        if (acceptance != null) {
            resumePendingLifecycleEvents(context.player(), definition, started);
        } else if (!campaignEntry.alternateStage()) {
            if (!QuestStageActionService.ready(context, started)) {
                deferLifecycleEvent(context.level(), definition, started, QuestDefinition.TriggerEvent.STARTED);
                data.setDirty();
            } else if (dispatchQuestTriggers(context, definition, started, QuestDefinition.TriggerEvent.STARTED)) {
                data.setDirty();
            }
        }
        sendTrackerSync(context.player(), true);

        return result(
                "started",
                lineId(definition, "start"),
                QuestPresentationService.resolveQuestText(
                        context,
                        definition.dialogue().selectStartText(context),
                        replacements(context, definition, started)),
                replacements(context, definition, started));
    }

    private static PartyQuestEnrollmentService.StartPlan preparePartyStart(DialogueContext context, QuestDefinition definition) {
        return PARTY_QUEST_ENROLLMENT.preparePartyStart(context, definition);
    }

    private static void shareStartedQuest(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, PartyQuestEnrollmentService.StartPlan plan) {
        PARTY_QUEST_ENROLLMENT.shareStartedQuest(context, definition, progress, plan);
    }

    private static void linkExistingActivePartyQuest(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        PARTY_QUEST_ENROLLMENT.linkExistingActivePartyQuest(context, definition, progress);
    }

    public static void attachPendingPartyQuests(ServerPlayer player) {
        attachPendingPartyQuests(player, true);
    }

    private static void attachPendingPartyQuests(ServerPlayer player, boolean deliverRewards) {
        PARTY_QUEST_ENROLLMENT.attachPendingPartyQuests(player, deliverRewards);
    }

    private static VillagerQuestSavedData.QuestProgress canonicalSharedProgress(ServerLevel level,
            com.jvn.villagerretaliation.party.PartySharedQuestRecord shared) {
        return PartyQuestEnrollmentService.canonicalSharedProgress(level, shared);
    }

    private static void applyPartyMemberStart(DialogueContext memberContext, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress linked, VillagerQuestTargets.LocatedTarget target) {
        ServerLevel level = memberContext.level();
        ServerPlayer member = memberContext.player();
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        markContinuousTriggersUsed(linked, memberContext, definition);
        markQuestLifecycleFact(level, member, definition, QUEST_STARTED_FACT, "started");
        initializeQuestStage(memberContext, definition, linked);
        lockBranchQuests(memberContext, definition, QuestDefinition.BranchLockEvent.STARTED);
        if (target != null) {
            rememberQuestStoryHint(memberContext, definition, target);
            maybeGiveQuestTargetMap(memberContext, definition, target);
        }
        sendQuestNotification(memberContext, "quest.started", definition, linked, "Quest started: {quest}");
        if (!QuestStageActionService.ready(memberContext, linked)) {
            deferLifecycleEvent(level, definition, linked, QuestDefinition.TriggerEvent.STARTED);
            data.setDirty();
        } else if (dispatchQuestTriggers(memberContext, definition, linked, QuestDefinition.TriggerEvent.STARTED)) {
            data.setDirty();
        }
    }

    private static QuestActionOutcome remindQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !matchesVillagerLock(context, definition, progress)) {
            return result(
                    "unavailable",
                    lineId(definition, "unavailable"),
                    QuestPresentationService.resolveQuestText(
                            context,
                            definition.dialogue().selectUnavailableText(context),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        if (!activeConditionsMet(context, definition)) {
            return result(
                    "inactive",
                    lineId(definition, "inactive"),
                    QuestPresentationService.resolveQuestText(
                            context,
                            definition.dialogue().selectInactiveText(context),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        return result(
                "reminder",
                lineId(definition, "reminder"),
                QuestPresentationService.resolveQuestText(
                        context,
                        definition.dialogue().selectReminderText(context),
                        replacements(context, definition, progress)),
                replacements(context, definition, progress));
    }

    private static QuestActionOutcome turnInQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !matchesVillagerLock(context, definition, progress)) {
            return result(
                    "unavailable",
                    lineId(definition, "unavailable"),
                    QuestPresentationService.resolveQuestText(
                            context,
                            definition.dialogue().selectUnavailableText(context),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        if (!progress.rewardDeliveryIssue().isBlank()) {
            return result("reward_pending", lineId(definition, "unavailable"), progress.rewardDeliveryIssue(), replacements(context, definition, progress));
        }
        if (progress.completionPending()) return finishQuestCompletion(context, definition, progress);
        if (!QuestStageActionService.ready(context, progress) || !progress.pendingStageTransition().isBlank()
                || !progress.pendingStageEntry().isBlank()) {
            return result("unavailable", lineId(definition, "unavailable"),
                    QuestPresentationService.resolveQuestText(context, definition.dialogue().selectUnavailableText(context),
                            replacements(context, definition, progress)), replacements(context, definition, progress));
        }
        if (!activeConditionsMet(context, definition)) {
            return result(
                    "inactive",
                    lineId(definition, "inactive"),
                    QuestPresentationService.resolveQuestText(
                            context,
                            definition.dialogue().selectInactiveText(context),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        if (!progress.campaignAlternateEntry() && definition.target().hasStructureTarget() && !progress.visitedTarget()) {
            return result(
                    "missing_target",
                    lineId(definition, "missing_target"),
                    QuestPresentationService.resolveQuestText(
                            context,
                            definition.dialogue().selectMissingTargetText(context),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        if (!progress.campaignAlternateEntry() && !hasRequiredProof(context.player(), definition)) {
            return result(
                    "missing_proof",
                    lineId(definition, "missing_proof"),
                    QuestPresentationService.resolveQuestText(
                            context,
                            definition.dialogue().selectMissingProofText(context),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        if (!requiredObjectivesComplete(context.player(), context, definition, progress)) {
            return result(
                    "missing_objectives",
                    lineId(definition, "missing_objectives"),
                    QuestPresentationService.resolveGlobalText(
                            context.player(),
                            "quest.dialogue.missing_objectives",
                            "There is still more to do before this is ready.",
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        String campaignIssue = CampaignQuestEntries.completionIssue(context.level(), context.player().getUUID(), definition, progress);
        if (!campaignIssue.isBlank()) return result("unavailable", lineId(definition, "unavailable"), campaignIssue, replacements(context, definition, progress));
        ResourceLocation rewardId = progress.campaignAlternateEntry() ? null : definition.rewards().lootTable();
        if (rewardId != null) {
            QuestRewardResolver.Resolution rewardResolution =
                    QuestRewardResolver.resolve(context.level().getServer(), rewardId);
            if (!rewardResolution.resolved()) {
                Map<String, String> rewardReplacements =
                        new LinkedHashMap<>(replacements(context, definition, progress));
                rewardReplacements.put("reward_id", rewardId.toString());
                return result(
                        "reward_unresolved",
                        lineId(definition, "unavailable"),
                        QuestPresentationService.resolveGlobalText(
                                context.player(),
                                "quest.reward_unresolved",
                                "The reward ledger for {reward_id} is unavailable; try again after reloading content.",
                                rewardReplacements),
                        rewardReplacements);
            }
        }
        ItemHandInResult itemHandInResult = handInRequiredObjectiveItems(context, definition, progress);
        if (itemHandInResult != ItemHandInResult.SUCCESS) {
            return result(
                    itemHandInResult.status(),
                    lineId(definition, "missing_objectives"),
                    QuestPresentationService.resolveGlobalText(
                            context.player(),
                            itemHandInResult.messageKey(),
                            itemHandInResult.message(),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }

        progress.markHasProof();
        progress.beginCompletion(progress.campaignAlternateEntry() ? QuestDefinition.Rewards.EMPTY : definition.rewards());
        data.setDirty();
        return finishQuestCompletion(context, definition, progress);
    }

    private static QuestActionOutcome finishQuestCompletion(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        if (!progress.campaignAlternateEntry()) {
            ActionResult reward = awardRewards(context, definition, progress);
            if (!reward.success()) {
                return result("reward_pending", lineId(definition, "unavailable"),
                        "Your submission is saved. Reward delivery is pending: " + reward.message(), replacements(context, definition, progress));
            }
        }
        QuestLifecycleService.complete(
                definition.id(),
                progress,
                context.level().getGameTime(),
                definition.rules().consumeOnCompletion());
        SceneLifecycleIntegration.onQuestTerminal(context.level(), context.player().getUUID(), definition.id(), "completed");
        markQuestLifecycleFact(context.level(), context.player(), definition, QUEST_COMPLETED_FACT, "completed");
        if (!progress.campaignAlternateEntry()) {
            recordScopedCompletion(context, definition);
            lockBranchQuests(context, definition, QuestDefinition.BranchLockEvent.COMPLETED);
        }
        data.setDirty();
        if (!progress.campaignAlternateEntry()) rewardQuestProviderKnowledge(context, definition, progress);
        clearTrackedQuestIf(data, context.player(), definition.id());
        if (!progress.campaignAlternateEntry()) {
            PartyQuestRewardService.completeSharedQuest(context, definition, progress);
            VillagerReputationAdvancements.onQuestCompleted(
                    context.player(), definition, progress.startedVillagerId(), progress.issuerDimension());
        }
        CampaignQuestEntries.syncCompletion(context.player(), definition, progress);
        sendQuestNotification(context, "quest.completed", definition, progress, "Quest completed: {quest}");
        if (!progress.campaignAlternateEntry() && dispatchQuestTriggers(context, definition, progress, QuestDefinition.TriggerEvent.COMPLETED)) {
            data.setDirty();
        }
        sendTrackerSync(context.player(), true);

        return result(
                "completed",
                lineId(definition, "turn_in"),
                QuestPresentationService.resolveQuestText(
                        context,
                        definition.dialogue().selectTurnInText(context),
                        replacements(context, definition, progress)),
                replacements(context, definition, progress));
    }

    private static void rewardQuestProviderKnowledge(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        UUID providerId = progress.startedVillagerId();
        if (providerId == null) return;
        int reward = definition.rules().repeatable()
                ? REPEATABLE_QUEST_PROVIDER_KNOWLEDGE_REWARD
                : ONE_SHOT_QUEST_PROVIDER_KNOWLEDGE_REWARD;
        Villager provider = VillagerEntityResolver.loaded(context.level().getServer(), providerId);
        if (provider != null) {
            VillagerProfileManager.adjustAttribute(context.level(), provider, VillagerSocialAttribute.KNOWLEDGE, reward);
        } else {
            VillagerProfileManager.adjustAttribute(context.level(), providerId, VillagerSocialAttribute.KNOWLEDGE, reward);
        }
    }

    static void onLinkedPartyRewardDelivered(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        markQuestLifecycleFact(context.level(), context.player(), definition, QUEST_COMPLETED_FACT, "completed");
        recordScopedCompletion(context, definition);
        lockBranchQuests(context, definition, QuestDefinition.BranchLockEvent.COMPLETED);
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        clearTrackedQuestIf(data, context.player(), definition.id());
        VillagerReputationAdvancements.onQuestCompleted(
                context.player(), definition, progress.startedVillagerId(), progress.issuerDimension());
        sendQuestNotification(context, "quest.completed", definition, progress, "Quest completed: {quest}");
        dispatchQuestTriggers(context, definition, progress, QuestDefinition.TriggerEvent.COMPLETED);
        data.setDirty();
        sendTrackerSync(context.player(), true);
    }

    static Villager pendingPartyRewardProvider(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        return PartyQuestRewardService.pendingPartyRewardProvider(player, progress);
    }

    private static QuestActionOutcome abandonQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !matchesVillagerLock(context, definition, progress)) {
            return result(
                    "unavailable",
                    lineId(definition, "unavailable"),
                    QuestPresentationService.resolveQuestText(
                            context,
                            definition.dialogue().selectUnavailableText(context),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }

        boolean consume = definition.rules().consumeOnAbandonment()
                || definition.rules().abandonment() == QuestDefinition.AbandonmentMode.REMOVE_FOREVER;
        QuestLifecycleService.abandon(definition.id(), progress, context.level().getGameTime(), consume);
        SceneLifecycleIntegration.onQuestTerminal(context.level(), context.player().getUUID(), definition.id(), "abandoned");
        com.jvn.villagerretaliation.party.PartyService
                .getPartyForPlayer(context.level(), context.player().getUUID())
                .ifPresent(party -> PartyQuestService.detachQuest(
                        context.level(),
                        party,
                        context.player().getUUID(),
                        definition.id()));
        markQuestLifecycleFact(context.level(), context.player(), definition, QUEST_ABANDONED_FACT, "abandoned");
        data.setDirty();
        clearTrackedQuestIf(data, context.player(), definition.id());
        sendQuestNotification(context, "quest.abandoned", definition, progress, "Quest abandoned: {quest}");
        if (dispatchQuestTriggers(context, definition, progress, QuestDefinition.TriggerEvent.ABANDONED)) {
            data.setDirty();
        }
        sendTrackerSync(context.player(), true);
        String status = consume
                ? "abandoned_forever"
                : definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN
                        ? "abandoned_cooldown"
                        : "abandoned";
        return result(
                status,
                lineId(definition, "abandoned"),
                consume
                        ? QuestPresentationService.resolveGlobalText(
                                context.player(),
                                "quest.dialogue.abandoned_forever",
                                "I will close my notes on that journey.",
                                replacements(context, definition, progress))
                        : QuestPresentationService.resolveGlobalText(
                                context.player(),
                                "quest.dialogue.abandoned",
                                "I will fold the map away for now.",
                                replacements(context, definition, progress)),
                replacements(context, definition, progress));
    }

    private static QuestActionOutcome blockQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.COMPLETED) {
            return result(
                    "already_completed",
                    lineId(definition, "already_completed"),
                    QuestPresentationService.resolveQuestText(
                            context,
                            definition.dialogue().selectAlreadyCompletedText(context),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.CONSUMED) {
            String status = branchLocked(progress) ? "branch_locked" : "consumed";
            return result(
                    status,
                    lineId(definition, status),
                    startBlockedLine(context, definition, progress),
                    replacements(context, definition, progress));
        }

        VillagerQuestSavedData.QuestProgress locked =
                progress == null ? data.getOrCreate(context.player().getUUID(), definition.id()) : progress;
        QuestLifecycleService.consume(
                definition.id(),
                locked,
                BRANCH_LOCK_CONSUMED_REASON,
                context.level().getGameTime());
        markQuestBranchLockedFact(context.level(), context.player(), definition, null, null, null);
        data.setDirty();
        clearTrackedQuestIf(data, context.player(), definition.id());
        sendTrackerSync(context.player(), true);
        return result(
                "branch_locked",
                lineId(definition, "branch_locked"),
                QuestPresentationService.resolveGlobalText(
                        context.player(),
                        "quest.dialogue.branch_locked",
                        "That path has closed because of another choice.",
                        replacements(context, definition, locked)),
                replacements(context, definition, locked));
    }

    /**
     * Evaluates every start requirement except quest-pool membership. Pool
     * selection uses this to avoid spending bounded offer slots on quests that
     * cannot be started in the current player/provider context.
     */
    public static boolean canStartIgnoringPools(DialogueContext context, QuestDefinition definition) {
        if (context == null || definition == null) {
            return false;
        }
        VillagerQuestSavedData.QuestProgress progress = VillagerQuestSavedData.get(context.level())
                .get(context.player().getUUID(), definition.id());
        if (!withinActiveCapacity(context, definition, progress)) return false;
        return QuestAvailabilityService.canStart(
                context, definition, progress, false, true,
                VillagerQuestService::parentCompleted,
                VillagerQuestService::scopedCompletionCount);
    }

    private static boolean canStart(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return canStart(context, definition, progress, false);
    }

    private static boolean canStart(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean bypassOfferRequirements) {
        if (!withinActiveCapacity(context, definition, progress)) return false;
        return QuestAvailabilityService.canStart(
                context,
                definition,
                progress,
                bypassOfferRequirements,
                VillagerQuestService::parentCompleted,
                VillagerQuestService::scopedCompletionCount);
    }

    private static boolean canStart(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean bypassOfferRequirements,
            QuestPoolResources.Evaluation poolEvaluation) {
        if (!withinActiveCapacity(context, definition, progress)) return false;
        if (!bypassOfferRequirements && !definition.offer().matches(context)) {
            return false;
        }
        if (!bypassOfferRequirements && !poolEvaluation.allows(definition)) {
            return false;
        }
        return QuestAvailabilityService.canStart(
                context,
                definition,
                progress,
                true,
                true,
                VillagerQuestService::parentCompleted,
                VillagerQuestService::scopedCompletionCount);
    }

    private static boolean withinActiveCapacity(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (context == null || definition == null
                || (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE)) return true;
        List<Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress>> active =
                VillagerQuestSavedData.get(context.level()).activeProgress(context.player().getUUID());
        int totalLimit = definition.rules().maxActiveQuests();
        if (totalLimit > 0 && active.size() >= totalLimit) return false;
        if (definition.rules().maxActiveByTag().isEmpty()) return true;
        Map<String, Integer> activeByTag = new HashMap<>();
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : active) {
            VillagerQuestResources.quest(context.level().getServer(), entry.getKey()).ifPresent(activeQuest ->
                    activeQuest.tags().forEach(tag -> activeByTag.merge(tag, 1, Integer::sum)));
        }
        for (Map.Entry<String, Integer> limit : definition.rules().maxActiveByTag().entrySet()) {
            if (definition.tags().contains(limit.getKey()) && limit.getValue() > 0
                    && activeByTag.getOrDefault(limit.getKey(), 0) >= limit.getValue()) return false;
        }
        return true;
    }

    private static boolean withinStartLimit(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return QuestAvailabilityService.withinStartLimit(definition, progress);
    }

    private static boolean parentCompleted(DialogueContext context, QuestDefinition definition) {
        if (definition.prerequisites().isEmpty()) {
            return true;
        }
        if (context == null) {
            return false;
        }
        for (ResourceLocation prerequisiteId : definition.prerequisites()) {
            QuestDefinition prerequisite = VillagerQuestResources.quest(
                    context.level().getServer(), prerequisiteId).orElse(null);
            if (prerequisite == null) {
                return false;
            }
            VillagerQuestSavedData.QuestProgress prerequisiteProgress =
                    VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), prerequisite.id());
            if (!matchesState(context, prerequisite, prerequisiteProgress, "completed")) {
                return false;
            }
            long completedAt = prerequisiteProgress == null ? -1L : prerequisiteProgress.completedGameTime();
            var campaignCompletion = CampaignQuestAccess.personalCompletion(context.level(), context.player().getUUID(), prerequisite);
            if (campaignCompletion.completed()) completedAt = Math.max(completedAt, campaignCompletion.gameTime());
            if (prerequisiteProgress == null && !campaignCompletion.completed()) return false;
            if (campaignCompletion.completed() && completedAt < 0L && definition.rules().prerequisiteCooldownTicks() > 0L) return false;
            if (!cooldownElapsed(context.level().getGameTime(), completedAt, definition.rules().prerequisiteCooldownTicks())) return false;
        }
        return true;
    }

    private static boolean withinCompletionLimit(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return QuestAvailabilityService.withinCompletionLimit(
                context,
                definition,
                progress,
                VillagerQuestService::scopedCompletionCount);
    }

    private static int scopedCompletionCount(DialogueContext context, QuestDefinition definition) {
        if (context == null || definition == null) {
            return 0;
        }
        if (isPlayerCompletionScope(definition.rules().completionScope())) {
            VillagerQuestSavedData.QuestProgress progress =
                    VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), definition.id());
            return progress == null ? 0 : progress.completionCount();
        }
        String scopeKey = completionScopeKey(context, definition);
        return scopeKey.isBlank()
                ? 0
                : VillagerQuestFacts.get(context.level()).counter(scopeKey, completionCounterKey(definition));
    }

    private static void recordScopedCompletion(DialogueContext context, QuestDefinition definition) {
        if (context == null
                || definition == null
                || isPlayerCompletionScope(definition.rules().completionScope())) {
            return;
        }
        String scopeKey = completionScopeKey(context, definition);
        if (!scopeKey.isBlank()) {
            VillagerQuestFacts.get(context.level()).addCounter(scopeKey, completionCounterKey(definition), 1);
        }
    }

    private static String completionScopeKey(DialogueContext context, QuestDefinition definition) {
        return QuestScopeKey.fromCompletionScope(
                context,
                definition.rules().completionScope(),
                definition.id()).asString();
    }

    private static String completionCounterKey(QuestDefinition definition) {
        return "completion:" + definition.id();
    }

    private static boolean isPlayerCompletionScope(QuestDefinition.CompletionScope scope) {
        return QuestAvailabilityService.isPlayerCompletionScope(scope);
    }

    private static void markQuestLifecycleFact(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition definition,
            ResourceLocation tag,
            String state) {
        String scopeKey = playerQuestScopeKey(player, definition);
        if (scopeKey.isBlank()) {
            return;
        }
        VillagerQuestFacts facts = VillagerQuestFacts.get(level);
        facts.setTag(scopeKey, tag);
        facts.setVariable(scopeKey, "state", state);
        facts.setVariable(scopeKey, "stage", state);
    }

    private static void markQuestChoiceFacts(
            DialogueContext context,
            QuestDefinition definition,
            CompiledQuestTransition transition,
            String priorStage,
            String nextStage) {
        if (context == null || definition == null || transition == null) {
            return;
        }
        String scopeKey = playerQuestScopeKey(context.player(), definition);
        if (scopeKey.isBlank()) {
            return;
        }
        VillagerQuestFacts facts = VillagerQuestFacts.get(context.level());
        facts.setTag(scopeKey, QUEST_BRANCH_SELECTED_FACT);
        facts.setVariable(scopeKey, "last_choice_scene", transition.scenePath());
        facts.setVariable(scopeKey, "last_choice_response", transition.responseId());
        facts.setVariable(scopeKey, "last_choice_prior_stage", priorStage);
        facts.setVariable(scopeKey, "last_choice_next_stage", nextStage);
        facts.setVariable(scopeKey, "last_choice_source", transition.sourcePointer());
        facts.addCounter(scopeKey, "choice_count", 1);
    }

    private static void markQuestObjectiveFact(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition definition,
            QuestDefinition.Objective objective) {
        String scopeKey = playerQuestScopeKey(player, definition);
        if (scopeKey.isBlank()) {
            return;
        }
        VillagerQuestFacts facts = VillagerQuestFacts.get(level);
        facts.setTag(scopeKey, QUEST_OBJECTIVE_COMPLETED_FACT);
        facts.setVariable(scopeKey, "last_objective", objective.id());
        facts.addCounter(scopeKey, "objective_completed:" + objective.id(), 1);
    }

    private static boolean lockBranchQuests(
            DialogueContext context,
            QuestDefinition definition,
            QuestDefinition.BranchLockEvent event) {
        if (context == null || definition == null || event == null) {
            return false;
        }

        QuestDefinition.Branching branching = definition.rules().branching();
        Set<ResourceLocation> questIds = new LinkedHashSet<>(branching.blocksFor(event));
        ResourceLocation exclusiveGroup = branching.exclusiveGroup();
        if (exclusiveGroup != null && branching.exclusiveOn() == event) {
            questIds.addAll(VillagerQuestResources.exclusiveGroupQuestIds(context.level().getServer(), exclusiveGroup));
        }
        questIds.remove(definition.id());

        boolean changed = false;
        for (ResourceLocation questId : questIds) {
            changed |= lockBranchQuest(context, definition, questId, exclusiveGroup, event);
        }
        return changed;
    }

    private static boolean lockBranchQuest(
            DialogueContext context,
            QuestDefinition source,
            ResourceLocation questId,
            ResourceLocation exclusiveGroup,
            QuestDefinition.BranchLockEvent event) {
        if (questId == null || source.id().equals(questId)) {
            return false;
        }

        QuestDefinition target = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        if (target == null) {
            return false;
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), target.id());
        if (progress != null
                && (progress.completionPending() || progress.pendingPartyReward()
                || progress.state() == VillagerQuestSavedData.QuestState.COMPLETED
                || progress.state() == VillagerQuestSavedData.QuestState.CONSUMED)) {
            return false;
        }

        VillagerQuestSavedData.QuestProgress locked =
                progress == null ? data.getOrCreate(context.player().getUUID(), target.id()) : progress;
        QuestLifecycleService.consume(
                target.id(),
                locked,
                BRANCH_LOCK_CONSUMED_REASON,
                context.level().getGameTime());
        clearTrackedQuestIf(data, context.player(), target.id());
        markQuestBranchLockedFact(context.level(), context.player(), target, source, exclusiveGroup, event);
        return true;
    }

    private static void markQuestBranchLockedFact(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition target,
            QuestDefinition source,
            ResourceLocation exclusiveGroup,
            QuestDefinition.BranchLockEvent event) {
        markQuestLifecycleFact(level, player, target, QUEST_BRANCH_LOCKED_FACT, "branch_locked");
        String scopeKey = playerQuestScopeKey(player, target);
        if (scopeKey.isBlank()) {
            return;
        }
        VillagerQuestFacts facts = VillagerQuestFacts.get(level);
        if (source != null) {
            facts.setVariable(scopeKey, "blocked_by", source.id().toString());
        }
        facts.setVariable(scopeKey, "blocked_on", event == null ? "action" : event.serializedName());
        if (exclusiveGroup != null) {
            facts.setVariable(scopeKey, "exclusive_group", exclusiveGroup.toString());
        }
    }

    private static String playerQuestScopeKey(ServerPlayer player, QuestDefinition definition) {
        return player == null || definition == null
                ? ""
                : QuestScopeKey.quest(player.getUUID(), definition.id()).asString();
    }

    private static boolean cooldownElapsed(long gameTime, long eventTime, long cooldownTicks) {
        return QuestAvailabilityService.cooldownElapsed(gameTime, eventTime, cooldownTicks);
    }

    private static boolean matchesVillagerLock(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return QuestAvailabilityService.matchesProviderLock(context, definition, progress);
    }

    private static boolean activeConditionsMet(DialogueContext context, QuestDefinition definition) {
        if (!CampaignQuestAccess.executionIssue(context, definition).isBlank()) return false;
        QuestDefinition.ActiveState activeState = definition.rules().activeState();
        if (!activeState.hasConditions()) {
            return true;
        }
        if (context == null) {
            return false;
        }
        return DialogueCondition.matchesAll(context, activeState.conditions());
    }

    private static ConditionMatch activeConditionsState(
            DialogueContext context,
            ServerPlayer player,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        QuestDefinition.ActiveState activeState = definition.rules().activeState();
        if (!activeState.hasConditions()) {
            return ConditionMatch.MET;
        }
        if (context != null) {
            if (!CampaignQuestAccess.executionIssue(context, definition).isBlank()) return ConditionMatch.UNMET;
            return conditionMatch(com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator.all(
                    context.conditions(), null, activeState.conditions()));
        }
        return conditionsStateWithoutLiveContext(player, level, definition, progress, activeState.conditions());
    }

    private static ConditionMatch activeConditionsStateForPlayer(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (!definition.rules().activeState().hasConditions()) {
            return ConditionMatch.MET;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return ConditionMatch.UNKNOWN;
        }
        DialogueContext context = contextForStartedVillager(level, player, progress).orElse(null);
        return activeConditionsState(context, player, level, definition, progress);
    }

    public static boolean matchesPlayerConditions(DialogueContext context, QuestTriggerContext trigger,
            List<DialogueCondition> conditions) {
        return DialogueCondition.matchesAll(context, trigger, conditions);
    }

    public static DialogueCondition.ConditionEvaluationTrace tracePlayerCondition(DialogueContext context, DialogueCondition condition) {
        return DialogueCondition.trace(context, condition);
    }

    private static ConditionMatch conditionsStateWithoutLiveContext(ServerPlayer player, ServerLevel level,
            QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress, List<DialogueCondition> conditions) {
        return conditionMatch(com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator.all(
                com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess.forSaved(level, player, definition, progress, conditionQueries(player)),
                null, conditions));
    }

    private static ConditionMatch conditionMatch(DialogueCondition.ConditionOutcome outcome) {
        return switch (outcome) {
            case MATCHED -> ConditionMatch.MET;
            case UNMET -> ConditionMatch.UNMET;
            case UNKNOWN -> ConditionMatch.UNKNOWN;
        };
    }

    public static com.jvn.villagerretaliation.dialogue.conditions.QuestConditionQueries conditionQueries(DialogueContext context) {
        return conditionQueries(context.player(), context);
    }

    public static com.jvn.villagerretaliation.dialogue.conditions.QuestConditionQueries conditionQueries(ServerPlayer player) {
        return conditionQueries(player, null);
    }

    private static com.jvn.villagerretaliation.dialogue.conditions.QuestConditionQueries conditionQueries(
            ServerPlayer player, DialogueContext context) {
        return new com.jvn.villagerretaliation.dialogue.conditions.QuestConditionQueries() {
            @Override
            public DialogueCondition.ConditionOutcome matchesState(ResourceLocation questId, Set<String> states) {
                if (context == null) return DialogueCondition.ConditionOutcome.UNKNOWN;
                return VillagerQuestService.matchesState(context, questId, states)
                        ? DialogueCondition.ConditionOutcome.MATCHED : DialogueCondition.ConditionOutcome.UNMET;
            }

            @Override
            public DialogueCondition.ConditionOutcome ready(QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress) {
                return conditionReadyForPlayer(player, definition, progress)
                        ? DialogueCondition.ConditionOutcome.MATCHED : DialogueCondition.ConditionOutcome.UNMET;
            }

            @Override
            public DialogueCondition.ConditionOutcome selectedChoice(DialogueCondition.SelectedChoice condition) {
                return com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess.hasSelectedChoice(
                        player.serverLevel(), player.getUUID(), condition)
                        ? DialogueCondition.ConditionOutcome.MATCHED : DialogueCondition.ConditionOutcome.UNMET;
            }

            @Override
            public DialogueCondition.ConditionOutcome stageHistory(DialogueCondition.StageHistory condition) {
                return com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess.hasStageHistory(
                        player.serverLevel(), player.getUUID(), condition)
                        ? DialogueCondition.ConditionOutcome.MATCHED : DialogueCondition.ConditionOutcome.UNMET;
            }
        };
    }

    public static boolean conditionReadyForPlayer(ServerPlayer player, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return isReadyToTurnIn(DialogueContext.forPlayer(player), definition, progress);
    }

    public static boolean debugEventTagMemoryMatchesWithoutLiveContextForTests(ServerPlayer player, ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress, DialogueCondition.Memory memory) {
        return com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator.evaluate(
                com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess.forSaved(level, player, null, progress),
                null, memory) == DialogueCondition.ConditionOutcome.MATCHED;
    }

    private static ConditionMatch expirationConditionsState(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        QuestDefinition.Expiration expiration = definition.rules().expiration();
        if (expiration.conditions().isEmpty()) {
            return ConditionMatch.UNMET;
        }
        if (context != null) {
            return conditionMatch(com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator.all(
                    context.conditions(), null, expiration.conditions()));
        }
        return conditionsStateWithoutLiveContext(player, level, definition, progress, expiration.conditions());
    }

    private static Optional<DialogueContext> contextForStartedVillager(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData.QuestProgress progress) {
        Villager villager = startedVillager(level, progress);
        if (villager == null || !villager.isAlive()) {
            return progress.startedVillagerId() == null ? Optional.of(DialogueContext.forPlayer(player)) : Optional.empty();
        }
        return Optional.of(VillagerInteractionService.createDialogueContext(level, player, villager));
    }

    private static boolean activeConditionsMetForPlayer(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return CampaignQuestAccess.executionIssue(player.serverLevel(), player.getUUID(), definition, progress).isBlank()
                && activeConditionsStateForPlayer(player, definition, progress) == ConditionMatch.MET;
    }

    private static boolean updateQuestTimers(ServerPlayer player, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, DialogueContext context) {
        boolean changed = false;
        for (var timer : com.jvn.villagerretaliation.quest.runtime.QuestTimers.active(definition, progress)) {
            if (timer.state().warn(timer.definition())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "villagerretaliation.gui.quest_timer.warning", definition.title(), (timer.state().remaining(timer.definition()) + 19) / 20), false);
                changed = true;
            }
            if (timer.state().handled() || timer.definition().mode() != QuestTimer.Mode.DEADLINE
                    || !timer.state().elapsed(timer.definition())) continue;
            if (!timer.definition().timeoutStage().isBlank()) {
                if (context == null) {
                    progress.requestStageTransition(timer.definition().timeoutStage());
                    changed = true;
                    continue;
                }
                if (!STAGES.changeQuestStage(context, definition, progress, timer.definition().timeoutStage(), true, true)) continue;
                timer.state().handle();
                return true;
            }
            timer.state().handle();
            if (context != null) failQuest(context, definition, "timer:" + timer.key());
            else {
                QuestLifecycleService.fail(definition.id(), progress, player.serverLevel().getGameTime(), "timer:" + timer.key());
                SceneLifecycleIntegration.onQuestTerminal(player.serverLevel(), player.getUUID(), definition.id(), "failed");
                markQuestLifecycleFact(player.serverLevel(), player, definition, QUEST_FAILED_FACT, "failed");
                deferLifecycleEvent(player.serverLevel(), definition, progress, QuestDefinition.TriggerEvent.FAILED);
                clearTrackedQuestIf(VillagerQuestSavedData.get(player.serverLevel()), player, definition.id());
                com.jvn.villagerretaliation.party.PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID())
                        .ifPresent(party -> PartyQuestService.detachQuest(player.serverLevel(), party, player.getUUID(), definition.id()));
            }
            return true;
        }
        return changed;
    }

    private static boolean expireQuestIfNeeded(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueContext context) {
        QuestDefinition.Expiration expiration = definition.rules().expiration();
        if (!expiration.enabled()) {
            return false;
        }
        long gameTime = player.level().getGameTime();
        boolean expiredByTime = expiration.afterTicks() > 0L
                && progress.startedGameTime() >= 0L
                && gameTime - progress.startedGameTime() >= expiration.afterTicks();
        ConditionMatch expirationConditions =
                expirationConditionsState(player, context, player.serverLevel(), definition, progress);
        if (!expiredByTime && expirationConditions != ConditionMatch.MET) {
            return false;
        }

        QuestLifecycleService.expire(definition.id(), progress, gameTime, expiration.consume());
        SceneLifecycleIntegration.onQuestTerminal(player.serverLevel(), player.getUUID(), definition.id(), "expired");
        if (player.level() instanceof ServerLevel level) {
            markQuestLifecycleFact(level, player, definition, QUEST_EXPIRED_FACT, "expired");
        }
        if (expiration.sendNotification()) {
            sendQuestExpiredNotification(player, context, definition, progress);
        }
        if (context != null) {
            dispatchQuestTriggers(context, definition, progress, QuestDefinition.TriggerEvent.EXPIRED);
        } else {
            deferLifecycleEvent(
                    player.serverLevel(), definition, progress, QuestDefinition.TriggerEvent.EXPIRED);
        }
        return true;
    }

    private static String startBlockedStatus(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null) {
            if (!withinCompletionLimit(context, definition, null)) {
                return isPlayerCompletionScope(definition.rules().completionScope()) ? "already_completed" : "completion_scope";
            }
            return parentCompleted(context, definition) ? "unavailable" : "parent_locked";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.CONSUMED) {
            return branchLocked(progress) ? "branch_locked" : "consumed";
        }
        if (crossVillagerLocked(context, definition, progress)) {
            return "locked_to_villager";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            return "active";
        }
        if (!parentCompleted(context, definition)) {
            return "parent_locked";
        }
        if (!withinStartLimit(definition, progress)) {
            return "start_limit";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.ABANDONED
                && definition.rules().abandonment() == QuestDefinition.AbandonmentMode.REMOVE_FOREVER) {
            return "abandoned_forever";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.ABANDONED
                && definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN) {
            return "abandoned_cooldown";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.EXPIRED) {
            return "expired";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.FAILED && !definition.rules().repeatable()) {
            return "failed";
        }
        if (completionCooldownActive(context, definition, progress)) {
            return "completion_cooldown";
        }
        if (!withinCompletionLimit(context, definition, progress)) {
            return isPlayerCompletionScope(definition.rules().completionScope()) ? "already_completed" : "completion_scope";
        }
        if (progress.completionCount() > 0 && !definition.rules().repeatable()) {
            return "already_completed";
        }
        return "unavailable";
    }

    private static String startBlockedLine(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (branchLocked(progress)) {
            return QuestPresentationService.resolveGlobalText(
                    context.player(),
                    "quest.dialogue.branch_locked",
                    "That path has closed because of another choice.",
                    replacements(context, definition, progress));
        }
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.CONSUMED) {
            return QuestPresentationService.resolveGlobalText(
                    context.player(),
                    "quest.dialogue.consumed",
                    "That path is no longer available.",
                    replacements(context, definition, progress));
        }
        if (!parentCompleted(context, definition)) {
            return QuestPresentationService.resolveGlobalText(
                    context.player(),
                    "quest.dialogue.parent_locked",
                    "Another chapter needs to be settled before this opens.",
                    replacements(context, definition, progress));
        }
        if (crossVillagerLocked(context, definition, progress)) {
            return QuestPresentationService.resolveGlobalText(
                    context.player(),
                    "quest.dialogue.locked_to_villager",
                    "That quest belongs to {issuer}.",
                    replacements(context, definition, progress));
        }
        if (progress != null && !withinStartLimit(definition, progress)) {
            return QuestPresentationService.resolveGlobalText(
                    context.player(),
                    "quest.dialogue.start_limit",
                    "That path cannot be started again.",
                    replacements(context, definition, progress));
        }
        if (progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ABANDONED
                && definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN
                && !cooldownElapsed(context.level().getGameTime(), progress.abandonedGameTime(), definition.rules().abandonmentCooldownTicks())) {
            return QuestPresentationService.resolveGlobalText(
                    context.player(),
                    "quest.dialogue.abandoned_cooldown",
                    "Give that path a little time, then return to {issuer}.",
                    replacements(context, definition, progress));
        }
        if (completionCooldownActive(context, definition, progress)) {
            return QuestPresentationService.resolveGlobalText(
                    context.player(),
                    "quest.dialogue.completion_cooldown",
                    "That matter needs time before it can be revisited.",
                    replacements(context, definition, progress));
        }
        if (!withinCompletionLimit(context, definition, progress)
                && !isPlayerCompletionScope(definition.rules().completionScope())) {
            return QuestPresentationService.resolveGlobalText(
                    context.player(),
                    "quest.dialogue.completion_scope",
                    "Someone has already settled this matter.",
                    replacements(context, definition, progress));
        }
        if ((progress != null && progress.completionCount() > 0)
                || (!isPlayerCompletionScope(definition.rules().completionScope())
                && scopedCompletionCount(context, definition) > 0)
                || !withinCompletionLimit(context, definition, progress)) {
            return QuestPresentationService.resolveQuestText(
                    context,
                    definition.dialogue().selectAlreadyCompletedText(context),
                    replacements(context, definition, progress));
        }
        return QuestPresentationService.resolveQuestText(
                context,
                definition.dialogue().selectUnavailableText(context),
                replacements(context, definition, progress));
    }

    private static boolean crossVillagerLocked(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return context != null
                && progress != null
                && progress.startedVillagerId() != null
                && !definition.rules().crossVillagerCompatible()
                && !progress.startedVillagerId().equals(context.providerId());
    }

    private static boolean completionCooldownActive(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return context != null
                && progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.COMPLETED
                && definition.rules().completionCooldownTicks() > 0L
                && !cooldownElapsed(
                        context.level().getGameTime(),
                        progress.completedGameTime(),
                        definition.rules().completionCooldownTicks());
    }

    private static boolean matchesState(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String state) {
        String normalized = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
        boolean campaignCompleted = definition.campaign() != null && context != null && context.player() != null && CampaignQuestAccess.personalCompletion(
                context.level(), context.player().getUUID(), definition).completed();
        boolean completed = campaignCompleted || progress != null && progress.completionCount() > 0;
        if (!completed && !isPlayerCompletionScope(definition.rules().completionScope())) {
            completed = scopedCompletionCount(context, definition) > 0;
        }
        boolean rawActive = progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && matchesVillagerLock(context, definition, progress);
        boolean activeConditionsMet = rawActive && activeConditionsMet(context, definition);
        boolean active = rawActive && (activeConditionsMet || !definition.rules().activeState().hideWhenUnmet());
        boolean ready = activeConditionsMet && isReadyToTurnIn(context, definition, progress);
        boolean notStarted = !campaignCompleted && (progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED);
        boolean abandoned = progress != null && progress.state() == VillagerQuestSavedData.QuestState.ABANDONED;
        boolean failed = progress != null && progress.state() == VillagerQuestSavedData.QuestState.FAILED;
        boolean expired = progress != null && progress.state() == VillagerQuestSavedData.QuestState.EXPIRED;
        boolean consumed = progress != null && progress.state() == VillagerQuestSavedData.QuestState.CONSUMED;
        boolean branchLocked = branchLocked(progress);
        return switch (normalized) {
            case "available" -> canStart(context, definition, progress);
            case "not_started", "locked" -> notStarted;
            case "active", "started" -> active;
            case "active_visible", "active_available", "active_conditions_met" -> activeConditionsMet;
            case "active_hidden", "active_unavailable", "inactive", "paused", "active_conditions_unmet" -> rawActive && !activeConditionsMet;
            case "in_progress", "incomplete" -> activeConditionsMet && !ready;
            case "ready", "turn_in", "turnin", "completeable", "completable" -> ready;
            case "completed", "complete" -> completed;
            case "abandoned", "dropped" -> abandoned;
            case "failed", "failure" -> failed;
            case "expired", "timed_out", "time_out" -> expired;
            case "consumed", "removed", "removed_forever" -> consumed;
            case "branch_locked", "branch_blocked", "blocked_branch" -> branchLocked;
            case "unavailable" -> !canStart(context, definition, progress) && !active;
            case "not_completed" -> !completed;
            default -> false;
        };
    }

    private static boolean branchLocked(VillagerQuestSavedData.QuestProgress progress) {
        return progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.CONSUMED
                && BRANCH_LOCK_CONSUMED_REASON.equals(progress.consumedReason());
    }

    private static boolean isReadyToTurnIn(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress != null && progress.completionPending()) return true;
        return progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && activeConditionsMet(context, definition)
                && (progress.campaignAlternateEntry() || !definition.target().hasStructureTarget() || progress.visitedTarget())
                && (progress.campaignAlternateEntry() || hasRequiredProof(context.player(), definition))
                && CampaignQuestEntries.completionIssue(context.level(), context.player().getUUID(), definition, progress).isBlank()
                && requiredObjectivesComplete(context.player(), context, definition, progress);
    }

    private static boolean trackerReadyToTurnIn(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress != null && progress.completionPending()) return true;
        if (player == null
                || progress == null
                || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                || (!progress.campaignAlternateEntry() && definition.target().hasStructureTarget() && !progress.visitedTarget())
                || (!progress.campaignAlternateEntry() && !hasRequiredProof(player, definition))
                || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!CampaignQuestEntries.completionIssue(level, player.getUUID(), definition, progress).isBlank()) return false;
        List<QuestDefinition.Objective> objectives = requiredObjectivesForReadiness(definition, progress);
        List<QuestDefinition.Objective> requiredItemHandIns = objectives.stream()
                .filter(QuestObjectiveRegistry::requiresItemHandIn)
                .filter(objective -> !com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.forced(player, definition.id(), progress.currentStage(), objective.id()))
                .toList();
        for (QuestDefinition.Objective objective : objectives) {
            if (QuestObjectiveRegistry.requiresItemHandIn(objective)) {
                continue;
            }
            if (!objectiveComplete(player, context, level, definition, progress, objective)) {
                return false;
            }
        }
        return requiredItemHandIns.isEmpty()
                || previewObjectiveItemStacks(player, requiredItemHandIns, progress).isPresent();
    }

    private static boolean hasRequiredProof(ServerPlayer player, QuestDefinition definition) {
        if (!definition.target().hasProofItem()) {
            return true;
        }
        return itemCount(
                        player,
                        definition.target().proofItem(),
                        definition.target().proofItemPredicate())
                >= 1;
    }

    private static boolean hasItemCount(ServerPlayer player, ResourceLocation itemId, int count) {
        return itemCount(player, itemId) >= Math.max(1, count);
    }

    private static int itemCount(ServerPlayer player, ResourceLocation itemId) {
        return QuestInventoryService.itemCount(player, itemId);
    }

    private static int itemCount(
            ServerPlayer player,
            ResourceLocation itemId,
            ItemStackPredicate stackPredicate) {
        return QuestInventoryService.itemCount(player, itemId, stackPredicate);
    }

    private static int itemCount(
            ServerPlayer player,
            QuestDefinition.Objective objective,
            VillagerQuestSavedData.QuestProgress progress) {
        return QuestInventoryService.itemCount(
                player, QuestObjectiveQuery.resolvedItem(objective, progress), objective.itemRequirements());
    }

    private static boolean updateObjectiveProgress(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueContext context) {
        if (QuestObjectiveQuery.activeObjectives(definition, progress).isEmpty()) {
            return false;
        }

        boolean changed = QuestObjectiveDispatchService.poll(
                objectiveEvaluationContext(player, context, level, definition, progress),
                objective -> markQuestObjectiveFact(level, player, definition, objective));
        /*
         * Do not locate generated structures from player tick. Vanilla nearest/generated-structure
         * searches can synchronously enter worldgen/StructureCheck while players are exploring.
         */
        return changed;
    }

    /**
     * Publishes a namespaced gameplay criterion to active data-driven quests.
     * Extra data is matched exactly against an objective's {@code match} object.
     */
    public static void onCriterion(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation criterion,
            Map<String, String> data,
            ItemStack item,
            LivingEntity entity) {
        if (criterion == null) {
            return;
        }
        onObjectiveEvent(level, player, QuestObjectiveEvent.criterion(criterion, data, item, entity));
    }

    public static void onCriterion(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation criterion,
            Map<String, String> data) {
        onCriterion(level, player, criterion, data, ItemStack.EMPTY, null);
    }

    private static void onObjectiveEvent(ServerLevel level, ServerPlayer player, QuestObjectiveEvent event) {
        onObjectiveEvent(level, player, event, Set.of());
    }

    private static QuestActionOutcome failQuest(
            DialogueContext context,
            QuestDefinition definition,
            String reason) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            return result("unavailable", lineId(definition, "unavailable"), "That quest is not active.",
                    replacements(context, definition, progress));
        }

        QuestLifecycleService.fail(definition.id(), progress, context.level().getGameTime(), reason);
        SceneLifecycleIntegration.onQuestTerminal(context.level(), context.player().getUUID(), definition.id(), "failed");
        com.jvn.villagerretaliation.party.PartyService
                .getPartyForPlayer(context.level(), context.player().getUUID())
                .ifPresent(party -> PartyQuestService.detachQuest(
                        context.level(), party, context.player().getUUID(), definition.id()));
        markQuestLifecycleFact(context.level(), context.player(), definition, QUEST_FAILED_FACT, "failed");
        data.setDirty();
        clearTrackedQuestIf(data, context.player(), definition.id());
        sendQuestNotification(context, "quest.failed", definition, progress, "Quest failed: {quest}");
        if (dispatchQuestTriggers(context, definition, progress, QuestDefinition.TriggerEvent.FAILED)) {
            data.setDirty();
        }
        sendTrackerSync(context.player(), true);
        return result(
                "failed",
                lineId(definition, "failed"),
                QuestPresentationService.resolveGlobalText(context.player(), "quest.dialogue.failed", "That journey has failed.",
                        replacements(context, definition, progress)),
                replacements(context, definition, progress));
    }

    private static void onObjectiveEvent(
            ServerLevel level,
            ServerPlayer player,
            QuestObjectiveEvent event,
            Set<ResourceLocation> excludedQuestIds) {
        if (level == null || player == null || event == null || event.kind() == null || player.level() != level) {
            return;
        }

        com.jvn.villagerretaliation.quest.discovery.QuestDiscoveryService.event(player, event);
        var content = com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.current(level.getServer());
        QuestDebugTraceService.recordIfEnabled(player, QuestDebugTraceService.EventType.OBJECTIVE_EVENT, null,
                "event=" + event.kind().name().toLowerCase(Locale.ROOT)
                        + " candidate_quests=" + com.jvn.villagerretaliation.quest.runtime.QuestObservationDispatcher.candidates(content, event).size());
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        var result = com.jvn.villagerretaliation.quest.runtime.QuestObservationDispatcher.dispatch(
                content, data, player.getUUID(), event, excludedQuestIds,
                new com.jvn.villagerretaliation.quest.runtime.QuestObservationDispatcher.Access<VillagerQuestSavedData.QuestProgress>() {
                    public boolean active(QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress) {
                        return activeConditionsMetForPlayer(player, definition, progress);
                    }

                    public boolean objectives(QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress,
                            QuestObjectiveEvent observation) {
                        return QuestObjectiveDispatchService.dispatch(
                                objectiveEvaluationContext(player, null, level, definition, progress), observation,
                                objective -> markQuestObjectiveFact(level, player, definition, objective));
                    }

                    public boolean triggers(QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress,
                            QuestDefinition.TriggerEvent trigger, QuestObjectiveEvent observation) {
                        return observation == null ? dispatchQuestTriggers(player, definition, progress, trigger)
                                : dispatchQuestTriggers(player, definition, progress, trigger, observation);
                    }

                    public void advance(QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress) {
                        advanceStageAfterEvent(level, player, definition, progress);
                    }

                    public void updated(QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress) {
                        sendQuestProgressNotification(player, definition, progress, "quest.updated", "Quest updated: {quest}");
                    }
                });
        if (result.changed()) {
            data.changed();
            sendTrackerSync(player, result.progressNotice());
        }
    }

    private static boolean requiredObjectivesComplete(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        List<QuestDefinition.Objective> objectives = requiredObjectivesForReadiness(definition, progress);
        List<QuestDefinition.Objective> requiredItemHandIns = objectives.stream()
                .filter(QuestObjectiveRegistry::requiresItemHandIn)
                .filter(objective -> !com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.forced(player, definition.id(), progress.currentStage(), objective.id()))
                .toList();
        for (QuestDefinition.Objective objective : objectives) {
            if (QuestObjectiveRegistry.requiresItemHandIn(objective)) {
                continue;
            }
            if (!objectiveComplete(player, context, context.level(), definition, progress, objective)) {
                return false;
            }
        }
        if (requiredItemHandIns.isEmpty()) {
            return true;
        }
        Optional<com.jvn.villagerretaliation.party.PartySharedQuestRecord> shared =
                PartyQuestService.sharedForPlayer(context.level(), player.getUUID(), definition.id());
        if (shared.isPresent()) {
            return PartyQuestService.planSharedItemSubmission(
                    player,
                    shared.get(),
                    requiredItemHandIns,
                    (objective, stack) -> matchesObjectiveItemStack(objective, stack, progress)).isPresent();
        }
        return previewObjectiveItemStacks(player, requiredItemHandIns, progress).isPresent();
    }

    private static List<QuestDefinition.Objective> requiredObjectivesForReadiness(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (definition == null) {
            return List.of();
        }
        Map<String, QuestDefinition.Objective> objectives = new LinkedHashMap<>();
        for (QuestDefinition.Objective objective : QuestObjectiveQuery.requiredObjectives(definition, progress)) {
            objectives.put(objective.id(), objective);
        }
        if (progress != null) {
            for (QuestDefinition.Objective objective : QuestObjectiveQuery.requiredObjectives(definition)) {
                if (objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK
                        && progress.objectiveComplete(objective.id())) {
                    objectives.putIfAbsent(objective.id(), objective);
                }
            }
            for (QuestDefinition.Objective objective : priorStageItemObjectivesForReadiness(definition, progress)) {
                objectives.putIfAbsent(objective.id(), objective);
            }
        }
        return List.copyOf(objectives.values());
    }

    private static List<QuestDefinition.Objective> priorStageItemObjectivesForReadiness(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (definition == null || progress == null || definition.stages().isEmpty()) {
            return List.of();
        }
        String currentStage = progress.currentStage();
        if (currentStage.isBlank() || currentStage.equals(progress.campaignEntryStage())) {
            return List.of();
        }
        Map<String, QuestDefinition.Objective> byId = definition.objectives().stream()
                .collect(java.util.stream.Collectors.toMap(
                        QuestDefinition.Objective::id,
                        objective -> objective,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        Map<String, QuestDefinition.Objective> carried = new LinkedHashMap<>();
        for (QuestDefinition.Stage stage : definition.stages().values()) {
            if (!currentStage.equals(stage.next())) {
                continue;
            }
            for (String objectiveId : stage.objectives()) {
                QuestDefinition.Objective objective = byId.get(objectiveId);
                if (objective != null && objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK && !objective.optional()) {
                    carried.putIfAbsent(objective.id(), objective);
                }
            }
        }
        return List.copyOf(carried.values());
    }

    private static boolean objectiveComplete(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        if (com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.forced(player, definition.id(), progress.currentStage(), objective.id())) return true;
        if (objective.type() != QuestDefinition.ObjectiveType.ITEM_CHECK
                && progress.objectiveComplete(objective.id())) {
            return true;
        }
        Optional<QuestObjectiveResult> registryResult = QuestObjectiveRegistry.evaluate(
                objectiveEvaluationContext(player, context, level, definition, progress),
                objective);
        if (registryResult.isPresent()) {
            return registryResult.get().complete();
        }
        return false;
    }

    private static QuestObjectiveEvaluationContext objectiveEvaluationContext(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && ensureRandomItemSelections(definition, progress)) {
            VillagerQuestSavedData.get(level).setDirty();
        }
        return new QuestObjectiveEvaluationContext(
                player,
                context,
                level,
                definition,
                progress,
                objective -> itemCount(player, objective, progress),
                (objective, stack) -> matchesObjectiveItemStack(objective, stack, progress),
                objective -> reputationForObjective(level, player, progress),
                objective -> matchesFactObjective(level, player, definition, progress, objective),
                objective -> objectiveConditionState(player, context, level, definition, progress, objective) == ConditionMatch.MET,
                objective -> objectiveDebugState(player, context, level, definition, progress, objective));
    }

    private static QuestObjectiveDebugState objectiveDebugState(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        String scopeKey = "";
        if (progress != null
                && (objective.type() == QuestDefinition.ObjectiveType.CHOICE
                || objective.type() == QuestDefinition.ObjectiveType.FACT)) {
            scopeKey = blankAs(factObjectiveScopeKey(level, player, definition, progress, objective), "unresolved");
        }
        String conditionState = objective.type() == QuestDefinition.ObjectiveType.CONDITION
                ? debugEnum(objectiveConditionState(player, context, level, definition, progress, objective))
                : "";
        return new QuestObjectiveDebugState(null, "", scopeKey, conditionState);
    }

    private static ConditionMatch objectiveConditionState(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        if (context != null) {
            return conditionMatch(com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator.all(
                    context.conditions(), null, objective.conditions()));
        }
        return conditionsStateWithoutLiveContext(player, level, definition, progress, objective.conditions());
    }

    private static int reputationForObjective(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData.QuestProgress progress) {
        if (level == null || player == null || progress == null || progress.startedVillagerId() == null) {
            return 0;
        }
        VillagerReputationSavedData.ReputationEntry entry = VillagerReputationSavedData.get(level)
                .get(progress.startedVillagerId(), player.getUUID());
        return entry == null ? 0 : entry.reputation();
    }

    private static boolean matchesFactObjective(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        String scopeKey = factObjectiveScopeKey(level, player, definition, progress, objective);
        if (scopeKey.isBlank()) {
            return false;
        }
        VillagerQuestFacts facts = VillagerQuestFacts.get(level);
        if (!objective.factTags().isEmpty()
                && objective.factTags().stream().noneMatch(tag -> facts.hasTag(scopeKey, tag))) {
            return false;
        }
        String key = objective.factKey();
        if (key == null || key.isBlank()) {
            return !objective.factTags().isEmpty();
        }
        Optional<String> variable = facts.variable(scopeKey, key);
        if (!objective.factValues().isEmpty() && variable.stream().noneMatch(objective.factValues()::contains)) {
            return false;
        }
        int counter = facts.counter(scopeKey, key);
        if (objective.factMin() != null && counter < objective.factMin()) {
            return false;
        }
        if (objective.factMax() != null && counter > objective.factMax()) {
            return false;
        }
        return !objective.factValues().isEmpty()
                || objective.factMin() != null
                || objective.factMax() != null
                || variable.isPresent()
                || counter != 0;
    }

    private static String factObjectiveScopeKey(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        QuestFactScope scope = objective.factScope();
        ResourceLocation questId = objective.factQuestId() == null ? definition.id() : objective.factQuestId();
        QuestScopeKey scopeKey = switch (scope) {
            case PLAYER -> QuestScopeKey.player(player.getUUID());
            case WORLD -> QuestScopeKey.WORLD;
            case QUEST -> QuestScopeKey.quest(player.getUUID(), questId);
            case VILLAGER -> QuestScopeKey.villager(progress.startedVillagerId());
            case VILLAGE -> QuestScopeKey.village(factVillageScopeKey(level, progress));
        };
        return scopeKey.asString();
    }

    private static String factVillageScopeKey(ServerLevel level, VillagerQuestSavedData.QuestProgress progress) {
        if (!progress.issuerVillageKey().isBlank()) {
            return progress.issuerVillageKey();
        }
        Villager villager = startedVillager(level, progress);
        if (villager != null && villager.isAlive()) {
            return VillageScopeKeys.forVillager(level, villager);
        }
        if (progress.issuerPos() == null) {
            return "";
        }
        ResourceKey<Level> issuerDimension = progress.issuerDimension() == null
                ? level.dimension()
                : progress.issuerDimension();
        return VillageScopeKeys.forPosition(issuerDimension, progress.issuerPos());
    }

    private static Optional<QuestDefinition.Objective> firstIncompleteRequiredObjective(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return QuestObjectiveQuery.firstIncompleteRequired(
                definition,
                progress,
                objective -> objectiveComplete(player, context, level, definition, progress, objective));
    }

    private static ItemHandInResult handInRequiredObjectiveItems(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        List<QuestDefinition.Objective> requiredItemHandIns = requiredObjectivesForReadiness(definition, progress).stream()
                .filter(QuestObjectiveRegistry::requiresItemHandIn)
                .filter(objective -> !com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.forced(context.player(), definition.id(), progress.currentStage(), objective.id()))
                .toList();
        if (requiredItemHandIns.isEmpty()) {
            return ItemHandInResult.SUCCESS;
        }
        Optional<com.jvn.villagerretaliation.party.PartySharedQuestRecord> shared =
                PartyQuestService.sharedForPlayer(context.level(), context.player().getUUID(), definition.id());
        ItemHandInResult result = QuestInventoryService.handInRequiredObjectiveItems(
                context.player(),
                context.villager(),
                requiredItemHandIns,
                objective -> QuestObjectiveQuery.resolvedItem(objective, progress),
                shared.orElse(null));
        if (result == ItemHandInResult.SUCCESS && shared.isPresent()) {
            for (QuestDefinition.Objective objective : requiredItemHandIns) {
                shared.get().mergeObjectiveCounter(objective.id(), objective.count());
                shared.get().markObjectiveComplete(objective.id());
            }
            com.jvn.villagerretaliation.party.PartyRecord party =
                    com.jvn.villagerretaliation.party.PartyService
                            .getPartyForPlayer(context.level(), context.player().getUUID())
                            .orElse(null);
            if (party != null) {
                syncSharedQuestAfterEvent(context.level(), shared.get(), definition);
                com.jvn.villagerretaliation.party.PartyService.markChanged(context.level());
            }
        }
        return result;
    }

    private static Optional<List<ItemStack>> previewObjectiveItemStacks(
            ServerPlayer player,
            List<QuestDefinition.Objective> requiredObjectives,
            VillagerQuestSavedData.QuestProgress progress) {
        return QuestInventoryService.previewObjectiveItemStacks(
                player, requiredObjectives, objective -> QuestObjectiveQuery.resolvedItem(objective, progress));
    }

    private static boolean matchesObjectiveItemStack(
            QuestDefinition.Objective objective,
            ItemStack stack,
            VillagerQuestSavedData.QuestProgress progress) {
        return QuestInventoryService.matchesObjectiveItemStack(
                objective, stack, QuestObjectiveQuery.resolvedItem(objective, progress));
    }

    static String unresolvedQuestRewardDiagnostic(
            MinecraftServer server, QuestDefinition definition) {
        return QuestRewardService.unresolvedDiagnostic(server, definition);
    }

    static ActionResult awardRewards(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return QuestRewardService.award(context, definition, progress, replacements(context, definition, progress));
    }

    private static void rememberQuestStoryHint(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestTargets.LocatedTarget target) {
        ResourceLocation structure = VillagerQuestTargets.targetStructure(definition, target.objectiveId());
        if (structure == null || context.villager() == null) {
            return;
        }
        VillagerInteractionTracker.rememberStoryHint(
                context.level(),
                context.villager(),
                context.player(),
                VillagerInteractionTracker.StoryHintKind.STRUCTURE,
                structure,
                targetDisplayName(context, definition, target),
                target.pos(),
                target.dimension().location(),
                context.level().getGameTime() + QUEST_STORY_HINT_TICKS,
                targetDiscoveryRadius(definition, target)
        );
    }

    private static void maybeGiveQuestTargetMap(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestTargets.LocatedTarget target) {
        ResourceLocation structure = VillagerQuestTargets.targetStructure(definition, target.objectiveId());
        if (structure == null) {
            return;
        }
        VillagerStoryHintService.maybeGiveQuestTargetMap(
                context,
                structure,
                target.dimension(),
                target.pos(),
                targetDisplayName(context, definition, target));
    }

    private static String targetDisplayName(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestTargets.LocatedTarget target) {
        ResourceLocation structure = VillagerQuestTargets.targetStructure(definition, target.objectiveId());
        if (structure == null) {
            return QuestPresentationService.targetName(definition);
        }
        return target.objectiveId().isBlank()
                ? QuestPresentationService.targetName(definition)
                : structureDisplayName(context, structure);
    }

    private static String structureDisplayName(DialogueContext context, ResourceLocation structure) {
        if (context != null && structure != null) {
            Optional<String> configuredName = DangerousStructureStoryResources.entries(context.level().getServer()).stream()
                    .filter(entry -> VillagerWorldTargetCache.sameStructureId(entry.structureId(), structure))
                    .findFirst()
                    .map(DangerousStructureStoryResources.Entry::targetName);
            if (configuredName.isPresent()) {
                return configuredName.get();
            }
        }
        return VillagerInteractionTextUtil.resourcePathName(structure);
    }

    private static int targetDiscoveryRadius(
            QuestDefinition definition,
            VillagerQuestTargets.LocatedTarget target) {
        if (target.objectiveId().isBlank()) {
            return definition.target().discoveryRadius();
        }
        return definition.objectives().stream()
                .filter(objective -> objective.id().equals(target.objectiveId()))
                .findFirst()
                .map(QuestDefinition.Objective::discoveryRadius)
                .orElse(definition.target().discoveryRadius());
    }

    private static boolean dispatchQuestTriggers(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.TriggerEvent event) {
        return dispatchQuestTriggers(player, definition, progress, event, null);
    }

    private static boolean dispatchQuestTriggers(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.TriggerEvent event,
            QuestObjectiveEvent objectiveEvent) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        CompiledQuest compiled = VillagerQuestResources.compiledQuest(level.getServer(), definition.id()).orElse(null);
        if (compiled == null || !compiled.triggerIndex().hasEvent(event)) {
            return false;
        }
        Villager villager = startedVillager(level, progress);
        if ((villager == null || !villager.isAlive()) && progress.startedVillagerId() != null) {
            if (objectiveEvent != null) {
                return progress.deferTriggerEvent(QuestTriggerContext.of(
                        null, event, level.getGameTime(), progress.currentStage(), objectiveEvent));
            }
            return false;
        }
        DialogueContext context = villager == null ? DialogueContext.forPlayer(player)
                : VillagerInteractionService.createDialogueContext(level, player, villager);
        QuestTriggerContext triggerContext = QuestTriggerContext.of(
                context, event, level.getGameTime(), progress.currentStage(), objectiveEvent);
        return dispatchQuestTriggers(triggerContext, compiled, progress);
    }

    private static boolean deferLifecycleEvent(
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.TriggerEvent event) {
        if (level == null || definition == null || progress == null || event == null) {
            return false;
        }
        CompiledQuest compiled = VillagerQuestResources.compiledQuest(level.getServer(), definition.id()).orElse(null);
        return compiled != null
                && compiled.triggerIndex().hasEvent(event)
                && progress.deferLifecycleEvent(event);
    }

    private static boolean resumePendingLifecycleEvents(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Villager provider = startedVillager(level, progress);
        if ((provider == null || !provider.isAlive()) && progress.startedVillagerId() != null) {
            return false;
        }
        CompiledQuest compiled = VillagerQuestResources.compiledQuest(level.getServer(), definition.id()).orElse(null);
        if (compiled == null) {
            return false;
        }
        DialogueContext context = provider == null ? DialogueContext.forPlayer(player)
                : VillagerInteractionService.createDialogueContext(level, player, provider);
        return resumePendingLifecycleEvents(context, compiled, progress);
    }

    private static boolean resumePendingLifecycleEvents(
            DialogueContext context,
            CompiledQuest compiled,
            VillagerQuestSavedData.QuestProgress progress) {
        if (context == null || compiled == null || progress == null || !progress.hasPendingLifecycleEvents()
                || !CampaignQuestAccess.executionIssue(context.level(), context.player().getUUID(), compiled.asQuestDefinition(), progress).isBlank()) {
            return false;
        }
        boolean resolved = false;
        for (QuestDefinition.TriggerEvent event : List.copyOf(progress.pendingLifecycleEvents())) {
            if (event == QuestDefinition.TriggerEvent.STARTED) {
                if (progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
                    resolved |= progress.resolveLifecycleEvent(event);
                    continue;
                }
                if (!progress.pendingStageEntry().isBlank() || !QuestStageActionService.ready(context, progress)) continue;
            }
            boolean stillAuthored = compiled.triggerIndex().hasEvent(event);
            if (stillAuthored) {
                dispatchQuestTriggers(context, compiled, progress, event);
            }
            if (progress.resolveLifecycleEvent(event)) {
                resolved = true;
                QuestDebugTraceService.recordIfEnabled(
                        context.player(), QuestDebugTraceService.EventType.TRIGGER, compiled.id(),
                        "resume deferred_event=" + QuestTriggerRegistry.canonicalEventId(event)
                                + " result=" + (stillAuthored ? "dispatched" : "removed_no_longer_authored"));
            }
        }
        return resolved;
    }

    private static boolean dispatchQuestTriggers(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.TriggerEvent event) {
        if (context == null || definition == null) {
            return false;
        }
        CompiledQuest compiled = VillagerQuestResources.compiledQuest(context.level().getServer(), definition.id()).orElse(null);
        return dispatchQuestTriggers(context, compiled, progress, event);
    }

    private static boolean dispatchQuestTriggers(
            DialogueContext context,
            CompiledQuest compiled,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.TriggerEvent event) {
        if (progress == null || compiled == null || !compiled.triggerIndex().hasEvent(event)) {
            return false;
        }
        QuestTriggerDispatchResult result = QuestTriggerDispatcher.dispatch(
                context,
                compiled,
                progress,
                event,
                VillagerQuestService::runQuestTriggerActions);
        if (context != null) {
            QuestDebugTraceService.recordIfEnabled(context.player(), QuestDebugTraceService.EventType.TRIGGER, compiled.id(),
                    "dispatch event=" + QuestTriggerRegistry.canonicalEventId(event)
                            + " candidates=" + result.trace().candidateTriggers()
                            + " evaluated=" + result.trace().evaluatedTriggers()
                            + " matched=" + result.trace().matchedTriggers()
                            + " ran=" + result.trace().ranTriggers()
                            + " dirty=" + result.dirty());
        }
        return result.dirty();
    }

    private static boolean resumePendingTriggerWork(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Villager provider = startedVillager(level, progress);
        if ((provider == null || !provider.isAlive()) && progress.startedVillagerId() != null) {
            return false;
        }
        CompiledQuest compiled = VillagerQuestResources.compiledQuest(level.getServer(), definition.id()).orElse(null);
        if (compiled == null) {
            return false;
        }
        DialogueContext context = provider == null ? DialogueContext.forPlayer(player)
                : VillagerInteractionService.createDialogueContext(level, player, provider);
        return resumePendingTriggerWork(context, compiled, progress);
    }

    private static boolean resumePendingTriggerWork(
            DialogueContext context,
            CompiledQuest compiled,
            VillagerQuestSavedData.QuestProgress progress) {
        if (!CampaignQuestAccess.executionIssue(context.level(), context.player().getUUID(), compiled.asQuestDefinition(), progress).isBlank()) return false;
        boolean changed = resumePendingLifecycleEvents(context, compiled, progress);
        for (QuestTriggerContext pending : List.copyOf(progress.pendingTriggerEvents())) {
            boolean stillAuthored = compiled.triggerIndex().hasEvent(pending.event());
            if (stillAuthored) {
                dispatchQuestTriggers(pending.withDialogueContext(context), compiled, progress);
            }
            if (progress.resolveTriggerEvent(pending)) {
                changed = true;
                QuestDebugTraceService.recordIfEnabled(
                        context.player(), QuestDebugTraceService.EventType.TRIGGER, compiled.id(),
                        "resume deferred_payload_event=" + QuestTriggerRegistry.canonicalEventId(pending.event())
                                + " stage=" + pending.stage()
                                + " payload=" + pending.payload().keySet()
                                + " result=" + (stillAuthored ? "dispatched" : "removed_no_longer_authored"));
            }
        }
        return changed;
    }

    private static boolean dispatchQuestTriggers(
            QuestTriggerContext triggerContext,
            CompiledQuest compiled,
            VillagerQuestSavedData.QuestProgress progress) {
        if (triggerContext == null || progress == null || compiled == null
                || !compiled.triggerIndex().hasEvent(triggerContext.event())) {
            return false;
        }
        QuestTriggerDispatchResult result = QuestTriggerDispatcher.dispatch(
                triggerContext, compiled, progress, VillagerQuestService::runQuestTriggerActions);
        DialogueContext context = triggerContext.dialogueContext();
        if (context != null) {
            QuestDebugTraceService.recordIfEnabled(context.player(), QuestDebugTraceService.EventType.TRIGGER, compiled.id(),
                    "dispatch event=" + QuestTriggerRegistry.canonicalEventId(triggerContext.event())
                            + " stage=" + triggerContext.stage()
                            + " payload=" + triggerContext.payload().keySet()
                            + " candidates=" + result.trace().candidateTriggers()
                            + " evaluated=" + result.trace().evaluatedTriggers()
                            + " matched=" + result.trace().matchedTriggers()
                            + " ran=" + result.trace().ranTriggers()
                            + " dirty=" + result.dirty());
        }
        return result.dirty();
    }

    private static boolean dispatchStageChangedTriggers(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (DISPATCHING_STAGE_TRIGGERS.get()) {
            return false;
        }
        DISPATCHING_STAGE_TRIGGERS.set(true);
        try {
            return dispatchQuestTriggers(context, definition, progress, QuestDefinition.TriggerEvent.STAGE_CHANGED);
        } finally {
            DISPATCHING_STAGE_TRIGGERS.set(false);
        }
    }

    private static void markContinuousTriggersUsed(
            VillagerQuestSavedData.QuestProgress progress,
            DialogueContext context,
            QuestDefinition definition) {
        if (context == null || definition == null) {
            return;
        }
        VillagerQuestResources.questTriggerIndex(context.level().getServer(), definition.id())
                .ifPresent(index -> QuestTriggerDispatcher.markContinuousTriggersUsed(
                        progress,
                        index,
                        context.level().getGameTime()));
    }

    private static boolean runQuestTriggerActions(
            QuestTriggerContext triggerContext,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Trigger trigger) {
        DialogueContext context = triggerContext == null ? null : triggerContext.dialogueContext();
        if (context == null) {
            return false;
        }
        Map<String, String> actionReplacements = new LinkedHashMap<>(replacements(context, definition, progress));
        actionReplacements.putAll(triggerContext.replacements());
        return QuestStageActionService.runTrigger(context, definition, progress,
                "trigger/" + QuestTriggerRegistry.canonicalEventId(triggerContext.event()) + "/" + trigger.id(),
                trigger.actions(), Map.copyOf(actionReplacements), () -> sendTrackerSync(context.player(), true))
                .legacyResult().ran();
    }

    private static void sendQuestNotification(
            DialogueContext context,
            String trigger,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String fallbackText) {
        if (clientEffectsSuppressedForTests(context.player())) {
            return;
        }
        Map<String, String> replacements = replacements(context, definition, progress);
        if (context.villager() == null) {
            context.player().sendSystemMessage(Component.literal(VillagerDialogueResources.resolveTemplate(fallbackText, replacements)));
            return;
        }
        VillagerNotifications.sendHud(
                context.player(),
                context.level(),
                context.villager(),
                trigger,
                replacements,
                VillagerDialogueResources.resolveTemplate(fallbackText, replacements),
                VillagerReputationNoticeKind.QUEST
        );
    }

    private static void sendQuestProgressNotification(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String trigger,
            String fallbackText) {
        if (clientEffectsSuppressedForTests(player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Map<String, String> replacements = trackerReplacements(
                player,
                definition,
                progress,
                activeConditionsMetForPlayer(player, definition, progress));
        String fallback = VillagerDialogueResources.resolveTemplate(fallbackText, replacements);
        Villager villager = startedVillager(level, progress);
        if (villager == null) {
            VillagerReputationNetworking.sendNotice(player, fallback, VillagerReputationNoticeKind.QUEST);
            return;
        }
        VillagerNotifications.sendHud(
                player,
                level,
                villager,
                trigger,
                replacements,
                fallback,
                VillagerReputationNoticeKind.QUEST
        );
    }

    private static void sendQuestExpiredNotification(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (clientEffectsSuppressedForTests(context == null ? player : context.player())) {
            return;
        }
        QuestDefinition.Expiration expiration = definition.rules().expiration();
        if (context != null && context.villager() != null) {
            Map<String, String> replacements = replacements(context, definition, progress);
            VillagerNotifications.sendHud(
                    context.player(),
                    context.level(),
                    context.villager(),
                    expiration.notificationTrigger(),
                    replacements,
                    QuestPresentationService.resolveQuestText(
                            context,
                            new QuestDefinition.SelectedText(expiration.notificationText(), expiration.notificationTextKey()),
                            replacements),
                    VillagerReputationNoticeKind.QUEST
            );
            return;
        }
        Map<String, String> replacements = trackerReplacements(player, definition, progress, true);
        VillagerReputationNetworking.sendNotice(
                player,
                QuestPresentationService.resolveQuestText(
                        player,
                        new QuestDefinition.SelectedText(expiration.notificationText(), expiration.notificationTextKey()),
                        replacements),
                VillagerReputationNoticeKind.QUEST);
    }

    private static void sendQuestIssuerDeathNotification(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (clientEffectsSuppressedForTests(player)) {
            return;
        }
        Map<String, String> replacements = trackerReplacements(player, definition, progress, true);
        VillagerReputationNetworking.sendNotice(
                player,
                QuestPresentationService.resolveGlobalText(player, "quest.expired", "Quest expired: {quest}", replacements),
                VillagerReputationNoticeKind.QUEST);
    }

    private static Villager startedVillager(ServerLevel level, VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || progress.startedVillagerId() == null) {
            return null;
        }
        Entity entity = level.getEntity(progress.startedVillagerId());
        return entity instanceof Villager villager ? villager : null;
    }

    private static void sendTrackerSync(ServerPlayer player, boolean flash) {
        sendTrackerSync(player, flash, false);
    }

    public static void refreshTracker(ServerPlayer player) {
        sendTrackerSync(player, false, true);
    }

    private static void sendPartyTrackerSync(ServerPlayer source, PartyRecord party, boolean flash) {
        if (source == null || party == null) {
            return;
        }
        for (UUID playerId : party.playerIds()) {
            ServerPlayer member = source.getServer().getPlayerList().getPlayer(playerId);
            if (member != null) {
                sendTrackerSync(member, flash, true);
            }
        }
    }

    private static void sendTrackerSync(ServerPlayer player, boolean flash, boolean force) {
        if (clientEffectsSuppressedForTests(player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        PartyRecord party = PartyService.getPartyForPlayer(level, player.getUUID()).orElse(null);
        List<ResourceLocation> personalTrackedQuestIds = new ArrayList<>(data.getTrackedQuests(player.getUUID()));
        personalTrackedQuestIds.removeIf(questId -> isEnrolledPartyQuest(party, player.getUUID(), questId));
        List<ResourceLocation> trackedQuestIds = combinedTrackedQuestIds(
                player.getUUID(), personalTrackedQuestIds, party);
        boolean personalTrackedChanged = false;
        for (ResourceLocation trackedQuestId : List.copyOf(trackedQuestIds)) {
            if (!canTrackQuest(level, player, trackedQuestId)) {
                if (personalTrackedQuestIds.contains(trackedQuestId)) {
                    data.removeTrackedQuest(player.getUUID(), trackedQuestId);
                    personalTrackedChanged = true;
                }
                trackedQuestIds.remove(trackedQuestId);
            }
        }
        if (personalTrackedChanged) {
            personalTrackedQuestIds = new ArrayList<>(data.getTrackedQuests(player.getUUID()));
            personalTrackedQuestIds.removeIf(questId -> isEnrolledPartyQuest(party, player.getUUID(), questId));
            trackedQuestIds = combinedTrackedQuestIds(player.getUUID(), personalTrackedQuestIds, party);
            trackedQuestIds.removeIf(questId -> !canTrackQuest(level, player, questId));
        }
        if (trackedQuestIds.size() > QuestTrackerLimits.MAX_TRACKED_QUESTS) {
            trackedQuestIds = new ArrayList<>(trackedQuestIds.subList(0, QuestTrackerLimits.MAX_TRACKED_QUESTS));
        }
        ResourceLocation trackedQuestId = trackedQuestIds.isEmpty() ? null : trackedQuestIds.getFirst();
        List<Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress>> visible =
                new ArrayList<>(data.progress(player.getUUID()));
        visible.removeIf(entry -> VillagerQuestResources.quest(level.getServer(), entry.getKey()).isEmpty());
        visible.sort(Comparator
                .comparingInt((Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry) ->
                        entry.getValue().state() == VillagerQuestSavedData.QuestState.ACTIVE ? 0 : 1)
                .thenComparing(Comparator
                        .comparingLong((Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry) ->
                                entry.getValue().startedGameTime())
                        .reversed()));

        List<QuestTrackerSyncPayload.Entry> entries = new ArrayList<>();
        List<QuestTrackerSyncPayload.Entry> completionEntries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : visible) {
            if (entries.size() >= QuestTrackerSyncPayload.MAX_SYNC_ENTRIES) {
                break;
            }
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition != null) {
                VillagerQuestSavedData.QuestProgress progress = entry.getValue();
                if (!progress.completionHistory().isEmpty()) {
                    appendArchivedCompletionEntries(player, definition, progress, completionEntries);
                    if (progress.state() == VillagerQuestSavedData.QuestState.COMPLETED
                            || progress.state() == VillagerQuestSavedData.QuestState.CONSUMED) {
                        continue;
                    }
                }
                if (!shouldSyncTrackerEntry(level, entry.getKey(), progress)) {
                    continue;
                }
                DialogueContext questContext = contextForStartedVillager(level, player, entry.getValue()).orElse(null);
                ConditionMatch activeConditions = activeConditionsState(questContext, player, level, definition, entry.getValue());
                if (activeConditions == ConditionMatch.UNMET && definition.rules().activeState().hideWhenUnmet()) {
                    continue;
                }
                QuestTrackerSyncPayload.Entry trackerEntry =
                        trackerEntry(player, questContext, definition, entry.getValue(), activeConditions);
                if (progress.state() == VillagerQuestSavedData.QuestState.COMPLETED) {
                    if (completionEntries.size() < QuestTrackerSyncPayload.MAX_SYNC_ENTRIES) {
                        completionEntries.add(trackerEntry);
                    }
                } else {
                    entries.add(trackerEntry);
                }
            }
        }
        appendEntries(entries, completionEntries);
        for (var offer : com.jvn.villagerretaliation.quest.discovery.QuestDiscoverySavedData.get(level).offers(player.getUUID()).entrySet()) {
            var compiled = VillagerQuestResources.compiledQuest(level.getServer(), offer.getKey()).orElse(null);
            var progress = data.get(player.getUUID(), offer.getKey());
            if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) continue;
            if (compiled == null || compiled.discovery().stream().noneMatch(value -> value.id().equals(offer.getValue().discovery()))) continue;
            if (entries.size() >= QuestTrackerSyncPayload.MAX_SYNC_ENTRIES) break;
            entries.removeIf(value -> value.questId().equals(offer.getKey().toString()));
            entries.add(availableTrackerEntry(player, DialogueContext.forPlayer(player), compiled.asQuestDefinition()).asDiscoveryOffer());
        }
        appendNearbyAvailableQuestEntries(level, player, data, entries);
        applyQuestlineProgress(level, player, data, entries);
        String signature = QuestTrackerPresenter.syncSignature(entries, trackedQuestIds);
        Map<String, String> entrySignatures = QuestTrackerPresenter.entrySignatures(entries);
        long gameTime = level.getGameTime();
        TrackerSyncState previous = LAST_TRACKER_SYNCS.get(player.getUUID());
        boolean heartbeatDue = previous == null
                || gameTime < previous.gameTime()
                || gameTime - previous.gameTime() >= QUEST_TRACKER_HEARTBEAT_TICKS;
        if (!force
                && !flash
                && previous != null
                && previous.signature().equals(signature)
                && !heartbeatDue) {
            QuestDebugTraceService.recordIfEnabled(player, QuestDebugTraceService.EventType.TRACKER_SYNC, trackedQuestId,
                    "result=skipped reason=unchanged entries=" + entries.size());
            return;
        }
        String syncReason = flash ? "flash" : force ? "force" : previous == null ? "initial" : heartbeatDue ? "heartbeat" : "changed";
        QuestDebugTraceService.recordIfEnabled(player, QuestDebugTraceService.EventType.TRACKER_SYNC, trackedQuestId,
                "result=sent reason=" + syncReason + " entries=" + entries.size());
        List<QuestTrackerSyncPayload.Entry> syncEntries =
                markQuestUpdateEntries(entries, entrySignatures, previous, flash, trackedQuestIds);
        List<QuestTrackerSyncPayload.QuestlineNode> questlineNodes =
                questlineNodes(level, player, data, syncEntries);
        List<QuestTrackerSyncPayload.QuestMarker> questMarkers =
                questMarkers(player, data, syncEntries);
        try {
            PacketDistributor.sendToPlayer(player, new QuestTrackerSyncPayload(
                    syncEntries,
                    trackedQuestIds.stream().map(ResourceLocation::toString).toList(),
                    flash,
                    questlineNodes,
                    questMarkers));
        } catch (UnsupportedOperationException ignored) {
            QuestDebugTraceService.recordIfEnabled(player, QuestDebugTraceService.EventType.TRACKER_SYNC, trackedQuestId,
                    "result=skipped reason=unsupported_connection entries=" + entries.size());
        }
        LAST_TRACKER_SYNCS.put(player.getUUID(), new TrackerSyncState(signature, gameTime, entrySignatures));
    }

    private static List<QuestTrackerSyncPayload.Entry> markQuestUpdateEntries(
            List<QuestTrackerSyncPayload.Entry> entries,
            Map<String, String> entrySignatures,
            TrackerSyncState previous,
            boolean flash,
            List<ResourceLocation> trackedQuestIds) {
        if (entries.isEmpty()) {
            return entries;
        }
        Set<String> trackedIds = trackedQuestIds == null
                ? Set.of()
                : trackedQuestIds.stream()
                        .filter(Objects::nonNull)
                        .map(ResourceLocation::toString)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<QuestTrackerSyncPayload.Entry> updated = new ArrayList<>(entries.size());
        for (QuestTrackerSyncPayload.Entry entry : entries) {
            String currentSignature = entrySignatures.getOrDefault(entry.questId(), "");
            boolean questUpdate = !entry.questAvailable() && (previous == null
                    ? flash && trackedIds.contains(entry.questId())
                    : !Objects.equals(previous.entrySignatures().get(entry.questId()), currentSignature));
            updated.add(entry.withQuestUpdate(questUpdate));
        }
        return List.copyOf(updated);
    }

    private static List<QuestTrackerSyncPayload.QuestMarker> questMarkers(
            ServerPlayer player,
            VillagerQuestSavedData data,
            List<QuestTrackerSyncPayload.Entry> entries) {
        Map<String, QuestTrackerSyncPayload.QuestMarker> markers = new LinkedHashMap<>();
        for (QuestTrackerSyncPayload.Entry entry : entries) {
            if (!entry.questUpdate() || entry.journal().hidden()) {
                continue;
            }
            ResourceLocation questId = ResourceLocation.tryParse(entry.questId());
            VillagerQuestSavedData.QuestProgress progress =
                    questId == null ? null : data.get(player.getUUID(), questId);
            UUID villagerId = progress == null ? null : progress.startedVillagerId();
            if (villagerId != null) {
                markers.put("update:" + villagerId + ":" + entry.questId(), new QuestTrackerSyncPayload.QuestMarker(
                        villagerId, entry.questId()));
            }
        }
        return markers.values().stream()
                .limit(QuestTrackerSyncPayload.MAX_QUEST_MARKERS)
                .toList();
    }

    private static void appendNearbyAvailableQuestEntries(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData data,
            List<QuestTrackerSyncPayload.Entry> entries) {
        if (entries.size() >= QuestTrackerSyncPayload.MAX_SYNC_ENTRIES || level.getServer() == null) {
            return;
        }
        Set<String> activeQuestIds = new HashSet<>();
        Set<String> availableQuestIds = new HashSet<>();
        for (QuestTrackerSyncPayload.Entry entry : entries) {
            if (!entry.questId().isBlank()) {
                if (entry.questAvailable()) {
                    availableQuestIds.add(entry.questId());
                } else if (entryState(entry) == VillagerQuestSavedData.QuestState.ACTIVE) {
                    activeQuestIds.add(entry.questId());
                }
            }
        }
        for (QuestTrackerSyncPayload.Entry entry : nearbyAvailableQuestEntries(level, player, data)) {
            if (entries.size() >= QuestTrackerSyncPayload.MAX_SYNC_ENTRIES) {
                return;
            }
            if (!activeQuestIds.contains(entry.questId()) && availableQuestIds.add(entry.questId())) {
                entries.add(entry);
            }
        }
    }

    private static void applyQuestlineProgress(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData data,
            List<QuestTrackerSyncPayload.Entry> entries) {
        if (level == null || player == null || data == null || entries == null || entries.isEmpty()) {
            return;
        }
        Map<String, QuestTrackerPresenter.QuestlineProgress> progressByQuestline =
                QuestTrackerPresenter.questlineProgressView(
                        VillagerQuestResources.quests(level.getServer()),
                        questId -> QuestProgressSnapshot.capture(data.get(player.getUUID(), questId)));
        for (int index = 0; index < entries.size(); index++) {
            QuestTrackerSyncPayload.Entry entry = entries.get(index);
            QuestTrackerPresenter.QuestlineProgress progress =
                    progressByQuestline.get(entry.journal().questline());
            if (progress != null) {
                entries.set(index, entry.withJournal(entry.journal().withQuestlineProgress(
                        progress.completed(), progress.total())));
            }
        }
    }

    private static List<QuestTrackerSyncPayload.QuestlineNode> questlineNodes(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData data,
            List<QuestTrackerSyncPayload.Entry> entries) {
        if (level == null || player == null || data == null || entries == null || entries.isEmpty()) {
            return List.of();
        }
        Set<String> visibleQuestlines = entries.stream()
                .map(entry -> entry.journal().questline())
                .filter(questline -> questline != null && !questline.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (visibleQuestlines.isEmpty()) {
            return List.of();
        }
        Set<String> availableQuestIds = entries.stream()
                .filter(QuestTrackerSyncPayload.Entry::questAvailable)
                .map(QuestTrackerSyncPayload.Entry::questId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<QuestTrackerSyncPayload.QuestlineNode> nodes = new ArrayList<>();
        for (QuestDefinition definition : VillagerQuestResources.quests(level.getServer())) {
            if (definition == null
                    || definition.tracker().hidden()
                    || !visibleQuestlines.contains(definition.questline())) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), definition.id());
            Map<String, String> replacements = Map.of(
                    "quest", definition.title(),
                    "quest_id", definition.id().toString());
            String title = QuestTrackerPresenter.resolveText(
                    player,
                    new QuestDefinition.SelectedText(definition.title(), definition.titleKey()),
                    replacements);
            String description = QuestTrackerPresenter.resolveText(
                    player,
                    new QuestDefinition.SelectedText(definition.description(), definition.descriptionKey()),
                    replacements);
            nodes.add(new QuestTrackerSyncPayload.QuestlineNode(
                    definition.id().toString(),
                    title,
                    description,
                    definition.questline(),
                    definition.prerequisites().stream().map(ResourceLocation::toString).toList(),
                    definition.tracker().icon() == null ? "" : definition.tracker().icon().toString(),
                    definition.tracker().color(),
                    questlineNodeState(progress, availableQuestIds.contains(definition.id().toString()))));
            if (nodes.size() >= QuestTrackerSyncPayload.MAX_QUESTLINE_NODES) {
                break;
            }
        }
        nodes.sort(Comparator.comparing(QuestTrackerSyncPayload.QuestlineNode::questline)
                .thenComparing(QuestTrackerSyncPayload.QuestlineNode::title)
                .thenComparing(QuestTrackerSyncPayload.QuestlineNode::questId));
        return List.copyOf(nodes);
    }

    private static String questlineNodeState(
            VillagerQuestSavedData.QuestProgress progress,
            boolean available) {
        if (branchLocked(progress)) {
            return "closed";
        }
        if (progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED) {
            return available ? "available" : "locked";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            return "active";
        }
        if (progress.completionCount() > 0
                || progress.state() == VillagerQuestSavedData.QuestState.COMPLETED
                || progress.completedGameTime() >= 0L) {
            return "completed";
        }
        return progress.state().name().toLowerCase(Locale.ROOT);
    }

    private static void appendEntries(
            List<QuestTrackerSyncPayload.Entry> entries,
            List<QuestTrackerSyncPayload.Entry> additions) {
        if (additions.isEmpty()) {
            return;
        }
        for (QuestTrackerSyncPayload.Entry entry : additions) {
            if (entries.size() >= QuestTrackerSyncPayload.MAX_SYNC_ENTRIES) {
                return;
            }
            entries.add(entry);
        }
    }

    private static VillagerQuestSavedData.QuestState entryState(QuestTrackerSyncPayload.Entry entry) {
        if (entry == null || entry.state().isBlank()) {
            return VillagerQuestSavedData.QuestState.NOT_STARTED;
        }
        return VillagerQuestSavedData.QuestState.byName(entry.state());
    }

    private static List<QuestTrackerSyncPayload.Entry> nearbyAvailableQuestEntries(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData data) {
        UUID playerId = player.getUUID();
        long gameTime = level.getGameTime();
        NearbyAvailableQuestCache cached = NEARBY_AVAILABLE_QUEST_CACHES.get(playerId);
        if (cached != null
                && cached.dimension().equals(level.dimension())
                && cached.playerChunk() == player.chunkPosition().toLong()
                && gameTime >= cached.gameTime()
                && gameTime - cached.gameTime() < NEARBY_AVAILABLE_QUEST_CACHE_TICKS) {
            return cached.entries();
        }
        List<QuestTrackerSyncPayload.Entry> entries = buildNearbyAvailableQuestEntries(level, player, data);
        NEARBY_AVAILABLE_QUEST_CACHES.put(playerId, new NearbyAvailableQuestCache(
                level.dimension(),
                player.chunkPosition().toLong(),
                gameTime,
                entries));
        return entries;
    }

    private static List<QuestTrackerSyncPayload.Entry> buildNearbyAvailableQuestEntries(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData data) {
        double radiusSqr = NEARBY_AVAILABLE_QUEST_RADIUS * NEARBY_AVAILABLE_QUEST_RADIUS;
        List<Villager> nearbyVillagers = new ArrayList<>(level.getEntitiesOfClass(
                Villager.class,
                player.getBoundingBox().inflate(NEARBY_AVAILABLE_QUEST_RADIUS),
                villager -> villager.isAlive()
                        && !villager.isBaby()
                        && player.distanceToSqr(villager) <= radiusSqr));
        nearbyVillagers.sort(Comparator.comparingDouble(player::distanceToSqr));
        if (nearbyVillagers.size() > MAX_NEARBY_AVAILABLE_QUEST_VILLAGERS) {
            nearbyVillagers = new ArrayList<>(nearbyVillagers.subList(0, MAX_NEARBY_AVAILABLE_QUEST_VILLAGERS));
        }

        List<QuestTrackerSyncPayload.Entry> entries = new ArrayList<>();
        Set<String> includedQuestIds = new HashSet<>();
        for (Villager villager : nearbyVillagers) {
            DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
            QuestPoolResources.Evaluation poolEvaluation = QuestPoolResources.evaluate(context);
            appendDialogueAvailableQuestEntries(
                    level, player, data, context, poolEvaluation, entries, includedQuestIds);
            for (QuestDefinition definition : VillagerQuestResources.quests(level.getServer())) {
                if (entries.size() >= QuestTrackerSyncPayload.MAX_SYNC_ENTRIES) {
                    return List.copyOf(entries);
                }
                String questId = definition.id().toString();
                if (includedQuestIds.contains(questId)) {
                    continue;
                }
                VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), definition.id());
                if (VillagerQuestResources.compiledQuest(level.getServer(), definition.id())
                        .map(quest -> quest.provider().providerType().equals(ResourceLocation.parse("villagerretaliation:discovery"))).orElse(false)
                        || !canStart(context, definition, progress, false, poolEvaluation)) {
                    continue;
                }
                entries.add(availableTrackerEntry(player, context, definition));
                includedQuestIds.add(questId);
            }
        }
        return List.copyOf(entries);
    }

    private static void appendDialogueAvailableQuestEntries(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData data,
            DialogueContext context,
            QuestPoolResources.Evaluation poolEvaluation,
            List<QuestTrackerSyncPayload.Entry> entries,
            Set<String> includedQuestIds) {
        DialogueDisposition disposition = VillagerDialogueService.moodFor(context);
        for (DialogueOptionDefinition option : VillagerDialogueResources.dialogueOptions(context, disposition)) {
            if (entries.size() >= QuestTrackerSyncPayload.MAX_SYNC_ENTRIES) {
                return;
            }
            DialogueQuestAction questAction = option.questAction();
            if (questAction.action() != DialogueQuestAction.Action.START || questAction.questId() == null) {
                continue;
            }
            QuestDefinition definition = VillagerQuestResources
                    .quest(level.getServer(), questAction.questId())
                    .orElse(null);
            if (definition == null) {
                continue;
            }
            String questId = definition.id().toString();
            if (includedQuestIds.contains(questId)) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), definition.id());
            if (!canStart(context, definition, progress, true, poolEvaluation)) {
                continue;
            }
            entries.add(availableTrackerEntry(player, context, definition));
            includedQuestIds.add(questId);
        }
    }

    private static QuestTrackerSyncPayload.Entry availableTrackerEntry(
            ServerPlayer player, DialogueContext context, QuestDefinition definition) {
        return QuestPresentationService.availableEntry(player, context, definition, prerequisitePreviews(player, definition));
    }

    private static void appendArchivedCompletionEntries(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            List<QuestTrackerSyncPayload.Entry> entries) {
        List<VillagerQuestSavedData.CompletionHistoryEntry> history = progress.completionHistory();
        for (int index = history.size() - 1; index >= 0; index--) {
            if (entries.size() >= QuestTrackerSyncPayload.MAX_SYNC_ENTRIES) {
                return;
            }
            entries.add(completedHistoryTrackerEntry(player, definition, history.get(index)));
        }
    }

    private static QuestTrackerSyncPayload.Entry completedHistoryTrackerEntry(
            ServerPlayer player, QuestDefinition definition, VillagerQuestSavedData.CompletionHistoryEntry history) {
        return QuestPresentationService.completedHistoryEntry(player, definition, history, prerequisitePreviews(player, definition));
    }

    private static boolean clientEffectsSuppressedForTests(ServerPlayer player) {
        return player != null && CLIENT_EFFECTS_SUPPRESSED_FOR_TEST_PLAYERS.contains(player.getUUID());
    }

    private static boolean shouldSyncTrackerEntry(
            ServerLevel level,
            ResourceLocation questId,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null) {
            return false;
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return false;
        }
        return switch (progress.state()) {
            case ACTIVE -> true;
            case FAILED -> true;
            case ABANDONED -> definition.rules().abandonment() != QuestDefinition.AbandonmentMode.REMOVE_FOREVER
                    && !definition.rules().consumeOnAbandonment();
            case EXPIRED -> definition.rules().expiration().allowRepickup();
            case COMPLETED -> true;
            case NOT_STARTED, CONSUMED -> false;
        };
    }

    private static boolean canTrackQuest(ServerLevel level, ServerPlayer player, ResourceLocation questId) {
        VillagerQuestSavedData.QuestProgress progress = VillagerQuestSavedData.get(level).get(player.getUUID(), questId);
        if (progress == null) {
            return false;
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return false;
        }
        if (definition.tracker().hidden()) {
            return false;
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            ConditionMatch activeConditions = activeConditionsStateForPlayer(player, definition, progress);
            return activeConditions != ConditionMatch.UNMET || !definition.rules().activeState().hideWhenUnmet();
        }
        return shouldSyncTrackerEntry(level, questId, progress);
    }

    private static boolean canTrackPartyQuest(
            ServerLevel level,
            ServerPlayer player,
            PartyRecord party,
            ResourceLocation questId) {
        return canTrackQuest(level, player, questId)
                && isEnrolledPartyQuest(party, player.getUUID(), questId);
    }

    private static boolean isEnrolledPartyQuest(
            PartyRecord party,
            UUID playerId,
            ResourceLocation questId) {
        return party != null
                && playerId != null
                && questId != null
                && party.sharedQuests().stream().anyMatch(shared -> !shared.completed()
                        && shared.questId().equals(questId)
                        && shared.enrollment(playerId) != null);
    }

    private static List<ResourceLocation> combinedTrackedQuestIds(
            UUID playerId,
            List<ResourceLocation> personalQuestIds,
            PartyRecord party) {
        LinkedHashSet<ResourceLocation> combined = new LinkedHashSet<>();
        if (personalQuestIds != null) {
            combined.addAll(personalQuestIds);
        }
        if (party != null) {
            party.trackedQuests().stream()
                    .filter(questId -> isEnrolledPartyQuest(party, playerId, questId))
                    .forEach(combined::add);
        }
        return new ArrayList<>(combined);
    }

    public static List<ResourceLocation> debugTrackedQuestIdsForTests(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return List.of();
        }
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        PartyRecord party = PartyService.getPartyForPlayer(level, player.getUUID()).orElse(null);
        List<ResourceLocation> personalQuestIds = new ArrayList<>(data.getTrackedQuests(player.getUUID()));
        personalQuestIds.removeIf(questId -> isEnrolledPartyQuest(party, player.getUUID(), questId));
        List<ResourceLocation> combined = combinedTrackedQuestIds(player.getUUID(), personalQuestIds, party);
        combined.removeIf(questId -> !canTrackQuest(level, player, questId));
        return List.copyOf(combined.stream().limit(QuestTrackerLimits.MAX_TRACKED_QUESTS).toList());
    }

    private static void clearTrackedQuestIf(
            VillagerQuestSavedData data,
            ServerPlayer player,
            ResourceLocation questId) {
        if (questId != null && player != null) {
            data.removeTrackedQuest(player.getUUID(), questId);
        }
    }

    public static QuestTrackerSyncPayload.Entry debugTrackerEntryForTests(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean activeConditionsMet) {
        return trackerEntry(player, context, definition, progress, activeConditionsMet ? ConditionMatch.MET : ConditionMatch.UNMET);
    }

    private static void appendActiveConditionDebugLines(
            List<String> lines,
            ServerPlayer player,
            ServerLevel level,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        QuestDefinition.ActiveState activeState = definition.rules().activeState();
        ConditionMatch state = activeConditionsState(context, player, level, definition, progress);
        lines.add("active_conditions count=" + activeState.conditions().size()
                + " state=" + debugEnum(state)
                + " hide_when_unmet=" + activeState.hideWhenUnmet()
                + " pause_progress_when_unmet=" + activeState.pauseProgressWhenUnmet());
        if (activeState.conditions().isEmpty()) {
            return;
        }
        if (context == null) {
            lines.add("condition trace=unknown live_context=false");
            return;
        }
        for (DialogueCondition condition : activeState.conditions()) {
            lines.add("condition " + formatConditionTrace(DialogueCondition.trace(context, condition)));
        }
    }

    private static void recordConditionDebugTrace(
            ServerPlayer player,
            QuestDefinition definition,
            DialogueContext context,
            String source) {
        QuestDefinition.ActiveState activeState = definition.rules().activeState();
        if (activeState.conditions().isEmpty()) {
            QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.CONDITION, definition.id(),
                    "source=" + source + " active_conditions=none outcome=true");
            return;
        }
        if (context == null) {
            QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.CONDITION, definition.id(),
                    "source=" + source + " active_conditions=unknown live_context=false");
            return;
        }
        for (DialogueCondition condition : activeState.conditions()) {
            QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.CONDITION, definition.id(),
                    "source=" + source + " " + formatConditionTrace(DialogueCondition.trace(context, condition)));
        }
    }

    private static void recordDialogueSlotTrace(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        QuestDialogueCatalog catalog = VillagerQuestResources.questDialogueCatalog(context.level().getServer());
        for (QuestDialogueCatalog.Binding binding : catalog.bindings().values()) {
            if (!definition.id().equals(binding.questId())) {
                continue;
            }
            boolean selected = matchesEmbeddedDialogueBinding(context, definition, progress, binding);
            QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.DIALOGUE_SLOT, definition.id(),
                    "slot=" + blankAs(binding.slot(), "none")
                            + " stage=" + blankAs(binding.stageId(), "initial")
                            + " tree=" + binding.treeId()
                            + " entry=" + blankAs(binding.entryId(), "none")
                            + " selected=" + selected);
        }
    }

    private static void recordResponseTrace(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.RESPONSE, definition.id(),
                    "responses hidden reason=quest_not_active");
            return;
        }
        QuestDefinition.Stage stage = definition.stages().get(progress.currentStage());
        if (stage == null || stage.branches().isEmpty()) {
            QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.RESPONSE, definition.id(),
                    "responses hidden reason=no_stage_branches stage=" + progress.currentStage());
            return;
        }
        for (QuestDefinition.StageBranch branch : stage.branches()) {
            QuestDefinition.StageBranchBlocker blocker = matchingStageBranchBlocker(context, branch).orElse(null);
            boolean conditionsMet = stageBranchConditionsMet(context, branch);
            boolean visible = shouldShowStageBranchOption(context, branch);
            String state = !visible ? "hidden" : blocker != null ? "disabled" : conditionsMet ? "visible" : "hidden";
            String reason = !visible ? "show_conditions_unmet" : blocker != null ? "blocked" : conditionsMet ? "conditions_met" : "conditions_unmet";
            QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.RESPONSE, definition.id(),
                    "response=" + branch.id() + " state=" + state + " reason=" + reason);
        }
    }

    private static void recordObjectiveTrace(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null) {
            QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.OBJECTIVE_PROGRESS, definition.id(),
                    "progress_delta=none reason=no_saved_progress");
            return;
        }
        for (QuestDefinition.Objective objective : definition.objectives()) {
            boolean complete = objectiveComplete(player, context, level, definition, progress, objective);
            QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.OBJECTIVE_PROGRESS, definition.id(),
                    "objective=" + objective.id()
                            + " type=" + debugEnum(objective.type())
                            + " complete=" + complete
                            + " counter=" + progress.objectiveCounter(objective.id())
                            + " progress_delta=0");
        }
    }

    private static void recordTriggerTrace(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        CompiledQuest compiled = VillagerQuestResources.compiledQuest(context.level().getServer(), definition.id()).orElse(null);
        if (compiled == null || progress == null) {
            QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.TRIGGER, definition.id(),
                    "trigger_filter skipped reason=" + (compiled == null ? "compiled_missing" : "no_progress"));
            return;
        }
        long gameTime = context.level().getGameTime();
        for (QuestDefinition.TriggerEvent event : compiled.triggerIndex().events()) {
            for (CompiledQuestTrigger compiledTrigger : compiled.triggerIndex().candidates(event, progress.currentStage())) {
                QuestDebugTraceService.record(player, QuestDebugTraceService.EventType.TRIGGER, definition.id(),
                        debugTriggerFilterLine(context, gameTime, progress, compiledTrigger.definition(), event));
            }
        }
    }

    private static String debugTriggerFilterLine(
            DialogueContext context,
            long gameTime,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Trigger trigger,
            QuestDefinition.TriggerEvent event) {
        return DEBUG.debugTriggerFilterLine(context, gameTime, progress, trigger, event);
    }

    private static String formatConditionTrace(DialogueCondition.ConditionEvaluationTrace trace) {
        return DEBUG.formatConditionTrace(trace);
    }

    private static String debugProvider(Villager villager) {
        if (villager == null) {
            return "none";
        }
        BlockPos pos = villager.blockPosition();
        return VillagerPresetNameRegistry.resolveDisplayName(villager).getString()
                + "/" + VillagerInteractionTextUtil.professionName(villager.getVillagerData().getProfession(), "villager")
                + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static QuestTrackerSyncPayload.Entry trackerEntry(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            ConditionMatch activeConditions) {
        boolean activeConditionsMet = activeConditions == ConditionMatch.MET;
        String stepKey = progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                ? trackerStepKey(player, context, definition, progress, activeConditionsMet)
                : trackerStateStepKey(player, definition, progress);
        QuestDefinition.Objective currentObjective = currentObjectiveForTrackerStep(
                player,
                context,
                definition,
                progress,
                activeConditionsMet,
                stepKey);
        boolean currentObjectiveComplete = currentObjective != null
                && player.level() instanceof ServerLevel level
                && objectiveComplete(player, context, level, definition, progress, currentObjective);
        boolean readyToTurnIn = progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && activeConditionsMet
                && trackerReadyToTurnIn(player, context, definition, progress);
        QuestDefinition.Objective replacementObjective = currentObjective == null
                ? currentObjectiveForReplacements(player, context, definition, progress, activeConditionsMet)
                : currentObjective;
        var presentationProgress = capturePresentationProgress(player, definition, progress);
        Map<String, String> replacements = trackerReplacements(
                player,
                context,
                definition,
                progress,
                presentationProgress,
                replacementObjective,
                activeConditionsMet);
        QuestDefinition.Step fallback = QuestTrackerPresenter.fallbackStep(stepKey, progress.state());
        if (readyToTurnIn && fallback.progress() < 1.0F) {
            fallback = new QuestDefinition.Step(
                    fallback.text(),
                    fallback.textKey(),
                    fallback.showProgress(),
                    1.0F,
                    fallback.metadata());
        }
        boolean objectiveTracker = progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && currentObjective != null
                && QuestTrackerPresenter.objectiveTrackerHasDisplay(currentObjective, currentObjectiveComplete);
        QuestDefinition.Step step = objectiveTracker
                ? QuestTrackerPresenter.objectiveTrackerStep(currentObjective, fallback, currentObjectiveComplete)
                : definition.tracker().step(stepKey, fallback);
        if (currentObjective != null) {
            step = progressAwareObjectiveStep(
                    player,
                    context,
                    definition,
                    progress,
                    presentationProgress,
                    currentObjective,
                    currentObjectiveComplete,
                    step);
        }
        boolean configuredStep = objectiveTracker
                || (progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && definition.tracker().steps().containsKey(stepKey));
        float fallbackProgress = readyToTurnIn ? 1.0F : QuestTrackerPresenter.fallbackProgress(stepKey);
        float progressValue = configuredStep && step.progress() >= 0.0F
                ? step.progress()
                : fallbackProgress;
        boolean showProgress = configuredStep ? step.showProgress() : progress.state() == VillagerQuestSavedData.QuestState.ACTIVE;
        QuestDefinition.SelectedText title = definition.tracker().title().isBlank() && definition.tracker().titleKey().isBlank()
                ? new QuestDefinition.SelectedText(definition.title(), definition.titleKey())
                : new QuestDefinition.SelectedText(definition.tracker().title(), definition.tracker().titleKey());
        String issuer = QuestPresentationService.issuerSummaryView(player, presentationProgress);
        String issuerLocation = QuestPresentationService.issuerLocationSummaryView(player, presentationProgress);
        String status = trackerStatusText(player, definition, progress, activeConditions, replacements, readyToTurnIn);
        QuestTrackerSyncPayload.Entry presented = QuestTrackerPresenter.entry(new QuestTrackerPresenter.EntryInput(
                player,
                definition,
                title,
                step,
                replacements,
                status,
                issuer,
                issuerLocation,
                QuestTrackerPresenter.questItemsView(
                        definition,
                        presentationProgress,
                        QuestPresentationService::itemName,
                        itemId -> itemId != null && itemId.equals(definition.target().proofItem())
                                ? itemCount(player, itemId, definition.target().proofItemPredicate())
                                : itemCount(player, itemId),
                        objective -> QuestInventoryService.itemCount(player,
                                QuestProgressSnapshot.resolvedItem(objective, presentationProgress), objective.itemRequirements()),
                        objective -> QuestProgressSnapshot.resolvedItem(objective, presentationProgress),
                        requiredObjectivesForReadiness(definition, progress)),
                QuestTrackerPresenter.rewardPreviews(player, definition, replacements),
                prerequisitePreviews(player, definition),
                progress.state() == VillagerQuestSavedData.QuestState.COMPLETED
                        ? QuestPresentationService.completedObjectiveStepsView(player, definition, presentationProgress)
                        : activeObjectiveSteps(player, context, definition, progress, presentationProgress),
                progressValue,
                showProgress,
                progress.state()));
        if (progress.state() == VillagerQuestSavedData.QuestState.ACTIVE && !activeConditionsMet) {
            presented = presented.withJournal(presented.journal().withBlocker(presented.objective()));
        }
        for (var timer : com.jvn.villagerretaliation.quest.runtime.QuestTimers.active(definition, progress)) {
            if (!timer.key().startsWith("stage/") || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) continue;
            String label = QuestPresentationService.resolveGlobalText(player, "quest.tracker.timer",
                    "Time remaining: {seconds}s", Map.of("seconds", Long.toString((timer.state().remaining(timer.definition()) + 19) / 20)));
            var steps = new ArrayList<>(presented.objectiveSteps());
            steps.add(new QuestTrackerSyncPayload.ObjectiveStep(label, false));
            presented = new QuestTrackerSyncPayload.Entry(presented.questId(), presented.title(), presented.objective(),
                    presented.description(), presented.metadata(), presented.progress(), presented.showProgress(), presented.state(),
                    presented.status(), presented.issuer(), presented.issuerLocation(), presented.questItems(), presented.rewardPreviews(),
                    presented.prerequisites(), steps, presented.questUpdate(), presented.questAvailable()).withJournal(presented.journal());
        }
        return QuestPresentationService.withRuntimeJournalView(presented, definition, presentationProgress, presentationProgress.completedGameTime());
    }

    private static List<QuestTrackerSyncPayload.Prerequisite> prerequisitePreviews(
            ServerPlayer player,
            QuestDefinition definition) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return List.of();
        }
        return QuestTrackerPresenter.prerequisites(
                player,
                definition,
                parentId -> parentQuestTitle(level, player, parentId),
                parentId -> parentQuestCompleted(level, player, parentId));
    }

    private static List<QuestTrackerSyncPayload.ObjectiveStep> activeObjectiveSteps(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestProgressSnapshot presentationProgress) {
        if (player == null
                || definition == null
                || progress == null
                || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                || !(player.level() instanceof ServerLevel level)) {
            return List.of();
        }
        List<QuestTrackerSyncPayload.ObjectiveStep> steps = new ArrayList<>();
        Map<String, String> baseReplacements = trackerReplacements(player, context, definition, progress, presentationProgress, null, true);
        QuestPresentationService.addActiveTrackerStep(
                steps,
                player,
                definition,
                "travel",
                definition.target().hasStructureTarget(),
                progress.visitedTarget(),
                baseReplacements);
        QuestPresentationService.addActiveTrackerStep(
                steps,
                player,
                definition,
                "proof",
                definition.target().hasProofItem(),
                hasRequiredProof(player, definition),
                baseReplacements);
        for (QuestDefinition.Objective objective : QuestObjectiveQuery.activeObjectives(definition, progress)) {
            boolean complete = objectiveComplete(player, context, level, definition, progress, objective);
            String label = activeObjectiveStepLabel(player, context, definition, progress, presentationProgress, objective, complete);
            if (!label.isBlank()) {
                Long remaining = presentationProgress.timerRemaining().get(
                        com.jvn.villagerretaliation.quest.runtime.QuestTimers.objectiveKey(objective.id()));
                if (!complete && remaining != null) label += " (" + (remaining + 19) / 20 + "s)";
                steps.add(new QuestTrackerSyncPayload.ObjectiveStep(label, complete));
            }
            if (steps.size() >= QuestTrackerSyncPayload.MAX_OBJECTIVE_STEPS) {
                break;
            }
        }
        return List.copyOf(steps);
    }

    private static String activeObjectiveStepLabel(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestProgressSnapshot presentationProgress,
            QuestDefinition.Objective objective,
            boolean complete) {
        if (objective == null) {
            return "";
        }
        QuestDefinition.Step fallback = new QuestDefinition.Step(
                QuestPresentationService.completedObjectiveFallback(objective),
                "",
                true,
                objectiveProgress(player, context, definition, progress, objective),
                Map.of());
        QuestDefinition.Step step = QuestTrackerPresenter.objectiveTrackerHasDisplay(objective, complete)
                ? QuestTrackerPresenter.objectiveTrackerStep(objective, fallback, complete)
                : definition.tracker().step(objective.id(), fallback);
        String label = QuestTrackerPresenter.resolveText(
                player,
                new QuestDefinition.SelectedText(step.text(), step.textKey()),
                activeObjectiveStepReplacements(player, context, definition, progress, presentationProgress, objective));
        return complete ? label : progressAwareObjectiveText(player, progress, objective, label);
    }

    private static QuestDefinition.Step progressAwareObjectiveStep(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestProgressSnapshot presentationProgress,
            QuestDefinition.Objective objective,
            boolean complete,
            QuestDefinition.Step step) {
        if (step == null || complete || !objectiveUsesProgressCount(objective)) {
            return step;
        }
        String text = QuestTrackerPresenter.resolveText(
                player,
                new QuestDefinition.SelectedText(step.text(), step.textKey()),
                activeObjectiveStepReplacements(player, context, definition, progress, presentationProgress, objective));
        return new QuestDefinition.Step(
                progressAwareObjectiveText(player, progress, objective, text),
                "",
                step.showProgress(),
                step.progress(),
                step.metadata());
    }

    private static String progressAwareObjectiveText(
            ServerPlayer player,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective,
            String text) {
        if (text == null || text.isBlank() || !objectiveUsesProgressCount(objective)) {
            return text == null ? "" : text;
        }
        int required = Math.max(1, objective.count());
        int current = Math.max(0, Math.min(required, objectiveProgressCount(player, progress, objective)));
        String progressText = current + "/" + required;
        String requiredText = Integer.toString(required);
        int index = firstStandaloneNumberIndex(text, requiredText);
        if (index >= 0) {
            return text.substring(0, index) + progressText + text.substring(index + requiredText.length());
        }
        return text + " (" + progressText + ")";
    }

    private static int firstStandaloneNumberIndex(String text, String number) {
        int from = 0;
        while (from < text.length()) {
            int index = text.indexOf(number, from);
            if (index < 0) {
                return -1;
            }
            int before = index - 1;
            int after = index + number.length();
            boolean startsClean = before < 0 || !Character.isDigit(text.charAt(before));
            boolean endsClean = after >= text.length() || !Character.isDigit(text.charAt(after));
            if (startsClean && endsClean) {
                return index;
            }
            from = index + number.length();
        }
        return -1;
    }

    private static boolean objectiveUsesProgressCount(QuestDefinition.Objective objective) {
        if (objective == null) {
            return false;
        }
        return switch (objective.type()) {
            case ITEM_CHECK, MOB_KILL, BLOCK_BREAK, BLOCK_PLACE, BLOCK_INTERACT, MEMORY_EVENT, TRADE, GIFT,
                    CRITERION -> true;
            default -> false;
        };
    }

    private static int objectiveProgressCount(
            ServerPlayer player,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        if (objective == null) {
            return 0;
        }
        if (objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK) {
            return player == null ? 0 : itemCount(player, objective, progress);
        }
        return progress == null ? 0 : progress.objectiveCounter(objective.id());
    }

    private static Map<String, String> activeObjectiveStepReplacements(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestProgressSnapshot presentationProgress,
            QuestDefinition.Objective objective) {
        return trackerReplacements(player, context, definition, progress, presentationProgress, objective, true);
    }

    private static String parentQuestTitle(ServerLevel level, ServerPlayer player, ResourceLocation parentId) {
        return VillagerQuestResources.quest(level.getServer(), parentId)
                .map(parent -> QuestPresentationService.questTitle(player, parent, Map.of()))
                .orElseGet(() -> VillagerInteractionTextUtil.resourcePathName(parentId));
    }

    private static boolean parentQuestCompleted(ServerLevel level, ServerPlayer player, ResourceLocation parentId) {
        QuestDefinition parent = VillagerQuestResources.quest(level.getServer(), parentId).orElse(null);
        if (parent == null) {
            return false;
        }
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(level).get(player.getUUID(), parent.id());
        return progress != null && progress.completionCount() > 0;
    }

    private static String trackerStateStepKey(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return switch (progress.state()) {
            case FAILED -> "failed";
            case ABANDONED -> definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN
                    && !cooldownElapsed(
                            player.level().getGameTime(),
                            progress.abandonedGameTime(),
                            definition.rules().abandonmentCooldownTicks())
                    ? "abandoned_cooldown"
                    : "abandoned";
            case EXPIRED -> "expired";
            case COMPLETED -> "completed";
            case CONSUMED -> "consumed";
            case NOT_STARTED -> "not_started";
            case ACTIVE -> trackerStepKey(player, definition, progress, true);
        };
    }

    private static QuestDefinition.Objective currentObjectiveForTrackerStep(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean activeConditionsMet,
            String stepKey) {
        if (!activeConditionsMet
                || progress == null
                || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                || QuestObjectiveQuery.activeObjectives(definition, progress).isEmpty()
                || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        QuestDefinition.Objective incomplete = firstIncompleteRequiredObjective(player, context, level, definition, progress).orElse(null);
        if (incomplete != null) {
            if (QuestTrackerPresenter.objectiveTrackerHasDisplay(incomplete, false)) {
                return incomplete;
            }
            boolean hasConfiguredStep = definition.tracker().steps().containsKey(stepKey);
            boolean itemCollectionStep = "proof".equals(stepKey) && !definition.target().hasProofItem();
            if (incomplete.id().equals(stepKey) || itemCollectionStep || !hasConfiguredStep) {
                return incomplete;
            }
            return null;
        }
        QuestDefinition.Objective optional = firstIncompleteOptionalObjectiveWithDisplay(
                player,
                context,
                level,
                definition,
                progress).orElse(null);
        if (optional != null && (optional.id().equals(stepKey)
                || QuestTrackerPresenter.objectiveTrackerHasDisplay(optional, false))) {
            return optional;
        }
        if (!"return".equals(stepKey)) {
            return null;
        }
        return firstCompletedRequiredObjectiveWithCompletionDisplay(player, context, level, definition, progress).orElse(null);
    }

    private static Optional<QuestDefinition.Objective> firstCompletedRequiredObjectiveWithCompletionDisplay(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        for (QuestDefinition.Objective objective : QuestObjectiveQuery.activeObjectives(definition, progress)) {
            if (objective.optional()
                    || !objective.tracker().hasCompletionDisplay()
                    || !objectiveComplete(player, context, level, definition, progress, objective)) {
                continue;
            }
            return Optional.of(objective);
        }
        return Optional.empty();
    }

    private static Optional<QuestDefinition.Objective> firstIncompleteOptionalObjectiveWithDisplay(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        for (QuestDefinition.Objective objective : QuestObjectiveQuery.activeObjectives(definition, progress)) {
            if (!objective.optional()
                    || !QuestTrackerPresenter.objectiveTrackerHasDisplay(objective, false)
                    || objectiveComplete(player, context, level, definition, progress, objective)) {
                continue;
            }
            return Optional.of(objective);
        }
        return Optional.empty();
    }

    private static QuestDefinition.Objective currentObjectiveForReplacements(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean activeConditionsMet) {
        if (!activeConditionsMet
                || progress == null
                || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                || QuestObjectiveQuery.activeObjectives(definition, progress).isEmpty()
                || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        return firstIncompleteRequiredObjective(player, context, level, definition, progress)
                .or(() -> firstIncompleteOptionalObjectiveWithDisplay(player, context, level, definition, progress))
                .or(() -> firstCompletedRequiredObjectiveWithCompletionDisplay(player, context, level, definition, progress))
                .orElse(null);
    }

    private static String trackerStepKey(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean activeConditionsMet) {
        return trackerStepKey(player, null, definition, progress, activeConditionsMet);
    }

    private static String trackerStepKey(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean activeConditionsMet) {
        if (!activeConditionsMet) {
            return "inactive";
        }
        if (progress == null) {
            return "inactive";
        }
        if (!progress.campaignAlternateEntry() && definition.target().hasStructureTarget() && !progress.visitedTarget()) {
            return "travel";
        }
        if (!progress.campaignAlternateEntry() && definition.target().hasProofItem() && !hasRequiredProof(player, definition)) {
            return "proof";
        }
        String stageStepKey = currentStageTrackerStepKey(definition, progress);
        if (progress != null && !QuestObjectiveQuery.activeObjectives(definition, progress).isEmpty() && player.level() instanceof ServerLevel level) {
            QuestDefinition.Objective objective = firstIncompleteRequiredObjective(
                    player,
                    context,
                    level,
                    definition,
                    progress).orElse(null);
            if (objective != null) {
                if (definition.tracker().steps().containsKey(objective.id())) {
                    return objective.id();
                }
                if (QuestTrackerPresenter.objectiveTrackerHasDisplay(objective, false)) {
                    return objective.id();
                }
                if (!stageStepKey.isBlank()) {
                    return stageStepKey;
                }
                String registeredStep = QuestObjectiveRegistry.trackerStepKey(objective);
                if (!registeredStep.isBlank()) {
                    return registeredStep;
                }
                return "inactive";
            }
            QuestDefinition.Objective optional = firstIncompleteOptionalObjectiveWithDisplay(
                    player,
                    context,
                    level,
                    definition,
                    progress).orElse(null);
            if (optional != null) {
                return optional.id();
            }
        }
        if (!stageStepKey.isBlank()) {
            return stageStepKey;
        }
        return "return";
    }

    private static String currentStageTrackerStepKey(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (definition == null || progress == null) {
            return "";
        }
        String currentStage = progress.currentStage();
        return !currentStage.isBlank() && definition.tracker().steps().containsKey(currentStage)
                ? currentStage
                : "";
    }

    private static Map<String, String> trackerReplacements(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean activeConditionsMet) {
        return trackerReplacements(player, null, definition, progress, null, activeConditionsMet);
    }

    private static Map<String, String> trackerReplacements(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective,
            boolean activeConditionsMet) {
        return trackerReplacements(player, null, definition, progress, objective, activeConditionsMet);
    }

    private static Map<String, String> trackerReplacements(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective,
            boolean activeConditionsMet) {
        return trackerReplacements(player, context, definition, progress,
                capturePresentationProgress(player, definition, progress), objective, activeConditionsMet);
    }

    private static QuestProgressSnapshot capturePresentationProgress(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (player != null && progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && ensureRandomItemSelections(definition, progress)) {
            VillagerQuestSavedData.get(player.serverLevel()).setDirty();
        }
        return QuestProgressSnapshot.capture(progress, definition);
    }

    private static Map<String, String> trackerReplacements(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestProgressSnapshot presentationProgress,
            QuestDefinition.Objective objective,
            boolean activeConditionsMet) {
        return QuestPresentationService.trackerReplacementsView(player, definition, presentationProgress, objective,
                activeConditionsMet, hasRequiredProof(player, definition),
                objectivePresentationState(player, context, definition, progress, objective));
    }

    private static QuestActionOutcome result(
            String status,
            String lineId,
            String template,
            Map<String, String> replacements) {
        String text = VillagerDialogueResources.resolveTemplate(template, replacements);
        return new QuestActionOutcome(
                status,
                lineId,
                text,
                replacements
        );
    }

    private static Map<String, String> replacements(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        boolean activeConditionsMet = activeConditionsMet(context, definition);
        QuestDefinition.Objective objective = currentObjectiveForReplacements(
                context.player(), context, definition, progress, activeConditionsMet);
        return QuestPresentationService.dialogueReplacementsView(context, definition, QuestProgressSnapshot.capture(progress, definition), objective,
                activeConditionsMet, hasRequiredProof(context.player(), definition),
                objectivePresentationState(context.player(), context, definition, progress, objective));
    }

    private static QuestPresentationService.ObjectiveState objectivePresentationState(
            ServerPlayer player, DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, QuestDefinition.Objective objective) {
        if (objective == null) return QuestPresentationService.ObjectiveState.EMPTY;
        return new QuestPresentationService.ObjectiveState(
                objectiveProgressCount(player, progress, objective),
                reputationForObjective(player.serverLevel(), player, progress),
                progress != null && objectiveComplete(player, context, player.serverLevel(), definition, progress, objective),
                objectiveProgress(player, context, definition, progress, objective));
    }

    private static float objectiveProgress(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        if (progress != null
                && objective.type() != QuestDefinition.ObjectiveType.ITEM_CHECK
                && progress.objectiveComplete(objective.id())) {
            return 1.0F;
        }
        Optional<QuestObjectiveResult> registryResult = QuestObjectiveRegistry.evaluate(
                objectiveEvaluationContext(player, context, player.serverLevel(), definition, progress),
                objective);
        if (registryResult.isPresent()) {
            return registryResult.get().progress();
        }
        return 0.0F;
    }

    private static String trackerStatusText(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            ConditionMatch activeConditions,
            Map<String, String> replacements,
            boolean readyToTurnIn) {
        if (progress.completionPending()) {
            return QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.reward_pending", "Rewards pending", replacements);
        }
        return switch (progress.state()) {
            case ACTIVE -> {
                boolean activeConditionsMet = activeConditions == ConditionMatch.MET;
                if (!activeConditionsMet) {
                    if (activeConditions == ConditionMatch.UNKNOWN) {
                        yield QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.waiting_for_context", "Waiting for issuer", replacements);
                    }
                    yield QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.inactive", "Inactive", replacements);
                }
                yield readyToTurnIn
                        ? QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.ready", "Ready to turn in", replacements)
                        : QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.active", "Active", replacements);
            }
            case ABANDONED -> {
                if (definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN
                        && !cooldownElapsed(
                                player.level().getGameTime(),
                                progress.abandonedGameTime(),
                                definition.rules().abandonmentCooldownTicks())) {
                    yield QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.abandoned_cooldown", "Abandoned - available later", replacements);
                }
                yield QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.abandoned", "Abandoned - return to restart", replacements);
            }
            case EXPIRED -> QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.expired", "Expired", replacements);
            case FAILED -> QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.failed", "Failed", replacements);
            case COMPLETED -> QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.completed", "Completed", replacements);
            case CONSUMED -> branchLocked(progress)
                    ? QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.branch_locked", "Closed by another choice", replacements)
                    : QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.consumed", "Unavailable", replacements);
            case NOT_STARTED -> QuestPresentationService.resolveGlobalText(player, "quest.tracker.status.not_started", "Not started", replacements);
        };
    }

    public static void onEntityKilled(LivingEntity killed, Entity attacker) {
        if (killed == null || !(killed.level() instanceof ServerLevel level)) {
            return;
        }
        Entity contributor = attacker;
        if (contributor instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() != null) {
            contributor = projectile.getOwner();
        }
        if (contributor == null) {
            contributor = killed.getKillCredit();
        }
        Set<ResourceLocation> sharedQuestIds = contributor == null
                ? Set.of()
                : onSharedPartyKill(level, killed, contributor);
        ServerPlayer player = contributor instanceof ServerPlayer serverPlayer
                ? serverPlayer
                : killed.getKillCredit() instanceof ServerPlayer serverPlayer
                        ? serverPlayer
                        : null;
        if (player == null || player.level() != level) {
            return;
        }
        onObjectiveEvent(level, player, QuestObjectiveEvent.mobKill(killed), sharedQuestIds);
    }

    private static Set<ResourceLocation> onSharedPartyKill(
            ServerLevel level,
            LivingEntity killed,
            Entity contributor) {
        com.jvn.villagerretaliation.party.PartyRecord party =
                com.jvn.villagerretaliation.party.PartyService.getPartyForEntity(contributor).orElse(null);
        if (party == null) {
            return Set.of();
        }
        boolean playerContributor = contributor instanceof ServerPlayer;
        boolean villagerContributor = contributor instanceof Villager
                && com.jvn.villagerretaliation.party.PartyService
                .getPartyForVillager(level, contributor.getUUID())
                .filter(current -> current.id().equals(party.id()))
                .isPresent();
        if (!playerContributor && !villagerContributor) {
            return Set.of();
        }

        QuestObjectiveEvent event = QuestObjectiveEvent.mobKill(killed);
        Set<ResourceLocation> handledQuestIds = new LinkedHashSet<>();
        boolean changed = false;
        for (com.jvn.villagerretaliation.party.PartySharedQuestRecord shared : party.sharedQuests()) {
            if (shared.completed() || playerContributor && !shared.linked(contributor.getUUID())) {
                continue;
            }
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), shared.questId()).orElse(null);
            VillagerQuestSavedData.QuestProgress canonical = canonicalSharedProgress(level, shared);
            if (definition == null || canonical == null || !PartyQuestService.isShareable(definition)) {
                continue;
            }
            QuestObjectiveEvaluationContext evaluationContext = new QuestObjectiveEvaluationContext(
                    null, null, level, definition, canonical,
                    objective -> 0,
                    (objective, stack) -> matchesObjectiveItemStack(objective, stack, canonical),
                    objective -> 0,
                    objective -> false,
                    objective -> false,
                    objective -> QuestObjectiveDebugState.EMPTY);
            boolean sharedChanged = false;
            for (QuestDefinition.Objective objective : QuestObjectiveQuery.activeObjectives(definition, canonical)) {
                if (objective.type() != QuestDefinition.ObjectiveType.MOB_KILL
                        || !QuestObjectiveRegistry.matchesEvent(evaluationContext, objective, event)) {
                    continue;
                }
                handledQuestIds.add(shared.questId());
                if (!shared.markDeathProcessed(objective.id(), killed.getUUID())) {
                    continue;
                }
                int count = shared.incrementObjective(objective.id());
                if (count >= objective.count()) {
                    shared.markObjectiveComplete(objective.id());
                }
                sharedChanged = true;
            }
            if (!sharedChanged) {
                continue;
            }
            changed = true;
            syncSharedQuestAfterEvent(level, shared, definition);
        }
        if (changed) {
            VillagerQuestSavedData.get(level).setDirty();
            com.jvn.villagerretaliation.party.PartyService.markChanged(level);
        }
        return Set.copyOf(handledQuestIds);
    }

    private static void syncSharedQuestAfterEvent(
            ServerLevel level,
            com.jvn.villagerretaliation.party.PartySharedQuestRecord shared,
            QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        for (com.jvn.villagerretaliation.party.PartySharedQuestRecord.Enrollment enrollment
                : shared.enrollments().values()) {
            if (enrollment.pendingStart()) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = data.get(enrollment.playerId(), definition.id());
            if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
                continue;
            }
            List<String> newlyCompleted = new ArrayList<>();
            for (QuestDefinition.Objective objective : definition.objectives()) {
                boolean wasComplete = progress.objectiveComplete(objective.id());
                int delta = shared.objectiveCounter(objective.id()) - progress.objectiveCounter(objective.id());
                if (delta > 0) {
                    progress.addObjectiveCounter(objective.id(), delta);
                }
                if (shared.objectiveComplete(objective.id())
                        && progress.markObjectiveComplete(objective.id())
                        && !wasComplete) {
                    newlyCompleted.add(objective.id());
                }
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(enrollment.playerId());
            if (player == null) {
                continue;
            }
            for (QuestDefinition.Objective objective : definition.objectives()) {
                if (newlyCompleted.contains(objective.id())) {
                    markQuestObjectiveFact(level, player, definition, objective);
                }
            }
            advanceStageAfterEvent(level, player, definition, progress);
            sendQuestProgressNotification(player, definition, progress, "quest.updated", "Quest updated: {quest}");
            dispatchQuestTriggers(player, definition, progress, QuestDefinition.TriggerEvent.PROGRESS);
            sendTrackerSync(player, true);
        }
    }

    public static void onBlockBroken(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        onBlockEvent(level, player, pos, state, QuestObjectiveEventKind.BLOCK_BREAK);
    }

    public static void onBlockPlaced(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        onBlockEvent(level, player, pos, state, QuestObjectiveEventKind.BLOCK_PLACE);
    }

    public static void onBlockInteracted(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        onBlockEvent(level, player, pos, state, QuestObjectiveEventKind.BLOCK_INTERACT);
    }

    public static void onMemoryEvent(ServerLevel level, VillageEventMemory.MemoryEvent event) {
        if (level == null || event == null || event.tagId() == null || event.playerId() == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(event.playerId());
        if (player == null || player.level() != level) {
            return;
        }
        onObjectiveEvent(level, player, QuestObjectiveEvent.memory(event));
    }

    public static void onGiftGiven(
            ServerLevel level,
            ServerPlayer player,
            Villager villager,
            ItemStack giftedStack,
            VillagerGiftPreferences.GiftReaction reaction,
            int reputationValue) {
        if (level == null
                || player == null
                || villager == null
                || villager.level() != level
                || giftedStack == null
                || giftedStack.isEmpty()
                || reaction == null
                || player.level() != level) {
            return;
        }

        onObjectiveEvent(level, player, QuestObjectiveEvent.gift(villager, giftedStack, reaction));
    }

    public static void onTradeCompleted(
            ServerLevel level,
            ServerPlayer player,
            AbstractVillager villager,
            MerchantOffer offer) {
        if (level == null
                || player == null
                || villager == null
                || offer == null
                || player.level() != level
                || villager.level() != level) {
            return;
        }

        onObjectiveEvent(level, player, QuestObjectiveEvent.trade(villager, offer));
    }

    public static void onReputationChanged(
            ServerLevel level,
            ServerPlayer player,
            AbstractVillager villager,
            int reputationValue) {
        if (level == null || player == null || villager == null || player.level() != level || villager.level() != level) {
            return;
        }
        onObjectiveEvent(level, player, QuestObjectiveEvent.reputation(villager, reputationValue));
    }

    private static void onBlockEvent(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos,
            BlockState state,
            QuestObjectiveEventKind kind) {
        if (level == null || player == null || pos == null || state == null || player.level() != level) {
            return;
        }
        onObjectiveEvent(level, player, QuestObjectiveEvent.block(kind, pos, state));
    }

    private static boolean ensureRandomItemSelections(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (definition == null || progress == null || progress.questRunId() == null) {
            return false;
        }
        boolean changed = false;
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (!objective.usesRandomItemSelection()
                    || progress.resolvedObjectiveItem(objective.id()) != null) {
                continue;
            }
            ResourceLocation selected = selectRandomObjectiveItem(objective, progress.questRunId());
            if (selected != null) {
                changed |= progress.resolveObjectiveItem(objective.id(), selected);
            }
        }
        return changed;
    }

    private static ResourceLocation selectRandomObjectiveItem(
            QuestDefinition.Objective objective,
            UUID runId) {
        List<WeightedItemSelection> candidates = new ArrayList<>();
        for (QuestDefinition.ItemSelectionEntry entry : objective.itemSelections()) {
            if (entry.item() != null
                    && BuiltInRegistries.ITEM.getOptional(entry.item()).filter(item -> item != Items.AIR).isPresent()) {
                candidates.add(new WeightedItemSelection(List.of(entry.item()), entry.weight()));
                continue;
            }
            if (entry.tag() == null) {
                continue;
            }
            List<ResourceLocation> tagItems = BuiltInRegistries.ITEM
                    .getTag(TagKey.create(Registries.ITEM, entry.tag()))
                    .stream()
                    .flatMap(holders -> holders.stream())
                    .filter(holder -> holder.value() != Items.AIR)
                    .map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
            if (!tagItems.isEmpty()) {
                candidates.add(new WeightedItemSelection(tagItems, entry.weight()));
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        long totalWeight = candidates.stream()
                .mapToLong(WeightedItemSelection::weight)
                .reduce(0L, VillagerQuestService::saturatingAdd);
        long seed = runId.getMostSignificantBits()
                ^ Long.rotateLeft(runId.getLeastSignificantBits(), 19)
                ^ ((long) objective.id().hashCode() * 0x9E3779B97F4A7C15L);
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(seed);
        double roll = random.nextDouble() * totalWeight;
        WeightedItemSelection selected = candidates.getLast();
        long cumulative = 0L;
        for (WeightedItemSelection candidate : candidates) {
            cumulative = saturatingAdd(cumulative, candidate.weight());
            if (roll < cumulative) {
                selected = candidate;
                break;
            }
        }
        return selected.items().get(random.nextInt(selected.items().size()));
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private record WeightedItemSelection(List<ResourceLocation> items, int weight) {
    }

    private static String debugProgressLine(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueContext context) {
        if (progress == null) {
            return QuestDebugFormatter.progressLine(new QuestDebugFormatter.ProgressLine(
                    false,
                    VillagerQuestSavedData.QuestState.NOT_STARTED,
                    "",
                    0,
                    0,
                    0,
                    "false",
                    "n/a",
                    false));
        }
        ConditionMatch activeConditions = activeConditionsStateForPlayer(player, definition, progress);
        String ready = progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                ? "false"
                : context == null
                        ? "unknown_no_live_issuer"
                        : Boolean.toString(isReadyToTurnIn(context, definition, progress));
        return QuestDebugFormatter.progressLine(new QuestDebugFormatter.ProgressLine(
                true,
                progress.state(),
                progress.currentStage(),
                progress.startCount(),
                progress.completionCount(),
                progress.abandonCount(),
                ready,
                debugEnum(activeConditions),
                branchLocked(progress)));
    }

    private static String debugObjectiveLine(
            ServerPlayer player,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueContext context,
            QuestDefinition.Objective objective) {
        QuestObjectiveEvaluationContext objectiveContext =
                objectiveEvaluationContext(player, context, level, definition, progress);
        Optional<QuestObjectiveResult> registryResult = QuestObjectiveRegistry.evaluate(objectiveContext, objective);
        QuestObjectiveDebugState registryDebug = registryResult
                .map(result -> QuestObjectiveRegistry.debugState(objectiveContext, objective, result))
                .orElse(QuestObjectiveDebugState.EMPTY);
        boolean complete = progress != null
                && objectiveComplete(player, context, level, definition, progress, objective);
        int counter = progress == null ? 0 : progress.objectiveCounter(objective.id());
        return QuestDebugFormatter.objectiveLine(
                definition.id(),
                objective,
                new QuestDebugFormatter.ObjectiveLineState(
                        complete,
                        counter,
                        registryDebug.itemCountOr(
                                objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK
                                        ? itemCount(player, objective, progress) : 0),
                        objective.type() == QuestDefinition.ObjectiveType.REPUTATION
                                ? reputationForObjective(level, player, progress)
                                : 0,
                        registryDebug.factScopeKeyOr(""),
                        registryDebug.conditionStateOr("")));
    }

    private static String debugEnum(Enum<?> value) {
        return DEBUG.debugEnum(value);
    }

    private static String blankAs(String value, String fallback) {
        return DEBUG.blankAs(value, fallback);
    }

    private static String lineId(QuestDefinition definition, String stage) {
        return "quest_" + definition.id().toString().replace(':', '_').replace('/', '_') + "_" + stage;
    }

    private static final class StageAccess implements QuestStageTransitionCoordinator.Access {
        @Override public void ensureStageTarget(
                DialogueContext context,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress) {
            VillagerQuestService.ensureStageTarget(context, definition, progress);
        }
        @Override public boolean dispatchStageChangedTriggers(
                DialogueContext context,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress) {
            return VillagerQuestService.dispatchStageChangedTriggers(context, definition, progress);
        }
        @Override public void sendTrackerSync(ServerPlayer player, boolean flash) {
            VillagerQuestService.sendTrackerSync(player, flash);
        }
        @Override public String playerQuestScopeKey(ServerPlayer player, QuestDefinition definition) {
            return VillagerQuestService.playerQuestScopeKey(player, definition);
        }
        @Override public boolean objectiveComplete(
                ServerPlayer player,
                DialogueContext context,
                ServerLevel level,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress,
                QuestDefinition.Objective objective) {
            return VillagerQuestService.objectiveComplete(player, context, level, definition, progress, objective);
        }
        @Override public ActionResult runStageActions(
                DialogueContext context,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress,
                String key,
                List<VillagerActionDefinition> actions) {
            return VillagerQuestService.runStageActions(context, definition, progress, key, actions);
        }
        @Override public Optional<DialogueContext> contextForStartedVillager(
                ServerLevel level,
                ServerPlayer player,
                VillagerQuestSavedData.QuestProgress progress) {
            return VillagerQuestService.contextForStartedVillager(level, player, progress);
        }
        @Override public boolean conditionsMet(ServerPlayer player, ServerLevel level, QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress, List<DialogueCondition> conditions) {
            return conditionsStateWithoutLiveContext(player, level, definition, progress, conditions) == ConditionMatch.MET;
        }
    }

    private static final class DebugAccess implements QuestDebugService.Access {
        @Override public boolean changeQuestStage(
                DialogueContext context,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress,
                String stage,
                boolean runExitActions,
                boolean runEntryActions) {
            return VillagerQuestService.changeQuestStage(context, definition, progress, stage, runExitActions, runEntryActions);
        }
        @Override public boolean advanceStageIfComplete(
                DialogueContext context,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress) {
            return VillagerQuestService.advanceStageIfComplete(context, definition, progress);
        }
        @Override public QuestActionOutcome startQuest(
                DialogueContext context,
                QuestDefinition definition,
                boolean bypassOfferRequirements,
                boolean forceRestart) {
            return VillagerQuestService.startQuest(context, definition, bypassOfferRequirements, forceRestart);
        }
        @Override public boolean canStart(
                DialogueContext context,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress,
                boolean bypassOfferRequirements) {
            return VillagerQuestService.canStart(context, definition, progress, bypassOfferRequirements);
        }
        @Override public Optional<DialogueContext> contextForStartedVillager(
                ServerLevel level,
                ServerPlayer player,
                VillagerQuestSavedData.QuestProgress progress) {
            return VillagerQuestService.contextForStartedVillager(level, player, progress);
        }
        @Override public boolean resumePendingTriggerWork(
                DialogueContext context,
                CompiledQuest compiled,
                VillagerQuestSavedData.QuestProgress progress) {
            return VillagerQuestService.resumePendingTriggerWork(context, compiled, progress);
        }
        @Override public boolean runQuestTriggerActions(
                QuestTriggerContext triggerContext,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress,
                QuestDefinition.Trigger trigger) {
            return VillagerQuestService.runQuestTriggerActions(triggerContext, definition, progress, trigger);
        }
        @Override public void sendTrackerSync(ServerPlayer player, boolean flash) {
            VillagerQuestService.sendTrackerSync(player, flash);
        }
        @Override public void sendTrackerSync(ServerPlayer player, boolean flash, boolean force) {
            VillagerQuestService.sendTrackerSync(player, flash, force);
        }
        @Override public void clearTrackedQuestIf(VillagerQuestSavedData data, ServerPlayer player, ResourceLocation questId) {
            VillagerQuestService.clearTrackedQuestIf(data, player, questId);
        }
        @Override public Map<String, String> replacements(
                DialogueContext context,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress) {
            return VillagerQuestService.replacements(context, definition, progress);
        }
        @Override public String debugProgressLine(
                ServerPlayer player,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress,
                DialogueContext context) {
            return VillagerQuestService.debugProgressLine(player, definition, progress, context);
        }
        @Override public String debugObjectiveLine(
                ServerPlayer player,
                ServerLevel level,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress,
                DialogueContext context,
                QuestDefinition.Objective objective) {
            return VillagerQuestService.debugObjectiveLine(player, level, definition, progress, context, objective);
        }
    }

    public record DebugStartResult(boolean started, String message) {
        public DebugStartResult {
            message = message == null ? "" : message;
        }
    }

    public record DebugRemoveResult(boolean removed, String message) {
        public DebugRemoveResult {
            message = message == null ? "" : message;
        }
    }

    public record DebugInspectResult(boolean found, List<String> lines, String message) {
        public DebugInspectResult {
            lines = lines == null ? List.of() : List.copyOf(lines);
            message = message == null ? "" : message;
        }
    }

    public record QuestActionOutcome(
            String status,
            String lineId,
            String text,
            Map<String, String> replacements) {
        public QuestActionOutcome {
            status = status == null ? "" : status;
            text = text == null ? "" : text;
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }

        public VillagerDialogueService.DialogueResult dialogueResult() {
            return new VillagerDialogueService.DialogueResult(this.lineId, this.text);
        }
    }

    public record ProviderRebindResult(
            boolean rebound,
            String message,
            UUID previousProviderId,
            UUID replacementProviderId) {
        public ProviderRebindResult {
            message = message == null ? "" : message;
        }
    }

    private record TrackerSyncState(String signature, long gameTime, Map<String, String> entrySignatures) {
        private TrackerSyncState {
            entrySignatures = entrySignatures == null ? Map.of() : Map.copyOf(entrySignatures);
        }
    }

    private record NearbyAvailableQuestCache(
            ResourceKey<Level> dimension,
            long playerChunk,
            long gameTime,
            List<QuestTrackerSyncPayload.Entry> entries) {
        private NearbyAvailableQuestCache {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }


}
