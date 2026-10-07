package com.jvn.villagerretaliation.villagelife;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import com.jvn.villagerretaliation.social.BreedingBlockReason;
import com.jvn.villagerretaliation.social.VillagerBreedingPolicy;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.village.VillageMembership;
import com.jvn.villagerretaliation.villager.VillagerMovementSpeedPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;

public final class VillageLifeService {
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final int MAX_CANDIDATES = 8;
    private static final int DURATION = 600;
    private static final int BACKOFF = 200;

    private VillageLifeService() {
    }

    public static void beforeTick(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        State state = STATES.get(level.getServer());
        if (state == null) return;
        Resident resident = state.residents.get(villager.getUUID());
        if (resident == null) return;
        resident.lastTick = level.getGameTime();
        if (resident.activity == null) return;
        long started = System.nanoTime();
        village(state, level, resident);
        if (resident.activity == null) return;
        var activity = resident.activity;
        if (!eligible(level, villager) || VillageLifeAvailability.blocker(level, villager) != BreedingBlockReason.NONE
                || villager.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET)
                || level.isRaining() && level.canSeeSky(activity.group.destination)
                || !ownsTargets(resident) || level.getGameTime() >= activity.group.expiresAt) {
            release(resident);
        } else if (villager.distanceToSqr(activity.group.destination.getBottomCenter()) <= 6.25D) {
            if (activity.arrivedAt == Long.MIN_VALUE) activity.arrivedAt = level.getGameTime();
        } else if (activity.moving && villager.getNavigation().isDone()) {
            state.failures++;
            resident.retryAt = level.getGameTime() + BACKOFF;
            release(resident);
        }
        state.nanos += System.nanoTime() - started;
    }

    public static void afterTick(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.get()
                || !TickThrottle.isSpreadTick(villager.getUUID(), level.getGameTime(),
                        VillagerRetaliationConfig.VILLAGE_LIFE_DECISION_INTERVAL.get())) return;
        State state = STATES.computeIfAbsent(level.getServer(), ignored -> new State());
        if (!eligible(level, villager)) {
            remove(villager);
            return;
        }
        Resident resident = state.residents.computeIfAbsent(villager.getUUID(), ignored -> new Resident(villager, state));
        resident.lastTick = level.getGameTime();
        if (!resident.queued && level.getGameTime() >= resident.retryAt) {
            resident.queued = true;
            resident.queuedAt = level.getGameTime();
            state.queue.addLast(resident);
        }
    }

    public static void tick(MinecraftServer server) {
        State state = STATES.get(server);
        if (state == null) return;
        if (!VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.get()) {
            clear(server);
            return;
        }
        long start = System.nanoTime();
        var activities = state.active.iterator();
        while (activities.hasNext()) {
            Resident resident = activities.next();
            if (resident.lastTick != resident.villager.level().getGameTime()) {
                activities.remove();
                release(resident);
            }
        }
        long budget = VillagerRetaliationConfig.VILLAGE_LIFE_BUDGET_MICROS.get() * 1_000L;
        int pathsBefore = state.paths;
        int decisions = 0;
        int limit = Math.min(8, VillagerRetaliationConfig.VILLAGE_LIFE_MAX_DECISIONS.get());
        while (!state.queue.isEmpty() && decisions < limit) {
            if (System.nanoTime() - start >= budget) {
                break;
            }
            Resident resident = state.queue.removeFirst();
            resident.queued = false;
            decisions++;
            state.decisions++;
            var level = (ServerLevel) resident.villager.level();
            state.maxQueueDelay = Math.max(state.maxQueueDelay, level.getGameTime() - resident.queuedAt);
            if (state.residents.get(resident.villager.getUUID()) != resident
                    || resident.lastTick != level.getGameTime() || !eligible(level, resident.villager)) {
                remove(resident.villager);
                continue;
            }
            decide(state, level, resident, state.paths - pathsBefore < Math.min(2, VillagerRetaliationConfig.VILLAGE_LIFE_MAX_PATHS.get()));
        }
        long elapsed = System.nanoTime() - start;
        if (elapsed > budget && decisions > 0) state.overruns++;
        state.nanos += elapsed;
    }

    private static void decide(State state, ServerLevel level, Resident resident, boolean pathAvailable) {
        Villager villager = resident.villager;
        rememberRaidVictory(level, villager, level.getRaidAt(villager.blockPosition()));
        if (VillageLifeAvailability.blocker(level, villager) != BreedingBlockReason.NONE
                || villager.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET)) {
            release(resident);
            return;
        }
        Village village = village(state, level, resident);
        if (village == null) return;
        rememberLocations(level, villager, village);
        if (resident.activity != null) {
            completeVisit(level, resident);
            return;
        }
        if (!unoccupied(villager) || !willing(level, villager)) return;
        if (village.id != null) {
            var occasions = VillageOccasionSavedData.get(level);
            occasions.scheduleMarket(level, village.id);
            var town = SettlementSavedData.get(level).get(village.id).orElse(null);
            if (town != null && level.getGameTime() - town.foundedAt() < 24_000L) {
                occasions.schedule(level, village.id, VillageOccasionSavedData.Kind.FOUNDING, "founding:" + town.foundedAt());
            }
            if (village.gathering != null && level.isRaining() && level.canSeeSky(village.gathering.destination)) {
                village.gathering = null;
            }
            if (village.gathering != null && village.gathering.expiresAt > level.getGameTime()
                    && village.gathering.members.size() < 6) {
                start(state, level, resident, village.gathering, pathAvailable);
                return;
            }
            if (!occasions.due(level, village.id).isEmpty()) {
                BlockPos destination = destination(level, resident, village, true);
                if (destination != null) {
                    Group group = new Group(destination, Kind.GATHERING, level.getGameTime() + DURATION);
                    if (start(state, level, resident, group, pathAvailable)) {
                        village.gathering = group;
                        occasions.consume(level, village.id);
                    }
                }
                return;
            }
        }
        Resident partner = partner(state, level, resident, village);
        if (partner != null) {
            if (partner.activity != null) {
                start(state, level, resident, partner.activity.group, pathAvailable);
                return;
            }
            boolean rain = level.isRaining();
            if (!rain || !level.canSeeSky(partner.villager.blockPosition())) {
                Group group = new Group(partner.villager.blockPosition(), villager.isBaby() ? Kind.PLAY : Kind.VISIT,
                        level.getGameTime() + DURATION);
                if (start(state, level, resident, group, pathAvailable)) hold(partner, group);
                return;
            }
        }
        BlockPos destination = destination(level, resident, village, false);
        if (destination == null) {
            resident.retryAt = level.getGameTime() + BACKOFF;
        } else {
            start(state, level, resident, new Group(destination, Kind.WALK, level.getGameTime() + DURATION), pathAvailable);
        }
    }

    static void rememberRaidVictory(ServerLevel level, Villager villager, net.minecraft.world.entity.raid.Raid raid) {
        if (raid == null || !raid.isVictory()) return;
        VillageAllegianceApi.canonicalPrimary(level, villager).ifPresent(village ->
                VillageOccasionSavedData.get(level).schedule(level, village, VillageOccasionSavedData.Kind.RAID_VICTORY,
                        "raid:" + raid.getId()));
    }

    private static boolean start(State state, ServerLevel level, Resident resident, Group group, boolean pathAvailable) {
        if (group.members.size() >= 6 || !level.hasChunkAt(group.destination)
                || level.isRaining() && level.canSeeSky(group.destination)
                || resident.villager.distanceToSqr(group.destination.getBottomCenter()) > 24.0D * 24.0D) return false;
        if (resident.villager.distanceToSqr(group.destination.getBottomCenter()) <= 4.0D) {
            hold(resident, group);
            return true;
        }
        if (!pathAvailable) return false;
        BlockPos origin = resident.villager.blockPosition();
        // vanilla builds a pathfinding region eight blocks beyond the supplied search range
        for (int x = (origin.getX() - 32) >> 4; x <= (origin.getX() + 32) >> 4; x++) {
            for (int z = (origin.getZ() - 32) >> 4; z <= (origin.getZ() + 32) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    resident.retryAt = level.getGameTime() + BACKOFF;
                    return false;
                }
            }
        }
        state.paths++;
        Path path = resident.villager.getNavigation().createPath(group.destination, 1, 24);
        if (path == null || !path.canReach()
                || !resident.villager.getNavigation().moveTo(path, VillagerMovementSpeedPolicy.WALK_SPEED_MODIFIER)) {
            state.failures++;
            resident.retryAt = level.getGameTime() + BACKOFF;
            return false;
        }
        own(resident, group, path, true);
        return true;
    }

    private static void hold(Resident resident, Group group) {
        own(resident, group, new Path(List.of(), group.destination, true), false);
    }

    private static void own(Resident resident, Group group, Path path, boolean moving) {
        var activity = new Activity(group, path, moving);
        resident.activity = activity;
        resident.state.active.add(resident);
        group.members.add(resident);
        var brain = resident.villager.getBrain();
        brain.setMemory(MemoryModuleType.WALK_TARGET, activity.walk);
        brain.setMemory(MemoryModuleType.PATH, path);
        if (!brain.hasMemoryValue(MemoryModuleType.LOOK_TARGET)) {
            brain.setMemory(MemoryModuleType.LOOK_TARGET, activity.look);
        }
    }

    private static boolean ownsTargets(Resident resident) {
        var activity = resident.activity;
        var brain = resident.villager.getBrain();
        return brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == activity.walk
                && brain.getMemory(MemoryModuleType.PATH).orElse(null) == activity.path
                && (!activity.moving || resident.villager.getNavigation().getPath() == activity.path);
    }

    private static void completeVisit(ServerLevel level, Resident resident) {
        Activity activity = resident.activity;
        if (activity.group.kind != Kind.VISIT || activity.visited || activity.arrivedAt == Long.MIN_VALUE
                || level.getGameTime() - activity.arrivedAt < 80L) return;
        for (Resident other : activity.group.members) {
            if (other == resident || other.activity == null || other.activity.arrivedAt == Long.MIN_VALUE
                    || level.getGameTime() - other.activity.arrivedAt < 80L
                    || resident.villager.distanceToSqr(other.villager) > 9.0D) continue;
            if (VillagerBreedingPolicy.availableForSocialVisit(level, resident.villager)
                    && VillagerBreedingPolicy.availableForSocialVisit(level, other.villager)) {
                VillagerSocialGraphSavedData.get(level).recordCourtshipVisit(level, resident.villager, other.villager);
                activity.visited = true;
                other.activity.visited = true;
            }
            break;
        }
    }

    private static Resident partner(State state, ServerLevel level, Resident resident, Village village) {
        Resident best = null;
        int bestScore = Integer.MIN_VALUE;
        int count = Math.min(MAX_CANDIDATES / 2, village.members.size());
        for (int index = 0; index < count; index++) {
            UUID id = village.members.get(Math.floorMod(village.cursor++, village.members.size()));
            Resident other = state.residents.get(id);
            if (other == null || other == resident || other.lastTick != level.getGameTime()
                    || other.villager.isBaby() != resident.villager.isBaby()
                    || resident.villager.distanceToSqr(other.villager) > 24.0D * 24.0D
                    || VillageLifeAvailability.blocker(level, other.villager) != BreedingBlockReason.NONE
                    || other.villager.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET)) continue;
            if (other.activity != null) {
                if (!resident.villager.isBaby() || other.activity.group.kind != Kind.PLAY
                        || other.activity.group.members.size() >= 6) continue;
            } else if (!unoccupied(other.villager) || !willing(level, other.villager)) continue;
            int score = -((int) resident.villager.distanceToSqr(other.villager));
            if (VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
                var graph = VillagerSocialGraphSavedData.get(level);
                for (var type : List.of(VillagerSocialGraphSavedData.RelationshipType.FRIEND,
                        VillagerSocialGraphSavedData.RelationshipType.PARENT, VillagerSocialGraphSavedData.RelationshipType.CHILD,
                        VillagerSocialGraphSavedData.RelationshipType.SIBLING)) {
                    if (graph.relationships(resident.villager.getUUID(), type).contains(id)) score += 100;
                }
                if (graph.relationshipStage(resident.villager.getUUID(), id).isPresent()) score += 150;
            }
            if (score > bestScore) {
                best = other;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean willing(ServerLevel level, Villager villager) {
        VillagerMood mood = VillagerMoodService.mood(level, villager).primaryMood();
        if (mood == VillagerMood.AFRAID || mood == VillagerMood.ANGRY) return false;
        var attributes = VillagerProfileManager.getProfile(level, villager.getUUID())
                .map(profile -> profile.socialAttributes()).orElse(VillagerSocialAttributes.DEFAULT);
        int chance = VillagerRetaliationConfig.VILLAGE_LIFE_ACTIVITY_CHANCE.get();
        chance += (attributes.kindness() + attributes.charm() - 100) / 10;
        if (mood == VillagerMood.LONELY || mood == VillagerMood.HOPEFUL || villager.isBaby()) chance += 10;
        if (mood == VillagerMood.GRIEVING || mood == VillagerMood.STRESSED) chance -= 10;
        return villager.getRandom().nextInt(100) < Math.clamp(chance, 0, 100);
    }

    private static Village village(State state, ServerLevel level, Resident resident) {
        VillageAllegianceId id = VillageAllegianceApi.canonicalPrimary(level, resident.villager).orElse(null);
        BlockPos meeting = VillageMembership.meetingPoint(level, resident.villager).orElse(null);
        String key = id != null ? level.dimension().location() + "/" + id
                : meeting != null ? level.dimension().location() + "/" + meeting.asLong() : null;
        if (key == null) {
            release(resident);
            leaveVillage(state, resident);
            resident.villageKey = null;
            return null;
        }
        if (!key.equals(resident.villageKey)) {
            release(resident);
            leaveVillage(state, resident);
            resident.villageKey = key;
            state.villages.computeIfAbsent(key, ignored -> new Village(id)).members.add(resident.villager.getUUID());
        }
        return state.villages.get(key);
    }

    private static void rememberLocations(ServerLevel level, Villager villager, Village village) {
        for (var memory : List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.MEETING_POINT)) {
            villager.getBrain().getMemory(memory).filter(pos -> pos.dimension().equals(level.dimension())).map(GlobalPos::pos)
                    .filter(level::hasChunkAt).ifPresent(pos -> {
                        village.locations.put(pos.immutable(), new Location(memory != MemoryModuleType.HOME, level.getGameTime() + 1_200));
                        if (village.locations.size() > 32) village.locations.remove(village.locations.keySet().iterator().next());
                    });
        }
    }

    private static BlockPos destination(ServerLevel level, Resident resident, Village village, boolean gathering) {
        var locations = village.locations.entrySet().iterator();
        BlockPos selected = null;
        int candidates = 0;
        while (locations.hasNext() && candidates++ < (gathering ? MAX_CANDIDATES : MAX_CANDIDATES / 2)) {
            var entry = locations.next();
            if (entry.getValue().expiresAt < level.getGameTime() || !level.hasChunkAt(entry.getKey())) {
                locations.remove();
                continue;
            }
            BlockPos pos = entry.getKey();
            if (gathering && !entry.getValue().publicPlace && !level.isRaining()
                    || resident.villager.distanceToSqr(pos.getBottomCenter()) > 24.0D * 24.0D) continue;
            if (level.isRaining() && level.canSeeSky(pos)) continue;
            selected = pos;
            if (resident.villager.getRandom().nextBoolean()) break;
        }
        if (selected != null) {
            Location location = village.locations.remove(selected);
            village.locations.put(selected, location);
        }
        return selected;
    }

    private static boolean unoccupied(Villager villager) {
        return !villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && !villager.getBrain().hasMemoryValue(MemoryModuleType.PATH)
                && villager.getNavigation().isDone();
    }

    private static boolean eligible(ServerLevel level, Villager villager) {
        return VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.get() && villager.isAlive() && !villager.isRemoved() && !villager.isNoAi()
                && level.isPositionEntityTicking(villager.blockPosition())
                && level.getNearestPlayer(villager, 64.0D) != null;
    }

    public static boolean cancelRecompute(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        State state = STATES.get(level.getServer());
        Resident resident = state == null ? null : state.residents.get(villager.getUUID());
        if (resident == null || resident.activity == null || !ownsTargets(resident)) return false;
        resident.retryAt = level.getGameTime() + BACKOFF;
        release(resident);
        return true;
    }

    public static void yield(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        State state = STATES.get(level.getServer());
        if (state == null) return;
        Resident resident = state.residents.get(villager.getUUID());
        if (resident != null) release(resident);
    }

    private static void release(Resident resident) {
        Activity activity = resident.activity;
        if (activity == null) return;
        var brain = resident.villager.getBrain();
        if (brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == activity.walk) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            if (resident.villager.getNavigation().getPath() == activity.path) resident.villager.getNavigation().stop();
        }
        if (brain.getMemory(MemoryModuleType.PATH).orElse(null) == activity.path) brain.eraseMemory(MemoryModuleType.PATH);
        if (brain.getMemory(MemoryModuleType.LOOK_TARGET).orElse(null) == activity.look) brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        activity.group.members.remove(resident);
        resident.activity = null;
        resident.state.active.remove(resident);
    }

    public static void remove(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        State state = STATES.get(level.getServer());
        if (state == null) return;
        Resident resident = state.residents.remove(entity.getUUID());
        if (resident == null) return;
        release(resident);
        leaveVillage(state, resident);
        if (resident.queued) state.queue.remove(resident);
    }

    private static void leaveVillage(State state, Resident resident) {
        Village previous = state.villages.get(resident.villageKey);
        if (previous == null) return;
        previous.members.remove(resident.villager.getUUID());
        if (previous.members.isEmpty()) state.villages.remove(resident.villageKey);
    }

    public static void clear(MinecraftServer server) {
        State state = STATES.remove(server);
        if (state != null) state.residents.values().forEach(VillageLifeService::release);
    }

    public static Diagnostics diagnostics(MinecraftServer server) {
        State state = STATES.get(server);
        return state == null ? new Diagnostics(0, 0, 0, 0, 0, 0, 0, 0)
                : new Diagnostics(state.nanos, state.decisions, state.paths, state.failures, state.overruns,
                        state.maxQueueDelay, state.queue.size(), (int) state.residents.values().stream().filter(r -> r.activity != null).count());
    }

    public record Diagnostics(long nanos, long decisions, int paths, long failures, long budgetOverruns,
                              long maxQueueDelayTicks, int queued, int active) {
    }

    private static final class State {
        private final Map<UUID, Resident> residents = new HashMap<>();
        private final java.util.Set<Resident> active = new java.util.HashSet<>();
        private final Map<String, Village> villages = new HashMap<>();
        private final ArrayDeque<Resident> queue = new ArrayDeque<>();
        private long nanos;
        private long decisions;
        private int paths;
        private long failures;
        private long overruns;
        private long maxQueueDelay;
    }

    private static final class Resident {
        private final Villager villager;
        private final State state;
        private String villageKey;
        private Activity activity;
        private long lastTick;
        private long retryAt;
        private long queuedAt;
        private boolean queued;

        private Resident(Villager villager, State state) {
            this.villager = villager;
            this.state = state;
        }
    }

    private static final class Village {
        private final VillageAllegianceId id;
        private final List<UUID> members = new ArrayList<>();
        private final Map<BlockPos, Location> locations = new LinkedHashMap<>();
        private int cursor;
        private Group gathering;

        private Village(VillageAllegianceId id) {
            this.id = id;
        }
    }

    private record Location(boolean publicPlace, long expiresAt) {
    }

    private enum Kind {
        WALK, VISIT, PLAY, GATHERING
    }

    private static final class Group {
        private final BlockPos destination;
        private final Kind kind;
        private final long expiresAt;
        private final List<Resident> members = new ArrayList<>();

        private Group(BlockPos destination, Kind kind, long expiresAt) {
            this.destination = destination.immutable();
            this.kind = kind;
            this.expiresAt = expiresAt;
        }
    }

    private static final class Activity {
        private final Group group;
        private final Path path;
        private final WalkTarget walk;
        private final PositionTracker look;
        private final boolean moving;
        private long arrivedAt = Long.MIN_VALUE;
        private boolean visited;

        private Activity(Group group, Path path, boolean moving) {
            this.group = group;
            this.path = path;
            this.walk = new WalkTarget(group.destination, (float) VillagerMovementSpeedPolicy.WALK_SPEED_MODIFIER, 1);
            this.look = new BlockPosTracker(group.destination);
            this.moving = moving;
        }
    }
}
