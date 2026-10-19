package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.Gson;
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
    private static final Gson JSON = AuthoringDocument.JSON;
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
    private boolean filesMode = true;
    private final EditorPackFiles packFiles = new EditorPackFiles();
    private final EditorPackBuild packBuild = new EditorPackBuild();
    private boolean showBranch;
    private boolean fitGraph = true;
    private boolean wireDragMoved;
    private boolean nodeDragMoved;
    private String focusPanel = "";
    private final ImString workspacePath = new ImString(4096);
    private AuthoringDocument createScope;
    private final ImString campaignDescriptionPath = new ImString(4096);
    private final ImString campaignImagePath = new ImString(4096);
    private net.minecraft.client.renderer.texture.DynamicTexture campaignPreview;
    private String campaignPreviewKey = "";
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
    private final EditorGraphText graphText = new EditorGraphText();
    private final EditorFieldEdits pendingFields = new EditorFieldEdits();
    private final Map<AuthoringDocument, EditorFieldEdits> translatedFields = new java.util.LinkedHashMap<>();
    private AuthoringDocument historyDocument;
    private final java.util.ArrayDeque<EditorOperation> operations = new java.util.ArrayDeque<>();
    private boolean rendering;
    private final Map<String, float[]> positions = new HashMap<>();
    private final PreviewConditions conditions = new PreviewConditions();
    private com.jvn.villagerretaliation.authoring.preview.QuestPreviewSession questPreview;
    private EditorSchema propertySchema;
    private boolean liveQuestMode = true;
    private float playtestFraction = 2F / 3;
    private boolean playtestHold = true;
    private long nextPlaytestRefresh;
    private final ImString playtestValue = new ImString(256);
    private Map<String, JsonObject> playtestVisuals = Map.of();
    private String playtestLocale = "";
    private Map<String, String> playtestReplacements = Map.of();
    private boolean playtestEditing;
    private boolean playtestStepRemoved;
    private final ImString scenarioName = new ImString("New scenario", 256);
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
    private final ImString locale = new ImString("en_us", 32);
    private final ImString objectiveResource = new ImString("minecraft:emerald", 256);
    private final imgui.type.ImInt objectiveCount = new imgui.type.ImInt(1);
    private String objectiveType = "item_check";
    private String contentKind = "All";
    private boolean groupContent;
    private boolean showMinimap;
    private boolean showGraphTools;
    private final ImString nodeFilter = new ImString(256);
    private boolean focusSearch;
    private String graphFocus = "";
    private final java.util.Set<String> nodeSelection = new java.util.LinkedHashSet<>();
    private final EditorGraphClipboard graphClipboard = new EditorGraphClipboard();
    private final ImString replaceText = new ImString(1024);
    private boolean showRecovery;
    private boolean confirmClose;
    private long nextRecovery;
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
        if (INSTANCE.workspace != null && !INSTANCE.showRecovery && net.minecraft.Util.getMillis() >= INSTANCE.nextRecovery) {
            INSTANCE.nextRecovery = net.minecraft.Util.getMillis() + 10_000;
            INSTANCE.attempt(INSTANCE::writeRecovery);
        }
        if (INSTANCE.view == 3 && !EditorTestWorld.applying()) INSTANCE.updatePlaytestPresentation();
        if (INSTANCE.view == 3 && !EditorTestWorld.applying() && INSTANCE.document != null && INSTANCE.document.kind() == DefinitionKind.QUEST
                && EditorTestWorld.active(INSTANCE.workspace) && !EditorLiveQuestTest.view().busy()
                && net.minecraft.Util.getMillis() >= INSTANCE.nextPlaytestRefresh) {
            INSTANCE.nextPlaytestRefresh = net.minecraft.Util.getMillis() + 250;
            INSTANCE.attempt(() -> EditorLiveQuestTest.inspect(INSTANCE.workspace, INSTANCE.document));
        }
        if (INSTANCE.view != 1) EditorCameraPanel.close();
        EditorCameraPanel.tick(INSTANCE.screen);
        if (INSTANCE.target != null && (MC.level != INSTANCE.target.level() || INSTANCE.target.isRemoved())) INSTANCE.target = null;
    }

    static void disconnect() {
        if (!INSTANCE.showRecovery) INSTANCE.attempt(INSTANCE::writeRecovery);
        AuthoringDialoguePreview.stop();
        EditorPlaytestViewport.close();
        EditorCameraPanel.close();
        EditorLiveQuestTest.stop();
        EditorPlaytestPresentation.clear();
        INSTANCE.clearCampaignPreview();
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
            if (!preferences.exportFolder().isBlank()) exportPath.set(preferences.exportFolder());
            locale.set(preferences.locale());
            playtestFraction = preferences.playtestFraction();
        } catch (IOException exception) {
            status = exception.getMessage();
        }
    }

    private void render() {
        boolean playtesting = view == 3 && EditorPlaytestViewport.active();
        textFields.beginFrame(MC.screen == screen || playtesting);
        graphText.beginFrame(MC.screen == screen && view == 0);
        if (MC.screen != screen && !playtesting) return;
        if (!ImGui.isKeyDown(ImGuiKey.Escape)) escapeReady = true;
        if (!playtesting && escapeReady && !popupWasOpen && ImGui.isKeyPressed(ImGuiKey.Escape) && !ImGui.getIO().getWantTextInput()) {
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
            if (document != null && view == 0 && !ImGui.getIO().getWantTextInput() && !showRecovery && !confirmClose) {
                if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.F)) focusSearch = true;
                if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.C)) operation(() -> graphClipboard.copy(workspace, document, selectedNodes()));
                if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.V)) operation(this::pasteNodes);
                if (ImGui.getIO().getKeyAlt() && ImGui.isKeyPressed(ImGuiKey.RightArrow)) operation(() -> navigateNode(1));
                if (ImGui.getIO().getKeyAlt() && ImGui.isKeyPressed(ImGuiKey.LeftArrow)) operation(() -> navigateNode(-1));
            }
            if (view == 3) { playtest(); return; }
            shell();
            ImGui.beginDisabled(showRecovery || confirmClose);
            browser();
            if (view == 0) graph();
            else if (view == 1) preview();
            else if (view == 4) files();
            else diagnostics();
            inspector();
            ImGui.endDisabled();
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
        ImGui.setWindowFontScale(preferences.textScale());
        toolbar();
        recoveryControls();
        guide();
        ImGui.separator();
        int dock = ImGui.getID("VR_AuthoringDock");
        boolean compact = EditorLayout.forWidth(ImGui.getContentRegionAvailX() / preferences.textScale()).compact();
        if (!initializedLayout || resetLayout || compact != compactLayout) {
            var existing = imgui.internal.ImGui.dockBuilderGetNode(dock);
            if (resetLayout || preferences.layoutVersion() != EditorPreferences.CURRENT_LAYOUT
                    || existing == null || existing.isNotValidPtr() || initializedLayout && compact != compactLayout) {
                arrangePanels(dock, ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY());
                preferences = preferences.withLayout(EditorPreferences.CURRENT_LAYOUT);
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
        var plan = EditorLayout.forWidth(width / preferences.textScale());
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
        ImGui.beginDisabled(showRecovery || confirmClose);
        if (ImGui.button("Workspace")) showWorkspace = !showWorkspace;
        ImGui.sameLine();
        ImGui.beginDisabled(workspace == null);
        if (ImGui.button("New")) { chooseCreateScope(); showCreate = !showCreate; view = 0; }
        ImGui.sameLine();
        if (ImGui.button("Save")) operation(this::save);
        ImGui.endDisabled();
        ImGui.sameLine();
        if (viewButton("Edit", 0)) { view = 0; focusPanel = WORK_PANEL; }
        ImGui.sameLine();
        if (viewButton("Preview", 1)) { view = 1; focusPanel = WORK_PANEL; }
        ImGui.sameLine();
        if (viewButton("Playtest", 3)) enterPlaytest();
        ImGui.sameLine();
        if (viewButton("Build pack", 2)) operation(() -> { validate(); view = 2; focusPanel = WORK_PANEL; });
        if (ImGui.getWindowWidth() > 1000) ImGui.sameLine();
        if (ImGui.button(guideStep < 0 ? "Quick guide" : "Hide guide")) {
            if (guideStep < 0) guideStep = 0;
            else dismissGuide();
        }
        ImGui.sameLine();
        if (ImGui.button("Reset layout")) resetLayout = true;
        ImGui.sameLine();
        if (ImGui.button("Files")) operation(() -> { filesMode = true; view = 4; focusPanel = WORK_PANEL; });
        ImGui.sameLine();
        if (ImGui.button("Close")) screen.onClose();
        ImGui.endDisabled();
        ImGui.textDisabled(workspace == null ? "No workspace open" : workspace.directory().getFileName()
                + (view == 4 ? "  /  " + packFiles.selected() : document == null ? "" : "  /  " + document.resourcePath())
                + (workspace != null && (workspace.dirty() || hasPendingFields() || rawModified) ? "  (unsaved)" : ""));
    }

    private boolean viewButton(String label, int index) {
        if (view == index) ImGui.pushStyleColor(ImGuiCol.Button, 0.30F, 0.38F, 0.32F, 1);
        boolean clicked = ImGui.button(label);
        if (view == index) ImGui.popStyleColor();
        return clicked;
    }

    private void guide() {
        if (guideStep < 0) return;
        String[] titles = {"Choose a workspace", "Create or select content", "Write and connect", "Try the conversation", "Check and export"};
        String[] hints = {
                "Your workspace holds drafts. Open an existing folder or create a new one in Content.",
                "Choose New, select a content type, and enter a name or resource ID. Content tools includes starter recipes.",
                "Click a Say block to write dialogue. Add a response, then connect its right-hand dot to another node's input dot. Use Arrange nodes to tidy the flow.",
                "Choose Preview in the top bar, select a nearby villager, then play. Camera and condition controls are optional.",
                "Choose Build pack to review settings and contents, fix errors, then build a folder or ZIP package."
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
        preferences = preferences.withGuide(true);
        attempt(this::savePreferences);
    }

    private void savePreferences() throws IOException { preferences.save(preferencesPath); }

    private void panel(String title) {
        if (title.equals(focusPanel)) { ImGui.setNextWindowFocus(); focusPanel = ""; }
        ImGui.begin(title, ImGuiWindowFlags.NoCollapse);
        ImGui.setWindowFontScale(preferences.textScale());
    }

    private static void input(String label, ImString value) {
        ImGui.textUnformatted(label);
        boolean browse = label.toLowerCase(Locale.ROOT).contains("folder") || label.toLowerCase(Locale.ROOT).contains("path");
        ImGui.setNextItemWidth(browse ? Math.max(60, ImGui.getContentRegionAvailX() - 86) : -1);
        ImGui.inputText("##" + label, value);
        if (browse) {
            ImGui.sameLine();
            if (ImGui.button("Browse##" + label)) EditorFileDialog.choose(label, value.get(), selected -> MC.execute(() -> value.set(selected)));
        }
    }

    private static String kindLabel(DefinitionKind kind) {
        return switch (kind) {
            case TREE -> "Dialogue tree";
            case CONVERSATION -> "Conversation";
            case FORCED -> "Triggered dialogue";
            case QUEST -> "Quest";
            case WORLD_CAMPAIGN -> "World campaign";
            case CAMPAIGN_ADDON -> "Campaign addon";
            case RESOURCE -> "Supporting resource";
        };
    }

    private void openWorkspace(boolean create) throws IOException {
        if (workspace != null) workspace.save();
        AuthoringWorkspace next = create ? AuthoringWorkspace.create(Path.of(workspacePath.get()))
                : AuthoringWorkspace.open(Path.of(workspacePath.get()));
        workspace = next;
        packFiles.reset();
        packBuild.load(next);
        createScope = null;
        select(null);
        showWorkspace = false;
        view = 0;
        filter.set("");
        showCreate = workspace.documents().isEmpty();
        preferences = preferences.withWorkspace(workspace.directory().toString());
        savePreferences();
        showRecovery = !create && workspace.recoveryAvailable();
        if (guideStep == 0) guideStep = 1;
        status = create ? "Workspace created." : "Workspace opened.";
    }

    private void browser() {
        panel(filesMode ? "Pack files###VR_Content" : CONTENT_PANEL);
        if (workspace == null || showWorkspace) {
            ImGui.textUnformatted("Workspace");
            ImGui.textWrapped("Keep drafts in a folder, separate from your active game.");
            input("Workspace folder", workspacePath);
            if (ImGui.button("Open folder")) operation(() -> openWorkspace(false));
            ImGui.sameLine();
            if (ImGui.button("Create new")) operation(() -> openWorkspace(true));
            ImGui.textWrapped("Create needs an empty folder. Open loads an existing workspace.");
            if (!preferences.recentWorkspaces().isEmpty() && ImGui.beginCombo("Recent workspaces", "Choose a workspace")) {
                for (String recent : preferences.recentWorkspaces()) if (ImGui.selectable(recent)) operation(() -> { workspacePath.set(recent); openWorkspace(false); });
                ImGui.endCombo();
            }
            ImGui.separator();
        }
        if (workspace == null) {
            ImGui.textWrapped(status);
            ImGui.end();
            return;
        }
        if (ImGui.button("New content", -1, 0)) {
            showCreate = !showCreate;
            view = 0;
            chooseCreateScope();
        }
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
                case WORLD_CAMPAIGN -> "Campaign card and content settings for world creation.";
                case CAMPAIGN_ADDON -> "Optional content with campaign compatibility, dependencies and existing-world settings.";
                default -> "";
            });
            input("Name or namespace:id", newId);
            if (!createKind.campaignManifest() && workspace.documents().stream().anyMatch(value -> value.kind().campaignManifest())) {
                if (createScope != null && !workspace.documents().contains(createScope)) createScope = null;
                ImGui.textUnformatted("Content folder");
                ImGui.setNextItemWidth(-1);
                if (ImGui.beginCombo("##content_folder", createScope == null ? "Default content" : createScope.id())) {
                    if (ImGui.selectable("Default content", createScope == null)) createScope = null;
                    for (var scope : workspace.documents()) {
                        if (scope.kind().campaignManifest() && ImGui.selectable(kindLabel(scope.kind()) + ": " + scope.id(), scope == createScope)) setCreateScope(scope);
                    }
                    ImGui.endCombo();
                }
                if (createScope != null) ImGui.textWrapped("Content uses the " + createScope.id().split(":", 2)[0] + " namespace and stays inside this folder.");
            }
            if (createKind == DefinitionKind.CONVERSATION) {
                ImGui.textUnformatted("Associated quest");
                ImGui.setNextItemWidth(-1);
                if (ImGui.beginCombo("##linked_quest", linkedQuest.get().isEmpty() ? "Choose a quest" : linkedQuest.get())) {
                    for (var candidate : workspace.documents()) {
                        if (candidate.kind() == DefinitionKind.QUEST && ImGui.selectable(candidate.id() + "##" + candidate.resourcePath(), linkedQuest.get().equals(candidate.id())))
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
                AuthoringDocument created = createContent(createKind);
                if (createKind == DefinitionKind.CONVERSATION) created.edit("Assign quest", root -> root.addProperty("quest", linkedQuest.get()));
                if (!newId.get().contains(":")) created.edit("Set content name", root -> {
                    if (createKind.campaignManifest()) root.addProperty("name", newId.get());
                    else if (createKind == DefinitionKind.TREE || createKind == DefinitionKind.QUEST) {
                        if (!root.has("display")) root.add("display", new JsonObject());
                        root.getAsJsonObject("display").addProperty("title", newId.get());
                    }
                });
                select(created);
                showCreate = false;
                view = 0;
                status = "Content created.";
                if (guideStep == 1) guideStep = 2;
            });
            ImGui.endDisabled();
            if (filesMode) {
                try {
                    String id = newContentId(createKind);
                    String target = createKind.path(id);
                    if (!createKind.campaignManifest() && createScope != null)
                        target = DefinitionKind.contentRoot(createScope.resourcePath()) + target.substring(("data/" + id.split(":", 2)[0] + "/").length());
                    ImGui.textWrapped("Creates: " + target);
                } catch (IllegalArgumentException exception) { ImGui.textWrapped(exception.getMessage()); }
            }
            ImGui.separator();
        }
        ImGui.setNextItemWidth(-1);
        if (focusSearch) { ImGui.setKeyboardFocusHere(); focusSearch = false; }
        ImGui.inputTextWithHint("##search", "Find content or wording...", filter);
        if (filesMode) {
            ImGui.textDisabled("Paths in the built pack");
            packFiles.renderTree(workspace, filter.get(), path -> operation(() -> openPackFile(path)));
            if (ImGui.smallButton("Use content list")) filesMode = false;
        } else {
            if (ImGui.checkbox("Supporting files", showResources)) showResources = !showResources;
            int visible = 0;
            String previousGroup = "";
            var listed = groupContent ? workspace.documents().stream().sorted(java.util.Comparator.comparing(this::contentGroup).thenComparing(AuthoringDocument::id)).toList() : workspace.documents();
            for (AuthoringDocument candidate : listed) {
                if (!showResources && candidate.kind() == DefinitionKind.RESOURCE) continue;
                if (!EditorContentTools.matches(candidate, filter.get())) continue;
                if (!contentKind.equals("All") && !kindLabel(candidate.kind()).equals(contentKind)) continue;
                visible++;
                String group = contentGroup(candidate);
                if (groupContent && !group.equals(previousGroup)) { ImGui.textDisabled(group); previousGroup = group; }
                String name = candidate.kind() == DefinitionKind.RESOURCE
                        ? candidate.resourcePath().substring(candidate.resourcePath().lastIndexOf('/') + 1) : candidate.id();
                if (ImGui.selectable((candidate.dirty() ? "* " : "") + shortText(name, Math.max(15, (int) (ImGui.getContentRegionAvailX() / ImGui.calcTextSize("M").x) - 3)) + "##" + candidate.resourcePath(), document == candidate))
                    operation(() -> { select(candidate); view = 0; });
                if (ImGui.isItemHovered()) ImGui.setTooltip(kindLabel(candidate.kind()) + "\n" + candidate.resourcePath());
            }
            if (visible == 0 && !showCreate) ImGui.textWrapped(filter.get().isBlank()
                    ? "No dialogue yet. Create a dialogue or import a pack." : "No matches. Clear the search to see all content.");
        }
        ImGui.separator();
        if (ImGui.collapsingHeader("Import existing pack")) {
            input("Pack folder or ZIP", importPath);
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
            if (document.kind().campaignManifest()) ImGui.textWrapped("Rename, duplicate and delete include all content and assets in this folder.");
            input("New resource ID", newId);
            if (ImGui.button("Rename")) operation(() -> { workspace.rename(document, newId.get()); refresh(); });
            ImGui.sameLine();
            if (ImGui.button("Duplicate")) operation(() -> select(workspace.duplicate(document, newId.get())));
            if (ImGui.button("Delete...")) confirmDelete = true;
            if (confirmDelete) {
                ImGui.textWrapped("Move this draft to Deleted files? You can restore it later.");
                if (ImGui.button("Confirm delete")) operation(() -> { workspace.remove(document); select(null); });
                ImGui.sameLine();
                if (ImGui.button("Cancel")) confirmDelete = false;
            }
        }
        contentTools();
        if (!workspace.deletedNames().isEmpty() && ImGui.collapsingHeader("Deleted files")) {
            var names = workspace.deletedNames();
            for (int i = 0; i < names.size(); i++) {
                int index = i;
                if (ImGui.button("Restore " + names.get(i) + "##trash" + i)) operation(() -> {
                    workspace.restoreDeleted(index);
                    validate();
                    status = "File restored.";
                });
            }
        }
        ImGui.separator();
        ImGui.textWrapped(shortText(status, 100));
        if (ImGui.isItemHovered()) ImGui.setTooltip(status);
        ImGui.end();
    }

    private void chooseCreateScope() {
        String path = document == null ? packFiles.selected() : document.resourcePath();
        setCreateScope(EditorContentTools.contentScope(workspace, path));
    }

    private void setCreateScope(AuthoringDocument scope) {
        createScope = scope;
        if (scope != null && newId.get().contains(":")) newId.set(scope.id().split(":", 2)[0] + newId.get().substring(newId.get().indexOf(':')));
    }

    private String newContentId(DefinitionKind kind) {
        String id = EditorContentTools.resourceId(newId.get());
        if (!kind.campaignManifest() && createScope != null && !newId.get().contains(":"))
            id = createScope.id().split(":", 2)[0] + id.substring(id.indexOf(':'));
        if (!kind.campaignManifest() && createScope != null && !createScope.resourcePath().startsWith("data/" + id.split(":", 2)[0] + "/"))
            throw new IllegalArgumentException("Content must use its campaign or addon namespace");
        return id;
    }

    private AuthoringDocument createContent(DefinitionKind kind) {
        return workspace.createDocument(kind, newContentId(kind), kind.campaignManifest() ? null : createScope);
    }

    private String contentGroup(AuthoringDocument value) {
        String[] parts = value.resourcePath().split("/");
        String campaign = parts.length > 3 && (parts[2].equals("world_campaigns") || parts[2].equals("campaign_addons"))
                ? (parts[2].equals("campaign_addons") ? "Addon " : "Campaign ") + parts[1] + ":" + parts[3]
                : DialogueGraph.string(value.json(), "world_campaign");
        return (campaign.isBlank() ? "Default content" : campaign) + " / " + kindLabel(value.kind());
    }

    private void contentTools() {
        if (!ImGui.collapsingHeader("Content tools")) return;
        if (ImGui.checkbox("Group by campaign, addon and type", groupContent)) groupContent = !groupContent;
        float[] scale = {preferences.textScale()};
        if (ImGui.sliderFloat("Editor text size", scale, .8f, 2)) operation(() -> { preferences = preferences.withScale(scale[0]); savePreferences(); });
        graphTools();
        input("Editing language", locale);
        if (ImGui.button("Use language")) operation(() -> {
            locale.set(locale.get().strip().toLowerCase(Locale.ROOT));
            if (!locale.get().matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Use a language code such as en_us");
            preferences = preferences.withLocale(locale.get()); savePreferences(); refresh();
        });
        if (ImGui.beginCombo("Content type", contentKind)) {
            if (ImGui.selectable("All", contentKind.equals("All"))) contentKind = "All";
            for (var kind : DefinitionKind.values()) if (ImGui.selectable(kindLabel(kind), contentKind.equals(kindLabel(kind)))) contentKind = kindLabel(kind);
            ImGui.endCombo();
        }
        if (ImGui.button("Open workspace folder")) net.minecraft.Util.getPlatform().openFile(workspace.directory().toFile());
        ImGui.textWrapped("Starter recipes use the name above and create editable content.");
        for (String recipe : List.of("Branching dialogue", "Fetch quest", "Triggered encounter", "Reputation choice")) {
            if (ImGui.button(recipe)) operation(() -> {
                var kind = recipe.equals("Fetch quest") ? DefinitionKind.QUEST : recipe.equals("Triggered encounter") ? DefinitionKind.FORCED : DefinitionKind.TREE;
                var created = createContent(kind);
                EditorContentTools.recipe(workspace, created, recipe);
                select(created); showCreate = false; view = 0;
                if (guideStep == 1) guideStep = 2;
            });
        }
        input("Replace matching wording with", replaceText);
        if (ImGui.button("Replace in selected file")) operation(() -> {
            if (document == null || filter.get().isBlank()) throw new IllegalArgumentException("Select a file and enter search text first");
            document.edit("Replace wording", root -> replaceWording(root, filter.get(), replaceText.get()));
            refresh();
        });
    }

    private static void replaceWording(JsonElement value, String find, String replacement) {
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
            String field = entry.getKey();
            if (List.of("text", "line", "label", "title", "description", "lines", "variants").contains(field)) {
                JsonElement wording = entry.getValue();
                if (wording.isJsonPrimitive() && wording.getAsJsonPrimitive().isString()) entry.setValue(new JsonPrimitive(wording.getAsString().replace(find, replacement)));
                else if (wording.isJsonArray()) for (int i = 0; i < wording.getAsJsonArray().size(); i++) {
                    var line = wording.getAsJsonArray().get(i);
                    if (line.isJsonPrimitive() && line.getAsJsonPrimitive().isString()) wording.getAsJsonArray().set(i, new JsonPrimitive(line.getAsString().replace(find, replacement)));
                    else replaceWording(line, find, replacement);
                }
            } else if (!List.of("metadata", "conditions", "actions", "effects").contains(field)) replaceWording(entry.getValue(), find, replacement);
        }
        else if (value.isJsonArray()) for (var child : value.getAsJsonArray()) replaceWording(child, find, replacement);
    }

    private void graph() {
        panel(WORK_PANEL);
        if (document == null) {
            ImGui.textWrapped(workspace == null ? "Create or open a workspace in Content."
                    : showCreate ? "Create a dialogue in Content." : "Choose a dialogue in Content.");
            ImGui.end();
            return;
        }
        if (document.kind().campaignManifest()) {
            campaignCard();
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
            cards = EditorGraph.cards(document, value -> workspace.resolveText(document, value, locale.get()));
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
            nodeSelection.clear(); nodeSelection.add(selectedNode);
            focusPanel = DETAIL_PANEL;
        });
        ImGui.endDisabled();
        boolean selected = nodes.stream().anyMatch(node -> node.id().equals(selectedNode));
        sameLineFor(document.kind() == DefinitionKind.QUEST ? "Connect stage" : "Add response");
        ImGui.beginDisabled(!selected);
        if (ImGui.button(document.kind() == DefinitionKind.QUEST ? "Connect stage" : "Add response")) {
            showBranch = !showBranch;
            if (showBranch) nextStage.set("");
        }
        ImGui.endDisabled();
        sameLineFor("Fit graph");
        if (ImGui.button("Fit graph")) fitGraph = true;
        sameLineFor("Arrange nodes");
        if (ImGui.button("Arrange nodes")) operation(() -> {
            persistPositions(EditorGraph.arrange(cards));
            fitGraph = true;
            refresh();
        });
        sameLineFor("100%");
        boolean resetZoom = ImGui.button(Math.round(zoom * 100) + "%###graph_zoom");
        if (ImGui.isItemHovered()) ImGui.setTooltip("Reset zoom to 100%. Scroll over the graph to zoom. Middle-drag to pan.");
        sameLineFor("Graph tools");
        if (ImGui.button("Graph tools")) showGraphTools = !showGraphTools;
        if (showGraphTools) graphTools();
        if (selected && ImGui.collapsingHeader("Node actions")) {
            ImGui.textUnformatted("Selected: " + selectedNode);
            input("Node name / copy ID", nodeName);
            if (document.kind() != DefinitionKind.FORCED && ImGui.button("Rename node and references")) operation(() -> {
                EditorContentTools.renameNode(document, selectedNode, nodeName.get());
                selectedNode = nodeName.get();
                refresh();
                setPointer(DialogueGraph.nodes(document).stream().filter(value -> value.id().equals(selectedNode)).findFirst().orElseThrow().pointer());
            });
            var incoming = EditorContentTools.usages(document, selectedNode);
            ImGui.textWrapped(incoming.size() + " incoming connections");
            for (var edge : incoming) if (ImGui.selectable((edge.from().isBlank() ? "File settings" : "From " + edge.from()) + "##usage" + edge.pointer())) operation(() -> {
                selectedNode = edge.from(); setPointer(edge.pointer());
            });
            if (document.kind() != DefinitionKind.FORCED) input("Redirect deleted node to", nextStage);
            else ImGui.textWrapped("Deleting a follow-up also removes its owning player response.");
            if (ImGui.button("Duplicate node")) operation(() -> { DialogueGraph.duplicateNode(document, selectedNode, nodeName.get()); refresh(); });
            ImGui.sameLine();
            if (ImGui.button("Delete node")) operation(() -> { EditorContentTools.deleteNode(document, selectedNode, nextStage.get()); selectedNode = ""; setPointer(""); refresh(); });
            ImGui.beginDisabled(document.kind() == DefinitionKind.FORCED);
            if (ImGui.button("Start here")) operation(() -> { DialogueGraph.setEntry(document, selectedNode); refresh(); });
            if (ImGui.isItemHovered()) ImGui.setTooltip(document.kind() == DefinitionKind.QUEST ? "Start the quest at this stage."
                    : "Change the first entry or topic to start at this node. Other entries are preserved.");
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button("End here")) operation(() -> { DialogueGraph.setTerminal(document, selectedNode, true); refresh(); });
            if (ImGui.isItemHovered()) ImGui.setTooltip(document.kind() == DefinitionKind.CONVERSATION || document.kind() == DefinitionKind.FORCED
                    ? "End this node. Remove existing player choices first; they will not be discarded automatically." : "End this node.");
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
                if (document.kind() != DefinitionKind.FORCED && ImGui.selectable("Create a new destination")) operation(() -> {
                    String id = nextNodeId(); DialogueGraph.addNode(document, id); nextStage.set(id); refresh();
                });
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
                if (guideStep == 2) guideStep = 3;
                showBranch = false;
                refresh();
            });
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button("Cancel connection")) showBranch = false;
        }
        ImGui.textWrapped("Select a block to edit. Connect an output dot to an input dot. Drag node headers to arrange.");
        if (!selectedEdge.isEmpty()) {
            if (ImGui.smallButton("Create connected node")) operation(() -> {
                selectedNode = EditorContentTools.createConnected(document, selectedEdge, nextNodeId());
                selectedEdge = ""; refresh();
                setPointer(DialogueGraph.nodes(document).stream().filter(value -> value.id().equals(selectedNode)).findFirst().orElseThrow().pointer());
                graphFocus = selectedNode; nodeSelection.clear(); nodeSelection.add(selectedNode);
            });
            ImGui.sameLine();
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
        boolean minimapHover = showMinimap && hover && ImGui.isMouseHoveringRect(canvasX + canvasWidth - 190, canvasY + canvasHeight - 130, canvasX + canvasWidth, canvasY + canvasHeight);
        if (minimapHover) hover = false;
        Map<String, float[]> defaults = EditorGraph.arrange(cards);
        for (var card : cards) positions.putIfAbsent(card.node().id(), defaults.get(card.node().id()));
        if (!graphFocus.isEmpty() && positions.containsKey(graphFocus)) {
            var point = positions.get(graphFocus);
            panX = canvasWidth / 2 - (point[0] + EditorGraph.WIDTH / 2) * zoom;
            panY = canvasHeight / 2 - (point[1] + EditorGraph.HEADER) * zoom;
            graphFocus = ""; fitGraph = false;
        }
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
        if (resetZoom) {
            panX = canvasWidth / 2 - (canvasWidth / 2 - panX) / zoom;
            panY = canvasHeight / 2 - (canvasHeight / 2 - panY) / zoom;
            zoom = 1;
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
                    selectedNode.equals(node.id()) || nodeSelection.contains(node.id()) ? 0xff65513d : 0xff494037, 4);
            draw.addRect(x, y, x + w, y + h, node.entry() ? 0xff9cc6a2 : 0xff78716b, 4);
            graphText(draw, x + 12 * zoom, y + 10 * zoom, 0xfff2f0e9,
                    (node.entry() ? "Start: " : "") + node.id(), EditorGraph.WIDTH - 24);
            long issueCount = diagnostics.stream().filter(issue -> issue.resourcePath().equals(document.resourcePath())
                    && (issue.pointer().equals(node.pointer()) || issue.pointer().startsWith(node.pointer() + "/"))).count();
            if (issueCount > 0) draw.addText(x + w - 28, y + 10, 0xff82b9ef, "!" + issueCount);
            float inputY = y + EditorGraph.HEADER / 2 * zoom;
            boolean inputHover = hover && EditorGraphHit.port(ImGui.getMousePosX(), ImGui.getMousePosY(), x, inputY, EditorGraph.HEADER * zoom);
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
                if (ImGui.getIO().getKeyCtrl()) {
                    if (nodeSelection.isEmpty() && !selectedNode.isEmpty()) nodeSelection.add(selectedNode);
                    if (!nodeSelection.add(node.id())) nodeSelection.remove(node.id());
                } else if (!nodeSelection.contains(node.id())) { nodeSelection.clear(); nodeSelection.add(node.id()); }
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
                        && EditorGraphHit.port(ImGui.getMousePosX(), ImGui.getMousePosY(), outputX, centerY, EditorGraph.ROW * zoom);
                boolean blockHover = !outputHover && hover && ImGui.isMouseHoveringRect(x + 6, rowY, x + w - 6, rowY + EditorGraph.ROW * zoom);
                int background = blockHover ? 0xff514a40 : block.output() != null ? 0xff403c32 : 0xff35322e;
                draw.addRectFilled(x + 5 * zoom, rowY + 2 * zoom, x + w - 5 * zoom, rowY + (EditorGraph.ROW - 2) * zoom, background, 3);
                if (selectedNode.equals(node.id()) && pointer.get().equals(block.pointer()))
                    draw.addRect(x + 5 * zoom, rowY + 2 * zoom, x + w - 5 * zoom, rowY + (EditorGraph.ROW - 2) * zoom, 0xff9cc6a2, 3);
                if (block.styledText() == null) {
                    graphText(draw, x + 12 * zoom, rowY + 9 * zoom, block.output() == null ? 0xffd9d2c7 : 0xffded8ad,
                            block.title() + ": " + block.text(), EditorGraph.WIDTH - 34);
                } else if (x + w > canvasX && x < canvasX + canvasWidth && rowY + EditorGraph.ROW * zoom > canvasY
                        && rowY < canvasY + canvasHeight) {
                    String label = block.title() + ":";
                    float labelWidth = ImGui.calcTextSize(label).x + 4;
                    graphText(draw, x + 12 * zoom, rowY + 9 * zoom, 0xffd9d2c7, label, labelWidth);
                    graphText.draw(block, x + (12 + labelWidth) * zoom, rowY + 3 * zoom,
                            (EditorGraph.WIDTH - 26 - labelWidth) * zoom, 28 * zoom, zoom, background);
                }
                if (blockHover) {
                    if (block.styledText() == null) ImGui.setTooltip(block.title() + ": " + block.text() + "\nClick to edit");
                    else graphText.tooltip(block);
                    if (ImGui.isMouseClicked(0)) operation(() -> {
                        selectedNode = node.id();
                        nodeSelection.clear(); nodeSelection.add(node.id());
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
                for (String id : selectedNodes()) {
                    float[] point = positions.get(id);
                    if (point == null) continue;
                    point[0] += ImGui.getIO().getMouseDeltaX() / zoom;
                    point[1] += ImGui.getIO().getMouseDeltaY() / zoom;
                }
            }
        }
        if (showMinimap && !cards.isEmpty()) minimap(draw, cards, canvasX, canvasY, canvasWidth, canvasHeight, minimapHover);
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

    private void minimap(ImDrawList draw, List<EditorGraph.Card> cards, float canvasX, float canvasY, float width, float height, boolean hovered) {
        float left = canvasX + width - 190, top = canvasY + height - 130;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (var card : cards) {
            float[] point = positions.get(card.node().id());
            minX = Math.min(minX, point[0]); minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0] + EditorGraph.WIDTH); maxY = Math.max(maxY, point[1] + card.height());
        }
        float scale = Math.min(170 / Math.max(1, maxX - minX), 110 / Math.max(1, maxY - minY));
        draw.addRectFilled(left, top, left + 190, top + 130, 0xf0202020);
        for (var card : cards) {
            float[] point = positions.get(card.node().id());
            float x = left + 10 + (point[0] - minX) * scale, y = top + 10 + (point[1] - minY) * scale;
            draw.addRectFilled(x, y, x + EditorGraph.WIDTH * scale, y + card.height() * scale, card.node().id().equals(selectedNode) ? 0xff9cc6a2 : 0xff727272);
        }
        draw.pushClipRect(left, top, left + 190, top + 130, true);
        draw.addRect(left + 10 + (-panX / zoom - minX) * scale, top + 10 + (-panY / zoom - minY) * scale,
                left + 10 + ((width - panX) / zoom - minX) * scale, top + 10 + ((height - panY) / zoom - minY) * scale, 0xffedc981);
        draw.popClipRect();
        if (hovered) ImGui.setTooltip("Click to move the graph view");
        if (hovered && ImGui.isMouseClicked(0)) {
            float x = (ImGui.getMousePosX() - left - 10) / scale + minX;
            float y = (ImGui.getMousePosY() - top - 10) / scale + minY;
            panX = width / 2 - x * zoom; panY = height / 2 - y * zoom;
        }
    }

    private static void sameLineFor(String label) {
        if (ImGui.getItemRectMaxX() + ImGui.calcTextSize(label).x + 32 < ImGui.getWindowPosX() + ImGui.getWindowWidth() - 14) ImGui.sameLine();
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
        if (view == 4) {
            ImGui.textUnformatted("File details");
            ImGui.textWrapped(packFiles.selected().isEmpty() ? "Select a folder or file in the tree." : packFiles.selected());
            ImGui.textWrapped("Content files open in their editor. Supporting text and images open in Pack files.");
            if (workspace != null) {
                ImGui.textDisabled("Draft folder: " + workspace.directory().getFileName());
                if (ImGui.isItemHovered()) ImGui.setTooltip(workspace.directory().toString());
            }
            if (workspace != null && ImGui.button("Open workspace folder")) net.minecraft.Util.getPlatform().openFile(workspace.directory().toFile());
            if (ImGui.button("Build pack")) operation(() -> { validate(); view = 2; });
            ImGui.end();
            return;
        }
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
            if (pointer.get().isEmpty()) campaignOwnership();
            selectedDetails();
            for (var issue : diagnostics) if (issue.resourcePath().equals(document.resourcePath())
                    && (issue.pointer().equals(pointer.get()) || issue.pointer().startsWith(pointer.get() + "/"))) {
                ImGui.textWrapped(issue.severity() + ": " + issue.message());
                ImGui.textWrapped(diagnosticHint(issue.message()));
            }
            if (pendingFields.pending()) try { pendingFields.validate(document); }
            catch (IllegalArgumentException | IllegalStateException | JsonParseException exception) { ImGui.textWrapped(exception.getMessage()); }
            guidedObjective();
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
            tutorialObjectiveCatalog();
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

    private void campaignOwnership() {
        if (document.kind().campaignManifest() || document.kind() == DefinitionKind.RESOURCE) return;
        String[] path = document.resourcePath().split("/", 5);
        if (path.length == 5 && path[2].equals("world_campaigns")) {
            ImGui.textWrapped("World campaign: " + path[1] + ":" + path[3] + " (from folder)");
            return;
        }
        boolean addon = path.length == 5 && path[2].equals("campaign_addons");
        if (addon) ImGui.textWrapped("Campaign addon: " + path[1] + ":" + path[3] + " (from folder)");
        String owner = DialogueGraph.string(document.json(), "world_campaign");
        String fallback = addon ? "All supported campaigns" : "Default VR";
        if (ImGui.beginCombo(addon ? "Campaign filter" : "World campaign", owner.isBlank() ? fallback : owner)) {
            if (ImGui.selectable(fallback, owner.isBlank())) operation(() -> {
                document.edit("Set campaign", root -> root.remove("world_campaign"));
                refresh();
            });
            if (ImGui.selectable("Shared by all campaigns", owner.equals("villagerretaliation:shared"))) operation(() -> {
                document.edit("Set campaign", root -> root.addProperty("world_campaign", "villagerretaliation:shared"));
                refresh();
            });
            for (AuthoringDocument campaign : workspace.documents()) {
                if (campaign.kind() == DefinitionKind.WORLD_CAMPAIGN && ImGui.selectable(campaign.id(), owner.equals(campaign.id()))) operation(() -> {
                    document.edit("Set campaign", root -> root.addProperty("world_campaign", campaign.id()));
                    refresh();
                });
            }
            ImGui.endCombo();
        }
        if (document.kind() == DefinitionKind.QUEST) {
            ImString arc = new ImString(DialogueGraph.string(document.json(), "story_arc"), 512);
            if (ImGui.inputText("Story arc (optional)", arc)) document.edit("Set story arc", root -> {
                if (arc.get().isBlank()) root.remove("story_arc");
                else root.addProperty("story_arc", arc.get());
            });
        }
    }

    private void campaignCard() {
        JsonObject value = document.json();
        ImGui.textUnformatted(DialogueGraph.string(value, "name"));
        try { ImGui.textWrapped(workspace.campaignDescription(document)); }
        catch (IOException exception) { ImGui.textWrapped(exception.getMessage()); }
        if (value.has("locked") && value.get("locked").getAsBoolean()) ImGui.textUnformatted("Coming soon");
        boolean addon = document.kind() == DefinitionKind.CAMPAIGN_ADDON;
        if (addon) {
            ImGui.textWrapped(value.has("allow_existing_worlds") && value.get("allow_existing_worlds").getAsBoolean()
                    ? "Can be added to existing worlds." : "Available when creating a world.");
            ImGui.textWrapped("Set supported campaigns, required addons and conflicts in Properties. Empty supported campaigns allows any campaign.");
            ImGui.textWrapped("Required addons must be enabled first. This addon takes priority over them. Saved addons stay enabled in existing worlds.");
        }
        String key = document.resourcePath() + ":" + DialogueGraph.string(value, "image");
        if (!key.equals(campaignPreviewKey)) {
            clearCampaignPreview();
            campaignPreviewKey = key;
            try {
                byte[] bytes = workspace.campaignImage(document);
                if (bytes.length > 0) campaignPreview = new net.minecraft.client.renderer.texture.DynamicTexture(
                        com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(bytes)));
            } catch (IOException exception) {
                status = "Could not preview campaign image: " + exception.getMessage();
            }
        }
        if (campaignPreview != null && campaignPreview.getPixels() != null) {
            var pixels = campaignPreview.getPixels();
            float scale = Math.min(240.0F / pixels.getWidth(), 160.0F / pixels.getHeight());
            ImGui.image(campaignPreview.getId(), pixels.getWidth() * scale, pixels.getHeight() * scale);
        }
        input("PNG image path", campaignImagePath);
        if (ImGui.button(addon ? "Import addon image" : "Import campaign image")) operation(() -> {
            workspace.importCampaignImage(document, Path.of(campaignImagePath.get()));
            clearCampaignPreview();
            refresh();
        });
        input("Markdown description path", campaignDescriptionPath);
        if (ImGui.button("Import description")) operation(() -> {
            workspace.importCampaignDescription(document, Path.of(campaignDescriptionPath.get()));
            refresh();
        });
        ImGui.textWrapped("Descriptions support Markdown and images. Imported files are included in the exported datapack. A description file overrides inline description text.");
        if (ImGui.button("Create content in this folder")) { setCreateScope(document); createKind = DefinitionKind.QUEST; showCreate = true; }
        ImGui.textWrapped(addon ? "Install the exported datapack, then enable this addon in Campaign addons."
                : "Edit the card details and content settings in Properties. Unlock the campaign when it is ready for world creation. The selected campaign is permanent.");
    }

    private void clearCampaignPreview() {
        if (campaignPreview != null) campaignPreview.close();
        campaignPreview = null;
        campaignPreviewKey = "";
    }

    private boolean hasPendingFields() {
        return pendingFields.pending() || translatedFields.values().stream().anyMatch(EditorFieldEdits::pending) || packFiles.pending() || packBuild.pending();
    }

    private void applyPendingFields() throws IOException {
        boolean changed = packFiles.pending() || packBuild.pending();
        if (workspace != null) {
            packFiles.apply(workspace);
            packBuild.load(workspace);
            packBuild.apply();
        }
        if (document != null) pendingFields.validate(document);
        translatedFields.forEach((owner, edits) -> edits.validate(owner));
        if (pendingFields.apply(document)) { historyDocument = document; changed = true; }
        for (var entry : translatedFields.entrySet()) {
            if (entry.getValue().apply(entry.getKey())) { historyDocument = entry.getKey(); changed = true; }
        }
        if (changed) refresh();
    }

    private java.util.Set<String> selectedNodes() {
        return nodeSelection.isEmpty() && !selectedNode.isEmpty() ? java.util.Set.of(selectedNode) : java.util.Set.copyOf(nodeSelection);
    }

    private void pasteNodes() {
        var added = graphClipboard.paste(workspace, document);
        refresh(); nodeSelection.clear(); nodeSelection.addAll(added);
        selectedNode = added.getFirst(); graphFocus = selectedNode;
        setPointer(DialogueGraph.nodes(document).stream().filter(value -> value.id().equals(selectedNode)).findFirst().orElseThrow().pointer());
    }

    private void navigateNode(int offset) {
        var nodes = DialogueGraph.nodes(document);
        if (nodes.isEmpty()) return;
        int index = 0;
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).id().equals(selectedNode)) index = i;
        var node = nodes.get(Math.floorMod(index + offset, nodes.size()));
        selectedNode = node.id(); setPointer(node.pointer()); graphFocus = node.id();
        nodeSelection.clear(); nodeSelection.add(node.id());
    }

    private void graphTools() {
        if (document == null || document.kind() == DefinitionKind.RESOURCE || document.kind().campaignManifest()) return;
        if (ImGui.checkbox("Show minimap", showMinimap)) showMinimap = !showMinimap;
        ImGui.textWrapped("Ctrl-click headers to select nodes. Ctrl+C copies, Ctrl+V pastes, Alt+Left/Right selects nodes. Shared localization stays linked.");
        if (ImGui.button("Copy selected nodes")) operation(() -> graphClipboard.copy(workspace, document, selectedNodes()));
        if (ImGui.button("Paste nodes")) operation(this::pasteNodes);
        if (!ImGui.collapsingHeader("Graph outline and bookmarks")) return;
        input("Find node or wording", nodeFilter);
        JsonObject metadata = document.metadata();
        var bookmarks = metadata.has("bookmarks") ? metadata.getAsJsonArray("bookmarks") : new JsonArray();
        for (var node : DialogueGraph.nodes(document)) {
            if (!(node.id() + JsonPointers.get(document.json(), node.pointer())).toLowerCase(Locale.ROOT).contains(nodeFilter.get().toLowerCase(Locale.ROOT))) continue;
            boolean bookmarked = bookmarks.asList().stream().anyMatch(value -> value.getAsString().equals(node.id()));
            if (ImGui.selectable((bookmarked ? "* " : "") + node.id(), selectedNode.equals(node.id()))) operation(() -> {
                selectedNode = node.id(); setPointer(node.pointer()); graphFocus = node.id(); nodeSelection.clear(); nodeSelection.add(node.id());
            });
            if (ImGui.smallButton((bookmarked ? "Unbookmark" : "Bookmark") + "##" + node.id())) operation(() -> document.editMetadata("Bookmark node", value -> {
                if (!value.has("bookmarks")) value.add("bookmarks", new JsonArray());
                var list = value.getAsJsonArray("bookmarks");
                if (bookmarked) list.asList().removeIf(entry -> entry.getAsString().equals(node.id())); else list.add(node.id());
            }));
        }
    }

    private void guidedObjective() {
        if (document.kind() != DefinitionKind.QUEST || selectedNode.isEmpty() || !ImGui.collapsingHeader("Add quest objective")) return;
        if (ImGui.beginCombo("Objective", objectiveType.replace('_', ' '))) {
            for (String type : com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry.descriptors().stream().map(value -> value.id()).sorted().toList())
                if (ImGui.selectable(type.replace('_', ' '), objectiveType.equals(type))) objectiveType = type;
            ImGui.endCombo();
        }
        boolean simple = List.of("item_check", "mob_kill", "block_break", "block_place", "block_interact", "trade").contains(objectiveType);
        if (simple) {
            if (!objectiveType.equals("trade")) {
                input("Resource ID", objectiveResource);
                ImGui.setNextItemWidth(-1);
                if (ImGui.beginCombo("Choose resource", objectiveResource.get())) {
                    var registry = objectiveType.equals("mob_kill") ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            : objectiveType.startsWith("block_") ? net.minecraft.core.registries.BuiltInRegistries.BLOCK : net.minecraft.core.registries.BuiltInRegistries.ITEM;
                    for (var id : registry.keySet().stream().sorted().toList()) if (ImGui.selectable(id.toString(), objectiveResource.get().equals(id.toString()))) objectiveResource.set(id.toString());
                    ImGui.endCombo();
                }
            }
            ImGui.inputInt("Required count", objectiveCount);
        } else ImGui.textWrapped("Add this objective, then fill its guided fields in Details.");
        if (ImGui.button("Track a player death")) operation(() -> {
            String added = EditorContentTools.appendObjective(document, selectedNode, EditorProgressConditions.deathObjective());
            refresh(); setPointer(added);
        });
        ImGui.textWrapped("The objective is added to this stage's completion requirements.");
        if (ImGui.button("Add objective")) operation(() -> {
            String added;
            if (List.of("item_check", "mob_kill", "block_break", "block_place", "block_interact", "trade").contains(objectiveType)) {
                added = EditorContentTools.appendObjective(document, selectedNode, objectiveType, objectiveResource.get(), objectiveCount.get());
            } else {
                JsonObject template = new JsonObject();
                template.addProperty("type", objectiveType);
                var schema = com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export();
                var model = new EditorSchema(schema);
                JsonObject objectiveSchema = model.effective(schema.getAsJsonObject("$defs").getAsJsonObject("objective"), template);
                JsonObject seeded = model.seed(objectiveSchema).getAsJsonObject();
                seeded.addProperty("type", objectiveType);
                added = EditorContentTools.appendObjective(document, selectedNode, seeded);
            }
            refresh(); setPointer(added);
        });
    }

    private void selectedDetails() {
        JsonElement selected = JsonPointers.get(document.json(), pointer.get());
        if (!selected.isJsonObject()) {
            inspectValue(selected, pointer.get(), "Value", 0);
            return;
        }
        JsonObject object = selected.getAsJsonObject();
        boolean matchMode = EditorProgressConditions.hasMatchMode(object);
        if (matchMode) matchMode(object, pointer.get());
        if (object.has("type") && object.get("type").isJsonPrimitive() && object.get("type").getAsString().equals("player_last_death"))
            ImGui.textWrapped("Each list matches any entry. If both lists are set, they must match the same death.");
        List<String> main = document.kind().campaignManifest()
                ? List.of("name", "description", "description_file", "allow_existing_worlds", "campaigns", "incompatible_campaigns", "requires", "incompatible_addons", "authors", "image", "locked", "include_default_content")
                : List.of("name", "description", "authors", "image", "locked", "include_default_content", "display", "title", "label", "text", "line", "lines", "variants", "exchanges", "response", "responses", "options", "objectives");
        boolean shown = false;
        for (String field : main) {
            if (!object.has(field)) continue;
            inspectValue(object.get(field), pointer.get() + "/" + escape(field), friendlyLabel(field), 0);
            shown = true;
        }
        if (!shown && object.isEmpty()) ImGui.textWrapped("This block is empty. Select the node header to add dialogue or behavior.");
        if (!shown || ImGui.collapsingHeader("More properties")) {
            for (var entry : object.entrySet()) {
                if (main.contains(entry.getKey()) || matchMode && entry.getKey().equals("all")) continue;
                inspectValue(entry.getValue(), pointer.get() + "/" + escape(entry.getKey()), friendlyLabel(entry.getKey()), 0);
            }
        }
        if (ImGui.treeNode("Guided properties")) { schemaObjectTools(object, pointer.get()); ImGui.treePop(); }
    }

    private void schemaObjectTools(JsonObject value, String path) {
        if (propertySchema == null) return;
        JsonObject rule = propertySchema.at(document.json(), path);
        JsonObject properties = EditorSchema.object(rule.get("properties"));
        var required = propertySchema.required(rule);
        if (ImGui.beginCombo("Add property##" + path, "Choose a field...")) {
            for (var entry : properties.entrySet()) {
                if (value.has(entry.getKey())) continue;
                if (ImGui.beginMenu(friendlyLabel(entry.getKey()) + (required.contains(entry.getKey()) ? " (required)" : ""))) {
                    for (var choice : propertySchema.choices(EditorSchema.object(entry.getValue())).entrySet()) {
                        if (ImGui.menuItem(choice.getKey())) operation(() -> replaceValue(path + "/" + escape(entry.getKey()), choice.getValue()));
                    }
                    ImGui.endMenu();
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.beginCombo("Remove property##" + path, "Choose an optional field...")) {
            for (String name : value.keySet()) if (!required.contains(name) && ImGui.selectable(friendlyLabel(name)))
                operation(() -> { document.edit("Remove property", root -> JsonPointers.remove(root, path + "/" + escape(name))); refresh(); });
            ImGui.endCombo();
        }
        if (!rule.has("additionalProperties") || !rule.get("additionalProperties").isJsonPrimitive()
                || rule.get("additionalProperties").getAsBoolean()) {
            if (ImGui.treeNode("Named fields##" + path)) {
                ImString name = fields.computeIfAbsent("schema_name:" + path, ignored -> new ImString(256));
                input("Field name", name);
                if (ImGui.beginCombo("Field value", "Choose a type...")) {
                    JsonObject additional = EditorSchema.object(rule.get("additionalProperties"));
                    var options = additional.isEmpty() ? Map.<String, JsonElement>of("Text", new JsonPrimitive(""), "Number", new JsonPrimitive(0),
                            "Toggle", new JsonPrimitive(false), "Object", new JsonObject(), "List", new JsonArray()) : propertySchema.choices(additional);
                    for (var choice : options.entrySet()) if (ImGui.selectable(choice.getKey())) operation(() -> {
                        if (name.get().isBlank() || value.has(name.get())) throw new IllegalArgumentException("Enter a new, nonempty field name");
                        replaceValue(path + "/" + escape(name.get()), choice.getValue());
                    });
                    ImGui.endCombo();
                }
                ImGui.treePop();
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
            case "campaigns" -> "Supported campaigns";
            case "incompatible_campaigns" -> "Incompatible campaigns";
            case "requires" -> "Required addons";
            case "incompatible_addons" -> "Incompatible addons";
            default -> field.isEmpty() ? "(unnamed field)" : Character.toUpperCase(field.charAt(0)) + field.substring(1).replace('_', ' ');
        };
    }

    private void localizedField(JsonObject reference, String label, String path) {
        String key = reference.get("key").getAsString();
        var location = workspace.textLocation(document, key, locale.get());
        ImGui.textUnformatted(label);
        if (location != null) {
            boolean fallback = !locale.get().equals("en_us") && !location.document().resourcePath().contains("/" + locale.get() + "/")
                    && !location.document().resourcePath().endsWith("/" + locale.get() + ".json");
            if (fallback) {
                ImGui.textWrapped("English fallback. Create a translation before editing this language.");
                if (location.document().resourcePath().endsWith("/locales/en_us.json") && ImGui.button("Create translation##" + path)) operation(() -> {
                    if (!locale.get().matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Choose a valid language code");
                    String targetPath = location.document().resourcePath().replace("/locales/en_us.json", "/locales/" + locale.get() + ".json");
                    var translated = workspace.documents().stream().filter(value -> value.resourcePath().equals(targetPath)).findFirst().orElse(null);
                    if (translated == null) { translated = new AuthoringDocument(DefinitionKind.RESOURCE, targetPath, new JsonObject()); workspace.add(translated); }
                    String messageKey = location.pointer().substring(location.pointer().lastIndexOf('/') + 1).replace("~1", "/").replace("~0", "~");
                    String initial = workspace.resolveText(document, reference, "en_us");
                    translated.edit("Create translation", root -> {
                        JsonObject messages = root.has("messages") ? root.getAsJsonObject("messages") : root;
                        messages.addProperty(messageKey, initial);
                    });
                    refresh();
                });
                ImGui.textWrapped(workspace.resolveText(document, reference, "en_us"));
                return;
            }
            JsonElement text = JsonPointers.get(location.document().json(), location.pointer());
            if (text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
                String bufferId = location.document().resourcePath() + location.pointer();
                ImString buffer = fields.computeIfAbsent(bufferId, ignored -> new ImString(text.getAsString(), 65536));
                ImGui.beginDisabled(rawModified);
                textFields.render(bufferId, buffer, document.kind(), !path.endsWith("/label") && !path.endsWith("/title"));
                ImGui.endDisabled();
                translatedFields.computeIfAbsent(location.document(), ignored -> new EditorFieldEdits())
                        .update(location.pointer(), text, buffer.get());
                if (ImGui.isItemHovered()) ImGui.setTooltip(locale.get() + " wording. The localization reference is preserved.");
                return;
            }
            ImGui.textWrapped(workspace.resolveText(document, reference, locale.get()));
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

    private void tutorialObjectiveCatalog(){
        if(!ImGui.collapsingHeader("Clipboard job objectives"))return;
        ImGui.textWrapped("Add a personal worker objective to the selected stage. Completion requirements are connected automatically. Productive checks read the saved bound run.");
        for(var role:com.jvn.villagerretaliation.interaction.HiredVillagerRole.values()){
            if(ImGui.treeNode(role.label()+"##tutorial_preset")){
                for(String check:java.util.List.of("assignment","area","storage","policy","selection","task","route","outcome","completed","disposed")){
                    if(ImGui.selectable(check+"##tutorial_"+role.serializedName())){
                        operation(() -> {
                            String added = EditorContentTools.appendObjective(document, selectedNode,
                                    com.jvn.villagerretaliation.quest.tutorial.TutorialAuthoringPresets.objective(role,check));
                            refresh(); setPointer(added);
                        });
                    }
                }
                ImGui.treePop();
            }
        }
    }

    private void conditionCatalog() {
        if (!ImGui.collapsingHeader("Runtime condition catalog")) return;
        ImGui.textWrapped("Select a conditions list, then choose a runtime condition. Guided properties supplies its declared fields.");
        for (var descriptor : com.jvn.villagerretaliation.dialogue.DialogueCondition.descriptors()) {
            if (ImGui.selectable(EditorProgressConditions.label(descriptor.id()) + "##" + descriptor.id())) {
                JsonObject condition = new JsonObject();
                condition.addProperty("type", descriptor.id());
                operation(() -> {
                    if (!pointer.get().endsWith("/conditions") || !JsonPointers.get(document.json(), pointer.get()).isJsonArray())
                        throw new IllegalArgumentException("Focus a conditions list first");
                    var schema = new EditorSchema(com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export());
                    JsonObject rule = schema.effective(com.jvn.villagerretaliation.quest.schema.ContentSchemaDefinitions.definitions().getAsJsonObject("condition"), condition);
                    JsonObject template = EditorProgressConditions.seed(descriptor.id());
                    JsonObject seeded = template == null ? schema.seed(rule).getAsJsonObject() : template;
                    seeded.addProperty("type", descriptor.id());
                    editArray(pointer.get(), values -> values.add(seeded));
                });
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
                boolean matchMode = EditorProgressConditions.hasMatchMode(value.getAsJsonObject());
                if (matchMode) matchMode(value.getAsJsonObject(), path);
                for (var entry : value.getAsJsonObject().entrySet()) {
                    if (matchMode && entry.getKey().equals("all")) continue;
                    inspectValue(entry.getValue(), path + "/" + escape(entry.getKey()), friendlyLabel(entry.getKey()), depth + 1);
                }
                schemaObjectTools(value.getAsJsonObject(), path);
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
                JsonObject rule = propertySchema.at(document.json(), path);
                if (ImGui.beginCombo("Add entry", "Choose an entry type...")) {
                    for (var choice : propertySchema.choices(EditorSchema.object(rule.get("items"))).entrySet())
                        if (ImGui.selectable(choice.getKey())) editArray(path, values -> values.add(choice.getValue().deepCopy()));
                    ImGui.endCombo();
                }
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
            else {
                apply = ImGui.inputText("##value", input, ImGuiInputTextFlags.EnterReturnsTrue);
                if (text) propertyPicker(path, input);
            }
            ImGui.endDisabled();
            pendingFields.update(path, value, input.get());
            if (apply) operation(() -> { });
        }
        ImGui.popID();
    }

    private void matchMode(JsonObject condition, String path) {
        boolean all = !condition.has("all") || condition.get("all").getAsBoolean();
        if (ImGui.beginCombo("Match entries", all ? "All" : "Any")) {
            if (ImGui.selectable("All", all)) operation(() -> replaceValue(path + "/all", new JsonPrimitive(true)));
            if (ImGui.selectable("Any", !all)) operation(() -> replaceValue(path + "/all", new JsonPrimitive(false)));
            ImGui.endCombo();
        }
    }

    private void propertyPicker(String path, ImString input) {
        String field = EditorProgressConditions.resourceField(path);
        List<String> choices = switch (field) {
            case "time" -> List.of("morning", "afternoon", "evening", "night");
            case "weather" -> List.of("clear", "rain", "thunder");
            case "state" -> List.of("available", "active", "completed", "failed", "abandoned");
            case "item" -> net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).sorted().toList();
            case "entity" -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString).sorted().toList();
            case "block" -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString).sorted().toList();
            case "quest", "quest_id" -> workspace.documents().stream().filter(value -> value.kind() == DefinitionKind.QUEST).map(AuthoringDocument::id).sorted().toList();
            case "trigger" -> java.util.Arrays.stream(com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueTrigger.values()).map(value -> value.name().toLowerCase(Locale.ROOT)).toList();
            default -> EditorProgressConditions.resources(field);
        };
        var parent = JsonPointers.get(document.json(), path.substring(0, path.lastIndexOf('/')));
        if (field.equals("target") && parent.isJsonObject()) {
            var type = parent.getAsJsonObject().get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()) {
                if (type.getAsString().equals("player_stat")) choices = EditorProgressConditions.statisticTargets(parent.getAsJsonObject());
                else if (type.getAsString().equals("event_history")) choices = EditorProgressConditions.historyTargets(parent.getAsJsonObject());
            }
        }
        JsonObject rule = propertySchema.at(document.json(), path);
        if (rule.has("enum")) choices = rule.getAsJsonArray("enum").asList().stream().map(JsonElement::getAsString).toList();
        if (rule.has("description")) ImGui.textWrapped(rule.get("description").getAsString());
        ImGui.setNextItemWidth(-1);
        if (!choices.isEmpty() && ImGui.beginCombo("##choose_" + field, "Choose " + field + "...")) {
            for (String choice : choices) if (ImGui.selectable(choice, input.get().equals(choice))) input.set(choice);
            ImGui.endCombo();
        }
    }

    private void enterPlaytest() {
        view = 3;
        EditorCameraPanel.close();
        EditorPlaytestViewport.open(playtestFraction, () -> {
            if (MC.screen == null) MC.setScreen(screen);
            MC.mouseHandler.releaseMouse();
            focusPanel = "Playtest###VR_Playtest";
        });
    }

    private void leavePlaytest(int next) {
        EditorPlaytestPresentation.clear();
        EditorLiveQuestTest.stop();
        AuthoringDialoguePreview.stop();
        EditorPlaytestViewport.close();
        view = next;
        MC.setScreen(screen);
        focusPanel = WORK_PANEL;
    }

    private void playtest() {
        var viewport = ImGui.getMainViewport();
        float width = viewport.getWorkSizeX();
        float height = viewport.getWorkSizeY();
        float fraction = PlaytestLayout.clampFraction(playtestFraction, width, preferences.textScale());
        EditorPlaytestViewport.fraction(fraction);
        float split = Math.round(width * fraction);
        ImGui.setNextWindowPos(viewport.getWorkPosX() + split - 3, viewport.getWorkPosY());
        ImGui.setNextWindowSize(6, height);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowMinSize, 1, 1);
        ImGui.begin("##VR_PlaytestDivider", ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoDocking);
        ImGui.getWindowDrawList().addRectFilled(ImGui.getWindowPosX(), ImGui.getWindowPosY(),
                ImGui.getWindowPosX() + 6, ImGui.getWindowPosY() + height, 0xff505050);
        ImGui.invisibleButton("##resize_playtest", 6, Math.max(1, ImGui.getContentRegionAvailY()));
        if (ImGui.isItemHovered() || ImGui.isItemActive()) ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.ResizeEW);
        if (ImGui.isItemActive()) {
            playtestFraction = Math.clamp(PlaytestLayout.clampFraction((ImGui.getMousePosX() - viewport.getWorkPosX()) / width,
                    width, preferences.textScale()), .2F, .9F);
        }
        if (ImGui.isItemDeactivated()) operation(() -> {
            preferences = preferences.withPlaytestFraction(playtestFraction);
            savePreferences();
        });
        ImGui.end();
        ImGui.popStyleVar(2);
        ImGui.setNextWindowPos(viewport.getWorkPosX() + split + 3, viewport.getWorkPosY());
        ImGui.setNextWindowSize(Math.max(1, width - split - 3), height);
        ImGui.begin("Playtest###VR_Playtest", ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoDocking);
        ImGui.setWindowFontScale(preferences.textScale());
        if (focusPanel.equals("Playtest###VR_Playtest")) { ImGui.setWindowFocus(); focusPanel = ""; }
        if (ImGui.button("Edit")) operation(() -> leavePlaytest(0));
        ImGui.sameLine();
        if (ImGui.button("Preview")) operation(() -> leavePlaytest(1));
        ImGui.sameLine();
        if (ImGui.button("Build pack")) operation(() -> { leavePlaytest(2); validate(); });
        if (ImGui.button("Save")) operation(this::save);
        ImGui.sameLine();
        if (ImGui.button("Stop playtest")) operation(() -> leavePlaytest(0));
        ImGui.textWrapped("Click the game to play. F9 returns control to this panel.");
        recoveryControls();
        ImGui.beginDisabled(EditorTestWorld.applying());
        if (workspace == null) {
            ImGui.textWrapped("Open a workspace in Edit first.");
        } else {
            ImGui.setNextItemWidth(-1);
            if (ImGui.beginCombo("##playtest_content", document == null ? "Choose content" : document.id())) {
                for (var candidate : workspace.documents()) if (candidate.kind() != DefinitionKind.RESOURCE
                        && ImGui.selectable(candidate.id(), document == candidate)) operation(() -> select(candidate));
                ImGui.endCombo();
            }
            if (document != null) {
                ImGui.separatorText("Steps");
                for (var node : DialogueGraph.nodes(document)) {
                    String running = document.kind() == DefinitionKind.QUEST ? EditorLiveQuestTest.snapshot().stage()
                            : AuthoringDialoguePreview.cursor() == null ? "" : AuthoringDialoguePreview.cursor().node();
                    if (ImGui.selectable((node.id().equals(running) ? "> " : "") + node.id(), node.id().equals(selectedNode))) operation(() -> {
                        selectedNode = node.id(); setPointer(node.pointer()); fields.clear(); textFields.clear();
                    });
                }
                if (document.kind() == DefinitionKind.QUEST) playtestQuest();
                else playtestDialogue();
                if (ImGui.collapsingHeader("Step properties", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                    try { selectedDetails(); }
                    catch (IllegalArgumentException | IllegalStateException | ClassCastException | IndexOutOfBoundsException exception) {
                        ImGui.textWrapped("Choose an existing step: " + exception.getMessage());
                    }
                }
                if (hasPendingFields() && ImGui.button("Apply field edits")) operation(() -> { });
            }
        }
        ImGui.endDisabled();
        if (EditorTestWorld.applying()) ImGui.textWrapped("Applying draft and reloading test content...");
        boolean editing = ImGui.isAnyItemActive();
        if (playtestEditing && !editing && hasPendingFields()) operation(() -> { });
        playtestEditing = editing;
        ImGui.separator();
        ImGui.textWrapped(status);
        ImGui.end();
        if (MC.screen == screen && ImGui.getMousePosX() < viewport.getWorkPosX() + split - 3
                && ImGui.isMouseClicked(0) && !confirmClose && !showRecovery) MC.setScreen(null);
    }

    private void updatePlaytestPresentation() {
        if (workspace == null || document == null || rawModified) return;
        Map<String, JsonObject> latest = EditorTestWorld.content(workspace);
        var replacements = new java.util.LinkedHashMap<>(EditorLiveQuestTest.snapshot().replacements());
        replacements.putAll(conditions.placeholders());
        if (MC.player != null) replacements.putIfAbsent("player", MC.player.getName().getString());
        if (target != null) {
            replacements.putIfAbsent("villager", target.getName().getString());
            replacements.putIfAbsent("villager_name", target.getName().getString());
        }
        if (latest.equals(playtestVisuals) && locale.get().equals(playtestLocale) && replacements.equals(playtestReplacements)) return;
        if (workspace.validate().stream().anyMatch(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR)) return;
        playtestVisuals = latest;
        playtestLocale = locale.get();
        playtestReplacements = Map.copyOf(replacements);
        EditorPlaytestPresentation.update(workspace, document, EditorTestWorld.appliedContent(workspace), replacements, locale.get(), conditions.speakers());
        if (AuthoringDialoguePreview.active()) attempt(() -> AuthoringDialoguePreview.refreshPresentation(document.json()));
    }

    private void applyPlaytestDraft() throws IOException {
        var errors = workspace.validate().stream().filter(issue -> issue.severity() == AuthoringDiagnostic.Severity.ERROR).toList();
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.getFirst().resourcePath() + errors.getFirst().pointer() + ": " + errors.getFirst().message());
        if (document.kind() != DefinitionKind.QUEST) {
            AuthoringDialoguePreview.stop();
            if (!playtestStepRemoved) startPreview(selectedNode);
            else status = "Selected step was removed. Choose a step and replay explicitly.";
            return;
        }
        if (EditorLiveQuestTest.view().busy()) throw new IllegalStateException("Wait for the current quest operation to finish");
        AuthoringDialoguePreview.stop();
        if (MC.screen != screen) MC.setScreen(screen);
        var expected = document;
        String stage = selectedNode;
        boolean replay = !playtestStepRemoved;
        var world = MC.getSingleplayerServer();
        EditorTestWorld.apply(workspace).whenComplete((ignored, failure) -> MC.execute(() -> {
            if (failure != null) {
                status = "Draft reload failed: " + failure.getMessage();
                return;
            }
            playtestVisuals = Map.of();
            if (view != 3 || document != expected || MC.getSingleplayerServer() != world) return;
            if (replay) attempt(() -> EditorLiveQuestTest.startAt(workspace, document, playtestHold, stage));
            status = replay ? "Draft applied in this test world. Replaying " + stage : "Draft applied. Selected step was removed. Replay the entry step explicitly.";
        }));
    }

    private void playtestDialogue() {
        if (ImGui.button("Nearest villager") && MC.level != null && MC.player != null)
            target = MC.level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, MC.player.getBoundingBox().inflate(24))
                    .stream().min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(MC.player))).orElse(null);
        ImGui.textWrapped(target == null ? "Choose a nearby villager to play dialogue." : "Speaker: " + target.getName().getString());
        ImGui.beginDisabled(target == null);
        if (ImGui.button("Play from entry")) startPreview("");
        if (ImGui.button("Jump / replay selected step")) startPreview(selectedNode);
        if (AuthoringDialoguePreview.active()) {
            if (!com.jvn.villagerretaliation.authoring.preview.PlaytestDrafts.logic(AuthoringDialoguePreview.definition())
                    .equals(com.jvn.villagerretaliation.authoring.preview.PlaytestDrafts.logic(document.json())))
                ImGui.textWrapped("Logic changes pending. Visual edits update automatically.");
            if (ImGui.button("Apply & replay")) operation(this::applyPlaytestDraft);
        }
        ImGui.endDisabled();
        var cursor = AuthoringDialoguePreview.cursor();
        if (cursor != null) {
            ImGui.textWrapped("Playing " + cursor.node() + " | Exchange " + (cursor.exchange() + 1) + "/" + cursor.exchanges());
            ImGui.beginDisabled(!cursor.previous());
            if (ImGui.button("Previous exchange")) operation(AuthoringDialoguePreview::previous);
            ImGui.endDisabled();
            ImGui.beginDisabled(!cursor.next());
            if (ImGui.button("Next exchange")) operation(AuthoringDialoguePreview::next);
            ImGui.endDisabled();
            if (cursor.exchanges() > 1 && ImGui.beginCombo("Exchange", Integer.toString(cursor.exchange() + 1))) {
                for (int i = 0; i < cursor.exchanges(); i++) {
                    int index = i;
                    if (ImGui.selectable("Exchange " + (i + 1), i == cursor.exchange())) operation(() -> AuthoringDialoguePreview.exchange(index));
                }
                ImGui.endCombo();
            }
            var choices = AuthoringDialoguePreview.choices();
            for (int i = 0; i < choices.size(); i++) {
                int index = i;
                var choice = choices.get(i);
                ImGui.beginDisabled(!choice.available());
                if (ImGui.button(choice.label() + "##playtest_choice" + i)) operation(() -> AuthoringDialoguePreview.choose(index));
                ImGui.endDisabled();
                if (!choice.available()) ImGui.textWrapped(choice.reason());
            }
            if (ImGui.button("Stop dialogue")) operation(AuthoringDialoguePreview::stop);
        }
        if (ImGui.collapsingHeader("Dialogue context")) {
            EditorPreviewConditions.time(conditions);
            EditorPreviewConditions.render(conditions, document.json());
        }
        if (ImGui.collapsingHeader("Dialogue log")) for (String entry : AuthoringDialoguePreview.log()) ImGui.textWrapped(entry);
    }

    private void playtestQuest() {
        if (!EditorTestWorld.active(workspace)) {
            ImGui.textWrapped("Create a separate test world for quest playtesting.");
            if (ImGui.button("Create live quest test world")) operation(() -> EditorTestWorld.prepare(workspace, screen));
            return;
        }
        var state = EditorLiveQuestTest.snapshot();
        if (EditorPlaytestPresentation.ambiguousEdits()) ImGui.textWrapped("Repeated wording needs Apply & replay to update the intended live line.");
        ImGui.textWrapped("Live stage: " + state.stage() + " | " + state.state());
        if (ImGui.checkbox("Wait for Next", playtestHold)) {
            playtestHold = !playtestHold;
            if (!state.stage().isBlank()) operation(() -> EditorLiveQuestTest.hold(workspace, document, playtestHold));
        }
        if (!EditorTestWorld.currentSnapshot(workspace)) {
            ImGui.textWrapped(EditorTestWorld.currentLogic(workspace) ? "Visual changes previewed. Apply to load them into the test pack."
                    : "Logic changes pending. Apply & replay to load this draft.");
        }
        if (ImGui.button("Apply & replay")) operation(this::applyPlaytestDraft);
        ImGui.beginDisabled(!EditorTestWorld.currentLogic(workspace) || EditorLiveQuestTest.view().busy());
        if (ImGui.button("Start / restart quest")) operation(() -> EditorLiveQuestTest.start(workspace, document, playtestHold));
        ImGui.beginDisabled(state.stage().isBlank());
        if (ImGui.button("Jump to selected stage")) operation(() -> EditorLiveQuestTest.stage(workspace, document, selectedNode));
        if (ImGui.button("Replay current stage")) operation(() -> EditorLiveQuestTest.replay(workspace, document));
        ImGui.beginDisabled(!state.ready());
        if (ImGui.button("Next stage")) operation(() -> EditorLiveQuestTest.next(workspace, document));
        ImGui.endDisabled();
        if (!state.ready()) ImGui.textWrapped(state.blocker());
        for (var transition : state.transitions()) {
            ImGui.beginDisabled(!transition.available());
            if (ImGui.button(transition.label() + "##quest_transition" + transition.id()))
                operation(() -> EditorLiveQuestTest.transition(workspace, document, transition.id()));
            ImGui.endDisabled();
            if (!transition.available()) ImGui.textWrapped(transition.reason());
        }
        if (ImGui.button("Turn in quest")) operation(() -> EditorLiveQuestTest.turnIn(workspace, document));
        if (ImGui.button("Open quest dialogue") && MC.player != null && MC.gameMode != null && MC.level != null) {
            var provider = MC.level.getEntity(state.provider());
            if (provider == null) status = "The quest provider is not loaded.";
            else MC.gameMode.interact(MC.player, provider, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        if (ImGui.button("Open quest journal")) MC.setScreen(new com.jvn.villagerretaliation.client.interaction.VillagerQuestJournalScreen());
        ImGui.separatorText("Current requirements");
        ImGui.inputText("Fact / reputation value", playtestValue);
        for (var objective : state.objectives()) {
            ImGui.pushID(objective.id());
            ImGui.textWrapped((objective.complete() ? "Complete: " : "Pending: ") + objective.id() + " (" + objective.type() + ")");
            ImGui.progressBar(objective.progress(), -1, 0);
            ImGui.textWrapped(objective.reason());
            if (!objective.setup().isBlank()) {
                if (ImGui.button(objective.setup())) operation(() -> EditorLiveQuestTest.prepare(workspace, document, objective.id(), playtestValue.get()));
            } else ImGui.textWrapped("Requires gameplay or configured campaign bindings. A test bypass is available.");
            if (ImGui.button("Force complete objective")) operation(() -> EditorLiveQuestTest.force(workspace, document, objective.id()));
            ImGui.popID();
        }
        if (ImGui.button("Force stage ready")) operation(() -> EditorLiveQuestTest.force(workspace, document, ""));
        if (ImGui.button("Clear quest progress")) operation(() -> EditorLiveQuestTest.clear(workspace, document));
        ImGui.endDisabled();
        ImGui.endDisabled();
        ImGui.textWrapped(EditorLiveQuestTest.view().message());
        if (ImGui.collapsingHeader("Test shortcuts and actions")) for (String line : state.log()) ImGui.textWrapped(line);
        if (ImGui.collapsingHeader("Runtime diagnostics")) for (String line : EditorLiveQuestTest.view().lines()) ImGui.textWrapped(line);
    }

    private void preview() {
        panel(WORK_PANEL);
        ImGui.textUnformatted("Preview dialogue");
        if (document == null) {
            ImGui.textWrapped("Choose a dialogue in Content first.");
            ImGui.end();
            return;
        }
        if (document.kind().campaignManifest()) {
            campaignCard();
            ImGui.end();
            return;
        }
        if (document.kind() == DefinitionKind.QUEST) {
            questSimulation();
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
        if (ImGui.button(document.kind() == DefinitionKind.RESOURCE ? "Play selected object" : "Play selected node"))
            startPreview(selectedNode.isEmpty() ? pointer.get() : selectedNode);
        ImGui.endDisabled();
        if (target == null) ImGui.textDisabled("Select a target to play.");
        else EditorCameraPanel.render(document, pointer.get(), target, screen, this::refresh, this::operation, preferences.textScale());
        EditorPreviewConditions.render(conditions, document == null ? null : document.json());
        previewScenarios();
        if (ImGui.collapsingHeader("Preview log"))
            for (String entry : AuthoringDialoguePreview.log()) ImGui.textWrapped(entry);
        ImGui.end();
    }

    private void questSimulation() {
        if (EditorTestWorld.active(workspace)) {
            if (ImGui.checkbox("Use live test world", liveQuestMode)) liveQuestMode = !liveQuestMode;
            if (liveQuestMode) { liveQuestTest(); return; }
        }
        conditions.worldTime(MC.level.getDayTime());
        ImGui.textWrapped("Isolated quest simulation. Objective completion is simulated. Actions and rewards are logged.");
        if (ImGui.button("Start / restart quest test")) operation(() -> questPreview = new com.jvn.villagerretaliation.authoring.preview.QuestPreviewSession(document, conditions));
        if (questPreview != null) {
            ImGui.textUnformatted("Current stage: " + questPreview.currentStage());
            for (String objective : questPreview.objectives()) {
                boolean complete = questPreview.completed(objective);
                if (ImGui.checkbox(objective, complete)) questPreview.completed(objective, !complete);
            }
            if (questPreview.ready()) ImGui.textWrapped("Stage requirements met.");
            else for (String blocker : questPreview.blockers()) ImGui.textWrapped(blocker);
            ImGui.beginDisabled(!questPreview.ready());
            if (ImGui.button("Advance quest")) operation(questPreview::advance);
            ImGui.endDisabled();
            for (var transition : questPreview.transitions()) {
                var availability = questPreview.availability(transition);
                ImGui.beginDisabled(availability != com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.MATCHED);
                if (ImGui.button(transition.label() + "##" + transition.id())) operation(() -> questPreview.choose(transition.id()));
                ImGui.endDisabled();
                if (availability != com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.MATCHED) ImGui.textWrapped("Transition " + availability.name().toLowerCase(Locale.ROOT));
            }
            for (String line : questPreview.log()) ImGui.textWrapped(line);
        }
        EditorPreviewConditions.time(conditions);
        EditorPreviewConditions.render(conditions, document.json());
        previewScenarios();
        liveQuestTest();
    }

    private void liveQuestTest() {
        if (!ImGui.collapsingHeader("Live quest test", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) return;
        if (!EditorTestWorld.active(workspace)) {
            ImGui.textWrapped("Create a separate test world to execute real objectives, actions, and rewards.");
            if (ImGui.button("Create live quest test world")) operation(() -> EditorTestWorld.prepare(workspace, screen));
            return;
        }
        var state = EditorLiveQuestTest.view();
        if (state.quest().equals(document.id())) {
            ImGui.textWrapped(state.summary());
            ImGui.textWrapped(state.message());
        }
        ImGui.textWrapped("Live tests change this test world. Restarting a quest repeats its actions. Return to the game to make objective progress, then refresh here.");
        boolean current = EditorTestWorld.currentSnapshot(workspace);
        if (!current) ImGui.textWrapped("Content changed after export. Open Playtest and use Apply & replay to load your latest draft.");
        ImGui.beginDisabled(!current || EditorLiveQuestTest.view().busy());
        if (ImGui.button("Start / restart live quest")) operation(() -> EditorLiveQuestTest.start(workspace, document));
        if (ImGui.button("Refresh live state")) operation(() -> EditorLiveQuestTest.inspect(workspace, document));
        if (ImGui.button("Turn in live quest")) operation(() -> EditorLiveQuestTest.turnIn(workspace, document));
        if (!selectedNode.isEmpty() && ImGui.button("Jump to selected stage")) operation(() -> EditorLiveQuestTest.stage(workspace, document, selectedNode));
        if (ImGui.button("Clear live quest progress")) operation(() -> EditorLiveQuestTest.clear(workspace, document));
        ImGui.endDisabled();
        if (ImGui.button("Return to game")) closeEditor();
        if (ImGui.button("Create fresh test world")) operation(() -> EditorTestWorld.prepare(workspace, screen));
        if (state.quest().equals(document.id()) && ImGui.collapsingHeader("Live quest diagnostics"))
            for (String line : state.lines()) ImGui.textWrapped(line);
    }

    private void previewScenarios() {
        if (!ImGui.collapsingHeader("Saved test scenarios")) return;
        input("Scenario name", scenarioName);
        if (ImGui.button("Save scenario")) operation(() -> {
            if (scenarioName.get().isBlank()) throw new IllegalArgumentException("Enter a scenario name");
            document.editMetadata("Save scenario", metadata -> {
                if (!metadata.has("preview_scenarios")) metadata.add("preview_scenarios", new JsonObject());
                metadata.getAsJsonObject("preview_scenarios").add(scenarioName.get(), conditions.snapshot());
            });
        });
        JsonObject metadata = document.metadata();
        if (metadata.has("preview_scenarios")) for (var entry : metadata.getAsJsonObject("preview_scenarios").entrySet()) {
            if (ImGui.button("Load " + entry.getKey())) operation(() -> { conditions.restore(entry.getValue().getAsJsonObject()); EditorPreviewConditions.sync(conditions); questPreview = null; });
            ImGui.sameLine();
            if (ImGui.smallButton("Remove##scenario" + entry.getKey())) operation(() -> document.editMetadata("Remove scenario", value -> value.getAsJsonObject("preview_scenarios").remove(entry.getKey())));
        }
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
            if (view == 3) { AuthoringDialoguePreview.stop(); playtestStepRemoved = false; playtestVisuals = Map.of(); }
            var authoredId = net.minecraft.resources.ResourceLocation.tryParse(document.id());
            var evaluationId = authoredId == null
                    ? net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("villagerretaliation", "authoring_preview") : authoredId;
            if (guideStep == 3) guideStep = 4;
            conditions.worldTime(MC.level.getDayTime());
            AuthoringDialoguePreview.start(document, node, target, conditions, () -> MC.setScreen(MC.level != null && MC.player != null ? screen : null), key -> {
                JsonObject reference = new JsonObject();
                reference.addProperty("key", key);
                String resolved = workspace.resolveText(document, reference, locale.get(),
                        conditions.variant(), candidate -> conditions.evaluate(evaluationId, candidate)
                                == com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.MATCHED);
                return resolved.equals(key) ? null : resolved;
            }, view != 3);
        });
    }

    private static String diagnosticHint(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        if (text.contains("missing target") || text.contains("missing entry")) return "Choose an existing node or create a connected destination.";
        if (text.contains("unreachable")) return "Connect this node from the conversation, or make it an entry.";
        if (text.contains("duplicate")) return "Give the conflicting entries distinct IDs.";
        if (text.contains("local") || text.contains("translation")) return "Check the selected language and import its locale file.";
        return "Select this issue to open the affected field.";
    }

    private void openPackFile(String path) {
        var content = workspace.documents().stream().filter(candidate -> candidate.resourcePath().equals(path)).findFirst().orElse(null);
        select(content);
        packFiles.select(path);
        filesMode = true;
        view = content == null ? 4 : 0;
        focusPanel = WORK_PANEL;
    }

    private void files() {
        panel(WORK_PANEL);
        if (workspace == null) ImGui.textWrapped("Open a workspace first.");
        else packFiles.render(workspace, exportPath.get(), path -> operation(() -> openPackFile(path)), this::operation, this::attempt, this::refresh);
        ImGui.end();
    }

    private void buildPackage() throws IOException {
        packFiles.apply(workspace);
        packBuild.load(workspace);
        packBuild.apply();
        validate();
        save();
        Path backup = com.jvn.villagerretaliation.authoring.core.AuthoringPackBuild.build(workspace, Path.of(exportPath.get()), packBuild.zip());
        preferences = preferences.withExport(exportPath.get()); savePreferences();
        status = "Built pack at " + exportPath.get() + (backup == null ? "" : ". Previous version: " + backup);
        if (guideStep == 4) dismissGuide();
    }

    private void diagnostics() {
        panel(WORK_PANEL);
        if (workspace == null) {
            ImGui.textWrapped("Open a workspace in Content first.");
            ImGui.end();
            return;
        }
        try { packBuild.render(workspace, diagnostics, exportPath, path -> operation(() -> openPackFile(path)), this::operation, this::validate, this::buildPackage); }
        catch (IOException exception) { ImGui.textWrapped("Could not read pack contents: " + exception.getMessage()); }
        if (ImGui.button("Open output folder")) attempt(() -> {
            Path output = Path.of(exportPath.get());
            net.minecraft.Util.getPlatform().openFile((packBuild.zip() ? output.getParent() : output).toFile());
        });
        if (ImGui.button("Prepare separate test world")) operation(() -> { save(); EditorTestWorld.prepare(workspace, screen); });
        ImGui.separator();
        ImGui.textWrapped(status);
        for (var diagnostic : diagnostics) {
            if (ImGui.selectable(diagnostic.severity() + ": " + diagnostic.message() + "##"
                    + diagnostic.resourcePath() + diagnostic.pointer())) {
                if (workspace != null) {
                    if (diagnostic.resourcePath().equals("pack.mcmeta")) operation(() -> openPackFile("pack.mcmeta"));
                    for (var candidate : workspace.documents()) {
                        if (candidate.resourcePath().equals(diagnostic.resourcePath())) {
                            operation(() -> {
                                select(candidate);
                                var location = EditorDiagnosticLocation.resolve(candidate, diagnostic.pointer());
                                setPointer(location.pointer());
                                selectedNode = location.nodeId();
                                showResources = candidate.kind() == DefinitionKind.RESOURCE || showResources;
                                status = diagnostic.resourcePath() + diagnostic.pointer() + ": " + diagnostic.message();
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

    private void writeRecovery() throws IOException {
        if (workspace == null || showRecovery || !(workspace.dirty() || hasPendingFields() || rawModified)) return;
        JsonObject buffers = new JsonObject();
        if (document != null) buffers.addProperty("document", document.resourcePath());
        buffers.addProperty("pointer", pointer.get());
        buffers.addProperty("node", selectedNode);
        buffers.add("fields", pendingFields.snapshot());
        buffers.add("pack_file", packFiles.recovery());
        buffers.add("pack_settings", packBuild.recovery());
        JsonObject translated = new JsonObject();
        translatedFields.forEach((owner, edits) -> translated.add(owner.resourcePath(), edits.snapshot()));
        buffers.add("translated", translated);
        if (rawModified) buffers.addProperty("raw", jsonText.get());
        workspace.saveRecovery(buffers);
    }

    private void recoveryControls() {
        if (showRecovery) {
            ImGui.textWrapped("Recovered drafts are available. Choose a version before continuing.");
            if (ImGui.button("Recover drafts")) attempt(() -> {
                workspace = AuthoringWorkspace.recover(workspace.directory());
                packFiles.reset();
                packBuild.load(workspace);
                JsonObject buffers = workspace.recoveryBuffers();
                String path = buffers.has("document") ? buffers.get("document").getAsString() : "";
                select(workspace.documents().stream().filter(value -> value.resourcePath().equals(path)).findFirst().orElse(null));
                if (document != null) {
                    if (buffers.has("pointer")) pointer.set(buffers.get("pointer").getAsString());
                    if (buffers.has("node")) selectedNode = buffers.get("node").getAsString();
                    if (buffers.has("fields")) {
                        pendingFields.restore(buffers.getAsJsonObject("fields"));
                        buffers.getAsJsonObject("fields").entrySet().forEach(entry -> fields.put(entry.getKey(),
                                new ImString(entry.getValue().getAsJsonObject().get("text").getAsString(), 65536)));
                    }
                    if (buffers.has("translated")) for (var entry : buffers.getAsJsonObject("translated").entrySet()) {
                        var owner = workspace.documents().stream().filter(value -> value.resourcePath().equals(entry.getKey())).findFirst().orElse(null);
                        if (owner == null) continue;
                        EditorFieldEdits edits = new EditorFieldEdits();
                        edits.restore(entry.getValue().getAsJsonObject());
                        translatedFields.put(owner, edits);
                        entry.getValue().getAsJsonObject().entrySet().forEach(field -> fields.put(owner.resourcePath() + field.getKey(),
                                new ImString(field.getValue().getAsJsonObject().get("text").getAsString(), 65536)));
                    }
                    if (buffers.has("raw")) { jsonText.set(buffers.get("raw").getAsString()); rawModified = true; }
                }
                if (buffers.has("pack_file")) {
                    packFiles.recover(workspace, buffers.getAsJsonObject("pack_file"));
                    if (packFiles.pending()) view = 4;
                }
                if (buffers.has("pack_settings")) packBuild.recover(buffers.getAsJsonObject("pack_settings"));
                showRecovery = false;
                status = "Drafts recovered. Save when ready.";
            });
            ImGui.sameLine();
            if (ImGui.button("Keep saved version")) attempt(() -> { workspace.discardRecovery(); showRecovery = false; });
        }
        if (confirmClose) {
            ImGui.textWrapped("You have unsaved drafts. Save them or keep a recovery copy before closing.");
            if (ImGui.button("Save and close")) operation(() -> { save(); closeEditor(); });
            ImGui.sameLine();
            if (ImGui.button("Keep recovery and close")) attempt(() -> { writeRecovery(); closeEditor(); });
            ImGui.sameLine();
            if (ImGui.button("Continue editing")) confirmClose = false;
        }
    }

    private void closeEditor() {
        confirmClose = false;
        EditorPlaytestPresentation.clear();
        EditorLiveQuestTest.stop();
        EditorPlaytestViewport.close();
        AuthoringDialoguePreview.stop();
        EditorCameraPanel.close();
        packFiles.close();
        MC.setScreen(null);
    }

    private void save() throws IOException {
        if (rawModified) throw new IllegalArgumentException("Apply or discard the JSON buffer before saving.");
        if (workspace == null) throw new IllegalArgumentException("Open a workspace first.");
        packFiles.apply(workspace);
        packBuild.load(workspace);
        packBuild.apply();
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
        if (document != next) {
            EditorLiveQuestTest.stop();
            EditorPlaytestPresentation.clear();
            AuthoringDialoguePreview.stop();
            EditorCameraPanel.close();
            clearCampaignPreview();
        }
        playtestVisuals = Map.of();
        playtestStepRemoved = false;
        document = next;
        questPreview = null;
        fitGraph = true;
        historyDocument = next;
        selectedNode = "";
        nodeSelection.clear();
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
        if (next != null) packFiles.select(next.resourcePath());
        refresh();
    }

    private void changeHistory(boolean redo) {
        AuthoringDocument history = historyDocument == null ? document : historyDocument;
        boolean changed = redo ? history.redo() : history.undo();
        if (!changed) throw new IllegalStateException(redo ? "No change can be redone." : "No change can be undone.");
        refresh();
    }

    private void refresh() {
        packFiles.invalidate();
        packBuild.invalidate();
        propertySchema = document == null ? null : EditorSchema.forDocument(document);
        rawModified = false;
        pendingFields.clear();
        translatedFields.clear();
        fields.clear();
        textFields.clear();
        graphText.clear();
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
        if (document != null) {
            jsonText.set(JSON.toJson(document.json()));
            try {
                var ids = DialogueGraph.nodes(document).stream().map(DialogueGraph.Node::id).collect(java.util.stream.Collectors.toSet());
                nodeSelection.retainAll(ids);
                if (!selectedNode.isEmpty() && !ids.contains(selectedNode)) {
                    playtestStepRemoved = view == 3;
                    var entry = DialogueGraph.nodes(document).stream().filter(DialogueGraph.Node::entry).findFirst();
                    selectedNode = entry.map(DialogueGraph.Node::id).orElse("");
                    pointer.set(entry.map(DialogueGraph.Node::pointer).orElse(""));
                    if (playtestStepRemoved) status = "Selected step was removed. Entry selected. Replay explicitly to continue.";
                }
            } catch (IllegalArgumentException | IllegalStateException | ClassCastException | UnsupportedOperationException exception) {
                nodeSelection.clear();
            }
        }
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
            if (showRecovery) { closeEditor(); return; }
            if (workspace != null && (workspace.dirty() || hasPendingFields() || rawModified)) confirmClose = true;
            else closeEditor();
        }
    }
}
