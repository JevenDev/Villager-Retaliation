package com.jvn.villagerretaliation.notification;

import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.network.VillagerWorldTextIndicatorKind;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.Set;
import net.minecraft.world.entity.npc.VillagerProfession;

public record VillagerNotificationDefinition(
        String id,
        String trigger,
        String text,
        int textColor,
        int chatColor,
        VillagerReputationNoticeKind noticeKind,
        VillagerWorldTextIndicatorKind worldTextKind,
        boolean showForAdults,
        boolean showForBabies,
        Set<VillagerProfession> professions,
        Set<VillagerReputationLevel> reputationLevels,
        Integer minReputation,
        Integer maxReputation,
        int weight,
        double chance) {
    public boolean matches(VillagerNotificationContext context, String trigger) {
        if (!this.trigger.equals(trigger)) {
            return false;
        }
        if (context.villager().isBaby()) {
            if (!this.showForBabies) {
                return false;
            }
        } else if (!this.showForAdults) {
            return false;
        }
        if (!this.professions.isEmpty() && !this.professions.contains(context.profession())) {
            return false;
        }
        if (!this.reputationLevels.isEmpty() && !this.reputationLevels.contains(context.reputationLevel())) {
            return false;
        }
        if (this.minReputation != null && context.reputation() < this.minReputation) {
            return false;
        }
        return this.maxReputation == null || context.reputation() <= this.maxReputation;
    }
}
