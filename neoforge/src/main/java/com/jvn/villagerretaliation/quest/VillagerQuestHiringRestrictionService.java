package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Resolves opt-in quest-provider restrictions on starting new paid hire contracts. */
public final class VillagerQuestHiringRestrictionService {
    private VillagerQuestHiringRestrictionService() {
    }

    public static boolean blocksHiring(ServerLevel level, Villager villager, ServerPlayer player) {
        return !blockingQuests(level, villager, player).isEmpty();
    }

    public static Set<ResourceLocation> blockingQuests(
            ServerLevel level,
            Villager villager,
            ServerPlayer player) {
        if (level == null || villager == null || player == null) {
            return Set.of();
        }

        boolean hasVillagerRestriction = VillagerQuestResources.compiledQuests(level.getServer()).stream()
                .map(CompiledQuest::provider)
                .anyMatch(provider -> provider.blocksHiring()
                        && VillagerQuestProviderType.ID.equals(provider.providerType()));
        if (!hasVillagerRestriction) {
            return Set.of();
        }

        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        Set<ResourceLocation> questIds = new LinkedHashSet<>();
        for (CompiledQuest quest : VillagerQuestResources.compiledQuests(level.getServer())) {
            if (quest.provider().blocksHiring()
                    && VillagerQuestProviderType.ID.equals(quest.provider().providerType())
                    && VillagerQuestProviderType.INSTANCE.matchesProviderFilters(context, quest.asQuestDefinition())) {
                questIds.add(quest.id());
            }
        }
        return Set.copyOf(questIds);
    }
}
