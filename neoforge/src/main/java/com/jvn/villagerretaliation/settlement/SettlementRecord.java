package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
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
    private final VillageAllegianceId id;
    private final ResourceLocation dimension;
    private final BlockPos center;
    private final long foundedAt;
    private final Runnable dirty;
    private final Map<UUID, Set<SettlementPermission>> delegates = new LinkedHashMap<>();
    private final Map<UUID, SettlementArea> areas = new LinkedHashMap<>();
    private final Map<UUID, UUID> duties = new LinkedHashMap<>();
    private final Map<UUID, SettlementGuardOrder> guardOrders = new LinkedHashMap<>();
    private final Map<UUID, SettlementDepot> depots = new LinkedHashMap<>();
    private final Map<UUID, SettlementBuildingSurvey.Survey> surveys = new LinkedHashMap<>();
    private final Map<UUID, SettlementApplicant> applicants = new LinkedHashMap<>();
    private final Map<UUID, SettlementResidentState> residents = new LinkedHashMap<>();
    private final Map<UUID, SettlementDeparture> departures = new LinkedHashMap<>();
    private final Map<UUID, SettlementWorkerState> workers = new LinkedHashMap<>();
    private final Map<BlockPos, SettlementFurnaceState> furnaces = new LinkedHashMap<>();
    private long nextDepartureAt;
    private long nextApplicantAt;
    private SettlementSupportState support = new SettlementSupportState();
    private SettlementEconomy economy = new SettlementEconomy(this::changed);
    private UUID leader;
    private SettlementStage stage = SettlementStage.CAMP;
    private long revision;

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

    public long revision() {
        return this.revision;
    }

    public SettlementStage stage() {
        return this.stage;
    }

    public SettlementEconomy economy() {
        return this.economy;
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
        if (this.residents.remove(resident) != null) {
            changed();
        }
    }

    public Map<UUID, SettlementApplicant> applicants() {
        return Collections.unmodifiableMap(this.applicants);
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

    public Map<UUID, UUID> duties() {
        return Collections.unmodifiableMap(this.duties);
    }

    public Map<UUID, SettlementGuardOrder> guardOrders() {
        return Collections.unmodifiableMap(this.guardOrders);
    }

    boolean setGuardOrder(UUID actor, UUID resident, SettlementGuardOrder order) {
        var area = this.areas.get(this.duties.get(resident));
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

    boolean assignDuty(UUID resident, UUID area) {
        SettlementArea target = this.areas.get(area);
        if (resident == null || target == null || target.type().role() == null) {
            return false;
        }
        if (!area.equals(this.duties.put(resident, area))) {
            this.guardOrders.remove(resident);
            changed();
        }
        return true;
    }

    boolean removeDuty(UUID resident) {
        if (this.duties.remove(resident) == null) {
            return false;
        }
        this.guardOrders.remove(resident);
        changed();
        return true;
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
                .filter(survey -> survey != null && survey.fullyLoaded())
                .mapToInt(survey -> survey.beds().size())
                .sum();
    }

    void recordSurvey(UUID area, SettlementBuildingSurvey.Survey survey) {
        if (!this.areas.containsKey(area)) {
            return;
        }
        var previous = this.surveys.get(area);
        if (previous != null && previous.beds().equals(survey.beds())
                && previous.workstations().equals(survey.workstations()) && previous.fullyLoaded() == survey.fullyLoaded()
                && previous.fixtures().equals(survey.fixtures()) && survey.checkedAt() - previous.checkedAt() < 1_200L) {
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
                || !area.within(this.center, this.stage.radius())
                || !this.areas.containsKey(area.id()) && this.areas.size() >= MAX_AREAS
                || this.areas.containsKey(area.id()) && this.areas.get(area.id()).type() != area.type()
                        && this.duties.containsValue(area.id())
                || this.depots.values().stream().anyMatch(depot -> depot.area().equals(area.id())
                        && (area.type() == SettlementAreaType.HOME || !area.contains(depot.position())))
                || this.areas.values().stream().anyMatch(existing ->
                        !existing.id().equals(area.id()) && existing.intersects(area))) {
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
        this.duties.values().removeIf(area::equals);
        this.guardOrders.keySet().removeIf(resident -> !this.duties.containsKey(resident));
        this.surveys.remove(area);
        changed();
        return true;
    }

    void advance() {
        this.stage.next().ifPresent(next -> {
            this.stage = next;
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
        tag.putString("Stage", this.stage.name());
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
        tag.put("Support", this.support.save());
        tag.put("Economy", this.economy.save());
        ListTag residentTags = new ListTag();
        this.residents.values().forEach(resident -> residentTags.add(resident.save()));
        tag.put("ResidentCare", residentTags);
        ListTag workerTags = new ListTag();
        this.workers.values().forEach(worker -> workerTags.add(worker.save()));
        tag.put("Workers", workerTags);
        ListTag furnaceTags = new ListTag();
        this.furnaces.values().forEach(furnace -> furnaceTags.add(furnace.save()));
        tag.put("Furnaces", furnaceTags);
        ListTag departureTags = new ListTag();
        this.departures.values().forEach(departure -> departureTags.add(departure.save()));
        tag.put("Departures", departureTags);
        tag.putLong("NextDepartureAt", this.nextDepartureAt);
        tag.putLong("NextApplicantAt", this.nextApplicantAt);
        ListTag applicantTags = new ListTag();
        this.applicants.values().forEach(applicant -> applicantTags.add(applicant.save()));
        tag.put("Applicants", applicantTags);
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
        record.stage = SettlementStage.valueOf(tag.getString("Stage"));
        record.support = SettlementSupportState.load(tag.getCompound("Support"));
        record.economy = SettlementEconomy.load(tag.getCompound("Economy"), record::changed);
        for (Tag raw : tag.getList("ResidentCare", Tag.TAG_COMPOUND)) {
            var resident = SettlementResidentState.load((CompoundTag) raw);
            if (record.residents.putIfAbsent(resident.id(), resident) != null) {
                throw new IllegalArgumentException("Resident care duplicates an identity");
            }
        }
        for (Tag raw : tag.getList("Workers", Tag.TAG_COMPOUND)) {
            var worker = SettlementWorkerState.load((CompoundTag) raw);
            if (record.workers.putIfAbsent(worker.id(), worker) != null) {
                throw new IllegalArgumentException("Background worker duplicates a resident identity");
            }
        }
        for (Tag raw : tag.getList("Furnaces", Tag.TAG_COMPOUND)) {
            var furnace = SettlementFurnaceState.load((CompoundTag) raw);
            if (record.furnaces.putIfAbsent(furnace.position(), furnace) != null) {
                throw new IllegalArgumentException("Settlement furnace duplicates a position");
            }
        }
        record.nextDepartureAt = Math.max(0L, tag.getLong("NextDepartureAt"));
        for (Tag raw : tag.getList("Departures", Tag.TAG_COMPOUND)) {
            var departure = SettlementDeparture.load((CompoundTag) raw);
            if (record.departures.putIfAbsent(departure.id(), departure) != null) {
                throw new IllegalArgumentException("Departure duplicates a resident identity");
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
            if (!area.within(record.center, record.stage.radius()) || record.areas.size() >= MAX_AREAS
                    || record.areas.containsKey(area.id())
                    || record.areas.values().stream().anyMatch(existing -> existing.intersects(area))) {
                throw new IllegalArgumentException("Settlement areas exceed their bounds or overlap");
            }
            record.areas.put(area.id(), area);
        }
        for (Tag raw : tag.getList("Duties", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Resident") || !entry.hasUUID("Area")) {
                throw new IllegalArgumentException("Settlement duty is missing its resident or area");
            }
            SettlementArea area = record.areas.get(entry.getUUID("Area"));
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
            var area = record.areas.get(record.duties.get(resident));
            var order = SettlementGuardOrder.load(entry);
            if (area == null || area.type() != SettlementAreaType.GUARD_POST || !order.within(record)
                    || record.guardOrders.putIfAbsent(resident, order) != null) {
                throw new IllegalArgumentException("Guard order is duplicated or outside its settlement");
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
            SettlementArea area = entry.hasUUID("Area") ? record.areas.get(entry.getUUID("Area")) : null;
            var survey = SettlementBuildingSurvey.Survey.load(entry);
            if (area == null || survey.beds().stream().anyMatch(pos -> !area.contains(pos))
                    || survey.workstations().stream().anyMatch(pos -> !area.contains(pos))
                    || record.surveys.putIfAbsent(area.id(), survey) != null) {
                throw new IllegalArgumentException("Building survey is duplicated or outside its area");
            }
        }
        return record;
    }
}
