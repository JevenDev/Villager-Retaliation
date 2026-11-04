package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.MapView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class MapOrientation {
    static int scaleBlocks(double scale, int maxPixels) {
        double desired = maxPixels / scale;
        double power = Math.pow(10, Math.floor(Math.log10(desired)));
        double fraction = desired / power;
        return Math.max(1, (int) ((fraction >= 5 ? 5 : fraction >= 2 ? 2 : 1) * power));
    }
    static void render(GuiGraphics graphics, MapView view, Font font, int mouseX, int mouseY, boolean minimap) {
        if (minimap) {
            int northX = view.width() - 10, northY = 5;
            graphics.fill(northX - 7, northY - 2, northX + 8, northY + 11, 0x99000000);
            graphics.drawCenteredString(font, MapLayers.text("north"), northX, northY, 0xffeee8d8);
            return;
        }
        int y = view.height() - 40;
        if (mouseX >= 0 && mouseX < view.width() && mouseY >= 64 && mouseY < view.height() - 64) {
            var coordinates = MapLocationsClient.text("coordinates", (int) Math.floor(view.worldX(mouseX)), (int) Math.floor(view.worldZ(mouseY)));
            String text = font.plainSubstrByWidth(coordinates.getString(), Math.max(80, view.width() - 210));
            int left = (view.width() - font.width(text)) / 2;
            graphics.fill(left - 3, y - 2, left + font.width(text) + 3, y + font.lineHeight + 2, 0x99000000);
            graphics.drawString(font, text, left, y, 0xffeee8d8);
        }
    }
}
