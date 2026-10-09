package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import java.io.IOException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

final class CampaignQuestLocations {
    private CampaignQuestLocations() { }

    static boolean reconcile(ServerPlayer player, QuestDefinition quest, VillagerQuestSavedData.QuestProgress progress) throws IOException {
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || progress.targetPos() == null
                || progress.targetDimension() == null) return false;
        var objective = QuestObjectiveQuery.activeObjectives(quest, progress).stream()
                .filter(value -> value.id().equals(progress.targetObjectiveId()) && !value.campaignLocation().isBlank()).findFirst().orElse(null);
        if (objective == null) return false;
        var resolved = CampaignBindingQueries.location(player.server, quest.campaign(), objective.campaignLocation());
        var binding = resolved.binding();
        if (!resolved.diagnostic().isBlank() || binding == null || binding.position().equals(progress.targetPos())
                && binding.dimension().equals(progress.targetDimension().location())) return false;
        var locations = CampaignLocationSavedData.get(player.serverLevel());
        if (locations.authorization(binding, progress.targetDimension().location(), progress.targetPos()).isEmpty()) return false;
        progress.setTarget(progress.startedVillagerId(), ResourceKey.create(Registries.DIMENSION, binding.dimension()), binding.position(), objective.id());
        var quests = VillagerQuestSavedData.get(player.serverLevel());
        quests.requireCampaignPersistence();
        quests.persistCampaignProgress(player.serverLevel());
        return true;
    }
}
