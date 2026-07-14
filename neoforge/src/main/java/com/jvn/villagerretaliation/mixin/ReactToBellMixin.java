package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.ReactToBell;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReactToBell.class)
public abstract class ReactToBellMixin {
    @Inject(method = "create()Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;", at = @At("HEAD"), cancellable = true)
    private static void villagerretaliation$suppressBellHide(
            CallbackInfoReturnable<BehaviorControl<LivingEntity>> cir) {
        cir.setReturnValue(BehaviorBuilder.create(instance -> instance.group(instance.present(MemoryModuleType.HEARD_BELL_TIME))
                .apply(instance, heardBell -> (level, entity, gameTime) -> {
                    Raid raid = level.getRaidAt(entity.blockPosition());
                    if (raid != null) {
                        return true;
                    }
                    if (entity instanceof Villager villager
                            && !com.jvn.villagerretaliation.raid.PlayerRaidService.shouldForceHide(villager)
                            && VillagerRetaliationVillagerRules.shouldSuppressVanillaFleeBehavior(villager)) {
                        heardBell.erase();
                        VillagerRetaliationVillagerBrainUtil.suppressVanillaFleeState(level, villager);
                        return true;
                    }

                    entity.getBrain().setActiveActivityIfPossible(Activity.HIDE);
                    return true;
                })));
    }
}
