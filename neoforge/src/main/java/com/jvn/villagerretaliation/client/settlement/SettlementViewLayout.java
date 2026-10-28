package com.jvn.villagerretaliation.client.settlement;

final class SettlementViewLayout {
    static final int HEADER_HEIGHT = 34;
    final int panelWidth;
    final int panelLeft;
    final int railSize;
    final int worldLeft;
    final int contentBottom;
    final int mapLeft;
    final int mapTop;
    final int mapWidth;
    final int mapHeight;
    final boolean dockedMap;
    final int commandBottom;
    final int commandTop;

    SettlementViewLayout(int width, int height, boolean sidebar, boolean map) {
        this.panelWidth = Math.min(width - 112, Math.clamp(width / 4, 144, 224));
        this.panelLeft = width - (sidebar ? this.panelWidth : 24);
        this.railSize = height < 240 ? Math.clamp((height - HEADER_HEIGHT - 12) / 7 - 2, 12, 20) : 26;
        this.worldLeft = this.railSize + 16;
        this.dockedMap = sidebar && height >= 280;
        this.mapWidth = this.dockedMap ? Math.min(this.panelWidth - 38, height / 3) : Math.clamp(this.panelLeft / 3, 64, 112);
        this.mapHeight = this.mapWidth;
        this.mapLeft = this.dockedMap ? this.panelLeft + 30 : this.panelLeft - this.mapWidth - 6;
        this.mapTop = this.dockedMap ? height - this.mapHeight - 30 : HEADER_HEIGHT + 6;
        this.contentBottom = this.dockedMap && map ? this.mapTop - 24 : height - 28;
        this.commandBottom = height - 32;
        this.commandTop = this.commandBottom - 46;
    }

    SettlementViewLayout(int width, int height, boolean sidebar, boolean map,
                         SettlementViewPreferences.WindowPosition sidebarWindow, SettlementViewPreferences.WindowPosition mapWindow) {
        var defaults = new SettlementViewLayout(width, height, sidebar, map);
        this.panelWidth = sidebarWindow != null && sidebarWindow.width() > 0
                ? SettlementUiWindow.clampSize(sidebarWindow.width(), 140, width - 4) + 4 : defaults.panelWidth;
        this.panelLeft = defaults.panelLeft;
        this.railSize = defaults.railSize;
        this.worldLeft = defaults.worldLeft;
        this.contentBottom = sidebarWindow != null && sidebarWindow.height() > 0
                ? HEADER_HEIGHT + SettlementUiWindow.clampSize(sidebarWindow.height(), 140, height - HEADER_HEIGHT - 4)
                : defaults.contentBottom;
        this.mapWidth = mapWindow != null && mapWindow.width() > 0
                ? SettlementUiWindow.clampSize(mapWindow.width() - 4, 64, width - 8) : defaults.mapWidth;
        this.mapHeight = mapWindow != null && mapWindow.height() > 0
                ? SettlementUiWindow.clampSize(mapWindow.height() - SettlementUiWindow.TITLE_HEIGHT - 4, 64,
                        height - HEADER_HEIGHT - SettlementUiWindow.TITLE_HEIGHT - 8) : defaults.mapHeight;
        this.mapLeft = defaults.mapLeft;
        this.mapTop = defaults.mapTop;
        this.dockedMap = defaults.dockedMap;
        this.commandBottom = defaults.commandBottom;
        this.commandTop = defaults.commandTop;
    }

    int browserRows() { return Math.max(0, (this.contentBottom - 138) / 28); }
}
