package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LookControl.class)
public abstract class LookControlMixin {
    @Shadow
    @Final
    protected Mob mob;

    @Inject(method = "tick", at = @At("HEAD"))
    private void villagerretaliation$keepActiveWorkerFocusedOnTask(CallbackInfo callback) {
        if (!(this.mob instanceof Villager villager)) {
            return;
        }
        BlockPos target = HiredVillagerFocusService.activeWorkLookTarget(villager);
        if (target == null) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(target);
        ((LookControl) (Object) this).setLookAt(center.x, center.y, center.z, 60.0F, 60.0F);
    }
}
