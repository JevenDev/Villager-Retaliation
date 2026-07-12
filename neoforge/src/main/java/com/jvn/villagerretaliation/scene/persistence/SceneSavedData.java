package com.jvn.villagerretaliation.scene.persistence;

import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneState;
import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import com.jvn.villagerretaliation.scene.runtime.SceneAuditEntry;
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
    public static final int CURRENT_DATA_VERSION = 3;
    private static final String DATA_NAME = "villagerretaliation_scenes";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<UUID, SceneInstance> instances = new LinkedHashMap<>();
    private final Map<String, UUID> byOperation = new LinkedHashMap<>();
    private final Map<String, UUID> legacyActiveOperations = new LinkedHashMap<>();
    private final Map<UUID, EncounterInstance> encounters = new LinkedHashMap<>();
    private final Map<String, UUID> encounterOperations = new LinkedHashMap<>();
    private final List<SceneAuditEntry> auditEntries = new ArrayList<>();
    private final ArrayDeque<UUID> pendingSceneCleanup = new ArrayDeque<>();
    private final Set<UUID> queuedSceneCleanup = new HashSet<>();
    private boolean futureVersion;
    private CompoundTag preservedFutureRoot;

    public static SceneSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SceneSavedData::new, SceneSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
    }

    public static SceneSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        SceneSaveMigrations.Result migration = SceneSaveMigrations.migrate(input, CURRENT_DATA_VERSION);
        SceneSavedData data = new SceneSavedData();
        data.futureVersion = migration.futureVersion();
        if (data.futureVersion) {
            data.preservedFutureRoot = migration.data().copy();
            LOGGER.error("Scene save DataVersion {} is newer than supported {}; preserving it without downgrade",
                    migration.sourceVersion(), CURRENT_DATA_VERSION);
        }
        for (Tag raw : migration.data().getList("Instances", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag tag)) continue;
            try {
                SceneInstance instance = SceneInstance.load(tag);
                data.instances.put(instance.id(), instance);
                data.byOperation.put(operationKey(instance), instance.id());
                if (instance.cleanupStatus() == SceneInstance.CleanupStatus.RUNNING
                        || instance.cleanupStatus() == SceneInstance.CleanupStatus.BLOCKED) data.requestCleanup(instance);
                if (instance.runIdentityKind() == SceneInstance.RunIdentityKind.LEGACY_OWNER
                        && !instance.state().terminal()) {
                    data.legacyActiveOperations.put(legacyOperationKey(instance.owner(), instance.owningQuestId(),
                            instance.operationId()), instance.id());
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Could not read scene instance; preserving the rest of the scene save", exception);
            }
        }
        for(Tag raw:migration.data().getList("Encounters",Tag.TAG_COMPOUND))if(raw instanceof CompoundTag encounterTag){try{EncounterInstance encounter=EncounterInstance.load(encounterTag);data.encounters.put(encounter.id(),encounter);data.encounterOperations.put(encounter.sceneId()+"|"+encounter.operationId(),encounter.id());}catch(RuntimeException exception){LOGGER.error("Could not read encounter instance",exception);}}
        for(Tag raw:migration.data().getList("Audit",Tag.TAG_COMPOUND))if(raw instanceof CompoundTag audit)data.auditEntries.add(SceneAuditEntry.load(audit));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag output, HolderLookup.Provider provider) {
        if (futureVersion && preservedFutureRoot != null) return preservedFutureRoot.copy();
        output.putInt(SceneSaveMigrations.DATA_VERSION, CURRENT_DATA_VERSION);
        output.putInt("SourceQuestDataVersion", SceneSaveMigrations.QUEST_SAVE_BASE_VERSION);
        ListTag values = new ListTag();
        instances.values().stream().sorted(Comparator.comparing(value -> value.id().toString()))
                .forEach(value -> values.add(value.save()));
        output.put("Instances", values);
        ListTag encounterTags=new ListTag();encounters.values().stream().sorted(Comparator.comparing(value->value.id().toString())).forEach(value->encounterTags.add(value.save()));output.put("Encounters",encounterTags);
        ListTag audit=new ListTag();auditEntries.forEach(value->audit.add(value.save()));output.put("Audit",audit);
        return output;
    }

    public StartResult start(CompiledScene scene, String operationId, SceneOwner owner, UUID owningQuestInstance,
            Set<UUID> participants, Map<String, com.jvn.villagerretaliation.scene.actor.SceneActorBinding> bindings, long time) {
        return start(scene, operationId, owner, owningQuestInstance, participants, bindings, time, null);
    }

    public StartResult start(CompiledScene scene, String operationId, SceneOwner owner, UUID owningQuestInstance,
            Set<UUID> participants, Map<String, com.jvn.villagerretaliation.scene.actor.SceneActorBinding> bindings,
            long time, ResourceLocation questId) {
        String key = operationKey(scene.id(), owner, questId, owningQuestInstance, operationId);
        UUID existingId = byOperation.get(key);
        if (existingId == null) {
            UUID legacyId = legacyActiveOperations.get(legacyOperationKey(owner, questId, operationId));
            SceneInstance legacy = instances.get(legacyId);
            if (legacy != null && legacy.sceneId().equals(scene.id()) && !legacy.state().terminal()) {
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
        SceneInstance instance = new SceneInstance(UUID.randomUUID(), scene, operationId, owner, owningQuestInstance,
                participants, bindings, time);
        instance.linkQuest(questId);
        instances.put(instance.id(), instance);
        byOperation.put(key, instance.id());
        setDirty();
        return new StartResult(instance, true, instance.id());
    }

    public Optional<SceneInstance> get(UUID id) { return Optional.ofNullable(instances.get(id)); }
    public List<SceneInstance> all() { return List.copyOf(instances.values()); }
    public List<SceneInstance> active() { return instances.values().stream().filter(value -> !value.state().terminal()).toList(); }
    public List<SceneInstance> byState(SceneState state) { return instances.values().stream().filter(value -> value.state()==state).toList(); }
    public void changed() { if (!futureVersion) setDirty(); }
    public boolean futureVersion() { return futureVersion; }
    public EncounterStartResult startEncounter(EncounterTemplate template,SceneInstance scene,String operationId,ResourceLocation dimension,net.minecraft.core.BlockPos anchor,String difficulty){return startEncounter(template,scene,operationId,dimension,anchor,difficulty,List.of());}
    public EncounterStartResult startEncounter(EncounterTemplate template,SceneInstance scene,String operationId,ResourceLocation dimension,net.minecraft.core.BlockPos anchor,String difficulty,java.util.Collection<EncounterInstance.ResolvedSpawnPoint> spawnPoints){return startEncounter(template,template.id(),"",0L,scene,operationId,dimension,anchor,difficulty,spawnPoints);}
    public EncounterStartResult startEncounter(EncounterTemplate template,ResourceLocation sourceTemplate,String selectedVariant,long variantSeed,SceneInstance scene,String operationId,ResourceLocation dimension,net.minecraft.core.BlockPos anchor,String difficulty,java.util.Collection<EncounterInstance.ResolvedSpawnPoint> spawnPoints){String key=scene.id()+"|"+operationId;UUID existing=encounterOperations.get(key);if(existing!=null)return new EncounterStartResult(encounters.get(existing),false);EncounterInstance encounter=new EncounterInstance(UUID.randomUUID(),template.id(),sourceTemplate,selectedVariant,variantSeed,scene.id(),operationId,scene.owner().stableKey(),scene.participants(),dimension,anchor,Math.max(1,scene.participants().size()),difficulty,template.cleanupPolicy(),template.completionCondition(),template.totalCount(Math.max(1,scene.participants().size())));if(template.completionObjectives()!=null)encounter.enableCustomCompletion();encounter.initializeWave(0,template.wave(0).id());encounter.setResolvedSpawnPoints(spawnPoints);encounters.put(encounter.id(),encounter);encounterOperations.put(key,encounter.id());setDirty();return new EncounterStartResult(encounter,true);}
    public Optional<EncounterInstance> encounter(UUID id){return Optional.ofNullable(encounters.get(id));}public List<EncounterInstance> encounters(){return List.copyOf(encounters.values());}
    public Optional<EncounterInstance> encounterByOperation(UUID sceneId,String operationId){UUID id=encounterOperations.get(sceneId+"|"+operationId);return Optional.ofNullable(id==null?null:encounters.get(id));}
    public void audit(SceneAuditEntry entry){auditEntries.add(entry);if(auditEntries.size()>2048)auditEntries.removeFirst();setDirty();}public List<SceneAuditEntry> auditEntries(){return List.copyOf(auditEntries);}
    public void requestCleanup(SceneInstance instance) {
        if (instance != null && instance.cleanupStatus() != SceneInstance.CleanupStatus.COMPLETE
                && queuedSceneCleanup.add(instance.id())) pendingSceneCleanup.addLast(instance.id());
    }
    public List<SceneInstance> takeCleanupBatch(int maximum) {
        List<SceneInstance> result = new ArrayList<>();
        while (result.size() < Math.max(1, maximum) && !pendingSceneCleanup.isEmpty()) {
            UUID id = pendingSceneCleanup.removeFirst();
            queuedSceneCleanup.remove(id);
            SceneInstance instance = instances.get(id);
            if (instance != null && instance.cleanupStatus() != SceneInstance.CleanupStatus.COMPLETE) result.add(instance);
        }
        return List.copyOf(result);
    }

    private static String operationKey(SceneInstance instance) {
        return operationKey(instance.sceneId(), instance.owner(), instance.owningQuestId(),
                instance.owningQuestInstance(), instance.operationId());
    }

    private static String operationKey(ResourceLocation sceneId, SceneOwner owner, ResourceLocation questId,
            UUID questRunId, String operationId) {
        return "scene:" + sceneId + "|owner:" + owner.stableKey() + "|quest:"
                + (questId == null ? "-" : questId) + "|run:"
                + (questRunId == null ? "-" : questRunId) + "|operation:" + operationId;
    }

    private static String legacyOperationKey(SceneOwner owner, ResourceLocation questId, String operationId) {
        String ownerKey = owner.mode() == com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.QUEST_INSTANCE
                && owner.playerId() != null
                ? "quest-player:" + owner.playerId()
                : owner.stableKey();
        return ownerKey + "|quest:" + (questId == null ? "-" : questId) + "|operation:" + operationId;
    }

    public record StartResult(SceneInstance instance, boolean created, UUID instanceId) { }
    public record EncounterStartResult(EncounterInstance encounter,boolean created){}
}
