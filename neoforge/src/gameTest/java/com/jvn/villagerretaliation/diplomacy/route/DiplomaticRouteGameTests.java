package com.jvn.villagerretaliation.diplomacy.route;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("diplomatic_routes")
@PrefixGameTestTemplate(false)
public final class DiplomaticRouteGameTests {
    @GameTest(template = "empty")
    public static void landObstaclesSlopesAndReverse(GameTestHelper helper) {
        var terrain = fixture(0, 2, (x, z) -> x == 20 && z != 10 ? RouteTerrain.BLOCKED : RouteTerrain.WALK, (x, z) -> Math.min(x, 12));
        var start = new BlockPos(4, 4, 8);
        var end = new BlockPos(40, 12, 8);
        var result = search(start, end, terrain, false);
        helper.assertTrue(result.route() != null, "walking route climbs a one-block slope and detours around obstruction");
        helper.assertFalse(result.route().needsBoat(), "land route does not reserve a boat");
        helper.assertTrue(result.route().steps().stream().anyMatch(step -> step.position().getZ() == 10), "route uses the verified opening");
        helper.assertTrue(RouteSearch.valid(result.route().reversed(), terrain), "reverse route uses the same observations");
        helper.assertValueEqual(DiplomaticRoute.load(result.route().save()), result.route(), "route persists exactly");
        var cliff = fixture(0, 2, (x, z) -> RouteTerrain.WALK, (x, z) -> x < 20 ? 0 : 3);
        helper.assertTrue(search(new BlockPos(4, 0, 8), new BlockPos(40, 3, 8), cliff, false).route() == null, "cliff is not a walking step");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void riverRequiresBoatAndSafeShores(GameTestHelper helper) {
        var terrain = fixture(0, 2, (x, z) -> x >= 16 && x <= 31 ? RouteTerrain.WATER : RouteTerrain.WALK, (x, z) -> 10);
        var start = new BlockPos(8, 10, 24);
        var end = new BlockPos(40, 10, 24);
        helper.assertTrue(search(start, end, terrain, false).route() == null, "river cannot be crossed without a boat");
        var result = search(start, end, terrain, true);
        helper.assertTrue(result.route() != null && result.route().needsBoat(), "boat route exists between clear shores");
        helper.assertTrue(result.route().steps().stream().anyMatch(step -> step.mode() == DiplomaticRoute.Mode.SAIL), "route includes sailing");
        helper.assertValueEqual(result.route().steps().stream().filter(step -> step.mode() == DiplomaticRoute.Mode.LAUNCH).count(), 1L, "one real boat launch");
        helper.assertValueEqual(result.route().steps().stream().filter(step -> step.mode() == DiplomaticRoute.Mode.LAND).count(), 1L, "one recovery at the shore");
        helper.assertTrue(RouteSearch.valid(result.route().reversed(), terrain), "collection can reuse reverse water route");
        var blocked = fixture(0, 2, (x, z) -> x >= 16 && x <= 31 ? RouteTerrain.WATER : RouteTerrain.WALK, (x, z) -> x >= 16 && x <= 31 ? 10 : 13);
        helper.assertTrue(search(new BlockPos(8, 13, 24), new BlockPos(40, 13, 24), blocked, true).route() == null, "high banks prevent launch and landing");
        var narrow = fixture(0, 2, (x, z) -> x >= 16 && x <= 17 ? RouteTerrain.WATER : RouteTerrain.WALK, (x, z) -> 10);
        helper.assertTrue(search(start, end, narrow, true).route() == null, "boat clearance is checked across its width");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingTerrainAndSearchLimits(GameTestHelper helper) {
        var start = new BlockPos(8, 0, 8);
        var end = new BlockPos(40, 0, 8);
        var unknown = search(start, end, Map.of(), true);
        helper.assertTrue(unknown.route() == null && !unknown.missing().isEmpty(), "unknown corridor requires observations");
        var terrain = fixture(0, 2, (x, z) -> RouteTerrain.WALK, (x, z) -> 0);
        var limited = RouteSearch.search(start, end, terrain, new RouteSearch.Bounds(0, 0, 2, 2), 1, 9, 4, 100, false);
        helper.assertValueEqual(limited.blocker(), "search_work_limit", "search cannot exceed node work budget");
        var shortRoute = RouteSearch.search(start, end, terrain, new RouteSearch.Bounds(0, 0, 2, 2), 1, 9, 10000, 10, false);
        helper.assertValueEqual(shortRoute.blocker(), "detour_limit", "route length remains bounded");
        var endpoint = search(new BlockPos(8, 2, 8), end, terrain, false);
        helper.assertValueEqual(endpoint.blocker(), "unsafe_endpoint", "search does not teleport to a different surface");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void depotAccessKeepsResidentOriginAndSurvivesMigration(GameTestHelper helper) {
        var terrain = fixture(0, 2, (x, z) -> x == 40 && z == 8 ? RouteTerrain.BLOCKED : RouteTerrain.WALK, (x, z) -> 0);
        var start = new BlockPos(8, 0, 8);
        var depot = new BlockPos(40, 0, 8);
        var bounds = new RouteSearch.Bounds(0, 0, 2, 2);
        var result = RouteSearch.search(start, depot, terrain, bounds, 1, 9, 10000, 100, false, 0, 2);
        helper.assertTrue(result.route() != null, "depot route reaches adjacent walkable ground");
        helper.assertValueEqual(result.route().steps().getFirst().position(), start, "resident origin stays exact");
        helper.assertTrue(RoutePreparation.accepts(depot, result.route().steps().getLast().position(), 2), "access remains near the depot");
        helper.assertTrue(RouteSearch.search(start.above(), depot, terrain, bounds, 1, 9, 10000, 100, false, 0, 2).route() == null,
                "depot tolerance cannot relocate the resident");
        var data = new RouteSavedData();
        var job = new RoutePreparation(UUID.randomUUID(), helper.getLevel().dimension().location(), start, depot, false, 0, 2);
        job.route = result.route(); job.state = RoutePreparation.State.READY; data.jobs.put(job.id(), job);
        var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(RouteSavedData.load(saved, helper.getLevel().registryAccess()).save(new CompoundTag(), helper.getLevel().registryAccess()), saved,
                "access endpoints persist without moving the requested depot");
        var legacy = new RoutePreparation(UUID.randomUUID(), helper.getLevel().dimension().location(), start, start.east(), false);
        data.jobs.clear(); data.jobs.put(legacy.id(), legacy);
        saved = data.save(new CompoundTag(), helper.getLevel().registryAccess()); saved.putInt("Version", 1);
        var entry = saved.getList("Preparations", 10).getCompound(0); entry.remove("StartTolerance"); entry.remove("EndTolerance");
        var migrated = RouteSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertFalse(migrated.readOnly(), "old exact-endpoint routes migrate");
        helper.assertValueEqual(migrated.get(legacy.id()).endTolerance(), 0, "migration keeps exact endpoints");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void immutableTerrainAndPreservedMalformedSaves(GameTestHelper helper) {
        var heights = new int[256]; var kinds = new byte[256]; Arrays.fill(kinds, RouteTerrain.WALK);
        var tile = new RouteTerrain(new ChunkPos(0, 0), heights, kinds, 5);
        heights[0] = 100; kinds[0] = RouteTerrain.BLOCKED;
        helper.assertValueEqual(tile.height(0, 0), 0, "terrain snapshot owns its heights");
        helper.assertValueEqual(tile.kind(0, 0), RouteTerrain.WALK, "terrain snapshot owns its passability");
        var data = new RouteSavedData();
        data.observe(helper.getLevel().dimension().location(), tile);
        var job = new RoutePreparation(UUID.randomUUID(), helper.getLevel().dimension().location(), new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), false);
        job.dependencies.add(123L); job.width = 2; job.attempts = 1; data.jobs.put(job.id(), job);
        var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var loaded = RouteSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.save(new CompoundTag(), helper.getLevel().registryAccess()), saved, "preparation and shared observations survive restart");
        saved.putInt("Version", 99);
        var future = RouteSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(future.readOnly(), "future version blocks preparation");
        helper.assertValueEqual(future.save(new CompoundTag(), helper.getLevel().registryAccess()), saved, "future data stays unchanged");
        saved.putInt("Version", 1);
        saved.getList("Preparations", 10).getCompound(0).putString("Dimension", "Invalid Dimension");
        var malformed = RouteSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(malformed.readOnly(), "malformed dimension is preserved read-only");
        helper.assertValueEqual(malformed.save(new CompoundTag(), helper.getLevel().registryAccess()), saved, "malformed data is not reset");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void surfaceObservationsRejectHazardsAndClearance(GameTestHelper helper) {
        var source = new BlockGetter() {
            @Override public BlockState getBlockState(BlockPos p) {
                if (p.getY() == 0) return switch (p.getX()) {
                    case 1 -> Blocks.MAGMA_BLOCK.defaultBlockState();
                    case 2 -> Blocks.WATER.defaultBlockState();
                    case 3 -> Blocks.LAVA.defaultBlockState();
                    case 4 -> Blocks.OAK_LEAVES.defaultBlockState();
                    default -> Blocks.STONE.defaultBlockState();
                };
                if (p.getX() == 5 && p.getY() == 1) return Blocks.SWEET_BERRY_BUSH.defaultBlockState();
                return Blocks.AIR.defaultBlockState();
            }
            @Override public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
            @Override public BlockEntity getBlockEntity(BlockPos p) { return null; }
            @Override public int getHeight() { return 16; }
            @Override public int getMinBuildHeight() { return 0; }
        };
        var reader = new RouteSurfaceReader(new ChunkPos(0, 0), source, 0);
        while (!reader.step()) { }
        var tile = reader.result();
        helper.assertValueEqual(tile.kind(0, 0), RouteTerrain.WALK, "stable land is walkable");
        helper.assertValueEqual(tile.kind(1, 0), RouteTerrain.BLOCKED, "magma rejected");
        helper.assertValueEqual(tile.kind(2, 0), RouteTerrain.WATER, "clear source water can be surveyed for sailing");
        helper.assertValueEqual(tile.kind(3, 0), RouteTerrain.BLOCKED, "lava rejected");
        helper.assertValueEqual(tile.kind(4, 0), RouteTerrain.BLOCKED, "tree canopy is not treated as ground");
        helper.assertValueEqual(tile.kind(5, 0), RouteTerrain.BLOCKED, "harmful vegetation rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void sharedCachedPreparationCancellationAndChanges(GameTestHelper helper) {
        var level = helper.getLevel();
        var data = RouteSavedData.get(level);
        var now = level.getServer().overworld().getGameTime();
        var terrain = fixture(9, 13, (x, z) -> RouteTerrain.WALK, (x, z) -> 64);
        terrain.values().forEach(tile -> data.observe(level.dimension().location(), new RouteTerrain(tile.chunk(), heights(64), kinds(RouteTerrain.WALK), now)));
        var first = DiplomaticRouteService.prepare(level, UUID.randomUUID(), new BlockPos(168, 64, 168), new BlockPos(200, 64, 168), false);
        var second = DiplomaticRouteService.prepare(level, UUID.randomUUID(), new BlockPos(200, 64, 168), new BlockPos(168, 64, 168), false);
        cleanup(helper, () -> { DiplomaticRouteService.forget(level, first.id()); DiplomaticRouteService.forget(level, second.id()); });
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(first.state() == RoutePreparation.State.READY && second.state() == RoutePreparation.State.READY,
                    "overlapping cached routes finish on the worker");
        }).thenExecute(() -> {
            helper.assertValueEqual(DiplomaticRouteService.metrics(level.getServer()).generationSubmissions(), 0L, "cached route never generates terrain");
            helper.assertValueEqual(DiplomaticRouteService.metrics(level.getServer()).tickets(), 0, "cached route never retains tickets");
            var reverse = DiplomaticRouteService.prepare(level, UUID.randomUUID(), second.start(), second.end(), false);
            helper.assertValueEqual(reverse.state(), RoutePreparation.State.READY, "validated reverse route reused immediately");
            DiplomaticRouteService.forget(level, reverse.id());
            DiplomaticRouteService.terrainChanged(level, new BlockPos(180, 64, 168));
            helper.assertTrue(DiplomaticRouteService.ready(level, first.id()) == null, "changed terrain invalidates cached route");
            helper.assertValueEqual(first.state(), RoutePreparation.State.PREPARING, "changed route returns to preparation");
            DiplomaticRouteService.cancel(level, first.id());
            helper.assertValueEqual(first.state(), RoutePreparation.State.CANCELLED, "preparation cancels explicitly");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 5000, batch = "route_generation")
    public static void terrainGenerationIsNonblockingAndDoesNotAdmitVillages(GameTestHelper helper) {
        var level = helper.getLevel();
        int origin = 10000 + Math.floorMod(UUID.randomUUID().hashCode(), 200000);
        var position = new ChunkPos(origin, origin);
        int villages = VillageAllegianceRegistrySavedData.get(level).records().size();
        var generation = new RouteGeneration(level, position, level.getServer().overworld().getGameTime());
        RoutePreparation[] prepared = {null};
        cleanup(helper, () -> { generation.release(); if (prepared[0] != null) DiplomaticRouteService.forget(level, prepared[0].id()); });
        long[] lastSubmission = {-1}, submissions = {DiplomaticRouteService.metrics(level.getServer()).generationSubmissions()};
        helper.onEachTick(() -> {
            var metrics = DiplomaticRouteService.metrics(level.getServer());
            helper.assertTrue(metrics.tickets() <= 1, "only one route generation ticket is held globally");
            if (metrics.generationSubmissions() != submissions[0]) {
                long now = level.getServer().overworld().getGameTime();
                helper.assertTrue(lastSubmission[0] < 0 || now - lastSubmission[0] >= 100, "new generation requests are spaced by at least 100 ticks");
                lastSubmission[0] = now; submissions[0] = metrics.generationSubmissions();
            }
        });
        helper.assertTrue(RouteGeneration.DISTANCE < 0, "ticket cannot promote a full chunk");
        helper.assertTrue(RouteGeneration.footprint(position).size() > 256, "generation budget includes the dependency square");
        helper.startSequence().thenWaitUntil(() -> {
            var chunk = generation.poll(level.getServer().overworld().getGameTime());
            helper.assertTrue(chunk != null, "terrain generation completes through a nonblocking holder future");
            helper.assertTrue(chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES), "features are present for collision survey");
            helper.assertTrue(chunk.getPersistedStatus().isBefore(ChunkStatus.SPAWN), "survey does not spawn residents");
            helper.assertTrue(level.getChunkSource().getChunkNow(position.x, position.z) == null, "survey does not make terrain full");
            helper.assertFalse(level.getChunkSource().isPositionTicking(position.toLong()), "survey does not tick terrain");
            helper.assertValueEqual(VillageAllegianceRegistrySavedData.get(level).records().size(), villages, "survey does not admit villages");
            helper.assertTrue(generation.generatedDependencies() > 256, "generation metrics count dependency chunks that actually advance");
            int x = position.getMinBlockX() + 8, z = position.getMinBlockZ() + 8;
            int y = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8) + 1;
            generation.release();
            helper.assertFalse(generation.ticketHeld(), "completion releases its temporary ticket");
            prepared[0] = DiplomaticRouteService.prepare(level, UUID.randomUUID(), new BlockPos(x, y, z), new BlockPos(x + 2, y, z), false);
        }).thenWaitUntil(() -> {
            helper.assertFalse(prepared[0].state() == RoutePreparation.State.BLOCKED, "live preparation blocker: " + prepared[0].blocker());
            helper.assertValueEqual(prepared[0].state(), RoutePreparation.State.READY, "scheduler surveys and validates generated terrain");
            helper.assertTrue(DiplomaticRouteService.ready(level, prepared[0].id()) != null, "prepared path is usable after survey resources are released");
            helper.assertValueEqual(DiplomaticRouteService.metrics(level.getServer()).tickets(), 0, "completed path has no retained terrain ticket");
            helper.assertValueEqual(VillageAllegianceRegistrySavedData.get(level).records().size(), villages, "complete preparation still does not admit villages");
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void diskTerrainDecodesWithoutLoading(GameTestHelper helper) {
        var palette = new net.minecraft.world.level.chunk.PalettedContainer<BlockState>(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(), net.minecraft.world.level.chunk.PalettedContainer.Strategy.SECTION_STATES);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) palette.set(x, 0, z, Blocks.STONE.defaultBlockState());
        var codec = net.minecraft.world.level.chunk.PalettedContainer.codecRW(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY,
                BlockState.CODEC, net.minecraft.world.level.chunk.PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
        var section = new CompoundTag();
        section.putByte("Y", (byte) 0);
        section.put("block_states", codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, palette).getOrThrow());
        var sections = new net.minecraft.nbt.ListTag(); sections.add(section);
        var saved = new CompoundTag();
        saved.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        saved.putInt("xPos", 20000); saved.putInt("zPos", 20000); saved.put("sections", sections);
        var position = new ChunkPos(20000, 20000);
        var view = RouteSurfaceReader.decode(saved, position, 0, 16);
        var reader = new RouteSurfaceReader(position, view, 10);
        while (!reader.step()) { }
        helper.assertValueEqual(reader.result().kind(position.getMinBlockX() + 8, position.getMinBlockZ() + 8), RouteTerrain.WALK, "saved block palettes yield real collision observations");
        helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(position.x, position.z) == null, "disk parsing does not load a chunk");
        saved.putInt("DataVersion", Integer.MAX_VALUE);
        boolean rejected = false;
        try { RouteSurfaceReader.decode(saved, position, 0, 16); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "future terrain format is not interpreted as empty air");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void preparationBoundsAndRetryLimits(GameTestHelper helper) {
        var level = helper.getLevel();
        var tooFar = DiplomaticRouteService.prepare(level, UUID.randomUUID(), BlockPos.ZERO, new BlockPos(10000, 0, 0), false);
        var border = DiplomaticRouteService.prepare(level, UUID.randomUUID(), new BlockPos(30000000, 0, 0), new BlockPos(30000001, 0, 0), false);
        try {
            helper.assertValueEqual(tooFar.blocker(), "shipment_range", "distance is checked before survey");
            helper.assertValueEqual(border.blocker(), "world_border", "world border checked before survey");
            helper.assertTrue(DiplomaticRouteService.retry(level, tooFar.id()), "blocked preparation can be retried explicitly");
            for (int index = 0; index < 3; index++) { tooFar.block("shipment_range"); DiplomaticRouteService.retry(level, tooFar.id()); }
            tooFar.block("shipment_range");
            helper.assertFalse(DiplomaticRouteService.retry(level, tooFar.id()), "retries stop at configured cap");
            helper.assertTrue(tooFar.generationFootprint() == 0 && border.generationFootprint() == 0, "invalid endpoints spend no generation budget");
        } finally { DiplomaticRouteService.forget(level, tooFar.id()); DiplomaticRouteService.forget(level, border.id()); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "route_cancel")
    public static void cancellationAndTimeoutReleaseTerrainTickets(GameTestHelper helper) {
        var level = helper.getLevel();
        long now = level.getServer().overworld().getGameTime();
        var position = new ChunkPos(5000, 5000);
        var generation = new RouteGeneration(level, position, now);
        cleanup(helper, generation::release);
        boolean timedOut = false;
        try { generation.poll(now + 1201); }
        catch (IllegalStateException expected) { timedOut = expected.getMessage().equals("route_generation_timeout"); }
        finally { generation.release(); }
        helper.assertTrue(timedOut, "preparation timeout is explicit");
        helper.assertFalse(generation.ticketHeld(), "timeout releases its temporary resource");
        var cancelled = new RouteGeneration(level, new ChunkPos(5001, 5000), now);
        cleanup(helper, cancelled::release);
        cancelled.release(); cancelled.release();
        helper.assertFalse(cancelled.ticketHeld(), "cancellation and repeated cleanup are idempotent");
        helper.startSequence().thenIdle(10).thenExecute(() -> {
            helper.assertTrue(level.getChunkSource().chunkMap.getVisibleChunkIfPresent(position.toLong()) == null, "cancelled ticket does not retain the holder");
            helper.assertFalse(level.getChunkSource().isPositionTicking(position.toLong()), "cancelled survey never creates a ticking corridor");
        }).thenSucceed();
    }

    private interface Column { int at(int x, int z); }
    private static Map<Long, RouteTerrain> fixture(int minimum, int maximum, Column kind, Column elevation) {
        var result = new HashMap<Long, RouteTerrain>();
        for (int x = minimum; x <= maximum; x++) for (int z = minimum; z <= maximum; z++) {
            var heights = new int[256]; var kinds = new byte[256];
            for (int index = 0; index < 256; index++) {
                int px = x * 16 + (index & 15), pz = z * 16 + (index >> 4);
                heights[index] = elevation.at(px, pz); kinds[index] = (byte) kind.at(px, pz);
            }
            var tile = new RouteTerrain(new ChunkPos(x, z), heights, kinds, 0); result.put(tile.chunk().toLong(), tile);
        }
        return result;
    }
    private static int[] heights(int value) { var values = new int[256]; Arrays.fill(values, value); return values; }
    private static byte[] kinds(byte value) { var values = new byte[256]; Arrays.fill(values, value); return values; }
    private static RouteSearch.Result search(BlockPos start, BlockPos end, Map<Long, RouteTerrain> terrain, boolean boat) {
        return RouteSearch.search(start, end, terrain, new RouteSearch.Bounds(0, 0, 2, 2), 1, 9, 10000, 200, boat);
    }
    private static void cleanup(GameTestHelper helper, Runnable cleanup) {
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) { }
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup.run(); }
        });
    }
}
