package com.jvn.villagerretaliation.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            AssignedContainerRecord record = new AssignedContainerRecord(
                    dimension,
                    BlockPos.of(entryTag.getLong(TAG_POS)),
                    entryTag.getUUID(TAG_VILLAGER),
                    hirer,
                    entryTag.getString(TAG_PURPOSE).isBlank() ? "general" : entryTag.getString(TAG_PURPOSE),
                    entryTag.getInt(TAG_PRIORITY),
                    entryTag.getString(TAG_VALIDATION).isBlank() ? "unknown" : entryTag.getString(TAG_VALIDATION)
            );
            data.put(record);
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

    public Optional<AssignedContainerRecord> assignedAt(ResourceKey<Level> dimension, BlockPos pos) {
        return Optional.ofNullable(this.byContainer.get(new ContainerKey(dimension, pos.immutable())));
    }

    public AssignmentResult assign(AssignedContainerRecord record) {
        ContainerKey key = new ContainerKey(record.dimension(), record.pos().immutable());
        AssignedContainerRecord existing = this.byContainer.get(key);
        if (existing != null && !existing.villagerId().equals(record.villagerId())) {
            return AssignmentResult.ALREADY_ASSIGNED;
        }
        put(record);
        setDirty();
        return AssignmentResult.ASSIGNED;
    }

    public int removeAssignedTo(UUID villagerId) {
        List<AssignedContainerRecord> records = new ArrayList<>(this.byVillager.getOrDefault(villagerId, List.of()));
        for (AssignedContainerRecord record : records) {
            this.byContainer.remove(new ContainerKey(record.dimension(), record.pos()));
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
        for (AssignedContainerRecord record : records) {
            put(new AssignedContainerRecord(
                    record.dimension(),
                    record.pos(),
                    targetVillagerId,
                    record.hirerId(),
                    record.purpose(),
                    record.priority(),
                    record.validationStatus()
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
            this.byContainer.remove(new ContainerKey(record.dimension(), record.pos()));
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
        AssignedContainerRecord removed = this.byContainer.remove(new ContainerKey(dimension, pos.immutable()));
        if (removed == null) {
            return false;
        }
        List<AssignedContainerRecord> records = this.byVillager.get(removed.villagerId());
        if (records != null) {
            records.removeIf(candidate -> candidate.dimension().equals(removed.dimension()) && candidate.pos().equals(removed.pos()));
            if (records.isEmpty()) {
                this.byVillager.remove(removed.villagerId());
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
                safeValidationStatus
        );
        put(updated);
        setDirty();
    }

    private void put(AssignedContainerRecord record) {
        ContainerKey key = new ContainerKey(record.dimension(), record.pos().immutable());
        AssignedContainerRecord previous = this.byContainer.put(key, record);
        if (previous != null) {
            List<AssignedContainerRecord> previousList = this.byVillager.get(previous.villagerId());
            if (previousList != null) {
                previousList.removeIf(candidate -> candidate.dimension().equals(previous.dimension()) && candidate.pos().equals(previous.pos()));
                if (previousList.isEmpty()) {
                    this.byVillager.remove(previous.villagerId());
                }
            }
        }
        List<AssignedContainerRecord> records = this.byVillager.computeIfAbsent(record.villagerId(), ignored -> new ArrayList<>());
        records.removeIf(candidate -> candidate.dimension().equals(record.dimension()) && candidate.pos().equals(record.pos()));
        records.add(record);
        records.sort(Comparator.comparingInt(AssignedContainerRecord::priority));
    }

    private static String normalizePurpose(String purpose) {
        return purpose == null || purpose.isBlank() ? "general" : purpose;
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
            String validationStatus) {
    }

    private record ContainerKey(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
