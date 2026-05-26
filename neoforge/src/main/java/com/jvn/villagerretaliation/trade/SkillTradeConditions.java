package com.jvn.villagerretaliation.trade;

import java.util.Set;

public record SkillTradeConditions(
        Set<SkillTradeConfigFlag> configFlags,
        Set<SkillTradeConfigFlag> disabledConfigFlags) {
    public static final SkillTradeConditions EMPTY = new SkillTradeConditions(Set.of(), Set.of());

    public SkillTradeConditions {
        configFlags = configFlags == null ? Set.of() : Set.copyOf(configFlags);
        disabledConfigFlags = disabledConfigFlags == null ? Set.of() : Set.copyOf(disabledConfigFlags);
    }

    public boolean matches() {
        for (SkillTradeConfigFlag flag : this.configFlags) {
            if (!flag.enabled()) {
                return false;
            }
        }
        for (SkillTradeConfigFlag flag : this.disabledConfigFlags) {
            if (flag.enabled()) {
                return false;
            }
        }
        return true;
    }
}
