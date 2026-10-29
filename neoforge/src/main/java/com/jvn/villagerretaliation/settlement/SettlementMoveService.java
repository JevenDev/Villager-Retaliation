package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerAiArbitration;
import com.jvn.villagerretaliation.interaction.work.HiredRouteNavigator;
import com.jvn.villagerretaliation.villager.VillagerMovementSpeedPolicy;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementMoveService {
    private static final Map<Villager, Order> ORDERS = new WeakHashMap<>();

    private SettlementMoveService() { }

    static boolean command(ServerPlayer player, SettlementRecord town, UUID resident, BlockPos destination) {
        var level = player.serverLevel();
        if (!SettlementViewService.eligible(player, town) || !town.permits(player.getUUID(), SettlementPermission.STAFF)
                || SettlementSavedData.get(level).readOnly() || !town.contains(destination)
                || level.isOutsideBuildHeight(destination) || !level.getWorldBorder().isWithinBounds(destination)
                || !level.hasChunkAt(destination) || !(level.getEntity(resident) instanceof Villager villager)
                || !available(villager) || VillagerAiArbitration.interruptsStudy(level, villager) || SettlementWorkforceService.home(level, villager).filter(home -> home.id().equals(town.id())).isEmpty()) return false;
        clear(villager);
        var area = town.workplace(town.duties().get(resident));
        if (area != null && area.type().role() != null)
            HiredWorkStateStore.pauseWork(level, villager, area.type().role(), "interaction.work.status.waiting_tick", Map.of());
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        if (move(level, villager, destination) == HiredRouteNavigator.NodeMovement.FAILED) return false;
        ORDERS.put(villager, new Order(town.id(), player.getUUID(), town.duties().get(resident), destination.immutable(), level.getGameTime() + 1200));
        return true;
    }

    public static boolean controls(Villager villager) {
        return ORDERS.containsKey(villager) && available(villager) && villager.level() instanceof ServerLevel level
                && !VillagerAiArbitration.interruptsStudy(level, villager);
    }

    private static boolean available(Villager villager) {
        return villager.isAlive() && !villager.isBaby() && !villager.isNoAi()
                && !SettlementWorkforceService.hasPersonalCommitment(villager)
                && !com.jvn.villagerretaliation.combat.downed.VillagerDownedService.isDowned(villager)
                && !com.jvn.villagerretaliation.quest.follower.QuestFollowerService.controls(villager);
    }

    public static boolean tick(Villager villager) {
        var order = ORDERS.get(villager);
        if (order == null || !(villager.level() instanceof ServerLevel level)) return false;
        var town = SettlementWorkforceService.home(level, villager).orElse(null);
        if (!available(villager) || level.getGameTime() >= order.expires() || town == null || !town.id().equals(order.town())
                || !town.permits(order.commander(), SettlementPermission.STAFF) || town.departures().containsKey(villager.getUUID())
                || !Objects.equals(order.area(), town.duties().get(villager.getUUID())) || !town.contains(order.destination())) {
            clear(villager);
            return false;
        }
        if (!controls(villager)) {
            clear(villager);
            return false;
        }
        var movement = move(level, villager, order.destination());
        if (movement == HiredRouteNavigator.NodeMovement.FAILED || villager.blockPosition().distSqr(order.destination()) <= 4.0D) clear(villager);
        return true;
    }

    private static HiredRouteNavigator.NodeMovement move(ServerLevel level, Villager villager, BlockPos destination) {
        var next = SettlementGuardService.nextStep(level, villager, destination);
        return next == null ? HiredRouteNavigator.NodeMovement.FAILED
                : HiredRouteNavigator.moveToRouteNode(level, villager, next, VillagerMovementSpeedPolicy.WALK_SPEED_MODIFIER);
    }

    static void clear(Villager villager) {
        if (ORDERS.remove(villager) != null && VillagerTaskNavigationUtil.isHiredWalkTarget(villager))
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
    }

    private record Order(VillageAllegianceId town, UUID commander, UUID area, BlockPos destination, long expires) { }
}
