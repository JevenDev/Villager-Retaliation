package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ConversationPresentationService {
    private static final ResourceLocation PRESENTATION_REQUEST = com.jvn.villagerretaliation.VillagerRetaliation.id("campaign_conversation_view");
    private static final Map<MinecraftServer, Map<UUID, Viewer>> VIEWERS = new HashMap<>();

    private ConversationPresentationService() { }

    public static boolean tryOpen(ServerPlayer player, Villager character) {
        UUID active = ConversationQueries.active(player, character);
        if (active == null) return false;
        var session = ConversationQueries.saved(player, active);
        if (session == null || !ConversationService.near(player, character)
                || VillagerConversationService.isConversing(character)) return false;
        VillagerConversationService.endForPlayer(player, false);
        VillagerInteractionScreenOpener.openCampaign(player, character);
        return true;
    }

    public static void handle(ServerPlayer player, CampaignConversationRequestPayload request) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Conversation presentation requires the server thread");
        boolean presentation = request.action() == CampaignConversationRequestPayload.Action.TOPICS
                || request.action() == CampaignConversationRequestPayload.Action.VIEW
                || request.action() == CampaignConversationRequestPayload.Action.HISTORY;
        if (request.action() != CampaignConversationRequestPayload.Action.CLOSE
                && !ServerboundRequestLimiter.tryAcquire(player, presentation ? PRESENTATION_REQUEST : CampaignConversationRequestPayload.TYPE.id(), 2L)) return;
        var result = respond(player, request);
        if (result != null) PacketDistributor.sendToPlayer(player, result);
    }

    public static CampaignConversationViewPayload respond(ServerPlayer player, CampaignConversationRequestPayload request) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Conversation presentation requires the server thread");
        var viewers = viewers(player.server);
        var viewer = viewers.get(player.getUUID());
        if (request.action() == CampaignConversationRequestPayload.Action.CLOSE) {
            if (viewer != null && viewer.view.equals(request.viewId()) && viewer.character.equals(request.character())) detach(player, viewer);
            return null;
        }
        var entity = player.serverLevel().getEntity(request.entityId());
        if (!(entity instanceof Villager character) || !character.getUUID().equals(request.character()) || !ConversationService.near(player, character)) {
            return failure(request, "Return to this character to continue the conversation.");
        }
        if (VillagerConversationService.isForced(player, character)) return failure(request, "Finish the current interruption first.");
        if (request.action() != CampaignConversationRequestPayload.Action.TOPICS
                && (viewer == null || !viewer.view.equals(request.viewId()) || !viewer.character.equals(request.character()))) {
            return failure(request, "Reopen the conversation before choosing again.");
        }
        if (request.action() == CampaignConversationRequestPayload.Action.TOPICS) {
            UUID active = ConversationQueries.active(player, character);
            if (active == null) {
                attach(player, request, null);
                return topics(player, character, request.viewId(), request.page(), "");
            }
            var session = ConversationQueries.saved(player, active);
            if (session == null) return failure(request, "This character is already taking part in another conversation.");
            var current = ConversationService.view(player, active);
            if (current.session() != null) session = current.session();
            if (!session.participants().contains(player.getUUID()) && session.active()) {
                var joined = ConversationService.request(player, active, session.revision(), request.requestId(), ConversationSession.Intent.JOIN, "");
                if (joined.session() == null || !joined.session().participants().contains(player.getUUID())) return failure(request, joined.diagnostic());
            }
            attach(player, request, active);
            return session(player, character, request.viewId(), active, request.page(), request.textPage(), "");
        }
        if (request.action() == CampaignConversationRequestPayload.Action.START) {
            var definition = ConversationResources.conversation(player.server, ResourceLocation.tryParse(request.conversation())).orElse(null);
            if (definition == null || !ConversationBinding.matches(player, definition, character)) {
                return failure(request, "This topic does not belong to this character.");
            }
            VillagerConversationService.endForPlayer(player, false);
            var result = ConversationService.start(player, definition.id(), request.argument(), request.requestId(), character);
            if (result.session() == null) {
                if (request.requestId().equals(ConversationQueries.active(player, character))) attach(player, request, request.requestId());
                if (!com.jvn.villagerretaliation.interaction.NarrativeActorReservations.conversation(character)) VillagerConversationService.start(player, character);
                return topics(player, character, request.viewId(), request.page(), result.diagnostic());
            }
            attach(player, request, result.session().identity().id());
            return session(player, character, request.viewId(), result.session().identity().id(), 0, 0, result.diagnostic());
        }
        if (request.action() == CampaignConversationRequestPayload.Action.HISTORY && viewer.session == null) {
            var archived = ConversationQueries.saved(player, request.session());
            if (archived == null || archived.active()
                    || archived.speakers().values().stream().noneMatch(speaker -> speaker.entity().equals(character.getUUID())))
                return failure(request, "This conversation history is unavailable.");
            attach(player, request, archived.identity().id());
            viewer = viewers.get(player.getUUID());
        }
        if (!java.util.Objects.equals(viewer.session, request.session())) return failure(request, "This screen belongs to a different conversation.");
        if (!ConversationQueries.referencesCharacter(player, request.session(), character.getUUID())) {
            return failure(request, "The saved conversation character is unavailable.");
        }
        viewer.touched = player.serverLevel().getGameTime();
        viewer.page = request.page();
        viewer.textPage = request.textPage();
        String issue = "";
        viewer.historyPage = request.action() == CampaignConversationRequestPayload.Action.HISTORY ? request.page() : -1;
        if (request.action() != CampaignConversationRequestPayload.Action.VIEW && request.action() != CampaignConversationRequestPayload.Action.HISTORY) {
            var result = ConversationService.request(player, request.session(), request.revision(), request.requestId(),
                    ConversationSession.Intent.valueOf(request.action().name()), request.argument());
            issue = result.diagnostic();
            viewer.page = 0;
            viewer.textPage = 0;
        }
        return session(player, character, request.viewId(), request.session(), viewer.page, viewer.textPage, issue);
    }

    private static CampaignConversationViewPayload topics(ServerPlayer player, Villager character, UUID view, int page, String issue) {
        var topics = ConversationQueries.topics(player, character).stream().filter(ConversationQueries.Topic::eligible)
                .map(topic -> new Topic(topic.conversation().toString(), topic.id(), label(topic.label()), topic.available(),
                        TopicGroup.valueOf(topic.group().name()))).toList();
        int pages = pages(topics.size());
        page = Math.min(page, pages - 1);
        return new CampaignConversationViewPayload(view, character.getId(), character.getUUID(), page, pages, slice(topics, page), null, text(issue));
    }

    private static CampaignConversationViewPayload session(ServerPlayer player, Villager character, UUID view, UUID id, int page, int textPage, String issue) {
        var result = ConversationQueries.view(player, id);
        if (result.snapshot() == null) return new CampaignConversationViewPayload(view, character.getId(), character.getUUID(), 0, 1, List.of(), null, text(result.diagnostic()));
        var snapshot = result.snapshot();
        var definition = result.definition();
        boolean valid = result.control().valid();
        boolean control = result.control().canControl();
        var handover = new ArrayList<Member>(result.control().handover().stream()
                .map(member -> new Member(member.id(), label(member.name()))).toList());
        handover.sort(java.util.Comparator.comparing(Member::name).thenComparing(member -> member.id().toString()));
        Line line = null;
        if (snapshot.confirmation() != null) {
            String key = "confirmation/" + snapshot.confirmation().id();
            var viewer = viewers(player.server).get(player.getUUID());
            if (viewer != null && viewer.view.equals(view) && !viewer.lineKey.equals(key)) {
                viewer.lineKey = key;
                viewer.textPage = 0;
                textPage = 0;
            }
            var parts = splitText(snapshot.confirmation().text());
            int part = Math.min(textPage, parts.size() - 1);
            line = new Line(key, null, -1, "", parts.get(part), part, parts.size());
        } else if (!snapshot.transcript().isEmpty()) {
            var entry = snapshot.transcript().getLast();
            var speaker = entry.speaker() == null ? null : player.serverLevel().getEntity(entry.speaker());
            String key = id + "/" + entry.visit() + "/" + entry.exchange();
            var viewer = viewers(player.server).get(player.getUUID());
            if (viewer != null && viewer.view.equals(view) && !viewer.lineKey.equals(key)) {
                viewer.lineKey = key;
                viewer.textPage = 0;
                textPage = 0;
            }
            var parts = splitText(ConversationText.text(player, entry));
            int part = Math.min(textPage, parts.size() - 1);
            var presentation = valid && snapshot.phase() == ConversationSession.Phase.EXCHANGES
                    ? linePresentation(definition, entry) : ConversationLinePresentation.EMPTY;
            line = new Line(entry.visit() + "/" + entry.exchange(), entry.speaker(), speaker == null ? -1 : speaker.getId(),
                    label(entry.name()), parts.get(part), part, parts.size(), presentation.camera(),
                    presentation.voice(com.jvn.villagerretaliation.util.VillagerLocale.locale(player)));
        }
        Check check = null;
        if (snapshot.phase() == ConversationSession.Phase.CHECK || snapshot.phase() == ConversationSession.Phase.ROLLED) {
            var rolled = snapshot.checks().get(snapshot.check());
            if (rolled != null) check = new Check(rolled.id(), rolled.difficulty(), true, rolled.die(), rolled.success(),
                    rolled.modifier(), rolled.total(), rolled.social() == null ? null : rolled.social().skill(),
                    rolled.social() == null ? 0 : rolled.social().rewardExperience(), snapshot.socialAwards().contains(rolled.id()),
                    rolled.social() == null ? "" : playerName(player, rolled.player()),
                    rolled.modifiers().stream().map(modifier -> new Modifier(modifier.id(), label(modifier.label()), modifier.amount(), modifier.applied())).toList());
            else if (definition != null && definition.checks().containsKey(snapshot.check())) {
                var authored = definition.checks().get(snapshot.check());
                check = new Check(snapshot.check(), authored.difficulty(), false, 0, false, 0, 0,
                        authored.social() == null ? null : authored.social().skill(), authored.social() == null ? 0 : authored.social().experience(), false);
            }
        }
        String outcome = snapshot.outcome();
        if (definition != null && definition.outcomes().containsKey(outcome)) outcome = ConversationService.text(player, definition.outcomes().get(outcome).labelKey());
        var responses = result.responses().stream().map(response -> new Response(response.id(), label(response.label()), response.available(), label(response.reason()))).toList();
        int pages = pages(responses.size());
        page = Math.min(page, pages - 1);
        var visible = slice(responses, page);
        var prices = ConversationTransactionQueries.view(player, snapshot, result.actingPlayer(), definition,
                visible.stream().map(Response::id).toList(), CampaignConversationViewPayload.MAX_TRANSACTIONS);
        if (prices.blocked()) { control = false; handover.clear(); }
        visible = visible.stream().map(response -> new Response(response.id(), response.label(),
                response.available() && !prices.unavailableResponses().contains(response.id()),
                prices.unavailableResponses().contains(response.id()) ? label(prices.diagnostic().isBlank()
                        ? "This transaction is not currently available." : prices.diagnostic()) : response.reason())).toList();
        if (!prices.diagnostic().isBlank()) issue = issue.isBlank() ? prices.diagnostic() : issue + "\n" + prices.diagnostic();
        var session = new Session(id, snapshot.revision(), label(snapshot.identity().title()), snapshot.phase(), line, control, result.control().canTakeControl(),
                label(result.control().controller()), handover, visible, check, label(outcome),
                snapshot.confirmation() == null ? null : snapshot.confirmation().id(), prices.transactions().stream().map(transaction -> new Transaction(transaction.quest(), transaction.id(), transaction.response(),
                        transaction.check(), label(transaction.title()), transaction.buyer(), label(transaction.buyerName()), transaction.item(),
                        transaction.basePrice(), transaction.bargainPrice(), transaction.price(), TransactionState.valueOf(transaction.state().name()),
                        transaction.possible(), label(transaction.diagnostic()))).toList(),
                result.control().canCancel(), result.control().canMigrate(), history(player, snapshot));
        return new CampaignConversationViewPayload(view, character.getId(), character.getUUID(), page, pages, List.of(), session,
                text(issue.isBlank() ? result.diagnostic() : issue));
    }

    private static History history(ServerPlayer player, ConversationSession.Snapshot saved) {
        var viewer = viewers(player.server).get(player.getUUID());
        if (viewer == null || viewer.historyPage < 0) return null;
        var entries = new ArrayList<String>();
        var visits = new java.util.TreeMap<Long, List<String>>();
        for (var line : saved.transcript()) visits.computeIfAbsent(line.visit(), ignored -> new ArrayList<>())
                .addAll(splitText((line.name().isBlank() ? "" : line.name() + ": ") + ConversationText.text(player, line)));
        for (var choice : saved.choices()) visits.computeIfAbsent(choice.visit(), ignored -> new ArrayList<>())
                .addAll(splitText(playerName(player, choice.player()) + ": " + ConversationText.resolve(player,
                        choice.labelKey(), choice.fallback(), choice.replacements(), !choice.labelKey().isEmpty())));
        visits.values().forEach(entries::addAll);
        int pages = Math.max(1, (entries.size() + 7) / 8);
        int page = Math.min(viewer.historyPage, pages - 1);
        int start = page * 8;
        return new History(page, pages, entries.subList(start, Math.min(entries.size(), start + 8)));
    }

    static ConversationLinePresentation linePresentation(ConversationDefinition definition, ConversationSession.Line entry) {
        var node = definition == null ? null : definition.nodes().get(entry.node());
        if (node == null) return ConversationLinePresentation.EMPTY;
        return node.exchanges().stream().filter(exchange -> exchange.id().equals(entry.exchange())
                        && exchange.speaker().kind() == entry.kind() && exchange.speaker().role().equals(entry.role())
                        && exchange.textKey().equals(entry.textKey())).findFirst()
                .map(ConversationDefinition.Exchange::presentation).orElse(ConversationLinePresentation.EMPTY);
    }

    static String playerName(ServerPlayer viewer, UUID id) {
        return label(ConversationQueries.playerName(viewer, id));
    }

    private static void attach(ServerPlayer player, CampaignConversationRequestPayload request, UUID session) {
        var prior = viewers(player.server).get(player.getUUID());
        if (prior != null && !java.util.Objects.equals(prior.session, session)) detach(player, prior);
        viewers(player.server).put(player.getUUID(), new Viewer(request.viewId(), request.entityId(), request.character(), session,
                request.page(), player.serverLevel().getGameTime()));
    }

    private static void detach(ServerPlayer player, Viewer viewer) {
        viewers(player.server).remove(player.getUUID(), viewer);
        if (viewer.session == null) return;
        ConversationService.releasePresentationControl(player, viewer.session);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.overworld().getGameTime() % 20L != 0) return;
        for (var entry : List.copyOf(viewers(server).entrySet())) {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            var viewer = entry.getValue();
            if (player == null) { viewers(server).remove(entry.getKey()); continue; }
            var entity = player.serverLevel().getEntity(viewer.entity);
            if (!(entity instanceof Villager character) || !character.getUUID().equals(viewer.character) || !ConversationService.near(player, character)
                    || player.serverLevel().getGameTime() - viewer.touched > 100L) { detach(player, viewer); continue; }
            if (viewer.session == null || VillagerConversationService.isForced(player, character)) continue;
            var value = session(player, character, viewer.view, viewer.session, viewer.page, viewer.textPage, "");
            if (!value.equals(viewer.last)) { PacketDistributor.sendToPlayer(player, value); viewer.last = value; }
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var viewer = viewers(player.server).get(player.getUUID());
            if (viewer != null) detach(player, viewer);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) { VIEWERS.remove(event.getServer()); }
    private static Map<UUID, Viewer> viewers(MinecraftServer server) { return VIEWERS.computeIfAbsent(server, ignored -> new HashMap<>()); }
    private static int pages(int count) { return Math.max(1, (count + CampaignConversationViewPayload.PAGE_SIZE - 1) / CampaignConversationViewPayload.PAGE_SIZE); }
    private static <T> List<T> slice(List<T> values, int page) {
        int start = page * CampaignConversationViewPayload.PAGE_SIZE;
        return values.subList(start, Math.min(values.size(), start + CampaignConversationViewPayload.PAGE_SIZE));
    }
    private static List<String> splitText(String value) {
        if (value.isEmpty()) return List.of("");
        var parts = new ArrayList<String>();
        for (int start = 0; start < value.length();) {
            int end = Math.min(value.length(), start + CampaignConversationViewPayload.MAX_TEXT_LENGTH);
            if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) end--;
            parts.add(value.substring(start, end));
            start = end;
        }
        return parts;
    }

    private static String label(String value) { return limit(value, CampaignConversationViewPayload.MAX_LABEL_LENGTH); }
    private static String text(String value) { return limit(value, CampaignConversationViewPayload.MAX_TEXT_LENGTH); }
    private static String limit(String value, int length) {
        if (value.length() <= length) return value;
        int end = Character.isHighSurrogate(value.charAt(length - 1)) ? length - 1 : length;
        return value.substring(0, end);
    }
    private static CampaignConversationViewPayload failure(CampaignConversationRequestPayload request, String diagnostic) {
        return new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), null, text(diagnostic));
    }

    private static final class Viewer {
        final UUID view;
        final int entity;
        final UUID character;
        final UUID session;
        int page;
        int textPage;
        int historyPage = -1;
        String lineKey = "";
        long touched;
        CampaignConversationViewPayload last;
        Viewer(UUID view, int entity, UUID character, UUID session, int page, long touched) {
            this.view = view; this.entity = entity; this.character = character; this.session = session; this.page = page; this.touched = touched;
        }
    }
}
