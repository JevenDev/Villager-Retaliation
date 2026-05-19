package com.jvn.villagerretaliation.village;

import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class VillageEventMemory {
    private static final long EVENT_TTL_TICKS = 20L * 60L * 10L;
    private static final int MAX_EVENTS_PER_DIMENSION = 80;
    private static final double RELEVANT_EVENT_RADIUS_SQR = 48.0D * 48.0D;
    private static final Map<ResourceKey<Level>, ArrayDeque<MemoryEvent>> EVENTS = new HashMap<>();

    private VillageEventMemory() {
    }

    public static void remember(ServerLevel level, EventTag tag, BlockPos pos, Entity source, Entity player) {
        ArrayDeque<MemoryEvent> events = EVENTS.computeIfAbsent(level.dimension(), ignored -> new ArrayDeque<>());
        events.addLast(new MemoryEvent(
                tag,
                level.getGameTime(),
                pos.immutable(),
                source == null ? null : source.getUUID(),
                player == null ? null : player.getUUID(),
                null
        ));
        prune(level);
    }

    public static void rememberGift(
            ServerLevel level,
            BlockPos pos,
            Entity villager,
            Entity player,
            String villagerName,
            String itemName,
            VillagerGiftPreferences.GiftReaction reaction,
            int reputationValue) {
        ArrayDeque<MemoryEvent> events = EVENTS.computeIfAbsent(level.dimension(), ignored -> new ArrayDeque<>());
        events.addLast(new MemoryEvent(
                giftTag(reaction),
                level.getGameTime(),
                pos.immutable(),
                villager == null ? null : villager.getUUID(),
                player == null ? null : player.getUUID(),
                new GiftMemory(villagerName, itemName, reaction, reputationValue)
        ));
        prune(level);
    }

    public static List<MemoryEvent> recentNear(ServerLevel level, BlockPos pos) {
        prune(level);
        ArrayDeque<MemoryEvent> events = EVENTS.get(level.dimension());
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        List<MemoryEvent> relevant = new ArrayList<>();
        for (MemoryEvent event : events) {
            if (event.pos().distSqr(pos) <= RELEVANT_EVENT_RADIUS_SQR) {
                relevant.add(event);
            }
        }
        return relevant;
    }

    private static void prune(ServerLevel level) {
        ArrayDeque<MemoryEvent> events = EVENTS.get(level.dimension());
        if (events == null) {
            return;
        }

        long oldestAllowed = level.getGameTime() - EVENT_TTL_TICKS;
        while (!events.isEmpty() && (events.peekFirst().gameTime() < oldestAllowed || events.size() > MAX_EVENTS_PER_DIMENSION)) {
            events.removeFirst();
        }
    }

    public static boolean hasAny(List<MemoryEvent> events, EventTag... tags) {
        EnumSet<EventTag> wanted = EnumSet.copyOf(List.of(tags));
        return events.stream().anyMatch(event -> wanted.contains(event.tag()));
    }

    public enum EventTag {
        BABY_BORN,
        IRON_GOLEM_DEFEATED_MOB,
        RAID,
        VILLAGER_DEATH,
        VILLAGER_ATTACKED,
        PLAYER_ATTACKED_VILLAGER,
        PLAYER_DEFENDED_VILLAGE,
        GOLEM_CREATED,
        GOLEM_KILLED,
        NEARBY_HOSTILE_MOB,
        REPUTATION_CHANGED,
        PLAYER_GAVE_LOVED_GIFT,
        PLAYER_GAVE_LIKED_GIFT,
        PLAYER_GAVE_NEUTRAL_GIFT,
        PLAYER_GAVE_DISLIKED_GIFT,
        PLAYER_GAVE_HATED_GIFT
    }

    private static EventTag giftTag(VillagerGiftPreferences.GiftReaction reaction) {
        return switch (reaction) {
            case LOVED -> EventTag.PLAYER_GAVE_LOVED_GIFT;
            case LIKED -> EventTag.PLAYER_GAVE_LIKED_GIFT;
            case NEUTRAL -> EventTag.PLAYER_GAVE_NEUTRAL_GIFT;
            case DISLIKED -> EventTag.PLAYER_GAVE_DISLIKED_GIFT;
            case HATED -> EventTag.PLAYER_GAVE_HATED_GIFT;
        };
    }

    public record MemoryEvent(EventTag tag, long gameTime, BlockPos pos, UUID sourceId, UUID playerId, GiftMemory gift) {
    }

    public record GiftMemory(String villagerName, String itemName, VillagerGiftPreferences.GiftReaction reaction, int reputationValue) {
    }
}
