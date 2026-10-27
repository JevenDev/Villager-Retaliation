package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class SettlementRecord {
    public static final int MAX_AREAS = 256;
    public static final int MAX_APPLICANTS = 8;
    public static final int MAX_HISTORY = 256;
    private final List<SettlementEvent> history = new ArrayList<>();
    private final VillageAllegianceId id;
    private final ResourceLocation dimension;
    private final BlockPos center;
    private final long foundedAt;
    private final Runnable dirty;
    private final Map<UUID, Set<SettlementPermission>> delegates = new LinkedHashMap<>();
    private final Map<UUID, SettlementArea> areas = new LinkedHashMap<>();
    private final Map<UUID, UUID> duties = new LinkedHashMap<>();
    private final Map<UUID, SettlementGuardOrder> guardOrders = new LinkedHashMap<>();
    private final Map<UUID, SettlementGuardOrder> temporaryGuardOrders = new LinkedHashMap<>();
    private final Map<UUID, SettlementWorkerSettings> workerSettings = new LinkedHashMap<>();
    private final Map<UUID, SettlementWorksitePlan> worksitePlans = new LinkedHashMap<>();
    private final Map<UUID, HiredProductionQueue> productionQueues = new LinkedHashMap<>();
    private final Map<UUID, com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue> transferQueues = new LinkedHashMap<>();
    private final Map<UUID, SettlementSupplyPolicy> supplyPolicies = new LinkedHashMap<>();
    private final Map<UUID, SettlementEquipmentPlan> equipmentPlans = new LinkedHashMap<>();
    private final Map<UUID, SettlementWorkerPool> workerPools = new LinkedHashMap<>();
    private final Map<UUID, Long> workerPoolCooldowns = new LinkedHashMap<>();
    private Map<UUID, UUID> workerPoolMembers;
    private Map<UUID, ManagedSupplyOrder> managedSupplyOrders;
    private Map<UUID, List<ManagedGathering>> managedGathering;
    private long supplyRevision;
    private final Map<UUID, SettlementProject> projects = new LinkedHashMap<>();
    private long constructionQueueRevision;
    static final int CONSTRUCTION_QUEUE_LIMIT = 128;
    private final Map<UUID, SettlementProject> projectHistory = new LinkedHashMap<>();
    private final Set<UUID> retiredProjects = new java.util.LinkedHashSet<>();
    private final java.util.ArrayDeque<UUID> projectMaintenance = new java.util.ArrayDeque<>();
    static final int PROJECT_HISTORY_LIMIT = 32;
    static final int PROJECT_MAINTENANCE_LIMIT = 8;
    SettlementStock.Catalog stockCatalog;
    final Map<SettlementFamilyRoutes.Key, List<BlockPos>> familyRoutes = new LinkedHashMap<>(64, 0.75F, true);
    private final Map<UUID, SettlementDepot> depots = new LinkedHashMap<>();
    private final Map<UUID, SettlementBuildingSurvey.Survey> surveys = new LinkedHashMap<>();
    private final Map<UUID, SettlementApplicant> applicants = new LinkedHashMap<>();
    private final Map<UUID, SettlementArrival> arrivals = new LinkedHashMap<>();
    private final Map<UUID, SettlementResidentState> residents = new LinkedHashMap<>();
    private final Map<UUID, SettlementHome> homes = new LinkedHashMap<>();
    private final Map<UUID, SettlementCourtship> courtships = new LinkedHashMap<>();
    private final Map<BlockPos, SettlementNavigationSection> navigation = new LinkedHashMap<>();
    private final Map<UUID, SettlementHomeRelease> homeReleases = new LinkedHashMap<>();
    private final Map<UUID, SettlementDeparture> departures = new LinkedHashMap<>();
    private final Map<UUID, SettlementWorkerState> workers = new LinkedHashMap<>();
    private final Map<UUID, SettlementLivestock> livestock = new LinkedHashMap<>();
    private final Map<UUID, SettlementHuntingTarget> huntingTargets = new LinkedHashMap<>();
    private final Map<UUID, SettlementProjectile> projectiles = new LinkedHashMap<>();
    private final Map<UUID, SettlementAnimalBirth> animalBirths = new LinkedHashMap<>();
    private final Map<UUID, SettlementItemDrop> itemDrops = new LinkedHashMap<>();
    private final Map<UUID, SettlementGroundItem> groundItems = new LinkedHashMap<>();
    private final Map<UUID, SettlementGroundExperience> groundExperience = new LinkedHashMap<>();
    private final Map<UUID, SettlementEntityDeath> entityDeaths = new LinkedHashMap<>();
    private final Map<BlockPos, SettlementFurnaceState> furnaces = new LinkedHashMap<>();
    private final Map<BlockPos, SettlementBrewingStandState> brewingStands = new LinkedHashMap<>();
    private final Map<BlockPos, SettlementFieldPlot> fields = new LinkedHashMap<>();
    private final Map<UUID, SettlementForest> forests = new LinkedHashMap<>();
    private final Map<UUID, SettlementMine> mines = new LinkedHashMap<>();
    private final Map<UUID, List<SettlementFishingSpot>> fishingSpots = new LinkedHashMap<>();
    private long nextDepartureAt;
    private long nextApplicantAt;
    private SettlementSupportState support = new SettlementSupportState();
    private SettlementEconomy economy = new SettlementEconomy(this::changed);
    private UUID leader;
    private AutonomousVillageState autonomous;

    public AutonomousVillageState autonomous() { return autonomous; }
    void autonomous(AutonomousVillageState state) { autonomous=state; changed(); }
    void acceptAutonomousLeadership(UUID player) {
        // Existing civic escrow/orders retain their issuer until naturally completed or cancelled.
        delegates.put(leader,EnumSet.allOf(SettlementPermission.class));
        autonomous=null; leader=player; changed();
    }
    private LeadershipOffer leadershipOffer;
    private SettlementStage stage = SettlementStage.CAMP;
    private int radius = SettlementStage.CAMP.radius();
    private SettlementTerritory territory;
    private long revision;
    private int cachedHousingCapacity = -1;
    private long housingTerritoryRevision = -1L;
    private Set<BlockPos> cachedHousingBeds = Set.of();
    private Map<BlockPos, UUID> cachedHomeOwners;
    private long simulationResumedAt;
    private boolean manualManagement;
    private final Set<UUID> manualDuties = new java.util.HashSet<>();
    private final Map<UUID, Long> manualDutyRevisions = new LinkedHashMap<>();

    SettlementRecord(VillageAllegianceId id, ResourceLocation dimension, BlockPos center,
                     UUID leader, long foundedAt, Runnable dirty) {
        this.id = Objects.requireNonNull(id, "id");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.center = Objects.requireNonNull(center, "center").immutable();
        this.leader = Objects.requireNonNull(leader, "leader");
        this.foundedAt = Math.max(0L, foundedAt);
        this.dirty = Objects.requireNonNull(dirty, "dirty");
        this.nextApplicantAt = this.foundedAt + 600L;
        this.territory = new SettlementTerritory(center);
        this.territory.start();
    }

    public VillageAllegianceId id() {
        return this.id;
    }

    public ResourceLocation dimension() {
        return this.dimension;
    }

    public BlockPos center() {
        return this.center;
    }

    public UUID leader() {
        return this.leader;
    }

    public long foundedAt() {
        return this.foundedAt;
    }

    long simulationResumedAt() {
        return this.simulationResumedAt;
    }

    void resumeSimulation(long now) {
        this.simulationResumedAt = now;
        changed();
    }

    public boolean manualManagement() {
        return this.manualManagement;
    }

    public boolean setManualManagement(UUID actor, boolean manual) {
        if (!this.leader.equals(actor)) return false;
        this.manualManagement = manual;
        changed();
        return true;
    }

    boolean manuallyStaffed(UUID resident) {
        return this.manualDuties.contains(resident);
    }

    void rememberManualDuty(UUID resident) {
        this.manualDuties.add(resident);
        this.manualDutyRevisions.put(resident, Math.addExact(this.revision, 1));
        changed();
    }

    long manualDutyRevision(UUID resident) {
        return this.manualDutyRevisions.getOrDefault(resident, -1L);
    }

    boolean returnToAutomaticStaffing(UUID actor, UUID resident) {
        if (!permits(actor, SettlementPermission.STAFF) || resident == null) return false;
        if (this.manualDuties.remove(resident)) {
            this.manualDutyRevisions.remove(resident);
            changed();
        }
        return true;
    }

    public long revision() {
        return this.revision;
    }

    public List<SettlementEvent> history() {
        return List.copyOf(this.history);
    }

    void recordEvent(long gameTime, SettlementEvent.Type type, String subject) {
        var event = SettlementEvent.create(gameTime, type, subject);
        if (this.history.size() == MAX_HISTORY) this.history.removeFirst();
        this.history.add(event);
        changed();
    }

    public SettlementStage stage() {
        return this.stage;
    }

    public int radius() {
        return Math.max(this.radius, this.territory.envelopeRadius(this.center));
    }

    public SettlementTerritory territory() { return this.territory; }

    public boolean contains(BlockPos position) { return this.territory.contains(position) && (autonomous == null || autonomous.contains(position)); }

    public boolean contains(BlockPos min, BlockPos max) { return this.territory.contains(min, max) && (autonomous == null || autonomous.contains(min,max)); }

    public boolean owns(SettlementArea area) { return area != null && contains(area.min(), area.max()); }

    boolean owns(SettlementProject project) {
        return contains(project.min(), project.max())
                && (project.workplace() == null || owns(project.workplace()))
                && (project.site() == null || owns(project.site().area()));
    }

    void migrateTerritory(Set<Long> plots, String conflict) {
        this.cachedHousingCapacity = -1;
        this.territory = new SettlementTerritory(this.center);
        this.territory.migrate(plots, this.stage, conflict);
        this.areas.replaceAll((id, area) -> effectiveArea(area));
        changed();
    }

    private SettlementArea effectiveArea(SettlementArea area) {
        return area == null || !this.territory.migrated() || owns(area) || !area.enabled() ? area
                : new SettlementArea(area.id(), area.type(), area.name(), area.min(), area.max(), area.priority(), false, area.crops());
    }

    public SettlementEconomy economy() {
        return this.economy;
    }

    public Map<UUID, List<SettlementFishingSpot>> fishingSpots() {
        return Collections.unmodifiableMap(this.fishingSpots);
    }

    void recordFishingSpots(UUID area, List<SettlementFishingSpot> spots) {
        if (spots.size() > SettlementFisheryService.MAX_SPOTS || spots.stream().anyMatch(spot -> !spot.area().equals(area))
                || spots.stream().map(SettlementFishingSpot::water).distinct().count() != spots.size()) {
            throw new IllegalArgumentException("Fishing sites exceed their limit or duplicate a water position");
        }
        if (spots.isEmpty()) {
            this.fishingSpots.remove(area);
        } else {
            this.fishingSpots.put(area, List.copyOf(spots));
        }
        changed();
    }

    public Map<UUID, SettlementMine> mines() {
        return Collections.unmodifiableMap(this.mines);
    }

    void recordMine(SettlementMine mine) {
        this.mines.put(mine.area(), mine);
        changed();
    }

    void removeMine(UUID area) {
        if (this.mines.remove(area) != null) changed();
    }

    public Map<UUID, SettlementForest> forests() {
        return Collections.unmodifiableMap(this.forests);
    }

    void recordForest(SettlementForest forest) {
        this.forests.put(forest.area(), forest);
        changed();
    }

    void removeForest(UUID area) {
        if (this.forests.remove(area) != null) changed();
    }

    public Map<BlockPos, SettlementFieldPlot> fields() {
        return Collections.unmodifiableMap(this.fields);
    }

    void recordField(SettlementFieldPlot plot) {
        this.fields.put(plot.position(), plot);
        changed();
    }

    void removeField(BlockPos position) {
        if (this.fields.remove(position) != null) {
            changed();
        }
    }

    public Map<BlockPos, SettlementFurnaceState> furnaces() {
        return Collections.unmodifiableMap(this.furnaces);
    }

    void recordFurnace(SettlementFurnaceState furnace) {
        this.furnaces.put(furnace.position(), furnace);
        changed();
    }

    void removeFurnace(BlockPos position) {
        if (this.furnaces.remove(position) != null) {
            changed();
        }
    }

    public Map<BlockPos, SettlementBrewingStandState> brewingStands() {
        return Collections.unmodifiableMap(this.brewingStands);
    }

    void recordBrewingStand(SettlementBrewingStandState stand) {
        this.brewingStands.put(stand.position(), stand);
        changed();
    }

    void removeBrewingStand(BlockPos position) {
        if (this.brewingStands.remove(position) != null) {
            changed();
        }
    }

    public Map<UUID, SettlementWorkerState> workers() {
        return Collections.unmodifiableMap(this.workers);
    }

    void recordWorker(SettlementWorkerState worker) {
        this.workers.put(worker.id(), worker);
        changed();
    }

    void removeWorker(UUID worker) {
        this.productionQueues.values().forEach(queue -> queue.detach(worker));
        this.transferQueues.values().forEach(queue -> queue.detach(worker));
        cancelProjects(worker);
        com.jvn.villagerretaliation.inventory.AssignedStorageService.releaseCourierClaims(worker);
        if (this.workers.remove(worker) != null) {
            changed();
        }
    }

    public Map<UUID, SettlementDeparture> departures() {
        return Collections.unmodifiableMap(this.departures);
    }

    public long nextDepartureAt() {
        return this.nextDepartureAt;
    }

    void scheduleDeparture(long gameTime) {
        this.nextDepartureAt = Math.max(0L, gameTime);
        changed();
    }

    void recordDeparture(SettlementDeparture departure) {
        this.departures.put(departure.id(), departure);
        changed();
    }

    void removeDeparture(UUID resident) {
        if (this.departures.remove(resident) != null) {
            changed();
        }
    }

    public Map<UUID, SettlementResidentState> residents() {
        return Collections.unmodifiableMap(this.residents);
    }

    void recordResident(SettlementResidentState resident) {
        this.residents.put(resident.id(), resident);
        changed();
    }

    void removeResident(UUID resident) {
        boolean removed = this.residents.remove(resident) != null;
        removed |= this.manualDuties.remove(resident);
        removed |= this.manualDutyRevisions.remove(resident) != null;
        removed |= this.workerPoolCooldowns.remove(resident) != null;
        for (var pool : List.copyOf(this.workerPools.values())) {
            if (!pool.configuration().residents().contains(resident)) continue;
            var configuration = new SettlementWorkerPool.Configuration(pool.configuration().name(),
                    pool.configuration().residents().stream().filter(id -> !id.equals(resident)).toList(), pool.configuration().worksites());
            boolean empty = pool.state() == SettlementWorkerPool.State.ACTIVE && !configuration.staffable();
            this.workerPools.put(pool.id(), new SettlementWorkerPool(pool.id(), configuration, pool.authority(),
                    Math.addExact(this.revision, 1), empty ? SettlementWorkerPool.State.SUSPENDED : pool.state(),
                    empty ? "pool_no_residents" : pool.blocker(), pool.history()));
            this.workerPoolMembers = null;
            changed();
        }
        removed |= this.homes.remove(resident) != null;
        this.cachedHomeOwners = null;
        removed |= this.courtships.values().removeIf(attempt -> attempt.involves(resident));
        if (!this.departures.containsKey(resident) && this.arrivals.remove(resident) != null) {
            removed = true;
        }
        if (removed) {
            changed();
        }
    }

    Map<UUID, SettlementCourtship> courtships() {
        return Collections.unmodifiableMap(this.courtships);
    }

    void recordCourtship(SettlementCourtship attempt) {
        if (this.courtships.values().stream().anyMatch(existing -> !existing.first().equals(attempt.first())
                && (existing.involves(attempt.first()) || existing.involves(attempt.second()) || existing.bed().equals(attempt.bed())))) {
            throw new IllegalArgumentException("Courtship duplicates an occupied parent or bed");
        }
        this.courtships.put(attempt.first(), attempt);
        changed();
    }

    void removeCourtship(UUID resident) {
        if (this.courtships.values().removeIf(attempt -> attempt.involves(resident))) changed();
    }

    Map<BlockPos, SettlementNavigationSection> navigation() {
        return Collections.unmodifiableMap(this.navigation);
    }

    void retainNavigation(Set<BlockPos> wanted) {
        if (this.navigation.keySet().retainAll(wanted)) changed();
    }

    void recordNavigation(SettlementNavigationSection section) {
        this.navigation.put(section.origin(), section);
        changed();
    }

    public Map<UUID, SettlementHome> homes() {
        return Collections.unmodifiableMap(this.homes);
    }

    UUID homeOwner(BlockPos bed) {
        if (this.cachedHomeOwners == null) {
            var owners = new java.util.HashMap<BlockPos, UUID>();
            for (var home : this.homes.values()) {
                if (home.bed() != null) owners.put(home.bed(), home.resident());
            }
            this.cachedHomeOwners = Map.copyOf(owners);
        }
        return this.cachedHomeOwners.get(bed);
    }

    boolean assignHome(UUID resident, BlockPos bed) {
        if (resident == null || bed != null && homeOwner(bed) != null && !resident.equals(homeOwner(bed))) return false;
        var previous = this.homes.get(resident);
        if (previous != null && Objects.equals(previous.bed(), bed)) return true;
        this.homes.put(resident, new SettlementHome(resident, bed, previous == null ? 1L : Math.incrementExact(previous.version())));
        this.cachedHomeOwners = null;
        changed();
        return true;
    }

    Map<UUID, SettlementHomeRelease> homeReleases() {
        return Collections.unmodifiableMap(this.homeReleases);
    }

    void releaseHome(UUID id, UUID resident, net.minecraft.core.GlobalPos home) {
        var release = new SettlementHomeRelease(id, resident, home);
        if (this.homeReleases.putIfAbsent(id, release) == null) changed();
    }

    void removeHomeRelease(UUID id) {
        if (this.homeReleases.remove(id) != null) changed();
    }

    public Map<UUID, SettlementApplicant> applicants() {
        return Collections.unmodifiableMap(this.applicants);
    }

    public Map<UUID, SettlementLivestock> livestock() {
        return Collections.unmodifiableMap(this.livestock);
    }

    void recordLivestock(SettlementLivestock animal) {
        this.livestock.put(animal.id(), animal);
        changed();
    }

    void removeLivestock(UUID animal) {
        if (this.livestock.remove(animal) != null) changed();
    }

    Map<UUID, SettlementAnimalBirth> animalBirths() {
        return Collections.unmodifiableMap(this.animalBirths);
    }

    void recordAnimalBirth(SettlementAnimalBirth birth) {
        if (this.animalBirths.putIfAbsent(birth.id(), birth) != null) {
            throw new IllegalArgumentException("Birth duplicates a livestock identity");
        }
        changed();
    }

    void removeAnimalBirth(UUID id) {
        if (this.animalBirths.remove(id) != null) changed();
    }

    Map<UUID, SettlementItemDrop> itemDrops() {
        return Collections.unmodifiableMap(this.itemDrops);
    }

    void recordItemDrop(SettlementItemDrop drop) {
        if (this.itemDrops.putIfAbsent(drop.id(), drop) != null) {
            throw new IllegalArgumentException("Pending item drops duplicate an identity");
        }
        changed();
    }

    void removeItemDrop(UUID id) {
        if (this.itemDrops.remove(id) != null) changed();
    }

    Map<UUID, SettlementGroundItem> groundItems() {
        return Collections.unmodifiableMap(this.groundItems);
    }

    void recordGroundItem(SettlementGroundItem item) {
        if (this.groundItems.putIfAbsent(item.drop().id(), item) != null) {
            throw new IllegalArgumentException("Ground items duplicate an identity");
        }
        changed();
    }

    void removeGroundItem(UUID id) {
        if (this.groundItems.remove(id) != null) changed();
    }

    Map<UUID, SettlementGroundExperience> groundExperience() {
        return Collections.unmodifiableMap(this.groundExperience);
    }

    void recordGroundExperience(SettlementGroundExperience experience) {
        if (this.groundExperience.putIfAbsent(experience.id(), experience) != null) {
            throw new IllegalArgumentException("Ground experience duplicates an identity");
        }
        changed();
    }

    void removeGroundExperience(UUID id) {
        if (this.groundExperience.remove(id) != null) changed();
    }

    Map<UUID, SettlementProjectile> projectiles() {
        return Collections.unmodifiableMap(this.projectiles);
    }

    void recordProjectile(SettlementProjectile projectile) {
        if (this.projectiles.putIfAbsent(projectile.id(), projectile) != null) {
            throw new IllegalArgumentException("Settlement projectiles duplicate an identity");
        }
        changed();
    }

    void removeProjectile(UUID id) {
        if (this.projectiles.remove(id) != null) changed();
    }

    Map<UUID, SettlementHuntingTarget> huntingTargets() {
        return Collections.unmodifiableMap(this.huntingTargets);
    }

    void recordHuntingTarget(SettlementHuntingTarget target) {
        this.huntingTargets.put(target.id(), target);
        changed();
    }

    void removeHuntingTarget(UUID id) {
        if (this.huntingTargets.remove(id) != null) changed();
    }

    Map<UUID, SettlementEntityDeath> entityDeaths() {
        return Collections.unmodifiableMap(this.entityDeaths);
    }

    void recordEntityDeath(SettlementEntityDeath death) {
        if (this.entityDeaths.putIfAbsent(death.id(), death) != null) {
            throw new IllegalArgumentException("Entity deaths duplicate an identity");
        }
        SettlementEntityDeathService.recorded(death.id());
        changed();
    }

    void removeEntityDeath(UUID id) {
        if (this.entityDeaths.remove(id) != null) changed();
    }

    public Map<UUID, SettlementArrival> arrivals() {
        return Collections.unmodifiableMap(this.arrivals);
    }

    void recordArrival(SettlementArrival arrival) {
        if (this.arrivals.putIfAbsent(arrival.id(), arrival) != null) {
            throw new IllegalArgumentException("Arrival duplicates a resident identity");
        }
        changed();
    }

    void removeArrival(UUID id) {
        if (this.arrivals.remove(id) != null) {
            changed();
        }
    }

    public long nextApplicantAt() {
        return this.nextApplicantAt;
    }

    void scheduleApplicant(long gameTime) {
        this.nextApplicantAt = Math.max(0L, gameTime);
        changed();
    }

    boolean putApplicant(SettlementApplicant applicant) {
        if (!this.applicants.containsKey(applicant.id()) && this.applicants.size() >= MAX_APPLICANTS) {
            return false;
        }
        this.applicants.put(applicant.id(), applicant);
        changed();
        return true;
    }

    boolean removeApplicant(UUID id) {
        if (this.applicants.remove(id) == null) {
            return false;
        }
        changed();
        return true;
    }

    public Map<UUID, Set<SettlementPermission>> delegates() {
        return Collections.unmodifiableMap(this.delegates);
    }

    public Map<UUID, SettlementArea> areas() {
        return Collections.unmodifiableMap(this.areas);
    }

    public SettlementArea workplace(UUID id) {
        if (id == null) return null;
        var area = this.areas.get(id);
        if (area != null) return effectiveArea(area);
        return this.projects.values().stream().filter(project -> project.workplace() != null
                && project.area().equals(id) && (project.active() || project.site() != null)).map(SettlementProject::workplace).map(this::effectiveArea).findFirst().orElse(null);
    }

    public java.util.List<SettlementArea> workplaces() {
        var result = new java.util.ArrayList<>(this.areas.values());
        this.projects.values().stream().filter(project -> project.workplace() != null && project.reservesFootprint())
                .map(SettlementProject::workplace).forEach(result::add);
        return result.stream().map(this::effectiveArea).toList();
    }

    public Map<UUID, UUID> duties() {
        return Collections.unmodifiableMap(this.duties);
    }

    public Map<UUID, SettlementGuardOrder> guardOrders() {
        return Collections.unmodifiableMap(this.guardOrders);
    }

    public Map<UUID, SettlementGuardOrder> temporaryGuardOrders() {
        return Collections.unmodifiableMap(this.temporaryGuardOrders);
    }

    boolean setTemporaryGuardOrder(UUID actor, UUID resident, SettlementGuardOrder order) {
        var area = workplace(this.duties.get(resident));
        if (!permits(actor, SettlementPermission.STAFF) || area == null || area.type() != SettlementAreaType.GUARD_POST
                || order != null && (order.points().isEmpty() || !order.within(this))) return false;
        var previous = order == null ? this.temporaryGuardOrders.remove(resident) : this.temporaryGuardOrders.put(resident, order);
        if (!Objects.equals(previous, order)) changed();
        return true;
    }

    boolean setGuardOrder(UUID actor, UUID resident, SettlementGuardOrder order) {
        var area = workplace(this.duties.get(resident));
        if (!permits(actor, SettlementPermission.STAFF) || area == null || !SettlementGuardOrder.supports(area.type().role())
                || order != null && (!order.within(this) || !order.validFor(area.type().role()))) {
            return false;
        }
        var previous = order == null ? this.guardOrders.remove(resident) : this.guardOrders.put(resident, order);
        if (!Objects.equals(previous, order)) {
            changed();
        }
        return true;
    }

    public Map<UUID, SettlementProject> projects() {
        return Collections.unmodifiableMap(this.projects);
    }

    public long constructionQueueRevision() { return this.constructionQueueRevision; }

    boolean queueProject(UUID actor, SettlementProject project) {
        var area = project.workplace() == null ? workplace(project.area()) : project.workplace();
        if (!permits(actor, SettlementPermission.BUILD) || !project.pending() || !actor.equals(project.authority())
                || !project.town().equals(this.id.value()) || knowsProject(project.id()) || !project.within(area) || !owns(project)
                || this.stage.projects() == 0 || this.projects.values().stream().filter(SettlementProject::pending).count() >= CONSTRUCTION_QUEUE_LIMIT
                || this.projects.values().stream().anyMatch(existing -> existing.reservesFootprint() && existing.overlaps(project))) return false;
        this.projects.put(project.id(), project);
        this.projectMaintenance.addLast(project.id());
        this.constructionQueueRevision++;
        changed();
        return true;
    }

    boolean activateProject(SettlementProject project, UUID builder) {
        if (this.projects.get(project.id()) != project || !project.pending() || !permits(project.authority(), SettlementPermission.BUILD)
                || activeProject(builder) != null || !owns(project)
                || project.workplace() == null && !project.area().equals(this.duties.get(builder))
                || this.projects.values().stream().filter(SettlementProject::active).count() >= this.stage.projects()
                || project.fee() > 0 && !this.economy.reserveConstruction(project.fee())) return false;
        project.activate(this, builder);
        if (project.workplace() != null) {
            this.duties.put(builder, project.area());
            this.workerSettings.remove(builder);
        }
        this.constructionQueueRevision++;
        changed();
        return true;
    }

    /** Move only pending work, relative to another pending project, using a stale-write guard. */
    boolean reorderProject(UUID actor, UUID projectId, UUID beforeId, long expected) {
        var project = this.projects.get(projectId);
        var before = this.projects.get(beforeId);
        if (!permits(actor, SettlementPermission.BUILD) || expected != this.constructionQueueRevision
                || project == null || !project.pending() || before == null || !before.pending() || project == before) return false;
        var pending = new java.util.ArrayList<>(this.projects.values().stream().filter(SettlementProject::pending).toList());
        int destination = pending.indexOf(before);
        pending.remove(project);
        pending.add(destination, project);
        var iterator = pending.iterator();
        var reordered = new LinkedHashMap<UUID, SettlementProject>();
        for (var entry : this.projects.values()) {
            var next = entry.pending() ? iterator.next() : entry;
            reordered.put(next.id(), next);
        }
        this.projects.clear();
        this.projects.putAll(reordered);
        this.constructionQueueRevision++;
        changed();
        return true;
    }

    public Map<UUID, SettlementProject> projectHistory() {
        return Collections.unmodifiableMap(this.projectHistory);
    }

    public SettlementProject project(UUID id) {
        var project = this.projects.get(id);
        return project == null ? this.projectHistory.get(id) : project;
    }

    boolean knowsProject(UUID id) {
        return this.projects.containsKey(id) || this.projectHistory.containsKey(id) || this.retiredProjects.contains(id);
    }

    int maintainProjects() {
        int visited = Math.min(PROJECT_MAINTENANCE_LIMIT, this.projectMaintenance.size());
        for (int index = 0; index < visited; index++) {
            var id = this.projectMaintenance.removeFirst();
            var project = this.projects.get(id);
            if (project == null) continue;
            if (!project.settled(this.residents.get(project.builder()))) {
                this.projectMaintenance.addLast(id);
                continue;
            }
            clearProjectSite(project);
            this.projects.remove(id);
            project.summarize();
            this.projectHistory.put(id, project);
            trimProjectHistory();
            changed();
        }
        return visited;
    }

    private void trimProjectHistory() {
        while (this.projectHistory.size() > PROJECT_HISTORY_LIMIT) {
            var oldest = this.projectHistory.keySet().iterator().next();
            this.projectHistory.remove(oldest);
            this.retiredProjects.add(oldest);
        }
    }

    public SettlementProject activeProject(UUID builder) {
        return this.projects.values().stream().filter(project -> project.active() && project.builder().equals(builder)).findFirst().orElse(null);
    }

    public SettlementProject assignedProject(UUID worker) {
        var home = workplace(this.duties.get(worker));
        if (home == null || !home.enabled() || home.type() != SettlementAreaType.CONSTRUCTION) return null;
        return this.projects.values().stream().filter(project -> project.active() && project.includes(worker) && owns(project)
                        && workplace(project.area()) != null && workplace(project.area()).enabled()
                        && (project.builder().equals(worker) || this.worksitePlans.containsKey(home.id())))
                .sorted(java.util.Comparator.comparingInt(SettlementProject::priority).reversed()
                        .thenComparingLong(SettlementProject::startedAt)
                        .thenComparing(SettlementProject::id)).findFirst().orElse(null);
    }

    boolean startProject(UUID actor, SettlementProject project) {
        var area = project.workplace() == null ? workplace(project.area()) : project.workplace();
        if (!permits(actor, SettlementPermission.BUILD) || !project.active() || !project.town().equals(this.id.value())
                || knowsProject(project.id()) || !project.within(area) || !owns(project)
                || project.workplace() == null && !project.area().equals(this.duties.get(project.builder()))
                || project.workplace() != null && !Objects.equals(project.previousArea(), this.duties.get(project.builder()))
                || activeProject(project.builder()) != null
                || this.projects.values().stream().filter(SettlementProject::active).count() >= this.stage.projects()
                || this.projects.values().stream().anyMatch(existing -> existing.reservesFootprint() && existing.overlaps(project))) {
            return false;
        }
        if (project.fee() > 0 && !this.economy.reserveConstruction(project.fee())) return false;
        this.projects.put(project.id(), project);
        this.projectMaintenance.addLast(project.id());
        if (project.workplace() != null) {
            this.duties.put(project.builder(), project.area());
            this.workerSettings.remove(project.builder());
        }
        changed();
        return true;
    }

    void clearProjectSite(SettlementProject project) {
        project.clearSite();
        if (project.workplace() != null) {
            this.surveys.remove(project.area());
            this.cachedHousingCapacity = -1;
        }
        changed();
    }

    void finishProject(UUID id, SettlementProject.Status status) {
        var project = this.projects.get(id);
        if (project != null && project.finish(status)) {
            this.constructionQueueRevision++;
            if (status == SettlementProject.Status.COMPLETED) project.earnPayment();
            else this.economy.credit(com.jvn.villagerretaliation.sell.CurrencyAmount.of(project.refundPayment(), 1));
            if (project.workplace() != null && project.area().equals(this.duties.get(project.builder()))) {
                this.duties.remove(project.builder());
                this.workerSettings.remove(project.builder());
                var previous = this.areas.get(project.previousArea());
                if (previous != null && previous.type() == SettlementAreaType.CONSTRUCTION) {
                    this.duties.put(project.builder(), previous.id());
                    if (project.previousSettings() != null) this.workerSettings.put(project.builder(), project.previousSettings());
                }
            }
            changed();
        }
    }

    private void cancelProjects(UUID builder) {
        for (var other : this.projects.values()) if (other.active() && other.detachAssistant(builder)) changed();
        var project = activeProject(builder);
        if (project != null) {
            if (project.waitFor("builder")) changed();
        }
    }

    public Map<UUID, SettlementWorkerSettings> workerSettings() {
        return Collections.unmodifiableMap(this.workerSettings);
    }

    public Map<UUID, SettlementWorksitePlan> worksitePlans() {
        return Collections.unmodifiableMap(this.worksitePlans);
    }

    public Map<UUID, HiredProductionQueue> productionQueues() {
        return Collections.unmodifiableMap(this.productionQueues);
    }

    public Map<UUID, com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue> transferQueues() {
        return Collections.unmodifiableMap(this.transferQueues);
    }

    public Map<UUID, SettlementSupplyPolicy> supplyPolicies() {
        return Collections.unmodifiableMap(this.supplyPolicies);
    }

    public Map<UUID, SettlementWorkerPool> workerPools() {
        return Collections.unmodifiableMap(this.workerPools);
    }

    public UUID workerPoolFor(UUID resident) {
        if (this.workerPoolMembers == null) {
            this.workerPoolMembers = new java.util.HashMap<>();
            this.workerPools.forEach((id, pool) -> pool.configuration().residents().forEach(member -> this.workerPoolMembers.put(member, id)));
        }
        return this.workerPoolMembers.get(resident);
    }

    String workerPoolConfigurationFailure(UUID id, SettlementWorkerPool.Configuration configuration, boolean active) {
        if (configuration.residents().stream().anyMatch(resident -> !this.residents.containsKey(resident))) return "pool_resident_missing";
        if (configuration.residents().stream().anyMatch(resident -> workerPoolFor(resident) != null && !id.equals(workerPoolFor(resident))))
            return "pool_resident_enrolled";
        if (!active) return "";
        if (configuration.residents().isEmpty()) return "pool_no_residents";
        if (!configuration.staffable()) return "pool_no_worksites";
        for (var staffing : configuration.worksites()) {
            var area = this.areas.get(staffing.area());
            var plan = this.worksitePlans.get(staffing.area());
            if (area == null || area.type().role() == null || !area.enabled() || !owns(area)
                    || plan != null && !plan.settings().enabled()) return "pool_worksite_unavailable";
        }
        return "";
    }

    boolean configureWorkerPool(UUID actor, UUID id, long expected, SettlementWorkerPool.Configuration configuration, boolean active) {
        if (id == null || configuration == null || !permits(actor, SettlementPermission.STAFF) || this.revision == Long.MAX_VALUE)
            return false;
        var previous = this.workerPools.get(id);
        if (expected != (previous == null ? -1 : previous.revision())
                || previous == null && this.workerPools.size() >= SettlementWorkerPool.MAX_POOLS
                || !workerPoolConfigurationFailure(id, configuration, active).isEmpty()) return false;
        this.workerPools.put(id, new SettlementWorkerPool(id, configuration, actor, Math.addExact(this.revision, 1),
                active ? SettlementWorkerPool.State.ACTIVE : SettlementWorkerPool.State.PAUSED, "",
                previous == null ? List.of() : previous.history()));
        this.workerPoolMembers = null;
        changed();
        return true;
    }

    boolean changeWorkerPool(UUID actor, UUID id, long expected, boolean active) {
        var pool = this.workerPools.get(id);
        if (pool == null || expected != pool.revision() || !permits(actor, SettlementPermission.STAFF)
                || this.revision == Long.MAX_VALUE || !workerPoolConfigurationFailure(id, pool.configuration(), active).isEmpty()) return false;
        var state = active ? SettlementWorkerPool.State.ACTIVE : SettlementWorkerPool.State.PAUSED;
        if (pool.state() == state && pool.authority().equals(actor)) return true;
        this.workerPools.put(id, pool.state(state, actor, Math.addExact(this.revision, 1), ""));
        changed();
        return true;
    }

    boolean removeWorkerPool(UUID actor, UUID id, long expected) {
        var pool = this.workerPools.get(id);
        if (pool == null || expected != pool.revision() || !permits(actor, SettlementPermission.STAFF)) return false;
        this.workerPools.remove(id);
        this.workerPoolMembers = null;
        changed();
        return true;
    }

    void suspendWorkerPool(UUID id, String blocker) {
        var pool = this.workerPools.get(id);
        if (pool == null || pool.state() != SettlementWorkerPool.State.ACTIVE) return;
        this.workerPools.put(id, pool.state(SettlementWorkerPool.State.SUSPENDED, pool.authority(), Math.addExact(this.revision, 1), blocker));
        changed();
    }

    void validateWorkerPoolAuthorities() {
        for (var pool : List.copyOf(this.workerPools.values()))
            if (!permits(pool.authority(), SettlementPermission.STAFF)) suspendWorkerPool(pool.id(), "pool_permission_changed");
    }

    public long workerPoolCooldown(UUID resident, long now) {
        var previous = this.workerPoolCooldowns.get(resident);
        if (previous == null) return this.workerPoolCooldowns.size() >= SettlementWorkerPool.MAX_POOLS * SettlementWorkerPool.MAX_RESIDENTS
                ? SettlementWorkerPool.COOLDOWN_TICKS : 0;
        return now < previous ? SettlementWorkerPool.COOLDOWN_TICKS : Math.max(0, SettlementWorkerPool.COOLDOWN_TICKS - (now - previous));
    }

    void reconcileWorkerPoolCooldowns(long now) {
        if (now < 0) throw new IllegalArgumentException("Invalid staffing time");
        boolean removed = this.workerPoolCooldowns.entrySet().removeIf(entry -> now >= entry.getValue()
                && now - entry.getValue() >= SettlementWorkerPool.COOLDOWN_TICKS);
        boolean rolledBack = this.workerPoolCooldowns.values().stream().anyMatch(previous -> previous > now);
        if (rolledBack) this.workerPoolCooldowns.replaceAll((resident, previous) -> Math.min(previous, now));
        if (removed || rolledBack) changed();
    }

    void recordWorkerPoolMove(SettlementWorkerPool pool, SettlementWorkerPool.Move move) {
        if (!pool.authorized(this) || !pool.configuration().residents().contains(move.resident())
                || !move.to().equals(this.duties.get(move.resident())) || workerPoolCooldown(move.resident(), move.at()) > 0)
            throw new IllegalStateException("Staffing move lost its pool authorization");
        this.workerPools.put(pool.id(), pool.moved(move));
        this.workerPoolCooldowns.put(move.resident(), move.at());
        changed();
    }

    public Map<UUID, SettlementEquipmentPlan> equipmentPlans() {
        return Collections.unmodifiableMap(this.equipmentPlans);
    }

    boolean setEquipmentPlan(UUID actor, UUID areaId, SettlementEquipmentPlan.Configuration configuration, long expectedRevision) {
        var area = this.areas.get(areaId);
        var previous = this.equipmentPlans.get(areaId);
        if (!permits(actor, SettlementPermission.STAFF) || area == null || configuration == null
                || !configuration.validFor(area.type().role()) || !this.worksitePlans.containsKey(areaId)
                || this.revision == Long.MAX_VALUE || expectedRevision != (previous == null ? -1 : previous.revision())) return false;
        this.equipmentPlans.put(areaId, new SettlementEquipmentPlan(configuration, actor, Math.addExact(this.revision, 1), ""));
        supplyChanged();
        return true;
    }

    boolean removeEquipmentPlan(UUID actor, UUID areaId, long expectedRevision) {
        var plan = this.equipmentPlans.get(areaId);
        if (!permits(actor, SettlementPermission.STAFF) || plan == null || plan.revision() != expectedRevision) return false;
        this.equipmentPlans.remove(areaId);
        supplyChanged();
        return true;
    }

    void validateEquipmentAuthority(UUID areaId) {
        var plan = this.equipmentPlans.get(areaId);
        if (plan != null && plan.active() && !plan.authorized(this)) {
            this.equipmentPlans.put(areaId, plan.suspend());
            supplyChanged();
        }
    }

    public long supplyRevision() { return this.supplyRevision; }

    void supplyChanged() {
        this.supplyRevision = Math.addExact(this.supplyRevision, 1);
        this.managedSupplyOrders = null;
        this.managedGathering = null;
        changed();
    }

    ManagedSupplyOrder managedSupplyOrder(UUID order) {
        if (this.managedSupplyOrders == null) {
            var index = new LinkedHashMap<UUID, ManagedSupplyOrder>();
            for (var policy : this.supplyPolicies.values()) for (var binding : policy.bindings())
                if (binding.order() != null) index.put(binding.order(), new ManagedSupplyOrder(policy, binding.id()));
            this.managedSupplyOrders = Map.copyOf(index);
        }
        return this.managedSupplyOrders.get(order);
    }

    record ManagedSupplyOrder(SettlementSupplyPolicy policy, UUID binding) { }

    List<ManagedGathering> managedGathering(UUID area) {
        if (this.managedGathering == null) {
            var index = new LinkedHashMap<UUID, List<ManagedGathering>>();
            for (var policy : this.supplyPolicies.values()) {
                var bound = new java.util.HashSet<SettlementSupplyPolicy.Step>();
                for (var binding : policy.bindings()) if (binding.step().kind() == SettlementSupplyPolicy.StepKind.GATHER) {
                    bound.add(binding.step());
                    index.computeIfAbsent(binding.step().area(), ignored -> new ArrayList<>())
                            .add(new ManagedGathering(policy, binding.step(), binding));
                }
                if (policy.state() == SettlementSupplyPolicy.State.ACTIVE) for (var step : policy.configuration().steps())
                    if (step.kind() == SettlementSupplyPolicy.StepKind.GATHER && !bound.contains(step))
                        index.computeIfAbsent(step.area(), ignored -> new ArrayList<>()).add(new ManagedGathering(policy, step, null));
            }
            var copy = new LinkedHashMap<UUID, List<ManagedGathering>>();
            index.forEach((id, entries) -> copy.put(id, List.copyOf(entries)));
            this.managedGathering = Map.copyOf(copy);
        }
        return this.managedGathering.getOrDefault(area, List.of());
    }

    record ManagedGathering(SettlementSupplyPolicy policy, SettlementSupplyPolicy.Step step, SettlementSupplyPolicy.Binding binding) { }

    boolean addSupplyPolicy(SettlementSupplyPolicy policy) {
        if (this.supplyPolicies.size() >= SettlementSupplyPolicy.MAX_POLICIES
                || this.supplyPolicies.putIfAbsent(policy.id(), policy) != null) return false;
        supplyChanged();
        return true;
    }

    boolean removeSupplyPolicy(UUID id) {
        var policy = this.supplyPolicies.get(id);
        if (policy == null || !policy.bindings().isEmpty()) return false;
        this.supplyPolicies.remove(id);
        supplyChanged();
        return true;
    }

    com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue beginTransfers(UUID actor, UUID areaId) {
        var area = workplace(areaId);
        if (!permits(actor, SettlementPermission.STAFF) || !permits(actor, SettlementPermission.LOGISTICS)
                || area == null || area.type() != SettlementAreaType.DEPOT || !this.worksitePlans.containsKey(areaId)) return null;
        var queue = this.transferQueues.get(areaId);
        if (queue == null) {
            queue = new com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue(areaId,
                    com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Scope.WORKSITE, ignored -> changed());
            SettlementPolicyExecution.configure(this, queue);
            this.transferQueues.put(areaId, queue);
            changed();
        }
        return queue;
    }

    HiredProductionQueue beginProduction(UUID actor, UUID areaId) {
        var area = workplace(areaId);
        if (!permits(actor, SettlementPermission.STAFF) || area == null || !this.worksitePlans.containsKey(areaId)
                || area.type() != SettlementAreaType.WORKSHOP && area.type() != SettlementAreaType.KITCHEN
                    && area.type() != SettlementAreaType.SMELTERY && area.type() != SettlementAreaType.BREWERY) return null;
        var queue = this.productionQueues.get(areaId);
        if (queue == null) {
            queue = new HiredProductionQueue(areaId, HiredProductionQueue.Scope.WORKSITE, area.type().role(), ignored -> changed());
            SettlementPolicyExecution.configure(this, queue);
            this.productionQueues.put(areaId, queue);
            changed();
        }
        return queue;
    }

    void installProductionMigration(UUID area, HiredProductionQueue queue, Map<UUID, SettlementWorkerState> workers) {
        SettlementPolicyExecution.configure(this, queue);
        this.productionQueues.put(area, queue);
        this.workers.putAll(workers);
        changed();
    }

    public SettlementWorkerSettings effectiveWorkerSettings(UUID resident) {
        var settings = this.workerSettings.get(resident);
        var plan = this.worksitePlans.get(this.duties.get(resident));
        return plan == null ? settings : settings == null ? plan.settings().withOverrides(0) : settings.resolve(plan.settings());
    }

    boolean setWorksitePlan(UUID actor, UUID areaId, SettlementWorksitePlan plan) {
        var area = this.areas.get(areaId);
        if (!permits(actor, SettlementPermission.STAFF) || area == null || plan == null
                || area.type().role() != plan.settings().role() || plan.route() != null && !plan.route().within(this)
                || !plan.settings().enabled() && this.projects.values().stream().anyMatch(project -> project.active() && project.area().equals(areaId))) return false;
        if (!plan.equals(this.worksitePlans.put(areaId, plan))) changed();
        return true;
    }

    boolean setWorkerSettings(UUID actor, UUID resident, SettlementWorkerSettings settings) {
        var area = workplace(this.duties.get(resident));
        if (!permits(actor, SettlementPermission.STAFF) || area == null || settings == null || area.type().role() != settings.role()
                || !this.worksitePlans.containsKey(area.id()) && settings.overrides() != SettlementWorkerSettings.allOverrides(settings.role())) {
            return false;
        }
        if (!settings.equals(this.workerSettings.put(resident, settings))) {
            changed();
        }
        return true;
    }

    boolean assignDuty(UUID resident, UUID area) {
        SettlementArea target = this.areas.get(area);
        if (resident == null || target == null || target.type().role() == null
                || activeProject(resident) != null && !area.equals(this.duties.get(resident))) {
            return false;
        }
        UUID previous = this.duties.put(resident, area);
        if (!area.equals(previous)) {
            var queue = this.productionQueues.get(previous);
            if (queue != null) queue.detach(resident);
            var transfers = this.transferQueues.get(previous);
            if (transfers != null) transfers.detach(resident);
            com.jvn.villagerretaliation.inventory.AssignedStorageService.releaseCourierClaims(resident);
            cancelProjects(resident);
            cancelBrewing(resident);
            this.guardOrders.remove(resident);
            this.temporaryGuardOrders.remove(resident);
            this.workerSettings.remove(resident);
            if (this.manualDuties.contains(resident)) this.manualDutyRevisions.put(resident, Math.addExact(this.revision, 1));
            changed();
        }
        return true;
    }

    boolean removeDuty(UUID resident) {
        UUID previous = this.duties.remove(resident);
        if (previous == null) {
            return false;
        }
        var queue = this.productionQueues.get(previous);
        if (queue != null) queue.detach(resident);
        var transfers = this.transferQueues.get(previous);
        if (transfers != null) transfers.detach(resident);
        com.jvn.villagerretaliation.inventory.AssignedStorageService.releaseCourierClaims(resident);
        cancelProjects(resident);
        cancelBrewing(resident);
        this.guardOrders.remove(resident);
        this.temporaryGuardOrders.remove(resident);
        this.workerSettings.remove(resident);
        if (this.manualDuties.contains(resident)) this.manualDutyRevisions.put(resident, Math.addExact(this.revision, 1));
        changed();
        return true;
    }

    private void cancelBrewing(UUID resident) {
        var worker = this.workers.get(resident);
        if (worker != null && worker.role() == com.jvn.villagerretaliation.interaction.HiredVillagerRole.BREWING) {
            worker.brewingOrder(null);
        }
    }

    public Map<UUID, SettlementDepot> depots() {
        return Collections.unmodifiableMap(this.depots);
    }

    SettlementDepot registerDepot(UUID actor, UUID areaId, BlockPos position, SettlementDepotPurpose purpose) {
        SettlementArea area = this.areas.get(areaId);
        if (!permits(actor, SettlementPermission.LOGISTICS) || area == null || purpose == null
                || area.type() == SettlementAreaType.HOME || !area.contains(position) || !owns(area)
                || this.depots.size() >= this.stage.depots()
                || this.depots.values().stream().anyMatch(depot -> depot.position().equals(position))) {
            return null;
        }
        SettlementDepot depot = new SettlementDepot(UUID.randomUUID(), areaId, position, purpose, 5, this::changed);
        this.depots.put(depot.id(), depot);
        changed();
        return depot;
    }

    boolean removeDepot(UUID id) {
        if (this.depots.remove(id) == null) {
            return false;
        }
        changed();
        return true;
    }

    public boolean configureDepot(UUID actor, UUID id, SettlementDepotPurpose purpose, int priority) {
        SettlementDepot depot = this.depots.get(id);
        return permits(actor, SettlementPermission.LOGISTICS) && depot != null && depot.configure(purpose, priority);
    }

    public boolean setDepotStockTarget(UUID actor, UUID depotId, ItemStack sample, int count) {
        SettlementDepot depot = this.depots.get(depotId);
        return permits(actor, SettlementPermission.LOGISTICS) && depot != null && depot.setStockTarget(sample, count);
    }

    public Map<UUID, SettlementBuildingSurvey.Survey> surveys() {
        return Collections.unmodifiableMap(this.surveys);
    }

    public SettlementSupportState support() {
        return this.support;
    }

    public int housingCapacity() {
        if (this.cachedHousingCapacity >= 0 && this.housingTerritoryRevision == this.territory.revision()) return this.cachedHousingCapacity;
        this.housingTerritoryRevision = this.territory.revision();
        // only survey, area and territory changes invalidate capacity, resident care does not
        var beds = new java.util.HashSet<BlockPos>();
        for (var area : this.areas.values()) {
            var survey = this.surveys.get(area.id());
            if (area.enabled() && owns(area) && area.type() == SettlementAreaType.HOME && survey != null) {
                beds.addAll(survey.beds());
            }
        }
        this.cachedHousingBeds = Set.copyOf(beds);
        this.cachedHousingCapacity = beds.size();
        return this.cachedHousingCapacity;
    }

    boolean usableBed(BlockPos bed) {
        housingCapacity();
        return this.cachedHousingBeds.contains(bed);
    }

    void recordSurvey(UUID area, SettlementBuildingSurvey.Survey survey) {
        if (workplace(area) == null) {
            return;
        }
        var previous = this.surveys.get(area);
        if (previous != null && previous.beds().equals(survey.beds())
                && previous.workstations().equals(survey.workstations()) && previous.fullyLoaded() == survey.fullyLoaded()
                && previous.fixtures().equals(survey.fixtures()) && previous.waterSources().equals(survey.waterSources())
                && previous.bedAvailability().equals(survey.bedAvailability())
                && survey.checkedAt() - previous.checkedAt() < 1_200L) {
            return;
        }
        this.surveys.put(area, survey);
        this.cachedHousingCapacity = -1;
        changed();
    }

    public boolean permits(UUID player, SettlementPermission permission) {
        if (player == null || permission == null) {
            return false;
        }
        if (this.leader.equals(player)) {
            return true;
        }
        Set<SettlementPermission> permissions = this.delegates.getOrDefault(player, Set.of());
        return permissions.contains(permission) || permission == SettlementPermission.VIEW && !permissions.isEmpty();
    }

    public boolean setPermissions(UUID actor, UUID delegate, Set<SettlementPermission> permissions) {
        if (!this.leader.equals(actor) || delegate == null || delegate.equals(this.leader) || permissions == null) {
            return false;
        }
        Set<SettlementPermission> updated = permissions.isEmpty() ? Set.of() : Set.copyOf(permissions);
        if (this.delegates.getOrDefault(delegate, Set.of()).equals(updated)) {
            return true;
        }
        if (updated.isEmpty()) {
            this.delegates.remove(delegate);
        } else {
            this.delegates.put(delegate, updated);
        }
        this.equipmentPlans.keySet().forEach(this::validateEquipmentAuthority);
        validateWorkerPoolAuthorities();
        changed();
        return true;
    }

    public LeadershipOffer leadershipOffer() { return this.leadershipOffer; }

    public LeadershipOffer offerLeadership(UUID actor, UUID successor, long now) {
        if (!this.leader.equals(actor) || successor == null || successor.equals(this.leader)) {
            return null;
        }
        this.leadershipOffer = new LeadershipOffer(UUID.randomUUID(), actor, successor, Math.addExact(now, 24_000L));
        changed();
        return this.leadershipOffer;
    }

    public boolean acceptLeadership(UUID actor, UUID offer, long now) {
        var pending = this.leadershipOffer;
        if (pending == null || !pending.id().equals(offer) || !pending.successor().equals(actor)
                || !pending.issuer().equals(this.leader) || now >= pending.expiresAt()) return false;
        changeLeader(actor);
        return true;
    }

    public boolean cancelLeadership(UUID actor, UUID offer) {
        var pending = this.leadershipOffer;
        if (pending == null || !pending.id().equals(offer)
                || !this.leader.equals(actor) && !pending.successor().equals(actor)) return false;
        this.leadershipOffer = null;
        changed();
        return true;
    }

    boolean recoverLeadership(net.minecraft.commands.CommandSourceStack source, UUID successor) {
        if (source == null || !source.hasPermission(2) || successor == null) return false;
        changeLeader(successor);
        return true;
    }

    private void changeLeader(UUID successor) {
        this.delegates.clear();
        this.leadershipOffer = null;
        this.leader = successor;
        this.equipmentPlans.keySet().forEach(this::validateEquipmentAuthority);
        validateWorkerPoolAuthorities();
        changed();
    }

    public record LeadershipOffer(UUID id, UUID issuer, UUID successor, long expiresAt) {
    }

    boolean putArea(UUID actor, SettlementArea area) {
        if (!permits(actor, SettlementPermission.BUILD) || area == null
                || !owns(area)
                || this.projects.values().stream().anyMatch(project -> project.workplace() != null
                        && (project.area().equals(area.id()) || project.reservesFootprint() && project.overlaps(area)
                                && area.type().changesTerrain() && area.type() != SettlementAreaType.CONSTRUCTION))
                || !this.areas.containsKey(area.id()) && this.areas.size() >= MAX_AREAS
                || this.areas.containsKey(area.id()) && this.areas.get(area.id()).type() != area.type()
                        && this.duties.containsValue(area.id())
                || this.depots.values().stream().anyMatch(depot -> depot.area().equals(area.id())
                        && (area.type() == SettlementAreaType.HOME || !area.contains(depot.position())))
                || this.projects.values().stream().anyMatch(project -> project.active() && project.area().equals(area.id()) && (!area.enabled() || !project.within(area)))
                || this.areas.values().stream().anyMatch(existing ->
                        !existing.id().equals(area.id()) && existing.conflictsWith(area))) {
            return false;
        }
        var transfers = this.transferQueues.get(area.id());
        if (transfers != null && this.areas.get(area.id()) != null && this.areas.get(area.id()).type() != area.type()
                && transfers.allocations().stream().anyMatch(com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Allocation::started)) return false;
        var production = this.productionQueues.get(area.id());
        if (production != null && this.areas.get(area.id()) != null && this.areas.get(area.id()).type() != area.type()
                && production.allocations().stream().anyMatch(com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue.Allocation::started)) return false;
        SettlementArea previous = this.areas.put(area.id(), area);
        if (previous != null && previous.type() != area.type()) {
            this.worksitePlans.remove(area.id());
            this.equipmentPlans.remove(area.id());
            this.productionQueues.remove(area.id());
            this.transferQueues.remove(area.id());
        }
        this.cachedHousingCapacity = -1;
        if (!area.equals(previous)) {
            if (previous == null || previous.type() != area.type()
                    || !previous.min().equals(area.min()) || !previous.max().equals(area.max())) {
                this.surveys.remove(area.id());
            }
            changed();
        }
        return true;
    }

    boolean removeArea(UUID actor, UUID area) {
        if (!permits(actor, SettlementPermission.BUILD)
                || this.projects.values().stream().anyMatch(project -> project.active() && project.area().equals(area))
                || this.depots.values().stream().anyMatch(depot -> depot.area().equals(area))
                || this.transferQueues.containsKey(area) && this.transferQueues.get(area).allocations().stream()
                        .anyMatch(com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Allocation::started)
                || this.productionQueues.containsKey(area) && this.productionQueues.get(area).allocations().stream()
                        .anyMatch(com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue.Allocation::started)
                || this.areas.remove(area) == null) {
            return false;
        }
        this.projects.values().stream().filter(project -> project.active() && project.area().equals(area))
                .forEach(project -> finishProject(project.id(), SettlementProject.Status.CANCELLED));
        this.duties.entrySet().stream().filter(duty -> duty.getValue().equals(area)).map(Map.Entry::getKey).toList().forEach(this::removeDuty);
        this.guardOrders.keySet().removeIf(resident -> !this.duties.containsKey(resident));
        this.temporaryGuardOrders.keySet().removeIf(resident -> !this.duties.containsKey(resident));
        this.workerSettings.keySet().removeIf(resident -> !this.duties.containsKey(resident));
        this.surveys.remove(area);
        this.worksitePlans.remove(area);
        this.equipmentPlans.remove(area);
        this.productionQueues.remove(area);
        this.transferQueues.remove(area);
        this.cachedHousingCapacity = -1;
        changed();
        return true;
    }

    void refreshStageRules(boolean resetSupport) {
        this.radius = Math.max(this.radius, this.stage.radius());
        this.territory.refresh(this.stage);
        if (resetSupport) this.support.resetAdvancement();
        changed();
    }

    void advance() {
        this.stage.next().ifPresent(next -> {
            this.stage = next;
            this.radius = Math.max(this.radius, next.radius());
            this.territory.advance(next);
            changed();
        });
    }

    void changed() {
        this.revision = Math.addExact(this.revision, 1L);
        this.dirty.run();
    }

    CompoundTag save(HolderLookup.Provider provider) {
        var navigationTags = new ListTag();
        this.navigation.values().forEach(section -> navigationTags.add(section.save()));
        return save(provider, navigationTags);
    }

    CompoundTag save(HolderLookup.Provider provider, ListTag navigationTags) {
        return save(provider, navigationTags, false);
    }

    CompoundTag snapshot(HolderLookup.Provider provider, ListTag navigationTags) {
        return save(provider, navigationTags, true);
    }

    private CompoundTag save(HolderLookup.Provider provider, ListTag navigationTags, boolean checkpoint) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id.value());
        tag.putString("Dimension", this.dimension.toString());
        tag.putLong("Center", this.center.asLong());
        tag.putUUID("Leader", this.leader);
        if (autonomous != null) tag.put("Autonomous", autonomous.save());
        if (this.leadershipOffer != null) {
            var offer = new CompoundTag();
            offer.putUUID("Id", this.leadershipOffer.id());
            offer.putUUID("Issuer", this.leadershipOffer.issuer());
            offer.putUUID("Successor", this.leadershipOffer.successor());
            offer.putLong("ExpiresAt", this.leadershipOffer.expiresAt());
            tag.put("LeadershipOffer", offer);
        }
        tag.putLong("FoundedAt", this.foundedAt);
        tag.putLong("Revision", this.revision);
        var historyTags = new ListTag();
        this.history.forEach(event -> historyTags.add(event.save()));
        tag.put("History", historyTags);
        tag.putString("Stage", this.stage.name());
        tag.putInt("ExpansionRadius", this.radius);
        tag.put("Territory", this.territory.save());
        ListTag delegateTags = new ListTag();
        this.delegates.forEach((player, permissions) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            for (SettlementPermission permission : permissions) {
                entry.putBoolean(permission.name(), true);
            }
            delegateTags.add(entry);
        });
        tag.put("Delegates", delegateTags);
        ListTag areaTags = new ListTag();
        this.areas.values().forEach(area -> areaTags.add(area.save()));
        tag.put("Areas", areaTags);
        ListTag dutyTags = new ListTag();
        this.duties.forEach((resident, area) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Resident", resident);
            entry.putUUID("Area", area);
            dutyTags.add(entry);
        });
        tag.put("Duties", dutyTags);
        ListTag guardTags = new ListTag();
        this.guardOrders.forEach((resident, order) -> {
            CompoundTag entry = order.save();
            entry.putUUID("Resident", resident);
            guardTags.add(entry);
        });
        tag.put("GuardOrders", guardTags);
        ListTag temporaryGuardTags = new ListTag();
        this.temporaryGuardOrders.forEach((resident, order) -> {
            CompoundTag entry = order.save();
            entry.putUUID("Resident", resident);
            temporaryGuardTags.add(entry);
        });
        tag.put("TemporaryGuardOrders", temporaryGuardTags);
        ListTag settingsTags = new ListTag();
        this.workerSettings.forEach((resident, settings) -> {
            CompoundTag entry = settings.save();
            entry.putUUID("Resident", resident);
            settingsTags.add(entry);
        });
        tag.put("WorkerSettings", settingsTags);
        ListTag planTags = new ListTag();
        this.worksitePlans.forEach((area, plan) -> {
            CompoundTag entry = plan.save();
            entry.putUUID("Area", area);
            planTags.add(entry);
        });
        tag.put("WorksitePlans", planTags);
        var productionTags = new ListTag();
        this.productionQueues.values().forEach(queue -> productionTags.add(queue.save(provider)));
        tag.put("ProductionQueues", productionTags);
        var transferTags = new ListTag();
        this.transferQueues.values().forEach(queue -> transferTags.add(queue.save(provider)));
        tag.put("TransferQueues", transferTags);
        var policyTags = new ListTag();
        this.supplyPolicies.values().forEach(policy -> policyTags.add(policy.save(provider)));
        tag.put("SupplyPolicies", policyTags);
        tag.putInt("WorkerPoolVersion", 1);
        var poolTags = new ListTag();
        this.workerPools.values().forEach(pool -> poolTags.add(pool.save()));
        tag.put("WorkerPools", poolTags);
        var cooldownTags = new ListTag();
        this.workerPoolCooldowns.forEach((resident, at) -> {
            var entry = new CompoundTag();
            entry.putUUID("Resident", resident);
            entry.putLong("At", at);
            cooldownTags.add(entry);
        });
        tag.put("WorkerPoolCooldowns", cooldownTags);
        var equipmentTags = new ListTag();
        this.equipmentPlans.forEach((area, plan) -> {
            var entry = plan.save();
            entry.putUUID("Area", area);
            equipmentTags.add(entry);
        });
        tag.put("EquipmentPlans", equipmentTags);
        ListTag projectTags = new ListTag();
        this.projects.values().forEach(project -> projectTags.add(project.save()));
        tag.put("Projects", projectTags);
        tag.putLong("ConstructionQueueRevision", this.constructionQueueRevision);
        var projectHistoryTags = new ListTag();
        this.projectHistory.values().forEach(project -> projectHistoryTags.add(project.save()));
        tag.put("ProjectHistory", projectHistoryTags);
        long[] retired = new long[this.retiredProjects.size() * 2];
        int retiredIndex = 0;
        for (UUID id : this.retiredProjects) {
            retired[retiredIndex++] = id.getMostSignificantBits();
            retired[retiredIndex++] = id.getLeastSignificantBits();
        }
        tag.putLongArray("RetiredProjects", retired);
        ListTag depotTags = new ListTag();
        this.depots.values().forEach(depot -> depotTags.add(depot.save(provider)));
        tag.put("Depots", depotTags);
        ListTag surveyTags = new ListTag();
        this.surveys.forEach((area, survey) -> {
            CompoundTag entry = survey.save();
            entry.putUUID("Area", area);
            surveyTags.add(entry);
        });
        tag.put("Surveys", surveyTags);
        tag.putBoolean("ManualManagement", this.manualManagement);
        var manualDutyTags = new ListTag();
        this.manualDuties.stream().sorted().forEach(id -> manualDutyTags.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
        tag.put("ManualDuties", manualDutyTags);
        var manualRevisionTags = new ListTag();
        this.manualDutyRevisions.forEach((resident, revision) -> {
            var entry = new CompoundTag();
            entry.putUUID("Resident", resident);
            entry.putLong("Revision", revision);
            manualRevisionTags.add(entry);
        });
        tag.put("ManualDutyRevisions", manualRevisionTags);
        tag.putLong("SimulationResumedAt", this.simulationResumedAt);
        tag.put("Support", this.support.save());
        ListTag supportServices = new ListTag();
        SettlementStageRules.forStage(this.stage.next().orElse(this.stage)).services().stream().sorted()
                .forEach(service -> supportServices.add(net.minecraft.nbt.StringTag.valueOf(service.name())));
        tag.put("SupportServices", supportServices);
        tag.put("Economy", this.economy.save());
        ListTag residentTags = new ListTag();
        this.residents.values().forEach(resident -> residentTags.add(checkpoint ? resident.snapshot() : resident.save()));
        tag.put("ResidentCare", residentTags);
        var homeTags = new ListTag();
        this.homes.values().forEach(home -> homeTags.add(home.save()));
        tag.put("Homes", homeTags);
        var courtshipTags = new ListTag();
        this.courtships.values().forEach(attempt -> courtshipTags.add(attempt.save()));
        tag.put("Courtships", courtshipTags);
        tag.put("Navigation", navigationTags);
        var homeReleaseTags = new ListTag();
        this.homeReleases.values().forEach(release -> homeReleaseTags.add(release.save()));
        tag.put("HomeReleases", homeReleaseTags);
        ListTag workerTags = new ListTag();
        this.workers.values().forEach(worker -> workerTags.add(checkpoint ? worker.snapshot() : worker.save()));
        tag.put("Workers", workerTags);
        ListTag livestockTags = new ListTag();
        this.livestock.values().forEach(animal -> livestockTags.add(animal.save()));
        tag.put("Livestock", livestockTags);
        var huntingTags = new ListTag();
        this.huntingTargets.values().forEach(target -> huntingTags.add(target.save()));
        tag.put("HuntingTargets", huntingTags);
        var projectileTags = new ListTag();
        this.projectiles.values().forEach(projectile -> projectileTags.add(projectile.save()));
        tag.put("Projectiles", projectileTags);
        ListTag animalBirthTags = new ListTag();
        this.animalBirths.values().forEach(birth -> animalBirthTags.add(birth.save()));
        tag.put("AnimalBirths", animalBirthTags);
        var itemDropTags = new ListTag();
        this.itemDrops.values().forEach(drop -> itemDropTags.add(drop.save()));
        tag.put("ItemDrops", itemDropTags);
        var groundItemTags = new ListTag();
        this.groundItems.values().forEach(item -> groundItemTags.add(item.save()));
        tag.put("GroundItems", groundItemTags);
        var groundExperienceTags = new ListTag();
        this.groundExperience.values().forEach(experience -> groundExperienceTags.add(experience.save()));
        tag.put("GroundExperience", groundExperienceTags);
        var deathTags = new ListTag();
        this.entityDeaths.values().forEach(death -> deathTags.add(death.save()));
        tag.put("EntityDeaths", deathTags);
        ListTag furnaceTags = new ListTag();
        this.furnaces.values().forEach(furnace -> furnaceTags.add(furnace.save()));
        tag.put("Furnaces", furnaceTags);
        ListTag brewingTags = new ListTag();
        this.brewingStands.values().forEach(stand -> brewingTags.add(stand.save()));
        tag.put("BrewingStands", brewingTags);
        ListTag fieldTags = new ListTag();
        this.fields.values().forEach(plot -> fieldTags.add(plot.save()));
        tag.put("Fields", fieldTags);
        var forestTags = new ListTag();
        this.forests.values().forEach(forest -> forestTags.add(forest.save()));
        tag.put("Forests", forestTags);
        var mineTags = new ListTag();
        this.mines.values().forEach(mine -> mineTags.add(mine.save()));
        tag.put("Mines", mineTags);
        ListTag fishingTags = new ListTag();
        this.fishingSpots.values().forEach(spots -> spots.forEach(spot -> fishingTags.add(spot.save())));
        tag.put("FishingSpots", fishingTags);
        ListTag departureTags = new ListTag();
        this.departures.values().forEach(departure -> departureTags.add(departure.save()));
        tag.put("Departures", departureTags);
        tag.putLong("NextDepartureAt", this.nextDepartureAt);
        tag.putLong("NextApplicantAt", this.nextApplicantAt);
        ListTag applicantTags = new ListTag();
        this.applicants.values().forEach(applicant -> applicantTags.add(applicant.save()));
        tag.put("Applicants", applicantTags);
        ListTag arrivalTags = new ListTag();
        this.arrivals.values().forEach(arrival -> arrivalTags.add(arrival.save()));
        tag.put("Arrivals", arrivalTags);
        return tag;
    }

    static SettlementRecord load(CompoundTag tag, HolderLookup.Provider provider, Runnable dirty) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null || !tag.hasUUID("Id") || !tag.hasUUID("Leader")
                || !tag.contains("Center", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Settlement identity, dimension, center or leader is missing");
        }
        SettlementRecord record = new SettlementRecord(new VillageAllegianceId(tag.getUUID("Id")),
                dimension, BlockPos.of(tag.getLong("Center")), tag.getUUID("Leader"),
                tag.getLong("FoundedAt"), dirty);
        if (tag.contains("LeadershipOffer")) {
            var offer = tag.getCompound("LeadershipOffer");
            if (!offer.hasUUID("Id") || !offer.hasUUID("Issuer") || !offer.hasUUID("Successor")
                    || !offer.getUUID("Issuer").equals(record.leader) || offer.getUUID("Successor").equals(record.leader)
                    || offer.getLong("ExpiresAt") < 0L) throw new IllegalArgumentException("Invalid leadership offer");
            record.leadershipOffer = new LeadershipOffer(offer.getUUID("Id"), offer.getUUID("Issuer"),
                    offer.getUUID("Successor"), offer.getLong("ExpiresAt"));
        }
        record.revision = Math.max(0L, tag.getLong("Revision"));
        var historyTags = tag.getList("History", Tag.TAG_COMPOUND);
        var eventIds = new java.util.HashSet<UUID>();
        for (int index = Math.max(0, historyTags.size() - MAX_HISTORY); index < historyTags.size(); index++) {
            var event = SettlementEvent.load(historyTags.getCompound(index));
            if (!eventIds.add(event.id())) throw new IllegalArgumentException("Duplicate settlement history event");
            record.history.add(event);
        }
        record.stage = SettlementStage.valueOf(tag.getString("Stage"));
        record.territory = tag.contains("Territory", Tag.TAG_COMPOUND)
                ? SettlementTerritory.load(record.center, tag.getCompound("Territory")) : new SettlementTerritory(record.center);
        record.territory.refresh(record.stage);
        int savedRadius = tag.contains("ExpansionRadius") ? tag.getInt("ExpansionRadius") : record.stage.legacyRadius();
        if (savedRadius < 16 || savedRadius > SettlementStageRules.MAX_RADIUS) {
            throw new IllegalArgumentException("Saved settlement expansion radius exceeds its limit");
        }
        record.radius = Math.max(savedRadius, record.stage.radius());
        record.manualManagement = tag.getBoolean("ManualManagement");
        for (var id : tag.getList("ManualDuties", Tag.TAG_INT_ARRAY)) record.manualDuties.add(net.minecraft.nbt.NbtUtils.loadUUID(id));
        if (tag.contains("ManualDutyRevisions")) {
            for (var raw : SettlementWorkerPool.list(tag, "ManualDutyRevisions", record.manualDuties.size())) {
                var entry = (CompoundTag) raw;
                if (!entry.hasUUID("Resident") || !entry.contains("Revision", Tag.TAG_LONG) || entry.getLong("Revision") < 0
                        || entry.getLong("Revision") > record.revision || !record.manualDuties.contains(entry.getUUID("Resident"))
                        || record.manualDutyRevisions.putIfAbsent(entry.getUUID("Resident"), entry.getLong("Revision")) != null)
                    throw new IllegalArgumentException("Invalid saved manual duty revision");
            }
            if (record.manualDutyRevisions.size() != record.manualDuties.size())
                throw new IllegalArgumentException("Manual duties are missing their revisions");
        } else record.manualDuties.forEach(resident -> record.manualDutyRevisions.put(resident, record.revision));
        record.simulationResumedAt = Math.max(0L, tag.getLong("SimulationResumedAt"));
        record.support = SettlementSupportState.load(tag.getCompound("Support"));
        var supportStage = record.stage.next().orElse(record.stage);
        Set<SettlementProgressionService.Requirement> supportedServices = SettlementStageRules.legacy(supportStage).services();
        if (tag.contains("SupportServices")) {
            supportedServices = EnumSet.noneOf(SettlementProgressionService.Requirement.class);
            for (var service : tag.getList("SupportServices", Tag.TAG_STRING)) {
                supportedServices.add(SettlementProgressionService.Requirement.valueOf(service.getAsString()));
            }
        }
        if (!supportedServices.equals(SettlementStageRules.forStage(supportStage).services())) record.support.resetAdvancement();
        if(tag.contains("Autonomous")) {
            record.autonomous=AutonomousVillageState.load(tag.getCompound("Autonomous"));
            for(long section:record.autonomous.sections) if(!record.territory.contains(net.minecraft.core.SectionPos.x(section),net.minecraft.core.SectionPos.z(section)))
                throw new IllegalArgumentException("Autonomous boundary exceeds owned plots");
        }
        record.economy = SettlementEconomy.load(tag.getCompound("Economy"), record::changed);
        record.economy.bank.load(tag.getCompound("Economy").getCompound("BankStorage"), provider);
        for (Tag raw : tag.getList("ResidentCare", Tag.TAG_COMPOUND)) {
            var resident = SettlementResidentState.load((CompoundTag) raw);
            if (record.residents.putIfAbsent(resident.id(), resident) != null) {
                throw new IllegalArgumentException("Resident care duplicates an identity");
            }
        }
        var occupiedParents = new java.util.HashSet<UUID>();
        var courtshipBeds = new java.util.HashSet<BlockPos>();
        for (var raw : tag.getList("Courtships", Tag.TAG_COMPOUND)) {
            var attempt = SettlementCourtship.load((CompoundTag) raw);
            if (!record.residents.containsKey(attempt.first()) || !record.residents.containsKey(attempt.second())
                    || !occupiedParents.add(attempt.first()) || !occupiedParents.add(attempt.second()) || !courtshipBeds.add(attempt.bed())) {
                throw new IllegalArgumentException("Courtship has missing or duplicate parents or bed");
            }
            record.courtships.put(attempt.first(), attempt);
        }
        for (var raw : tag.getList("Navigation", Tag.TAG_COMPOUND)) {
            var section = SettlementNavigationSection.load((CompoundTag) raw);
            if (record.navigation.putIfAbsent(section.origin(), section) != null) {
                throw new IllegalArgumentException("Navigation observations duplicate a section");
            }
        }
        var claimedBeds = new java.util.HashSet<BlockPos>();
        for (Tag raw : tag.getList("Homes", Tag.TAG_COMPOUND)) {
            var home = SettlementHome.load((CompoundTag) raw);
            if (record.homes.putIfAbsent(home.resident(), home) != null || home.bed() != null && !claimedBeds.add(home.bed())) {
                throw new IllegalArgumentException("Home assignments duplicate a resident or bed");
            }
        }
        for (Tag raw : tag.getList("HomeReleases", Tag.TAG_COMPOUND)) {
            var release = SettlementHomeRelease.load((CompoundTag) raw);
            if (record.homeReleases.putIfAbsent(release.id(), release) != null) {
                throw new IllegalArgumentException("Home releases duplicate an identity");
            }
        }
        for (Tag raw : tag.getList("Workers", Tag.TAG_COMPOUND)) {
            var worker = SettlementWorkerState.load((CompoundTag) raw);
            if (record.workers.putIfAbsent(worker.id(), worker) != null) {
                throw new IllegalArgumentException("Background worker duplicates a resident identity");
            }
        }
        for (Tag raw : tag.getList("Livestock", Tag.TAG_COMPOUND)) {
            var animal = SettlementLivestock.load((CompoundTag) raw);
            if (record.livestock.putIfAbsent(animal.id(), animal) != null) {
                throw new IllegalArgumentException("Livestock duplicates an animal identity");
            }
        }
        for (Tag raw : tag.getList("HuntingTargets", Tag.TAG_COMPOUND)) {
            var target = SettlementHuntingTarget.load((CompoundTag) raw);
            if (record.huntingTargets.putIfAbsent(target.id(), target) != null) {
                throw new IllegalArgumentException("Hunting targets duplicate an identity");
            }
        }
        for (Tag raw : tag.getList("Projectiles", Tag.TAG_COMPOUND)) {
            var projectile = SettlementProjectile.load((CompoundTag) raw);
            if (record.projectiles.putIfAbsent(projectile.id(), projectile) != null) {
                throw new IllegalArgumentException("Settlement projectiles duplicate an identity");
            }
        }
        for (Tag raw : tag.getList("AnimalBirths", Tag.TAG_COMPOUND)) {
            var birth = SettlementAnimalBirth.load((CompoundTag) raw);
            if (record.animalBirths.putIfAbsent(birth.id(), birth) != null) {
                throw new IllegalArgumentException("Birth duplicates a livestock identity");
            }
        }
        for (Tag raw : tag.getList("ItemDrops", Tag.TAG_COMPOUND)) {
            var drop = SettlementItemDrop.load((CompoundTag) raw, provider);
            if (record.itemDrops.putIfAbsent(drop.id(), drop) != null) {
                throw new IllegalArgumentException("Pending item drops duplicate an identity");
            }
        }
        for (Tag raw : tag.getList("GroundItems", Tag.TAG_COMPOUND)) {
            var item = SettlementGroundItem.load((CompoundTag) raw, provider);
            if (record.groundItems.putIfAbsent(item.drop().id(), item) != null) {
                throw new IllegalArgumentException("Ground items duplicate an identity");
            }
        }
        for (Tag raw : tag.getList("GroundExperience", Tag.TAG_COMPOUND)) {
            var experience = SettlementGroundExperience.load((CompoundTag) raw);
            if (record.groundExperience.putIfAbsent(experience.id(), experience) != null) {
                throw new IllegalArgumentException("Ground experience duplicates an identity");
            }
        }
        for (Tag raw : tag.getList("EntityDeaths", Tag.TAG_COMPOUND)) {
            var death = SettlementEntityDeath.load((CompoundTag) raw);
            if (record.entityDeaths.putIfAbsent(death.id(), death) != null) {
                throw new IllegalArgumentException("Entity deaths duplicate an identity");
            }
        }
        for (Tag raw : tag.getList("Furnaces", Tag.TAG_COMPOUND)) {
            var furnace = SettlementFurnaceState.load((CompoundTag) raw);
            if (record.furnaces.putIfAbsent(furnace.position(), furnace) != null) {
                throw new IllegalArgumentException("Settlement furnace duplicates a position");
            }
        }
        for (Tag raw : tag.getList("BrewingStands", Tag.TAG_COMPOUND)) {
            var stand = SettlementBrewingStandState.load((CompoundTag) raw);
            if (record.brewingStands.putIfAbsent(stand.position(), stand) != null) {
                throw new IllegalArgumentException("Settlement brewing stand duplicates a position");
            }
        }
        for (Tag raw : tag.getList("Fields", Tag.TAG_COMPOUND)) {
            var plot = SettlementFieldPlot.load((CompoundTag) raw);
            if (record.fields.putIfAbsent(plot.position(), plot) != null) {
                throw new IllegalArgumentException("Settlement field duplicates a position");
            }
        }
        for (Tag raw : tag.getList("Forests", Tag.TAG_COMPOUND)) {
            var forest = SettlementForest.load((CompoundTag) raw);
            if (record.forests.putIfAbsent(forest.area(), forest) != null) {
                throw new IllegalArgumentException("Settlement forests duplicate an area");
            }
        }
        for (Tag raw : tag.getList("Mines", Tag.TAG_COMPOUND)) {
            var mine = SettlementMine.load((CompoundTag) raw);
            if (record.mines.putIfAbsent(mine.area(), mine) != null) {
                throw new IllegalArgumentException("Settlement mines duplicate an area");
            }
        }
        Map<UUID, List<SettlementFishingSpot>> fishingSpots = new LinkedHashMap<>();
        for (Tag raw : tag.getList("FishingSpots", Tag.TAG_COMPOUND)) {
            var spot = SettlementFishingSpot.load((CompoundTag) raw);
            fishingSpots.computeIfAbsent(spot.area(), ignored -> new ArrayList<>()).add(spot);
        }
        for (var entry : fishingSpots.entrySet()) {
            if (entry.getValue().size() > SettlementFisheryService.MAX_SPOTS
                    || entry.getValue().stream().map(SettlementFishingSpot::water).distinct().count() != entry.getValue().size()) {
                throw new IllegalArgumentException("Saved fishing sites exceed their limit or duplicate a water position");
            }
            record.fishingSpots.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        record.nextDepartureAt = Math.max(0L, tag.getLong("NextDepartureAt"));
        for (Tag raw : tag.getList("Departures", Tag.TAG_COMPOUND)) {
            var departure = SettlementDeparture.load((CompoundTag) raw);
            if (record.departures.putIfAbsent(departure.id(), departure) != null) {
                throw new IllegalArgumentException("Departure duplicates a resident identity");
            }
        }
        for (Tag raw : tag.getList("Arrivals", Tag.TAG_COMPOUND)) {
            var arrival = SettlementArrival.load((CompoundTag) raw);
            if (!record.residents.containsKey(arrival.id()) && !record.departures.containsKey(arrival.id())
                    || record.arrivals.putIfAbsent(arrival.id(), arrival) != null) {
                throw new IllegalArgumentException("Arrival is duplicated or has no resident care");
            }
        }
        if (tag.contains("NextApplicantAt")) {
            record.nextApplicantAt = Math.max(0L, tag.getLong("NextApplicantAt"));
        }
        for (Tag raw : tag.getList("Applicants", Tag.TAG_COMPOUND)) {
            SettlementApplicant applicant = SettlementApplicant.load((CompoundTag) raw);
            if (record.applicants.size() >= MAX_APPLICANTS || record.applicants.putIfAbsent(applicant.id(), applicant) != null) {
                throw new IllegalArgumentException("Settlement applicants exceed their limit or duplicate an identity");
            }
        }
        for (Tag raw : tag.getList("Delegates", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Player") || entry.getUUID("Player").equals(record.leader)) {
                continue;
            }
            Set<SettlementPermission> permissions = EnumSet.noneOf(SettlementPermission.class);
            for (SettlementPermission permission : SettlementPermission.values()) {
                if (entry.getBoolean(permission.name())) {
                    permissions.add(permission);
                }
            }
            if (!permissions.isEmpty()) {
                record.delegates.put(entry.getUUID("Player"), Set.copyOf(permissions));
            }
        }
        for (Tag raw : tag.getList("Areas", Tag.TAG_COMPOUND)) {
            SettlementArea area = SettlementArea.load((CompoundTag) raw);
            if (!record.territory.migrated() && !area.within(record.center, record.radius) || record.areas.size() >= MAX_AREAS
                    || record.areas.containsKey(area.id())
                    || record.areas.values().stream().anyMatch(existing -> existing.conflictsWith(area))) {
                throw new IllegalArgumentException("Settlement areas exceed their bounds or have conflicting uses");
            }
            record.areas.put(area.id(), area);
        }
        for (Tag raw : tag.getList("Projects", Tag.TAG_COMPOUND)) {
            var project = SettlementProject.load((CompoundTag) raw);
            if (!project.town().equals(record.id.value()) || record.projects.putIfAbsent(project.id(), project) != null
                    || project.workplace() != null && (!record.territory.migrated() && !project.workplace().within(record.center, record.radius)
                            || record.areas.containsKey(project.area()))) {
                throw new IllegalArgumentException("Invalid or duplicated construction project");
            }
        }
        record.projectMaintenance.addAll(record.projects.keySet());
        record.constructionQueueRevision = tag.getLong("ConstructionQueueRevision");
        if (record.constructionQueueRevision < 0) throw new IllegalArgumentException("Invalid construction queue revision");
        if (tag.contains("RetiredProjects") && !tag.contains("RetiredProjects", Tag.TAG_LONG_ARRAY)) throw new IllegalArgumentException("Invalid retired construction identities");
        long[] retired = tag.getLongArray("RetiredProjects");
        if (retired.length % 2 != 0) throw new IllegalArgumentException("Incomplete retired construction identity");
        for (int index = 0; index < retired.length; index += 2) {
            var id = new UUID(retired[index], retired[index + 1]);
            if (record.knowsProject(id) || !record.retiredProjects.add(id)) throw new IllegalArgumentException("Duplicated retired construction identity");
        }
        if (tag.contains("ProjectHistory") && (!(tag.get("ProjectHistory") instanceof ListTag history)
                || !history.isEmpty() && history.getElementType() != Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid construction summary history");
        for (Tag raw : tag.getList("ProjectHistory", Tag.TAG_COMPOUND)) {
            var project = SettlementProject.load((CompoundTag) raw);
            if (record.knowsProject(project.id()) || project.unfinished() || project.site() != null
                    || !project.town().equals(record.id.value()) || project.paymentEarned() && !project.paymentDelivered()) {
                throw new IllegalArgumentException("Construction summary has unresolved obligations or a duplicated identity");
            }
            record.projectHistory.put(project.id(), project);
        }
        record.trimProjectHistory();
        for (Tag raw : tag.getList("Duties", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Resident") || !entry.hasUUID("Area")) {
                throw new IllegalArgumentException("Settlement duty is missing its resident or area");
            }
            SettlementArea area = record.workplace(entry.getUUID("Area"));
            if (area == null || area.type().role() == null
                    || record.duties.putIfAbsent(entry.getUUID("Resident"), area.id()) != null) {
                throw new IllegalArgumentException("Settlement duty is duplicated or has no workplace");
            }
        }
        for (Tag raw : tag.getList("GuardOrders", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Resident")) {
                throw new IllegalArgumentException("Guard order is missing its resident");
            }
            UUID resident = entry.getUUID("Resident");
            var area = record.workplace(record.duties.get(resident));
            var order = SettlementGuardOrder.load(entry);
            if (area == null || !order.validFor(area.type().role()) || !record.territory.migrated() && !order.positions().allMatch(point ->
                            Math.abs((long) point.getX() - record.center.getX()) <= record.radius
                                    && Math.abs((long) point.getZ() - record.center.getZ()) <= record.radius)
                    || record.guardOrders.putIfAbsent(resident, order) != null) {
                throw new IllegalArgumentException("Guard order is duplicated or outside its settlement");
            }
        }
        if (tag.contains("WorksitePlans") && !tag.contains("WorksitePlans", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Worksite plans must be a list");
        }
        if (tag.contains("TemporaryGuardOrders") && (!(tag.get("TemporaryGuardOrders") instanceof ListTag list)
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Invalid temporary guard orders");
        }
        for (Tag raw : tag.getList("TemporaryGuardOrders", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Resident")) throw new IllegalArgumentException("Temporary guard order has no resident");
            var resident = entry.getUUID("Resident");
            var area = record.workplace(record.duties.get(resident));
            var order = SettlementGuardOrder.load(entry);
            if (area == null || area.type() != SettlementAreaType.GUARD_POST || order.points().isEmpty() || !order.within(record)
                    || record.temporaryGuardOrders.putIfAbsent(resident, order) != null) throw new IllegalArgumentException("Invalid temporary guard order");
        }
        if (tag.get("WorksitePlans") instanceof ListTag list && !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Worksite plan entries must be compounds");
        }
        ListTag planTags = tag.getList("WorksitePlans", Tag.TAG_COMPOUND);
        if (planTags.size() > MAX_AREAS) throw new IllegalArgumentException("Too many worksite plans");
        for (Tag raw : planTags) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Area")) throw new IllegalArgumentException("Worksite plan has no area");
            var area = record.areas.get(entry.getUUID("Area"));
            var plan = SettlementWorksitePlan.load(entry);
            if (area == null || area.type().role() != plan.settings().role()
                    || plan.route() != null && !plan.route().within(record)
                    || record.worksitePlans.putIfAbsent(area.id(), plan) != null) {
                throw new IllegalArgumentException("Worksite plan is duplicated or has no matching area");
            }
        }
        if (tag.contains("ProductionQueues") && !(tag.get("ProductionQueues") instanceof ListTag)
                || tag.get("ProductionQueues") instanceof ListTag list && (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND
                        || list.size() > MAX_AREAS)) {
            throw new IllegalArgumentException("Invalid worksite production queues");
        }
        for (Tag raw : tag.getList("ProductionQueues", Tag.TAG_COMPOUND)) {
            var queue = HiredProductionQueue.load((CompoundTag) raw, provider, ignored -> record.changed());
            SettlementPolicyExecution.configure(record, queue);
            var area = record.areas.get(queue.owner());
            if (queue.scope() != HiredProductionQueue.Scope.WORKSITE || area == null || area.type().role() != queue.role()
                    || !record.worksitePlans.containsKey(area.id()) || record.productionQueues.putIfAbsent(area.id(), queue) != null
                    || queue.allocations().stream().anyMatch(allocation -> allocation.worker() != null
                        && !area.id().equals(record.duties.get(allocation.worker())))) {
                throw new IllegalArgumentException("Worksite production has invalid ownership");
            }
        }
        if (tag.contains("TransferQueues") && (!(tag.get("TransferQueues") instanceof ListTag list) || list.size() > MAX_AREAS
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid transfer queues");
        for (Tag raw : tag.getList("TransferQueues", Tag.TAG_COMPOUND)) {
            var queue = com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.load(
                    (CompoundTag) raw, provider, ignored -> record.changed());
            SettlementPolicyExecution.configure(record, queue);
            var area = record.areas.get(queue.owner());
            if (queue.scope() != com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Scope.WORKSITE
                    || area == null || area.type() != SettlementAreaType.DEPOT || !record.worksitePlans.containsKey(area.id())
                    || record.transferQueues.putIfAbsent(area.id(), queue) != null
                    || queue.allocations().stream().anyMatch(allocation -> !allocation.started() && !area.id().equals(record.duties.get(allocation.worker())))) {
                throw new IllegalArgumentException("Transfer queue has invalid ownership");
            }
        }
        if (tag.contains("SupplyPolicies") && (!(tag.get("SupplyPolicies") instanceof ListTag list)
                || list.size() > SettlementSupplyPolicy.MAX_POLICIES || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid supply policies");
        if (tag.contains("EquipmentPlans") && (!(tag.get("EquipmentPlans") instanceof ListTag list)
                || list.size() > MAX_AREAS || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid equipment plans");
        for (Tag raw : tag.getList("EquipmentPlans", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Area")) throw new IllegalArgumentException("Equipment plan has no worksite");
            var area = record.areas.get(entry.getUUID("Area"));
            var plan = SettlementEquipmentPlan.load(entry);
            if (area == null || plan.revision() > record.revision || !plan.configuration().validFor(area.type().role()) || !record.worksitePlans.containsKey(area.id())
                    || record.equipmentPlans.putIfAbsent(area.id(), plan) != null)
                throw new IllegalArgumentException("Equipment plan has invalid worksite ownership");
        }
        if (tag.contains("WorkerPoolVersion")) {
            if (!tag.contains("WorkerPoolVersion", Tag.TAG_INT) || tag.getInt("WorkerPoolVersion") != 1)
                throw new IllegalArgumentException("Unsupported worker pool version");
            var enrolled = new java.util.HashSet<UUID>();
            for (var raw : SettlementWorkerPool.list(tag, "WorkerPools", SettlementWorkerPool.MAX_POOLS)) {
                var pool = SettlementWorkerPool.load((CompoundTag) raw);
                if (pool.revision() > record.revision || record.workerPools.putIfAbsent(pool.id(), pool) != null
                        || pool.configuration().residents().stream().anyMatch(resident -> !record.residents.containsKey(resident) || !enrolled.add(resident)))
                    throw new IllegalArgumentException("Worker pools duplicate an identity or enroll an unknown resident");
            }
            for (var raw : SettlementWorkerPool.list(tag, "WorkerPoolCooldowns", SettlementWorkerPool.MAX_POOLS * SettlementWorkerPool.MAX_RESIDENTS)) {
                var entry = (CompoundTag) raw;
                if (!entry.hasUUID("Resident") || !entry.contains("At", Tag.TAG_LONG) || entry.getLong("At") < 0
                        || !record.residents.containsKey(entry.getUUID("Resident"))
                        || record.workerPoolCooldowns.putIfAbsent(entry.getUUID("Resident"), entry.getLong("At")) != null)
                    throw new IllegalArgumentException("Invalid saved worker pool cooldown");
            }
        } else if (tag.contains("WorkerPools") || tag.contains("WorkerPoolCooldowns")) {
            throw new IllegalArgumentException("Worker pools have no version");
        }
        var managedOrders = new java.util.HashSet<UUID>();
        for (Tag raw : tag.getList("SupplyPolicies", Tag.TAG_COMPOUND)) {
            var policy = SettlementSupplyPolicy.load((CompoundTag) raw, provider, record::supplyChanged, record::changed);
            if (record.supplyPolicies.putIfAbsent(policy.id(), policy) != null
                    || policy.bindings().stream().anyMatch(binding -> binding.order() != null && !managedOrders.add(binding.order())))
                throw new IllegalArgumentException("Supply policies duplicate an identity or managed order");
        }
        for (Tag raw : tag.getList("WorkerSettings", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Resident")) {
                throw new IllegalArgumentException("Worker settings are missing their resident");
            }
            UUID resident = entry.getUUID("Resident");
            var area = record.workplace(record.duties.get(resident));
            var settings = SettlementWorkerSettings.load(entry);
            if (area == null || area.type().role() != settings.role()
                    || !record.worksitePlans.containsKey(area.id()) && settings.overrides() != SettlementWorkerSettings.allOverrides(settings.role())
                    || record.workerSettings.putIfAbsent(resident, settings) != null) {
                throw new IllegalArgumentException("Worker settings are duplicated or have no matching duty");
            }
        }
        for (var project : record.projects.values()) {
            if (project.active() && (!project.within(record.workplace(project.area()))
                    || !project.area().equals(record.duties.get(project.builder())) && !project.blocker().equals("builder")
                    || record.projects.values().stream().anyMatch(other -> other != project && other.active() && other.builder().equals(project.builder())))
                    || project.reservesFootprint() && record.projects.values().stream().anyMatch(existing -> existing != project
                            && existing.reservesFootprint() && existing.overlaps(project))) {
                throw new IllegalArgumentException("Construction project overlaps reserved work or has no matching duty");
            }
        }
        for (var project : record.projects.values()) {
            if (!project.active()) continue;
            for (var assistant : project.assistants()) {
                var home = record.workplace(record.duties.get(assistant));
                if (home == null || home.type() != SettlementAreaType.CONSTRUCTION || !record.worksitePlans.containsKey(home.id())) {
                    throw new IllegalArgumentException("Construction assistant has no adopted construction assignment");
                }
            }
        }
        for (Tag raw : tag.getList("Depots", Tag.TAG_COMPOUND)) {
            SettlementDepot depot = SettlementDepot.load((CompoundTag) raw, provider, record::changed);
            SettlementArea area = record.areas.get(depot.area());
            if (area == null || area.type() == SettlementAreaType.HOME || !area.contains(depot.position())
                    || record.depots.values().stream().anyMatch(existing -> existing.position().equals(depot.position()))
                    || record.depots.putIfAbsent(depot.id(), depot) != null) {
                throw new IllegalArgumentException("Depot is duplicated or outside its functional area");
            }
        }
        for (Tag raw : tag.getList("Surveys", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            SettlementArea area = entry.hasUUID("Area") ? record.workplace(entry.getUUID("Area")) : null;
            var survey = SettlementBuildingSurvey.Survey.load(entry);
            if (area == null || survey.beds().stream().anyMatch(pos -> !area.contains(pos))
                    || survey.workstations().stream().anyMatch(pos -> !area.contains(pos))
                    || survey.waterSources().stream().anyMatch(pos -> !area.contains(pos))
                    || record.surveys.putIfAbsent(area.id(), survey) != null) {
                throw new IllegalArgumentException("Building survey is duplicated or outside its area");
            }
        }
        record.areas.replaceAll((id, area) -> record.effectiveArea(area));
        return record;
    }
}
