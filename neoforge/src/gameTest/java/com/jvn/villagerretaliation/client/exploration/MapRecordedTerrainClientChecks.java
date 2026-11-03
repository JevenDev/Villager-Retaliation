package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.exploration.MapRecordedTerrainFixture;
import com.jvn.villagerretaliation.exploration.MapRepository;
import com.jvn.villagerretaliation.exploration.MapStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

final class MapRecordedTerrainClientChecks {
    private static CompletableFuture<MapRepository> loading;
    private static MapRepository original;
    private static Object observation;
    private static Properties metadata;
    private static Path output;
    private static int stage;
    private static long started, uploads;
    private static boolean parchment;

    private MapRecordedTerrainClientChecks() { }

    static boolean tick(ExploredMapScreen screen) {
        String source = System.getProperty("villagerretaliation.mapRecordedTerrainSource", "");
        if (source.isEmpty() || stage == 5) return true;
        if (loading == null) {
            output = Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"));
            loading = CompletableFuture.supplyAsync(() -> {
                try {
                    MapRecordedTerrainFixture.main(new String[]{source, output.toString()});
                    metadata = new Properties();
                    try (var input = Files.newInputStream(output.resolve("terrain-fixture.properties"))) { metadata.load(input); }
                    return new MapRepository(new MapStore(output.resolve("recorded-terrain"), "benchmark", "recorded", "player", "dimension"));
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            });
            return false;
        }
        if (!loading.isDone()) return false;
        if (stage == 0) {
            original = ExploredMapClient.repository();
            observation = field("observation");
            parchment = VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.get();
            set("repository", loading.join());
            ExploredMapClient.tiles().close();
            set("observation", null);
            ExploredMapClientSmoke.navigation(screen).focus(Double.parseDouble(metadata.getProperty("cameraX")),
                    Double.parseDouble(metadata.getProperty("cameraZ")));
            ExploredMapClientSmoke.zoom(screen, 0.8);
            VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(true);
            uploads = ExploredMapClient.tiles().uploads();
            stage = 1;
            started = System.nanoTime();
        }
        set("observation", null);
        loading.join().tick();
        ExploredMapClientSmoke.navigation(screen).focus(Double.parseDouble(metadata.getProperty("cameraX")),
                Double.parseDouble(metadata.getProperty("cameraZ")));
        ExploredMapClientSmoke.zoom(screen, stage == 1 ? 0.8 : stage == 4 ? 0.4 : 2.5);
        if (System.nanoTime() - started < 10_000_000_000L) return false;
        if (ExploredMapClient.tiles().uploads() - uploads < 100)
            throw new AssertionError("Actual recorded terrain must reach the map atlas before visual checks");
        String name = switch (stage) {
            case 1 -> "map-recorded-terrain";
            case 2 -> "map-recorded-terrain-detail";
            case 3 -> "map-recorded-vanilla-clouds";
            case 4 -> "map-recorded-terrain-wide";
            default -> throw new AssertionError("Unexpected terrain visual stage " + stage);
        };
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(output.resolve(name + ".png"));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
        stage++;
        started = System.nanoTime();
        if (stage == 2) ExploredMapClientSmoke.zoom(screen, 2.5);
        if (stage == 3) VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(false);
        if (stage == 4) {
            VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(true);
            ExploredMapClientSmoke.zoom(screen, 0.4);
        }
        if (stage < 5) return false;
        set("repository", original);
        set("observation", observation);
        loading.join().close();
        ExploredMapClient.tiles().close();
        VillagerRetaliationConfig.PARCHMENT_MAP_COLORS.set(parchment);
        return true;
    }

    private static Object field(String name) {
        try { var field = ExploredMapClient.class.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void set(String name, Object value) {
        try { var field = ExploredMapClient.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
