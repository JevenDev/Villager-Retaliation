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
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class VillagerFishingHookRenderer extends EntityRenderer<VillagerFishingHook> {
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE_LOCATION);
    private static final int LINE_SEGMENTS = 16;
    private static final float LINE_HALF_WIDTH = 0.0125F;

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
        renderString(x, y, z, lineOffset(x, y, z), buffer.getBuffer(RenderType.leash()), poseStack.last().pose(), packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private Vec3 getOwnerHandPos(LivingEntity owner, float swing, float partialTick) {
        int side = owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        ItemStack mainHand = owner instanceof AbstractVillager villager
                ? VillagerRenderEquipmentState.visibleMainHand(villager)
                : owner.getMainHandItem();
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
                .add(-cos * sideOffset - sin * forwardOffset, crouchOffset - 0.75D * scale, -sin * sideOffset + cos * forwardOffset)
                .add(0.0D, -swing * 0.1D, 0.0D);
    }

    private static float fraction(int numerator, int denominator) {
        return (float)numerator / denominator;
    }

    private Vec3 lineOffset(float x, float y, float z) {
        Vec3 direction = new Vec3(x, y, z);
        Vector3f forwardVector = new Vector3f(0.0F, 0.0F, -1.0F).rotate(this.entityRenderDispatcher.cameraOrientation());
        Vec3 offset = direction.cross(new Vec3(forwardVector.x(), forwardVector.y(), forwardVector.z()));
        if (offset.lengthSqr() < 1.0E-5D) {
            Vector3f rightVector = new Vector3f(1.0F, 0.0F, 0.0F).rotate(this.entityRenderDispatcher.cameraOrientation());
            offset = new Vec3(rightVector.x(), rightVector.y(), rightVector.z());
        }
        if (offset.lengthSqr() < 1.0E-5D) {
            offset = new Vec3(-z, 0.0D, x);
        }
        return offset.lengthSqr() < 1.0E-5D
                ? new Vec3(LINE_HALF_WIDTH, 0.0D, 0.0D)
                : offset.normalize().scale(LINE_HALF_WIDTH);
    }

    private static void renderString(float x, float y, float z, Vec3 offset, VertexConsumer consumer, Matrix4f pose, int packedLight) {
        for (int index = 0; index <= LINE_SEGMENTS; index++) {
            float stringFraction = fraction(index, LINE_SEGMENTS);
            float currentX = x * stringFraction;
            float currentY = y * (stringFraction * stringFraction + stringFraction) * 0.5F + 0.25F;
            float currentZ = z * stringFraction;
            stringRibbonVertex(consumer, pose, packedLight, currentX + (float)offset.x, currentY + (float)offset.y, currentZ + (float)offset.z);
            stringRibbonVertex(consumer, pose, packedLight, currentX - (float)offset.x, currentY - (float)offset.y, currentZ - (float)offset.z);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int packedLight, float x, int y, int u, int v) {
        consumer.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void stringRibbonVertex(VertexConsumer consumer, Matrix4f pose, int packedLight, float x, float y, float z) {
        consumer.addVertex(pose, x, y, z).setColor(0, 0, 0, 255).setLight(packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(VillagerFishingHook entity) {
        return TEXTURE_LOCATION;
    }
}
