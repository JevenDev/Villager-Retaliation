package com.jvn.villagerretaliation.client.reputation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

public record VillagerReputationClientTooltipComponent(VillagerReputationTooltipComponent component) implements ClientTooltipComponent {
    private static final int ICON_SIZE = 16;
    private static final int GAP = 6;

    @Override
    public int getHeight() {
        return ICON_SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return ICON_SIZE + GAP + font.width(component.text());
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f pose, MultiBufferSource.BufferSource bufferSource) {
        font.drawInBatch(component.text(), x + ICON_SIZE + GAP, y + 4, 0xFFFFFFFF, true, pose, bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        graphics.blit(VillagerReputationIconSet.iconFor(component.level()), x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }
}
