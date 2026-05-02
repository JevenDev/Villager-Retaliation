package com.jvn.villagerretaliation.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerFleeBehaviorHandler {
    private VillagerFleeBehaviorHandler() {
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager)
                || villager.level().isClientSide
                || !VillagerRetaliationVillagerRules.shouldSuppressFleeingBehavior(villager)) {
            return;
        }

        clearImmediateFleeMemories(villager);
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)
                || villager.level().isClientSide
                || !(villager.level() instanceof ServerLevel level)
                || !VillagerRetaliationVillagerRules.shouldSuppressFleeingBehavior(villager)) {
            return;
        }

        Brain<Villager> brain = villager.getBrain();
        boolean shouldResetFleeState = brain.isActive(Activity.PANIC)
                || brain.isActive(Activity.HIDE)
                || shouldSuppressActiveRaidHide(level, villager, brain)
                || hasFleeMemory(brain);

        VillagerRetaliationVillagerBrainUtil.clearThreatMemories(villager);
        brain.eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
        brain.eraseMemory(MemoryModuleType.HIDING_PLACE);

        if (!shouldResetFleeState) {
            return;
        }

        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(scheduledActivity(level, brain));
    }

    private static void clearImmediateFleeMemories(Villager villager) {
        VillagerRetaliationVillagerBrainUtil.clearThreatMemories(villager);
    }

    private static boolean hasFleeMemory(Brain<Villager> brain) {
        return VillagerRetaliationVillagerBrainUtil.hasThreatMemories(brain)
                || brain.hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME)
                || brain.hasMemoryValue(MemoryModuleType.HIDING_PLACE);
    }

    private static boolean shouldSuppressActiveRaidHide(ServerLevel level, Villager villager, Brain<Villager> brain) {
        if (!brain.isActive(Activity.RAID)) {
            return false;
        }

        Raid raid = level.getRaidAt(villager.blockPosition());
        return raid != null && raid.isActive() && !raid.isVictory() && !raid.isLoss();
    }

    private static Activity scheduledActivity(ServerLevel level, Brain<Villager> brain) {
        Activity activity = brain.getSchedule().getActivityAt((int) (level.getDayTime() % 24000L));
        return isFleeActivity(activity) ? Activity.IDLE : activity;
    }

    private static boolean isFleeActivity(Activity activity) {
        return activity == Activity.PANIC || activity == Activity.PRE_RAID || activity == Activity.RAID || activity == Activity.HIDE;
    }
}
