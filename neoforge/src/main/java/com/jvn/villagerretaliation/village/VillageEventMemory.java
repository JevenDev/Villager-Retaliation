package com.jvn.villagerretaliation.village;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.event.VillagerEventTriggerService;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    private static final int MAX_EVENTS_PER_BUCKET = 80;
    private static final int MAX_RECENT_QUERY_CACHE_ENTRIES = 512;
    private static final double DEDUPE_RADIUS_SQR = 48.0D * 48.0D;
    private static final Map<ResourceKey<Level>, Long> NEXT_PRUNE_TICKS = new HashMap<>();
    private static final Map<RecentQueryKey, CachedRecentEvents> RECENT_QUERY_CACHE =
            new LinkedHashMap<>(64, 0.75F, true);

    private VillageEventMemory() {
    }

    public static boolean remember(ServerLevel level, EventTag tag, BlockPos pos, Entity source, Entity player) {
        return remember(level, tag, pos, source, player, defaultScope(tag)).dispatched();
    }

    public static WriteResult remember(
            ServerLevel level,
            EventTag tag,
            BlockPos pos,
            Entity source,
            Entity player,
            MemoryScope scope) {
        return remember(level, new MemoryEvent(
                tag,
                level.getGameTime(),
                level.dimension(),
                pos.immutable(),
                source == null ? null : source.getUUID(),
                player == null ? null : player.getUUID(),
                null,
                null,
                null,
                null,
                null
        ), source, scope);
    }

    public static boolean remember(ServerLevel level, ResourceLocation tagId, BlockPos pos, Entity source, Entity player) {
        return remember(level, tagId, pos, source, player, MemoryScope.BOTH).dispatched();
    }

    public static WriteResult remember(
            ServerLevel level,
            ResourceLocation tagId,
            BlockPos pos,
            Entity source,
            Entity player,
            MemoryScope scope) {
        if (tagId == null) {
            return WriteResult.EMPTY;
        }
        return remember(level, new MemoryEvent(
                legacyTag(tagId).orElse(null),
                tagId,
                level.getGameTime(),
                level.dimension(),
                pos.immutable(),
                source == null ? null : source.getUUID(),
                player == null ? null : player.getUUID(),
                null,
                null,
                null,
                null,
                null
        ), source, scope);
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
                level.dimension(),
                pos.immutable(),
                villager == null ? null : villager.getUUID(),
                player == null ? null : player.getUUID(),
                new GiftMemory(villagerName, itemName, reaction, reputationValue),
                null,
                null,
                null,
                null
        ), villager, MemoryScope.BOTH);
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
                level.dimension(),
                pos.immutable(),
                villager == null ? null : villager.getUUID(),
                playerId,
                null,
                null,
                null,
                new CuredVillagerMemory(villagerName),
                null
        ), villager, MemoryScope.BOTH);
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
                level.dimension(),
                pos.immutable(),
                villager == null ? null : villager.getUUID(),
                player == null ? null : player.getUUID(),
                null,
                new ContainerTheftMemory(villagerName, itemName, itemId, itemCount, containerName, lootTable),
                null,
                null,
                null
        ), villager, MemoryScope.BOTH);
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
                level.dimension(),
                pos.immutable(),
                witness == null ? null : witness.getUUID(),
                player == null ? null : player.getUUID(),
                null,
                null,
                null,
                null,
                new KilledVillagerMemory(killedVillagerName)
        ), witness, MemoryScope.BOTH);
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
                level.dimension(),
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
        ), villager, MemoryScope.BOTH);
    }

    /** Returns the speaker's memories, which follow the villager across allegiance changes. */
    public static List<MemoryEvent> recentForVillager(ServerLevel level, Villager villager) {
        return villager == null ? List.of() : recentForVillager(level, villager.getUUID());
    }

    public static List<MemoryEvent> recentForVillager(ServerLevel level, UUID villagerId) {
        if (villagerId == null) {
            return List.of();
        }
        return cachedRecentEvents(
                level,
                RecentQueryKey.villager(level, villagerId),
                () -> recentForVillagerUncached(level, villagerId));
    }

    /** Resolves an affiliated villager's canonical home, or an unaffiliated villager's exact current village. */
    public static Optional<VillageAllegianceId> villageForVillager(ServerLevel level, Villager villager) {
        if (villager == null || !villager.isAlive()) {
            return Optional.empty();
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> home = VillageAllegianceApi.canonicalPrimary(level, villager);
        return home.isPresent()
                ? home
                : registry.resolveAt(level, villager.blockPosition()).flatMap(registry::canonical);
    }

    /** Resolves an unloaded villager through the durable canonical resident roster. */
    public static Optional<VillageAllegianceId> villageForVillager(ServerLevel level, UUID villagerId) {
        if (level == null || villagerId == null) {
            return Optional.empty();
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        List<VillageAllegianceId> matches = registry.activeRecords(level.dimension().location()).stream()
                .filter(record -> record.residents().containsKey(villagerId))
                .map(VillageAllegianceRegistrySavedData.AllegianceRecord::id)
                .map(id -> registry.canonical(id).orElse(id))
                .distinct()
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public static List<MemoryEvent> recentForVillage(ServerLevel level, Villager villager) {
        return villageForVillager(level, villager)
                .map(id -> recentForVillage(level, id))
                .orElseGet(List::of);
    }

    public static List<MemoryEvent> recentForVillage(ServerLevel level, VillageAllegianceId villageId) {
        if (villageId == null) {
            return List.of();
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> canonical = registry.canonical(villageId);
        if (canonical.isEmpty()) {
            return List.of();
        }
        return recentForVillageUncached(level, canonical.get());
    }

    /** Reads communal memory only when the position is inside an indexed village footprint. */
    public static List<MemoryEvent> recentForVillageAt(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return List.of();
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        return registry.resolveAt(level, pos)
                .flatMap(registry::canonical)
                .map(id -> recentForVillage(level, id))
                .orElseGet(List::of);
    }

    public static List<MemoryEvent> lazyRecentForVillager(ServerLevel level, Villager villager) {
        return villager == null ? List.of() : new LazyRecentEvents(level, villager, false);
    }

    public static List<MemoryEvent> lazyRecentForVillage(ServerLevel level, Villager villager) {
        return villager == null || !villager.isAlive() ? List.of() : new LazyRecentEvents(level, villager, true);
    }

    private static List<MemoryEvent> recentForVillagerUncached(ServerLevel level, UUID villagerId) {
        pruneIfReady(level);
        ArrayDeque<MemoryEvent> events = VillageEventMemorySavedData.get(level).villagerEvents(villagerId);
        return events == null || events.isEmpty() ? List.of() : List.copyOf(events);
    }

    private static List<MemoryEvent> recentForVillageUncached(ServerLevel level, VillageAllegianceId villageId) {
        pruneIfReady(level);
        VillageEventMemorySavedData data = VillageEventMemorySavedData.get(level);
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        List<MemoryEvent> events = new ArrayList<>();
        for (Map.Entry<VillageAllegianceId, ArrayDeque<MemoryEvent>> bucket : data.villageBucketsById().entrySet()) {
            if (registry.canonical(bucket.getKey()).filter(villageId::equals).isPresent()) {
                events.addAll(cachedRecentEvents(
                        level,
                        RecentQueryKey.village(level, bucket.getKey()),
                        () -> bucket.getValue().isEmpty() ? List.of() : List.copyOf(bucket.getValue())));
            }
        }
        if (events.isEmpty()) {
            return List.of();
        }
        events.sort(Comparator.comparingLong(MemoryEvent::gameTime));
        if (events.size() > MAX_EVENTS_PER_BUCKET) {
            events = new ArrayList<>(events.subList(events.size() - MAX_EVENTS_PER_BUCKET, events.size()));
        }
        return List.copyOf(events);
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
        makeRecentCacheRoom(gameTime);
        RECENT_QUERY_CACHE.put(cacheKey, new CachedRecentEvents(events, gameTime + RECENT_QUERY_CACHE_TICKS));
        return events;
    }

    private static void makeRecentCacheRoom(long gameTime) {
        if (RECENT_QUERY_CACHE.size() < MAX_RECENT_QUERY_CACHE_ENTRIES) {
            return;
        }
        RECENT_QUERY_CACHE.entrySet().removeIf(entry -> !entry.getValue().isValid(gameTime));
        Iterator<RecentQueryKey> iterator = RECENT_QUERY_CACHE.keySet().iterator();
        while (RECENT_QUERY_CACHE.size() >= MAX_RECENT_QUERY_CACHE_ENTRIES && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static WriteResult remember(ServerLevel level, MemoryEvent event, Entity source, MemoryScope scope) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(event.tagId(), "event tag id");
        Objects.requireNonNull(event.dimension(), "event dimension");
        Objects.requireNonNull(event.pos(), "event position");
        MemoryScope effectiveScope = scope == null ? MemoryScope.BOTH : scope;
        VillageEventMemorySavedData data = VillageEventMemorySavedData.get(level);
        pruneIfReady(level, data);

        UUID villagerId = effectiveScope.includesVillager() && source instanceof Villager
                ? source.getUUID()
                : null;
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> villageId = effectiveScope.includesVillage()
                ? registry.resolveAt(level, event.pos()).flatMap(registry::canonical)
                : Optional.empty();

        boolean villagerChanged = false;
        if (villagerId != null) {
            ArrayDeque<MemoryEvent> events = data.villagerEventsForWrite(villagerId);
            villagerChanged = appendIfUnique(events, event);
        }

        boolean villageChanged = false;
        if (villageId.isPresent()) {
            ArrayDeque<MemoryEvent> events = data.villageEventsForWrite(villageId.get());
            villageChanged = appendIfUnique(events, event);
        }

        boolean changed = villagerChanged || villageChanged;
        if (changed) {
            data.markChanged();
            invalidateRecentCache();
        }

        // A communal bucket is the incident-level dedupe anchor. Personal witness copies may still be added
        // without firing quest/event callbacks again for the same village incident.
        boolean dispatched = villageId.isPresent() ? villageChanged : villagerChanged;
        if (dispatched) {
            VillagerQuestService.onMemoryEvent(level, event);
            VillagerEventTriggerService.onMemoryWritten(level, event);
        }
        return new WriteResult(changed, villagerChanged, villageChanged, dispatched);
    }

    private static boolean appendIfUnique(ArrayDeque<MemoryEvent> events, MemoryEvent event) {
        if (isDuplicateEvent(events, event)) {
            return false;
        }
        events.addLast(event);
        trimToMaxEvents(events);
        return true;
    }

    public static void clear() {
        NEXT_PRUNE_TICKS.clear();
        RECENT_QUERY_CACHE.clear();
    }

    static void onLegacyMigration() {
        invalidateRecentCache();
    }

    private static void pruneIfReady(ServerLevel level) {
        pruneIfReady(level, VillageEventMemorySavedData.get(level));
    }

    private static void pruneIfReady(ServerLevel level, VillageEventMemorySavedData data) {
        long gameTime = level.getGameTime();
        long nextPrune = NEXT_PRUNE_TICKS.getOrDefault(level.dimension(), 0L);
        if (gameTime < nextPrune) {
            return;
        }
        boolean removed = false;
        for (ArrayDeque<MemoryEvent> events : data.villageBuckets()) {
            removed |= pruneNow(events, gameTime);
        }
        for (ArrayDeque<MemoryEvent> events : data.villagerBuckets()) {
            removed |= pruneNow(events, gameTime);
        }
        data.removeEmptyBuckets();
        NEXT_PRUNE_TICKS.put(level.dimension(), gameTime + EVENT_PRUNE_INTERVAL_TICKS);
        if (removed) {
            data.markChanged();
            invalidateRecentCache();
        }
    }

    private static boolean pruneNow(ArrayDeque<MemoryEvent> events, long gameTime) {
        boolean removed = false;
        long oldestAllowed = gameTime - EVENT_TTL_TICKS;
        while (!events.isEmpty()
                && (events.peekFirst().gameTime() < oldestAllowed || events.size() > MAX_EVENTS_PER_BUCKET)) {
            events.removeFirst();
            removed = true;
        }
        return removed;
    }

    private static void trimToMaxEvents(ArrayDeque<MemoryEvent> events) {
        while (events.size() > MAX_EVENTS_PER_BUCKET) {
            events.removeFirst();
        }
    }

    private static void invalidateRecentCache() {
        RECENT_QUERY_CACHE.clear();
    }

    private static boolean isDuplicateEvent(ArrayDeque<MemoryEvent> events, MemoryEvent event) {
        return isDuplicateWeatherEvent(events, event) || isDuplicateNoisyEvent(events, event);
    }

    private static final class LazyRecentEvents extends AbstractList<MemoryEvent> {
        private final ServerLevel level;
        private final Villager villager;
        private final boolean village;
        private List<MemoryEvent> events;

        private LazyRecentEvents(ServerLevel level, Villager villager, boolean village) {
            this.level = level;
            this.villager = villager;
            this.village = village;
        }

        @Override
        public MemoryEvent get(int index) {
            return events().get(index);
        }

        @Override
        public int size() {
            return events().size();
        }

        private List<MemoryEvent> events() {
            if (this.events == null) {
                this.events = this.village
                        ? recentForVillage(this.level, this.villager)
                        : recentForVillager(this.level, this.villager);
            }
            return this.events;
        }
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
            if (sameTag(previous, event) && previous.pos().distSqr(event.pos()) <= DEDUPE_RADIUS_SQR) {
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
                    && previous.pos().distSqr(event.pos()) <= DEDUPE_RADIUS_SQR) {
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
            if (!sameTag(previous, event) || previous.retaliation() == null || event.retaliation() == null) {
                continue;
            }
            if (previous.pos().distSqr(event.pos()) > DEDUPE_RADIUS_SQR) {
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
                || tag == EventTag.PLAYER_KILLED_VILLAGER
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

    private static MemoryScope defaultScope(EventTag tag) {
        if (tag == null) {
            return MemoryScope.BOTH;
        }
        return switch (tag) {
            case BABY_BORN, IRON_GOLEM_DEFEATED_MOB, THUNDERSTORM, SANDSTORM, SNOWSTORM,
                    VILLAGE_FIRE, NIGHT_ATTACK, RAID, VILLAGER_DEATH, PLAYER_DEFENDED_VILLAGE,
                    PLAYER_DEFENDED_RAID, GOLEM_CREATED, GOLEM_KILLED, NEARBY_HOSTILE_MOB -> MemoryScope.VILLAGE;
            default -> MemoryScope.BOTH;
        };
    }

    public static boolean hasAny(List<MemoryEvent> events, EventTag... tags) {
        if (events == null || events.isEmpty() || tags == null || tags.length == 0) {
            return false;
        }
        EnumSet<EventTag> wanted = wantedTags(tags);
        return !wanted.isEmpty() && events.stream().anyMatch(event -> wanted.contains(event.tag()));
    }

    public static boolean hasAnyTag(List<MemoryEvent> events, Set<ResourceLocation> tagIds) {
        return events != null && !events.isEmpty() && tagIds != null && !tagIds.isEmpty()
                && events.stream().anyMatch(event -> tagIds.contains(event.tagId()));
    }

    public static boolean hasAnyForPlayer(List<MemoryEvent> events, UUID playerId, EventTag... tags) {
        if (playerId == null || events == null || events.isEmpty() || tags == null || tags.length == 0) {
            return false;
        }
        EnumSet<EventTag> wanted = wantedTags(tags);
        return !wanted.isEmpty() && events.stream().anyMatch(event ->
                playerId.equals(event.playerId()) && wanted.contains(event.tag()));
    }

    public static boolean hasAnyTagForPlayer(List<MemoryEvent> events, UUID playerId, Set<ResourceLocation> tagIds) {
        return playerId != null && events != null && !events.isEmpty() && tagIds != null && !tagIds.isEmpty()
                && events.stream().anyMatch(event ->
                playerId.equals(event.playerId()) && tagIds.contains(event.tagId()));
    }

    public static int countForPlayer(List<MemoryEvent> events, UUID playerId, EventTag... tags) {
        if (playerId == null || events == null || events.isEmpty() || tags == null || tags.length == 0) {
            return 0;
        }
        EnumSet<EventTag> wanted = wantedTags(tags);
        return wanted.isEmpty() ? 0 : (int) events.stream()
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

    public enum MemoryScope {
        VILLAGER("villager"),
        VILLAGE("village"),
        BOTH("both");

        private final String serializedName;

        MemoryScope(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public boolean includesVillager() {
            return this == VILLAGER || this == BOTH;
        }

        public boolean includesVillage() {
            return this == VILLAGE || this == BOTH;
        }

        public static Optional<MemoryScope> parse(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            for (MemoryScope scope : values()) {
                if (scope.serializedName.equals(normalized)) {
                    return Optional.of(scope);
                }
            }
            return Optional.empty();
        }
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

    public record WriteResult(boolean changed, boolean villagerChanged, boolean villageChanged, boolean dispatched) {
        public static final WriteResult EMPTY = new WriteResult(false, false, false, false);
    }

    public record MemoryEvent(
            EventTag tag,
            ResourceLocation tagId,
            long gameTime,
            ResourceKey<Level> dimension,
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
                ResourceKey<Level> dimension,
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
                    dimension,
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

    public record RetaliationMemory(String villagerName, String targetName, String targetTypeId) {
    }

    public record CuredVillagerMemory(String villagerName) {
    }

    public record KilledVillagerMemory(String villagerName) {
    }

    private record RecentQueryKey(ResourceKey<Level> dimension, MemoryScope scope, UUID id) {
        private static RecentQueryKey villager(ServerLevel level, UUID id) {
            return new RecentQueryKey(level.dimension(), MemoryScope.VILLAGER, id);
        }

        private static RecentQueryKey village(ServerLevel level, VillageAllegianceId id) {
            return new RecentQueryKey(level.dimension(), MemoryScope.VILLAGE, id.value());
        }
    }

    private record CachedRecentEvents(List<MemoryEvent> events, long expiresGameTime) {
        private boolean isValid(long gameTime) {
            return gameTime < this.expiresGameTime;
        }
    }
}
