package com.jvn.villagerretaliation.authoring.client;

import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class EditorResourcePicker {
    private final ImString search = new ImString(256);
    private int selected;

    static List<String> matching(List<String> choices, String query) {
        String term = query.strip().toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).contains(term))
                .sorted(java.util.Comparator.comparingInt(choice -> {
                    String id = choice.toLowerCase(Locale.ROOT);
                    return id.equals(term) || id.substring(id.indexOf(':') + 1).equals(term) ? 0 : 1;
                })).toList();
    }

    void render(String id, String label, String current, List<String> choices, Consumer<String> choose) {
        if (choices.isEmpty()) return;
        ImGui.pushID(id);
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##resource_picker", label, imgui.flag.ImGuiComboFlags.HeightLarge)) {
            if (ImGui.isWindowAppearing()) { search.set(""); selected = 0; ImGui.setKeyboardFocusHere(); }
            String before = search.get();
            ImGui.setNextItemWidth(-1);
            ImGui.inputTextWithHint("##filter", "Type to filter...", search);
            boolean enter = ImGui.isKeyPressed(ImGuiKey.Enter);
            if (!before.equals(search.get())) selected = 0;
            var matches = matching(choices, search.get());
            boolean moved = false;
            if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) { selected++; moved = true; }
            if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) { selected--; moved = true; }
            selected = Math.clamp(selected, 0, Math.max(0, matches.size() - 1));
            if (enter && !matches.isEmpty()) { choose.accept(matches.get(selected)); ImGui.closeCurrentPopup(); }
            if (matches.isEmpty()) ImGui.textDisabled("No matches");
            ImGui.beginChild("##choices", 0, Math.min(240, Math.max(1, matches.size()) * ImGui.getFrameHeightWithSpacing()), false);
            for (int i = 0; i < matches.size(); i++) {
                String choice = matches.get(i);
                ImGui.pushID(i);
                if (ImGui.selectable(choice, choice.equals(current) || i == selected)) { choose.accept(choice); ImGui.closeCurrentPopup(); }
                if (moved && i == selected) ImGui.setScrollHereY();
                ImGui.popID();
            }
            ImGui.endChild();
            ImGui.endCombo();
        }
        ImGui.popID();
    }
}
