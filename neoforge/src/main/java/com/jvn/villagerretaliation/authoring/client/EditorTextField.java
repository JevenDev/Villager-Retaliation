package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;
import java.util.HashMap;
import java.util.Map;

final class EditorTextField {
    private final Map<String, Selection> selections = new HashMap<>();

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
        if (formatting && !selection.sourceMode && selection.command == null) {
            if (selection.preview.render(buffer.get())) {
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
            ImGui.setNextItemWidth(-1);
            ImGui.inputTextMultiline("##text", buffer, -1, formatting ? 112 : 64,
                    ImGuiInputTextFlags.CallbackAlways | ImGuiInputTextFlags.WordWrap, selection);
            selection.editing = ImGui.isItemActive();
            if (!selection.editing && selection.command == null && ImGui.getFrameCount() > selection.focusFrame + 1)
                selection.sourceMode = false;
        }
        if (formatting) {
            var warnings = DialogueTextSegment.markupWarnings(buffer.get());
            for (String warning : warnings.stream().limit(3).toList()) ImGui.textWrapped(warning);
        }
        ImGui.popID();
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
