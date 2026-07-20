package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class VillageAllegianceService {
    private static final long RETRY_INTERVAL_TICKS = 20L;
    private static final long MAX_RETRY_INTERVAL_TICKS = 200L;
    private static final int NATURAL_SPAWN_GRACE_ATTEMPTS = 5;
    private static final long WANDERER_CHECK_INTERVAL_TICKS = 200L;
    private static final long RESIDENT_REFRESH_INTERVAL_TICKS = 1_200L;
    private static final long LIFECYCLE_REFRESH_TICKS = 1_200L;
    private static final long LIFECYCLE_WORK_SPACING_TICKS = 5L;
    private static final int MAX_PENDING_CHECKS_PER_TICK = 8;
    private static final int MAX_VILLAGER_CHECKS_PER_TICK = 16;
    private static final int MAX_LIFECYCLE_CHECKS_PER_TICK = 1;
    private static final Map<UUID, PendingAssignment> PENDING = new HashMap<>();
    private static final PriorityQueue<PendingTask> PENDING_QUEUE = new PriorityQueue<>(
            Comparator.comparingLong((PendingTask task) -> task.pending().nextAttemptGameTime())
                    .thenComparing(PendingTask::entityId));
    private static final Map<UUID, ScheduledVillager> SCHEDULED_VILLAGERS = new HashMap<>();
    private static final PriorityQueue<ScheduledVillager> VILLAGER_QUEUE = new PriorityQueue<>(
            Comparator.comparingLong(ScheduledVillager::nextCheckGameTime)
                    .thenComparing(ScheduledVillager::villagerId));
    private static final ArrayDeque<LifecycleTask> LIFECYCLE_QUEUE = new ArrayDeque<>();
    private static final Set<LifecycleTask> QUEUED_LIFECYCLES = new HashSet<>();
    private static long migratedKnown;
    private static long migratedUnknown;
    private static long migratedUnaffiliated;
    private static long nextLifecycleRefresh;
    private static long nextLifecycleWork;

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
            if (entity instanceof Villager villager
                    && data.state() == AllegianceState.UNKNOWN
                    && data.assignmentSource() == AllegianceAssignmentSource.BIRTH
                    && !data.protectedParents().isEmpty()) {
                recoverLegacyBirthAssignment(level, villager, data);
                return;
            }
            normalizeAndTrack(level, entity, data);
            scheduleLoadedVillager(level, entity, data, false);
            if (data.state() == AllegianceState.UNKNOWN) {
                VillageAllegianceEntityData.readPending(entity).ifPresent(pending -> putPending(
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
        if (entity instanceof Villager villager && source == AllegianceAssignmentSource.NATURAL_SPAWN) {
            assignFreshVillager(level, villager, evidencePosition);
            return;
        }
        if (tryResolve(level, entity, source, false, evidencePosition)) {
            return;
        }
        schedulePending(level, entity, evidencePosition, source, 0,
                level.getServer().overworld().getGameTime() + RETRY_INTERVAL_TICKS);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long gameTime = server.overworld().getGameTime();
        processPendingAssignments(server, gameTime);
        processScheduledVillagers(server, gameTime);

        if (gameTime >= nextLifecycleRefresh) {
            nextLifecycleRefresh = gameTime + LIFECYCLE_REFRESH_TICKS;
            enqueueLifecycleRefreshes(server);
        }
        processLifecycleRefreshes(server, gameTime);
    }

    private static void processPendingAssignments(MinecraftServer server, long gameTime) {
        int processed = 0;
        while (processed < MAX_PENDING_CHECKS_PER_TICK
                && !PENDING_QUEUE.isEmpty()
                && PENDING_QUEUE.peek().pending().nextAttemptGameTime() <= gameTime) {
            PendingTask task = PENDING_QUEUE.remove();
            PendingAssignment pending = PENDING.get(task.entityId());
            if (pending == null || !pending.equals(task.pending())) {
                continue;
            }
            processed++;
            Entity entity = findLoaded(server, task.entityId());
            if (entity == null || !(entity.level() instanceof ServerLevel level)) {
                PENDING.remove(task.entityId());
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
                scheduleLoadedVillager(level, entity, explicit.get(), false);
                PENDING.remove(task.entityId());
                continue;
            }
            int attempts = pending.attempts() + 1;
            boolean deferUnaffiliated = pending.source() == AllegianceAssignmentSource.NATURAL_SPAWN
                    && attempts < NATURAL_SPAWN_GRACE_ATTEMPTS;
            if (tryResolve(level, entity, pending.source(), deferUnaffiliated, pending.initialPosition())) {
                PENDING.remove(task.entityId());
            } else {
                long delay = Math.min(MAX_RETRY_INTERVAL_TICKS, RETRY_INTERVAL_TICKS * Math.max(1L, attempts));
                PendingAssignment updated = pending.withAttempt(attempts, gameTime + delay);
                putPending(task.entityId(), updated);
                VillageAllegianceEntityData.writePending(entity, updated.toData());
            }
        }
    }

    private static void processScheduledVillagers(MinecraftServer server, long gameTime) {
        int processed = 0;
        while (processed < MAX_VILLAGER_CHECKS_PER_TICK
                && !VILLAGER_QUEUE.isEmpty()
                && VILLAGER_QUEUE.peek().nextCheckGameTime() <= gameTime) {
            ScheduledVillager scheduled = VILLAGER_QUEUE.remove();
            if (SCHEDULED_VILLAGERS.get(scheduled.villagerId()) != scheduled) {
                continue;
            }
            processed++;
            ServerLevel level = server.getLevel(scheduled.dimension());
            Entity entity = level == null ? null : level.getEntity(scheduled.villagerId());
            if (!(entity instanceof Villager villager)) {
                SCHEDULED_VILLAGERS.remove(scheduled.villagerId(), scheduled);
                continue;
            }
            VillageAllegianceData data = VillageAllegianceApi.get(villager).orElse(null);
            if (data == null || data.state() == AllegianceState.UNKNOWN) {
                SCHEDULED_VILLAGERS.remove(scheduled.villagerId(), scheduled);
                continue;
            }
            if (data.isKnown()) {
                normalizeAndTrack(level, villager, data);
            } else {
                updateWanderingResidency(level, villager, data);
            }
            VillageAllegianceData updated = VillageAllegianceApi.get(villager).orElse(data);
            scheduleLoadedVillager(level, villager, updated, false);
        }
    }

    private static void enqueueLifecycleRefreshes(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (VillageAllegianceRegistrySavedData.AllegianceRecord record
                    : VillageAllegianceRegistrySavedData.get(level).activeRecords(level.dimension().location())) {
                LifecycleTask task = new LifecycleTask(level.dimension(), record.id());
                if (QUEUED_LIFECYCLES.add(task)) {
                    LIFECYCLE_QUEUE.addLast(task);
                }
            }
        }
    }

    private static void processLifecycleRefreshes(MinecraftServer server, long gameTime) {
        if (gameTime < nextLifecycleWork) {
            return;
        }
        int processed = 0;
        while (processed < MAX_LIFECYCLE_CHECKS_PER_TICK && !LIFECYCLE_QUEUE.isEmpty()) {
            LifecycleTask task = LIFECYCLE_QUEUE.removeFirst();
            QUEUED_LIFECYCLES.remove(task);
            ServerLevel level = server.getLevel(task.dimension());
            if (level != null) {
                VillageAllegianceRegistrySavedData.get(level).refreshLoadedLifecycle(
                        level, task.villageId(), LIFECYCLE_REFRESH_TICKS);
            }
            processed++;
        }
        if (processed > 0) {
            nextLifecycleWork = gameTime + LIFECYCLE_WORK_SPACING_TICKS;
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
                .toList();
        Optional<VillageAllegianceId> discovered = activeVillageAt(level, child.blockPosition());
        if (discovered.isPresent()) {
            assignKnown(level, child, discovered.get(), AllegianceAssignmentSource.BIRTH,
                    child.blockPosition(), AllegianceConfidence.INHERITED, parents);
            return;
        }
        if (!parents.isEmpty()) {
            assignKnown(level, child, parents.getFirst(), AllegianceAssignmentSource.BIRTH,
                    child.blockPosition(), AllegianceConfidence.INHERITED, parents);
            return;
        }
        assignUnaffiliated(level, child, AllegianceAssignmentSource.BIRTH);
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
            unscheduleVillager(event.getEntity().getUUID());
        }
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            UUID entityId = event.getEntity().getUUID();
            PENDING.remove(entityId);
            unscheduleVillager(entityId);
        }
    }

    public static boolean reassignToCurrentVillage(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return false;
        }
        Optional<VillageAllegianceId> current = activeVillageAt(level, villager.blockPosition());
        if (current.isEmpty()) {
            return false;
        }
        assignKnown(level, villager, current.get(), AllegianceAssignmentSource.TRUST_REASSIGNMENT,
                villager.blockPosition());
        return true;
    }

    /**
     * Advances a Wanderer's settlement clock. A continuous day in one active village makes
     * that village home, unless the villager is traveling as a party member.
     */
    public static boolean updateWanderingResidency(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return false;
        }
        VillageAllegianceData allegiance = VillageAllegianceApi.get(villager).orElse(null);
        return updateWanderingResidency(level, villager, allegiance);
    }

    private static boolean updateWanderingResidency(
            ServerLevel level,
            Villager villager,
            VillageAllegianceData allegiance) {
        if (allegiance == null || allegiance.state() != AllegianceState.UNAFFILIATED
                || VillagerBehaviorSuppressionPolicy.suppresses(
                        villager, VillagerBehaviorSuppressionPolicy.Behavior.VILLAGE_MIGRATION)) {
            VillageAllegianceReassignmentService.resetResidency(villager);
            return false;
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceReassignmentService.Residency residency =
                VillageAllegianceReassignmentService.readResidency(villager).orElse(null);
        Optional<VillageAllegianceId> current = residencyVillageAtCurrentPosition(
                registry, villager.blockPosition(), residency).or(() -> activeVillageAt(level, villager.blockPosition()));
        if (current.isEmpty()) {
            VillageAllegianceReassignmentService.resetResidency(villager);
            return false;
        }
        long now = level.getGameTime();
        if (residency == null
                || registry.canonical(residency.village()).filter(current.get()::equals).isEmpty()
                || now < residency.sinceGameTime()) {
            VillageAllegianceReassignmentService.writeResidency(villager,
                    new VillageAllegianceReassignmentService.Residency(current.get(), now));
            return false;
        }
        if (now - residency.sinceGameTime()
                < VillageAllegianceReassignmentService.REQUIRED_RESIDENCY_TICKS) {
            return false;
        }
        assignKnown(level, villager, current.get(), AllegianceAssignmentSource.SETTLEMENT,
                villager.blockPosition());
        VillageAllegianceReassignmentService.complete(villager);
        return true;
    }

    public static boolean retryMigration(ServerLevel level, Entity entity) {
        VillageAllegianceEntityData.clearForRepair(entity);
        assignUnknown(level, entity, AllegianceAssignmentSource.ADMIN);
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
        PENDING_QUEUE.clear();
        SCHEDULED_VILLAGERS.clear();
        VILLAGER_QUEUE.clear();
        LIFECYCLE_QUEUE.clear();
        QUEUED_LIFECYCLES.clear();
        VillageAllegianceReassignmentService.clearRuntimeState();
        migratedKnown = 0L;
        migratedUnknown = 0L;
        migratedUnaffiliated = 0L;
        nextLifecycleRefresh = 0L;
        nextLifecycleWork = 0L;
        if (server != null) {
            VillageAllegianceRegistrySavedData.get(server.overworld()).clearRuntimeCache();
        }
    }

    private static boolean tryResolve(
            ServerLevel level,
            Entity entity,
            AllegianceAssignmentSource source,
            boolean deferUnaffiliated,
            BlockPos evidencePosition) {
        if (!(entity instanceof Villager) && !(entity instanceof IronGolem)) {
            return false;
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> discovered = villageAt(level, evidencePosition);
        List<VillageAllegianceId> protectedParents = VillageAllegianceApi.get(entity)
                .map(VillageAllegianceData::protectedParents)
                .orElse(List.of());
        if (entity instanceof Villager villager && !villager.isBaby()
                && source == AllegianceAssignmentSource.BIRTH && !protectedParents.isEmpty()) {
            protectedParents = List.of();
            assignUnknown(level, entity, source, AllegianceConfidence.INHERITED, protectedParents);
        }
        if (source == AllegianceAssignmentSource.BIRTH && protectedParents.size() > 1) {
            discovered = discovered.flatMap(registry::canonical).filter(protectedParents::contains);
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
        if (resolution.status() == VillageAssignmentResolution.Status.NONE
                && resolution.observationComplete()
                && !deferUnaffiliated) {
            assignUnaffiliated(level, entity, source);
            return true;
        }
        if (VillageAllegianceApi.get(entity).isEmpty()) {
            assignUnknown(level, entity, source);
        }
        return false;
    }

    private static void assignFreshVillager(ServerLevel level, Villager villager, BlockPos spawnPosition) {
        if (PartyService.isRecruitedPartyVillager(level, villager.getUUID())) {
            assignUnaffiliated(level, villager, AllegianceAssignmentSource.NATURAL_SPAWN);
            return;
        }
        Optional<VillageAllegianceId> village = activeVillageAt(level, spawnPosition);
        if (village.isPresent()) {
            assignKnown(level, villager, village.get(), AllegianceAssignmentSource.NATURAL_SPAWN, spawnPosition);
        } else {
            // Structure entities join the level while their chunk is still being generated. At that
            // point the village POIs and indexed footprint may not exist yet, so an immediate negative
            // lookup would permanently turn a generated resident into a Wanderer. Preserve the spawn
            // position and let the bounded assignment queue retry after worldgen has finished exposing
            // the POIs. A genuinely outside spawn is finalized as unaffiliated once the surrounding
            // observation is complete.
            schedulePending(level, villager, spawnPosition, AllegianceAssignmentSource.NATURAL_SPAWN, 0,
                    level.getServer().overworld().getGameTime() + RETRY_INTERVAL_TICKS);
        }
    }

    private static void recoverLegacyBirthAssignment(
            ServerLevel level,
            Villager villager,
            VillageAllegianceData data) {
        Optional<VillageAllegianceId> birthplace = data.originDimension() != null
                && data.originDimension().equals(level.dimension().location())
                ? activeVillageAt(level, data.originPosition())
                : Optional.empty();
        VillageAllegianceId home = birthplace.orElse(data.protectedParents().getFirst());
        assignKnown(level, villager, home, AllegianceAssignmentSource.BIRTH,
                data.originPosition(), AllegianceConfidence.INHERITED, data.protectedParents());
    }

    public static Optional<VillageAllegianceId> activeVillageAt(ServerLevel level, BlockPos position) {
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        return villageAt(level, position)
                .flatMap(registry::canonical)
                .filter(id -> registry.canonicalRecord(id)
                        .filter(record -> record.lifecycleState() == VillageLifecycleState.ACTIVE)
                        .isPresent());
    }

    private static Optional<VillageAllegianceId> villageAt(ServerLevel level, BlockPos position) {
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> indexed = registry.peekAt(level, position);
        return indexed.isPresent() || !level.isVillage(position)
                ? indexed
                : registry.discoverAt(level, position);
    }

    private static Optional<VillageAllegianceId> residencyVillageAtCurrentPosition(
            VillageAllegianceRegistrySavedData registry,
            BlockPos position,
            VillageAllegianceReassignmentService.Residency residency) {
        if (residency == null) {
            return Optional.empty();
        }
        return registry.canonicalRecord(residency.village())
                .filter(record -> record.lifecycleState() == VillageLifecycleState.ACTIVE)
                .filter(record -> record.footprintSections().contains(net.minecraft.core.SectionPos.asLong(position)))
                .map(VillageAllegianceRegistrySavedData.AllegianceRecord::id);
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
            registry.addOrUpdateResident(
                    canonical,
                    villager.getUUID(),
                    !villager.isBaby(),
                    villager.getVillagerData().getProfession() == VillagerProfession.NITWIT,
                    level.getGameTime());
            scheduleLoadedVillager(level, villager, VillageAllegianceApi.get(villager).orElse(null), false);
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
            // Keep the raw identity intact for diagnostics and explicit repair. Combat resolves it conservatively.
            registry.removeResidentEverywhere(entity.getUUID());
            return;
        }
        if (!canonical.get().equals(data.primary()) || data.dataVersion() < VillageAllegianceData.CURRENT_VERSION) {
            VillageAllegianceEntityData.write(entity, VillageAllegianceData.known(
                    canonical.get(), data.assignmentSource(), data.confidence(), data.assignedGameTime(),
                    data.originDimension(), data.originPosition(), List.of()));
        }
        if (entity instanceof Villager villager) {
            registry.addOrUpdateResident(
                    canonical.get(),
                    villager.getUUID(),
                    !villager.isBaby(),
                    villager.getVillagerData().getProfession() == VillagerProfession.NITWIT,
                    level.getGameTime());
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
        unscheduleVillager(entity.getUUID());
        migratedUnknown++;
    }

    private static void assignUnaffiliated(ServerLevel level, Entity entity, AllegianceAssignmentSource source) {
        VillageAllegianceRegistrySavedData.get(level).removeResidentEverywhere(entity.getUUID());
        VillageAllegianceEntityData.write(entity, VillageAllegianceData.unaffiliated(
                source, level.getGameTime(), level.dimension().location(), entity.blockPosition()));
        VillageAllegianceEntityData.clearPending(entity);
        PENDING.remove(entity.getUUID());
        scheduleLoadedVillager(level, entity, VillageAllegianceApi.get(entity).orElse(null), true);
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
        putPending(entity.getUUID(), pending);
        VillageAllegianceEntityData.writePending(entity, pending.toData());
    }

    public static void onExplicitAssignment(
            ServerLevel level,
            Entity entity,
            VillageAllegianceData data) {
        scheduleLoadedVillager(level, entity, data, data != null && data.state() == AllegianceState.UNAFFILIATED);
    }

    private static void scheduleLoadedVillager(
            ServerLevel level,
            Entity entity,
            VillageAllegianceData data,
            boolean immediate) {
        if (!(entity instanceof Villager villager) || level == null || data == null
                || data.state() == AllegianceState.UNKNOWN) {
            if (entity != null) {
                unscheduleVillager(entity.getUUID());
            }
            return;
        }
        long interval = data.state() == AllegianceState.UNAFFILIATED
                ? WANDERER_CHECK_INTERVAL_TICKS
                : RESIDENT_REFRESH_INTERVAL_TICKS;
        long next = level.getServer().overworld().getGameTime() + (immediate ? 1L : interval);
        ScheduledVillager scheduled = new ScheduledVillager(villager.getUUID(), level.dimension(), next);
        SCHEDULED_VILLAGERS.put(villager.getUUID(), scheduled);
        VILLAGER_QUEUE.add(scheduled);
    }

    private static void unscheduleVillager(UUID villagerId) {
        if (villagerId != null) {
            SCHEDULED_VILLAGERS.remove(villagerId);
        }
    }

    private static void putPending(UUID entityId, PendingAssignment pending) {
        PENDING.put(entityId, pending);
        PENDING_QUEUE.add(new PendingTask(entityId, pending));
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

    private record PendingTask(UUID entityId, PendingAssignment pending) {
    }

    private record ScheduledVillager(
            UUID villagerId,
            ResourceKey<net.minecraft.world.level.Level> dimension,
            long nextCheckGameTime) {
    }

    private record LifecycleTask(
            ResourceKey<net.minecraft.world.level.Level> dimension,
            VillageAllegianceId villageId) {
    }

    public record MigrationStatistics(long known, long unknown, long unaffiliated, int pending) {
    }
}
