package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class DialogueTreeService {
    private static final String ENTRY_OPTION_PREFIX = "dt:";
    private static final String RESPONSE_OPTION_PREFIX = "dr:";
    public static final String LEAVE_OPTION_ID = "dt:leave";

    private DialogueTreeService() {
    }

    public static void clearRuntimeState() {
        // sessions now belong to each world's saved data
    }

    public static String entryOptionId(ResourceLocation treeId, String entryId) {
        return ENTRY_OPTION_PREFIX + treeId + ":" + entryId;
    }

    public static String responseOptionId(ResourceLocation treeId, String responseId) {
        return RESPONSE_OPTION_PREFIX + treeId + ":" + responseId;
    }

    public static Optional<List<DialogueOptionDefinition>> activeOptions(DialogueContext context) {
        if (!sessions(context).diagnostic().isBlank() || SceneContinuationService.hasPendingDialogue(context)) return Optional.of(List.of());
        SessionKey key = key(context);
        Session session = sessions(context).get(key);
        if (session == null) {
            return Optional.empty();
        }

        DialogueTreeDefinition tree = DialogueTreeResources
                .tree(context.level().getServer(), context.locale(), session.treeId())
                .orElse(null);
        if (tree == null || !tree.matches(context)) return Optional.empty();

        DialogueTreeDefinition.Node node = tree.node(session.nodeId()).orElse(null);
        if (node == null || !node.matches(context)) return Optional.empty();
        if (node.end()) {
            sessions(context).remove(key);
            return Optional.empty();
        }

        List<DialogueOptionDefinition> options = node.responses().stream()
                .filter(response -> response.matches(context))
                .sorted(Comparator.comparingInt(DialogueTreeDefinition.Response::priority).reversed()
                        .thenComparingInt(DialogueTreeDefinition.Response::order)
                        .thenComparing(DialogueTreeDefinition.Response::id))
                .map(response -> response.toOption(context, tree.id(), tree.metadata()))
                .toList();
        return options.isEmpty() ? Optional.empty() : Optional.of(options);
    }

    public static Optional<VillagerDialogueService.DialogueResult> handleDialogueOption(
            DialogueContext context,
            DialogueOptionDefinition option) {
        DialogueTreeReference reference = option.treeReference();
        if (reference.isEmpty()) {
            return Optional.empty();
        }

        if (reference.isEntry()) {
            return startEntry(context, reference.treeId(), reference.entryId());
        }
        if (reference.isResponse()) {
            return selectResponse(context, reference.treeId(), reference.responseId());
        }
        return Optional.empty();
    }

    public static SelectionPreview previewSelection(DialogueContext context, DialogueOptionDefinition option) {
        DialogueTreeReference reference = option.treeReference();
        if (reference.isEmpty()) return new SelectionPreview(null, List.of());
        var tree = DialogueTreeResources.tree(context.level().getServer(), context.locale(), reference.treeId()).orElse(null);
        if (tree == null || !tree.matches(context)) return new SelectionPreview(null, List.of());
        if (reference.isEntry()) {
            var entry = tree.entry(reference.entryId()).orElse(null);
            var node = entry == null ? null : tree.node(entry.start()).orElse(null);
            return entry != null && entry.matches(context, VillagerDialogueService.moodFor(context)) && node != null && node.matches(context)
                    ? new SelectionPreview(null, node.actions()) : new SelectionPreview(null, List.of());
        }
        Session session = sessions(context).get(key(context));
        if (session == null || !tree.id().equals(session.treeId())) {
            return new SelectionPreview(null, List.of());
        }
        var node = tree.node(session.nodeId()).orElse(null);
        if (node == null || !node.matches(context)) return new SelectionPreview(session.id(), List.of());
        var response = node.responses().stream().filter(value -> value.id().equals(reference.responseId()) && value.matches(context))
                .findFirst().orElse(null);
        if (response == null) return new SelectionPreview(session.id(), List.of());
        var actions = new java.util.ArrayList<>(response.actions());
        var next = response.end() ? null : tree.node(response.next()).orElse(null);
        if (next != null) actions.addAll(next.actions());
        return new SelectionPreview(session.id(), actions);
    }

    public record SelectionPreview(UUID sessionId, List<VillagerActionDefinition> actions) {
        public SelectionPreview {
            actions = List.copyOf(actions);
        }
    }

    public static boolean leaveActiveSession(DialogueContext context) {
        return sessions(context).remove(key(context)) != null;
    }

    public static Optional<VillagerDialogueService.DialogueResult> startEntry(
            DialogueContext context,
            ResourceLocation treeId,
            String entryId) {
        return startEntry(context, treeId, entryId, Map.of());
    }

    public static Optional<VillagerDialogueService.DialogueResult> startEntry(
            DialogueContext context,
            ResourceLocation treeId,
            String entryId,
            Map<String, String> inheritedReplacements) {
        return startEntryResult(context, treeId, entryId, inheritedReplacements, false).dialogue();
    }

    public static EntryResult startEntryResult(DialogueContext context, ResourceLocation treeId, String entryId,
            Map<String, String> inheritedReplacements, boolean forced) {
        if (!sessions(context).diagnostic().isBlank()) return unavailableEntry(sessions(context).diagnostic());
        if (SceneContinuationService.hasPendingDialogue(context)) return unavailableEntry("dialogue actions are still pending");
        DialogueTreeDefinition tree = DialogueTreeResources.tree(context.level().getServer(), context.locale(), treeId).orElse(null);
        if (tree == null || !tree.matches(context)) return unavailableEntry("dialogue tree unavailable");
        DialogueTreeDefinition.Entry entry = tree.entry(entryId).orElse(null);
        if (entry == null || !entry.matches(context, VillagerDialogueService.moodFor(context))) {
            return unavailableEntry("dialogue entry unavailable");
        }
        return enterNodeResult(context, tree, entry.start(), "", inheritedReplacements, forced);
    }

    private static Optional<VillagerDialogueService.DialogueResult> selectResponse(
            DialogueContext context,
            ResourceLocation treeId,
            String responseId) {
        SessionKey key = key(context);
        Session session = sessions(context).get(key);
        if (session == null || !treeId.equals(session.treeId())) {
            return Optional.empty();
        }

        if (SceneContinuationService.hasPendingDialogue(context)) return Optional.empty();
        DialogueTreeDefinition tree = DialogueTreeResources.tree(context.level().getServer(), context.locale(), treeId).orElse(null);
        if (tree == null || !tree.matches(context)) {
            sessions(context).remove(key);
            return Optional.empty();
        }
        DialogueTreeDefinition.Node node = tree.node(session.nodeId()).orElse(null);
        if (node == null || !node.matches(context)) {
            sessions(context).remove(key);
            return Optional.empty();
        }
        DialogueTreeDefinition.Response response = node.responses().stream()
                .filter(candidate -> candidate.id().equals(responseId) && candidate.matches(context))
                .findFirst()
                .orElse(null);
        if (response == null) {
            return Optional.empty();
        }

        String responseTemplate = response.selectLine(context);
        ActionText responseActionText = executeActions(context, response.actions(), session.replacements(),
                tree.id() + "/" + node.id() + "/response/" + response.id(),
                new SceneContinuation.DialogueResume(tree.id(), response.end() ? "" : response.next(),
                        true, responseTemplate, VillagerConversationService.isForced(context.player(), context.villager())));
        String responseLine = resolve(responseTemplate, context, responseActionText.replacements());
        String leadingText = firstNonBlank(responseLine, responseActionText.text());
        if (!responseActionText.outcome().success()) {
            sessions(context).remove(key);
            return Optional.of(result(lineId(tree.id(), node.id(), response.id()),
                    responseActionText.outcome().suspended() ? leadingText : responseActionText.text()));
        }
        if (!response.end() && !response.next().isBlank()) {
            return Optional.of(enterNode(context, tree, response.next(), leadingText, responseActionText.replacements()));
        }

        sessions(context).remove(key);
        return Optional.of(result(
                lineId(tree.id(), node.id(), response.id()),
                firstNonBlank(leadingText, "")
        ));
    }

    private static VillagerDialogueService.DialogueResult enterNode(
            DialogueContext context,
            DialogueTreeDefinition tree,
            String nodeId,
            String leadingText,
            Map<String, String> inheritedReplacements) {
        return enterNodeResult(context, tree, nodeId, leadingText, inheritedReplacements,
                VillagerConversationService.isForced(context.player(), context.villager())).dialogue().orElseThrow();
    }

    private static EntryResult enterNodeResult(DialogueContext context, DialogueTreeDefinition tree, String nodeId,
            String leadingText, Map<String, String> inheritedReplacements, boolean forced) {
        DialogueTreeDefinition.Node node = tree.node(nodeId).orElse(null);
        if (node == null || !node.matches(context)) {
            sessions(context).remove(key(context));
            return new EntryResult(Optional.of(result(lineId(tree.id(), nodeId, "missing"), leadingText)),
                    ActionResult.unavailable("dialogue node unavailable", Set.of(), VillagerActionResult.EMPTY));
        }

        ActionText actionText = executeActions(context, node.actions(), inheritedReplacements,
                tree.id() + "/node/" + node.id(),
                new SceneContinuation.DialogueResume(tree.id(), node.id(), false, leadingText, forced));
        if (!actionText.outcome().success()) {
            sessions(context).remove(key(context));
            return new EntryResult(Optional.of(result(actionText.lineId(), firstNonBlank(actionText.text(), leadingText))),
                    actionText.outcome());
        }
        return new EntryResult(Optional.of(presentNode(context, tree, node, firstNonBlank(leadingText, actionText.text()),
                actionText.replacements(), actionText.lineId())), actionText.outcome());
    }

    private static VillagerDialogueService.DialogueResult presentNode(DialogueContext context,
            DialogueTreeDefinition tree, DialogueTreeDefinition.Node node, String leadingText,
            Map<String, String> replacements, String actionLineId) {
        String nodeLine = resolve(node.selectLine(context), context, replacements);
        String text = firstNonBlank(leadingText, nodeLine);
        if (!node.end()) {
            sessions(context).put(key(context), new Session(UUID.randomUUID(), tree.id(), node.id(), context.level().getGameTime(), replacements));
        } else {
            sessions(context).remove(key(context));
        }
        String resultLineId = actionLineId.isBlank() ? lineId(tree.id(), node.id(), "") : actionLineId;
        return result(resultLineId, text);
    }

    public static void resumeAfterActions(DialogueContext context, SceneContinuation.DialogueResume resume,
            Map<String, String> replacements) {
        if (!sessions(context).diagnostic().isBlank()) return;
        DialogueTreeDefinition tree = DialogueTreeResources.tree(
                context.level().getServer(), context.locale(), resume.treeId()).orElse(null);
        if (tree == null || !tree.matches(context) || resume.nodeId().isBlank()) return;
        DialogueTreeDefinition.Node node = tree.node(resume.nodeId()).orElse(null);
        if (node == null || !node.matches(context)) return;
        String leading = resolve(resume.leadingText(), context, replacements);
        VillagerDialogueService.DialogueResult result;
        if (resume.runActions()) {
            EntryResult entry = enterNodeResult(context, tree, node.id(), leading, replacements, resume.forced());
            if (!entry.outcome().success()) return;
            result = entry.dialogue().orElseThrow();
        } else {
            result = presentNode(context, tree, node, leading, replacements, "");
        }
        if (resume.forced()) {
            List<DialogueOptionDefinition> options = activeOptions(context).orElse(List.of());
            if (options.isEmpty()) {
                if (!result.text().isBlank()) VillagerInteractionService.sendVillagerNotice(context.player(), context.villager(), result.text());
            } else if (!VillagerInteractionService.openForcedDialogue(context.player(), context.villager(), result.text(),
                    options, options.stream().anyMatch(DialogueOptionDefinition::forceCameraTowardsVillager))) {
                leaveActiveSession(context);
            }
        } else if (VillagerConversationService.validate(context.player(), context.villager())
                && !VillagerConversationService.isForced(context.player(), context.villager())) {
            VillagerInteractionScreenOpener.refreshNormal(context.player(), context.villager());
            VillagerInteractionService.sendPersonalVillagerChat(
                    context.player(), context.villager(), result.text(), result.textSegments());
        }
    }

    private static ActionText executeActions(DialogueContext context, List<VillagerActionDefinition> actions,
            Map<String, String> inheritedReplacements, String sourcePointer,
            SceneContinuation.DialogueResume resume) {
        Map<String, String> replacements = new LinkedHashMap<>(DialoguePlaceholders.base(context));
        if (inheritedReplacements != null) replacements.putAll(inheritedReplacements);
        SceneContinuationService.RunResult execution = SceneContinuationService.run(context, actions, replacements,
                () -> VillagerQuestService.flashTracker(context.player(), true), sourcePointer, resume);
        ActionResult outcome = execution.outcome();
        if (execution.suspended()) {
            outcome = new ActionResult(outcome.status(), outcome.message(), outcome.capabilities(), outcome.legacyResult(),
                    execution.continuationId(), ActionResult.SuspensionKind.CONTINUATION, false);
        }
        VillagerActionResult result = outcome.legacyResult();
        return new ActionText(result.lineId(), result.text(), result.replacements(), outcome);
    }

    private static EntryResult unavailableEntry(String diagnostic) {
        return new EntryResult(Optional.empty(), ActionResult.unavailable(diagnostic, Set.of(), VillagerActionResult.EMPTY));
    }

    public record EntryResult(Optional<VillagerDialogueService.DialogueResult> dialogue, ActionResult outcome) {
    }

    private static String resolve(String template, DialogueContext context, Map<String, String> replacements) {
        if (template == null || template.isBlank()) {
            return "";
        }
        return VillagerDialogueResources.resolveTemplate(
                template,
                DialoguePlaceholders.merge(DialoguePlaceholders.base(context), replacements)
        );
    }

    private static VillagerDialogueService.DialogueResult result(String lineId, String text) {
        return new VillagerDialogueService.DialogueResult(lineId, text == null ? "" : text);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String lineId(ResourceLocation treeId, String nodeId, String responseId) {
        StringBuilder builder = new StringBuilder("tree_")
                .append(treeId.toString().replace(':', '_').replace('/', '_'))
                .append("_")
                .append(nodeId == null ? "missing" : nodeId);
        if (responseId != null && !responseId.isBlank()) {
            builder.append("_").append(responseId);
        }
        return builder.toString();
    }

    private static DialogueTreeSessionSavedData sessions(DialogueContext context) {
        return DialogueTreeSessionSavedData.get(context.level());
    }

    private static SessionKey key(DialogueContext context) {
        return new SessionKey(context.player().getUUID(), context.villager().getUUID());
    }

    record SessionKey(UUID playerId, UUID villagerId) {
    }

    record Session(
            UUID id,
            ResourceLocation treeId,
            String nodeId,
            long lastTouchedGameTime,
            Map<String, String> replacements) {
        Session {
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }

    }

    private record ActionText(String lineId, String text, Map<String, String> replacements, ActionResult outcome) {
        private ActionText {
            lineId = lineId == null ? "" : lineId;
            text = text == null ? "" : text;
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }
    }
}
