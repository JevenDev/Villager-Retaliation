package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.phys.AABB;

public final class VillagerCombatSurvivalService {
    private static final String ACTIVE_PLAYER_KEY = "VillagerRetaliationCombatSurvivalPlayer";
    private static final String ACTIVE_EVENT_KIND_KEY = "VillagerRetaliationCombatSurvivalEventKind";
    private static final String LAST_COMBAT_TICK_KEY = "VillagerRetaliationCombatSurvivalLastCombatTick";
    private static final double NEARBY_PLAYER_RADIUS = 24.0D;
    private static final double HOSTILE_THREAT_RADIUS = 16.0D;
    private static final long QUIET_SETTLE_TICKS = 20L * 8L;
    private static final long NEARBY_THREAT_SCAN_INTERVAL_TICKS = 20L;
    private static final long ACTIVE_COMBAT_WRITE_INTERVAL_TICKS = 20L;
    private static final Map<UUID, Long> NEXT_NEARBY_THREAT_SCAN_TICKS = new HashMap<>();

    private VillagerCombatSurvivalService() {
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || villager.isBaby()) {
            return;
        }

        long gameTime = level.getGameTime();
        boolean followingPlayer = VillagerRecruitmentService.followingPlayerId(villager).isPresent();
        LivingEntity threat = directHostileThreat(villager);
        if (threat == null && followingPlayer) {
            threat = nearbyHostileThreatIfReady(villager, gameTime);
        }
        String eventKind = eventKind(level, villager, threat);
        ServerPlayer player = eventKind == null ? null : reportPlayer(level, villager);
        if (player != null) {
            rememberActiveCombat(villager, player, eventKind, gameTime);
            return;
        }

        maybeFinishActiveCombat(level, villager, gameTime);
    }

    public static void onVillagerDeath(Villager villager) {
        clearActiveCombat(villager);
    }

    public static void onVillagerLeaveLevel(Villager villager) {
        NEXT_NEARBY_THREAT_SCAN_TICKS.remove(villager.getUUID());
    }

    private static void maybeFinishActiveCombat(ServerLevel level, Villager villager, long gameTime) {
        if (!villager.getPersistentData().hasUUID(ACTIVE_PLAYER_KEY)) {
            return;
        }
        long lastCombatTick = villager.getPersistentData().getLong(LAST_COMBAT_TICK_KEY);
        if (gameTime - lastCombatTick < QUIET_SETTLE_TICKS) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(villager.getPersistentData().getUUID(ACTIVE_PLAYER_KEY));
        if (player != null && player.isAlive() && !player.isSpectator()) {
            String eventKind = villager.getPersistentData().getString(ACTIVE_EVENT_KIND_KEY);
            VillagerInteractionTracker.rememberCombatSurvivalReport(
                    level,
                    villager,
                    player,
                    eventKind
            );
            VillagerMoodService.recordCombatSurvival(level, villager, player, eventKind);
        }
        clearActiveCombat(villager);
    }

    private static void rememberActiveCombat(Villager villager, ServerPlayer player, String eventKind, long gameTime) {
        if (villager.getPersistentData().hasUUID(ACTIVE_PLAYER_KEY)
                && villager.getPersistentData().getUUID(ACTIVE_PLAYER_KEY).equals(player.getUUID())
                && eventKind.equals(villager.getPersistentData().getString(ACTIVE_EVENT_KIND_KEY))
                && gameTime - villager.getPersistentData().getLong(LAST_COMBAT_TICK_KEY) < ACTIVE_COMBAT_WRITE_INTERVAL_TICKS) {
            return;
        }
        villager.getPersistentData().putUUID(ACTIVE_PLAYER_KEY, player.getUUID());
        villager.getPersistentData().putString(ACTIVE_EVENT_KIND_KEY, eventKind);
        villager.getPersistentData().putLong(LAST_COMBAT_TICK_KEY, gameTime);
    }

    private static void clearActiveCombat(Villager villager) {
        NEXT_NEARBY_THREAT_SCAN_TICKS.remove(villager.getUUID());
        villager.getPersistentData().remove(ACTIVE_PLAYER_KEY);
        villager.getPersistentData().remove(ACTIVE_EVENT_KIND_KEY);
        villager.getPersistentData().remove(LAST_COMBAT_TICK_KEY);
    }

    private static ServerPlayer reportPlayer(ServerLevel level, Villager villager) {
        Optional<UUID> followingPlayerId = VillagerRecruitmentService.followingPlayerId(villager);
        if (followingPlayerId.isPresent()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(followingPlayerId.get());
            if (isEligiblePlayer(player)) {
                return player;
            }
        }

        ServerPlayer closest = null;
        double closestDistanceSqr = NEARBY_PLAYER_RADIUS * NEARBY_PLAYER_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (!isEligiblePlayer(player)) {
                continue;
            }
            double distanceSqr = player.distanceToSqr(villager);
            if (distanceSqr <= closestDistanceSqr) {
                closest = player;
                closestDistanceSqr = distanceSqr;
            }
        }
        return closest;
    }

    private static boolean isEligiblePlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    private static String eventKind(ServerLevel level, Villager villager, LivingEntity threat) {
        if (threat == null) {
            return null;
        }
        Raid raid = level.getRaidAt(villager.blockPosition());
        if (raid != null && raid.isActive() && !raid.isVictory() && !raid.isLoss()) {
            return "raid";
        }
        return isNight(level) ? "night" : null;
    }

    private static boolean isNight(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        return dayTime >= 13000L && dayTime <= 23000L;
    }

    private static LivingEntity directHostileThreat(Villager villager) {
        LivingEntity target = villager.getTarget();
        if (isNaturalHostile(villager, target)) {
            return target;
        }

        LivingEntity lastHurtBy = villager.getLastHurtByMob();
        if (isNaturalHostile(villager, lastHurtBy)) {
            return lastHurtBy;
        }
        return null;
    }

    private static LivingEntity nearbyHostileThreatIfReady(Villager villager, long gameTime) {
        UUID villagerId = villager.getUUID();
        Long nextScan = NEXT_NEARBY_THREAT_SCAN_TICKS.get(villagerId);
        if (nextScan == null) {
            long firstScan = gameTime + scanStagger(villagerId, NEARBY_THREAT_SCAN_INTERVAL_TICKS);
            if (firstScan > gameTime) {
                NEXT_NEARBY_THREAT_SCAN_TICKS.put(villagerId, firstScan);
                return null;
            }
        } else if (nextScan > gameTime) {
            return null;
        }

        NEXT_NEARBY_THREAT_SCAN_TICKS.put(villagerId, gameTime + NEARBY_THREAT_SCAN_INTERVAL_TICKS);
        return nearbyHostileThreat(villager);
    }

    private static LivingEntity nearbyHostileThreat(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return null;
        }
        AABB area = villager.getBoundingBox().inflate(HOSTILE_THREAT_RADIUS);
        LivingEntity closest = null;
        double closestDistanceSqr = HOSTILE_THREAT_RADIUS * HOSTILE_THREAT_RADIUS;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area, candidate -> isNaturalHostile(villager, candidate))) {
            double distanceSqr = candidate.distanceToSqr(villager);
            if (distanceSqr <= closestDistanceSqr) {
                closest = candidate;
                closestDistanceSqr = distanceSqr;
            }
        }
        return closest;
    }

    private static boolean isNaturalHostile(Villager villager, LivingEntity target) {
        return target != null && VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, target);
    }

    private static long scanStagger(UUID villagerId, long intervalTicks) {
        if (intervalTicks <= 1L) {
            return 0L;
        }
        return Math.floorMod(villagerId.getMostSignificantBits() ^ villagerId.getLeastSignificantBits(), intervalTicks);
    }
}
