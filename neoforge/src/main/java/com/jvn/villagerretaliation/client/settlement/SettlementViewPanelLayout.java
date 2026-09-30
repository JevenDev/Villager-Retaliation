package com.jvn.villagerretaliation.client.settlement;

public record SettlementViewPanelLayout(int left, int top, int width, int height) {
    static SettlementViewPanelLayout create(int width, int height, int preferredWidth, int preferredHeight, boolean connected, boolean compact) {
        if (!connected || height < 280) return new SettlementViewPanelLayout((width - preferredWidth) / 2, (height - preferredHeight) / 2,
                preferredWidth, preferredHeight);
        int panelWidth = compact && width >= 480 ? Math.min(preferredWidth, Math.clamp(width / 2, 260, 360)) : preferredWidth;
        int panelHeight = Math.min(preferredHeight, height - SettlementViewLayout.HEADER_HEIGHT - 28);
        int left = compact && width >= 480 ? width - panelWidth - 12 : (width - panelWidth) / 2;
        int top = SettlementViewLayout.HEADER_HEIGHT + 12 + Math.max(0, (height - SettlementViewLayout.HEADER_HEIGHT - 28 - panelHeight) / 2);
        return new SettlementViewPanelLayout(left, top, panelWidth, panelHeight);
    }
}
