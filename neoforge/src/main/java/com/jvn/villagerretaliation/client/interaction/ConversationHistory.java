package com.jvn.villagerretaliation.client.interaction;

import com.google.gson.Gson;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class ConversationHistory {
    public static final int LIMIT = 50;
    private static final Gson GSON = new Gson();
    private final List<Entry> entries = new ArrayList<>();

    public record Entry(String key, UUID speaker, String name, boolean player, CompoundTag portrait,
            List<DialogueTextSegment> segments) {
        public Entry {
            portrait = portrait.copy();
            segments = segments.stream().map(segment -> new DialogueTextSegment(segment.text(), segment.effects())).toList();
        }
    }

    public List<Entry> entries() { return List.copyOf(entries); }

    public boolean add(Entry entry) {
        if (DialogueTextSegment.plainText(entry.segments()).isBlank()
                || entries.stream().anyMatch(previous -> previous.key().equals(entry.key()))) return false;
        entries.add(entry);
        if (entries.size() > LIMIT) entries.removeFirst();
        return true;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        var list = new ListTag();
        for (var entry : entries) {
            var value = new CompoundTag();
            value.putString("Key", entry.key());
            if (entry.speaker() != null) value.putUUID("Speaker", entry.speaker());
            value.putString("Name", entry.name());
            value.putBoolean("Player", entry.player());
            value.put("Portrait", entry.portrait().copy());
            var segments = new ListTag();
            for (var segment : entry.segments()) {
                var text = new CompoundTag();
                text.putString("Text", segment.text());
                text.putString("Effects", GSON.toJson(segment.effects()));
                segments.add(text);
            }
            value.put("Segments", segments);
            list.add(value);
        }
        tag.putInt("Version", 1);
        tag.put("Entries", list);
        return tag;
    }

    public static ConversationHistory load(CompoundTag tag) {
        if (tag.getInt("Version") != 1) throw new IllegalArgumentException("Unsupported conversation history version");
        var history = new ConversationHistory();
        var entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, entries.size() - LIMIT); i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            var segments = new ArrayList<DialogueTextSegment>();
            var values = entry.getList("Segments", Tag.TAG_COMPOUND);
            for (int j = 0; j < values.size(); j++) {
                var value = values.getCompound(j);
                segments.add(new DialogueTextSegment(value.getString("Text"),
                        GSON.fromJson(value.getString("Effects"), DialogueTextEffects.class)));
            }
            history.add(new Entry(entry.getString("Key"), entry.hasUUID("Speaker") ? entry.getUUID("Speaker") : null,
                    entry.getString("Name"), entry.getBoolean("Player"), entry.getCompound("Portrait"), segments));
        }
        return history;
    }
}
