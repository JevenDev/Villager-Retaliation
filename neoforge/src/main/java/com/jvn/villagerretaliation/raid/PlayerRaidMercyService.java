package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/** Server-authoritative mercy-stage pleas and verdict conversations. */
public final class PlayerRaidMercyService {
    public static final String SPARE_OPTION_ID = "player_raid_mercy_spare";
    public static final String KILL_OPTION_ID = "player_raid_mercy_kill";
    public static final String SILENCE_OPTION_ID = "player_raid_mercy_silence";

    private static final long MIN_PLEA_COOLDOWN_TICKS = 20L * 30L;
    private static final long PLEA_COOLDOWN_VARIANCE_TICKS = 20L * 30L;
    private static final long RAID_PLEA_GAP_TICKS = 20L * 5L;
    private static final int SPARED_REPUTATION = -1_000;
    private static final Map<UUID, MercySession> SESSIONS = new HashMap<>();

    private static final List<String> BABY_PLEAS = List.of(
            "Please don't hurt me.",
            "I didn't fight you. Please let me live.",
            "Please... I just want to go home.",
            "The fighting is over. Please spare me.",
            "I don't want to die here."
    );
    private static final List<String> NITWIT_PLEAS = List.of(
            "I never raised a weapon against you. Please spare me.",
            "The defenders are gone. You have won. Let me live.",
            "I cannot stop you. I can only ask for mercy.",
            "Please. There has been enough killing.",
            "Leave me my life, and I will remember what happened here."
    );
    private static final List<String> BABY_SPARED = List.of(
            "I won't forget this.",
            "Thank you... but I will always remember today.",
            "You let me live. I can never forgive what I saw."
    );
    private static final List<String> NITWIT_SPARED = List.of(
            "You spared me, but you destroyed everything I knew.",
            "I will live. I will also remember.",
            "Mercy does not erase what happened here."
    );
    private static final List<String> BABY_KILL = List.of(
            "Then... do it yourself.",
            "Please change your mind.",
            "I don't want to die."
    );
    private static final List<String> NITWIT_KILL = List.of(
            "Then finish your own decision.",
            "If that is your answer, I have nothing left to say.",
            "You have already won. This is your choice now."
    );

    private PlayerRaidMercyService() {
    }

    static void initialize(
            MinecraftServer server,
            PlayerRaidSavedData data,
            PlayerRaidSavedData.RaidRecord raid,
            long now) {
        boolean changed = false;
        for (UUID candidateId : raid.mercyCandidates()) {
            if (raid.nextMercyPleaAt(candidateId) > 0L) continue;
            raid.setNextMercyPleaAt(candidateId, nextPleaTime(server, now));
            changed = true;
        }
        if (raid.nextRaidMercyPleaAt() <= 0L) {
            raid.setNextRaidMercyPleaAt(now);
            changed = true;
        }
        if (changed) data.changed();
    }

    static void tick(
            MinecraftServer server,
            PlayerRaidSavedData data,
            PlayerRaidSavedData.RaidRecord raid,
            long now) {
        if (now < raid.nextRaidMercyPleaAt()) return;
        List<UUID> candidates = new ArrayList<>(raid.mercyCandidates());
        candidates.sort(Comparator.naturalOrder());
        for (UUID candidateId : candidates) {
            if (now < raid.nextMercyPleaAt(candidateId)) continue;
            Entity entity = find(server, candidateId);
            if (!(entity instanceof Villager villager) || !villager.isAlive()) continue;
            ServerPlayer listener = nearestApproachingRaider(server, raid, villager);
            if (listener == null) continue;
            sendPlea(listener, villager, raid.mercyKind(candidateId));
            scheduleNextPlea(server, data, raid, candidateId, now);
            return;
        }
    }

    public static boolean shouldHandleInteraction(
            Villager villager,
            ServerPlayer player,
            InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND
                || !VillagerInteractionService.canStartVillagerInteractionWithHeldItems(player)
                || villager == null
                || !villager.isAlive()
                || VillagerDownedService.isDowned(villager)
                || !player.isAlive()
                || player.isSpectator()) {
            return false;
        }
        PlayerRaidSavedData.RaidRecord raid = PlayerRaidSavedData.get(player.serverLevel())
                .activeForParticipant(player.getUUID());
        if (!isAuthorizedTarget(player, villager, raid)) return false;
        return withinDialogueDistance(player, villager);
    }

    public static InteractionResult openVerdict(ServerPlayer player, Villager villager) {
        PlayerRaidSavedData data = PlayerRaidSavedData.get(player.serverLevel());
        PlayerRaidSavedData.RaidRecord raid = data.activeForParticipant(player.getUUID());
        if (!isAuthorizedTarget(player, villager, raid) || !withinDialogueDistance(player, villager)) {
            return InteractionResult.FAIL;
        }
        if (villager.isSleeping()) villager.stopSleeping();
        SESSIONS.remove(player.getUUID());
        if (!VillagerConversationService.startForcedIgnoringDisposition(player, villager)) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.busy");
            return InteractionResult.FAIL;
        }
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        PlayerRaidSavedData.MercyKind kind = raid.mercyKind(villager.getUUID());
        VillagerInteractionScreenOpener.openForced(player, villager, verdictOptions(player), true);
        VillagerInteractionService.focusVillagerOnPlayer(villager, player);
        SESSIONS.put(player.getUUID(), new MercySession(raid.id(), villager.getUUID(), villager.getId()));
        long now = player.getServer().overworld().getGameTime();
        if (now >= raid.nextRaidMercyPleaAt() && now >= raid.nextMercyPleaAt(villager.getUUID())) {
            sendPlea(player, villager, kind);
            scheduleNextPlea(player.getServer(), data, raid, villager.getUUID(), now);
        }
        return InteractionResult.CONSUME;
    }

    public static boolean handleDialogueRequest(ServerPlayer player, int entityId, String optionId) {
        if (!isMercyOption(optionId)) return false;
        MercySession session = SESSIONS.get(player.getUUID());
        if (session == null || session.entityId() != entityId) return true;
        Villager villager = resolveValidSession(player, session);
        if (villager == null) {
            end(player);
            return true;
        }
        PlayerRaidSavedData data = PlayerRaidSavedData.get(player.serverLevel());
        PlayerRaidSavedData.RaidRecord raid = data.raid(session.raidId());
        PlayerRaidSavedData.MercyKind kind = raid.mercyKind(villager.getUUID());
        if (SPARE_OPTION_ID.equals(optionId)) {
            String response = message(player, villager, responseKey(kind, true), responseFallback(kind, true));
            for (UUID raiderId : raid.raiderPlayers()) {
                VillagerReputationManager.setReputation(player.serverLevel(), villager, raiderId, SPARED_REPUTATION);
            }
            if (raid.removeMercyCandidate(villager.getUUID())) data.changed();
            VillagerReputationAdvancements.onVillagerSparedDuringRaid(player);
            PlayerRaidService.releaseMercyCandidate(player.getServer(), villager);
            end(player);
            VillagerInteractionService.sendPersonalVillagerChat(player, villager, response);
            PlayerRaidService.completeMercyIfResolved(player.getServer(), raid.id());
            return true;
        }
        if (KILL_OPTION_ID.equals(optionId)) {
            String response = message(player, villager, responseKey(kind, false), responseFallback(kind, false));
            end(player);
            VillagerInteractionService.sendPersonalVillagerChat(player, villager, response);
            return true;
        }
        end(player);
        return true;
    }

    public static boolean handleConversationEndRequest(ServerPlayer player, int entityId) {
        MercySession session = SESSIONS.get(player.getUUID());
        if (session == null || session.entityId() != entityId) return false;
        end(player);
        return true;
    }

    static void reconcile(MinecraftServer server) {
        for (Map.Entry<UUID, MercySession> entry : new ArrayList<>(SESSIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                SESSIONS.remove(entry.getKey(), entry.getValue());
                continue;
            }
            if (resolveValidSession(player, entry.getValue()) == null) end(player);
        }
    }

    static void onPlayerLoggedOut(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    static void onRaidFinished(MinecraftServer server, UUID raidId) {
        for (Map.Entry<UUID, MercySession> entry : new ArrayList<>(SESSIONS.entrySet())) {
            if (!entry.getValue().raidId().equals(raidId)) continue;
            SESSIONS.remove(entry.getKey(), entry.getValue());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) VillagerConversationService.endForPlayer(player, true);
        }
    }

    static void clearRuntimeState() {
        SESSIONS.clear();
    }

    private static Villager resolveValidSession(ServerPlayer player, MercySession session) {
        Entity entity = player.serverLevel().getEntity(session.villagerId());
        if (!(entity instanceof Villager villager)
                || villager.getId() != session.entityId()
                || !VillagerConversationService.isForced(player, villager)
                || !VillagerConversationService.validate(player, villager)
                || !withinDialogueDistance(player, villager)) {
            return null;
        }
        PlayerRaidSavedData.RaidRecord raid = PlayerRaidSavedData.get(player.serverLevel()).raid(session.raidId());
        return isAuthorizedTarget(player, villager, raid) ? villager : null;
    }

    private static boolean isAuthorizedTarget(
            ServerPlayer player,
            Villager villager,
            PlayerRaidSavedData.RaidRecord raid) {
        return raid != null
                && raid.phase() == PlayerRaidSavedData.Phase.MERCY
                && raid.raiderPlayers().contains(player.getUUID())
                && raid.mercyCandidates().contains(villager.getUUID())
                && raid.dimension().equals(player.serverLevel().dimension().location())
                && villager.level() == player.serverLevel();
    }

    private static boolean withinDialogueDistance(ServerPlayer player, Villager villager) {
        double distance = VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();
        return player.distanceToSqr(villager) <= distance * distance;
    }

    private static ServerPlayer nearestApproachingRaider(
            MinecraftServer server,
            PlayerRaidSavedData.RaidRecord raid,
            Villager villager) {
        double maxDistance = VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();
        double maxDistanceSqr = maxDistance * maxDistance;
        return raid.raiderPlayers().stream()
                .map(id -> server.getPlayerList().getPlayer(id))
                .filter(player -> player != null
                        && player.isAlive()
                        && !player.isSpectator()
                        && player.serverLevel() == villager.level()
                        && player.distanceToSqr(villager) <= maxDistanceSqr)
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(villager)))
                .orElse(null);
    }

    private static void scheduleNextPlea(
            MinecraftServer server,
            PlayerRaidSavedData data,
            PlayerRaidSavedData.RaidRecord raid,
            UUID candidateId,
            long now) {
        raid.setNextMercyPleaAt(candidateId, nextPleaTime(server, now));
        raid.setNextRaidMercyPleaAt(now + RAID_PLEA_GAP_TICKS);
        data.changed();
    }

    private static long nextPleaTime(MinecraftServer server, long now) {
        return now + MIN_PLEA_COOLDOWN_TICKS
                + server.overworld().getRandom().nextInt((int) PLEA_COOLDOWN_VARIANCE_TICKS + 1);
    }

    private static void sendPlea(
            ServerPlayer player,
            Villager villager,
            PlayerRaidSavedData.MercyKind kind) {
        String key = kind == PlayerRaidSavedData.MercyKind.BABY
                ? "interaction.player_raid.mercy.plea.baby"
                : "interaction.player_raid.mercy.plea.nitwit";
        List<String> fallback = kind == PlayerRaidSavedData.MercyKind.BABY ? BABY_PLEAS : NITWIT_PLEAS;
        VillagerInteractionService.sendPersonalVillagerChat(player, villager, message(player, villager, key, fallback));
    }

    private static List<DialogueOptionDefinition> verdictOptions(ServerPlayer player) {
        return List.of(
                DialogueOptionDefinition.simple(
                        SPARE_OPTION_ID,
                        globalText(player, "interaction.player_raid.mercy.option.spare", "Spare"),
                        DialogueRequestType.QUESTION,
                        0),
                DialogueOptionDefinition.simple(
                        KILL_OPTION_ID,
                        globalText(player, "interaction.player_raid.mercy.option.kill", "Kill"),
                        DialogueRequestType.QUESTION,
                        1),
                DialogueOptionDefinition.simple(
                        SILENCE_OPTION_ID,
                        globalText(player, "interaction.player_raid.mercy.option.silence", "Say nothing"),
                        DialogueRequestType.QUESTION,
                        2));
    }

    private static String responseKey(PlayerRaidSavedData.MercyKind kind, boolean spared) {
        return "interaction.player_raid.mercy."
                + (spared ? "spared." : "kill.")
                + (kind == PlayerRaidSavedData.MercyKind.BABY ? "baby" : "nitwit");
    }

    private static List<String> responseFallback(PlayerRaidSavedData.MercyKind kind, boolean spared) {
        if (kind == PlayerRaidSavedData.MercyKind.BABY) return spared ? BABY_SPARED : BABY_KILL;
        return spared ? NITWIT_SPARED : NITWIT_KILL;
    }

    private static String message(
            ServerPlayer player,
            Villager villager,
            String key,
            List<String> fallback) {
        return VillagerDialogueResources.globalMessage(
                        player.getServer(),
                        player.getRandom(),
                        key,
                        VillagerLocale.locale(player),
                        Map.of(
                                "villager", villager.getDisplayName().getString(),
                                "player", player.getDisplayName().getString()))
                .orElseGet(() -> fallback.get(player.getRandom().nextInt(fallback.size())));
    }

    private static String globalText(ServerPlayer player, String key, String fallback) {
        return VillagerDialogueResources.globalMessage(
                        player.getServer(), player.getRandom(), key, VillagerLocale.locale(player))
                .orElse(fallback);
    }

    private static boolean isMercyOption(String optionId) {
        return SPARE_OPTION_ID.equals(optionId)
                || KILL_OPTION_ID.equals(optionId)
                || SILENCE_OPTION_ID.equals(optionId);
    }

    private static void end(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        VillagerConversationService.endForPlayer(player, true);
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private record MercySession(UUID raidId, UUID villagerId, int entityId) {
    }
}
