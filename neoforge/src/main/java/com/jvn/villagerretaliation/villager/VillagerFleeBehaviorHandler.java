package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerFleeBehaviorHandler {
    private VillagerFleeBehaviorHandler() {
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager)
                || villager.level().isClientSide) {
            return;
        }

        Brain<Villager> brain = villager.getBrain();
        if (!VillagerRetaliationVillagerBrainUtil.hasVanillaFleeState(levelOrNull(villager), brain)
                || !VillagerRetaliationVillagerRules.shouldSuppressVanillaFleeBehavior(villager)) {
            return;
        }

        VillagerRetaliationVillagerBrainUtil.clearFleeMemories(villager);
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)
                || villager.level().isClientSide
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        Brain<Villager> brain = villager.getBrain();
        if (!VillagerRetaliationVillagerBrainUtil.hasVanillaFleeState(level, brain)) {
            return;
        }

        boolean keepFleeingBehavior = VillagerRetaliationVillagerRules.shouldKeepFleeingBehavior(villager);
        if (keepFleeingBehavior) {
            maybeAnnounceFlee(level, villager, brain);
            return;
        }
        if (!VillagerRetaliationVillagerRules.shouldSuppressVanillaFleeBehavior(villager, keepFleeingBehavior)) {
            return;
        }

        VillagerRetaliationVillagerBrainUtil.suppressVanillaFleeState(level, villager);
    }

    private static boolean hasFleeMemory(Brain<Villager> brain) {
        return VillagerRetaliationVillagerBrainUtil.hasThreatMemories(brain)
                || brain.hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME)
                || brain.hasMemoryValue(MemoryModuleType.HIDING_PLACE);
    }

    private static ServerLevel levelOrNull(Villager villager) {
        return villager.level() instanceof ServerLevel level ? level : null;
    }

    private static void maybeAnnounceFlee(ServerLevel level, Villager villager, Brain<Villager> brain) {
        if (!brain.isActive(Activity.PANIC) && !hasFleeMemory(brain)) {
            return;
        }
        brain.getMemory(MemoryModuleType.NEAREST_HOSTILE)
                .filter(LivingEntity::isAlive)
                .ifPresent(hostile -> VillagerAmbientIndicatorService.onFleeStarted(level, villager, hostile));
    }
}
