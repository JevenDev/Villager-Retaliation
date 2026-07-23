package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.duel.DuelService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityDuelDropMixin {
    @Inject(
            method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true)
    private void villagerretaliation$blockDuelVillagerDrop(
            ItemStack stack,
            float offsetY,
            CallbackInfoReturnable<ItemEntity> callbackInfo) {
        if ((Object) this instanceof Villager villager && DuelService.isParticipant(villager)) {
            callbackInfo.setReturnValue(null);
        }
    }
}
