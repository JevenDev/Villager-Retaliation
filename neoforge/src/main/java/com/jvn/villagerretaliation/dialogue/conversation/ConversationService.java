package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.campaign.CampaignAuthority;
import com.jvn.villagerretaliation.campaign.CampaignCharacterService;
import com.jvn.villagerretaliation.campaign.CampaignSavedData;
import com.jvn.villagerretaliation.campaign.CampaignWorldMode;
import com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ConversationService {
    private ConversationService() { }

    public static Result start(ServerPlayer player, ResourceLocation conversation, String topicId, UUID request) {
        if (!player.server.isSameThread()) return new Result(null, List.of(), "Conversation requests require the server thread.");
        var data = ConversationSavedData.get(player.serverLevel());
        try {
            require(request != null, "A conversation request identity is required.");
            require(data.issue(request).isBlank(), data.issue(request));
            var existing = data.session(request);
            if (existing != null) {
                require(existing.identity.conversation().equals(conversation) && existing.identity.topic().equals(topicId) && member(player, existing), "Conversation request belongs to another session.");
                return view(player, request);
            }
            require(CampaignAuthority.canControl(player), CampaignAuthority.controlIssue(player));
            var definition = ConversationResources.conversation(player.server, conversation).orElse(null);
            require(definition != null, "Conversation content is unavailable. Restore its datapack.");
            require(CampaignWorldModeSavedData.get(player.serverLevel()).mode() == CampaignWorldMode.CAMPAIGN, "This conversation requires Campaign Mode.");
            var content = CampaignResources.current(player.server).campaigns().get(definition.campaign());
            require(content != null, "Campaign content is unavailable. Restore its datapack.");
            String compatibility = CampaignSavedData.get(player.serverLevel()).compatibility(content.definition());
            require(compatibility.isBlank(), compatibility);
            var character = CampaignCharacterService.resolve(player.server, definition.campaign(), definition.character());
            require(character.available(), character.diagnostic());
            require(near(player, character.entity()), "Return to this character to begin the conversation.");
            UUID active = ConversationCharacters.active(player.server, data, character.entity().getUUID());
            if (active != null) {
                var session = data.session(active);
                require(session != null && session.identity.conversation().equals(conversation) && member(player, session),
                        "This character is already taking part in another conversation.");
                return view(player, active);
            }
            require(!com.jvn.villagerretaliation.interaction.VillagerConversationService.isConversing(character.entity()),
                    "This character is already taking part in an ordinary conversation.");
            var topic = definition.topics().get(topicId);
            require(topic != null, "Unknown conversation topic.");
            var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, character.entity());
            require(conditions(context, topic.conditions()), "This topic is not currently available.");
            var instance = CampaignSavedData.get(player.serverLevel()).instances().get(definition.campaign());
            require(instance != null, "Campaign instance is unavailable.");
            UUID party = PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).map(value -> value.id()).orElse(null);
            var identity = new ConversationSession.Identity(request, definition.id(), definition.revision(), definition.fingerprint(),
                    definition.campaign(), instance.instanceId(), instance.fingerprint(), definition.quest(), definition.character(),
                    character.entity().getUUID(), character.entity().level().dimension().location(), player.getUUID(), party,
                    text(player, definition.titleKey()), topicId, player.serverLevel().getGameTime());
            var session = new ConversationSession(identity, player.getUUID(), questRun(player, definition.quest()), topic.start(),
                    new ConversationSession.Speaker(definition.character(), character.entity().getUUID(), identity.dimension(), character.entity().getName().getString()));
            data.write(player.serverLevel(), 0, session);
            ConversationRuntime.drive(player, request);
            return view(player, request);
        } catch (Unavailable | ConversationSavedData.Conflict | IOException exception) {
            var result = request == null ? new Result(null, List.of(), "") : view(player, request);
            return new Result(result.session(), result.responses(), exception.getMessage());
        }
    }

    public static Result request(ServerPlayer player, UUID sessionId, long revision, UUID requestId, ConversationSession.Intent intent, String argument) {
        if (!player.server.isSameThread()) return new Result(null, List.of(), "Conversation requests require the server thread.");
        var data = ConversationSavedData.get(player.serverLevel());
        try {
            require(data.diagnostic().isBlank(), data.diagnostic());
            var session = data.session(sessionId);
            require(session != null && member(player, session), "This conversation belongs to another player or party.");
            require(data.issue(sessionId).isBlank(), data.issue(sessionId));
            require(requestId != null && intent != null && argument != null && argument.length() <= 256, "Conversation intent is incomplete or too large.");
            require(intent == ConversationSession.Intent.CHOOSE || intent == ConversationSession.Intent.HAND_OVER || intent == ConversationSession.Intent.CONFIRM
                    || intent == ConversationSession.Intent.DECLINE || argument.isEmpty(), "This conversation intent does not accept an argument.");
            var request = new ConversationSession.Request(player.getUUID(), revision, intent, argument);
            var prior = session.requests.get(requestId);
            if (prior != null) {
                require(prior.equals(request), "Conversation request identity was already used for another intent.");
                return view(player, sessionId);
            }
            if (intent != ConversationSession.Intent.RELEASE_CONTROL) session = ConversationCharacters.reconcile(player.server, data, session);
            require(session.revision == revision, "The conversation changed. Refresh before choosing again.");
            require(session.active(), "This conversation has already reached its outcome.");
            if (intent == ConversationSession.Intent.RELEASE_CONTROL) {
                require(player.getUUID().equals(session.controller), "Only the current controller can release this conversation.");
                session.controller = null;
                session.requests.put(requestId, request);
                session.diagnostic = "";
                save(player, data, session);
                return view(player, sessionId);
            }
            var definition = definition(player.server, session);
            var character = character(player, session);
            require(near(player, character), "Return to the conversation to continue.");
            if (intent == ConversationSession.Intent.JOIN) {
                session.participants.add(player.getUUID());
            } else if (intent == ConversationSession.Intent.TAKE_CONTROL || intent == ConversationSession.Intent.HAND_OVER) {
                changeController(player, session, intent, argument);
            } else {
                require(!com.jvn.villagerretaliation.interaction.VillagerConversationService.isConversing(character), "Wait for the current interruption to finish.");
                require(player.getUUID().equals(session.controller) && CampaignAuthority.canControl(player), "An authorized controller must advance this conversation.");
                var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, character);
                switch (intent) {
                    case ADVANCE -> advance(player, session, definition);
                    case CHOOSE -> {
                        require(session.phase == ConversationSession.Phase.RESPONSES, "The conversation is not waiting for a response.");
                        var response = definition.nodes().get(session.node).responses().get(argument);
                        require(response != null && conditions(context, response.conditions()), "This response is no longer available.");
                        session.response = response.id();
                        session.actingPlayer = player.getUUID();
                        session.questRun = questRun(player, definition.quest());
                        if (!ConversationConfirmations.prepare(player, session, response.effects(), requestId)) {
                            selectResponse(player, session, requestId, response.id());
                        }
                    }
                    case CONFIRM -> {
                        String issue = ConversationConfirmations.confirm(player, session, definition, argument);
                        if (!issue.isBlank()) {
                            session.requests.put(requestId, request);
                            session.diagnostic = issue;
                            save(player, data, session);
                            var result = view(player, sessionId);
                            return new Result(result.session(), result.responses(), issue);
                        }
                    }
                    case DECLINE -> ConversationConfirmations.decline(player, session, argument);
                    case ROLL -> {
                        require(session.phase == ConversationSession.Phase.CHECK, "The conversation is not waiting for a check.");
                        var check = definition.checks().get(session.check);
                        require(check != null, "The authored check is unavailable.");
                        if (!session.checks.containsKey(check.id())) {
                            var social = ConversationSocialProgression.capture(player, data, check);
                            session.checks.put(check.id(), ConversationSession.Check.roll(requestId, check, player.getRandom().nextInt(20) + 1,
                                    player.getUUID(), player.serverLevel().getGameTime(), social));
                        }
                        session.phase = ConversationSession.Phase.ROLLED;
                    }
                    case RESUME -> require(session.phase == ConversationSession.Phase.NODE_EFFECTS || session.phase == ConversationSession.Phase.RESPONSE_EFFECTS,
                            "The conversation has no suspended effects.");
                    default -> throw new Unavailable("Unsupported conversation intent.");
                }
            }
            session.requests.put(requestId, request);
            session.diagnostic = "";
            save(player, data, session);
            if (intent == ConversationSession.Intent.ROLL) ConversationSocialProgression.deliverOne(player.server, data, sessionId);
            if (intent != ConversationSession.Intent.JOIN && intent != ConversationSession.Intent.HAND_OVER && intent != ConversationSession.Intent.DECLINE) ConversationRuntime.drive(player, sessionId);
            return view(player, sessionId);
        } catch (Unavailable | ConversationSavedData.Conflict | IOException exception) {
            var result = view(player, sessionId);
            return new Result(result.session(), result.responses(), exception.getMessage());
        }
    }

    public static Result view(ServerPlayer player, UUID sessionId) {
        var data = ConversationSavedData.get(player.serverLevel());
        if (!data.diagnostic().isBlank()) return new Result(null, List.of(), data.diagnostic());
        var session = data.session(sessionId);
        if (session == null || !member(player, session)) return new Result(null, List.of(), "This conversation belongs to another player or party.");
        if (!session.active()) return new Result(session.snapshot(), List.of(), data.issue(sessionId).isBlank() ? data.socialIssue(sessionId) : data.issue(sessionId));
        String issue = data.issue(sessionId);
        var responses = new ArrayList<Response>();
        try {
            require(issue.isBlank(), issue);
            session = ConversationCharacters.reconcile(player.server, data, session);
            var definition = definition(player.server, session);
            var character = character(player, session);
            require(near(player, character), "Return to the conversation to continue.");
            if (session.phase == ConversationSession.Phase.RESPONSES) {
                var controller = player.getUUID().equals(session.controller) ? player : session.controller == null ? null : player.server.getPlayerList().getPlayer(session.controller);
                boolean authorized = controller != null && member(controller, session) && CampaignAuthority.canControl(controller) && near(controller, character);
                var context = authorized ? VillagerInteractionService.createDialogueContext(controller.serverLevel(), controller, character) : null;
                for (var response : definition.nodes().get(session.node).responses().values()) {
                    responses.add(new Response(response.id(), text(player, response.labelKey()), authorized && conditions(context, response.conditions())));
                }
            }
            issue = session.controller == null ? "The conversation is paused until an authorized member takes control." : session.diagnostic;
        } catch (Unavailable | ConversationSavedData.Conflict | IOException exception) { issue = exception.getMessage(); }
        return new Result(session.snapshot(), List.copyOf(responses), issue.isBlank() ? data.socialIssue(sessionId) : issue);
    }

    static void selectResponse(ServerPlayer player, ConversationSession session, UUID request, String response) {
        session.response = response;
        session.actingPlayer = player.getUUID();
        session.questRun = questRun(player, session.identity.quest());
        captureDecisionParticipants(player, session, request);
        session.choices.add(new ConversationSession.Choice(request, session.node, session.visit, response, player.getUUID(),
                session.decisionParticipants.participants(), player.serverLevel().getGameTime()));
        session.phase = ConversationSession.Phase.RESPONSE_EFFECTS;
        session.effect = 0;
    }

    static void advance(ServerPlayer player, ConversationSession session, ConversationDefinition definition) {
        var node = definition.nodes().get(session.node);
        if (session.phase == ConversationSession.Phase.ROLLED) {
            enter(session, session.checks.get(session.check).destination());
        } else {
            require(session.phase == ConversationSession.Phase.EXCHANGES, "Choose a response or resolve the pending check first.");
            ConversationObjectiveEvidence.capture(player, session);
            if (session.exchange + 1 < node.exchanges().size()) {
                session.exchange++;
                ConversationRuntime.appendLine(player, session, node.exchanges().get(session.exchange));
            } else finishExchanges(session, node);
        }
    }

    static void finishExchanges(ConversationSession session, ConversationDefinition.Node node) {
        if (!node.responses().isEmpty()) session.phase = ConversationSession.Phase.RESPONSES;
        else if (!node.next().isBlank()) enter(session, node.next());
        else {
            session.outcome = node.outcome();
            session.phase = ConversationSession.Phase.COMPLETE;
        }
    }

    static void enter(ConversationSession session, String node) {
        session.node = node;
        session.visit++;
        session.exchange = 0;
        session.effect = 0;
        session.response = "";
        session.check = "";
        session.phase = ConversationSession.Phase.NODE_EFFECTS;
    }

    private static void changeController(ServerPlayer player, ConversationSession session, ConversationSession.Intent intent, String argument) {
        require(CampaignAuthority.canControl(player), CampaignAuthority.controlIssue(player));
        ServerPlayer next = player;
        if (intent == ConversationSession.Intent.HAND_OVER) {
            require(player.getUUID().equals(session.controller), "Only the current controller can hand over this conversation.");
            UUID target;
            try { target = UUID.fromString(argument); } catch (IllegalArgumentException exception) { throw new Unavailable("Choose a valid player for the handover."); }
            next = player.server.getPlayerList().getPlayer(target);
            require(next != null && member(next, session) && CampaignAuthority.canControl(next) && near(next, character(player, session)),
                    "The new controller must be an authorized member present at the conversation.");
        } else if (session.controller != null && !session.controller.equals(player.getUUID())) {
            var current = session.controller == null ? null : player.server.getPlayerList().getPlayer(session.controller);
            require(current == null || !member(current, session) || !CampaignAuthority.canControl(current) || !near(current, character(player, session)),
                    "The current controller must hand over the conversation explicitly.");
        }
        if (session.confirmation != null && !next.getUUID().equals(session.actingPlayer)
                && (session.pendingChoice != null || !ConversationConfirmations.continuesDecision(session))) {
            ConversationConfirmations.discard(session);
            session.actingPlayer = next.getUUID();
            session.questRun = questRun(next, session.identity.quest());
        }
        session.controller = next.getUUID();
        session.participants.add(next.getUUID());
    }

    static ConversationDefinition definition(MinecraftServer server, ConversationSession session) {
        require(CampaignWorldModeSavedData.get(server.overworld()).mode() == CampaignWorldMode.CAMPAIGN, "This conversation requires Campaign Mode.");
        var definition = ConversationResources.conversation(server, session.identity.conversation()).orElse(null);
        require(definition != null, "Conversation content is missing. Its saved history is retained.");
        require(definition.revision() == session.identity.definitionRevision() && definition.fingerprint().equals(session.identity.fingerprint()),
                "Conversation content changed. Restore its saved revision before continuing.");
        var content = CampaignResources.current(server).campaigns().get(session.identity.campaign());
        require(content != null, "Campaign content is missing. Its saved conversation is retained.");
        var campaigns = CampaignSavedData.get(server.overworld());
        String issue = campaigns.compatibility(content.definition());
        require(issue.isBlank(), issue);
        var instance = campaigns.instances().get(session.identity.campaign());
        require(instance != null && instance.instanceId().equals(session.identity.campaignInstance())
                && instance.fingerprint().equals(session.identity.campaignFingerprint()), "Restore the original campaign instance before continuing this conversation.");
        require(definition.nodes().containsKey(session.node), "Saved conversation node is unavailable.");
        return definition;
    }

    static Villager character(ServerPlayer player, ConversationSession session) {
        var resolved = CampaignCharacterService.resolve(player.server, session.identity.campaign(), session.identity.characterRole());
        require(resolved.available(), resolved.diagnostic());
        var speaker = session.speakers.get(session.identity.characterRole());
        require(speaker != null && resolved.entity().getUUID().equals(speaker.entity()), "The conversation is waiting for its repaired character binding to be saved.");
        return resolved.entity();
    }

    static boolean member(ServerPlayer player, ConversationSession session) {
        if (session.identity.party() == null) return session.identity.owner().equals(player.getUUID());
        return PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).filter(party -> party.id().equals(session.identity.party())).isPresent();
    }

    static boolean near(ServerPlayer player, Villager character) {
        return player.isAlive() && player.serverLevel() == character.level() && player.distanceToSqr(character) <= 256.0
                && VillagerInteractionService.shouldStayConversable(player, character);
    }

    static UUID questRun(ServerPlayer player, ResourceLocation quest) {
        return questRun(player.server, player.getUUID(), quest);
    }

    static UUID questRun(MinecraftServer server, UUID player, ResourceLocation quest) {
        var progress = quest == null ? null : VillagerQuestSavedData.get(server.overworld()).get(player, quest);
        return progress == null ? null : progress.questRunId();
    }

    static boolean conditions(DialogueContext context, List<DialogueCondition> conditions) {
        return conditions.stream().allMatch(condition -> condition.unavailableReason(context).isBlank() && condition.matches(context));
    }

    static String text(ServerPlayer player, String key) {
        return QuestContentCatalogs.current(player.server).localization().plainText(player.clientInformation().language(), key).orElse(key);
    }

    static void captureDecisionParticipants(ServerPlayer player, ConversationSession session, UUID decision) {
        session.decisionParticipants = new com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants(
                session.identity.id(), decision, session.identity.campaign(), session.identity.campaignInstance(),
                player.getUUID(), presentParticipants(player, session));
    }

    static Set<UUID> presentParticipants(ServerPlayer controller, ConversationSession session) {
        var participants = new java.util.LinkedHashSet<UUID>();
        participants.add(controller.getUUID());
        var character = character(controller, session);
        for (UUID id : session.participants) {
            var player = controller.server.getPlayerList().getPlayer(id);
            if (player != null && member(player, session) && near(player, character)) participants.add(id);
        }
        return Set.copyOf(participants);
    }

    static void save(ServerPlayer player, ConversationSavedData data, ConversationSession session) throws IOException {
        long expected = session.revision;
        session.revision++;
        data.write(player.serverLevel(), expected, session);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) pauseController(player.server, player.getUUID(), "Controller disconnected.");
    }

    private static void pauseController(MinecraftServer server, UUID player, String reason) {
        var data = ConversationSavedData.get(server.overworld());
        for (UUID id : data.controlledBy(player)) pause(server, data, id, player, reason);
    }

    private static void pause(MinecraftServer server, ConversationSavedData data, UUID id, UUID controller, String reason) {
        if (data.pausePendingController(id, controller, reason) || !data.issue(id).isBlank()) return;
        var session = data.session(id);
        if (session == null || !session.active() || !controller.equals(session.controller)) return;
        session.controller = null;
        session.diagnostic = reason;
        long revision = session.revision++;
        try { data.write(server.overworld(), revision, session); }
        catch (IOException exception) { com.mojang.logging.LogUtils.getLogger().warn("Conversation {} is waiting to save its paused controller", id, exception); }
    }

    public static void releasePresentationControl(ServerPlayer player, UUID id) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Conversation ownership requires the server thread");
        pause(player.server, ConversationSavedData.get(player.serverLevel()), id, player.getUUID(), "Controller left the conversation.");
    }

    public static String characterRepairIssue(MinecraftServer server, com.jvn.villagerretaliation.campaign.CampaignCharacterBinding candidate) {
        var data = ConversationSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return data.diagnostic();
        var characters = com.jvn.villagerretaliation.campaign.CampaignCharacterSavedData.get(server.overworld());
        if (characters.bindings().containsKey(candidate.key())) return "";
        for (var session : data.activeSessions()) {
            var speaker = session.speakers.get(candidate.role());
            if (session.identity.campaign().equals(candidate.campaign()) && session.identity.campaignInstance().equals(candidate.instance())
                    && speaker != null && !speaker.entity().equals(candidate.entityId()))
                return "Restore the original character " + speaker.entity() + " for conversation " + session.identity.id() + " before reassigning its missing binding.";
        }
        return "";
    }

    public static boolean isCharacterReserved(Villager character) {
        return character.level() instanceof net.minecraft.server.level.ServerLevel level
                && ConversationCharacters.active(level.getServer(), ConversationSavedData.get(level), character.getUUID()) != null;
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.overworld().getGameTime() % 20L != 0
                || CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN) return;
        maintain(server);
    }

    static void maintain(MinecraftServer server) {
        var data = ConversationSavedData.get(server.overworld());
        data.retryPending(server.overworld());
        ConversationSocialProgression.retry(server, data);
        for (UUID id : data.maintenanceBatch()) {
            var session = data.session(id);
            if (session.controller == null || !data.issue(id).isBlank()) continue;
            var player = server.getPlayerList().getPlayer(session.controller);
            if (player == null || !member(player, session) || !CampaignAuthority.canControl(player)) {
                pause(server, data, id, session.controller, "Controller is unavailable or no longer has permission.");
                continue;
            }
            if (session.phase != ConversationSession.Phase.NODE_EFFECTS && session.phase != ConversationSession.Phase.RESPONSE_EFFECTS
                    && session.nestedDecision == null) continue;
            try { ConversationRuntime.drive(player, id, false); }
            catch (Unavailable exception) {
                session = data.session(id);
                if (session.diagnostic.equals(exception.getMessage()) || !data.issue(id).isBlank()) continue;
                session.diagnostic = exception.getMessage();
                try { save(player, data, session); }
                catch (IOException failure) { com.mojang.logging.LogUtils.getLogger().warn("Conversation {} is waiting to save its recovery state", id, failure); }
            } catch (IOException exception) {
                com.mojang.logging.LogUtils.getLogger().warn("Conversation {} is waiting for world storage", id, exception);
            }
        }
    }

    static void require(boolean value, String message) { if (!value) throw new Unavailable(message); }
    static final class Unavailable extends RuntimeException { Unavailable(String message) { super(message); } }
    public record Response(String id, String label, boolean available) { }
    public record Result(ConversationSession.Snapshot session, List<Response> responses, String diagnostic) { }
}
