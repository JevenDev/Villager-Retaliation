package com.jvn.villagerretaliation.dialogue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class VillagerInteractionSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_interactions";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_VILLAGER = "Villager";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_HAS_TALKED = "HasTalked";
    private static final String TAG_RECENT_LINES = "RecentLines";
    private static final int MAX_RECENT_LINES = 5;

    private final Map<UUID, Map<UUID, InteractionEntry>> entries = new HashMap<>();

    public static VillagerInteractionSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerInteractionSavedData::new, VillagerInteractionSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerInteractionSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerInteractionSavedData data = new VillagerInteractionSavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)
                    || !entryTag.hasUUID(TAG_VILLAGER)
                    || !entryTag.hasUUID(TAG_PLAYER)) {
                continue;
            }

            InteractionEntry entry = new InteractionEntry();
            entry.hasTalked = entryTag.getBoolean(TAG_HAS_TALKED);
            ListTag recentLines = entryTag.getList(TAG_RECENT_LINES, Tag.TAG_STRING);
            for (Tag rawLine : recentLines) {
                entry.recentDialogueIds.addLast(rawLine.getAsString());
            }
            data.entries.computeIfAbsent(entryTag.getUUID(TAG_VILLAGER), ignored -> new HashMap<>())
                    .put(entryTag.getUUID(TAG_PLAYER), entry);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Map.Entry<UUID, Map<UUID, InteractionEntry>> villagerEntry : this.entries.entrySet()) {
            for (Map.Entry<UUID, InteractionEntry> playerEntry : villagerEntry.getValue().entrySet()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putUUID(TAG_VILLAGER, villagerEntry.getKey());
                entryTag.putUUID(TAG_PLAYER, playerEntry.getKey());
                entryTag.putBoolean(TAG_HAS_TALKED, playerEntry.getValue().hasTalked);
                ListTag recentLines = new ListTag();
                for (String lineId : playerEntry.getValue().recentDialogueIds) {
                    recentLines.add(StringTag.valueOf(lineId));
                }
                entryTag.put(TAG_RECENT_LINES, recentLines);
                entriesTag.add(entryTag);
            }
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    public InteractionEntry getOrCreate(UUID villagerId, UUID playerId) {
        return this.entries.computeIfAbsent(villagerId, ignored -> new HashMap<>())
                .computeIfAbsent(playerId, ignored -> new InteractionEntry());
    }

    public static class InteractionEntry {
        private boolean hasTalked;
        private final ArrayDeque<String> recentDialogueIds = new ArrayDeque<>();

        public boolean hasTalked() {
            return this.hasTalked;
        }

        public void markTalked() {
            this.hasTalked = true;
        }

        public List<String> recentDialogueIds() {
            return new ArrayList<>(this.recentDialogueIds);
        }

        public void rememberDialogueId(String dialogueId) {
            this.recentDialogueIds.remove(dialogueId);
            this.recentDialogueIds.addLast(dialogueId);
            while (this.recentDialogueIds.size() > MAX_RECENT_LINES) {
                this.recentDialogueIds.removeFirst();
            }
        }
    }
}
