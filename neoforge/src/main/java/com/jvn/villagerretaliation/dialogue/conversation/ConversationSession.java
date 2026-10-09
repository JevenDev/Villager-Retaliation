package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class ConversationSession {
    final Identity identity;
    long revision;
    long visit;
    String node;
    Phase phase;
    int exchange;
    int effect;
    String response = "";
    String check = "";
    String outcome = "";
    String diagnostic = "";
    UUID controller;
    UUID actingPlayer;
    UUID questRun;
    com.jvn.villagerretaliation.campaign.CampaignConfirmation confirmation;
    UUID pendingChoice;
    ConversationNestedDecision nestedDecision;
    final Map<UUID, ConversationParticipationEvidence> nestedParticipation = new LinkedHashMap<>();
    com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants decisionParticipants;
    net.minecraft.nbt.CompoundTag consent = new net.minecraft.nbt.CompoundTag();
    final Set<UUID> participants = new LinkedHashSet<>();
    final Map<String, Speaker> speakers = new LinkedHashMap<>();
    final List<ConversationSpeakerHistory.Entry> speakerHistory = new ArrayList<>();
    final List<Line> transcript = new ArrayList<>();
    final List<Choice> choices = new ArrayList<>();
    final Map<ConversationObjectiveEvidence.Key, ConversationObjectiveEvidence> objectiveEvidence = new LinkedHashMap<>();
    final Map<String, Check> checks = new LinkedHashMap<>();
    final Map<String, Receipt> receipts = new LinkedHashMap<>();
    final Map<UUID, Request> requests = new LinkedHashMap<>();
    final Map<String, String> replacements = new LinkedHashMap<>();

    ConversationSession(Identity identity, UUID controller, UUID questRun, String node, Speaker character) {
        this.identity = identity;
        this.controller = controller;
        this.actingPlayer = controller;
        this.questRun = questRun;
        this.node = node;
        this.phase = Phase.NODE_EFFECTS;
        this.revision = 1;
        this.visit = 1;
        this.participants.add(controller);
        this.speakers.put(character.role(), character);
    }

    ConversationSession(ConversationSession source) {
        identity = source.identity;
        revision = source.revision;
        visit = source.visit;
        node = source.node;
        phase = source.phase;
        exchange = source.exchange;
        effect = source.effect;
        response = source.response;
        check = source.check;
        outcome = source.outcome;
        diagnostic = source.diagnostic;
        controller = source.controller;
        actingPlayer = source.actingPlayer;
        questRun = source.questRun;
        confirmation = source.confirmation;
        pendingChoice = source.pendingChoice;
        nestedDecision = source.nestedDecision;
        nestedParticipation.putAll(source.nestedParticipation);
        decisionParticipants = source.decisionParticipants;
        consent = source.consent.copy();
        participants.addAll(source.participants);
        speakers.putAll(source.speakers);
        speakerHistory.addAll(source.speakerHistory);
        transcript.addAll(source.transcript);
        choices.addAll(source.choices);
        objectiveEvidence.putAll(source.objectiveEvidence);
        checks.putAll(source.checks);
        receipts.putAll(source.receipts);
        requests.putAll(source.requests);
        replacements.putAll(source.replacements);
    }

    public Identity identity() { return identity; }
    public long revision() { return revision; }
    public boolean active() { return phase != Phase.COMPLETE; }

    public Snapshot snapshot() {
        return new Snapshot(identity, revision, visit, node, phase, exchange, response, check, outcome,
                controller, Set.copyOf(participants), ordered(speakers), List.copyOf(transcript),
                List.copyOf(choices), ordered(checks), ordered(receipts), diagnostic, confirmation != null ? confirmation
                        : nestedDecision != null && nestedDecision.state() == ConversationNestedDecision.State.PENDING ? nestedDecision.confirmation() : null);
    }

    private static <K, V> Map<K, V> ordered(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public enum Phase { NODE_EFFECTS, EXCHANGES, RESPONSES, RESPONSE_EFFECTS, CHECK, ROLLED, COMPLETE, CONFIRMATION }
    public enum ReceiptState { PREPARED, RESOLVED }
    public enum Intent { ADVANCE, CHOOSE, ROLL, RESUME, JOIN, TAKE_CONTROL, HAND_OVER, RELEASE_CONTROL, CONFIRM, DECLINE }

    public record Identity(UUID id, ResourceLocation conversation, int definitionRevision, String fingerprint,
            ResourceLocation campaign, UUID campaignInstance, String campaignFingerprint,
            ResourceLocation quest, String characterRole, UUID character, ResourceLocation dimension,
            UUID owner, UUID party, String title, String topic, long createdAt) { }

    public record Speaker(String role, UUID entity, ResourceLocation dimension, String name) { }

    public record Line(String node, long visit, String exchange, ConversationDefinition.SpeakerKind kind,
            String role, UUID speaker, String name, String textKey, String fallback) { }

    public record Choice(UUID request, String node, long visit, String response, UUID player,
            Set<UUID> participants, long chosenAt) {
        public Choice { participants = Set.copyOf(participants); }
    }

    public record Check(UUID request, String id, int die, int difficulty, int modifier, int total,
            boolean success, String destination, UUID player, long rolledAt) {
        public Check {
            if (die < 1 || die > 20 || difficulty < 0 || modifier != 0 || total != die
                    || success != (die == 20 || die != 1 && total >= difficulty)) {
                throw new IllegalArgumentException("Saved conversation check has an invalid result.");
            }
        }

        static Check roll(UUID request, ConversationDefinition.Check definition, int die, UUID player, long time) {
            boolean success = die == 20 || die != 1 && die >= definition.difficulty();
            return new Check(request, definition.id(), die, definition.difficulty(), 0, die, success,
                    success ? definition.success() : definition.failure(), player, time);
        }
    }

    public record Receipt(String key, UUID actor, UUID questRun, VillagerActionDefinition action,
            ReceiptState state, ActionResult result) { }

    public record Request(UUID player, long revision, Intent intent, String argument) { }

    public record Snapshot(Identity identity, long revision, long visit, String node, Phase phase,
            int exchange, String response, String check, String outcome, UUID controller,
            Set<UUID> participants, Map<String, Speaker> speakers, List<Line> transcript,
            List<Choice> choices, Map<String, Check> checks, Map<String, Receipt> receipts, String diagnostic, com.jvn.villagerretaliation.campaign.CampaignConfirmation confirmation) { }
}
