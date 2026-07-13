package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

public final class PartyQuickCommandService {
    private static final double MAX_TARGET_DISTANCE = 64.0D;
    private static final double ATTACK_TARGET_DISTANCE = 96.0D;
    private static final double ARRIVAL_DISTANCE_SQR = 1.75D * 1.75D;
    private static final double FALL_BACK_ARRIVAL_DISTANCE_SQR = 2.5D * 2.5D;
    private static final double MOVE_SPEED = 0.72D;
    private static final double FALL_BACK_SPEED = 0.9D;
    private static final long PATH_REFRESH_TICKS = 8L;

    private static final Map<UUID, RuntimeOrder> RUNTIME_ORDERS = new HashMap<>();
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
            case FOLLOW_ME -> followMe(player, party, participants);
            case STAY_HERE -> stayHere(player, party, participants, payload.targetPosition());
            case FALL_BACK -> fallBack(player, participants);
            case STAND_GUARD -> standGuard(player, participants);
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
            Villager loaded = loadedVillager(player.getServer(), villagerId);
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
        if (order == null && !standingGuard) {
            return;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (record == null || !record.quickCommandsEnabled() || !villager.isAlive()) {
            clearAllOrders(villager);
            return;
        }

        if (order != null) {
            switch (order.type()) {
                case MOVE_TO -> tickMoveTo(villager, order, party);
                case FALL_BACK -> tickFallBack(level, villager, order, party);
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
        return order != null && (order.type() == RuntimeOrderType.MOVE_TO
                || order.type() == RuntimeOrderType.FALL_BACK);
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
        STAND_GUARD_VILLAGERS.clear();
    }

    private static int attack(ServerPlayer player, List<PartyVillagerRecord> records, int entityId) {
        Entity entity = entityId == PartyQuickCommandRequestPayload.NO_ENTITY
                ? null
                : player.serverLevel().getEntity(entityId);
        if (!(entity instanceof LivingEntity target)
                || !target.isAlive()
                || target == player
                || player.distanceToSqr(target) > ATTACK_TARGET_DISTANCE * ATTACK_TARGET_DISTANCE
                || !player.hasLineOfSight(target)) {
            return 0;
        }
        int affected = 0;
        for (PartyVillagerRecord record : records) {
            Villager villager = loadedVillager(player.serverLevel(), record.villagerId());
            if (villager == null) {
                continue;
            }
            clearAllOrders(villager);
            if (VillagerRetaliationHandler.engageCustomTarget(villager, target, false)) {
                affected++;
            }
        }
        return affected;
    }

    private static int moveTo(ServerPlayer player, List<PartyVillagerRecord> records, BlockPos requestedTarget) {
        BlockPos target = validatedTarget(player, requestedTarget);
        if (target == null) {
            return 0;
        }
        int affected = 0;
        for (PartyVillagerRecord record : records) {
            Villager villager = loadedVillager(player.serverLevel(), record.villagerId());
            if (villager == null) {
                continue;
            }
            VillagerRetaliationHandler.clearCustomTarget(villager);
            RUNTIME_ORDERS.put(villager.getUUID(), RuntimeOrder.moveTo(
                    target,
                    player.serverLevel().dimension().location()));
            affected++;
        }
        return affected;
    }

    private static int followMe(
            ServerPlayer player,
            PartyRecord party,
            List<PartyVillagerRecord> records) {
        for (PartyVillagerRecord record : records) {
            record.setFollowing();
            Villager villager = loadedVillager(player.getServer(), record.villagerId());
            if (villager != null && villager.level() instanceof ServerLevel level) {
                clearMovementOrder(villager.getUUID());
                VillagerRecruitmentService.applyPartyFollowing(level, villager, party.leaderId());
            } else {
                clearMovementOrder(record.villagerId());
            }
        }
        return records.size();
    }

    private static int stayHere(
            ServerPlayer player,
            PartyRecord party,
            List<PartyVillagerRecord> records,
            BlockPos requestedTarget) {
        BlockPos target = validatedTarget(player, requestedTarget);
        if (target == null) {
            return 0;
        }
        for (PartyVillagerRecord record : records) {
            record.setStaying(player.serverLevel().dimension().location(), target);
            Villager villager = loadedVillager(player.serverLevel(), record.villagerId());
            if (villager != null) {
                clearMovementOrder(villager.getUUID());
                VillagerRetaliationHandler.clearCustomTarget(villager);
                VillagerRecruitmentService.applyPartyStay(
                        player.serverLevel(), villager, party.leaderId(), target);
            } else {
                clearMovementOrder(record.villagerId());
            }
        }
        return records.size();
    }

    private static int fallBack(ServerPlayer player, List<PartyVillagerRecord> records) {
        int affected = 0;
        for (PartyVillagerRecord record : records) {
            Villager villager = loadedVillager(player.serverLevel(), record.villagerId());
            if (villager == null) {
                continue;
            }
            VillagerRetaliationHandler.clearCustomTarget(villager);
            RUNTIME_ORDERS.put(villager.getUUID(), RuntimeOrder.fallBack(player.getUUID()));
            affected++;
        }
        return affected;
    }

    private static int standGuard(ServerPlayer player, List<PartyVillagerRecord> records) {
        List<Villager> loaded = records.stream()
                .map(record -> loadedVillager(player.serverLevel(), record.villagerId()))
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

    private static void tickMoveTo(Villager villager, RuntimeOrder order, PartyRecord party) {
        BlockPos target = order.targetPosition();
        if (target == null || villager.distanceToSqr(
                target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D) <= ARRIVAL_DISTANCE_SQR) {
            clearMovementOrderAndSync(villager, party);
            return;
        }
        VillagerRetaliationHandler.clearCustomTarget(villager);
        refreshPath(villager, order, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, MOVE_SPEED);
    }

    private static void tickFallBack(
            ServerLevel level,
            Villager villager,
            RuntimeOrder order,
            PartyRecord party) {
        ServerPlayer commander = level.getServer().getPlayerList().getPlayer(order.commanderId());
        if (commander == null || commander.serverLevel() != level || !commander.isAlive()) {
            clearMovementOrderAndSync(villager, party);
            return;
        }
        if (villager.distanceToSqr(commander) <= FALL_BACK_ARRIVAL_DISTANCE_SQR) {
            clearMovementOrderAndSync(villager, party);
            return;
        }
        VillagerRetaliationHandler.clearCustomTarget(villager);
        refreshPath(villager, order, commander.getX(), commander.getY(), commander.getZ(), FALL_BACK_SPEED);
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

    private static void refreshPath(
            Villager villager,
            RuntimeOrder order,
            double x,
            double y,
            double z,
            double speed) {
        long gameTime = villager.level().getGameTime();
        if (!villager.getNavigation().isDone() && gameTime < order.nextPathRefreshGameTime()) {
            return;
        }
        VillagerRetaliationVillagerBrainUtil.clearPathingMemories(villager);
        villager.getNavigation().moveTo(x, y, z, speed);
        order.setNextPathRefreshGameTime(gameTime + PATH_REFRESH_TICKS);
    }

    private static void clearMovementOrder(UUID villagerId) {
        RUNTIME_ORDERS.remove(villagerId);
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
        STAND_GUARD_VILLAGERS.remove(villagerId);
    }

    private static void clearAllOrders(Villager villager) {
        clearMovementOrder(villager.getUUID());
        clearStandGuard(villager);
    }

    private static void clearMovementOrderAndSync(Villager villager, PartyRecord party) {
        clearMovementOrder(villager.getUUID());
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
        if (requested == null || !player.serverLevel().isInWorldBounds(requested)
                || player.distanceToSqr(
                        requested.getX() + 0.5D,
                        requested.getY() + 0.5D,
                        requested.getZ() + 0.5D) > MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE
                || !player.serverLevel().isLoaded(requested)) {
            return null;
        }
        return findStandablePosition(player.serverLevel(), requested);
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

    private static Villager loadedVillager(ServerLevel level, UUID villagerId) {
        Entity entity = level == null ? null : level.getEntity(villagerId);
        return entity instanceof Villager villager && villager.isAlive() ? villager : null;
    }

    private static Villager loadedVillager(MinecraftServer server, UUID villagerId) {
        return loadedVillagerFromAnyServerLevel(server, villagerId);
    }

    private static Villager loadedVillagerFromAnyServerLevel(MinecraftServer server, UUID villagerId) {
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Villager villager = loadedVillager(level, villagerId);
            if (villager != null) {
                return villager;
            }
        }
        return null;
    }

    private static boolean targetRequired(PartyQuickCommand command) {
        return command == PartyQuickCommand.ATTACK
                || command == PartyQuickCommand.MOVE_TO
                || command == PartyQuickCommand.STAY_HERE;
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
        FALL_BACK
    }

    private static final class RuntimeOrder {
        private final RuntimeOrderType type;
        private final BlockPos targetPosition;
        private final UUID commanderId;
        private final ResourceLocation targetDimension;
        private long nextPathRefreshGameTime;

        private RuntimeOrder(
                RuntimeOrderType type,
                BlockPos targetPosition,
                UUID commanderId,
                ResourceLocation targetDimension) {
            this.type = type;
            this.targetPosition = targetPosition;
            this.commanderId = commanderId;
            this.targetDimension = targetDimension;
        }

        static RuntimeOrder moveTo(BlockPos target, ResourceLocation dimension) {
            return new RuntimeOrder(RuntimeOrderType.MOVE_TO, target.immutable(), null, dimension);
        }

        static RuntimeOrder fallBack(UUID commanderId) {
            return new RuntimeOrder(RuntimeOrderType.FALL_BACK, null, commanderId, null);
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
    }
}
