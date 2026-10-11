package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.inventory.SmithRepairScreen;
import com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import com.jvn.villagerretaliation.inventory.SmithRepairMenu;
import com.jvn.villagerretaliation.network.VillagerRepairRequestPayload;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

final class SmithRepairClientChecks {
    private static CompletableFuture<Integer> preparing;
    private static int phase;
    private static long shownAt;
    private static int entityId;
    private static boolean sawHammering;
    private static boolean sawApproach;
    private static boolean sawReturn;
    private static long workingAt;

    private SmithRepairClientChecks() {
    }

    static void start(int smithId) {
        entityId = smithId;
        var minecraft = Minecraft.getInstance();
        var playerId = minecraft.player.getUUID();
        preparing = minecraft.getSingleplayerServer().submit(() -> {
            var server = minecraft.getSingleplayerServer();
            var player = server.getPlayerList().getPlayer(playerId);
            player.closeContainer();
            var smith = (Villager) server.overworld().getEntity(entityId);
            smith.setTradingPlayer(null);
            smith.setVillagerData(smith.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
            smith.setVillagerXp(10);
            smith.setPos(player.getX() + 1, player.getY(), player.getZ() + 3);
            smith.setNoAi(true);
            var station = smith.blockPosition().south(4);
            server.overworld().setBlockAndUpdate(station, net.minecraft.world.level.block.Blocks.SMITHING_TABLE.defaultBlockState());
            smith.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
                    net.minecraft.core.GlobalPos.of(server.overworld().dimension(), station));
            player.getInventory().clearContent();
            player.getInventory().setItem(0, VillagerCurrencyResources.createStack(server, 64));
            require(VillagerConversationService.start(player, smith), "smith conversation starts");
            VillagerInteractionScreenOpener.openNormal(player, smith, false);
            return entityId;
        });
        shownAt = Util.getMillis();
        phase = 0;
        sawHammering = false;
        sawApproach = false;
        sawReturn = false;
        workingAt = 0;
    }

    static boolean tick() {
        var minecraft = Minecraft.getInstance();
        if (phase >= 2 && phase <= 4 && preparing.isDone())
            require(minecraft.screen instanceof SmithRepairScreen, "repair screen remains open while the smith works and returns");
        if (phase == 3 && minecraft.screen instanceof SmithRepairScreen screen) {
            var smith = (Villager) minecraft.level.getEntity(entityId);
            if (screen.getMenu().status() == SmithRepairMenu.Status.APPROACHING) {
                require(!com.jvn.villagerretaliation.repair.SmithRepairAnimationState.isWorking(smith), "approaching smith is not hammering");
                require(screen.getMenu().isBusy(), "approach protects the accepted item");
                sawApproach = true;
            } else if (screen.getMenu().status() == SmithRepairMenu.Status.WORKING && workingAt == 0) {
                workingAt = Util.getMillis();
            } else if (screen.getMenu().status() == SmithRepairMenu.Status.RETURNING
                    && !com.jvn.villagerretaliation.repair.SmithRepairAnimationState.isWorking(smith)) {
                sawReturn = true;
            }
        }
        if (phase == 2 && preparing.isDone() && minecraft.screen instanceof SmithRepairScreen screen && Util.getMillis() - shownAt > 5000) {
            capture("repair-not-ready");
            var smith = (Villager) minecraft.level.getEntity(entityId);
            require(screen.getMenu().status() == SmithRepairMenu.Status.READY,
                    "repair becomes ready: " + screen.getMenu().status() + ", " + smith.position() + ", " + smith.getVillagerData().getProfession());
        }
        if (phase == 0 && preparing.isDone() && minecraft.screen instanceof VillagerInteractionScreen) {
            preparing.join();
            if (Util.getMillis() - shownAt < 1200) return false;
            capture("repair-interaction");
            PacketDistributor.sendToServer(new VillagerRepairRequestPayload(entityId));
            shownAt = Util.getMillis();
            phase = 1;
        } else if (phase == 1 && minecraft.screen instanceof SmithRepairScreen screen && Util.getMillis() - shownAt > 1000) {
            require(VillagerTradeScreenLayout.of(screen).split(), "repair screen uses the split trading layout");
            require(DialogueCameraController.active(), "smith camera is active");
            require(VillagerTradeScreenLayout.hidesRecipeViewer(screen), "repair screen suppresses recipe overlays");
            require(screen.getMenu().status() == SmithRepairMenu.Status.EMPTY, "empty repair menu synchronized");
            require(screen.getMenu().getSlot(1).y == 47, "material slot follows old smithing texture");
            require(button(screen).getY() == screen.getGuiTop() + 68, "repair button sits below the smithing slots");
            VillagerCameraEntityClientChecks.verify(screen, entityId);
            capture("repair-empty");
            var playerId = minecraft.player.getUUID();
            preparing = minecraft.getSingleplayerServer().submit(() -> {
                var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                var menu = (SmithRepairMenu) player.containerMenu;
                ((Villager) player.serverLevel().getEntity(entityId)).setNoAi(false);
                ItemStack item = new ItemStack(Items.DIAMOND_PICKAXE);
                item.setDamageValue(1000);
                menu.getSlot(0).set(item);
                menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 4));
                menu.broadcastChanges();
                return entityId;
            });
            shownAt = Util.getMillis();
            phase = 2;
        } else if (phase == 2 && preparing.isDone() && minecraft.screen instanceof SmithRepairScreen screen
                && screen.getMenu().status() == SmithRepairMenu.Status.READY && Util.getMillis() - shownAt > 700) {
            preparing.join();
            require(screen.getMenu().cost() == 6, "client receives specialist price");
            require(screen.getMenu().getSlot(2).getItem().getDamageValue() == 454, "client receives repaired preview");
            require(button(screen).active, "repair button enabled");
            require(button(screen).getX() == screen.getGuiLeft() + 26, "button follows split panel origin");
            capture("repair-split");
            button(screen).onPress();
            shownAt = Util.getMillis();
            phase = 3;
        } else if (phase == 3 && minecraft.screen instanceof SmithRepairScreen screen
                && screen.getMenu().status() == SmithRepairMenu.Status.WORKING
                && !sawHammering && workingAt != 0 && Util.getMillis() - workingAt > 1200) {
            require(screen.getMenu().getSlot(0).getItem().getDamageValue() == 1000, "client item stays damaged during hammering");
            require(screen.getMenu().getSlot(1).getItem().getCount() == 4, "client materials stay intact during hammering");
            require(!button(screen).active, "working smith disables repeat requests");
            capture("repair-hammering");
            verifyPose((Villager) minecraft.level.getEntity(entityId));
            sawHammering = true;
        } else if (phase == 3 && minecraft.screen instanceof SmithRepairScreen screen
                && screen.getMenu().getSlot(0).getItem().getDamageValue() == 454
                && screen.getMenu().status() == SmithRepairMenu.Status.READY && Util.getMillis() - shownAt > 500) {
            require(sawApproach, "client observed the walk to the workstation");
            require(sawHammering, "client observed the workstation animation before completion");
            require(sawReturn, "client observed the walk back to the player");
            require(screen.getMenu().getSlot(1).getItem().getCount() == 3, "material consumption synchronized");
            capture("repair-after");
            button(screen).onPress();
            shownAt = Util.getMillis();
            phase = 4;
        } else if (phase == 4 && minecraft.screen instanceof SmithRepairScreen screen
                && screen.getMenu().status() == SmithRepairMenu.Status.FULL && Util.getMillis() - shownAt > 500) {
            require(!button(screen).active, "fully repaired item disables button");
            require(!screen.getMenu().getSlot(2).hasItem(), "fully repaired preview is empty");
            screen.resize(minecraft, 320, 240);
            VillagerTradeScreenLayout.of(screen).update(screen);
            require(!VillagerTradeScreenLayout.of(screen).split(), "narrow screens use centered fallback");
            require(screen.getGuiLeft() == 72, "fallback panel is centered");
            require(button(screen).getX() == 98, "button follows resized panel");
            screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
            VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.set(false);
            shownAt = Util.getMillis();
            phase = 5;
        } else if (phase == 5 && minecraft.screen instanceof SmithRepairScreen screen && Util.getMillis() - shownAt > 1200) {
            require(!DialogueCameraController.active(), "camera setting releases smith camera");
            require(Math.abs(screen.getGuiLeft() + 88 - screen.width / 2) <= 1, "disabled camera centers repairs");
            capture("repair-centered");
            minecraft.player.closeContainer();
            VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.set(true);
            shownAt = Util.getMillis();
            phase = 6;
        } else if (phase == 6 && minecraft.screen == null && Util.getMillis() - shownAt > 1200) {
            require(!DialogueCameraController.active(), "closing repairs releases camera");
            return true;
        }
        return false;
    }

    private static void verifyPose(Villager villager) {
        require(com.jvn.villagerretaliation.repair.SmithRepairAnimationState.isWorking(villager), "repair state synchronizes to the client");
        var station = ((com.jvn.villagerretaliation.repair.SmithRepairAnimationState) villager).villagerretaliation$repairStation();
        require(Math.abs(villager.getY() - station.getY()) < 0.1, "smith hammers at ground level beside the workstation");
        require(!villager.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(station)), "smith does not stand on the workstation");
        var minecraft = Minecraft.getInstance();
        var state = (com.jvn.villagerretaliation.repair.SmithRepairAnimationState) villager;
        var forward = net.minecraft.world.phys.Vec3.atCenterOf(state.villagerretaliation$repairStation())
                .subtract(villager.position()).multiply(1, 0, 1).normalize();
        require(minecraft.gameRenderer.getMainCamera().getPosition().subtract(villager.position()).dot(forward) > 0.25,
                "repair camera shows the workstation in front of the smith: camera=" + minecraft.gameRenderer.getMainCamera().getPosition()
                        + ", smith=" + villager.position() + ", forward=" + forward);
        var location = net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                com.jvn.villagerretaliation.VillagerRetaliation.id("item/smithing_hammer"));
        require(minecraft.getModelManager().getModel(location) != minecraft.getModelManager().getMissingModel(), "extracted hammer model is baked");
        var hammer = minecraft.getModelManager().getModel(location);
        var sword = minecraft.getItemRenderer().getModel(new ItemStack(Items.IRON_SWORD), villager.level(), villager, villager.getId());
        for (var context : new net.minecraft.world.item.ItemDisplayContext[] {
                net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND}) {
            var hammerTransform = hammer.getTransforms().getTransform(context);
            var swordTransform = sword.getTransforms().getTransform(context);
            require(hammerTransform.rotation.equals(swordTransform.rotation)
                    && hammerTransform.translation.equals(swordTransform.translation)
                    && hammerTransform.scale.equals(swordTransform.scale), "hammer uses the sword's normal held-item grip for " + context);
        }
        var model = new com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel<Villager>(
                minecraft.getEntityModels().bakeLayer(com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel.LAYER_LOCATION));
        model.setupAnim(villager, 0, 0, villager.tickCount, 0, 0);
        require(model.getHead().xRot > 0.2F, "smith looks down at the repair item");
        var arm = model.root().getChild(villager.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? "RightArm" : "LeftArm");
        require(Math.abs(arm.yScale - 1) < 0.001, "hammering preserves normal held-item arm proportions");
        require(arm.xRot < -0.4F && arm.xRot >= -1.91F, "hammer follows the normal hand through the smithing swing");
    }

    private static Button button(SmithRepairScreen screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow();
    }

    private static void capture(String name) {
        var minecraft = Minecraft.getInstance();
        Screenshot.grab(Path.of(System.getProperty("villagerretaliation.dialogueCameraClientSmoke")).toFile(),
                name + ".png", minecraft.getMainRenderTarget(), ignored -> { });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
