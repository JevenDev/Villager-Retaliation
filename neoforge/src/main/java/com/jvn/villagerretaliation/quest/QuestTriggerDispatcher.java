package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTrigger;
import java.util.Comparator;
import java.util.List;

public final class QuestTriggerDispatcher {
    private QuestTriggerDispatcher() {
    }

    @FunctionalInterface
    public interface TriggerActionRunner {
        boolean run(
                DialogueContext context,
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
        return dispatchAtGameTime(
                context,
                context.level().getGameTime(),
                quest.asQuestDefinition(),
                quest.triggerIndex(),
                progress,
                event,
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
        if (definition == null || index == null || progress == null || event == null || actionRunner == null) {
            return QuestTriggerDispatchResult.empty();
        }

        List<CompiledQuestTrigger> candidates = index.candidates(event, progress.currentStage()).stream()
                .sorted(Comparator.comparingInt((CompiledQuestTrigger candidate) -> candidate.definition().priority())
                        .reversed().thenComparingInt(CompiledQuestTrigger::index))
                .toList();
        int evaluated = 0;
        int matched = 0;
        int ran = 0;
        boolean dirty = false;
        for (CompiledQuestTrigger compiledTrigger : candidates) {
            QuestDefinition.Trigger trigger = compiledTrigger.definition();
            evaluated++;
            if (!matches(context, gameTime, progress, trigger, event)) {
                continue;
            }
            if (trigger.chance() < 1.0D
                    && (context == null || context.random().nextDouble() >= trigger.chance())) {
                continue;
            }
            matched++;
            if (actionRunner.run(context, definition, progress, trigger)) {
                progress.markTriggerUsed(trigger.id(), gameTime);
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
            DialogueContext context,
            long gameTime,
            VillagerQuestSavedData.QuestProgress progress,
            QuestDefinition.Trigger trigger,
            QuestDefinition.TriggerEvent event) {
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
        if (!trigger.stages().isEmpty() && !trigger.stages().contains(progress.currentStage())) {
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
                || context != null && DialogueCondition.matchesAll(context, trigger.conditions());
    }
}
