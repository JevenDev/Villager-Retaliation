package com.jvn.villagerretaliation.progress;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class EventHistorySavedData extends SavedData {
    private final Map<Key, Summary> entries = new HashMap<>();

    public static EventHistorySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(EventHistorySavedData::new, EventHistorySavedData::load), "villagerretaliation_event_history");
    }

    public void record(ServerLevel level, UUID actor, UUID villager, VillageAllegianceId settlement,
                       ResourceLocation event, String target, long count, long amount) {
        long now = level.getServer().overworld().getGameTime();
        add("world", null, actor, event, target, count, amount, now);
        if (villager != null) add("villager", villager, actor, event, target, count, amount, now);
        if (settlement != null) add("settlement", settlement.value(), actor, event, target, count, amount, now);
    }

    public void add(String scope, UUID subject, UUID actor, ResourceLocation event, String target,
                    long count, long amount, long now) {
        if (count <= 0 || amount < 0 || now < 0) throw new IllegalArgumentException("Invalid history quantities or time");
        if (!scope.equals("world") && !scope.equals("villager") && !scope.equals("settlement")
                || !scope.equals("world") && subject == null) throw new IllegalArgumentException("Invalid history scope");
        var key = new Key(scope, subject, actor, Objects.requireNonNull(event), target == null ? "" : target);
        entries.merge(key, new Summary(count, amount, now, now), Summary::merge);
        setDirty();
    }

    public Summary query(ServerLevel level, String scope, UUID subject, UUID actor, ResourceLocation event, String target) {
        Summary result = Summary.EMPTY;
        String bucket = scope.equals("player") ? "world" : scope;
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var canonical = subject == null || !bucket.equals("settlement") ? null
                : registry.canonical(new VillageAllegianceId(subject)).orElse(null);
        for (var entry : entries.entrySet()) {
            var key = entry.getKey();
            if (!key.scope.equals(bucket) || !key.event.equals(event)
                    || actor != null && !actor.equals(key.actor) || target != null && !target.equals(key.target)) continue;
            if (bucket.equals("settlement")) {
                if (canonical == null || !registry.canonical(new VillageAllegianceId(key.subject)).filter(canonical::equals).isPresent()) continue;
            } else if (!Objects.equals(subject, key.subject)) continue;
            result = result.merge(entry.getValue());
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        entries.forEach((key, value) -> {
            var entry = new CompoundTag();
            entry.putString("Scope", key.scope);
            if (key.subject != null) entry.putUUID("Subject", key.subject);
            if (key.actor != null) entry.putUUID("Actor", key.actor);
            entry.putString("Event", key.event.toString());
            entry.putString("Target", key.target);
            entry.putLong("Count", value.count);
            entry.putLong("Amount", value.amount);
            entry.putLong("First", value.first);
            entry.putLong("Last", value.last);
            list.add(entry);
        });
        tag.put("Entries", list);
        return tag;
    }

    public static EventHistorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new EventHistorySavedData();
        for (Tag value : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            var event = ResourceLocation.tryParse(entry.getString("Event"));
            String scope = entry.getString("Scope");
            if (event == null || !scope.equals("world") && !scope.equals("villager") && !scope.equals("settlement")
                    || !scope.equals("world") && !entry.hasUUID("Subject") || entry.getLong("Count") <= 0
                    || entry.getLong("Amount") < 0 || entry.getLong("First") < 0 || entry.getLong("Last") < entry.getLong("First"))
                throw new IllegalArgumentException("Invalid persisted event history entry");
            var key = new Key(scope, entry.hasUUID("Subject") ? entry.getUUID("Subject") : null,
                    entry.hasUUID("Actor") ? entry.getUUID("Actor") : null, event, entry.getString("Target"));
            data.entries.merge(key, new Summary(entry.getLong("Count"), entry.getLong("Amount"),
                    entry.getLong("First"), entry.getLong("Last")), Summary::merge);
        }
        return data;
    }

    private record Key(String scope, UUID subject, UUID actor, ResourceLocation event, String target) { }

    public record Summary(long count, long amount, long first, long last) {
        public static final Summary EMPTY = new Summary(0, 0, -1, -1);
        public Summary merge(Summary other) {
            if (count == 0) return other;
            if (other.count == 0) return this;
            return new Summary(saturatedAdd(count, other.count), saturatedAdd(amount, other.amount),
                    Math.min(first, other.first), Math.max(last, other.last));
        }
        private static long saturatedAdd(long left, long right) { return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right; }
    }
}
