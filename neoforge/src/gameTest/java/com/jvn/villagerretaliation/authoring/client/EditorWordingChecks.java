package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview;
import com.jvn.villagerretaliation.authoring.preview.PreviewWordPools;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.resources.ResourceLocation;

final class EditorWordingChecks {
    static int run() throws IOException {
        poolCreationPreservesTheOwningVariant();
        existingPoolsKeepTheirIdentityAndUndo();
        failedCreationLeavesBothFilesUntouched();
        professionOverridesAreOptionalAndScoped();
        professionPoolsKeepInheritedRules();
        examplesFollowProfessionAndDraftChanges();
        writingSlotsExcludeConfiguration();
        return 7;
    }

    private static AuthoringWorkspace workspace() throws IOException { return AuthoringWorkspace.create(Files.createTempDirectory("wording-editor")); }
    private static AuthoringDocument add(AuthoringWorkspace workspace, String path, String json) {
        var document = new AuthoringDocument(DefinitionKind.fromPath(path), path, JsonParser.parseString(json).getAsJsonObject());
        workspace.add(document); return document;
    }
    private static AuthoringDocument tree(AuthoringWorkspace workspace) {
        return add(workspace, "data/test/dialogue_trees/en_us/hello.json", """
                {"id":"test:hello","nodes":{"start":{"variants":[{"id":"warm","text":"Welcome back.","weight":3}],"responses":[]}}}
                """);
    }

    private static void poolCreationPreservesTheOwningVariant() throws IOException {
        var workspace = workspace(); var tree = tree(workspace); var before = tree.json();
        var pool = EditorDialogueAuthoring.createPool(workspace, tree, "/nodes/start/variants/0/text", "Welcome wording", "en_us");
        check(pool.resourcePath().equals("data/test/dialogue_words/en_us/welcome_wording.json"), "plain names use the owner's namespace");
        check(pool.value("/values/0/text").getAsString().equals("Welcome back."), "pool creation carries the existing words");
        EditorDialogueAuthoring.addWording(workspace, pool, false, "en_us");
        check(!pool.value("/values/0/id").equals(pool.value("/values/1/id")), "new alternatives have stable names for safe future inheritance");
        check(tree.value("/nodes/start/variants/0/weight").getAsInt() == 3, "pool reuse preserves variant selection settings");
        tree.undo(); check(tree.json().equals(before), "undo restores the original literal line");
    }

    private static void existingPoolsKeepTheirIdentityAndUndo() throws IOException {
        var workspace = workspace(); var tree = tree(workspace); var before = tree.json();
        var pool = add(workspace, "data/test/dialogue_words/en_us/welcome.json", "{\"values\":[\"Good to see you.\"]}");
        var original = pool.json();
        EditorDialogueAuthoring.usePool(workspace, tree, "/nodes/start/variants/0/text", "test:welcome", "en_us");
        check(tree.value("/nodes/start/variants/0/text").getAsString().equals("{word:test:welcome}"), "guided reuse writes the runtime reference");
        check(pool.json().equals(original), "using a pool leaves shared wording untouched");
        tree.undo(); check(tree.json().equals(before), "pool selection uses normal document undo");
    }

    private static void failedCreationLeavesBothFilesUntouched() throws IOException {
        var workspace = workspace(); var tree = tree(workspace); var before = tree.json(); int count = workspace.documents().size();
        try { EditorDialogueAuthoring.createPool(workspace, tree, "/nodes/start/variants/0/text", "bad:name?", "en_us"); throw new AssertionError("invalid ID accepted"); }
        catch (IllegalArgumentException expected) { }
        check(tree.json().equals(before) && count == workspace.documents().size(), "failed pool creation is atomic");
        try { EditorDialogueAuthoring.usePool(workspace, tree, "/nodes/start/variants/0/text", "test:missing", "en_us"); throw new AssertionError("missing pool accepted"); }
        catch (IllegalArgumentException expected) { }
        check(tree.json().equals(before), "missing pools do not replace existing wording");
        var pool = EditorDialogueAuthoring.createPool(workspace, tree, "/nodes/start/variants/0/text", "Words", "en_us");
        var other = tree.json();
        try { EditorDialogueAuthoring.createPool(workspace, tree, "/nodes/start/variants/0/text", "Words", "en_us"); throw new AssertionError("duplicate accepted"); }
        catch (IllegalArgumentException expected) { }
        check(tree.json().equals(other) && pool.value("/values/0/text").getAsString().equals("Welcome back."), "existing pool files cannot be overwritten");
    }

    private static void professionOverridesAreOptionalAndScoped() throws IOException {
        var workspace = workspace();
        var shared = add(workspace, "data/test/world_campaigns/village/dialogue/fr_fr/global/messages/hello.json", """
                {"messages":[{"key":"hello","text":"Bonjour","show_for_babies":false},{"key":"other","text":"Au revoir"}]}
                """);
        var original = shared.json();
        check(EditorDialogueAuthoring.previewPointer(shared, "/messages/0/arguments/body").equals("/messages/0"),
                "preview starts at the complete message when a writing slot is selected");
        check(EditorDialogueAuthoring.previewPointer(shared, "").equals("/messages/0"),
                "opening a wording file previews the initially displayed message");
        var target = EditorDialogueAuthoring.professionOverride(workspace, shared, "/messages/0/text", "farmer");
        check(target.document().resourcePath().equals("data/test/world_campaigns/village/dialogue/fr_fr/professions/farmer/messages/hello.json"),
                "profession overrides keep the campaign, language and relative grouping");
        check(target.document().value("/messages").getAsJsonArray().size() == 1 && shared.json().equals(original),
                "only the chosen message is overridden");
        var same = EditorDialogueAuthoring.professionOverride(workspace, shared, "/messages/0", "farmer");
        check(same.equals(target), "an existing override opens without duplication");
        EditorDialogueAuthoring.professionOverride(workspace, shared, "/messages/1", "farmer");
        check(target.document().value("/messages").getAsJsonArray().size() == 2, "another override preserves existing profession messages");
    }

    private static void professionPoolsKeepInheritedRules() throws IOException {
        var workspace = workspace();
        var shared = add(workspace, "data/test/dialogue_words/en_us/welcome.json", """
                {"values":[{"id":"ordinary","text":"Hello"},{"id":"warm","text":"Welcome","weight":4,"priority":10,"conditions":[{"type":"mood","moods":["content"]}]}]}
                """);
        var message = add(workspace, "data/test/dialogue/en_us/professions/farmer/messages/hello.json",
                "{\"messages\":[{\"key\":\"hello\",\"text\":\"<gold>{word:test:welcome}</gold>\"}]}");
        var original = shared.json();
        var pool = EditorDialogueAuthoring.professionPool(workspace, message, "/messages/0/text", "test:welcome", "en_us");
        EditorWordPoolPreview.overrideWording(workspace, pool, "warm", "Good to see you.");
        var resolved = PreviewWordPools.lookupDefinitions(workspace, message, "en_us", AuthoringDialoguePreview::defaultWordPool)
                .apply(ResourceLocation.parse("test:authoring/professions/farmer/test/welcome"));
        var entry = resolved.entries().stream().filter(value -> value.id().equals("warm")).findFirst().orElseThrow();
        check(entry.text().equals("Good to see you.") && entry.weight() == 4 && entry.priority() == 10 && entry.conditions().size() == 1,
                "profession wording preserves inherited conditions, priority and weight");
        check(shared.json().equals(original) && message.value("/messages/0/text").getAsString().startsWith("<gold>"),
                "customizing a profession leaves shared words and styling intact");
    }

    private static void examplesFollowProfessionAndDraftChanges() throws IOException {
        var workspace = workspace(); var tree = tree(workspace);
        var shared = add(workspace, "data/test/dialogue/en_us/global/messages/hello.json", "{\"messages\":[{\"key\":\"hello\",\"text\":\"Hello.\"}]}");
        add(workspace, "data/test/dialogue_templates/en_us/simple.json", "{\"parameters\":[\"body\"],\"text\":\"<gold>{body}</gold>\"}");
        var farmer = add(workspace, "data/test/dialogue/en_us/professions/farmer/messages/hello.json",
                "{\"messages\":[{\"key\":\"hello\",\"template\":\"test:simple\",\"arguments\":{\"body\":\"{word:test:farm}\"}}]}");
        var pool = add(workspace, "data/test/dialogue_words/en_us/farm.json", "{\"values\":[\"Morning from the farm.\"]}");
        var reference = JsonParser.parseString("{\"key\":\"hello\"}");
        check(EditorDialogueAuthoring.example(workspace, tree, reference, "en_us", "farmer", 1).text().contains("Morning from the farm."),
                "profession examples assemble the effective wording and pools");
        check(EditorDialogueAuthoring.example(workspace, tree, reference, "en_us", "librarian", 1).text().equals("Hello."),
                "professions without overrides fall back to shared wording");
        pool.edit("Revise wording", root -> root.getAsJsonArray("values").set(0, new com.google.gson.JsonPrimitive("Welcome to the farm.")));
        check(EditorDialogueAuthoring.example(workspace, tree, reference, "en_us", "farmer", 1).text().contains("Welcome to the farm."),
                "examples use unsaved pool edits");
    }

    private static void writingSlotsExcludeConfiguration() {
        var message = JsonParser.parseString("{\"key\":\"hello\",\"template\":\"test:style\",\"arguments\":{\"body\":\"Hello\"},\"conditions\":[{\"type\":\"profession\",\"profession\":\"farmer\"}]}");
        var slots = EditorDialogueAuthoring.wording(message, "/messages/0");
        check(slots.size() == 1 && slots.getFirst().pointer().equals("/messages/0/arguments/body"), "writing slots exclude keys and selection configuration");
        check(EditorDialogueAuthoring.pools("<gold>{word:test:one} {word:test:two|fallback=Hello}</gold>").size() == 2,
                "guided pool links support composed and fallback wording");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
