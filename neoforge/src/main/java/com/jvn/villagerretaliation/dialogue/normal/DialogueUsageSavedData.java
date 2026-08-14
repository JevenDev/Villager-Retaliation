package com.jvn.villagerretaliation.dialogue.normal;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable, scope-neutral usage ledger for all dialogue surfaces. */
public final class DialogueUsageSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_dialogue_usage";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_SCOPE = "Scope";
    private static final String TAG_DIALOGUE_ID = "DialogueId";
    private static final String TAG_COUNT = "Count";
    private static final String TAG_GAME_TIME = "GameTime";
    private static final int MAX_ENTRIES = 16_384;

    private final LinkedHashMap<Key, Usage> entries = new LinkedHashMap<>();

    public static DialogueUsageSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DialogueUsageSavedData::new, DialogueUsageSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static DialogueUsageSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        DialogueUsageSavedData data = new DialogueUsageSavedData();
        for (Tag raw : tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag entry)) {
                continue;
            }
            String scope = entry.getString(TAG_SCOPE);
            String dialogueId = entry.getString(TAG_DIALOGUE_ID);
            if (scope.isBlank() || dialogueId.isBlank()) {
                continue;
            }
            data.entries.put(new Key(scope, dialogueId), new Usage(
                    Math.max(0, entry.getInt(TAG_COUNT)), entry.getLong(TAG_GAME_TIME)));
        }
        data.trim();
        return data;
    }

    public Usage usage(String scope, String dialogueId) {
        return this.entries.getOrDefault(new Key(scope, dialogueId), Usage.EMPTY);
    }

    public void remember(String scope, String dialogueId, long gameTime) {
        if (scope == null || scope.isBlank() || dialogueId == null || dialogueId.isBlank()) {
            return;
        }
        Key key = new Key(scope, dialogueId);
        Usage previous = this.entries.remove(key);
        this.entries.put(key, new Usage(previous == null ? 1 : previous.count() + 1, gameTime));
        trim();
        setDirty();
    }

    private void trim() {
        while (this.entries.size() > MAX_ENTRIES) {
            this.entries.remove(this.entries.keySet().iterator().next());
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Map.Entry<Key, Usage> value : this.entries.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_SCOPE, value.getKey().scope());
            entry.putString(TAG_DIALOGUE_ID, value.getKey().dialogueId());
            entry.putInt(TAG_COUNT, value.getValue().count());
            entry.putLong(TAG_GAME_TIME, value.getValue().lastUsedGameTime());
            entriesTag.add(entry);
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    private record Key(String scope, String dialogueId) {
    }

    public record Usage(int count, long lastUsedGameTime) {
        public static final Usage EMPTY = new Usage(0, Long.MIN_VALUE);
    }
}
