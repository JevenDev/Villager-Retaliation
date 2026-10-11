package com.jvn.villagerretaliation.client.interaction;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.DialogueTextSpeed;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class DialogueCameraClientSmoke {
    private static final String OUTPUT = "villagerretaliation.dialogueCameraClientSmoke";
    private static final float NETWORK_ROTATION_TOLERANCE = 360.0F / 256.0F + 0.01F;
    private static CompletableFuture<Integer> preparing;
    private static CompletableFuture<Integer> blockers;
    private static int entityId;
    private static int phase;
    private static int perspective;
    private static long started;
    private static long shownAt;
    private static boolean duplicate;
    private static float yaw;
    private static float pitch;
    private static List<DialogueSpeech> speech;
    private static DialogueCameraOcclusion.Snapshot masked;

    private DialogueCameraClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || phase == 9) return;
        var minecraft = Minecraft.getInstance();
        minecraft.setWindowActive(true);
        minecraft.options.pauseOnLostFocus = false;
        if (started == 0) started = Util.getMillis();
        require(Util.getMillis() - started < 180000, "camera client smoke timed out at phase " + phase);
        if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) return;
        if ((phase == 1 || phase == 11) && Util.getMillis() - shownAt > 15000) {
            capture("startup-timeout");
            var pending = preparing.isDone() ? minecraft.level.getEntity(preparing.join()) : null;
            require(false, "interaction startup did not settle: screen=" + minecraft.screen + ", speaker=" + pending
                    + (pending instanceof Villager villager ? ", body=" + villager.yBodyRot + ", head=" + villager.getYHeadRot()
                            + ", yaw=" + villager.getYRot() : ""));
        }
        var server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        if (phase == 0 && minecraft.screen == null) {
            minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            minecraft.options.fov().set(96);
            VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.set(true);
            VillagerRetaliationConfig.ENABLE_NORMAL_DIALOGUE_CAMERA_FOCUS.set(true);
            VillagerRetaliationConfig.DIALOGUE_TEXT_SPEED.set(DialogueTextSpeed.INSTANT);
            preparing = server.submit(() -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayer(playerId);
                for (var position : BlockPos.betweenClosed(5, 99, 5, 25, 99, 25))
                    level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
                player.teleportTo(15.5, 100, 15.5);
                var villager = EntityType.VILLAGER.create(level);
                villager.setNoAi(true);
                villager.setPos(15.5, 100, 19.5);
                level.addFreshEntity(villager);
                return villager.getId();
            });
            shownAt = Util.getMillis();
            phase = 1;
        } else if (phase == 1 && preparing.isDone()) {
            entityId = preparing.join();
            if (!(minecraft.level.getEntity(entityId) instanceof Villager)
                    || minecraft.player.position().distanceToSqr(new Vec3(15.5, 100, 15.5)) > 0.01) return;
            preparing = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var villager = (Villager) server.overworld().getEntity(entityId);
                require(com.jvn.villagerretaliation.interaction.VillagerConversationService.start(player, villager),
                        "normal interaction begins for the camera fixture");
                VillagerInteractionScreenOpener.openNormal(player, villager, false);
                return entityId;
            });
            shownAt = Util.getMillis();
            phase = 11;
        } else if (phase == 11 && preparing.isDone() && minecraft.screen instanceof VillagerInteractionScreen screen) {
            preparing.join();
            if (!(minecraft.level.getEntity(entityId) instanceof Villager villager)
                    || Math.abs(net.minecraft.util.Mth.wrapDegrees(villager.yBodyRot - 180)) >= NETWORK_ROTATION_TOLERANCE
                    || Math.abs(net.minecraft.util.Mth.wrapDegrees(villager.getYHeadRot() - 180)) >= NETWORK_ROTATION_TOLERANCE) return;
            beginSequence(screen);
            phase = 2;
        } else if (phase == 2 && minecraft.screen instanceof VillagerInteractionScreen screen) {
            double elapsed = (Util.getMillis() - shownAt) / 1000.0;
            require(DialogueCameraController.active(), "authored camera is active");
            require(minecraft.options.getCameraType() == CameraType.values()[perspective], "authored shots preserve the selected perspective");
            require(minecraft.player.getYRot() == yaw && minecraft.player.getXRot() == pitch, "authored shots never rotate the physical player");
            checkRange();
            if (elapsed > 0.25 && !duplicate) {
                require(minecraft.gameRenderer.getMainCamera().isDetached(), "bird's-eye renders the player's body");
                DialogueCameraController.onComputeFov(new net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov(
                        minecraft.gameRenderer, minecraft.gameRenderer.getMainCamera(), 1, 70, false));
                require(Math.abs(DialogueCameraController.update(minecraft.gameRenderer.getMainCamera(), 1).fov() - 96) < 0.01,
                        "default shots preserve the configured FOV across held-item FOV events");
                screen.acceptDialogueExchanges(speech);
                duplicate = true;
                capture("birds-eye-" + perspective);
            }
            if (elapsed > 2 && elapsed < 2.5) {
                require(cameraTimeline().index() == 1, "duplicate updates do not restart the sequence");
                var villager = (Villager) minecraft.level.getEntity(entityId);
                require(Math.abs(net.minecraft.util.Mth.wrapDegrees(villager.yBodyRot - 180)) < NETWORK_ROTATION_TOLERANCE,
                        "the villager's rendered body faces the interacting player: body=" + villager.yBodyRot
                                + ", head=" + villager.getYHeadRot() + ", yaw=" + villager.getYRot()
                                + ", speaker=" + villager.position() + ", player=" + minecraft.player.position());
                require(Math.abs(net.minecraft.util.Mth.wrapDegrees(villager.getYHeadRot() - 180)) < NETWORK_ROTATION_TOLERANCE,
                        "the villager's rendered head faces the interacting player");
            }
            if (elapsed >= 4.3) {
                capture("shoulder-" + perspective);
                screen.acceptDialogueExchanges(List.of(line(camera("""
                        {"camera":{"preset":"speaker_facing","fov":45}}
                        """))));
                DialogueCameraController.synchronize();
                var pose = DialogueCameraController.update(minecraft.gameRenderer.getMainCamera(), 1);
                require(pose != null && pose.fov() == 45, "following line cuts to its FOV immediately");
                shownAt = Util.getMillis();
                phase = 10;
            }
        } else if (phase == 10 && minecraft.screen instanceof VillagerInteractionScreen screen && Util.getMillis() - shownAt > 600) {
            capture("speaker-facing-" + perspective);
            verifyPlacement(screen);
            if (perspective == 0) verifyCampaignLifecycle(screen);
            if (++perspective < CameraType.values().length) {
                beginSequence(screen);
                phase = 2;
            }
            else {
                blockers = server.submit(() -> {
                    var level = server.overworld();
                    for (var position : BlockPos.betweenClosed(12, 100, 13, 19, 105, 14))
                        level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(15, 101, 14), Blocks.CHEST.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(16, 101, 14), Blocks.GLASS.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(15, 102, 14), Blocks.OAK_LEAVES.defaultBlockState());
                    for (var position : BlockPos.betweenClosed(12, 107, 12, 19, 107, 20))
                        level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
                    var blocker = EntityType.IRON_GOLEM.create(level);
                    blocker.setNoAi(true);
                    blocker.setPos(15.5, 100, 17.5);
                    level.addFreshEntity(blocker);
                    return blocker.getId();
                });
                phase = 3;
            }
        } else if (phase == 3 && blockers.isDone() && minecraft.screen instanceof VillagerInteractionScreen screen) {
            if (!minecraft.level.getBlockState(new BlockPos(15, 101, 14)).is(Blocks.CHEST)
                    || minecraft.level.getEntity(blockers.join()) == null) return;
            screen.acceptDialogueExchanges(List.of(line(camera("""
                    {"camera":{"preset":"custom","anchor":"player","perspective":"third_person","offset":[0,0,-3]}}
                    """))));
            shownAt = Util.getMillis();
            phase = 4;
        } else if (phase == 4 && minecraft.screen instanceof VillagerInteractionScreen screen && Util.getMillis() - shownAt > 4000) {
            checkRange();
            masked = DialogueCameraOcclusion.snapshot(minecraft.level);
            require(!masked.blocks().isEmpty(), "walls and near-plane blockers receive a render mask, camera="
                    + minecraft.gameRenderer.getMainCamera().getPosition() + ", frame=" + screen.dialogueCameraFrame()
                    + ", active=" + DialogueCameraController.active());
            require(masked.blocks().stream().anyMatch(position -> position.getZ() == 14), "subject sightlines hide blocking wall geometry");
            require(minecraft.level.getBlockState(new BlockPos(15, 101, 14)).is(Blocks.CHEST), "masking leaves world data unchanged");
            require(DialogueCameraOcclusion.hides(minecraft.level.getEntity(blockers.join())), "unrelated entities covering the speaker are hidden");
            if (!net.neoforged.fml.ModList.get().isLoaded("sodium")) {
                var cache = new net.minecraft.client.renderer.chunk.RenderRegionCache();
                for (var position : masked.blocks()) {
                    var region = cache.createRegion(minecraft.level, SectionPos.of(position));
                    require(region != null && region.getBlockState(position).isAir(), "vanilla worker snapshots mask every hidden block");
                }
            }
            shownAt = Util.getMillis();
            capture("masked-blockers");
            phase = 5;
        } else if (phase == 5 && minecraft.screen instanceof VillagerInteractionScreen screen && Util.getMillis() - shownAt > 500) {
            require(DialogueCameraOcclusion.snapshot(minecraft.level).version() == masked.version(), "stationary shots keep an unchanged mask");
            capture("stable-mask");
            VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.set(false);
            DialogueCameraController.synchronize();
            require(!DialogueCameraController.active() && DialogueCameraOcclusion.snapshot(minecraft.level).blocks().isEmpty(),
                    "accessibility preferences release cameras and masks");
            VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.set(true);
            screen.acceptDialogueExchanges(List.of(line(camera("""
                    {"camera":{"preset":"birds_eye","target":"group"}}
                    """))));
            DialogueCameraController.synchronize();
            DialogueCameraController.update(minecraft.gameRenderer.getMainCamera(), 1);
            require(DialogueCameraController.active(), "group camera resolves loaded participants under a low ceiling");
            checkRange();
            minecraft.level.removeEntity(entityId, Entity.RemovalReason.UNLOADED_TO_CHUNK);
            DialogueCameraController.update(minecraft.gameRenderer.getMainCamera(), 1);
            require(!DialogueCameraController.active(), "missing speaker and character release authored control");
            minecraft.levelRenderer.allChanged();
            require(DialogueCameraOcclusion.snapshot(minecraft.level).blocks().isEmpty(), "resource rebuild clears mask snapshots");
            screen.closeFromServer(false);
            DialogueCameraController.synchronize();
            require(!DialogueCameraController.active(), "closure releases authored control");
            for (var position : masked.blocks())
                DialogueCameraOcclusion.built(SectionPos.of(position), masked);
            DialogueCameraOcclusion.tick();
            require(DialogueCameraOcclusion.snapshot(minecraft.level).blocks().isEmpty(), "stale mesh completions cannot restore an old mask");
            shownAt = Util.getMillis();
            phase = 6;
        } else if (phase == 6 && Util.getMillis() - shownAt > 1500) {
            require(DialogueCameraOcclusion.snapshot(minecraft.level).blocks().isEmpty(), "geometry remains restored after closure");
            capture("restored-blockers");
            VillagerRetaliationConfig.ENABLE_NORMAL_DIALOGUE_CAMERA_FOCUS.set(false);
            preparing = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var villager = EntityType.VILLAGER.create(server.overworld());
                villager.setNoAi(true);
                villager.setPos(15.5, 100, 19.5);
                server.overworld().addFreshEntity(villager);
                require(com.jvn.villagerretaliation.interaction.VillagerInteractionService.openForcedDialogue(
                        player, villager, "", List.of(), true), "forced fixture opens its interaction screen");
                com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendDialogueExchanges(player, villager,
                        List.of(DialogueSpeech.npc(villager, DialogueTextSegment.parse("Forced camera.", DialogueTextEffects.NONE),
                                camera("""
                                        {"camera":{"preset":"speaker_facing","fov":55}}
                                        """))));
                return villager.getId();
            });
            phase = 7;
        } else if (phase == 7 && preparing.isDone() && minecraft.screen instanceof VillagerInteractionScreen screen) {
            entityId = preparing.join();
            if (screen.dialogueCameraFrame() == null || screen.dialogueCameraFrame().camera() == null) return;
            require(ClientVillagerConversationState.usesForcedCameraZoom(), "forced dialogue keeps its camera policy");
            DialogueCameraController.synchronize();
            var pose = DialogueCameraController.update(minecraft.gameRenderer.getMainCamera(), 1);
            require(pose != null && pose.fov() == 55, "forced dialogue uses the shared authored camera with normal focus disabled");
            checkRange();
            VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.set(false);
            DialogueCameraController.synchronize();
            require(!DialogueCameraController.active(), "global accessibility preference also disables forced shots");
            VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.set(true);
            VillagerRetaliationConfig.ENABLE_NORMAL_DIALOGUE_CAMERA_FOCUS.set(true);
            screen.closeFromServer(false);
            shownAt = Util.getMillis();
            phase = 8;
        } else if (phase == 8 && Util.getMillis() - shownAt > 500) {
            DialogueCameraController.clear();
            try { Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Dialogue camera client smoke passed" + System.lineSeparator()); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            phase = 9;
            minecraft.stop();
        }
    }

    private static void beginSequence(VillagerInteractionScreen screen) {
        var minecraft = Minecraft.getInstance();
        minecraft.options.setCameraType(CameraType.values()[perspective]);
        speech = List.of(line(camera("""
                {"camera":{"shots":[{"preset":"birds_eye","hold_seconds":1},
                  {"preset":"over_shoulder_right","transition":{"type":"lerp","duration_seconds":3}}]}}
                """)));
        screen.acceptDialogueExchanges(speech);
        DialogueCameraController.synchronize();
        yaw = minecraft.player.getYRot();
        pitch = minecraft.player.getXRot();
        shownAt = Util.getMillis();
        duplicate = false;
    }

    private static void verifyPlacement(VillagerInteractionScreen screen) {
        var minecraft = Minecraft.getInstance();
        var renderer = minecraft.gameRenderer;
        var speaker = minecraft.level.getEntity(entityId);
        screen.acceptDialogueExchanges(List.of(line(camera("""
                {"camera":{"preset":"custom","anchor":"speaker","offset":[0,1,-2],"perspective":"third_person","placement":"fixed"}}
                """))));
        DialogueCameraController.synchronize();
        var fixed = DialogueCameraController.update(renderer.getMainCamera(), 1);
        Vec3 original = speaker.position();
        speaker.setPos(original.add(1, 0, 0));
        var moved = DialogueCameraController.update(renderer.getMainCamera(), 1);
        require(moved.position().distanceTo(fixed.position()) < 0.001, "fixed placements do not follow moving anchors");
        speaker.setPos(original);
        screen.acceptDialogueExchanges(List.of(line(camera("""
                {"camera":{"preset":"custom","anchor":"speaker","offset":[0,1,-2],"perspective":"third_person"}}
                """))));
        DialogueCameraController.synchronize();
        var relative = DialogueCameraController.update(renderer.getMainCamera(), 1);
        speaker.setPos(original.add(1, 0, 0));
        moved = DialogueCameraController.update(renderer.getMainCamera(), 1);
        require(Math.abs(moved.position().x - relative.position().x - 1) < 0.001, "relative placements follow moving anchors");
        speaker.setPos(original);
        screen.acceptDialogueExchanges(List.of(line(camera("""
                {"camera":{"preset":"custom","anchor":"player","offset":[0,8,8],"perspective":"third_person","placement":"fixed"}}
                """))));
        DialogueCameraController.synchronize();
        fixed = DialogueCameraController.update(renderer.getMainCamera(), 1);
        Vec3 playerPosition = minecraft.player.position();
        minecraft.player.setPos(playerPosition.add(0, 0, 1));
        moved = DialogueCameraController.update(renderer.getMainCamera(), 1);
        require(moved.position().distanceTo(fixed.position()) < 0.001,
                "a fixed shot freezes its initial bounded position when the player moves closer");
        minecraft.player.setPos(playerPosition);
        screen.acceptDialogueExchanges(List.of(line(camera("""
                {"camera":{"preset":"telescope"}}
                """))));
        DialogueCameraController.synchronize();
        var telescope = DialogueCameraController.update(renderer.getMainCamera(), 1);
        require(telescope.fov() == 20 && !DialogueCameraController.detached(), "telescope uses a first-person 20-degree view");
        var missing = new DialogueSpeech(java.util.UUID.randomUUID(), Integer.MAX_VALUE, "Unloaded",
                DialogueTextSegment.parse("Still here.", DialogueTextEffects.NONE), camera("""
                        {"camera":{"preset":"first_person"}}
                        """));
        screen.acceptDialogueExchanges(List.of(missing));
        DialogueCameraController.synchronize();
        require(DialogueCameraController.update(renderer.getMainCamera(), 1) != null, "missing speaker falls back to the interaction character");
        screen.acceptVillagerDialogue("No camera on this line.", List.of());
        DialogueCameraController.synchronize();
        require(!DialogueCameraController.active(), "a new line without a cue releases authored control");
    }

    private static void verifyCampaignLifecycle(VillagerInteractionScreen screen) {
        var minecraft = Minecraft.getInstance();
        var npc = minecraft.level.getEntity(entityId);
        try {
            var field = VillagerInteractionScreen.class.getDeclaredField("campaignConversation");
            field.setAccessible(true);
            var controller = (CampaignConversationScreenController) field.get(screen);
            field = CampaignConversationScreenController.class.getDeclaredField("view");
            field.setAccessible(true);
            var view = (java.util.UUID) field.get(controller);
            field = CampaignConversationScreenController.class.getDeclaredField("character");
            field.setAccessible(true);
            field.set(controller, npc.getUUID());
            var session = java.util.UUID.randomUUID();
            var cue = camera("""
                    {"camera":{"shots":[{"preset":"birds_eye","hold_seconds":1},
                      {"preset":"over_shoulder_left","transition":{"type":"lerp","duration_seconds":3}}]}}
                    """);
            var line = new com.jvn.villagerretaliation.network.CampaignConversationViewPayload.Line(
                    "camera", npc.getUUID(), entityId, "Guide", "First page.", 0, 2, cue, null);
            var payload = campaignPayload(view, session, 1, line,
                    com.jvn.villagerretaliation.dialogue.conversation.ConversationSession.Phase.EXCHANGES, null);
            screen.acceptCampaignConversation(payload);
            DialogueCameraController.synchronize();
            var timeline = cameraTimeline();
            require(timeline != null, "campaign exchanges use the shared camera controller");
            screen.acceptCampaignConversation(payload);
            DialogueCameraController.synchronize();
            require(cameraTimeline() == timeline, "campaign packet refreshes preserve the timeline");
            var page = new com.jvn.villagerretaliation.network.CampaignConversationViewPayload.Line(
                    "camera", npc.getUUID(), entityId, "Guide", "Second page.", 1, 2, cue, null);
            screen.acceptCampaignConversation(campaignPayload(view, session, 2, page,
                    com.jvn.villagerretaliation.dialogue.conversation.ConversationSession.Phase.EXCHANGES, null));
            DialogueCameraController.synchronize();
            require(cameraTimeline() == timeline, "text pagination preserves the timeline");
            screen.acceptCampaignConversation(campaignPayload(view, session, 3, null,
                    com.jvn.villagerretaliation.dialogue.conversation.ConversationSession.Phase.RESPONSES, null));
            DialogueCameraController.synchronize();
            int shot = timeline.index();
            timeline.update(600, ignored -> new DialogueCameraTimeline.Pose(Vec3.ZERO, 0, 0, 70));
            require(timeline.index() == shot, "early advancement cancels unfinished shots during response selection");
            screen.acceptCampaignConversation(campaignPayload(view, session, 4, line,
                    com.jvn.villagerretaliation.dialogue.conversation.ConversationSession.Phase.RESPONSES,
                    new com.jvn.villagerretaliation.network.CampaignConversationViewPayload.History(0, 1, List.of("Earlier line."))));
            DialogueCameraController.synchronize();
            require(cameraTimeline() == timeline, "history navigation does not replay camera cues");
            var unframed = new com.jvn.villagerretaliation.network.CampaignConversationViewPayload.Line(
                    "following", npc.getUUID(), entityId, "Guide", "No camera.", 0, 1);
            screen.acceptCampaignConversation(campaignPayload(view, session, 5, unframed,
                    com.jvn.villagerretaliation.dialogue.conversation.ConversationSession.Phase.EXCHANGES, null));
            DialogueCameraController.synchronize();
            require(!DialogueCameraController.active(), "a following campaign line without a camera releases control");
            field = VillagerInteractionScreen.class.getDeclaredField("page");
            field.setAccessible(true);
            @SuppressWarnings({"unchecked", "rawtypes"})
            var talk = Enum.valueOf((Class<? extends Enum>) field.getType(), "TALK");
            field.set(screen, talk);
            field = CampaignConversationScreenController.class.getDeclaredField("state");
            field.setAccessible(true);
            field.set(controller, null);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Campaign camera fixture is unavailable", exception); }
    }

    private static com.jvn.villagerretaliation.network.CampaignConversationViewPayload campaignPayload(
            java.util.UUID view, java.util.UUID session, long revision,
            com.jvn.villagerretaliation.network.CampaignConversationViewPayload.Line line,
            com.jvn.villagerretaliation.dialogue.conversation.ConversationSession.Phase phase,
            com.jvn.villagerretaliation.network.CampaignConversationViewPayload.History history) {
        var npc = Minecraft.getInstance().level.getEntity(entityId);
        var state = new com.jvn.villagerretaliation.network.CampaignConversationViewPayload.Session(session, revision,
                "Camera fixture", phase, line, true, false, "", List.of(), List.of(), null, "", null, List.of(), false, false, history);
        return new com.jvn.villagerretaliation.network.CampaignConversationViewPayload(view, entityId, npc.getUUID(), 0, 1, List.of(), state, "");
    }

    private static DialogueCameraTimeline cameraTimeline() {
        try {
            var field = DialogueCameraController.class.getDeclaredField("timeline");
            field.setAccessible(true);
            return (DialogueCameraTimeline) field.get(null);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static DialogueSpeech line(com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera camera) {
        return DialogueSpeech.npc(Minecraft.getInstance().level.getEntity(entityId),
                DialogueTextSegment.parse("Camera fixture.", DialogueTextEffects.NONE), camera);
    }

    private static com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera camera(String json) {
        return DialogueCamera.read(JsonParser.parseString(json).getAsJsonObject());
    }

    private static void checkRange() {
        var minecraft = Minecraft.getInstance();
        require(minecraft.gameRenderer.getMainCamera().getPosition().distanceTo(minecraft.player.position()) <= 8.001,
                "rendered camera remains within eight blocks");
    }

    private static void capture(String name) {
        var minecraft = Minecraft.getInstance();
        Screenshot.grab(Path.of(System.getProperty(OUTPUT)).toFile(), name + ".png", minecraft.getMainRenderTarget(), ignored -> { });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
