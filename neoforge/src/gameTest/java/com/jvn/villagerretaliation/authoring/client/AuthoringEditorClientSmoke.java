package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen;
import com.mojang.blaze3d.platform.NativeImage;
import foundry.imgui.api.ImGuiMCEvents;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import java.nio.ByteOrder;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class AuthoringEditorClientSmoke {
    private static final String OUTPUT = "villagerretaliation.authoringEditorClientSmoke";
    private static final String GREETING = "<i><b><u><wave><color:gold>Welcome</color></wave></u></b></i>, {player}! It is {time} ({time_of_day}), day {day}.";
    private static final String RESPONSE = "Where should we meet, {player}?";
    private static final String FOLLOW_UP = "Meet me by the village gate tomorrow.";
    private static final ArrayDeque<Runnable> STEPS = new ArrayDeque<>();
    private static Object editor;
    private static AuthoringWorkspace workspace;
    private static AuthoringDocument document;
    private static long started, resumeAt;
    private static java.util.concurrent.CompletableFuture<?> liveCheck;
    private static Path originalWorld;
    private static Path reloadWorld;
    private static AuthoringDocument playtestTitleLocale;
    private static int reloadExperience, playtestLogSize;
    private static java.util.concurrent.CompletableFuture<Void> reloadCheck;
    private static int frames;
    private static boolean finished, installed, mouseDown, previousDown, stepping, mouseDoubleClicked;
    private static boolean cameraGuiBefore;
    private static float mouseX = -100, mouseY = -100, previousX = -100, previousY = -100, downX, downY;
    private static float mouseWheel;
    private static float[] canvas;
    private static float[] originalPosition;
    private static com.jvn.villagerretaliation.client.interaction.DialogueCameraTimeline.Pose cameraBefore;

    private AuthoringEditorClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || finished) return;
        var minecraft = Minecraft.getInstance();
        if (started == 0) started = Util.getMillis();
        require(Util.getMillis() - started < 360_000, "Editor client test timed out after " + frames + " frames with "
                + STEPS.size() + " steps remaining on " + (minecraft.screen == null ? "the game" : minecraft.screen.getClass().getSimpleName()) + ".");
        long window = minecraft.getWindow().getWindow();
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE) GLFW.glfwShowWindow(window);
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) GLFW.glfwRestoreWindow(window);
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_FALSE) GLFW.glfwFocusWindow(window);
        minecraft.setWindowActive(true);
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.screen instanceof com.jvn.villagerretaliation.client.BetaWarningScreen warning) {
            warning.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(button -> button.active && button.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable(
                            "screen.villagerretaliation.beta_warning.acknowledge").getString())).findFirst().ifPresent(net.minecraft.client.gui.components.Button::onPress);
            return;
        }
        if (installed && minecraft.screen instanceof com.jvn.villagerretaliation.client.campaign.CampaignSelectScreen selection) {
            var campaigns = (java.util.List<?>) field(selection, "campaigns");
            var campaign = campaigns.stream().map(com.jvn.villagerretaliation.campaign.selection.WorldCampaign.class::cast)
                    .filter(value -> value.id().equals(com.jvn.villagerretaliation.campaign.selection.WorldCampaign.DEFAULT)).findFirst().orElseThrow();
            selection.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast).filter(button -> button.getMessage().getString().equals(campaign.name()))
                    .findFirst().orElseThrow().onPress();
            selection.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast).filter(button -> button.active && button.getMessage().getString().equals(
                            net.minecraft.network.chat.Component.translatable("selectWorld.create").getString())).findFirst().orElseThrow().onPress();
            return;
        }
        if (installed || minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        installed = true;
        minecraft.options.guiScale().set(3);
        minecraft.resizeDisplay();
        GLFW.glfwMaximizeWindow(minecraft.getWindow().getWindow());
        try {
            var server = minecraft.getSingleplayerServer();
            var playerId = minecraft.player.getUUID();
            server.execute(() -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayer(playerId);
                level.setDayTime(0);
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
                villager.setNoAi(true);
                villager.setNoGravity(true);
                villager.setInvulnerable(true);
                villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 2);
                level.addFreshEntity(villager);
            });
            workspace = AuthoringWorkspace.create(output().resolve("drafts"));
            document = workspace.createDocument(DefinitionKind.TREE, "test:editor_smoke");
            DialogueGraph.addNode(document, "finish");
            DialogueGraph.addNode(document, "other");
            DialogueGraph.addResponse(document, "start", "continue", "Tell me more.", "finish");
            DialogueGraph.addResponse(document, "start", "other", "Something else.", "other");
            EditorBlocks.append(document, "/nodes/start", EditorBlocks.Kind.REPUTATION_GATE);
            EditorBlocks.append(document, "/nodes/finish", EditorBlocks.Kind.EXPERIENCE);
            editor = field(null, "INSTANCE");
            set("workspace", workspace);
            set("status", "Test workspace opened.");
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            ImGuiAuthoringEditor.open();
            buildSteps();
            ImGuiMCEvents.INSTANCE.preRenderImGuiEvent(AuthoringEditorClientSmoke::frame);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void buildSteps() {
        STEPS.add(() -> {
            try { EditorClientChecks.questSimulationUsesRuntimeCompletionRules(); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        STEPS.add(() -> {
            require((int) field(editor, "guideStep") == 0, "First open shows the guide.");
            capture("01-first-open");
        });
        click(() -> toolbar("Hide guide"));
        STEPS.add(() -> {
            require((int) field(editor, "guideStep") == -1, "Hide guide button dismisses onboarding.");
            try {
                require(EditorPreferences.load(output().resolve("config/authoring-editor.json")).guideDismissed(), "Guide dismissal persists.");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            capture("02-automatic-layout");
            locateCanvas();
        });
        buildCampaignSteps();
        click(() -> point("start", 80, EditorGraph.HEADER + EditorGraph.ROW + 12));
        STEPS.add(() -> require(((imgui.type.ImString) field(editor, "pointer")).get().equals("/nodes/start/lines"),
                "Clicking the Say block opens its editable lines."));
        click(() -> port("start"));
        STEPS.add(() -> {
            require(field(editor, "selectedEdge").equals("/nodes/start/responses/0/next"), "Clicking an output arms its connection.");
            float[] previousCanvas = canvas.clone();
            locateCanvas();
            require(Math.abs(canvas[0] - previousCanvas[0]) < 1 && Math.abs(canvas[1] - previousCanvas[1]) < 1,
                    "Arming a connection keeps the canvas stationary.");
        });
        click(() -> point("other", 0, EditorGraph.HEADER / 2));
        STEPS.add(() -> {
            require(DialogueGraph.edges(document).getFirst().to().equals("other"), "Input click reconnects the actual response.");
            require(field(editor, "selectedEdge").equals(""), "Successful connection clears the pending wire.");
            capture("03-connected-blocks");
            invoke("changeHistory", new Class<?>[] {boolean.class}, false);
            require(DialogueGraph.edges(document).getFirst().to().equals("finish"), "Undo restores the connection.");
            invoke("changeHistory", new Class<?>[] {boolean.class}, true);
            require(DialogueGraph.edges(document).getFirst().to().equals("other"), "Redo restores the new connection.");
        });
        STEPS.add(() -> originalPosition = positions().get("finish").clone());
        move(() -> point("finish", 100, 18), false);
        move(() -> point("finish", 100, 18), true);
        STEPS.add(() -> { mouseX += 45; mouseY += 30; });
        STEPS.add(() -> mouseDown = false);
        STEPS.add(() -> {
            float[] after = positions().get("finish");
            require(after[0] > originalPosition[0] + 10 && after[1] > originalPosition[1] + 10, "Dragging a header moves its node.");
            require(document.metadata().has("node_positions"), "Drag saves node positions.");
            capture("04-moved-node");
        });
        move(() -> port("start"), false);
        move(() -> port("start"), true);
        move(() -> point("finish", 0, EditorGraph.HEADER / 2), true);
        STEPS.add(() -> mouseDown = false);
        STEPS.add(() -> {
            require(DialogueGraph.edges(document).getFirst().to().equals("finish"), "Dragging an output to an input reconnects the branch.");
            capture("05-drag-connected");
        });
        click(() -> toolbar("Quick guide"));
        STEPS.add(() -> {
            require((int) field(editor, "guideStep") == 0, "Quick guide reopens onboarding.");
            capture("06-guide-reopened");
        });
        click(() -> toolbar("Hide guide"));
        click(() -> toolbar("Preview"));
        STEPS.add(() -> {
            require((int) field(editor, "view") == 1, "Preview toolbar control switches the rendered view.");
            require(((Map<?, ?>) field(field(editor, "graphText"), "previews")).isEmpty(),
                    "Leaving the graph releases its styled preview textures.");
            capture("07-preview");
        });
        click(() -> toolbar("Check & export"));
        STEPS.add(() -> {
            require((int) field(editor, "view") == 2, "Check toolbar control switches the rendered view.");
            capture("08-validation");
        });
        buildViewportSteps();
        buildSetupSteps();
    }

    private static void buildViewportSteps() {
        STEPS.add(() -> invoke("enterPlaytest", new Class<?>[0]));
        STEPS.add(() -> {
            var mc = Minecraft.getInstance();
            var layout = EditorPlaytestViewport.layout();
            require(layout != null && layout.width() < mc.getWindow().getWidth(), "Playtest renders at the left pane size.");
            require(mc.screen.width == layout.guiWidth(), "Native screens use the game pane GUI width.");
            require(mc.getMainRenderTarget().width == mc.getWindow().getWidth(), "ImGui retains the physical framebuffer.");
            capture("08a-playtest-split");
            set("playtestFraction", .55F);
        });
        STEPS.add(() -> capture("08b-playtest-resized"));
        STEPS.add(() -> {
            Minecraft.getInstance().setScreen(new com.jvn.villagerretaliation.client.interaction.VillagerQuestJournalScreen());
        });
        STEPS.add(() -> {
            require(EditorPlaytestViewport.active(), "Opening the quest journal retains Playtest.");
            capture("08c-playtest-journal");
            invoke("leavePlaytest", new Class<?>[] {int.class}, 2);
        });
        STEPS.add(() -> require(!EditorPlaytestViewport.active()
                && Minecraft.getInstance().screen.width == Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                "Leaving Playtest restores native screen dimensions."));
    }

    private static void buildSetupSteps() {
        click(() -> toolbar("Workspace"));
        replaceText(() -> screenPoint(120, 277), () -> output().resolve("setup-drafts").toString());
        click(() -> screenPoint(200, 317));
        STEPS.add(() -> {
            workspace = (AuthoringWorkspace) field(editor, "workspace");
            require(workspace.directory().equals(output().resolve("setup-drafts")), "Create new opens the typed workspace folder.");
            require(workspace.documents().isEmpty(), "The new workspace starts empty.");
            capture("09-empty-workspace");
        });
        replaceText(() -> screenPoint(120, 357), () -> "test:from_scratch");
        click(() -> screenPoint(120, 397));
        STEPS.add(() -> {
            document = (AuthoringDocument) field(editor, "document");
            require(document != null && document.id().equals("test:from_scratch"), "Create makes the typed dialogue ID.");
            require(DialogueGraph.nodes(document).size() == 1, "New dialogue starts with one node.");
            locateCanvas();
            capture("10-created-dialogue");
        });
        editLine("start", "An initial draft.");
        buildRichTextSteps();
        click(() -> screenPoint(320, 181));
        STEPS.add(() -> {
            require(DialogueGraph.nodes(document).size() == 2, "Add node creates a follow-up.");
            locateCanvas();
        });
        editLine("node_2", FOLLOW_UP);
        click(() -> point("start", 80, 18));
        click(() -> screenPoint(430, 181));
        replaceText(() -> screenPoint(450, 330), () -> RESPONSE);
        click(() -> screenPoint(450, 417));
        click(() -> screenPoint(320, 515));
        click(() -> screenPoint(350, 457));
        STEPS.add(() -> {
            require(DialogueGraph.edges(document).size() == 1, "Add connection creates a response.");
            require(DialogueGraph.edges(document).getFirst().to().equals("node_2"), "The response leads to the chosen node.");
            require(document.json().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("responses")
                    .get(0).getAsJsonObject().get("label").getAsString().equals(RESPONSE), "The response keeps the typed wording.");
        });
        click(() -> screenPoint(655, 181));
        click(() -> toolbar("Save"));
        STEPS.add(() -> {
            require(!document.dirty(), "Save persists the authored dialogue.");
            capture("11-authored-flow");
        });
        buildStyledGraphSteps();
        click(() -> toolbar("Preview"));
        click(() -> screenPoint(550, 209));
        STEPS.add(() -> require(field(editor, "target") instanceof net.minecraft.world.entity.npc.Villager,
                "Nearest villager selects a real preview target."));
        click(() -> screenPoint(500, 345));
        click(() -> screenPoint(400, 443));
        STEPS.add(() -> require(((com.jvn.villagerretaliation.authoring.preview.PreviewConditions) field(editor, "conditions")).dayTime() == 6000,
                "Preview time menu selects afternoon."));
        click(() -> screenPoint(350, 385));
        pause(1000);
        STEPS.add(() -> require(AuthoringDialoguePreview.active(), "Play from entry opens the native conversation screen."));
        choosePreview("Talk");
        pause(2500);
        STEPS.add(() -> {
            require(field(Minecraft.getInstance().screen, "villagerDialogueText").equals(previewGreeting("12:00", "afternoon")), "Preview resolves player and time variables.");
            var segments = (List<?>) field(Minecraft.getInstance().screen, "villagerDialogueTextSegments");
            var first = (com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment) segments.getFirst();
            require(first.text().equals("Welcome") && first.effects().italic() && first.effects().bold()
                    && first.effects().underlined() && first.effects().wavy() && Integer.valueOf(0xffaa00).equals(first.effects().color()),
                    "Native preview retains italic, bold, underline, wave and gold styling.");
            capture("12-preview-greeting");
        });
        choosePreview(() -> "Where should we meet, " + Minecraft.getInstance().player.getName().getString() + "?");
        pause(2500);
        STEPS.add(() -> {
            require(field(Minecraft.getInstance().screen, "villagerDialogueText").equals(FOLLOW_UP), "Preview follows the authored connection.");
            capture("13-preview-follow-up");
        });
        choosePreview("Return to editor");
        STEPS.add(() -> {
            require(!AuthoringDialoguePreview.active(), "Return to editor closes the preview.");
            require(Minecraft.getInstance().screen == field(editor, "screen"), "The editor is restored after preview.");
        });
        click(() -> screenPoint(500, 345));
        click(() -> screenPoint(400, 499));
        click(() -> screenPoint(520, 385));
        pause(2500);
        STEPS.add(() -> {
            require(field(Minecraft.getInstance().screen, "villagerDialogueText").equals(previewGreeting("00:00", "night")),
                    "Selected-node preview uses the changed time override.");
            require(Minecraft.getInstance().level.getDayTime() == 0, "Preview time overrides never change the live world clock.");
            capture("13b-preview-night");
        });
        choosePreview("Return to editor");
        buildCameraSteps();
        click(() -> toolbar("Check & export"));
        click(() -> screenPoint(340, 209));
        replaceText(() -> screenPoint(600, 305), () -> output().resolve("setup-export").toString());
        click(() -> screenPoint(350, 345));
        STEPS.add(() -> {
            require(field(editor, "status").toString().startsWith("Exported datapack to "), "Save and export reports success.");
            try {
                Path exported = output().resolve("setup-export");
                require(Files.isRegularFile(exported.resolve("pack.mcmeta")), "Export writes pack metadata.");
                var json = JsonParser.parseString(Files.readString(exported.resolve(document.resourcePath()))).getAsJsonObject();
                require(json.equals(JsonParser.parseString(document.json().toString())), "Exported JSON exactly matches the edited document.");
                require(com.jvn.villagerretaliation.dialogue.DialogueCamera.read(json.getAsJsonObject("nodes").getAsJsonObject("start"))
                        .equals(com.jvn.villagerretaliation.dialogue.DialogueCamera.read(document.json().getAsJsonObject("nodes").getAsJsonObject("start"))),
                        "Export preserves the runtime camera pose and timing.");
                require(json.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").get(0).getAsString().equals(GREETING),
                        "The exported greeting contains the final edit.");
                require(Files.isRegularFile(workspace.directory().resolve("workspace.json")), "Export also saves the workspace.");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            capture("14-exported-pack");
        });
        click(() -> toolbar("Workspace"));
        click(() -> screenPoint(85, 317));
        STEPS.add(() -> {
            workspace = (AuthoringWorkspace) field(editor, "workspace");
            require(workspace.documents().size() == 1, "Open folder reloads the saved workspace.");
        });
        click(() -> screenPoint(140, 267));
        click(() -> toolbar("Edit"));
        STEPS.add(() -> {
            document = (AuthoringDocument) field(editor, "document");
            require(document != null && document.id().equals("test:from_scratch"), "Reopened content can be selected.");
            require(document.json().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").get(0).getAsString().equals(GREETING),
                    "Reopening preserves the final wording.");
            require(DialogueGraph.edges(document).getFirst().to().equals("node_2"), "Reopening preserves the connection.");
            capture("15-reopened-draft");
        });
        buildPlaytestDialogueSteps();
        buildImportSteps();
        buildCreatorToolsSteps();
        buildProgressConditionSteps();
        STEPS.add(() -> {
            try {
                Files.writeString(output().resolve("result.txt"),
                        "Authoring editor client smoke passed\nGraph interactions, automatic layout, onboarding, and full setup through native controls.\n"
                        + "Created an empty workspace, typed a dialogue ID, wrote and revised text, added a node and response, saved, played the entry and response, returned to the editor, checked drafts, exported, and reopened the saved draft. Variables, formatting, response substitution, afternoon and midnight previews, free-camera positioning, shot capture, camera restoration, import protection, and reimport into a new workspace. Separate world creation, automatic test pack activation, real inventory objectives, live entry actions and rewards, original world preservation, and stale snapshot rejection.\n");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            finished = true;
            Minecraft.getInstance().stop();
        });
    }

    private static void buildPlaytestDialogueSteps() {
        STEPS.add(() -> invoke("enterPlaytest", new Class<?>[0]));
        STEPS.add(() -> invoke("startPreview", new Class<?>[] {String.class}, "start"));
        pause(500);
        STEPS.add(() -> {
            require(AuthoringDialoguePreview.active() && EditorPlaytestViewport.active(), "Native dialogue keeps the editor beside it.");
            require(Minecraft.getInstance().screen.width == EditorPlaytestViewport.layout().guiWidth(), "Dialogue reflows inside the game viewport.");
            require(((List<?>) field(Minecraft.getInstance().screen, "options")).stream()
                    .noneMatch(option -> field(option, "label").equals("Restart preview") || field(option, "label").equals("Return to editor")),
                    "Playtest transport controls stay in the editor pane.");
            capture("15a-playtest-dialogue");
            playtestLogSize = AuthoringDialoguePreview.log().size();
            document.edit("Revise live wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("start")
                    .getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Updated dialogue for {player}")));
        });
        pause(400);
        STEPS.add(() -> {
            require(field(Minecraft.getInstance().screen, "villagerDialogueText").equals("Updated dialogue for " + Minecraft.getInstance().player.getName().getString()),
                    "Text edits refresh the current native dialogue automatically.");
            require(AuthoringDialoguePreview.log().size() == playtestLogSize, "Visual refresh does not replay actions.");
            capture("15b-playtest-live-text");
            document.undo();
        });
        pause(400);
        STEPS.add(() -> {
            AuthoringDialoguePreview.choose(0);
        });
        STEPS.add(() -> {
            require(AuthoringDialoguePreview.cursor().previous(), "Dialogue navigation records the previous exchange.");
            AuthoringDialoguePreview.previous();
            require(AuthoringDialoguePreview.cursor().node().equals("start"), "Dialogue can return to the previous node.");
            invoke("leavePlaytest", new Class<?>[] {int.class}, 0);
        });
    }

    private static void buildCampaignSteps() {
        var originalWorkspace = new AuthoringWorkspace[1];
        var originalDocument = new AuthoringDocument[1];
        var originalName = new String[1];
        STEPS.add(() -> {
            originalWorkspace[0] = workspace;
            originalDocument[0] = document;
            originalName[0] = ((imgui.type.ImString) field(editor, "newId")).get();
            try {
                workspace = AuthoringWorkspace.create(output().resolve("addon-drafts"));
                document = workspace.createDocument(DefinitionKind.CAMPAIGN_ADDON, "test:editor_addon");
                document.edit("Name addon", value -> value.addProperty("name", "Village Stories"));
                Path description = output().resolve("addon-description.md");
                Files.writeString(description, "# Village Stories\nAn optional adventure for your campaign.");
                workspace.importCampaignDescription(document, description);
                Path image = output().resolve("addon-image.png");
                try (var stream = AuthoringEditorClientSmoke.class.getResourceAsStream("/villager_retaliation_cover.png")) { Files.write(image, stream.readAllBytes()); }
                workspace.importCampaignImage(document, image);
                set("workspace", workspace);
                invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        pause(250);
        STEPS.add(() -> {
            require(field(editor, "campaignPreview") != null, "Addon artwork renders in the native editor.");
            capture("02a-addon-card");
            invoke("setCreateScope", new Class<?>[] {AuthoringDocument.class}, document);
            set("createKind", DefinitionKind.QUEST);
            ((imgui.type.ImString) field(editor, "newId")).set("test:addon_quest");
            set("showCreate", true);
        });
        pause(250);
        STEPS.add(() -> {
            capture("02b-addon-content-creation");
            invoke("createContent", new Class<?>[] {DefinitionKind.class}, DefinitionKind.QUEST);
            require(workspace.documents().stream().anyMatch(value -> value.resourcePath().equals(
                    "data/test/campaign_addons/editor_addon/quests/authored/addon_quest/quest.json")), "Editor creation uses the selected addon folder.");
            workspace = originalWorkspace[0];
            document = originalDocument[0];
            set("workspace", workspace);
            set("createScope", null);
            set("createKind", DefinitionKind.TREE);
            set("showCreate", false);
            ((imgui.type.ImString) field(editor, "newId")).set(originalName[0]);
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
        });
        pause(250);
        STEPS.add(AuthoringEditorClientSmoke::locateCanvas);
    }

    private static void buildCreatorToolsSteps() {
        var original = new AuthoringDocument[1];
        STEPS.add(() -> { original[0] = document; locateCanvas(); });
        click(() -> point("start", 80, 18));
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, true));
        click(() -> point("node_2", 80, 18));
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, false));
        STEPS.add(() -> {
            require(((java.util.Set<?>) field(editor, "nodeSelection")).size() == 2, "Ctrl-click selects multiple nodes.");
            capture("19-multiselect");
        });
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.C);
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.V);
        STEPS.add(() -> {
            require(DialogueGraph.nodes(document).size() == 4, "Keyboard paste creates the selected nodes.");
            capture("20-pasted-selection");
        });
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Z);
        STEPS.add(() -> {
            require(DialogueGraph.nodes(document).size() == 2, "One undo removes the entire pasted selection.");
            set("showMinimap", true); set("showGraphTools", true);
        });
        pause(250);
        STEPS.add(() -> capture("21-graph-tools"));
        STEPS.add(() -> {
            document = workspace.createDocument(DefinitionKind.QUEST, "test:creator_tools");
            EditorContentTools.recipe(workspace, document, "Fetch quest");
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("view", 1);
        });
        click(() -> screenPoint(440, 209));
        STEPS.add(() -> {
            var preview = (com.jvn.villagerretaliation.authoring.preview.QuestPreviewSession) field(editor, "questPreview");
            require(preview != null && !preview.ready(), "Quest test starts through its native control.");
            capture("22-quest-test-blocked");
        });
        click(() -> screenPoint(400, 277));
        click(() -> screenPoint(360, 345));
        STEPS.add(() -> {
            var preview = (com.jvn.villagerretaliation.authoring.preview.QuestPreviewSession) field(editor, "questPreview");
            require(preview.outcome().equals("complete"), "Native quest controls satisfy and advance a simulated quest.");
            capture("23-quest-test-completed");
            document = original[0];
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("view", 0); set("showGraphTools", false);
            set("preferences", ((EditorPreferences) field(editor, "preferences")).withScale(1.3f));
            long handle = Minecraft.getInstance().getWindow().getWindow();
            GLFW.glfwRestoreWindow(handle); GLFW.glfwSetWindowSize(handle, 1280, 720);
        });
        pause(400);
        STEPS.add(() -> {
            Minecraft.getInstance().resizeDisplay(); capture("24-small-window-large-text");
            GLFW.glfwIconifyWindow(Minecraft.getInstance().getWindow().getWindow());
        });
        pause(250);
        STEPS.add(() -> require(GLFW.glfwGetWindowAttrib(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_FALSE,
                "The smoke harness restores a minimized test window."));
        STEPS.add(() -> ((net.minecraft.client.gui.screens.Screen) field(editor, "screen")).onClose());
        pause(250);
        STEPS.add(() -> {
            require((boolean) field(editor, "confirmClose"), "Closing dirty content asks how to preserve drafts.");
            capture("25-unsaved-close");
            set("confirmClose", false);
            try { EditorTestWorld.prepare(workspace, Minecraft.getInstance().screen); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        pause(250);
        STEPS.add(() -> {
            require(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ConfirmScreen, "Preparing a separate test world explains the world switch.");
            capture("26-test-world-setup");
            Minecraft.getInstance().screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(Minecraft.getInstance().screen == field(editor, "screen"), "Cancelling test world setup returns to the editor.");
        });
        buildLiveWorldSteps();
    }

    private static void buildLiveWorldSteps() {
        STEPS.add(() -> {
            var quest = workspace.createDocument(DefinitionKind.QUEST, "test:live_execution");
            EditorContentTools.recipe(workspace, quest, "Fetch quest");
            DialogueGraph.addNode(quest, "finish");
            DialogueGraph.connect(quest, "/stages/0/transition", "finish");
            quest.edit("Set live rewards", root -> {
                var start = root.getAsJsonArray("stages").get(0).getAsJsonObject();
                var entry = new com.google.gson.JsonArray();
                var experience = new com.google.gson.JsonObject();
                experience.addProperty("type", "experience"); experience.addProperty("amount", 7); entry.add(experience);
                start.add("on_enter", entry);
                var reward = new com.google.gson.JsonObject(); reward.addProperty("experience", 17); root.add("rewards", reward);
            });
            require(workspace.validate().stream().noneMatch(issue -> issue.severity() == com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR),
                    "The live test workspace validates before world creation: " + workspace.validate());
            document = quest;
            invoke("select", new Class<?>[] {AuthoringDocument.class}, quest);
            originalWorld = Minecraft.getInstance().getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
            require(!EditorTestWorld.active(workspace), "Ordinary worlds cannot execute editor live tests.");
            try { EditorLiveQuestTest.start(workspace, quest); throw new IllegalStateException("Live start escaped its separate test world"); }
            catch (IllegalArgumentException expected) { }
            try { EditorTestWorld.prepare(workspace, Minecraft.getInstance().screen); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            Minecraft.getInstance().tell(AuthoringEditorClientSmoke::confirmYes);
        });
        waitUntil(() -> Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen);
        STEPS.add(() -> {
            var minecraft = Minecraft.getInstance();
            require(minecraft.screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen,
                    "Confirmed setup opens the vanilla world creation screen.");
            var create = (net.minecraft.client.gui.screens.worldselection.CreateWorldScreen) minecraft.screen;
            require(create.getUiState().getSettings().dataConfiguration().dataPacks().getEnabled().contains("file/test_content"),
                    "The exported test pack is enabled automatically.");
            capture("27-test-world-ready");
            minecraft.tell(() -> create.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        });
        waitUntil(() -> {
            if (Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) Minecraft.getInstance().tell(AuthoringEditorClientSmoke::confirmYes);
            return Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null && EditorTestWorld.active(workspace);
        });
        STEPS.add(() -> {
            var minecraft = Minecraft.getInstance();
            require(!originalWorld.equals(minecraft.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize()),
                    "Live testing creates a different world directory.");
            require(Files.isRegularFile(originalWorld.resolve("level.dat")) && !Files.exists(originalWorld.resolve("datapacks/test_content")),
                    "The original world remains available without the test pack.");
            require(minecraft.getSingleplayerServer().getPackRepository().getSelectedIds().contains("file/test_content"), "The created server loaded the test pack.");
            require(EditorTestWorld.currentSnapshot(workspace), "Live execution identifies the exported snapshot.");
            EditorLiveQuestTest.start(workspace, document);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        STEPS.add(() -> {
            require(EditorLiveQuestTest.view().message().contains("Started quest"), "Live quest starts through the runtime: " + EditorLiveQuestTest.view());
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                require(player.totalExperience == 7, "Live entry actions grant real experience exactly once.");
                var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(player.serverLevel())
                        .get(playerId, net.minecraft.resources.ResourceLocation.parse(document.id()));
                require(progress != null && progress.currentStage().equals("start"), "Unmet live objectives keep the quest at its first stage.");
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 3));
            });
        });
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> liveCheck.join());
        pause(2500);
        STEPS.add(() -> EditorLiveQuestTest.inspect(workspace, document));
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        STEPS.add(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(player.serverLevel())
                        .get(playerId, net.minecraft.resources.ResourceLocation.parse(document.id()));
                require(progress != null && progress.state() == com.jvn.villagerretaliation.quest.VillagerQuestSavedData.QuestState.COMPLETED,
                        "Real inventory progress completes the live quest: " + (progress == null ? "missing" : progress.state() + "/" + progress.currentStage()));
                require(player.totalExperience == 24, "Live quest rewards execute exactly once: " + player.totalExperience);
            });
            ImGuiAuthoringEditor.open();
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("view", 0);
        });
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> liveCheck.join());
        click(() -> toolbar("Preview"));
        pause(500);
        STEPS.add(() -> {
            require((int) field(editor, "view") == 1, "Compact mode opens Preview at the configured text scale.");
            capture("28-live-quest-complete");
            document.edit("Change draft", root -> root.addProperty("revision", 2));
            require(!EditorTestWorld.currentSnapshot(workspace), "Live tests detect edits made after export.");
            try { EditorLiveQuestTest.start(workspace, document); throw new IllegalStateException("Stale snapshot accepted"); }
            catch (IllegalArgumentException expected) { }
            document.undo();
            require(EditorTestWorld.currentSnapshot(workspace), "Undo restores the exported test snapshot.");
        });
        buildPlaytestQuestSteps();
    }

    private static void buildPlaytestQuestSteps() {
        STEPS.add(() -> {
            invoke("enterPlaytest", new Class<?>[0]);
            EditorLiveQuestTest.start(workspace, document, true);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().stage().equals("start"));
        STEPS.add(() -> {
            require(EditorLiveQuestTest.snapshot().hold(), "Quest playtesting waits for Next.");
            String objective = EditorLiveQuestTest.snapshot().objectives().getFirst().id();
            EditorLiveQuestTest.prepare(workspace, document, objective, "");
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        pause(1500);
        STEPS.add(() -> {
            require(EditorLiveQuestTest.snapshot().stage().equals("start") && EditorLiveQuestTest.snapshot().ready(),
                    "Real inventory requirements become ready while the stage stays held: " + EditorLiveQuestTest.snapshot());
            capture("29-playtest-quest-ready");
            String localePath = document.resourcePath().substring(0, document.resourcePath().lastIndexOf('/') + 1) + "locales/en_us.json";
            playtestTitleLocale = workspace.documents().stream().filter(candidate -> candidate.resourcePath().equals(localePath)).findFirst().orElseThrow();
            playtestTitleLocale.edit("Revise title wording", root -> root.addProperty("test.quest.live_execution.title", "Revised live quest"));
            document.edit("Revise quest display", root -> {
                var title = new com.google.gson.JsonObject(); title.addProperty("key", "#title");
                var display = new com.google.gson.JsonObject(); display.add("title", title);
                root.add("display", display);
            });
            require(workspace.validate().stream().noneMatch(issue -> issue.severity() == com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR),
                    "The title preview uses a valid localized draft: " + workspace.validate());
        });
        pause(400);
        STEPS.add(() -> {
            require(EditorTestWorld.currentLogic(workspace), "Quest display edits keep live logic unchanged.");
            require(com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay.entries().stream()
                    .anyMatch(entry -> entry.questId().equals(document.id()) && entry.title().equals("Revised live quest")),
                    "Quest journal and HUD entries use the edited presentation.");
            Minecraft.getInstance().setScreen(new com.jvn.villagerretaliation.client.interaction.VillagerQuestJournalScreen());
        });
        STEPS.add(() -> {
            capture("29a-playtest-quest-live-title");
            Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen) field(editor, "screen"));
            document.undo();
            playtestTitleLocale.undo();
        });
        pause(400);
        STEPS.add(() -> {
            EditorLiveQuestTest.replay(workspace, document);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        STEPS.add(() -> EditorLiveQuestTest.force(workspace, document, ""));
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        STEPS.add(() -> {
            require(EditorLiveQuestTest.snapshot().log().stream().anyMatch(line -> line.contains("Test bypass")), "Quest shortcuts are recorded.");
            EditorLiveQuestTest.next(workspace, document);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        pause(1000);
        STEPS.add(() -> {
            require(!EditorLiveQuestTest.snapshot().stage().equals("start"), "Next advances through the real stage runtime.");
            capture("30-playtest-quest-next");
        });
        buildPlaytestReloadSteps();
    }

    private static void buildPlaytestReloadSteps() {
        STEPS.add(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            reloadWorld = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
            liveCheck = server.submit(() -> reloadExperience = server.getPlayerList().getPlayer(playerId).totalExperience);
        });
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> {
            liveCheck.join();
            document.edit("Update live logic", root -> {
                root.addProperty("revision", 2);
                root.getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonArray("on_enter").get(0).getAsJsonObject().addProperty("amount", 11);
            });
            require(!EditorTestWorld.currentLogic(workspace), "Quest logic stays pending until applied.");
        });
        waitUntil(() -> {
            if (EditorLiveQuestTest.view().busy()) return false;
            invoke("applyPlaytestDraft", new Class<?>[0]);
            return true;
        });
        waitUntil(() -> {
            if (!EditorTestWorld.applying() && field(editor, "status").toString().startsWith("Draft reload failed"))
                throw new IllegalStateException(field(editor, "status").toString());
            return !EditorTestWorld.applying() && EditorTestWorld.currentSnapshot(workspace)
                    && !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().stage().equals("start");
        });
        STEPS.add(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> require(server.getPlayerList().getPlayer(playerId).totalExperience == reloadExperience + 11,
                    "Apply & replay executes the updated entry action exactly once."));
            require(reloadWorld.equals(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize()),
                    "Applying logic keeps the same test world.");
            capture("31-playtest-applied-draft");
        });
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> {
            liveCheck.join();
            document.edit("Rejected logic", root -> root.addProperty("revision", 3));
            var attempts = new java.util.concurrent.atomic.AtomicInteger();
            try {
                reloadCheck = EditorTestWorld.apply(workspace, server -> attempts.getAndIncrement() == 0
                        ? java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Injected resource reload failure"))
                        : server.reloadResources(server.getPackRepository().getSelectedIds()));
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        waitUntil(() -> reloadCheck.isDone());
        STEPS.add(() -> {
            require(reloadCheck.isCompletedExceptionally(), "A failed resource reload is reported.");
            require(!EditorTestWorld.currentSnapshot(workspace), "A failed reload does not advance the applied snapshot.");
            document.undo();
            require(EditorTestWorld.currentSnapshot(workspace), "Rollback restores the last applied draft.");
            capture("32-playtest-reload-rollback");
            invoke("leavePlaytest", new Class<?>[] {int.class}, 1);
        });
    }

    private static void confirmYes() {
        require(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ConfirmScreen, "The expected confirmation is visible.");
        Minecraft.getInstance().screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                .map(child -> (net.minecraft.client.gui.components.Button) child).findFirst().orElseThrow().onPress();
    }

    private static void waitUntil(java.util.function.BooleanSupplier ready) {
        STEPS.add(new Runnable() {
            @Override public void run() { if (!ready.getAsBoolean()) STEPS.addFirst(this); }
        });
    }

    private static void buildProgressConditionSteps() {
        var original = new AuthoringDocument[1];
        var progress = new AuthoringDocument[1];
        STEPS.add(() -> {
            original[0] = (AuthoringDocument) field(editor, "document");
            require(EditorProgressConditions.resources("dimension").contains("minecraft:the_nether"), "Dimension selectors use the connected world.");
            require(EditorProgressConditions.resources("structure").contains("minecraft:monument"), "Structure selectors use the server registry.");
            require(EditorProgressConditions.resources("biome").contains("minecraft:plains"), "Biome selectors use the connected registry.");
            require(EditorProgressConditions.resources("damage_type").contains("minecraft:fall"), "Damage selectors include vanilla damage types.");
            require(EditorProgressConditions.resources("damage_type").contains("player_history:arcane"), "Damage selectors include fixture-defined mod damage types.");
            require(EditorProgressConditions.resources("advancement").contains("minecraft:story/mine_stone"), "Advancement selectors use the connected server.");
            progress[0] = ((AuthoringWorkspace) field(editor, "workspace")).createDocument(DefinitionKind.TREE, "test:progress_checks");
            set("view", 0);
            String visited = EditorBlocks.append(progress[0], "/nodes/start", EditorBlocks.Kind.PLAYER_DIMENSION_VISITED);
            String latest = EditorBlocks.append(progress[0], "/nodes/start", EditorBlocks.Kind.PLAYER_LAST_DEATH);
            progress[0].edit("Choose dimensions and death causes", root -> {
                var dimensions = com.jvn.villagerretaliation.authoring.core.JsonPointers.get(root, visited).getAsJsonObject();
                dimensions.getAsJsonArray("dimensions").add("minecraft:the_end");
                var death = com.jvn.villagerretaliation.authoring.core.JsonPointers.get(root, latest).getAsJsonObject();
                death.getAsJsonArray("damage_types").add("player_history:arcane");
                death.add("entities", JsonParser.parseString("[\"minecraft:player\",\"minecraft:skeleton\"]"));
            });
            invoke("select", new Class<?>[] {AuthoringDocument.class}, progress[0]);
            invoke("setPointer", new Class<?>[] {String.class}, visited);
        });
        pause(400);
        STEPS.add(() -> {
            capture("27-progress-dimension-lists");
            invoke("setPointer", new Class<?>[] {String.class}, "/nodes/start/conditions/1");
        });
        pause(400);
        STEPS.add(() -> {
            capture("28-progress-latest-death");
        });
        for (var kind : List.of(EditorBlocks.Kind.PLAYER_STAT, EditorBlocks.Kind.EVENT_HISTORY, EditorBlocks.Kind.PERSONAL_TRADE,
                EditorBlocks.Kind.SETTLEMENT_NEED, EditorBlocks.Kind.SETTLEMENT_CONTRIBUTION,
                EditorBlocks.Kind.PLAYER_STRUCTURE_VISITED, EditorBlocks.Kind.PLAYER_SETTLEMENT_VISITED,
                EditorBlocks.Kind.PLAYER_DESTINATION_COUNT, EditorBlocks.Kind.PLAYER_ITEM_POSSESSED)) {
            STEPS.add(() -> {
                String path = EditorBlocks.append(progress[0], "/nodes/start", kind);
                progress[0].edit("Configure progress check", root -> {
                    var value = com.jvn.villagerretaliation.authoring.core.JsonPointers.get(root, path).getAsJsonObject();
                    if (kind != EditorBlocks.Kind.PLAYER_ITEM_POSSESSED) value.addProperty("max", 100);
                    if (kind == EditorBlocks.Kind.EVENT_HISTORY) {
                        value.addProperty("since", "last");
                        value.addProperty("max_age_ticks", 24000);
                    }
                });
                invoke("setPointer", new Class<?>[] {String.class}, path);
                require(EditorProgressConditions.statisticTargets(EditorProgressConditions.seed("player_stat")).contains("minecraft:damage_blocked_by_shield"),
                        "Statistic selector includes vanilla combat measures.");
            });
            pause(400);
            STEPS.add(() -> capture("29-progress-" + kind.name().toLowerCase(java.util.Locale.ROOT)));
        }
        STEPS.add(() -> invoke("select", new Class<?>[] {AuthoringDocument.class}, original[0]));
    }

    private static void buildCameraSteps() {
        click(() -> toolbar("Edit"));
        STEPS.add(AuthoringEditorClientSmoke::locateCanvas);
        click(() -> point("start", 80, EditorGraph.HEADER + 12));
        click(() -> toolbar("Preview"));
        click(() -> screenPoint(400, 425));
        STEPS.add(() -> {
            require("/nodes/start".equals(field(EditorCameraPanel.class, "cameraOwner")), "Camera panel resolves the Say block's owner.");
            capture("13c-camera-controls");
            cameraGuiBefore = Minecraft.getInstance().options.hideGui;
        });
        click(() -> screenPoint(365, 533));
        STEPS.add(() -> require(cameraSession() != null && cameraSession().active() && Minecraft.getInstance().options.hideGui, "Begin camera session starts the native camera with a clean viewport."));
        click(() -> screenPoint(350, 653));
        click(() -> screenPoint(365, 693));
        STEPS.add(() -> {
            require(cameraSession().ownsScreen(Minecraft.getInstance().screen), "Position free camera opens native positioning controls.");
            cameraBefore = cameraSession().pose();
            Minecraft.getInstance().screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
            Minecraft.getInstance().screen.mouseDragged(320, 180, GLFW.GLFW_MOUSE_BUTTON_RIGHT, 20, -10);
            Minecraft.getInstance().screen.mouseScrolled(320, 180, 0, 2);
        });
        pause(400);
        STEPS.add(() -> {
            Minecraft.getInstance().screen.keyReleased(GLFW.GLFW_KEY_E, 0, 0);
            var pose = cameraSession().pose();
            require(pose.position().distanceTo(cameraBefore.position()) > 0.1, "Free-camera movement changes the rendered framing.");
            require(Math.abs(pose.fov() - cameraBefore.fov() + 4) < 0.1, "Mouse wheel adjusts camera FOV.");
            capture("13d-free-camera");
            Minecraft.getInstance().screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        });
        click(() -> screenPoint(500, 693));
        STEPS.add(() -> {
            var camera = com.jvn.villagerretaliation.dialogue.DialogueCamera.read(document.json().getAsJsonObject("nodes").getAsJsonObject("start"));
            require(camera != null && camera.shots().size() == 1 && camera.shots().getFirst().orientation() != null,
                    "Add shot stores the captured pose on the selected node.");
            String encoded = EditorCameraShots.thumbnails(document.metadata(), "/nodes/start", 1).get(0).getAsString();
            require(!encoded.isBlank(), "Captured shots retain a real viewport thumbnail.");
            try (var thumbnail = NativeImage.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(encoded)))) {
                require(thumbnail.getWidth() == 480 && thumbnail.getHeight() == 270, "Shot thumbnails keep their preview dimensions.");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            capture("13e-captured-shot");
        });
        click(() -> screenPoint(375, 773));
        click(() -> screenPoint(560, 533));
        STEPS.add(() -> require(cameraSession() == null
                && !com.jvn.villagerretaliation.client.interaction.DialogueCameraController.active()
                && !Minecraft.getInstance().mouseHandler.isMouseGrabbed()
                && Minecraft.getInstance().options.hideGui == cameraGuiBefore,
                "Restore player camera disposes the authoring camera."));
    }

    private static com.jvn.villagerretaliation.authoring.camera.AuthoringCameraSession cameraSession() {
        return (com.jvn.villagerretaliation.authoring.camera.AuthoringCameraSession) field(EditorCameraPanel.class, "session");
    }

    private static void buildImportSteps() {
        click(() -> screenPoint(140, 310));
        replaceText(() -> screenPoint(130, 378), () -> output().resolve("setup-export").toString());
        click(() -> screenPoint(70, 418));
        STEPS.add(() -> {
            require(field(editor, "status").toString().startsWith("Import would overwrite "), "Import explains duplicate content without overwriting it.");
            require(workspace.documents().size() == 1 && document.json().getAsJsonObject("nodes").getAsJsonObject("start")
                    .getAsJsonArray("lines").get(0).getAsString().equals(GREETING), "Rejected import preserves the authored dialogue.");
            capture("16-import-protection");
        });
        click(() -> screenPoint(140, 310));
        click(() -> toolbar("Workspace"));
        replaceText(() -> screenPoint(120, 277), () -> output().resolve("import-drafts").toString());
        click(() -> screenPoint(200, 317));
        click(() -> screenPoint(140, 153));
        STEPS.add(() -> capture("17-empty-import-workspace"));
        click(() -> screenPoint(140, 350));
        replaceText(() -> screenPoint(130, 418), () -> output().resolve("setup-export").toString());
        click(() -> screenPoint(70, 458));
        STEPS.add(() -> {
            workspace = (AuthoringWorkspace) field(editor, "workspace");
            document = (AuthoringDocument) field(editor, "document");
            require(document != null && document.id().equals("test:from_scratch"), "Import selects the first editable dialogue.");
            require(!(boolean) field(editor, "showCreate") && (int) field(editor, "view") == 0,
                    "Import closes the unused create form and opens the graph.");
            try {
                var exported = JsonParser.parseString(Files.readString(output().resolve("setup-export").resolve(document.resourcePath())));
                require(exported.equals(document.json()), "Reimport preserves wording, formatting, variables, responses and camera shots.");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            capture("18-reimported-dialogue");
        });
    }

    private static void buildStyledGraphSteps() {
        STEPS.add(AuthoringEditorClientSmoke::locateCanvas);
        click(() -> point("start", 80, EditorGraph.HEADER + 12));
        move(() -> screenPoint(1400, 900), false);
        STEPS.add(() -> {
            require(((imgui.type.ImString) field(editor, "pointer")).get().equals("/nodes/start/lines"),
                    "Styled blocks retain direct text editing.");
            var previews = (Map<?, ?>) field(field(editor, "graphText"), "previews");
            require(field(field(previews.get("/nodes/start/lines"), "preview"), "text").equals(GREETING),
                    "The visible graph uses the authored formatting source.");
            try (var image = renderedImage()) {
                float[] min = point("start", 0, EditorGraph.HEADER);
                float[] max = point("start", EditorGraph.WIDTH, EditorGraph.HEADER + EditorGraph.ROW);
                int gold = 0;
                for (int y = Math.max(0, (int) (min[1] - ImGui.getMainViewport().getPosY()));
                        y < Math.min(image.getHeight(), max[1] - ImGui.getMainViewport().getPosY()); y++) {
                    for (int x = Math.max(0, (int) (min[0] - ImGui.getMainViewport().getPosX()));
                            x < Math.min(image.getWidth(), max[0] - ImGui.getMainViewport().getPosX()); x++) {
                        int pixel = image.getPixelRGBA(x, y);
                        if ((pixel & 255) > 200 && (pixel >> 8 & 255) > 100 && (pixel >> 8 & 255) < 210 && (pixel >> 16 & 255) < 80) gold++;
                    }
                }
                require(gold > 5, "The graph framebuffer contains the greeting's gold text.");
            }
            capture("11a-styled-graph");
        });
        move(() -> point("start", 80, EditorGraph.HEADER + 12), false);
        STEPS.add(() -> {
            var previews = (Map<?, ?>) field(field(editor, "graphText"), "previews");
            require(previews.containsKey("/nodes/start/lines##tooltip"), "Hover opens an expanded styled preview.");
            var tooltip = field(previews.get("/nodes/start/lines##tooltip"), "preview");
            require(!(boolean) field(tooltip, "clipped"),
                    "A complete hover preview does not claim more text is hidden. Content height: " + field(tooltip, "contentHeight")
                            + ", texture height: " + field(tooltip, "renderedHeight") + ", scale: " + field(tooltip, "renderedScale"));
            capture("11b-styled-graph-tooltip");
        });
        move(() -> screenPoint(1400, 900), false);
        STEPS.add(() -> mouseWheel = -2);
        STEPS.add(() -> {
            require((float) field(editor, "zoom") < 1, "Mouse wheel zooms the graph out.");
            capture("11c-graph-zoomed");
        });
        click(() -> screenPoint(780, 181));
        STEPS.add(() -> {
            require((float) field(editor, "zoom") == 1, "The zoom percentage resets to 100%.");
            require(!document.dirty(), "Graph browsing and zoom do not dirty the dialogue.");
            capture("11d-graph-zoom-reset");
        });
    }

    private static void buildRichTextSteps() {
        var originalHeight = new int[1];
        var originalWidth = new int[1];
        var beforeResize = new com.google.gson.JsonObject[1];
        editLine("start", "Welcome, ");
        click(() -> screenPoint(1700, 350));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.End);
        expectCaret("Welcome, ");
        click(() -> textTool("Insert variable"));
        key(ImGuiKey.Escape);
        STEPS.add(() -> require(Minecraft.getInstance().screen == field(editor, "screen")
                && !ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopup), "Escape dismisses the variable menu without closing the editor."));
        insertVariable(307);
        expectCaret("Welcome, {player}");
        type("! It is ");
        expectCaret("Welcome, {player}! It is ");
        insertVariable(372);
        type(" (");
        insertVariable(400);
        type("), day ");
        insertVariable(428);
        type(".");
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Home);
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Shift, true));
        for (int i = 0; i < 7; i++) key(ImGuiKey.RightArrow);
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Shift, false));
        click(() -> textTool("I"));
        click(() -> textTool("B"));
        click(() -> textTool("U"));
        click(() -> textTool("Style"));
        click(() -> screenPoint(1800, 363));
        click(() -> textTool("Style"));
        move(() -> screenPoint(1800, 307), false);
        pause(500);
        STEPS.add(() -> capture("10b-color-menu"));
        click(() -> screenPoint(1600, 363));
        STEPS.add(() -> {
            require(textEditing(), "Formatting keeps the source field active.");
            require(((imgui.type.ImString) field(greetingSelection(), "buffer")).get().equals(GREETING),
                    "Toolbar commands produce the exact authored markup before round-trip checks.");
            capture("10c-formatted-text");
        });
        click(() -> screenPoint(1700, 250));
        STEPS.add(() -> {
            require(!textEditing(), "Leaving the field displays styled text.");
            capture("10d-styled-field");
            originalHeight[0] = (int) field(field(greetingSelection(), "preview"), "renderedHeight");
            originalWidth[0] = (int) field(field(greetingSelection(), "preview"), "renderedWidth");
            beforeResize[0] = document.json();
        });
        move(() -> screenPoint(1886, 407), false);
        move(() -> screenPoint(1886, 407), true);
        STEPS.add(() -> { mouseX -= 64; mouseY += 96; });
        STEPS.add(() -> mouseDown = false);
        STEPS.add(() -> {
            require((int) field(field(greetingSelection(), "preview"), "renderedHeight") >= originalHeight[0] + 90,
                    "Dragging the corner grows the styled text box.");
            require((int) field(field(greetingSelection(), "preview"), "renderedWidth") <= originalWidth[0] - 64 + ImGui.getStyle().getScrollbarSize() + 1,
                    "Dragging the corner also adjusts text box width.");
            require(document.json().equals(beforeResize[0]) && ((imgui.type.ImString) field(greetingSelection(), "buffer")).get().equals(GREETING),
                    "Resizing preserves the draft and pending wording.");
            capture("10g-resized-styled-field");
        });
        click(() -> screenPoint(1700, 350));
        STEPS.add(() -> require(textEditing(), "The resized box opens the same editable source."));
        move(() -> screenPoint(1822, 503), false);
        move(() -> screenPoint(1822, 503), true);
        STEPS.add(() -> { mouseX -= 32; mouseY += 32; });
        STEPS.add(() -> mouseDown = false);
        STEPS.add(() -> {
            require(textEditing(), "Resizing editable source restores its text focus.");
            capture("10h-resized-source-field");
        });
        click(() -> toolbar("Save"));
        STEPS.add(() -> {
            require((int) field(field(greetingSelection(), "preview"), "renderedHeight") >= originalHeight[0] + 90
                            && (int) field(field(greetingSelection(), "preview"), "renderedWidth") <= originalWidth[0] - 96 + ImGui.getStyle().getScrollbarSize() + 1,
                    "Applying field edits preserves the chosen text box dimensions.");
            require(document.json().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").get(0).getAsString().equals(GREETING),
                    "Saving a resized field preserves its authored text.");
        });
        move(() -> screenPoint(1790, 535), false);
        STEPS.add(() -> { mouseDown = true; mouseDoubleClicked = true; });
        STEPS.add(() -> { mouseDown = false; mouseDoubleClicked = false; });
        STEPS.add(() -> require((int) field(field(greetingSelection(), "preview"), "renderedHeight") == originalHeight[0]
                        && (int) field(field(greetingSelection(), "preview"), "renderedWidth") == originalWidth[0],
                "Double-clicking the resize handle restores the default dimensions."));
        click(() -> screenPoint(1700, 350));
        STEPS.add(() -> {
            require(textEditing(), "Clicking the styled field reveals editable tags.");
            capture("10e-source-field");
        });
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.End);
        type("!");
        key(ImGuiKey.Backspace);
        key(ImGuiKey.Tab);
        STEPS.add(() -> require(!textEditing(), "Tab leaves source editing and restores styled text."));
        chord(ImGuiKey.ImGuiMod_Shift, ImGuiKey.Tab);
        STEPS.add(() -> require(textEditing(), "Keyboard focus reveals the same editable source."));
        replaceText(() -> screenPoint(1700, 350), () -> "<b>" + "A longer styled line. ".repeat(45) + "</b>");
        click(() -> screenPoint(1700, 250));
        move(() -> screenPoint(1700, 350), false);
        STEPS.add(() -> ImGui.getIO().addMouseWheelEvent(0, -4));
        pause(200);
        STEPS.add(() -> {
            var selections = (Map<?, ?>) field(field(editor, "textFields"), "selections");
            var selection = selections.values().stream().filter(value ->
                    ((imgui.type.ImString) field(value, "buffer")).get().contains("A longer styled line")).findFirst().orElseThrow();
            require((float) field(field(selection, "preview"), "scroll") > 0, "Long styled text scrolls inside its field.");
            capture("10f-scrolled-styled-field");
        });
        replaceText(() -> screenPoint(1700, 350), () -> GREETING);
        click(() -> toolbar("Save"));
        STEPS.add(() -> require(document.json().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines")
                .get(0).getAsString().equals(GREETING), "Toolbar formatting and variable insertion preserve the selected text."));
    }

    private static boolean textEditing() {
        return (boolean) field(greetingSelection(), "editing");
    }

    private static Object greetingSelection() {
        var selections = (Map<?, ?>) field(field(editor, "textFields"), "selections");
        return selections.values().stream().filter(value ->
                ((imgui.type.ImString) field(value, "buffer")).get().contains("Welcome")).findFirst().orElseThrow();
    }

    private static void expectCaret(String expected) {
        STEPS.add(() -> {
            var selections = (Map<?, ?>) field(field(editor, "textFields"), "selections");
            var selection = selections.values().stream().filter(value ->
                    ((imgui.type.ImString) field(value, "buffer")).get().contains("Welcome")).findFirst().orElseThrow();
            String actual = ((imgui.type.ImString) field(selection, "buffer")).get();
            require(actual.equals(expected) && (int) field(selection, "start") == EditorTextEdit.bytes(expected)
                    && (int) field(selection, "end") == EditorTextEdit.bytes(expected),
                    "Text insertion keeps the caret: " + actual + " [" + field(selection, "start") + ", " + field(selection, "end") + "]");
        });
    }

    private static void insertVariable(float y) {
        click(() -> textTool("Insert variable"));
        STEPS.add(() -> capture("10a-variable-menu"));
        click(() -> screenPoint(1700, y));
    }

    private static float[] textTool(String target) {
        float x = ImGui.getMainViewport().getWorkSizeX() - 380;
        for (String label : new String[] {"B", "I", "U", "Insert variable", "Style"}) {
            float width = ImGui.calcTextSize(label).x + 16;
            if (label.equals(target)) return screenPoint(x + width / 2, 283);
            x += width + 8;
        }
        throw new IllegalArgumentException(target);
    }

    private static String previewGreeting(String time, String phase) {
        return "Welcome, " + Minecraft.getInstance().player.getName().getString() + "! It is " + time + " (" + phase + "), day 1.";
    }

    private static void type(String text) { STEPS.add(() -> ImGui.getIO().addInputCharactersUTF8(text)); }

    private static void key(int key) {
        STEPS.add(() -> ImGui.getIO().addKeyEvent(key, true));
        STEPS.add(() -> ImGui.getIO().addKeyEvent(key, false));
    }

    private static void chord(int modifier, int key) {
        STEPS.add(() -> ImGui.getIO().addKeyEvent(modifier, true));
        key(key);
        STEPS.add(() -> ImGui.getIO().addKeyEvent(modifier, false));
    }

    private static void editLine(String node, String text) {
        STEPS.add(AuthoringEditorClientSmoke::locateCanvas);
        click(() -> point(node, 80, EditorGraph.HEADER + 12));
        STEPS.add(() -> require(((imgui.type.ImString) field(editor, "pointer")).get().equals("/nodes/" + node + "/lines"),
                "Say selects the line editor for " + node));
        replaceText(() -> screenPoint(1700, 350), () -> text);
        click(() -> toolbar("Save"));
        STEPS.add(() -> require(document.json().getAsJsonObject("nodes").getAsJsonObject(node).getAsJsonArray("lines")
                .get(0).getAsString().equals(text), "Save applies typed dialogue text for " + node));
    }

    private static void replaceText(Supplier<float[]> point, Supplier<String> text) {
        click(point);
        STEPS.add(() -> {
            ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, true);
            ImGui.getIO().addKeyEvent(ImGuiKey.A, true);
        });
        STEPS.add(() -> {
            ImGui.getIO().addKeyEvent(ImGuiKey.A, false);
            ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, false);
        });
        STEPS.add(() -> ImGui.getIO().addInputCharactersUTF8(text.get()));
    }

    private static void pause(long millis) { STEPS.add(() -> resumeAt = Util.getMillis() + millis); }

    private static void choosePreview(String label) { choosePreview(() -> label); }

    private static void choosePreview(Supplier<String> labelSupplier) {
        STEPS.add(() -> {
            String label = labelSupplier.get();
            require(Minecraft.getInstance().screen instanceof VillagerInteractionScreen, "Preview screen is open.");
            var screen = Minecraft.getInstance().screen;
            List<?> options = (List<?>) field(screen, "options");
            int index = -1;
            for (int i = 0; i < options.size(); i++) if (field(options.get(i), "label").equals(label)) index = i;
            require(index >= 0, "Preview offers " + label);
            Object state = field(screen, "state");
            for (int tries = 0; tries <= options.size(); tries++) {
                try {
                    var selected = state.getClass().getDeclaredMethod("selectedOption");
                    selected.setAccessible(true);
                    if ((int) selected.invoke(state) == index) {
                        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                        screen.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0);
                        return;
                    }
                } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
                screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                screen.keyReleased(GLFW.GLFW_KEY_DOWN, 0, 0);
            }
            throw new IllegalStateException("Could not select preview option " + label);
        });
    }

    private static float[] screenPoint(float x, float y) {
        return new float[] {ImGui.getMainViewport().getPosX() + x, ImGui.getMainViewport().getPosY() + y};
    }

    private static void frame() {
        if (finished || stepping) return;
        stepping = true;
        try {
            if (++frames % 12 == 0 && Util.getMillis() >= resumeAt && !STEPS.isEmpty()) STEPS.removeFirst().run();
            var io = ImGui.getIO();
            io.setMouseWheel(mouseWheel);
            mouseWheel = 0;
            io.setMouseWheelH(0);
            io.setMousePos(mouseX, mouseY);
            io.setMouseDelta(mouseX - previousX, mouseY - previousY);
            io.setMouseDown(0, mouseDown);
            io.setMouseClicked(0, mouseDown && !previousDown);
            io.setMouseReleased(0, !mouseDown && previousDown);
            io.setMouseDownOwned(0, true);
            io.setMouseDownOwnedUnlessPopupClose(0, true);
            if (mouseDown && !previousDown) {
                downX = mouseX;
                downY = mouseY;
                var clicked = io.getMouseClickedPos();
                clicked[0] = new ImVec2(mouseX, mouseY);
                io.setMouseClickedPos(clicked);
            }
            io.setMouseDownDurationPrev(0, io.getMouseDownDuration(0));
            io.setMouseDownDuration(0, mouseDown ? previousDown ? .5f : 0 : -1);
            io.setMouseClickedCount(0, mouseDown && !previousDown ? mouseDoubleClicked ? 2 : 1 : 0);
            if (mouseDoubleClicked) io.setMouseDoubleClicked(0, mouseDown && !previousDown);
            io.setMouseDragMaxDistanceSqr(0, mouseDown ? (mouseX - downX) * (mouseX - downX) + (mouseY - downY) * (mouseY - downY) : 0);
            imgui.internal.ImGui.updateHoveredWindowAndCaptureFlags(mouseX, mouseY);
            previousX = mouseX;
            previousY = mouseY;
            previousDown = mouseDown;
        } catch (RuntimeException exception) {
            capture("failure");
            throw exception;
        } finally {
            stepping = false;
        }
    }

    private static void click(Supplier<float[]> point) {
        move(point, false);
        move(point, true);
        STEPS.add(() -> mouseDown = false);
    }

    private static void move(Supplier<float[]> point, boolean down) {
        STEPS.add(() -> {
            float[] destination = point.get();
            mouseX = destination[0];
            mouseY = destination[1];
            // manual frame input already sets this position before queued events reach NewFrame
            ImGui.getIO().addMousePosEvent(mouseX + 1, mouseY);
            mouseDown = down;
        });
    }

    private static float[] toolbar(String target) {
        float x = ImGui.getMainViewport().getWorkPosX() + 14;
        float scale = ((EditorPreferences) field(editor, "preferences")).textScale();
        for (String label : new String[] {"Workspace", "New", "Save", "Edit", "Preview", "Playtest", "Check & export",
                (int) field(editor, "guideStep") < 0 ? "Quick guide" : "Hide guide", "Reset layout", "Close"}) {
            float width = ImGui.calcTextSize(label).x * scale + 16;
            if (label.equals(target)) return new float[] {x + width / 2, ImGui.getMainViewport().getWorkPosY() + 14 + (ImGui.getFontSize() * scale + 12) / 2};
            x += width + 8;
        }
        throw new IllegalArgumentException(target);
    }

    private static float[] port(String node) {
        var card = EditorGraph.cards(document, value -> workspace.resolveText(document, value, "en_us")).stream()
                .filter(candidate -> candidate.node().id().equals(node)).findFirst().orElseThrow();
        for (int i = 0; i < card.blocks().size(); i++)
            if (card.blocks().get(i).output() != null) return point(node, EditorGraph.WIDTH, card.outputY(i));
        throw new IllegalStateException("No output on " + node);
    }

    private static float[] point(String node, float x, float y) {
        float[] position = positions().get(node);
        float zoom = (float) field(editor, "zoom");
        return new float[] {canvas[0] + (float) field(editor, "panX") + (position[0] + x) * zoom,
                canvas[1] + (float) field(editor, "panY") + (position[1] + y) * zoom};
    }

    private static void locateCanvas() {
        try (NativeImage image = renderedImage()) {
            require(image.getWidth() >= 1600 && image.getHeight() >= 900, "Client is rendered in a full-size window.");
            int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1, pixels = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                if (image.getPixelRGBA(x, y) != 0xff242321) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
                pixels++;
            }
            require(pixels > 50_000 && right - left > 600, "Automatically docked graph has visible usable space.");
            require(left > 200 && right < image.getWidth() - 250, "Content and Details flank the graph automatically.");
            canvas = new float[] {left + ImGui.getMainViewport().getPosX(), top + ImGui.getMainViewport().getPosY()};
        }
    }

    private static NativeImage renderedImage() {
        var window = Minecraft.getInstance().getWindow();
        int width = window.getWidth(), height = window.getHeight();
        int framebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        var pixels = MemoryUtil.memAlloc(width * height * 4).order(ByteOrder.nativeOrder());
        NativeImage image = new NativeImage(width, height, false);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL11.glReadBuffer(GL11.GL_FRONT);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                image.setPixelRGBA(x, height - y - 1, pixels.getInt((y * width + x) * 4));
            return image;
        } catch (RuntimeException exception) {
            image.close();
            throw exception;
        } finally {
            MemoryUtil.memFree(pixels);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(readBuffer);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
        }
    }

    private static void capture(String name) {
        try (NativeImage image = renderedImage()) {
            image.writeToFile(output().resolve(name + ".png"));
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, float[]> positions() { return (Map<String, float[]>) field(editor, "positions"); }

    private static Object field(Object target, String name) {
        try {
            var field = (target == null ? ImGuiAuthoringEditor.class : target instanceof Class<?> type ? type : target.getClass()).getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target instanceof Class<?> ? null : target);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void set(String name, Object value) {
        try {
            var field = ImGuiAuthoringEditor.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(editor, value);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void invoke(String name, Class<?>[] types, Object... arguments) {
        try {
            var method = ImGuiAuthoringEditor.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(editor, arguments);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static Path output() { return Path.of(System.getProperty(OUTPUT)); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
