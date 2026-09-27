package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

record SettlementCourtship(UUID first, UUID second, BlockPos firstStart, BlockPos secondStart,
                           BlockPos bed, int courtshipTicks, int progress, long checkedAt) {
    SettlementCourtship {
        if (first == null || second == null || first.equals(second) || firstStart == null || secondStart == null || bed == null
                || courtshipTicks < 275 || courtshipTicks > 324 || progress < 0 || progress > 24_000 || checkedAt < 0) {
            throw new IllegalArgumentException("Courtship requires distinct parents, observed locations and valid timing");
        }
        firstStart = firstStart.immutable();
        secondStart = secondStart.immutable();
        bed = bed.immutable();
    }

    boolean involves(UUID resident) {
        return this.first.equals(resident) || this.second.equals(resident);
    }

    SettlementCourtship advance(long now) {
        return new SettlementCourtship(this.first, this.second, this.firstStart, this.secondStart, this.bed, this.courtshipTicks,
                Math.min(24_000, this.progress + SettlementSimulationClock.elapsed(this.checkedAt, now)), Math.max(0L, now));
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("First", this.first);
        tag.putUUID("Second", this.second);
        tag.putLong("FirstStart", this.firstStart.asLong());
        tag.putLong("SecondStart", this.secondStart.asLong());
        tag.putLong("Bed", this.bed.asLong());
        tag.putInt("CourtshipTicks", this.courtshipTicks);
        tag.putInt("Progress", this.progress);
        tag.putLong("CheckedAt", this.checkedAt);
        return tag;
    }

    static SettlementCourtship load(CompoundTag tag) {
        if (!tag.hasUUID("First") || !tag.hasUUID("Second") || !tag.contains("FirstStart", Tag.TAG_LONG)
                || !tag.contains("SecondStart", Tag.TAG_LONG) || !tag.contains("Bed", Tag.TAG_LONG)
                || !tag.contains("CourtshipTicks", Tag.TAG_INT) || !tag.contains("Progress", Tag.TAG_INT)
                || !tag.contains("CheckedAt", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Courtship is missing its parents, locations or timing");
        }
        return new SettlementCourtship(tag.getUUID("First"), tag.getUUID("Second"), BlockPos.of(tag.getLong("FirstStart")),
                BlockPos.of(tag.getLong("SecondStart")), BlockPos.of(tag.getLong("Bed")),
                tag.getInt("CourtshipTicks"), tag.getInt("Progress"), tag.getLong("CheckedAt"));
    }
}
