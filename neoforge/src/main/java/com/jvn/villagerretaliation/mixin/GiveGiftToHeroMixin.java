package com.jvn.villagerretaliation.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GiveGiftToHero.class)
public abstract class GiveGiftToHeroMixin {
    @Inject(method = "throwGift", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$suppressHeroGiftThrow(
            Villager villager,
            LivingEntity target,
            CallbackInfo ci) {
        ci.cancel();
    }
}
