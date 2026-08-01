package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.block.SellBoxMenu;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class SellBoxScreen extends AbstractContainerScreen<SellBoxMenu> {
    private static final ResourceLocation CONTAINER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "villagerretaliation", "textures/gui/container/sell_box.png");
    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;
    private static final int BUTTON_Y = 21;
    private static final int BUTTON_WIDTH = 68;
    private static final int BUTTON_HEIGHT = 18;
    private static final int CURRENCY_ICON_X = 8;
    private static final int CURRENCY_ICON_SIZE = 16;
    private static final int CURRENCY_TEXT_X = CURRENCY_ICON_X + CURRENCY_ICON_SIZE + 3;
    private static final int PENDING_ROW_Y = 43;
    private static final int BALANCE_ROW_Y = 63;
    private static final int VALUE_TEXT_Y_OFFSET = 4;
    private static final int VALUE_TEXT_COLOR = 0xFF404040;
    private Button sellButton;
    private Button withdrawButton;

    public SellBoxScreen(SellBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.sellButton = addRenderableWidget(Button.builder(
                        Component.translatable("villagerretaliation.sell_box.sell"),
                        ignored -> clickButton(SellBoxMenu.SELL_BUTTON))
                .bounds(
                        this.leftPos + 7,
                        this.topPos + BUTTON_Y,
                        BUTTON_WIDTH,
                        BUTTON_HEIGHT)
                .build());
        this.withdrawButton = addRenderableWidget(Button.builder(
                        Component.translatable("villagerretaliation.sell_box.withdraw"),
                        ignored -> clickButton(SellBoxMenu.COLLECT_BUTTON))
                .bounds(
                        this.leftPos + 101,
                        this.topPos + BUTTON_Y,
                        BUTTON_WIDTH,
                        BUTTON_HEIGHT)
                .build());
        refreshButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderValueTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(CONTAINER_TEXTURE, leftPos, topPos, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        SellBoxClientState.Snapshot state = SellBoxClientState.snapshot(menu.containerId);
        Component marketTitle = state.validMarket()
                ? Component.translatable("villagerretaliation.sell_box.market_title", state.villageName())
                : Component.translatable("villagerretaliation.sell_box.no_market");
        graphics.drawString(
                font,
                marketTitle,
                titleLabelX - 1,
                titleLabelY,
                VALUE_TEXT_COLOR,
                false);
        CurrencyAmount pendingValue = pendingValue(state);
        renderCurrencyIcon(graphics, state, PENDING_ROW_Y);
        renderCurrencyIcon(graphics, state, BALANCE_ROW_Y);
        graphics.drawString(
                font,
                Component.translatable(
                        "villagerretaliation.sell_box.pending",
                        SellBoxClientState.compactCurrency(pendingValue, state)),
                CURRENCY_TEXT_X,
                PENDING_ROW_Y + VALUE_TEXT_Y_OFFSET,
                VALUE_TEXT_COLOR,
                false);
        graphics.drawString(
                font,
                Component.translatable(
                        "villagerretaliation.sell_box.balance",
                        SellBoxClientState.compactCurrency(state.balance(), state)),
                CURRENCY_TEXT_X,
                BALANCE_ROW_Y + VALUE_TEXT_Y_OFFSET,
                VALUE_TEXT_COLOR,
                false);
    }

    private void renderCurrencyIcon(
            GuiGraphics graphics,
            SellBoxClientState.Snapshot state,
            int rowY) {
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(state.currencyIconSprite());
        graphics.blit(
                CURRENCY_ICON_X,
                rowY,
                0,
                CURRENCY_ICON_SIZE,
                CURRENCY_ICON_SIZE,
                sprite);
    }

    private void renderValueTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int localMouseX = mouseX - leftPos;
        int localMouseY = mouseY - topPos;
        SellBoxClientState.Snapshot state = SellBoxClientState.snapshot(menu.containerId);
        CurrencyAmount amount;
        String titleKey;
        String detailKey;
        if (isValueRowHovered(localMouseX, localMouseY, PENDING_ROW_Y)) {
            amount = pendingValue(state);
            titleKey = "villagerretaliation.sell_box.pending_tooltip.title";
            detailKey = "villagerretaliation.sell_box.pending_tooltip.detail";
        } else if (isValueRowHovered(localMouseX, localMouseY, BALANCE_ROW_Y)) {
            amount = state.balance();
            titleKey = "villagerretaliation.sell_box.balance_tooltip.title";
            detailKey = "villagerretaliation.sell_box.balance_tooltip.detail";
        } else {
            return;
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(titleKey).withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable(detailKey).withStyle(ChatFormatting.GRAY));
        if (isValueRowHovered(localMouseX, localMouseY, PENDING_ROW_Y)) {
            ItemStack pending = menu.getSlot(0).getItem();
            var entry = pending.isEmpty()
                    ? null
                    : state.entries().get(BuiltInRegistries.ITEM.getKey(pending.getItem()));
            if (entry != null) {
                lines.add(Component.translatable(
                        "villagerretaliation.sell_box.current_rate",
                        rateText(pending, entry, state)).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(
                        "villagerretaliation.sell_box.daily_demand",
                        titleCase(entry.demandBand().name())).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(
                        "villagerretaliation.sell_box.recent_supply",
                        titleCase(entry.supplyBand().name())).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(
                        "villagerretaliation.sell_box.market_group",
                        entry.marketGroup().toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (!amount.isExactlyRepresentable(2)) {
            lines.add(Component.translatable(
                    "villagerretaliation.sell_box.exact",
                    SellBoxClientState.exactCurrency(amount, state)).withStyle(ChatFormatting.GRAY));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private boolean isValueRowHovered(int mouseX, int mouseY, int rowY) {
        return mouseX >= CURRENCY_ICON_X
                && mouseX < WIDTH - 8
                && mouseY >= rowY
                && mouseY < rowY + CURRENCY_ICON_SIZE;
    }

    private CurrencyAmount pendingValue(SellBoxClientState.Snapshot state) {
        ItemStack pending = menu.getSlot(0).getItem();
        if (pending.isEmpty()) {
            return CurrencyAmount.ZERO;
        }
        var entry = state.entries().get(BuiltInRegistries.ITEM.getKey(pending.getItem()));
        return SellBoxClientState.payout(entry, pending.getCount());
    }

    private String rateText(
            ItemStack pending,
            com.jvn.villagerretaliation.network.SellBoxSyncPayload.MarketEntry entry,
            SellBoxClientState.Snapshot state) {
        CurrencyAmount rate = entry.effectiveUnitPrice();
        if (rate.numerator().bitLength() < 31 && rate.denominator().bitLength() < 31) {
            return rate.denominator() + " " + pending.getHoverName().getString()
                    + " -> " + rate.numerator() + " "
                    + (rate.numerator().equals(java.math.BigInteger.ONE)
                            ? state.currencyName()
                            : state.currencyPluralName());
        }
        return "~" + CurrencyAmount.of(1, 1).multiply(rate).decimal(3)
                + " " + state.currencyPluralName() + " each";
    }

    private static String titleCase(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return normalized.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + normalized.substring(1);
    }

    private void clickButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void refreshButtons() {
        if (sellButton == null || withdrawButton == null) {
            return;
        }
        SellBoxClientState.Snapshot state = SellBoxClientState.snapshot(menu.containerId);
        sellButton.active = state.validMarket() && menu.getSlot(0).hasItem();
        withdrawButton.active = state.balance().wholeUnits().signum() > 0;
    }
}