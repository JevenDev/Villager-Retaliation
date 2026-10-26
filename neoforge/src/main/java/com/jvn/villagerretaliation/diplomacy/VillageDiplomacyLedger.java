package com.jvn.villagerretaliation.diplomacy;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class VillageDiplomacyLedger {
    static final int CONTACT_LIMIT = 1024;
    private static final int OWNER_LIMIT = 16384;
    private static final int LETTER_LIMIT = 4096;
    private static final int STANDING_LIMIT = 65536;
    final Map<UUID, Map<VillageAllegianceId, VillageContact>> players = new LinkedHashMap<>();
    final Map<UUID, Map<VillageAllegianceId, VillageContact>> villages = new LinkedHashMap<>();
    final Map<VillageDiplomacySavedData.Pair, VillageStanding> standings = new LinkedHashMap<>();
    final Map<UUID, VillageLetter> pending = new LinkedHashMap<>();
    final Map<UUID, List<VillageLetter>> reports = new LinkedHashMap<>();
    final Map<UUID, Long> nextLetters = new LinkedHashMap<>();
    private final PriorityQueue<VillageLetter> delivery = new PriorityQueue<>(Comparator.comparingLong(VillageLetter::arrivesAt)
            .thenComparing(VillageLetter::id));

    boolean learn(boolean personal, UUID owner, VillageContact contact) {
        var owners = personal ? players : villages;
        var known = owners.get(owner);
        if (known == null) {
            if (owners.size() >= OWNER_LIMIT) return false;
            known = new LinkedHashMap<>();
        }
        var previous = known.get(contact.village());
        if (previous != null) {
            if (!previous.established() && contact.established()) {
                known.put(contact.village(), previous.establish());
                return true;
            }
            return false;
        }
        if (known.size() >= CONTACT_LIMIT) return false;
        known.put(contact.village(), contact);
        owners.put(owner, known);
        return true;
    }

    boolean send(VillageLetter letter, long now) {
        if (pending.size() >= LETTER_LIMIT || pending.containsKey(letter.id())
                || now < 0 || now > Long.MAX_VALUE - 1200
                || !nextLetters.containsKey(letter.sender().value()) && nextLetters.size() >= OWNER_LIMIT
                || now < nextLetters.getOrDefault(letter.sender().value(), 0L)) return false;
        pending.put(letter.id(), letter);
        delivery.add(letter);
        nextLetters.put(letter.sender().value(), Math.addExact(now, 1200));
        return true;
    }

    VillageLetter due(long now) {
        return !delivery.isEmpty() && delivery.peek().arrivesAt() <= now ? delivery.peek() : null;
    }

    void delivered(VillageLetter letter, boolean receivedSuccessfully) {
        pending.remove(letter.id());
        delivery.remove(letter);
        if (!receivedSuccessfully) return;
        var received = reports.computeIfAbsent(letter.recipient().value(), ignored -> new ArrayList<>());
        received.add(letter);
        if (received.size() > 32) received.removeFirst();
    }

    boolean canAddStanding(VillageDiplomacySavedData.Pair pair) {
        return standings.containsKey(pair) || standings.size() < STANDING_LIMIT;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("Players", saveContacts(players));
        tag.put("Villages", saveContacts(villages));
        var savedStandings = new ListTag();
        standings.forEach((pair, standing) -> {
            var entry = standing.save();
            entry.putUUID("First", pair.first().value());
            entry.putUUID("Second", pair.second().value());
            savedStandings.add(entry);
        });
        tag.put("Standings", savedStandings);
        var letters = new ListTag();
        pending.values().forEach(letter -> letters.add(letter.save()));
        tag.put("Pending", letters);
        var received = new ListTag();
        reports.forEach((owner, history) -> {
            var entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            var items = new ListTag();
            history.forEach(letter -> items.add(letter.save()));
            entry.put("Letters", items);
            received.add(entry);
        });
        tag.put("Reports", received);
        var cooldowns = new ListTag();
        nextLetters.forEach((owner, next) -> {
            var entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putLong("Next", next);
            cooldowns.add(entry);
        });
        tag.put("Cooldowns", cooldowns);
        return tag;
    }

    private static ListTag saveContacts(Map<UUID, Map<VillageAllegianceId, VillageContact>> owners) {
        var list = new ListTag();
        owners.forEach((owner, contacts) -> {
            var entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            var known = new ListTag();
            contacts.values().forEach(contact -> known.add(contact.save()));
            entry.put("Contacts", known);
            list.add(entry);
        });
        return list;
    }

    static VillageDiplomacyLedger load(CompoundTag tag) {
        var ledger = new VillageDiplomacyLedger();
        loadContacts(tag, "Players", ledger.players);
        loadContacts(tag, "Villages", ledger.villages);
        for (var raw : list(tag, "Standings", STANDING_LIMIT)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("First") || !entry.hasUUID("Second")) throw new IllegalArgumentException("Missing standing identities");
            var pair = VillageDiplomacySavedData.Pair.of(new VillageAllegianceId(entry.getUUID("First")), new VillageAllegianceId(entry.getUUID("Second")));
            if (pair.first().equals(pair.second()) || ledger.standings.put(pair, VillageStanding.load(entry)) != null) {
                throw new IllegalArgumentException("Duplicate or self standing");
            }
        }
        for (var raw : list(tag, "Pending", LETTER_LIMIT)) {
            var letter = VillageLetter.load((CompoundTag) raw);
            if (ledger.pending.put(letter.id(), letter) != null) throw new IllegalArgumentException("Duplicate pending letter");
            ledger.delivery.add(letter);
        }
        for (var raw : list(tag, "Reports", OWNER_LIMIT)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Owner")) throw new IllegalArgumentException("Missing report recipient");
            var history = new ArrayList<VillageLetter>();
            for (var item : list(entry, "Letters", 32)) {
                var letter = VillageLetter.load((CompoundTag) item);
                if (!letter.recipient().value().equals(entry.getUUID("Owner"))) throw new IllegalArgumentException("Wrong report recipient");
                history.add(letter);
            }
            if (ledger.reports.put(entry.getUUID("Owner"), history) != null) throw new IllegalArgumentException("Duplicate report recipient");
        }
        for (var raw : list(tag, "Cooldowns", OWNER_LIMIT)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Owner") || !entry.contains("Next", Tag.TAG_LONG) || entry.getLong("Next") < 0
                    || ledger.nextLetters.put(entry.getUUID("Owner"), entry.getLong("Next")) != null) {
                throw new IllegalArgumentException("Invalid letter cooldown");
            }
        }
        return ledger;
    }

    private static void loadContacts(CompoundTag tag, String key, Map<UUID, Map<VillageAllegianceId, VillageContact>> owners) {
        for (var raw : list(tag, key, OWNER_LIMIT)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Owner")) throw new IllegalArgumentException("Missing contact owner");
            var contacts = new LinkedHashMap<VillageAllegianceId, VillageContact>();
            for (var item : list(entry, "Contacts", OWNER_LIMIT * CONTACT_LIMIT)) {
                var contact = VillageContact.load((CompoundTag) item);
                if (contacts.put(contact.village(), contact) != null) throw new IllegalArgumentException("Duplicate contact");
            }
            if (owners.put(entry.getUUID("Owner"), contacts) != null) throw new IllegalArgumentException("Duplicate contact owner");
        }
    }

    static ListTag list(CompoundTag tag, String key, int maximum) {
        if (!(tag.get(key) instanceof ListTag list) || list.size() > maximum
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid " + key);
        return list;
    }

    void reconcile(VillageAllegianceRegistrySavedData registry) {
        var rebuilt = new LinkedHashMap<UUID, Map<VillageAllegianceId, VillageContact>>();
        villages.forEach((owner, contacts) -> {
            var canonical = active(registry, new VillageAllegianceId(owner));
            if (canonical != null) contacts.values().forEach(contact -> {
                var target = active(registry, contact.village());
                if (target != null && !target.equals(canonical)) rebuilt.computeIfAbsent(canonical.value(), ignored -> new LinkedHashMap<>())
                        .merge(target, contact.identifiedAs(target), VillageDiplomacyLedger::preferContact);
            });
        });
        villages.clear();
        villages.putAll(rebuilt);
        players.replaceAll((owner, contacts) -> {
            var known = new LinkedHashMap<VillageAllegianceId, VillageContact>();
            contacts.values().forEach(contact -> {
                var target = active(registry, contact.village());
                if (target != null) known.merge(target, contact.identifiedAs(target), VillageDiplomacyLedger::preferContact);
            });
            return known;
        });
        var pairs = new LinkedHashMap<VillageDiplomacySavedData.Pair, VillageStanding>();
        standings.forEach((pair, standing) -> {
            var a = active(registry, pair.first());
            var b = active(registry, pair.second());
            if (a != null && b != null && !a.equals(b)) pairs.merge(VillageDiplomacySavedData.Pair.of(a, b), standing,
                    VillageStanding::merge);
        });
        standings.clear();
        standings.putAll(pairs);
        pending.replaceAll((id, letter) -> canonical(registry, letter));
        pending.values().removeIf(java.util.Objects::isNull);
        delivery.clear();
        delivery.addAll(pending.values());
        var received = new LinkedHashMap<UUID, List<VillageLetter>>();
        reports.values().forEach(history -> history.forEach(letter -> {
            var current = canonical(registry, letter);
            if (current != null) received.computeIfAbsent(current.recipient().value(), ignored -> new ArrayList<>()).add(current);
        }));
        received.replaceAll((id, history) -> new ArrayList<>(history.stream().sorted(Comparator.comparingLong(VillageLetter::arrivesAt).reversed()).limit(32).toList().reversed()));
        reports.clear();
        reports.putAll(received);
        var cooldowns = new LinkedHashMap<UUID, Long>();
        nextLetters.forEach((id, time) -> {
            var current = active(registry, new VillageAllegianceId(id));
            if (current != null) cooldowns.merge(current.value(), time, Math::max);
        });
        nextLetters.clear();
        nextLetters.putAll(cooldowns);
    }

    private static VillageLetter canonical(VillageAllegianceRegistrySavedData registry, VillageLetter letter) {
        var sender = active(registry, letter.sender());
        var recipient = active(registry, letter.recipient());
        var subject = active(registry, letter.subject().village());
        return sender == null || recipient == null || subject == null || sender.equals(recipient) || recipient.equals(subject)
                ? null : letter.canonical(sender, recipient, subject);
    }

    private static VillageContact preferContact(VillageContact first, VillageContact second) {
        var selected = first.learnedAt() <= second.learnedAt() ? first : second;
        return first.established() || second.established() ? selected.establish() : selected;
    }

    static VillageAllegianceId active(VillageAllegianceRegistrySavedData registry, VillageAllegianceId id) {
        return registry.canonicalRecord(id).filter(record -> !record.archived()).map(VillageAllegianceRegistrySavedData.AllegianceRecord::id).orElse(null);
    }
}
