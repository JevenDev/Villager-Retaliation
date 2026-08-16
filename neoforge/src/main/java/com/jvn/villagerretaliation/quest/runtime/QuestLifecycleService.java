package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class QuestLifecycleService {
    private QuestLifecycleService() {
    }

    public static LifecycleEvent start(
            ResourceLocation questId,
            VillagerQuestSavedData.QuestProgress progress,
            QuestProviderBinding providerBinding,
            VillagerQuestTargets.LocatedTarget target,
            long gameTime,
            UUID playerId,
            UUID sharedRunId) {
        return start(questId, progress, providerBinding, target, gameTime, playerId, sharedRunId, false);
    }

    public static LifecycleEvent start(
            ResourceLocation questId,
            VillagerQuestSavedData.QuestProgress progress,
            QuestProviderBinding providerBinding,
            VillagerQuestTargets.LocatedTarget target,
            long gameTime,
            UUID playerId,
            UUID sharedRunId,
            boolean forceRestart) {
        if (progress == null || providerBinding == null) {
            return event(LifecycleEventType.NONE, questId, progress, gameTime, "missing_start_context");
        }
        QuestStateMachine.TransitionResult result = QuestStateMachine.start(
                progress,
                providerBinding.providerId(),
                target == null ? providerBinding.dimension() : target.dimension(),
                target == null ? null : target.pos(),
                gameTime,
                forceRestart);
        if (!result.dirty()) {
            return blockedEvent(questId, progress, gameTime, result);
        }
        progress.beginRun(playerId, questId, sharedRunId);
        progress.setIssuer(
                providerBinding.providerId(),
                providerBinding.displayName(),
                providerBinding.professionId() == null ? "" : providerBinding.professionId().toString(),
                providerBinding.level(),
                providerBinding.dimension(),
                providerBinding.pos(),
                providerBinding.villageKey());
        if (target != null && !target.objectiveId().isBlank()) {
            progress.setTarget(providerBinding.providerId(), target.dimension(), target.pos(), target.objectiveId());
        }
        return event(LifecycleEventType.STARTED, questId, progress, gameTime, "");
    }

    public static StageTransition initializeStage(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime) {
        String stage = initialStage(definition);
        if (stage.isBlank()) {
            return StageTransition.skipped(progress == null ? "" : progress.currentStage());
        }
        return setStage(definition, progress, stage, gameTime, true);
    }

    public static StageTransition initializeStage(
            CompiledQuest quest,
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime) {
        if (quest == null) {
            return StageTransition.skipped(progress == null ? "" : progress.currentStage());
        }
        String stage = initialStage(quest.asQuestDefinition());
        if (stage.isBlank()) {
            return StageTransition.skipped(progress == null ? "" : progress.currentStage());
        }
        return setStage(quest.asQuestDefinition(), progress, stage, gameTime, true);
    }

    public static String initialStage(QuestDefinition definition) {
        if (definition == null || definition.stages().isEmpty()) {
            return "";
        }
        if (!definition.entryStage().isBlank() && definition.stages().containsKey(definition.entryStage())) {
            return definition.entryStage();
        }
        if (definition.stages().containsKey("started")) {
            return "started";
        }
        return definition.stages().keySet().iterator().next();
    }

    public static boolean canTransitionStage(
            VillagerQuestSavedData.QuestProgress progress,
            String stage) {
        String nextStage = normalizeStage(stage);
        return progress != null
                && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && !nextStage.isBlank()
                && !progress.currentStage().equals(nextStage);
    }

    public static StageTransition transitionStage(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String stage,
            long gameTime) {
        String nextStage = normalizeStage(stage);
        if (definition == null || !canTransitionStage(progress, nextStage)) {
            return StageTransition.unchanged(progress == null ? "" : progress.currentStage());
        }
        return setStage(definition, progress, nextStage, gameTime, false);
    }

    private static StageTransition setStage(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String stage,
            long gameTime,
            boolean allowUnchanged) {
        String nextStage = normalizeStage(stage);
        if (definition == null || progress == null || !definition.stages().containsKey(nextStage)) {
            return StageTransition.unchanged(progress == null ? "" : progress.currentStage());
        }
        String previousStage = progress.currentStage();
        QuestStateMachine.TransitionResult result = allowUnchanged
                ? QuestStateMachine.initializeStage(progress, nextStage)
                : QuestStateMachine.transitionStage(progress, nextStage);
        if (!result.dirty() && !allowUnchanged) {
            return StageTransition.unchanged(progress.currentStage());
        }
        return new StageTransition(
                !previousStage.equals(progress.currentStage()),
                previousStage,
                progress.currentStage(),
                event(LifecycleEventType.STAGE_CHANGED, definition.id(), progress, gameTime, ""));
    }

    public static LifecycleEvent complete(
            ResourceLocation questId,
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime,
            boolean consume) {
        QuestStateMachine.TransitionResult result = QuestStateMachine.complete(progress, gameTime, consume);
        if (!result.dirty()) {
            return blockedEvent(questId, progress, gameTime, result);
        }
        return event(LifecycleEventType.COMPLETED, questId, progress, gameTime, consume ? "completion" : "");
    }

    public static LifecycleEvent abandon(
            ResourceLocation questId,
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime,
            boolean consume) {
        QuestStateMachine.TransitionResult result = QuestStateMachine.abandon(progress, gameTime, consume);
        if (!result.dirty()) {
            return blockedEvent(questId, progress, gameTime, result);
        }
        return event(LifecycleEventType.ABANDONED, questId, progress, gameTime, consume ? "abandonment" : "");
    }

    public static LifecycleEvent fail(
            ResourceLocation questId,
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime,
            String reason) {
        QuestStateMachine.TransitionResult result = QuestStateMachine.fail(progress, gameTime, reason);
        if (!result.dirty()) {
            return blockedEvent(questId, progress, gameTime, result);
        }
        return event(LifecycleEventType.FAILED, questId, progress, gameTime, result.failureCode());
    }

    public static LifecycleEvent expire(
            ResourceLocation questId,
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime,
            boolean consume) {
        QuestStateMachine.TransitionResult result = QuestStateMachine.expire(progress, gameTime, consume);
        if (!result.dirty()) {
            return blockedEvent(questId, progress, gameTime, result);
        }
        return event(LifecycleEventType.EXPIRED, questId, progress, gameTime, consume ? "expiration" : "");
    }

    public static LifecycleEvent consume(
            ResourceLocation questId,
            VillagerQuestSavedData.QuestProgress progress,
            String reason,
            long gameTime) {
        QuestStateMachine.TransitionResult result = QuestStateMachine.consume(progress, reason);
        if (!result.dirty()) {
            return blockedEvent(questId, progress, gameTime, result);
        }
        return event(LifecycleEventType.CONSUMED, questId, progress, gameTime, reason);
    }

    private static LifecycleEvent blockedEvent(
            ResourceLocation questId,
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime,
            QuestStateMachine.TransitionResult result) {
        return event(LifecycleEventType.NONE, questId, progress, gameTime, result.blockerCode());
    }

    private static String normalizeStage(String stage) {
        return stage == null ? "" : stage.trim();
    }

    private static LifecycleEvent event(
            LifecycleEventType type,
            ResourceLocation questId,
            VillagerQuestSavedData.QuestProgress progress,
            long gameTime,
            String reason) {
        return new LifecycleEvent(
                type,
                questId,
                progress == null ? null : progress.state(),
                gameTime,
                progress == null ? "" : progress.currentStage(),
                reason == null ? "" : reason);
    }

    public enum LifecycleEventType {
        NONE,
        STARTED,
        COMPLETED,
        FAILED,
        ABANDONED,
        EXPIRED,
        CONSUMED,
        STAGE_CHANGED
    }

    public record LifecycleEvent(
            LifecycleEventType type,
            ResourceLocation questId,
            VillagerQuestSavedData.QuestState state,
            long gameTime,
            String stage,
            String reason
    ) {
    }

    public record StageTransition(
            boolean changed,
            String previousStage,
            String currentStage,
            LifecycleEvent event
    ) {
        private static StageTransition unchanged(String stage) {
            return new StageTransition(false, stage == null ? "" : stage, stage == null ? "" : stage, null);
        }

        private static StageTransition skipped(String previousStage) {
            return new StageTransition(false, previousStage == null ? "" : previousStage, "", null);
        }
    }
}
