package com.jvn.villagerretaliation.mount;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class VillagerMountAssignmentSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_mount_assignments";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_ASSIGNMENTS = "Assignments";
    private static final String TAG_VILLAGER = "Villager";
    private static final String TAG_MOUNT = "Mount";
    private static final String TAG_MOUNT_TYPE = "MountType";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POSITION = "Position";
    private static final String TAG_PARKING_DIMENSION = "ParkingDimension";
    private static final String TAG_PARKING_POSITION = "ParkingPosition";
    private static final String TAG_ASSIGNED_TIME = "AssignedGameTime";
    private static final int CURRENT_VERSION = 1;

    private final Map<UUID, VillagerMountAssignment> byVillager = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashMap<UUID, VillagerMountAssignment>> byMount = new LinkedHashMap<>();
    private final Collection<VillagerMountAssignment> assignments =
            Collections.unmodifiableCollection(this.byVillager.values());

    public static VillagerMountAssignmentSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerMountAssignmentSavedData::new,
                        VillagerMountAssignmentSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerMountAssignmentSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerMountAssignmentSavedData data = new VillagerMountAssignmentSavedData();
        ListTag assignments = tag.getList(TAG_ASSIGNMENTS, Tag.TAG_COMPOUND);
        for (Tag raw : assignments) {
            if (!(raw instanceof CompoundTag entry)) {
                continue;
            }
            VillagerMountAssignment assignment = readAssignment(entry);
            if (assignment != null
                    && !data.byVillager.containsKey(assignment.villagerId())) {
                data.byVillager.put(assignment.villagerId(), assignment);
                data.mountAssignments(assignment.mountId()).put(assignment.villagerId(), assignment);
            }
        }
        data.setDirty();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt(TAG_VERSION, CURRENT_VERSION);
        ListTag list = new ListTag();
        for (VillagerMountAssignment assignment : byVillager.values()) {
            list.add(writeAssignment(assignment));
        }
        tag.put(TAG_ASSIGNMENTS, list);
        return tag;
    }

    public Optional<VillagerMountAssignment> forVillager(UUID villagerId) {
        return Optional.ofNullable(villagerId == null ? null : byVillager.get(villagerId));
    }

    public Optional<VillagerMountAssignment> forMount(UUID mountId) {
        List<VillagerMountAssignment> records = assignmentsForMount(mountId);
        return records.isEmpty() ? Optional.empty() : Optional.of(records.getFirst());
    }

    public List<VillagerMountAssignment> assignmentsForMount(UUID mountId) {
        Map<UUID, VillagerMountAssignment> records = mountId == null ? null : byMount.get(mountId);
        return records == null ? List.of() : List.copyOf(records.values());
    }

    public Collection<VillagerMountAssignment> assignments() {
        return assignments;
    }

    public boolean assign(VillagerMountAssignment assignment) {
        if (assignment == null
                || byVillager.containsKey(assignment.villagerId())) {
            return false;
        }
        byVillager.put(assignment.villagerId(), assignment);
        mountAssignments(assignment.mountId()).put(assignment.villagerId(), assignment);
        setDirty();
        return true;
    }

    public VillagerMountAssignment removeForVillager(UUID villagerId) {
        VillagerMountAssignment removed = byVillager.remove(villagerId);
        if (removed != null) {
            Map<UUID, VillagerMountAssignment> records = byMount.get(removed.mountId());
            if (records != null) {
                records.remove(removed.villagerId());
                if (records.isEmpty()) {
                    byMount.remove(removed.mountId());
                }
            }
            setDirty();
        }
        return removed;
    }

    public List<VillagerMountAssignment> removeForMount(UUID mountId) {
        Map<UUID, VillagerMountAssignment> removed = byMount.remove(mountId);
        if (removed != null && !removed.isEmpty()) {
            removed.values().forEach(assignment -> byVillager.remove(assignment.villagerId(), assignment));
            setDirty();
        }
        return removed == null ? List.of() : List.copyOf(removed.values());
    }

    public boolean updateMountLocation(UUID mountId, ResourceLocation dimension, BlockPos position) {
        List<VillagerMountAssignment> current = assignmentsForMount(mountId);
        if (current.isEmpty() || dimension == null || position == null) {
            return false;
        }
        boolean changed = false;
        for (VillagerMountAssignment assignment : current) {
            if (!dimension.equals(assignment.mountDimension()) || !position.equals(assignment.lastMountPosition())) {
                replace(assignment, assignment.withMountLocation(dimension, position.immutable()));
                changed = true;
            }
        }
        return changed;
    }

    public boolean setParkingAnchor(UUID villagerId, ResourceLocation dimension, BlockPos position) {
        VillagerMountAssignment current = byVillager.get(villagerId);
        if (current == null) {
            return false;
        }
        VillagerMountAssignment updated = dimension == null || position == null
                ? current.withoutParkingAnchor()
                : current.withParkingAnchor(dimension, position.immutable());
        if (updated.equals(current)) {
            return false;
        }
        replace(current, updated);
        return true;
    }

    private void replace(VillagerMountAssignment current, VillagerMountAssignment updated) {
        byVillager.put(current.villagerId(), updated);
        mountAssignments(current.mountId()).put(current.villagerId(), updated);
        setDirty();
    }

    private LinkedHashMap<UUID, VillagerMountAssignment> mountAssignments(UUID mountId) {
        return byMount.computeIfAbsent(mountId, ignored -> new LinkedHashMap<>());
    }

    private static CompoundTag writeAssignment(VillagerMountAssignment assignment) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_VILLAGER, assignment.villagerId());
        tag.putUUID(TAG_MOUNT, assignment.mountId());
        tag.putString(TAG_MOUNT_TYPE, assignment.mountType().toString());
        tag.putString(TAG_DIMENSION, assignment.mountDimension().toString());
        tag.putLong(TAG_POSITION, assignment.lastMountPosition().asLong());
        if (assignment.parkingDimension() != null && assignment.parkingPosition() != null) {
            tag.putString(TAG_PARKING_DIMENSION, assignment.parkingDimension().toString());
            tag.putLong(TAG_PARKING_POSITION, assignment.parkingPosition().asLong());
        }
        tag.putLong(TAG_ASSIGNED_TIME, assignment.assignedGameTime());
        return tag;
    }

    private static VillagerMountAssignment readAssignment(CompoundTag tag) {
        if (!tag.hasUUID(TAG_VILLAGER)
                || !tag.hasUUID(TAG_MOUNT)
                || !tag.contains(TAG_MOUNT_TYPE, Tag.TAG_STRING)
                || !tag.contains(TAG_DIMENSION, Tag.TAG_STRING)
                || !tag.contains(TAG_POSITION, Tag.TAG_LONG)) {
            return null;
        }
        ResourceLocation mountType = ResourceLocation.tryParse(tag.getString(TAG_MOUNT_TYPE));
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        if (mountType == null || dimension == null) {
            return null;
        }
        ResourceLocation parkingDimension = tag.contains(TAG_PARKING_DIMENSION, Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString(TAG_PARKING_DIMENSION))
                : null;
        BlockPos parkingPosition = parkingDimension != null && tag.contains(TAG_PARKING_POSITION, Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong(TAG_PARKING_POSITION))
                : null;
        return new VillagerMountAssignment(
                tag.getUUID(TAG_VILLAGER),
                tag.getUUID(TAG_MOUNT),
                mountType,
                dimension,
                BlockPos.of(tag.getLong(TAG_POSITION)),
                parkingDimension,
                parkingPosition,
                tag.getLong(TAG_ASSIGNED_TIME)
        );
    }
}
