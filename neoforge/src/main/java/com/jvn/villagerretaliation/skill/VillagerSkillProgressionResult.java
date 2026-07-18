package com.jvn.villagerretaliation.skill;

import java.util.List;

public record VillagerSkillProgressionResult(
        List<SkillIncrease> increases,
        double requestedXp,
        double grantedXp,
        boolean profileChanged) {
    public static final VillagerSkillProgressionResult NONE = new VillagerSkillProgressionResult(List.of(), 0.0D, 0.0D, false);

    public VillagerSkillProgressionResult {
        increases = increases == null ? List.of() : List.copyOf(increases);
    }

    public boolean increased() {
        return !this.increases.isEmpty();
    }

    public record SkillIncrease(VillagerSkill skill, int amount, int newValue) {
    }
}
