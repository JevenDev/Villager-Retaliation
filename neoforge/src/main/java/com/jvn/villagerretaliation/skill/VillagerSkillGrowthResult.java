package com.jvn.villagerretaliation.skill;

import java.util.List;
import java.util.Optional;

public record VillagerSkillGrowthResult(
        int previousAwardedTradeLevel,
        int currentTradeLevel,
        List<SkillIncrease> increases) {
    public static final VillagerSkillGrowthResult NONE = new VillagerSkillGrowthResult(1, 1, List.of());

    public VillagerSkillGrowthResult {
        increases = increases == null ? List.of() : List.copyOf(increases);
    }

    public boolean changed() {
        return !this.increases.isEmpty();
    }

    public Optional<SkillIncrease> primaryIncrease() {
        return this.increases.stream().filter(SkillIncrease::primary).findFirst();
    }

    public record SkillIncrease(VillagerSkill skill, int amount, int newValue, boolean primary) {
    }
}
