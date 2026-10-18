package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.client.interaction.ClientVillagerConversationState;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraController;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

public final class AuthoringDialoguePreview implements VillagerInteractionScreen.LocalPresentation {
    private static AuthoringDialoguePreview active;
    private static List<String> lastLog = List.of();
    private final DialoguePreviewSession session;
    private final Entity target;
    private final PreviewConditions conditions;
    private final Runnable returnToEditor;
    private final DialogueCameraController.Preview camera;
    private final ClientVillagerConversationState.Snapshot conversation;
    private final Object level;
    private final String start;
    private boolean showTransport = true;
    private VillagerInteractionScreen screen;
    private SoundInstance voice;
    private int shownRevision = -1;
    private boolean disposed;

    private AuthoringDialoguePreview(DialoguePreviewSession session, Entity target, String start,
            PreviewConditions conditions, Runnable returnToEditor) {
        this.session = session;
        this.conditions = conditions;
        this.target = target;
        this.start = start;
        this.returnToEditor = returnToEditor;
        this.conversation = ClientVillagerConversationState.snapshot();
        this.level = Minecraft.getInstance().level;
        this.camera = DialogueCameraController.beginPreview();
    }

    public static void start(AuthoringDocument document, String nodeId, Entity target,
            PreviewConditions conditions, Runnable returnToEditor) {
        start(document, nodeId, target, conditions, returnToEditor, key -> I18n.exists(key) ? I18n.get(key) : null);
    }

    public static void start(AuthoringDocument document, String nodeId, Entity target,
            PreviewConditions conditions, Runnable returnToEditor, Function<String, String> localization) {
        start(document, nodeId, target, conditions, returnToEditor, localization, true);
    }

    public static void start(AuthoringDocument document, String nodeId, Entity target,
            PreviewConditions conditions, Runnable returnToEditor, Function<String, String> localization, boolean showTransport) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || target == null
                || target.level() != minecraft.level || !target.isAlive() || target.isRemoved())
            throw new IllegalArgumentException("Select a living entity in the current world.");
        if (active != null || ClientVillagerConversationState.active())
            throw new IllegalStateException("Close the current conversation before previewing.");
        var json = document.json();
        if (document.kind().name().equals("QUEST")) throw new IllegalArgumentException("Quest execution preview is not supported.");
        ResourceLocation id = ResourceLocation.tryParse(DialoguePreviewSession.string(json, "id"));
        if (id == null) id = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "authoring_preview");
        var session = new DialoguePreviewSession(json, id, conditions, localization);
        session.start(nodeId);
        var preview = new AuthoringDialoguePreview(session, target, nodeId, conditions, returnToEditor);
        preview.showTransport = showTransport;
        active = preview;
        try {
            preview.screen = VillagerInteractionScreen.localPreview(target, preview);
            minecraft.setScreen(preview.screen);
            preview.tick(preview.screen);
        } catch (RuntimeException exception) {
            preview.close();
            throw exception;
        }
    }

    public static JsonObject definition() { return active == null ? null : active.session.definition(); }

    public static void refreshPresentation(JsonObject latest) {
        var preview = active;
        if (preview == null) return;
        preview.session.refreshPresentation(latest);
        preview.update(preview.screen);
    }

    public static DialoguePreviewSession.Cursor cursor() { return active == null ? null : active.session.cursor(); }
    public static List<DialoguePreviewSession.Choice> choices() {
        var preview = active;
        return preview == null ? List.of() : preview.session.choices().stream().map(choice -> new DialoguePreviewSession.Choice(
                choice.id(), preview.resolveText(choice.label()), choice.available(), choice.reason(), choice.next(), choice.source())).toList();
    }
    public static void next() { command(() -> active.session.advance()); }
    public static void previous() { command(() -> active.session.previous()); }
    public static void exchange(int index) { command(() -> active.session.exchange(index)); }
    public static void choose(int index) { command(() -> active.session.choose(index)); }
    private static void command(Runnable action) {
        var preview = active;
        if (preview != null) preview.perform(() -> { action.run(); preview.update(preview.screen); });
    }

    public static boolean active() { return active != null; }
    public static List<String> log() { return active == null ? lastLog : active.session.log(); }
    public static void stop() { if (active != null) active.close(); }

    @Override public void tick(VillagerInteractionScreen screen) {
        perform(() -> update(screen));
    }

    private void update(VillagerInteractionScreen screen) {
        var minecraft = Minecraft.getInstance();
        if (disposed) return;
        if (minecraft.level == null || minecraft.player == null || target.level() != minecraft.level
                || !target.isAlive() || target.isRemoved() || !camera.active()) {
            close();
            return;
        }
        if (shownRevision == session.revision()) return;
        shownRevision = session.revision();
        stopVoice();
        JsonObject line = session.currentLine();
        if (line == null) {
            screen.presentLocalLine(new DialogueSpeech(null, -1, "Preview",
                    DialogueTextSegment.parse(session.choices().isEmpty() ? "Preview ended. Review the preview log in the editor."
                            : "Select a dialogue entry. Conditions are simulated.", DialogueTextEffects.NONE)));
            return;
        }
        String kind = line.has("speaker") ? DialoguePreviewSession.string(line.getAsJsonObject("speaker"), "type") : "role";
        Entity speaker = kind.equals("player") ? minecraft.player : kind.equals("narrator") ? null : resolveSpeaker(line);
        String text = resolveText(DialoguePreviewSession.string(line, "resolved_text"));
        var presentation = ConversationLinePresentation.read(line);
        var speech = new DialogueSpeech(speaker == null ? null : speaker.getUUID(), speaker == null ? -1 : speaker.getId(),
                speaker == null ? "" : speaker.getName().getString(),
                DialogueTextSegment.parse(text, DialogueTextEffects.read(line)), presentation.camera());
        screen.presentLocalLine(speech);
        ResourceLocation sound = presentation.voice(minecraft.options.languageCode);
        if (sound != null && minecraft.getSoundManager().getSoundEvent(sound) != null) {
            voice = new SimpleSoundInstance(sound, SoundSource.VOICE, 1.0F, 1.0F, RandomSource.create(),
                    false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            minecraft.getSoundManager().play(voice);
        }
    }

    private String resolveText(String text) {
        var minecraft = Minecraft.getInstance();
        var replacements = new java.util.LinkedHashMap<>(conditions.placeholders());
        String name = target.getName().getString();
        replacements.putIfAbsent("player", minecraft.player.getName().getString());
        replacements.putIfAbsent("villager", name);
        replacements.putIfAbsent("villager_name", name);
        replacements.putIfAbsent("villager_possessive", name.endsWith("s") || name.endsWith("S") ? name + "'" : name + "'s");
        replacements.putIfAbsent("x", Integer.toString(target.blockPosition().getX()));
        replacements.putIfAbsent("y", Integer.toString(target.blockPosition().getY()));
        replacements.putIfAbsent("z", Integer.toString(target.blockPosition().getZ()));
        com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.time(conditions.resolveDayTime(minecraft.level.getDayTime()))
                .forEach(replacements::putIfAbsent);
        return com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources.resolveTemplate(text, replacements);
    }

    @Override public List<Entity> cameraSubjects() {
        var result = new ArrayList<Entity>();
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null) for (Entity entity : minecraft.level.entitiesForRendering())
            if (conditions.speakers().containsValue(entity.getUUID()) && entity.isAlive() && !entity.isRemoved()) result.add(entity);
        return result;
    }

    @Override public List<VillagerInteractionScreen.LocalOption> options() {
        var result = new ArrayList<VillagerInteractionScreen.LocalOption>();
        if (session.hasNextLine()) result.add(new VillagerInteractionScreen.LocalOption("Continue", () -> perform(() -> {
            session.advance();
            update(screen);
        }), false));
        var choices = session.choices();
        for (int index = 0; index < choices.size(); index++) {
            int selected = index;
            var choice = choices.get(index);
            result.add(new VillagerInteractionScreen.LocalOption(resolveText(choice.label())
                    + (choice.available() ? "" : " [" + choice.reason() + "]"), () -> perform(() -> {
                session.choose(selected);
                update(screen);
            }), !choice.available()));
        }
        if (showTransport) result.add(new VillagerInteractionScreen.LocalOption("Restart preview", () -> perform(() -> {
            session.start(start);
            update(screen);
        }), false));
        if (showTransport) result.add(new VillagerInteractionScreen.LocalOption("Return to editor", this::close, false));
        return result;
    }

    @Override public void close() {
        if (disposed) return;
        var minecraft = Minecraft.getInstance();
        boolean ownedScreen = minecraft.screen == screen;
        if (ownedScreen) minecraft.setScreen(null);
        removed();
        if (ownedScreen && minecraft.level == level && minecraft.player != null && minecraft.player.isAlive())
            returnToEditor.run();
    }

    @Override public void removed() {
        if (disposed) return;
        disposed = true;
        lastLog = session.log();
        session.close();
        stopVoice();
        camera.close();
        if (Minecraft.getInstance().level == level) ClientVillagerConversationState.restore(conversation);
        if (active == this) active = null;
    }

    private Entity resolveSpeaker(JsonObject line) {
        String role = line.has("speaker") ? DialoguePreviewSession.string(line.getAsJsonObject("speaker"), "role") : "provider";
        java.util.UUID binding = conditions.speakers().get(role);
        if (binding == null && (role.isBlank() || role.equals("provider"))) return target;
        if (binding != null) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player.getUUID().equals(binding)) return minecraft.player;
            for (Entity entity : minecraft.level.entitiesForRendering())
                if (entity.getUUID().equals(binding) && entity.isAlive() && !entity.isRemoved()) return entity;
        }
        throw new IllegalArgumentException("Bind the preview speaker role '" + role + "' to a loaded entity.");
    }

    private void perform(Runnable action) {
        if (disposed) return;
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException
                | IndexOutOfBoundsException | ClassCastException exception) {
            session.diagnostic(exception.getMessage());
            close();
        }
    }

    private void stopVoice() {
        if (voice != null) Minecraft.getInstance().getSoundManager().stop(voice);
        voice = null;
    }
}
