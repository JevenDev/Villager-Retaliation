package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.combat.VillagerRangedCombatHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class VillagerRangedStateMixin {
    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    private void villagerretaliation$captureRangedUse(CompoundTag tag, CallbackInfoReturnable<CompoundTag> callback) {
        if ((Object) this instanceof AbstractVillager villager) VillagerRangedCombatHelper.captureUse(villager);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void villagerretaliation$restoreRangedUse(CompoundTag tag, CallbackInfo callback) {
        if ((Object) this instanceof AbstractVillager villager) VillagerRangedCombatHelper.restoreUse(villager);
    }
}
