package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload.Action;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import java.util.UUID;

public final class CampaignConversationScreenController {
    private final Host host;
    private final UUID view = UUID.randomUUID();
    private final CampaignDieAnimation dieAnimation = new CampaignDieAnimation();
    private UUID character;
    private CampaignConversationViewPayload state;
    private long lastPoll = -1L;
    private long pendingUntil;
    private boolean pending;
    private String displayed = "";
    private String displayedLine = "";

    public CampaignConversationScreenController(Host host) { this.host = host; }

    public void tick() {
        if (dieAnimation.tick(host.now())) refreshPresentation();
        if (character == null) character = host.character();
        if (character == null || host.now() < pendingUntil || lastPoll >= 0L && host.now() - lastPoll < 1000L) return;
        request(session() == null ? Action.TOPICS : Action.VIEW, "", "", state == null ? 0 : state.page(), false);
    }

    public boolean accept(CampaignConversationViewPayload payload) {
        if (!view.equals(payload.viewId()) || character == null || !character.equals(payload.character()) || payload.entityId() != host.entityId()) return false;
        if (session() != null && payload.session() != null && session().id().equals(payload.session().id()) && payload.session().revision() < session().revision()) return false;
        if (session() != null && payload.session() == null && !payload.diagnostic().isBlank()) {
            var prior = session();
            var unavailable = new CampaignConversationViewPayload.Session(prior.id(), prior.revision(), prior.title(), prior.phase(), prior.line(),
                    false, false, prior.controller(), java.util.List.of(), prior.responses(), prior.check(), prior.outcome(), prior.confirmation());
            payload = new CampaignConversationViewPayload(payload.viewId(), payload.entityId(), payload.character(), state.page(), state.pageCount(),
                    java.util.List.of(), unavailable, payload.diagnostic());
        }
        state = payload;
        pending = false;
        pendingUntil = 0L;
        dieAnimation.accept(session(), host.now(), host.animateChecks());
        refreshPresentation();
        return true;
    }

    private void refreshPresentation() {
        String text = presentationText();
        String line = session() == null || session().line() == null ? "" : session().id() + "/" + session().line().key();
        boolean changed = !text.equals(displayed) || !line.equals(displayedLine);
        displayedLine = line;
        displayed = text;
        host.updated(session() != null, changed && !text.isBlank() ? text : null);
    }

    public float dieProgress() { return dieAnimation.progress(host.now()); }
    public boolean dieAnimating() { return dieAnimation.playing(); }
    public void skipDieAnimation() { if (dieAnimation.skip()) refreshPresentation(); }

    public CampaignConversationViewPayload.Session session() { return state == null ? null : state.session(); }

    public void addTopics() {
        if (state == null || session() != null) return;
        for (var topic : state.topics()) host.option(topic.label(), () -> request(Action.START, topic.conversation(), topic.id(), 0, true), pending || !topic.available());
        addPages(Action.TOPICS);
    }

    public void addSessionOptions() {
        var session = session();
        if (session == null) return;
        if (pending) host.option(host.translate("conversation.waiting"), () -> { }, true);
        else {
            switch (session.phase()) {
                case EXCHANGES, ROLLED -> { if (session.canControl() && !dieAnimation.playing()) intent("conversation.continue", Action.ADVANCE, ""); }
                case CHECK -> {
                    if (session.canControl() && session.check() != null) host.option(host.translate(session.check().resolved() ? "conversation.saved_roll" : "conversation.roll",
                            session.check().difficulty()), () -> request(Action.ROLL, "", "", 0, true), false);
                }
                case RESPONSES -> {
                    for (var response : session.responses()) host.option(response.label(), () -> request(Action.CHOOSE, "", response.id(), 0, true),
                            !session.canControl() || !response.available());
                    addPages(Action.VIEW);
                }
                case NODE_EFFECTS, RESPONSE_EFFECTS -> { if (session.canControl()) intent("conversation.retry", Action.RESUME, ""); }
                case CONFIRMATION -> {
                    if (session.canControl() && session.confirmation() != null) {
                        if (!host.confirmationTextRead()) {
                            host.option(host.translate("conversation.more_consequences"), host::readConfirmationText, false);
                        } else if (session.line() != null && session.line().part() + 1 == session.line().parts()) {
                            intent("conversation.confirm", Action.CONFIRM, session.confirmation().toString());
                        }
                        intent("conversation.decline", Action.DECLINE, session.confirmation().toString());
                    }
                }
                case COMPLETE -> { }
            }
            if (dieAnimation.playing()) host.option(host.translate("conversation.skip_roll"), this::skipDieAnimation, false);
            if (session.canTakeControl()) intent("conversation.take_control", Action.TAKE_CONTROL, "");
            if (!session.canControl() && !session.controller().isBlank()) host.option(host.translate("conversation.listening", session.controller()), () -> { }, true);
            for (var member : session.handover()) host.option(host.translate("conversation.handover", member.name()),
                    () -> request(Action.HAND_OVER, "", member.id().toString(), 0, true), false);
        }
        if (session.line() != null) {
            var line = session.line();
            if (line.part() > 0) host.option(host.translate("conversation.previous_text"), () -> textPage(line.part() - 1), pending);
            if (line.part() + 1 < line.parts()) host.option(host.translate("conversation.more_text"), () -> textPage(line.part() + 1), pending);
        }
        host.option(host.translate(session.phase() == com.jvn.villagerretaliation.dialogue.conversation.ConversationSession.Phase.COMPLETE
                ? "conversation.done" : "conversation.leave"), host::close, false);
    }

    private void intent(String label, Action action, String argument) {
        host.option(host.translate(label), () -> request(action, "", argument, 0, true), false);
    }

    private void addPages(Action action) {
        if (state.page() > 0) host.option(host.translate("conversation.previous"), () -> request(action, "", "", state.page() - 1, false), pending);
        if (state.page() + 1 < state.pageCount()) host.option(host.translate("conversation.next"), () -> request(action, "", "", state.page() + 1, false), pending);
    }

    private void textPage(int part) {
        request(Action.VIEW, "", "", state.page(), false, part);
    }

    private void request(Action action, String conversation, String argument, int page, boolean wait) {
        int part = action == Action.VIEW && session() != null && session().line() != null ? session().line().part() : 0;
        request(action, conversation, argument, page, wait, part);
    }

    private void request(Action action, String conversation, String argument, int page, boolean wait, int textPage) {
        var session = session();
        lastPoll = host.now();
        if (wait) { pending = true; pendingUntil = host.now() + 1500L; }
        host.send(new CampaignConversationRequestPayload(view, UUID.randomUUID(), host.entityId(), character, action,
                session == null ? null : session.id(), session == null ? 0 : session.revision(), conversation, argument, page, textPage));
        if (wait) host.updated(session != null, null);
    }

    public void close() {
        if (character != null) request(Action.CLOSE, "", "", 0, false);
    }

    private String presentationText() {
        if (state == null) return "";
        var session = session();
        var text = new StringBuilder();
        if (session != null) {
            if (session.line() != null && session.check() == null) text.append(session.line().text());
            if (session.check() != null) {
                var check = session.check();
                append(text, dieAnimation.playing() ? host.translate("conversation.rolling", check.difficulty())
                        : check.resolved() ? host.translate("conversation.result", check.die(), check.difficulty(), host.translate(check.success() ? "conversation.success" : "conversation.failure"))
                        : host.translate("conversation.difficulty", check.difficulty()));
            }
            if (!session.outcome().isBlank()) append(text, session.outcome());
        }
        if (!state.diagnostic().isBlank()) append(text, state.diagnostic());
        return text.toString();
    }

    private static void append(StringBuilder text, String line) {
        if (!text.isEmpty()) text.append("\n\n");
        text.append(line);
    }

    public interface Host {
        int entityId();
        UUID character();
        long now();
        default boolean animateChecks() { return true; }
        default boolean confirmationTextRead() { return true; }
        default void readConfirmationText() { }
        String translate(String key, Object... arguments);
        void send(CampaignConversationRequestPayload payload);
        void option(String label, Runnable action, boolean locked);
        void updated(boolean session, String text);
        void close();
    }
}
