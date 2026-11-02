package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

final class EditorDialogueLinkChecks {
    private EditorDialogueLinkChecks() { }

    static int run() throws IOException {
        tokensResolveResourcesWithoutAuthoredIds();
        localizedResourcesRespectScopeAndFallback();
        templatesKeepTheirResourceTypes();
        messageSlotsExposeOptionalProfessionOverrides();
        liveIndexesFollowEditsAndUndo();
        graphAndInspectorKeepTheirSelection();
        shippedGreetingsLinkThroughSharedResources();
        templateWordingPreviewsKeepMessageKeysReadOnly();
        writingSurfaceAndTabsKeepTheirContext();
        return 9;
    }

    private static void writingSurfaceAndTabsKeepTheirContext() throws IOException {
        var workspace = workspace();
        var tree = add(workspace, "data/test/dialogue_trees/en_us/hello.json",
                "{\"id\":\"test:hello\",\"nodes\":{\"start\":{\"lines\":[\"Hello\"],\"responses\":[{\"label\":\"Hi\",\"end\":true}]}}}");
        var card = EditorGraph.cards(workspace, tree, "en_us").getFirst();
        check(EditorDialogueWriting.ownsWording(card, tree, "/nodes/start/lines"), "Details directs speech to its graph writing surface");
        check(!EditorDialogueWriting.ownsWording(card, tree, "/nodes/start"), "node rules remain editable in Details");
        check(!EditorDialogueWriting.ownsWording(card, tree, "/nodes/start/responses"), "choice structure remains editable in Details");
        var shared = add(workspace, "data/test/dialogue/en_us/global/hello.json", "{}");
        var farmer = add(workspace, "data/test/dialogue/en_us/professions/farmer/hello.json", "{}");
        check(!EditorOpenFiles.label(new EditorOpenFiles.Entry(shared, "", false))
                .equals(EditorOpenFiles.label(new EditorOpenFiles.Entry(farmer, "", false))), "shared and profession tabs distinguish identical filenames");
        check(card.width() >= 400 && card.blocks().getFirst().fields().getFirst().height() >= 168, "new dialogue gives prose room to breathe");
    }

    private static AuthoringWorkspace workspace() throws IOException { return AuthoringWorkspace.create(Files.createTempDirectory("dialogue-links")); }
    private static AuthoringDocument add(AuthoringWorkspace workspace, String path, String json) {
        var document = new AuthoringDocument(DefinitionKind.fromPath(path), path, JsonParser.parseString(json).getAsJsonObject());
        workspace.add(document); return document;
    }
    private static EditorReferences.Location location(AuthoringDocument document, String pointer) { return new EditorReferences.Location(document, pointer); }

    private static void tokensResolveResourcesWithoutAuthoredIds() throws IOException {
        var workspace = workspace();
        var tree = add(workspace, "data/test/dialogue_trees/en_us/shared.json", """
                {"id":"test:shared","nodes":{"hello":{"lines":["<warm>{word:test:hello}</warm> {word:test:other}"]}}}
                """);
        var words = add(workspace, "data/test/dialogue_words/en_us/hello.json", "{\"values\":[\"Hello\"]}");
        var other = add(workspace, "data/test/dialogue_words/en_us/other.json", "{\"values\":[\"Welcome\"]}");
        var style = add(workspace, "data/test/dialogue_styles/warm.json", "{\"tag\":\"warm\"}");
        var refs = EditorReferences.definitions(workspace, tree, "/nodes/hello/lines/0",
                "<warm>{word:test:hello}</warm> {word:test:other}", "en_us");
        check(refs.size() == 3 && refs.containsAll(List.of(location(words, ""), location(other, ""), location(style, ""))),
                "each phrase token and custom style links to its actual file");
        check(EditorReferences.definitions(workspace, tree, "/nodes/hello/lines/0", "test:hello", "en_us").isEmpty(),
                "ordinary prose is not mistaken for a resource reference");
        check(EditorReferences.usages(workspace, location(style, ""), "en_us").equals(List.of(location(tree, "/nodes/hello/lines/0"))),
                "closing tags do not duplicate a style usage");
    }

    private static void localizedResourcesRespectScopeAndFallback() throws IOException {
        var workspace = workspace();
        var owner = add(workspace, "data/test/world_campaigns/one/dialogue/en_us/hello.json", "{}");
        var english = add(workspace, "data/test/dialogue_words/en_us/hello.json", "{\"values\":[\"Hello\"]}");
        var scoped = add(workspace, "data/test/world_campaigns/one/dialogue_words/en_us/hello.json", "{\"values\":[\"Welcome\"]}");
        var french = add(workspace, "data/test/dialogue_words/fr_fr/hello.json", "{\"values\":[\"Bonjour\"]}");
        add(workspace, "data/test/world_campaigns/two/dialogue_words/en_us/hello.json", "{\"values\":[\"Other\"]}");
        check(EditorReferences.definitions(workspace, owner, "/text", "{word:test:hello}", "en_us").equals(List.of(location(scoped, ""))),
                "the owning campaign wins within the same locale");
        check(EditorReferences.definitions(workspace, owner, "/text", "{word:test:hello}", "fr_fr").equals(List.of(location(french, ""))),
                "localized wording wins before English fallback");
        var global = add(workspace, "data/test/dialogue/en_us/global.json", "{}");
        check(EditorReferences.definitions(workspace, global, "/text", "{word:test:hello}", "de_de").equals(List.of(location(english, ""))),
                "global English fallback does not leak another campaign");
    }

    private static void templatesKeepTheirResourceTypes() throws IOException {
        var workspace = workspace();
        var message = add(workspace, "data/test/dialogue/en_us/messages.json", "{\"messages\":[{\"template\":\"test:shared\"}]}");
        var presentation = add(workspace, "data/test/dialogue_templates/en_us/shared.json", "{\"extends\":\"test:base\"}");
        var base = add(workspace, "data/test/dialogue_templates/en_us/base.json", "{\"text\":\"Hello\"}");
        var scene = add(workspace, "data/test/scenes/shared.json", "{\"id\":\"test:scene\",\"template\":\"test:shared\"}");
        var encounter = add(workspace, "data/test/encounters/shared.json", "{\"schema\":\"villagerretaliation:encounter/v1\",\"id\":\"test:shared\"}");
        check(EditorReferences.definitions(workspace, message, "/messages/0/template", "test:shared", "en_us").equals(List.of(location(presentation, ""))),
                "dialogue template IDs stay separate from encounter IDs");
        check(EditorReferences.definitions(workspace, scene, "/template", "test:shared", "en_us").equals(List.of(location(encounter, ""))),
                "encounter navigation retains its existing behavior");
        check(EditorReferences.definitions(workspace, presentation, "/extends", "test:base", "en_us").equals(List.of(location(base, ""))),
                "template inheritance links to the parent inspector");
        var context = add(workspace, "data/test/dialogue_contexts/quiet.json", "{\"id\":\"test:quiet\",\"conditions\":[]}");
        message.edit("Set context", root -> root.add("conditions", JsonParser.parseString("[{\"type\":\"context\",\"id\":\"test:quiet\"}]")));
        check(EditorReferences.definitions(workspace, message, "/conditions/0/id", "test:quiet", "en_us").equals(List.of(location(context, ""))),
                "named condition IDs link to their definition");
    }

    private static void messageSlotsExposeOptionalProfessionOverrides() throws IOException {
        var workspace = workspace();
        var tree = add(workspace, "data/test/dialogue_trees/en_us/shared.json", """
                {"id":"test:shared","nodes":{"hello":{"lines":[{"key":"hello"}]}}}
                """);
        var shared = add(workspace, "data/test/dialogue/en_us/global/messages/shared.json",
                "{\"messages\":[{\"key\":\"hello\",\"template\":\"test:greeting\",\"arguments\":{\"body\":\"Hello.\"}}]}");
        var farmer = add(workspace, "data/test/dialogue/en_us/professions/farmer/messages/shared.json",
                "{\"messages\":[{\"key\":\"hello\",\"template\":\"test:greeting\",\"arguments\":{\"body\":\"How are the crops?\"}}]}");
        add(workspace, "data/test/world_campaigns/other/dialogue/en_us/global/messages/shared.json",
                "{\"messages\":[{\"key\":\"hello\",\"text\":\"Unrelated\"}]}");
        var targets = EditorReferences.definitions(workspace, tree, "/nodes/hello/lines/0/key", "hello", "en_us");
        check(targets.equals(List.of(location(shared, "/messages/0/arguments"), location(farmer, "/messages/0/arguments"))),
                "a shared message slot exposes defaults and profession overrides without another flow");
        var links = new EditorDialogueLinks();
        var outgoing = links.outgoing(workspace, new EditorContentBrowser(), tree, "/nodes/hello/lines", "en_us");
        check(EditorDialogueLinks.visible(outgoing, "").size() == 1 && EditorDialogueLinks.visible(outgoing, "farmer").size() == 2,
                "the inspector shows shared defaults alongside the selected profession");
        check(EditorDialogueLinks.anchor(farmer, "/messages/0/arguments/body").equals("/messages/0"),
                "selecting wording keeps its template and condition links visible");
        check(EditorDialogueLinks.label(targets.get(1)).equals("Farmer wording: hello"), "profession overrides have a readable navigation label");
        check(EditorReferences.usages(workspace, location(shared, "/messages/0/arguments"), "en_us").equals(List.of(location(tree, "/nodes/hello/lines/0/key"))),
                "a message declaration never counts as its own usage");
    }

    private static void liveIndexesFollowEditsAndUndo() throws IOException {
        var workspace = workspace();
        var tree = add(workspace, "data/test/dialogue_trees/en_us/shared.json", "{\"id\":\"test:shared\",\"nodes\":{\"hello\":{\"text\":\"{word:test:first}\"}}}");
        var first = add(workspace, "data/test/dialogue_words/en_us/first.json", "{\"values\":[\"First\"]}");
        var second = add(workspace, "data/test/dialogue_words/en_us/second.json", "{\"extends\":\"test:first\"}");
        var browser = new EditorContentBrowser(); var links = new EditorDialogueLinks();
        var original = links.outgoing(workspace, browser, tree, "/nodes/hello", "en_us");
        check(original.getFirst().to().document() == first, "the first link uses the current wording");
        tree.edit("Change pool", root -> root.getAsJsonObject("nodes").getAsJsonObject("hello").addProperty("text", "{word:test:second}"));
        check(links.outgoing(workspace, browser, tree, "/nodes/hello", "en_us").getFirst().to().document() == second,
                "unsaved references refresh the graph index");
        tree.undo();
        check(links.outgoing(workspace, browser, tree, "/nodes/hello", "en_us").getFirst().to().document() == first,
                "undo restores the prior resource links");
        check(EditorReferences.definitions(workspace, second, "/extends", "test:first", "en_us").equals(List.of(location(first, ""))),
                "inherited pools link to their parent");
    }

    private static void templateWordingPreviewsKeepMessageKeysReadOnly() throws IOException {
        var workspace = workspace();
        var tree = add(workspace, "data/test/dialogue_trees/en_us/shared.json",
                "{\"id\":\"test:shared\",\"nodes\":{\"hello\":{\"lines\":[{\"key\":\"hello\"}]}}}");
        var message = add(workspace, "data/test/dialogue/en_us/global/messages/shared.json",
                "{\"messages\":[{\"key\":\"hello\",\"template\":\"test:hello\",\"arguments\":{\"body\":\"Hello, friend.\"}}]}");
        add(workspace, "data/test/dialogue_templates/en_us/hello.json",
                "{\"parameters\":[\"body\"],\"text\":\"<gold>{body}</gold>\"}");
        var before = message.json();
        var field = EditorGraph.cards(workspace, tree, "en_us").getFirst().blocks().getFirst().fields().getFirst();
        check(field.value().getAsString().equals("<gold>Hello, friend.</gold>") && !field.editable(),
                "template-backed graph text previews the assembled presentation without editing the localization key");
        check(field.document() == message && field.pointer().equals("/messages/0") && message.json().equals(before),
                "shared text retains its source for inspector navigation");
    }

    private static void graphAndInspectorKeepTheirSelection() throws IOException {
        var workspace = workspace();
        var first = workspace.createDocument(DefinitionKind.TREE, "test:first");
        var second = workspace.createDocument(DefinitionKind.TREE, "test:second");
        var history = new EditorNavigation();
        var origin = new EditorNavigation.Location(first, first.resourcePath(), 0, false, "/nodes/start/lines", 73, -21, .8f, 40, 120);
        history.record(workspace, origin);
        history.record(workspace, new EditorNavigation.Location(second, second.resourcePath(), 0, false, "/nodes/start", 0, 0, 1, 0));
        check(history.target(-1).equals(origin), "back restores the selected graph field, camera position, zoom and inspector scroll");
    }

    private static void shippedGreetingsLinkThroughSharedResources() throws IOException {
        var workspace = workspace();
        var paths = List.of("dialogue_trees/en_us/greetings/shared.json", "dialogue/en_us/global/messages/greetings/shared.json",
                "dialogue/en_us/professions/farmer/messages/greetings/shared.json", "dialogue_templates/en_us/greetings/ordinary.json",
                "dialogue_templates/en_us/greetings/styled.json", "dialogue_words/en_us/greetings/professions/farmer/ordinary.json",
                "dialogue_styles/greetings/warm.json");
        for (String path : paths) {
            var stream = EditorDialogueLinkChecks.class.getResourceAsStream("/data/villagerretaliation/" + path);
            if (stream == null) throw new IOException("Missing shipped greeting resource " + path);
            try (var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                var file = "data/villagerretaliation/" + path;
                workspace.add(new AuthoringDocument(DefinitionKind.fromPath(file), file, root));
            }
        }
        var graph = new EditorCampaignGraph().get(workspace, new EditorContentBrowser(), "en_us");
        check(graph.links().stream().anyMatch(link -> link.from().document().kind() == DefinitionKind.TREE
                && link.to().path().contains("/professions/farmer/messages/")), "the shipped shared flow links to farmer wording");
        check(graph.links().stream().anyMatch(link -> link.sourcePointer().equals("/messages/0/template")
                && link.to().path().contains("/dialogue_templates/")), "profession wording opens its shared presentation template");
        check(graph.links().stream().anyMatch(link -> link.sourcePointer().equals("/messages/0/arguments/greeting")
                && link.to().path().contains("/dialogue_words/")), "profession wording opens its phrase pool");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
