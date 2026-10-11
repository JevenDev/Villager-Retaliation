package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class DialogueCameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(net.minecraft.world.phys.Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void villagerretaliation$dialogueCamera(BlockGetter level, Entity entity, boolean detached,
            boolean reversed, float partialTick, CallbackInfo callback) {
        var pose = DialogueCameraController.update((Camera) (Object) this, partialTick);
        if (pose != null) {
            this.detached = DialogueCameraController.detached();
            setPosition(pose.position());
            setRotation(pose.yaw(), pose.pitch(), 0);
        }
        DialogueCameraController.rememberCamera((Camera) (Object) this);
    }
}
