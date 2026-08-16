package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import com.jvn.villagerretaliation.quest.provider.QuestProviderDeathProtection;
import net.minecraft.resources.ResourceLocation;

public record CompiledQuestProvider(
        ResourceLocation providerType,
        QuestDefinition.Offer offer,
        QuestProviderDeathProtection deathProtection,
        boolean blocksHiring) {
    public CompiledQuestProvider(QuestDefinition.Offer offer) {
        this(VillagerQuestProviderType.ID, offer, QuestProviderDeathProtection.NONE, false);
    }

    public CompiledQuestProvider(ResourceLocation providerType, QuestDefinition.Offer offer) {
        this(providerType, offer, QuestProviderDeathProtection.NONE, false);
    }

    public CompiledQuestProvider(
            ResourceLocation providerType,
            QuestDefinition.Offer offer,
            QuestProviderDeathProtection deathProtection) {
        this(providerType, offer, deathProtection, false);
    }

    public CompiledQuestProvider {
        providerType = providerType == null ? VillagerQuestProviderType.ID : providerType;
        offer = offer == null ? QuestDefinition.Offer.any() : offer;
        deathProtection = deathProtection == null ? QuestProviderDeathProtection.NONE : deathProtection;
    }
}
