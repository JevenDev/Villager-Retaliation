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
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDir;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
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
            org.lwjgl.glfw.GLFW.GLFW_KEY_F9, "key.categories.villagerretaliation");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Minecraft MC = Minecraft.getInstance();
    private static final ImGuiAuthoringEditor INSTANCE = new ImGuiAuthoringEditor();
    private static final String CONTENT_PANEL = "Content###VR_Content";
    private static final String WORK_PANEL = "Work area###VR_Work";
    private static final String DETAIL_PANEL = "Details###VR_Details";
    private final Path preferencesPath = MC.gameDirectory.toPath().resolve("config/authoring-editor.json");
    private EditorPreferences preferences = EditorPreferences.defaults();
    private int guideStep = 0;
    private int view;
    private boolean resetLayout;
    private boolean compactLayout;
    private boolean showWorkspace;
    private boolean showCreate;
    private boolean showResources;
    private boolean showBranch;
    private boolean fitGraph = true;
    private boolean wireDragMoved;
    private boolean nodeDragMoved;
    private String focusPanel = "";
    private final ImString workspacePath = new ImString(4096);
    private final ImString importPath = new ImString(4096);
    private final ImString exportPath = new ImString(4096);
    private final ImString filter = new ImString(256);
    private final ImString newId = new ImString("example:new_dialogue", 512);
    private final ImString nodeName = new ImString("new_node", 256);
    private final ImString responseLabel = new ImString("Continue", 1024);
    private final ImString linkedQuest = new ImString(512);
    private final ImString nextStage = new ImString(256);
    private final ImString jsonText = new ImString(1_048_576);
    private final ImString pointer = new ImString(4096);
    private final ImString fieldName = new ImString(256);
    private final ImString fieldValue = new ImString("{}", 8192);
    private final Map<String, ImString> fields = new HashMap<>();
    private final EditorTextField textFields = new EditorTextField();
    private final EditorFieldEdits pendingFields = new EditorFieldEdits();
    private final Map<AuthoringDocument, EditorFieldEdits> translatedFields = new java.util.LinkedHashMap<>();
    private AuthoringDocument historyDocument;
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
    private boolean popupWasOpen;
    private boolean confirmDelete;
    private Entity target;

    static void initialize() {
        ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(INSTANCE::render);
    }

    static void open() {
        if (MC.player == null || MC.level == null) return;
        if (MC.hitResult instanceof EntityHitResult hit) INSTANCE.target = hit.getEntity();
        if (INSTANCE.workspace == null && !INSTANCE.preferences.workspace().isBlank())
            INSTANCE.runOperation(() -> INSTANCE.openWorkspace(false));
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
        try {
            preferences = EditorPreferences.load(preferencesPath);
            guideStep = preferences.guideDismissed() ? -1 : 0;
            if (!preferences.workspace().isBlank()) workspacePath.set(preferences.workspace());
        } catch (IOException exception) {
            status = exception.getMessage();
        }
    }

    private void render() {
        if (MC.screen != screen) return;
        if (!ImGui.isKeyDown(ImGuiKey.Escape)) escapeReady = true;
        if (escapeReady && !popupWasOpen && ImGui.isKeyPressed(ImGuiKey.Escape) && !ImGui.getIO().getWantTextInput()) {
            if (!selectedEdge.isEmpty()) {
                selectedEdge = "";
                wireDragMoved = false;
            } else screen.onClose();
            return;
        }
        rendering = true;
        pushEditorStyle();
        try {
            if (ImGui.getIO().getKeyCtrl()) {
                if (ImGui.isKeyPressed(ImGuiKey.S)) operation(this::save);
                if (document != null && !ImGui.getIO().getWantTextInput()) {
                    if (ImGui.isKeyPressed(ImGuiKey.Z)) operation(() -> changeHistory(false));
                    if (ImGui.isKeyPressed(ImGuiKey.Y)) operation(() -> changeHistory(true));
                }
            }
            shell();
            browser();
            if (view == 0) graph();
            else if (view == 1) preview();
            else diagnostics();
            inspector();
            popupWasOpen = ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopup);
        } finally {
            ImGui.popStyleColor(13);
            ImGui.popStyleVar(5);
            rendering = false;
            while (!operations.isEmpty()) runOperation(operations.removeFirst());
        }
    }

    private void pushEditorStyle() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 14, 14);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8, 6);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8, 8);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 3);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.10F, 0.10F, 0.11F, 1);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.17F, 0.17F, 0.19F, 1);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0.23F, 0.23F, 0.25F, 1);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0.27F, 0.27F, 0.29F, 1);
        ImGui.pushStyleColor(ImGuiCol.Button, 0.22F, 0.22F, 0.24F, 1);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.31F, 0.31F, 0.33F, 1);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.37F, 0.37F, 0.39F, 1);
        ImGui.pushStyleColor(ImGuiCol.Header, 0.21F, 0.24F, 0.22F, 1);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.28F, 0.32F, 0.29F, 1);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.32F, 0.38F, 0.34F, 1);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.93F, 0.93F, 0.91F, 1);
        ImGui.pushStyleColor(ImGuiCol.TextDisabled, 0.65F, 0.65F, 0.63F, 1);
        ImGui.pushStyleColor(ImGuiCol.TabSelected, 0.26F, 0.30F, 0.27F, 1);
    }

    private void shell() {
        var viewport = ImGui.getMainViewport();
        ImGui.getIO().addConfigFlags(ImGuiConfigFlags.DockingEnable | ImGuiConfigFlags.NavEnableKeyboard);
        ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY());
        ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY());
        ImGui.setNextWindowViewport(viewport.getID());
        ImGui.begin("Authoring###VR_AuthoringShell", ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoDocking
                | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoSavedSettings);
        toolbar();
        guide();
        ImGui.separator();
        int dock = ImGui.getID("VR_AuthoringDock");
        boolean compact = EditorLayout.forWidth(ImGui.getContentRegionAvailX()).compact();
        if (!initializedLayout || resetLayout || compact != compactLayout) {
            var existing = imgui.internal.ImGui.dockBuilderGetNode(dock);
            if (resetLayout || preferences.layoutVersion() != EditorPreferences.CURRENT_LAYOUT
                    || existing == null || existing.isNotValidPtr() || initializedLayout && compact != compactLayout) {
                arrangePanels(dock, ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY());
                preferences = new EditorPreferences(preferences.guideDismissed(), EditorPreferences.CURRENT_LAYOUT, preferences.workspace());
                attempt(this::savePreferences);
            }
            initializedLayout = true;
            compactLayout = compact;
            resetLayout = false;
        }
        ImGui.dockSpace(dock, 0, 0, imgui.flag.ImGuiDockNodeFlags.None);
        ImGui.end();
    }

    private void arrangePanels(int dock, float width, float height) {
        var plan = EditorLayout.forWidth(width);
        imgui.internal.ImGui.dockBuilderRemoveNode(dock);
        imgui.internal.ImGui.dockBuilderAddNode(dock, imgui.internal.flag.ImGuiDockNodeFlags.DockSpace);
        imgui.internal.ImGui.dockBuilderSetNodeSize(dock, width, height);
        var left = new imgui.type.ImInt();
        var center = new imgui.type.ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(dock, ImGuiDir.Left, plan.contentFraction(), left, center);
        int details = center.get();
        if (!plan.compact()) {
            var right = new imgui.type.ImInt();
            imgui.internal.ImGui.dockBuilderSplitNode(center.get(), ImGuiDir.Right, plan.detailsFraction(), right, center);
            details = right.get();
        }
        imgui.internal.ImGui.dockBuilderDockWindow(CONTENT_PANEL, left.get());
        imgui.internal.ImGui.dockBuilderDockWindow(WORK_PANEL, center.get());
        imgui.internal.ImGui.dockBuilderDockWindow(DETAIL_PANEL, details);
        imgui.internal.ImGui.dockBuilderFinish(dock);
    }

    private void toolbar() {
        if (ImGui.button("Workspace")) showWorkspace = !showWorkspace;
        ImGui.sameLine();
        ImGui.beginDisabled(workspace == null);
        if (ImGui.button("New")) { showCreate = !showCreate; view = 0; }
        ImGui.sameLine();
        if (ImGui.button("Save")) operation(this::save);
        ImGui.endDisabled();
        ImGui.sameLine();
        if (viewButton("Edit", 0)) view = 0;
        ImGui.sameLine();
        if (viewButton("Preview", 1)) view = 1;
        ImGui.sameLine();
        if (viewButton("Check & export", 2)) operation(() -> { validate(); view = 2; });
        if (ImGui.getWindowWidth() > 1000) ImGui.sameLine();
        if (ImGui.button(guideStep < 0 ? "Quick guide" : "Hide guide")) {
            if (guideStep < 0) guideStep = 0;
            else dismissGuide();
        }
        ImGui.sameLine();
        if (ImGui.button("Reset layout")) resetLayout = true;
        ImGui.sameLine();
        if (ImGui.button("Close")) screen.onClose();
        ImGui.textDisabled(workspace == null ? "No workspace open" : workspace.directory().getFileName()
                + (document == null ? "" : "  /  " + document.id())
                + (document != null && (document.dirty() || hasPendingFields() || rawModified) ? "  (unsaved)" : ""));
    }

    private boolean viewButton(String label, int index) {
        if (view == index) ImGui.pushStyleColor(ImGuiCol.Button, 0.30F, 0.38F, 0.32F, 1);
        boolean clicked = ImGui.button(label);
        if (view == index) ImGui.popStyleColor();
        return clicked;
    }

    private void guide() {
        if (guideStep < 0) return;
        String[] titles = {"Choose a workspace", "Create or select dialogue", "Write and connect", "Try the conversation", "Check and export"};
        String[] hints = {
                "Your workspace holds drafts. Open an existing folder or create a new one in Content.",
                "Choose New, pick a format, and give it an ID. Your dialogues stay in the Content list.",
                "Click a Say block to write dialogue. Add a response, then connect its right-hand dot to another node's input dot. Use Arrange nodes to tidy the flow.",
                "Choose Preview in the top bar, select a nearby villager, then play. Camera and condition controls are optional.",
                "Save your drafts, then choose Check & export. Fix any errors before exporting to a new datapack folder."
        };
        ImGui.separator();
        ImGui.textUnformatted("Quick guide  " + (guideStep + 1) + " / " + titles.length + "  |  " + titles[guideStep]);
        ImGui.textWrapped(hints[guideStep]);
        ImGui.beginDisabled(guideStep == 0);
        if (ImGui.smallButton("Back")) guideStep--;
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.smallButton("Show me")) {
            if (guideStep == 0) showWorkspace = true;
            if (guideStep == 1) showCreate = true;
            view = guideStep < 3 ? 0 : guideStep == 3 ? 1 : 2;
            focusPanel = guideStep < 2 ? CONTENT_PANEL : guideStep == 2 ? DETAIL_PANEL : WORK_PANEL;
        }
        ImGui.sameLine();
        if (ImGui.smallButton(guideStep == titles.length - 1 ? "Finish" : "Next")) {
            if (guideStep == titles.length - 1) dismissGuide();
            else guideStep++;
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Skip tutorial")) dismissGuide();
    }

    private void dismissGuide() {
        guideStep = -1;
        preferences = new EditorPreferences(true, preferences.layoutVersion(), preferences.workspace());
        attempt(this::savePreferences);
    }

    private void savePreferences() throws IOException { preferences.save(preferencesPath); }

    private void panel(String title) {
        if (title.equals(focusPanel)) { ImGui.setNextWindowFocus(); focusPanel = ""; }
        ImGui.begin(title, ImGuiWindowFlags.NoCollapse);
    }

    private static void input(String label, ImString value) {
        ImGui.textUnformatted(label);
        ImGui.setNextItemWidth(-1);
        ImGui.inputText("##" + label, value);
    }

    private static String kindLabel(DefinitionKind kind) {
        return switch (kind) {
            case TREE -> "Dialogue tree";
            case CONVERSATION -> "Conversation";
            case FORCED -> "Triggered dialogue";
            case QUEST -> "Quest";
            case RESOURCE -> "Supporting resource";
        };
    }

    private void openWorkspace(boolean create) throws IOException {
        if (workspace != null) workspace.save();
        AuthoringWorkspace next = create ? AuthoringWorkspace.create(Path.of(workspacePath.get()))
                : AuthoringWorkspace.open(Path.of(workspacePath.get()));
        workspace = next;
        select(null);
        showWorkspace = false;
        view = 0;
        filter.set("");
        showCreate = workspace.documents().isEmpty();
        preferences = new EditorPreferences(preferences.guideDismissed(), preferences.layoutVersion(), workspace.directory().toString());
        savePreferences();
        status = create ? "Workspace created." : "Workspace opened.";
    }

    private void browser() {
        panel(CONTENT_PANEL);
        if (workspace == null || showWorkspace) {
            ImGui.textUnformatted("Workspace");
            ImGui.textWrapped("Keep drafts in a folder, separate from your active game.");
            input("Workspace folder", workspacePath);
            if (ImGui.button("Open folder")) operation(() -> openWorkspace(false));
            ImGui.sameLine();
            if (ImGui.button("Create new")) operation(() -> openWorkspace(true));
            ImGui.textWrapped("Create needs an empty folder. Open loads an existing workspace.");
            ImGui.separator();
        }
        if (workspace == null) {
            ImGui.textWrapped(status);
            ImGui.end();
            return;
        }
        if (ImGui.button("New dialogue", -1, 0)) { showCreate = !showCreate; view = 0; }
        if (showCreate) {
            ImGui.textUnformatted("Format");
            ImGui.setNextItemWidth(-1);
            if (ImGui.beginCombo("##format", kindLabel(createKind))) {
                for (DefinitionKind kind : DefinitionKind.values()) {
                    if (kind != DefinitionKind.RESOURCE && ImGui.selectable(kindLabel(kind), kind == createKind)) createKind = kind;
                }
                ImGui.endCombo();
            }
            ImGui.textWrapped(switch (createKind) {
                case TREE -> "Standalone dialogue with branches and player responses.";
                case CONVERSATION -> "A quest conversation with localized exchanges, speakers and outcomes.";
                case FORCED -> "Dialogue opened by a game event.";
                case QUEST -> "Stages, objectives and rewards.";
                default -> "";
            });
            input("ID (namespace:name)", newId);
            if (createKind == DefinitionKind.CONVERSATION) {
                ImGui.textUnformatted("Associated quest");
                ImGui.setNextItemWidth(-1);
                if (ImGui.beginCombo("##linked_quest", linkedQuest.get().isEmpty() ? "Choose a quest" : linkedQuest.get())) {
                    for (var candidate : workspace.documents()) {
                        if (candidate.kind() == DefinitionKind.QUEST && ImGui.selectable(candidate.id(), linkedQuest.get().equals(candidate.id())))
                            linkedQuest.set(candidate.id());
                    }
                    ImGui.endCombo();
                }
                if (workspace.documents().stream().noneMatch(candidate -> candidate.kind() == DefinitionKind.QUEST))
                    ImGui.textWrapped("Create a quest first, or choose Dialogue tree for standalone dialogue.");
            }
            boolean questSelected = workspace.documents().stream().anyMatch(candidate ->
                    candidate.kind() == DefinitionKind.QUEST && candidate.id().equals(linkedQuest.get()));
            ImGui.beginDisabled(createKind == DefinitionKind.CONVERSATION && !questSelected);
            if (ImGui.button("Create", -1, 0)) operation(() -> {
                AuthoringDocument created = workspace.createDocument(createKind, newId.get());
                if (createKind == DefinitionKind.CONVERSATION) created.edit("Assign quest", root -> root.addProperty("quest", linkedQuest.get()));
                select(created);
                showCreate = false;
                view = 0;
                status = "Dialogue created.";
            });
            ImGui.endDisabled();
            ImGui.separator();
        }
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##search", "Find dialogue...", filter);
        if (ImGui.checkbox("Supporting files", showResources)) showResources = !showResources;
        int visible = 0;
        for (AuthoringDocument candidate : workspace.documents()) {
            if (!showResources && candidate.kind() == DefinitionKind.RESOURCE) continue;
            if (!(candidate.resourcePath() + candidate.id()).toLowerCase(Locale.ROOT).contains(filter.get().toLowerCase(Locale.ROOT))) continue;
            visible++;
            String name = candidate.kind() == DefinitionKind.RESOURCE
                    ? candidate.resourcePath().substring(candidate.resourcePath().lastIndexOf('/') + 1) : candidate.id();
            if (ImGui.selectable((candidate.dirty() ? "* " : "") + shortText(name, 26) + "##" + candidate.resourcePath(), document == candidate))
                operation(() -> { select(candidate); view = 0; });
            if (ImGui.isItemHovered()) ImGui.setTooltip(kindLabel(candidate.kind()) + "\n" + candidate.resourcePath());
        }
        if (visible == 0 && !showCreate) ImGui.textWrapped(filter.get().isBlank()
                ? "No dialogue yet. Create a dialogue or import a pack." : "No matches. Clear the search to see all content.");
        ImGui.separator();
        if (ImGui.collapsingHeader("Import existing pack")) {
            input("Pack folder", importPath);
            if (ImGui.button("Import")) operation(() -> {
                int count = workspace.importPack(Path.of(importPath.get()));
                if (document == null && count > 0) {
                    select(workspace.documents().stream().filter(candidate -> candidate.kind() != DefinitionKind.RESOURCE)
                            .findFirst().orElse(workspace.documents().getFirst()));
                    showResources = document.kind() == DefinitionKind.RESOURCE;
                    showCreate = false;
                    filter.set("");
                    view = 0;
                }
                status = count == 0 ? "No supported content found in this pack." : "Imported " + count + " resources.";
                validate();
            });
        }
        if (document != null && ImGui.collapsingHeader("Manage selected file")) {
            ImGui.textWrapped(document.resourcePath());
            input("New resource ID", newId);
            if (ImGui.button("Rename")) operation(() -> { workspace.rename(document, newId.get()); refresh(); });
            ImGui.sameLine();
            if (ImGui.button("Duplicate")) operation(() -> select(workspace.duplicate(document, newId.get())));
            if (ImGui.button("Delete...")) confirmDelete = true;
            if (confirmDelete) {
                ImGui.textWrapped("Delete this draft? Saving makes the removal permanent.");
                if (ImGui.button("Confirm delete")) operation(() -> { workspace.remove(document); select(null); });
                ImGui.sameLine();
                if (ImGui.button("Cancel")) confirmDelete = false;
            }
        }
        ImGui.separator();
        ImGui.textWrapped(shortText(status, 100));
        if (ImGui.isItemHovered()) ImGui.setTooltip(status);
        ImGui.end();
    }

    private void graph() {
        panel(WORK_PANEL);
        if (document == null) {
            ImGui.textWrapped(workspace == null ? "Create or open a workspace in Content."
                    : showCreate ? "Create a dialogue in Content." : "Choose a dialogue in Content.");
            ImGui.end();
            return;
        }
        if (document.kind() == DefinitionKind.RESOURCE) {
            ImGui.textWrapped("Edit resource pools and localization in Properties. Select a dialogue-bearing object to preview it.");
            ImGui.end();
            return;
        }
        List<DialogueGraph.Node> nodes;
        List<EditorGraph.Card> cards;
        try {
            nodes = DialogueGraph.nodes(document);
            cards = EditorGraph.cards(document, value -> workspace.resolveText(document, value, "en_us"));
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException
                | UnsupportedOperationException | IndexOutOfBoundsException exception) {
            ImGui.textWrapped("Repair the definition in Advanced full JSON: " + exception.getMessage());
            ImGui.end();
            return;
        }
        ImGui.textUnformatted(document.kind() == DefinitionKind.QUEST ? "Quest stages" : "Dialogue");
        ImGui.beginDisabled(document.kind() == DefinitionKind.FORCED && !document.json().has("entries"));
        if (ImGui.button(document.kind() == DefinitionKind.QUEST ? "Add stage" : "Add node")) operation(() -> {
            String id = nextNodeId();
            DialogueGraph.addNode(document, id);
            var added = document.kind() == DefinitionKind.FORCED ? DialogueGraph.nodes(document).getLast()
                    : DialogueGraph.nodes(document).stream().filter(node -> node.id().equals(id)).findFirst().orElseThrow();
            selectedNode = added.id();
            setPointer(added.pointer());
            showBranch = false;
            refresh();
            focusPanel = DETAIL_PANEL;
        });
        ImGui.endDisabled();
        boolean selected = nodes.stream().anyMatch(node -> node.id().equals(selectedNode));
        ImGui.sameLine();
        ImGui.beginDisabled(!selected);
        if (ImGui.button(document.kind() == DefinitionKind.QUEST ? "Connect stage" : "Add response")) {
            showBranch = !showBranch;
            if (showBranch) nextStage.set("");
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Fit graph")) fitGraph = true;
        ImGui.sameLine();
        if (ImGui.button("Arrange nodes")) operation(() -> {
            persistPositions(EditorGraph.arrange(cards));
            fitGraph = true;
            refresh();
        });
        if (selected && ImGui.collapsingHeader("Node actions")) {
            ImGui.textUnformatted("Selected: " + selectedNode);
            input("Copy ID", nodeName);
            if (ImGui.button("Duplicate node")) operation(() -> { DialogueGraph.duplicateNode(document, selectedNode, nodeName.get()); refresh(); });
            ImGui.sameLine();
            if (ImGui.button("Delete node")) operation(() -> { DialogueGraph.removeNode(document, selectedNode); selectedNode = ""; setPointer(""); refresh(); });
            ImGui.beginDisabled(document.kind() == DefinitionKind.FORCED);
            if (ImGui.button("Start here")) operation(() -> { DialogueGraph.setEntry(document, selectedNode); refresh(); });
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button("End here")) operation(() -> { DialogueGraph.setTerminal(document, selectedNode, true); refresh(); });
        }
        if (selected && showBranch) {
            if (document.kind() != DefinitionKind.QUEST) {
                ImGui.textUnformatted("Player response");
                textFields.render("new_response", responseLabel, document.kind(), false);
            }
            ImGui.textUnformatted("Leads to");
            ImGui.setNextItemWidth(-1);
            boolean needsDestination = document.kind() == DefinitionKind.CONVERSATION || document.kind() == DefinitionKind.QUEST;
            if (ImGui.beginCombo("##destination", nextStage.get().isEmpty()
                    ? needsDestination ? "Choose a destination" : "End conversation" : nextStage.get())) {
                if (document.kind() != DefinitionKind.CONVERSATION && document.kind() != DefinitionKind.QUEST
                        && ImGui.selectable("End conversation", nextStage.get().isEmpty())) nextStage.set("");
                for (var destination : nodes) {
                    if (document.kind() == DefinitionKind.FORCED && (selectedNode.equals(destination.id())
                            || selectedNode.startsWith(destination.id() + "/") || destination.id().isEmpty())) continue;
                    if (ImGui.selectable(destination.id(), destination.id().equals(nextStage.get()))) nextStage.set(destination.id());
                }
                ImGui.endCombo();
            }
            if (document.kind() == DefinitionKind.FORCED && ImGui.button("Create follow-up")) operation(() -> {
                String id = nextResponseId();
                document.edit("Add follow-up", root -> {
                    JsonObject owner = JsonPointers.get(root, selectedNode).getAsJsonObject();
                    JsonArray options = owner.has("options") ? owner.getAsJsonArray("options") : new JsonArray();
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
                    owner.add("options", options);
                });
                showBranch = false;
                refresh();
            });
            if (needsDestination && nextStage.get().isBlank()) ImGui.textWrapped("Choose a node above. Add another node first if you need a new destination.");
            ImGui.beginDisabled(needsDestination && nextStage.get().isBlank());
            if (ImGui.button(document.kind() == DefinitionKind.QUEST ? "Connect stage" : "Create response")) operation(() -> {
                if (document.kind() == DefinitionKind.QUEST) EditorDocumentOperations.nextStage(document, selectedNode, nextStage.get());
                else workspace.addDialogueResponse(document, selectedNode, nextResponseId(), responseLabel.get(), nextStage.get());
                showBranch = false;
                refresh();
            });
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button("Cancel connection")) showBranch = false;
        }
        ImGui.textWrapped("Select a block to edit. Connect an output dot to an input dot. Drag node headers to arrange.");
        if (!selectedEdge.isEmpty()) {
            if (ImGui.smallButton("Cancel connection")) selectedEdge = "";
            boolean responseEdge = selectedEdge.contains("/responses/") || selectedEdge.contains("/options/");
            if (responseEdge) {
                ImGui.sameLine();
                if (ImGui.smallButton("Delete selected response")) operation(() -> {
                    DialogueGraph.removeResponse(document, selectedEdge);
                    selectedEdge = "";
                    refresh();
                });
            }
        } else ImGui.dummy(0, ImGui.getTextLineHeight());
        float canvasX = ImGui.getCursorScreenPosX();
        float canvasY = ImGui.getCursorScreenPosY();
        float canvasWidth = Math.max(100, ImGui.getContentRegionAvailX());
        float canvasHeight = Math.max(100, ImGui.getContentRegionAvailY());
        ImGui.invisibleButton("graph_canvas", canvasWidth, canvasHeight);
        boolean hover = ImGui.isItemHovered();
        Map<String, float[]> defaults = EditorGraph.arrange(cards);
        for (var card : cards) positions.putIfAbsent(card.node().id(), defaults.get(card.node().id()));
        if (fitGraph && !cards.isEmpty()) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (var card : cards) {
                float[] point = positions.get(card.node().id());
                minX = Math.min(minX, point[0]);
                minY = Math.min(minY, point[1]);
                maxX = Math.max(maxX, point[0] + EditorGraph.WIDTH);
                maxY = Math.max(maxY, point[1] + card.height());
            }
            zoom = Math.clamp(Math.min((canvasWidth - 50) / (maxX - minX), (canvasHeight - 50) / (maxY - minY)), .35f, 1f);
            panX = 25 - minX * zoom;
            panY = 25 - minY * zoom;
            fitGraph = false;
        }
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
        if (!selectedEdge.isEmpty() && ImGui.isMouseDragging(0)) wireDragMoved = true;
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.pushClipRect(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, true);
        draw.addRectFilled(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, 0xff242321);
        for (var card : cards) {
            float[] from = positions.get(card.node().id());
            for (int i = 0; i < card.blocks().size(); i++) {
                var edge = card.blocks().get(i).output();
                if (edge == null) continue;
                float x1 = canvasX + panX + (from[0] + EditorGraph.WIDTH) * zoom;
                float y1 = canvasY + panY + (from[1] + card.outputY(i)) * zoom;
                float[] to = edge.to().isEmpty() ? null : positions.get(edge.to());
                if (to != null) {
                    float x2 = canvasX + panX + to[0] * zoom;
                    float y2 = canvasY + panY + (to[1] + EditorGraph.HEADER / 2) * zoom;
                    wire(draw, x1, y1, x2, y2, 0xffb2a68c);
                }
                if (edge.pointer().equals(selectedEdge))
                    wire(draw, x1, y1, ImGui.getIO().getMousePosX(), ImGui.getIO().getMousePosY(), 0xff81c9ed);
            }
        }
        for (var card : cards) {
            var node = card.node();
            float[] position = positions.get(node.id());
            float x = canvasX + panX + position[0] * zoom;
            float y = canvasY + panY + position[1] * zoom;
            float w = EditorGraph.WIDTH * zoom;
            float h = card.height() * zoom;
            draw.addRectFilled(x, y, x + w, y + h, 0xff39352f, 4);
            draw.addRectFilled(x, y, x + w, y + EditorGraph.HEADER * zoom,
                    selectedNode.equals(node.id()) ? 0xff65513d : 0xff494037, 4);
            draw.addRect(x, y, x + w, y + h, node.entry() ? 0xff9cc6a2 : 0xff78716b, 4);
            graphText(draw, x + 12 * zoom, y + 10 * zoom, 0xfff2f0e9,
                    (node.entry() ? "Start: " : "") + node.id(), EditorGraph.WIDTH - 24);
            float inputY = y + EditorGraph.HEADER / 2 * zoom;
            boolean inputHover = hover && ImGui.isMouseHoveringRect(x - 12, inputY - 12, x + 12, inputY + 12);
            draw.addCircleFilled(x, inputY, 6 * zoom, inputHover ? 0xff81c9ed : 0xffc2bdb4);
            if (inputHover) {
                ImGui.setTooltip(selectedEdge.isEmpty() ? "Input: connect a response here" : "Connect to " + node.id());
                if (!selectedEdge.isEmpty() && (ImGui.isMouseClicked(0) || wireDragMoved && ImGui.isMouseReleased(0))) {
                    String edge = selectedEdge;
                    operation(() -> {
                        DialogueGraph.connect(document, edge, node.id());
                        selectedEdge = "";
                        refresh();
                    });
                }
            }
            if (!inputHover && hover && ImGui.isMouseHoveringRect(x, y, x + w, y + EditorGraph.HEADER * zoom)
                    && ImGui.isMouseClicked(0)) operation(() -> {
                selectedNode = node.id();
                setPointer(node.pointer());
                selectedEdge = "";
                showBranch = false;
                draggingNode = node.id();
                nodeDragMoved = false;
            });
            for (int i = 0; i < card.blocks().size(); i++) {
                var block = card.blocks().get(i);
                float rowY = y + (EditorGraph.HEADER + i * EditorGraph.ROW) * zoom;
                float centerY = y + card.outputY(i) * zoom;
                float outputX = x + w;
                boolean outputHover = block.output() != null && hover
                        && ImGui.isMouseHoveringRect(outputX - 12, centerY - 12, outputX + 12, centerY + 12);
                boolean blockHover = !outputHover && hover && ImGui.isMouseHoveringRect(x + 6, rowY, x + w - 6, rowY + EditorGraph.ROW * zoom);
                draw.addRectFilled(x + 5 * zoom, rowY + 2 * zoom, x + w - 5 * zoom, rowY + (EditorGraph.ROW - 2) * zoom,
                        blockHover ? 0xff514a40 : block.output() != null ? 0xff403c32 : 0xff35322e, 3);
                graphText(draw, x + 12 * zoom, rowY + 9 * zoom, block.output() == null ? 0xffd9d2c7 : 0xffded8ad,
                        block.title() + ": " + block.text(), EditorGraph.WIDTH - 34);
                if (blockHover) {
                    ImGui.setTooltip(block.title() + ": " + block.text() + "\nClick to edit");
                    if (ImGui.isMouseClicked(0)) operation(() -> {
                        selectedNode = node.id();
                        setPointer(block.pointer());
                        selectedEdge = "";
                        showBranch = false;
                        focusPanel = DETAIL_PANEL;
                    });
                }
                if (block.output() != null) {
                    boolean active = block.output().pointer().equals(selectedEdge);
                    draw.addCircleFilled(outputX, centerY, 6 * zoom, active || outputHover ? 0xff81c9ed : 0xffd3c48e);
                    if (outputHover) {
                        ImGui.setTooltip(block.text() + "\n" + (block.output().to().isEmpty() ? "Ends here" : "Leads to " + block.output().to())
                                + "\nClick or drag to an input dot");
                        if (ImGui.isMouseClicked(0)) operation(() -> {
                            selectedNode = node.id();
                            selectedEdge = block.output().pointer();
                            setPointer(block.pointer());
                            draggingNode = null;
                            wireDragMoved = false;
                        });
                    }
                }
            }
            float footerY = y + h - 34 * zoom;
            String footer = document.kind() == DefinitionKind.QUEST ? "Edit stage" : "+ Add response";
            graphText(draw, x + 12 * zoom, footerY + 9 * zoom, 0xffbdcddd, footer, EditorGraph.WIDTH - 24);
            if (hover && ImGui.isMouseHoveringRect(x, footerY, x + w, y + h) && ImGui.isMouseClicked(0)) operation(() -> {
                selectedNode = node.id();
                setPointer(node.pointer());
                selectedEdge = "";
                showBranch = document.kind() != DefinitionKind.QUEST;
                nextStage.set("");
                if (!showBranch) focusPanel = DETAIL_PANEL;
            });
            if (node.id().equals(draggingNode) && ImGui.isMouseDragging(0)) {
                nodeDragMoved = true;
                position[0] += ImGui.getIO().getMouseDeltaX() / zoom;
                position[1] += ImGui.getIO().getMouseDeltaY() / zoom;
            }
        }
        draw.popClipRect();
        if (ImGui.isMouseReleased(0)) {
            wireDragMoved = false;
            if (draggingNode != null) {
                Map<String, float[]> movedPositions = new HashMap<>();
                positions.forEach((id, position) -> movedPositions.put(id, position.clone()));
                if (nodeDragMoved) operation(() -> { persistPositions(movedPositions); refresh(); });
                draggingNode = null;
            }
        }
        ImGui.end();
    }

    private void wire(ImDrawList draw, float x1, float y1, float x2, float y2, int color) {
        float curve = Math.max(70 * zoom, Math.abs(x2 - x1) * .45f);
        draw.addBezierCubic(x1, y1, x1 + curve, y1, x2 - curve, y2, x2, y2, color, 2);
    }

    private void graphText(ImDrawList draw, float x, float y, int color, String text, float width) {
        String label = text.replace('\n', ' ');
        int length = label.length();
        while (length > 0 && ImGui.calcTextSize(length == label.length() ? label : label.substring(0, length) + "...").x > width) length--;
        if (length < label.length()) label = label.substring(0, length) + "...";
        draw.addText(ImGui.getFont(), Math.max(6, (int) (ImGui.getFontSize() * zoom)), x, y, color, label);
    }

    private String nextNodeId() {
        var nodes = DialogueGraph.nodes(document);
        int index = nodes.size() + 1;
        while (true) {
            String id = "node_" + index++;
            if (nodes.stream().noneMatch(node -> node.id().equals(id))
                    && (!document.json().has("nodes") || !document.json().get("nodes").isJsonObject()
                        || !document.json().getAsJsonObject("nodes").has(id))) return id;
        }
    }

    private String nextResponseId() {
        var node = DialogueGraph.nodes(document).stream().filter(value -> value.id().equals(selectedNode)).findFirst().orElseThrow();
        JsonObject owner = JsonPointers.get(document.json(), node.pointer()).getAsJsonObject();
        JsonArray responses = DialogueGraph.array(owner, document.kind() == DefinitionKind.FORCED ? "options" : "responses");
        int index = responses.size() + 1;
        while (true) {
            String id = "choice_" + index++;
            if (responses.asList().stream().noneMatch(value -> value.isJsonObject() && id.equals(DialogueGraph.string(value.getAsJsonObject(), "id")))) return id;
        }
    }

    private void inspector() {
        panel(DETAIL_PANEL);
        if (document == null) {
            ImGui.textUnformatted("No definition selected.");
            ImGui.end();
            return;
        }
        ImGui.textUnformatted(selectedNode.isEmpty() ? kindLabel(document.kind()) : "Selected: " + selectedNode);
        ImGui.beginDisabled(!(historyDocument == null ? document : historyDocument).canUndo() || rawModified);
        if (ImGui.button("Undo")) operation(() -> changeHistory(false));
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(!(historyDocument == null ? document : historyDocument).canRedo() || rawModified);
        if (ImGui.button("Redo")) operation(() -> changeHistory(true));
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("File settings")) operation(() -> { selectedNode = ""; setPointer(""); });
        if (!selectedNode.isEmpty() || document.kind() == DefinitionKind.FORCED && pointer.get().isEmpty()) {
            if (document.kind() != DefinitionKind.QUEST && document.kind() != DefinitionKind.RESOURCE
                    && ImGui.button(document.kind() == DefinitionKind.CONVERSATION ? "Add spoken line" : "Add line variation")) operation(() -> { workspace.addDialogueLine(document, selectedNode); refresh(); });
        }
        try {
            var availableBlocks = EditorBlocks.available(document, pointer.get());
            if (!availableBlocks.isEmpty() && ImGui.beginCombo("##add_block", "Add behavior block...")) {
                for (var block : availableBlocks) {
                    if (ImGui.selectable(block.label)) operation(() -> {
                        String added = EditorBlocks.append(document, pointer.get(), block);
                        refresh();
                        setPointer(added);
                    });
                }
                ImGui.endCombo();
            }
            selectedDetails();
        } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException
                | ClassCastException | UnsupportedOperationException exception) {
            ImGui.textWrapped("Select an existing JSON object or field: " + exception.getMessage());
        }
        if (hasPendingFields()) {
            if (ImGui.button("Apply field edits")) operation(() -> { });
            ImGui.sameLine();
            if (ImGui.button("Discard field edits")) attempt(() -> { pendingFields.clear(); translatedFields.clear(); fields.clear(); textFields.clear(); });
        }
        ImGui.separator();
        if (ImGui.collapsingHeader("Advanced editing")) {
            ImGui.inputText("JSON pointer", pointer);
            if (ImGui.button("Inspect pointer")) operation(() -> { });
            if (!pointer.get().isEmpty() && ImGui.button("Remove selected field")) operation(() -> {
                document.edit("Remove field", root -> JsonPointers.remove(root, pointer.get()));
                setPointer("");
                refresh();
            });
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
                ImGui.beginDisabled(hasPendingFields());
                if (ImGui.inputTextMultiline("##raw", jsonText, -1, 260)) rawModified = true;
                ImGui.endDisabled();
                if (hasPendingFields()) ImGui.textWrapped("Apply or discard field edits before editing full JSON.");
                if (ImGui.button("Apply JSON")) attempt(() -> {
                    if (hasPendingFields()) throw new IllegalStateException("Apply or discard field edits before applying full JSON.");
                    document.replaceJson(jsonText.get());
                    historyDocument = document;
                    refresh();
                });
                ImGui.sameLine();
                if (ImGui.button("Discard JSON buffer")) attempt(() -> {
                    if (hasPendingFields()) throw new IllegalStateException("Apply or discard field edits first.");
                    refresh();
                });
                if (rawModified) ImGui.textWrapped("Unapplied JSON buffer. Apply before saving, previewing or switching definitions.");
            }
        }
        ImGui.end();
    }

    private boolean hasPendingFields() {
        return pendingFields.pending() || translatedFields.values().stream().anyMatch(EditorFieldEdits::pending);
    }

    private void applyPendingFields() {
        if (document != null) pendingFields.validate(document);
        translatedFields.forEach((owner, edits) -> edits.validate(owner));
        boolean changed = false;
        if (pendingFields.apply(document)) { historyDocument = document; changed = true; }
        for (var entry : translatedFields.entrySet()) {
            if (entry.getValue().apply(entry.getKey())) { historyDocument = entry.getKey(); changed = true; }
        }
        if (changed) refresh();
    }

    private void selectedDetails() {
        JsonElement selected = JsonPointers.get(document.json(), pointer.get());
        if (!selected.isJsonObject()) {
            inspectValue(selected, pointer.get(), "Value", 0);
            return;
        }
        JsonObject object = selected.getAsJsonObject();
        List<String> main = List.of("title", "label", "text", "line", "lines", "variants", "exchanges", "response", "responses", "options", "objectives");
        boolean shown = false;
        for (String field : main) {
            if (!object.has(field)) continue;
            inspectValue(object.get(field), pointer.get() + "/" + escape(field), friendlyLabel(field), 0);
            shown = true;
        }
        if (!shown && object.isEmpty()) ImGui.textWrapped("This block is empty. Select the node header to add dialogue or behavior.");
        if (!shown || ImGui.collapsingHeader("More properties")) {
            for (var entry : object.entrySet()) {
                if (main.contains(entry.getKey())) continue;
                inspectValue(entry.getValue(), pointer.get() + "/" + escape(entry.getKey()), friendlyLabel(entry.getKey()), 0);
            }
        }
    }

    private static String friendlyLabel(String field) {
        return switch (field) {
            case "lines", "variants" -> "Line variations";
            case "exchanges" -> "Spoken lines";
            case "responses", "options" -> "Player responses";
            case "end", "end_conversation" -> "End conversation";
            case "next" -> "Next node";
            default -> field.isEmpty() ? "(unnamed field)" : Character.toUpperCase(field.charAt(0)) + field.substring(1).replace('_', ' ');
        };
    }

    private void localizedField(JsonObject reference, String label, String path) {
        String key = reference.get("key").getAsString();
        var location = workspace.textLocation(document, key, "en_us");
        ImGui.textUnformatted(label);
        if (location != null) {
            JsonElement text = JsonPointers.get(location.document().json(), location.pointer());
            if (text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
                String bufferId = location.document().resourcePath() + location.pointer();
                ImString buffer = fields.computeIfAbsent(bufferId, ignored -> new ImString(text.getAsString(), 65536));
                ImGui.beginDisabled(rawModified);
                textFields.render(bufferId, buffer, document.kind(), !path.endsWith("/label") && !path.endsWith("/title"));
                ImGui.endDisabled();
                translatedFields.computeIfAbsent(location.document(), ignored -> new EditorFieldEdits())
                        .update(location.pointer(), text, buffer.get());
                if (ImGui.isItemHovered()) ImGui.setTooltip("English wording. The localization reference is preserved.");
                return;
            }
            ImGui.textWrapped(workspace.resolveText(document, reference, "en_us"));
            if (ImGui.smallButton("Edit text variations")) operation(() -> {
                select(location.document());
                setPointer(location.pointer());
                showResources = true;
            });
        } else {
            ImGui.textWrapped("Translation not found: " + key);
            ImGui.textWrapped("Import its locale file, or use Advanced to edit the reference.");
        }
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
        if (value.isJsonObject() && value.getAsJsonObject().has("key")
                && value.getAsJsonObject().get("key").isJsonPrimitive() && workspace != null
                && (path.endsWith("/text") || path.endsWith("/label") || path.endsWith("/title"))) {
            localizedField(value.getAsJsonObject(), label, path);
        } else if (value.isJsonObject()) {
            if (depth < 2) ImGui.setNextItemOpen(true, ImGuiCond.Once);
            if (ImGui.treeNode(label)) {
                if (ImGui.smallButton("Focus this item")) operation(() -> setPointer(path));
                for (var entry : value.getAsJsonObject().entrySet()) {
                    inspectValue(entry.getValue(), path + "/" + escape(entry.getKey()), friendlyLabel(entry.getKey()), depth + 1);
                }
                ImGui.treePop();
            }
        } else if (value.isJsonArray() && path.endsWith("/lines")
                && value.getAsJsonArray().asList().stream().allMatch(line -> line.isJsonPrimitive() && line.getAsJsonPrimitive().isString())) {
            var lines = value.getAsJsonArray();
            if (lines.size() > 1) ImGui.textDisabled("One variation is chosen each time.");
            for (int i = 0; i < lines.size(); i++) {
                int index = i;
                ImGui.pushID(i);
                inspectValue(lines.get(i), path + "/" + i, lines.size() == 1 ? "Dialogue text" : "Line " + (i + 1), depth + 1);
                ImGui.beginDisabled(lines.size() == 1);
                if (ImGui.smallButton("Remove line")) editArray(path, values -> values.remove(index));
                ImGui.endDisabled();
                ImGui.sameLine();
                if (ImGui.smallButton("Duplicate line")) editArray(path, values -> values.add(values.get(index).deepCopy()));
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
        } else if (value.isJsonArray()) {
            if (depth == 0 && (!path.endsWith("/responses") && !path.endsWith("/options") || pointer.get().equals(path)))
                ImGui.setNextItemOpen(true, ImGuiCond.Once);
            if (ImGui.treeNode(label + " [" + value.getAsJsonArray().size() + "]")) {
                if (ImGui.smallButton("Focus list")) operation(() -> setPointer(path));
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
            ImGui.textUnformatted(label);
            ImGui.setNextItemWidth(-1);
            boolean text = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            boolean apply = false;
            if (text && (path.contains("/lines/") || path.contains("/variants/") || path.endsWith("/text") || path.endsWith("/line") || path.endsWith("/response")))
                textFields.render(path, input, document.kind(), true);
            else if (text && path.endsWith("/label"))
                textFields.render(path, input, document.kind(), false);
            else apply = ImGui.inputText("##value", input, ImGuiInputTextFlags.EnterReturnsTrue);
            ImGui.endDisabled();
            pendingFields.update(path, value, input.get());
            if (apply) operation(() -> { });
        }
        ImGui.popID();
    }

    private void preview() {
        panel(WORK_PANEL);
        ImGui.textUnformatted("Preview dialogue");
        if (document == null) {
            ImGui.textWrapped("Choose a dialogue in Content first.");
            ImGui.end();
            return;
        }
        if (document.kind() == DefinitionKind.QUEST) {
            ImGui.textWrapped("Quest progression preview is not available. Select a conversation or dialogue tree to play it.");
            ImGui.end();
            return;
        }
        ImGui.textWrapped("Choose a villager, then play your dialogue. Actions and rewards are shown in the log.");
        if (ImGui.button("Use crosshair target") && MC.hitResult instanceof EntityHitResult hit) target = hit.getEntity();
        ImGui.sameLine();
        if (ImGui.button("Nearest villager") && MC.player != null && MC.level != null) {
            target = MC.level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                    MC.player.getBoundingBox().inflate(16)).stream()
                    .min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(MC.player))).orElse(null);
        }
        ImGui.textUnformatted("Conversation target");
        ImGui.setNextItemWidth(-1);
        if (MC.level != null && MC.player != null && ImGui.beginCombo("##target", target == null ? "Select entity" : target.getName().getString())) {
            for (Entity entity : MC.level.getEntities(MC.player, MC.player.getBoundingBox().inflate(32), Entity::isAlive)) {
                if (ImGui.selectable(entity.getName().getString() + "##" + entity.getId(), entity == target)) target = entity;
            }
            ImGui.endCombo();
        }
        if (target != null && !target.isAlive()) { target = null; status = "Preview target disappeared."; }
        EditorPreviewConditions.time(conditions);
        ImGui.beginDisabled(target == null);
        if (ImGui.button("Play from entry")) startPreview("");
        ImGui.sameLine();
        if (ImGui.button("Play selected node")) startPreview(selectedNode.isEmpty() ? pointer.get() : selectedNode);
        ImGui.endDisabled();
        if (target == null) ImGui.textDisabled("Select a target to play.");
        else EditorCameraPanel.render(document, pointer.get(), target, screen, this::refresh, this::operation);
        EditorPreviewConditions.render(conditions, document == null ? null : document.json());
        if (ImGui.collapsingHeader("Preview log"))
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
            conditions.worldTime(MC.level.getDayTime());
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
        panel(WORK_PANEL);
        ImGui.textUnformatted("Check and export");
        if (workspace == null) {
            ImGui.textWrapped("Open a workspace in Content first.");
            ImGui.end();
            return;
        }
        ImGui.textWrapped("Check your drafts, fix any errors, then export a new datapack folder.");
        if (ImGui.button("Check drafts")) operation(this::validate);
        long errors = diagnostics.stream().filter(value -> value.severity() == AuthoringDiagnostic.Severity.ERROR).count();
        ImGui.textWrapped(errors == 0 ? "No blocking errors. Ready to export." : errors + " errors need attention. Select an issue below to open its field.");
        input("New datapack folder", exportPath);
        ImGui.beginDisabled(errors > 0);
        if (ImGui.button("Save and export")) operation(() -> {
            validate();
            workspace.save();
            workspace.exportPack(Path.of(exportPath.get()));
            status = "Exported datapack to " + exportPath.get();
        });
        ImGui.endDisabled();
        ImGui.separator();
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
                                view = 0;
                                focusPanel = DETAIL_PANEL;
                            });
                            break;
                        }
                    }
                }
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(diagnostic.message() + "\n" + diagnostic.resourcePath() + diagnostic.pointer());
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
        fitGraph = true;
        historyDocument = next;
        selectedNode = "";
        selectedEdge = "";
        draggingNode = null;
        pointer.set("");
        positions.clear();
        confirmDelete = false;
        showBranch = false;
        if (next != null && next.kind() != DefinitionKind.RESOURCE) {
            try {
                var nodes = DialogueGraph.nodes(next);
                if (!nodes.isEmpty()) {
                    var first = nodes.stream().filter(DialogueGraph.Node::entry).findFirst().orElse(nodes.getFirst());
                    selectedNode = first.id();
                    pointer.set(first.pointer());
                }
            } catch (IllegalArgumentException | IllegalStateException | ClassCastException | UnsupportedOperationException exception) {
                status = "This draft needs repair in Advanced editing: " + exception.getMessage();
            }
        }
        refresh();
    }

    private void changeHistory(boolean redo) {
        AuthoringDocument history = historyDocument == null ? document : historyDocument;
        boolean changed = redo ? history.redo() : history.undo();
        if (!changed) throw new IllegalStateException(redo ? "No change can be redone." : "No change can be undone.");
        refresh();
    }

    private void refresh() {
        rawModified = false;
        pendingFields.clear();
        translatedFields.clear();
        fields.clear();
        textFields.clear();
        positions.clear();
        if (document != null) {
            JsonElement savedPositions = document.metadata().get("node_positions");
            if (document.metadata().has("graph_card_layout") && savedPositions != null && savedPositions.isJsonObject()) {
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
            metadata.addProperty("graph_card_layout", 1);
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
            applyPendingFields();
            JsonObject before = document == null ? null : document.json();
            JsonObject beforeMetadata = document == null ? null : document.metadata();
            action.run();
            if (document != null && document == expected
                    && (!document.json().equals(before) || !document.metadata().equals(beforeMetadata))) historyDocument = document;
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
            com.mojang.logging.LogUtils.getLogger().warn("Authoring operation failed", exception);
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
