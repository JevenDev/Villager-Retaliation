package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData.RelationshipType;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementDepartureService {
    private static final String WALK_TAG = "VillagerRetaliationLeavingSettlement";

    private SettlementDepartureService() {
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 200L != 0L) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (SettlementRecord town : SettlementSavedData.get(level).settlements()) {
                if (town.dimension().equals(level.dimension().location())) {
                    process(level, town, now);
                }
            }
        }
    }

    static List<UUID> process(ServerLevel level, SettlementRecord town, long now) {
        if (now < town.nextDepartureAt()) {
            return List.of();
        }
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var members = registry.residentRoster(town.id(), now).stream()
                .collect(Collectors.toMap(VillageAllegianceRegistrySavedData.ResidentRecord::id, resident -> resident));
        boolean housingShortage = town.housingCapacity() < members.size();
        List<SettlementResidentState> candidates = town.residents().values().stream()
                .filter(resident -> members.containsKey(resident.id()) && resident.age() >= 0 && resident.canDepart())
                .filter(resident -> resident.shortageTicks() >= graceTicks() && (housingShortage || resident.food() < 6))
                .sorted(Comparator.comparingLong(SettlementResidentState::shortageTicks).reversed()
                        .thenComparing(SettlementResidentState::id)).toList();
        for (SettlementResidentState candidate : candidates) {
            Set<UUID> family = family(level, members, candidate.id());
            if (family.stream().anyMatch(id -> !town.residents().containsKey(id) || !town.residents().get(id).canDepart())) {
                continue;
            }
            var reason = candidate.food() < 6 ? SettlementDeparture.Reason.FOOD : SettlementDeparture.Reason.HOUSING;
            for (UUID id : family) {
                var state = town.residents().get(id);
                var departure = new SettlementDeparture(id, VillagerSocialGraphSavedData.get(level).knownName(id).orElse("Resident"),
                        now, reason, SettlementResidentState.load(state.save()));
                town.recordDeparture(departure);
                town.removeDuty(id);
                town.removeResident(id);
                registry.removeResident(town.id(), id);
                if (level.getEntity(id) instanceof Villager villager) {
                    apply(level, villager, town, departure);
                }
            }
            town.scheduleDeparture(now + 24_000L);
            return List.copyOf(family);
        }
        return List.of();
    }

    private static Set<UUID> family(ServerLevel level,
                                   Map<UUID, VillageAllegianceRegistrySavedData.ResidentRecord> members, UUID resident) {
        var graph = VillagerSocialGraphSavedData.get(level);
        Set<UUID> family = new LinkedHashSet<>();
        var pending = new ArrayDeque<UUID>();
        pending.add(resident);
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            if (!members.containsKey(current) || !family.add(current)) {
                continue;
            }
            graph.relationships(current, RelationshipType.SPOUSE).stream().filter(members::containsKey).forEach(pending::add);
            graph.relationships(current, RelationshipType.CHILD).stream()
                    .filter(id -> members.containsKey(id) && !members.get(id).adult()).forEach(pending::add);
            if (!members.get(current).adult()) {
                graph.relationships(current, RelationshipType.PARENT).stream().filter(members::containsKey).forEach(pending::add);
            }
        }
        return family;
    }

    public static long graceTicks() {
        return Math.clamp(VillagerRetaliationConfig.SETTLEMENT_DEPARTURE_GRACE_DAYS.get(), 1, 30) * 24_000L;
    }

    public static void onActive(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !villager.isAlive()) {
            return;
        }
        var town = SettlementWorkforceService.home(level, villager).orElse(null);
        if (town != null) {
            var departure = town.departures().get(villager.getUUID());
            if (departure != null) {
                apply(level, villager, town, departure);
            }
        }
        walkAway(level, villager);
    }

    private static void apply(ServerLevel level, Villager villager, SettlementRecord town, SettlementDeparture departure) {
        departure.care().restore(villager);
        VillageAllegianceApi.assignUnaffiliated(level, villager, AllegianceAssignmentSource.SETTLEMENT);
        SettlementWorkforceService.reconcile(level, villager);
        for (var memory : List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE,
                MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT)) {
            villager.releasePoi(memory);
            villager.getBrain().eraseMemory(memory);
        }
        CompoundTag walk = new CompoundTag();
        walk.putLong("Center", town.center().asLong());
        walk.putInt("Radius", town.stage().radius() + 16);
        villager.getPersistentData().put(WALK_TAG, walk);
    }

    public static void onAssignment(ServerLevel level, Entity entity, VillageAllegianceData data) {
        if (!(entity instanceof Villager) || data == null || !data.isKnown()) {
            return;
        }
        boolean explicit = switch (data.assignmentSource()) {
            case ADMIN, COMMAND, QUEST, EXPLICIT_API, TRUST_REASSIGNMENT -> true;
            default -> false;
        };
        if (explicit) {
            SettlementSavedData.get(level).get(data.primary()).ifPresent(town -> town.removeDeparture(entity.getUUID()));
            entity.getPersistentData().remove(WALK_TAG);
        }
    }

    private static void walkAway(ServerLevel level, Villager villager) {
        CompoundTag walk = villager.getPersistentData().getCompound(WALK_TAG);
        if (walk.isEmpty()) {
            return;
        }
        BlockPos center = BlockPos.of(walk.getLong("Center"));
        int radius = walk.getInt("Radius");
        if (VillageAllegianceApi.canonicalPrimary(level, villager).isPresent()
                || HiredVillagerContractService.hasActiveOrPendingContract(villager)
                || PartyVillagerContractService.hasPartyEntityReference(villager)
                || VillagerRecruitmentService.isFollowingAnyPlayer(villager)
                || Math.abs(villager.getX() - center.getX()) > radius || Math.abs(villager.getZ() - center.getZ()) > radius) {
            villager.getPersistentData().remove(WALK_TAG);
            return;
        }
        if (Math.floorMod(level.getGameTime() + villager.getUUID().getLeastSignificantBits(), 40L) != 0L
                || villager.isNoAi() || villager.isSleeping() || villager.isTrading()
                || villager.getTarget() != null || VillagerDownedService.isDowned(villager)) {
            return;
        }
        var target = LandRandomPos.getPosAway(villager, 12, 4, center.getCenter());
        if (target != null && level.hasChunkAt(BlockPos.containing(target))) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 1));
            villager.getNavigation().moveTo(target.x, target.y, target.z, 0.5D);
        }
    }
}
