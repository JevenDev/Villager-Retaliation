package com.jvn.villagerretaliation.client.campaign;

import com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaign;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "villagerretaliation", value = Dist.CLIENT)
public final class CampaignSelectionClientSmoke {
    private static final String OUTPUT = "villagerretaliation.campaignSelectionClientSmoke";
    private static long started;
    private static int step;
    private static int frames;
    private static CreateWorldScreen setup;
    private static CampaignSelectScreen selection;
    private static CampaignSelectScreen alphaSelection;
    private static String previousScreen = "";

    private CampaignSelectionClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (System.getProperty(OUTPUT) == null || step == 6) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (started == 0) started = Util.getMillis();
        require(Util.getMillis() - started < 180_000, "Campaign selection smoke timed out at step " + step);
        String screenName = minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName();
        if (!screenName.equals(previousScreen)) {
            previousScreen = screenName;
            System.out.println("Campaign selection smoke step " + step + ": " + screenName);
        }
        minecraft.setWindowActive(true);
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.screen instanceof com.jvn.villagerretaliation.client.BetaWarningScreen warning) {
            warning.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.active && button.getMessage().getString().equals(
                            Component.translatable("screen.villagerretaliation.beta_warning.acknowledge").getString()))
                    .findFirst().ifPresent(Button::onPress);
            return;
        }
        if (step == 0 && minecraft.screen instanceof TitleScreen) {
            minecraft.options.guiScale().set(3);
            minecraft.resizeDisplay();
            step = 1;
            CreateWorldScreen.openFresh(minecraft, minecraft.screen);
        } else if (step == 1 && minecraft.screen instanceof CreateWorldScreen screen) {
            setup = screen;
            CampaignLoadingClientChecks.start(screen);
            step = 16;
            frames = 0;
        } else if (step == 16 && ++frames >= 5) {
            CampaignLoadingClientChecks.cancel();
            step = 17;
            frames = 0;
        } else if (step == 17 && ++frames >= 5) {
            CampaignLoadingClientChecks.verifyCanceled();
            button(setup, Component.translatable("selectWorld.create").getString()).onPress();
            require(minecraft.screen instanceof CampaignLoadingScreen, "Create prepares campaigns without blocking the menu render thread");
            step = 2;
            frames = 0;
        } else if (step == 2 && minecraft.screen instanceof CampaignSelectScreen screen && ++frames >= 15) {
            require(campaignButton(screen, "default").active, "Default VR is available");
            require(!campaignButton(screen, "vr").active, "Campaign VR is locked");
            require(!button(screen, Component.translatable("selectWorld.create").getString()).active, "Creation requires a selection");
            Screenshot.grab(output().toFile(), "campaign-select.png", minecraft.getMainRenderTarget(), ignored -> { });
            selection = screen;
            CampaignDiskReloadChecks.start(screen);
            step = 11;
            frames = 0;
        } else if (step == 11 && minecraft.screen instanceof CampaignSelectScreen screen && ++frames >= 5) {
            CampaignDiskReloadChecks.verify(screen, false);
            Screenshot.grab(output().toFile(), "campaign-disk-reload-before.png", minecraft.getMainRenderTarget(), ignored -> { });
            CampaignDiskReloadChecks.edit(screen);
            step = 12;
            frames = 0;
        } else if (step == 12 && minecraft.screen instanceof CampaignSelectScreen screen && ++frames >= 5) {
            CampaignDiskReloadChecks.verify(screen, true);
            Screenshot.grab(output().toFile(), "campaign-disk-reload-after.png", minecraft.getMainRenderTarget(), ignored -> { });
            CampaignDiskReloadChecks.restore(screen);
            minecraft.setScreen(CampaignCardClientChecks.fixture(screen));
            step = 7;
            frames = 0;
        } else if (step == 7 && minecraft.screen instanceof CampaignSelectScreen screen && ++frames >= 10) {
            require(CampaignCardClientChecks.cards(screen) == 3, "At most three campaign cards are visible");
            CampaignCardClientChecks.authors(screen);
            CampaignCardClientChecks.comingSoon(screen);
            Button first = button(screen, "Campaign 1");
            require(first.getWidth() == 128 && first.getHeight() == 192, "Collapsed frame uses its native 128x192 GUI size");
            Button last = button(screen, "Campaign 3");
            require(Math.abs(first.getX() + last.getRight() - screen.width) <= 2, "Cards distribute around the screen center");
            Screenshot.grab(output().toFile(), "campaign-three-cards.png", minecraft.getMainRenderTarget(), ignored -> { });
            minecraft.setScreen(CampaignAnimationClientChecks.start(screen));
            step = 13;
            frames = 0;
        } else if (step == 13 && ++frames >= 20) {
            CampaignAnimationClientChecks.hover();
            step = 14;
            frames = 0;
        } else if (step == 14 && ++frames >= 20) {
            CampaignAnimationClientChecks.verifyHover();
            Screenshot.grab(output().toFile(), "campaign-hover.png", minecraft.getMainRenderTarget(), ignored -> { });
            var screen = CampaignAnimationClientChecks.finish();
            minecraft.setScreen(screen);
            Button expand = button(screen, "Expand Campaign 1");
            require(screen.mouseClicked(expand.getX() + 1, expand.getY() + 1, 0), "Caret accepts clicks outside its visible pixels");
            require(CampaignCardClientChecks.cards(screen) == 1, "Expansion opens only the chosen card");
            step = 15;
            frames = 0;
        } else if (step == 15 && minecraft.screen instanceof CampaignSelectScreen && ++frames >= 1) {
            Screenshot.grab(output().toFile(), "campaign-expansion-transition.png", minecraft.getMainRenderTarget(), ignored -> { });
            step = 8;
            frames = 0;
        } else if (step == 8 && minecraft.screen instanceof CampaignSelectScreen screen && ++frames >= 15) {
            Screenshot.grab(output().toFile(), "campaign-expanded.png", minecraft.getMainRenderTarget(), ignored -> { });
            Button card = button(screen, "Campaign 1");
            double scale = card.getHeight() / 192.0;
            require(screen.mouseScrolled(card.getX() + 150 * scale, card.getY() + 20 * scale, 0, -3), "Description accepts wheel scrolling");
            require(CampaignCardClientChecks.scroll(screen) > 0, "Long descriptions scroll");
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
            require(CampaignCardClientChecks.scroll(screen) > 54, "Descriptions support keyboard scrolling");
            CampaignAnimationClientChecks.scrolling(screen, false);
            alphaSelection = screen;
            minecraft.setScreen(CampaignCardClientChecks.alphaScreen(screen));
            step = 10;
            frames = 0;
        } else if (step == 10 && ++frames >= 5) {
            CampaignCardClientChecks.verifyAlpha(alphaSelection);
            Screenshot.grab(output().toFile(), "campaign-alpha.png", minecraft.getMainRenderTarget(), ignored -> { });
            minecraft.setScreen(alphaSelection);
            step = 9;
            frames = 0;
        } else if (step == 9 && minecraft.screen instanceof CampaignSelectScreen screen && ++frames >= 10) {
            CampaignAnimationClientChecks.scrolling(screen, true);
            Screenshot.grab(output().toFile(), "campaign-description-scrolled.png", minecraft.getMainRenderTarget(), ignored -> { });
            CampaignCardClientChecks.reload(screen);
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(CampaignCardClientChecks.cards(screen) == 3, "Escape collapses the expanded card");
            button(screen, "Expand Campaign 2").onPress();
            require(!button(screen, Component.translatable("selectWorld.create").getString()).active, "Locked cards can expand without enabling creation");
            button(screen, "Collapse Campaign 2").onPress();
            button(screen, ">").onPress();
            require(CampaignCardClientChecks.cards(screen) == 3 && button(screen, "Campaign 4").active, "Second page contains the next three cards");
            button(screen, ">").onPress();
            require(CampaignCardClientChecks.cards(screen) == 1, "Final page contains the remaining card");
            Button single = button(screen, "Campaign 7");
            require(Math.abs(single.getX() + single.getRight() - screen.width) <= 2, "Single card stays centered");
            button(screen, "<").onPress();
            require(button(screen, "Campaign 4").active, "Previous page restores the preceding cards");
            screen.onClose();
            require(minecraft.screen == selection, "Fixture returns to selection");
            selection.onClose();
            require(minecraft.screen == setup, "Back preserves the world setup screen");
            button(setup, Component.translatable("selectWorld.create").getString()).onPress();
            step = 3;
            frames = 0;
        } else if (step == 3 && minecraft.screen instanceof CampaignSelectScreen screen && ++frames >= 5) {
            campaignButton(screen, "default").onPress();
            CampaignAddonClientChecks.start(screen);
            step = 18;
            frames = 0;
        } else if (step == 18 && minecraft.screen instanceof CampaignAddonScreen screen && ++frames >= 10) {
            Screenshot.grab(output().toFile(), "campaign-addons-available.png", minecraft.getMainRenderTarget(), ignored -> { });
            CampaignAddonClientChecks.exercise(screen);
            step = 19;
            frames = 0;
        } else if (step == 19 && minecraft.screen instanceof CampaignAddonScreen screen && ++frames >= 10) {
            Screenshot.grab(output().toFile(), "campaign-addons-enabled.png", minecraft.getMainRenderTarget(), ignored -> { });
            CampaignAddonClientChecks.finish(screen);
            step = 20;
            frames = 0;
        } else if (step == 20 && minecraft.screen instanceof CampaignSelectScreen screen && ++frames >= 5) {
            Button create = button(screen, Component.translatable("selectWorld.create").getString());
            require(create.active, "Selecting an available campaign enables creation");
            create.onPress();
            step = 4;
        } else if (step == 4 && minecraft.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen screen) {
            screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow().onPress();
        } else if (step == 4 && minecraft.player != null && minecraft.getSingleplayerServer() != null) {
            var server = minecraft.getSingleplayerServer();
            var selection = CampaignWorldModeSavedData.readSelection(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT), server.registryAccess());
            require(WorldCampaign.DEFAULT.equals(selection.worldCampaign()), "Created world persists Default VR");
            require(selection.includeDefaultContent(), "Created world retains default content");
            CampaignAddonClientChecks.verifyServer(server);
            CampaignAddonClientChecks.disconnect(server);
            step = 21;
            frames = 0;
        } else if (step == 21 && minecraft.screen instanceof TitleScreen && ++frames >= 10) {
            CampaignAddonClientChecks.openExisting();
            step = 22;
            frames = 0;
        } else if (step == 22 && minecraft.screen instanceof CampaignAddonScreen screen && ++frames >= 10) {
            Screenshot.grab(output().toFile(), "campaign-addons-existing-world.png", minecraft.getMainRenderTarget(), ignored -> { });
            CampaignAddonClientChecks.existing(screen);
            Files.writeString(output().resolve("result.txt"), "Campaign selection client smoke passed: centered cards, responsive background loading and cancellation, continuous panorama handoff, animated paging and expansion, cursor tilt, cover-only tooltips, parallax hover slowdown, moving hit areas, accessible carets, Markdown and Minecraft color codes, transparent PNG/GIF media, F5 disk edits to campaign titles, authors, PNG covers, Markdown, and GIFs, selected datapack priority, campaign catalog reload, failed reload recovery, scrolling, locked state, Back, creation, addon icons, vanilla controls, drag transfers and ordering, compatibility, search, dependencies, conflicts, cancel, runtime quests, existing-world additions, and permanent saved selection.\n");
            step = 6;
            minecraft.stop();
        }
    }

    private static Button campaignButton(CampaignSelectScreen screen, String path) throws ReflectiveOperationException {
        var field = CampaignSelectScreen.class.getDeclaredField("campaigns");
        field.setAccessible(true);
        var id = ResourceLocation.fromNamespaceAndPath("villagerretaliation", path);
        for (var value : (java.util.List<?>) field.get(screen)) {
            var campaign = (WorldCampaign) value;
            if (campaign.id().equals(id)) return button(screen, campaign.name());
        }
        throw new IllegalStateException("Missing campaign: " + id);
    }

    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing button: " + label));
    }

    private static Path output() { return Path.of(System.getProperty(OUTPUT)); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
