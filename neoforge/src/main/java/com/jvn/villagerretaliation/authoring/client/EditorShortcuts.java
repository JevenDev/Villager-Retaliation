package com.jvn.villagerretaliation.authoring.client;

import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

final class EditorShortcuts {
    private static final Map<String, Integer> KEYS = keys();
    private final Map<String, ImString> fields = new LinkedHashMap<>();
    private boolean requested;

    void open() { requested = true; }

    void popup(List<EditorCommands.Command> commands, EditorLibrary library, Consumer<ImGuiAuthoringEditor.EditorOperation> run, float textScale) {
        if (requested) { requested = false; ImGui.openPopup("Customize shortcuts"); }
        if (!EditorWidgets.beginPopup("Customize shortcuts", 620, 620, textScale)) return;
        ImGui.setNextItemOpen(true); render(commands, library, run);
        ImGui.endPopup();
    }

    record Binding(boolean ctrl, boolean alt, boolean shift, int key, String name) {
        boolean pressed() {
            var io = ImGui.getIO();
            return ctrl == io.getKeyCtrl() && alt == io.getKeyAlt() && shift == io.getKeyShift() && !io.getKeySuper() && ImGui.isKeyPressed(key, false);
        }
        String label() { return (ctrl ? "Ctrl+" : "") + (alt ? "Alt+" : "") + (shift ? "Shift+" : "") + name; }
    }

    private static Map<String, Integer> keys() {
        var result = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < 26; i++) result.put(String.valueOf((char) ('A' + i)), ImGuiKey.A + i);
        for (int i = 0; i < 12; i++) result.put("F" + (i + 1), ImGuiKey.F1 + i);
        result.put("TAB", ImGuiKey.Tab); result.put("SPACE", ImGuiKey.Space);
        result.put("LEFT", ImGuiKey.LeftArrow); result.put("RIGHT", ImGuiKey.RightArrow);
        result.put("UP", ImGuiKey.UpArrow); result.put("DOWN", ImGuiKey.DownArrow);
        result.put("DELETE", ImGuiKey.Delete); result.put("HOME", ImGuiKey.Home); result.put("END", ImGuiKey.End);
        return Map.copyOf(result);
    }

    static Binding parse(String text) {
        if (text.isBlank()) return null;
        boolean ctrl = false, alt = false, shift = false;
        String[] parts = text.strip().toUpperCase(Locale.ROOT).split("\\+", -1);
        for (int i = 0; i < parts.length - 1; i++) switch (parts[i].strip()) {
            case "CTRL" -> { if (ctrl) throw new IllegalArgumentException("Duplicate Ctrl modifier"); ctrl = true; }
            case "ALT" -> { if (alt) throw new IllegalArgumentException("Duplicate Alt modifier"); alt = true; }
            case "SHIFT" -> { if (shift) throw new IllegalArgumentException("Duplicate Shift modifier"); shift = true; }
            default -> throw new IllegalArgumentException("Use Ctrl, Alt or Shift before the key");
        }
        String key = parts[parts.length - 1].strip();
        Integer code = KEYS.get(key);
        if (code == null) throw new IllegalArgumentException("Use A-Z, F1-F12, Tab, Delete, Home, End or an arrow key");
        if (key.equals("SPACE")) throw new IllegalArgumentException("Space is reserved for activating focused controls");
        if (key.equals("F9") || key.equals("F11")) throw new IllegalArgumentException("F9 opens the editor and F11 toggles Minecraft fullscreen");
        if (!ctrl && !alt && !key.startsWith("F") && !key.equals("DELETE")) throw new IllegalArgumentException("Use Ctrl or Alt with this key");
        String label = key.length() == 1 || key.startsWith("F") ? key : key.substring(0, 1) + key.substring(1).toLowerCase(Locale.ROOT);
        return new Binding(ctrl, alt, shift, code, label);
    }

    static String binding(EditorCommands.Command command, Map<String, String> overrides) {
        return overrides.getOrDefault(command.id(), command.shortcut());
    }

    static void validate(List<EditorCommands.Command> commands, Map<String, String> overrides) {
        Map<String, String> used = new LinkedHashMap<>();
        for (var command : commands) {
            var parsed = parse(binding(command, overrides));
            if (parsed == null) continue;
            String previous = used.putIfAbsent(parsed.label(), command.id());
            if (previous != null) throw new IllegalArgumentException(parsed.label() + " is already assigned to " + previous);
        }
    }

    List<EditorCommands.Command> effective(List<EditorCommands.Command> commands, EditorLibrary library) {
        var overrides = library.shortcuts();
        return commands.stream().map(command -> new EditorCommands.Command(command.name(), binding(command, overrides), command.enabled(), command.action())).toList();
    }

    boolean pressed() {
        for (int key : KEYS.values()) if (ImGui.isKeyPressed(key, false)) return true;
        return false;
    }

    void dispatch(List<EditorCommands.Command> commands, EditorLibrary library, Consumer<ImGuiAuthoringEditor.EditorOperation> run, boolean typing) {
        var overrides = library.shortcuts();
        var matches = commands.stream().filter(command -> {
            var parsed = parse(binding(command, overrides));
            return parsed != null && parsed.pressed();
        }).toList();
        if (matches.size() != 1) return;
        var command = matches.getFirst();
        boolean textSafe = List.of("Save drafts", "Quick open file", "Find content", "Open commands", "Focus work area", "Focus properties").contains(command.id());
        if (command.enabled() && (!typing || textSafe)) run.accept(command.action());
    }

    void render(List<EditorCommands.Command> commands, EditorLibrary library, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        if (!EditorWidgets.section("Keyboard shortcuts")) return;
        ImGui.textWrapped("Enter a chord such as Ctrl+Shift+G or F10. Leave it blank to unbind. F9 opens the editor and F11 toggles fullscreen.");
        if (ImGui.button("Restore default shortcuts")) run.accept(() -> { library.resetShortcuts(); fields.clear(); });
        var overrides = library.shortcuts();
        for (var command : commands) {
            ImGui.pushID(command.id());
            var field = fields.computeIfAbsent(command.id(), ignored -> new ImString(binding(command, overrides), 128));
            ImGui.textUnformatted(command.id());
            ImGui.setNextItemWidth(EditorWidgets.inputWidth("Assign"));
            ImGui.inputText("##binding", field);
            EditorWidgets.sameLineFor("Assign");
            if (ImGui.button("Assign")) run.accept(() -> {
                var parsed = parse(field.get());
                String value = parsed == null ? "" : parsed.label();
                var changed = new LinkedHashMap<>(library.shortcuts()); changed.put(command.id(), value);
                validate(commands, changed); library.shortcut(command.id(), value); field.set(value);
            });
            ImGui.popID();
        }
    }
}
