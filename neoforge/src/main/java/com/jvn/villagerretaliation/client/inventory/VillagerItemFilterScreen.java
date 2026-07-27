package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterMenu;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.network.ItemFilterAmountChangePayload;
import com.jvn.villagerretaliation.network.ItemFilterModeChangePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerItemFilterScreen extends AbstractContainerScreen<VillagerItemFilterMenu> {
    private static final int TEXTURE_WIDTH = 176;
    private static final int TEXTURE_HEIGHT = 166;
    private static final int LIMIT_FEEDBACK_TICKS = 8;

    private final int[] limitFeedback = new int[VillagerItemFilterMenu.GHOST_SLOT_COUNT];
    private Button allowlistButton;
    private Button denylistButton;

    public VillagerItemFilterScreen(VillagerItemFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = TEXTURE_WIDTH;
        this.imageHeight = TEXTURE_HEIGHT;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.allowlistButton = Button.builder(
                        Component.translatable("villagerretaliation.gui.item_filter.mode.allowlist"),
                        button -> setMode(VillagerItemFilterData.Mode.ALLOWLIST))
                .bounds(this.leftPos + 8, this.topPos + 43, 78, 20)
                .tooltip(modeTooltip(
                        "villagerretaliation.gui.item_filter.mode.allowlist",
                        ChatFormatting.GREEN,
                        "villagerretaliation.gui.item_filter.mode.allowlist.description"))
                .build();
        this.denylistButton = Button.builder(
                        Component.translatable("villagerretaliation.gui.item_filter.mode.denylist"),
                        button -> setMode(VillagerItemFilterData.Mode.DENYLIST))
                .bounds(this.leftPos + 90, this.topPos + 43, 78, 20)
                .tooltip(modeTooltip(
                        "villagerretaliation.gui.item_filter.mode.denylist",
                        ChatFormatting.RED,
                        "villagerretaliation.gui.item_filter.mode.denylist.description"))
                .build();
        addRenderableWidget(this.allowlistButton);
        addRenderableWidget(this.denylistButton);
        refreshButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (int slot = 0; slot < this.limitFeedback.length; slot++) {
            if (this.limitFeedback[slot] > 0) {
                this.limitFeedback[slot]--;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
                VillagerRetaliationClientAssets.ITEM_FILTER_CONTAINER_TEXTURE,
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int slot = ghostSlotAt((int) mouseX, (int) mouseY);
        if (scrollY != 0.0
                && slot >= 0
                && this.menu.mode() == VillagerItemFilterData.Mode.ALLOWLIST
                && this.menu.isAmountEntry(slot)) {
            int step = hasControlDown() && hasShiftDown()
                    ? 100
                    : hasControlDown() ? 10 : hasShiftDown() ? 5 : 1;
            int delta = scrollY > 0.0 ? step : -step;
            VillagerItemFilterData.AmountAdjustment adjustment =
                    this.menu.adjustEntryAmount(slot, delta);
            if (adjustment.valid()) {
                if (adjustment.hitLimit()) {
                    this.limitFeedback[slot] = LIMIT_FEEDBACK_TICKS;
                }
                if (adjustment.changed()) {
                    PacketDistributor.sendToServer(new ItemFilterAmountChangePayload(slot, delta));
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (this.hoveredSlot == null
                || this.hoveredSlot.index < 0
                || this.hoveredSlot.index >= VillagerItemFilterMenu.GHOST_SLOT_COUNT
                || !this.menu.isAmountEntry(this.hoveredSlot.index)) {
            return tooltip;
        }

        int slot = this.hoveredSlot.index;
        int amount = this.menu.amount(slot);
        Component value = amount == VillagerItemFilterData.UNLIMITED_AMOUNT
                ? Component.translatable("villagerretaliation.gui.item_filter.amount.unlimited")
                : Component.literal(VillagerItemFilterData.formatAmount(amount));
        String limitKey = this.menu.identityEntryCount(slot) > 1
                ? "villagerretaliation.gui.item_filter.amount.entry_limit"
                : "villagerretaliation.gui.item_filter.amount.stock_limit";
        tooltip.add(Component.translatable(limitKey, value).withStyle(ChatFormatting.GRAY));
        if (this.menu.identityEntryCount(slot) > 1) {
            tooltip.add(Component.translatable(
                    "villagerretaliation.gui.item_filter.amount.combined_limit",
                    VillagerItemFilterData.formatAmount(this.menu.combinedAmount(slot)))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (this.menu.mode() == VillagerItemFilterData.Mode.ALLOWLIST) {
            tooltip.add(Component.translatable(
                    "villagerretaliation.gui.item_filter.amount.scroll")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                    "villagerretaliation.gui.item_filter.amount.scroll_shift")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                    "villagerretaliation.gui.item_filter.amount.scroll_control")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                    "villagerretaliation.gui.item_filter.amount.scroll_control_shift")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (this.menu.mode() == VillagerItemFilterData.Mode.DENYLIST) {
            tooltip.add(Component.translatable(
                    "villagerretaliation.gui.item_filter.amount.inactive_denylist")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return tooltip;
    }

    @Override
    protected void renderSlotContents(
            GuiGraphics graphics,
            ItemStack stack,
            Slot slot,
            String countString) {
        super.renderSlotContents(graphics, stack, slot, countString);
        if (slot.index < 0
                || slot.index >= VillagerItemFilterMenu.GHOST_SLOT_COUNT
                || !this.menu.isAmountEntry(slot.index)) {
            return;
        }

        int amount = this.menu.amount(slot.index);
        if (amount == VillagerItemFilterData.UNLIMITED_AMOUNT) {
            return;
        }

        String label = VillagerItemFilterData.formatAmount(amount);
        int x = slot.x + 17 - this.font.width(label);
        int color = this.menu.mode() == VillagerItemFilterData.Mode.ALLOWLIST
                ? 0xFFFFFFFF
                : 0xFFAAAAAA;
        if (this.limitFeedback[slot.index] > 0) {
            color = 0xFFFF7777;
            int phase = this.limitFeedback[slot.index] & 3;
            x += phase == 0 ? -1 : phase == 2 ? 1 : 0;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.drawString(this.font, label, x, slot.y + 9, color, true);
        graphics.pose().popPose();
    }

    private static Tooltip modeTooltip(
            String titleKey,
            ChatFormatting titleColor,
            String descriptionKey) {
        Component message = Component.empty()
                .append(Component.translatable(titleKey).withStyle(titleColor))
                .append("\n")
                .append(Component.translatable(descriptionKey));
        return Tooltip.create(message);
    }

    private void setMode(VillagerItemFilterData.Mode mode) {
        if (this.menu.mode() == mode) {
            return;
        }
        this.menu.setClientMode(mode);
        PacketDistributor.sendToServer(new ItemFilterModeChangePayload(-1, mode));
        refreshButtons();
    }

    private void refreshButtons() {
        if (this.allowlistButton == null || this.denylistButton == null) {
            return;
        }
        VillagerItemFilterData.Mode mode = this.menu.mode();
        this.allowlistButton.active = mode != VillagerItemFilterData.Mode.ALLOWLIST;
        this.denylistButton.active = mode != VillagerItemFilterData.Mode.DENYLIST;
    }

    /**
     * Returns the ghost-slot index at the given screen coordinates, or {@code -1} when none is hit.
     */
    public int ghostSlotAt(int mouseX, int mouseY) {
        for (int slot = 0; slot < VillagerItemFilterMenu.GHOST_SLOT_COUNT; slot++) {
            var ghostSlot = this.menu.slots.get(slot);
            int slotX = this.leftPos + ghostSlot.x;
            int slotY = this.topPos + ghostSlot.y;
            if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                return slot;
            }
        }
        return -1;
    }
}
