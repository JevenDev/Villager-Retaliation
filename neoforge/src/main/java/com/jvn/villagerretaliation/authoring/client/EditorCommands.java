package com.jvn.villagerretaliation.authoring.client;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class EditorCommands {
    record Command(String name, String shortcut, boolean enabled, ImGuiAuthoringEditor.EditorOperation action) {
        String id() { return name.startsWith("Undo:") ? "Undo" : name.startsWith("Redo:") ? "Redo" : name; }
    }
    private final ImString query = new ImString(256);
    private int selected;
    private boolean requested;

    void open() { requested = true; }
    boolean active() { return requested || ImGui.isPopupOpen("Commands###editor_commands"); }

    static List<Command> matching(List<Command> commands, String query) {
        String term = query.strip().toLowerCase(Locale.ROOT);
        return commands.stream().filter(command -> command.name().toLowerCase(Locale.ROOT).contains(term)).toList();
    }

    void render(List<Command> commands, Consumer<ImGuiAuthoringEditor.EditorOperation> run, float textScale) {
        if (requested) { requested = false; query.set(""); selected = 0; ImGui.openPopup("Commands###editor_commands"); }
        ImGui.setNextWindowSize(640, 420, ImGuiCond.Appearing);
        if (!ImGui.beginPopup("Commands###editor_commands")) return;
        ImGui.setWindowFontScale(textScale);
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere();
        String before = query.get();
        ImGui.setNextItemWidth(-1); ImGui.inputTextWithHint("##query", "Find an editor action...", query);
        if (!before.equals(query.get())) selected = 0;
        var matches = matching(commands, query.get());
        boolean moved = false;
        if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) { selected++; moved = true; }
        if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) { selected--; moved = true; }
        selected = Math.clamp(selected, 0, Math.max(0, matches.size() - 1));
        if (ImGui.isKeyPressed(ImGuiKey.Enter) && !matches.isEmpty() && matches.get(selected).enabled()) {
            run.accept(matches.get(selected).action()); ImGui.closeCurrentPopup();
        }
        ImGui.textDisabled("Enter runs the selected action. Escape closes this list.");
        ImGui.beginChild("##actions", 0, 0, false);
        for (int i = 0; i < matches.size(); i++) {
            var command = matches.get(i);
            ImGui.pushID(i); ImGui.beginDisabled(!command.enabled());
            if (ImGui.selectable(command.name() + (command.shortcut().isEmpty() ? "" : " (" + command.shortcut() + ")"), i == selected)) {
                run.accept(command.action()); ImGui.closeCurrentPopup();
            }
            if (moved && i == selected) ImGui.setScrollHereY();
            ImGui.endDisabled(); ImGui.popID();
        }
        if (matches.isEmpty()) ImGui.textWrapped("No matching actions.");
        ImGui.endChild(); ImGui.endPopup();
    }
}
