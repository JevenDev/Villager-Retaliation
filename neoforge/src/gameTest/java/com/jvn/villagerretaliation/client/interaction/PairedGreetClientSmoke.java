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
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.getInventory().clearContent();
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
            phaseStarted = now;
            phase = 20;
        } else if (phase == 20 && minecraft.screen instanceof VillagerInteractionScreen screen && now - phaseStarted > 1000) {
            originalOptions = options(screen);
            require(!originalOptions.isEmpty(), "original talk menu exists");
            ordinaryReplyKeepsOptions(screen);
            screen.acceptDialogueExchanges(pair(), true, screen.beginGreetingRequest());
            require(field(screen, "outgoingDialogueOptions") != null, "hidden options remain available for their exit animation");
            require((float) invoke(screen, "dialogueOptionsTransitionProgress") < 1.0F, "option exit animates");
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
            if (revealed && fullyRevealedAt == 0) {
                long readyAt = (long) field(automatic, "readyAt");
                fullyRevealedAt = readyAt >= 0 ? readyAt - DialogueExchangeAutoAdvance.holdMillis(text.length()) : now;
            }
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
                require((float) invoke(screen, "dialogueOptionsTransitionProgress") < 1.0F, "restored options animate in");
                screen.acceptDialogueExchanges(pair(), false);
                phaseStarted = now; phase = 4;
            }
        } else if (phase == 4 && minecraft.screen instanceof VillagerInteractionScreen screen && now - phaseStarted > 6000) {
            require(field(screen, "villagerDialogueText").equals(PLAYER), "legacy manual exchange does not auto-advance, actual text: " + field(screen, "villagerDialogueText"));
            require(options(screen).size() == 1 && options(screen).getFirst().toLowerCase(java.util.Locale.ROOT).contains("continue"), "legacy Continue option is unchanged");
            clickDialogue(screen);
            require(field(screen, "villagerDialogueText").equals(NPC), "clicking revealed manual text advances the exchange");
            require(options(screen).size() == 1 && options(screen).getFirst().toLowerCase(java.util.Locale.ROOT).contains("continue"),
                    "choices stay behind the final exchange until its text finishes");
            clickDialogue(screen);
            require(field(screen, "villagerDialogueText").equals(NPC), "skipping the final exchange does not select a choice");
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
            talk(screen);
            phaseStarted = now;
            phase = 6;
        } else if (phase == 6 && minecraft.screen instanceof VillagerInteractionScreen screen && now - phaseStarted > 400) {
            originalOptions = options(screen);
            require(!originalOptions.isEmpty(), "talk menu is ready for click advancement checks");
            screen.acceptDialogueExchanges(pair(), true, screen.beginGreetingRequest());
            require(!(boolean) invoke(screen, "isDialogueTextAnimationComplete"), "new player line starts animating");
            clickDialogue(screen);
            require(field(screen, "villagerDialogueText").equals(PLAYER), "first text click keeps the current line");
            require((boolean) invoke(screen, "isDialogueTextAnimationComplete"), "first text click reveals the full line");
            clickDialogue(screen);
            require(field(screen, "villagerDialogueText").equals(NPC), "second text click advances to the NPC");
            require(((DialogueExchangeAutoAdvance) field(screen, "automaticDialogue")).active(), "manual advance preserves automatic playback");
            require(!(boolean) invoke(screen, "isDialogueTextAnimationComplete"), "advanced line still animates");
            phase = 7;
        } else if (phase == 7 && minecraft.screen instanceof VillagerInteractionScreen screen
                && !((DialogueExchangeAutoAdvance) field(screen, "automaticDialogue")).active()) {
            require(options(screen).equals(originalOptions), "automatic final hold still restores the menu after manual advance: " + options(screen) + " expected " + originalOptions
                    + ", page=" + field(screen, "page") + ", text=" + field(screen, "villagerDialogueText"));
            phaseStarted = now;
            phase = 8;
        } else if (phase == 8 && minecraft.screen instanceof VillagerInteractionScreen screen && now - phaseStarted > 400) {
            screen.acceptDialogueExchanges(pair(), true, screen.beginGreetingRequest());
            clickDialogue(screen);
            clickDialogue(screen);
            clickDialogue(screen);
            require(((DialogueExchangeAutoAdvance) field(screen, "automaticDialogue")).active(), "revealing the final line keeps its hold active");
            clickDialogue(screen);
            require(!((DialogueExchangeAutoAdvance) field(screen, "automaticDialogue")).active(), "second final-line click finishes the exchange");
            require(options(screen).equals(originalOptions), "manual finish restores the menu");
            phaseStarted = now;
            phase = 9;
        } else if (phase == 9 && minecraft.screen instanceof VillagerInteractionScreen screen && now - phaseStarted > 400) {
            require((float) invoke(screen, "dialogueOptionsTransitionProgress") == 1.0F, "option entrance finishes");
            require(field(screen, "outgoingDialogueOptions") == null, "departed option snapshot is released");
            capture("paired-menu-restored");
            lockedChoicePresentation(screen);
            minecraft.setScreen(null);
            try { Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Paired Greet client smoke passed: actual typewriter, readable holds, exact sequence, animated menu restoration, stable ordinary reply options, click reveal and advance, automatic continuation after clicks, manual Continue, replacement and Back cancellation.\n"); }
            catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            phase = 99; minecraft.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private static void lockedChoicePresentation(VillagerInteractionScreen screen) {
        var definitions = (List<com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition>) field(screen, "dialogueOptions");
        var original = List.copyOf(definitions);
        definitions.clear();
        definitions.add(com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition.simple("locked", "Offer help",
                com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType.STORY, 0)
                .withChoice(com.jvn.villagerretaliation.dialogue.DialogueChoice.DISABLED, false, "First earn my trust."));
        invoke(screen, "rebuildOptions");
        var choice = ((List<?>) field(screen, "options")).stream()
                .filter(option -> field(option, "label").equals("Offer help")).findFirst().orElseThrow();
        require((boolean) field(choice, "locked"), "unavailable choices render locked");
        require(field(choice, "reason").equals("First earn my trust."), "locked choices retain their localized explanation");
        ((Runnable) field(choice, "action")).run();
        require(!((PairedGreetingRequestState) field(screen, "greetingRequest")).pending(), "locked choices cannot send a request");
        capture("locked-choice");
        definitions.clear();
        definitions.addAll(original);
        invoke(screen, "rebuildOptions");
    }

    private static void ordinaryReplyKeepsOptions(VillagerInteractionScreen screen) {
        long layoutVersion = (long) field(screen, "optionLayoutVersion");
        long transitionStarted = (long) field(screen, "dialogueOptionsTransitionStartMillis");
        var state = (VillagerInteractionScreenState) field(screen, "state");
        state.setSelectedOption(Math.min(1, originalOptions.size() - 1));
        var position = state.captureOptionListPosition();
        var request = screen.beginGreetingRequest();
        require(request != null, "ordinary choice begins a correlated request");
        require(options(screen).equals(originalOptions), "pending request keeps the menu visible");
        require(!(boolean) field(screen, "dialogueOptionsHidden"), "pending request does not hide options");
        require(screen.beginGreetingRequest() == null, "pending request still blocks duplicate choices");
        screen.acceptGreetingNotice(new com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload(entityId,
                "An ordinary reply.", "Resident", DialogueTextSegment.plain("An ordinary reply.", DialogueTextEffects.NONE),
                List.of(), false, request));
        require(field(screen, "villagerDialogueText").equals("An ordinary reply."), "ordinary reply updates the dialogue text");
        require(options(screen).equals(originalOptions), "ordinary reply keeps the same options");
        require((long) field(screen, "optionLayoutVersion") == layoutVersion, "ordinary reply does not rebuild the menu");
        require((long) field(screen, "dialogueOptionsTransitionStartMillis") == transitionStarted,
                "ordinary reply does not restart the option animation");
        require(state.captureOptionListPosition().equals(position), "ordinary reply preserves selection and scroll");
        require(!((PairedGreetingRequestState) field(screen, "greetingRequest")).pending(), "ordinary reply releases the pending request");
    }

    private static void clickDialogue(VillagerInteractionScreen screen) {
        int left = (int) invoke(screen, "interactionContainerLeft");
        int top = (int) invoke(screen, "interactionContainerTop");
        int offsetY = (int) invoke(screen, "slideOffsetY") + (int) invoke(screen, "interactionStateTransitionOffsetY");
        require(screen.mouseClicked(left + 100, top + 20 + offsetY, 0), "dialogue text click is handled");
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
