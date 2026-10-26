package com.jvn.villagerretaliation.diplomacy.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

final class RouteSearch {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    record Bounds(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    }
    record Result(DiplomaticRoute route, List<Long> missing, String blocker) {
        static Result blocked(String reason) { return new Result(null, List.of(), reason); }
    }
    private record Node(long key, int cost, int estimate) { }
    private record Link(long previous, DiplomaticRoute.Mode mode) { }

    static Result search(BlockPos start, BlockPos end, Map<Long, RouteTerrain> terrain, Bounds bounds,
                         int width, int tileLimit, int nodeLimit, int maximumLength, boolean boat) {
        var corridor = coarse(new ChunkPos(start), new ChunkPos(end), terrain, bounds, tileLimit);
        if (corridor.isEmpty()) return Result.blocked("unreachable");
        var region = new LinkedHashSet<Long>();
        for (long key : corridor) {
            var tile = new ChunkPos(key);
            for (int x = tile.x - width; x <= tile.x + width; x++) {
                for (int z = tile.z - width; z <= tile.z + width; z++) {
                    if (bounds.contains(x, z)) region.add(ChunkPos.asLong(x, z));
                    if (region.size() > tileLimit) return Result.blocked("search_area_limit");
                }
            }
        }
        var missing = region.stream().filter(key -> !terrain.containsKey(key)).toList();
        if (!missing.isEmpty()) return new Result(null, missing, "");
        if (!walk(terrain, start.getX(), start.getZ()) || !walk(terrain, end.getX(), end.getZ())
                || height(terrain, start.getX(), start.getZ()) != start.getY()
                || height(terrain, end.getX(), end.getZ()) != end.getY()) return Result.blocked("unsafe_endpoint");
        var open = new PriorityQueue<Node>(Comparator.comparingInt(Node::estimate).thenComparingInt(Node::cost).thenComparingLong(Node::key));
        var costs = new HashMap<Long, Integer>();
        var links = new HashMap<Long, Link>();
        long first = key(start.getX(), start.getZ()), last = key(end.getX(), end.getZ());
        costs.put(first, 0);
        open.add(new Node(first, 0, distance(first, last)));
        int visited = 0;
        while (!open.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) return Result.blocked("cancelled");
            var node = open.remove();
            if (node.cost != costs.getOrDefault(node.key, Integer.MAX_VALUE)) continue;
            if (++visited > nodeLimit) return Result.blocked("search_work_limit");
            if (node.key == last) {
                var steps = new ArrayList<DiplomaticRoute.Step>();
                for (long at = last; at != first; at = links.get(at).previous) {
                    steps.add(new DiplomaticRoute.Step(position(terrain, at), links.get(at).mode));
                    if (steps.size() > maximumLength) return Result.blocked("detour_limit");
                }
                steps.add(new DiplomaticRoute.Step(start, DiplomaticRoute.Mode.WALK));
                Collections.reverse(steps);
                return new Result(new DiplomaticRoute(steps), List.of(), "");
            }
            int x = ChunkPos.getX(node.key), z = ChunkPos.getZ(node.key);
            boolean water = kind(terrain, x, z) == RouteTerrain.WATER;
            for (int[] direction : DIRECTIONS) {
                for (int length = 1; length <= 2; length++) {
                    int nx = x + direction[0] * length, nz = z + direction[1] * length;
                    if (!region.contains(ChunkPos.asLong(nx >> 4, nz >> 4))) continue;
                    DiplomaticRoute.Mode mode;
                    int difference = Math.abs(height(terrain, x, z) - height(terrain, nx, nz));
                    if (length == 1 && !water && walk(terrain, nx, nz) && difference <= 1) mode = DiplomaticRoute.Mode.WALK;
                    else if (length == 1 && water && navigableWater(terrain, nx, nz) && difference == 0) mode = DiplomaticRoute.Mode.SAIL;
                    else if (length == 2 && boat && difference <= 1 && kind(terrain, x + direction[0], z + direction[1]) == RouteTerrain.WATER
                            && height(terrain, x + direction[0], z + direction[1]) == (water ? height(terrain, x, z) : height(terrain, nx, nz))) {
                        if (!water && navigableWater(terrain, nx, nz)) mode = DiplomaticRoute.Mode.LAUNCH;
                        else if (water && walk(terrain, nx, nz)) mode = DiplomaticRoute.Mode.LAND;
                        else continue;
                    } else continue;
                    long next = key(nx, nz);
                    int cost = node.cost + (length == 2 ? 20 : 1 + difference);
                    if (cost >= costs.getOrDefault(next, Integer.MAX_VALUE)) continue;
                    costs.put(next, cost);
                    links.put(next, new Link(node.key, mode));
                    open.add(new Node(next, cost, cost + distance(next, last)));
                }
            }
        }
        return Result.blocked(boat ? "unreachable" : "no_walking_route");
    }

    private static List<Long> coarse(ChunkPos start, ChunkPos end, Map<Long, RouteTerrain> terrain, Bounds bounds, int limit) {
        var open = new PriorityQueue<Node>(Comparator.comparingInt(Node::estimate).thenComparingLong(Node::key));
        var costs = new HashMap<Long, Integer>();
        var previous = new HashMap<Long, Long>();
        long first = start.toLong(), last = end.toLong();
        open.add(new Node(first, 0, distance(first, last)));
        costs.put(first, 0);
        while (!open.isEmpty() && costs.size() <= limit) {
            if (Thread.currentThread().isInterrupted()) return List.of();
            var node = open.remove();
            if (node.cost != costs.getOrDefault(node.key, Integer.MAX_VALUE)) continue;
            if (node.key == last) {
                var path = new ArrayList<Long>();
                for (long at = last; at != first; at = previous.get(at)) path.add(at);
                path.add(first);
                Collections.reverse(path);
                return path;
            }
            int x = ChunkPos.getX(node.key), z = ChunkPos.getZ(node.key);
            for (int[] direction : DIRECTIONS) {
                int nx = x + direction[0], nz = z + direction[1];
                if (!bounds.contains(nx, nz)) continue;
                long next = ChunkPos.asLong(nx, nz);
                var tile = terrain.get(next);
                if (tile != null && !tile.passable()) continue;
                int cost = node.cost + 1;
                if (cost >= costs.getOrDefault(next, Integer.MAX_VALUE)) continue;
                costs.put(next, cost);
                previous.put(next, node.key);
                open.add(new Node(next, cost, cost + distance(next, last)));
            }
        }
        return List.of();
    }

    static boolean valid(DiplomaticRoute route, Map<Long, RouteTerrain> terrain) {
        for (var step : route.steps()) {
            var p = step.position();
            if (height(terrain, p.getX(), p.getZ()) != p.getY()) return false;
            if (step.mode() == DiplomaticRoute.Mode.SAIL || step.mode() == DiplomaticRoute.Mode.LAUNCH) {
                if (!navigableWater(terrain, p.getX(), p.getZ())) return false;
            } else if (!walk(terrain, p.getX(), p.getZ())) return false;
        }
        return true;
    }
    private static long key(int x, int z) { return ChunkPos.asLong(x, z); }
    private static int distance(long first, long second) {
        return Math.abs(ChunkPos.getX(first) - ChunkPos.getX(second)) + Math.abs(ChunkPos.getZ(first) - ChunkPos.getZ(second));
    }
    private static RouteTerrain tile(Map<Long, RouteTerrain> terrain, int x, int z) { return terrain.get(ChunkPos.asLong(x >> 4, z >> 4)); }
    private static int kind(Map<Long, RouteTerrain> terrain, int x, int z) {
        var tile = tile(terrain, x, z);
        return tile == null ? RouteTerrain.BLOCKED : tile.kind(x, z);
    }
    private static int height(Map<Long, RouteTerrain> terrain, int x, int z) {
        var tile = tile(terrain, x, z);
        return tile == null ? Integer.MIN_VALUE / 2 : tile.height(x, z);
    }
    private static BlockPos position(Map<Long, RouteTerrain> terrain, long key) {
        int x = ChunkPos.getX(key), z = ChunkPos.getZ(key);
        return new BlockPos(x, height(terrain, x, z), z);
    }
    private static boolean walk(Map<Long, RouteTerrain> terrain, int x, int z) { return kind(terrain, x, z) == RouteTerrain.WALK; }
    private static boolean navigableWater(Map<Long, RouteTerrain> terrain, int x, int z) {
        int y = height(terrain, x, z);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (kind(terrain, x + dx, z + dz) != RouteTerrain.WATER || height(terrain, x + dx, z + dz) != y) return false;
        }
        return true;
    }
}
