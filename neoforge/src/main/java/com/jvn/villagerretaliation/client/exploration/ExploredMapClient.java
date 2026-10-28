package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.*;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "villagerretaliation", value = Dist.CLIENT)
public final class ExploredMapClient {
    public static final KeyMapping OPEN = new KeyMapping("key.villagerretaliation.explored_map", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.villagerretaliation");
    private static MapRepository repository, retiring;
    private static final VillageRelationsMap RELATIONS = new VillageRelationsMap();
    static VillageRelationsMap relations() { return RELATIONS; }
    public static void acceptRelations(com.jvn.villagerretaliation.network.VillageRelationsPayload payload) { RELATIONS.accept(payload); }
    public static void acceptCouriers(com.jvn.villagerretaliation.network.SettlementCourierMapPayload payload) { RELATIONS.acceptCouriers(payload); }
    private static final MapTileCache TILES = new MapTileCache();
    private static MapObservationPayload observation;
    private static String scope;
    private static Object level;
    private static final MapCaptureQueue CAPTURE = new MapCaptureQueue();
    private static final Map<net.minecraft.world.level.block.state.BlockState, String> STATE_NAMES = new IdentityHashMap<>();
    private static long lastConflict;
    private static final List<int[]> OFFSETS = offsets();
    private static List<int[]> captureChunks = List.of();
    private static int captureDistance, captureCursor;
    @EventBusSubscriber(modid = "villagerretaliation", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    }
    public static MapRepository repository() { return repository; }
    static MapTileCache tiles() { return TILES; }
    public static void accept(MapObservationPayload payload) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.level.dimension().location().equals(payload.dimension())) observation = payload;
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (Minecraft.getInstance().screen instanceof ExploredMapScreen map) event.setPitch(map.transitionPitch(event.getPitch()));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); observation = null; }
    private static void reset() {
        if (repository != null) { repository.close(); retiring = repository; repository = null; }
        scope = null; level = null; CAPTURE.clear(); TILES.close(); ExploredMapMinimap.reset(); ExploredMapScreen.resetTiles();
        captureChunks = List.of(); captureDistance = 0; captureCursor = 0; RELATIONS.clear();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (level != null && level != mc.level) { reset(); observation = null; }
        if (mc.screen == null) {
            if (com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SHOW_EXPLORED_MAP_MINIMAP.get()) RELATIONS.tick(new MapLayers(true));
        }
        while (OPEN.consumeClick()) if (mc.screen == null) {
            var conflict = Arrays.stream(mc.options.keyMappings).filter(k -> k != OPEN && !k.isUnbound() && k.same(OPEN)).findFirst();
            if (conflict.isPresent()) {
                if (System.currentTimeMillis() - lastConflict > 2000) {
                    mc.player.displayClientMessage(Component.translatable("map.villagerretaliation.key_conflict", OPEN.getTranslatedKeyMessage(), Component.translatable(conflict.get().getName())), false);
                    lastConflict = System.currentTimeMillis();
                }
            } else mc.setScreen(new ExploredMapScreen());
        }
        if (observation == null || !observation.dimension().equals(mc.level.dimension().location())) return;
        if (retiring != null) {
            try { if (!retiring.awaitClosed(0)) return; } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            retiring = null;
        }
        String nextScope = observation.world() + "/" + observation.dimension();
        if (!nextScope.equals(scope)) {
            if (repository != null) { reset(); return; }
            String server = mc.getCurrentServer() == null ? "local:" + (mc.getSingleplayerServer() == null ? "unknown" :
                    mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize()) : mc.getCurrentServer().ip;
            repository = new MapRepository(new MapStore(mc.gameDirectory.toPath().resolve("vr-explored-map"), server,
                    observation.world().toString(), mc.player.getUUID().toString(), observation.dimension().toString()));
            scope = nextScope; level = mc.level;
        }
        repository.tick();
        if (mc.isPaused() || !mc.player.isAlive()) return;
        var playerChunk = mc.level.getChunkSource().getChunk(mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4, false);
        if (playerChunk == null || !mc.player.isSpectator() && playerChunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                mc.player.getBlockX() & 15, mc.player.getBlockZ() & 15) > mc.player.getY() + 2) return;
        if (mc.player.tickCount % 40 == 0) CAPTURE.refresh();
        for (var offset : OFFSETS) {
            int x = mc.player.getBlockX() + offset[0], z = mc.player.getBlockZ() + offset[1];
            if (!MapSnapshot.inObservation(x, z, mc.player.getX(), mc.player.getZ())) continue;
            var chunk = mc.level.getChunkSource().getChunk(x >> 4, z >> 4, false);
            if (chunk != null && (mc.player.isSpectator() || Math.abs(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) - mc.player.getY()) <= 48))
                CAPTURE.offer(x, z);
        }
        scheduleRenderDistance();
        captureQueued();
        var marker = observation;
        if (!marker.village().isEmpty() && marker.position().distSqr(mc.player.blockPosition()) <= 24 * 24) {
            int x = marker.position().getX(), z = marker.position().getZ();
            var tile = repository.get(MapSnapshot.tile(x), MapSnapshot.tile(z));
            if (tile != null && tile.observed(x, z)) {
                // keep the observed anchor even after its terrain leaves the detailed cache
                boolean exists = repository.hasMarker(marker.village());
                if (!exists && tile.markers().size() < 16) {
                    var markers = new ArrayList<>(tile.markers());
                    markers.add(new MapSnapshot.Marker(marker.village(), marker.name(), x, marker.position().getY(), z));
                    repository.put(new MapSnapshot(tile.x(), tile.z(), tile.revision() + 1, tile.columns(), markers));
                }
            }
        }
    }
    private static void scheduleRenderDistance() {
        var mc = Minecraft.getInstance();
        int distance = Math.clamp(mc.options.getEffectiveRenderDistance(), 2, 32);
        if (distance != captureDistance) {
            captureChunks = MapCaptureQueue.chunkOffsets(distance); captureDistance = distance; captureCursor = 0;
            for (long key : CAPTURE.tiles()) if (Math.abs(((int)(key >> 32) >> 1) - (mc.player.getBlockX() >> 4)) > distance
                    || Math.abs(((int)key >> 1) - (mc.player.getBlockZ() >> 4)) > distance) CAPTURE.discard(key);
        }
        // reserve half the backlog for nearby movement while sweeping the rest of the loaded view
        for (int budget = 0; budget < 64 && CAPTURE.size() < MapCaptureQueue.MAX_TILES / 2; budget++) {
            var offset = captureChunks.get(captureCursor); captureCursor = (captureCursor + 1) % captureChunks.size();
            int x = (mc.player.getBlockX() >> 4) + offset[0], z = (mc.player.getBlockZ() >> 4) + offset[1];
            if (mc.level.getChunkSource().getChunk(x, z, false) == null) continue;
            for (int dz = 0; dz < 2; dz++) for (int dx = 0; dx < 2; dx++) CAPTURE.offerTile(x * 2 + dx, z * 2 + dz);
        }
    }
    private static void captureQueued() {
        long deadline = System.nanoTime() + 3_000_000;
        int budget = 512;
        for (long key : CAPTURE.tiles()) {
            if (budget <= 0 || System.nanoTime() >= deadline) break;
            int tx = (int)(key >> 32), tz = (int)key;
            var world = Minecraft.getInstance().level;
            if (world.getChunkSource().getChunk(tx >> 1, tz >> 1, false) == null) { CAPTURE.discard(key); continue; }
            var tile = repository.get(tx, tz);
            if (tile == null || repository.dirtyCount() >= MapRepository.MAX_DIRTY) continue;
            var columns = new ArrayList<>(tile.columns());
            long remaining = CAPTURE.mask(key), completed = 0;
            boolean changed = false;
            while (remaining != 0 && budget-- > 0 && System.nanoTime() < deadline) {
                int index = Long.numberOfTrailingZeros(remaining);
                var column = capture(tx * 8 + index % 8, tz * 8 + index / 8);
                var old = columns.stream().filter(c -> c.index() == index).findFirst().orElse(null);
                if (!column.equals(old)) { columns.removeIf(c -> c.index() == index); columns.add(column); changed = true; }
                completed |= 1L << index; remaining &= ~(1L << index);
            }
            // publish once per tile and retain unfinished world coordinates across movement and I/O delays
            if (!changed || repository.put(new MapSnapshot(tx, tz, tile.revision() + 1, columns, tile.markers()))) CAPTURE.complete(key, completed);
        }
    }
    private static MapSnapshot.Column capture(int x, int z) {
        var mc = Minecraft.getInstance(); var world = mc.level;
        var chunk = world.getChunkSource().getChunk(x >> 4, z >> 4, false);
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        var pos = new BlockPos.MutableBlockPos(x, top, z);
        while (pos.getY() > world.getMinBuildHeight() && chunk.getBlockState(pos).getMapColor(world, pos) == net.minecraft.world.level.material.MapColor.NONE) pos.move(0, -1, 0);
        var state = chunk.getBlockState(pos);
        if (state.isAir()) return new MapSnapshot.Column(Math.floorMod(x, 8) + 8 * Math.floorMod(z, 8), top, List.of(), true);
        int depth = 1;
        if (!state.getFluidState().isEmpty()) {
            var below = new BlockPos.MutableBlockPos(x, pos.getY() - 1, z);
            // deeper water has the same darkest map shade, so capture stays bounded
            while (depth < 10 && below.getY() > world.getMinBuildHeight() && !chunk.getBlockState(below).getFluidState().isEmpty()) {
                depth++; below.move(0, -1, 0);
            }
            if (!state.isFaceSturdy(world, pos, net.minecraft.core.Direction.UP)) state = state.getFluidState().createLegacyBlock();
        }
        int tint;
        try { tint = mc.getBlockColors().getColor(state, world, pos, 0); }
        catch (RuntimeException e) {
            System.getLogger("VR map").log(System.Logger.Level.DEBUG, "Map tint unavailable: " + state, e);
            tint = 0xffffff;
        }
        if (STATE_NAMES.size() >= 4096) STATE_NAMES.clear();
        int light = Math.max(world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos),
                world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos.above()));
        var block = new MapSnapshot.Block(pos.getY(), STATE_NAMES.computeIfAbsent(state, BlockStateParser::serialize),
                tint == -1 ? 0xffffff : tint, BiomeColors.getAverageWaterColor(world, pos), 2, light);
        return new MapSnapshot.Column(Math.floorMod(x, 8) + 8 * Math.floorMod(z, 8), pos.getY(), depth == 1 ? List.of(block) : List.of(block,
                new MapSnapshot.Block(pos.getY() - depth + 1, block.state(), block.tint(), block.waterTint(), 0, light)), true);
    }
    private static List<int[]> offsets() {
        List<int[]> list = new ArrayList<>();
        for (int z = -24; z <= 24; z++) for (int x = -24; x <= 24; x++) if (x*x + z*z <= 24*24) list.add(new int[]{x,z});
        list.sort(Comparator.comparingInt(a -> a[0]*a[0] + a[1]*a[1])); return List.copyOf(list);
    }
}
