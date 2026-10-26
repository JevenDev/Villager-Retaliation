package com.jvn.villagerretaliation.diplomacy;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record VillageContact(VillageAllegianceId village, ResourceLocation dimension, BlockPos position,
                             Origin origin, long learnedAt, boolean established) {
    public VillageContact {
        Objects.requireNonNull(village);
        Objects.requireNonNull(dimension);
        position = Objects.requireNonNull(position).immutable();
        Objects.requireNonNull(origin);
        if (learnedAt < 0) throw new IllegalArgumentException("Negative village contact date");
    }

    VillageContact identifiedAs(VillageAllegianceId id) {
        return new VillageContact(id, dimension, position, origin, learnedAt, established);
    }

    VillageContact establish() {
        return established ? this : new VillageContact(village, dimension, position, origin, learnedAt, true);
    }

    VillageContact introduced(long now) {
        return new VillageContact(village, dimension, position, Origin.INTRODUCTION, now, false);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Village", village.value());
        tag.putString("Dimension", dimension.toString());
        tag.putLong("Position", position.asLong());
        tag.putString("Origin", origin.name());
        tag.putLong("LearnedAt", learnedAt);
        tag.putBoolean("Established", established);
        return tag;
    }

    static VillageContact load(CompoundTag tag) {
        if (!tag.hasUUID("Village") || !tag.contains("Dimension", Tag.TAG_STRING)
                || !tag.contains("Position", Tag.TAG_LONG) || !tag.contains("Origin", Tag.TAG_STRING)
                || !tag.contains("LearnedAt", Tag.TAG_LONG) || !tag.contains("Established", Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Malformed village contact");
        }
        return new VillageContact(new VillageAllegianceId(tag.getUUID("Village")),
                ResourceLocation.parse(tag.getString("Dimension")), BlockPos.of(tag.getLong("Position")),
                Origin.valueOf(tag.getString("Origin")), tag.getLong("LearnedAt"), tag.getBoolean("Established"));
    }

    public enum Origin { VISIT, INTRODUCTION, CORRESPONDENCE, NEIGHBOR }
}
