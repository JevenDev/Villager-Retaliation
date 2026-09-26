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

public final class SettlementRecord {
    public static final int MAX_AREAS = 256;
    private final VillageAllegianceId id;
    private final ResourceLocation dimension;
    private final BlockPos center;
    private final long foundedAt;
    private final Runnable dirty;
    private final Map<UUID, Set<SettlementPermission>> delegates = new LinkedHashMap<>();
    private final Map<UUID, SettlementArea> areas = new LinkedHashMap<>();
    private final Map<UUID, UUID> duties = new LinkedHashMap<>();
    private final Map<UUID, SettlementDepot> depots = new LinkedHashMap<>();
    private final Map<UUID, SettlementBuildingSurvey.Survey> surveys = new LinkedHashMap<>();
    private SettlementSupportState support = new SettlementSupportState();
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

    public Map<UUID, Set<SettlementPermission>> delegates() {
        return Collections.unmodifiableMap(this.delegates);
    }

    public Map<UUID, SettlementArea> areas() {
        return Collections.unmodifiableMap(this.areas);
    }

    public Map<UUID, UUID> duties() {
        return Collections.unmodifiableMap(this.duties);
    }

    boolean assignDuty(UUID resident, UUID area) {
        SettlementArea target = this.areas.get(area);
        if (resident == null || target == null || target.type().role() == null) {
            return false;
        }
        if (!area.equals(this.duties.put(resident, area))) {
            changed();
        }
        return true;
    }

    boolean removeDuty(UUID resident) {
        if (this.duties.remove(resident) == null) {
            return false;
        }
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
