package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

final class EditorExplorer {
    private EditorExplorer() { }

    private enum Icon {
        FOLDER("folder"), OPEN_FOLDER("folder-open"), FILE("file-text"), DIALOGUE("message-square"),
        REVEAL("locate-fixed"), COLLAPSE("copy-minus"), OPTIONS("ellipsis");
        private final ResourceLocation resource;
        Icon(String name) { resource = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/authoring/lucide/" + name + ".png"); }
        void draw(float x, float y, float size, int color) {
            int texture = Minecraft.getInstance().getTextureManager().getTexture(resource).getId();
            ImGui.getWindowDrawList().addImage(texture, x, y, x + size, y + size, 0, 0, 1, 1, color);
        }
    }

    static void begin(boolean compact) {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0, compact ? 4 : ImGui.getStyle().getItemSpacingY());
        ImGui.pushStyleVar(ImGuiStyleVar.IndentSpacing, ImGui.getFontSize() * .8f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, ImGui.getColorU32(ImGuiCol.FrameBgHovered));
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, ImGui.getColorU32(ImGuiCol.FrameBgActive));
    }

    static void end() { ImGui.popStyleColor(2); ImGui.popStyleVar(2); }

    static boolean folder(String id, String label, String count, int flags) {
        float x = ImGui.getCursorScreenPosX();
        boolean open = ImGui.treeNodeEx(id, flags | ImGuiTreeNodeFlags.SpanAvailWidth, "");
        caption(label, count, x + ImGui.getTreeNodeToLabelSpacing(), true, open, false, null);
        return open;
    }

    static boolean file(String id, String label, boolean selected, boolean modified, DefinitionKind kind) {
        float x = ImGui.getCursorScreenPosX();
        boolean clicked = ImGui.selectable("##" + id, selected);
        caption(label, "", x + ImGui.getTreeNodeToLabelSpacing(), false, false, modified, kind);
        return clicked;
    }

    static boolean root(String label, boolean selected) {
        float x = ImGui.getCursorScreenPosX();
        boolean clicked = ImGui.selectable("##pack_root", selected);
        caption(label, "", x + ImGui.getTreeNodeToLabelSpacing(), true, true, false, null);
        return clicked;
    }

    static void guides(float left, int depth) {
        float top = ImGui.getCursorScreenPosY();
        for (int i = 0; i < depth; i++) {
            float x = left + (i * .8f + .45f) * ImGui.getFontSize();
            ImGui.getWindowDrawList().addLine(x, top - 4, x, top + ImGui.getTextLineHeight(), ImGui.getColorU32(ImGuiCol.Border));
        }
    }

    static boolean action(String id, String tooltip) {
        float size = ImGui.getFrameHeight();
        ImGui.pushStyleColor(ImGuiCol.Button, 0);
        boolean clicked = ImGui.button("##explorer_" + id, size, size);
        ImGui.popStyleColor();
        float x = ImGui.getItemRectMinX() + size / 2, y = ImGui.getItemRectMinY() + size / 2;
        float iconSize = ImGui.getFontSize();
        var icon = switch (id) { case "options" -> Icon.OPTIONS; case "collapse" -> Icon.COLLAPSE; default -> Icon.REVEAL; };
        icon.draw(x - iconSize / 2, y - iconSize / 2, iconSize, ImGui.getColorU32(ImGuiCol.TextDisabled));
        if (ImGui.isItemHovered() || ImGui.isItemFocused()) ImGui.setTooltip(tooltip);
        return clicked;
    }

    private static void caption(String label, String count, float x, boolean folder, boolean open, boolean modified, DefinitionKind kind) {
        float top = ImGui.getItemRectMinY(), bottom = ImGui.getItemRectMaxY();
        float right = ImGui.getItemRectMaxX() - 6;
        float size = ImGui.getFontSize(), y = (top + bottom - size) / 2;
        int color = folder ? 0xffd3c48e : kind == DefinitionKind.QUEST ? 0xff9cc6a2 : ImGui.getColorU32(ImGuiCol.TextDisabled);
        var draw = ImGui.getWindowDrawList();
        Icon icon = folder ? open ? Icon.OPEN_FOLDER : Icon.FOLDER
                : kind == DefinitionKind.TREE || kind == DefinitionKind.CONVERSATION || kind == DefinitionKind.FORCED ? Icon.DIALOGUE : Icon.FILE;
        icon.draw(x, y, size, color);
        if (!count.isEmpty()) {
            float width = ImGui.calcTextSize(count).x;
            draw.addText(right - width, top, ImGui.getColorU32(ImGuiCol.TextDisabled), count);
            right -= width + 12;
        }
        if (modified) {
            draw.addCircleFilled(right - 4, (top + bottom) / 2, 3, ImGui.getColorU32(ImGuiCol.Text));
            right -= 14;
        }
        float textX = x + size + 7;
        draw.pushClipRect(textX, top, Math.max(textX, right), bottom, true);
        draw.addText(textX, top, ImGui.getColorU32(ImGuiCol.Text), shorten(label, Math.max(0, right - textX)));
        draw.popClipRect();
    }

    private static String shorten(String value, float width) {
        if (ImGui.calcTextSize(value).x <= width) return value;
        int dot = value.lastIndexOf('.');
        String suffix = dot > 0 && value.length() - dot <= 8 ? value.substring(dot) : "";
        int low = 0, high = suffix.isEmpty() ? value.length() : dot;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (ImGui.calcTextSize(value.substring(0, mid) + "..." + suffix).x <= width) low = mid;
            else high = mid - 1;
        }
        if (low > 0 && Character.isHighSurrogate(value.charAt(low - 1))) low--;
        return value.substring(0, low) + "..." + suffix;
    }
}
