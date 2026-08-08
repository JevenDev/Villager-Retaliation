package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class VillagerGiftPreferences {
    private static final int MAX_POSITIVE_REPUTATION = 120;
    private static final int MAX_NEGATIVE_REPUTATION = -100;

    private VillagerGiftPreferences() {
    }

    public static ResolvedGiftPreference evaluate(ServerLevel level, VillagerProfession profession, ItemStack stack) {
        if (stack.isEmpty()) {
            return ResolvedGiftPreference.neutral();
        }
        return VillagerGiftResources.preference(level, profession, stack)
                .map(preference -> preference.withReputationValue(reputationValue(preference.reaction(), preference.perItemReputation(), stack)))
                .orElseGet(ResolvedGiftPreference::neutral);
    }

    public static ResolvedGiftPreference evaluate(ServerLevel level, Villager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            return ResolvedGiftPreference.neutral();
        }
        return VillagerGiftResources.preference(level, villager, stack)
                .map(preference -> preference.withReputationValue(reputationValue(preference.reaction(), preference.perItemReputation(), stack)))
                .orElseGet(ResolvedGiftPreference::neutral);
    }

    public static List<GiftCandidate> giftCandidates(ServerLevel level, VillagerProfession profession) {
        return VillagerGiftResources.giftCandidates(level, profession);
    }

    public static ItemStack highReputationReward(ServerLevel level, Villager villager, VillagerReputationLevel reputationLevel) {
        return VillagerGiftResources.highReputationReward(level, villager, reputationLevel);
    }

    private static int reputationValue(GiftReaction reaction, int perItemReputation, ItemStack stack) {
        int value = perItemReputation * stack.getCount();
        return Math.clamp(value, MAX_NEGATIVE_REPUTATION, MAX_POSITIVE_REPUTATION);
    }

    public enum GiftReaction {
        LOVED(6),
        LIKED(3),
        NEUTRAL(0),
        DISLIKED(-2),
        HATED(-5);

        private final int defaultPerItemReputation;

        GiftReaction(int defaultPerItemReputation) {
            this.defaultPerItemReputation = defaultPerItemReputation;
        }

        public int defaultPerItemReputation() {
            return this.defaultPerItemReputation;
        }

        public int legacyRating() {
            return switch (this) {
                case LOVED -> 2;
                case LIKED -> 1;
                case NEUTRAL -> 0;
                case DISLIKED -> -1;
                case HATED -> -3;
            };
        }

        public static GiftReaction fromRating(int rating) {
            if (rating >= 2) {
                return LOVED;
            }
            if (rating == 1) {
                return LIKED;
            }
            if (rating == -1) {
                return DISLIKED;
            }
            if (rating <= -2) {
                return HATED;
            }
            return NEUTRAL;
        }

        private boolean isPositive() {
            return this.defaultPerItemReputation > 0;
        }
    }

    public record GiftCandidate(Item item, GiftReaction reaction, boolean professionSpecific) {
        public boolean positive() {
            return this.reaction.isPositive();
        }
    }
}
