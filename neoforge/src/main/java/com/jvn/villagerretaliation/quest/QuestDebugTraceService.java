package com.jvn.villagerretaliation.quest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class QuestDebugTraceService {
    private static final int MAX_EVENTS_PER_PLAYER = 80;
    private static final Map<UUID, ArrayDeque<Event>> EVENTS = new java.util.HashMap<>();
    private static final Set<UUID> ENABLED_PLAYERS = new HashSet<>();

    private QuestDebugTraceService() {
    }

    public static int capacity() {
        return MAX_EVENTS_PER_PLAYER;
    }

    public static boolean isEnabled(ServerPlayer player) {
        return player != null && ENABLED_PLAYERS.contains(player.getUUID());
    }

    public static boolean setEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) {
            return false;
        }
        if (enabled) {
            ENABLED_PLAYERS.add(player.getUUID());
        } else {
            ENABLED_PLAYERS.remove(player.getUUID());
        }
        return true;
    }

    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        EVENTS.remove(player.getUUID());
    }

    public static List<Event> recent(ServerPlayer player, int limit) {
        if (player == null) {
            return List.of();
        }
        ArrayDeque<Event> events = EVENTS.get(player.getUUID());
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        int max = limit <= 0 ? MAX_EVENTS_PER_PLAYER : Math.min(limit, MAX_EVENTS_PER_PLAYER);
        List<Event> copy = new ArrayList<>(events);
        return copy.subList(Math.max(0, copy.size() - max), copy.size());
    }

    public static Map<EventType, Integer> counts(ServerPlayer player) {
        Map<EventType, Integer> counts = new EnumMap<>(EventType.class);
        for (Event event : recent(player, MAX_EVENTS_PER_PLAYER)) {
            counts.merge(event.type(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    public static void recordIfEnabled(ServerPlayer player, EventType type, ResourceLocation questId, String message) {
        if (isEnabled(player)) {
            record(player, type, questId, message);
        }
    }

    public static void record(ServerPlayer player, EventType type, ResourceLocation questId, String message) {
        if (player == null) {
            return;
        }
        ArrayDeque<Event> events = EVENTS.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        events.addLast(new Event(
                player.serverLevel().getGameTime(),
                type == null ? EventType.NOTE : type,
                questId,
                message == null ? "" : message));
        while (events.size() > MAX_EVENTS_PER_PLAYER) {
            events.removeFirst();
        }
    }

    public enum EventType {
        NOTE,
        PROVIDER,
        CONDITION,
        DIALOGUE_SLOT,
        RESPONSE,
        ACTION,
        OBJECTIVE_EVENT,
        OBJECTIVE_PROGRESS,
        STAGE_TRANSITION,
        TRIGGER,
        REWARD,
        TRACKER_SYNC
    }

    public record Event(
            long gameTime,
            EventType type,
            ResourceLocation questId,
            String message
    ) {
        public Event {
            type = type == null ? EventType.NOTE : type;
            message = message == null ? "" : message;
        }

        public String line() {
            String quest = questId == null ? "" : " quest=" + questId;
            return "trace t=" + gameTime
                    + " type=" + type.name().toLowerCase(java.util.Locale.ROOT)
                    + quest
                    + " " + message;
        }
    }
}
