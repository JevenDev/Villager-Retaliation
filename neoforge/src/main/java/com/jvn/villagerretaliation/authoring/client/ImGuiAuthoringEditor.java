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
import imgui.flag.ImGuiHoveredFlags;
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
    private final EditorNavigation navigation = new EditorNavigation();
    private final EditorOpenFiles openFiles = new EditorOpenFiles();
    private final EditorSession sessions = new EditorSession(MC.gameDirectory.toPath().resolve("config/authoring-editor-sessions.json"));
    private final EditorCommands commandPalette = new EditorCommands();
    private final EditorShortcuts shortcuts = new EditorShortcuts();
    private String focusMode = "";
    private float focusX, focusY, focusWidth, focusHeight;
    private float detailsScroll, restoreDetailsScroll = Float.NaN;
    private boolean snapGrid, fitSelection, boxSelecting, boxAdditive;
    private float boxX, boxY;
    private String fieldFocus = "";
    private boolean requestReferences;
    private String referenceTitle = "";
    private List<EditorReferences.Location> referenceResults = List.of();
    private final EditorResourcePicker resourcePicker = new EditorResourcePicker();
    private final EditorLibrary library = new EditorLibrary(MC.gameDirectory.toPath().resolve("config/authoring-editor-library.json"));
    private final EditorSnippets snippets = new EditorSnippets();
    private final EditorBulkEdit bulkEdit = new EditorBulkEdit();
    private final EditorChanges changes = new EditorChanges();
    private final EditorCoverage coverage = new EditorCoverage();
    private java.util.function.Consumer<com.jvn.villagerretaliation.authoring.preview.PreviewCoverage> simulationCoverage = ignored -> { };
    private boolean requestQuickOpen;
    private final ImString quickQuery = new ImString(256);
    private int quickIndex;
    private List<EditorCommands.Command> frameCommands;
    private String searchQuery = "", searchLocale = "";
    private boolean searchWording;
    private List<EditorSearch.Hit> searchHits = List.of();
    private float workScroll, restoreWorkScroll = Float.NaN;
    private boolean showBranch;
    private boolean fitGraph = true;
    private boolean wireDragMoved;
    private boolean nodeDragMoved;
    private EditorGraphSizing.Drag nodeResize;
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
    private final EditorDialoguePool dialoguePool = new EditorDialoguePool();
    private final EditorResourceView resourceView = new EditorResourceView();
    private final EditorGraphText graphText = new EditorGraphText();
    private final EditorDocumentCache documentCache = new EditorDocumentCache();
    private EditorGraphModel graphModel = new EditorGraphModel();
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
    private final EditorScenarioTests scenarioTests = new EditorScenarioTests();
    private final EditorScreen screen = new EditorScreen();
    private AuthoringWorkspace workspace;
    private AuthoringDocument document;
    private DefinitionKind createKind = DefinitionKind.TREE;
    private List<AuthoringDiagnostic> diagnostics = List.of();
    private boolean diagnosticErrors = true, diagnosticWarnings = true;
    private final ImString diagnosticFilter = new ImString(256);
    private AuthoringDiagnostic activeDiagnostic;
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
    private boolean requestNodeDelete;
    private AuthoringDocument deleteDocument;
    private java.util.Set<String> deleteNodes = java.util.Set.of();
    private String deleteRedirect = "";
    private final ImString nodeFilter = new ImString(256);
    private boolean focusSearch;
    private String graphFocus = "";
    private final java.util.Set<String> nodeSelection = new java.util.LinkedHashSet<>();
    private final EditorGraphClipboard graphClipboard = new EditorGraphClipboard();
    private final EditorGraphOrganization graphOrganization = new EditorGraphOrganization();
    private String contextNode = "", contextOutput = "";
    private float contextX, contextY;
    private final ImString graphSnippetName = new ImString(256);
    private final ImString replaceText = new ImString(1024);
    private final ImString replaceFind = new ImString(1024);
    private EditorWordingReplacement.Preview replacementPreview;
    private final EditorWorkspaceReplacement workspaceReplacement = new EditorWorkspaceReplacement();
    private boolean showRecovery;
    private boolean confirmClose;
    private long nextRecovery;
    private Entity target;
    private java.util.concurrent.CompletableFuture<EditorWorldContent.Selection> worldSelection;
    private java.util.concurrent.CompletableFuture<EditorWorldContent.Prepared> worldContent;
    private java.util.concurrent.CompletableFuture<EditorWorldContent.Source> worldContentSource;
    private volatile String worldLoadStatus = "";
    private EditorContentBrowser contentBrowser = new EditorContentBrowser();
    private EditorValidationCache validationCache = new EditorValidationCache();
    private boolean revealBrowserSelection;
    private net.minecraft.server.MinecraftServer contentServer;
    private net.minecraft.server.MinecraftServer loadingServer;

    static void initialize() {
        ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(INSTANCE::render);
    }

    static void open() {
        if (MC.player == null || MC.level == null) return;
        if (MC.hitResult instanceof EntityHitResult hit) INSTANCE.target = hit.getEntity();
        if (INSTANCE.workspace == null && !INSTANCE.preferences.workspace().isBlank())
            INSTANCE.runOperation(() -> INSTANCE.openWorkspace(false));
        INSTANCE.refreshWorldSelection();
        MC.setScreen(INSTANCE.screen);
    }

    static void tick() {
        INSTANCE.finishWorldContent();
        if (INSTANCE.worldContent != null) return;
        EditorTestWorld.tick(INSTANCE.workspace);
        if (INSTANCE.workspace != null && !INSTANCE.showRecovery && net.minecraft.Util.getMillis() >= INSTANCE.nextRecovery) {
            INSTANCE.nextRecovery = net.minecraft.Util.getMillis() + 10_000;
            INSTANCE.attempt(INSTANCE::writeRecovery);
        }
        if (INSTANCE.questPreview != null) INSTANCE.simulationCoverage.accept(INSTANCE.questPreview.drainCoverage());
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
        if (INSTANCE.worldContentSource != null)
            INSTANCE.worldContentSource.completeExceptionally(new IllegalStateException("World closed before the import started"));
        if ((INSTANCE.worldContent == null || INSTANCE.worldContent.isDone()) && !INSTANCE.showRecovery) INSTANCE.attempt(INSTANCE::writeRecovery);
        AuthoringDialoguePreview.stop();
        EditorPlaytestViewport.close();
        EditorCameraPanel.close();
        EditorLiveQuestTest.stop();
        EditorPlaytestPresentation.clear();
        INSTANCE.clearCampaignPreview();
        EditorTestWorld.disconnect();
        INSTANCE.target = null;
        INSTANCE.nodeResize = null;
        INSTANCE.worldSelection = null;
        INSTANCE.contentServer = null;
        if (MC.screen == INSTANCE.screen) MC.setScreen(null);
    }

    private ImGuiAuthoringEditor() {
        EditorLiveQuestTest.coverage = coverage::live;
        Path root = MC.gameDirectory.toPath().resolve("authoring");
        workspacePath.set(root.resolve("workspaces/default").toAbsolutePath().toString());
        exportPath.set(root.resolve("exports/pack").toAbsolutePath().toString());
        importPath.set(root.toAbsolutePath().toString());
        try { library.load(); }
        catch (IOException exception) { status = exception.getMessage(); }
        try {
            preferences = EditorPreferences.load(preferencesPath);
            guideStep = preferences.guideDismissed() ? -1 : 0;
            if (!preferences.workspace().isBlank()) workspacePath.set(preferences.workspace());
            if (!preferences.exportFolder().isBlank()) exportPath.set(preferences.exportFolder());
            locale.set(preferences.locale());
            playtestFraction = preferences.playtestFraction();
            var views = preferences.views();
            showMinimap = views.minimap(); filesMode = views.filesMode(); groupContent = views.groupContent();
            showResources = views.showResources();
            contentKind = views.contentKind().equals("All") || java.util.Arrays.stream(DefinitionKind.values()).anyMatch(kind -> kindLabel(kind).equals(views.contentKind()))
                    ? views.contentKind() : "All";
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
        if (worldContent == null && !playtesting && escapeReady && !popupWasOpen && ImGui.isKeyPressed(ImGuiKey.Escape) && !ImGui.getIO().getWantTextInput()) {
            if (nodeResize != null) { nodeResize = null; return; }
            if (!selectedEdge.isEmpty()) {
                selectedEdge = "";
                wireDragMoved = false;
            } else if (!focusMode.isEmpty()) focusMode = "";
            else screen.onClose();
            return;
        }
        if (worldContent == null) recordLocation();
        rendering = true;
        frameCommands = null;
        float fontScale = ImGui.getStyle().getFontScaleMain();
        ImGui.getStyle().setFontScaleMain(fontScale * preferences.textScale());
        pushEditorStyle();
        try {
            if (worldContent != null) { loadingWorldContent(); return; }
            if (MC.screen == screen && !showRecovery && !confirmClose && !popupWasOpen && !EditorTestWorld.applying()) {
                if (shortcuts.pressed()) shortcuts.dispatch(view == 3 ? commands().stream().filter(command -> List.of("Save drafts", "Focus work area").contains(command.id())).toList() : commands(),
                        library, this::operation, ImGui.getIO().getWantTextInput() || ImGui.isAnyItemActive());
                if (ImGui.isMouseClicked(3)) operation(() -> navigateHistory(-1));
                if (ImGui.isMouseClicked(4)) operation(() -> navigateHistory(1));
            }
            if (view == 3) { playtest(); return; }
            shell();
            ImGui.beginDisabled(showRecovery || confirmClose || worldContent != null);
            if (focusMode.isEmpty() || focusMode.equals(CONTENT_PANEL)) browser();
            if (focusMode.isEmpty() || focusMode.equals(WORK_PANEL)) {
                if (view == 0) graph();
                else if (view == 1) preview();
                else if (view == 4) files();
                else diagnostics();
            }
            if (focusMode.isEmpty() || focusMode.equals(DETAIL_PANEL)) inspector();
            quickOpen();
            if (commandPalette.active()) commandPalette.render(shortcuts.effective(commands(), library), this::operation, preferences.textScale());
            referencePopup();
            nodeDeletePopup();
            shortcuts.popup(this::commands, library, this::operation, preferences.textScale());
            ImGui.endDisabled();
            popupWasOpen = ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopup);
        } finally {
            ImGui.popStyleColor(14);
            ImGui.popStyleVar(5);
            ImGui.getStyle().setFontScaleMain(fontScale);
            rendering = false;
            while (!operations.isEmpty()) runOperation(operations.removeFirst());
            if (worldContent == null) recordLocation();
        }
    }

    private void pushEditorStyle() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 14, 14);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8, 6);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8, 8);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 3);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.10F, 0.10F, 0.11F, 1);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, 0.10F, 0.10F, 0.11F, 1);
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
        ImGui.setWindowFontScale(1);
        toolbar();
        recoveryControls();
        guide();
        if (!focusMode.isEmpty()) ImGui.textWrapped(status);
        ImGui.separator();
        focusX = ImGui.getCursorScreenPosX(); focusY = ImGui.getCursorScreenPosY();
        focusWidth = ImGui.getContentRegionAvailX(); focusHeight = ImGui.getContentRegionAvailY();
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
        ImGui.beginDisabled(showRecovery || confirmClose || worldContent != null);
        if (ImGui.button("Workspace")) showWorkspace = !showWorkspace;
        sameLineFor("New");
        ImGui.beginDisabled(workspace == null);
        if (ImGui.button("New")) operation(() -> { chooseCreateScope(); showCreate = !showCreate; view = 0; });
        sameLineFor("Save");
        if (ImGui.button("Save")) operation(this::save);
        ImGui.endDisabled();
        sameLineFor("Edit");
        if (viewButton("Edit", 0)) operation(() -> { view = 0; focusPanel = WORK_PANEL; });
        sameLineFor("Preview");
        if (viewButton("Preview", 1)) operation(() -> { view = 1; focusPanel = WORK_PANEL; });
        sameLineFor("Playtest");
        if (viewButton("Playtest", 3)) operation(this::enterPlaytest);
        sameLineFor("Build pack");
        if (viewButton("Build pack", 2)) operation(() -> { validate(); view = 2; focusPanel = WORK_PANEL; });
        sameLineFor("Tools");
        if (ImGui.button("Tools")) ImGui.openPopup("Editor tools");
        if (ImGui.beginPopup("Editor tools")) {
            if (ImGui.menuItem(guideStep < 0 ? "Quick guide" : "Hide guide")) {
                if (guideStep < 0) guideStep = 0;
                else dismissGuide();
            }
            ImGui.setItemDefaultFocus();
            if (ImGui.menuItem("Keyboard shortcuts")) shortcuts.open();
            if (ImGui.menuItem("Pack files")) operation(() -> { filesMode = true; saveViewPreferences(); view = 4; focusPanel = WORK_PANEL; });
            if (ImGui.menuItem("Find an action", keyHint("Open commands"))) commandPalette.open();
            ImGui.separator();
            if (ImGui.menuItem("Restore all panes", "", focusMode.isEmpty())) focusMode = "";
            for (String title : List.of(WORK_PANEL, DETAIL_PANEL, CONTENT_PANEL))
                if (ImGui.menuItem("Focus " + title.substring(0, title.indexOf("###")).toLowerCase(Locale.ROOT), "", focusMode.equals(title))) focusMode = title;
            if (ImGui.menuItem("Reset layout")) resetLayout = true;
            ImGui.endPopup();
        }
        sameLineFor("Close");
        if (ImGui.button("Close")) screen.onClose();
        navigationControls();
        breadcrumbs();
        ImGui.endDisabled();
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
        EditorWidgets.sameLineFor("Show me");
        if (ImGui.smallButton("Show me")) operation(() -> {
            if (guideStep == 0) showWorkspace = true;
            if (guideStep == 1) showCreate = true;
            view = guideStep < 3 ? 0 : guideStep == 3 ? 1 : 2;
            focusPanel = guideStep < 2 ? CONTENT_PANEL : guideStep == 2 ? DETAIL_PANEL : WORK_PANEL;
        });
        EditorWidgets.sameLineFor(guideStep == titles.length - 1 ? "Finish" : "Next");
        if (ImGui.smallButton(guideStep == titles.length - 1 ? "Finish" : "Next")) {
            if (guideStep == titles.length - 1) dismissGuide();
            else guideStep++;
        }
        EditorWidgets.sameLineFor("Skip tutorial");
        if (ImGui.smallButton("Skip tutorial")) dismissGuide();
    }

    private void dismissGuide() {
        guideStep = -1;
        preferences = preferences.withGuide(true);
        attempt(this::savePreferences);
    }

    private void savePreferences() throws IOException { preferences.save(preferencesPath); }

    private void saveViewPreferences() {
        preferences = preferences.withViews(new EditorPreferences.ViewSettings(showMinimap, filesMode, groupContent, showResources, contentKind));
        attempt(this::savePreferences);
    }

    private void panel(String title) {
        if (title.equals(focusPanel)) { ImGui.setNextWindowFocus(); focusPanel = ""; }
        if (focusMode.equals(title)) {
            ImGui.setNextWindowPos(focusX, focusY); ImGui.setNextWindowSize(focusWidth, focusHeight);
            ImGui.begin(title.substring(0, title.indexOf("###")) + "###VR_Focused", ImGuiWindowFlags.NoCollapse
                    | ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize);
        } else ImGui.begin(title, ImGuiWindowFlags.NoCollapse);
        if (title.equals(WORK_PANEL)) {
            if (!Float.isNaN(restoreWorkScroll)) { ImGui.setScrollY(restoreWorkScroll); restoreWorkScroll = Float.NaN; }
            workScroll = ImGui.getScrollY();
        }
        if (title.equals(DETAIL_PANEL)) {
            if (!Float.isNaN(restoreDetailsScroll)) { ImGui.setScrollY(restoreDetailsScroll); restoreDetailsScroll = Float.NaN; }
            detailsScroll = ImGui.getScrollY();
        }
        ImGui.setWindowFontScale(1);
    }

    private static void input(String label, ImString value) {
        ImGui.textUnformatted(label);
        boolean browse = label.toLowerCase(Locale.ROOT).contains("folder") || label.toLowerCase(Locale.ROOT).contains("path");
        ImGui.setNextItemWidth(browse ? EditorWidgets.inputWidth("Browse") : -1);
        ImGui.inputText("##" + label, value);
        if (browse) {
            sameLineFor("Browse");
            if (ImGui.button("Browse##" + label)) EditorFileDialog.choose(label, value.get(), selected -> MC.execute(() -> value.set(selected)));
        }
    }

    private static String kindLabel(DefinitionKind kind) { return EditorContentBrowser.kindLabel(kind); }

    private void openWorkspace(boolean create) throws IOException {
        if (workspace != null) { saveSession(); workspace.save(); }
        AuthoringWorkspace next = create ? AuthoringWorkspace.create(Path.of(workspacePath.get()))
                : AuthoringWorkspace.open(Path.of(workspacePath.get()));
        installWorkspace(next, create);
    }

    private void installWorkspace(AuthoringWorkspace next, boolean create) throws IOException {
        workspace = next;
        documentCache.clear();
        graphModel.clear();
        packFiles.reset();
        packBuild.load(next);
        createScope = null;
        searchQuery = ""; searchHits = List.of(); searchWording = false;
        activeDiagnostic = null; replacementPreview = null;
        select(null);
        showWorkspace = false;
        view = 0;
        filter.set("");
        showCreate = workspace.documents().isEmpty();
        preferences = preferences.withWorkspace(workspace.directory().toString());
        savePreferences();
        showRecovery = !create && workspace.recoveryAvailable();
        restoreSession(!showRecovery);
        if (guideStep == 0) guideStep = 1;
        status = create ? "Workspace created." : "Workspace opened.";
    }

    private void refreshWorldSelection() {
        var server = MC.getSingleplayerServer();
        contentServer = server;
        worldSelection = server == null ? null : server.submit(() -> EditorWorldContent.source(server))
                .thenApplyAsync(EditorWorldContent::identify, net.minecraft.Util.backgroundExecutor());
    }

    private void currentWorld() {
        if (!ImGui.collapsingHeader("Current world", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) return;
        if (contentServer == null || contentServer != MC.getSingleplayerServer()) {
            ImGui.textWrapped("Current world content is available in singleplayer. For a remote server, import its datapack from a folder or ZIP.");
            return;
        }
        if (worldSelection == null || !worldSelection.isDone()) {
            ImGui.textDisabled("Identifying campaign and addons...");
            return;
        }
        EditorWorldContent.Selection selected;
        try { selected = worldSelection.join(); }
        catch (java.util.concurrent.CompletionException exception) {
            ImGui.textWrapped("Could not identify current world content: " + exception.getCause().getMessage());
            if (ImGui.smallButton("Retry current world")) refreshWorldSelection();
            return;
        }
        ImGui.textWrapped("Campaign: " + selected.campaign());
        ImGui.textWrapped("Default content: " + (selected.defaults() ? "included" : "excluded"));
        if (selected.addons().isEmpty()) ImGui.textDisabled("Addons: none");
        else {
            ImGui.textUnformatted("Addons (low to high priority):");
            for (String addon : selected.addons()) ImGui.bulletText(addon);
        }
        ImGui.beginDisabled(worldContent != null);
        if (ImGui.button(worldContent == null ? "Load current world content" : "Loading current world content...", -1, 0))
            operation(this::loadWorldContent);
        ImGui.endDisabled();
        ImGui.textWrapped("Copies loaded campaign, addon and shared definitions into a new draft workspace.");
        ImGui.separator();
    }

    private void loadWorldContent() throws IOException {
        if (worldContent != null) return;
        if (contentServer == null || contentServer != MC.getSingleplayerServer())
            throw new IllegalStateException("The current world is no longer available");
        if (showRecovery) throw new IllegalStateException("Resolve workspace recovery before loading world content");
        if (workspace != null) {
            packFiles.apply(workspace);
            packBuild.load(workspace);
            packBuild.apply();
            saveSession();
        }
        var previous = workspace;
        var parent = MC.gameDirectory.toPath().resolve("authoring/workspaces");
        String language = locale.get();
        worldLoadStatus = "Preparing selected datapacks";
        var server = contentServer;
        loadingServer = server;
        worldContentSource = server.submit(() -> EditorWorldContent.source(server));
        worldContent = worldContentSource.thenApplyAsync(
                source -> EditorWorldContent.prepare(source, parent, previous, language, stage -> worldLoadStatus = stage), net.minecraft.Util.backgroundExecutor());
        status = "Loading current world content...";
    }

    private void finishWorldContent() {
        if (worldContent == null || !worldContent.isDone()) return;
        var pending = worldContent;
        var server = loadingServer;
        worldContent = null;
        worldContentSource = null;
        loadingServer = null;
        if (server != MC.getSingleplayerServer() || server == null) return;
        operation(() -> {
            EditorWorldContent.Prepared prepared;
            try { prepared = pending.join(); }
            catch (java.util.concurrent.CompletionException exception) {
                throw new IOException("Could not load current world content: " + exception.getCause().getMessage(), exception.getCause());
            }
            contentBrowser = prepared.browser();
            validationCache = prepared.validation();
            workspacePath.set(prepared.workspace().directory().toString());
            installWorkspace(prepared.workspace(), false);
            filesMode = false;
            groupContent = true;
            showResources = true;
            contentKind = "All";
            saveViewPreferences();
            showWorkspace = false;
            graphModel = prepared.graph();
            select(EditorWorldContent.firstDocument(workspace));
            status = "Loaded " + workspace.documents().size() + " definitions from " + prepared.selection().campaign() + ".";
        });
    }

    private void browser() {
        panel(filesMode ? "Pack files###VR_Content" : CONTENT_PANEL);
        if (workspace == null || showWorkspace) {
            currentWorld();
            ImGui.textUnformatted("Workspace");
            ImGui.textWrapped("Keep drafts in a folder, separate from your active game.");
            input("Workspace folder", workspacePath);
            if (ImGui.button("Open folder")) operation(() -> openWorkspace(false));
            EditorWidgets.sameLineFor("Create new");
            if (ImGui.button("Create new")) operation(() -> openWorkspace(true));
            ImGui.textWrapped("Create needs an empty folder. Open loads an existing workspace.");
            if (!preferences.recentWorkspaces().isEmpty() && ImGui.beginCombo(EditorWidgets.field("Recent workspaces"), "Choose a workspace")) {
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
        float modeWidth = ImGui.calcTextSize("Pack files").x + ImGui.getFrameHeight() + 2 * ImGui.getStyle().getFramePaddingX();
        float newWidth = Math.max(ImGui.calcTextSize("New content").x + 16, ImGui.getContentRegionAvailX() - modeWidth - 8);
        if (ImGui.button("New content", newWidth, 0)) operation(() -> {
            showCreate = !showCreate;
            view = 0;
            chooseCreateScope();
        });
        EditorWidgets.sameLineFor(modeWidth);
        ImGui.setNextItemWidth(Math.min(modeWidth, ImGui.getContentRegionAvailX()));
        if (ImGui.beginCombo("##explorer_view", filesMode ? "Pack files" : "Content")) {
            if (ImGui.selectable("Content", !filesMode)) { filesMode = false; saveViewPreferences(); }
            if (ImGui.selectable("Pack files", filesMode)) { filesMode = true; saveViewPreferences(); }
            ImGui.endCombo();
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
                resourcePicker.render("linked_quest", "Choose quest...", linkedQuest.get(),
                        workspace.documents().stream().filter(candidate -> candidate.kind() == DefinitionKind.QUEST).map(AuthoringDocument::id).sorted().toList(), linkedQuest::set, library, this::attempt);
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
        ImGui.setNextItemWidth(EditorWidgets.inputWidth("Clear"));
        ImGui.inputTextWithHint("##search", "Find content or wording...", filter);
        if (ImGui.isItemHovered()) ImGui.setTooltip("Find content: " + keyHint("Find content") + ". Quick open: " + keyHint("Quick open file") + ".");
        sameLineFor("Clear");
        ImGui.beginDisabled(filter.get().isEmpty());
        if (ImGui.button("Clear##search")) { filter.set(""); focusSearch = true; }
        ImGui.endDisabled();
        if (!filter.get().isBlank()) {
            boolean library = !filesMode && workspace.documents().size() > 30;
            if (library && ImGui.checkbox("Show matching lines", searchWording)) searchWording = !searchWording;
            if (!library || searchWording) contextualSearch();
        }
        if (filesMode) {
            ImGui.textDisabled("Paths in the built pack");
            ImGui.beginChild("##pack_explorer", 0, Math.max(180, ImGui.getContentRegionAvailY() - 150));
            EditorExplorer.begin(false);
            packFiles.renderTree(workspace, filter.get(), this::openPackFile, path -> { openPackFile(path); view = 4; }, this::operation, this::refresh);
            EditorExplorer.end();
            ImGui.endChild();
        } else {
            if (workspace.documents().size() <= 30 && ImGui.checkbox("Supporting files", showResources)) { showResources = !showResources; saveViewPreferences(); }
            contentBrowser();
        }
        ImGui.separator();
        if (EditorWidgets.section("Import existing pack")) {
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
        if (document != null && EditorWidgets.section("Manage selected file")) {
            ImGui.textWrapped(document.resourcePath());
            if (document.kind().campaignManifest()) ImGui.textWrapped("Rename, duplicate and delete include all content and assets in this folder.");
            input("New resource ID", newId);
            if (ImGui.button("Rename")) operation(() -> { workspace.rename(document, newId.get()); refresh(); });
            EditorWidgets.sameLineFor("Duplicate");
            if (ImGui.button("Duplicate")) operation(() -> select(workspace.duplicate(document, newId.get())));
            if (ImGui.button("Delete...")) confirmDelete = true;
            if (confirmDelete) {
                ImGui.textWrapped("Move this draft to Deleted files? You can restore it later.");
                if (ImGui.button("Confirm delete")) operation(() -> { workspace.remove(document); select(null); });
                EditorWidgets.sameLineFor("Cancel");
                if (ImGui.button("Cancel")) confirmDelete = false;
            }
        }
        contentTools();
        if (!workspace.deletedNames().isEmpty() && EditorWidgets.section("Deleted files")) {
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

    private void loadingWorldContent() {
        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY());
        ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY());
        ImGui.begin("Authoring###VR_AuthoringShell", ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoDocking
                | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoSavedSettings);
        ImGui.dockSpace(ImGui.getID("VR_AuthoringDock"), 0, 0, imgui.flag.ImGuiDockNodeFlags.KeepAliveOnly);
        ImGui.end();
        ImGui.setNextWindowPos(viewport.getWorkPosX() + viewport.getWorkSizeX() / 2, viewport.getWorkPosY() + viewport.getWorkSizeY() / 2,
                ImGuiCond.Always, .5f, .5f);
        ImGui.setNextWindowSize(Math.min(480 * preferences.textScale(), viewport.getWorkSizeX() - 28), 0, ImGuiCond.Always);
        ImGui.begin("Loading current world###VR_Loading", ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoMove);
        ImGui.textWrapped(worldLoadStatus);
        ImGui.textDisabled("The editor will open when the content is ready.");
        ImGui.end();
    }

    private void contentBrowser() {
        var index = contentBrowser.get(workspace, locale.get());
        boolean library = index.entries().size() > 30;
        if (library) {
            float right = ImGui.getCursorPosX() + ImGui.getContentRegionAvailX();
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled(index.entries().size() + " files");
            ImGui.sameLine(Math.max(ImGui.getCursorPosX(), right - ImGui.getFrameHeight() * 3));
            ImGui.beginDisabled(document == null);
            if (EditorExplorer.action("reveal", "Reveal selected file")) {
                filter.set(""); contentKind = "All";
                if (document.kind() == DefinitionKind.RESOURCE) showResources = true;
                contentBrowser.reveal(document); revealBrowserSelection = true;
            }
            ImGui.endDisabled();
            ImGui.sameLine(0, 0);
            if (EditorExplorer.action("collapse", "Collapse all folders")) contentBrowser.collapse();
            ImGui.sameLine(0, 0);
            if (EditorExplorer.action("options", "Explorer options")) ImGui.openPopup("explorer_options");
            if (ImGui.beginPopup("explorer_options")) {
                if (ImGui.menuItem("Group by campaign and type", "", groupContent)) { groupContent = !groupContent; saveViewPreferences(); }
                if (ImGui.menuItem("Show supporting files", "", showResources)) { showResources = !showResources; saveViewPreferences(); }
                ImGui.endPopup();
            }
            ImGui.separator();
            ImGui.beginChild("##content_library", 0, Math.max(180, ImGui.getContentRegionAvailY() - 150 - ImGui.getTextLineHeightWithSpacing()));
        }
        EditorExplorer.begin(true);
        var listing = contentBrowser.rows(workspace, locale.get(), filter.get(), showResources, contentKind, groupContent, document);
        var rows = listing.rows();
        float height = ImGui.getTextLineHeightWithSpacing();
        if (revealBrowserSelection) {
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).entry() != null && rows.get(i).entry().document() == document)
                ImGui.setScrollY(i * height);
            revealBrowserSelection = false;
        }
        float left = ImGui.getCursorPosX();
        var clipper = new imgui.ImGuiListClipper();
        try {
            clipper.begin(rows.size(), height);
            while (clipper.step()) for (int i = clipper.getDisplayStart(); i < clipper.getDisplayEnd(); i++) {
                var row = rows.get(i);
                float screenLeft = ImGui.getCursorScreenPosX() - ImGui.getCursorPosX() + left;
                EditorExplorer.guides(screenLeft, row.depth());
                ImGui.setCursorPosX(left + row.depth() * ImGui.getFontSize() * .8f);
                if (row.entry() == null) {
                    ImGui.setNextItemOpen(contentBrowser.expanded(row.key()), ImGuiCond.Always);
                    boolean open = EditorExplorer.folder(row.key(), row.label(), Integer.toString(row.count()), imgui.flag.ImGuiTreeNodeFlags.NoTreePushOnOpen);
                    if (open != contentBrowser.expanded(row.key())) contentBrowser.toggle(row.key());
                    if (ImGui.isItemHovered()) ImGui.setTooltip(row.label() + " (" + row.count() + " files)");
                } else {
                    var entry = row.entry();
                    var candidate = entry.document();
                    if (EditorExplorer.file("content:" + entry.path(), entry.title(), document == candidate, candidate.dirty(), candidate.kind()))
                        operation(() -> { select(candidate); view = 0; });
                    if (ImGui.isItemHovered()) ImGui.setTooltip(entry.title() + "\n" + kindLabel(candidate.kind()) + "\n" + entry.id() + "\n" + entry.path());
                }
            }
            clipper.end();
        } finally { clipper.destroy(); }
        ImGui.setCursorPosX(left);
        if (library) ImGui.dummy(0, 0);
        if (rows.isEmpty() && !showCreate) ImGui.textWrapped(filter.get().isBlank()
                ? "No content in this view. Choose New content or include supporting files." : "No matches. Try a title, resource ID or phrase.");
        EditorExplorer.end();
        if (library) {
            ImGui.endChild();
            if (!filter.get().isBlank()) ImGui.textDisabled(listing.matches() + " matching files");
            else ImGui.textDisabled(index.quests() + " quests / " + index.dialogue() + " dialogue");
        }
    }

    private void contentTools() {
        if (!EditorWidgets.section("Content tools")) return;
        if (ImGui.checkbox("Group content", groupContent)) { groupContent = !groupContent; saveViewPreferences(); }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Group content by campaign, addon and type.");
        float[] scale = {preferences.textScale()};
        if (ImGui.sliderFloat(EditorWidgets.field("Editor text size"), scale, .8f, 2)) operation(() -> { preferences = preferences.withScale(scale[0]); savePreferences(); });
        graphTools();
        shortcuts.render(commands(), library, this::operation);
        if (document != null) coverage.render(document, id -> operation(() -> {
            selectedNode = id; graphFocus = id; view = 0; nodeSelection.clear(); nodeSelection.add(id);
            setPointer(documentCache.nodes(document).stream().filter(node -> node.id().equals(id)).findFirst().orElseThrow().pointer());
        }));
        input("Editing language", locale);
        if (ImGui.button("Use language")) operation(() -> {
            locale.set(locale.get().strip().toLowerCase(Locale.ROOT));
            if (!locale.get().matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Use a language code such as en_us");
            preferences = preferences.withLocale(locale.get()); savePreferences(); refresh();
        });
        if (ImGui.beginCombo(EditorWidgets.field("Content type"), contentKind)) {
            if (ImGui.selectable("All", contentKind.equals("All"))) { contentKind = "All"; saveViewPreferences(); }
            for (var kind : DefinitionKind.values()) if (ImGui.selectable(kindLabel(kind), contentKind.equals(kindLabel(kind)))) { contentKind = kindLabel(kind); saveViewPreferences(); }
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
        if (ImGui.button("Quick open (" + keyHint("Quick open file") + ")")) requestQuickOpen = true;
        if (ImGui.button("Commands (" + keyHint("Open commands") + ")")) commandPalette.open();
        openFileTools();
        historyTools();
        try { changes.render(workspace, this::operation, this::openPackFile, changed -> { if (changed != null) historyDocument = changed; refresh(); }); }
        catch (IOException exception) { ImGui.textWrapped("Could not read saved changes: " + exception.getMessage()); }
        if (document != null && ImGui.button("Find usages across workspace")) findUsages();
        replacementTools();
        workspaceReplacement.render(workspace, this::operation, changed -> { refresh(); historyDocument = changed; });
    }

    private void replacementTools() {
        if (!EditorWidgets.section("Replace wording")) return;
        input("Find wording (case sensitive)", replaceFind);
        if (!filter.get().isBlank() && ImGui.smallButton("Use content search wording")) replaceFind.set(filter.get());
        input("Replace matching wording with", replaceText);
        ImGui.beginDisabled(document == null || replaceFind.get().isBlank());
        if (ImGui.button("Preview replacements")) operation(() -> replacementPreview = EditorWordingReplacement.preview(document, replaceFind.get(), replaceText.get()));
        ImGui.endDisabled();
        var preview = replacementPreview;
        if (preview == null || preview.document() != document) return;
        boolean current = preview.find().equals(replaceFind.get()) && preview.replacement().equals(replaceText.get()) && preview.baseline().equals(documentCache.json(document));
        String summary = preview.occurrences() + (preview.occurrences() == 1 ? " replacement in " : " replacements in ")
                + preview.changes().size() + (preview.changes().size() == 1 ? " field" : " fields");
        ImGui.textWrapped(summary + " of " + document.id());
        if (!current) ImGui.textWrapped("Wording or file contents changed. Preview again before applying.");
        ImGui.beginDisabled(!current || preview.changes().isEmpty());
        if (ImGui.button("Apply replacements")) operation(() -> {
            if (document != preview.document() || !preview.find().equals(replaceFind.get()) || !preview.replacement().equals(replaceText.get()))
                throw new IllegalStateException("Preview the current wording again before applying");
            preview.apply();
            status = "Applied " + summary + ".";
            replacementPreview = null;
            refresh();
        });
        ImGui.endDisabled();
        EditorWidgets.sameLineFor("Cancel preview");
        if (ImGui.button("Cancel preview")) replacementPreview = null;
        if (preview.changes().isEmpty()) ImGui.textWrapped("No wording would change. IDs, references and behavior fields are preserved.");
        else {
            ImGui.beginChild("##replacement_preview", 0, 220, true);
            for (var change : preview.changes()) {
                ImGui.textWrapped(change.pointer() + " (" + change.occurrences() + " matches)");
                ImGui.textDisabled("Before"); ImGui.textWrapped(change.before());
                ImGui.textDisabled("After"); ImGui.textWrapped(change.after());
                ImGui.separator();
            }
            ImGui.endChild();
        }
    }

    private void contextualSearch() {
        if (!searchQuery.equals(filter.get()) || !searchLocale.equals(locale.get())) {
            searchQuery = filter.get(); searchLocale = locale.get();
            searchHits = EditorSearch.find(workspace, searchQuery, searchLocale, 200);
        }
        ImGui.setNextItemOpen(true, ImGuiCond.Once);
        if (!EditorWidgets.section("Search matches (" + searchHits.size() + (searchHits.size() == 200 ? "+" : "") + ")###search_matches")) return;
        ImGui.beginChild("##search_results", 0, Math.min(220, Math.max(1, searchHits.size()) * 76), true);
        for (int i = 0; i < searchHits.size(); i++) {
            var hit = searchHits.get(i);
            ImGui.pushID(i);
            if (ImGui.selectable(shortText(hit.document().id(), 48) + "##hit")) operation(() -> openSearchHit(hit));
            ImGui.textDisabled(hit.pointer().isEmpty() ? "File settings" : hit.pointer());
            ImGui.textWrapped(EditorSearch.snippet(hit.text(), searchQuery));
            if (ImGui.isItemHovered()) ImGui.setTooltip(hit.document().resourcePath() + hit.pointer() + "\n" + hit.text());
            ImGui.separator();
            ImGui.popID();
        }
        if (searchHits.isEmpty()) ImGui.textWrapped("No matching content or wording.");
        ImGui.endChild();
    }

    private void openSearchHit(EditorSearch.Hit hit) {
        if (!workspace.documents().contains(hit.document())) throw new IllegalStateException("This search result is no longer available");
        select(hit.document());
        var location = EditorDiagnosticLocation.resolve(document, hit.pointer());
        selectedNode = location.nodeId(); setPointer(location.pointer());
        nodeSelection.clear();
        if (!selectedNode.isEmpty()) nodeSelection.add(selectedNode);
        graphFocus = selectedNode;
        fitGraph = false;
        view = 0; focusPanel = DETAIL_PANEL;
        status = document.resourcePath() + hit.pointer();
    }

    private void quickOpen() {
        if (requestQuickOpen) {
            requestQuickOpen = false; quickQuery.set(""); quickIndex = 0;
            ImGui.openPopup("Quick open###quick_open");
        }
        if (!EditorWidgets.beginPopup("Quick open###quick_open", 640, 420, preferences.textScale())) return;
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere();
        String before = quickQuery.get();
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##quick_query", "Find a file or resource ID...", quickQuery);
        boolean enter = ImGui.isKeyPressed(ImGuiKey.Enter);
        if (!before.equals(quickQuery.get())) quickIndex = 0;
        var recent = navigation.recentFiles(workspace);
        var matches = EditorSearch.files(workspace, quickQuery.get(), recent);
        boolean moved = false;
        if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) { quickIndex++; moved = true; }
        if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) { quickIndex--; moved = true; }
        quickIndex = Math.clamp(quickIndex, 0, Math.max(0, matches.size() - 1));
        if (enter && !matches.isEmpty()) {
            String path = matches.get(quickIndex);
            operation(() -> openPackFile(path)); ImGui.closeCurrentPopup();
        }
        ImGui.textWrapped(quickQuery.get().isBlank() ? "Recent files first. Enter to open, Escape to close." : matches.size() + " matching files");
        ImGui.beginChild("##quick_files", 0, 0, false);
        for (int i = 0; i < matches.size(); i++) {
            String path = matches.get(i);
            ImGui.pushID(i);
            if (ImGui.selectable(path, i == quickIndex)) { operation(() -> openPackFile(path)); ImGui.closeCurrentPopup(); }
            if (moved && i == quickIndex) ImGui.setScrollHereY();
            ImGui.popID();
        }
        if (matches.isEmpty()) ImGui.textWrapped("No matching files.");
        ImGui.endChild();
        ImGui.endPopup();
    }

    private boolean graphDocument() {
        return document != null && document.kind() != DefinitionKind.RESOURCE && !document.kind().campaignManifest();
    }

    private List<EditorCommands.Command> commands() {
        if (rendering && frameCommands != null) return frameCommands;
        boolean graph = graphDocument() && view != 3, selected = graph && !selectedNode.isEmpty();
        AuthoringDocument history = historyDocument == null ? document : historyDocument;
        var result = new java.util.ArrayList<EditorCommands.Command>();
        result.add(new EditorCommands.Command("Save drafts", "Ctrl+S", workspace != null, this::save));
        result.add(new EditorCommands.Command("Customize keyboard shortcuts", "", view != 3, shortcuts::open));
        result.add(new EditorCommands.Command("Open commands", "Ctrl+Shift+P", view != 3, commandPalette::open));
        result.add(new EditorCommands.Command("Find content", "Ctrl+F", workspace != null && view != 3, () -> { focusMode = ""; focusSearch = true; }));
        result.add(new EditorCommands.Command("Navigate back", "Ctrl+Alt+Left", view != 3, () -> navigateHistory(-1)));
        result.add(new EditorCommands.Command("Navigate forward", "Ctrl+Alt+Right", view != 3, () -> navigateHistory(1)));
        result.add(new EditorCommands.Command("Parent folder", "Alt+Up", workspace != null && view != 3 && !activePath().isEmpty(), this::navigateUp));
        result.add(new EditorCommands.Command("Copy nodes", "Ctrl+C", graph && view == 0 && !selectedNodes().isEmpty(), () -> graphClipboard.copy(workspace, document, selectedNodes())));
        result.add(new EditorCommands.Command("Delete selected nodes", "Delete", graph && view == 0 && !selectedNodes().isEmpty(), this::requestDeleteNodes));
        result.add(new EditorCommands.Command("Paste nodes", "Ctrl+V", graph && view == 0, this::pasteNodes));
        result.add(new EditorCommands.Command("Next node", "Alt+Right", graph && view == 0, () -> navigateNode(1)));
        result.add(new EditorCommands.Command("Previous node", "Alt+Left", graph && view == 0, () -> navigateNode(-1)));
        result.add(new EditorCommands.Command("Focus work area", "Ctrl+Shift+G", true, () -> focusMode = focusMode.equals(WORK_PANEL) ? "" : WORK_PANEL));
        result.add(new EditorCommands.Command("Focus properties", "Ctrl+Shift+H", view != 3, () -> focusMode = focusMode.equals(DETAIL_PANEL) ? "" : DETAIL_PANEL));
        result.add(new EditorCommands.Command("Quick open file", "Ctrl+P", workspace != null && view != 3, () -> requestQuickOpen = true));
        result.add(new EditorCommands.Command("Next open file", "Ctrl+Tab", workspace != null && !openFiles.entries(workspace).isEmpty(), () -> cycleOpenFile(1)));
        result.add(new EditorCommands.Command("Previous open file", "Ctrl+Shift+Tab", workspace != null && !openFiles.entries(workspace).isEmpty(), () -> cycleOpenFile(-1)));
        result.add(new EditorCommands.Command("Pin or unpin current file", "", workspace != null && workspace.filePaths().contains(activePath()), () -> openFiles.pin(workspace, activePath())));
        result.add(new EditorCommands.Command("Close current file", "", workspace != null && workspace.filePaths().contains(activePath()), () -> closeOpenFile(activePath())));
        result.add(new EditorCommands.Command("Undo" + (history == null ? "" : ": " + history.undoLabel()), "Ctrl+Z", history != null && history.canUndo(), () -> changeHistory(false)));
        result.add(new EditorCommands.Command("Redo" + (history == null ? "" : ": " + history.redoLabel()), "Ctrl+Y", history != null && history.canRedo(), () -> changeHistory(true)));
        result.add(new EditorCommands.Command("Add node or stage", "", graph && (document.kind() != DefinitionKind.FORCED || documentCache.json(document).has("entries")), this::addGraphNode));
        result.add(new EditorCommands.Command("Add response or connection", "", selected, () -> { view = 0; showBranch = true; nextStage.set(""); }));
        result.add(new EditorCommands.Command("Duplicate selected node", "", selected && document.kind() != DefinitionKind.FORCED, () -> {
            DialogueGraph.duplicateNode(document, selectedNode, nextNodeId()); refresh();
        }));
        result.add(new EditorCommands.Command("Fit graph", "", graph, () -> { view = 0; fitSelection = false; fitGraph = true; }));
        result.add(new EditorCommands.Command("Frame selected nodes", "", selected, () -> { view = 0; fitSelection = true; fitGraph = true; }));
        for (String action : List.of("Align left", "Align top", "Space horizontally", "Space vertically", "Arrange selection"))
            result.add(new EditorCommands.Command(action, "", graph && selectedNodes().size() > 1, () -> alignNodes(action)));
        result.add(new EditorCommands.Command("Validate workspace", "", workspace != null, () -> { validate(); view = 2; focusPanel = WORK_PANEL; }));
        result.add(new EditorCommands.Command("Go to definition", "", document != null && !selectedDefinitions().isEmpty(), this::goToDefinition));
        result.add(new EditorCommands.Command("Find usages", "", document != null, this::findUsages));
        result.add(new EditorCommands.Command("Preview content", "", graph, () -> { view = 1; focusPanel = WORK_PANEL; }));
        result.add(new EditorCommands.Command("Playtest content", "", graph, this::enterPlaytest));
        result.add(new EditorCommands.Command("Reset editor layout", "", true, () -> resetLayout = true));
        if (rendering) frameCommands = result;
        return result;
    }

    private String keyHint(String id) {
        var overrides = library.shortcuts();
        String binding = commands().stream().filter(command -> command.id().equals(id)).findFirst()
                .map(command -> EditorShortcuts.binding(command, overrides)).orElse("");
        return binding.isEmpty() ? "unbound" : binding;
    }

    private void openFileTools() {
        if (!EditorWidgets.section("Open and pinned files")) return;
        ImGui.textWrapped("Next file: " + keyHint("Next open file") + ". Previous file: " + keyHint("Previous open file") + ".");
        if (workspace.filePaths().contains(activePath()) && ImGui.button("Pin or unpin current file")) operation(() -> openFiles.pin(workspace, activePath()));
        var listed = openFiles.entries(workspace).stream().sorted(java.util.Comparator.comparing(entry -> !entry.pinned())).toList();
        for (int i = 0; i < listed.size(); i++) {
            var entry = listed.get(i);
            String path = entry.currentPath();
            ImGui.pushID(i);
            if (ImGui.smallButton(entry.pinned() ? "Unpin" : "Pin")) operation(() -> openFiles.pin(workspace, path));
            EditorWidgets.sameLineFor("Close");
            if (ImGui.smallButton("Close")) operation(() -> closeOpenFile(path));
            if (ImGui.selectable(shortText(path, 45), path.equals(activePath()))) operation(() -> openPackFile(path));
            if (ImGui.isItemHovered()) ImGui.setTooltip(path);
            ImGui.popID();
        }
    }

    private void cycleOpenFile(int direction) {
        String next = openFiles.cycle(workspace, activePath(), direction);
        if (!next.isEmpty()) openPackFile(next);
    }

    private void closeOpenFile(String path) {
        String next = openFiles.cycle(workspace, path, 1);
        if (activePath().equals(path)) {
            if (!next.isEmpty() && !next.equals(path)) openPackFile(next);
            else { select(null); packFiles.select(""); view = 4; workScroll = 0; restoreWorkScroll = 0; }
        }
        openFiles.close(workspace, path);
        status = "Closed file. Its draft remains in the workspace.";
    }

    private void historyTools() {
        if (!EditorWidgets.section("Edit history")) return;
        AuthoringDocument history = historyDocument == null ? document : historyDocument;
        if (history == null) { ImGui.textWrapped("Select a content file to view its edit history."); return; }
        ImGui.beginDisabled(!history.canUndo());
        if (ImGui.button("Undo: " + history.undoLabel())) operation(() -> changeHistory(false));
        ImGui.endDisabled(); ImGui.beginDisabled(!history.canRedo());
        if (ImGui.button("Redo: " + history.redoLabel())) operation(() -> changeHistory(true));
        ImGui.endDisabled();
        ImGui.textDisabled("Most recent changes first");
        ImGui.beginChild("##edit_history", 0, 140, true);
        for (String label : history.undoLabels().stream().limit(20).toList()) ImGui.textWrapped(label);
        for (String label : history.redoLabels().stream().limit(10).toList()) ImGui.textDisabled("Redo: " + label);
        ImGui.endChild();
    }

    private List<EditorReferences.Location> selectedDefinitions() {
        if (document == null || workspace == null) return List.of();
        String path = EditorDiagnosticLocation.resolve(document, pointer.get()).pointer();
        JsonElement value = JsonPointers.get(documentCache.json(document), path);
        if (value.isJsonObject() && value.getAsJsonObject().has("key")) {
            var key = value.getAsJsonObject().get("key");
            if (key.isJsonPrimitive() && key.getAsJsonPrimitive().isString())
                return EditorReferences.definitions(workspace, document, path + "/key", key.getAsString(), locale.get());
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
            return EditorReferences.definitions(workspace, document, path, value.getAsString(), locale.get());
        return List.of();
    }

    private void goToDefinition() {
        var targets = selectedDefinitions();
        if (targets.size() == 1) navigateReference(targets.getFirst());
        else showReferences("Choose a definition", targets);
    }

    private void findUsages() {
        var definitions = selectedDefinitions();
        var target = definitions.isEmpty() ? new EditorReferences.Location(document, document.kind() == DefinitionKind.RESOURCE ? pointer.get() : "") : definitions.getFirst();
        showReferences("Usages of " + target.label(), EditorReferences.usages(workspace, target, locale.get()));
    }

    private void showReferences(String title, List<EditorReferences.Location> results) {
        referenceTitle = title; referenceResults = results; requestReferences = true;
    }

    private void navigateReference(EditorReferences.Location location) {
        openSearchHit(new EditorSearch.Hit(location.document(), location.pointer(), ""));
    }

    private void referenceButtons(String path, String value) {
        var targets = EditorReferences.definitions(workspace, document, path, value, locale.get());
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            ImGui.pushID("reference_" + i);
            if (ImGui.smallButton("Go to definition")) operation(() -> navigateReference(target));
            EditorWidgets.sameLineFor("Find usages");
            if (ImGui.smallButton("Find usages")) operation(() -> showReferences("Usages of " + target.label(), EditorReferences.usages(workspace, target, locale.get())));
            ImGui.popID();
        }
    }

    private void referencePopup() {
        if (requestReferences) { requestReferences = false; ImGui.openPopup("References###workspace_references"); }
        if (!EditorWidgets.beginPopup("References###workspace_references", 680, 380, preferences.textScale())) return;
        ImGui.textWrapped(referenceTitle);
        if (referenceResults.isEmpty()) ImGui.textWrapped("No matching references.");
        ImGui.beginChild("##reference_results", 0, 0, false);
        for (int i = 0; i < referenceResults.size(); i++) {
            var location = referenceResults.get(i);
            ImGui.pushID(i);
            if (ImGui.selectable(location.label())) { operation(() -> navigateReference(location)); ImGui.closeCurrentPopup(); }
            ImGui.textDisabled(location.document().resourcePath());
            ImGui.popID();
        }
        ImGui.endChild(); ImGui.endPopup();
    }

    private void addGraphNode() {
        String id = nextNodeId();
        DialogueGraph.addNode(document, id);
        var added = document.kind() == DefinitionKind.FORCED ? documentCache.nodes(document).getLast()
                : documentCache.nodes(document).stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        selectedNode = added.id(); setPointer(added.pointer()); showBranch = false;
        refresh(); nodeSelection.clear(); nodeSelection.add(selectedNode);
        view = 0; focusPanel = DETAIL_PANEL;
    }

    private void alignNodes(String action) {
        var cards = graphModel.get(workspace, document, locale.get()).cards();
        persistPositions(EditorGraphSelection.layout(cards, positions, selectedNodes(), action));
        refresh();
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
            if (dialoguePool.supports(document, documentCache.json(document))) {
                ImGui.beginDisabled(rawModified);
                dialoguePool.render(document, pointer.get(), preferences.textScale(), this::poolField,
                        path -> { setPointer(path); focusPanel = DETAIL_PANEL; }, this::operation, this::refresh);
                ImGui.endDisabled();
            } else {
                ImGui.beginDisabled(rawModified);
                resourceView.render(document, documentCache.json(document), pointer.get(), preferences.textScale(),
                        this::resourceFields, this::resourceCollectionTools,
                        path -> { setPointer(path); fields.clear(); textFields.clear(); }, this::operation);
                ImGui.endDisabled();
            }
            ImGui.end();
            return;
        }
        List<DialogueGraph.Node> nodes;
        List<EditorGraph.Card> cards;
        EditorGraphModel.Snapshot model;
        var coverageSnapshot = coverage.snapshot(document);
        try {
            model = graphModel.get(workspace, document, locale.get());
            nodes = model.nodes();
            cards = nodeResize == null ? model.cards() : model.cards().stream()
                    .map(card -> nodeResize.preview(card, ImGui.getMousePosX(), ImGui.getMousePosY())).toList();
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException
                | UnsupportedOperationException | IndexOutOfBoundsException exception) {
            ImGui.textWrapped("Repair the definition in Advanced full JSON: " + exception.getMessage());
            ImGui.end();
            return;
        }
        ImGui.textUnformatted(document.kind() == DefinitionKind.QUEST ? "Quest stages" : "Dialogue");
        ImGui.beginDisabled(document.kind() == DefinitionKind.FORCED && !documentCache.json(document).has("entries"));
        if (ImGui.button(document.kind() == DefinitionKind.QUEST ? "Add stage" : "Add node")) operation(this::addGraphNode);
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
        if (ImGui.button("Fit graph")) { fitSelection = false; fitGraph = true; }
        sameLineFor("Arrange nodes");
        if (ImGui.button("Arrange nodes")) operation(() -> {
            persistPositions(EditorGraph.arrange(cards));
            fitGraph = true;
            refresh();
        });
        sameLineFor("100%");
        boolean resetZoom = ImGui.button(Math.round(zoom * 100) + "%###graph_zoom");
        if (ImGui.isItemHovered()) ImGui.setTooltip("Reset zoom to 100%. Scroll over the graph to zoom. Middle-drag to pan.");
        sameLineFor("Go to start");
        if (ImGui.button("Go to start")) nodes.stream().filter(DialogueGraph.Node::entry).findFirst().ifPresent(node -> graphFocus = node.id());
        sameLineFor("Graph tools");
        if (ImGui.button("Graph tools")) showGraphTools = !showGraphTools;
        if (showGraphTools) graphTools();
        if (selected && showBranch) {
            if (document.kind() != DefinitionKind.QUEST) {
                ImGui.textUnformatted("Player response");
                textFields.render("new_response", responseLabel, document.kind(), false, preferences.textScale());
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
            EditorWidgets.sameLineFor("Cancel connection");
            if (ImGui.button("Cancel connection")) showBranch = false;
        }
        ImGui.textWrapped(!selectedEdge.isEmpty() ? "Choose an input dot on the left of a node. Escape cancels the connection."
                : selectedNodes().isEmpty() ? "Edit text and values inside nodes. Drag headers to move. Connect right dots to left dots."
                : selectedNodes().size() + " selected. Drag headers to move. " + keyHint("Delete selected nodes") + " to delete. " + keyHint("Undo") + " to undo.");
        if (!selectedEdge.isEmpty()) {
            if (ImGui.smallButton("Create connected node")) operation(() -> {
                selectedNode = EditorContentTools.createConnected(document, selectedEdge, nextNodeId());
                selectedEdge = ""; refresh();
                setPointer(documentCache.nodes(document).stream().filter(value -> value.id().equals(selectedNode)).findFirst().orElseThrow().pointer());
                graphFocus = selectedNode; nodeSelection.clear(); nodeSelection.add(selectedNode);
            });
            EditorWidgets.sameLineFor("Cancel connection");
            if (ImGui.smallButton("Cancel connection")) selectedEdge = "";
            boolean responseEdge = selectedEdge.contains("/responses/") || selectedEdge.contains("/options/");
            if (responseEdge) {
                EditorWidgets.sameLineFor("Delete selected response");
                if (ImGui.smallButton("Delete selected response")) operation(() -> {
                    DialogueGraph.removeResponse(document, selectedEdge);
                    selectedEdge = "";
                    refresh();
                });
            }
        } else ImGui.dummy(0, ImGui.getTextLineHeight());
        float canvasWidth = Math.max(100, ImGui.getContentRegionAvailX());
        float canvasHeight = Math.max(100, ImGui.getContentRegionAvailY());
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.beginChild("graph_view", canvasWidth, canvasHeight, false, ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        ImGui.popStyleVar();
        float canvasX = ImGui.getCursorScreenPosX();
        float canvasY = ImGui.getCursorScreenPosY();
        ImGui.setNextItemAllowOverlap();
        ImGui.invisibleButton("graph_canvas", canvasWidth, canvasHeight);
        boolean hover = ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows | ImGuiHoveredFlags.AllowWhenBlockedByActiveItem)
                && ImGui.isMouseHoveringRect(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight);
        var canvasEnd = ImGui.getCursorScreenPos();
        boolean minimapHover = showMinimap && hover && ImGui.isMouseHoveringRect(canvasX + canvasWidth - 190, canvasY + canvasHeight - 130, canvasX + canvasWidth, canvasY + canvasHeight);
        if (minimapHover) hover = false;
        model.placeMissing(positions);
        if (!graphFocus.isEmpty() && positions.containsKey(graphFocus)) {
            var point = positions.get(graphFocus);
            float width = cards.stream().filter(card -> card.node().id().equals(graphFocus)).map(EditorGraph.Card::width).findFirst().orElse(EditorGraph.WIDTH);
            panX = canvasWidth / 2 - (point[0] + width / 2) * zoom;
            panY = canvasHeight / 2 - (point[1] + EditorGraph.HEADER) * zoom;
            graphFocus = ""; fitGraph = false;
        }
        var fitCards = fitSelection ? cards.stream().filter(card -> selectedNodes().contains(card.node().id())).toList() : cards;
        if (fitGraph && !fitCards.isEmpty()) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (var card : fitCards) {
                float[] point = positions.get(card.node().id());
                minX = Math.min(minX, point[0]);
                minY = Math.min(minY, point[1]);
                maxX = Math.max(maxX, point[0] + card.width());
                maxY = Math.max(maxY, point[1] + card.height());
            }
            float padding = EditorSchema.object(documentCache.metadata(document).get("graph_groups")).isEmpty() ? 25 : 60;
            zoom = Math.clamp(Math.min((canvasWidth - padding * 2) / (maxX - minX), (canvasHeight - padding * 2) / (maxY - minY)), .35f, 1f);
            panX = padding - minX * zoom;
            panY = padding - minY * zoom;
            fitGraph = false; fitSelection = false;
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
        String foreground = nodeResize != null ? nodeResize.original().node().id() : draggingNode != null ? draggingNode : selectedNode;
        var layeredCards = EditorGraphSelection.front(cards, foreground);
        String hoveredNode = EditorGraphSelection.topmost(layeredCards, positions,
                (ImGui.getMousePosX() - canvasX - panX) / zoom, (ImGui.getMousePosY() - canvasY - panY) / zoom, Math.max(12, 10 * zoom) / zoom);
        boolean overField = layeredCards.stream().filter(card -> card.node().id().equals(hoveredNode)).anyMatch(card -> {
            float[] point = positions.get(card.node().id());
            float gx = (ImGui.getMousePosX() - canvasX - panX) / zoom - point[0];
            float gy = (ImGui.getMousePosY() - canvasY - panY) / zoom - point[1];
            for (int i = 0; i < card.blocks().size(); i++)
                if (!card.blocks().get(i).fields().isEmpty() && gx >= 10 && gx <= card.width() - 10
                        && gy >= card.blockY(i) + 28 && gy <= card.blockY(i) + card.blockHeight(i) - 6) return true;
            return false;
        });
        if (hover && nodeResize == null && !overField && !ImGui.isAnyItemActive() && ImGui.getIO().getMouseWheel() != 0) {
            float oldZoom = zoom;
            zoom = Math.clamp(zoom * (float) Math.pow(1.15, ImGui.getIO().getMouseWheel()), .35f, 2f);
            float mx = ImGui.getIO().getMousePosX() - canvasX;
            float my = ImGui.getIO().getMousePosY() - canvasY;
            panX = mx - (mx - panX) * zoom / oldZoom;
            panY = my - (my - panY) * zoom / oldZoom;
        }
        if (hover && selectedEdge.isEmpty() && draggingNode == null && nodeResize == null && ImGui.isMouseClicked(0)) {
            float mx = (ImGui.getMousePosX() - canvasX - panX) / zoom, my = (ImGui.getMousePosY() - canvasY - panY) / zoom;
            boolean overCard = cards.stream().anyMatch(card -> {
                float[] point = positions.get(card.node().id());
                return mx >= point[0] - 10 && mx <= point[0] + card.width() + 10 && my >= point[1] - 10 && my <= point[1] + card.height() + 10;
            });
            if (!overCard) { boxSelecting = true; boxAdditive = ImGui.getIO().getKeyCtrl(); boxX = mx; boxY = my; }
        }
        if (!selectedEdge.isEmpty() && ImGui.isMouseDragging(0)) wireDragMoved = true;
        ImDrawList draw = ImGui.getWindowDrawList();
        ImGui.pushClipRect(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, true);
        draw.addRectFilled(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, 0xff242321);
        graphOrganization.draw(draw, document, cards, positions, canvasX + panX, canvasY + panY, zoom);
        var highlighted = graphOrganization.highlighted(model, selectedNodes());
        for (var card : cards) {
            float[] from = positions.get(card.node().id());
            for (int i = 0; i < card.blocks().size(); i++) {
                var edge = card.blocks().get(i).output();
                if (edge == null) continue;
                float x1 = canvasX + panX + (from[0] + card.width()) * zoom;
                float y1 = canvasY + panY + (from[1] + card.outputY(i)) * zoom;
                float[] to = edge.to().isEmpty() ? null : positions.get(edge.to());
                if (to != null) {
                    float x2 = canvasX + panX + to[0] * zoom;
                    float y2 = canvasY + panY + (to[1] + EditorGraph.HEADER / 2) * zoom;
                    wire(draw, x1, y1, x2, y2, !highlighted.contains(edge.from()) || !highlighted.contains(edge.to()) ? 0xff524e46 : coverage.visible() && coverageSnapshot.chosen(edge.pointer()) ? 0xff94c697 : 0xffb2a68c);
                }
                if (edge.pointer().equals(selectedEdge))
                    wire(draw, x1, y1, ImGui.getIO().getMousePosX(), ImGui.getIO().getMousePosY(), 0xff81c9ed);
            }
        }
        for (var card : layeredCards) {
            var node = card.node();
            boolean nodeHover = hover && node.id().equals(hoveredNode);
            float[] position = positions.get(node.id());
            float x = canvasX + panX + position[0] * zoom;
            float y = canvasY + panY + position[1] * zoom;
            float w = card.width() * zoom;
            float h = card.height() * zoom;
            float margin = Math.max(12, 10 * zoom);
            if (x + w + margin < canvasX || x - margin > canvasX + canvasWidth
                    || y + h + margin < canvasY || y - margin > canvasY + canvasHeight) continue;
            ImGui.setCursorScreenPos(x - margin, y - margin);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
            // keep each card and its child text fields together in the window draw order
            boolean visible = ImGui.beginChild("graph_node:" + node.id(), w + margin * 2, h + margin * 2, false,
                    ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoBackground);
            ImGui.popStyleVar();
            if (!visible) { ImGui.endChild(); continue; }
            draw = ImGui.getWindowDrawList();
            draw.addRectFilled(x, y, x + w, y + h, 0xff39352f, 4);
            draw.addRectFilled(x, y, x + w, y + EditorGraph.HEADER * zoom,
                    selectedNode.equals(node.id()) || nodeSelection.contains(node.id()) ? 0xff65513d : 0xff494037, 4);
            boolean tested = coverage.visible() && coverageSnapshot.visited(node.pointer());
            draw.addRect(x, y, x + w, y + h, tested ? 0xff94c697 : node.entry() ? 0xff9cc6a2 : 0xff78716b, 4);
            if (tested) draw.addCircleFilled(x + w - 12 * zoom, y + h - 12 * zoom, 4 * zoom, 0xff94c697);
            graphText(draw, x + 12 * zoom, y + 10 * zoom, 0xfff2f0e9,
                    (node.entry() ? "Start: " : "") + node.id(), card.width() - 24);
            long issueCount = diagnostics.stream().filter(issue -> issue.resourcePath().equals(document.resourcePath())
                    && (issue.pointer().equals(node.pointer()) || issue.pointer().startsWith(node.pointer() + "/"))).count();
            if (issueCount > 0) draw.addText(x + w - 28, y + 10, 0xff82b9ef, "!" + issueCount);
            float inputY = y + EditorGraph.HEADER / 2 * zoom;
            boolean inputHover = nodeHover && EditorGraphHit.port(ImGui.getMousePosX(), ImGui.getMousePosY(), x, inputY, EditorGraph.HEADER * zoom);
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
            if (nodeResize == null && !inputHover && nodeHover && ImGui.isMouseHoveringRect(x, y, x + w, y + EditorGraph.HEADER * zoom)
                    && ImGui.isMouseClicked(0)) operation(() -> {
                if (ImGui.getIO().getKeyCtrl()) {
                    if (nodeSelection.isEmpty() && !selectedNode.isEmpty()) nodeSelection.add(selectedNode);
                    if (!nodeSelection.add(node.id())) nodeSelection.remove(node.id());
                } else if (!nodeSelection.contains(node.id())) { nodeSelection.clear(); nodeSelection.add(node.id()); }
                selectedNode = nodeSelection.contains(node.id()) ? node.id() : nodeSelection.stream().findFirst().orElse("");
                setPointer(nodes.stream().filter(value -> value.id().equals(selectedNode)).map(DialogueGraph.Node::pointer).findFirst().orElse(""));
                selectedEdge = "";
                showBranch = false;
                draggingNode = nodeSelection.contains(node.id()) ? node.id() : null;
                nodeDragMoved = false;
            });
            for (int i = 0; i < card.blocks().size(); i++) {
                var block = card.blocks().get(i);
                float rowY = y + card.blockY(i) * zoom;
                float rowHeight = card.blockHeight(i) * zoom;
                float centerY = y + card.outputY(i) * zoom;
                float outputX = x + w;
                boolean outputHover = block.output() != null && nodeHover
                        && EditorGraphHit.port(ImGui.getMousePosX(), ImGui.getMousePosY(), outputX, centerY, EditorGraph.ROW * zoom);
                boolean blockHover = !outputHover && nodeHover && ImGui.isMouseHoveringRect(x + 6, rowY, x + w - 6, rowY + rowHeight);
                boolean testedChoice = coverage.visible() && coverageSnapshot.chosen(block.pointer());
                int background = blockHover ? 0xff514a40 : testedChoice ? 0xff3b5540 : block.output() != null ? 0xff403c32 : 0xff35322e;
                draw.addRectFilled(x + 5 * zoom, rowY + 2 * zoom, x + w - 5 * zoom, rowY + rowHeight - 2 * zoom, background, 3);
                if (selectedNode.equals(node.id()) && pointer.get().equals(block.pointer()))
                    draw.addRect(x + 5 * zoom, rowY + 2 * zoom, x + w - 5 * zoom, rowY + rowHeight - 2 * zoom, 0xff9cc6a2, 3);
                if (!block.fields().isEmpty()) {
                    graphText(draw, x + 12 * zoom, rowY + 7 * zoom, 0xffd9d2c7,
                            block.title() + (block.styledText() == null ? ": " + block.text() : ""), card.width() - 58);
                    renderGraphFields(card, block, x, rowY);
                } else if (block.styledText() == null) {
                    graphText(draw, x + 12 * zoom, rowY + 9 * zoom, block.output() == null ? 0xffd9d2c7 : 0xffded8ad,
                            block.title() + ": " + block.text(), card.width() - 34);
                } else if (x + w > canvasX && x < canvasX + canvasWidth && rowY + rowHeight > canvasY
                        && rowY < canvasY + canvasHeight) {
                    String label = block.title() + ":";
                    float labelWidth = ImGui.calcTextSize(label).x + 4;
                    graphText(draw, x + 12 * zoom, rowY + 9 * zoom, 0xffd9d2c7, label, labelWidth);
                    graphText.draw(block, x + (12 + labelWidth) * zoom, rowY + 3 * zoom,
                            (card.width() - 26 - labelWidth) * zoom, 28 * zoom, zoom, background);
                }
                if (blockHover && (block.fields().isEmpty() || ImGui.getMousePosY() < rowY + 26 * zoom && ImGui.getMousePosX() < x + w - 48 * zoom)) {
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
                            nodeSelection.clear(); nodeSelection.add(node.id());
                            selectedEdge = block.output().pointer();
                            setPointer(block.pointer());
                            draggingNode = null;
                            wireDragMoved = false;
                        });
                    }
                }
            }
            float grip = Math.max(12, 16 * zoom);
            boolean resizeHover = nodeHover && selectedEdge.isEmpty() && ImGui.isMouseHoveringRect(x + w - grip, y + h - grip, x + w, y + h);
            if (resizeHover || nodeResize != null && nodeResize.original().node().id().equals(node.id()))
                ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.ResizeNWSE);
            if (resizeHover && nodeResize == null && ImGui.isMouseClicked(0)) operation(() -> {
                selectedNode = node.id(); nodeSelection.clear(); nodeSelection.add(node.id());
                setPointer(node.pointer()); selectedEdge = ""; draggingNode = null; boxSelecting = false;
                nodeResize = new EditorGraphSizing.Drag(card, ImGui.getMousePosX(), ImGui.getMousePosY(), zoom);
            });
            float footerY = y + h - 34 * zoom;
            String footer = document.kind() == DefinitionKind.QUEST ? "Edit stage" : "+ Add response";
            graphText(draw, x + 12 * zoom, footerY + 9 * zoom, 0xffbdcddd, footer, card.width() - 42);
            if (nodeResize == null && !resizeHover && nodeHover && ImGui.isMouseHoveringRect(x, footerY, x + w, y + h) && ImGui.isMouseClicked(0)) operation(() -> {
                selectedNode = node.id();
                setPointer(node.pointer());
                selectedEdge = "";
                showBranch = document.kind() != DefinitionKind.QUEST;
                nextStage.set("");
                if (!showBranch) focusPanel = DETAIL_PANEL;
            });
            if (!highlighted.contains(node.id())) draw.addRectFilled(x, y, x + w, y + h, 0x99000000, 4);
            for (int line = 0; line < 2; line++) {
                float inset = (5 + line * 5) * zoom;
                draw.addLine(x + w - inset - 6 * zoom, y + h - 4 * zoom, x + w - 4 * zoom, y + h - inset - 6 * zoom,
                        resizeHover ? 0xff81c9ed : 0xffaaa49a, Math.max(1, zoom));
            }
            if (resizeHover) ImGui.setTooltip("Drag to resize node. Right-click to reset its size.");
            ImGui.endChild();
        }
        draw = ImGui.getWindowDrawList();
        if (nodeResize != null && !ImGui.isMouseDown(0)) {
            var drag = nodeResize;
            nodeResize = null;
            var resized = cards.stream().filter(card -> card.node().id().equals(drag.original().node().id())).findFirst().orElse(null);
            if (resized != null && (resized.width() != drag.original().width() || resized.height() != drag.original().height())) {
                Map<String, float[]> layout = new HashMap<>();
                positions.forEach((id, point) -> layout.put(id, point.clone()));
                operation(() -> { EditorGraphSizing.save(document, resized, layout); refresh(); });
            }
        }
        if (draggingNode != null && positions.containsKey(draggingNode) && ImGui.isMouseDragging(0)) {
            nodeDragMoved = true;
            for (String id : selectedNodes()) {
                float[] point = positions.get(id);
                if (point == null) continue;
                point[0] += ImGui.getIO().getMouseDeltaX() / zoom;
                point[1] += ImGui.getIO().getMouseDeltaY() / zoom;
            }
        }
        boolean overlay = boxSelecting || showMinimap && !cards.isEmpty();
        if (overlay) {
            ImGui.setCursorScreenPos(canvasX, canvasY);
            ImGui.beginChild("graph_overlay", canvasWidth, canvasHeight, false, ImGuiWindowFlags.NoInputs
                    | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
            draw = ImGui.getWindowDrawList();
        }
        if (boxSelecting) {
            float mx = (ImGui.getMousePosX() - canvasX - panX) / zoom, my = (ImGui.getMousePosY() - canvasY - panY) / zoom;
            float left = canvasX + panX + Math.min(boxX, mx) * zoom, top = canvasY + panY + Math.min(boxY, my) * zoom;
            float right = canvasX + panX + Math.max(boxX, mx) * zoom, bottom = canvasY + panY + Math.max(boxY, my) * zoom;
            draw.addRectFilled(left, top, right, bottom, 0x337bc5eb);
            draw.addRect(left, top, right, bottom, 0xff7bc5eb);
            if (ImGui.isMouseReleased(0)) {
                var boxed = EditorGraphSelection.box(cards, positions, boxX, boxY, mx, my);
                boolean additive = boxAdditive;
                operation(() -> {
                    if (!additive) nodeSelection.clear();
                    nodeSelection.addAll(boxed);
                    selectedNode = nodeSelection.isEmpty() ? "" : nodeSelection.iterator().next();
                    setPointer(nodes.stream().filter(node -> node.id().equals(selectedNode)).map(DialogueGraph.Node::pointer).findFirst().orElse(""));
                });
                boxSelecting = false;
            }
        }
        if (showMinimap && !cards.isEmpty()) minimap(draw, layeredCards, canvasX, canvasY, canvasWidth, canvasHeight, minimapHover);
        if (overlay) ImGui.endChild();
        ImGui.popClipRect();
        ImGui.setCursorScreenPos(canvasEnd.x, canvasEnd.y);
        ImGui.dummy(0, 0);
        graphContext(layeredCards, canvasX, canvasY, hover);
        if (ImGui.isMouseReleased(0)) {
            wireDragMoved = false;
            if (draggingNode != null) {
                if (snapGrid && nodeDragMoved) EditorGraphSelection.snap(positions, selectedNodes(), draggingNode, 16);
                Map<String, float[]> movedPositions = new HashMap<>();
                positions.forEach((id, position) -> movedPositions.put(id, position.clone()));
                if (nodeDragMoved) operation(() -> { persistPositions(movedPositions); refresh(); });
                draggingNode = null;
            }
        }
        ImGui.endChild();
        ImGui.end();
    }

    private void renderGraphFields(EditorGraph.Card card, EditorGraph.Block block, float x, float y) {
        var node = card.node();
        ImGui.pushID("graph_inline:" + node.id() + ":" + block.pointer());
        ImGui.setWindowFontScale(zoom);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4 * zoom, 3 * zoom);
        ImGui.setCursorScreenPos(x + (card.width() - 40) * zoom, y + 3 * zoom);
        if (ImGui.smallButton("...")) operation(() -> {
            selectedNode = node.id(); nodeSelection.clear(); nodeSelection.add(node.id());
            setPointer(block.pointer()); focusPanel = DETAIL_PANEL;
        });
        if (ImGui.isItemHovered()) ImGui.setTooltip("More settings in Details");
        float fieldY = y + 28 * zoom;
        for (var field : block.fields()) {
            ImGui.pushID(field.document().resourcePath() + field.pointer());
            ImGui.setCursorScreenPos(x + 12 * zoom, fieldY);
            ImGui.textUnformatted(field.label());
            ImGui.setCursorScreenPos(x + 12 * zoom, fieldY + 20 * zoom);
            float width = (card.width() - 24) * zoom;
            ImString buffer = fields.computeIfAbsent(field.bufferId(document), ignored -> new ImString(
                    field.value().getAsJsonPrimitive().isString() ? field.value().getAsString() : field.value().toString(), 65536));
            ImGui.beginDisabled(rawModified || !field.editable() || nodeResize != null);
            boolean changed = false, activated = false, apply = false;
            if (field.value().getAsJsonPrimitive().isBoolean()) {
                if (ImGui.checkbox("##value", Boolean.parseBoolean(buffer.get()))) {
                    buffer.set(Boolean.toString(!Boolean.parseBoolean(buffer.get()))); changed = true;
                }
            } else if (field.multiline()) {
                var edit = textFields.inline("graph:" + node.id() + ":" + block.pointer() + ":" + field.pointer(), buffer, width, (76 + card.fieldGrowth()) * zoom);
                activated = edit.activated(); apply = edit.apply();
            } else {
                ImGui.setNextItemWidth(width);
                if (!field.options().isEmpty()) {
                    if (ImGui.beginCombo("##value", buffer.get())) {
                        for (String option : field.options()) if (ImGui.selectable(option, option.equals(buffer.get()))) {
                            buffer.set(option); changed = true;
                        }
                        ImGui.endCombo();
                    }
                } else ImGui.inputText("##value", buffer);
            }
            apply |= changed || ImGui.isItemDeactivatedAfterEdit();
            if (activated || ImGui.isItemActivated()) {
                selectedNode = node.id(); nodeSelection.clear(); nodeSelection.add(node.id());
                setPointer(block.pointer()); selectedEdge = ""; showBranch = false;
            }
            ImGui.endDisabled();
            if (field.editable()) {
                var edits = field.document() == document ? pendingFields : translatedFields.computeIfAbsent(field.document(), ignored -> new EditorFieldEdits());
                edits.update(field.pointer(), field.value(), buffer.get());
                String issue = edits.issue(field.document(), field.pointer());
                if (!issue.isEmpty() && ImGui.isItemHovered()) ImGui.setTooltip(issue);
                if (apply) operation(() -> { });
            } else if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) ImGui.setTooltip("Open Details to create or repair the translation.");
            ImGui.popID();
            fieldY += (field.height() + (field.multiline() ? card.fieldGrowth() : 0)) * zoom;
        }
        ImGui.popStyleVar();
        ImGui.setWindowFontScale(1);
        ImGui.popID();
    }

    private void graphContext(List<EditorGraph.Card> cards, float canvasX, float canvasY, boolean hover) {
        if (nodeResize != null) return;
        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
        float gx = (mx - canvasX - panX) / zoom, gy = (my - canvasY - panY) / zoom;
        String hoveredNode = EditorGraphSelection.topmost(cards, positions, gx, gy, Math.max(12, 10 * zoom) / zoom);
        var underMouse = cards.stream().filter(card -> card.node().id().equals(hoveredNode)).findFirst();
        boolean dropped = hover && wireDragMoved && ImGui.isMouseReleased(0) && !selectedEdge.isEmpty() && underMouse.isEmpty();
        if (hover && ImGui.isMouseClicked(1) || dropped) {
            contextX = gx; contextY = gy;
            contextNode = underMouse.map(card -> card.node().id()).orElse("");
            contextOutput = dropped ? selectedEdge : "";
            if (!dropped) for (var card : cards) {
                if (underMouse.isPresent() && !card.node().id().equals(hoveredNode)) continue;
                var point = positions.get(card.node().id());
                for (int i = 0; i < card.blocks().size(); i++) {
                    var edge = card.blocks().get(i).output();
                    if (edge == null) continue;
                    float x1 = canvasX + panX + (point[0] + card.width()) * zoom;
                    float y1 = canvasY + panY + (point[1] + card.outputY(i)) * zoom;
                    var target = positions.get(edge.to());
                    if (EditorGraphHit.port(mx, my, x1, y1, EditorGraph.ROW * zoom)
                            || underMouse.isEmpty() && target != null && EditorGraphHit.wire(mx, my, x1, y1,
                                    canvasX + panX + target[0] * zoom, canvasY + panY + (target[1] + EditorGraph.HEADER / 2) * zoom, zoom))
                        contextOutput = edge.pointer();
                }
            }
            nextStage.set(""); nodeName.set(contextNode); graphSnippetName.set(contextNode.isEmpty() ? "Branch" : contextNode);
            if (!contextNode.isEmpty()) {
                String id = contextNode;
                operation(() -> {
                    if (!selectedNodes().contains(id)) { nodeSelection.clear(); nodeSelection.add(id); }
                    selectedNode = id;
                    setPointer(documentCache.nodes(document).stream().filter(node -> node.id().equals(id)).findFirst().orElseThrow().pointer());
                });
            }
            boxSelecting = false; draggingNode = null;
            ImGui.openPopup("Graph actions");
        }
        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowSize(Math.min(520 * preferences.textScale(), viewport.getWorkSizeX() - 28), 0);
        ImGui.setNextWindowSizeConstraints(0, 0, viewport.getWorkSizeX() - 28, viewport.getWorkSizeY() - 28);
        if (!ImGui.beginPopup("Graph actions")) return;
        ImGui.setWindowFontScale(1);
        if (!contextOutput.isEmpty()) {
            String output = contextOutput;
            boolean connected = DialogueGraph.edges(document).stream().anyMatch(edge -> edge.pointer().equals(output) && !edge.to().isEmpty());
            if (ImGui.menuItem(connected ? "Insert node into connection" : "Create connected node")) operation(() -> createGraphAt(output, connected));
            if (connected && ImGui.menuItem("Reconnect destination")) { selectedEdge = output; wireDragMoved = false; }
            if (ImGui.menuItem("Cancel connection")) selectedEdge = "";
        } else if (!contextNode.isEmpty()) {
            String id = contextNode;
            if (ImGui.menuItem("Add response or connection")) { showBranch = true; nextStage.set(""); }
            if (ImGui.menuItem("Copy selected nodes")) operation(() -> graphClipboard.copy(workspace, document, selectedNodes()));
            if (ImGui.menuItem("Duplicate node")) operation(() -> {
                DialogueGraph.duplicateNode(document, id, nextNodeId()); refresh();
            });
            if (ImGui.menuItem("Reset node sizes")) operation(() -> { EditorGraphSizing.reset(document, selectedNodes()); refresh(); });
            if (ImGui.menuItem("Highlight upstream")) graphOrganization.focus("Upstream");
            if (ImGui.menuItem("Highlight downstream")) graphOrganization.focus("Downstream");
            if (ImGui.menuItem("Show all paths")) graphOrganization.focus("All nodes");
            ImGui.separator();
            if (document.kind() != DefinitionKind.FORCED) {
                ImGui.inputText(EditorWidgets.field("New node ID"), nodeName);
                if (ImGui.button("Rename node and references")) {
                    operation(() -> { EditorContentTools.renameNode(document, id, nodeName.get()); selectedNode = nodeName.get(); nodeSelection.clear(); nodeSelection.add(selectedNode); refresh(); });
                    ImGui.closeCurrentPopup();
                }
            }
            if (ImGui.menuItem("Delete selected nodes", keyHint("Delete selected nodes"))) operation(this::requestDeleteNodes);
            if (document.kind() != DefinitionKind.FORCED) {
                ImGui.inputText(EditorWidgets.field("Snippet name"), graphSnippetName);
                if (ImGui.button("Save selection as snippet")) {
                    operation(() -> library.saveSnippet(graphSnippetName.get(), EditorSnippets.nodes(workspace, document, selectedNodes())));
                    ImGui.closeCurrentPopup();
                }
            }
        } else {
            ImGui.beginDisabled(document.kind() == DefinitionKind.FORCED && !documentCache.json(document).has("entries"));
            if (ImGui.menuItem(document.kind() == DefinitionKind.QUEST ? "Add stage here" : "Add node here")) operation(() -> createGraphAt("", false));
            ImGui.endDisabled();
            if (ImGui.menuItem("Paste nodes")) operation(this::pasteNodes);
            if (ImGui.menuItem("Fit graph")) { fitSelection = false; fitGraph = true; }
            if (ImGui.menuItem("Show all paths")) graphOrganization.focus("All nodes");
            if (ImGui.menuItem("Comment groups")) showGraphTools = true;
        }
        ImGui.endPopup();
    }

    private void requestDeleteNodes() {
        deleteNodes = java.util.Set.copyOf(selectedNodes());
        if (deleteNodes.isEmpty()) return;
        deleteDocument = document;
        deleteRedirect = "";
        if (EditorGraphActions.needsRedirect(document, deleteNodes)) requestNodeDelete = true;
        else deleteSelectedNodes();
    }

    private void deleteSelectedNodes() {
        if (document != deleteDocument) throw new IllegalStateException("Select the original file before deleting its nodes");
        EditorGraphActions.delete(document, deleteNodes, deleteRedirect);
        selectedNode = ""; nodeSelection.clear(); selectedEdge = "";
        draggingNode = null; showBranch = false;
        setPointer(""); refresh();
        status = deleteNodes.size() == 1 ? "Node deleted. Ctrl+Z to undo." : "Selected nodes deleted. Ctrl+Z to undo.";
        deleteNodes = java.util.Set.of(); deleteDocument = null;
    }

    private void nodeDeletePopup() {
        if (requestNodeDelete) { requestNodeDelete = false; ImGui.openPopup("Delete nodes###node_delete"); }
        if (!EditorWidgets.beginPopup("Delete nodes###node_delete", 520, 310, preferences.textScale())) return;
        ImGui.textWrapped("These nodes have incoming connections or are a start point. Choose where those connections should lead after deletion.");
        var remaining = deleteDocument == document ? documentCache.nodes(document).stream().filter(node -> !deleteNodes.contains(node.id())).toList() : List.<DialogueGraph.Node>of();
        if (remaining.isEmpty()) ImGui.textWrapped("Keep at least one node. Add a replacement or select fewer nodes.");
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##delete_redirect", deleteRedirect.isEmpty() ? "Choose replacement node..." : deleteRedirect)) {
            for (var node : remaining) if (ImGui.selectable(node.id(), node.id().equals(deleteRedirect))) deleteRedirect = node.id();
            ImGui.endCombo();
        }
        ImGui.beginDisabled(deleteRedirect.isEmpty());
        if (ImGui.button("Delete and reconnect")) { operation(this::deleteSelectedNodes); ImGui.closeCurrentPopup(); }
        ImGui.endDisabled();
        sameLineFor("Cancel");
        if (ImGui.button("Cancel")) { deleteNodes = java.util.Set.of(); deleteDocument = null; ImGui.closeCurrentPopup(); }
        ImGui.endPopup();
    }

    private void createGraphAt(String output, boolean insert) {
        selectedNode = EditorGraphActions.create(document, output, nextNodeId(), insert, contextX, contextY);
        selectedEdge = ""; refresh();
        nodeSelection.clear(); nodeSelection.add(selectedNode);
        setPointer(documentCache.nodes(document).stream().filter(node -> node.id().equals(selectedNode)).findFirst().orElseThrow().pointer());
    }

    private void minimap(ImDrawList draw, List<EditorGraph.Card> cards, float canvasX, float canvasY, float width, float height, boolean hovered) {
        float left = canvasX + width - 190, top = canvasY + height - 130;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (var card : cards) {
            float[] point = positions.get(card.node().id());
            minX = Math.min(minX, point[0]); minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0] + card.width()); maxY = Math.max(maxY, point[1] + card.height());
        }
        float scale = Math.min(170 / Math.max(1, maxX - minX), 110 / Math.max(1, maxY - minY));
        draw.addRectFilled(left, top, left + 190, top + 130, 0xf0202020);
        for (var card : cards) {
            float[] point = positions.get(card.node().id());
            float x = left + 10 + (point[0] - minX) * scale, y = top + 10 + (point[1] - minY) * scale;
            draw.addRectFilled(x, y, x + card.width() * scale, y + card.height() * scale, card.node().id().equals(selectedNode) ? 0xff9cc6a2 : 0xff727272);
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
        EditorWidgets.sameLineFor(label);
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
        var nodes = documentCache.nodes(document);
        int index = nodes.size() + 1;
        while (true) {
            String id = "node_" + index++;
            if (nodes.stream().noneMatch(node -> node.id().equals(id))
                    && (!documentCache.json(document).has("nodes") || !documentCache.json(document).get("nodes").isJsonObject()
                        || !documentCache.json(document).getAsJsonObject("nodes").has(id))) return id;
        }
    }

    private String nextResponseId() {
        var node = documentCache.nodes(document).stream().filter(value -> value.id().equals(selectedNode)).findFirst().orElseThrow();
        JsonObject owner = JsonPointers.get(documentCache.json(document), node.pointer()).getAsJsonObject();
        JsonArray responses = DialogueGraph.array(owner, document.kind() == DefinitionKind.FORCED ? "options" : "responses");
        int index = responses.size() + 1;
        while (true) {
            String id = "choice_" + index++;
            if (responses.asList().stream().noneMatch(value -> value.isJsonObject() && id.equals(DialogueGraph.string(value.getAsJsonObject(), "id")))) return id;
        }
    }

    private void nodeActions() {
        if (!selectedNode.isEmpty() && EditorWidgets.section("Node actions")) {
            ImGui.textUnformatted("Selected: " + selectedNode);
            input("Node ID", nodeName);
            if (document.kind() != DefinitionKind.FORCED && ImGui.button("Rename node and references")) operation(() -> {
                EditorContentTools.renameNode(document, selectedNode, nodeName.get());
                selectedNode = nodeName.get();
                nodeSelection.clear(); nodeSelection.add(selectedNode);
                refresh();
                setPointer(documentCache.nodes(document).stream().filter(value -> value.id().equals(selectedNode)).findFirst().orElseThrow().pointer());
            });
            var incoming = EditorContentTools.usages(document, selectedNode);
            ImGui.textWrapped(incoming.size() + " incoming connections");
            for (var edge : incoming) if (ImGui.selectable((edge.from().isBlank() ? "File settings" : "From " + edge.from()) + "##usage" + edge.pointer())) operation(() -> {
                selectedNode = edge.from(); nodeSelection.clear();
                if (!selectedNode.isEmpty()) nodeSelection.add(selectedNode);
                setPointer(edge.pointer());
            });
            if (document.kind() == DefinitionKind.FORCED) ImGui.textWrapped("Deleting a follow-up also removes its owning player response.");
            if (ImGui.button("Duplicate node")) operation(() -> { DialogueGraph.duplicateNode(document, selectedNode, nextNodeId()); refresh(); });
            String deleteLabel = selectedNodes().size() > 1 ? "Delete selected nodes" : "Delete node";
            EditorWidgets.sameLineFor(deleteLabel);
            if (ImGui.button(deleteLabel)) operation(this::requestDeleteNodes);
            ImGui.beginDisabled(document.kind() == DefinitionKind.FORCED);
            if (ImGui.button("Start here")) operation(() -> { DialogueGraph.setEntry(document, selectedNode); refresh(); });
            if (ImGui.isItemHovered()) ImGui.setTooltip(document.kind() == DefinitionKind.QUEST ? "Start the quest at this stage."
                    : "Change the first entry or topic to start at this node. Other entries are preserved.");
            ImGui.endDisabled();
            EditorWidgets.sameLineFor("End here");
            if (ImGui.button("End here")) operation(() -> { DialogueGraph.setTerminal(document, selectedNode, true); refresh(); });
            if (ImGui.isItemHovered()) ImGui.setTooltip(document.kind() == DefinitionKind.CONVERSATION || document.kind() == DefinitionKind.FORCED
                    ? "End this node. Remove existing player choices first; they will not be discarded automatically." : "End this node.");
        }
    }

    private void inspector() {
        panel(DETAIL_PANEL);
        if (activeDiagnostic != null) diagnosticNavigation();
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
        if (ImGui.button("File settings", -1, 0)) operation(() -> {
            selectedNode = ""; nodeSelection.clear(); showBranch = false; setPointer("");
            if (document.kind() == DefinitionKind.RESOURCE) resourceView.settings();
        });
        if (!selectedNode.isEmpty() || document.kind() == DefinitionKind.FORCED && pointer.get().isEmpty()) {
            if (document.kind() != DefinitionKind.QUEST && document.kind() != DefinitionKind.RESOURCE
                    && ImGui.button(document.kind() == DefinitionKind.CONVERSATION ? "Add spoken line" : "Add line variation", -1, 0)) operation(() -> { workspace.addDialogueLine(document, selectedNode); refresh(); });
        }
        try {
            bulkEdit.render(workspace, document, selectedNodes(), this::operation, this::refresh);
            var availableBlocks = EditorBlocks.available(document, pointer.get());
            if (!availableBlocks.isEmpty()) ImGui.setNextItemWidth(-1);
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
                EditorQuickFixes.render(workspace, document, issue, this::operation, this::refresh);
            }
            if (pendingFields.pending()) try { pendingFields.validate(document); }
            catch (IllegalArgumentException | IllegalStateException | JsonParseException exception) { ImGui.textWrapped(exception.getMessage()); }
            guidedObjective();
            nodeActions();
        } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException
                | ClassCastException | UnsupportedOperationException exception) {
            ImGui.textWrapped("Select an existing JSON object or field: " + exception.getMessage());
        }
        if (hasPendingFields()) {
            if (ImGui.button("Apply field edits")) operation(this::focusFirstError);
            EditorWidgets.sameLineFor("Discard field edits");
            if (ImGui.button("Discard field edits")) attempt(() -> { pendingFields.clear(); translatedFields.clear(); fields.clear(); textFields.clear(); });
        }
        ImGui.separator();
        if (EditorWidgets.section("Advanced editing")) {
            input("JSON pointer", pointer);
            if (ImGui.button("Inspect pointer")) operation(() -> { });
            if (!pointer.get().isEmpty() && ImGui.button("Remove selected field")) operation(() -> {
                document.edit("Remove field", root -> JsonPointers.remove(root, pointer.get()));
                setPointer("");
                refresh();
            });
            tutorialObjectiveCatalog();
            conditionCatalog();
            ImGui.textWrapped("Add an object field or append to an array. JSON values preserve their types.");
            input("Field name", fieldName);
            ImGui.textUnformatted("Value JSON");
            ImGui.inputTextMultiline("##field_value_json", fieldValue, -1, 80);
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
            if (EditorWidgets.section("Advanced full JSON")) {
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
                EditorWidgets.sameLineFor("Discard JSON buffer");
                if (ImGui.button("Discard JSON buffer")) attempt(() -> {
                    if (hasPendingFields()) throw new IllegalStateException("Apply or discard field edits first.");
                    refresh();
                });
                if (rawModified) ImGui.textWrapped("Unapplied JSON buffer. Apply before saving, previewing or switching definitions.");
            }
        }
        snippets.render(library, workspace, document, selectedNodes(), pointer.get(), this::operation, this::selectInsertedNodes);
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
        String owner = DialogueGraph.string(documentCache.json(document), "world_campaign");
        String fallback = addon ? "All supported campaigns" : "Default VR";
        if (ImGui.beginCombo(EditorWidgets.field(addon ? "Campaign filter" : "World campaign"), owner.isBlank() ? fallback : owner)) {
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
            ImString arc = new ImString(DialogueGraph.string(documentCache.json(document), "story_arc"), 512);
            if (ImGui.inputText(EditorWidgets.field("Story arc (optional)"), arc)) document.edit("Set story arc", root -> {
                if (arc.get().isBlank()) root.remove("story_arc");
                else root.addProperty("story_arc", arc.get());
            });
        }
    }

    private void campaignCard() {
        JsonObject value = documentCache.json(document);
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
        if (document != null) {
            var issues = pendingFields.issues(document);
            if (!issues.isEmpty()) focusField(document, issues.keySet().iterator().next());
            pendingFields.validate(document);
        }
        for (var entry : translatedFields.entrySet()) {
            var issues = entry.getValue().issues(entry.getKey());
            if (!issues.isEmpty()) focusField(entry.getKey(), issues.keySet().iterator().next());
            entry.getValue().validate(entry.getKey());
        }
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
        selectInsertedNodes(graphClipboard.paste(workspace, document));
    }

    private void selectInsertedNodes(List<String> added) {
        refresh();
        if (added.isEmpty()) return;
        nodeSelection.clear(); nodeSelection.addAll(added);
        selectedNode = added.getFirst(); graphFocus = selectedNode;
        setPointer(documentCache.nodes(document).stream().filter(value -> value.id().equals(selectedNode)).findFirst().orElseThrow().pointer());
    }

    private void navigateNode(int offset) {
        var nodes = documentCache.nodes(document);
        if (nodes.isEmpty()) return;
        int index = 0;
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).id().equals(selectedNode)) index = i;
        var node = nodes.get(Math.floorMod(index + offset, nodes.size()));
        selectedNode = node.id(); setPointer(node.pointer()); graphFocus = node.id();
        nodeSelection.clear(); nodeSelection.add(node.id());
    }

    private void graphTools() {
        if (document == null || document.kind() == DefinitionKind.RESOURCE || document.kind().campaignManifest()) return;
        graphOrganization.render(document, selectedNodes(), this::operation, ids -> operation(() -> {
            nodeSelection.clear(); nodeSelection.addAll(ids);
            selectedNode = ids.isEmpty() ? "" : ids.iterator().next();
            setPointer(documentCache.nodes(document).stream().filter(node -> node.id().equals(selectedNode)).map(DialogueGraph.Node::pointer).findFirst().orElse(""));
        }));
        if (ImGui.checkbox("Show minimap", showMinimap)) { showMinimap = !showMinimap; saveViewPreferences(); }
        ImGui.textWrapped("Drag empty space to box-select. Ctrl adds to the selection. Drag selected headers to move the group.");
        ImGui.textWrapped("Copy: " + keyHint("Copy nodes") + ". Paste: " + keyHint("Paste nodes") + ". Previous node: " + keyHint("Previous node") + ". Next node: " + keyHint("Next node") + ".");
        if (ImGui.checkbox("Snap to grid", snapGrid)) { snapGrid = !snapGrid; attempt(this::saveSession); }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Snap selected nodes to a 16-unit grid while preserving their relative positions.");
        ImGui.beginDisabled(selectedNodes().isEmpty());
        if (ImGui.button("Frame selection")) { fitSelection = true; fitGraph = true; }
        ImGui.endDisabled();
        ImGui.beginDisabled(selectedNodes().size() < 2);
        if (ImGui.beginCombo(EditorWidgets.field("Arrange selected nodes"), "Choose alignment or spacing...")) {
            for (String action : List.of("Align left", "Align top", "Space horizontally", "Space vertically", "Arrange selection"))
                if (ImGui.selectable(action)) operation(() -> alignNodes(action));
            ImGui.endCombo();
        }
        ImGui.endDisabled();
        if (ImGui.button("Copy selected nodes")) operation(() -> graphClipboard.copy(workspace, document, selectedNodes()));
        if (ImGui.button("Paste nodes")) operation(this::pasteNodes);
        if (!EditorWidgets.section("Graph outline and bookmarks")) return;
        input("Find node or wording", nodeFilter);
        JsonObject metadata = documentCache.metadata(document);
        var bookmarks = metadata.has("bookmarks") ? metadata.getAsJsonArray("bookmarks") : new JsonArray();
        for (var node : documentCache.nodes(document)) {
            if (!(node.id() + JsonPointers.get(documentCache.json(document), node.pointer())).toLowerCase(Locale.ROOT).contains(nodeFilter.get().toLowerCase(Locale.ROOT))) continue;
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
        if (document.kind() != DefinitionKind.QUEST || selectedNode.isEmpty() || !EditorWidgets.section("Add quest objective")) return;
        if (ImGui.beginCombo(EditorWidgets.field("Objective"), objectiveType.replace('_', ' '))) {
            for (String type : com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry.descriptors().stream().map(value -> value.id()).sorted().toList())
                if (ImGui.selectable(type.replace('_', ' '), objectiveType.equals(type))) objectiveType = type;
            ImGui.endCombo();
        }
        boolean simple = List.of("item_check", "mob_kill", "block_break", "block_place", "block_interact", "trade").contains(objectiveType);
        if (simple) {
            if (!objectiveType.equals("trade")) {
                input("Resource ID", objectiveResource);
                var registry = objectiveType.equals("mob_kill") ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        : objectiveType.startsWith("block_") ? net.minecraft.core.registries.BuiltInRegistries.BLOCK : net.minecraft.core.registries.BuiltInRegistries.ITEM;
                resourcePicker.render("objective_resource", "Choose " + (objectiveType.equals("mob_kill") ? "entity" : objectiveType.startsWith("block_") ? "block" : "item") + "...", objectiveResource.get(),
                        registry.keySet().stream().map(Object::toString).sorted().toList(), objectiveResource::set, library, this::attempt);
            }
            ImGui.inputInt(EditorWidgets.field("Required count"), objectiveCount);
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

    private void resourceFields(JsonElement value, String path, boolean settings) {
        if (!value.isJsonObject()) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && (document.resourcePath().contains("/locales/") || document.resourcePath().contains("/dialogue_words/")))
                inspectValue(value, path, "Text", 0, true);
            else inspectValue(value, path, "Value", 0);
            return;
        }
        var object = value.getAsJsonObject();
        for (var entry : object.entrySet()) {
            if (settings && !entry.getKey().equals("metadata") && (entry.getValue().isJsonArray() || entry.getValue().isJsonObject())) continue;
            if (settings && document.resourcePath().contains("/locales/") && !List.of("schema", "metadata", "locale").contains(entry.getKey())) continue;
            inspectValue(entry.getValue(), path + "/" + escape(entry.getKey()), friendlyLabel(entry.getKey()), 0);
        }
        schemaObjectTools(object, path);
    }

    private void resourceCollectionTools(EditorResourceView.Section section) {
        if (section.value().isJsonArray()) {
            var rule = propertySchema.at(document, documentCache.json(document), section.pointer());
            ImGui.setNextItemWidth(-1);
            if (ImGui.beginCombo("##resource_add_entry", "Add entry...")) {
                for (var option : propertySchema.options(EditorSchema.object(rule.get("items"))).entrySet())
                    if (ImGui.selectable(option.getKey())) editArray(section.pointer(), values -> values.add(option.getValue().get()));
                ImGui.endCombo();
            }
        } else if (section.value().isJsonObject()) schemaObjectTools(section.value().getAsJsonObject(), section.pointer());
    }

    private void poolField(JsonElement value, String path, String label, boolean text) {
        if (!value.isJsonPrimitive()) { inspectValue(value, path, label, 0); return; }
        ImGui.pushID(path);
        ImGui.textUnformatted(label);
        ImString buffer = fields.computeIfAbsent(path, ignored -> new ImString(value.getAsString(), 65536));
        ImGui.setNextItemWidth(-1);
        boolean activated, apply;
        if (text) {
            var edit = textFields.inline("pool:" + path, buffer, Math.max(1, ImGui.getContentRegionAvailX()), 100 * preferences.textScale());
            activated = edit.activated(); apply = edit.apply();
        } else {
            ImGui.inputText("##value", buffer);
            activated = ImGui.isItemActivated(); apply = ImGui.isItemDeactivatedAfterEdit();
        }
        if (activated) setPointer("/lines/" + path.split("/")[2]);
        pendingFields.update(path, value, buffer.get());
        String error = fieldMessage(path, value);
        if (!error.isEmpty()) ImGui.textWrapped(error);
        if (apply) operation(() -> { });
        ImGui.popID();
    }

    private void selectedDetails() {
        if (view == 0 && !focusMode.equals(DETAIL_PANEL) && document.kind() == DefinitionKind.RESOURCE && !dialoguePool.supports(document, documentCache.json(document))) {
            ImGui.textWrapped("Edit this resource in the work area. Choose a section, then select an entry.");
            ImGui.textWrapped(pointer.get().isEmpty() ? document.resourcePath() : pointer.get());
            return;
        }
        JsonElement selected = JsonPointers.get(documentCache.json(document), pointer.get());
        if (selected.isJsonObject() && document.kind() == DefinitionKind.RESOURCE
                && dialoguePool.supports(document, documentCache.json(document))
                && (pointer.get().isEmpty() || pointer.get().matches("/lines/[0-9]+"))) {
            ImGui.textWrapped(pointer.get().isEmpty() ? "Select a pool card to edit its selection rules."
                    : "Edit dialogue, ID, request and weight on the card. Additional selection rules are below.");
            for (var entry : selected.getAsJsonObject().entrySet()) {
                if (pointer.get().isEmpty() ? entry.getKey().equals("lines") : List.of("id", "request", "weight", "lines", "variants").contains(entry.getKey())) continue;
                inspectValue(entry.getValue(), pointer.get() + "/" + escape(entry.getKey()), friendlyLabel(entry.getKey()), 0);
            }
            schemaObjectTools(selected.getAsJsonObject(), pointer.get());
            return;
        }
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
        if (!shown || EditorWidgets.section("More properties")) {
            for (var entry : object.entrySet()) {
                if (main.contains(entry.getKey()) || matchMode && entry.getKey().equals("all")) continue;
                inspectValue(entry.getValue(), pointer.get() + "/" + escape(entry.getKey()), friendlyLabel(entry.getKey()), 0);
            }
        }
        if (EditorWidgets.section("Guided properties")) schemaObjectTools(object, pointer.get());
    }

    private void schemaObjectTools(JsonObject value, String path) {
        if (propertySchema == null) return;
        JsonObject rule = propertySchema.at(document, documentCache.json(document), path);
        JsonObject properties = EditorSchema.object(rule.get("properties"));
        var required = propertySchema.required(rule);
        ImGui.textUnformatted("Add property");
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##add_property" + path, "Choose a field...")) {
            for (var entry : properties.entrySet()) {
                if (value.has(entry.getKey())) continue;
                if (ImGui.beginMenu(friendlyLabel(entry.getKey()) + (required.contains(entry.getKey()) ? " (required)" : ""))) {
                    for (var choice : propertySchema.options(EditorSchema.object(entry.getValue())).entrySet()) {
                        if (ImGui.menuItem(choice.getKey())) operation(() -> replaceValue(path + "/" + escape(entry.getKey()), choice.getValue().get()));
                    }
                    ImGui.endMenu();
                }
            }
            ImGui.endCombo();
        }
        ImGui.textUnformatted("Remove property");
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##remove_property" + path, "Choose an optional field...")) {
            for (String name : value.keySet()) if (!required.contains(name) && ImGui.selectable(friendlyLabel(name)))
                operation(() -> { document.edit("Remove property", root -> JsonPointers.remove(root, path + "/" + escape(name))); refresh(); });
            ImGui.endCombo();
        }
        if (!rule.has("additionalProperties") || !rule.get("additionalProperties").isJsonPrimitive()
                || rule.get("additionalProperties").getAsBoolean()) {
            if (ImGui.treeNode("Named fields##" + path)) {
                ImString name = fields.computeIfAbsent("schema_name:" + path, ignored -> new ImString(256));
                input("Field name", name);
                if (ImGui.beginCombo(EditorWidgets.field("Field value"), "Choose a type...")) {
                    JsonObject additional = EditorSchema.object(rule.get("additionalProperties"));
                    var options = propertySchema.options(additional);
                    for (var choice : options.entrySet()) if (ImGui.selectable(choice.getKey())) operation(() -> {
                        if (name.get().isBlank() || value.has(name.get())) throw new IllegalArgumentException("Enter a new, nonempty field name");
                        replaceValue(path + "/" + escape(name.get()), choice.getValue().get());
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
            JsonElement text = JsonPointers.get(documentCache.json(location.document()), location.pointer());
            if (text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
                String bufferId = location.document().resourcePath() + location.pointer();
                ImString buffer = fields.computeIfAbsent(bufferId, ignored -> new ImString(text.getAsString(), 65536));
                ImGui.beginDisabled(rawModified);
                textFields.render(bufferId, buffer, document.kind(), !path.endsWith("/label") && !path.endsWith("/title"), preferences.textScale());
                ImGui.endDisabled();
                translatedFields.computeIfAbsent(location.document(), ignored -> new EditorFieldEdits())
                        .update(location.pointer(), text, buffer.get());
                if (fieldFocus.equals(bufferId)) { fieldFocus = ""; ImGui.setScrollHereY(.25f); textFields.focus(bufferId); }
                if (ImGui.isItemHovered()) ImGui.setTooltip(locale.get() + " wording. The localization reference is preserved.");
                referenceButtons(path + "/key", key);
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
        if(!EditorWidgets.section("Clipboard job objectives"))return;
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
        if (!EditorWidgets.section("Runtime condition catalog")) return;
        ImGui.textWrapped("Select a conditions list, then choose a runtime condition. Guided properties supplies its declared fields.");
        for (var descriptor : com.jvn.villagerretaliation.dialogue.DialogueCondition.descriptors()) {
            if (ImGui.selectable(EditorProgressConditions.label(descriptor.id()) + "##" + descriptor.id())) {
                JsonObject condition = new JsonObject();
                condition.addProperty("type", descriptor.id());
                operation(() -> {
                    if (!pointer.get().endsWith("/conditions") || !JsonPointers.get(documentCache.json(document), pointer.get()).isJsonArray())
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
        inspectValue(value, path, label, depth, false);
    }

    private void inspectValue(JsonElement value, String path, String label, int depth, boolean styledText) {
        if (depth > 16) { ImGui.textUnformatted("Use advanced JSON for deeper nesting."); return; }
        ImGui.pushID(path);
        boolean scalar = value.isJsonPrimitive() || value.isJsonNull();
        String error = scalar ? fieldMessage(path, value) : "";
        if (!error.isEmpty()) ImGui.pushStyleColor(ImGuiCol.FrameBg, .32f, .12f, .12f, 1);
        if (value.isJsonObject() && value.getAsJsonObject().has("key")
                && value.getAsJsonObject().get("key").isJsonPrimitive() && workspace != null
                && (path.endsWith("/text") || path.endsWith("/label") || path.endsWith("/title"))) {
            localizedField(value.getAsJsonObject(), label, path);
        } else if (value.isJsonObject()) {
            if (depth < 2) ImGui.setNextItemOpen(true, ImGuiCond.Once);
            if (depth == 0 ? EditorWidgets.treeSection(label) : ImGui.treeNode(label)) {
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
                ImGui.beginGroup();
                inspectValue(lines.get(i), path + "/" + i, lines.size() == 1 ? "Dialogue text" : "Line " + (i + 1), depth + 1);
                ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, ImGui.getStyle().getFramePaddingY());
                ImGui.beginDisabled(lines.size() == 1);
                if (ImGui.smallButton("Remove line")) editArray(path, values -> values.remove(index));
                ImGui.endDisabled();
                sameLineFor("Duplicate line");
                if (ImGui.smallButton("Duplicate line")) editArray(path, values -> values.add(values.get(index)));
                if (i > 0) {
                    sameLineFor("Move up");
                    if (ImGui.smallButton("Move up")) editArray(path, values -> {
                        JsonElement previous = values.get(index - 1);
                        values.set(index - 1, values.get(index));
                        values.set(index, previous);
                    });
                }
                listReorderHandle(path, i, lines);
                ImGui.popStyleVar();
                ImGui.endGroup();
                listDropTarget(path, i);
                ImGui.popID();
            }
        } else if (value.isJsonArray()) {
            if (depth == 0 && (!path.endsWith("/responses") && !path.endsWith("/options") || pointer.get().equals(path)))
                ImGui.setNextItemOpen(true, ImGuiCond.Once);
            String title = label + " [" + value.getAsJsonArray().size() + "]";
            if (depth == 0 ? EditorWidgets.treeSection(title) : ImGui.treeNode(title)) {
                if (ImGui.smallButton("Focus list")) operation(() -> setPointer(path));
                var array = value.getAsJsonArray();
                JsonObject rule = propertySchema.at(document, documentCache.json(document), path);
                if (ImGui.beginCombo(EditorWidgets.field("Add entry"), "Choose an entry type...")) {
                    for (var choice : propertySchema.options(EditorSchema.object(rule.get("items"))).entrySet())
                        if (ImGui.selectable(choice.getKey())) editArray(path, values -> values.add(choice.getValue().get()));
                    ImGui.endCombo();
                }
                for (int i = 0; i < array.size(); i++) {
                    final int index = i;
                    ImGui.pushID(i);
                    ImGui.beginGroup();
                    inspectValue(array.get(i), path + "/" + i, "[" + i + "]", depth + 1);
                    ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, ImGui.getStyle().getFramePaddingY());
                    if (ImGui.smallButton("Remove")) editArray(path, values -> values.remove(index));
                    EditorWidgets.sameLineFor("Duplicate");
                    if (ImGui.smallButton("Duplicate")) editArray(path, values -> values.add(values.get(index)));
                    if (i > 0) {
                        EditorWidgets.sameLineFor("Move up");
                        if (ImGui.smallButton("Move up")) editArray(path, values -> {
                            JsonElement previous = values.get(index - 1);
                            values.set(index - 1, values.get(index));
                            values.set(index, previous);
                        });
                    }
                    listReorderHandle(path, i, array);
                    ImGui.popStyleVar();
                    ImGui.endGroup();
                    listDropTarget(path, i);
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
            if (fieldFocus.equals(path)) {
                fieldFocus = ""; ImGui.setScrollHereY(.25f);
                if (text) textFields.focus(path);
                ImGui.setKeyboardFocusHere();
            }
            if (text && (styledText || path.matches(".*/(?:lines|variants)/[0-9]+") || path.endsWith("/text") || path.endsWith("/line") || path.endsWith("/response")))
                textFields.render(path, input, document.kind(), true, preferences.textScale());
            else if (text && path.endsWith("/label"))
                textFields.render(path, input, document.kind(), false, preferences.textScale());
            else {
                ImGui.inputText("##value", input);
                apply = ImGui.isItemDeactivatedAfterEdit() && ImGui.isKeyPressed(ImGuiKey.Enter);
                if (text) propertyPicker(path, input);
            }
            ImGui.endDisabled();
            pendingFields.update(path, value, input.get());
            if (text) referenceButtons(path, input.get());
            if (apply) operation(() -> { });
        }
        if (!error.isEmpty()) ImGui.popStyleColor();
        if (scalar) {
            String message = fieldMessage(path, value);
            if (!message.isEmpty()) { ImGui.pushStyleColor(ImGuiCol.Text, 1, .4f, .35f, 1); ImGui.textWrapped(message); ImGui.popStyleColor(); }
        }
        ImGui.popID();
    }

    private void listReorderHandle(String path, int index, JsonArray baseline) {
        sameLineFor("::");
        ImGui.smallButton("::");
        if (ImGui.isItemHovered()) ImGui.setTooltip("Drag to reorder this entry");
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(new EditorArrayOrder.Drag(document, path, index, baseline.deepCopy()));
            ImGui.textUnformatted("Move entry " + (index + 1));
            ImGui.endDragDropSource();
        }
    }

    private void listDropTarget(String path, int index) {
        if (!ImGui.beginDragDropTarget()) return;
        var drag = ImGui.acceptDragDropPayload(EditorArrayOrder.Drag.class);
        if (drag != null && drag.document() == document && drag.pointer().equals(path)) operation(() -> {
            EditorArrayOrder.move(document, path, drag.index(), index, drag.baseline()); refresh();
        });
        ImGui.endDragDropTarget();
    }

    private String fieldMessage(String path, JsonElement value) {
        String issue = pendingFields.issue(document, path);
        if (!issue.isEmpty()) return issue;
        if (!pendingFields.contains(path)) for (var diagnostic : diagnostics)
            if (diagnostic.resourcePath().equals(document.resourcePath()) && diagnostic.pointer().equals(path)
                    && diagnostic.severity() == AuthoringDiagnostic.Severity.ERROR) return diagnostic.message();
        if (pendingFields.contains(path)) value = pendingFields.candidate(document, path);
        if (!value.isJsonPrimitive() && !value.isJsonNull()) return "";
        var messages = propertySchema.scalarIssues(document, documentCache.json(document), path, value);
        return messages.isEmpty() ? "" : messages.getFirst();
    }

    private void focusField(AuthoringDocument owner, String path) {
        if (owner == document) {
            var location = EditorDiagnosticLocation.resolve(owner, path);
            selectedNode = location.nodeId(); setPointer(location.pointer()); fieldFocus = location.pointer();
        } else {
            fieldFocus = owner.resourcePath() + path;
            textFields.focus(fieldFocus);
        }
        focusPanel = DETAIL_PANEL;
    }

    private void focusFirstError() {
        var issue = diagnostics.stream().filter(value -> value.severity() == AuthoringDiagnostic.Severity.ERROR
                && document != null && value.resourcePath().equals(document.resourcePath())).findFirst().orElse(null);
        if (issue != null) focusField(document, issue.pointer());
    }

    private void matchMode(JsonObject condition, String path) {
        boolean all = !condition.has("all") || condition.get("all").getAsBoolean();
        if (ImGui.beginCombo(EditorWidgets.field("Match entries"), all ? "All" : "Any")) {
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
        var parent = JsonPointers.get(documentCache.json(document), path.substring(0, path.lastIndexOf('/')));
        if (field.equals("target") && parent.isJsonObject()) {
            var type = parent.getAsJsonObject().get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()) {
                if (type.getAsString().equals("player_stat")) choices = EditorProgressConditions.statisticTargets(parent.getAsJsonObject());
                else if (type.getAsString().equals("event_history")) choices = EditorProgressConditions.historyTargets(parent.getAsJsonObject());
            }
        }
        JsonObject rule = propertySchema.at(document, documentCache.json(document), path);
        if (rule.has("enum")) choices = rule.getAsJsonArray("enum").asList().stream().map(JsonElement::getAsString).toList();
        if (rule.has("description")) ImGui.textWrapped(rule.get("description").getAsString());
        resourcePicker.render(path, "Choose " + field + "...", input.get(), choices, input::set, library, this::attempt);
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
        float split = focusMode.equals(WORK_PANEL) ? 0 : Math.round(width * fraction);
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
        if (!focusMode.equals(WORK_PANEL) && ImGui.isItemActive()) {
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
        ImGui.setWindowFontScale(1);
        if (focusPanel.equals("Playtest###VR_Playtest")) { ImGui.setWindowFocus(); focusPanel = ""; }
        if (ImGui.button("Edit")) operation(() -> leavePlaytest(0));
        EditorWidgets.sameLineFor("Preview");
        if (ImGui.button("Preview")) operation(() -> leavePlaytest(1));
        EditorWidgets.sameLineFor("Build pack");
        if (ImGui.button("Build pack")) operation(() -> { leavePlaytest(2); validate(); });
        if (ImGui.button("Save")) operation(this::save);
        EditorWidgets.sameLineFor("Stop playtest");
        if (ImGui.button("Stop playtest")) operation(() -> leavePlaytest(0));
        EditorWidgets.sameLineFor(focusMode.equals(WORK_PANEL) ? "Restore game view" : "Focus test controls");
        if (ImGui.button(focusMode.equals(WORK_PANEL) ? "Restore game view" : "Focus test controls")) focusMode = focusMode.equals(WORK_PANEL) ? "" : WORK_PANEL;
        navigationControls();
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
                for (var node : documentCache.nodes(document)) {
                    String running = document.kind() == DefinitionKind.QUEST ? EditorLiveQuestTest.snapshot().stage()
                            : AuthoringDialoguePreview.cursor() == null ? "" : AuthoringDialoguePreview.cursor().node();
                    if (ImGui.selectable((node.id().equals(running) ? "> " : "") + node.id(), node.id().equals(selectedNode))) operation(() -> {
                        selectedNode = node.id(); setPointer(node.pointer()); fields.clear(); textFields.clear();
                    });
                }
                if (document.kind() == DefinitionKind.QUEST) playtestQuest();
                else playtestDialogue();
                if (EditorWidgets.section("Step properties", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
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
        if (AuthoringDialoguePreview.active()) attempt(() -> AuthoringDialoguePreview.refreshPresentation(documentCache.json(document)));
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
                    .equals(com.jvn.villagerretaliation.authoring.preview.PlaytestDrafts.logic(documentCache.json(document))))
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
            if (cursor.exchanges() > 1 && ImGui.beginCombo(EditorWidgets.field("Exchange"), Integer.toString(cursor.exchange() + 1))) {
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
        if (EditorWidgets.section("Dialogue context")) {
            EditorPreviewConditions.time(conditions);
            EditorPreviewConditions.render(conditions, documentCache.json(document));
        }
        if (EditorWidgets.section("Dialogue log")) for (String entry : AuthoringDialoguePreview.log()) ImGui.textWrapped(entry);
    }

    private void playtestQuest() {
        if (!EditorTestWorld.active(workspace)) {
            String problem = EditorTestWorld.problem(workspace);
            if (!problem.isEmpty()) ImGui.textWrapped("Could not resume this test world: " + problem);
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
        ImGui.inputText(EditorWidgets.field("Fact / reputation value"), playtestValue);
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
        if (EditorWidgets.section("Test shortcuts and actions")) for (String line : state.log()) ImGui.textWrapped(line);
        if (EditorWidgets.section("Runtime diagnostics")) for (String line : EditorLiveQuestTest.view().lines()) ImGui.textWrapped(line);
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
        EditorWidgets.sameLineFor("Nearest villager");
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
        EditorWidgets.sameLineFor(document.kind() == DefinitionKind.RESOURCE ? "Play selected object" : "Play selected node");
        if (ImGui.button(document.kind() == DefinitionKind.RESOURCE ? "Play selected object" : "Play selected node"))
            startPreview(selectedNode.isEmpty() ? pointer.get() : selectedNode);
        ImGui.endDisabled();
        if (target == null) ImGui.textDisabled("Select a target to play.");
        else EditorCameraPanel.render(document, pointer.get(), target, screen, this::refresh, this::operation);
        EditorPreviewConditions.render(conditions, document == null ? null : documentCache.json(document));
        previewScenarios();
        if (EditorWidgets.section("Preview log"))
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
        if (ImGui.button("Start / restart quest test")) operation(() -> {
            questPreview = new com.jvn.villagerretaliation.authoring.preview.QuestPreviewSession(document, conditions);
            simulationCoverage = coverage.observe(document);
            simulationCoverage.accept(questPreview.drainCoverage());
        });
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
        EditorPreviewConditions.render(conditions, documentCache.json(document));
        previewScenarios();
        liveQuestTest();
    }

    private void liveQuestTest() {
        if (!EditorWidgets.section("Live quest test", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) return;
        if (!EditorTestWorld.active(workspace)) {
            String problem = EditorTestWorld.problem(workspace);
            if (!problem.isEmpty()) ImGui.textWrapped("Could not resume this test world: " + problem);
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
        if (state.quest().equals(document.id()) && EditorWidgets.section("Live quest diagnostics"))
            for (String line : state.lines()) ImGui.textWrapped(line);
    }

    private void previewScenarios() {
        if (!EditorWidgets.section("Saved test scenarios")) return;
        input("Scenario name", scenarioName);
        if (ImGui.button("Save scenario")) operation(() -> {
            if (scenarioName.get().isBlank()) throw new IllegalArgumentException("Enter a scenario name");
            document.editMetadata("Save scenario", metadata -> {
                if (!metadata.has("preview_scenarios")) metadata.add("preview_scenarios", new JsonObject());
                metadata.getAsJsonObject("preview_scenarios").add(scenarioName.get(), conditions.snapshot());
            });
        });
        JsonObject metadata = documentCache.metadata(document);
        if (metadata.has("preview_scenarios")) for (var entry : metadata.getAsJsonObject("preview_scenarios").entrySet()) {
            if (ImGui.button("Load " + entry.getKey())) operation(() -> { scenarioName.set(entry.getKey()); conditions.restore(entry.getValue().getAsJsonObject()); EditorPreviewConditions.sync(conditions); questPreview = null; });
            EditorWidgets.sameLineFor("Remove##scenario");
            if (ImGui.smallButton("Remove##scenario" + entry.getKey())) operation(() -> document.editMetadata("Remove scenario", value -> {
                value.getAsJsonObject("preview_scenarios").remove(entry.getKey());
                if (value.has("preview_scenario_tests")) value.getAsJsonObject("preview_scenario_tests").remove(entry.getKey());
            }));
        }
        scenarioTests.render(document, this::operation, (key, simulated) -> {
            var reference = new JsonObject(); reference.addProperty("key", key);
            var id = net.minecraft.resources.ResourceLocation.tryParse(document.id());
            String resolved = workspace.resolveText(document, reference, locale.get(), simulated.variant(),
                    candidate -> simulated.evaluate(id, candidate) == com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.MATCHED);
            return resolved.equals(key) ? null : resolved;
        },
                coverage.observe(document), id -> operation(() -> openScenarioResult(id)));
    }

    private void openScenarioResult(String id) {
        if (id.equals("/") && document.kind() == DefinitionKind.FORCED) id = "";
        String destination = id;
        selectedNode = id;
        nodeSelection.clear(); if (!id.isEmpty()) nodeSelection.add(id);
        setPointer(documentCache.nodes(document).stream().filter(node -> node.id().equals(destination)).map(DialogueGraph.Node::pointer).findFirst().orElse(""));
        graphFocus = id; view = 0; focusMode = ""; focusPanel = WORK_PANEL;
    }

    private void startPreview(String node) {
        operation(() -> {
            if (rawModified) throw new IllegalArgumentException("Apply or discard the JSON buffer before preview.");
            if (document.kind() == DefinitionKind.RESOURCE) {
                JsonElement selected = JsonPointers.get(documentCache.json(document), node);
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
            }, view != 3, coverage.observe(document), com.jvn.villagerretaliation.authoring.preview.PreviewWordPools.lookupDefinitions(
                    workspace, document, locale.get(), AuthoringDialoguePreview::defaultWordPool));
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
        recordLocation();
        select(content);
        packFiles.select(path);
        openFiles.open(workspace, path);
        filesMode = true;
        saveViewPreferences();
        if (content == null) {
            var remembered = navigation.remembered(workspace, null, path);
            workScroll = remembered == null ? 0 : remembered.scroll();
            detailsScroll = remembered == null ? 0 : remembered.detailsScroll();
            restoreWorkScroll = workScroll; restoreDetailsScroll = detailsScroll;
        }
        view = content == null ? 4 : 0;
        focusPanel = WORK_PANEL;
    }

    private String activePath() {
        return view == 4 ? packFiles.selected() : document == null ? "" : document.resourcePath();
    }

    private void recordLocation() {
        navigation.record(workspace, currentLocation());
        if (workspace != null && !activePath().isEmpty()) openFiles.open(workspace, activePath());
    }

    private EditorNavigation.Location currentLocation() {
        return new EditorNavigation.Location(document, packFiles.selected(), view, filesMode, pointer.get(), panX, panY, zoom, workScroll, detailsScroll);
    }

    private void saveSession() throws IOException {
        if (workspace == null || showRecovery) return;
        recordLocation();
        sessions.save(workspace, EditorSession.snapshot(workspace, navigation, openFiles, currentLocation(), snapGrid));
    }

    private void restoreSession(boolean active) throws IOException {
        JsonObject saved = sessions.read(workspace);
        var recent = saved.has("recent") ? saved.getAsJsonArray("recent").asList().stream().map(JsonElement::getAsString).toList() : List.<String>of();
        navigation.restore(workspace, EditorSession.positions(workspace, saved), recent);
        openFiles.restore(workspace, saved.has("open_files") ? saved.getAsJsonArray("open_files") : new JsonArray());
        snapGrid = saved.has("snap_grid") && saved.get("snap_grid").getAsBoolean();
        if (!active || !saved.has("current")) return;
        var current = EditorSession.location(workspace, saved.getAsJsonObject("current"));
        if (current == null) return;
        if (current.document() != null) select(current.document());
        else if (current.view() == 4) openPackFile(current.currentPath());
        filesMode = current.filesMode();
        view = current.view();
        restoreDocumentPosition(current);
        focusPanel = WORK_PANEL;
    }

    private void navigateHistory(int direction) {
        recordLocation();
        var target = navigation.target(direction);
        if (target == null) return;
        if (view == 3 && target.view() != 3) leavePlaytest(target.view());
        select(target.document());
        String path = target.currentPath();
        packFiles.select(target.view() != 4 && !path.isEmpty() && !workspace.filePaths().contains(path) && !workspace.folderPaths().contains(path) ? "" : path);
        filesMode = target.filesMode();
        saveViewPreferences();
        restoreDocumentPosition(target);
        if (target.view() == 3 && view != 3) enterPlaytest();
        else view = target.view();
        focusPanel = view == 3 ? "Playtest###VR_Playtest" : WORK_PANEL;
        navigation.accept(target);
        status = (direction < 0 ? "Back to " : "Forward to ") + target.label() + ".";
    }

    private void openFolder(String path) {
        if (!path.isEmpty() && !workspace.folderPaths().contains(path)) throw new IllegalArgumentException("Folder no longer exists: " + path);
        if (view == 3) leavePlaytest(4);
        openPackFile(path); view = 4;
        status = path.isEmpty() ? "Opened pack root." : "Opened folder: " + path;
    }

    private void navigateUp() {
        if (!activePath().isEmpty()) openFolder(EditorNavigation.parent(activePath()));
    }

    private void navigationControls() {
        float width = ImGui.calcTextSize("BackForwardUndoRedo").x + ImGui.getStyle().getFramePaddingX() * 8
                + ImGui.getStyle().getItemSpacingX() * 3;
        ImGui.sameLine();
        if (ImGui.getContentRegionAvailX() < width) ImGui.newLine();
        ImGui.setCursorPosX(ImGui.getCursorPosX() + Math.max(0, ImGui.getContentRegionAvailX() - width));
        ImGui.beginDisabled(showRecovery || confirmClose || EditorTestWorld.applying());
        var back = navigation.target(-1);
        ImGui.beginDisabled(back == null);
        if (ImGui.button("Back##navigation_back")) operation(() -> navigateHistory(-1));
        ImGui.endDisabled();
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) ImGui.setTooltip("Back (" + keyHint("Navigate back") + ")" + (back == null ? "" : "\n" + back.label()));
        ImGui.sameLine();
        var forward = navigation.target(1);
        ImGui.beginDisabled(forward == null);
        if (ImGui.button("Forward##navigation_forward")) operation(() -> navigateHistory(1));
        ImGui.endDisabled();
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) ImGui.setTooltip("Forward (" + keyHint("Navigate forward") + ")" + (forward == null ? "" : "\n" + forward.label()));
        ImGui.sameLine();
        AuthoringDocument history = historyDocument == null ? document : historyDocument;
        ImGui.beginDisabled(history == null || !history.canUndo() || rawModified);
        if (ImGui.button("Undo")) operation(() -> changeHistory(false));
        ImGui.endDisabled();
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) ImGui.setTooltip("Undo" + (history == null || history.undoLabel().isEmpty() ? "" : ": " + history.undoLabel()) + " (" + keyHint("Undo") + ")");
        ImGui.sameLine();
        ImGui.beginDisabled(history == null || !history.canRedo() || rawModified);
        if (ImGui.button("Redo")) operation(() -> changeHistory(true));
        ImGui.endDisabled();
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) ImGui.setTooltip("Redo" + (history == null || history.redoLabel().isEmpty() ? "" : ": " + history.redoLabel()) + " (" + keyHint("Redo") + ")");
        ImGui.endDisabled();
    }

    private void breadcrumbs() {
        if (workspace == null) { ImGui.textDisabled("No workspace open"); return; }
        boolean dirty = workspace.dirty() || hasPendingFields() || rawModified;
        String path = activePath();
        String root = java.util.Objects.toString(workspace.directory().getFileName(), workspace.directory().toString());
        if (ImGui.smallButton(shortText(root, 24) + "##pack_root_path")) operation(() -> openFolder(""));
        if (ImGui.isItemHovered()) ImGui.setTooltip("Open pack root\nDraft workspace: " + workspace.directory());
        var ancestors = EditorNavigation.ancestors(path);
        float width = ImGui.calcTextSize(path).x + ancestors.size() * 32 + 25 + (dirty ? 120 : 0);
        float available = ImGui.getCursorScreenPosX() + ImGui.getContentRegionAvailX() - ImGui.getItemRectMaxX() - 8;
        if (width > available && !ancestors.isEmpty()) {
            ImGui.sameLine(); ImGui.textDisabled("/"); ImGui.sameLine();
            if (ImGui.smallButton("...##path_parents")) ImGui.openPopup("Containing folders##path_parents");
            if (ImGui.beginPopup("Containing folders##path_parents")) {
                for (String folder : ancestors) if (ImGui.menuItem(folder)) operation(() -> openFolder(folder));
                ImGui.endPopup();
            }
        } else for (String folder : ancestors) {
            ImGui.sameLine(); ImGui.textDisabled("/"); ImGui.sameLine();
            if (ImGui.smallButton(folder.substring(folder.lastIndexOf('/') + 1) + "##path:" + folder)) operation(() -> openFolder(folder));
            if (ImGui.isItemHovered()) ImGui.setTooltip(folder);
        }
        if (!path.isEmpty()) {
            ImGui.sameLine(); ImGui.textDisabled("/"); ImGui.sameLine();
            int limit = Math.max(4, (int) ((ImGui.getContentRegionAvailX() - (dirty ? 120 : 0)) / ImGui.calcTextSize("M").x));
            ImGui.textDisabled(shortText(path.substring(path.lastIndexOf('/') + 1), limit));
            if (ImGui.isItemHovered()) ImGui.setTooltip(path);
        }
        if (dirty) { ImGui.sameLine(); ImGui.textDisabled("(unsaved)"); }
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
        ImGui.separator();
        if (ImGui.checkbox("Errors", diagnosticErrors)) diagnosticErrors = !diagnosticErrors;
        EditorWidgets.sameLineFor(ImGui.getFrameHeight() + ImGui.getStyle().getItemInnerSpacingX() + ImGui.calcTextSize("Warnings").x);
        if (ImGui.checkbox("Warnings", diagnosticWarnings)) diagnosticWarnings = !diagnosticWarnings;
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##diagnostic_filter", "Filter issues by file, field or message...", diagnosticFilter);
        diagnosticNavigation();
        var visible = visibleDiagnostics();
        String previousPath = null;
        boolean expanded = false;
        for (int i = 0; i < visible.size(); i++) {
            var diagnostic = visible.get(i);
            if (!diagnostic.resourcePath().equals(previousPath)) {
                previousPath = diagnostic.resourcePath();
                long count = visible.stream().filter(issue -> issue.resourcePath().equals(diagnostic.resourcePath())).count();
                ImGui.setNextItemOpen(true, ImGuiCond.Once);
                String label = diagnostic.resourcePath().isEmpty() ? "Workspace" : diagnostic.resourcePath();
                expanded = EditorWidgets.section(label + " (" + count + ")###issues:" + diagnostic.resourcePath());
                if (ImGui.isItemHovered()) ImGui.setTooltip(label);
            }
            if (!expanded) continue;
            ImGui.pushID(i);
            if (ImGui.selectable(diagnostic.severity() + ": " + diagnostic.message() + "##issue", diagnostic.equals(activeDiagnostic)))
                operation(() -> openDiagnostic(diagnostic));
            var owner = workspace.documents().stream().filter(value -> value.resourcePath().equals(diagnostic.resourcePath())).findFirst().orElse(null);
            EditorQuickFixes.render(workspace, owner, diagnostic, this::operation, () -> { historyDocument = owner; refresh(); });
            ImGui.textDisabled(diagnostic.pointer().isEmpty() ? "File settings" : diagnostic.pointer());
            if (ImGui.isItemHovered()) ImGui.setTooltip(diagnostic.message() + "\n" + diagnostic.resourcePath() + diagnostic.pointer());
            ImGui.popID();
        }
        if (visible.isEmpty()) ImGui.textWrapped(diagnostics.isEmpty() ? "No validation issues." : "No issues match these filters.");
        ImGui.end();
    }

    private List<AuthoringDiagnostic> visibleDiagnostics() {
        return EditorDiagnostics.matching(diagnostics, diagnosticErrors, diagnosticWarnings, diagnosticFilter.get());
    }

    private void diagnosticNavigation() {
        var visible = visibleDiagnostics();
        ImGui.beginDisabled(visible.isEmpty());
        if (ImGui.button("Previous issue")) operation(() -> openDiagnostic(EditorDiagnostics.next(visibleDiagnostics(), activeDiagnostic, -1)));
        sameLineFor("Next issue");
        if (ImGui.button("Next issue")) operation(() -> openDiagnostic(EditorDiagnostics.next(visibleDiagnostics(), activeDiagnostic, 1)));
        ImGui.endDisabled();
        int index = visible.indexOf(activeDiagnostic);
        ImGui.textDisabled(index < 0 ? visible.size() + " matching issues" : "Issue " + (index + 1) + " of " + visible.size());
    }

    private void openDiagnostic(AuthoringDiagnostic diagnostic) {
        if (diagnostic == null) return;
        if (diagnostic.resourcePath().isEmpty()) { view = 2; focusPanel = WORK_PANEL; }
        else if (diagnostic.resourcePath().equals("pack.mcmeta")) openPackFile("pack.mcmeta");
        else {
            var owner = workspace.documents().stream().filter(candidate -> candidate.resourcePath().equals(diagnostic.resourcePath())).findFirst().orElse(null);
            if (owner == null) throw new IllegalStateException("The file for this issue is no longer available");
            select(owner);
            var location = EditorDiagnosticLocation.resolve(owner, diagnostic.pointer());
            setPointer(location.pointer()); selectedNode = location.nodeId();
            nodeSelection.clear();
            if (!selectedNode.isEmpty()) nodeSelection.add(selectedNode);
            graphFocus = selectedNode; fitGraph = false;
            showResources = owner.kind() == DefinitionKind.RESOURCE || showResources;
            view = 0; focusPanel = DETAIL_PANEL;
        }
        activeDiagnostic = diagnostic;
        status = diagnostic.resourcePath() + diagnostic.pointer() + ": " + diagnostic.message();
    }

    private void writeRecovery() throws IOException {
        saveSession();
        if (workspace == null || showRecovery) return;
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
        EditorRecovery.write(workspace, buffers, hasPendingFields() || rawModified);
    }

    private void recoveryControls() {
        if (showRecovery) {
            ImGui.textWrapped("Recovered drafts are available. Choose a version before continuing.");
            if (ImGui.button("Recover drafts")) attempt(() -> {
                workspace = AuthoringWorkspace.recover(workspace.directory());
                restoreSession(false);
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
            EditorWidgets.sameLineFor("Keep saved version");
            if (ImGui.button("Keep saved version")) attempt(() -> { workspace.discardRecovery(); showRecovery = false; restoreSession(true); });
        }
        if (confirmClose) {
            ImGui.textWrapped("You have unsaved drafts. Save them or keep a recovery copy before closing.");
            if (ImGui.button("Save and close")) operation(() -> { save(); closeEditor(); });
            EditorWidgets.sameLineFor("Keep recovery and close");
            if (ImGui.button("Keep recovery and close")) attempt(() -> { writeRecovery(); closeEditor(); });
            EditorWidgets.sameLineFor("Continue editing");
            if (ImGui.button("Continue editing")) confirmClose = false;
        }
    }

    private void closeEditor() {
        attempt(this::writeRecovery);
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
        validate(); focusFirstError();
        status = "Drafts saved.";
    }

    private void validate() {
        searchQuery = "";
        if (workspace != null) diagnostics = validationCache.get(workspace);
    }

    private void select(AuthoringDocument next) {
        if (rawModified) {
            status = "Apply or discard the JSON buffer before changing selection.";
            return;
        }
        if (document == next && next != null) return;
        boolean changed = document != next;
        if (changed) {
            recordLocation();
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
        fitGraph = true; fitSelection = false; boxSelecting = false;
        historyDocument = next;
        selectedNode = "";
        nodeSelection.clear();
        selectedEdge = "";
        draggingNode = null;
        nodeResize = null;
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
        if (next != null) { packFiles.select(next.resourcePath()); openFiles.open(workspace, next.resourcePath()); }
        refresh();
        if (changed) {
            var remembered = navigation.remembered(workspace, next, next == null ? "" : next.resourcePath());
            workScroll = remembered == null ? 0 : remembered.scroll();
            detailsScroll = remembered == null ? 0 : remembered.detailsScroll();
            restoreWorkScroll = workScroll; restoreDetailsScroll = detailsScroll;
            if (remembered != null) restoreDocumentPosition(remembered);
        }
    }

    private void restoreDocumentPosition(EditorNavigation.Location location) {
        if (document != null) {
            var resolved = EditorDiagnosticLocation.resolve(document, location.pointer());
            selectedNode = resolved.nodeId(); pointer.set(resolved.pointer());
            nodeSelection.clear();
            if (!selectedNode.isEmpty()) nodeSelection.add(selectedNode);
        }
        panX = location.panX(); panY = location.panY(); zoom = location.zoom();
        fitGraph = false; graphFocus = "";
        workScroll = location.scroll(); restoreWorkScroll = workScroll;
        detailsScroll = location.detailsScroll(); restoreDetailsScroll = detailsScroll;
    }

    private void changeHistory(boolean redo) {
        AuthoringDocument history = historyDocument == null ? document : historyDocument;
        String label = redo ? history.redoLabel() : history.undoLabel();
        boolean changed = redo ? history.redo() : history.undo();
        if (!changed) throw new IllegalStateException(redo ? "No change can be redone." : "No change can be undone.");
        refresh();
        status = (redo ? "Redid: " : "Undid: ") + label + ".";
    }

    private void refresh() {
        searchQuery = "";
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
        nodeResize = null;
        if (document != null) {
            JsonElement savedPositions = documentCache.metadata(document).get("node_positions");
            if (documentCache.metadata(document).has("graph_card_layout") && savedPositions != null && savedPositions.isJsonObject()) {
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
            jsonText.set(JSON.toJson(documentCache.json(document)));
            if (document.kind() == DefinitionKind.RESOURCE && dialoguePool.supports(document, documentCache.json(document))) {
                try { JsonPointers.get(documentCache.json(document), pointer.get()); }
                catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException exception) { pointer.set(""); }
            }
            try {
                var ids = documentCache.nodes(document).stream().map(DialogueGraph.Node::id).collect(java.util.stream.Collectors.toSet());
                nodeSelection.retainAll(ids);
                if (!selectedNode.isEmpty() && !ids.contains(selectedNode)) {
                    playtestStepRemoved = view == 3;
                    var entry = documentCache.nodes(document).stream().filter(DialogueGraph.Node::entry).findFirst();
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
            EditorGraphSizing.positions(metadata, movedPositions);
        });
    }

    private void replaceValue(String path, JsonElement value) {
        document.edit("Edit " + path, root -> JsonPointers.set(root, path, value));
        refresh();
    }

    private void editArray(String path, java.util.function.Consumer<JsonArray> edit) {
        operation(() -> {
            EditorArrayOrder.edit(document, path, "Edit " + path, edit);
            refresh();
        });
    }

    private void operation(EditorOperation action) {
        AuthoringDocument expected = document;
        attempt(() -> {
            if (expected != document) throw new IllegalStateException("Selection changed before the operation could run.");
            if (rawModified) throw new IllegalStateException("Apply or discard the JSON buffer before other operations.");
            applyPendingFields();
            JsonObject before = document == null ? null : documentCache.json(document);
            JsonObject beforeMetadata = document == null ? null : documentCache.metadata(document);
            action.run();
            if (document != null && document == expected
                    && (!documentCache.json(document).equals(before) || !documentCache.metadata(document).equals(beforeMetadata))) historyDocument = document;
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
            if (worldContent != null) return;
            if (showRecovery) { closeEditor(); return; }
            if (workspace != null && (workspace.dirty() || hasPendingFields() || rawModified)) confirmClose = true;
            else closeEditor();
        }
    }
}
