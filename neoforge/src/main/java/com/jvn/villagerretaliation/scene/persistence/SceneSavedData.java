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
    public static final int CURRENT_DATA_VERSION = 2;
    private static final String DATA_NAME = "villagerretaliation_scenes";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<UUID, SceneInstance> instances = new LinkedHashMap<>();
    private final Map<String, UUID> byOperation = new LinkedHashMap<>();
    private final Map<UUID, EncounterInstance> encounters = new LinkedHashMap<>();
    private final Map<String, UUID> encounterOperations = new LinkedHashMap<>();
    private final List<SceneAuditEntry> auditEntries = new ArrayList<>();
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
                data.byOperation.put(operationKey(instance.owner(), instance.operationId()), instance.id());
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
        String key = operationKey(owner, operationId);
        UUID existingId = byOperation.get(key);
        if (existingId != null) return new StartResult(instances.get(existingId), false);
        SceneInstance instance = new SceneInstance(UUID.randomUUID(), scene, operationId, owner, owningQuestInstance,
                participants, bindings, time);
        instances.put(instance.id(), instance);
        byOperation.put(key, instance.id());
        setDirty();
        return new StartResult(instance, true);
    }

    public Optional<SceneInstance> get(UUID id) { return Optional.ofNullable(instances.get(id)); }
    public List<SceneInstance> all() { return List.copyOf(instances.values()); }
    public List<SceneInstance> active() { return instances.values().stream().filter(value -> !value.state().terminal()).toList(); }
    public List<SceneInstance> byState(SceneState state) { return instances.values().stream().filter(value -> value.state()==state).toList(); }
    public void changed() { if (!futureVersion) setDirty(); }
    public boolean futureVersion() { return futureVersion; }
    public EncounterStartResult startEncounter(EncounterTemplate template,SceneInstance scene,String operationId,ResourceLocation dimension,net.minecraft.core.BlockPos anchor,String difficulty){String key=scene.id()+"|"+operationId;UUID existing=encounterOperations.get(key);if(existing!=null)return new EncounterStartResult(encounters.get(existing),false);EncounterInstance encounter=new EncounterInstance(UUID.randomUUID(),template.id(),scene.id(),operationId,scene.owner().stableKey(),scene.participants(),dimension,anchor,Math.max(1,scene.participants().size()),difficulty,template.cleanupPolicy(),template.completionCondition(),template.scaledCount(Math.max(1,scene.participants().size())));encounters.put(encounter.id(),encounter);encounterOperations.put(key,encounter.id());setDirty();return new EncounterStartResult(encounter,true);}
    public Optional<EncounterInstance> encounter(UUID id){return Optional.ofNullable(encounters.get(id));}public List<EncounterInstance> encounters(){return List.copyOf(encounters.values());}
    public void audit(SceneAuditEntry entry){auditEntries.add(entry);if(auditEntries.size()>2048)auditEntries.removeFirst();setDirty();}public List<SceneAuditEntry> auditEntries(){return List.copyOf(auditEntries);}

    private static String operationKey(SceneOwner owner, String operationId) {
        return owner.stableKey() + "|" + operationId;
    }

    public record StartResult(SceneInstance instance, boolean created) { }
    public record EncounterStartResult(EncounterInstance encounter,boolean created){}
}
