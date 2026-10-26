package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementCourierRecovery;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class DiplomaticCargoHopperMixin {
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void villagerretaliation$waitForCargoCustody(Container container, ItemEntity item, CallbackInfoReturnable<Boolean> callback) {
        if (SettlementCourierRecovery.locked(item)) callback.setReturnValue(false);
    }
}
