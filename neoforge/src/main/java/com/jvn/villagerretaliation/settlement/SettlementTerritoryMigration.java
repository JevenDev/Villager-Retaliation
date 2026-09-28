package com.jvn.villagerretaliation.settlement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

final class SettlementTerritoryMigration {
    private SettlementTerritoryMigration() {
    }

    static Set<Long> desired(SettlementRecord town, Set<Long> footprint) {
        Set<Long> desired = new TreeSet<>();
        desired.add(new ChunkPos(town.center()).toLong());
        for (long section : footprint) desired.add(ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section)));
        for (var area : town.areas().values()) add(desired, area.min(), area.max());
        for (var depot : town.depots().values()) desired.add(new ChunkPos(depot.position()).toLong());
        for (var project : town.projects().values()) {
            add(desired, project.min(), project.max());
            if (project.workplace() != null) add(desired, project.workplace().min(), project.workplace().max());
            if (project.site() != null) add(desired, project.site().area().min(), project.site().area().max());
        }
        return desired;
    }

    private static void add(Set<Long> chunks, BlockPos min, BlockPos max) {
        for (int x = Math.floorDiv(min.getX(), 16); x <= Math.floorDiv(max.getX(), 16); x++) {
            for (int z = Math.floorDiv(min.getZ(), 16); z <= Math.floorDiv(max.getZ(), 16); z++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }
    }

    static Set<Long> connect(Set<Long> desired, Set<Long> occupied, long founding) {
        var remaining = new TreeSet<>(desired);
        remaining.removeAll(occupied);
        remaining.removeIf(packed -> !SettlementTerritory.validChunk(ChunkPos.getX(packed), ChunkPos.getZ(packed)));
        if (remaining.isEmpty()) return Set.of();
        var components = components(remaining);
        var foundingComponent = components.stream().filter(component -> component.contains(founding)).findFirst();
        if (foundingComponent.isPresent()) return connectFrom(foundingComponent.get(), components, occupied);
        Set<Long> largest = Set.of();
        Set<Long> visited = new java.util.HashSet<>();
        for (var component : components) {
            if (visited.containsAll(component)) continue;
            var connected = connectFrom(component, components, occupied);
            visited.addAll(connected);
            if (connected.size() > largest.size()) largest = connected;
        }
        return largest;
    }

    private static Set<Long> connectFrom(Set<Long> seed, List<Set<Long>> components, Set<Long> occupied) {
        Set<Long> owned = new LinkedHashSet<>(seed);
        for (var component : components) {
            if (owned.containsAll(component)) continue;
            var path = shortestPath(owned, component, occupied);
            if (!path.isEmpty()) {
                owned.addAll(path);
                owned.addAll(component);
            }
        }
        return owned;
    }

    private static List<Set<Long>> components(Set<Long> chunks) {
        var remaining = new TreeSet<>(chunks);
        List<Set<Long>> result = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Set<Long> component = new LinkedHashSet<>();
            var queue = new ArrayDeque<Long>();
            queue.add(remaining.first());
            remaining.remove(queue.peek());
            while (!queue.isEmpty()) {
                long current = queue.remove();
                component.add(current);
                int x = ChunkPos.getX(current), z = ChunkPos.getZ(current);
                for (long neighbor : new long[]{ChunkPos.asLong(x - 1, z), ChunkPos.asLong(x, z - 1),
                        ChunkPos.asLong(x, z + 1), ChunkPos.asLong(x + 1, z)}) {
                    if (remaining.remove(neighbor)) queue.add(neighbor);
                }
            }
            result.add(component);
        }
        return result;
    }

    private static Set<Long> shortestPath(Set<Long> source, Set<Long> target, Set<Long> blocked) {
        var xs = new TreeSet<Integer>();
        var zs = new TreeSet<Integer>();
        for (var group : List.of(source, target, blocked)) {
            for (long packed : group) {
                int x = ChunkPos.getX(packed), z = ChunkPos.getZ(packed);
                for (int offset = -1; offset <= 1; offset++) {
                    if (SettlementTerritory.validChunk(x + offset, z)) xs.add(x + offset);
                    if (SettlementTerritory.validChunk(x, z + offset)) zs.add(z + offset);
                }
            }
        }
        var frontier = new PriorityQueue<Node>(Comparator.comparingLong(Node::distance).thenComparingLong(Node::position));
        Map<Long, Long> distance = new HashMap<>();
        Map<Long, Long> previous = new HashMap<>();
        for (long start : new TreeSet<>(source)) {
            distance.put(start, 0L);
            frontier.add(new Node(start, 0L));
        }
        while (!frontier.isEmpty()) {
            var node = frontier.remove();
            if (node.distance() != distance.get(node.position())) continue;
            if (target.contains(node.position())) return expand(node.position(), previous);
            int x = ChunkPos.getX(node.position()), z = ChunkPos.getZ(node.position());
            var neighbors = new ArrayList<Long>(4);
            if (xs.lower(x) != null) neighbors.add(ChunkPos.asLong(xs.lower(x), z));
            if (zs.lower(z) != null) neighbors.add(ChunkPos.asLong(x, zs.lower(z)));
            if (zs.higher(z) != null) neighbors.add(ChunkPos.asLong(x, zs.higher(z)));
            if (xs.higher(x) != null) neighbors.add(ChunkPos.asLong(xs.higher(x), z));
            for (long neighbor : neighbors) {
                if (blocked.contains(neighbor)) continue;
                long next = node.distance() + Math.abs((long) ChunkPos.getX(neighbor) - x)
                        + Math.abs((long) ChunkPos.getZ(neighbor) - z);
                if (next >= distance.getOrDefault(neighbor, Long.MAX_VALUE)) continue;
                distance.put(neighbor, next);
                previous.put(neighbor, node.position());
                frontier.add(new Node(neighbor, next));
            }
        }
        return Set.of();
    }

    private static Set<Long> expand(long end, Map<Long, Long> previous) {
        Set<Long> path = new LinkedHashSet<>();
        long current = end;
        path.add(current);
        while (previous.containsKey(current)) {
            long next = previous.get(current);
            int x = ChunkPos.getX(current), z = ChunkPos.getZ(current);
            int targetX = ChunkPos.getX(next), targetZ = ChunkPos.getZ(next);
            while (x != targetX || z != targetZ) {
                x += Integer.compare(targetX, x);
                z += Integer.compare(targetZ, z);
                path.add(ChunkPos.asLong(x, z));
            }
            current = next;
        }
        return path;
    }

    private record Node(long position, long distance) {
    }
}
