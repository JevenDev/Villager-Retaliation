package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.storage.StorageBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;

final class VaultContentsRenderer {
    private static final float FLOOR=-14F/16;
    private final ItemRenderer itemRenderer;

    VaultContentsRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer=context.getItemRenderer();
    }
    void render(StorageBlockEntity bank,PoseStack poses,MultiBufferSource buffer,int light,int overlay) {
        var display=bank.vaultDisplay();
        int blocks=0,items=0;
        for(int pile=0;pile<display.getContainerSize();pile++) {
            var stack=display.getItem(pile);
            if(!stack.isEmpty()) { if(stack.getItem() instanceof BlockItem) blocks++; else items++; }
        }
        int rows=(blocks+2)/3+(items+2)/3,blockIndex=0,itemIndex=0;
        float rowSpacing=2.34F/Math.max(1,rows);
        float blockSize=Math.min(0.55F-0.05F*Math.min(3,blocks),(rowSpacing-0.12F)/2);
        float itemSize=Math.min(0.58F,(rowSpacing-0.18F)/1.42F);
        for(int pile=0;pile<display.getContainerSize();pile++) {
            var stack=display.getItem(pile);
            if(stack.isEmpty()) continue;
            var model=itemRenderer.getModel(stack,bank.getLevel(),null,pile);
            var bounds=bounds(model);
            var center=bounds.getCenter();
            var random=RandomSource.create(bank.getBlockPos().asLong()^Item.getId(stack.getItem())*7219L);
            boolean solid=model.isGui3d();
            float size=(solid?blockSize:itemSize)*(0.92F+random.nextFloat()*0.08F);
            float scale=size/(float)Math.max(bounds.getXsize(),Math.max(bounds.getYsize(),bounds.getZsize()));
            float depthScale=solid?scale:scale*1.8F;
            float height=(float)bounds.getYsize()*scale;
            int maxLayers=Math.max(1,(int)((1.8F+random.nextFloat()*0.65F)/Math.max(0.01F,height)));
            int corner=random.nextInt(4);
            int[] limits=new int[4];
            for(int column=0;column<4;column++) limits[column^corner]=Math.max(1,maxLayers-(column==1?2:column==2?1:column==3?3:0));
            int copies=Mth.clamp(1+(stack.getCount()-1)/(solid?3:4),1,solid?24:20);
            if(solid) copies=Math.min(copies,limits[0]+limits[1]+limits[2]+limits[3]);
            boolean block=stack.getItem() instanceof BlockItem;
            int index=block?blockIndex++:itemIndex++;
            int columns=Math.min(3,(block?blocks:items)-index/3*3);
            int row=block?rows-1-index/3:index/3;
            float x=0.5F+(index%3-(columns-1)/2F)*(block?0.86F:0.74F)+(random.nextFloat()-0.5F)*0.04F;
            float z=0.455F+(row+0.5F)*rowSpacing+(random.nextFloat()-0.5F)*0.1F;
            float pileYaw=random.nextFloat()*10-5;
            int[] heights=new int[4];
            for(int copy=0;copy<copies;copy++) {
                float dx,dz,base,pitch,roll,yaw;
                if(solid) {
                    int column=copy%4;
                    if(copy>=4) {
                        column=0;
                        for(int candidate=1;candidate<4;candidate++)
                            if((float)heights[candidate]/limits[candidate]<(float)heights[column]/limits[column]) column=candidate;
                    }
                    dx=copies==1?0:(column%2-0.5F)*(size+0.006F);
                    dz=copies<=2?0:(column/2-0.5F)*(size+0.006F);
                    int layer=heights[column]++;
                    dx+=(random.nextFloat()-0.5F)*0.025F;
                    dz+=(random.nextFloat()-0.5F)*0.025F;
                    base=layer*height;
                    pitch=0; roll=0; yaw=pileYaw+random.nextFloat()*4-2;
                } else {
                    boolean leaning=copy>=Math.max(1,copies-4);
                    float spread=Math.min(0.12F,rowSpacing*0.12F);
                    dx=(copy%2==0?-1:1)*spread+(random.nextFloat()-0.5F)*0.12F;
                    dz=(copy%2==0?-1:1)*spread/2+(random.nextFloat()-0.5F)*0.16F;
                    base=leaning?0:(copy/2+copy%2*0.4F)*(float)bounds.getZsize()*depthScale;
                    pitch=leaning?18+random.nextFloat()*28:86+random.nextFloat()*8;
                    roll=leaning?(copy%2==0?-1:1)*(12+random.nextFloat()*16):0;
                    yaw=random.nextFloat()*(leaning?50:150)-(leaning?25:75);
                }
                var rotation=Axis.YP.rotationDegrees(yaw).mul(Axis.XP.rotationDegrees(pitch)).mul(Axis.ZP.rotationDegrees(roll));
                var matrix=new Matrix3f().rotation(rotation);
                float halfHeight=(float)(Math.abs(matrix.m01())*bounds.getXsize()*scale
                        +Math.abs(matrix.m11())*bounds.getYsize()*scale+Math.abs(matrix.m21())*bounds.getZsize()*depthScale)/2;
                poses.pushPose();
                poses.translate(x+dx,FLOOR+base+halfHeight,z+dz);
                poses.mulPose(rotation);
                poses.scale(scale,scale,depthScale);
                poses.translate(0.5-center.x,0.5-center.y,0.5-center.z);
                itemRenderer.render(stack,ItemDisplayContext.NONE,false,poses,buffer,light,overlay,model);
                poses.popPose();
            }
        }
    }
    private static AABB bounds(BakedModel model) {
        float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        var random=RandomSource.create(42);
        for(int side=0;side<=6;side++) {
            random.setSeed(42);
            for(var quad:model.getQuads(null,side==6?null:Direction.values()[side],random)) {
                var vertices=quad.getVertices();
                for(int vertex=0;vertex<vertices.length;vertex+=vertices.length/4) {
                    float x=Float.intBitsToFloat(vertices[vertex]),y=Float.intBitsToFloat(vertices[vertex+1]),z=Float.intBitsToFloat(vertices[vertex+2]);
                    minX=Math.min(minX,x); minY=Math.min(minY,y); minZ=Math.min(minZ,z);
                    maxX=Math.max(maxX,x); maxY=Math.max(maxY,y); maxZ=Math.max(maxZ,z);
                }
            }
        }
        return minX==Float.POSITIVE_INFINITY || Math.max(maxX-minX,Math.max(maxY-minY,maxZ-minZ))<0.001F
                ?new AABB(0,0,0,1,1,1):new AABB(minX,minY,minZ,maxX,maxY,maxZ);
    }
}
