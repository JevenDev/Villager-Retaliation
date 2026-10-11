package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout;
import com.jvn.villagerretaliation.inventory.SmithRepairMenu;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CyclingSlotBackground;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class SmithRepairScreen extends AbstractContainerScreen<SmithRepairMenu> implements VillagerTradeScreenLayout.Host {
    private static final ResourceLocation TEXTURE = VillagerRetaliation.id("textures/gui/container/smithing.png");
    private static final List<ResourceLocation> MATERIAL_ICONS = List.of(ResourceLocation.withDefaultNamespace("item/empty_slot_ingot"));
    private final CyclingSlotBackground materialIcon = new CyclingSlotBackground(1);
    private final VillagerTradeScreenLayout layout;
    private Button repair;

    public SmithRepairScreen(SmithRepairMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 187;
        titleLabelX = 60;
        titleLabelY = 18;
        inventoryLabelY = 93;
        layout = new VillagerTradeScreenLayout(menu.entityId());
    }

    @Override
    public VillagerTradeScreenLayout villagerretaliation$tradeLayout() { return layout; }

    @Override
    protected void init() {
        super.init();
        repair = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
        }).bounds(leftPos + 26, topPos + 68, 125, 20).build());
        layout.update(this);
        updateButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        materialIcon.tick(MATERIAL_ICONS);
    }

    private void updateButton() {
        repair.active = menu.status() == SmithRepairMenu.Status.READY;
        repair.setMessage(Component.translatable("villagerretaliation.gui.repair.button", menu.cost()));
        repair.setTooltip(Tooltip.create(Component.translatable("villagerretaliation.gui.repair.status."
                + menu.status().name().toLowerCase(Locale.ROOT))));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout.update(this);
        updateButton();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int labelWidth = font.width(repair.getMessage());
        int iconX = repair.getX() + (repair.getWidth() - labelWidth) / 2 + 1;
        var sprite = minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(menu.currencyIcon());
        graphics.blit(iconX, repair.getY() + 2, 0, 16, 16, sprite);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected java.util.List<Component> getTooltipFromContainerItem(net.minecraft.world.item.ItemStack stack) {
        var lines = new java.util.ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot != null && hoveredSlot.index == 2) {
            lines.add(Component.translatable("villagerretaliation.gui.repair.preview").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return lines;
    }

    @Override
    public void renderTransparentBackground(GuiGraphics graphics) {
        if (!layout.split()) super.renderTransparentBackground(graphics);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, 67);
        graphics.blit(TEXTURE, leftPos, topPos + 67, imageWidth, 21, 0, 67, imageWidth, 1, 256, 256);
        graphics.blit(TEXTURE, leftPos, topPos + 88, 0, 67, imageWidth, 99);
        materialIcon.render(menu, graphics, partialTick, leftPos, topPos);
    }
}
