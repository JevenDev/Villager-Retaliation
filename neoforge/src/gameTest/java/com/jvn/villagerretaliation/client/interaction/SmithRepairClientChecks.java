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
            smith.setPos(player.getX() + 1, player.getY(), player.getZ() + 3);
            smith.setNoAi(true);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, VillagerCurrencyResources.createStack(server, 64));
            require(VillagerConversationService.start(player, smith), "smith conversation starts");
            VillagerInteractionScreenOpener.openNormal(player, smith, false);
            return entityId;
        });
        shownAt = Util.getMillis();
        phase = 0;
    }

    static boolean tick() {
        var minecraft = Minecraft.getInstance();
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
            capture("repair-empty");
            var playerId = minecraft.player.getUUID();
            preparing = minecraft.getSingleplayerServer().submit(() -> {
                var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                var menu = (SmithRepairMenu) player.containerMenu;
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
                && screen.getMenu().getSlot(0).getItem().getDamageValue() == 454 && Util.getMillis() - shownAt > 500) {
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
