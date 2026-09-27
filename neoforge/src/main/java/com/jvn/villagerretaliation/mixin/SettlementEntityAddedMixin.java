package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementGroundExperienceService;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PersistentEntitySectionManager.class)
public abstract class SettlementEntityAddedMixin {
    @Inject(method = "addEntityWithoutEvent", at = @At("RETURN"), remap = false)
    private void villagerretaliation$observeAcceptedExperience(EntityAccess entity, boolean loaded, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && entity instanceof ExperienceOrb orb) SettlementGroundExperienceService.onChanged(orb);
    }
}
