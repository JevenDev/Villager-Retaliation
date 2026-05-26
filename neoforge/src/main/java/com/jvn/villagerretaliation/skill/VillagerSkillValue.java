package com.jvn.villagerretaliation.skill;

public record VillagerSkillValue(VillagerSkill skill, int value) {
    public VillagerSkillValue {
        value = VillagerSkillSet.clamp(value);
    }

    public VillagerSkillRank rank() {
        return VillagerSkillRank.fromValue(this.value);
    }
}
