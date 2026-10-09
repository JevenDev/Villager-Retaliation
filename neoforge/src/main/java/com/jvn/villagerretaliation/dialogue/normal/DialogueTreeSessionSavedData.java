package com.jvn.villagerretaliation.dialogue.normal;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class DialogueTreeSessionSavedData extends SavedData {
    private final Map<DialogueTreeService.SessionKey, DialogueTreeService.Session> sessions = new LinkedHashMap<>();

    public static DialogueTreeSessionSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(
                DialogueTreeSessionSavedData::new, (tag, provider) -> load(tag)), "villagerretaliation_dialogue_trees");
    }

    static DialogueTreeSessionSavedData load(CompoundTag tag) {
        var data = new DialogueTreeSessionSavedData();
        for (var raw : tag.getList("Sessions", Tag.TAG_COMPOUND)) {
            var value = (CompoundTag) raw;
            if (!value.hasUUID("Player") || !value.hasUUID("Villager") || !value.hasUUID("Id")
                    || ResourceLocation.tryParse(value.getString("Tree")) == null || value.getString("Node").isBlank())
                throw new IllegalArgumentException("Invalid saved dialogue tree session");
            var key = new DialogueTreeService.SessionKey(value.getUUID("Player"), value.getUUID("Villager"));
            var replacements = new LinkedHashMap<String, String>();
            var saved = value.getCompound("Replacements");
            for (String name : saved.getAllKeys()) replacements.put(name, saved.getString(name));
            var session = new DialogueTreeService.Session(value.getUUID("Id"), ResourceLocation.parse(value.getString("Tree")),
                    value.getString("Node"), value.getLong("Touched"), replacements);
            if (data.sessions.putIfAbsent(key, session) != null) throw new IllegalArgumentException("Duplicate saved dialogue tree session");
        }
        return data;
    }

    DialogueTreeService.Session get(DialogueTreeService.SessionKey key) { return sessions.get(key); }

    void put(DialogueTreeService.SessionKey key, DialogueTreeService.Session session) {
        sessions.put(key, session);
        setDirty();
    }

    DialogueTreeService.Session remove(DialogueTreeService.SessionKey key) {
        var removed = sessions.remove(key);
        if (removed != null) setDirty();
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
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
