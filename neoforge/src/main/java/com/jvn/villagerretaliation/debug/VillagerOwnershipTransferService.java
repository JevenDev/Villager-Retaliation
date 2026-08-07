package com.jvn.villagerretaliation.debug;

import com.jvn.villagerretaliation.interaction.HireOverflowClaimService;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData;
import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartySyncService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerOwnershipTransferService {
    private VillagerOwnershipTransferService() {
    }

    public static TransferResult transfer(ServerLevel level, Villager villager, UUID newOwnerId) {
        if (level == null || villager == null || newOwnerId == null) {
            return TransferResult.failure("Invalid villager ownership transfer.");
        }

        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        UUID previousOwnerId;
        OwnershipType type;
        UUID sourcePartyId = null;
        UUID targetPartyId = null;
        if (party != null) {
            PartyService.OwnershipTransferResult partyTransfer =
                    PartyService.transferVillagerOwnership(level, villager.getUUID(), newOwnerId);
            if (!partyTransfer.success()) {
                return TransferResult.failure(partyTransfer.error());
            }
            previousOwnerId = partyTransfer.previousOwnerId();
            sourcePartyId = partyTransfer.sourcePartyId();
            targetPartyId = partyTransfer.targetPartyId();
            type = OwnershipType.PARTY;
            PartyVillagerContractService.refreshTransferredOwnership(level, villager);
        } else {
            previousOwnerId = HiredVillagerContractService.transferOwnership(level, villager, newOwnerId)
                    .orElse(null);
            if (previousOwnerId == null) {
                return TransferResult.failure("The villager has no active hired or party ownership.");
            }
            type = OwnershipType.HIRED;
        }

        int transferredStorages = AssignedStorageSavedData.get(level)
                .transferAssignmentOwnership(villager.getUUID(), newOwnerId);
        boolean transferredOverflowClaim = HireOverflowClaimService.transferOwner(level, villager, newOwnerId);
        if (sourcePartyId != null) {
            PartySyncService.syncParty(level.getServer(), sourcePartyId);
        }
        if (targetPartyId != null && !targetPartyId.equals(sourcePartyId)) {
            PartySyncService.syncParty(level.getServer(), targetPartyId);
        }
        return TransferResult.success(type, previousOwnerId, newOwnerId, transferredStorages,
                transferredOverflowClaim);
    }

    public enum OwnershipType {
        HIRED,
        PARTY
    }

    public record TransferResult(
            boolean success,
            String error,
            OwnershipType type,
            UUID previousOwnerId,
            UUID newOwnerId,
            int transferredStorages,
            boolean transferredOverflowClaim) {
        static TransferResult success(
                OwnershipType type,
                UUID previousOwnerId,
                UUID newOwnerId,
                int transferredStorages,
                boolean transferredOverflowClaim) {
            return new TransferResult(true, "", type, previousOwnerId, newOwnerId,
                    transferredStorages, transferredOverflowClaim);
        }

        static TransferResult failure(String error) {
            return new TransferResult(false, error, null, null, null, 0, false);
        }
    }
}
