package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.skill.player.SocialCheckBreakdown;
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
    int contentRevision;
    String contentFingerprint;
    String compatibilityFingerprint = "";
    final List<String> recoveryHistory = new ArrayList<>();
    long revision;
    long visit;
    String node;
    Phase phase;
    int exchange;
    int effect;
    String response = "";
    long responseDeadline = -1;
    boolean responseTimerResolved;
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
    final Map<String, UUID> transactionChecks = new LinkedHashMap<>();
    final Set<String> socialAwards = new LinkedHashSet<>();
    final Map<String, Receipt> receipts = new LinkedHashMap<>();
    final Map<UUID, Request> requests = new LinkedHashMap<>();
    final Map<String, String> replacements = new LinkedHashMap<>();
    final Map<String, com.jvn.villagerretaliation.dialogue.DialogueCameraReferences.Saved> cameraReferences = new LinkedHashMap<>();

    ConversationSession(Identity identity, UUID controller, UUID questRun, String node, Speaker character) {
        this.identity = identity;
        this.contentRevision = identity.definitionRevision();
        this.contentFingerprint = identity.fingerprint();
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
        contentRevision = source.contentRevision;
        contentFingerprint = source.contentFingerprint;
        compatibilityFingerprint = source.compatibilityFingerprint;
        recoveryHistory.addAll(source.recoveryHistory);
        revision = source.revision;
        visit = source.visit;
        node = source.node;
        phase = source.phase;
        exchange = source.exchange;
        effect = source.effect;
        response = source.response;
        responseDeadline = source.responseDeadline;
        responseTimerResolved = source.responseTimerResolved;
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
        transactionChecks.putAll(source.transactionChecks);
        socialAwards.addAll(source.socialAwards);
        receipts.putAll(source.receipts);
        requests.putAll(source.requests);
        replacements.putAll(source.replacements);
        cameraReferences.putAll(source.cameraReferences);
    }

    public Identity identity() { return identity; }
    public long revision() { return revision; }
    public boolean active() { return phase != Phase.COMPLETE && phase != Phase.CANCELLED; }
    boolean hasPendingSocialAwards() {
        return checks.values().stream().anyMatch(check -> check.social() != null && !socialAwards.contains(check.id()));
    }

    public Snapshot snapshot() {
        return new Snapshot(identity, revision, visit, node, phase, exchange, response, check, outcome,
                controller, participants, speakers, transcript, choices, checks, receipts, socialAwards, diagnostic, confirmation != null ? confirmation
                        : nestedDecision != null && nestedDecision.state() == ConversationNestedDecision.State.PENDING ? nestedDecision.confirmation() : null, transactionChecks, responseDeadline, responseTimerResolved);
    }

    private static <K, V> Map<K, V> ordered(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public enum Phase { NODE_EFFECTS, EXCHANGES, RESPONSES, RESPONSE_EFFECTS, CHECK, ROLLED, COMPLETE, CONFIRMATION, CANCELLED }
    public enum ReceiptState { PREPARED, RESOLVED }
    public enum Intent { ADVANCE, CHOOSE, ROLL, RESUME, JOIN, TAKE_CONTROL, HAND_OVER, RELEASE_CONTROL, CONFIRM, DECLINE, CANCEL, MIGRATE }

    public record Identity(UUID id, ResourceLocation conversation, int definitionRevision, String fingerprint,
            ResourceLocation campaign, UUID campaignInstance, String campaignFingerprint,
            ResourceLocation quest, String characterRole, UUID character, ResourceLocation dimension,
            UUID owner, UUID party, String title, String topic, long createdAt) { }

    public record Speaker(String role, UUID entity, ResourceLocation dimension, String name) { }

    public record Line(String node, long visit, String exchange, ConversationDefinition.SpeakerKind kind,
            String role, UUID speaker, String name, String textKey, String fallback, Map<String, String> replacements,
            boolean localized) {
        public Line { replacements = Map.copyOf(replacements); }
        public Line(String node, long visit, String exchange, ConversationDefinition.SpeakerKind kind,
                String role, UUID speaker, String name, String textKey, String fallback) {
            this(node, visit, exchange, kind, role, speaker, name, textKey, fallback, Map.of(), false);
        }
    }

    public record Choice(UUID request, String node, long visit, String response, UUID player,
            Set<UUID> participants, long chosenAt, String labelKey, String fallback, Map<String, String> replacements) {
        public Choice { participants = Set.copyOf(participants); replacements = Map.copyOf(replacements); }
        public Choice(UUID request, String node, long visit, String response, UUID player, Set<UUID> participants, long chosenAt) {
            this(request, node, visit, response, player, participants, chosenAt, "", response, Map.of());
        }
    }

    public record Check(UUID request, String id, int die, int difficulty, int modifier, int total,
            boolean success, String destination, UUID player, long rolledAt, SocialCheckBreakdown social,
            List<ConversationCheckModifier> modifiers) {
        public Check {
            modifiers = List.copyOf(modifiers);
            int expected = (social == null ? 0 : social.bonus()) + ConversationCheckModifier.total(modifiers);
            if (die < 1 || die > 20 || difficulty < 0 || modifier != expected || total != die + modifier
                    || success != (die == 20 || die != 1 && total >= difficulty)) {
                throw new IllegalArgumentException("Saved conversation check has an invalid result.");
            }
        }

        public Check(UUID request, String id, int die, int difficulty, int modifier, int total,
                boolean success, String destination, UUID player, long rolledAt, SocialCheckBreakdown social) {
            this(request, id, die, difficulty, modifier, total, success, destination, player, rolledAt, social, List.of());
        }

        public Check(UUID request, String id, int die, int difficulty, int modifier, int total,
                boolean success, String destination, UUID player, long rolledAt) {
            this(request, id, die, difficulty, modifier, total, success, destination, player, rolledAt, null, List.of());
        }

        static Check roll(UUID request, ConversationDefinition.Check definition, int die, UUID player, long time) {
            return roll(request, definition, die, player, time, null, List.of());
        }

        static Check roll(UUID request, ConversationDefinition.Check definition, int die, UUID player, long time, SocialCheckBreakdown social) {
            return roll(request, definition, die, player, time, social, List.of());
        }

        static Check roll(UUID request, ConversationDefinition.Check definition, int die, UUID player, long time,
                SocialCheckBreakdown social, List<ConversationCheckModifier> modifiers) {
            if (definition.social() == null ? social != null : social == null
                    || !definition.social().progression().equals(social.progression()) || definition.social().skill() != social.skill()
                    || definition.social().experience() != social.rewardExperience())
                throw new IllegalArgumentException("The check requires its authored social progression snapshot.");
            if (definition.modifiers().size() != modifiers.size())
                throw new IllegalArgumentException("The check requires its authored situational modifiers.");
            for (int index = 0; index < modifiers.size(); index++) {
                var authored = definition.modifiers().get(index);
                var captured = modifiers.get(index);
                if (!authored.id().equals(captured.id()) || !authored.labelKey().equals(captured.labelKey()) || authored.amount() != captured.amount())
                    throw new IllegalArgumentException("The check requires its authored situational modifiers.");
            }
            int modifier = (social == null ? 0 : social.bonus()) + ConversationCheckModifier.total(modifiers);
            int total = die + modifier;
            boolean success = die == 20 || die != 1 && total >= definition.difficulty();
            return new Check(request, definition.id(), die, definition.difficulty(), modifier, total, success,
                    success ? definition.success() : definition.failure(), player, time, social, modifiers);
        }
    }

    public record Receipt(String key, UUID actor, UUID questRun, VillagerActionDefinition action,
            ReceiptState state, ActionResult result) { }

    public record Request(UUID player, long revision, Intent intent, String argument) { }

    public record Snapshot(Identity identity, long revision, long visit, String node, Phase phase,
            int exchange, String response, String check, String outcome, UUID controller,
            Set<UUID> participants, Map<String, Speaker> speakers, List<Line> transcript,
            List<Choice> choices, Map<String, Check> checks, Map<String, Receipt> receipts, Set<String> socialAwards,
            String diagnostic, com.jvn.villagerretaliation.campaign.CampaignConfirmation confirmation, Map<String, UUID> transactionChecks,
            long responseDeadline, boolean responseTimerResolved) {
        public Snapshot(Identity identity, long revision, long visit, String node, Phase phase,
                int exchange, String response, String check, String outcome, UUID controller,
                Set<UUID> participants, Map<String, Speaker> speakers, List<Line> transcript,
                List<Choice> choices, Map<String, Check> checks, Map<String, Receipt> receipts, Set<String> socialAwards,
                String diagnostic, com.jvn.villagerretaliation.campaign.CampaignConfirmation confirmation, Map<String, UUID> transactionChecks) {
            this(identity, revision, visit, node, phase, exchange, response, check, outcome, controller,
                    participants, speakers, transcript, choices, checks, receipts, socialAwards, diagnostic, confirmation,
                    transactionChecks, -1, false);
        }
        public Snapshot {
            participants = Set.copyOf(participants);
            speakers = ordered(speakers);
            transcript = List.copyOf(transcript);
            choices = List.copyOf(choices);
            checks = ordered(checks);
            receipts = ordered(receipts);
            socialAwards = Set.copyOf(socialAwards);
            transactionChecks = ordered(transactionChecks);
        }

        public boolean active() { return phase != Phase.COMPLETE && phase != Phase.CANCELLED; }

        public Snapshot(Identity identity, long revision, long visit, String node, Phase phase,
                int exchange, String response, String check, String outcome, UUID controller,
                Set<UUID> participants, Map<String, Speaker> speakers, List<Line> transcript,
                List<Choice> choices, Map<String, Check> checks, Map<String, Receipt> receipts, Set<String> socialAwards,
                String diagnostic, com.jvn.villagerretaliation.campaign.CampaignConfirmation confirmation) {
            this(identity, revision, visit, node, phase, exchange, response, check, outcome, controller, participants,
                    speakers, transcript, choices, checks, receipts, socialAwards, diagnostic, confirmation, Map.of());
        }

        public Snapshot(Identity identity, long revision, long visit, String node, Phase phase,
                int exchange, String response, String check, String outcome, UUID controller,
                Set<UUID> participants, Map<String, Speaker> speakers, List<Line> transcript,
                List<Choice> choices, Map<String, Check> checks, Map<String, Receipt> receipts,
                String diagnostic, com.jvn.villagerretaliation.campaign.CampaignConfirmation confirmation) {
            this(identity, revision, visit, node, phase, exchange, response, check, outcome, controller, participants,
                    speakers, transcript, choices, checks, receipts, Set.of(), diagnostic, confirmation);
        }
    }
}
