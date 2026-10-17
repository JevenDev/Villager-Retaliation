package com.jvn.villagerretaliation.client.exploration;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class MapVillageIcon {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("vr_explored_map", "textures/gui/village.png");

    private MapVillageIcon() { }

    static void render(GuiGraphics graphics, int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(TEXTURE, x - 8, y - 8, 0, 0, 16, 16, 16, 16);
        RenderSystem.disableBlend();
    }
}
