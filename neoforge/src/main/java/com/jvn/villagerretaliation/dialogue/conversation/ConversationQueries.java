package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.campaign.CampaignAuthority;
import com.jvn.villagerretaliation.campaign.CampaignCharacterSavedData;
import com.jvn.villagerretaliation.campaign.CampaignCharacterService;
import com.jvn.villagerretaliation.campaign.CampaignSavedData;
import com.jvn.villagerretaliation.campaign.CampaignWorldMode;
import com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueAvailability;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class ConversationQueries {
    private ConversationQueries() { }

    public static UUID active(ServerPlayer player, Villager character) {
        return ConversationCharacters.active(player.server, ConversationSavedData.get(player.serverLevel()), character.getUUID());
    }

    public static ConversationSession.Snapshot saved(ServerPlayer player, UUID id) {
        var session = ConversationSavedData.get(player.serverLevel()).session(id);
        return session == null || !ConversationService.member(player, session) ? null : session.snapshot();
    }

    static boolean referencesCharacter(ServerPlayer player, UUID id, UUID character) {
        var session = ConversationSavedData.get(player.serverLevel()).session(id);
        return session != null && session.speakers.values().stream().anyMatch(speaker -> speaker.entity().equals(character));
    }

    public static List<Topic> topics(ServerPlayer player, Villager character) {
        var topics = new ArrayList<Topic>();
        var binding = CampaignCharacterSavedData.get(player.serverLevel()).forEntity(character.getUUID());
        var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, character);
        if (binding != null && CampaignWorldModeSavedData.get(player.serverLevel()).mode() == CampaignWorldMode.CAMPAIGN) {
            var content = CampaignResources.current(player.server).campaigns().get(binding.campaign());
            boolean compatible = content != null && CampaignSavedData.get(player.serverLevel()).compatibility(content.definition()).isBlank();
            if (compatible) for (var definition : QuestContentCatalogs.current(player.server).conversations().values().stream()
                    .filter(value -> binding.campaign().equals(value.campaign())
                            && CampaignCharacterService.matchesBoundCharacter(player.server, value.campaign(), value.character(), character.getUUID()))
                    .sorted(java.util.Comparator.comparing(value -> value.id().toString())).toList()) {
                appendTopics(player, context, definition, CampaignAuthority.canControl(player), topics);
            }
        }
        for (var definition : QuestContentCatalogs.current(player.server).conversations().values().stream()
                .filter(value -> value.campaign() == null && ConversationBinding.matches(player, value, character))
                .sorted(java.util.Comparator.comparing(value -> value.id().toString())).toList()) {
            appendTopics(player, context, definition, true, topics);
        }
        for (var saved : ConversationSavedData.get(player.serverLevel()).finishedSessions(character.getUUID()))
            if (ConversationService.member(player, saved)) topics.add(new Topic(saved.identity.conversation(),
                    saved.identity.id().toString(), saved.identity.title(), true, true, "", TopicGroup.HISTORY));
        topics.sort(java.util.Comparator.comparing(Topic::group));
        return List.copyOf(topics);
    }

    private static void appendTopics(ServerPlayer player, DialogueContext context, ConversationDefinition definition,
            boolean authority, List<Topic> topics) {
        var group = topicGroup(context, definition.quest());
        for (var topic : definition.topics().values()) {
            boolean eligible = ConversationService.conditions(context, topic.conditions());
            String reason = !eligible ? DialogueAvailability.reason(context, topic.conditions())
                    : authority ? "" : CampaignAuthority.controlIssue(player);
            topics.add(new Topic(definition.id(), topic.id(), ConversationService.text(player, topic.labelKey()),
                    eligible, eligible && authority, reason, group));
        }
    }

    public static Session view(ServerPlayer player, UUID id) {
        var result = ConversationService.view(player, id);
        if (result.session() == null) return new Session(null, List.of(), null, null, Control.NONE, result.diagnostic());
        var saved = ConversationSavedData.get(player.serverLevel()).session(id);
        var snapshot = result.session();
        var primary = ConversationCharacters.primary(player, saved);
        boolean valid = ConversationSavedData.get(player.serverLevel()).issue(id).isBlank() && primary != null
                && ConversationService.near(player, primary)
                && (saved.identity.campaign() == null || CampaignCharacterService.matchesBoundCharacter(player.server,
                        saved.identity.campaign(), saved.identity.characterRole(), primary.getUUID()));
        ConversationDefinition definition = null;
        try { definition = ConversationService.definition(player.server, saved); }
        catch (ConversationService.Unavailable unavailable) { valid = false; }
        boolean authority = ConversationService.canControl(player, saved) && ConversationService.member(player, saved);
        boolean control = valid && saved.active() && authority && player.getUUID().equals(snapshot.controller())
                && !VillagerConversationService.isConversing(primary);
        var controller = snapshot.controller() == null ? null : player.server.getPlayerList().getPlayer(snapshot.controller());
        boolean takeover = valid && saved.active() && authority && !control && (controller == null || !ConversationService.member(controller, saved)
                || !ConversationService.canControl(controller, saved) || !ConversationService.near(controller, primary));
        var handover = new ArrayList<Member>();
        if (control) PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).ifPresent(party -> {
            for (UUID member : party.playerIds()) {
                var target = player.server.getPlayerList().getPlayer(member);
                if (target != null && !target.getUUID().equals(player.getUUID()) && ConversationService.member(target, saved)
                        && ConversationService.canControl(target, saved) && ConversationService.near(target, primary))
                    handover.add(new Member(member, target.getName().getString()));
            }
        });
        return new Session(snapshot, result.responses(), definition, saved.actingPlayer,
                new Control(valid, control, takeover, controller == null ? "" : controller.getName().getString(), handover,
                        ConversationRecovery.canCancel(player, saved), ConversationRecovery.canMigrate(player, saved)), result.diagnostic());
    }

    static String playerName(ServerPlayer viewer, UUID id) {
        var player = viewer.server.getPlayerList().getPlayer(id);
        if (player != null) return player.getGameProfile().getName();
        var cache = viewer.server.getProfileCache();
        return cache == null ? id.toString() : cache.get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString());
    }

    private static TopicGroup topicGroup(DialogueContext context, ResourceLocation quest) {
        if (quest == null) return TopicGroup.GENERAL;
        if (com.jvn.villagerretaliation.quest.VillagerQuestService.matchesState(context, quest, java.util.Set.of("ready")))
            return TopicGroup.REPORTING_BACK;
        if (com.jvn.villagerretaliation.quest.VillagerQuestService.matchesState(context, quest, java.util.Set.of("active")))
            return TopicGroup.CURRENT_OBJECTIVES;
        if (com.jvn.villagerretaliation.quest.VillagerQuestService.matchesState(context, quest, java.util.Set.of("completed")))
            return TopicGroup.FOLLOW_UPS;
        return TopicGroup.NEW_BUSINESS;
    }

    public enum TopicGroup { NEW_BUSINESS, CURRENT_OBJECTIVES, REPORTING_BACK, FOLLOW_UPS, GENERAL, HISTORY }
    public record Topic(ResourceLocation conversation, String id, String label, boolean eligible,
            boolean available, String reason, TopicGroup group) { }
    public record Member(UUID id, String name) { }
    public record Control(boolean valid, boolean canControl, boolean canTakeControl, String controller,
            List<Member> handover, boolean canCancel, boolean canMigrate) {
        static final Control NONE = new Control(false, false, false, "", List.of(), false, false);
        public Control { handover = List.copyOf(handover); }
    }
    public record Session(ConversationSession.Snapshot snapshot, List<ConversationService.Response> responses,
            ConversationDefinition definition, UUID actingPlayer, Control control, String diagnostic) {
        public Session { responses = List.copyOf(responses); }
    }
}
