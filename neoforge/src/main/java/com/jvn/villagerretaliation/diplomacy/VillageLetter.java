package com.jvn.villagerretaliation.diplomacy;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record VillageLetter(UUID id, VillageAllegianceId sender, VillageAllegianceId recipient,
                            Kind kind, VillageContact subject, int standing, long sentAt, long arrivesAt) {
    public VillageLetter {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sender);
        Objects.requireNonNull(recipient);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(subject);
        if (sender.equals(recipient) || recipient.equals(subject.village()) || Math.abs((long) standing) > 100
                || sentAt < 0 || arrivesAt < sentAt) throw new IllegalArgumentException("Invalid diplomatic letter");
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Sender", sender.value());
        tag.putUUID("Recipient", recipient.value());
        tag.putString("Kind", kind.name());
        tag.put("Subject", subject.save());
        tag.putInt("Standing", standing);
        tag.putLong("SentAt", sentAt);
        tag.putLong("ArrivesAt", arrivesAt);
        return tag;
    }

    static VillageLetter load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("Sender") || !tag.hasUUID("Recipient")
                || !tag.contains("Kind", Tag.TAG_STRING) || !tag.contains("Subject", Tag.TAG_COMPOUND)
                || !tag.contains("Standing", Tag.TAG_INT) || !tag.contains("SentAt", Tag.TAG_LONG)
                || !tag.contains("ArrivesAt", Tag.TAG_LONG)) throw new IllegalArgumentException("Malformed diplomatic letter");
        return new VillageLetter(tag.getUUID("Id"), new VillageAllegianceId(tag.getUUID("Sender")),
                new VillageAllegianceId(tag.getUUID("Recipient")), Kind.valueOf(tag.getString("Kind")),
                VillageContact.load(tag.getCompound("Subject")), tag.getInt("Standing"), tag.getLong("SentAt"), tag.getLong("ArrivesAt"));
    }

    VillageLetter canonical(VillageAllegianceId from, VillageAllegianceId to, VillageAllegianceId target) {
        return new VillageLetter(id, from, to, kind, subject.identifiedAs(target), standing, sentAt, arrivesAt);
    }

    public enum Kind { INTRODUCTION, STANDING_REPORT }
}
