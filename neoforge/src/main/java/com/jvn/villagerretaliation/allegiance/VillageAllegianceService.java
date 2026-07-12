package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.village.VillageScopeKeys;
import java.util.ArrayList;
import java.util.HashMap;
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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class VillageAllegianceService {
    private static final int MAX_ASSIGNMENT_ATTEMPTS = 5;
    private static final long RETRY_INTERVAL_TICKS = 20L;
    private static final long LIFECYCLE_REFRESH_TICKS = 200L;
    private static final Map<UUID, PendingAssignment> PENDING = new HashMap<>();
    private static long migratedKnown;
    private static long migratedUnknown;
    private static long migratedUnaffiliated;
    private static long nextLifecycleRefresh;

    private VillageAllegianceService() {
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!AllegianceEntityClassifier.bearsAllegiance(entity)) {
            return;
        }
        Optional<VillageAllegianceData> existing = VillageAllegianceApi.get(entity);
        if (existing.filter(data -> data.dataVersion() >= VillageAllegianceData.CURRENT_VERSION).isPresent()) {
            normalizeAndTrack(level, entity, existing.get());
            return;
        }

        // Version 1 and missing payloads intentionally reset from the entity's current load position.
        VillageAllegianceEntityData.clear(entity);
        AllegianceAssignmentSource source = event.loadedFromDisk()
                ? AllegianceAssignmentSource.MIGRATION
                : AllegianceAssignmentSource.NATURAL_SPAWN;
        if (entity instanceof WanderingTrader || AllegianceEntityClassifier.classify(entity)
                == AllegianceEntityClassifier.Classification.NEUTRAL_TRADER) {
            assignUnaffiliated(level, entity, source);
            return;
        }
        if (entity instanceof IronGolem golem && golem.isPlayerCreated()) {
            assignUnaffiliated(level, entity, source);
            return;
        }
        if (entity instanceof ZombieVillager) {
            assignUnknown(level, entity, source);
            return;
        }
        BlockPos evidencePosition = entity.blockPosition().immutable();
        if (tryResolve(level, entity, source, false, evidencePosition)) {
            return;
        }
        PENDING.put(entity.getUUID(), new PendingAssignment(
                level.dimension().location().toString(), evidencePosition, source,
                0, level.getServer().overworld().getGameTime() + RETRY_INTERVAL_TICKS));
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
                assignUnaffiliated(level, entity, pending.source());
                completed.add(entry.getKey());
                continue;
            }
            Optional<VillageAllegianceData> explicit = VillageAllegianceApi.get(entity)
                    .filter(data -> data.dataVersion() >= VillageAllegianceData.CURRENT_VERSION);
            if (explicit.isPresent()) {
                normalizeAndTrack(level, entity, explicit.get());
                completed.add(entry.getKey());
                continue;
            }
            int attempts = pending.attempts() + 1;
            boolean finalAttempt = attempts >= MAX_ASSIGNMENT_ATTEMPTS;
            if (tryResolve(level, entity, pending.source(), finalAttempt, pending.initialPosition())) {
                completed.add(entry.getKey());
            } else {
                PENDING.put(entry.getKey(), pending.withAttempt(attempts, gameTime + RETRY_INTERVAL_TICKS));
            }
        }
        completed.forEach(PENDING::remove);

        if (gameTime >= nextLifecycleRefresh) {
            nextLifecycleRefresh = gameTime + LIFECYCLE_REFRESH_TICKS;
            for (ServerLevel level : server.getAllLevels()) {
                VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
                registry.refreshLoadedLifecycles(level, LIFECYCLE_REFRESH_TICKS);
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof Villager villager) {
                        VillageAllegianceApi.get(villager).ifPresent(data -> normalizeAndTrack(level, villager, data));
                    }
                }
            }
        }
    }

    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getChild() instanceof Villager child)
                || !(event.getParentA().level() instanceof ServerLevel level)) {
            return;
        }
        if (!tryResolve(level, child, AllegianceAssignmentSource.BIRTH, true, child.blockPosition())) {
            assignUnaffiliated(level, child, AllegianceAssignmentSource.BIRTH);
        }
    }

    public static void onLivingConversionPost(LivingConversionEvent.Post event) {
        Entity source = event.getEntity();
        Entity outcome = event.getOutcome();
        if (!(outcome.level() instanceof ServerLevel level) || !AllegianceEntityClassifier.bearsAllegiance(outcome)) {
            return;
        }
        VillageAllegianceData sourceData = VillageAllegianceApi.get(source)
                .filter(data -> data.dataVersion() >= VillageAllegianceData.CURRENT_VERSION)
                .orElse(null);
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        registry.removeResidentEverywhere(source.getUUID());
        if (sourceData != null) {
            VillageAllegianceEntityData.copy(source, outcome,
                    outcome instanceof Villager ? AllegianceAssignmentSource.CURE_COPY : AllegianceAssignmentSource.EXPLICIT_API);
            VillageAllegianceApi.get(outcome).ifPresent(data -> normalizeAndTrack(level, outcome, data));
            return;
        }
        VillageAllegianceEntityData.clear(outcome);
        if (!tryResolve(level, outcome, AllegianceAssignmentSource.CURE_INFERRED, true, outcome.blockPosition())) {
            assignUnaffiliated(level, outcome, AllegianceAssignmentSource.CURE_INFERRED);
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            VillageAllegianceRegistrySavedData.get(level).removeResidentEverywhere(event.getEntity().getUUID());
            PENDING.remove(event.getEntity().getUUID());
        }
    }

    public static boolean reassignToCurrentVillage(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return false;
        }
        Optional<VillageAllegianceId> current = VillageAllegianceRegistrySavedData.get(level)
                .discoverAt(level, villager.blockPosition());
        if (current.isEmpty()) {
            return false;
        }
        assignKnown(level, villager, current.get(), AllegianceAssignmentSource.TRUST_REASSIGNMENT,
                villager.blockPosition());
        return true;
    }

    public static boolean retryMigration(ServerLevel level, Entity entity) {
        VillageAllegianceEntityData.clear(entity);
        return tryResolve(level, entity, AllegianceAssignmentSource.ADMIN, true, entity.blockPosition());
    }

    public static MigrationStatistics statistics() {
        return new MigrationStatistics(migratedKnown, migratedUnknown, migratedUnaffiliated, PENDING.size());
    }

    public static void clearRuntimeState(MinecraftServer server) {
        PENDING.clear();
        migratedKnown = 0L;
        migratedUnknown = 0L;
        migratedUnaffiliated = 0L;
        nextLifecycleRefresh = 0L;
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
            boolean finalize,
            BlockPos evidencePosition) {
        if (!(entity instanceof Villager) && !(entity instanceof IronGolem)) {
            if (finalize) {
                assignUnknown(level, entity, source);
                return true;
            }
            return false;
        }
        Optional<VillageAllegianceId> village = VillageAllegianceRegistrySavedData.get(level)
                .discoverAt(level, evidencePosition);
        if (village.isPresent()) {
            assignKnown(level, entity, village.get(), source, evidencePosition);
            return true;
        }
        if (finalize) {
            assignUnaffiliated(level, entity, source);
            return true;
        }
        return false;
    }

    private static void assignKnown(
            ServerLevel level,
            Entity entity,
            VillageAllegianceId id,
            AllegianceAssignmentSource source,
            BlockPos evidencePosition) {
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId canonical = registry.canonical(id).orElse(id);
        registry.ensureRecord(canonical, level.getGameTime(), level.dimension().location(), evidencePosition);
        VillageAllegianceEntityData.write(entity, VillageAllegianceData.known(
                canonical,
                source,
                source == AllegianceAssignmentSource.MIGRATION
                        ? AllegianceConfidence.LEGACY_INFERRED
                        : AllegianceConfidence.AUTHORITATIVE,
                level.getGameTime(),
                level.dimension().location(),
                evidencePosition,
                List.of()));
        registry.removeResidentEverywhere(entity.getUUID());
        if (entity instanceof Villager villager) {
            registry.addOrUpdateResident(canonical, villager.getUUID(), !villager.isBaby(), level.getGameTime());
        }
        registry.canonicalRecord(canonical).ifPresent(record -> registry.addScopeCandidate(
                VillageScopeKeys.forPosition(record.originDimension(), record.originPosition()), canonical));
        migratedKnown++;
    }

    private static void normalizeAndTrack(ServerLevel level, Entity entity, VillageAllegianceData data) {
        if (!data.isKnown()) {
            VillageAllegianceRegistrySavedData.get(level).removeResidentEverywhere(entity.getUUID());
            return;
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> canonical = registry.canonical(data.primary());
        if (canonical.isEmpty()) {
            assignUnaffiliated(level, entity, AllegianceAssignmentSource.MIGRATION);
            return;
        }
        if (!canonical.get().equals(data.primary()) || data.dataVersion() < VillageAllegianceData.CURRENT_VERSION) {
            VillageAllegianceEntityData.write(entity, VillageAllegianceData.known(
                    canonical.get(), data.assignmentSource(), data.confidence(), data.assignedGameTime(),
                    data.originDimension(), data.originPosition(), List.of()));
        }
        if (entity instanceof Villager villager) {
            registry.removeResidentEverywhere(villager.getUUID());
            registry.addOrUpdateResident(canonical.get(), villager.getUUID(), !villager.isBaby(), level.getGameTime());
        }
    }

    private static void assignUnknown(ServerLevel level, Entity entity, AllegianceAssignmentSource source) {
        VillageAllegianceEntityData.write(entity, VillageAllegianceData.unknown(
                source,
                source == AllegianceAssignmentSource.MIGRATION
                        ? AllegianceConfidence.LEGACY_INFERRED
                        : AllegianceConfidence.AUTHORITATIVE,
                level.getGameTime(), level.dimension().location(), entity.blockPosition()));
        migratedUnknown++;
    }

    private static void assignUnaffiliated(ServerLevel level, Entity entity, AllegianceAssignmentSource source) {
        VillageAllegianceRegistrySavedData.get(level).removeResidentEverywhere(entity.getUUID());
        VillageAllegianceEntityData.write(entity, VillageAllegianceData.unaffiliated(
                source, level.getGameTime(), level.dimension().location(), entity.blockPosition()));
        migratedUnaffiliated++;
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
