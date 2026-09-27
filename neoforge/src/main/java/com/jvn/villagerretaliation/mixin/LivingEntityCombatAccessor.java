package com.jvn.villagerretaliation.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityCombatAccessor {
    @Accessor("useItem")
    void villagerretaliation$setUseItem(net.minecraft.world.item.ItemStack item);

    @Accessor("useItemRemaining")
    void villagerretaliation$setUseItemRemaining(int ticks);

    @Invoker("setLivingEntityFlag")
    void villagerretaliation$setLivingEntityFlag(int flag, boolean value);

    @Invoker("detectEquipmentUpdates")
    void villagerretaliation$detectEquipmentUpdates();

    @Accessor("effectsDirty")
    void villagerretaliation$setEffectsDirty(boolean dirty);

    @Accessor("lastHurt")
    float villagerretaliation$getLastHurt();

    @Accessor("lastHurt")
    void villagerretaliation$setLastHurt(float damage);
}
