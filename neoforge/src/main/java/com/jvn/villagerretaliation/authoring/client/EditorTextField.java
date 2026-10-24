package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImString;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class EditorTextField {
    private final Map<String, Selection> selections = new HashMap<>();
    private final Map<String, Size> sizes = new HashMap<>();
    private final EditorVariablePicker variables = new EditorVariablePicker();
    private Map<String, String> overrides = Map.of();
    private final Map<String, EditorShortcuts.Binding> bindings = new HashMap<>();
    private boolean bindingsReady;
    private String focus = "";

    void focus(String id) { focus = id; }

    void clear() {
        selections.values().forEach(selection -> selection.preview.retire());
        selections.clear();
    }

    void beginFrame(boolean visible, Map<String, String> shortcuts) {
        if (!bindingsReady || !overrides.equals(shortcuts)) {
            bindingsReady = true;
            overrides = Map.copyOf(shortcuts);
            bindings.clear();
            for (var style : EditorTextStyles.STYLES) {
                var binding = EditorShortcuts.parse(EditorShortcuts.binding(style.command(), overrides));
                if (binding != null) bindings.put(style.tag(), binding);
            }
        }
        beginFrame(visible);
    }

    void beginFrame(boolean visible) {
        EditorTextPreview.disposeRetired();
        if (!visible) selections.values().forEach(selection -> selection.preview.close());
    }

    record InlineEdit(boolean activated, boolean apply) { }

    InlineEdit inline(String id, ImString buffer, float width, float height) {
        return inline(id, buffer, width, height, true);
    }

    InlineEdit inline(String id, ImString buffer, float width, float height, boolean formatting) {
        Selection selection = selections.compute(id, (key, prior) -> {
            if (prior != null && prior.buffer == buffer) return prior;
            if (prior != null) prior.preview.retire();
            return new Selection(buffer);
        });
        ImGui.pushID(id);
        boolean activated = false, apply = false;
        if (!selection.sourceMode) {
            if (selection.preview.render(buffer.get(), width, height)) {
                selection.sourceMode = true; selection.requestFocus = true; activated = true;
            }
        } else {
            selection.toolbarBusy = false;
            float left = ImGui.getCursorScreenPosX(), top = ImGui.getCursorScreenPosY();
            if (formatting) {
                formatToolbar(selection);
                ImGui.sameLine();
                styleMenu(selection);
            }
            float toolbarHeight = ImGui.getCursorScreenPosY() - top;
            ImGui.setCursorScreenPos(left, top + toolbarHeight);
            focusSource(selection);
            if (ImGui.inputTextMultiline("##text", buffer, width, Math.max(1, height - toolbarHeight),
                    ImGuiInputTextFlags.CallbackAlways | ImGuiInputTextFlags.WordWrap, selection)) selection.dirty = true;
            activated = ImGui.isItemActivated();
            selection.editing = ImGui.isItemActive();
            if (formatting && selection.editing) formattingShortcut(selection);
            if (leftSource(selection)) {
                selection.sourceMode = false;
                apply = selection.dirty;
                selection.dirty = false;
            }
        }
        ImGui.popID();
        return new InlineEdit(activated, apply);
    }

    void render(String id, ImString buffer, DefinitionKind kind, boolean formatting, float textScale) {
        Selection selection = selections.compute(id, (key, prior) -> {
            if (prior != null && prior.buffer == buffer) return prior;
            if (prior != null) prior.preview.retire();
            return new Selection(buffer);
        });
        if (focus.equals(id)) { focus = ""; selection.sourceMode = true; selection.requestFocus = true; }
        ImGui.pushID(id);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, ImGui.getStyle().getFramePaddingY());
        selection.toolbarBusy = false;
        if (formatting) {
            formatToolbar(selection);
            EditorWidgets.sameLineFor("Insert variable");
        }
        String variable = variables.render(kind, textScale);
        if (variable != null) selection.command = new Command("{" + variable + "}", false);
        if (formatting) {
            EditorWidgets.sameLineFor("Style");
            styleMenu(selection);
        }
        ImGui.popStyleVar();
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
            focusSource(selection);
            ImGui.pushStyleColor(ImGuiCol.NavCursor, 0);
            ImGui.inputTextMultiline("##text", buffer, width, height - 12,
                    ImGuiInputTextFlags.CallbackAlways | ImGuiInputTextFlags.WordWrap, selection);
            ImGui.popStyleColor();
            selection.editing = ImGui.isItemActive();
            if (formatting && selection.editing) formattingShortcut(selection);
            if (leftSource(selection)) selection.sourceMode = false;
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

    private static void focusSource(Selection selection) {
        if (selection.requestFocus || selection.command != null && selection.focusFrame < 0) {
            ImGui.setKeyboardFocusHere();
            selection.requestFocus = false;
            selection.sourceMode = true;
            selection.focusFrame = ImGui.getFrameCount();
        }
    }

    private static boolean leftSource(Selection selection) {
        return !selection.editing && !selection.toolbarBusy && selection.command == null
                && ImGui.getFrameCount() > selection.focusFrame + 1;
    }

    private void formattingShortcut(Selection selection) {
        if (selection.toolbarBusy || selection.command != null) return;
        for (var style : EditorTextStyles.STYLES) {
            var binding = bindings.get(style.tag());
            if (binding != null && binding.pressed()) { selection.command = new Command(style.tag(), true); return; }
        }
    }

    private void formatToolbar(Selection selection) {
        for (int i = 0; i < 3; i++) {
            if (i > 0) ImGui.sameLine();
            var style = EditorTextStyles.STYLES.get(i);
            if (ImGui.smallButton(style.tag().toUpperCase(Locale.ROOT))) selection.command = new Command(style.tag(), true);
            toolbarItem(selection);
            if (ImGui.isItemHovered()) {
                String shortcut = EditorShortcuts.binding(style.command(), overrides);
                ImGui.setTooltip(style.name() + (shortcut.isEmpty() ? "" : " (" + shortcut + ")")
                        + ": apply to the selection, or insert a text span. Rebind in Keyboard shortcuts.");
            }
        }
    }

    private void styleMenu(Selection selection) {
        if (ImGui.smallButton("Style")) ImGui.openPopup("styles");
        toolbarItem(selection);
        if (ImGui.isItemHovered()) ImGui.setTooltip("Colors, gradients and text effects. Select text to apply or remove a style.");
        if (!ImGui.beginPopup("styles")) return;
        selection.toolbarBusy = true;
        ImGui.setWindowFontScale(1);
        if (ImGui.beginMenu("Color")) {
            ImGui.setWindowFontScale(1);
            for (String color : EditorTextStyles.COLORS) {
                if (ImGui.menuItem(color.replace('_', ' ') + "    ")) selection.command = new Command("color:" + color, true);
                int rgb = DialogueTextEffects.parseColor(color);
                float right = ImGui.getItemRectMaxX() - 8, y = ImGui.getItemRectMinY() + 4;
                ImGui.getWindowDrawList().addRectFilled(right - 10, y, right, y + 10,
                        ImGui.getColorU32((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1));
            }
            ImGui.separator();
            if (selection.color == null) selection.color = new float[] {1, .67f, 0};
            ImGui.setNextItemWidth(220);
            ImGui.colorEdit3("Custom", selection.color, ImGuiColorEditFlags.DisplayHex);
            if (ImGui.button("Apply custom color")) {
                selection.command = new Command("color:" + EditorTextStyles.hex(selection.color), true);
                ImGui.closeCurrentPopup();
            }
            ImGui.endMenu();
        }
        for (var style : EditorTextStyles.STYLES.subList(3, EditorTextStyles.STYLES.size())) {
            if (ImGui.menuItem(style.name(), EditorShortcuts.binding(style.command(), overrides)))
                selection.command = new Command(style.tag(), true);
        }
        ImGui.separator();
        if (ImGui.beginMenu("Gradient")) {
            ImGui.setWindowFontScale(1);
            if (selection.gradientStart == null) {
                selection.gradientStart = new float[] {1, .67f, 0};
                selection.gradientEnd = new float[] {1, .33f, .33f};
            }
            ImGui.setNextItemWidth(220);
            ImGui.colorEdit3("Start", selection.gradientStart, ImGuiColorEditFlags.DisplayHex);
            ImGui.setNextItemWidth(220);
            ImGui.colorEdit3("End", selection.gradientEnd, ImGuiColorEditFlags.DisplayHex);
            if (ImGui.button("Apply gradient")) {
                selection.command = new Command("gradient:" + EditorTextStyles.hex(selection.gradientStart) + ":" + EditorTextStyles.hex(selection.gradientEnd), true);
                ImGui.closeCurrentPopup();
            }
            ImGui.endMenu();
        }
        var presets = com.jvn.villagerretaliation.dialogue.resources.DialogueStyleResources.current();
        if (!presets.isEmpty() && ImGui.beginMenu("Custom styles")) {
            for (String tag : new java.util.TreeSet<>(presets.keySet()))
                if (ImGui.menuItem(tag)) selection.command = new Command(tag, true);
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Gaze")) {
            for (String target : java.util.List.of("player", "nearest_participant", "role:provider"))
                if (ImGui.menuItem(target.replace('_', ' '))) selection.command = new Command("<look=" + target + ">", false);
            ImGui.textDisabled("Use <look=role:name> for a bound character.");
            ImGui.endMenu();
        }
        ImGui.endPopup();
    }

    private static void toolbarItem(Selection selection) {
        selection.toolbarBusy |= ImGui.isItemActive() || ImGui.isItemHovered() && (ImGui.isMouseDown(0) || ImGui.isMouseReleased(0));
    }

    private record Command(String value, boolean format) { }

    private static final class Selection extends ImGuiInputTextCallback {
        private final ImString buffer;
        private final EditorTextPreview preview = new EditorTextPreview();
        private boolean editing, sourceMode, requestFocus, toolbarBusy, dirty;
        private float[] color, gradientStart, gradientEnd;
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
