package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerTrafficService;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PathNavigation.class)
public abstract class PathNavigationTrafficMixin {
    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    private net.minecraft.core.BlockPos targetPos;

    @Shadow
    protected boolean hasDelayedRecomputation;

    @Inject(method = "recomputePath", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$deferAmbientReplanning(CallbackInfo callback) {
        if (this.mob instanceof Villager villager
                && com.jvn.villagerretaliation.villagelife.VillageLifeService.cancelRecompute(villager)) {
            this.targetPos = null;
            this.hasDelayedRecomputation = false;
            callback.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void villagerretaliation$coordinateVillagerTraffic(CallbackInfo callback) {
        if (this.mob instanceof Villager villager) {
            VillagerTrafficService.controlNavigation(villager);
        }
    }
}
