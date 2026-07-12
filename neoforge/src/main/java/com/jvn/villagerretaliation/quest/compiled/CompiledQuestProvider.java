package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import net.minecraft.resources.ResourceLocation;

public record CompiledQuestProvider(ResourceLocation providerType, QuestDefinition.Offer offer) {
    public CompiledQuestProvider(QuestDefinition.Offer offer) {
        this(VillagerQuestProviderType.ID, offer);
    }

    public CompiledQuestProvider {
        providerType = providerType == null ? VillagerQuestProviderType.ID : providerType;
        offer = offer == null ? QuestDefinition.Offer.any() : offer;
    }
}
