package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.item.BannerHelmetData;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CustomHeadLayer.class)
public abstract class BannerHelmetCustomHeadLayerMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack villagerretaliation$renderAttachedBanner(LivingEntity entity, EquipmentSlot slot) {
        ItemStack equipped = entity.getItemBySlot(slot);
        if (slot != EquipmentSlot.HEAD) {
            return equipped;
        }
        return BannerHelmetData.getAttachedBanner(equipped, entity.registryAccess()).orElse(equipped);
    }
}
