package com.jvn.villagerretaliation.client.interaction;

import com.google.gson.JsonParser;
import com.jvn.toucanlib.client.ToucanTextRenderer;
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
    private static final String PERFORMANCE = "<look=player><agree><wave>Yes, I heard it too.</wave><sound:entity.villager.yes volume=0.5 pitch=1.1><particle:smoke amount:2><pause=650>"
            + "<look=left> <shake><italic>Over there...</italic></shake><pause=700><look=right> <jump><bold>or there?</bold></jump><pause=650>"
            + "<look=player><disagree> <pulse><rainbow>No. Stay here.</rainbow></pulse><pause=600><look=up> <gradient:#ff0000:#0000ff><underline>Above us?</underline></gradient><pause=650>"
            + "<look=down> <strikethrough>Nothing below.</strikethrough><pause=600><reset><disagree><aqua>Wait.</aqua><pause=100>"
            + "<agree> <obfuscated>Maybe.</obfuscated><pause=100><reset><pause=300><speed=0.5> Let's think.<particle:minecraft:heart amount:3><nervous> I don't like this.</nervous>";
    private static CompletableFuture<Integer> preparing;
    private static int phase;
    private static int entityId;
    private static int frame;
    private static long started;
    private static long shownAt;
    private static long lastCapture;
    private static long finishedAt;
    private static boolean timelineWindowRendered;

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
            foundry.imgui.api.ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(() -> {
                if (com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.active())
                    timelineWindowRendered |= imgui.internal.ImGui.findWindowByName("Dialogue timeline###VR_DialogueTimeline").isValidPtr();
            });
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
            require(com.jvn.villagerretaliation.dialogue.resources.DialogueStyleResources.current().containsKey("nervous"),
                    "server style catalog reaches the client before dialogue");
            var nervous = DialogueTextSegment.parse("<nervous>Listen.</nervous> Fine.", DialogueTextEffects.NONE);
            require(nervous.getFirst().effects().shake() && !nervous.getLast().effects().shake(), "client renders scoped custom styles");
            verifyAnimatedTextSettings();
            verifyParticipantTargets();
            verifyHeadPoseAcrossSlowFrame();
            verifyHeadPoseTransitions();
            verifyParticleEmission();
            verifySoundEmission();
            verifyPreviewParticles();
            verifyStyleLifecycleCues();
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
            minecraft.setScreen(null);
            startTimelinePreview();
            shownAt = now;
            phase = 10;
        } else if (phase == 10 && now - shownAt >= 750) {
            var timeline = com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.timeline();
            require(timeline != null && !timeline.playing() && timeline.position() == 250,
                    "editor timeline stays at the scrubbed position across native frames");
            require(timeline.visibleCharacters() == 0, "editor timeline holds back text during its leading pause");
            require(timelineWindowRendered, "the editor renders its timeline controls");
            captureTimeline("dialogue-timeline-paused");
            com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.seek(700);
            require(com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.timeline().visibleCharacters() > 0,
                    "scrubbing forward reveals text through the native dialogue screen");
            com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.replay();
            shownAt = Util.getMillis();
            phase = 11;
        } else if (phase == 11 && now - shownAt >= 750) {
            var timeline = com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.timeline();
            require(timeline != null && timeline.playing() && timeline.position() >= 700,
                    "editor replay advances the shared timeline: " + timeline);
            captureTimeline("dialogue-timeline-playing");
            com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.stop();
            require(!com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.active(),
                    "closing preview releases the timeline");
            try {
                Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"),
                        "Dialogue cue client smoke passed\nRecorded frames: " + frame + "\n");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            phase = 99;
            minecraft.stop();
        }
    }

    private static void verifyAnimatedTextSettings() {
        var defaults = DialogueTextEffects.read(JsonParser.parseString("{\"text_effects\":{\"wavy\":true,\"shake\":true,\"jump\":true,\"pulse\":true}}").getAsJsonObject());
        var tuned = DialogueTextEffects.read(JsonParser.parseString("{\"text_effects\":{\"wavy\":{\"strength\":0.25,\"speed\":2},\"shake\":{\"strength\":0.25,\"speed\":2},\"jump\":{\"strength\":0.25,\"speed\":2},\"pulse\":{\"speed\":2}}}").getAsJsonObject());
        require(Math.abs(ToucanTextRenderer.waveOffset(VillagerStyledTextRenderer.effects(tuned), 17, 24) - ToucanTextRenderer.waveOffset(VillagerStyledTextRenderer.effects(defaults), 34, 24) * 0.25F) < 0.0001,
                "wave strength and speed reach the renderer");
        require(Math.abs(ToucanTextRenderer.shakeOffset(VillagerStyledTextRenderer.effects(tuned), 17, 4) - ToucanTextRenderer.shakeOffset(VillagerStyledTextRenderer.effects(defaults), 34, 4) * 0.25F) < 0.0001,
                "shake strength and speed reach the renderer");
        require(Math.abs(ToucanTextRenderer.jumpOffset(VillagerStyledTextRenderer.effects(tuned), 17, 4) - ToucanTextRenderer.jumpOffset(VillagerStyledTextRenderer.effects(defaults), 34, 4) * 0.25F) < 0.0001,
                "jump strength and speed reach the renderer");
        require(ToucanTextRenderer.colorWithAlpha(net.minecraft.network.chat.Style.EMPTY, VillagerStyledTextRenderer.effects(tuned), 0xabcdef, 255, 17, 4)
                == ToucanTextRenderer.colorWithAlpha(net.minecraft.network.chat.Style.EMPTY, VillagerStyledTextRenderer.effects(defaults), 0xabcdef, 255, 34, 4), "pulse animation speed reaches the renderer");
        var quiet = DialogueTextEffects.read(JsonParser.parseString("{\"text_effects\":{\"shake\":{\"strength\":0},\"pulse\":{\"strength\":0}}}").getAsJsonObject());
        require(ToucanTextRenderer.shakeOffset(VillagerStyledTextRenderer.effects(quiet), 17, 4) == 0, "zero shake strength is stationary");
        require(ToucanTextRenderer.colorWithAlpha(net.minecraft.network.chat.Style.EMPTY, VillagerStyledTextRenderer.effects(quiet), 0xabcdef, 255, 17, 4) == 0xffabcdef,
                "zero pulse strength preserves the original color and opacity");
    }

    private static void verifyParticipantTargets() {
        var minecraft = Minecraft.getInstance();
        var speaker = minecraft.level.getEntity(entityId);
        var near = EntityType.VILLAGER.create(minecraft.level);
        var guard = EntityType.VILLAGER.create(minecraft.level);
        var bystander = EntityType.VILLAGER.create(minecraft.level);
        near.setPos(speaker.position().add(2, 0, 0));
        guard.setPos(speaker.position().add(-4, 0, 0));
        bystander.setPos(speaker.position().add(0.1, 0, 0));
        var participants = List.of(speaker, near, guard);
        var roles = java.util.Map.<String, net.minecraft.world.entity.Entity>of("guard", guard);
        require(VillagerDialoguePerformance.selectTarget(speaker, "nearest_participant", participants, roles) == near,
                "nearest gaze excludes the speaker and nearby bystanders");
        require(VillagerDialoguePerformance.selectTarget(speaker, "role:guard", participants, roles) == guard,
                "named gaze uses the bound role even when another participant is closer");
        require(VillagerDialoguePerformance.selectTarget(speaker, "role:missing", participants, roles) == null,
                "missing role has no gaze target");
        guard.discard();
        require(VillagerDialoguePerformance.selectTarget(speaker, "role:guard", participants, roles) == null,
                "removed participants cannot be targeted");
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

    private static void verifyHeadPoseTransitions() {
        var neutral = new DialoguePlayback.Pose(0, 0);
        var held = new DialoguePlayback.Pose(35, 12);
        var clock = new DialoguePlayback(DialogueTextSegment.parse("<look=left><agree>Hello.", DialogueTextEffects.NONE), 0, 100);
        var transition = new VillagerDialoguePerformance.PoseTransition(neutral);
        require(transition.sample(clock, held, 0).equals(neutral), "new speaker starts from its rendered pose");
        require(transition.sample(clock, held, 90).equals(new DialoguePlayback.Pose(17.5F, 6)), "speaker eases into its pose");
        require(transition.sample(clock, held, 180).equals(held), "speaker settles at the authored pose");
        clock.skip();
        require(transition.sample(clock, neutral, 180).equals(held), "skipping preserves the current nod and gaze");
        require(transition.sample(clock, neutral, 270).equals(new DialoguePlayback.Pose(17.5F, 6)), "skipped nod eases back");
        require(transition.sample(clock, neutral, 360).equals(neutral), "skipped pose finishes returning");
        var next = new DialoguePlayback(List.of(), 360, 100);
        var right = new DialoguePlayback.Pose(-35, -12);
        require(transition.sample(next, right, 360).equals(neutral), "next line starts without a head snap");
        var halfway = transition.sample(next, right, 450);
        var interrupted = new DialoguePlayback(List.of(), 450, 100);
        require(transition.sample(interrupted, held, 450).equals(halfway), "rapid line changes retain the in-flight pose");
        require(transition.sample(interrupted, held, 630).equals(held), "interrupted transition reaches the latest pose");
        require(transition.sample(null, neutral, 630).equals(held), "departing speaker keeps its last rendered pose");
        require(!transition.settled(720) && transition.sample(null, neutral, 720).equals(new DialoguePlayback.Pose(17.5F, 6)),
                "departing speaker returns gradually");
        require(transition.sample(null, neutral, 810).equals(neutral) && transition.settled(810),
                "departing speaker releases its pose after settling");
        clock.seek(100, 900);
        require(transition.sample(clock, held, 900).equals(held), "paused editor scrubbing remains exact");
    }

    private static void captureTimeline(String name) {
        try (var image = com.jvn.villagerretaliation.authoring.client.AuthoringEditorClientSmoke.renderedImage()) {
            image.writeToFile(Path.of(System.getProperty(OUTPUT), "screenshots", name + ".png"));
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    private static void startTimelinePreview() {
        var minecraft = Minecraft.getInstance();
        var json = JsonParser.parseString("""
                {"entries":[{"id":"talk","label":"Talk","start":"start"}],"nodes":{
                  "start":{"lines":["<look=left><pause=500>A<sound:entity.villager.yes><particle:heart amount=3><agree> hello.<pause=500>"],
                    "end":true}}}
                """).getAsJsonObject();
        var document = new com.jvn.villagerretaliation.authoring.core.AuthoringDocument(
                com.jvn.villagerretaliation.authoring.core.DefinitionKind.TREE,
                "data/fixture/dialogue_trees/en_us/timeline.json", json);
        VillagerRetaliationConfig.DIALOGUE_TEXT_SPEED.set(DialogueTextSpeed.INSTANT);
        com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.start(document, "start",
                minecraft.level.getEntity(entityId), new com.jvn.villagerretaliation.authoring.preview.PreviewConditions(),
                () -> minecraft.setScreen(null));
        com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.seek(250);
        json.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0,
                new com.google.gson.JsonPrimitive("<look=left><pause=500>A<sound:entity.villager.yes><particle:heart amount=3><agree><wave> hello again.</wave><pause=500>"));
        com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.refreshPresentation(json);
        var updated = com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.timeline();
        require(updated.position() == 250 && !updated.playing(), "draft edits preserve the scrubbed position");
        require(com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview.timeline().duration() > 4000,
                "preview keeps a particle effect tail even with instant text selected");
    }

    private static void verifyPreviewParticles() {
        var minecraft = Minecraft.getInstance();
        var entity = minecraft.level.getEntity(entityId);
        var preview = new DialoguePreviewParticles();
        var clock = new DialoguePlayback(DialogueTextSegment.parse(
                "A<particle:smoke amount=2><pause=300><particle:heart>", DialogueTextEffects.NONE), 0, 100);
        preview.update(entity, clock, 99);
        require(((java.util.List<?>) field(preview, "particles")).isEmpty(), "preview particles wait for the cue");
        preview.update(entity, clock, 100);
        var active = (java.util.List<?>) field(preview, "particles");
        require(active.size() == 2, "preview builds the authored particle count");
        var particle = (net.minecraft.client.particle.Particle) field(active.getFirst(), "particle");
        var bounds = particle.getBoundingBox();
        try {
            var tick = minecraft.particleEngine.getClass().getDeclaredMethod("tickParticle", net.minecraft.client.particle.Particle.class);
            tick.setAccessible(true);
            tick.invoke(minecraft.particleEngine, particle);
            require(particle.getBoundingBox().equals(bounds), "native particle ticks cannot advance preview-owned particles");
            var normal = ((com.jvn.villagerretaliation.mixin.client.ParticleEngineAccessor) minecraft.particleEngine)
                    .villagerretaliation$makeParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                            entity.getX(), entity.getEyeY(), entity.getZ(), 0, 0.1, 0);
            var before = normal.getBoundingBox();
            tick.invoke(minecraft.particleEngine, normal);
            require(!normal.getBoundingBox().equals(before), "unrelated world particles tick normally");
            normal.remove();
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        clock.seek(125, 0);
        preview.update(entity, clock, 9000);
        require(DialoguePreviewParticles.partialTick(particle, 0) == 0.5F, "preview particle interpolation follows the scrubbed time");
        var paused = particle.getBoundingBox();
        preview.update(entity, clock, 12000);
        require(particle.getBoundingBox().equals(paused), "preview particles stay still while paused");
        clock.seek(0, 0);
        preview.update(entity, clock, 0);
        require(!particle.isAlive() && ((java.util.List<?>) field(preview, "particles")).isEmpty(),
                "rewinding removes earlier particle bursts");
        clock.seek(clock.previewDurationMillis(), 0);
        preview.update(entity, clock, 0);
        require(((java.util.List<?>) field(preview, "particles")).isEmpty(), "seeking past a burst respects particle lifetime");
        preview.clear();
    }

    private static void verifyStyleLifecycleCues() {
        var minecraft = Minecraft.getInstance();
        var entity = minecraft.level.getEntity(entityId);
        var style = com.jvn.villagerretaliation.dialogue.DialogueStyle.read(JsonParser.parseString("""
                {"tag":"uneasy","text_effects":{"shake":{"strength":0.35,"speed":0.8}},
                 "on_enter":["look=left","particle:smoke amount:2","sound:entity.villager.no volume=0.4"],
                 "on_exit":["look=right","particle:heart","sound:entity.villager.yes"]}
                """).getAsJsonObject());
        List<DialogueTextSegment> segments;
        try (var ignored = com.jvn.villagerretaliation.dialogue.resources.DialogueStyleResources.preview(java.util.Map.of("uneasy", style))) {
            segments = DialogueTextSegment.parse("<uneasy>AB</uneasy>C", null);
        }
        var clock = new DialoguePlayback(segments, 0, 100);
        require(segments.getFirst().effects().shakeAnimation().strength() == 0.35F, "native style combines tuned text effects and lifecycle cues");
        var pending = (java.util.Queue<?>) field(minecraft.particleEngine, "particlesToAdd");
        int before = pending.size();
        VillagerDialoguePerformance.emitParticles(entity, clock, 0);
        require(pending.size() == before + 2, "style entry emits real client particles");
        var entered = VillagerDialoguePerformance.emitSounds(entity, clock, 0);
        require(entered.size() == 1 && entered.getFirst().getVolume() == 0.4F, "style entry plays its configured client sound");
        VillagerDialoguePerformance.emitParticles(entity, clock, 200);
        require(pending.size() == before + 3, "style exit emits real client particles");
        var exited = VillagerDialoguePerformance.emitSounds(entity, clock, 200);
        require(exited.size() == 1, "style exit plays its configured client sound");
        require(clock.pose(400, 0, 0, 0, 0).yaw() == -35, "style exit gaze overrides the restored pose");
        entered.forEach(minecraft.getSoundManager()::stop);
        exited.forEach(minecraft.getSoundManager()::stop);
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
