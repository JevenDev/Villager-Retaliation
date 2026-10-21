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
            require(layout.width() == Math.min(width, 476), "the supplied bar never extends beyond its native width");
            require(layout.left() == (width - layout.width()) / 2, "the native bar is centered");
            require(layout.subtitle().startsWith("Day 1  "), "day and level share the second line");
            require(layout.divider() >= 96, "the name panel retains space without scaling its contents");
            require(layout.boundary(layout.metricLeft(4)) <= layout.left() + layout.width() - 1, "metrics stay inside the container");
            for (int index = 0; index < 4; index++) {
                int cell = layout.metricLeft(index + 1) - layout.metricLeft(index);
                require(cell >= 32, "native icons retain equal padding inside each metric group");
                if (width >= 476) {
                    require(cell == 32 + font.width(layout.values().get(index)), "group widths follow their own text");
                    if (index > 0) {
                        int previousEnd = layout.metricLeft(index - 1) + 6 + 16 + 4 + font.width(layout.values().get(index - 1));
                        require(layout.metricLeft(index) + 6 - previousEnd == 12, "content has the same gap between every group");
                    }
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
