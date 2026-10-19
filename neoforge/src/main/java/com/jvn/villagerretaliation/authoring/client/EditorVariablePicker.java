package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;

final class EditorVariablePicker {
    private final ImString search = new ImString(256);
    private final ImString custom = new ImString(256);
    private int selected;
    private boolean moved;
    private final ImGuiInputTextCallback navigation = new ImGuiInputTextCallback() {
        @Override public void accept(ImGuiInputTextCallbackData data) {
            if (data.getEventFlag() != ImGuiInputTextFlags.CallbackHistory) return;
            if (data.getEventKey() == ImGuiKey.DownArrow) { selected++; moved = true; }
            if (data.getEventKey() == ImGuiKey.UpArrow) { selected--; moved = true; }
        }
    };

    String render(DefinitionKind kind, float textScale) {
        if (ImGui.smallButton("Insert variable")) ImGui.openPopup("variables");
        if (!EditorWidgets.beginPopup("variables", 660, 580, textScale)) return null;
        if (ImGui.isWindowAppearing()) {
            search.set(""); custom.set(""); selected = 0;
            ImGui.setKeyboardFocusHere();
        }
        String before = search.get();
        ImGui.setNextItemWidth(-1);
        moved = false;
        ImGui.inputTextWithHint("##search", "Search names, tokens, or categories...", search, ImGuiInputTextFlags.CallbackHistory, navigation);
        boolean searchActive = ImGui.isItemActive();
        boolean searchEnter = ImGui.isKeyPressed(ImGuiKey.Enter) && (searchActive || ImGui.isItemDeactivated());
        boolean changed = !before.equals(search.get());
        if (changed) selected = 0;
        ImGui.textWrapped(kind == DefinitionKind.FORCED
                ? "Triggered dialogue uses values supplied by its event or quest. Each category explains when its variables work."
                : "Each category explains where its variables are supplied. Search, then use arrows and Enter, or click a variable.");
        var groups = EditorVariables.matching(search.get());
        var matches = groups.stream().flatMap(group -> group.variables().stream()).toList();
        boolean searching = !search.get().isBlank();
        selected = Math.clamp(selected, 0, Math.max(0, matches.size() - 1));
        String choice = null;
        if (searching && searchEnter && !matches.isEmpty()) choice = matches.get(selected).key();
        ImGui.textDisabled(searching ? matches.size() + (matches.size() == 1 ? " variable matches" : " variables match")
                : matches.size() + " variables in " + groups.size() + " categories");
        float footer = ImGui.getFrameHeightWithSpacing() + ImGui.getTextLineHeightWithSpacing() * 3;
        ImGui.beginChild("##catalog", 0, Math.max(1, ImGui.getContentRegionAvailY() - footer), false);
        if (changed) ImGui.setScrollY(0);
        if (matches.isEmpty()) ImGui.textWrapped("No matching built-in variables. Pack-defined names can be entered below.");
        int index = 0;
        for (var group : groups) {
            String title = group.title() + " (" + group.variables().size() + ")";
            boolean open;
            if (searching) { ImGui.separatorText(title); open = true; }
            else {
                ImGui.setNextItemOpen(group == EditorVariables.groups().getFirst(), imgui.flag.ImGuiCond.Once);
                open = EditorWidgets.section(title + "###" + group.title());
            }
            if (open) {
                ImGui.textWrapped(group.availability());
                for (var variable : group.variables()) {
                    if (ImGui.selectable(variable.label() + "\n" + variable.token(), searching && index == selected)) choice = variable.key();
                    if (ImGui.isItemHovered()) ImGui.setTooltip(variable.token() + "\n" + group.availability());
                    if (searching && moved && index == selected) ImGui.setScrollHereY();
                    index++;
                }
            } else index += group.variables().size();
        }
        ImGui.endChild();
        ImGui.separator();
        ImGui.textUnformatted("Custom or pack-defined variable");
        ImGui.setNextItemWidth(EditorWidgets.inputWidth("Insert"));
        ImGui.inputTextWithHint("##custom", "Variable name, without braces", custom);
        boolean customEnter = ImGui.isKeyPressed(ImGuiKey.Enter) && (ImGui.isItemActive() || ImGui.isItemDeactivated());
        EditorWidgets.sameLineFor("Insert");
        String key = EditorVariables.customKey(custom.get());
        ImGui.beginDisabled(key.isEmpty());
        if (ImGui.button("Insert") || customEnter && !key.isEmpty()) choice = key;
        ImGui.endDisabled();
        ImGui.textWrapped("Custom names resolve only when your pack supplies a value for them.");
        if (choice != null) ImGui.closeCurrentPopup();
        ImGui.endPopup();
        return choice;
    }
}
