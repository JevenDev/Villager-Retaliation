package com.jvn.villagerretaliation.study;

import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * Places studying dialogue in the normal interaction priority chain.
 */
public final class VillagerStudyDialogueService {
    private VillagerStudyDialogueService() {
    }

    public static boolean tryHandle(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerStudyState state = VillagerStudyService.state(level, villager);
        if (!state.active() || state.skill() == null) {
            return false;
        }
        VillagerInteractionService.sendVillagerNotice(
                player,
                villager,
                "interaction.study.busy",
                Map.of("skill", localizedSkillName(state.skill())));
        return true;
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
