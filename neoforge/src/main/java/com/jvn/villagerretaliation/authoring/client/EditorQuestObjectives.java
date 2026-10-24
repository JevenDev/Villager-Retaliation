package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.util.List;
import java.util.function.Consumer;

final class EditorQuestObjectives {
    private final ImString dimension = new ImString("minecraft:overworld", 256);
    private final ImString structure = new ImString("minecraft:pillager_outpost", 256);
    private final ImString actor = new ImString("companion", 128);
    private final ImString actorTag = new ImString("quest_companion", 128);
    private final ImString destinationTag = new ImString("quest_destination", 128);
    private final ImString criterion = new ImString("villagerretaliation:captive_rescued", 256);
    private final ImString fact = new ImString("objective_done", 128);
    private final ImString tracker = new ImString(1024);
    private final ImInt x = new ImInt(0), y = new ImInt(64), z = new ImInt(0), radius = new ImInt(4);
    private boolean optional, taggedDestination = true;

    static boolean guided(String type) {
        return List.of("location_visit", "structure_visit", "follower_arrival", "rescue", "criterion", "fact").contains(type);
    }

    void fields(String type) {
        if (List.of("location_visit", "structure_visit", "follower_arrival", "rescue").contains(type)) {
            ImGui.inputText(EditorWidgets.field("Dimension"), dimension);
            if (type.equals("structure_visit") || type.equals("rescue")) ImGui.inputText(EditorWidgets.field("Structure ID"), structure);
            if (type.equals("follower_arrival")) {
                ImGui.inputText(EditorWidgets.field("Follower alias"), actor);
                ImGui.inputText(EditorWidgets.field("Follower entity tag"), actorTag);
                if (ImGui.checkbox("Destination is a tagged NPC", taggedDestination)) taggedDestination = !taggedDestination;
                if (taggedDestination) ImGui.inputText(EditorWidgets.field("Destination entity tag"), destinationTag);
                ImGui.textWrapped("Adds a protected follower bound to an existing entity tag. Tag the companion and destination in your world before testing.");
            }
            if (type.equals("location_visit") || type.equals("follower_arrival") && !taggedDestination) {
                ImGui.inputInt(EditorWidgets.field("X"), x);
                ImGui.inputInt(EditorWidgets.field("Y"), y);
                ImGui.inputInt(EditorWidgets.field("Z"), z);
                var player = net.minecraft.client.Minecraft.getInstance().player;
                if (player != null && ImGui.button("Use player position")) {
                    x.set(player.blockPosition().getX()); y.set(player.blockPosition().getY()); z.set(player.blockPosition().getZ());
                    dimension.set(player.level().dimension().location().toString());
                }
            }
            if (!type.equals("rescue")) ImGui.inputInt(EditorWidgets.field("Arrival radius"), radius);
            if (type.equals("rescue")) ImGui.textWrapped("Uses the quest's rescue site. If none exists, creates a holding camp with an escort-home rescue.");
        }
        if (type.equals("criterion")) ImGui.inputText(EditorWidgets.field("Criterion ID"), criterion);
        if (type.equals("fact")) ImGui.inputText(EditorWidgets.field("Quest tag"), fact);
    }

    void common() {
        if (ImGui.checkbox("Optional objective", optional)) optional = !optional;
        ImGui.inputText(EditorWidgets.field("Tracker text (optional)"), tracker);
    }

    record Location(String dimension, int x, int y, int z, int radius) { }
    record Setup(Location location, String structure, String actor, String destinationTag, String criterion, String fact) { }

    JsonObject template(String type, String quest) {
        if (type.equals("follower_arrival") && taggedDestination && destinationTag.get().isBlank())
            throw new IllegalArgumentException("Enter the destination entity tag");
        return template(type, quest, new Setup(new Location(dimension.get(), x.get(), y.get(), z.get(), radius.get()),
                structure.get(), actor.get(), taggedDestination ? destinationTag.get() : "", criterion.get(), fact.get()));
    }

    static JsonObject template(String type, String quest, Setup setup) {
        var dimension = setup.location().dimension();
        var structure = setup.structure();
        var actor = setup.actor();
        var destinationTag = setup.destinationTag();
        var criterion = setup.criterion();
        var fact = setup.fact();
        int x = setup.location().x(), y = setup.location().y(), z = setup.location().z(), radius = setup.location().radius();
        boolean taggedDestination = !destinationTag.isBlank();
        var value = new JsonObject();
        value.addProperty("type", type.equals("rescue") ? "criterion" : type);
        if (List.of("location_visit", "structure_visit", "follower_arrival").contains(type)) {
            requireId(dimension);
            if (radius < 1) throw new IllegalArgumentException("Arrival radius must be positive");
            value.addProperty("dimension", dimension);
            value.addProperty("radius", radius);
        }
        if (type.equals("structure_visit")) { requireId(structure); value.addProperty("structure", structure); }
        if (type.equals("location_visit") || type.equals("follower_arrival") && !taggedDestination) {
            var location = new JsonObject();
            location.addProperty("x", x); location.addProperty("y", y); location.addProperty("z", z);
            value.add("location", location);
        }
        if (type.equals("follower_arrival")) {
            requireAlias(actor);
            var match = new JsonObject();
            match.addProperty("actor", actor);
            if (taggedDestination) {
                if (destinationTag.isBlank()) throw new IllegalArgumentException("Enter the destination entity tag");
                match.addProperty("destination_tag", destinationTag);
            }
            value.add("match", match);
        }
        if (type.equals("rescue") || type.equals("criterion")) {
            requireId(type.equals("rescue") ? "villagerretaliation:captive_rescued" : criterion);
            value.addProperty("criterion", type.equals("rescue") ? "villagerretaliation:captive_rescued" : criterion);
            value.addProperty("count", 1);
            if (type.equals("rescue")) {
                var match = new JsonObject(); match.addProperty("quest", quest); value.add("match", match);
            }
        }
        if (type.equals("fact")) {
            if (fact.isBlank()) throw new IllegalArgumentException("Enter a quest tag");
            value.addProperty("scope", "quest"); value.addProperty("tag", fact);
        }
        return value;
    }

    String append(AuthoringDocument document, String stage, String type, JsonObject objective) {
        var value = objective.deepCopy();
        value.addProperty("optional", optional);
        if (!tracker.get().isBlank()) {
            var text = new JsonObject(); text.addProperty("text", tracker.get()); value.add("tracker", text);
        }
        if (!guided(type) && !List.of("item_check", "mob_kill", "block_break", "block_place", "block_interact", "trade").contains(type))
            return EditorContentTools.appendObjective(document, stage, value);
        var follower = type.equals("follower_arrival") ? follower(actor.get(), actorTag.get()) : null;
        JsonObject target = null;
        if (type.equals("rescue") && !EditorSchema.object(document.json().get("target")).has("site")) {
            requireId(structure.get()); requireId(dimension.get());
            target = new JsonObject();
            target.addProperty("dimension", dimension.get());
            var site = new JsonObject();
            site.addProperty("selection", "prefer_existing");
            site.add("structures", strings(structure.get()));
            site.add("templates", strings("villagerretaliation:quest/holding_camp"));
            site.addProperty("rescue", "escort_home");
            site.addProperty("min_distance", 256); site.addProperty("max_distance", 2048);
            target.add("site", site);
        } else if (type.equals("rescue") && !List.of("escort_home", "free_at_site").contains(
                DialogueGraph.string(EditorSchema.object(EditorSchema.object(document.json().get("target")).get("site")), "rescue")))
            throw new IllegalArgumentException("Set the existing site's rescue mode to escort_home or free_at_site first");
        return append(document, stage, value, follower, target);
    }

    static String append(AuthoringDocument document, String stage, JsonObject objective, JsonObject follower, JsonObject target) {
        var node = EditorQuestFlow.stage(document, stage);
        var draft = new AuthoringDocument(document.kind(), document.resourcePath(), document.json());
        String path = EditorContentTools.appendObjective(draft, stage, objective);
        draft.edit("Set objective setup", root -> {
            var owner = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            if (follower != null) {
                var values = DialogueGraph.array(owner, "followers");
                String alias = DialogueGraph.string(follower.getAsJsonObject("actor"), "alias");
                var existing = values.asList().stream().filter(value -> value.isJsonObject()
                        && alias.equals(DialogueGraph.string(EditorSchema.object(value.getAsJsonObject().get("actor")), "alias"))).findFirst();
                if (existing.isPresent() && !existing.get().equals(follower)) throw new IllegalArgumentException("Follower alias already has different settings");
                if (existing.isEmpty()) values.add(follower.deepCopy());
                owner.add("followers", values);
            }
            if (target != null) {
                var merged = EditorSchema.object(root.get("target")).deepCopy();
                target.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue().deepCopy()));
                root.add("target", merged);
            }
        });
        var issues = com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry.structuralIssues(
                JsonPointers.get(draft.json(), path).getAsJsonObject());
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.getFirst().message());
        var changed = draft.json();
        document.edit("Add quest objective and setup", root -> {
            root.keySet().clear();
            changed.entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue()));
        });
        return path;
    }

    static JsonObject follower(String alias, String tag) {
        requireAlias(alias);
        if (tag.isBlank()) throw new IllegalArgumentException("Enter the follower entity tag");
        var actor = new JsonObject();
        actor.addProperty("alias", alias); actor.addProperty("type", "villagerretaliation:living_entity");
        actor.addProperty("binding_source", "entity_tag"); actor.addProperty("binding", tag);
        actor.addProperty("missing_actor_policy", "block"); actor.addProperty("death_policy", "fail");
        var result = new JsonObject(); result.add("actor", actor);
        result.addProperty("command", "follow"); result.addProperty("protected", true);
        return result;
    }

    private static void requireAlias(String value) {
        if (!value.matches("[a-z][a-z0-9_.-]{0,63}")) throw new IllegalArgumentException("Use a lowercase follower alias starting with a letter");
    }

    private static void requireId(String value) {
        if (net.minecraft.resources.ResourceLocation.tryParse(value) == null) throw new IllegalArgumentException("Enter a valid namespaced resource ID");
    }

    private static JsonArray strings(String value) { var values = new JsonArray(); values.add(value); return values; }

    static String summary(JsonObject objective) {
        String type = DialogueGraph.string(objective, "type");
        String target = "";
        for (String key : List.of("item", "entity", "block", "structure", "criterion", "tag", "campaign_location"))
            if (objective.has(key)) { target = DialogueGraph.string(objective, key); break; }
        if (type.equals("location_visit") && objective.has("location")) {
            var location = objective.getAsJsonObject("location");
            target = location.get("x") + ", " + location.get("y") + ", " + location.get("z");
        }
        if (type.equals("follower_arrival")) target = DialogueGraph.string(EditorSchema.object(objective.get("match")), "actor");
        String count = objective.has("count") ? " × " + objective.get("count").getAsInt() : "";
        return type.replace('_', ' ') + (target.isBlank() ? "" : ": " + target) + count
                + (objective.has("optional") && objective.get("optional").getAsBoolean() ? " (optional)" : "");
    }

    static void summaries(AuthoringDocument document, String stage, Consumer<String> inspect) {
        var node = EditorQuestFlow.stage(document, stage);
        var objectives = DialogueGraph.array(JsonPointers.get(document.json(), node.pointer()).getAsJsonObject(), "objectives");
        for (int i = 0; i < objectives.size(); i++) if (objectives.get(i).isJsonObject()) {
            String path = node.pointer() + "/objectives/" + i;
            if (ImGui.selectable(summary(objectives.get(i).getAsJsonObject()) + "##" + path)) inspect.accept(path);
        }
    }
}
