package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.campaign.CampaignAuthority;
import com.jvn.villagerretaliation.campaign.CampaignCharacterSavedData;
import com.jvn.villagerretaliation.campaign.CampaignCharacterService;
import com.jvn.villagerretaliation.campaign.CampaignSavedData;
import com.jvn.villagerretaliation.campaign.CampaignWorldMode;
import com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload.*;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
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
        UUID active = ConversationCharacters.active(player.server, ConversationSavedData.get(player.serverLevel()), character.getUUID());
        if (active == null) return false;
        var session = ConversationSavedData.get(player.serverLevel()).session(active);
        if (session == null || !ConversationService.member(player, session) || !ConversationService.near(player, character)
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
            UUID active = ConversationCharacters.active(player.server, ConversationSavedData.get(player.serverLevel()), character.getUUID());
            if (active == null) {
                attach(player, request, null);
                return topics(player, character, request.viewId(), request.page(), "");
            }
            var data = ConversationSavedData.get(player.serverLevel());
            var session = data.session(active);
            if (session == null || !ConversationService.member(player, session)) return failure(request, "This character is already taking part in another conversation.");
            ConversationService.view(player, active);
            session = data.session(active);
            if (!session.participants.contains(player.getUUID()) && session.active()) {
                var joined = ConversationService.request(player, active, session.revision, request.requestId(), ConversationSession.Intent.JOIN, "");
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
                var data = ConversationSavedData.get(player.serverLevel());
                if (request.requestId().equals(ConversationCharacters.active(player.server, data, character.getUUID()))) attach(player, request, request.requestId());
                if (!ConversationService.isCharacterReserved(character)) VillagerConversationService.start(player, character);
                return topics(player, character, request.viewId(), request.page(), result.diagnostic());
            }
            attach(player, request, result.session().identity().id());
            return session(player, character, request.viewId(), result.session().identity().id(), 0, 0, result.diagnostic());
        }
        if (request.action() == CampaignConversationRequestPayload.Action.HISTORY && viewer.session == null) {
            var archived = ConversationSavedData.get(player.serverLevel()).session(request.session());
            if (archived == null || archived.active() || !ConversationService.member(player, archived)
                    || archived.speakers.values().stream().noneMatch(speaker -> speaker.entity().equals(character.getUUID())))
                return failure(request, "This conversation history is unavailable.");
            attach(player, request, archived.identity.id());
            viewer = viewers.get(player.getUUID());
        }
        if (!java.util.Objects.equals(viewer.session, request.session())) return failure(request, "This screen belongs to a different conversation.");
        var saved = ConversationSavedData.get(player.serverLevel()).session(request.session());
        if (saved == null || saved.speakers.values().stream().noneMatch(speaker -> speaker.entity().equals(character.getUUID()))) {
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
        var topics = new ArrayList<Topic>();
        var binding = CampaignCharacterSavedData.get(player.serverLevel()).forEntity(character.getUUID());
        if (binding != null && CampaignWorldModeSavedData.get(player.serverLevel()).mode() == CampaignWorldMode.CAMPAIGN) {
            var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, character);
            var content = CampaignResources.current(player.server).campaigns().get(binding.campaign());
            boolean compatible = content != null && CampaignSavedData.get(player.serverLevel()).compatibility(content.definition()).isBlank();
            if (compatible) for (var definition : QuestContentCatalogs.current(player.server).conversations().values().stream()
                    .filter(value -> binding.campaign().equals(value.campaign())
                            && CampaignCharacterService.matchesBoundCharacter(player.server, value.campaign(), value.character(), character.getUUID()))
                    .sorted(java.util.Comparator.comparing(value -> value.id().toString())).toList()) {
                for (var topic : definition.topics().values()) if (ConversationService.conditions(context, topic.conditions())) {
                    topics.add(new Topic(definition.id().toString(), topic.id(), label(ConversationService.text(player, topic.labelKey())),
                            CampaignAuthority.canControl(player), topicGroup(context, definition.quest())));
                }
            }
        }
        var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, character);
        for (var definition : QuestContentCatalogs.current(player.server).conversations().values().stream()
                .filter(value -> value.campaign() == null && ConversationBinding.matches(player, value, character))
                .sorted(java.util.Comparator.comparing(value -> value.id().toString())).toList()) {
            for (var topic : definition.topics().values()) if (ConversationService.conditions(context, topic.conditions()))
                topics.add(new Topic(definition.id().toString(), topic.id(), label(ConversationService.text(player, topic.labelKey())),
                        true, topicGroup(context, definition.quest())));
        }
        for (var saved : ConversationSavedData.get(player.serverLevel()).finishedSessions(character.getUUID()))
            if (ConversationService.member(player, saved)) topics.add(new Topic(saved.identity.conversation().toString(),
                    saved.identity.id().toString(), label(saved.identity.title()), true, TopicGroup.HISTORY));
        topics.sort(java.util.Comparator.comparing(Topic::group));
        int pages = pages(topics.size());
        page = Math.min(page, pages - 1);
        return new CampaignConversationViewPayload(view, character.getId(), character.getUUID(), page, pages, slice(topics, page), null, text(issue));
    }

    private static CampaignConversationViewPayload session(ServerPlayer player, Villager character, UUID view, UUID id, int page, int textPage, String issue) {
        var result = ConversationService.view(player, id);
        if (result.session() == null) return new CampaignConversationViewPayload(view, character.getId(), character.getUUID(), 0, 1, List.of(), null, text(result.diagnostic()));
        var snapshot = result.session();
        var saved = ConversationSavedData.get(player.serverLevel()).session(id);
        var primary = ConversationCharacters.primary(player, saved);
        boolean valid = ConversationSavedData.get(player.serverLevel()).issue(id).isBlank() && primary != null
                && ConversationService.near(player, primary)
                && (saved.identity.campaign() == null || CampaignCharacterService.matchesBoundCharacter(player.server,
                        saved.identity.campaign(), saved.identity.characterRole(), primary.getUUID()));
        ConversationDefinition definition = null;
        try { definition = ConversationService.definition(player.server, saved); }
        catch (ConversationService.Unavailable unavailable) { valid = false; }
        boolean active = saved.active();
        boolean authority = ConversationService.canControl(player, saved) && ConversationService.member(player, saved);
        boolean control = valid && active && authority && player.getUUID().equals(snapshot.controller()) && !VillagerConversationService.isConversing(primary);
        var controller = snapshot.controller() == null ? null : player.server.getPlayerList().getPlayer(snapshot.controller());
        boolean takeover = valid && active && authority && !control && (controller == null || !ConversationService.member(controller, saved)
                || !ConversationService.canControl(controller, saved) || !ConversationService.near(controller, primary));
        var handover = new ArrayList<Member>();
        if (control) PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).ifPresent(party -> {
            for (UUID member : party.playerIds()) {
                var target = player.server.getPlayerList().getPlayer(member);
                if (target != null && !target.getUUID().equals(player.getUUID()) && ConversationService.member(target, saved)
                        && ConversationService.canControl(target, saved) && ConversationService.near(target, primary)) handover.add(new Member(member, label(target.getName().getString())));
            }
        });
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
        var prices = ConversationTransactionPresentation.view(player, saved, definition, visible);
        if (prices.blocked()) { control = false; handover.clear(); }
        visible = visible.stream().map(response -> new Response(response.id(), response.label(),
                response.available() && !prices.unavailableResponses().contains(response.id()),
                prices.unavailableResponses().contains(response.id()) ? label(prices.diagnostic().isBlank()
                        ? "This transaction is not currently available." : prices.diagnostic()) : response.reason())).toList();
        if (!prices.diagnostic().isBlank()) issue = issue.isBlank() ? prices.diagnostic() : issue + "\n" + prices.diagnostic();
        var session = new Session(id, snapshot.revision(), label(snapshot.identity().title()), snapshot.phase(), line, control, takeover,
                controller == null ? "" : label(controller.getName().getString()), handover, visible, check, label(outcome),
                snapshot.confirmation() == null ? null : snapshot.confirmation().id(), prices.transactions(),
                ConversationRecovery.canControl(player, saved), ConversationRecovery.canMigrate(player, saved), history(player, saved));
        return new CampaignConversationViewPayload(view, character.getId(), character.getUUID(), page, pages, List.of(), session,
                text(issue.isBlank() ? result.diagnostic() : issue));
    }

    private static TopicGroup topicGroup(com.jvn.villagerretaliation.dialogue.DialogueContext context, ResourceLocation quest) {
        if (quest == null) return TopicGroup.GENERAL;
        if (com.jvn.villagerretaliation.quest.VillagerQuestService.matchesState(context, quest, java.util.Set.of("ready")))
            return TopicGroup.REPORTING_BACK;
        if (com.jvn.villagerretaliation.quest.VillagerQuestService.matchesState(context, quest, java.util.Set.of("active")))
            return TopicGroup.CURRENT_OBJECTIVES;
        if (com.jvn.villagerretaliation.quest.VillagerQuestService.matchesState(context, quest, java.util.Set.of("completed")))
            return TopicGroup.FOLLOW_UPS;
        return TopicGroup.NEW_BUSINESS;
    }

    private static History history(ServerPlayer player, ConversationSession saved) {
        var viewer = viewers(player.server).get(player.getUUID());
        if (viewer == null || viewer.historyPage < 0) return null;
        var entries = new ArrayList<String>();
        var visits = new java.util.TreeMap<Long, List<String>>();
        for (var line : saved.transcript) visits.computeIfAbsent(line.visit(), ignored -> new ArrayList<>())
                .addAll(splitText((line.name().isBlank() ? "" : line.name() + ": ") + ConversationText.text(player, line)));
        for (var choice : saved.choices) visits.computeIfAbsent(choice.visit(), ignored -> new ArrayList<>())
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
        var player = viewer.server.getPlayerList().getPlayer(id);
        if (player != null) return label(player.getGameProfile().getName());
        var cache = viewer.server.getProfileCache();
        return label(cache == null ? id.toString() : cache.get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString()));
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
