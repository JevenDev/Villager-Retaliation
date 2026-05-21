package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerRecruitmentService {
    private static final String FOLLOWING_PLAYER_KEY = "VillagerRetaliationFollowingPlayer";
    private static final String HIRED_PLAYER_KEY = "VillagerRetaliationHiredPlayer";
    private static final double FOLLOW_START_DISTANCE_SQR = 5.0D * 5.0D;
    private static final double FOLLOW_STOP_DISTANCE_SQR = 2.5D * 2.5D;
    private static final double FOLLOW_FORGET_DISTANCE_SQR = 96.0D * 96.0D;
    private static final double FOLLOW_SPEED = 0.62D;
    private static final long RECENT_BETRAYED_FOLLOWER_DEATH_NOTICE_TICKS = 200L;
    private static final Map<UUID, RecentRecruitmentOwner> RECENT_BETRAYED_FOLLOWERS = new HashMap<>();

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
            stopFollowing(villager);
            sendNoLongerFollowingNotice(player, villager);
            return false;
        }
        villager.getPersistentData().putUUID(FOLLOWING_PLAYER_KEY, player.getUUID());
        sendFollowingNotice(player, villager);
        return true;
    }

    public static void stopFollowing(Villager villager) {
        clearFollowTarget(villager);
    }

    public static void stopFollowingIfFollowingAttacker(Villager villager, Player attacker) {
        if (attacker != null
                && villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)
                && villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY).equals(attacker.getUUID())) {
            rememberBetrayedFollower(villager, attacker);
            clearFollowTarget(villager);
            if (attacker instanceof ServerPlayer serverPlayer) {
                sendNoLongerFollowingNotice(serverPlayer, villager);
                sendFollowerBetrayalDialogue(villager, serverPlayer);
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
        if (!isValidFollowTarget(level, villager, player)) {
            clearFollowTarget(villager);
            return;
        }
        if (villager.isSleeping() || villager.isTrading() || villager.getTarget() != null || villager.getLastHurtByMob() != null) {
            suppressFollowerAi(villager);
            return;
        }

        suppressFollowerAi(villager);
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
        double distanceSqr = villager.distanceToSqr(player);
        if (distanceSqr > FOLLOW_START_DISTANCE_SQR) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.PATH);
            villager.getNavigation().moveTo(player, FOLLOW_SPEED);
        } else if (distanceSqr < FOLLOW_STOP_DISTANCE_SQR) {
            villager.getNavigation().stop();
        }
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
                && villager.distanceToSqr(player) <= FOLLOW_FORGET_DISTANCE_SQR
                && VillagerReputationManager.getReputationLevel(level, villager, player.getUUID()).trustRank()
                >= VillagerReputationLevel.NEUTRAL.trustRank();
    }

    private static void clearFollowTarget(Villager villager) {
        villager.getPersistentData().remove(FOLLOWING_PLAYER_KEY);
        villager.getNavigation().stop();
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
                        villagerSpeakerLabel(villager)
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

    private static String villagerSpeakerLabel(Villager villager) {
        String resolvedName = displayName(villager);
        String profession = VillagerInteractionTextUtil.professionName(villager.getVillagerData().getProfession(), "Unemployed");
        if (profession == null || profession.isBlank() || profession.equals("Villager")) {
            return resolvedName;
        }
        return profession + " " + resolvedName;
    }

    private static String displayName(Villager villager) {
        return VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
    }

    private record RecentRecruitmentOwner(UUID playerId, long expiresGameTime) {
    }
}
