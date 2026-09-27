package com.jvn.villagerretaliation.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityCombatAccessor {
    @Invoker("detectEquipmentUpdates")
    void villagerretaliation$detectEquipmentUpdates();

    @Accessor("effectsDirty")
    void villagerretaliation$setEffectsDirty(boolean dirty);

    @Accessor("lastHurt")
    float villagerretaliation$getLastHurt();

    @Accessor("lastHurt")
    void villagerretaliation$setLastHurt(float damage);
}
