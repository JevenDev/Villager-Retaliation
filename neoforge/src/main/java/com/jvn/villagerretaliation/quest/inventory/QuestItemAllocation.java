package com.jvn.villagerretaliation.quest.inventory;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import net.minecraft.world.item.ItemStack;

public final class QuestItemAllocation {
    private QuestItemAllocation() { }

    public record Supply(ItemStack stack, int count) { }
    public record Allocation(int source, QuestDefinition.Objective objective, int count) { }

    public static Optional<List<Allocation>> plan(List<QuestDefinition.Objective> objectives,
            List<Supply> supplies, BiPredicate<QuestDefinition.Objective, ItemStack> matcher) {
        int firstObjective = supplies.size() + 1;
        int sink = firstObjective + objectives.size();
        int[][] capacity = new int[sink + 1][sink + 1];
        var sourceNodes = new IdentityHashMap<ItemStack, Integer>();
        for (int source = 0; source < supplies.size(); source++) {
            var supply = supplies.get(source);
            int available = Math.max(0, Math.min(supply.count(), supply.stack().getCount()));
            Integer existing = sourceNodes.putIfAbsent(supply.stack(), source + 1);
            if (existing != null) {
                capacity[0][existing] = Math.min(capacity[0][existing], available);
                continue;
            }
            capacity[0][source + 1] = available;
            for (int objective = 0; objective < objectives.size(); objective++) {
                var requirement = objectives.get(objective);
                if (available > 0 && matcher.test(requirement, supply.stack())) {
                    capacity[source + 1][firstObjective + objective] = Math.min(available, requirement.count());
                }
            }
        }
        for (int objective = 0; objective < objectives.size(); objective++) {
            capacity[firstObjective + objective][sink] = objectives.get(objective).count();
        }

        int[] parent = new int[sink + 1];
        int[] queue = new int[sink + 1];
        while (augmentingPath(capacity, parent, queue, sink)) {
            int count = Integer.MAX_VALUE;
            for (int node = sink; node != 0; node = parent[node]) {
                count = Math.min(count, capacity[parent[node]][node]);
            }
            for (int node = sink; node != 0; node = parent[node]) {
                capacity[parent[node]][node] -= count;
                // reverse capacity lets stricter objectives reclaim an earlier allocation
                capacity[node][parent[node]] += count;
            }
        }

        List<Allocation> allocations = new ArrayList<>();
        for (int objective = 0; objective < objectives.size(); objective++) {
            int node = firstObjective + objective;
            if (capacity[node][sink] > 0) return Optional.empty();
            for (int source = 0; source < supplies.size(); source++) {
                int count = capacity[node][source + 1];
                if (count > 0) allocations.add(new Allocation(source, objectives.get(objective), count));
            }
        }
        return Optional.of(List.copyOf(allocations));
    }

    private static boolean augmentingPath(int[][] capacity, int[] parent, int[] queue, int sink) {
        Arrays.fill(parent, -1);
        parent[0] = 0;
        int head = 0;
        int tail = 0;
        queue[tail++] = 0;
        while (head < tail) {
            int node = queue[head++];
            for (int next = 1; next <= sink; next++) {
                if (parent[next] != -1 || capacity[node][next] <= 0) continue;
                parent[next] = node;
                if (next == sink) return true;
                queue[tail++] = next;
            }
        }
        return false;
    }
}
