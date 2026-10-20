package com.jvn.villagerretaliation.quest.conditions;

import com.jvn.villagerretaliation.campaign.CampaignQuestAccess;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.runtime.QuestStartRules;
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
        return canStart(
                context,
                definition,
                progress,
                bypassOfferRequirements,
                bypassOfferRequirements,
                parentCompletionLookup,
                scopedCompletionCounter);
    }

    public static boolean canStart(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            boolean bypassOfferRequirements,
            boolean bypassQuestPools,
            ParentCompletionLookup parentCompletionLookup,
            ScopedCompletionCounter scopedCompletionCounter) {
        // A live dialogue offer is fully described by DialogueContext. Building a
        // QuestExecutionContext here also rebuilds the provider binding and village
        // scope once per quest candidate, which made tracker scans multiplicative.
        if (!CampaignQuestAccess.availabilityIssue(context, definition).isBlank()) return false;
        if (!com.jvn.villagerretaliation.campaign.CampaignCharacterService.matchesProvider(
                context.level().getServer(), definition, context.providerId())) return false;
        if (!bypassOfferRequirements && !definition.offer().matches(context)) {
            return false;
        }
        if (context.villager() != null && !bypassQuestPools && !QuestPoolResources.allows(context, definition)) {
            return false;
        }
        if (!parentCompletionLookup.parentCompleted(context, definition)) {
            return false;
        }
        return QuestStartRules.canStart(definition, progress, context.providerId(), context.level().getGameTime(),
                () -> scopedCompletionCounter.countScopedCompletions(context, definition));
    }

    public static boolean canStart(QuestExecutionContext context,
            com.jvn.villagerretaliation.quest.runtime.QuestProgressModel progress, boolean bypassOfferRequirements,
            java.util.function.Predicate<net.minecraft.resources.ResourceLocation> prerequisiteCompleted,
            java.util.function.IntSupplier scopedCompletions) {
        if (context == null || context.quest() == null || context.player() == null || context.level() == null
                || context.providerType() == null || context.binding() == null) return false;
        if (!context.providerType().id().equals(context.binding().providerType())) return false;
        var definition = context.quest();
        if (!bypassOfferRequirements && !context.providerType().matchesOffer(context, definition)) return false;
        if (!definition.prerequisites().stream().allMatch(prerequisiteCompleted)) return false;
        return QuestStartRules.canStart(definition, progress, context.binding().providerId(),
                context.level().getGameTime(), scopedCompletions);
    }

    public static boolean withinStartLimit(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return QuestStartRules.withinStartLimit(definition, progress);
    }

    public static boolean withinCompletionLimit(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            ScopedCompletionCounter scopedCompletionCounter) {
        return QuestStartRules.withinCompletionLimit(definition, progress,
                () -> scopedCompletionCounter.countScopedCompletions(context, definition));
    }

    public static boolean isPlayerCompletionScope(QuestDefinition.CompletionScope scope) {
        return QuestStartRules.isPlayerCompletionScope(scope);
    }

    public static boolean cooldownElapsed(long gameTime, long eventTime, long cooldownTicks) {
        return QuestStartRules.cooldownElapsed(gameTime, eventTime, cooldownTicks);
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

    public static boolean canStart(QuestExecutionContext context,
            com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress, boolean bypassOfferRequirements,
            java.util.function.Predicate<net.minecraft.resources.ResourceLocation> prerequisiteCompleted,
            java.util.function.IntSupplier scopedCompletions) {
        return canStart(context, (com.jvn.villagerretaliation.quest.runtime.QuestProgressModel) progress, bypassOfferRequirements, prerequisiteCompleted, scopedCompletions);
    }

}
