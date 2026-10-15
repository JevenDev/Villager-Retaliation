package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview;
import com.jvn.villagerretaliation.authoring.preview.PreviewConditions;
import foundry.imgui.api.ImGuiMCEvents;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

import java.io.IOException;
import com.google.gson.JsonParseException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ImGuiAuthoringEditor {
    static final net.minecraft.client.KeyMapping OPEN_EDITOR = new net.minecraft.client.KeyMapping(
            "key.villagerretaliation.open_authoring_editor", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_F8, "key.categories.villagerretaliation");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Minecraft MC = Minecraft.getInstance();
    private static final ImGuiAuthoringEditor INSTANCE = new ImGuiAuthoringEditor();
    private final ImString workspacePath = new ImString(4096);
    private final ImString importPath = new ImString(4096);
    private final ImString exportPath = new ImString(4096);
    private final ImString filter = new ImString(256);
    private final ImString newId = new ImString("example:new_dialogue", 512);
    private final ImString nodeName = new ImString("new_node", 256);
    private final ImString responseLabel = new ImString("Continue", 1024);
    private final ImString nextStage = new ImString(256);
    private final ImString jsonText = new ImString(1_048_576);
    private final ImString pointer = new ImString(4096);
    private final ImString fieldName = new ImString(256);
    private final ImString fieldValue = new ImString("{}", 8192);
    private final Map<String, ImString> fields = new HashMap<>();
    private final EditorFieldEdits pendingFields = new EditorFieldEdits();
    private final java.util.ArrayDeque<EditorOperation> operations = new java.util.ArrayDeque<>();
    private boolean rendering;
    private final Map<String, float[]> positions = new HashMap<>();
    private final PreviewConditions conditions = new PreviewConditions();
    private final EditorScreen screen = new EditorScreen();
    private AuthoringWorkspace workspace;
    private AuthoringDocument document;
    private DefinitionKind createKind = DefinitionKind.TREE;
    private List<AuthoringDiagnostic> diagnostics = List.of();
    private String selectedNode = "";
    private String selectedEdge = "";
    private String status = "Create or open a workspace to begin.";
    private String draggingNode;
    private float panX = 25;
    private float panY = 25;
    private float zoom = 1;
    private boolean rawModified;
    private boolean initializedLayout;
    private boolean escapeReady;
    private boolean confirmDelete;
    private Entity target;

    static void initialize() {
        ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(INSTANCE::render);
    }

    static void open() {
        if (MC.player == null || MC.level == null) return;
        if (MC.hitResult instanceof EntityHitResult hit) INSTANCE.target = hit.getEntity();
        MC.setScreen(INSTANCE.screen);
    }

    static void tick() {
        EditorCameraPanel.tick(INSTANCE.screen);
        if (INSTANCE.target != null && (MC.level != INSTANCE.target.level() || INSTANCE.target.isRemoved())) INSTANCE.target = null;
    }

    static void disconnect() {
        AuthoringDialoguePreview.stop();
        EditorCameraPanel.close();
        INSTANCE.target = null;
        if (MC.screen == INSTANCE.screen) MC.setScreen(null);
    }

    private ImGuiAuthoringEditor() {
        Path root = MC.gameDirectory.toPath().resolve("authoring");
        workspacePath.set(root.resolve("workspaces/default").toAbsolutePath().toString());
        exportPath.set(root.resolve("exports/pack").toAbsolutePath().toString());
        importPath.set(root.toAbsolutePath().toString());
    }

    private void render() {
        if (MC.screen != screen) return;
        if (!initializedLayout) {
            ImGui.getIO().addConfigFlags(ImGuiConfigFlags.DockingEnable);
            initializedLayout = true;
        }
        if (!ImGui.isKeyDown(ImGuiKey.Escape)) escapeReady = true;
        if (escapeReady && ImGui.isKeyPressed(ImGuiKey.Escape)) {
            screen.onClose();
            return;
        }
        rendering = true;
        try {
            if (ImGui.getIO().getKeyCtrl()) {
                if (ImGui.isKeyPressed(ImGuiKey.S)) operation(this::save);
                if (document != null && !ImGui.getIO().getWantTextInput()) {
                    if (ImGui.isKeyPressed(ImGuiKey.Z)) operation(() -> changeHistory(false));
                    if (ImGui.isKeyPressed(ImGuiKey.Y)) operation(() -> changeHistory(true));
                }
            }
            ImGui.dockSpaceOverViewport();
            browser();
            graph();
            inspector();
            preview();
            diagnostics();
        } finally {
            rendering = false;
            while (!operations.isEmpty()) runOperation(operations.removeFirst());
        }
    }

    private void panel(String title, float x, float y, float width, float height) {
        float scale = Math.min(1, Math.min(ImGui.getIO().getDisplaySizeX() / 1500f, ImGui.getIO().getDisplaySizeY() / 930f));
        ImGui.setNextWindowPos(x * scale, y * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(Math.min(width * scale, ImGui.getIO().getDisplaySizeX() - x * scale - 8),
                Math.max(100, Math.min(height * scale, ImGui.getIO().getDisplaySizeY() - y * scale - 8)), ImGuiCond.FirstUseEver);
        ImGui.begin(title);
    }

    private void browser() {
        panel("Content browser", 8, 8, 340, 680);
        ImGui.inputText("Workspace", workspacePath);
        if (ImGui.button("Create workspace")) operation(() -> {
            if (workspace != null) workspace.save();
            workspace = AuthoringWorkspace.create(Path.of(workspacePath.get()));
            select(null);
            status = "Workspace created.";
        });
        ImGui.sameLine();
        if (ImGui.button("Open")) operation(() -> {
            if (workspace != null) workspace.save();
            workspace = AuthoringWorkspace.open(Path.of(workspacePath.get()));
            select(null);
            status = "Workspace opened.";
        });
        if (workspace != null) {
            if (ImGui.button("Save drafts  Ctrl+S")) operation(this::save);
            ImGui.inputText("Import pack folder", importPath);
            if (ImGui.button("Import content")) operation(() -> {
                int count = workspace.importPack(Path.of(importPath.get()));
                status = "Imported " + count + " resources.";
                validate();
            });
            ImGui.inputText("Export new pack folder", exportPath);
            if (ImGui.button("Validate and export")) operation(() -> {
                validate();
                workspace.exportPack(Path.of(exportPath.get()));
                status = "Exported to " + exportPath.get();
            });
            ImGui.separator();
            ImGui.inputTextWithHint("##search", "Search IDs and paths", filter);
            if (ImGui.beginCombo("New kind", createKind.name())) {
                for (DefinitionKind kind : DefinitionKind.values()) {
                    if (kind != DefinitionKind.RESOURCE && ImGui.selectable(kind.name(), kind == createKind)) createKind = kind;
                }
                ImGui.endCombo();
            }
            ImGui.inputText("Resource ID", newId);
            if (ImGui.button("Create definition")) operation(() -> select(workspace.createDocument(createKind, newId.get())));
            if (document != null) {
                if (ImGui.button("Rename to ID")) operation(() -> { workspace.rename(document, newId.get()); refresh(); });
                ImGui.sameLine();
                if (ImGui.button("Duplicate to ID")) operation(() -> select(workspace.duplicate(document, newId.get())));
                if (ImGui.button("Delete definition")) confirmDelete = true;
                if (confirmDelete) {
                    ImGui.textWrapped("Delete this draft? Save commits the removal to this workspace.");
                    if (ImGui.button("Confirm delete")) operation(() -> {
                        workspace.remove(document);
                        select(null);
                        confirmDelete = false;
                    });
                    ImGui.sameLine();
                    if (ImGui.button("Cancel")) confirmDelete = false;
                }
            }
            ImGui.separator();
            for (AuthoringDocument candidate : workspace.documents()) {
                if (!(candidate.resourcePath() + candidate.id()).toLowerCase(Locale.ROOT).contains(filter.get().toLowerCase(Locale.ROOT))) continue;
                if (ImGui.selectable((candidate.dirty() ? "* " : "") + candidate.kind() + " " + candidate.id()
                        + "##" + candidate.resourcePath(), document == candidate)) operation(() -> select(candidate));
                if (ImGui.isItemHovered()) ImGui.setTooltip(candidate.resourcePath());
            }
        }
        ImGui.separator();
        ImGui.textWrapped(status);
        ImGui.end();
    }

    private void graph() {
        panel("Dialogue graph", 356, 8, 760, 440);
        if (document == null) {
            ImGui.textWrapped("Select a dialogue definition.");
            ImGui.end();
            return;
        }
        if (document.kind() == DefinitionKind.RESOURCE) {
            ImGui.textWrapped("Edit resource pools and localization in Properties. Select a dialogue-bearing object to preview it.");
            ImGui.end();
            return;
        }
        List<DialogueGraph.Node> nodes;
        List<DialogueGraph.Edge> edges;
        try {
            nodes = DialogueGraph.nodes(document);
            edges = DialogueGraph.edges(document);
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException
                | UnsupportedOperationException | IndexOutOfBoundsException exception) {
            ImGui.textWrapped("Repair the definition in Advanced full JSON: " + exception.getMessage());
            ImGui.end();
            return;
        }
        ImGui.inputText(document.kind() == DefinitionKind.QUEST ? "Stage ID" : "Node / option ID", nodeName);
        ImGui.beginDisabled(document.kind() == DefinitionKind.FORCED && !document.json().has("entries"));
        if (ImGui.button(document.kind() == DefinitionKind.QUEST ? "Add stage" : "Add node")) operation(() -> {
            DialogueGraph.addNode(document, nodeName.get());
            selectedNode = document.kind() == DefinitionKind.FORCED
                    ? DialogueGraph.nodes(document).getLast().id() : nodeName.get();
            refresh();
        });
        ImGui.endDisabled();
        if (nodes.stream().anyMatch(node -> node.id().equals(selectedNode))) {
            ImGui.sameLine();
            if (ImGui.button("Duplicate")) operation(() -> { DialogueGraph.duplicateNode(document, selectedNode, nodeName.get()); refresh(); });
            ImGui.sameLine();
            if (ImGui.button("Remove")) operation(() -> { DialogueGraph.removeNode(document, selectedNode); selectedNode = ""; refresh(); });
            ImGui.beginDisabled(document.kind() == DefinitionKind.FORCED);
            if (ImGui.button("Set entry")) operation(() -> { DialogueGraph.setEntry(document, selectedNode); refresh(); });
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button("Set terminal")) operation(() -> { DialogueGraph.setTerminal(document, selectedNode, true); refresh(); });
            if (document.kind() == DefinitionKind.QUEST) {
                if (ImGui.beginCombo("Next stage", nextStage.get())) {
                    for (var destination : DialogueGraph.nodes(document)) {
                        if (ImGui.selectable(destination.id(), destination.id().equals(nextStage.get()))) nextStage.set(destination.id());
                    }
                    ImGui.endCombo();
                }
                if (ImGui.button("Connect next stage")) operation(() -> {
                    EditorDocumentOperations.nextStage(document, selectedNode, nextStage.get());
                    refresh();
                });
            } else {
                ImGui.inputText(document.kind() == DefinitionKind.CONVERSATION ? "Response localization key" : "Response text", responseLabel);
                if (ImGui.button("Add response to selected node")) operation(() -> {
                    DialogueGraph.addResponse(document, selectedNode, nodeName.get(), responseLabel.get(),
                            document.kind() == DefinitionKind.FORCED ? null : selectedNode);
                    refresh();
                });
                if (document.kind() == DefinitionKind.FORCED && ImGui.button("Add follow-up branch")) operation(() -> {
                    String id = nodeName.get();
                    if (id.isBlank()) throw new IllegalArgumentException("Enter an option ID.");
                    document.edit("Add follow-up branch", root -> {
                        JsonObject owner = JsonPointers.get(root, selectedNode).getAsJsonObject();
                        if (!owner.has("options")) owner.add("options", new JsonArray());
                        JsonArray options = owner.getAsJsonArray("options");
                        for (JsonElement value : options) {
                            if (value.isJsonObject() && id.equals(DialogueGraph.string(value.getAsJsonObject(), "id")))
                                throw new IllegalArgumentException("Duplicate response ID " + id);
                        }
                        JsonObject followUp = DialogueGraph.defaultNode(DefinitionKind.FORCED, id);
                        followUp.remove("id");
                        followUp.remove("trigger");
                        followUp.add("options", new JsonArray());
                        JsonObject response = new JsonObject();
                        response.addProperty("id", id);
                        response.addProperty("label", responseLabel.get());
                        response.addProperty("end_conversation", false);
                        response.add("follow_up", followUp);
                        options.add(response);
                    });
                    refresh();
                });
            }
        }
        ImGui.textUnformatted("Drag nodes. Middle-drag to pan. Wheel to zoom. Select an edge below, then click a destination node.");
        float canvasX = ImGui.getCursorScreenPosX();
        float canvasY = ImGui.getCursorScreenPosY();
        float canvasWidth = Math.max(100, ImGui.getContentRegionAvailX());
        float canvasHeight = Math.max(100, ImGui.getContentRegionAvailY() - 90);
        ImGui.invisibleButton("graph_canvas", canvasWidth, canvasHeight);
        boolean hover = ImGui.isItemHovered();
        if (hover && ImGui.isMouseDragging(2)) {
            panX += ImGui.getIO().getMouseDeltaX();
            panY += ImGui.getIO().getMouseDeltaY();
        }
        if (hover && ImGui.getIO().getMouseWheel() != 0) {
            float oldZoom = zoom;
            zoom = Math.clamp(zoom * (float) Math.pow(1.15, ImGui.getIO().getMouseWheel()), .35f, 2f);
            float mx = ImGui.getIO().getMousePosX() - canvasX;
            float my = ImGui.getIO().getMousePosY() - canvasY;
            panX = mx - (mx - panX) * zoom / oldZoom;
            panY = my - (my - panY) * zoom / oldZoom;
        }
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.pushClipRect(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, true);
        draw.addRectFilled(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, 0xff242321);
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            final int index = i;
            positions.computeIfAbsent(node.id(), key -> new float[] {(index % 3) * 240, (index / 3) * 110});
        }
        for (var edge : edges) {
            float[] from = positions.get(edge.from());
            float[] to = positions.get(edge.to());
            if (from == null || to == null) continue;
            float x1 = canvasX + panX + (from[0] + 185) * zoom;
            float y1 = canvasY + panY + (from[1] + 30) * zoom;
            float x2 = canvasX + panX + to[0] * zoom;
            float y2 = canvasY + panY + (to[1] + 30) * zoom;
            draw.addBezierCubic(x1, y1, x1 + 60 * zoom, y1, x2 - 60 * zoom, y2, x2, y2,
                    edge.pointer().equals(selectedEdge) ? 0xff81c9ed : 0xff8d8b79, 2);
        }
        for (var node : nodes) {
            float[] position = positions.get(node.id());
            float x = canvasX + panX + position[0] * zoom;
            float y = canvasY + panY + position[1] * zoom;
            float w = 185 * zoom;
            float h = 65 * zoom;
            draw.addRectFilled(x, y, x + w, y + h, selectedNode.equals(node.id()) ? 0xff65513d : 0xff423c34, 4);
            draw.addRect(x, y, x + w, y + h, node.entry() ? 0xff9cc6a2 : 0xff78716b, 4);
            draw.addText(x + 8, y + 7, 0xfff2f0e9, shortText(node.id(), 23));
            draw.addText(x + 8, y + 28, 0xffcbc7bf, shortText(node.label(), 23));
            draw.addText(x + 8, y + 47, 0xffb4ab9c, node.entry() ? "ENTRY" : node.terminal() ? "END" : "");
            boolean overNode = hover && ImGui.isMouseHoveringRect(x, y, x + w, y + h);
            if (overNode && ImGui.isMouseClicked(0)) {
                String edge = selectedEdge;
                operation(() -> {
                    selectedNode = node.id();
                    setPointer(node.pointer());
                    if (!edge.isEmpty()) {
                        DialogueGraph.connect(document, edge, node.id());
                        selectedEdge = "";
                        refresh();
                    } else draggingNode = node.id();
                });
            }
            if (node.id().equals(draggingNode) && ImGui.isMouseDragging(0)) {
                position[0] += ImGui.getIO().getMouseDeltaX() / zoom;
                position[1] += ImGui.getIO().getMouseDeltaY() / zoom;
            }
        }
        draw.popClipRect();
        if (draggingNode != null && ImGui.isMouseReleased(0)) {
            Map<String, float[]> movedPositions = new HashMap<>();
            positions.forEach((id, position) -> movedPositions.put(id, position.clone()));
            operation(() -> { persistPositions(movedPositions); refresh(); });
            draggingNode = null;
        }
        if (nodes.stream().anyMatch(node -> node.id().equals(selectedNode))) {
            for (var edge : edges) {
                if (!edge.from().equals(selectedNode)) continue;
                if (ImGui.selectable(edge.label() + " -> " + edge.to() + "##" + edge.pointer(), edge.pointer().equals(selectedEdge))) {
                    operation(() -> {
                        selectedEdge = edge.pointer();
                        setPointer(edge.pointer());
                    });
                }
            }
        }
        boolean responseEdge = selectedEdge.contains("/responses/") || selectedEdge.contains("/options/");
        if (document.kind() == DefinitionKind.QUEST && !selectedEdge.isEmpty() && ImGui.button("End at selected source stage")) operation(() -> {
            var source = DialogueGraph.edges(document).stream().filter(edge -> edge.pointer().equals(selectedEdge)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Select an existing stage connection."));
            DialogueGraph.setTerminal(document, source.from(), true);
            selectedEdge = "";
            refresh();
        });
        if (responseEdge && ImGui.button("Remove selected response")) operation(() -> {
            DialogueGraph.removeResponse(document, selectedEdge);
            selectedEdge = "";
            refresh();
        });
        ImGui.end();
    }

    private void inspector() {
        panel("Properties", 1124, 8, 370, 680);
        if (document == null) {
            ImGui.textUnformatted("No definition selected.");
            ImGui.end();
            return;
        }
        ImGui.textWrapped(document.resourcePath());
        ImGui.beginDisabled(!document.canUndo() || rawModified);
        if (ImGui.button("Undo")) operation(() -> changeHistory(false));
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(!document.canRedo() || rawModified);
        if (ImGui.button("Redo")) operation(() -> changeHistory(true));
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Whole definition")) operation(() -> setPointer(""));
        ImGui.textWrapped("Text and numeric edits apply together on Enter, Save or another editing action. Expand arrays to duplicate, reorder and remove items.");
        if (pendingFields.pending()) {
            if (ImGui.button("Apply field edits")) operation(() -> { });
            ImGui.sameLine();
            if (ImGui.button("Discard field edits")) attempt(() -> { pendingFields.clear(); fields.clear(); });
        }
        ImGui.inputText("JSON pointer", pointer);
        if (ImGui.button("Inspect pointer")) operation(() -> { });
        if (!pointer.get().isEmpty() && ImGui.button("Remove selected field")) operation(() -> {
            document.edit("Remove field", root -> JsonPointers.remove(root, pointer.get()));
            setPointer("");
            refresh();
        });
        try {
            inspectValue(JsonPointers.get(document.json(), pointer.get()), pointer.get(), "Selected", 0);
        } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException
                | ClassCastException | UnsupportedOperationException exception) {
            ImGui.textWrapped("Select an existing JSON object or field: " + exception.getMessage());
        }
        ImGui.separator();
        conditionCatalog();
        ImGui.textWrapped("Add an object field or append to an array. JSON values preserve their types.");
        ImGui.inputText("Field name", fieldName);
        ImGui.inputTextMultiline("Value JSON", fieldValue, -1, 80);
        if (ImGui.button("Add field / append")) operation(() -> {
            JsonElement value = JsonParser.parseString(fieldValue.get());
            document.edit("Add field", root -> {
                JsonElement parent = JsonPointers.get(root, pointer.get());
                if (parent != null && parent.isJsonObject()) {
                    if (fieldName.get().isBlank()) throw new IllegalArgumentException("Enter a field name.");
                    if (parent.getAsJsonObject().has(fieldName.get()))
                        throw new IllegalArgumentException("This field already exists. Edit it in Properties instead.");
                    parent.getAsJsonObject().add(fieldName.get(), value);
                }
                else if (parent != null && parent.isJsonArray()) parent.getAsJsonArray().add(value);
                else throw new IllegalArgumentException("Select an object or array first.");
            });
            refresh();
        });
        if (ImGui.collapsingHeader("Advanced full JSON")) {
            ImGui.beginDisabled(pendingFields.pending());
            if (ImGui.inputTextMultiline("##raw", jsonText, -1, 260)) rawModified = true;
            ImGui.endDisabled();
            if (pendingFields.pending()) ImGui.textWrapped("Apply or discard field edits before editing full JSON.");
            if (ImGui.button("Apply JSON")) attempt(() -> {
                if (pendingFields.pending()) throw new IllegalStateException("Apply or discard field edits before applying full JSON.");
                document.replaceJson(jsonText.get());
                refresh();
            });
            ImGui.sameLine();
            if (ImGui.button("Discard JSON buffer")) attempt(() -> {
                if (pendingFields.pending()) throw new IllegalStateException("Apply or discard field edits first.");
                refresh();
            });
            if (rawModified) ImGui.textWrapped("Unapplied JSON buffer. Apply before saving, previewing or switching definitions.");
        }
        ImGui.end();
    }

    private void conditionCatalog() {
        if (!ImGui.collapsingHeader("Runtime condition catalog")) return;
        ImGui.textWrapped("Select a runtime condition type to prepare a JSON value. Add it to a selected conditions array, or add a conditions field to an object.");
        for (var descriptor : com.jvn.villagerretaliation.dialogue.DialogueCondition.descriptors()) {
            if (ImGui.selectable(descriptor.id())) {
                JsonObject condition = new JsonObject();
                condition.addProperty("type", descriptor.id());
                fieldValue.set(JSON.toJson(condition));
                status = "Prepared " + descriptor.id() + ". Fill required values using its schema, then append to a conditions array.";
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(JSON.toJson(descriptor.schema()));
        }
    }

    private void inspectValue(JsonElement value, String path, String label, int depth) {
        if (depth > 16) { ImGui.textUnformatted("Use advanced JSON for deeper nesting."); return; }
        ImGui.pushID(path);
        if (value.isJsonObject()) {
            if (ImGui.treeNode(label)) {
                if (ImGui.smallButton("Select object")) operation(() -> setPointer(path));
                for (var entry : value.getAsJsonObject().entrySet()) {
                    inspectValue(entry.getValue(), path + "/" + escape(entry.getKey()), entry.getKey(), depth + 1);
                }
                ImGui.treePop();
            }
        } else if (value.isJsonArray()) {
            if (ImGui.treeNode(label + " [" + value.getAsJsonArray().size() + "]")) {
                if (ImGui.smallButton("Select array")) operation(() -> setPointer(path));
                var array = value.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    final int index = i;
                    ImGui.pushID(i);
                    inspectValue(array.get(i), path + "/" + i, "[" + i + "]", depth + 1);
                    if (ImGui.smallButton("Remove")) editArray(path, values -> values.remove(index));
                    ImGui.sameLine();
                    if (ImGui.smallButton("Duplicate")) editArray(path, values -> values.add(values.get(index).deepCopy()));
                    if (i > 0) {
                        ImGui.sameLine();
                        if (ImGui.smallButton("Move up")) editArray(path, values -> {
                            JsonElement previous = values.get(index - 1);
                            values.set(index - 1, values.get(index));
                            values.set(index, previous);
                        });
                    }
                    ImGui.popID();
                }
                ImGui.treePop();
            }
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            if (ImGui.checkbox(label, value.getAsBoolean())) operation(() -> replaceValue(path, new JsonPrimitive(!value.getAsBoolean())));
        } else {
            ImString input = fields.computeIfAbsent(path, key -> new ImString(
                    value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : value.toString(), 65536));
            ImGui.beginDisabled(rawModified);
            boolean apply = ImGui.inputText(label, input, ImGuiInputTextFlags.EnterReturnsTrue);
            ImGui.endDisabled();
            pendingFields.update(path, value, input.get());
            if (apply) operation(() -> { });
        }
        ImGui.popID();
    }

    private void preview() {
        panel("Preview and cameras", 356, 456, 760, 360);
        ImGui.textWrapped("Preview runs locally with simulated conditions. Actions and rewards are logged, never applied.");
        if (ImGui.button("Use crosshair target") && MC.hitResult instanceof EntityHitResult hit) target = hit.getEntity();
        ImGui.sameLine();
        if (ImGui.button("Nearest villager") && MC.player != null && MC.level != null) {
            target = MC.level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                    MC.player.getBoundingBox().inflate(16)).stream()
                    .min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(MC.player))).orElse(null);
        }
        if (MC.level != null && MC.player != null && ImGui.beginCombo("Conversation target", target == null ? "Select entity" : target.getName().getString())) {
            for (Entity entity : MC.level.getEntities(MC.player, MC.player.getBoundingBox().inflate(32), Entity::isAlive)) {
                if (ImGui.selectable(entity.getName().getString() + "##" + entity.getId(), entity == target)) target = entity;
            }
            ImGui.endCombo();
        }
        if (target != null && !target.isAlive()) { target = null; status = "Preview target disappeared."; }
        if (document != null && target != null) {
            if (ImGui.button("Play from entry")) startPreview("");
            ImGui.sameLine();
            if (ImGui.button("Play selected node / object")) startPreview(selectedNode.isEmpty() ? pointer.get() : selectedNode);
            ImGui.sameLine();
            if (ImGui.button("Stop preview")) AuthoringDialoguePreview.stop();
            EditorCameraPanel.render(document, pointer.get(), target, screen, this::refresh, this::operation);
        }
        EditorPreviewConditions.render(conditions, document == null ? null : document.json());
        for (String entry : AuthoringDialoguePreview.log()) ImGui.textWrapped(entry);
        ImGui.end();
    }

    private void startPreview(String node) {
        operation(() -> {
            if (rawModified) throw new IllegalArgumentException("Apply or discard the JSON buffer before preview.");
            if (document.kind() == DefinitionKind.RESOURCE) {
                JsonElement selected = JsonPointers.get(document.json(), node);
                if (!selected.isJsonObject() || java.util.Collections.disjoint(selected.getAsJsonObject().keySet(),
                        List.of("text", "line", "lines", "variants", "text_key", "line_key", "exchanges", "options", "responses", "entries"))) {
                    throw new IllegalArgumentException("Select a dialogue-bearing object in Properties, then play selected object.");
                }
            }
            EditorCameraPanel.close();
            var authoredId = net.minecraft.resources.ResourceLocation.tryParse(document.id());
            var evaluationId = authoredId == null
                    ? net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("villagerretaliation", "authoring_preview") : authoredId;
            AuthoringDialoguePreview.start(document, node, target, conditions, () -> MC.setScreen(MC.level != null && MC.player != null ? screen : null), key -> {
                JsonObject reference = new JsonObject();
                reference.addProperty("key", key);
                String resolved = workspace.resolveText(document, reference, MC.getLanguageManager().getSelected(),
                        conditions.variant(), candidate -> conditions.evaluate(evaluationId, candidate)
                                == com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.MATCHED);
                return resolved.equals(key) ? null : resolved;
            });
        });
    }

    private void diagnostics() {
        panel("Diagnostics", 8, 696, 340, 220);
        if (ImGui.button("Validate workspace")) operation(this::validate);
        ImGui.textWrapped(status);
        for (var diagnostic : diagnostics) {
            if (ImGui.selectable(diagnostic.severity() + ": " + diagnostic.message() + "##"
                    + diagnostic.resourcePath() + diagnostic.pointer())) {
                if (workspace != null) {
                    for (var candidate : workspace.documents()) {
                        if (candidate.resourcePath().equals(diagnostic.resourcePath())) {
                            operation(() -> {
                                select(candidate);
                                setPointer(diagnostic.pointer());
                            });
                            break;
                        }
                    }
                }
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(diagnostic.resourcePath() + diagnostic.pointer());
        }
        ImGui.end();
    }

    private void save() throws IOException {
        if (rawModified) throw new IllegalArgumentException("Apply or discard the JSON buffer before saving.");
        if (workspace == null) throw new IllegalArgumentException("Open a workspace first.");
        workspace.save();
        status = "Drafts saved.";
    }

    private void validate() {
        if (workspace != null) diagnostics = workspace.validate();
    }

    private void select(AuthoringDocument next) {
        if (rawModified) {
            status = "Apply or discard the JSON buffer before changing selection.";
            return;
        }
        if (document != next) EditorCameraPanel.close();
        document = next;
        selectedNode = "";
        selectedEdge = "";
        draggingNode = null;
        pointer.set("");
        positions.clear();
        confirmDelete = false;
        refresh();
    }

    private void changeHistory(boolean redo) {
        boolean changed = redo ? document.redo() : document.undo();
        if (!changed) throw new IllegalStateException(redo ? "No change can be redone." : "No change can be undone.");
        refresh();
    }

    private void refresh() {
        rawModified = false;
        pendingFields.clear();
        fields.clear();
        positions.clear();
        if (document != null) {
            JsonElement savedPositions = document.metadata().get("node_positions");
            if (savedPositions != null && savedPositions.isJsonObject()) {
                for (var entry : savedPositions.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject point = entry.getValue().getAsJsonObject();
                    JsonElement x = point.get("x");
                    JsonElement y = point.get("y");
                    if (x == null || y == null || !x.isJsonPrimitive() || !y.isJsonPrimitive()
                            || !x.getAsJsonPrimitive().isNumber() || !y.getAsJsonPrimitive().isNumber()) continue;
                    float px = x.getAsFloat();
                    float py = y.getAsFloat();
                    if (Float.isFinite(px) && Float.isFinite(py)) positions.put(entry.getKey(), new float[] {px, py});
                }
            }
        }
        if (document != null) jsonText.set(JSON.toJson(document.json()));
        validate();
    }

    private void setPointer(String path) {
        pointer.set(path);
    }

    private void persistPositions(Map<String, float[]> movedPositions) {
        document.editMetadata("Move node", metadata -> {
            JsonObject values = new JsonObject();
            movedPositions.forEach((id, position) -> {
                JsonObject point = new JsonObject();
                point.addProperty("x", position[0]);
                point.addProperty("y", position[1]);
                values.add(id, point);
            });
            metadata.add("node_positions", values);
        });
    }

    private void replaceValue(String path, JsonElement value) {
        document.edit("Edit " + path, root -> JsonPointers.set(root, path, value));
        refresh();
    }

    private void editArray(String path, java.util.function.Consumer<JsonArray> edit) {
        operation(() -> {
            document.edit("Edit " + path, root -> edit.accept(JsonPointers.get(root, path).getAsJsonArray()));
            refresh();
        });
    }

    private void operation(EditorOperation action) {
        AuthoringDocument expected = document;
        attempt(() -> {
            if (expected != document) throw new IllegalStateException("Selection changed before the operation could run.");
            if (rawModified) throw new IllegalStateException("Apply or discard the JSON buffer before other operations.");
            if (pendingFields.apply(document)) refresh();
            action.run();
        });
    }

    private void attempt(EditorOperation action) {
        if (rendering) operations.addLast(action);
        else runOperation(action);
    }

    private void runOperation(EditorOperation action) {
        try {
            action.run();
        } catch (IOException | IllegalArgumentException | IllegalStateException | JsonParseException
                | ClassCastException | UnsupportedOperationException | IndexOutOfBoundsException exception) {
            status = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    private static String escape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private static String shortText(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    @FunctionalInterface
    interface EditorOperation {
        void run() throws IOException;
    }

    private final class EditorScreen extends Screen {
        private EditorScreen() { super(Component.literal("Villager Retaliation authoring")); }
        @Override protected void init() { escapeReady = false; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
        @Override public void onClose() {
            operation(() -> {
                AuthoringDialoguePreview.stop();
                EditorCameraPanel.close();
                MC.setScreen(null);
            });
        }
    }
}
