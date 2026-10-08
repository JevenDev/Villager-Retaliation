package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.campaign.CampaignQuestAccess;
import com.jvn.villagerretaliation.campaign.CampaignStoryObjective;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ConversationObjectiveEvidence(UUID player, ResourceLocation quest, UUID questRun, String objective,
        String node, long visit, String exchange, String role, UUID speaker) {
    public ConversationObjectiveEvidence {
        if (player == null || quest == null || questRun == null || objective == null || objective.isBlank()
                || node == null || node.isBlank() || visit < 1 || exchange == null || exchange.isBlank()
                || role == null || role.isBlank() || speaker == null) throw new IllegalArgumentException("Invalid conversation objective evidence.");
    }

    static void capture(ServerPlayer controller, ConversationSession session) {
        if (session.transcript.isEmpty()) return;
        var line = session.transcript.getLast();
        if (line.visit() != session.visit || !line.node().equals(session.node) || line.kind() != ConversationDefinition.SpeakerKind.ROLE) return;
        var quests = VillagerQuestSavedData.get(controller.serverLevel());
        var catalog = QuestContentCatalogs.current(controller.server);
        for (UUID playerId : ConversationService.presentParticipants(controller, session)) {
            for (var entry : quests.activeProgress(playerId)) {
                var definition = catalog.quest(entry.getKey()).orElse(null);
                var progress = entry.getValue();
                if (definition == null || !session.identity.campaign().equals(definition.campaign()) || progress.questRunId() == null
                        || !CampaignQuestAccess.executionIssue(controller.serverLevel(), playerId, definition, progress).isBlank()) continue;
                for (var objective : QuestObjectiveQuery.activeObjectives(definition, progress)) {
                    if (progress.objectiveComplete(objective.id()) || !(objective.story() instanceof CampaignStoryObjective.Speak speak)
                            || !speak.conversation().equals(session.identity.conversation()) || !speak.node().equals(line.node())
                            || !speak.role().equals(line.role())) continue;
                    var evidence = new ConversationObjectiveEvidence(playerId, definition.id(), progress.questRunId(), objective.id(),
                            line.node(), line.visit(), line.exchange(), line.role(), line.speaker());
                    session.objectiveEvidence.putIfAbsent(evidence.key(session.identity), evidence);
                }
            }
        }
    }

    public Key key(ConversationSession.Identity identity) {
        return new Key(identity.campaign(), identity.campaignInstance(), identity.campaignFingerprint(), player, quest, questRun, objective,
                identity.conversation(), identity.fingerprint(), node, role);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Player", player); tag.putString("Quest", quest.toString()); tag.putUUID("QuestRun", questRun);
        tag.putString("Objective", objective); tag.putString("Node", node); tag.putLong("Visit", visit);
        tag.putString("Exchange", exchange); tag.putString("Role", role); tag.putUUID("Speaker", speaker);
        return tag;
    }

    static ConversationObjectiveEvidence load(CompoundTag tag, ConversationSession session) {
        var evidence = new ConversationObjectiveEvidence(ConversationSessionCodec.uuid(tag, "Player"), ConversationSessionCodec.resource(tag, "Quest"),
                ConversationSessionCodec.uuid(tag, "QuestRun"), ConversationSessionCodec.text(tag, "Objective"), ConversationSessionCodec.text(tag, "Node"),
                ConversationSessionCodec.number(tag, "Visit"), ConversationSessionCodec.text(tag, "Exchange"), ConversationSessionCodec.text(tag, "Role"),
                ConversationSessionCodec.uuid(tag, "Speaker"));
        var bound = session.speakers.get(evidence.role);
        ConversationSessionCodec.require(session.participants.contains(evidence.player) && bound != null && bound.entity().equals(evidence.speaker)
                && session.transcript.stream().anyMatch(line -> line.kind() == ConversationDefinition.SpeakerKind.ROLE && line.visit() == evidence.visit
                        && line.node().equals(evidence.node) && line.exchange().equals(evidence.exchange) && line.role().equals(evidence.role)
                        && evidence.speaker.equals(line.speaker())), "conversation objective evidence has no matching participant or exchange");
        return evidence;
    }

    public record Key(ResourceLocation campaign, UUID instance, String campaignFingerprint, UUID player, ResourceLocation quest,
            UUID questRun, String objective, ResourceLocation conversation, String conversationFingerprint, String node, String role) { }
}
