package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerGossipMixin {
    @Inject(method = "gossip", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$suppressControlledVillagerGossip(
            ServerLevel level,
            Villager other,
            long gameTime,
            CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (VillagerBehaviorSuppressionPolicy.suppresses(
                        villager, VillagerBehaviorSuppressionPolicy.Behavior.GOSSIPING)
                || VillagerBehaviorSuppressionPolicy.suppresses(
                        other, VillagerBehaviorSuppressionPolicy.Behavior.GOSSIPING)) {
            ci.cancel();
        }
    }
}
