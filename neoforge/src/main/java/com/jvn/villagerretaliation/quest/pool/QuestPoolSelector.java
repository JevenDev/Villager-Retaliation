package com.jvn.villagerretaliation.quest.pool;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class QuestPoolSelector {
    private QuestPoolSelector() {
    }

    public static Set<net.minecraft.resources.ResourceLocation> select(
            QuestPoolDefinition pool,
            Collection<QuestDefinition> quests,
            String scopeKey,
            long epoch) {
        List<QuestDefinition> candidates = quests.stream()
                .filter(pool::claims)
                .sorted(Comparator.comparing(quest -> quest.id().toString()))
                .toList();
        if (candidates.isEmpty()) {
            return Set.of();
        }

        Set<net.minecraft.resources.ResourceLocation> recent = new LinkedHashSet<>();
        for (int offset = 1; offset <= pool.antiRepeatRotations(); offset++) {
            recent.addAll(draw(pool, candidates, scopeKey, epoch - offset, Set.of(), pool.maxOffers(), List.of()));
        }
        Set<net.minecraft.resources.ResourceLocation> selected = new LinkedHashSet<>(
                draw(pool, candidates, scopeKey, epoch, recent, pool.maxOffers(), List.of()));
        if (selected.size() < Math.min(pool.maxOffers(), candidates.size())) {
            List<QuestDefinition> selectedQuests = candidates.stream().filter(quest -> selected.contains(quest.id())).toList();
            selected.addAll(draw(pool, candidates, scopeKey + "\u0000backfill", epoch, selected,
                    pool.maxOffers() - selected.size(), selectedQuests));
        }
        return Set.copyOf(selected);
    }

    private static Set<net.minecraft.resources.ResourceLocation> draw(
            QuestPoolDefinition pool,
            List<QuestDefinition> candidates,
            String scopeKey,
            long epoch,
            Set<net.minecraft.resources.ResourceLocation> excluded,
            int limit,
            List<QuestDefinition> initialSelections) {
        List<QuestDefinition> remaining = new ArrayList<>(candidates.stream()
                .filter(quest -> !excluded.contains(quest.id()))
                .toList());
        Set<net.minecraft.resources.ResourceLocation> selected = new LinkedHashSet<>();
        List<QuestDefinition> selectedQuests = new ArrayList<>(initialSelections);
        long state = seed(pool, scopeKey, epoch);
        while (!remaining.isEmpty() && selected.size() < limit) {
            remaining.removeIf(candidate -> !pool.quotaAllows(candidate, selectedQuests));
            if (remaining.isEmpty()) break;
            int totalWeight = remaining.stream().mapToInt(pool::weight).sum();
            state = mix64(state + 0x9E3779B97F4A7C15L);
            int ticket = (int) Long.remainderUnsigned(state, totalWeight);
            int cumulative = 0;
            int chosen = 0;
            for (int index = 0; index < remaining.size(); index++) {
                cumulative += pool.weight(remaining.get(index));
                if (ticket < cumulative) {
                    chosen = index;
                    break;
                }
            }
            QuestDefinition choice = remaining.remove(chosen);
            selected.add(choice.id());
            selectedQuests.add(choice);
        }
        return selected;
    }

    private static long seed(QuestPoolDefinition pool, String scopeKey, long epoch) {
        long value = 0xcbf29ce484222325L;
        byte[] bytes = (pool.id() + "\u0000" + scopeKey).getBytes(StandardCharsets.UTF_8);
        for (byte current : bytes) {
            value ^= current & 0xffL;
            value *= 0x100000001b3L;
        }
        return mix64(value ^ pool.seedSalt() ^ epoch);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
