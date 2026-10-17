package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class ExploredMapCaptureChecks {
    static CompletableFuture<Void> prepare() {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        return CompletableFuture.runAsync(() -> {
            var player = server.getPlayerList().getPlayer(id); var level = player.serverLevel();
            var pos = new BlockPos.MutableBlockPos();
            for (int z = -25; z <= 25; z++) for (int x = -25; x <= 25; x++) {
                int top = x < -8 ? 92 : 96 + Math.max(0, x - 12) / 4;
                for (int y = 80; y <= top; y++) {
                    var state = y == top ? (x < -6 ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState() : Blocks.STONE.defaultBlockState();
                    level.setBlock(pos.set(x, y, z), state, 2);
                }
                for (int y = top + 1; y <= 96; y++) level.setBlock(pos.set(x, y, z), Blocks.WATER.defaultBlockState(), 2);
            }
            for (int z = -12; z <= 12; z++) for (int y = 97; y <= 104; y++) level.setBlock(pos.set(3, y, z), Blocks.STONE.defaultBlockState(), 2);
            var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
            for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) level.setBlock(pos.set(x, 103, z), leaves, 2);
            for (int tz : new int[]{-9, 7, 16}) for (int tx : new int[]{8, 19}) {
                int ground = 96 + Math.max(0, tx - 12) / 4;
                for (int y = ground + 1; y <= ground + 6; y++) level.setBlock(pos.set(tx, y, tz), Blocks.OAK_LOG.defaultBlockState(), 2);
                for (int y = ground + 4; y <= ground + 7; y++) for (int z = tz - 2; z <= tz + 2; z++) for (int x = tx - 2; x <= tx + 2; x++)
                    if (x != tx || z != tz || y > ground + 6) level.setBlock(pos.set(x, y, z), leaves, 2);
            }
            for (int y = 97; y <= 101; y++) level.setBlock(pos.set(10, y, 10), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            level.setBlock(pos.set(11, 105, 10), leaves, 2);
            for (int z = -7; z <= -3; z++) for (int x = -2; x <= 2; x++) for (int y = 86; y <= 90; y++)
                level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos.set(0, 85, -5), Blocks.DIAMOND_ORE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(-4, 97, 5), Blocks.SHORT_GRASS.defaultBlockState(), 2);
            level.setBlock(new BlockPos(-4, 97, 6), Blocks.POPPY.defaultBlockState(), 2);
            level.setBlock(new BlockPos(-4, 97, 7), Blocks.SNOW.defaultBlockState(), 2);
            level.setBlock(new BlockPos(-4, 97, 8), Blocks.OAK_SLAB.defaultBlockState(), 2);
            level.setBlock(new BlockPos(-4, 97, 0), Blocks.TORCH.defaultBlockState(), 2);
            level.setBlock(new BlockPos(-4, 97, 16), Blocks.LANTERN.defaultBlockState(), 2);
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
                level.setBlock(new BlockPos(17, 98, -10).relative(direction), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(17, 98, -10), Blocks.LAVA.defaultBlockState(), 2);
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(.5, 97, .5);
        }, server);
    }
    static boolean complete() {
        var mc = Minecraft.getInstance();
        if (mc.player.getY() < 96 || mc.player.isSpectator() || mc.level.canSeeSky(mc.player.blockPosition())) return false;
        for (int z = -24; z <= 24; z++) for (int x = -24; x <= 24; x++) {
            if (!MapSnapshot.inObservation(x, z, .5, .5)) continue;
            var column = column(x, z);
            if (column == null || column.blocks().isEmpty() || column.blocks().getFirst().y() < 90) return false;
        }
        return light(-4, 0) >= 14 && light(-4, 16) == 15;
    }
    private static int light(int x, int z) {
        var column = column(x, z);
        return column == null ? 0 : column.blocks().stream().mapToInt(MapSnapshot.Block::light).max().orElse(0);
    }
    static void verify() {
        require(light(-4, 0) >= 14 && light(-4, 16) == 15,
                "visible torch and lantern retain their actual block light");
        require(light(-3, 0) > light(-1, 0) && light(-1, 0) > light(4, 0),
                "light falls off across the ground and the ridge blocks its propagation");
        require(column(0, -5).blocks().stream().noneMatch(b -> b.y() < 96), "sealed cave floors and walls are excluded from surface capture");
        require(column(0, -5).exterior(), "new terrain is marked as outdoor-only");
        require(column(20, 0) != null, "surface capture reaches behind a ridge without leaving eye-line holes");
        require(column(10, 10).top() >= 101 && column(10, 10).blocks().size() == 1, "flat capture stores only the pillar surface");
        require(column(-12, 0).blocks().getFirst().state().contains("water"), "shoreline capture retains actual water");
        require(column(8, 7).blocks().getFirst().state().contains("oak_leaves"), "forest capture retains the canopy");
        for (int z = -24; z <= 24; z++) for (int x = -24; x <= 24; x++) {
            var captured = column(x, z);
            if (captured != null) require(captured.blocks().size() <= 2, "flat capture stores only a surface and optional bounded fluid depth");
        }
    }
    static CompletableFuture<Void> prepareSpectator() {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        return CompletableFuture.runAsync(() -> {
            var player = server.getPlayerList().getPlayer(id);
            player.serverLevel().setBlock(new BlockPos(20, 108, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            player.serverLevel().setBlock(new BlockPos(40, 110, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            player.setGameMode(GameType.SPECTATOR); player.teleportTo(32.5, 220, .5);
        }, server);
    }
    static boolean spectatorComplete() {
        var mc = Minecraft.getInstance();
        if (!mc.player.isSpectator() || mc.player.getY() < 219) return false;
        var existing = column(20, 0); var fresh = column(40, 0);
        return existing != null && fresh != null && existing.top() == 108 && fresh.top() == 110
                && existing.blocks().getFirst().state().contains("gold_block") && fresh.blocks().getFirst().state().contains("gold_block");
    }
    static CompletableFuture<Void> prepareMovement() {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        mc.options.renderDistance().set(8); mc.options.broadcastOptions();
        return CompletableFuture.runAsync(() -> {
            var player = server.getPlayerList().getPlayer(id); var level = player.serverLevel();
            var pos = new BlockPos.MutableBlockPos();
            for (int z = 55; z <= 105; z++) for (int x = -25; x <= 121; x++)
                level.setBlock(pos.set(x, 128, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            player.teleportTo(.5, 180, 80.5);
        }, server);
    }
    static boolean movementReady() {
        var mc = Minecraft.getInstance();
        if (Math.abs(mc.player.getX() - .5) > .1 || Math.abs(mc.player.getZ() - 80.5) > .1) return false;
        for (int z = 56; z <= 104; z += 8) for (int x = -24; x <= 120; x += 8) {
            var chunk = mc.level.getChunkSource().getChunk(x >> 4, z >> 4, false);
            if (chunk == null || chunk.getBlockState(new BlockPos(x, 128, z)).getBlock() != Blocks.GRASS_BLOCK) return false;
        }
        return true;
    }
    static CompletableFuture<Void> move(int x) {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        return CompletableFuture.runAsync(() -> server.getPlayerList().getPlayer(id).teleportTo(x + .5, 180, 80.5), server);
    }
    static boolean movementComplete() {
        for (int z = 56; z <= 104; z++) for (int x = -24; x <= 120; x++) {
            boolean visited = false;
            for (int px = 0; px <= 96; px += 8) if (MapSnapshot.inObservation(x, z, px + .5, 80.5)) { visited = true; break; }
            if (!visited) continue;
            var column = column(x, z);
            if (column == null || !column.exterior() || column.blocks().isEmpty() || column.blocks().getFirst().y() != 128) return false;
        }
        return true;
    }
    static CompletableFuture<Void> prepareRange() {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        mc.options.renderDistance().set(2); mc.options.broadcastOptions();
        return CompletableFuture.runAsync(() -> {
            var player = server.getPlayerList().getPlayer(id);
            var pos = new BlockPos.MutableBlockPos();
            for (int z = 2016; z <= 2080; z++) for (int x = 2016; x <= 2080; x++)
                player.serverLevel().setBlock(pos.set(x, 64, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            player.serverLevel().setBlock(new BlockPos(2064, 150, 2048), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            player.serverLevel().setBlock(new BlockPos(2144, 150, 2048), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
            player.teleportTo(2048.5, 220, 2048.5);
        }, server);
    }
    static CompletableFuture<Void> moveRange() {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        return CompletableFuture.runAsync(() -> server.getPlayerList().getPlayer(id).teleportTo(2052.5, 220, 2048.5), server);
    }
    static boolean rangeNear(String state) { return rangeBlock(2064, state); }
    static boolean rangeFar(String state) { return rangeBlock(2144, state); }
    private static boolean rangeBlock(int x, String state) {
        var column = column(x, 2048);
        return column != null && column.blocks().stream().anyMatch(b -> b.y() == 150 && b.state().contains(state));
    }
    static CompletableFuture<Void> changeRangeBlocks() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return CompletableFuture.runAsync(() -> {
            server.overworld().setBlock(new BlockPos(2064, 150, 2048), Blocks.IRON_BLOCK.defaultBlockState(), 2);
            server.overworld().setBlock(new BlockPos(2144, 150, 2048), Blocks.EMERALD_BLOCK.defaultBlockState(), 2);
        }, server);
    }
    private static MapSnapshot.Column column(int x, int z) {
        var tile = ExploredMapClient.repository().get(MapSnapshot.tile(x), MapSnapshot.tile(z));
        if (tile == null) return null;
        int index = Math.floorMod(x, 8) + 8 * Math.floorMod(z, 8);
        return tile.columns().stream().filter(c -> c.index() == index).findFirst().orElse(null);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
