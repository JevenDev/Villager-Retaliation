package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.block.PaymentBoxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class PaymentBoxScreen extends AbstractContainerScreen<PaymentBoxMenu> {
    private static final ResourceLocation CONTAINER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    public PaymentBoxScreen(PaymentBoxMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 114 + menu.rowCount() * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int topSectionHeight = this.menu.rowCount() * 18 + 17;
        graphics.blit(
                CONTAINER_TEXTURE,
                this.leftPos,
                this.topPos,
                0,
                0,
                this.imageWidth,
                topSectionHeight,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
        graphics.blit(
                CONTAINER_TEXTURE,
                this.leftPos,
                this.topPos + topSectionHeight,
                0,
                126,
                this.imageWidth,
                96,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
    }
}
