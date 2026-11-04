package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.util.VillagerDurationFormat;
import net.minecraft.client.Minecraft;

final class SettlementStatusBarClientChecks {
    private SettlementStatusBarClientChecks() { }

    static void verify(SettlementManagementPayload.Header header) {
        require(VillagerDurationFormat.formatGameDays(12).equals("1W, 5D"), "food uses weeks and remaining days");
        require(VillagerDurationFormat.formatGameDays(7).equals("1W"), "whole weeks omit zero days");
        require(VillagerDurationFormat.formatGameDays(0.5).equals("12H"), "partial days retain useful precision");
        require(VillagerDurationFormat.formatGameDays(0).equals("0D"), "empty food reserves show zero days");
        require(VillagerDurationFormat.formatGameDays(-1).equals("0D"), "negative durations clamp to zero");
        require(VillagerDurationFormat.formatGameDays(365 + 30 + 7).equals("1Y, 1MO"), "long durations show the two largest units");
        require(SettlementStatusBar.funds(CurrencyAmount.of(1200, 1)).equals("1.2K"), "funds use compact thousands");
        require(SettlementStatusBar.funds(CurrencyAmount.of(999_999, 1)).equals("1M"), "rounded thousands carry into millions");
        require(SettlementStatusBar.funds(CurrencyAmount.of(1, 4)).equals("0.25"), "small balances retain fractions");
        var font = Minecraft.getInstance().font;
        for (int width : new int[]{320, 480, 640, 960}) {
            var layout = SettlementStatusBar.layout(font, header, 0, width);
            require(layout.width() > 0 && layout.left() >= 0 && layout.left() + layout.width() <= width,
                    "the status bar remains inside the screen");
            require(layout.name().equals(header.name()), "the settlement name remains visible");
            require(layout.subtitle().startsWith("Day 1  "), "day and level share the second line");
            require(layout.divider() > 0 && layout.divider() < layout.width(), "the name and metrics both have visible space");
            require(layout.values().stream().noneMatch(String::isBlank), "each settlement metric has a value");
            for (int index = 0; index < layout.values().size(); index++) {
                int left = layout.boundary(layout.metricLeft(index));
                int right = layout.boundary(layout.metricLeft(index + 1));
                require(left >= layout.left() && right > left && right <= layout.left() + layout.width(),
                        "metric groups remain visible inside the container");
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
