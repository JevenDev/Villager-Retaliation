package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.compat.rideon.VillagerRideOnCompat;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.network.VillagerMountTargetModePayload;
import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartySyncService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerMountAssignmentService {
    public static final int TARGET_MODE_TICKS = 20 * 30;
    private static final Map<UUID, PendingTarget> PENDING_TARGETS = new HashMap<>();

    private VillagerMountAssignmentService() {
    }

    public static boolean featureAvailable() {
        return true;
    }

    public static Optional<VillagerMountAssignment> assignment(ServerLevel level, UUID villagerId) {
        return level == null || villagerId == null
                ? Optional.empty()
                : VillagerMountAssignmentSavedData.get(level).forVillager(villagerId);
    }

    public static boolean hasAssignment(ServerLevel level, UUID villagerId) {
        return assignment(level, villagerId).isPresent();
    }

    public static Optional<VillagerMountAssignment> assignmentForMount(ServerLevel level, UUID mountId) {
        return level == null || mountId == null
                ? Optional.empty()
                : VillagerMountAssignmentSavedData.get(level).forMount(mountId);
    }

    public static List<VillagerMountAssignment> assignmentsForMount(ServerLevel level, UUID mountId) {
        return level == null || mountId == null
                ? List.of()
                : VillagerMountAssignmentSavedData.get(level).assignmentsForMount(mountId);
    }

    public static boolean canManage(ServerPlayer player, Villager villager) {
        if (player == null
                || villager == null
                || !(villager.level() instanceof ServerLevel level)
                || player.serverLevel() != level
                || !villager.isAlive()
                || villager.isBaby()) {
            return false;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        boolean partyLeader = party != null
                && party.villager(villager.getUUID()) != null
                && party.leaderId().equals(player.getUUID())
                && PartyVillagerContractService.isActivePartyVillager(level, villager);
        return partyLeader || HiredVillagerContractService.isHiredBy(level, villager, player);
    }

    public static boolean canRideAssignedMount(ServerPlayer player, Villager villager) {
        if (player == null
                || villager == null
                || !(villager.level() instanceof ServerLevel level)
                || player.serverLevel() != level
                || !villager.isAlive()) {
            return false;
        }
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        boolean partyMember = party != null
                && party.playerIds().contains(player.getUUID())
                && PartyVillagerContractService.isActivePartyVillager(level, villager);
        return partyMember || HiredVillagerContractService.isHiredBy(level, villager, player);
    }

    public static boolean structurallyEligible(ServerLevel level, Entity mount) {
        VillagerMountAdapter adapter = VillagerMountAdapters.find(mount);
        return adapter != null && adapter.structurallyEligible(level, mount);
    }

    public static boolean isEligibleCandidate(ServerLevel level, Entity mount) {
        if (!featureAvailable() || !structurallyEligible(level, mount) || !mount.getPassengers().isEmpty()) {
            return false;
        }
        VillagerMountAdapter adapter = VillagerMountAdapters.find(mount);
        return adapter != null
                && VillagerMountAssignmentSavedData.get(level).assignmentsForMount(mount.getUUID()).size()
                < adapter.seatCapacity(mount);
    }

    public static AssignmentResult startTargeting(ServerPlayer player, Villager villager) {
        if (!featureAvailable()) {
            notice(player, "villagerretaliation.mount.unavailable");
            return AssignmentResult.UNAVAILABLE;
        }
        if (!canManage(player, villager)) {
            notice(player, "villagerretaliation.mount.unauthorized");
            return AssignmentResult.UNAUTHORIZED;
        }
        if (hasAssignment(player.serverLevel(), villager.getUUID())) {
            notice(player, "villagerretaliation.mount.already_assigned");
            return AssignmentResult.VILLAGER_ALREADY_ASSIGNED;
        }
        long now = player.getServer().overworld().getGameTime();
        PENDING_TARGETS.put(player.getUUID(), new PendingTarget(villager.getUUID(), now + TARGET_MODE_TICKS));
        VillagerConversationService.endForPlayer(player, true);
        sendTargetMode(player, true, villager.getId(), TARGET_MODE_TICKS);
        notice(player, "villagerretaliation.mount.target_started");
        return AssignmentResult.SUCCESS;
    }

    public static AssignmentResult unassign(ServerPlayer player, Villager villager) {
        if (!canManage(player, villager)) {
            notice(player, "villagerretaliation.mount.unauthorized");
            return AssignmentResult.UNAUTHORIZED;
        }
        if (!clearAssignment(player.serverLevel(), villager.getUUID())) {
            notice(player, "villagerretaliation.mount.none_assigned");
            return AssignmentResult.NO_ASSIGNMENT;
        }
        notice(player, "villagerretaliation.mount.unassigned");
        return AssignmentResult.SUCCESS;
    }

    public static boolean clearAssignment(ServerLevel level, UUID villagerId) {
        if (level == null || villagerId == null) {
            return false;
        }
        VillagerMountAssignmentSavedData data = VillagerMountAssignmentSavedData.get(level);
        VillagerMountAssignment assignment = data.forVillager(villagerId).orElse(null);
        if (assignment == null) {
            return false;
        }
        data.removeForVillager(villagerId);
        releaseAssignment(level.getServer(), assignment);
        if (data.assignmentsForMount(assignment.mountId()).isEmpty()) {
            VillagerMountTravelService.releaseRestriction(level.getServer(), assignment);
        }
        syncPartyIfPresent(level, villagerId);
        return true;
    }

    public static boolean clearAssignmentForMount(ServerLevel level, UUID mountId) {
        if (level == null || mountId == null) {
            return false;
        }
        VillagerMountAssignmentSavedData data = VillagerMountAssignmentSavedData.get(level);
        List<VillagerMountAssignment> assignments = data.removeForMount(mountId);
        if (assignments.isEmpty()) {
            return false;
        }
        for (VillagerMountAssignment assignment : assignments) {
            releaseAssignment(level.getServer(), assignment);
            syncPartyIfPresent(level, assignment.villagerId());
        }
        VillagerMountTravelService.releaseRestriction(level.getServer(), assignments.getFirst());
        return true;
    }

    public static void onEntityPermanentlyRemoved(Entity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (entity instanceof Villager villager) {
            clearAssignment(level, villager.getUUID());
        } else {
            clearAssignmentForMount(level, entity.getUUID());
        }
    }

    public static AssignmentResult assign(
            ServerPlayer player,
            Villager villager,
            Entity mount) {
        if (!featureAvailable()) {
            return AssignmentResult.UNAVAILABLE;
        }
        if (!canManage(player, villager)) {
            return AssignmentResult.UNAUTHORIZED;
        }
        ServerLevel level = player.serverLevel();
        VillagerMountAssignmentSavedData data = VillagerMountAssignmentSavedData.get(level);
        if (data.forVillager(villager.getUUID()).isPresent()) {
            return AssignmentResult.VILLAGER_ALREADY_ASSIGNED;
        }
        if (!structurallyEligible(level, mount)) {
            return AssignmentResult.INVALID_MOUNT;
        }
        VillagerMountAdapter adapter = VillagerMountAdapters.find(mount);
        if (adapter == null) {
            return AssignmentResult.INVALID_MOUNT;
        }
        List<VillagerMountAssignment> mountAssignments = data.assignmentsForMount(mount.getUUID());
        if (mountAssignments.size() >= adapter.seatCapacity(mount)) {
            return AssignmentResult.MOUNT_ALREADY_ASSIGNED;
        }
        Set<UUID> assignedVillagers = new HashSet<>();
        mountAssignments.forEach(assignment -> assignedVillagers.add(assignment.villagerId()));
        assignedVillagers.add(villager.getUUID());
        if (adapter.hasUnrelatedPassengers(mount, assignedVillagers)) {
            return AssignmentResult.INVALID_MOUNT;
        }
        VillagerMountAssignment assignment = new VillagerMountAssignment(
                villager.getUUID(),
                mount.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()),
                level.dimension().location(),
                mount.blockPosition().immutable(),
                level.dimension().location(),
                mount.blockPosition().immutable(),
                level.getServer().overworld().getGameTime()
        );
        if (!data.assign(assignment)) {
            return AssignmentResult.VILLAGER_ALREADY_ASSIGNED;
        }
        syncPartyIfPresent(level, villager.getUUID());
        return AssignmentResult.SUCCESS;
    }

    /** Runs before the normal villager interaction handler. */
    public static boolean handleEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return false;
        }
        if (event.getTarget() instanceof AbstractHorse mount && PENDING_TARGETS.containsKey(player.getUUID())) {
            handleTargetClick(player, mount);
            consume(event);
            return true;
        }
        if (event.getTarget() instanceof AbstractHorse mount && tryTakeAssignedDriverSeat(player, mount)) {
            consume(event);
            return true;
        }
        if (event.getTarget() instanceof AbstractHorse mount
                && VillagerRideOnCompat.available()
                && !player.isSecondaryUseActive()
                && event.getItemStack().isEmpty()
                && VillagerRideOnCompat.occupant(mount, false) instanceof Villager driver
                && assignment(player.serverLevel(), driver.getUUID())
                        .filter(assigned -> assigned.mountId().equals(mount.getUUID()))
                        .isPresent()) {
            // Ride On permits a player to join behind any living driver. Assigned mounts reserve
            // that interaction for VR's authorization-aware driver takeover instead of allowing
            // an unauthorized player to bypass the party contract by occupying the rear seat.
            if (!canRideAssignedMount(player, driver)) {
                notice(player, "villagerretaliation.mount.unauthorized");
            }
            consume(event);
            return true;
        }
        if (event.getTarget() instanceof Villager villager && tryAssignLeashedMount(player, villager)) {
            consume(event);
            return true;
        }
        return false;
    }

    public static boolean tryTakeAssignedDriverSeat(ServerPlayer player, AbstractHorse mount) {
        if (!featureAvailable() || player == null || mount == null || player.getVehicle() != null) {
            return false;
        }
        Entity driver = VillagerRideOnCompat.available()
                ? VillagerRideOnCompat.occupant(mount, false)
                : VanillaHorseMounting.rider(mount);
        if (!(driver instanceof Villager villager)
                || VillagerMountAssignmentSavedData.get(player.serverLevel())
                .forVillager(villager.getUUID())
                .filter(assignment -> assignment.mountId().equals(mount.getUUID()))
                .isEmpty()
                || !canRideAssignedMount(player, villager)) {
            return false;
        }
        return VillagerRideOnCompat.available()
                ? VillagerRideOnCompat.tryTakeDriverSeat(mount, player)
                : VanillaHorseMounting.tryTakeOver(mount, player);
    }

    public static void cancelTargeting(ServerPlayer player) {
        cancelTargeting(player, false);
    }

    public static void onServerTick(MinecraftServer server) {
        VillagerMountTravelService.onServerTick(server);
        if (server == null || PENDING_TARGETS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, PendingTarget>> iterator = PENDING_TARGETS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingTarget> entry = iterator.next();
            if (entry.getValue().expiresGameTime() > now) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                sendTargetMode(player, false, -1, 0);
                notice(player, "villagerretaliation.mount.target_timed_out");
            }
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_TARGETS.remove(event.getEntity().getUUID());
    }

    public static void clearRuntimeState() {
        PENDING_TARGETS.clear();
    }

    private static void handleTargetClick(ServerPlayer player, AbstractHorse mount) {
        PendingTarget pending = PENDING_TARGETS.get(player.getUUID());
        if (pending == null) {
            return;
        }
        long now = player.getServer().overworld().getGameTime();
        if (pending.expiresGameTime() <= now) {
            cancelTargeting(player, true);
            return;
        }
        Villager villager = VillagerEntityResolver.loaded(player.getServer(), pending.villagerId());
        if (villager == null || villager.level() != player.serverLevel() || !canManage(player, villager)) {
            cancelTargeting(player, false);
            notice(player, "villagerretaliation.mount.unauthorized");
            return;
        }
        AssignmentResult result = assign(player, villager, mount);
        if (result == AssignmentResult.SUCCESS) {
            PENDING_TARGETS.remove(player.getUUID());
            sendTargetMode(player, false, -1, 0);
            notice(player, "villagerretaliation.mount.assigned");
        } else {
            notice(player, messageKey(result));
        }
    }

    private static boolean tryAssignLeashedMount(ServerPlayer player, Villager villager) {
        if (!featureAvailable()
                || !canManage(player, villager)
                || hasAssignment(player.serverLevel(), villager.getUUID())) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        List<AbstractHorse> candidates = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AbstractHorse horse
                    && horse.getLeashHolder() == player
                    && isEligibleCandidate(level, horse)) {
                candidates.add(horse);
            }
        }
        if (candidates.size() != 1) {
            return false;
        }
        AbstractHorse mount = candidates.getFirst();
        if (assign(player, villager, mount) != AssignmentResult.SUCCESS) {
            return false;
        }
        mount.dropLeash(true, false);
        ItemStack lead = new ItemStack(Items.LEAD);
        if (!player.addItem(lead)) {
            player.drop(lead, false);
        }
        notice(player, "villagerretaliation.mount.assigned_from_lead");
        return true;
    }

    private static void cancelTargeting(ServerPlayer player, boolean timedOut) {
        if (player == null || PENDING_TARGETS.remove(player.getUUID()) == null) {
            return;
        }
        sendTargetMode(player, false, -1, 0);
        notice(player, timedOut
                ? "villagerretaliation.mount.target_timed_out"
                : "villagerretaliation.mount.target_cancelled");
    }

    private static void sendTargetMode(ServerPlayer player, boolean active, int villagerId, int ticks) {
        try {
            PacketDistributor.sendToPlayer(player,
                    new VillagerMountTargetModePayload(active, villagerId, ticks));
        } catch (UnsupportedOperationException ignored) {
            // Fake players in GameTests do not always negotiate custom payload channels.
        }
    }

    private static void notice(ServerPlayer player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    private static String messageKey(AssignmentResult result) {
        return switch (result) {
            case UNAVAILABLE -> "villagerretaliation.mount.unavailable";
            case UNAUTHORIZED -> "villagerretaliation.mount.unauthorized";
            case VILLAGER_ALREADY_ASSIGNED -> "villagerretaliation.mount.already_assigned";
            case MOUNT_ALREADY_ASSIGNED -> "villagerretaliation.mount.mount_already_assigned";
            case INVALID_MOUNT -> "villagerretaliation.mount.invalid";
            case NO_ASSIGNMENT -> "villagerretaliation.mount.none_assigned";
            case SUCCESS -> "villagerretaliation.mount.assigned";
        };
    }

    private static void syncPartyIfPresent(ServerLevel level, UUID villagerId) {
        PartyService.getPartyForVillager(level, villagerId)
                .ifPresent(party -> PartySyncService.syncParty(level.getServer(), party.id()));
    }

    private static void releaseAssignment(MinecraftServer server, VillagerMountAssignment assignment) {
        Entity mount = VillagerMountEntities.loaded(server, assignment.mountId());
        Entity villager = VillagerMountEntities.loaded(server, assignment.villagerId());
        VillagerMountAdapter adapter = VillagerMountAdapters.find(mount);
        if (adapter != null && villager instanceof Villager assignedVillager && villager.getVehicle() == mount) {
            adapter.tryDismount(mount, assignedVillager);
        }
    }

    private static void consume(PlayerInteractEvent.EntityInteract event) {
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public enum AssignmentResult {
        SUCCESS,
        UNAVAILABLE,
        UNAUTHORIZED,
        VILLAGER_ALREADY_ASSIGNED,
        MOUNT_ALREADY_ASSIGNED,
        INVALID_MOUNT,
        NO_ASSIGNMENT
    }

    private record PendingTarget(UUID villagerId, long expiresGameTime) {
    }
}
