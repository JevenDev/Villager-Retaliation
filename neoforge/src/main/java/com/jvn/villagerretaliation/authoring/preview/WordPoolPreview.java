package com.jvn.villagerretaliation.authoring.preview;

import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.DialogueTemplates;
import com.jvn.villagerretaliation.dialogue.DialogueWordHistory;
import com.jvn.villagerretaliation.dialogue.DialogueWordPool;
import com.jvn.villagerretaliation.dialogue.DialogueWordSelection;
import com.jvn.villagerretaliation.dialogue.SpeakingStyle;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.runtime.ReputationProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public final class WordPoolPreview {
    public record EntryResult(ResourceLocation pool, int index, DialogueWordPool.Entry entry,
            ConditionOutcome conditions, boolean eligible, boolean selected) { }
    public record Result(String text, List<DialogueWordSelection.Selection> selections,
            List<EntryResult> entries, List<String> diagnostics) {
        public Result { selections = List.copyOf(selections); entries = List.copyOf(entries); diagnostics = List.copyOf(diagnostics); }
    }
    public record Row(VillagerMood mood, VillagerReputationLevel reputation, String style, Result result, int score, int intensity) {
        public Row(VillagerMood mood, VillagerReputationLevel reputation, String style, Result result) { this(mood, reputation, style, result, 0, 50); }
    }
    public record Finding(String kind, String message) { }
    public record Coverage(List<Row> rows, List<Finding> findings) {
        public Coverage { rows = List.copyOf(rows); findings = List.copyOf(findings); }
    }

    private WordPoolPreview() { }

    public static Result inspect(String template, Function<ResourceLocation, DialogueWordPool> pools,
            PreviewConditions conditions, long seed, DialogueWordHistory history, String historyKey) {
        var selections = new ArrayList<DialogueWordSelection.Selection>();
        var diagnostics = new ArrayList<String>();
        var entries = new ArrayList<EntryResult>();
        var resolver = new DialogueWordSelection(pools,
                rules -> conditions.evaluate(rules) == ConditionOutcome.MATCHED,
                RandomSource.create(seed), history, historyKey, diagnostics::add, selections::add);
        String text = resolver.apply(DialogueTemplates.resolveTemplate(template, conditions.placeholders()));
        diagnostics.addAll(com.jvn.villagerretaliation.dialogue.DialogueWordGrammar.warnings(template, pools));
        for (var selection : selections) {
            var pool = pools.apply(selection.pool());
            for (int i = 0; i < pool.entries().size(); i++) {
                var entry = pool.entries().get(i);
                entries.add(new EntryResult(selection.pool(), i, entry, conditions.evaluate(entry.conditions()),
                        selection.eligible().contains(entry), selection.entry().equals(entry)));
            }
        }
        return new Result(text, selections, entries, diagnostics);
    }

    public static List<Row> matrix(String template, Function<ResourceLocation, DialogueWordPool> pools,
            PreviewConditions base, long seed) {
        return matrix(template, pools, base, seed, VillagerReputationLevel.PROFILE);
    }

    public static List<Row> matrix(String template, Function<ResourceLocation, DialogueWordPool> pools,
            PreviewConditions base, long seed, ReputationProfile<VillagerReputationLevel> profile) {
        var rows = new ArrayList<Row>();
        for (var mood : VillagerMood.values()) for (var reputation : VillagerReputationLevel.values()) for (String style : SpeakingStyle.STYLES) {
            var conditions = base.copy();
            conditions.mood(mood, base.moodIntensity());
            int score = reputation == VillagerReputationLevel.NEUTRAL ? 0 : profile.tiers().stream()
                    .filter(tier -> tier.value() == reputation).findFirst().orElseThrow().threshold().getAsInt();
            conditions.reputation(score, reputation);
            conditions.speakingStyle(style);
            rows.add(new Row(mood, reputation, style, inspect(template, pools, conditions, seed, null, ""), score, base.moodIntensity()));
        }
        return List.copyOf(rows);
    }
    public static Coverage coverage(String template, Function<ResourceLocation, DialogueWordPool> pools, PreviewConditions base, long seed) {
        return coverage(template, pools, base, seed, VillagerReputationLevel.PROFILE);
    }

    public static Coverage coverage(String template, Function<ResourceLocation, DialogueWordPool> pools,
            PreviewConditions base, long seed, ReputationProfile<VillagerReputationLevel> profile) {
        var intensities = new java.util.TreeSet<Integer>();
        var scores = new java.util.TreeSet<Integer>();
        for (var tier : profile.tiers()) boundary(scores, tier.threshold().getAsInt(), Integer.MIN_VALUE, Integer.MAX_VALUE);
        for (var id : com.jvn.villagerretaliation.dialogue.DialogueWordPools.references(template))
            for (var entry : pools.apply(id).entries()) boundaries(entry.conditions(), base, intensities, scores, new java.util.HashSet<>());
        var rows = new ArrayList<>(matrix(template, pools, base, seed, profile));
        var findings = new ArrayList<Finding>();
        for (int intensity : intensities) {
            if (intensity == base.moodIntensity()) continue;
            var simulated = base.copy(); simulated.mood(base.mood(), intensity);
            if (rows.size() + 432 > 8192) { findings.add(new Finding("sampling_limit", "Coverage reached its 8192-scenario budget. Some boundary combinations were not sampled.")); break; }
            rows.addAll(matrix(template, pools, simulated, seed, profile));
        }
        for (int score : scores) for (var mood : VillagerMood.values()) for (String style : SpeakingStyle.STYLES) {
            if (rows.size() >= 8192) break;
            var conditions = base.copy(); conditions.reputation(score, profile.tier(score)); conditions.mood(mood, base.moodIntensity()); conditions.speakingStyle(style);
            rows.add(new Row(mood, profile.tier(score), style, inspect(template, pools, conditions, seed, null, ""), score, base.moodIntensity()));
        }
        if (rows.size() >= 8192 && findings.stream().noneMatch(finding -> finding.kind().equals("sampling_limit")))
            findings.add(new Finding("sampling_limit", "Coverage reached its 8192-scenario budget. Some boundaries were not sampled."));
        var stats = new java.util.TreeMap<String, int[]>();
        var fallbacks = new java.util.TreeMap<String, int[]>();
        var outputs = new java.util.TreeMap<String, Integer>();
        var grammar = new java.util.TreeSet<String>();
        for (var row : rows) {
            var simulated = base.copy(); simulated.reputation(row.score(), row.reputation()); simulated.mood(row.mood(), row.intensity()); simulated.speakingStyle(row.style());
            var candidates = new java.util.HashMap<ResourceLocation, List<DialogueWordPool.Entry>>();
            outputs.merge(row.result().text(), 1, Integer::sum);
            grammar.addAll(row.result().diagnostics());
            for (var selected : row.result().selections()) {
                String key = selected.pool().toString();
                var counts = fallbacks.computeIfAbsent(key, ignored -> new int[3]); counts[0]++;
                if (selected.fallback() && !selected.omitted()) counts[1]++;
                if (!selected.omitted() && selected.eligible().size() == 1) counts[2]++;
            }
            for (var entry : row.result().entries()) {
                String key = entry.pool() + " " + (entry.entry().source().isEmpty() ? "/values/" + entry.index() : entry.entry().source());
                var counts = stats.computeIfAbsent(key, ignored -> new int[4]);
                if (entry.conditions() == ConditionOutcome.MATCHED) counts[0]++;
                if (entry.conditions() == ConditionOutcome.UNKNOWN) counts[1]++;
                if (entry.eligible()) counts[2]++;
                if (entry.conditions() == ConditionOutcome.MATCHED && candidates.computeIfAbsent(entry.pool(),
                        poolId -> pools.apply(poolId).candidates(rules -> simulated.evaluate(rules) == ConditionOutcome.MATCHED).entries()).contains(entry.entry())) counts[3]++;
            }
        }
        stats.forEach((key, counts) -> {
            if (counts[0] == 0 && counts[1] > 0) findings.add(new Finding("unknown_context", key + ": requirements need context that the sampled scenarios cannot evaluate."));
            else if (counts[0] == 0) findings.add(new Finding("not_matched", key + ": conditions did not match any sampled scenario."));
            else if (counts[3] == 0) findings.add(new Finding("shadowed", key + ": matched conditions but was always displaced by priority or the conditional fallback rule in sampled scenarios."));
            else if (counts[2] == 0) findings.add(new Finding("variant_conflict", key + ": priority allowed this entry, but shared variants excluded it in every sampled scenario."));
        });
        fallbacks.forEach((key, counts) -> {
            if (counts[1] * 2 >= counts[0]) findings.add(new Finding("frequent_fallback", key + ": neutral fallback in " + counts[1] + " of " + counts[0] + " sampled scenarios."));
            if (counts[2] > 0) findings.add(new Finding("limited_variety", key + ": only one eligible phrase in " + counts[2] + " sampled scenarios."));
        });
        outputs.entrySet().stream().filter(entry -> entry.getValue() > 1).sorted(java.util.Comparator.<java.util.Map.Entry<String, Integer>>comparingInt(java.util.Map.Entry::getValue).reversed())
                .limit(5).forEach(entry -> findings.add(new Finding("duplicate_output", entry.getValue() + " sampled scenarios render the same wording: " + entry.getKey())));
        grammar.forEach(message -> findings.add(new Finding("diagnostic", message)));
        return new Coverage(rows, findings);
    }

    private static void boundary(java.util.Set<Integer> values, Integer value, int min, int max) {
        if (value == null) return;
        for (long sample = (long) value - 1; sample <= (long) value + 1; sample++) if (sample >= min && sample <= max) values.add((int) sample);
    }

    private static void boundaries(List<com.jvn.villagerretaliation.dialogue.DialogueCondition> conditions, PreviewConditions preview,
            java.util.Set<Integer> intensities, java.util.Set<Integer> scores, java.util.Set<ResourceLocation> active) {
        for (var condition : conditions) {
            if (condition instanceof com.jvn.villagerretaliation.dialogue.DialogueCondition.Mood mood) {
                boundary(intensities, mood.minIntensity(), 0, 100); boundary(intensities, mood.maxIntensity(), 0, 100);
            } else if (condition instanceof com.jvn.villagerretaliation.dialogue.DialogueCondition.Reputation reputation) {
                boundary(scores, reputation.minReputation(), Integer.MIN_VALUE, Integer.MAX_VALUE); boundary(scores, reputation.maxReputation(), Integer.MIN_VALUE, Integer.MAX_VALUE);
            } else if (condition instanceof com.jvn.villagerretaliation.dialogue.DialogueCondition.AllOf all) boundaries(all.conditions(), preview, intensities, scores, active);
            else if (condition instanceof com.jvn.villagerretaliation.dialogue.DialogueCondition.AnyOf any) boundaries(any.conditions(), preview, intensities, scores, active);
            else if (condition instanceof com.jvn.villagerretaliation.dialogue.DialogueCondition.Not not) boundaries(List.of(not.condition()), preview, intensities, scores, active);
            else if (condition instanceof com.jvn.villagerretaliation.dialogue.DialogueCondition.ContextRule context && active.size() < 64 && active.add(context.id())) {
                var rule = preview.contextRule(context.id());
                if (rule != null) boundaries(rule.conditions(), preview, intensities, scores, active);
                active.remove(context.id());
            }
        }
    }

}
