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
        return this.dispositions.isEmpty() || this.dispositions.contains(disposition);
    }

    public static DialogueOptionDefinition simple(String id, String label, DialogueRequestType requestType, int order) {
        return new DialogueOptionDefinition(id, label, requestType, true, true, Set.of(), Set.of(), order);
    }
}
