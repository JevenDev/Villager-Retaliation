package com.jvn.villagerretaliation.interaction;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.npc.Villager;

/**
 * Persistence boundary for recruitment assignments and command journey state.
 * Nothing outside this class needs to know the NBT layout or legacy keys.
 */
public final class VillagerAssignmentStore {
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

    // Version-zero recruitment keys. They remain readable while old worlds migrate.
    private static final String FOLLOWING_PLAYER_KEY = "VillagerRetaliationFollowingPlayer";
    private static final String FOLLOW_MODE_KEY = "VillagerRetaliationFollowMode";
    private static final String FOLLOW_MODE_FOLLOW = "follow";
    private static final String FOLLOW_MODE_STAY = "stay";
    private static final String FOLLOW_START_HEALTH_KEY = "VillagerRetaliationFollowStartHealth";
    private static final String FOLLOW_MIN_HEALTH_KEY = "VillagerRetaliationFollowMinHealth";
    private static final String FOLLOW_START_X_KEY = "VillagerRetaliationFollowStartX";
    private static final String FOLLOW_START_Y_KEY = "VillagerRetaliationFollowStartY";
    private static final String FOLLOW_START_Z_KEY = "VillagerRetaliationFollowStartZ";
    private static final String FOLLOW_START_BIOME_KEY = "VillagerRetaliationFollowStartBiome";
    private static final String FOLLOW_MAX_DISTANCE_KEY = "VillagerRetaliationFollowMaxDistance";
    private static final String FOLLOW_USED_BOAT_KEY = "VillagerRetaliationFollowUsedBoat";
    private static final String FOLLOW_CROSSED_OCEAN_KEY = "VillagerRetaliationFollowCrossedOcean";
    private static final String STAY_ANCHOR_X_KEY = "VillagerRetaliationStayAnchorX";
    private static final String STAY_ANCHOR_Y_KEY = "VillagerRetaliationStayAnchorY";
    private static final String STAY_ANCHOR_Z_KEY = "VillagerRetaliationStayAnchorZ";

    private VillagerAssignmentStore() {
    }

    public static VillagerAssignmentSnapshot snapshot(Villager villager) {
        if (villager == null || !villager.getPersistentData().contains(ASSIGNMENT_TAG, Tag.TAG_COMPOUND)) {
            return VillagerAssignmentSnapshot.unassigned(0L);
        }
        CompoundTag tag = villager.getPersistentData().getCompound(ASSIGNMENT_TAG);
        CompoundTag original = tag.copy();
        migrateAndValidate(villager, tag);
        if (!tag.equals(original)) {
            villager.getPersistentData().put(ASSIGNMENT_TAG, tag);
            villager.setPersistenceRequired();
        }
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
                state == VillagerAssignmentState.HIRED
                        ? VillagerAssignmentCommand.WORK
                        : VillagerAssignmentCommand.RETURN_HOME);
        HiredVillagerRole role = HiredVillagerRole.bySerializedName(tag.getString(ROLE_TAG));
        if (state == VillagerAssignmentState.HIRED && role == null) {
            // A malformed role must not invalidate ownership. The contract synchronizer can
            // restore it, while the villager safely remains in a non-specialized work state.
            command = command == VillagerAssignmentCommand.GUARD
                    ? VillagerAssignmentCommand.WORK
                    : command;
        }
        return new VillagerAssignmentSnapshot(
                state,
                owner,
                command,
                role,
                readPos(tag, WORK_ANCHOR_TAG),
                readPos(tag, HOME_ANCHOR_TAG),
                Math.max(0L, tag.getLong(HIRED_AT_TAG)),
                Math.max(0L, tag.getLong(REVISION_TAG)),
                Math.max(1, tag.getInt(SCHEMA_VERSION_TAG)));
    }

    public static VillagerAssignmentSnapshot hire(
            Villager villager, UUID owner, HiredVillagerRole role, long hiredAt, BlockPos homeAnchor) {
        if (villager == null || owner == null || role == null) return snapshot(villager);
        VillagerAssignmentSnapshot before = snapshot(villager);
        if (before.state() == VillagerAssignmentState.HIRED && !before.ownedBy(owner)) return before;
        CompoundTag tag = new CompoundTag();
        tag.putString(STATE_TAG, VillagerAssignmentState.HIRED.name());
        tag.putUUID(OWNER_TAG, owner);
        tag.putString(COMMAND_TAG, defaultCommand(role).name());
        tag.putString(ROLE_TAG, role.serializedName());
        tag.putLong(HIRED_AT_TAG, Math.max(0L, hiredAt));
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
        if (command == null || !before.ownedBy(owner)) return before;
        CompoundTag tag = villager.getPersistentData().getCompound(ASSIGNMENT_TAG);
        tag.putString(COMMAND_TAG, command.name());
        if (command == VillagerAssignmentCommand.STAY) putPos(tag, WORK_ANCHOR_TAG, anchor);
        else if (command == VillagerAssignmentCommand.RETURN_HOME) tag.remove(WORK_ANCHOR_TAG);
        tag.putLong(REVISION_TAG, before.revision() + 1L);
        tag.putInt(SCHEMA_VERSION_TAG, VillagerAssignmentSnapshot.CURRENT_SCHEMA_VERSION);
        villager.setPersistenceRequired();
        return snapshot(villager);
    }

    public static VillagerAssignmentSnapshot setRole(Villager villager, HiredVillagerRole role) {
        VillagerAssignmentSnapshot before = snapshot(villager);
        if (before.state() != VillagerAssignmentState.HIRED || role == null) return before;
        CompoundTag tag = villager.getPersistentData().getCompound(ASSIGNMENT_TAG);
        tag.putString(ROLE_TAG, role.serializedName());
        tag.putString(COMMAND_TAG, defaultCommand(role).name());
        tag.putLong(REVISION_TAG, before.revision() + 1L);
        tag.putInt(SCHEMA_VERSION_TAG, VillagerAssignmentSnapshot.CURRENT_SCHEMA_VERSION);
        villager.setPersistenceRequired();
        return snapshot(villager);
    }

    public static VillagerAssignmentSnapshot unassign(Villager villager) {
        VillagerAssignmentSnapshot before = snapshot(villager);
        if (villager == null || before.state() == VillagerAssignmentState.UNASSIGNED) return before;
        CompoundTag tag = new CompoundTag();
        tag.putString(STATE_TAG, VillagerAssignmentState.UNASSIGNED.name());
        tag.putString(COMMAND_TAG, VillagerAssignmentCommand.RETURN_HOME.name());
        tag.putLong(REVISION_TAG, before.revision() + 1L);
        tag.putInt(SCHEMA_VERSION_TAG, VillagerAssignmentSnapshot.CURRENT_SCHEMA_VERSION);
        villager.getPersistentData().put(ASSIGNMENT_TAG, tag);
        clearRecruitmentCommand(villager);
        villager.setPersistenceRequired();
        return snapshot(villager);
    }

    public static Optional<UUID> commandOwner(Villager villager) {
        VillagerAssignmentSnapshot assignment = snapshot(villager);
        if (assignment.state() == VillagerAssignmentState.HIRED
                && (assignment.command() == VillagerAssignmentCommand.FOLLOW
                || assignment.command() == VillagerAssignmentCommand.STAY)) {
            return assignment.owner();
        }
        return villager != null && villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)
                ? Optional.of(villager.getPersistentData().getUUID(FOLLOWING_PLAYER_KEY))
                : Optional.empty();
    }

    public static boolean isFollowing(Villager villager) {
        return commandOwner(villager).isPresent() && command(villager) == VillagerAssignmentCommand.FOLLOW;
    }

    public static boolean isStaying(Villager villager) {
        return commandOwner(villager).isPresent() && command(villager) == VillagerAssignmentCommand.STAY;
    }

    public static VillagerAssignmentCommand command(Villager villager) {
        VillagerAssignmentSnapshot assignment = snapshot(villager);
        if (assignment.state() == VillagerAssignmentState.HIRED) return assignment.command();
        if (villager == null || !villager.getPersistentData().hasUUID(FOLLOWING_PLAYER_KEY)) {
            return VillagerAssignmentCommand.RETURN_HOME;
        }
        return FOLLOW_MODE_STAY.equals(villager.getPersistentData().getString(FOLLOW_MODE_KEY))
                ? VillagerAssignmentCommand.STAY
                : VillagerAssignmentCommand.FOLLOW;
    }

    public static void beginFollowing(
            Villager villager, UUID owner, BlockPos start, String startBiome, boolean startedInOcean) {
        if (villager == null || owner == null || start == null) return;
        if (snapshot(villager).ownedBy(owner)) setCommand(villager, owner, VillagerAssignmentCommand.FOLLOW, null);
        CompoundTag data = villager.getPersistentData();
        data.putUUID(FOLLOWING_PLAYER_KEY, owner);
        data.putString(FOLLOW_MODE_KEY, FOLLOW_MODE_FOLLOW);
        data.putFloat(FOLLOW_START_HEALTH_KEY, villager.getHealth());
        data.putFloat(FOLLOW_MIN_HEALTH_KEY, villager.getHealth());
        data.putInt(FOLLOW_START_X_KEY, start.getX());
        data.putInt(FOLLOW_START_Y_KEY, start.getY());
        data.putInt(FOLLOW_START_Z_KEY, start.getZ());
        data.putString(FOLLOW_START_BIOME_KEY, startBiome == null ? "the wilds" : startBiome);
        data.putInt(FOLLOW_MAX_DISTANCE_KEY, 0);
        data.putBoolean(FOLLOW_USED_BOAT_KEY, false);
        data.putBoolean(FOLLOW_CROSSED_OCEAN_KEY, startedInOcean);
        removeStayAnchor(data);
        villager.setPersistenceRequired();
    }

    public static void beginStaying(Villager villager, UUID owner, BlockPos anchor) {
        if (villager == null || owner == null || anchor == null) return;
        if (snapshot(villager).ownedBy(owner)) setCommand(villager, owner, VillagerAssignmentCommand.STAY, anchor);
        CompoundTag data = villager.getPersistentData();
        data.putUUID(FOLLOWING_PLAYER_KEY, owner);
        data.putString(FOLLOW_MODE_KEY, FOLLOW_MODE_STAY);
        data.putInt(STAY_ANCHOR_X_KEY, anchor.getX());
        data.putInt(STAY_ANCHOR_Y_KEY, anchor.getY());
        data.putInt(STAY_ANCHOR_Z_KEY, anchor.getZ());
        villager.setPersistenceRequired();
    }

    public static void clearRecruitmentCommand(Villager villager) {
        if (villager == null) return;
        CompoundTag data = villager.getPersistentData();
        data.remove(FOLLOWING_PLAYER_KEY);
        data.remove(FOLLOW_MODE_KEY);
        data.remove(FOLLOW_START_HEALTH_KEY);
        data.remove(FOLLOW_MIN_HEALTH_KEY);
        data.remove(FOLLOW_START_X_KEY);
        data.remove(FOLLOW_START_Y_KEY);
        data.remove(FOLLOW_START_Z_KEY);
        data.remove(FOLLOW_START_BIOME_KEY);
        data.remove(FOLLOW_MAX_DISTANCE_KEY);
        data.remove(FOLLOW_USED_BOAT_KEY);
        data.remove(FOLLOW_CROSSED_OCEAN_KEY);
        removeStayAnchor(data);
        VillagerAssignmentSnapshot assignment = snapshot(villager);
        assignment.owner().ifPresent(owner -> setCommand(villager, owner, defaultCommand(assignment.role()), null));
        villager.setPersistenceRequired();
    }

    public static BlockPos stayAnchor(Villager villager) {
        VillagerAssignmentSnapshot assignment = snapshot(villager);
        if (assignment.state() == VillagerAssignmentState.HIRED) return assignment.workAnchor();
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(STAY_ANCHOR_X_KEY) || !data.contains(STAY_ANCHOR_Y_KEY) || !data.contains(STAY_ANCHOR_Z_KEY)) {
            return null;
        }
        return new BlockPos(data.getInt(STAY_ANCHOR_X_KEY), data.getInt(STAY_ANCHOR_Y_KEY), data.getInt(STAY_ANCHOR_Z_KEY));
    }

    public static void rememberDamage(Villager villager) {
        if (commandOwner(villager).isEmpty()) return;
        CompoundTag data = villager.getPersistentData();
        float currentMin = data.contains(FOLLOW_MIN_HEALTH_KEY) ? data.getFloat(FOLLOW_MIN_HEALTH_KEY) : villager.getHealth();
        data.putFloat(FOLLOW_MIN_HEALTH_KEY, Math.min(currentMin, villager.getHealth()));
    }

    public static boolean wasInjured(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(FOLLOW_START_HEALTH_KEY)) return false;
        float min = data.contains(FOLLOW_MIN_HEALTH_KEY) ? data.getFloat(FOLLOW_MIN_HEALTH_KEY) : villager.getHealth();
        return Math.min(min, villager.getHealth()) + 0.5F < data.getFloat(FOLLOW_START_HEALTH_KEY);
    }

    public static JourneySnapshot journey(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        return new JourneySnapshot(
                data.getString(FOLLOW_START_BIOME_KEY),
                distanceFromStart(villager),
                data.getBoolean(FOLLOW_USED_BOAT_KEY),
                data.getBoolean(FOLLOW_CROSSED_OCEAN_KEY));
    }

    public static void updateJourney(Villager villager, boolean ridingBoat, boolean inOcean) {
        CompoundTag data = villager.getPersistentData();
        int distance = distanceFromStart(villager);
        if (distance > data.getInt(FOLLOW_MAX_DISTANCE_KEY)) data.putInt(FOLLOW_MAX_DISTANCE_KEY, distance);
        if (ridingBoat) data.putBoolean(FOLLOW_USED_BOAT_KEY, true);
        if (inOcean) data.putBoolean(FOLLOW_CROSSED_OCEAN_KEY, true);
    }

    public static void clearInheritedStateForNewborn(Villager child) {
        if (child == null) return;
        child.getPersistentData().remove(ASSIGNMENT_TAG);
        clearRecruitmentCommand(child);
    }

    private static int distanceFromStart(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        int currentMax = data.getInt(FOLLOW_MAX_DISTANCE_KEY);
        if (!data.contains(FOLLOW_START_X_KEY) || !data.contains(FOLLOW_START_Y_KEY) || !data.contains(FOLLOW_START_Z_KEY)) {
            return currentMax;
        }
        BlockPos start = new BlockPos(data.getInt(FOLLOW_START_X_KEY), data.getInt(FOLLOW_START_Y_KEY), data.getInt(FOLLOW_START_Z_KEY));
        return Math.max(currentMax, (int) Math.round(Math.sqrt(villager.blockPosition().distSqr(start))));
    }

    private static VillagerAssignmentCommand defaultCommand(HiredVillagerRole role) {
        return role == HiredVillagerRole.COMBAT ? VillagerAssignmentCommand.GUARD : VillagerAssignmentCommand.WORK;
    }

    private static void removeStayAnchor(CompoundTag data) {
        data.remove(STAY_ANCHOR_X_KEY);
        data.remove(STAY_ANCHOR_Y_KEY);
        data.remove(STAY_ANCHOR_Z_KEY);
    }

    private static void migrateAndValidate(Villager villager, CompoundTag tag) {
        int schemaVersion = tag.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)
                ? Math.max(0, tag.getInt(SCHEMA_VERSION_TAG))
                : 0;
        VillagerAssignmentState state = readEnum(
                VillagerAssignmentState.class, tag.getString(STATE_TAG), VillagerAssignmentState.UNASSIGNED);

        if (schemaVersion < 1 && state == VillagerAssignmentState.HIRED && tag.hasUUID(OWNER_TAG)) {
            CompoundTag legacy = villager.getPersistentData();
            UUID owner = tag.getUUID(OWNER_TAG);
            if (legacy.hasUUID(FOLLOWING_PLAYER_KEY) && legacy.getUUID(FOLLOWING_PLAYER_KEY).equals(owner)) {
                if (FOLLOW_MODE_STAY.equals(legacy.getString(FOLLOW_MODE_KEY))) {
                    tag.putString(COMMAND_TAG, VillagerAssignmentCommand.STAY.name());
                    if (legacy.contains(STAY_ANCHOR_X_KEY)
                            && legacy.contains(STAY_ANCHOR_Y_KEY)
                            && legacy.contains(STAY_ANCHOR_Z_KEY)) {
                        putPos(tag, WORK_ANCHOR_TAG, new BlockPos(
                                legacy.getInt(STAY_ANCHOR_X_KEY),
                                legacy.getInt(STAY_ANCHOR_Y_KEY),
                                legacy.getInt(STAY_ANCHOR_Z_KEY)));
                    }
                } else {
                    tag.putString(COMMAND_TAG, VillagerAssignmentCommand.FOLLOW.name());
                }
            }
        }

        if (state != VillagerAssignmentState.HIRED || !tag.hasUUID(OWNER_TAG)) {
            tag.putString(STATE_TAG, VillagerAssignmentState.UNASSIGNED.name());
            tag.putString(COMMAND_TAG, VillagerAssignmentCommand.RETURN_HOME.name());
            tag.remove(OWNER_TAG);
            tag.remove(ROLE_TAG);
            tag.remove(WORK_ANCHOR_TAG);
        } else {
            tag.putString(STATE_TAG, VillagerAssignmentState.HIRED.name());
            VillagerAssignmentCommand command = readEnum(
                    VillagerAssignmentCommand.class, tag.getString(COMMAND_TAG), VillagerAssignmentCommand.WORK);
            HiredVillagerRole role = HiredVillagerRole.bySerializedName(tag.getString(ROLE_TAG));
            if (command == VillagerAssignmentCommand.GUARD && role != HiredVillagerRole.COMBAT) {
                command = VillagerAssignmentCommand.WORK;
            }
            if (command == VillagerAssignmentCommand.STAY && readPos(tag, WORK_ANCHOR_TAG) == null) {
                command = VillagerAssignmentCommand.WORK;
            }
            tag.putString(COMMAND_TAG, command.name());
        }
        tag.putLong(HIRED_AT_TAG, Math.max(0L, tag.getLong(HIRED_AT_TAG)));
        tag.putLong(REVISION_TAG, Math.max(0L, tag.getLong(REVISION_TAG)));
        if (schemaVersion <= VillagerAssignmentSnapshot.CURRENT_SCHEMA_VERSION) {
            tag.putInt(SCHEMA_VERSION_TAG, VillagerAssignmentSnapshot.CURRENT_SCHEMA_VERSION);
        }
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

    public record JourneySnapshot(String startBiome, int distanceBlocks, boolean usedBoat, boolean crossedOcean) {
    }
}
