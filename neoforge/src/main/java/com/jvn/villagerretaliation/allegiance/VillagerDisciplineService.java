package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class VillagerDisciplineService {
    private static final long INCIDENT_TIMEOUT_TICKS = 80L;
    private static final double MAX_PURSUIT_DISTANCE_SQR = 16.0D * 16.0D;
    private static final Map<UUID, DisciplinaryIncident> INCIDENTS = new HashMap<>();
    private static final Set<CombatPair> COMMITTING = new HashSet<>();

    private VillagerDisciplineService() {
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F
                || !(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !PartyService.areInSameParty(villager, player)) {
            return;
        }
        recordQualifyingHit(level, villager, player);
    }

    public static int recordQualifyingHit(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerAbuseSavedData.AbuseRecord record = VillagerAbuseSavedData.get(level)
                .recordHit(villager.getUUID(), player.getUUID(), level.getGameTime());
        String message = record.hits() == 1
                ? "villagerretaliation.allegiance.warning.first"
                : record.hits() == 2
                        ? "villagerretaliation.allegiance.warning.final"
                        : "villagerretaliation.allegiance.warning.discipline";
        player.sendSystemMessage(Component.translatable(message, villager.getDisplayName()));
        if (record.hits() >= 3) {
            INCIDENTS.put(villager.getUUID(), new DisciplinaryIncident(
                    player.getUUID(), player, level.getGameTime() + INCIDENT_TIMEOUT_TICKS));
        }
        return record.hits();
    }

    public static void onLivingConversionPost(LivingConversionEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            VillagerAbuseSavedData.get(level).transferVillager(
                    event.getEntity().getUUID(), event.getOutcome().getUUID());
        }
    }

    public static boolean tickVillager(Villager villager) {
        DisciplinaryIncident incident = INCIDENTS.get(villager.getUUID());
        if (incident == null || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        Entity entity = incident.player() != null ? incident.player() : level.getEntity(incident.playerId());
        if (!(entity instanceof ServerPlayer player)
                || !player.isAlive()
                || level.getGameTime() > incident.expiresGameTime()
                || villager.distanceToSqr(player) > MAX_PURSUIT_DISTANCE_SQR) {
            finish(villager);
            return false;
        }
        villager.setTarget(player);
        villager.setAggressive(true);
        villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (!VillagerRetaliationRetaliationUtil.canMeleeHit(villager, player)) {
            villager.getNavigation().moveTo(player, 1.1D);
            return true;
        }
        CombatPair pair = new CombatPair(villager.getUUID(), player.getUUID());
        COMMITTING.add(pair);
        try {
            villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            villager.doHurtTarget(player);
        } finally {
            COMMITTING.remove(pair);
            finish(villager);
        }
        return true;
    }

    public static boolean isCommitting(LivingEntity actor, LivingEntity target) {
        return actor != null && target != null
                && COMMITTING.contains(new CombatPair(actor.getUUID(), target.getUUID()));
    }

    public static void capFinalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSource().getEntity() instanceof Villager villager)
                || !isCommitting(villager, player)) {
            return;
        }
        float maximum = Math.max(0.0F, player.getHealth() - 1.0F);
        event.setNewDamage(Math.min(event.getNewDamage(), maximum));
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        clearRuntimeState();
    }

    public static void clearRuntimeState() {
        INCIDENTS.clear();
        COMMITTING.clear();
    }

    public static int abuseCount(ServerLevel level, UUID villagerId, UUID playerId) {
        return VillagerAbuseSavedData.get(level).record(villagerId, playerId).hits();
    }

    public static boolean hasIncident(UUID villagerId) {
        return INCIDENTS.containsKey(villagerId);
    }

    public static boolean reset(ServerLevel level, UUID villagerId, UUID playerId) {
        INCIDENTS.remove(villagerId);
        return VillagerAbuseSavedData.get(level).reset(villagerId, playerId);
    }

    private static void finish(Villager villager) {
        INCIDENTS.remove(villager.getUUID());
        villager.setTarget(null);
        villager.setAggressive(false);
        villager.setLastHurtByMob(null);
        villager.getNavigation().stop();
    }

    private record DisciplinaryIncident(UUID playerId, ServerPlayer player, long expiresGameTime) {
    }

    private record CombatPair(UUID actorId, UUID targetId) {
    }
}
