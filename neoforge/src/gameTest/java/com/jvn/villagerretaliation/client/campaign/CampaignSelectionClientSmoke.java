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
            button(screen, Component.translatable("selectWorld.create").getString()).onPress();
            require(minecraft.screen instanceof CampaignSelectScreen, "Create opens campaign selection");
            step = 2;
        } else if (step == 2 && minecraft.screen instanceof CampaignSelectScreen screen && ++frames >= 15) {
            require(button(screen, "Default VR").active, "Default VR is available");
            require(!button(screen, "Campaign VR").active, "Campaign VR is locked");
            require(!button(screen, Component.translatable("selectWorld.create").getString()).active, "Creation requires a selection");
            Screenshot.grab(output().toFile(), "campaign-select.png", minecraft.getMainRenderTarget(), ignored -> { });
            screen.onClose();
            require(minecraft.screen == setup, "Back preserves the world setup screen");
            button(setup, Component.translatable("selectWorld.create").getString()).onPress();
            step = 3;
            frames = 0;
        } else if (step == 3 && minecraft.screen instanceof CampaignSelectScreen screen && ++frames >= 5) {
            button(screen, "Default VR").onPress();
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
            Files.writeString(output().resolve("result.txt"), "Campaign selection client smoke passed: cards, locked state, Back, creation, and permanent saved selection.\n");
            step = 6;
            minecraft.stop();
        }
    }

    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing button: " + label));
    }

    private static Path output() { return Path.of(System.getProperty(OUTPUT)); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
