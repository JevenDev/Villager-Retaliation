package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.DialogueTextSpeed;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual client-screen smoke. A compiled class is not evidence that this run passed. */
@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class PairedGreetClientSmoke {
    private static final String OUTPUT = "villagerretaliation.pairedGreetClientSmoke";
    private static final String PLAYER = "Hello. How are you feeling?";
    private static final String NPC = "I am content, thanks for asking.";
    private static CompletableFuture<Integer> preparing;
    private static int phase, entityId, playerAppearances, npcAppearances;
    private static long started, phaseStarted, fullyRevealedAt;
    private static String previous = "";
    private static List<String> originalOptions;
    private static final java.util.Set<String> captured=new java.util.HashSet<>();

    private PairedGreetClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || phase == 99) return;
        var minecraft = Minecraft.getInstance();
        minecraft.setWindowActive(true);
        minecraft.options.pauseOnLostFocus = false;
        long now = Util.getMillis();
        if (started == 0) started = now;
        require(now - started < 180000, "paired Greet client smoke timed out at phase " + phase);
        if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) return;
        var server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        if (phase == 0 && minecraft.screen == null) {
            minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            VillagerRetaliationConfig.DIALOGUE_TEXT_SPEED.set(DialogueTextSpeed.MEDIUM);
            preparing = server.submit(() -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayer(playerId);
                for (var position : BlockPos.betweenClosed(5, 99, 5, 25, 99, 25)) level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
                player.teleportTo(15.5, 100, 15.5);
                var villager = EntityType.VILLAGER.create(level);
                villager.setNoAi(true); villager.setPos(15.5, 100, 18.5); level.addFreshEntity(villager);
                return villager.getId();
            });
            phase = 1;
        } else if (phase == 1 && preparing.isDone()) {
            entityId = preparing.join();
            if (!(minecraft.level.getEntity(entityId) instanceof Villager)) return;
            preparing = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var villager = (Villager) server.overworld().getEntity(entityId);
                require(com.jvn.villagerretaliation.interaction.VillagerConversationService.start(player, villager), "normal conversation begins");
                VillagerInteractionScreenOpener.openNormal(player, villager, false);
                return entityId;
            });
            phase = 2;
        } else if (phase == 2 && preparing.isDone() && minecraft.screen instanceof VillagerInteractionScreen screen) {
            talk(screen);
            originalOptions = options(screen);
            require(!originalOptions.isEmpty(), "original talk menu exists");
            screen.acceptDialogueExchanges(pair(), true, screen.beginGreetingRequest());
            phaseStarted = now; fullyRevealedAt = 0; phase = 3;
        } else if (phase == 3 && minecraft.screen instanceof VillagerInteractionScreen screen) {
            String text = (String) field(screen, "villagerDialogueText");
            var automatic = (DialogueExchangeAutoAdvance) field(screen, "automaticDialogue");
            if (!text.equals(previous)) {
                if (text.equals(PLAYER)) playerAppearances++;
                if (text.equals(NPC)) {
                    npcAppearances++;
                    require(fullyRevealedAt != 0 && now - fullyRevealedAt >= DialogueExchangeAutoAdvance.holdMillis(PLAYER.length()) - 100,
                            "actual screen holds fully revealed player text before NPC");
                    phaseStarted = now; fullyRevealedAt = 0;
                }
                previous = text;
            }
            boolean revealed = (boolean) invoke(screen, "isDialogueTextAnimationComplete");
            if (revealed && fullyRevealedAt == 0) fullyRevealedAt = now;
            if(revealed && fullyRevealedAt!=0 && now-fullyRevealedAt>=200) {
                String shot=text.equals(PLAYER)?"paired-player":"paired-npc";
                if(captured.add(shot))capture(shot);
            }
            if (automatic.active()) require(options(screen).isEmpty(), "automatic pair shows no Continue or prematurely restored menu");
            if (now - phaseStarted < 400) require(!revealed, "actual client typewriter remains active at medium speed");
            if (!automatic.active()) {
                require(playerAppearances == 1 && npcAppearances == 1, "player and NPC are displayed exactly once");
                require(fullyRevealedAt != 0 && now - fullyRevealedAt >= DialogueExchangeAutoAdvance.holdMillis(NPC.length()) - 100, "NPC readable hold completes");
                require(options(screen).equals(originalOptions), "original talk options return after final NPC hold");
                screen.acceptDialogueExchanges(pair(), false);
                phaseStarted = now; phase = 4;
            }
        } else if (phase == 4 && minecraft.screen instanceof VillagerInteractionScreen screen && now - phaseStarted > 6000) {
            require(field(screen, "villagerDialogueText").equals(PLAYER), "legacy manual exchange does not auto-advance");
            require(options(screen).size() == 1 && options(screen).getFirst().toLowerCase(java.util.Locale.ROOT).contains("continue"), "legacy Continue option is unchanged");
            screen.acceptVillagerDialogue("Replacement notice.", List.of());
            require(!((DialogueExchangeAutoAdvance) field(screen, "automaticDialogue")).active(), "replacement notice cancels automatic state");
            require(((java.util.Collection<?>) field(screen, "dialogueExchanges")).isEmpty(), "replacement notice discards stale queued NPC speech");
            var cancelledRequest = screen.beginGreetingRequest();
            screen.acceptDialogueExchanges(pair(), true, cancelledRequest);
            screen.onClose();
            require(!((DialogueExchangeAutoAdvance) field(screen, "automaticDialogue")).active(), "Back during player line cancels playback");
            require(((java.util.Collection<?>) field(screen, "dialogueExchanges")).isEmpty(), "Back clears the player's queued reply");
            talk(screen);
            var currentRequest = screen.beginGreetingRequest();
            screen.acceptDialogueExchanges(pair(), true, cancelledRequest);
            require(!((DialogueExchangeAutoAdvance) field(screen, "automaticDialogue")).active(), "stale notice cannot restart playback after cancellation/new request");
            screen.acceptGreetingNotice(new com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload(entityId,
                    "Stale fallback.", "Resident", DialogueTextSegment.plain("Stale fallback.", DialogueTextEffects.NONE), List.of(), false, cancelledRequest));
            require(((PairedGreetingRequestState) field(screen, "greetingRequest")).pending(), "stale manual fallback cannot consume current pending request");
            require(!field(screen, "villagerDialogueText").equals("Stale fallback."), "stale fallback cannot replace current screen text");
            screen.acceptGreetingNotice(new com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload(entityId,
                    "Current fallback.", "Resident", DialogueTextSegment.plain("Current fallback.", DialogueTextEffects.NONE), List.of(), false, currentRequest));
            require(field(screen, "villagerDialogueText").equals("Current fallback."), "matching ordinary fallback remains visible");
            require(options(screen).equals(originalOptions), "matching ordinary fallback releases pending lock and restores original options");
            screen.acceptDialogueExchanges(pair(), true, screen.beginGreetingRequest()); phase = 5;
        } else if (phase == 5 && minecraft.screen instanceof VillagerInteractionScreen screen && field(screen, "villagerDialogueText").equals(NPC)) {
            screen.onClose();
            require(!((DialogueExchangeAutoAdvance) field(screen, "automaticDialogue")).active(), "Back during NPC line cancels playback");
            require(((java.util.Collection<?>) field(screen, "dialogueExchanges")).isEmpty(), "Back during NPC leaves no stale work");
            minecraft.setScreen(null);
            try { Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Paired Greet client smoke passed: actual typewriter, readable holds, exact sequence, original menu restoration, manual Continue, replacement and Back cancellation.\n"); }
            catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            phase = 99; minecraft.stop();
        }
    }

    private static List<DialogueSpeech> pair() {
        var minecraft = Minecraft.getInstance();
        return List.of(new DialogueSpeech(minecraft.player.getUUID(), minecraft.player.getId(), minecraft.player.getName().getString(), DialogueTextSegment.plain(PLAYER, DialogueTextEffects.NONE)),
                DialogueSpeech.npc(minecraft.level.getEntity(entityId), DialogueTextSegment.plain(NPC, DialogueTextEffects.NONE), null));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void talk(VillagerInteractionScreen screen) {
        try {
            var field = VillagerInteractionScreen.class.getDeclaredField("page");
            var method = VillagerInteractionScreen.class.getDeclaredMethod("openPage", field.getType()); method.setAccessible(true);
            method.invoke(screen, Enum.valueOf((Class) field.getType(), "TALK"));
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static Object invoke(Object target, String name) {
        try { var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static List<String> options(VillagerInteractionScreen screen) {
        return ((List<?>) field(screen, "options")).stream().map(option -> (String) field(option, "label")).toList();
    }
    private static void capture(String name) {
        var minecraft = Minecraft.getInstance();
        Screenshot.grab(Path.of(System.getProperty(OUTPUT)).toFile(), name + ".png", minecraft.getMainRenderTarget(), ignored -> { });
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
