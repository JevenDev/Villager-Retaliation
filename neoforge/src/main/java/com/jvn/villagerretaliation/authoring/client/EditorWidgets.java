package com.jvn.villagerretaliation.authoring.client;

import imgui.ImGui;

final class EditorWidgets {
    private EditorWidgets() { }

    static float buttonWidth(String label) {
        return ImGui.calcTextSize(label).x + ImGui.getStyle().getFramePaddingX() * 2;
    }

    static float inputWidth(String button) {
        float available = ImGui.getContentRegionAvailX();
        float width = available - buttonWidth(button) - ImGui.getStyle().getItemSpacingX();
        return Math.max(1, width >= ImGui.getFontSize() * 4 ? width : available);
    }

    static void sameLineFor(String label) { sameLineFor(buttonWidth(label)); }

    static void sameLineFor(float width) {
        float right = ImGui.getCursorScreenPosX() + ImGui.getContentRegionAvailX();
        if (ImGui.getItemRectMaxX() + ImGui.getStyle().getItemSpacingX() + width <= right + .5f) ImGui.sameLine();
    }

    static boolean section(String label) {
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        ImGui.pushClipRect(x, y, x + ImGui.getContentRegionAvailX(), y + ImGui.getFrameHeight(), true);
        boolean open = ImGui.collapsingHeader(label);
        ImGui.popClipRect();
        return open;
    }

    static boolean treeSection(String label) {
        boolean open = section(label);
        if (open) ImGui.treePush(label);
        return open;
    }

    static boolean beginPopup(String title, float width, float height, float textScale) {
        var viewport = ImGui.getMainViewport();
        width = Math.max(1, Math.min(width * textScale, viewport.getWorkSizeX() - 28));
        height = Math.max(1, Math.min(height * textScale, viewport.getWorkSizeY() - 28));
        ImGui.setNextWindowSize(width, height);
        ImGui.setNextWindowPos(viewport.getWorkPosX() + (viewport.getWorkSizeX() - width) / 2,
                viewport.getWorkPosY() + (viewport.getWorkSizeY() - height) / 2);
        if (!ImGui.beginPopup(title)) return false;
        ImGui.setWindowFontScale(textScale);
        return true;
    }
}
