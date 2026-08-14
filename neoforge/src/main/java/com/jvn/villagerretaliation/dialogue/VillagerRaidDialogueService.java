package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.dialogue.resources.DialogueTuningResources;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.village.VillagerRaidMemorySavedData;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerRaidDialogueService {
    private VillagerRaidDialogueService() {
    }

    public static Optional<VillagerDialogueService.DialogueResult> selectRaidStory(DialogueContext context) {
        Optional<VillagerRaidMemorySavedData.RaidMemory> memory = VillagerRaidMemorySavedData.get(context.level())
                .memory(context.villager().getUUID(), context.player().getUUID());
        if (memory.isEmpty() || !DialogueTuningResources.passes(context, "raid.story_chance", 0.35D)) {
            return Optional.empty();
        }

        String outcome = memory.get().outcome().name().toLowerCase(java.util.Locale.ROOT);
        return VillagerDialogueResources.message(context, "raid_memory.story." + outcome)
                .filter(text -> !text.isBlank())
                .map(text -> new VillagerDialogueService.DialogueResult("raid_memory_story_" + outcome, text));
    }

    public static void claimVictoryAcknowledgement(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerRaidMemorySavedData.get(level)
                .claimVictoryAcknowledgement(villager.getUUID(), player.getUUID());
    }
}
