package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.item.VillagerRecipeSemantics;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class WorkerRecipeReloadMixin {
    @Inject(method = {"apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", "replaceRecipes"}, at = @At("RETURN"))
    private void villagerretaliation$recipesApplied(CallbackInfo callback) {
        VillagerRecipeSemantics.markReloaded();
    }
}
