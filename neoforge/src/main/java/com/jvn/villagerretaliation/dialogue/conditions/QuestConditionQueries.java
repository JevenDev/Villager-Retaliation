package com.jvn.villagerretaliation.dialogue.conditions;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public interface QuestConditionQueries {
    QuestConditionQueries UNAVAILABLE = new QuestConditionQueries() {
        @Override
        public ConditionOutcome matchesState(ResourceLocation questId, Set<String> states) {
            return ConditionOutcome.UNKNOWN;
        }

        @Override
        public ConditionOutcome ready(QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress) {
            return ConditionOutcome.UNKNOWN;
        }
    };

    ConditionOutcome matchesState(ResourceLocation questId, Set<String> states);

    ConditionOutcome ready(QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress);

    default ConditionOutcome selectedChoice(DialogueCondition.SelectedChoice condition) {
        return ConditionOutcome.UNKNOWN;
    }

    default ConditionOutcome stageHistory(DialogueCondition.StageHistory condition) {
        return ConditionOutcome.UNKNOWN;
    }
}
