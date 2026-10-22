package com.jvn.villagerretaliation.dialogue.normal;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

public final class DialogueTreeSessionSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_dialogue_trees";
    private final Map<DialogueTreeService.SessionKey, DialogueTreeService.Session> sessions = new LinkedHashMap<>();
    private final LinkedHashSet<DialogueTreeService.SessionKey> timedSessions = new LinkedHashSet<>();

    private final ListTag quarantined = new ListTag();
    private final Map<DialogueTreeService.SessionKey, String> issues = new LinkedHashMap<>();
    private CompoundTag preserved;
    private String diagnostic = "";

    public static DialogueTreeSessionSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(
                () -> missing(level), (tag, provider) -> load(tag)), DATA_NAME);
    }

    private static DialogueTreeSessionSavedData missing(ServerLevel level) {
        var data = new DialogueTreeSessionSavedData();
        var file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATA_NAME + ".dat");
        if (!Files.notExists(file)) data.diagnostic = "Dialogue tree data could not be read. Restore it from a known-good backup.";
        return data;
    }

    static DialogueTreeSessionSavedData load(CompoundTag tag) {
        var data = new DialogueTreeSessionSavedData();
        try {
            if (tag.contains("DataVersion") && (!tag.contains("DataVersion", Tag.TAG_INT) || tag.getInt("DataVersion") != 1))
                throw new IllegalArgumentException("Unsupported dialogue tree save version");
            if (!tag.contains("Sessions", Tag.TAG_LIST) || tag.getList("Sessions", Tag.TAG_COMPOUND).size() != ((ListTag) tag.get("Sessions")).size())
                throw new IllegalArgumentException("Invalid saved dialogue tree sessions");
            var originals = new LinkedHashMap<DialogueTreeService.SessionKey, CompoundTag>();
            for (var raw : tag.getList("Sessions", Tag.TAG_COMPOUND)) {
                var value = (CompoundTag) raw;
                var key = value.hasUUID("Player") && value.hasUUID("Villager")
                        ? new DialogueTreeService.SessionKey(value.getUUID("Player"), value.getUUID("Villager")) : null;
                try {
                    if (key != null && (originals.containsKey(key) || data.issues.containsKey(key))) {
                        var prior = originals.remove(key);
                        if (prior != null) data.quarantined.add(prior.copy());
                        data.sessions.remove(key);
                        throw new IllegalArgumentException("Duplicate saved dialogue tree session");
                    }
                    if (key == null || !value.hasUUID("Id")
                            || ResourceLocation.tryParse(value.getString("Tree")) == null || value.getString("Node").isBlank()
                            || !value.contains("Touched", Tag.TAG_LONG) || value.getLong("Touched") < 0
                            || !value.contains("Replacements", Tag.TAG_COMPOUND)
                            || value.contains("ResponseDeadline") && (!value.contains("ResponseDeadline", Tag.TAG_LONG) || value.getLong("ResponseDeadline") < -1)
                            || value.contains("TimerResolved") && !value.contains("TimerResolved", Tag.TAG_BYTE))
                        throw new IllegalArgumentException("Invalid saved dialogue tree session");
                    var replacements = new LinkedHashMap<String, String>();
                    var saved = value.getCompound("Replacements");
                    for (String name : saved.getAllKeys()) {
                        if (!saved.contains(name, Tag.TAG_STRING)) throw new IllegalArgumentException("Invalid saved dialogue replacement");
                        replacements.put(name, saved.getString(name));
                    }
                    data.sessions.put(key, new DialogueTreeService.Session(value.getUUID("Id"), ResourceLocation.parse(value.getString("Tree")),
                            value.getString("Node"), value.getLong("Touched"), replacements,
                            value.contains("ResponseDeadline", Tag.TAG_LONG) ? value.getLong("ResponseDeadline") : -1,
                            value.getBoolean("TimerResolved")));
                    originals.put(key, value);
                } catch (IllegalArgumentException exception) {
                    data.quarantined.add(value.copy());
                    if (key != null) data.issues.put(key, exception.getMessage() + ". Restore this conversation from a known-good backup.");
                }
            }
        } catch (IllegalArgumentException exception) {
            data.sessions.clear();
            data.preserved = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible dialogue tree data or a known-good backup.";
        }
        data.sessions.forEach(data::schedule);
        return data;
    }

    String diagnostic() { return diagnostic; }

    String diagnostic(DialogueTreeService.SessionKey key) {
        return diagnostic.isBlank() ? issues.getOrDefault(key, "") : diagnostic;
    }

    DialogueTreeService.Session get(DialogueTreeService.SessionKey key) { return sessions.get(key); }

    void put(DialogueTreeService.SessionKey key, DialogueTreeService.Session session) {
        if (!diagnostic(key).isBlank()) throw new IllegalStateException(diagnostic(key));
        sessions.put(key, session);
        schedule(key, session);
        setDirty();
    }

    DialogueTreeService.Session remove(DialogueTreeService.SessionKey key) {
        if (!diagnostic(key).isBlank()) return null;
        var removed = sessions.remove(key);
        timedSessions.remove(key);
        if (removed != null) setDirty();
        return removed;
    }

    private void schedule(DialogueTreeService.SessionKey key, DialogueTreeService.Session session) {
        if (session.responseDeadline() >= 0 && !session.timerResolved()) timedSessions.add(key);
        else timedSessions.remove(key);
    }

    java.util.List<DialogueTreeService.SessionKey> nextTimedKeys(int limit) {
        int count = Math.min(Math.max(limit, 0), timedSessions.size());
        if (count == 0) return java.util.List.of();
        var keys = new java.util.ArrayList<DialogueTreeService.SessionKey>(count);
        for (int index = 0; index < count; index++) {
            var key = timedSessions.removeFirst();
            timedSessions.add(key);
            keys.add(key);
        }
        return keys;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (preserved != null) return preserved.copy();
        if (!diagnostic.isBlank()) throw new IllegalStateException(diagnostic);
        tag.putInt("DataVersion", 1);
        var entries = new ListTag();
        sessions.forEach((key, session) -> {
            var value = new CompoundTag();
            value.putUUID("Player", key.playerId());
            value.putUUID("Villager", key.villagerId());
            value.putUUID("Id", session.id());
            value.putString("Tree", session.treeId().toString());
            value.putString("Node", session.nodeId());
            value.putLong("Touched", session.lastTouchedGameTime());
            value.putLong("ResponseDeadline", session.responseDeadline());
            value.putBoolean("TimerResolved", session.timerResolved());
            var replacements = new CompoundTag();
            session.replacements().forEach(replacements::putString);
            value.put("Replacements", replacements);
            entries.add(value);
        });
        quarantined.forEach(raw -> entries.add(raw.copy()));
        tag.put("Sessions", entries);
        return tag;
    }
}
