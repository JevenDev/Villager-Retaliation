package com.jvn.villagerretaliation.settlement;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class SettlementConstructionCrew {
    static final int MAX_MEMBERS = 16;
    private static final long CLAIM_TICKS = 200L;
    private final UUID lead;
    private final int total;
    private final Set<UUID> assistants = new LinkedHashSet<>();
    private final Map<UUID, Claim> claims = new HashMap<>();
    private BitSet completed;
    private int priority = 5;
    private long revision;

    SettlementConstructionCrew(UUID lead, int total, int prefix) {
        if (lead == null || total <= 0 || prefix < 0 || prefix > total) throw new IllegalArgumentException("Invalid construction crew");
        this.lead = lead;
        this.total = total;
        this.completed = new BitSet(total);
        this.completed.set(0, prefix);
    }

    long revision() { return this.revision; }
    int priority() { return this.priority; }
    int placed() { return this.completed.cardinality(); }
    int next() { return Math.min(this.total, this.completed.nextClearBit(0)); }
    List<UUID> assistants() { return List.copyOf(this.assistants); }
    boolean includes(UUID worker) { return this.lead.equals(worker) || this.assistants.contains(worker); }

    boolean configure(long expected, int priority, List<UUID> assistants) {
        if (expected != this.revision || this.revision == Long.MAX_VALUE || priority < 0 || priority > 9
                || assistants == null || assistants.size() >= MAX_MEMBERS || assistants.stream().anyMatch(java.util.Objects::isNull)
                || assistants.contains(this.lead) || new LinkedHashSet<>(assistants).size() != assistants.size()) return false;
        this.priority = priority;
        this.assistants.clear();
        this.assistants.addAll(assistants);
        this.claims.keySet().removeIf(worker -> !includes(worker));
        this.revision++;
        return true;
    }

    boolean detach(UUID worker) {
        release(worker);
        if (!this.assistants.remove(worker)) return false;
        if (this.revision < Long.MAX_VALUE) this.revision++;
        return true;
    }

    boolean refresh(IntPredicate matches) {
        var next = new BitSet(this.total);
        for (int index = 0; index < this.total; index++) if (matches.test(index)) next.set(index);
        if (next.equals(this.completed)) return false;
        this.completed = next;
        return true;
    }

    boolean completed(int index) { return this.completed.get(index); }

    Claim claim(UUID worker, long now) {
        expire(now);
        return this.claims.get(worker);
    }

    boolean acquire(UUID worker, int index, List<BlockPos> positions, long now) {
        if (!includes(worker) || index < -1 || index >= this.total || positions == null || positions.isEmpty()
                || positions.size() > 2 || positions.stream().anyMatch(java.util.Objects::isNull) || Set.copyOf(positions).size() != positions.size()
                || index >= 0 && completed(index) || now < 0L) return false;
        expire(now);
        if (this.claims.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(worker)
                && entry.getValue().positions().stream().anyMatch(positions::contains))) return false;
        this.claims.put(worker, new Claim(index, positions.stream().map(BlockPos::immutable).toList(), now > Long.MAX_VALUE - CLAIM_TICKS ? Long.MAX_VALUE : now + CLAIM_TICKS));
        return true;
    }

    void release(UUID worker) { this.claims.remove(worker); }
    void releaseAll() { this.claims.clear(); }

    private void expire(long now) {
        this.claims.values().removeIf(claim -> claim.until() <= now || claim.until() - CLAIM_TICKS > now);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putLong("Revision", this.revision);
        tag.putInt("Priority", this.priority);
        var members = new ListTag();
        for (var assistant : this.assistants) {
            var member = new CompoundTag();
            member.putUUID("Id", assistant);
            members.add(member);
        }
        tag.put("Assistants", members);
        tag.putLongArray("Completed", this.completed.toLongArray());
        return tag;
    }

    static SettlementConstructionCrew load(CompoundTag tag, UUID lead, int total, int prefix) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || !tag.contains("Revision", Tag.TAG_LONG) || tag.getLong("Revision") < 0L
                || !tag.contains("Priority", Tag.TAG_INT) || tag.getInt("Priority") < 0 || tag.getInt("Priority") > 9
                || !(tag.get("Assistants") instanceof ListTag members) || members.size() >= MAX_MEMBERS
                || !members.isEmpty() && members.getElementType() != Tag.TAG_COMPOUND
                || !tag.contains("Completed", Tag.TAG_LONG_ARRAY) || tag.getLongArray("Completed").length > (total + 63L) / 64L) {
            throw new IllegalArgumentException("Invalid construction crew record");
        }
        var crew = new SettlementConstructionCrew(lead, total, 0);
        crew.revision = tag.getLong("Revision");
        crew.priority = tag.getInt("Priority");
        for (var raw : members) {
            var member = (CompoundTag) raw;
            if (!member.hasUUID("Id") || member.getUUID("Id").equals(lead) || !crew.assistants.add(member.getUUID("Id"))) {
                throw new IllegalArgumentException("Invalid construction assistant");
            }
        }
        crew.completed = BitSet.valueOf(tag.getLongArray("Completed"));
        if (crew.completed.length() > total || crew.next() != prefix) throw new IllegalArgumentException("Invalid construction progress");
        return crew;
    }

    record Claim(int index, List<BlockPos> positions, long until) {}
}
