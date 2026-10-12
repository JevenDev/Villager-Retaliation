package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.combat.ClericHealingService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import com.jvn.villagerretaliation.network.VillagerHealingStatePayload;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

final class ClericHealingClientChecks {
    private static CompletableFuture<Integer> preparing;
    private static VillagerInteractionScreen healingScreen;
    private static int entityId;
    private static int phase;
    private static long shownAt;

    private ClericHealingClientChecks() {}

    static void start() {
        var minecraft = Minecraft.getInstance();
        var playerId = minecraft.player.getUUID();
        preparing = minecraft.getSingleplayerServer().submit(() -> {
            var server = minecraft.getSingleplayerServer();
            var level = server.overworld();
            var player = server.getPlayerList().getPlayer(playerId);
            VillagerConversationService.endForPlayer(player, false);
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(19.0F);
            player.getFoodData().setFoodLevel(0);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, VillagerCurrencyResources.createStack(server, 64));
            var cleric = EntityType.VILLAGER.create(level);
            cleric.setVillagerData(cleric.getVillagerData().setProfession(VillagerProfession.CLERIC));
            cleric.setVillagerXp(10);
            cleric.setNoAi(true);
            cleric.setPos(player.getX() + 1, player.getY(), player.getZ() + 3);
            level.addFreshEntity(cleric);
            VillagerReputationManager.setReputation(level, cleric, playerId, VillagerRetaliationConfig.RESPECTED_THRESHOLD.get());
            require(VillagerConversationService.start(player, cleric), "cleric conversation starts");
            VillagerInteractionScreenOpener.openNormal(player, cleric, false);
            return cleric.getId();
        });
        shownAt = Util.getMillis();
        phase = 0;
    }

    static boolean tick() {
        var minecraft = Minecraft.getInstance();
        if (!preparing.isDone()) return false;
        preparing.join();
        if (phase == 0 && minecraft.screen instanceof VillagerInteractionScreen screen && Util.getMillis() - shownAt > 1200) {
            entityId = preparing.join();
            healingScreen = screen;
            List<?> buttons = (List<?>) invoke(screen, "interactionMenuButtons");
            int heal = -1;
            for (int i = 0; i < buttons.size(); i++) {
                if (invoke(buttons.get(i), "id").toString().equals("HEAL")) heal = i;
            }
            require(heal > 0 && invoke(buttons.get(heal - 1), "id").toString().equals("TRADE"), "Heal appears immediately after Trade");
            capture("healing-root");
            screen.keyPressed(GLFW.GLFW_KEY_1 + heal, 0, 0);
            shownAt = Util.getMillis();
            phase = 1;
        } else if (phase == 1 && ready(ClericHealingService.Source.FREE)) {
            require(state().healLabel().equals("Heal (Free)"), "free healing label is displayed");
            capture("healing-free");
            healingScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            shownAt = Util.getMillis();
            phase = 2;
        } else if (phase == 2 && !pending() && Util.getMillis() - shownAt > 1800) {
            require(minecraft.screen == healingScreen, "free healing keeps the same interaction screen open");
            preparing = minecraft.getSingleplayerServer().submit(() -> {
                var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
                var cleric = (Villager) player.serverLevel().getEntity(entityId);
                require(VillagerCurrencyPayment.count(player) == 64, "free healing spends no currency");
                require(VillagerConversationService.validate(player, cleric), "free healing preserves the server conversation");
                player.setHealth(10.0F);
                return entityId;
            });
            phase = 3;
        } else if (phase == 3 && ready(ClericHealingService.Source.PAID)) {
            require(state().healLabel().contains("3 emeralds"), "paid price is displayed");
            capture("healing-paid");
            healingScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            shownAt = Util.getMillis();
            phase = 4;
        } else if (phase == 4 && ready(ClericHealingService.Source.PAID) && Util.getMillis() - shownAt > 700) {
            require(minecraft.screen == healingScreen, "paid healing keeps the same screen open");
            preparing = verifyPayment(61);
            phase = 41;
        } else if (phase == 41) {
            healingScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            shownAt = Util.getMillis();
            phase = 5;
        } else if (phase == 5 && !pending() && Util.getMillis() - shownAt > 700) {
            preparing = verifyPayment(58);
            capture("healing-repeat");
            healingScreen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            shownAt = Util.getMillis();
            phase = 51;
        } else if (phase == 51 && !pending() && Util.getMillis() - shownAt > 1500) {
            require((boolean) field(healingScreen, "keyboardOptionFocusVisible"), "availability refresh preserves keyboard focus");
            require(((VillagerInteractionScreenState) field(healingScreen, "state")).selectedOption() == 1, "availability refresh preserves the selected option");
            healingScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            phase = 6;
        } else if (phase == 6) {
            require(minecraft.screen == healingScreen, "nevermind keeps the interaction screen open");
            require(field(healingScreen, "page").toString().equals("ROOT"), "nevermind returns to the root menu");
            healingScreen.onClose();
            phase = 7;
        } else if (phase == 7 && minecraft.screen == null) {
            return true;
        }
        return false;
    }

    private static CompletableFuture<Integer> verifyPayment(int expected) {
        var minecraft = Minecraft.getInstance();
        var playerId = minecraft.player.getUUID();
        return minecraft.getSingleplayerServer().submit(() -> {
            var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            require(VillagerCurrencyPayment.count(player) == expected, "paid healing removes exactly three emeralds");
            require(VillagerConversationService.validate(player, (Villager) player.serverLevel().getEntity(entityId)), "paid healing preserves the server conversation");
            return entityId;
        });
    }

    private static boolean ready(ClericHealingService.Source source) {
        var state = state();
        return !pending() && state != null && state.offer().available() && state.offer().source() == source;
    }

    private static boolean pending() {
        return (boolean) field(healingScreen, "awaitingHealingState");
    }

    private static VillagerHealingStatePayload state() {
        return (VillagerHealingStatePayload) field(healingScreen, "healingState");
    }

    private static Object field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object invoke(Object target, String name) {
        try {
            var method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void capture(String name) {
        var minecraft = Minecraft.getInstance();
        Screenshot.grab(Path.of(System.getProperty("villagerretaliation.dialogueCameraClientSmoke")).toFile(),
                name + ".png", minecraft.getMainRenderTarget(), ignored -> {});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
