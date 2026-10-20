package com.jvn.villagerretaliation.quest.provider;

import com.jvn.villagerretaliation.dialogue.conditions.ConditionContext;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public interface QuestProviderType {
    ResourceLocation id();

    boolean matchesOffer(QuestExecutionContext context, QuestDefinition definition);

    default boolean matchesIssuerLock(QuestExecutionContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return matchesIdentityLock(context, definition, progress);
    }

    default ConditionContext conditions(
            QuestExecutionContext context, VillagerQuestSavedData.QuestProgress progress) {
        return com.jvn.villagerretaliation.dialogue.conditions.EntityConditionAccess.forQuest(context, progress);
    }

    default ConditionContext conditions(QuestExecutionContext context,
            com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress) {
        if (progress == null || progress instanceof VillagerQuestSavedData.QuestProgress) {
            return conditions(context, (VillagerQuestSavedData.QuestProgress) progress);
        }
        return com.jvn.villagerretaliation.dialogue.conditions.EntityConditionAccess.forQuest(context, progress);
    }

    default boolean matchesProviderLock(QuestExecutionContext context, QuestDefinition definition,
            com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress) {
        if (progress != null && !progress.issuerProviderType().isBlank()
                && !id().toString().equals(progress.issuerProviderType())) return false;
        if (progress == null || progress instanceof VillagerQuestSavedData.QuestProgress) {
            return matchesIssuerLock(context, definition, (VillagerQuestSavedData.QuestProgress) progress);
        }
        return matchesIdentityLock(context, definition, progress);
    }

    private boolean matchesIdentityLock(QuestExecutionContext context, QuestDefinition definition,
            com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress) {
        if (progress == null || !definition.rules().lockedToVillager() || progress.startedVillagerId() == null) return true;
        return context.providerBinding().map(binding -> binding.matchesProviderId(progress.startedVillagerId())
                || definition.rules().crossVillagerCompatible() && matchesOffer(context, definition)).orElse(false);
    }

    default Optional<DialogueContext> legacyDialogueContext(QuestExecutionContext context) {
        return context == null ? Optional.empty() : context.dialogueContext();
    }
}
