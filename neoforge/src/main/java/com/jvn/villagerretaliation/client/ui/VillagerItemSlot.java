package com.jvn.villagerretaliation.client.ui;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class VillagerItemSlot extends Button {
    private final Supplier<ItemStack> item;
    private final IntConsumer click;
    private final IntConsumer scroll;
    private boolean selected;

    public VillagerItemSlot(int x, int y, Supplier<ItemStack> item, IntConsumer click, IntConsumer scroll) {
        super(x, y, 20, 20, Component.empty(), ignored -> click.accept(0), DEFAULT_NARRATION);
        this.item = item;
        this.click = click;
        this.scroll = scroll;
    }

    public void selected(boolean selected) { this.selected = selected; }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!this.active || !this.visible || !isMouseOver(x, y) || button != 0 && button != 1) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        this.click.accept(button);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!this.active || !this.visible || !isMouseOver(x, y) || vertical == 0 || this.scroll == null) return false;
        this.scroll.accept(vertical > 0 ? 1 : -1);
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(VillagerRetaliationClientAssets.ITEM_FILTER_CONTAINER_TEXTURE,
                getX() + 1, getY() + 1, 7, 17, 18, 18, 176, 166);
        var stack = this.item.get();
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, getX() + 2, getY() + 2);
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, getX() + 2, getY() + 2);
        }
        if (isHoveredOrFocused() && this.active) graphics.fill(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2, 0x40FFFFFF);
        if (this.selected || isFocused()) graphics.renderOutline(getX(), getY(), 20, 20, 0xFF81B6FF);
    }
}
