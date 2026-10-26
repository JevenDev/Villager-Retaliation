package com.jvn.villagerretaliation.diplomacy;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class VillageDiplomacySavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_village_diplomacy";
    private static final int FORMAT_VERSION = 2;
    private VillageDiplomacyLedger ledger = new VillageDiplomacyLedger();
    private final Map<Pair, Entry> relations = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();
    private ListTag rejectedEntries = new ListTag();
    private CompoundTag preservedRoot;
    private long revision;

    public static VillageDiplomacySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageDiplomacySavedData::new, VillageDiplomacySavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static VillageDiplomacySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new VillageDiplomacySavedData();
        if (!tag.contains("FormatVersion", Tag.TAG_INT) || tag.getInt("FormatVersion") < 1
                || tag.getInt("FormatVersion") > FORMAT_VERSION
                || !tag.contains("Relations", Tag.TAG_LIST)
                || !tag.contains("Revision", Tag.TAG_LONG) || tag.getLong("Revision") < 0
                || !tag.getList("Relations", Tag.TAG_COMPOUND).equals(tag.get("Relations"))) {
            data.preservedRoot = tag.copy();
            data.diagnostic("Unsupported or malformed diplomacy root preserved read-only");
            return data;
        }
        data.revision = tag.getLong("Revision");
        if (tag.getInt("FormatVersion") >= 2) {
            try {
                if (!tag.contains("Ledger", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Missing diplomacy ledger");
                data.ledger = VillageDiplomacyLedger.load(tag.getCompound("Ledger"));
                for (var standing : data.ledger.standings.values()) {
                    if (standing.revision() > data.revision) throw new IllegalArgumentException("Standing revision exceeds root");
                }
            } catch (IllegalArgumentException failure) {
                data.ledger = new VillageDiplomacyLedger();
                data.preservedRoot = tag.copy();
                data.diagnostic("Malformed diplomacy ledger preserved read-only: " + failure.getMessage());
                return data;
            }
        } else data.setDirty();
        data.rejectedEntries = tag.getList("RejectedEntries", Tag.TAG_COMPOUND).copy();
        for (Tag raw : tag.getList("Relations", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                if (!entry.hasUUID("First") || !entry.hasUUID("Second")
                        || !entry.contains("State", Tag.TAG_STRING) || !entry.contains("ResumesAs", Tag.TAG_STRING)
                        || !entry.contains("Revision", Tag.TAG_LONG) || entry.getLong("Revision") <= 0
                        || !entry.contains("ExpiresAt", Tag.TAG_LONG)) {
                    throw new IllegalArgumentException("missing or invalid fields");
                }
                Pair pair = Pair.of(new VillageAllegianceId(entry.getUUID("First")),
                        new VillageAllegianceId(entry.getUUID("Second")));
                VillageRelation state = VillageRelation.valueOf(entry.getString("State"));
                VillageRelation resumes = VillageRelation.valueOf(entry.getString("ResumesAs"));
                long expires = entry.getLong("ExpiresAt");
                if (pair.first().equals(pair.second()) || expires < 0 || resumes == VillageRelation.TRUCE
                        || state != VillageRelation.TRUCE && (expires != 0 || resumes != state)) {
                    throw new IllegalArgumentException("invalid pair or truce");
                }
                Entry value = new Entry(state, expires, resumes, entry.getLong("Revision"));
                data.relations.merge(pair, value, VillageDiplomacySavedData::latest);
                data.revision = Math.max(data.revision, value.revision());
            } catch (IllegalArgumentException failure) {
                data.rejectedEntries.add(entry.copy());
                data.diagnostic("Rejected diplomacy entry: " + failure.getMessage());
                data.setDirty();
            }
        }
        if (!data.rejectedEntries.isEmpty()) {
            data.diagnostic("Preserved " + data.rejectedEntries.size() + " rejected diplomacy entries");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (this.preservedRoot != null) {
            return this.preservedRoot.copy();
        }
        tag.putInt("FormatVersion", FORMAT_VERSION);
        tag.putLong("Revision", this.revision);
        tag.put("Ledger", this.ledger.save());
        var entries = new ListTag();
        this.relations.forEach((pair, value) -> {
            var entry = new CompoundTag();
            entry.putUUID("First", pair.first().value());
            entry.putUUID("Second", pair.second().value());
            entry.putString("State", value.state().name());
            entry.putString("ResumesAs", value.resumesAs().name());
            entry.putLong("ExpiresAt", value.expiresAt());
            entry.putLong("Revision", value.revision());
            entries.add(entry);
        });
        tag.put("Relations", entries);
        if (!this.rejectedEntries.isEmpty()) {
            tag.put("RejectedEntries", this.rejectedEntries.copy());
        }
        return tag;
    }

    public boolean readOnly() {
        return this.preservedRoot != null || this.revision == Long.MAX_VALUE;
    }

    public long revision() { return this.revision; }

    public Map<VillageAllegianceId, VillageContact> personalContacts(java.util.UUID player) {
        return Map.copyOf(this.ledger.players.getOrDefault(player, Map.of()));
    }

    public boolean personallyKnows(java.util.UUID player, VillageAllegianceId village) {
        return this.ledger.players.getOrDefault(player, Map.of()).containsKey(village);
    }

    public VillageContact contact(VillageAllegianceId village, VillageAllegianceId target) {
        return this.ledger.villages.getOrDefault(village.value(), Map.of()).get(target);
    }

    public Map<VillageAllegianceId, VillageContact> contacts(VillageAllegianceId village) {
        return Map.copyOf(this.ledger.villages.getOrDefault(village.value(), Map.of()));
    }

    boolean learn(java.util.UUID player, VillageContact contact) {
        if (readOnly() || !this.ledger.learn(true, player, contact)) return false;
        changed();
        return true;
    }

    boolean canLearn(VillageAllegianceId village, VillageAllegianceId... targets) {
        var contacts = this.ledger.villages.get(village.value());
        if (readOnly() || contacts == null && this.ledger.villages.size() >= 16384) return false;
        long additions = java.util.Arrays.stream(targets).distinct().filter(target -> !target.equals(village)
                && (contacts == null || !contacts.containsKey(target))).count();
        return additions == 0 || (contacts == null ? 0 : contacts.size()) + additions <= VillageDiplomacyLedger.CONTACT_LIMIT;
    }

    boolean learn(VillageAllegianceId village, VillageContact contact) {
        if (readOnly() || village.equals(contact.village()) || !this.ledger.learn(false, village.value(), contact)) return false;
        changed();
        return true;
    }

    public int standing(VillageAllegianceId first, VillageAllegianceId second) {
        var standing = this.ledger.standings.get(Pair.of(first, second));
        return standing == null ? 0 : standing.score();
    }

    public VillageStanding.Band band(VillageAllegianceId first, VillageAllegianceId second) {
        var standing = this.ledger.standings.get(Pair.of(first, second));
        return standing == null ? VillageStanding.Band.NEUTRAL : standing.band();
    }

    public List<VillageStanding.Event> history(VillageAllegianceId first, VillageAllegianceId second) {
        var standing = this.ledger.standings.get(Pair.of(first, second));
        return standing == null ? List.of() : standing.history();
    }

    boolean adjust(Pair pair, java.util.UUID source, long sequence, int delta, String reason, long now, VillageDiplomacyRules.Rules rules) {
        if (readOnly() || pair.first().equals(pair.second()) || !this.ledger.canAddStanding(pair)) return false;
        var standing = this.ledger.standings.getOrDefault(pair, new VillageStanding());
        if (!standing.apply(source, sequence, delta, reason, now, this.revision + 1, rules)) return false;
        this.ledger.standings.put(pair, standing);
        changed();
        return true;
    }

    boolean send(VillageLetter letter, long now) {
        if (readOnly() || !this.ledger.send(letter, now)) return false;
        changed();
        return true;
    }

    VillageLetter due(long now) { return this.ledger.due(now); }

    void delivered(VillageLetter letter, boolean received) {
        if (readOnly()) return;
        this.ledger.delivered(letter, received);
        changed();
    }

    public List<VillageLetter> reports(VillageAllegianceId village) {
        return List.copyOf(this.ledger.reports.getOrDefault(village.value(), List.of()));
    }

    public List<VillageLetter> outgoing(VillageAllegianceId village) {
        return this.ledger.pending.values().stream().filter(letter -> letter.sender().equals(village)).toList();
    }

    private void changed() {
        this.revision++;
        setDirty();
    }

    public List<String> diagnostics() {
        return List.copyOf(this.diagnostics);
    }

    VillageRelationSnapshot query(Pair pair, long now) {
        Entry entry = this.relations.get(pair);
        return entry == null ? VillageRelationSnapshot.NEUTRAL : entry.snapshot(now);
    }

    boolean set(Pair pair, VillageRelation state, long duration, long now) {
        if (readOnly()) {
            return false;
        }
        VillageRelationSnapshot previous = query(pair, now);
        VillageRelation resumes = state == VillageRelation.TRUCE
                ? previous.resumesAs() : state;
        long expires = state == VillageRelation.TRUCE && duration > 0 ? Math.addExact(now, duration) : 0;
        this.relations.put(pair, new Entry(state, expires, resumes, ++this.revision));
        setDirty();
        return true;
    }

    public void reconcile(VillageAllegianceRegistrySavedData registry) {
        if (readOnly()) return;
        var previousLedger = this.ledger.save();
        this.ledger.reconcile(registry);
        if (!previousLedger.equals(this.ledger.save()) && !readOnly()) changed();
        Map<Pair, Entry> canonical = new LinkedHashMap<>();
        this.relations.forEach((pair, value) -> {
            var first = registry.canonicalRecord(pair.first()).filter(record -> !record.archived());
            var second = registry.canonicalRecord(pair.second()).filter(record -> !record.archived());
            if (first.isPresent() && second.isPresent() && !first.get().id().equals(second.get().id())) {
                canonical.merge(Pair.of(first.get().id(), second.get().id()), value, VillageDiplomacySavedData::latest);
            }
        });
        canonical.keySet().forEach(pair -> {
            var a = registry.canonicalRecord(pair.first()).orElseThrow();
            var b = registry.canonicalRecord(pair.second()).orElseThrow();
            if (a.originDimension() != null && b.originDimension() != null) {
                learn(a.id(), VillageDiplomacyService.contact(b, VillageContact.Origin.CORRESPONDENCE, 0, true));
                learn(b.id(), VillageDiplomacyService.contact(a, VillageContact.Origin.CORRESPONDENCE, 0, true));
            }
        });
        if (!this.relations.equals(canonical)) {
            this.relations.clear();
            this.relations.putAll(canonical);
            setDirty();
        }
    }

    private static Entry latest(Entry first, Entry second) {
        return first.revision() >= second.revision() ? first : second;
    }

    private void diagnostic(String message) {
        this.diagnostics.add(message);
        LogUtils.getLogger().warn(message);
    }

    record Pair(VillageAllegianceId first, VillageAllegianceId second) {
        static Pair of(VillageAllegianceId first, VillageAllegianceId second) {
            return first.compareTo(second) <= 0 ? new Pair(first, second) : new Pair(second, first);
        }
    }

    private record Entry(VillageRelation state, long expiresAt, VillageRelation resumesAs, long revision) {
        VillageRelationSnapshot snapshot(long now) {
            if (state != VillageRelation.TRUCE) {
                return VillageRelationSnapshot.permanent(state);
            }
            if (expiresAt != 0 && now >= expiresAt) {
                return VillageRelationSnapshot.permanent(resumesAs);
            }
            return new VillageRelationSnapshot(state,
                    expiresAt == 0 ? OptionalLong.empty() : OptionalLong.of(expiresAt), resumesAs);
        }
    }
}
