package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class EditorGraph {
    static final float WIDTH = 340;
    static final float HEADER = 38;
    static final float ROW = 34;

    private EditorGraph() { }

    record Block(String pointer, String title, String text, DialogueGraph.Edge output, String styledText, List<EditorGraphInline.Field> fields) {
        Block(String pointer, String title, String text, DialogueGraph.Edge output, String styledText) {
            this(pointer, title, text, output, styledText, List.of());
        }
        Block(String pointer, String title, String text, DialogueGraph.Edge output) {
            this(pointer, title, text, output, null, List.of());
        }
        float height() { return fields.isEmpty() ? ROW : 28 + (float) fields.stream().mapToDouble(EditorGraphInline.Field::height).sum() + 6; }
    }
    record Card(DialogueGraph.Node node, List<Block> blocks, float width, float requestedHeight, float naturalHeight, long textFields) {
        Card(DialogueGraph.Node node, List<Block> blocks) { this(node, blocks, WIDTH, 0); }
        Card(DialogueGraph.Node node, List<Block> blocks, float width, float height) {
            this(node, blocks, width, height, HEADER + (float) blocks.stream().mapToDouble(Block::height).sum() + 34,
                    blocks.stream().flatMap(block -> block.fields().stream()).filter(EditorGraphInline.Field::multiline).count());
        }
        Card {
            width = Float.isFinite(width) ? Math.clamp(width, 240, 2000) : WIDTH;
            requestedHeight = Float.isFinite(requestedHeight) ? Math.clamp(requestedHeight, 0, 10000) : 0;
        }
        Card resized(float width, float height) { return new Card(node, blocks, width, height); }
        float minimumHeight() { return naturalHeight() - textFields() * 40; }
        float height() { return requestedHeight == 0 ? naturalHeight() : Math.max(minimumHeight(), requestedHeight); }
        float fieldGrowth() { return requestedHeight == 0 || textFields() == 0 ? 0 : (height() - naturalHeight()) / textFields(); }
        float blockHeight(int index) {
            var block = blocks.get(index);
            if (requestedHeight == 0) return block.height();
            return block.height() + (textFields() == 0 ? (height() - naturalHeight()) / blocks.size()
                    : block.fields().stream().filter(EditorGraphInline.Field::multiline).count() * fieldGrowth());
        }
        float blockY(int index) {
            float y = HEADER;
            for (int i = 0; i < index; i++) y += blockHeight(i);
            return y;
        }
        float outputY(int index) { return blockY(index) + blockHeight(index) / 2; }
    }

    static List<Card> cards(AuthoringDocument document, Function<JsonElement, String> wording) {
        return cards(null, document, wording, "en_us");
    }

    static List<Card> cards(AuthoringWorkspace workspace, AuthoringDocument document, String locale) {
        return cards(workspace, document, value -> workspace.resolveText(document, value, locale), locale);
    }

    private static List<Card> cards(AuthoringWorkspace workspace, AuthoringDocument document,
                                    Function<JsonElement, String> wording, String locale) {
        List<Card> cards = new ArrayList<>();
        JsonObject root = document.json();
        JsonObject metadata = document.metadata();
        var schema = EditorSchema.forDocument(document);
        List<DialogueGraph.Edge> outputs = DialogueGraph.outputs(document).stream().distinct().toList();
        Map<String, List<DialogueGraph.Edge>> byNode = outputs.stream().collect(java.util.stream.Collectors.groupingBy(DialogueGraph.Edge::from));
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (var node : DialogueGraph.nodes(document)) {
            if (!ids.add(node.id())) throw new IllegalArgumentException("Duplicate node ID: " + node.id());
            List<DialogueGraph.Edge> nodeOutputs = byNode.getOrDefault(node.id(), List.of());
            JsonObject value = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            List<Block> blocks = new ArrayList<>();
            for (String field : List.of("conditions", "available_when", "availability", "objectives", "on_enter", "entry_actions", "actions", "effects",
                    "text", "line", "lines", "variants", "exchanges", "scenes", "dialogue", "camera", "rewards", "on_exit", "exit_actions")) {
                if (!value.has(field)) continue;
                JsonElement item = value.get(field);
                if (item.isJsonArray() && item.getAsJsonArray().isEmpty()) continue;
                String title = switch (field) {
                    case "conditions", "available_when", "availability" -> "Only if";
                    case "on_enter", "entry_actions" -> "On entry";
                    case "on_exit", "exit_actions" -> "On exit";
                    case "actions", "effects" -> "Do";
                    case "text", "line", "lines", "variants" -> "Say";
                    case "exchanges" -> "Conversation";
                    default -> Character.toUpperCase(field.charAt(0)) + field.substring(1);
                };
                if (item.isJsonArray() && List.of("conditions", "actions", "effects", "on_enter", "entry_actions", "on_exit", "exit_actions", "objectives").contains(field)) {
                    for (int i = 0; i < item.getAsJsonArray().size(); i++) {
                        JsonElement block = item.getAsJsonArray().get(i);
                        String summary = block.isJsonObject() ? behaviorLabel(block.getAsJsonObject()) : wording.apply(block);
                        blocks.add(new Block(node.pointer() + "/" + field + "/" + i, title, summary, null));
                    }
                    continue;
                }
                String text;
                String styledText = null;
                if (List.of("text", "line", "lines", "variants", "exchanges").contains(field)) {
                    if (!field.equals("exchanges") && item.isJsonArray() && item.getAsJsonArray().size() > 1) title = "Say one of";
                    JsonElement first = item.isJsonArray() ? item.getAsJsonArray().get(0) : item;
                    styledText = wording.apply(first);
                    text = com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.plainText(
                            com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.parse(styledText,
                                    com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE));
                    if (item.isJsonArray() && item.getAsJsonArray().size() > 1) {
                        String more = " (+" + (item.getAsJsonArray().size() - 1) + ")";
                        text += more;
                        styledText += more;
                    }
                } else {
                    int count = item.isJsonArray() ? item.getAsJsonArray().size() : 1;
                    text = count + (count == 1 ? " block" : " blocks");
                    if (item.isJsonArray() && item.getAsJsonArray().get(0).isJsonObject()) {
                        String type = DialogueGraph.string(item.getAsJsonArray().get(0).getAsJsonObject(), "type");
                        if (!type.isBlank()) text += " - " + type.replace('_', ' ');
                    }
                }
                blocks.add(new Block(node.pointer() + "/" + field, title, text, null, styledText));
            }
            String responseField = document.kind() == DefinitionKind.FORCED ? "options" : "responses";
            var responses = DialogueGraph.array(value, responseField);
            for (int i = 0; i < responses.size(); i++) {
                if (!responses.get(i).isJsonObject()) continue;
                JsonObject response = responses.get(i).getAsJsonObject();
                String path = node.pointer() + "/" + responseField + "/" + i;
                String label = wording.apply(response.get("label"));
                if (response.has("check")) {
                    blocks.add(new Block(path, "Choice", label, null));
                    String checkId = DialogueGraph.string(response, "check");
                    var checks = DialogueGraph.array(root, "checks");
                    for (int c = 0; c < checks.size(); c++) {
                        if (!checks.get(c).isJsonObject() || !checkId.equals(DialogueGraph.string(checks.get(c).getAsJsonObject(), "id"))) continue;
                        String checkPath = "/checks/" + c;
                        blocks.add(new Block(checkPath, "Check", checkId, null));
                        for (var edge : nodeOutputs) {
                            if (edge.from().equals(node.id()) && edge.pointer().startsWith(checkPath + "/"))
                                blocks.add(new Block(path, edge.label().equals("success") ? "Pass" : "Fail", edge.to(), edge));
                        }
                    }
                } else {
                    var output = nodeOutputs.stream().filter(edge -> edge.pointer().startsWith(path + "/"))
                            .findFirst().orElse(null);
                    blocks.add(new Block(path, output != null && output.to().isEmpty() ? "End choice" : "Choice", label, output));
                }
                for (String field : List.of("conditions", "actions", "effects")) {
                    var behaviors = DialogueGraph.array(response, field);
                    for (int b = 0; b < behaviors.size(); b++) {
                        JsonElement behavior = behaviors.get(b);
                        blocks.add(new Block(path + "/" + field + "/" + b, field.equals("conditions") ? "Choice only if" : "Choice does",
                                behavior.isJsonObject() ? behaviorLabel(behavior.getAsJsonObject()) : wording.apply(behavior), null));
                    }
                }
            }
            for (var edge : nodeOutputs) {
                if (edge.from().equals(node.id()) && (edge.pointer().startsWith(node.pointer() + "/next") || edge.pointer().startsWith(node.pointer() + "/transition")))
                    blocks.add(new Block(node.pointer(), "Then", edge.to().isEmpty() ? terminalLabel(value) : edge.to(), edge));
            }
            if (document.kind() == DefinitionKind.QUEST) {
                var events = DialogueGraph.array(value, "events");
                for (int i = 0; i < events.size(); i++) {
                    String path = node.pointer() + "/events/" + i;
                    var output = nodeOutputs.stream().filter(edge -> edge.pointer().startsWith(path + "/")).findFirst().orElse(null);
                    if (events.get(i).isJsonObject()) blocks.add(new Block(path, "When " + DialogueGraph.string(events.get(i).getAsJsonObject(), "event"),
                            EditorQuestFlow.summary(EditorSchema.object(events.get(i).getAsJsonObject().get("transition"))), output));
                }
            }
            if (blocks.stream().noneMatch(block -> block.output() != null))
                blocks.add(new Block(node.pointer(), "End", terminalLabel(value), null));
            cards.add(EditorGraphSizing.restore(new Card(node, blocks.stream().map(block -> new Block(block.pointer(), block.title(), block.text(), block.output(),
                    block.styledText(), EditorGraphInline.fields(workspace, document, root, block, locale, schema))).toList()), metadata));
        }
        return List.copyOf(cards);
    }

    private static String behaviorLabel(JsonObject value) {
        if (value.has("action") && value.get("action").isJsonObject()) value = value.getAsJsonObject("action");
        String type = DialogueGraph.string(value, "type").replace('_', ' ');
        if (type.isBlank()) type = DialogueGraph.string(value, "id");
        if (value.has("min") && value.get("min").isJsonPrimitive()) return type + " >= " + value.get("min").getAsString();
        if (value.has("amount") && value.get("amount").isJsonPrimitive()) return type + " " + value.get("amount").getAsString();
        if (value.has("text") && value.get("text").isJsonPrimitive()) return type + ": " + value.get("text").getAsString();
        return type.isBlank() ? "Edit block" : type;
    }

    private static String terminalLabel(JsonObject value) {
        if (value.has("outcome")) return "Outcome: " + DialogueGraph.string(value, "outcome");
        String fieldName = value.has("transition") ? "transition" : "next";
        if (value.has(fieldName) && value.get(fieldName).isJsonObject()) {
            JsonObject next = value.getAsJsonObject(fieldName);
            for (String field : List.of("complete", "fail", "abandon"))
                if (next.has(field) && next.get(field).isJsonPrimitive() && next.get(field).getAsBoolean())
                    return Character.toUpperCase(field.charAt(0)) + field.substring(1) + " quest";
        }
        return "Finish";
    }

    static Map<String, float[]> arrange(List<Card> cards) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        var queue = new java.util.ArrayDeque<String>();
        for (var card : cards) if (card.node().entry()) {
            columns.put(card.node().id(), 0);
            queue.add(card.node().id());
        }
        Map<String, Card> byId = new LinkedHashMap<>();
        for (var card : cards) byId.put(card.node().id(), card);
        while (columns.size() < cards.size() || !queue.isEmpty()) {
            if (queue.isEmpty()) {
                String id = cards.stream().map(card -> card.node().id()).filter(key -> !columns.containsKey(key)).findFirst().orElseThrow();
                columns.put(id, 0);
                queue.add(id);
            }
            String id = queue.remove();
            for (var block : byId.get(id).blocks()) {
                if (block.output() == null || block.output().to().isEmpty() || !byId.containsKey(block.output().to())
                        || columns.containsKey(block.output().to())) continue;
                columns.put(block.output().to(), columns.get(id) + 1);
                queue.add(block.output().to());
            }
        }
        Map<Integer, Float> widths = new java.util.TreeMap<>();
        columns.forEach((id, column) -> widths.merge(column, byId.get(id).width(), Math::max));
        Map<Integer, Float> offsets = new LinkedHashMap<>();
        float offset = 0;
        for (var column : widths.entrySet()) {
            offsets.put(column.getKey(), offset);
            offset += column.getValue() + 90;
        }
        Map<String, float[]> result = new LinkedHashMap<>();
        Map<Integer, Float> heights = new LinkedHashMap<>();
        for (String id : columns.keySet()) {
            var card = byId.get(id);
            int column = columns.get(id);
            float y = heights.getOrDefault(column, 0f);
            result.put(card.node().id(), new float[] {offsets.get(column), y});
            heights.put(column, y + card.height() + 40);
        }
        return result;
    }
}
