package com.jvn.villagerretaliation.mixin.client;

import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PanoramaRenderer.class)
public interface PanoramaRendererAccessor {
    @Accessor("spin")
    float villagerretaliation$getSpin();

    @Accessor("spin")
    void villagerretaliation$setSpin(float spin);
}
