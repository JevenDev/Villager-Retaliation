package com.jvn.villagerretaliation.quest.provider;

import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import java.util.List;
import java.util.Set;

public final class QuestProviderRegistry {
    private static final List<QuestProviderTypeDescriptor> REGISTRATIONS = List.of(
            new QuestProviderTypeDescriptor(
                    VillagerQuestProviderType.ID,
                    Set.of(
                            QuestExecutionContext.LIVE_PROVIDER,
                            QuestExecutionContext.SAVED_PROVIDER,
                            QuestExecutionContext.DIALOGUE_CONTEXT,
                            QuestExecutionContext.VILLAGER_PROVIDER))
    );

    private QuestProviderRegistry() {
    }

    public static List<QuestProviderTypeDescriptor> descriptors() {
        return REGISTRATIONS;
    }
}
