package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.village.VillageMembership;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class VillageAllegianceService {
    private static final int MAX_ASSIGNMENT_ATTEMPTS = 5;
    private static final long RETRY_INTERVAL_TICKS = 20L;
    private static final Map<UUID, PendingAssignment> PENDING = new HashMap<>();
    private static long migratedKnown;
    private static long migratedUnknown;
    private static long migratedUnaffiliated;

    private VillageAllegianceService() {
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!AllegianceEntityClassifier.bearsAllegiance(entity) || VillageAllegianceApi.get(entity).isPresent()) {
            return;
        }
        if (entity instanceof WanderingTrader || AllegianceEntityClassifier.classify(entity)
                == AllegianceEntityClassifier.Classification.NEUTRAL_TRADER) {
            assignUnaffiliated(level, entity, event.loadedFromDisk() ? AllegianceAssignmentSource.MIGRATION : AllegianceAssignmentSource.NATURAL_SPAWN);
            return;
        }
        if (entity instanceof IronGolem golem && golem.isPlayerCreated()) {
            assignUnaffiliated(level, entity, AllegianceAssignmentSource.NATURAL_SPAWN);
            return;
        }
        if (entity instanceof ZombieVillager) {
            assignUnknown(level, entity, AllegianceAssignmentSource.MIGRATION);
            return;
        }
        AllegianceAssignmentSource source = event.loadedFromDisk()
                ? AllegianceAssignmentSource.MIGRATION
                : AllegianceAssignmentSource.NATURAL_SPAWN;
        if (tryResolve(level, entity, source, false)) {
            return;
        }
        PENDING.put(entity.getUUID(), new PendingAssignment(
                level.dimension().location().toString(), entity.blockPosition().immutable(), source,
                0, level.getGameTime() + RETRY_INTERVAL_TICKS));
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long gameTime = server.overworld().getGameTime();
        List<UUID> completed = new ArrayList<>();
        for (Map.Entry<UUID, PendingAssignment> entry : List.copyOf(PENDING.entrySet())) {
            PendingAssignment pending = entry.getValue();
            if (gameTime < pending.nextAttemptGameTime()) {
                continue;
            }
            Entity entity = findLoaded(server, entry.getKey());
            if (entity == null || !(entity.level() instanceof ServerLevel level)) {
                completed.add(entry.getKey());
                continue;
            }
            if (!level.dimension().location().toString().equals(pending.initialDimension())) {
                assignUnknown(level, entity, pending.source());
                completed.add(entry.getKey());
                continue;
            }
            int attempts = pending.attempts() + 1;
            boolean finalAttempt = attempts >= MAX_ASSIGNMENT_ATTEMPTS;
            if (tryResolve(level, entity, pending.source(), finalAttempt, pending.initialPosition()) || finalAttempt) {
                completed.add(entry.getKey());
            } else {
                PENDING.put(entry.getKey(), pending.withAttempt(attempts, gameTime + RETRY_INTERVAL_TICKS));
            }
        }
        completed.forEach(PENDING::remove);
    }

    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getParentA() instanceof Villager parentA)
                || !(event.getParentB() instanceof Villager parentB)
                || !(event.getChild() instanceof Villager child)
                || !(parentA.level() instanceof ServerLevel level)) {
            return;
        }
        VillageAllegianceData first = VillageAllegianceApi.get(parentA).orElseGet(() -> unknownFor(level, parentA, AllegianceAssignmentSource.BIRTH));
        VillageAllegianceData second = VillageAllegianceApi.get(parentB).orElseGet(() -> unknownFor(level, parentB, AllegianceAssignmentSource.BIRTH));
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> firstId = first.isKnown() ? registry.canonical(first.primary()) : Optional.empty();
        Optional<VillageAllegianceId> secondId = second.isKnown() ? registry.canonical(second.primary()) : Optional.empty();
        if (firstId.isPresent() && secondId.isPresent()) {
            List<VillageAllegianceId> parents = new ArrayList<>(List.of(firstId.get(), secondId.get()));
            parents.sort(Comparator.naturalOrder());
            VillageAllegianceId primary;
            if (firstId.get().equals(secondId.get())) {
                primary = firstId.get();
                parents = List.of();
            } else {
                primary = deterministicParent(parents, child.getUUID());
            }
            VillageAllegianceEntityData.write(child, VillageAllegianceData.known(
                    primary, AllegianceAssignmentSource.BIRTH, AllegianceConfidence.INHERITED,
                    level.getGameTime(), level.dimension().location(), child.blockPosition(), parents));
        } else if (firstId.isPresent() || secondId.isPresent()) {
            VillageAllegianceId primary = firstId.orElseGet(secondId::get);
            VillageAllegianceEntityData.write(child, VillageAllegianceData.known(
                    primary, AllegianceAssignmentSource.BIRTH, AllegianceConfidence.INHERITED,
                    level.getGameTime(), level.dimension().location(), child.blockPosition(), List.of(primary)));
        } else if (!tryResolve(level, child, AllegianceAssignmentSource.BIRTH, true)) {
            assignUnknown(level, child, AllegianceAssignmentSource.BIRTH);
        }
    }

    public static void onLivingConversionPost(LivingConversionEvent.Post event) {
        Entity source = event.getEntity();
        Entity outcome = event.getOutcome();
        if (!(source.level() instanceof ServerLevel) || !AllegianceEntityClassifier.bearsAllegiance(outcome)) {
            return;
        }
        VillageAllegianceEntityData.copy(source, outcome,
                outcome instanceof Villager ? AllegianceAssignmentSource.CURE_COPY : AllegianceAssignmentSource.EXPLICIT_API);
    }

    public static boolean retryMigration(ServerLevel level, Entity entity) {
        VillageAllegianceEntityData.clear(entity);
        return tryResolve(level, entity, AllegianceAssignmentSource.ADMIN, true);
    }

    public static MigrationStatistics statistics() {
        return new MigrationStatistics(migratedKnown, migratedUnknown, migratedUnaffiliated, PENDING.size());
    }

    public static void clearRuntimeState(MinecraftServer server) {
        PENDING.clear();
        migratedKnown = 0L;
        migratedUnknown = 0L;
        migratedUnaffiliated = 0L;
        if (server != null) {
            VillageAllegianceRegistrySavedData.get(server.overworld()).clearRuntimeCache();
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        clearRuntimeState(event.getServer());
    }

    private static boolean tryResolve(
            ServerLevel level,
            Entity entity,
            AllegianceAssignmentSource source,
            boolean finalizeUnknown) {
        return tryResolve(level, entity, source, finalizeUnknown, entity.blockPosition());
    }

    private static boolean tryResolve(
            ServerLevel level,
            Entity entity,
            AllegianceAssignmentSource source,
            boolean finalizeUnknown,
            BlockPos evidencePosition) {
        if (VillageAllegianceApi.get(entity).isPresent()) {
            return true;
        }
        if (!(entity instanceof Villager villager) && !(entity instanceof IronGolem)) {
            if (finalizeUnknown) {
                assignUnknown(level, entity, source);
                return true;
            }
            return false;
        }
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        if (entity instanceof Villager villager) {
            VillagerSocialGraphService.knownVillage(level, villager.getUUID())
                    .map(VillageScopeKeys::fromSavedSocialKey)
                    .filter(value -> !value.isBlank())
                    .ifPresent(scopes::add);
            boolean recruitedLegacyVillager = source == AllegianceAssignmentSource.MIGRATION
                    && PartyService.isRecruitedPartyVillager(level, villager.getUUID());
            if (!recruitedLegacyVillager) {
                VillageMembership.resolve(level, evidencePosition)
                        .map(area -> VillageScopeKeys.forArea(level, area))
                        .filter(value -> !value.isBlank())
                        .ifPresent(scopes::add);
            }
        } else {
            VillageMembership.resolve(level, evidencePosition)
                    .map(area -> VillageScopeKeys.forArea(level, area))
                    .filter(value -> !value.isBlank())
                    .ifPresent(scopes::add);
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        LinkedHashSet<VillageAllegianceId> candidates = new LinkedHashSet<>();
        for (String scope : scopes) {
            candidates.addAll(registry.candidates(scope));
        }
        VillageAllegianceId resolved = null;
        if (candidates.size() == 1) {
            resolved = candidates.getFirst();
        } else if (candidates.isEmpty() && scopes.size() == 1) {
            resolved = registry.create(level.getGameTime(), level.dimension().location(), evidencePosition, "");
            registry.addScopeCandidate(scopes.getFirst(), resolved);
        }
        if (resolved != null) {
            VillageAllegianceEntityData.write(entity, VillageAllegianceData.known(
                    resolved, source,
                    source == AllegianceAssignmentSource.MIGRATION
                            ? AllegianceConfidence.LEGACY_INFERRED
                            : AllegianceConfidence.AUTHORITATIVE,
                    level.getGameTime(), level.dimension().location(), evidencePosition, List.of()));
            migratedKnown++;
            return true;
        }
        if (finalizeUnknown || candidates.size() > 1 || scopes.size() > 1) {
            assignUnknown(level, entity, source);
            return true;
        }
        return false;
    }

    private static void assignUnknown(ServerLevel level, Entity entity, AllegianceAssignmentSource source) {
        VillageAllegianceEntityData.write(entity, unknownFor(level, entity, source));
        migratedUnknown++;
    }

    private static VillageAllegianceData unknownFor(ServerLevel level, Entity entity, AllegianceAssignmentSource source) {
        return VillageAllegianceData.unknown(
                source, source == AllegianceAssignmentSource.MIGRATION
                        ? AllegianceConfidence.LEGACY_INFERRED
                        : AllegianceConfidence.AUTHORITATIVE,
                level.getGameTime(), level.dimension().location(), entity.blockPosition());
    }

    private static void assignUnaffiliated(ServerLevel level, Entity entity, AllegianceAssignmentSource source) {
        VillageAllegianceEntityData.write(entity, VillageAllegianceData.unaffiliated(
                source, level.getGameTime(), level.dimension().location(), entity.blockPosition()));
        migratedUnaffiliated++;
    }

    private static VillageAllegianceId deterministicParent(List<VillageAllegianceId> sorted, UUID childId) {
        long hash = childId.getMostSignificantBits() ^ childId.getLeastSignificantBits();
        return sorted.get(Math.floorMod(Long.hashCode(hash), sorted.size()));
    }

    private static Entity findLoaded(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private record PendingAssignment(
            String initialDimension,
            BlockPos initialPosition,
            AllegianceAssignmentSource source,
            int attempts,
            long nextAttemptGameTime) {
        private PendingAssignment withAttempt(int attempts, long nextAttemptGameTime) {
            return new PendingAssignment(this.initialDimension, this.initialPosition, this.source, attempts, nextAttemptGameTime);
        }
    }

    public record MigrationStatistics(long known, long unknown, long unaffiliated, int pending) {
    }
}
