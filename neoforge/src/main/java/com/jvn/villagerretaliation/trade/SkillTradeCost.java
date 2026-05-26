package com.jvn.villagerretaliation.trade;

import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public record SkillTradeCost(
        Item item,
        int count,
        SkillDiscount skillDiscount) {
    public static final SkillTradeCost DEFAULT = new SkillTradeCost(Items.EMERALD, 1, SkillDiscount.DISABLED);

    public SkillTradeCost {
        item = item == null ? Items.EMERALD : item;
        count = Math.clamp(count, 1, 64);
        skillDiscount = skillDiscount == null ? SkillDiscount.DISABLED : skillDiscount;
    }

    public int countForSkill(int skillValue, int minimumSkillValue) {
        if (!this.skillDiscount.enabled()) {
            return this.count;
        }

        int skillRange = Math.max(1, 100 - minimumSkillValue);
        double skillProgress = Mth.clamp((skillValue - minimumSkillValue) / (double) skillRange, 0.0D, 1.0D);
        double discountPercent = this.skillDiscount.maxPercent() * skillProgress;
        int discount = Mth.floor(this.count * (discountPercent / 100.0D));
        return Math.clamp(this.count - discount, 1, this.count);
    }

    public record SkillDiscount(boolean enabled, int maxPercent) {
        public static final SkillDiscount DISABLED = new SkillDiscount(false, 0);

        public SkillDiscount {
            maxPercent = Math.clamp(maxPercent, 0, 90);
        }
    }
}
