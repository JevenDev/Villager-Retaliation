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
    private static final String GREETING = "Welcome, traveler! Let's plan our next trip.";
    private static final String FOLLOW_UP = "Meet me by the village gate tomorrow.";
    private static final ArrayDeque<Runnable> STEPS = new ArrayDeque<>();
    private static Object editor;
    private static AuthoringWorkspace workspace;
    private static AuthoringDocument document;
    private static long started, resumeAt;
    private static int frames;
    private static boolean finished, installed, mouseDown, previousDown;
    private static float mouseX = -100, mouseY = -100, previousX = -100, previousY = -100, downX, downY;
    private static float[] canvas;
    private static float[] originalPosition;

    private AuthoringEditorClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || finished) return;
        var minecraft = Minecraft.getInstance();
        if (started == 0) started = Util.getMillis();
        require(Util.getMillis() - started < 180_000, "Editor client test timed out after " + frames + " frames.");
        minecraft.setWindowActive(true);
        minecraft.options.pauseOnLostFocus = false;
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
            capture("07-preview");
        });
        click(() -> toolbar("Check & export"));
        STEPS.add(() -> {
            require((int) field(editor, "view") == 2, "Check toolbar control switches the rendered view.");
            capture("08-validation");
        });
        buildSetupSteps();
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
        editLine("start", GREETING);
        click(() -> screenPoint(320, 181));
        STEPS.add(() -> {
            require(DialogueGraph.nodes(document).size() == 2, "Add node creates a follow-up.");
            locateCanvas();
        });
        editLine("node_2", FOLLOW_UP);
        click(() -> point("start", 80, 18));
        click(() -> screenPoint(430, 181));
        replaceText(() -> screenPoint(450, 289), () -> "Where should we meet?");
        click(() -> screenPoint(450, 357));
        click(() -> screenPoint(320, 455));
        click(() -> screenPoint(350, 397));
        STEPS.add(() -> {
            require(DialogueGraph.edges(document).size() == 1, "Add connection creates a response.");
            require(DialogueGraph.edges(document).getFirst().to().equals("node_2"), "The response leads to the chosen node.");
            require(document.json().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("responses")
                    .get(0).getAsJsonObject().get("label").getAsString().equals("Where should we meet?"), "The response keeps the typed wording.");
        });
        click(() -> screenPoint(655, 181));
        click(() -> toolbar("Save"));
        STEPS.add(() -> {
            require(!document.dirty(), "Save persists the authored dialogue.");
            capture("11-authored-flow");
        });
        click(() -> toolbar("Preview"));
        click(() -> screenPoint(550, 209));
        STEPS.add(() -> require(field(editor, "target") instanceof net.minecraft.world.entity.npc.Villager,
                "Nearest villager selects a real preview target."));
        click(() -> screenPoint(350, 317));
        pause(1000);
        STEPS.add(() -> require(AuthoringDialoguePreview.active(), "Play from entry opens the native conversation screen."));
        choosePreview("Talk");
        pause(2500);
        STEPS.add(() -> {
            require(field(Minecraft.getInstance().screen, "villagerDialogueText").equals(GREETING), "Preview displays the edited greeting.");
            capture("12-preview-greeting");
        });
        choosePreview("Where should we meet?");
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
                require(json.equals(document.json()), "Exported JSON exactly matches the edited document.");
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
            try {
                Files.writeString(output().resolve("result.txt"),
                        "Authoring editor client smoke passed\nGraph interactions, automatic layout, onboarding, and full setup through native controls.\n"
                        + "Created an empty workspace, typed a dialogue ID, wrote and revised text, added a node and response, saved, played the entry and response, returned to the editor, checked drafts, exported, and reopened the saved draft.\n");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            finished = true;
            Minecraft.getInstance().stop();
        });
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

    private static void choosePreview(String label) {
        STEPS.add(() -> {
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
        if (finished) return;
        try {
            if (++frames % 12 == 0 && Util.getMillis() >= resumeAt && !STEPS.isEmpty()) STEPS.removeFirst().run();
            var io = ImGui.getIO();
            io.setMouseWheel(0);
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
            io.setMouseClickedCount(0, mouseDown && !previousDown ? 1 : 0);
            io.setMouseDragMaxDistanceSqr(0, mouseDown ? (mouseX - downX) * (mouseX - downX) + (mouseY - downY) * (mouseY - downY) : 0);
            imgui.internal.ImGui.updateHoveredWindowAndCaptureFlags(mouseX, mouseY);
            previousX = mouseX;
            previousY = mouseY;
            previousDown = mouseDown;
        } catch (RuntimeException exception) {
            capture("failure");
            throw exception;
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
            mouseDown = down;
        });
    }

    private static float[] toolbar(String target) {
        float x = ImGui.getMainViewport().getWorkPosX() + 14;
        for (String label : new String[] {"Workspace", "New", "Save", "Edit", "Preview", "Check & export",
                (int) field(editor, "guideStep") < 0 ? "Quick guide" : "Hide guide", "Reset layout", "Close"}) {
            float width = ImGui.calcTextSize(label).x + 16;
            if (label.equals(target)) return new float[] {x + width / 2, ImGui.getMainViewport().getWorkPosY() + 14 + (ImGui.getFontSize() + 12) / 2};
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
            var field = (target == null ? ImGuiAuthoringEditor.class : target.getClass()).getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
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
