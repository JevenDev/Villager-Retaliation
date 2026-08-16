package com.jvn.villagerretaliation.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShowTradesToPlayer.class)
public abstract class ShowTradesToPlayerMixin {
    @Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$suppressTradePreviewStart(
            ServerLevel level,
            Villager villager,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)Z", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$suppressTradePreviewContinue(
            ServerLevel level,
            Villager villager,
            long gameTime,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "displayAsHeldItem(Lnet/minecraft/world/entity/npc/Villager;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private static void villagerretaliation$suppressTradePreviewHeldItem(
            Villager villager,
            ItemStack stack,
            CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "clearHeldItem(Lnet/minecraft/world/entity/npc/Villager;)V", at = @At("HEAD"), cancellable = true)
    private static void villagerretaliation$preserveNonPreviewHeldItem(
            Villager villager,
            CallbackInfo ci) {
        ci.cancel();
    }
}
