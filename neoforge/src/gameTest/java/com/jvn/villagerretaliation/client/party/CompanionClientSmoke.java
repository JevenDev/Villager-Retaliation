package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.party.CompanionRelationshipService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class CompanionClientSmoke {
    private static final String OUTPUT = "villagerretaliation.companionClientSmoke";
    private static CompletableFuture<UUID> prepared;
    private static UUID villagerId;
    private static long started;
    private static int phase;
    private static int ticks;

    private CompanionClientSmoke() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || phase == 5) return;
        var minecraft = Minecraft.getInstance();
        if (started == 0) started = Util.getMillis();
        require(Util.getMillis() - started < 180000L, "Companion client smoke timed out at phase " + phase);
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        var server = minecraft.getSingleplayerServer();
        if (phase == 0 && minecraft.screen == null) {
            phase = 1;
            minecraft.options.guiScale().set(2);
            minecraft.resizeDisplay();
            minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            UUID playerId = minecraft.player.getUUID();
            prepared = CompletableFuture.supplyAsync(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var level = player.serverLevel();
                var villager = EntityType.VILLAGER.create(level);
                require(villager != null, "Villager created");
                villager.setNoAi(true);
                villager.setNoGravity(true);
                villager.moveTo(player.getX() + 1, player.getY(), player.getZ(), 0, 0);
                level.addFreshEntity(villager);
                CompanionRelationshipService.unlock(level, villager.getUUID(), player.getUUID(), true, -1000);
                require(PartyVillagerContractService.recruit(player, villager).success(), "Recruitment through real integrated server");
                require(PartyVillagerContractService.getPartyEndGameTime(level, villager).isEmpty(), "Persistent companion has no rental countdown");
                com.jvn.villagerretaliation.profile.VillagerProfileManager.setSkill(level, villager,
                        com.jvn.villagerretaliation.skill.VillagerSkill.GUARDING, 10);
                require(CompanionRelationshipService.rescued(level, villager, player), "Rescue event makes real story available");
                require(VillagerInventoryAccess.openPreferred(player, villager), "Owner opens existing inventory without reputation gate");
                return villager.getUUID();
            }, server);
        }
        if (phase == 1 && prepared != null && prepared.isDone()) {
            villagerId = prepared.join();
            var roster = PartyRosterClient.roster();
            var entry = roster.villagers().stream().filter(value -> value.villagerId().equals(villagerId)).findFirst().orElse(null);
            if (entry == null || !(minecraft.screen instanceof com.jvn.villagerretaliation.client.inventory.VillagerInventoryScreen)) return;
            require(entry.canCommand(minecraft.player.getUUID()), "Real client receives command authority");
            require(entry.canEquip(minecraft.player.getUUID()), "Real client receives equipment authority");
            require(entry.contractEndGameTime() == -1L, "Party roster hides permanent rental timer");
            phase = 2;
        }
        if (phase == 2 && ++ticks >= 40) {
            var output = Path.of(System.getProperty(OUTPUT));
            try {
                Files.createDirectories(output);
                try (var screenshot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                    screenshot.writeToFile(output.resolve("companion-inventory.png"));
                }
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            phase = 3;
            ticks = 0;
            minecraft.player.closeContainer();
            minecraft.player.connection.sendCommand("vr companion stories " + villagerId);
            minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
        }
        if (phase == 3 && ++ticks >= 40) {
            try (var screenshot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                screenshot.writeToFile(Path.of(System.getProperty(OUTPUT)).resolve("companion-story.png"));
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            minecraft.player.connection.sendCommand("vr companion grow " + villagerId + " villagerretaliation:after_the_fall hold_the_line");
            ticks = 0;
            phase = 4;
        }
        if (phase == 4 && ++ticks == 20) {
            UUID playerId = minecraft.player.getUUID();
            prepared = CompletableFuture.supplyAsync(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var villager = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(server, villagerId);
                require(com.jvn.villagerretaliation.profile.VillagerProfileManager.getSkill(player.serverLevel(), villager,
                        com.jvn.villagerretaliation.skill.VillagerSkill.GUARDING) == 14, "Native client command applies chosen story growth on server");
                require(PartyVillagerContractService.dismiss(player, villager).success(), "Native companion says goodbye safely");
                require(PartyVillagerContractService.recruit(player, villager).success(), "Native companion rejoins freely");
                require(!com.jvn.villagerretaliation.party.CompanionGrowthService.choose(player, villager,
                        net.minecraft.resources.ResourceLocation.parse("villagerretaliation:after_the_fall"), "find_a_way"), "Rejoining does not reset story choice");
                return villagerId;
            }, server);
        }
        if (phase == 4 && ticks > 20 && prepared.isDone()) {
            prepared.join();
            try {
                Files.writeString(Path.of(System.getProperty(OUTPUT)).resolve("result.txt"),
                        "Companion client smoke passed: real server recruitment, owner roster authority, inventory menu rendering, permanent countdown hidden; story chat rendered, native command selects existing skill growth, goodbye/rejoin preserves choice\n");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            phase = 5;
            minecraft.stop();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
