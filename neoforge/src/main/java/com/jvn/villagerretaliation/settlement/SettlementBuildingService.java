package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;

public final class SettlementBuildingService {
    private static final int SURVEYS_PER_SECOND = 4;

    private SettlementBuildingService() {
    }

    public static boolean register(ServerPlayer player, VillageAllegianceId id, SettlementArea area) {
        ServerLevel level = player.serverLevel();
        var data = SettlementSavedData.get(level);
        var town = data.get(id).orElse(null);
        if (town == null || !player.isAlive() || player.isSpectator()
                || !town.dimension().equals(level.dimension().location())
                || area.min().getY() < level.getMinBuildHeight() || area.max().getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(area.min()) || !level.getWorldBorder().isWithinBounds(area.max())
                || !data.putArea(VillageAllegianceRegistrySavedData.get(level), id, player.getUUID(), area)) {
            return false;
        }
        refresh(level, town, area);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 20L != 0L) {
            return;
        }
        var towns = SettlementSavedData.get(server.overworld()).settlements();
        for (ServerLevel level : server.getAllLevels()) {
            List<Site> sites = new ArrayList<>();
            for (SettlementRecord town : towns) {
                if (town.dimension().equals(level.dimension().location())) {
                    town.areas().values().stream().filter(SettlementArea::enabled)
                            .forEach(area -> sites.add(new Site(town, area)));
                }
            }
            if (sites.isEmpty()) {
                continue;
            }
            int start = (int) Math.floorMod(level.getGameTime() / 20L * SURVEYS_PER_SECOND, sites.size());
            for (int index = 0; index < Math.min(SURVEYS_PER_SECOND, sites.size()); index++) {
                Site site = sites.get((start + index) % sites.size());
                refresh(level, site.town(), site.area());
            }
        }
    }

    public static void refresh(ServerLevel level, SettlementRecord town, SettlementArea area) {
        var observed = SettlementBuildingSurvey.inspect(level, area);
        var previous = town.surveys().get(area.id());
        if (previous != null && !observed.fullyLoaded()) {
            List<BlockPos> beds = new ArrayList<>(observed.beds());
            previous.beds().stream().filter(pos -> bedUnavailable(level, pos)).forEach(beds::add);
            List<BlockPos> stations = new ArrayList<>(observed.workstations());
            previous.workstations().stream().filter(pos -> !level.hasChunkAt(pos)).forEach(stations::add);
            observed = new SettlementBuildingSurvey.Survey(observed.checkedAt(), beds, stations, previous.fullyLoaded());
        }
        town.recordSurvey(area.id(), observed);
    }

    private static boolean bedUnavailable(ServerLevel level, BlockPos head) {
        if (!level.hasChunkAt(head)) {
            return true;
        }
        var state = level.getBlockState(head);
        return state.getBlock() instanceof BedBlock
                && !level.hasChunkAt(head.relative(state.getValue(BedBlock.FACING).getOpposite()));
    }

    private record Site(SettlementRecord town, SettlementArea area) {
    }
}
