package com.jvn.villagerretaliation.quest.pool;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.util.ContentTagDomain;
import com.jvn.villagerretaliation.util.ContentTagQuery;
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
        Map<String, Integer> tagQuotas,
        List<DialogueCondition> conditions) {
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
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public QuestPoolDefinition(
            ResourceLocation id, boolean enabled, Scope scope, long refreshTicks, int maxOffers,
            int antiRepeatRotations, int defaultWeight, long seedSalt, Set<ResourceLocation> quests,
            Set<String> anyTags, Set<String> allTags, Set<ResourceLocation> excludedQuests,
            Set<String> excludedTags, Map<ResourceLocation, Integer> weights) {
        this(id, enabled, scope, refreshTicks, maxOffers, antiRepeatRotations, defaultWeight, seedSalt,
                quests, anyTags, allTags, excludedQuests, excludedTags, weights,
                MatchMode.ANY, 0, false, List.of(), Map.of(), List.of());
    }

    public QuestPoolDefinition(
            ResourceLocation id, boolean enabled, Scope scope, long refreshTicks, int maxOffers,
            int antiRepeatRotations, int defaultWeight, long seedSalt, Set<ResourceLocation> quests,
            Set<String> anyTags, Set<String> allTags, Set<ResourceLocation> excludedQuests,
            Set<String> excludedTags, Map<ResourceLocation, Integer> weights, MatchMode matchMode,
            int priority, boolean exclusive, List<WeightRule> weightRules, Map<String, Integer> tagQuotas) {
        this(id, enabled, scope, refreshTicks, maxOffers, antiRepeatRotations, defaultWeight, seedSalt,
                quests, anyTags, allTags, excludedQuests, excludedTags, weights, matchMode, priority,
                exclusive, weightRules, tagQuotas, List.of());
    }

    public boolean matchesContext(DialogueContext context) {
        return this.enabled && (this.conditions.isEmpty()
                || DialogueCondition.matchesAll(context, this.conditions));
    }

    public boolean claims(QuestDefinition quest) {
        if (!this.enabled || quest == null || this.excludedQuests.contains(quest.id())) {
            return false;
        }
        Set<String> tags = quest.tags();
        ContentTagQuery query = new ContentTagQuery(
                ContentTagDomain.CLASSIFICATION, this.anyTags, this.allTags, this.excludedTags);
        if (tags.stream().anyMatch(query.not()::contains)) {
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
        return weight(quest, null);
    }

    public int weight(QuestDefinition quest, DialogueContext context) {
        double value = this.weights.containsKey(quest.id())
                ? Math.max(0, this.weights.get(quest.id()))
                : (double) this.defaultWeight * quest.offer().weight();
        for (WeightRule rule : this.weightRules) {
            if (rule.matches(quest.tags(), context)) value *= rule.multiplier();
        }
        return (int) Math.max(0, Math.min(10_000, Math.round(value)));
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

    public record WeightRule(
            Set<String> anyTags,
            Set<String> allTags,
            Set<String> excludeTags,
            double multiplier,
            List<DialogueCondition> conditions) {
        public WeightRule(Set<String> anyTags, Set<String> allTags, Set<String> excludeTags, int multiplier) {
            this(anyTags, allTags, excludeTags, (double) multiplier, List.of());
        }

        public WeightRule {
            anyTags = ContentTags.normalizeAll(anyTags);
            allTags = ContentTags.normalizeAll(allTags);
            excludeTags = ContentTags.normalizeAll(excludeTags);
            multiplier = Double.isFinite(multiplier) ? Math.max(0.0D, Math.min(100.0D, multiplier)) : 1.0D;
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
        }

        public boolean matches(Set<String> tags) {
            return matches(tags, null);
        }

        public boolean matches(Set<String> tags, DialogueContext context) {
            return new ContentTagQuery(ContentTagDomain.CLASSIFICATION,
                    this.anyTags, this.allTags, this.excludeTags).matches(tags)
                    && (this.conditions.isEmpty() || DialogueCondition.matchesAll(context, this.conditions));
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
