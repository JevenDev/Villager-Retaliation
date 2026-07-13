package com.jvn.villagerretaliation.allegiance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
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
    private static final long RETRY_INTERVAL_TICKS = 20L;
    private static final long MAX_RETRY_INTERVAL_TICKS = 200L;
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
            VillageAllegianceData data = existing.get();
            normalizeAndTrack(level, entity, data);
            if (data.state() == AllegianceState.UNKNOWN) {
                VillageAllegianceEntityData.readPending(entity).ifPresent(pending -> PENDING.put(
                        entity.getUUID(), PendingAssignment.from(pending)));
            }
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
        schedulePending(level, entity, evidencePosition, source, 0,
                level.getServer().overworld().getGameTime() + RETRY_INTERVAL_TICKS);
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
            if (!level.dimension().location().equals(pending.initialDimension())) {
                pending = new PendingAssignment(
                        level.dimension().location(), entity.blockPosition(), pending.source(),
                        pending.attempts(), gameTime);
            }
            Optional<VillageAllegianceData> explicit = VillageAllegianceApi.get(entity)
                    .filter(data -> data.dataVersion() >= VillageAllegianceData.CURRENT_VERSION);
            if (explicit.isPresent() && explicit.get().state() != AllegianceState.UNKNOWN) {
                normalizeAndTrack(level, entity, explicit.get());
                completed.add(entry.getKey());
                continue;
            }
            int attempts = pending.attempts() + 1;
            if (tryResolve(level, entity, pending.source(), false, pending.initialPosition())) {
                completed.add(entry.getKey());
            } else {
                long delay = Math.min(MAX_RETRY_INTERVAL_TICKS, RETRY_INTERVAL_TICKS * Math.max(1L, attempts));
                PendingAssignment updated = pending.withAttempt(attempts, gameTime + delay);
                PENDING.put(entry.getKey(), updated);
                VillageAllegianceEntityData.writePending(entity, updated.toData());
            }
        }
        completed.forEach(PENDING::remove);

        if (gameTime >= nextLifecycleRefresh) {
            nextLifecycleRefresh = gameTime + LIFECYCLE_REFRESH_TICKS;
            for (ServerLevel level : server.getAllLevels()) {
                VillageAllegianceRegistrySavedData.get(level).refreshLoadedLifecycles(level, LIFECYCLE_REFRESH_TICKS);
            }
            VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(server.overworld());
            for (UUID residentId : registry.residentIds()) {
                Entity entity = findLoaded(server, residentId);
                if (entity instanceof Villager villager && villager.level() instanceof ServerLevel level) {
                    VillageAllegianceApi.get(villager).ifPresent(data -> normalizeAndTrack(level, villager, data));
                }
            }
        }
    }

    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getChild() instanceof Villager child)
                || !(event.getParentA().level() instanceof ServerLevel level)) {
            return;
        }
        assignBirthAllegiance(level, child, event.getParentA(), event.getParentB());
    }

    /** Applies deterministic parent and physical-village evidence to a newborn villager. */
    public static void assignBirthAllegiance(
            ServerLevel level,
            Villager child,
            Entity firstParent,
            Entity secondParent) {
        List<VillageAllegianceId> parents = Stream.of(firstParent, secondParent)
                .filter(java.util.Objects::nonNull)
                .map(parent -> VillageAllegianceApi.canonicalPrimary(level, parent))
                .flatMap(Optional::stream)
                .distinct()
                .sorted()
                .toList();
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> discovered = registry.discoverAt(level, child.blockPosition());
        VillageAssignmentResolution resolution = VillageAssignmentResolver.resolve(
                level, child, child.blockPosition(), discovered, parents);
        if (resolution.status() == VillageAssignmentResolution.Status.RESOLVED) {
            assignKnown(level, child, resolution.selected(), AllegianceAssignmentSource.BIRTH,
                    child.blockPosition(), AllegianceConfidence.INHERITED, parents);
            return;
        }
        if (resolution.status() == VillageAssignmentResolution.Status.NONE
                && resolution.observationComplete() && parents.isEmpty()) {
            assignUnaffiliated(level, child, AllegianceAssignmentSource.BIRTH);
            return;
        }
        assignUnknown(level, child, AllegianceAssignmentSource.BIRTH,
                AllegianceConfidence.INHERITED, parents);
        if (resolution.status() != VillageAssignmentResolution.Status.NONE || !resolution.observationComplete()) {
            schedulePending(level, child, child.blockPosition(), AllegianceAssignmentSource.BIRTH, 0,
                    level.getServer().overworld().getGameTime() + RETRY_INTERVAL_TICKS);
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
        if (!tryResolve(level, outcome, AllegianceAssignmentSource.CURE_INFERRED, false, outcome.blockPosition())) {
            schedulePending(level, outcome, outcome.blockPosition(), AllegianceAssignmentSource.CURE_INFERRED, 0,
                    level.getServer().overworld().getGameTime() + RETRY_INTERVAL_TICKS);
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
        if (tryResolve(level, entity, AllegianceAssignmentSource.ADMIN, false, entity.blockPosition())) {
            return true;
        }
        schedulePending(level, entity, entity.blockPosition(), AllegianceAssignmentSource.ADMIN, 0,
                level.getServer().overworld().getGameTime() + RETRY_INTERVAL_TICKS);
        return false;
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
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> discovered = registry.discoverAt(level, evidencePosition);
        List<VillageAllegianceId> protectedParents = VillageAllegianceApi.get(entity)
                .map(VillageAllegianceData::protectedParents)
                .orElse(List.of());
        if (entity instanceof Villager villager && !villager.isBaby()
                && source == AllegianceAssignmentSource.BIRTH && !protectedParents.isEmpty()) {
            protectedParents = List.of();
            assignUnknown(level, entity, source, AllegianceConfidence.INHERITED, protectedParents);
        }
        VillageAssignmentResolution resolution = VillageAssignmentResolver.resolve(
                level, entity, evidencePosition, discovered, protectedParents);
        if (resolution.status() == VillageAssignmentResolution.Status.RESOLVED) {
            assignKnown(level, entity, resolution.selected(), source, evidencePosition,
                    source == AllegianceAssignmentSource.BIRTH
                            ? AllegianceConfidence.INHERITED
                            : source == AllegianceAssignmentSource.MIGRATION
                                    ? AllegianceConfidence.LEGACY_INFERRED
                                    : AllegianceConfidence.AUTHORITATIVE,
                    protectedParents);
            return true;
        }
        if (resolution.status() == VillageAssignmentResolution.Status.NONE && resolution.observationComplete()) {
            assignUnaffiliated(level, entity, source);
            return true;
        }
        if (VillageAllegianceApi.get(entity).isEmpty()) {
            assignUnknown(level, entity, source);
        }
        return false;
    }

    private static void assignKnown(
            ServerLevel level,
            Entity entity,
            VillageAllegianceId id,
            AllegianceAssignmentSource source,
            BlockPos evidencePosition) {
        assignKnown(level, entity, id, source, evidencePosition,
                source == AllegianceAssignmentSource.MIGRATION
                        ? AllegianceConfidence.LEGACY_INFERRED
                        : AllegianceConfidence.AUTHORITATIVE,
                List.of());
    }

    private static void assignKnown(
            ServerLevel level,
            Entity entity,
            VillageAllegianceId id,
            AllegianceAssignmentSource source,
            BlockPos evidencePosition,
            AllegianceConfidence confidence,
            List<VillageAllegianceId> protectedParents) {
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId canonical = registry.canonical(id).orElse(id);
        registry.ensureRecord(canonical, level.getGameTime(), level.dimension().location(), evidencePosition);
        VillageAllegianceEntityData.write(entity, VillageAllegianceData.known(
                canonical,
                source,
                confidence,
                level.getGameTime(),
                level.dimension().location(),
                evidencePosition,
                protectedParents));
        VillageAllegianceEntityData.clearPending(entity);
        PENDING.remove(entity.getUUID());
        if (entity instanceof Villager villager) {
            registry.addOrUpdateResident(canonical, villager.getUUID(), !villager.isBaby(), level.getGameTime());
        }
        migratedKnown++;
    }

    private static void normalizeAndTrack(ServerLevel level, Entity entity, VillageAllegianceData data) {
        if (entity instanceof Villager villager && !villager.isBaby()
                && data.assignmentSource() == AllegianceAssignmentSource.BIRTH
                && !data.protectedParents().isEmpty()) {
            data = data.isKnown()
                    ? VillageAllegianceData.known(
                            data.primary(), data.assignmentSource(), data.confidence(), data.assignedGameTime(),
                            data.originDimension(), data.originPosition(), List.of())
                    : VillageAllegianceData.unknown(
                            data.assignmentSource(), data.confidence(), data.assignedGameTime(),
                            data.originDimension(), data.originPosition(), List.of());
            VillageAllegianceEntityData.write(entity, data);
        }
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
            registry.addOrUpdateResident(canonical.get(), villager.getUUID(), !villager.isBaby(), level.getGameTime());
        }
    }

    private static void assignUnknown(ServerLevel level, Entity entity, AllegianceAssignmentSource source) {
        assignUnknown(level, entity, source,
                source == AllegianceAssignmentSource.MIGRATION
                        ? AllegianceConfidence.LEGACY_INFERRED
                        : AllegianceConfidence.AUTHORITATIVE,
                List.of());
    }

    private static void assignUnknown(
            ServerLevel level,
            Entity entity,
            AllegianceAssignmentSource source,
            AllegianceConfidence confidence,
            List<VillageAllegianceId> protectedParents) {
        VillageAllegianceEntityData.write(entity, VillageAllegianceData.unknown(
                source,
                confidence,
                level.getGameTime(), level.dimension().location(), entity.blockPosition(), protectedParents));
        migratedUnknown++;
    }

    private static void assignUnaffiliated(ServerLevel level, Entity entity, AllegianceAssignmentSource source) {
        VillageAllegianceRegistrySavedData.get(level).removeResidentEverywhere(entity.getUUID());
        VillageAllegianceEntityData.write(entity, VillageAllegianceData.unaffiliated(
                source, level.getGameTime(), level.dimension().location(), entity.blockPosition()));
        VillageAllegianceEntityData.clearPending(entity);
        PENDING.remove(entity.getUUID());
        migratedUnaffiliated++;
    }

    private static void schedulePending(
            ServerLevel level,
            Entity entity,
            BlockPos evidencePosition,
            AllegianceAssignmentSource source,
            int attempts,
            long nextAttemptGameTime) {
        if (VillageAllegianceApi.get(entity).isEmpty()) {
            assignUnknown(level, entity, source);
        }
        PendingAssignment pending = new PendingAssignment(
                level.dimension().location(), evidencePosition.immutable(), source, attempts, nextAttemptGameTime);
        PENDING.put(entity.getUUID(), pending);
        VillageAllegianceEntityData.writePending(entity, pending.toData());
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
            net.minecraft.resources.ResourceLocation initialDimension,
            BlockPos initialPosition,
            AllegianceAssignmentSource source,
            int attempts,
            long nextAttemptGameTime) {
        private PendingAssignment withAttempt(int attempts, long nextAttemptGameTime) {
            return new PendingAssignment(this.initialDimension, this.initialPosition, this.source, attempts, nextAttemptGameTime);
        }

        private VillageAllegianceEntityData.PendingAssignmentData toData() {
            return new VillageAllegianceEntityData.PendingAssignmentData(
                    this.initialDimension, this.initialPosition, this.source, this.attempts, this.nextAttemptGameTime);
        }

        private static PendingAssignment from(VillageAllegianceEntityData.PendingAssignmentData data) {
            return new PendingAssignment(
                    data.dimension(), data.position(), data.source(), data.attempts(), data.nextAttemptGameTime());
        }
    }

    public record MigrationStatistics(long known, long unknown, long unaffiliated, int pending) {
    }
}
