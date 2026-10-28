package com.jvn.villagerretaliation.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("eyeHeight")
    float villagerretaliation$getEyeHeight();

    @Accessor("eyeHeight")
    void villagerretaliation$setEyeHeight(float height);

    @Accessor("eyeHeightOld")
    float villagerretaliation$getEyeHeightOld();

    @Accessor("eyeHeightOld")
    void villagerretaliation$setEyeHeightOld(float height);
}
