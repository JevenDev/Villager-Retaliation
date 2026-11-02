package com.jvn.villagerretaliation.dialogue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public final class DialogueWordSelection implements UnaryOperator<String> {
    public record Selection(ResourceLocation pool, DialogueWordPool.Entry entry, boolean fallback,
            String group, String variant, boolean avoidedRecent, List<DialogueWordPool.Entry> eligible, boolean omitted) {
        public Selection { eligible = List.copyOf(eligible); }
    }

    private final Function<ResourceLocation, DialogueWordPool> pools;
    private final Predicate<List<DialogueCondition>> matches;
    private final RandomSource random;
    private final DialogueWordHistory history;
    private final String historyKey;
    private final Consumer<Selection> trace;
    private final Map<String, String> variants = new HashMap<>();
    private final UnaryOperator<String> resolver;

    public DialogueWordSelection(Function<ResourceLocation, DialogueWordPool> pools,
            Predicate<List<DialogueCondition>> matches, RandomSource random, DialogueWordHistory history,
            String historyKey, Consumer<String> diagnostic, Consumer<Selection> trace) {
        this.pools = pools;
        this.matches = matches;
        this.random = random;
        this.history = history;
        this.historyKey = historyKey;
        this.trace = trace;
        resolver = DialogueWordPools.selectingResolver(this::select, diagnostic);
    }

    @Override public String apply(String text) { prepare(text); return resolver.apply(text); }

    private record Part(DialogueWordPool.Candidates candidates, boolean optional) { }

    public void prepare(String text) {
        var grouped = new java.util.TreeMap<String, List<Part>>();
        for (var id : DialogueWordPools.references(text).stream().sorted().toList()) {
            var pool = pools.apply(id);
            if (pool.selectionGroup().isEmpty() || variants.containsKey(pool.selectionGroup())) continue;
            var candidates = pool.candidates(matches);
            if (pool.optional() && (candidates.entries().isEmpty() || pool.chance() == 0)) continue;
            grouped.computeIfAbsent(pool.selectionGroup(), ignored -> new java.util.ArrayList<>()).add(new Part(candidates, pool.optional()));
        }
        for (var group : grouped.entrySet()) {
            var possible = new java.util.TreeSet<String>();
            group.getValue().forEach(part -> part.candidates().entries().stream().map(DialogueWordPool.Entry::variant).filter(value -> !value.isEmpty()).forEach(possible::add));
            possible.removeIf(variant -> group.getValue().stream().anyMatch(part -> !part.optional() && part.candidates().entries().stream().noneMatch(entry -> entry.variant().isEmpty() || entry.variant().equals(variant))));
            String selected = "";
            if (possible.size() == 1) selected = possible.first();
            else if (!possible.isEmpty()) {
                var weights = new java.util.LinkedHashMap<String, Double>();
                for (String variant : possible) weights.put(variant, group.getValue().stream().mapToDouble(part -> Math.log(Math.max(1, part.candidates().entries().stream()
                        .filter(entry -> entry.variant().isEmpty() || entry.variant().equals(variant)).mapToInt(DialogueWordPool.Entry::weight).sum()))).sum());
                double max = weights.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                weights.replaceAll((variant, weight) -> Math.exp(weight - max));
                double roll = random.nextDouble() * weights.values().stream().mapToDouble(Double::doubleValue).sum();
                selected = possible.last();
                for (var weight : weights.entrySet()) { roll -= weight.getValue(); if (roll < 0) { selected = weight.getKey(); break; } }
            }
            variants.put(group.getKey(), selected);
        }
    }

    private String select(ResourceLocation id) {
        var pool = pools.apply(id);
        var candidates = pool.candidates(matches);
        var eligible = candidates.entries();
        if (pool.optional() && (eligible.isEmpty() || pool.chance() == 0 || pool.chance() < 1 && random.nextDouble() >= pool.chance())) {
            trace.accept(new Selection(id, new DialogueWordPool.Entry("", 1, List.of()), candidates.fallback(), pool.selectionGroup(),
                    variants.getOrDefault(pool.selectionGroup(), ""), false, eligible, true));
            return "";
        }
        boolean fallback = candidates.fallback();
        String variant = variants.get(pool.selectionGroup());
        if (variant != null) {
            eligible = eligible.stream().filter(entry -> entry.variant().isEmpty() || entry.variant().equals(variant)).toList();
            if (eligible.isEmpty()) {
                var neutral = pool.entries().stream().filter(entry -> entry.conditions().isEmpty() && entry.variant().isEmpty()).toList();
                int priority = neutral.stream().mapToInt(DialogueWordPool.Entry::priority).max().orElse(0);
                eligible = neutral.stream().filter(entry -> entry.priority() == priority).toList();
                fallback = true;
            }
        }
        boolean avoided = false;
        String key = historyKey + "/" + id;
        if (history != null && pool.avoidRecent() > 0) {
            var recent = history.recent(key, pool.avoidRecent());
            var fresh = eligible.stream().filter(entry -> !recent.contains(entry.text())).toList();
            if (!fresh.isEmpty()) { avoided = fresh.size() != eligible.size(); eligible = fresh; }
        }
        var selected = DialogueWordPool.pick(eligible, random);
        if (selected == null) {
            if (!pool.optional()) return null;
            trace.accept(new Selection(id, new DialogueWordPool.Entry("", 1, List.of()), fallback, pool.selectionGroup(),
                    variants.getOrDefault(pool.selectionGroup(), ""), false, eligible, true));
            return "";
        }
        if (!pool.selectionGroup().isEmpty() && !selected.variant().isEmpty())
            variants.putIfAbsent(pool.selectionGroup(), selected.variant());
        if (history != null && pool.avoidRecent() > 0 && !selected.text().isEmpty()) history.remember(key, selected.text());
        trace.accept(new Selection(id, selected, fallback, pool.selectionGroup(),
                variants.getOrDefault(pool.selectionGroup(), ""), avoided, eligible, selected.text().isEmpty()));
        return pool.format(selected.text());
    }
}
