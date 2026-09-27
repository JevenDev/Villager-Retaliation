package com.jvn.villagerretaliation.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Projectile.class)
public interface ProjectileCombatAccessor {
    @Accessor("hasBeenShot")
    boolean villagerretaliation$hasBeenShot();

    @Accessor("hasBeenShot")
    void villagerretaliation$setHasBeenShot(boolean value);

    @Invoker("canHitEntity")
    boolean villagerretaliation$canHitEntity(Entity entity);

    @Invoker("hitTargetOrDeflectSelf")
    ProjectileDeflection villagerretaliation$hit(HitResult hit);

    @Invoker("updateRotation")
    void villagerretaliation$updateRotation();

    @Accessor("leftOwner")
    boolean villagerretaliation$leftOwner();

    @Accessor("leftOwner")
    void villagerretaliation$setLeftOwner(boolean value);
}
