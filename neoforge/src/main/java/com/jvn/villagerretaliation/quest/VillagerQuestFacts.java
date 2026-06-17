package com.jvn.villagerretaliation.quest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class VillagerQuestFacts extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_quest_facts";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_SCOPE = "Scope";
    private static final String TAG_TAGS = "Tags";
    private static final String TAG_VARIABLES = "Variables";
    private static final String TAG_COUNTERS = "Counters";
    private static final String TAG_KEY = "Key";
    private static final String TAG_VALUE = "Value";

    private final Map<String, FactBucket> factsByScope = new HashMap<>();

    public static VillagerQuestFacts get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerQuestFacts::new, VillagerQuestFacts::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerQuestFacts load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerQuestFacts data = new VillagerQuestFacts();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag) || !entryTag.contains(TAG_SCOPE, Tag.TAG_STRING)) {
                continue;
            }
            String scopeKey = entryTag.getString(TAG_SCOPE);
            if (scopeKey.isBlank()) {
                continue;
            }
            FactBucket bucket = FactBucket.load(entryTag);
            if (!bucket.isEmpty()) {
                data.factsByScope.put(scopeKey, bucket);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Map.Entry<String, FactBucket> entry : this.factsByScope.entrySet()) {
            if (entry.getKey().isBlank() || entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag entryTag = entry.getValue().save();
            entryTag.putString(TAG_SCOPE, entry.getKey());
            entriesTag.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    public boolean setTag(String scopeKey, ResourceLocation tag) {
        if (scopeKey == null || scopeKey.isBlank() || tag == null) {
            return false;
        }
        boolean changed = bucket(scopeKey).tags.add(tag);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean clearTag(String scopeKey, ResourceLocation tag) {
        if (scopeKey == null || scopeKey.isBlank() || tag == null) {
            return false;
        }
        FactBucket bucket = this.factsByScope.get(scopeKey);
        if (bucket == null) {
            return false;
        }
        boolean changed = bucket.tags.remove(tag);
        removeEmptyBucket(scopeKey, bucket);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean hasTag(String scopeKey, ResourceLocation tag) {
        if (scopeKey == null || scopeKey.isBlank() || tag == null) {
            return false;
        }
        FactBucket bucket = this.factsByScope.get(scopeKey);
        return bucket != null && bucket.tags.contains(tag);
    }

    public boolean setVariable(String scopeKey, String key, String value) {
        key = normalizeKey(key);
        value = value == null ? "" : value;
        if (scopeKey == null || scopeKey.isBlank() || key.isBlank()) {
            return false;
        }
        String previous = bucket(scopeKey).variables.put(key, value);
        boolean changed = !value.equals(previous);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public Optional<String> variable(String scopeKey, String key) {
        key = normalizeKey(key);
        if (scopeKey == null || scopeKey.isBlank() || key.isBlank()) {
            return Optional.empty();
        }
        FactBucket bucket = this.factsByScope.get(scopeKey);
        return bucket == null ? Optional.empty() : Optional.ofNullable(bucket.variables.get(key));
    }

    public int addCounter(String scopeKey, String key, int amount) {
        key = normalizeKey(key);
        if (scopeKey == null || scopeKey.isBlank() || key.isBlank() || amount == 0) {
            return counter(scopeKey, key);
        }
        FactBucket bucket = bucket(scopeKey);
        int next = bucket.counters.getOrDefault(key, 0) + amount;
        bucket.counters.put(key, next);
        setDirty();
        return next;
    }

    public int counter(String scopeKey, String key) {
        key = normalizeKey(key);
        if (scopeKey == null || scopeKey.isBlank() || key.isBlank()) {
            return 0;
        }
        FactBucket bucket = this.factsByScope.get(scopeKey);
        return bucket == null ? 0 : bucket.counters.getOrDefault(key, 0);
    }

    private FactBucket bucket(String scopeKey) {
        return this.factsByScope.computeIfAbsent(scopeKey, ignored -> new FactBucket());
    }

    private void removeEmptyBucket(String scopeKey, FactBucket bucket) {
        if (bucket.isEmpty()) {
            this.factsByScope.remove(scopeKey);
        }
    }

    public static String normalizeKey(String key) {
        return key == null ? "" : key.trim();
    }

    private static final class FactBucket {
        private final Set<ResourceLocation> tags = new HashSet<>();
        private final Map<String, String> variables = new HashMap<>();
        private final Map<String, Integer> counters = new HashMap<>();

        private static FactBucket load(CompoundTag tag) {
            FactBucket bucket = new FactBucket();
            ListTag tagsTag = tag.getList(TAG_TAGS, Tag.TAG_STRING);
            for (Tag rawTag : tagsTag) {
                ResourceLocation tagId = ResourceLocation.tryParse(rawTag.getAsString());
                if (tagId != null) {
                    bucket.tags.add(tagId);
                }
            }
            readStringMap(tag.getList(TAG_VARIABLES, Tag.TAG_COMPOUND), bucket.variables);
            readIntMap(tag.getList(TAG_COUNTERS, Tag.TAG_COMPOUND), bucket.counters);
            return bucket;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (!this.tags.isEmpty()) {
                ListTag tagsTag = new ListTag();
                this.tags.stream()
                        .map(ResourceLocation::toString)
                        .sorted()
                        .map(StringTag::valueOf)
                        .forEach(tagsTag::add);
                tag.put(TAG_TAGS, tagsTag);
            }
            if (!this.variables.isEmpty()) {
                tag.put(TAG_VARIABLES, writeStringMap(this.variables));
            }
            if (!this.counters.isEmpty()) {
                tag.put(TAG_COUNTERS, writeIntMap(this.counters));
            }
            return tag;
        }

        private boolean isEmpty() {
            return this.tags.isEmpty() && this.variables.isEmpty() && this.counters.isEmpty();
        }

        private static void readStringMap(ListTag entries, Map<String, String> values) {
            for (Tag rawEntry : entries) {
                if (!(rawEntry instanceof CompoundTag entry)
                        || !entry.contains(TAG_KEY, Tag.TAG_STRING)
                        || !entry.contains(TAG_VALUE, Tag.TAG_STRING)) {
                    continue;
                }
                String key = normalizeKey(entry.getString(TAG_KEY));
                if (!key.isBlank()) {
                    values.put(key, entry.getString(TAG_VALUE));
                }
            }
        }

        private static void readIntMap(ListTag entries, Map<String, Integer> values) {
            for (Tag rawEntry : entries) {
                if (!(rawEntry instanceof CompoundTag entry)
                        || !entry.contains(TAG_KEY, Tag.TAG_STRING)
                        || !entry.contains(TAG_VALUE, Tag.TAG_INT)) {
                    continue;
                }
                String key = normalizeKey(entry.getString(TAG_KEY));
                if (!key.isBlank()) {
                    values.put(key, entry.getInt(TAG_VALUE));
                }
            }
        }

        private static ListTag writeStringMap(Map<String, String> values) {
            ListTag entries = new ListTag();
            values.entrySet().stream()
                    .filter(entry -> !entry.getKey().isBlank())
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        CompoundTag tag = new CompoundTag();
                        tag.putString(TAG_KEY, entry.getKey());
                        tag.putString(TAG_VALUE, entry.getValue());
                        entries.add(tag);
                    });
            return entries;
        }

        private static ListTag writeIntMap(Map<String, Integer> values) {
            ListTag entries = new ListTag();
            values.entrySet().stream()
                    .filter(entry -> !entry.getKey().isBlank())
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        CompoundTag tag = new CompoundTag();
                        tag.putString(TAG_KEY, entry.getKey());
                        tag.putInt(TAG_VALUE, entry.getValue());
                        entries.add(tag);
                    });
            return entries;
        }
    }
}
