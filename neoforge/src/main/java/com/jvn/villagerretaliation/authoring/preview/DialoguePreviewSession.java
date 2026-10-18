package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

public final class DialoguePreviewSession implements AutoCloseable {
    private final JsonObject root;
    private final ResourceLocation id;
    private final PreviewConditions conditions;
    private final Function<String, String> localization;
    private final Map<String, JsonObject> nodes = new LinkedHashMap<>();
    private final List<String> log = new ArrayList<>();
    private final List<JsonObject> lines = new ArrayList<>();
    private final List<Choice> choices = new ArrayList<>();
    private JsonObject node;
    private String nodeId = "";
    private String pendingNext;
    private boolean pendingEnd;
    private int line;
    private int revision;
    private boolean closed;

    public DialoguePreviewSession(JsonObject definition, ResourceLocation id, PreviewConditions conditions,
            Function<String, String> localization) {
        this.root = definition.deepCopy();
        this.id = id;
        this.conditions = conditions;
        this.localization = localization;
        conditions.defaultQuest(ResourceLocation.tryParse(string(root, "quest")));
        JsonElement raw = root.get("nodes");
        if (raw != null && raw.isJsonObject()) raw.getAsJsonObject().entrySet().forEach(entry ->
                nodes.put(string(entry.getValue().getAsJsonObject(), "id").isBlank() ? entry.getKey()
                        : string(entry.getValue().getAsJsonObject(), "id"), entry.getValue().getAsJsonObject()));
        else if (raw != null && raw.isJsonArray()) for (JsonElement value : raw.getAsJsonArray()) {
            JsonObject entry = value.getAsJsonObject();
            nodes.put(string(entry, "id"), entry);
        }
        if (nodes.isEmpty() && root.has("entries")) {
            int index = 0;
            for (JsonElement value : array(root, "entries")) {
                nodes.put("/entries/" + index++, value.getAsJsonObject());
            }
        }
        boolean pool = root.has("openings") || root.has("closings")
                || array(root, "lines").asList().stream().anyMatch(value -> value.isJsonObject()
                    && (value.getAsJsonObject().has("request") || value.getAsJsonObject().has("option_ids")))
                || root.has("options") && !root.has("trigger") && !root.has("lines")
                    && !root.has("line") && !root.has("exchanges");
        if (nodes.isEmpty() && pool) {
            for (String field : List.of("openings", "closings", "options", "lines")) {
                int index = 0;
                for (JsonElement value : array(root, field)) {
                    if (value.isJsonObject()) nodes.put("/" + field + "/" + index, value.getAsJsonObject());
                    index++;
                }
            }
        }
        if (nodes.isEmpty()) nodes.put("/", root);
    }

    public void start(String selected) {
        requireOpen();
        lines.clear();
        choices.clear();
        log.clear();
        node = null;
        nodeId = "";
        line = 0;
        pendingNext = null;
        pendingEnd = false;
        log.add("Local simulation. Actions are logged and no gameplay data is changed.");
        if (selected != null && !selected.isBlank()) {
            enter(selected);
            return;
        }
        ConditionOutcome eligibility = conditions.evaluate(id, root);
        if (eligibility != ConditionOutcome.MATCHED) {
            log.add("Definition requirements: " + eligibility);
            revision++;
            return;
        }
        JsonArray entries = root.has("topics") ? array(root, "topics")
                : root.has("nodes") ? array(root, "entries") : new JsonArray();
        if (!entries.isEmpty()) {
            for (JsonElement raw : entries) {
                JsonObject entry = raw.getAsJsonObject();
                ConditionOutcome outcome = conditions.evaluate(id, entry);
                choices.add(new Choice(string(entry, "id"), text(entry, "label"),
                        outcome == ConditionOutcome.MATCHED, outcome.name(), string(entry, "start"), entry));
            }
            revision++;
        } else if (root.has("entries") && !root.has("nodes")) {
            for (var entry : nodes.entrySet()) {
                ConditionOutcome outcome = conditions.evaluate(id, entry.getValue());
                choices.add(new Choice(entry.getKey(), string(entry.getValue(), "id"),
                        outcome == ConditionOutcome.MATCHED, outcome.name(), entry.getKey(), new JsonObject()));
            }
            revision++;
        } else if (!root.has("nodes") && nodes.size() > 1) {
            for (var entry : nodes.entrySet()) {
                ConditionOutcome outcome = conditions.evaluate(id, entry.getValue());
                choices.add(new Choice(entry.getKey(), entry.getKey() + " " + string(entry.getValue(), "id"),
                        outcome == ConditionOutcome.MATCHED, outcome.name(), entry.getKey(), new JsonObject()));
            }
            revision++;
        } else enter(nodes.keySet().iterator().next());
    }

    public void choose(int index) {
        requireOpen();
        Choice choice = choices.get(index);
        if (!choice.available()) throw new IllegalStateException("Preview condition is " + choice.reason());
        choices.clear();
        log.add("Choice: " + choice.label());
        effects(choice.source());
        lines.clear();
        line = 0;
        addLines(choice.source(), node == null ? new JsonObject() : node);
        pendingNext = choice.next();
        pendingEnd = bool(choice.source(), "end") || bool(choice.source(), "end_conversation");
        String checkId = string(choice.source(), "check");
        if (!checkId.isBlank()) {
            JsonObject check = find(array(root, "checks"), checkId);
            if (check == null) throw new IllegalArgumentException("Missing check: " + checkId);
            pendingNext = string(check, conditions.checkSuccess(checkId) ? "success" : "failure");
            log.add("Simulated check " + checkId + ": " + (conditions.checkSuccess(checkId) ? "success" : "failure"));
        }
        if (choice.source().has("follow_up")) {
            String nested = nodeId + "/follow_up/" + choice.id();
            nodes.put(nested, choice.source().getAsJsonObject("follow_up"));
            pendingNext = nested;
            pendingEnd = false;
        }
        if (lines.isEmpty()) finishResponse();
        else revision++;
    }

    public void advance() {
        requireOpen();
        if (line + 1 < lines.size()) {
            line++;
            if (line + 1 == lines.size() && pendingNext == null && !pendingEnd) finishNode();
            revision++;
        } else if (pendingNext != null || pendingEnd) finishResponse();
        else finishNode();
    }

    public JsonObject currentLine() {
        return closed || lines.isEmpty() ? null : lines.get(Math.min(line, lines.size() - 1)).deepCopy();
    }

    public List<Choice> choices() { return List.copyOf(choices); }
    public List<String> log() { return List.copyOf(log); }
    public void diagnostic(String message) { log.add("Preview stopped: " + message); }
    public int revision() { return revision; }
    public boolean hasNextLine() { return !closed && (!lines.isEmpty() && line + 1 < lines.size() || pendingNext != null || pendingEnd); }
    public boolean closed() { return closed; }
    public String nodeId() { return nodeId; }

    private void enter(String next) {
        JsonObject value = nodes.get(next);
        if (value == null && next.startsWith("/")) value = pointer(root, next);
        if (value == null) throw new IllegalArgumentException("Missing dialogue node: " + next);
        node = value;
        nodeId = next;
        lines.clear();
        choices.clear();
        line = 0;
        pendingNext = null;
        pendingEnd = false;
        ConditionOutcome outcome = conditions.evaluate(id, node);
        if (outcome != ConditionOutcome.MATCHED) {
            log.add("Node " + next + " requirements: " + outcome);
            revision++;
            return;
        }
        log.add("Node: " + next);
        effects(node);
        addLines(node, root);
        // stop automatic traversal at each node so authored cycles cannot lock the render thread
        if (lines.isEmpty()) lines.add(lineObject("(No dialogue line)", node));
        revision++;
        if (lines.size() == 1) finishNode();
    }

    private void finishResponse() {
        String next = pendingNext;
        boolean end = pendingEnd;
        pendingNext = null;
        pendingEnd = false;
        if (!end && next != null && !next.isBlank()) enter(next);
        else {
            choices.clear();
            log.add("Conversation ended.");
            revision++;
        }
    }

    private void finishNode() {
        if (pendingNext != null || pendingEnd || node == null) return;
        choices.clear();
        if (!bool(node, "end") && !bool(node, "end_conversation")) {
            String field = node.has("options") ? "options" : "responses";
            int index = 0;
            for (JsonElement raw : array(node, field)) {
                if (!raw.isJsonObject()) continue;
                JsonObject value = raw.getAsJsonObject();
                ConditionOutcome outcome = conditions.evaluate(id, value);
                boolean shown = outcome == ConditionOutcome.MATCHED
                        || string(value, "unavailable_behavior").equals("disable") || root.has("topics");
                if (shown || outcome == ConditionOutcome.UNKNOWN) {
                    String label = text(value, "label");
                    choices.add(new Choice(string(value, "id").isBlank() ? Integer.toString(index) : string(value, "id"),
                            label.isBlank() ? "Response " + (index + 1) : label, outcome == ConditionOutcome.MATCHED,
                            outcome.name(), string(value, "next"), value));
                }
                index++;
            }
            if (node.has("leave_option")) {
                JsonObject value = node.getAsJsonObject("leave_option");
                choices.add(new Choice("leave", text(value, "label"), true, "", "", value));
            }
            choices.sort(Comparator.comparingInt(choice -> integer(choice.source(), "order")));
            if (choices.isEmpty() && !string(node, "next").isBlank()) {
                pendingNext = string(node, "next");
                choices.add(new Choice("continue", "Continue", true, "", pendingNext, new JsonObject()));
                pendingNext = null;
            }
        }
        String outcome = string(node, "outcome");
        if (!outcome.isBlank()) {
            JsonObject value = find(array(root, "outcomes"), outcome);
            log.add("Outcome: " + (value == null ? outcome : text(value, "label")));
            if (value != null) effects(value);
        }
        revision++;
    }

    private void addLines(JsonObject value, JsonObject inherited) {
        JsonArray exchanges = array(value, "exchanges");
        if (!exchanges.isEmpty()) {
            for (JsonElement raw : exchanges) {
                JsonObject exchange = raw.getAsJsonObject();
                ConditionOutcome eligible = conditions.evaluate(id, exchange);
                if (eligible != ConditionOutcome.MATCHED) {
                    log.add("Exchange " + string(exchange, "id") + ": " + eligible);
                    continue;
                }
                JsonObject copy = exchange.deepCopy();
                if (!copy.has("camera")) {
                    if (value.has("camera")) copy.add("camera", value.get("camera").deepCopy());
                    else if (inherited.has("camera")) copy.add("camera", inherited.get("camera").deepCopy());
                }
                copy.addProperty("resolved_text", text(copy, "text"));
                lines.add(copy);
            }
            return;
        }
        JsonArray variants = array(value, value.has("variants") ? "variants" : "lines");
        if (variants.isEmpty() && value.has("line")) {
            variants = new JsonArray();
            variants.add(value.get("line"));
        }
        boolean responseText = variants.isEmpty() && (value.has("response") || value.has("response_key")
                || value.has("response_keys") || value.has("responses") && !value.has("nodes"));
        if (responseText) {
            variants = new JsonArray();
            if (value.has("response")) variants.add(value.get("response"));
            for (JsonElement raw : array(value, "responses")) {
                if (raw.isJsonPrimitive() || raw.isJsonObject() && !raw.getAsJsonObject().has("label")) variants.add(raw);
            }
        }
        if (variants.isEmpty() && value.has("text")) {
            variants = new JsonArray();
            variants.add(value.get("text"));
        }
        JsonArray keys = new JsonArray();
        for (String keyField : responseText ? new String[] {"response_key", "response_keys"}
                : new String[] {"line_key", "line_keys", "text_key", "text_keys"}) {
            if (!value.has(keyField)) continue;
            JsonElement key = value.get(keyField);
            if (key.isJsonArray()) key.getAsJsonArray().forEach(keys::add);
            else if (key.isJsonPrimitive()) keys.add(key);
        }
        if (variants.isEmpty() && !keys.isEmpty()) variants.add("");
        List<JsonObject> eligible = new ArrayList<>();
        boolean keyed = !keys.isEmpty() && variants.asList().stream().noneMatch(JsonElement::isJsonObject);
        int variantCount = keyed ? keys.size() : variants.size();
        for (int index = 0; index < variantCount; index++) {
            JsonElement raw = variants.get(keyed ? 0 : index);
            String fallbackField = responseText ? "response" : "line";
            if (keyed && value.has(fallbackField) && value.get(fallbackField).isJsonPrimitive()) raw = value.get(fallbackField);
            JsonObject candidate = raw.isJsonObject() ? raw.getAsJsonObject().deepCopy() : lineObject(raw.getAsString(), value);
            if (keyed) {
                candidate.remove("line");
                candidate.remove("line_key");
                candidate.addProperty("text", raw.getAsString());
                candidate.add("text_key", keys.get(index).deepCopy());
            }

            if (conditions.evaluate(id, candidate) != ConditionOutcome.MATCHED) continue;
            if (!candidate.has("camera")) {
                if (value.has("camera")) candidate.add("camera", value.get("camera").deepCopy());
                else if (inherited.has("camera")) candidate.add("camera", inherited.get("camera").deepCopy());
            }
            candidate.addProperty("resolved_text", text(candidate, candidate.has("line") || candidate.has("line_key") ? "line" : "text"));
            eligible.add(candidate);
        }
        if (!eligible.isEmpty()) {
            lines.add(eligible.get(conditions.variant() % eligible.size()));
            if (eligible.size() > 1) log.add("Authored variant " + (conditions.variant() % eligible.size() + 1)
                    + " of " + eligible.size() + ". Preview selection bypasses random weights and saved usage history.");
        }
        else if (value.has("text_key") || value.has("line_key"))
            lines.add(lineObject(text(value, value.has("text_key") ? "text" : "line"), value));
    }

    private JsonObject lineObject(String text, JsonObject source) {
        JsonObject result = source.deepCopy();
        result.addProperty("text", text);
        result.addProperty("resolved_text", text);
        for (String field : new String[] {"camera", "voices", "text_effects"})
            if (source.has(field)) result.add(field, source.get(field).deepCopy());
        return result;
    }

    public String text(JsonObject value, String field) {
        String key = string(value, field + "_key");
        JsonElement raw = value.get(field);
        if (raw == null && key.isBlank()) key = string(value, "key");
        if (raw != null && raw.isJsonObject()) key = string(raw.getAsJsonObject(), "key");
        if (!key.isBlank()) {
            String translated = localization.apply(key);
            if (translated == null) {
                log.add("Unresolved localization: " + key);
                return raw != null && raw.isJsonPrimitive() && !raw.getAsString().isBlank() ? raw.getAsString() : "[" + key + "]";
            }
            return translated;
        }
        return raw != null && raw.isJsonPrimitive() ? raw.getAsString() : "";
    }

    private void effects(JsonObject value) {
        for (String field : new String[] {"actions", "effects", "rewards", "reputation", "aggro", "aggro_immediately",
                "payment", "quest", "loot_table", "memory", "end_actions"}) {
            if (value.has(field)) log.add("Simulated " + field + ": " + value.get(field));
        }
    }

    @Override public void close() {
        closed = true;
        nodes.clear();
        choices.clear();
        lines.clear();
        pendingNext = null;
        node = null;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Preview has closed");
    }

    private static JsonObject pointer(JsonObject root, String path) {
        JsonElement value = root;
        try {
            for (String token : path.substring(1).split("/")) {
                token = token.replace("~1", "/").replace("~0", "~");
                value = value.isJsonArray() ? value.getAsJsonArray().get(Integer.parseInt(token))
                        : value.getAsJsonObject().get(token);
                if (value == null) return null;
            }
            return value.isJsonObject() ? value.getAsJsonObject() : null;
        } catch (IndexOutOfBoundsException | NumberFormatException exception) {
            return null;
        }
    }

    private static JsonObject find(JsonArray values, String id) {
        for (JsonElement value : values) if (string(value.getAsJsonObject(), "id").equals(id)) return value.getAsJsonObject();
        return null;
    }

    public static String string(JsonObject value, String field) {
        return value.has(field) && value.get(field).isJsonPrimitive() ? value.get(field).getAsString() : "";
    }

    private static boolean bool(JsonObject value, String field) { return value.has(field) && value.get(field).getAsBoolean(); }
    private static int integer(JsonObject value, String field) { return value.has(field) ? value.get(field).getAsInt() : 0; }
    private static JsonArray array(JsonObject value, String field) {
        return value.has(field) && value.get(field).isJsonArray() ? value.getAsJsonArray(field) : new JsonArray();
    }

    public record Choice(String id, String label, boolean available, String reason, String next, JsonObject source) {
        public Choice { source = source.deepCopy(); }
        @Override public JsonObject source() { return source.deepCopy(); }
    }
}
