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

    private String selected = "";
    private String loaded = "";
    private String originalText = "";
    private final ImString text = new ImString(1_048_576);
    private final ImString name = new ImString(512);
    private final ImString parent = new ImString(4096);
    private AuthoringWorkspace.FileMove preview;
    private boolean editable, reveal;
    private DynamicTexture image;
    private int size;
    private byte[] baseline = new byte[0];
    private Node cachedTree;
    private List<String> cachedPaths = List.of();
    private String cachedSearch = "";
    private Map<String, AuthoringDocument> treeDocuments = Map.of();

    static Node tree(List<String> paths, String search) {
        Node root = new Node("");
        String term = search.strip().toLowerCase(Locale.ROOT);
        for (String path : paths) {
            if (!path.toLowerCase(Locale.ROOT).contains(term)) continue;
            Node node = root;
            for (String part : path.split("/")) {
                String child = node.path.isEmpty() ? part : node.path + "/" + part;
                node = node.children.computeIfAbsent(part, ignored -> new Node(child));
            }
            node.file = true;
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
        cachedTree = null; cachedPaths = List.of(); treeDocuments = Map.of(); baseline = new byte[0];
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

    void renderTree(AuthoringWorkspace workspace, String search, Consumer<String> open) {
        var paths = workspace.filePaths();
        if (cachedTree == null || !cachedPaths.equals(paths) || !cachedSearch.equals(search)) {
            treeDocuments = workspace.documents().stream().collect(java.util.stream.Collectors.toMap(AuthoringDocument::resourcePath, document -> document));
            var matching = paths.stream().filter(path -> path.toLowerCase(Locale.ROOT).contains(search.strip().toLowerCase(Locale.ROOT))
                    || treeDocuments.containsKey(path) && EditorContentTools.matches(treeDocuments.get(path), search)).toList();
            cachedTree = tree(matching, ""); cachedPaths = paths; cachedSearch = search;
        }
        if (cachedTree.children.isEmpty()) ImGui.textWrapped("No files match this search.");
        for (var entry : cachedTree.children.entrySet()) renderNode(entry.getKey(), entry.getValue(), workspace, !search.isBlank(), 0, open);
        reveal = false;
    }

    private void renderNode(String label, Node node, AuthoringWorkspace workspace, boolean searching, int depth, Consumer<String> open) {
        if (node.file) {
            AuthoringDocument document = treeDocuments.get(node.path);
            if (ImGui.selectable((document != null && document.dirty() ? "* " : "") + fileLabel(label) + "##file:" + node.path, node.path.equals(selected))) open.accept(node.path);
            if (ImGui.isItemHovered()) ImGui.setTooltip(node.path + (document == null ? "" : "\n" + document.id()));
            return;
        }
        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.OpenOnDoubleClick | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (node.path.equals(selected)) flags |= ImGuiTreeNodeFlags.Selected;
        if (searching || reveal && (selected.equals(node.path) || selected.startsWith(node.path + "/"))) ImGui.setNextItemOpen(true);
        else if (depth < 2) ImGui.setNextItemOpen(true, ImGuiCond.Once);
        boolean expanded = ImGui.treeNodeEx(label + "##folder:" + node.path, flags);
        if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) open.accept(node.path);
        if (ImGui.isItemHovered()) ImGui.setTooltip(node.path + "/");
        if (expanded) {
            node.children.entrySet().stream().filter(entry -> !entry.getValue().file).forEach(entry -> renderNode(entry.getKey(), entry.getValue(), workspace, searching, depth + 1, open));
            node.children.entrySet().stream().filter(entry -> entry.getValue().file).forEach(entry -> renderNode(entry.getKey(), entry.getValue(), workspace, searching, depth + 1, open));
            ImGui.treePop();
        }
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
        List<String> files = workspace.filePaths().stream().filter(path -> selected.isEmpty() || path.equals(selected) || path.startsWith(selected + "/")).toList();
        if (files.size() == 1 && files.getFirst().equals(selected)) {
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
            ImGui.textUnformatted(files.size() + " files in this folder");
            ImGui.setNextItemOpen(true, ImGuiCond.Once);
            if (ImGui.collapsingHeader("Folder contents##" + selected)) for (String file : files)
                if (ImGui.selectable((selected.isEmpty() ? file : file.substring(selected.length() + 1)) + "##contents:" + file)) open.accept(file);
        }
        if (selected.isEmpty() || selected.equals("pack.mcmeta") || selected.equals("pack.png")) return;
        ImGui.separator();
        String source = selected;
        var owner = workspace.documents().stream().filter(value -> value.resourcePath().equals(selected)).findFirst().orElse(null);
        if (owner != null && (owner.kind().campaignManifest() || owner.kind() == com.jvn.villagerretaliation.authoring.core.DefinitionKind.QUEST)) {
            source = selected.substring(0, selected.lastIndexOf('/'));
            ImGui.textWrapped("This action includes the entire owning folder and its companion files.");
            if (preview == null && name.get().equals(selected.substring(selected.lastIndexOf('/') + 1))) setMoveSource(source);
        }
        ImGui.textUnformatted("Move or rename");
        ImGui.textDisabled("From: " + source);
        ImGui.textUnformatted("Name"); ImGui.setNextItemWidth(-1);
        if (ImGui.inputText("##move_name", name)) preview = null;
        ImGui.textUnformatted("Destination folder in pack"); ImGui.setNextItemWidth(-1);
        if (ImGui.inputText("##move_parent", parent)) preview = null;
        if (ImGui.beginCombo("Choose folder", "Existing folders...")) {
            var folders = new java.util.TreeSet<String>();
            for (String file : workspace.filePaths()) for (int slash = file.lastIndexOf('/'); slash >= 0; slash = file.lastIndexOf('/', slash - 1)) folders.add(file.substring(0, slash));
            for (String folder : folders) if (ImGui.selectable(folder)) { parent.set(folder); preview = null; }
            ImGui.endCombo();
        }
        String from = source;
        if (ImGui.button("Preview move")) schedule.accept(() -> {
            if (name.get().isBlank()) throw new IllegalArgumentException("Enter a file or folder name");
            preview = workspace.previewFileMove(from, parent.get().isBlank() ? name.get() : parent.get() + "/" + name.get());
        });
        if (preview != null) {
            ImGui.textWrapped(preview.paths().size() + " files will move to " + preview.destination());
            for (String change : preview.changes()) ImGui.textWrapped(change);
            if (ImGui.collapsingHeader("Affected files")) preview.paths().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> ImGui.textWrapped(entry.getKey() + " -> " + entry.getValue()));
            if (ImGui.button("Apply move")) schedule.accept(() -> {
                var plan = preview; workspace.applyFileMove(plan); select(plan.destination()); changed.run();
            });
        }
        ImGui.beginDisabled(!workspace.hasFileMove());
        if (ImGui.button("Undo last file move")) schedule.accept(() -> { workspace.undoFileMove(); reset(); changed.run(); });
        ImGui.endDisabled();
    }

    void invalidate() { cachedTree = null; if (!pending()) close(); }

    @Override public void close() { if (image != null) image.close(); image = null; if (!pending()) loaded = ""; }
}
