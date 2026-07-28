package com.jvn.villagerretaliation.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class AssignedStorageSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_assigned_storage";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POS = "Pos";
    private static final String TAG_VILLAGER = "Villager";
    private static final String TAG_HIRER = "Hirer";
    private static final String TAG_PURPOSE = "Purpose";
    private static final String TAG_PRIORITY = "Priority";
    private static final String TAG_VALIDATION = "Validation";
    private static final String TAG_OUTPUT_FILTERS = "OutputFilters";
    private static final String TAG_OUTPUT_FILTER_SNAPSHOT_KNOWN = "OutputFilterSnapshotKnown";

    private final Map<ContainerKey, AssignedContainerRecord> byContainer = new HashMap<>();
    private final Map<UUID, List<AssignedContainerRecord>> byVillager = new HashMap<>();

    public static AssignedStorageSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AssignedStorageSavedData::new, AssignedStorageSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static AssignedStorageSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        AssignedStorageSavedData data = new AssignedStorageSavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        boolean migratedPurpose = false;
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)
                    || !entryTag.hasUUID(TAG_VILLAGER)
                    || !entryTag.contains(TAG_POS, Tag.TAG_LONG)) {
                continue;
            }
            ResourceLocation dimensionId = ResourceLocation.tryParse(entryTag.getString(TAG_DIMENSION));
            if (dimensionId == null) {
                continue;
            }
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            UUID hirer = entryTag.hasUUID(TAG_HIRER) ? entryTag.getUUID(TAG_HIRER) : null;
            List<ItemStack> outputFilters = new ArrayList<>();
            for (Tag rawFilter : entryTag.getList(TAG_OUTPUT_FILTERS, Tag.TAG_COMPOUND)) {
                if (rawFilter instanceof CompoundTag filterTag) {
                    ItemStack filter = ItemStack.parseOptional(provider, filterTag);
                    if (!filter.isEmpty()) {
                        outputFilters.add(filter.copyWithCount(1));
                    }
                }
            }
            String rawPurpose = entryTag.getString(TAG_PURPOSE);
            String purpose = AssignedStorageService.normalizePurpose(rawPurpose);
            migratedPurpose |= !purpose.equals(rawPurpose);
            AssignedContainerRecord record = new AssignedContainerRecord(
                    dimension,
                    BlockPos.of(entryTag.getLong(TAG_POS)),
                    entryTag.getUUID(TAG_VILLAGER),
                    hirer,
                    purpose,
                    entryTag.getInt(TAG_PRIORITY),
                    entryTag.getString(TAG_VALIDATION).isBlank() ? "unknown" : entryTag.getString(TAG_VALIDATION),
                    outputFilters,
                    entryTag.getBoolean(TAG_OUTPUT_FILTER_SNAPSHOT_KNOWN)
            );
            data.put(record);
        }
        if (migratedPurpose) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (AssignedContainerRecord record : this.byContainer.values()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(TAG_DIMENSION, record.dimension().location().toString());
            entryTag.putLong(TAG_POS, record.pos().asLong());
            entryTag.putUUID(TAG_VILLAGER, record.villagerId());
            if (record.hirerId() != null) {
                entryTag.putUUID(TAG_HIRER, record.hirerId());
            }
            entryTag.putString(TAG_PURPOSE, record.purpose());
            entryTag.putInt(TAG_PRIORITY, record.priority());
            entryTag.putString(TAG_VALIDATION, record.validationStatus());
            entryTag.putBoolean(TAG_OUTPUT_FILTER_SNAPSHOT_KNOWN, record.outputFilterSnapshotKnown());
            if (!record.outputFilters().isEmpty()) {
                ListTag filtersTag = new ListTag();
                for (ItemStack filter : record.outputFilters()) {
                    filtersTag.add(filter.saveOptional(provider));
                }
                entryTag.put(TAG_OUTPUT_FILTERS, filtersTag);
            }
            entriesTag.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    public List<AssignedContainerRecord> assignedTo(UUID villagerId) {
        List<AssignedContainerRecord> records = this.byVillager.get(villagerId);
        return records == null || records.isEmpty() ? List.of() : List.copyOf(records);
    }

    public List<AssignedContainerRecord> assignedTo(UUID villagerId, String purpose) {
        String normalizedPurpose = normalizePurpose(purpose);
        List<AssignedContainerRecord> records = this.byVillager.get(villagerId);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<AssignedContainerRecord> matches = new ArrayList<>();
        for (AssignedContainerRecord record : records) {
            if (normalizePurpose(record.purpose()).equals(normalizedPurpose)) {
                matches.add(record);
            }
        }
        return matches;
    }

    public AssignedContainerRecord assignedAt(
            ResourceKey<Level> dimension,
            BlockPos pos,
            UUID villagerId,
            String purpose) {
        if (dimension == null || pos == null || villagerId == null) {
            return null;
        }
        return this.byContainer.get(new ContainerKey(
                dimension, pos.immutable(), villagerId, normalizePurpose(purpose)));
    }

    public AssignmentResult assign(AssignedContainerRecord record) {
        ContainerKey key = key(record);
        AssignedContainerRecord existing = this.byContainer.get(key);
        if (existing != null) {
            return AssignmentResult.ALREADY_ASSIGNED;
        }
        put(record);
        setDirty();
        return AssignmentResult.ASSIGNED;
    }

    public boolean removeAssignment(AssignedContainerRecord record) {
        if (record == null || this.byContainer.remove(key(record)) == null) {
            return false;
        }
        List<AssignedContainerRecord> records = this.byVillager.get(record.villagerId());
        if (records != null) {
            records.removeIf(candidate -> sameAssignment(candidate, record));
            if (records.isEmpty()) {
                this.byVillager.remove(record.villagerId());
            }
        }
        setDirty();
        return true;
    }

    public int removeAssignedTo(UUID villagerId) {
        List<AssignedContainerRecord> records = new ArrayList<>(this.byVillager.getOrDefault(villagerId, List.of()));
        for (AssignedContainerRecord record : records) {
            this.byContainer.remove(key(record));
        }
        this.byVillager.remove(villagerId);
        if (!records.isEmpty()) {
            setDirty();
        }
        return records.size();
    }

    public int transferVillagerAssignments(UUID sourceVillagerId, UUID targetVillagerId) {
        if (sourceVillagerId == null || targetVillagerId == null || sourceVillagerId.equals(targetVillagerId)) {
            return 0;
        }

        List<AssignedContainerRecord> records = new ArrayList<>(this.byVillager.getOrDefault(sourceVillagerId, List.of()));
        if (records.isEmpty()) {
            return 0;
        }

        this.byVillager.remove(sourceVillagerId);
        records.forEach(record -> this.byContainer.remove(key(record)));
        for (AssignedContainerRecord record : records) {
            put(new AssignedContainerRecord(
                    record.dimension(),
                    record.pos(),
                    targetVillagerId,
                    record.hirerId(),
                    record.purpose(),
                    record.priority(),
                    record.validationStatus(),
                    record.outputFilters(),
                    record.outputFilterSnapshotKnown()
            ));
        }
        setDirty();
        return records.size();
    }

    public int removeAssignedTo(UUID villagerId, String purpose) {
        String normalizedPurpose = normalizePurpose(purpose);
        List<AssignedContainerRecord> records = new ArrayList<>(this.byVillager.getOrDefault(villagerId, List.of()).stream()
                .filter(record -> normalizePurpose(record.purpose()).equals(normalizedPurpose))
                .toList());
        for (AssignedContainerRecord record : records) {
            this.byContainer.remove(key(record));
        }
        List<AssignedContainerRecord> remaining = this.byVillager.get(villagerId);
        if (remaining != null) {
            remaining.removeIf(record -> normalizePurpose(record.purpose()).equals(normalizedPurpose));
            if (remaining.isEmpty()) {
                this.byVillager.remove(villagerId);
            }
        }
        if (!records.isEmpty()) {
            setDirty();
        }
        return records.size();
    }

    public boolean removeAssignedAt(ResourceKey<Level> dimension, BlockPos pos) {
        List<AssignedContainerRecord> removed = new ArrayList<>(this.byContainer.values().stream()
                .filter(record -> record.dimension().equals(dimension) && record.pos().equals(pos))
                .toList());
        if (removed.isEmpty()) {
            return false;
        }
        for (AssignedContainerRecord record : removed) {
            this.byContainer.remove(key(record));
            List<AssignedContainerRecord> records = this.byVillager.get(record.villagerId());
            if (records != null) {
                records.removeIf(candidate -> sameAssignment(candidate, record));
                if (records.isEmpty()) {
                    this.byVillager.remove(record.villagerId());
                }
            }
        }
        setDirty();
        return true;
    }

    public void updateValidation(AssignedContainerRecord record, String validationStatus) {
        String safeValidationStatus = validationStatus == null || validationStatus.isBlank() ? "unknown" : validationStatus;
        if (record.validationStatus().equals(safeValidationStatus)) {
            return;
        }
        AssignedContainerRecord updated = new AssignedContainerRecord(
                record.dimension(),
                record.pos(),
                record.villagerId(),
                record.hirerId(),
                record.purpose(),
                record.priority(),
                safeValidationStatus,
                record.outputFilters(),
                record.outputFilterSnapshotKnown()
        );
        put(updated);
        setDirty();
    }

    public void updateOutputFilterSnapshot(AssignedContainerRecord record, List<ItemStack> filters) {
        List<ItemStack> safeFilters = copyFilters(filters);
        if (record.outputFilterSnapshotKnown() && sameFilters(record.outputFilters(), safeFilters)) {
            return;
        }
        put(new AssignedContainerRecord(
                record.dimension(),
                record.pos(),
                record.villagerId(),
                record.hirerId(),
                record.purpose(),
                record.priority(),
                record.validationStatus(),
                safeFilters,
                true));
        setDirty();
    }

    private static List<ItemStack> copyFilters(List<ItemStack> filters) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        return filters.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .toList();
    }

    private static boolean sameFilters(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!ItemStack.matches(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private void put(AssignedContainerRecord record) {
        ContainerKey key = key(record);
        AssignedContainerRecord previous = this.byContainer.put(key, record);
        if (previous != null) {
            List<AssignedContainerRecord> previousList = this.byVillager.get(previous.villagerId());
            if (previousList != null) {
                previousList.removeIf(candidate -> sameAssignment(candidate, previous));
                if (previousList.isEmpty()) {
                    this.byVillager.remove(previous.villagerId());
                }
            }
        }
        List<AssignedContainerRecord> records = this.byVillager.computeIfAbsent(record.villagerId(), ignored -> new ArrayList<>());
        records.removeIf(candidate -> sameAssignment(candidate, record));
        records.add(record);
        records.sort(Comparator
                .comparingInt(AssignedContainerRecord::priority)
                .thenComparing(AssignedContainerRecord::purpose));
    }

    private static boolean sameAssignment(AssignedContainerRecord first, AssignedContainerRecord second) {
        return first.dimension().equals(second.dimension())
                && first.pos().equals(second.pos())
                && normalizePurpose(first.purpose()).equals(normalizePurpose(second.purpose()));
    }

    private static ContainerKey key(AssignedContainerRecord record) {
        return new ContainerKey(
                record.dimension(),
                record.pos().immutable(),
                record.villagerId(),
                normalizePurpose(record.purpose()));
    }

    private static String normalizePurpose(String purpose) {
        return AssignedStorageService.normalizePurpose(purpose);
    }

    public enum AssignmentResult {
        ASSIGNED,
        ALREADY_ASSIGNED
    }

    public record AssignedContainerRecord(
            ResourceKey<Level> dimension,
            BlockPos pos,
            UUID villagerId,
            UUID hirerId,
            String purpose,
            int priority,
            String validationStatus,
            List<ItemStack> outputFilters,
            boolean outputFilterSnapshotKnown) {
        public AssignedContainerRecord {
            pos = pos.immutable();
            purpose = AssignedStorageService.normalizePurpose(purpose);
            outputFilters = copyFilters(outputFilters);
        }

        public AssignedContainerRecord(
                ResourceKey<Level> dimension,
                BlockPos pos,
                UUID villagerId,
                UUID hirerId,
                String purpose,
                int priority,
                String validationStatus) {
            this(dimension, pos, villagerId, hirerId, purpose, priority, validationStatus, List.of(), false);
        }
    }

    private record ContainerKey(ResourceKey<Level> dimension, BlockPos pos, UUID villagerId, String purpose) {
    }
}
