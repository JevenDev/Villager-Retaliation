package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.campaign.CampaignCharacterService;
import com.jvn.villagerretaliation.campaign.CampaignSavedData;
import com.jvn.villagerretaliation.campaign.CampaignWorldMode;
import com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import static com.jvn.villagerretaliation.dialogue.conversation.ConversationService.require;

public final class ConversationBinding {
    private ConversationBinding() { }

    static void validateCampaign(MinecraftServer server, ResourceLocation campaign, UUID originalInstance, String fingerprint) {
        if (campaign == null) return;
        require(CampaignWorldModeSavedData.get(server.overworld()).mode() == CampaignWorldMode.CAMPAIGN, "This conversation requires Campaign Mode.");
        var content = CampaignResources.current(server).campaigns().get(campaign);
        require(content != null, "Campaign content is unavailable. Its saved conversation is retained.");
        var data = CampaignSavedData.get(server.overworld());
        String issue = data.compatibility(content.definition());
        require(issue.isBlank(), issue);
        var instance = data.instances().get(campaign);
        require(originalInstance == null || instance != null && originalInstance.equals(instance.instanceId())
                && fingerprint.equals(instance.fingerprint()), "Restore the original campaign instance before continuing this conversation.");
    }

    public static boolean matches(ServerPlayer player, ConversationDefinition definition, Villager provider) {
        if (definition.campaign() != null) return CampaignWorldModeSavedData.get(player.serverLevel()).mode() == CampaignWorldMode.CAMPAIGN
                && CampaignCharacterService.matchesBoundCharacter(player.server, definition.campaign(), definition.character(), provider.getUUID());
        var quest = QuestContentCatalogs.current(player.server).quest(definition.quest()).orElse(null);
        if (quest == null || quest.campaign() != null || provider.level() != player.serverLevel()) return false;
        var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, provider);
        var progress = VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), quest.id());
        return VillagerQuestProviderType.INSTANCE.matchesProviderFilters(context, quest)
                && (progress == null || QuestAvailabilityService.matchesProviderLock(context, quest, progress));
    }

    static Villager resolve(ServerPlayer player, ConversationDefinition definition, Villager requested) {
        if (definition.campaign() != null) {
            var resolved = CampaignCharacterService.resolve(player.server, definition.campaign(), definition.character());
            require(resolved.available(), resolved.diagnostic());
            require(requested == null || requested.getUUID().equals(resolved.entity().getUUID()), "This topic belongs to another character.");
            return resolved.entity();
        }
        var provider = requested != null ? requested : player.serverLevel().getEntitiesOfClass(Villager.class,
                player.getBoundingBox().inflate(16), entity -> matches(player, definition, entity)).stream()
                .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        require(provider != null && matches(player, definition, provider), "Return to this quest's provider to begin the conversation.");
        return provider;
    }

    static void bindCast(ServerPlayer player, ConversationDefinition definition, Villager provider, ConversationSession session) {
        for (var entry : definition.characters().entrySet()) {
            var candidates = player.serverLevel().getEntitiesOfClass(Villager.class,
                    provider.getBoundingBox().inflate(entry.getValue().radius()),
                    entity -> entity.isAlive() && !entity.getUUID().equals(provider.getUUID()) && entity.getTags().contains(entry.getValue().tag()));
            require(candidates.size() == 1, "The character " + entry.getKey() + " requires exactly one nearby villager tagged " + entry.getValue().tag() + ".");
            var target = candidates.getFirst();
            require(session.speakers.values().stream().noneMatch(value -> value.entity().equals(target.getUUID())), "Each conversation role requires a different villager.");
            require(!com.jvn.villagerretaliation.interaction.VillagerConversationService.isConversing(target)
                    && ConversationCharacters.active(player.server, ConversationSavedData.get(player.serverLevel()), target.getUUID()) == null,
                    "The character " + entry.getKey() + " is already in a conversation.");
            session.speakers.put(entry.getKey(), new ConversationSession.Speaker(entry.getKey(), target.getUUID(),
                    target.level().dimension().location(), target.getName().getString()));
        }
    }

    static Villager speaker(ServerPlayer player, ConversationSession session, String role) {
        var prior = session.speakers.get(role);
        if (session.identity.campaign() == null) {
            require(prior != null, "This conversation character has no saved binding.");
            var entity = player.serverLevel().getEntity(prior.entity());
            require(entity instanceof Villager && entity.isAlive(), "Return to the saved character " + role + " to continue.");
            return (Villager) entity;
        }
        var resolved = CampaignCharacterService.resolve(player.server, session.identity.campaign(), role);
        require(resolved.available(), resolved.diagnostic());
        require(prior == null || resolved.entity().getUUID().equals(prior.entity()),
                "The conversation is waiting for its repaired character binding to be saved.");
        return resolved.entity();
    }
}
