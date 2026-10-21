package com.jvn.villagerretaliation.interaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class InteractionSessions {
    private static final Map<MinecraftServer, State> SERVERS = new WeakHashMap<>();

    private InteractionSessions() { }

    public static Optional<InteractionSession> start(ServerPlayer player, LivingEntity actor,
            InteractionSessionPolicy policy, StartMode mode) {
        Objects.requireNonNull(policy);
        Objects.requireNonNull(mode);
        if (!participantsAvailable(player, actor) || !policy.canStart(player, actor, mode)
                || mode == StartMode.NORMAL && NarrativeActorReservations.conversation(actor)) return Optional.empty();
        policy.prepare(player, actor);
        var state = state(player.server);
        UUID owner = state.playersByActor.get(actor.getUUID());
        if (owner != null && !owner.equals(player.getUUID())) return Optional.empty();
        endForPlayer(player, false, false);
        var session = new InteractionSession(player.getUUID(), actor.getUUID(), actor.getId(),
                player.level().dimension(), actor.blockPosition(), player.serverLevel().getGameTime(), mode != StartMode.NORMAL);
        state.byPlayer.put(player.getUUID(), new Entry(session, policy));
        state.playersByActor.put(actor.getUUID(), player.getUUID());
        policy.hold(player, actor, session);
        return Optional.of(session);
    }

    public static Optional<InteractionSession> current(ServerPlayer player) {
        var entry = state(player.server).byPlayer.get(player.getUUID());
        return entry == null || !entry.session.active() ? Optional.empty() : Optional.of(entry.session);
    }

    public static UUID sessionId(ServerPlayer player, LivingEntity actor) {
        var session = current(player).orElse(null);
        return session != null && session.actorId().equals(actor.getUUID()) ? session.id() : null;
    }

    public static boolean validate(ServerPlayer player, LivingEntity actor) {
        var entry = state(player.server).byPlayer.get(player.getUUID());
        if (entry == null || !entry.session.active() || !entry.session.actorId().equals(actor.getUUID())) return false;
        if (!valid(player, actor, entry)) {
            end(player, entry, true, false);
            return false;
        }
        entry.session.touch(player.serverLevel().getGameTime());
        entry.policy.hold(player, actor, entry.session);
        return true;
    }

    public static boolean validate(ServerPlayer player, LivingEntity actor, UUID sessionId) {
        return sessionId != null && sessionId.equals(sessionId(player, actor)) && validate(player, actor);
    }

    public static void recordActivity(ServerPlayer player, int entityId) {
        var session = current(player).orElse(null);
        if (session == null || session.entityId() != entityId) return;
        var entity = player.serverLevel().getEntity(entityId);
        if (entity instanceof LivingEntity actor && session.actorId().equals(actor.getUUID())) validate(player, actor);
    }

    public static boolean isForced(ServerPlayer player, LivingEntity actor) {
        var session = current(player).orElse(null);
        return session != null && session.forced() && session.actorId().equals(actor.getUUID());
    }

    public static boolean isConversing(LivingEntity actor) {
        return actor.level() instanceof ServerLevel level && state(level.getServer()).playersByActor.containsKey(actor.getUUID());
    }

    public static boolean isConversing(ServerPlayer player) { return current(player).isPresent(); }

    public static void endForPlayer(ServerPlayer player, boolean notifyClient, boolean finishExchanges) {
        var entry = state(player.server).byPlayer.get(player.getUUID());
        if (entry != null) end(player, entry, notifyClient, finishExchanges);
    }

    public static void endForActor(LivingEntity actor, boolean notifyClient) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        var state = state(level.getServer());
        UUID playerId = state.playersByActor.get(actor.getUUID());
        if (playerId == null) return;
        var entry = state.byPlayer.get(playerId);
        var found = level.getPlayerByUUID(playerId);
        if (found instanceof ServerPlayer player && entry != null) end(player, entry, notifyClient, false);
        else retire(state, playerId, actor.getUUID(), entry);
    }

    public static void endForEntityLeaving(Entity entity, boolean notifyClient) {
        if (entity instanceof ServerPlayer player) endForPlayer(player, notifyClient, false);
        else if (entity instanceof LivingEntity actor) endForActor(actor, notifyClient);
    }

    public static void tick(LivingEntity actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        var state = state(level.getServer());
        UUID playerId = state.playersByActor.get(actor.getUUID());
        if (playerId == null) return;
        var entry = state.byPlayer.get(playerId);
        var found = level.getPlayerByUUID(playerId);
        if (!(found instanceof ServerPlayer player) || entry == null) {
            retire(state, playerId, actor.getUUID(), entry);
            return;
        }
        if (!valid(player, actor, entry)) {
            end(player, entry, true, false);
            return;
        }
        entry.policy.hold(player, actor, entry.session);
        entry.policy.tick(player, actor, entry.session);
    }

    public static void clear(MinecraftServer server) {
        var state = SERVERS.remove(server);
        if (state != null) state.byPlayer.values().forEach(entry -> entry.session.deactivate());
    }

    public static void clear() {
        SERVERS.values().forEach(state -> state.byPlayer.values().forEach(entry -> entry.session.deactivate()));
        SERVERS.clear();
    }

    private static boolean participantsAvailable(ServerPlayer player, LivingEntity actor) {
        return player.isAlive() && !player.isSpectator() && actor.isAlive()
                && !actor.isRemoved() && actor.level() == player.serverLevel();
    }

    private static boolean valid(ServerPlayer player, LivingEntity actor, Entry entry) {
        var session = entry.session;
        if (!session.active() || !participantsAvailable(player, actor) || !session.playerId().equals(player.getUUID())
                || !session.actorId().equals(actor.getUUID()) || session.entityId() != actor.getId()
                || player.level().dimension() != session.dimension()) return false;
        long idle = player.serverLevel().getGameTime() - session.lastInteractionGameTime();
        return entry.policy.valid(player, actor, session) && idle <= entry.policy.idleTimeout(player, actor, session);
    }

    private static void end(ServerPlayer player, Entry entry, boolean notifyClient, boolean finishExchanges) {
        var state = state(player.server);
        retire(state, player.getUUID(), entry.session.actorId(), entry);
        entry.policy.ended(player, entry.session, notifyClient, finishExchanges);
    }

    private static void retire(State state, UUID playerId, UUID actorId, Entry entry) {
        if (entry != null) {
            entry.session.deactivate();
            state.byPlayer.remove(playerId, entry);
        } else state.byPlayer.remove(playerId);
        state.playersByActor.remove(actorId, playerId);
    }

    private static State state(MinecraftServer server) { return SERVERS.computeIfAbsent(server, ignored -> new State()); }

    public enum StartMode { NORMAL, FORCED, AUTHORIZED_FORCED }

    private record Entry(InteractionSession session, InteractionSessionPolicy policy) { }

    private static final class State {
        private final Map<UUID, Entry> byPlayer = new HashMap<>();
        private final Map<UUID, UUID> playersByActor = new HashMap<>();
    }
}
