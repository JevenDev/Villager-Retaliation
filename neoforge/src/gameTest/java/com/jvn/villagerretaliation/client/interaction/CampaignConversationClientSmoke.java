package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.campaign.CampaignConversationClientFixture;
import com.jvn.villagerretaliation.config.DialogueTextSpeed;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class CampaignConversationClientSmoke {
    private static final String OUTPUT = "villagerretaliation.campaignConversationClientSmoke";
    private static CompletableFuture<CampaignConversationClientFixture> preparing;
    private static CompletableFuture<Void> verification;
    private static CampaignConversationClientFixture fixture;
    private static long started;
    private static int phase;
    private static int ticks;
    private static int die;
    private static java.util.UUID confirmation;

    private CampaignConversationClientSmoke() { }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || phase == 12) return;
        var minecraft = Minecraft.getInstance();
        minecraft.setWindowActive(true);
        minecraft.options.pauseOnLostFocus = false;
        if (started == 0) started = Util.getMillis();
        require(Util.getMillis() - started < 180000, "conversation client smoke timed out at phase " + phase);
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        var server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        if (phase == 0 && minecraft.screen == null) {
            minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            minecraft.options.guiScale().set(2); minecraft.resizeDisplay();
            VillagerRetaliationConfig.DIALOGUE_TEXT_SPEED.set(DialogueTextSpeed.MEDIUM);
            preparing = server.submit(() -> {
                try { return CampaignConversationClientFixture.prepare(server.getPlayerList().getPlayer(playerId)); }
                catch (IOException exception) { throw new UncheckedIOException(exception); }
            });
            phase = 1;
        } else if (phase == 1 && preparing.isDone()) {
            fixture = preparing.join();
            if (!(minecraft.level.getEntity(fixture.entity()) instanceof Villager)) return;
            server.execute(() -> require(ConversationPresentationService.tryOpen(server.getPlayerList().getPlayer(playerId),
                    (Villager) server.overworld().getEntity(fixture.character())), "saved conversation opens through normal interaction"));
            phase = 2;
        } else if (minecraft.screen instanceof VillagerInteractionScreen screen) {
            var controller = controller(screen);
            var session = controller.session();
            if (session == null) return;
            if (phase == 2 && confirmation == null && session.phase() == ConversationSession.Phase.CONFIRMATION && ++ticks >= 20) {
                require(checkTextIsImmediate(screen), "shared consequences are immediately readable");
                confirmation = session.confirmation();
                capture("quest-impact-confirmation"); choose(screen, localized("leave")); ticks = 0; phase = 13;
            } else if (phase == 14 && verification.isDone() && session.phase() == ConversationSession.Phase.CONFIRMATION && ++ticks >= 20) {
                verification.join(); verification = null;
                require(confirmation.equals(session.confirmation()) && session.canTakeControl(), "reopening retains the pending approval");
                capture("reopened-confirmation"); choose(screen, localized("take_control")); ticks = 0; phase = 15;
            } else if (phase == 15 && session.canControl() && ++ticks >= 10) {
                if (chooseIfPresent(screen, localized("more_consequences"))) { ticks = 0; return; }
                capture("quest-impact-details"); choose(screen, localized("confirm")); ticks = 0; phase = 2;
            } else if (phase == 6 && session.phase() == ConversationSession.Phase.CONFIRMATION && ++ticks >= 20) {
                require(checkTextIsImmediate(screen), "irreversible decision is immediately readable");
                capture("outcome-confirmation");
                if (chooseIfPresent(screen, localized("more_consequences"))) { ticks = 0; return; }
                capture("outcome-details"); choose(screen, localized("confirm")); ticks = 0;
            } else if (phase == 2 && session.phase() == ConversationSession.Phase.EXCHANGES && ++ticks >= 35) {
                require(session.line().speaker().equals(fixture.character()), "mentor portrait belongs to the saved speaker");
                capture("mentor"); choose(screen, localized("continue")); ticks = 0; phase = 3;
            } else if (phase == 3 && session.line().speaker() != null && session.line().speaker().equals(playerId) && ++ticks >= 35) {
                capture("player-speaker"); choose(screen, localized("continue")); ticks = 0; phase = 4;
            } else if (phase == 4 && session.line().speaker() == null && ++ticks >= 35) {
                capture("narrator"); choose(screen, localized("continue")); ticks = 0; phase = 5;
            } else if (phase == 5 && session.phase() == ConversationSession.Phase.RESPONSES && ++ticks >= 10) {
                capture("responses"); choose(screen, session.responses().getFirst().label()); ticks = 0; phase = 6;
            } else if (phase == 6 && session.phase() == ConversationSession.Phase.CHECK && ++ticks >= 20) {
                require(!controller.dieAnimating() && !session.check().resolved(), "screen waits for a saved server roll");
                require(checkTextIsImmediate(screen), "difficulty is readable without waiting for dialogue animation");
                capture("difficulty"); choose(screen, localized("roll", session.check().difficulty())); ticks = 0; phase = 7;
            } else if (phase == 7 && session.phase() == ConversationSession.Phase.ROLLED && controller.dieProgress() >= 0.25F) {
                require(controller.dieAnimating(), "acknowledged roll animates");
                capture("die-rolling");
                die = session.check().die();
                choose(screen, localized("skip_roll"));
                require(!controller.dieAnimating(), "skip finishes presentation immediately");
                ticks = 0; phase = 8;
            } else if (phase == 8 && ++ticks >= 30) {
                require(session.check().die() == die, "skip preserves the saved result");
                require(checkTextIsImmediate(screen), "skip reveals the complete saved result immediately");
                capture("die-result"); choose(screen, localized("leave")); ticks = 0; phase = 9;
            } else if (phase == 10 && verification.isDone() && session.check() != null && session.check().resolved() && ++ticks >= 25) {
                verification.join();
                require(session.check().die() == die && !controller.dieAnimating(), "reopening shows the same result without replaying animation");
                require(session.canTakeControl(), "closed controller resumes explicitly");
                capture("reopened-check");
                minecraft.setScreen(new DieFacesScreen()); ticks = 0; phase = 11;
            }
        } else if (phase == 13 && minecraft.screen == null) {
            verification = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var saved = ConversationService.view(player, fixture.session()).session();
                require(saved != null && saved.controller() == null && saved.confirmation().id().equals(confirmation), "pending acceptance persists after closing");
                require(player.totalExperience == 0, "unconfirmed quest applies no preceding effects");
                require(ConversationPresentationService.tryOpen(player, (Villager) server.overworld().getEntity(fixture.character())), "pending confirmation reopens");
            });
            phase = 14;
        } else if (phase == 9 && minecraft.screen == null) {
            if (verification == null) verification = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var saved = ConversationService.view(player, fixture.session()).session();
                require(saved != null && saved.controller() == null && saved.checks().get("convince").die() == die, "server retained the roll and released control");
                require(player.totalExperience == 20, "confirmed quest and outcome effects each apply once");
                var campaign = com.jvn.villagerretaliation.campaign.CampaignSavedData.get(player.serverLevel()).instances()
                        .get(net.minecraft.resources.ResourceLocation.parse("campaign_fixture:characters"));
                require(campaign.outcomes().get("decision").outcome().equals("help"), "confirmed decision commits before the check");
                require(ConversationPresentationService.tryOpen(player, (Villager) server.overworld().getEntity(fixture.character())), "reopening saved check succeeds");
            });
            phase = 10;
        } else if (phase == 11 && minecraft.screen instanceof DieFacesScreen && ++ticks >= 10) {
            capture("all-die-faces");
            try { Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Campaign conversation client smoke passed\n"); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            phase = 12;
            minecraft.stop();
        }
    }

    private static boolean checkTextIsImmediate(VillagerInteractionScreen screen) {
        try {
            var field = VillagerInteractionScreen.class.getDeclaredField("dialogueTextAnimationSkipped"); field.setAccessible(true);
            return field.getBoolean(screen);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Check text presentation is unavailable", exception); }
    }

    static CampaignConversationScreenController controller(VillagerInteractionScreen screen) {
        try {
            var field = VillagerInteractionScreen.class.getDeclaredField("campaignConversation"); field.setAccessible(true);
            return (CampaignConversationScreenController) field.get(screen);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Conversation screen state is unavailable", exception); }
    }

    static void choose(VillagerInteractionScreen screen, String label) {
        require(chooseIfPresent(screen, label), "Conversation option is unavailable: " + label);
    }

    static boolean chooseIfPresent(VillagerInteractionScreen screen, String label) {
        try {
            var field = VillagerInteractionScreen.class.getDeclaredField("options"); field.setAccessible(true);
            for (var option : (List<?>) field.get(screen)) {
                var name = option.getClass().getDeclaredMethod("label"); name.setAccessible(true);
                if (!label.equals(name.invoke(option))) continue;
                var locked = option.getClass().getDeclaredMethod("locked"); locked.setAccessible(true);
                require(!(Boolean) locked.invoke(option), "requested option is enabled: " + label);
                var action = option.getClass().getDeclaredMethod("action"); action.setAccessible(true);
                ((Runnable) action.invoke(option)).run();
                return true;
            }
            return false;
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Conversation option could not be invoked", exception); }
    }

    static String localized(String key, Object... arguments) { return I18n.get("villagerretaliation.gui.conversation." + key, arguments); }

    private static void capture(String name) {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty(OUTPUT), name + ".png"));
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static final class DieFacesScreen extends Screen {
        DieFacesScreen() { super(Component.literal("Campaign die faces")); }
        @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF28251F);
            for (int value = 1; value <= 20; value++) {
                float x = width / 2.0F + ((value - 1) % 5 - 2) * 75;
                float y = height / 2.0F + ((value - 1) / 5 - 1.5F) * 75;
                require(CampaignDieRenderer.render(graphics, font, x, y, 28, value, 1), "die renderer is available");
            }
            require(!CampaignDieRenderer.render(graphics, font, 16, height - 12, 10, 17, 1, null), "missing shader selects readable fallback");
            CampaignDieRenderer.renderFallback(graphics, font, 16, height - 12, 17);
            graphics.drawString(font, localized("result", 17, 10, localized("success")), 35, height - 16, 0xFFFFFFFF, false);
        }
    }
}
