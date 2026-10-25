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
import java.util.ArrayList;
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
    private static AuthoringWorkspace campaignWorkspace;
    private static AuthoringDocument document;
    private static long started, resumeAt;
    private static java.util.concurrent.CompletableFuture<?> liveCheck;
    private static Path originalWorld;
    private static Path reloadWorld;
    private static AuthoringDocument playtestTitleLocale;
    private static int initialLiveExperience, reloadExperience, playtestLogSize;
    private static String reloadStage;
    private static AuthoringDocument repeatedLiveTree;
    private static List<com.jvn.villagerretaliation.dialogue.DialogueSpeech> repeatedLiveSpeech;
    private static java.util.concurrent.CompletableFuture<Void> reloadCheck;
    private static int frames;
    private static int keyRelease = -1;
    private static boolean workflowControls, workflowLists;
    private static boolean questLiveControls;
    private static boolean deletionTyping, deletionFocus;
    private static final imgui.type.ImString deletionText = new imgui.type.ImString("Keep typing", 128);
    private static final float[][] workflowHandles = new float[3][], workflowTargets = new float[3][];
    private static float[] workflowFieldPoint;
    private static boolean confirmationChecks;
    private static int confirmationFrames;
    private static final int[] confirmationViewport = new int[4];
    private static float confirmationPixelScaleX, confirmationPixelScaleY;
    private static boolean qualityControls;
    private static boolean resourceControls;
    private static String resourceKind = "item", resourceValue = "minecraft:stone";
    private static float[] resourcePickerPoint, resourceShortcutPoint, resourceListenPoint;
    private static net.minecraft.world.item.ItemStack originalHeldItem;
    private static net.minecraft.world.entity.Entity resourceTarget;
    private static boolean layoutChecks;
    private static boolean variableControls;
    private static boolean completionInline, completionPlain;
    private static final List<PanelSection> panelSections = new ArrayList<>();
    private record PanelSection(String panel, String label, boolean open) { }
    private static final EditorTextField variableFields = new EditorTextField();
    private static final imgui.type.ImString variableText = new imgui.type.ImString("Hello, ", 1024);
    private static float[] variableMenuPoint;
    private static float variableTextScale = 1;
    private static float[] shortcutPopupSize;
    private static float shortcutBaseFontSize;
    private static int shortcutPopupFrames;
    private static int featureStage = -1;
    private static float[] featureActionPoint;
    private static final List<Long> dropdownFrameTimes = new ArrayList<>();
    private static final List<Long> contentBrowserTimes = new ArrayList<>();
    private static boolean measureContentBrowser, measureWorldLoading;
    private static long worldLoadingFrame, worldLoadingGap;
    private static String qualityResource = "minecraft:stone";
    private static float[] qualityPickerPoint;
    private static boolean finished, installed, mouseDown, previousDown, stepping, mouseDoubleClicked;
    private static boolean cameraGuiBefore, rightDown, previousRightDown;
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
            set("filesMode", false);
            set("status", "Test workspace opened.");
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            ImGuiAuthoringEditor.open();
            buildSteps();
            ImGuiMCEvents.INSTANCE.preRenderImGuiEvent(AuthoringEditorClientSmoke::frame);
            ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(AuthoringEditorClientSmoke::renderContentBrowserBenchmark);
            ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(AuthoringEditorClientSmoke::renderQualityControls);
            ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(AuthoringEditorClientSmoke::renderResourceControls);
            ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(AuthoringEditorClientSmoke::renderWorkflowControls);
            ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(AuthoringEditorClientSmoke::renderFeatureControls);
            ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(AuthoringEditorClientSmoke::checkShortcutPopup);
            ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(AuthoringEditorClientSmoke::renderVariableControls);
            ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(AuthoringEditorClientSmoke::preparePanelSections);
            ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(AuthoringEditorClientSmoke::renderQuestLiveControls);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void buildWorldContentSteps() {
        STEPS.add(() -> {
            set("showWorkspace", true);
            invoke("refreshWorldSelection", new Class<?>[0]);
        });
        waitUntil(() -> ((java.util.concurrent.CompletableFuture<?>) field(editor, "worldSelection")).isDone());
        STEPS.add(() -> {
            var selection = (EditorWorldContent.Selection) ((java.util.concurrent.CompletableFuture<?>) field(editor, "worldSelection")).join();
            require(!selection.campaign().isBlank(), "Current world identifies its campaign.");
            capture("00-current-world-selection");
            long started = System.nanoTime();
            invoke("loadWorldContent", new Class<?>[0]);
            long elapsed = System.nanoTime() - started;
            System.out.printf(java.util.Locale.ROOT, "World content import request: %.3f ms on the client thread%n", elapsed / 1e6);
            require(elapsed < 250_000_000, "Starting a world import does not parse or validate the library on the client thread.");
            worldLoadingFrame = 0; worldLoadingGap = 0; measureWorldLoading = true;
        });
        waitUntil(() -> field(editor, "worldContent") == null);
        STEPS.add(() -> {
            measureWorldLoading = false;
            var imported = (AuthoringWorkspace) field(editor, "workspace");
            campaignWorkspace = imported;
            require(imported != workspace && !imported.documents().isEmpty(), "Current world content opens in a fresh populated workspace: " + field(editor, "status"));
            require(imported.documents().stream().anyMatch(value -> value.kind() == DefinitionKind.QUEST), "Current world import exposes quests.");
            require(imported.documents().stream().anyMatch(value -> value.kind() == DefinitionKind.TREE), "Current world import exposes dialogue.");
            require((boolean) field(editor, "showResources"), "World imports expose dialogue pools and supporting content.");
            require(field(editor, "document") != null, "World imports select a campaign or quest to begin editing.");
            require(Files.exists(workspace.directory().resolve("workspace.json")), "Earlier drafts are saved before switching workspaces.");
            var browser = (EditorContentBrowser) field(editor, "contentBrowser");
            var index = browser.get(imported, "en_us");
            require(index == browser.get(imported, "en_us"), "Imported content adopts the prepared browser index.");
            require(index.entries().stream().anyMatch(entry -> entry.title().equals("Blank Map Promise")), "The library displays localized quest titles.");
            System.out.printf(java.util.Locale.ROOT, "World content loading and handoff: longest rendered frame gap %.3f ms%n", worldLoadingGap / 1e6);
            require(worldLoadingGap < 500_000_000, "World loading and handoff keep rendering without a long client stall.");
            capture("00-current-world-content");
            set("groupContent", false);
            contentBrowserTimes.clear(); measureContentBrowser = true;
        });
        pause(1250);
        STEPS.add(() -> {
            measureContentBrowser = false;
            require(contentBrowserTimes.size() >= 20, "The imported library renders through multiple frames.");
            var times = contentBrowserTimes.stream().sorted().toList();
            double median = times.get(times.size() / 2) / 1e6;
            System.out.printf(java.util.Locale.ROOT, "Content library (%d entries): %.3f ms median render%n", ((AuthoringWorkspace) field(editor, "workspace")).documents().size(), median);
            require(median < 8, "A large content list renders only visible rows: " + median + " ms.");
            set("groupContent", true);
            ((EditorContentBrowser) field(editor, "contentBrowser")).reveal((AuthoringDocument) field(editor, "document"));
        });
        pause(250);
        STEPS.add(() -> {
            capture("00-readable-library");
            EditorCampaignProfile.start("default-campaign");
        });
        pause(4000);
        STEPS.add(() -> {
            var profile = EditorCampaignProfile.stop(output());
            require(profile.medianMillis() < 8 && profile.p95Millis() < 16 && profile.allocatedMegabytes() < 4,
                    "The complete bundled campaign editor stays responsive without repeated schema work: " + profile);
            var imported = (AuthoringWorkspace) field(editor, "workspace");
            var largest = imported.documents().stream().filter(value -> value.kind() == DefinitionKind.QUEST)
                    .max(java.util.Comparator.comparingInt(value -> value.value("/stages").getAsJsonArray().size())).orElseThrow();
            invoke("select", new Class<?>[] {AuthoringDocument.class}, largest);
        });
        pause(500);
        STEPS.add(() -> EditorCampaignProfile.start("largest-quest"));
        pause(4000);
        STEPS.add(() -> {
            var profile = EditorCampaignProfile.stop(output());
            require(profile.medianMillis() < 8 && profile.p95Millis() < 16 && profile.allocatedMegabytes() < 8,
                    "The largest bundled quest stays responsive with the full campaign loaded: " + profile);
            capture("00-largest-campaign-quest");
        });
        STEPS.add(() -> {
            long before = System.nanoTime();
            invoke("updatePlaytestPresentation", new Class<?>[0]);
            double elapsed = (System.nanoTime() - before) / 1e6;
            System.out.printf(java.util.Locale.ROOT, "Campaign playtest presentation update: %.3f ms%n", elapsed);
            require(elapsed < 50, "Opening Playtest reuses prepared campaign validation without blocking a tick.");
            set("validationCache", new EditorValidationCache());
            worldLoadingFrame = 0; worldLoadingGap = 0; measureWorldLoading = true;
            invoke("enterPlaytest", new Class<?>[0]);
        });
        pause(3000);
        STEPS.add(() -> {
            measureWorldLoading = false;
            require(EditorPlaytestViewport.layout() != null, "The full campaign opens the split game viewport.");
            System.out.printf(java.util.Locale.ROOT, "Campaign Playtest with cold validation: longest frame gap %.3f ms%n", worldLoadingGap / 1e6);
            require(worldLoadingGap < 500_000_000, "Cold campaign validation keeps the game rendering in Playtest.");
            capture("00-campaign-playtest");
        });
        replaceText(() -> poolPoint(1450, 98), () -> "missing neighbour");
        click(() -> poolPoint(1450, 138));
        STEPS.add(() -> {
            var query = ((imgui.type.ImString) field(editor, "playtestSearch")).get();
            require(query.equals("missing neighbour"), "Playtest exposes an editable search field.");
            var browser = (EditorContentBrowser) field(editor, "contentBrowser");
            var matches = browser.playtestEntries((AuthoringWorkspace) field(editor, "workspace"), "en_us", query);
            require(matches.size() == 1 && matches.getFirst().id().equals("villagerretaliation:missing_neighbor")
                    && matches.getFirst().title().equals("A Missing Neighbor"), "The default campaign's Missing Neighbor quest is discoverable by either spelling.");
            capture("00-campaign-playtest-search");
        });
        click(() -> poolPoint(1450, 170));
        STEPS.add(() -> require(((AuthoringDocument) field(editor, "document")).id().equals("villagerretaliation:missing_neighbor")
                && (int) field(editor, "view") == 3, "Selecting the search result opens Missing Neighbor inside Playtest."));
        click(() -> poolPoint(1870, 98));
        STEPS.add(() -> {
            require(((imgui.type.ImString) field(editor, "playtestSearch")).get().isEmpty(), "Clear restores the full Playtest content list.");
            capture("00-campaign-playtest-missing-neighbor");
            invoke("leavePlaytest", new Class<?>[] {int.class}, 0);
        });
        buildDialoguePoolSteps();
        buildResourceViewSteps();
        STEPS.add(() -> {
            ((imgui.type.ImString) field(editor, "workspacePath")).set(workspace.directory().toString());
            invoke("openWorkspace", new Class<?>[] {boolean.class}, false);
            workspace = (AuthoringWorkspace) field(editor, "workspace");
            document = workspace.documents().stream().filter(value -> value.id().equals("test:editor_smoke")).findFirst().orElseThrow();
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("filesMode", false);
            set("guideStep", 0);
            set("groupContent", false);
            set("showResources", false);
        });
    }

    @SubscribeEvent
    public static void checkConfirmationViewport(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        if (!confirmationChecks || !(event.getScreen() instanceof net.minecraft.client.gui.screens.ConfirmScreen)) return;
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, confirmationViewport);
        var projection = com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();
        confirmationPixelScaleX = Math.abs(projection.m00()) * confirmationViewport[2];
        confirmationPixelScaleY = Math.abs(projection.m11()) * confirmationViewport[3];
        confirmationFrames++;
    }

    private static void buildConfirmationViewportSteps() {
        int[] originalOptions = new int[2];
        float[] originalFraction = new float[1];
        STEPS.add(() -> {
            var mc = Minecraft.getInstance();
            originalOptions[0] = mc.options.guiScale().get();
            originalOptions[1] = mc.options.menuBackgroundBlurriness().get();
            originalFraction[0] = (float) field(editor, "playtestFraction");
            invoke("enterPlaytest", new Class<?>[0]);
            invoke("prepareTestWorld", new Class<?>[0]);
            require(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen, "Live test setup opens its confirmation in split Playtest.");
            confirmationChecks = true;
        });
        for (int variant = 0; variant < 3; variant++) {
            int index = variant;
            STEPS.add(() -> {
                var mc = Minecraft.getInstance();
                set("playtestFraction", new float[] {2F / 3, .55F, .75F}[index]);
                mc.options.guiScale().set(new int[] {3, 2, 4}[index]);
                mc.options.menuBackgroundBlurriness().set(index == 2 ? 0 : 5);
                mc.resizeDisplay();
                confirmationFrames = 0;
            });
            pause(250);
            STEPS.add(() -> {
                var layout = EditorPlaytestViewport.layout();
                var screen = Minecraft.getInstance().screen;
                require(confirmationFrames > 3, "The confirmation renders across multiple frames.");
                capture("00-live-world-confirmation-" + index);
                require(confirmationViewport[0] == 0 && confirmationViewport[1] == 0
                        && confirmationViewport[2] == layout.width() && confirmationViewport[3] == layout.height(),
                        "The confirmation restores the split framebuffer viewport after blur: " + java.util.Arrays.toString(confirmationViewport));
                require(Math.abs(confirmationPixelScaleX - confirmationPixelScaleY) < .01f,
                        "Confirmation text keeps equal horizontal and vertical pixel scales: " + confirmationPixelScaleX + " / " + confirmationPixelScaleY);
                require(screen.width == layout.guiWidth() && screen.height == layout.guiHeight(), "The dialog wraps within the game pane.");
                for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.Button button)
                    require(button.getX() >= 0 && button.getX() + button.getWidth() <= screen.width, "Confirmation buttons remain inside the game pane.");
            });
        }
        STEPS.add(() -> {
            var mc = Minecraft.getInstance();
            var cancel = mc.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals(net.minecraft.network.chat.CommonComponents.GUI_NO.getString())).findFirst().orElseThrow();
            mc.screen.mouseClicked(cancel.getX() + cancel.getWidth() / 2d, cancel.getY() + cancel.getHeight() / 2d, 0);
            require(mc.screen == field(editor, "screen"), "The resized No button cancels setup and returns to the editor.");
            mc.options.menuBackgroundBlurriness().set(5);
            invoke("leavePlaytest", new Class<?>[] {int.class}, 0);
            invoke("prepareTestWorld", new Class<?>[0]);
            confirmationFrames = 0;
        });
        pause(250);
        STEPS.add(() -> {
            var mc = Minecraft.getInstance();
            require(confirmationFrames > 3 && confirmationViewport[2] == mc.getWindow().getWidth()
                    && confirmationViewport[3] == mc.getWindow().getHeight(), "Full-window confirmations retain their native viewport.");
            require(Math.abs(confirmationPixelScaleX - confirmationPixelScaleY) < .01f, "Full-window confirmation text retains its proportions.");
            mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            confirmationChecks = false;
            mc.options.guiScale().set(originalOptions[0]);
            mc.options.menuBackgroundBlurriness().set(originalOptions[1]);
            set("playtestFraction", originalFraction[0]);
            mc.resizeDisplay();
        });
    }

    private static void buildCampaignLiveWorldSteps() {
        long[] rescueDeadline = {0L};
        var selectedPacks = new ArrayList<String>();
        STEPS.add(() -> liveCheck = Minecraft.getInstance().getSingleplayerServer().submit(() ->
                EditorWorldContent.source(Minecraft.getInstance().getSingleplayerServer())).thenApplyAsync(source ->
                EditorWorldContent.prepare(source, output().resolve("live-world-drafts"), null, "en_us", ignored -> { }), net.minecraft.Util.backgroundExecutor()));
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> {
            campaignWorkspace = ((EditorWorldContent.Prepared) liveCheck.join()).workspace();
            workspace = campaignWorkspace;
            set("workspace", workspace);
            document = workspace.documents().stream().filter(value -> value.id().equals("villagerretaliation:missing_neighbor")).findFirst().orElseThrow();
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            invoke("enterPlaytest", new Class<?>[0]);
            var server = Minecraft.getInstance().getSingleplayerServer();
            originalWorld = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
            selectedPacks.addAll(server.getPackRepository().getSelectedIds());
            var attempts = new java.util.concurrent.atomic.AtomicInteger();
            try {
                reloadCheck = EditorTestWorld.apply(workspace, target -> attempts.getAndIncrement() == 0
                        ? java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Injected initial setup failure"))
                        : target.reloadResources(target.getPackRepository().getSelectedIds()));
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        waitUntil(() -> reloadCheck.isDone());
        STEPS.add(() -> {
            require(reloadCheck.isCompletedExceptionally(), "Initial setup reports a failed reload.");
            require(!Files.exists(originalWorld.resolve("datapacks/test_content")) && !EditorTestWorld.active(workspace),
                    "Failed initial setup removes the unready pack from live use.");
            require(List.copyOf(Minecraft.getInstance().getSingleplayerServer().getPackRepository().getSelectedIds()).equals(selectedPacks),
                    "Failed initial setup restores the world's original pack selection.");
            invoke("prepareLiveTest", new Class<?>[0]);
        });
        waitUntil(() -> !EditorTestWorld.applying());
        STEPS.add(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            require(originalWorld.equals(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize()),
                    "Quest setup stays in the current world.");
            require(Minecraft.getInstance().screen == field(editor, "screen"), "Quest setup keeps the editor open.");
            require(EditorTestWorld.active(workspace) && EditorTestWorld.currentSnapshot(workspace),
                    "The full default campaign is ready for live testing: " + field(editor, "status"));
            for (String id : List.of("villagerretaliation:blank_map_promise", "villagerretaliation:missing_neighbor")) {
                document = workspace.documents().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
                invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
                require(EditorTestWorld.currentLogic(workspace), "Campaign quest setup is available for " + id);
            }
            EditorLiveQuestTest.inspect(workspace, document);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        STEPS.add(() -> {
            require(EditorLiveQuestTest.view().summary().equals("Not started"), "Inspecting an unstarted quest finishes without leaving live controls busy.");
            capture("90-campaign-current-world-ready");
            EditorLiveQuestTest.offer(workspace, document, false, false);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().state().equals("offered"));
        pause(10000);
        STEPS.add(() -> EditorLiveQuestTest.returnToProvider(workspace, document));
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        pause(1000);
        STEPS.add(() -> {
            var minecraft = Minecraft.getInstance();
            var provider = minecraft.level.getEntity(EditorLiveQuestTest.snapshot().provider());
            require(provider != null, "The rescue quest provider is loaded.");
            minecraft.gameMode.interact(minecraft.player, provider, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        pause(1200);
        STEPS.add(() -> {
            require(Minecraft.getInstance().screen instanceof com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen,
                    "The rescue provider opens the normal conversation screen.");
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var selection = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.selection(player);
                var provider = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(server, selection.provider());
                var quest = com.jvn.villagerretaliation.quest.VillagerQuestResources.quest(server, selection.quest()).orElseThrow();
                require(quest.offer().professions().contains(provider.getVillagerData().getProfession()),
                        "The rescue provider retains its authored profession without a workstation.");
                player.teleportTo(provider.getX() - 1, provider.getY(), provider.getZ());
                com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.require(player, selection.quest()).focusDialogue(true);
                var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(player.serverLevel(), player, provider);
                for (int step = 0; step < 2; step++) {
                    var choices = com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources.dialogueOptions(context,
                            com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService.moodFor(context));
                    choices = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestOffers.filter(context, choices, step > 0);
                    var choice = choices.stream().filter(option -> option.available()
                            && !option.id().equals(com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.LEAVE_OPTION_ID))
                            .findFirst().orElseThrow(() -> new IllegalStateException("Missing Neighbor has no natural offer: "
                                    + com.jvn.villagerretaliation.quest.VillagerQuestService.debugWhyAvailable(player, provider, selection.quest())));
                    com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player, provider.getId(), choice.id());
                }
            });
        });
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> {
            liveCheck.join();
            Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen) field(editor, "screen"));
            EditorLiveQuestTest.inspect(workspace, document);
            rescueDeadline[0] = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
        });
        waitUntil(() -> {
            require(System.nanoTime() < rescueDeadline[0], "Rescue acceptance timed out: "
                    + EditorLiveQuestTest.view() + " / " + EditorLiveQuestTest.snapshot());
            return EditorLiveQuestTest.snapshot().stage().equals("rescue");
        });
        STEPS.add(() -> {
            require(EditorLiveQuestTest.snapshot().state().equals("active"), "Missing Neighbor starts through its authored offer and acceptance.");
            capture("90a-missing-neighbor-accepted");
            EditorLiveQuestTest.travelTarget(workspace, document);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        pause(1000);
        STEPS.add(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var selection = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.selection(player);
                var rescue = com.jvn.villagerretaliation.quest.site.QuestSiteService.rescue(player, selection.quest());
                var captive = player.serverLevel().getEntity(rescue.entity());
                require(captive != null && !rescue.released(), "Travel reaches the actual captive before release.");
                require(com.jvn.villagerretaliation.quest.site.QuestSiteService.playtestEscort(player, selection.quest()) == null,
                        "Teleport assistance cannot bypass freeing the captive.");
                var guards = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Pillager.class, captive.getBoundingBox().inflate(20));
                require(guards.size() == 2, "The shipped rescue has its two authored guards.");
                guards.forEach(guard -> guard.hurt(player.damageSources().playerAttack(player), 1000));
            });
        });
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> {
            liveCheck.join();
            var minecraft = Minecraft.getInstance();
            var captive = minecraft.level.getEntity(EditorLiveQuestTest.rescue().entity());
            require(captive != null, "The captive is visible to the client.");
            minecraft.gameMode.interact(minecraft.player, captive, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        pause(1000);
        STEPS.add(() -> EditorLiveQuestTest.inspect(workspace, document));
        waitUntil(() -> EditorLiveQuestTest.rescue().released());
        STEPS.add(() -> {
            var minecraft = Minecraft.getInstance();
            var captive = minecraft.level.getEntity(EditorLiveQuestTest.rescue().entity());
            require(EditorLiveQuestTest.focusedRescueInteractions(captive.getId()), "The focused menu includes the rescued villager.");
            minecraft.gameMode.interact(minecraft.player, captive, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        pause(1000);
        STEPS.add(() -> {
            var interaction = Minecraft.getInstance().screen;
            require(interaction instanceof com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen,
                    "The released captive opens the interaction menu.");
            try {
                var method = interaction.getClass().getDeclaredMethod("interactionMenuButtons");
                method.setAccessible(true);
                var buttons = (java.util.List<?>) method.invoke(interaction);
                var ids = buttons.stream().map(button -> field(button, "id").toString()).toList();
                require(ids.equals(List.of("FOLLOW", "STAY")), "The rescue menu contains only escort commands: " + ids);
                require(buttons.stream().allMatch(button -> Boolean.TRUE.equals(field(button, "active"))),
                        "Escort commands work without recruitment trust.");
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
            capture("90b-rescued-villager-menu");
            EditorLiveQuestTest.commandRescue(EditorLiveQuestTest.rescue().entity(), false);
        });
        pause(500);
        STEPS.add(() -> EditorLiveQuestTest.commandRescue(EditorLiveQuestTest.rescue().entity(), true));
        pause(500);
        STEPS.add(() -> {
            Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen) field(editor, "screen"));
            EditorLiveQuestTest.bringRescue(workspace, document, true);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.bringRescue());
        STEPS.add(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var selection = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.selection(player);
                var provider = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(server, selection.provider());
                provider.setNoAi(true);
                provider.moveTo(provider.position().add(40, 0, 0));
            });
        });
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> {
            liveCheck.join();
            rescueDeadline[0] = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
            EditorLiveQuestTest.returnToProvider(workspace, document);
        });
        waitUntil(() -> {
            require(System.nanoTime() < rescueDeadline[0], "Rescue return timed out: " + EditorLiveQuestTest.view()
                    + " / " + EditorLiveQuestTest.rescue());
            return !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().stage().equals("return");
        });
        STEPS.add(() -> {
            require(EditorLiveQuestTest.rescue().rescued(), "Teleporting with the released captive satisfies the real rescue at the moved provider.");
            capture("90c-missing-neighbor-returned");
            var minecraft = Minecraft.getInstance();
            minecraft.gameMode.interact(minecraft.player, minecraft.level.getEntity(EditorLiveQuestTest.snapshot().provider()),
                    net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        pause(1000);
        STEPS.add(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var selection = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.selection(player);
                var provider = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(server, selection.provider());
                var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(player.serverLevel(), player, provider);
                for (int step = 0; step < 2; step++) {
                    var choices = com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources.dialogueOptions(context,
                            com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService.moodFor(context));
                    var choice = choices.stream().filter(option -> option.available()
                            && !option.id().equals(com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.LEAVE_OPTION_ID)).findFirst().orElseThrow();
                    com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player, provider.getId(), choice.id());
                }
                var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(player.serverLevel()).get(playerId, selection.quest());
                require(progress.state() == com.jvn.villagerretaliation.quest.VillagerQuestSavedData.QuestState.CONSUMED
                                && progress.completionCount() == 1,
                        "The one-time rescue completes and is consumed through authored dialogue.");
            });
        });
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> {
            liveCheck.join();
            Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen) field(editor, "screen"));
            invoke("closeEditor", new Class<?>[0]);
        });
        pause(750);
        STEPS.add(() -> {
            require(EditorLiveQuestTest.view().quest().isEmpty(), "Closing Playtest stops background live polling.");
            ImGuiAuthoringEditor.open();
            require(EditorPlaytestViewport.active(), "Reopening Playtest restores the split game viewport.");
        });
        pause(3000);
        STEPS.add(() -> EditorCampaignProfile.start("current-world-playtest-reopened"));
        pause(4000);
        STEPS.add(() -> {
            var profile = EditorCampaignProfile.stop(output());
            require(profile.medianMillis() < 8 && profile.p95Millis() < 16 && profile.allocatedMegabytes() < 4,
                    "Reopened Playtest remains responsive with the complete campaign: " + profile);
            capture("91-campaign-current-world-playtest");
            System.out.println("Default campaign quest testing passed in the current world, including setup rollback and reopened editor performance.");
            document = workspace.createDocument(DefinitionKind.QUEST, "test:current_world_setup");
            EditorContentTools.recipe(workspace, document, "Fetch quest");
            DialogueGraph.addNode(document, "finish");
            DialogueGraph.connect(document, "/stages/0/transition", "finish");
            document.edit("Require provider turn-in", root -> root.getAsJsonArray("stages").get(1).getAsJsonObject().remove("transition"));
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            try { reloadCheck = EditorTestWorld.apply(workspace); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        waitUntil(() -> reloadCheck.isDone());
        STEPS.add(() -> {
            reloadCheck.join();
            EditorLiveQuestTest.inspect(workspace, document);
            require(!EditorLiveQuestTest.view().busy(), "Background inspection never locks the playtest controls.");
            EditorLiveQuestTest.offer(workspace, document, false, false);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().state().equals("offered"));
        STEPS.add(() -> {
            require(!EditorLiveQuestTest.snapshot().hold(), "Natural playtesting runs without manual stepping.");
            EditorLiveQuestTest.returnToProvider(workspace, document);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        pause(1000);
        STEPS.add(() -> {
            var minecraft = Minecraft.getInstance();
            var provider = minecraft.level.getEntity(EditorLiveQuestTest.snapshot().provider());
            require(provider != null, "The natural test has a loaded provider.");
            minecraft.gameMode.interact(minecraft.player, provider, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        pause(1200);
        STEPS.add(() -> {
            var interaction = Minecraft.getInstance().screen;
            require(interaction instanceof com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen, "Talking to the test provider opens the normal interaction screen.");
            try {
                var method = interaction.getClass().getDeclaredMethod("interactionMenuButtons");
                method.setAccessible(true);
                var buttons = (java.util.List<?>) method.invoke(interaction);
                var ids = buttons.stream().map(button -> field(button, "id").toString()).toList();
                require(ids.contains("ADVENTURES") && ids.contains("INVENTORY") && ids.contains("FOLLOW"), "The normal test menu keeps quests, inventory, and follow: " + ids);
                require(ids.contains("PROFILE"), "Normal villager options remain available by default: " + ids);
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
            capture("91a-natural-quest-provider");
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var selection = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.selection(player);
                var provider = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(server, selection.provider());
                var data = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(player.serverLevel());
                require(data.get(playerId, selection.quest()) == null, "An offer waits for the player's acceptance.");
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
                    if (player.getInventory().getItem(slot).is(net.minecraft.world.item.Items.EMERALD))
                        player.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
                com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.require(player, selection.quest()).focusDialogue(true);
                var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(player.serverLevel(), player, provider);
                for (int step = 0; step < 8 && data.get(playerId, selection.quest()) == null; step++) {
                    var choices = com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources.dialogueOptions(context,
                            com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService.moodFor(context));
                    var choice = choices.stream().filter(option -> option.available() && !option.id().equals(com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.LEAVE_OPTION_ID))
                            .findFirst().orElseThrow(() -> new IllegalStateException("The natural quest offer has no available choice: " + choices));
                    com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player, provider.getId(), choice.id());
                }
                var progress = data.get(playerId, selection.quest());
                require(progress != null && progress.startedVillagerId().equals(provider.getUUID()), "Normal dialogue acceptance binds this exact quest provider.");
            });
        });
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> {
            liveCheck.join();
            EditorLiveQuestTest.inspect(workspace, document);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().state().equals("active")
                && !EditorLiveQuestTest.snapshot().objectives().isEmpty());
        STEPS.add(() -> EditorLiveQuestTest.prepare(workspace, document, EditorLiveQuestTest.snapshot().objectives().getFirst().id(), ""));
        waitUntil(() -> {
            require(!EditorLiveQuestTest.snapshot().state().equals("completed"), "The manual turn-in fixture must wait for the quest provider.");
            return !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().stage().equals("finish");
        });
        STEPS.add(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var selection = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.selection(player);
                var provider = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(server, selection.provider());
                var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(player.serverLevel(), player, provider);
                var choices = com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources.dialogueOptions(context,
                        com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService.moodFor(context));
                var turnIn = choices.stream().filter(option -> option.available() && option.questAction().action()
                        == com.jvn.villagerretaliation.dialogue.normal.DialogueQuestAction.Action.TURN_IN).findFirst().orElseThrow();
                com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player, provider.getId(), turnIn.id());
                var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(player.serverLevel()).get(playerId, selection.quest());
                require(progress.state() == com.jvn.villagerretaliation.quest.VillagerQuestSavedData.QuestState.COMPLETED,
                        "Normal dialogue turn-in completes the quest with its original provider.");
            });
        });
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> {
            liveCheck.join();
            Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen) field(editor, "screen"));
            EditorLiveQuestTest.start(workspace, document, true);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        STEPS.add(() -> {
            require(EditorLiveQuestTest.snapshot().hold() && !EditorLiveQuestTest.snapshot().objectives().isEmpty(),
                    "A quest starts with live requirement controls in the current world: " + EditorLiveQuestTest.view());
            EditorLiveQuestTest.prepare(workspace, document, EditorLiveQuestTest.snapshot().objectives().getFirst().id(), "");
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        pause(1500);
        STEPS.add(() -> {
            require(EditorLiveQuestTest.snapshot().ready(), "Requirement setup supplies real matching items in the current world: " + EditorLiveQuestTest.snapshot() + " / " + EditorLiveQuestTest.view());
            capture("92-current-world-quest-requirements");
            EditorLiveQuestTest.clear(workspace, document);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        STEPS.add(() -> require(EditorLiveQuestTest.view().summary().equals("Not started"), "Clearing the test quest keeps live controls responsive."));
    }

    private static void buildDialoguePoolSteps() {
        var pool = new AuthoringDocument[1];
        var preferences = new EditorPreferences[1];
        STEPS.add(() -> {
            var imported = (AuthoringWorkspace) field(editor, "workspace");
            pool[0] = imported.documents().stream().filter(value -> value.resourcePath().endsWith(
                    "/professions/fisherman/share_stories/03_share_stories__biomes_hot_dry.json")).findFirst().orElseThrow();
            invoke("select", new Class<?>[] {AuthoringDocument.class}, pool[0]);
            require(((EditorDialoguePool) field(editor, "dialoguePool")).supports(pool[0], pool[0].json()), "Bundled fisherman dialogue opens as pool cards.");
        });
        pause(500);
        STEPS.add(() -> { capture("00-dialogue-pool-cards"); EditorCampaignProfile.start("dialogue-pool"); });
        pause(4000);
        STEPS.add(() -> {
            var profile = EditorCampaignProfile.stop(output());
            require(profile.medianMillis() < 8 && profile.p95Millis() < 16 && profile.allocatedMegabytes() < 4,
                    "Dialogue pool cards stay responsive with the default campaign loaded: " + profile);
        });
        replaceText(() -> poolCardPoint(600, 675), () -> "<b>Edited on a pool card</b>, {player}.");
        click(() -> poolCardPoint(530, 350));
        STEPS.add(() -> {
            require(pool[0].value("/lines/0/lines/0").getAsString().contains("Edited on a pool card"), "Card text edits apply through the normal draft buffers.");
            require(((imgui.type.ImString) field(editor, "pointer")).get().equals("/lines/0"), "Details selects the pool entry's conditions.");
        });
        replaceText(() -> poolCardPoint(600, 548), () -> "121");
        click(() -> poolCardPoint(530, 350));
        STEPS.add(() -> require(pool[0].value("/lines/0/weight").getAsInt() == 121, "Pool selection weights are editable on cards."));
        click(() -> toolbar("Undo"));
        STEPS.add(() -> require(pool[0].value("/lines/0/weight").getAsInt() == 120, "Card field edits support undo."));
        click(() -> toolbar("Redo"));
        STEPS.add(() -> { require(pool[0].value("/lines/0/weight").getAsInt() == 121, "Card field edits support redo."); capture("00-dialogue-pool-edited"); });
        click(() -> poolCardPoint(462, 758));
        STEPS.add(() -> {
            require(pool[0].value("/lines/0/lines").getAsJsonArray().size() == 21, "Add line creates a new editable variation.");
            require(pool[0].value("/lines/0/lines/20").getAsString().equals("New dialogue."), "Add line selects a fresh variation.");
            capture("00-dialogue-pool-added-line");
        });
        click(() -> poolCardPoint(590, 758));
        STEPS.add(() -> require(pool[0].value("/lines/0/lines").getAsJsonArray().size() == 22, "Duplicate line preserves the variation: " + pool[0].value("/lines/0/lines").getAsJsonArray().size()));
        click(() -> poolCardPoint(725, 758));
        STEPS.add(() -> require(pool[0].value("/lines/0/lines").getAsJsonArray().size() == 21, "Remove line deletes only the chosen variation."));
        click(() -> poolCardPoint(498, 796));
        STEPS.add(() -> {
            require(pool[0].value("/lines").getAsJsonArray().size() == 8, "Duplicate entry copies the complete card.");
            require(pool[0].value("/lines/7/story_biome").getAsString().equals("minecraft:badlands"), "Card duplication preserves selection rules.");
        });
        STEPS.add(() -> invoke("setPointer", new Class<?>[] {String.class}, "/lines/0"));
        pause(250);
        click(() -> poolCardPoint(650, 796));
        STEPS.add(() -> require(pool[0].value("/lines").getAsJsonArray().size() == 7, "Remove entry deletes one card."));
        click(() -> toolbar("Undo"));
        STEPS.add(() -> require(pool[0].value("/lines/0/id").getAsString().equals("fisherman_share_story_badlands"), "Undo restores a deleted pool card."));
        click(() -> poolCardPoint(460, 274));
        STEPS.add(() -> require(pool[0].value("/lines").getAsJsonArray().size() == 9, "Add entry works in the pool toolbar."));
        click(() -> toolbar("Undo"));
        STEPS.add(() -> {
            require(((imgui.type.ImString) field(editor, "pointer")).get().isEmpty(), "Undoing a new card clears its removed Details selection.");
            invoke("setPointer", new Class<?>[] {String.class}, "/lines/0");
            preferences[0] = (EditorPreferences) field(editor, "preferences");
            set("preferences", preferences[0].withScale(1.5f));
        });
        pause(250);
        STEPS.add(() -> { capture("00-dialogue-pool-large-text"); set("preferences", preferences[0]); });
        STEPS.add(() -> {
            var imported = (AuthoringWorkspace) field(editor, "workspace");
            var root = new com.google.gson.JsonObject(); var lines = new com.google.gson.JsonArray();
            for (int i = 0; i < 500; i++) {
                var entry = pool[0].value("/lines/0").getAsJsonObject(); entry.addProperty("id", "stress_" + i); lines.add(entry);
            }
            root.add("lines", lines);
            var stress = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue/en_us/stress.json", root);
            imported.add(stress); invoke("select", new Class<?>[] {AuthoringDocument.class}, stress);
        });
        pause(500);
        STEPS.add(() -> EditorCampaignProfile.start("dialogue-pool-500-entries"));
        pause(4000);
        STEPS.add(() -> {
            var profile = EditorCampaignProfile.stop(output());
            require(profile.medianMillis() < 8 && profile.p95Millis() < 16 && profile.allocatedMegabytes() < 4,
                    "Large dialogue pools render only visible cards: " + profile);
            capture("00-dialogue-pool-500-entries");
        });
    }

    private static void buildResourceViewSteps() {
        var scene = new AuthoringDocument[1];
        STEPS.add(() -> {
            var imported = (AuthoringWorkspace) field(editor, "workspace");
            scene[0] = imported.documents().stream().filter(value -> value.resourcePath().endsWith(
                    "/last_ember/breath_after_the_roar/scenes/last_ember_charge.json")).findFirst().orElseThrow();
            invoke("select", new Class<?>[] {AuthoringDocument.class}, scene[0]);
            invoke("setPointer", new Class<?>[] {String.class}, "/steps/2");
            set("fieldFocus", "/steps/2/data/ticks");
        });
        pause(250);
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        type("75");
        click(() -> toolbar("Save"));
        STEPS.add(() -> require(scene[0].value("/steps/2/data/ticks").getAsInt() == 75, "Scene fields are editable in the work area."));
        click(() -> toolbar("Undo"));
        STEPS.add(() -> require(scene[0].value("/steps/2/data/ticks").getAsInt() == 50, "Scene work area edits support undo."));
        click(() -> toolbar("Redo"));
        STEPS.add(() -> {
            require(scene[0].value("/steps/2/data/ticks").getAsInt() == 75, "Scene work area edits support redo.");
            invoke("setPointer", new Class<?>[] {String.class}, "/steps/1");
        });
        pause(250);
        STEPS.add(() -> { capture("00-scene-step-editor"); EditorCampaignProfile.start("scene-resource"); });
        pause(4000);
        STEPS.add(() -> {
            var profile = EditorCampaignProfile.stop(output());
            require(profile.medianMillis() < 8 && profile.p95Millis() < 16 && profile.allocatedMegabytes() < 4, "Scene editing stays responsive: " + profile);
            invoke("setPointer", new Class<?>[] {String.class}, "/actors/1");
        });
        pause(250);
        STEPS.add(() -> capture("00-scene-actor-editor"));
        STEPS.add(() -> {
            var imported = (AuthoringWorkspace) field(editor, "workspace");
            var encounter = imported.documents().stream().filter(value -> value.resourcePath().endsWith("/encounters/atlas_risky_patrol.json")).findFirst().orElseThrow();
            invoke("select", new Class<?>[] {AuthoringDocument.class}, encounter);
        });
        pause(250);
        STEPS.add(() -> capture("00-encounter-editor"));
        STEPS.add(() -> {
            var imported = (AuthoringWorkspace) field(editor, "workspace");
            var messages = imported.documents().stream().filter(value -> value.resourcePath().endsWith("/professions/weaponsmith/messages/00_messages.json")).findFirst().orElseThrow();
            invoke("select", new Class<?>[] {AuthoringDocument.class}, messages);
        });
        pause(250);
        STEPS.add(() -> { capture("00-dialogue-message-editor"); EditorCampaignProfile.start("dialogue-message-resource"); });
        pause(4000);
        STEPS.add(() -> {
            var profile = EditorCampaignProfile.stop(output());
            require(profile.medianMillis() < 8 && profile.p95Millis() < 16 && profile.allocatedMegabytes() < 4, "Remaining dialogue formats edit one entry at a time: " + profile);
            var imported = (AuthoringWorkspace) field(editor, "workspace");
            var words = imported.documents().stream().filter(value -> value.resourcePath().endsWith("/dialogue_words/en_us/greetings/voice.json")).findFirst().orElseThrow();
            invoke("select", new Class<?>[] {AuthoringDocument.class}, words);
        });
        pause(250);
        STEPS.add(() -> capture("00-word-pool-editor"));
        STEPS.add(() -> {
            var imported = (AuthoringWorkspace) field(editor, "workspace");
            var root = new com.google.gson.JsonObject(); var messages = new com.google.gson.JsonObject();
            for (int i = 0; i < 5000; i++) messages.addProperty("translation." + i, "Readable translation " + i);
            root.add("messages", messages);
            var locale = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/quests/example/locales/en_us.json", root);
            imported.add(locale); invoke("select", new Class<?>[] {AuthoringDocument.class}, locale);
        });
        pause(500);
        STEPS.add(() -> { capture("00-translation-editor"); EditorCampaignProfile.start("translations-5000-entries"); });
        pause(4000);
        STEPS.add(() -> {
            var profile = EditorCampaignProfile.stop(output());
            require(profile.medianMillis() < 8 && profile.p95Millis() < 16 && profile.allocatedMegabytes() < 4, "Large translation resources render only visible entries: " + profile);
        });
    }

    private static float[] poolCardPoint(float x, float y) {
        return poolPoint(x, y + 40);
    }

    private static float[] poolPoint(float x, float y) {
        return new float[] {ImGui.getMainViewport().getPosX() + x, ImGui.getMainViewport().getPosY() + y};
    }

    private static void buildResizeSteps() {
        var previous = new AuthoringDocument[1];
        var original = new EditorGraph.Card[1];
        var history = new int[1];
        var position = new float[2];
        var previewScale = new float[1];
        STEPS.add(() -> {
            previous[0] = document;
            document = workspace.createDocument(DefinitionKind.TREE, "test:resize_graph");
            DialogueGraph.addNode(document, "finish");
            document.edit("Set node wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("start")
                    .getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Readable first line.\nSecond line.")));
            workspace.addDialogueResponse(document, "start", "go", "Continue", "finish");
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("view", 0); set("focusMode", ""); set("showGraphTools", false); set("showMinimap", true);
            set("zoom", .75f); set("fitGraph", false); set("panX", 25f); set("panY", 25f);
            original[0] = EditorGraph.cards(workspace, document, "en_us").getFirst();
            history[0] = document.undoLabels().size();
        });
        pause(250);
        STEPS.add(() -> previewScale[0] = (float) field(graphPreview(), "renderedScale") / .75f);
        for (float zoom : new float[] {.35f, .56f, 1, 2, .75f}) {
            STEPS.add(() -> set("zoom", zoom));
            pause(150);
            STEPS.add(() -> {
                var preview = graphPreview();
                float actualSize = (float) field(preview, "renderedScale") * Minecraft.getInstance().font.lineHeight;
                float expectedSize = previewScale[0] * zoom * Minecraft.getInstance().font.lineHeight;
                System.out.printf(java.util.Locale.ROOT, "Node text zoom %.2f: font %.2f px, expected %.2f px, viewport %d px%n",
                        zoom, actualSize, expectedSize, (int) field(preview, "renderedHeight"));
                require(Math.abs(actualSize - expectedSize) <= 1,
                        "Inline text follows node zoom " + zoom + ": " + actualSize + " vs " + expectedSize);
                require((int) field(preview, "renderedHeight") >= (int) (68 * zoom),
                        "Inline text uses the full field viewport at zoom " + zoom + ": " + field(preview, "renderedHeight"));
                if (zoom == .56f) capture("00-node-text-56-percent");
            });
        }
        STEPS.add(() -> {
            locateCanvas();
            System.arraycopy(positions().get("start"), 0, position, 0, 2);
            positions().put("finish", new float[] {position[0] + original[0].width() + 20, position[1]});
        });
        move(() -> point("start", original[0].width() - 4, original[0].height() - 4), false);
        move(() -> point("start", original[0].width() - 4, original[0].height() - 4), true);
        STEPS.add(() -> { mouseX += 120; mouseY += 90; });
        pause(200);
        STEPS.add(() -> {
            require(field(editor, "nodeResize") != null, "The overlapping node is still being resized.");
            assertResizedNodeInFront();
            capture("00-node-resizing-overlap");
        });
        STEPS.add(() -> mouseDown = false);
        pause(200);
        STEPS.add(() -> {
            var card = EditorGraph.cards(workspace, document, "en_us").getFirst();
            require(Math.abs(card.width() - original[0].width() - 160) < 1 && Math.abs(card.height() - original[0].height() - 120) < 1,
                    "The corner grip resizes both axes at the current zoom: " + card.width() + " x " + card.height());
            require(document.undoLabels().size() == history[0] + 1 && document.undoLabel().equals("Resize node"), "A complete drag creates one resize history entry.");
            require(positions().get("start")[0] == position[0] && positions().get("start")[1] == position[1], "Resizing preserves node position.");
            require(!(boolean) field(editor, "showBranch") && field(editor, "nodeResize") == null, "The grip avoids the footer action and finishes on release.");
            assertResizedNodeInFront();
            capture("00-node-resized");
        });
        click(() -> toolbar("Undo"));
        STEPS.add(() -> require(EditorGraph.cards(workspace, document, "en_us").getFirst().width() == original[0].width(), "Toolbar Undo restores node dimensions."));
        click(() -> toolbar("Redo"));
        STEPS.add(() -> require(EditorGraph.cards(workspace, document, "en_us").getFirst().width() > original[0].width(), "Toolbar Redo restores the resized node."));
        click(() -> point("finish", 300, 16));
        STEPS.add(() -> require(field(editor, "selectedNode").equals("finish"), "An exposed background header can be brought forward."));
        click(() -> point("start", 400, 16));
        STEPS.add(() -> require(field(editor, "selectedNode").equals("finish"), "Overlapping headers hit the foreground node."));
        click(() -> point("start", 60, 16));
        click(() -> point("start", 400, 100));
        STEPS.add(() -> {
            var selections = (Map<?, ?>) field(field(editor, "textFields"), "selections");
            require(field(editor, "selectedNode").equals("start") && (boolean) field(selections.get("graph:start:/nodes/start/lines:/nodes/start/lines/0"), "sourceMode"),
                    "The foreground text field receives overlapping clicks instead of the covered field.");
        });
        click(() -> port("start"));
        STEPS.add(() -> require(!field(editor, "selectedEdge").equals(""), "The output port follows the resized card."));
        key(ImGuiKey.Escape);
        STEPS.add(() -> {
            document = previous[0];
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("showMinimap", false);
        });
    }

    private static void assertResizedNodeInFront() {
        try (NativeImage image = renderedImage()) {
            float ox = ImGui.getMainViewport().getPosX(), oy = ImGui.getMainViewport().getPosY();
            var header = point("finish", 30, 16);
            require(image.getPixelRGBA((int) (header[0] - ox), (int) (header[1] - oy)) == 0xff65513d,
                    "The resized header covers the overlapping background header.");
            var reference = point("start", 300, 102);
            int background = image.getPixelRGBA((int) (reference[0] - ox), (int) (reference[1] - oy));
            var left = point("finish", 24, 98);
            var right = point("finish", 65, 108);
            for (int y = (int) (left[1] - oy); y < (int) (right[1] - oy); y++)
                for (int x = (int) (left[0] - ox); x < (int) (right[0] - ox); x++)
                    require(image.getPixelRGBA(x, y) == background, "The resized text field hides text from the covered node.");
        }
    }

    private static void buildSteps() {
        if (Boolean.getBoolean("villagerretaliation.authoringEditorCampaignSmokeOnly")) {
            buildCampaignOverviewSteps();
            finishSteps("Native campaign map selection, navigation, zoom and filters passed.");
            return;
        }
        if (Boolean.getBoolean("villagerretaliation.authoringEditorLocalizationSmokeOnly")) {
            buildLocalizationSteps();
            finishSteps("Native translation table and review controls passed.");
            return;
        }
        if (Boolean.getBoolean("villagerretaliation.authoringEditorResourceSmokeOnly")) {
            buildResourcePickerSteps();
            finishSteps("Visual resource search, icons, mod filters, entity previews, shortcuts and sound controls passed.");
            return;
        }
        if ("workflow".equals(System.getProperty("villagerretaliation.authoringEditorBufferSmokeOnly"))) {
            STEPS.add(() -> { set("guideStep", -1); set("resetLayout", true); });
            buildWorkflowSteps(); buildFeatureSteps(); buildProductivitySteps();
            finishSteps("Editor workflow, navigation, recovery and productivity controls passed.");
            return;
        }
        if ("setup".equals(System.getProperty("villagerretaliation.authoringEditorBufferSmokeOnly"))) {
            STEPS.add(() -> { set("guideStep", -1); set("resetLayout", true); });
            buildSetupSteps();
            return;
        }
        if ("files".equals(System.getProperty("villagerretaliation.authoringEditorBufferSmokeOnly"))) {
            STEPS.add(() -> { set("guideStep", -1); set("resetLayout", true); });
            buildCampaignSteps();
            finishSteps("File-tree actions, navigation with unfinished metadata and pack finalization passed.");
            return;
        }
        if (Boolean.getBoolean("villagerretaliation.authoringEditorBufferSmokeOnly")) {
            buildBufferSteps();
            finishSteps("Per-file drafts, keyboard navigation, partial saves and multi-file recovery passed.");
            return;
        }
        if (Boolean.getBoolean("villagerretaliation.authoringEditorPackFolderSmokeOnly")) {
            buildPackFolderSteps();
            finishSteps("Direct pack-folder saving and external-change review passed.");
            return;
        }
        if (Boolean.getBoolean("villagerretaliation.authoringEditorCompletionSmokeOnly")) {
            buildCompletionSteps();
            finishSteps("Inline text completion keyboard, formatting, plain labels and diagnostics passed.");
            return;
        }
        if ("quests".equals(System.getProperty("villagerretaliation.authoringEditorPlaytestSmokeOnly"))) {
            buildQuestAuthoringSteps();
            buildLiveWorldSteps();
            finishSteps("Guided quest authoring forms, linked content and current-world playtesting passed.");
            return;
        }
        if ("improvements".equals(System.getProperty("villagerretaliation.authoringEditorPlaytestSmokeOnly"))) {
            STEPS.add(() -> {
                try { workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-playtest-improvements")); }
                catch (IOException exception) { throw new UncheckedIOException(exception); }
                invoke("installWorkspace", new Class<?>[] {AuthoringWorkspace.class, boolean.class}, workspace, true);
                set("guideStep", -1);
            });
            buildLiveWorldSteps();
            buildDiscoveryPlaytestSteps();
            finishSteps("Playtest apply, replay, baselines, reload rollback, menu tabs and discovery quest completion passed.");
            return;
        }
        if (Boolean.getBoolean("villagerretaliation.authoringEditorPlaytestSmokeOnly")) {
            buildCampaignLiveWorldSteps();
            finishSteps("Current-world quest setup, reload recovery and Playtest performance passed.");
            return;
        }
        buildConfirmationViewportSteps();
        buildInlineSteps();
        buildWorldContentSteps();
        buildResizeSteps();
        buildLayoutSteps();
        buildVariableSteps();
        buildCompletionSteps();
        buildPackFolderSteps();
        buildBufferSteps();
        buildPanelSteps();
        buildPerformanceSteps();
        buildDropdownSteps();
        buildDeletionSteps();
        if (Boolean.getBoolean("villagerretaliation.authoringEditorWorkflowSmokeOnly")) {
            buildWorkflowSteps(); buildFeatureSteps(); buildProductivitySteps(); buildCampaignLiveWorldSteps(); finishSteps("Focused editor workflow, quality and productivity controls passed."); return;
        }
        STEPS.add(() -> {
            try { EditorClientChecks.questSimulationUsesRuntimeCompletionRules(); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        STEPS.add(() -> {
            require((int) field(editor, "guideStep") == 0, "First open shows the guide.");
            capture("01-first-open");
        });
        toolMenu("Hide guide");
        STEPS.add(() -> {
            require((int) field(editor, "guideStep") == -1, "Hide guide button dismisses onboarding.");
            try {
                require(EditorPreferences.load(output().resolve("config/authoring-editor.json")).guideDismissed(), "Guide dismissal persists.");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            capture("02-automatic-layout");
            locateCanvas();
        });
        buildCampaignSteps();
        click(() -> blockDetails("start", "/nodes/start/lines"));
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
        });
        click(() -> toolbar("Undo"));
        STEPS.add(() -> require(DialogueGraph.edges(document).getFirst().to().equals("finish"), "Navbar Undo restores the connection."));
        click(() -> toolbar("Redo"));
        STEPS.add(() -> require(DialogueGraph.edges(document).getFirst().to().equals("other"), "Navbar Redo restores the new connection."));
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
        toolMenu("Quick guide");
        STEPS.add(() -> {
            require((int) field(editor, "guideStep") == 0, "Quick guide reopens onboarding.");
            capture("06-guide-reopened");
        });
        toolMenu("Hide guide");
        click(() -> toolbar("Preview"));
        STEPS.add(() -> {
            require((int) field(editor, "view") == 1, "Preview toolbar control switches the rendered view.");
            require(((Map<?, ?>) field(field(editor, "graphText"), "previews")).isEmpty(),
                    "Leaving the graph releases its styled preview textures.");
            capture("07-preview");
        });
        click(() -> toolbar("Build pack"));
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
            var presentation = com.jvn.villagerretaliation.client.quest.VrQuestPresentation.DEFAULT;
            var custom = new com.jvn.villagerretaliation.client.quest.QuestPresentation(
                    presentation.textures(), presentation.colors(), presentation.translationPrefix(), 2);
            require(new com.jvn.villagerretaliation.client.quest.QuestHud(custom).visibleTrackerEntryCount(true, 3) == 2,
                    "Reusable quest HUD uses the consumer's presentation settings.");
            require(com.jvn.villagerretaliation.client.quest.VrQuestPresentation.HUD.visibleTrackerEntryCount(true, 4) == 3,
                    "VR retains its existing three-entry quest HUD.");
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
        var reopenedFrom = new AuthoringWorkspace[1];
        click(() -> toolbar("Workspace"));
        click(() -> screenPoint(120, 153));
        replaceText(() -> screenPoint(120, 297), () -> output().resolve("setup-drafts").toString());
        click(() -> screenPoint(200, 337));
        STEPS.add(() -> {
            workspace = (AuthoringWorkspace) field(editor, "workspace");
            require(workspace.directory().equals(output().resolve("setup-drafts")), "Create new opens the typed workspace folder.");
            require(workspace.documents().isEmpty(), "The new workspace starts empty.");
            capture("09-empty-workspace");
        });
        replaceText(() -> screenPoint(120, 337), () -> "test:from_scratch");
        click(() -> screenPoint(120, 377));
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
        STEPS.add(() -> capture("10g-response-editor"));
        replaceText(() -> screenPoint(450, 285), () -> RESPONSE);
        click(() -> screenPoint(450, 425));
        key(ImGuiKey.Home);
        key(ImGuiKey.DownArrow);
        key(ImGuiKey.DownArrow);
        key(ImGuiKey.Enter);
        click(() -> screenPoint(350, 465));
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
        click(() -> toolbar("Build pack"));
        STEPS.add(() -> {
            ((imgui.type.ImString) field(editor, "exportPath")).set(output().resolve("setup-export").toString());
            invoke("buildPackage", new Class<?>[] {});
        });
        STEPS.add(() -> {
            require(field(editor, "status").toString().startsWith("Built pack at "), "Save and export reports success.");
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
        STEPS.add(() -> reopenedFrom[0] = workspace);
        click(() -> screenPoint(85, 337));
        STEPS.add(() -> {
            workspace = (AuthoringWorkspace) field(editor, "workspace");
            require(workspace != reopenedFrom[0] && workspace.documents().size() == 1, "Open folder reloads the saved workspace.");
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
        buildQualityOfLifeSteps();
        buildResourcePickerSteps();
        buildWorkflowSteps();
        buildFeatureSteps();
        buildProductivitySteps();
        finishSteps("Full editor graph, pack, camera, playtest, recovery and workflow checks passed.");
    }

    private static void toolMenu(String name) {
        click(() -> toolbar("Tools"));
        key(ImGuiKey.Home);
        int index = switch (name) { case "Quick guide", "Hide guide" -> 0; case "Keyboard shortcuts" -> 1; default -> throw new IllegalArgumentException(name); };
        for (int i = 0; i < index; i++) key(ImGuiKey.DownArrow);
        key(ImGuiKey.Enter);
    }

    private static void buildDeletionSteps() {
        var originalWorkspace = new AuthoringWorkspace[1];
        var originalDocument = new AuthoringDocument[1];
        STEPS.add(() -> {
            originalWorkspace[0] = workspace; originalDocument[0] = document;
            try { workspace = AuthoringWorkspace.create(output().resolve("delete-key-drafts")); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            set("workspace", workspace);
            document = workspace.createDocument(DefinitionKind.TREE, "test:delete_keyboard");
            DialogueGraph.addNode(document, "discard"); DialogueGraph.addNode(document, "keep");
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("view", 0); set("guideStep", -1); set("showGraphTools", false); set("fitGraph", true);
        });
        pause(250);
        STEPS.add(AuthoringEditorClientSmoke::locateCanvas);
        click(() -> point("discard", 80, 18));
        STEPS.add(() -> { deletionTyping = true; deletionFocus = true; });
        pause(250);
        STEPS.add(() -> require(ImGui.getIO().getWantTextInput(), "A focused text field captures keyboard input."));
        key(ImGuiKey.Delete);
        STEPS.add(() -> {
            require(DialogueGraph.nodes(document).size() == 3, "Delete in a text field keeps the selected node.");
            deletionTyping = false;
        });
        pause(150);
        click(() -> point("discard", 80, 18));
        key(ImGuiKey.Delete);
        STEPS.add(() -> require(DialogueGraph.nodes(document).stream().noneMatch(node -> node.id().equals("discard")), "Delete removes the selected graph node."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Z);
        STEPS.add(() -> {
            require(DialogueGraph.nodes(document).size() == 3, "Ctrl+Z restores the deleted node.");
            workspace.addDialogueResponse(document, "start", "continue", "Continue", "discard");
            invoke("refresh", new Class<?>[0]);
        });
        click(() -> point("discard", 80, 18));
        key(ImGuiKey.Delete);
        STEPS.add(() -> {
            require(DialogueGraph.nodes(document).size() == 3 && ImGui.isPopupOpen("Delete nodes###node_delete"), "Connected deletion opens the replacement picker without changing the draft.");
            capture("78-delete-reconnect");
        });
        key(ImGuiKey.Escape);
        STEPS.add(() -> {
            require(DialogueGraph.nodes(document).size() == 3, "Cancel preserves connected nodes.");
            capture("79-creator-scene");
            workspace = originalWorkspace[0]; document = originalDocument[0];
            set("workspace", workspace);
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("guideStep", 0); set("fitGraph", true);
        });
    }

    private static void buildLayoutSteps() {
        for (int variant : new int[] {1, 2, 3}) {
            float scale = variant == 1 ? 1 : 2;
            STEPS.add(() -> {
                if (variant == 3) {
                    long window = Minecraft.getInstance().getWindow().getWindow();
                    GLFW.glfwRestoreWindow(window);
                    GLFW.glfwSetWindowSize(window, 1280, 800);
                    Minecraft.getInstance().resizeDisplay();
                }
                set("preferences", ((EditorPreferences) field(editor, "preferences")).withScale(scale));
                shortcutPopupSize = null;
                shortcutPopupFrames = 0;
                layoutChecks = true;
            });
            if (variant == 1) toolMenu("Keyboard shortcuts");
            else STEPS.add(() -> ((EditorShortcuts) field(editor, "shortcuts")).open());
            pause(1200);
            STEPS.add(() -> {
                require(shortcutPopupFrames >= 20, "The actual Shortcuts popup remains open across frames.");
                capture(variant == 1 ? "64-shortcuts-popup" : variant == 2 ? "65-shortcuts-popup-large-text" : "66-shortcuts-popup-compact");
                layoutChecks = false;
            });
            key(ImGuiKey.Escape);
        }
        STEPS.add(() -> {
            GLFW.glfwMaximizeWindow(Minecraft.getInstance().getWindow().getWindow());
            Minecraft.getInstance().resizeDisplay();
            set("preferences", ((EditorPreferences) field(editor, "preferences")).withScale(1));
            set("resetLayout", true);
        });
        pause(250);
        STEPS.add(() -> capture("67-details-panel-alignment"));
    }

    private static void checkShortcutPopup() {
        if (!layoutChecks) return;
        float fontScale = ImGui.getStyle().getFontScaleMain();
        ImGui.getStyle().setFontScaleMain(fontScale * ((EditorPreferences) field(editor, "preferences")).textScale());
        if (!ImGui.beginPopup("Customize shortcuts")) { ImGui.getStyle().setFontScaleMain(fontScale); return; }
        try {
            float width = ImGui.getWindowWidth(), height = ImGui.getWindowHeight();
            if (++shortcutPopupFrames > 3) {
                if (shortcutPopupSize == null) shortcutPopupSize = new float[] {width, height};
                require(Math.abs(width - shortcutPopupSize[0]) < 1 && Math.abs(height - shortcutPopupSize[1]) < 1,
                        "The actual Shortcuts popup keeps a stable size across frames: " + width + " x " + height);
                var viewport = ImGui.getMainViewport();
                require(width <= viewport.getWorkSizeX() && height <= viewport.getWorkSizeY(), "The Shortcuts popup fits the viewport.");
                require(ImGui.getScrollMaxY() > 0, "The shortcut list scrolls inside its popup.");
                float scale = ((EditorPreferences) field(editor, "preferences")).textScale();
                if (scale == 1) shortcutBaseFontSize = ImGui.getTextLineHeight();
                require(shortcutBaseFontSize > 0 && Math.abs(ImGui.getTextLineHeight() - shortcutBaseFontSize * scale) <= 1,
                        "The Shortcuts popup uses the editor text scale: font=" + ImGui.getTextLineHeight() + ", base=" + shortcutBaseFontSize + ", scale=" + scale);
            }
        } finally {
            ImGui.endPopup();
            ImGui.getStyle().setFontScaleMain(fontScale);
        }
    }

    private static void buildBufferSteps() {
        var original = new AuthoringWorkspace[1];
        var priorDocument = new AuthoringDocument[1];
        var guide = new int[1];
        String firstPath = "data/test/dialogue_trees/en_us/first.json", secondPath = "data/test/dialogue_trees/en_us/second.json";
        STEPS.add(() -> {
            original[0] = workspace; priorDocument[0] = document; guide[0] = (int) field(editor, "guideStep");
            set("resetLayout", true);
            try {
                EditorBufferChecks.supportingFilesKeepIndependentDraftsAndBaselines();
                EditorClientChecks.packFileEditorsPreservePendingEdits();
                EditorClientChecks.packFinalizationPreservesAdvancedMetadataAndRecovery();
                workspace = AuthoringWorkspace.create(output().resolve("buffer-workspace"));
                String json = "{\"nodes\":{\"start\":{\"lines\":[\"Original\"],\"responses\":[]}},\"entries\":[{\"id\":\"default\",\"label\":\"Talk\",\"start\":\"start\"}]}";
                workspace.importResources(Map.of(firstPath, json.getBytes(java.nio.charset.StandardCharsets.UTF_8), secondPath, json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                workspace.save();
                invoke("installWorkspace", new Class<?>[] {AuthoringWorkspace.class, boolean.class}, workspace, false);
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            set("guideStep", -1);
            invoke("openPackFile", new Class<?>[] {String.class}, firstPath);
            invoke("openPackFile", new Class<?>[] {String.class}, secondPath);
            invoke("openPackFile", new Class<?>[] {String.class}, firstPath);
            document = (AuthoringDocument) field(editor, "document");
        });
        pause(300);
        STEPS.add(() -> {
            var command = ((List<EditorCommands.Command>) invoke("commands", new Class<?>[0])).stream()
                    .filter(value -> value.id().equals("Duplicate selected node")).findFirst().orElseThrow();
            invoke("openPackFile", new Class<?>[] {String.class}, secondPath);
            var next = (AuthoringDocument) field(editor, "document");
            var before = next.json();
            invoke("attempt", new Class<?>[] {ImGuiAuthoringEditor.EditorOperation.class}, command.action());
            require(next.json().equals(before) && field(editor, "status").toString().contains("Selection changed"),
                    "A queued edit command cannot mutate a different file after navigation.");
            invoke("openPackFile", new Class<?>[] {String.class}, firstPath);
        });
        pause(250);
        replaceText(() -> new float[] {1700, 380}, () -> "Typed first file");
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Tab);
        pause(250);
        STEPS.add(() -> {
            document = (AuthoringDocument) field(editor, "document");
            require(document.resourcePath().equals(secondPath), "Typing in Properties does not block file navigation.");
            require(!((EditorFieldEdits) field(editor, "pendingFields")).pending(), "The active text widget does not copy one file's input into another file.");
            var first = workspace.documents().stream().filter(value -> value.resourcePath().equals(firstPath)).findFirst().orElseThrow();
            require(((EditorDocumentBuffers) field(editor, "documentBuffers")).fields(first).text("/nodes/start/lines/0", "").equals("Typed first file"),
                    "Property text stays pending in the file it belongs to.");
        });
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.S);
        STEPS.add(() -> require(document.value("/nodes/start/lines/0").getAsString().equals("Original"), "Saving all drafts leaves the second file's wording unchanged."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Tab);
        STEPS.add(() -> {
            document = (AuthoringDocument) field(editor, "document");
            ((imgui.type.ImString) field(editor, "jsonText")).set("");
            set("rawModified", true);
            invoke("captureRawDraft", new Class<?>[0]);
        });
        pause(300);
        click(() -> new float[] {650, 500});
        type("{first");
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Tab);
        STEPS.add(() -> {
            document = (AuthoringDocument) field(editor, "document");
            require(document.resourcePath().equals(secondPath), "Ctrl+Tab leaves an unfinished JSON draft without applying it.");
            var first = workspace.documents().stream().filter(value -> value.resourcePath().equals(firstPath)).findFirst().orElseThrow();
            require(((EditorDocumentBuffers) field(editor, "documentBuffers")).raw(first).equals("{first"), "The first file retains its exact typed JSON.");
            ((EditorFieldEdits) field(editor, "pendingFields")).update("/nodes/start/lines/0", document.value("/nodes/start/lines/0"), "Saved in second file");
            invoke("refresh", new Class<?>[0]);
        });
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.S);
        STEPS.add(() -> {
            try {
                var saved = AuthoringWorkspace.open(workspace.directory());
                require(saved.documents().stream().filter(value -> value.resourcePath().equals(secondPath)).findFirst().orElseThrow()
                        .value("/nodes/start/lines/0").getAsString().equals("Saved in second file"), "Ctrl+S saves valid edits while another file has invalid JSON.");
                require(workspace.recoveryAvailable(), "Partial saves retain recovery for unfinished files.");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Tab);
        STEPS.add(() -> {
            document = (AuthoringDocument) field(editor, "document");
            require(document.resourcePath().equals(firstPath) && ((imgui.type.ImString) field(editor, "jsonText")).get().equals("{first"),
                    "Returning to a file restores its unfinished JSON editor.");
            capture("buffers-01-retained-draft");
        });
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Tab);
        STEPS.add(() -> {
            document = (AuthoringDocument) field(editor, "document");
            ((imgui.type.ImString) field(editor, "jsonText")).set("");
            set("rawModified", true); invoke("captureRawDraft", new Class<?>[0]);
        });
        pause(250);
        click(() -> new float[] {650, 500});
        type("{second");
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.S);
        STEPS.add(() -> {
            require(((Map<?, ?>) field(editor, "bufferIssues")).size() == 2, "Each invalid file is reported separately.");
            capture("buffers-02-two-drafts");
        });
        click(() -> new float[] {490, 154});
        STEPS.add(() -> require(((AuthoringDocument) field(editor, "document")).resourcePath().equals(firstPath)
                && ((imgui.type.ImString) field(editor, "jsonText")).get().equals("{first"), "Clicking an inactive tab restores that file's draft."));
        click(() -> new float[] {620, 154});
        STEPS.add(() -> {
            require(((AuthoringDocument) field(editor, "document")).resourcePath().equals(secondPath), "Tab clicks follow the active file.");
            try {
                var saved = AuthoringWorkspace.open(workspace.directory());
                invoke("installWorkspace", new Class<?>[] {AuthoringWorkspace.class, boolean.class}, saved, false);
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            require((boolean) field(editor, "showRecovery"), "Reopening offers recovery after a partial save.");
            invoke("recoverDrafts", new Class<?>[0]);
            workspace = (AuthoringWorkspace) field(editor, "workspace");
            var buffers = (EditorDocumentBuffers) field(editor, "documentBuffers");
            require(buffers.raw(workspace.documents().stream().filter(value -> value.resourcePath().equals(firstPath)).findFirst().orElseThrow()).equals("{first")
                    && buffers.raw(workspace.documents().stream().filter(value -> value.resourcePath().equals(secondPath)).findFirst().orElseThrow()).equals("{second"),
                    "Recovery restores all unfinished files, including inactive tabs.");
            invoke("openPackFile", new Class<?>[] {String.class}, firstPath);
            document = (AuthoringDocument) field(editor, "document");
        });
        pause(300);
        STEPS.add(() -> capture("buffers-03-recovered"));
        replaceText(() -> new float[] {650, 500}, () -> {
            var repaired = document.json();
            repaired.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Repaired first file"));
            return repaired.toString();
        });
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.S);
        STEPS.add(() -> {
            require(document.value("/nodes/start/lines/0").getAsString().equals("Repaired first file"), "Ctrl+S applies a repaired full JSON draft.");
        });
        click(() -> new float[] {690, 154});
        STEPS.add(() -> require(((EditorOpenFiles) field(editor, "openFiles")).entries(workspace).stream().noneMatch(entry -> entry.currentPath().equals(secondPath)),
                "Closing a tab hides it without discarding its draft."));
        click(() -> new float[] {225, 469});
        STEPS.add(() -> {
            document = (AuthoringDocument) field(editor, "document");
            require(document.resourcePath().equals(secondPath), "The file browser reopens a closed draft.");
            require(((imgui.type.ImString) field(editor, "jsonText")).get().equals("{second"), "Closing and reopening a tab keeps its unfinished input.");
            ((EditorDocumentBuffers) field(editor, "documentBuffers")).discardRaw(document);
            set("rawModified", false); invoke("refresh", new Class<?>[0]); invoke("save", new Class<?>[0]);
            require(!workspace.recoveryAvailable(), "Resolving the last draft clears recovery.");
            workspace = original[0]; document = priorDocument[0];
            invoke("installWorkspace", new Class<?>[] {AuthoringWorkspace.class, boolean.class}, workspace, false);
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("guideStep", guide[0]);
        });
    }

    private static void buildPackFolderSteps() {
        var original = new AuthoringWorkspace[1];
        var priorDocument = new AuthoringDocument[1];
        var guide = new int[1];
        Path pack = output().resolve("editable-pack");
        String path = "data/test/dialogue_trees/en_us/folder_edit.json";
        STEPS.add(() -> {
            original[0] = workspace; priorDocument[0] = document; guide[0] = (int) field(editor, "guideStep");
            set("resetLayout", true);
            try {
                Files.createDirectories(pack.resolve(path).getParent());
                Files.writeString(pack.resolve("pack.mcmeta"), AuthoringDocument.JSON.toJson(workspace.packMetadata()));
                Files.writeString(pack.resolve(path), "{\"id\":\"test:folder_edit\",\"nodes\":{\"start\":{\"lines\":[\"Original\"],\"responses\":[]}},\"entries\":[{\"id\":\"default\",\"label\":\"Talk\",\"start\":\"start\"}]}");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            ((imgui.type.ImString) field(editor, "workspacePath")).set(pack.toString());
            invoke("openWorkspace", new Class<?>[] {boolean.class}, false);
            workspace = (AuthoringWorkspace) field(editor, "workspace");
            require(workspace.editsPackFolder() && workspace.location().equals(pack), "Open folder recognizes ordinary datapacks.");
            document = workspace.documents().getFirst();
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("guideStep", -1); set("filesMode", false);
        });
        pause(300);
        STEPS.add(() -> {
            capture("pack-folder-01-open");
            document.edit("Edit source wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Edited in game")));
            invoke("refresh", new Class<?>[0]);
        });
        click(() -> toolbar("Save"));
        STEPS.add(() -> {
            try {
                require(Files.readString(pack.resolve(path)).contains("Edited in game"), "The Save button writes the original pack file.");
                require(!Files.exists(pack.resolve("workspace.json")), "Pack-folder editing keeps workspace metadata outside the source.");
                var disk = document.json();
                disk.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Edited elsewhere"));
                Files.writeString(pack.resolve(path), AuthoringDocument.JSON.toJson(disk));
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            document.edit("Conflicting wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Keep in game")));
            var unfinished = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/custom/unfinished.json", new com.google.gson.JsonObject());
            workspace.add(unfinished);
            ((EditorDocumentBuffers) field(editor, "documentBuffers")).raw(unfinished, "{unfinished");
            invoke("refresh", new Class<?>[0]);
            ((EditorPackFolder) field(editor, "packFolder")).refresh();
        });
        pause(2400);
        STEPS.add(() -> {
            var panel = field(editor, "packFolder");
            require(!((List<?>) field(panel, "changes")).isEmpty(), "External edits are detected without reopening the pack.");
        });
        click(() -> new float[] {160, 210});
        pause(250);
        STEPS.add(() -> capture("pack-folder-02-conflict"));
        click(() -> new float[] {500, 260});
        click(() -> toolbar("Save"));
        STEPS.add(() -> {
            require(document.value("/nodes/start/lines/0").getAsString().equals("Edited elsewhere"), "The disk version can replace the conflicting editor version.");
            try {
                var disk = document.json();
                disk.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Changed again elsewhere"));
                Files.writeString(pack.resolve(path), AuthoringDocument.JSON.toJson(disk));
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            document.edit("Keep local wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Keep in game")));
            invoke("refresh", new Class<?>[0]);
            ((EditorPackFolder) field(editor, "packFolder")).refresh();
        });
        pause(2400);
        click(() -> new float[] {160, 210});
        pause(250);
        click(() -> new float[] {680, 260});
        click(() -> toolbar("Save"));
        STEPS.add(() -> {
            try { require(Files.readString(pack.resolve(path)).contains("Keep in game"), "Keeping the editor version saves the chosen content to disk."); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            var unfinished = workspace.documents().stream().filter(value -> value.resourcePath().equals("data/test/custom/unfinished.json")).findFirst().orElseThrow();
            var buffers = (EditorDocumentBuffers) field(editor, "documentBuffers");
            require("{unfinished".equals(buffers.raw(unfinished)), "Resolving another file's external conflict preserves an unrelated invalid draft.");
            buffers.discardRaw(unfinished); invoke("save", new Class<?>[0]);
            capture("pack-folder-03-resolved");
            workspace = original[0]; document = priorDocument[0];
            invoke("installWorkspace", new Class<?>[] {AuthoringWorkspace.class, boolean.class}, workspace, false);
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("guideStep", guide[0]);
        });
    }

    private static void buildCompletionSteps() {
        STEPS.add(() -> {
            variableControls = true; variableTextScale = 1;
            variableText.set("Hello, "); variableFields.clear(); variableFields.focus("variable_smoke");
        });
        pause(250);
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.End);
        type("{pla");
        pause(250);
        STEPS.add(() -> capture("completion-01-variables"));
        key(ImGuiKey.DownArrow); key(ImGuiKey.UpArrow); key(ImGuiKey.Tab);
        STEPS.add(() -> require(variableText.get().equals("Hello, {player}"), "Tab inserts the selected variable without tabs or extra braces. Actual: " + variableText.get()));
        type(" <bo");
        key(ImGuiKey.Enter);
        type("friend");
        STEPS.add(() -> require(variableText.get().equals("Hello, {player} <b>friend</b>"), "Enter inserts paired tags and keeps typing between them. Actual: " + variableText.get()));
        type(" {que");
        key(ImGuiKey.Escape);
        STEPS.add(() -> require(variableText.get().contains("{que") && imgui.internal.ImGui.getActiveID() != 0 && !(boolean) field(editor, "confirmClose"), "Escape dismisses completion while retaining text and focus."));
        type("st}");
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        type("<b>Hi</");
        key(ImGuiKey.Tab);
        STEPS.add(() -> require(variableText.get().equals("<b>Hi</b>"), "Closing suggestions match the innermost open tag."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        type("Wrap me");
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Space);
        key(ImGuiKey.Enter);
        STEPS.add(() -> require(variableText.get().equals("<b>Wrap me</b>"), "Ctrl+Space wraps selected text and does not insert a space."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.End);
        type(" <color:go");
        pause(250);
        STEPS.add(() -> capture("completion-02-colors"));
        key(ImGuiKey.Enter);
        type("Gold");
        STEPS.add(() -> require(variableText.get().endsWith("<color:gold>Gold</color>"), "Color completion pairs its closing tag."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.End);
        type(" <pau");
        key(ImGuiKey.Enter);
        STEPS.add(() -> require(variableText.get().endsWith("<pause=500>"), "Dialogue cues have no closing tag."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        type("<color:");
        STEPS.add(() -> capture("completion-05a-before-color-click"));
        click(() -> completionPoint(28, 55));
        STEPS.add(() -> {
            require(ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId | imgui.flag.ImGuiPopupFlags.AnyPopupLevel), "The completion color swatch opens its picker.");
            capture("completion-05-custom-color-picker");
        });
        key(ImGuiKey.Escape);
        click(() -> completionPoint(100, 97));
        type("Custom");
        STEPS.add(() -> require(variableText.get().equals("<color:#ffaa00>Custom</color>"), "Custom color insertion restores focus and the caret inside its span. Actual: " + variableText.get()));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        type("{pla");
        STEPS.add(() -> capture("completion-06-before-mouse-variable"));
        click(() -> completionPoint(80, 52));
        type("!");
        STEPS.add(() -> require(variableText.get().equals("{player}!"), "Mouse selection accepts a variable and restores typing. Actual: " + variableText.get()));
        for (boolean inline : new boolean[] {false, true}) {
            STEPS.add(() -> {
                completionInline = inline; completionPlain = !inline; variableTextScale = inline ? 2 : 1;
                variableFields.clear(); variableFields.focus("variable_smoke");
            });
            pause(250);
            chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
            type("{pla");
            key(ImGuiKey.Tab);
            STEPS.add(() -> require(variableText.get().equals("{player}"), "Variable completion works in " + (inline ? "inline fields" : "plain labels") + "."));
            type(" {my_pack:score}");
            pause(250);
            STEPS.add(() -> capture(inline ? "completion-04-inline-large-text" : "completion-03-plain-label"));
        }
        STEPS.add(() -> { completionInline = false; completionPlain = false; variableControls = false; variableFields.clear(); variableTextScale = 1; });
    }

    private static float[] completionPoint(float offsetX, float offsetY) {
        var selection = ((Map<?, ?>) field(variableFields, "selections")).get("variable_smoke");
        var assist = field(selection, "assist");
        float scale = Math.max(1, (float) field(assist, "fontSize") / 16);
        var viewport = ImGui.getMainViewport();
        float width = Math.min(440 * scale, viewport.getWorkSizeX() - 24);
        float height = Math.min(300 * scale, viewport.getWorkSizeY() - 24);
        return new float[] {
                Math.clamp((float) field(assist, "x"), viewport.getWorkPosX() + 8, viewport.getWorkPosX() + viewport.getWorkSizeX() - width - 8) + offsetX,
                Math.clamp((float) field(assist, "y") + 3, viewport.getWorkPosY() + 8, viewport.getWorkPosY() + viewport.getWorkSizeY() - height - 8) + offsetY
        };
    }

    private static void buildVariableSteps() {
        STEPS.add(() -> { variableControls = true; variableFields.focus("variable_smoke"); });
        pause(250);
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.End);
        click(() -> variableMenuPoint);
        pause(250);
        STEPS.add(() -> capture("68-variable-categories"));
        type("objective_item");
        key(ImGuiKey.DownArrow);
        key(ImGuiKey.UpArrow);
        key(ImGuiKey.DownArrow);
        pause(250);
        STEPS.add(() -> {
            require(((imgui.type.ImString) field(field(variableFields, "variables"), "search")).get().equals("objective_item"), "Variable search updates before Enter is pressed.");
            capture("69-variable-search");
        });
        key(ImGuiKey.Enter);
        STEPS.add(() -> require(variableText.get().equals("Hello, {objective_item_id}"), "Searching, arrow selection and Enter insert a previously missing quest variable at the caret."));
        type(" and ");
        click(() -> variableMenuPoint);
        type("{parent}");
        key(ImGuiKey.Enter);
        STEPS.add(() -> require(variableText.get().equals("Hello, {objective_item_id} and {parent}"), "Reopening clears search and exact variable names rank ahead of partial matches."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        click(() -> variableMenuPoint);
        type("held_item_damage");
        key(ImGuiKey.Enter);
        type("!");
        STEPS.add(() -> {
            require(variableText.get().equals("{held_item_damage}!"), "Variable selection replaces selected source text and restores its caret.");
        });
        click(() -> variableMenuPoint);
        type("not_a_builtin_variable");
        key(ImGuiKey.Tab);
        type("trigger_criterion_boss_killed");
        pause(250);
        STEPS.add(() -> capture("70-custom-variable"));
        key(ImGuiKey.Enter);
        STEPS.add(() -> {
            require(variableText.get().equals("{held_item_damage}!{trigger_criterion_boss_killed}"), "Custom variable entry inserts once and restores source focus.");
            variableTextScale = 2;
        });
        pause(250);
        click(() -> variableMenuPoint);
        pause(250);
        STEPS.add(() -> capture("71-variable-picker-large-text"));
        key(ImGuiKey.Escape);
        STEPS.add(() -> { variableControls = false; variableFields.clear(); });
    }

    private static void renderVariableControls() {
        if (!variableControls) return;
        invoke("pushEditorStyle", new Class<?>[0]);
        float fontScale = ImGui.getStyle().getFontScaleMain();
        ImGui.getStyle().setFontScaleMain(fontScale * variableTextScale);
        ImGui.setNextWindowPos(450, 150); ImGui.setNextWindowSize(800, 600);
        ImGui.begin("Text variable acceptance"); ImGui.setWindowFontScale(1);
        float x = ImGui.getCursorScreenPosX();
        for (String label : List.of("B", "I", "U")) x += ImGui.calcTextSize(label).x + 8 + ImGui.getStyle().getItemSpacingX();
        variableMenuPoint = new float[] {x + (ImGui.calcTextSize("Insert variable").x + 8) / 2,
                ImGui.getCursorScreenPosY() + ImGui.getTextLineHeight() / 2};
        if (completionInline) variableFields.inline("variable_smoke", variableText, ImGui.getContentRegionAvailX(), 180 * variableTextScale);
        else variableFields.render("variable_smoke", variableText, DefinitionKind.TREE, !completionPlain, variableTextScale);
        ImGui.dummy(0, 0);
        ImGui.end();
        ImGui.popStyleColor(14); ImGui.popStyleVar(5);
        ImGui.getStyle().setFontScaleMain(fontScale);
    }

    private static void buildPanelSteps() {
        for (float scale : new float[] {1, 2}) {
            STEPS.add(() -> {
                set("preferences", ((EditorPreferences) field(editor, "preferences")).withScale(scale));
                set("view", 0); set("filesMode", false); set("guideStep", -1); set("showCreate", false); set("resetLayout", true);
            });
            pause(350);
            STEPS.add(() -> {
                capture("72-content-panel-" + (int) scale);
                panelSections.add(new PanelSection("Content###VR_Content", "Content tools", true));
            });
            pause(250);
            STEPS.add(() -> {
                capture("73-content-tools-" + (int) scale);
                panelSections.add(new PanelSection("Content###VR_Content", "Content tools", false));
                panelSections.add(new PanelSection("Work area###VR_Work", "Simulated conditions", true));
                set("view", 1);
            });
            pause(250);
            STEPS.add(() -> { capture("74-preview-panel-" + (int) scale); set("view", 2); });
            pause(250);
            STEPS.add(() -> { capture("75-build-panel-" + (int) scale); set("view", 4); set("filesMode", true); });
            pause(250);
            STEPS.add(() -> capture("76-files-panel-" + (int) scale));
        }
        STEPS.add(() -> {
            set("preferences", ((EditorPreferences) field(editor, "preferences")).withScale(1));
            set("view", 0); set("filesMode", false); set("resetLayout", true);
            panelSections.add(new PanelSection("Work area###VR_Work", "Simulated conditions", false));
        });
        pause(250);
    }

    private static void preparePanelSections() {
        for (var section : panelSections) {
            ImGui.begin(section.panel());
            ImGui.getStateStorage().setBool(ImGui.getID(section.label()), section.open());
            ImGui.end();
        }
        panelSections.clear();
    }

    private static Object graphPreview() {
        var selections = (Map<?, ?>) field(field(editor, "textFields"), "selections");
        Object selection = selections.get("graph:start:/nodes/start/lines:/nodes/start/lines/0");
        require(selection != null, "The visible graph has a text preview.");
        return field(selection, "preview");
    }

    private static void buildPerformanceSteps() {
        var model = new Object[1]; var texture = new Object[1]; var ticks = new long[2];
        STEPS.add(() -> {
            model[0] = field(field(editor, "graphModel"), "snapshot");
            Object preview = graphPreview(); texture[0] = field(preview, "target");
            ticks[0] = (long) field(preview, "renderedTick"); ticks[1] = (long) field(preview, "renderedResources");
            require(!(boolean) field(preview, "animated"), "The idle graph fixture uses static text.");
            var organization = (EditorGraphOrganization) field(editor, "graphOrganization");
            var graph = (EditorGraphModel.Snapshot) model[0];
            organization.focus("Downstream");
            var highlighted = organization.highlighted(graph, java.util.Set.of("start"));
            require(highlighted == organization.highlighted(graph, java.util.Set.of("start")) && highlighted.size() == 3,
                    "Idle graph frames reuse branch reachability without losing connected nodes.");
            require(organization.highlighted(graph, java.util.Set.of("finish")).equals(java.util.Set.of("finish")), "Changing the selection refreshes branch reachability.");
            organization.focus("All nodes");
        });
        pause(1250);
        STEPS.add(() -> {
            Object preview = graphPreview();
            require(model[0] == field(field(editor, "graphModel"), "snapshot"), "Idle editor frames reuse the graph model.");
            require(texture[0] == field(preview, "target") && ticks[0] == (long) field(preview, "renderedTick"), "Static graph text keeps its rasterized texture beyond one second.");
            EditorTextPreview.resourcesReloaded();
        });
        pause(250);
        STEPS.add(() -> {
            require(ticks[1] != (long) field(graphPreview(), "renderedResources"), "Resource changes invalidate static graph text.");
            document.edit("Animate line", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("<wave>Animated</wave>")));
            invoke("refresh", new Class<?>[0]);
        });
        pause(250);
        STEPS.add(() -> {
            Object preview = graphPreview();
            require((boolean) field(preview, "animated"), "Graph text keeps animated effects.");
            ticks[0] = (long) field(preview, "renderedTick");
        });
        pause(250);
        STEPS.add(() -> {
            require(ticks[0] < (long) field(graphPreview(), "renderedTick"), "Animated graph text continues updating.");
            document.undo(); invoke("refresh", new Class<?>[0]);
            capture("77-editor-performance");
        });
        pause(250);
    }

    private static void buildDropdownSteps() {
        var previous = new AuthoringDocument[1];
        STEPS.add(() -> {
            previous[0] = document;
            document = workspace.createDocument(DefinitionKind.QUEST, "test:dropdown_performance");
            document.edit("Empty objectives", root -> root.getAsJsonArray("stages").get(0).getAsJsonObject().add("objectives", new com.google.gson.JsonArray()));
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            featureStage = 8; featureActionPoint = null;
        });
        pause(250);
        click(() -> featureActionPoint);
        STEPS.add(dropdownFrameTimes::clear);
        pause(1000);
        STEPS.add(() -> {
            require(dropdownFrameTimes.size() >= 20, "The objective dropdown renders across multiple frames.");
            var sorted = dropdownFrameTimes.stream().sorted().toList();
            System.out.printf(java.util.Locale.ROOT, "Editor objective dropdown: %.3f ms median render across %d open frames%n", sorted.get(sorted.size() / 2) / 1_000_000.0, sorted.size());
            capture("81-objective-dropdown");
        });
        key(ImGuiKey.Home);
        var root = com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export();
        var labels = new EditorSchema(root).options(root.getAsJsonObject("$defs").getAsJsonObject("objective")).keySet().stream().toList();
        int index = labels.indexOf("break block");
        require(index >= 0, "The objective menu contains the break block alias.");
        for (int i = 0; i < index; i++) key(ImGuiKey.DownArrow);
        key(ImGuiKey.Enter);
        STEPS.add(() -> {
            var objectives = document.value("/stages/0/objectives").getAsJsonArray();
            require(objectives.size() == 1, "Choosing an objective from the dropdown adds one entry.");
            var objective = objectives.get(0).getAsJsonObject();
            require(objective.get("type").getAsString().equals("break_block") && objective.has("id"),
                    "The break block menu entry supplies its required defaults.");
            document.undo(); invoke("refresh", new Class<?>[0]);
            require(document.value("/stages/0/objectives").getAsJsonArray().isEmpty(), "Objective dropdown edits remain undoable.");
            featureStage = -1;
            workspace.remove(document); document = previous[0];
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
        });
        pause(250);
    }

    private static void finishSteps(String details) {
        STEPS.add(() -> {
            try { Files.writeString(output().resolve("result.txt"), "Authoring editor client smoke passed\n" + details + "\n"); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            finished = true;
            Minecraft.getInstance().stop();
        });
    }

    private static void buildQuestAuthoringSteps() {
        var previous = new AuthoringDocument[1];
        STEPS.add(() -> {
            previous[0] = document;
            document = workspace.createDocument(DefinitionKind.QUEST, "test:quest_authoring");
            DialogueGraph.addNode(document, "finish");
            EditorQuestFlow.add(workspace, document, "start", EditorQuestFlow.Route.CHOICE, "stage", "finish", "I'll help the village", "", new com.google.gson.JsonArray());
            EditorQuestSettings.defaults(document, "availability", JsonParser.parseString("{\"repeatable\":true,\"prerequisites\":[]}"));
            EditorQuestSettings.performanceTier(document, "swift", "elapsed", "elapsed_ticks", "", "", "", 1200, true, 25, 2);
            EditorQuestLinks.create(workspace, document, "start", EditorQuestLinks.Kind.CONVERSATION, "briefing");
            EditorQuestLinks.create(workspace, document, "start", EditorQuestLinks.Kind.SCENE, "arrival");
            EditorQuestLinks.create(workspace, document, "start", EditorQuestLinks.Kind.ENCOUNTER, "ambush");
            require(workspace.validate().stream().noneMatch(issue -> issue.severity() == com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR),
                    "Guided quest content validates before opening its forms.");
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("guideStep", -1); set("view", 0); set("focusMode", "Details###VR_Details");
            ((imgui.type.ImString) field(editor, "pointer")).set("/stages/0/transition");
            panelSections.add(new PanelSection("Details###VR_Focused", "Stage progression", true));
        });
        pause(400);
        STEPS.add(() -> {
            capture("quest-01-stage-progression");
            panelSections.add(new PanelSection("Details###VR_Focused", "Stage progression", false));
            panelSections.add(new PanelSection("Details###VR_Focused", "Related quest content", true));
        });
        pause(300);
        STEPS.add(() -> {
            capture("quest-02-linked-content");
            panelSections.add(new PanelSection("Details###VR_Focused", "Related quest content", false));
            panelSections.add(new PanelSection("Details###VR_Focused", "Add quest objective", true));
            set("objectiveType", "follower_arrival");
        });
        pause(300);
        STEPS.add(() -> capture("quest-03-escort-objective"));
        STEPS.add(() -> {
            set("selectedNode", ""); ((imgui.type.ImString) field(editor, "pointer")).set("");
            panelSections.add(new PanelSection("Details###VR_Focused", "Add quest objective", false));
            panelSections.add(new PanelSection("Details###VR_Focused", "Prerequisites, repeatability and turn-in", true));
        });
        pause(300);
        STEPS.add(() -> {
            capture("quest-04-settings");
            panelSections.add(new PanelSection("Details###VR_Focused", "Prerequisites, repeatability and turn-in", false));
            panelSections.add(new PanelSection("Details###VR_Focused", "Performance reward tiers", true));
        });
        pause(300);
        STEPS.add(() -> {
            capture("quest-05-performance-rewards");
            questLiveControls = true;
        });
        pause(300);
        STEPS.add(() -> {
            capture("quest-06-live-test-controls");
            questLiveControls = false;
            set("focusMode", "");
            document = previous[0];
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
        });
    }

    private static void renderQuestLiveControls() {
        if (!questLiveControls) return;
        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX() + 80, viewport.getWorkPosY() + 60);
        ImGui.setNextWindowSize(640, viewport.getWorkSizeY() - 100);
        ImGui.begin("Quest live test controls");
        ((EditorQuestLiveTests) field(editor, "questLiveTests")).render(workspace, document, operation -> {
            try { operation.run(); } catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        ImGui.end();
    }

    private static void buildWorkflowSteps() {
        var controls = new AuthoringDocument[1];
        var quest = new AuthoringDocument[1];
        var conversation = new AuthoringDocument[1];
        var graph = new AuthoringDocument[1];
        var groupOffset = new float[2];
        STEPS.add(() -> {
            set("activeDiagnostic", null); set("view", 0); set("guideStep", -1); set("showWorkspace", false); set("showCreate", false);
            set("preferences", ((EditorPreferences) field(editor, "preferences")).withScale(1));
            GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(), 1920, 1080);
            document = workspace.createDocument(DefinitionKind.TREE, "test:workflow_graph");
            DialogueGraph.addNode(document, "second"); DialogueGraph.addNode(document, "third");
            graph[0] = document;
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("view", 0); set("resetLayout", true); set("fitGraph", true); set("showGraphTools", false);
        });
        pause(400);
        STEPS.add(() -> { Minecraft.getInstance().resizeDisplay(); locateCanvas(); });
        move(() -> new float[] {canvas[0] + 4, canvas[1] + 4}, false);
        move(() -> new float[] {canvas[0] + 4, canvas[1] + 4}, true);
        move(() -> new float[] {canvas[2] - 4, canvas[3] - 4}, true);
        STEPS.add(() -> mouseDown = false);
        STEPS.add(() -> {
            require(((java.util.Set<?>) field(editor, "nodeSelection")).size() == 3, "Box selection selects the nodes inside the graph rectangle.");
            groupOffset[0] = positions().get("second")[0] - positions().get("start")[0];
            groupOffset[1] = positions().get("second")[1] - positions().get("start")[1];
            set("snapGrid", true); capture("44-workflow-box-selection");
        });
        move(() -> point("start", 90, 18), false);
        move(() -> point("start", 90, 18), true);
        STEPS.add(() -> { mouseX += 27; mouseY += 19; });
        STEPS.add(() -> mouseDown = false);
        STEPS.add(() -> {
            var start = positions().get("start"); var second = positions().get("second");
            require(start[0] % 16 == 0 && start[1] % 16 == 0, "Group dragging snaps its anchor to the optional grid.");
            require(Math.abs(second[0] - start[0] - groupOffset[0]) < .01f && Math.abs(second[1] - start[1] - groupOffset[1]) < .01f,
                    "Group snapping preserves node offsets.");
        });
        workflowCommand("Align top");
        STEPS.add(() -> {
            float y = positions().get("start")[1];
            require(positions().values().stream().allMatch(point -> point[1] == y), "The command palette aligns the selected nodes.");
            require(document.undoLabel().equals("Move node"), "The resulting edit has a named undo entry.");
            capture("45-workflow-aligned-selection");
            controls[0] = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/workflow_controls.json",
                    JsonParser.parseString("{\"count\":2,\"values\":[{\"id\":\"first\"},{\"id\":\"second\"},{\"id\":\"third\"}]}").getAsJsonObject());
            workspace.add(controls[0]); document = controls[0];
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("view", 4); workflowLists = true; workflowControls = true;
        });
        pause(250);
        move(() -> workflowHandles[0], false);
        move(() -> workflowHandles[0], true);
        STEPS.add(() -> mouseX += 10);
        move(() -> workflowTargets[2], true);
        STEPS.add(() -> mouseDown = false);
        STEPS.add(() -> {
            require(controls[0].json().getAsJsonArray("values").get(2).getAsJsonObject().get("id").getAsString().equals("first"), "Native drag-and-drop reorders list entries.");
            capture("46-workflow-list-reorder");
            invoke("changeHistory", new Class<?>[] {boolean.class}, false);
            require(controls[0].json().getAsJsonArray("values").get(0).getAsJsonObject().get("id").getAsString().equals("first"), "One undo restores the original list order.");
            workflowLists = false;
        });
        pause(250);
        replaceText(() -> workflowFieldPoint, () -> "\"wrong type\"");
        click(() -> toolbar("Save"));
        STEPS.add(() -> {
            require(controls[0].json().get("count").getAsInt() == 2 && ((EditorFieldEdits) field(editor, "pendingFields")).pending(),
                    "Saving an invalid field preserves the pending buffer and the original value.");
            require(((imgui.type.ImString) field(editor, "pointer")).get().equals("/count"), "Save focuses the first invalid field.");
            capture("47-workflow-inline-error");
        });
        replaceText(() -> workflowFieldPoint, () -> "3");
        click(() -> toolbar("Save"));
        STEPS.add(() -> {
            require(controls[0].json().get("count").getAsInt() == 3, "Correcting the focused field allows saving.");
            workflowControls = false;
            var files = (EditorOpenFiles) field(editor, "openFiles");
            files.restore(workspace, new com.google.gson.JsonArray());
            files.open(workspace, controls[0].resourcePath()); files.open(workspace, graph[0].resourcePath());
        });
        workflowCommand("Pin or unpin current file");
        STEPS.add(() -> require(((EditorOpenFiles) field(editor, "openFiles")).entries(workspace).stream()
                .anyMatch(entry -> entry.currentPath().equals(controls[0].resourcePath()) && entry.pinned()), "The palette can pin the current file."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Tab);
        STEPS.add(() -> require(field(editor, "document") == graph[0], "Ctrl+Tab opens the next file."));
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, true));
        chord(ImGuiKey.ImGuiMod_Shift, ImGuiKey.Tab);
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, false));
        STEPS.add(() -> {
            require(field(editor, "document") == controls[0], "Ctrl+Shift+Tab switches back.");
            quest[0] = workspace.createDocument(DefinitionKind.QUEST, "test:workflow_quest");
            conversation[0] = workspace.createDocument(DefinitionKind.CONVERSATION, "test:workflow_conversation");
            conversation[0].edit("Assign quest", root -> root.addProperty("quest", quest[0].id()));
            document = conversation[0]; invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            ((imgui.type.ImString) field(editor, "pointer")).set("/quest"); set("view", 0);
        });
        workflowCommand("Go to definition");
        STEPS.add(() -> require(field(editor, "document") == quest[0], "Go to definition follows a quest reference across files."));
        workflowCommand("Find usages");
        STEPS.add(() -> {
            var references = (java.util.List<?>) field(editor, "referenceResults");
            require(references.contains(new EditorReferences.Location(conversation[0], "/quest")), "Find usages lists references from other files.");
            capture("48-workflow-references");
        });
        key(ImGuiKey.Escape);
        STEPS.add(() -> {
            document = graph[0]; invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            ((imgui.type.ImString) field(editor, "pointer")).set("/nodes/start/lines");
            set("selectedNode", "start"); set("panX", 135f); set("panY", 95f); set("zoom", .85f); set("fitGraph", false);
            ((imgui.type.ImString) field(editor, "workspacePath")).set(workspace.directory().toString());
            invoke("saveSession", new Class<?>[0]);
            invoke("openWorkspace", new Class<?>[] {boolean.class}, false);
            workspace = (AuthoringWorkspace) field(editor, "workspace"); document = (AuthoringDocument) field(editor, "document");
            require(document != graph[0] && document.resourcePath().equals(graph[0].resourcePath())
                    && ((imgui.type.ImString) field(editor, "pointer")).get().equals("/nodes/start/lines")
                    && (float) field(editor, "panX") == 135f && (float) field(editor, "zoom") == .85f,
                    "Reloading a workspace restores its saved editing context against newly loaded documents.");
            require(((EditorOpenFiles) field(editor, "openFiles")).entries(workspace).stream().anyMatch(EditorOpenFiles.Entry::pinned),
                    "Pinned files survive workspace reload.");
        });
        pause(250);
        STEPS.add(() -> capture("49-workflow-restored-session"));
    }

    private static void buildFeatureSteps() {
        STEPS.add(() -> {
            try {
                EditorQualityChecks.questSimulationRecordsCoverage();
                EditorProductivityChecks.questScenarioTestsSimulateObjectivesAndOutcomes();
                workspace = AuthoringWorkspace.create(output().resolve("feature-drafts"));
                document = workspace.createDocument(DefinitionKind.TREE, "test:feature_graph");
                DialogueGraph.addNode(document, "finish");
                workspace.addDialogueResponse(document, "start", "continue", "Continue", "finish");
                workspace.save();
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            set("workspace", workspace); set("view", 0); set("filesMode", false);
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            document.edit("Different wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("finish").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("Different")));
            invoke("refresh", new Class<?>[0]);
            featureStage = 0;
        });
        pause(250);
        STEPS.add(() -> {
            var bulk = (EditorBulkEdit) field(editor, "bulkEdit");
            setField(bulk, "field", "/lines/0");
            ((imgui.type.ImString) field(bulk, "value")).set("Shared greeting");
            setField(bulk, "preview", EditorBulkEdit.preview(document, java.util.Set.of("start", "finish"), "/lines/0", "Shared greeting"));
        });
        pause(250);
        STEPS.add(() -> capture("50-bulk-property-preview"));
        click(() -> featureActionPoint);
        STEPS.add(() -> {
            require(document.json().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").get(0).getAsString().equals("Shared greeting"), "Native bulk Apply button commits the preview.");
            require(document.json().getAsJsonObject("nodes").getAsJsonObject("finish").getAsJsonArray("lines").get(0).getAsString().equals("Shared greeting"), "Native bulk Apply changes every selected node.");
            var library = (EditorLibrary) field(editor, "library");
            try { library.saveSnippet("Greeting branch", EditorSnippets.nodes(workspace, document, java.util.Set.of("start"))); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            setField(field(editor, "snippets"), "selected", "Greeting branch"); featureStage = 1;
        });
        pause(250);
        STEPS.add(() -> capture("51-reusable-snippets"));
        STEPS.add(() -> {
            var library = (EditorLibrary) field(editor, "library");
            var inserted = EditorSnippets.insert(workspace, document, "", library.snippet("Greeting branch"));
            require(inserted.size() == 2, "Saved snippet pastes its connected branch in the native editor.");
            invoke("selectInsertedNodes", new Class<?>[] {List.class}, inserted);
            featureStage = 2;
        });
        pause(250);
        click(() -> new float[] {570, 198});
        STEPS.add(() -> capture("52-saved-change-review"));
        STEPS.add(() -> {
            document.edit("Broken entry", root -> root.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("start", "missing_destination"));
            invoke("refresh", new Class<?>[0]); featureStage = 3;
        });
        pause(250);
        STEPS.add(() -> capture("53-validation-quick-fix"));
        click(() -> featureActionPoint);
        STEPS.add(() -> {
            require(DialogueGraph.nodes(document).stream().anyMatch(node -> node.id().equals("missing_destination")), "Native quick-fix button creates the missing destination.");
            var session = new com.jvn.villagerretaliation.authoring.preview.DialoguePreviewSession(document.json(), net.minecraft.resources.ResourceLocation.parse(document.id()), new com.jvn.villagerretaliation.authoring.preview.PreviewConditions(), key -> null);
            var observe = ((EditorCoverage) field(editor, "coverage")).observe(document);
            session.start("start"); observe.accept(session.drainCoverage()); session.choose(0); observe.accept(session.drainCoverage()); session.close();
            featureStage = 4;
        });
        pause(250);
        STEPS.add(() -> {
            require(((EditorCoverage) field(editor, "coverage")).visited(document, "/nodes/finish"), "Native graph coverage contains the executed branch.");
            capture("54-playtest-coverage");
            var library = (EditorLibrary) field(editor, "library");
            try { library.resource("Choose item...", "minecraft:emerald", true); library.resource("Choose item...", "minecraft:diamond", false); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            qualityResource = "minecraft:stone"; featureStage = 5;
        });
        pause(250);
        click(() -> featureActionPoint);
        STEPS.add(() -> capture("55-favorite-resource-picker"));
        key(ImGuiKey.Enter);
        STEPS.add(() -> {
            require(qualityResource.equals("minecraft:emerald"), "Native picker keyboard selection chooses the favorite first.");
            featureStage = -1; set("fitGraph", true);
        });
        pause(250);
        STEPS.add(() -> capture("56-coverage-graph"));
    }

    private static void focusWorkShortcut() {
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, true));
        chord(ImGuiKey.ImGuiMod_Shift, ImGuiKey.G);
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, false));
    }

    private static void buildProductivitySteps() {
        var drop = new float[2];
        STEPS.add(() -> {
            document = workspace.createDocument(DefinitionKind.TREE, "test:productivity_graph");
            DialogueGraph.addNode(document, "finish");
            workspace.addDialogueResponse(document, "start", "continue", "Continue", "finish");
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("view", 0); set("focusMode", ""); set("fitGraph", true); set("showGraphTools", false);
            EditorGraphOrganization.saveGroup(document, "", "Greeting branch", "Ask about the village", java.util.Set.of("start", "finish"));
        });
        pause(250);
        focusWorkShortcut();
        STEPS.add(() -> {
            require(!field(editor, "focusMode").equals(""), "The default focus shortcut focuses the work area.");
            capture("57-focused-graph");
        });
        focusWorkShortcut();
        STEPS.add(() -> {
            require(field(editor, "focusMode").equals(""), "The default focus shortcut restores the dock layout.");
            try { ((EditorLibrary) field(editor, "library")).shortcut("Focus work area", "Ctrl+G"); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.G);
        STEPS.add(() -> require(!field(editor, "focusMode").equals(""), "A remapped keyboard shortcut runs the configured action."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.G);
        STEPS.add(() -> {
            require(field(editor, "focusMode").equals(""), "The remapped shortcut toggles focus back.");
            require(DialogueGraph.nodes(document).size() == 2, "Focus shortcuts do not activate unrelated controls.");
            try { ((EditorLibrary) field(editor, "library")).shortcut("Focus work area", "Ctrl+Shift+G"); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        pause(250);
        STEPS.add(AuthoringEditorClientSmoke::locateCanvas);
        move(() -> new float[] {canvas[0] + 60, canvas[3] - 80}, false);
        STEPS.add(() -> rightDown = true); STEPS.add(() -> rightDown = false);
        STEPS.add(() -> {
            require(field(editor, "contextNode").equals("") && field(editor, "contextOutput").equals(""), "Right-clicking empty canvas opens canvas actions.");
            capture("58-graph-context-menu");
        });
        key(ImGuiKey.Escape);
        pause(150);
        STEPS.add(() -> { drop[0] = canvas[0] + 140; drop[1] = canvas[3] - 180; });
        move(() -> port("start"), false); move(() -> port("start"), true);
        move(() -> drop, true); move(() -> drop, false);
        STEPS.add(() -> {
            require(!field(editor, "contextOutput").equals(""), "Dropping a wire on empty canvas opens connected-node actions.");
            capture("59-wire-drop-create");
        });
        click(() -> new float[] {drop[0] + 35, drop[1] + 28});
        STEPS.add(() -> require(DialogueGraph.nodes(document).size() == 3
                && DialogueGraph.edges(document).stream().anyMatch(edge -> !edge.from().equals("start") && edge.to().equals("finish")), "The native insert action preserves the existing downstream destination."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Z);
        STEPS.add(() -> {
            require(DialogueGraph.nodes(document).size() == 2, "Inserted graph nodes undo with their position.");
            document.editMetadata("Save scenario", metadata -> {
                var scenarios = new com.google.gson.JsonObject();
                scenarios.add("Greeting", new com.jvn.villagerretaliation.authoring.preview.PreviewConditions().snapshot());
                metadata.add("preview_scenarios", scenarios);
                var tests = new com.google.gson.JsonObject();
                tests.add("Greeting", EditorScenarioTests.config("start", "Node or stage", "finish", List.of(new EditorScenarioTests.Step("choice", "continue"))));
                metadata.add("preview_scenario_tests", tests);
            });
            featureStage = 6;
        });
        pause(250);
        STEPS.add(() -> capture("60-scenario-test-steps"));
        click(() -> featureActionPoint);
        STEPS.add(() -> {
            var results = (List<?>) field(field(editor, "scenarioTests"), "results");
            require(results.size() == 1 && ((EditorScenarioTests.Result) results.getFirst()).passed(), "The native Run all button evaluates saved steps and expectations.");
            capture("61-scenario-test-results");
            featureStage = 7;
        });
        pause(250);
        STEPS.add(() -> { capture("62-shortcut-settings"); featureStage = -1; set("zoom", .35f); set("fitGraph", false); });
        pause(250);
        STEPS.add(() -> capture("63-comment-groups-minimum-zoom"));
        STEPS.add(() -> { set("fitGraph", true); });
        pause(250);
    }

    private static void renderFeatureControls() {
        if (featureStage < 0) return;
        ImGui.setNextWindowPos(460, 100); ImGui.setNextWindowSize(850, 740);
        ImGui.begin("Editor quality acceptance"); ImGui.setNextItemOpen(true);
        java.util.function.Consumer<ImGuiAuthoringEditor.EditorOperation> run = action -> invoke("operation", new Class<?>[] {ImGuiAuthoringEditor.EditorOperation.class}, action);
        if (featureStage == 0) {
            var bulk = (EditorBulkEdit) field(editor, "bulkEdit");
            bulk.render(workspace, document, java.util.Set.of("start", "finish"), run, () -> invoke("refresh", new Class<?>[0]));
            if (field(bulk, "preview") != null) featureActionPoint = new float[] {(ImGui.getItemRectMinX() + ImGui.getItemRectMaxX()) / 2, (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) / 2};
        } else if (featureStage == 1) {
            ((EditorSnippets) field(editor, "snippets")).render((EditorLibrary) field(editor, "library"), workspace, document, java.util.Set.of("start"), "", run,
                    added -> invoke("selectInsertedNodes", new Class<?>[] {List.class}, added));
        } else if (featureStage == 2) {
            try { ((EditorChanges) field(editor, "changes")).render(workspace, run, path -> invoke("openPackFile", new Class<?>[] {String.class}, path), changed -> invoke("refresh", new Class<?>[0])); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        } else if (featureStage == 3) {
            ImGui.textUnformatted("Missing entry destination");
            var issue = workspace.validate().stream().filter(value -> value.message().equals("Missing entry node")).findFirst().orElse(null);
            if (issue != null) {
                EditorQuickFixes.render(workspace, document, issue, run, () -> invoke("refresh", new Class<?>[0]));
                featureActionPoint = new float[] {(ImGui.getItemRectMinX() + ImGui.getItemRectMaxX()) / 2, (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) / 2};
            }
        } else if (featureStage == 6) {
            ((EditorScenarioTests) field(editor, "scenarioTests")).render(document, run, (key, simulated) -> null,
                    ((EditorCoverage) field(editor, "coverage")).observe(document), id -> { });
            if (((List<?>) field(field(editor, "scenarioTests"), "results")).isEmpty())
                featureActionPoint = new float[] {(ImGui.getItemRectMinX() + ImGui.getItemRectMaxX()) / 2, (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) / 2};
        } else if (featureStage == 7) ((EditorShortcuts) field(editor, "shortcuts")).render(((List<?>) invoke("commands", new Class<?>[0])).stream().map(EditorCommands.Command.class::cast).toList(), (EditorLibrary) field(editor, "library"), run);
        else if (featureStage == 8) {
            long start = System.nanoTime();
            invoke("inspectValue", new Class<?>[] {com.google.gson.JsonElement.class, String.class, String.class, int.class},
                    document.value("/stages/0/objectives"), "/stages/0/objectives", "Objectives", 0);
            dropdownFrameTimes.add(System.nanoTime() - start);
            if (featureActionPoint == null) featureActionPoint = new float[] {(ImGui.getItemRectMinX() + ImGui.getItemRectMaxX()) / 2,
                    (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) / 2};
        }
        else if (featureStage == 4) ((EditorCoverage) field(editor, "coverage")).render(document, id -> { });
        else {
            featureActionPoint = new float[] {ImGui.getCursorScreenPosX() + 150, ImGui.getCursorScreenPosY() + ImGui.getFrameHeight() / 2};
            ((EditorResourcePicker) field(editor, "resourcePicker")).render("feature_resource", "Choose item...", qualityResource,
                    List.of("minecraft:stone", "minecraft:diamond", "minecraft:emerald"), value -> qualityResource = value, (EditorLibrary) field(editor, "library"), run);
        }
        ImGui.end();
    }

    private static void workflowCommand(String query) {
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, true));
        chord(ImGuiKey.ImGuiMod_Shift, ImGuiKey.P);
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, false));
        type(query);
        STEPS.add(() -> capture("workflow-command-palette"));
        key(ImGuiKey.Enter);
        pause(100);
    }

    private static void renderWorkflowControls() {
        if (deletionTyping) {
            ImGui.setNextWindowPos(500, 200); ImGui.setNextWindowSize(420, 140);
            ImGui.begin("Typing during graph selection");
            if (deletionFocus) { deletionFocus = false; ImGui.setKeyboardFocusHere(); }
            ImGui.inputText("Text", deletionText);
            ImGui.end();
        }
        if (!workflowControls) return;
        ImGui.setNextWindowPos(450, 150); ImGui.setNextWindowSize(800, 600);
        ImGui.begin("Editor workflow acceptance");
        if (workflowLists) {
            var values = ((AuthoringDocument) field(editor, "document")).json().getAsJsonArray("values");
            for (int i = 0; i < values.size(); i++) {
                ImGui.pushID(i); ImGui.beginGroup();
                float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
                ImGui.textUnformatted(values.get(i).getAsJsonObject().get("id").getAsString());
                invoke("listReorderHandle", new Class<?>[] {String.class, int.class, com.google.gson.JsonArray.class}, "/values", i, values);
                if (!mouseDown) workflowHandles[i] = new float[] {(ImGui.getItemRectMinX() + ImGui.getItemRectMaxX()) / 2, (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) / 2};
                workflowTargets[i] = new float[] {x + 15, y + ImGui.getFrameHeight() / 2};
                ImGui.endGroup();
                invoke("listDropTarget", new Class<?>[] {String.class, int.class}, "/values", i);
                ImGui.dummy(0, 24); ImGui.popID();
            }
        } else {
            workflowFieldPoint = new float[] {ImGui.getCursorScreenPosX() + 100, ImGui.getCursorScreenPosY() + ImGui.getTextLineHeightWithSpacing() + ImGui.getFrameHeight() / 2};
            var value = ((AuthoringDocument) field(editor, "document")).json().get("count");
            invoke("inspectValue", new Class<?>[] {com.google.gson.JsonElement.class, String.class, String.class, int.class}, value, "/count", "Count", 0);
        }
        ImGui.end();
    }

    private static void buildQualityOfLifeSteps() {
        var remembered = new AuthoringDocument[1];
        STEPS.add(() -> {
            set("view", 0); set("activeDiagnostic", null); set("filesMode", false); set("showWorkspace", false); set("showCreate", false);
            set("preferences", ((EditorPreferences) field(editor, "preferences")).withScale(1));
            document = workspace.createDocument(DefinitionKind.TREE, "test:qol_smoke");
            document.edit("Set searchable wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines")
                    .set(0, new com.google.gson.JsonPrimitive("Meet at the village gate, then return to the gate.")));
            remembered[0] = document;
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("selectedNode", "start");
            ((imgui.type.ImString) field(editor, "pointer")).set("/nodes/start/lines");
            set("panX", 130f); set("panY", 90f); set("zoom", .8f); set("fitGraph", false); set("graphFocus", "");
        });
        pause(250);
        STEPS.add(() -> invoke("openPackFile", new Class<?>[] {String.class}, "pack.mcmeta"));
        STEPS.add(() -> invoke("openPackFile", new Class<?>[] {String.class}, remembered[0].resourcePath()));
        STEPS.add(() -> require(field(editor, "document") == remembered[0]
                && ((imgui.type.ImString) field(editor, "pointer")).get().equals("/nodes/start/lines")
                && (float) field(editor, "panX") == 130f && (float) field(editor, "zoom") == .8f,
                "Ordinary file opening restores the selected field and graph position."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.P);
        type("pack.mcmeta");
        STEPS.add(() -> {
            require(((imgui.type.ImString) field(editor, "quickQuery")).get().equals("pack.mcmeta"), "Quick open focuses its searchable input.");
            capture("39-qol-quick-open");
        });
        key(ImGuiKey.Enter);
        STEPS.add(() -> require((int) field(editor, "view") == 4 && field(field(editor, "packFiles"), "selected").equals("pack.mcmeta"), "Enter opens a supporting file from quick open."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.P);
        type("test:qol_smoke");
        key(ImGuiKey.Enter);
        STEPS.add(() -> {
            require(field(editor, "document") == remembered[0], "Quick open resolves resource IDs back to their files.");
            ((imgui.type.ImString) field(editor, "filter")).set("village gate");
        });
        pause(250);
        STEPS.add(() -> {
            var hits = (java.util.List<?>) field(editor, "searchHits");
            require(!hits.isEmpty(), "Contextual content search finds the matching wording.");
            capture("40-qol-contextual-search");
            var hit = hits.stream().map(EditorSearch.Hit.class::cast).filter(value -> value.document() == remembered[0]).findFirst().orElseThrow();
            invoke("openSearchHit", new Class<?>[] {EditorSearch.Hit.class}, hit);
            require(field(editor, "selectedNode").equals("start"), "Opening a search result selects its owning graph node.");
            ((imgui.type.ImString) field(editor, "filter")).set("");
            var preview = EditorWordingReplacement.preview(remembered[0], "gate", "square");
            require(preview.occurrences() == 2, "Replacement preview counts every wording match.");
            preview.apply(); invoke("refresh", new Class<?>[0]);
            invoke("changeHistory", new Class<?>[] {boolean.class}, false);
            require(remembered[0].json().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").get(0).getAsString().contains("gate"), "Wording replacements undo together in the editor.");
            ((imgui.type.ImString) field(editor, "replaceFind")).set("gate");
            ((imgui.type.ImString) field(editor, "replaceText")).set("square");
            set("replacementPreview", EditorWordingReplacement.preview(remembered[0], "gate", "square"));
            qualityControls = true;
        });
        pause(250);
        STEPS.add(() -> capture("41-qol-replacement-preview"));
        click(() -> qualityPickerPoint);
        type("DIAMOND");
        STEPS.add(() -> capture("42-qol-filtered-resource-picker"));
        key(ImGuiKey.Enter);
        STEPS.add(() -> {
            require(qualityResource.equals("minecraft:diamond"), "Typing in a resource picker filters identifiers and Enter selects the match.");
            qualityControls = false;
            set("replacementPreview", null);
            var issue = new com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic(
                    com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR, remembered[0].resourcePath(), "/nodes/start/missing", "Missing field acceptance check");
            set("diagnostics", java.util.List.of(issue));
            invoke("openDiagnostic", new Class<?>[] {com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.class}, issue);
            require(field(editor, "selectedNode").equals("start") && ((imgui.type.ImString) field(editor, "pointer")).get().equals("/nodes/start"), "Issue navigation opens the nearest editable owner of a missing field.");
        });
        pause(250);
        STEPS.add(() -> capture("43-qol-issue-navigation"));
    }

    private static void buildCampaignOverviewSteps() {
        STEPS.add(() -> {
            set("guideStep", -1); set("resetLayout", true);
            var quest = workspace.createDocument(DefinitionKind.QUEST, "test:overview");
            EditorQuestLinks.create(workspace, quest, DialogueGraph.nodes(quest).getFirst().id(), EditorQuestLinks.Kind.ENCOUNTER, "skirmish");
            invoke("select", new Class<?>[] {AuthoringDocument.class}, quest);
            set("view", 6);
        });
        pause(300);
        STEPS.add(() -> {
            var graph = (EditorCampaignGraph.Snapshot) field(field(editor, "campaignOverview"), "graph");
            require(graph.links().stream().anyMatch(link -> link.from().document().kind() == DefinitionKind.QUEST && link.to().category().equals("Scenes")),
                    "The overview connects a quest to its scene.");
            require(graph.links().stream().anyMatch(link -> link.from().category().equals("Scenes") && link.to().category().equals("Encounters")),
                    "The overview connects a scene to its encounter.");
            capture("campaign-overview");
        });
        click(() -> campaignNodePoint("Scenes"));
        STEPS.add(() -> {
            var selected = (EditorCampaignGraph.Node) field(field(editor, "campaignOverview"), "selected");
            require(selected.category().equals("Scenes"), "Clicking a map node selects its reference inspector.");
            capture("campaign-scene-links");
        });
        move(() -> campaignNodePoint("Encounters"), false);
        STEPS.add(() -> { mouseDown = true; mouseDoubleClicked = true; });
        STEPS.add(() -> { mouseDown = false; mouseDoubleClicked = false; });
        STEPS.add(() -> {
            require((int) field(editor, "view") == 0 && ((AuthoringDocument) field(editor, "document")).resourcePath().contains("/encounters/"),
                    "Double-clicking a map node opens its owning file.");
            set("view", 6);
        });
        move(() -> campaignNodePoint("Scenes"), false);
        STEPS.add(() -> mouseWheel = 2);
        STEPS.add(() -> {
            require((float) field(field(editor, "campaignOverview"), "zoom") > 1, "The campaign map zooms around the pointer.");
            capture("campaign-zoom");
        });
        click(() -> new float[] {620, 261});
        STEPS.add(() -> {
            var view = field(editor, "campaignOverview");
            require((boolean) field(view, "orphaned") && ((List<?>) field(view, "visible")).size() == 2,
                    "The native incoming-reference filter leaves only the two root files.");
            capture("campaign-orphans");
        });
        click(() -> new float[] {620, 261});
        click(() -> new float[] {850, 261});
        STEPS.add(() -> {
            var view = field(editor, "campaignOverview");
            require((boolean) field(view, "untested") && ((List<?>) field(view, "visible")).size() == 2,
                    "The native coverage filter includes untested dialogue and quests.");
            var quest = workspace.documents().stream().filter(value -> value.id().equals("test:overview")).findFirst().orElseThrow();
            ((EditorCoverage) field(editor, "coverage")).observe(quest).accept(
                    new com.jvn.villagerretaliation.authoring.preview.PreviewCoverage(java.util.Set.of("/stages/0"), java.util.Set.of()));
        });
        STEPS.add(() -> require(((List<?>) field(field(editor, "campaignOverview"), "visible")).size() == 1,
                "Recording coverage updates the visible map immediately."));
        click(() -> new float[] {850, 261});
        click(() -> new float[] {600, 220});
        type("skirmish");
        key(ImGuiKey.Enter);
        STEPS.add(() -> {
            var view = field(editor, "campaignOverview");
            require(((List<?>) field(view, "visible")).size() == 2, "Native search finds both the scene and encounter.");
            capture("campaign-search");
        });
        click(() -> new float[] {426, 301});
        STEPS.add(() -> require((boolean) field(field(editor, "campaignOverview"), "nearby"),
                "The connections control focuses the selected file and its neighbours."));
        click(() -> new float[] {780, 301});
        click(() -> new float[] {426, 301});
        click(() -> campaignNodePoint("Scenes"));
        click(() -> new float[] {1620, 410});
        STEPS.add(() -> {
            require((int) field(editor, "view") == 0 && ((AuthoringDocument) field(editor, "document")).id().equals("test:overview")
                            && ((imgui.type.ImString) field(editor, "pointer")).get().equals("/stages/0/on_enter/0/scene"),
                    "The reference inspector jumps to the exact quest action field.");
            set("view", 6);
            set("preferences", ((EditorPreferences) field(editor, "preferences")).withScale(1.5f));
        });
        pause(200);
        click(() -> campaignNodePoint("Scenes"));
        STEPS.add(() -> {
            require(((EditorCampaignGraph.Node) field(field(editor, "campaignOverview"), "selected")).category().equals("Scenes"),
                    "Map hit testing follows the editor text scale.");
            capture("campaign-large-text");
            set("preferences", ((EditorPreferences) field(editor, "preferences")).withScale(1));
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document); set("view", 0);
        });
    }

    private static float[] campaignNodePoint(String category) {
        var view = field(editor, "campaignOverview");
        var graph = (EditorCampaignGraph.Snapshot) field(view, "graph");
        var node = graph.nodes().stream().filter(value -> value.category().equals(category)).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var positions = (Map<EditorCampaignGraph.Node, EditorCampaignGraph.Position>) field(view, "positions");
        var position = positions.get(node);
        float zoom = (float) field(view, "zoom") * ((EditorPreferences) field(editor, "preferences")).textScale();
        return new float[] {(float) field(view, "canvasX") + (float) field(view, "panX") + (position.x() + 130) * zoom,
                (float) field(view, "canvasY") + (float) field(view, "panY") + (position.y() + 40) * zoom};
    }

    private static void buildLocalizationSteps() {
        STEPS.add(() -> {
            set("guideStep", -1); set("resetLayout", true);
            var quest = workspace.createDocument(DefinitionKind.QUEST, "test:localization");
            quest.edit("Localized title", root -> {
                var reference = new com.google.gson.JsonObject(); reference.addProperty("key", "test.greeting");
                if (!root.has("display")) root.add("display", new com.google.gson.JsonObject());
                root.getAsJsonObject("display").add("title", reference);
            });
            workspace.documents().stream().filter(value -> value.resourcePath().equals("data/test/quests/authored/localization/locales/en_us.json")).findFirst().orElseThrow().replaceJson("""
                    {"schema":"villagerretaliation:quest_locale/v1","messages":{"test.greeting":"Hello, {player}!","test.lines":["First line","Second {count}"]}}
                    """);
            invoke("select", new Class<?>[] {AuthoringDocument.class}, quest);
            set("view", 5);
        });
        pause(300);
        STEPS.add(() -> {
            var view = field(editor, "localization");
            require(((List<?>) field(view, "rows")).size() == 2, "The translation table lists localized messages.");
            require(!((List<?>) field(view, "usages")).isEmpty(), "Selected translation links to its owning content.");
            capture("localization-table");
        });
        click(() -> new float[] {1618, 402});
        pause(200);
        STEPS.add(() -> {
            var view = field(editor, "localization");
            require(((AuthoringDocument) field(editor, "document")).resourcePath().endsWith("/fr_fr.json"), "Adding a translation selects its own file for saving and undo.");
            ((EditorTextField) field(view, "textFields")).focus("data/test/quests/authored/localization/locales/fr_fr.json/messages/test.greeting");
        });
        type("Bonjour, {player}!");
        pause(200);
        STEPS.add(() -> {
            var target = (AuthoringDocument) field(editor, "document");
            require(((EditorDocumentBuffers) field(editor, "documentBuffers")).fields(target).text("/messages/test.greeting", "").equals("Bonjour, {player}!"),
                    "Native translation text is retained in the per-file draft.");
            capture("localization-editing");
        });
        click(() -> new float[] {1650, 654});
        pause(150);
        STEPS.add(() -> require(localizationGreeting().state().equals("Reviewed"), "The native review button applies wording and source tracking."));
        click(() -> toolbar("Undo"));
        STEPS.add(() -> require(localizationGreeting().state().equals("Missing"), "Toolbar undo reverses the selected translation."));
        click(() -> toolbar("Undo"));
        pause(150);
        STEPS.add(() -> require(localizationGreeting().target() == null, "Undo removes a newly added locale."));
        click(() -> toolbar("Redo"));
        click(() -> toolbar("Redo"));
        pause(150);
        STEPS.add(() -> {
            require(localizationGreeting().state().equals("Reviewed"), "Redo remains available after the locale was removed.");
            localizationGreeting().source().edit("Change greeting", root -> root.getAsJsonObject("messages").addProperty("test.greeting", "Welcome, {player}!"));
        });
        pause(150);
        click(() -> new float[] {1400, 301});
        key(ImGuiKey.Home); key(ImGuiKey.DownArrow); key(ImGuiKey.DownArrow); key(ImGuiKey.DownArrow); key(ImGuiKey.Enter);
        STEPS.add(() -> {
            require(localizationGreeting().state().equals("Source changed"), "A new source version makes the reviewed translation stale.");
            require(field(field(editor, "localization"), "filter").equals("Source changed"), "The native filter selects changed source messages.");
            capture("localization-stale");
        });
        click(() -> new float[] {560, 369});
        STEPS.add(() -> ((imgui.type.ImString) field(field(editor, "localization"), "csvPath")).set(output().resolve("translation-roundtrip.csv").toString()));
        click(() -> new float[] {530, 449});
        STEPS.add(() -> {
            try {
                Path csv = output().resolve("translation-roundtrip.csv");
                require(Files.isRegularFile(csv), "The export button writes a CSV.");
                var table = new java.util.ArrayList<>(EditorCsv.read(Files.readString(csv)).stream().map(java.util.ArrayList::new).toList());
                require(table.size() == 2, "Export respects the changed-source filter.");
                table.get(1).set(4, "Salut, {player}!");
                Files.writeString(csv, EditorCsv.write(new java.util.ArrayList<>(table)));
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        click(() -> new float[] {740, 449});
        pause(150);
        STEPS.add(() -> {
            require(field(field(editor, "localization"), "preview") != null, "The import button builds a reviewable preview.");
            capture("localization-csv-preview");
        });
        click(() -> new float[] {490, 647});
        pause(150);
        STEPS.add(() -> {
            require(localizationGreeting().translated().getAsString().equals("Salut, {player}!"), "The import button applies the reviewed CSV.");
            require(localizationGreeting().state().equals("Reviewed"), "Imported wording records the current source version.");
        });
        click(() -> toolbar("Undo"));
        STEPS.add(() -> require(localizationGreeting().state().equals("Source changed"), "CSV import undo restores earlier wording and review metadata."));
        click(() -> toolbar("Redo"));
        click(() -> toolbar("Save"));
        STEPS.add(() -> require(!localizationGreeting().target().dirty(), "Save writes the translated file."));
        click(() -> new float[] {1680, 297});
        click(() -> new float[] {1670, 329});
        STEPS.add(() -> {
            require(((AuthoringDocument) field(editor, "document")).kind() == DefinitionKind.QUEST && (int) field(editor, "view") == 0,
                    "The owner link returns to the content using this translation.");
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("view", 0);
        });
    }

    private static EditorLocalization.Row localizationGreeting() {
        return EditorLocalization.rows(workspace, "en_us", "fr_fr").stream().filter(row -> row.key().equals("test.greeting")).findFirst().orElseThrow();
    }

    private static void buildResourcePickerSteps() {
        STEPS.add(() -> {
            set("guideStep", -1); set("resetLayout", true);
            resourceControls = true; resourceKind = "item"; resourceValue = "minecraft:stone";
            originalHeldItem = Minecraft.getInstance().player.getMainHandItem().copy();
        });
        pause(250);
        click(() -> resourcePickerPoint);
        type("DIAMOND SWORD");
        pause(250);
        STEPS.add(() -> {
            var picker = field(editor, "resourcePicker");
            require(!((java.util.Map<?, ?>) field(field(picker, "preview"), "icons")).isEmpty(), "Visible item rows render game icons.");
            capture("resources-item-icons");
        });
        key(ImGuiKey.Enter);
        pause(150);
        STEPS.add(() -> require(resourceValue.equals("minecraft:diamond_sword"), "Display-name search selects the canonical resource ID."));
        STEPS.add(() -> Minecraft.getInstance().player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                net.minecraft.world.item.Items.EMERALD.getDefaultInstance()));
        click(() -> resourceShortcutPoint);
        STEPS.add(() -> require(resourceValue.equals("minecraft:emerald"), "Held-item shortcut reads the current item."));
        click(() -> resourcePickerPoint);
        pause(200);
        click(() -> new float[] {resourcePickerPoint[0], resourcePickerPoint[1] + 2 * ImGui.getFrameHeight()
                + ImGui.getStyle().getItemSpacingY() + ImGui.getStyle().getWindowPaddingY()});
        key(ImGuiKey.End);
        key(ImGuiKey.Enter);
        pause(150);
        STEPS.add(() -> {
            require(!field(field(editor, "resourcePicker"), "namespace").equals(""), "Native mod filter selects a namespace.");
            capture("resources-mod-filter");
        });
        click(() -> new float[] {1000, 800});
        STEPS.add(() -> { resourceKind = "entity"; resourceValue = "minecraft:villager"; });
        pause(150);
        click(() -> resourcePickerPoint);
        pause(350);
        STEPS.add(() -> {
            var preview = field(field(editor, "resourcePicker"), "preview");
            var entity = (net.minecraft.world.entity.Entity) field(preview, "entity");
            require(entity != null && field(preview, "error").equals(""), "The selected entity has a rendered preview.");
            require(Minecraft.getInstance().level.getEntity(entity.getId()) != entity, "Entity preview never spawns an entity in the world.");
            capture("resources-entity-preview");
        });
        click(() -> new float[] {1000, 800});
        STEPS.add(() -> {
            resourceValue = "minecraft:pig";
            resourceTarget = java.util.stream.StreamSupport.stream(Minecraft.getInstance().level.entitiesForRendering().spliterator(), false)
                    .filter(net.minecraft.world.entity.npc.Villager.class::isInstance).findFirst().orElseThrow();
        });
        pause(100);
        click(() -> resourceShortcutPoint);
        STEPS.add(() -> {
            require(resourceValue.equals("minecraft:villager"), "Target-entity shortcut preserves the entity type.");
            resourceKind = "block"; resourceValue = "minecraft:diamond_block";
        });
        pause(150);
        click(() -> resourceShortcutPoint);
        STEPS.add(() -> {
            var minecraft = Minecraft.getInstance();
            String expected = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                    minecraft.level.getBlockState(minecraft.player.blockPosition().below()).getBlock()).toString();
            require(resourceValue.equals(expected), "Target-block shortcut reads the actual block: " + resourceValue + " expected " + expected);
        });
        click(() -> resourcePickerPoint);
        type("Oak Planks");
        key(ImGuiKey.Enter);
        STEPS.add(() -> {
            require(resourceValue.equals("minecraft:oak_planks"), "Block display names select block registry IDs.");
            resourceKind = "sound"; resourceValue = "minecraft:music.game";
        });
        pause(150);
        click(() -> resourceListenPoint);
        pause(150);
        STEPS.add(() -> require(field(field(editor, "resourcePicker"), "sound") != null, "Listen starts a dedicated sound preview."));
        click(() -> resourceShortcutPoint);
        STEPS.add(() -> require(field(field(editor, "resourcePicker"), "sound") == null, "Stop releases the dedicated preview."));
        click(() -> resourceListenPoint);
        STEPS.add(() -> resourceControls = false);
        pause(200);
        STEPS.add(() -> {
            require(field(field(editor, "resourcePicker"), "sound") == null, "Leaving the owner stops its preview.");
            var picker = (EditorResourcePicker) field(editor, "resourcePicker");
            picker.close();
            Minecraft.getInstance().player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, originalHeldItem);
            resourceTarget = null;
        });
        pause(150);
        STEPS.add(() -> require(((java.util.Map<?, ?>) field(field(field(editor, "resourcePicker"), "preview"), "icons")).isEmpty(),
                "Closing the picker releases cached textures before the next frame."));
    }

    private static void renderResourceControls() {
        if (!resourceControls) return;
        var minecraft = Minecraft.getInstance();
        if (resourceKind.equals("block")) minecraft.hitResult = new net.minecraft.world.phys.BlockHitResult(
                minecraft.player.position(), net.minecraft.core.Direction.UP, minecraft.player.blockPosition().below(), false);
        else if (resourceTarget != null) minecraft.hitResult = new net.minecraft.world.phys.EntityHitResult(resourceTarget);
        ImGui.setNextWindowPos(450, 150);
        ImGui.setNextWindowSize(700, 700);
        ImGui.begin("Resource picker acceptance");
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        resourcePickerPoint = new float[] {x + 150, y + ImGui.getFrameHeight() / 2};
        ((EditorResourcePicker) field(editor, "resourcePicker")).renderRegistry("native_resource", resourceKind, resourceValue,
                value -> resourceValue = value, (EditorLibrary) field(editor, "library"),
                action -> invoke("attempt", new Class<?>[] {ImGuiAuthoringEditor.EditorOperation.class}, action));
        resourceShortcutPoint = new float[] {(ImGui.getItemRectMinX() + ImGui.getItemRectMaxX()) / 2,
                (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) / 2};
        resourceListenPoint = new float[] {x + 20, resourceShortcutPoint[1]};
        ImGui.textUnformatted(resourceValue);
        ImGui.end();
    }

    private static void renderQualityControls() {
        if (!qualityControls) return;
        ImGui.setNextWindowPos(450, 150);
        ImGui.setNextWindowSize(800, 600);
        ImGui.begin("Editor control acceptance");
        qualityPickerPoint = new float[] {ImGui.getCursorScreenPosX() + 150, ImGui.getCursorScreenPosY() + ImGui.getFrameHeight() / 2};
        ((EditorResourcePicker) field(editor, "resourcePicker")).render("acceptance_resource", "Choose an item", qualityResource,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).sorted().toList(), value -> qualityResource = value, (EditorLibrary) field(editor, "library"), action -> invoke("attempt", new Class<?>[] {ImGuiAuthoringEditor.EditorOperation.class}, action));
        ImGui.setNextItemOpen(true);
        invoke("replacementTools", new Class<?>[0]);
        ImGui.end();
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
        STEPS.add(() -> {
            try {
                EditorClientChecks.packFileEditorsPreservePendingEdits();
                EditorClientChecks.packFinalizationPreservesAdvancedMetadataAndRecovery();
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        var originalWorkspace = new AuthoringWorkspace[1];
        var originalDocument = new AuthoringDocument[1];
        var originalName = new String[1];
        var originalOutput = new String[1];
        STEPS.add(() -> {
            originalWorkspace[0] = workspace;
            originalDocument[0] = document;
            originalName[0] = ((imgui.type.ImString) field(editor, "newId")).get();
            originalOutput[0] = ((imgui.type.ImString) field(editor, "exportPath")).get();
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
            set("showCreate", false);
            invoke("openPackFile", new Class<?>[] {String.class}, "data/test/campaign_addons/editor_addon");
        });
        pause(250);
        buildFileTreeSteps();
        buildNavigationSteps();
        STEPS.add(() -> {
            capture("02c-pack-file-tree");
            var files = field(editor, "packFiles");
            ((imgui.type.ImString) field(files, "name")).set("renamed_addon");
            var plan = workspace.previewFileMove("data/test/campaign_addons/editor_addon", "data/test/campaign_addons/renamed_addon");
            setField(files, "preview", plan);
        });
        pause(250);
        STEPS.add(() -> {
            capture("02d-folder-move-preview");
            workspace.applyFileMove((AuthoringWorkspace.FileMove) field(field(editor, "packFiles"), "preview"));
            document = workspace.documents().stream().filter(value -> value.kind() == DefinitionKind.CAMPAIGN_ADDON).findFirst().orElseThrow();
            String description = "data/" + document.json().get("description_file").getAsString().replace(":", "/");
            invoke("openPackFile", new Class<?>[] {String.class}, description);
        });
        pause(250);
        STEPS.add(() -> capture("02e-description-file"));
        replaceText(() -> screenPoint(600, 330), () -> "# Edited in the file browser");
        click(() -> toolbar("Save"));
        STEPS.add(() -> {
            var addon = workspace.documents().stream().filter(value -> value.kind() == DefinitionKind.CAMPAIGN_ADDON).findFirst().orElseThrow();
            try { require(workspace.campaignDescription(addon).equals("# Edited in the file browser"), "Native file editing saves Markdown attachments."); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        click(() -> toolbar("Build pack"));
        pause(250);
        STEPS.add(() -> capture("02f-pack-finalization"));
        replaceText(() -> screenPoint(600, 285), () -> "Village Stories release");
        STEPS.add(() -> {
            setField(field(editor, "packBuild"), "zip", true);
            ((imgui.type.ImString) field(editor, "exportPath")).set(output().resolve("village-stories.zip").toString());
        });
        pause(250);
        click(() -> screenPoint(375, 840));
        pause(250);
        STEPS.add(() -> {
            try (var zip = new java.util.zip.ZipFile(output().resolve("village-stories.zip").toFile())) {
                require(zip.getEntry("pack.mcmeta") != null && zip.getEntry("data/test/campaign_addons/renamed_addon/addon.json") != null,
                        "Finalization builds a shareable ZIP containing the renamed addon.");
                try (var stream = zip.getInputStream(zip.getEntry("pack.mcmeta"))) {
                    require(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("Village Stories release"),
                            "Finalization includes the description typed into its settings.");
                }
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            capture("02g-built-package");
            setField(field(editor, "packBuild"), "zip", false);
            ((imgui.type.ImString) field(editor, "exportPath")).set(originalOutput[0]);
            set("filesMode", false);
            set("view", 0);
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

    private static void buildFileTreeSteps() {
        String addon = "data/test/campaign_addons/editor_addon";
        rightClick(() -> screenPoint(180, 415));
        STEPS.add(() -> capture("02c1-file-context-menu"));
        click(() -> screenPoint(230, 541));
        STEPS.add(() -> require(field(field(editor, "packFiles"), "folderParent").equals(addon), "Right-click creates folders in the chosen tree folder."));
        STEPS.add(() -> ImGui.getIO().addInputCharactersUTF8("drafts"));
        key(ImGuiKey.Enter);
        pause(250);
        STEPS.add(() -> {
            require(workspace.folderPaths().contains(addon + "/drafts"), "The native new-folder dialog creates an empty draft folder.");
            capture("02c2-new-folder");
        });
        rightClick(() -> screenPoint(180, 443));
        click(() -> screenPoint(230, 513));
        STEPS.add(() -> require(field(field(editor, "packFiles"), "selected").equals(addon + "/drafts"), "Rename targets the right-clicked folder."));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        STEPS.add(() -> ImGui.getIO().addInputCharactersUTF8("release"));
        click(() -> screenPoint(350, 586));
        STEPS.add(() -> {
            var plan = (AuthoringWorkspace.FileMove) field(field(editor, "packFiles"), "preview");
            require(plan != null && plan.destination().equals(addon + "/release"), "Rename edits create the expected folder move preview.");
        });
        click(() -> screenPoint(350, 690));
        STEPS.add(() -> require(workspace.folderPaths().contains(addon + "/release"), "Apply move renames an empty folder."));
        move(() -> screenPoint(180, 471), false);
        move(() -> screenPoint(180, 471), true);
        move(() -> screenPoint(200, 478), true);
        STEPS.add(() -> {
            Object drag = field(field(editor, "packFiles"), "dragging");
            require(drag != null && field(drag, "source").equals(addon + "/release"), "Dragging uses the renamed folder as its source.");
        });
        move(() -> screenPoint(180, 443), true);
        STEPS.add(() -> mouseDown = false);
        pause(250);
        STEPS.add(() -> {
            var plan = (AuthoringWorkspace.FileMove) field(field(editor, "packFiles"), "preview");
            require(plan != null && plan.source().equals(addon + "/release") && plan.destination().equals(addon + "/quests/release"), "Dragging a folder onto another folder prepares a safe move preview.");
            capture("02c3-drag-drop-preview");
        });
        click(() -> screenPoint(350, 690));
        STEPS.add(() -> {
            require(workspace.folderPaths().contains(addon + "/quests/release"), "Drag-and-drop moves preserve empty folders.");
            invoke("openPackFile", new Class<?>[] {String.class}, addon);
        });
        pause(250);
        move(() -> screenPoint(180, 555), false);
        move(() -> screenPoint(180, 555), true);
        move(() -> screenPoint(200, 562), true);
        move(() -> screenPoint(180, 443), true);
        STEPS.add(() -> mouseDown = false);
        pause(250);
        STEPS.add(() -> {
            String source = "data/" + document.json().get("description_file").getAsString().replace(":", "/");
            var plan = (AuthoringWorkspace.FileMove) field(field(editor, "packFiles"), "preview");
            require(plan != null && plan.source().equals(source) && plan.destination().startsWith(addon + "/quests/"), "Dragging a file prepares a move into the target folder.");
            capture("02c4-file-drop-preview");
        });
        click(() -> screenPoint(350, 944));
        STEPS.add(() -> {
            require(document.json().get("description_file").getAsString().contains("/quests/"), "Applying file drops updates campaign asset references.");
            invoke("openPackFile", new Class<?>[] {String.class}, addon);
        });
        pause(250);
    }

    private static void buildNavigationSteps() {
        String addon = "data/test/campaign_addons/editor_addon";
        var selected = new AuthoringDocument[1];
        var selectedPointer = new String[1];
        STEPS.add(() -> {
            selected[0] = workspace.documents().stream().filter(value -> value.kind() == DefinitionKind.QUEST).findFirst().orElseThrow();
            invoke("select", new Class<?>[] {AuthoringDocument.class}, selected[0]);
            set("view", 0); set("fitGraph", false); set("graphFocus", "");
            set("panX", 120f); set("panY", 80f); set("zoom", .75f);
        });
        pause(250);
        STEPS.add(() -> {
            selectedPointer[0] = ((imgui.type.ImString) field(editor, "pointer")).get();
            invoke("openPackFile", new Class<?>[] {String.class}, "pack.mcmeta");
        });
        click(() -> toolbar("Back"));
        STEPS.add(() -> require(field(editor, "document") == selected[0] && (int) field(editor, "view") == 0
                && ((imgui.type.ImString) field(editor, "pointer")).get().equals(selectedPointer[0])
                && (float) field(editor, "panX") == 120f && (float) field(editor, "panY") == 80f && (float) field(editor, "zoom") == .75f,
                "Back restores the graph document, field, pan and zoom after opening metadata."));
        STEPS.add(() -> invoke("openFolder", new Class<?>[] {String.class}, addon));
        click(() -> toolbar("Build pack"));
        STEPS.add(() -> invoke("openPackFile", new Class<?>[] {String.class}, "pack.mcmeta"));
        replaceText(() -> screenPoint(600, 330), () -> "{");
        click(() -> toolbar("Back"));
        STEPS.add(() -> {
            require((int) field(editor, "view") == 2, "Invalid metadata does not block returning to pack finalization.");
            var files = (EditorPackFiles) field(editor, "packFiles");
            require(files.pending("pack.mcmeta") && files.recovery().getAsJsonObject("files").getAsJsonObject("pack.mcmeta").get("text").getAsString().equals("{"),
                    "Navigation retains invalid metadata in its own file draft.");
        });
        click(() -> toolbar("Forward"));
        pause(250);
        STEPS.add(() -> require(((imgui.type.ImString) field(field(editor, "packFiles"), "text")).get().equals("{"), "Returning to metadata restores the unfinished text."));
        replaceText(() -> screenPoint(600, 330), () -> {
            var metadata = workspace.packMetadata(); metadata.getAsJsonObject("pack").addProperty("description", "Navigation keeps edits");
            return metadata.toString();
        });
        click(() -> toolbar("Back"));
        STEPS.add(() -> {
            require((int) field(editor, "view") == 2, "Back returns from pack.mcmeta to pack finalization.");
            require(((String) field(editor, "status")).startsWith("Back to"), "Successful navigation clears the previous error message.");
            require(((EditorPackFiles) field(editor, "packFiles")).pending("pack.mcmeta"), "Valid edits remain in their file until saved.");
        });
        click(() -> toolbar("Save"));
        STEPS.add(() -> {
            require(workspace.packMetadata().getAsJsonObject("pack").get("description").getAsString().equals("Navigation keeps edits"), "Save applies valid metadata from an inactive file.");
            capture("02h-navigation-back");
        });
        click(() -> toolbar("Back"));
        STEPS.add(() -> require((int) field(editor, "view") == 4 && field(field(editor, "packFiles"), "selected").equals(addon), "Back returns to the previous folder."));
        click(() -> toolbar("Forward"));
        click(() -> toolbar("Forward"));
        STEPS.add(() -> require(field(field(editor, "packFiles"), "selected").equals("pack.mcmeta"), "Forward reopens the metadata file."));
        click(() -> breadcrumb(""));
        STEPS.add(() -> {
            require(field(field(editor, "packFiles"), "selected").equals(""), "The root breadcrumb opens the pack root from pack.mcmeta.");
            capture("02i-pack-root-navigation");
        });
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, true));
        chord(ImGuiKey.ImGuiMod_Alt, ImGuiKey.LeftArrow);
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, false));
        STEPS.add(() -> require(field(field(editor, "packFiles"), "selected").equals("pack.mcmeta"), "Ctrl+Alt+Left navigates back."));
        click(() -> screenPoint(600, 330));
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, true));
        chord(ImGuiKey.ImGuiMod_Alt, ImGuiKey.RightArrow);
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, false));
        STEPS.add(() -> require(field(field(editor, "packFiles"), "selected").equals(""), "Ctrl+Alt+Right works while a text file is focused."));
        STEPS.add(() -> ImGui.getIO().addMouseButtonEvent(3, true));
        STEPS.add(() -> ImGui.getIO().addMouseButtonEvent(3, false));
        STEPS.add(() -> require(field(field(editor, "packFiles"), "selected").equals("pack.mcmeta"), "The mouse back button reopens the previous file."));
        STEPS.add(() -> ImGui.getIO().addMouseButtonEvent(4, true));
        STEPS.add(() -> ImGui.getIO().addMouseButtonEvent(4, false));
        STEPS.add(() -> require(field(field(editor, "packFiles"), "selected").equals(""), "The mouse forward button restores the next location."));
        STEPS.add(() -> invoke("openPackFile", new Class<?>[] {String.class}, "pack.mcmeta"));
        chord(ImGuiKey.ImGuiMod_Alt, ImGuiKey.UpArrow);
        STEPS.add(() -> {
            require(field(field(editor, "packFiles"), "selected").equals(""), "Alt+Up opens the containing folder.");
            invoke("openFolder", new Class<?>[] {String.class}, addon + "/quests/release");
        });
        click(() -> breadcrumb("data/test"));
        STEPS.add(() -> require(field(field(editor, "packFiles"), "selected").equals("data/test"), "Breadcrumbs open the chosen containing folder."));
        click(() -> toolbar("Back"));
        STEPS.add(() -> {
            require(field(field(editor, "packFiles"), "selected").equals(addon + "/quests/release"), "Breadcrumb navigation participates in history.");
            ((imgui.type.ImString) field(editor, "filter")).set("no_matching_file");
        });
        click(() -> clearSearchPoint());
        STEPS.add(() -> require(((imgui.type.ImString) field(editor, "filter")).get().isEmpty(), "Clear search restores the file tree."));
        click(() -> screenPoint(600, 150));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.F);
        STEPS.add(() -> ImGui.getIO().addInputCharactersUTF8("release"));
        STEPS.add(() -> require(((imgui.type.ImString) field(editor, "filter")).get().equals("release"), "Ctrl+F focuses search from a folder view."));
        click(() -> clearSearchPoint());
        STEPS.add(() -> invoke("openFolder", new Class<?>[] {String.class}, addon));
        pause(250);
    }

    private static float[] breadcrumb(String target) {
        float x = ImGui.getMainViewport().getWorkPosX() + 14;
        float scale = ((EditorPreferences) field(editor, "preferences")).textScale();
        String root = workspace.directory().getFileName().toString();
        float rootWidth = ImGui.calcTextSize(root).x * scale + 16;
        if (target.isEmpty()) return new float[] {x + rootWidth / 2, ImGui.getMainViewport().getWorkPosY() + 64};
        x += rootWidth;
        String path = (String) field(field(editor, "packFiles"), "selected");
        for (String folder : EditorNavigation.ancestors(path)) {
            x += ImGui.calcTextSize("/").x * scale + 16;
            float width = ImGui.calcTextSize(folder.substring(folder.lastIndexOf('/') + 1)).x * scale + 16;
            if (folder.equals(target)) return new float[] {x + width / 2, ImGui.getMainViewport().getWorkPosY() + 64};
            x += width;
        }
        throw new IllegalArgumentException(target);
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
            set("preferences", ((EditorPreferences) field(editor, "preferences")).withScale(1));
            GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(), 1920, 1080);
        });
        pause(400);
        STEPS.add(() -> { Minecraft.getInstance().resizeDisplay(); set("resetLayout", true); });
        buildLiveWorldSteps();
    }

    private static void buildLiveWorldSteps() {
        STEPS.add(() -> {
            repeatedLiveTree = workspace.createDocument(DefinitionKind.TREE, "test:live_repeated_lines");
            repeatedLiveTree.edit("Set repeated exchanges", root -> {
                var node = root.getAsJsonObject("nodes").getAsJsonObject("start");
                node.remove("lines");
                node.add("exchanges", com.google.gson.JsonParser.parseString("""
                        [{"id":"a","speaker":{"type":"role","role":"provider"},"text":"Repeated line"},
                         {"id":"b","speaker":{"type":"role","role":"provider"},"text":"Repeated line"}]
                        """));
            });
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
            require(!EditorTestWorld.active(workspace), "Live testing waits for explicit setup in this world.");
            try { EditorLiveQuestTest.start(workspace, quest); throw new IllegalStateException("Live start escaped setup"); }
            catch (IllegalArgumentException expected) { }
            try { reloadCheck = EditorTestWorld.prepareCurrent(workspace); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        waitUntil(() -> reloadCheck.isDone());
        STEPS.add(() -> reloadCheck.join());
        STEPS.add(() -> {
            var minecraft = Minecraft.getInstance();
            require(originalWorld.equals(minecraft.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize()),
                    "Live quest testing keeps the current world directory.");
            require(Files.isRegularFile(originalWorld.resolve("datapacks/test_content/pack.mcmeta")),
                    "The current world contains the managed test pack.");
            require(minecraft.getSingleplayerServer().getPackRepository().getSelectedIds().contains("file/test_content"), "The created server loaded the test pack.");
            require(EditorTestWorld.currentSnapshot(workspace), "Live execution identifies the exported snapshot.");
            EditorTestWorld.disconnect();
            require(EditorTestWorld.active(workspace) && EditorTestWorld.currentSnapshot(workspace), "Live controls restore their workspace and snapshot after losing the in-memory session.");
            var server = minecraft.getSingleplayerServer();
            var playerId = minecraft.player.getUUID();
            liveCheck = server.submit(() -> initialLiveExperience = server.getPlayerList().getPlayer(playerId).totalExperience);
            EditorLiveQuestTest.start(workspace, document);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        STEPS.add(() -> {
            require(EditorLiveQuestTest.view().message().contains("Started quest"), "Live quest starts through the runtime: " + EditorLiveQuestTest.view());
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                require(player.totalExperience == initialLiveExperience + 7, "Live entry actions grant real experience exactly once.");
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
                require(player.totalExperience == initialLiveExperience + 24, "Live quest rewards execute exactly once: " + player.totalExperience);
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
            require(((EditorCoverage) field(editor, "coverage")).visited(document, "/stages/0"), "Live quest startup records the actual entry stage.");
            String objective = EditorLiveQuestTest.snapshot().objectives().getFirst().id();
            EditorLiveQuestTest.prepare(workspace, document, objective, "");
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        pause(1500);
        STEPS.add(() -> {
            require(EditorLiveQuestTest.snapshot().stage().equals("start") && EditorLiveQuestTest.snapshot().ready(),
                    "Real inventory requirements become ready while the stage stays held: " + EditorLiveQuestTest.snapshot());
            invoke("focusPlaytestRequirement", new Class<?>[] {String.class}, EditorLiveQuestTest.snapshot().objectives().getFirst().id());
            require(((imgui.type.ImString) field(editor, "pointer")).get().equals("/stages/0/objectives/0"), "Requirement links select the authored objective.");
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
            require(((EditorCoverage) field(editor, "coverage")).chosen(document, "/stages/0/transition"), "Live stage execution records its source transition.");
            capture("30-playtest-quest-next");
        });
        buildPlaytestReloadSteps();
    }

    private static void buildPlaytestReloadSteps() {
        STEPS.add(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            reloadWorld = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
            reloadStage = EditorLiveQuestTest.snapshot().stage();
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
            long started = System.nanoTime();
            invoke("applyPlaytestDraft", new Class<?>[0]);
            double elapsed = (System.nanoTime() - started) / 1e6;
            System.out.printf(java.util.Locale.ROOT, "Playtest apply dispatch: %.3f ms%n", elapsed);
            require(elapsed < 100, "Applying a draft returns to rendering while export and validation run in the background.");
            return true;
        });
        waitUntil(() -> {
            if (!EditorTestWorld.applying() && field(editor, "status").toString().startsWith("Draft reload failed"))
                throw new IllegalStateException(field(editor, "status").toString());
            return !EditorTestWorld.applying() && EditorTestWorld.currentSnapshot(workspace)
                    && !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().stage().equals(reloadStage);
        });
        STEPS.add(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> require(server.getPlayerList().getPlayer(playerId).totalExperience == reloadExperience,
                    "Apply changes retains progress without replaying entry actions."));
            require(reloadWorld.equals(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize()),
                    "Applying logic keeps the same test world.");
            capture("31-playtest-applied-draft");
        });
        click(() -> poolPoint(1570, 337));
        pause(300);
        STEPS.add(() -> capture("31a-playtest-tools"));
        click(() -> poolPoint(1650, 337));
        pause(900);
        STEPS.add(() -> {
            require(!EditorLiveQuestTest.view().lines().isEmpty(), "The visible Log tab loads runtime diagnostics.");
            capture("31b-playtest-log");
        });
        click(() -> poolPoint(1740, 337));
        pause(300);
        STEPS.add(() -> {
            require(field(field(editor, "questLiveTests"), "owner") == document, "The Live tests tab renders the selected quest's saved scenarios.");
            capture("31c-live-test-tab");
        });
        click(() -> poolPoint(1320, 337));
        pause(300);
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> {
            liveCheck.join();
            EditorLiveQuestTest.startAt(workspace, document, true, "start");
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().stage().equals("start"));
        STEPS.add(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            liveCheck = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                require(player.totalExperience == reloadExperience + 11, "Explicit restart executes the updated entry action once.");
                var quest = net.minecraft.resources.ResourceLocation.parse(document.id());
                var facts = com.jvn.villagerretaliation.quest.VillagerQuestFacts.get(player.serverLevel());
                String scope = com.jvn.villagerretaliation.quest.QuestScopeKey.quest(playerId, quest).asString();
                facts.setVariable(scope, "baseline_check", "before");
                var position = player.position();
                var inventory = player.getInventory().getItem(0).copy();
                com.jvn.villagerretaliation.authoring.preview.QuestPlaytestBaseline.capture(player, quest, "Repeatable run");
                player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 9));
                player.giveExperiencePoints(37);
                var data = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(player.serverLevel());
                data.get(playerId, quest).setCurrentStage("finish");
                player.teleportTo(player.serverLevel(), position.x + 5, position.y, position.z, player.getYRot(), player.getXRot());
                facts.setVariable(scope, "baseline_check", "after");
                com.jvn.villagerretaliation.authoring.preview.QuestPlaytestBaseline.restore(player, quest, "Repeatable run");
                require(player.totalExperience == reloadExperience + 11, "Baseline restores experience after test rewards.");
                require(net.minecraft.world.item.ItemStack.matches(inventory, player.getInventory().getItem(0)), "Baseline restores inventory without retaining test supplies.");
                require(player.position().distanceToSqr(position) < .001 && facts.variable(scope, "baseline_check").orElseThrow().equals("before"), "Baseline restores position and quest facts.");
                require(data.get(playerId, quest).currentStage().equals("start"), "Baseline restores the saved quest stage.");
                var binding = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.selection(player);
                var provider = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(server, binding.provider());
                var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(player.serverLevel(), player, provider);
                repeatedLiveSpeech = com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.startEntry(context,
                        net.minecraft.resources.ResourceLocation.parse(repeatedLiveTree.id()), "default").orElseThrow().exchanges();
                require(repeatedLiveSpeech.size() == 2, "Repeated authored exchanges reach the runtime.");
                require(com.jvn.villagerretaliation.authoring.preview.PlaytestLineSources.get(repeatedLiveSpeech.get(0).presentationId()).line().equals("a")
                        && com.jvn.villagerretaliation.authoring.preview.PlaytestLineSources.get(repeatedLiveSpeech.get(1).presentationId()).line().equals("b"),
                        "Runtime speech carries distinct source identities without changing packets.");
            });
        });
        waitUntil(() -> liveCheck.isDone());
        STEPS.add(() -> {
            liveCheck.join();
            repeatedLiveTree.edit("Edit identical live lines", root -> {
                var exchanges = root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("exchanges");
                exchanges.get(0).getAsJsonObject().addProperty("text", "First independent edit");
                exchanges.get(1).getAsJsonObject().addProperty("text", "Second independent edit");
            });
        });
        pause(500);
        STEPS.add(() -> {
            require(EditorPlaytestPresentation.speech(repeatedLiveSpeech.get(0)).text().equals("First independent edit")
                    && EditorPlaytestPresentation.speech(repeatedLiveSpeech.get(1)).text().equals("Second independent edit"),
                    "Identical live wording updates independently by document, node and exchange identity.");
            repeatedLiveTree.undo();
        });
        pause(300);
        STEPS.add(() -> {
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
            document.edit("Require explicit restart", root -> {
                root.addProperty("revision", 4);
                root.add("migration", JsonParser.parseString("{\"active_policy\":\"restart\"}").getAsJsonObject());
            });
            require(!EditorTestWorld.currentLogic(workspace), "The migration draft is intentionally unapplied.");
        });
        waitUntil(() -> {
            if (EditorLiveQuestTest.view().busy()) return false;
            EditorLiveQuestTest.offer(workspace, document, false, true);
            return true;
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().state().equals("offered"));
        STEPS.add(() -> {
            require(invoke("playtestRestartReason", new Class<?>[0]).toString().isEmpty(),
                    "Restarting an unapplied migration removes the apply deadlock.");
            invoke("applyPlaytestDraft", new Class<?>[0]);
        });
        waitUntil(() -> !EditorTestWorld.applying() && EditorTestWorld.currentSnapshot(workspace));
        STEPS.add(() -> {
            require(EditorLiveQuestTest.snapshot().state().equals("offered"), "Applying after restart keeps normal acceptance explicit.");
            capture("33-playtest-restart-after-edit");
            if (!"improvements".equals(System.getProperty("villagerretaliation.authoringEditorPlaytestSmokeOnly")))
                invoke("leavePlaytest", new Class<?>[] {int.class}, 1);
        });
    }

    private static void buildDiscoveryPlaytestSteps() {
        STEPS.add(() -> {
            document = workspace.createDocument(DefinitionKind.QUEST, "test:discovery_playtest");
            document.replaceJson("""
                    {"schema":"villagerretaliation:quest/v3","id":"test:discovery_playtest","localization_prefix":"test.quest.discovery_playtest",
                     "provider":{"type":"villagerretaliation:discovery"},"metadata":{"questline":"authored"},"display":{"title":{"key":"#title"}},
                     "availability":{"max_starts":0,"max_completions":0},
                     "discovery":[{"id":"letter","event":"item_acquired","mode":"automatic","match":{"book":"test:letter"}}],
                     "entry_stage":"work","stages":[
                       {"id":"work","objectives":[{"id":"diamond","type":"item_check","item":"minecraft:diamond","count":1}],"transition":{"stage":"return"}},
                       {"id":"return","objectives":[]}]}
                    """);
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            workspace.documents().stream().filter(value -> value.resourcePath().endsWith("/discovery_playtest/locales/en_us.json"))
                    .findFirst().orElseThrow().edit("Set discovery title", root -> root.addProperty("test.quest.discovery_playtest.title", "Discovery playtest"));
            var errors = workspace.validate().stream().filter(issue -> issue.severity() == com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR).toList();
            require(errors.isEmpty(), "The discovery client fixture is valid: " + errors);
            try { reloadCheck = EditorTestWorld.apply(workspace); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        waitUntil(() -> reloadCheck.isDone());
        STEPS.add(() -> {
            reloadCheck.join();
            EditorLiveQuestTest.offer(workspace, document, true, true);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().playerOnly());
        STEPS.add(() -> {
            require(EditorLiveQuestTest.snapshot().provider() == -1 && EditorLiveQuestTest.snapshot().discoveries().size() == 1,
                    "Discovery playtesting exposes the route without fabricating a villager.");
            capture("34-discovery-playtest-offer");
            EditorLiveQuestTest.baseline(workspace, document, "Before discovery", "save");
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy());
        STEPS.add(() -> EditorLiveQuestTest.discovery(workspace, document, "letter", false));
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().state().equals("active"));
        STEPS.add(() -> EditorLiveQuestTest.prepare(workspace, document, EditorLiveQuestTest.snapshot().objectives().getFirst().id(), ""));
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().ready());
        STEPS.add(() -> {
            capture("35-discovery-playtest-ready");
            EditorLiveQuestTest.next(workspace, document);
        });
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().stage().equals("return"));
        STEPS.add(() -> EditorLiveQuestTest.turnIn(workspace, document));
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().state().equals("completed"));
        STEPS.add(() -> EditorLiveQuestTest.baseline(workspace, document, "Before discovery", "restore"));
        waitUntil(() -> !EditorLiveQuestTest.view().busy() && EditorLiveQuestTest.snapshot().state().equals("offered"));
        STEPS.add(() -> invoke("leavePlaytest", new Class<?>[] {int.class}, 1));
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
        click(() -> screenPoint(365, 561));
        STEPS.add(() -> require(cameraSession() != null && cameraSession().active() && Minecraft.getInstance().options.hideGui, "Begin camera session starts the native camera with a clean viewport."));
        click(() -> screenPoint(350, 709));
        click(() -> screenPoint(365, 749));
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
        click(() -> screenPoint(500, 749));
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
        click(() -> screenPoint(375, 829));
        click(() -> screenPoint(560, 561));
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
        replaceText(() -> screenPoint(120, 297), () -> output().resolve("import-drafts").toString());
        click(() -> screenPoint(200, 337));
        click(() -> screenPoint(140, 153));
        STEPS.add(() -> capture("17-empty-import-workspace"));
        click(() -> screenPoint(140, 330));
        replaceText(() -> screenPoint(130, 398), () -> output().resolve("setup-export").toString());
        click(() -> screenPoint(70, 438));
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
        move(() -> screenPoint(1300, 900), false);
        STEPS.add(() -> {
            require(((imgui.type.ImString) field(editor, "pointer")).get().equals("/nodes/start/lines"),
                    "Styled blocks retain direct text editing.");
            require(field(graphPreview(), "text").equals(GREETING),
                    "The visible graph uses the authored formatting source.");
            try (var image = renderedImage()) {
                float[] min = point("start", 0, EditorGraph.HEADER);
                var card = EditorGraph.cards(workspace, document, "en_us").stream().filter(value -> value.node().id().equals("start")).findFirst().orElseThrow();
                float[] max = point("start", EditorGraph.WIDTH, card.blockY(1));
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
        move(() -> screenPoint(1300, 900), false);
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
        insertVariable("player");
        expectCaret("Welcome, {player}");
        type("! It is ");
        expectCaret("Welcome, {player}! It is ");
        insertVariable("time");
        type(" (");
        insertVariable("time_of_day");
        type("), day ");
        insertVariable("day");
        type(".");
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.Home);
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Shift, true));
        for (int i = 0; i < 7; i++) key(ImGuiKey.RightArrow);
        STEPS.add(() -> ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Shift, false));
        click(() -> textTool("I"));
        click(() -> textTool("B"));
        click(() -> textTool("U"));
        click(() -> textTool("Style"));
        key(ImGuiKey.Home);
        key(ImGuiKey.DownArrow);
        key(ImGuiKey.DownArrow);
        key(ImGuiKey.Enter);
        click(() -> textTool("Style"));
        key(ImGuiKey.Home);
        key(ImGuiKey.RightArrow);
        pause(500);
        STEPS.add(() -> capture("10b-color-menu"));
        key(ImGuiKey.Home);
        key(ImGuiKey.DownArrow);
        key(ImGuiKey.DownArrow);
        key(ImGuiKey.Enter);
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
            var selection = greetingSelection();
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
        Object selection = selections.get("/nodes/start/lines/0");
        require(selection != null, "The Details panel has the greeting field.");
        return selection;
    }

    private static void expectCaret(String expected) {
        STEPS.add(() -> {
            var selection = greetingSelection();
            String actual = ((imgui.type.ImString) field(selection, "buffer")).get();
            require(actual.equals(expected) && (int) field(selection, "start") == EditorTextEdit.bytes(expected)
                    && (int) field(selection, "end") == EditorTextEdit.bytes(expected),
                    "Text insertion keeps the caret: " + actual + " [" + field(selection, "start") + ", " + field(selection, "end") + "]");
        });
    }

    private static void insertVariable(String name) {
        click(() -> textTool("Insert variable"));
        type(name);
        STEPS.add(() -> capture("10a-variable-menu"));
        key(ImGuiKey.Enter);
    }

    private static float[] textTool(String target) {
        float x = ImGui.getMainViewport().getWorkSizeX() - 380;
        for (String label : new String[] {"B", "I", "U", "Insert variable", "Style"}) {
            float width = ImGui.calcTextSize(label).x + 8;
            if (label.equals(target)) return screenPoint(x + width / 2, 283);
            x += width + 8;
        }
        throw new IllegalArgumentException(target);
    }

    private static String previewGreeting(String time, String phase) {
        return "Welcome, " + Minecraft.getInstance().player.getName().getString() + "! It is " + time + " (" + phase + "), day 1.";
    }

    private static void buildInlineSteps() {
        var originalWorkspace = new AuthoringWorkspace[1];
        var originalDocument = new AuthoringDocument[1];
        STEPS.add(() -> {
            originalWorkspace[0] = workspace; originalDocument[0] = document;
            try { workspace = AuthoringWorkspace.create(output().resolve("inline-drafts")); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            set("workspace", workspace);
            document = workspace.createDocument(DefinitionKind.TREE, "test:inline_controls");
            DialogueGraph.addNode(document, "finish");
            DialogueGraph.addResponse(document, "start", "continue", "Tell me more.", "finish");
            EditorBlocks.append(document, "/nodes/start", EditorBlocks.Kind.REPUTATION_GATE);
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
            set("zoom", 1f); set("panX", 25f); set("panY", 25f); set("fitGraph", false);
            locateCanvas();
        });
        click(() -> inlinePoint("start", "/nodes/start/lines/0"));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        type("Written inside the node.\nSecond line.");
        STEPS.add(() -> {
            require(ImGui.getIO().getWantTextInput(), "Inline dialogue takes keyboard focus.");
            capture("02-inline-typing");
        });
        click(() -> point("finish", 100, 18));
        STEPS.add(() -> require(document.value("/nodes/start/lines/0").getAsString().equals("Written inside the node.\nSecond line."), "Leaving an inline field applies its wording."));
        click(() -> inlinePoint("start", "/nodes/start/lines/0"));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        type("Hello, Zo\u00eb!");
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.B);
        STEPS.add(() -> require(inlineText().equals("<b>Hello, Zo\u00eb!</b>"), "Ctrl+B formats the active inline selection: " + inlineText()));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.B);
        STEPS.add(() -> require(inlineText().equals("Hello, Zo\u00eb!"), "Ctrl+B toggles the selected style."));
        click(() -> inlineTool("B"));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.I);
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.U);
        STEPS.add(() -> {
            require(inlineText().equals("<b><i><u>Hello, Zo\u00eb!</u></i></b>"), "Toolbar clicks preserve Unicode selection and keyboard focus.");
            capture("02-inline-formatting-toolbar");
            try { ((EditorLibrary) field(editor, "library")).shortcut("Text: Bold", "Ctrl+Shift+B"); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.B);
        STEPS.add(() -> require(inlineText().equals("<b><i><u>Hello, Zo\u00eb!</u></i></b>"), "Rebinding disables the old formatting chord."));
        STEPS.add(() -> { ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, true); ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Shift, true); });
        key(ImGuiKey.B);
        STEPS.add(() -> { ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, false); ImGui.getIO().addKeyEvent(ImGuiKey.ImGuiMod_Shift, false); });
        STEPS.add(() -> {
            require(inlineText().equals("<b><i><u><b>Hello, Zo\u00eb!</b></u></i></b>"), "The rebound formatting chord applies to the active selection.");
            try { ((EditorLibrary) field(editor, "library")).shortcut("Text: Bold", "Ctrl+B"); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
        click(() -> inlineTool("Style"));
        STEPS.add(() -> capture("02-inline-style-menu"));
        key(ImGuiKey.Home);
        key(ImGuiKey.DownArrow);
        key(ImGuiKey.DownArrow);
        key(ImGuiKey.Enter);
        STEPS.add(() -> require(inlineText().contains("<wave>"), "The Style menu preserves the active selection."));
        click(() -> inlineTool("Style"));
        key(ImGuiKey.Home);
        key(ImGuiKey.RightArrow);
        STEPS.add(() -> capture("02-inline-color-menu"));
        key(ImGuiKey.Home);
        key(ImGuiKey.DownArrow);
        key(ImGuiKey.DownArrow);
        key(ImGuiKey.Enter);
        STEPS.add(() -> require(inlineText().contains("<color:gold>"), "The visual color palette applies a named color: " + inlineText()));
        click(() -> inlineTool("Style"));
        key(ImGuiKey.Home);
        key(ImGuiKey.RightArrow);
        key(ImGuiKey.End);
        key(ImGuiKey.Enter);
        STEPS.add(() -> require(inlineText().contains("<color:#"), "The custom color control applies a hex color: " + inlineText()));
        click(() -> inlineTool("Style"));
        key(ImGuiKey.End);
        key(ImGuiKey.UpArrow);
        key(ImGuiKey.UpArrow);
        key(ImGuiKey.RightArrow);
        STEPS.add(() -> capture("02-inline-gradient-menu"));
        key(ImGuiKey.End);
        key(ImGuiKey.Enter);
        STEPS.add(() -> require(inlineText().contains("<gradient:#"), "The gradient controls apply both colors: " + inlineText()));
        click(() -> point("finish", 100, 18));
        STEPS.add(() -> {
            require(document.value("/nodes/start/lines/0").getAsString().contains("<wave>"), "The Style menu applies an effect and commits when leaving the field: " + document.value("/nodes/start/lines/0").getAsString());
            require(document.value("/nodes/start/lines/0").getAsString().contains("Zo\u00eb"), "Formatting preserves the original wording.");
        });
        click(() -> inlinePoint("start", "/nodes/start/responses/0/label"));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        type("Ask about the village.");
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.B);
        click(() -> point("finish", 100, 18));
        STEPS.add(() -> require(document.value("/nodes/start/responses/0/label").getAsString().equals("Ask about the village."), "Response wording edits inside the node."));
        click(() -> inlinePoint("start", "/nodes/start/conditions/0/min"));
        chord(ImGuiKey.ImGuiMod_Ctrl, ImGuiKey.A);
        type("12");
        click(() -> point("finish", 100, 18));
        STEPS.add(() -> {
            require(document.value("/nodes/start/conditions/0/min").getAsInt() == 12, "Inline condition values apply as numbers.");
            require(DialogueGraph.edges(document).getFirst().to().equals("finish"), "Inline editing preserves the response connection.");
            capture("02-inline-edited-nodes");
        });
        click(() -> toolbar("Undo"));
        STEPS.add(() -> require(document.value("/nodes/start/conditions/0/min").getAsInt() == 0, "Inline numeric edits undo normally."));
        click(() -> toolbar("Redo"));
        STEPS.add(() -> {
            require(document.value("/nodes/start/conditions/0/min").getAsInt() == 12, "Inline numeric edits redo normally.");
            workspace = originalWorkspace[0]; document = originalDocument[0];
            set("workspace", workspace);
            invoke("select", new Class<?>[] {AuthoringDocument.class}, document);
        });
    }

    private static String inlineText() {
        return ((Map<String, imgui.type.ImString>) field(editor, "fields")).get("/nodes/start/lines/0").get();
    }

    private static float[] inlineTool(String target) {
        float[] input = inlinePoint("start", "/nodes/start/lines/0");
        float x = point("start", 12, 0)[0];
        for (String label : new String[] {"B", "I", "U", "Insert variable", "Style"}) {
            float width = ImGui.calcTextSize(label).x + 8;
            if (label.equals(target)) return new float[] {x + width / 2, input[1] - 15 + ImGui.getTextLineHeight() / 2};
            x += width + ImGui.getStyle().getItemSpacingX();
        }
        throw new IllegalArgumentException(target);
    }

    private static float[] inlinePoint(String node, String path) {
        var card = EditorGraph.cards(workspace, document, "en_us").stream().filter(value -> value.node().id().equals(node)).findFirst().orElseThrow();
        for (int i = 0; i < card.blocks().size(); i++) {
            float y = card.blockY(i) + 28;
            for (var field : card.blocks().get(i).fields()) {
                if (field.pointer().equals(path)) return point(node, 90, y + 35);
                y += field.height() + (field.multiline() ? card.fieldGrowth() : 0);
            }
        }
        throw new AssertionError("Missing inline field: " + path);
    }

    private static float[] blockDetails(String node, String path) {
        var card = EditorGraph.cards(workspace, document, "en_us").stream().filter(value -> value.node().id().equals(node)).findFirst().orElseThrow();
        for (int i = 0; i < card.blocks().size(); i++) if (card.blocks().get(i).pointer().equals(path))
            return point(node, card.width() - 28, card.blockY(i) + 12);
        throw new AssertionError("Missing graph block: " + path);
    }

    private static void type(String text) { STEPS.add(() -> ImGui.getIO().addInputCharactersUTF8(text)); }

    private static void key(int key) {
        STEPS.add(() -> { ImGui.getIO().addKeyEvent(key, true); keyRelease = key; });
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

    private static float contentColumnWidth() {
        float width = ImGui.getMainViewport().getWorkSizeX() - 28;
        float scale = ((EditorPreferences) field(editor, "preferences")).textScale();
        return width * EditorLayout.forWidth(width / scale).contentFraction();
    }

    private static float[] clearSearchPoint() {
        return new float[] {ImGui.getMainViewport().getPosX() + contentColumnWidth() - 34, ImGui.getMainViewport().getPosY() + 193};
    }

    private static float[] screenPoint(float x, float y) {
        float referenceContentWidth = 250;
        if (x >= referenceContentWidth + 14 && x < 1525) {
            x += contentColumnWidth() - referenceContentWidth;
            y += 40;
        }
        return new float[] {ImGui.getMainViewport().getPosX() + x, ImGui.getMainViewport().getPosY() + y};
    }

    private static void renderContentBrowserBenchmark() {
        EditorCampaignProfile.endUI();
        if (!measureContentBrowser) return;
        ImGui.setNextWindowPos(ImGui.getMainViewport().getWorkPosX() + 450, ImGui.getMainViewport().getWorkPosY() + 220);
        ImGui.setNextWindowSize(440, 440);
        ImGui.begin("Content library performance", imgui.flag.ImGuiWindowFlags.NoDocking | imgui.flag.ImGuiWindowFlags.NoResize);
        long started = System.nanoTime();
        invoke("contentBrowser", new Class<?>[0]);
        contentBrowserTimes.add(System.nanoTime() - started);
        ImGui.end();
    }

    private static void frame() {
        EditorCampaignProfile.beginFrame();
        if (finished || stepping) return;
        if (measureWorldLoading) {
            long now = System.nanoTime();
            if (worldLoadingFrame != 0) worldLoadingGap = Math.max(worldLoadingGap, now - worldLoadingFrame);
            worldLoadingFrame = now;
        }
        stepping = true;
        try {
            if (keyRelease >= 0) { ImGui.getIO().addKeyEvent(keyRelease, false); keyRelease = -1; }
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
            io.setMouseDown(1, rightDown);
            io.setMouseClicked(1, rightDown && !previousRightDown);
            io.setMouseReleased(1, !rightDown && previousRightDown);
            io.setMouseDownOwned(1, true);
            io.setMouseDownOwnedUnlessPopupClose(1, true);
            io.setMouseDownDurationPrev(1, io.getMouseDownDuration(1));
            io.setMouseDownDuration(1, rightDown ? previousRightDown ? .5f : 0 : -1);
            io.setMouseClickedCount(1, rightDown && !previousRightDown ? 1 : 0);
            previousRightDown = rightDown;
            previousDown = mouseDown;
        } catch (RuntimeException exception) {
            capture("failure");
            throw exception;
        } finally {
            stepping = false;
        }
    }

    private static void rightClick(Supplier<float[]> point) {
        move(point, false);
        STEPS.add(() -> rightDown = true);
        STEPS.add(() -> rightDown = false);
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
        boolean navigation = List.of("Back", "Forward", "Undo", "Redo").contains(target);
        String[] labels = navigation ? new String[] {"Back", "Forward", "Undo", "Redo"}
                : new String[] {"Workspace", "New", "Save", "Edit", "Preview", "Playtest", "Build pack", "Tools", "Close"};
        if (navigation) {
            x = ImGui.getMainViewport().getWorkPosX() + ImGui.getMainViewport().getWorkSizeX() - 14;
            for (String label : labels) x -= ImGui.calcTextSize(label).x * scale + 24;
            x += 8;
        }
        for (String label : labels) {
            float width = label.equals("Focus pane...") ? 150 : ImGui.calcTextSize(label).x * scale + 16;
            if (label.equals(target)) return new float[] {x + width / 2, ImGui.getMainViewport().getWorkPosY() + 14 + (ImGui.getFontSize() * scale + 12) / 2};
            x += width + 8;
        }
        throw new IllegalArgumentException(target);
    }

    private static float[] port(String node) {
        var card = EditorGraph.cards(workspace, document, "en_us").stream()
                .filter(candidate -> candidate.node().id().equals(node)).findFirst().orElseThrow();
        for (int i = 0; i < card.blocks().size(); i++)
            if (card.blocks().get(i).output() != null) return point(node, card.width(), card.outputY(i));
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
            canvas = new float[] {left + ImGui.getMainViewport().getPosX(), top + ImGui.getMainViewport().getPosY(), right + ImGui.getMainViewport().getPosX(), bottom + ImGui.getMainViewport().getPosY()};
        }
    }

    public static NativeImage renderedImage() {
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

    private static void setField(Object target, String name, Object value) {
        try {
            var member = target.getClass().getDeclaredField(name);
            member.setAccessible(true);
            member.set(target, value);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void set(String name, Object value) {
        try {
            var field = ImGuiAuthoringEditor.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(editor, value);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static Object invoke(String name, Class<?>[] types, Object... arguments) {
        try {
            var method = ImGuiAuthoringEditor.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(editor, arguments);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static Path output() { return Path.of(System.getProperty(OUTPUT)); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
