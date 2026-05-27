package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
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
        VillagerEquipmentCondition equipmentCondition,
        VillagerPlayerItemCondition playerItemCondition,
        VillagerReputationCondition reputationCondition,
        DialogueItemPayment itemPayment,
        boolean forceCameraTowardsVillager,
        boolean requiresUnreportedCartographerMapDiscovery,
        boolean requiresUnreportedStoryHintDiscovery,
        boolean requiresUnreportedCombatSurvivalReport,
        boolean requiresUnreportedGearReport,
        boolean requiresUnreportedRecruitmentFollowup,
        boolean requiresUnreportedCuredRecognition,
        boolean requiresRecentVillageEvent,
        boolean requiresUnreportedGiftAdviceResult,
        boolean requiresUnapologizedRememberedHarm,
        boolean requiresUnreportedVillageDefense,
        boolean requiresShareableStory,
        boolean requiresKnownFamily,
        boolean requiresKnownParent,
        boolean requiresKnownSibling,
        boolean requiresKnownSpouse,
        boolean requiresKnownChild,
        boolean requiresKnownGrandparent,
        boolean requiresKnownGrandchild,
        boolean requiresKnownDescendant,
        boolean requiresKnownAuntUncle,
        boolean requiresKnownCousin,
        boolean requiresKnownNieceNephew,
        boolean requiresKnownExtendedFamily,
        boolean requiresKnownDeceasedFamily,
        boolean requiresKnownRelationship,
        boolean requiresKnownCurrentRelationship,
        boolean requiresKnownPastRelationship,
        boolean requiresKnownCrush,
        boolean requiresKnownDatingPartner,
        boolean requiresKnownFiance,
        boolean requiresKnownRomanticSpouse,
        boolean requiresKnownSeparatedPartner,
        boolean requiresKnownWidowedPartner,
        boolean requiresActiveSpecialOrders,
        int order
) {
    private static final String LEFT_BEHIND_OPTION_ID = "recruitment_left_behind";
    private static final String DEFAULT_FOLLOWUP_OPTION_ID = "recruitment_followup";
    private static final String LEFT_BEHIND_SCENARIO = "left_behind";

    public boolean matches(DialogueContext context, DialogueDisposition disposition) {
        if (LEFT_BEHIND_OPTION_ID.equals(this.id) && !context.hasRecruitmentMemoryScenario(LEFT_BEHIND_SCENARIO)) {
            return false;
        }
        if (DEFAULT_FOLLOWUP_OPTION_ID.equals(this.id) && context.hasRecruitmentMemoryScenario(LEFT_BEHIND_SCENARIO)) {
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
        if (!this.equipmentCondition.matches(context.villager())) {
            return false;
        }
        if (!this.playerItemCondition.matches(context.player())) {
            return false;
        }
        if (!this.itemPayment.isEmpty() && !this.itemPayment.removal().canRemove(context.player())) {
            return false;
        }
        if (!this.reputationCondition.matches(context.reputation(), context.reputationLevel())) {
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
        if (this.requiresUnreportedRecruitmentFollowup && !context.hasUnreportedRecruitmentFollowup()) {
            return false;
        }
        if (this.requiresUnreportedCuredRecognition && !context.hasUnreportedCuredRecognition()) {
            return false;
        }
        if (this.requiresRecentVillageEvent && !context.hasRecentVillageEventConcern()) {
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
        if (this.requiresShareableStory && !context.hasShareableStory()) {
            return false;
        }
        if (this.requiresKnownFamily && !context.hasKnownFamily()) {
            return false;
        }
        if (this.requiresKnownParent && !context.hasKnownParent()) {
            return false;
        }
        if (this.requiresKnownSibling && !context.hasKnownSibling()) {
            return false;
        }
        if (this.requiresKnownSpouse && !context.hasKnownSpouse()) {
            return false;
        }
        if (this.requiresKnownChild && !context.hasKnownChild()) {
            return false;
        }
        if (this.requiresKnownGrandparent && !context.hasKnownGrandparent()) {
            return false;
        }
        if (this.requiresKnownGrandchild && !context.hasKnownGrandchild()) {
            return false;
        }
        if (this.requiresKnownDescendant && !context.hasKnownDescendant()) {
            return false;
        }
        if (this.requiresKnownAuntUncle && !context.hasKnownAuntUncle()) {
            return false;
        }
        if (this.requiresKnownCousin && !context.hasKnownCousin()) {
            return false;
        }
        if (this.requiresKnownNieceNephew && !context.hasKnownNieceNephew()) {
            return false;
        }
        if (this.requiresKnownExtendedFamily && !context.hasKnownExtendedFamily()) {
            return false;
        }
        if (this.requiresKnownDeceasedFamily && !context.hasKnownDeceasedFamily()) {
            return false;
        }
        if (this.requiresKnownRelationship && !context.hasKnownRelationship()) {
            return false;
        }
        if (this.requiresKnownCurrentRelationship && !context.hasKnownCurrentRelationship()) {
            return false;
        }
        if (this.requiresKnownPastRelationship && !context.hasKnownPastRelationship()) {
            return false;
        }
        if (this.requiresKnownCrush && !context.hasKnownCrush()) {
            return false;
        }
        if (this.requiresKnownDatingPartner && !context.hasKnownDatingPartner()) {
            return false;
        }
        if (this.requiresKnownFiance && !context.hasKnownFiance()) {
            return false;
        }
        if (this.requiresKnownRomanticSpouse && !context.hasKnownRomanticSpouse()) {
            return false;
        }
        if (this.requiresKnownSeparatedPartner && !context.hasKnownSeparatedPartner()) {
            return false;
        }
        if (this.requiresKnownWidowedPartner && !context.hasKnownWidowedPartner()) {
            return false;
        }
        if (this.requiresActiveSpecialOrders && !context.hasActiveSpecialOrders()) {
            return false;
        }
        return this.dispositions.isEmpty() || this.dispositions.contains(disposition);
    }

    public static DialogueOptionDefinition simple(String id, String label, DialogueRequestType requestType, int order) {
        return new DialogueOptionDefinition(
                id,
                label,
                requestType,
                true,
                true,
                Set.of(),
                Set.of(),
                VillagerEquipmentCondition.empty(),
                VillagerPlayerItemCondition.empty(),
                VillagerReputationCondition.empty(),
                DialogueItemPayment.empty(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                order
        );
    }
}
