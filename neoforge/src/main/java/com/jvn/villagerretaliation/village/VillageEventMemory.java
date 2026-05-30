package com.jvn.villagerretaliation.village;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.event.VillagerEventTriggerService;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

public final class VillageEventMemory {
    private static final long EVENT_TTL_TICKS = 20L * 60L * 10L;
    private static final long EVENT_PRUNE_INTERVAL_TICKS = 20L;
    private static final long WEATHER_EVENT_DEDUPE_TICKS = 200L;
    private static final long NOISY_EVENT_DEDUPE_TICKS = 40L;
    private static final long RECENT_QUERY_CACHE_TICKS = 20L;
    private static final int MAX_EVENTS_PER_DIMENSION = 80;
    private static final double RELEVANT_EVENT_RADIUS_SQR = 48.0D * 48.0D;
    private static final Map<ResourceKey<Level>, ArrayDeque<MemoryEvent>> EVENTS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> NEXT_PRUNE_TICKS = new HashMap<>();
    private static final Map<RecentQueryKey, CachedRecentEvents> RECENT_QUERY_CACHE = new HashMap<>();

    private VillageEventMemory() {
    }

    public static void remember(ServerLevel level, EventTag tag, BlockPos pos, Entity source, Entity player) {
        remember(level, new MemoryEvent(
                tag,
                level.getGameTime(),
                pos.immutable(),
                source == null ? null : source.getUUID(),
                player == null ? null : player.getUUID(),
                null,
                null,
                null,
                null,
                null
        ));
    }

    public static void remember(ServerLevel level, ResourceLocation tagId, BlockPos pos, Entity source, Entity player) {
        if (tagId == null) {
            return;
        }
        remember(level, new MemoryEvent(
                legacyTag(tagId).orElse(null),
                tagId,
                level.getGameTime(),
                pos.immutable(),
                source == null ? null : source.getUUID(),
                player == null ? null : player.getUUID(),
                null,
                null,
                null,
                null,
                null
        ));
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
        remember(level, new MemoryEvent(
                giftTag(reaction),
                level.getGameTime(),
                pos.immutable(),
                villager == null ? null : villager.getUUID(),
                player == null ? null : player.getUUID(),
                new GiftMemory(villagerName, itemName, reaction, reputationValue),
                null,
                null,
                null,
                null
        ));
    }

    public static void rememberCuredVillager(
            ServerLevel level,
            BlockPos pos,
            Entity villager,
            UUID playerId,
            String villagerName) {
        remember(level, new MemoryEvent(
                EventTag.PLAYER_CURED_VILLAGER,
                level.getGameTime(),
                pos.immutable(),
                villager == null ? null : villager.getUUID(),
                playerId,
                null,
                null,
                null,
                new CuredVillagerMemory(villagerName),
                null
        ));
    }

    public static void rememberContainerTheft(
            ServerLevel level,
            BlockPos pos,
            Entity villager,
            Entity player,
            String villagerName,
            String itemName,
            String itemId,
            int itemCount,
            String containerName,
            String lootTable) {
        remember(level, new MemoryEvent(
                EventTag.PLAYER_CONTAINER_THEFT,
                level.getGameTime(),
                pos.immutable(),
                villager == null ? null : villager.getUUID(),
                player == null ? null : player.getUUID(),
                null,
                new ContainerTheftMemory(villagerName, itemName, itemId, itemCount, containerName, lootTable),
                null,
                null,
                null
        ));
    }

    public static void rememberPlayerKilledVillager(
            ServerLevel level,
            BlockPos pos,
            Entity witness,
            Entity player,
            String killedVillagerName) {
        remember(level, new MemoryEvent(
                EventTag.PLAYER_KILLED_VILLAGER,
                level.getGameTime(),
                pos.immutable(),
                witness == null ? null : witness.getUUID(),
                player == null ? null : player.getUUID(),
                null,
                null,
                null,
                null,
                new KilledVillagerMemory(killedVillagerName)
        ));
    }

    public static void rememberRetaliation(
            ServerLevel level,
            BlockPos pos,
            Entity villager,
            LivingEntity target,
            String villagerName) {
        if (target == null) {
            return;
        }
        remember(level, new MemoryEvent(
                EventTag.VILLAGER_RETALIATION_STARTED,
                level.getGameTime(),
                pos.immutable(),
                villager == null ? null : villager.getUUID(),
                target instanceof net.minecraft.world.entity.player.Player player ? player.getUUID() : null,
                null,
                null,
                new RetaliationMemory(
                        villagerName,
                        target.getDisplayName().getString(),
                        BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()) == null
                                ? ""
                                : BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString()
                ),
                null,
                null
        ));
    }

    public static List<MemoryEvent> recentNear(ServerLevel level, BlockPos pos) {
        return cachedRecentEvents(
                level,
                RecentQueryKey.near(level, pos),
                () -> recentNearUncached(level, pos)
        );
    }

    public static List<MemoryEvent> recentForVillage(ServerLevel level, Villager villager) {
        if (villager == null || !villager.isAlive()) {
            return List.of();
        }
        return VillageMembership.resolve(level, villager)
                .map(area -> recentForArea(level, villager.blockPosition(), area))
                .orElseGet(() -> recentNear(level, villager.blockPosition()));
    }

    private static List<MemoryEvent> recentForArea(ServerLevel level, BlockPos fallbackPos, VillageMembership.VillageArea area) {
        return cachedRecentEvents(
                level,
                RecentQueryKey.area(level, fallbackPos, area.centerBlock()),
                () -> recentForAreaUncached(level, fallbackPos, area)
        );
    }

    private static List<MemoryEvent> recentNearUncached(ServerLevel level, BlockPos pos) {
        pruneIfReady(level);
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
        return List.copyOf(relevant);
    }

    private static List<MemoryEvent> recentForAreaUncached(ServerLevel level, BlockPos fallbackPos, VillageMembership.VillageArea area) {
        pruneIfReady(level);
        ArrayDeque<MemoryEvent> events = EVENTS.get(level.dimension());
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        List<MemoryEvent> relevant = new ArrayList<>();
        for (MemoryEvent event : events) {
            if (area.contains(event.pos()) || event.pos().distSqr(fallbackPos) <= RELEVANT_EVENT_RADIUS_SQR) {
                relevant.add(event);
            }
        }
        return List.copyOf(relevant);
    }

    private static List<MemoryEvent> cachedRecentEvents(
            ServerLevel level,
            RecentQueryKey cacheKey,
            Supplier<List<MemoryEvent>> eventsSupplier) {
        long gameTime = level.getGameTime();
        CachedRecentEvents cached = RECENT_QUERY_CACHE.get(cacheKey);
        if (cached != null && cached.isValid(gameTime)) {
            return cached.events();
        }

        List<MemoryEvent> events = eventsSupplier.get();
        RECENT_QUERY_CACHE.put(cacheKey, new CachedRecentEvents(events, gameTime + RECENT_QUERY_CACHE_TICKS));
        return events;
    }

    private static void remember(ServerLevel level, MemoryEvent event) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(event.tagId(), "event tag id");
        Objects.requireNonNull(event.pos(), "event position");
        ArrayDeque<MemoryEvent> events = EVENTS.computeIfAbsent(level.dimension(), ignored -> new ArrayDeque<>());
        pruneIfReady(level);
        if (isDuplicateEvent(events, event)) {
            return;
        }
        events.addLast(event);
        trimToMaxEvents(events);
        invalidateRecentCache(level.dimension());
        VillagerEventTriggerService.onMemoryWritten(level, event);
    }

    public static void clear() {
        EVENTS.clear();
        NEXT_PRUNE_TICKS.clear();
        RECENT_QUERY_CACHE.clear();
    }

    private static void pruneIfReady(ServerLevel level) {
        ArrayDeque<MemoryEvent> events = EVENTS.get(level.dimension());
        if (events == null) {
            return;
        }

        long gameTime = level.getGameTime();
        long nextPrune = NEXT_PRUNE_TICKS.getOrDefault(level.dimension(), 0L);
        if (gameTime < nextPrune && events.size() <= MAX_EVENTS_PER_DIMENSION) {
            return;
        }

        pruneNow(level, events, gameTime);
    }

    private static void pruneNow(ServerLevel level, ArrayDeque<MemoryEvent> events, long gameTime) {
        long oldestAllowed = level.getGameTime() - EVENT_TTL_TICKS;
        while (!events.isEmpty() && (events.peekFirst().gameTime() < oldestAllowed || events.size() > MAX_EVENTS_PER_DIMENSION)) {
            events.removeFirst();
        }
        NEXT_PRUNE_TICKS.put(level.dimension(), gameTime + EVENT_PRUNE_INTERVAL_TICKS);
    }

    private static void trimToMaxEvents(ArrayDeque<MemoryEvent> events) {
        while (events.size() > MAX_EVENTS_PER_DIMENSION) {
            events.removeFirst();
        }
    }

    private static void invalidateRecentCache(ResourceKey<Level> dimension) {
        RECENT_QUERY_CACHE.keySet().removeIf(key -> key.dimension().equals(dimension));
    }

    private static boolean isDuplicateEvent(ArrayDeque<MemoryEvent> events, MemoryEvent event) {
        return isDuplicateWeatherEvent(events, event) || isDuplicateNoisyEvent(events, event);
    }

    private static boolean isDuplicateWeatherEvent(ArrayDeque<MemoryEvent> events, MemoryEvent event) {
        if (!isWeatherEvent(event.tag())) {
            return false;
        }

        Iterator<MemoryEvent> iterator = events.descendingIterator();
        while (iterator.hasNext()) {
            MemoryEvent previous = iterator.next();
            if (event.gameTime() - previous.gameTime() > WEATHER_EVENT_DEDUPE_TICKS) {
                return false;
            }
            if (sameTag(previous, event) && previous.pos().distSqr(event.pos()) <= RELEVANT_EVENT_RADIUS_SQR) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDuplicateNoisyEvent(ArrayDeque<MemoryEvent> events, MemoryEvent event) {
        if (!isNoisyEvent(event.tag())) {
            return false;
        }
        if (event.tag() == EventTag.VILLAGER_RETALIATION_STARTED) {
            return isDuplicateRetaliationEvent(events, event);
        }

        Iterator<MemoryEvent> iterator = events.descendingIterator();
        while (iterator.hasNext()) {
            MemoryEvent previous = iterator.next();
            if (event.gameTime() - previous.gameTime() > NOISY_EVENT_DEDUPE_TICKS) {
                return false;
            }
            if (sameTag(previous, event)
                    && Objects.equals(previous.playerId(), event.playerId())
                    && previous.pos().distSqr(event.pos()) <= RELEVANT_EVENT_RADIUS_SQR) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDuplicateRetaliationEvent(ArrayDeque<MemoryEvent> events, MemoryEvent event) {
        Iterator<MemoryEvent> iterator = events.descendingIterator();
        while (iterator.hasNext()) {
            MemoryEvent previous = iterator.next();
            if (event.gameTime() - previous.gameTime() > NOISY_EVENT_DEDUPE_TICKS) {
                return false;
            }
            if (!sameTag(previous, event)
                    || previous.retaliation() == null
                    || event.retaliation() == null) {
                continue;
            }
            if (previous.pos().distSqr(event.pos()) > RELEVANT_EVENT_RADIUS_SQR) {
                continue;
            }
            if (Objects.equals(previous.sourceId(), event.sourceId())
                    && Objects.equals(previous.playerId(), event.playerId())
                    && Objects.equals(previous.retaliation().targetTypeId(), event.retaliation().targetTypeId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameTag(MemoryEvent left, MemoryEvent right) {
        return Objects.equals(left.tagId(), right.tagId());
    }

    private static boolean isWeatherEvent(EventTag tag) {
        return tag == EventTag.THUNDERSTORM || tag == EventTag.SANDSTORM || tag == EventTag.SNOWSTORM;
    }

    private static boolean isNoisyEvent(EventTag tag) {
        return tag == EventTag.REPUTATION_CHANGED
                || tag == EventTag.VILLAGER_ATTACKED
                || tag == EventTag.BABY_VILLAGER_ATTACKED
                || tag == EventTag.PLAYER_ATTACKED_VILLAGER
                || tag == EventTag.PLAYER_CONTAINER_THEFT
                || tag == EventTag.NIGHT_ATTACK
                || tag == EventTag.RAID
                || tag == EventTag.PLAYER_DEFENDED_VILLAGE
                || tag == EventTag.PLAYER_DEFENDED_RAID
                || tag == EventTag.VILLAGER_RETALIATION_STARTED;
    }

    public static boolean hasAny(List<MemoryEvent> events, EventTag... tags) {
        if (events == null || events.isEmpty() || tags == null || tags.length == 0) {
            return false;
        }
        EnumSet<EventTag> wanted = wantedTags(tags);
        if (wanted.isEmpty()) {
            return false;
        }
        return events.stream().anyMatch(event -> wanted.contains(event.tag()));
    }

    public static boolean hasAnyTag(List<MemoryEvent> events, Set<ResourceLocation> tagIds) {
        if (events == null || events.isEmpty() || tagIds == null || tagIds.isEmpty()) {
            return false;
        }
        return events.stream().anyMatch(event -> tagIds.contains(event.tagId()));
    }

    public static boolean hasAnyForPlayer(List<MemoryEvent> events, UUID playerId, EventTag... tags) {
        if (playerId == null || events == null || events.isEmpty() || tags == null || tags.length == 0) {
            return false;
        }
        EnumSet<EventTag> wanted = wantedTags(tags);
        if (wanted.isEmpty()) {
            return false;
        }
        return events.stream().anyMatch(event ->
                playerId.equals(event.playerId()) && wanted.contains(event.tag()));
    }

    public static boolean hasAnyTagForPlayer(List<MemoryEvent> events, UUID playerId, Set<ResourceLocation> tagIds) {
        if (playerId == null || events == null || events.isEmpty() || tagIds == null || tagIds.isEmpty()) {
            return false;
        }
        return events.stream().anyMatch(event ->
                playerId.equals(event.playerId()) && tagIds.contains(event.tagId()));
    }

    public static int countForPlayer(List<MemoryEvent> events, UUID playerId, EventTag... tags) {
        if (playerId == null || events == null || events.isEmpty() || tags == null || tags.length == 0) {
            return 0;
        }
        EnumSet<EventTag> wanted = wantedTags(tags);
        if (wanted.isEmpty()) {
            return 0;
        }
        return (int) events.stream()
                .filter(event -> playerId.equals(event.playerId()) && wanted.contains(event.tag()))
                .count();
    }

    private static EnumSet<EventTag> wantedTags(EventTag... tags) {
        EnumSet<EventTag> wanted = EnumSet.noneOf(EventTag.class);
        for (EventTag tag : tags) {
            if (tag != null) {
                wanted.add(tag);
            }
        }
        return wanted;
    }

    public static ResourceLocation idFor(EventTag tag) {
        return tag == null
                ? null
                : ResourceLocation.fromNamespaceAndPath(
                        VillagerRetaliation.MOD_ID,
                        tag.name().toLowerCase(Locale.ROOT));
    }

    public static Optional<EventTag> legacyTag(ResourceLocation tagId) {
        if (tagId == null || !VillagerRetaliation.MOD_ID.equals(tagId.getNamespace())) {
            return Optional.empty();
        }
        try {
            return Optional.of(EventTag.valueOf(tagId.getPath().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<ResourceLocation> parseTagId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (!normalized.contains(":")) {
            try {
                return Optional.of(idFor(EventTag.valueOf(normalized.toUpperCase(Locale.ROOT))));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        ResourceLocation tagId = ResourceLocation.tryParse(normalized);
        return tagId == null ? Optional.empty() : Optional.of(tagId);
    }

    public enum EventTag {
        BABY_BORN,
        IRON_GOLEM_DEFEATED_MOB,
        THUNDERSTORM,
        SANDSTORM,
        SNOWSTORM,
        VILLAGE_FIRE,
        NIGHT_ATTACK,
        RAID,
        VILLAGER_DEATH,
        PLAYER_KILLED_VILLAGER,
        VILLAGER_ATTACKED,
        BABY_VILLAGER_ATTACKED,
        PLAYER_ATTACKED_VILLAGER,
        PLAYER_DEFENDED_VILLAGE,
        PLAYER_DEFENDED_RAID,
        PLAYER_CURED_VILLAGER,
        GOLEM_CREATED,
        GOLEM_KILLED,
        NEARBY_HOSTILE_MOB,
        REPUTATION_CHANGED,
        PLAYER_GAVE_LOVED_GIFT,
        PLAYER_GAVE_LIKED_GIFT,
        PLAYER_GAVE_NEUTRAL_GIFT,
        PLAYER_GAVE_DISLIKED_GIFT,
        PLAYER_GAVE_HATED_GIFT,
        PLAYER_CONTAINER_THEFT,
        PLAYER_COMPLETED_QUEST,
        VILLAGER_RETALIATION_STARTED
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

    public record MemoryEvent(
            EventTag tag,
            ResourceLocation tagId,
            long gameTime,
            BlockPos pos,
            UUID sourceId,
            UUID playerId,
            GiftMemory gift,
            ContainerTheftMemory containerTheft,
            RetaliationMemory retaliation,
            CuredVillagerMemory curedVillager,
            KilledVillagerMemory killedVillager) {
        public MemoryEvent(
                EventTag tag,
                long gameTime,
                BlockPos pos,
                UUID sourceId,
                UUID playerId,
                GiftMemory gift,
                ContainerTheftMemory containerTheft,
                RetaliationMemory retaliation,
                CuredVillagerMemory curedVillager,
                KilledVillagerMemory killedVillager) {
            this(
                    tag,
                    idFor(tag),
                    gameTime,
                    pos,
                    sourceId,
                    playerId,
                    gift,
                    containerTheft,
                    retaliation,
                    curedVillager,
                    killedVillager);
        }
    }

    public record GiftMemory(String villagerName, String itemName, VillagerGiftPreferences.GiftReaction reaction, int reputationValue) {
    }

    public record ContainerTheftMemory(
            String villagerName,
            String itemName,
            String itemId,
            int itemCount,
            String containerName,
            String lootTable) {
    }

    public record RetaliationMemory(
            String villagerName,
            String targetName,
            String targetTypeId) {
    }

    public record CuredVillagerMemory(String villagerName) {
    }

    public record KilledVillagerMemory(String villagerName) {
    }

    private record RecentQueryKey(
            ResourceKey<Level> dimension,
            String kind,
            BlockPos pos,
            BlockPos areaCenter) {
        private static RecentQueryKey near(ServerLevel level, BlockPos pos) {
            return new RecentQueryKey(level.dimension(), "near", pos.immutable(), BlockPos.ZERO);
        }

        private static RecentQueryKey area(ServerLevel level, BlockPos fallbackPos, BlockPos areaCenter) {
            return new RecentQueryKey(level.dimension(), "area", fallbackPos.immutable(), areaCenter.immutable());
        }
    }

    private record CachedRecentEvents(List<MemoryEvent> events, long expiresGameTime) {
        private boolean isValid(long gameTime) {
            return gameTime < this.expiresGameTime;
        }
    }
}
