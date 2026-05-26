package com.jvn.villagerretaliation.trade;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record SkillTradeEnchantments(
        Mode mode,
        List<ResourceLocation> candidates,
        Map<ResourceLocation, Integer> fixedLevels,
        boolean levelBySkill,
        int minLevel,
        int maxLevel) {
    public static final SkillTradeEnchantments NONE = new SkillTradeEnchantments(
            Mode.NONE,
            List.of(),
            Map.of(),
            false,
            1,
            1);

    public SkillTradeEnchantments {
        mode = mode == null ? Mode.NONE : mode;
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        fixedLevels = fixedLevels == null ? Map.of() : Map.copyOf(fixedLevels);
        minLevel = Math.clamp(minLevel, 1, 255);
        maxLevel = Math.clamp(maxLevel, minLevel, 255);
    }

    public boolean enabled() {
        return this.mode != Mode.NONE;
    }

    public enum Mode {
        NONE,
        RANDOM_FROM,
        FIXED
    }
}
