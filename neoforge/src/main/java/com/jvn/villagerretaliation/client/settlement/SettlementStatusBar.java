package com.jvn.villagerretaliation.client.settlement;

import com.jvn.toucanlib.client.ToucanHudText;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.util.VillagerDurationFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class SettlementStatusBar {
    private static final int WIDTH = 476;
    private static final int HEIGHT = 32;
    private static final int ICON_SIZE = 16;
    private static final int ENTRY_PADDING = 6;
    private static final List<ResourceLocation> ICONS = List.of(
            VillagerRetaliationClientAssets.SETTLEMENT_STATUS_BAR_RESIDENTS_TEXTURE,
            VillagerRetaliationClientAssets.SETTLEMENT_STATUS_BAR_HOUSING_TEXTURE,
            VillagerRetaliationClientAssets.SETTLEMENT_STATUS_BAR_FOOD_TEXTURE,
            VillagerRetaliationClientAssets.SETTLEMENT_STATUS_BAR_FUNDS_TEXTURE);

    private SettlementStatusBar() { }

    static Layout layout(Font font, SettlementManagementPayload.Header header, long dayTime, int screenWidth) {
        String subtitle = "Day " + (Math.floorDiv(dayTime, 24_000L) + 1) + "  "
                + SettlementManagementScreen.enumText("stage", header.stage()).getString();
        var values = List.of(header.population() + "/" + header.growthTarget(), Integer.toString(header.housing()),
                VillagerDurationFormat.formatGameDays(header.foodDays()), funds(header.balance()));
        int width = Math.min(WIDTH, screenWidth);
        int fixedWidth = ICON_SIZE + 4 + ENTRY_PADDING * 2;
        int textSpace = Math.max(0, width - 96 - 3 - 18 - values.size() * fixedWidth);
        int totalTextWidth = values.stream().mapToInt(font::width).sum();
        var metricWidths = values.stream().map(value -> {
            int textWidth = font.width(value);
            if (totalTextWidth > textSpace) textWidth = (int) ((long) textWidth * textSpace / totalTextWidth);
            return fixedWidth + font.width(font.plainSubstrByWidth(value, textWidth));
        }).toList();
        int divider = width - 3 - 18 - metricWidths.stream().mapToInt(Integer::intValue).sum();
        return new Layout((screenWidth - width) / 2, width, divider, header.name(), subtitle, values, metricWidths);
    }

    static void render(GuiGraphics graphics, Font font, Layout layout, int mouseX, int mouseY, int highlightedEntry) {
        int divider = layout.boundary(layout.divider());
        graphics.enableScissor(layout.left(), 1, layout.left() + layout.width(), HEIGHT + 1);
        // draw each native background once and mask it at the shared divider
        graphics.enableScissor(layout.left(), 1, divider, HEIGHT + 1);
        graphics.blit(VillagerRetaliationClientAssets.SETTLEMENT_STATUS_BAR_BG_LEFT_TEXTURE,
                layout.left(), 1, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        graphics.disableScissor();
        graphics.enableScissor(divider, 1, layout.left() + layout.width(), HEIGHT + 1);
        graphics.blit(VillagerRetaliationClientAssets.SETTLEMENT_STATUS_BAR_BG_RIGHT_TEXTURE,
                layout.left(), 1, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        graphics.disableScissor();
        graphics.pose().pushPose();
        graphics.pose().translate(layout.left(), 1, 0);
        int textTop = (HEIGHT - font.lineHeight * 2 - 1) / 2 + 2;
        int nameWidth = Math.max(0, layout.divider() - 32);
        ToucanHudText.drawOutlinedString(graphics, font, font.plainSubstrByWidth(layout.name(), nameWidth),
                24, textTop, 0xFFFFFF, 0xFF000000);
        ToucanHudText.drawOutlinedString(graphics, font, font.plainSubstrByWidth(layout.subtitle(), nameWidth),
                24, textTop + font.lineHeight + 1, 0xFFFFFF, 0xFF000000);
        for (int index = 0; index < ICONS.size(); index++) {
            int left = layout.metricLeft(index) + ENTRY_PADDING;
            graphics.blit(ICONS.get(index), left, (HEIGHT - ICON_SIZE) / 2, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            int textWidth = Math.max(0, layout.metricLeft(index + 1) - left - ICON_SIZE - 4 - ENTRY_PADDING);
            ToucanHudText.drawOutlinedString(graphics, font, font.plainSubstrByWidth(layout.values().get(index), textWidth),
                    left + ICON_SIZE + 4, (HEIGHT - font.lineHeight) / 2 + 2, 0xFFFFFF, 0xFF000000);
        }
        graphics.pose().popPose();
        if (highlightedEntry >= 0) {
            int left = highlightedEntry == 0 ? layout.left() + 4 : layout.boundary(layout.metricLeft(highlightedEntry - 1));
            int right = highlightedEntry == 0 ? divider : layout.boundary(layout.metricLeft(highlightedEntry));
            graphics.fill(left + 1, 5, right - 1, 29, 0x20FFFFFF);
        }
        graphics.blit(VillagerRetaliationClientAssets.SETTLEMENT_STATUS_BAR_CONTAINER_TEXTURE,
                layout.left(), 1, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        graphics.blit(VillagerRetaliationClientAssets.SETTLEMENT_STATUS_BAR_DIVIDER_TEXTURE,
                divider, 4, 0, 0, 3, 26, 3, 26);
        graphics.disableScissor();
        if (mouseY >= 1 && mouseY < HEIGHT + 1 && mouseX >= layout.left() && mouseX < layout.left() + layout.width()) {
            Component tooltip = Component.literal(layout.name() + "\n" + layout.subtitle());
            var labels = List.of("Residents", "Beds", "Food supply", "Treasury");
            for (int index = 0; index < ICONS.size(); index++) {
                if (mouseX >= layout.boundary(layout.metricLeft(index))
                        && mouseX < layout.boundary(layout.metricLeft(index + 1))) {
                    tooltip = Component.literal(labels.get(index) + ": " + layout.values().get(index));
                }
            }
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    static String funds(CurrencyAmount amount) {
        BigDecimal value = new BigDecimal(amount.numerator()).divide(new BigDecimal(amount.denominator()), 2, RoundingMode.HALF_UP);
        String[] suffixes = {"", "K", "M", "B", "T"};
        int unit = 0;
        while (value.compareTo(BigDecimal.valueOf(1000)) >= 0 && unit < suffixes.length - 1) {
            value = value.movePointLeft(3);
            unit++;
        }
        value = value.setScale(unit == 0 ? 2 : 1, RoundingMode.HALF_UP);
        if (value.compareTo(BigDecimal.valueOf(1000)) >= 0 && unit < suffixes.length - 1) {
            value = value.movePointLeft(3).setScale(1, RoundingMode.HALF_UP);
            unit++;
        }
        return value.stripTrailingZeros().toPlainString() + suffixes[unit];
    }

    record Layout(int left, int width, int divider, String name, String subtitle, List<String> values, List<Integer> metricWidths) {
        int metricLeft(int index) {
            int position = divider + 3;
            for (int metric = 0; metric < index; metric++) position += metricWidths.get(metric);
            return position;
        }

        int boundary(int x) {
            return left + x;
        }
    }
}
