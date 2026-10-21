package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService.DialogueResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class InteractionService {
    private static final Map<ServerPlayer, Active> ACTIVE = new WeakHashMap<>();
    private static final InteractionChoiceLedger CHOICES = new InteractionChoiceLedger();

    private InteractionService() { }

    public static Optional<InteractionMenu> open(ServerPlayer player, LivingEntity actor) {
        return open(player, actor, InteractionSessions.StartMode.NORMAL);
    }

    public static Optional<InteractionMenu> open(ServerPlayer player, LivingEntity actor, InteractionSessions.StartMode mode) {
        var registration = InteractionActors.find(actor);
        if (registration == null) return Optional.empty();
        var session = InteractionSessions.start(player, actor, new Policy(registration), mode).orElse(null);
        if (session == null) return Optional.empty();
        var active = new Active(registration, InteractionMenu.SessionRef.of(session));
        ACTIVE.put(player, active);
        boolean published = false;
        try {
            publish(new ActorInteractionContext(player.serverLevel(), player, actor, session), active);
            published = active.menu != null && current(player, active);
            return published ? Optional.of(active.menu) : Optional.empty();
        } finally {
            if (!published) end(player, active, true, false);
        }
    }

    public static boolean select(ServerPlayer player, InteractionMenu.Selection selection) {
        var active = ACTIVE.get(player);
        if (active == null || !active.reference.equals(selection.session()) || active.menu == null
                || !active.menu.id().equals(selection.menuId()) || active.menu.revision() != selection.revision()) return false;
        var context = InteractionRequests.require(player, selection.session()).orElse(null);
        if (context == null || !active.registration.active()) return false;
        if (!CHOICES.consume(player, InteractionChoiceLedger.Target.of(active.menu), selection.choiceId(), selection.token())) return false;
        active.menu = null;
        boolean completed = false;
        try {
            var dialogue = context.dialogue();
            boolean available = active.registration.adapter.content(dialogue).choices().stream()
                    .anyMatch(choice -> choice.available() && choice.id().equals(selection.choiceId()));
            boolean selected = available && active.registration.adapter.select(dialogue, selection.choiceId());
            completed = true;
            return selected;
        } finally {
            if (current(player, active)) {
                if (!completed) end(player, active, true, false);
                else if (active.menu == null) publish(context, active);
            }
        }
    }

    public static boolean activity(ServerPlayer player, InteractionMenu.SessionRef reference) {
        var active = ACTIVE.get(player);
        return active != null && active.reference.equals(reference) && active.registration.active()
                && InteractionRequests.require(player, reference).isPresent();
    }

    public static boolean close(ServerPlayer player, InteractionMenu.SessionRef reference) {
        var active = ACTIVE.get(player);
        if (active == null || !active.reference.equals(reference) || !current(player, active)) return false;
        var context = InteractionRequests.require(player, reference).orElse(null);
        if (context == null || !active.registration.adapter.leave(context)) return false;
        if (current(player, active)) InteractionSessions.endForPlayer(player, false, false);
        return true;
    }

    public static boolean refresh(DialogueRuntimeContext context) {
        var active = active(context);
        if (active == null) return false;
        publish(new ActorInteractionContext(context.level(), context.player(), context.speaker(),
                InteractionSessions.current(context.player()).orElseThrow()), active);
        return true;
    }

    static boolean deliver(DialogueRuntimeContext context, DialogueResult result, boolean forced) {
        var active = active(context);
        if (active == null) return false;
        active.text = result.text();
        active.exchanges = result.exchanges();
        publish(new ActorInteractionContext(context.level(), context.player(), context.speaker(),
                InteractionSessions.current(context.player()).orElseThrow()), active);
        if (forced && active.menu != null && active.menu.choices().stream().noneMatch(InteractionMenu.Choice::available))
            end(context.player(), active, true, !result.exchanges().isEmpty());
        return true;
    }

    static boolean finish(DialogueRuntimeContext context, List<DialogueSpeech> exchanges, String text, boolean forced) {
        var active = active(context);
        if (active == null) return false;
        active.text = text;
        active.exchanges = List.copyOf(exchanges);
        publish(new ActorInteractionContext(context.level(), context.player(), context.speaker(),
                InteractionSessions.current(context.player()).orElseThrow()), active);
        if (forced) end(context.player(), active, true, !exchanges.isEmpty());
        return true;
    }

    static void flashTracker(DialogueRuntimeContext context) {
        var active = active(context);
        if (active != null) active.registration.adapter.trackerChanged(context);
    }

    public static boolean handleRightClick(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof LivingEntity actor)) return false;
        var registration = InteractionActors.find(actor);
        if (registration == null || !registration.adapter.handles(player, actor, event.getHand(), event.getItemStack())) return false;
        event.setCanceled(true);
        event.setCancellationResult(open(player, actor).isPresent() ? InteractionResult.CONSUME : InteractionResult.FAIL);
        return true;
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        for (var player : new ArrayList<>(ACTIVE.keySet())) {
            if (player.server != event.getServer()) continue;
            var active = ACTIVE.get(player);
            if (active == null) continue;
            if (!current(player, active)) {
                end(player, active, true, false);
                continue;
            }
            var actor = player.serverLevel().getEntity(active.reference.actorId());
            if (!(actor instanceof LivingEntity living) || living.getId() != active.reference.entityId()) end(player, active, true, false);
            else InteractionSessions.tick(living);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var active = ACTIVE.get(player);
            if (active != null) end(player, active, false, false);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        for (var player : new ArrayList<>(ACTIVE.keySet())) {
            if (player.server != event.getServer()) continue;
            var active = ACTIVE.get(player);
            if (active != null) end(player, active, false, false);
        }
        InteractionSessions.clear(event.getServer());
    }

    static void unregister(InteractionActors.Registration registration) {
        for (var player : new ArrayList<>(ACTIVE.keySet())) {
            var active = ACTIVE.get(player);
            if (active != null && active.registration == registration) end(player, active, true, false);
        }
    }

    private static Active active(DialogueRuntimeContext context) {
        var active = ACTIVE.get(context.player());
        if (active == null || context.speaker() == null || !active.reference.actorId().equals(context.speaker().getUUID())) return null;
        if (!active.registration.active() || !InteractionSessions.check(context.player(), context.speaker(), active.reference.id())) {
            end(context.player(), active, true, false);
            return null;
        }
        return active;
    }

    private static boolean current(ServerPlayer player, Active active) {
        return ACTIVE.get(player) == active && InteractionSessions.current(player)
                .map(session -> session.id().equals(active.reference.id())).orElse(false);
    }

    private static void publish(ActorInteractionContext context, Active active) {
        if (!current(context.player(), active) || !active.registration.active()) return;
        boolean published = false;
        try {
            var content = active.registration.adapter.content(context.dialogue());
            UUID menuId = UUID.randomUUID();
            var target = new InteractionChoiceLedger.Target(active.reference.entityId(), active.reference.actorId(), active.reference.id(), menuId);
            var choices = CHOICES.publish(context.player(), target, content.choices());
            active.menu = new InteractionMenu(active.reference, menuId, ++active.revision, content.title(),
                    active.text == null ? content.text() : active.text,
                    active.exchanges == null ? content.exchanges() : active.exchanges, choices);
            active.registration.adapter.show(context, active.menu);
            published = true;
        } finally {
            if (!published) end(context.player(), active, true, false);
        }
    }

    private static void end(ServerPlayer player, Active active, boolean notifyClient, boolean finishExchanges) {
        if (current(player, active)) InteractionSessions.endForPlayer(player, notifyClient, finishExchanges);
        else {
            if (ACTIVE.remove(player, active)) {
                CHOICES.clear(player);
                active.registration.adapter.closed(player, active.reference, notifyClient, finishExchanges);
            }
        }
    }

    private static final class Active {
        private final InteractionActors.Registration registration;
        private final InteractionMenu.SessionRef reference;
        private InteractionMenu menu;
        private long revision;
        private String text;
        private List<DialogueSpeech> exchanges;

        private Active(InteractionActors.Registration registration, InteractionMenu.SessionRef reference) {
            this.registration = registration;
            this.reference = reference;
        }
    }

    private record Policy(InteractionActors.Registration registration) implements InteractionSessionPolicy {
        @Override public boolean canStart(ServerPlayer player, LivingEntity actor, InteractionSessions.StartMode mode) {
            return this.registration.active() && this.registration.adapter.canStart(player, actor, mode);
        }
        @Override public boolean valid(ServerPlayer player, LivingEntity actor, InteractionSession session) {
            return this.registration.active() && this.registration.adapter.valid(player, actor, session);
        }
        @Override public long idleTimeout(ServerPlayer player, LivingEntity actor, InteractionSession session) {
            return this.registration.adapter.idleTimeout(player, actor, session);
        }
        @Override public void prepare(ServerPlayer player, LivingEntity actor) { this.registration.adapter.prepare(player, actor); }
        @Override public void hold(ServerPlayer player, LivingEntity actor, InteractionSession session) { this.registration.adapter.hold(player, actor, session); }
        @Override public void tick(ServerPlayer player, LivingEntity actor, InteractionSession session) { this.registration.adapter.tick(player, actor, session); }
        @Override public void ended(ServerPlayer player, InteractionSession session, boolean notifyClient, boolean finishExchanges) {
            var active = ACTIVE.get(player);
            if (active != null && active.reference.id().equals(session.id())) {
                ACTIVE.remove(player, active);
                CHOICES.clear(player);
            }
            this.registration.adapter.closed(player, InteractionMenu.SessionRef.of(session), notifyClient, finishExchanges);
            this.registration.adapter.ended(player, session, notifyClient, finishExchanges);
        }
    }
}
