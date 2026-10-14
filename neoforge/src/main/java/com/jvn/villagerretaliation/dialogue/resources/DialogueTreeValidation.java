package com.jvn.villagerretaliation.dialogue.resources;

import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class DialogueTreeValidation {
    private DialogueTreeValidation() {
    }

    public static DialogueTreeDefinition validate(ResourceLocation location, DialogueTreeDefinition tree) {
        if (tree.nodes().isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree", "tree must define at least one node.");
            return null;
        }
        if (tree.entries().isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree", "tree must define at least one entry.");
            return null;
        }
        var entryIds = new java.util.HashSet<String>();
        for (var entry : tree.entries()) {
            if (!entryIds.add(entry.id())) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree", "duplicate entry id: " + entry.id());
                return null;
            }
        }
        for (var node : tree.nodes().values()) {
            var responseIds = new java.util.HashSet<String>();
            for (var response : node.responses()) {
                if (!responseIds.add(response.id()) || (!response.next().isBlank() && !tree.nodes().containsKey(response.next()))) {
                    DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree node " + node.id(),
                            "duplicate response or unknown target: " + response.id() + " -> " + response.next());
                    return null;
                }
                if (response.end() && !response.next().isBlank())
                    DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree response " + response.id(),
                            "next is ignored because end=true closes the conversation");
            }
            if (node.end() && !node.responses().isEmpty())
                DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree node " + node.id(),
                        "responses are unreachable because end=true closes the conversation");
        }
        var reachable = new java.util.HashSet<String>();
        var pending = new java.util.ArrayDeque<String>();
        tree.entries().forEach(entry -> pending.add(entry.start()));
        while (!pending.isEmpty()) {
            var id = pending.removeFirst();
            if (!reachable.add(id)) continue;
            var node = tree.nodes().get(id);
            if (node != null && !node.end()) node.responses().stream()
                    .filter(response -> !response.end() && !response.next().isBlank())
                    .forEach(response -> pending.add(response.next()));
        }
        for (var id : tree.nodes().keySet()) if (!reachable.contains(id))
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree node " + id, "node is unreachable");
        List<DialogueTreeDefinition.Entry> entries = tree.entries().stream().filter(entry -> {
            if (!networkSafeOption(location, "dialogue tree entry \"" + entry.id() + "\"",
                    DialogueTreeService.entryOptionId(tree.id(), entry.id()), entry.label())) return false;
            if (!tree.nodes().containsKey(entry.start())) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree entry \"" + entry.id() + "\"",
                        "start references unknown node \"" + entry.start() + "\".");
                return false;
            }
            return true;
        }).toList();
        if (entries.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree", "tree has no valid entries.");
            return null;
        }
        Map<String, DialogueTreeDefinition.Node> nodes = new LinkedHashMap<>();
        for (var node : tree.nodes().values()) {
            List<DialogueTreeDefinition.Response> responses = node.responses().stream().filter(response ->
                    networkSafeOption(location, "dialogue tree node \"" + node.id() + "\" response \"" + response.id() + "\"",
                            DialogueTreeService.responseOptionId(tree.id(), response.id()), response.label())).toList();
            nodes.put(node.id(), new DialogueTreeDefinition.Node(node.id(), node.lines(), node.textVariants(),
                    node.actions(), node.conditions(), responses, node.end(), node.exchanges(), node.camera()));
        }
        return new DialogueTreeDefinition(tree.id(), tree.title(), tree.description(), tree.metadata(),
                tree.conditions(), entries, nodes);
    }

    private static boolean networkSafeOption(
            ResourceLocation location,
            String context,
            String optionId,
            String label) {
        if (!DialogueOptionDefinition.isNetworkSafeId(optionId)) {
            DatapackDiagnostics.warnSkippedEntry(
                    location,
                    "dialogue tree option",
                    context,
                    "generated id must be at most " + DialogueOptionDefinition.MAX_NETWORK_ID_LENGTH + " characters.");
            return false;
        }
        if (!DialogueOptionDefinition.isNetworkSafeLabel(label)) {
            DatapackDiagnostics.warnSkippedEntry(
                    location,
                    "dialogue tree option",
                    context,
                    "label must be at most " + DialogueOptionDefinition.MAX_NETWORK_LABEL_LENGTH + " characters.");
            return false;
        }
        return true;
    }

}
