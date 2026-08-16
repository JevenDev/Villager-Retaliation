package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerTradingMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$suppressControlledVanillaTrade(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        Villager villager = (Villager) (Object) this;
        if (VillagerInteractionService.shouldDeferVillagerInteractionToHeldItem(villager, player, hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        } else if (VillagerInteractionService.shouldSuppressVanillaTradeFallback(villager, player, hand)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
