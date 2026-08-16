package com.jvn.villagerretaliation.skill;

import java.util.Objects;

/** A normalized, measurable piece of work that can train one skill. */
public record VillagerSkillPractice(
        VillagerSkill skill,
        double units,
        String source,
        long repetitionKey,
        int repetitions) {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    public VillagerSkillPractice(VillagerSkill skill, double units, String source, long repetitionKey) {
        this(skill, units, source, repetitionKey, 1);
    }

    public VillagerSkillPractice {
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(source, "source");
        if (!Double.isFinite(units) || units <= 0.0D) {
            throw new IllegalArgumentException("Practice units must be finite and positive");
        }
        if (source.isBlank()) {
            throw new IllegalArgumentException("Practice source must not be blank");
        }
        if (repetitions <= 0) {
            throw new IllegalArgumentException("Practice repetitions must be positive");
        }
    }

    public long repetitionIdentity() {
        long hash = FNV_OFFSET_BASIS;
        for (int index = 0; index < this.source.length(); index++) {
            char character = this.source.charAt(index);
            hash = fnv(hash, character & 0xFFL);
            hash = fnv(hash, character >>> 8);
        }
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            hash = fnv(hash, (this.repetitionKey >>> shift) & 0xFFL);
        }
        return hash;
    }

    private static long fnv(long hash, long value) {
        return (hash ^ value) * FNV_PRIME;
    }
}
