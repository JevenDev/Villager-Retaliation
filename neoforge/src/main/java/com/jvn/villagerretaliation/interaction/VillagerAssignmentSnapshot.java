package com.jvn.villagerretaliation.interaction;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Immutable, network-safe view of the villager's canonical assignment lifecycle. */
public record VillagerAssignmentSnapshot(
        VillagerAssignmentState state,
        Optional<UUID> owner,
        VillagerAssignmentCommand command,
        HiredVillagerRole role,
        BlockPos workAnchor,
        BlockPos homeAnchor,
        long hiredAt,
        long revision,
        int schemaVersion) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static VillagerAssignmentSnapshot unassigned(long revision) {
        return new VillagerAssignmentSnapshot(
                VillagerAssignmentState.UNASSIGNED,
                Optional.empty(),
                VillagerAssignmentCommand.RETURN_HOME,
                null,
                null,
                null,
                0L,
                revision,
                CURRENT_SCHEMA_VERSION);
    }

    public boolean ownedBy(UUID playerId) {
        return state == VillagerAssignmentState.HIRED && owner.filter(playerId::equals).isPresent();
    }
}
