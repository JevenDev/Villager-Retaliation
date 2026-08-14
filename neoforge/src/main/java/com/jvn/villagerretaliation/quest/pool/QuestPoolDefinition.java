package com.jvn.villagerretaliation.quest.pool;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.util.ContentTags;
import java.util.Map;
import java.util.List;
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
        Map<ResourceLocation, Integer> weights,
        MatchMode matchMode,
        int priority,
        boolean exclusive,
        List<WeightRule> weightRules,
        Map<String, Integer> tagQuotas) {
    public QuestPoolDefinition {
        scope = scope == null ? Scope.PLAYER : scope;
        refreshTicks = Math.max(1L, refreshTicks);
        maxOffers = Math.max(1, Math.min(64, maxOffers));
        antiRepeatRotations = Math.max(0, Math.min(16, antiRepeatRotations));
        defaultWeight = Math.max(1, Math.min(10_000, defaultWeight));
        quests = quests == null ? Set.of() : Set.copyOf(quests);
        anyTags = ContentTags.normalizeAll(anyTags);
        allTags = ContentTags.normalizeAll(allTags);
        excludedQuests = excludedQuests == null ? Set.of() : Set.copyOf(excludedQuests);
        excludedTags = ContentTags.normalizeAll(excludedTags);
        weights = weights == null ? Map.of() : Map.copyOf(weights);
        matchMode = matchMode == null ? MatchMode.ANY : matchMode;
        weightRules = weightRules == null ? List.of() : List.copyOf(weightRules);
        tagQuotas = ContentTags.normalizeKeys(tagQuotas);
    }

    public QuestPoolDefinition(
            ResourceLocation id, boolean enabled, Scope scope, long refreshTicks, int maxOffers,
            int antiRepeatRotations, int defaultWeight, long seedSalt, Set<ResourceLocation> quests,
            Set<String> anyTags, Set<String> allTags, Set<ResourceLocation> excludedQuests,
            Set<String> excludedTags, Map<ResourceLocation, Integer> weights) {
        this(id, enabled, scope, refreshTicks, maxOffers, antiRepeatRotations, defaultWeight, seedSalt,
                quests, anyTags, allTags, excludedQuests, excludedTags, weights,
                MatchMode.ANY, 0, false, List.of(), Map.of());
    }

    public boolean claims(QuestDefinition quest) {
        if (!this.enabled || quest == null || this.excludedQuests.contains(quest.id())) {
            return false;
        }
        Set<String> tags = quest.tags();
        if (tags.stream().anyMatch(this.excludedTags::contains)) {
            return false;
        }
        boolean explicit = this.quests.isEmpty() || this.quests.contains(quest.id());
        boolean all = this.allTags.isEmpty() || tags.containsAll(this.allTags);
        boolean any = this.anyTags.isEmpty() || tags.stream().anyMatch(this.anyTags::contains);
        if (this.matchMode == MatchMode.ALL) {
            return explicit && all && any
                    && (!this.quests.isEmpty() || !this.allTags.isEmpty() || !this.anyTags.isEmpty());
        }
        return (!this.quests.isEmpty() && explicit)
                || (!this.allTags.isEmpty() && all)
                || (!this.anyTags.isEmpty() && any);
    }

    public int weight(QuestDefinition quest) {
        long value = this.weights.containsKey(quest.id())
                ? Math.max(0, this.weights.get(quest.id()))
                : (long) this.defaultWeight * quest.offer().weight();
        for (WeightRule rule : this.weightRules) {
            if (rule.matches(quest.tags())) value *= rule.multiplier();
        }
        return (int) Math.max(0, Math.min(10_000, value));
    }

    public boolean quotaAllows(QuestDefinition candidate, List<QuestDefinition> selected) {
        for (Map.Entry<String, Integer> quota : this.tagQuotas.entrySet()) {
            if (!candidate.tags().contains(quota.getKey())) continue;
            long used = selected.stream().filter(quest -> quest.tags().contains(quota.getKey())).count();
            if (used >= Math.max(0, quota.getValue())) return false;
        }
        return true;
    }

    public enum MatchMode {
        ANY, ALL;

        public static MatchMode parse(String value) {
            return "all".equalsIgnoreCase(value) ? ALL : ANY;
        }
    }

    public record WeightRule(Set<String> anyTags, Set<String> allTags, Set<String> excludeTags, int multiplier) {
        public WeightRule {
            anyTags = ContentTags.normalizeAll(anyTags);
            allTags = ContentTags.normalizeAll(allTags);
            excludeTags = ContentTags.normalizeAll(excludeTags);
            multiplier = Math.max(1, Math.min(100, multiplier));
        }

        public boolean matches(Set<String> tags) {
            return tags.stream().noneMatch(this.excludeTags::contains)
                    && (this.anyTags.isEmpty() || tags.stream().anyMatch(this.anyTags::contains))
                    && (this.allTags.isEmpty() || tags.containsAll(this.allTags));
        }
    }

    public enum Scope {
        PLAYER,
        VILLAGE,
        PROVIDER,
        WORLD,
        DIMENSION;

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
