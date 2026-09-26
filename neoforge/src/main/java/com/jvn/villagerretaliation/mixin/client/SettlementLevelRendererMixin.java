package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.settlement.SettlementViewClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public abstract class SettlementLevelRendererMixin {
    @ModifyVariable(method = "setupRender", at = @At("STORE"), ordinal = 0)
    private double villagerretaliation$viewX(double coordinate, Camera camera, Frustum frustum, boolean captured, boolean spectator) {
        return SettlementViewClient.ownsCamera(camera) ? camera.getPosition().x : coordinate;
    }

    @ModifyVariable(method = "setupRender", at = @At("STORE"), ordinal = 1)
    private double villagerretaliation$viewY(double coordinate, Camera camera, Frustum frustum, boolean captured, boolean spectator) {
        return SettlementViewClient.ownsCamera(camera) ? camera.getPosition().y : coordinate;
    }

    @ModifyVariable(method = "setupRender", at = @At("STORE"), ordinal = 2)
    private double villagerretaliation$viewZ(double coordinate, Camera camera, Frustum frustum, boolean captured, boolean spectator) {
        return SettlementViewClient.ownsCamera(camera) ? camera.getPosition().z : coordinate;
    }
}
