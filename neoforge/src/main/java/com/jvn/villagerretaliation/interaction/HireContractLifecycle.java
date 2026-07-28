package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/** Shared lifecycle orchestration for regular and one-off contracts. */
public final class HireContractLifecycle {
    private static final int HIRED_PROFESSION_LOCK_XP = 1;

    private HireContractLifecycle() { }

    public static void begin(ServerLevel level, Villager villager, HireContract contract) {
        VillagerRecruitmentService.stopFollowing(villager);
        if (!contract.oneOffBuilderJob()) lockProfession(villager, contract);
        HireContractStore.save(villager, contract);
        HireContractAssignmentAdapter.synchronize(level, villager);
        HiredWorkSession.invalidate(villager);
        HiredVillagerContractService.takeOverJobInventoryOverflow(villager);
        HiredJobInventory.getJobInventory(villager).markRemovableItemsForContract(contract.id());
        HiredVillagerIndex.update(level, villager);
        com.jvn.villagerretaliation.network.VillagerReputationNetworking.syncNameToTracking(villager);
        com.jvn.villagerretaliation.social.VillagerBreedingPolicy.cancelActiveAttempt(level, villager);
        VillagerBehaviorSuppressionPolicy.enforce(level, villager);
    }

    public static void unlockProfession(Villager villager) {
        HireContractStore.load(villager).ifPresent(contract -> unlockProfession(villager, contract));
    }

    public static void unlockProfession(Villager villager, HireContract contract) {
        if (!contract.hasArtificialProfessionLock()) {
            contract.clearProfessionLock();
            return;
        }
        int appliedXp = contract.appliedProfessionXp() > 0
                ? contract.appliedProfessionXp()
                : HIRED_PROFESSION_LOCK_XP;
        if (villager.getVillagerXp() == appliedXp) villager.setVillagerXp(contract.originalProfessionXp());
        contract.clearProfessionLock();
        villager.setPersistenceRequired();
    }

    private static void lockProfession(Villager villager, HireContract contract) {
        contract.clearProfessionLock();
        if (villager.isBaby()) return;
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) return;
        int originalXp = villager.getVillagerXp();
        if (originalXp > 0) return;
        villager.setVillagerXp(HIRED_PROFESSION_LOCK_XP);
        contract.artificialProfessionLock(originalXp, HIRED_PROFESSION_LOCK_XP);
    }
}
