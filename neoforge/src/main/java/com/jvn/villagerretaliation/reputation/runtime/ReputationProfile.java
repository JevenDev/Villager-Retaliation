package com.jvn.villagerretaliation.reputation.runtime;

import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

public record ReputationProfile<T>(T neutral, List<Tier<T>> tiers) {
    public ReputationProfile {
        Objects.requireNonNull(neutral, "neutral reputation tier");
        tiers = List.copyOf(tiers);
    }

    public T tier(int score) {
        for (var tier : this.tiers) {
            int threshold = tier.threshold().getAsInt();
            if (tier.atLeast() ? score >= threshold : score <= threshold) return tier.value();
        }
        return this.neutral;
    }

    public record Tier<T>(T value, IntSupplier threshold, boolean atLeast) {
        public Tier {
            Objects.requireNonNull(value, "reputation tier");
            Objects.requireNonNull(threshold, "reputation threshold");
        }

        public Tier(T value, int threshold, boolean atLeast) {
            this(value, () -> threshold, atLeast);
        }
    }
}
