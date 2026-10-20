package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.campaign.CampaignCharacterService;
import com.jvn.villagerretaliation.campaign.CampaignWorldMode;
import com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;

final class VrConversationActors implements ConversationActors.Access {
    static final VrConversationActors INSTANCE = new VrConversationActors();

    private VrConversationActors() { }

    static DialogueContext context(ServerPlayer player, Villager actor) {
        return VillagerInteractionService.createDialogueContext(player.serverLevel(), player, actor);
    }

    @Override public boolean matches(ServerPlayer player, ConversationDefinition definition, LivingEntity actor) {
        var provider = (Villager) actor;
        if (definition.campaign() != null) return CampaignWorldModeSavedData.get(player.serverLevel()).mode() == CampaignWorldMode.CAMPAIGN
                && CampaignCharacterService.matchesBoundCharacter(player.server, definition.campaign(), definition.character(), provider.getUUID());
        var quest = QuestContentCatalogs.current(player.server).quest(definition.quest()).orElse(null);
        if (quest == null || quest.campaign() != null || provider.level() != player.serverLevel()) return false;
        var context = context(player, provider);
        var progress = VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), quest.id());
        return VillagerQuestProviderType.INSTANCE.matchesProviderFilters(context, quest)
                && (progress == null || QuestAvailabilityService.matchesProviderLock(context, quest, progress));
    }

    @Override public boolean conversable(ServerPlayer player, LivingEntity actor) {
        return VillagerInteractionService.shouldStayConversable(player, (Villager) actor);
    }

    @Override public boolean interrupted(ServerPlayer player, LivingEntity actor) {
        return VillagerConversationService.isConversing((Villager) actor);
    }
}
