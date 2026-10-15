package com.jvn.villagerretaliation.authoring.client;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import java.util.HashMap;
import java.util.Map;

final class EditorGraphText {
    private record Cached(EditorTextPreview preview, int frame) { }
    private final Map<String, Cached> previews = new HashMap<>();

    void beginFrame(boolean visible) {
        previews.entrySet().removeIf(entry -> {
            if (visible && entry.getValue().frame() >= ImGui.getFrameCount() - 1) return false;
            entry.getValue().preview().close();
            return true;
        });
    }

    void clear() {
        previews.values().forEach(value -> value.preview().retire());
        previews.clear();
    }

    void draw(EditorGraph.Block block, float x, float y, float width, float height, float zoom, int background) {
        if (preview(block.pointer()).image(block.styledText(), x, y, width, height, ImGui.getFontSize() * zoom, background, true)) {
            var draw = ImGui.getWindowDrawList();
            float size = 22 * zoom;
            draw.addRectFilled(x + width - size, y, x + width, y + height, background);
            draw.addText(ImGui.getFont(), Math.max(6, (int) (ImGui.getFontSize() * zoom)),
                    x + width - size, y + 6 * zoom, 0xffd9d2c7, "...");
        }
    }

    void tooltip(EditorGraph.Block block) {
        ImGui.beginTooltip();
        ImGui.textUnformatted(block.title());
        float width = Math.min(480, ImGui.getMainViewport().getWorkSizeX() - 40);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        float scale = (float) ImGui.getFontSize() / font.lineHeight;
        float measured = com.jvn.villagerretaliation.client.interaction.VillagerStyledTextRenderer.measureWrapped(
                font, block.styledText(), Math.max(1, (int) (width / scale) - 9)) * scale;
        float height = Math.min(measured, Math.min(240, ImGui.getMainViewport().getWorkSizeY() - 100));
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        boolean clipped = preview(block.pointer() + "##tooltip").image(block.styledText(), x, y, width, height,
                ImGui.getFontSize(), ImGui.getColorU32(ImGuiCol.PopupBg), false);
        ImGui.dummy(width, height);
        ImGui.textDisabled(clipped ? "Click to edit and read the rest" : "Click to edit");
        ImGui.endTooltip();
    }

    private EditorTextPreview preview(String key) {
        Cached cached = previews.get(key);
        EditorTextPreview preview = cached == null ? new EditorTextPreview() : cached.preview();
        previews.put(key, new Cached(preview, ImGui.getFrameCount()));
        return preview;
    }
}
