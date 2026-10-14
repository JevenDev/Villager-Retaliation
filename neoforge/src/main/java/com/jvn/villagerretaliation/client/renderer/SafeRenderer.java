package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.storage.StorageBlock;
import com.jvn.villagerretaliation.storage.StorageBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class SafeRenderer implements BlockEntityRenderer<StorageBlockEntity> {
    private static final ModelResourceLocation DOOR = model("safe_door");
    private static final ModelResourceLocation HANDLE = model("safe_handle");
    private static final ModelResourceLocation HANDLE_MOUNT = model("safe_handle_mount");
    private static final ModelResourceLocation CHARGE = model("mounted_charge");
    private static final ModelResourceLocation[] DAMAGE = {
            model("lock_damage_1"), model("lock_damage_2"), model("lock_damage_3")
    };
    private final BlockRenderDispatcher blockRenderer;

    public SafeRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(VillagerRetaliation.id("block/" + name));
    }

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(DOOR);
        event.register(HANDLE);
        event.register(HANDLE_MOUNT);
        event.register(CHARGE);
        for (var model : DAMAGE) event.register(model);
    }

    public static float doorAngle(float openness) {
        float closed = 1 - Mth.clamp((openness - 0.25F) / 0.75F, 0, 1);
        return -90 * (1 - closed * closed * closed);
    }

    public static float handleAngle(float openness) {
        float pressed = Mth.clamp(openness / 0.75F, 0, 1);
        return -20 * pressed * pressed * (3 - 2 * pressed);
    }

    @Override
    public void render(
            StorageBlockEntity bank,
            float partialTick,
            PoseStack poses,
            MultiBufferSource buffer,
            int light,
            int overlay) {
        if (!bank.safe()) return;
        BlockState state = bank.getBlockState();
        poses.pushPose();
        poses.translate(0.5F, 0.5F, 0.5F);
        poses.mulPose(Axis.YP.rotationDegrees(180 - state.getValue(StorageBlock.FACING).toYRot()));
        poses.translate(-0.5F, -0.5F, -0.5F);

        float openness = bank.getOpenNess(partialTick);
        // blockbench hinge [4, 8, 0] after turning the model's front north
        poses.translate(1, 0.5F, 0.25F);
        poses.mulPose(Axis.YP.rotationDegrees(doorAngle(openness)));
        poses.translate(-1, -0.5F, -0.25F);

        renderModel(DOOR, poses, buffer, light, overlay);
        if (state.getValue(StorageBlock.CHARGE)) renderModel(CHARGE, poses, buffer, light, overlay);
        int damage = state.getValue(StorageBlock.DAMAGE);
        if (damage > 0) renderModel(DAMAGE[damage - 1], poses, buffer, light, overlay);

        renderModel(HANDLE_MOUNT, poses, buffer, light, overlay);
        // inherit the door swing before turning only the grip at its attachment point
        poses.pushPose();
        poses.translate(4.5F / 16, 6.0F / 16, -0.5F / 16);
        poses.mulPose(Axis.ZP.rotationDegrees(handleAngle(openness)));
        poses.translate(-4.5F / 16, -6.0F / 16, 0.5F / 16);
        renderModel(HANDLE, poses, buffer, light, overlay);
        poses.popPose();
        poses.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(StorageBlockEntity bank) {
        return new AABB(bank.getBlockPos()).inflate(1, 0, 1);
    }

    private void renderModel(
            ModelResourceLocation location,
            PoseStack poses,
            MultiBufferSource buffer,
            int light,
            int overlay) {
        var model = blockRenderer.getBlockModelShaper().getModelManager().getModel(location);
        blockRenderer.getModelRenderer().renderModel(
                poses.last(), buffer.getBuffer(Sheets.cutoutBlockSheet()), null, model, 1, 1, 1, light, overlay);
    }
}
