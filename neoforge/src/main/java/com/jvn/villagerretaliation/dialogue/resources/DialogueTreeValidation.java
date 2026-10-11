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
