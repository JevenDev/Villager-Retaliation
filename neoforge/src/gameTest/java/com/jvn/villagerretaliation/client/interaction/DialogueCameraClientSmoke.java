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
    private static int tradeGuiScale;
    private static double tradeFov;
    private static int tradeLeft;
    private static int tradeMovementStep;
    private static List<Integer> tradeWidgetXs;
    private static Vec3 tradeCameraPosition;
    private static boolean tradeCameraMoved;
    private static long tradePackReadyAt;
    private static java.util.concurrent.CompletableFuture<Void> tradeResourceReload;
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
            CameraAuthoringChecks.run();
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
                double beforeCut = currentTradeFov();
                screen.acceptDialogueExchanges(List.of(line(camera("""
                        {"camera":{"preset":"speaker_facing","fov":45}}
                        """))));
                DialogueCameraController.synchronize();
                var pose = DialogueCameraController.update(minecraft.gameRenderer.getMainCamera(), 1);
                require(pose != null && Math.abs(pose.fov() - beforeCut) < 1, "a default transition starts at the visible FOV");
                double afterCut = computeFov(true, 96);
                require(Math.abs(afterCut - beforeCut) < 1, "following line eases from the visible FOV instead of cutting: " + beforeCut + " -> " + afterCut);
                shownAt = Util.getMillis();
                phase = 10;
            }
        } else if (phase == 10 && minecraft.screen instanceof VillagerInteractionScreen screen && Util.getMillis() - shownAt > 600) {
            capture("speaker-facing-" + perspective);
            if (perspective == 0) {
                verifyFovTransitions(screen);
                if (Boolean.getBoolean(OUTPUT + ".fovOnly")) {
                    try { Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Dialogue camera client smoke passed (FOV checks only)" + System.lineSeparator()); }
                    catch (IOException exception) { throw new UncheckedIOException(exception); }
                    phase = 9;
                    minecraft.stop();
                    return;
                }
            }
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
                                        {"camera":{"preset":"speaker_facing","fov":55,"transition":{"type":"cut"}}}
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
            verifyTradeLayout();
            tradeResourceReload = prepareTradeResourcePack();
            tradeGuiScale = minecraft.options.guiScale().get();
            minecraft.options.guiScale().set(1);
            minecraft.resizeDisplay();
            preparing = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var villager = (Villager) server.overworld().getEntity(entityId);
                villager.setTradingPlayer(player);
                var offers = new net.minecraft.world.item.trading.MerchantOffers();
                offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(
                        net.minecraft.world.item.Items.EMERALD, 1), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD), 10, 1, 0));
                villager.getOffers().clear();
                villager.getOffers().addAll(offers);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.jvn.villagerretaliation.network.ServerConfigSyncPayload(true, true, true, true,
                                com.jvn.villagerretaliation.config.VillagerStatDisplayMode.PARTY_ONLY));
                villager.openTradingScreen(player, net.minecraft.network.chat.Component.literal("Camera trade"), 1);
                com.jvn.villagerretaliation.trade.VillagerTradeRefreshService.sendState(player, villager);
                return entityId;
            });
            shownAt = Util.getMillis();
            phase = 12;
        } else if (phase == 12 && preparing.isDone() && tradeResourceReload.isDone()
                && minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen screen
                && Util.getMillis() - shownAt > 1500) {
            tradeResourceReload.join();
            if (minecraft.getOverlay() != null) return;
            if (tradePackReadyAt == 0) tradePackReadyAt = Util.getMillis();
            if (Util.getMillis() - tradePackReadyAt < 600) return;
            require(screen.getMenu().getOffers().size() == 1, "trade offers arrive through the normal merchant menu");
            require(DialogueCameraController.active(), "trading uses the shared camera when both halves fit");
            verifyTradeFraming(screen);
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                int pixel = image.getPixelRGBA(screen.getGuiLeft() + 274, screen.getGuiTop() + 2);
                require(pixel == 0xFF12CD34,
                        "high-resolution resource packs supply the shifted vanilla trading background: " + Integer.toHexString(pixel));
            }
            verifyRecipeViewer(screen, true);
            capture("trade-split");
            tradeFov = currentTradeFov();
            require(tradeFov < 95, "distant merchants receive a dynamic zoom");
            preparing = server.submit(() -> {
                var villager = (Villager) server.overworld().getEntity(entityId);
                villager.setPos(15.5, 100, 16.5);
                for (var position : BlockPos.betweenClosed(12, 100, 13, 19, 105, 14))
                    server.overworld().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                server.overworld().getEntity(blockers.join()).discard();
                return entityId;
            });
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            minecraft.options.guiScale().set(2);
            minecraft.resizeDisplay();
            shownAt = Util.getMillis();
            phase = 17;
        } else if (phase == 17 && preparing.isDone()
                && minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen screen
                && Util.getMillis() - shownAt > 1800) {
            capture("trade-close");
            verifyTradeFraming(screen);
            tradeFov = currentTradeFov();
            var villager = minecraft.level.getEntity(entityId);
            double distance = minecraft.player.getEyePosition().distanceTo(
                    com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.focus(villager, 1));
            double expected = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.targetFov(96, distance, villager.getBbHeight());
            require(Math.abs(tradeFov - expected) < 0.2 && tradeFov < 90,
                    "the rendered FOV changes even within two blocks: actual=" + tradeFov + ", expected=" + expected);
            require(minecraft.gameRenderer.getMainCamera().getPosition().distanceTo(minecraft.player.getEyePosition()) > 0.5,
                    "the camera pulls back instead of overfilling the screen at close range");
            tradeLeft = screen.getGuiLeft();
            tradeWidgetXs = screen.children().stream()
                    .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                    .map(child -> ((net.minecraft.client.gui.components.AbstractWidget) child).getX()).toList();
            tradeCameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
            tradeMovementStep = 0;
            moveTradingVillager();
            shownAt = Util.getMillis();
            phase = 19;
        } else if (phase == 19 && minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen screen) {
            com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.of(screen).update(screen);
            require(screen.getGuiLeft() == tradeLeft, "villager movement cannot drag or swap the trading menu");
            require(screen.children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                            .map(child -> ((net.minecraft.client.gui.components.AbstractWidget) child).getX()).toList().equals(tradeWidgetXs),
                    "trade buttons stay fixed while the camera tracks a moving villager");
            require(DialogueCameraController.active(), "the camera continues tracking a pushed villager");
            checkRange();
            tradeCameraMoved |= minecraft.gameRenderer.getMainCamera().getPosition().distanceTo(tradeCameraPosition) > 0.1;
            if (!preparing.isDone() || Util.getMillis() - shownAt < 500) return;
            preparing.join();
            if (++tradeMovementStep < 4) moveTradingVillager();
            else {
                require(tradeCameraMoved, "the camera follows movement while the menu stays anchored");
                phase = 20;
            }
            shownAt = Util.getMillis();
        } else if (phase == 20 && minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen screen
                && Util.getMillis() - shownAt > 1800) {
            require(screen.getGuiLeft() == tradeLeft, "the menu remains anchored after movement settles");
            verifyTradeFraming(screen);
            capture("trade-after-movement");
            VillagerCameraEntityClientChecks.verify(screen, entityId);
            verifyTradeCorrections(screen);
            shownAt = Util.getMillis();
            phase = 21;
        } else if (phase == 21 && minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen screen
                && Util.getMillis() - shownAt > 1800) {
            verifyTradeFraming(screen);
            tradeFov = currentTradeFov();
            minecraft.player.closeContainer();
            shownAt = Util.getMillis();
            phase = 15;
        } else if (phase == 15 && Util.getMillis() - shownAt > 150) {
            require(DialogueCameraController.active(), "closing trading keeps the camera alive during its return");
            double returningFov = currentTradeFov();
            require(returningFov > tradeFov && returningFov < 96,
                    "the visible FOV interpolates back to normal after closing: " + returningFov);
            phase = 16;
        } else if (phase == 16 && Util.getMillis() - shownAt > 1400) {
            require(!DialogueCameraController.active(), "closing trading eventually releases camera control");
            require(Math.abs(currentTradeFov() - 96) < 0.001, "closing restores the configured FOV");
            minecraft.options.guiScale().set(3);
            minecraft.resizeDisplay();
            preparing = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var villager = (Villager) server.overworld().getEntity(entityId);
                villager.setTradingPlayer(player);
                villager.openTradingScreen(player, villager.getDisplayName(), 1);
                com.jvn.villagerretaliation.trade.VillagerTradeRefreshService.sendState(player, villager);
                return entityId;
            });
            shownAt = Util.getMillis();
            phase = 13;
        } else if (phase == 13 && preparing.isDone() && minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen screen
                && Util.getMillis() - shownAt > 1400) {
            require(!DialogueCameraController.active(), "large GUI scales release the split camera");
            require(Math.abs(screen.getGuiLeft() - 20 + 148 - screen.width / 2) <= 1,
                    "large GUI scales center the complete trade panel");
            verifyRecipeViewer(screen, false);
            capture("trade-large-gui-scale");
            minecraft.options.guiScale().set(1);
            minecraft.resizeDisplay();
            VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.set(false);
            shownAt = Util.getMillis();
            phase = 14;
        } else if (phase == 14 && minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen screen
                && Util.getMillis() - shownAt > 500) {
            require(!DialogueCameraController.active(), "the camera accessibility setting also disables trade framing");
            require(Math.abs(screen.getGuiLeft() - 20 + 148 - screen.width / 2) <= 1,
                    "disabled cameras restore centered trading");
            minecraft.setScreen(null);
            minecraft.options.guiScale().set(tradeGuiScale);
            minecraft.resizeDisplay();
            VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.set(true);
            DialogueCameraController.clear();
            minecraft.options.guiScale().set(1);
            minecraft.resizeDisplay();
            SmithRepairClientChecks.start(entityId);
            phase = 18;
        } else if (phase == 18 && SmithRepairClientChecks.tick()) {
            ClericHealingClientChecks.start();
            phase = 19;
        } else if (phase == 19 && ClericHealingClientChecks.tick()) {
            minecraft.options.guiScale().set(tradeGuiScale);
            minecraft.resizeDisplay();
            try { Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Dialogue camera client smoke passed" + System.lineSeparator()); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            phase = 9;
            minecraft.stop();
        }
    }

    private static void verifyTradeCorrections(net.minecraft.client.gui.screens.inventory.MerchantScreen screen) {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();
        var beforeReload = DialogueCameraController.renderedPose();
        minecraft.levelRenderer.allChanged();
        require(DialogueCameraController.active(), "renderer rebuilds keep the trade camera active");
        require(DialogueCameraController.renderedPose().equals(beforeReload), "renderer rebuilds preserve the framed camera pose");
        require(DialogueCameraOcclusion.snapshot(minecraft.level).blocks().isEmpty(), "renderer rebuilds clear stale blocker masks");
        Vec3 before = DialogueCameraController.update(camera, 1).position();
        BlockPos obstruction = BlockPos.containing(before);
        var originalBlock = minecraft.level.getBlockState(obstruction);
        try {
            minecraft.level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
            Vec3 destination = DialogueCameraOcclusion.placement(before, minecraft.player.getEyePosition(),
                    List.of(minecraft.level.getEntity(entityId)), 1, true);
            require(destination != null && destination.distanceTo(before) > 0.1,
                    "ignoring passing entities retains solid-block collision handling");
            var corrected = DialogueCameraController.update(camera, 1);
            require(corrected != null && corrected.position().distanceTo(before) < 0.1,
                    "collision corrections ease into the camera instead of snapping after smoothing");
        } finally {
            minecraft.level.setBlock(obstruction, originalBlock, 3);
        }
        var layout = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.of(screen);
        var villager = minecraft.level.getEntity(entityId);
        Vec3 originalPosition = villager.position();
        try {
            var beforeStall = DialogueCameraController.renderedPose();
            villager.setPos(originalPosition.add(1.5, 0, 0));
            elapseCameraTime(2);
            var afterStall = DialogueCameraController.update(camera, 1);
            require(afterStall != null && afterStall.position().distanceTo(beforeStall.position()) < 0.3
                            && Math.abs(net.minecraft.util.Mth.wrapDegrees(afterStall.yaw() - beforeStall.yaw())) < 10,
                    "a delayed frame cannot skip the trade camera transition");
            villager.setPos(originalPosition);
            layout.update(screen);
            int beforeLeft = screen.getGuiLeft();
            elapseLayoutTime(layout, 2);
            villager.setPos(minecraft.player.position().add(0, 0, 8.1));
            layout.update(screen);
            require(!layout.split(), "merchants beyond eight blocks release split framing");
            int centeredLeft = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.left(
                    screen.width, screen.getXSize(), 20, false, false);
            require(Math.abs(screen.getGuiLeft() - beforeLeft) < Math.abs(centeredLeft - beforeLeft) / 2,
                    "a delayed frame cannot snap the menu to the center");
            for (double distance : new double[] {7.9, 8.1, 7.5}) {
                villager.setPos(minecraft.player.position().add(0, 0, distance));
                layout.update(screen);
                require(!layout.split(), "small pushes around the range boundary cannot toggle split framing");
            }
            villager.setPos(minecraft.player.position().add(0, 0, 6.9));
            layout.update(screen);
            require(layout.split(), "split framing resumes once the villager crosses the re-entry deadzone");
        } finally {
            villager.setPos(originalPosition);
            layout.update(screen);
        }
    }

    private static void elapseCameraTime(double seconds) {
        try {
            var field = DialogueCameraController.class.getDeclaredField("lastNanos");
            field.setAccessible(true);
            field.setLong(null, System.nanoTime() - (long) (seconds * 1_000_000_000));
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void elapseLayoutTime(com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout layout, double seconds) {
        try {
            var field = layout.getClass().getDeclaredField("lastLayoutNanos");
            field.setAccessible(true);
            field.setLong(layout, System.nanoTime() - (long) (seconds * 1_000_000_000));
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void moveTradingVillager() {
        var minecraft = Minecraft.getInstance();
        int step = tradeMovementStep;
        preparing = minecraft.getSingleplayerServer().submit(() -> {
            var villager = (Villager) minecraft.getSingleplayerServer().overworld().getEntity(entityId);
            Vec3 destination = switch (step) {
                case 0 -> new Vec3(17, 100, 16.5);
                case 1 -> new Vec3(14, 100, 16.5);
                case 2 -> new Vec3(15.5, 100, 14.5);
                default -> new Vec3(15.5, 100, 16.5);
            };
            villager.setPos(destination);
            return entityId;
        });
    }

    private static void verifyTradeFraming(net.minecraft.client.gui.screens.inventory.MerchantScreen screen) {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();
        var villager = minecraft.level.getEntity(entityId);
        Vec3 relative = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.focus(villager, 1).subtract(camera.getPosition());
        Vec3 forward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        Vec3 horizontal = Vec3.directionFromRotation(0, camera.getYRot() + 90);
        Vec3 up = horizontal.cross(forward);
        double tangent = Math.tan(Math.toRadians(currentTradeFov()) / 2);
        double aspect = (double) minecraft.getWindow().getWidth() / minecraft.getWindow().getHeight();
        double merchantX = (1 + relative.dot(horizontal) / (relative.dot(forward) * aspect * tangent)) * screen.width / 2;
        double panelCenter = screen.getGuiLeft() - 20 + 148;
        boolean right = panelCenter > screen.width / 2.0;
        require(Math.abs(panelCenter - screen.width * (right ? 0.625 : 0.375)) <= 1,
                "the menu is anchored halfway between the intended villager framing and the opposite edge");
        require(right ? merchantX >= 0 && merchantX < screen.width * 0.5 : merchantX > screen.width * 0.5 && merchantX <= screen.width,
                "the camera keeps the villager in the half opposite the anchored menu: projected=" + merchantX);
        for (double height : new double[] {0, villager.getBbHeight()}) {
            Vec3 point = villager.position().add(0, height, 0).subtract(camera.getPosition());
            double projectedY = point.dot(up) / (point.dot(forward) * tangent);
            require(Math.abs(projectedY) < 0.95, "the villager's head and feet fit in the rendered viewport: projected=" + projectedY
                    + ", height=" + height + ", camera=" + camera.getPosition() + ", entity=" + villager.position()
                    + ", fov=" + currentTradeFov() + ", rotation=" + camera.getYRot() + "/" + camera.getXRot());
        }
        require(com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.of(screen).split(),
                "the immersive layout remains enabled when both sides fit");
    }

    private static double currentTradeFov() {
        try {
            var field = DialogueCameraController.class.getDeclaredField("renderedFov");
            field.setAccessible(true);
            return field.getDouble(null);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void verifyRecipeViewer(net.minecraft.client.gui.screens.Screen screen, boolean hidden) {
        try {
            if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
                var type = Class.forName("dev.emi.emi.screen.EmiScreenBase");
                var base = type.getMethod("of", net.minecraft.client.gui.screens.Screen.class).invoke(null, screen);
                require((boolean) type.getMethod("isEmpty").invoke(base) == hidden,
                        "EMI is suppressed only for immersive trading");
            }
            if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
                var runtime = Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
                var helper = Class.forName("mezz.jei.api.runtime.IJeiRuntime").getMethod("getScreenHelper").invoke(runtime);
                var getProperties = Class.forName("mezz.jei.api.runtime.IScreenHelper")
                        .getMethod("getGuiProperties", net.minecraft.client.gui.screens.Screen.class);
                var properties = (java.util.Optional<?>) getProperties.invoke(helper, screen);
                var inventory = new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player);
                inventory.width = screen.width;
                inventory.height = screen.height;
                var standardProperties = (java.util.Optional<?>) getProperties.invoke(helper, inventory);
                require(hidden ? properties.isEmpty() : properties.isEmpty() == standardProperties.isEmpty(),
                        "JEI hides immersive trading and restores ordinary container behavior");
            }
            if (net.neoforged.fml.ModList.get().isLoaded("roughlyenoughitems")) {
                var registryType = Class.forName("me.shedaniel.rei.api.client.registry.screen.ScreenRegistry");
                var registry = registryType.getMethod("getInstance").invoke(null);
                var deciders = (java.util.List<?>) registryType.getMethod("getDeciders", net.minecraft.client.gui.screens.Screen.class)
                        .invoke(registry, screen);
                var decision = net.minecraft.world.InteractionResult.PASS;
                for (var decider : deciders) {
                    decision = (net.minecraft.world.InteractionResult) Class.forName("me.shedaniel.rei.api.client.registry.screen.OverlayDecider")
                            .getMethod("shouldScreenBeOverlaid", net.minecraft.client.gui.screens.Screen.class).invoke(decider, screen);
                    if (decision != net.minecraft.world.InteractionResult.PASS) break;
                }
                require((decision == net.minecraft.world.InteractionResult.FAIL) == hidden, "REI is suppressed only for immersive trading");
            }
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Recipe viewer fixture is unavailable", exception); }
    }

    private static java.util.concurrent.CompletableFuture<Void> prepareTradeResourcePack() {
        var minecraft = Minecraft.getInstance();
        Path pack = Path.of(System.getProperty(OUTPUT), "resourcepacks", "trade-smoke");
        Path texture = pack.resolve("assets/minecraft/textures/gui/container/villager.png");
        try {
            Files.createDirectories(texture.getParent());
            Files.writeString(pack.resolve("pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":34,\"description\":\"Trade smoke fixture\"}}");
            var location = net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/gui/container/villager.png");
            try (var stream = minecraft.getResourceManager().getResourceOrThrow(location).open();
                    var source = com.mojang.blaze3d.platform.NativeImage.read(stream);
                    var image = new com.mojang.blaze3d.platform.NativeImage(1024, 512, false)) {
                for (int y = 0; y < 512; y++) {
                    for (int x = 0; x < 1024; x++) {
                        int color = x / 2 >= 270 && x / 2 < 276 && y / 2 < 6
                                ? 0xFF12CD34 : source.getPixelRGBA(x / 2, y / 2);
                        image.setPixelRGBA(x, y, color);
                    }
                }
                image.writeToFile(texture);
            }
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
        minecraft.getResourcePackRepository().reload();
        var selected = new java.util.ArrayList<>(minecraft.getResourcePackRepository().getSelectedIds());
        selected.add("file/trade-smoke");
        minecraft.getResourcePackRepository().setSelected(selected);
        return minecraft.reloadResourcePacks();
    }

    private static void verifyTradeLayout() {
        var minecraft = Minecraft.getInstance();
        var menu = new net.minecraft.world.inventory.MerchantMenu(123, minecraft.player.getInventory());
        var offers = new net.minecraft.world.item.trading.MerchantOffers();
        offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(
                        net.minecraft.world.item.Items.EMERALD, 1), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD), 10, 1, 0));
        while (offers.size() < 9) offers.add(offers.getFirst());
        menu.setOffers(offers);
        var screen = new net.minecraft.client.gui.screens.inventory.MerchantScreen(menu,
                minecraft.player.getInventory(), net.minecraft.network.chat.Component.literal("Trade layout"));
        minecraft.setScreen(screen);
        for (int width : new int[] {320, 426, 640, 854, 1280}) {
            screen.resize(minecraft, width, 360);
            com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.of(screen).update(screen);
            require(screen.getGuiLeft() >= 20 && screen.getGuiLeft() + screen.getXSize() <= width,
                    "trade background and refresh controls stay inside the scaled viewport");
            var button = (net.minecraft.client.gui.components.AbstractWidget) screen.children().getFirst();
            require(button.getX() == screen.getGuiLeft() + 5, "trade buttons follow the container origin after resizing");
            screen.mouseClicked(screen.getGuiLeft() + 97, screen.getGuiTop() + 30, 0);
            require(screen.mouseDragged(screen.getGuiLeft() + 97, screen.getGuiTop() + 150, 0, 0, 120),
                    "the scrollbar accepts dragging at the shifted origin");
            screen.mouseReleased(screen.getGuiLeft() + 97, screen.getGuiTop() + 150, 0);
            for (boolean right : new boolean[] {false, true}) {
                boolean split = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.fitsHalf(width, 360, 276, 166, 20);
                int left = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.left(width, 276, 20, right, split);
                if (split) {
                    require(Math.abs(left - 20 + 148 - width * (right ? 0.625 : 0.375)) <= 1,
                            "the extended panel is centered between the villager and the opposite edge");
                    double merchantX = width * (right ? 0.35 : 0.65);
                    left = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.left(width, 276, 20, right, true, merchantX);
                    require(Math.abs(left - 20 + 148 - (merchantX + (right ? width : 0)) / 2) <= 1,
                            "the anchor follows the villager's projected screen position on either side");
                }
            }
        }
        for (double aspect : new double[] {4.0 / 3, 16.0 / 9, 21.0 / 9}) {
            for (float pitch : new float[] {-30, 0, 30}) {
                for (boolean right : new boolean[] {false, true}) {
                    var rotation = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.rotation(0, pitch, 55, aspect, right);
                    Vec3 target = Vec3.directionFromRotation(pitch, 0);
                    Vec3 forward = Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
                    Vec3 horizontal = Vec3.directionFromRotation(0, rotation.yaw() + 90);
                    Vec3 up = horizontal.cross(forward);
                    double projectedX = target.dot(horizontal) / target.dot(forward) / (aspect * Math.tan(Math.toRadians(55) / 2));
                    require(Math.abs(projectedX - (right ? -0.5 : 0.5)) < 0.001,
                            "the villager projects into the half opposite the trade panel");
                    require(Math.abs(target.dot(up)) < 0.001, "trade framing keeps the villager vertically centered");
                }
            }
        }
        for (double fov : new double[] {40, 70, 96, 110}) {
            double near = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.targetFov(fov, 1, 1.95);
            double far = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.targetFov(fov, 5, 1.95);
            require(near <= fov && (fov <= 40 || near < fov), "near merchants keep a gentle zoom without widening narrow FOV settings");
            require(far <= near && far >= Math.min(55, fov), "distant zoom respects the configured FOV");
            for (double distance : new double[] {1, 2, 3, 5}) {
                double before = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.targetFov(fov, distance - 0.0001, 1.95);
                double after = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.targetFov(fov, distance + 0.0001, 1.95);
                require(Math.abs(after - before) < 0.01, "distance-based zoom has no threshold jumps");
            }
            double minimum = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.minimumDistance(1.95, near, 16.0 / 9, 0.6);
            var aim = new Vec3(0, 1.17, 0);
            var eye = new Vec3(0, 1.62, -0.5);
            var pulled = com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.cameraPosition(eye, aim, minimum, 0);
            require(pulled.distanceTo(aim) >= minimum - 0.001 && pulled.y == eye.y,
                    "close-range pullback keeps the villager in frame without changing camera height");
            eye = new Vec3(0, 1.62, -8);
            require(com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.cameraPosition(eye, aim, minimum, 0).equals(eye),
                    "distant merchants do not pull the camera back unnecessarily");
        }
        require(!com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout.fitsHalf(1280, 170, 276, 166, 20),
                "short windows use the centered fallback");
        screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        capture("trade-layout");
        minecraft.setScreen(null);
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
                {"camera":{"preset":"custom","anchor":"speaker","offset":[0,1,-2],"perspective":"third_person","placement":"fixed","transition":{"type":"cut"}}}
                """))));
        DialogueCameraController.synchronize();
        var fixed = DialogueCameraController.update(renderer.getMainCamera(), 1);
        Vec3 original = speaker.position();
        speaker.setPos(original.add(1, 0, 0));
        var moved = DialogueCameraController.update(renderer.getMainCamera(), 1);
        require(moved.position().distanceTo(fixed.position()) < 0.001, "fixed placements do not follow moving anchors");
        speaker.setPos(original);
        screen.acceptDialogueExchanges(List.of(line(camera("""
                {"camera":{"preset":"custom","anchor":"speaker","offset":[0,1,-2],"perspective":"third_person","transition":{"type":"cut"}}}
                """))));
        DialogueCameraController.synchronize();
        var relative = DialogueCameraController.update(renderer.getMainCamera(), 1);
        speaker.setPos(original.add(1, 0, 0));
        moved = DialogueCameraController.update(renderer.getMainCamera(), 1);
        require(Math.abs(moved.position().x - relative.position().x - 1) < 0.001, "relative placements follow moving anchors");
        speaker.setPos(original);
        screen.acceptDialogueExchanges(List.of(line(camera("""
                {"camera":{"preset":"custom","anchor":"player","offset":[0,8,8],"perspective":"third_person","placement":"fixed","transition":{"type":"cut"}}}
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
                {"camera":{"preset":"telescope","transition":{"type":"cut"}}}
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

    private static void verifyFovTransitions(VillagerInteractionScreen screen) {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();
        screen.acceptDialogueExchanges(List.of(line(camera("""
                {"camera":{"preset":"speaker_facing","fov":30,"transition":{"type":"cut"}}}
                """))));
        DialogueCameraController.update(camera, 1);
        double zoomed = computeFov(true, 96);
        require(zoomed == 30, "explicit cuts change the visible FOV immediately");
        screen.acceptDialogueExchanges(List.of(line(camera("""
                {"camera":{"preset":"speaker_facing","fov":80}}
                """))));
        DialogueCameraController.update(camera, 1);
        require(Math.abs(computeFov(true, 96) - zoomed) < 1, "skipping preserves the visible zoom");
        elapseCameraTime(0.125);
        DialogueCameraController.update(camera, 1);
        double widening = computeFov(true, 96);
        require(Math.abs(widening - 55) < 1, "default transitions ease toward the next line's FOV");
        computeFov(false, 70);
        require(currentTradeFov() == widening, "held-item FOV events cannot overwrite the visible world FOV");

        screen.acceptDialogueExchanges(List.of(line(camera("""
                {"camera":{"preset":"speaker_facing","fov":50,"transition":{"type":"lerp","duration_seconds":2}}}
                """))));
        DialogueCameraController.synchronize();
        require(Math.abs(DialogueCameraController.update(camera, 1).fov() - widening) < 1,
                "an authored lerp begins at the visible zoom when skipping a cut");
        double beforeLerp = computeFov(true, 96);
        elapseCameraTime(1);
        var halfway = DialogueCameraController.update(camera, 1);
        require(Math.abs(halfway.fov() - (beforeLerp + 50) / 2) < 1, "authored FOV lerps retain their duration");
        require(Math.abs(computeFov(true, 96) - halfway.fov()) < 0.001, "authored lerps are not smoothed twice");

        screen.acceptDialogueExchanges(List.of(line(camera("""
                {"camera":{"target":"speaker","zoom":0.1}}
                """))));
        DialogueCameraController.synchronize();
        double beforeOrdinary = currentTradeFov();
        require(Math.abs(computeFov(true, 96) - beforeOrdinary) < 1, "ordinary focus starts at the visible authored FOV");
        for (int frame = 0; frame < 30; frame++) {
            elapseFovTime();
            computeFov(true, 96);
        }
        double ordinary = currentTradeFov();
        screen.acceptDialogueExchanges(List.of(line(camera("""
                {"camera":{"target":"speaker","zoom":0}}
                """))));
        require(Math.abs(computeFov(true, 96) - ordinary) < 1, "a zero-zoom line eases instead of releasing instantly");
        elapseFovTime();
        double releasing = computeFov(true, 96);
        require(releasing > ordinary && releasing < 96, "ordinary focus eases back toward the player FOV");
        var state = ClientVillagerConversationState.snapshot();
        var previousScreen = minecraft.screen;
        try {
            ClientVillagerConversationState.clear();
            minecraft.screen = null;
            double beforeClose = currentTradeFov();
            require(Math.abs(computeFov(true, 96) - beforeClose) < 1, "closing preserves the visible FOV");
            for (int frame = 0; frame < 60; frame++) {
                ClientVillagerConversationState.tickCameraRelease();
                elapseFovTime();
                computeFov(true, 96);
            }
            require(Math.abs(currentTradeFov() - 96) < 0.001, "closing restores the player's configured FOV");
        } finally {
            minecraft.screen = previousScreen;
            ClientVillagerConversationState.restore(state);
        }
    }

    private static double computeFov(boolean configured, double fov) {
        var minecraft = Minecraft.getInstance();
        var event = new net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov(
                minecraft.gameRenderer, minecraft.gameRenderer.getMainCamera(), 1, fov, configured);
        VillagerDialogueCameraFocus.onComputeFov(event);
        return event.getFOV();
    }

    private static void elapseFovTime() {
        try {
            var field = VillagerDialogueCameraFocus.class.getDeclaredField("fovTransition");
            field.setAccessible(true);
            var transition = field.get(null);
            if (transition == null) return;
            field = transition.getClass().getDeclaredField("lastNanos");
            field.setAccessible(true);
            field.setLong(transition, System.nanoTime() - 50_000_000L);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
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
