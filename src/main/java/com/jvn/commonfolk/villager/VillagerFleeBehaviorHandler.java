package com.jvn.commonfolk.villager;

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
                || !CommonfolkVillagerRules.shouldSuppressFleeingBehavior(villager)) {
            return;
        }

        clearImmediateFleeMemories(villager);
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)
                || villager.level().isClientSide
                || !(villager.level() instanceof ServerLevel level)
                || !CommonfolkVillagerRules.shouldSuppressFleeingBehavior(villager)) {
            return;
        }

        Brain<Villager> brain = villager.getBrain();
        boolean shouldResetFleeState = brain.isActive(Activity.PANIC)
                || brain.isActive(Activity.HIDE)
                || shouldSuppressActiveRaidHide(level, villager, brain)
                || hasFleeMemory(brain);

        clearImmediateFleeMemories(villager);
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
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.HURT_BY);
        brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
    }

    private static boolean hasFleeMemory(Brain<Villager> brain) {
        return brain.hasMemoryValue(MemoryModuleType.HURT_BY)
                || brain.hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)
                || brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)
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
