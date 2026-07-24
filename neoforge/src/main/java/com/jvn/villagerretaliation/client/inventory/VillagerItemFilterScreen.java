package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterMenu;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.network.ItemFilterModeChangePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerItemFilterScreen extends AbstractContainerScreen<VillagerItemFilterMenu> {
    private static final int TEXTURE_WIDTH = 176;
    private static final int TEXTURE_HEIGHT = 166;

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
                .tooltip(Tooltip.create(Component.translatable(
                        "villagerretaliation.gui.item_filter.mode.allowlist.description")))
                .build();
        this.denylistButton = Button.builder(
                        Component.translatable("villagerretaliation.gui.item_filter.mode.denylist"),
                        button -> setMode(VillagerItemFilterData.Mode.DENYLIST))
                .bounds(this.leftPos + 90, this.topPos + 43, 78, 20)
                .tooltip(Tooltip.create(Component.translatable(
                        "villagerretaliation.gui.item_filter.mode.denylist.description")))
                .build();
        addRenderableWidget(this.allowlistButton);
        addRenderableWidget(this.denylistButton);
        refreshButtons();
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
     * Kept independent of EMI so this screen can still load when EMI is absent.
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
