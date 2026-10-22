package com.jvn.villagerretaliation.dialogue;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class KnowledgeSavedData extends SavedData {
    private final Map<Key, Entry> entries = new LinkedHashMap<>();
    private CompoundTag damaged;

    public record Key(UUID owner, ResourceLocation topic) { }
    public record Entry(UUID source, String kind, long learnedAt) { }

    public static KnowledgeSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(KnowledgeSavedData::new, KnowledgeSavedData::load, DataFixTypes.LEVEL),
                "villagerretaliation_knowledge");
    }

    public boolean available() { return damaged == null; }

    public Optional<Entry> knowledge(UUID owner, ResourceLocation topic) {
        return available() ? Optional.ofNullable(entries.get(new Key(owner, topic))) : Optional.empty();
    }

    public boolean witness(UUID owner, ResourceLocation topic, long time) {
        return learn(owner, topic, new Entry(owner, "witnessed", time));
    }

    public boolean share(UUID source, UUID recipient, ResourceLocation topic, boolean partial, long time) {
        var known = knowledge(source, topic);
        if (known.isEmpty() || source.equals(recipient)) return false;
        return learn(recipient, topic, new Entry(source,
                partial || known.orElseThrow().kind().equals("partial") ? "partial" : "heard", time));
    }

    private boolean learn(UUID owner, ResourceLocation topic, Entry entry) {
        if (!available() || owner == null || topic == null || entry.source() == null || entry.learnedAt() < 0) return false;
        var key = new Key(owner, topic);
        var previous = entries.get(key);
        if (previous != null && strength(previous.kind()) >= strength(entry.kind())) return false;
        entries.put(key, entry);
        setDirty();
        return true;
    }

    public boolean forget(UUID owner, ResourceLocation topic) {
        if (!available() || entries.remove(new Key(owner, topic)) == null) return false;
        setDirty();
        return true;
    }

    private static int strength(String kind) {
        return switch (kind) { case "partial" -> 1; case "heard" -> 2; case "witnessed" -> 3; default -> 0; };
    }

    public static KnowledgeSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new KnowledgeSavedData();
        try {
            if (tag.getInt("Version") != 1 || !tag.contains("Entries", Tag.TAG_LIST))
                throw new IllegalArgumentException("unsupported knowledge save format");
            var list = tag.getList("Entries", Tag.TAG_COMPOUND);
            if (!((ListTag) tag.get("Entries")).isEmpty() && ((ListTag) tag.get("Entries")).getElementType() != Tag.TAG_COMPOUND)
                throw new IllegalArgumentException("invalid knowledge entry list");
            for (var raw : list) {
                var entry = (CompoundTag) raw;
                if (!entry.hasUUID("Owner") || !entry.hasUUID("Source") || !entry.contains("Topic", Tag.TAG_STRING)
                        || !entry.contains("Kind", Tag.TAG_STRING) || !entry.contains("LearnedAt", Tag.TAG_LONG))
                    throw new IllegalArgumentException("incomplete knowledge entry");
                var topic = ResourceLocation.tryParse(entry.getString("Topic"));
                if (topic == null || strength(entry.getString("Kind")) == 0 || entry.getLong("LearnedAt") < 0)
                    throw new IllegalArgumentException("invalid knowledge entry");
                var key = new Key(entry.getUUID("Owner"), topic);
                if (data.entries.putIfAbsent(key, new Entry(entry.getUUID("Source"), entry.getString("Kind"), entry.getLong("LearnedAt"))) != null)
                    throw new IllegalArgumentException("duplicate knowledge entry");
            }
        } catch (IllegalArgumentException exception) {
            data.entries.clear();
            data.damaged = tag.copy();
            LogUtils.getLogger().error("Knowledge data is unavailable and was preserved for repair: {}", exception.getMessage());
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (!available()) return damaged.copy();
        tag.putInt("Version", 1);
        var list = new ListTag();
        entries.forEach((key, entry) -> {
            var value = new CompoundTag();
            value.putUUID("Owner", key.owner());
            value.putString("Topic", key.topic().toString());
            value.putUUID("Source", entry.source());
            value.putString("Kind", entry.kind());
            value.putLong("LearnedAt", entry.learnedAt());
            list.add(value);
        });
        tag.put("Entries", list);
        return tag;
    }
}
