package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraMaskView;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.List;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public abstract class DialogueCameraSectionMixin {
    @Inject(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;", at = @At("RETURN"))
    private void villagerretaliation$finishedMask(SectionPos position, RenderChunkRegion region, VertexSorting sorting,
            SectionBufferBuilderPack buffers, List<AddSectionGeometryEvent.AdditionalSectionRenderer> additional,
            CallbackInfoReturnable<SectionCompiler.Results> callback) {
        var mask = ((DialogueCameraMaskView) region).villagerretaliation$cameraMask();
        if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) DialogueCameraOcclusion.built(position, mask);
        else DialogueCameraOcclusion.captureBuild(position, mask);
    }
}
