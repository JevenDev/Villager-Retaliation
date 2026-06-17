package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionExecutor;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueQuestAction;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.mood.VillagerMoodSavedData;
import com.jvn.villagerretaliation.mood.VillagerMoodState;
import com.jvn.villagerretaliation.network.QuestTrackerRequestPayload;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import com.jvn.villagerretaliation.social.VillagerFamilyTreeSnapshot;
import com.jvn.villagerretaliation.social.VillagerRelationshipSnapshot;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.village.VillageMembership;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerQuestService {
    private static final int QUEST_PROGRESS_SCAN_INTERVAL_TICKS = 20;
    private static final long DIRECT_HIT_MEMORY_TICKS = 20L * 60L * 20L;
    private static final long BROKEN_BED_MEMORY_TICKS = 20L * 60L * 20L;
    private static final int QUEST_TRACKER_HEARTBEAT_TICKS = 20 * 30;
    private static final int MAX_STAGE_ADVANCES_PER_CHECK = 8;
    private static final int APPROXIMATE_COORDINATE_STEP = 50;
    private static final long QUEST_STORY_HINT_TICKS = 20L * 60L * 60L * 6L;
    private static final ResourceLocation QUEST_STARTED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_started");
    private static final ResourceLocation QUEST_COMPLETED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_completed");
    private static final ResourceLocation QUEST_ABANDONED_FACT =
            ResourceLocation.fromNamespaceAndPath(VillagerRetaliation.MOD_ID, "quest_abandoned");
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
    private static final String STAGE_BRANCH_OPTION_PREFIX = "vr_stage_branch:";
    private static final int STAGE_BRANCH_OPTION_ORDER = 900;
    private static final ThreadLocal<Boolean> DISPATCHING_STAGE_TRIGGERS =
            ThreadLocal.withInitial(() -> false);
    private static final Map<UUID, TrackerSyncState> LAST_TRACKER_SYNCS = new HashMap<>();
    private static final Map<UUID, InventoryItemCountCache> INVENTORY_ITEM_COUNT_CACHES = new HashMap<>();

    private VillagerQuestService() {
    }

    private enum ConditionMatch {
        MET,
        UNMET,
        UNKNOWN
    }

    private record StageBranchOptionKey(ResourceLocation questId, String branchId) {
    }

    private record InventoryItemCountCache(
            int changeCount,
            Map<ResourceLocation, Integer> counts,
            Map<InventoryObjectiveCountKey, Integer> objectiveCounts) {
    }

    private record InventoryObjectiveCountKey(
            ResourceLocation itemId,
            QuestDefinition.ItemRequirements requirements) {
        private static InventoryObjectiveCountKey of(QuestDefinition.Objective objective) {
            return new InventoryObjectiveCountKey(objective.item(), objective.itemRequirements());
        }
    }

    public static void clearRuntimeState() {
        LAST_TRACKER_SYNCS.clear();
        INVENTORY_ITEM_COUNT_CACHES.clear();
    }

    public static void clearRuntimeState(ServerPlayer player) {
        if (player != null) {
            LAST_TRACKER_SYNCS.remove(player.getUUID());
            INVENTORY_ITEM_COUNT_CACHES.remove(player.getUUID());
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
                        stageBranchOptionId(definition.id(), branch.id()),
                        label,
                        DialogueRequestType.QUESTION,
                        order++));
            }
        }
        return List.copyOf(options);
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

    private static Optional<VillagerDialogueService.DialogueResult> handleStageBranchOption(
            DialogueContext context,
            String optionId) {
        Optional<StageBranchOptionKey> key = parseStageBranchOptionId(optionId);
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

        recordStageBranchFact(context, definition, progress, branch, QUEST_BRANCH_SELECTED_FACT, "selected");
        boolean changed = runStageActions(context, definition, progress, branch.actions());
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
        for (DialogueCondition condition : branch.conditions()) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
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
        for (DialogueCondition condition : conditions) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
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

    private static String stageBranchOptionId(ResourceLocation questId, String branchId) {
        return STAGE_BRANCH_OPTION_PREFIX + encodeOptionPart(questId.toString()) + ":" + encodeOptionPart(branchId);
    }

    private static Optional<StageBranchOptionKey> parseStageBranchOptionId(String optionId) {
        if (optionId == null || !optionId.startsWith(STAGE_BRANCH_OPTION_PREFIX)) {
            return Optional.empty();
        }
        String payload = optionId.substring(STAGE_BRANCH_OPTION_PREFIX.length());
        int separator = payload.indexOf(':');
        if (separator <= 0 || separator >= payload.length() - 1) {
            return Optional.empty();
        }
        ResourceLocation questId = ResourceLocation.tryParse(decodeOptionPart(payload.substring(0, separator)));
        String branchId = decodeOptionPart(payload.substring(separator + 1));
        if (questId == null || branchId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new StageBranchOptionKey(questId, branchId));
    }

    private static String encodeOptionPart(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeOptionPart(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    public static Optional<QuestActionOutcome> performAction(
            DialogueContext context,
            ResourceLocation questId,
            DialogueQuestAction.Action action) {
        return performAction(context, questId, fromDialogueAction(action));
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

        return Optional.of(switch (action) {
            case START -> startQuest(context, definition);
            case REMIND -> remindQuest(context, definition);
            case TURN_IN -> turnInQuest(context, definition);
            case ABANDON -> abandonQuest(context, definition);
            case BLOCK -> blockQuest(context, definition);
            case NONE -> result("none", "quest_no_action", "", Map.of());
        });
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
            case NONE -> VillagerActionDefinition.QuestAction.NONE;
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
        if (!(player.level() instanceof ServerLevel level)
                || player.tickCount % QUEST_PROGRESS_SCAN_INTERVAL_TICKS != 0
                || !player.isAlive()
                || player.isSpectator()) {
            return;
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        boolean changed = false;
        boolean progressNotice = false;
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : data.activeProgress(player.getUUID())) {
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = entry.getValue();
            DialogueContext questContext = contextForStartedVillager(level, player, progress).orElse(null);
            if (expireQuestIfNeeded(player, definition, progress, questContext)) {
                changed = true;
                progressNotice = true;
                clearTrackedQuestIf(data, player, entry.getKey());
                continue;
            }
            if (definition.rules().activeState().pauseProgressWhenUnmet()
                    && activeConditionsState(questContext, player, level, definition, progress) != ConditionMatch.MET) {
                continue;
            }
            boolean questProgressChanged = false;
            if (!progress.hasProof()
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
            if (!progress.visitedTarget()
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
            if (questContext != null && advanceStageIfComplete(questContext, definition, progress)) {
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
            if (definition != null && !definition.rules().lockedToVillager()) {
                continue;
            }

            entry.progress().expire(gameTime, false);
            changed = true;
            if (entry.questId().equals(data.getTrackedQuest(entry.playerId()))) {
                data.clearTrackedQuest(entry.playerId());
            }

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
        if (questId == null || !canTrackQuest(level, player, questId)) {
            sendTrackerSync(player, false, true);
            return;
        }

        switch (action == null ? QuestTrackerRequestPayload.Action.TOGGLE : action) {
            case TRACK -> data.setTrackedQuest(player.getUUID(), questId);
            case UNTRACK -> {
                if (questId.equals(data.getTrackedQuest(player.getUUID()))) {
                    data.clearTrackedQuest(player.getUUID());
                }
            }
            case TOGGLE -> data.toggleTrackedQuest(player.getUUID(), questId);
        }
        sendTrackerSync(player, false, true);
    }

    public static DebugStartResult debugStartQuest(ServerPlayer player, Villager provider, ResourceLocation questId, boolean force) {
        if (player == null || provider == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugStartResult(false, "This debug command must be run by a player so nearby villagers can be resolved.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugStartResult(false, "Unknown quest: " + questId);
        }
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, provider);
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), definition.id());
        if (!force && progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            return new DebugStartResult(false, "Quest is already active for " + player.getGameProfile().getName() + ".");
        }
        if (!force && !canStart(context, definition, progress, true)) {
            return new DebugStartResult(false, "Quest cannot restart from its current state. Use force_start to replace existing quest state.");
        }

        boolean replacedExisting = force && progress != null && progress.state() != VillagerQuestSavedData.QuestState.NOT_STARTED;
        QuestActionOutcome outcome = startQuest(context, definition, true, force);
        if (!"started".equals(outcome.status())) {
            return new DebugStartResult(false, outcome.text().isBlank() ? "Debug quest start failed." : outcome.text());
        }

        String providerName = VillagerPresetNameRegistry.resolveDisplayName(provider).getString();
        String profession = VillagerInteractionTextUtil.professionName(provider.getVillagerData().getProfession(), "villager");
        BlockPos pos = provider.blockPosition();
        String replaced = replacedExisting
                ? " Existing quest state was replaced."
                : "";
        return new DebugStartResult(
                true,
                "Started quest " + definition.title()
                        + " for " + player.getGameProfile().getName()
                        + " from provider " + providerName + " the " + profession
                        + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        + ". Offer requirements were bypassed for debug."
                        + replaced);
    }

    public static DebugRemoveResult debugRemoveQuest(ServerPlayer player, ResourceLocation questId) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugRemoveResult(false, "This debug command must be run by a player so quest state can be resolved.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugRemoveResult(false, "Unknown quest: " + questId);
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress removed = data.remove(player.getUUID(), definition.id());
        if (removed == null) {
            return new DebugRemoveResult(false, "No saved quest state exists for " + definition.title()
                    + " on " + player.getGameProfile().getName() + ".");
        }

        clearTrackedQuestIf(data, player, definition.id());
        sendTrackerSync(player, true);

        return new DebugRemoveResult(
                true,
                "Removed quest " + definition.title()
                        + " for " + player.getGameProfile().getName()
                        + ". Previous state was " + removed.state().name().toLowerCase(Locale.ROOT) + ".");
    }

    public static DebugInspectResult debugInspectQuest(ServerPlayer player, ResourceLocation questId) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return new DebugInspectResult(false, List.of(), "This debug command must be run by a player so quest state can be resolved.");
        }
        QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (definition == null) {
            return new DebugInspectResult(false, List.of(), "Unknown quest: " + questId);
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress progress = data.get(player.getUUID(), definition.id());
        DialogueContext context = progress == null
                ? null
                : contextForStartedVillager(level, player, progress).orElse(null);
        List<String> lines = new ArrayList<>();
        lines.add("Quest " + definition.id() + " | " + definition.title());
        lines.add("identity questline=" + blankAs(definition.questline(), "none")
                + " parent=" + debugParentState(data, player, definition.parent())
                + " objectives=" + definition.objectives().size()
                + " triggers=" + definition.triggers().size());
        QuestDefinition.Rules rules = definition.rules();
        lines.add("rules repeatable=" + rules.repeatable()
                + " max_starts=" + rules.maxStarts()
                + " max_completions=" + rules.maxCompletions()
                + " completion_scope=" + debugEnum(rules.completionScope())
                + " completion_cooldown_ticks=" + rules.completionCooldownTicks()
                + " locked_to_villager=" + rules.lockedToVillager()
                + " cross_villager=" + rules.crossVillagerCompatible());
        lines.add("active_state conditions=" + rules.activeState().conditions().size()
                + " hide_when_unmet=" + rules.activeState().hideWhenUnmet()
                + " pause_progress_when_unmet=" + rules.activeState().pauseProgressWhenUnmet()
                + " expiration_enabled=" + rules.expiration().enabled()
                + " expiration_after_ticks=" + rules.expiration().afterTicks());
        lines.add("branching exclusive_group=" + debugResource(rules.branching().exclusiveGroup())
                + " exclusive_on=" + debugEnum(rules.branching().exclusiveOn())
                + " blocks_on_start=" + debugResourceSet(rules.branching().blocksOnStart())
                + " blocks_on_completion=" + debugResourceSet(rules.branching().blocksOnCompletion()));
        lines.add(debugProgressLine(player, definition, progress, context));
        if (progress != null) {
            lines.add("issuer status=" + issuerStatus(player, progress)
                    + " id=" + (progress.startedVillagerId() == null ? "none" : progress.startedVillagerId())
                    + " name=" + blankAs(progress.issuerName(), "unknown")
                    + " profession=" + blankAs(progress.issuerProfession(), "unknown")
                    + " dimension=" + debugDimension(progress.issuerDimension())
                    + " pos=" + debugPos(progress.issuerPos()));
            lines.add("target visited=" + progress.visitedTarget()
                    + " proof=" + progress.hasProof()
                    + " objective=" + blankAs(progress.targetObjectiveId(), "none")
                    + " dimension=" + debugDimension(progress.targetDimension())
                    + " pos=" + debugPos(progress.targetPos()));
            lines.add("times started=" + progress.startedGameTime()
                    + " completed=" + progress.completedGameTime()
                    + " abandoned=" + progress.abandonedGameTime()
                    + " expired=" + progress.expiredGameTime()
                    + " consumed_reason=" + blankAs(progress.consumedReason(), "none"));
        }
        if (definition.target().hasStructureTarget()) {
            lines.add("target_definition structure=" + definition.target().structure()
                    + " dimension=" + debugDimension(definition.target().dimension())
                    + " search_radius=" + definition.target().searchRadius()
                    + " discovery_radius=" + definition.target().discoveryRadius()
                    + " proof_item=" + debugResource(definition.target().proofItem()));
        }
        for (QuestDefinition.Objective objective : definition.objectives()) {
            lines.add(debugObjectiveLine(player, level, definition, progress, context, objective));
        }
        return new DebugInspectResult(true, lines, "");
    }

    public static boolean syncQuestStage(DialogueContext context, ResourceLocation questId, String stage) {
        if (context == null || questId == null || stage == null) {
            return false;
        }
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), questId);
        if (progress == null) {
            return false;
        }
        QuestDefinition definition = VillagerQuestResources.quest(context.level().getServer(), questId).orElse(null);
        if (definition == null) {
            return false;
        }
        boolean changed = changeQuestStage(context, definition, progress, stage, true, true);
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
        String initialStage = initialStage(definition);
        if (initialStage.isBlank()) {
            return;
        }
        progress.setCurrentStage(initialStage);
        syncQuestStageFact(context, definition, progress.currentStage());
        QuestDefinition.Stage stage = definition.stages().get(progress.currentStage());
        if (stage != null) {
            runStageActions(context, definition, progress, stage.entryActions());
        }
        advanceStageIfComplete(context, definition, progress);
    }

    private static String initialStage(QuestDefinition definition) {
        if (definition == null || definition.stages().isEmpty()) {
            return "";
        }
        if (definition.stages().containsKey("started")) {
            return "started";
        }
        return definition.stages().keySet().iterator().next();
    }

    private static boolean changeQuestStage(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String stage,
            boolean runExitActions,
            boolean runEntryActions) {
        String nextStage = stage == null ? "" : stage.trim();
        if (context == null
                || definition == null
                || progress == null
                || nextStage.isBlank()
                || progress.currentStage().equals(nextStage)) {
            return false;
        }

        QuestDefinition.Stage previous = definition.stages().get(progress.currentStage());
        if (runExitActions && previous != null) {
            runStageActions(context, definition, progress, previous.exitActions());
        }
        if (!progress.setCurrentStage(nextStage)) {
            return false;
        }
        syncQuestStageFact(context, definition, nextStage);
        dispatchStageChangedTriggers(context, definition, progress);

        QuestDefinition.Stage current = definition.stages().get(nextStage);
        if (runEntryActions && current != null) {
            runStageActions(context, definition, progress, current.entryActions());
        }
        sendTrackerSync(context.player(), true);
        return true;
    }

    private static void syncQuestStageFact(
            DialogueContext context,
            QuestDefinition definition,
            String stage) {
        String scopeKey = playerQuestScopeKey(context.player(), definition);
        if (!scopeKey.isBlank()) {
            VillagerQuestFacts.get(context.level()).setVariable(scopeKey, "stage", stage);
        }
    }

    private static boolean advanceStageIfComplete(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (context == null || definition == null || progress == null || definition.stages().isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < MAX_STAGE_ADVANCES_PER_CHECK; i++) {
            QuestDefinition.Stage stage = definition.stages().get(progress.currentStage());
            if (stage == null
                    || stage.next().isBlank()
                    || stage.completeWhen().isEmpty()
                    || !definition.stages().containsKey(stage.next())
                    || !stagePredicatesMet(context, definition, progress, stage)) {
                break;
            }
            if (!changeQuestStage(context, definition, progress, stage.next(), true, true)) {
                break;
            }
            changed = true;
        }
        return changed;
    }

    private static boolean advanceStageAfterEvent(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return contextForStartedVillager(level, player, progress)
                .map(context -> advanceStageIfComplete(context, definition, progress))
                .orElse(false);
    }

    private static boolean stagePredicatesMet(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Stage stage) {
        for (QuestDefinition.StagePredicate predicate : stage.completeWhen()) {
            if (!stagePredicateMet(context, definition, progress, predicate)) {
                return false;
            }
        }
        return true;
    }

    private static boolean stagePredicateMet(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.StagePredicate predicate) {
        if (predicate == null || predicate.isEmpty()) {
            return false;
        }
        if (!predicate.objective().isBlank()) {
            QuestDefinition.Objective objective = definition.objectives().stream()
                    .filter(candidate -> candidate.id().equals(predicate.objective()))
                    .findFirst()
                    .orElse(null);
            if (objective == null
                    || !objectiveComplete(context.player(), context, context.level(), definition, progress, objective)) {
                return false;
            }
        }
        for (DialogueCondition condition : predicate.conditions()) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
    }

    private static boolean runStageActions(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            List<VillagerActionDefinition> actions) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        boolean ranAction = false;
        Map<String, String> replacements = new LinkedHashMap<>(replacements(context, definition, progress));
        for (VillagerActionDefinition action : actions) {
            VillagerActionResult result = VillagerActionExecutor.execute(context, action, replacements);
            replacements.putAll(result.replacements());
            if (result.flashTracker()) {
                sendTrackerSync(context.player(), true);
            }
            ranAction |= result.ran();
        }
        return ranAction;
    }

    private static QuestActionOutcome startQuest(DialogueContext context, QuestDefinition definition) {
        return startQuest(context, definition, false, false);
    }

    private static QuestActionOutcome startQuest(
            DialogueContext context,
            QuestDefinition definition,
            boolean bypassOfferRequirements,
            boolean forceRestart) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (!forceRestart && progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            return matchesVillagerLock(context, definition, progress)
                    ? remindQuest(context, definition)
                    : result(
                            "locked_to_villager",
                            lineId(definition, "unavailable"),
                            startBlockedLine(context, definition, progress),
                            replacements(context, definition, progress));
        }
        if (!forceRestart && !canStart(context, definition, progress, bypassOfferRequirements)) {
            return result(
                    startBlockedStatus(context, definition, progress),
                    lineId(definition, "unavailable"),
                    startBlockedLine(context, definition, progress),
                    replacements(context, definition, progress));
        }

        VillagerQuestTargets.LocatedTarget target =
                VillagerQuestTargets.locateInitialTarget(context, definition).orElse(null);
        if (target == null && VillagerQuestTargets.requiresLocatedTarget(definition)) {
            return result(
                    "locate_failed",
                    lineId(definition, "locate_failed"),
                    resolveQuestText(
                            context,
                            definition.dialogue().selectLocateFailedText(context.random()),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }

        VillagerQuestSavedData.QuestProgress started = data.getOrCreate(context.player().getUUID(), definition.id());
        started.start(
                context.villager().getUUID(),
                target == null ? context.level().dimension() : target.dimension(),
                target == null ? null : target.pos(),
                context.level().getGameTime());
        started.setIssuer(
                context.villager().getUUID(),
                VillagerPresetNameRegistry.resolveDisplayName(context.villager()).getString(),
                VillagerProfessionUtil.id(context.profession()).toString(),
                context.villager().getVillagerData().getLevel(),
                context.level().dimension(),
                context.villager().blockPosition(),
                liveVillageScopeKey(context.level(), context.villager()));
        if (target != null && !target.objectiveId().isBlank()) {
            started.setTarget(context.villager().getUUID(), target.dimension(), target.pos(), target.objectiveId());
        }
        markContinuousTriggersUsed(started, definition, context.level().getGameTime());
        if (definition.target().hasProofItem() && hasRequiredProof(context.player(), definition)) {
            started.markHasProof();
        }
        markQuestLifecycleFact(context.level(), context.player(), definition, QUEST_STARTED_FACT, "started");
        initializeQuestStage(context, definition, started);
        lockBranchQuests(context, definition, QuestDefinition.BranchLockEvent.STARTED);
        data.setDirty();
        data.setTrackedQuest(context.player().getUUID(), definition.id());
        if (target != null) {
            rememberQuestStoryHint(context, definition, target);
        }
        sendQuestNotification(context, "quest.started", definition, started, "Quest started: {quest}");
        if (dispatchQuestTriggers(context, definition, started, QuestDefinition.TriggerEvent.STARTED)) {
            data.setDirty();
        }
        sendTrackerSync(context.player(), true);

        return result(
                "started",
                lineId(definition, "start"),
                resolveQuestText(
                        context,
                        definition.dialogue().selectStartText(context.random()),
                        replacements(context, definition, started)),
                replacements(context, definition, started));
    }

    private static QuestActionOutcome remindQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !matchesVillagerLock(context, definition, progress)) {
            return result(
                    "unavailable",
                    lineId(definition, "unavailable"),
                    resolveQuestText(
                            context,
                            definition.dialogue().selectUnavailableText(context.random()),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        if (!activeConditionsMet(context, definition)) {
            return result(
                    "inactive",
                    lineId(definition, "inactive"),
                    resolveQuestText(
                            context,
                            definition.dialogue().selectInactiveText(context.random()),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        return result(
                "reminder",
                lineId(definition, "reminder"),
                resolveQuestText(
                        context,
                        definition.dialogue().selectReminderText(context.random()),
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
                    resolveQuestText(
                            context,
                            definition.dialogue().selectUnavailableText(context.random()),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        if (!activeConditionsMet(context, definition)) {
            return result(
                    "inactive",
                    lineId(definition, "inactive"),
                    resolveQuestText(
                            context,
                            definition.dialogue().selectInactiveText(context.random()),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        if (definition.target().hasStructureTarget() && !progress.visitedTarget()) {
            return result(
                    "missing_target",
                    lineId(definition, "missing_target"),
                    resolveQuestText(
                            context,
                            definition.dialogue().selectMissingTargetText(context.random()),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        if (!hasRequiredProof(context.player(), definition)) {
            return result(
                    "missing_proof",
                    lineId(definition, "missing_proof"),
                    resolveQuestText(
                            context,
                            definition.dialogue().selectMissingProofText(context.random()),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        if (!requiredObjectivesComplete(context.player(), context, definition, progress)) {
            return result(
                    "missing_objectives",
                    lineId(definition, "missing_objectives"),
                    resolveGlobalText(
                            context.player(),
                            "quest.dialogue.missing_objectives",
                            "There is still more to do before this is ready.",
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }
        ItemHandInResult itemHandInResult = handInRequiredObjectiveItems(context, definition);
        if (itemHandInResult != ItemHandInResult.SUCCESS) {
            return result(
                    itemHandInResult.status,
                    lineId(definition, "missing_objectives"),
                    resolveGlobalText(
                            context.player(),
                            itemHandInResult.messageKey,
                            itemHandInResult.message,
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }

        progress.markHasProof();
        progress.complete(context.level().getGameTime(), definition.rules().consumeOnCompletion());
        markQuestLifecycleFact(context.level(), context.player(), definition, QUEST_COMPLETED_FACT, "completed");
        recordScopedCompletion(context, definition);
        lockBranchQuests(context, definition, QuestDefinition.BranchLockEvent.COMPLETED);
        data.setDirty();
        clearTrackedQuestIf(data, context.player(), definition.id());
        awardRewards(context, definition);
        sendQuestNotification(context, "quest.completed", definition, progress, "Quest completed: {quest}");
        if (dispatchQuestTriggers(context, definition, progress, QuestDefinition.TriggerEvent.COMPLETED)) {
            data.setDirty();
        }
        sendTrackerSync(context.player(), true);

        return result(
                "completed",
                lineId(definition, "turn_in"),
                resolveQuestText(
                        context,
                        definition.dialogue().selectTurnInText(context.random()),
                        replacements(context, definition, progress)),
                replacements(context, definition, progress));
    }

    private static QuestActionOutcome abandonQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !matchesVillagerLock(context, definition, progress)) {
            return result(
                    "unavailable",
                    lineId(definition, "unavailable"),
                    resolveQuestText(
                            context,
                            definition.dialogue().selectUnavailableText(context.random()),
                            replacements(context, definition, progress)),
                    replacements(context, definition, progress));
        }

        boolean consume = definition.rules().consumeOnAbandonment()
                || definition.rules().abandonment() == QuestDefinition.AbandonmentMode.REMOVE_FOREVER;
        progress.abandon(context.level().getGameTime(), consume);
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
                        ? resolveGlobalText(
                                context.player(),
                                "quest.dialogue.abandoned_forever",
                                "I will close my notes on that journey.",
                                replacements(context, definition, progress))
                        : resolveGlobalText(
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
                    resolveQuestText(
                            context,
                            definition.dialogue().selectAlreadyCompletedText(context.random()),
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
        locked.consume(BRANCH_LOCK_CONSUMED_REASON);
        markQuestBranchLockedFact(context.level(), context.player(), definition, null, null, null);
        data.setDirty();
        clearTrackedQuestIf(data, context.player(), definition.id());
        sendTrackerSync(context.player(), true);
        return result(
                "branch_locked",
                lineId(definition, "branch_locked"),
                resolveGlobalText(
                        context.player(),
                        "quest.dialogue.branch_locked",
                        "That path has closed because of another choice.",
                        replacements(context, definition, locked)),
                replacements(context, definition, locked));
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
        if (!bypassOfferRequirements && !definition.offer().matches(context)) {
            return false;
        }
        if (!parentCompleted(context, definition)) {
            return false;
        }
        if (progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED) {
            return withinStartLimit(definition, progress) && withinCompletionLimit(context, definition, progress);
        }
        if (!definition.rules().crossVillagerCompatible()
                && progress.startedVillagerId() != null
                && !progress.startedVillagerId().equals(context.villager().getUUID())) {
            return false;
        }
        if (!withinStartLimit(definition, progress) || !withinCompletionLimit(context, definition, progress)) {
            return false;
        }
        return switch (progress.state()) {
            case ACTIVE, CONSUMED -> false;
            case COMPLETED -> definition.rules().repeatable()
                    && cooldownElapsed(context.level().getGameTime(), progress.completedGameTime(), definition.rules().completionCooldownTicks());
            case EXPIRED -> definition.rules().expiration().allowRepickup();
            case ABANDONED -> switch (definition.rules().abandonment()) {
                case REMOVE_FOREVER -> false;
                case ALLOW_REPICKUP -> true;
                case COOLDOWN -> cooldownElapsed(
                        context.level().getGameTime(),
                        progress.abandonedGameTime(),
                        definition.rules().abandonmentCooldownTicks());
            };
            case NOT_STARTED -> true;
        };
    }

    private static boolean withinStartLimit(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        int maxStarts = definition.rules().maxStarts();
        return maxStarts <= 0 || progress == null || progress.startCount() < maxStarts;
    }

    private static boolean parentCompleted(DialogueContext context, QuestDefinition definition) {
        if (definition.parent() == null) {
            return true;
        }
        if (context == null) {
            return false;
        }
        QuestDefinition parent = VillagerQuestResources.quest(context.level().getServer(), definition.parent()).orElse(null);
        if (parent == null) {
            return false;
        }
        VillagerQuestSavedData.QuestProgress parentProgress =
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), parent.id());
        return matchesState(context, parent, parentProgress, "completed");
    }

    private static boolean withinCompletionLimit(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        int maxCompletions = definition.rules().maxCompletions();
        if (maxCompletions <= 0) {
            return true;
        }
        if (isPlayerCompletionScope(definition.rules().completionScope())) {
            return progress == null || progress.completionCount() < maxCompletions;
        }
        return scopedCompletionCount(context, definition) < maxCompletions;
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
        QuestFactScope factScope = switch (definition.rules().completionScope()) {
            case PLAYER, PLAYER_WORLD -> QuestFactScope.PLAYER;
            case WORLD -> QuestFactScope.WORLD;
            case VILLAGE -> QuestFactScope.VILLAGE;
            case VILLAGER -> QuestFactScope.VILLAGER;
        };
        return factScope.scopeKey(context, definition.id());
    }

    private static String completionCounterKey(QuestDefinition definition) {
        return "completion:" + definition.id();
    }

    private static boolean isPlayerCompletionScope(QuestDefinition.CompletionScope scope) {
        return scope == null
                || scope == QuestDefinition.CompletionScope.PLAYER
                || scope == QuestDefinition.CompletionScope.PLAYER_WORLD;
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
                && (progress.state() == VillagerQuestSavedData.QuestState.COMPLETED
                || progress.state() == VillagerQuestSavedData.QuestState.CONSUMED)) {
            return false;
        }

        VillagerQuestSavedData.QuestProgress locked =
                progress == null ? data.getOrCreate(context.player().getUUID(), target.id()) : progress;
        locked.consume(BRANCH_LOCK_CONSUMED_REASON);
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
                : "quest:" + player.getUUID() + ":" + definition.id();
    }

    private static boolean cooldownElapsed(long gameTime, long eventTime, long cooldownTicks) {
        return cooldownTicks <= 0L || eventTime <= 0L || gameTime - eventTime >= cooldownTicks;
    }

    private static boolean matchesVillagerLock(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return progress == null
                || !definition.rules().lockedToVillager()
                || progress.startedVillagerId() == null
                || progress.startedVillagerId().equals(context.villager().getUUID());
    }

    private static boolean activeConditionsMet(DialogueContext context, QuestDefinition definition) {
        QuestDefinition.ActiveState activeState = definition.rules().activeState();
        if (!activeState.hasConditions()) {
            return true;
        }
        if (context == null) {
            return false;
        }
        for (DialogueCondition condition : activeState.conditions()) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
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
            return activeConditionsMet(context, definition) ? ConditionMatch.MET : ConditionMatch.UNMET;
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

    private static ConditionMatch conditionsStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            List<DialogueCondition> conditions) {
        ConditionMatch result = ConditionMatch.MET;
        for (DialogueCondition condition : conditions) {
            ConditionMatch conditionMatch = conditionStateWithoutLiveContext(player, level, definition, progress, condition);
            if (conditionMatch == ConditionMatch.UNMET) {
                return ConditionMatch.UNMET;
            }
            if (conditionMatch == ConditionMatch.UNKNOWN) {
                result = ConditionMatch.UNKNOWN;
            }
        }
        return result;
    }

    private static ConditionMatch conditionStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition condition) {
        if (condition instanceof DialogueCondition.AllOf allOf) {
            return conditionsStateWithoutLiveContext(player, level, definition, progress, allOf.conditions());
        }
        if (condition instanceof DialogueCondition.AnyOf anyOf) {
            boolean sawUnknown = false;
            for (DialogueCondition child : anyOf.conditions()) {
                ConditionMatch childMatch = conditionStateWithoutLiveContext(player, level, definition, progress, child);
                if (childMatch == ConditionMatch.MET) {
                    return ConditionMatch.MET;
                }
                sawUnknown |= childMatch == ConditionMatch.UNKNOWN;
            }
            return sawUnknown ? ConditionMatch.UNKNOWN : ConditionMatch.UNMET;
        }
        if (condition instanceof DialogueCondition.Not not) {
            ConditionMatch childMatch = conditionStateWithoutLiveContext(player, level, definition, progress, not.condition());
            return switch (childMatch) {
                case MET -> ConditionMatch.UNMET;
                case UNMET -> ConditionMatch.MET;
                case UNKNOWN -> ConditionMatch.UNKNOWN;
            };
        }
        if (condition instanceof DialogueCondition.Quest quest) {
            return questConditionStateWithoutLiveContext(player, level, quest.questId(), quest.states());
        }
        if (condition instanceof DialogueCondition.QuestFact fact) {
            return questFactStateWithoutLiveContext(player, level, definition, progress, fact);
        }
        if (condition instanceof DialogueCondition.Reputation reputation) {
            return reputationStateWithoutLiveContext(player, level, progress, reputation);
        }
        if (condition instanceof DialogueCondition.Memory memory) {
            ConditionMatch memoryState = memoryStateWithoutLiveContext(player, level, progress, memory);
            if (memoryState != ConditionMatch.UNKNOWN) {
                return memoryState;
            }
        }
        if (condition instanceof DialogueCondition.SocialAttribute socialAttribute) {
            return socialAttributeStateWithoutLiveContext(level, progress, socialAttribute);
        }
        if (condition instanceof DialogueCondition.Skill skill) {
            return skillStateWithoutLiveContext(level, progress, skill);
        }
        if (condition instanceof DialogueCondition.Family family) {
            return familyStateWithoutLiveContext(level, progress, family);
        }
        if (condition instanceof DialogueCondition.Relationship relationship) {
            return relationshipStateWithoutLiveContext(level, progress, relationship);
        }
        if (condition instanceof DialogueCondition.RecruitmentMemory recruitmentMemory) {
            return recruitmentMemoryStateWithoutLiveContext(player, level, progress, recruitmentMemory);
        }
        if (condition instanceof DialogueCondition.VillagerAge villagerAge) {
            return villagerAgeStateWithoutLiveContext(level, progress, villagerAge);
        }
        if (condition instanceof DialogueCondition.VillagerLevel villagerLevel) {
            return villagerLevelStateWithoutLiveContext(progress, villagerLevel);
        }
        if (condition instanceof DialogueCondition.Mood mood) {
            return moodStateWithoutLiveContext(level, progress, mood);
        }
        if (condition instanceof DialogueCondition.Weather weather) {
            return weather.states().contains(savedWeatherState(level, player, progress))
                    ? ConditionMatch.MET
                    : ConditionMatch.UNMET;
        }
        if (condition instanceof DialogueCondition.Time time) {
            return time.times().contains(savedTimeOfDay(level)) ? ConditionMatch.MET : ConditionMatch.UNMET;
        }
        return ConditionMatch.UNKNOWN;
    }

    private static ConditionMatch questConditionStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            ResourceLocation questId,
            Set<String> states) {
        QuestDefinition target = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
        if (target == null) {
            return ConditionMatch.UNMET;
        }
        if (states == null || states.isEmpty()) {
            return ConditionMatch.MET;
        }
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(level).get(player.getUUID(), questId);
        boolean sawUnknown = false;
        for (String state : states) {
            ConditionMatch stateMatch = savedQuestState(target, progress, state);
            if (stateMatch == ConditionMatch.MET) {
                return ConditionMatch.MET;
            }
            sawUnknown |= stateMatch == ConditionMatch.UNKNOWN;
        }
        return sawUnknown ? ConditionMatch.UNKNOWN : ConditionMatch.UNMET;
    }

    private static ConditionMatch savedQuestState(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String state) {
        String normalized = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
        boolean completed = progress != null && progress.completionCount() > 0;
        return switch (normalized) {
            case "not_started", "locked" -> progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED
                    ? ConditionMatch.MET
                    : ConditionMatch.UNMET;
            case "active", "started" -> progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                    ? ConditionMatch.MET
                    : ConditionMatch.UNMET;
            case "completed", "complete" -> {
                if (completed) {
                    yield ConditionMatch.MET;
                }
                yield isPlayerCompletionScope(definition.rules().completionScope())
                        ? ConditionMatch.UNMET
                        : ConditionMatch.UNKNOWN;
            }
            case "abandoned", "dropped" -> progress != null && progress.state() == VillagerQuestSavedData.QuestState.ABANDONED
                    ? ConditionMatch.MET
                    : ConditionMatch.UNMET;
            case "expired", "timed_out", "time_out" -> progress != null && progress.state() == VillagerQuestSavedData.QuestState.EXPIRED
                    ? ConditionMatch.MET
                    : ConditionMatch.UNMET;
            case "consumed", "removed", "removed_forever" -> progress != null && progress.state() == VillagerQuestSavedData.QuestState.CONSUMED
                    ? ConditionMatch.MET
                    : ConditionMatch.UNMET;
            case "branch_locked", "branch_blocked", "blocked_branch" -> branchLocked(progress)
                    ? ConditionMatch.MET
                    : ConditionMatch.UNMET;
            case "not_completed" -> {
                if (completed) {
                    yield ConditionMatch.UNMET;
                }
                yield isPlayerCompletionScope(definition.rules().completionScope())
                        ? ConditionMatch.MET
                        : ConditionMatch.UNKNOWN;
            }
            default -> ConditionMatch.UNKNOWN;
        };
    }

    private static ConditionMatch questFactStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.QuestFact fact) {
        String scopeKey = savedQuestFactScopeKey(player, level, definition, progress, fact.scope(), fact.questId());
        if (scopeKey.isBlank()) {
            return ConditionMatch.UNKNOWN;
        }
        VillagerQuestFacts facts = VillagerQuestFacts.get(level);
        if (!fact.tags().isEmpty() && fact.tags().stream().noneMatch(tag -> facts.hasTag(scopeKey, tag))) {
            return ConditionMatch.UNMET;
        }
        if (fact.key() == null || fact.key().isBlank()) {
            return fact.tags().isEmpty() ? ConditionMatch.UNKNOWN : ConditionMatch.MET;
        }
        Optional<String> variable = facts.variable(scopeKey, fact.key());
        if (!fact.values().isEmpty() && variable.stream().noneMatch(fact.values()::contains)) {
            return ConditionMatch.UNMET;
        }
        int counter = facts.counter(scopeKey, fact.key());
        if (fact.min() != null && counter < fact.min()) {
            return ConditionMatch.UNMET;
        }
        if (fact.max() != null && counter > fact.max()) {
            return ConditionMatch.UNMET;
        }
        return !fact.values().isEmpty() || fact.min() != null || fact.max() != null || variable.isPresent() || counter != 0
                ? ConditionMatch.MET
                : ConditionMatch.UNMET;
    }

    private static ConditionMatch reputationStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Reputation reputation) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionMatch.UNKNOWN;
        }
        VillagerReputationSavedData.ReputationEntry entry =
                VillagerReputationSavedData.get(level).get(villagerId, player.getUUID());
        int value = entry == null ? 0 : entry.reputation();
        VillagerReputationLevel reputationLevel = VillagerReputationLevel.fromReputation(value);
        if (!reputation.levels().isEmpty() && !reputation.levels().contains(reputationLevel)) {
            return ConditionMatch.UNMET;
        }
        if (reputation.minReputation() != null && value < reputation.minReputation()) {
            return ConditionMatch.UNMET;
        }
        if (reputation.maxReputation() != null && value > reputation.maxReputation()) {
            return ConditionMatch.UNMET;
        }
        return ConditionMatch.MET;
    }

    private static ConditionMatch socialAttributeStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.SocialAttribute socialAttribute) {
        Optional<VillagerProfile> profile = savedIssuerProfile(level, progress);
        if (profile.isEmpty()) {
            return ConditionMatch.UNKNOWN;
        }
        for (var attribute : socialAttribute.attributes()) {
            int value = profile.get().socialAttributes().get(attribute);
            if (socialAttribute.minValue() != null && value < socialAttribute.minValue()) {
                continue;
            }
            if (socialAttribute.maxValue() != null && value > socialAttribute.maxValue()) {
                continue;
            }
            return ConditionMatch.MET;
        }
        return ConditionMatch.UNMET;
    }

    private static ConditionMatch skillStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Skill skill) {
        Optional<VillagerProfile> profile = savedIssuerProfile(level, progress);
        if (profile.isEmpty()) {
            return ConditionMatch.UNKNOWN;
        }
        for (var villagerSkill : skill.skills()) {
            int value = profile.get().skills().get(villagerSkill);
            if (skill.minValue() != null && value < skill.minValue()) {
                continue;
            }
            if (skill.maxValue() != null && value > skill.maxValue()) {
                continue;
            }
            if (skill.minRank() != null && value < skill.minRank().minInclusive()) {
                continue;
            }
            if (skill.maxRank() != null && value > skill.maxRank().maxInclusive()) {
                continue;
            }
            return ConditionMatch.MET;
        }
        return ConditionMatch.UNMET;
    }

    private static Optional<VillagerProfile> savedIssuerProfile(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        return villagerId == null ? Optional.empty() : VillagerProfileManager.getProfile(level, villagerId);
    }

    private static ConditionMatch familyStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Family family) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionMatch.UNKNOWN;
        }
        VillagerFamilyTreeSnapshot snapshot = VillagerSocialGraphService.familySnapshot(level, villagerId);
        if (family.relations().isEmpty()) {
            return snapshot.hasFamily() ? ConditionMatch.MET : ConditionMatch.UNMET;
        }
        for (String relation : family.relations()) {
            if (savedFamilyRelationMatches(snapshot, relation)) {
                return ConditionMatch.MET;
            }
        }
        return ConditionMatch.UNMET;
    }

    private static boolean savedFamilyRelationMatches(VillagerFamilyTreeSnapshot snapshot, String relation) {
        return switch (relation) {
            case "family", "any" -> snapshot.hasFamily();
            case "parent" -> snapshot.hasParent();
            case "sibling" -> snapshot.hasSibling();
            case "spouse" -> snapshot.hasSpouse();
            case "child" -> snapshot.hasChild();
            case "grandparent" -> snapshot.hasGrandparent();
            case "grandchild" -> snapshot.hasGrandchild();
            case "descendant" -> snapshot.hasDescendant();
            case "aunt_uncle", "aunt_or_uncle" -> snapshot.hasAuntUncle();
            case "cousin" -> snapshot.hasCousin();
            case "niece_nephew", "niece_or_nephew" -> snapshot.hasNieceNephew();
            case "extended_family" -> snapshot.hasExtendedFamily();
            case "deceased_family" -> snapshot.hasDeceasedFamily();
            default -> false;
        };
    }

    private static ConditionMatch relationshipStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Relationship relationship) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionMatch.UNKNOWN;
        }
        VillagerRelationshipSnapshot snapshot = VillagerSocialGraphService.relationshipSnapshot(level, villagerId);
        if (relationship.states().isEmpty()) {
            return snapshot.hasRelationships() ? ConditionMatch.MET : ConditionMatch.UNMET;
        }
        for (String state : relationship.states()) {
            if (savedRelationshipStateMatches(snapshot, state)) {
                return ConditionMatch.MET;
            }
        }
        return ConditionMatch.UNMET;
    }

    private static boolean savedRelationshipStateMatches(VillagerRelationshipSnapshot snapshot, String state) {
        return switch (state) {
            case "relationship", "any" -> snapshot.hasRelationships();
            case "current", "current_relationship" -> snapshot.hasCurrentRelationship();
            case "past", "past_relationship" -> snapshot.hasPastRelationship();
            case "crush" -> snapshot.hasCrush();
            case "dating", "dating_partner" -> snapshot.hasDatingPartner();
            case "fiance", "fiancee" -> snapshot.hasFiance();
            case "romantic_spouse", "spouse" -> snapshot.hasRomanticSpouse();
            case "separated", "separated_partner" -> snapshot.hasSeparatedPartner();
            case "widowed", "widowed_partner" -> snapshot.hasWidowedPartner();
            default -> false;
        };
    }

    private static ConditionMatch recruitmentMemoryStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.RecruitmentMemory recruitmentMemory) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionMatch.UNKNOWN;
        }
        VillagerInteractionTracker.RecruitmentMemory savedMemory =
                savedRecruitmentMemory(player, level, villagerId).orElse(null);
        if (savedMemory == null) {
            return ConditionMatch.UNMET;
        }
        if (!recruitmentMemory.scenarios().isEmpty()
                && recruitmentMemory.scenarios().stream().noneMatch(scenario -> savedRecruitmentScenarioMatches(savedMemory, scenario))) {
            return ConditionMatch.UNMET;
        }
        if (!recruitmentMemory.biomeKeys().isEmpty()
                && !recruitmentMemory.biomeKeys().contains(savedRecruitmentBiomeKey(savedMemory))) {
            return ConditionMatch.UNMET;
        }
        if (recruitmentMemory.minFollowDistance() != null
                && savedMemory.distanceBlocks() < recruitmentMemory.minFollowDistance()) {
            return ConditionMatch.UNMET;
        }
        if (recruitmentMemory.boatTrip() != null
                && savedMemory.boatTrip() != recruitmentMemory.boatTrip()) {
            return ConditionMatch.UNMET;
        }
        if (recruitmentMemory.oceanCrossing() != null
                && savedMemory.oceanCrossing() != recruitmentMemory.oceanCrossing()) {
            return ConditionMatch.UNMET;
        }
        boolean swimTrip = savedMemory.oceanCrossing() && !savedMemory.boatTrip();
        if (recruitmentMemory.swimTrip() != null && swimTrip != recruitmentMemory.swimTrip()) {
            return ConditionMatch.UNMET;
        }
        if (recruitmentMemory.excludesOceanCrossing() != null
                && recruitmentMemory.excludesOceanCrossing()
                && savedMemory.oceanCrossing()) {
            return ConditionMatch.UNMET;
        }
        return ConditionMatch.MET;
    }

    private static ConditionMatch memoryStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Memory memory) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionMatch.UNKNOWN;
        }
        VillagerInteractionSavedData interactionData = VillagerInteractionSavedData.get(level);
        return switch (memory.kind()) {
            case RECENT_BROKEN_BED -> interactionData.hasRecentBrokenBedMemory(
                    villagerId,
                    player.getUUID(),
                    level.getGameTime(),
                    BROKEN_BED_MEMORY_TICKS)
                    ? ConditionMatch.MET
                    : ConditionMatch.UNMET;
            case RECENT_DIRECT_HIT -> interactionData.hasRecentDirectHitMemory(
                    villagerId,
                    player.getUUID(),
                    level.getGameTime(),
                    DIRECT_HIT_MEMORY_TICKS)
                    ? ConditionMatch.MET
                    : ConditionMatch.UNMET;
            case GEAR_REPORT_USED_IN_COMBAT -> {
                VillagerInteractionTracker.GearReport gearReport =
                        interactionData.unreportedGearReport(villagerId, player.getUUID());
                yield gearReport != null && gearReport.usedInCombat()
                        ? ConditionMatch.MET
                        : ConditionMatch.UNMET;
            }
            case GEAR_REPORT_UNUSED_IN_COMBAT -> {
                VillagerInteractionTracker.GearReport gearReport =
                        interactionData.unreportedGearReport(villagerId, player.getUUID());
                yield gearReport != null && !gearReport.usedInCombat()
                        ? ConditionMatch.MET
                        : ConditionMatch.UNMET;
            }
            case RECRUITMENT_MEMORY -> recruitmentMemoryPresentWithoutLiveContext(player, level, progress);
            case EVENT_TAG -> eventTagMemoryStateWithoutLiveContext(player, level, progress, memory, villagerId);
        };
    }

    private static ConditionMatch eventTagMemoryStateWithoutLiveContext(
            ServerPlayer player,
            ServerLevel fallbackLevel,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Memory memory,
            UUID villagerId) {
        if (memory.tags().isEmpty()) {
            return ConditionMatch.UNKNOWN;
        }
        Optional<BlockPos> queryPos = savedVillageMemoryQueryPos(progress);
        if (queryPos.isEmpty()) {
            return ConditionMatch.UNKNOWN;
        }
        ServerLevel memoryLevel = savedVillageMemoryLevel(fallbackLevel, progress);
        if (memoryLevel == null) {
            return ConditionMatch.UNKNOWN;
        }
        UUID playerId = player.getUUID();
        for (VillageEventMemory.MemoryEvent event : VillageEventMemory.recentNear(memoryLevel, queryPos.get())) {
            if (!memory.tags().contains(event.tagId())) {
                continue;
            }
            if (memory.currentPlayerOnly() && !playerId.equals(event.playerId())) {
                continue;
            }
            if (memory.source() == DialogueCondition.MemorySource.THIS_VILLAGER && !villagerId.equals(event.sourceId())) {
                continue;
            }
            if (memory.source() == DialogueCondition.MemorySource.OTHER_VILLAGER && villagerId.equals(event.sourceId())) {
                continue;
            }
            return ConditionMatch.MET;
        }
        return ConditionMatch.UNMET;
    }

    private static Optional<BlockPos> savedVillageMemoryQueryPos(VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null) {
            return Optional.empty();
        }
        Optional<BlockPos> villagePos = villageScopePos(progress.issuerVillageKey());
        return villagePos.isPresent() ? villagePos : Optional.ofNullable(progress.issuerPos());
    }

    private static ServerLevel savedVillageMemoryLevel(
            ServerLevel fallbackLevel,
            VillagerQuestSavedData.QuestProgress progress) {
        ResourceKey<Level> dimension = progress == null
                ? null
                : villageScopeDimension(progress.issuerVillageKey()).orElse(progress.issuerDimension());
        if (dimension == null || dimension.equals(fallbackLevel.dimension())) {
            return fallbackLevel;
        }
        return fallbackLevel.getServer().getLevel(dimension);
    }

    private static Optional<ResourceKey<Level>> villageScopeDimension(String scopeKey) {
        String dimension = villageScopeDimensionText(scopeKey);
        if (dimension.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(dimension);
        return dimensionId == null
                ? Optional.empty()
                : Optional.of(ResourceKey.create(Registries.DIMENSION, dimensionId));
    }

    private static Optional<BlockPos> villageScopePos(String scopeKey) {
        String pos = villageScopePosText(scopeKey);
        if (pos.isBlank()) {
            return Optional.empty();
        }
        String[] parts = pos.split(",");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String villageScopeDimensionText(String scopeKey) {
        if (scopeKey == null || !scopeKey.startsWith("village:")) {
            return "";
        }
        int separator = scopeKey.lastIndexOf(':');
        return separator <= "village:".length() ? "" : scopeKey.substring("village:".length(), separator);
    }

    private static String villageScopePosText(String scopeKey) {
        if (scopeKey == null || !scopeKey.startsWith("village:")) {
            return "";
        }
        int separator = scopeKey.lastIndexOf(':');
        return separator < 0 || separator >= scopeKey.length() - 1 ? "" : scopeKey.substring(separator + 1);
    }

    private static ConditionMatch recruitmentMemoryPresentWithoutLiveContext(
            ServerPlayer player,
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionMatch.UNKNOWN;
        }
        return savedRecruitmentMemory(player, level, villagerId).isPresent()
                ? ConditionMatch.MET
                : ConditionMatch.UNMET;
    }

    private static Optional<VillagerInteractionTracker.RecruitmentMemory> savedRecruitmentMemory(
            ServerPlayer player,
            ServerLevel level,
            UUID villagerId) {
        return Optional.ofNullable(VillagerInteractionSavedData.get(level)
                .recruitmentMemory(villagerId, player.getUUID()));
    }

    private static boolean savedRecruitmentScenarioMatches(
            VillagerInteractionTracker.RecruitmentMemory memory,
            String scenario) {
        return memory.scenario() != null && scenario != null && memory.scenario().equalsIgnoreCase(scenario);
    }

    private static String savedRecruitmentBiomeKey(VillagerInteractionTracker.RecruitmentMemory memory) {
        if (memory.biomeName() == null || memory.biomeName().isBlank()) {
            return "";
        }
        return memory.biomeName().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static ConditionMatch villagerAgeStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.VillagerAge villagerAge) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionMatch.UNKNOWN;
        }
        Optional<Boolean> savedBaby = VillagerSocialGraphService.knownBaby(level, villagerId);
        if (savedBaby.isEmpty()) {
            return ConditionMatch.UNKNOWN;
        }
        boolean isBaby = savedBaby.get();
        if (villagerAge.baby() != null && isBaby != villagerAge.baby()) {
            return ConditionMatch.UNMET;
        }
        if (villagerAge.adult() != null && isBaby == villagerAge.adult()) {
            return ConditionMatch.UNMET;
        }
        return ConditionMatch.MET;
    }

    private static ConditionMatch villagerLevelStateWithoutLiveContext(
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.VillagerLevel villagerLevel) {
        int level = progress == null ? 0 : progress.issuerLevel();
        if (level <= 0) {
            return ConditionMatch.UNKNOWN;
        }
        if (!villagerLevel.levels().isEmpty() && !villagerLevel.levels().contains(level)) {
            return ConditionMatch.UNMET;
        }
        if (villagerLevel.minLevel() != null && level < villagerLevel.minLevel()) {
            return ConditionMatch.UNMET;
        }
        if (villagerLevel.maxLevel() != null && level > villagerLevel.maxLevel()) {
            return ConditionMatch.UNMET;
        }
        return ConditionMatch.MET;
    }

    private static ConditionMatch moodStateWithoutLiveContext(
            ServerLevel level,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueCondition.Mood mood) {
        UUID villagerId = progress == null ? null : progress.startedVillagerId();
        if (villagerId == null) {
            return ConditionMatch.UNKNOWN;
        }
        VillagerMoodState state = VillagerRetaliationConfig.ENABLE_VILLAGER_MOODS.get()
                ? VillagerMoodSavedData.get(level).get(villagerId).withEffectiveDecay(level.getGameTime())
                : VillagerMoodState.DEFAULT;
        if (!mood.moods().isEmpty() && !mood.moods().contains(state.primaryMood())) {
            return ConditionMatch.UNMET;
        }
        if (mood.minIntensity() != null && state.intensity() < mood.minIntensity()) {
            return ConditionMatch.UNMET;
        }
        if (mood.maxIntensity() != null && state.intensity() > mood.maxIntensity()) {
            return ConditionMatch.UNMET;
        }
        return ConditionMatch.MET;
    }

    private static String savedQuestFactScopeKey(
            ServerPlayer player,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestFactScope scope,
            ResourceLocation questId) {
        QuestFactScope resolvedScope = scope == null ? QuestFactScope.PLAYER : scope;
        ResourceLocation resolvedQuestId = questId == null && definition != null ? definition.id() : questId;
        return switch (resolvedScope) {
            case PLAYER -> "player:" + player.getUUID();
            case WORLD -> "world";
            case QUEST -> resolvedQuestId == null ? "" : "quest:" + player.getUUID() + ":" + resolvedQuestId;
            case VILLAGER -> progress == null || progress.startedVillagerId() == null ? "" : "villager:" + progress.startedVillagerId();
            case VILLAGE -> progress == null ? "" : factVillageScopeKey(level, progress);
        };
    }

    private static DialogueContext.WeatherState savedWeatherState(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData.QuestProgress progress) {
        if (level.isThundering()) {
            return DialogueContext.WeatherState.THUNDER;
        }
        BlockPos pos = progress == null || progress.issuerPos() == null ? player.blockPosition() : progress.issuerPos();
        return level.isRainingAt(pos) ? DialogueContext.WeatherState.RAIN : DialogueContext.WeatherState.CLEAR;
    }

    private static DialogueContext.TimeOfDay savedTimeOfDay(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        if (dayTime < 6000L) {
            return DialogueContext.TimeOfDay.MORNING;
        }
        if (dayTime < 12000L) {
            return DialogueContext.TimeOfDay.AFTERNOON;
        }
        if (dayTime < 14000L) {
            return DialogueContext.TimeOfDay.EVENING;
        }
        return DialogueContext.TimeOfDay.NIGHT;
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
            return expiration.conditions().stream().allMatch(condition -> condition.matches(context))
                    ? ConditionMatch.MET
                    : ConditionMatch.UNMET;
        }
        return conditionsStateWithoutLiveContext(player, level, definition, progress, expiration.conditions());
    }

    private static Optional<DialogueContext> contextForStartedVillager(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData.QuestProgress progress) {
        Villager villager = startedVillager(level, progress);
        if (villager == null || !villager.isAlive()) {
            return Optional.empty();
        }
        return Optional.of(VillagerInteractionService.createDialogueContext(level, player, villager));
    }

    private static boolean activeConditionsMetForPlayer(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return activeConditionsStateForPlayer(player, definition, progress) == ConditionMatch.MET;
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
                && progress.startedGameTime() > 0L
                && gameTime - progress.startedGameTime() >= expiration.afterTicks();
        ConditionMatch expirationConditions =
                expirationConditionsState(player, context, player.serverLevel(), definition, progress);
        if (!expiredByTime && expirationConditions != ConditionMatch.MET) {
            return false;
        }

        progress.expire(gameTime, expiration.consume());
        if (player.level() instanceof ServerLevel level) {
            markQuestLifecycleFact(level, player, definition, QUEST_EXPIRED_FACT, "expired");
        }
        if (expiration.sendNotification()) {
            sendQuestExpiredNotification(player, context, definition, progress);
        }
        if (context != null) {
            dispatchQuestTriggers(context, definition, progress, QuestDefinition.TriggerEvent.EXPIRED);
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
            return resolveGlobalText(
                    context.player(),
                    "quest.dialogue.branch_locked",
                    "That path has closed because of another choice.",
                    replacements(context, definition, progress));
        }
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.CONSUMED) {
            return resolveGlobalText(
                    context.player(),
                    "quest.dialogue.consumed",
                    "That path is no longer available.",
                    replacements(context, definition, progress));
        }
        if (!parentCompleted(context, definition)) {
            return resolveGlobalText(
                    context.player(),
                    "quest.dialogue.parent_locked",
                    "Another chapter needs to be settled before this opens.",
                    replacements(context, definition, progress));
        }
        if (crossVillagerLocked(context, definition, progress)) {
            return resolveGlobalText(
                    context.player(),
                    "quest.dialogue.locked_to_villager",
                    "That quest belongs to {issuer}.",
                    replacements(context, definition, progress));
        }
        if (progress != null && !withinStartLimit(definition, progress)) {
            return resolveGlobalText(
                    context.player(),
                    "quest.dialogue.start_limit",
                    "That path cannot be started again.",
                    replacements(context, definition, progress));
        }
        if (progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ABANDONED
                && definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN
                && !cooldownElapsed(context.level().getGameTime(), progress.abandonedGameTime(), definition.rules().abandonmentCooldownTicks())) {
            return resolveGlobalText(
                    context.player(),
                    "quest.dialogue.abandoned_cooldown",
                    "Give that path a little time, then return to {issuer}.",
                    replacements(context, definition, progress));
        }
        if (completionCooldownActive(context, definition, progress)) {
            return resolveGlobalText(
                    context.player(),
                    "quest.dialogue.completion_cooldown",
                    "That matter needs time before it can be revisited.",
                    replacements(context, definition, progress));
        }
        if (!withinCompletionLimit(context, definition, progress)
                && !isPlayerCompletionScope(definition.rules().completionScope())) {
            return resolveGlobalText(
                    context.player(),
                    "quest.dialogue.completion_scope",
                    "Someone has already settled this matter.",
                    replacements(context, definition, progress));
        }
        if ((progress != null && progress.completionCount() > 0)
                || (!isPlayerCompletionScope(definition.rules().completionScope())
                && scopedCompletionCount(context, definition) > 0)
                || !withinCompletionLimit(context, definition, progress)) {
            return resolveQuestText(
                    context,
                    definition.dialogue().selectAlreadyCompletedText(context.random()),
                    replacements(context, definition, progress));
        }
        return resolveQuestText(
                context,
                definition.dialogue().selectUnavailableText(context.random()),
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
                && !progress.startedVillagerId().equals(context.villager().getUUID());
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
        boolean completed = progress != null && progress.completionCount() > 0;
        if (!completed && !isPlayerCompletionScope(definition.rules().completionScope())) {
            completed = scopedCompletionCount(context, definition) > 0;
        }
        boolean rawActive = progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && matchesVillagerLock(context, definition, progress);
        boolean activeConditionsMet = rawActive && activeConditionsMet(context, definition);
        boolean active = rawActive && (activeConditionsMet || !definition.rules().activeState().hideWhenUnmet());
        boolean ready = activeConditionsMet && isReadyToTurnIn(context, definition, progress);
        boolean notStarted = progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED;
        boolean abandoned = progress != null && progress.state() == VillagerQuestSavedData.QuestState.ABANDONED;
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
        return progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && activeConditionsMet(context, definition)
                && (!definition.target().hasStructureTarget() || progress.visitedTarget())
                && hasRequiredProof(context.player(), definition)
                && requiredObjectivesComplete(context.player(), context, definition, progress);
    }

    private static boolean hasRequiredProof(ServerPlayer player, QuestDefinition definition) {
        if (!definition.target().hasProofItem()) {
            return true;
        }
        return hasItemCount(player, definition.target().proofItem(), 1);
    }

    private static boolean hasItemCount(ServerPlayer player, ResourceLocation itemId, int count) {
        return itemCount(player, itemId) >= Math.max(1, count);
    }

    private static boolean hasItemCount(ServerPlayer player, QuestDefinition.Objective objective) {
        return itemCount(player, objective) >= Math.max(1, objective.count());
    }

    private static int itemCount(ServerPlayer player, ResourceLocation itemId) {
        if (itemId == null) {
            return 0;
        }
        return cachedInventoryItemCounts(player).getOrDefault(itemId, 0);
    }

    private static int itemCount(ServerPlayer player, QuestDefinition.Objective objective) {
        if (objective.item() == null) {
            return 0;
        }
        if (hasSimpleItemRequirements(objective.itemRequirements())) {
            return itemCount(player, objective.item());
        }
        InventoryItemCountCache cache = cachedInventoryCache(player);
        InventoryObjectiveCountKey cacheKey = InventoryObjectiveCountKey.of(objective);
        Integer cached = cache.objectiveCounts().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(objective.item());
        if (item.isEmpty()) {
            cache.objectiveCounts().put(cacheKey, 0);
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (matchesObjectiveItem(stack, objective, item.get())) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (matchesObjectiveItem(stack, objective, item.get())) {
                total += stack.getCount();
            }
        }
        cache.objectiveCounts().put(cacheKey, total);
        return total;
    }

    private static Map<ResourceLocation, Integer> cachedInventoryItemCounts(ServerPlayer player) {
        return cachedInventoryCache(player).counts();
    }

    private static InventoryItemCountCache cachedInventoryCache(ServerPlayer player) {
        int changeCount = player.getInventory().getTimesChanged();
        InventoryItemCountCache cache = INVENTORY_ITEM_COUNT_CACHES.get(player.getUUID());
        if (cache != null && cache.changeCount() == changeCount) {
            return cache;
        }

        Map<ResourceLocation, Integer> counts = new HashMap<>();
        addInventoryItemCounts(counts, player.getInventory().items);
        addInventoryItemCounts(counts, player.getInventory().offhand);
        Map<ResourceLocation, Integer> immutableCounts = Map.copyOf(counts);
        InventoryItemCountCache updated = new InventoryItemCountCache(changeCount, immutableCounts, new HashMap<>());
        INVENTORY_ITEM_COUNT_CACHES.put(player.getUUID(), updated);
        return updated;
    }

    private static void addInventoryItemCounts(Map<ResourceLocation, Integer> counts, Iterable<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null) {
                counts.merge(itemId, stack.getCount(), Integer::sum);
            }
        }
    }

    private static boolean hasSimpleItemRequirements(QuestDefinition.ItemRequirements requirements) {
        return requirements == null
                || (requirements.enchantments().isEmpty()
                && requirements.minDurability().isEmpty()
                && requirements.maxDurability().isEmpty()
                && requirements.minDurabilityPercent().isEmpty()
                && requirements.maxDurabilityPercent().isEmpty()
                && !requirements.hasCustomData());
    }

    private static boolean updateObjectiveProgress(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueContext context) {
        if (definition.objectives().isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (progress.objectiveComplete(objective.id())) {
                continue;
            }
            if (objectiveComplete(player, context, level, definition, progress, objective)) {
                boolean newlyComplete = progress.markObjectiveComplete(objective.id());
                changed |= newlyComplete;
                if (newlyComplete) {
                    markQuestObjectiveFact(level, player, definition, objective);
                }
                if (objective.id().equals(progress.targetObjectiveId())) {
                    progress.setTarget(progress.startedVillagerId(), progress.targetDimension(), null, "");
                }
            }
        }
        /*
         * Do not locate generated structures from player tick. Vanilla nearest/generated-structure
         * searches can synchronously enter worldgen/StructureCheck while players are exploring.
         */
        return changed;
    }

    private static boolean updateMobKillProgress(
            ServerLevel level,
            ServerPlayer player,
            LivingEntity killed,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        boolean changed = false;
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.type() != QuestDefinition.ObjectiveType.MOB_KILL || progress.objectiveComplete(objective.id())) {
                continue;
            }
            if (!matchesMobKillObjective(level, player, killed, objective)) {
                continue;
            }
            int count = progress.addObjectiveCounter(objective.id(), 1);
            changed = true;
            if (count >= objective.count()) {
                if (progress.markObjectiveComplete(objective.id())) {
                    markQuestObjectiveFact(level, player, definition, objective);
                }
            }
        }
        return changed;
    }

    private static boolean updateBlockEventProgress(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos,
            BlockState state,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.ObjectiveType type) {
        boolean changed = false;
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.type() != type || progress.objectiveComplete(objective.id())) {
                continue;
            }
            if (!matchesBlockObjective(level, pos, state, objective)) {
                continue;
            }
            int count = progress.addObjectiveCounter(objective.id(), 1);
            changed = true;
            if (count >= objective.count()) {
                if (progress.markObjectiveComplete(objective.id())) {
                    markQuestObjectiveFact(level, player, definition, objective);
                }
            }
        }
        return changed;
    }

    private static boolean updateMemoryEventProgress(
            ServerLevel level,
            ServerPlayer player,
            VillageEventMemory.MemoryEvent event,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        boolean changed = false;
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.type() != QuestDefinition.ObjectiveType.MEMORY_EVENT || progress.objectiveComplete(objective.id())) {
                continue;
            }
            if (!matchesMemoryEventObjective(level, player, event, objective)) {
                continue;
            }
            int count = progress.addObjectiveCounter(objective.id(), 1);
            changed = true;
            if (count >= objective.count()) {
                if (progress.markObjectiveComplete(objective.id())) {
                    markQuestObjectiveFact(level, player, definition, objective);
                }
            }
        }
        return changed;
    }

    private static boolean updateGiftProgress(
            ServerLevel level,
            ServerPlayer player,
            ItemStack giftedStack,
            VillagerGiftPreferences.GiftReaction reaction,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        boolean changed = false;
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.type() != QuestDefinition.ObjectiveType.GIFT || progress.objectiveComplete(objective.id())) {
                continue;
            }
            if (!matchesGiftObjective(giftedStack, reaction, objective)) {
                continue;
            }
            int count = progress.addObjectiveCounter(objective.id(), 1);
            changed = true;
            if (count >= objective.count()) {
                if (progress.markObjectiveComplete(objective.id())) {
                    markQuestObjectiveFact(level, player, definition, objective);
                }
            }
        }
        return changed;
    }

    private static boolean updateTradeProgress(
            ServerLevel level,
            ServerPlayer player,
            AbstractVillager villager,
            MerchantOffer offer,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        boolean changed = false;
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.type() != QuestDefinition.ObjectiveType.TRADE || progress.objectiveComplete(objective.id())) {
                continue;
            }
            if (!matchesTradeObjective(level, villager, offer, objective)) {
                continue;
            }
            int count = progress.addObjectiveCounter(objective.id(), 1);
            changed = true;
            if (count >= objective.count()) {
                if (progress.markObjectiveComplete(objective.id())) {
                    markQuestObjectiveFact(level, player, definition, objective);
                }
            }
        }
        return changed;
    }

    private static boolean requiredObjectivesComplete(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        List<QuestDefinition.Objective> requiredItemHandIns = new ArrayList<>();
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.optional()) {
                continue;
            }
            if (objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK && objective.consume() && objective.item() != null) {
                requiredItemHandIns.add(objective);
                continue;
            }
            if (!objectiveComplete(player, context, context.level(), definition, progress, objective)) {
                return false;
            }
        }
        return requiredItemHandIns.isEmpty() || previewObjectiveItemStacks(player, requiredItemHandIns).isPresent();
    }

    private static boolean objectiveComplete(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        if (objective.type() != QuestDefinition.ObjectiveType.ITEM_CHECK && progress.objectiveComplete(objective.id())) {
            return true;
        }
        return switch (objective.type()) {
            case STRUCTURE_VISIT -> VillagerQuestTargets.isAtObjectiveTarget(level, player.blockPosition(), objective, progress);
            case LOCATION_VISIT -> isAtLocationObjective(level, player.blockPosition(), objective);
            case ITEM_CHECK -> hasItemCount(player, objective);
            case MOB_KILL -> progress != null && progress.objectiveCounter(objective.id()) >= objective.count();
            case BLOCK_BREAK, BLOCK_PLACE, BLOCK_INTERACT, MEMORY_EVENT, TRADE, GIFT -> progress != null && progress.objectiveCounter(objective.id()) >= objective.count();
            case REPUTATION -> progress != null && matchesReputationObjective(level, player, progress, objective);
            case CHOICE, FACT -> progress != null && matchesFactObjective(level, player, definition, progress, objective);
            case CONDITION -> objectiveConditionState(player, context, level, definition, progress, objective) == ConditionMatch.MET;
        };
    }

    private static ConditionMatch objectiveConditionState(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        if (context != null) {
            return objective.conditions().stream().allMatch(condition -> condition.matches(context))
                    ? ConditionMatch.MET
                    : ConditionMatch.UNMET;
        }
        return conditionsStateWithoutLiveContext(player, level, definition, progress, objective.conditions());
    }

    private static boolean isAtLocationObjective(ServerLevel level, BlockPos playerPos, QuestDefinition.Objective objective) {
        if (objective.location() == null) {
            return false;
        }
        if (objective.dimension() != null && level.dimension() != objective.dimension()) {
            return false;
        }
        double radius = Math.max(1, objective.radius());
        return playerPos.distSqr(objective.location()) <= radius * radius;
    }

    private static boolean matchesMobKillObjective(
            ServerLevel level,
            ServerPlayer player,
            LivingEntity killed,
            QuestDefinition.Objective objective) {
        if (objective.dimension() != null && level.dimension() != objective.dimension()) {
            return false;
        }
        if (objective.location() != null) {
            double radius = Math.max(1, objective.radius());
            if (killed.blockPosition().distSqr(objective.location()) > radius * radius) {
                return false;
            }
        }
        if (objective.entityTypes().isEmpty() && objective.entityTags().isEmpty()) {
            return false;
        }
        ResourceLocation killedType = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType());
        if (killedType != null && objective.entityTypes().contains(killedType)) {
            return true;
        }
        for (ResourceLocation tagId : objective.entityTags()) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, tagId);
            if (killed.getType().is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesBlockObjective(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            QuestDefinition.Objective objective) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (objective.dimension() != null && level.dimension() != objective.dimension()) {
            return false;
        }
        if (objective.location() != null) {
            double radius = Math.max(1, objective.radius());
            if (pos.distSqr(objective.location()) > radius * radius) {
                return false;
            }
        }
        if (objective.blockTypes().isEmpty() && objective.blockTags().isEmpty()) {
            return false;
        }
        ResourceLocation blockType = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockType != null && objective.blockTypes().contains(blockType)) {
            return true;
        }
        for (ResourceLocation tagId : objective.blockTags()) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesMemoryEventObjective(
            ServerLevel level,
            ServerPlayer player,
            VillageEventMemory.MemoryEvent event,
            QuestDefinition.Objective objective) {
        if (event == null || event.tagId() == null || event.pos() == null) {
            return false;
        }
        if (!player.getUUID().equals(event.playerId())) {
            return false;
        }
        if (objective.dimension() != null && level.dimension() != objective.dimension()) {
            return false;
        }
        if (objective.location() != null) {
            double radius = Math.max(1, objective.radius());
            if (event.pos().distSqr(objective.location()) > radius * radius) {
                return false;
            }
        }
        return objective.memoryTags().contains(event.tagId());
    }

    private static boolean matchesTradeObjective(
            ServerLevel level,
            AbstractVillager villager,
            MerchantOffer offer,
            QuestDefinition.Objective objective) {
        if (villager == null || offer == null) {
            return false;
        }
        if (objective.dimension() != null && level.dimension() != objective.dimension()) {
            return false;
        }
        if (objective.location() != null) {
            double radius = Math.max(1, objective.radius());
            if (villager.blockPosition().distSqr(objective.location()) > radius * radius) {
                return false;
            }
        }
        if (objective.item() == null) {
            return true;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(objective.item());
        return item.isPresent() && matchesObjectiveItem(offer.getResult(), objective, item.get());
    }

    private static boolean matchesReputationObjective(
            ServerLevel level,
            ServerPlayer player,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        if (level == null || player == null || progress == null || progress.startedVillagerId() == null) {
            return false;
        }
        int reputation = reputationForObjective(level, player, progress);
        VillagerReputationLevel reputationLevel = VillagerReputationLevel.fromReputation(reputation);
        if (!objective.reputationLevels().isEmpty() && !objective.reputationLevels().contains(reputationLevel)) {
            return false;
        }
        if (objective.minReputation() != null && reputation < objective.minReputation()) {
            return false;
        }
        return objective.maxReputation() == null || reputation <= objective.maxReputation();
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

    private static boolean matchesGiftObjective(
            ItemStack giftedStack,
            VillagerGiftPreferences.GiftReaction reaction,
            QuestDefinition.Objective objective) {
        if (giftedStack == null || giftedStack.isEmpty() || reaction == null) {
            return false;
        }
        if (!objective.giftReactions().isEmpty()
                && !objective.giftReactions().contains(reaction.name().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (objective.item() == null) {
            return true;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(objective.item());
        return item.isPresent() && matchesObjectiveItem(giftedStack, objective, item.get());
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
        return switch (scope) {
            case PLAYER -> "player:" + player.getUUID();
            case WORLD -> "world";
            case QUEST -> questId == null ? "" : "quest:" + player.getUUID() + ":" + questId;
            case VILLAGER -> progress.startedVillagerId() == null ? "" : "villager:" + progress.startedVillagerId();
            case VILLAGE -> factVillageScopeKey(level, progress);
        };
    }

    private static String factVillageScopeKey(ServerLevel level, VillagerQuestSavedData.QuestProgress progress) {
        if (!progress.issuerVillageKey().isBlank()) {
            return progress.issuerVillageKey();
        }
        Villager villager = startedVillager(level, progress);
        if (villager != null && villager.isAlive()) {
            return liveVillageScopeKey(level, villager);
        }
        if (progress.issuerPos() == null) {
            return "";
        }
        ResourceKey<Level> issuerDimension = progress.issuerDimension() == null
                ? level.dimension()
                : progress.issuerDimension();
        return "village:" + issuerDimension.location() + ":" + factPosKey(progress.issuerPos());
    }

    private static String liveVillageScopeKey(ServerLevel level, Villager villager) {
        String dimension = level.dimension().location().toString();
        return VillageMembership.resolve(level, villager)
                .map(area -> "village:" + dimension + ":" + factPosKey(area.centerBlock()))
                .or(() -> VillagerSocialGraphService.knownVillage(level, villager.getUUID())
                        .map(VillagerQuestService::savedSocialVillageScopeKey)
                        .filter(key -> !key.isBlank()))
                .orElseGet(() -> "village:" + dimension + ":" + factPosKey(villager.blockPosition()));
    }

    private static String savedSocialVillageScopeKey(String savedVillageKey) {
        if (savedVillageKey == null || savedVillageKey.isBlank()) {
            return "";
        }
        if (savedVillageKey.startsWith("village:")) {
            return savedVillageKey;
        }
        int separator = savedVillageKey.indexOf('@');
        if (separator <= 0 || separator >= savedVillageKey.length() - 1) {
            return "";
        }
        return "village:" + savedVillageKey.substring(0, separator) + ":" + savedVillageKey.substring(separator + 1);
    }

    private static String factPosKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static Optional<QuestDefinition.Objective> firstIncompleteRequiredObjective(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (!objective.optional() && !objectiveComplete(player, context, level, definition, progress, objective)) {
                return Optional.of(objective);
            }
        }
        return Optional.empty();
    }

    private static ItemHandInResult handInRequiredObjectiveItems(DialogueContext context, QuestDefinition definition) {
        List<QuestDefinition.Objective> requiredItemHandIns = new ArrayList<>();
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (!objective.optional()
                    && objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK
                    && objective.consume()
                    && objective.item() != null) {
                requiredItemHandIns.add(objective);
            }
        }
        if (requiredItemHandIns.isEmpty()) {
            return ItemHandInResult.SUCCESS;
        }
        List<ItemStack> handInStacks = previewObjectiveItemStacks(context.player(), requiredItemHandIns)
                .orElse(null);
        if (handInStacks == null) {
            return ItemHandInResult.MISSING_ITEMS;
        }
        if (!VillagerInventoryAccess.canAddItems(context.villager(), handInStacks)) {
            return ItemHandInResult.NO_ROOM;
        }
        if (!removeSpecificPlayerStacks(context.player(), handInStacks)) {
            return ItemHandInResult.MISSING_ITEMS;
        }
        for (ItemStack stack : handInStacks) {
            ItemStack remainder = VillagerInventoryAccess.addItem(context.villager(), stack.copy());
            if (!remainder.isEmpty()) {
                context.player().addItem(remainder);
            }
        }
        return ItemHandInResult.SUCCESS;
    }

    private static Optional<List<ItemStack>> previewObjectiveItemStacks(
            ServerPlayer player,
            List<QuestDefinition.Objective> requiredObjectives) {
        List<ItemStack> handInStacks = new ArrayList<>();
        List<ItemStack> availableStacks = removablePlayerStacks(player).stream()
                .map(ItemStack::copy)
                .toList();
        for (QuestDefinition.Objective objective : requiredObjectives) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(objective.item());
            if (item.isEmpty()) {
                return Optional.empty();
            }
            int remaining = objective.count();
            for (ItemStack stack : availableStacks) {
                if (remaining <= 0) {
                    break;
                }
                if (!matchesObjectiveItem(stack, objective, item.get())) {
                    continue;
                }
                int removed = Math.min(remaining, stack.getCount());
                handInStacks.add(stack.copyWithCount(removed));
                stack.shrink(removed);
                remaining -= removed;
            }
            if (remaining > 0) {
                return Optional.empty();
            }
        }
        return Optional.of(List.copyOf(handInStacks));
    }

    private static boolean matchesObjectiveItem(
            ItemStack stack,
            QuestDefinition.Objective objective,
            Item item) {
        if (stack.isEmpty() || !stack.is(item)) {
            return false;
        }
        QuestDefinition.ItemRequirements requirements = objective.itemRequirements();
        if (!requirements.enchantments().isEmpty()
                && requirements.enchantments().stream().anyMatch(requirement -> !matchesEnchantment(stack, requirement))) {
            return false;
        }
        if ((requirements.minDurability().isPresent()
                || requirements.maxDurability().isPresent()
                || requirements.minDurabilityPercent().isPresent()
                || requirements.maxDurabilityPercent().isPresent())
                && !matchesDurability(stack, requirements)) {
            return false;
        }
        if (requirements.hasCustomData() && !matchesCustomData(stack, requirements.customData())) {
            return false;
        }
        return true;
    }

    private static boolean matchesEnchantment(ItemStack stack, QuestDefinition.EnchantmentRequirement requirement) {
        int level = Math.max(
                enchantmentLevel(stack.getEnchantments(), requirement.id()),
                enchantmentLevel(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY), requirement.id()));
        return level > 0
                && requirement.minLevel().stream().allMatch(min -> level >= min)
                && requirement.maxLevel().stream().allMatch(max -> level <= max);
    }

    private static int enchantmentLevel(ItemEnchantments enchantments, ResourceLocation id) {
        int level = 0;
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.unwrapKey().map(key -> key.location().equals(id)).orElse(false)) {
                level = Math.max(level, entry.getIntValue());
            }
        }
        return level;
    }

    private static boolean matchesDurability(ItemStack stack, QuestDefinition.ItemRequirements requirements) {
        int maximum = stack.isDamageableItem() ? Math.max(0, stack.getMaxDamage()) : 0;
        int remaining = stack.isDamageableItem() ? Math.max(0, maximum - stack.getDamageValue()) : 0;
        int percent = maximum <= 0 ? 0 : Math.round(remaining * 100.0F / maximum);
        return requirements.minDurability().stream().allMatch(min -> remaining >= min)
                && requirements.maxDurability().stream().allMatch(max -> remaining <= max)
                && requirements.minDurabilityPercent().stream().allMatch(min -> percent >= min)
                && requirements.maxDurabilityPercent().stream().allMatch(max -> percent <= max);
    }

    private static boolean matchesCustomData(ItemStack stack, CompoundTag requiredData) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return containsTagSubset(customData.copyTag(), requiredData);
    }

    private static boolean containsTagSubset(CompoundTag actual, CompoundTag required) {
        for (String key : required.getAllKeys()) {
            Tag requiredChild = required.get(key);
            Tag actualChild = actual.get(key);
            if (requiredChild == null || actualChild == null) {
                return false;
            }
            if (requiredChild instanceof CompoundTag requiredCompound) {
                if (!(actualChild instanceof CompoundTag actualCompound)
                        || !containsTagSubset(actualCompound, requiredCompound)) {
                    return false;
                }
                continue;
            }
            if (requiredChild instanceof NumericTag requiredNumber && actualChild instanceof NumericTag actualNumber) {
                if (Double.compare(requiredNumber.getAsDouble(), actualNumber.getAsDouble()) != 0) {
                    return false;
                }
                continue;
            }
            if (!requiredChild.equals(actualChild)) {
                return false;
            }
        }
        return true;
    }

    private static boolean removeSpecificPlayerStacks(ServerPlayer player, List<ItemStack> handInStacks) {
        if (!canRemoveSpecificPlayerStacks(player, handInStacks)) {
            return false;
        }
        for (ItemStack handInStack : handInStacks) {
            int remaining = handInStack.getCount();
            for (ItemStack stack : removablePlayerStacks(player)) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, handInStack)) {
                    continue;
                }
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
        }
        player.getInventory().setChanged();
        return true;
    }

    private static boolean canRemoveSpecificPlayerStacks(ServerPlayer player, List<ItemStack> handInStacks) {
        List<ItemStack> availableStacks = removablePlayerStacks(player).stream()
                .map(ItemStack::copy)
                .toList();
        for (ItemStack handInStack : handInStacks) {
            int remaining = handInStack.getCount();
            for (ItemStack stack : availableStacks) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, handInStack)) {
                    continue;
                }
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> removablePlayerStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    private static void awardRewards(DialogueContext context, QuestDefinition definition) {
        QuestDefinition.Rewards rewards = definition.rewards();
        VillagerActionExecutor.awardExperience(context, rewards.experience());
        VillagerActionExecutor.changeReputation(context, rewards.reputation());
        VillagerActionExecutor.spreadGossip(context, rewards.gossipReputation());
        VillagerActionExecutor.rememberMemory(context, rewards.memoryEvent());
        VillagerActionExecutor.giveLoot(context, rewards.lootTable());
        context.villager().playSound(SoundEvents.PLAYER_LEVELUP, 0.55F, 1.1F);
    }

    private static void rememberQuestStoryHint(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestTargets.LocatedTarget target) {
        if (!definition.target().hasStructureTarget()) {
            return;
        }
        VillagerInteractionTracker.rememberStoryHint(
                context.level(),
                context.villager(),
                context.player(),
                VillagerInteractionTracker.StoryHintKind.STRUCTURE,
                definition.target().structure(),
                targetName(definition),
                target.pos(),
                target.dimension().location(),
                context.level().getGameTime() + QUEST_STORY_HINT_TICKS,
                definition.target().discoveryRadius()
        );
    }

    private static boolean dispatchQuestTriggers(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.TriggerEvent event) {
        if (!(player.level() instanceof ServerLevel level)
                || !VillagerQuestResources.hasQuestTrigger(level.getServer(), definition.id(), event)) {
            return false;
        }
        Villager villager = startedVillager(level, progress);
        if (villager == null || !villager.isAlive()) {
            return false;
        }
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        return dispatchQuestTriggers(context, definition, progress, event);
    }

    private static boolean dispatchQuestTriggers(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.TriggerEvent event) {
        if (progress == null
                || !VillagerQuestResources.hasQuestTrigger(context.level().getServer(), definition.id(), event)) {
            return false;
        }

        boolean dirty = false;
        for (QuestDefinition.Trigger trigger : definition.triggers()) {
            if (trigger.event() != event || !questTriggerMatches(context, progress, trigger)) {
                continue;
            }
            if (runQuestTriggerActions(context, definition, progress, trigger)) {
                progress.markTriggerUsed(trigger.id(), context.level().getGameTime());
                dirty = true;
            }
        }
        return dirty;
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
            QuestDefinition definition,
            long gameTime) {
        for (QuestDefinition.Trigger trigger : definition.triggers()) {
            if (trigger.repeatable() && trigger.event().isContinuous() && trigger.cooldownTicks() > 0L) {
                progress.markTriggerUsed(trigger.id(), gameTime);
            }
        }
    }

    private static boolean questTriggerMatches(
            DialogueContext context,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Trigger trigger) {
        if (trigger.event() == QuestDefinition.TriggerEvent.PROXIMITY) {
            double radius = trigger.radius();
            if (context.player().distanceToSqr(context.villager()) > radius * radius) {
                return false;
            }
        }
        if (!trigger.stages().isEmpty() && !trigger.stages().contains(progress.currentStage())) {
            return false;
        }
        long lastTriggered = progress.lastTriggerGameTime(trigger.id());
        if (!trigger.repeatable() && lastTriggered > 0L) {
            return false;
        }
        if (trigger.cooldownTicks() > 0L) {
            if (lastTriggered > 0L && context.level().getGameTime() - lastTriggered < trigger.cooldownTicks()) {
                return false;
            }
            if (lastTriggered <= 0L
                    && trigger.event().isContinuous()
                    && progress.startedGameTime() > 0L
                    && context.level().getGameTime() - progress.startedGameTime() < trigger.cooldownTicks()) {
                return false;
            }
        }
        for (DialogueCondition condition : trigger.conditions()) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
    }

    private static boolean runQuestTriggerActions(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Trigger trigger) {
        boolean ranAction = false;
        Map<String, String> replacements = new LinkedHashMap<>(replacements(context, definition, progress));
        for (VillagerActionDefinition action : trigger.actions()) {
            VillagerActionResult result = VillagerActionExecutor.execute(context, action, replacements);
            replacements.putAll(result.replacements());
            if (result.flashTracker()) {
                sendTrackerSync(context.player(), true);
            }
            ranAction |= result.ran();
        }
        return ranAction;
    }

    private static void sendQuestNotification(
            DialogueContext context,
            String trigger,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String fallbackText) {
        Map<String, String> replacements = replacements(context, definition, progress);
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
        QuestDefinition.Expiration expiration = definition.rules().expiration();
        if (context != null) {
            Map<String, String> replacements = replacements(context, definition, progress);
            VillagerNotifications.sendHud(
                    context.player(),
                    context.level(),
                    context.villager(),
                    expiration.notificationTrigger(),
                    replacements,
                    resolveQuestText(
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
                resolveQuestText(
                        player,
                        new QuestDefinition.SelectedText(expiration.notificationText(), expiration.notificationTextKey()),
                        replacements),
                VillagerReputationNoticeKind.QUEST);
    }

    private static void sendQuestIssuerDeathNotification(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        Map<String, String> replacements = trackerReplacements(player, definition, progress, true);
        VillagerReputationNetworking.sendNotice(
                player,
                resolveGlobalText(player, "quest.expired", "Quest expired: {quest}", replacements),
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

    private static void sendTrackerSync(ServerPlayer player, boolean flash, boolean force) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        ResourceLocation trackedQuestId = data.getTrackedQuest(player.getUUID());
        if (trackedQuestId != null && !canTrackQuest(level, player, trackedQuestId)) {
            data.clearTrackedQuest(player.getUUID());
            trackedQuestId = null;
        }
        List<Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress>> visible =
                new ArrayList<>(data.progress(player.getUUID()));
        visible.removeIf(entry -> !shouldSyncTrackerEntry(level, entry.getKey(), entry.getValue()));
        visible.sort(Comparator
                .comparingInt((Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry) ->
                        entry.getValue().state() == VillagerQuestSavedData.QuestState.ACTIVE ? 0 : 1)
                .thenComparing(Comparator
                        .comparingLong((Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry) ->
                                entry.getValue().startedGameTime())
                        .reversed()));

        List<QuestTrackerSyncPayload.Entry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : visible) {
            if (entries.size() >= QuestTrackerSyncPayload.MAX_SYNC_ENTRIES) {
                break;
            }
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition != null) {
                DialogueContext questContext = contextForStartedVillager(level, player, entry.getValue()).orElse(null);
                ConditionMatch activeConditions = activeConditionsState(questContext, player, level, definition, entry.getValue());
                if (activeConditions == ConditionMatch.UNMET && definition.rules().activeState().hideWhenUnmet()) {
                    continue;
                }
                entries.add(trackerEntry(player, questContext, definition, entry.getValue(), activeConditions));
            }
        }
        String signature = trackerSyncSignature(entries, trackedQuestId);
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
            return;
        }
        PacketDistributor.sendToPlayer(player, new QuestTrackerSyncPayload(
                entries,
                trackedQuestId == null ? "" : trackedQuestId.toString(),
                flash));
        LAST_TRACKER_SYNCS.put(player.getUUID(), new TrackerSyncState(signature, gameTime));
    }

    private static String trackerSyncSignature(
            List<QuestTrackerSyncPayload.Entry> entries,
            ResourceLocation trackedQuestId) {
        StringBuilder builder = new StringBuilder();
        builder.append(trackedQuestId == null ? "" : trackedQuestId.toString()).append('\n');
        for (QuestTrackerSyncPayload.Entry entry : entries) {
            builder.append(entry.questId()).append('|')
                    .append(entry.title()).append('|')
                    .append(entry.objective()).append('|')
                    .append(entry.metadata()).append('|')
                    .append(entry.progress()).append('|')
                    .append(entry.showProgress()).append('|')
                    .append(entry.state()).append('|')
                    .append(entry.status()).append('|')
                    .append(entry.issuer()).append('|')
                    .append(entry.issuerLocation()).append('|');
            for (QuestTrackerSyncPayload.QuestItem item : entry.questItems()) {
                builder.append(item.itemId()).append(',')
                        .append(item.label()).append(',')
                        .append(item.count()).append(';');
            }
            builder.append('\n');
        }
        return builder.toString();
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
            case ABANDONED -> definition.rules().abandonment() != QuestDefinition.AbandonmentMode.REMOVE_FOREVER
                    && !definition.rules().consumeOnAbandonment();
            case EXPIRED -> definition.rules().expiration().allowRepickup();
            case NOT_STARTED, COMPLETED, CONSUMED -> false;
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
        if (progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            ConditionMatch activeConditions = activeConditionsStateForPlayer(player, definition, progress);
            return activeConditions != ConditionMatch.UNMET || !definition.rules().activeState().hideWhenUnmet();
        }
        return shouldSyncTrackerEntry(level, questId, progress);
    }

    private static void clearTrackedQuestIf(
            VillagerQuestSavedData data,
            ServerPlayer player,
            ResourceLocation questId) {
        if (questId != null && questId.equals(data.getTrackedQuest(player.getUUID()))) {
            data.clearTrackedQuest(player.getUUID());
        }
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
        Map<String, String> replacements = trackerReplacements(player, context, definition, progress, currentObjective, activeConditionsMet);
        QuestDefinition.Step fallback = new QuestDefinition.Step(
                trackerFallbackText(stepKey),
                trackerFallbackTextKey(stepKey),
                progress.state() == VillagerQuestSavedData.QuestState.ACTIVE,
                trackerFallbackProgress(stepKey),
                Map.of()
        );
        boolean objectiveTracker = progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && currentObjective != null
                && objectiveTrackerHasDisplay(currentObjective, currentObjectiveComplete);
        QuestDefinition.Step step = objectiveTracker
                ? objectiveTrackerStep(currentObjective, fallback, currentObjectiveComplete)
                : definition.tracker().step(stepKey, fallback);
        boolean configuredStep = objectiveTracker
                || (progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && definition.tracker().steps().containsKey(stepKey));
        float progressValue = configuredStep && step.progress() >= 0.0F ? step.progress() : trackerFallbackProgress(stepKey);
        boolean showProgress = configuredStep ? step.showProgress() : progress.state() == VillagerQuestSavedData.QuestState.ACTIVE;
        QuestDefinition.SelectedText title = definition.tracker().title().isBlank() && definition.tracker().titleKey().isBlank()
                ? new QuestDefinition.SelectedText(definition.title(), definition.titleKey())
                : new QuestDefinition.SelectedText(definition.tracker().title(), definition.tracker().titleKey());
        String issuer = issuerSummary(player, progress);
        String issuerLocation = issuerLocationSummary(player, progress);
        String status = trackerStatusText(player, context, definition, progress, activeConditions, replacements);
        return new QuestTrackerSyncPayload.Entry(
                definition.id().toString(),
                resolveQuestText(player, title, replacements),
                resolveQuestText(player, new QuestDefinition.SelectedText(step.text(), step.textKey()), replacements),
                metadataText(player, step.metadata(), replacements, status, issuer),
                Mth.clamp(progressValue, 0.0F, 1.0F),
                showProgress,
                progress.state().name().toLowerCase(Locale.ROOT),
                status,
                issuer,
                issuerLocation,
                questItems(player, definition, progress)
        );
    }

    private static String trackerStateStepKey(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return switch (progress.state()) {
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

    private static QuestDefinition.Step objectiveTrackerStep(
            QuestDefinition.Objective objective,
            QuestDefinition.Step fallback,
            boolean complete) {
        QuestDefinition.ObjectiveTracker tracker = objective.tracker();
        QuestDefinition.SelectedText displayText = tracker.displayText(complete);
        String text = displayText.text().isBlank() ? fallback.text() : displayText.text();
        String textKey = displayText.key().isBlank() ? fallback.textKey() : displayText.key();
        float progress = !complete && tracker.progress() >= 0.0F ? tracker.progress() : fallback.progress();
        Map<String, String> metadata = tracker.metadata().isEmpty() ? fallback.metadata() : tracker.metadata();
        return new QuestDefinition.Step(text, textKey, tracker.showProgress(), progress, metadata);
    }

    private static boolean objectiveTrackerHasDisplay(QuestDefinition.Objective objective, boolean complete) {
        return complete ? objective.tracker().hasCompletionDisplay() : objective.tracker().hasActiveDisplay();
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
                || definition.objectives().isEmpty()
                || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        QuestDefinition.Objective incomplete = firstIncompleteRequiredObjective(player, context, level, definition, progress).orElse(null);
        if (incomplete != null) {
            boolean hasConfiguredStep = definition.tracker().steps().containsKey(stepKey);
            boolean itemCollectionStep = "proof".equals(stepKey) && !definition.target().hasProofItem();
            if (incomplete.id().equals(stepKey) || itemCollectionStep || !hasConfiguredStep) {
                return incomplete;
            }
            return null;
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
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.optional()
                    || !objective.tracker().hasCompletionDisplay()
                    || !objectiveComplete(player, context, level, definition, progress, objective)) {
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
                || definition.objectives().isEmpty()
                || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        return firstIncompleteRequiredObjective(player, context, level, definition, progress)
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
        if (definition.target().hasStructureTarget() && !progress.visitedTarget()) {
            return "travel";
        }
        if (definition.target().hasProofItem() && !hasRequiredProof(player, definition)) {
            return "proof";
        }
        if (progress != null && !definition.objectives().isEmpty() && player.level() instanceof ServerLevel level) {
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
                return switch (objective.type()) {
                    case STRUCTURE_VISIT, LOCATION_VISIT -> "travel";
                    case ITEM_CHECK -> "proof";
                    case MOB_KILL -> "hunt";
                    case BLOCK_BREAK -> "break";
                    case BLOCK_PLACE -> "build";
                    case BLOCK_INTERACT -> "interact";
                    case MEMORY_EVENT -> "event";
                    case TRADE -> "trade";
                    case GIFT -> "gift";
                    case REPUTATION -> "reputation";
                    case CHOICE -> "choice";
                    case FACT -> "fact";
                    case CONDITION -> "inactive";
                };
            }
        }
        return "return";
    }

    private static String trackerFallbackText(String stepKey) {
        return switch (stepKey) {
            case "inactive" -> "Return when this quest's conditions are right again.";
            case "proof" -> "Obtain {proof_item} for this quest.";
            case "hunt" -> "Defeat {objective_count} {objective_entity}.";
            case "break" -> "Break {objective_count} {objective_block}.";
            case "build" -> "Place {objective_count} {objective_block}.";
            case "interact" -> "Use {objective_count} {objective_block}.";
            case "event" -> "Wait for {objective_memory}.";
            case "trade" -> "Complete trades: {objective_progress_count}/{objective_count}.";
            case "gift" -> "Give {objective_count} {objective_item}.";
            case "reputation" -> "Reach {objective_reputation_level} reputation with {issuer}.";
            case "choice" -> "Make a choice for this quest.";
            case "fact" -> "Resolve {objective_fact}.";
            case "return" -> "Return to {issuer}.";
            case "abandoned" -> "Return to {issuer} near {issuer_x}, {issuer_y}, {issuer_z} to pick this back up.";
            case "abandoned_cooldown" -> "Available later. Return to {issuer} near {issuer_x}, {issuer_y}, {issuer_z}.";
            case "expired" -> "Expired. Return to {issuer} near {issuer_x}, {issuer_y}, {issuer_z} if this can be restarted.";
            case "completed" -> "Completed.";
            case "branch_locked" -> "Closed by another choice.";
            case "consumed" -> "Unavailable.";
            default -> "Reach the center of {target} near {target_x}, {target_z}.";
        };
    }

    private static String trackerFallbackTextKey(String stepKey) {
        String normalized = stepKey == null || stepKey.isBlank() ? "travel" : stepKey.toLowerCase(Locale.ROOT);
        return "quest.tracker.step." + normalized;
    }

    private static float trackerFallbackProgress(String stepKey) {
        return switch (stepKey) {
            case "inactive", "abandoned", "abandoned_cooldown", "expired", "branch_locked", "consumed", "not_started" -> 0.0F;
            case "proof" -> 0.66F;
            case "return", "completed" -> 1.0F;
            default -> 0.25F;
        };
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
        Map<String, String> values = new LinkedHashMap<>();
        values.put("quest", questTitle(player, definition, Map.of()));
        values.put("quest_id", definition.id().toString());
        values.put("target", targetName(definition));
        values.put("proof_item", questItemName(definition, progress));
        values.put("quest_stage", progress == null ? "" : progress.currentStage());
        values.put("current_stage", progress == null ? "" : progress.currentStage());
        values.put("visited_target", progress != null && progress.visitedTarget() ? "yes" : "no");
        values.put("has_proof", hasRequiredProof(player, definition) ? "yes" : "no");
        values.put("active_conditions", activeConditionsMet ? "met" : "unmet");
        values.put("objective", progress == null ? "" : progress.targetObjectiveId());
        addObjectiveReplacements(values, player, context, definition, progress, objective);
        addIssuerReplacements(values, player, progress);

        BlockPos targetPos = progress == null ? null : progress.targetPos();
        values.put("target_dimension", targetDimensionText(progress));
        if (targetPos != null) {
            values.put("target_x", Integer.toString(roundCoordinate(targetPos.getX())));
            values.put("target_z", Integer.toString(roundCoordinate(targetPos.getZ())));
            values.put("direction", directionPhrase(player.blockPosition(), targetPos));
            values.put("distance", Integer.toString(roundDistance(player.blockPosition(), targetPos)));
        } else {
            values.put("target_x", "unknown");
            values.put("target_z", "unknown");
            values.put("direction", "somewhere beyond the map");
            values.put("distance", "unknown");
        }
        return Map.copyOf(values);
    }

    private static String metadataText(
            ServerPlayer player,
            Map<String, String> metadata,
            Map<String, String> replacements,
            String status,
            String issuer) {
        List<String> parts = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            parts.add(status);
        }
        if (issuer != null && !issuer.isBlank()) {
            parts.add(resolveGlobalText(player, "quest.tracker.metadata.issued_by", "Issued by {issuer}", replacements));
        }
        if (metadata == null || metadata.isEmpty()) {
            return String.join(" | ", parts);
        }
        metadata.values().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> VillagerDialogueResources.resolveTemplate(value, replacements))
                .forEach(parts::add);
        return String.join(" | ", parts);
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

    private static String resolveQuestText(
            DialogueContext context,
            QuestDefinition.SelectedText selected,
            Map<String, String> replacements) {
        if (selected == null) {
            return "";
        }
        if (!selected.key().isBlank()) {
            return VillagerDialogueResources
                    .message(context, selected.key(), replacements)
                    .orElseGet(() -> VillagerDialogueResources.resolveTemplate(selected.text(), replacements));
        }
        return VillagerDialogueResources.resolveTemplate(selected.text(), replacements);
    }

    private static String resolveQuestText(
            ServerPlayer player,
            QuestDefinition.SelectedText selected,
            Map<String, String> replacements) {
        if (selected == null) {
            return "";
        }
        if (!selected.key().isBlank()) {
            return resolveGlobalText(player, selected.key(), selected.text(), replacements);
        }
        return VillagerDialogueResources.resolveTemplate(selected.text(), replacements);
    }

    private static String resolveGlobalText(
            ServerPlayer player,
            String key,
            String fallback,
            Map<String, String> replacements) {
        if (player != null && player.getServer() != null && key != null && !key.isBlank()) {
            return VillagerDialogueResources
                    .globalMessage(player.getServer(), player.getRandom(), key, VillagerLocale.locale(player), replacements)
                    .orElseGet(() -> VillagerDialogueResources.resolveTemplate(fallback, replacements));
        }
        return VillagerDialogueResources.resolveTemplate(fallback, replacements);
    }

    private static String questTitle(
            DialogueContext context,
            QuestDefinition definition,
            Map<String, String> replacements) {
        return resolveQuestText(
                context,
                new QuestDefinition.SelectedText(definition.title(), definition.titleKey()),
                replacements);
    }

    private static String questTitle(
            ServerPlayer player,
            QuestDefinition definition,
            Map<String, String> replacements) {
        return resolveQuestText(
                player,
                new QuestDefinition.SelectedText(definition.title(), definition.titleKey()),
                replacements);
    }

    private static Map<String, String> replacements(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("quest", questTitle(context, definition, Map.of()));
        values.put("quest_id", definition.id().toString());
        values.put("target", targetName(definition));
        values.put("proof_item", questItemName(definition, progress));
        values.put("visited_target", progress != null && progress.visitedTarget() ? "yes" : "no");
        values.put("has_proof", hasRequiredProof(context.player(), definition) ? "yes" : "no");
        boolean activeConditionsMet = activeConditionsMet(context, definition);
        values.put("active_conditions", activeConditionsMet ? "met" : "unmet");
        values.put("objective", progress == null ? "" : progress.targetObjectiveId());
        addObjectiveReplacements(
                values,
                context.player(),
                context,
                definition,
                progress,
                currentObjectiveForReplacements(context.player(), context, definition, progress, activeConditionsMet));
        addIssuerReplacements(values, context, progress);

        BlockPos targetPos = progress == null ? null : progress.targetPos();
        values.put("target_dimension", targetDimensionText(progress));
        if (targetPos != null) {
            values.put("target_x", Integer.toString(roundCoordinate(targetPos.getX())));
            values.put("target_z", Integer.toString(roundCoordinate(targetPos.getZ())));
            values.put("direction", directionPhrase(context.villager().blockPosition(), targetPos));
            values.put("distance", Integer.toString(roundDistance(context.villager().blockPosition(), targetPos)));
        } else {
            values.put("target_x", "unknown");
            values.put("target_z", "unknown");
            values.put("direction", "somewhere beyond my maps");
            values.put("distance", "unknown");
        }
        return Map.copyOf(values);
    }

    private static void addIssuerReplacements(
            Map<String, String> values,
            ServerPlayer player,
            VillagerQuestSavedData.QuestProgress progress) {
        values.put("issuer", issuerSummary(player, progress));
        values.put("issuer_name", issuerName(player, progress));
        values.put("issuer_profession", issuerProfessionName(player, progress));
        values.put("issuer_dimension", issuerDimensionText(player, progress));
        values.put("issuer_location", issuerLocationSummary(player, progress));
        values.put("issuer_status", issuerStatus(player, progress));
        BlockPos issuerPos = issuerPos(player, progress);
        if (issuerPos == null) {
            values.put("issuer_x", "unknown");
            values.put("issuer_y", "unknown");
            values.put("issuer_z", "unknown");
        } else {
            values.put("issuer_x", Integer.toString(issuerPos.getX()));
            values.put("issuer_y", Integer.toString(issuerPos.getY()));
            values.put("issuer_z", Integer.toString(issuerPos.getZ()));
        }
    }

    private static void addObjectiveReplacements(
            Map<String, String> values,
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        if (objective == null) {
            values.put("objective_id", progress == null ? "" : progress.targetObjectiveId());
            values.put("objective_type", "");
            values.put("objective_item", questItemName(definition, progress));
            values.put("objective_item_id", "");
            values.put("objective_count", "");
            values.put("objective_progress_count", "0");
            values.put("objective_entity", "");
            values.put("objective_block", "");
            values.put("objective_block_id", "");
            values.put("objective_memory", "");
            values.put("objective_memory_id", "");
            values.put("objective_gift_reaction", "");
            values.put("objective_reputation", "0");
            values.put("objective_reputation_level", "");
            values.put("objective_reputation_min", "");
            values.put("objective_reputation_max", "");
            values.put("objective_choice", "");
            values.put("objective_choice_key", "");
            values.put("objective_choice_value", "");
            values.put("objective_fact", "");
            values.put("objective_fact_id", "");
            values.put("objective_fact_key", "");
            values.put("objective_fact_value", "");
            values.put("objective_fact_scope", "");
            values.put("objective_radius", "");
            values.put("objective_complete", "no");
            values.put("objective_progress", "0");
            values.put("objective_target_x", "unknown");
            values.put("objective_target_y", "unknown");
            values.put("objective_target_z", "unknown");
            values.put("objective_target_dimension", "unknown");
            return;
        }

        values.put("objective", objective.id());
        values.put("objective_id", objective.id());
        values.put("objective_type", objective.type().name().toLowerCase(Locale.ROOT));
        values.put("objective_item", objective.item() == null ? questItemName(definition, progress) : itemName(objective.item()));
        values.put("objective_item_id", objective.item() == null ? "" : objective.item().toString());
        values.put("objective_count", Integer.toString(objective.count()));
        values.put("objective_progress_count", Integer.toString(progress == null ? 0 : progress.objectiveCounter(objective.id())));
        values.put("objective_entity", objectiveEntityName(objective));
        values.put("objective_block", objectiveBlockName(objective));
        values.put("objective_block_id", objectiveBlockId(objective));
        values.put("objective_memory", objectiveMemoryName(objective));
        values.put("objective_memory_id", objectiveMemoryId(objective));
        values.put("objective_gift_reaction", objectiveGiftReaction(objective));
        values.put("objective_reputation", Integer.toString(reputationForObjective(player.serverLevel(), player, progress)));
        values.put("objective_reputation_level", objectiveReputationLevel(objective));
        values.put("objective_reputation_min", objective.minReputation() == null ? "" : objective.minReputation().toString());
        values.put("objective_reputation_max", objective.maxReputation() == null ? "" : objective.maxReputation().toString());
        values.put("objective_choice", objectiveChoiceValue(objective));
        values.put("objective_choice_key", objective.type() == QuestDefinition.ObjectiveType.CHOICE ? objective.factKey() : "");
        values.put("objective_choice_value", objectiveChoiceValue(objective));
        values.put("objective_fact", objectiveFactName(objective));
        values.put("objective_fact_id", objectiveFactId(objective));
        values.put("objective_fact_key", objective.factKey());
        values.put("objective_fact_value", objectiveFactValue(objective));
        values.put("objective_fact_scope", objective.factScope().name().toLowerCase(Locale.ROOT));
        values.put("objective_radius", Integer.toString(objective.radius()));
        boolean complete = progress != null && objectiveComplete(player, context, player.serverLevel(), definition, progress, objective);
        values.put("objective_complete", complete ? "yes" : "no");
        values.put("objective_progress", String.format(Locale.ROOT, "%.2f", objectiveProgress(player, context, definition, progress, objective)));

        BlockPos targetPos = progress != null
                && objective.id().equals(progress.targetObjectiveId())
                ? progress.targetPos()
                : null;
        if (targetPos == null && objective.location() != null) {
            targetPos = objective.location();
        }
        if (targetPos == null) {
            values.put("objective_target_x", "unknown");
            values.put("objective_target_y", "unknown");
            values.put("objective_target_z", "unknown");
            values.put("objective_target_dimension", "unknown");
        } else {
            values.put("objective_target_x", Integer.toString(roundCoordinate(targetPos.getX())));
            values.put("objective_target_y", Integer.toString(roundCoordinate(targetPos.getY())));
            values.put("objective_target_z", Integer.toString(roundCoordinate(targetPos.getZ())));
            values.put("objective_target_dimension", targetDimensionText(progress));
        }
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
        return switch (objective.type()) {
            case ITEM_CHECK -> objective.item() == null
                    ? 0.0F
                    : Mth.clamp((float) itemCount(player, objective) / (float) objective.count(), 0.0F, 1.0F);
            case MOB_KILL, BLOCK_BREAK, BLOCK_PLACE, BLOCK_INTERACT, MEMORY_EVENT, TRADE, GIFT -> progress == null
                    ? 0.0F
                    : Mth.clamp((float) progress.objectiveCounter(objective.id()) / (float) objective.count(), 0.0F, 1.0F);
            case STRUCTURE_VISIT, LOCATION_VISIT, REPUTATION, CHOICE, FACT, CONDITION -> progress != null
                    && objectiveComplete(player, context, player.serverLevel(), definition, progress, objective)
                    ? 1.0F
                    : 0.0F;
        };
    }

    private static void addIssuerReplacements(
            Map<String, String> values,
            DialogueContext context,
            VillagerQuestSavedData.QuestProgress progress) {
        addIssuerReplacements(values, context.player(), progress);
    }

    private static String trackerStatusText(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            ConditionMatch activeConditions,
            Map<String, String> replacements) {
        return switch (progress.state()) {
            case ACTIVE -> {
                boolean activeConditionsMet = activeConditions == ConditionMatch.MET;
                if (!activeConditionsMet) {
                    if (activeConditions == ConditionMatch.UNKNOWN) {
                        yield resolveGlobalText(player, "quest.tracker.status.waiting_for_context", "Waiting for issuer", replacements);
                    }
                    yield resolveGlobalText(player, "quest.tracker.status.inactive", "Inactive", replacements);
                }
                yield "return".equals(trackerStepKey(player, context, definition, progress, true))
                        ? resolveGlobalText(player, "quest.tracker.status.ready", "Ready to turn in", replacements)
                        : resolveGlobalText(player, "quest.tracker.status.active", "Active", replacements);
            }
            case ABANDONED -> {
                if (definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN
                        && !cooldownElapsed(
                                player.level().getGameTime(),
                                progress.abandonedGameTime(),
                                definition.rules().abandonmentCooldownTicks())) {
                    yield resolveGlobalText(player, "quest.tracker.status.abandoned_cooldown", "Abandoned - available later", replacements);
                }
                yield resolveGlobalText(player, "quest.tracker.status.abandoned", "Abandoned - return to restart", replacements);
            }
            case EXPIRED -> resolveGlobalText(player, "quest.tracker.status.expired", "Expired", replacements);
            case COMPLETED -> resolveGlobalText(player, "quest.tracker.status.completed", "Completed", replacements);
            case CONSUMED -> branchLocked(progress)
                    ? resolveGlobalText(player, "quest.tracker.status.branch_locked", "Closed by another choice", replacements)
                    : resolveGlobalText(player, "quest.tracker.status.consumed", "Unavailable", replacements);
            case NOT_STARTED -> resolveGlobalText(player, "quest.tracker.status.not_started", "Not started", replacements);
        };
    }

    private static List<QuestTrackerSyncPayload.QuestItem> questItems(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            return List.of();
        }
        Map<String, QuestTrackerSyncPayload.QuestItem> items = new LinkedHashMap<>();
        addQuestItem(items, definition.target().proofItem(), 1);
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.type() != QuestDefinition.ObjectiveType.ITEM_CHECK || objective.item() == null) {
                continue;
            }
            addQuestItem(items, objective.item(), objective.count());
        }
        return List.copyOf(items.values());
    }

    private static void addQuestItem(
            Map<String, QuestTrackerSyncPayload.QuestItem> items,
            ResourceLocation itemId,
            int count) {
        if (itemId == null) {
            return;
        }
        String key = itemId.toString();
        QuestTrackerSyncPayload.QuestItem existing = items.get(key);
        if (existing == null || count > existing.count()) {
            items.put(key, new QuestTrackerSyncPayload.QuestItem(key, itemName(itemId), count));
        }
    }

    public static void onEntityKilled(LivingEntity killed, Entity attacker) {
        if (killed == null || !(killed.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer player = attacker instanceof ServerPlayer serverPlayer
                ? serverPlayer
                : killed.getKillCredit() instanceof ServerPlayer serverPlayer
                        ? serverPlayer
                        : null;
        if (player == null || player.level() != level) {
            return;
        }
        Set<ResourceLocation> candidateQuestIds = VillagerQuestResources.questIdsWithObjective(
                level.getServer(),
                QuestDefinition.ObjectiveType.MOB_KILL);
        if (candidateQuestIds.isEmpty()) {
            return;
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        boolean changed = false;
        boolean progressNotice = false;
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : data.activeProgress(player.getUUID())) {
            if (!candidateQuestIds.contains(entry.getKey())) {
                continue;
            }
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = entry.getValue();
            if (!activeConditionsMetForPlayer(player, definition, progress)) {
                continue;
            }
            boolean questProgressChanged = updateMobKillProgress(level, player, killed, definition, progress);
            if (!questProgressChanged) {
                continue;
            }
            questProgressChanged |= advanceStageAfterEvent(level, player, definition, progress);
            changed = true;
            progressNotice = true;
            sendQuestProgressNotification(
                    player,
                    definition,
                    progress,
                    "quest.updated",
                    "Quest updated: {quest}");
            changed |= dispatchQuestTriggers(player, definition, progress, QuestDefinition.TriggerEvent.PROGRESS);
        }
        if (changed) {
            data.setDirty();
            sendTrackerSync(player, progressNotice);
        }
    }

    public static void onBlockBroken(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        onBlockEvent(level, player, pos, state, QuestDefinition.ObjectiveType.BLOCK_BREAK);
    }

    public static void onBlockPlaced(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        onBlockEvent(level, player, pos, state, QuestDefinition.ObjectiveType.BLOCK_PLACE);
    }

    public static void onBlockInteracted(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        onBlockEvent(level, player, pos, state, QuestDefinition.ObjectiveType.BLOCK_INTERACT);
    }

    public static void onMemoryEvent(ServerLevel level, VillageEventMemory.MemoryEvent event) {
        if (level == null || event == null || event.tagId() == null || event.playerId() == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(event.playerId());
        if (player == null || player.level() != level) {
            return;
        }
        Set<ResourceLocation> candidateQuestIds = VillagerQuestResources.memoryEventQuestIds(level.getServer(), event.tagId());
        if (candidateQuestIds.isEmpty()) {
            return;
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        boolean changed = false;
        boolean progressNotice = false;
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : data.activeProgress(player.getUUID())) {
            if (!candidateQuestIds.contains(entry.getKey())) {
                continue;
            }
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = entry.getValue();
            if (!activeConditionsMetForPlayer(player, definition, progress)) {
                continue;
            }
            boolean questProgressChanged = updateMemoryEventProgress(level, player, event, definition, progress);
            if (!questProgressChanged) {
                continue;
            }
            questProgressChanged |= advanceStageAfterEvent(level, player, definition, progress);
            changed = true;
            progressNotice = true;
            sendQuestProgressNotification(
                    player,
                    definition,
                    progress,
                    "quest.updated",
                    "Quest updated: {quest}");
            changed |= dispatchQuestTriggers(player, definition, progress, QuestDefinition.TriggerEvent.PROGRESS);
        }
        if (changed) {
            data.setDirty();
            sendTrackerSync(player, progressNotice);
        }
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
                || giftedStack == null
                || giftedStack.isEmpty()
                || reaction == null
                || player.level() != level) {
            return;
        }

        Set<ResourceLocation> candidateQuestIds = VillagerQuestResources.questIdsWithObjective(
                level.getServer(),
                QuestDefinition.ObjectiveType.GIFT);
        if (candidateQuestIds.isEmpty()) {
            return;
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        boolean changed = false;
        boolean progressNotice = false;
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : data.activeProgress(player.getUUID())) {
            if (!candidateQuestIds.contains(entry.getKey())) {
                continue;
            }
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = entry.getValue();
            if (!activeConditionsMetForPlayer(player, definition, progress)) {
                continue;
            }
            boolean questProgressChanged = updateGiftProgress(level, player, giftedStack, reaction, definition, progress);
            if (!questProgressChanged) {
                continue;
            }
            questProgressChanged |= advanceStageAfterEvent(level, player, definition, progress);
            changed = true;
            progressNotice = true;
            sendQuestProgressNotification(
                    player,
                    definition,
                    progress,
                    "quest.updated",
                    "Quest updated: {quest}");
            changed |= dispatchQuestTriggers(player, definition, progress, QuestDefinition.TriggerEvent.PROGRESS);
        }
        if (changed) {
            data.setDirty();
            sendTrackerSync(player, progressNotice);
        }
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

        Set<ResourceLocation> candidateQuestIds = VillagerQuestResources.questIdsWithObjective(
                level.getServer(),
                QuestDefinition.ObjectiveType.TRADE);
        if (candidateQuestIds.isEmpty()) {
            return;
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        boolean changed = false;
        boolean progressNotice = false;
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : data.activeProgress(player.getUUID())) {
            if (!candidateQuestIds.contains(entry.getKey())) {
                continue;
            }
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = entry.getValue();
            if (!activeConditionsMetForPlayer(player, definition, progress)) {
                continue;
            }
            boolean questProgressChanged = updateTradeProgress(level, player, villager, offer, definition, progress);
            if (!questProgressChanged) {
                continue;
            }
            questProgressChanged |= advanceStageAfterEvent(level, player, definition, progress);
            changed = true;
            progressNotice = true;
            sendQuestProgressNotification(
                    player,
                    definition,
                    progress,
                    "quest.updated",
                    "Quest updated: {quest}");
            changed |= dispatchQuestTriggers(player, definition, progress, QuestDefinition.TriggerEvent.PROGRESS);
        }
        if (changed) {
            data.setDirty();
            sendTrackerSync(player, progressNotice);
        }
    }

    private static void onBlockEvent(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos,
            BlockState state,
            QuestDefinition.ObjectiveType type) {
        if (level == null || player == null || pos == null || state == null || player.level() != level) {
            return;
        }

        Set<ResourceLocation> candidateQuestIds = VillagerQuestResources.questIdsWithObjective(level.getServer(), type);
        if (candidateQuestIds.isEmpty()) {
            return;
        }

        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        boolean changed = false;
        boolean progressNotice = false;
        for (Map.Entry<ResourceLocation, VillagerQuestSavedData.QuestProgress> entry : data.activeProgress(player.getUUID())) {
            if (!candidateQuestIds.contains(entry.getKey())) {
                continue;
            }
            QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = entry.getValue();
            if (!activeConditionsMetForPlayer(player, definition, progress)) {
                continue;
            }
            boolean questProgressChanged = updateBlockEventProgress(level, player, pos, state, definition, progress, type);
            if (!questProgressChanged) {
                continue;
            }
            questProgressChanged |= advanceStageAfterEvent(level, player, definition, progress);
            changed = true;
            progressNotice = true;
            sendQuestProgressNotification(
                    player,
                    definition,
                    progress,
                    "quest.updated",
                    "Quest updated: {quest}");
            changed |= dispatchQuestTriggers(player, definition, progress, QuestDefinition.TriggerEvent.PROGRESS);
        }
        if (changed) {
            data.setDirty();
            sendTrackerSync(player, progressNotice);
        }
    }

    private static String questItemName(QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress) {
        if (definition.target().hasProofItem()) {
            return itemName(definition.target().proofItem());
        }
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK
                    && objective.item() != null
                    && (progress == null || !progress.objectiveComplete(objective.id()))) {
                return itemName(objective.item());
            }
        }
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK && objective.item() != null) {
                return itemName(objective.item());
            }
        }
        return "proof";
    }

    private static String itemName(ResourceLocation itemId) {
        if (itemId == null) {
            return "proof";
        }
        return BuiltInRegistries.ITEM.getOptional(itemId)
                .map(item -> new ItemStack(item).getHoverName().getString())
                .orElseGet(() -> VillagerInteractionTextUtil.resourcePathName(itemId));
    }

    private static String objectiveEntityName(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.MOB_KILL) {
            return "";
        }
        if (!objective.entityTypes().isEmpty()) {
            ResourceLocation entityType = objective.entityTypes().iterator().next();
            return BuiltInRegistries.ENTITY_TYPE.getOptional(entityType)
                    .map(type -> type.getDescription().getString())
                    .orElseGet(() -> VillagerInteractionTextUtil.resourcePathName(entityType));
        }
        if (!objective.entityTags().isEmpty()) {
            return VillagerInteractionTextUtil.resourcePathName(objective.entityTags().iterator().next());
        }
        return "mobs";
    }

    private static String objectiveBlockName(QuestDefinition.Objective objective) {
        if (objective == null
                || (objective.type() != QuestDefinition.ObjectiveType.BLOCK_BREAK
                && objective.type() != QuestDefinition.ObjectiveType.BLOCK_PLACE
                && objective.type() != QuestDefinition.ObjectiveType.BLOCK_INTERACT)) {
            return "";
        }
        if (!objective.blockTypes().isEmpty()) {
            ResourceLocation blockType = objective.blockTypes().iterator().next();
            return BuiltInRegistries.BLOCK.getOptional(blockType)
                    .map(block -> block.getName().getString())
                    .orElseGet(() -> VillagerInteractionTextUtil.resourcePathName(blockType));
        }
        if (!objective.blockTags().isEmpty()) {
            return VillagerInteractionTextUtil.resourcePathName(objective.blockTags().iterator().next());
        }
        return "blocks";
    }

    private static String objectiveBlockId(QuestDefinition.Objective objective) {
        if (objective == null
                || (objective.type() != QuestDefinition.ObjectiveType.BLOCK_BREAK
                && objective.type() != QuestDefinition.ObjectiveType.BLOCK_PLACE
                && objective.type() != QuestDefinition.ObjectiveType.BLOCK_INTERACT)) {
            return "";
        }
        if (!objective.blockTypes().isEmpty()) {
            return objective.blockTypes().iterator().next().toString();
        }
        if (!objective.blockTags().isEmpty()) {
            return "#" + objective.blockTags().iterator().next();
        }
        return "";
    }

    private static String objectiveMemoryName(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.MEMORY_EVENT) {
            return "";
        }
        if (!objective.memoryTags().isEmpty()) {
            return VillagerInteractionTextUtil.resourcePathName(objective.memoryTags().iterator().next());
        }
        return "event";
    }

    private static String objectiveMemoryId(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.MEMORY_EVENT) {
            return "";
        }
        return objective.memoryTags().isEmpty() ? "" : objective.memoryTags().iterator().next().toString();
    }

    private static String objectiveGiftReaction(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.GIFT) {
            return "";
        }
        return objective.giftReactions().isEmpty() ? "" : objective.giftReactions().iterator().next();
    }

    private static String objectiveReputationLevel(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.REPUTATION) {
            return "";
        }
        return objective.reputationLevels().isEmpty()
                ? "required"
                : objective.reputationLevels().iterator().next().name().toLowerCase(Locale.ROOT);
    }

    private static String objectiveChoiceValue(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.CHOICE) {
            return "";
        }
        return objective.factValues().isEmpty() ? "" : objective.factValues().iterator().next();
    }

    private static String objectiveFactName(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.FACT) {
            return "";
        }
        if (!objective.factTags().isEmpty()) {
            return VillagerInteractionTextUtil.resourcePathName(objective.factTags().iterator().next());
        }
        if (!objective.factKey().isBlank()) {
            return objective.factKey().replace('_', ' ').replace('.', ' ');
        }
        return "fact";
    }

    private static String objectiveFactId(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.FACT) {
            return "";
        }
        if (!objective.factTags().isEmpty()) {
            return objective.factTags().iterator().next().toString();
        }
        return objective.factKey();
    }

    private static String objectiveFactValue(QuestDefinition.Objective objective) {
        if (objective == null || objective.type() != QuestDefinition.ObjectiveType.FACT) {
            return "";
        }
        if (!objective.factValues().isEmpty()) {
            return objective.factValues().iterator().next();
        }
        if (objective.factMin() != null && objective.factMax() != null) {
            return objective.factMin() + "-" + objective.factMax();
        }
        if (objective.factMin() != null) {
            return ">=" + objective.factMin();
        }
        if (objective.factMax() != null) {
            return "<=" + objective.factMax();
        }
        return "";
    }

    private static String issuerSummary(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        String name = issuerName(player, progress);
        String profession = issuerProfessionName(player, progress);
        if (profession.isBlank() || "villager".equalsIgnoreCase(profession)) {
            return name;
        }
        return name + " the " + profession;
    }

    private static String issuerName(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        Villager villager = liveStartedVillager(player, progress);
        if (villager != null) {
            return VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        }
        if (progress != null && !progress.issuerName().isBlank()) {
            return progress.issuerName();
        }
        return "Unknown villager";
    }

    private static String issuerProfessionName(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        Villager villager = liveStartedVillager(player, progress);
        if (villager != null) {
            return VillagerInteractionTextUtil.professionName(villager.getVillagerData().getProfession(), "villager");
        }
        if (progress != null && !progress.issuerProfession().isBlank()) {
            ResourceLocation professionId = ResourceLocation.tryParse(progress.issuerProfession());
            if (professionId != null) {
                return VillagerInteractionTextUtil.resourcePathName(professionId);
            }
        }
        return "villager";
    }

    private static String issuerLocationSummary(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        Villager live = liveStartedVillager(player, progress);
        if (live != null && live.isAlive()) {
            BlockPos livePos = live.blockPosition();
            return "Current location: " + livePos.getX() + ", " + livePos.getY() + ", " + livePos.getZ()
                    + " in " + live.level().dimension().location();
        }
        BlockPos pos = progress == null ? null : progress.issuerPos();
        if (pos == null) {
            return "Last seen location unknown";
        }
        String dimension = issuerDimensionText(player, progress);
        return "Last seen near " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + (dimension.isBlank() || "unknown".equals(dimension) ? "" : " in " + dimension);
    }

    private static BlockPos issuerPos(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        Villager villager = liveStartedVillager(player, progress);
        if (villager != null) {
            return villager.blockPosition();
        }
        return progress == null ? null : progress.issuerPos();
    }

    private static String issuerDimensionText(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        Villager villager = liveStartedVillager(player, progress);
        if (villager != null) {
            return villager.level().dimension().location().toString();
        }
        ResourceKey<Level> dimension = progress == null ? null : progress.issuerDimension();
        return dimension == null ? "unknown" : dimension.location().toString();
    }

    private static String targetDimensionText(VillagerQuestSavedData.QuestProgress progress) {
        ResourceKey<Level> dimension = progress == null ? null : progress.targetDimension();
        return dimension == null ? "unknown" : dimension.location().toString();
    }

    private static String issuerStatus(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        if (liveStartedVillager(player, progress) != null) {
            return "current";
        }
        return progress != null
                && (progress.issuerPos() != null || !progress.issuerName().isBlank() || progress.issuerDimension() != null)
                ? "last_seen"
                : "unknown";
    }

    private static Villager liveStartedVillager(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        if (player.level() instanceof ServerLevel level) {
            return startedVillager(level, progress);
        }
        return null;
    }

    private static String targetName(QuestDefinition definition) {
        return definition.target().structure() == null
                ? "the target"
                : VillagerInteractionTextUtil.resourcePathName(definition.target().structure());
    }

    private static int roundCoordinate(int value) {
        return Math.round((float) value / APPROXIMATE_COORDINATE_STEP) * APPROXIMATE_COORDINATE_STEP;
    }

    private static int roundDistance(BlockPos origin, BlockPos target) {
        int dx = target.getX() - origin.getX();
        int dz = target.getZ() - origin.getZ();
        return Math.max(100, Math.round((float) Math.sqrt((double) dx * dx + (double) dz * dz) / 100.0F) * 100);
    }

    private static String directionPhrase(BlockPos origin, BlockPos target) {
        int dx = target.getX() - origin.getX();
        int dz = target.getZ() - origin.getZ();
        String northSouth = dz < -32 ? "north" : dz > 32 ? "south" : "";
        String eastWest = dx > 32 ? "east" : dx < -32 ? "west" : "";
        if (!northSouth.isBlank() && !eastWest.isBlank()) {
            return northSouth + "-" + eastWest;
        }
        if (!northSouth.isBlank()) {
            return northSouth;
        }
        if (!eastWest.isBlank()) {
            return eastWest;
        }
        return "nearby";
    }

    private static String debugParentState(
            VillagerQuestSavedData data,
            ServerPlayer player,
            ResourceLocation parentId) {
        if (parentId == null) {
            return "none";
        }
        VillagerQuestSavedData.QuestProgress parentProgress = data.get(player.getUUID(), parentId);
        if (parentProgress == null) {
            return parentId + "(not_started)";
        }
        String completed = parentProgress.completionCount() > 0 ? ",completed=true" : "";
        return parentId + "(" + debugEnum(parentProgress.state()) + completed + ")";
    }

    private static String debugProgressLine(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueContext context) {
        if (progress == null) {
            return "progress saved=false state=not_started ready=false active_conditions=n/a branch_locked=false";
        }
        ConditionMatch activeConditions = activeConditionsStateForPlayer(player, definition, progress);
        String ready = progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                ? "false"
                : context == null
                        ? "unknown_no_live_issuer"
                        : Boolean.toString(isReadyToTurnIn(context, definition, progress));
        return "progress saved=true state=" + debugEnum(progress.state())
                + " stage=" + blankAs(progress.currentStage(), "none")
                + " starts=" + progress.startCount()
                + " completions=" + progress.completionCount()
                + " abandons=" + progress.abandonCount()
                + " ready=" + ready
                + " active_conditions=" + debugEnum(activeConditions)
                + " branch_locked=" + branchLocked(progress);
    }

    private static String debugObjectiveLine(
            ServerPlayer player,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            DialogueContext context,
            QuestDefinition.Objective objective) {
        boolean complete = progress != null
                && objectiveComplete(player, context, level, definition, progress, objective);
        int counter = progress == null ? 0 : progress.objectiveCounter(objective.id());
        List<String> parts = new ArrayList<>();
        parts.add("objective " + objective.id());
        parts.add("type=" + debugEnum(objective.type()));
        parts.add("optional=" + objective.optional());
        parts.add("complete=" + complete);
        switch (objective.type()) {
            case STRUCTURE_VISIT -> {
                parts.add("structure=" + debugResource(objective.structure()));
                parts.add("dimension=" + debugDimension(objective.dimension()));
                parts.add("pieces=" + debugStringList(objective.pieces()));
                parts.add("search_radius=" + objective.searchRadius());
                parts.add("discovery_radius=" + objective.discoveryRadius());
            }
            case LOCATION_VISIT -> debugAddLocation(parts, objective);
            case ITEM_CHECK -> {
                parts.add("item=" + debugResource(objective.item()));
                parts.add("current=" + itemCount(player, objective));
                parts.add("count=" + objective.count());
                parts.add("consume=" + objective.consume());
                parts.add("enchantments=" + objective.itemRequirements().enchantments().size());
                parts.add("custom_data=" + objective.itemRequirements().hasCustomData());
            }
            case MOB_KILL -> {
                debugAddCounter(parts, counter, objective.count());
                parts.add("entities=" + debugResourceSet(objective.entityTypes()));
                parts.add("entity_tags=" + debugResourceSet(objective.entityTags()));
                debugAddLocation(parts, objective);
            }
            case BLOCK_BREAK, BLOCK_PLACE, BLOCK_INTERACT -> {
                debugAddCounter(parts, counter, objective.count());
                parts.add("blocks=" + debugResourceSet(objective.blockTypes()));
                parts.add("block_tags=" + debugResourceSet(objective.blockTags()));
                debugAddLocation(parts, objective);
            }
            case MEMORY_EVENT -> {
                debugAddCounter(parts, counter, objective.count());
                parts.add("memory_tags=" + debugResourceSet(objective.memoryTags()));
                debugAddLocation(parts, objective);
            }
            case TRADE -> {
                debugAddCounter(parts, counter, objective.count());
                parts.add("result_item=" + debugResource(objective.item()));
                debugAddLocation(parts, objective);
            }
            case GIFT -> {
                debugAddCounter(parts, counter, objective.count());
                parts.add("item=" + debugResource(objective.item()));
                parts.add("reactions=" + debugStringSet(objective.giftReactions()));
            }
            case REPUTATION -> {
                parts.add("current=" + reputationForObjective(level, player, progress));
                parts.add("levels=" + debugEnumSet(objective.reputationLevels()));
                parts.add("min=" + (objective.minReputation() == null ? "none" : objective.minReputation()));
                parts.add("max=" + (objective.maxReputation() == null ? "none" : objective.maxReputation()));
            }
            case CHOICE -> {
                parts.add("scope=" + debugEnum(objective.factScope()));
                parts.add("quest=" + debugResource(objective.factQuestId() == null ? definition.id() : objective.factQuestId()));
                parts.add("key=" + blankAs(objective.factKey(), "none"));
                parts.add("choices=" + debugStringSet(objective.factValues()));
                if (progress != null) {
                    parts.add("scope_key=" + blankAs(factObjectiveScopeKey(level, player, definition, progress, objective), "unresolved"));
                }
            }
            case FACT -> {
                parts.add("scope=" + debugEnum(objective.factScope()));
                parts.add("quest=" + debugResource(objective.factQuestId() == null ? definition.id() : objective.factQuestId()));
                parts.add("tags=" + debugResourceSet(objective.factTags()));
                parts.add("key=" + blankAs(objective.factKey(), "none"));
                parts.add("values=" + debugStringSet(objective.factValues()));
                parts.add("min=" + (objective.factMin() == null ? "none" : objective.factMin()));
                parts.add("max=" + (objective.factMax() == null ? "none" : objective.factMax()));
                if (progress != null) {
                    parts.add("scope_key=" + blankAs(factObjectiveScopeKey(level, player, definition, progress, objective), "unresolved"));
                }
            }
            case CONDITION -> {
                parts.add("conditions=" + objective.conditions().size());
                parts.add("condition_state=" + debugEnum(objectiveConditionState(player, context, level, definition, progress, objective)));
            }
        }
        return String.join(" ", parts);
    }

    private static void debugAddCounter(List<String> parts, int current, int required) {
        parts.add("counter=" + current + "/" + required);
    }

    private static void debugAddLocation(List<String> parts, QuestDefinition.Objective objective) {
        parts.add("dimension=" + debugDimension(objective.dimension()));
        if (objective.location() != null) {
            parts.add("location=" + debugPos(objective.location()));
            parts.add("radius=" + objective.radius());
        }
    }

    private static String debugResource(ResourceLocation id) {
        return id == null ? "none" : id.toString();
    }

    private static String debugDimension(ResourceKey<Level> dimension) {
        return dimension == null ? "any" : dimension.location().toString();
    }

    private static String debugPos(BlockPos pos) {
        return pos == null ? "none" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String debugEnum(Enum<?> value) {
        return value == null ? "none" : value.name().toLowerCase(Locale.ROOT);
    }

    private static String debugEnumSet(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(VillagerQuestService::debugEnum)
                .sorted()
                .toList()
                .toString();
    }

    private static String debugResourceSet(Set<ResourceLocation> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList()
                .toString();
    }

    private static String debugStringSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .sorted()
                .toList()
                .toString();
    }

    private static String debugStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.toString();
    }

    private static String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String lineId(QuestDefinition definition, String stage) {
        return "quest_" + definition.id().toString().replace(':', '_').replace('/', '_') + "_" + stage;
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

    private record TrackerSyncState(String signature, long gameTime) {
    }

    private enum ItemHandInResult {
        SUCCESS("completed", "", ""),
        MISSING_ITEMS("missing_objectives", "There is still more to do before this is ready.", "quest.dialogue.missing_objectives"),
        NO_ROOM("inventory_full", "I do not have room in my inventory for that.", "quest.dialogue.inventory_full");

        private final String status;
        private final String message;
        private final String messageKey;

        ItemHandInResult(String status, String message, String messageKey) {
            this.status = status;
            this.message = message;
            this.messageKey = messageKey;
        }
    }
}
