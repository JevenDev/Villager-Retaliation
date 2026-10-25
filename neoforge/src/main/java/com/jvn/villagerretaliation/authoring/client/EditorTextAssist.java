package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.dialogue.DialogueStyle;
import com.jvn.villagerretaliation.dialogue.resources.DialogueStyleResources;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.ImGuiKeyData;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class EditorTextAssist {
    private EditorTextCompletion.Context context = EditorTextCompletion.Context.empty(com.jvn.villagerretaliation.authoring.core.DefinitionKind.TREE);
    private Map<String, DialogueStyle> styles = Map.of();
    private EditorTextCompletion.Token token;
    private List<EditorTextCompletion.Suggestion> choices = List.of();
    private List<EditorTextCompletion.Inspection> inspections = List.of();
    private List<String> warnings = List.of();
    private String text = "", dismissed = "", inspected = "";
    private EditorTextCompletion.Context inspectedContext;
    private Map<String, DialogueStyle> inspectedStyles = Map.of();
    private boolean formatting, inspectedFormatting, explicit, open, hovered, moved;
    private int start, end, selected;
    private float x, y, left, top, width, height, fontSize, scrollY, scrollX;
    private EditorTextEdit pending;
    private final float[] color = {1, 170 / 255f, 0};
    private final List<Suppressed> suppressed = new ArrayList<>();
    private record Suppressed(ImGuiKeyData key, boolean down, float duration) { }

    void configure(EditorTextCompletion.Context context, boolean formatting) {
        this.context = context; this.formatting = formatting;
        styles = DialogueStyleResources.current();
    }

    boolean busy() { return hovered || pending != null || open; }
    boolean open() { return open; }
    void close() { open = false; token = null; choices = List.of(); }

    void before(boolean active, String source, int start, int end, float width, float height) {
        this.left = ImGui.getCursorScreenPosX(); this.top = ImGui.getCursorScreenPosY();
        this.width = width; this.height = height; fontSize = ImGui.getFontSize();
        this.start = start; this.end = end; text = source;
        boolean popup = ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId | imgui.flag.ImGuiPopupFlags.AnyPopupLevel);
        boolean interacting = hovered || popup;
        hovered = false;
        if (!active) { if (pending == null && !interacting) close(); return; }
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.Space, false)) {
            explicit = true; dismissed = ""; refresh(); suppress(ImGuiKey.Space);
        }
        if (!open) return;
        if (ImGui.isKeyPressed(ImGuiKey.Escape, false)) {
            dismissed = fingerprint(); close(); explicit = false; suppress(ImGuiKey.Escape);
        } else if (!choices.isEmpty() && (ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.Tab, false))) {
            choose(choices.get(selected));
            suppress(ImGuiKey.Enter); suppress(ImGuiKey.Tab);
        } else if (ImGui.isKeyPressed(ImGuiKey.UpArrow) || ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
            if (!choices.isEmpty()) selected = Math.floorMod(selected + (ImGui.isKeyPressed(ImGuiKey.UpArrow) ? -1 : 1), choices.size());
            moved = true;
            suppress(ImGuiKey.UpArrow); suppress(ImGuiKey.DownArrow);
        }
    }

    private void suppress(int key) {
        // this binding has no key-owner API, so hide consumed keys only while the text widget runs
        var data = ImGui.getIO().getKeysData()[key - ImGuiKey.NamedKey_BEGIN];
        suppressed.add(new Suppressed(data, data.getDown(), data.getDownDuration()));
        data.setDown(false); data.setDownDuration(-1);
    }

    void restoreKeys() {
        for (var saved : suppressed) { saved.key().setDown(saved.down()); saved.key().setDownDuration(saved.duration()); }
        suppressed.clear();
    }

    boolean callback(ImGuiInputTextCallbackData data) {
        if (data.getEventFlag() == ImGuiInputTextFlags.CallbackCharFilter) {
            if (explicit && ImGui.getIO().getKeyCtrl() && data.getEventChar() == ' ') data.setEventChar(0);
            return false;
        }
        boolean changed = false;
        if (pending != null) {
            data.deleteChars(0, data.getBufTextLen()); data.insertChars(0, pending.text());
            data.setSelectionStart(pending.start()); data.setSelectionEnd(pending.end()); data.setCursorPos(pending.end());
            pending = null; changed = true;
        }
        text = data.getBuf();
        start = data.hasSelection() ? data.getSelectionStart() : data.getCursorPos();
        end = data.hasSelection() ? data.getSelectionEnd() : data.getCursorPos();
        refresh();
        locate(data.getCursorPos(), ImGui.getScrollY(), ImGui.getScrollX());
        return changed;
    }

    private String fingerprint() { return text + "\u0000" + start + ":" + end; }

    private void refresh() {
        var next = EditorTextCompletion.token(text, end, formatting, explicit);
        if (start != end && !explicit) next = null;
        if (next == null || dismissed.equals(fingerprint())) { close(); return; }
        if (token == null || token.start() != next.start() || !token.query().equals(next.query())) { selected = 0; moved = true; }
        token = next;
        choices = EditorTextCompletion.suggestions(text, token, context, styles.keySet());
        selected = Math.clamp(selected, 0, Math.max(0, choices.size() - 1));
        open = true;
    }

    private void choose(EditorTextCompletion.Suggestion choice) {
        if (token == null) return;
        pending = EditorTextCompletion.complete(text, token, choice, start, end);
        dismissed = pending.text() + "\u0000" + pending.start() + ":" + pending.end();
        explicit = false; close();
    }

    private void locate(int caret, float scrollY, float scrollX) {
        this.scrollY = scrollY; this.scrollX = scrollX;
        int offset = EditorTextEdit.offset(text, caret);
        var lines = lines();
        int row = 0;
        while (row + 1 < lines.size() && offset >= lines.get(row + 1)[0]) row++;
        int lineStart = lines.get(row)[0];
        float lineX = ImGui.calcTextSize(text.substring(lineStart, Math.max(lineStart, offset))).x;
        x = left + ImGui.getStyle().getFramePaddingX() + lineX - scrollX;
        y = Math.clamp(top + ImGui.getStyle().getFramePaddingY() + row * fontSize - scrollY + fontSize, top + fontSize, top + height);
    }

    private List<int[]> lines() {
        float wrap = Math.max(1, width - ImGui.getStyle().getFramePaddingX() * 2 - ImGui.getStyle().getScrollbarSize());
        var result = new ArrayList<int[]>();
        int start = 0;
        while (start < text.length()) {
            int newline = text.indexOf('\n', start);
            int end = newline < 0 ? text.length() : newline;
            String paragraph = text.substring(start, end);
            float used = 0;
            int count = 0, boundary = 0;
            while (count < paragraph.length()) {
                int length = Character.charCount(paragraph.codePointAt(count));
                float advance = ImGui.calcTextSize(paragraph.substring(count, count + length)).x;
                if (used + advance > wrap) { if (boundary > 0) count = boundary; break; }
                used += advance;
                count += length;
                if (Character.isWhitespace(paragraph.charAt(count - 1)) || ".,;!?".indexOf(paragraph.charAt(count - 1)) >= 0) boundary = count;
            }
            if (count <= 0 && !paragraph.isEmpty()) count = Character.charCount(text.codePointAt(start));
            result.add(new int[] {start, start + count});
            start += count;
            if (start == end) { if (newline < 0) break; start++; }
            else while (start < end && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) start++;
        }
        if (result.isEmpty() || text.endsWith("\n")) result.add(new int[] {text.length(), text.length()});
        return result;
    }

    void hover() {
        if (open || !ImGui.isItemHovered()) return;
        float localY = ImGui.getMousePosY() - top - ImGui.getStyle().getFramePaddingY() + scrollY;
        var lines = lines();
        int row = (int) Math.floor(localY / Math.max(1, fontSize));
        if (row < 0 || row >= lines.size()) return;
        int offset = lines.get(row)[0], end = lines.get(row)[1];
        float localX = ImGui.getMousePosX() - left - ImGui.getStyle().getFramePaddingX() + scrollX;
        if (localX < 0) return;
        while (offset < end) {
            int length = Character.charCount(text.codePointAt(offset));
            float advance = ImGui.calcTextSize(text.substring(offset, offset + length)).x;
            if (localX < advance) break;
            localX -= advance; offset += length;
        }
        if (offset == end) return;
        var matcher = java.util.regex.Pattern.compile("\\{[^{}\\n]+}|<[^<>\\n]+>").matcher(text);
        while (matcher.find()) if (offset >= matcher.start() && offset < matcher.end()) {
            var entries = EditorTextCompletion.inspect(matcher.group(), context, styles, formatting);
            if (!entries.isEmpty()) tooltip(entries.getFirst().token() + "\n" + entries.getFirst().help());
            return;
        }
    }

    boolean render(String id) {
        if (!open) return false;
        var viewport = ImGui.getMainViewport();
        float popupWidth = Math.min(440 * Math.max(1, fontSize / 16), viewport.getWorkSizeX() - 24);
        float popupHeight = Math.min(300 * Math.max(1, fontSize / 16), viewport.getWorkSizeY() - 24);
        ImGui.setNextWindowPos(Math.clamp(x, viewport.getWorkPosX() + 8, viewport.getWorkPosX() + viewport.getWorkSizeX() - popupWidth - 8),
                Math.clamp(y + 3, viewport.getWorkPosY() + 8, viewport.getWorkPosY() + viewport.getWorkSizeY() - popupHeight - 8));
        ImGui.setNextWindowSize(popupWidth, popupHeight);
        boolean clicked = false;
        if (ImGui.begin("Text suggestions###text_completion_" + id, ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoFocusOnAppearing | ImGuiWindowFlags.NoNavInputs | ImGuiWindowFlags.NoNavFocus)) {
            // keep the list above the source without taking its keyboard focus
            if (!ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId | imgui.flag.ImGuiPopupFlags.AnyPopupLevel))
                imgui.internal.ImGui.bringWindowToDisplayFront(imgui.internal.ImGui.getCurrentWindow());
            ImGui.textDisabled("Tab / Enter insert    Arrows select    Esc dismiss");
            if (token != null && token.delimiter() == '<' && token.query().startsWith("color:")) {
                ImGui.setNextItemWidth(-1);
                ImGui.colorEdit3("##completion_color", color, ImGuiColorEditFlags.DisplayHex | ImGuiColorEditFlags.NoInputs);
                if (ImGui.button("Insert custom color")) {
                    choose(new EditorTextCompletion.Suggestion("Custom color", "color:" + EditorTextStyles.hex(color), "color", "Color", "", 0));
                    clicked = true;
                }
            }
            ImGui.beginChild("##completion_choices", 0, Math.max(40, ImGui.getContentRegionAvailY() * .58f), false);
            for (int i = 0; i < choices.size(); i++) {
                var choice = choices.get(i);
                if (ImGui.selectable(choice.token() + "  " + choice.label() + "##" + i, selected == i)) {
                    choose(choice); clicked = true; break;
                }
                if (ImGui.isItemHovered()) tooltip(choice.category() + "\n" + choice.help());
                if (moved && i == selected) ImGui.setScrollHereY();
            }
            if (choices.isEmpty()) ImGui.textWrapped(token != null && token.delimiter() == '{'
                    ? "No matching built-in variable. Custom names are allowed when your pack supplies them."
                    : "No matching tag. Keep typing a parameter, or press Escape to continue.");
            ImGui.endChild();
            if (!choices.isEmpty()) {
                var choice = choices.get(Math.min(selected, choices.size() - 1));
                ImGui.textDisabled(choice.category());
                ImGui.textWrapped(choice.help());
            }
            hovered = ImGui.isWindowHovered(imgui.flag.ImGuiHoveredFlags.ChildWindows | imgui.flag.ImGuiHoveredFlags.AllowWhenBlockedByActiveItem);
            hovered |= ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId | imgui.flag.ImGuiPopupFlags.AnyPopupLevel);
        }
        ImGui.end(); moved = false;
        return clicked;
    }

    private static void tooltip(String text) {
        ImGui.beginTooltip();
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 32);
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
    }

    void help(String source, boolean compact) {
        if (!source.equals(inspected) || !context.equals(inspectedContext) || !styles.equals(inspectedStyles) || inspectedFormatting != formatting) {
            inspected = source; inspectedContext = context; inspectedStyles = styles; inspectedFormatting = formatting;
            inspections = EditorTextCompletion.inspect(source, context, styles, formatting);
            warnings = EditorTextCompletion.warnings(source, context, styles, formatting);
        }
        String hint = formatting ? "Type { for variables or < for styles. Ctrl+Space suggests." : "Type { for variables. Ctrl+Space suggests.";
        if (compact) {
            if (!warnings.isEmpty()) ImGui.pushStyleColor(ImGuiCol.Text, 1, .72f, .3f, 1);
            ImGui.textUnformatted(warnings.isEmpty() ? (formatting ? "{ / <  Text help" : "{  Text help") : warnings.size() + " text message(s)");
            if (!warnings.isEmpty()) ImGui.popStyleColor();
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip(); ImGui.pushTextWrapPos(ImGui.getFontSize() * 30);
                ImGui.textUnformatted(hint);
                for (String warning : warnings) ImGui.textWrapped(warning);
                for (var entry : inspections) { ImGui.separator(); ImGui.textUnformatted(entry.token()); ImGui.textWrapped(entry.help()); }
                ImGui.popTextWrapPos(); ImGui.endTooltip();
            }
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.TextDisabled));
            ImGui.textWrapped(hint);
            ImGui.popStyleColor();
            for (String warning : warnings.stream().limit(3).toList()) ImGui.textWrapped(warning);
            if (!inspections.isEmpty() && ImGui.treeNode("Tokens and preview values")) {
                for (var entry : inspections) {
                    ImGui.textUnformatted(entry.token());
                    if (ImGui.isItemHovered()) tooltip(entry.help());
                    ImGui.textWrapped(entry.help());
                }
                ImGui.treePop();
            }
        }
    }
}
