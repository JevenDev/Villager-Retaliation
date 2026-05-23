package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerRecruitmentService {
    private static final String FOLLOWING_PLAYER_KEY = "VillagerRetaliationFollowingPlayer";
    private static final String FOLLOW_START_HEALTH_KEY = "VillagerRetaliationFollowStartHealth";
    private static final String FOLLOW_MIN_HEALTH_KEY = "VillagerRetaliationFollowMinHealth";
    private static final String FOLLOW_START_X_KEY = "VillagerRetaliationFollowStartX";
    private static final String FOLLOW_START_Y_KEY = "VillagerRetaliationFollowStartY";
    private static final String FOLLOW_START_Z_KEY = "VillagerRetaliationFollowStartZ";
    private static final String FOLLOW_START_BIOME_KEY = "VillagerRetaliationFollowStartBiome";
    private static final String FOLLOW_MAX_DISTANCE_KEY = "VillagerRetaliationFollowMaxDistance";
    private static final String FOLLOW_USED_BOAT_KEY = "VillagerRetaliationFollowUsedBoat";
    private static final String FOLLOW_CROSSED_OCEAN_KEY = "VillagerRetaliationFollowCrossedOcean";
    private static final String HIRED_PLAYER_KEY = "VillagerRetaliationHiredPlayer";
    private static final double FOLLOW_START_DISTANCE_SQR = 5.0D * 5.0D;
    private static final double FOLLOW_STOP_DISTANCE_SQR = 2.5D * 2.5D;
    private static final double FOLLOW_SPEED = 0.62D;
    private static final long FOLLOW_TRAVEL_MEMORY_INTERVAL_TICKS = 20L;
    private static final long FOLLOW_REPUTATION_CHECK_INTERVAL_TICKS = 40L;
    private static final double FOLLOW_VEHICLE_BOARD_DISTANCE_SQR = 4.0D * 4.0D;
    private static final long RECENT_BETRAYED_FOLLOWER_DEATH_NOTICE_TICKS = 200L;
    private static final Map<UUID, RecentRecruitmentOwner> RECENT_BETRAYED_FOLLOWERS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_FOLLOW_TRAVEL_MEMORY_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_FOLLOW_REPUTATION_CHECK_TICKS = new HashMap<>();

    private VillagerRecruitmentService() {
    }

    public static boolean canRecruit(ServerLevel level, Villager villager, ServerPlayer player) {
        return !villager.isBaby()
                && VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                >= VillagerReputationLevel.NEUTRAL.trustRank();
    }

    public static boolean isFollowing(Villager villager, ServerPlayer player) {
        return villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)
                && villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY).equals(player.getUUID());
    }

    public static boolean isFollowingAnyPlayer(Villager villager) {
        return villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY);
    }

    public static Optional<UUID> followingPlayerId(Villager villager) {
        if (!villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)) {
            return Optional.empty();
        }
        return Optional.of(villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY));
    }

    public static boolean isHiredAnyPlayer(Villager villager) {
        return villager.getPersistentData().hasUUID(HIRED_PLAYER_KEY);
    }

    public static boolean toggleFollow(ServerLevel level, Villager villager, ServerPlayer player) {
        if (isFollowing(villager, player)) {
            stopFollowing(level, villager, player);
            sendNoLongerFollowingNotice(player, villager);
            return false;
        }
        beginFollowing(level, villager, player);
        sendFollowingNotice(player, villager);
        return true;
    }

    public static void stopFollowing(Villager villager) {
        clearFollowTarget(villager);
    }

    public static void stopFollowing(ServerLevel level, Villager villager, ServerPlayer player) {
        if (isFollowing(villager, player)) {
            String scenario = wasFollowerInjured(villager) ? "injured" : "safe";
            rememberRecruitmentMemory(level, villager, player, scenario);
            if (isNearVillage(level, villager)) {
                VillagerInteractionTracker.rememberRecruitmentFollowup(level, villager, player, scenario);
            }
        }
        clearFollowTarget(villager);
    }

    public static void rememberFollowerDamage(Villager villager) {
        if (!villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)) {
            return;
        }
        float currentMin = villager.getPersistentData().contains(FOLLOW_MIN_HEALTH_KEY)
                ? villager.getPersistentData().getFloat(FOLLOW_MIN_HEALTH_KEY)
                : villager.getHealth();
        villager.getPersistentData().putFloat(FOLLOW_MIN_HEALTH_KEY, Math.min(currentMin, villager.getHealth()));
    }

    public static void stopFollowingIfFollowingAttacker(Villager villager, Player attacker) {
        if (attacker != null
                && villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)
                && villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY).equals(attacker.getUUID())) {
            rememberBetrayedFollower(villager, attacker);
            if (attacker instanceof ServerPlayer serverPlayer) {
                rememberRecruitmentMemory(serverPlayer.serverLevel(), villager, serverPlayer, "betrayed");
                VillagerInteractionTracker.rememberRecruitmentFollowup(serverPlayer.serverLevel(), villager, serverPlayer, "betrayed");
                clearFollowTarget(villager);
                sendNoLongerFollowingNotice(serverPlayer, villager);
                sendFollowerBetrayalDialogue(villager, serverPlayer);
            } else {
                clearFollowTarget(villager);
            }
        }
    }

    public static void notifyRecruitmentDeath(Villager villager, Entity killer) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        boolean sentNotice = false;
        if (villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY));
            if (player != null) {
                awardLuredKillIfOwner(player, killer);
                VillagerNotifications.sendHud(
                        player,
                        level,
                        villager,
                        "recruitment.follower_death",
                        replacements(villager),
                        displayName(villager) + " died while following you.",
                        VillagerReputationNoticeKind.VILLAGER_DEATH
                );
                sentNotice = true;
            }
        }
        if (!sentNotice) {
            notifyRecentBetrayedFollowerDeath(level, villager, killer);
        }
        if (villager.getPersistentData().hasUUID(HIRED_PLAYER_KEY)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(villager.getPersistentData().getUUID(HIRED_PLAYER_KEY));
            if (player != null) {
                VillagerNotifications.sendHud(
                        player,
                        level,
                        villager,
                        "recruitment.hired_death",
                        replacements(villager),
                        displayName(villager) + " died while hired by you.",
                        VillagerReputationNoticeKind.VILLAGER_DEATH
                );
            }
        }
    }

    public static void sendHiredNotice(ServerPlayer player, Villager villager) {
        VillagerNotifications.sendHud(
                player,
                player.serverLevel(),
                villager,
                "recruitment.hired",
                replacements(villager),
                displayName(villager) + " hired.",
                VillagerReputationNoticeKind.VILLAGER_HIRED
        );
    }

    public static void sendFiredNotice(ServerPlayer player, Villager villager) {
        VillagerNotifications.sendHud(
                player,
                player.serverLevel(),
                villager,
                "recruitment.fired",
                replacements(villager),
                displayName(villager) + " fired.",
                VillagerReputationNoticeKind.VILLAGER_FIRED
        );
    }

    public static void sendNoLongerFollowingNotice(ServerPlayer player, Villager villager) {
        VillagerNotifications.sendHud(
                player,
                player.serverLevel(),
                villager,
                "recruitment.follow_stop",
                replacements(villager),
                displayName(villager) + " is no longer following you.",
                VillagerReputationNoticeKind.VILLAGER_DISMISSED
        );
    }

    public static void onVillagerTickPre(Villager villager) {
        if (villager.level().isClientSide || !isFollowingAnyPlayer(villager)) {
            return;
        }
        suppressFollowerAi(villager);
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)) {
            return;
        }

        UUID playerId = villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        updateTravelMemoryIfReady(level, villager);
        if (!isValidFollowTarget(level, villager, player)) {
            clearFollowTarget(villager);
            return;
        }
        syncVehicleWithPlayer(villager, player);
        if (isBeyondMaxFollowDistance(villager, player)) {
            stopFollowing(level, villager, player);
            sendNoLongerFollowingNotice(player, villager);
            return;
        }
        if (villager.isSleeping() || villager.isTrading() || villager.getTarget() != null || villager.getLastHurtByMob() != null) {
            suppressFollowerAi(villager);
            return;
        }

        suppressFollowerAi(villager);
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (isRidingSameVehicle(villager, player)) {
            villager.getNavigation().stop();
            return;
        }

        Entity followTarget = player.getVehicle() == null ? player : player.getVehicle();
        double distanceSqr = villager.distanceToSqr(player);
        if (distanceSqr > FOLLOW_START_DISTANCE_SQR) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.PATH);
            villager.getNavigation().moveTo(followTarget, FOLLOW_SPEED);
        } else if (distanceSqr < FOLLOW_STOP_DISTANCE_SQR) {
            villager.getNavigation().stop();
        }
    }

    private static void syncVehicleWithPlayer(Villager villager, ServerPlayer player) {
        Entity playerVehicle = player.getVehicle();
        if (playerVehicle == null) {
            dismountFollower(villager);
            return;
        }
        if (villager.getVehicle() == playerVehicle) {
            return;
        }
        if (villager.isPassenger()) {
            dismountFollower(villager);
        }
        if (!villager.isPassenger() && villager.distanceToSqr(playerVehicle) <= FOLLOW_VEHICLE_BOARD_DISTANCE_SQR) {
            villager.startRiding(playerVehicle);
        }
    }

    private static boolean isRidingSameVehicle(Villager villager, ServerPlayer player) {
        Entity playerVehicle = player.getVehicle();
        return playerVehicle != null && villager.getVehicle() == playerVehicle;
    }

    private static void suppressFollowerAi(Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.NEAREST_HOSTILE);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY_ENTITY);
        villager.setTarget(null);
        villager.setLastHurtByMob(null);
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
    }

    private static boolean isValidFollowTarget(ServerLevel level, Villager villager, ServerPlayer player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && villager.isAlive()
                && hasRecentlyValidReputation(level, villager, player);
    }

    private static boolean isBeyondMaxFollowDistance(Villager villager, ServerPlayer player) {
        double maxDistance = VillagerRetaliationConfig.MAX_FOLLOW_DISTANCE.get();
        return villager.distanceToSqr(player) > maxDistance * maxDistance;
    }

    private static boolean hasRecentlyValidReputation(ServerLevel level, Villager villager, ServerPlayer player) {
        long gameTime = level.getGameTime();
        UUID villagerId = villager.getUUID();
        Long nextCheck = NEXT_FOLLOW_REPUTATION_CHECK_TICKS.get(villagerId);
        if (nextCheck != null && nextCheck > gameTime) {
            return true;
        }

        NEXT_FOLLOW_REPUTATION_CHECK_TICKS.put(villagerId, gameTime + FOLLOW_REPUTATION_CHECK_INTERVAL_TICKS);
        return VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                >= VillagerReputationLevel.NEUTRAL.trustRank();
    }

    private static void clearFollowTarget(Villager villager) {
        NEXT_FOLLOW_TRAVEL_MEMORY_TICKS.remove(villager.getUUID());
        NEXT_FOLLOW_REPUTATION_CHECK_TICKS.remove(villager.getUUID());
        dismountFollower(villager);
        villager.getPersistentData().remove(FOLLOWING_PLAYER_KEY);
        villager.getPersistentData().remove(FOLLOW_START_HEALTH_KEY);
        villager.getPersistentData().remove(FOLLOW_MIN_HEALTH_KEY);
        villager.getPersistentData().remove(FOLLOW_START_X_KEY);
        villager.getPersistentData().remove(FOLLOW_START_Y_KEY);
        villager.getPersistentData().remove(FOLLOW_START_Z_KEY);
        villager.getPersistentData().remove(FOLLOW_START_BIOME_KEY);
        villager.getPersistentData().remove(FOLLOW_MAX_DISTANCE_KEY);
        villager.getPersistentData().remove(FOLLOW_USED_BOAT_KEY);
        villager.getPersistentData().remove(FOLLOW_CROSSED_OCEAN_KEY);
        villager.getNavigation().stop();
    }

    private static void dismountFollower(Villager villager) {
        if (villager.isPassenger()) {
            villager.stopRiding();
        }
    }

    private static void beginFollowing(ServerLevel level, Villager villager, ServerPlayer player) {
        BlockPos start = villager.blockPosition();
        villager.getPersistentData().putUUID(FOLLOWING_PLAYER_KEY, player.getUUID());
        villager.getPersistentData().putFloat(FOLLOW_START_HEALTH_KEY, villager.getHealth());
        villager.getPersistentData().putFloat(FOLLOW_MIN_HEALTH_KEY, villager.getHealth());
        villager.getPersistentData().putInt(FOLLOW_START_X_KEY, start.getX());
        villager.getPersistentData().putInt(FOLLOW_START_Y_KEY, start.getY());
        villager.getPersistentData().putInt(FOLLOW_START_Z_KEY, start.getZ());
        villager.getPersistentData().putString(FOLLOW_START_BIOME_KEY, biomeName(level, start));
        villager.getPersistentData().putInt(FOLLOW_MAX_DISTANCE_KEY, 0);
        villager.getPersistentData().putBoolean(FOLLOW_USED_BOAT_KEY, false);
        villager.getPersistentData().putBoolean(FOLLOW_CROSSED_OCEAN_KEY, isOceanBiome(level, start));
    }

    private static void rememberRecruitmentMemory(ServerLevel level, Villager villager, ServerPlayer player, String scenario) {
        VillagerInteractionTracker.rememberRecruitmentMemory(
                level,
                villager,
                player,
                scenario,
                villager.getPersistentData().getString(FOLLOW_START_BIOME_KEY),
                followDistanceBlocks(villager),
                villager.getPersistentData().getBoolean(FOLLOW_USED_BOAT_KEY),
                villager.getPersistentData().getBoolean(FOLLOW_CROSSED_OCEAN_KEY)
        );
    }

    private static void rememberBoatTripIfRiding(Villager villager) {
        if (villager.getVehicle() instanceof Boat && !villager.getPersistentData().getBoolean(FOLLOW_USED_BOAT_KEY)) {
            villager.getPersistentData().putBoolean(FOLLOW_USED_BOAT_KEY, true);
        }
    }

    private static void updateTravelMemoryIfReady(ServerLevel level, Villager villager) {
        long gameTime = level.getGameTime();
        Long nextUpdate = NEXT_FOLLOW_TRAVEL_MEMORY_TICKS.get(villager.getUUID());
        if (nextUpdate != null && nextUpdate > gameTime) {
            return;
        }
        NEXT_FOLLOW_TRAVEL_MEMORY_TICKS.put(villager.getUUID(), gameTime + FOLLOW_TRAVEL_MEMORY_INTERVAL_TICKS);
        updateTravelMemory(level, villager);
        rememberBoatTripIfRiding(villager);
    }

    private static void updateTravelMemory(ServerLevel level, Villager villager) {
        int distance = followDistanceBlocks(villager);
        int currentMax = villager.getPersistentData().contains(FOLLOW_MAX_DISTANCE_KEY)
                ? villager.getPersistentData().getInt(FOLLOW_MAX_DISTANCE_KEY)
                : 0;
        if (distance > currentMax) {
            villager.getPersistentData().putInt(FOLLOW_MAX_DISTANCE_KEY, distance);
        }
        if (!villager.getPersistentData().getBoolean(FOLLOW_CROSSED_OCEAN_KEY)
                && isOceanBiome(level, villager.blockPosition())) {
            villager.getPersistentData().putBoolean(FOLLOW_CROSSED_OCEAN_KEY, true);
        }
    }

    private static int followDistanceBlocks(Villager villager) {
        int currentMax = villager.getPersistentData().contains(FOLLOW_MAX_DISTANCE_KEY)
                ? villager.getPersistentData().getInt(FOLLOW_MAX_DISTANCE_KEY)
                : 0;
        if (!villager.getPersistentData().contains(FOLLOW_START_X_KEY)
                || !villager.getPersistentData().contains(FOLLOW_START_Y_KEY)
                || !villager.getPersistentData().contains(FOLLOW_START_Z_KEY)) {
            return currentMax;
        }
        BlockPos start = new BlockPos(
                villager.getPersistentData().getInt(FOLLOW_START_X_KEY),
                villager.getPersistentData().getInt(FOLLOW_START_Y_KEY),
                villager.getPersistentData().getInt(FOLLOW_START_Z_KEY)
        );
        return Math.max(currentMax, (int) Math.round(Math.sqrt(villager.blockPosition().distSqr(start))));
    }

    private static String biomeName(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos)
                .unwrapKey()
                .map(key -> VillagerInteractionTextUtil.resourcePathName(key.location()))
                .orElse("the wilds");
    }

    private static boolean isOceanBiome(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).is(BiomeTags.IS_OCEAN);
    }

    private static boolean wasFollowerInjured(Villager villager) {
        if (!villager.getPersistentData().contains(FOLLOW_START_HEALTH_KEY)) {
            return false;
        }
        float startHealth = villager.getPersistentData().getFloat(FOLLOW_START_HEALTH_KEY);
        float minHealth = villager.getPersistentData().contains(FOLLOW_MIN_HEALTH_KEY)
                ? villager.getPersistentData().getFloat(FOLLOW_MIN_HEALTH_KEY)
                : villager.getHealth();
        minHealth = Math.min(minHealth, villager.getHealth());
        return minHealth + 0.5F < startHealth;
    }

    private static boolean isNearVillage(ServerLevel level, Villager villager) {
        return level.sectionsToVillage(SectionPos.of(villager.blockPosition())) <= 2;
    }

    private static void rememberBetrayedFollower(Villager villager, Player attacker) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        RECENT_BETRAYED_FOLLOWERS.put(
                villager.getUUID(),
                new RecentRecruitmentOwner(attacker.getUUID(), level.getGameTime() + RECENT_BETRAYED_FOLLOWER_DEATH_NOTICE_TICKS)
        );
    }

    private static void notifyRecentBetrayedFollowerDeath(ServerLevel level, Villager villager, Entity killer) {
        RecentRecruitmentOwner recentOwner = RECENT_BETRAYED_FOLLOWERS.remove(villager.getUUID());
        if (recentOwner == null || level.getGameTime() > recentOwner.expiresGameTime()) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(recentOwner.playerId());
        if (player != null) {
            awardLuredKillIfOwner(player, killer);
            VillagerNotifications.sendHud(
                    player,
                    level,
                    villager,
                    "recruitment.betrayed_follower_death",
                    replacements(villager),
                    displayName(villager) + " died after you broke their trust.",
                    VillagerReputationNoticeKind.VILLAGER_DEATH
            );
        }
    }

    private static void awardLuredKillIfOwner(ServerPlayer owner, Entity killer) {
        if (killer instanceof ServerPlayer killerPlayer && killerPlayer.getUUID().equals(owner.getUUID())) {
            VillagerReputationAdvancements.onLuredVillagerKilled(killerPlayer);
        }
    }

    private static void sendFollowerBetrayalDialogue(Villager villager, ServerPlayer player) {
        PacketDistributor.sendToPlayer(
                player,
                new VillagerInteractionNoticePayload(
                        villager.getId(),
                        followerBetrayalResponse(villager, player),
                        ""
                )
        );
    }

    private static void sendFollowingNotice(ServerPlayer player, Villager villager) {
        VillagerNotifications.sendHud(
                player,
                player.serverLevel(),
                villager,
                "recruitment.follow_start",
                replacements(villager),
                displayName(villager) + " is following you.",
                VillagerReputationNoticeKind.VILLAGER_FOLLOWING
        );
    }

    private static Map<String, String> replacements(Villager villager) {
        return VillagerNotifications.replacements("villager", displayName(villager));
    }

    private static String followerBetrayalResponse(Villager villager, ServerPlayer player) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return "";
        }
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        return VillagerDialogueResources.message(context, "interaction.follow_betrayal").orElse("");
    }

    private static String displayName(Villager villager) {
        return VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
    }

    private record RecentRecruitmentOwner(UUID playerId, long expiresGameTime) {
    }
}
