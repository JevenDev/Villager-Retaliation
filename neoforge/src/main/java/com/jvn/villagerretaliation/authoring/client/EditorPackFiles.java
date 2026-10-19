package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

final class EditorPackFiles implements AutoCloseable {
    static final class Node {
        final String path;
        final Map<String, Node> children = new TreeMap<>();
        boolean file;
        Node(String path) { this.path = path; }
    }

    private record FileDrag(Path workspace, String source) { }
    record Entry(String path, boolean folder) { }

    static List<Entry> contents(AuthoringWorkspace workspace, String folder) {
        return java.util.stream.Stream.concat(
                workspace.folderPaths().stream().filter(path -> EditorNavigation.parent(path).equals(folder)).map(path -> new Entry(path, true)),
                workspace.filePaths().stream().filter(path -> EditorNavigation.parent(path).equals(folder)).map(path -> new Entry(path, false))).toList();
    }

    private String selected = "";
    private String loaded = "";
    private String originalText = "";
    private final ImString text = new ImString(1_048_576);
    private final ImString name = new ImString(512);
    private final ImString parent = new ImString(4096);
    private AuthoringWorkspace.FileMove preview;
    private boolean editable, reveal, revealMove;
    private DynamicTexture image;
    private int size;
    private byte[] baseline = new byte[0];
    private Node cachedTree;
    private List<String> cachedPaths = List.of();
    private String cachedSearch = "";
    private List<String> cachedFolders = List.of();
    private final ImString folderName = new ImString(512);
    private String folderParent = "";
    private String folderError = "";
    private boolean requestFolderPopup, closeFolderPopup;
    private int focusMove;
    private FileDrag dragging;
    private String pressedFolder = "";
    private Map<String, AuthoringDocument> treeDocuments = Map.of();

    static Node tree(List<String> paths, String search) {
        return tree(paths, List.of(), search);
    }

    static Node tree(List<String> paths, List<String> folders, String search) {
        Node root = new Node("");
        var files = new java.util.HashSet<>(paths);
        String term = search.strip().toLowerCase(Locale.ROOT);
        for (String path : java.util.stream.Stream.concat(paths.stream(), folders.stream()).toList()) {
            if (!path.toLowerCase(Locale.ROOT).contains(term)) continue;
            Node node = root;
            for (String part : path.split("/")) {
                String child = node.path.isEmpty() ? part : node.path + "/" + part;
                node = node.children.computeIfAbsent(part, ignored -> new Node(child));
            }
            node.file = files.contains(path);
        }
        return root;
    }

    String selected() { return selected; }
    boolean pending() { return editable && !originalText.equals(text.get()); }

    void select(String path) {
        if (selected.equals(path)) { if (!pending()) close(); reveal = true; return; }
        if (pending()) throw new IllegalStateException("Apply or discard the file edits before opening another file");
        close();
        selected = path;
        loaded = "";
        originalText = "";
        text.set("");
        editable = false;
        preview = null;
        reveal = true;
        setMoveSource(path);
    }

    private void setMoveSource(String source) {
        int slash = source.lastIndexOf('/');
        name.set(source.substring(slash + 1));
        parent.set(slash < 0 ? "" : source.substring(0, slash));
    }

    void reset() {
        close(); selected = ""; loaded = ""; editable = false; text.set(""); originalText = ""; preview = null;
        closeFolderPopup = true; requestFolderPopup = false; focusMove = 0; pressedFolder = ""; dragging = null; revealMove = false;
        cachedTree = null; cachedPaths = List.of(); cachedFolders = List.of(); treeDocuments = Map.of(); baseline = new byte[0];
    }

    void apply(AuthoringWorkspace workspace) throws IOException {
        if (!pending()) return;
        if (!java.util.Arrays.equals(workspace.readFile(selected), baseline)) throw new IllegalStateException("This file changed after it was opened. Discard the file edits and reopen it before editing");
        workspace.writeTextFile(selected, text.get());
        baseline = workspace.readFile(selected);
        originalText = text.get();
    }

    JsonObject recovery() {
        JsonObject result = new JsonObject();
        if (pending()) { result.addProperty("path", selected); result.addProperty("text", text.get()); }
        return result;
    }

    void recover(AuthoringWorkspace workspace, JsonObject state) throws IOException {
        if (!state.has("path") || !state.has("text")) return;
        reset(); select(state.get("path").getAsString()); load(workspace);
        if (!editable) throw new IOException("Recovered file is not editable text: " + selected);
        text.set(state.get("text").getAsString());
    }

    void renderTree(AuthoringWorkspace workspace, String search, Consumer<String> open, Consumer<String> manage,
                    Consumer<ImGuiAuthoringEditor.EditorOperation> schedule, Runnable changed) {
        var paths = workspace.filePaths();
        var folders = workspace.folderPaths();
        if (cachedTree == null || !cachedPaths.equals(paths) || !cachedFolders.equals(folders) || !cachedSearch.equals(search)) {
            treeDocuments = workspace.documents().stream().collect(java.util.stream.Collectors.toMap(AuthoringDocument::resourcePath, document -> document));
            String term = search.strip().toLowerCase(Locale.ROOT);
            var matching = paths.stream().filter(path -> path.toLowerCase(Locale.ROOT).contains(term)
                    || treeDocuments.containsKey(path) && EditorContentTools.matches(treeDocuments.get(path), search)).toList();
            cachedTree = tree(matching, folders.stream().filter(path -> path.toLowerCase(Locale.ROOT).contains(term)).toList(), "");
            cachedPaths = paths; cachedFolders = folders; cachedSearch = search;
        }
        if (ImGui.selectable("Pack root##pack_root", selected.isEmpty())) schedule.accept(() -> manage.accept(""));
        contextMenu("", false, workspace, open, manage, schedule);
        ImGui.textDisabled("Drag to move.\nRight-click for actions.");
        if (cachedTree.children.isEmpty()) ImGui.textWrapped("No files match this search.");
        for (var entry : cachedTree.children.entrySet()) renderNode(entry.getKey(), entry.getValue(), workspace, !search.isBlank(), 0, open, manage, schedule);
        reveal = false;
        if (ImGui.isMouseReleased(0)) pressedFolder = "";
        folderPopup(workspace, manage, schedule, changed);
    }

    private void renderNode(String label, Node node, AuthoringWorkspace workspace, boolean searching, int depth,
                            Consumer<String> open, Consumer<String> manage, Consumer<ImGuiAuthoringEditor.EditorOperation> schedule) {
        if (node.file) {
            AuthoringDocument document = treeDocuments.get(node.path);
            if (ImGui.selectable((document != null && document.dirty() ? "* " : "") + fileLabel(label) + "##file:" + node.path, node.path.equals(selected))) schedule.accept(() -> open.accept(node.path));
            if (ImGui.isItemHovered()) ImGui.setTooltip(node.path + (document == null ? "" : "\n" + document.id()));
            treeActions(node, workspace, open, manage, schedule);
            return;
        }
        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.OpenOnDoubleClick | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (node.path.equals(selected)) flags |= ImGuiTreeNodeFlags.Selected;
        if (searching || reveal && (selected.equals(node.path) || selected.startsWith(node.path + "/"))) ImGui.setNextItemOpen(true);
        else if (depth < 2) ImGui.setNextItemOpen(true, ImGuiCond.Once);
        boolean expanded = ImGui.treeNodeEx(label + "##folder:" + node.path, flags);
        if (ImGui.isItemClicked()) pressedFolder = ImGui.isItemToggledOpen() ? "" : node.path;
        if (node.path.equals(pressedFolder) && ImGui.isItemHovered() && ImGui.isMouseReleased(0)) {
            var delta = ImGui.getMouseDragDelta(0);
            if (delta.x == 0 && delta.y == 0) schedule.accept(() -> open.accept(node.path));
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(node.path + "/");
        treeActions(node, workspace, open, manage, schedule);
        if (expanded) {
            node.children.entrySet().stream().filter(entry -> !entry.getValue().file).forEach(entry -> renderNode(entry.getKey(), entry.getValue(), workspace, searching, depth + 1, open, manage, schedule));
            node.children.entrySet().stream().filter(entry -> entry.getValue().file).forEach(entry -> renderNode(entry.getKey(), entry.getValue(), workspace, searching, depth + 1, open, manage, schedule));
            ImGui.treePop();
        }
    }

    static String moveSource(AuthoringWorkspace workspace, String path) {
        var owner = workspace.documents().stream().filter(document -> document.resourcePath().equals(path)).findFirst().orElse(null);
        return owner != null && (owner.kind().campaignManifest() || owner.kind() == com.jvn.villagerretaliation.authoring.core.DefinitionKind.QUEST)
                ? path.substring(0, path.lastIndexOf('/')) : path;
    }

    static String dropDestination(AuthoringWorkspace workspace, String path, String folder) {
        if (!workspace.folderPaths().contains(folder)) throw new IllegalArgumentException("Drop onto a folder in this pack");
        String source = moveSource(workspace, path);
        return folder + "/" + source.substring(source.lastIndexOf('/') + 1);
    }

    private void treeActions(Node node, AuthoringWorkspace workspace, Consumer<String> open, Consumer<String> manage,
                             Consumer<ImGuiAuthoringEditor.EditorOperation> schedule) {
        String source = moveSource(workspace, node.path);
        if (source.split("/").length >= 3 && ImGui.beginDragDropSource()) {
            dragging = new FileDrag(workspace.directory(), source);
            ImGui.setDragDropPayload(dragging);
            ImGui.textUnformatted(source);
            ImGui.textDisabled("Drop on a folder to preview the move");
            ImGui.endDragDropSource();
        }
        if (!node.file && ImGui.beginDragDropTarget()) {
            FileDrag drag = ImGui.acceptDragDropPayload(FileDrag.class);
            if (drag != null && drag.workspace().equals(workspace.directory())) schedule.accept(() -> {
                String destination = dropDestination(workspace, drag.source(), node.path);
                var plan = workspace.previewFileMove(drag.source(), destination);
                manage.accept(drag.source());
                setMoveSource(destination);
                preview = plan; focusMove = 2; revealMove = true;
            });
            ImGui.endDragDropTarget();
        }
        contextMenu(node.path, node.file, workspace, open, manage, schedule);
    }

    private void contextMenu(String path, boolean file, AuthoringWorkspace workspace, Consumer<String> open, Consumer<String> manage,
                             Consumer<ImGuiAuthoringEditor.EditorOperation> schedule) {
        if (!ImGui.beginPopupContextItem("file_actions:" + path)) return;
        ImGui.textDisabled(path.isEmpty() ? "Pack root" : path);
        if (ImGui.menuItem("Open")) schedule.accept(() -> open.accept(path));
        String source = moveSource(workspace, path);
        ImGui.beginDisabled(source.split("/").length < 3);
        if (ImGui.menuItem("Rename...")) schedule.accept(() -> { manage.accept(source); setMoveSource(source); preview = null; focusMove = 1; });
        if (ImGui.menuItem("Move...")) schedule.accept(() -> { manage.accept(source); setMoveSource(source); preview = null; focusMove = 2; });
        ImGui.endDisabled();
        if (ImGui.menuItem("New folder...")) schedule.accept(() -> {
            folderParent = file ? path.substring(0, Math.max(0, path.lastIndexOf('/'))) : path;
            folderName.set(""); folderError = ""; closeFolderPopup = false; requestFolderPopup = true;
        });
        if (ImGui.menuItem("Copy pack path")) Minecraft.getInstance().keyboardHandler.setClipboard(path);
        ImGui.endPopup();
    }

    private void folderPopup(AuthoringWorkspace workspace, Consumer<String> manage, Consumer<ImGuiAuthoringEditor.EditorOperation> schedule, Runnable changed) {
        if (requestFolderPopup) { ImGui.openPopup("New folder##pack_folder"); requestFolderPopup = false; }
        if (!ImGui.beginPopupModal("New folder##pack_folder", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) return;
        if (closeFolderPopup) { ImGui.closeCurrentPopup(); closeFolderPopup = false; }
        ImGui.textUnformatted(folderParent.isEmpty() ? "In: pack root (data or assets)" : "In: " + folderParent);
        ImGui.setNextItemWidth(400);
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere();
        boolean create = ImGui.inputText("Folder name", folderName, ImGuiInputTextFlags.EnterReturnsTrue);
        if (!folderError.isEmpty()) ImGui.textWrapped(folderError);
        if (ImGui.button("Create folder") || create) schedule.accept(() -> {
            String name = folderName.get().strip();
            try {
                if (name.isEmpty() || name.contains("/") || name.contains("\\")) throw new IllegalArgumentException("Enter a single folder name");
                String target = folderParent.isEmpty() ? name : folderParent + "/" + name;
                workspace.createFolder(target);
                manage.accept(target); changed.run(); closeFolderPopup = true;
            } catch (IllegalArgumentException exception) { folderError = exception.getMessage(); throw exception; }
        });
        ImGui.sameLine();
        if (ImGui.button("Cancel") || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) ImGui.closeCurrentPopup();
        ImGui.endPopup();
    }

    private static String fileLabel(String name) {
        int limit = Math.max(12, (int) (ImGui.getContentRegionAvailX() / ImGui.calcTextSize("M").x) - 2);
        if (name.length() <= limit) return name;
        int dot = name.lastIndexOf('.');
        String suffix = dot >= 0 && name.length() - dot <= 12 ? name.substring(dot) : "";
        return name.substring(0, Math.max(3, limit - suffix.length() - 3)) + "..." + suffix;
    }

    private void load(AuthoringWorkspace workspace) throws IOException {
        String key = workspace.directory() + ":" + selected;
        if (loaded.equals(key)) return;
        loaded = key;
        if (!workspace.filePaths().contains(selected)) return;
        byte[] bytes = workspace.readFile(selected);
        baseline = bytes.clone();
        size = bytes.length;
        editable = bytes.length < 1_000_000 && textFile(selected);
        if (editable) {
            originalText = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            text.set(originalText);
        } else if (selected.endsWith(".png") && bytes.length >= 24 && bytes.length <= 4 * 1024 * 1024) {
            var dimensions = java.nio.ByteBuffer.wrap(bytes);
            int width = dimensions.getInt(16), height = dimensions.getInt(20);
            if (width > 0 && height > 0 && (long) width * height <= 16 * 1024 * 1024)
                image = new DynamicTexture(com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(bytes)));
        }
    }

    static boolean textFile(String path) {
        return List.of(".json", ".mcmeta", ".md", ".txt", ".mcfunction", ".lang").stream().anyMatch(path::endsWith);
    }

    void render(AuthoringWorkspace workspace, String output, Consumer<String> open,
                Consumer<ImGuiAuthoringEditor.EditorOperation> schedule, Consumer<ImGuiAuthoringEditor.EditorOperation> attempt, Runnable changed) {
        ImGui.textUnformatted(selected.isEmpty() ? "Pack files" : selected);
        ImGui.textWrapped("These paths match the built datapack. Drafts are stored in workspace.json. Build the pack to write this file tree to disk.");
        if (!selected.isEmpty() && ImGui.button("Copy pack path")) Minecraft.getInstance().keyboardHandler.setClipboard(selected);
        if (workspace.filePaths().contains(selected)) {
            try {
                load(workspace);
                ImGui.textDisabled(size + " bytes");
                if (editable) {
                    ImGui.inputTextMultiline("##pack_file_text", text, -1, Math.max(160, Math.min(360, ImGui.getContentRegionAvailY() * .5f)), ImGuiInputTextFlags.AllowTabInput);
                    ImGui.beginDisabled(!pending());
                    if (ImGui.button("Apply file edits")) schedule.accept(() -> { apply(workspace); changed.run(); });
                    ImGui.sameLine();
                    if (ImGui.button("Discard file edits")) attempt.accept(() -> { text.set(originalText); close(); });
                    ImGui.endDisabled();
                } else if (image != null && image.getPixels() != null) {
                    var pixels = image.getPixels();
                    float scale = Math.min(1, Math.min(ImGui.getContentRegionAvailX() / pixels.getWidth(), 300F / pixels.getHeight()));
                    ImGui.image(image.getId(), pixels.getWidth() * scale, pixels.getHeight() * scale);
                } else ImGui.textWrapped("This asset is included unchanged in the pack. Text previews are limited to 1 MB and artwork previews to PNG files.");
            } catch (IOException exception) { editable = false; loaded = ""; ImGui.textWrapped("Could not open file: " + exception.getMessage()); }
            try {
                Path built = Path.of(output).resolve(selected);
                if (Files.isRegularFile(built) && ImGui.button("Open built file")) net.minecraft.Util.getPlatform().openFile(built.toFile());
            } catch (java.nio.file.InvalidPathException exception) { ImGui.textDisabled("Choose an output folder to open built files."); }
        } else {
            var entries = contents(workspace, selected);
            long folders = entries.stream().filter(Entry::folder).count(), files = entries.size() - folders;
            ImGui.textUnformatted(folders + (folders == 1 ? " folder, " : " folders, ") + files + (files == 1 ? " file" : " files"));
            ImGui.setNextItemOpen(true, ImGuiCond.Once);
            if (ImGui.collapsingHeader("Folder contents##" + selected)) for (Entry entry : entries) {
                String label = entry.path().substring(entry.path().lastIndexOf('/') + 1) + (entry.folder() ? "/" : "");
                if (ImGui.selectable(label + "##contents:" + entry.path())) open.accept(entry.path());
                if (ImGui.isItemHovered()) ImGui.setTooltip(entry.path());
            }
        }
        if (selected.isEmpty() || selected.equals("pack.mcmeta") || selected.equals("pack.png")) return;
        ImGui.separator();
        String source = moveSource(workspace, selected);
        var owner = workspace.documents().stream().filter(value -> value.resourcePath().equals(selected)).findFirst().orElse(null);
        if (owner != null && (owner.kind().campaignManifest() || owner.kind() == com.jvn.villagerretaliation.authoring.core.DefinitionKind.QUEST)) {
            source = selected.substring(0, selected.lastIndexOf('/'));
            ImGui.textWrapped("This action includes the entire owning folder and its companion files.");
            if (preview == null && name.get().equals(selected.substring(selected.lastIndexOf('/') + 1))) setMoveSource(source);
        }
        ImGui.textUnformatted("Move or rename");
        ImGui.textDisabled("From: " + source);
        ImGui.textUnformatted("Name"); ImGui.setNextItemWidth(-1);
        if (focusMove == 1) { ImGui.setKeyboardFocusHere(); focusMove = 0; }
        if (ImGui.inputText("##move_name", name)) preview = null;
        ImGui.textUnformatted("Destination folder in pack"); ImGui.setNextItemWidth(-1);
        if (focusMove == 2) { ImGui.setKeyboardFocusHere(); focusMove = 0; }
        if (ImGui.inputText("##move_parent", parent)) preview = null;
        if (ImGui.beginCombo("Choose folder", "Existing folders...")) {
            for (String folder : workspace.folderPaths()) if (ImGui.selectable(folder)) { parent.set(folder); preview = null; }
            ImGui.endCombo();
        }
        String from = source;
        if (ImGui.button("Preview move")) schedule.accept(() -> {
            if (name.get().isBlank()) throw new IllegalArgumentException("Enter a file or folder name");
            preview = workspace.previewFileMove(from, parent.get().isBlank() ? name.get() : parent.get() + "/" + name.get());
        });
        if (preview != null) {
            ImGui.textWrapped((preview.paths().isEmpty() ? "Empty folder" : preview.paths().size() + (preview.paths().size() == 1 ? " file" : " files")) + " will move to " + preview.destination());
            for (String change : preview.changes()) ImGui.textWrapped(change);
            if (ImGui.collapsingHeader("Affected files")) preview.paths().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> ImGui.textWrapped(entry.getKey() + " -> " + entry.getValue()));
            if (ImGui.button("Apply move")) schedule.accept(() -> {
                var plan = preview; workspace.applyFileMove(plan); select(plan.destination()); changed.run();
            });
            if (revealMove) { ImGui.setScrollHereY(1); revealMove = false; }
        }
        ImGui.beginDisabled(!workspace.hasFileMove());
        if (ImGui.button("Undo last file move")) schedule.accept(() -> { workspace.undoFileMove(); reset(); changed.run(); });
        ImGui.endDisabled();
    }

    void invalidate() { cachedTree = null; if (!pending()) close(); }

    @Override public void close() { if (image != null) image.close(); image = null; if (!pending()) loaded = ""; }
}
