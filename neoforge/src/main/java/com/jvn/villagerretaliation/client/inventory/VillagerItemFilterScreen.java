package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.inventory.VillagerItemFilterMenu;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.network.ItemFilterModeChangePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerItemFilterScreen extends AbstractContainerScreen<VillagerItemFilterMenu> {
    private static final ResourceLocation CONTAINER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private Button allowlistButton;
    private Button denylistButton;

    public VillagerItemFilterScreen(VillagerItemFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
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
        graphics.blit(CONTAINER_TEXTURE, this.leftPos, this.topPos, 0, 0, 176, 71, 256, 256);
        graphics.blit(CONTAINER_TEXTURE, this.leftPos, this.topPos + 70, 0, 126, 176, 96, 256, 256);
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
}
