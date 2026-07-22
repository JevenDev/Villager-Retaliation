package com.jvn.villagerretaliation.client.duel;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public final class DuelInventoryScreenRenderer {
    private static final int WIDTH = 176;
    public static final int HEIGHT = 122;

    private DuelInventoryScreenRenderer() {}

    public static void render(GuiGraphics graphics, InventoryScreen screen, float mouseX, float mouseY) {
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        graphics.blit(VillagerRetaliationClientAssets.DUEL_INVENTORY_TEXTURE,
                left, top, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        if (Minecraft.getInstance().player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    left + 63,
                    top + 8,
                    left + 112,
                    top + 78,
                    30,
                    0.0625F,
                    mouseX,
                    mouseY,
                    Minecraft.getInstance().player);
        }
    }
}
