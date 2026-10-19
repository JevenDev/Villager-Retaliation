package com.jvn.villagerretaliation.authoring;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import com.jvn.villagerretaliation.authoring.core.AuthoringPackBuild;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipFile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("authoring")
@PrefixGameTestTemplate(false)
public final class AuthoringFilesGameTests {
    private AuthoringFilesGameTests() { }

    @GameTest(template = "empty", batch = "authoring")
    public static void filesIncludePackMetadataDocumentsAndEditableAssets(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-file-browser-");
        try {
            var workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            var addon = workspace.createDocument(DefinitionKind.CAMPAIGN_ADDON, "test:addon");
            var quest = workspace.createDocument(DefinitionKind.QUEST, "test:quest", addon);
            Path description = root.resolve("description.md");
            Files.writeString(description, "# Original");
            workspace.importCampaignDescription(addon, description);
            String asset = addon.json().get("description_file").getAsString().replace(":", "/");
            asset = "data/" + asset;
            helper.assertTrue(workspace.filePaths().containsAll(List.of("pack.mcmeta", addon.resourcePath(), quest.resourcePath(), asset)), "the file browser includes metadata, content and attachments");
            workspace.writeTextFile(asset, "# Updated\nNew wording");
            helper.assertValueEqual(workspace.campaignDescription(addon), "# Updated\nNew wording", "text assets can be edited directly");
            byte[] detached = workspace.readFile(asset);
            detached[0] = 0;
            helper.assertTrue(workspace.campaignDescription(addon).startsWith("#"), "file reads do not expose mutable attachment storage");
            workspace.save();
            var reopened = AuthoringWorkspace.open(workspace.directory());
            helper.assertValueEqual(reopened.campaignDescription(reopened.documents().getFirst()), "# Updated\nNew wording", "text edits survive reopening");
            helper.succeed();
        } finally { cleanup(root); }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void folderMovesKeepQuestCompanionsAndReferencesTogether(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-file-move-");
        try {
            var workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            var quest = workspace.createDocument(DefinitionKind.QUEST, "test:quest");
            var conversation = workspace.createDocument(DefinitionKind.CONVERSATION, "test:talk");
            conversation.edit("Assign quest", value -> value.addProperty("quest", "test:quest"));
            var plan = workspace.previewFileMove("data/test/quests/authored/quest", "data/test/quests/chapter_one/renamed");
            helper.assertTrue(plan.paths().size() == 2 && !plan.changes().isEmpty(), "move previews include locale companions and identity changes");
            workspace.applyFileMove(plan);
            helper.assertValueEqual(quest.id(), "test:renamed", "quest IDs follow their required slug folder");
            helper.assertValueEqual(quest.json().getAsJsonObject("metadata").get("questline").getAsString(), "chapter_one", "questline metadata follows the folder");
            helper.assertValueEqual(conversation.json().get("quest").getAsString(), "test:renamed", "quest references are updated");
            helper.assertTrue(workspace.filePaths().contains("data/test/quests/chapter_one/renamed/locales/en_us.json"), "locale companions move with the quest");
            workspace.undoFileMove();
            helper.assertValueEqual(quest.id(), "test:quest", "a move can be undone as a unit");
            helper.assertValueEqual(conversation.json().get("quest").getAsString(), "test:quest", "undo restores references");
            var addon = workspace.createDocument(DefinitionKind.CAMPAIGN_ADDON, "test:addon");
            workspace.applyFileMove(workspace.previewFileMove("data/test/quests/authored/quest", "data/test/campaign_addons/addon/quests/authored/quest"));
            helper.assertTrue(quest.resourcePath().startsWith(DefinitionKind.contentRoot(addon.resourcePath())), "existing quest bundles can move into an addon");
            helper.assertTrue(workspace.validate().stream().noneMatch(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR), "moved content passes runtime validation");
            helper.succeed();
        } finally { cleanup(root); }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void movesRejectCollisionsStalePreviewsAndInvalidLayouts(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-file-move-safety-");
        try {
            var workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            var quest = workspace.createDocument(DefinitionKind.QUEST, "test:quest");
            workspace.createDocument(DefinitionKind.QUEST, "test:occupied");
            for (String target : List.of("data/test/quests/authored/occupied", "data/other/quests/authored/quest", "data/test/not_quests/quest", "../escaped")) {
                boolean rejected = false;
                try { workspace.previewFileMove("data/test/quests/authored/quest", target); } catch (IllegalArgumentException expected) { rejected = true; }
                helper.assertTrue(rejected && quest.id().equals("test:quest"), "invalid destinations cannot change drafts: " + target);
            }
            var preview = workspace.previewFileMove("data/test/quests/authored/quest", "data/test/quests/authored/next");
            quest.edit("Edit after preview", value -> value.addProperty("revision", 2));
            boolean rejected = false;
            try { workspace.applyFileMove(preview); } catch (IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected && quest.id().equals("test:quest"), "stale move previews cannot overwrite newer edits");
            quest.edit("Broken metadata", value -> value.remove("metadata"));
            rejected = false;
            try { workspace.previewFileMove("data/test/quests/authored/quest", "data/test/quests/authored/next"); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected && quest.id().equals("test:quest"), "malformed quest metadata produces a recoverable move error");
            helper.succeed();
        } finally { cleanup(root); }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void campaignFolderRenamesMoveAssetsAndUpdateOwnership(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-folder-rename-");
        try {
            var workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            var campaign = workspace.createDocument(DefinitionKind.WORLD_CAMPAIGN, "test:campaign");
            workspace.createDocument(DefinitionKind.QUEST, "test:quest", campaign);
            var addon = workspace.createDocument(DefinitionKind.CAMPAIGN_ADDON, "test:addon");
            addon.edit("Support campaign", value -> value.add("campaigns", JsonParser.parseString("[\"test:campaign\"]")));
            Path description = root.resolve("description.md");
            Files.writeString(description, "# Story");
            workspace.importCampaignDescription(campaign, description);
            workspace.applyFileMove(workspace.previewFileMove("data/test/world_campaigns/campaign", "data/test/world_campaigns/renamed"));
            helper.assertValueEqual(campaign.id(), "test:renamed", "manifest IDs follow campaign folder names");
            helper.assertValueEqual(addon.json().getAsJsonArray("campaigns").get(0).getAsString(), "test:renamed", "addon compatibility follows campaign renames");
            helper.assertValueEqual(workspace.campaignDescription(campaign), "# Story", "description assets remain linked");
            workspace.undoFileMove();
            helper.assertValueEqual(campaign.id(), "test:campaign", "folder rename undo restores manifest identity");
            helper.assertValueEqual(workspace.campaignDescription(campaign), "# Story", "folder rename undo restores assets");
            String original = "data/" + campaign.json().get("description_file").getAsString().replace(":", "/");
            workspace.applyFileMove(workspace.previewFileMove(original, "data/test/world_campaigns/campaign/description.md"));
            helper.assertValueEqual(workspace.campaignDescription(campaign), "# Story", "renaming individual assets updates their references");
            helper.succeed();
        } finally { cleanup(root); }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void packageBuildsProduceCleanZipRootsAndProtectExistingOutputs(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-package-build-");
        try {
            var workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            workspace.createDocument(DefinitionKind.QUEST, "test:quest");
            var metadata = workspace.packMetadata();
            metadata.getAsJsonObject("pack").addProperty("description", "Final package");
            metadata.add("filter", JsonParser.parseString("{\"block\":[]}"));
            workspace.setPackMetadata(metadata);
            Path zip = root.resolve("release.zip");
            AuthoringPackBuild.build(workspace, zip, true);
            try (var archive = new ZipFile(zip.toFile())) {
                helper.assertTrue(archive.getEntry("pack.mcmeta") != null && archive.getEntry("data/test/quests/authored/quest/quest.json") != null, "ZIPs place pack.mcmeta and data at the archive root");
                helper.assertTrue(archive.getEntry("workspace.json") == null && archive.getEntry(".vr-authoring.json") == null, "shareable packages exclude editor state and local paths");
                var pack = JsonParser.parseString(new String(archive.getInputStream(archive.getEntry("pack.mcmeta")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                helper.assertTrue(pack.has("filter") && pack.getAsJsonObject("pack").get("description").getAsString().equals("Final package"), "builds preserve advanced pack metadata");
            }
            byte[] previous = Files.readAllBytes(zip);
            boolean rejected = false;
            try { AuthoringPackBuild.build(workspace, zip, true); } catch (IOException expected) { rejected = true; }
            helper.assertTrue(rejected && java.util.Arrays.equals(previous, Files.readAllBytes(zip)), "ZIP builds preserve existing packages");
            Path folder = root.resolve("folder");
            AuthoringPackBuild.build(workspace, folder, false);
            helper.assertTrue(AuthoringPackBuild.build(workspace, folder, false) != null, "folder builds retain the managed export backup workflow");
            var bad = workspace.packMetadata();
            bad.getAsJsonObject("pack").addProperty("pack_format", -1);
            rejected = false;
            try { workspace.setPackMetadata(bad); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected && workspace.packMetadata().equals(metadata), "invalid pack settings are rejected without partial edits");
            helper.succeed();
        } finally { cleanup(root); }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void emptyFoldersPersistMoveAndExport(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-empty-folders-");
        try {
            var workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            workspace.createFolder("data/test/drafts/nested");
            helper.assertTrue(workspace.dirty() && workspace.folderPaths().contains("data/test/drafts"), "new empty folders mark the workspace dirty and expose parents");
            workspace.save();
            workspace = AuthoringWorkspace.open(workspace.directory());
            helper.assertTrue(!workspace.dirty() && workspace.folderPaths().contains("data/test/drafts/nested"), "empty folders survive reopening");
            var move = workspace.previewFileMove("data/test/drafts", "data/test/renamed");
            workspace.applyFileMove(move);
            helper.assertTrue(workspace.folderPaths().contains("data/test/renamed/nested") && !workspace.folderPaths().contains("data/test/drafts"), "folder moves include empty descendants");
            workspace.undoFileMove();
            helper.assertTrue(!workspace.dirty() && workspace.folderPaths().contains("data/test/drafts/nested"), "empty folder moves undo completely");
            workspace.createFolder("assets/test/textures/new");
            workspace.saveRecovery(new com.google.gson.JsonObject());
            var recovered = AuthoringWorkspace.recover(workspace.directory());
            helper.assertTrue(recovered.folderPaths().contains("assets/test/textures/new"), "recovery preserves new folders");
            Path folder = root.resolve("pack");
            AuthoringPackBuild.build(recovered, folder, false);
            helper.assertTrue(Files.isDirectory(folder.resolve("data/test/drafts/nested")), "folder exports retain empty directories");
            Path zip = root.resolve("pack.zip");
            AuthoringPackBuild.build(recovered, zip, true);
            try (var archive = new ZipFile(zip.toFile())) {
                helper.assertTrue(archive.getEntry("assets/test/textures/new/").isDirectory(), "ZIP builds retain empty directories");
            }
            helper.succeed();
        } finally { cleanup(root); }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void folderCreationRejectsUnsafeAndOccupiedPaths(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-folder-safety-");
        try {
            var workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            var quest = workspace.createDocument(DefinitionKind.QUEST, "test:quest");
            workspace.createFolder("data/test/empty");
            for (String path : List.of("../outside", "data/test/UPPER", "data/test/con", "pack.mcmeta", "other/test/folder", "data/test/empty", "data/test/quests", quest.resourcePath(), quest.resourcePath() + "/child")) {
                boolean rejected = false;
                try { workspace.createFolder(path); } catch (IllegalArgumentException expected) { rejected = true; }
                helper.assertTrue(rejected, "unsafe or occupied folder creation is rejected: " + path);
            }
            for (String path : List.of("data/test/quests", "data/test/empty/child", "data/other/empty", quest.resourcePath(), quest.resourcePath() + "/child")) {
                boolean rejected = false;
                try { workspace.previewFileMove("data/test/empty", path); } catch (IllegalArgumentException expected) { rejected = true; }
                helper.assertTrue(rejected, "empty folders honor the same move protections: " + path);
            }
            var preview = workspace.previewFileMove("data/test/empty", "data/test/next");
            workspace.createFolder("data/test/newer");
            boolean rejected = false;
            try { workspace.applyFileMove(preview); } catch (IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected && workspace.folderPaths().contains("data/test/empty"), "folder changes invalidate move previews");
            helper.succeed();
        } finally { cleanup(root); }
    }

    @GameTest(template = "empty", batch = "authoring")
    public static void bundleOperationsKeepEmptyFoldersTogether(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("vr-bundle-folders-");
        try {
            var workspace = AuthoringWorkspace.create(root.resolve("workspace"));
            var addon = workspace.createDocument(DefinitionKind.CAMPAIGN_ADDON, "test:addon");
            String source = "data/test/campaign_addons/addon";
            workspace.createFolder(source + "/artwork/empty");
            workspace.applyFileMove(workspace.previewFileMove(source, "data/test/campaign_addons/moved"));
            helper.assertTrue(workspace.folderPaths().contains("data/test/campaign_addons/moved/artwork/empty"), "bundle file moves include empty folders");
            addon.undo();
            helper.assertTrue(workspace.folderPaths().contains(source + "/artwork/empty"), "document history restores empty folders with bundle moves");
            workspace.rename(addon, "test:renamed");
            helper.assertTrue(workspace.folderPaths().contains("data/test/campaign_addons/renamed/artwork/empty"), "content rename keeps folder structure");
            addon.undo();
            workspace.remove(addon);
            helper.assertTrue(!workspace.folderPaths().contains(source + "/artwork/empty"), "deleted bundles remove their empty folders");
            workspace.restoreDeleted(0);
            helper.assertTrue(workspace.folderPaths().contains(source + "/artwork/empty"), "restoring bundles restores their empty folders");
            helper.succeed();
        } finally { cleanup(root); }
    }

    private static void cleanup(Path root) throws IOException {
        try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
}
