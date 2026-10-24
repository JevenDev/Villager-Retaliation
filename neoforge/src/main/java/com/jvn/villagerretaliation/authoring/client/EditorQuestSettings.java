package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.quest.QuestPerformance;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.util.List;
import java.util.function.Consumer;

final class EditorQuestSettings {
    interface Fields { void render(JsonElement value, String path, String label); }
    private record Section(String field, String title, String defaults, String hint) { }
    private static final List<Section> SECTIONS = List.of(
            new Section("display", "Journal presentation", "{}", "Set the title, description, tracker wording and journal visibility."),
            new Section("provider", "Quest provider", "{\"type\":\"villagerretaliation:villager\"}", "Choose villager or discovery delivery, then configure provider filters."),
            new Section("availability", "Prerequisites, repeatability and turn-in", "{\"repeatable\":false}", "Configure availability, cooldowns, provider locking and item consumption."),
            new Section("discovery", "Discovery routes", "[]", "Configure how an item, book, location or world event offers or starts this quest."),
            new Section("target", "World target and rescue site", "{}", "Set the quest destination, structure search or rescue site."),
            new Section("dialogue", "Offer and turn-in dialogue", "{}", "Configure the quest offer, reminder and turn-in presentation."),
            new Section("lifecycle", "Quest lifecycle", "{}", "Configure actions for starting, completing, failing, abandoning or expiring."),
            new Section("rewards", "Base rewards", "{\"experience\":0,\"reputation\":0}", "Configure experience, reputation, loot and completion actions."),
            new Section("performance", "Performance reward tiers", "", "The first matching tier overrides its declared base reward fields. Reorder tiers to set priority.")
    );

    private final ImString tierId = new ImString("swift", 128), measurementId = new ImString("elapsed", 128);
    private final ImString actor = new ImString("companion", 128), objective = new ImString(128);
    private final ImString threshold = new ImString("1200", 128);
    private final ImInt experience = new ImInt(25), reputation = new ImInt(0), deadline = new ImInt(12000);
    private String metric = "elapsed_ticks", stage = "";
    private boolean maximum = true;
    private final ImString routeId = new ImString("discovered", 128), match = new ImString("minecraft:paper", 256);
    private String discoveryEvent = "item_acquired", discoveryMode = "offer";

    static void defaults(AuthoringDocument document, String field, JsonElement defaults) {
        if (!SECTIONS.stream().anyMatch(section -> section.field().equals(field))) throw new IllegalArgumentException("Choose a quest settings section");
        var before = document.json().get(field);
        JsonElement value;
        if (before == null) value = defaults.deepCopy();
        else if (before.isJsonObject() && defaults.isJsonObject()) {
            value = before.deepCopy();
            mergeMissing(value.getAsJsonObject(), defaults.getAsJsonObject());
        } else throw new IllegalArgumentException("This section already exists");
        validate(field, value);
        document.edit("Configure quest " + field, root -> root.add(field, value));
    }

    private static void mergeMissing(JsonObject target, JsonObject defaults) {
        defaults.entrySet().forEach(entry -> {
            if (!target.has(entry.getKey())) target.add(entry.getKey(), entry.getValue().deepCopy());
            else if (target.get(entry.getKey()).isJsonObject() && entry.getValue().isJsonObject())
                mergeMissing(target.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject());
        });
    }

    static void validate(String field, JsonElement value) {
        var schema = com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export();
        var rule = schema.getAsJsonObject("properties").getAsJsonObject(field).deepCopy();
        rule.add("$defs", schema.get("$defs"));
        var issues = com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(value, rule);
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.getFirst().message());
    }

    static void performanceTier(AuthoringDocument document, String id, String measurementId, String metric, String actor,
                                String stage, String objective, double threshold, boolean maximum, int experience, int reputation) {
        if (!id.matches("[a-z][a-z0-9_.-]{0,63}") || !measurementId.matches("[a-z][a-z0-9_.-]{0,63}"))
            throw new IllegalArgumentException("Use lowercase tier and measurement IDs starting with a letter");
        if (!Double.isFinite(threshold) || threshold < 0 || experience < 0) throw new IllegalArgumentException("Enter nonnegative bounds and experience");
        var performance = EditorSchema.object(document.json().get("performance")).deepCopy();
        var measurements = EditorSchema.object(performance.get("measurements"));
        var measurement = new JsonObject(); measurement.addProperty("metric", metric);
        if (!actor.isBlank()) measurement.addProperty("actor", actor);
        if (!stage.isBlank()) measurement.addProperty("stage", stage);
        if (!objective.isBlank()) measurement.addProperty("objective", objective);
        if (measurements.has(measurementId) && !measurements.get(measurementId).equals(measurement))
            throw new IllegalArgumentException("That measurement ID has different settings. Choose another ID");
        measurements.add(measurementId, measurement);
        performance.add("measurements", measurements);
        var tiers = DialogueGraph.array(performance, "tiers");
        if (tiers.asList().stream().anyMatch(value -> value.isJsonObject() && id.equals(DialogueGraph.string(value.getAsJsonObject(), "id"))))
            throw new IllegalArgumentException("That performance tier already exists");
        var tier = new JsonObject(); tier.addProperty("id", id);
        var bounds = new JsonObject(); bounds.addProperty(maximum ? "max" : "min", threshold);
        var requirements = new JsonObject(); requirements.add(measurementId, bounds); tier.add("requirements", requirements);
        var rewards = new JsonObject(); rewards.addProperty("experience", experience); rewards.addProperty("reputation", reputation);
        tier.add("rewards", rewards); tiers.add(tier); performance.add("tiers", tiers);
        var parsed = QuestPerformance.read(performance);
        var errors = parsed.referenceErrors(document.json());
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.getFirst());
        document.edit("Add performance reward tier", root -> root.add("performance", performance));
    }

    static void discoveryRoute(AuthoringDocument document, String id, String event, String mode, JsonObject match) {
        var routes = DialogueGraph.array(document.json(), "discovery");
        if (routes.asList().stream().anyMatch(value -> value.isJsonObject() && id.equals(DialogueGraph.string(value.getAsJsonObject(), "id"))))
            throw new IllegalArgumentException("That discovery route already exists");
        var route = new JsonObject(); route.addProperty("id", id); route.addProperty("event", event); route.addProperty("mode", mode);
        if (!match.isEmpty()) route.add("match", match.deepCopy());
        routes.add(route);
        validate("discovery", routes);
        document.edit("Add quest discovery route", root -> root.add("discovery", routes));
    }

    void render(AuthoringDocument document, Fields fields, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable refresh) {
        var root = document.json();
        for (var section : SECTIONS) {
            if (!EditorWidgets.section(section.title())) continue;
            ImGui.pushID(section.field());
            ImGui.textWrapped(section.hint());
            if (!root.has(section.field()) && !section.defaults().isEmpty()
                    && ImGui.button("Configure " + section.title().toLowerCase(java.util.Locale.ROOT))) run.accept(() -> {
                defaults(document, section.field(), JsonParser.parseString(section.defaults())); refresh.run();
            });
            if (root.has(section.field())) fields.render(root.get(section.field()), "/" + section.field(), section.title());
            if (section.field().equals("provider") && root.has("provider") && ImGui.button("Add profession filters")) run.accept(() -> {
                defaults(document, "provider", JsonParser.parseString("{\"filters\":{\"professions\":[]}}")); refresh.run();
            });
            if (section.field().equals("availability")) availability(document, run, refresh);
            if (section.field().equals("performance")) performance(document, run, refresh);
            if (section.field().equals("discovery")) discovery(document, run, refresh);
            ImGui.popID();
        }
        if (EditorWidgets.section("Quest identity and compatibility")) for (var entry : root.entrySet()) {
            if (SECTIONS.stream().anyMatch(section -> section.field().equals(entry.getKey())) || entry.getKey().equals("stages")
                    || List.of("world_campaign", "story_arc").contains(entry.getKey())) continue;
            fields.render(entry.getValue(), "/" + JsonPointers.escape(entry.getKey()), entry.getKey().replace('_', ' '));
        }
    }

    private void availability(AuthoringDocument document, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable refresh) {
        if (ImGui.button("Add prerequisites")) run.accept(() -> {
            defaults(document, "availability", JsonParser.parseString("{\"prerequisites\":[]}")); refresh.run();
        });
        if (ImGui.button("Add turn-in rules")) run.accept(() -> {
            defaults(document, "availability", JsonParser.parseString("{\"locked_to_villager\":true,\"cross_villager_compatible\":false,\"consume_on_completion\":true}")); refresh.run();
        });
        if (ImGui.button("Add repeatability controls")) run.accept(() -> {
            defaults(document, "availability", JsonParser.parseString("{\"repeatable\":false,\"completion_cooldown_ticks\":0,\"max_starts\":0,\"max_completions\":0}")); refresh.run();
        });
        ImGui.textWrapped("Existing limits are preserved. Review max starts and max completions when making a quest repeatable.");
        ImGui.inputInt(EditorWidgets.field("Deadline after start (ticks)"), deadline);
        if (ImGui.button("Add deadline")) run.accept(() -> {
            if (deadline.get() < 1) throw new IllegalArgumentException("Enter a positive deadline");
            var expiration = new JsonObject(); expiration.addProperty("after_ticks", deadline.get());
            var defaults = new JsonObject(); defaults.add("expiration", expiration);
            defaults(document, "availability", defaults); refresh.run();
        });
    }

    private void performance(AuthoringDocument document, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable refresh) {
        ImGui.inputText(EditorWidgets.field("New tier ID"), tierId);
        ImGui.inputText(EditorWidgets.field("Measurement ID"), measurementId);
        if (ImGui.beginCombo(EditorWidgets.field("Metric"), metric.replace('_', ' '))) {
            for (var value : QuestPerformance.Metric.values()) {
                String id = value.name().toLowerCase(java.util.Locale.ROOT);
                if (ImGui.selectable(id.replace('_', ' '), metric.equals(id))) metric = id;
            }
            ImGui.endCombo();
        }
        boolean health = metric.equals("final_health_percent") || metric.equals("lowest_health_percent");
        if (health) ImGui.inputText(EditorWidgets.field("Follower alias"), actor);
        if (ImGui.beginCombo(EditorWidgets.field("Stage scope"), stage.isEmpty() ? "Whole quest" : stage)) {
            if (ImGui.selectable("Whole quest", stage.isEmpty())) stage = "";
            for (var node : DialogueGraph.nodes(document)) if (ImGui.selectable(node.id(), stage.equals(node.id()))) stage = node.id();
            ImGui.endCombo();
        }
        if (stage.isEmpty()) ImGui.inputText(EditorWidgets.field("Objective scope (optional stage.objective)"), objective);
        if (ImGui.checkbox("Require at most this value", maximum)) maximum = !maximum;
        ImGui.inputText(EditorWidgets.field("Threshold"), threshold);
        ImGui.inputInt(EditorWidgets.field("Tier experience"), experience);
        ImGui.inputInt(EditorWidgets.field("Tier reputation"), reputation);
        if (ImGui.button("Add performance tier")) run.accept(() -> {
            double bound;
            try { bound = Double.parseDouble(threshold.get()); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException("Enter a numeric threshold"); }
            performanceTier(document, tierId.get(), measurementId.get(), metric, health ? actor.get() : "", stage,
                    stage.isEmpty() ? objective.get() : "", bound, maximum, experience.get(), reputation.get());
            refresh.run();
        });
    }

    private void discovery(AuthoringDocument document, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable refresh) {
        ImGui.inputText(EditorWidgets.field("New discovery route ID"), routeId);
        if (ImGui.beginCombo(EditorWidgets.field("Discovery event"), discoveryEvent.replace('_', ' '))) {
            var schema = com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export();
            for (var value : schema.getAsJsonObject("properties").getAsJsonObject("discovery").getAsJsonObject("items")
                    .getAsJsonObject("properties").getAsJsonObject("event").getAsJsonArray("enum")) {
                String id = value.getAsString();
                if (ImGui.selectable(id.replace('_', ' '), discoveryEvent.equals(id))) discoveryEvent = id;
            }
            ImGui.endCombo();
        }
        if (ImGui.beginCombo(EditorWidgets.field("Discovery result"), discoveryMode)) {
            for (String mode : List.of("offer", "automatic")) if (ImGui.selectable(mode, discoveryMode.equals(mode))) discoveryMode = mode;
            ImGui.endCombo();
        }
        boolean item = List.of("item_acquired", "item_used", "book_opened").contains(discoveryEvent);
        if (item) ImGui.inputText(EditorWidgets.field(discoveryEvent.equals("book_opened") ? "Book ID" : "Item ID"), match);
        if (discoveryEvent.equals("location")) ImGui.textWrapped("Uses your current dimension and position with a radius of 8 blocks. Edit the match fields to adjust.");
        if (ImGui.button("Add discovery route")) run.accept(() -> {
            var filter = new JsonObject();
            if (item) {
                if (net.minecraft.resources.ResourceLocation.tryParse(match.get()) == null) throw new IllegalArgumentException("Enter a valid item or book ID");
                filter.addProperty(discoveryEvent.equals("book_opened") ? "book" : "item", match.get());
            }
            if (discoveryEvent.equals("location")) {
                var player = net.minecraft.client.Minecraft.getInstance().player;
                if (player == null) throw new IllegalStateException("Enter a world to capture the discovery location");
                filter.addProperty("dimension", player.level().dimension().location().toString());
                var position = new com.google.gson.JsonArray();
                position.add(player.blockPosition().getX()); position.add(player.blockPosition().getY()); position.add(player.blockPosition().getZ());
                filter.add("pos", position); filter.addProperty("radius", 8);
            }
            discoveryRoute(document, routeId.get(), discoveryEvent, discoveryMode, filter); refresh.run();
        });
    }
}
