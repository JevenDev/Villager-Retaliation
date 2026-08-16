package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetRaidStatus;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SetRaidStatus.class)
public abstract class SetRaidStatusMixin {
    @Inject(method = "create()Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;", at = @At("HEAD"), cancellable = true)
    private static void villagerretaliation$suppressRaidFleeState(
            CallbackInfoReturnable<BehaviorControl<LivingEntity>> cir) {
        cir.setReturnValue(BehaviorBuilder.create(instance -> instance.point((level, entity, gameTime) -> {
            if (level.random.nextInt(20) != 0) {
                return false;
            }

            if (entity instanceof Villager villager
                    && VillagerRetaliationVillagerRules.shouldSuppressVanillaFleeBehavior(villager)) {
                VillagerRetaliationVillagerBrainUtil.suppressVanillaFleeState(level, villager);
                return false;
            }

            Brain<?> brain = entity.getBrain();
            Raid raid = level.getRaidAt(entity.blockPosition());
            if (raid != null) {
                if (raid.hasFirstWaveSpawned() && !raid.isBetweenWaves()) {
                    brain.setDefaultActivity(Activity.RAID);
                    brain.setActiveActivityIfPossible(Activity.RAID);
                } else {
                    brain.setDefaultActivity(Activity.PRE_RAID);
                    brain.setActiveActivityIfPossible(Activity.PRE_RAID);
                }
            }

            return true;
        })));
    }
}
