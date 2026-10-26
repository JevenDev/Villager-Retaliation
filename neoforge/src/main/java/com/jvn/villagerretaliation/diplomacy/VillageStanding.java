package com.jvn.villagerretaliation.diplomacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class VillageStanding {
    private static final int HISTORY_LIMIT = 32;
    private static final int SOURCE_LIMIT = 256;
    private final Map<UUID, Long> receipts = new LinkedHashMap<>();
    private final List<Event> history = new ArrayList<>();
    private int score;
    private Band band = Band.NEUTRAL;
    private long aidWindow;
    private int aidGain;
    private long revision;

    public int score() { return score; }
    public Band band() { return band; }
    public long revision() { return revision; }
    public List<Event> history() { return List.copyOf(history); }

    boolean apply(UUID source, long sequence, int delta, String reason, long now, long revision,
                  VillageDiplomacyRules.Rules rules) {
        if (source == null || sequence <= 0 || sequence <= receipts.getOrDefault(source, 0L)
                || now < 0 || revision <= this.revision || reason == null || reason.isBlank() || reason.length() > 64
                || !receipts.containsKey(source) && receipts.size() >= SOURCE_LIMIT) return false;
        receipts.put(source, sequence);
        int change = Math.clamp(delta, -100, 100);
        if (reason.equals("food_aid") && change > 0) {
            if (now >= aidWindow && now - aidWindow >= rules.aidCooldown()) {
                aidWindow = now;
                aidGain = 0;
            }
            change = Math.min(change, Math.max(0, rules.aidStandingLimit() - aidGain));
            aidGain += change;
        }
        int previous = score;
        score = Math.clamp(score + change, -100, 100);
        updateBand(rules);
        this.revision = revision;
        if (score != previous) {
            history.add(new Event(source, sequence, score - previous, reason, now));
            if (history.size() > HISTORY_LIMIT) history.removeFirst();
        }
        return true;
    }

    private void updateBand(VillageDiplomacyRules.Rules rules) {
        int friendly = rules.friendlyThreshold(), trusted = rules.trustedThreshold(), buffer = rules.standingBuffer();
        int index = band.ordinal();
        int[] boundaries = {-trusted, -friendly, friendly, trusted};
        while (index < 4 && score >= boundaries[index] + (index < 2 ? buffer : 0)) index++;
        while (index > 0 && (index <= 2 ? score <= boundaries[index - 1] : score < boundaries[index - 1] - buffer)) index--;
        band = Band.values()[index];
    }

    static VillageStanding merge(VillageStanding first, VillageStanding second) {
        var retained = first.revision >= second.revision ? first : second;
        var other = retained == first ? second : first;
        other.receipts.forEach((source, sequence) -> retained.receipts.merge(source, sequence, Math::max));
        retained.aidWindow = Math.max(first.aidWindow, second.aidWindow);
        retained.aidGain = Math.min(100, first.aidGain + second.aidGain);
        var events = new ArrayList<>(retained.history);
        events.addAll(other.history);
        retained.history.clear();
        retained.history.addAll(events.stream().distinct().sorted(java.util.Comparator.comparingLong(Event::time).reversed())
                .limit(HISTORY_LIMIT).toList().reversed());
        return retained;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Score", score);
        tag.putString("Band", band.name());
        tag.putLong("Revision", revision);
        tag.putLong("AidWindow", aidWindow);
        tag.putInt("AidGain", aidGain);
        var savedReceipts = new ListTag();
        receipts.forEach((id, sequence) -> {
            var entry = new CompoundTag();
            entry.putUUID("Source", id);
            entry.putLong("Sequence", sequence);
            savedReceipts.add(entry);
        });
        tag.put("Receipts", savedReceipts);
        var events = new ListTag();
        for (var event : history) {
            var entry = new CompoundTag();
            entry.putUUID("Source", event.source());
            entry.putLong("Sequence", event.sequence());
            entry.putInt("Change", event.change());
            entry.putString("Reason", event.reason());
            entry.putLong("Time", event.time());
            events.add(entry);
        }
        tag.put("History", events);
        return tag;
    }

    static VillageStanding load(CompoundTag tag) {
        if (!tag.contains("Score", Tag.TAG_INT) || !tag.contains("Band", Tag.TAG_STRING)
                || !tag.contains("Revision", Tag.TAG_LONG) || !tag.contains("AidWindow", Tag.TAG_LONG)
                || !tag.contains("AidGain", Tag.TAG_INT)) throw new IllegalArgumentException("Malformed village standing");
        var standing = new VillageStanding();
        standing.score = tag.getInt("Score");
        standing.band = Band.valueOf(tag.getString("Band"));
        standing.revision = tag.getLong("Revision");
        standing.aidWindow = tag.getLong("AidWindow");
        standing.aidGain = tag.getInt("AidGain");
        if (Math.abs((long) standing.score) > 100 || standing.revision < 0 || standing.aidWindow < 0
                || standing.aidGain < 0 || standing.aidGain > 100) throw new IllegalArgumentException("Invalid standing values");
        for (var raw : VillageDiplomacyLedger.list(tag, "Receipts", 65536 * SOURCE_LIMIT)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Source") || !entry.contains("Sequence", Tag.TAG_LONG) || entry.getLong("Sequence") <= 0
                    || standing.receipts.put(entry.getUUID("Source"), entry.getLong("Sequence")) != null) {
                throw new IllegalArgumentException("Invalid standing receipt");
            }
        }
        for (var raw : VillageDiplomacyLedger.list(tag, "History", HISTORY_LIMIT)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Source") || !entry.contains("Sequence", Tag.TAG_LONG)
                    || !entry.contains("Change", Tag.TAG_INT) || !entry.contains("Reason", Tag.TAG_STRING)
                    || !entry.contains("Time", Tag.TAG_LONG) || entry.getLong("Time") < 0
                    || entry.getLong("Sequence") <= 0
                    || standing.receipts.getOrDefault(entry.getUUID("Source"), 0L) < entry.getLong("Sequence") || Math.abs((long) entry.getInt("Change")) > 200
                    || entry.getString("Reason").isBlank() || entry.getString("Reason").length() > 64) {
                throw new IllegalArgumentException("Invalid standing history");
            }
            standing.history.add(new Event(entry.getUUID("Source"), entry.getLong("Sequence"),
                    entry.getInt("Change"), entry.getString("Reason"), entry.getLong("Time")));
        }
        return standing;
    }

    public enum Band { DISTRUSTFUL, UNFRIENDLY, NEUTRAL, FRIENDLY, TRUSTED }
    public record Event(UUID source, long sequence, int change, String reason, long time) { }
}
