package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class EditorDialoguePool {
    private AuthoringDocument owner;
    private long revision = -1;
    private boolean supported, resetScroll;
    private String selection = "";
    private List<Entry> entries = List.of();
    private final Map<Integer, Integer> variations = new HashMap<>();

    record Entry(int index, JsonObject value, String field) {
        String pointer() { return "/lines/" + index; }
        JsonArray lines() { return value.getAsJsonArray(field); }
        String textPointer(int variation) { return pointer() + "/" + field + "/" + variation + (lines().get(variation).isJsonObject() ? "/text" : ""); }
        JsonElement text(int variation) { return lines().get(variation).isJsonObject() ? lines().get(variation).getAsJsonObject().get("text") : lines().get(variation); }
    }

    interface FieldRenderer { void render(JsonElement value, String path, String label, boolean text); }

    boolean supports(AuthoringDocument document, JsonObject snapshot) {
        if (owner == document && revision == document.revision()) return supported;
        if (owner != document) { variations.clear(); selection = ""; resetScroll = true; }
        owner = document; revision = document.revision();
        supported = recognizes(document, snapshot);
        var result = new ArrayList<Entry>();
        if (supported) for (int i = 0; i < snapshot.getAsJsonArray("lines").size(); i++) {
            var value = snapshot.getAsJsonArray("lines").get(i).getAsJsonObject();
            result.add(new Entry(i, value, value.has("variants") ? "variants" : "lines"));
        }
        entries = List.copyOf(result);
        return supported;
    }

    static boolean recognizes(AuthoringDocument document, JsonObject snapshot) {
        String path = DefinitionKind.unscopedPath(document.resourcePath());
        if (document.kind() != DefinitionKind.RESOURCE || !path.matches("data/[^/]+/dialogue/.+")
                || !snapshot.has("lines") || !snapshot.get("lines").isJsonArray()) return false;
        for (var item : snapshot.getAsJsonArray("lines")) {
            if (!item.isJsonObject()) return false;
            var value = item.getAsJsonObject();
            String field = value.has("variants") ? "variants" : "lines";
            if (!value.has(field) || !value.get(field).isJsonArray()) return false;
            for (var line : value.getAsJsonArray(field)) {
                var text = line.isJsonObject() ? line.getAsJsonObject().get("text") : line;
                if (text == null || !text.isJsonPrimitive() || !text.getAsJsonPrimitive().isString()) return false;
            }
        }
        return true;
    }

    void render(AuthoringDocument document, String selection, float scale, FieldRenderer fields, Consumer<String> inspect,
                Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable refresh) {
        ImGui.textUnformatted("Dialogue pool / " + entries.size() + " entries");
        if (ImGui.button("Add entry")) run.accept(() -> { int index = append(document, -1); refresh.run(); inspect.accept("/lines/" + index); });
        ImGui.textWrapped("Each entry has its own selection rules. One line variation is chosen when it plays.");
        ImGui.beginChild("pool_cards", 0, 0, false);
        float gap = ImGui.getStyle().getItemSpacingX();
        int columns = ImGui.getContentRegionAvailX() >= 760 * scale ? 2 : 1;
        float width = Math.max(1, (ImGui.getContentRegionAvailX() - gap * (columns - 1)) / columns);
        float height = 536 * scale;
        if (resetScroll) { ImGui.setScrollY(0); resetScroll = false; }
        if (!this.selection.equals(selection)) {
            this.selection = selection;
            for (var entry : entries) if (selection.equals(entry.pointer()) || selection.startsWith(entry.pointer() + "/")) {
                ImGui.setScrollY((entry.index() / columns) * (height + ImGui.getStyle().getItemSpacingY()));
                break;
            }
        }
        var clipper = new imgui.ImGuiListClipper();
        try {
            clipper.begin((entries.size() + columns - 1) / columns, height + ImGui.getStyle().getItemSpacingY());
            while (clipper.step()) for (int row = clipper.getDisplayStart(); row < clipper.getDisplayEnd(); row++) {
                for (int column = 0; column < columns && row * columns + column < entries.size(); column++) {
                    var entry = entries.get(row * columns + column);
                    if (column > 0) ImGui.sameLine();
                    boolean selected = selection.equals(entry.pointer()) || selection.startsWith(entry.pointer() + "/");
                    ImGui.pushStyleColor(ImGuiCol.Border, selected ? 0xffa6b99b : ImGui.getColorU32(ImGuiCol.Border));
                    boolean visible = ImGui.beginChild("pool_entry_" + entry.index(), width, height, true);
                    ImGui.popStyleColor();
                    if (visible) card(document, entry, fields, inspect, run, refresh);
                    ImGui.endChild();
                }
            }
        } finally { clipper.end(); clipper.destroy(); }
        ImGui.endChild();
    }

    private void card(AuthoringDocument document, Entry entry, FieldRenderer fields, Consumer<String> inspect,
                      Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable refresh) {
        ImGui.textUnformatted("Entry " + (entry.index() + 1));
        EditorWidgets.sameLineFor("Details");
        if (ImGui.smallButton("Details")) run.accept(() -> inspect.accept(entry.pointer()));
        if (ImGui.isItemHovered()) ImGui.setTooltip("Edit conditions and other selection rules in Details");
        for (String field : List.of("id", "request", "weight")) if (entry.value().has(field))
            fields.render(entry.value().get(field), entry.pointer() + "/" + field,
                    field.equals("id") ? "ID" : field.equals("request") ? "Request" : "Weight", false);
        int count = entry.lines().size();
        int chosen = Math.clamp(variations.getOrDefault(entry.index(), 0), 0, Math.max(0, count - 1));
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##variation", count == 0 ? "No line variations" : "Line " + (chosen + 1) + " of " + count)) {
            for (int i = 0; i < count; i++) {
                String preview = entry.text(i).getAsString().replace('\n', ' ');
                if (preview.length() > 65) preview = preview.substring(0, 65) + "...";
                if (ImGui.selectable("Line " + (i + 1) + ": " + preview + "##" + i, chosen == i)) {
                    int target = i;
                    run.accept(() -> variations.put(entry.index(), target));
                }
            }
            ImGui.endCombo();
        }
        if (count > 0) fields.render(entry.text(chosen), entry.textPointer(chosen), "Dialogue text", true);
        if (ImGui.smallButton("Add line")) run.accept(() -> {
            variations.put(entry.index(), appendLine(document, entry.index(), -1)); refresh.run();
        });
        if (count > 0) {
            EditorWidgets.sameLineFor("Duplicate line");
            if (ImGui.smallButton("Duplicate line")) run.accept(() -> {
                variations.put(entry.index(), appendLine(document, entry.index(), chosen)); refresh.run();
            });
            EditorWidgets.sameLineFor("Remove line");
            ImGui.beginDisabled(count < 2);
            if (ImGui.smallButton("Remove line")) run.accept(() -> {
                removeLine(document, entry.index(), chosen); refresh.run();
            });
            ImGui.endDisabled();
            if (entry.lines().get(chosen).isJsonObject() && ImGui.smallButton("Line settings"))
                run.accept(() -> inspect.accept(entry.pointer() + "/" + entry.field() + "/" + chosen));
        }
        ImGui.separator();
        if (ImGui.smallButton("Duplicate entry")) run.accept(() -> {
            int added = append(document, entry.index()); refresh.run(); inspect.accept("/lines/" + added);
        });
        EditorWidgets.sameLineFor("Remove entry");
        if (ImGui.smallButton("Remove entry")) run.accept(() -> {
            remove(document, entry.index());
            variations.clear(); refresh.run(); inspect.accept("");
        });
    }

    static int append(AuthoringDocument document, int copy) {
        int index = document.value("/lines").getAsJsonArray().size();
        document.edit(copy < 0 ? "Add dialogue pool entry" : "Duplicate dialogue pool entry", root -> {
            var entries = root.getAsJsonArray("lines");
            JsonObject entry;
            if (copy >= 0) entry = entries.get(copy).getAsJsonObject().deepCopy();
            else {
                entry = new JsonObject(); entry.addProperty("request", "greeting"); entry.addProperty("weight", 1);
                var lines = new JsonArray(); lines.add("New dialogue."); entry.add("lines", lines);
            }
            String base = copy >= 0 && entry.has("id") ? entry.get("id").getAsString() + "_copy" : "line";
            entry.addProperty("id", uniqueId(entries, base)); entries.add(entry);
        });
        return index;
    }

    static int appendLine(AuthoringDocument document, int entry, int copy) {
        int[] added = new int[1];
        document.edit(copy < 0 ? "Add dialogue variation" : "Duplicate dialogue variation", root -> {
            var value = root.getAsJsonArray("lines").get(entry).getAsJsonObject();
            String field = value.has("variants") ? "variants" : "lines";
            var lines = value.getAsJsonArray(field);
            JsonElement line;
            if (copy >= 0) line = lines.get(copy).deepCopy();
            else if (field.equals("lines")) line = new com.google.gson.JsonPrimitive("New dialogue.");
            else { var variant = new JsonObject(); variant.addProperty("text", "New dialogue."); line = variant; }
            if (line.isJsonObject()) line.getAsJsonObject().addProperty("id", uniqueId(lines, "line"));
            added[0] = lines.size(); lines.add(line);
        });
        return added[0];
    }

    static void remove(AuthoringDocument document, int entry) {
        EditorArrayOrder.edit(document, "/lines", "Remove dialogue pool entry", entries -> entries.remove(entry));
    }

    static void removeLine(AuthoringDocument document, int entry, int index) {
        var value = document.value("/lines/" + entry).getAsJsonObject();
        String field = value.has("variants") ? "variants" : "lines";
        EditorArrayOrder.edit(document, "/lines/" + entry + "/" + field, "Remove dialogue variation", lines -> {
            if (lines.size() < 2) throw new IllegalArgumentException("Keep at least one line variation");
            lines.remove(index);
        });
    }

    private static String uniqueId(JsonArray entries, String base) {
        var ids = new java.util.HashSet<String>();
        for (var entry : entries) if (entry.isJsonObject() && entry.getAsJsonObject().has("id"))
            ids.add(entry.getAsJsonObject().get("id").getAsString());
        String id = base;
        for (int i = 2; ids.contains(id); i++) id = base + "_" + i;
        return id;
    }
}
