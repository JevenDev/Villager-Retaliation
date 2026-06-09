package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.entity.VillagerFishingHook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VillagerFishingHookRenderer extends EntityRenderer<VillagerFishingHook> {
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE_LOCATION);

    public VillagerFishingHookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(VillagerFishingHook entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Entity owner = entity.getOwner();
        if (!(owner instanceof LivingEntity livingOwner)) {
            return;
        }
        poseStack.pushPose();
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer hookConsumer = buffer.getBuffer(RENDER_TYPE);
        vertex(hookConsumer, pose, packedLight, 0.0F, 0, 0, 1);
        vertex(hookConsumer, pose, packedLight, 1.0F, 0, 1, 1);
        vertex(hookConsumer, pose, packedLight, 1.0F, 1, 1, 0);
        vertex(hookConsumer, pose, packedLight, 0.0F, 1, 0, 0);
        poseStack.popPose();

        float attackAnim = livingOwner.getAttackAnim(partialTicks);
        float handSwing = Mth.sin(Mth.sqrt(attackAnim) * (float)Math.PI);
        Vec3 hand = getOwnerHandPos(livingOwner, handSwing, partialTicks);
        Vec3 hook = entity.getPosition(partialTicks).add(0.0D, 0.25D, 0.0D);
        float x = (float)(hand.x - hook.x);
        float y = (float)(hand.y - hook.y);
        float z = (float)(hand.z - hook.z);
        VertexConsumer lineConsumer = buffer.getBuffer(RenderType.lineStrip());
        PoseStack.Pose linePose = poseStack.last();
        for (int index = 0; index <= 16; index++) {
            stringVertex(x, y, z, lineConsumer, linePose, fraction(index, 16), fraction(index + 1, 16));
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private Vec3 getOwnerHandPos(LivingEntity owner, float swing, float partialTick) {
        int side = owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        ItemStack mainHand = owner.getMainHandItem();
        if (!mainHand.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.FISHING_ROD_CAST)) {
            side = -side;
        }
        float bodyYaw = Mth.lerp(partialTick, owner.yBodyRotO, owner.yBodyRot) * (float)(Math.PI / 180.0D);
        double sin = Mth.sin(bodyYaw);
        double cos = Mth.cos(bodyYaw);
        float scale = owner.getScale();
        double sideOffset = side * 0.35D * scale;
        double forwardOffset = 0.8D * scale;
        float crouchOffset = owner.isCrouching() ? -0.1875F : 0.0F;
        return owner.getEyePosition(partialTick)
                .add(-cos * sideOffset - sin * forwardOffset, crouchOffset - 0.45D * scale, -sin * sideOffset + cos * forwardOffset)
                .add(0.0D, -swing * 0.1D, 0.0D);
    }

    private static float fraction(int numerator, int denominator) {
        return (float)numerator / denominator;
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int packedLight, float x, int y, int u, int v) {
        consumer.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void stringVertex(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose, float stringFraction, float nextStringFraction) {
        float currentX = x * stringFraction;
        float currentY = y * (stringFraction * stringFraction + stringFraction) * 0.5F + 0.25F;
        float currentZ = z * stringFraction;
        float normalX = x * nextStringFraction - currentX;
        float normalY = y * (nextStringFraction * nextStringFraction + nextStringFraction) * 0.5F + 0.25F - currentY;
        float normalZ = z * nextStringFraction - currentZ;
        float length = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        normalX /= length;
        normalY /= length;
        normalZ /= length;
        consumer.addVertex(pose, currentX, currentY, currentZ).setColor(-16777216).setNormal(pose, normalX, normalY, normalZ);
    }

    @Override
    public ResourceLocation getTextureLocation(VillagerFishingHook entity) {
        return TEXTURE_LOCATION;
    }
}
