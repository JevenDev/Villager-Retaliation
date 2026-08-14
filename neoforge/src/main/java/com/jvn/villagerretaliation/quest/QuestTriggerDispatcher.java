package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.CandidateArbitrator;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTrigger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class QuestTriggerDispatcher {
    private QuestTriggerDispatcher() {
    }

    @FunctionalInterface
    public interface TriggerActionRunner {
        boolean run(
                QuestTriggerContext triggerContext,
                QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress,
                QuestDefinition.Trigger trigger);
    }

    public static QuestTriggerDispatchResult dispatch(
            DialogueContext context,
            CompiledQuest quest,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.TriggerEvent event,
            TriggerActionRunner actionRunner) {
        if (context == null || context.level() == null || quest == null) {
            return QuestTriggerDispatchResult.empty();
        }
        return dispatch(
                QuestTriggerContext.of(context, event, context.level().getGameTime(), progress.currentStage(), null),
                quest,
                progress,
                actionRunner);
    }

    public static QuestTriggerDispatchResult dispatch(
            QuestTriggerContext triggerContext,
            CompiledQuest quest,
            VillagerQuestSavedData.QuestProgress progress,
            TriggerActionRunner actionRunner) {
        if (triggerContext == null || quest == null) {
            return QuestTriggerDispatchResult.empty();
        }
        return dispatchAtGameTime(
                triggerContext,
                quest.asQuestDefinition(),
                quest.triggerIndex(),
                progress,
                actionRunner);
    }

    public static QuestTriggerDispatchResult dispatchAtGameTime(
            DialogueContext context,
            long gameTime,
            QuestDefinition definition,
            QuestTriggerIndex index,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.TriggerEvent event,
            TriggerActionRunner actionRunner) {
        return dispatchAtGameTime(
                new QuestTriggerContext(event, gameTime,
                        progress == null ? "" : progress.currentStage(), Map.of(), context),
                definition,
                index,
                progress,
                actionRunner);
    }

    public static QuestTriggerDispatchResult dispatchAtGameTime(
            QuestTriggerContext triggerContext,
            QuestDefinition definition,
            QuestTriggerIndex index,
            VillagerQuestSavedData.QuestProgress progress,
            TriggerActionRunner actionRunner) {
        QuestDefinition.TriggerEvent event = triggerContext == null ? null : triggerContext.event();
        if (definition == null || index == null || progress == null || event == null || actionRunner == null) {
            return QuestTriggerDispatchResult.empty();
        }

        String dispatchStage = triggerContext.stage().isBlank()
                ? progress.currentStage()
                : triggerContext.stage();
        List<CompiledQuestTrigger> candidates = index.candidates(event, dispatchStage).stream()
                .sorted(Comparator.comparingInt((CompiledQuestTrigger candidate) -> candidate.definition().priority())
                        .reversed().thenComparingInt(CompiledQuestTrigger::index))
                .toList();
        int evaluated = candidates.size();
        List<CompiledQuestTrigger> eligible = new ArrayList<>();
        for (CompiledQuestTrigger compiledTrigger : candidates) {
            if (matches(triggerContext, progress, compiledTrigger.definition(), event, dispatchStage)) {
                eligible.add(compiledTrigger);
            }
        }
        net.minecraft.util.RandomSource random = triggerContext.dialogueContext() == null
                ? net.minecraft.util.RandomSource.create(triggerContext.gameTime() ^ definition.id().hashCode())
                : triggerContext.dialogueContext().random();
        List<CompiledQuestTrigger> ordered = CandidateArbitrator.ordered(
                eligible.stream().map(candidate -> CandidateArbitrator.Candidate.eligible(
                        candidate.definition().id(),
                        candidate,
                        candidate.definition().priority(),
                        candidate.definition().chance(),
                        candidate.definition().weight())).toList(),
                random);
        int matched = ordered.size();
        int ran = 0;
        boolean dirty = false;
        for (CompiledQuestTrigger compiledTrigger : ordered) {
            QuestDefinition.Trigger trigger = compiledTrigger.definition();
            if (actionRunner.run(triggerContext, definition, progress, trigger)) {
                progress.markTriggerUsed(trigger.id(), triggerContext.gameTime());
                dirty = true;
                ran++;
                if (trigger.exclusive()) {
                    break;
                }
            }
        }
        return new QuestTriggerDispatchResult(
                dirty,
                new QuestTriggerDispatchTrace(candidates.size(), evaluated, matched, ran));
    }

    public static void markContinuousTriggersUsed(
            VillagerQuestSavedData.QuestProgress progress,
            QuestTriggerIndex index,
            long gameTime) {
        if (progress == null || index == null) {
            return;
        }
        for (CompiledQuestTrigger compiledTrigger : index.continuousTriggers()) {
            QuestDefinition.Trigger trigger = compiledTrigger.definition();
            if (trigger.repeatable()
                    && QuestTriggerRegistry.isContinuous(trigger.event())
                    && trigger.cooldownTicks() > 0L) {
                progress.markTriggerUsed(trigger.id(), gameTime);
            }
        }
    }

    private static boolean matches(
            QuestTriggerContext triggerContext,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Trigger trigger,
            QuestDefinition.TriggerEvent event,
            String dispatchStage) {
        DialogueContext context = triggerContext.dialogueContext();
        long gameTime = triggerContext.gameTime();
        if (trigger == null || trigger.event() != event) {
            return false;
        }
        if (trigger.event() == QuestDefinition.TriggerEvent.PROXIMITY) {
            if (context == null || context.player() == null || context.villager() == null) {
                return false;
            }
            double radius = trigger.radius();
            if (context.player().distanceToSqr(context.villager()) > radius * radius) {
                return false;
            }
        }
        if (!trigger.stages().isEmpty() && !trigger.stages().contains(dispatchStage)) {
            return false;
        }
        long lastTriggered = progress.lastTriggerGameTime(trigger.id());
        if (!trigger.repeatable() && lastTriggered >= 0L) {
            return false;
        }
        if (trigger.cooldownTicks() > 0L) {
            if (lastTriggered >= 0L && gameTime - lastTriggered < trigger.cooldownTicks()) {
                return false;
            }
            if (lastTriggered < 0L
                    && QuestTriggerRegistry.isContinuous(trigger.event())
                    && progress.startedGameTime() >= 0L
                    && gameTime - progress.startedGameTime() < trigger.cooldownTicks()) {
                return false;
            }
        }
        return trigger.conditions().isEmpty()
                || DialogueCondition.matchesAll(context, triggerContext, trigger.conditions());
    }
}
