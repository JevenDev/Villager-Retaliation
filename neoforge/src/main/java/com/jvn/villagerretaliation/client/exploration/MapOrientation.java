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
        int northX = view.width() - (minimap ? 10 : 22), northY = minimap ? 5 : 66;
        graphics.fill(northX - 7, northY - 2, northX + 8, northY + (minimap ? 11 : 26), 0x99000000);
        graphics.drawCenteredString(font, MapLayers.text("north"), northX, northY, 0xffeee8d8);
        if (minimap) return;
        graphics.vLine(northX, northY + 13, northY + 22, 0xffeee8d8);
        graphics.fill(northX - 1, northY + 11, northX + 2, northY + 13, 0xffeee8d8);
        graphics.fill(northX - 3, northY + 13, northX - 1, northY + 15, 0xffeee8d8);
        graphics.fill(northX + 2, northY + 13, northX + 4, northY + 15, 0xffeee8d8);
        int blocks = scaleBlocks(view.scale(), 80), pixels = (int) Math.round(blocks * view.scale());
        int x = 10, y = view.height() - 40;
        var label = MapLayers.text("scale", blocks);
        graphics.fill(x - 3, y - 14, x + Math.max(pixels, font.width(label)) + 4, y + 4, 0x99000000);
        graphics.drawString(font, label, x, y - 12, 0xffeee8d8);
        graphics.hLine(x, x + pixels, y, 0xffeee8d8);
        graphics.vLine(x, y - 2, y + 3, 0xffeee8d8); graphics.vLine(x + pixels, y - 2, y + 3, 0xffeee8d8);
        if (mouseX >= 0 && mouseX < view.width() && mouseY >= 64 && mouseY < view.height() - 64) {
            var coordinates = MapLocationsClient.text("coordinates", (int) Math.floor(view.worldX(mouseX)), (int) Math.floor(view.worldZ(mouseY)));
            String text = font.plainSubstrByWidth(coordinates.getString(), Math.max(80, view.width() - 210));
            int left = (view.width() - font.width(text)) / 2;
            graphics.fill(left - 3, y - 2, left + font.width(text) + 3, y + font.lineHeight + 2, 0x99000000);
            graphics.drawString(font, text, left, y, 0xffeee8d8);
        }
    }
}
