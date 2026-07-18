package com.jvn.villagerretaliation.skill;

import java.util.Objects;

/** A normalized, measurable piece of work that can train one skill. */
public record VillagerSkillPractice(
        VillagerSkill skill,
        double units,
        String source,
        long repetitionKey) {
    public VillagerSkillPractice {
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(source, "source");
        if (!Double.isFinite(units) || units <= 0.0D) {
            throw new IllegalArgumentException("Practice units must be finite and positive");
        }
        if (source.isBlank()) {
            throw new IllegalArgumentException("Practice source must not be blank");
        }
    }
}
