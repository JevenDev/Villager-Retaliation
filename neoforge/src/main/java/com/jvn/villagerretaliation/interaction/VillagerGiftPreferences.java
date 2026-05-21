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

    public static GiftPreference evaluate(ServerLevel level, VillagerProfession profession, ItemStack stack) {
        if (stack.isEmpty()) {
            return new GiftPreference(GiftReaction.NEUTRAL, false, 0);
        }
        return VillagerGiftResources.preference(level, profession, stack)
                .map(preference -> preference.withReputationValue(reputationValue(preference.reaction(), preference.perItemReputation(), stack)))
                .orElse(new GiftPreference(GiftReaction.NEUTRAL, false, 0));
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

        private boolean isPositive() {
            return this.defaultPerItemReputation > 0;
        }
    }

    public record GiftPreference(GiftReaction reaction, boolean professionSpecific, int reputationValue, int perItemReputation) {
        public GiftPreference(GiftReaction reaction, boolean professionSpecific, int reputationValue) {
            this(reaction, professionSpecific, reputationValue, reaction.defaultPerItemReputation());
        }

        private GiftPreference withReputationValue(int reputationValue) {
            return new GiftPreference(this.reaction, this.professionSpecific, reputationValue, this.perItemReputation);
        }
    }

    public record GiftCandidate(Item item, GiftReaction reaction, boolean professionSpecific) {
        public boolean positive() {
            return this.reaction.isPositive();
        }
    }
}
