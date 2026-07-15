package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.allegiance.AllegianceCombatContext;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceCombatPolicy;
import com.jvn.villagerretaliation.allegiance.VillageCombatAuthorizationService;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredPathResult;
import com.jvn.villagerretaliation.interaction.work.HiredRouteNavigator;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.inventory.PartyContainerLootService;
import com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload;
import com.jvn.villagerretaliation.raid.PlayerRaidService;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PartyQuickCommandService {
    private static final double MAX_TARGET_DISTANCE = 64.0D;
    private static final double ATTACK_TARGET_DISTANCE = 32.0D;
    private static final double TRANSMITTED_TARGET_TOLERANCE = 0.85D;
    private static final double ARRIVAL_DISTANCE_SQR = 1.75D * 1.75D;
    private static final double REGROUP_ARRIVAL_DISTANCE_SQR = 2.5D * 2.5D;
    private static final double MOVE_SPEED = 0.72D;
    private static final double REGROUP_SPEED = 0.9D;
    private static final double GATHER_SPEED = 0.78D;
    private static final double ITEM_PICKUP_DISTANCE_SQR = 1.5D * 1.5D;
    private static final double DROP_SEARCH_HORIZONTAL_RADIUS = 32.0D;
    private static final double DROP_SEARCH_VERTICAL_RADIUS = 12.0D;
    private static final long PATH_REFRESH_TICKS = 8L;
    private static final int MAX_BACKGROUND_PATH_FAILURES = 12;
    private static final int MAX_PICKUP_WAIT_TICKS = 100;

    private static final Map<UUID, RuntimeOrder> RUNTIME_ORDERS = new HashMap<>();
    private static final Map<UUID, UUID> MANUAL_ATTACK_TARGETS = new HashMap<>();
    private static final Set<UUID> STAND_GUARD_VILLAGERS = new HashSet<>();

    private PartyQuickCommandService() {
    }

    public static void handle(ServerPlayer player, PartyQuickCommandRequestPayload payload) {
        if (player == null || payload == null || payload.command() == null || !player.isAlive()) {
            return;
        }
        PartyRecord party = commandedParty(player);
        if (party == null) {
            notice(player, "villagerretaliation.party.error.leader_only");
            return;
        }
        List<PartyVillagerRecord> participants = party.villagers().stream()
                .filter(PartyVillagerRecord::quickCommandsEnabled)
                .toList();
        if (participants.isEmpty()) {
            notice(player, "villagerretaliation.party.quick_command.none_enabled");
            return;
        }

        boolean loweringShields = payload.command() == PartyQuickCommand.STAND_GUARD
                && isStandGuardActive(party);
        int affected = switch (payload.command()) {
            case ATTACK -> attack(player, participants, payload.targetEntityId());
            case MOVE_TO -> moveTo(player, participants, payload.targetPosition());
            case STAY_HERE -> stayHere(player, participants);
            case REGROUP -> regroup(player, party, participants);
            case STAND_GUARD -> standGuard(player, participants);
            case RANGE -> setWeaponPreference(player, participants, PartyWeaponPreference.RANGED);
            case MELEE -> setWeaponPreference(player, participants, PartyWeaponPreference.MELEE);
            case HEAL -> heal(player, participants);
            case PICK_UP_DROPS -> pickUpDrops(player, participants);
            case LOOT_CONTAINERS -> lootContainers(player, participants, payload.targetPosition());
        };
        if (affected <= 0) {
            notice(player, targetRequired(payload.command())
                    ? "villagerretaliation.party.quick_command.invalid_target"
                    : "villagerretaliation.party.quick_command.none_available");
            return;
        }
        PartyService.markChanged(player.serverLevel());
        PartySyncService.syncParty(player.getServer(), party.id());
        player.displayClientMessage(Component.translatable(
                "villagerretaliation.party.quick_command.sent",
                Component.translatable(loweringShields
                        ? "villagerretaliation.party.quick_command.lower_shields"
                        : translationKey(payload.command())),
                affected), true);
    }

    public static void setParticipation(ServerPlayer player, UUID villagerId, boolean enabled) {
        PartyRecord party = commandedParty(player);
        PartyVillagerRecord record = party == null || villagerId == null ? null : party.villager(villagerId);
        if (record == null) {
            notice(player, "villagerretaliation.party.error.leader_only");
            return;
        }
        record.setQuickCommandsEnabled(enabled);
        if (!enabled) {
            record.setRegrouping(false);
            Villager loaded = PartyEntityResolver.activeVillager(player.getServer(), villagerId);
            if (loaded == null) {
                clearAllOrders(villagerId);
            } else {
                clearAllOrders(loaded);
            }
        }
        PartyService.markChanged(player.serverLevel());
        PartySyncService.syncParty(player.getServer(), party.id());
    }

    public static void onVillagerTickPost(Villager villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        RuntimeOrder order = RUNTIME_ORDERS.get(villager.getUUID());
        boolean standingGuard = STAND_GUARD_VILLAGERS.contains(villager.getUUID());
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (record == null || !record.quickCommandsEnabled() || !villager.isAlive()) {
            clearAllOrders(villager);
            return;
        }

        if (record.regrouping()) {
            releaseRegroupSuppressionIfArrived(level, villager, party, record);
        }
        if (order == null && !standingGuard) {
            return;
        }

        if (order != null
                && order.type() == RuntimeOrderType.MOVE_TO
                && VillagerRetaliationHandler.hasActiveRetaliationTarget(villager)) {
            return;
        }
        if (order != null
                && order.type().background()
                && com.jvn.villagerretaliation.villager.VillagerRecoveryService.isForcingRecovery(villager)) {
            return;
        }
        if (order != null && order.type().background()) {
            VillagerRetaliationHandler.suppressCombatForPartyOrder(villager);
        }
        if (order != null) {
            switch (order.type()) {
                case MOVE_TO -> tickMoveTo(level, villager, order, party);
                case REGROUP -> tickRegroup(level, villager, order, party);
                case PICK_UP_DROPS -> tickPickUpDrops(level, villager, order, party);
                case LOOT_CONTAINERS -> tickLootContainers(level, villager, order, party);
            }
        }
        if (standingGuard && STAND_GUARD_VILLAGERS.contains(villager.getUUID())) {
            tickStandGuard(villager, party);
        }
    }

    public static boolean overridesRecruitmentMovement(Villager villager) {
        if (villager == null) {
            return false;
        }
        RuntimeOrder order = RUNTIME_ORDERS.get(villager.getUUID());
        return order != null
                || VillagerRetaliationHandler.hasActiveRetaliationTarget(villager)
                || com.jvn.villagerretaliation.villager.VillagerRecoveryService.isForcingRecovery(villager);
    }

    public static boolean suppressesPartyTargetAcquisition(Villager villager) {
        if (villager == null) {
            return false;
        }
        RuntimeOrder order = RUNTIME_ORDERS.get(villager.getUUID());
        boolean persistedRegroup = villager.level() instanceof ServerLevel level
                && PartyService.getPartyForVillager(level, villager.getUUID())
                .map(party -> party.villager(villager.getUUID()))
                .map(record -> record.quickCommandsEnabled() && record.regrouping())
                .orElse(false);
        return (order != null
                && (order.type() == RuntimeOrderType.REGROUP || order.type().background())
                || persistedRegroup)
                || com.jvn.villagerretaliation.villager.VillagerRecoveryService.isForcingRecovery(villager);
    }

    public static boolean overridesCombatTargeting(Villager villager) {
        if (villager == null) {
            return false;
        }
        RuntimeOrder order = RUNTIME_ORDERS.get(villager.getUUID());
        return order != null && order.type().background();
    }

    public static void maintainManualAttackAuthorization(Villager villager) {
        UUID targetId = villager == null ? null : MANUAL_ATTACK_TARGETS.get(villager.getUUID());
        if (targetId == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = level.getEntity(targetId);
        if (!(entity instanceof LivingEntity target)
                || !target.isAlive()
                || !VillagerRetaliationHandler.hasRetaliationTarget(villager, target)) {
            MANUAL_ATTACK_TARGETS.remove(villager.getUUID());
            return;
        }
        if (VillageCombatAuthorizationService.isAuthorized(villager, target)) {
            return;
        }
        var decision = VillageAllegianceCombatPolicy.evaluate(
                level, villager, target, AllegianceCombatContext.PARTY_ATTACK, false);
        if (decision.denied()
                || decision.action() == com.jvn.villagerretaliation.allegiance.AllegianceCombatDecision.Action.ALLOW
                && !VillageCombatAuthorizationService.authorize(level, villager, target)) {
            MANUAL_ATTACK_TARGETS.remove(villager.getUUID());
            VillagerRetaliationHandler.clearCustomTarget(villager);
        }
    }

    public static BlockPos moveTarget(PartyRecord party) {
        if (party == null) {
            return null;
        }
        for (PartyVillagerRecord record : party.villagers()) {
            RuntimeOrder order = RUNTIME_ORDERS.get(record.villagerId());
            if (order != null && order.type() == RuntimeOrderType.MOVE_TO && order.targetPosition() != null) {
                return order.targetPosition().below();
            }
        }
        return null;
    }

    public static ResourceLocation moveTargetDimension(PartyRecord party) {
        if (party == null) {
            return null;
        }
        for (PartyVillagerRecord record : party.villagers()) {
            RuntimeOrder order = RUNTIME_ORDERS.get(record.villagerId());
            if (order != null && order.type() == RuntimeOrderType.MOVE_TO) {
                return order.targetDimension();
            }
        }
        return null;
    }

    public static boolean isStandGuardActive(PartyRecord party) {
        if (party == null) {
            return false;
        }
        return party.villagers().stream()
                .anyMatch(record -> STAND_GUARD_VILLAGERS.contains(record.villagerId()));
    }

    public static void onVillagerUnloaded(Villager villager) {
        if (villager != null) {
            clearAllOrders(villager);
        }
    }

    public static void clearRuntimeState() {
        RUNTIME_ORDERS.clear();
        MANUAL_ATTACK_TARGETS.clear();
        STAND_GUARD_VILLAGERS.clear();
    }

    private static int attack(
            ServerPlayer player,
            List<PartyVillagerRecord> records,
            int transmittedTargetId) {
        LivingEntity target = attackTargetAtCrosshair(player, records, transmittedTargetId);
        if (target == null) {
            return 0;
        }
        int affected = 0;
        for (PartyVillagerRecord record : records) {
            Villager villager = PartyEntityResolver.activeVillager(player.serverLevel(), record.villagerId());
            if (!canReceiveAttackOrder(player.serverLevel(), villager, record, target)) {
                continue;
            }
            var decision = VillageAllegianceCombatPolicy.evaluate(
                    player.serverLevel(), villager, target, AllegianceCombatContext.PARTY_ATTACK, false);
            if (decision.denied()) {
                continue;
            }
            if (decision.action() == com.jvn.villagerretaliation.allegiance.AllegianceCombatDecision.Action.ALLOW
                    && !VillageCombatAuthorizationService.authorize(player.serverLevel(), villager, target)) {
                continue;
            }
            if (VillagerRetaliationHandler.engageCustomTarget(villager, target, false)) {
                clearMovementOrder(villager);
                com.jvn.villagerretaliation.villager.VillagerRecoveryService.cancelForcedRecovery(villager);
                record.setRegrouping(false);
                MANUAL_ATTACK_TARGETS.put(villager.getUUID(), target.getUUID());
                affected++;
            }
        }
        return affected;
    }

    private static LivingEntity attackTargetAtCrosshair(
            ServerPlayer player,
            List<PartyVillagerRecord> records,
            int transmittedTargetId) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 rayEnd = eye.add(player.getViewVector(1.0F).scale(ATTACK_TARGET_DISTANCE));
        HitResult blockHit = level.clip(new ClipContext(
                eye,
                rayEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        Vec3 visibleEnd = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation()
                : rayEnd;
        Entity transmitted = transmittedTargetId == PartyQuickCommandRequestPayload.NO_ENTITY
                ? null
                : level.getEntity(transmittedTargetId);
        if (transmitted instanceof LivingEntity captured
                && captured != player
                && captured.isAlive()
                && captured.isPickable()
                && player.hasLineOfSight(captured)
                && canReceiveAnyAttackOrder(level, records, captured)
                && captured.getBoundingBox().inflate(TRANSMITTED_TARGET_TOLERANCE).clip(eye, visibleEnd).isPresent()) {
            return captured;
        }
        AABB search = player.getBoundingBox()
                .expandTowards(visibleEnd.subtract(eye))
                .inflate(1.0D);

        LivingEntity nearest = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(player, search, candidate ->
                candidate instanceof LivingEntity living
                        && living.isAlive()
                        && candidate.isPickable())) {
            LivingEntity candidate = (LivingEntity) entity;
            if (!canReceiveAnyAttackOrder(level, records, candidate)) {
                continue;
            }
            AABB bounds = candidate.getBoundingBox();
            Optional<Vec3> intersection = bounds.contains(eye)
                    ? Optional.of(eye)
                    : bounds.clip(eye, visibleEnd);
            if (intersection.isEmpty()) {
                continue;
            }
            double distanceSqr = eye.distanceToSqr(intersection.get());
            if (distanceSqr < nearestDistanceSqr) {
                nearest = candidate;
                nearestDistanceSqr = distanceSqr;
            }
        }
        return nearest;
    }

    private static boolean canReceiveAnyAttackOrder(
            ServerLevel level,
            List<PartyVillagerRecord> records,
            LivingEntity target) {
        for (PartyVillagerRecord record : records) {
            Villager villager = PartyEntityResolver.activeVillager(level, record.villagerId());
            if (canReceiveAttackOrder(level, villager, record, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canReceiveAttackOrder(
            ServerLevel level,
            Villager villager,
            PartyVillagerRecord record,
            LivingEntity target) {
        boolean playerRaidOpponents = villager != null
                && target != null
                && PlayerRaidService.areOpposingParticipants(villager, target);
        if (villager == null
                || !villager.isAlive()
                || target == null
                || target == villager
                || !target.isAlive()
                || !villager.canAttack(target)
                || target.isAlliedTo(villager)
                || PartyService.areInSameOrAlliedParty(villager, target)
                || !playerRaidOpponents && !attackModeAllows(record.attackMode(), villager, target)) {
            return false;
        }
        return playerRaidOpponents || !VillageAllegianceCombatPolicy.evaluate(
                level, villager, target, AllegianceCombatContext.PARTY_ATTACK, false).denied();
    }

    private static boolean attackModeAllows(
            PartyAttackMode mode,
            Villager villager,
            LivingEntity target) {
        PartyAttackMode resolved = mode == null ? PartyAttackMode.ALL : mode;
        return resolved.allows(
                target instanceof Animal,
                VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, target),
                target instanceof Player,
                target instanceof Villager,
                target instanceof IronGolem,
                PartyService.getPartyForEntity(target).isPresent());
    }

    private static int moveTo(ServerPlayer player, List<PartyVillagerRecord> records, BlockPos requestedTarget) {
        BlockPos target = validatedTarget(player, requestedTarget);
        if (target == null) {
            return 0;
        }
        int affected = 0;
        for (PartyVillagerRecord record : records) {
            record.setStaying(player.serverLevel().dimension().location(), target);
            record.setRegrouping(false);
            Villager villager = PartyEntityResolver.activeVillager(player.serverLevel(), record.villagerId());
            if (villager == null) {
                clearMovementOrder(record.villagerId());
                MANUAL_ATTACK_TARGETS.remove(record.villagerId());
                affected++;
                continue;
            }
            MANUAL_ATTACK_TARGETS.remove(villager.getUUID());
            com.jvn.villagerretaliation.villager.VillagerRecoveryService.cancelForcedRecovery(villager);
            VillagerRetaliationHandler.clearCustomTarget(villager);
            RUNTIME_ORDERS.put(villager.getUUID(), RuntimeOrder.moveTo(
                    target,
                    player.serverLevel().dimension().location()));
            affected++;
        }
        return affected;
    }

    private static int pickUpDrops(ServerPlayer player, List<PartyVillagerRecord> records) {
        int affected = 0;
        BlockPos center = player.blockPosition().immutable();
        for (PartyVillagerRecord record : records) {
            record.setRegrouping(false);
            Villager villager = PartyEntityResolver.activeVillager(player.serverLevel(), record.villagerId());
            if (villager == null) {
                continue;
            }
            clearMovementOrder(villager);
            MANUAL_ATTACK_TARGETS.remove(villager.getUUID());
            VillagerRetaliationHandler.clearCustomTarget(villager);
            RUNTIME_ORDERS.put(villager.getUUID(), RuntimeOrder.pickUpDrops(
                    center,
                    player.serverLevel().dimension().location()));
            affected++;
        }
        return affected;
    }

    private static int lootContainers(
            ServerPlayer player,
            List<PartyVillagerRecord> records,
            BlockPos requestedTarget) {
        BlockPos center = validatedSearchCenter(player, requestedTarget);
        if (center == null) {
            return 0;
        }
        List<BlockPos> containers = PartyContainerLootService.findContainersNear(player.serverLevel(), center);
        if (containers.isEmpty()) {
            return 0;
        }
        int affected = 0;
        for (PartyVillagerRecord record : records) {
            record.setRegrouping(false);
            Villager villager = PartyEntityResolver.activeVillager(player.serverLevel(), record.villagerId());
            if (villager == null) {
                continue;
            }
            clearMovementOrder(villager);
            MANUAL_ATTACK_TARGETS.remove(villager.getUUID());
            VillagerRetaliationHandler.clearCustomTarget(villager);
            RUNTIME_ORDERS.put(villager.getUUID(), RuntimeOrder.lootContainers(
                    center,
                    player.getUUID(),
                    player.serverLevel().dimension().location(),
                    containers));
            affected++;
        }
        return affected;
    }

    private static int regroup(
            ServerPlayer player,
            PartyRecord party,
            List<PartyVillagerRecord> records) {
        int affected = 0;
        for (PartyVillagerRecord record : records) {
            record.setFollowing();
            record.setRegrouping(true);
            Villager villager = PartyEntityResolver.activeVillager(player.getServer(), record.villagerId());
            if (villager == null) {
                clearMovementOrder(record.villagerId());
                MANUAL_ATTACK_TARGETS.remove(record.villagerId());
                affected++;
                continue;
            }
            MANUAL_ATTACK_TARGETS.remove(villager.getUUID());
            com.jvn.villagerretaliation.villager.VillagerRecoveryService.cancelForcedRecovery(villager);
            VillagerRetaliationHandler.clearCustomTarget(villager);
            if (villager.level() == player.serverLevel()) {
                RUNTIME_ORDERS.put(villager.getUUID(), RuntimeOrder.regroup(player.getUUID()));
            } else {
                clearMovementOrder(villager);
            }
            if (villager.level() instanceof ServerLevel level) {
                VillagerRecruitmentService.applyPartyFollowing(level, villager, party.leaderId());
            }
            affected++;
        }
        return affected;
    }

    private static int stayHere(ServerPlayer player, List<PartyVillagerRecord> records) {
        int affected = 0;
        for (PartyVillagerRecord record : records) {
            Villager villager = PartyEntityResolver.activeVillager(player.getServer(), record.villagerId());
            if (villager == null || !(villager.level() instanceof ServerLevel level)) {
                continue;
            }
            BlockPos target = villager.blockPosition().immutable();
            record.setStaying(level.dimension().location(), target);
            record.setRegrouping(false);
            clearMovementOrder(villager);
            MANUAL_ATTACK_TARGETS.remove(villager.getUUID());
            com.jvn.villagerretaliation.villager.VillagerRecoveryService.cancelForcedRecovery(villager);
            VillagerRetaliationHandler.clearCustomTarget(villager);
            VillagerRecruitmentService.applyPartyStay(level, villager, player.getUUID(), target);
            affected++;
        }
        return affected;
    }

    private static int setWeaponPreference(
            ServerPlayer player,
            List<PartyVillagerRecord> records,
            PartyWeaponPreference preference) {
        int affected = 0;
        for (PartyVillagerRecord record : records) {
            record.setWeaponPreference(preference);
            Villager villager = PartyEntityResolver.activeVillager(player.getServer(), record.villagerId());
            if (villager != null) {
                com.jvn.villagerretaliation.combat.VillagerCombatLoadoutService.applyPreference(villager, preference);
            }
            affected++;
        }
        return affected;
    }

    private static int heal(ServerPlayer player, List<PartyVillagerRecord> records) {
        int affected = 0;
        for (PartyVillagerRecord record : records) {
            Villager villager = PartyEntityResolver.activeVillager(player.getServer(), record.villagerId());
            if (villager == null || !villager.isAlive() || villager.getHealth() >= villager.getMaxHealth()) {
                continue;
            }
            boolean urgent = VillagerRetaliationHandler.hasActiveRetaliationTarget(villager);
            clearMovementOrder(villager);
            MANUAL_ATTACK_TARGETS.remove(villager.getUUID());
            VillagerRetaliationHandler.clearCustomTarget(villager);
            if (com.jvn.villagerretaliation.villager.VillagerRecoveryService.beginForcedRecovery(villager, urgent)) {
                record.setRegrouping(false);
                affected++;
            }
        }
        return affected;
    }

    private static int standGuard(ServerPlayer player, List<PartyVillagerRecord> records) {
        List<Villager> loaded = records.stream()
                .map(record -> PartyEntityResolver.activeVillager(player.serverLevel(), record.villagerId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        boolean lowerShields = loaded.stream()
                .anyMatch(villager -> STAND_GUARD_VILLAGERS.contains(villager.getUUID()));
        if (lowerShields) {
            int affected = 0;
            for (Villager villager : loaded) {
                if (STAND_GUARD_VILLAGERS.contains(villager.getUUID())) {
                    clearStandGuard(villager);
                    affected++;
                }
            }
            return affected;
        }
        int affected = 0;
        for (Villager villager : loaded) {
            VillagerRetaliationHandler.clearCustomTarget(villager);
            STAND_GUARD_VILLAGERS.add(villager.getUUID());
            affected++;
        }
        return affected;
    }

    private static void tickMoveTo(
            ServerLevel level,
            Villager villager,
            RuntimeOrder order,
            PartyRecord party) {
        BlockPos target = order.targetPosition();
        if (target == null || villager.distanceToSqr(
                target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D) <= ARRIVAL_DISTANCE_SQR) {
            clearMovementOrderAndSync(villager, party);
            return;
        }
        VillagerRetaliationHandler.clearCustomTarget(villager);
        HiredRouteNavigator.NodeMovement movement = moveAlongSharedRoute(
                level,
                villager,
                order,
                target,
                MOVE_SPEED,
                ARRIVAL_DISTANCE_SQR);
        if (movement == HiredRouteNavigator.NodeMovement.ARRIVED) {
            clearMovementOrderAndSync(villager, party);
        }
    }

    private static HiredRouteNavigator.NodeMovement moveAlongSharedRoute(
            ServerLevel level,
            Villager villager,
            RuntimeOrder order,
            BlockPos target,
            double speed,
            double arrivalDistanceSqr) {
        long gameTime = level.getGameTime();
        if (gameTime < order.nextPathRefreshGameTime()) {
            return HiredRouteNavigator.NodeMovement.FAILED;
        }
        VillagerTaskNavigationUtil.enableHiredWaterTraversal(villager);
        HiredRouteNavigator.NodeMovement movement = HiredRouteNavigator.moveToRouteNode(
                level,
                villager,
                target,
                speed,
                arrivalDistanceSqr);
        if (movement == HiredRouteNavigator.NodeMovement.FAILED) {
            order.setNextPathRefreshGameTime(gameTime + PATH_REFRESH_TICKS);
        }
        return movement;
    }

    private static void finishRegroup(
            ServerLevel level,
            Villager villager,
            PartyRecord party) {
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (record != null) {
            record.setRegrouping(false);
        }
        PartyService.markChanged(level);
        clearMovementOrderAndSync(villager, party);
    }

    private static void tickRegroup(
            ServerLevel level,
            Villager villager,
            RuntimeOrder order,
            PartyRecord party) {
        ServerPlayer commander = level.getServer().getPlayerList().getPlayer(order.commanderId());
        if (commander == null || commander.serverLevel() != level || !commander.isAlive()) {
            clearMovementOrder(villager);
            return;
        }
        if (villager.distanceToSqr(commander) <= REGROUP_ARRIVAL_DISTANCE_SQR) {
            finishRegroup(level, villager, party);
            return;
        }
        VillagerRetaliationHandler.clearCustomTarget(villager);
        HiredRouteNavigator.NodeMovement movement = moveAlongSharedRoute(
                level,
                villager,
                order,
                commander.blockPosition(),
                REGROUP_SPEED,
                REGROUP_ARRIVAL_DISTANCE_SQR);
        if (movement == HiredRouteNavigator.NodeMovement.ARRIVED) {
            finishRegroup(level, villager, party);
        }
    }

    private static void tickPickUpDrops(
            ServerLevel level,
            Villager villager,
            RuntimeOrder order,
            PartyRecord party) {
        Entity drop = order.activeEntityTarget() == null
                ? null
                : level.getEntity(order.activeEntityTarget());
        if (!isGatherableDrop(villager, drop, order)) {
            order.clearActiveTarget();
            drop = nearestUnclaimedDrop(level, villager, order);
            if (drop == null) {
                clearMovementOrderAndSync(villager, party);
                return;
            }
            order.setActiveEntityTarget(drop.getUUID());
        }

        if (villager.distanceToSqr(drop) <= ITEM_PICKUP_DISTANCE_SQR) {
            if (drop instanceof ItemEntity item && item.hasPickUpDelay()) {
                if (order.incrementTargetWaitTicks() > MAX_PICKUP_WAIT_TICKS) {
                    order.skipEntity(item.getUUID());
                    order.clearActiveTarget();
                }
                return;
            }
            int countBefore = drop instanceof ItemEntity item ? item.getItem().getCount() : 1;
            int moved = drop instanceof ItemEntity item
                    ? PartyVillagerDropCollection.collectAny(villager, item)
                    : PartyVillagerDropCollection.collectArrow(villager, (AbstractArrow) drop);
            order.clearActiveTarget();
            if (moved <= 0 || moved < countBefore && drop.isAlive()) {
                clearMovementOrderAndSync(villager, party);
            }
            return;
        }

        boolean attemptedPathRefresh = level.getGameTime() >= order.nextPathRefreshGameTime();
        HiredRouteNavigator.NodeMovement movement = moveAlongSharedRoute(
                level,
                villager,
                order,
                drop.blockPosition(),
                GATHER_SPEED,
                ITEM_PICKUP_DISTANCE_SQR);
        if (attemptedPathRefresh
                && movement == HiredRouteNavigator.NodeMovement.FAILED
                && order.incrementPathFailures() >= MAX_BACKGROUND_PATH_FAILURES) {
            order.skipEntity(drop.getUUID());
            order.clearActiveTarget();
        }
    }

    private static Entity nearestUnclaimedDrop(ServerLevel level, Villager villager, RuntimeOrder order) {
        BlockPos center = order.targetPosition();
        if (center == null) {
            return null;
        }
        AABB search = new AABB(center).inflate(
                DROP_SEARCH_HORIZONTAL_RADIUS,
                DROP_SEARCH_VERTICAL_RADIUS,
                DROP_SEARCH_HORIZONTAL_RADIUS);
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, search, candidate ->
                isGatherableDrop(villager, candidate, order)
                        && !isDropClaimedByOther(villager.getUUID(), candidate.getUUID()))) {
            double distance = villager.distanceToSqr(item);
            if (distance < nearestDistance) {
                nearest = item;
                nearestDistance = distance;
            }
        }
        for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, search, candidate ->
                isGatherableDrop(villager, candidate, order)
                        && !isDropClaimedByOther(villager.getUUID(), candidate.getUUID()))) {
            double distance = villager.distanceToSqr(arrow);
            if (distance < nearestDistance) {
                nearest = arrow;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static boolean isGatherableDrop(Villager villager, Entity drop, RuntimeOrder order) {
        return drop != null
                && drop.isAlive()
                && !order.skippedEntities().contains(drop.getUUID())
                && (drop instanceof ItemEntity item && !item.getItem().isEmpty()
                || drop instanceof AbstractArrow arrow
                && PartyVillagerDropCollection.isRecoverableArrow(villager, arrow));
    }

    private static boolean isDropClaimedByOther(UUID villagerId, UUID itemId) {
        for (Map.Entry<UUID, RuntimeOrder> entry : RUNTIME_ORDERS.entrySet()) {
            if (!entry.getKey().equals(villagerId)
                    && itemId.equals(entry.getValue().activeEntityTarget())) {
                return true;
            }
        }
        return false;
    }

    private static void tickLootContainers(
            ServerLevel level,
            Villager villager,
            RuntimeOrder order,
            PartyRecord party) {
        if (order.activeBlockTarget() == null) {
            List<BlockPos> available = order.containerTargets().stream()
                    .filter(pos -> !order.visitedBlocks().contains(pos))
                    .filter(pos -> !isContainerClaimedByOther(villager.getUUID(), pos))
                    .filter(pos -> PartyContainerLootService.isAvailable(level, pos))
                    .toList();
            HiredPathResult result = new HiredMoveToBlockFaceJob(level, villager, available, 16).search();
            if (result.target() == null) {
                clearMovementOrderAndSync(villager, party);
                return;
            }
            order.setActiveBlockTarget(result.target().blockPos(), result.target().approachPos());
        }

        BlockPos containerPos = order.activeBlockTarget();
        ServerPlayer commander = order.commanderId() == null
                ? null
                : level.getServer().getPlayerList().getPlayer(order.commanderId());
        PartyContainerLootService.LootResult lootResult =
                PartyContainerLootService.loot(level, villager, containerPos, commander);
        if (lootResult != PartyContainerLootService.LootResult.OUT_OF_REACH) {
            order.visitBlock(containerPos);
            order.clearActiveTarget();
            if (lootResult == PartyContainerLootService.LootResult.FULL) {
                clearMovementOrderAndSync(villager, party);
            }
            return;
        }

        BlockPos approach = order.approachPosition();
        boolean attemptedPathRefresh = level.getGameTime() >= order.nextPathRefreshGameTime();
        HiredRouteNavigator.NodeMovement movement = moveAlongSharedRoute(
                level,
                villager,
                order,
                approach,
                GATHER_SPEED,
                ARRIVAL_DISTANCE_SQR);
        if (attemptedPathRefresh
                && movement == HiredRouteNavigator.NodeMovement.FAILED
                && order.incrementPathFailures() >= MAX_BACKGROUND_PATH_FAILURES) {
            order.visitBlock(containerPos);
            order.clearActiveTarget();
        }
    }

    private static boolean isContainerClaimedByOther(UUID villagerId, BlockPos containerPos) {
        for (Map.Entry<UUID, RuntimeOrder> entry : RUNTIME_ORDERS.entrySet()) {
            if (!entry.getKey().equals(villagerId)
                    && containerPos.equals(entry.getValue().activeBlockTarget())) {
                return true;
            }
        }
        return false;
    }

    private static void releaseRegroupSuppressionIfArrived(
            ServerLevel level,
            Villager villager,
            PartyRecord party,
            PartyVillagerRecord record) {
        ServerPlayer leader = level.getServer().getPlayerList().getPlayer(party.leaderId());
        if (leader == null || leader.serverLevel() != level
                || villager.distanceToSqr(leader) > REGROUP_ARRIVAL_DISTANCE_SQR) {
            return;
        }
        record.setRegrouping(false);
        PartyService.markChanged(level);
        syncRuntimeState(villager, party);
    }

    private static void tickStandGuard(Villager villager, PartyRecord party) {
        if (villager.getTarget() != null || villager.getLastHurtByMob() != null || villager.isAggressive()) {
            clearStandGuardAndSync(villager, party);
            return;
        }
        if (villager.getOffhandItem().is(Items.SHIELD)) {
            villager.startUsingItem(InteractionHand.OFF_HAND);
        } else if (villager.getMainHandItem().is(Items.SHIELD)) {
            villager.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    private static void clearMovementOrder(UUID villagerId) {
        RUNTIME_ORDERS.remove(villagerId);
    }

    private static void clearMovementOrder(Villager villager) {
        if (RUNTIME_ORDERS.remove(villager.getUUID()) == null) {
            return;
        }
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
        HiredPathMemory.clearNavigationProgress(villager);
    }

    private static void clearStandGuard(Villager villager) {
        if (STAND_GUARD_VILLAGERS.remove(villager.getUUID())
                && villager.isUsingItem()
                && (villager.getUsedItemHand() == InteractionHand.OFF_HAND
                || villager.getUsedItemHand() == InteractionHand.MAIN_HAND)) {
            villager.stopUsingItem();
        }
    }

    private static void clearAllOrders(UUID villagerId) {
        clearMovementOrder(villagerId);
        MANUAL_ATTACK_TARGETS.remove(villagerId);
        STAND_GUARD_VILLAGERS.remove(villagerId);
    }

    private static void clearAllOrders(Villager villager) {
        clearMovementOrder(villager);
        MANUAL_ATTACK_TARGETS.remove(villager.getUUID());
        clearStandGuard(villager);
    }

    private static void clearMovementOrderAndSync(Villager villager, PartyRecord party) {
        clearMovementOrder(villager);
        syncRuntimeState(villager, party);
    }

    private static void clearStandGuardAndSync(Villager villager, PartyRecord party) {
        clearStandGuard(villager);
        syncRuntimeState(villager, party);
    }

    private static void syncRuntimeState(Villager villager, PartyRecord party) {
        if (party != null && villager.level().getServer() != null) {
            PartySyncService.syncParty(villager.level().getServer(), party.id());
        }
    }

    private static BlockPos validatedTarget(ServerPlayer player, BlockPos requested) {
        BlockPos center = validatedSearchCenter(player, requested);
        return center == null ? null : findStandablePosition(player.serverLevel(), center);
    }

    private static BlockPos validatedSearchCenter(ServerPlayer player, BlockPos requested) {
        if (requested == null || !player.serverLevel().isInWorldBounds(requested)
                || player.distanceToSqr(
                        requested.getX() + 0.5D,
                        requested.getY() + 0.5D,
                        requested.getZ() + 0.5D) > MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE
                || !player.serverLevel().isLoaded(requested)) {
            return null;
        }
        return requested.immutable();
    }

    private static BlockPos findStandablePosition(ServerLevel level, BlockPos requested) {
        for (int offset : new int[]{0, 1, -1, 2, -2}) {
            BlockPos candidate = requested.offset(0, offset, 0);
            if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()
                    && !level.getBlockState(candidate.below()).getCollisionShape(level, candidate.below()).isEmpty()) {
                return candidate.immutable();
            }
        }
        return null;
    }

    private static PartyRecord commandedParty(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        PartyRecord party = PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).orElse(null);
        return party != null && party.leaderId().equals(player.getUUID()) ? party : null;
    }

    private static boolean targetRequired(PartyQuickCommand command) {
        return command == PartyQuickCommand.ATTACK
                || command == PartyQuickCommand.MOVE_TO
                || command == PartyQuickCommand.LOOT_CONTAINERS;
    }

    private static String translationKey(PartyQuickCommand command) {
        return "villagerretaliation.party.quick_command." + command.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static void notice(ServerPlayer player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    private enum RuntimeOrderType {
        MOVE_TO,
        REGROUP,
        PICK_UP_DROPS,
        LOOT_CONTAINERS;

        boolean background() {
            return this == PICK_UP_DROPS || this == LOOT_CONTAINERS;
        }
    }

    private static final class RuntimeOrder {
        private final RuntimeOrderType type;
        private final BlockPos targetPosition;
        private final UUID commanderId;
        private final ResourceLocation targetDimension;
        private final List<BlockPos> containerTargets;
        private final Set<BlockPos> visitedBlocks = new HashSet<>();
        private final Set<UUID> skippedEntities = new HashSet<>();
        private UUID activeEntityTarget;
        private BlockPos activeBlockTarget;
        private BlockPos approachPosition;
        private long nextPathRefreshGameTime;
        private int pathFailures;
        private int targetWaitTicks;

        private RuntimeOrder(
                RuntimeOrderType type,
                BlockPos targetPosition,
                UUID commanderId,
                ResourceLocation targetDimension,
                List<BlockPos> containerTargets) {
            this.type = type;
            this.targetPosition = targetPosition;
            this.commanderId = commanderId;
            this.targetDimension = targetDimension;
            this.containerTargets = containerTargets == null ? List.of() : List.copyOf(containerTargets);
        }

        static RuntimeOrder moveTo(BlockPos target, ResourceLocation dimension) {
            return new RuntimeOrder(RuntimeOrderType.MOVE_TO, target.immutable(), null, dimension, List.of());
        }

        static RuntimeOrder regroup(UUID commanderId) {
            return new RuntimeOrder(RuntimeOrderType.REGROUP, null, commanderId, null, List.of());
        }

        static RuntimeOrder pickUpDrops(BlockPos center, ResourceLocation dimension) {
            return new RuntimeOrder(RuntimeOrderType.PICK_UP_DROPS, center.immutable(), null, dimension, List.of());
        }

        static RuntimeOrder lootContainers(
                BlockPos center,
                UUID commanderId,
                ResourceLocation dimension,
                List<BlockPos> containers) {
            return new RuntimeOrder(
                    RuntimeOrderType.LOOT_CONTAINERS,
                    center.immutable(),
                    commanderId,
                    dimension,
                    containers);
        }

        RuntimeOrderType type() {
            return this.type;
        }

        BlockPos targetPosition() {
            return this.targetPosition;
        }

        UUID commanderId() {
            return this.commanderId;
        }

        ResourceLocation targetDimension() {
            return this.targetDimension;
        }

        long nextPathRefreshGameTime() {
            return this.nextPathRefreshGameTime;
        }

        void setNextPathRefreshGameTime(long nextPathRefreshGameTime) {
            this.nextPathRefreshGameTime = nextPathRefreshGameTime;
        }

        List<BlockPos> containerTargets() {
            return this.containerTargets;
        }

        Set<BlockPos> visitedBlocks() {
            return this.visitedBlocks;
        }

        Set<UUID> skippedEntities() {
            return this.skippedEntities;
        }

        UUID activeEntityTarget() {
            return this.activeEntityTarget;
        }

        void setActiveEntityTarget(UUID target) {
            this.activeEntityTarget = target;
            resetTargetProgress();
        }

        BlockPos activeBlockTarget() {
            return this.activeBlockTarget;
        }

        BlockPos approachPosition() {
            return this.approachPosition;
        }

        void setActiveBlockTarget(BlockPos target, BlockPos approach) {
            this.activeBlockTarget = target == null ? null : target.immutable();
            this.approachPosition = approach == null ? null : approach.immutable();
            resetTargetProgress();
        }

        int incrementPathFailures() {
            return ++this.pathFailures;
        }

        int incrementTargetWaitTicks() {
            return ++this.targetWaitTicks;
        }

        void skipEntity(UUID entityId) {
            if (entityId != null) {
                this.skippedEntities.add(entityId);
            }
        }

        void visitBlock(BlockPos pos) {
            if (pos != null) {
                this.visitedBlocks.add(pos.immutable());
            }
        }

        void clearActiveTarget() {
            this.activeEntityTarget = null;
            this.activeBlockTarget = null;
            this.approachPosition = null;
            resetTargetProgress();
        }

        private void resetTargetProgress() {
            this.pathFailures = 0;
            this.targetWaitTicks = 0;
            this.nextPathRefreshGameTime = 0L;
        }
    }
}
