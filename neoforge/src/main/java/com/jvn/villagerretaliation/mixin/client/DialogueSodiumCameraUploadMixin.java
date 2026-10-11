package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager", remap = false)
public abstract class DialogueSodiumCameraUploadMixin {
    @Inject(method = "uploadResults(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Ljava/util/Collection;)V", at = @At("RETURN"), remap = false)
    private void villagerretaliation$uploadedMask(@Coerce Object commands, Collection<?> outputs, CallbackInfo callback) {
        DialogueCameraOcclusion.uploadedSodium(outputs);
    }
}
