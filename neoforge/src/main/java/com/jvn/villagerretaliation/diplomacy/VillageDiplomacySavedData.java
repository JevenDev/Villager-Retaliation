package com.jvn.villagerretaliation.diplomacy;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class VillageDiplomacySavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_village_diplomacy";
    private static final int FORMAT_VERSION = 1;
    private final Map<Pair, Entry> relations = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();
    private ListTag rejectedEntries = new ListTag();
    private CompoundTag preservedRoot;
    private long revision;

    public static VillageDiplomacySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageDiplomacySavedData::new, VillageDiplomacySavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static VillageDiplomacySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new VillageDiplomacySavedData();
        if (!tag.contains("FormatVersion", Tag.TAG_INT) || tag.getInt("FormatVersion") != FORMAT_VERSION
                || !tag.contains("Relations", Tag.TAG_LIST)
                || !tag.contains("Revision", Tag.TAG_LONG) || tag.getLong("Revision") < 0
                || !tag.getList("Relations", Tag.TAG_COMPOUND).equals(tag.get("Relations"))) {
            data.preservedRoot = tag.copy();
            data.diagnostic("Unsupported or malformed diplomacy root preserved read-only");
            return data;
        }
        data.revision = tag.getLong("Revision");
        data.rejectedEntries = tag.getList("RejectedEntries", Tag.TAG_COMPOUND).copy();
        for (Tag raw : tag.getList("Relations", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                if (!entry.hasUUID("First") || !entry.hasUUID("Second")
                        || !entry.contains("State", Tag.TAG_STRING) || !entry.contains("ResumesAs", Tag.TAG_STRING)
                        || !entry.contains("Revision", Tag.TAG_LONG) || entry.getLong("Revision") <= 0
                        || !entry.contains("ExpiresAt", Tag.TAG_LONG)) {
                    throw new IllegalArgumentException("missing or invalid fields");
                }
                Pair pair = Pair.of(new VillageAllegianceId(entry.getUUID("First")),
                        new VillageAllegianceId(entry.getUUID("Second")));
                VillageRelation state = VillageRelation.valueOf(entry.getString("State"));
                VillageRelation resumes = VillageRelation.valueOf(entry.getString("ResumesAs"));
                long expires = entry.getLong("ExpiresAt");
                if (pair.first().equals(pair.second()) || expires < 0 || resumes == VillageRelation.TRUCE
                        || state != VillageRelation.TRUCE && (expires != 0 || resumes != state)) {
                    throw new IllegalArgumentException("invalid pair or truce");
                }
                Entry value = new Entry(state, expires, resumes, entry.getLong("Revision"));
                data.relations.merge(pair, value, VillageDiplomacySavedData::latest);
                data.revision = Math.max(data.revision, value.revision());
            } catch (IllegalArgumentException failure) {
                data.rejectedEntries.add(entry.copy());
                data.diagnostic("Rejected diplomacy entry: " + failure.getMessage());
                data.setDirty();
            }
        }
        if (!data.rejectedEntries.isEmpty()) {
            data.diagnostic("Preserved " + data.rejectedEntries.size() + " rejected diplomacy entries");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (this.preservedRoot != null) {
            return this.preservedRoot.copy();
        }
        tag.putInt("FormatVersion", FORMAT_VERSION);
        tag.putLong("Revision", this.revision);
        var entries = new ListTag();
        this.relations.forEach((pair, value) -> {
            var entry = new CompoundTag();
            entry.putUUID("First", pair.first().value());
            entry.putUUID("Second", pair.second().value());
            entry.putString("State", value.state().name());
            entry.putString("ResumesAs", value.resumesAs().name());
            entry.putLong("ExpiresAt", value.expiresAt());
            entry.putLong("Revision", value.revision());
            entries.add(entry);
        });
        tag.put("Relations", entries);
        if (!this.rejectedEntries.isEmpty()) {
            tag.put("RejectedEntries", this.rejectedEntries.copy());
        }
        return tag;
    }

    public boolean readOnly() {
        return this.preservedRoot != null || this.revision == Long.MAX_VALUE;
    }

    public List<String> diagnostics() {
        return List.copyOf(this.diagnostics);
    }

    VillageRelationSnapshot query(Pair pair, long now) {
        Entry entry = this.relations.get(pair);
        return entry == null ? VillageRelationSnapshot.NEUTRAL : entry.snapshot(now);
    }

    boolean set(Pair pair, VillageRelation state, long duration, long now) {
        if (readOnly()) {
            return false;
        }
        VillageRelationSnapshot previous = query(pair, now);
        VillageRelation resumes = state == VillageRelation.TRUCE
                ? previous.resumesAs() : state;
        long expires = state == VillageRelation.TRUCE && duration > 0 ? Math.addExact(now, duration) : 0;
        this.relations.put(pair, new Entry(state, expires, resumes, ++this.revision));
        setDirty();
        return true;
    }

    public void reconcile(VillageAllegianceRegistrySavedData registry) {
        if (this.preservedRoot != null || this.relations.isEmpty()) {
            return;
        }
        Map<Pair, Entry> canonical = new LinkedHashMap<>();
        this.relations.forEach((pair, value) -> {
            var first = registry.canonicalRecord(pair.first()).filter(record -> !record.archived());
            var second = registry.canonicalRecord(pair.second()).filter(record -> !record.archived());
            if (first.isPresent() && second.isPresent() && !first.get().id().equals(second.get().id())) {
                canonical.merge(Pair.of(first.get().id(), second.get().id()), value, VillageDiplomacySavedData::latest);
            }
        });
        if (!this.relations.equals(canonical)) {
            this.relations.clear();
            this.relations.putAll(canonical);
            setDirty();
        }
    }

    private static Entry latest(Entry first, Entry second) {
        return first.revision() >= second.revision() ? first : second;
    }

    private void diagnostic(String message) {
        this.diagnostics.add(message);
        LogUtils.getLogger().warn(message);
    }

    record Pair(VillageAllegianceId first, VillageAllegianceId second) {
        static Pair of(VillageAllegianceId first, VillageAllegianceId second) {
            return first.compareTo(second) <= 0 ? new Pair(first, second) : new Pair(second, first);
        }
    }

    private record Entry(VillageRelation state, long expiresAt, VillageRelation resumesAs, long revision) {
        VillageRelationSnapshot snapshot(long now) {
            if (state != VillageRelation.TRUCE) {
                return VillageRelationSnapshot.permanent(state);
            }
            if (expiresAt != 0 && now >= expiresAt) {
                return VillageRelationSnapshot.permanent(resumesAs);
            }
            return new VillageRelationSnapshot(state,
                    expiresAt == 0 ? OptionalLong.empty() : OptionalLong.of(expiresAt), resumesAs);
        }
    }
}
