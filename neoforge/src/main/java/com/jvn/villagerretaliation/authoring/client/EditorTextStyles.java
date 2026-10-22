package com.jvn.villagerretaliation.authoring.client;

import java.util.List;
import java.util.Locale;

final class EditorTextStyles {
    static final List<Style> STYLES = List.of(new Style("Bold", "b", "Ctrl+B"), new Style("Italic", "i", "Ctrl+I"),
            new Style("Underline", "u", "Ctrl+U"), new Style("Strikethrough", "s", "Ctrl+Shift+X"),
            new Style("Wave", "wave", ""), new Style("Shake", "shake", ""), new Style("Pulse", "pulse", ""),
            new Style("Jump", "jump", ""), new Style("Rainbow", "rainbow", ""), new Style("Obfuscated", "obfuscated", ""));
    static final List<String> COLORS = List.of("white", "gray", "gold", "yellow", "red", "green", "aqua", "blue", "light_purple",
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "dark_gray");
    record Style(String name, String tag, String shortcut) {
        String id() { return "Text: " + name; }
        EditorCommands.Command command() { return new EditorCommands.Command(id(), shortcut, false, () -> { }); }
    }

    static List<EditorCommands.Command> shortcutCommands(List<EditorCommands.Command> commands) {
        return java.util.stream.Stream.concat(commands.stream(), STYLES.stream().map(Style::command)).toList();
    }

    static String hex(float[] color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", Math.round(Math.clamp(color[0], 0, 1) * 255),
                Math.round(Math.clamp(color[1], 0, 1) * 255), Math.round(Math.clamp(color[2], 0, 1) * 255));
    }

    private EditorTextStyles() { }
}
