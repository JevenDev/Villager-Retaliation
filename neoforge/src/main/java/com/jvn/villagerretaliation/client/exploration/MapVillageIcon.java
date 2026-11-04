package com.jvn.villagerretaliation.client.exploration;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class MapVillageIcon {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/map_village.png");

    private MapVillageIcon() { }

    static void render(GuiGraphics graphics, int x, int y) {
        MapMarkerIcon.render(graphics, TEXTURE, x - 8, y - 16, 16);
    }

    static void render(GuiGraphics graphics, MapMarkerLayout.Sprite sprite) {
        MapMarkerIcon.render(graphics, TEXTURE, (float) sprite.x(), (float) sprite.y(), (float) sprite.size());
    }
}
