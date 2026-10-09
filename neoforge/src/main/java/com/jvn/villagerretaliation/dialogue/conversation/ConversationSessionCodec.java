package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

final class ConversationSessionCodec {
    private ConversationSessionCodec() { }

    static CompoundTag save(ConversationSession session) {
        var tag = new CompoundTag();
        var identity = session.identity;
        tag.putUUID("Id", identity.id());
        tag.putString("Conversation", identity.conversation().toString());
        tag.putInt("DefinitionRevision", identity.definitionRevision());
        tag.putString("Fingerprint", identity.fingerprint());
        tag.putString("Campaign", identity.campaign().toString());
        tag.putUUID("CampaignInstance", identity.campaignInstance());
        tag.putString("CampaignFingerprint", identity.campaignFingerprint());
        if (identity.quest() != null) tag.putString("Quest", identity.quest().toString());
        tag.putString("CharacterRole", identity.characterRole());
        tag.putUUID("Character", identity.character());
        tag.putString("Dimension", identity.dimension().toString());
        tag.putUUID("Owner", identity.owner());
        if (identity.party() != null) tag.putUUID("Party", identity.party());
        tag.putString("Title", identity.title());
        tag.putString("Topic", identity.topic());
        tag.putLong("CreatedAt", identity.createdAt());
        tag.putLong("Revision", session.revision);
        tag.putLong("Visit", session.visit);
        tag.putString("Node", session.node);
        tag.putString("Phase", session.phase.name());
        tag.putInt("Exchange", session.exchange);
        tag.putInt("Effect", session.effect);
        tag.putString("Response", session.response);
        tag.putString("Check", session.check);
        tag.putString("Outcome", session.outcome);
        tag.putString("Diagnostic", session.diagnostic);
        if (session.controller != null) tag.putUUID("Controller", session.controller);
        tag.putUUID("ActingPlayer", session.actingPlayer);
        if (session.questRun != null) tag.putUUID("QuestRun", session.questRun);
        tag.put("Consent", session.consent.copy());
        tag.put("DecisionParticipants", session.decisionParticipants == null ? new CompoundTag() : session.decisionParticipants.save());
        if (session.confirmation != null) tag.put("Confirmation", session.confirmation.save());
        if (session.nestedDecision != null) tag.put("NestedDecision", session.nestedDecision.save());
        var nestedParticipation = new ListTag();
        session.nestedParticipation.values().forEach(evidence -> nestedParticipation.add(evidence.save()));
        tag.put("NestedParticipation", nestedParticipation);
        if (session.pendingChoice != null) tag.putUUID("PendingChoice", session.pendingChoice);
        tag.put("Participants", players(session.participants));
        var speakers = new ListTag();
        session.speakers.values().forEach(speaker -> {
            var value = new CompoundTag();
            value.putString("Role", speaker.role());
            value.putUUID("Entity", speaker.entity());
            value.putString("Dimension", speaker.dimension().toString());
            value.putString("Name", speaker.name());
            speakers.add(value);
        });
        tag.put("Speakers", speakers);
        tag.put("SpeakerHistory", ConversationSpeakerHistory.save(session));
        var lines = new ListTag();
        session.transcript.forEach(line -> {
            var value = new CompoundTag();
            value.putString("Node", line.node());
            value.putLong("Visit", line.visit());
            value.putString("Exchange", line.exchange());
            value.putString("Kind", line.kind().name());
            value.putString("Role", line.role());
            if (line.speaker() != null) value.putUUID("Speaker", line.speaker());
            value.putString("Name", line.name());
            value.putString("TextKey", line.textKey());
            value.putString("Fallback", line.fallback());
            lines.add(value);
        });
        tag.put("Transcript", lines);
        var objectiveEvidence = new ListTag();
        session.objectiveEvidence.values().forEach(evidence -> objectiveEvidence.add(evidence.save()));
        tag.put("ObjectiveEvidence", objectiveEvidence);
        var choices = new ListTag();
        session.choices.forEach(choice -> {
            var value = new CompoundTag();
            value.putUUID("Request", choice.request());
            value.putString("Node", choice.node());
            value.putLong("Visit", choice.visit());
            value.putString("Response", choice.response());
            value.putUUID("Player", choice.player());
            value.put("Participants", players(choice.participants()));
            value.putLong("ChosenAt", choice.chosenAt());
            choices.add(value);
        });
        tag.put("Choices", choices);
        var checks = new ListTag();
        session.checks.values().forEach(check -> {
            var value = new CompoundTag();
            value.putUUID("Request", check.request());
            value.putString("Id", check.id());
            value.putInt("Die", check.die());
            value.putInt("Difficulty", check.difficulty());
            value.putInt("Modifier", check.modifier());
            value.putInt("Total", check.total());
            value.putBoolean("Success", check.success());
            value.putString("Destination", check.destination());
            value.putUUID("Player", check.player());
            value.putLong("RolledAt", check.rolledAt());
            checks.add(value);
        });
        tag.put("Checks", checks);
        var receipts = new ListTag();
        session.receipts.values().forEach(receipt -> {
            var value = new CompoundTag();
            value.putString("Key", receipt.key());
            value.putUUID("Actor", receipt.actor());
            if (receipt.questRun() != null) value.putUUID("QuestRun", receipt.questRun());
            value.put("Action", SceneContinuation.saveAction(receipt.action()));
            value.putString("State", receipt.state().name());
            value.put("Result", result(receipt.result()));
            receipts.add(value);
        });
        tag.put("Receipts", receipts);
        var requests = new ListTag();
        session.requests.forEach((id, request) -> {
            var value = new CompoundTag();
            value.putUUID("Id", id);
            value.putUUID("Player", request.player());
            value.putLong("Revision", request.revision());
            value.putString("Intent", request.intent().name());
            value.putString("Argument", request.argument());
            requests.add(value);
        });
        tag.put("Requests", requests);
        tag.put("Replacements", strings(session.replacements));
        return tag;
    }

    static ConversationSession load(CompoundTag tag) {
        var identity = new ConversationSession.Identity(uuid(tag, "Id"), resource(tag, "Conversation"), integer(tag, "DefinitionRevision"),
                text(tag, "Fingerprint"), resource(tag, "Campaign"), uuid(tag, "CampaignInstance"), text(tag, "CampaignFingerprint"),
                optionalResource(tag, "Quest"), text(tag, "CharacterRole"), uuid(tag, "Character"), resource(tag, "Dimension"),
                uuid(tag, "Owner"), optionalId(tag, "Party"), text(tag, "Title"), text(tag, "Topic"), number(tag, "CreatedAt"));
        require(identity.definitionRevision() > 0, "invalid conversation definition revision");
        var session = new ConversationSession(identity, identity.owner(), optionalId(tag, "QuestRun"), text(tag, "Node"),
                new ConversationSession.Speaker(identity.characterRole(), identity.character(), identity.dimension(), ""));
        session.controller = optionalId(tag, "Controller");
        session.actingPlayer = uuid(tag, "ActingPlayer");
        session.revision = number(tag, "Revision");
        session.visit = number(tag, "Visit");
        session.phase = ConversationSession.Phase.valueOf(text(tag, "Phase"));
        session.exchange = integer(tag, "Exchange");
        session.effect = integer(tag, "Effect");
        session.response = string(tag, "Response");
        session.check = string(tag, "Check");
        session.outcome = string(tag, "Outcome");
        session.diagnostic = string(tag, "Diagnostic");
        require(session.revision > 0 && session.revision < Long.MAX_VALUE && session.visit > 0 && session.visit < Long.MAX_VALUE
                && session.exchange >= 0 && session.effect >= 0, "invalid conversation cursor");
        session.consent = compound(tag, "Consent").copy();
        if (!session.consent.isEmpty()) com.jvn.villagerretaliation.campaign.CampaignQuestConsent.load(session.consent);
        if (tag.contains("Confirmation")) session.confirmation = com.jvn.villagerretaliation.campaign.CampaignConfirmation.load(compound(tag, "Confirmation"));
        session.pendingChoice = optionalId(tag, "PendingChoice");
        if (tag.contains("NestedDecision")) session.nestedDecision = ConversationNestedDecision.load(compound(tag, "NestedDecision"));
        boolean nestedPending = session.nestedDecision != null && session.nestedDecision.state() == ConversationNestedDecision.State.PENDING;
        require((session.phase == ConversationSession.Phase.CONFIRMATION) == (session.confirmation != null || nestedPending)
                && (session.confirmation == null || session.consent.isEmpty() && session.nestedDecision == null), "conversation confirmation phase has inconsistent consent");
        if (session.nestedDecision != null) {
            require(session.pendingChoice == null && session.confirmation == null
                    && session.nestedDecision.confirmation().consent().playerId().equals(session.actingPlayer)
                    && (nestedPending || session.phase == session.nestedDecision.resumePhase()), "nested confirmation belongs to another actor or execution phase");
        }

        require(session.pendingChoice == null || session.confirmation != null, "conversation choice has no pending confirmation");
        if (!session.consent.isEmpty()) {
            var consent = com.jvn.villagerretaliation.campaign.CampaignQuestConsent.load(session.consent);
            require(consent.playerId().equals(session.actingPlayer)
                    && (session.phase == ConversationSession.Phase.NODE_EFFECTS || session.phase == ConversationSession.Phase.RESPONSE_EFFECTS || nestedPending),
                    "conversation approval belongs to another actor or execution phase");
        }
        session.participants.clear();
        session.participants.addAll(players(list(tag, "Participants")));
        require(session.participants.contains(identity.owner()) && session.participants.contains(session.actingPlayer)
                && (session.controller == null || session.participants.contains(session.controller)), "conversation actor is not a recorded participant");
        var decisionParticipants = compound(tag, "DecisionParticipants");
        if (!decisionParticipants.isEmpty()) {
            session.decisionParticipants = com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants.load(decisionParticipants);
            require(session.decisionParticipants.conversation().equals(identity.id())
                    && session.decisionParticipants.campaign().equals(identity.campaign())
                    && session.decisionParticipants.instance().equals(identity.campaignInstance())
                    && session.participants.containsAll(session.decisionParticipants.participants()), "conversation participation belongs to another session");
        }
        session.speakers.clear();
        for (var raw : list(tag, "Speakers")) {
            var value = (CompoundTag) raw;
            var speaker = new ConversationSession.Speaker(text(value, "Role"), uuid(value, "Entity"), resource(value, "Dimension"), text(value, "Name"));
            require(session.speakers.putIfAbsent(speaker.role(), speaker) == null, "duplicate conversation speaker");
        }
        ConversationSpeakerHistory.load(tag, session);
        require(session.confirmation == null || session.confirmation.consent().playerId().equals(session.actingPlayer)
                && ConversationSpeakerHistory.known(session, identity.characterRole(), session.confirmation.consent().providerId()),
                "conversation confirmation belongs to another actor");
        if (!session.consent.isEmpty()) require(ConversationSpeakerHistory.known(session, identity.characterRole(),
                com.jvn.villagerretaliation.campaign.CampaignQuestConsent.load(session.consent).providerId()), "conversation approval belongs to another character");
        var lines = new LinkedHashSet<String>();
        for (var raw : list(tag, "Transcript")) {
            var value = (CompoundTag) raw;
            var line = new ConversationSession.Line(text(value, "Node"), number(value, "Visit"), text(value, "Exchange"),
                    ConversationDefinition.SpeakerKind.valueOf(text(value, "Kind")), string(value, "Role"), optionalId(value, "Speaker"),
                    string(value, "Name"), text(value, "TextKey"), text(value, "Fallback"));
            require(line.visit() > 0 && line.visit() <= session.visit && lines.add(line.visit() + "/" + line.exchange()), "duplicate or invalid conversation exchange");
            require(line.kind() == ConversationDefinition.SpeakerKind.NARRATOR ? line.speaker() == null : line.speaker() != null, "invalid saved exchange speaker");
            session.transcript.add(line);
        }
        for (var raw : list(tag, "ObjectiveEvidence")) {
            var evidence = ConversationObjectiveEvidence.load((CompoundTag) raw, session);
            require(session.objectiveEvidence.putIfAbsent(evidence.key(identity), evidence) == null, "duplicate conversation objective evidence");
        }
        var choiceVisits = new LinkedHashSet<Long>();
        for (var raw : list(tag, "Choices")) {
            var value = (CompoundTag) raw;
            var choice = new ConversationSession.Choice(uuid(value, "Request"), text(value, "Node"), number(value, "Visit"),
                    text(value, "Response"), uuid(value, "Player"), players(list(value, "Participants")), number(value, "ChosenAt"));
            require(choice.visit() > 0 && choice.visit() <= session.visit && choiceVisits.add(choice.visit())
                    && choice.participants().contains(choice.player()) && session.participants.containsAll(choice.participants()), "invalid saved conversation choice");
            session.choices.add(choice);
        }
        for (var raw : list(tag, "Checks")) {
            var value = (CompoundTag) raw;
            var check = new ConversationSession.Check(uuid(value, "Request"), text(value, "Id"), integer(value, "Die"), integer(value, "Difficulty"),
                    integer(value, "Modifier"), integer(value, "Total"), bool(value, "Success"), text(value, "Destination"), uuid(value, "Player"), number(value, "RolledAt"));
            require(session.participants.contains(check.player()) && session.checks.putIfAbsent(check.id(), check) == null, "duplicate or invalid conversation check");
        }
        for (var raw : list(tag, "Receipts")) {
            var value = (CompoundTag) raw;
            var actionTag = compound(value, "Action");
            var action = SceneContinuation.loadAction(actionTag);
            require(action.kind() != VillagerActionDefinition.Kind.NONE && SceneContinuation.saveAction(action).equals(actionTag), "unreadable conversation action receipt");
            var receipt = new ConversationSession.Receipt(text(value, "Key"), uuid(value, "Actor"), optionalId(value, "QuestRun"), action,
                    ConversationSession.ReceiptState.valueOf(text(value, "State")), result(compound(value, "Result"), action));
            require(session.participants.contains(receipt.actor()) && session.receipts.putIfAbsent(receipt.key(), receipt) == null, "duplicate or invalid conversation effect receipt");
        }
        for (var raw : list(tag, "Requests")) {
            var value = (CompoundTag) raw;
            var request = new ConversationSession.Request(uuid(value, "Player"), number(value, "Revision"),
                    ConversationSession.Intent.valueOf(text(value, "Intent")), string(value, "Argument"));
            require(request.argument().length() <= 256 && (request.intent() == ConversationSession.Intent.CHOOSE
                    || request.intent() == ConversationSession.Intent.HAND_OVER || request.intent() == ConversationSession.Intent.CONFIRM
                    || request.intent() == ConversationSession.Intent.DECLINE || request.argument().isEmpty()), "invalid saved conversation intent");
            require(request.revision() > 0 && request.revision() < session.revision && session.participants.contains(request.player())
                    && session.requests.putIfAbsent(uuid(value, "Id"), request) == null, "duplicate or invalid conversation request");
        }
        if (session.pendingChoice != null) {
            var request = session.requests.get(session.pendingChoice);
            require(request != null && request.intent() == ConversationSession.Intent.CHOOSE && request.player().equals(session.actingPlayer)
                    && request.argument().equals(session.response) && session.choices.stream().noneMatch(choice -> choice.visit() == session.visit),
                    "pending conversation choice does not match its request receipt");
        }
        session.choices.forEach(choice -> {
            var request = session.requests.get(choice.request());
            require(request != null && request.intent() == ConversationSession.Intent.CHOOSE && request.player().equals(choice.player())
                    && request.argument().equals(choice.response()), "conversation choice does not match its request receipt");
        });
        if (session.decisionParticipants != null) {
            var decision = session.decisionParticipants;
            var choice = session.choices.stream().filter(value -> value.request().equals(decision.decision())).findFirst().orElse(null);
            boolean confirmed = session.requests.values().stream().anyMatch(request -> request.intent() == ConversationSession.Intent.CONFIRM
                    && request.player().equals(decision.player()) && request.argument().equals(decision.decision().toString()));
            require(choice == null ? confirmed : choice.player().equals(decision.player()) && choice.participants().equals(decision.participants()),
                    "conversation participation has no matching decision receipt");
        }
        for (var raw : list(tag, "NestedParticipation")) {
            var evidence = ConversationParticipationEvidence.load((CompoundTag) raw);
            evidence.validate(session);
            require(session.nestedParticipation.putIfAbsent(evidence.participants().decision(), evidence) == null,
                    "duplicate nested participation record");
        }
        if (session.nestedDecision != null) {
            var nested = session.nestedDecision;
            var evidence = session.nestedParticipation.get(nested.confirmation().id());
            require((nested.state() == ConversationNestedDecision.State.APPROVED) == (evidence != null),
                    "nested approval has inconsistent participation evidence");
            require(evidence == null || evidence.effect().equals(nested.origin().effect()) && evidence.controller().equals(nested.resolver()),
                    "nested approval belongs to another participation record");
            var root = session.receipts.get(nested.origin().effect());
            require(root != null && root.state() == ConversationSession.ReceiptState.RESOLVED && root.result().suspended()
                    && root.actor().equals(session.actingPlayer), "nested confirmation has no saved parent suspension");
            if (nested.state() != ConversationNestedDecision.State.PENDING) {
                require(session.participants.contains(nested.resolver()) && session.requests.values().stream().anyMatch(request ->
                        request.player().equals(nested.resolver()) && request.argument().equals(nested.confirmation().id().toString())
                                && request.intent() == (nested.state() == ConversationNestedDecision.State.DECLINED
                                        ? ConversationSession.Intent.DECLINE : ConversationSession.Intent.CONFIRM)),
                        "nested confirmation resolution has no matching request receipt");
            }
        }
        session.checks.values().forEach(check -> {
            var request = session.requests.get(check.request());
            require(request != null && request.intent() == ConversationSession.Intent.ROLL && request.player().equals(check.player())
                    && request.argument().isEmpty(), "conversation check does not match its request receipt");
        });
        require(session.phase != ConversationSession.Phase.RESPONSE_EFFECTS || !session.response.isBlank(), "conversation effects have no selected response");
        require(session.phase != ConversationSession.Phase.CHECK && session.phase != ConversationSession.Phase.ROLLED || !session.check.isBlank(), "conversation has no selected check");
        require(session.phase != ConversationSession.Phase.ROLLED || session.checks.containsKey(session.check), "conversation roll presentation has no saved check");
        require(session.phase != ConversationSession.Phase.COMPLETE || !session.outcome.isBlank(), "completed conversation has no outcome");
        session.replacements.putAll(strings(compound(tag, "Replacements")));
        return session;
    }

    private static CompoundTag result(ActionResult result) {
        var tag = new CompoundTag();
        tag.putString("Status", result.status().name());
        tag.putString("Message", result.message());
        if (result.suspensionId() != null) tag.putUUID("Suspension", result.suspensionId());
        tag.putString("SuspensionKind", result.suspensionKind().name());
        tag.putBoolean("Retry", result.retryOnResume());
        var legacy = result.legacyResult();
        tag.putBoolean("Ran", legacy.ran());
        tag.putString("LineId", legacy.lineId());
        tag.putString("Text", legacy.text());
        tag.putBoolean("Flash", legacy.flashTracker());
        tag.put("Replacements", strings(legacy.replacements()));
        return tag;
    }

    private static ActionResult result(CompoundTag tag, VillagerActionDefinition action) {
        var legacy = new VillagerActionResult(bool(tag, "Ran"), string(tag, "LineId"), string(tag, "Text"), strings(compound(tag, "Replacements")), bool(tag, "Flash"));
        var result = new ActionResult(ActionStatus.valueOf(text(tag, "Status")), string(tag, "Message"), VillagerActionRegistry.capabilities(action),
                legacy, optionalId(tag, "Suspension"), ActionResult.SuspensionKind.valueOf(text(tag, "SuspensionKind")), bool(tag, "Retry"));
        require(!result.suspended() || result.suspensionId() != null, "suspended conversation effect has no saved dependency");
        return result;
    }

    private static ListTag players(Set<UUID> players) {
        var list = new ListTag();
        players.stream().sorted().forEach(player -> { var value = new CompoundTag(); value.putUUID("Player", player); list.add(value); });
        return list;
    }

    private static Set<UUID> players(ListTag list) {
        var players = new LinkedHashSet<UUID>();
        for (var raw : list) require(players.add(uuid((CompoundTag) raw, "Player")), "duplicate conversation participant");
        return players;
    }

    private static CompoundTag strings(Map<String, String> values) {
        var tag = new CompoundTag();
        values.forEach(tag::putString);
        return tag;
    }

    private static Map<String, String> strings(CompoundTag tag) {
        var values = new java.util.LinkedHashMap<String, String>();
        tag.getAllKeys().stream().sorted().forEach(key -> values.put(key, string(tag, key)));
        return values;
    }

    static void require(boolean value, String message) { if (!value) throw new IllegalArgumentException(message); }
    static CompoundTag compound(CompoundTag tag, String key) { require(tag.contains(key, Tag.TAG_COMPOUND), "missing conversation object " + key); return tag.getCompound(key); }
    static ListTag list(CompoundTag tag, String key) {
        require(tag.get(key) instanceof ListTag, "missing conversation list " + key);
        var list = (ListTag) tag.get(key);
        require(list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND, "invalid conversation list " + key);
        return list;
    }
    static String string(CompoundTag tag, String key) { require(tag.contains(key, Tag.TAG_STRING), "missing conversation text " + key); return tag.getString(key); }
    static String text(CompoundTag tag, String key) { var value = string(tag, key); require(!value.isBlank(), "blank conversation text " + key); return value; }
    static long number(CompoundTag tag, String key) { require(tag.contains(key, Tag.TAG_LONG), "invalid conversation number " + key); return tag.getLong(key); }
    static int integer(CompoundTag tag, String key) { require(tag.contains(key, Tag.TAG_INT), "invalid conversation integer " + key); return tag.getInt(key); }
    static boolean bool(CompoundTag tag, String key) { require(tag.contains(key, Tag.TAG_BYTE) && (tag.getByte(key) == 0 || tag.getByte(key) == 1), "invalid conversation flag " + key); return tag.getBoolean(key); }
    static UUID uuid(CompoundTag tag, String key) { require(tag.hasUUID(key), "invalid conversation identity " + key); return tag.getUUID(key); }
    static UUID optionalId(CompoundTag tag, String key) { return tag.contains(key) ? uuid(tag, key) : null; }
    static ResourceLocation resource(CompoundTag tag, String key) {
        var value = text(tag, key);
        var id = ResourceLocation.tryParse(value);
        require(value.contains(":") && id != null, "invalid conversation resource " + key);
        return id;
    }
    private static ResourceLocation optionalResource(CompoundTag tag, String key) { return tag.contains(key) ? resource(tag, key) : null; }
}
