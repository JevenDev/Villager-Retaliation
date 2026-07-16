package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.mount.VillagerMountedCombatPolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileFriendlyFireMixin {
    @Inject(method = "canHitEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"), cancellable = true)
    private void villagerretaliation$allowHostileFrontRider(
            Entity target,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()
                && target.canBeHitByProjectile()
                && VillagerMountedCombatPolicy.allowsSameVehicleProjectileHit(
                        (Projectile) (Object) this,
                        target)) {
            cir.setReturnValue(true);
        }
    }
}
