package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

public final class CampaignQuestCharacters {
    private CampaignQuestCharacters() { }

    static String role(MinecraftServer server, ResourceLocation quest) {
        return quest == null ? "" : VillagerQuestResources.compiledQuest(server, quest)
                .map(value -> value.provider().campaignRole()).orElse("");
    }

    public static boolean canReplaceProvider(MinecraftServer server, ResourceLocation questId, UUID previous, UUID replacement) {
        var quest = questId == null ? null : VillagerQuestResources.quest(server, questId).orElse(null);
        return quest != null && quest.campaign() != null && !CampaignCharacterReplacements.authorization(
                server, quest.campaign(), role(server, questId), previous, replacement).isBlank();
    }

    static boolean reconcile(ServerPlayer player, QuestDefinition quest, VillagerQuestSavedData.QuestProgress progress) throws IOException {
        if (progress == null || progress.startedVillagerId() == null) return false;
        String role = role(player.getServer(), quest.id());
        if (role.isBlank()) return false;
        var characters = CampaignCharacterSavedData.get(player.serverLevel());
        var original = characters.bindings().get(new CampaignCharacterBinding.Key(quest.campaign(), role));
        if (!characters.diagnostic().isBlank() || original == null || !original.lost()) return false;
        var resolved = CampaignCharacterService.resolve(player.getServer(), quest.campaign(), role);
        if (!resolved.available()) return false;
        String reason = CampaignCharacterReplacements.authorization(player.getServer(), quest.campaign(), role,
                progress.startedVillagerId(), resolved.entity().getUUID());
        if (reason.isBlank()) return false;
        var provider = resolved.entity();
        var context = VillagerInteractionService.createDialogueContext((ServerLevel) provider.level(), player, provider);
        progress.rebindProvider(VillagerQuestProviderType.INSTANCE.bindingFromDialogueContext(context), player.serverLevel().getGameTime(), reason);
        var quests = VillagerQuestSavedData.get(player.serverLevel());
        quests.requireCampaignPersistence();
        quests.persistCampaignProgress(player.serverLevel());
        return true;
    }

    static String executionIssue(MinecraftServer server, QuestDefinition quest, VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || progress.startedVillagerId() == null) return "";
        String role = role(server, quest.id());
        if (role.isBlank()) return "";
        var original = CampaignBindingQueries.originalCharacter(server, quest.campaign(), role);
        if (!original.diagnostic().isBlank()) return original.diagnostic();
        if (original.binding() == null) return "Campaign quest provider has no saved character binding. Restore its original campaign character data.";
        if (!original.binding().lost()
                || original.binding().lossPolicy() != com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition.LossPolicy.REPLACEMENT) return "";
        var active = CampaignBindingQueries.character(server, quest.campaign(), role);
        return active.binding() != null && !active.binding().lost() && active.diagnostic().isBlank()
                && active.binding().entityId().equals(progress.startedVillagerId()) ? ""
                : "Campaign quest is waiting for its authored replacement character and saved provider binding.";
    }

    public static UUID continuationProvider(MinecraftServer server, ResourceLocation questId, UUID original) {
        var quest = questId == null ? null : VillagerQuestResources.quest(server, questId).orElse(null);
        if (quest == null || quest.campaign() == null) return original;
        String role = role(server, questId);
        if (role.isBlank()) return original;
        var active = CampaignBindingQueries.character(server, quest.campaign(), role);
        if (!active.available()) return null;
        UUID current = active.binding().entityId();
        return current.equals(original) || canReplaceProvider(server, questId, original, current) ? current : null;
    }
}
