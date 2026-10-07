package com.jvn.villagerretaliation.villagelife;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.duel.DuelService;
import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import com.jvn.villagerretaliation.social.BreedingBlockReason;
import com.jvn.villagerretaliation.study.VillagerStudyService;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

public final class VillageLifeAvailability {
    private VillageLifeAvailability() {
    }

    public static BreedingBlockReason blocker(ServerLevel level, Villager villager) {
        if (!villager.isAlive() || villager.isRemoved()) return BreedingBlockReason.DEAD;
        if (VillagerDownedService.isDowned(villager)) return BreedingBlockReason.DOWNED;
        if (PartyVillagerContractService.isActivePartyVillager(level, villager)) return BreedingBlockReason.PARTY_MEMBER;
        if (VillagerRecruitmentService.isActivelyFollowingAnyPlayer(villager)) return BreedingBlockReason.FOLLOWING_PLAYER;
        if (VillagerRecruitmentService.isOrderedToStay(villager)) return BreedingBlockReason.ORDERED_TO_STAY;
        if (danger(level, villager)) return BreedingBlockReason.IN_COMBAT;
        if (villager.isTrading() || VillagerConversationService.isConversing(villager)
                || VillagerStudyService.isActivelyStudying(level, villager) || DuelService.isParticipant(villager)) {
            return BreedingBlockReason.CONTROLLED_ACTIVITY;
        }
        if (SceneSavedData.get(level).hasActiveActor(villager.getUUID())) return BreedingBlockReason.SCENE_CONTROLLED;
        if (HiredVillagerWorkService.isActivelyWorking(villager)
                || SettlementWorkforceService.onDuty(level, villager)
                || HiredVillagerFocusService.isOnDutyGuard(level, villager)
                || HiredVillagerFocusService.shouldSuppressVanillaBrainTick(level, villager)) return BreedingBlockReason.WORKING;
        if (VillagerWorkAuthority.hasWork(villager) && !settled(level, villager)) return BreedingBlockReason.HIRED;
        return leisure(level, villager) ? BreedingBlockReason.NONE : BreedingBlockReason.OFF_HOURS;
    }

    public static boolean danger(ServerLevel level, Villager villager) {
        return villager.isOnFire() || villager.isInLava() || villager.getLastHurtByMob() != null
                || VillagerRetaliationHandler.hasActiveRetaliationTarget(villager)
                || VillagerRetaliationVillagerCombatUtil.isThreatened(villager)
                || VillagerRetaliationVillagerBrainUtil.hasVanillaFleeState(level, villager.getBrain())
                || activeRaid(level, villager);
    }

    private static boolean activeRaid(ServerLevel level, Villager villager) {
        var raid = level.getRaidAt(villager.blockPosition());
        return raid != null && !raid.isOver();
    }

    public static boolean leisure(ServerLevel level, Villager villager) {
        if (villager.isSleeping()) return false;
        Activity scheduled = villager.getBrain().getSchedule().getActivityAt((int) Math.floorMod(level.getDayTime(), 24_000L));
        return scheduled == Activity.IDLE || scheduled == Activity.MEET || scheduled == Activity.PLAY;
    }

    public static boolean settled(ServerLevel level, Villager villager) {
        return SettlementWorkforceService.home(level, villager).isPresent()
                || villager.getBrain().getMemory(MemoryModuleType.HOME)
                        .filter(home -> home.dimension().equals(level.dimension())).isPresent();
    }
}
