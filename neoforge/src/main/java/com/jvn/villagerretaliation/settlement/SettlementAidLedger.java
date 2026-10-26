package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class SettlementAidLedger {
    static final int REQUEST_LIMIT = 1024, TOWN_LIMIT = 16384, RECEIPT_LIMIT = 1024;
    private UUID stream = UUID.randomUUID();
    private long sequence;
    private long revision;
    long durableRevision;
    final Map<VillageAllegianceId, Town> towns = new LinkedHashMap<>();
    final Map<UUID, SettlementFoodRequest> requests = new LinkedHashMap<>();
    final Map<UUID, UUID> couriers = new LinkedHashMap<>();
    final TreeMap<Long, Receipt> receipts = new TreeMap<>();

    static final class Town {
        SettlementAidPolicy policy;
        long shortageSince = -1, lastObserved, nextRequest, nextAid, policyRevision;
        Town(SettlementAidPolicy policy) { this.policy = policy; }
    }
    record Receipt(long sequence, UUID request, VillageAllegianceId donor, VillageAllegianceId recipient, int standing, long date, long revision) {
        Receipt {
            if (sequence <= 0 || request == null || donor == null || recipient == null || donor.equals(recipient)
                    || standing < 1 || standing > 3 || date < 0 || revision < 0) throw new IllegalArgumentException("Invalid aid receipt");
        }
    }
    public long revision() { return this.revision; }
    public UUID stream() { return this.stream; }
    public List<SettlementFoodRequest> requests() { return List.copyOf(this.requests.values()); }
    SettlementFoodRequest courier(UUID resident) { return this.requests.get(this.couriers.get(resident)); }
    boolean courierAvailable(UUID resident) {
        var previous = courier(resident);
        return previous == null || previous.journey != null && previous.journey.finished()
                && previous.state() == SettlementFoodRequest.State.COMPLETED
                && previous.journey.presence == SettlementCourierJourney.Presence.STORED
                && previous.journey.interference.values().stream().allMatch(incident -> incident.applied);
    }
    void journey(SettlementFoodRequest request, SettlementCourierJourney journey) {
        if (request.journey != null || !courierAvailable(journey.resident())) throw new IllegalStateException("Resident already belongs to a journey");
        var previous = courier(journey.resident());
        if (previous != null) {
            previous.journey.presence = SettlementCourierJourney.Presence.RELEASED;
            changed(previous); previous.journey.revision = previous.revision();
        }
        request.journey = journey; this.couriers.put(journey.resident(), request.id()); changed(request); journey.revision = this.revision;
    }
    public SettlementFoodRequest request(UUID id) { return this.requests.get(id); }
    public SettlementAidPolicy policy(SettlementRecord town) {
        var state = this.towns.get(town.id());
        return state == null ? SettlementAidPolicy.defaults(town) : state.policy;
    }
    public long policyRevision(SettlementRecord town) {
        var state = this.towns.get(town.id());
        return state == null ? 0 : state.policyRevision;
    }
    boolean writable() { return this.revision < Long.MAX_VALUE && this.sequence < Long.MAX_VALUE; }
    void changed() {
        if (this.revision == Long.MAX_VALUE) throw new IllegalStateException("Food aid ledger exhausted");
        this.revision++;
    }
    void changed(SettlementFoodRequest request) { changed(); request.revision = this.revision; }
    Town town(SettlementRecord town) {
        var state = this.towns.get(town.id());
        if (state == null) {
            if (this.towns.size() >= TOWN_LIMIT) throw new IllegalStateException("Food aid village limit reached");
            state = new Town(SettlementAidPolicy.defaults(town)); this.towns.put(town.id(), state); changed();
        }
        return state;
    }
    boolean canRequest() {
        if (!writable()) return false;
        if (this.requests.size() < REQUEST_LIMIT) return true;
        var obsolete = this.requests.values().stream().filter(SettlementFoodRequest::terminal)
                .filter(request -> request.journey == null || request.journey.released() && request.journey.interference.values().stream().allMatch(incident -> incident.applied))
                .filter(request -> request.offer() == null || request.offer().empty())
                .filter(request -> this.receipts.values().stream().noneMatch(receipt -> receipt.request().equals(request.id())))
                .min(java.util.Comparator.comparingLong(SettlementFoodRequest::created)).orElse(null);
        if (obsolete == null) return false;
        this.requests.remove(obsolete.id()); changed();
        return true;
    }
    void receipt(SettlementFoodRequest request, int points, long now) {
        if (points < 1 || points > 3 || request.creditedStanding + points > 3 || this.receipts.size() >= RECEIPT_LIMIT || !writable())
            throw new IllegalStateException("Food aid receipt unavailable");
        this.receipts.put(++this.sequence, new Receipt(this.sequence, request.id(), request.donor(), request.requester(), points, now, this.revision + 1));
        request.creditedStanding += points; changed(request);
    }
    CompoundTag save(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.putInt("Version", 3); tag.putUUID("Stream", this.stream); tag.putLong("Sequence", this.sequence); tag.putLong("Revision", this.revision);
        var towns = new ListTag();
        this.towns.forEach((id, state) -> {
            var entry = new CompoundTag(); entry.putUUID("Village", id.value()); entry.put("Policy", state.policy.save());
            entry.putLong("PolicyRevision", state.policyRevision);
            entry.putLong("ShortageSince", state.shortageSince); entry.putLong("LastObserved", state.lastObserved);
            entry.putLong("NextRequest", state.nextRequest); entry.putLong("NextAid", state.nextAid); towns.add(entry);
        });
        tag.put("Towns", towns);
        var requests = new ListTag(); this.requests.values().forEach(request -> requests.add(request.save(provider))); tag.put("Requests", requests);
        var receipts = new ListTag();
        this.receipts.values().forEach(receipt -> {
            var entry = new CompoundTag(); entry.putLong("Sequence", receipt.sequence()); entry.putUUID("Request", receipt.request());
            entry.putUUID("Donor", receipt.donor().value()); entry.putUUID("Recipient", receipt.recipient().value());
            entry.putInt("Standing", receipt.standing()); entry.putLong("Date", receipt.date()); entry.putLong("Revision", receipt.revision()); receipts.add(entry);
        });
        tag.put("Receipts", receipts);
        return tag;
    }
    static SettlementAidLedger load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.contains("Version", Tag.TAG_INT) || (tag.getInt("Version") < 1 || tag.getInt("Version") > 3) || !tag.hasUUID("Stream")
                || !tag.contains("Sequence", Tag.TAG_LONG) || tag.getLong("Sequence") < 0
                || !tag.contains("Revision", Tag.TAG_LONG) || tag.getLong("Revision") < 0) throw new IllegalArgumentException("Unsupported food aid ledger");
        var result = new SettlementAidLedger();
        result.stream = tag.getUUID("Stream"); result.sequence = tag.getLong("Sequence"); result.revision = tag.getLong("Revision");
        result.durableRevision = result.revision;
        for (var raw : list(tag, "Towns", TOWN_LIMIT)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Village") || !entry.contains("Policy", Tag.TAG_COMPOUND) || !entry.contains("ShortageSince", Tag.TAG_LONG)
                    || !entry.contains("LastObserved", Tag.TAG_LONG) || !entry.contains("NextRequest", Tag.TAG_LONG)
                    || !entry.contains("NextAid", Tag.TAG_LONG)) throw new IllegalArgumentException("Missing aid village state");
            var town = new Town(SettlementAidPolicy.load(entry.getCompound("Policy")));
            if (!entry.contains("PolicyRevision", Tag.TAG_LONG) || entry.getLong("PolicyRevision") < 0 || entry.getLong("PolicyRevision") > result.revision)
                throw new IllegalArgumentException("Invalid aid policy revision");
            town.policyRevision = entry.getLong("PolicyRevision");
            town.shortageSince = entry.getLong("ShortageSince"); town.lastObserved = entry.getLong("LastObserved");
            town.nextRequest = entry.getLong("NextRequest"); town.nextAid = entry.getLong("NextAid");
            if (town.shortageSince < -1 || town.lastObserved < 0 || town.shortageSince > town.lastObserved || town.nextRequest < 0 || town.nextAid < 0
                    || result.towns.put(new VillageAllegianceId(entry.getUUID("Village")), town) != null) throw new IllegalArgumentException("Invalid aid village state");
        }
        for (var raw : list(tag, "Requests", REQUEST_LIMIT)) {
            var request = SettlementFoodRequest.load((CompoundTag) raw, provider);
            if (request.journey != null && (!request.journey.released() || request.journey.interference.values().stream().anyMatch(incident -> !incident.applied))
                    && result.couriers.put(request.journey.resident(), request.id()) != null) throw new IllegalArgumentException("Resident belongs to multiple courier journeys");
            if (request.revision() > result.revision || result.requests.put(request.id(), request) != null) throw new IllegalArgumentException("Invalid aid request identity or revision");
        }
        for (var raw : list(tag, "Receipts", RECEIPT_LIMIT)) {
            var entry = (CompoundTag) raw;
            if (!entry.contains("Sequence", Tag.TAG_LONG) || !entry.hasUUID("Request") || !entry.hasUUID("Donor") || !entry.hasUUID("Recipient")
                    || !entry.contains("Standing", Tag.TAG_INT) || !entry.contains("Date", Tag.TAG_LONG)) throw new IllegalArgumentException("Missing aid receipt fields");
            var receipt = new Receipt(entry.getLong("Sequence"), entry.getUUID("Request"), new VillageAllegianceId(entry.getUUID("Donor")),
                    new VillageAllegianceId(entry.getUUID("Recipient")), entry.getInt("Standing"), entry.getLong("Date"), tag.getInt("Version") < 3 ? result.revision : entry.getLong("Revision"));
            if (tag.getInt("Version") >= 3 && !entry.contains("Revision", Tag.TAG_LONG)) throw new IllegalArgumentException("Missing aid receipt revision");
            if (receipt.revision() > result.revision || receipt.sequence() > result.sequence || result.receipts.put(receipt.sequence(), receipt) != null
                    || !result.requests.containsKey(receipt.request())) throw new IllegalArgumentException("Invalid aid receipt reference");
            var request = result.requests.get(receipt.request());
            if (!request.donor().equals(receipt.donor()) || !request.requester().equals(receipt.recipient()) || receipt.date() < request.created()
                    || result.receipts.values().stream().filter(value -> value.request().equals(request.id())).mapToInt(Receipt::standing).sum() > request.creditedStanding)
                throw new IllegalArgumentException("Aid receipt does not match its delivery");
        }
        return result;
    }
    private static ListTag list(CompoundTag tag, String key, int limit) {
        var list = tag.getList(key, Tag.TAG_COMPOUND);
        if (!list.equals(tag.get(key)) || list.size() > limit) throw new IllegalArgumentException("Invalid aid list: " + key);
        return list;
    }
}
