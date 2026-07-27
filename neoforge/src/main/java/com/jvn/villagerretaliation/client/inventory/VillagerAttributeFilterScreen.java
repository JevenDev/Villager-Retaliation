package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.ui.VillagerNineSlice;
import com.jvn.villagerretaliation.inventory.VillagerAttributeFilterMenu;
import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import com.jvn.villagerretaliation.network.AttributeFilterSelectPayload;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Compact attribute picker: insert a reference item, scroll through its derived
 * attributes, and choose whether matching items are allowed or denied.
 */
public final class VillagerAttributeFilterScreen
        extends AbstractContainerScreen<VillagerAttributeFilterMenu> {
    private static final int TEXTURE_WIDTH = 176;
    private static final int TEXTURE_HEIGHT = 166;
    private static final int SELECTOR_X = 43;
    private static final int SELECTOR_Y = 17;
    private static final int SELECTOR_WIDTH = 126;
    private static final int SELECTOR_HEIGHT = 18;
    private static final int SCROLL_ROW_FRAME = 3;
    private static final int SELECTED_ROW_FRAME = 5;
    private static final int SCROLL_ROW_WIDTH = SELECTOR_WIDTH + SCROLL_ROW_FRAME * 2;
    private static final int SCROLL_ROW_HEIGHT = SELECTOR_HEIGHT + SCROLL_ROW_FRAME * 2;
    private static final int SELECTED_ROW_WIDTH = SELECTOR_WIDTH + SELECTED_ROW_FRAME * 2;
    private static final int SELECTED_ROW_HEIGHT = SELECTOR_HEIGHT + SELECTED_ROW_FRAME * 2;
    private static final int NEIGHBOR_COUNT = 3;
    private static final int SCROLL_VISIBILITY_TICKS = 30;
    private static final int ATTRIBUTE_TEXT_PADDING = 8;
    private static final int SELECTOR_Z = 1_000;
    private static final VillagerNineSlice SCROLL_ROW_NINE_SLICE =
            new VillagerNineSlice(
                    VillagerRetaliationClientAssets.ATTRIBUTE_FILTER_SCROLL_ROW_TEXTURE,
                    SCROLL_ROW_WIDTH, SCROLL_ROW_HEIGHT,
                    SCROLL_ROW_FRAME, SCROLL_ROW_FRAME, SCROLL_ROW_FRAME, SCROLL_ROW_FRAME);
    private static final VillagerNineSlice SELECTED_ROW_NINE_SLICE =
            new VillagerNineSlice(
                    VillagerRetaliationClientAssets.ATTRIBUTE_FILTER_SELECTED_SCROLL_ROW_TEXTURE,
                    SELECTED_ROW_WIDTH, SELECTED_ROW_HEIGHT,
                    SELECTED_ROW_FRAME, SELECTED_ROW_FRAME, SELECTED_ROW_FRAME, SELECTED_ROW_FRAME);


    private List<VillagerAttributeFilterData.Attribute> attributes = List.of();
    private ItemStack lastReference = ItemStack.EMPTY;
    private int focusedIndex;
    private int scrollVisibilityTicks;
    private boolean inverted;
    private Button allowlistButton;
    private Button denylistButton;

    public VillagerAttributeFilterScreen(
            VillagerAttributeFilterMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
        this.imageWidth = TEXTURE_WIDTH;
        this.imageHeight = TEXTURE_HEIGHT;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.inverted = this.menu.configuration().inverted();
        this.allowlistButton = Button.builder(
                        Component.translatable("villagerretaliation.gui.item_filter.mode.allowlist"),
                        button -> setInverted(false))
                .bounds(this.leftPos + 7, this.topPos + 43, 80, 20)
                .tooltip(Tooltip.create(Component.translatable(
                        "villagerretaliation.gui.attribute_filter.mode.allowlist.description")))
                .build();
        this.denylistButton = Button.builder(
                        Component.translatable("villagerretaliation.gui.item_filter.mode.denylist"),
                        button -> setInverted(true))
                .bounds(this.leftPos + 89, this.topPos + 43, 80, 20)
                .tooltip(Tooltip.create(Component.translatable(
                        "villagerretaliation.gui.attribute_filter.mode.denylist.description")))
                .build();
        addRenderableWidget(this.allowlistButton);
        addRenderableWidget(this.denylistButton);
        refreshReference(true);
        refreshButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshReference(false);
        if (this.scrollVisibilityTicks > 0) {
            this.scrollVisibilityTicks--;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderAttributeSelector(graphics);
        int selectorOffset = selectorOffsetAt(mouseX, mouseY);
        if (selectorOffset == Integer.MIN_VALUE) {
            renderTooltip(graphics, mouseX, mouseY);
        } else {
            renderAttributeTooltip(graphics, mouseX, mouseY, selectorOffset);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
                VillagerRetaliationClientAssets.ATTRIBUTE_FILTER_CONTAINER_TEXTURE,
                this.leftPos,
                this.topPos,
                0,
                0,
                this.imageWidth,
                this.imageHeight,
                this.imageWidth,
                this.imageHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int offset = selectorOffsetAt(mouseX, mouseY);
            if (offset != Integer.MIN_VALUE) {
                int index = this.focusedIndex + offset;
                if (index < 0 || index >= this.attributes.size()) {
                    return true;
                }
                this.focusedIndex = index;
                this.scrollVisibilityTicks = 0;
                select(this.attributes.get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0 && selectorOffsetAt(mouseX, mouseY) != Integer.MIN_VALUE
                && this.attributes.size() > 1) {
            int nextIndex = Mth.clamp(
                    this.focusedIndex - (int) Math.signum(scrollY),
                    0,
                    this.attributes.size() - 1);
            this.scrollVisibilityTicks = SCROLL_VISIBILITY_TICKS;
            if (nextIndex != this.focusedIndex) {
                this.focusedIndex = nextIndex;
                select(this.attributes.get(nextIndex));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean isReferenceSlotAt(int mouseX, int mouseY) {
        var slot = this.menu.slots.get(VillagerAttributeFilterMenu.REFERENCE_SLOT);
        int slotX = this.leftPos + slot.x;
        int slotY = this.topPos + slot.y;
        return mouseX >= slotX && mouseX < slotX + 18
                && mouseY >= slotY && mouseY < slotY + 18;
    }

    private void renderAttributeSelector(GuiGraphics graphics) {
        boolean scrolling = this.scrollVisibilityTicks > 0;
        int selectorWidth = selectorWidth();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, SELECTOR_Z);
        try {
            if (scrolling) {
                renderScrollingRows(graphics, selectorWidth);
            }

            int rowX = this.leftPos + SELECTOR_X;
            int rowY = this.topPos + SELECTOR_Y;
            if (scrolling) {
                SELECTED_ROW_NINE_SLICE.renderAtTextureScale(
                        graphics,
                        rowX - SELECTED_ROW_FRAME,
                        rowY - SELECTED_ROW_FRAME,
                        selectorWidth + SELECTED_ROW_FRAME * 2,
                        SELECTED_ROW_HEIGHT);
            }
            if (this.attributes.isEmpty()) {
                graphics.drawString(
                        this.font,
                        Component.translatable("villagerretaliation.gui.attribute_filter.add_reference")
                                .withStyle(ChatFormatting.ITALIC),
                        rowX + 4,
                        rowY + 5,
                        0xFFFFFFFF,
                        true);
                return;
            }
            VillagerAttributeFilterData.Attribute focused = this.attributes.get(this.focusedIndex);
            if (scrolling) {
                drawAttribute(graphics, focused, rowX, rowY);
            } else {
                drawCollapsedAttribute(graphics, focused, rowX, rowY);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderScrollingRows(GuiGraphics graphics, int selectorWidth) {
        for (int offset = -NEIGHBOR_COUNT; offset <= NEIGHBOR_COUNT; offset++) {
            if (offset == 0) {
                continue;
            }
            int index = this.focusedIndex + offset;
            if (index < 0 || index >= this.attributes.size()) {
                continue;
            }
            int selectorX = this.leftPos + SELECTOR_X;
            int selectorY = selectorY(offset);
            SCROLL_ROW_NINE_SLICE.renderAtTextureScale(
                    graphics,
                    selectorX - SCROLL_ROW_FRAME,
                    selectorY - SCROLL_ROW_FRAME,
                    selectorWidth + SCROLL_ROW_FRAME * 2,
                    SCROLL_ROW_HEIGHT);
            drawAttribute(graphics, this.attributes.get(index), selectorX, selectorY);
        }
    }

    private int selectorY(int offset) {
        int centerY = this.topPos + SELECTOR_Y;
        if (offset == 0) {
            return centerY;
        }
        int distance = SELECTED_ROW_FRAME + SELECTOR_HEIGHT + SCROLL_ROW_FRAME - 1
                + (Math.abs(offset) - 1) * (SCROLL_ROW_HEIGHT - 1);
        return centerY + Integer.signum(offset) * distance;
    }

    private int selectorWidth() {
        int width = SELECTOR_WIDTH;
        for (VillagerAttributeFilterData.Attribute attribute : this.attributes) {
            width = Math.max(width, this.font.width(attribute.display()) + ATTRIBUTE_TEXT_PADDING);
        }
        return width;
    }

    private void drawAttribute(
            GuiGraphics graphics,
            VillagerAttributeFilterData.Attribute attribute,
            int x,
            int y) {
        graphics.drawString(
                this.font,
                attribute.display(),
                x + 4,
                y + 5,
                0xFFFFFFFF,
                true);
    }

    private void drawCollapsedAttribute(
            GuiGraphics graphics,
            VillagerAttributeFilterData.Attribute attribute,
            int x,
            int y) {
        graphics.drawString(
                this.font,
                trimmed(attribute.display(), SELECTOR_WIDTH - ATTRIBUTE_TEXT_PADDING),
                x + 4,
                y + 5,
                0xFFFFFFFF,
                true);
    }

    private void renderAttributeTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int offset) {
        int index = this.focusedIndex + offset;
        if (index < 0 || index >= this.attributes.size()) {
            return;
        }
        Component display = this.attributes.get(index).display();
        if (this.font.width(display) > selectorWidth() - ATTRIBUTE_TEXT_PADDING) {
            graphics.renderTooltip(this.font, display, mouseX, mouseY);
        }
    }

    private int selectorOffsetAt(double mouseX, double mouseY) {
        int selectorLeft = this.leftPos + SELECTOR_X;
        if (mouseX < selectorLeft || mouseX >= selectorLeft + selectorWidth()) {
            return Integer.MIN_VALUE;
        }

        int minOffset = this.scrollVisibilityTicks > 0 ? -NEIGHBOR_COUNT : 0;
        int maxOffset = this.scrollVisibilityTicks > 0 ? NEIGHBOR_COUNT : 0;
        for (int offset = minOffset; offset <= maxOffset; offset++) {
            int rowTop = selectorY(offset);
            if (mouseY >= rowTop && mouseY < rowTop + SELECTOR_HEIGHT) {
                return offset;
            }
        }
        return Integer.MIN_VALUE;
    }

    private void refreshReference(boolean force) {
        ItemStack reference = this.menu.referenceItem();
        if (!force && ItemStack.matches(reference, this.lastReference)) {
            return;
        }
        this.lastReference = reference.copy();
        this.attributes = VillagerAttributeFilterData.availableAttributes(reference, this.minecraft.level);
        VillagerAttributeFilterData.Attribute selected = this.menu.configuration().attribute();
        int selectedIndex = selected == null ? -1 : this.attributes.indexOf(selected);
        this.focusedIndex = selectedIndex >= 0 ? selectedIndex : 0;
        this.scrollVisibilityTicks = 0;
    }

    private void setInverted(boolean inverted) {
        this.inverted = inverted;
        VillagerAttributeFilterData.Attribute selected = this.menu.configuration().attribute();
        if (selected != null) {
            select(selected);
        }
        refreshButtons();
    }

    private void select(VillagerAttributeFilterData.Attribute attribute) {
        VillagerAttributeFilterData.Configuration current = this.menu.configuration();
        if (attribute.equals(current.attribute()) && this.inverted == current.inverted()) {
            return;
        }
        this.menu.setClientSelection(attribute, this.inverted);
        PacketDistributor.sendToServer(new AttributeFilterSelectPayload(
                attribute.type(), attribute.value(), this.inverted));
    }

    private void refreshButtons() {
        if (this.allowlistButton == null || this.denylistButton == null) {
            return;
        }
        this.allowlistButton.active = this.inverted;
        this.denylistButton.active = !this.inverted;
    }

    private Component trimmed(Component component, int width) {
        if (this.font.width(component) <= width) {
            return component;
        }
        return Component.literal(this.font.plainSubstrByWidth(component.getString(), width - 6) + "...");
    }
}
