package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.settlement.SettlementViewClient;
import net.minecraft.client.renderer.blockentity.BedRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class SettlementBlockEntityRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"))
    private <T extends BlockEntity> boolean villagerretaliation$renderViewedBed(BlockEntityRenderer<T> renderer, T block, Vec3 camera) {
        return renderer.shouldRender(block, camera)
                || renderer instanceof BedRenderer && SettlementViewClient.contains(block.getLevel(), block.getBlockPos());
    }
}
