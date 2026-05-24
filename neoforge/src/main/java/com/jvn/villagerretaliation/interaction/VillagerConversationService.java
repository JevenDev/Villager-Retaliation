package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerConversationService {
    private static final int IDLE_TIMEOUT_TICKS = 20 * 60 * 2;
    private static final Map<UUID, VillagerConversationSession> SESSIONS_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_BY_VILLAGER = new HashMap<>();

    private VillagerConversationService() {
    }

    public static boolean start(ServerPlayer player, Villager villager) {
        if (!VillagerInteractionService.canUseInteractionSystem(player, villager)) {
            return false;
        }

        UUID existingPlayerId = PLAYER_BY_VILLAGER.get(villager.getUUID());
        if (existingPlayerId != null && !existingPlayerId.equals(player.getUUID())) {
            return false;
        }

        endForPlayer(player, false);
        VillagerConversationSession session = new VillagerConversationSession(
                player.getUUID(),
                villager.getUUID(),
                villager.getId(),
                player.level().dimension(),
                villager.blockPosition(),
                player.serverLevel().getGameTime()
        );
        SESSIONS_BY_PLAYER.put(player.getUUID(), session);
        PLAYER_BY_VILLAGER.put(villager.getUUID(), player.getUUID());
        holdVillager(villager, player);
        return true;
    }

    public static boolean validate(ServerPlayer player, Villager villager) {
        VillagerConversationSession session = SESSIONS_BY_PLAYER.get(player.getUUID());
        if (session == null || !session.active() || !session.villagerId().equals(villager.getUUID())) {
            return false;
        }
        if (!isSessionStillValid(player, villager, session)) {
            end(player, session, true);
            return false;
        }
        session.touch(player.serverLevel().getGameTime());
        holdVillager(villager, player);
        return true;
    }

    public static void endForPlayer(ServerPlayer player, boolean notifyClient) {
        VillagerConversationSession session = SESSIONS_BY_PLAYER.get(player.getUUID());
        if (session != null) {
            end(player, session, notifyClient);
        }
    }

    public static void endForVillager(Villager villager, boolean notifyClient) {
        UUID playerId = PLAYER_BY_VILLAGER.get(villager.getUUID());
        if (playerId == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        Player foundPlayer = level.getPlayerByUUID(playerId);
        ServerPlayer player = foundPlayer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        VillagerConversationSession session = SESSIONS_BY_PLAYER.get(playerId);
        if (player != null && session != null) {
            end(player, session, notifyClient);
            return;
        }
        SESSIONS_BY_PLAYER.remove(playerId);
        PLAYER_BY_VILLAGER.remove(villager.getUUID());
    }

    public static boolean isConversing(Villager villager) {
        return PLAYER_BY_VILLAGER.containsKey(villager.getUUID());
    }

    public static void tickVillager(Villager villager) {
        UUID playerId = PLAYER_BY_VILLAGER.get(villager.getUUID());
        if (playerId == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        Player foundPlayer = level.getPlayerByUUID(playerId);
        ServerPlayer player = foundPlayer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        VillagerConversationSession session = SESSIONS_BY_PLAYER.get(playerId);
        if (player == null || session == null || !isSessionStillValid(player, villager, session)) {
            if (player != null && session != null) {
                end(player, session, true);
            } else {
                SESSIONS_BY_PLAYER.remove(playerId);
                PLAYER_BY_VILLAGER.remove(villager.getUUID());
            }
            return;
        }

        holdVillager(villager, player);
    }

    public static void endForEntityLeaving(Entity entity, boolean notifyClient) {
        if (entity instanceof ServerPlayer player) {
            endForPlayer(player, notifyClient);
        } else if (entity instanceof Villager villager) {
            endForVillager(villager, notifyClient);
        }
    }

    private static void end(ServerPlayer player, VillagerConversationSession session, boolean notifyClient) {
        session.deactivate();
        SESSIONS_BY_PLAYER.remove(player.getUUID());
        PLAYER_BY_VILLAGER.remove(session.villagerId());
        if (notifyClient) {
            PacketDistributor.sendToPlayer(player, new VillagerConversationEndedPayload(session.villagerEntityId(), ""));
        }
    }

    private static boolean isSessionStillValid(ServerPlayer player, Villager villager, VillagerConversationSession session) {
        if (player.level().dimension() != session.dimension()) {
            return false;
        }
        if (!VillagerInteractionService.shouldStayConversable(player, villager)) {
            return false;
        }
        if (villager.getTarget() != null || villager.getLastHurtByMob() != null) {
            return false;
        }
        long idleTicks = player.serverLevel().getGameTime() - session.lastInteractionGameTime();
        return idleTicks <= IDLE_TIMEOUT_TICKS;
    }

    private static void holdVillager(Villager villager, ServerPlayer player) {
        if (villager.isSleeping()) {
            villager.stopSleeping();
        }
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (VillagerRetaliationConfig.FREEZE_VILLAGER_DURING_DIALOGUE.get() && !villager.getNavigation().isDone()) {
            villager.getNavigation().stop();
        }
    }
}
