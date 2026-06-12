package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityNameMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$usePresetVillagerName(CallbackInfoReturnable<Component> cir) {
        if (!((Object) this instanceof AbstractVillager villager) || villager.hasCustomName()) {
            return;
        }

        String presetName = VillagerPresetNameRegistry.resolvePresetName(villager);
        if (!presetName.isBlank()) {
            cir.setReturnValue(Component.literal(presetName));
        }
    }
}
