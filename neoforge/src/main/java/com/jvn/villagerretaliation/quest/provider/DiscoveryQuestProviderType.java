package com.jvn.villagerretaliation.quest.provider;

import com.jvn.villagerretaliation.dialogue.conditions.ConditionContext;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import net.minecraft.resources.ResourceLocation;

public final class DiscoveryQuestProviderType implements QuestProviderType {
    public static final DiscoveryQuestProviderType INSTANCE = new DiscoveryQuestProviderType();
    public static final ResourceLocation ID = VillagerRetaliation.id("discovery");

    private DiscoveryQuestProviderType() { }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean matchesOffer(QuestExecutionContext context, QuestDefinition definition) {
        return context != null && context.liveProviderEntity().isEmpty()
                && VillagerQuestProviderType.INSTANCE.matchesOffer(context, definition);
    }

    @Override
    public ConditionContext conditions(
            QuestExecutionContext context, VillagerQuestSavedData.QuestProgress progress) {
        return VillagerQuestProviderType.INSTANCE.conditions(context, progress);
    }

    @Override
    public boolean matchesIssuerLock(QuestExecutionContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return VillagerQuestProviderType.INSTANCE.matchesIssuerLock(context, definition, progress);
    }
}
