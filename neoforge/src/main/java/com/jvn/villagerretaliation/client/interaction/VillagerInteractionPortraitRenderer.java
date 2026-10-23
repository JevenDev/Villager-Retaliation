package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.villager.VillagerModelPreviewRenderContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class VillagerInteractionPortraitRenderer {
    private VillagerInteractionPortraitRenderer() { }

    static void renderBust(GuiGraphics graphics, LivingEntity entity, int left, int top, int right, int bottom) {
        float entityHeight = entity.getBbHeight();
        float upperBodyHeight = entityHeight * 0.65F;
        float upperBodyWidth = Math.max(entity.getBbWidth() * 1.5F, entityHeight * 0.55F);
        float renderScale = Math.min((bottom - top - 4) / upperBodyHeight, (right - left - 4) / upperBodyWidth);
        float renderY = top + 2 + (entityHeight / 2 - 0.0625F * entity.getScale()) * renderScale;
        render(graphics, entity, (left + right) / 2.0F, renderY, renderScale * entity.getScale(), -0.6F, 0, false);
    }

    static void render(GuiGraphics graphics, LivingEntity entity, float centerX, float renderY,
            float size, float yaw, float pitch, boolean talking) {
        Quaternionf entityRotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraRotation = new Quaternionf().rotateX(pitch * 20.0F * ((float) Math.PI / 180.0F));
        entityRotation.mul(cameraRotation);
        float previousBodyRot = entity.yBodyRot;
        float previousYRot = entity.getYRot();
        float previousXRot = entity.getXRot();
        float previousHeadRotO = entity.yHeadRotO;
        float previousHeadRot = entity.yHeadRot;
        boolean previousSprinting = entity.isSprinting();
        entity.yBodyRot = 180.0F + yaw * 20.0F;
        entity.setYRot(180.0F + yaw * 40.0F);
        entity.setXRot(-pitch * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        entity.setSprinting(talking);
        float scale = entity.getScale();
        try (var ignored = VillagerModelPreviewRenderContext.begin(entity, VillagerModelPreviewRenderContext.PreviewType.INTERACTION)) {
            InventoryScreen.renderEntityInInventory(graphics, centerX, renderY, size / scale,
                    new Vector3f(0.0F, entity.getBbHeight() / 2.0F + 0.0625F * scale, 0.0F),
                    entityRotation, cameraRotation, entity);
        } finally {
            entity.yBodyRot = previousBodyRot;
            entity.setYRot(previousYRot);
            entity.setXRot(previousXRot);
            entity.yHeadRotO = previousHeadRotO;
            entity.yHeadRot = previousHeadRot;
            entity.setSprinting(previousSprinting);
        }
    }
}
