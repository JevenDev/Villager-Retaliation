package com.jvn.villagerretaliation.quest.pool;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record QuestPoolDefinition(
        ResourceLocation id,
        boolean enabled,
        Scope scope,
        long refreshTicks,
        int maxOffers,
        int antiRepeatRotations,
        int defaultWeight,
        long seedSalt,
        Set<ResourceLocation> quests,
        Set<String> anyTags,
        Set<String> allTags,
        Set<ResourceLocation> excludedQuests,
        Set<String> excludedTags,
        Map<ResourceLocation, Integer> weights) {
    public QuestPoolDefinition {
        scope = scope == null ? Scope.PLAYER : scope;
        refreshTicks = Math.max(1L, refreshTicks);
        maxOffers = Math.max(1, Math.min(64, maxOffers));
        antiRepeatRotations = Math.max(0, Math.min(16, antiRepeatRotations));
        defaultWeight = Math.max(1, Math.min(10_000, defaultWeight));
        quests = quests == null ? Set.of() : Set.copyOf(quests);
        anyTags = anyTags == null ? Set.of() : Set.copyOf(anyTags);
        allTags = allTags == null ? Set.of() : Set.copyOf(allTags);
        excludedQuests = excludedQuests == null ? Set.of() : Set.copyOf(excludedQuests);
        excludedTags = excludedTags == null ? Set.of() : Set.copyOf(excludedTags);
        weights = weights == null ? Map.of() : Map.copyOf(weights);
    }

    public boolean claims(QuestDefinition quest) {
        if (!this.enabled || quest == null || this.excludedQuests.contains(quest.id())) {
            return false;
        }
        Set<String> tags = quest.tags();
        if (tags.stream().anyMatch(this.excludedTags::contains)) {
            return false;
        }
        boolean explicit = this.quests.contains(quest.id());
        boolean all = !this.allTags.isEmpty() && tags.containsAll(this.allTags);
        boolean any = !this.anyTags.isEmpty() && tags.stream().anyMatch(this.anyTags::contains);
        return explicit || all || any;
    }

    public int weight(QuestDefinition quest) {
        return Math.max(1, Math.min(10_000, this.weights.getOrDefault(quest.id(), this.defaultWeight)));
    }

    public enum Scope {
        PLAYER,
        VILLAGE,
        PROVIDER,
        WORLD;

        public static Scope parse(String value) {
            if (value == null || value.isBlank()) {
                return PLAYER;
            }
            try {
                return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return PLAYER;
            }
        }
    }
}
