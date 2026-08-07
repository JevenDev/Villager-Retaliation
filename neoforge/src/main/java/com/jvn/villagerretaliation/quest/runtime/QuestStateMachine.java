package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Pure quest lifecycle mutations. Minecraft side effects are returned as follow-up requirements. */
public final class QuestStateMachine {
    private QuestStateMachine() {
    }

    public static TransitionResult start(
            VillagerQuestSavedData.QuestProgress progress,
            UUID providerId,
            ResourceKey<Level> dimension,
            BlockPos target,
            long gameTime) {
        if (progress == null) {
            return blocked(null, "missing_progress");
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
            return blocked(progress, "quest_already_active");
        }
        return mutate(progress, LifecycleEvent.STARTED, "", () -> progress.start(providerId, dimension, target, gameTime),
                FollowUp.DISPATCH_LIFECYCLE, FollowUp.SYNC_TRACKER);
    }

    public static TransitionResult initializeStage(
            VillagerQuestSavedData.QuestProgress progress,
            String stage) {
        String normalized = normalize(stage);
        if (progress == null || normalized.isBlank()) {
            return blocked(progress, "missing_entry_stage");
        }
        return mutate(progress, LifecycleEvent.STAGE_CHANGED, "", () -> progress.setCurrentStage(normalized),
                FollowUp.DISPATCH_LIFECYCLE, FollowUp.RUN_STAGE_ACTIONS, FollowUp.SYNC_TRACKER);
    }

    public static TransitionResult transitionStage(
            VillagerQuestSavedData.QuestProgress progress,
            String stage) {
        String normalized = normalize(stage);
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            return blocked(progress, "quest_not_active");
        }
        if (normalized.isBlank()) {
            return blocked(progress, "missing_target_stage");
        }
        if (normalized.equals(progress.currentStage())) {
            return blocked(progress, "stage_unchanged");
        }
        return mutate(progress, LifecycleEvent.STAGE_CHANGED, "", () -> progress.setCurrentStage(normalized),
                FollowUp.DISPATCH_LIFECYCLE, FollowUp.RUN_STAGE_ACTIONS, FollowUp.SYNC_TRACKER);
    }

    public static TransitionResult complete(
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime,
            boolean consume) {
        if (!active(progress)) {
            return blocked(progress, "quest_not_active");
        }
        return mutate(progress, LifecycleEvent.COMPLETED, "", () -> progress.complete(gameTime, consume),
                FollowUp.DISPATCH_LIFECYCLE, FollowUp.APPLY_REWARDS, FollowUp.SYNC_TRACKER);
    }

    public static TransitionResult fail(
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime,
            String reason) {
        if (!active(progress)) {
            return blocked(progress, "quest_not_active");
        }
        String normalized = normalizeCode(reason, "unspecified_failure");
        return mutate(progress, LifecycleEvent.FAILED, normalized, () -> progress.fail(gameTime, normalized),
                FollowUp.DISPATCH_LIFECYCLE, FollowUp.CLEAR_TRACKING, FollowUp.DETACH_PARTY, FollowUp.SYNC_TRACKER);
    }

    public static TransitionResult abandon(
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime,
            boolean consume) {
        if (!active(progress)) {
            return blocked(progress, "quest_not_active");
        }
        return mutate(progress, LifecycleEvent.ABANDONED, "", () -> progress.abandon(gameTime, consume),
                FollowUp.DISPATCH_LIFECYCLE, FollowUp.CLEAR_TRACKING, FollowUp.DETACH_PARTY, FollowUp.SYNC_TRACKER);
    }

    public static TransitionResult expire(
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime,
            boolean consume) {
        if (!active(progress)) {
            return blocked(progress, "quest_not_active");
        }
        return mutate(progress, LifecycleEvent.EXPIRED, "", () -> progress.expire(gameTime, consume),
                FollowUp.DISPATCH_LIFECYCLE, FollowUp.CLEAR_TRACKING, FollowUp.DETACH_PARTY, FollowUp.SYNC_TRACKER);
    }

    public static TransitionResult consume(
            VillagerQuestSavedData.QuestProgress progress,
            String reason) {
        if (progress == null) {
            return blocked(null, "missing_progress");
        }
        if (progress.state() == VillagerQuestSavedData.QuestState.CONSUMED) {
            return blocked(progress, "quest_already_consumed");
        }
        String normalized = normalizeCode(reason, "consumed");
        return mutate(progress, LifecycleEvent.CONSUMED, normalized, () -> progress.consume(normalized),
                FollowUp.CLEAR_TRACKING, FollowUp.SYNC_TRACKER);
    }

    public static String normalizeCode(String value, String fallback) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    private static boolean active(VillagerQuestSavedData.QuestProgress progress) {
        return progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE;
    }

    private static TransitionResult mutate(
            VillagerQuestSavedData.QuestProgress progress,
            LifecycleEvent event,
            String code,
            Runnable mutation,
            FollowUp... followUps) {
        VillagerQuestSavedData.QuestState previousState = progress.state();
        String previousStage = progress.currentStage();
        mutation.run();
        boolean dirty = previousState != progress.state() || !previousStage.equals(progress.currentStage());
        return new TransitionResult(
                previousState,
                progress.state(),
                previousStage,
                progress.currentStage(),
                event,
                dirty,
                dirty ? "" : "state_unchanged",
                code,
                followUps.length == 0 ? Set.of() : Set.copyOf(EnumSet.of(followUps[0], followUps)));
    }

    private static TransitionResult blocked(VillagerQuestSavedData.QuestProgress progress, String blocker) {
        VillagerQuestSavedData.QuestState state = progress == null
                ? VillagerQuestSavedData.QuestState.NOT_STARTED
                : progress.state();
        String stage = progress == null ? "" : progress.currentStage();
        return new TransitionResult(state, state, stage, stage, LifecycleEvent.NONE, false, blocker, "", Set.of());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public enum LifecycleEvent {
        NONE,
        STARTED,
        STAGE_CHANGED,
        COMPLETED,
        FAILED,
        ABANDONED,
        EXPIRED,
        CONSUMED
    }

    public enum FollowUp {
        DISPATCH_LIFECYCLE,
        RUN_STAGE_ACTIONS,
        APPLY_REWARDS,
        CLEAR_TRACKING,
        DETACH_PARTY,
        SYNC_TRACKER
    }

    public record TransitionResult(
            VillagerQuestSavedData.QuestState previousState,
            VillagerQuestSavedData.QuestState newState,
            String previousStage,
            String newStage,
            LifecycleEvent lifecycleEvent,
            boolean dirty,
            String blockerCode,
            String failureCode,
            Set<FollowUp> requiredFollowUps) {
        public TransitionResult {
            previousStage = normalize(previousStage);
            newStage = normalize(newStage);
            blockerCode = normalize(blockerCode);
            failureCode = normalize(failureCode);
            requiredFollowUps = requiredFollowUps == null ? Set.of() : Set.copyOf(requiredFollowUps);
        }
    }
}
