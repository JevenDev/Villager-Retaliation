package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;

public final class QuestStartRules {
    private QuestStartRules() { }

    public static boolean canStart(QuestDefinition definition, QuestProgressState progress, UUID providerId,
            long gameTime, IntSupplier scopedCompletionCount) {
        if (progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED) {
            return withinStartLimit(definition, progress)
                    && withinCompletionLimit(definition, progress, scopedCompletionCount);
        }
        if (!definition.rules().crossVillagerCompatible() && progress.startedVillagerId() != null
                && !Objects.equals(providerId, progress.startedVillagerId())) return false;
        if (!withinStartLimit(definition, progress)
                || !withinCompletionLimit(definition, progress, scopedCompletionCount)) return false;
        return switch (progress.state()) {
            case ACTIVE, CONSUMED -> false;
            case COMPLETED -> definition.rules().repeatable()
                    && cooldownElapsed(gameTime, progress.completedGameTime(), definition.rules().completionCooldownTicks());
            case FAILED -> definition.rules().repeatable();
            case EXPIRED -> definition.rules().expiration().allowRepickup();
            case ABANDONED -> switch (definition.rules().abandonment()) {
                case REMOVE_FOREVER -> false;
                case ALLOW_REPICKUP -> true;
                case COOLDOWN -> cooldownElapsed(gameTime, progress.abandonedGameTime(), definition.rules().abandonmentCooldownTicks());
            };
            case NOT_STARTED -> true;
        };
    }

    public static boolean withinStartLimit(QuestDefinition definition, QuestProgressState progress) {
        int maxStarts = definition.rules().maxStarts();
        return maxStarts <= 0 || progress == null || progress.startCount() < maxStarts;
    }

    public static boolean withinCompletionLimit(QuestDefinition definition, QuestProgressState progress,
            IntSupplier scopedCompletionCount) {
        int maxCompletions = definition.rules().maxCompletions();
        if (maxCompletions <= 0) return true;
        if (isPlayerCompletionScope(definition.rules().completionScope())) {
            return progress == null || progress.completionCount() < maxCompletions;
        }
        return scopedCompletionCount.getAsInt() < maxCompletions;
    }

    public static boolean isPlayerCompletionScope(QuestDefinition.CompletionScope scope) {
        return scope == null || scope == QuestDefinition.CompletionScope.PLAYER || scope == QuestDefinition.CompletionScope.PLAYER_WORLD;
    }

    public static boolean cooldownElapsed(long gameTime, long eventTime, long cooldownTicks) {
        return cooldownTicks <= 0L || eventTime < 0L || gameTime - eventTime >= cooldownTicks;
    }
}
