package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.mount.VillagerMountTravelService;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class PartyCommandService {
    private PartyCommandService() {
    }

    static void follow(ServerLevel level, PartyVillagerRecord record) {
        Villager villager = PartyQuickCommandService.cancelMovement(level.getServer(), record);
        record.setFollowing();
        CompanionLifecycleService.resume(level, record.villagerId(), true);
        if (villager != null && villager.level() instanceof ServerLevel villagerLevel) {
            VillagerRecruitmentService.applyPartyFollowing(villagerLevel, villager, record.commanderId());
        }
        PartyService.markChanged(level);
    }

    static void stay(ServerLevel level, Villager villager, PartyVillagerRecord record, BlockPos target) {
        PartyQuickCommandService.cancelMovement(level.getServer(), record);
        record.setStaying(level.dimension().location(), target);
        CompanionLifecycleService.resume(level, record.villagerId(), false);
        VillagerRecruitmentService.applyPartyStay(level, villager, record.commanderId(), target);
        VillagerMountTravelService.stopAssignedMountNavigation(villager);
        PartyService.markChanged(level);
    }

    public static void reassignCommander(ServerLevel level, PartyVillagerRecord record, UUID commanderId) {
        if (record.commanderId().equals(commanderId)) return;
        record.assignCommander(commanderId);
        PartyQuickCommandService.resetForOwnershipTransfer(level.getServer(), record);
        Villager villager = VillagerEntityResolver.loaded(level.getServer(), record.villagerId());
        if (villager != null && villager.level() instanceof ServerLevel villagerLevel) {
            PartyVillagerContractService.refreshTransferredOwnership(villagerLevel, villager);
        }
        PartyService.markChanged(level);
    }
}
