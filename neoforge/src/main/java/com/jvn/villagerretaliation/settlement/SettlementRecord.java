package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
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
    private final Map<UUID, SettlementWorkerSettings> workerSettings = new LinkedHashMap<>();
    private final Map<UUID, SettlementProject> projects = new LinkedHashMap<>();
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
    private SettlementStage stage = SettlementStage.CAMP;
    private int radius = SettlementStage.CAMP.radius();
    private long revision;
    private long simulationResumedAt;
    private boolean manualManagement;
    private final Set<UUID> manualDuties = new java.util.HashSet<>();

    SettlementRecord(VillageAllegianceId id, ResourceLocation dimension, BlockPos center,
                     UUID leader, long foundedAt, Runnable dirty) {
        this.id = Objects.requireNonNull(id, "id");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.center = Objects.requireNonNull(center, "center").immutable();
        this.leader = Objects.requireNonNull(leader, "leader");
        this.foundedAt = Math.max(0L, foundedAt);
        this.dirty = Objects.requireNonNull(dirty, "dirty");
        this.nextApplicantAt = this.foundedAt + 600L;
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
        if (this.manualDuties.add(resident)) changed();
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
        return this.radius;
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
        removed |= this.homes.remove(resident) != null;
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

    boolean assignHome(UUID resident, BlockPos bed) {
        if (resident == null || bed != null && this.homes.values().stream()
                .anyMatch(home -> bed.equals(home.bed()) && !resident.equals(home.resident()))) return false;
        var previous = this.homes.get(resident);
        if (previous != null && Objects.equals(previous.bed(), bed)) return true;
        this.homes.put(resident, new SettlementHome(resident, bed, previous == null ? 1L : Math.incrementExact(previous.version())));
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
        if (area != null) return area;
        return this.projects.values().stream().filter(project -> project.workplace() != null
                && project.area().equals(id) && (project.active() || project.site() != null)).map(SettlementProject::workplace).findFirst().orElse(null);
    }

    public java.util.List<SettlementArea> workplaces() {
        var result = new java.util.ArrayList<>(this.areas.values());
        this.projects.values().stream().filter(project -> project.workplace() != null && project.reservesFootprint())
                .map(SettlementProject::workplace).forEach(result::add);
        return java.util.List.copyOf(result);
    }

    public Map<UUID, UUID> duties() {
        return Collections.unmodifiableMap(this.duties);
    }

    public Map<UUID, SettlementGuardOrder> guardOrders() {
        return Collections.unmodifiableMap(this.guardOrders);
    }

    boolean setGuardOrder(UUID actor, UUID resident, SettlementGuardOrder order) {
        var area = workplace(this.duties.get(resident));
        if (!permits(actor, SettlementPermission.STAFF) || area == null || area.type() != SettlementAreaType.GUARD_POST
                || order != null && !order.within(this)) {
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

    public SettlementProject activeProject(UUID builder) {
        return this.projects.values().stream().filter(project -> project.active() && project.builder().equals(builder)).findFirst().orElse(null);
    }

    boolean startProject(UUID actor, SettlementProject project) {
        var area = project.workplace() == null ? workplace(project.area()) : project.workplace();
        if (!permits(actor, SettlementPermission.BUILD) || !project.active() || !project.town().equals(this.id.value())
                || this.projects.containsKey(project.id()) || !project.within(area)
                || project.workplace() == null && !project.area().equals(this.duties.get(project.builder()))
                || project.workplace() != null && !Objects.equals(project.previousArea(), this.duties.get(project.builder()))
                || activeProject(project.builder()) != null
                || this.projects.values().stream().filter(SettlementProject::active).count() >= this.stage.projects()
                || this.projects.values().stream().anyMatch(existing -> existing.reservesFootprint() && existing.overlaps(project))) {
            return false;
        }
        if (project.fee() > 0 && !this.economy.reserveConstruction(project.fee())) return false;
        this.projects.put(project.id(), project);
        if (project.workplace() != null) {
            this.duties.put(project.builder(), project.area());
            this.workerSettings.remove(project.builder());
        }
        changed();
        return true;
    }

    void clearProjectSite(SettlementProject project) {
        project.clearSite();
        if (project.workplace() != null) this.surveys.remove(project.area());
        changed();
    }

    void finishProject(UUID id, SettlementProject.Status status) {
        var project = this.projects.get(id);
        if (project != null && project.finish(status)) {
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
        var project = activeProject(builder);
        if (project != null) {
            finishProject(project.id(), SettlementProject.Status.CANCELLED);
        }
    }

    public Map<UUID, SettlementWorkerSettings> workerSettings() {
        return Collections.unmodifiableMap(this.workerSettings);
    }

    boolean setWorkerSettings(UUID actor, UUID resident, SettlementWorkerSettings settings) {
        var area = workplace(this.duties.get(resident));
        if (!permits(actor, SettlementPermission.STAFF) || area == null || settings == null || area.type().role() != settings.role()) {
            return false;
        }
        if (!settings.equals(this.workerSettings.put(resident, settings))) {
            changed();
        }
        return true;
    }

    boolean assignDuty(UUID resident, UUID area) {
        SettlementArea target = this.areas.get(area);
        if (resident == null || target == null || target.type().role() == null) {
            return false;
        }
        if (!area.equals(this.duties.put(resident, area))) {
            cancelProjects(resident);
            cancelBrewing(resident);
            this.guardOrders.remove(resident);
            this.workerSettings.remove(resident);
            changed();
        }
        return true;
    }

    boolean removeDuty(UUID resident) {
        if (this.duties.remove(resident) == null) {
            return false;
        }
        cancelProjects(resident);
        cancelBrewing(resident);
        this.guardOrders.remove(resident);
        this.workerSettings.remove(resident);
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
                || area.type() == SettlementAreaType.HOME || !area.contains(position)
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
        return this.areas.values().stream()
                .filter(area -> area.enabled() && area.type() == SettlementAreaType.HOME)
                .map(area -> this.surveys.get(area.id()))
                .filter(Objects::nonNull)
                .mapToInt(survey -> survey.beds().size())
                .sum();
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
        changed();
        return true;
    }

    public boolean transferLeadership(UUID actor, UUID successor) {
        if (!this.leader.equals(actor) || successor == null || successor.equals(this.leader)) {
            return false;
        }
        this.delegates.remove(successor);
        this.leader = successor;
        changed();
        return true;
    }

    boolean putArea(UUID actor, SettlementArea area) {
        if (!permits(actor, SettlementPermission.BUILD) || area == null
                || !area.within(this.center, this.radius)
                || this.projects.values().stream().anyMatch(project -> project.workplace() != null
                        && (project.area().equals(area.id()) || project.reservesFootprint() && project.overlaps(area)
                                && area.type().changesTerrain() && area.type() != SettlementAreaType.CONSTRUCTION))
                || !this.areas.containsKey(area.id()) && this.areas.size() >= MAX_AREAS
                || this.areas.containsKey(area.id()) && this.areas.get(area.id()).type() != area.type()
                        && this.duties.containsValue(area.id())
                || this.depots.values().stream().anyMatch(depot -> depot.area().equals(area.id())
                        && (area.type() == SettlementAreaType.HOME || !area.contains(depot.position())))
                || this.projects.values().stream().anyMatch(project -> project.active() && project.area().equals(area.id()) && !project.within(area))
                || this.areas.values().stream().anyMatch(existing ->
                        !existing.id().equals(area.id()) && existing.conflictsWith(area))) {
            return false;
        }
        SettlementArea previous = this.areas.put(area.id(), area);
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
                || this.depots.values().stream().anyMatch(depot -> depot.area().equals(area))
                || this.areas.remove(area) == null) {
            return false;
        }
        this.projects.values().stream().filter(project -> project.active() && project.area().equals(area))
                .forEach(project -> finishProject(project.id(), SettlementProject.Status.CANCELLED));
        this.duties.entrySet().stream().filter(duty -> duty.getValue().equals(area)).map(Map.Entry::getKey).forEach(this::cancelBrewing);
        this.duties.values().removeIf(area::equals);
        this.guardOrders.keySet().removeIf(resident -> !this.duties.containsKey(resident));
        this.workerSettings.keySet().removeIf(resident -> !this.duties.containsKey(resident));
        this.surveys.remove(area);
        changed();
        return true;
    }

    void refreshStageRules(boolean resetSupport) {
        this.radius = Math.max(this.radius, this.stage.radius());
        if (resetSupport) this.support.resetAdvancement();
        changed();
    }

    void advance() {
        this.stage.next().ifPresent(next -> {
            this.stage = next;
            this.radius = Math.max(this.radius, next.radius());
            changed();
        });
    }

    void changed() {
        this.revision = Math.addExact(this.revision, 1L);
        this.dirty.run();
    }

    CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id.value());
        tag.putString("Dimension", this.dimension.toString());
        tag.putLong("Center", this.center.asLong());
        tag.putUUID("Leader", this.leader);
        tag.putLong("FoundedAt", this.foundedAt);
        tag.putLong("Revision", this.revision);
        var historyTags = new ListTag();
        this.history.forEach(event -> historyTags.add(event.save()));
        tag.put("History", historyTags);
        tag.putString("Stage", this.stage.name());
        tag.putInt("ExpansionRadius", this.radius);
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
        ListTag settingsTags = new ListTag();
        this.workerSettings.forEach((resident, settings) -> {
            CompoundTag entry = settings.save();
            entry.putUUID("Resident", resident);
            settingsTags.add(entry);
        });
        tag.put("WorkerSettings", settingsTags);
        ListTag projectTags = new ListTag();
        this.projects.values().forEach(project -> projectTags.add(project.save()));
        tag.put("Projects", projectTags);
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
        tag.putLong("SimulationResumedAt", this.simulationResumedAt);
        tag.put("Support", this.support.save());
        ListTag supportServices = new ListTag();
        SettlementStageRules.forStage(this.stage.next().orElse(this.stage)).services().stream().sorted()
                .forEach(service -> supportServices.add(net.minecraft.nbt.StringTag.valueOf(service.name())));
        tag.put("SupportServices", supportServices);
        tag.put("Economy", this.economy.save());
        ListTag residentTags = new ListTag();
        this.residents.values().forEach(resident -> residentTags.add(resident.save()));
        tag.put("ResidentCare", residentTags);
        var homeTags = new ListTag();
        this.homes.values().forEach(home -> homeTags.add(home.save()));
        tag.put("Homes", homeTags);
        var courtshipTags = new ListTag();
        this.courtships.values().forEach(attempt -> courtshipTags.add(attempt.save()));
        tag.put("Courtships", courtshipTags);
        var navigationTags = new ListTag();
        this.navigation.values().forEach(section -> navigationTags.add(section.save()));
        tag.put("Navigation", navigationTags);
        var homeReleaseTags = new ListTag();
        this.homeReleases.values().forEach(release -> homeReleaseTags.add(release.save()));
        tag.put("HomeReleases", homeReleaseTags);
        ListTag workerTags = new ListTag();
        this.workers.values().forEach(worker -> workerTags.add(worker.save()));
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
        record.revision = Math.max(0L, tag.getLong("Revision"));
        var historyTags = tag.getList("History", Tag.TAG_COMPOUND);
        var eventIds = new java.util.HashSet<UUID>();
        for (int index = Math.max(0, historyTags.size() - MAX_HISTORY); index < historyTags.size(); index++) {
            var event = SettlementEvent.load(historyTags.getCompound(index));
            if (!eventIds.add(event.id())) throw new IllegalArgumentException("Duplicate settlement history event");
            record.history.add(event);
        }
        record.stage = SettlementStage.valueOf(tag.getString("Stage"));
        int savedRadius = tag.contains("ExpansionRadius") ? tag.getInt("ExpansionRadius") : record.stage.legacyRadius();
        if (savedRadius < 16 || savedRadius > SettlementStageRules.MAX_RADIUS) {
            throw new IllegalArgumentException("Saved settlement expansion radius exceeds its limit");
        }
        record.radius = Math.max(savedRadius, record.stage.radius());
        record.manualManagement = tag.getBoolean("ManualManagement");
        for (var id : tag.getList("ManualDuties", Tag.TAG_INT_ARRAY)) record.manualDuties.add(net.minecraft.nbt.NbtUtils.loadUUID(id));
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
        record.economy = SettlementEconomy.load(tag.getCompound("Economy"), record::changed);
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
            if (!area.within(record.center, record.radius) || record.areas.size() >= MAX_AREAS
                    || record.areas.containsKey(area.id())
                    || record.areas.values().stream().anyMatch(existing -> existing.conflictsWith(area))) {
                throw new IllegalArgumentException("Settlement areas exceed their bounds or have conflicting uses");
            }
            record.areas.put(area.id(), area);
        }
        for (Tag raw : tag.getList("Projects", Tag.TAG_COMPOUND)) {
            var project = SettlementProject.load((CompoundTag) raw);
            if (!project.town().equals(record.id.value()) || record.projects.putIfAbsent(project.id(), project) != null
                    || project.workplace() != null && (!project.workplace().within(record.center, record.radius)
                            || record.areas.containsKey(project.area()))) {
                throw new IllegalArgumentException("Invalid or duplicated construction project");
            }
        }
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
            if (area == null || area.type() != SettlementAreaType.GUARD_POST || !order.within(record)
                    || record.guardOrders.putIfAbsent(resident, order) != null) {
                throw new IllegalArgumentException("Guard order is duplicated or outside its settlement");
            }
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
                    || record.workerSettings.putIfAbsent(resident, settings) != null) {
                throw new IllegalArgumentException("Worker settings are duplicated or have no matching duty");
            }
        }
        for (var project : record.projects.values()) {
            if (project.active() && (!project.within(record.workplace(project.area()))
                    || !project.area().equals(record.duties.get(project.builder()))
                    || record.projects.values().stream().anyMatch(other -> other != project && other.active() && other.builder().equals(project.builder())))
                    || project.reservesFootprint() && record.projects.values().stream().anyMatch(existing -> existing != project
                            && existing.reservesFootprint() && existing.overlaps(project))) {
                throw new IllegalArgumentException("Construction project overlaps reserved work or has no matching duty");
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
        return record;
    }
}
