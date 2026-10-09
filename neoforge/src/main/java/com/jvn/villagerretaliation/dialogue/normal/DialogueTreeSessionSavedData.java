package com.jvn.villagerretaliation.dialogue.normal;

import java.nio.file.Files;
import java.util.LinkedHashMap;
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
            for (var raw : tag.getList("Sessions", Tag.TAG_COMPOUND)) {
                var value = (CompoundTag) raw;
                if (!value.hasUUID("Player") || !value.hasUUID("Villager") || !value.hasUUID("Id")
                        || ResourceLocation.tryParse(value.getString("Tree")) == null || value.getString("Node").isBlank()
                        || !value.contains("Touched", Tag.TAG_LONG) || value.getLong("Touched") < 0
                        || !value.contains("Replacements", Tag.TAG_COMPOUND))
                    throw new IllegalArgumentException("Invalid saved dialogue tree session");
                var key = new DialogueTreeService.SessionKey(value.getUUID("Player"), value.getUUID("Villager"));
                var replacements = new LinkedHashMap<String, String>();
                var saved = value.getCompound("Replacements");
                for (String name : saved.getAllKeys()) {
                    if (!saved.contains(name, Tag.TAG_STRING)) throw new IllegalArgumentException("Invalid saved dialogue replacement");
                    replacements.put(name, saved.getString(name));
                }
                var session = new DialogueTreeService.Session(value.getUUID("Id"), ResourceLocation.parse(value.getString("Tree")),
                        value.getString("Node"), value.getLong("Touched"), replacements);
                if (data.sessions.putIfAbsent(key, session) != null) throw new IllegalArgumentException("Duplicate saved dialogue tree session");
            }
        } catch (IllegalArgumentException exception) {
            data.sessions.clear();
            data.preserved = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible dialogue tree data or a known-good backup.";
        }
        return data;
    }

    String diagnostic() { return diagnostic; }

    DialogueTreeService.Session get(DialogueTreeService.SessionKey key) { return sessions.get(key); }

    void put(DialogueTreeService.SessionKey key, DialogueTreeService.Session session) {
        if (!diagnostic.isBlank()) throw new IllegalStateException(diagnostic);
        sessions.put(key, session);
        setDirty();
    }

    DialogueTreeService.Session remove(DialogueTreeService.SessionKey key) {
        if (!diagnostic.isBlank()) return null;
        var removed = sessions.remove(key);
        if (removed != null) setDirty();
        return removed;
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
            var replacements = new CompoundTag();
            session.replacements().forEach(replacements::putString);
            value.put("Replacements", replacements);
            entries.add(value);
        });
        tag.put("Sessions", entries);
        return tag;
    }
}
