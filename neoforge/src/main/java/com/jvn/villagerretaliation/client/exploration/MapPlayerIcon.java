package com.jvn.villagerretaliation.client.exploration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class MapPlayerIcon {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("vr_explored_map", "textures/gui/player.png");
    private MapPlayerIcon() { }
    static void render(GuiGraphics graphics, float x, float y, float yaw) {
        graphics.pose().pushPose(); graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(yaw + 180));
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        graphics.blit(TEXTURE, -7, -5, 0, 0, 14, 10, 14, 10);
        graphics.pose().popPose();
    }
}
