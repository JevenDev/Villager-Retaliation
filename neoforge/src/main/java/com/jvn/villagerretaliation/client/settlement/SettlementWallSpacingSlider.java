package com.jvn.villagerretaliation.client.settlement;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

final class SettlementWallSpacingSlider extends AbstractSliderButton {
    private final SettlementWallEditor editor;
    SettlementWallSpacingSlider(SettlementWallEditor editor) {
        super(0, 0, 118, 18, Component.empty(), 10.0 / 58);
        this.editor = editor;
    }
    void sync() {
        if (this.editor.active()) this.value = (this.editor.draft().spacing() - 6) / 58.0;
        updateMessage();
    }
    @Override
    public void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int color = this.active ? 0xFFFFFFFF : 0xFF777777;
        if (this.active && isHoveredOrFocused()) graphics.fill(getX(), getY(), getRight(), getBottom(), 0x30FFFFFF);
        graphics.fill(getX() + 4, getBottom() - 3, getRight() - 4, getBottom() - 2, 0xFF555555);
        int thumb = getX() + 4 + (int) Math.round(this.value * (getWidth() - 12));
        graphics.fill(thumb, getBottom() - 5, thumb + 4, getBottom(), color);
        graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + 2, color);
    }

    @Override protected void updateMessage() { setMessage(Component.literal("Spacing " + (6 + (int) Math.round(this.value * 58)))); }
    @Override protected void applyValue() {
        if (this.editor.active() && !this.editor.confirming()) this.editor.draft().spacing(6 + (int) Math.round(this.value * 58));
    }
}
