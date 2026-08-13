package com.jvn.villagerretaliation.study;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;

/**
 * Places studying dialogue in the normal interaction priority chain.
 */
public final class VillagerStudyDialogueService {
    private VillagerStudyDialogueService() {
    }

    public static Optional<String> openingLine(DialogueContext context) {
        VillagerStudyState state = VillagerStudyService.state(context.level(), context.villager());
        if (!usesStudyOpening(state)) {
            return Optional.empty();
        }
        return VillagerDialogueResources.message(
                context,
                "interaction.study.busy",
                Map.of("skill", localizedSkillName(state.skill())));
    }

    static boolean usesStudyOpening(VillagerStudyState state) {
        return state != null && state.studying() && state.skill() != null;
    }

    public static String localizedSkillName(VillagerSkill skill) {
        String key = skill.translationKey();
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key)) {
            return translated;
        }
        return Arrays.stream(skill.serializedName().split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining(" "));
    }
}
