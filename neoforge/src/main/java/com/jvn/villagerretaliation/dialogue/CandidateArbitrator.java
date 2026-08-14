package com.jvn.villagerretaliation.dialogue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.util.RandomSource;

/** Shared priority -> chance -> weight -> fallback arbitration contract. */
public final class CandidateArbitrator {
    private CandidateArbitrator() {
    }

    public static <T> List<T> ordered(List<Candidate<T>> candidates, RandomSource random) {
        if (candidates == null || candidates.isEmpty() || random == null) {
            return List.of();
        }
        Map<Integer, List<Candidate<T>>> tiers = new LinkedHashMap<>();
        candidates.stream()
                .filter(candidate -> candidate != null && candidate.eligible() && candidate.weight() > 0)
                .sorted(Comparator.comparingInt((Candidate<T> candidate) -> candidate.priority()).reversed()
                        .thenComparing(Candidate::id))
                .forEach(candidate -> tiers.computeIfAbsent(candidate.priority(), ignored -> new ArrayList<>()).add(candidate));

        List<T> ordered = new ArrayList<>();
        for (List<Candidate<T>> tier : tiers.values()) {
            List<Candidate<T>> survivors = tier.stream()
                    .filter(candidate -> passesChance(candidate.chance(), random))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            while (!survivors.isEmpty()) {
                long totalWeight = survivors.stream().mapToLong(Candidate::weight).sum();
                long ticket = totalWeight <= Integer.MAX_VALUE
                        ? random.nextInt((int) totalWeight)
                        : Long.remainderUnsigned(random.nextLong(), totalWeight);
                long cumulative = 0L;
                int selected = survivors.size() - 1;
                for (int index = 0; index < survivors.size(); index++) {
                    cumulative += survivors.get(index).weight();
                    if (ticket < cumulative) {
                        selected = index;
                        break;
                    }
                }
                ordered.add(survivors.remove(selected).value());
            }
        }
        return List.copyOf(ordered);
    }

    public static <T> Optional<T> select(
            List<Candidate<T>> candidates,
            RandomSource random,
            Predicate<T> accept) {
        Predicate<T> consumer = accept == null ? ignored -> true : accept;
        return ordered(candidates, random).stream().filter(consumer).findFirst();
    }

    public static <T> T selectOrFallback(
            List<Candidate<T>> candidates,
            RandomSource random,
            Predicate<T> accept,
            Supplier<T> fallback) {
        return select(candidates, random, accept).orElseGet(fallback);
    }

    public static boolean passesChance(double chance, RandomSource random) {
        double bounded = Double.isFinite(chance) ? Math.clamp(chance, 0.0D, 1.0D) : 1.0D;
        return bounded >= 1.0D || bounded > 0.0D && random.nextDouble() < bounded;
    }

    public record Candidate<T>(
            String id,
            T value,
            int priority,
            double chance,
            long weight,
            boolean eligible
    ) {
        public Candidate {
            id = id == null ? "" : id;
            chance = Double.isFinite(chance) ? Math.clamp(chance, 0.0D, 1.0D) : 1.0D;
            weight = Math.max(0L, weight);
        }

        public static <T> Candidate<T> eligible(
                String id, T value, int priority, double chance, long weight) {
            return new Candidate<>(id, value, priority, chance, weight, true);
        }
    }
}
