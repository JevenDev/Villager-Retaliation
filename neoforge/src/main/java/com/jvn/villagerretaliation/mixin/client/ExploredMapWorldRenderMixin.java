package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.exploration.ExploredMapScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GameRenderer.class)
public abstract class ExploredMapWorldRenderMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private boolean villagerretaliation$skipCoveredWorld(boolean renderLevel, DeltaTracker delta, boolean original) {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ExploredMapScreen map) || !map.coversWorld() || mc.level == null || mc.player == null) return renderLevel;
        if (mc.getCameraEntity() == null) mc.setCameraEntity(mc.player);
        var entity = mc.getCameraEntity();
        float partial = mc.level.tickRateManager().isEntityFrozen(entity) ? 1 : delta.getGameTimeDeltaPartialTick(true);
        // sound positioning still follows the player while the opaque map covers the world
        mc.gameRenderer.getMainCamera().setup(mc.level, entity, !mc.options.getCameraType().isFirstPerson(), mc.options.getCameraType().isMirrored(), partial);
        return false;
    }
}
