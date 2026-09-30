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
        this.railSize = height < 240 ? 20 : 26;
        this.worldLeft = this.railSize + 16;
        this.dockedMap = sidebar && height >= 280;
        this.mapWidth = this.dockedMap ? Math.min(this.panelWidth - 38, height / 3) : Math.clamp(this.panelLeft / 3, 64, 112);
        this.mapHeight = this.mapWidth;
        this.mapLeft = this.dockedMap ? this.panelLeft + 30 : this.panelLeft - this.mapWidth - 6;
        this.mapTop = this.dockedMap ? height - this.mapHeight - 22 : HEADER_HEIGHT + 6;
        this.contentBottom = this.dockedMap && map ? this.mapTop - 24 : height - 28;
        this.commandBottom = height - 32;
        this.commandTop = this.commandBottom - 46;
    }

    int browserRows() { return Math.max(0, (this.contentBottom - 124) / 28); }
}
