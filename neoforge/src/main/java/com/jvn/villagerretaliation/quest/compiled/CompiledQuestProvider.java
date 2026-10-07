package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import com.jvn.villagerretaliation.quest.provider.QuestProviderDeathProtection;
import net.minecraft.resources.ResourceLocation;

public record CompiledQuestProvider(
        ResourceLocation providerType,
        QuestDefinition.Offer offer,
        QuestProviderDeathProtection deathProtection,
        boolean blocksHiring,
        String campaignRole) {
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

    public CompiledQuestProvider(ResourceLocation providerType, QuestDefinition.Offer offer,
            QuestProviderDeathProtection deathProtection, boolean blocksHiring) {
        this(providerType, offer, deathProtection, blocksHiring, "");
    }

    public CompiledQuestProvider {
        campaignRole = campaignRole == null ? "" : campaignRole;
        if (!campaignRole.isEmpty() && (!campaignRole.matches("[a-z0-9][a-z0-9_./-]*") || campaignRole.length() > 256)) {
            throw new IllegalArgumentException("Invalid campaign role reference.");
        }
        providerType = providerType == null ? VillagerQuestProviderType.ID : providerType;
        offer = offer == null ? QuestDefinition.Offer.any() : offer;
        deathProtection = deathProtection == null ? QuestProviderDeathProtection.NONE : deathProtection;
    }
}
