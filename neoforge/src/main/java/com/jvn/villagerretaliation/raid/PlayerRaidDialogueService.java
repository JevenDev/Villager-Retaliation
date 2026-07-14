package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.village.VillagerRaidMemorySavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Runtime controller for the chained betrayal declaration conversation. */
public final class PlayerRaidDialogueService {
    private static final String CONTINUE = "player_raid.continue";
    private static final String VICTORY_MESSAGE_KEY = "interaction.party.player_raid_victory";
    private static final String LOSS_MESSAGE_KEY = "interaction.party.player_raid_loss";
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final List<String> VICTORY = List.of(
            "The last defender is down. This raid is ours.",
            "We held our ground and broke theirs. That is a victory.",
            "Hard fighting, clean finish. We saw it through together.",
            "Their defense is finished. We can leave with our heads high.",
            "The village has fallen, and every one of us earned the road out.",
            "That was no easy fight, but our party proved stronger.",
            "The battle is over. Remember who stood beside you when it mattered.",
            "We came under one banner and leave beneath it victorious.",
            "Their line broke before ours did. The raid is won.",
            "Count us all before we move on. Victory means more when everyone returns.",
            "We took every blow they offered and still finished the fight.",
            "The defenders gave everything. Today, everything was not enough.",
            "Our party held together from the first horn to the last strike.",
            "No defenders remain between us and victory.",
            "Catch your breath. We won this raid, and we won it together."
    );
    private static final List<String> LOSS = List.of(
            "The defenders held. There is no victory for us here today.",
            "We lost this one. Regroup before the village takes anything else from us.",
            "Their line did not break, and ours could not stay. The raid is over.",
            "We leave without victory, but at least we still leave together.",
            "That village was ready for us. Next time, we must be readier.",
            "The raid failed. Save your strength for a fight we can finish.",
            "We pushed hard and gained nothing. It is time to fall back.",
            "Their defenders earned this field. We should remember how.",
            "The horn called us in, but it cannot turn this defeat into a victory.",
            "We were beaten, not broken. Get everyone home and tend the wounds.",
            "This fight is lost. Do not lose anyone else trying to deny it.",
            "We could not finish the raid. We can still learn from why.",
            "The village stands, and we are the ones retreating. That is the truth of it.",
            "No excuses. Their defense outlasted our attack.",
            "We leave empty-handed today. Let the loss make us wiser."
    );

    private static final List<String> PRIMARY = List.of(
            "You march beneath that banner against my home? Traitor.",
            "I followed you in trust, and you answer by threatening my village. Traitor.",
            "Lower that horn. Those are my people, and you have betrayed us.",
            "So this is what your banner means: treachery against my home.",
            "You brought me here to watch you destroy my neighbors? Traitor.",
            "My contract ends where your attack on my home begins.",
            "I know every door in this village, and I will defend every one from you.",
            "You called me companion, then raised a war banner over my family.",
            "That horn declares more than a raid. It declares your betrayal.",
            "I will not stand beside someone who threatens the village that raised me.",
            "You mistook my loyalty for permission to burn my own home.",
            "The party is over. From this moment, I stand with my village.",
            "You have chosen plunder over friendship, and my answer is no.",
            "I recognize that banner now: it belongs to a traitor.",
            "If you cross into my village as a raider, you cross me as an enemy."
    );
    private static final List<String> CHAINED = List.of(
            "And you thought I would follow? This is my home too.",
            "You betrayed more than one companion today. I stand with the village.",
            "Do not count me among your raiders. Count me among your opposition.",
            "I heard the horn, and I heard the lie behind every promise you made us.",
            "My neighbors are not targets. Turn back, or face me with them.",
            "Another contract broken by your own treachery. I choose my people.",
            "We shared a road, but I will not share this crime.",
            "You recruited us from this village only to return as its enemy.",
            "There will be no divided loyalty. My home comes first.",
            "The banner on your head cannot command me to betray my family.",
            "I leave your party freely, because you have made yourself our foe.",
            "Every one of us remembers where we came from. You should have remembered too.",
            "You have forced my choice, and I choose the village.",
            "Whatever bond we had ends at the boundary of my threatened home.",
            "You will find no ally in me while that raid horn echoes here."
    );
    private static final List<String> TURN = List.of(
            "The whole village knows what you are now. We will defend it together.",
            "Enough words. You face villagers, militia, and iron from this moment on.",
            "Your reputation here is ash. Now leave, or fight every one of us.",
            "The doors are barred, the defenders are armed, and we are ready for you.",
            "You wanted a raid. What you have awakened is a village united against you.",
            "No neighbor will shelter you after this betrayal. We stand as one.",
            "Children, get inside. Defenders, to arms. The traitors have shown themselves.",
            "The warning is given. Our golems and our people will answer your attack.",
            "From the smallest house to the tallest bell, this village rejects you.",
            "You are no guest and no friend here now. You are the raider.",
            "Remember this moment: you chose to make enemies of an entire village.",
            "We have counted our people, armed our defenders, and marked every traitor.",
            "There is still time to flee. There is no time left to pretend loyalty.",
            "The village will not scatter for you. It will close ranks.",
            "Your horn has finished speaking. Now the village answers."
    );

    private PlayerRaidDialogueService() {
    }

    static boolean begin(ServerPlayer player, PlayerRaidSavedData.RaidRecord raid) {
        if (!VillagerRetaliationConfig.ENABLE_FORCED_DIALOGUE.get() || raid.defectors().isEmpty()) return false;
        List<UUID> speakers = new ArrayList<>();
        for (UUID id : raid.defectors()) {
            Entity entity = find(player.getServer(), id);
            if (entity instanceof Villager villager && villager.isAlive()
                    && villager.level() == player.serverLevel()) speakers.add(id);
        }
        speakers.sort(Comparator.comparingDouble(id -> {
            Entity entity = find(player.getServer(), id);
            return entity == null ? Double.MAX_VALUE : entity.distanceToSqr(player);
        }));
        if (speakers.isEmpty()) return false;
        Session session = new Session(raid.id(), speakers, 0, false, -1);
        SESSIONS.put(player.getUUID(), session);
        if (openCurrent(player, session)) return true;
        SESSIONS.remove(player.getUUID());
        return false;
    }

    static void announceOutcome(
            MinecraftServer server, PlayerRaidSavedData.RaidRecord raid, boolean raidersWon) {
        if (raid.raiderVillagers().isEmpty() || raid.raiderPlayers().isEmpty()) return;
        VillagerRaidMemorySavedData memories = VillagerRaidMemorySavedData.get(server.overworld());
        VillagerRaidMemorySavedData.RaidOutcome outcome = raidersWon
                ? VillagerRaidMemorySavedData.RaidOutcome.VICTORY
                : VillagerRaidMemorySavedData.RaidOutcome.LOSS;
        long gameTime = server.overworld().getGameTime();
        for (UUID villagerId : raid.raiderVillagers()) {
            for (UUID playerId : raid.raiderPlayers()) {
                memories.remember(villagerId, playerId, outcome, gameTime);
            }
        }
        String messageKey = raidersWon ? VICTORY_MESSAGE_KEY : LOSS_MESSAGE_KEY;
        List<String> fallback = raidersWon ? VICTORY : LOSS;
        for (UUID villagerId : raid.raiderVillagers()) {
            Entity entity = find(server, villagerId);
            if (!(entity instanceof Villager villager) || !villager.isAlive()) continue;
            for (UUID playerId : raid.raiderPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) continue;
                String line = VillagerDialogueResources.globalMessage(
                                server,
                                player.getRandom(),
                                messageKey,
                                VillagerLocale.locale(player),
                                Map.of(
                                        "village", raid.villageName(),
                                        "villager", VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                                        "player", player.getDisplayName().getString()))
                        .orElseGet(() -> fallback.get(player.getRandom().nextInt(fallback.size())));
                VillagerInteractionService.sendPersonalVillagerChat(player, villager, line);
            }
        }
    }

    public static boolean handleDialogueRequest(ServerPlayer player, int entityId, String optionId) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !CONTINUE.equals(optionId)) return false;
        if (session.entityId != entityId) return true;
        advance(player, session);
        return true;
    }

    public static boolean handleConversationEndRequest(ServerPlayer player, int entityId) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.entityId != entityId) return false;
        advance(player, session);
        return true;
    }

    private static void advance(ServerPlayer player, Session session) {
        VillagerConversationService.endForPlayer(player, true);
        if (session.turnStage) {
            complete(player, session.raidId);
            return;
        }
        session.index++;
        if (session.index >= session.speakers.size()) {
            session.index = session.speakers.size() - 1;
            session.turnStage = true;
        }
        if (!openCurrent(player, session)) advance(player, session);
    }

    private static boolean openCurrent(ServerPlayer player, Session session) {
        Entity entity = find(player.getServer(), session.speakers.get(session.index));
        if (!(entity instanceof Villager villager) || !villager.isAlive() || villager.level() != player.serverLevel()) {
            return false;
        }
        String stage = session.turnStage ? "turn" : session.index == 0 ? "primary" : "chained";
        List<String> fallback = session.turnStage ? TURN : session.index == 0 ? PRIMARY : CHAINED;
        ForcedDialogueResources.ForcedDialogueDefinition definition = ForcedDialogueResources.selectCandidates(
                        player.getServer(), ForcedDialogueResources.ForcedDialogueTrigger.PLAYER_RAID_BETRAYAL, null)
                .stream().filter(candidate -> candidate.id().equals(stage)).findFirst().orElse(null);
        String line = "";
        if (definition != null) {
            ForcedDialogueResources.LocalizedText selected = definition.selectLine(player.getRandom());
            line = selected.text();
            if (!selected.key().isBlank()) {
                line = VillagerDialogueResources.globalMessage(
                        player.getServer(), player.getRandom(), selected.key(), VillagerLocale.locale(player),
                        Map.of(
                                "villager", VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                                "player", player.getDisplayName().getString()))
                        .orElse(line);
            }
        }
        if (line.isBlank()) line = fallback.get(player.getRandom().nextInt(fallback.size()));
        boolean opened = VillagerInteractionService.openForcedDialogue(player, villager, line, List.of(
                DialogueOptionDefinition.simple(CONTINUE, "Continue", DialogueRequestType.QUESTION, 0)), true);
        if (opened) session.entityId = villager.getId();
        return opened;
    }

    private static void complete(ServerPlayer player, UUID raidId) {
        SESSIONS.remove(player.getUUID());
        PlayerRaidService.beginPreparation(player.getServer(), raidId);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerRaidMercyService.onPlayerLoggedOut(player);
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) PlayerRaidService.beginPreparation(player.getServer(), session.raidId);
    }

    static void clearRuntimeState() {
        SESSIONS.clear();
    }

    static boolean hasSession(UUID raidId) {
        return SESSIONS.values().stream().anyMatch(session -> session.raidId.equals(raidId));
    }

    static void reconcile(MinecraftServer server) {
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null) {
                SESSIONS.remove(entry.getKey(), session);
                PlayerRaidService.beginPreparation(server, session.raidId);
                continue;
            }
            Entity speaker = player.serverLevel().getEntity(session.entityId);
            if (!(speaker instanceof Villager villager) || !villager.isAlive()) {
                VillagerConversationService.endForPlayer(player, true);
                advance(player, session);
            }
        }
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static final class Session {
        private final UUID raidId;
        private final List<UUID> speakers;
        private int index;
        private boolean turnStage;
        private int entityId;

        private Session(UUID raidId, List<UUID> speakers, int index, boolean turnStage, int entityId) {
            this.raidId = raidId;
            this.speakers = List.copyOf(speakers);
            this.index = index;
            this.turnStage = turnStage;
            this.entityId = entityId;
        }
    }
}
