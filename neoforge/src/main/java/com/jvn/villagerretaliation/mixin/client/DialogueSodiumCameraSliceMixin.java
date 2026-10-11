package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class DialogueSodiumCameraSliceMixin {
    @Shadow @Final private ClientLevel level;
    @Shadow private int originBlockX;
    @Shadow private int originBlockY;
    @Shadow private int originBlockZ;
    @Unique private DialogueCameraOcclusion.Snapshot villagerretaliation$mask;

    @Inject(method = "copyData", at = @At("RETURN"), remap = false)
    private void villagerretaliation$captureMask(CallbackInfo callback) {
        villagerretaliation$mask = DialogueCameraOcclusion.snapshot(level);
        DialogueCameraOcclusion.captureBuild(SectionPos.of((originBlockX + 16) >> 4, (originBlockY + 16) >> 4,
                (originBlockZ + 16) >> 4), villagerretaliation$mask);
    }

    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true, remap = false)
    private void villagerretaliation$hideBlock(int x, int y, int z, CallbackInfoReturnable<BlockState> callback) {
        if (villagerretaliation$mask != null && villagerretaliation$mask.hides(new net.minecraft.core.BlockPos(x, y, z)))
            callback.setReturnValue(Blocks.AIR.defaultBlockState());
    }
}
