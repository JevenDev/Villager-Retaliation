package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.inventory.SmithRepairMenu;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class SmithRepairAiMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$controlRepairMovement(CallbackInfo callback) {
        if (!((Object) this instanceof Villager villager)) return;
        SmithRepairMenu menu = SmithRepairMenu.controlling(villager);
        if (menu == null) return;
        villager.goalSelector.getAvailableGoals().forEach(WrappedGoal::stop);
        villager.targetSelector.getAvailableGoals().forEach(WrappedGoal::stop);
        menu.tickVillager();
        // keep locomotion running while repair owns all movement decisions
        villager.getNavigation().tick();
        villager.getMoveControl().tick();
        villager.getLookControl().tick();
        villager.getJumpControl().tick();
        callback.cancel();
    }
}
