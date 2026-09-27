package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.settlement.SettlementViewClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class SettlementEntityRendererMixin {
    @Redirect(method = "shouldRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;shouldRender(DDD)Z"))
    private boolean villagerretaliation$renderViewedResident(Entity entity, double x, double y, double z) {
        return entity.shouldRender(x, y, z)
                || entity instanceof Villager && SettlementViewClient.contains(entity.level(), entity.blockPosition());
    }
}
