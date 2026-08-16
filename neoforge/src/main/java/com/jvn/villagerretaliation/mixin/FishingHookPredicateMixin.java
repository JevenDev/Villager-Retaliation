package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.entity.VillagerFishingHook;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.FishingHookPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHookPredicate.class)
public abstract class FishingHookPredicateMixin {
    @Shadow
    public abstract Optional<Boolean> inOpenWater();

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$matchVillagerFishingHook(
            Entity entity,
            ServerLevel level,
            @Nullable Vec3 position,
            CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof VillagerFishingHook hook) {
            Optional<Boolean> requiredOpenWater = this.inOpenWater();
            callback.setReturnValue(requiredOpenWater.isEmpty()
                    || requiredOpenWater.get() == hook.isOpenWaterFishing());
        }
    }
}
