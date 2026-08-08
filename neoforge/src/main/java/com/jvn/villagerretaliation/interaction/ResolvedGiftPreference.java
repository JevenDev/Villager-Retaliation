package com.jvn.villagerretaliation.interaction;

import net.minecraft.resources.ResourceLocation;

public record ResolvedGiftPreference(
        ResourceLocation categoryId,
        int rating,
        VillagerGiftPreferences.GiftReaction reaction,
        boolean professionSpecific,
        int reputationValue,
        int perItemReputation,
        String responseKey,
        GiftCategoryName name,
        GiftPreferenceDefinition.MatchSource source,
        ResourceLocation sourceId) {
    public static ResolvedGiftPreference neutral() {
        return new ResolvedGiftPreference(
                null,
                0,
                VillagerGiftPreferences.GiftReaction.NEUTRAL,
                false,
                0,
                0,
                "",
                GiftCategoryName.EMPTY,
                GiftPreferenceDefinition.MatchSource.NONE,
                null);
    }

    public boolean matched() {
        return this.categoryId != null;
    }

    public ResolvedGiftPreference withReputationValue(int value) {
        return new ResolvedGiftPreference(
                this.categoryId,
                this.rating,
                this.reaction,
                this.professionSpecific,
                value,
                this.perItemReputation,
                this.responseKey,
                this.name,
                this.source,
                this.sourceId);
    }
}
