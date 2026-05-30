package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionExecutor;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueQuestAction;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerQuestService {
    private static final int QUEST_PROGRESS_SCAN_INTERVAL_TICKS = 20;
    private static final int APPROXIMATE_COORDINATE_STEP = 50;
    private static final long QUEST_STORY_HINT_TICKS = 20L * 60L * 60L * 6L;

    private VillagerQuestService() {
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

    public static Optional<VillagerDialogueService.DialogueResult> handleDialogueOption(
            DialogueContext context,
            DialogueOptionDefinition option) {
        DialogueQuestAction questAction = option.questAction();
        if (questAction.isEmpty()) {
            return Optional.empty();
        }

        return performAction(context, questAction.questId(), questAction.action())
                .map(QuestActionOutcome::dialogueResult);
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
                continue;
            }
            if (definition.rules().activeState().pauseProgressWhenUnmet()
                    && !activeConditionsMet(questContext, definition)) {
                continue;
            }
            boolean questProgressChanged = false;
            if (definition.target().hasProofItem() && hasRequiredProof(player, definition) && progress.markHasProof()) {
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
            if (!progress.visitedTarget() && isAtQuestTarget(level, player.blockPosition(), definition, progress) && progress.markVisitedTarget()) {
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

    private static QuestActionOutcome startQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            return matchesVillagerLock(context, definition, progress)
                    ? remindQuest(context, definition)
                    : result(
                            "locked_to_villager",
                            lineId(definition, "unavailable"),
                            startBlockedLine(context, definition, progress),
                            replacements(context, definition, progress));
        }
        if (!canStart(context, definition, progress)) {
            return result(
                    startBlockedStatus(definition, progress),
                    lineId(definition, "unavailable"),
                    startBlockedLine(context, definition, progress),
                    replacements(context, definition, progress));
        }

        LocatedTarget target = locateInitialTarget(context, definition).orElse(null);
        if (target == null && requiresLocatedTarget(definition)) {
            return result(
                    "locate_failed",
                    lineId(definition, "locate_failed"),
                    definition.dialogue().selectLocateFailed(context.random()),
                    replacements(context, definition, progress));
        }

        VillagerQuestSavedData.QuestProgress started = data.getOrCreate(context.player().getUUID(), definition.id());
        started.start(
                context.villager().getUUID(),
                context.level().dimension(),
                target == null ? null : target.pos(),
                context.level().getGameTime());
        started.setIssuer(
                context.villager().getUUID(),
                VillagerPresetNameRegistry.resolveDisplayName(context.villager()).getString(),
                VillagerProfessionUtil.id(context.profession()).toString(),
                context.level().dimension(),
                context.villager().blockPosition());
        if (target != null && !target.objectiveId().isBlank()) {
            started.setTarget(context.villager().getUUID(), context.level().dimension(), target.pos(), target.objectiveId());
        }
        markContinuousTriggersUsed(started, definition, context.level().getGameTime());
        if (definition.target().hasProofItem() && hasRequiredProof(context.player(), definition)) {
            started.markHasProof();
        }
        data.setDirty();
        if (target != null) {
            rememberQuestStoryHint(context, definition, target.pos());
        }
        sendQuestNotification(context, "quest.started", definition, started, "Quest started: {quest}");
        if (dispatchQuestTriggers(context, definition, started, QuestDefinition.TriggerEvent.STARTED)) {
            data.setDirty();
        }
        sendTrackerSync(context.player(), true);

        return result(
                "started",
                lineId(definition, "start"),
                definition.dialogue().selectStart(context.random()),
                replacements(context, definition, started));
    }

    private static QuestActionOutcome remindQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !matchesVillagerLock(context, definition, progress)) {
            return result(
                    "unavailable",
                    lineId(definition, "unavailable"),
                    definition.dialogue().selectUnavailable(context.random()),
                    replacements(context, definition, progress));
        }
        if (!activeConditionsMet(context, definition)) {
            return result(
                    "inactive",
                    lineId(definition, "inactive"),
                    definition.dialogue().selectInactive(context.random()),
                    replacements(context, definition, progress));
        }
        return result(
                "reminder",
                lineId(definition, "reminder"),
                definition.dialogue().selectReminder(context.random()),
                replacements(context, definition, progress));
    }

    private static QuestActionOutcome turnInQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !matchesVillagerLock(context, definition, progress)) {
            return result(
                    "unavailable",
                    lineId(definition, "unavailable"),
                    definition.dialogue().selectUnavailable(context.random()),
                    replacements(context, definition, progress));
        }
        if (!activeConditionsMet(context, definition)) {
            return result(
                    "inactive",
                    lineId(definition, "inactive"),
                    definition.dialogue().selectInactive(context.random()),
                    replacements(context, definition, progress));
        }
        if (definition.target().hasStructureTarget() && !progress.visitedTarget()) {
            return result(
                    "missing_target",
                    lineId(definition, "missing_target"),
                    definition.dialogue().selectMissingTarget(context.random()),
                    replacements(context, definition, progress));
        }
        if (!hasRequiredProof(context.player(), definition)) {
            return result(
                    "missing_proof",
                    lineId(definition, "missing_proof"),
                    definition.dialogue().selectMissingProof(context.random()),
                    replacements(context, definition, progress));
        }
        if (!requiredObjectivesComplete(context.player(), context, definition, progress)) {
            return result(
                    "missing_objectives",
                    lineId(definition, "missing_objectives"),
                    "There is still more to do before this is ready.",
                    replacements(context, definition, progress));
        }

        progress.markHasProof();
        progress.complete(context.level().getGameTime(), definition.rules().consumeOnCompletion());
        data.setDirty();
        awardRewards(context, definition);
        sendQuestNotification(context, "quest.completed", definition, progress, "Quest completed: {quest}");
        if (dispatchQuestTriggers(context, definition, progress, QuestDefinition.TriggerEvent.COMPLETED)) {
            data.setDirty();
        }
        sendTrackerSync(context.player(), true);

        return result(
                "completed",
                lineId(definition, "turn_in"),
                definition.dialogue().selectTurnIn(context.random()),
                replacements(context, definition, progress));
    }

    private static QuestActionOutcome abandonQuest(DialogueContext context, QuestDefinition definition) {
        VillagerQuestSavedData data = VillagerQuestSavedData.get(context.level());
        VillagerQuestSavedData.QuestProgress progress = data.get(context.player().getUUID(), definition.id());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !matchesVillagerLock(context, definition, progress)) {
            return result(
                    "unavailable",
                    lineId(definition, "unavailable"),
                    definition.dialogue().selectUnavailable(context.random()),
                    replacements(context, definition, progress));
        }

        boolean consume = definition.rules().consumeOnAbandonment()
                || definition.rules().abandonment() == QuestDefinition.AbandonmentMode.REMOVE_FOREVER;
        progress.abandon(context.level().getGameTime(), consume);
        data.setDirty();
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
                        ? "I will close my notes on that journey."
                        : "I will fold the map away for now.",
                replacements(context, definition, progress));
    }

    private static boolean canStart(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (!definition.offer().matches(context)) {
            return false;
        }
        if (progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED) {
            return withinStartLimit(definition, progress) && withinCompletionLimit(definition, progress);
        }
        if (!definition.rules().crossVillagerCompatible()
                && progress.startedVillagerId() != null
                && !progress.startedVillagerId().equals(context.villager().getUUID())) {
            return false;
        }
        if (!withinStartLimit(definition, progress) || !withinCompletionLimit(definition, progress)) {
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

    private static boolean withinCompletionLimit(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        int maxCompletions = definition.rules().maxCompletions();
        return maxCompletions <= 0 || progress == null || progress.completionCount() < maxCompletions;
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

    private static boolean expirationConditionsMet(DialogueContext context, QuestDefinition definition) {
        QuestDefinition.Expiration expiration = definition.rules().expiration();
        if (expiration.conditions().isEmpty()) {
            return false;
        }
        if (context == null) {
            return false;
        }
        for (DialogueCondition condition : expiration.conditions()) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
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
        if (!definition.rules().activeState().hasConditions()) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return activeConditionsMet(contextForStartedVillager(level, player, progress).orElse(null), definition);
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
        if (!expiredByTime && !expirationConditionsMet(context, definition)) {
            return false;
        }

        progress.expire(gameTime, expiration.consume());
        if (expiration.sendNotification()) {
            sendQuestExpiredNotification(player, context, definition, progress);
        }
        if (context != null) {
            dispatchQuestTriggers(context, definition, progress, QuestDefinition.TriggerEvent.EXPIRED);
        }
        return true;
    }

    private static String startBlockedStatus(QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null) {
            return "unavailable";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.CONSUMED) {
            return "consumed";
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            return "active";
        }
        if (!withinCompletionLimit(definition, progress)
                || (progress.completionCount() > 0 && !definition.rules().repeatable())) {
            return "already_completed";
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
        if (progress.state() == VillagerQuestSavedData.QuestState.COMPLETED
                && definition.rules().completionCooldownTicks() > 0L) {
            return "completion_cooldown";
        }
        return "unavailable";
    }

    private static String startBlockedLine(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress != null && progress.completionCount() > 0) {
            return definition.dialogue().selectAlreadyCompleted(context.random());
        }
        if (progress != null
                && progress.startedVillagerId() != null
                && !definition.rules().crossVillagerCompatible()
                && !progress.startedVillagerId().equals(context.villager().getUUID())) {
            return "That quest belongs to " + issuerSummary(context.player(), progress) + ".";
        }
        if (progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ABANDONED
                && definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN
                && !cooldownElapsed(context.level().getGameTime(), progress.abandonedGameTime(), definition.rules().abandonmentCooldownTicks())) {
            return "Give that path a little time, then return to " + issuerSummary(context.player(), progress) + ".";
        }
        return definition.dialogue().selectUnavailable(context.random());
    }

    private static boolean matchesState(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String state) {
        String normalized = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
        boolean completed = progress != null && progress.completionCount() > 0;
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
            case "unavailable" -> !canStart(context, definition, progress) && !active;
            case "not_completed" -> !completed;
            default -> false;
        };
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

    private static boolean requiresLocatedTarget(QuestDefinition definition) {
        return definition.target().hasStructureTarget()
                || definition.objectives().stream()
                .anyMatch(objective -> !objective.optional()
                        && objective.type() == QuestDefinition.ObjectiveType.STRUCTURE_VISIT);
    }

    private static Optional<LocatedTarget> locateInitialTarget(DialogueContext context, QuestDefinition definition) {
        if (definition.target().hasStructureTarget()) {
            return locateTarget(context.level(), context.villager().blockPosition(), definition)
                    .map(pos -> new LocatedTarget(pos.pos(), ""));
        }
        Optional<QuestDefinition.Objective> structureObjective = definition.objectives().stream()
                .filter(objective -> objective.type() == QuestDefinition.ObjectiveType.STRUCTURE_VISIT)
                .findFirst();
        if (structureObjective.isPresent()) {
            QuestDefinition.Objective objective = structureObjective.get();
            return locateTarget(context.level(), context.villager().blockPosition(), objective)
                    .map(pos -> new LocatedTarget(pos.pos(), objective.id()));
        }
        return Optional.empty();
    }

    private static Optional<LocatedTarget> locateTarget(ServerLevel level, BlockPos origin, QuestDefinition definition) {
        if (!definition.target().hasStructureTarget() || !level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return Optional.empty();
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, definition.target().structure());
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(structureKey);
        if (holder.isEmpty()) {
            return Optional.empty();
        }

        Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator().findNearestMapStructure(
                level,
                HolderSet.direct(holder.get()),
                origin,
                definition.target().searchRadius(),
                false
        );
        return nearest == null ? Optional.empty() : Optional.of(new LocatedTarget(nearest.getFirst()));
    }

    private static Optional<LocatedTarget> locateTarget(ServerLevel level, BlockPos origin, QuestDefinition.Objective objective) {
        if (objective.structure() == null || !level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return Optional.empty();
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, objective.structure());
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(structureKey);
        if (holder.isEmpty()) {
            return Optional.empty();
        }

        Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator().findNearestMapStructure(
                level,
                HolderSet.direct(holder.get()),
                origin,
                objective.searchRadius(),
                false
        );
        return nearest == null ? Optional.empty() : Optional.of(new LocatedTarget(nearest.getFirst(), objective.id()));
    }

    private static boolean isAtQuestTarget(
            ServerLevel level,
            BlockPos playerPos,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress.targetDimension() == null
                || progress.targetPos() == null
                || level.dimension() != progress.targetDimension()
                || !definition.target().hasStructureTarget()) {
            return false;
        }
        double targetTolerance = Math.max(512.0D, (double) definition.target().discoveryRadius() * 4.0D);
        if (playerPos.distSqr(progress.targetPos()) > targetTolerance * targetTolerance) {
            return false;
        }

        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, definition.target().structure());
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(structureKey);
        if (holder.isEmpty()) {
            return false;
        }

        StructureStart start = level.structureManager().getStructureWithPieceAt(playerPos, HolderSet.direct(holder.get()));
        if (!start.isValid()) {
            return false;
        }
        if (definition.target().pieces().isEmpty()) {
            return true;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (!piece.getBoundingBox().isInside(playerPos)) {
                continue;
            }
            if (matchesStructurePiece(piece, definition.target().pieces())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAtObjectiveTarget(
            ServerLevel level,
            BlockPos playerPos,
            QuestDefinition.Objective objective,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress.targetDimension() == null
                || progress.targetPos() == null
                || level.dimension() != progress.targetDimension()
                || objective.structure() == null
                || !objective.id().equals(progress.targetObjectiveId())) {
            return false;
        }
        double targetTolerance = Math.max(512.0D, (double) objective.discoveryRadius() * 4.0D);
        if (playerPos.distSqr(progress.targetPos()) > targetTolerance * targetTolerance) {
            return false;
        }

        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, objective.structure());
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(structureKey);
        if (holder.isEmpty()) {
            return false;
        }

        StructureStart start = level.structureManager().getStructureWithPieceAt(playerPos, HolderSet.direct(holder.get()));
        if (!start.isValid()) {
            return false;
        }
        if (objective.pieces().isEmpty()) {
            return true;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (!piece.getBoundingBox().isInside(playerPos)) {
                continue;
            }
            if (matchesStructurePiece(piece, objective.pieces())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStructurePiece(StructurePiece piece, List<String> expectedPieces) {
        String pieceDescription = piece instanceof PoolElementStructurePiece poolPiece
                ? poolPiece.getElement().toString()
                : piece.toString();
        for (String expectedPiece : expectedPieces) {
            if (!expectedPiece.isBlank() && pieceDescription.contains(expectedPiece)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRequiredProof(ServerPlayer player, QuestDefinition definition) {
        if (!definition.target().hasProofItem()) {
            return true;
        }
        return hasItemCount(player, definition.target().proofItem(), 1);
    }

    private static boolean hasItemCount(ServerPlayer player, ResourceLocation itemId, int count) {
        if (itemId == null) {
            return false;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
        if (item.isEmpty()) {
            return false;
        }
        int remaining = Math.max(1, count);
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item.get())) {
                remaining -= stack.getCount();
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        return false;
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
                changed |= progress.markObjectiveComplete(objective.id());
                if (objective.id().equals(progress.targetObjectiveId())) {
                    progress.setTarget(progress.startedVillagerId(), progress.targetDimension(), null, "");
                }
            }
        }
        changed |= ensureStructureObjectiveTarget(level, player, definition, progress);
        return changed;
    }

    private static boolean ensureStructureObjectiveTarget(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (definition.target().hasStructureTarget() && !progress.visitedTarget()) {
            return false;
        }
        if (!progress.targetObjectiveId().isBlank() && progress.targetPos() != null) {
            return false;
        }
        Optional<QuestDefinition.Objective> next = definition.objectives().stream()
                .filter(objective -> objective.type() == QuestDefinition.ObjectiveType.STRUCTURE_VISIT)
                .filter(objective -> !progress.objectiveComplete(objective.id()))
                .findFirst();
        if (next.isEmpty()) {
            return false;
        }
        LocatedTarget target = locateTarget(level, player.blockPosition(), next.get()).orElse(null);
        if (target == null) {
            return false;
        }
        progress.setTarget(progress.startedVillagerId(), level.dimension(), target.pos(), target.objectiveId());
        return true;
    }

    private static boolean requiredObjectivesComplete(
            ServerPlayer player,
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (!objective.optional() && !objectiveComplete(player, context, context.level(), definition, progress, objective)) {
                return false;
            }
        }
        return true;
    }

    private static boolean objectiveComplete(
            ServerPlayer player,
            DialogueContext context,
            ServerLevel level,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Objective objective) {
        if (progress.objectiveComplete(objective.id())) {
            return true;
        }
        return switch (objective.type()) {
            case STRUCTURE_VISIT -> isAtObjectiveTarget(level, player.blockPosition(), objective, progress);
            case ITEM_CHECK -> hasItemCount(player, objective.item(), objective.count());
            case CONDITION -> context != null && objective.conditions().stream().allMatch(condition -> condition.matches(context));
        };
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

    private static void awardRewards(DialogueContext context, QuestDefinition definition) {
        QuestDefinition.Rewards rewards = definition.rewards();
        VillagerActionExecutor.awardExperience(context, rewards.experience());
        VillagerActionExecutor.changeReputation(context, rewards.reputation());
        VillagerActionExecutor.spreadGossip(context, rewards.gossipReputation());
        VillagerActionExecutor.rememberMemory(context, rewards.memoryEvent());
        VillagerActionExecutor.giveLoot(context, rewards.lootTable());
        context.villager().playSound(SoundEvents.PLAYER_LEVELUP, 0.55F, 1.1F);
    }

    private static void rememberQuestStoryHint(DialogueContext context, QuestDefinition definition, BlockPos targetPos) {
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
                targetPos,
                context.level().getGameTime() + QUEST_STORY_HINT_TICKS,
                definition.target().discoveryRadius()
        );
    }

    private static boolean dispatchQuestTriggers(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.TriggerEvent event) {
        if (!(player.level() instanceof ServerLevel level) || definition.triggers().isEmpty()) {
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
        if (definition.triggers().isEmpty() || progress == null) {
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
            sendQuestNotification(
                    context,
                    expiration.notificationTrigger(),
                    definition,
                    progress,
                    expiration.notificationText());
            return;
        }
        Map<String, String> replacements = trackerReplacements(player, definition, progress, true);
        VillagerReputationNetworking.sendNotice(
                player,
                VillagerDialogueResources.resolveTemplate(expiration.notificationText(), replacements),
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
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
                boolean activeConditionsMet = activeConditionsMetForPlayer(player, definition, entry.getValue());
                if (!activeConditionsMet && definition.rules().activeState().hideWhenUnmet()) {
                    continue;
                }
                entries.add(trackerEntry(player, definition, entry.getValue(), activeConditionsMet));
            }
        }
        PacketDistributor.sendToPlayer(player, new QuestTrackerSyncPayload(entries, flash));
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

    private static QuestTrackerSyncPayload.Entry trackerEntry(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean activeConditionsMet) {
        String stepKey = progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                ? trackerStepKey(player, definition, progress, activeConditionsMet)
                : trackerStateStepKey(player, definition, progress);
        Map<String, String> replacements = trackerReplacements(player, definition, progress, activeConditionsMet);
        QuestDefinition.Step fallback = new QuestDefinition.Step(
                trackerFallbackText(stepKey),
                progress.state() == VillagerQuestSavedData.QuestState.ACTIVE,
                trackerFallbackProgress(stepKey),
                Map.of()
        );
        QuestDefinition.Step step = definition.tracker().step(stepKey, fallback);
        boolean configuredStep = progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && definition.tracker().steps().containsKey(stepKey);
        float progressValue = configuredStep && step.progress() > 0.0F ? step.progress() : trackerFallbackProgress(stepKey);
        boolean showProgress = configuredStep ? step.showProgress() : progress.state() == VillagerQuestSavedData.QuestState.ACTIVE;
        String title = definition.tracker().title().isBlank() ? definition.title() : definition.tracker().title();
        String issuer = issuerSummary(player, progress);
        String issuerLocation = issuerLocationSummary(player, progress);
        String status = trackerStatusText(player, definition, progress, activeConditionsMet);
        return new QuestTrackerSyncPayload.Entry(
                definition.id().toString(),
                VillagerDialogueResources.resolveTemplate(title, replacements),
                VillagerDialogueResources.resolveTemplate(step.text(), replacements),
                metadataText(step.metadata(), replacements, status, issuer),
                Mth.clamp(progressValue, 0.0F, 1.0F),
                showProgress,
                progress.state().name().toLowerCase(Locale.ROOT),
                status,
                issuer,
                issuerLocation,
                questItems(definition, progress)
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

    private static String trackerStepKey(
            ServerPlayer player,
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
                    null,
                    level,
                    definition,
                    progress).orElse(null);
            if (objective != null) {
                if (definition.tracker().steps().containsKey(objective.id())) {
                    return objective.id();
                }
                return switch (objective.type()) {
                    case STRUCTURE_VISIT -> "travel";
                    case ITEM_CHECK -> "proof";
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
            case "return" -> "Return to {issuer}.";
            case "abandoned" -> "Return to {issuer} near {issuer_x}, {issuer_y}, {issuer_z} to pick this back up.";
            case "abandoned_cooldown" -> "Available later. Return to {issuer} near {issuer_x}, {issuer_y}, {issuer_z}.";
            case "expired" -> "Expired. Return to {issuer} near {issuer_x}, {issuer_y}, {issuer_z} if this can be restarted.";
            case "completed" -> "Completed.";
            case "consumed" -> "Unavailable.";
            default -> "Reach the center of {target} near {target_x}, {target_z}.";
        };
    }

    private static float trackerFallbackProgress(String stepKey) {
        return switch (stepKey) {
            case "inactive", "abandoned", "abandoned_cooldown", "expired", "consumed", "not_started" -> 0.0F;
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
        Map<String, String> values = new LinkedHashMap<>();
        values.put("quest", definition.title());
        values.put("quest_id", definition.id().toString());
        values.put("target", targetName(definition));
        values.put("proof_item", questItemName(definition, progress));
        values.put("visited_target", progress != null && progress.visitedTarget() ? "yes" : "no");
        values.put("has_proof", hasRequiredProof(player, definition) ? "yes" : "no");
        values.put("active_conditions", activeConditionsMet ? "met" : "unmet");
        values.put("objective", progress == null ? "" : progress.targetObjectiveId());
        addIssuerReplacements(values, player, progress);

        BlockPos targetPos = progress == null ? null : progress.targetPos();
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

    private static String metadataText(Map<String, String> metadata, Map<String, String> replacements, String status, String issuer) {
        List<String> parts = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            parts.add(status);
        }
        if (issuer != null && !issuer.isBlank()) {
            parts.add("Issued by " + issuer);
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

    private static Map<String, String> replacements(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("quest", definition.title());
        values.put("quest_id", definition.id().toString());
        values.put("target", targetName(definition));
        values.put("proof_item", questItemName(definition, progress));
        values.put("visited_target", progress != null && progress.visitedTarget() ? "yes" : "no");
        values.put("has_proof", hasRequiredProof(context.player(), definition) ? "yes" : "no");
        values.put("active_conditions", activeConditionsMet(context, definition) ? "met" : "unmet");
        values.put("objective", progress == null ? "" : progress.targetObjectiveId());
        addIssuerReplacements(values, context, progress);

        BlockPos targetPos = progress == null ? null : progress.targetPos();
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

    private static void addIssuerReplacements(
            Map<String, String> values,
            DialogueContext context,
            VillagerQuestSavedData.QuestProgress progress) {
        addIssuerReplacements(values, context.player(), progress);
    }

    private static String trackerStatusText(
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean activeConditionsMet) {
        return switch (progress.state()) {
            case ACTIVE -> {
                if (!activeConditionsMet) {
                    yield "Inactive";
                }
                yield "return".equals(trackerStepKey(player, definition, progress, true))
                        ? "Ready to turn in"
                        : "Active";
            }
            case ABANDONED -> {
                if (definition.rules().abandonment() == QuestDefinition.AbandonmentMode.COOLDOWN
                        && !cooldownElapsed(
                                player.level().getGameTime(),
                                progress.abandonedGameTime(),
                                definition.rules().abandonmentCooldownTicks())) {
                    yield "Abandoned - available later";
                }
                yield "Abandoned - return to restart";
            }
            case EXPIRED -> "Expired";
            case COMPLETED -> "Completed";
            case CONSUMED -> "Unavailable";
            case NOT_STARTED -> "Not started";
        };
    }

    private static List<QuestTrackerSyncPayload.QuestItem> questItems(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        Map<String, QuestTrackerSyncPayload.QuestItem> items = new LinkedHashMap<>();
        addQuestItem(items, definition.target().proofItem(), 1);
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.type() != QuestDefinition.ObjectiveType.ITEM_CHECK || objective.item() == null) {
                continue;
            }
            if (progress != null
                    && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                    && progress.objectiveComplete(objective.id())) {
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
        items.putIfAbsent(key, new QuestTrackerSyncPayload.QuestItem(key, itemName(itemId), count));
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

    private static String issuerSummary(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        String name = issuerName(player, progress);
        String profession = issuerProfessionName(player, progress);
        if (profession.isBlank() || "villager".equalsIgnoreCase(profession)) {
            return name;
        }
        return name + " the " + profession;
    }

    private static String issuerName(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        if (progress != null && !progress.issuerName().isBlank()) {
            return progress.issuerName();
        }
        Villager villager = liveStartedVillager(player, progress);
        return villager == null
                ? "Unknown villager"
                : VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
    }

    private static String issuerProfessionName(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        if (progress != null && !progress.issuerProfession().isBlank()) {
            ResourceLocation professionId = ResourceLocation.tryParse(progress.issuerProfession());
            if (professionId != null) {
                return VillagerInteractionTextUtil.resourcePathName(professionId);
            }
        }
        Villager villager = liveStartedVillager(player, progress);
        return villager == null
                ? "villager"
                : VillagerInteractionTextUtil.professionName(villager.getVillagerData().getProfession(), "villager");
    }

    private static String issuerLocationSummary(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        BlockPos pos = issuerPos(player, progress);
        if (pos == null) {
            return "Last seen location unknown";
        }
        String dimension = issuerDimensionText(player, progress);
        return "Last seen near " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + (dimension.isBlank() || "unknown".equals(dimension) ? "" : " in " + dimension);
    }

    private static BlockPos issuerPos(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        if (progress != null && progress.issuerPos() != null) {
            return progress.issuerPos();
        }
        Villager villager = liveStartedVillager(player, progress);
        return villager == null ? null : villager.blockPosition();
    }

    private static String issuerDimensionText(ServerPlayer player, VillagerQuestSavedData.QuestProgress progress) {
        ResourceKey<Level> dimension = progress == null ? null : progress.issuerDimension();
        if (dimension == null) {
            Villager villager = liveStartedVillager(player, progress);
            if (villager != null) {
                dimension = villager.level().dimension();
            }
        }
        return dimension == null ? "unknown" : dimension.location().toString();
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

    private static String lineId(QuestDefinition definition, String stage) {
        return "quest_" + definition.id().toString().replace(':', '_').replace('/', '_') + "_" + stage;
    }

    private record LocatedTarget(BlockPos pos, String objectiveId) {
        private LocatedTarget(BlockPos pos) {
            this(pos, "");
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
}
