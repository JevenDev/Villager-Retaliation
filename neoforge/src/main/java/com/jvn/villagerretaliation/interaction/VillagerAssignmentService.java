package com.jvn.villagerretaliation.interaction;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.npc.Villager;

/**
 * Single persisted source of truth for ownership and commands. Contract payment
 * details remain in the contract compound, but may not independently control a villager.
 */
public final class VillagerAssignmentService {
    private static final String ASSIGNMENT_TAG = "VillagerRetaliationAssignment";
    private static final String STATE_TAG = "State";
    private static final String OWNER_TAG = "Owner";
    private static final String COMMAND_TAG = "Command";
    private static final String ROLE_TAG = "Role";
    private static final String HIRED_AT_TAG = "HiredAt";
    private static final String REVISION_TAG = "Revision";
    private static final String SCHEMA_VERSION_TAG = "SchemaVersion";
    private static final String WORK_ANCHOR_TAG = "WorkAnchor";
    private static final String HOME_ANCHOR_TAG = "HomeAnchor";
    private static final String X_TAG = "X";
    private static final String Y_TAG = "Y";
    private static final String Z_TAG = "Z";

    private VillagerAssignmentService() {
    }

    public static VillagerAssignmentSnapshot snapshot(Villager villager) {
        if (villager == null || !villager.getPersistentData().contains(ASSIGNMENT_TAG, Tag.TAG_COMPOUND)) {
            return VillagerAssignmentSnapshot.unassigned(0L);
        }
        CompoundTag tag = villager.getPersistentData().getCompound(ASSIGNMENT_TAG);
        VillagerAssignmentState state = readEnum(
                VillagerAssignmentState.class, tag.getString(STATE_TAG), VillagerAssignmentState.UNASSIGNED);
        Optional<UUID> owner = state == VillagerAssignmentState.HIRED && tag.hasUUID(OWNER_TAG)
                ? Optional.of(tag.getUUID(OWNER_TAG))
                : Optional.empty();
        if (owner.isEmpty()) {
            state = VillagerAssignmentState.UNASSIGNED;
        }
        VillagerAssignmentCommand command = readEnum(
                VillagerAssignmentCommand.class,
                tag.getString(COMMAND_TAG),
                state == VillagerAssignmentState.HIRED ? VillagerAssignmentCommand.WORK : VillagerAssignmentCommand.RETURN_HOME);
        HiredVillagerRole role = HiredVillagerRole.bySerializedName(tag.getString(ROLE_TAG));
        return new VillagerAssignmentSnapshot(
                state,
                owner,
                command,
                role,
                readPos(tag, WORK_ANCHOR_TAG),
                readPos(tag, HOME_ANCHOR_TAG),
                tag.getLong(HIRED_AT_TAG),
                Math.max(0L, tag.getLong(REVISION_TAG)),
                Math.max(1, tag.getInt(SCHEMA_VERSION_TAG)));
    }

    public static VillagerAssignmentSnapshot hire(
            Villager villager, UUID owner, HiredVillagerRole role, long hiredAt, BlockPos homeAnchor) {
        if (villager == null || owner == null || role == null) {
            return snapshot(villager);
        }
        VillagerAssignmentSnapshot before = snapshot(villager);
        if (before.state() == VillagerAssignmentState.HIRED && !before.ownedBy(owner)) {
            return before;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(STATE_TAG, VillagerAssignmentState.HIRED.name());
        tag.putUUID(OWNER_TAG, owner);
        tag.putString(COMMAND_TAG, VillagerAssignmentCommand.WORK.name());
        tag.putString(ROLE_TAG, role.serializedName());
        tag.putLong(HIRED_AT_TAG, hiredAt);
        tag.putLong(REVISION_TAG, before.revision() + 1L);
        tag.putInt(SCHEMA_VERSION_TAG, VillagerAssignmentSnapshot.CURRENT_SCHEMA_VERSION);
        putPos(tag, HOME_ANCHOR_TAG, homeAnchor);
        villager.getPersistentData().put(ASSIGNMENT_TAG, tag);
        villager.setPersistenceRequired();
        return snapshot(villager);
    }

    public static VillagerAssignmentSnapshot setCommand(
            Villager villager, UUID owner, VillagerAssignmentCommand command, BlockPos anchor) {
        VillagerAssignmentSnapshot before = snapshot(villager);
        if (command == null || !before.ownedBy(owner)) {
            return before;
        }
        CompoundTag tag = villager.getPersistentData().getCompound(ASSIGNMENT_TAG);
        tag.putString(COMMAND_TAG, command.name());
        if (command == VillagerAssignmentCommand.STAY) {
            putPos(tag, WORK_ANCHOR_TAG, anchor);
        } else if (command == VillagerAssignmentCommand.RETURN_HOME) {
            tag.remove(WORK_ANCHOR_TAG);
        }
        tag.putLong(REVISION_TAG, before.revision() + 1L);
        villager.setPersistenceRequired();
        return snapshot(villager);
    }

    public static VillagerAssignmentSnapshot setRole(Villager villager, HiredVillagerRole role) {
        VillagerAssignmentSnapshot before = snapshot(villager);
        if (before.state() != VillagerAssignmentState.HIRED || role == null) {
            return before;
        }
        CompoundTag tag = villager.getPersistentData().getCompound(ASSIGNMENT_TAG);
        tag.putString(ROLE_TAG, role.serializedName());
        tag.putString(COMMAND_TAG, role == HiredVillagerRole.COMBAT
                ? VillagerAssignmentCommand.GUARD.name()
                : VillagerAssignmentCommand.WORK.name());
        tag.putLong(REVISION_TAG, before.revision() + 1L);
        villager.setPersistenceRequired();
        return snapshot(villager);
    }

    public static VillagerAssignmentSnapshot unassign(Villager villager) {
        VillagerAssignmentSnapshot before = snapshot(villager);
        if (villager == null || before.state() == VillagerAssignmentState.UNASSIGNED) {
            return before;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(STATE_TAG, VillagerAssignmentState.UNASSIGNED.name());
        tag.putString(COMMAND_TAG, VillagerAssignmentCommand.RETURN_HOME.name());
        tag.putLong(REVISION_TAG, before.revision() + 1L);
        tag.putInt(SCHEMA_VERSION_TAG, VillagerAssignmentSnapshot.CURRENT_SCHEMA_VERSION);
        villager.getPersistentData().put(ASSIGNMENT_TAG, tag);
        villager.setPersistenceRequired();
        return snapshot(villager);
    }

    public static void clearInheritedStateForNewborn(Villager child) {
        if (child != null) child.getPersistentData().remove(ASSIGNMENT_TAG);
    }

    private static void putPos(CompoundTag parent, String key, BlockPos pos) {
        if (pos == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt(X_TAG, pos.getX());
        tag.putInt(Y_TAG, pos.getY());
        tag.putInt(Z_TAG, pos.getZ());
        parent.put(key, tag);
    }

    private static BlockPos readPos(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = parent.getCompound(key);
        return new BlockPos(tag.getInt(X_TAG), tag.getInt(Y_TAG), tag.getInt(Z_TAG));
    }

    private static <E extends Enum<E>> E readEnum(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
