package com.jvn.villagerretaliation.client.campaign;

import com.jvn.villagerretaliation.campaign.selection.WorldCampaign;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

final class CampaignAddonClientChecks {
    private static CampaignSelectScreen parent;
    private static final List<ResourceLocation> EXPECTED = List.of(id("specific"), id("general"));

    static void start(CampaignSelectScreen screen) throws Exception {
        parent = screen;
        Path source = Path.of(System.getProperty(CampaignResources.SOURCE_RESOURCES_PROPERTY));
        for (String addon : List.of("general", "specific", "blocked", "dependent", "conflict", "spare_1", "spare_2", "spare_3", "spare_4")) {
            copy(source, addon + "/addon.json");
        }
        for (String addon : List.of("general", "specific")) {
            copy(source, addon + "/quests/addon/quest/quest.json");
            copy(source, addon + "/quests/addon/quest/locales/en_us.json");
        }
        screen.keyPressed(GLFW.GLFW_KEY_F5, 0, 0);
        button(screen, "Addons (0)").onPress();
        require(Minecraft.getInstance().screen instanceof CampaignAddonScreen, "Addon configuration opens from the selected campaign");
    }

    static void exercise(CampaignAddonScreen screen) throws Exception {
        var search = (EditBox) field(screen, "search");
        var icons = (java.util.Map<?, ?>) field(screen, "icons");
        require(((CampaignMedia) icons.get(id("general"))).available(), "Addon-specific pack icons load");
        require(((CampaignMedia) icons.get(id("blocked"))).available(), "Vanilla fallback pack icons load");
        search.setValue("Incompatible");
        require(field(button(screen, "Incompatible expansion"), "issue") != null, "Excluded campaigns explain why transfer is blocked");
        drag(screen, button(screen, "Incompatible expansion"), right(screen) + 60, 80);
        require(((List<?>) field(screen, "enabled")).isEmpty(), "Dragging cannot bypass campaign compatibility");
        search.setValue("Dependent");
        require(field(button(screen, "Dependent stories"), "issue") != null, "Missing dependencies block transfer");
        search.setValue("General");
        drag(screen, button(screen, "General stories"), right(screen) + 60, 80);
        search.setValue("Conflicting");
        require(field(button(screen, "Conflicting stories"), "issue") != null, "One-sided addon conflicts are enforced");
        search.setValue("Default");
        clickIcon(screen, button(screen, "Default expansion"));
        search.setValue("");
        drag(screen, button(screen, "General stories"), right(screen) + 60, 77);
        require(field(screen, "enabled").equals(EXPECTED), "Dragging reorders addon priority");
        search.setValue("Dependent");
        drag(screen, button(screen, "Dependent stories"), right(screen) + 60, 77);
        var before = List.copyOf((List<?>) field(screen, "enabled"));
        int bottom = 76 + (int) field(screen, "visibleRows") * 48;
        drag(screen, button(screen, "Dependent stories"), right(screen) + 60, bottom - 1);
        require(field(screen, "enabled").equals(before), "Dragging cannot invert dependency priority");
        drag(screen, button(screen, "Dependent stories"), (int) field(screen, "left") + 60, 80);
        require(field(screen, "enabled").equals(EXPECTED), "Dragging to Available disables an addon");
        search.setValue("");
        int left = (int) field(screen, "left");
        require(screen.mouseScrolled(left + 10, 90, 0, -1), "Available addons accept scrolling");
        require((int) field(screen, "availableScroll") > 0, "Long available lists scroll");
        screen.mouseScrolled(left + 10, 90, 0, 1);
        button(screen, "Done").onPress();
        require(Minecraft.getInstance().screen == parent, "Applying addons returns to campaign selection");
        button(parent, "Addons (2)").onPress();
    }

    static void finish(CampaignAddonScreen screen) throws Exception {
        require(field(screen, "enabled").equals(EXPECTED), "Reopening retains addon order");
        clickIcon(screen, button(screen, "General stories"));
        screen.onClose();
        button(parent, "Addons (2)").onPress();
        var reopened = (CampaignAddonScreen) Minecraft.getInstance().screen;
        require(field(reopened, "enabled").equals(EXPECTED), "Cancel discards addon edits");
        button(reopened, "Done").onPress();
        require(button(parent, "Create New World").active, "A valid addon selection can create a world");
    }

    private static int right(CampaignAddonScreen screen) throws Exception {
        return (int) field(screen, "left") + (int) field(screen, "columnWidth") + 12;
    }

    private static void clickIcon(CampaignAddonScreen screen, Button row) {
        screen.mouseClicked(row.getX() + 12, row.getY() + 14, 0);
        screen.mouseReleased(row.getX() + 12, row.getY() + 14, 0);
    }

    private static void drag(CampaignAddonScreen screen, Button row, double x, double y) {
        double startX = row.getX() + 54;
        double startY = row.getY() + 18;
        screen.mouseClicked(startX, startY, 0);
        screen.mouseDragged(x, y, 0, x - startX, y - startY);
        screen.mouseReleased(x, y, 0);
    }

    static void verifyServer(net.minecraft.server.MinecraftServer server) throws Exception {
        var selection = com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection.get(server);
        require(WorldCampaign.DEFAULT.equals(selection.id()) && selection.addons().equals(EXPECTED), "Server startup retains the saved ordered addon selection");
        var stacks = com.jvn.villagerretaliation.util.DatapackResourceLoader.jsonResourceStacks(server, "quests");
        var quest = stacks.stream().filter(stack -> stack.location().toString().equals("campaign_addon_fixture:quests/addon/quest/locales/en_us.json")).findFirst().orElseThrow();
        try (var reader = quest.layersLowToHigh().getLast().resource().openAsReader()) {
            require(com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().get("campaign_addon_fixture.quest.example.title").getAsString().equals("General addon quest"),
                    "Actual server resources respect the reordered addon priority");
        }
        require(com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.current(server).quests().containsKey(ResourceLocation.parse("campaign_addon_fixture:quest")),
                "The server materializes selected addon quests: " + com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.loadReport(server).entries());
    }

    private static Path existingWorld;
    private static net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess access;
    private static net.minecraft.client.gui.screens.worldselection.EditWorldScreen editor;

    static void disconnect(net.minecraft.server.MinecraftServer server) {
        existingWorld = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
        Minecraft.getInstance().level.disconnect();
        Minecraft.getInstance().disconnect(new net.minecraft.client.gui.screens.TitleScreen());
    }

    static void openExisting() throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        access = minecraft.getLevelSource().validateAndCreateAccess(existingWorld.getFileName().toString());
        editor = net.minecraft.client.gui.screens.worldselection.EditWorldScreen.create(minecraft, access, changed -> {
            try { access.close(); }
            catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
        });
        minecraft.setScreen(editor);
        button(editor, "Campaign addons").onPress();
    }

    static void existing(CampaignAddonScreen screen) throws Exception {
        require(field(screen, "enabled").equals(EXPECTED), "World editing reads the saved addon stack");
        var search = (EditBox) field(screen, "search");
        search.setValue("Dependent");
        require(((String) field(button(screen, "Dependent stories"), "issue")).toLowerCase(java.util.Locale.ROOT).contains("new world"), "New-world-only addons explain their late-install restriction");
        drag(screen, button(screen, "Dependent stories"), right(screen) + 60, 77);
        require(field(screen, "enabled").equals(EXPECTED), "Drag cannot bypass new-world-only restrictions");
        clickIcon(screen, button(screen, "General stories"));
        require(field(screen, "enabled").equals(EXPECTED), "Saved addons cannot be disabled");
        search.setValue("Extra stories 1");
        clickIcon(screen, button(screen, "Extra stories 1"));
        button(screen, "Done").onPress();
        require(Minecraft.getInstance().screen == editor, "Late addon changes return to world editing");
        var saved = com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData.readSelection(existingWorld, null);
        require(saved.addons().equals(List.of(id("specific"), id("general"), id("spare_1"))) && saved.worldCampaign().equals(WorldCampaign.DEFAULT),
                "A permitted late addon saves without changing the world campaign or existing addon order");
        editor.onClose();
    }

    private static void copy(Path source, String relative) throws Exception {
        String resource = "data/campaign_addon_fixture/campaign_addons/" + relative;
        Path target = source.resolve(resource);
        Files.createDirectories(target.getParent());
        try (var stream = CampaignAddonClientChecks.class.getResourceAsStream("/" + resource)) {
            Files.copy(java.util.Objects.requireNonNull(stream), target);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Button button(Screen screen, String text) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(text)).findFirst().orElseThrow(() -> new IllegalStateException("Missing button " + text));
    }

    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("campaign_addon_fixture", name); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
