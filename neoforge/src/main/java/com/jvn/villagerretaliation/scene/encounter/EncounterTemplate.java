package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.util.item.ItemStackPredicate;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public record EncounterTemplate(
        ResourceLocation id,
        int version,
        ResourceLocation controller,
        List<Member> members,
        int extraPerAdditionalPlayer,
        int maxPartySize,
        int placementAttempts,
        int spawnRadius,
        RespawnPolicy respawnPolicy,
        CleanupPolicy cleanupPolicy,
        CompletionCondition completionCondition,
        SpawnMode spawnMode,
        int waveCount,
        int waveIntervalTicks,
        WaveTrigger waveTrigger,
        boolean bossBar,
        String locationMessage,
        Area area,
        List<Wave> waves,
        List<SpawnPoint> spawnPoints,
        SpawnSelectionMode spawnSelection,
        List<Phase> phases,
        ObjectiveComposition completionObjectives,
        List<Ally> allies,
        FailurePolicy failure,
        List<Variant> variants,
        Environment environment,
        Guidance guidance,
        RewardPolicy rewards) {
    public EncounterTemplate {
        members = members == null ? List.of() : List.copyOf(members);
        waves = waves == null ? List.of() : List.copyOf(waves);
        spawnPoints = spawnPoints == null ? List.of() : List.copyOf(spawnPoints);
        spawnSelection = spawnSelection == null ? SpawnSelectionMode.RANDOM : spawnSelection;
        phases = phases == null ? List.of() : List.copyOf(phases);
        allies = allies == null ? List.of() : List.copyOf(allies);
        variants = variants == null ? List.of() : List.copyOf(variants);
        if (id == null
                || controller == null
                || (members.isEmpty() && waves.isEmpty() && variants.isEmpty()))
            throw new IllegalArgumentException(
                    "encounter needs id, controller, and members, waves, or variants");
        if (!members.isEmpty() && !waves.isEmpty())
            throw new IllegalArgumentException(
                    "encounter members and waves are mutually exclusive");
        if (!variants.isEmpty() && (!members.isEmpty() || !waves.isEmpty()))
            throw new IllegalArgumentException(
                    "encounter variants are mutually exclusive with members and waves");
        if (members.size() > 64 || waves.size() > 32)
            throw new IllegalArgumentException("encounter composition exceeds its bounded limits");
        if (spawnPoints.size() > 64)
            throw new IllegalArgumentException("encounter exceeds 64 authored spawn points");
        if (phases.size() > 64) throw new IllegalArgumentException("encounter exceeds 64 phases");
        if (allies.size() > 32)
            throw new IllegalArgumentException("encounter exceeds 32 ally definitions");
        if (variants.size() > 32)
            throw new IllegalArgumentException("encounter exceeds 32 variants");
        version = Math.max(1, version);
        extraPerAdditionalPlayer = Math.max(0, Math.min(64, extraPerAdditionalPlayer));
        maxPartySize = Math.max(1, Math.min(16, maxPartySize));
        placementAttempts = Math.max(1, Math.min(64, placementAttempts));
        spawnRadius = Math.max(1, Math.min(32, spawnRadius));
        respawnPolicy = respawnPolicy == null ? RespawnPolicy.NEVER : respawnPolicy;
        cleanupPolicy = cleanupPolicy == null ? CleanupPolicy.REMOVE_SURVIVORS : cleanupPolicy;
        completionCondition =
                completionCondition == null
                        ? CompletionCondition.ALL_DEFEATED
                        : completionCondition;
        spawnMode = spawnMode == null ? SpawnMode.GROUP : spawnMode;
        if (!waves.isEmpty() && spawnMode != SpawnMode.RAID_WAVES)
            throw new IllegalArgumentException("explicit waves require spawn_mode raid_waves");
        waveCount =
                !waves.isEmpty()
                        ? waves.size()
                        : spawnMode == SpawnMode.RAID_WAVES
                                ? Math.max(1, Math.min(32, waveCount))
                                : 1;
        waveIntervalTicks = Math.max(0, waveIntervalTicks);
        waveTrigger = waveTrigger == null ? WaveTrigger.ALL_DEFEATED : waveTrigger;
        locationMessage = locationMessage == null ? "" : locationMessage;
        if (variants.isEmpty()) {
            long maximum = 0;
            if (waves.isEmpty()) {
                long perWave =
                        members.stream().mapToInt(Member::count).sum()
                                + (long) (maxPartySize - 1) * extraPerAdditionalPlayer;
                maximum = perWave * waveCount;
            } else
                for (Wave wave : waves)
                    maximum +=
                            wave.members().stream().mapToInt(Member::count).sum()
                                    + (long) (maxPartySize - 1) * extraPerAdditionalPlayer;
            if (maximum > 4096)
                throw new IllegalArgumentException(
                        "scaled encounter composition exceeds 4096 owned mobs");
            List<Wave> phaseWaves = new java.util.ArrayList<>();
            if (!waves.isEmpty()) phaseWaves.addAll(waves);
            else
                for (int i = 0; i < waveCount; i++)
                    phaseWaves.add(
                            new Wave(
                                    "repeat_" + (i + 1),
                                    members,
                                    i == 0 ? 0 : waveIntervalTicks,
                                    waveTrigger,
                                    "",
                                    List.of()));
            java.util.Set<String> memberIds = new java.util.LinkedHashSet<>();
            java.util.stream.Stream<Member> identityMembers =
                    waves.isEmpty()
                            ? members.stream()
                            : waves.stream().flatMap(wave -> wave.members().stream());
            for (Member member : identityMembers.toList())
                if (!member.id().isBlank() && !memberIds.add(member.id()))
                    throw new IllegalArgumentException(
                            "duplicate encounter member id " + member.id());
            java.util.Set<String> phaseIds = new java.util.LinkedHashSet<>();
            for (Phase phase : phases) {
                if (!phaseIds.add(phase.id()))
                    throw new IllegalArgumentException(
                            "duplicate encounter phase id " + phase.id());
                if ((phase.trigger().type() == PhaseTriggerType.WAVE_STARTED
                                || phase.trigger().type() == PhaseTriggerType.WAVE_COMPLETED)
                        && phaseWaves.stream()
                                .noneMatch(wave -> wave.id().equals(phase.trigger().waveId())))
                    throw new IllegalArgumentException(
                            "phase "
                                    + phase.id()
                                    + " references unknown wave "
                                    + phase.trigger().waveId());
                if (phase.trigger().type() == PhaseTriggerType.ELITE_DEFEATED) {
                    Wave eliteWave =
                            phaseWaves.stream()
                                    .filter(
                                            wave ->
                                                    wave.members().stream()
                                                            .anyMatch(
                                                                    member ->
                                                                            member.id()
                                                                                    .equals(
                                                                                            phase.trigger()
                                                                                                    .memberId())))
                                    .findFirst()
                                    .orElse(null);
                    Member elite =
                            eliteWave == null
                                    ? null
                                    : eliteWave.members().stream()
                                            .filter(
                                                    member ->
                                                            member.id()
                                                                    .equals(
                                                                            phase.trigger()
                                                                                    .memberId()))
                                            .findFirst()
                                            .orElse(null);
                    if (elite == null)
                        throw new IllegalArgumentException(
                                "phase "
                                        + phase.id()
                                        + " references unknown elite member "
                                        + phase.trigger().memberId());
                    boolean scalesElite =
                            extraPerAdditionalPlayer > 0
                                    && maxPartySize > 1
                                    && eliteWave.members().getFirst() == elite;
                    if (elite.count() != 1
                            || scalesElite
                            || elite.options().customName().isBlank()
                                    && elite.options().attributes().isEmpty()
                                    && !elite.options().boss())
                        throw new IllegalArgumentException(
                                "phase "
                                        + phase.id()
                                        + " elite_defeated requires a single named or enhanced member");
                }
            }
            if (completionObjectives != null) {
                java.util.Set<String> objectiveIds = new java.util.LinkedHashSet<>();
                java.util.Set<String> pointIds =
                        spawnPoints.stream()
                                .map(SpawnPoint::id)
                                .collect(java.util.stream.Collectors.toSet());
                for (Objective objective : completionObjectives.objectives()) {
                    if (!objectiveIds.add(objective.id()))
                        throw new IllegalArgumentException(
                                "duplicate completion objective id " + objective.id());
                    if ((objective.type() == ObjectiveType.PREVENT_ENTRY
                                    || objective.type() == ObjectiveType.ESCORT_ACTOR)
                            && !pointIds.contains(objective.pointId()))
                        throw new IllegalArgumentException(
                                "objective "
                                        + objective.id()
                                        + " references unknown spawn point "
                                        + objective.pointId());
                    if (objective.type() == ObjectiveType.HOLD_AREAS)
                        for (String point : objective.pointIds())
                            if (!pointIds.contains(point))
                                throw new IllegalArgumentException(
                                        "objective "
                                                + objective.id()
                                                + " references unknown spawn point "
                                                + point);
                    if (objective.type() == ObjectiveType.DEFEAT_LEADER
                            && !memberIds.contains(objective.memberId()))
                        throw new IllegalArgumentException(
                                "objective "
                                        + objective.id()
                                        + " references unknown leader member "
                                        + objective.memberId());
                }
            }
        }
        java.util.Set<String> allyIds = new java.util.LinkedHashSet<>();
        long maximumAllies = 0;
        for (Ally ally : allies) {
            if (!allyIds.add(ally.id()))
                throw new IllegalArgumentException("duplicate encounter ally id " + ally.id());
            maximumAllies += ally.count();
        }
        if (maximumAllies > 64)
            throw new IllegalArgumentException("encounter exceeds 64 controlled ally instances");
        java.util.Set<String> variantIds = new java.util.LinkedHashSet<>();
        for (Variant variant : variants)
            if (!variantIds.add(variant.id()))
                throw new IllegalArgumentException(
                        "duplicate encounter variant id " + variant.id());
    }

    /** Source-compatible constructor for extension code targeting encounter/v1 before spawn modes were added. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                SpawnMode.GROUP,
                1,
                0,
                WaveTrigger.ALL_DEFEATED,
                true,
                "",
                null,
                List.of(),
                List.of(),
                SpawnSelectionMode.RANDOM,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                null,
                null,
                null);
    }

    /** Source-compatible constructor for encounter/v1 spawn-mode extension code written before encounter areas. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition,
            SpawnMode spawnMode,
            int waveCount,
            int waveIntervalTicks,
            WaveTrigger waveTrigger,
            boolean bossBar,
            String locationMessage) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                spawnMode,
                waveCount,
                waveIntervalTicks,
                waveTrigger,
                bossBar,
                locationMessage,
                null,
                List.of(),
                List.of(),
                SpawnSelectionMode.RANDOM,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                null,
                null,
                null);
    }

    /** Source-compatible constructor for encounter/v1 area extension code written before explicit waves. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition,
            SpawnMode spawnMode,
            int waveCount,
            int waveIntervalTicks,
            WaveTrigger waveTrigger,
            boolean bossBar,
            String locationMessage,
            Area area) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                spawnMode,
                waveCount,
                waveIntervalTicks,
                waveTrigger,
                bossBar,
                locationMessage,
                area,
                List.of(),
                List.of(),
                SpawnSelectionMode.RANDOM,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                null,
                null,
                null);
    }

    /** Source-compatible constructor for encounter/v1 explicit-wave code written before authored spawn points. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition,
            SpawnMode spawnMode,
            int waveCount,
            int waveIntervalTicks,
            WaveTrigger waveTrigger,
            boolean bossBar,
            String locationMessage,
            Area area,
            List<Wave> waves) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                spawnMode,
                waveCount,
                waveIntervalTicks,
                waveTrigger,
                bossBar,
                locationMessage,
                area,
                waves,
                List.of(),
                SpawnSelectionMode.RANDOM,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                null,
                null,
                null);
    }

    /** Source-compatible constructor for authored-spawn-point extension code written before encounter phases. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition,
            SpawnMode spawnMode,
            int waveCount,
            int waveIntervalTicks,
            WaveTrigger waveTrigger,
            boolean bossBar,
            String locationMessage,
            Area area,
            List<Wave> waves,
            List<SpawnPoint> spawnPoints,
            SpawnSelectionMode spawnSelection) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                spawnMode,
                waveCount,
                waveIntervalTicks,
                waveTrigger,
                bossBar,
                locationMessage,
                area,
                waves,
                spawnPoints,
                spawnSelection,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                null,
                null,
                null);
    }

    /** Source-compatible constructor for encounter phase extension code written before completion objectives. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition,
            SpawnMode spawnMode,
            int waveCount,
            int waveIntervalTicks,
            WaveTrigger waveTrigger,
            boolean bossBar,
            String locationMessage,
            Area area,
            List<Wave> waves,
            List<SpawnPoint> spawnPoints,
            SpawnSelectionMode spawnSelection,
            List<Phase> phases) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                spawnMode,
                waveCount,
                waveIntervalTicks,
                waveTrigger,
                bossBar,
                locationMessage,
                area,
                waves,
                spawnPoints,
                spawnSelection,
                phases,
                null,
                List.of(),
                null,
                List.of(),
                null,
                null,
                null);
    }

    /** Source-compatible constructor for completion-objective extension code written before controlled allies. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition,
            SpawnMode spawnMode,
            int waveCount,
            int waveIntervalTicks,
            WaveTrigger waveTrigger,
            boolean bossBar,
            String locationMessage,
            Area area,
            List<Wave> waves,
            List<SpawnPoint> spawnPoints,
            SpawnSelectionMode spawnSelection,
            List<Phase> phases,
            ObjectiveComposition objectives) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                spawnMode,
                waveCount,
                waveIntervalTicks,
                waveTrigger,
                bossBar,
                locationMessage,
                area,
                waves,
                spawnPoints,
                spawnSelection,
                phases,
                objectives,
                List.of(),
                null,
                List.of(),
                null,
                null,
                null);
    }

    /** Source-compatible constructor for controlled-ally extension code written before failure policies. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition,
            SpawnMode spawnMode,
            int waveCount,
            int waveIntervalTicks,
            WaveTrigger waveTrigger,
            boolean bossBar,
            String locationMessage,
            Area area,
            List<Wave> waves,
            List<SpawnPoint> spawnPoints,
            SpawnSelectionMode spawnSelection,
            List<Phase> phases,
            ObjectiveComposition objectives,
            List<Ally> allies) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                spawnMode,
                waveCount,
                waveIntervalTicks,
                waveTrigger,
                bossBar,
                locationMessage,
                area,
                waves,
                spawnPoints,
                spawnSelection,
                phases,
                objectives,
                allies,
                null,
                List.of(),
                null,
                null,
                null);
    }

    /** Source-compatible constructor for failure-policy extension code written before deterministic variants. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition,
            SpawnMode spawnMode,
            int waveCount,
            int waveIntervalTicks,
            WaveTrigger waveTrigger,
            boolean bossBar,
            String locationMessage,
            Area area,
            List<Wave> waves,
            List<SpawnPoint> spawnPoints,
            SpawnSelectionMode spawnSelection,
            List<Phase> phases,
            ObjectiveComposition objectives,
            List<Ally> allies,
            FailurePolicy failure) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                spawnMode,
                waveCount,
                waveIntervalTicks,
                waveTrigger,
                bossBar,
                locationMessage,
                area,
                waves,
                spawnPoints,
                spawnSelection,
                phases,
                objectives,
                allies,
                failure,
                List.of(),
                null,
                null,
                null);
    }

    /** Source-compatible constructor for deterministic-variant extension code written before environment effects. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition,
            SpawnMode spawnMode,
            int waveCount,
            int waveIntervalTicks,
            WaveTrigger waveTrigger,
            boolean bossBar,
            String locationMessage,
            Area area,
            List<Wave> waves,
            List<SpawnPoint> spawnPoints,
            SpawnSelectionMode spawnSelection,
            List<Phase> phases,
            ObjectiveComposition objectives,
            List<Ally> allies,
            FailurePolicy failure,
            List<Variant> variants) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                spawnMode,
                waveCount,
                waveIntervalTicks,
                waveTrigger,
                bossBar,
                locationMessage,
                area,
                waves,
                spawnPoints,
                spawnSelection,
                phases,
                objectives,
                allies,
                failure,
                variants,
                null,
                null,
                null);
    }

    /** Source-compatible constructor for environmental-effect extension code written before navigation guidance. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition,
            SpawnMode spawnMode,
            int waveCount,
            int waveIntervalTicks,
            WaveTrigger waveTrigger,
            boolean bossBar,
            String locationMessage,
            Area area,
            List<Wave> waves,
            List<SpawnPoint> spawnPoints,
            SpawnSelectionMode spawnSelection,
            List<Phase> phases,
            ObjectiveComposition objectives,
            List<Ally> allies,
            FailurePolicy failure,
            List<Variant> variants,
            Environment environment) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                spawnMode,
                waveCount,
                waveIntervalTicks,
                waveTrigger,
                bossBar,
                locationMessage,
                area,
                waves,
                spawnPoints,
                spawnSelection,
                phases,
                objectives,
                allies,
                failure,
                variants,
                environment,
                null,
                null);
    }

    /** Source-compatible constructor for navigation-guidance extension code written before encounter rewards. */
    public EncounterTemplate(
            ResourceLocation id,
            int version,
            ResourceLocation controller,
            List<Member> members,
            int extraPerAdditionalPlayer,
            int maxPartySize,
            int placementAttempts,
            int spawnRadius,
            RespawnPolicy respawnPolicy,
            CleanupPolicy cleanupPolicy,
            CompletionCondition completionCondition,
            SpawnMode spawnMode,
            int waveCount,
            int waveIntervalTicks,
            WaveTrigger waveTrigger,
            boolean bossBar,
            String locationMessage,
            Area area,
            List<Wave> waves,
            List<SpawnPoint> spawnPoints,
            SpawnSelectionMode spawnSelection,
            List<Phase> phases,
            ObjectiveComposition objectives,
            List<Ally> allies,
            FailurePolicy failure,
            List<Variant> variants,
            Environment environment,
            Guidance guidance) {
        this(
                id,
                version,
                controller,
                members,
                extraPerAdditionalPlayer,
                maxPartySize,
                placementAttempts,
                spawnRadius,
                respawnPolicy,
                cleanupPolicy,
                completionCondition,
                spawnMode,
                waveCount,
                waveIntervalTicks,
                waveTrigger,
                bossBar,
                locationMessage,
                area,
                waves,
                spawnPoints,
                spawnSelection,
                phases,
                objectives,
                allies,
                failure,
                variants,
                environment,
                guidance,
                null);
    }

    public boolean variantSelector() {
        return !variants.isEmpty();
    }

    public boolean explicitWaves() {
        return !waves.isEmpty();
    }

    public Wave wave(int index) {
        if (explicitWaves()) return waves.get(index);
        return new Wave(
                "repeat_" + (index + 1),
                members,
                index == 0 ? 0 : waveIntervalTicks,
                waveTrigger,
                "",
                List.of());
    }

    public int scaledCount(int partySize) {
        return scaledCount(wave(0), partySize);
    }

    public int scaledCount(Wave wave, int partySize) {
        int base = wave.members().stream().mapToInt(Member::count).sum();
        return base + Math.max(0, Math.min(maxPartySize, partySize) - 1) * extraPerAdditionalPlayer;
    }

    public int waveStart(int index, int partySize) {
        int total = 0;
        for (int i = 0; i < index; i++) total += scaledCount(wave(i), partySize);
        return total;
    }

    public int totalCount(int partySize) {
        int total = 0;
        for (int i = 0; i < waveCount; i++) total += scaledCount(wave(i), partySize);
        return total;
    }

    public record Member(
            ResourceLocation entityType,
            int count,
            Map<EquipmentSlot, Gear> equipment,
            MobOptions options,
            String id) {
        public Member {
            if (entityType == null)
                throw new IllegalArgumentException("encounter member entity type is required");
            count = Math.max(1, Math.min(64, count));
            equipment = equipment == null ? Map.of() : Map.copyOf(equipment);
            options = options == null ? MobOptions.DEFAULT : options;
            id = id == null ? "" : id;
            if (!id.isBlank() && !id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException(
                        "encounter member id must be a stable lowercase identifier");
        }

        public Member(ResourceLocation entityType, int count) {
            this(entityType, count, Map.of(), MobOptions.DEFAULT, "");
        }

        public Member(ResourceLocation entityType, int count, Map<EquipmentSlot, Gear> equipment) {
            this(entityType, count, equipment, MobOptions.DEFAULT, "");
        }

        public Member(
                ResourceLocation entityType,
                int count,
                Map<EquipmentSlot, Gear> equipment,
                MobOptions options) {
            this(entityType, count, equipment, options, "");
        }
    }

    public record Gear(
            ResourceLocation item,
            int count,
            Map<ResourceLocation, Integer> enchantments,
            float dropChance) {
        public Gear {
            if (item == null) throw new IllegalArgumentException("equipment item is required");
            count = Math.max(1, Math.min(99, count));
            enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
            dropChance = Math.max(0.0F, Math.min(1.0F, dropChance));
        }
    }

    public record Wave(
            String id,
            List<Member> members,
            int delayTicks,
            WaveTrigger trigger,
            String bossBarTitle,
            List<WaveHook> hooks) {
        public Wave {
            if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException("wave id must be a stable lowercase identifier");
            if (members == null || members.isEmpty())
                throw new IllegalArgumentException("wave " + id + " needs members");
            if (members.size() > 64)
                throw new IllegalArgumentException("wave " + id + " exceeds 64 member definitions");
            members = List.copyOf(members);
            if (delayTicks < 0 || delayTicks > 12000)
                throw new IllegalArgumentException(
                        "wave " + id + " delay_ticks must be between 0 and 12000");
            trigger = trigger == null ? WaveTrigger.ALL_DEFEATED : trigger;
            bossBarTitle = bossBarTitle == null ? "" : bossBarTitle;
            if (bossBarTitle.length() > 128)
                throw new IllegalArgumentException(
                        "wave " + id + " boss_bar_title exceeds 128 characters");
            hooks = hooks == null ? List.of() : List.copyOf(hooks);
            if (hooks.size() > 32)
                throw new IllegalArgumentException("wave " + id + " exceeds 32 hooks");
        }
    }

    public record WaveHook(String id, HookType type, String text) {
        public WaveHook {
            if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException(
                        "wave hook id must be a stable lowercase identifier");
            if (type == null) throw new IllegalArgumentException("wave hook type is required");
            if (text == null || text.isBlank() || text.length() > 512)
                throw new IllegalArgumentException(
                        "wave hook text must contain 1 to 512 characters");
        }
    }

    public enum HookType {
        NOTIFICATION,
        DIALOGUE
    }

    public record MobOptions(
            String customName,
            boolean nameVisible,
            boolean glowing,
            boolean persistent,
            Map<ResourceLocation, Double> attributes,
            boolean boss,
            BossColor bossBarColor,
            BossOverlay bossBarOverlay) {
        public static final MobOptions DEFAULT =
                new MobOptions(
                        "",
                        false,
                        false,
                        false,
                        Map.of(),
                        false,
                        BossColor.RED,
                        BossOverlay.PROGRESS);

        public MobOptions {
            customName = customName == null ? "" : customName;
            if (customName.length() > 128)
                throw new IllegalArgumentException("custom_name exceeds 128 characters");
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
            bossBarColor = bossBarColor == null ? BossColor.RED : bossBarColor;
            bossBarOverlay = bossBarOverlay == null ? BossOverlay.PROGRESS : bossBarOverlay;
        }
    }

    public enum BossColor {
        PINK,
        BLUE,
        RED,
        GREEN,
        YELLOW,
        PURPLE,
        WHITE
    }

    public enum BossOverlay {
        PROGRESS,
        NOTCHED_6,
        NOTCHED_10,
        NOTCHED_12,
        NOTCHED_20
    }

    public record SpawnPoint(
            String id,
            String actorAlias,
            ResourceLocation dimension,
            BlockPos position,
            BlockPos offset,
            int weight) {
        public SpawnPoint {
            id = id == null ? "" : id;
            if (!id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException(
                        "spawn point id must be a stable lowercase identifier");
            actorAlias = actorAlias == null ? "" : actorAlias;
            if (!actorAlias.isBlank() && !actorAlias.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException(
                        "spawn point " + id + " actor must be a stable scene alias");
            position = position == null ? null : position.immutable();
            offset = offset == null ? BlockPos.ZERO : offset.immutable();
            if (actorAlias.isBlank() == (position == null))
                throw new IllegalArgumentException(
                        "spawn point " + id + " requires exactly one actor or coordinate source");
            if (!actorAlias.isBlank() && dimension != null)
                throw new IllegalArgumentException(
                        "spawn point " + id + " dimension is only valid for coordinate sources");
            if (actorAlias.isBlank() && !offset.equals(BlockPos.ZERO))
                throw new IllegalArgumentException(
                        "spawn point " + id + " offsets require an actor or marker source");
            if (weight < 1 || weight > 10000)
                throw new IllegalArgumentException(
                        "spawn point " + id + " weight must be between 1 and 10000");
        }

        public SpawnPoint(
                String id,
                String actorAlias,
                ResourceLocation dimension,
                BlockPos position,
                int weight) {
            this(id, actorAlias, dimension, position, BlockPos.ZERO, weight);
        }

        public boolean actorSource() {
            return !actorAlias.isBlank();
        }
    }

    public enum SpawnSelectionMode {
        RANDOM,
        SEQUENTIAL,
        WEIGHTED,
        NEAREST_PLAYER,
        FARTHEST_PLAYER,
        ONE_GROUP_PER_POINT
    }

    public record Phase(
            String id,
            PhaseTrigger trigger,
            List<PhaseAction> actions,
            boolean repeatable,
            int repeatIntervalTicks,
            int maxFires) {
        public Phase {
            id = id == null ? "" : id;
            if (!id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException(
                        "phase id must be a stable lowercase identifier");
            if (trigger == null)
                throw new IllegalArgumentException("phase " + id + " requires a trigger");
            actions = actions == null ? List.of() : List.copyOf(actions);
            if (actions.isEmpty() || actions.size() > 32)
                throw new IllegalArgumentException("phase " + id + " requires 1 to 32 actions");
            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            int transitions = 0;
            for (PhaseAction action : actions) {
                if (!ids.add(action.id()))
                    throw new IllegalArgumentException(
                            "phase " + id + " has duplicate action id " + action.id());
                if (action.type() == PhaseActionType.TRANSITION) transitions++;
            }
            if (transitions > 1)
                throw new IllegalArgumentException(
                        "phase " + id + " may contain at most one transition action");
            if (repeatable) {
                if (transitions > 0)
                    throw new IllegalArgumentException(
                            "repeatable phase " + id + " cannot contain a transition action");
                if (repeatIntervalTicks < 1 || repeatIntervalTicks > 12000)
                    throw new IllegalArgumentException(
                            "repeatable phase "
                                    + id
                                    + " repeat_interval_ticks must be between 1 and 12000");
                if (maxFires < 2 || maxFires > 64)
                    throw new IllegalArgumentException(
                            "repeatable phase " + id + " max_fires must be between 2 and 64");
            } else if (repeatIntervalTicks != 0 || maxFires != 1)
                throw new IllegalArgumentException(
                        "non-repeatable phase "
                                + id
                                + " cannot configure repeat interval or max fires");
        }
    }

    public record PhaseTrigger(
            PhaseTriggerType type, String waveId, int percentage, long ticks, String memberId) {
        public PhaseTrigger {
            if (type == null) throw new IllegalArgumentException("phase trigger type is required");
            waveId = waveId == null ? "" : waveId;
            memberId = memberId == null ? "" : memberId;
            switch (type) {
                case WAVE_STARTED, WAVE_COMPLETED -> {
                    if (!waveId.matches("[a-z][a-z0-9_.-]{0,63}"))
                        throw new IllegalArgumentException(
                                "wave phase trigger requires a stable wave id");
                }
                case REMAINING_PERCENTAGE -> {
                    if (percentage < 0 || percentage > 100)
                        throw new IllegalArgumentException(
                                "remaining_percentage must be between 0 and 100");
                }
                case ELAPSED_TIME -> {
                    if (ticks < 1 || ticks > 1728000)
                        throw new IllegalArgumentException(
                                "elapsed_time ticks must be between 1 and 1728000");
                }
                case ELITE_DEFEATED -> {
                    if (!memberId.matches("[a-z][a-z0-9_.-]{0,63}"))
                        throw new IllegalArgumentException(
                                "elite_defeated requires a stable member id");
                }
            }
        }
    }

    public record PhaseAction(
            String id,
            PhaseActionType type,
            String text,
            FactScope scope,
            ResourceLocation tag,
            String key,
            String value,
            String target) {
        public PhaseAction {
            id = id == null ? "" : id;
            if (!id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException(
                        "phase action id must be a stable lowercase identifier");
            if (type == null)
                throw new IllegalArgumentException("phase action " + id + " requires a type");
            text = text == null ? "" : text;
            scope = scope == null ? FactScope.PLAYER : scope;
            key = key == null ? "" : key;
            value = value == null ? "" : value;
            target = target == null ? "" : target;
            switch (type) {
                case NOTIFICATION, DIALOGUE -> {
                    if (text.isBlank() || text.length() > 512)
                        throw new IllegalArgumentException(
                                "phase action " + id + " text must contain 1 to 512 characters");
                }
                case FACT -> {
                    if ((tag == null) == key.isBlank())
                        throw new IllegalArgumentException(
                                "phase fact " + id + " requires exactly one tag or key/value");
                    if (!key.isBlank()
                            && (!key.matches("[a-zA-Z0-9_.:-]{1,128}") || value.length() > 128))
                        throw new IllegalArgumentException(
                                "phase fact " + id + " has an invalid key or value");
                }
                case TRANSITION -> {
                    if (!target.matches("[a-z][a-z0-9_.-]{0,63}"))
                        throw new IllegalArgumentException(
                                "phase transition " + id + " requires a stable target step");
                }
            }
        }
    }

    public enum PhaseTriggerType {
        WAVE_STARTED,
        WAVE_COMPLETED,
        REMAINING_PERCENTAGE,
        ELAPSED_TIME,
        ELITE_DEFEATED
    }

    public enum PhaseActionType {
        NOTIFICATION,
        DIALOGUE,
        FACT,
        TRANSITION
    }

    public enum FactScope {
        PLAYER,
        QUEST,
        WORLD
    }

    public record ObjectiveComposition(ObjectiveMode mode, List<Objective> objectives) {
        public ObjectiveComposition {
            mode = mode == null ? ObjectiveMode.ALL : mode;
            objectives = objectives == null ? List.of() : List.copyOf(objectives);
            if (objectives.isEmpty() || objectives.size() > 32)
                throw new IllegalArgumentException(
                        "completion_objectives requires 1 to 32 objectives");
        }
    }

    public record Objective(
            String id,
            ObjectiveType type,
            long durationTicks,
            String actorAlias,
            String pointId,
            List<String> actorAliases,
            List<String> pointIds,
            String memberId,
            ResourceLocation item,
            ItemStackPredicate itemPredicate,
            int count,
            int radius,
            int verticalRadius) {
        public Objective(
                String id,
                ObjectiveType type,
                long durationTicks,
                String actorAlias,
                String pointId,
                List<String> actorAliases,
                List<String> pointIds,
                String memberId,
                ResourceLocation item,
                int count,
                int radius,
                int verticalRadius) {
            this(
                    id,
                    type,
                    durationTicks,
                    actorAlias,
                    pointId,
                    actorAliases,
                    pointIds,
                    memberId,
                    item,
                    ItemStackPredicate.ANY,
                    count,
                    radius,
                    verticalRadius);
        }

        public Objective {
            id = id == null ? "" : id;
            itemPredicate = itemPredicate == null ? ItemStackPredicate.ANY : itemPredicate;
            if (!id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException(
                        "completion objective id must be a stable lowercase identifier");
            if (type == null)
                throw new IllegalArgumentException("objective " + id + " requires a type");
            actorAlias = actorAlias == null ? "" : actorAlias;
            pointId = pointId == null ? "" : pointId;
            actorAliases = actorAliases == null ? List.of() : List.copyOf(actorAliases);
            pointIds = pointIds == null ? List.of() : List.copyOf(pointIds);
            memberId = memberId == null ? "" : memberId;
            if (actorAliases.stream().anyMatch(java.util.Objects::isNull)
                    || pointIds.stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException(
                        "objective " + id + " contains a null actor or point id");
            for (String value :
                    java.util.stream.Stream.concat(
                                    java.util.stream.Stream.of(actorAlias, pointId, memberId),
                                    java.util.stream.Stream.concat(
                                            actorAliases.stream(), pointIds.stream()))
                            .filter(value -> !value.isBlank())
                            .toList())
                if (!value.matches("[a-z][a-z0-9_.-]{0,63}"))
                    throw new IllegalArgumentException(
                            "objective " + id + " references an invalid stable id " + value);
            if (new java.util.LinkedHashSet<>(actorAliases).size() != actorAliases.size()
                    || new java.util.LinkedHashSet<>(pointIds).size() != pointIds.size())
                throw new IllegalArgumentException(
                        "objective " + id + " contains duplicate actor or point ids");
            if (durationTicks < 0 || durationTicks > 1728000)
                throw new IllegalArgumentException(
                        "objective " + id + " duration_ticks must be between 0 and 1728000");
            if (count < 0 || count > 64)
                throw new IllegalArgumentException(
                        "objective " + id + " count must be between 0 and 64");
            if (radius < 0 || radius > 64 || verticalRadius < 0 || verticalRadius > 64)
                throw new IllegalArgumentException(
                        "objective " + id + " radii must be between 0 and 64");
            switch (type) {
                case ALL_DEFEATED, ALL_GONE -> {}
                case SURVIVE_DURATION -> {
                    if (durationTicks < 1)
                        throw new IllegalArgumentException(
                                "survive_duration objective " + id + " requires duration_ticks");
                }
                case PROTECT_ACTOR -> {
                    if (actorAlias.isBlank() || durationTicks < 1)
                        throw new IllegalArgumentException(
                                "protect_actor objective "
                                        + id
                                        + " requires actor and duration_ticks");
                }
                case PREVENT_ENTRY -> {
                    if (pointId.isBlank() || durationTicks < 1 || radius < 1 || verticalRadius < 1)
                        throw new IllegalArgumentException(
                                "prevent_entry objective "
                                        + id
                                        + " requires point, duration, and radii");
                }
                case ESCORT_ACTOR -> {
                    if (actorAlias.isBlank()
                            || pointId.isBlank()
                            || radius < 1
                            || verticalRadius < 1)
                        throw new IllegalArgumentException(
                                "escort_actor objective "
                                        + id
                                        + " requires actor, point, and radii");
                }
                case DESTROY_TARGETS -> {
                    if (actorAliases.isEmpty() || actorAliases.size() > 32)
                        throw new IllegalArgumentException(
                                "destroy_targets objective " + id + " requires 1 to 32 actors");
                }
                case DEFEAT_LEADER -> {
                    if (memberId.isBlank())
                        throw new IllegalArgumentException(
                                "defeat_leader objective " + id + " requires member");
                }
                case RETRIEVE_ITEM -> {
                    if (item == null || count < 1)
                        throw new IllegalArgumentException(
                                "retrieve_item objective " + id + " requires item and count");
                }
                case HOLD_AREAS -> {
                    if (pointIds.isEmpty()
                            || pointIds.size() > 16
                            || durationTicks < 1
                            || radius < 1
                            || verticalRadius < 1)
                        throw new IllegalArgumentException(
                                "hold_areas objective "
                                        + id
                                        + " requires points, duration, and radii");
                }
            }
        }
    }

    public enum ObjectiveMode {
        ALL,
        ANY
    }

    public enum ObjectiveType {
        ALL_DEFEATED,
        ALL_GONE,
        SURVIVE_DURATION,
        PROTECT_ACTOR,
        PREVENT_ENTRY,
        ESCORT_ACTOR,
        DESTROY_TARGETS,
        DEFEAT_LEADER,
        RETRIEVE_ITEM,
        HOLD_AREAS
    }

    public record Variant(String id, int weight, ResourceLocation template) {
        public Variant {
            if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException(
                        "encounter variant id must be a stable lowercase identifier");
            if (weight < 1 || weight > 10000)
                throw new IllegalArgumentException(
                        "encounter variant " + id + " weight must be between 1 and 10000");
            if (template == null)
                throw new IllegalArgumentException(
                        "encounter variant " + id + " requires a namespaced template");
        }
    }

    public record Environment(List<EnvironmentCue> cues, List<TemporaryBlock> temporaryBlocks) {
        public Environment {
            cues = cues == null ? List.of() : List.copyOf(cues);
            temporaryBlocks = temporaryBlocks == null ? List.of() : List.copyOf(temporaryBlocks);
            if (cues.size() > 32 || temporaryBlocks.size() > 64)
                throw new IllegalArgumentException(
                        "encounter environment exceeds bounded cue or block limits");
            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            for (EnvironmentCue cue : cues)
                if (!ids.add(cue.id()))
                    throw new IllegalArgumentException(
                            "duplicate environment effect id " + cue.id());
            for (TemporaryBlock block : temporaryBlocks)
                if (!ids.add(block.id()))
                    throw new IllegalArgumentException(
                            "duplicate environment effect id " + block.id());
        }
    }

    public record EnvironmentCue(
            String id,
            EnvironmentCueType type,
            ResourceLocation resource,
            BlockPos offset,
            int count,
            int height,
            float volume,
            float pitch) {
        public EnvironmentCue {
            id = id == null ? "" : id;
            if (!id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException("environment cue id must be stable");
            if (type == null || resource == null)
                throw new IllegalArgumentException(
                        "environment cue " + id + " requires type and resource");
            offset = offset == null ? BlockPos.ZERO : offset.immutable();
            if (Math.abs(offset.getX()) > 64
                    || Math.abs(offset.getY()) > 64
                    || Math.abs(offset.getZ()) > 64)
                throw new IllegalArgumentException(
                        "environment cue " + id + " offset exceeds 64 blocks");
            if (count < 1 || count > 128 || height < 1 || height > 64)
                throw new IllegalArgumentException(
                        "environment cue " + id + " count or height is out of bounds");
            if (volume < 0 || volume > 4 || pitch < 0.25F || pitch > 4)
                throw new IllegalArgumentException(
                        "environment cue " + id + " volume or pitch is out of bounds");
        }
    }

    public enum EnvironmentCueType {
        SOUND,
        MUSIC,
        PARTICLES,
        GLOWING_COLUMN
    }

    public record TemporaryBlock(String id, ResourceLocation block, BlockPos offset) {
        public TemporaryBlock {
            id = id == null ? "" : id;
            if (!id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException("temporary block id must be stable");
            if (block == null)
                throw new IllegalArgumentException("temporary block " + id + " requires a block");
            offset = offset == null ? BlockPos.ZERO : offset.immutable();
            if (Math.abs(offset.getX()) > 64
                    || Math.abs(offset.getY()) > 64
                    || Math.abs(offset.getZ()) > 64)
                throw new IllegalArgumentException(
                        "temporary block " + id + " offset exceeds 64 blocks");
        }
    }

    public record Guidance(
            String coordinateMessage,
            String arrivalMessage,
            int discoveryRadius,
            int arrivalRadius,
            boolean distanceTracker,
            boolean compassTarget,
            boolean directionalParticles,
            boolean hudMarker,
            ExactCoordinates exactCoordinates,
            int updateIntervalTicks) {
        public Guidance {
            coordinateMessage = coordinateMessage == null ? "" : coordinateMessage;
            arrivalMessage = arrivalMessage == null ? "" : arrivalMessage;
            if (coordinateMessage.length() > 512 || arrivalMessage.length() > 512)
                throw new IllegalArgumentException(
                        "guidance messages must not exceed 512 characters");
            if (discoveryRadius < 1 || discoveryRadius > 512)
                throw new IllegalArgumentException(
                        "guidance discovery_radius must be between 1 and 512");
            if (arrivalRadius < 1 || arrivalRadius > 64 || arrivalRadius > discoveryRadius)
                throw new IllegalArgumentException(
                        "guidance arrival_radius must be between 1 and 64 and no larger than discovery_radius");
            exactCoordinates =
                    exactCoordinates == null ? ExactCoordinates.AFTER_DISCOVERY : exactCoordinates;
            if (updateIntervalTicks < 10 || updateIntervalTicks > 200)
                throw new IllegalArgumentException(
                        "guidance update_interval_ticks must be between 10 and 200");
            if (coordinateMessage.isBlank()
                    && arrivalMessage.isBlank()
                    && !distanceTracker
                    && !compassTarget
                    && !directionalParticles
                    && !hudMarker)
                throw new IllegalArgumentException(
                        "guidance requires at least one visible feature");
        }
    }

    public enum ExactCoordinates {
        ALWAYS,
        AFTER_DISCOVERY,
        NEVER
    }

    public record RewardPolicy(
            List<Reward> waves,
            List<Reward> phases,
            List<Reward> completion,
            List<Trophy> trophies,
            DropPolicy dropPolicy) {
        public RewardPolicy {
            waves = waves == null ? List.of() : List.copyOf(waves);
            phases = phases == null ? List.of() : List.copyOf(phases);
            completion = completion == null ? List.of() : List.copyOf(completion);
            trophies = trophies == null ? List.of() : List.copyOf(trophies);
            dropPolicy = dropPolicy == null ? DropPolicy.NORMAL : dropPolicy;
            if (waves.size() > 32
                    || phases.size() > 32
                    || completion.size() > 32
                    || trophies.size() > 32
                    || waves.size() + phases.size() + completion.size() > 64)
                throw new IllegalArgumentException("encounter rewards exceed bounded limits");
            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            for (Reward reward :
                    java.util.stream.Stream.of(waves, phases, completion)
                            .flatMap(List::stream)
                            .toList())
                if (!ids.add(reward.id()))
                    throw new IllegalArgumentException(
                            "duplicate encounter reward id " + reward.id());
            for (Trophy trophy : trophies)
                if (!ids.add(trophy.id()))
                    throw new IllegalArgumentException(
                            "duplicate encounter reward id " + trophy.id());
            if (dropPolicy == DropPolicy.TROPHY_ONLY && trophies.isEmpty())
                throw new IllegalArgumentException("trophy_only drop policy requires trophies");
        }
    }

    public record Reward(
            String id,
            String target,
            ResourceLocation lootTable,
            ResourceLocation item,
            int count,
            String trophyName) {
        public Reward {
            id = id == null ? "" : id;
            target = target == null ? "" : target;
            trophyName = trophyName == null ? "" : trophyName;
            if (!id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException("reward id must be stable");
            if ((lootTable == null) == (item == null))
                throw new IllegalArgumentException(
                        "reward " + id + " requires exactly one loot_table or item");
            if (item == null && count != 1)
                throw new IllegalArgumentException(
                        "loot-table reward " + id + " cannot configure count");
            if (count < 1 || count > 64)
                throw new IllegalArgumentException(
                        "reward " + id + " count must be between 1 and 64");
            if (item == null && !trophyName.isBlank())
                throw new IllegalArgumentException(
                        "reward " + id + " trophy_name requires an item");
            if (trophyName.length() > 128)
                throw new IllegalArgumentException(
                        "reward " + id + " trophy_name must not exceed 128 characters");
        }
    }

    public record Trophy(
            String id, String memberId, ResourceLocation item, int count, String name) {
        public Trophy {
            id = id == null ? "" : id;
            memberId = memberId == null ? "" : memberId;
            name = name == null ? "" : name;
            if (!id.matches("[a-z][a-z0-9_.-]{0,63}")
                    || !memberId.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException("trophy id and member must be stable");
            if (item == null || count < 1 || count > 64)
                throw new IllegalArgumentException(
                        "trophy " + id + " requires an item count from 1 to 64");
            if (name.length() > 128)
                throw new IllegalArgumentException(
                        "trophy " + id + " name must not exceed 128 characters");
        }
    }

    public enum DropPolicy {
        NORMAL,
        SUPPRESS,
        AUTHORED_ONLY,
        TROPHY_ONLY
    }

    public record FailurePolicy(
            FailureAction onPlayerDeath,
            FailureAction onProtectedActorDeath,
            int retryDelayTicks,
            int maxAttempts,
            boolean retainDefeated,
            String branchStep) {
        public FailurePolicy {
            onPlayerDeath = onPlayerDeath == null ? FailureAction.FAIL : onPlayerDeath;
            onProtectedActorDeath =
                    onProtectedActorDeath == null ? FailureAction.FAIL : onProtectedActorDeath;
            if (retryDelayTicks < 0 || retryDelayTicks > 12000)
                throw new IllegalArgumentException(
                        "failure retry_delay_ticks must be between 0 and 12000");
            if (maxAttempts < 1 || maxAttempts > 16)
                throw new IllegalArgumentException("failure max_attempts must be between 1 and 16");
            branchStep = branchStep == null ? "" : branchStep;
            if ((onPlayerDeath == FailureAction.BRANCH_SCENE
                            || onProtectedActorDeath == FailureAction.BRANCH_SCENE)
                    != !branchStep.isBlank())
                throw new IllegalArgumentException(
                        "failure branch_step is required exactly when branch_scene is used");
            if (!branchStep.isBlank() && !branchStep.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException(
                        "failure branch_step must be a stable scene step id");
        }
    }

    public enum FailureAction {
        FAIL,
        RESET_WAVE,
        RESTART_ENCOUNTER,
        PAUSE,
        BRANCH_SCENE
    }

    public record Ally(
            String id,
            ResourceLocation entityType,
            String actorAlias,
            int count,
            Map<EquipmentSlot, Gear> equipment,
            MobOptions options,
            boolean requiredSurvival,
            boolean invulnerable,
            boolean revivable,
            int reviveDelayTicks,
            AllyReplacementPolicy replacementPolicy,
            AllyCleanupPolicy cleanupPolicy,
            boolean affectsCompletion) {
        public Ally {
            id = id == null ? "" : id;
            if (!id.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException("ally id must be a stable lowercase identifier");
            actorAlias = actorAlias == null ? "" : actorAlias;
            if ((entityType == null) == actorAlias.isBlank())
                throw new IllegalArgumentException(
                        "ally " + id + " requires exactly one entity or actor source");
            if (!actorAlias.isBlank() && !actorAlias.matches("[a-z][a-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException(
                        "ally " + id + " actor must be a stable scene alias");
            if (!actorAlias.isBlank() && count != 1)
                throw new IllegalArgumentException("bound ally " + id + " must have count 1");
            if (count < 1 || count > 16)
                throw new IllegalArgumentException(
                        "ally " + id + " count must be between 1 and 16");
            equipment = equipment == null ? Map.of() : Map.copyOf(equipment);
            options = options == null ? MobOptions.DEFAULT : options;
            if (!actorAlias.isBlank() && (!equipment.isEmpty() || options != MobOptions.DEFAULT))
                throw new IllegalArgumentException(
                        "bound ally " + id + " cannot override entity equipment or presentation");
            if (requiredSurvival && revivable)
                throw new IllegalArgumentException(
                        "ally " + id + " required_survival and revivable are mutually exclusive");
            if (revivable && (reviveDelayTicks < 1 || reviveDelayTicks > 12000))
                throw new IllegalArgumentException(
                        "ally " + id + " revive_delay_ticks must be between 1 and 12000");
            if (!revivable && reviveDelayTicks != 0)
                throw new IllegalArgumentException(
                        "ally " + id + " revive_delay_ticks requires revivable true");
            replacementPolicy =
                    replacementPolicy == null ? AllyReplacementPolicy.NEVER : replacementPolicy;
            cleanupPolicy = cleanupPolicy == null ? AllyCleanupPolicy.REMOVE : cleanupPolicy;
        }
    }

    public enum AllyReplacementPolicy {
        NEVER,
        MISSING_IF_LOADED
    }

    public enum AllyCleanupPolicy {
        REMOVE,
        PRESERVE
    }

    public record Area(
            int radius,
            int verticalRadius,
            LeaveBehavior leaveBehavior,
            int leaveTimeoutTicks,
            MobBehavior mobBehavior,
            int mobTimeoutTicks) {
        public Area {
            if (radius < 1 || radius > 256)
                throw new IllegalArgumentException("area.radius must be between 1 and 256");
            if (verticalRadius < 1 || verticalRadius > 128)
                throw new IllegalArgumentException(
                        "area.vertical_radius must be between 1 and 128");
            if (leaveTimeoutTicks < 1 || leaveTimeoutTicks > 12000)
                throw new IllegalArgumentException(
                        "area.leave_timeout_ticks must be between 1 and 12000");
            if (mobTimeoutTicks < 1 || mobTimeoutTicks > 12000)
                throw new IllegalArgumentException(
                        "area.mob_timeout_ticks must be between 1 and 12000");
            leaveBehavior = leaveBehavior == null ? LeaveBehavior.IGNORE : leaveBehavior;
            mobBehavior = mobBehavior == null ? MobBehavior.IGNORE : mobBehavior;
        }
    }

    public enum LeaveBehavior {
        IGNORE,
        WARN,
        PAUSE,
        FAIL
    }

    public enum MobBehavior {
        IGNORE,
        RETURN,
        TELEPORT
    }

    public enum SpawnMode {
        GROUP,
        NEAR_PLAYER,
        FIXED,
        RAID_WAVES
    }

    public enum WaveTrigger {
        ALL_DEFEATED,
        TIMER
    }

    public enum RespawnPolicy {
        NEVER,
        MISSING_IF_LOADED,
        UNTIL_FIRST_DEFEAT
    }

    public enum CleanupPolicy {
        REMOVE_SURVIVORS,
        PRESERVE_IN_WORLD
    }

    public enum CompletionCondition {
        ALL_DEFEATED,
        ALL_GONE
    }
}
