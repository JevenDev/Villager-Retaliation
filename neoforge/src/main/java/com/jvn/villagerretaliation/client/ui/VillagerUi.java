package com.jvn.villagerretaliation.client.ui;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class VillagerUi {
    private static final VillagerNineSlice FRAME = new VillagerNineSlice(
            VillagerRetaliationClientAssets.ATTRIBUTE_FILTER_SCROLL_ROW_TEXTURE, 132, 24, 3, 3, 3, 3);
    private static final VillagerNineSlice SELECTED = new VillagerNineSlice(
            VillagerRetaliationClientAssets.ATTRIBUTE_FILTER_SELECTED_SCROLL_ROW_TEXTURE, 136, 28, 5, 5, 5, 5);

    private VillagerUi() { }

    public static void frame(GuiGraphics graphics, int left, int top, int width, int height) {
        if (com.jvn.villagerretaliation.client.settlement.SettlementViewClient.basicUi()) {
            graphics.fill(left, top, left + width, top + height, 0xFF202020);
            graphics.renderOutline(left, top, width, height, 0xFF808080);
            return;
        }
        FRAME.renderAtTextureScale(graphics, left, top, width, height);
        graphics.fill(left + 3, top + 3, left + width - 3, top + height - 3, 0xF023282E);
    }

    public static void row(GuiGraphics graphics, int left, int top, int width, int height, boolean selected, boolean hovered) {
        if (com.jvn.villagerretaliation.client.settlement.SettlementViewClient.basicUi()) {
            graphics.fill(left, top, left + width, top + height, selected ? 0xFF606060 : hovered ? 0xFF484848 : 0xFF303030);
            graphics.renderOutline(left, top, width, height, selected ? 0xFFFFFFFF : 0xFF808080);
            return;
        }
        (selected ? SELECTED : FRAME).renderAtTextureScale(graphics, left, top, width, height);
        graphics.fill(left + 3, top + 3, left + width - 3, top + height - 3,
                selected ? 0xE02D5284 : hovered ? 0xF03B444E : 0xF023282E);
    }

    public static Button.Builder buttonBuilder(Component message, Button.OnPress action) {
        return new Button.Builder(message, action) {
            @Override
            public Button build() { return new OptionButton(this); }
        };
    }

    public static final class OptionButton extends Button {
        private boolean checkbox;
        private boolean checked;
        private ItemStack icon = ItemStack.EMPTY;
        private boolean selected;

        private OptionButton(Button.Builder builder) {
            super(builder);
            if (getTooltip() == null) setTooltip(Tooltip.create(getMessage()));
        }

        @Override
        public void setMessage(Component message) {
            super.setMessage(message);
            setTooltip(Tooltip.create(message));
        }

        public void checkbox(boolean visible, boolean checked) {
            this.checkbox = visible;
            this.checked = checked;
        }

        public void icon(ItemStack icon) { this.icon = icon; }

        public void selected(boolean selected) { this.selected = selected; }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            row(graphics, getX(), getY(), getWidth(), getHeight(), this.selected || this.checkbox && this.checked, isHoveredOrFocused() && this.active);
            var font = Minecraft.getInstance().font;
            int textLeft = getX() + 5;
            if (this.checkbox) {
                int y = getY() + (getHeight() - 9) / 2;
                graphics.blit(VillagerRetaliationClientAssets.INTERACTION_CONTAINER_OPTION_CHECKBOX_TEXTURE,
                        textLeft, y, 0, 0, 9, 9, 9, 9);
                if (this.checked) graphics.drawString(font, "x", textLeft + 2, y, this.active ? 0xFFFFFF : 0x999999, false);
                textLeft += 13;
            }
            if (!this.icon.isEmpty()) {
                graphics.renderItem(this.icon, textLeft, getY() + (getHeight() - 16) / 2);
                textLeft += 20;
            }
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, getRight() - textLeft - 5));
            graphics.drawString(font, label, textLeft, getY() + (getHeight() - font.lineHeight) / 2,
                    this.active ? 0xFFFFFF : 0x999999, false);
            if (isFocused()) graphics.renderOutline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2,
                    com.jvn.villagerretaliation.client.settlement.SettlementViewClient.basicUi() ? 0xFFFFFFFF : 0xFF81B6FF);
        }
    }
}
