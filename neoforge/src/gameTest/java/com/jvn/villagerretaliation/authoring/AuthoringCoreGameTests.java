package com.jvn.villagerretaliation.authoring;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringValidation;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("authoring")
@PrefixGameTestTemplate(false)
public final class AuthoringCoreGameTests {
    private AuthoringCoreGameTests() {
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void editsPreserveUnknownFieldsAndUndoBothDefinitionAndLayout(GameTestHelper helper) {
        JsonObject original = json("""
                {"id":"test:dialogue","extension":{"number":1.2500,"nested":[true,{"future":"value"}]},
                 "entries":[{"id":"talk","label":"Talk","start":"start"}],
                 "nodes":{"start":{"lines":["Hello"],"end":true,"extension_node":{"enabled":true}}}}
                """);
        AuthoringDocument document = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/dialogue.json", original);
        document.edit("Text", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").addProperty("line", "Changed"));
        helper.assertTrue(document.json().get("extension").equals(original.get("extension")), "editing retains unknown root payloads");
        document.editMetadata("Position", metadata -> metadata.add("node_positions", json("{\"start\":{\"x\":42,\"y\":91}}")));
        helper.assertTrue(!document.json().has("node_positions"), "layout stays outside runtime JSON");
        helper.assertTrue(document.undo() && document.metadata().isEmpty(), "undo restores metadata");
        helper.assertTrue(document.undo() && document.json().equals(original), "undo restores all original JSON fields");
        helper.assertTrue(document.redo() && document.redo(), "redo restores both edits");
        JsonObject detached = document.json();
        detached.remove("extension");
        helper.assertTrue(document.json().has("extension"), "read snapshots cannot mutate the document");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void graphOperationsMaintainSeparateFormatsAndReportBrokenReferences(GameTestHelper helper) {
        AuthoringDocument tree = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/tree.json", json("""
                {"id":"test:tree","entries":[{"id":"talk","label":"Talk","start":"start"}],
                 "nodes":{"start":{"lines":["Hello"],"end":true}}}
                """));
        DialogueGraph.addNode(tree, "second");
        DialogueGraph.addResponse(tree, "start", "continue", "Continue", "second");
        helper.assertTrue(tree.json().get("nodes").isJsonObject(), "ordinary dialogue retains its keyed node map");
        helper.assertTrue(DialogueGraph.edges(tree).size() == 1, "response creates a graph edge");
        DialogueGraph.duplicateNode(tree, "second", "third");
        DialogueGraph.connect(tree, "/nodes/start/responses/0/next", "third");
        DialogueGraph.removeNode(tree, "third");
        var diagnostics = AuthoringValidation.validate(List.of(tree));
        helper.assertTrue(diagnostics.stream().anyMatch(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR
                && issue.pointer().equals("/nodes/start/responses/0/next")), "deleted targets get exact branch diagnostics");
        helper.assertTrue(diagnostics.stream().anyMatch(issue -> issue.message().contains("unreachable")), "unreachable nodes are reported");
        helper.assertTrue(tree.undo(), "node deletion is undoable");
        helper.assertTrue(AuthoringValidation.validate(List.of(tree)).stream().noneMatch(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR),
                "restoring the target repairs the graph");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void forcedFollowUpsStayNestedAndCannotBecomeRecursive(GameTestHelper helper) {
        AuthoringDocument document = new AuthoringDocument(DefinitionKind.FORCED, "data/test/forced_dialogue/test.json", json("""
                {"entries":[{"id":"first","trigger":"container_opened","lines":["Hello"],
                 "options":[{"id":"next","label":"Next","follow_up":{"lines":["Again"],"options":[]}}]},
                 {"id":"second","trigger":"container_opened","lines":["Elsewhere"]}]}
                """));
        helper.assertValueEqual(DialogueGraph.nodes(document).size(), 3, "forced graph includes embedded follow-ups");
        DialogueGraph.addResponse(document, "/entries/1", "nested", "Continue", "/entries/0/options/0/follow_up");
        helper.assertTrue(document.json().getAsJsonArray("entries").get(1).getAsJsonObject()
                .getAsJsonArray("options").get(0).getAsJsonObject().has("follow_up"), "connecting forced dialogue embeds an independent follow-up");
        boolean rejected = false;
        try {
            DialogueGraph.connect(document, "/entries/0/options/0/follow_up", "/entries/0");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "forced nested graphs reject recursive ancestor connections");
        helper.assertTrue(!document.json().has("nodes"), "forced definitions are never rewritten as ordinary node maps");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void workspaceRoundTripExportsRuntimeContentAndCompanionAssets(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-test-");
        try {
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            AuthoringDocument document = workspace.createDocument(DefinitionKind.TREE, "test:hello");
            document.edit("Extension", json -> json.add("extension", json("{\"preserve\":[1,2,3]}")));
            document.editMetadata("Layout", metadata -> metadata.add("node_positions", json("{\"start\":{\"x\":12,\"y\":34}}")));
            workspace.save();
            AuthoringWorkspace restored = AuthoringWorkspace.open(workspace.directory());
            helper.assertTrue(restored.documents().getFirst().json().equals(document.json()), "saved drafts preserve the definition");
            helper.assertTrue(restored.documents().getFirst().metadata().equals(document.metadata()), "saved drafts preserve separate layout");
            Path pack = root.resolve("pack");
            restored.exportPack(pack);
            helper.assertTrue(Files.isRegularFile(pack.resolve("pack.mcmeta")), "export emits pack metadata");
            String runtime = Files.readString(pack.resolve(document.resourcePath()));
            helper.assertTrue(!runtime.contains("node_positions"), "runtime export excludes editor metadata");
            Path asset = pack.resolve("assets/test/sounds/hello.ogg");
            Files.createDirectories(asset.getParent());
            byte[] payload = new byte[]{1, 2, 3, 4};
            Files.write(asset, payload);
            AuthoringWorkspace imported = AuthoringWorkspace.create(root.resolve("imported"));
            imported.importPack(pack);
            helper.assertTrue(imported.documents().getFirst().json().equals(document.json()), "exported JSON reimports losslessly");
            imported.exportPack(root.resolve("second-pack"));
            helper.assertTrue(java.util.Arrays.equals(payload, Files.readAllBytes(root.resolve("second-pack/assets/test/sounds/hello.ogg"))),
                    "opaque voice assets survive pack import and export");
            boolean refused = false;
            try {
                imported.exportPack(pack);
            } catch (IOException expected) {
                refused = true;
            }
            helper.assertTrue(refused, "export never overwrites an existing pack");
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void templatesPassRuntimeSchemasAndConversationCompiler(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-template-test-");
        try {
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            workspace.createDocument(DefinitionKind.TREE, "test:tree");
            workspace.createDocument(DefinitionKind.FORCED, "test:forced");
            workspace.createDocument(DefinitionKind.QUEST, "test:authored_quest");
            AuthoringDocument conversation = workspace.createDocument(DefinitionKind.CONVERSATION, "test:conversation");
            List<AuthoringDiagnostic> errors = workspace.validate().stream()
                    .filter(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR).toList();
            helper.assertTrue(errors.isEmpty(), "default content passes runtime validation: " + errors);
            helper.assertValueEqual(workspace.resolveText(conversation,
                    conversation.json().getAsJsonArray("nodes").get(0).getAsJsonObject()
                            .getAsJsonArray("exchanges").get(0).getAsJsonObject().get("text"), "en_us"), "Hello.",
                    "workspace locale messages resolve for interaction preview");
            workspace.documents().stream().filter(document -> document.kind() == DefinitionKind.QUEST).findFirst().orElseThrow()
                    .edit("Invalid objective", value -> value.getAsJsonArray("stages").get(0).getAsJsonObject()
                            .add("objectives", JsonParser.parseString("[{\"id\":\"bad\",\"type\":\"not_a_real_objective\"}]")));
            helper.assertTrue(workspace.validate().stream().anyMatch(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR),
                    "quest structural validation uses registered objective types");
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void unsafePathsAndNestedLocaleClassificationAreHandled(GameTestHelper helper) {
        for (String path : List.of("../pack.json", "data/test/../x.json", "C:/world.json", "/absolute.json",
                "data/test/CON.json", "data/test/trailing./test.json", "data/test/x:stream", "data/test//x.json")) {
            boolean rejected = false;
            try {
                new AuthoringDocument(DefinitionKind.RESOURCE, path, new JsonObject());
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "unsafe path rejected: " + path);
        }
        helper.assertValueEqual(DefinitionKind.fromPath("data/test/dialogue/en_us/forced_dialogue/messages.json"), DefinitionKind.RESOURCE,
                "localized forced dialogue messages are not trigger definitions");
        helper.assertValueEqual(DefinitionKind.fromPath("assets/test/lang/en_us.json"), DefinitionKind.RESOURCE,
                "asset translations are not runtime definitions");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void bundledForcedDialogueVariantsSurviveImportAndExport(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-builtin-test-");
        try {
            Path pack = root.resolve("source-pack");
            for (String resource : List.of("forced_dialogue/trade_refresh.json",
                    "forced_dialogue/events/container_theft.json", "dialogue/en_us/global/messages/01_trade_refresh.json")) {
                String path = "data/villagerretaliation/" + resource;
                Path destination = pack.resolve(path);
                Files.createDirectories(destination.getParent());
                try (var input = AuthoringCoreGameTests.class.getResourceAsStream("/" + path)) {
                    if (input == null) throw new IOException("Missing bundled test resource " + path);
                    Files.copy(input, destination);
                }
            }
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            workspace.importPack(pack);
            var errors = workspace.validate().stream().filter(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR).toList();
            helper.assertTrue(errors.isEmpty(), "existing forced dialogue eligibility variants remain valid: " + errors);
            workspace.exportPack(root.resolve("exported"));
            for (AuthoringDocument document : workspace.documents()) {
                helper.assertTrue(document.json().equals(json(Files.readString(root.resolve("exported").resolve(document.resourcePath())))),
                        "built-in data is preserved: " + document.resourcePath());
            }
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void fallbackTreeIdsAndPackOverridesRemainValid(GameTestHelper helper) {
        AuthoringDocument tree = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/fallback.json", json("""
                {"entries":[{"id":"default","label":"Talk","start":"start"}],
                 "nodes":{"storage_key":{"id":"start","lines":["Hello"],"end":true}}}
                """));
        helper.assertValueEqual(tree.id(), "test:fallback", "missing tree IDs derive from the locale resource path");
        helper.assertValueEqual(DialogueGraph.nodes(tree).getFirst().id(), "start", "explicit node IDs override map keys");
        AuthoringDocument replacement = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/replace.json", json("{\"replace\":true}"));
        AuthoringDocument removal = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/remove.json", json("{\"remove\":true,\"id\":\"test:old\"}"));
        AuthoringDocument translated = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/fr_fr/fallback.json", tree.json());
        var errors = AuthoringValidation.validate(List.of(tree, translated, replacement, removal)).stream()
                .filter(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR).toList();
        helper.assertTrue(errors.isEmpty(), "runtime-compatible overrides pass validation: " + errors);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void questBundleCopiesAndRenamesKeepLocalesAndReferences(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-bundle-test-");
        try {
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            AuthoringDocument quest = workspace.createDocument(DefinitionKind.QUEST, "test:authored_quest");
            AuthoringDocument conversation = workspace.createDocument(DefinitionKind.CONVERSATION, "test:conversation");
            quest.edit("Title", definition -> definition.add("display", json("{\"title\":{\"key\":\"#title\"}}")));
            workspace.documents().stream().filter(document -> document.resourcePath().equals("data/test/quests/authored/authored_quest/locales/en_us.json"))
                    .findFirst().orElseThrow().edit("Title message", messages -> messages.addProperty("test.quest.authored_quest.title", "Original"));
            AuthoringDocument copy = workspace.duplicate(quest, "test:copied_quest");
            helper.assertValueEqual(workspace.resolveText(copy, copy.json().getAsJsonObject("display").get("title"), "en_us"),
                    "Original", "duplicating a quest keeps and remaps its localized messages");
            workspace.rename(quest, "test:renamed_quest");
            helper.assertValueEqual(conversation.json().get("quest").getAsString(), "test:renamed_quest",
                    "renaming a quest repairs workspace conversation references");
            helper.assertTrue(workspace.documents().stream().anyMatch(document ->
                    document.resourcePath().equals("data/test/quests/authored/renamed_quest/locales/en_us.json")),
                    "renaming a quest moves its companion locale");
            var errors = workspace.validate().stream().filter(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR).toList();
            helper.assertTrue(errors.isEmpty(), "copied and renamed bundles pass runtime validation: " + errors);
            helper.assertTrue(quest.undo(), "undoing the selected quest rolls back the whole rename");
            helper.assertValueEqual(quest.id(), "test:authored_quest", "undo restores the quest identity");
            helper.assertValueEqual(conversation.json().get("quest").getAsString(), "test:authored_quest", "undo restores external references");
            helper.assertTrue(workspace.documents().stream().anyMatch(document ->
                    document.resourcePath().equals("data/test/quests/authored/authored_quest/locales/en_us.json")),
                    "undo restores companion paths");
            helper.assertTrue(conversation.redo(), "redo from any affected document reapplies the complete rename");
            helper.assertValueEqual(quest.id(), "test:renamed_quest", "redo restores the renamed quest identity");
            conversation.edit("Newer revision", definition -> definition.addProperty("revision", 2));
            boolean blocked = false;
            try {
                quest.undo();
            } catch (IllegalStateException expected) {
                blocked = true;
            }
            helper.assertTrue(blocked, "later changes in another affected document block a partial undo");
            helper.assertValueEqual(quest.id(), "test:renamed_quest", "blocked undo changes no identities");
            helper.assertValueEqual(conversation.json().get("revision").getAsInt(), 2, "blocked undo retains newer edits");
            helper.assertTrue(conversation.undo() && quest.undo(), "undoing the newer edit permits the grouped undo");
            helper.assertTrue(quest.undo(), "history from before the rename remains available");
            helper.assertTrue(!quest.json().has("display"), "the earlier title edit remains undoable");
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void ordinaryMessageFilesResolveWithEnglishFallback(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-locale-test-");
        try {
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            AuthoringDocument owner = workspace.createDocument(DefinitionKind.TREE, "test:tree");
            workspace.add(new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue/en_us/global/messages/greeting.json",
                    json("{\"messages\":[{\"key\":\"hello\",\"lines\":[\"Welcome\",\"Hello\"]}]}")));
            helper.assertValueEqual(workspace.resolveText(owner, json("{\"key\":\"hello\"}"), "fr_fr"),
                    "Welcome", "ordinary keyed dialogue messages use English fallback");
            helper.assertValueEqual(workspace.resolveText(owner, json("{\"key\":\"hello\"}"), "en_us", 1, value -> true),
                    "Hello", "preview variants select alternate localized lines");
            JsonObject conditionalVariants = json("""
                    {"variants":[{"text":"Hidden","conditions":[{"type":"quest","quest":"test:locked"}]},
                     {"text":"Visible"},{"text":"Alternate"}]}
                    """);
            helper.assertValueEqual(workspace.resolveText(owner, conditionalVariants, "en_us", 1, value -> !value.has("conditions")),
                    "Alternate", "variant selection runs the supplied simulation predicate before indexing");

            workspace.add(new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue/fr_fr/global/messages/greeting.json",
                    json("{\"key\":\"hello\",\"text\":\"Bonjour\"}")));
            helper.assertValueEqual(workspace.resolveText(owner, json("{\"key\":\"hello\"}"), "fr_fr"),
                    "Bonjour", "single-section locale files take precedence over fallback");
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void invalidRuntimeTriggersAndFilteredResponsesBlockExport(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-validation-test-");
        try {
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            AuthoringDocument forced = workspace.createDocument(DefinitionKind.FORCED, "test:forced");
            forced.edit("Invalid trigger", definition -> definition.addProperty("trigger", "not_a_trigger"));
            helper.assertTrue(workspace.validate().stream().anyMatch(issue -> issue.pointer().equals("/trigger")
                    && issue.severity() == AuthoringDiagnostic.Severity.ERROR), "unknown triggers are rejected using the runtime trigger type");
            workspace.remove(forced);
            AuthoringDocument tree = workspace.createDocument(DefinitionKind.TREE, "test:tree");
            DialogueGraph.addResponse(tree, "start", "too_long",
                    "x".repeat(com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition.MAX_NETWORK_LABEL_LENGTH + 1), "start");
            helper.assertTrue(workspace.validate().stream().anyMatch(issue -> issue.pointer().equals("/nodes/start/responses")
                    && issue.severity() == AuthoringDiagnostic.Severity.ERROR), "responses filtered by the runtime parser block export");
            AuthoringDocument quest = workspace.createDocument(DefinitionKind.QUEST, "test:quest");
            helper.assertTrue(DialogueGraph.nodes(quest).getFirst().terminal(), "quest completion objects display as terminal nodes");
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void forcedDuplicationPreservesRootEntriesAndNestedChoices(GameTestHelper helper) {
        JsonObject original = json("""
                {"id":"original","trigger":"container_opened","replace":true,"lines":["Opening"],"extension":{"keep":true},
                 "options":[{"id":"branch","label":"Continue","response":"Yes","end_conversation":false,
                 "follow_up":{"lines":["Follow-up"],"options":[{"id":"leave","label":"Leave","end_conversation":true}]}}]}
                """);
        AuthoringDocument document = new AuthoringDocument(DefinitionKind.FORCED, "data/test/forced_dialogue/root.json", original);
        DialogueGraph.duplicateNode(document, "", "copy");
        helper.assertTrue(document.json().getAsJsonArray("entries").get(0).equals(original), "root duplication retains the complete original entry");
        helper.assertTrue(document.json().get("replace").getAsBoolean(), "root replacement policy stays at the file root");
        helper.assertValueEqual(document.json().getAsJsonArray("entries").get(1).getAsJsonObject().get("id").getAsString(),
                "copy", "the second independent entry uses its requested identity");
        helper.assertTrue(document.undo() && document.json().equals(original), "root conversion is a single undoable edit");
        DialogueGraph.duplicateNode(document, "/options/0/follow_up", "copied_branch");
        JsonObject duplicated = document.json();
        helper.assertTrue(!duplicated.has("entries"), "nested follow-up duplication does not create independent trigger entries");
        helper.assertValueEqual(duplicated.getAsJsonArray("options").size(), 2, "nested duplication creates a sibling response");
        JsonObject copiedOption = duplicated.getAsJsonArray("options").get(1).getAsJsonObject();
        helper.assertValueEqual(copiedOption.get("id").getAsString(), "copied_branch", "the sibling response uses its requested ID");
        helper.assertTrue(copiedOption.get("follow_up").equals(original.getAsJsonArray("options").get(0).getAsJsonObject().get("follow_up")),
                "the duplicate retains the entire nested branch");
        AuthoringDocument incomplete = new AuthoringDocument(DefinitionKind.FORCED, "data/test/forced_dialogue/draft.json", json("{\"lines\":[\"Draft\"]}"));
        boolean blocked = false;
        try {
            DialogueGraph.addNode(incomplete, "new_entry");
        } catch (IllegalArgumentException expected) {
            blocked = true;
        }
        helper.assertTrue(blocked && !incomplete.json().has("entries"), "adding a node cannot hide an incomplete single-entry draft");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void ordinaryRenameUndoRejectsOccupiedPaths(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-rename-test-");
        try {
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            AuthoringDocument renamed = workspace.createDocument(DefinitionKind.TREE, "test:original");
            workspace.rename(renamed, "test:renamed");
            AuthoringDocument occupant = workspace.createDocument(DefinitionKind.TREE, "test:original");
            boolean blocked = false;
            try {
                renamed.undo();
            } catch (IllegalStateException expected) {
                blocked = true;
            }
            helper.assertTrue(blocked, "rename undo rejects a newly occupied path");
            helper.assertValueEqual(renamed.id(), "test:renamed", "blocked undo leaves the renamed resource untouched");
            workspace.save();
            helper.assertValueEqual(AuthoringWorkspace.open(workspace.directory()).documents().size(), 2, "blocked undo leaves a reloadable draft");
            workspace.remove(occupant);
            helper.assertTrue(renamed.undo() && renamed.id().equals("test:original"), "freeing the original path permits undo");
            helper.assertTrue(renamed.redo() && renamed.id().equals("test:renamed"), "rename remains redoable");
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void renamingAndDuplicatingKeepTreeLocales(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-tree-locale-test-");
        try {
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            AuthoringDocument source = workspace.createDocument(DefinitionKind.TREE, "test:bonjour");
            AuthoringDocument french = new AuthoringDocument(DefinitionKind.TREE,
                    "data/test/dialogue_trees/fr_fr/bonjour.json", source.json());
            workspace.remove(source);
            workspace.add(french);
            workspace.rename(french, "test:renamed");
            helper.assertValueEqual(french.resourcePath(), "data/test/dialogue_trees/fr_fr/renamed.json",
                    "renaming preserves the source locale");
            AuthoringDocument duplicate = workspace.duplicate(french, "test:copy");
            helper.assertValueEqual(duplicate.resourcePath(), "data/test/dialogue_trees/fr_fr/copy.json",
                    "duplicating preserves the source locale");
            helper.assertTrue(!duplicate.canUndo(), "duplicate setup does not expose an undo that restores the original identity");
            helper.assertTrue(french.undo(), "localized rename remains undoable");
            helper.assertValueEqual(french.resourcePath(), "data/test/dialogue_trees/fr_fr/bonjour.json",
                    "undo restores the complete localized path");
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void conversationCopiesAndRenamesRetainTheirOwningBundle(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-conversation-owner-test-");
        try {
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            workspace.createDocument(DefinitionKind.QUEST, "test:authored_quest");
            AuthoringDocument shared = workspace.createDocument(DefinitionKind.CONVERSATION, "test:talk");
            JsonObject definition = shared.json();
            definition.add("title", json("{\"key\":\"#private_title\"}"));
            AuthoringDocument privateConversation = new AuthoringDocument(DefinitionKind.CONVERSATION,
                    "data/test/quests/authored/authored_quest/conversations/talk.json", definition);
            workspace.remove(shared);
            workspace.add(privateConversation);
            AuthoringDocument sharedMessages = workspace.documents().stream().filter(document -> document.resourcePath()
                    .equals("data/test/quests/_shared/locales/en_us.json")).findFirst().orElseThrow();
            AuthoringDocument privateMessages = workspace.documents().stream().filter(document -> document.resourcePath()
                    .equals("data/test/quests/authored/authored_quest/locales/en_us.json")).findFirst().orElseThrow();
            privateMessages.replaceJson(AuthoringDocument.JSON.toJson(sharedMessages.json()));
            privateMessages.edit("Private title", messages -> messages.addProperty("test.quest.authored_quest.private_title", "Private conversation"));
            workspace.remove(sharedMessages);
            AuthoringDocument references = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/references.json",
                    json("{\"conversation\":\"test:talk\",\"text\":\"test:talk\"}"));
            workspace.add(references);
            AuthoringDocument duplicate = workspace.duplicate(privateConversation, "test:copy");
            helper.assertValueEqual(duplicate.resourcePath(), "data/test/quests/authored/authored_quest/conversations/copy.json",
                    "copies stay in the same private bundle");
            helper.assertValueEqual(workspace.resolveText(duplicate, duplicate.json().get("title"), "en_us"), "Private conversation",
                    "copies retain relative localization references");
            workspace.rename(privateConversation, "test:renamed");
            helper.assertValueEqual(privateConversation.resourcePath(), "data/test/quests/authored/authored_quest/conversations/renamed.json",
                    "renames retain the owning quest directory");
            helper.assertValueEqual(references.json().get("conversation").getAsString(), "test:renamed", "rename repairs conversation references");
            helper.assertValueEqual(references.json().get("text").getAsString(), "test:talk", "rename does not alter ordinary text matching the old ID");
            helper.assertTrue(privateConversation.undo(), "conversation identity and references undo together");
            helper.assertValueEqual(references.json().get("conversation").getAsString(), "test:talk", "undo restores conversation references");
            boolean rejected = false;
            int documentCount = workspace.documents().size();
            try {
                workspace.duplicate(privateConversation, "other:copy");
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected && workspace.documents().size() == documentCount,
                    "isolated namespace changes cannot orphan private localization or mutate the workspace");
            var errors = workspace.validate().stream().filter(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR).toList();
            helper.assertTrue(errors.isEmpty(), "preserved conversation owners pass runtime validation: " + errors);
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void graphStorageKeysAndNumericPointerPrefixesDoNotCollide(GameTestHelper helper) {
        AuthoringDocument tree = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/tree.json", json("""
                {"id":"test:tree","entries":[{"id":"talk","label":"Talk","start":"public_id"}],
                 "nodes":{"storage":{"id":"public_id","lines":["Retain me"],"end":true}}}
                """));
        JsonObject original = tree.json();
        for (boolean duplicate : List.of(false, true)) {
            boolean rejected = false;
            try {
                if (duplicate) DialogueGraph.duplicateNode(tree, "public_id", "storage");
                else DialogueGraph.addNode(tree, "storage");
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected && tree.json().equals(original), "an effective node ID cannot overwrite an existing storage key");
        }
        JsonObject forcedRoot = new JsonObject();
        com.google.gson.JsonArray entries = new com.google.gson.JsonArray();
        for (int index = 0; index < 11; index++) entries.add(DialogueGraph.defaultNode(DefinitionKind.FORCED, "entry_" + index));
        forcedRoot.add("entries", entries);
        AuthoringDocument forced = new AuthoringDocument(DefinitionKind.FORCED, "data/test/forced_dialogue/many.json", forcedRoot);
        DialogueGraph.addResponse(forced, "/entries/10", "branch", "Continue", "/entries/1");
        DialogueGraph.connect(forced, "/entries/10/options/0/follow_up", "/entries/1");
        helper.assertTrue(forced.json().getAsJsonArray("entries").get(10).getAsJsonObject().getAsJsonArray("options").get(0)
                .getAsJsonObject().has("follow_up"), "entry 1 is not an ancestor of sibling entry 10");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void failedCreationAndMalformedTypedImportsAreAtomic(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-atomic-test-");
        try {
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            AuthoringDocument existingLocale = new AuthoringDocument(DefinitionKind.RESOURCE,
                    "data/test/quests/authored/quest/locales/en_us.json", json("{\"existing\":\"Preserve\"}"));
            workspace.add(existingLocale);
            boolean rejectedQuest = false;
            try {
                workspace.createDocument(DefinitionKind.QUEST, "test:quest");
            } catch (IllegalArgumentException expected) {
                rejectedQuest = true;
            }
            helper.assertTrue(rejectedQuest && workspace.documents().size() == 1, "companion collisions cannot leave a partially created quest");
            AuthoringDocument sharedLocale = new AuthoringDocument(DefinitionKind.RESOURCE,
                    "data/test/quests/_shared/locales/en_us.json", json("{\"test:conversation.title\":\"Preserve title\"}"));
            workspace.add(sharedLocale);
            boolean rejectedConversation = false;
            try {
                workspace.createDocument(DefinitionKind.CONVERSATION, "test:conversation");
            } catch (IllegalArgumentException expected) {
                rejectedConversation = true;
            }
            helper.assertTrue(rejectedConversation && workspace.documents().size() == 2, "existing locale messages are not overwritten by a partial conversation creation");
            helper.assertValueEqual(sharedLocale.json().get("test:conversation.title").getAsString(), "Preserve title",
                    "existing translation content remains unchanged");
            Path pack = root.resolve("pack");
            Path invalid = pack.resolve("data/test/dialogue_trees/en_us/invalid.json");
            Files.createDirectories(invalid.getParent());
            Files.writeString(invalid, "[]");
            boolean rejectedImport = false;
            try {
                workspace.importPack(pack);
            } catch (IOException expected) {
                rejectedImport = true;
            }
            helper.assertTrue(rejectedImport && workspace.documents().size() == 2, "typed non-object JSON cannot bypass validation as an opaque asset");
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void explicitNullsAndAdvancedFieldsSurviveEveryRoundTrip(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-nulls-");
        try {
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            AuthoringDocument document = workspace.createDocument(DefinitionKind.TREE, "test:roundtrip");
            document.edit("Advanced fields", value -> {
                value.add("extension", json("{\"optional\":null,\"nested\":[null,{\"future\":null}],\"unicode\":\"Zoë 🌻\",\"number\":1.2500}"));
                value.getAsJsonObject("nodes").getAsJsonObject("start").add("extension_node", json("{\"optional\":null}"));
            });
            document.editMetadata("Layout extension", value -> value.add("future", com.google.gson.JsonNull.INSTANCE));
            JsonObject expected = document.json();
            document.replaceJson(AuthoringDocument.JSON.toJson(expected));
            helper.assertValueEqual(document.json(), expected, "full JSON editing preserves explicit null properties");
            workspace.addDialogueLine(document, "start");
            helper.assertValueEqual(document.json().get("extension"), expected.get("extension"), "plain dialogue operations retain advanced fields");
            expected = document.json();
            workspace.save();
            AuthoringWorkspace loaded = AuthoringWorkspace.open(workspace.directory());
            helper.assertValueEqual(loaded.documents().getFirst().json(), expected, "workspace reload preserves complete runtime JSON");
            helper.assertTrue(loaded.documents().getFirst().metadata().has("future"), "workspace reload preserves explicit null editor metadata");
            loaded.exportPack(root.resolve("export"));
            helper.assertValueEqual(json(Files.readString(root.resolve("export").resolve(document.resourcePath()))), expected,
                    "export retains explicit nulls, unicode, numeric values and extensions");
            AuthoringWorkspace imported = AuthoringWorkspace.create(root.resolve("reimport"));
            imported.importPack(root.resolve("export"));
            helper.assertValueEqual(imported.documents().getFirst().json(), expected, "exported runtime JSON imports without field loss");
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void failedSaveAndExportKeepDraftsAndExistingFiles(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-failed-save-");
        try {
            AuthoringWorkspace workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            AuthoringDocument document = workspace.createDocument(DefinitionKind.TREE, "test:save");
            workspace.save();
            Path file = workspace.directory().resolve("workspace.json");
            byte[] saved = Files.readAllBytes(file);
            Path backup = root.resolve("saved-workspace.json");
            Files.move(file, backup);
            Files.createDirectory(file);
            document.edit("Unsaved wording", value -> value.getAsJsonObject("nodes").getAsJsonObject("start")
                    .getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Keep this draft")));
            JsonObject draft = document.json();
            boolean failed = false;
            try { workspace.save(); } catch (IOException expected) { failed = true; }
            helper.assertTrue(failed && document.dirty() && document.canUndo(), "failed save retains dirty state and undo history");
            helper.assertValueEqual(document.json(), draft, "failed save leaves authored content available for retry");
            helper.assertTrue(java.util.Arrays.equals(Files.readAllBytes(backup), saved), "existing saved content remains intact");
            try (var paths = Files.list(workspace.directory())) {
                helper.assertTrue(paths.noneMatch(path -> path.getFileName().toString().startsWith(".vr-draft-")), "failed save cleans staging files");
            }
            Files.delete(file);
            Files.move(backup, file);
            Path export = Files.createDirectory(root.resolve("existing-pack"));
            Files.writeString(export.resolve("keep.txt"), "Existing pack");
            failed = false;
            try { workspace.exportPack(export); } catch (IOException expected) { failed = true; }
            helper.assertTrue(failed && document.dirty() && document.json().equals(draft), "failed export retains unsaved drafts");
            helper.assertValueEqual(Files.readString(export.resolve("keep.txt")), "Existing pack", "failed export preserves an existing destination");
            workspace.save();
            helper.assertTrue(!document.dirty(), "save can be retried after fixing the destination");
            helper.assertValueEqual(AuthoringWorkspace.open(workspace.directory()).documents().getFirst().json(), draft, "retry saves the original draft");
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void workspaceLoadErrorsLocateMalformedDocuments(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-authoring-load-");
        try {
            Path file = root.resolve("workspace.json");
            Files.writeString(file, "{\"version\":1,\"documents\":[{\"kind\":\"RESOURCE\",\"path\":\"data/test/resource.json\",\"definition\":{\"future\":null}}]}");
            AuthoringWorkspace loaded = AuthoringWorkspace.open(root);
            helper.assertTrue(loaded.documents().getFirst().metadata().isEmpty(), "optional editor metadata defaults to empty");
            for (String broken : List.of("null", "[]", "\"text\"")) {
                Files.writeString(file, "{\"version\":1,\"documents\":[{\"kind\":\"RESOURCE\",\"path\":\"data/test/resource.json\",\"definition\":{} ,\"editor\":" + broken + "}]}");
                boolean located = false;
                try { AuthoringWorkspace.open(root); } catch (IOException expected) { located = expected.getMessage().contains("/documents/0/editor"); }
                helper.assertTrue(located, "invalid editor metadata gives a document and field location");
            }
            Files.writeString(file, "{\"version\":1,\"documents\":[{\"kind\":\"RESOURCE\",\"path\":\"data/test/resource.json\"}]}");
            boolean located = false;
            try { AuthoringWorkspace.open(root); } catch (IOException expected) { located = expected.getMessage().contains("/documents/0/definition"); }
            helper.assertTrue(located, "missing definition is an actionable load error");
            helper.succeed();
        } finally {
            removeTemporaryTree(root);
        }
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static void removeTemporaryTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
