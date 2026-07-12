package com.jvn.villagerretaliation.allegiance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class VillageAllegianceRegistrySavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_village_allegiances";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_ALIAS_DEPTH = 32;
    private final Map<VillageAllegianceId, AllegianceRecord> records = new LinkedHashMap<>();
    private final Map<VillageAllegianceId, VillageAllegianceId> aliases = new LinkedHashMap<>();
    private final Map<String, LinkedHashSet<VillageAllegianceId>> candidatesByScope = new LinkedHashMap<>();
    private final Map<VillageAllegianceId, Optional<VillageAllegianceId>> canonicalCache = new HashMap<>();

    public static VillageAllegianceRegistrySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageAllegianceRegistrySavedData::new, VillageAllegianceRegistrySavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static VillageAllegianceRegistrySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillageAllegianceRegistrySavedData data = new VillageAllegianceRegistrySavedData();
        if (tag.getInt("FormatVersion") > FORMAT_VERSION) {
            return data;
        }
        for (Tag raw : tag.getList("Records", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag recordTag) || !recordTag.hasUUID("Id")) {
                continue;
            }
            VillageAllegianceId id = new VillageAllegianceId(recordTag.getUUID("Id"));
            ResourceLocation dimension = ResourceLocation.tryParse(recordTag.getString("OriginDimension"));
            BlockPos position = new BlockPos(recordTag.getInt("OriginX"), recordTag.getInt("OriginY"), recordTag.getInt("OriginZ"));
            data.records.put(id, new AllegianceRecord(
                    id, recordTag.getLong("CreatedGameTime"), recordTag.getBoolean("Archived"),
                    recordTag.getString("DisplayName"), dimension, position));
        }
        for (Tag raw : tag.getList("Aliases", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag aliasTag && aliasTag.hasUUID("Source") && aliasTag.hasUUID("Target")) {
                data.aliases.put(
                        new VillageAllegianceId(aliasTag.getUUID("Source")),
                        new VillageAllegianceId(aliasTag.getUUID("Target")));
            }
        }
        for (Tag raw : tag.getList("Scopes", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag scopeTag)) {
                continue;
            }
            String scope = scopeTag.getString("Scope");
            if (scope.isBlank()) {
                continue;
            }
            LinkedHashSet<VillageAllegianceId> candidates = new LinkedHashSet<>();
            for (Tag candidateRaw : scopeTag.getList("Candidates", Tag.TAG_COMPOUND)) {
                if (candidateRaw instanceof CompoundTag candidateTag && candidateTag.hasUUID("Id")) {
                    candidates.add(new VillageAllegianceId(candidateTag.getUUID("Id")));
                }
            }
            if (!candidates.isEmpty()) {
                data.candidatesByScope.put(scope, candidates);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("FormatVersion", FORMAT_VERSION);
        ListTag recordTags = new ListTag();
        for (AllegianceRecord record : this.records.values()) {
            CompoundTag recordTag = new CompoundTag();
            recordTag.putUUID("Id", record.id().value());
            recordTag.putLong("CreatedGameTime", record.createdGameTime());
            recordTag.putBoolean("Archived", record.archived());
            recordTag.putString("DisplayName", record.displayName());
            if (record.originDimension() != null) {
                recordTag.putString("OriginDimension", record.originDimension().toString());
            }
            recordTag.putInt("OriginX", record.originPosition().getX());
            recordTag.putInt("OriginY", record.originPosition().getY());
            recordTag.putInt("OriginZ", record.originPosition().getZ());
            recordTags.add(recordTag);
        }
        tag.put("Records", recordTags);
        ListTag aliasTags = new ListTag();
        for (Map.Entry<VillageAllegianceId, VillageAllegianceId> alias : this.aliases.entrySet()) {
            CompoundTag aliasTag = new CompoundTag();
            aliasTag.putUUID("Source", alias.getKey().value());
            aliasTag.putUUID("Target", alias.getValue().value());
            aliasTags.add(aliasTag);
        }
        tag.put("Aliases", aliasTags);
        ListTag scopeTags = new ListTag();
        for (Map.Entry<String, LinkedHashSet<VillageAllegianceId>> mapping : this.candidatesByScope.entrySet()) {
            CompoundTag scopeTag = new CompoundTag();
            scopeTag.putString("Scope", mapping.getKey());
            ListTag candidateTags = new ListTag();
            for (VillageAllegianceId id : mapping.getValue()) {
                CompoundTag candidateTag = new CompoundTag();
                candidateTag.putUUID("Id", id.value());
                candidateTags.add(candidateTag);
            }
            scopeTag.put("Candidates", candidateTags);
            scopeTags.add(scopeTag);
        }
        tag.put("Scopes", scopeTags);
        return tag;
    }

    public VillageAllegianceId create(long gameTime, ResourceLocation dimension, BlockPos position, String displayName) {
        VillageAllegianceId id;
        do {
            id = VillageAllegianceId.random();
        } while (this.records.containsKey(id));
        this.records.put(id, new AllegianceRecord(id, gameTime, false, displayName, dimension, position));
        setDirty();
        return id;
    }

    public void ensureRecord(VillageAllegianceId id, long gameTime, ResourceLocation dimension, BlockPos position) {
        if (id != null && !this.records.containsKey(id)) {
            this.records.put(id, new AllegianceRecord(id, gameTime, false, "", dimension, position));
            setDirty();
        }
    }

    public Optional<AllegianceRecord> record(VillageAllegianceId id) {
        return Optional.ofNullable(this.records.get(id));
    }

    public List<AllegianceRecord> records() {
        return List.copyOf(this.records.values());
    }

    public boolean archive(VillageAllegianceId id) {
        AllegianceRecord current = this.records.get(id);
        if (current == null || current.archived()) {
            return false;
        }
        this.records.put(id, current.withArchived(true));
        setDirty();
        return true;
    }

    public Optional<VillageAllegianceId> canonical(VillageAllegianceId raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Optional<VillageAllegianceId> cached = this.canonicalCache.get(raw);
        if (cached != null) {
            return cached;
        }
        Set<VillageAllegianceId> seen = new HashSet<>();
        List<VillageAllegianceId> path = new ArrayList<>();
        VillageAllegianceId current = raw;
        for (int depth = 0; depth <= MAX_ALIAS_DEPTH; depth++) {
            if (!seen.add(current)) {
                return cachePath(path, Optional.empty());
            }
            path.add(current);
            VillageAllegianceId next = this.aliases.get(current);
            if (next == null) {
                return cachePath(path, Optional.of(current));
            }
            current = next;
        }
        return cachePath(path, Optional.empty());
    }

    public boolean merge(VillageAllegianceId source, VillageAllegianceId target) {
        if (source == null || target == null || source.equals(target)) {
            return false;
        }
        Optional<VillageAllegianceId> targetCanonical = canonical(target);
        Optional<VillageAllegianceId> sourceCanonical = canonical(source);
        if (targetCanonical.isEmpty() || sourceCanonical.isEmpty() || targetCanonical.get().equals(source)) {
            return false;
        }
        if (sourceCanonical.get().equals(targetCanonical.get())) {
            return true;
        }
        this.aliases.put(source, targetCanonical.get());
        this.canonicalCache.clear();
        if (canonical(source).isEmpty()) {
            this.aliases.remove(source);
            this.canonicalCache.clear();
            return false;
        }
        setDirty();
        return true;
    }

    public void addScopeCandidate(String scope, VillageAllegianceId id) {
        if (scope == null || scope.isBlank() || id == null) {
            return;
        }
        if (this.candidatesByScope.computeIfAbsent(scope, ignored -> new LinkedHashSet<>()).add(id)) {
            setDirty();
        }
    }

    public List<VillageAllegianceId> candidates(String scope) {
        LinkedHashSet<VillageAllegianceId> raw = this.candidatesByScope.get(scope);
        if (raw == null) {
            return List.of();
        }
        LinkedHashSet<VillageAllegianceId> canonical = new LinkedHashSet<>();
        for (VillageAllegianceId id : raw) {
            canonical(id).ifPresent(canonical::add);
        }
        return List.copyOf(canonical);
    }

    public Optional<VillageAllegianceId> uniqueCandidate(String scope) {
        List<VillageAllegianceId> candidates = candidates(scope);
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    public Map<String, List<VillageAllegianceId>> scopeMappings() {
        Map<String, List<VillageAllegianceId>> result = new LinkedHashMap<>();
        this.candidatesByScope.forEach((scope, ids) -> result.put(scope, List.copyOf(ids)));
        return Map.copyOf(result);
    }

    public int aliasCount() {
        return this.aliases.size();
    }

    public void clearRuntimeCache() {
        this.canonicalCache.clear();
    }

    private Optional<VillageAllegianceId> cachePath(
            List<VillageAllegianceId> path,
            Optional<VillageAllegianceId> result) {
        for (VillageAllegianceId id : path) {
            this.canonicalCache.put(id, result);
        }
        return result;
    }

    public record AllegianceRecord(
            VillageAllegianceId id,
            long createdGameTime,
            boolean archived,
            String displayName,
            ResourceLocation originDimension,
            BlockPos originPosition) {
        public AllegianceRecord {
            displayName = displayName == null ? "" : displayName;
            originPosition = originPosition == null ? BlockPos.ZERO : originPosition.immutable();
        }

        private AllegianceRecord withArchived(boolean value) {
            return new AllegianceRecord(this.id, this.createdGameTime, value, this.displayName, this.originDimension, this.originPosition);
        }
    }
}
