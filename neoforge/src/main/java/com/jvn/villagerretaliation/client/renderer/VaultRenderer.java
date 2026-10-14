package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.storage.StorageBlock;
import com.jvn.villagerretaliation.storage.StorageBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class VaultRenderer {
    private static final ModelResourceLocation BODY=model("vault_body"), DOOR=model("vault_door"), COG=model("vault_cog");
    private static final ModelResourceLocation LOWER_HINGE=model("vault_hinge_lower"), UPPER_HINGE=model("vault_hinge_upper");
    private static final ModelResourceLocation HINGE_MOUNTS=model("vault_hinge_mounts");
    private static final ModelResourceLocation CHARGE=model("mounted_charge");
    private static final ModelResourceLocation[] DAMAGE={model("lock_damage_1"),model("lock_damage_2"),model("lock_damage_3")};
    private final BlockRenderDispatcher blockRenderer;
    private final VaultContentsRenderer contentsRenderer;

    public VaultRenderer(BlockEntityRendererProvider.Context context) {
        blockRenderer=context.getBlockRenderDispatcher();
        contentsRenderer=new VaultContentsRenderer(context);
    }
    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(VillagerRetaliation.id("block/"+name));
    }
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(BODY); event.register(DOOR); event.register(COG);
        event.register(LOWER_HINGE); event.register(UPPER_HINGE); event.register(HINGE_MOUNTS);
    }
    public static float doorAngle(float openness,float breachProgress) {
        float closed=1-Mth.clamp((openness-0.25F)/0.75F,0,1);
        float intact=1-breachProgress;
        return Mth.lerp(1-intact*intact*intact*intact,-100*(1-closed*closed*closed),-115);
    }
    public static float cogAngle(float openness) {
        float turned=Mth.clamp(openness/0.25F,0,1);
        return -45*turned*turned*(3-2*turned);
    }
    public void render(StorageBlockEntity bank,float partialTick,PoseStack poses,MultiBufferSource buffer,int light,int overlay) {
        if(!bank.controller() || !bank.vaultFormed()) return;
        var state=bank.getBlockState();
        poses.pushPose();
        poses.translate(0.5F,0.5F,0.5F);
        poses.mulPose(Axis.YP.rotationDegrees(180-state.getValue(StorageBlock.FACING).toYRot()));
        poses.translate(-0.5F,-0.5F,-0.5F);
        draw(BODY,8,poses,buffer,light,overlay);
        draw(HINGE_MOUNTS,10,poses,buffer,light,overlay);
        float openness=bank.getOpenNess(partialTick), breach=bank.getBreachProgress(partialTick);
        if(openness>0.25F || breach>0) contentsRenderer.render(bank,poses,buffer,light,overlay);
        float sag=breach*breach, impulse=SafeRenderer.blastImpulse(breach);
        if(breach>0) {
            looseHinge(LOWER_HINGE,9,sag,poses,buffer,light,overlay);
            looseHinge(UPPER_HINGE,39,sag/2,poses,buffer,light,overlay);
        }
        poses.translate(0,0,-impulse/4);
        pivot(poses,23,24,-21);
        poses.mulPose(Axis.YP.rotationDegrees(doorAngle(openness,breach)-25*impulse));
        unpivot(poses,23,24,-21);
        pivot(poses,23,39,-21);
        poses.mulPose(Axis.ZP.rotationDegrees(6*sag));
        unpivot(poses,23,39,-21);
        poses.translate(0,-sag/16,0);
        draw(DOOR,10,poses,buffer,light,overlay);
        if(breach==0) {
            draw(LOWER_HINGE,10,poses,buffer,light,overlay);
            draw(UPPER_HINGE,10,poses,buffer,light,overlay);
        }
        if(state.getValue(StorageBlock.DAMAGE)>0) {
            poses.pushPose();
            poses.translate(-1,-1,-0.3F);
            poses.scale(3,3,1);
            renderModel(DAMAGE[state.getValue(StorageBlock.DAMAGE)-1],poses,buffer,light,overlay);
            poses.popPose();
        }
        if(state.getValue(StorageBlock.CHARGE)) {
            poses.pushPose();
            poses.translate(0,0,-0.3F);
            int flash=SafeRenderer.chargeFlashes(bank.fuse)?OverlayTexture.pack(OverlayTexture.u(1),10):OverlayTexture.NO_OVERLAY;
            int frontLight=LevelRenderer.getLightColor(bank.getLevel(),bank.getBlockPos().relative(state.getValue(StorageBlock.FACING)));
            renderModel(CHARGE,poses,buffer,frontLight,flash);
            poses.popPose();
        }
        pivot(poses,0,24,-26.40909F);
        poses.mulPose(Axis.XP.rotationDegrees(12*sag));
        poses.mulPose(Axis.ZP.rotationDegrees(cogAngle(Math.max(openness,breach))-8*sag));
        unpivot(poses,0,24,-26.40909F);
        draw(COG,16,poses,buffer,light,overlay);
        poses.popPose();
    }

    private static void pivot(PoseStack poses,float x,float y,float z) {
        poses.translate((x+8)/16,(y-16)/16,(z+24)/16);
    }
    private static void unpivot(PoseStack poses,float x,float y,float z) {
        poses.translate(-(x+8)/16,-(y-16)/16,-(z+24)/16);
    }
    private void looseHinge(ModelResourceLocation model,float y,float sag,PoseStack poses,MultiBufferSource buffer,int light,int overlay) {
        poses.pushPose();
        pivot(poses,23,y,-21);
        poses.translate(sag/16,-sag/8,-sag/16);
        poses.mulPose(Axis.ZP.rotationDegrees(16*sag));
        poses.mulPose(Axis.XP.rotationDegrees(10*sag));
        unpivot(poses,23,y,-21);
        draw(model,10,poses,buffer,light,overlay);
        poses.popPose();
    }
    private void draw(ModelResourceLocation model,int zOffset,PoseStack poses,MultiBufferSource buffer,int light,int overlay) {
        poses.pushPose();
        // separate export offsets keep every part inside vanilla model coordinate limits
        poses.translate(model==LOWER_HINGE || model==UPPER_HINGE || model==HINGE_MOUNTS?1F/16:0,0,(24-zOffset)/16F);
        renderModel(model,poses,buffer,light,overlay);
        poses.popPose();
    }
    private void renderModel(ModelResourceLocation location,PoseStack poses,MultiBufferSource buffer,int light,int overlay) {
        var model=blockRenderer.getBlockModelShaper().getModelManager().getModel(location);
        blockRenderer.getModelRenderer().renderModel(poses.last(),buffer.getBuffer(Sheets.cutoutBlockSheet()),null,model,1,1,1,light,overlay);
    }
}
