package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.type.ImString;
import java.util.HashMap;
import java.util.Map;

final class EditorTextField {
    private final Map<String, Selection> selections = new HashMap<>();
    private final Map<String, Size> sizes = new HashMap<>();

    void clear() {
        selections.values().forEach(selection -> selection.preview.retire());
        selections.clear();
    }

    void beginFrame(boolean visible) {
        EditorTextPreview.disposeRetired();
        if (!visible) selections.values().forEach(selection -> selection.preview.close());
    }

    void render(String id, ImString buffer, DefinitionKind kind, boolean formatting) {
        Selection selection = selections.compute(id, (key, prior) -> {
            if (prior != null && prior.buffer == buffer) return prior;
            if (prior != null) prior.preview.retire();
            return new Selection(buffer);
        });
        ImGui.pushID(id);
        if (formatting) {
            formatButton("B", "Bold", "b", selection);
            ImGui.sameLine();
            formatButton("I", "Italic", "i", selection);
            ImGui.sameLine();
            formatButton("U", "Underline", "u", selection);
            ImGui.sameLine();
        }
        if (ImGui.smallButton("Insert variable")) ImGui.openPopup("variables");
        if (ImGui.beginPopup("variables")) {
            variable("Player name", "player", selection);
            variable("Villager name", "villager", selection);
            ImGui.separator();
            variable("Time (06:00)", "time", selection);
            variable("Time of day (morning)", "time_of_day", selection);
            variable("Day number (1)", "day", selection);
            ImGui.separator();
            variable("Last death message", "last_death_message", selection);
            variable("Last death killer", "last_death_killer", selection);
            variable("Last death dimension", "last_death_dimension", selection);
            ImGui.separator();
            if (kind != DefinitionKind.FORCED) {
                variable("Villager's name (possessive)", "villager_possessive", selection);
                variable("Profession", "profession", selection);
                variable("Reputation", "reputation", selection);
                variable("Reputation level", "reputation_level", selection);
            }
            variable("Villager X", "x", selection);
            variable("Villager Y", "y", selection);
            variable("Villager Z", "z", selection);
            ImGui.endPopup();
        }
        if (formatting) {
            ImGui.sameLine();
            if (ImGui.smallButton("Style")) ImGui.openPopup("styles");
            if (ImGui.beginPopup("styles")) {
                if (ImGui.beginMenu("Color")) {
                    for (String color : new String[] {"white", "gray", "gold", "yellow", "red", "green", "aqua", "blue", "light_purple"}) {
                        if (ImGui.menuItem(color.replace('_', ' '))) selection.command = new Command("color:" + color, true);
                    }
                    ImGui.endMenu();
                }
                for (String[] style : new String[][] {{"Strikethrough", "s"}, {"Wave", "wave"}, {"Shake", "shake"},
                        {"Pulse", "pulse"}, {"Jump", "jump"}, {"Rainbow", "rainbow"}, {"Obfuscated", "obfuscated"}}) {
                    if (ImGui.menuItem(style[0])) selection.command = new Command(style[1], true);
                }
                ImGui.endPopup();
            }
        }
        float defaultHeight = formatting ? 112 : 64;
        Size size = sizes.computeIfAbsent(id, ignored -> new Size(defaultHeight));
        float available = Math.max(1, ImGui.getContentRegionAvailX());
        float width = size.width == 0 ? available : clampWidth(size.width, available);
        float height = clampHeight(size.height);
        var origin = ImGui.getCursorScreenPos();
        boolean editingSource = selection.sourceMode || selection.editing;
        if (formatting && !selection.sourceMode && selection.command == null) {
            if (selection.preview.render(buffer.get(), width, height - 12)) {
                selection.sourceMode = true;
                selection.requestFocus = true;
            }
        } else {
            if (selection.requestFocus || selection.command != null && selection.focusFrame < 0) {
                ImGui.setKeyboardFocusHere();
                selection.focusFrame = ImGui.getFrameCount();
                selection.requestFocus = false;
                selection.sourceMode = true;
            }
            ImGui.pushStyleColor(ImGuiCol.NavCursor, 0);
            ImGui.inputTextMultiline("##text", buffer, width, height - 12,
                    ImGuiInputTextFlags.CallbackAlways | ImGuiInputTextFlags.WordWrap, selection);
            ImGui.popStyleColor();
            selection.editing = ImGui.isItemActive();
            if (!selection.editing && selection.command == null && ImGui.getFrameCount() > selection.focusFrame + 1)
                selection.sourceMode = false;
        }
        resizeCorner(selection, size, origin.x, origin.y, width, height, available, defaultHeight, editingSource);
        if (formatting) {
            var warnings = DialogueTextSegment.markupWarnings(buffer.get());
            for (String warning : warnings.stream().limit(3).toList()) ImGui.textWrapped(warning);
        }
        ImGui.popID();
    }

    private void resizeCorner(Selection selection, Size size, float x, float y, float width, float height,
            float available, float defaultHeight, boolean editingSource) {
        float right = x + width, bottom = y + height;
        float spacing = ImGui.getStyle().getItemSpacingY();
        ImGui.setCursorScreenPos(right - 12, bottom - 12);
        ImGui.dummy(12, 12);
        boolean hovered = ImGui.isItemHovered();
        if (hovered && ImGui.isMouseClicked(0)) {
            selection.resizeSource = editingSource;
            if (ImGui.isMouseDoubleClicked(0)) {
                size.width = 0;
                size.height = defaultHeight;
                selection.resizing = false;
                selection.sourceMode = editingSource;
                selection.requestFocus = editingSource;
            } else {
                selection.resizing = true;
                selection.resizeWidth = width;
                selection.resizeHeight = height;
                selection.resizeX = ImGui.getMousePosX();
                selection.resizeY = ImGui.getMousePosY();
            }
        }
        if (selection.resizing) {
            if (ImGui.isMouseDown(0)) {
                size.width = clampWidth(selection.resizeWidth + ImGui.getMousePosX() - selection.resizeX, available);
                size.height = clampHeight(selection.resizeHeight + ImGui.getMousePosY() - selection.resizeY);
                if (selection.resizeSource) selection.sourceMode = true;
            } else {
                selection.resizing = false;
                if (selection.resizeSource) { selection.sourceMode = true; selection.requestFocus = true; }
            }
        }
        if (hovered || selection.resizing) ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNWSE);
        if (hovered && !selection.resizing) ImGui.setTooltip("Drag the corner to resize. Double-click to reset.");
        var draw = ImGui.getWindowDrawList();
        draw.addRectFilled(x, bottom - 15, right, bottom, ImGui.getColorU32(ImGuiCol.FrameBg), ImGui.getStyle().getFrameRounding());
        if (selection.editing) draw.addRect(x - 4, y - 4, right + 4, bottom + 4,
                ImGui.getColorU32(ImGuiCol.NavCursor), ImGui.getStyle().getFrameRounding(), 0, 2);
        int color = ImGui.getColorU32(hovered || selection.resizing ? ImGuiCol.Text : ImGuiCol.TextDisabled);
        for (int offset = 4; offset <= 12; offset += 4)
            draw.addLine(right - offset, bottom - 2, right - 2, bottom - offset, color, 1);
        ImGui.setCursorScreenPos(x, bottom + spacing);
    }

    private static float clampWidth(float width, float available) {
        return Math.clamp(width, Math.min(160, available), available);
    }

    private static float clampHeight(float height) {
        float minimum = Math.max(60, ImGui.getTextLineHeight() * 2 + ImGui.getStyle().getFramePaddingY() * 2 + 12);
        return Math.clamp(height, minimum, Math.max(minimum, ImGui.getMainViewport().getWorkSizeY() * .75f));
    }

    private static final class Size {
        private float width, height;
        private Size(float height) { this.height = height; }
    }

    private static void formatButton(String label, String name, String tag, Selection selection) {
        if (ImGui.smallButton(label)) selection.command = new Command(tag, true);
        if (ImGui.isItemHovered()) ImGui.setTooltip(name + ": apply to the selection, or insert a text span.");
    }

    private static void variable(String label, String key, Selection selection) {
        if (ImGui.menuItem(label, "{" + key + "}")) selection.command = new Command("{" + key + "}", false);
    }

    private record Command(String value, boolean format) { }

    private static final class Selection extends ImGuiInputTextCallback {
        private final ImString buffer;
        private final EditorTextPreview preview = new EditorTextPreview();
        private boolean editing, sourceMode, requestFocus;
        private int start, end;
        private Command command;
        private int focusFrame = -1;
        private boolean resizing, resizeSource;
        private float resizeWidth, resizeHeight, resizeX, resizeY;

        private Selection(ImString buffer) {
            this.buffer = buffer;
            start = end = EditorTextEdit.bytes(buffer.get());
        }

        @Override public void accept(ImGuiInputTextCallbackData data) {
            if (command != null && ImGui.getFrameCount() <= focusFrame) return;
            if (command != null) {
                EditorTextEdit edit = command.format
                        ? EditorTextEdit.format(data.getBuf(), start, end, command.value)
                        : EditorTextEdit.insert(data.getBuf(), start, end, command.value);
                data.deleteChars(0, data.getBufTextLen());
                data.insertChars(0, edit.text());
                data.setCursorPos(edit.end());
                data.setSelectionStart(edit.start());
                data.setSelectionEnd(edit.end());
                command = null;
            }
            focusFrame = -1;
            start = data.hasSelection() ? data.getSelectionStart() : data.getCursorPos();
            end = data.hasSelection() ? data.getSelectionEnd() : data.getCursorPos();
        }
    }
}
