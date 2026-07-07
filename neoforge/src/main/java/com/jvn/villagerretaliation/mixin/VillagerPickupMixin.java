package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.work.HiredFarmingInventoryBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerPickupMixin {
    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$routeHiredFarmerPickupPredicate(
            ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        Villager villager = (Villager) (Object) this;
        if (villager.level() instanceof ServerLevel level) {
            Boolean result = HiredFarmingInventoryBridge.wantsToPickUp(level, villager, stack);
            if (result != null) {
                cir.setReturnValue(result);
            }
        }
    }

    @Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$routeHiredFarmerPickup(
            ItemEntity itemEntity,
            CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (villager.level() instanceof ServerLevel level
                && HiredFarmingInventoryBridge.capturePickup(level, villager, itemEntity)) {
            ci.cancel();
        }
    }
}
