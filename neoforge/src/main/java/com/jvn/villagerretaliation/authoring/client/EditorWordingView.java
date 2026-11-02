package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview;
import com.jvn.villagerretaliation.authoring.preview.PreviewWordPools;
import com.jvn.villagerretaliation.authoring.preview.WordPoolPreview;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

final class EditorWordingView {
    interface Fields { void render(JsonElement value, String pointer, String label); }
    private final ImString search = new ImString(256), poolName = new ImString("New wording", 256);
    private final Map<String, WordPoolPreview.Result> examples = new HashMap<>();
    private AuthoringWorkspace owner;
    private long revision = -1, seed;
    private String selectedPool = "", profession = "farmer";

    private WordPoolPreview.Result example(AuthoringWorkspace workspace, AuthoringDocument document, JsonElement value, String locale, String voice) {
        if (owner != workspace || revision != workspace.revision()) {
            owner = workspace; revision = workspace.revision(); examples.clear();
        }
        String key = document.resourcePath() + value + locale + voice + seed;
        return examples.computeIfAbsent(key, ignored -> EditorDialogueAuthoring.example(workspace, document, value, locale, voice, seed));
    }

    String graphExample(AuthoringWorkspace workspace, AuthoringDocument document, JsonElement value, String locale, String voice) {
        try { return EditorDialogueAuthoring.plain(example(workspace, document, value, locale, voice).text()); }
        catch (IllegalArgumentException exception) { return "Example unavailable: " + exception.getMessage(); }
    }

    private void assembled(AuthoringWorkspace workspace, AuthoringDocument document, JsonElement value, String locale) {
        ImGui.textUnformatted("Assembled example");
        WordPoolPreview.Result result;
        try { result = example(workspace, document, value, locale, EditorDialogueLinks.profession(document)); }
        catch (IllegalArgumentException exception) {
            ImGui.textWrapped("Example unavailable: " + exception.getMessage());
            ImGui.textWrapped("Repair the wording in advanced settings, then try the preview again.");
            return;
        }
        ImGui.textWrapped(EditorDialogueAuthoring.plain(result.text()));
        for (String issue : result.diagnostics()) ImGui.textWrapped(issue);
        if (ImGui.button("Another example")) { seed++; examples.clear(); }
        ImGui.textWrapped("Example uses neutral mood and reputation. Preview offers the full condition and variation checks.");
        ImGui.separator();
    }

    void render(AuthoringWorkspace workspace, AuthoringDocument document, JsonObject snapshot, String pointer, String locale,
            Fields fields, EditorResourceView.Fields advanced, Consumer<EditorReferences.Location> open,
            Consumer<String> select, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable refresh) {
        ImGui.textUnformatted(EditorOpenFiles.label(new EditorOpenFiles.Entry(document, "", false)));
        if (snapshot.has("messages") && snapshot.get("messages").isJsonArray()) {
            var messages = snapshot.getAsJsonArray("messages");
            String anchor = EditorDialogueLinks.anchor(document, pointer);
            int chosen = anchor.isEmpty() ? 0 : Integer.parseInt(anchor.split("/")[2]);
            if (chosen >= messages.size()) chosen = 0;
            ImGui.textUnformatted("Choose wording");
            ImGui.setNextItemWidth(-1);
            String title = messages.isEmpty() ? "No wording in this file" : messageName(messages.get(chosen), chosen);
            if (ImGui.beginCombo("##choose_wording", title)) {
                for (int i = 0; i < messages.size(); i++) {
                    String target = "/messages/" + i;
                    if (ImGui.selectable(messageName(messages.get(i), i) + "##" + i, chosen == i))
                        run.accept(() -> select.accept(target));
                }
                ImGui.endCombo();
            }
            if (messages.isEmpty()) { ImGui.textWrapped("This file has no shared messages."); return; }
            String target = "/messages/" + chosen;
            var message = messages.get(chosen);
            ImGui.beginChild("wording_writer", 0, 0, false);
            assembled(workspace, document, message, locale);
            for (var wording : EditorDialogueAuthoring.wording(message, target))
                wording(workspace, document, wording, locale, fields, open, run, refresh);
            if (EditorDialogueLinks.profession(document).isEmpty()) {
                ImGui.separator();
                ImGui.textUnformatted("Profession voice");
                ImGui.textWrapped("Start with shared wording. Add an override only when this profession needs its own voice.");
                ImGui.setNextItemWidth(-1);
                if (ImGui.beginCombo("##override_profession", EditorContentBrowser.humanize(profession))) {
                    for (String option : EditorDialogueAuthoring.PROFESSIONS)
                        if (ImGui.selectable(EditorContentBrowser.humanize(option), profession.equals(option))) profession = option;
                    ImGui.endCombo();
                }
                if (ImGui.button("Add profession override")) run.accept(() -> {
                    var location = EditorDialogueAuthoring.professionOverride(workspace, document, target, profession);
                    refresh.run(); open.accept(location);
                });
            } else ImGui.textWrapped("This wording belongs to " + EditorContentBrowser.humanize(EditorDialogueLinks.profession(document))
                    + ". Other messages continue to use shared defaults. Shared pools remain shared until you create a wording override.");
            if (EditorWidgets.section("Advanced message settings")) advanced.render(message, target, false);
            ImGui.endChild();
            return;
        }
        ImGui.beginChild("wording_writer", 0, 0, false);
        boolean pool = document.resourcePath().contains("/dialogue_words/");
        ImGui.textWrapped(pool ? "Alternative wording for one part of a conversation. Selection rules belong to each entry."
                : "Shared presentation controls how wording is styled. Keep the writing slots in braces so messages can supply their own words.");
        if (pool) {
            assembled(workspace, document, new com.google.gson.JsonPrimitive(EditorWordPoolPreview.templates(document).getFirst()), locale);
            if (ImGui.button("Add alternative wording")) run.accept(() -> {
                select.accept(EditorDialogueAuthoring.addWording(workspace, document, false, locale)); refresh.run();
            });
            EditorWidgets.sameLineFor("Add conditional wording");
            if (ImGui.button("Add conditional wording")) run.accept(() -> {
                select.accept(EditorDialogueAuthoring.addWording(workspace, document, true, locale)); refresh.run();
            });
            if (snapshot.has("extends")) {
                ImGui.textWrapped("Customize a named entry to preserve its inherited rules. Unnamed entries remain shared. Advanced settings can assign names in the parent pool.");
                var id = EditorReferences.identifiers(document).stream().filter(value -> value.contains(":") && !value.startsWith("data/")).findFirst().orElseThrow();
                var resolved = PreviewWordPools.lookupDefinitions(workspace, document, locale, AuthoringDialoguePreview::defaultWordPool).apply(ResourceLocation.parse(id));
                for (var entry : resolved.entries()) {
                    if (entry.id().isBlank() || snapshot.has("text_overrides") && snapshot.getAsJsonObject("text_overrides").has(entry.id())
                            || entry.source().startsWith(id + "/values/") || entry.source().startsWith(id + "/groups/")) continue;
                    ImGui.pushID(entry.id());
                    ImGui.textWrapped(entry.text());
                    if (ImGui.button("Customize inherited wording")) run.accept(() -> {
                        EditorWordPoolPreview.overrideWording(workspace, document, entry.id(), entry.text()); refresh.run();
                    });
                    ImGui.popID();
                }
            }
        }
        for (var wording : EditorDialogueAuthoring.wording(snapshot, ""))
            wording(workspace, document, wording, locale, fields, open, run, refresh);
        if (snapshot.has("extends")) {
            ImGui.separator();
            ImGui.textUnformatted(pool ? "Inherits wording and selection rules" : "Uses a shared presentation");
            String id = snapshot.get("extends").getAsString();
            for (var location : EditorReferences.definitions(workspace, document, "/extends", id, locale))
                if (ImGui.button("Open " + EditorDialogueAuthoring.readable(id))) open.accept(location);
        }
        if (EditorWidgets.section(pool ? "Selection rules and advanced settings" : "Presentation rules and writing slots"))
            advanced.render(snapshot, "", false);
        ImGui.endChild();
    }

    private static String messageName(JsonElement value, int index) {
        String key = value.isJsonObject() ? DialogueGraph.string(value.getAsJsonObject(), "key") : "";
        return key.isBlank() ? "Wording " + (index + 1) : EditorDialogueAuthoring.readable(key);
    }

    private void wording(AuthoringWorkspace workspace, AuthoringDocument document, EditorDialogueAuthoring.Wording wording, String locale,
            Fields fields, Consumer<EditorReferences.Location> open, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable refresh) {
        ImGui.pushID(wording.pointer());
        var pools = EditorDialogueAuthoring.pools(wording.text());
        if (pools.isEmpty()) fields.render(JsonPointers.get(document.json(), wording.pointer()), wording.pointer(), wording.label());
        else {
            ImGui.textUnformatted(wording.label());
            ImGui.textWrapped("Pools are shared. Editing one changes every line that uses it.");
            ImGui.textWrapped(EditorDialogueAuthoring.plain(example(workspace, document, new com.google.gson.JsonPrimitive(wording.text()), locale,
                    EditorDialogueLinks.profession(document)).text()));
            for (String pool : pools) {
                if (ImGui.button("Edit pool: " + EditorDialogueAuthoring.readable(pool))) {
                    var locations = EditorReferences.definitions(workspace, document, wording.pointer(), "{word:" + pool + "}", locale);
                    if (!locations.isEmpty()) open.accept(locations.getFirst());
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip(pool);
                if (!EditorDialogueLinks.profession(document).isEmpty() && ImGui.button("Customize pool for this profession##" + pool))
                    run.accept(() -> {
                        var target = EditorDialogueAuthoring.professionPool(workspace, document, wording.pointer(), pool, locale);
                        refresh.run(); open.accept(new EditorReferences.Location(target, ""));
                    });
            }
            if (ImGui.button("Add alternative wording")) {
                var locations = EditorReferences.definitions(workspace, document, wording.pointer(), "{word:" + pools.getFirst() + "}", locale);
                if (!locations.isEmpty()) open.accept(locations.getFirst());
            }
            if (EditorWidgets.section("Edit sentence assembly"))
                fields.render(JsonPointers.get(document.json(), wording.pointer()), wording.pointer(), "Sentence assembly");
        }
        if (!document.resourcePath().contains("/dialogue_words/"))
            poolPicker(workspace, document, wording.pointer(), locale, open, run, refresh, pools.isEmpty(), true);
        ImGui.separator();
        ImGui.popID();
    }

    void poolPicker(AuthoringWorkspace workspace, AuthoringDocument document, String pointer, String locale,
            Consumer<EditorReferences.Location> open, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable refresh, boolean literal, boolean alternatives) {
        if (ImGui.button("Use a pool")) { search.set(""); selectedPool = ""; ImGui.openPopup("Choose a phrase pool"); }
        if (literal && alternatives && ImGui.button("Add alternative wording##pool")) { search.set(""); selectedPool = ""; ImGui.openPopup("Choose a phrase pool"); }
        if (ImGui.beginPopup("Choose a phrase pool")) {
            ImGui.textWrapped("A pool replaces this writing slot with alternative wording. Shared presentation styles still apply.");
            ImGui.setNextItemWidth(320);
            ImGui.inputTextWithHint("##pool_search", "Find wording...", search);
            var ids = EditorWordPoolPreview.poolIds(workspace, document, locale).stream().sorted()
                    .filter(id -> (id + " " + EditorDialogueAuthoring.readable(id)).toLowerCase(java.util.Locale.ROOT)
                            .contains(search.get().toLowerCase(java.util.Locale.ROOT))).toList();
            ImGui.beginChild("pool_options", 400, 180, true);
            for (String id : ids) {
                if (ImGui.selectable(EditorDialogueAuthoring.readable(id) + "##" + id, selectedPool.equals(id))) selectedPool = id;
                if (ImGui.isItemHovered()) ImGui.setTooltip(id);
            }
            ImGui.endChild();
            if (!selectedPool.isEmpty()) {
                ImGui.textWrapped(EditorDialogueAuthoring.plain(example(workspace, document, new com.google.gson.JsonPrimitive("{word:" + selectedPool + "}"), locale,
                        EditorDialogueLinks.profession(document)).text()));
                if (ImGui.button("Use selected pool")) {
                    String id = selectedPool;
                    run.accept(() -> { EditorDialogueAuthoring.usePool(workspace, document, pointer, id, locale); refresh.run(); });
                    ImGui.closeCurrentPopup();
                }
            }
            if (literal) {
                ImGui.separator();
                ImGui.textWrapped("Or turn this line into a new pool, then add alternative wording there.");
                ImGui.setNextItemWidth(320);
                ImGui.inputText(EditorWidgets.field("Pool name"), poolName);
                if (ImGui.button("Create pool from this line")) {
                    String name = poolName.get();
                    run.accept(() -> {
                        var pool = EditorDialogueAuthoring.createPool(workspace, document, pointer, name, locale);
                        refresh.run(); open.accept(new EditorReferences.Location(pool, ""));
                    });
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.endPopup();
        }
    }
}
