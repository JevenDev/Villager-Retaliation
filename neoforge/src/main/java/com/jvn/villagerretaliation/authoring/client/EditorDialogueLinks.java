package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import java.util.List;
import java.util.function.Consumer;

final class EditorDialogueLinks {
    private final EditorCampaignGraph model = new EditorCampaignGraph();
    private String profession = "";

    String selectedProfession() { return profession; }

    static boolean supports(AuthoringDocument document) {
        if (document == null) return false;
        if (List.of(DefinitionKind.TREE, DefinitionKind.FORCED, DefinitionKind.CONVERSATION).contains(document.kind())) return true;
        return DefinitionKind.unscopedPath(document.resourcePath()).matches("data/[^/]+/(dialogue|dialogue_words|dialogue_templates|dialogue_styles|dialogue_contexts)/.+[.]json");
    }

    static String profession(AuthoringDocument document) {
        var match = java.util.regex.Pattern.compile("/dialogue/[^/]+/professions/([^/]+)/").matcher(DefinitionKind.unscopedPath(document.resourcePath()));
        return match.find() ? match.group(1) : "";
    }

    static String anchor(AuthoringDocument document, String pointer) {
        if (!DefinitionKind.unscopedPath(document.resourcePath()).contains("/dialogue/")) return pointer;
        var match = java.util.regex.Pattern.compile("^/messages/[0-9]+").matcher(pointer);
        return match.find() ? match.group() : "";
    }

    List<EditorCampaignGraph.Link> outgoing(AuthoringWorkspace workspace, EditorContentBrowser browser,
            AuthoringDocument document, String pointer, String locale) {
        var graph = model.get(workspace, browser, locale);
        var node = graph.nodes().stream().filter(value -> value.document() == document).findFirst().orElse(null);
        String parent = anchor(document, pointer);
        return node == null ? List.of() : graph.outgoing().get(node).stream()
                .filter(link -> parent.isEmpty() || link.sourcePointer().equals(parent) || link.sourcePointer().startsWith(parent + "/")).toList();
    }

    List<EditorReferences.Location> targets(AuthoringWorkspace workspace, EditorContentBrowser browser,
            AuthoringDocument document, String pointer, String locale) {
        return visible(outgoing(workspace, browser, document, pointer, locale), profession).stream()
                .map(link -> new EditorReferences.Location(link.to().document(), link.targetPointer())).distinct().toList();
    }

    static List<EditorCampaignGraph.Link> visible(List<EditorCampaignGraph.Link> links, String profession) {
        return links.stream().filter(link -> profession(link.to().document()).isEmpty()
                || profession(link.to().document()).equals(profession)).toList();
    }

    static String label(EditorReferences.Location location) {
        var document = location.document();
        String path = DefinitionKind.unscopedPath(document.resourcePath());
        if (document.kind() != DefinitionKind.RESOURCE) return "Flow: " + document.id();
        if (path.contains("/dialogue/")) {
            String parent = anchor(document, location.pointer());
            var value = document.value(parent);
            String key = value.isJsonObject() ? DialogueGraph.string(value.getAsJsonObject(), "key") : "";
            String voice = profession(document);
            return (voice.isEmpty() ? "Shared wording" : EditorContentBrowser.humanize(voice) + " wording")
                    + (key.isEmpty() ? "" : ": " + key);
        }
        String category = path.contains("/dialogue_templates/") ? "Template" : path.contains("/dialogue_words/") ? "Phrase pool"
                : path.contains("/dialogue_styles/") ? "Style" : path.contains("/dialogue_contexts/") ? "Conditions" : "Resource";
        String id = EditorReferences.identifiers(document).stream().filter(value -> value.contains(":") && !value.startsWith("data/")).findFirst().orElse(document.id());
        return category + ": " + id;
    }

    void render(AuthoringWorkspace workspace, EditorContentBrowser browser, AuthoringDocument document, String pointer,
            String locale, Consumer<EditorReferences.Location> open, Runnable map) {
        if (!supports(document)) return;
        var graph = model.get(workspace, browser, locale);
        var node = graph.nodes().stream().filter(value -> value.document() == document).findFirst().orElse(null);
        if (node == null) return;
        var outgoing = outgoing(workspace, browser, document, pointer, locale);
        var incoming = graph.incoming().get(node);
        if (outgoing.isEmpty() && incoming.isEmpty()) return;
        if (!EditorWidgets.section("Linked dialogue", ImGuiTreeNodeFlags.DefaultOpen)) return;
        var professions = outgoing.stream().map(link -> profession(link.to().document())).filter(value -> !value.isEmpty()).distinct().sorted().toList();
        if (!professions.isEmpty()) {
            ImGui.setNextItemWidth(-1);
            if (ImGui.beginCombo("##dialogue_profession", profession.isEmpty() ? "Shared defaults" : EditorContentBrowser.humanize(profession))) {
                if (ImGui.selectable("Shared defaults", profession.isEmpty())) profession = "";
                for (String option : professions) if (ImGui.selectable(EditorContentBrowser.humanize(option), profession.equals(option))) profession = option;
                ImGui.endCombo();
            }
            ImGui.textWrapped("Graph examples use this voice. Messages without an override keep shared wording.");
        }
        ImGui.pushID("dialogue_outgoing");
        int index = 0;
        for (var link : visible(outgoing, profession)) {
            var target = new EditorReferences.Location(link.to().document(), link.targetPointer());
            ImGui.pushID(index++);
            if (ImGui.selectable(label(target))) open.accept(target);
            if (ImGui.isItemHovered()) ImGui.setTooltip(target.document().resourcePath() + target.pointer());
            ImGui.popID();
        }
        ImGui.popID();
        if (!incoming.isEmpty() && EditorWidgets.section("Used by (" + incoming.size() + ")")) {
            ImGui.pushID("dialogue_incoming");
            index = 0;
            for (var link : incoming) {
                var target = new EditorReferences.Location(link.from().document(), link.sourcePointer());
                ImGui.pushID(index++);
                if (ImGui.selectable(label(target))) open.accept(target);
                if (ImGui.isItemHovered()) ImGui.setTooltip(target.document().resourcePath() + target.pointer());
                ImGui.popID();
            }
            ImGui.popID();
        }
        if (ImGui.button("Show dialogue map")) map.run();
        ImGui.separator();
    }
}
