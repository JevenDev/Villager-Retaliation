package com.jvn.villagerretaliation.client.repair;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.repair.SmithRepairAnimationState;
import com.jvn.villagerretaliation.repair.SmithRepairWorkstation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class SmithRepairRenderer {
    private static final ModelResourceLocation HAMMER = ModelResourceLocation.standalone(VillagerRetaliation.id("item/smithing_hammer"));
    private static final ItemStack HAMMER_STACK = new ItemStack(Items.IRON_AXE);

    private SmithRepairRenderer() {
    }

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(HAMMER);
    }

    public static void render(Villager villager, float partialTick, PoseStack poses, MultiBufferSource buffer) {
        if (!SmithRepairAnimationState.isWorking(villager) || villager.isInvisible()) return;
        float elapsed = SmithRepairAnimation.elapsed(villager, partialTick);
        if (elapsed >= SmithRepairWorkstation.WORK_TICKS) return;
        var state = (SmithRepairAnimationState) villager;
        Vec3 origin = villager.getPosition(partialTick);
        Vec3 item = SmithRepairWorkstation.itemPosition(villager, state.villagerretaliation$repairStation());
        var minecraft = Minecraft.getInstance();
        int light = LevelRenderer.getLightColor(villager.level(), BlockPos.containing(item));
        float yaw = Mth.rotLerp(partialTick, villager.yBodyRotO, villager.yBodyRot);
        poses.pushPose();
        Vec3 offset = item.subtract(origin);
        poses.translate(offset.x, offset.y, offset.z);
        poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        poses.mulPose(Axis.XP.rotationDegrees(90));
        poses.mulPose(Axis.ZP.rotationDegrees(-35));
        poses.scale(0.65F, 0.65F, 0.65F);
        minecraft.getItemRenderer().renderStatic(state.villagerretaliation$repairItem(), ItemDisplayContext.NONE,
                light, OverlayTexture.NO_OVERLAY, poses, buffer, villager.level(), villager.getId());
        poses.popPose();
    }

    public static void renderHammer(ItemDisplayContext context, boolean leftHand, PoseStack poses, MultiBufferSource buffer, int light) {
        var minecraft = Minecraft.getInstance();
        minecraft.getItemRenderer().render(HAMMER_STACK, context, leftHand, poses, buffer,
                light, OverlayTexture.NO_OVERLAY, minecraft.getModelManager().getModel(HAMMER));
    }
}
