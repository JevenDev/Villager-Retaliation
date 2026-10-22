package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class EditorResourceView {
    record Item(String pointer, String title, String summary, JsonElement value) { }
    record Section(String key, String label, String pointer, JsonElement value, List<Item> items) { }
    interface Fields { void render(JsonElement value, String pointer, boolean settings); }

    private AuthoringDocument owner;
    private long revision = -1;
    private List<Section> sections = List.of();
    private String active = "", selected = "", lastPointer, query = "";
    private List<Item> filtered = List.of(), filterSource;
    private ImString search;
    private boolean reveal, reset, tools;

    List<Section> sections(AuthoringDocument document, JsonObject snapshot) {
        if (owner == document && revision == document.revision()) return sections;
        boolean changed = owner != document;
        owner = document; revision = document.revision();
        var result = new ArrayList<Section>();
        boolean flatLocale = document.resourcePath().contains("/locales/") && !snapshot.has("messages");
        if (flatLocale) {
            var items = new ArrayList<Item>();
            for (var field : snapshot.entrySet()) if (!List.of("schema", "metadata", "locale").contains(field.getKey()))
                items.add(item("/" + JsonPointers.escape(field.getKey()), field.getKey(), field.getValue()));
            result.add(new Section("translations", "Translations", "", snapshot, List.copyOf(items)));
        } else {
            var order = new java.util.LinkedHashSet<>(List.of("steps", "actors", "waves", "members", "variants", "messages", "openings", "closings", "pacify", "options", "lines", "values"));
            order.addAll(snapshot.keySet());
            for (String key : order) {
                var value = snapshot.get(key);
                if (value == null || key.equals("metadata") || !value.isJsonArray() && !value.isJsonObject()) continue;
                String path = "/" + JsonPointers.escape(key);
                var items = new ArrayList<Item>();
                if (value.isJsonArray()) for (int i = 0; i < value.getAsJsonArray().size(); i++)
                    items.add(item(path + "/" + i, "Entry " + (i + 1), value.getAsJsonArray().get(i)));
                else for (var field : value.getAsJsonObject().entrySet())
                    items.add(item(path + "/" + JsonPointers.escape(field.getKey()), field.getKey(), field.getValue()));
                result.add(new Section(key, EditorContentBrowser.humanize(key), path, value, List.copyOf(items)));
            }
        }
        result.add(new Section("settings", "File settings", "", snapshot, List.of(new Item("", "File settings", "", snapshot))));
        sections = List.copyOf(result);
        if (changed) {
            active = sections.getFirst().key(); selected = first(sections.getFirst());
            lastPointer = null; if (search != null) search.set(""); tools = false; reset = true;
        }
        var section = current();
        if (!section.items().isEmpty() && section.items().stream().noneMatch(item -> item.pointer().equals(selected))) selected = first(section);
        filterSource = null;
        return sections;
    }

    static String title(AuthoringDocument document, JsonObject snapshot) {
        String schema = scalar(snapshot, "schema");
        if (schema.equals("villagerretaliation:scene/v1")) return "Scene";
        if (schema.equals("villagerretaliation:encounter/v1")) return "Encounter";
        String path = DefinitionKind.unscopedPath(document.resourcePath());
        if (path.contains("/locales/")) return "Translations";
        if (path.contains("/dialogue_words/")) return "Word pool";
        if (path.contains("/dialogue/")) return "Dialogue resource";
        if (path.contains("/campaign_scenarios/")) return "Scenario";
        return "Resource";
    }

    private static Item item(String path, String fallback, JsonElement value) {
        String label = fallback, summary = "";
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            for (String key : List.of("id", "alias", "key", "name")) if (!scalar(object, key).isBlank()) { label = scalar(object, key); break; }
            summary = scalar(object, "type");
            if (summary.contains(":")) summary = summary.substring(summary.indexOf(':') + 1);
            summary = EditorContentBrowser.humanize(summary);
            if (object.has("next")) summary += " / Next: " + scalar(object, "next");
            if (summary.isBlank()) for (String key : List.of("text", "label", "entity", "request"))
                if (!scalar(object, key).isBlank()) { summary = scalar(object, key); break; }
            if (summary.isBlank() && object.has("lines") && object.get("lines").isJsonArray()) summary = object.getAsJsonArray("lines").size() + " line variations";
        } else if (value.isJsonPrimitive()) summary = value.getAsString();
        else if (value.isJsonArray()) summary = value.getAsJsonArray().size() + " entries";
        summary = summary.replace('\n', ' ');
        return new Item(path, label, summary.length() > 160 ? summary.substring(0, 160) + "..." : summary, value);
    }

    private static String scalar(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonPrimitive() ? value.get(key).getAsString() : "";
    }

    private static String first(Section section) { return section.items().isEmpty() ? section.pointer() : section.items().getFirst().pointer(); }
    private Section current() { return sections.stream().filter(value -> value.key().equals(active)).findFirst().orElse(sections.getLast()); }
    void settings() { active = "settings"; selected = ""; reveal = true; }

    List<Item> matches(Section section, String needle) {
        if (filterSource == section.items() && query.equals(needle)) return filtered;
        query = needle; filterSource = section.items();
        String lowered = needle.strip().toLowerCase(Locale.ROOT);
        filtered = lowered.isEmpty() ? section.items() : section.items().stream()
                .filter(item -> (item.title() + " " + item.summary() + " " + item.pointer()).toLowerCase(Locale.ROOT).contains(lowered)).toList();
        return filtered;
    }

    private void locate(String pointer) {
        if (pointer.isEmpty()) { settings(); return; }
        for (var section : sections) if (!section.key().equals("settings")) for (var item : section.items())
            if (pointer.equals(item.pointer()) || pointer.startsWith(item.pointer() + "/")) {
                active = section.key(); selected = item.pointer(); reveal = true; return;
            }
    }

    void render(AuthoringDocument document, JsonObject snapshot, String pointer, float scale, Fields fields,
                Consumer<Section> collectionTools, Consumer<String> select, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        sections(document, snapshot);
        if (search == null) search = new ImString(256);
        if (lastPointer != null && !lastPointer.equals(pointer)) locate(pointer);
        else if (lastPointer == null && !pointer.isEmpty()) locate(pointer);
        lastPointer = pointer;
        ImGui.textUnformatted(title(document, snapshot));
        var section = current();
        ImGui.setNextItemWidth(Math.min(320 * scale, ImGui.getContentRegionAvailX()));
        if (ImGui.beginCombo("##resource_section", section.label() + " (" + section.items().size() + ")")) {
            for (var option : sections) if (ImGui.selectable(option.label() + " (" + option.items().size() + ")", option == section))
                run.accept(() -> { active = option.key(); selected = first(option); search.set(""); reveal = true; select.accept(selected); });
            ImGui.endCombo();
        }
        EditorWidgets.sameLineFor("File settings");
        if (ImGui.button("File settings")) run.accept(() -> { settings(); select.accept(""); });
        if (!section.key().equals("settings")) {
            EditorWidgets.sameLineFor("Collection tools");
            if (ImGui.button("Collection tools")) tools = !tools;
            if (tools) collectionTools.accept(section);
        }
        float width = ImGui.getContentRegionAvailX(), height = ImGui.getContentRegionAvailY();
        boolean stacked = width < 620 * scale;
        float outlineWidth = stacked ? width : Math.min(300 * scale, width * .35f);
        ImGui.beginChild("resource_outline", outlineWidth, stacked ? Math.min(190 * scale, height * .35f) : 0, true);
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##resource_search", "Find an entry...", search);
        var items = matches(section, search.get());
        ImGui.beginChild("resource_entries", 0, 0, false);
        float rowHeight = ImGui.getTextLineHeightWithSpacing() * 3 + 12 * scale;
        if (reset) { ImGui.setScrollY(0); reset = false; }
        if (reveal) {
            for (int i = 0; i < items.size(); i++) if (items.get(i).pointer().equals(selected)) ImGui.setScrollY(i * (rowHeight + ImGui.getStyle().getItemSpacingY()));
            reveal = false;
        }
        var clipper = new imgui.ImGuiListClipper();
        try {
            clipper.begin(items.size(), rowHeight + ImGui.getStyle().getItemSpacingY());
            while (clipper.step()) for (int i = clipper.getDisplayStart(); i < clipper.getDisplayEnd(); i++) {
                var item = items.get(i);
                float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY(), available = ImGui.getContentRegionAvailX();
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(item.pointer().equals(selected) ? ImGuiCol.Header : ImGuiCol.FrameBg));
                if (ImGui.button("##resource_" + item.pointer(), available, rowHeight))
                    run.accept(() -> { selected = item.pointer(); reveal = true; select.accept(selected); });
                ImGui.popStyleColor();
                var draw = ImGui.getWindowDrawList();
                draw.pushClipRect(x + 8, y, x + available - 8, y + rowHeight, true);
                draw.addText(x + 8, y + 6, ImGui.getColorU32(ImGuiCol.Text), item.title());
                draw.addText(x + 8, y + 6 + ImGui.getTextLineHeightWithSpacing(), ImGui.getColorU32(ImGuiCol.TextDisabled), item.summary());
                draw.popClipRect();
                if (ImGui.isItemHovered()) ImGui.setTooltip(item.title() + "\n" + item.summary() + "\n" + item.pointer());
            }
            clipper.end();
        } finally { clipper.destroy(); }
        if (items.isEmpty()) ImGui.textWrapped(section.items().isEmpty() ? "This section is empty. Use Collection tools to add entries." : "No matching entries.");
        ImGui.dummy(0, 0);
        ImGui.endChild(); ImGui.endChild();
        if (!stacked) ImGui.sameLine();
        ImGui.beginChild("resource_fields", 0, 0, true);
        var item = section.items().stream().filter(value -> value.pointer().equals(selected)).findFirst().orElse(null);
        ImGui.textWrapped(item == null ? section.label() : item.title());
        if (item != null && !item.summary().isBlank()) ImGui.textWrapped(item.summary());
        ImGui.separator();
        fields.render(item == null ? section.value() : item.value(), item == null ? section.pointer() : item.pointer(), section.key().equals("settings"));
        ImGui.dummy(0, 0);
        ImGui.endChild();
    }
}
