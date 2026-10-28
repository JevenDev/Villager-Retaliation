package com.jvn.villagerretaliation.settlement;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class SettlementDebugService {
    public static final int MAX_RADIUS = 256;

    private SettlementDebugService() {
    }

    public static int create(CommandSourceStack source, int radius) {
        var player = source.getPlayer();
        if (!source.hasPermission(2) || player == null || !player.isAlive() || player.isSpectator()) {
            source.sendFailure(Component.literal("Run this command as an operator player."));
            return 0;
        }
        if (radius < 1 || radius > MAX_RADIUS) {
            source.sendFailure(Component.literal("Village radius must be between 1 and " + MAX_RADIUS + " blocks."));
            return 0;
        }
        var level = player.serverLevel();
        var town = SettlementSavedData.get(level).establishDebug(
                level, player.getUUID(), player.blockPosition(), plots(player.position(), radius));
        if (town.isEmpty()) {
            source.sendFailure(Component.literal("Cannot create debug village: the circle overlaps reserved village plots, "
                    + "crosses the world border, or settlement data is read-only. No village was created."));
            return 0;
        }
        var created = town.orElseThrow();
        source.sendSuccess(() -> Component.literal("Created debug village " + created.id().value()
                + " at " + created.center().toShortString() + " with radius " + radius + " blocks ("
                + created.territory().plots().size() + " plots). Plots are whole 16x16 chunks touched by the circle. "
                + "You own this settlement. No buildings or villagers were spawned."), true);
        return 1;
    }

    static Set<Long> plots(Vec3 center, int radius) {
        if (!Double.isFinite(center.x) || !Double.isFinite(center.z) || radius < 1 || radius > MAX_RADIUS
                || Math.abs(center.x) > 30_000_000 || Math.abs(center.z) > 30_000_000) {
            throw new IllegalArgumentException("Invalid debug village circle");
        }
        var plots = new LinkedHashSet<Long>();
        int minX = (int) Math.floor((center.x - radius) / 16);
        int maxX = (int) Math.floor((center.x + radius) / 16);
        int minZ = (int) Math.floor((center.z - radius) / 16);
        int maxZ = (int) Math.floor((center.z + radius) / 16);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = Math.max(x * 16.0 - center.x, Math.max(0, center.x - (x * 16.0 + 16)));
                double dz = Math.max(z * 16.0 - center.z, Math.max(0, center.z - (z * 16.0 + 16)));
                if (dx * dx + dz * dz < (double) radius * radius) plots.add(ChunkPos.asLong(x, z));
            }
        }
        return plots;
    }
}
