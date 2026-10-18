package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.authoring.preview.PlaytestDrafts;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class EditorPlaytestPresentation {
    private static Map<String, Visual> visuals = Map.of();
    private static String quest = "";
    private static String questTitle;
    private static String questDescription;
    private static boolean ambiguousEdits;

    static boolean ambiguousEdits() { return ambiguousEdits; }
    private static Map<String, java.util.UUID> speakers = Map.of();
    private static net.minecraft.client.resources.sounds.SoundInstance voice;
    private static String voiceKey = "";
    private static final Map<String, Visual> SEEN = new HashMap<>();
    private static final Set<String> AMBIGUOUS = new HashSet<>();
    private record Visual(String text, JsonObject presentation, boolean cameraEdited, boolean voiceEdited) { }

    private EditorPlaytestPresentation() { }

    public static void clear() {
        visuals = Map.of(); quest = ""; questTitle = null; questDescription = null; speakers = Map.of();
        stopVoice();
    }

    private static void stopVoice() {
        if (voice != null) net.minecraft.client.Minecraft.getInstance().getSoundManager().stop(voice);
        voice = null; voiceKey = "";
    }

    public static boolean refreshVoice(DialogueSpeech original) {
        var visual = original == null || !EditorPlaytestViewport.active() ? null : visuals.get(original.text());
        if (visual == null || !visual.voiceEdited()) { stopVoice(); return false; }
        String key = original.presentationId() + ":" + visual.presentation().get("voices");
        if (voiceKey.equals(key)) return false;
        stopVoice(); voiceKey = key;
        var mc = net.minecraft.client.Minecraft.getInstance();
        var sound = ConversationLinePresentation.read(visual.presentation()).voice(mc.options.languageCode);
        if (sound != null && mc.getSoundManager().getSoundEvent(sound) != null) {
            voice = new net.minecraft.client.resources.sounds.SimpleSoundInstance(sound, net.minecraft.sounds.SoundSource.VOICE,
                    1, 1, net.minecraft.util.RandomSource.create(), false, 0,
                    net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            mc.getSoundManager().play(voice);
        }
        return true;
    }
    public static String text(String value) {
        var visual = EditorPlaytestViewport.active() ? visuals.get(value) : null;
        return visual == null ? value : DialogueTextSegment.plainText(DialogueTextSegment.parse(visual.text(), DialogueTextEffects.NONE));
    }

    static void update(AuthoringWorkspace workspace, AuthoringDocument selected, Map<String, JsonObject> applied,
            Map<String, String> replacements, String locale, Map<String, java.util.UUID> bindings) {
        var result = new HashMap<String, Visual>();
        AMBIGUOUS.clear();
        SEEN.clear();
        ambiguousEdits = false;
        speakers = Map.copyOf(bindings);
        quest = selected.kind() == DefinitionKind.QUEST ? selected.id() : "";
        var root = selected.json();
        JsonObject display = root.has("display") ? root.getAsJsonObject("display") : root.has("ui") ? root.getAsJsonObject("ui") : new JsonObject();
        var title = reference(display, "title");
        var description = reference(display, "description");
        questTitle = title == null ? null : workspace.resolveText(selected, title, locale);
        questDescription = description == null ? null : workspace.resolveText(selected, description, locale);
        for (var owner : workspace.documents()) {
            boolean relevant = owner == selected || selected.kind() == DefinitionKind.QUEST
                    && owner.kind() == DefinitionKind.CONVERSATION && owner.json().has("quest") && owner.json().get("quest").getAsString().equals(selected.id());
            JsonObject previous = applied.get(owner.resourcePath());
            if (relevant && previous != null) visit(workspace, owner, previous,
                    PlaytestDrafts.presentation(previous, owner.json()), new JsonObject(), new JsonObject(), applied, replacements, locale, result);
        }
        visuals = Map.copyOf(result);

    }

    private static void visit(AuthoringWorkspace workspace, AuthoringDocument owner, JsonElement old, JsonElement next,
            JsonObject oldInherited, JsonObject inherited, Map<String, JsonObject> applied, Map<String, String> replacements,
            String locale, Map<String, Visual> result) {
        if (old.isJsonObject() && next.isJsonObject()) {
            var before = old.getAsJsonObject();
            var after = next.getAsJsonObject();
            var oldStyle = inherit(oldInherited, before);
            var style = inherit(inherited, after);
            for (String field : java.util.List.of("text", "line", "label", "title", "description", "response")) {
                JsonElement original = reference(before, field);
                JsonElement latest = reference(after, field);
                if (original != null && latest != null) add(workspace, owner, original, latest, oldStyle, style, applied, replacements, locale, result);
            }
            for (var entry : before.entrySet()) if (after.has(entry.getKey())) {
                var child = entry.getValue();
                var latest = after.get(entry.getKey());
                if ((entry.getKey().equals("lines") || entry.getKey().equals("variants") || entry.getKey().equals("responses"))
                        && child.isJsonArray() && latest.isJsonArray()) {
                    for (int i = 0; i < Math.min(child.getAsJsonArray().size(), latest.getAsJsonArray().size()); i++) {
                        var a = child.getAsJsonArray().get(i); var b = latest.getAsJsonArray().get(i);
                        if (a.isJsonPrimitive() && b.isJsonPrimitive()) add(workspace, owner, a, b, oldStyle, style, applied, replacements, locale, result);
                    }
                }
                visit(workspace, owner, child, latest, oldStyle, style, applied, replacements, locale, result);
            }
        } else if (old.isJsonArray() && next.isJsonArray()) {
            for (int i = 0; i < Math.min(old.getAsJsonArray().size(), next.getAsJsonArray().size()); i++)
                visit(workspace, owner, old.getAsJsonArray().get(i), next.getAsJsonArray().get(i), oldInherited, inherited, applied, replacements, locale, result);
        }
    }

    private static JsonObject inherit(JsonObject inherited, JsonObject source) {
        var result = inherited.deepCopy();
        for (String key : java.util.List.of("text_effects", "camera", "speaker", "voices")) if (source.has(key)) result.add(key, source.get(key));
        return result;
    }

    private static JsonElement reference(JsonObject value, String field) {
        if (value.has(field + "_key")) { var reference = new JsonObject(); reference.add("key", value.get(field + "_key")); return reference; }
        return value.get(field);
    }

    private static void add(AuthoringWorkspace workspace, AuthoringDocument owner, JsonElement old, JsonElement next,
            JsonObject oldStyle, JsonObject style, Map<String, JsonObject> applied, Map<String, String> replacements,
            String locale, Map<String, Visual> result) {
        String previous = resolve(workspace, owner, old, applied, locale);
        String latest = workspace.resolveText(owner, next, locale);
        previous = com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources.resolveTemplate(previous, replacements);
        latest = com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources.resolveTemplate(latest, replacements);
        String key = DialogueTextSegment.plainText(DialogueTextSegment.parse(previous, DialogueTextEffects.read(oldStyle)));
        if (key.isBlank() || AMBIGUOUS.contains(key)) return;
        var visual = new Visual(latest, style.deepCopy(), !java.util.Objects.equals(oldStyle.get("camera"), style.get("camera")),
                !java.util.Objects.equals(oldStyle.get("voices"), style.get("voices")));
        var prior = SEEN.putIfAbsent(key, visual);
        if (prior != null && !prior.equals(visual)) {
            ambiguousEdits |= result.containsKey(key) || !previous.equals(latest) || !oldStyle.equals(style);
            result.remove(key); AMBIGUOUS.add(key); return;
        }
        if (!previous.equals(latest) || !oldStyle.equals(style)) result.put(key, visual);
    }

    private static String resolve(AuthoringWorkspace workspace, AuthoringDocument owner, JsonElement value,
            Map<String, JsonObject> applied, String locale) {
        if (value.isJsonObject() && value.getAsJsonObject().has("key")) {
            var location = workspace.textLocation(owner, value.getAsJsonObject().get("key").getAsString(), locale);
            if (location != null && applied.containsKey(location.document().resourcePath())) {
                var text = JsonPointers.get(applied.get(location.document().resourcePath()), location.pointer());
                if (text != null && text.isJsonPrimitive()) return text.getAsString();
            }
        }
        return value.isJsonPrimitive() ? value.getAsString() : workspace.resolveText(owner, value, locale);
    }

    public static java.util.List<DialogueTextSegment> segments(String text, java.util.List<DialogueTextSegment> original) {
        var visual = EditorPlaytestViewport.active() ? visuals.get(text) : null;
        return visual == null ? original : DialogueTextSegment.parse(visual.text(), DialogueTextEffects.read(visual.presentation()));
    }

    public static DialogueSpeech speech(DialogueSpeech original) {
        if (original == null || !EditorPlaytestViewport.active()) return original;
        var visual = visuals.get(original.text());
        if (visual == null) return original;
        var camera = visual.cameraEdited() ? ConversationLinePresentation.read(visual.presentation()).camera() : original.camera();
        var speaker = original.speaker(); var entityId = original.entityId(); String name = original.name();
        if (visual.presentation().has("speaker")) {
            var declared = visual.presentation().getAsJsonObject("speaker");
            String type = declared.has("type") ? declared.get("type").getAsString() : "role";
            if (type.equals("narrator")) { speaker = null; entityId = -1; name = ""; }
            else if (type.equals("player")) {
                var player = net.minecraft.client.Minecraft.getInstance().player;
                if (player != null) { speaker = player.getUUID(); entityId = player.getId(); name = player.getName().getString(); }
            } else if (type.equals("role")) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                String role = declared.has("role") ? declared.get("role").getAsString() : "provider";
                var binding = speakers.get(role);
                net.minecraft.world.entity.Entity entity = null;
                if (mc.level != null && binding != null) for (var candidate : mc.level.entitiesForRendering())
                    if (candidate.getUUID().equals(binding)) { entity = candidate; break; }
                if (entity == null && role.equals("provider") && mc.level != null) entity = mc.level.getEntity(EditorLiveQuestTest.snapshot().provider());
                if (entity != null) { speaker = entity.getUUID(); entityId = entity.getId(); name = entity.getName().getString(); }
            }
        }
        return new DialogueSpeech(speaker, entityId, name, DialogueTextSegment.parse(visual.text(), DialogueTextEffects.read(visual.presentation())), camera, original.presentationId());
    }

    public static QuestTrackerSyncPayload.Entry entry(QuestTrackerSyncPayload.Entry value) {
        if (!EditorPlaytestViewport.active() || !quest.equals(value.questId())) return value;
        return new QuestTrackerSyncPayload.Entry(value.questId(), questTitle == null ? text(value.title()) : questTitle, text(value.objective()), questDescription == null ? text(value.description()) : questDescription,
                text(value.metadata()), value.progress(), value.showProgress(), value.state(), value.status(), value.issuer(), value.issuerLocation(),
                value.questItems(), value.rewardPreviews(), value.prerequisites(), value.objectiveSteps().stream()
                        .map(step -> new QuestTrackerSyncPayload.ObjectiveStep(text(step.label()), step.completed())).toList(),
                value.questUpdate(), value.questAvailable(), value.journal());
    }
}
