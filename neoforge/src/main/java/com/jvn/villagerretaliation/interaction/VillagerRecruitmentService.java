package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.notification.VillagerNotifications;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
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
    private static final String FOLLOW_MODE_KEY = "VillagerRetaliationFollowMode";
    private static final String FOLLOW_MODE_FOLLOW = "follow";
    private static final String FOLLOW_MODE_STAY = "stay";
    private static final String FOLLOW_START_HEALTH_KEY = "VillagerRetaliationFollowStartHealth";
    private static final String FOLLOW_MIN_HEALTH_KEY = "VillagerRetaliationFollowMinHealth";
    private static final String FOLLOW_START_X_KEY = "VillagerRetaliationFollowStartX";
    private static final String FOLLOW_START_Y_KEY = "VillagerRetaliationFollowStartY";
    private static final String FOLLOW_START_Z_KEY = "VillagerRetaliationFollowStartZ";
    private static final String FOLLOW_START_BIOME_KEY = "VillagerRetaliationFollowStartBiome";
    private static final String FOLLOW_MAX_DISTANCE_KEY = "VillagerRetaliationFollowMaxDistance";
    private static final String FOLLOW_USED_BOAT_KEY = "VillagerRetaliationFollowUsedBoat";
    private static final String FOLLOW_CROSSED_OCEAN_KEY = "VillagerRetaliationFollowCrossedOcean";
    private static final String STAY_ANCHOR_X_KEY = "VillagerRetaliationStayAnchorX";
    private static final String STAY_ANCHOR_Y_KEY = "VillagerRetaliationStayAnchorY";
    private static final String STAY_ANCHOR_Z_KEY = "VillagerRetaliationStayAnchorZ";
    private static final double FOLLOW_START_DISTANCE_SQR = 1.5D * 1.5D;
    private static final double FOLLOW_STOP_DISTANCE_SQR = 0.75D * 0.75D;
    private static final double FOLLOW_FORMATION_SCAN_RADIUS = 16.0D;
    private static final int FOLLOW_FORMATION_COLUMNS = 3;
    private static final double FOLLOW_FORMATION_BACK_DISTANCE = 2.75D;
    private static final double FOLLOW_FORMATION_LATERAL_SPACING = 2.0D;
    private static final double FOLLOW_FORMATION_ROW_SPACING = 1.75D;
    private static final double STAY_RETURN_START_DISTANCE_SQR = 2.25D * 2.25D;
    private static final double STAY_RETURN_STOP_DISTANCE_SQR = 1.25D * 1.25D;
    private static final double STAY_HERE_SPEED = 0.52D;
    private static final double FOLLOW_SPEED = 0.62D;
    private static final int FOLLOW_PATH_RECALCULATION_MIN_TICKS = 4;
    private static final int FOLLOW_PATH_RECALCULATION_RANDOM_TICKS = 7;
    private static final double FOLLOW_TARGET_MOVED_DISTANCE_SQR = 1.0D;
    private static final long FOLLOW_TRAVEL_MEMORY_INTERVAL_TICKS = 20L;
    private static final long FOLLOW_REPUTATION_CHECK_INTERVAL_TICKS = 40L;
    private static final long LEFT_BEHIND_PROXIMITY_SCAN_INTERVAL_TICKS = 20L;
    private static final double FOLLOW_VEHICLE_BOARD_DISTANCE_SQR = 4.0D * 4.0D;
    private static final long RECENT_BETRAYED_FOLLOWER_DEATH_NOTICE_TICKS = 200L;
    private static final String LEFT_BEHIND_SCENARIO = "left_behind";
    private static final String LEFT_BEHIND_OPTION_ID = "recruitment_left_behind";
    private static final Map<UUID, RecentRecruitmentOwner> RECENT_BETRAYED_FOLLOWERS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_FOLLOW_TRAVEL_MEMORY_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_FOLLOW_REPUTATION_CHECK_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_LEFT_BEHIND_PROXIMITY_SCAN_TICKS = new HashMap<>();
    private static final Map<UUID, Long> LAST_FOLLOWER_AI_SUPPRESSION_TICKS = new HashMap<>();
    private static final Map<UUID, FollowPathState> FOLLOW_PATH_STATES = new HashMap<>();
    private static final Map<UUID, FollowFormationState> FOLLOW_FORMATION_STATES = new HashMap<>();
    private static final Map<RecruitmentDialogueKey, Long> LAST_LEFT_BEHIND_PROXIMITY_GAME_TIMES = new HashMap<>();

    private VillagerRecruitmentService() {
    }

    public static boolean canRecruit(ServerLevel level, Villager villager, ServerPlayer player) {
        return !villager.isBaby()
                && VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                >= VillagerReputationLevel.NEUTRAL.trustRank();
    }

    public static boolean canFollow(ServerLevel level, Villager villager, ServerPlayer player) {
        return VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                >= VillagerReputationLevel.NEUTRAL.trustRank();
    }

    public static boolean canCommandStayHere(ServerLevel level, Villager villager, ServerPlayer player) {
        return VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                >= VillagerReputationLevel.TRUSTED.trustRank();
    }

    public static boolean isFollowing(Villager villager, ServerPlayer player) {
        return villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)
                && villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY).equals(player.getUUID())
                && isFollowMode(villager);
    }

    public static boolean isStayingHere(Villager villager, ServerPlayer player) {
        return villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)
                && villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY).equals(player.getUUID())
                && isStayMode(villager);
    }

    public static boolean isFollowingAnyPlayer(Villager villager) {
        return villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY);
    }

    public static boolean isActivelyFollowingAnyPlayer(Villager villager) {
        return villager != null
                && villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)
                && isFollowMode(villager);
    }

    public static boolean isOrderedToStay(Villager villager) {
        return villager != null
                && villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)
                && isStayMode(villager);
    }

    public static Optional<UUID> followingPlayerId(Villager villager) {
        if (!villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)) {
            return Optional.empty();
        }
        return Optional.of(villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY));
    }

    public static boolean isHiredAnyPlayer(Villager villager) {
        return villager.level() instanceof ServerLevel level
                && HiredVillagerContractService.isHired(level, villager);
    }

    public static boolean startFollowing(ServerLevel level, Villager villager, ServerPlayer player) {
        if (!canTakeFollowCommand(villager, player)) {
            return false;
        }
        beginFollowing(level, villager, player);
        sendFollowingNotice(player, villager);
        return true;
    }

    public static boolean stayHere(ServerLevel level, Villager villager, ServerPlayer player) {
        if (!canCommandStayHere(level, villager, player) || !canTakeFollowCommand(villager, player)) {
            return false;
        }
        beginStayingHere(level, villager, player);
        sendStayingHereNotice(player, villager);
        return true;
    }

    public static void stopFollowing(Villager villager) {
        clearFollowTarget(villager);
    }

    public static void applyPartyFollowing(ServerLevel level, Villager villager, ServerPlayer leader) {
        applyPartyFollowing(level, villager, leader == null ? null : leader.getUUID());
    }

    public static void applyPartyFollowing(ServerLevel level, Villager villager, UUID leaderId) {
        if (level == null || villager == null || leaderId == null
                || !PartyVillagerContractService.hasPartyEntityReference(villager)) {
            return;
        }
        beginFollowing(level, villager, leaderId);
        villager.setPersistenceRequired();
    }

    public static void applyPartyStay(ServerLevel level, Villager villager, ServerPlayer leader) {
        applyPartyStay(level, villager, leader, villager == null ? null : villager.blockPosition());
    }

    public static void applyPartyStay(
            ServerLevel level,
            Villager villager,
            ServerPlayer leader,
            BlockPos anchor) {
        applyPartyStay(level, villager, leader == null ? null : leader.getUUID(), anchor);
    }

    public static void applyPartyStay(
            ServerLevel level,
            Villager villager,
            UUID leaderId,
            BlockPos anchor) {
        if (level == null || villager == null || leaderId == null || anchor == null
                || !PartyVillagerContractService.hasPartyEntityReference(villager)) {
            return;
        }
        beginStayingHere(villager, leaderId, anchor);
        villager.setPersistenceRequired();
    }

    public static void clearPartyFollowing(Villager villager) {
        if (PartyVillagerContractService.hasPartyEntityReference(villager)) {
            clearFollowTarget(villager);
            villager.setPersistenceRequired();
        }
    }

    public static boolean stopFollowing(ServerLevel level, Villager villager, ServerPlayer player) {
        if (!isFollowStateOwnedBy(villager, player)) {
            return false;
        }
        if (isFollowing(villager, player)) {
            String scenario = wasFollowerInjured(villager) ? "injured" : "safe";
            rememberRecruitmentMemory(level, villager, player, scenario);
            VillagerInteractionTracker.rememberRecruitmentFollowup(level, villager, player, scenario);
        }
        clearFollowTarget(villager);
        return true;
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
        if (attacker != null && PartyService.areInSameParty(villager, attacker)) {
            return;
        }
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
        Optional<UUID> hiredPlayerId = HiredVillagerContractService.getHirer(level, villager);
        if (hiredPlayerId.isPresent()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(hiredPlayerId.get());
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

    public static void sendMovingFreelyNotice(ServerPlayer player, Villager villager) {
        VillagerNotifications.sendHud(
                player,
                player.serverLevel(),
                villager,
                "recruitment.move_freely",
                replacements(villager),
                displayName(villager) + " can move freely again.",
                VillagerReputationNoticeKind.VILLAGER_DISMISSED
        );
    }

    public static void onVillagerTickPre(Villager villager) {
        if (villager.level().isClientSide || !isFollowingAnyPlayer(villager)) {
            return;
        }
        if (isHiredAnyPlayer(villager)) {
            clearFollowTarget(villager);
            return;
        }
        suppressFollowerAiIfNeeded(villager);
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)) {
            return;
        }
        if (HiredVillagerContractService.isHired(level, villager)) {
            clearFollowTarget(villager);
            return;
        }
        if (com.jvn.villagerretaliation.party.PartyQuickCommandService.overridesRecruitmentMovement(villager)) {
            return;
        }

        boolean partyVillager = PartyVillagerContractService.isActivePartyVillager(level, villager);
        UUID playerId = villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (isFollowMode(villager)) {
            if (partyVillager && player == null) {
                suppressFollowerAiIfNeeded(villager);
                stopFollowNavigation(villager);
                return;
            }
            if (!partyVillager) {
                updateTravelMemoryIfReady(level, villager);
            }
            if (!isValidFollowTarget(level, villager, player)) {
                if (partyVillager) {
                    stopFollowNavigation(villager);
                } else {
                    clearFollowTarget(villager);
                }
                return;
            }
            syncVehicleWithPlayer(villager, player);
            if (!partyVillager && isBeyondMaxFollowDistance(villager, player)) {
                stopFollowingBecauseLeftBehind(level, villager, player);
                sendNoLongerFollowingNotice(player, villager);
                return;
            }
        } else if (player != null) {
            dismountFollower(villager);
        }
        if (villager.isSleeping() || villager.isTrading() || villager.getTarget() != null || villager.getLastHurtByMob() != null) {
            suppressFollowerAiIfNeeded(villager);
            return;
        }

        if (isStayMode(villager)) {
            maintainStayHere(villager);
            return;
        }

        suppressFollowerAiIfNeeded(villager);
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (isRidingSameVehicle(villager, player)) {
            stopFollowNavigation(villager);
            return;
        }

        Entity pathAnchor = player.getVehicle() == null ? player : player.getVehicle();
        FollowTarget followTarget = followTarget(level, villager, player, pathAnchor);
        double distanceSqr = villager.distanceToSqr(followTarget.x(), followTarget.y(), followTarget.z());
        if (distanceSqr > FOLLOW_START_DISTANCE_SQR) {
            moveTowardFollowTarget(villager, followTarget, adaptiveFollowSpeed(distanceSqr));
        } else if (distanceSqr < FOLLOW_STOP_DISTANCE_SQR) {
            stopFollowNavigation(villager);
        }
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) {
            return;
        }
        long gameTime = level.getGameTime();
        UUID playerId = player.getUUID();
        if (!consumePlayerScanSlot(playerId, gameTime, NEXT_LEFT_BEHIND_PROXIMITY_SCAN_TICKS, LEFT_BEHIND_PROXIMITY_SCAN_INTERVAL_TICKS)) {
            return;
        }

        double maxDistance = VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();
        double maxDistanceSqr = maxDistance * maxDistance;
        Villager nearestVillager = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        VillagerInteractionTracker.RecruitmentFollowupReport nearestReport = null;

        for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(maxDistance))) {
            if (!villager.isAlive()) {
                continue;
            }
            Optional<VillagerInteractionTracker.RecruitmentFollowupReport> report =
                    VillagerInteractionTracker.unreportedRecruitmentFollowup(level, villager, player);
            if (report.isEmpty() || !LEFT_BEHIND_SCENARIO.equalsIgnoreCase(report.get().scenario())) {
                continue;
            }
            double distanceSqr = villager.distanceToSqr(player);
            if (distanceSqr > maxDistanceSqr) {
                continue;
            }
            RecruitmentDialogueKey key = new RecruitmentDialogueKey(villager.getUUID(), player.getUUID());
            if (LAST_LEFT_BEHIND_PROXIMITY_GAME_TIMES.getOrDefault(key, Long.MIN_VALUE) >= report.get().gameTime()) {
                continue;
            }
            if (distanceSqr < nearestDistanceSqr) {
                nearestDistanceSqr = distanceSqr;
                nearestVillager = villager;
                nearestReport = report.get();
            }
        }

        if (nearestVillager == null || nearestReport == null) {
            return;
        }

        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, nearestVillager);
        Optional<DialogueOptionDefinition> option = VillagerDialogueResources.dialogueOption(context, LEFT_BEHIND_OPTION_ID);
        if (option.isEmpty()) {
            return;
        }

        VillagerDialogueService.DialogueResult result = VillagerDialogueService.select(
                context,
                option.get(),
                VillagerInteractionTracker.getState(level, nearestVillager, player).recentDialogueIds());
        if (result.text().isBlank()) {
            return;
        }

        VillagerInteractionTracker.rememberDialogue(level, nearestVillager, player, option.get().requestType(), result.lineId());
        LAST_LEFT_BEHIND_PROXIMITY_GAME_TIMES.put(
                new RecruitmentDialogueKey(nearestVillager.getUUID(), player.getUUID()),
                nearestReport.gameTime());
        VillagerInteractionService.broadcastVillagerChat(level, nearestVillager, result.text());
    }

    public static void clearRuntimeState() {
        RECENT_BETRAYED_FOLLOWERS.clear();
        NEXT_FOLLOW_TRAVEL_MEMORY_TICKS.clear();
        NEXT_FOLLOW_REPUTATION_CHECK_TICKS.clear();
        NEXT_LEFT_BEHIND_PROXIMITY_SCAN_TICKS.clear();
        LAST_FOLLOWER_AI_SUPPRESSION_TICKS.clear();
        FOLLOW_PATH_STATES.clear();
        FOLLOW_FORMATION_STATES.clear();
        LAST_LEFT_BEHIND_PROXIMITY_GAME_TIMES.clear();
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

    private static void suppressFollowerAiIfNeeded(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            suppressFollowerAi(villager);
            return;
        }

        UUID villagerId = villager.getUUID();
        long gameTime = level.getGameTime();
        if (LAST_FOLLOWER_AI_SUPPRESSION_TICKS.getOrDefault(villagerId, Long.MIN_VALUE) == gameTime) {
            return;
        }

        LAST_FOLLOWER_AI_SUPPRESSION_TICKS.put(villagerId, gameTime);
        suppressFollowerAi(villager);
    }

    private static void suppressFollowerAi(Villager villager) {
        if (villager.level() instanceof ServerLevel level
                && PartyVillagerContractService.isActivePartyVillager(level, villager)
                && (villager.getTarget() != null || villager.getLastHurtByMob() != null)) {
            return;
        }
        Brain<Villager> brain = villager.getBrain();
        VillagerRetaliationVillagerBrainUtil.clearMovementMemories(villager);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.NEAREST_HOSTILE);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY_ENTITY);
        villager.setTarget(null);
        villager.setLastHurtByMob(null);
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
    }

    private static FollowTarget followTarget(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            Entity pathAnchor) {
        FollowFormationState state = FOLLOW_FORMATION_STATES.get(player.getUUID());
        long gameTime = level.getGameTime();
        if (state == null || state.gameTime() != gameTime) {
            List<Villager> followers = new ArrayList<>(level.getEntitiesOfClass(
                    Villager.class,
                    player.getBoundingBox().inflate(FOLLOW_FORMATION_SCAN_RADIUS),
                    candidate -> isFollowing(candidate, player)
            ));
            if (!followers.contains(villager)) {
                followers.add(villager);
            }
            followers.sort(Comparator.comparing(Villager::getUUID));

            Map<UUID, FollowFormationSlot> slots = new HashMap<>();
            for (int index = 0; index < followers.size(); index++) {
                slots.put(followers.get(index).getUUID(), formationSlot(index, followers.size()));
            }
            double forwardX;
            double forwardZ;
            double movementX = player.getDeltaMovement().x;
            double movementZ = player.getDeltaMovement().z;
            double movementLengthSqr = movementX * movementX + movementZ * movementZ;
            if (movementLengthSqr > 0.01D) {
                double movementLength = Math.sqrt(movementLengthSqr);
                forwardX = movementX / movementLength;
                forwardZ = movementZ / movementLength;
            } else if (state != null) {
                forwardX = state.forwardX();
                forwardZ = state.forwardZ();
            } else {
                double yawRadians = player.getYRot() * Mth.DEG_TO_RAD;
                forwardX = -Mth.sin((float) yawRadians);
                forwardZ = Mth.cos((float) yawRadians);
            }
            state = new FollowFormationState(gameTime, slots, forwardX, forwardZ);
            FOLLOW_FORMATION_STATES.put(player.getUUID(), state);
        }

        FollowFormationSlot slot = state.slots().getOrDefault(villager.getUUID(), formationSlot(0, 1));
        double forwardX = state.forwardX();
        double forwardZ = state.forwardZ();
        double rightX = forwardZ;
        double rightZ = -forwardX;
        double x = pathAnchor.getX() - forwardX * slot.back() + rightX * slot.lateral();
        double z = pathAnchor.getZ() - forwardZ * slot.back() + rightZ * slot.lateral();
        return new FollowTarget(pathAnchor, x, pathAnchor.getY(), z);
    }

    private static FollowFormationSlot formationSlot(int index, int followerCount) {
        int row = index / FOLLOW_FORMATION_COLUMNS;
        int rowStart = row * FOLLOW_FORMATION_COLUMNS;
        int rowSize = Math.min(FOLLOW_FORMATION_COLUMNS, Math.max(1, followerCount - rowStart));
        int column = index - rowStart;
        double lateral = (column - (rowSize - 1) * 0.5D) * FOLLOW_FORMATION_LATERAL_SPACING;
        double back = FOLLOW_FORMATION_BACK_DISTANCE + row * FOLLOW_FORMATION_ROW_SPACING;
        return new FollowFormationSlot(lateral, back);
    }

    private static boolean moveTowardFollowTarget(Villager villager, FollowTarget followTarget, double speed) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }

        UUID villagerId = villager.getUUID();
        long gameTime = level.getGameTime();
        FollowPathState state = FOLLOW_PATH_STATES.get(villagerId);
        boolean targetChanged = state == null || !state.targetId().equals(followTarget.pathAnchor().getUUID());
        boolean targetMoved = state == null
                || distanceToSqr(followTarget, state.targetX(), state.targetY(), state.targetZ()) >= FOLLOW_TARGET_MOVED_DISTANCE_SQR;
        boolean shouldRecalculate = targetChanged
                || targetMoved
                || villager.getNavigation().isDone()
                || gameTime >= state.nextRecalculationGameTime()
                || villager.getRandom().nextFloat() < 0.05F;

        if (!shouldRecalculate) {
            return true;
        }

        Brain<Villager> brain = villager.getBrain();
        VillagerRetaliationVillagerBrainUtil.clearPathingMemories(villager);

        int failedPathFindingPenalty = targetChanged || state == null ? 0 : state.failedPathFindingPenalty();
        long recalculationDelay = FOLLOW_PATH_RECALCULATION_MIN_TICKS
                + villager.getRandom().nextInt(FOLLOW_PATH_RECALCULATION_RANDOM_TICKS);
        double distanceSqr = villager.distanceToSqr(followTarget.x(), followTarget.y(), followTarget.z());
        if (distanceSqr > 1024.0D) {
            recalculationDelay += 10L;
        } else if (distanceSqr > 256.0D) {
            recalculationDelay += 5L;
        }

        boolean moved = villager.getNavigation().moveTo(followTarget.x(), followTarget.y(), followTarget.z(), speed);
        if (!moved) {
            moved = villager.getNavigation().moveTo(followTarget.pathAnchor(), speed);
        }
        if (moved) {
            failedPathFindingPenalty = 0;
        } else {
            failedPathFindingPenalty += 15;
            recalculationDelay += failedPathFindingPenalty;
        }

        FOLLOW_PATH_STATES.put(villagerId, new FollowPathState(
                followTarget.pathAnchor().getUUID(),
                followTarget.x(),
                followTarget.y(),
                followTarget.z(),
                gameTime + recalculationDelay,
                failedPathFindingPenalty
        ));
        return moved;
    }

    private static double distanceToSqr(FollowTarget target, double x, double y, double z) {
        double deltaX = target.x() - x;
        double deltaY = target.y() - y;
        double deltaZ = target.z() - z;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private static double adaptiveFollowSpeed(double distanceSqr) {
        double distance = Math.sqrt(Math.max(0.0D, distanceSqr));
        double distancePastComfort = Math.max(0.0D, distance - Math.sqrt(FOLLOW_STOP_DISTANCE_SQR));
        double multiplier = Mth.clamp(distancePastComfort * 0.12D, 0.85D, 1.45D);
        return FOLLOW_SPEED * multiplier;
    }

    private static void stopFollowNavigation(Villager villager) {
        FOLLOW_PATH_STATES.remove(villager.getUUID());
        VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
    }

    private static boolean consumePlayerScanSlot(
            UUID playerId,
            long gameTime,
            Map<UUID, Long> nextScanTicks,
            long intervalTicks) {
        return TickThrottle.consume(playerId, nextScanTicks, gameTime, intervalTicks);
    }

    private static boolean isValidFollowTarget(ServerLevel level, Villager villager, ServerPlayer player) {
        if (PartyVillagerContractService.isActivePartyVillager(level, villager)) {
            return player != null
                    && player.isAlive()
                    && !player.isSpectator()
                    && villager.isAlive()
                    && PartyVillagerContractService.leaderId(level, villager)
                    .filter(player.getUUID()::equals)
                    .isPresent();
        }
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
        UUID villagerId = villager.getUUID();
        if (villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)) {
            FOLLOW_FORMATION_STATES.remove(villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY));
        }
        NEXT_FOLLOW_TRAVEL_MEMORY_TICKS.remove(villagerId);
        NEXT_FOLLOW_REPUTATION_CHECK_TICKS.remove(villagerId);
        LAST_FOLLOWER_AI_SUPPRESSION_TICKS.remove(villagerId);
        FOLLOW_PATH_STATES.remove(villagerId);
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
        villager.getPersistentData().remove(FOLLOW_MODE_KEY);
        villager.getPersistentData().remove(STAY_ANCHOR_X_KEY);
        villager.getPersistentData().remove(STAY_ANCHOR_Y_KEY);
        villager.getPersistentData().remove(STAY_ANCHOR_Z_KEY);
        VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
    }

    private static void dismountFollower(Villager villager) {
        if (villager.isPassenger()) {
            villager.stopRiding();
        }
    }

    private static void beginFollowing(ServerLevel level, Villager villager, ServerPlayer player) {
        beginFollowing(level, villager, player.getUUID());
    }

    private static void beginFollowing(ServerLevel level, Villager villager, UUID playerId) {
        BlockPos start = villager.blockPosition();
        FOLLOW_FORMATION_STATES.remove(playerId);
        villager.getPersistentData().putUUID(FOLLOWING_PLAYER_KEY, playerId);
        villager.getPersistentData().putString(FOLLOW_MODE_KEY, FOLLOW_MODE_FOLLOW);
        villager.getPersistentData().putFloat(FOLLOW_START_HEALTH_KEY, villager.getHealth());
        villager.getPersistentData().putFloat(FOLLOW_MIN_HEALTH_KEY, villager.getHealth());
        villager.getPersistentData().putInt(FOLLOW_START_X_KEY, start.getX());
        villager.getPersistentData().putInt(FOLLOW_START_Y_KEY, start.getY());
        villager.getPersistentData().putInt(FOLLOW_START_Z_KEY, start.getZ());
        villager.getPersistentData().putString(FOLLOW_START_BIOME_KEY, biomeName(level, start));
        villager.getPersistentData().putInt(FOLLOW_MAX_DISTANCE_KEY, 0);
        villager.getPersistentData().putBoolean(FOLLOW_USED_BOAT_KEY, false);
        villager.getPersistentData().putBoolean(FOLLOW_CROSSED_OCEAN_KEY, isOceanBiome(level, start));
        villager.getPersistentData().remove(STAY_ANCHOR_X_KEY);
        villager.getPersistentData().remove(STAY_ANCHOR_Y_KEY);
        villager.getPersistentData().remove(STAY_ANCHOR_Z_KEY);
    }

    private static void beginStayingHere(ServerLevel level, Villager villager, ServerPlayer player) {
        beginStayingHere(level, villager, player, villager.blockPosition());
    }

    private static void beginStayingHere(ServerLevel level, Villager villager, ServerPlayer player, BlockPos anchor) {
        beginStayingHere(villager, player.getUUID(), anchor);
    }

    private static void beginStayingHere(Villager villager, UUID playerId, BlockPos anchor) {
        FOLLOW_FORMATION_STATES.remove(playerId);
        villager.getPersistentData().putUUID(FOLLOWING_PLAYER_KEY, playerId);
        villager.getPersistentData().putString(FOLLOW_MODE_KEY, FOLLOW_MODE_STAY);
        villager.getPersistentData().putInt(STAY_ANCHOR_X_KEY, anchor.getX());
        villager.getPersistentData().putInt(STAY_ANCHOR_Y_KEY, anchor.getY());
        villager.getPersistentData().putInt(STAY_ANCHOR_Z_KEY, anchor.getZ());
        stopFollowNavigation(villager);
    }

    private static void stopFollowingBecauseLeftBehind(ServerLevel level, Villager villager, ServerPlayer player) {
        if (!isFollowing(villager, player)) {
            clearFollowTarget(villager);
            return;
        }
        rememberRecruitmentMemory(level, villager, player, LEFT_BEHIND_SCENARIO);
        VillagerInteractionTracker.rememberRecruitmentFollowup(level, villager, player, LEFT_BEHIND_SCENARIO);
        clearFollowTarget(villager);
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

    private static boolean isFollowMode(Villager villager) {
        return FOLLOW_MODE_FOLLOW.equals(villager.getPersistentData().getString(FOLLOW_MODE_KEY))
                || !villager.getPersistentData().contains(FOLLOW_MODE_KEY);
    }

    private static boolean isStayMode(Villager villager) {
        return FOLLOW_MODE_STAY.equals(villager.getPersistentData().getString(FOLLOW_MODE_KEY));
    }

    private static void maintainStayHere(Villager villager) {
        suppressFollowerAiIfNeeded(villager);
        BlockPos anchor = stayAnchor(villager);
        if (anchor == null) {
            return;
        }
        double distanceSqr = villager.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        if (distanceSqr > STAY_RETURN_START_DISTANCE_SQR) {
            villager.getLookControl().setLookAt(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 20.0F, 20.0F);
            villager.getNavigation().moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, STAY_HERE_SPEED);
        } else if (distanceSqr < STAY_RETURN_STOP_DISTANCE_SQR) {
            stopFollowNavigation(villager);
        }
    }

    private static BlockPos stayAnchor(Villager villager) {
        if (!villager.getPersistentData().contains(STAY_ANCHOR_X_KEY)
                || !villager.getPersistentData().contains(STAY_ANCHOR_Y_KEY)
                || !villager.getPersistentData().contains(STAY_ANCHOR_Z_KEY)) {
            return null;
        }
        return new BlockPos(
                villager.getPersistentData().getInt(STAY_ANCHOR_X_KEY),
                villager.getPersistentData().getInt(STAY_ANCHOR_Y_KEY),
                villager.getPersistentData().getInt(STAY_ANCHOR_Z_KEY)
        );
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

    private static boolean canTakeFollowCommand(Villager villager, ServerPlayer player) {
        return !isHiredAnyPlayer(villager)
                && !PartyVillagerContractService.hasPartyEntityReference(villager)
                && followingPlayerId(villager).map(player.getUUID()::equals).orElse(true);
    }

    private static boolean isFollowStateOwnedBy(Villager villager, ServerPlayer player) {
        return followingPlayerId(villager).filter(player.getUUID()::equals).isPresent();
    }

    private static void sendStayingHereNotice(ServerPlayer player, Villager villager) {
        VillagerNotifications.sendHud(
                player,
                player.serverLevel(),
                villager,
                "recruitment.stay_here",
                replacements(villager),
                displayName(villager) + " will stay here.",
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

    private record FollowPathState(
            UUID targetId,
            double targetX,
            double targetY,
            double targetZ,
            long nextRecalculationGameTime,
            int failedPathFindingPenalty
    ) {
    }

    private record FollowTarget(Entity pathAnchor, double x, double y, double z) {
    }

    private record FollowFormationSlot(double lateral, double back) {
    }

    private record FollowFormationState(
            long gameTime,
            Map<UUID, FollowFormationSlot> slots,
            double forwardX,
            double forwardZ
    ) {
    }

    private record RecruitmentDialogueKey(UUID villagerId, UUID playerId) {
    }
}
