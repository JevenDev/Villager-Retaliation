package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.campaign.CampaignAuthority;
import com.jvn.villagerretaliation.campaign.CampaignBindingQueries;
import com.jvn.villagerretaliation.campaign.CampaignSavedData;
import com.jvn.villagerretaliation.campaign.CampaignWorldMode;
import com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class ConversationTopicInspection {
    private ConversationTopicInspection() { }

    public static Snapshot inspect(MinecraftServer server, ResourceLocation conversation, String topic, UUID playerId) {
        if (!server.isSameThread()) throw new IllegalStateException("Conversation inspection requires the server thread.");
        var definition = ConversationResources.conversation(server, conversation).orElse(null);
        var report = new Report(conversation, topic, playerId, definition);
        if (definition == null) return report.block("content", "Conversation content is missing or invalid. Check datapack diagnostics.");
        var declared = definition.topics().get(topic);
        if (declared == null) return report.block("topic", "Unknown conversation topic.");
        var player = server.getPlayerList().getPlayer(playerId);
        if (player == null) report.gates.add(new Gate("player", GateState.UNKNOWN, "Player is offline. Live permissions, distance, and conditions cannot be evaluated."));
        else report.gate("authority", CampaignAuthority.canControl(player), CampaignAuthority.controlIssue(player));
        report.gate("mode", CampaignWorldModeSavedData.get(server.overworld()).mode() == CampaignWorldMode.CAMPAIGN,
                "This conversation requires Campaign Mode.");
        var campaign = CampaignResources.current(server).campaigns().get(definition.campaign());
        if (campaign == null) return report.block("campaign", "Campaign content is missing or invalid. Saved history is retained.");
        var campaigns = CampaignSavedData.get(server.overworld());
        String compatibility = campaigns.compatibility(campaign.definition());
        if (!compatibility.isBlank()) return report.block("campaign", compatibility);
        var data = ConversationSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return report.block("storage", data.diagnostic());
        var character = CampaignBindingQueries.character(server, definition.campaign(), definition.character());
        if (!character.diagnostic().isBlank()) return report.block("character", character.diagnostic());
        if (character.binding() == null) {
            report.gates.add(new Gate("character", GateState.UNKNOWN,
                    "Role " + definition.character() + " has no saved binding. Starting may cast or spawn its authored actor."));
            return report.finish(Status.NEEDS_BINDING);
        }
        if (character.binding().lost()) return report.block("character", "This role has a recorded loss. Inspect its authored loss policy.");
        if (character.entity() == null) return report.block("character", "The bound character is unloaded. Its identity is retained and no death is inferred.");
        if (!character.available()) return report.block("character", "The bound character is downed. Conversation is paused.");
        if (player != null) report.gate("presence", ConversationService.near(player, character.entity()), "Return to this character to begin or continue.");
        UUID active = ConversationCharacters.active(server, data, character.entity().getUUID());
        if (active != null) {
            var session = data.session(active);
            report.session = session == null ? null : session.snapshot();
            if (!data.issue(active).isBlank()) return report.block("session", data.issue(active));
            if (session == null || !session.identity.conversation().equals(conversation)) {
                return report.block("session", "This character is already taking part in another conversation: " + active);
            }
            if (player != null && !ConversationService.member(player, session)) {
                return report.block("session", "The saved conversation belongs to another player or party.");
            }
            try {
                ConversationService.definition(server, session);
            } catch (ConversationService.Unavailable exception) {
                return report.block("session", exception.getMessage());
            }
            if (!session.speakers.get(session.identity.characterRole()).entity().equals(character.entity().getUUID())) {
                return report.block("session", "The saved conversation will reconcile its authorized character repair when reopened.");
            }
            report.gates.add(new Gate("session", GateState.PASS,
                    "Opening returns saved topic " + session.identity.topic() + " at node " + session.node + ". Entry conditions are not reapplied."));
            var controller = session.controller == null ? null : server.getPlayerList().getPlayer(session.controller);
            boolean controlling = controller != null && ConversationService.member(controller, session)
                    && CampaignAuthority.canControl(controller) && ConversationService.near(controller, character.entity());
            report.gates.add(new Gate("controller", GateState.PASS, !controlling
                    ? "An authorized member present at the character must take control to continue."
                    : controller.getUUID().equals(playerId) ? "This player controls the saved conversation."
                    : "The current controller must hand over explicitly before this player can advance."));
            report.gate("interruption", !VillagerConversationService.isConversing(character.entity()), "Wait for the current interruption to finish.");
            if (!session.diagnostic.isBlank()) report.gates.add(new Gate("continuation", GateState.BLOCKED, session.diagnostic));
            return report.finish(Status.EXISTING_SESSION);
        }
        report.gate("ordinary_conversation", !VillagerConversationService.isConversing(character.entity()),
                "This character is already taking part in an ordinary conversation.");
        if (!declared.conditions().isEmpty() && player != null && player.serverLevel() == character.entity().level()) {
            report.context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, character.entity());
        }
        report.evaluate = true;
        return report.finish(Status.AVAILABLE);
    }

    public enum Status { AVAILABLE, EXISTING_SESSION, NEEDS_BINDING, BLOCKED, UNKNOWN }
    public enum GateState { PASS, BLOCKED, UNKNOWN }
    public record Gate(String id, GateState state, String message) { }
    public record Condition(String pointer, DialogueCondition.ConditionEvaluationTrace trace) { }
    public record Snapshot(ResourceLocation conversation, String topic, UUID player, ConversationDefinition definition,
            Status status, List<Gate> gates, List<Condition> conditions, ConversationSession.Snapshot session) {
        public Snapshot {
            gates = List.copyOf(gates);
            conditions = List.copyOf(conditions);
        }
    }

    private static final class Report {
        final ResourceLocation conversation;
        final String topic;
        final UUID player;
        final ConversationDefinition definition;
        final List<Gate> gates = new ArrayList<>();
        DialogueContext context;
        ConversationSession.Snapshot session;
        boolean evaluate;

        Report(ResourceLocation conversation, String topic, UUID player, ConversationDefinition definition) {
            this.conversation = conversation;
            this.topic = topic;
            this.player = player;
            this.definition = definition;
        }

        void gate(String id, boolean passed, String failure) {
            gates.add(new Gate(id, passed ? GateState.PASS : GateState.BLOCKED, passed ? "Passed" : failure));
        }

        Snapshot block(String id, String message) {
            gates.add(new Gate(id, GateState.BLOCKED, message));
            return finish(Status.BLOCKED);
        }

        Snapshot finish(Status proposed) {
            List<Condition> conditions = new ArrayList<>();
            var declared = definition == null ? null : definition.topics().get(topic);
            if (evaluate && declared != null) {
                for (int i = 0; i < declared.conditions().size(); i++) {
                    conditions.add(new Condition(declared.pointer() + "/conditions/" + i,
                            DialogueCondition.trace(context, declared.conditions().get(i))));
                }
            }
            Status status = gates.stream().anyMatch(gate -> gate.state() == GateState.BLOCKED)
                    || conditions.stream().anyMatch(condition -> condition.trace().outcome() == DialogueCondition.ConditionOutcome.UNMET)
                    ? Status.BLOCKED
                    : proposed == Status.NEEDS_BINDING ? proposed
                    : gates.stream().anyMatch(gate -> gate.state() == GateState.UNKNOWN)
                    || conditions.stream().anyMatch(condition -> condition.trace().outcome() == DialogueCondition.ConditionOutcome.UNKNOWN)
                    ? Status.UNKNOWN : proposed;
            if (!evaluate && declared != null && !declared.conditions().isEmpty() && proposed != Status.EXISTING_SESSION) {
                gates.add(new Gate("conditions", GateState.UNKNOWN, "Entry conditions were not evaluated because a preceding gate is unresolved."));
            }
            return new Snapshot(conversation, topic, player, definition, status, gates, conditions, session);
        }
    }
}
