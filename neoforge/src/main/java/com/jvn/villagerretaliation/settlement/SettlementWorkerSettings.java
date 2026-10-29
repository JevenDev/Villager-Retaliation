package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.HiredFishingSpots;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class SettlementWorkerSettings {
    public static final int MAX_FILTERS = 128;
    public static final int LOGS_OVERRIDE = 1 << 28;
    public static final int ANIMALS_OVERRIDE = 1 << 29;
    public static final int FISHING_SPOTS_OVERRIDE = 1 << 30;
    private static final String APPLIED_TAG = "SettlementWorkerSettings";

    private final HiredVillagerRole role;
    private final Map<SettlementWorkerSetting, Integer> options;
    private final Set<ResourceLocation> logs;
    private final Set<ResourceLocation> animals;
    private final HiredFishingSpots fishingSpots;
    private final SettlementStorageLinks storage;
    private final CompoundTag saved;
    private final int overrides;
    private volatile Resolution resolution;
    private volatile OverrideChange overrideChange;

    public SettlementWorkerSettings(HiredVillagerRole role, Map<SettlementWorkerSetting, Integer> options,
                                    Set<ResourceLocation> logs, Set<ResourceLocation> animals) {
        this(role, options, logs, animals, allOverrides(role));
    }

    public SettlementWorkerSettings(HiredVillagerRole role, Map<SettlementWorkerSetting, Integer> options,
                                    Set<ResourceLocation> logs, Set<ResourceLocation> animals, int overrides) {
        this(role, options, logs, animals, overrides, HiredFishingSpots.AUTOMATIC);
    }

    public SettlementWorkerSettings(HiredVillagerRole role, Map<SettlementWorkerSetting, Integer> options,
                                    Set<ResourceLocation> logs, Set<ResourceLocation> animals, int overrides, HiredFishingSpots fishingSpots) {
        this(role, options, logs, animals, overrides, fishingSpots, SettlementStorageLinks.AUTOMATIC);
    }

    public SettlementWorkerSettings(HiredVillagerRole role, Map<SettlementWorkerSetting, Integer> options,
                                    Set<ResourceLocation> logs, Set<ResourceLocation> animals, int overrides, HiredFishingSpots fishingSpots,
                                    SettlementStorageLinks storage) {
        this.role = Objects.requireNonNull(role);
        if ((overrides & ~allOverrides(role)) != 0) {
            throw new IllegalArgumentException("Overrides do not match their role");
        }
        this.overrides = overrides;
        this.options = Map.copyOf(options);
        this.logs = Set.copyOf(logs);
        this.animals = Set.copyOf(animals);
        this.fishingSpots = Objects.requireNonNull(fishingSpots);
        this.storage = Objects.requireNonNull(storage);
        for (var setting : SettlementWorkerSetting.values()) {
            Integer value = options.get(setting);
            if (setting.supports(role) ? value == null || !setting.definition().valid(role, value) : value != null) {
                throw new IllegalArgumentException("Worker option does not match its role or range");
            }
        }
        if (logs.size() > MAX_FILTERS || animals.size() > MAX_FILTERS
                || role != HiredVillagerRole.LOGGING && !logs.isEmpty()
                || role != HiredVillagerRole.ANIMAL_HANDLING && !animals.isEmpty()
                || role != HiredVillagerRole.FISHING && !fishingSpots.positions().isEmpty()) {
            throw new IllegalArgumentException("Worker filters do not match their role or limit");
        }
        this.saved = encode();
    }

    public HiredVillagerRole role() {
        return this.role;
    }

    public Map<SettlementWorkerSetting, Integer> options() {
        return this.options;
    }

    public Set<ResourceLocation> logs() {
        return this.logs;
    }

    public Set<ResourceLocation> animals() {
        return this.animals;
    }

    public HiredFishingSpots fishingSpots() { return this.fishingSpots; }
    public SettlementStorageLinks storage() { return this.storage; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SettlementWorkerSettings settings && this.role == settings.role
                && this.options.equals(settings.options) && this.logs.equals(settings.logs) && this.animals.equals(settings.animals)
                && this.overrides == settings.overrides && this.fishingSpots.equals(settings.fishingSpots) && this.storage.equals(settings.storage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.role, this.options, this.logs, this.animals, this.overrides, this.fishingSpots, this.storage);
    }

    public static SettlementWorkerSettings capture(HiredVillagerRole role, CompoundTag state) {
        var options = new EnumMap<SettlementWorkerSetting, Integer>(SettlementWorkerSetting.class);
        for (var setting : SettlementWorkerSetting.values()) {
            if (setting.supports(role)) {
                options.put(setting, setting.read(state));
            }
        }
        return new SettlementWorkerSettings(role, options,
                role == HiredVillagerRole.LOGGING ? HiredLoggingFilters.selectedFilterIds(state) : Set.of(),
                role == HiredVillagerRole.ANIMAL_HANDLING ? HiredAnimalBreedingTargets.selectedTargetIds(state) : Set.of(), allOverrides(role),
                role == HiredVillagerRole.FISHING ? HiredFishingSpots.read(state) : HiredFishingSpots.AUTOMATIC,
                SettlementStorageLinks.read(state));
    }

    public SettlementWorkerSettings with(SettlementWorkerSetting setting, int value) {
        var changed = new EnumMap<SettlementWorkerSetting, Integer>(SettlementWorkerSetting.class);
        changed.putAll(this.options);
        changed.put(setting, value);
        return new SettlementWorkerSettings(this.role, changed, this.logs, this.animals, this.overrides | 1 << setting.ordinal(), this.fishingSpots, this.storage);
    }

    public int overrides() {
        return this.overrides;
    }

    public boolean overrides(SettlementWorkerSetting setting) {
        return (this.overrides & 1 << setting.ordinal()) != 0;
    }

    public SettlementWorkerSettings withOverrides(int overrides) {
        if (overrides == this.overrides) return this;
        var cached = this.overrideChange;
        if (cached != null && cached.overrides == overrides) return cached.settings;
        var settings = new SettlementWorkerSettings(this.role, this.options, this.logs, this.animals, overrides, this.fishingSpots, this.storage);
        this.overrideChange = new OverrideChange(overrides, settings);
        return settings;
    }

    private record OverrideChange(int overrides, SettlementWorkerSettings settings) { }
    private record Resolution(SettlementWorkerSettings worksite, SettlementWorkerSettings settings) { }

    public SettlementWorkerSettings withFilters(Set<ResourceLocation> logs, Set<ResourceLocation> animals) {
        int mask = this.role == HiredVillagerRole.LOGGING ? LOGS_OVERRIDE
                : this.role == HiredVillagerRole.ANIMAL_HANDLING ? ANIMALS_OVERRIDE : 0;
        return new SettlementWorkerSettings(this.role, this.options, logs, animals, this.overrides | mask, this.fishingSpots, this.storage);
    }

    public SettlementWorkerSettings withFishingSpots(HiredFishingSpots fishingSpots) {
        if (this.role != HiredVillagerRole.FISHING) throw new IllegalArgumentException("Fishing spots require a fisher");
        return new SettlementWorkerSettings(this.role, this.options, this.logs, this.animals,
                this.overrides | FISHING_SPOTS_OVERRIDE, fishingSpots, this.storage);
    }

    public SettlementWorkerSettings withStorage(SettlementStorageLinks storage) {
        return new SettlementWorkerSettings(this.role, this.options, this.logs, this.animals,
                this.overrides | SettlementStorageLinks.OVERRIDE, this.fishingSpots, storage);
    }

    public SettlementWorkerSettings resolve(SettlementWorkerSettings worksite) {
        if (this.role != worksite.role) throw new IllegalArgumentException("Worksite role changed");
        var cached = this.resolution;
        if (cached != null && cached.worksite == worksite) return cached.settings;
        var effective = new EnumMap<SettlementWorkerSetting, Integer>(SettlementWorkerSetting.class);
        worksite.options.forEach((setting, value) -> effective.put(setting, overrides(setting) ? this.options.get(setting) : value));
        var settings = new SettlementWorkerSettings(this.role, effective,
                (this.overrides & LOGS_OVERRIDE) != 0 ? this.logs : worksite.logs,
                (this.overrides & ANIMALS_OVERRIDE) != 0 ? this.animals : worksite.animals, this.overrides,
                (this.overrides & FISHING_SPOTS_OVERRIDE) != 0 ? this.fishingSpots : worksite.fishingSpots,
                (this.overrides & SettlementStorageLinks.OVERRIDE) != 0 ? this.storage : worksite.storage);
        this.resolution = new Resolution(worksite, settings);
        return settings;
    }

    public static int allOverrides(HiredVillagerRole role) {
        int mask = SettlementStorageLinks.OVERRIDE;
        for (var setting : SettlementWorkerSetting.values()) {
            if (setting.supports(role)) mask |= 1 << setting.ordinal();
        }
        if (role == HiredVillagerRole.LOGGING) mask |= LOGS_OVERRIDE;
        if (role == HiredVillagerRole.ANIMAL_HANDLING) mask |= ANIMALS_OVERRIDE;
        if (role == HiredVillagerRole.FISHING) mask |= FISHING_SPOTS_OVERRIDE;
        return mask;
    }

    public boolean enabled() {
        return this.options.get(SettlementWorkerSetting.ENABLED) != 0;
    }

    boolean apply(CompoundTag state, boolean areaEnabled) {
        boolean changed = !this.saved.equals(state.getCompound(APPLIED_TAG));
        if (changed) {
            this.options.forEach((setting, value) -> setting.apply(state, value));
            state.put(SettlementStorageLinks.STATE_TAG, this.storage.save());
            if (this.role == HiredVillagerRole.LOGGING) {
                state.put("LoggingFilters", filters(this.logs));
                state.putString(HiredLoggingFilters.LEGACY_FILTER_TAG, "any");
            } else if (this.role == HiredVillagerRole.ANIMAL_HANDLING) {
                state.put("AnimalBreedingTargets", filters(this.animals));
            } else if (this.role == HiredVillagerRole.FISHING) {
                this.fishingSpots.apply(state);
            }
            state.put(APPLIED_TAG, this.saved.copy());
        }
        boolean enabled = enabled() && areaEnabled;
        changed |= state.getBoolean("Enabled") != enabled;
        state.putBoolean("Enabled", enabled);
        return changed;
    }

    CompoundTag save() {
        return this.saved.copy();
    }

    private CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 5);
        tag.put("Storage", this.storage.save());
        tag.putString("Role", this.role.name());
        CompoundTag settings = new CompoundTag();
        this.options.forEach((key, value) -> settings.putInt(key.name(), value));
        tag.put("Options", settings);
        tag.putInt("Overrides", this.overrides);
        tag.put("Logs", filters(this.logs));
        tag.put("Animals", filters(this.animals));
        tag.put("FishingSpots", this.fishingSpots.save());
        return tag;
    }

    static SettlementWorkerSettings load(CompoundTag tag) {
        if (tag.contains("Overrides") && !tag.contains("Overrides", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Worker overrides must be an integer");
        }
        if (tag.contains("Version") && (!tag.contains("Version", Tag.TAG_INT) || (tag.getInt("Version") < 2 || tag.getInt("Version") > 5))) {
            throw new IllegalArgumentException("Unsupported worker settings version");
        }
        var role = HiredVillagerRole.valueOf(tag.getString("Role"));
        var options = new EnumMap<SettlementWorkerSetting, Integer>(SettlementWorkerSetting.class);
        CompoundTag values = tag.getCompound("Options");
        for (String key : values.getAllKeys()) {
            if (!values.contains(key, Tag.TAG_INT)) {
                throw new IllegalArgumentException("Worker option must be an integer");
            }
            options.put(SettlementWorkerSetting.valueOf(key), values.getInt(key));
        }
        if (tag.contains("Version") && tag.getInt("Version") < 5 && role == HiredVillagerRole.FARMING)
            options.putIfAbsent(SettlementWorkerSetting.FARM_BUCKETS, 0);
        boolean legacyFishing = role == HiredVillagerRole.FISHING && tag.getInt("Version") < 3;
        boolean legacyStorage = tag.getInt("Version") < 4;
        int legacyMask = allOverrides(role) & (legacyFishing ? ~FISHING_SPOTS_OVERRIDE : -1) & (legacyStorage ? ~SettlementStorageLinks.OVERRIDE : -1);
        int overrides = tag.contains("Overrides", Tag.TAG_INT) ? tag.getInt("Overrides") : legacyMask;
        if (!tag.contains("Version")) {
            int added = 0;
            for (var setting : SettlementWorkerSetting.values()) {
                if (setting.ordinal() > SettlementWorkerSetting.GUARD_MODE.ordinal() && setting.supports(role)) {
                    if (options.containsKey(setting)) throw new IllegalArgumentException("Unversioned worker option");
                    options.put(setting, setting.read(new CompoundTag()));
                    added |= 1 << setting.ordinal();
                }
            }
            if ((overrides | added) == legacyMask) overrides |= added;
        }
        if (legacyFishing && overrides == legacyMask) overrides |= FISHING_SPOTS_OVERRIDE;
        if (legacyStorage && overrides == (allOverrides(role) & ~SettlementStorageLinks.OVERRIDE)) overrides |= SettlementStorageLinks.OVERRIDE;
        if (tag.getInt("Version") >= 4 ? !tag.contains("Storage", Tag.TAG_COMPOUND) : tag.contains("Storage")) {
            throw new IllegalArgumentException("Invalid storage links in worker settings");
        }
        if (tag.getInt("Version") >= 3 ? !tag.contains("FishingSpots", Tag.TAG_COMPOUND) : tag.contains("FishingSpots")) {
            throw new IllegalArgumentException("Invalid fishing preferences in worker settings");
        }
        return new SettlementWorkerSettings(role, options, readFilters(tag.getList("Logs", Tag.TAG_STRING)),
                readFilters(tag.getList("Animals", Tag.TAG_STRING)), overrides,
                tag.getInt("Version") >= 3 ? HiredFishingSpots.load(tag.getCompound("FishingSpots")) : HiredFishingSpots.AUTOMATIC,
                legacyStorage ? SettlementStorageLinks.AUTOMATIC : SettlementStorageLinks.load(tag.getCompound("Storage")));
    }

    private static ListTag filters(Set<ResourceLocation> values) {
        ListTag list = new ListTag();
        values.stream().sorted().forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }

    private static Set<ResourceLocation> readFilters(ListTag tags) {
        if (tags.size() > MAX_FILTERS) {
            throw new IllegalArgumentException("Too many worker filters");
        }
        Set<ResourceLocation> ids = new HashSet<>();
        for (Tag tag : tags) {
            if (!ids.add(ResourceLocation.parse(tag.getAsString()))) {
                throw new IllegalArgumentException("Duplicate worker filter");
            }
        }
        return ids;
    }
}
