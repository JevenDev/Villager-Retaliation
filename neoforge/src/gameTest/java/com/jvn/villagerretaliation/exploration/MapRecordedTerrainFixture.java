package com.jvn.villagerretaliation.exploration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;

public final class MapRecordedTerrainFixture {
    public static void main(String[] args) throws IOException {
        var output = Path.of(args[1]); Files.createDirectories(output);
        var report = new ArrayList<String>();
        var source = Path.of(args[0]);
        var root = output.resolve("recorded-terrain");
        int tiles = 0, columns = 0, blocks = 0, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        var states = new HashSet<String>();
        var regions = new HashMap<Long, int[]>();
        try (var store = new MapStore(root, "benchmark", "recorded", "player", "dimension");
                var files = Files.newDirectoryStream(source, "*.vrregion")) {
            var destination = store.root(); Files.createDirectories(destination);
            for (var file : files) {
                Files.copy(file, destination.resolve(file.getFileName()));
                var parts = file.getFileName().toString().split("\\.");
                int rx = Integer.parseInt(parts[1]), rz = Integer.parseInt(parts[2]);
                for (int z = rz * 16; z < rz * 16 + 16; z++) for (int x = rx * 16; x < rx * 16 + 16; x++) {
                    var tile = store.read(x, z);
                    if (tile.columns().isEmpty()) continue;
                    tiles++; columns += tile.columns().size();
                    for (var column : tile.columns()) {
                        min = Math.min(min, column.top()); max = Math.max(max, column.top());
                        var stats = regions.computeIfAbsent(MapSnapshot.key(rx, rz), key -> new int[]{0, 0, Integer.MAX_VALUE, Integer.MIN_VALUE});
                        stats[0]++; stats[1] += column.top(); stats[2] = Math.min(stats[2], column.top()); stats[3] = Math.max(stats[3], column.top());
                        blocks += column.blocks().size();
                        for (var block : column.blocks()) states.add(block.state());
                    }
                }
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
        if (columns < 500_000 || max - min < 100 || states.size() < 50)
            throw new AssertionError("Terrain benchmark requires at least 500000 recorded columns, 100 blocks of relief, and 50 states");
        var rugged = regions.entrySet().stream().filter(entry -> entry.getValue()[0] > 12_000)
                .max(Comparator.comparingInt(entry -> entry.getValue()[3] - entry.getValue()[2])).orElseThrow();
        double cameraX = (int)(rugged.getKey() >> 32) * 128 + 64;
        double cameraZ = (int)(long)rugged.getKey() * 128 + 64;
        double cameraY = (double)rugged.getValue()[1] / rugged.getValue()[0] + 1;
        report.add(String.format(Locale.ROOT, "Actual recorded terrain: %d tiles, %d columns, %d blocks, %d states, heights %d..%d", tiles, columns, blocks, states.size(), min, max));
        report.add(String.format(Locale.ROOT, "Rugged terrain focus: %.0f, %.1f, %.0f, local relief %d blocks", cameraX, cameraY, cameraZ, rugged.getValue()[3] - rugged.getValue()[2]));
        System.out.println(String.join("\n", report));
        var metadata = new Properties();
        metadata.setProperty("summary", String.join("\n", report));
        metadata.setProperty("cameraX", Double.toString(cameraX)); metadata.setProperty("cameraY", Double.toString(cameraY)); metadata.setProperty("cameraZ", Double.toString(cameraZ));
        try (var out = Files.newOutputStream(output.resolve("terrain-fixture.properties"))) { metadata.store(out, null); }
    }

}
