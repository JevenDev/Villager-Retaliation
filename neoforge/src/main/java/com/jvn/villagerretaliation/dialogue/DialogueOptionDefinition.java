package com.jvn.villagerretaliation.dialogue;

import java.util.Set;
import net.minecraft.world.entity.npc.VillagerProfession;

public record DialogueOptionDefinition(
        String id,
        String label,
        DialogueRequestType requestType,
        boolean showForAdults,
        boolean showForBabies,
        Set<VillagerProfession> professions,
        Set<DialogueDisposition> dispositions,
        boolean requiresUnreportedCartographerMapDiscovery,
        boolean requiresUnreportedStoryHintDiscovery,
        boolean requiresUnreportedCombatSurvivalReport,
        boolean requiresUnreportedGearReport,
        boolean requiresUnreportedGiftAdviceResult,
        boolean requiresUnapologizedRememberedHarm,
        boolean requiresUnreportedVillageDefense,
        int order
) {
    public boolean matches(DialogueContext context, DialogueDisposition disposition) {
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
        if (this.requiresUnreportedCartographerMapDiscovery && !context.hasUnreportedCartographerMapDiscovery()) {
            return false;
        }
        if (this.requiresUnreportedStoryHintDiscovery && !context.hasUnreportedStoryHintDiscovery()) {
            return false;
        }
        if (this.requiresUnreportedCombatSurvivalReport && !context.hasUnreportedCombatSurvivalReport()) {
            return false;
        }
        if (this.requiresUnreportedGearReport && !context.hasUnreportedGearReport()) {
            return false;
        }
        if (this.requiresUnreportedGiftAdviceResult && !context.hasUnreportedGiftAdviceResult()) {
            return false;
        }
        if (this.requiresUnapologizedRememberedHarm && !context.hasUnapologizedRememberedHarm()) {
            return false;
        }
        if (this.requiresUnreportedVillageDefense && !context.hasUnreportedVillageDefense()) {
            return false;
        }
        return this.dispositions.isEmpty() || this.dispositions.contains(disposition);
    }

    public static DialogueOptionDefinition simple(String id, String label, DialogueRequestType requestType, int order) {
        return new DialogueOptionDefinition(id, label, requestType, true, true, Set.of(), Set.of(), false, false, false, false, false, false, false, order);
    }
}
