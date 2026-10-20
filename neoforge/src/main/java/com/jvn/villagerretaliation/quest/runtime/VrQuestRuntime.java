package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.content.QuestContentAccess;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

final class VrQuestRuntime implements QuestRuntimeAccess {
    static final VrQuestRuntime INSTANCE = new VrQuestRuntime();
    private static final ResourceLocation ID = VillagerRetaliation.id("quests");

    private VrQuestRuntime() { }

    @Override public ResourceLocation id() { return ID; }

    @Override public QuestModelStore<? extends QuestProgressModel> progress(ServerLevel level) {
        return VillagerQuestSavedData.get(level);
    }

    @Override public QuestContentAccess content(MinecraftServer server) {
        return QuestContentCatalogs.current(server);
    }

    @Override public Optional<Entity> campaignActor(MinecraftServer server, ResourceLocation quest, String role) {
        return content(server).quest(quest).filter(definition -> definition.campaign() != null)
                .map(definition -> com.jvn.villagerretaliation.campaign.CampaignCharacterService.resolve(server, definition.campaign(), role).entity());
    }

    @Override public String executionIssue(ServerLevel level, UUID player, ResourceLocation quest, QuestProgressModel progress) {
        var definition = content(level.getServer()).quest(quest).orElse(null);
        return definition == null ? "Quest content is unavailable."
                : com.jvn.villagerretaliation.campaign.CampaignQuestAccess.executionIssue(level, player, definition,
                        progress instanceof VillagerQuestSavedData.QuestProgress legacy ? legacy : null);
    }

    @Override public boolean fail(ServerPlayer player, ResourceLocation quest, UUID expectedRun) {
        var progress = VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), quest);
        if (progress == null || !expectedRun.equals(progress.questRunId())) return false;
        Entity provider = null;
        if (progress.providerId() != null) for (var level : player.server.getAllLevels()) {
            provider = level.getEntity(progress.providerId());
            if (provider != null) break;
        }
        var context = provider instanceof Villager villager
                ? VillagerInteractionService.createDialogueContext((ServerLevel) villager.level(), player, villager) : DialogueContext.forPlayer(player);
        VillagerQuestService.performAction(context, quest, VillagerActionDefinition.QuestAction.FAIL);
        return progress.state() != VillagerQuestSavedData.QuestState.ACTIVE;
    }
}
