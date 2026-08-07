package com.jvn.villagerretaliation.quest.conditions;

import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import com.jvn.villagerretaliation.quest.pool.QuestPoolResources;

public final class QuestAvailabilityService {
    private QuestAvailabilityService() {
    }

    public static boolean canStart(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean bypassOfferRequirements,
            ParentCompletionLookup parentCompletionLookup,
            ScopedCompletionCounter scopedCompletionCounter) {
        // A live dialogue offer is fully described by DialogueContext. Building a
        // QuestExecutionContext here also rebuilds the provider binding and village
        // scope once per quest candidate, which made tracker scans multiplicative.
        if (!bypassOfferRequirements && !definition.offer().matches(context)) {
            return false;
        }
        if (!bypassOfferRequirements && !QuestPoolResources.allows(context, definition)) {
            return false;
        }
        if (!parentCompletionLookup.parentCompleted(context, definition)) {
            return false;
        }
        if (progress == null || progress.state() == VillagerQuestSavedData.QuestState.NOT_STARTED) {
            return withinStartLimit(definition, progress)
                    && withinCompletionLimit(context, definition, progress, scopedCompletionCounter);
        }
        if (!definition.rules().crossVillagerCompatible()
                && progress.startedVillagerId() != null
                && !context.villager().getUUID().equals(progress.startedVillagerId())) {
            return false;
        }
        if (!withinStartLimit(definition, progress)
                || !withinCompletionLimit(context, definition, progress, scopedCompletionCounter)) {
            return false;
        }
        return switch (progress.state()) {
            case ACTIVE, CONSUMED -> false;
            case COMPLETED -> definition.rules().repeatable()
                    && cooldownElapsed(
                            context.level().getGameTime(),
                            progress.completedGameTime(),
                            definition.rules().completionCooldownTicks());
            case FAILED -> definition.rules().repeatable();
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

    public static boolean withinStartLimit(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        int maxStarts = definition.rules().maxStarts();
        return maxStarts <= 0 || progress == null || progress.startCount() < maxStarts;
    }

    public static boolean withinCompletionLimit(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            ScopedCompletionCounter scopedCompletionCounter) {
        int maxCompletions = definition.rules().maxCompletions();
        if (maxCompletions <= 0) {
            return true;
        }
        if (isPlayerCompletionScope(definition.rules().completionScope())) {
            return progress == null || progress.completionCount() < maxCompletions;
        }
        return scopedCompletionCounter.countScopedCompletions(context, definition) < maxCompletions;
    }

    public static boolean isPlayerCompletionScope(QuestDefinition.CompletionScope scope) {
        return scope == null
                || scope == QuestDefinition.CompletionScope.PLAYER
                || scope == QuestDefinition.CompletionScope.PLAYER_WORLD;
    }

    public static boolean cooldownElapsed(long gameTime, long eventTime, long cooldownTicks) {
        return cooldownTicks <= 0L || eventTime < 0L || gameTime - eventTime >= cooldownTicks;
    }

    public static boolean matchesProviderLock(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return VillagerQuestProviderType.INSTANCE.matchesIssuerLock(
                QuestExecutionContext.fromDialogueContext(context, definition, "issuer_lock"),
                definition,
                progress);
    }

    @FunctionalInterface
    public interface ParentCompletionLookup {
        boolean parentCompleted(DialogueContext context, QuestDefinition definition);
    }

    @FunctionalInterface
    public interface ScopedCompletionCounter {
        int countScopedCompletions(DialogueContext context, QuestDefinition definition);
    }
}
