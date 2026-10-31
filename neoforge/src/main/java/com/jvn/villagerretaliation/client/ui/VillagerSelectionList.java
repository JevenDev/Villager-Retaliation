package com.jvn.villagerretaliation.client.ui;

import com.jvn.toucanlib.client.ui.ToucanSelectionList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;

public abstract class VillagerSelectionList<E extends ObjectSelectionList.Entry<E>> extends ToucanSelectionList<E> {
    protected VillagerSelectionList(Minecraft minecraft, int width, int height, int top, int rowHeight) {
        super(minecraft, width, height, top, rowHeight, VillagerUi::style);
    }
}
