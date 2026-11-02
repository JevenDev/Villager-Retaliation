package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview;
import com.jvn.villagerretaliation.authoring.preview.PreviewConditions;
import com.jvn.villagerretaliation.authoring.preview.PreviewWordPools;
import com.jvn.villagerretaliation.authoring.preview.WordPoolPreview;
import com.jvn.villagerretaliation.dialogue.DialogueWordHistory;
import com.jvn.villagerretaliation.dialogue.DialogueWordPool;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

final class EditorWordPoolPreview {
    private final ImString template = new ImString(8192);
    private final ImString filter = new ImString(256);
    private final ImInt intensity = new ImInt(50);
    private final PreviewConditions conditions = new PreviewConditions();
    private DialogueWordHistory history = new DialogueWordHistory();
    private AuthoringDocument owner;
    private WordPoolPreview.Result result;
    private List<WordPoolPreview.Row> rows = List.of();
    private List<WordPoolPreview.Finding> findings = List.of();
    private List<String> matchingPools = List.of();
    private String roleFilter = "all", selectedPool = "";
    private long seed;
    private long templateRevision = -1;
    private List<String> availableTemplates = List.of();
    private String fingerprint = "";
    private String templateFingerprint = "", templateProblem = "", wordingId = "", wordingProblem = "";
    private final ImString wording = new ImString(4096);

    static List<String> templates(AuthoringDocument document) {
        return templates(null, document);
    }

    static List<String> templates(AuthoringWorkspace workspace, AuthoringDocument document) {
        String path = DefinitionKind.unscopedPath(document.resourcePath());
        if (path.matches("data/[^/]+/dialogue_words/[^/]+/.+[.]json")) {
            String[] parts = path.split("/", 5);
            return List.of("{word:" + parts[1] + ":" + parts[4].substring(0, parts[4].length() - 5) + "}");
        }
        var values = new java.util.LinkedHashSet<String>();
        var documents = workspace == null ? List.of(document) : workspace.documents();
        collect(com.jvn.villagerretaliation.authoring.core.AuthoringPresentationTemplates.expand(document, documents), values);
        return List.copyOf(values);
    }

    static java.util.Set<String> poolIds(AuthoringWorkspace workspace, AuthoringDocument owner, String locale) {
        var ids = new java.util.TreeSet<String>();
        com.jvn.villagerretaliation.dialogue.resources.DialogueWordPoolResources.ids(net.minecraft.client.Minecraft.getInstance().getSingleplayerServer(), locale)
                .forEach(id -> ids.add(id.toString()));
        String scope = DefinitionKind.contentRoot(owner.resourcePath());
        for (var document : workspace.documents()) {
            String root = DefinitionKind.contentRoot(document.resourcePath());
            if (root.split("/").length == 4 && !root.equals(scope)) continue;
            String path = DefinitionKind.unscopedPath(document.resourcePath());
            if (!path.matches("data/[^/]+/dialogue_words/[^/]+/.+[.]json")) continue;
            String[] parts = path.split("/", 5);
            if (parts[3].equals(locale) || parts[3].equals("en_us")) ids.add(parts[1] + ":" + parts[4].substring(0, parts[4].length() - 5));
        }
        return java.util.Set.copyOf(ids);
    }

    private static void collect(JsonElement value, java.util.Set<String> templates) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().contains("{word")) templates.add(value.getAsString());
        else if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> collect(child, templates));
        else if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(entry -> collect(entry.getValue(), templates));
    }

    void render(AuthoringWorkspace workspace, AuthoringDocument document, String locale) {
        if (!EditorWidgets.section("Phrase pool inspector")) return;
        String presentationFingerprint = workspace.documents().stream().filter(value -> value.resourcePath().contains("/dialogue_templates/"))
                .map(value -> value.resourcePath() + value.revision()).collect(java.util.stream.Collectors.joining());
        if (owner != document || templateRevision != document.revision() || !templateFingerprint.equals(presentationFingerprint)) {
            int previousTemplate = availableTemplates.indexOf(template.get());
            try {
                availableTemplates = templates(workspace, document); templateProblem = "";
                if (owner == document && previousTemplate >= 0 && previousTemplate < availableTemplates.size())
                    template.set(availableTemplates.get(previousTemplate));
            }
            catch (IllegalArgumentException exception) { availableTemplates = List.of(); templateProblem = exception.getMessage(); }
            templateRevision = document.revision();
            templateFingerprint = presentationFingerprint;
        }
        if (!templateProblem.isEmpty()) ImGui.textWrapped(templateProblem);
        var templates = availableTemplates;
        if (owner != document) {
            owner = document;
            wordingId = ""; wordingProblem = "";
            template.set(templates.isEmpty() ? "" : templates.getFirst());
            result = null; rows = List.of(); findings = List.of(); matchingPools = List.of(); history = new DialogueWordHistory(); fingerprint = "";
        }
        if (!templates.isEmpty() && ImGui.beginCombo("Wording from this file", template.get())) {
            for (int i = 0; i < templates.size(); i++)
                if (ImGui.selectable(templates.get(i) + "##word_template_" + i, template.get().equals(templates.get(i)))) template.set(templates.get(i));
            ImGui.endCombo();
        }
        ImGui.inputTextMultiline(EditorWidgets.field("Template##word_preview"), template, -1, 70);
        if (ImGui.beginCombo("Mood##word_preview", conditions.mood().serializedName())) {
            for (var mood : com.jvn.villagerretaliation.mood.VillagerMood.values())
                if (ImGui.selectable(mood.serializedName(), mood == conditions.mood())) conditions.mood(mood, intensity.get());
            ImGui.endCombo();
        }
        if (ImGui.sliderInt("Mood intensity##word_preview", intensity.getData(), 0, 100)) conditions.mood(conditions.mood(), intensity.get());
        var saved = conditions.snapshot();
        var tier = com.jvn.villagerretaliation.reputation.VillagerReputationLevel.valueOf(saved.get("tier").getAsString());
        if (ImGui.beginCombo("Reputation tier##word_preview", tier.name())) {
            for (var candidate : com.jvn.villagerretaliation.reputation.VillagerReputationLevel.values()) {
                if (ImGui.selectable(candidate.name(), tier == candidate)) {
                    int score = candidate == com.jvn.villagerretaliation.reputation.VillagerReputationLevel.NEUTRAL ? 0
                            : com.jvn.villagerretaliation.reputation.VillagerReputationLevel.PROFILE.tiers().stream()
                                    .filter(value -> value.value() == candidate).findFirst().orElseThrow().threshold().getAsInt();
                    conditions.reputation(score, candidate);
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.beginCombo("Speaking style##word_preview", conditions.speakingStyle().isEmpty() ? "Unknown" : conditions.speakingStyle())) {
            if (ImGui.selectable("Unknown", conditions.speakingStyle().isEmpty())) conditions.speakingStyle("");
            for (String style : com.jvn.villagerretaliation.dialogue.SpeakingStyle.STYLES)
                if (ImGui.selectable(style, style.equals(conditions.speakingStyle()))) conditions.speakingStyle(style);
            ImGui.endCombo();
        }
        String current = template.get() + locale + conditions.snapshot() + workspace.documents().stream()
                .filter(value -> value.resourcePath().contains("/dialogue_words/") || value.resourcePath().contains("/dialogue_contexts/"))
                .map(value -> value.resourcePath() + value.revision()).collect(java.util.stream.Collectors.joining());
        if (!current.equals(fingerprint)) { result = null; rows = List.of(); findings = List.of(); matchingPools = List.of(); history = new DialogueWordHistory(); fingerprint = current; }
        var cache = new LinkedHashMap<ResourceLocation, DialogueWordPool>();
        var lookup = PreviewWordPools.lookupDefinitions(workspace, document, locale, AuthoringDialoguePreview::defaultWordPool);
        java.util.function.Function<ResourceLocation, DialogueWordPool> pools = id -> cache.computeIfAbsent(id, lookup);
        conditions.contexts(PreviewWordPools.contexts(workspace, document));
        ImGui.textWrapped("Preview uses unsaved pools and named contexts. Unknown requirements stay ineligible. Shared variants are chosen from all pools in the line before wording is rolled.");
        if (ImGui.button("Roll wording")) result = WordPoolPreview.inspect(template.get(), pools, conditions, ++seed, history, "preview/" + locale);
        EditorWidgets.sameLineFor("Build variation matrix");
        if (ImGui.button("Build variation matrix")) { rows = WordPoolPreview.matrix(template.get(), pools, conditions, ++seed); findings = List.of(); result = null; }
        EditorWidgets.sameLineFor("Clear recent wording");
        if (ImGui.button("Clear recent wording")) history = new DialogueWordHistory();
        EditorWidgets.sameLineFor("Analyze coverage");
        if (ImGui.button("Analyze coverage")) {
            var coverage = WordPoolPreview.coverage(template.get(), pools, conditions, ++seed);
            rows = coverage.rows(); findings = coverage.findings(); result = null;
        }
        if (EditorWidgets.section("Insert phrase pool")) {
            if (ImGui.beginCombo("Pool role filter", roleFilter)) {
                for (String role : List.of("all", "literal", "sentence", "opening_clause", "address", "adjective_phrase"))
                    if (ImGui.selectable(role, role.equals(roleFilter))) { roleFilter = role; matchingPools = List.of(); }
                ImGui.endCombo();
            }
            if (ImGui.button("List matching pools")) matchingPools = poolIds(workspace, document, locale).stream().sorted()
                    .filter(id -> roleFilter.equals("all") || pools.apply(ResourceLocation.parse(id)).role().equals(roleFilter)).toList();
            if (!matchingPools.isEmpty()) {
                if (ImGui.beginCombo("Phrase pool", selectedPool)) {
                    for (String id : matchingPools) if (ImGui.selectable(id, id.equals(selectedPool))) selectedPool = id;
                    ImGui.endCombo();
                }
                if (ImGui.button("Append pool reference") && matchingPools.contains(selectedPool))
                    template.set(template.get() + (template.get().isEmpty() || template.get().endsWith(" ") ? "" : " ") + "{word:" + selectedPool + "}");
            }
        }
        if (!findings.isEmpty() && EditorWidgets.section("Coverage findings (" + findings.size() + ")")) {
            ImGui.textWrapped("These findings describe sampled contexts and boundaries, rather than proving an entry can never be used.");
            for (var finding : findings) ImGui.textWrapped(finding.kind() + ": " + finding.message());
        }
        if (!rows.isEmpty()) {
            ImGui.textWrapped(rows.size() + " combinations at mood intensity " + conditions.moodIntensity() + ". Coverage also samples intensity and reputation boundaries. Build again to reroll.");
            ImGui.inputText("Filter combinations", filter);
            if (ImGui.beginTable("##word_matrix", 4, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.ScrollY, 0, 250)) {
                ImGui.tableSetupColumn("Mood / intensity"); ImGui.tableSetupColumn("Reputation / score"); ImGui.tableSetupColumn("Style"); ImGui.tableSetupColumn("Wording"); ImGui.tableHeadersRow();
                for (int i = 0; i < rows.size(); i++) {
                    var row = rows.get(i);
                    String searchable = row.mood() + " " + row.reputation() + " " + row.style() + " " + row.result().text();
                    if (!searchable.toLowerCase(java.util.Locale.ROOT).contains(filter.get().toLowerCase(java.util.Locale.ROOT))) continue;
                    ImGui.tableNextRow(); ImGui.tableNextColumn();
                    if (ImGui.selectable(row.mood().serializedName() + " (" + row.intensity() + ")##word_row_" + i, result == row.result())) result = row.result();
                    ImGui.tableNextColumn(); ImGui.textUnformatted(row.reputation().name() + " (" + row.score() + ")");
                    ImGui.tableNextColumn(); ImGui.textUnformatted(row.style());
                    ImGui.tableNextColumn(); ImGui.textWrapped(row.result().text());
                }
                ImGui.endTable();
            }
        }
        if (result != null) details(workspace, document, result);
    }

    static void overrideWording(AuthoringWorkspace workspace, AuthoringDocument document, String id, String text) {
        var changed = document.json();
        if (!changed.has("extends") || !id.matches("[a-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("Select a named entry in an inherited pool");
        var overrides = changed.has("text_overrides") ? changed.getAsJsonObject("text_overrides") : new com.google.gson.JsonObject();
        overrides.addProperty(id, text);
        changed.add("text_overrides", overrides);
        EditorMutation.validate(workspace, document, changed);
        document.edit("Override wording", root -> root.add("text_overrides", overrides.deepCopy()));
    }

    private static boolean canOverride(AuthoringDocument document, WordPoolPreview.EntryResult entry) {
        String path = DefinitionKind.unscopedPath(document.resourcePath());
        if (!path.matches("data/[^/]+/dialogue_words/[^/]+/.+[.]json") || !document.json().has("extends") || entry.entry().id().isEmpty()) return false;
        String[] parts = path.split("/", 5);
        String id = parts[1] + ":" + parts[4].substring(0, parts[4].length() - 5);
        return entry.pool().toString().equals(id) && !entry.entry().source().startsWith(id + "/values/")
                && !entry.entry().source().startsWith(id + "/groups/");
    }

    private void details(AuthoringWorkspace workspace, AuthoringDocument document, WordPoolPreview.Result result) {
        ImGui.separator();
        ImGui.textWrapped(result.text());
        for (String issue : result.diagnostics()) ImGui.textWrapped(issue);
        for (var selection : result.selections()) {
            ImGui.textWrapped(selection.pool() + ": priority " + selection.entry().priority() + ", weight " + selection.entry().weight()
                    + (selection.omitted() ? ", optional clause omitted" : selection.fallback() ? ", neutral fallback" : ", matching conditions")
                    + (selection.group().isEmpty() ? "" : ", group " + selection.group() + ", variant " + selection.variant())
                    + (selection.avoidedRecent() ? ", recent wording avoided" : ""));
        }
        for (var entry : result.entries()) {
            if (ImGui.treeNode(entry.pool() + " " + (entry.entry().source().isEmpty() ? "/values/" + entry.index() : entry.entry().source()) + (entry.selected() ? " [selected]" : entry.eligible() ? " [eligible]" : " [excluded]"))) {
                ImGui.textWrapped(entry.entry().text());
                ImGui.textWrapped("Conditions: " + entry.conditions() + ", priority " + entry.entry().priority() + ", weight " + entry.entry().weight());
                for (var condition : entry.entry().conditions()) ImGui.textWrapped(condition.toString());
                if (canOverride(document, entry)) {
                    if (ImGui.smallButton("Edit inherited wording")) {
                        wordingId = entry.entry().id(); wording.set(entry.entry().text()); wordingProblem = "";
                    }
                    if (wordingId.equals(entry.entry().id())) {
                        ImGui.inputTextMultiline("Wording##inherited", wording, -1, 70);
                        ImGui.textWrapped("Conditions, priority, weight and tone stay inherited.");
                        if (ImGui.button("Apply wording override")) {
                            try { overrideWording(workspace, document, wordingId, wording.get()); wordingId = ""; wordingProblem = ""; }
                            catch (IllegalArgumentException | IllegalStateException exception) { wordingProblem = exception.getMessage(); }
                        }
                        if (!wordingProblem.isEmpty()) ImGui.textWrapped(wordingProblem);
                    }
                }
                ImGui.treePop();
            }
        }
    }
}
