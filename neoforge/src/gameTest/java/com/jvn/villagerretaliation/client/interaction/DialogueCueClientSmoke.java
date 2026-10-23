package com.jvn.villagerretaliation.client.interaction;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.DialogueTextSpeed;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import com.jvn.villagerretaliation.dialogue.DialoguePlayback;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class DialogueCueClientSmoke {
    private static final String OUTPUT = "villagerretaliation.dialogueCueClientSmoke";
    private static final String PERFORMANCE = "<look=player><agree>Yes, I heard it too.<sound:entity.villager.yes volume=0.5 pitch=1.1><particle:smoke amount:2><pause=650>"
            + "<look=left> Over there...<pause=700><look=right> or there?<pause=650>"
            + "<look=player><disagree> No. Stay here.<pause=600><look=up> Above us?<pause=650>"
            + "<look=down> Nothing below.<pause=600><reset><disagree>Wait.<pause=100>"
            + "<agree> Maybe.<pause=100><reset><pause=300><speed=0.5> Let's think.<particle:minecraft:heart amount:3>";
    private static CompletableFuture<Integer> preparing;
    private static int phase;
    private static int entityId;
    private static int frame;
    private static long started;
    private static long shownAt;
    private static long lastCapture;
    private static long finishedAt;

    private DialogueCueClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || phase == 99) return;
        var minecraft = Minecraft.getInstance();
        minecraft.setWindowActive(true);
        minecraft.options.pauseOnLostFocus = false;
        long now = Util.getMillis();
        if (started == 0) started = now;
        require(now - started < 180_000, "dialogue cue client smoke timed out at phase " + phase);
        if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) return;
        var server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        if (phase == 0 && minecraft.screen == null) {
            minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            minecraft.options.fov().set(70);
            VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.set(true);
            VillagerRetaliationConfig.ENABLE_NORMAL_DIALOGUE_CAMERA_FOCUS.set(true);
            VillagerRetaliationConfig.DIALOGUE_TEXT_SPEED.set(DialogueTextSpeed.MEDIUM);
            preparing = server.submit(() -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayer(playerId);
                for (var position : BlockPos.betweenClosed(5, 99, 5, 25, 99, 25))
                    level.setBlockAndUpdate(position, Blocks.GRASS_BLOCK.defaultBlockState());
                level.setDayTime(6000);
                level.setWeatherParameters(6000, 0, false, false);
                player.teleportTo(15.5, 100, 15.5);
                var villager = EntityType.VILLAGER.create(level);
                villager.setNoAi(true);
                villager.setInvulnerable(true);
                villager.setCustomName(Component.literal("Rowan"));
                villager.setPos(15.5, 100, 19.5);
                level.addFreshEntity(villager);
                return villager.getId();
            });
            phase = 1;
        } else if (phase == 1 && preparing.isDone()) {
            entityId = preparing.join();
            if (!(minecraft.level.getEntity(entityId) instanceof Villager)
                    || minecraft.player.position().distanceToSqr(new net.minecraft.world.phys.Vec3(15.5, 100, 15.5)) > 0.01) return;
            preparing = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var villager = (Villager) server.overworld().getEntity(entityId);
                require(VillagerConversationService.start(player, villager), "native conversation opens");
                VillagerInteractionScreenOpener.openNormal(player, villager, false);
                return entityId;
            });
            phase = 2;
        } else if (phase == 2 && preparing.isDone() && minecraft.screen instanceof VillagerInteractionScreen screen) {
            preparing.join();
            screen.acceptDialogueExchanges(List.of(speech("<look=left><pause=1200>Hello.")));
            shownAt = now;
            phase = 3;
        } else if (phase == 3 && minecraft.screen instanceof VillagerInteractionScreen screen && now - shownAt >= 400) {
            var clock = playback(screen);
            require(clock.visibleCharacters(now) == 0, "leading pause holds back native screen text");
            require(!VillagerDialogueMouthAnimation.isTalking((Villager) minecraft.level.getEntity(entityId)),
                    "native mouth animation stops during a pause");
            var renderer = minecraft.getEntityRenderDispatcher().getRenderer(minecraft.level.getEntity(entityId));
            require(renderer instanceof LivingEntityRenderer<?, ?>, "speaker has a living renderer");
            var model = ((LivingEntityRenderer<?, ?>) renderer).getModel();
            require(model instanceof HeadedModel && Math.abs(((HeadedModel) model).getHead().yRot - Math.toRadians(35)) < 0.04,
                    "the render hook applies held gaze to the actual speaker model");
            invoke(screen, "trySkipDialogueTextAnimation");
            require(playback(screen).complete(now), "native skip bypasses the pending pause");
            verifyHeadPoseAcrossSlowFrame();
            verifyParticleEmission();
            verifySoundEmission();
            screen.acceptDialogueExchanges(List.of(speech(PERFORMANCE)));
            shownAt = now;
            phase = 4;
        } else if (phase == 4 && minecraft.screen instanceof VillagerInteractionScreen screen) {
            if (now - lastCapture >= 100) {
                Screenshot.grab(Path.of(System.getProperty(OUTPUT)).toFile(), String.format("frame-%04d.png", frame++),
                        minecraft.getMainRenderTarget(), ignored -> { });
                lastCapture = now;
            }
            if (playback(screen).complete(now) && finishedAt == 0) finishedAt = now;
            if (finishedAt != 0 && now - finishedAt >= 1200) {
                require(frame >= 50, "performance recording contains enough rendered frames");
                require(!((String) field(screen, "villagerDialogueText")).contains("<pause"), "cue tags are absent from rendered text");
                require((int) field(playback(screen), "nextParticle") == 2, "native screen dispatches both authored particle bursts");
                verifyCampaignRefreshes(screen);
                minecraft.setScreen(null);
                phase = 5;
                shownAt = now;
            }
        } else if (phase == 5 && now - shownAt >= 500) {
            var entity = (Villager) minecraft.level.getEntity(entityId);
            require(!VillagerDialogueMouthAnimation.isTalking(entity), "closing dialogue clears mouth animation");
            require(field(VillagerDialoguePerformance.class, "speaker") == null, "closing dialogue clears the held speaker pose");
            ConversationHistoryClientChecks.run(entity);
            shownAt = now;
            phase = 6;
        } else if (phase == 6 && now - shownAt >= 500) {
            Screenshot.grab(Path.of(System.getProperty(OUTPUT)).toFile(), "conversation-history.png",
                    minecraft.getMainRenderTarget(), ignored -> { });
            ConversationHistoryClientChecks.showPauseMenu();
            shownAt = now;
            phase = 7;
        } else if (phase == 7 && now - shownAt >= 500) {
            Screenshot.grab(Path.of(System.getProperty(OUTPUT)).toFile(), "conversation-history-button.png",
                    minecraft.getMainRenderTarget(), ignored -> { });
            ConversationHistoryClientChecks.showSmallScreen();
            shownAt = now;
            phase = 8;
        } else if (phase == 8 && now - shownAt >= 500) {
            Screenshot.grab(Path.of(System.getProperty(OUTPUT)).toFile(), "conversation-history-small.png",
                    minecraft.getMainRenderTarget(), ignored -> { });
            shownAt = now;
            phase = 9;
        } else if (phase == 9 && now - shownAt >= 1000) {
            ConversationHistoryClientChecks.verifyRendered();
            try {
                Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"),
                        "Dialogue cue client smoke passed\nRecorded frames: " + frame + "\n");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            phase = 99;
            minecraft.stop();
        }
    }

    private static void verifyHeadPoseAcrossSlowFrame() {
        var minecraft = Minecraft.getInstance();
        var entity = (Villager) minecraft.level.getEntity(entityId);
        var renderer = (LivingEntityRenderer<?, ?>) minecraft.getEntityRenderDispatcher().getRenderer(entity);
        var head = ((HeadedModel) renderer.getModel()).getHead();
        try {
            Thread.sleep(300);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        head.yRot = 0;
        head.xRot = 0;
        VillagerDialoguePerformance.apply(entity, head);
        require(Math.abs(head.yRot - Math.toRadians(35)) < 0.04,
                "a slow frame cannot briefly drop the held dialogue head pose");
        VillagerDialoguePerformance.clear();
        head.yRot = 0;
        VillagerDialoguePerformance.apply(entity, head);
        require(Math.abs(head.yRot - Math.toRadians(35)) < 0.04,
                "active dialogue restores its pose before rendering after screen replacement");
    }

    private static void verifySoundEmission() {
        var minecraft = Minecraft.getInstance();
        var clock = new DialoguePlayback(DialogueTextSegment.parse(
                "A<sound:entity.villager.yes volume=0.5 pitch=1.2><particle:heart>", DialogueTextEffects.NONE), 0, 100);
        require(VillagerDialoguePerformance.emitSounds(minecraft.level.getEntity(entityId), clock, 99).isEmpty(),
                "native sounds wait for their text position");
        var played = VillagerDialoguePerformance.emitSounds(minecraft.level.getEntity(entityId), clock, 100);
        require(played.size() == 1 && played.getFirst().getLocation().toString().equals("minecraft:entity.villager.yes")
                        && played.getFirst().getVolume() == 0.5F && played.getFirst().getPitch() == 1.2F,
                "native sound uses the authored resource, volume and pitch");
        require(VillagerDialoguePerformance.emitSounds(null, clock.copy(), 200).isEmpty(), "native sounds never replay on copying");
        played.forEach(minecraft.getSoundManager()::stop);
    }

    private static void verifyParticleEmission() {
        var minecraft = Minecraft.getInstance();
        minecraft.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
        var entity = minecraft.level.getEntity(entityId);
        var clock = new DialoguePlayback(DialogueTextSegment.parse(
                "<particle:smoke amount:2><pause=500><particle:heart amount:3>", DialogueTextEffects.NONE), 0, 35);
        var pending = (java.util.Queue<?>) field(minecraft.particleEngine, "particlesToAdd");
        int before = pending.size();
        VillagerDialoguePerformance.emitParticles(entity, clock, 0);
        require(pending.size() == before + 2, "particle cue creates the authored number of real client particles");
        VillagerDialoguePerformance.emitParticles(entity, clock, 499);
        require(pending.size() == before + 2, "particles do not repeat or fire during the pause");
        var copy = clock.copy();
        VillagerDialoguePerformance.emitParticles(entity, copy, 500);
        require(pending.size() == before + 5, "copied playback emits only the remaining burst");
        VillagerDialoguePerformance.emitParticles(entity, copy, 900);
        require(pending.size() == before + 5, "completed particle bursts never repeat");
    }

    private static void verifyCampaignRefreshes(VillagerInteractionScreen screen) {
        var npc = Minecraft.getInstance().level.getEntity(entityId);
        var controller = field(screen, "campaignConversation");
        setField(controller, "character", npc.getUUID());
        var view = (java.util.UUID) field(controller, "view");
        var session = java.util.UUID.randomUUID();
        var line = new CampaignConversationViewPayload.Line("cue", npc.getUUID(), entityId, "Rowan",
                "<pause=60000><agree>Campaign line.<reset>", 0, 1);
        screen.acceptCampaignConversation(campaign(view, session, 1, line, "", null));
        var firstPlayback = playback(screen);
        Object startedAt = field(firstPlayback, "startedAt");
        screen.acceptCampaignConversation(campaign(view, session, 2, line, "Updated information", null));
        require(startedAt.equals(field(playback(screen), "startedAt"))
                        && field(firstPlayback, "revealTimes") == field(playback(screen), "revealTimes"),
                "campaign metadata refresh preserves the cue clock");
        var history = new CampaignConversationViewPayload.History(0, 1, List.of("<pause=60000><agree>Earlier line.<reset>"));
        screen.acceptCampaignConversation(campaign(view, session, 3, line, "", history));
        @SuppressWarnings("unchecked")
        var segments = (List<DialogueTextSegment>) field(screen, "villagerDialogueTextSegments");
        require(segments.stream().allMatch(segment -> segment.cues().isEmpty()), "history displays text without performing cues");
        require(!((String) field(screen, "villagerDialogueText")).contains("<pause"), "history hides cue markup");
    }

    private static CampaignConversationViewPayload campaign(java.util.UUID view, java.util.UUID session, long revision,
            CampaignConversationViewPayload.Line line, String outcome, CampaignConversationViewPayload.History history) {
        var npc = Minecraft.getInstance().level.getEntity(entityId);
        var state = new CampaignConversationViewPayload.Session(session, revision, "Cue fixture",
                history == null ? ConversationSession.Phase.EXCHANGES : ConversationSession.Phase.RESPONSES,
                line, true, false, "", List.of(), List.of(), null, outcome, null, List.of(), false, false, history);
        return new CampaignConversationViewPayload(view, entityId, npc.getUUID(), 0, 1, List.of(), state, "");
    }

    private static DialogueSpeech speech(String text) {
        return DialogueSpeech.npc(Minecraft.getInstance().level.getEntity(entityId),
                DialogueTextSegment.parse(text, DialogueTextEffects.NONE),
                DialogueCamera.read(JsonParser.parseString("{\"camera\":{\"preset\":\"speaker_facing\",\"fov\":40}}").getAsJsonObject()));
    }

    private static DialoguePlayback playback(VillagerInteractionScreen screen) {
        return (DialoguePlayback) field(screen, "dialoguePlayback");
    }

    private static Object field(Object target, String name) {
        try {
            var type = target instanceof Class<?> value ? value : target.getClass();
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target instanceof Class<?> ? null : target);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void invoke(Object target, String name) {
        try {
            var method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
