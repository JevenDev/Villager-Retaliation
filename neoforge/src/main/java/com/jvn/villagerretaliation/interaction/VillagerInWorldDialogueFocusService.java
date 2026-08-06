package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.interaction.VillagerAiArbitration.Priority;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/** Maintains best-effort player focus for chat barks without stealing higher-priority AI attention. */
public final class VillagerInWorldDialogueFocusService {
    private static final long FOCUS_TICKS = 60L;
    private static final double MAX_FOCUS_DISTANCE_SQR = 64.0D * 64.0D;
    private static final Map<UUID, Focus> ACTIVE_FOCUS = new HashMap<>();

    private VillagerInWorldDialogueFocusService() {
    }

    public static void requestFocus(Villager villager, ServerPlayer player) {
        if (!(villager.level() instanceof ServerLevel level) || !canTakeSpeechFocus(level, villager, player)) return;
        long gameTime = level.getGameTime();
        ACTIVE_FOCUS.put(villager.getUUID(), new Focus(player.getUUID(), gameTime + FOCUS_TICKS));
        if ((gameTime & 255L) == 0L) {
            ACTIVE_FOCUS.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= gameTime);
        }
    }

    public static ServerPlayer activeFocusTarget(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return null;
        Focus focus = ACTIVE_FOCUS.get(villager.getUUID());
        if (focus == null) return null;
        if (level.getGameTime() >= focus.expiresAt()) {
            ACTIVE_FOCUS.remove(villager.getUUID());
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(focus.playerId());
        return canTakeSpeechFocus(level, villager, player) ? player : null;
    }

    public static void clearRuntimeState() {
        ACTIVE_FOCUS.clear();
    }

    static boolean canTakeSpeechFocus(ServerLevel level, Villager villager, ServerPlayer player) {
        if (level == null || villager == null || player == null
                || !villager.isAlive() || !player.isAlive() || player.isSpectator()
                || player.level() != level || villager.distanceToSqr(player) > MAX_FOCUS_DISTANCE_SQR) {
            return false;
        }
        Brain<Villager> brain = villager.getBrain();
        if (brain.isActive(Activity.WORK)) return false;
        Priority priority = VillagerAiArbitration.currentPriority(level, villager);
        return priority == Priority.VANILLA_SCHEDULE_OR_IDLE;
    }

    private record Focus(UUID playerId, long expiresAt) {
    }
}
