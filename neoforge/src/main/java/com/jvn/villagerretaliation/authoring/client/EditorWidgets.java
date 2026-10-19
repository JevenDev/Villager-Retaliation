package com.jvn.villagerretaliation.authoring.client;

import imgui.ImGui;

final class EditorWidgets {
    private EditorWidgets() { }

    static float buttonWidth(String label) {
        return ImGui.calcTextSize(label.split("##", 2)[0]).x + ImGui.getStyle().getFramePaddingX() * 2;
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

    static String field(String label) {
        ImGui.textWrapped(label.split("##", 2)[0]);
        ImGui.setNextItemWidth(-1);
        return "##" + label;
    }

    static boolean section(String label) { return section(label, 0); }

    static boolean section(String label, int flags) {
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY(), width = ImGui.getContentRegionAvailX();
        ImGui.pushClipRect(x, y, x + width, y + ImGui.getFrameHeight(), true);
        boolean open = ImGui.collapsingHeader(label, flags);
        ImGui.popClipRect();
        String title = label.split("##", 2)[0];
        if (ImGui.isItemHovered() && ImGui.calcTextSize(title).x + ImGui.getFontSize() + ImGui.getStyle().getFramePaddingX() * 3 > width)
            ImGui.setTooltip(title);
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
        ImGui.setWindowFontScale(1);
        return true;
    }
}
