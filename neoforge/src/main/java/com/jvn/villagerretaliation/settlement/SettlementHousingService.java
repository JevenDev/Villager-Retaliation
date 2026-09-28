package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementHousingService {
    private static final String HOME_TAG = "VillagerRetaliationSettlementHome";

    private SettlementHousingService() {
    }

    public static boolean assign(ServerPlayer player, SettlementRecord town, UUID resident, BlockPos bed) {
        if (!authorized(player, town) || resident == null || bed == null || !member(player.serverLevel(), town, resident)) return false;
        var level = player.serverLevel();
        if (level.isOutsideBuildHeight(bed) || !level.getWorldBorder().isWithinBounds(bed)) return false;
        update(level, town);
        refresh(level, town, bed);
        if (!usable(town, bed) || !canUse(level, bed, resident)) return false;
        var owner = owner(town, bed);
        if (owner != null && !owner.equals(resident)) return false;
        if (owner == null && !Boolean.TRUE.equals(availability(town, bed)) && !nativeHome(level, town, resident, bed)) return false;
        if (!town.assignHome(resident, bed)) return false;
        applyLoaded(level, town, resident);
        return true;
    }

    public static boolean release(ServerPlayer player, SettlementRecord town, UUID resident, BlockPos expected) {
        if (!authorized(player, town) || resident == null || expected == null) return false;
        var home = town.homes().get(resident);
        if (home == null || !expected.equals(home.bed())) return false;
        town.assignHome(resident, null);
        applyLoaded(player.serverLevel(), town, resident);
        return true;
    }

    private static boolean authorized(ServerPlayer player, SettlementRecord town) {
        return player != null && town != null && player.isAlive() && !player.isSpectator()
                && town.dimension().equals(player.level().dimension().location())
                && SettlementSavedData.get(player.serverLevel()).get(town.id()).orElse(null) == town
                && town.permits(player.getUUID(), SettlementPermission.STAFF);
    }

    private static boolean member(ServerLevel level, SettlementRecord town, UUID resident) {
        if (town.departures().containsKey(resident) || !VillageAllegianceRegistrySavedData.get(level).canonicalRecord(town.id())
                .map(record -> record.residents().containsKey(resident)).orElse(false)) return false;
        for (var dimension : level.getServer().getAllLevels()) {
            var entity = dimension.getEntity(resident);
            if (entity != null) return entity instanceof Villager villager && villager.isAlive()
                    && com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.canonicalPrimary(dimension, villager).filter(town.id()::equals).isPresent();
        }
        return true;
    }

    private static void applyLoaded(ServerLevel level, SettlementRecord town, UUID resident) {
        for (var dimension : level.getServer().getAllLevels()) {
            if (dimension.getEntity(resident) instanceof Villager villager) {
                reconcile(villager);
                var care = town.residents().get(resident);
                if (care != null) care.remember(villager);
                return;
            }
        }
        var care = town.residents().get(resident);
        var context = care == null ? null : care.context(level);
        if (context != null) {
            var desired = town.homes().get(resident);
            context.getBrain().getMemory(MemoryModuleType.HOME).filter(home ->
                    desired == null || desired.bed() == null || !home.dimension().location().equals(town.dimension())
                            || !home.pos().equals(desired.bed())).ifPresent(home -> releaseNativeHome(town, context, home));
            flushReleases(level, town);
        }
    }

    private static boolean nativeHome(ServerLevel level, SettlementRecord town, UUID resident, BlockPos bed) {
        for (var dimension : level.getServer().getAllLevels()) {
            if (dimension.getEntity(resident) instanceof Villager villager) {
                return villager.getBrain().getMemory(MemoryModuleType.HOME)
                        .filter(home -> home.dimension().location().equals(town.dimension()) && home.pos().equals(bed)).isPresent();
            }
        }
        return false;
    }

    static void update(ServerLevel level, SettlementRecord town) {
        flushReleases(level, town);
        for (var home : List.copyOf(town.homes().values())) {
            if (home.bed() != null && !usable(town, home.bed())) {
                town.assignHome(home.resident(), null);
                applyLoaded(level, town, home.resident());
            }
        }
    }

    private static void flushReleases(ServerLevel level, SettlementRecord town) {
        for (var release : List.copyOf(town.homeReleases().values())) {
            var dimension = level.getServer().getLevel(release.home().dimension());
            if (dimension == null) continue;
            var bed = release.home().pos();
            var chunk = dimension.getChunkSource().getChunkNow(bed.getX() >> 4, bed.getZ() >> 4);
            if (chunk == null) continue;
            String key = "HomeRelease:" + release.id();
            if (SettlementChunkReceipts.version(chunk, key, release.id()) < 1L) {
                if (!claimedByAnother(dimension, bed, release.resident()) && dimension.getPoiManager().existsAtPosition(PoiTypes.HOME, bed)
                        && dimension.getPoiManager().getFreeTickets(bed) == 0) dimension.getPoiManager().release(bed);
                SettlementChunkReceipts.applied(chunk, key, release.id(), 1L);
            }
            town.removeHomeRelease(release.id());
        }
    }

    private static boolean claimedByAnother(ServerLevel level, BlockPos bed, UUID resident) {
        for (var town : SettlementSavedData.get(level).settlements()) {
            var owner = town.dimension().equals(level.dimension().location()) ? owner(town, bed) : null;
            if (owner != null && !owner.equals(resident)) return true;
        }
        for (var entity : level.getAllEntities()) {
            if (entity instanceof Villager other && !other.getUUID().equals(resident)
                    && other.getBrain().getMemory(MemoryModuleType.HOME)
                            .filter(home -> home.dimension().equals(level.dimension()) && home.pos().equals(bed)).isPresent()) return true;
        }
        return false;
    }

    static void observe(Villager villager, SettlementRecord town) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        var home = town.homes().get(villager.getUUID());
        var applied = villager.getPersistentData().getCompound(HOME_TAG);
        if (home != null && (home.bed() != null || !applied.hasUUID("Town") || !applied.getUUID("Town").equals(town.id().value())
                || applied.getLong("Version") < home.version())) return;
        villager.getBrain().getMemory(MemoryModuleType.HOME).filter(memory -> memory.dimension().location().equals(town.dimension())
                && usable(town, memory.pos()) && canUse(level, memory.pos(), villager.getUUID()))
                .ifPresent(memory -> town.assignHome(villager.getUUID(), memory.pos()));
    }

    public static void reconcile(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !villager.isAlive()) return;
        var town = SettlementWorkforceService.home(level, villager).orElse(null);
        if (town == null || !member(level, town, villager.getUUID())) return;
        var home = town.homes().get(villager.getUUID());
        if (home != null && home.bed() != null && !usable(town, home.bed())) {
            town.assignHome(villager.getUUID(), null);
            home = town.homes().get(villager.getUUID());
        }
        var memory = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        var applied = villager.getPersistentData().getCompound(HOME_TAG);
        boolean acknowledged = home != null && applied.hasUUID("Town") && applied.getUUID("Town").equals(town.id().value())
                && applied.getLong("Version") >= home.version();
        if ((home == null || home.bed() == null && acknowledged) && memory != null
                && memory.dimension().location().equals(town.dimension()) && usable(town, memory.pos())
                && canUse(level, memory.pos(), villager.getUUID()) && town.assignHome(villager.getUUID(), memory.pos())) {
            home = town.homes().get(villager.getUUID());
        }
        if (home == null) return;
        var targetLevel = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, town.dimension()));
        if (targetLevel == null) return;
        GlobalPos desired = home.bed() == null ? null : GlobalPos.of(targetLevel.dimension(), home.bed());
        if (acknowledged && Objects.equals(memory, desired)
                && (desired == null || applied.getBoolean("Claimed")
                        && Math.floorMod(level.getGameTime() - villager.getUUID().getLeastSignificantBits(), 200L) != 0L)) return;
        if (!Objects.equals(memory, desired)) {
            if (villager.isSleeping() && level.hasChunkAt(villager.blockPosition())) villager.stopSleeping();
            if (memory != null) {
                releaseNativeHome(town, villager, memory);
                villager.getBrain().eraseMemory(MemoryModuleType.HOME);
                flushReleases(level, town);
            }
            if (desired != null) {
                var bed = desired.pos();
                if (targetLevel.hasChunkAt(bed) && targetLevel.getPoiManager().existsAtPosition(PoiTypes.HOME, bed)
                        && targetLevel.getPoiManager().getFreeTickets(bed) == 0) return;
                villager.getBrain().setMemory(MemoryModuleType.HOME, desired);
            }
        }
        if (desired != null && targetLevel.hasChunkAt(desired.pos())
                && targetLevel.getPoiManager().existsAtPosition(PoiTypes.HOME, desired.pos())
                && targetLevel.getPoiManager().getFreeTickets(desired.pos()) > 0) {
            targetLevel.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(desired.pos()), desired.pos(), 0);
        }
        var receipt = new CompoundTag();
        receipt.putUUID("Town", town.id().value());
        receipt.putLong("Version", home.version());
        receipt.putBoolean("Claimed", desired == null || targetLevel.hasChunkAt(desired.pos())
                && targetLevel.getPoiManager().existsAtPosition(PoiTypes.HOME, desired.pos())
                && targetLevel.getPoiManager().getFreeTickets(desired.pos()) == 0);
        villager.getPersistentData().put(HOME_TAG, receipt);
    }

    private static void releaseNativeHome(SettlementRecord town, Villager villager, GlobalPos memory) {
        var applied = villager.getPersistentData().getCompound(HOME_TAG);
        String source = applied.hasUUID("Town") ? applied.getUUID("Town").toString() : "native";
        var id = UUID.nameUUIDFromBytes((villager.getUUID() + ":" + source + ":" + applied.getLong("Version")
                + ":" + memory.dimension().location() + ":" + memory.pos().asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        town.releaseHome(id, villager.getUUID(), memory);
    }

    public static boolean canAcquire(ServerLevel level, BlockPos bed, UUID resident) {
        for (var town : SettlementSavedData.get(level).settlements()) {
            var home = town.homes().get(resident);
            if (home != null && home.bed() != null
                    && (!town.dimension().equals(level.dimension().location()) || !bed.equals(home.bed()))) return false;
        }
        return canUse(level, bed, resident);
    }

    public static boolean canUse(ServerLevel level, BlockPos bed, UUID resident) {
        for (var town : SettlementSavedData.get(level).settlements()) {
            for (var release : town.homeReleases().values()) {
                if (release.home().dimension().equals(level.dimension()) && release.home().pos().equals(bed)) return false;
            }
            if (!town.dimension().equals(level.dimension().location())) continue;
            var owner = owner(town, bed);
            if (owner != null && !owner.equals(resident)) return false;
        }
        return true;
    }

    public static UUID owner(SettlementRecord town, BlockPos bed) {
        return town.homes().values().stream().filter(home -> bed.equals(home.bed())).map(SettlementHome::resident).findFirst().orElse(null);
    }

    public static Boolean availability(SettlementRecord town, BlockPos bed) {
        for (var area : town.areas().values()) {
            var survey = town.surveys().get(area.id());
            if (area.enabled() && town.owns(area) && area.type() == SettlementAreaType.HOME && survey != null && survey.beds().contains(bed)) {
                return survey.bedAvailability().get(bed);
            }
        }
        return null;
    }

    static boolean usable(SettlementRecord town, BlockPos bed) {
        return town.areas().values().stream().filter(area -> area.enabled() && town.owns(area) && area.type() == SettlementAreaType.HOME)
                .map(area -> town.surveys().get(area.id())).filter(Objects::nonNull).anyMatch(survey -> survey.beds().contains(bed));
    }

    private static void refresh(ServerLevel level, SettlementRecord town, BlockPos bed) {
        for (var area : town.areas().values()) {
            if (area.type() == SettlementAreaType.HOME && area.contains(bed)) SettlementBuildingService.refresh(level, town, area);
        }
    }
}
