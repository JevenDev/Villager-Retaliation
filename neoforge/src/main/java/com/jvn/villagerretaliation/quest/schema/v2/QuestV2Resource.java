package com.jvn.villagerretaliation.quest.schema.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestResourceSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record QuestV2Resource(
        ResourceLocation id,
        QuestResourceSource source,
        Map<String, JsonElement> metadata,
        Provider provider,
        Availability availability,
        Lifecycle lifecycle,
        JsonObject target,
        String entryStage,
        List<Stage> stages,
        Map<String, Stage> stagesById,
        List<Event> events,
        Rewards rewards,
        UiHints ui
) {
    public QuestV2Resource {
        if (id == null) {
            throw new IllegalArgumentException("quest v2 id must not be null");
        }
        source = source == null ? new QuestResourceSource(id, "") : source;
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        provider = provider == null ? Provider.EMPTY : provider;
        availability = availability == null ? Availability.EMPTY : availability;
        lifecycle = lifecycle == null ? Lifecycle.EMPTY : lifecycle;
        target = copy(target);
        entryStage = normalizeId(entryStage);
        stages = stages == null ? List.of() : List.copyOf(stages);
        stagesById = stagesById == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(stagesById));
        events = events == null ? List.of() : List.copyOf(events);
        rewards = rewards == null ? Rewards.EMPTY : rewards;
        ui = ui == null ? UiHints.EMPTY : ui;
    }

    public record Provider(
            ResourceLocation type,
            Set<ResourceLocation> requiredCapabilities,
            JsonObject data
    ) {
        public static final Provider EMPTY = new Provider(null, Set.of(), new JsonObject());

        public Provider {
            requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
            data = copy(data);
        }
    }

    public record Availability(
            List<JsonObject> conditions,
            List<ResourceLocation> prerequisites,
            JsonObject data
    ) {
        public static final Availability EMPTY = new Availability(List.of(), List.of(), new JsonObject());

        public Availability {
            conditions = copyObjects(conditions);
            prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
            data = copy(data);
        }
    }

    public record Lifecycle(
            Map<String, Hook> hooks,
            List<Scene> dialogue
    ) {
        public static final Lifecycle EMPTY = new Lifecycle(Map.of(), List.of());

        public Lifecycle {
            hooks = hooks == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(hooks));
            dialogue = dialogue == null ? List.of() : List.copyOf(dialogue);
        }
    }

    public record Hook(
            String id,
            List<JsonObject> actions,
            Transition transition,
            JsonObject data
    ) {
        public Hook {
            id = normalizeId(id);
            actions = copyObjects(actions);
            transition = transition == null ? Transition.EMPTY : transition;
            data = copy(data);
        }
    }

    public record Stage(
            String id,
            List<Objective> objectives,
            List<String> completeWhenObjectives,
            Transition next,
            Map<String, DialogueSlot> dialogueSlots,
            Map<String, Scene> scenes,
            List<Response> responses,
            List<Event> events,
            List<JsonObject> entryActions,
            List<JsonObject> exitActions,
            Rewards rewards,
            UiHints ui,
            JsonObject data
    ) {
        public Stage {
            id = normalizeId(id);
            objectives = objectives == null ? List.of() : List.copyOf(objectives);
            completeWhenObjectives = completeWhenObjectives == null ? List.of() : List.copyOf(completeWhenObjectives);
            next = next == null ? Transition.EMPTY : next;
            dialogueSlots = dialogueSlots == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(dialogueSlots));
            scenes = scenes == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(scenes));
            responses = responses == null ? List.of() : List.copyOf(responses);
            events = events == null ? List.of() : List.copyOf(events);
            entryActions = copyObjects(entryActions);
            exitActions = copyObjects(exitActions);
            rewards = rewards == null ? Rewards.EMPTY : rewards;
            ui = ui == null ? UiHints.EMPTY : ui;
            data = copy(data);
        }
    }

    public record Objective(
            String id,
            String type,
            boolean optional,
            JsonObject data
    ) {
        public Objective {
            id = normalizeId(id);
            type = normalizeId(type);
            data = copy(data);
        }
    }

    public record DialogueSlot(
            String slot,
            String scene,
            Scene inlineScene,
            ExternalScene externalScene,
            JsonObject data
    ) {
        public DialogueSlot {
            slot = normalizeId(slot);
            scene = normalizeId(scene);
            externalScene = externalScene == null ? ExternalScene.EMPTY : externalScene;
            data = copy(data);
        }
    }

    public record ExternalScene(
            ResourceLocation tree,
            String entry
    ) {
        public static final ExternalScene EMPTY = new ExternalScene(null, "");

        public ExternalScene {
            entry = normalizeId(entry);
        }

        public boolean isEmpty() {
            return this.tree == null;
        }

        public String entryOr(String fallback) {
            return this.entry.isBlank() ? normalizeId(fallback) : this.entry;
        }
    }

    public record Scene(
            String id,
            List<String> lines,
            String textKey,
            ExternalScene externalScene,
            List<Response> responses,
            JsonObject data
    ) {
        public Scene {
            id = normalizeId(id);
            lines = lines == null ? List.of() : List.copyOf(lines);
            textKey = textKey == null ? "" : textKey;
            externalScene = externalScene == null ? ExternalScene.EMPTY : externalScene;
            responses = responses == null ? List.of() : List.copyOf(responses);
            data = copy(data);
        }

        public boolean hasInlineContent() {
            return !this.lines.isEmpty() || !this.textKey.isBlank() || !this.responses.isEmpty();
        }
    }

    public record Response(
            String id,
            TextSpec label,
            Transition transition,
            List<JsonObject> actions,
            JsonObject data
    ) {
        public Response {
            id = normalizeId(id);
            label = label == null ? TextSpec.EMPTY : label;
            transition = transition == null ? Transition.EMPTY : transition;
            actions = copyObjects(actions);
            data = copy(data);
        }
    }

    public record TextSpec(
            String text,
            String key,
            List<String> lines
    ) {
        public static final TextSpec EMPTY = new TextSpec("", "", List.of());

        public TextSpec {
            text = text == null ? "" : text;
            key = key == null ? "" : key;
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public record Transition(
            String stage,
            String scene,
            String response,
            boolean complete,
            boolean abandon,
            boolean fail
    ) {
        public static final Transition EMPTY = new Transition("", "", "", false, false, false);

        public Transition {
            stage = normalizeId(stage);
            scene = normalizeId(scene);
            response = normalizeId(response);
        }

        public boolean isEmpty() {
            return this.stage.isBlank()
                    && this.scene.isBlank()
                    && this.response.isBlank()
                    && !this.complete
                    && !this.abandon
                    && !this.fail;
        }
    }

    public record Event(
            String id,
            String trigger,
            Set<String> stages,
            List<JsonObject> conditions,
            List<JsonObject> actions,
            Transition transition,
            JsonObject data
    ) {
        public Event {
            id = normalizeId(id);
            trigger = normalizeId(trigger);
            stages = stages == null ? Set.of() : Set.copyOf(stages);
            conditions = copyObjects(conditions);
            actions = copyObjects(actions);
            transition = transition == null ? Transition.EMPTY : transition;
            data = copy(data);
        }
    }

    public record Rewards(
            List<JsonObject> actions,
            JsonObject data
    ) {
        public static final Rewards EMPTY = new Rewards(List.of(), new JsonObject());

        public Rewards {
            actions = copyObjects(actions);
            data = copy(data);
        }
    }

    public record UiHints(
            String trackerText,
            String trackerTextKey,
            Map<String, String> placeholders,
            JsonObject data
    ) {
        public static final UiHints EMPTY = new UiHints("", "", Map.of(), new JsonObject());

        public UiHints {
            trackerText = trackerText == null ? "" : trackerText;
            trackerTextKey = trackerTextKey == null ? "" : trackerTextKey;
            placeholders = placeholders == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(placeholders));
            data = copy(data);
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }

    private static JsonObject copy(JsonObject object) {
        return object == null ? new JsonObject() : object.deepCopy();
    }

    private static List<JsonObject> copyObjects(List<JsonObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return List.of();
        }
        return objects.stream()
                .map(QuestV2Resource::copy)
                .toList();
    }
}
