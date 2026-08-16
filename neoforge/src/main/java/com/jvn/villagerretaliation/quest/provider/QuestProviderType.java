package com.jvn.villagerretaliation.quest.provider;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public interface QuestProviderType {
    ResourceLocation id();

    boolean matchesOffer(QuestExecutionContext context, QuestDefinition definition);

    boolean matchesIssuerLock(
            QuestExecutionContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress);

    default Optional<DialogueContext> legacyDialogueContext(QuestExecutionContext context) {
        return context == null ? Optional.empty() : context.dialogueContext();
    }
}
