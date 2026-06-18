package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetHiddenState;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import org.apache.commons.lang3.mutable.MutableInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SetHiddenState.class)
public abstract class SetHiddenStateMixin {
    @Inject(method = "create(II)Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;", at = @At("HEAD"), cancellable = true)
    private static void villagerretaliation$suppressHiddenState(
            int stayHiddenSeconds,
            int closeEnoughDist,
            CallbackInfoReturnable<BehaviorControl<LivingEntity>> cir) {
        int stayHiddenTicks = stayHiddenSeconds * 20;
        MutableInt hiddenTicks = new MutableInt(0);
        cir.setReturnValue(BehaviorBuilder.create(instance -> instance.group(
                        instance.present(MemoryModuleType.HIDING_PLACE),
                        instance.present(MemoryModuleType.HEARD_BELL_TIME))
                .apply(instance, (hidingPlace, heardBell) -> (level, entity, gameTime) -> {
                    if (entity instanceof Villager villager
                            && VillagerRetaliationVillagerRules.shouldSuppressVanillaFleeBehavior(villager)) {
                        hiddenTicks.setValue(0);
                        heardBell.erase();
                        hidingPlace.erase();
                        VillagerRetaliationVillagerBrainUtil.suppressVanillaFleeState(level, villager);
                        return true;
                    }

                    long heardBellTime = instance.<Long>get(heardBell);
                    boolean timedOut = heardBellTime + 300L <= gameTime;
                    if (hiddenTicks.getValue() <= stayHiddenTicks && !timedOut) {
                        BlockPos hidingPos = instance.get(hidingPlace).pos();
                        if (hidingPos.closerThan(entity.blockPosition(), (double) closeEnoughDist)) {
                            hiddenTicks.increment();
                        }
                        return true;
                    }

                    heardBell.erase();
                    hidingPlace.erase();
                    entity.getBrain().updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
                    hiddenTicks.setValue(0);
                    return true;
                })));
    }
}
