package com.jvn.villagerretaliation.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;

public abstract class VillagerSelectionList<E extends ObjectSelectionList.Entry<E>> extends ObjectSelectionList<E> {
    protected VillagerSelectionList(Minecraft minecraft, int width, int height, int top, int rowHeight) {
        super(minecraft, width, height, top, rowHeight);
    }

    @Override
    protected void renderListBackground(GuiGraphics graphics) { }

    @Override
    protected void renderListSeparators(GuiGraphics graphics) { }

    @Override
    protected void renderSelection(GuiGraphics graphics, int top, int width, int height, int outerColor, int innerColor) { }

    @Override
    protected void renderItem(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                              int index, int left, int top, int width, int height) {
        var entry = getEntry(index);
        VillagerUi.row(graphics, left - 2, top - 2, width, height + 2, entry == getSelected(), entry == getHovered());
        super.renderItem(graphics, mouseX, mouseY, partialTick, index, left, top, width, height);
    }
}
