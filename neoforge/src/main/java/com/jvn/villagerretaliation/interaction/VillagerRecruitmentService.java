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
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerRecruitmentService {
    private static final long LEFT_BEHIND_PROXIMITY_SCAN_INTERVAL_TICKS = 20L;
    private static final long RECENT_BETRAYED_FOLLOWER_DEATH_NOTICE_TICKS = 200L;
    private static final String LEFT_BEHIND_SCENARIO = "left_behind";
    private static final String LEFT_BEHIND_OPTION_ID = "recruitment_left_behind";
    private static final Map<UUID, RecentRecruitmentOwner> RECENT_BETRAYED_FOLLOWERS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_LEFT_BEHIND_PROXIMITY_SCAN_TICKS = new HashMap<>();
    private static final Map<RecruitmentDialogueKey, Long> LAST_LEFT_BEHIND_PROXIMITY_GAME_TIMES = new HashMap<>();

    private VillagerRecruitmentService() {
    }

    public static boolean canRecruit(ServerLevel level, Villager villager, ServerPlayer player) {
        return RecruitmentPolicy.mayHire(level, villager, player).allowed();
    }

    public static boolean canFollow(ServerLevel level, Villager villager, ServerPlayer player) {
        return RecruitmentPolicy.mayCommand(level, villager, player, VillagerAssignmentCommand.FOLLOW).allowed();
    }

    public static boolean canCommandStayHere(ServerLevel level, Villager villager, ServerPlayer player) {
        return RecruitmentPolicy.mayCommand(level, villager, player, VillagerAssignmentCommand.STAY).allowed();
    }

    public static boolean isFollowing(Villager villager, ServerPlayer player) {
        return player != null
                && VillagerAssignmentStore.commandOwner(villager).filter(player.getUUID()::equals).isPresent()
                && VillagerAssignmentStore.isFollowing(villager);
    }

    public static boolean isStayingHere(Villager villager, ServerPlayer player) {
        return player != null
                && VillagerAssignmentStore.commandOwner(villager).filter(player.getUUID()::equals).isPresent()
                && VillagerAssignmentStore.isStaying(villager);
    }

    public static boolean isFollowingAnyPlayer(Villager villager) {
        return VillagerAssignmentStore.commandOwner(villager).isPresent();
    }

    public static boolean isActivelyFollowingAnyPlayer(Villager villager) {
        return villager != null && VillagerAssignmentStore.isFollowing(villager);
    }

    public static boolean isOrderedToStay(Villager villager) {
        return villager != null && VillagerAssignmentStore.isStaying(villager);
    }

    public static Optional<UUID> followingPlayerId(Villager villager) {
        return VillagerAssignmentStore.commandOwner(villager);
    }

    public static boolean isHiredAnyPlayer(Villager villager) {
        return villager.level() instanceof ServerLevel level
                && HiredVillagerContractService.isHired(level, villager);
    }

    public static boolean startFollowing(ServerLevel level, Villager villager, ServerPlayer player) {
        if (!RecruitmentPolicy.mayCommand(level, villager, player, VillagerAssignmentCommand.FOLLOW).allowed()) {
            return false;
        }
        VillagerCommandController.beginFollow(level, villager, player.getUUID());
        sendFollowingNotice(player, villager);
        return true;
    }

    public static boolean stayHere(ServerLevel level, Villager villager, ServerPlayer player) {
        if (!RecruitmentPolicy.mayCommand(level, villager, player, VillagerAssignmentCommand.STAY).allowed()) {
            return false;
        }
        VillagerCommandController.beginStay(level, villager, player.getUUID(), villager.blockPosition());
        sendStayingHereNotice(player, villager);
        return true;
    }

    public static void stopFollowing(Villager villager) {
        VillagerCommandController.clear(villager);
    }

    public static void clearInheritedStateForNewborn(Villager child) {
        if (child != null) {
            VillagerCommandController.clear(child);
            VillagerAssignmentStore.clearInheritedStateForNewborn(child);
        }
    }

    public static void applyPartyFollowing(ServerLevel level, Villager villager, ServerPlayer leader) {
        applyPartyFollowing(level, villager, leader == null ? null : leader.getUUID());
    }

    public static void applyPartyFollowing(ServerLevel level, Villager villager, UUID leaderId) {
        if (level == null || villager == null || leaderId == null
                || !PartyVillagerContractService.hasPartyEntityReference(villager)) {
            return;
        }
        VillagerCommandController.beginFollow(level, villager, leaderId);
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
        VillagerCommandController.beginStay(level, villager, leaderId, anchor);
        villager.setPersistenceRequired();
    }

    public static void clearPartyFollowing(Villager villager) {
        if (PartyVillagerContractService.hasPartyEntityReference(villager)) {
            VillagerCommandController.clear(villager);
            villager.setPersistenceRequired();
        }
    }

    public static boolean stopFollowing(ServerLevel level, Villager villager, ServerPlayer player) {
        if (VillagerAssignmentStore.commandOwner(villager).filter(player.getUUID()::equals).isEmpty()) {
            return false;
        }
        if (isFollowing(villager, player)) {
            String scenario = VillagerAssignmentStore.wasInjured(villager) ? "injured" : "safe";
            rememberRecruitmentMemory(level, villager, player, scenario);
            VillagerInteractionTracker.rememberRecruitmentFollowup(level, villager, player, scenario);
        }
        VillagerCommandController.clear(villager);
        return true;
    }

    public static void rememberFollowerDamage(Villager villager) {
        VillagerAssignmentStore.rememberDamage(villager);
    }

    public static void stopFollowingIfFollowingAttacker(Villager villager, Player attacker) {
        if (attacker != null && PartyService.areInSameParty(villager, attacker)) {
            return;
        }
        if (attacker != null
                && VillagerAssignmentStore.commandOwner(villager).filter(attacker.getUUID()::equals).isPresent()) {
            rememberBetrayedFollower(villager, attacker);
            if (attacker instanceof ServerPlayer serverPlayer) {
                rememberRecruitmentMemory(serverPlayer.serverLevel(), villager, serverPlayer, "betrayed");
                VillagerInteractionTracker.rememberRecruitmentFollowup(serverPlayer.serverLevel(), villager, serverPlayer, "betrayed");
                VillagerCommandController.clear(villager);
                sendNoLongerFollowingNotice(serverPlayer, villager);
                sendFollowerBetrayalDialogue(villager, serverPlayer);
            } else {
                VillagerCommandController.clear(villager);
            }
        }
    }

    public static void notifyRecruitmentDeath(Villager villager, Entity killer) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        boolean sentNotice = false;
        UUID commandOwner = VillagerAssignmentStore.commandOwner(villager).orElse(null);
        if (commandOwner != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(commandOwner);
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
        if (villager == null) return;
        VillagerCommandController.onVillagerTickPre(villager);
    }

    public static void onVillagerTickPost(Villager villager) {
        if (villager == null) return;
        VillagerCommandController.TickResult result = VillagerCommandController.onVillagerTickPost(villager);
        if (result == VillagerCommandController.TickResult.LEFT_BEHIND
                && villager.level() instanceof ServerLevel commandLevel) {
            ServerPlayer owner = VillagerAssignmentStore.commandOwner(villager)
                    .map(id -> commandLevel.getServer().getPlayerList().getPlayer(id))
                    .orElse(null);
            if (owner != null) {
                rememberRecruitmentMemory(commandLevel, villager, owner, LEFT_BEHIND_SCENARIO);
                VillagerInteractionTracker.rememberRecruitmentFollowup(
                        commandLevel, villager, owner, LEFT_BEHIND_SCENARIO);
                VillagerCommandController.clear(villager);
                sendNoLongerFollowingNotice(owner, villager);
            } else {
                VillagerCommandController.clear(villager);
            }
        } else if (result == VillagerCommandController.TickResult.OWNER_LOST
                || result == VillagerCommandController.TickResult.OWNER_CHANGED_DIMENSION) {
            VillagerCommandController.clear(villager);
        }
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) {
            return;
        }
        long gameTime = level.getGameTime();
        UUID playerId = player.getUUID();
        if (!TickThrottle.consume(playerId, NEXT_LEFT_BEHIND_PROXIMITY_SCAN_TICKS, gameTime, LEFT_BEHIND_PROXIMITY_SCAN_INTERVAL_TICKS)) {
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

    private static void rememberRecruitmentMemory(ServerLevel level, Villager villager, ServerPlayer player, String scenario) {
        VillagerAssignmentStore.JourneySnapshot journey = VillagerAssignmentStore.journey(villager);
        VillagerInteractionTracker.rememberRecruitmentMemory(
                level,
                villager,
                player,
                scenario,
                journey.startBiome(),
                journey.distanceBlocks(),
                journey.usedBoat(),
                journey.crossedOcean()
        );
    }
    public static void clearRuntimeState() {
        VillagerCommandController.clearRuntimeState();
        RECENT_BETRAYED_FOLLOWERS.clear();
        NEXT_LEFT_BEHIND_PROXIMITY_SCAN_TICKS.clear();
        LAST_LEFT_BEHIND_PROXIMITY_GAME_TIMES.clear();
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


    private record RecruitmentDialogueKey(UUID villagerId, UUID playerId) {
    }
}
