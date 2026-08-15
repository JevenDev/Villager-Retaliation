package com.jvn.villagerretaliation.scene.persistence;

import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneState;
import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import com.jvn.villagerretaliation.scene.runtime.SceneAuditEntry;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import java.nio.charset.StandardCharsets;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.HashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public final class SceneSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 4;
    public static final int DEFAULT_COMPACTION_WORK = 16;
    public static final long TERMINAL_RETENTION_TICKS = 7L * 24000L;
    private static final int MAX_TOMBSTONES = 4096;
    private static final int MAX_AUDIT_ENTRIES = 2048;
    private static final String DATA_NAME = "villagerretaliation_scenes";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<UUID, SceneInstance> instances = new LinkedHashMap<>();
    private final Map<String, UUID> byOperation = new LinkedHashMap<>();
    private final Map<String, UUID> legacyActiveOperations = new LinkedHashMap<>();
    private final Map<String, OperationTombstone> tombstones = new LinkedHashMap<>();
    private final Map<UUID, EncounterInstance> encounters = new LinkedHashMap<>();
    private final Map<String, UUID> encounterOperations = new LinkedHashMap<>();
    private final List<SceneAuditEntry> auditEntries = new ArrayList<>();
    private final Map<UUID, SceneContinuation> continuations = new LinkedHashMap<>();
    private final Map<String, UUID> continuationKeys = new LinkedHashMap<>();
    private final ArrayDeque<UUID> pendingSceneCleanup = new ArrayDeque<>();
    private final Set<UUID> queuedSceneCleanup = new HashSet<>();
    private final ArrayDeque<UUID> pendingCompaction = new ArrayDeque<>();
    private final Set<UUID> queuedCompaction = new HashSet<>();
    private boolean futureVersion;
    private CompoundTag preservedFutureRoot;

    public static SceneSavedData get(ServerLevel level) {
        return level.getServer()
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(
                                SceneSavedData::new, SceneSavedData::load, DataFixTypes.LEVEL),
                        DATA_NAME);
    }

    public static SceneSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        SceneSaveMigrations.Result migration =
                SceneSaveMigrations.migrate(input, CURRENT_DATA_VERSION);
        SceneSavedData data = new SceneSavedData();
        data.futureVersion = migration.futureVersion();
        if (data.futureVersion) {
            data.preservedFutureRoot = migration.data().copy();
            LOGGER.error(
                    "Scene save DataVersion {} is newer than supported {}; preserving it without downgrade",
                    migration.sourceVersion(),
                    CURRENT_DATA_VERSION);
        }
        for (Tag raw : migration.data().getList("Instances", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag tag)) continue;
            try {
                SceneInstance instance = SceneInstance.load(tag);
                data.instances.put(instance.id(), instance);
                data.byOperation.put(operationKey(instance), instance.id());
                if (instance.state().terminal()) data.requestCompaction(instance);
                if (instance.cleanupStatus() == SceneInstance.CleanupStatus.RUNNING
                        || instance.cleanupStatus() == SceneInstance.CleanupStatus.BLOCKED)
                    data.requestCleanup(instance);
                if (instance.runIdentityKind() == SceneInstance.RunIdentityKind.LEGACY_OWNER
                        && !instance.state().terminal()) {
                    data.legacyActiveOperations.put(
                            legacyOperationKey(
                                    instance.owner(),
                                    instance.owningQuestId(),
                                    instance.operationId()),
                            instance.id());
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Could not read scene instance; preserving the rest of the scene save",
                        exception);
            }
        }
        for (Tag raw : migration.data().getList("Encounters", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag encounterTag)) {
                continue;
            }
            try {
                EncounterInstance encounter = EncounterInstance.load(encounterTag);
                data.encounters.put(encounter.id(), encounter);
                data.encounterOperations.put(
                        encounter.sceneId() + "|" + encounter.operationId(), encounter.id());
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Could not read encounter instance; preserving the rest of the scene save",
                        exception);
            }
        }
        for (Tag raw : migration.data().getList("Audit", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag auditTag)) {
                continue;
            }
            try {
                data.appendAuditEntry(SceneAuditEntry.load(auditTag));
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Could not read scene audit entry; preserving the rest of the scene save",
                        exception);
            }
        }
        for (Tag raw : migration.data().getList("Tombstones", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag tombstoneTag)) {
                continue;
            }
            try {
                OperationTombstone tombstone = OperationTombstone.load(tombstoneTag);
                if (!tombstone.operationKey().isBlank()) {
                    data.putTombstone(tombstone);
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Could not read scene tombstone; preserving the rest of the scene save",
                        exception);
            }
        }
        for (Tag raw : migration.data().getList("Continuations", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag continuationTag)) continue;
            try {
                SceneContinuation continuation = SceneContinuation.load(continuationTag);
                data.continuations.put(continuation.id(), continuation);
                data.continuationKeys.put(
                        continuationKey(
                                continuation.sceneInstanceId(),
                                continuation.playerId(),
                                continuation.sourcePointer()),
                        continuation.id());
            } catch (RuntimeException exception) {
                LOGGER.error("Could not read scene continuation", exception);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag output, HolderLookup.Provider provider) {
        if (futureVersion && preservedFutureRoot != null) return preservedFutureRoot.copy();
        output.putInt(SceneSaveMigrations.DATA_VERSION, CURRENT_DATA_VERSION);
        output.putInt("SourceQuestDataVersion", SceneSaveMigrations.QUEST_SAVE_BASE_VERSION);
        ListTag values = new ListTag();
        instances.values().stream()
                .sorted(Comparator.comparing(value -> value.id().toString()))
                .forEach(value -> values.add(value.save()));
        output.put("Instances", values);
        ListTag encounterTags = new ListTag();
        encounters.values().stream()
                .sorted(Comparator.comparing(value -> value.id().toString()))
                .forEach(value -> encounterTags.add(value.save()));
        output.put("Encounters", encounterTags);
        ListTag audit = new ListTag();
        auditEntries.forEach(value -> audit.add(value.save()));
        output.put("Audit", audit);
        ListTag continuationTags = new ListTag();
        continuations.values().stream()
                .sorted(Comparator.comparing(value -> value.id().toString()))
                .forEach(value -> continuationTags.add(value.save()));
        output.put("Continuations", continuationTags);
        ListTag tombstoneTags = new ListTag();
        tombstones.values().forEach(value -> tombstoneTags.add(value.save()));
        output.put("Tombstones", tombstoneTags);
        return output;
    }

    public StartResult start(
            CompiledScene scene,
            String operationId,
            SceneOwner owner,
            UUID owningQuestInstance,
            Set<UUID> participants,
            Map<String, com.jvn.villagerretaliation.scene.actor.SceneActorBinding> bindings,
            long time) {
        return start(
                scene, operationId, owner, owningQuestInstance, participants, bindings, time, null);
    }

    public StartResult start(
            CompiledScene scene,
            String operationId,
            SceneOwner owner,
            UUID owningQuestInstance,
            Set<UUID> participants,
            Map<String, com.jvn.villagerretaliation.scene.actor.SceneActorBinding> bindings,
            long time,
            ResourceLocation questId) {
        String key = operationKey(scene.id(), owner, questId, owningQuestInstance, operationId);
        UUID existingId = byOperation.get(key);
        OperationTombstone tombstone = tombstones.get(key);
        if (existingId == null && tombstone != null) {
            return new StartResult(null, false, tombstone.instanceId());
        }
        if (existingId == null) {
            UUID legacyId =
                    legacyActiveOperations.get(legacyOperationKey(owner, questId, operationId));
            SceneInstance legacy = instances.get(legacyId);
            if (legacy != null
                    && legacy.sceneId().equals(scene.id())
                    && !legacy.state().terminal()) {
                existingId = legacyId;
                byOperation.put(key, legacyId);
            }
        }
        if (existingId != null) {
            SceneInstance existing = instances.get(existingId);
            if (existing != null && existing.mergeLaunchContext(participants, bindings)) {
                setDirty();
            }
            return new StartResult(existing, false, existingId);
        }
        SceneInstance instance =
                new SceneInstance(
                        UUID.randomUUID(),
                        scene,
                        operationId,
                        owner,
                        owningQuestInstance,
                        participants,
                        bindings,
                        time);
        instance.linkQuest(questId);
        instances.put(instance.id(), instance);
        byOperation.put(key, instance.id());
        setDirty();
        return new StartResult(instance, true, instance.id());
    }

    public Optional<SceneInstance> get(UUID id) {
        return Optional.ofNullable(instances.get(id));
    }

    public List<SceneInstance> all() {
        return List.copyOf(instances.values());
    }

    public List<SceneInstance> active() {
        return instances.values().stream().filter(value -> !value.state().terminal()).toList();
    }

    public boolean hasActiveActor(UUID entityId) {
        if (entityId == null) return false;
        for (SceneInstance scene : instances.values()) {
            if (scene.state().terminal()) continue;
            for (var binding : scene.actorBindings().values()) {
                if (entityId.equals(binding.entityId())) return true;
            }
        }
        return false;
    }

    public List<SceneInstance> byState(SceneState state) {
        return instances.values().stream().filter(value -> value.state() == state).toList();
    }

    public void changed() {
        if (!futureVersion) setDirty();
    }

    public boolean futureVersion() {
        return futureVersion;
    }

    public EncounterStartResult startEncounter(
            EncounterTemplate template,
            SceneInstance scene,
            String operationId,
            ResourceLocation dimension,
            net.minecraft.core.BlockPos anchor,
            String difficulty) {
        return startEncounter(
                template, scene, operationId, dimension, anchor, difficulty, List.of());
    }

    public EncounterStartResult startEncounter(
            EncounterTemplate template,
            SceneInstance scene,
            String operationId,
            ResourceLocation dimension,
            net.minecraft.core.BlockPos anchor,
            String difficulty,
            java.util.Collection<EncounterInstance.ResolvedSpawnPoint> spawnPoints) {
        return startEncounter(
                template,
                template.id(),
                "",
                0L,
                scene,
                operationId,
                dimension,
                anchor,
                difficulty,
                spawnPoints);
    }

    public EncounterStartResult startEncounter(
            EncounterTemplate template,
            ResourceLocation sourceTemplate,
            String selectedVariant,
            long variantSeed,
            SceneInstance scene,
            String operationId,
            ResourceLocation dimension,
            net.minecraft.core.BlockPos anchor,
            String difficulty,
            java.util.Collection<EncounterInstance.ResolvedSpawnPoint> spawnPoints) {
        String key = scene.id() + "|" + operationId;
        UUID existing = encounterOperations.get(key);
        if (existing != null) return new EncounterStartResult(encounters.get(existing), false);
        EncounterInstance encounter =
                new EncounterInstance(
                        UUID.randomUUID(),
                        template.id(),
                        sourceTemplate,
                        selectedVariant,
                        variantSeed,
                        scene.id(),
                        operationId,
                        scene.owner().stableKey(),
                        scene.participants(),
                        dimension,
                        anchor,
                        Math.max(1, scene.participants().size()),
                        difficulty,
                        template.cleanupPolicy(),
                        template.completionCondition(),
                        template.totalCount(Math.max(1, scene.participants().size())));
        if (template.completionObjectives() != null) encounter.enableCustomCompletion();
        encounter.initializeWave(0, template.wave(0).id());
        encounter.setResolvedSpawnPoints(spawnPoints);
        encounters.put(encounter.id(), encounter);
        encounterOperations.put(key, encounter.id());
        setDirty();
        return new EncounterStartResult(encounter, true);
    }

    public Optional<EncounterInstance> encounter(UUID id) {
        return Optional.ofNullable(encounters.get(id));
    }

    public List<EncounterInstance> encounters() {
        return List.copyOf(encounters.values());
    }

    public List<SceneContinuation> continuations() {
        return List.copyOf(continuations.values());
    }

    public List<OperationTombstone> tombstones() {
        return List.copyOf(tombstones.values());
    }

    public int compactTerminalHistory(long gameTime, int maximumWork) {
        int limit = Math.max(1, maximumWork);
        int inspected = 0;
        int compacted = 0;
        while (inspected < limit && !pendingCompaction.isEmpty()) {
            UUID sceneId = pendingCompaction.removeFirst();
            queuedCompaction.remove(sceneId);
            inspected++;
            SceneInstance scene = instances.get(sceneId);
            if (scene == null || !scene.state().terminal()) continue;
            if (gameTime - scene.updateGameTime() < TERMINAL_RETENTION_TICKS
                    || !settledForCompaction(scene)) {
                requestCompaction(scene);
                continue;
            }
            String key = operationKey(scene);
            Set<String> completedReceipts =
                    new java.util.LinkedHashSet<>(scene.receipts().keySet());
            continuations.values().stream()
                    .filter(
                            continuation ->
                                    continuation.sceneInstanceId().equals(scene.id())
                                            && continuation.completionReceipt())
                    .map(continuation -> "continuation:" + continuation.id())
                    .forEach(completedReceipts::add);
            putTombstone(
                    new OperationTombstone(
                            key,
                            scene.id(),
                            scene.sceneId(),
                            scene.completionResult(),
                            scene.updateGameTime(),
                            gameTime,
                            Set.copyOf(completedReceipts)));
            instances.remove(scene.id());
            byOperation.entrySet().removeIf(value -> value.getValue().equals(scene.id()));
            legacyActiveOperations
                    .entrySet()
                    .removeIf(value -> value.getValue().equals(scene.id()));
            removeSettledEncounters(scene.id());
            removeCompletedContinuations(scene.id());
            compacted++;
        }
        int pruned = 0;
        while (tombstones.size() > MAX_TOMBSTONES && pruned < limit) {
            tombstones.remove(tombstones.keySet().iterator().next());
            pruned++;
        }
        if (compacted > 0 || pruned > 0) setDirty();
        return compacted;
    }

    private boolean settledForCompaction(SceneInstance scene) {
        if (scene.cleanupStatus() != SceneInstance.CleanupStatus.COMPLETE
                || !scene.pendingOperations().isEmpty()
                || scene.receipts().values().stream()
                        .anyMatch(
                                receipt ->
                                        receipt.state()
                                                != com.jvn.villagerretaliation.scene.runtime
                                                        .SceneOperationReceipt.ReceiptState
                                                        .COMPLETED)
                || continuations.values().stream()
                        .anyMatch(
                                continuation ->
                                        continuation.sceneInstanceId().equals(scene.id())
                                                && !continuation.completionReceipt())) return false;
        return encounters.values().stream()
                .filter(encounter -> encounter.sceneId().equals(scene.id()))
                .allMatch(
                        encounter ->
                                !encounter.completionRewardEligible()
                                        && (encounter.state()
                                                        == EncounterInstance.EncounterState.CLEANED
                                                || encounter.state()
                                                        == EncounterInstance.EncounterState
                                                                .RELEASED));
    }

    private void removeSettledEncounters(UUID sceneId) {
        Set<UUID> removed = new HashSet<>();
        encounters
                .entrySet()
                .removeIf(
                        entry -> {
                            if (!entry.getValue().sceneId().equals(sceneId)) return false;
                            removed.add(entry.getKey());
                            return true;
                        });
        encounterOperations.entrySet().removeIf(entry -> removed.contains(entry.getValue()));
    }

    private void removeCompletedContinuations(UUID sceneId) {
        Set<UUID> removed = new HashSet<>();
        continuations
                .entrySet()
                .removeIf(
                        entry -> {
                            SceneContinuation continuation = entry.getValue();
                            if (!continuation.sceneInstanceId().equals(sceneId)
                                    || !continuation.completionReceipt()) return false;
                            removed.add(entry.getKey());
                            return true;
                        });
        continuationKeys.entrySet().removeIf(entry -> removed.contains(entry.getValue()));
    }

    public SceneContinuation suspendContinuation(
            SceneInstance scene,
            UUID playerId,
            UUID providerId,
            String sourcePointer,
            List<VillagerActionDefinition> actions,
            int nextActionIndex,
            Map<String, String> replacements) {
        String key = continuationKey(scene.id(), playerId, sourcePointer);
        UUID existingId = continuationKeys.get(key);
        SceneContinuation existing = continuations.get(existingId);
        if (existing != null && !existing.completionReceipt()) return existing;
        UUID id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        SceneContinuation continuation =
                new SceneContinuation(
                        id,
                        scene.id(),
                        playerId,
                        providerId,
                        scene.owningQuestId(),
                        scene.owningQuestInstance(),
                        sourcePointer,
                        actions,
                        nextActionIndex,
                        replacements);
        continuations.put(id, continuation);
        continuationKeys.put(key, id);
        setDirty();
        return continuation;
    }

    public Optional<EncounterInstance> encounterByOperation(UUID sceneId, String operationId) {
        UUID id = encounterOperations.get(sceneId + "|" + operationId);
        return Optional.ofNullable(id == null ? null : encounters.get(id));
    }

    public void audit(SceneAuditEntry entry) {
        appendAuditEntry(entry);
        setDirty();
    }

    public List<SceneAuditEntry> auditEntries() {
        return List.copyOf(auditEntries);
    }

    private void appendAuditEntry(SceneAuditEntry entry) {
        if (entry == null) {
            return;
        }
        auditEntries.add(entry);
        while (auditEntries.size() > MAX_AUDIT_ENTRIES) {
            auditEntries.removeFirst();
        }
    }

    private void putTombstone(OperationTombstone tombstone) {
        tombstones.put(tombstone.operationKey(), tombstone);
        while (tombstones.size() > MAX_TOMBSTONES) {
            tombstones.remove(tombstones.keySet().iterator().next());
        }
    }

    public void requestCleanup(SceneInstance instance) {
        if (instance == null) return;
        if (instance.state().terminal()) requestCompaction(instance);
        if (instance.cleanupStatus() != SceneInstance.CleanupStatus.COMPLETE
                && queuedSceneCleanup.add(instance.id()))
            pendingSceneCleanup.addLast(instance.id());
    }

    private void requestCompaction(SceneInstance instance) {
        if (instance != null && queuedCompaction.add(instance.id()))
            pendingCompaction.addLast(instance.id());
    }

    public List<SceneInstance> takeCleanupBatch(int maximum) {
        return takeCleanupBatch(maximum, Long.MAX_VALUE);
    }

    public List<SceneInstance> takeCleanupBatch(int maximum, long gameTime) {
        List<SceneInstance> result = new ArrayList<>();
        int inspected = 0;
        int inspectionLimit = Math.max(1, maximum);
        while (result.size() < inspectionLimit
                && inspected < inspectionLimit
                && !pendingSceneCleanup.isEmpty()) {
            UUID id = pendingSceneCleanup.removeFirst();
            queuedSceneCleanup.remove(id);
            inspected++;
            SceneInstance instance = instances.get(id);
            if (instance == null
                    || instance.cleanupStatus() == SceneInstance.CleanupStatus.COMPLETE) continue;
            if (instance.cleanupRetryAt() > gameTime) {
                requestCleanup(instance);
                continue;
            }
            result.add(instance);
        }
        return List.copyOf(result);
    }

    private static String operationKey(SceneInstance instance) {
        return operationKey(
                instance.sceneId(),
                instance.owner(),
                instance.owningQuestId(),
                instance.owningQuestInstance(),
                instance.operationId());
    }

    private static String operationKey(
            ResourceLocation sceneId,
            SceneOwner owner,
            ResourceLocation questId,
            UUID questRunId,
            String operationId) {
        return "scene:"
                + sceneId
                + "|owner:"
                + owner.stableKey()
                + "|quest:"
                + (questId == null ? "-" : questId)
                + "|run:"
                + (questRunId == null ? "-" : questRunId)
                + "|operation:"
                + operationId;
    }

    private static String legacyOperationKey(
            SceneOwner owner, ResourceLocation questId, String operationId) {
        String ownerKey =
                owner.mode()
                                        == com.jvn.villagerretaliation.scene.model.SceneResource
                                                .OwnershipMode.QUEST_INSTANCE
                                && owner.playerId() != null
                        ? "quest-player:" + owner.playerId()
                        : owner.stableKey();
        return ownerKey
                + "|quest:"
                + (questId == null ? "-" : questId)
                + "|operation:"
                + operationId;
    }

    private static String continuationKey(UUID sceneId, UUID playerId, String sourcePointer) {
        return sceneId
                + "|player:"
                + playerId
                + "|source:"
                + (sourcePointer == null ? "" : sourcePointer);
    }

    public record StartResult(SceneInstance instance, boolean created, UUID instanceId) {}

    public record EncounterStartResult(EncounterInstance encounter, boolean created) {}

    public record OperationTombstone(
            String operationKey,
            UUID instanceId,
            ResourceLocation sceneId,
            SceneInstance.CompletionResult result,
            long terminalGameTime,
            long compactedGameTime,
            Set<String> completedReceiptIds) {
        public OperationTombstone {
            operationKey = operationKey == null ? "" : operationKey;
            result = result == null ? SceneInstance.CompletionResult.NONE : result;
            completedReceiptIds =
                    completedReceiptIds == null ? Set.of() : Set.copyOf(completedReceiptIds);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("OperationKey", operationKey);
            tag.putUUID("InstanceId", instanceId);
            tag.putString("SceneId", sceneId.toString());
            tag.putString("Result", result.name());
            tag.putLong("TerminalGameTime", terminalGameTime);
            tag.putLong("CompactedGameTime", compactedGameTime);
            tag.put(
                    "CompletedReceiptIds",
                    com.jvn.villagerretaliation.util.NbtDataUtil.stringList(completedReceiptIds));
            return tag;
        }

        static OperationTombstone load(CompoundTag tag) {
            SceneInstance.CompletionResult result;
            try {
                result = SceneInstance.CompletionResult.valueOf(tag.getString("Result"));
            } catch (IllegalArgumentException ignored) {
                result = SceneInstance.CompletionResult.NONE;
            }
            return new OperationTombstone(
                    tag.getString("OperationKey"),
                    tag.getUUID("InstanceId"),
                    ResourceLocation.parse(tag.getString("SceneId")),
                    result,
                    tag.getLong("TerminalGameTime"),
                    tag.getLong("CompactedGameTime"),
                    com.jvn.villagerretaliation.util.NbtDataUtil.readStringSet(
                            tag, "CompletedReceiptIds"));
        }
    }
}
